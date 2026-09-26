@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.math.pow
import kotlin.math.roundToInt
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for W6's pre-reservation contextual filter recipe. */
class W6FilterBoundsRecipeSurfacePixelTest {
    /**
     * A full 2x1 output clip is deliberately larger than the 1x1 DECAL Crop content.  B is
     * derived before any Surface exists: root 2x1 RGBA8 (8), four 1x1 RGBA8 targets (16:
     * layer, source, Crop and terminal composite), two 16-byte W6 rows (32), and the 2x1
     * readback aligned to 256 bytes.  All terms use checked I64 arithmetic: 8+16+32+256=312.
     * Treating producedOutput as desiredOutput would make a Crop target 2x1 and exceed B.
     * The identity ColorFilter has its own independently stated B/B-1 witness below: the
     * content-sized target accounting is the same, but it is not admitted at a default budget.
     */
    @Test
    fun tinyCropAndIdentityKeepContentSizedBudget() {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 0u, 0u)
        val budgetB = listOf(
            Math.multiplyExact(2L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(4L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(2L, 16L),
            256L,
        ).fold(0L, Math::addExact)
        fun recordCrop(surface: Surface) = surface.canvas {
            clipRect(full, antiAlias = false)
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(unit, TileMode.DECAL), antiAlias = false)))
            drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        val admitted = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        recordCrop(admitted)
        assertRenderAndReadback(admitted, expected)

