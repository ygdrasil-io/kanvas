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
    @Test fun unownedDirectImagesKeepExplicitLinearSampling() {
        // A DrawColor keeps this entire frame outside W5e admission. Losing the
        // explicit sampler turns the two interior gray pixels into black/white.
        val linear = listOf(0f, .25f, .75f, 1f).map(::grayAttachment)
        val nearest = listOf(0f, 0f, 1f, 1f).map(::grayAttachment)
        for ((sampling, expected) in listOf(SamplingOptions.LINEAR to linear, SamplingOptions.NEAREST to nearest)) {
            val surface = Surface(4, 1)
            surface.canvas {
                drawColor(ColorARGB.Black)
                drawImage(blackWhiteImage(), rect(4f), sampling, Paint(antiAlias = false))
            }
            assertExpectedPixels(expected, surface.render().pixels)
        }
    }

    @Test fun unownedDirectCubicKeepsItsTypedRefusal() {
        val surface = Surface(4, 1)
        surface.canvas {
            drawColor(ColorARGB.Black)
            drawImage(blackWhiteImage(), rect(4f), SamplingOptions.Cubic(0f, .5f), Paint(antiAlias = false))
        }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.image.sampling_cubic", failure.message.orEmpty().substringBefore(':'))
    }

    @Test fun unownedPaddedImageRowsKeepDeclaredStrideAndSourceOffset() {
        val rows = listOf(
            listOf(ColorARGB.White, ColorARGB.Red, ColorARGB.Black, ColorARGB.Magenta),
            listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.Black),
            listOf(ColorARGB.White, ColorARGB.Yellow, ColorARGB.Cyan, ColorARGB.Red))
        val expected = endpointBytes(listOf(ColorARGB.Green, ColorARGB.Blue, ColorARGB.Yellow, ColorARGB.Cyan))
        for (format in listOf(ColorType.RGBA_8888, ColorType.BGRA_8888)) {
            val rowBytesI32 = 21
            val payload = ByteArray(rowBytesI32 * 3) { 73 }
            rows.forEachIndexed { rowI32, colors ->
                endpointBytes(colors, format == ColorType.BGRA_8888).toByteArray().copyInto(payload, rowI32 * rowBytesI32)
            }
            val image = Image.fromPixels(4, 3, payload, format, alphaType = AlphaType.PREMUL, rowBytesI32 = rowBytesI32)
            val surface = Surface(2, 2)
            surface.canvas {
                drawColor(ColorARGB.Black)
                drawImageRect(image, RectF32.ofLTRB(1f, 1f, 3f, 3f), RectF32.ofLTRB(0f, 0f, 2f, 2f),
                    SamplingOptions.NEAREST, Paint(antiAlias = false))
            }
            assertContentEquals(expected, surface.render().pixels, format.name)
        }
    }

    @Test fun unownedSyntheticImageSamplersKeepHistoricalCompatibility() {
        val linear = listOf(0f, .25f, .75f, 1f).map(::grayAttachment)
        val nearest = listOf(0f, 0f, 1f, 1f).map(::grayAttachment)
        val atlasExpected = listOf(0f, 1f, 0f, 0f).map(::grayAttachment)
        val image = blackWhiteImage()
        val shaderImage = image.copy(alphaType = AlphaType.UNPREMUL)
        for (kind in listOf("rect", "nine", "lattice-linear", "lattice-nearest", "atlas")) {
            val expected = when (kind) { "atlas", "rect" -> atlasExpected; "lattice-nearest" -> nearest; else -> linear }
            val surface = Surface(4, 1)
            surface.canvas {
                drawColor(ColorARGB.Black)
                when (kind) {
                    "rect" -> drawRect(rect(2f), Paint(antiAlias = false,
                        shader = Shader.Image(shaderImage, sampling = SamplingOptions.LINEAR)))
                    // Legacy Nine/Atlas are historically Linear, unlike promoted Nearest.
                    "nine" -> drawImageNine(image, rect(2f), rect(4f), Paint(antiAlias = false))
                    "lattice-linear", "lattice-nearest" -> drawImageLattice(image, Lattice(emptyList(), emptyList()),
                        rect(4f), sampling = if (kind == "lattice-linear") SamplingOptions.LINEAR else SamplingOptions.NEAREST,
                        paint = Paint(antiAlias = false))
                    "atlas" -> drawAtlas(image, listOf(Matrix3x3F32()), listOf(rect(2f)), paint = Paint(antiAlias = false))
                }
            }
            val result = try { surface.render() } catch (failure: IllegalStateException) {
                throw AssertionError("synthetic image kind=$kind", failure)
            }
            assertExpectedPixels(expected, result.pixels)
        }
        // Removing the old shader bridge must retain the grid's implicit WHITE paint.
        val white = endpointBytes(listOf(ColorARGB.White))
        for (nine in listOf(false, true)) {
            val mask = Image.fromPixels(1, 1, byteArrayOf(-1), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
            val surface = Surface(1, 1)
            surface.canvas {
                drawColor(ColorARGB.Black)
                if (nine) drawImageNine(mask, rect(), rect())
                else drawImageLattice(mask, Lattice(emptyList(), emptyList()), rect())
            }
            assertContentEquals(white, surface.render().pixels)
        }
        val excluded = Surface(4, 1)
        excluded.canvas {
            drawColor(ColorARGB.Black)
            drawRect(rect(4f), Paint(antiAlias = false, shader = Shader.WithLocalMatrix(
                Shader.Image(image, sampling = SamplingOptions.LINEAR), Matrix3x3F32.scaling(2f, 1f))))
        }
        val failure = assertThrows<IllegalStateException> { excluded.render() }
        assertEquals("unsupported.material.mapping.local_matrix", failure.message.orEmpty().substringBefore(':'))
        val boundedRoute = Surface(2, 1, config = RenderConfig(preparedImageRoute = PreparedImageRoute.BOUNDED_NEAREST_1_TO_1))
        boundedRoute.canvas {
            drawColor(ColorARGB.Black)
            drawRect(rect(2f), Paint(antiAlias = false, shader = Shader.Image(shaderImage, sampling = SamplingOptions.LINEAR)))
        }
        val samplingFailure = assertThrows<IllegalStateException> { boundedRoute.render() }
        assertEquals("unsupported.image.sampling_filter", samplingFailure.message.orEmpty().substringBefore(':'))
    }

    @Test fun nonuniformOffsetMultirowSnapshotsPreserveRgbaAndBgraCopies() {
        val colors = listOf(ColorARGB.White, ColorARGB.Red, ColorARGB.Black, ColorARGB.Magenta,
            ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.Black,
            ColorARGB.White, ColorARGB.Yellow, ColorARGB.Cyan, ColorARGB.Red)
        val subsetColors = listOf(ColorARGB.Green, ColorARGB.Blue, ColorARGB.Yellow, ColorARGB.Cyan)
        val expected = endpointBytes(subsetColors)
        for (format in PixelFormat.entries) {
            val fullRaw = endpointBytes(colors, format == PixelFormat.BGRA8)
            val subsetRaw = endpointBytes(subsetColors, format == PixelFormat.BGRA8)
            val source = Surface(4, 3, format)
            source.canvas { colors.forEachIndexed { indexI32, color ->
                val xF32 = (indexI32 % 4).toFloat(); val yF32 = (indexI32 / 4).toFloat()
                drawRect(RectF32.ofLTRB(xF32, yF32, xF32 + 1f, yF32 + 1f), paint().copy(color = color))
            } }
            assertContentEquals(fullRaw, assertNotNull(source.makeImageSnapshot().pixels).toUByteArray())
            val subset = assertNotNull(source.makeImageSnapshot(RectF32.ofLTRB(1f, 1f, 3f, 3f)))
                .copy().reinterpretColorSpace(ColorSpace.SRGB)
            assertContentEquals(subsetRaw, assertNotNull(subset.pixels).toUByteArray())
            val recorder = PictureRecorder()
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 2f)).drawImage(subset,
                RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.NEAREST, paint())
            val restored = assertNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
            val replay = Surface(2, 2)
            replay.canvas { restored.playback(this) }
            assertContentEquals(expected, replay.render().pixels)
        }
    }

    private fun blackWhiteImage() = Image.fromPixels(2, 1, byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1), alphaType = AlphaType.PREMUL)
    private fun grayAttachment(valueF32: Float) = bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(
        arrayOf(WgslFloatEnvelopeV1Oracle.Interval.input(valueF32), WgslFloatEnvelopeV1Oracle.Interval.input(valueF32),
            WgslFloatEnvelopeV1Oracle.Interval.input(valueF32), WgslFloatEnvelopeV1Oracle.Interval.ONE)))
    private fun assertExpectedPixels(expected: List<WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>, pixels: UByteArray) {
        assertEquals(expected.size * 4, pixels.size)
        expected.forEachIndexed { indexI32, value -> WgslFloatEnvelopeV1Oracle.assertAdmits(value,
            pixels.copyOfRange(indexI32 * 4, indexI32 * 4 + 4)) }
    }
    private fun endpointBytes(colors: List<ColorARGB>, bgra: Boolean = false): UByteArray = colors.flatMap { color ->
        val rgb = listOf(color.red, color.green, color.blue)
        (if (bgra) rgb.reversed() else rgb) + color.alpha
    }.map(Int::toUByte).toUByteArray()

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
