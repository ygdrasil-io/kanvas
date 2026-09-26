@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for W6's pre-reservation contextual filter recipe. */
class W6FilterBoundsRecipeSurfacePixelTest {
    /**
     * Unioning the logical convolution halo into the sampled source would insert transparent
     * texels before its left CLAMP edge. The one initialized blue texel must remain that edge.
     */
    @Test
    fun reverseHaloDoesNotReplaceClampedLayerSourceEdge() {
        val expected = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 255u, 255u, 0u, 0u, 0u, 0u)
        val filter = ImageFilter.MatrixConvolution(SizeF32.of(3f, 1f), floatArrayOf(1f, 0f, 0f),
            1f, 0f, Vector2F32(1f, 0f), TileMode.CLAMP, true)
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }
        assertRenderAndReadback(surface, expected)
    }

    /**
     * Removing the recipe's forward-produced bounds must erase the offset pixel at x=1 and the
     * blur halo around the 7x7 impulse, even though neither belongs to the raw child content.
     */
    @Test
    fun nestedOffsetAndBlurExpandOnlyTheirProducedOutput() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val offsetExpected = transparent + blue + transparent + transparent
        val offsetSurface = Surface(4, 1)
        offsetSurface.canvas {
            saveLayer()
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
            restore()
            restore()
        }
        assertRenderAndReadback(offsetSurface, offsetExpected)

        val blurAlpha = W6bImageBlurCpuOracle.blurredAlpha(
            7, 7, UByteArray(49).also { it[3 + 3 * 7] = 255u }, 1f, 1f, TileMode.DECAL,
        )
        val blurExpected = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(blurAlpha)
        val blurSurface = Surface(7, 7)
        blurSurface.canvas {
            saveLayer()
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL), antiAlias = false)))
            drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(ColorARGB.White, antiAlias = false))
            restore()
            restore()
            restore()
        }
        assertRenderAndReadback(blurSurface, blurExpected, tolerance = 12)
    }

    /**
     * Removing direct auto-layer production from the parent known content clips the x=1 Offset
     * pixel and the Blur halo when this otherwise unfiltered layer is restored.
     */
    @Test
    fun directOffsetAndBlurSurviveUnfilteredParent() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val offsetExpected = transparent + blue + transparent + transparent
        val offsetSurface = Surface(4, 1)
        offsetSurface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                ColorARGB.Blue, imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false,
            ))
            restore()
        }
        assertRenderAndReadback(offsetSurface, offsetExpected)

        val blurExpected = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(W6bImageBlurCpuOracle.blurredAlpha(
            7, 7, UByteArray(49).also { it[3 + 3 * 7] = 255u }, 1f, 1f, TileMode.DECAL,
        ))
        val blurSurface = Surface(7, 7)
        blurSurface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(
                ColorARGB.White, imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL), antiAlias = false,
            ))
            restore()
        }
        assertRenderAndReadback(blurSurface, blurExpected, tolerance = 12)
    }

    /** A non-writing direct filter is a no-op even when a sibling gives its parent a smaller target. */
    @Test
    fun nonWritingDirectFilterWithSeparateWritingSiblingIsNoOpAndRecovers() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val expected = blue + transparent + transparent + transparent + transparent + transparent
        val surface = Surface(6, 1)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(5f, 0f, 6f, 1f), Paint(
                ColorARGB.Blue, blendMode = BlendMode.DST,
                imageFilter = ImageFilter.Offset(0f, 0f), antiAlias = false,
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertRenderAndReadback(surface, expected)
    }

    /** A removed DST source must also skip mask-shader capture and leave the surface reusable. */
    @Test
    fun nonWritingDirectMaskShaderWithSeparateWritingSiblingIsNoOpAndRecovers() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val red = ubyteArrayOf(255u, 0u, 0u, 255u)
        val expected = blue + transparent + transparent + transparent + transparent + transparent
        val recoveryExpected = transparent + transparent + transparent + transparent + transparent + red
        val surface = Surface(6, 1)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(5f, 0f, 6f, 1f), Paint(
                ColorARGB.Blue, blendMode = BlendMode.DST,
                maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)), antiAlias = false,
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }
        assertRenderAndReadback(surface, expected)

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(5f, 0f, 6f, 1f), Paint(ColorARGB.Red, antiAlias = false))
        }
        assertRenderAndReadback(surface, recoveryExpected)
    }

    /** A prematurely clipped Compose intermediate cannot return from +20 to the terminal x=0. */
    @Test
    fun composeKeepsIntermediateOutsideTerminalClip() {
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val filter = ImageFilter.Compose(ImageFilter.Offset(-20f, 0f), ImageFilter.Offset(20f, 0f))
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
            restore()
        }

        assertRenderAndReadback(surface, expected)
    }

    /** Lighting must receive the outer Offset's inverse demand, not the terminal clip. */
    @Test
    fun composeLightingProducesInItsOwnDemandOutsideTerminalClip() {
        val expected = ubyteArrayOf(255u, 255u, 255u, 255u)
        val filter = ImageFilter.Compose(ImageFilter.Offset(-20f, 0f),
            ImageFilter.DistantLitDiffuse(Vector3F32(0f, 0f, 1f), ColorARGB.White, 0f, 1f))
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
            restore()
        }
        assertRenderAndReadback(surface, expected)
    }

    /**
     * The independent Sobel oracle requires both source-edge CLAMP and transparent requested
     * output.  Collapsing the source domain to the final allocation changes this 3x3 result.
     */
    @Test
    fun nestedLightingRetainsSourceEdgeAndTransparentOutput() {
        val alpha = FloatArray(9).also { it[4] = 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(
            3, 3, alpha, 1, 1, 2, 2,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f,
        )
        assertTrue(expected[0].toInt() > 0, "The transparent requested edge texel must be lit by the independent oracle.")
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.DistantLitDiffuse(
                Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f), antiAlias = false)))
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.White, antiAlias = false))
            restore()
            restore()
        }

        assertRenderAndReadback(surface, expected, tolerance = 2)
    }

    private fun assertRenderAndReadback(surface: Surface, expected: UByteArray, tolerance: Int = 0) {
        val actual = surface.render()
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), actual.nativeEvidenceScopeKinds.toString())
        if (tolerance == 0) assertContentEquals(expected, actual.pixels)
        else W6bImageBlurCpuOracle.assertNear(expected, actual.pixels, tolerance)
    }
}
