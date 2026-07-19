package com.young.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextEditorViewTest {

    @Test
    fun `markdown image and link are converted to html`() {
        val result = RichTextEditorView.processMarkdown(
            "![alt text](https://example.com/pic.png)\n[site](https://example.com)"
        )

        assertTrue(result.contains("<img src=\"https://example.com/pic.png\""))
        assertTrue(result.contains("alt=\"alt text\""))
        assertTrue(result.contains("<a href=\"https://example.com\""))
    }

    @Test
    fun `plain text escapes tags and links bare urls`() {
        val result = RichTextEditorView.plainTextToHtml("<b>x</b> https://example.com")

        assertTrue(result.contains("&lt;b&gt;x&lt;/b&gt;"))
        assertFalse(result.contains("<b>x</b>"))
        assertTrue(result.contains("<a href=\"https://example.com\""))
    }

    @Test
    fun `image tap url round trips source`() {
        val source = "https://example.com/assets/pic one.png?size=lg"
        val url = RichTextEditorView.buildImageTapUrl(source)

        assertTrue(RichTextEditorView.isImageTapUrl(url))
        assertEquals(source, RichTextEditorView.extractImageSrcFromTapUrl(url))
    }

    @Test
    fun `preview image format support is conservative`() {
        assertTrue(RichTextEditorView.isImageFormatSupportedForPreview("photo.JPG"))
        assertTrue(RichTextEditorView.isImageFormatSupportedForPreview("https://example.com/logo.svg?v=1"))
        assertFalse(RichTextEditorView.isImageFormatSupportedForPreview("photo.heic"))
        assertFalse(RichTextEditorView.isImageFormatSupportedForPreview("image.avif"))
    }
}
