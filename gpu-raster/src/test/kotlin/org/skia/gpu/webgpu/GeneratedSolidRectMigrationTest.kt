package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl

class GeneratedSolidRectMigrationTest {
    @Test
    fun `solid color rect uses generated WGSL path by default`() = withGeneratedSolidRectFlag(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                drawMigratedRect(device)

                val diagnostics = device.solidRectMigrationDiagnosticsForTests()
                assertEquals("Rect + SolidColor + SrcOver", diagnostics.shaderFamily)
                assertEquals("generated", diagnostics.selectedPath)
                assertTrue(diagnostics.generatedDefaultAvailable)
                assertNull(diagnostics.retainedFallbackReason)
                assertTrue(diagnostics.handwrittenRetirementCriteria.contains("color filters"))
            }
        }
    }

    @Test
    fun `handwritten solid color fallback remains named when generated path is disabled`() =
        withGeneratedSolidRectFlag("false") {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, 64, 64).use { device ->
                    drawMigratedRect(device)

                    val diagnostics = device.solidRectMigrationDiagnosticsForTests()
                    assertEquals("handwritten", diagnostics.selectedPath)
                    assertFalse(diagnostics.generatedDefaultAvailable)
                    assertEquals(
                        "generated solid rect disabled via -Dkanvas.gpu.generatedSolidRect.enabled=false",
                        diagnostics.retainedFallbackReason,
                    )
                }
            }
        }

    @Test
    fun `generated solid color rect reuses warm pipeline cache`() = withGeneratedSolidRectFlag(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                drawMigratedRect(device)
                val cold = device.cacheTelemetrySnapshot()

                drawMigratedRect(device, left = 12f, top = 12f, right = 48f, bottom = 48f)
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheMisses >= 1, "cold frame should create generated rect pipeline")
                assertTrue(
                    warm.pipelineCacheHits > cold.pipelineCacheHits,
                    "warm generated rect draw should reuse pipeline cache (cold=$cold warm=$warm)",
                )
                assertEquals("generated", device.solidRectMigrationDiagnosticsForTests().selectedPath)
            }
        }
    }

    private fun drawMigratedRect(
        device: SkWebGpuDevice,
        left: Float = 8f,
        top: Float = 8f,
        right: Float = 40f,
        bottom: Float = 40f,
    ) {
        val paint = SkPaint().apply { color = SK_ColorBLUE }
        val canvas = SkCanvas(device)
        canvas.drawRect(SkRect.MakeLTRB(left, top, right, bottom), paint)
        device.flush()
    }

    private fun <T> withGeneratedSolidRectFlag(value: String?, block: () -> T): T {
        val previous = System.getProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        if (value == null) {
            System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        } else {
            System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, value)
        }
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
            } else {
                System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, previous)
            }
        }
    }
}
