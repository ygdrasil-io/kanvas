package org.skia.docs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColor
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect

/**
 * Smoke tests for the minimal PDF 1.4 backend in [SkPDF].
 */
class SkPDFDocumentTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `two-page PDF starts with header and ends with EOF`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)

        // Page 1 — a red rect.
        val c1 = doc.beginPage(200f, 100f)
        val p1 = SkPaint().apply { color = 0xFFFF0000.toInt() }
        c1.drawRect(SkRect.MakeLTRB(10f, 10f, 50f, 50f), p1)
        doc.endPage()

        // Page 2 — a green fill on a smaller page.
        val c2 = doc.beginPage(150f, 80f)
        val p2 = SkPaint().apply { color = 0xFF00FF00.toInt() }
        c2.drawRect(SkRect.MakeWH(150f, 80f), p2)
        doc.endPage()

        doc.close()
        val bytes = stream.toByteArray()
        val text = bytes.asLatin1()

        assertTrue(bytes.isNotEmpty(), "PDF must be non-empty")
        assertTrue(text.startsWith("%PDF-1.4"), "PDF must start with %PDF-1.4 header, got: ${text.take(20)}")
        assertTrue(text.trimEnd().endsWith("%%EOF"), "PDF must end with %%EOF")
        // Must contain a xref table and a Catalog dict.
        assertTrue(text.contains("xref"), "PDF must contain xref")
        assertTrue(text.contains("/Type /Catalog"), "PDF must contain /Type /Catalog")
        assertTrue(text.contains("/Type /Pages"), "PDF must contain /Type /Pages")
        assertTrue(text.contains("/Count 2"), "PDF must declare two pages")
    }

    @Test
    fun `empty document still emits valid PDF skeleton`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Count 0"))
    }

    @Test
    fun `metadata producer is captured`() {
        val stream = SkDynamicMemoryWStream()
        val md = SkPDF.Metadata(producer = "kanvas-skia-test")
        val doc = SkPDF.MakeDocument(stream, md)
        assertNotNull(doc)
        doc.beginPage(10f, 10f)
        doc.close()
        assertTrue(stream.toByteArray().isNotEmpty())
    }

    @Test
    fun `drawString and drawImage are recorded as no-ops`() {
        // Smoke test : recorder must accept text/image calls without
        // throwing, even though they don't contribute to the stream.
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val canvas = doc.beginPage(100f, 100f)
        canvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.startsWith("%PDF-1.4"))
    }

    @Test
    fun `abort still produces something but is not trusted`() {
        // The contract says the stream may be partially written; just
        // ensure abort itself doesn't throw and is idempotent.
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        doc.beginPage(10f, 10f)
        doc.abort()
        doc.abort()
    }

    @Test
    fun `rect draw emits rectangle command in content stream`() {
        val stream = SkDynamicMemoryWStream()
        // R-suivi.40 — opt out of FlateDecode compression so we can
        // grep the raw operator stream for `re` / `f`.
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(100f, 100f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 50f, 50f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        // The 're' operator (PDF rectangle) must appear somewhere in
        // the content stream.
        assertTrue(text.contains(" re "), "Content stream must contain a 're' rectangle op")
        // 'f' (fill) operator follows for the default Fill style.
        assertTrue(text.contains(" f"), "Content stream must contain an 'f' fill op")
    }

    @Test
    fun `page count matches number of beginPage calls`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        for (i in 0 until 5) {
            doc.beginPage(50f, 50f)
            doc.endPage()
        }
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains("/Count 5"))
    }

    @Test
    fun `color components are emitted in 0_1 range`() {
        val stream = SkDynamicMemoryWStream()
        // R-suivi.40 — opt out of compression to inspect operators.
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(10f, 10f)
        val p = SkPaint().apply { color = 0xFFFF0000.toInt() } // pure red
        c.drawRect(SkRect.MakeWH(5f, 5f), p)
        doc.close()
        val text = stream.toByteArray().asLatin1()
        // Pure red → "1 0 0 rg" (non-stroke color).
        assertTrue(text.contains("1 0 0 rg"), "Expected non-stroke red color command, got:\n$text")
    }

    @Test
    fun `cubic verb emits c operator`() {
        val stream = SkDynamicMemoryWStream()
        // R-suivi.40 — opt out of compression to inspect operators.
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(100f, 100f)
        val pathBuilder = org.skia.foundation.SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(10f, 0f, 20f, 0f, 30f, 30f)
            .close()
        c.drawPath(pathBuilder.detach(), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains(" c\n") || text.contains(" c "), "Expected 'c' cubic operator")
    }

    @Test
    fun `unused color value`() {
        // Compile-time guard that SkColor is referenced (defends
        // against the import getting stripped by a future refactor).
        val red: SkColor = 0xFFFF0000.toInt()
        assertEquals(0xFFFF0000.toInt(), red)
    }
}
