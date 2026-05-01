package com.young.aircraft.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QRCodeToolViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QRCodeToolUiState())
    val uiState: StateFlow<QRCodeToolUiState> = _uiState.asStateFlow()

    fun startScanning() {
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            mode = QrToolMode.Scanning
        )
    }

    fun stopScanning() {
        val hasPreview = _uiState.value.generatedBitmap != null
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            mode = QrToolMode.Content(hasPreview = hasPreview)
        )
    }

    fun generateQrCode(content: String): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix = MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, 512, 512, hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            val bgColor = "#0F1118".toColorInt()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.WHITE else bgColor
                }
            }
            val bitmap = createBitmap(width, height)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            onQrGenerated(bitmap)
            bitmap
        } catch (_: WriterException) {
            null
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true
        )
        val scaled800 = scaleBitmapDown(bitmap, 800)
        val scaled400 = scaleBitmapDown(bitmap, 400)
        return tryDecodeBitmap(bitmap, hints)
            ?: tryDecodeBitmap(scaled800, hints)
            ?: tryDecodeBitmap(scaled400, hints)
            ?: tryDecodeBitmap(cropCenter(bitmap, 0.6f), hints)
            ?: tryDecodeBitmap(cropCenter(scaled800, 0.6f), hints)
    }

    fun onSaveSuccess(uri: Uri) {
        _uiState.value = _uiState.value.copy(savedFileUri = uri)
    }

    private fun onQrGenerated(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            generatedBitmap = bitmap,
            savedFileUri = null,
            mode = QrToolMode.Content(hasPreview = true)
        )
    }

    private fun tryDecodeBitmap(bitmap: Bitmap, hints: Map<DecodeHintType, Any>): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader()
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: NotFoundException) {}
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
        } catch (_: NotFoundException) {}
        try {
            return reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
        } catch (_: NotFoundException) {}
        try {
            return reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())), hints).text
        } catch (_: NotFoundException) {}
        return null
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(
            bitmap, (width * scale).toInt(), (height * scale).toInt(), true
        )
    }

    private fun cropCenter(bitmap: Bitmap, ratio: Float): Bitmap {
        val cropW = (bitmap.width * ratio).toInt()
        val cropH = (bitmap.height * ratio).toInt()
        val x = (bitmap.width - cropW) / 2
        val y = (bitmap.height - cropH) / 2
        return Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QRCodeToolViewModel() as T
        }
    }
}
