package com.jamesmoran.adventurepad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartSaveCapabilityInstrumentedTest {
    @Test
    fun explicitRefreshPublishesColdProcessCapabilitiesWithoutOpeningLauncher() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val connected = CountDownLatch(1)
        val published = CountDownLatch(1)
        var remote: Messenger? = null
        var readyReply: Bundle? = null
        val reply = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (message.what != MSG_GAME_LIBRARY ||
                    !message.data.getBoolean(KEY_SAVE_CAPABILITIES_READY, false)
                ) return
                readyReply = Bundle(message.data)
                published.countDown()
            }
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote = Messenger(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        val intent = Intent().setComponent(ComponentName(SCUMMVM_PACKAGE, SCUMMVM_SERVICE))
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("ScummVM bridge did not connect", connected.await(5, TimeUnit.SECONDS))
            repeat(2) {
                remote!!.send(Message.obtain(null, MSG_REFRESH_SAVE_CAPABILITIES).apply {
                    replyTo = reply
                })
            }
            assertTrue("Fresh save capabilities were not published", published.await(15, TimeUnit.SECONDS))

            val targets = readyReply!!.getParcelableArrayList(KEY_TARGETS, Bundle::class.java).orEmpty()
            val atlantis = targets.single { it.getString(KEY_TARGET_ID) == "atlantis" }
            assertTrue(atlantis.getBoolean(KEY_LOAD_GAME_AVAILABLE, false))
            assertTrue(atlantis.getInt(KEY_RESUME_SAVE_SLOT, -1) >= 0)

            val sky = targets.single { it.getString(KEY_TARGET_ID) == "sky" }
            assertTrue(sky.getInt(KEY_RESUME_SAVE_SLOT, -1) < 0)
            assertNotNull(sky.getString(KEY_RESUME_UNAVAILABLE_REASON))
        } finally {
            context.unbindService(connection)
            activity.finish()
        }
    }

    private companion object {
        const val SCUMMVM_PACKAGE = "org.scummvm.scummvm.debug"
        const val SCUMMVM_SERVICE = "org.scummvm.scummvm.RelativeInputService"
        const val MSG_GAME_LIBRARY = 203
        const val MSG_REFRESH_SAVE_CAPABILITIES = 206
        const val KEY_TARGETS = "targets"
        const val KEY_TARGET_ID = "targetId"
        const val KEY_RESUME_SAVE_SLOT = "resumeSaveSlot"
        const val KEY_RESUME_UNAVAILABLE_REASON = "resumeUnavailableReason"
        const val KEY_LOAD_GAME_AVAILABLE = "loadGameAvailable"
        const val KEY_SAVE_CAPABILITIES_READY = "saveCapabilitiesReady"
    }
}
