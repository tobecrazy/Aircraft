package com.young.aircraft.gui

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.databinding.ActivityRichTextEditorBinding
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.themeAccent
import com.young.aircraft.utils.DataUriUtils
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.RichTextEditorViewModel
import com.young.richtext.RichTextEditorView
import org.json.JSONObject

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRichTextEditorBinding
    private lateinit var viewModel: RichTextEditorViewModel
    private var accentArgb: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this, RichTextEditorViewModel.Factory())[RichTextEditorViewModel::class.java]

        binding = ActivityRichTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.richEditor.onMessage = { message ->
            ThemedMessage.makeText(this, message, ThemedMessage.LENGTH_SHORT).show()
        }
        applyThemeColors()

        binding.btnBack.setOnClickListener { finish() }
        setupModeToggle()
        setupWebView()

        loadDefaultContent()

        if (viewModel.isEditMode) {
            switchToEditMode()
        } else {
            switchToPreviewMode()
        }
    }

    /**
     * Seeds sample rich text only when it is small enough for native EditText layout. Oversized
     * samples are skipped so the screen still opens as an editable rich-text surface.
     */
    private fun loadDefaultContent() {
        if (binding.richEditor.text?.isNotEmpty() == true) return
        val default = runCatching {
            assets.open(DEFAULT_CONTENT_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (default.isNullOrEmpty()) return
        if (default.length > MAX_EDITABLE_LENGTH) {
            return
        }
        binding.richEditor.editor.setText(default)
    }

    private fun setupModeToggle() {
        binding.btnEditMode.setOnClickListener { switchToEditMode() }
        binding.btnPreviewMode.setOnClickListener { switchToPreviewMode() }
        binding.btnLoadExampleJson.setOnClickListener { loadExampleJson() }
    }

    private fun loadExampleJson() {
        val loaded = runCatching {
            val rawJson = assets.open(EXAMPLE_JSON_ASSET).bufferedReader().use { it.readText() }
            val html = JSONObject(rawJson).optString(EXAMPLE_JSON_HTML_KEY)
            if (html.isBlank()) return@runCatching false
            binding.richEditor.editor.setText(makeHtmlEditable(html))
            binding.richEditor.editor.setSelection(binding.richEditor.editor.text?.length ?: 0)
            switchToEditMode()
            true
        }.getOrDefault(false)

        ThemedMessage.makeText(
            this,
            if (loaded) R.string.rich_text_example_json_loaded else R.string.rich_text_example_json_failed,
            ThemedMessage.LENGTH_SHORT
        ).show()
    }

    /** Applies the persisted accent to the chrome; dark surfaces stay constant by design. */
    private fun applyThemeColors() {
        val accent = themeAccent(SettingsRepository(this).getTheme())
        accentArgb = accent.toArgb()
        binding.btnBack.setColorFilter(accentArgb)
        binding.tvHeaderTitle.setTextColor(accentArgb)
        binding.dividerTop.setBackgroundColor(accent.copy(alpha = 0x44 / 255f).toArgb())
        binding.dividerBottom.setBackgroundColor(accent.copy(alpha = 0x44 / 255f).toArgb())
        binding.richEditor.markdownActiveColor = accentArgb
        renderModeToggle()
    }

    private fun renderModeToggle() {
        binding.btnEditMode.setTextColor(
            if (viewModel.isEditMode) accentArgb else MODE_INACTIVE_COLOR
        )
        binding.btnPreviewMode.setTextColor(
            if (viewModel.isEditMode) MODE_INACTIVE_COLOR else accentArgb
        )
    }

    private fun switchToEditMode() {
        viewModel.switchToEditMode()
        renderModeToggle()
        binding.richEditor.visibility = View.VISIBLE
        binding.wvPreview.visibility = View.GONE
    }

    private fun switchToPreviewMode() {
        val html = buildPreviewHtml()

        viewModel.switchToPreviewMode()
        renderModeToggle()
        binding.richEditor.visibility = View.GONE
        binding.wvPreview.visibility = View.VISIBLE

        binding.wvPreview.post {
            binding.wvPreview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        binding.wvPreview.settings.javaScriptEnabled = false
        binding.wvPreview.settings.loadWithOverviewMode = true
        binding.wvPreview.settings.useWideViewPort = true
        binding.wvPreview.setBackgroundColor(BackgroundDark.toArgb())
        binding.wvPreview.setWebViewClient(object : android.webkit.WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let {
                    if (RichTextEditorView.isImageTapUrl(it)) {
                        openImageDetails(it)
                        return true
                    }
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it)
                    )
                    startActivity(intent)
                }
                return true
            }
        })
    }

    private fun buildPreviewHtml(): String {
        val editable = binding.richEditor.text ?: return wrapHtml("")

        if (binding.richEditor.isMarkdownMode) {
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
        private const val MODE_INACTIVE_COLOR = 0x66FFFFFF

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
