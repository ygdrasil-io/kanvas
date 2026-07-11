package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUMaskBlurSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `normal rect blur has exterior non-flat halo`() {
        val pixels = renderRect(BlurStyle.NORMAL, 2f)
        val exterior = alphaAt(pixels, 7, 12, 32)
        val interior = alphaAt(pixels, 9, 12, 32)

        assertTrue(exterior > 0) { "normal exterior alpha=$exterior interior alpha=$interior" }
        assertTrue(interior < 255)
    }

    @Test
    fun `outer blur removes interior coverage`() {
        val pixels = renderRect(BlurStyle.OUTER, 2f)
        val interior = alphaAt(pixels, 12, 12, 32)
        val exterior = alphaAt(pixels, 7, 12, 32)

        assertEquals(0, interior)
        assertTrue(exterior > 0) { "outer exterior alpha=$exterior interior alpha=$interior" }
    }

    @Test
    fun `solid and inner preserve documented coverage`() {
        assertEquals(255, alphaAt(renderRect(BlurStyle.SOLID, 2f), 12, 12, 32))
        assertTrue(alphaAt(renderRect(BlurStyle.INNER, 2f), 12, 12, 32) > 0)
    }

    @Test
    fun `path and rrect create a halo`() {
        val triangle = alphaAt(renderTriangle(2f), 7, 12, 32)
        val rrect = alphaAt(renderRRect(2f), 7, 12, 32)

        assertTrue(triangle > 0) { "triangle exterior alpha=$triangle rrect exterior alpha=$rrect" }
        assertTrue(rrect > 0)
    }

    @Test
    fun `sigma forty eight reduces rather than refuses`() {
        val result = renderRectResult(BlurStyle.NORMAL, 48f)

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertTrue(result.diagnostics.entries.any { entry ->
            entry.reason.contains("mask-filter.blur.reduced-resolution")
        })
    }

    @Test
    fun `budget refusal has no dispatch`() {
        val result = renderRectResult(
            style = BlurStyle.NORMAL,
            sigma = 12f,
            config = RenderConfig(maxMaskBlurIntermediateBytes = 8u),
        )

        assertEquals(0, result.stats.opsDispatched)
        assertEquals(1, result.stats.opsRefused)
        assertTrue(result.diagnostics.entries.any { entry ->
            entry.reason == "unsupported.mask-filter.blur.intermediate-budget"
        })
    }

    private fun renderRect(style: BlurStyle, sigma: Float): ByteArray =
        renderRectResult(style, sigma).pixels.toByteArray()

    private fun renderRectResult(
        style: BlurStyle,
        sigma: Float,
        config: RenderConfig = RenderConfig.DEFAULT,
    ) = Surface(width = 32, height = 32, config = config).run {
        requireWebGpu()
        canvas {
            drawRect(Rect(8f, 8f, 17f, 17f), blurPaint(style, sigma))
        }
        render()
    }

    private fun renderTriangle(sigma: Float): ByteArray = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawPath(
                Path {
                    moveTo(8f, 8f)
                    lineTo(17f, 8f)
                    lineTo(12.5f, 17f)
                    close()
                },
                blurPaint(BlurStyle.NORMAL, sigma),
            )
        }
        render().pixels.toByteArray()
    }

    private fun renderRRect(sigma: Float): ByteArray = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRRect(
                RRect(
                    rect = Rect(8f, 8f, 17f, 17f),
                    topLeft = CornerRadii(2f, 2f),
                    topRight = CornerRadii(2f, 2f),
                    bottomRight = CornerRadii(2f, 2f),
                    bottomLeft = CornerRadii(2f, 2f),
                ),
                blurPaint(BlurStyle.NORMAL, sigma),
            )
        }
        render().pixels.toByteArray()
    }

    private fun blurPaint(style: BlurStyle, sigma: Float): Paint = Paint(
        color = Color.BLACK,
        maskFilter = MaskFilter.Blur(style, sigma),
        antiAlias = false,
    )

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun alphaAt(pixels: ByteArray, x: Int, y: Int, width: Int): Int =
        pixels[(y * width + x) * 4 + 3].toInt() and 0xff
}
