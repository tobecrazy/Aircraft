package com.young.aircraft.gui

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityRichTextEditorBinding
import com.young.aircraft.utils.DataUriUtils
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.RichTextEditorViewModel
import com.young.richtext.RichTextEditorView
import org.json.JSONObject

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRichTextEditorBinding
    private lateinit var viewModel: RichTextEditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this, RichTextEditorViewModel.Factory())[RichTextEditorViewModel::class.java]

        binding = ActivityRichTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        Toast.makeText(
            this,
            if (loaded) R.string.rich_text_example_json_loaded else R.string.rich_text_example_json_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun switchToEditMode() {
        viewModel.switchToEditMode()
        binding.btnEditMode.setTextColor("#00FF88".toColorInt())
        binding.btnPreviewMode.setTextColor("#66FFFFFF".toColorInt())
        binding.richEditor.visibility = View.VISIBLE
        binding.wvPreview.visibility = View.GONE
    }

    private fun switchToPreviewMode() {
        val html = buildPreviewHtml()

        viewModel.switchToPreviewMode()
        binding.btnEditMode.setTextColor("#66FFFFFF".toColorInt())
        binding.btnPreviewMode.setTextColor("#00FF88".toColorInt())
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
        binding.wvPreview.setBackgroundColor("#0F1118".toColorInt())
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
                    color: #00FF88;
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
                    color: #00FF88;
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
                    border-top: 1px solid #4400FF88;
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
