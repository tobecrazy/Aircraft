package com.young.aircraft.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.young.aircraft.R

class RichTextEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val btnBold: View
    private val btnItalic: View
    private val btnUnderline: View
    private val btnSize: View
    private val btnColor: View
    private val btnMarkdown: View
    private val btnHtml: View
    val toolbarScroll: View
    val toolbarDivider: View
    val editor: EditText

    var isMarkdownMode = false
        private set

    val text: Editable?
        get() = editor.text

    val plainText: String
        get() = editor.text?.toString().orEmpty()

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_rich_text_editor, this, true)

        toolbarScroll = findViewById(R.id.rich_toolbar_scroll)
        toolbarDivider = findViewById(R.id.rich_toolbar_divider)
        editor = findViewById(R.id.rich_et_editor)
        btnBold = findViewById(R.id.rich_btn_bold)
        btnItalic = findViewById(R.id.rich_btn_italic)
        btnUnderline = findViewById(R.id.rich_btn_underline)
        btnSize = findViewById(R.id.rich_btn_size)
        btnColor = findViewById(R.id.rich_btn_color)
        btnMarkdown = findViewById(R.id.rich_btn_markdown)
        btnHtml = findViewById(R.id.rich_btn_html)

        setupUnderlineButton()
        setupFormatting()
    }

    fun setHint(hint: CharSequence) {
        editor.hint = hint
    }

    fun setEditorBackground(resId: Int) {
        editor.setBackgroundResource(resId)
    }

    fun setEditorHeight(heightPx: Int) {
        val lp = editor.layoutParams
        lp.height = heightPx
        editor.layoutParams = lp
    }

    fun setToolbarVisible(visible: Boolean) {
        val vis = if (visible) VISIBLE else GONE
        toolbarScroll.visibility = vis
        toolbarDivider.visibility = vis
    }

    // ── Formatting ─────────────────────────────────────────

    private fun setupUnderlineButton() {
        val label = SpannableStringBuilder("U")
        label.setSpan(UnderlineSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        (btnUnderline as? TextView)?.text = label
    }

    private fun setupFormatting() {
        btnBold.setOnClickListener { applySpan { StyleSpan(Typeface.BOLD) } }
        btnItalic.setOnClickListener { applySpan { StyleSpan(Typeface.ITALIC) } }
        btnUnderline.setOnClickListener { applySpan { UnderlineSpan() } }
        btnSize.setOnClickListener { showSizePopup(it) }
        btnColor.setOnClickListener { showColorPopup(it) }
        btnMarkdown.setOnClickListener { toggleMarkdownMode() }
        btnHtml.setOnClickListener { insertHtmlSnippet() }
    }

    private fun applySpan(spanFactory: () -> Any) {
        val rawStart = editor.selectionStart
        val rawEnd = editor.selectionEnd
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || start == end) {
            Toast.makeText(context, R.string.rich_text_select_text, Toast.LENGTH_SHORT).show()
            return
        }
        val spannable = editor.text as SpannableStringBuilder
        spannable.setSpan(spanFactory(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun requireSelection(): Pair<Int, Int>? {
        val start = minOf(editor.selectionStart, editor.selectionEnd)
        val end = maxOf(editor.selectionStart, editor.selectionEnd)
        if (start < 0 || start == end) {
            Toast.makeText(context, R.string.rich_text_select_text, Toast.LENGTH_SHORT).show()
            return null
        }
        return start to end
    }

    private fun showSizePopup(anchor: View) {
        val (start, end) = requireSelection() ?: return
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 0, 0, context.getString(R.string.rich_text_size_small))
        popup.menu.add(0, 1, 1, context.getString(R.string.rich_text_size_medium))
        popup.menu.add(0, 2, 2, context.getString(R.string.rich_text_size_large))
        popup.menu.add(0, 3, 3, context.getString(R.string.rich_text_size_xlarge))
        popup.setOnMenuItemClickListener { item ->
            val sizeSp = when (item.itemId) {
                0 -> 12; 1 -> 16; 2 -> 22; 3 -> 30; else -> 16
            }
            val spannable = editor.text as SpannableStringBuilder
            spannable.setSpan(
                AbsoluteSizeSpan(sizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            true
        }
        popup.show()
    }

    private fun showColorPopup(anchor: View) {
        val (start, end) = requireSelection() ?: return
        val popup = PopupMenu(context, anchor)
        val colors = listOf(
            context.getString(R.string.rich_text_color_green) to "#00FF88",
            context.getString(R.string.rich_text_color_red) to "#FF5555",
            context.getString(R.string.rich_text_color_blue) to "#55AAFF",
            context.getString(R.string.rich_text_color_yellow) to "#FFDD57",
            context.getString(R.string.rich_text_color_white) to "#FFFFFF"
        )
        colors.forEachIndexed { index, (name, _) ->
            popup.menu.add(0, index, index, name)
        }
        popup.setOnMenuItemClickListener { item ->
            val colorHex = colors[item.itemId].second
            val spannable = editor.text as SpannableStringBuilder
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor(colorHex)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            true
        }
        popup.show()
    }

    private fun toggleMarkdownMode() {
        isMarkdownMode = !isMarkdownMode
        (btnMarkdown as? TextView)?.setTextColor(
            if (isMarkdownMode) Color.parseColor("#00FF88") else Color.parseColor("#66FFFFFF")
        )
        val msg = if (isMarkdownMode) R.string.rich_text_md_on else R.string.rich_text_md_off
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun insertHtmlSnippet() {
        val popup = PopupMenu(context, btnHtml)
        popup.menu.add(0, 0, 0, context.getString(R.string.rich_text_html_image))
        popup.menu.add(0, 1, 1, context.getString(R.string.rich_text_html_link))
        popup.menu.add(0, 2, 2, context.getString(R.string.rich_text_html_heading))
        popup.setOnMenuItemClickListener { item ->
            val snippet = when (item.itemId) {
                0 -> "<img src=\"https://example.com/image.png\" width=\"200\" />"
                1 -> "<a href=\"https://example.com\">Link Text</a>"
                2 -> "<h2>Heading</h2>"
                else -> ""
            }
            val pos = editor.selectionStart
            editor.text?.insert(pos, snippet)
            true
        }
        popup.show()
    }

    companion object {
        fun processMarkdown(input: String): String {
            var result = input

            result = result.replace(
                Regex("```([\\s\\S]*?)```"),
                "<pre style=\"background:#1E2233;padding:12px;border-radius:6px;overflow-x:auto;\"><code style=\"color:#00FF88;\">$1</code></pre>"
            )
            result = result.replace(Regex("(?m)^#{6}\\s+(.+)$"), "<h6>$1</h6>")
            result = result.replace(Regex("(?m)^#{5}\\s+(.+)$"), "<h5>$1</h5>")
            result = result.replace(Regex("(?m)^#{4}\\s+(.+)$"), "<h4>$1</h4>")
            result = result.replace(Regex("(?m)^#{3}\\s+(.+)$"), "<h3>$1</h3>")
            result = result.replace(Regex("(?m)^#{2}\\s+(.+)$"), "<h2>$1</h2>")
            result = result.replace(Regex("(?m)^#\\s+(.+)$"), "<h1>$1</h1>")
            result = result.replace(Regex("(?m)^-{3,}$"), "<hr style=\"border-color:#4400FF88;\" />")
            result = result.replace(Regex("(?m)^-\\s+(.+)$"), "<li>$1</li>")
            result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            result =
                result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
            result = result.replace(Regex("~~(.+?)~~"), "<s>$1</s>")
            result = result.replace(
                Regex("`([^`]+)`"),
                "<code style=\"background:#1E2233;padding:2px 6px;border-radius:3px;color:#00FF88;\">$1</code>"
            )
            result = result.replace(
                Regex("!\\[([^]]*)]\\(([^)]+)\\)"),
                "<img src=\"$2\" alt=\"$1\" style=\"max-width:100%;\" />"
            )
            result = result.replace(
                Regex("\\[([^]]+)]\\(([^)]+)\\)"),
                "<a href=\"$2\" style=\"color:#55AAFF;\">$1</a>"
            )
            result = result.replace("\n", "<br>\n")

            return result
        }
    }
}