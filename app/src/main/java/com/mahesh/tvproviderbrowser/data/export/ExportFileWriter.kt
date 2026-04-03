package com.mahesh.tvproviderbrowser.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Provides multiple strategies for writing export files:
 *
 * 1. **MediaStore (API 29+)** – writes to the public Downloads folder via content resolver.
 * 2. **Direct write (API < 29)** – writes to the external Downloads directory.
 * 3. **File path** – writes to a user-chosen directory (app files, USB, SD card).
 */
object ExportFileWriter {

    /**
     * Opens an [OutputStream] to a new file in the Downloads folder.
     * The caller must close the stream after writing.
     *
     * @return Pair of (OutputStream, absolute file path for the user)
     */
    fun createDownloadFile(
        context: Context,
        fileName: String,
        mimeType: String = "text/csv",
    ): Pair<OutputStream, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(context, fileName, mimeType)
        } else {
            createViaDirectWrite(fileName)
        }
    }

    /**
     * Opens an [OutputStream] to a new file in the given [directory].
     * Used when the user picks a specific storage location (app files, USB, etc.).
     *
     * @return Pair of (OutputStream, absolute file path for the user)
     */
    fun createFileAtPath(directory: File, fileName: String): Pair<OutputStream, String> {
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, fileName)
        return FileOutputStream(file) to file.absolutePath
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createViaMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
    ): Pair<OutputStream, String> {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create file in Downloads")
        val outputStream = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Failed to open output stream")

        // Build the real absolute path so the user sees the actual location
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val absolutePath = File(downloadsDir, fileName).absolutePath

        return outputStream to absolutePath
    }

    @Suppress("DEPRECATION")
    private fun createViaDirectWrite(fileName: String): Pair<OutputStream, String> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        return FileOutputStream(file) to file.absolutePath
    }
}




