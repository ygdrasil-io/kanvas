@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public pixel witnesses for the two frozen W6b drop-shadow terminal modes. */
class W6bDropShadowSurfacePixelTest {
    /**
     * Catches a terminal that composites the source for SHADOW_ONLY, clips the offset halo to
     * the original source, or colors the blurred alpha after recreating a shadow plan.
     */
    @Test
    fun shadowOnlyOmitsSourceAndExpandsBounds() {
        val color = ColorARGB.of(255, 17, 61, 211)
        val expected = W6bDropShadowCpuOracle.render(
            widthI32 = 9,
            heightI32 = 7,
            sourceAlpha = UByteArray(9 * 7).also { it[1 + 3 * 9] = 255u },
            dxI32 = 4,
            dyI32 = 0,
            sigmaF32 = 1f,
            color = color,
            mode = DropShadowMode.SHADOW_ONLY,
        )
        val actual = renderImpulse(DropShadowMode.SHADOW_ONLY, color, sigmaF32 = 1f)

        W6bDropShadowCpuOracle.assertNear(expected, actual)
        assertEquals(0u, actual[(1 + 3 * 9) * 4 + 3])
    }

    /**
     * Catches a Picture aggregate that loses its W6a child ordering, applies COMPOSITE without
     * the original source, or changes the frozen mode while replaying the public wire bytes.
     */
    @Test
    fun compositePictureKeepsSourceAfterNestedLayerAndWireReplay() {
        val color = ColorARGB.of(255, 17, 61, 211)
        val expected = W6bDropShadowCpuOracle.render(
            widthI32 = 9,
            heightI32 = 7,
            sourceAlpha = UByteArray(9 * 7).also { it[1 + 3 * 9] = 255u },
            dxI32 = 4,
            dyI32 = 0,
            sigmaF32 = 0f,
            color = color,
            mode = DropShadowMode.COMPOSITE,
        )
        val picture = nestedSourcePicture()
        val decoded = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        val filter = ImageFilter.DropShadow(4f, 0f, 0f, 0f, color, mode = DropShadowMode.COMPOSITE)

        listOf(picture, decoded).forEach { replay ->
            val actual = Surface(9, 7).also { surface ->
                surface.canvas { drawPicture(replay, Paint(imageFilter = filter, antiAlias = false)) }
            }.render().pixels
            W6bDropShadowCpuOracle.assertNear(expected, actual, toleranceI32 = 3)
            assertEquals(255u, actual[(1 + 3 * 9) * 4 + 3])
        }
    }

    private fun renderImpulse(mode: DropShadowMode, color: ColorARGB, sigmaF32: Float): UByteArray =
        Surface(9, 7).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(1f, 3f, 2f, 4f), Paint(
                    ColorARGB.White,
                    imageFilter = ImageFilter.DropShadow(4f, 0f, sigmaF32, sigmaF32, color, mode = mode),
                    antiAlias = false,
                ))
            }
        }.render().pixels

    private fun nestedSourcePicture(): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 9f, 7f)).apply {
            saveLayer(SaveLayerRec())
            drawRect(RectF32.ofLTRB(1f, 3f, 2f, 4f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }
    }.finishRecordingAsPicture()
}

/** Deliberately test-local CPU oracle; it has no renderer, plan, or Surface dependency. */
private object W6bDropShadowCpuOracle {
    fun render(
        widthI32: Int,
        heightI32: Int,
        sourceAlpha: UByteArray,
        dxI32: Int,
        dyI32: Int,
        sigmaF32: Float,
        color: ColorARGB,
        mode: DropShadowMode,
    ): UByteArray {
        // ImageFilter blur owns an expanded transparent domain.  Blurring only the root Surface
        // would silently clip the left/top halo before the frozen shadow offset is applied.
        val radiusI32 = ceil(3f * sigmaF32).toInt()
        val paddedWidthI32 = widthI32 + 2 * radiusI32
        val paddedHeightI32 = heightI32 + 2 * radiusI32
        val paddedAlpha = UByteArray(paddedWidthI32 * paddedHeightI32).also { padded ->
            sourceAlpha.indices.forEach { sourcePixelI32 ->
                val xI32 = sourcePixelI32 % widthI32
                val yI32 = sourcePixelI32 / widthI32
                padded[xI32 + radiusI32 + (yI32 + radiusI32) * paddedWidthI32] = sourceAlpha[sourcePixelI32]
            }
        }
        val blurredAlpha = if (sigmaF32 == 0f) paddedAlpha else W6bImageBlurCpuOracle.blurredAlpha(
            paddedWidthI32,
            paddedHeightI32,
            paddedAlpha,
            sigmaF32,
            sigmaF32,
            org.graphiks.kanvas.paint.TileMode.DECAL,
        )
        return UByteArray(widthI32 * heightI32 * 4).also { pixels ->
            blurredAlpha.indices.forEach { pixelI32 ->
                val xI32 = pixelI32 % paddedWidthI32 - radiusI32
                val yI32 = pixelI32 / paddedWidthI32 - radiusI32
                val shadowXI32 = xI32 + dxI32
                val shadowYI32 = yI32 + dyI32
                if (shadowXI32 in 0 until widthI32 && shadowYI32 in 0 until heightI32) {
                    val alpha = blurredAlpha[pixelI32].toInt()
                    writePremul(pixels, shadowXI32 + shadowYI32 * widthI32, color, alpha)
                }
            }
            if (mode == DropShadowMode.COMPOSITE) sourceAlpha.indices.forEach { pixelI32 ->
                if (sourceAlpha[pixelI32] != 0.toUByte()) writePremul(pixels, pixelI32, ColorARGB.White, sourceAlpha[pixelI32].toInt())
            }
        }
    }

    fun assertNear(expected: UByteArray, actual: UByteArray, toleranceI32: Int = 12) {
        W6bImageBlurCpuOracle.assertNear(expected, actual, toleranceI32)
    }

    private fun writePremul(pixels: UByteArray, pixelI32: Int, color: ColorARGB, alphaI32: Int) {
        val offsetI32 = pixelI32 * 4
        fun encoded(channelI32: Int): UByte {
            val srgb = channelI32 / 255f
            val linear = if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
            val premul = linear * alphaI32 / 255f
            val stored = if (premul <= 0.0031308f) premul * 12.92f else
                1.055f * premul.pow(1f / 2.4f) - 0.055f
            return (stored * 255f).roundToInt().toUByte()
        }
        pixels[offsetI32] = encoded(color.red)
        pixels[offsetI32 + 1] = encoded(color.green)
        pixels[offsetI32 + 2] = encoded(color.blue)
        pixels[offsetI32 + 3] = alphaI32.toUByte()
    }
}
