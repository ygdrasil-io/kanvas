package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalUnsignedTypes::class)
class GPUImageFilterSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `sigma two image blur is an exact prepared native binding refusal`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderFixtureThroughSurface(
                image = opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                paint = Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
                dst = RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                surfaceSize = 32,
            )
        }
    }

    @Test
    fun `clamp edge reference rejects the historical decal result`() {
        val clamp = ImageFilterBlurCpuOracle.clampBlurOpaqueRedLeftEdgeInSurface(
            surfaceSize = 32,
            originX = 8,
            originY = 8,
            sigmaX = 2f,
            sigmaY = 2f,
        )
        val decal = ImageFilterBlurCpuOracle.decalBlurOpaqueRedLeftEdgeInSurface(
            surfaceSize = 32,
            originX = 8,
            originY = 8,
            sigmaX = 2f,
            sigmaY = 2f,
        )

        assertTrue(alphaAt(clamp, 7, 12, 32) - alphaAt(decal, 7, 12, 32) > 100)
    }

    @Test
    fun `clamp edge image blur is an exact prepared native binding refusal`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderFixtureThroughSurface(
                image = opaqueRedLeftEdge(width = 9, height = 9),
                paint = Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
                dst = RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                surfaceSize = 32,
            )
        }
    }

    @Test
    fun `image filter refusal takes precedence over fractional AA clip lowering`() {
        requireWebGpu()
        listOf(false, true).forEach { clip ->
            assertPreparedImageFilterRefusal {
                Surface(width = 32, height = 32).run {
                canvas {
                    if (clip) {
                        save()
                        clipRect(RectF32(7.5f, 0f, 24f, 32f), ClipOp.INTERSECT, antiAlias = true)
                    }
                    drawImage(
                        opaqueRedLeftEdge(width = 9, height = 9),
                        RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                        Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
                    )
                    if (clip) restore()
                }
                    render()
                }
            }
        }
    }

    @Test
    fun `partial source image blur is an exact prepared native binding refusal`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderImageRectThroughSurface(
                image = blueImageWithOpaqueRedCenterCrop(),
                src = RectF32(1f, 1f, 3f, 3f),
                dst = RectF32.ofOriginSize(10f, 10f, 2f, 2f),
                paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.CLAMP)),
                surfaceSize = 32,
            )
        }
    }

    @Test
    fun `zero sigma image filter remains an exact prepared native binding refusal`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderFixtureThroughSurface(
                opaqueRedImpulse(9, 9, 4, 4),
                Paint(imageFilter = ImageFilter.Blur(0f, 0f)),
                RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                32,
            )
        }
    }

    @Test
    fun `filtered image after a valid image terminates the frame instead of returning legacy stats`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderFixtureResult(
                Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.REPEAT)),
            )
        }
    }

    @Test
    fun `isolated repeat image blur is an exact prepared native binding refusal`() {
        requireWebGpu()
        assertPreparedImageFilterRefusal {
            renderIsolatedFixtureResult(
                Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.REPEAT)),
            )
        }
    }

    private fun renderFixtureThroughSurface(
        image: Image,
        paint: Paint,
        dst: RectF32,
        surfaceSize: Int,
    ): ByteArray {
        val surface = Surface(surfaceSize, surfaceSize)
        surface.canvas { drawImage(image, dst, paint) }
        return surface.render().pixels.toByteArray()
    }

    private fun renderImageRectThroughSurface(
        image: Image,
        src: RectF32,
        dst: RectF32,
        paint: Paint,
        surfaceSize: Int,
    ): ByteArray {
        val surface = Surface(surfaceSize, surfaceSize)
        surface.canvas { drawImageRect(image, src, dst, paint) }
        return surface.render().pixels.toByteArray()
    }

    private fun renderFixtureResult(paint: Paint) = Surface(32, 32).run {
        canvas {
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                RectF32.ofOriginSize(0f, 0f, 9f, 9f),
                Paint(),
            )
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                paint,
            )
        }
        render()
    }

    private fun renderIsolatedFixtureResult(paint: Paint) = Surface(32, 32).run {
        canvas {
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                RectF32.ofOriginSize(8f, 8f, 9f, 9f),
                paint,
            )
        }
        render()
    }

    private fun opaqueRedImpulse(width: Int, height: Int, centerX: Int, centerY: Int): Image {
        val pixels = ByteArray(width * height * 4)
        val offset = (centerY * width + centerX) * 4
        pixels[offset] = 0xff.toByte()
        pixels[offset + 3] = 0xff.toByte()
        return Image.fromPixels(
            width,
            height,
            pixels,
            ColorType.RGBA_8888,
            "red-impulse",
            AlphaType.PREMUL,
        )
    }

    private fun opaqueRedLeftEdge(width: Int, height: Int): Image {
        val pixels = ByteArray(width * height * 4)
        for (y in 0 until height) {
            val offset = (y * width) * 4
            pixels[offset] = 0xff.toByte()
            pixels[offset + 3] = 0xff.toByte()
        }
        return Image.fromPixels(
            width,
            height,
            pixels,
            ColorType.RGBA_8888,
            "red-left-edge",
            AlphaType.PREMUL,
        )
    }

    private fun blueImageWithOpaqueRedCenterCrop(): Image {
        val pixels = ByteArray(4 * 4 * 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val offset = (y * 4 + x) * 4
                pixels[offset + 2] = 0xff.toByte()
                pixels[offset + 3] = 0xff.toByte()
                if (x in 1..2 && y in 1..2) {
                    pixels[offset] = 0xff.toByte()
                    pixels[offset + 2] = 0
                }
            }
        }
        return Image.fromPixels(
            4,
            4,
            pixels,
            ColorType.RGBA_8888,
            "blue-red-crop",
            AlphaType.PREMUL,
        )
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPreparedImageFilterRefusal(block: () -> Any?) {
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> { block() }
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, failure.diagnostic.code.value)
        assertEquals("unsupported_paint_effect", failure.diagnostic.facts["reason"])
        assertEquals("imageFilter", failure.diagnostic.facts["paintField"])
    }

    private fun alphaAt(pixels: ByteArray, x: Int, y: Int, width: Int): Int =
        pixels[(y * width + x) * 4 + 3].toInt() and 0xff

}
