package org.skia.core

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAAClip
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.pipeline.CoverageBackendStrategy
import org.skia.pipeline.CoverageLoweringResult
import org.skia.pipeline.CoverageModel
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.CpuAnalyticRectCoverageExecutor
import org.skia.pipeline.FallbackPlan
import org.skia.pipeline.FloatRect
import org.skia.pipeline.GeometryCoverageMigrationHarness
import org.skia.pipeline.ImageRectLowering
import org.skia.pipeline.ImageSamplingPayloadRef
import org.skia.pipeline.MatrixSpec
import org.skia.pipeline.PixelBuffer
import org.skia.pipeline.Point
import org.skia.pipeline.RRectSpec
import org.skia.pipeline.Rgba
import org.skia.pipeline.StandardCoverageReason
import org.skia.pipeline.TransformFacts

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
            assertEquals("CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)", diagnostics.loweringResult)
            assertEquals(
                "lowering-consumed:CoverageModel.AnalyticRect;kernel=${CpuAnalyticRectCoverageExecutor.KERNEL_ID};touchedPixels=25",
                diagnostics.executionEvidence,
            )
            assertEquals(null, diagnostics.fallbackReason)
            assertEquals(25, diagnostics.touchedPixels)
            assertEquals("Default", diagnostics.mode)
            assertTrue(diagnostics.dump().contains("fallbackReason=none"))
            assertTrue(diagnostics.dump().contains("backend=CPU"))
            assertTrue(diagnostics.dump().contains("drawKind=axis-aligned-filled-rect"))
            assertTrue(diagnostics.dump().contains("loweringResult=CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)"))
            assertTrue(diagnostics.dump().contains("executionEvidence=lowering-consumed:CoverageModel.AnalyticRect"))
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
            assertEquals("CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)", diagnostics.loweringResult)
            assertEquals("legacy-fallback-before-descriptor-execution", diagnostics.executionEvidence)
            assertEquals(0, diagnostics.touchedPixels)
            assertEquals("Rollback", diagnostics.mode)
            assertTrue(diagnostics.dump().contains("mode=Rollback"))
            assertTrue(diagnostics.dump().contains("fallbackRoute=cpu.descriptor.coverage-plan.solid-rect"))
            assertTrue(diagnostics.dump().contains("loweringResult=CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)"))
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
            assertEquals("CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)", diagnostics.loweringResult)
            assertEquals("legacy-fallback-before-descriptor-execution", diagnostics.executionEvidence)
            assertEquals("Fallback", diagnostics.mode)
            assertFalse(bitmap.pixels.all { it == SK_ColorTRANSPARENT })
        }

    @Test
    fun `production descriptor rect records stable fallback for active aa clip`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.setActiveClip(SkAAClip(SkIRect.MakeLTRB(1, 1, 7, 7)))
            device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("Fallback", diagnostics.mode)
            assertEquals("kanvas-skia.current.draw-rect", diagnostics.selectedRoute)
            assertEquals("coverage.cpu-descriptor-aa-clip-unsupported", diagnostics.fallbackReason)
            assertEquals("legacy-fallback-before-descriptor-execution", diagnostics.executionEvidence)
            assertEquals(0, diagnostics.touchedPixels)
            assertTrue(diagnostics.dump().contains("fallbackReason=coverage.cpu-descriptor-aa-clip-unsupported"))
        }

    @Test
    fun `production descriptor rect records stable fallback for active clip shader`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.setActiveClipShader(opaqueClipShader(), SkMatrix.Identity, SkClipOp.kIntersect)
            device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("Fallback", diagnostics.mode)
            assertEquals("kanvas-skia.current.draw-rect", diagnostics.selectedRoute)
            assertEquals("coverage.cpu-descriptor-clip-shader-unsupported", diagnostics.fallbackReason)
            assertEquals("legacy-fallback-before-descriptor-execution", diagnostics.executionEvidence)
            assertEquals(0, diagnostics.touchedPixels)
            assertTrue(diagnostics.dump().contains("fallbackReason=coverage.cpu-descriptor-clip-shader-unsupported"))
        }

    @Test
    fun `production bitmap device descriptor route records aa lowering execution evidence`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.drawRect(SkRect.MakeLTRB(1.25f, 2.5f, 6.75f, 7f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = true))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("cpu.descriptor.coverage-plan.solid-rect", diagnostics.selectedRoute)
            assertEquals("AnalyticRect(1.25,2.5,6.75,7.0,aa=true)", diagnostics.coveragePlan)
            assertEquals("CoverageModel.AnalyticRect(1.25,2.5,6.75,7.0,aa=true)", diagnostics.loweringResult)
            assertTrue(
                diagnostics.executionEvidence.startsWith(
                    "lowering-consumed:CoverageModel.AnalyticRect;kernel=${CpuAnalyticRectCoverageExecutor.KERNEL_ID};touchedPixels=",
                ),
            )
            assertEquals(diagnostics.touchedPixels.toString(), diagnostics.executionEvidence.substringAfter("touchedPixels="))
            assertTrue(diagnostics.touchedPixels > 0)
        }

    @Test
    fun `production descriptor execution consumes lowered analytic rect bounds`() =
        withDescriptorRectFlag(null) {
            withCoverageLoweringForTests(
                CoverageLoweringResult.CoverageModelResult(
                    CoverageModel.AnalyticRect(bounds = FloatRect(3f, 2f, 5f, 4f), aa = false),
                ),
            ) {
                val bitmap = SkBitmap(8, 8)
                val device = SkBitmapDevice(bitmap)
                device.drawRect(SkRect.MakeLTRB(1f, 1f, 7f, 7f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

                val diagnostics = device.descriptorCoverageDiagnosticsForTests()
                assertEquals("AnalyticRect(1.0,1.0,7.0,7.0,aa=false)", diagnostics.coveragePlan)
                assertEquals("CoverageModel.AnalyticRect(3.0,2.0,5.0,4.0,aa=false)", diagnostics.loweringResult)
                assertEquals(
                    "lowering-consumed:CoverageModel.AnalyticRect;kernel=${CpuAnalyticRectCoverageExecutor.KERNEL_ID};touchedPixels=4",
                    diagnostics.executionEvidence,
                )
                assertEquals(4, diagnostics.touchedPixels)
                assertEquals(SK_ColorTRANSPARENT, bitmap.getPixel(1, 1))
                assertEquals(SK_ColorBLACK, bitmap.getPixel(3, 2))
                assertEquals(SK_ColorBLACK, bitmap.getPixel(4, 3))
                assertEquals(SK_ColorTRANSPARENT, bitmap.getPixel(5, 4))
            }
        }

    @Test
    fun `production descriptor shared executor honors explicit clip bounds`() =
        withDescriptorRectFlag(null) {
            val bitmap = SkBitmap(8, 8)
            val device = SkBitmapDevice(bitmap)
            device.drawRect(SkRect.MakeLTRB(1f, 1f, 7f, 7f), SkIRect.MakeLTRB(2, 3, 5, 6), blackPaint(antiAlias = false))

            val diagnostics = device.descriptorCoverageDiagnosticsForTests()
            assertEquals("cpu.descriptor.coverage-plan.solid-rect", diagnostics.selectedRoute)
            assertEquals(9, diagnostics.touchedPixels)
            assertEquals(
                "lowering-consumed:CoverageModel.AnalyticRect;kernel=${CpuAnalyticRectCoverageExecutor.KERNEL_ID};touchedPixels=9",
                diagnostics.executionEvidence,
            )
            assertEquals(SK_ColorTRANSPARENT, bitmap.getPixel(1, 3))
            assertEquals(SK_ColorBLACK, bitmap.getPixel(2, 3))
            assertEquals(SK_ColorBLACK, bitmap.getPixel(4, 5))
            assertEquals(SK_ColorTRANSPARENT, bitmap.getPixel(5, 5))
        }

    @Test
    fun `production descriptor route falls back when lowering returns strategy result`() =
        withDescriptorRectFlag(null) {
            withCoverageLoweringForTests(
                CoverageLoweringResult.StrategyResult(
                    CoverageBackendStrategy.UnsupportedFallback(
                        fallback = FallbackPlan.RefuseDiagnostic(StandardCoverageReason.AlphaMaskUnsupported.code),
                        reason = StandardCoverageReason.AlphaMaskUnsupported,
                    ),
                ),
            ) {
                val bitmap = SkBitmap(8, 8)
                val device = SkBitmapDevice(bitmap)
                device.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), SkIRect.MakeWH(8, 8), blackPaint(antiAlias = false))

                val diagnostics = device.descriptorCoverageDiagnosticsForTests()
                assertEquals("Fallback", diagnostics.mode)
                assertEquals("kanvas-skia.current.draw-rect", diagnostics.selectedRoute)
                assertEquals("cpu.descriptor.coverage-plan.solid-rect", diagnostics.compatibilityFallbackRoute)
                assertEquals("StrategyResult(coverage.alpha-mask-unsupported)", diagnostics.loweringResult)
                assertEquals("descriptor-execution-refused", diagnostics.executionEvidence)
                assertEquals("coverage.cpu-descriptor-lowering-unsupported", diagnostics.fallbackReason)
                assertEquals(0, diagnostics.touchedPixels)
                assertFalse(bitmap.pixels.all { it == SK_ColorTRANSPARENT })
            }
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

    @Test
    fun `image rect descriptor captures analytic coverage while sampling remains paint owned`() {
        val src = SkRect.MakeLTRB(0f, 0f, 2f, 2f)
        val dst = SkRect.MakeLTRB(2f, 2f, 6f, 6f)
        val image = quadrantImage()
        val oracleBitmap = render(8, 8) {
            drawImageRect(
                image = image,
                src = src,
                dst = dst,
                sampling = SkSamplingOptions(SkFilterMode.kNearest),
                paint = null,
                constraint = SrcRectConstraint.kStrict,
            )
        }
        val descriptor = ImageRectLowering.lower(
            source = FloatRect(src.left, src.top, src.right, src.bottom),
            destination = FloatRect(dst.left, dst.top, dst.right, dst.bottom),
            transform = TransformFacts(
                matrix = MatrixSpec.Identity,
                isAxisAligned = true,
                hasPerspective = false,
                maxScale = 2f,
                isInvertible = true,
            ),
            payloadRef = ImageSamplingPayloadRef("kanvas-skia.quadrant-image"),
        )

        assertEquals(SK_ColorTRANSPARENT, oracleBitmap.getPixel(1, 1))
        assertEquals(0xFFFF0000.toInt(), oracleBitmap.getPixel(2, 2))
        assertEquals(0xFF00FF00.toInt(), oracleBitmap.getPixel(5, 2))
        assertEquals(0xFF0000FF.toInt(), oracleBitmap.getPixel(2, 5))
        assertEquals(0xFFFFFFFF.toInt(), oracleBitmap.getPixel(5, 5))
        assertEquals(SK_ColorTRANSPARENT, oracleBitmap.getPixel(6, 6))
        assertEquals("geometry.image-rect.analytic-rect", descriptor.routeIdentifier)
        assertEquals("AnalyticRect(2.0,2.0,6.0,6.0,aa=true)", descriptor.dump().substringAfter("coverage=").dropLast(1))
        assertTrue(descriptor.dump().contains("payload=kanvas-skia.quadrant-image"))
        assertTrue(descriptor.dump().contains("paint owns sampling, pixels, filtering, and colorspace"))
        assertEquals("paint-owned", descriptor.primitive.sampling.filter)
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

    private fun opaqueClipShader(): SkShader = object : SkShader() {
        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            for (i in 0 until count) {
                dst[i] = 0xFF000000.toInt()
            }
        }
    }

    private fun quadrantImage(): SkImage {
        val bitmap = SkBitmap(2, 2)
        bitmap.setPixel(0, 0, 0xFFFF0000.toInt())
        bitmap.setPixel(1, 0, 0xFF00FF00.toInt())
        bitmap.setPixel(0, 1, 0xFF0000FF.toInt())
        bitmap.setPixel(1, 1, 0xFFFFFFFF.toInt())
        return SkImage.Make(bitmap)
    }

    private fun renderWithDescriptorFlag(value: String?, antiAlias: Boolean, rect: SkRect): SkBitmap =
        withDescriptorRectFlag(value) {
            render(8, 8) {
                drawRect(rect, blackPaint(antiAlias = antiAlias).apply { color = SK_ColorRED })
            }
        }

    private fun <T> withCoverageLoweringForTests(lowering: CoverageLoweringResult, block: () -> T): T {
        val previous = SkBitmapDescriptorCoverageLowering.lower
        SkBitmapDescriptorCoverageLowering.lower = { _: CoveragePlan -> lowering }
        return try {
            block()
        } finally {
            SkBitmapDescriptorCoverageLowering.lower = previous
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
