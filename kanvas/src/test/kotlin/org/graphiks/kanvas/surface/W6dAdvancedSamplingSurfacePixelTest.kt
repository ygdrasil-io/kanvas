@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for the first W6d advanced sampling slice. */
class W6dAdvancedSamplingSurfacePixelTest {
    @Test
    fun `convolution matches its independent oracle at clamp edges`() {
        val source = sourcePixels()
        val kernel = floatArrayOf(1f, 0f, 0f)
        val expected = W6dAdvancedSamplingCpuOracle.convolution3x1Clamp(source, kernel, offsetX = 1)
        val surface = Surface(3, 1)

        surface.canvas {
            drawSource(this, ImageFilter.MatrixConvolution(
                SizeF32.of(3f, 1f), kernel, 1f, 0f, Vector2F32(1f, 0f), TileMode.CLAMP, true,
            ))
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
    }

    @Test
    fun `convolution honors frozen tile mode at edges`() {
        val source = sourcePixels()
        val kernel = floatArrayOf(1f, 0f, 0f)
        listOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR, TileMode.DECAL).forEach { mode ->
            val expected = W6dAdvancedSamplingCpuOracle.convolution3x1(source, kernel, offsetX = 2, tileMode = mode.name)
            val surface = Surface(3, 1)
            surface.canvas {
                drawSource(this, ImageFilter.MatrixConvolution(
                    SizeF32.of(3f, 1f), kernel, 1f, 0f, Vector2F32(2f, 0f), mode, true,
                ))
            }
            assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
        }
    }

    @Test
    fun `displacement uses selected channels and implicit source`() {
        val source = sourcePixels()
        val expected = W6dAdvancedSamplingCpuOracle.displacementRedNearestClamp(source, scale = 1f)
        val surface = Surface(3, 1)

        surface.canvas {
            drawSource(this, ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 1f,
                ImageFilter.Offset(0f, 0f)))
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
    }

    @Test
    fun `displacement maps a shifted map in its own frozen target-local coordinates`() {
        val source = sourcePixels()
        val expected = W6dAdvancedSamplingCpuOracle.displacementOffsetMapRedNearestClamp(source, mapOffsetX = 1, scale = 1f)
        val surface = Surface(3, 1)

        surface.canvas {
            drawSource(this, ImageFilter.DisplacementMap(
                ColorChannel.R,
                ColorChannel.G,
                1f,
                ImageFilter.Offset(1f, 0f),
            ))
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
    }

    @Test
    fun `magnifier honors lens inset and origin`() {
        val source = sourcePixels()
        val expected = W6dAdvancedSamplingCpuOracle.magnifierNearestClamp(source, -1f, 3f, zoom = 2f, inset = .5f)
        val surface = Surface(3, 1)

        surface.canvas {
            drawSource(this, ImageFilter.Magnifier(RectF32.ofLTRB(-1f, 0f, 3f, 1f), zoom = 2f, inset = .5f))
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
    }

    @Test
    fun `magnifier localizes its lens through a translated nonzero-origin layer`() {
        val source = sourcePixels()
        val localExpected = W6dAdvancedSamplingCpuOracle.magnifierNearestClamp(source, 1f, 4f, zoom = 2f, inset = .5f)
        val expected = UByteArray(5 * 4).also { localExpected.copyInto(it, destinationOffset = 4) }
        val surface = Surface(5, 1)

        surface.canvas {
            translate(1f, 0f)
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(0f, 0f, 3f, 1f),
                paint = Paint(imageFilter = ImageFilter.Magnifier(
                    RectF32.ofLTRB(1f, 0f, 4f, 1f), zoom = 2f, inset = .5f,
                ), antiAlias = false),
            ))
            drawSourceRects(this)
            restore()
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 1)
    }

    @Test
    fun `magnifier inset excludes a lens edge pixel`() {
        val source = sourcePixels()
        val expected = W6dAdvancedSamplingCpuOracle.magnifierNearestClamp(source, -1f, 3f, zoom = 2f, inset = 1f)
        val withoutInset = W6dAdvancedSamplingCpuOracle.magnifierNearestClamp(source, -1f, 3f, zoom = 2f, inset = 0f)
        assertTrue(!expected.contentEquals(withoutInset), "The lens-edge fixture must discriminate inset.")
        listOf(1f to expected, 0f to withoutInset).forEach { (inset, pixels) ->
            val surface = Surface(3, 1)
            surface.canvas {
                // Keep the vertical coordinate inside the lens for both insets.
                drawSource(this, ImageFilter.Magnifier(RectF32.ofLTRB(-1f, -2f, 3f, 3f), zoom = 2f, inset = inset))
            }
            assertFamilyNear(pixels, surface.render(), maxChannelDelta = 1)
        }
    }

    private fun sourcePixels(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u,
        0u, 255u, 0u, 255u,
        0u, 0u, 255u, 255u,
    )

    private fun drawSource(canvas: Canvas, filter: ImageFilter) {
        canvas.saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
        drawSourceRects(canvas)
        canvas.restore()
    }

    private fun drawSourceRects(canvas: Canvas) {
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
    }

    private fun assertFamilyNear(expected: UByteArray, actual: RenderResult, maxChannelDelta: Int) {
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            actual.nativeEvidenceScopeKinds.toString())
        val pixels = actual.pixels
        assertTrue(pixels.size == expected.size)
        expected.indices.forEach { index ->
            assertTrue(abs(expected[index].toInt() - pixels[index].toInt()) <= maxChannelDelta,
                "channel $index expected=${expected[index]} actual=${pixels[index]}")
        }
    }
}
