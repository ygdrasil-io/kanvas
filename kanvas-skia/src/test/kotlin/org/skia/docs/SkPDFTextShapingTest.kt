package org.skia.docs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.skia.tools.ToolUtils

/**
 * Phase R-suivi.36 — verify that [SkPDF] routes
 * [org.skia.core.SkCanvas.drawString] through
 * [SkFont.makeTextPath] and emits real vector-outline operators
 * (`m` / `l` / `c`) in the page's content stream.
 */
class SkPDFTextShapingTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `drawString emits cubic curves in the content stream`() {
        val face = ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal())
        val font = SkFont(face, 24f)

        val stream = SkDynamicMemoryWStream()
        // Compression off so the operator stream is greppable as plain
        // ASCII (the operators are still there under FlateDecode, just
        // packed into binary — separate test exercises that path).
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(200f, 100f)
        c.drawString("hello", 10f, 40f, font, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(
            text.contains(" c\n"),
            "Expected glyph outlines to emit `c` cubic operators ; got first 400 bytes:\n${text.take(400)}",
        )
        // A glyph outline must also seed at least one `m` move-to.
        assertTrue(
            text.contains(" m\n"),
            "Expected glyph outlines to emit at least one `m` moveTo operator",
        )
    }

    @Test
    fun `drawString with empty typeface stays a content-stream no-op`() {
        // Empty typeface has no glyph paths — drawString must remain a
        // graceful no-op without leaving operator litter in the stream.
        val font = SkFont(org.skia.foundation.SkTypeface.MakeEmpty(), 24f)
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(50f, 50f)
        c.drawString("hello", 5f, 30f, font, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        // No path verbs in the page content stream.
        assertTrue(
            !text.contains(" c\n") && !text.contains(" m\n"),
            "Empty typeface must not produce path operators",
        )
    }
}
