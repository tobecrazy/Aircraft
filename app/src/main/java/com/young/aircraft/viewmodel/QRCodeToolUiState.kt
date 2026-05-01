package com.young.aircraft.viewmodel

import android.graphics.Bitmap
import android.net.Uri

sealed class QrToolMode {
    data class Content(val hasPreview: Boolean) : QrToolMode()
    data object Scanning : QrToolMode()
}

data class QRCodeToolUiState(
    val isScanning: Boolean = false,
    val generatedBitmap: Bitmap? = null,
    val savedFileUri: Uri? = null,
    val mode: QrToolMode = QrToolMode.Content(hasPreview = false)
)
