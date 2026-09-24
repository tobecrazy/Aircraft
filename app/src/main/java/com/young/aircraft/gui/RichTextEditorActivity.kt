package com.young.aircraft.gui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.themeAccent
import com.young.aircraft.utils.DataUriUtils
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.RichTextEditorViewModel
import com.young.richtext.RichTextEditorView
import org.json.JSONObject

private val ModeInactiveColor = Color(0x66FFFFFF)

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var viewModel: RichTextEditorViewModel
    private var accentArgb: Int = 0

    // Hosted inside AndroidView; the activity keeps references for content plumbing and tests.
    internal var richEditorView: RichTextEditorView? = null
        private set
    internal var previewWebView: WebView? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this, RichTextEditorViewModel.Factory())[RichTextEditorViewModel::class.java]
        accentArgb = themeAccent(SettingsRepository(this).getTheme()).toArgb()
        enableEdgeToEdge()

        setContent {
            AircraftTheme {
                var editMode by remember { mutableStateOf(viewModel.isEditMode) }
                var previewHtml by remember { mutableStateOf("") }

                RichTextEditorScreen(
                    editMode = editMode,
                    previewHtml = previewHtml,
                    onBack = { finish() },
                    onSwitchToEdit = {
                        viewModel.switchToEditMode()
                        editMode = true
                    },
                    onSwitchToPreview = {
                        previewHtml = buildPreviewHtml()
                        viewModel.switchToPreviewMode()
                        editMode = false
                    },
                    onLoadExampleJson = {
                        if (loadExampleJson()) editMode = true
                    },
                    onEditorCreated = { view ->
                        richEditorView = view
                        seedDefaultContent(view)
                    },
                    onEditorMessage = { message ->
                        ThemedMessage.makeText(this, message, ThemedMessage.LENGTH_SHORT).show()
                    },
                    onPreviewWebViewCreated = { webView ->
                        previewWebView?.destroy()
                        previewWebView = webView
                    },
                    onPreviewWebViewDestroyed = {
                        previewWebView?.destroy()
                        previewWebView = null
                    },
                    onImageTap = ::openImageDetails
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewWebView?.destroy()
        previewWebView = null
    }

    /**
     * Seeds sample rich text only when it is small enough for native EditText layout. Oversized
     * samples are skipped so the screen still opens as an editable rich-text surface.
     */
    private fun seedDefaultContent(editorView: RichTextEditorView) {
        if (editorView.text?.isNotEmpty() == true) return
        val default = runCatching {
            assets.open(DEFAULT_CONTENT_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (default.isNullOrEmpty() || default.length > MAX_EDITABLE_LENGTH) {
            return
        }
        editorView.editor.setText(default)
    }

    /** Returns whether the example JSON was loaded into the editor; the mode flip is caller's. */
    private fun loadExampleJson(): Boolean {
        val loaded = runCatching {
            val rawJson = assets.open(EXAMPLE_JSON_ASSET).bufferedReader().use { it.readText() }
            val html = JSONObject(rawJson).optString(EXAMPLE_JSON_HTML_KEY)
            if (html.isBlank()) return@runCatching false
            val editor = requireNotNull(richEditorView).editor
            editor.setText(makeHtmlEditable(html))
            editor.setSelection(editor.text?.length ?: 0)
            true
        }.getOrDefault(false)

        ThemedMessage.makeText(
            this,
            if (loaded) R.string.rich_text_example_json_loaded else R.string.rich_text_example_json_failed,
            ThemedMessage.LENGTH_SHORT
        ).show()
        return loaded
    }

    private fun buildPreviewHtml(): String {
        val editable = richEditorView?.text ?: return wrapHtml("")

        if (richEditorView?.isMarkdownMode == true) {
            val content = RichTextEditorView.processMarkdown(editable.toString())
            return wrapHtml(content)
        }

        // Pure plain text (no formatting spans, no HTML tags) is escaped so metacharacters render
        // literally and cannot inject markup. Content with toolbar spans or typed tags keeps the
        // span-serializing path below.
        val plain = editable.toString()
        val hasSpans = editable.getSpans(0, editable.length, Any::class.java)
            .any { editable.getSpanFlags(it) and Spanned.SPAN_COMPOSING == 0 }
        if (!hasSpans && !CONTAINS_HTML_TAG.containsMatchIn(plain)) {
            return wrapHtml(RichTextEditorView.plainTextToHtml(plain))
        }

        @Suppress("DEPRECATION")
        var content = Html.toHtml(editable, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)

        content = content
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        return wrapHtml(content)
    }

    private fun wrapHtml(body: String): String {
        val clickableBody = RichTextEditorView.makeImagesClickable(body)
        val accentHex = "#%06X".format(accentArgb and 0xFFFFFF)
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    background-color: #0F1118;
                    color: #CDD2E0;
                    font-family: monospace;
                    font-size: 14px;
                    line-height: 1.6;
                    padding: 16px;
                    margin: 0;
                    word-wrap: break-word;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: $accentHex;
                    margin: 12px 0 6px 0;
                }
                a {
                    color: #55AAFF;
                    text-decoration: underline;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    border-radius: 4px;
                    margin: 8px 0;
                }
                code {
                    background: #1E2233;
                    padding: 2px 6px;
                    border-radius: 3px;
                    color: $accentHex;
                    font-size: 13px;
                }
                pre {
                    background: #1E2233;
                    padding: 12px;
                    border-radius: 6px;
                    overflow-x: auto;
                }
                hr {
                    border: none;
                    border-top: 1px solid ${accentHex}44;
                    margin: 12px 0;
                }
                li {
                    margin: 4px 0;
                    margin-left: 16px;
                }
                b, strong { color: #FFFFFF; }
            </style>
            </head>
            <body>$clickableBody</body>
            </html>
        """.trimIndent()
    }

    private fun openImageDetails(imageTapUrl: String) {
        val src = RichTextEditorView.extractImageSrcFromTapUrl(imageTapUrl) ?: return
        val name = if (DataUriUtils.isBase64DataUri(src)) {
            "image.${DataUriUtils.fileExtension(src)}"
        } else {
            src.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "image"
        }
        val item = SupperBannerItem(
            name = name,
            description = src,
            image = SupperBannerImage.Network(src)
        )
        startActivity(ShowImageDetailsActivity.createIntent(this, item))
    }

    companion object {
        private const val DEFAULT_CONTENT_ASSET = "rich_text_default.html"
        private const val EXAMPLE_JSON_ASSET = "example.json"
        private const val EXAMPLE_JSON_HTML_KEY = "sectDesc"

        // Content above this length is not seeded into the editor. Editing very large content
        // (e.g. a long unbreakable base64 image token) in an EditText makes native text layout
        // allocate enormous buffers and the process gets OOM-killed.
        private const val MAX_EDITABLE_LENGTH = 100_000

        // Heuristic: does the text contain an HTML tag (e.g. <b>, <img ...>)? If so, treat it as
        // authored HTML; otherwise escape it as plain text.
        private val CONTAINS_HTML_TAG = Regex("<[a-zA-Z/][^>]*>")
        private val DATA_IMAGE_TAG = Regex(
            pattern = "<img\\b[^>]*\\bsrc\\s*=\\s*([\"'])data:image/[^\"']+\\1[^>]*>",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        fun makeHtmlEditable(html: String): String {
            return DATA_IMAGE_TAG.replace(html, "<span>[embedded image omitted]</span>")
        }
    }
}

@Composable
internal fun RichTextEditorScreen(
    editMode: Boolean,
    previewHtml: String,
    onBack: () -> Unit,
    onSwitchToEdit: () -> Unit,
    onSwitchToPreview: () -> Unit,
    onLoadExampleJson: () -> Unit,
    onEditorCreated: (RichTextEditorView) -> Unit,
    onEditorMessage: (CharSequence) -> Unit,
    onPreviewWebViewCreated: (WebView) -> Unit,
    onPreviewWebViewDestroyed: () -> Unit,
    onImageTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        EditorHeader(onBack = onBack)
        NeonDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Captured once at factory time; the editor keeps it across later theme
            // changes (same staleness as the pre-migration accentArgb capture).
            val editorAccent = MaterialTheme.colorScheme.primary.toArgb()
            // The editor stays composed underneath the preview so drafts (text + spans)
            // survive preview round-trips, mirroring the XML GONE/VISIBLE switching.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    RichTextEditorView(context).apply {
                        onMessage = onEditorMessage
                        markdownActiveColor = editorAccent
                        onEditorCreated(this)
                    }
                }
            )

            if (!editMode) {
                PreviewWebView(
                    html = previewHtml,
                    onImageTap = onImageTap,
                    onCreated = onPreviewWebViewCreated,
                    onDestroyed = onPreviewWebViewDestroyed,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        NeonDivider()

        EditorModeBar(
            editMode = editMode,
            onSwitchToEdit = onSwitchToEdit,
            onSwitchToPreview = onSwitchToPreview,
            onLoadExampleJson = onLoadExampleJson
        )
    }
}

@Composable
private fun EditorHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
                .padding(start = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.rich_text_editor_title),
            modifier = Modifier.align(Alignment.Center),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@Composable
private fun PreviewWebView(
    html: String,
    onImageTap: (String) -> Unit,
    onCreated: (WebView) -> Unit,
    onDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(BackgroundDark.toArgb())
                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let {
                            if (RichTextEditorView.isImageTapUrl(it)) {
                                onImageTap(it)
                                return true
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                        return true
                    }
                }
                onCreated(this)
                webView = this
            }
        }
    )

    LaunchedEffect(webView, html) {
        webView?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    DisposableEffect(Unit) {
        onDispose { onDestroyed() }
    }
}

@Composable
private fun EditorModeBar(
    editMode: Boolean,
    onSwitchToEdit: () -> Unit,
    onSwitchToPreview: () -> Unit,
    onLoadExampleJson: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HeaderBackground)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeButton(
            label = stringResource(R.string.rich_text_mode_edit),
            active = editMode,
            onClick = onSwitchToEdit,
            modifier = Modifier
                .weight(1f)
                .testTag("btn_edit_mode")
        )
        ModeButton(
            label = stringResource(R.string.rich_text_load_example_json),
            active = false,
            onClick = onLoadExampleJson,
            modifier = Modifier
                .weight(1f)
                .testTag("btn_load_example_json")
        )
        ModeButton(
            label = stringResource(R.string.rich_text_mode_preview),
            active = !editMode,
            onClick = onSwitchToPreview,
            modifier = Modifier
                .weight(1f)
                .testTag("btn_preview_mode")
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) AccentGreen else ModeInactiveColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1118, widthDp = 412, heightDp = 892)
@Composable
private fun RichTextEditorScreenPreview() {
    AircraftTheme {
        RichTextEditorScreen(
            editMode = true,
            previewHtml = "",
            onBack = {},
            onSwitchToEdit = {},
            onSwitchToPreview = {},
            onLoadExampleJson = {},
            onEditorCreated = {},
            onEditorMessage = {},
            onPreviewWebViewCreated = {},
            onPreviewWebViewDestroyed = {},
            onImageTap = {}
        )
    }
}
