package com.young.aircraft.gui

import org.junit.Assert.*
import org.junit.Test

class RichTextMarkdownTest {

    // ── Headers ──────────────────────────────────────────────

    @Test
    fun `h1 header is converted`() {
        val result = RichTextEditorActivity.processMarkdown("# Hello")
        assertTrue(result.contains("<h1>Hello</h1>"))
    }

    @Test
    fun `h2 header is converted`() {
        val result = RichTextEditorActivity.processMarkdown("## Sub heading")
        assertTrue(result.contains("<h2>Sub heading</h2>"))
    }

    @Test
    fun `h3 through h6 headers are converted`() {
        val input = "### H3\n#### H4\n##### H5\n###### H6"
        val result = RichTextEditorActivity.processMarkdown(input)
        assertTrue(result.contains("<h3>H3</h3>"))
        assertTrue(result.contains("<h4>H4</h4>"))
        assertTrue(result.contains("<h5>H5</h5>"))
        assertTrue(result.contains("<h6>H6</h6>"))
    }

    @Test
    fun `text without hash is not converted to header`() {
        val result = RichTextEditorActivity.processMarkdown("no header here")
        assertFalse(result.contains("<h1>"))
        assertFalse(result.contains("<h2>"))
    }

    // ── Bold / Italic / Strikethrough ────────────────────────

    @Test
    fun `double asterisks produce bold`() {
        val result = RichTextEditorActivity.processMarkdown("**bold**")
        assertTrue(result.contains("<b>bold</b>"))
    }

    @Test
    fun `single asterisks produce italic`() {
        val result = RichTextEditorActivity.processMarkdown("*italic*")
        assertTrue(result.contains("<i>italic</i>"))
    }

    @Test
    fun `double tildes produce strikethrough`() {
        val result = RichTextEditorActivity.processMarkdown("~~deleted~~")
        assertTrue(result.contains("<s>deleted</s>"))
    }

    @Test
    fun `bold and italic in same line`() {
        val result = RichTextEditorActivity.processMarkdown("**bold** and *italic*")
        assertTrue(result.contains("<b>bold</b>"))
        assertTrue(result.contains("<i>italic</i>"))
    }

    // ── Inline code ──────────────────────────────────────────

    @Test
    fun `backtick produces inline code`() {
        val result = RichTextEditorActivity.processMarkdown("`code`")
        assertTrue(result.contains("<code"))
        assertTrue(result.contains("code</code>"))
    }

    // ── Code blocks ──────────────────────────────────────────

    @Test
    fun `triple backticks produce code block`() {
        val result = RichTextEditorActivity.processMarkdown("```\nval x = 1\n```")
        assertTrue(result.contains("<pre"))
        assertTrue(result.contains("<code"))
        assertTrue(result.contains("val x = 1"))
    }

    // ── Images ───────────────────────────────────────────────

    @Test
    fun `markdown image is converted to img tag`() {
        val result = RichTextEditorActivity.processMarkdown("![alt text](https://example.com/pic.png)")
        assertTrue(result.contains("<img src=\"https://example.com/pic.png\""))
        assertTrue(result.contains("alt=\"alt text\""))
    }

    @Test
    fun `image with empty alt text`() {
        val result = RichTextEditorActivity.processMarkdown("![](https://example.com/pic.png)")
        assertTrue(result.contains("<img src=\"https://example.com/pic.png\""))
    }

    // ── Links ────────────────────────────────────────────────

    @Test
    fun `markdown link is converted to anchor tag`() {
        val result = RichTextEditorActivity.processMarkdown("[Click here](https://example.com)")
        assertTrue(result.contains("<a href=\"https://example.com\""))
        assertTrue(result.contains("Click here</a>"))
    }

    // ── Horizontal rule ──────────────────────────────────────

    @Test
    fun `three dashes produce horizontal rule`() {
        val result = RichTextEditorActivity.processMarkdown("above\n---\nbelow")
        assertTrue(result.contains("<hr"))
    }

    @Test
    fun `five dashes also produce horizontal rule`() {
        val result = RichTextEditorActivity.processMarkdown("-----")
        assertTrue(result.contains("<hr"))
    }

    // ── Unordered list ───────────────────────────────────────

    @Test
    fun `dash items produce list items`() {
        val result = RichTextEditorActivity.processMarkdown("- item one\n- item two")
        assertTrue(result.contains("<li>item one</li>"))
        assertTrue(result.contains("<li>item two</li>"))
    }

    // ── Line breaks ──────────────────────────────────────────

    @Test
    fun `newlines are converted to br tags`() {
        val result = RichTextEditorActivity.processMarkdown("line1\nline2")
        assertTrue(result.contains("<br>"))
    }

    // ── Mixed content ────────────────────────────────────────

    @Test
    fun `multiline document with mixed markdown`() {
        val input = """
            # Title
            Some **bold** and *italic* text.
            - first
            - second
            ---
            [link](https://example.com)
        """.trimIndent()
        val result = RichTextEditorActivity.processMarkdown(input)
        assertTrue(result.contains("<h1>Title</h1>"))
        assertTrue(result.contains("<b>bold</b>"))
        assertTrue(result.contains("<i>italic</i>"))
        assertTrue(result.contains("<li>first</li>"))
        assertTrue(result.contains("<li>second</li>"))
        assertTrue(result.contains("<hr"))
        assertTrue(result.contains("<a href=\"https://example.com\""))
    }

    // ── Edge cases ───────────────────────────────────────────

    @Test
    fun `empty input returns empty with br`() {
        val result = RichTextEditorActivity.processMarkdown("")
        assertEquals("", result)
    }

    @Test
    fun `plain text without markdown passes through`() {
        val result = RichTextEditorActivity.processMarkdown("hello world")
        assertTrue(result.contains("hello world"))
        assertFalse(result.contains("<h1>"))
        assertFalse(result.contains("<b>"))
    }

    @Test
    fun `hash without space is not a header`() {
        val result = RichTextEditorActivity.processMarkdown("#noheader")
        assertFalse(result.contains("<h1>"))
    }

    @Test
    fun `image link is not confused with regular link`() {
        val input = "![img](pic.png) and [link](url)"
        val result = RichTextEditorActivity.processMarkdown(input)
        assertTrue(result.contains("<img src=\"pic.png\""))
        assertTrue(result.contains("<a href=\"url\""))
    }
}
