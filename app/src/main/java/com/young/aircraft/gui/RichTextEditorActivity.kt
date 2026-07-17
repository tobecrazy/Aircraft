package com.young.aircraft.gui

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityRichTextEditorBinding
import com.young.aircraft.ui.RichTextEditorView
import com.young.aircraft.utils.DataUriUtils
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.RichTextEditorViewModel
import androidx.core.graphics.toColorInt

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRichTextEditorBinding
    private lateinit var viewModel: RichTextEditorViewModel

    /**
     * Raw HTML that is too large to edit safely (a huge unbreakable base64 token would make the
     * EditText's native text layout allocate gigabytes and get OOM-killed). When set, the content
     * is preview-only: it is rendered by the WebView and never placed in the EditText.
     */
    private var previewOnlyHtml: String? = null

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

        // Preview-only content must never reach the EditText; otherwise open in the saved mode.
        if (previewOnlyHtml != null || !viewModel.isEditMode) {
            switchToPreviewMode()
        }
    }

    /**
     * Seeds the sample rich-text (HTML with an embedded base64 image). Content larger than
     * [MAX_EDITABLE_LENGTH] is held as [previewOnlyHtml] and never placed in the EditText, since a
     * single unbreakable base64 token would blow up the text layout engine. Smaller content is
     * loaded into the editor as before.
     */
    private fun loadDefaultContent() {
        if (binding.richEditor.text?.isNotEmpty() == true) return
        val default = runCatching {
            assets.open(DEFAULT_CONTENT_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (default.isNullOrEmpty()) return
        if (default.length > MAX_EDITABLE_LENGTH) {
            previewOnlyHtml = default
        } else {
            binding.richEditor.editor.setText(default)
        }
    }

    private fun setupModeToggle() {
        binding.btnEditMode.setOnClickListener { switchToEditMode() }
        binding.btnPreviewMode.setOnClickListener { switchToPreviewMode() }
    }

    private fun switchToEditMode() {
        // Large preview-only content cannot be edited safely — keep it in preview.
        if (previewOnlyHtml != null) {
            Toast.makeText(this, R.string.rich_text_preview_only, Toast.LENGTH_SHORT).show()
            return
        }
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
        // Preview-only content is treated as raw HTML (it never passed through the editor).
        previewOnlyHtml?.let { return wrapHtml(it) }

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

        // Content above this length is rendered preview-only. Editing very large content (e.g. a
        // long unbreakable base64 image token) in an EditText makes native text layout allocate
        // enormous buffers and the process gets OOM-killed.
        private const val MAX_EDITABLE_LENGTH = 100_000

        // Heuristic: does the text contain an HTML tag (e.g. <b>, <img ...>)? If so, treat it as
        // authored HTML; otherwise escape it as plain text.
        private val CONTAINS_HTML_TAG = Regex("<[a-zA-Z/][^>]*>")
    }
}
