@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public Surface pixel contract for native W6b image-only blur materialization. */
class W6bImageBlurSurfacePixelTest {
    @Test
    fun `isolated Picture impulse is blurred by the frozen X then Y passes`() {
        val expected = impulseExpected(TileMode.DECAL)

        val surface = blurredImpulseSurface(TileMode.DECAL)

        W6bImageBlurCpuOracle.assertNear(expected, surface.render().pixels)
    }

    @Test
    fun `image blur honors all four frozen tile modes`() {
        val pixelsByTileMode = TileMode.entries.associateWith { tileMode ->
            val expected = edgeImpulseExpected(tileMode)

            val surface = blurredEdgeImpulseSurface(tileMode)

            surface.render().pixels.also { actual -> W6bImageBlurCpuOracle.assertNear(expected, actual) }
        }
        assertTrue(pixelsByTileMode.getValue(TileMode.CLAMP)[3] > pixelsByTileMode.getValue(TileMode.DECAL)[3])
    }

    @Test
    fun `deferred Picture clip preserves blur demand outside cull under noncommuting transform`() {
        // This is intentionally computed before Surface: the local half-pixel rect maps to one
        // device pixel at x=3 under T(1) then S(2); the parent clip admits only its x=4 halo.
        val expected = deferredClipCullTransformExpected()

        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 3f)).drawRect(
                RectF32.ofLTRB(1f, 1f, 1.5f, 2f), Paint(ColorARGB.White, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val surface = Surface(7, 3).also { target ->
            target.canvas {
                clipRect(RectF32.ofLTRB(4f, 1f, 5f, 2f), ClipOp.INTERSECT, antiAlias = false)
                translate(1f, 0f)
                scale(2f, 1f)
                drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL)))
            }
        }

        W6bImageBlurCpuOracle.assertNear(expected, surface.render().pixels)
    }

    @Test
    fun `Picture parent alpha color filter and blend apply exactly once`() {
        // An opaque green destination makes SRC observable; the R/B swap is an involution, so
        // applying the frozen color filter twice would return red rather than the expected blue.
        val expected = parentPaintAppliedOnceExpected()

        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 3f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(ColorARGB.Red, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val swapRedBlue = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        val surface = Surface(3, 3).also { target ->
            target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(ColorARGB.Green, antiAlias = false))
                drawPicture(picture, Paint(
                    color = ColorARGB.of(128, 255, 255, 255),
                    colorFilter = swapRedBlue,
                    imageFilter = ImageFilter.Blur(1f, 1f, TileMode.CLAMP),
                    blendMode = BlendMode.SRC,
                    antiAlias = false,
                ))
            }
        }

        W6bImageBlurCpuOracle.assertNear(expected, surface.render().pixels, tolerance = 3)
    }

    @Test
    fun `nested inline DST_OUT and painted child isolation keep their frozen Picture modes`() {
        // Both arrays are fixed before either Surface is created. The inline child erases the
        // already-recorded red sibling; the painted child runs that erase against its isolated
        // transparent aggregate, so the parent red remains through the outer blur.
        val inlineExpected = UByteArray(3 * 3 * 4)
        val paintedExpected = opaqueRed3x3()
        val bounds = RectF32.ofLTRB(0f, 0f, 3f, 3f)
        val erase = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White,
                blendMode = BlendMode.DST_OUT, antiAlias = false))
        }.finishRecordingAsPicture()
        fun parent(childPaint: Paint?): org.graphiks.kanvas.picture.Picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false))
                if (childPaint == null) drawPicture(erase) else drawPicture(erase, childPaint)
            }
        }.finishRecordingAsPicture()
        fun rendered(parent: org.graphiks.kanvas.picture.Picture): UByteArray = Surface(3, 3).also { surface ->
            surface.canvas { drawPicture(parent, Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.CLAMP))) }
        }.render().pixels

        W6bImageBlurCpuOracle.assertNear(inlineExpected, rendered(parent(null)), tolerance = 3)
        W6bImageBlurCpuOracle.assertNear(paintedExpected, rendered(parent(Paint(ColorARGB.White, antiAlias = false))), tolerance = 3)
    }

    @Test
    fun `nested filtered Pictures run the child blur before the sealed parent blur`() {
        // The reference computes two blur generations on a larger transparent canvas before
        // cropping the public target, so the parent pass consumes the child halo rather than a
        // prematurely clipped 7×7 intermediate.
        val expected = nestedPictureBlurExpected()
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(
                ColorARGB.White, imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL), antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawPicture(child, Paint(
                imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL), antiAlias = false,
            ))
        }.finishRecordingAsPicture()

        val actual = Surface(7, 7).also { surface -> surface.canvas { drawPicture(parent) } }.render().pixels

        W6bImageBlurCpuOracle.assertNear(expected, actual)
    }

    private fun blurredImpulseSurface(tileMode: TileMode): Surface {
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 7f, 7f)).drawRect(
                RectF32.ofLTRB(3f, 3f, 4f, 4f),
                Paint(ColorARGB.White, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        return Surface(7, 7).also { surface ->
            surface.canvas { drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f, tileMode))) }
        }
    }

    private fun blurredEdgeImpulseSurface(tileMode: TileMode): Surface {
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 7f, 7f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.White, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        return Surface(7, 7).also { surface ->
            surface.canvas { drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f, tileMode))) }
        }
    }

    private fun impulseExpected(tileMode: TileMode): UByteArray = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(
        W6bImageBlurCpuOracle.blurredAlpha(7, 7, UByteArray(49).also { it[3 + 3 * 7] = 255u }, 1f, 1f, tileMode),
    )

    private fun edgeImpulseExpected(tileMode: TileMode): UByteArray = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(
        W6bImageBlurCpuOracle.blurredAlpha(7, 7, UByteArray(49).also { it[0] = 255u }, 1f, 1f, tileMode,
            knownRight = 1, knownBottom = 1),
    )

    private fun deferredClipCullTransformExpected(): UByteArray {
        val blurred = W6bImageBlurCpuOracle.blurredAlpha(
            width = 7,
            height = 3,
            sourceAlpha = UByteArray(21).also { it[3 + 7] = 255u },
            sigmaX = 1f,
            sigmaY = 1f,
            tileMode = TileMode.DECAL,
            knownLeft = 1,
            knownTop = 0,
            knownRight = 5,
            knownBottom = 3,
        )
        return W6bImageBlurCpuOracle.toOpaqueWhiteRgba(blurred.also { alpha ->
            alpha.indices.filter { pixel -> pixel != 4 + 7 }.forEach { pixel -> alpha[pixel] = 0u }
        })
    }

    private fun parentPaintAppliedOnceExpected(): UByteArray = UByteArray(3 * 3 * 4).also { pixels ->
        repeat(9) { pixel ->
            val offset = pixel * 4
            pixels[offset] = 0u
            pixels[offset + 1] = 0u
            // sRGB encoding of a linear premultiplied 128/255 blue component.
            pixels[offset + 2] = 188u
            pixels[offset + 3] = 128u
        }
    }

    private fun opaqueRed3x3(): UByteArray = UByteArray(3 * 3 * 4).also { pixels ->
        repeat(9) { pixel ->
            val offset = pixel * 4
            pixels[offset] = 255u
            pixels[offset + 3] = 255u
        }
    }

    private fun nestedPictureBlurExpected(): UByteArray {
        val wide = W6bImageBlurCpuOracle.blurredAlpha(19, 19, UByteArray(19 * 19).also {
            it[9 + 9 * 19] = 255u
        }, 1f, 1f, TileMode.DECAL)
        val twice = W6bImageBlurCpuOracle.blurredAlpha(19, 19, wide, 1f, 1f, TileMode.DECAL)
        val cropped = UByteArray(7 * 7) { pixel ->
            val x = pixel % 7
            val y = pixel / 7
            twice[(x + 6) + (y + 6) * 19]
        }
        return W6bImageBlurCpuOracle.toOpaqueWhiteRgba(cropped)
    }
}
