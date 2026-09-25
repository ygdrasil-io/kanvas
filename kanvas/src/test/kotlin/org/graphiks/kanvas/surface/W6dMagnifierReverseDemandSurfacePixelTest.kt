@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public witnesses that a Magnifier retains every input texel its inverse sampling can read. */
class W6dMagnifierReverseDemandSurfacePixelTest {
    @Test
    fun `backdrop magnifier zooming out snapshots left input beyond lens and output clip`() {
        // At device pixel center x=1.5, the lens [1,4] with center 2.5 and zoom .5 samples
        // x=.5, i.e. source texel 0.  The independent attachment oracle therefore replaces
        // only clipped x=1 with red; it is fixed before recording the Surface.
        val expected = red + red + blue + yellow + white
        val surface = Surface(5, 1)

        surface.canvas {
            drawBackdropFixture(this)
            clipRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), antiAlias = false)
            saveLayer(SaveLayerRec(
                backdrop = ImageFilter.Magnifier(RectF32.ofLTRB(1f, 0f, 4f, 1f), zoom = .5f, inset = 0f),
                paint = Paint(blendMode = BlendMode.SRC, antiAlias = false),
            ))
            restore()
        }

        assertPublicPixels(expected, surface, RectF32.ofLTRB(0f, 0f, 5f, 1f))
    }

    @Test
    fun `translated direct magnifier composes draw mapping before reserving clipped input`() {
        // At local x=.5, lens [0,10] at zoom 2 samples local x=2.75, nearest texel 2.
        // The device clip exposes only x=100, so a prepass that maps the lens before applying
        // the captured +100 draw transform incorrectly retains only source texel 100.
        val source = directSourcePixels()
        val expected = UByteArray(110 * 4).also { source.copyInto(it, destinationOffset = 100 * 4,
            startIndex = 2 * 4, endIndex = 3 * 4) }
        val image = Image.fromPixels(10, 1, source.toByteArray(), sourceId = "w6d-magnifier-direct-source",
            alphaType = AlphaType.PREMUL)
        val surface = Surface(110, 1)

        surface.canvas {
            translate(100f, 0f)
            clipRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 10f, 1f), Paint(
                shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
                imageFilter = ImageFilter.Magnifier(RectF32.ofLTRB(0f, 0f, 10f, 1f), zoom = 2f, inset = 0f),
                antiAlias = false,
            ))
        }

        assertPublicPixels(expected, surface, RectF32.ofLTRB(100f, 0f, 101f, 1f))
    }

    private fun drawBackdropFixture(canvas: Canvas) {
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(3f, 0f, 4f, 1f), Paint(ColorARGB.Yellow, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(4f, 0f, 5f, 1f), Paint(ColorARGB.White, antiAlias = false))
    }

    private fun directSourcePixels(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u,
        0u, 255u, 0u, 255u,
        0u, 0u, 255u, 255u,
        255u, 255u, 0u, 255u,
        255u, 0u, 255u, 255u,
        0u, 255u, 255u, 255u,
        255u, 128u, 0u, 255u,
        128u, 0u, 255u, 255u,
        0u, 128u, 255u, 255u,
        255u, 255u, 255u, 255u,
    )

    private fun assertPublicPixels(expected: UByteArray, surface: Surface, readbackBounds: RectF32) {
        val rendered = surface.render()
        assertTrue(rendered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            rendered.nativeEvidenceScopeKinds.toString())
        assertNear(expected, rendered.pixels)

        val readback = UByteArray(readbackBounds.width().toInt() * readbackBounds.height().toInt() * 4)
        assertTrue(surface.readPixels(readbackBounds, readback))
        val offset = readbackBounds.left.toInt() * 4
        assertNear(expected.copyOfRange(offset, offset + readback.size), readback)
    }

    private fun assertNear(expected: UByteArray, actual: UByteArray) {
        assertTrue(expected.size == actual.size, "expected size=${expected.size} actual size=${actual.size}")
        expected.indices.forEach { index -> assertTrue(abs(expected[index].toInt() - actual[index].toInt()) <= 1,
            "channel $index expected=${expected[index]} actual=${actual[index]}") }
    }

    private companion object {
        val red: UByteArray = ubyteArrayOf(255u, 0u, 0u, 255u)
        val blue: UByteArray = ubyteArrayOf(0u, 0u, 255u, 255u)
        val yellow: UByteArray = ubyteArrayOf(255u, 255u, 0u, 255u)
        val white: UByteArray = ubyteArrayOf(255u, 255u, 255u, 255u)
    }
}
