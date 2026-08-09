package com.jamesmoran.adventurepad

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/** Read-only, signature-protected bridge for ScummVM's upper non-game surround. */
class ActiveSkinProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (resolveFile(uri) != null) "image/png" else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("AdventurePad skins are read-only")
        val file = resolveFile(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun resolveFile(uri: Uri) = context?.let { appContext ->
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != "gameplay" || segments[2] != "top-surround") return@let null
        SkinRepository.get(appContext)
            .resolve(SkinContext.GAMEPLAY, Uri.decode(segments[1]))
            .assetFile(SkinSlots.TOP_SURROUND)
    }

    companion object {
        const val AUTHORITY = "com.jamesmoran.adventurepad.skins"
        val CHANGES_URI: Uri = Uri.parse("content://$AUTHORITY/gameplay")

        fun upperSurroundUri(targetId: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath("gameplay")
            .appendPath(targetId)
            .appendPath("top-surround")
            .build()
    }
}
