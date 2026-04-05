package com.young.aircraft.gui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityRichTextEditorBinding

class RichTextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRichTextEditorBinding
    private var isEditMode = true
    private var isMarkdownMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        binding = ActivityRichTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUnderlineButton()
        setupToolbar()
        setupModeToggle()
        setupFormatting()
        setupWebView()
    }

    private fun setupUnderlineButton() {
        val text = SpannableStringBuilder("U")
        text.setSpan(UnderlineSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.btnUnderline.text = text
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupModeToggle() {
        binding.btnEditMode.setOnClickListener { switchToEditMode() }
        binding.btnPreviewMode.setOnClickListener { switchToPreviewMode() }
    }

    private fun switchToEditMode() {
        isEditMode = true
        binding.btnEditMode.setTextColor(Color.parseColor("#00FF88"))
        binding.btnPreviewMode.setTextColor(Color.parseColor("#66FFFFFF"))
        binding.etEditor.visibility = View.VISIBLE
        binding.toolbarScroll.visibility = View.VISIBLE
        binding.toolbarDivider.visibility = View.VISIBLE
        binding.wvPreview.visibility = View.GONE
    }

    private fun switchToPreviewMode() {
        // Build HTML while EditText is still visible
        val html = buildPreviewHtml()

        isEditMode = false
        binding.btnEditMode.setTextColor(Color.parseColor("#66FFFFFF"))
        binding.btnPreviewMode.setTextColor(Color.parseColor("#00FF88"))
        binding.etEditor.visibility = View.GONE
        binding.toolbarScroll.visibility = View.GONE
        binding.toolbarDivider.visibility = View.GONE
        binding.wvPreview.visibility = View.VISIBLE

        // Post to ensure WebView is laid out before loading content
        binding.wvPreview.post {
            binding.wvPreview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    private fun setupFormatting() {
        binding.btnBold.setOnClickListener { applySpan { StyleSpan(Typeface.BOLD) } }
        binding.btnItalic.setOnClickListener { applySpan { StyleSpan(Typeface.ITALIC) } }
        binding.btnUnderline.setOnClickListener { applySpan { UnderlineSpan() } }
        binding.btnSize.setOnClickListener { showSizePopup(it) }
        binding.btnColor.setOnClickListener { showColorPopup(it) }
        binding.btnMarkdown.setOnClickListener { toggleMarkdownMode() }
        binding.btnHtml.setOnClickListener { insertHtmlSnippet() }
    }

    private fun applySpan(spanFactory: () -> Any) {
        val rawStart = binding.etEditor.selectionStart
        val rawEnd = binding.etEditor.selectionEnd
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || start == end) {
            Toast.makeText(this, R.string.rich_text_select_text, Toast.LENGTH_SHORT).show()
            return
        }
        val spannable = binding.etEditor.text as SpannableStringBuilder
        spannable.setSpan(spanFactory(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showSizePopup(anchor: View) {
        val start = minOf(binding.etEditor.selectionStart, binding.etEditor.selectionEnd)
        val end = maxOf(binding.etEditor.selectionStart, binding.etEditor.selectionEnd)
        if (start < 0 || start == end) {
            Toast.makeText(this, R.string.rich_text_select_text, Toast.LENGTH_SHORT).show()
            return
        }
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, getString(R.string.rich_text_size_small))
        popup.menu.add(0, 1, 1, getString(R.string.rich_text_size_medium))
        popup.menu.add(0, 2, 2, getString(R.string.rich_text_size_large))
        popup.menu.add(0, 3, 3, getString(R.string.rich_text_size_xlarge))
        popup.setOnMenuItemClickListener { item ->
            val sizeSp = when (item.itemId) {
                0 -> 12
                1 -> 16
                2 -> 22
                3 -> 30
                else -> 16
            }
            val spannable = binding.etEditor.text as SpannableStringBuilder
            spannable.setSpan(AbsoluteSizeSpan(sizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            true
        }
        popup.show()
    }

    private fun showColorPopup(anchor: View) {
        val start = minOf(binding.etEditor.selectionStart, binding.etEditor.selectionEnd)
        val end = maxOf(binding.etEditor.selectionStart, binding.etEditor.selectionEnd)
        if (start < 0 || start == end) {
            Toast.makeText(this, R.string.rich_text_select_text, Toast.LENGTH_SHORT).show()
            return
        }
        val popup = PopupMenu(this, anchor)
        val colors = listOf(
            getString(R.string.rich_text_color_green) to "#00FF88",
            getString(R.string.rich_text_color_red) to "#FF5555",
            getString(R.string.rich_text_color_blue) to "#55AAFF",
            getString(R.string.rich_text_color_yellow) to "#FFDD57",
            getString(R.string.rich_text_color_white) to "#FFFFFF"
        )
        colors.forEachIndexed { index, (name, _) ->
            popup.menu.add(0, index, index, name)
        }
        popup.setOnMenuItemClickListener { item ->
            val colorHex = colors[item.itemId].second
            val spannable = binding.etEditor.text as SpannableStringBuilder
            spannable.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            true
        }
        popup.show()
    }

    private fun toggleMarkdownMode() {
        isMarkdownMode = !isMarkdownMode
        binding.btnMarkdown.setTextColor(
            if (isMarkdownMode) Color.parseColor("#00FF88") else Color.parseColor("#66FFFFFF")
        )
        val msg = if (isMarkdownMode) R.string.rich_text_md_on else R.string.rich_text_md_off
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun insertHtmlSnippet() {
        val popup = PopupMenu(this, binding.btnHtml)
        popup.menu.add(0, 0, 0, getString(R.string.rich_text_html_image))
        popup.menu.add(0, 1, 1, getString(R.string.rich_text_html_link))
        popup.menu.add(0, 2, 2, getString(R.string.rich_text_html_heading))
        popup.setOnMenuItemClickListener { item ->
            val snippet = when (item.itemId) {
                0 -> "<img src=\"https://example.com/image.png\" width=\"200\" />"
                1 -> "<a href=\"https://example.com\">Link Text</a>"
                2 -> "<h2>Heading</h2>"
                else -> ""
            }
            val pos = binding.etEditor.selectionStart
            binding.etEditor.text?.insert(pos, snippet)
            true
        }
        popup.show()
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        binding.wvPreview.settings.javaScriptEnabled = false
        binding.wvPreview.settings.loadWithOverviewMode = true
        binding.wvPreview.settings.useWideViewPort = true
        binding.wvPreview.setBackgroundColor(Color.parseColor("#0F1118"))
        binding.wvPreview.setWebViewClient(object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it))
                    startActivity(intent)
                }
                return true
            }
        })
    }

    private fun buildPreviewHtml(): String {
        val editable = binding.etEditor.text ?: return wrapHtml("")

        if (isMarkdownMode) {
            // Markdown mode: process the raw plain text directly.
            // Html.toHtml() wraps lines in <p dir="ltr">...</p> which breaks
            // line-anchored Markdown regex (headers, lists, hr).
            val content = processMarkdown(editable.toString())
            return wrapHtml(content)
        }

        // Rich-text mode: convert spans (Bold, Italic, etc.) to HTML tags
        @Suppress("DEPRECATION")
        var content = Html.toHtml(editable, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)

        // Unescape HTML entities so user-typed HTML tags are rendered by the WebView.
        // Literal '&' in the original text was double-escaped to '&amp;amp;' by Html.toHtml(),
        // so this replacement chain is safe and won't corrupt it.
        content = content
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        return wrapHtml(content)
    }

    private fun processMarkdown(input: String): String {
        var result = input

        // Escape bare & and < that are NOT part of user-written HTML tags,
        // so they don't break the final HTML. Skip this for characters inside tags.
        // (User-written HTML tags like <img>, <a> are preserved as-is.)

        // Code blocks: ```...```
        result = result.replace(Regex("```([\\s\\S]*?)```"),
            "<pre style=\"background:#1E2233;padding:12px;border-radius:6px;overflow-x:auto;\"><code style=\"color:#00FF88;\">$1</code></pre>")

        // Headers: # through ######
        result = result.replace(Regex("(?m)^#{6}\\s+(.+)$"), "<h6>$1</h6>")
        result = result.replace(Regex("(?m)^#{5}\\s+(.+)$"), "<h5>$1</h5>")
        result = result.replace(Regex("(?m)^#{4}\\s+(.+)$"), "<h4>$1</h4>")
        result = result.replace(Regex("(?m)^#{3}\\s+(.+)$"), "<h3>$1</h3>")
        result = result.replace(Regex("(?m)^#{2}\\s+(.+)$"), "<h2>$1</h2>")
        result = result.replace(Regex("(?m)^#\\s+(.+)$"), "<h1>$1</h1>")

        // Horizontal rule: --- (must come before list processing)
        result = result.replace(Regex("(?m)^-{3,}$"), "<hr style=\"border-color:#4400FF88;\" />")

        // Unordered list: - item
        result = result.replace(Regex("(?m)^-\\s+(.+)$"), "<li>$1</li>")

        // Bold: **text**
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")

        // Italic: *text*
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")

        // Strikethrough: ~~text~~
        result = result.replace(Regex("~~(.+?)~~"), "<s>$1</s>")

        // Inline code: `code`
        result = result.replace(Regex("`([^`]+)`"),
            "<code style=\"background:#1E2233;padding:2px 6px;border-radius:3px;color:#00FF88;\">$1</code>")

        // Images: ![alt](url)
        result = result.replace(Regex("!\\[([^]]*)]\\(([^)]+)\\)"),
            "<img src=\"$2\" alt=\"$1\" style=\"max-width:100%;\" />")

        // Links: [text](url)
        result = result.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"),
            "<a href=\"$2\" style=\"color:#55AAFF;\">$1</a>")

        // Convert remaining newlines to <br> for proper line breaks
        result = result.replace("\n", "<br>\n")

        return result
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
