package com.young.aircraft.gui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.viewmodel.DEFAULT_PDF_ASPECT_RATIO
import com.young.aircraft.viewmodel.PdfViewModel
import com.young.aircraft.viewmodel.renderWidthFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_SCALE = 5f

/**
 * Debug-only PDF reader: [android.graphics.pdf.PdfRenderer] behind a zoomable LazyColumn.
 * Pages render at the settled zoom width, so memory stays bounded by the page cache instead of
 * holding one full-resolution bitmap per page.
 */
class PdfReaderActivity : ComponentActivity() {

    private lateinit var viewModel: PdfViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[PdfViewModel::class.java]
        setContent {
            AircraftTheme {
                PdfReaderScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}

@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    /** Only updated once a gesture ends; drives the high-resolution re-render. */
    var settledScale by mutableFloatStateOf(1f)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
        settledScale = 1f
    }
}

/** One laid-out page in a [androidx.compose.foundation.lazy.LazyList]: index, top, height. */
internal data class PageLayout(val index: Int, val offset: Int, val size: Int)

/**
 * 0-based index of the page filling most of the viewport.
 *
 * `firstVisibleItemIndex` is the item at the viewport *top*, so it sticks at `pageCount - 2` once
 * the list is scrolled to the end: the last page can never reach the top. Pick the item that
 * contains the viewport centre instead. [pages] must be sorted by [PageLayout.index], which is how
 * `LazyListLayoutInfo.visibleItemsInfo` is ordered.
 */
internal fun dominantPageIndex(
    viewportStart: Int,
    viewportEnd: Int,
    pages: List<PageLayout>
): Int {
    if (pages.isEmpty()) return 0
    val center = (viewportStart + viewportEnd) / 2
    val byCentre = pages.firstOrNull { it.offset + it.size > center }?.index ?: pages.last().index
    // The centre rule assumes pages taller than half a viewport. A short trailing page (a blank
    // back page is common) breaks that and would report N-1 while scrolled fully to the end, so
    // an unscrolled-past last page wins outright.
    val last = pages.last()
    val atEnd = last.offset < viewportEnd && last.offset + last.size >= viewportEnd
    return if (byCentre != last.index && atEnd) last.index else byCentre
}

private fun dominantPageIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    return dominantPageIndex(
        info.viewportStartOffset,
        info.viewportEndOffset,
        info.visibleItemsInfo.map { PageLayout(it.index, it.offset, it.size) }
    )
}

@Composable
private fun PdfReaderScreen(viewModel: PdfViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val pageCount by viewModel.pageCount.collectAsState()
    val errorRes by viewModel.errorRes.collectAsState()
    val zoom = remember { ZoomState() }
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { dominantPageIndex(listState) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            zoom.reset()
            viewModel.open(uri)
        }
    }

    // The message pane shows the failure, so no extra snackbar here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        PdfHeader(
            title = if (pageCount > 0) {
                stringResource(
                    R.string.pdf_reader_page_counter,
                    currentPage + 1,
                    pageCount
                )
            } else {
                stringResource(R.string.pdf_reader_title)
            },
            onBack = onBack,
            onOpen = { picker.launch(arrayOf("application/pdf")) }
        )

        when {
            pageCount > 0 -> PdfPageList(pageCount, currentPage, zoom, listState, viewModel)
            // Both empty and error states offer the picker; an error must not dead-end the screen.
            else -> MessagePane(
                text = stringResource(
                    if (errorRes != null) R.string.pdf_reader_error_invalid
                    else R.string.pdf_reader_empty_hint
                ),
                onPickFile = { picker.launch(arrayOf("application/pdf")) }
            )
        }
    }
}

@Composable
private fun MessagePane(text: String, onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = TextSubtle,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Surface(
            onClick = onPickFile,
            shape = RoundedCornerShape(4.dp),
            color = AccentGreen.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
            modifier = Modifier
                .padding(top = 20.dp)
                .width(200.dp)
                .heightIn(min = 48.dp)
                .testTag("btn_pick_pdf")
        ) {
            Text(
                text = stringResource(R.string.pdf_reader_choose_file),
                color = AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            )
        }
    }
}

/**
 * Zoom/pan container. Single-finger drags are left to the LazyColumn until the content is
 * actually zoomed in, so page scrolling still works; [ZoomState.settledScale] is what triggers
 * the sharper re-render once the gesture settles.
 */
@Composable
private fun ZoomableBox(
    zoom: ZoomState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.size > 1
                        if (multiTouch || zoom.scale > 1f) {
                            val newScale = (zoom.scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                            val pan = event.calculatePan()
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            zoom.scale = newScale
                            zoom.offset = if (newScale == 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    (zoom.offset.x + pan.x * newScale).coerceIn(-maxX, maxX),
                                    (zoom.offset.y + pan.y * newScale).coerceIn(-maxY, maxY)
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    zoom.settledScale = zoom.scale
                }
            }
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }
    ) {
        content()
    }
}

@Composable
private fun PdfPageList(
    pageCount: Int,
    currentPage: Int,
    zoom: ZoomState,
    listState: LazyListState,
    viewModel: PdfViewModel
) {
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Cap the page column so tablets/foldables don't stretch it edge to edge. Must wrap the
        // constraint reader too, otherwise the render width still measures the full window.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .maxContentWidth()
        ) {
            val density = LocalDensity.current
            ZoomableBox(zoom = zoom, modifier = Modifier.fillMaxSize()) {
                val baseWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                // Re-render at the settled zoom only, so a pinch never queues a render per frame.
                val renderWidth = renderWidthFor(baseWidthPx, zoom.settledScale)

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageCount, key = { it }) { index ->
                        PdfPage(index, renderWidth, viewModel)
                    }
                }
            }
        }

        if (pageCount >= 2) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = HeaderBackground,
                tonalElevation = 6.dp
            ) {
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { target ->
                        scope.launch { listState.scrollToItem(target.roundToInt().coerceIn(0, pageCount - 1)) }
                    },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = AccentGreen,
                        inactiveTrackColor = TextSubtle.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .width(280.dp)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPage(index: Int, widthPx: Int, viewModel: PdfViewModel) {
    var ratio by remember(index) { mutableFloatStateOf(DEFAULT_PDF_ASPECT_RATIO) }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index) { ratio = viewModel.aspectRatio(index) }
    // Keep the previous bitmap while the sharper one renders, so zoom does not flash white.
    LaunchedEffect(index, widthPx) { bitmap = viewModel.getPage(index, widthPx) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / ratio)
            .background(Color.White)
    ) {
        bitmap?.let { page ->
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_reader_page_desc, index + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun PdfHeader(title: String, onBack: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = title,
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        // Box, not Text: heightIn stretches the label's own line box, and the glyphs sit at the
        // top of it. Centering inside a 48dp box keeps the touch target without the offset.
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .heightIn(min = 48.dp)
                .background(AccentGreen.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.pdf_reader_open),
                color = AccentGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}