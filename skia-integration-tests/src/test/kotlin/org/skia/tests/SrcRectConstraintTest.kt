package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkSamplingOptions
import org.skia.testing.TestUtils

class SrcRectConstraintTest {

    @Test
    fun `SrcRectConstraintGM renders strict and fast columns`() {
        TestUtils.runGmTest(SrcRectConstraintGM())
    }

    @Test
    fun `strict drawImageRect prevents linear filter guard-pixel bleed`() {
        val source = SkBitmap(6, 6).apply {
            eraseColor(SK_ColorRED)
            for (y in 1 until 5) {
                for (x in 1 until 5) {
                    setPixel(x, y, SK_ColorGREEN)
                }
            }
        }.asImage()

        val dst = SkBitmap(16, 8).also { it.eraseColor(SK_ColorGRAY) }
        val canvas = SkCanvas(dst)
        val srcRect = SkRect.MakeLTRB(1f, 1f, 5f, 5f)
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)

        canvas.drawImageRect(
            source,
            srcRect,
            SkRect.MakeXYWH(0f, 0f, 8f, 8f),
            sampling,
            constraint = SrcRectConstraint.kStrict,
        )
        canvas.drawImageRect(
            source,
            srcRect,
            SkRect.MakeXYWH(8f, 0f, 8f, 8f),
            sampling,
            constraint = SrcRectConstraint.kFast,
        )

        val strictEdge = dst.getPixel(0, 0)
        val fastEdge = dst.getPixel(8, 0)

        assertEquals(SK_ColorGREEN, strictEdge)
        assertTrue(SkColorGetR(fastEdge) > 0, "kFast should sample red guard pixels at the subset edge")
        assertTrue(SkColorGetG(fastEdge) in 1 until 255, "kFast should blend green center with red guard pixels")
    }
}
