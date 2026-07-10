package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUImageFilterSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `sigma two image blur matches cpu oracle and expands beyond opaque impulse`() {
        requireWebGpu()
        val actual = renderFixtureThroughSurface(
            image = opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
            dst = Rect.fromXYWH(8f, 8f, 9f, 9f),
            surfaceSize = 32,
        )
        val expected = ImageFilterBlurCpuOracle.clampBlurImpulseInSurface(
            surfaceSize = 32,
            originX = 8,
            originY = 8,
            sigmaX = 2f,
            sigmaY = 2f,
        )

        assertRgbaWithin(expected, actual, tolerance = 2)
        // NORMAL sigma=2 uses five taps, so it has a two-pixel support radius.
        // (10, 12) is outside the one-pixel impulse while remaining in that support.
        assertTrue(alphaAt(actual, 10, 12, 32) > 0)
        assertTrue(alphaAt(actual, 12, 12, 32) < 255)
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
    fun `clamp blur keeps an opaque edge in the exterior halo`() {
        requireWebGpu()
        val actual = renderFixtureThroughSurface(
            image = opaqueRedLeftEdge(width = 9, height = 9),
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP)),
            dst = Rect.fromXYWH(8f, 8f, 9f, 9f),
            surfaceSize = 32,
        )
        val expected = ImageFilterBlurCpuOracle.clampBlurOpaqueRedLeftEdgeInSurface(
            surfaceSize = 32,
            originX = 8,
            originY = 8,
            sigmaX = 2f,
            sigmaY = 2f,
        )

        assertRgbaWithin(expected, actual, tolerance = 2)
        // One pixel left of dst: CLAMP replicates the opaque left edge there.
        assertTrue(alphaAt(actual, 7, 12, 32) >= 200)
    }

    @Test
    fun `zero sigma image filter is byte identical to unfiltered image`() {
        requireWebGpu()
        val image = opaqueRedImpulse(9, 9, 4, 4)
        val dst = Rect.fromXYWH(8f, 8f, 9f, 9f)

        val filtered = renderFixtureThroughSurface(
            image,
            Paint(imageFilter = ImageFilter.Blur(0f, 0f)),
            dst,
            32,
        )
        val plain = renderFixtureThroughSurface(image, Paint(), dst, 32)

        assertArrayEquals(plain, filtered)
    }

    @Test
    fun `refused filtered image increments refusal count without negative stats`() {
        requireWebGpu()
        val result = renderFixtureResult(
            Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.REPEAT)),
        )

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(1, result.stats.opsRefused)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.image-filter.blur.tile-mode",
            result.diagnostics.entries.single().reason,
        )
    }

    @Test
    fun `isolated repeat image blur is refused without a dispatch`() {
        requireWebGpu()
        val result = renderIsolatedFixtureResult(
            Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.REPEAT)),
        )

        assertEquals(0, result.stats.opsDispatched)
        assertEquals(1, result.stats.opsRefused)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.image-filter.blur.tile-mode",
            result.diagnostics.entries.single().reason,
        )
    }

    private fun renderFixtureThroughSurface(
        image: Image,
        paint: Paint,
        dst: Rect,
        surfaceSize: Int,
    ): ByteArray {
        val surface = Surface(surfaceSize, surfaceSize)
        surface.canvas { drawImage(image, dst, paint) }
        return surface.render().pixels.toByteArray()
    }

    private fun renderFixtureResult(paint: Paint) = Surface(32, 32).run {
        canvas {
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                Rect.fromXYWH(0f, 0f, 9f, 9f),
                Paint(),
            )
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                Rect.fromXYWH(8f, 8f, 9f, 9f),
                paint,
            )
        }
        render()
    }

    private fun renderIsolatedFixtureResult(paint: Paint) = Surface(32, 32).run {
        canvas {
            drawImage(
                opaqueRedImpulse(width = 9, height = 9, centerX = 4, centerY = 4),
                Rect.fromXYWH(8f, 8f, 9f, 9f),
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
        return Image.fromPixels(width, height, pixels, ColorType.RGBA_8888, "red-impulse")
    }

    private fun opaqueRedLeftEdge(width: Int, height: Int): Image {
        val pixels = ByteArray(width * height * 4)
        for (y in 0 until height) {
            val offset = (y * width) * 4
            pixels[offset] = 0xff.toByte()
            pixels[offset + 3] = 0xff.toByte()
        }
        return Image.fromPixels(width, height, pixels, ColorType.RGBA_8888, "red-left-edge")
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertRgbaWithin(expected: ByteArray, actual: ByteArray, tolerance: Int) {
        require(expected.size == actual.size) { "pixel buffers have different sizes" }
        expected.indices.forEach { index ->
            assertTrue(abs((expected[index].toInt() and 0xff) - (actual[index].toInt() and 0xff)) <= tolerance) {
                "channel $index expected=${expected[index].toInt() and 0xff} actual=${actual[index].toInt() and 0xff}"
            }
        }
    }

    private fun alphaAt(pixels: ByteArray, x: Int, y: Int, width: Int): Int =
        pixels[(y * width + x) * 4 + 3].toInt() and 0xff
}
