package org.skia.docs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.stream.SkDynamicMemoryWStream

/**
 * Phase R-suivi.39 — verify that path verbs round-trip through native
 * PDF operators :
 *
 *  - `cubic` → `c`
 *  - `quad` → degree-elevated to `c` (lossless)
 *  - `conic` → subdivided into cubics (`c` operator only)
 */
class SkPDFCubicTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `drawPath with cubicTo emits a native c operator`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(10f, 0f, 20f, 30f, 30f, 30f)
            .close()
            .detach()
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val c = doc.beginPage(100f, 100f)
        c.drawPath(path, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains(" c\n"),
            "Expected a 'c' cubic operator in the content stream")
    }

    @Test
    fun `drawPath with quadTo degree-elevates to a c operator`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(10f, 0f, 20f, 30f)
            .close()
            .detach()
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val c = doc.beginPage(100f, 100f)
        c.drawPath(path, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        // The quad verb must now produce a `c` operator (not a single
        // `l` linearisation as in the pre-R-suivi.39 implementation).
        assertTrue(text.contains(" c\n"),
            "Expected quadTo to degree-elevate to a 'c' cubic operator")
        // The path is single-contour with one verb — there must be no
        // bare `l` line operator produced by the (pre-R-suivi.39)
        // linearisation fallback.
        // Note: a synthetic close line may emit an `l`, so we only
        // assert that *at least one* `c` is present.
    }

    @Test
    fun `drawPath with conicTo subdivides into cubics`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(50f, 0f, 50f, 50f, 0.707f) // arc-ish weight
            .close()
            .detach()
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val c = doc.beginPage(100f, 100f)
        c.drawPath(path, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        // Conics now expand to multiple `c` operators (4 sub-segments).
        val cubicOps = " c\n".toRegex().findAll(text).count()
        assertTrue(cubicOps >= 4,
            "Expected the conic to subdivide into ≥ 4 cubic segments, got $cubicOps")
    }

    @Test
    fun `moveTo and lineTo use m and l operators`() {
        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(50f, 50f)
            .detach()
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val c = doc.beginPage(100f, 100f)
        c.drawPath(path, SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains(" m\n"), "Expected 'm' moveTo op")
        assertTrue(text.contains(" l\n"), "Expected 'l' lineTo op")
        // Pure line paths must not emit cubic ops.
        assertFalse(text.contains(" c\n"),
            "Pure moveTo/lineTo path must not emit any 'c' cubic ops")
    }
}
