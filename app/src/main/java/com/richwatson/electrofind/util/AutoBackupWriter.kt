package com.richwatson.electrofind.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore

// Writes to the public Downloads collection rather than app-private storage: app-private
// files (SharedPreferences, getFilesDir()) are wiped on uninstall, but MediaStore entries
// outside the app's own external-files directory are not, so this survives a reinstall.
object AutoBackupWriter {
    private const val FILE_NAME = "electrofind-auto-backup.json"
    private const val RELATIVE_PATH = "Download/ElectroFind"

    fun write(context: Context, json: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val existingId = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(FILE_NAME, "$RELATIVE_PATH/"),
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        val uri = if (existingId != null) {
            ContentUris.withAppendedId(collection, existingId)
        } else {
            resolver.insert(collection, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            }) ?: return false
        }

        return try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
