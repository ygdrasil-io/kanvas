@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public save/restore order witnesses for W6d backdrop and filtered previous layers. */
class W6dBackdropPreviousSurfacePixelTest {
    @Test
    fun `backdrop and filtered previous see parent and child at their specified times`() {
        // A save-time backdrop is green before the blue child exists. The registered layer
        // filter then halves the completed [blue, green] layer before its one restore over red.
        // The green half-opacity sample is quantized in its RGBA8 filter target before its
        // final linear restore.  These are the independent, fixed attachment bytes.
        val expected = halfSourceOver(red, blue) + rgba(177, 136, 84)
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 2f, red)
            saveLayer(SaveLayerRec(backdrop = solidGreenFilter(), paint = Paint(imageFilter = imageOpacity(.5f), antiAlias = false)))
            drawOpaque(0f, 1f, blue)
            restore()
        }

        assertPublicPixels(expected, surface)
    }

    @Test
    fun filteredPreviousCopiesUnfilteredParentAtSaveThenFiltersAfterChild() {
        // Moving image-opacity to save would leave the opaque blue child unfiltered. The first
        // texel must instead be the independently computed half-blue-over-red result.
        val expected = halfSourceOver(red, blue) + rgba(239, 51, 73)
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 2f, red)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(imageFilter = imageOpacity(.5f), antiAlias = false)))
            drawOpaque(0f, 1f, blue)
            restore()
        }

        assertPublicPixels(expected, surface)
    }

    @Test
    fun nestedBackdropReadsImmediateParentNotRoot() {
        // The inner snapshot sees its enclosing blue layer. Sampling the red root (or treating
        // the backdrop as clear) would leave the second texel red rather than half-blue over red.
        val expected = rgba(43, 181, 93) + halfSourceOver(red, blue)
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 2f, red)
            saveLayer()
            drawOpaque(0f, 2f, blue)
            saveLayer(SaveLayerRec(backdrop = imageOpacity(.5f), paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
            drawOpaque(0f, 1f, green)
            restore()
            restore()
        }

        assertPublicPixels(expected, surface)
    }

    @Test
    fun backdropAndPreviousAreExclusiveBackdropWins() {
        // Backdrop's half-red source must win over the unfiltered previous copy. SRC preserves
        // alpha, so using the previous copy (or composing both initializers) would be opaque red.
        val expected = halfOpacity(red)
        val surface = Surface(1, 1)
        surface.canvas {
            drawOpaque(0f, 1f, red)
            saveLayer(SaveLayerRec(
                backdrop = imageOpacity(.5f),
                initWithPrevious = true,
                paint = Paint(blendMode = BlendMode.SRC, antiAlias = false),
            ))
            restore()
        }

        assertPublicPixels(expected, surface)
    }

    @Test
    fun filteredPreviousIgnoresRestrictiveHintWhenRestoreChangesTransparentBlack() {
        // The restore color filter makes transparent black opaque green. A bounds hint is only a
        // filter region, never an implicit clip, so every parent texel must receive the restore.
        val expected = rgba(0, 255, 0) + rgba(0, 255, 0) + rgba(0, 255, 0) + rgba(0, 255, 0)
        val surface = Surface(4, 1)
        surface.canvas {
            drawOpaque(0f, 4f, red)
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 0f, 3f, 1f),
                initWithPrevious = true,
                paint = Paint(
                    imageFilter = imageOpacity(.5f),
                    colorFilter = opaqueGreenFromAnyInput(),
                    blendMode = BlendMode.SRC,
                    antiAlias = false,
                ),
            ))
            restore()
        }

        assertPublicPixels(expected, surface)
    }

    @Test
    fun `backdrop blur reads the immediate parent outside its restrictive output clip`() {
        // The clip admits only x=1 on restore, but DECAL blur there needs the opaque parent
        // impulse at x=0. The CPU oracle owns the Gaussian taps before Surface is constructed.
        val expected = restrictiveClipBlurExpected()
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 1f, ColorARGB.White)
            clipRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), antiAlias = false)
            saveLayer(SaveLayerRec(backdrop = ImageFilter.Blur(1f, 1f, TileMode.DECAL), paint = Paint(
                blendMode = BlendMode.SRC,
                antiAlias = false,
            )))
            restore()
        }

        assertPublicBlurPixels(expected, surface)
    }

    @Test
    fun `filtered previous blur reads the immediate parent outside its restrictive output clip`() {
        // This is the same public spatial-filter witness through initWithPrevious. It proves
        // that the pre-child copy uses filter input demand rather than the output clip alone.
        val expected = restrictiveClipBlurExpected()
        val surface = Surface(2, 1)
        surface.canvas {
            drawOpaque(0f, 1f, ColorARGB.White)
            clipRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), antiAlias = false)
            saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(
                imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL),
                blendMode = BlendMode.SRC,
                antiAlias = false,
            )))
            restore()
        }

        assertPublicBlurPixels(expected, surface)
    }

    private fun assertPublicPixels(expected: UByteArray, surface: Surface) {
        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    private fun assertPublicBlurPixels(expected: UByteArray, surface: Surface) {
        val result = surface.render()

        W6bImageBlurCpuOracle.assertNear(expected, result.pixels, tolerance = 3)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawOpaque(left: Float, right: Float, color: ColorARGB) {
        drawRect(RectF32.ofLTRB(left, 0f, right, 1f), Paint(color, antiAlias = false))
    }

    private fun imageOpacity(alpha: Float): ImageFilter.RuntimeEffect = ImageFilter.RuntimeEffect(
        requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
        UniformBlock { float1("alpha", alpha) },
    )

    private fun solidGreenFilter(): ImageFilter = ImageFilter.ColorFilter(ColorFilter.Blend(green, BlendMode.SRC))

    private fun opaqueGreenFromAnyInput(): ColorFilter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 1f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 1f,
    )))

    /** Independent source/blur/clip oracle; x=0 is the untouched parent after clipped restore. */
    private fun restrictiveClipBlurExpected(): UByteArray {
        val blurred = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(W6bImageBlurCpuOracle.blurredAlpha(
            width = 2,
            height = 1,
            sourceAlpha = ubyteArrayOf(255u, 0u),
            sigmaX = 1f,
            sigmaY = 1f,
            tileMode = TileMode.DECAL,
        ))
        blurred[0] = 255u
        blurred[1] = 255u
        blurred[2] = 255u
        blurred[3] = 255u
        return blurred
    }

    /** Independent linear-light CPU oracle for one .5 opacity source restored over opaque red. */
    private fun halfSourceOver(destination: ColorARGB, source: ColorARGB): UByteArray = rgba(
        encodeLinear((decodeSrgb(source.red) + decodeSrgb(destination.red)) * .5),
        encodeLinear((decodeSrgb(source.green) + decodeSrgb(destination.green)) * .5),
        encodeLinear((decodeSrgb(source.blue) + decodeSrgb(destination.blue)) * .5),
    )

    /** Independent RGBA8 attachment oracle for `SRC` after a .5 IMAGE_FILTER opacity pass. */
    private fun halfOpacity(color: ColorARGB): UByteArray = rgba(
        encodePremultipliedHalf(color.red),
        encodePremultipliedHalf(color.green),
        encodePremultipliedHalf(color.blue),
        128,
    )

    private fun decodeSrgb(encoded: Int): Double = (encoded / 255.0).let { value ->
        if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    }

    private fun encodeLinear(linear: Double): Int = (if (linear <= .0031308) linear * 12.92
        else 1.055 * linear.pow(1.0 / 2.4) - .055).times(255.0).roundToInt()

    private fun encodePremultipliedHalf(encoded: Int): Int = encodeLinear(decodeSrgb(encoded) * .5)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte(),
    )

    private companion object {
        val red: ColorARGB = ColorARGB.of(255, 239, 51, 73)
        val blue: ColorARGB = ColorARGB.of(255, 17, 61, 211)
        val green: ColorARGB = ColorARGB.of(255, 43, 181, 93)
    }
}
