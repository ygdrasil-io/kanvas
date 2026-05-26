package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl

class PipelineKeyTelemetryTest {
    init {
        System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, "false")
    }
    @Test
    fun `pipeline key serialization is deterministic across map order`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val a = device.buildPipelineKeyForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "generatedPath" to "false",
                    ),
                )
                val b = device.buildPipelineKeyForDiagnostics(
                    linkedMapOf(
                        "generatedPath" to "false",
                        "blendMode" to "kSrcOver",
                    ),
                )
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `unknown pipeline axis throws stable diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    device.buildPipelineKeyForDiagnostics(mapOf("futureAxis" to "v"))
                }
                assertTrue(error.message?.contains("Unknown PipelineKey axis: futureAxis") == true)
            }
        }
    }

    @Test
    fun `warm frame reuses pipeline cache after first draw`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                val paint = SkPaint().apply { color = SK_ColorBLUE }
                val canvas = SkCanvas(device)
                canvas.drawRect(SkRect.MakeLTRB(4f, 4f, 40f, 40f), paint)
                device.flush()
                val cold = device.cacheTelemetrySnapshot()

                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 44f, 44f), paint)
                device.flush()
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheMisses >= 1, "cold frame should create at least one pipeline")
                assertTrue(
                    warm.pipelineCacheHits > cold.pipelineCacheHits,
                    "warm frame should increase pipeline cache hits (cold=$cold warm=$warm)",
                )
            }
        }
    }
}
