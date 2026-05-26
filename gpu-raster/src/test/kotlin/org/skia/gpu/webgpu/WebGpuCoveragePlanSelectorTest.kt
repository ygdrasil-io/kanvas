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
import org.skia.foundation.SkRRect
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.FloatRect
import org.skia.pipeline.IntRect
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
                    ),
                )
                assertEquals("blendMode=kSrcOver|coverageKind=analyticRect|generatedPath=true", key)
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
            maxDelta <= 10,
            "rrect/simple-shape GPU max channel delta exceeded tolerance: exactRatio=$exactRatio maxDelta=$maxDelta",
        )
    }

    private companion object {
        const val W: Int = 16
        const val H: Int = 16
    }
}
