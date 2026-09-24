@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

class W6dPictureFilterSurfacePixelTest {
    @Test
    fun filterPictureIgnoresCarrierPixelsAndSamplesOnlySealedScene() {
        val expected = ubyteArrayOf(255u, 0u, 0u, 255u)
        val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val recorder = PictureRecorder()
        recorder.beginRecording(rect).drawRect(rect, Paint(ColorARGB.Red, antiAlias = false))
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(rect, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(picture), antiAlias = false))
        }
        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Test
    fun filterPictureCarriesCarrierTranslationIntoItsSealedPictureSource() {
        val expected = ubyteArrayOf(
            0u, 0u, 0u, 0u,
            255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u,
            0u, 0u, 0u, 0u,
        )
        val pictureRect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val recorder = PictureRecorder()
        recorder.beginRecording(pictureRect).drawRect(pictureRect, Paint(ColorARGB.Red, antiAlias = false))
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(4, 1)
        surface.canvas {
            translate(1f, 0f)
            scale(2f, 1f)
            drawRect(pictureRect, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(picture), antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /** One shared Picture-filter object remains two execution occurrences, one per carrier. */
    @Test
    fun sharedPictureFilterObjectKeepsEachNestedFilteredChild() {
        val expected = ubyteArrayOf(
            0u, 0u, 255u, 255u,
            0u, 0u, 255u, 255u,
        )
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(
                ColorARGB.Red,
                imageFilter = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC)),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val shared = ImageFilter.Picture(source)
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(unit, Paint(ColorARGB.Green, imageFilter = shared, antiAlias = false))
            save()
            translate(1f, 0f)
            drawRect(unit, Paint(ColorARGB.Green, imageFilter = shared, antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /**
     * A source-only Picture aggregate has to preserve the captured sibling order: red Clear,
     * green draw, then the opaque DST_OUT draw. Removing the middle texel after it was painted
     * must not erase the red texel or fall back to the blue carrier source.
     */
    @Test
    fun pictureFilterNestedSceneKeepsSiblingAndDstOutOrder() {
        val expected = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 0u, 0u, 0u,
        )
        val pictureBounds = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val secondTexel = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(pictureBounds).apply {
                clear(ColorARGB.Red)
                drawRect(secondTexel, Paint(ColorARGB.Green, antiAlias = false))
                drawRect(secondTexel, Paint(ColorARGB.White, blendMode = BlendMode.DST_OUT, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(pictureBounds, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(picture), antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /**
     * The literal is the sRGB encoding of the linear-premultiplied source-over result of blue,
     * half red, then half green.
     * Reordering either the DrawColor sibling or the saveLayer child changes at least one color
     * channel, so this is a public record-order check rather than a graph-shape check.
     */
    @Test
    fun pictureFilterNestedPictureLayerAndDrawColorKeepOrder() {
        val expected = ubyteArrayOf(137u, 188u, 136u, 255u)
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false))
        }.finishRecordingAsPicture()
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawPicture(child)
                drawColor(ColorARGB.of(128, 255, 0, 0))
                saveLayer(SaveLayerRec(paint = Paint(ColorARGB.of(128, 255, 255, 255), antiAlias = false)))
                drawRect(bounds, Paint(ColorARGB.Green, antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /** DrawColor is transform-invariant but must retain the clip captured with its source command. */
    @Test
    fun pictureFilterDrawColorRespectsRecordedClip() {
        val expected = ubyteArrayOf(
            0u, 0u, 255u, 255u,
            255u, 0u, 0u, 255u,
        )
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val secondTexel = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clear(ColorARGB.Blue)
                clipRect(secondTexel, ClipOp.INTERSECT, antiAlias = false)
                drawColor(ColorARGB.Red, BlendMode.SRC)
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /** A fractional hard clip may render exactly or refuse before publication, but never widen. */
    @Test
    fun pictureFilterFractionalDrawColorClipNeverPaintsBoundingBox() {
        val exactExpected = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 0u, 255u, 255u,
        )
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val fractional = RectF32.ofLTRB(0.25f, 0f, 1.25f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clear(ColorARGB.Blue)
                clipRect(fractional, ClipOp.INTERSECT, antiAlias = false)
                drawColor(ColorARGB.Red, BlendMode.SRC)
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }

        val rendered = runCatching { surface.render() }
        val result = rendered.getOrNull()
        if (result != null) {
            assertContentEquals(exactExpected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        } else {
            val failure = requireNotNull(rendered.exceptionOrNull())
            assertTrue(failure is IllegalStateException)
            assertTrue(failure.message?.startsWith("w6a.layer.unsupported_child:") == true, failure.message ?: "missing diagnostic")
        }
    }

    /** A non-pixel DeviceRect clip is rejected before it can widen an SRC DrawColor write. */
    @Test
    fun pictureFilterFractionalDrawColorClipRefusesWithoutReadbackMutation() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val fractional = RectF32.ofLTRB(0.25f, 0f, 1.25f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clear(ColorARGB.Blue)
                clipRect(fractional, ClipOp.INTERSECT, antiAlias = false)
                drawColor(ColorARGB.Red, BlendMode.SRC)
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }
        val sentinel = UByteArray(8) { 0x5au }
        val before = sentinel.copyOf()

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }

        assertTrue(failure.message?.startsWith(
            "w6a.layer.unsupported_child: Picture DrawColor clip has no exact pixel-aligned scissor representation.",
        ) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Green, antiAlias = false)) }
        val recovered = surface.render()
        assertContentEquals(ubyteArrayOf(0u, 255u, 0u, 255u, 0u, 255u, 0u, 255u), recovered.pixels)
        assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /** A rotated recorded clip has no scissor-equivalent frozen DrawColor representation. */
    @Test
    fun pictureFilterRotatedDrawColorClipRefusesWithoutReadbackMutation() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clear(ColorARGB.Blue)
                save()
                rotate(30f, 1f, 1f)
                clipRect(RectF32.ofLTRB(0.5f, 0f, 1.5f, 2f), ClipOp.INTERSECT, antiAlias = false)
                drawColor(ColorARGB.Red, BlendMode.SRC)
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }

        assertTrue(failure.message?.startsWith(
            "w6a.layer.unsupported_child: Picture DrawColor clip has no exact pixel-aligned scissor representation.",
        ) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    /** A fully excluded DrawColor is a clip no-op even when its un-clipped mode is SRC. */
    @Test
    fun pictureFilterFullyClippedDrawColorSrcKeepsPriorPixels() {
        val expected = ubyteArrayOf(
            0u, 0u, 255u, 255u,
            0u, 0u, 255u, 255u,
        )
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val outside = RectF32.ofLTRB(2f, 0f, 3f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clear(ColorARGB.Blue)
                clipRect(outside, ClipOp.INTERSECT, antiAlias = false)
                drawColor(ColorARGB.Red, BlendMode.SRC)
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Black, imageFilter = ImageFilter.Picture(source), antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /**
     * The inner ColorFilter is deliberately materialized before the outer Picture leaf. Its
     * sealed source then contains a filtered blue child, so a flat/non-reentrant schedule either
     * refuses the capture or exposes the carrier instead of the independently fixed blue pixel.
     */
    @Test
    fun pictureFilterReentrantPassOrderKeepsEarlierAndInnerFilters() {
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(
                ColorARGB.Red,
                imageFilter = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC)),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val filter = ImageFilter.Compose(
            ImageFilter.Picture(source),
            ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Green, BlendMode.SRC)),
        )
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Red, imageFilter = filter, antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Test
    fun pictureFilterSrcIsCropWithoutRescaleAndEmptyRemainsTransparent() {
        val croppedExpected = ubyteArrayOf(
            0u, 0u, 0u, 0u,
            0u, 255u, 0u, 255u,
            0u, 0u, 0u, 0u,
        )
        val emptyExpected = UByteArray(3 * 4)
        val bounds = RectF32.ofLTRB(0f, 0f, 3f, 1f)
        val src = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val emptySrc = RectF32.ofLTRB(3f, 0f, 4f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
                drawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val croppedSurface = Surface(3, 1)
        val emptySurface = Surface(3, 1)
        croppedSurface.canvas {
            drawRect(bounds, Paint(ColorARGB.White, imageFilter = ImageFilter.Picture(source, src), antiAlias = false))
        }
        emptySurface.canvas {
            drawRect(bounds, Paint(ColorARGB.White, imageFilter = ImageFilter.Picture(source, emptySrc), antiAlias = false))
        }

        val cropped = croppedSurface.render()
        val empty = emptySurface.render()

        assertContentEquals(croppedExpected, cropped.pixels)
        assertContentEquals(emptyExpected, empty.pixels)
        assertTrue(cropped.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        assertTrue(empty.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /** A transparent Picture input is still observable through an outer ColorFilter and SRC. */
    @Test
    fun emptyPictureStillRunsComposeColorFilterAndSrcComposite() {
        val expected = ubyteArrayOf(0u, 0u, 0u, 128u)
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val empty = PictureRecorder().also { recorder -> recorder.beginRecording(bounds) }.finishRecordingAsPicture()
        val filter = ImageFilter.Compose(
            ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.of(128, 0, 0, 0), BlendMode.SRC)),
            ImageFilter.Picture(empty),
        )
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Green, antiAlias = false))
            drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, blendMode = BlendMode.SRC, antiAlias = false))
        }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    /**
     * The crop is transformed once through T(1) then S(2), placing the impulse at x=3. The
     * independently evaluated DECAL blur has a visible x=4 halo, which the parent clip retains
     * although x=4 lies outside the Picture cull itself.
     */
    @Test
    fun transformedPictureCropKeepsBlurHaloOutsideCull() {
        val expected = transformedCropBlurHaloExpected()
        val pictureBounds = RectF32.ofLTRB(0f, 0f, 2f, 3f)
        val sourceCrop = RectF32.ofLTRB(0f, 0f, 2f, 3f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(pictureBounds).drawRect(
                RectF32.ofLTRB(1f, 1f, 1.5f, 2f), Paint(ColorARGB.White, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val surface = Surface(7, 3)
        surface.canvas {
            clipRect(RectF32.ofLTRB(4f, 1f, 5f, 2f), ClipOp.INTERSECT, antiAlias = false)
            translate(1f, 0f)
            scale(2f, 1f)
            drawRect(pictureBounds, Paint(
                ColorARGB.Blue,
                imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL, ImageFilter.Picture(source, sourceCrop)),
                antiAlias = false,
            ))
        }

        val result = surface.render()

        W6bImageBlurCpuOracle.assertNear(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    private fun transformedCropBlurHaloExpected(): UByteArray {
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
}
