package org.skia.docs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.skia.math.SkRect

/**
 * Phase R-suivi.40 — verifies that [SkPDF] honours
 * [SkPDF.Metadata.compress] by deflating the content stream and
 * emitting a `/Filter /FlateDecode` filter entry in the dictionary.
 */
class SkPDFCompressionTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    private fun renderRectsPdf(compress: Boolean): ByteArray {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = compress))
        val c = doc.beginPage(400f, 400f)
        // Many rects → a fat content stream so compression has
        // something meaningful to shrink.
        for (i in 0 until 200) {
            val p = SkPaint().apply { color = (0xFF000000.toInt() or i) }
            c.drawRect(SkRect.MakeXYWH(i.toFloat(), i.toFloat(), 5f, 5f), p)
        }
        doc.close()
        return stream.toByteArray()
    }

    @Test
    fun `compressed content stream declares FlateDecode and shrinks the output`() {
        val compressed = renderRectsPdf(compress = true)
        val uncompressed = renderRectsPdf(compress = false)
        val cText = compressed.asLatin1()
        val uText = uncompressed.asLatin1()

        assertTrue(
            cText.contains("/Filter /FlateDecode"),
            "Compressed PDF must declare /Filter /FlateDecode",
        )
        assertTrue(
            !uText.contains("/FlateDecode"),
            "Uncompressed PDF must not declare /FlateDecode",
        )
        assertTrue(
            compressed.size < uncompressed.size,
            "Compressed PDF (${compressed.size} B) must be smaller than uncompressed (${uncompressed.size} B)",
        )
    }

    @Test
    fun `compression preserves header and EOF marker`() {
        val bytes = renderRectsPdf(compress = true)
        val text = bytes.asLatin1()
        assertTrue(text.startsWith("%PDF-1.4"), "Header must survive compression")
        assertTrue(text.trimEnd().endsWith("%%EOF"), "EOF marker must survive compression")
    }
}
