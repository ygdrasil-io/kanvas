@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class W5eImageConvergenceSurfaceTest {
    // Losing attachment premultiplication provenance makes translucent red replay
    // saturate to255 instead of remaining near188; expectations precede BOTH renders.
    @Test fun translucentSnapshotReplayPreservesSourceEncoding() {
        val expected = bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888,
            AlphaType.UNPREMUL, ColorSpace.SRGB, byteArrayOf(-1, 0, 0, -128)))
        val source = Surface(31, 1)
        source.canvas { drawRect(rect(31f), Paint(color = ColorARGB.fromRGBA(1f, 0f, 0f, .5f),
            antiAlias = false, blendMode = BlendMode.SRC)) }
        val snapshot = source.makeImageSnapshot()
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, assertNotNull(snapshot.pixels).copyOfRange(0, 4).toUByteArray())
        val replay = Surface(33, 1)
        replay.canvas { drawImage(snapshot, rect(31f), SamplingOptions.NEAREST, paint()) }
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, replay.render().pixels.copyOfRange(0, 4))
    }

    @Test fun allPromotedImageLanesArePlanOwned() {
        val image = redImage()
        val surface = Surface(6, 1)
        surface.canvas {
            drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST, paint())
            drawImageNine(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(1f, 0f, 2f, 1f), paint())
            drawImageLattice(image, Lattice(emptyList(), emptyList()), RectF32.ofLTRB(2f, 0f, 3f, 1f), paint = paint())
            drawAtlas(image, listOf(Matrix3x3F32.translation(3f, 0f)), listOf(rect()), paint = paint())
            drawRect(RectF32.ofLTRB(4f, 0f, 5f, 1f), paint().copy(shader = Shader.Image(image)))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(5f, 0f, 6f, 1f)) }, paint().copy(shader = Shader.Image(image)))
        }
        val result = surface.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        assertContentEquals(UByteArray(24) { if (it % 4 == 0 || it % 4 == 3) 255u else 0u }, result.pixels)
        assertEquals(6, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        result.assertClean()
    }

    @Test fun alignedOneToOneDrawsWithDifferentPaddingAndSamplingRenderIdentically() {
        val bytes = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1)
        for (paddingI32 in listOf(0, 7)) for (sampling in listOf(SamplingOptions.NEAREST, SamplingOptions.LINEAR)) {
            val padded = ByteArray((4 + paddingI32) * 2) { 73 }
            bytes.copyInto(padded, 0, 0, 4)
            bytes.copyInto(padded, 4 + paddingI32, 4, 8)
            val image = Image.fromPixels(1, 2, padded, alphaType = AlphaType.PREMUL, rowBytesI32 = 4 + paddingI32)
            val surface = Surface(1, 2)
            surface.canvas { drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 2f), sampling, paint()) }
            val result = surface.render()
            assertContentEquals(bytes.toUByteArray(), result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun repeatedRenderAndSubsequentSurfaceRemainStable() {
        val surface = Surface(1, 1)
        surface.canvas { drawImage(redImage(), rect(), SamplingOptions.NEAREST, paint()) }
        repeat(3) { assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels) }
        val next = Surface(1, 1)
        next.canvas { drawImage(redImage(), rect(), SamplingOptions.LINEAR, paint()) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), next.render().pixels)
    }

    @Test fun fullAndSubsetSnapshotPictureAndAtlasPreserveTranslucentPixels() {
        // Dropping provenance in subset capture, immutable Image copies, archive
        // adapters or Atlas decoding makes the same red saturation error observable.
        val expected = bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888,
            AlphaType.UNPREMUL, ColorSpace.SRGB, byteArrayOf(-1, 0, 0, -128)))
        for (format in PixelFormat.entries) for (subset in listOf(false, true)) {
            val source = Surface(3, 2, format)
            source.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f),
                Paint(color = ColorARGB.fromRGBA(1f, 0f, 0f, .5f), antiAlias = false, blendMode = BlendMode.SRC)) }
            val snapshot = if (subset) assertNotNull(source.makeImageSnapshot(RectF32.ofLTRB(1f, 1f, 2f, 2f)))
                else source.makeImageSnapshot()
            val copied = snapshot.copy().reinterpretColorSpace(ColorSpace.SRGB)
            assertContentEquals(assertNotNull(snapshot.pixels), assertNotNull(copied.pixels))
            val recorder = PictureRecorder()
            recorder.beginRecording(rect()).drawImageRect(copied, rect(), rect(), SamplingOptions.NEAREST, paint())
            val picture = recorder.finishRecordingAsPicture()
            val restored = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
            val images = mutableListOf<Image>()
            restored.walkImages(images::add)
            assertContentEquals(assertNotNull(snapshot.pixels), assertNotNull(images.single().pixels))
            val replay = Surface(1, 1)
            replay.canvas { restored.playback(this) }
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, replay.render().pixels)
            val atlas = Surface(1, 1)
            atlas.canvas { drawAtlas(images.single(), listOf(Matrix3x3F32.translation(0f, 0f)), listOf(rect()),
                listOf(ColorARGB.White), BlendMode.MODULATE, paint()) }
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, atlas.render().pixels)
        }
    }

    @Test fun zeroAlphaSnapshotRemainsTransparentThroughPicture() {
        val expected = bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888,
            AlphaType.UNPREMUL, ColorSpace.SRGB, byteArrayOf(-1, 71, 127, 0)))
        val source = Surface(1, 1)
        source.canvas { drawRect(rect(), paint().copy(color = ColorARGB.Transparent)) }
        val snapshot = source.makeImageSnapshot()
        val recorder = PictureRecorder()
        recorder.beginRecording(rect()).drawImage(snapshot, rect(), SamplingOptions.NEAREST, paint())
        val restored = assertNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
        val replay = Surface(1, 1)
        replay.canvas { restored.playback(this) }
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, replay.render().pixels)
    }

    @Test fun unpromotedFormatMixtureCannotMisinterpretAttachmentSnapshot() {
        val expected = bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888,
            AlphaType.UNPREMUL, ColorSpace.SRGB, byteArrayOf(-1, 0, 0, -128)))
        val source = Surface(1, 1)
        source.canvas { drawRect(rect(), paint().copy(color = ColorARGB.fromRGBA(1f, 0f, 0f, .5f))) }
        val snapshot = source.makeImageSnapshot()
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, assertNotNull(snapshot.pixels).toUByteArray())
        val mixed = Surface(2, 1)
        mixed.canvas {
            drawImage(snapshot, rect(), SamplingOptions.NEAREST, paint().copy(blendMode = BlendMode.SRC_OVER))
            drawImage(Image.fromPixels(1, 1, byteArrayOf(0, -8), ColorType.RGB_565),
                RectF32.ofLTRB(1f, 0f, 2f, 1f), SamplingOptions.NEAREST, paint().copy(blendMode = BlendMode.SRC_OVER))
        }
        val failure = assertThrows<IllegalStateException> { mixed.render() }
        assertEquals("unsupported.image.prepared.premultiplication", failure.message.orEmpty().substringBefore(':'))
    }

    @Test fun snapshotRepresentationParticipatesInPublicValueAndRetainedAliasSemantics() {
        val expected = bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888,
            AlphaType.UNPREMUL, ColorSpace.SRGB, byteArrayOf(-1, 0, 0, -128)))
        val source = Surface(1, 1)
        source.canvas { drawRect(rect(), paint().copy(color = ColorARGB.fromRGBA(1f, 0f, 0f, .5f))) }
        val snapshot = source.makeImageSnapshot()
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, assertNotNull(snapshot.pixels).toUByteArray())
        assertEquals(ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL, snapshot.premultiplication)
        assertEquals(snapshot, snapshot.copy().reinterpretColorSpace(ColorSpace.SRGB))
        val ordinary = snapshot.copy(premultiplication = ImagePremultiplicationV1.SOURCE_SPACE)
        assertNotEquals(snapshot, ordinary)
        val recorder = PictureRecorder(SceneCaptureLimits(maxImageBytesI64 = 4L))
        val canvas = recorder.beginRecording(rect())
        canvas.drawImage(snapshot, rect(), SamplingOptions.NEAREST, paint())
        val refusal = assertThrows<SceneRecordingLimitException> {
            canvas.drawImage(ordinary, rect(), SamplingOptions.NEAREST, paint())
        }
        assertEquals("scene-recording-image-bytes-exceeded", refusal.diagnostic.code.value)
        for (alpha in listOf(AlphaType.OPAQUE, AlphaType.UNPREMUL, AlphaType.UNKNOWN)) {
            val failure = assertThrows<IllegalArgumentException> { snapshot.copy(alphaType = alpha) }
            assertEquals("invalid.material.image.premultiplication", failure.message)
        }
        assertThrows<IllegalArgumentException> { snapshot.copy(colorType = ColorType.ALPHA_8) }
        assertThrows<IllegalArgumentException> { snapshot.copy(pixels = null) }
    }

    @Test fun captureBudgetRefusalIsTransactionalAndSameSurfaceRecovers() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxImageBytesI64 = 4L))
        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawImage(Image.fromPixels(2, 1, ByteArray(8)), rect(), SamplingOptions.NEAREST, paint()) }
        }
        assertEquals("scene-recording-image-bytes-exceeded", failure.diagnostic.code.value)
        surface.canvas { drawImage(redImage(), rect(), SamplingOptions.NEAREST, paint()) }
        val result = surface.render()
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    @Test fun frameBudgetRefusalIsTerminalAndNextSurfaceOnSameRuntimeRecovers() {
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = 1L))
        surface.canvas { drawImage(redImage(), rect(), SamplingOptions.NEAREST, paint()) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("resource.material.image.frame-budget", failure.message.orEmpty().substringBefore(':'))
        val next = Surface(1, 1)
        next.canvas { drawImage(redImage(), rect(), SamplingOptions.NEAREST, paint()) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), next.render().pixels)
    }

    private fun bounded(result: WgslFloatEnvelopeV1Oracle.DrawResult): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        require(result is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { result.toString() }
        return result
    }
    private fun redImage() = Image.fromPixels(1, 1, byteArrayOf(-1, 0, 0, -1), alphaType = AlphaType.PREMUL)
    private fun rect(widthF32: Float = 1f) = RectF32.ofLTRB(0f, 0f, widthF32, 1f)
    private fun paint() = Paint(antiAlias = false, blendMode = BlendMode.SRC)
}
