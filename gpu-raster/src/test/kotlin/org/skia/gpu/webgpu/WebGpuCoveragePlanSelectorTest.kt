package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.pipeline.AaClipRef
import org.skia.pipeline.ClipInteraction
import org.skia.pipeline.ClipShapeSpec
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.FloatRect
import org.skia.pipeline.IntRect
import org.skia.pipeline.PathFillType
import org.skia.pipeline.Point
import org.skia.pipeline.RRectSpec
import org.skia.pipeline.StandardCoverageReason

class WebGpuCoveragePlanSelectorTest {
    @Test
    fun `analytic rect selection adds only coverage kind code axis`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "axis-aligned-filled-rect",
            plan = CoveragePlan.AnalyticRect(FloatRect(2f, 1f, 7f, 6f), aa = true),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRect, selection.strategy)
        assertEquals("webgpu.coverage.analytic-rect", selection.routeIdentifier)
        assertEquals(
            listOf(
                SkWebGpuDevice.PipelineKeyClassification(
                    axis = "coverageKind",
                    axisClass = SkWebGpuDevice.PipelineKeyAxisClass.Code,
                    value = "analyticRect",
                ),
            ),
            selection.pipelineAxes,
        )
        assertFalse(selection.pipelineKeyDump().contains("2.0"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `analytic rrect selection is driven by coverage plan without uniform key axes`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "axis-aligned-filled-rrect",
            plan = CoveragePlan.AnalyticRRect(
                shape = RRectSpec(
                    bounds = FloatRect(2f, 2f, 14f, 14f),
                    topLeftRadius = Point(4f, 4f),
                    topRightRadius = Point(4f, 4f),
                    bottomRightRadius = Point(4f, 4f),
                    bottomLeftRadius = Point(4f, 4f),
                ),
                aa = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRRect, selection.strategy)
        assertEquals("webgpu.coverage.analytic-rrect", selection.routeIdentifier)
        assertEquals("coverageKind=analyticRRect:Code", selection.pipelineKeyDump())
        assertFalse(selection.pipelineKeyDump().contains("14.0"))
    }

    @Test
    fun `unsupported span coverage emits shared gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "span-fixture",
            plan = CoveragePlan.SpanRuns(IntRect(0, 0, 8, 8)),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals(StandardCoverageReason.SpanRunsUnsupported, selection.diagnostic?.reason)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.diagnostic?.dump()?.contains("coverage.span-runs-unsupported") == true)
    }

    @Test
    fun `analytic clip is accepted and recorded in webgpu selection dump`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "analytic-clipped-rect",
            plan = CoveragePlan.AnalyticRect(FloatRect(0f, 0f, 8f, 8f), aa = true),
            clipInteraction = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(1f, 1f, 7f, 7f), kind = "rrect-intersect"),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRect, selection.strategy)
        assertEquals(null, selection.diagnostic)
        assertTrue(selection.dump().contains("clip=AnalyticShape(rrect-intersect,1.0,1.0,7.0,7.0)"))
    }

    @Test
    fun `arbitrary aa clip emits stable gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "aa-clip-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 1, edgeCount = 4),
            clipInteraction = ClipInteraction.AaClip(
                ref = AaClipRef("cpu.sk-aa-clip.fixture"),
                bounds = IntRect(0, 0, 8, 8),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.ArbitraryAaClipUnsupported, selection.diagnostic?.reason)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.dump().contains("coverage.arbitrary-aa-clip-unsupported"))
        assertTrue(selection.dump().contains("clip=AaClip(ref=cpu.sk-aa-clip.fixture,bounds=0,0,8,8)"))
    }

    @Test
    fun `path coverage convex fan selection records pipeline axes`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "simple-filled-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 1, edgeCount = 5),
        )

        assertEquals(WebGpuCoverageStrategy.CpuPreparedConvexFan, selection.strategy)
        assertEquals("webgpu.coverage.path-convex-fan", selection.routeIdentifier)
        assertEquals(
            "coverageKind=pathConvexFan:Code|pathFillRule=winding:PipelineState|topology=triangleList:PipelineState",
            selection.pipelineKeyDump(),
        )
        assertTrue(selection.dump().contains("coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `path coverage concave multi contour and inverse select stencil cover`() {
        val concave = WebGpuCoveragePlanSelector.select(
            drawKind = "concave-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = false, contourCount = 1, edgeCount = 7),
        )
        val inverse = WebGpuCoveragePlanSelector.select(
            drawKind = "inverse-path",
            plan = CoveragePlan.PathCoverage(PathFillType.EvenOdd, aa = false, inverse = true),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 1, edgeCount = 4),
        )
        val multiContour = WebGpuCoveragePlanSelector.select(
            drawKind = "multi-contour-path",
            plan = CoveragePlan.PathCoverage(PathFillType.EvenOdd, aa = false, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 2, edgeCount = 8),
        )

        assertEquals(WebGpuCoverageStrategy.StencilCover, concave.strategy)
        assertEquals(WebGpuCoverageStrategy.StencilCover, inverse.strategy)
        assertEquals(WebGpuCoverageStrategy.StencilCover, multiContour.strategy)
        assertEquals("webgpu.coverage.path-stencil-cover", inverse.routeIdentifier)
        assertTrue(inverse.pipelineKeyDump().contains("pathFillRule=evenOdd:PipelineState"))
    }

    @Test
    fun `aa path edge budget overflow emits stable gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-edge-overflow",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.EdgeCountExceeded, selection.diagnostic?.reason)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.dump().contains("coverage.edge-count-exceeded"))
        assertTrue(selection.pipelineKeyDump().contains("coverageKind=pathCoverageUnsupported:Code"))
    }

    @Test
    fun `edge overflow can select explicit mask or atlas fallback when enabled`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-edge-overflow-mask",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
                maskOrAtlasFallbackEnabled = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback, selection.strategy)
        assertEquals("webgpu.coverage.path-mask-or-atlas", selection.routeIdentifier)
        assertTrue(selection.pipelineKeyDump().contains("coverageKind=pathMaskOrAtlas:Code"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `pipeline key diagnostics classify coverage kind as code axis`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 16, 16).use { device ->
                val key = device.buildPipelineKeyForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "coverageKind" to "analyticRect",
                        "generatedPath" to "true",
                        "pathFillRule" to "winding",
                    ),
                )
                assertEquals("blendMode=kSrcOver|coverageKind=analyticRect|generatedPath=true|pathFillRule=winding", key)
            }
        }
    }

    @Test
    fun `webgpu path fixtures match raster oracle when adapter is available`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val paint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            }
            assertRgbaNear(
                renderRaster { drawPath(convexPath(), paint) },
                renderGpu(ctx) { drawPath(convexPath(), paint) },
                minExactPixelRatio = 0.88,
                maxChannelDelta = 3,
                maxObservedChannelDelta = 255,
            )
            assertRgbaNear(
                renderRaster { drawPath(concavePath(), paint) },
                renderGpu(ctx) { drawPath(concavePath(), paint) },
                minExactPixelRatio = 0.84,
                maxChannelDelta = 4,
                maxObservedChannelDelta = 255,
            )
            assertRgbaNear(
                renderRaster { drawPath(inverseEvenOddPath(), paint) },
                renderGpu(ctx) { drawPath(inverseEvenOddPath(), paint) },
                minExactPixelRatio = 0.88,
                maxChannelDelta = 4,
                maxObservedChannelDelta = 255,
            )
        }
    }

    @Test
    fun `warm path frame does not create unbounded pipeline cache entries`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                }
                val canvas = SkCanvas(device)
                canvas.drawPath(convexPath(), paint)
                device.flush()
                val cold = device.cacheTelemetrySnapshot()

                canvas.drawPath(convexPath(), paint)
                device.flush()
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheEntryCount >= 1, "cold path frame should create at least one pipeline")
                assertEquals(
                    cold.pipelineCacheEntryCount,
                    warm.pipelineCacheEntryCount,
                    "warm path frame should reuse pipeline cache entries (cold=$cold warm=$warm)",
                )
            }
        }
    }

    @Test
    fun `webgpu rect and rrect fixtures match raster oracle when adapter is available`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val rectPaint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = false
            }
            val rrectPaint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            }
            assertArrayEquals(
                renderRaster { drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), rectPaint) },
                renderGpu(ctx) { drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), rectPaint) },
                "analytic rect GPU output should match raster oracle",
            )
            assertRgbaNear(
                renderRaster {
                    drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), rrectPaint)
                },
                renderGpu(ctx) {
                    drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), rrectPaint)
                },
                minExactPixelRatio = 0.90,
                maxChannelDelta = 2,
            )
        }
    }

    @Test
    fun `production device records coverage selector routes for rect rrect and path`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                }

                canvas.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), paint)
                val rect = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.analytic-rect", rect?.routeIdentifier)
                assertEquals("coverageKind=analyticRect:Code", rect?.pipelineKeyDump)
                assertTrue(rect?.selectionDump?.contains("diagnostic=none") == true)

                canvas.drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), paint)
                val rrect = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.analytic-rrect", rrect?.routeIdentifier)
                assertEquals("coverageKind=analyticRRect:Code", rrect?.pipelineKeyDump)

                canvas.drawPath(convexPath(), paint)
                val path = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.path-convex-fan", path?.routeIdentifier)
                assertTrue(path?.pipelineKeyDump?.contains("coverageKind=pathConvexFan:Code") == true)
                assertFalse(path?.pipelineKeyDump?.contains("3.0") == true)

                canvas.drawPath(concavePath(), paint)
                val concave = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.path-stencil-cover", concave?.routeIdentifier)
                assertTrue(concave?.pipelineKeyDump?.contains("coverageKind=pathStencilCover:Code") == true)
                assertTrue(concave?.selectionDump?.contains("diagnostic=none") == true)
            }
        }
    }

    private fun renderRaster(draw: SkCanvas.() -> Unit): ByteArray {
        val bitmap = SkBitmap(W, H, colorType = SkColorType.kRGBA_8888).apply {
            eraseColor(SK_ColorWHITE)
        }
        val canvas = SkCanvas(SkBitmapDevice(bitmap))
        canvas.draw()
        return bitmap.pixels8888.toRgbaBytes()
    }

    private fun renderGpu(context: WebGpuContext, draw: SkCanvas.() -> Unit): ByteArray =
        SkWebGpuDevice(context, W, H).use { device ->
            device.setBackground(SK_ColorWHITE)
            SkCanvas(device).draw()
            device.flush()
        }

    private fun IntArray.toRgbaBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val pixel = this[i]
            out[i * 4] = ((pixel ushr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((pixel ushr 8) and 0xFF).toByte()
            out[i * 4 + 2] = (pixel and 0xFF).toByte()
            out[i * 4 + 3] = ((pixel ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun assertRgbaNear(
        expected: ByteArray,
        actual: ByteArray,
        minExactPixelRatio: Double,
        maxChannelDelta: Int,
        maxObservedChannelDelta: Int = 10,
    ) {
        assertEquals(expected.size, actual.size)
        var exactPixels = 0
        var maxDelta = 0
        for (i in expected.indices step 4) {
            var pixelExact = true
            for (channel in 0 until 4) {
                val delta = kotlin.math.abs(
                    (expected[i + channel].toInt() and 0xFF) -
                        (actual[i + channel].toInt() and 0xFF),
                )
                maxDelta = maxOf(maxDelta, delta)
                if (delta > maxChannelDelta) pixelExact = false
            }
            if (pixelExact) exactPixels++
        }
        val totalPixels = expected.size / 4
        val exactRatio = exactPixels.toDouble() / totalPixels.toDouble()
        assertTrue(
            exactRatio >= minExactPixelRatio,
            "rrect/simple-shape GPU diff exceeded tolerance: exactRatio=$exactRatio maxDelta=$maxDelta",
        )
        assertTrue(
            maxDelta <= maxObservedChannelDelta,
            "rrect/simple-shape GPU max channel delta exceeded tolerance: exactRatio=$exactRatio maxDelta=$maxDelta",
        )
    }

    private companion object {
        const val W: Int = 16
        const val H: Int = 16

        fun convexPath(): SkPath = SkPathBuilder()
            .moveTo(3f, 3f)
            .lineTo(13f, 4f)
            .lineTo(11f, 13f)
            .lineTo(4f, 12f)
            .close()
            .detach()

        fun concavePath(): SkPath = SkPathBuilder()
            .moveTo(2f, 2f)
            .lineTo(14f, 2f)
            .lineTo(8f, 8f)
            .lineTo(14f, 14f)
            .lineTo(2f, 14f)
            .close()
            .detach()

        fun inverseEvenOddPath(): SkPath = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseEvenOdd)
            .addRect(SkRect.MakeLTRB(4f, 4f, 12f, 12f))
            .detach()
    }
}