        val refused = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L)))
        recordCrop(refused)
        val sentinel = UByteArray(8) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(full, sentinel) }
        assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            failure.message ?: "missing W6 budget diagnostic")
        assertContentEquals(before, sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertRenderAndReadback(refused, expected)

        val identity = ImageFilter.ColorFilter(ColorFilter.Matrix(ColorMatrixF32.ofIdentity()))
        // Identity has the same 2x1 root (8), four 1x1 targets (16), and two W6 rows (32),
        // plus its actual 20-F32 matrix uniform (80) and the aligned readback (256): B=392.
        val identityBudgetB = listOf(
            Math.multiplyExact(2L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(4L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(2L, 16L),
            Math.multiplyExact(20L, 4L),
            256L,
        ).fold(0L, Math::addExact)
        fun recordIdentity(surface: Surface) = surface.canvas {
            clipRect(full, antiAlias = false)
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = identity, antiAlias = false)))
            drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        val identityAdmitted = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = identityBudgetB))
        recordIdentity(identityAdmitted)
        assertRenderAndReadback(identityAdmitted, expected)

        val identityRefused = Surface(2, 1, config = RenderConfig(
            frameLocalBudgetBytes = Math.subtractExact(identityBudgetB, 1L),
        ))
        recordIdentity(identityRefused)
        val identitySentinel = UByteArray(8) { 0x5au }
        val identityBefore = identitySentinel.copyOf()
        val identityFailure = assertFailsWith<IllegalStateException> { identityRefused.readPixels(full, identitySentinel) }
        assertTrue(identityFailure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            identityFailure.message ?: "missing W6 budget diagnostic")
        assertContentEquals(identityBefore, identitySentinel)
        identityRefused.discardRecordedOperations()
        identityRefused.canvas { drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertRenderAndReadback(identityRefused, expected)
    }

    /** The backdrop snapshots its parent at save; filtered previous is filtered only after its child. */
    @Test
    fun backdropAndPreviousKeepSaveThenPostChildOrder() {
        val red = ColorARGB.of(255, 241, 53, 106)
        val childBlack = ColorARGB.Black
        val recoveryBlue = ColorARGB.Blue
        val greenFromParent = ColorARGB.of(255, 0, red.red, 0)
        // The matrix maps the saved red parent to green. The .5 black child still leaves that
        // backdrop visible at x=0; then the .5 layer opacity restores it over red. A late
        // snapshot that included the child would map red+black instead and is observably distinct.
        val backdropExpected = backdropChildThenHalfRestore(red, greenFromParent, childBlack) +
            halfSourceOver(red, greenFromParent)
        val lateMatrixInput = halfSourceOverColor(red, childBlack)
        val childContaminatedLateSnapshot = backdropChildThenHalfRestore(
            red, ColorARGB.of(255, 0, lateMatrixInput.red, 0), childBlack,
        ) + halfSourceOver(red, greenFromParent)
        assertTrue(!backdropExpected.contentEquals(childContaminatedLateSnapshot),
            "A child-contaminated late backdrop must differ from the save-time snapshot oracle.")
        val previousExpected = previousChildThenHalfRestore(red, childBlack) + rgba(red.red, red.green, red.blue)
        val recoveryExpected = rgba(recoveryBlue.red, recoveryBlue.green, recoveryBlue.blue) +
            rgba(recoveryBlue.red, recoveryBlue.green, recoveryBlue.blue)
        val parentDependentBackdrop = ImageFilter.ColorFilter(ColorFilter.Matrix(
            ColorMatrixF32.of(floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )),
        ))
        val halfOpacity = ImageFilter.RuntimeEffect(
            requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
            UniformBlock { float1("alpha", .5f) },
        )

        val backdrop = Surface(2, 1)
        backdrop.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(red, antiAlias = false))
            saveLayer(SaveLayerRec(
                backdrop = parentDependentBackdrop,
                paint = Paint(imageFilter = halfOpacity, antiAlias = false),
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                shader = Shader.Opacity(Shader.SolidColor(childBlack), .5f), antiAlias = false,
            ))
            restore()
        }
        assertRenderAndReadback(backdrop, backdropExpected)
        backdrop.discardRecordedOperations()
        backdrop.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(recoveryBlue, antiAlias = false)) }
        assertRenderAndReadback(backdrop, recoveryExpected)

        val previous = Surface(2, 1)
        previous.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(red, antiAlias = false))
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(imageFilter = halfOpacity, antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                shader = Shader.Opacity(Shader.SolidColor(childBlack), .5f), antiAlias = false,
            ))
            restore()
        }
        assertRenderAndReadback(previous, previousExpected)
        previous.discardRecordedOperations()
        previous.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(recoveryBlue, antiAlias = false)) }
        assertRenderAndReadback(previous, recoveryExpected)
    }

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

    private fun halfSourceOver(destination: ColorARGB, source: ColorARGB): UByteArray =
        halfSourceOverColor(destination, source).let { color -> rgba(color.red, color.green, color.blue) }

    private fun halfSourceOverColor(destination: ColorARGB, source: ColorARGB): ColorARGB = ColorARGB.of(255,
        encodeLinear((decodeSrgb(source.red) + decodeSrgb(destination.red)) * .5),
        encodeLinear((decodeSrgb(source.green) + decodeSrgb(destination.green)) * .5),
        encodeLinear((decodeSrgb(source.blue) + decodeSrgb(destination.blue)) * .5),
    )

    private fun backdropChildThenHalfRestore(parent: ColorARGB, backdrop: ColorARGB, child: ColorARGB): UByteArray = rgba(
        encodeLinear(decodeSrgb(parent.red) * .5 + decodeSrgb(backdrop.red) * .25 + decodeSrgb(child.red) * .25),
        encodeLinear(decodeSrgb(parent.green) * .5 + decodeSrgb(backdrop.green) * .25 + decodeSrgb(child.green) * .25),
        encodeLinear(decodeSrgb(parent.blue) * .5 + decodeSrgb(backdrop.blue) * .25 + decodeSrgb(child.blue) * .25),
    )

    private fun previousChildThenHalfRestore(parent: ColorARGB, child: ColorARGB): UByteArray = rgba(
        encodeLinear(decodeSrgb(parent.red) * .75 + decodeSrgb(child.red) * .25),
        encodeLinear(decodeSrgb(parent.green) * .75 + decodeSrgb(child.green) * .25),
        encodeLinear(decodeSrgb(parent.blue) * .75 + decodeSrgb(child.blue) * .25),
    )

    private fun decodeSrgb(encoded: Int): Double = (encoded / 255.0).let { value ->
        if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    }

    private fun encodeLinear(linear: Double): Int = (if (linear <= .0031308) linear * 12.92
        else 1.055 * linear.pow(1.0 / 2.4) - .055).times(255.0).roundToInt()

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte(),
    )
}
