package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.pow
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
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
    fun `fractional AA device clip applies one coverage factor to an image blur halo`() {
        requireWebGpu()

        listOf(BlendMode.SRC_OVER, BlendMode.SRC).forEach { blendMode ->
            fun render(clip: Boolean): ByteArray = Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                    if (clip) {
                        save()
                        clipRect(Rect(7.5f, 0f, 24f, 32f), ClipOp.INTERSECT, antiAlias = true)
                    }
                    drawImage(
                        opaqueRedLeftEdge(width = 9, height = 9),
                        Rect.fromXYWH(8f, 8f, 9f, 9f),
                        Paint(imageFilter = ImageFilter.Blur(2f, 2f, TileMode.CLAMP), blendMode = blendMode),
                    )
                    if (clip) restore()
                }
                render().pixels.toByteArray()
            }

            val offset = (12 * 32 + 7) * 4
            val fullPixels = render(clip = false)
            val clippedPixels = render(clip = true)
            val full = IntArray(4) { channel -> fullPixels[offset + channel].toInt() and 0xff }
            val actual = IntArray(4) { channel -> clippedPixels[offset + channel].toInt() and 0xff }
            fun expected(coverage: Float, channel: Int): Int = if (channel == 3) {
                (255f + coverage * (full[channel] - 255)).toInt()
            } else {
                (linearToSrgb((1f - coverage) + coverage * srgbToLinear(full[channel] / 255f)) * 255f).toInt()
            }

            // The fractional DeviceRect coverage F=0.5 is applied after the blur exactly once.
            // The old route produced the distinguishable F² result at this halo edge.
            (0 until 4).forEach { channel ->
                assertTrue(
                    abs(actual[channel] - expected(.5f, channel)) <= 3,
                    "$blendMode channel=$channel actual=${actual[channel]} full=${full[channel]}",
                )
            }
            assertTrue(
                abs(actual[1] - expected(.25f, 1)) > 10,
                "$blendMode must not apply AA coverage twice: actual=${actual[1]} full=${full[1]}",
            )
        }
    }

    @Test
    fun `partial src blur clamps its halo to crop bounds rather than texture bounds`() {
        requireWebGpu()
        val surfaceSize = 32
        val dst = Rect.fromXYWH(10f, 10f, 2f, 2f)
        val actual = renderImageRectThroughSurface(
            image = blueImageWithOpaqueRedCenterCrop(),
            src = Rect(1f, 1f, 3f, 3f),
            dst = dst,
            paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.CLAMP)),
            surfaceSize = surfaceSize,
        )
        val expected = opaqueRedCropClampOracle(surfaceSize, dst, halo = 3)

        assertRgbaWithin(expected, actual, tolerance = 2)
        val haloPixel = (7 * surfaceSize + 7) * 4
        assertEquals(255, actual[haloPixel].toInt() and 0xff)
        assertEquals(0, actual[haloPixel + 2].toInt() and 0xff)
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
        assertEquals(1, result.diagnostics.entries.size, result.diagnostics.entries.toString())
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

    private fun renderImageRectThroughSurface(
        image: Image,
        src: Rect,
        dst: Rect,
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
        return Image.fromPixels(4, 4, pixels, ColorType.RGBA_8888, "blue-red-crop")
    }

    private fun opaqueRedCropClampOracle(surfaceSize: Int, dst: Rect, halo: Int): ByteArray {
        val pixels = ByteArray(surfaceSize * surfaceSize * 4)
        val left = dst.left.toInt() - halo
        val top = dst.top.toInt() - halo
        val right = dst.right.toInt() + halo
        val bottom = dst.bottom.toInt() + halo
        for (y in top until bottom) {
            for (x in left until right) {
                val offset = (y * surfaceSize + x) * 4
                pixels[offset] = 0xff.toByte()
                pixels[offset + 3] = 0xff.toByte()
            }
        }
        return pixels
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

    private fun srgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float =
        if (value <= 0.0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - 0.055f
}
