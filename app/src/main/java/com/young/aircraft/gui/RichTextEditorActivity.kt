package com.young.aircraft.gui

import android.os.Bundle
import android.text.Html
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.databinding.ActivityRichTextEditorBinding
import com.young.aircraft.ui.RichTextEditorView
import androidx.core.graphics.toColorInt

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRichTextEditorBinding
    private var isEditMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        binding = ActivityRichTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        setupModeToggle()
        setupWebView()
    }

    private fun setupModeToggle() {
        binding.btnEditMode.setOnClickListener { switchToEditMode() }
        binding.btnPreviewMode.setOnClickListener { switchToPreviewMode() }
    }

    private fun switchToEditMode() {
        isEditMode = true
        binding.btnEditMode.setTextColor("#00FF88".toColorInt())
        binding.btnPreviewMode.setTextColor("#66FFFFFF".toColorInt())
        binding.richEditor.visibility = View.VISIBLE
        binding.wvPreview.visibility = View.GONE
    }

    private fun switchToPreviewMode() {
        val html = buildPreviewHtml()

        isEditMode = false
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
            <body>$body</body>
            </html>
        """.trimIndent()
    }
}
