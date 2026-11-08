package com.jamesmoran.adventurepad

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val EXTERNAL_LAUNCHER_SYNC_TAG = "ExternalLauncherSync"

internal object ExternalLauncherPreparationSerialiser {
    private val lock = ReentrantLock()

    fun <T> run(block: () -> T): T = lock.withLock(block)
}

internal data class ExternalLauncherSyncEligibility(
    val setupComplete: Boolean,
    val treeUriPresent: Boolean,
    val hasReadWritePermission: Boolean,
    val launcherSupported: Boolean,
) {
    val eligible: Boolean
        get() = setupComplete && treeUriPresent && hasReadWritePermission && launcherSupported
}

internal fun externalLauncherSyncEligibility(
    setupComplete: Boolean,
    treeUri: Uri?,
    hasReadWritePermission: Boolean,
    launcher: ExternalLauncherAdapter?,
) = ExternalLauncherSyncEligibility(
    setupComplete = setupComplete,
    treeUriPresent = treeUri != null,
    hasReadWritePermission = hasReadWritePermission,
    launcherSupported = launcher != null,
)

internal fun interface AutomaticExternalLauncherSynchronizer {
    fun synchronize(targets: List<ScummVMTarget>, shouldContinue: () -> Boolean): Boolean
}

/** Serial, latest-snapshot-wins coordinator for automatic add-only launcher preparation. */
internal class ExternalLauncherSyncCoordinator(
    private val scope: CoroutineScope,
    private val synchronizer: AutomaticExternalLauncherSynchronizer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var nextSequence = 0L
    private var latestSequence = 0L
    private var pending: Snapshot? = null
    private var activeFingerprint: String? = null
    private var workerRunning = false

    fun submit(targets: List<ScummVMTarget>) {
        val snapshotTargets = normalizeScummVMTargets(targets)
        val fingerprint = externalLauncherLibraryFingerprint(snapshotTargets)
        var launchWorker = false
        synchronized(lock) {
            if (pending?.fingerprint == fingerprint ||
                (activeFingerprint == fingerprint && pending == null)
            ) {
                return
            }
            val snapshot = Snapshot(++nextSequence, fingerprint, snapshotTargets)
            latestSequence = snapshot.sequence
            pending = snapshot
            if (!workerRunning) {
                workerRunning = true
                launchWorker = true
            }
        }
        if (launchWorker) scope.launch(dispatcher) { drain() }
    }

    private fun drain() {
        while (true) {
            val snapshot = synchronized(lock) {
                val next = pending
                if (next == null) {
                    activeFingerprint = null
                    workerRunning = false
                    return
                }
                pending = null
                activeFingerprint = next.fingerprint
                next
            }
            if (!isCurrent(snapshot.sequence)) continue
            runCatching {
                synchronizer.synchronize(snapshot.targets) { isCurrent(snapshot.sequence) }
            }.onFailure { error ->
                Log.w(EXTERNAL_LAUNCHER_SYNC_TAG, "Automatic external-launcher preparation failed", error)
            }
            synchronized(lock) {
                if (activeFingerprint == snapshot.fingerprint) activeFingerprint = null
            }
        }
    }

    private fun isCurrent(sequence: Long): Boolean = synchronized(lock) {
        sequence == latestSequence
    }

    private data class Snapshot(
        val sequence: Long,
        val fingerprint: String,
        val targets: List<ScummVMTarget>,
    )

    companion object {
        fun create(context: Context, scope: CoroutineScope): ExternalLauncherSyncCoordinator {
            val applicationContext = context.applicationContext
            return ExternalLauncherSyncCoordinator(
                scope = scope,
                synchronizer = AutomaticExternalLauncherSynchronizer { targets, shouldContinue ->
                    synchronizeConfiguredLauncher(applicationContext, targets, shouldContinue)
                },
            )
        }
    }
}

private fun synchronizeConfiguredLauncher(
    context: Context,
    targets: List<ScummVMTarget>,
    shouldContinue: () -> Boolean,
): Boolean {
    return ExternalLauncherPreparationSerialiser.run {
        synchronizeConfiguredLauncherSerially(context, targets, shouldContinue)
    }
}

private fun synchronizeConfiguredLauncherSerially(
    context: Context,
    targets: List<ScummVMTarget>,
    shouldContinue: () -> Boolean,
): Boolean {
    val preferences = CocoonIntegrationPreferences(context)
    val treeUri = preferences.treeUri
    val adapter = findExternalLauncher(preferences.selectedLauncherId)
    val eligibility = externalLauncherSyncEligibility(
        setupComplete = preferences.setupComplete,
        treeUri = treeUri,
        hasReadWritePermission = preferences.hasPersistedReadWritePermission(context.contentResolver),
        launcher = adapter,
    )
    if (!eligibility.eligible || treeUri == null || adapter == null || !shouldContinue()) return false

    val preparer = ExternalLauncherPreparer(
        gameExporter = CocoonDptExporter(FileCocoonOwnershipStore(context, treeUri)),
        platformOwnershipStore = FilePreparedLauncherOwnershipStore(context, treeUri),
        adapter = adapter,
    )
    val result = preparer.applyAdditions(
        targets = targets,
        directory = SafCocoonExportDirectory(context.contentResolver, treeUri),
        journal = FilePendingGameExportStore(context, treeUri),
        shouldContinue = shouldContinue,
    )
    if (result.successful && shouldContinue()) {
        preferences.preparedGameCount = targets.distinctBy(ScummVMTarget::targetId).size
    }
    if (result.written.isNotEmpty()) {
        Log.i(
            EXTERNAL_LAUNCHER_SYNC_TAG,
            "Prepared ${result.written.size} new external-launcher game entry(s)",
        )
    }
    if (!result.platformReady || result.conflicts > 0 || result.updatesRequired > 0 || result.failures.isNotEmpty()) {
        Log.w(
            EXTERNAL_LAUNCHER_SYNC_TAG,
            "Automatic preparation needs attention: platformReady=${result.platformReady}, " +
                "conflicts=${result.conflicts}, updatesRequired=${result.updatesRequired}, " +
                "failures=${result.failures.size}",
        )
    }
    return result.successful
}

internal fun externalLauncherLibraryFingerprint(targets: List<ScummVMTarget>): String {
    val canonical = normalizeScummVMTargets(targets).joinToString(separator = "\u0000") {
        "${it.targetId}\u0001${it.title}"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
