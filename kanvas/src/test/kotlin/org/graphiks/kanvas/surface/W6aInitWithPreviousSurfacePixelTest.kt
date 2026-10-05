@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public W6a contract for the snapshot taken at a layer's logical save point.  Every expected
 * byte sequence is calculated before its Surface is created and never observes plan internals.
 */
class W6aInitWithPreviousSurfacePixelTest {
    @Test
    fun `defaultFalseStartsTransparent`() {
        val transparent = rgba(0, 0, 0, 0)
        val surface = Surface(1, 1)
        surface.canvas {
            drawOpaque(0f, 1f, red)
            saveLayer(paint = Paint(blendMode = BlendMode.SRC, antiAlias = false))
            restore()
        }

        assertContentEquals(transparent, surface.render().pixels)
    }

    @Test
    fun `copiesParentAtSaveTime`() {
        val expected = rgba(17, 61, 211) + rgba(239, 51, 73)
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 2f, red)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
            drawOpaque(0f, 1f, blue)
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `nestedPreviousReadsItsParentLayer`() {
        val expected = rgba(43, 181, 93) + rgba(17, 61, 211)
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 2f, red)
            saveLayer()
            drawOpaque(0f, 2f, blue)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
            drawOpaque(0f, 1f, green)
            restore()
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `emptyPreviousLayerPreservesParent`() {
        val expected = rgba(239, 51, 73)
        val surface = Surface(1, 1)
        surface.canvas {
            drawOpaque(0f, 1f, red)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `previousPlusAlphaIgnoresRestrictiveHint`() {
        val halfBlack = groupAlpha(ColorARGB.Black, 128)
        val halfWhite = groupAlpha(ColorARGB.White, 128)
        val expected = halfBlack + halfWhite + halfBlack + halfBlack
        val surface = Surface(4, 1)
        surface.canvas {
            drawOpaque(0f, 4f, ColorARGB.Black)
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 0f, 3f, 1f),
                paint = Paint(color = ColorARGB.of(128, 0, 0, 0), blendMode = BlendMode.SRC, antiAlias = false),
                initWithPrevious = true,
            ))
            drawOpaque(1f, 2f, ColorARGB.White)
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `previousPlusColorFilterUsesFullDesiredOutput`() {
        // Literal CPU oracle: the matrix preserves alpha and writes zero into every color
        // channel. It therefore transforms both the copied red parent outside the hint and the
        // blue child inside it into opaque black.
        val expected = rgba(0, 0, 0) + rgba(0, 0, 0) + rgba(0, 0, 0) + rgba(0, 0, 0)
        val blacken = ColorMatrixF32.of(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val surface = Surface(4, 1)
        surface.canvas {
            drawOpaque(0f, 4f, red)
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 0f, 3f, 1f),
                paint = Paint(colorFilter = ColorFilter.Matrix(blacken), antiAlias = false),
                initWithPrevious = true,
            ))
            drawOpaque(1f, 2f, blue)
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `identityPreviousUsesHintSizedTargetWithinBudget`() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(239, 51, 73) + rgba(239, 51, 73)
        // 4x1 root/readback/geometry plus a 1x1 layer is 324 bytes. A wrongly full-domain
        // previous layer needs 12 additional bytes and must not be allocated for identity SRC_OVER.
        val surface = Surface(4, 1, config = RenderConfig(frameLocalBudgetBytes = 324L))
        surface.canvas {
            drawOpaque(0f, 4f, red)
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 0f, 2f, 1f),
                initWithPrevious = true,
            ))
            drawOpaque(1f, 2f, blue)
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `previousPlusDestinationReadBlendUsesFreshParentVersion`() {
        val expected = rgba(0, 0, 0)
        val surface = Surface(1, 1)
        surface.canvas {
            drawOpaque(0f, 1f, red)
            drawOpaque(0f, 1f, blue)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.DIFFERENCE, antiAlias = false)))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawOpaque(left: Float, right: Float, color: ColorARGB) {
        drawRect(RectF32.ofLTRB(left, 0f, right, 1f), Paint(color, antiAlias = false))
    }

    /** Premultiplied encoded-sRGB bytes for an opaque color with independently applied group alpha. */
    private fun groupAlpha(color: ColorARGB, alpha: Int): UByteArray = rgba(
        encodedPremulByte(color.red, alpha),
        encodedPremulByte(color.green, alpha),
        encodedPremulByte(color.blue, alpha),
        alpha,
    )

    private fun encodedPremulByte(encoded: Int, alpha: Int): Int {
        val encodedUnit = encoded / 255.0
        val linear = if (encodedUnit <= .04045) encodedUnit / 12.92 else ((encodedUnit + .055) / 1.055).pow(2.4)
        val premultiplied = linear * alpha / 255.0
        val result = if (premultiplied <= .0031308) premultiplied * 12.92 else 1.055 * premultiplied.pow(1.0 / 2.4) - .055
        return (result * 255.0).roundToInt()
    }

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    private companion object {
        val red: ColorARGB = ColorARGB.of(255, 239, 51, 73)
        val blue: ColorARGB = ColorARGB.of(255, 17, 61, 211)
        val green: ColorARGB = ColorARGB.of(255, 43, 181, 93)
    }
}
