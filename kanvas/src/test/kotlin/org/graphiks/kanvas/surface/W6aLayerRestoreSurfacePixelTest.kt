@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public W6a restore contract. Every expected pixel is calculated before its Surface exists;
 * the compact CPU equations deliberately do not invoke a planner, renderer, or test control.
 */
class W6aLayerRestoreSurfacePixelTest {
    @Test
    fun `restoreAlphaAppliesOnceToOverlappingChildren`() {
        // Opaque blue is the final child result. The independent CPU oracle applies 128/255 in
        // linear premultiplied light, then encodes the attachment bytes exactly once.
        val expected = groupAlphaCpu(0, 0, 255, 255, 128)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(paint = Paint(color = ColorARGB.of(128, 0, 0, 0), blendMode = BlendMode.SRC, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `restoreColorFilterRunsAfterGroupAlpha`() {
        // Alpha first makes the layer transparent; the restore matrix then creates opaque red.
        // Reversing that order would multiply the created alpha back to zero.
        val expected = rgbaCpu(255, 0, 0, 255)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(paint = Paint(
                color = ColorARGB.of(0, 0, 0, 0),
                colorFilter = opaqueRedFromAnyInput(),
                blendMode = BlendMode.SRC,
                antiAlias = false,
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `fixedFunctionRestoreBlendTargetsImmediateParent`() {
        // DST_OVER with an opaque blue parent is exactly the parent. SRC_OVER would expose red.
        val expected = dstOverOpaqueCpu(17, 61, 211, 239, 51, 73)
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            saveLayer(paint = Paint(blendMode = BlendMode.DST_OVER, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `destinationReadRestoreUsesFreshParentVersion`() {
        // Difference must read the immediately preceding blue parent, not the older red parent.
        val expected = differenceOpaqueCpu(239, 51, 73, 17, 61, 211)
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            saveLayer(paint = Paint(blendMode = BlendMode.DIFFERENCE, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `emptyLayerColorFilterThatCreatesAlphaIsNotElided`() {
        // Transparent black through this matrix becomes opaque green, so an empty layer is not a no-op.
        val expected = rgbaCpu(0, 255, 0, 255)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(paint = Paint(
                colorFilter = opaqueGreenFromTransparentBlack(),
                blendMode = BlendMode.SRC,
                antiAlias = false,
            ))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `transparentSourceClearSrcAndDstInUseTheParentClip`() {
        val parent = rgbaCpu(17, 61, 211, 255)
        val transparent = rgbaCpu(0, 0, 0, 0)
        val cases = listOf(
            BlendMode.CLEAR to parent + transparent + parent,
            BlendMode.SRC to parent + transparent + parent,
            BlendMode.DST to parent + parent + parent,
        )
        cases.forEach { (mode, expected) ->
            val surface = Surface(3, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 3f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
                clipRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), antiAlias = false)
                saveLayer(paint = Paint(blendMode = mode, antiAlias = false))
                restore()
            }
            assertContentEquals(expected, surface.render().pixels, "mode=$mode")
        }
    }

    @Test
    fun `restoreIgnoresGeometryPaintAttributes`() {
        val expected = rgbaCpu(239, 51, 73, 255)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(paint = Paint(
                blendMode = BlendMode.SRC,
                style = PaintStyle.STROKE,
                strokeWidth = 100f,
                pathEffect = PathEffect.Dash(floatArrayOf(1f, 1f)),
                antiAlias = false,
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    private fun opaqueRedFromAnyInput(): ColorFilter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
        0f, 0f, 0f, 0f, 1f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 1f,
    )))

    private fun opaqueGreenFromTransparentBlack(): ColorFilter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 1f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 1f,
    )))

    /** SRC alpha multiplication rounded to the RGBA8 attachment lattice, independently of W6. */
    private fun groupAlphaCpu(red: Int, green: Int, blue: Int, alpha: Int, groupAlpha: Int): UByteArray =
        rgbaCpu(
            encodedPremulByte(red, groupAlpha),
            encodedPremulByte(green, groupAlpha),
            encodedPremulByte(blue, groupAlpha),
            (alpha * groupAlpha + 127) / 255,
        )

    private fun encodedPremulByte(encoded: Int, alpha: Int): Int {
        val encodedUnit = encoded / 255.0
        val linear = if (encodedUnit <= 0.04045) encodedUnit / 12.92 else ((encodedUnit + 0.055) / 1.055).pow(2.4)
        val premul = linear * alpha / 255.0
        val encodedPremul = if (premul <= 0.0031308) premul * 12.92 else 1.055 * premul.pow(1.0 / 2.4) - 0.055
        return (encodedPremul * 255.0).roundToInt()
    }

    /** Opaque destination is in front of the source for DST_OVER. */
    private fun dstOverOpaqueCpu(destinationRed: Int, destinationGreen: Int, destinationBlue: Int,
        sourceRed: Int, sourceGreen: Int, sourceBlue: Int): UByteArray {
        require(listOf(destinationRed, destinationGreen, destinationBlue, sourceRed, sourceGreen, sourceBlue).all { it in 0..255 })
        return rgbaCpu(destinationRed, destinationGreen, destinationBlue, 255)
    }

    /** For two opaque pixels, W3C Difference is channel-wise absolute difference in linear light. */
    private fun differenceOpaqueCpu(sourceRed: Int, sourceGreen: Int, sourceBlue: Int,
        destinationRed: Int, destinationGreen: Int, destinationBlue: Int): UByteArray =
        rgbaCpu(
            encodedLinearDifferenceByte(sourceRed, destinationRed),
            encodedLinearDifferenceByte(sourceGreen, destinationGreen),
            encodedLinearDifferenceByte(sourceBlue, destinationBlue),
            255,
        )

    private fun encodedLinearDifferenceByte(source: Int, destination: Int): Int {
        fun decode(value: Int): Double {
            val encoded = value / 255.0
            return if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)
        }
        val linear = abs(decode(source) - decode(destination))
        val encoded = if (linear <= 0.0031308) linear * 12.92 else 1.055 * linear.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt()
    }

    private fun rgbaCpu(red: Int, green: Int, blue: Int, alpha: Int): UByteArray {
        require(listOf(red, green, blue, alpha).all { it in 0..255 })
        return ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())
    }
}
