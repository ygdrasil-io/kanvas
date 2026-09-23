@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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
     * The outer translation makes the sealed source origin negative once its transparent
     * support is accounted for.  The half-texel offset must distribute the sample linearly;
     * nearest/floor sampling collapses it onto one neighbour.
     */
    @Test
    fun shadowOnlyFractionalOffsetIsLinearAtTranslatedTransparentEdge() {
        val color = ColorARGB.of(160, 17, 61, 211)
        val expected = W6bDropShadowCpuOracle.render(
            widthI32 = 9,
            heightI32 = 7,
            sourceAlpha = UByteArray(9 * 7).also { it[1 + 3 * 9] = 255u },
            sourceColor = ColorARGB.White,
            dxF32 = 1.5f,
            dyF32 = -0.5f,
            sigmaF32 = 1f,
            color = color,
            mode = DropShadowMode.SHADOW_ONLY,
        )
        val actual = Surface(9, 7).also { surface ->
            surface.canvas {
                save()
                translate(-2f, 0f)
                drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(
                    ColorARGB.White,
                    imageFilter = ImageFilter.DropShadow(1.5f, -0.5f, 1f, 1f, color,
                        mode = DropShadowMode.SHADOW_ONLY),
                    antiAlias = false,
                ))
                restore()
            }
        }.render().pixels

        W6bDropShadowCpuOracle.assertNear(expected, actual, toleranceI32 = 3)
        assertNotEquals(0u, actual[(2 + 2 * 9) * 4 + 3])
    }

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
            sourceColor = ColorARGB.White,
            dxF32 = 4f,
            dyF32 = 0f,
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
            sourceColor = ColorARGB.White,
            dxF32 = 4f,
            dyF32 = 0f,
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

    /** A sealed Picture aggregate keeps its transparent blur halo and nonzero shadow offset. */
    @Test
    fun shadowOnlyPictureAggregateKeepsBlurAndOffsetThroughMemoryAndWireReplay() {
        val color = ColorARGB.of(160, 17, 61, 211)
        val expected = W6bDropShadowCpuOracle.render(
            widthI32 = 9,
            heightI32 = 7,
            sourceAlpha = UByteArray(9 * 7).also { it[1 + 3 * 9] = 255u },
            sourceColor = ColorARGB.White,
            dxF32 = 2f,
            dyF32 = 0f,
            sigmaF32 = 1f,
            color = color,
            mode = DropShadowMode.SHADOW_ONLY,
        )
        val picture = nestedSourcePicture()
        val decoded = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        listOf(picture, decoded).forEach { replay ->
            val actual = Surface(9, 7).also { surface ->
                surface.canvas { drawPicture(replay, Paint(imageFilter = ImageFilter.DropShadow(2f, 0f, 1f, 1f,
                    color, mode = DropShadowMode.SHADOW_ONLY), antiAlias = false)) }
            }.render().pixels
            W6bDropShadowCpuOracle.assertNear(expected, actual, toleranceI32 = 3)
            // The Gaussian tail may reach the old source pixel, but SHADOW_ONLY cannot restore
            // the opaque white source there (COMPOSITE would make this alpha 255).
            assertTrue(actual[(1 + 3 * 9) * 4 + 3] < 64u)
        }
    }

    /** A translucent original must be the source of the internal SrcOver, not a replacement. */
    @Test
    fun compositeUsesSrcOverForTranslucentOriginalAndShadowAlpha() {
        val source = ColorARGB.of(128, 255, 255, 255)
        val shadow = ColorARGB.of(96, 17, 61, 211)
        val expected = W6bDropShadowCpuOracle.render(
            widthI32 = 7,
            heightI32 = 5,
            sourceAlpha = UByteArray(7 * 5).also { it[3 + 2 * 7] = 255u },
            sourceColor = source,
            dxF32 = 0f,
            dyF32 = 0f,
            sigmaF32 = 0f,
            color = shadow,
            mode = DropShadowMode.COMPOSITE,
        )
        val actual = Surface(7, 5).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(3f, 2f, 4f, 3f), Paint(source,
                    imageFilter = ImageFilter.DropShadow(0f, 0f, 0f, 0f, shadow,
                        mode = DropShadowMode.COMPOSITE), antiAlias = false))
            }
        }.render().pixels

        W6bDropShadowCpuOracle.assertNear(expected, actual, toleranceI32 = 3)
        assertNotEquals(0u, actual[(3 + 2 * 7) * 4 + 3])
    }

    /**
     * Both memory and wire Picture replays keep the filtered layer between two siblings.  The
     * trailing sibling overlaps the translated shadow, so source-order publication is visible.
     */
    @Test
    fun bothModesReplayMemoryAndWireWithTranslatedAggregateAndOrderedSiblings() {
        val picture = nestedSourcePicture()
        val decoded = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        DropShadowMode.entries.forEach { mode ->
            val replays = listOf(picture, decoded).map { replay ->
                Surface(9, 7).also { surface ->
                    surface.canvas {
                        drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
                        saveLayer(SaveLayerRec())
                        save()
                        translate(-2f, 0f)
                        drawPicture(replay, Paint(imageFilter = ImageFilter.DropShadow(2f, 0f, 1f, 1f,
                            ColorARGB.of(160, 17, 61, 211), mode = mode), antiAlias = false))
                        restore()
                        restore()
                        drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(ColorARGB.Red, antiAlias = false))
                    }
                }.render().pixels
            }
            assertContentEquals(replays.first(), replays.last())
            assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), replays.first().copyOfRange(0, 4))
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u),
                replays.first().copyOfRange((3 + 3 * 9) * 4, (3 + 3 * 9) * 4 + 4))
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
        sourceColor: ColorARGB,
        dxF32: Float,
        dyF32: Float,
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
                padded[xI32 + radiusI32 + (yI32 + radiusI32) * paddedWidthI32] =
                    ((sourceAlpha[sourcePixelI32].toInt() * sourceColor.alpha + 127) / 255).toUByte()
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
            for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
                val alphaF32 = bilinearDecal(blurredAlpha, paddedWidthI32, paddedHeightI32,
                    xI32 - dxF32 + radiusI32, yI32 - dyF32 + radiusI32)
                if (alphaF32 > 0f) writeSrcOver(pixels, xI32 + yI32 * widthI32, color, alphaF32)
            }
            if (mode == DropShadowMode.COMPOSITE) sourceAlpha.indices.forEach { pixelI32 ->
                if (sourceAlpha[pixelI32] != 0.toUByte()) {
                    writeSrcOver(pixels, pixelI32, sourceColor, sourceAlpha[pixelI32].toInt() / 255f)
                }
            }
        }
    }

    fun assertNear(expected: UByteArray, actual: UByteArray, toleranceI32: Int = 12) {
        W6bImageBlurCpuOracle.assertNear(expected, actual, toleranceI32)
    }

    private fun bilinearDecal(alpha: UByteArray, widthI32: Int, heightI32: Int, xF32: Float, yF32: Float): Float {
        val leftI32 = floor(xF32).toInt()
        val topI32 = floor(yF32).toInt()
        val fractionX = xF32 - leftI32
        val fractionY = yF32 - topI32
        fun sample(xI32: Int, yI32: Int): Float = if (xI32 in 0 until widthI32 && yI32 in 0 until heightI32)
            alpha[xI32 + yI32 * widthI32].toInt() / 255f else 0f
        val top = sample(leftI32, topI32) * (1f - fractionX) + sample(leftI32 + 1, topI32) * fractionX
        val bottom = sample(leftI32, topI32 + 1) * (1f - fractionX) + sample(leftI32 + 1, topI32 + 1) * fractionX
        return top * (1f - fractionY) + bottom * fractionY
    }

    private fun writeSrcOver(pixels: UByteArray, pixelI32: Int, color: ColorARGB, coverageF32: Float) {
        val offsetI32 = pixelI32 * 4
        fun linear(channelI32: Int): Float {
            val srgb = channelI32 / 255f
            return if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
        }
        fun encoded(valueF32: Float): UByte {
            val stored = if (valueF32 <= 0.0031308f) valueF32 * 12.92f else 1.055f * valueF32.pow(1f / 2.4f) - 0.055f
            return (stored.coerceIn(0f, 1f) * 255f).roundToInt().toUByte()
        }
        fun destination(channelI32: Int): Float {
            val encoded = pixels[offsetI32 + channelI32].toInt() / 255f
            return if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
        }
        val alpha = color.alpha / 255f * coverageF32
        val inverse = 1f - alpha
        pixels[offsetI32] = encoded(linear(color.red) * alpha + destination(0) * inverse)
        pixels[offsetI32 + 1] = encoded(linear(color.green) * alpha + destination(1) * inverse)
        pixels[offsetI32 + 2] = encoded(linear(color.blue) * alpha + destination(2) * inverse)
        val destinationAlpha = pixels[offsetI32 + 3].toInt() / 255f
        pixels[offsetI32 + 3] = ((alpha + destinationAlpha * inverse).coerceIn(0f, 1f) * 255f).roundToInt().toUByte()
    }
}
