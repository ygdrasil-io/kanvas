package org.skia.core

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.pipeline.FloatRect
import org.skia.pipeline.GeometryCoverageMigrationHarness
import org.skia.pipeline.PixelBuffer
import org.skia.pipeline.Point
import org.skia.pipeline.RRectSpec
import org.skia.pipeline.Rgba

class SkBitmapDescriptorCoverageOracleTest {
    @Test
    fun `descriptor rect path matches kanvas skia non aa oracle`() {
        val oracle = render(8, 8) {
            drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), blackPaint(antiAlias = false))
        }.toPixelBuffer()

        val result = GeometryCoverageMigrationHarness.compareAxisAlignedFilledRectAgainstOracle(
            width = 8,
            height = 8,
            rect = FloatRect(2f, 1f, 7f, 6f),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            artifactPath = "artifacts/gra-33/kanvas-skia-rect-non-aa.json",
            antiAlias = false,
        )

        assertEquals(true, result.diffSummary.passed)
        assertEquals(25, result.metrics.touchedPixels)
        assertTrue(result.dump().contains("currentRoute=kanvas-skia.current.draw-rect"))
        assertTrue(result.dump().contains("coverage=AnalyticRect(2.0,1.0,7.0,6.0,aa=false)"))
    }

    @Test
    fun `descriptor rect path matches kanvas skia aa oracle`() {
        val oracle = render(8, 8) {
            drawRect(SkRect.MakeLTRB(1f, 2f, 6f, 7f), blackPaint(antiAlias = true))
        }.toPixelBuffer()

        val result = GeometryCoverageMigrationHarness.compareAxisAlignedFilledRectAgainstOracle(
            width = 8,
            height = 8,
            rect = FloatRect(1f, 2f, 6f, 7f),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            artifactPath = "artifacts/gra-33/kanvas-skia-rect-aa.json",
            antiAlias = true,
        )

        assertEquals(true, result.diffSummary.passed)
        assertEquals(25, result.metrics.touchedPixels)
        assertTrue(result.dump().contains("coverage=AnalyticRect(1.0,2.0,6.0,7.0,aa=true)"))
        assertTrue(result.dump().contains("metrics=touchedPixels=25,scalarVectorStatus=scalar-analytic-rect-aa"))
    }

    @Test
    fun `materialized rrect descriptor path matches kanvas skia oracle`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f)
        val oracleBitmap = render(16, 16) {
            drawRRect(rrect, blackPaint(antiAlias = true))
        }
        val oracle = oracleBitmap.toPixelBuffer()
        val coverage = oracleBitmap.toAlphaCoverage()

        val result = GeometryCoverageMigrationHarness.compareMaterializedRRectCoverageAgainstOracle(
            width = 16,
            height = 16,
            rrect = RRectSpec(
                bounds = FloatRect(2f, 2f, 14f, 14f),
                topLeftRadius = Point(4f, 4f),
                topRightRadius = Point(4f, 4f),
                bottomRightRadius = Point(4f, 4f),
                bottomLeftRadius = Point(4f, 4f),
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-33/kanvas-skia-rrect-materialized.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.metrics.touchedPixels > 0)
        assertTrue(result.dump().contains("currentRoute=kanvas-skia.current.draw-rrect"))
        assertTrue(result.dump().contains("coverage=AlphaMask(ref=fixture.rrect.a8,bounds=0,0,16,16,format=A8)"))
        assertTrue(result.dump().contains("lowering=CoverageModel.AlphaMask(0,0,16,16,format=A8)"))
    }

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorTRANSPARENT)
        SkCanvas(bitmap).draw()
        return bitmap
    }

    private fun blackPaint(antiAlias: Boolean): SkPaint = SkPaint().apply {
        color = SK_ColorBLACK
        isAntiAlias = antiAlias
    }

    private fun SkBitmap.toPixelBuffer(): PixelBuffer =
        PixelBuffer(width, height, IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            getPixel(x, y)
        })

    private fun SkBitmap.toAlphaCoverage(): ByteArray =
        ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            SkColorGetA(getPixel(x, y)).toByte()
        }

}
