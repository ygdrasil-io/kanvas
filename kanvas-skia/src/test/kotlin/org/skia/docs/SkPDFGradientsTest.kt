package org.skia.docs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Phase R-suivi.38 — verify that [SkPDF.MakeDocument] emits a PDF
 * Type 2 (axial) shading dictionary when a paint carries a
 * [SkLinearGradient].
 */
class SkPDFGradientsTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `linear gradient fill emits a Type 2 axial shading + Pattern`() {
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(100f, 0f),
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply { shader = grad }

        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val canvas = doc.beginPage(100f, 100f)
        canvas.drawRect(SkRect.MakeWH(100f, 100f), paint)
        doc.close()

        val text = stream.toByteArray().asLatin1()

        // The PDF must contain a /ShadingType 2 axial shading dict.
        assertTrue(
            text.contains("/ShadingType 2"),
            "Expected a /ShadingType 2 axial shading dictionary, got:\n${text.take(400)}",
        )
        // It must declare a Pattern colour-space alias and reference
        // it from a Pattern object.
        assertTrue(
            text.contains("/PatternType 2"),
            "Expected /PatternType 2 (shading pattern) in the body",
        )
        assertTrue(
            text.contains("/Pattern") && text.contains("/P1"),
            "Expected /Pattern resource entry with name /P1",
        )
        // The Function backing the shading is a Type 2 exponential for
        // a 2-stop gradient.
        assertTrue(
            text.contains("/FunctionType 2"),
            "Expected /FunctionType 2 for 2-stop gradient",
        )
        // The content stream must select the pattern with the `scn` op.
        assertTrue(
            text.contains("/CSp cs") && text.contains("/P1 scn"),
            "Expected /CSp cs … /P1 scn pattern-selection sequence",
        )
        // Coords [x0 y0 x1 y1] — y is flipped to PDF y-up so y0/y1
        // become (pageHeight - origY) = 100 here.
        assertTrue(
            text.contains("/Coords [0 100 100 100]"),
            "Expected y-flipped /Coords [0 100 100 100]",
        )
        // /Extend [true true] — gradient clamps tile mode mirror upstream's
        // "extend at both ends" Type 2 shading default for kClamp.
        assertTrue(
            text.contains("/Extend [true true]"),
            "Expected /Extend [true true]",
        )
    }

    @Test
    fun `three-stop gradient emits a Type 3 stitching function`() {
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(100f, 0f),
            colors = intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
            ),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply { shader = grad }
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream)
        val c = doc.beginPage(50f, 50f)
        c.drawRect(SkRect.MakeWH(50f, 50f), paint)
        doc.close()

        val text = stream.toByteArray().asLatin1()
        assertTrue(
            text.contains("/FunctionType 3"),
            "Expected /FunctionType 3 stitching function for 3-stop gradient",
        )
        // Two Type 2 segments back-to-back.
        val occurrences = "/FunctionType 2".toRegex().findAll(text).count()
        assertTrue(occurrences >= 2,
            "Expected ≥ 2 Type 2 sub-functions in the body, found $occurrences")
    }
}
