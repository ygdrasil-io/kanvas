package org.skia.core

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `production bitmap device selects descriptor route for non aa filled rect by default`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("cpu.descriptor.coverage-plan.solid-rect", diagnostics.selectedRoute)
            assertEquals("kanvas-skia.current.draw-rect", diagnostics.compatibilityFallbackRoute)
            assertEquals("AnalyticRect(2.0,1.0,7.0,6.0,aa=false)", diagnostics.coveragePlan)
            assertEquals(null, diagnostics.fallbackReason)
            assertEquals(25, diagnostics.touchedPixels)
            assertEquals("Default", diagnostics.mode)
            assertTrue(diagnostics.dump().contains("fallbackReason=none"))
            assertTrue(diagnostics.dump().contains("backend=CPU"))
            assertTrue(diagnostics.dump().contains("drawKind=axis-aligned-filled-rect"))
        }

    @Test
    fun `production descriptor rect can be rolled back to legacy route`() =
        withDescriptorRectFlag("false") {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("kanvas-skia.current.draw-rect", diagnostics.selectedRoute)
            assertEquals("cpu.descriptor.coverage-plan.solid-rect", diagnostics.compatibilityFallbackRoute)
            assertEquals("coverage.cpu-descriptor-rect-disabled", diagnostics.fallbackReason)
            assertEquals(0, diagnostics.touchedPixels)
            assertEquals("Rollback", diagnostics.mode)
            assertTrue(diagnostics.dump().contains("mode=Rollback"))
            assertTrue(diagnostics.dump().contains("fallbackRoute=cpu.descriptor.coverage-plan.solid-rect"))
        }

    @Test
    fun `production descriptor rect falls back for unsupported stroke style`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            val paint = blackPaint(antiAlias = false).apply {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 1f
            }
            device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), paint)

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("kanvas-skia.current.draw-rect", diagnostics.selectedRoute)
            assertEquals("coverage.cpu-descriptor-fill-style-only", diagnostics.fallbackReason)
            assertEquals("Fallback", diagnostics.mode)
            assertFalse(bitmap.pixels.all { it == SK_ColorTRANSPARENT })
        }

    @Test
    fun `production descriptor non aa rect remains pixel equivalent to forced legacy`() {
        val descriptor = renderWithDescriptorFlag(null, antiAlias = false, rect = SkRect.MakeLTRB(2f, 1f, 7f, 6f))
        val legacy = renderWithDescriptorFlag("false", antiAlias = false, rect = SkRect.MakeLTRB(2f, 1f, 7f, 6f))

        assertTrue(descriptor.pixels.contentEquals(legacy.pixels))
    }

    @Test
    fun `production descriptor aa rect remains pixel equivalent to forced legacy`() {
        val descriptor = renderWithDescriptorFlag(null, antiAlias = true, rect = SkRect.MakeLTRB(1.25f, 2.5f, 6.75f, 7f))
        val legacy = renderWithDescriptorFlag("false", antiAlias = true, rect = SkRect.MakeLTRB(1.25f, 2.5f, 6.75f, 7f))

        assertTrue(descriptor.pixels.contentEquals(legacy.pixels))
    }

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

    private fun renderWithDescriptorFlag(value: String?, antiAlias: Boolean, rect: SkRect): SkBitmap =
        withDescriptorRectFlag(value) {
            render(8, 8) {
                drawRect(rect, blackPaint(antiAlias = antiAlias).apply { color = SK_ColorRED })
            }
        }

    private fun <T> withDescriptorRectFlag(value: String?, block: () -> T): T {
        val previous = System.getProperty("kanvas.cpu.descriptorRect.enabled")
        if (value == null) {
            System.clearProperty("kanvas.cpu.descriptorRect.enabled")
        } else {
            System.setProperty("kanvas.cpu.descriptorRect.enabled", value)
        }
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty("kanvas.cpu.descriptorRect.enabled")
            } else {
                System.setProperty("kanvas.cpu.descriptorRect.enabled", previous)
            }
        }
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
