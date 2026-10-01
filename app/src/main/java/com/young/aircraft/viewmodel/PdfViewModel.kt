package com.young.aircraft.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.aircraft.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/** Upper bound for a rendered page edge; 2048px ARGB_8888 is ~16MB, a page-safe ceiling. */
internal const val MAX_RENDER_WIDTH_PX = 2048

/** Default A4-ish ratio, only used before the real page size is known. */
internal const val DEFAULT_PDF_ASPECT_RATIO = 0.707f

private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.ISO_8859_1)

/** Rendered width for a [settledScale] zoom, clamped so a pinch can never ask for a huge bitmap. */
internal fun renderWidthFor(baseWidthPx: Int, settledScale: Float): Int =
    (baseWidthPx * settledScale).toInt().coerceIn(1, MAX_RENDER_WIDTH_PX)

/** True when [head] starts with the `%PDF-` magic number. */
internal fun hasPdfHeader(head: ByteArray): Boolean =
    head.size >= PDF_MAGIC.size && head.copyOf(PDF_MAGIC.size).contentEquals(PDF_MAGIC)

/**
 * Renders PDF pages on demand at an arbitrary width, so zooming re-renders at the needed
 * resolution instead of holding one oversized bitmap per page. A single [PdfRenderer] guarded by
 * [mutex]: opening pages from several coroutines at once is not safe.
 */
internal class PdfPageRenderer(context: Context, uri: Uri) : Closeable {

    private val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("Cannot open $uri")

    private val renderer: PdfRenderer

    init {
        // PdfRenderer only rejects an unreadable/encrypted file when a page is opened, so sniff
        // the header here and fail before anything reaches the UI.
        val head = context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArray(PDF_MAGIC.size).also { input.read(it) }
        } ?: throw IOException("Cannot read $uri")
        if (!hasPdfHeader(head)) throw IOException("Not a PDF: $uri")
        renderer = try {
            PdfRenderer(pfd)
        } catch (e: Throwable) {
            pfd.close()
            throw e
        }
    }

    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /** Width/height ratio, used as the placeholder so scrolling never jumps while loading. */
    suspend fun aspectRatio(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { it.width.toFloat() / it.height }
        }
    }

    /** Page [index] rendered [targetWidth] px wide, or null when it cannot be rendered. */
    suspend fun render(index: Int, targetWidth: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceIn(1, MAX_RENDER_WIDTH_PX)
                    val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    override fun close() {
        renderer.close()
        pfd.close()
    }
}

/** Owns the open document and a bitmap cache; the Activity only renders UI. */
class PdfViewModel(app: Application) : AndroidViewModel(app) {

    private var renderer: PdfPageRenderer? = null

    private val _pageCount = MutableStateFlow(0)
    val pageCount = _pageCount.asStateFlow()

    private val _errorRes = MutableStateFlow<Int?>(null)
    val errorRes = _errorRes.asStateFlow()

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun open(uri: Uri) {
        closeDocument()
        _pageCount.value = 0
        _errorRes.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PdfPageRenderer(getApplication(), uri) }
                .onSuccess {
                    renderer = it
                    _pageCount.value = it.pageCount
                }
                .onFailure { error ->
                    _errorRes.value = when (error) {
                        is SecurityException -> R.string.pdf_reader_error_no_permission
                        else -> R.string.pdf_reader_error_invalid
                    }
                }
        }
    }

    suspend fun aspectRatio(index: Int): Float =
        renderer?.aspectRatio(index) ?: DEFAULT_PDF_ASPECT_RATIO

    /** Cached page bitmap at [width] px, or null while it renders or if rendering failed. */
    suspend fun getPage(index: Int, width: Int): Bitmap? {
        val key = "$index@$width"
        cache.get(key)?.let { return it }
        return renderer?.render(index, width)?.also { cache.put(key, it) }
    }

    private fun closeDocument() {
        renderer?.close()
        renderer = null
        cache.evictAll()
    }

    override fun onCleared() {
        closeDocument()
    }
}