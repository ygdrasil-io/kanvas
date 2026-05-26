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
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkStroker
import org.skia.pipeline.FloatRect
import org.skia.pipeline.GeometryCoverageMigrationHarness
import org.skia.pipeline.PathCoverageFixture
import org.skia.pipeline.PathFillType
import org.skia.pipeline.PixelBuffer
import org.skia.pipeline.Rgba
import org.skia.pipeline.StrokePlan

class SkBitmapPathCoverageOracleTest {
    @Test
    fun `simple filled path descriptor matches kanvas skia oracle`() {
        val path = SkPathBuilder()
            .moveTo(3f, 3f)
            .lineTo(14f, 3f)
            .lineTo(8f, 13f)
            .close()
            .detach()
        val oracleBitmap = render(18, 18) {
            drawPath(path, blackPaint(antiAlias = true))
        }

        val result = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = oracleBitmap.width,
            height = oracleBitmap.height,
            fixture = pathFixture(path, antiAlias = true),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracleBitmap.toPixelBuffer(),
            coverageAlpha = oracleBitmap.toAlphaCoverage(),
            artifactPath = "artifacts/gra-35/kanvas-skia-simple-path.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.metrics.touchedPixels > 0)
        assertTrue(result.dump().contains("currentRoute=kanvas-skia.current.draw-path"))
        assertTrue(result.dump().contains("descriptorRoute=cpu.descriptor.coverage-plan.path-coverage"))
        assertTrue(result.dump().contains("coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)"))
        assertTrue(result.dump().contains("fallback=strategy.CpuSpanPath"))
        assertTrue(result.dump().contains("pathVerbCount=4,edgeCount=2,segmentCount=2"))
    }

    @Test
    fun `inverse even odd path descriptor matches kanvas skia oracle`() {
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseEvenOdd)
            .addRect(SkRect.MakeLTRB(5f, 5f, 15f, 15f))
            .detach()
        val oracleBitmap = render(20, 20) {
            drawPath(path, blackPaint(antiAlias = false))
        }

        val result = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = oracleBitmap.width,
            height = oracleBitmap.height,
            fixture = pathFixture(path, antiAlias = false),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracleBitmap.toPixelBuffer(),
            coverageAlpha = oracleBitmap.toAlphaCoverage(),
            artifactPath = "artifacts/gra-35/kanvas-skia-inverse-even-odd-path.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.metrics.touchedPixels > 0)
        assertTrue(result.dump().contains("coverage=PathCoverage(fillType=EvenOdd,aa=false,inverse=true)"))
        assertTrue(result.dump().contains("geometry=Supported(Path(fillType=EvenOdd,stroke=false"))
    }

    @Test
    fun `stroke outline path descriptor matches kanvas skia oracle`() {
        val source = SkPathBuilder()
            .moveTo(4f, 4f)
            .lineTo(16f, 4f)
            .lineTo(16f, 16f)
            .detach()
        val strokePaint = blackPaint(antiAlias = true).apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 4f
            strokeJoin = SkPaint.Join.kMiter_Join
            strokeMiter = 4f
        }
        val outline = SkStroker.fromPaint(strokePaint).stroke(source)
        val oracleBitmap = render(22, 22) {
            drawPath(source, strokePaint)
        }

        val result = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = oracleBitmap.width,
            height = oracleBitmap.height,
            fixture = pathFixture(outline, antiAlias = true, stroke = StrokePlan(width = 4f, miterLimit = 4f)),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracleBitmap.toPixelBuffer(),
            coverageAlpha = oracleBitmap.toAlphaCoverage(),
            artifactPath = "artifacts/gra-35/kanvas-skia-stroke-outline-path.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.metrics.touchedPixels > 0)
        assertTrue(result.dump().contains("currentRoute=kanvas-skia.current.stroke-path"))
        assertTrue(result.dump().contains("descriptorRoute=cpu.descriptor.coverage-plan.stroke-outline"))
        assertTrue(result.dump().contains("geometry=Supported(Path(fillType=Winding,stroke=true"))
        assertTrue(result.dump().contains("coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)"))
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

    private fun pathFixture(
        path: SkPath,
        antiAlias: Boolean,
        stroke: StrokePlan? = null,
    ): PathCoverageFixture {
        val bounds = path.computeBounds()
        return PathCoverageFixture(
            bounds = FloatRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            fillType = path.fillType.toCoverageFillType(),
            inverse = path.fillType.isInverse(),
            antiAlias = antiAlias,
            verbCount = path.verbs.size,
            edgeCount = path.edgeCount(),
            segmentCount = path.segmentCount(),
            stroke = stroke,
        )
    }

    private fun SkPathFillType.toCoverageFillType(): PathFillType =
        if (isEvenOdd()) PathFillType.EvenOdd else PathFillType.Winding

    private fun SkPath.edgeCount(): Int =
        verbs.count { it == SkPath.Verb.kLine || it == SkPath.Verb.kQuad || it == SkPath.Verb.kConic || it == SkPath.Verb.kCubic }

    private fun SkPath.segmentCount(): Int =
        verbs.count { it == SkPath.Verb.kLine || it == SkPath.Verb.kQuad || it == SkPath.Verb.kConic || it == SkPath.Verb.kCubic }

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
