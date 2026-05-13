package org.skia.docs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.stream.SkDynamicMemoryWStream

/**
 * Phase R-suivi.37 — verify that [SkPDF.MakeDocument] embeds images
 * passed to `drawImage` as JPEG `/DCTDecode` XObjects.
 */
class SkPDFImagesTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `drawImage embeds a JPEG-encoded XObject in the PDF body`() {
        // Build a 4×4 RGBA bitmap with a simple gradient pattern so
        // the JPEG encoder has actual content to compress (a single
        // flat colour would still encode but is less convincing).
        val bmp = SkBitmap(4, 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val r = (x * 64).coerceAtMost(255)
                val g = (y * 64).coerceAtMost(255)
                val b = ((x + y) * 32).coerceAtMost(255)
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        val image = bmp.asImage()

        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val canvas = doc.beginPage(50f, 50f)
        canvas.drawImage(image, 5f, 5f)
        doc.endPage()
        doc.close()

        val text = stream.toByteArray().asLatin1()

        // Must contain a /Subtype /Image XObject and a /DCTDecode filter
        // (the JPEG XObject this phase adds).
        assertTrue(
            text.contains("/Subtype /Image"),
            "Expected /Subtype /Image XObject, got:\n${text.take(400)}",
        )
        assertTrue(
            text.contains("/Filter /DCTDecode"),
            "Expected /Filter /DCTDecode in JPEG XObject body",
        )
        assertTrue(
            text.contains("/Width 4") && text.contains("/Height 4"),
            "Expected /Width 4 + /Height 4 in JPEG XObject",
        )
        assertTrue(
            text.contains("/ColorSpace /DeviceRGB"),
            "Expected /ColorSpace /DeviceRGB",
        )
        // Page must reference the XObject in its /Resources and place
        // it with a cm transform + Do invocation in the content stream.
        assertTrue(
            text.contains("/XObject <<") && text.contains("/Im1"),
            "Expected /XObject <</Im1 …>> resource entry",
        )
        assertTrue(
            text.contains("/Im1 Do"),
            "Expected '/Im1 Do' invocation in the content stream",
        )
        assertTrue(
            text.contains(" cm\n"),
            "Expected a cm transform sandwiching the image draw",
        )
    }

    @Test
    fun `multiple drawImage calls produce one XObject per draw`() {
        val a = SkBitmap(2, 2).apply { setPixel(0, 0, 0xFFFF0000.toInt()) }.asImage()
        val b = SkBitmap(3, 3).apply { setPixel(0, 0, 0xFF00FF00.toInt()) }.asImage()
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val canvas = doc.beginPage(100f, 100f)
        canvas.drawImage(a, 10f, 10f)
        canvas.drawImage(b, 50f, 50f)
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains("/Im1") && text.contains("/Im2"),
            "Expected /Im1 + /Im2 resource names for two draws")
        assertTrue(text.contains("/Width 2") && text.contains("/Width 3"),
            "Expected both source widths to appear in distinct XObjects")
    }
}
