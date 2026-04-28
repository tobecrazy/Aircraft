package com.young.aircraft.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilePickerHelper {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun getUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}$AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun createQrImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, "QRCode_$timeStamp.png")
    }

    fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val fileName = queryFileName(context, uri) ?: "temp_${System.currentTimeMillis()}"
            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (_: Exception) {
            null
        }
    }

    data class FileInfo(
        val name: String,
        val size: Long,
        val mimeType: String?
    )

    fun queryFileInfo(context: Context, uri: Uri): FileInfo {
        var name = "unknown"
        var size = 0L
        val mimeType = context.contentResolver.getType(uri)

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: "unknown"
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }

        return FileInfo(name, size, mimeType)
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return null
    }
}
