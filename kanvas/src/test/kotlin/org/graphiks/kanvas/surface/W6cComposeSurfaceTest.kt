@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public pixels for contextual W6c Compose evaluation. */
class W6cComposeSurfaceTest {
    /**
     * A regression in which Compose binds outer to the original layer source would place the
     * luma alpha in pixel zero. The independent expected pixels are transparent then luma(red).
     */
    @Test
    fun composeBindsInnerThenOuterAcrossSurfaceAndPicture() {
        // luma(red) is transparent black with alpha round(0.2126 * 255) = 54.
        val expectedComposeBytes = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 54u)
        val inner = ImageFilter.Offset(1f, 0f)
        val filter = ImageFilter.Compose(ImageFilter.ColorFilter(ColorFilter.Luma), inner)

        val surface = Surface(2, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }

        assertContentEquals(expectedComposeBytes, surface.render().pixels)
    }

    /** The W5f luma graph must be applied once to the shifted image-filter result. */
    @Test
    fun imageColorFilterUsesW5fNumericGraphOnce() {
        val expectedColorFilterBytes = ubyteArrayOf(0u, 0u, 0u, 54u)
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.Red, imageFilter = ImageFilter.ColorFilter(ColorFilter.Luma), antiAlias = false),
            )
        }

        assertContentEquals(expectedColorFilterBytes, surface.render().pixels)
    }

    /** A contextual FilterTarget may feed the outer blur; it is not a mask materialization. */
    @Test
    fun composeAllowsBlurAfterOffsetResult() {
        // A zero-sigma blur is the identity, so Offset(1) moves the opaque red texel to x=1.
        val expectedComposeBytes = ubyteArrayOf(0u, 0u, 0u, 0u, 255u, 0u, 0u, 255u)
        val filter = ImageFilter.Compose(ImageFilter.Blur(0f, 0f), ImageFilter.Offset(1f, 0f))
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, imageFilter = filter, antiAlias = false))
        }

        assertContentEquals(expectedComposeBytes, surface.render().pixels)
    }

    @Test
    fun oversizedBalancedImageColorFilterRefusesBeforeReadbackAndSameSurfaceRecovers() {
        // 1,024 identity matrices require 81,920 W5f dynamic bytes.  The expected terminal
        // refusal is established before Surface creation; a later simple recording must recover.
        var filter: ColorFilter = ColorFilter.Matrix(ColorMatrixF32.ofIdentity())
        repeat(10) { filter = ColorFilter.Compose(filter, filter) }
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val surface = Surface(1, 1)
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, imageFilter = ImageFilter.ColorFilter(filter), antiAlias = false)) }
        val sentinel = UByteArray(4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("resource-limit.w5f.filter-binding:") == true,
            failure.message ?: "missing W5f capability refusal")
        assertContentEquals(UByteArray(4) { 0x5au }, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
    }
}
