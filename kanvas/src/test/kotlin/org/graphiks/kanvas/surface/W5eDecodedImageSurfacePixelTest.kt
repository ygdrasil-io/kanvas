@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class W5eDecodedImageSurfacePixelTest {
    @Test fun formatsAlphaAndColorSpaceMatchOracle() {
        // Every legal format × alpha × color-space × layout combination is publicly rendered.
        // The two rows exercise nonzero and zero stored alpha, including OPAQUE's ignored alpha.
        val sourcePaint = paint().copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        for (colorType in listOf(ColorType.RGBA_8888, ColorType.BGRA_8888, ColorType.SRGBA_8888, ColorType.ALPHA_8))
            for (alphaType in listOf(AlphaType.OPAQUE, AlphaType.PREMUL, AlphaType.UNPREMUL))
                for (colorSpace in listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.LINEAR_SRGB)) {
                    if (colorType == ColorType.SRGBA_8888 && colorSpace != ColorSpace.SRGB) continue // contradicted below
                    val isMask = colorType == ColorType.ALPHA_8
                    val rgb = if (alphaType == AlphaType.PREMUL) listOf(27, 79, 45) else listOf(55, 160, 91)
                    val ordered = if (colorType == ColorType.BGRA_8888) rgb.reversed() else rgb
                    val texels = if (isMask) listOf(byteArrayOf(127), byteArrayOf(0)) else listOf(
                        (ordered + 127).map(Int::toByte).toByteArray(),
                        (ordered + 0).map(Int::toByte).toByteArray())
                    for (paddingI32 in listOf(0, 5)) {
                        val rowBytesI32 = texels[0].size + paddingI32
                        val bytes = ByteArray(rowBytesI32 * 2) { 73 }
                        texels.forEachIndexed { rowI32, texel -> texel.copyInto(bytes, rowI32 * rowBytesI32) }
                        val label = "$colorType/$alphaType/${colorSpace.name}/padding=$paddingI32"
                        val image = Image.fromPixels(1, 2, bytes, colorType, label, alphaType, colorSpace, rowBytesI32)
                        val surface = Surface(1, 2)
                        surface.canvas { drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 2f), SamplingOptions.NEAREST, sourcePaint) }
                        val expected = texels.map { texel ->
                            W5eDecodedImageCpuOracle.colorPixel(colorType, alphaType, colorSpace, texel,
                                paintAlphaF32 = if (isMask) 1f else sourcePaint.color.alphaNormalized,
                                paintColor = sourcePaint.color).also {
                                require(it is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "$label: $it" }
                            } as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
                        }
                        val result = surface.render()
                        expected.forEachIndexed { rowI32, value ->
                            WgslFloatEnvelopeV1Oracle.assertAdmits(value, result.pixels.copyOfRange(rowI32 * 4, rowI32 * 4 + 4))
                        }
                        assertEquals(1, result.stats.opsDispatched, label)
                        assertEquals(0, result.stats.opsRefused, label)
                    }
                }
    }
    @Test fun rowPaddingIsIgnoredAndWebGpuPaddingDoesNotLeak() {
        val logical = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1, -1, -1, 0, -1)
        val padded = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 33, 33, 33, 33,
            0, 0, -1, -1, -1, -1, 0, -1, 44, 44, 44, 44)
        fun render(image: Image): UByteArray {
            val surface = Surface(2, 2)
            surface.canvas { drawImage(image, RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.NEAREST, paint()) }
            return surface.render().pixels
        }
        assertContentEquals(render(Image.fromPixels(2, 2, logical, alphaType = AlphaType.PREMUL)),
            render(Image.fromPixels(2, 2, padded, alphaType = AlphaType.PREMUL, rowBytesI32 = 12)))
    }

    @Test fun a8UsesPaintColorAndAlpha() {
        val mask = Image.fromPixels(1, 1, byteArrayOf(127), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        val paint = Paint(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f), antiAlias = false, blendMode = BlendMode.SRC)
        val surface = Surface(1, 1)
        surface.canvas { drawImage(mask, RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST, paint) }
        val expected = W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.PREMUL, ColorSpace.SRGB,
            byteArrayOf(127), paintColor = paint.color)
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, surface.render().pixels)
        // The paint shader is an adjacent child; its local matrix precedes the image source projection.
        val opaqueMask = Image.fromPixels(1, 1, byteArrayOf(-1), ColorType.ALPHA_8, alphaType = AlphaType.UNPREMUL)
        val child = org.graphiks.kanvas.paint.Shader.WithLocalMatrix(
            org.graphiks.kanvas.paint.Shader.LinearGradient(org.graphiks.math.geometry.Point2F32(0f, 0f),
                org.graphiks.math.geometry.Point2F32(8f, 0f), listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Red),
                    org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Blue), org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue))),
            Matrix3x3F32.translation(4f, 0f))
        val shaderSurface = Surface(16, 1)
        shaderSurface.canvas {
            translate(2f, 0f)
            drawImage(opaqueMask, RectF32.ofLTRB(4f, 0f, 12f, 1f), SamplingOptions.NEAREST,
                paint.copy(shader = child))
        }
        val shaderPixels = shaderSurface.render().pixels
        for ((pixelXI32, color) in listOf(6 to ColorARGB.Red, 10 to ColorARGB.Blue)) {
            val childExpected = W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.UNPREMUL, ColorSpace.SRGB,
                byteArrayOf(-1), paintAlphaF32 = paint.color.alphaNormalized, paintColor = color)
            require(childExpected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { childExpected.toString() }
            WgslFloatEnvelopeV1Oracle.assertAdmits(childExpected, shaderPixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
        }
        // Two independently normalized child tables share one frame slab after range rebasing.
        fun hardChild(left: ColorARGB, right: ColorARGB) = org.graphiks.kanvas.paint.Shader.LinearGradient(
            org.graphiks.math.geometry.Point2F32(0f, 0f), org.graphiks.math.geometry.Point2F32(4f, 0f), listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, left), org.graphiks.kanvas.paint.GradientStop(.5f, left),
                org.graphiks.kanvas.paint.GradientStop(.5f, right), org.graphiks.kanvas.paint.GradientStop(1f, right)))
        val mixed = Surface(24, 1)
        mixed.canvas {
            drawImage(opaqueMask, RectF32.ofLTRB(0f, 0f, 4f, 1f), SamplingOptions.NEAREST,
                paint.copy(shader = hardChild(ColorARGB.Red, ColorARGB.Blue)))
            drawImage(opaqueMask, RectF32.ofLTRB(4f, 0f, 8f, 1f), SamplingOptions.NEAREST,
                paint.copy(shader = org.graphiks.kanvas.paint.Shader.WithLocalMatrix(
                    hardChild(ColorARGB.Green, ColorARGB.Yellow), Matrix3x3F32.translation(4f, 0f))))
        }
        val mixedResult = mixed.render()
        for ((pixelXI32, color) in listOf(0 to ColorARGB.Red, 3 to ColorARGB.Blue, 4 to ColorARGB.Green, 7 to ColorARGB.Yellow)) {
            val childExpected = W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.UNPREMUL,
                ColorSpace.SRGB, byteArrayOf(-1), paintAlphaF32 = paint.color.alphaNormalized, paintColor = color)
            require(childExpected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { childExpected.toString() }
            WgslFloatEnvelopeV1Oracle.assertAdmits(childExpected, mixedResult.pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
        }
        assertEquals(2, mixedResult.stats.opsDispatched)
        assertEquals(0, mixedResult.stats.opsRefused)
    }

    @Test fun rgbaPaintRgbDoesNotTintButPaintAlphaApplies() {
        val bytes = byteArrayOf(-1, 0, 0, -1)
        val image = Image.fromPixels(1, 1, bytes, alphaType = AlphaType.PREMUL)
        val sourcePaint = Paint(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f), antiAlias = false, blendMode = BlendMode.SRC)
        val surface = Surface(1, 1)
        surface.canvas { drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST,
            sourcePaint) }
        val expected = W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.SRGB,
            bytes, paintAlphaF32 = sourcePaint.color.alphaNormalized)
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, surface.render().pixels)
    }

    @Test fun publicLayoutRefusalsAndPromotedPayloadRefusalsRecover() {
        val strideFailure = assertThrows<IllegalArgumentException> {
            Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), rowBytesI32 = 3)
        }
        assertEquals("image.row-bytes-too-small", strideFailure.message)
        assertRecovery()
        val overflowFailure = assertThrows<IllegalArgumentException> {
            Image.fromPixels(Int.MAX_VALUE, 1, byteArrayOf(), rowBytesI32 = 0)
        }
        assertEquals("image.row-bytes-overflow", overflowFailure.message)
        assertRecovery()
        val cases = listOf(
            Image.fromPixels(1, 1, byteArrayOf(1, 2, 3), alphaType = AlphaType.PREMUL) to "payload",
            Image.fromPixels(0, 1, byteArrayOf(), alphaType = AlphaType.PREMUL) to "dimensions",
            Image.fromPixels(1, Int.MAX_VALUE, byteArrayOf(), alphaType = AlphaType.PREMUL) to "overflow",
            Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), ColorType.SRGBA_8888, alphaType = AlphaType.UNPREMUL,
                colorSpace = ColorSpace.DISPLAY_P3) to "color-space",
            Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), ColorType.SRGBA_8888, alphaType = AlphaType.UNPREMUL,
                colorSpace = ColorSpace.LINEAR_SRGB) to "color-space",
        )
        for ((image, code) in cases) {
            val surface = Surface(1, 1)
            surface.canvas { drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST, paint()) }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.image.$code", failure.message.orEmpty().substringBefore(':'))
            assertRecovery()
        }
    }
    @Test fun rgbaPremulNearestDrawImageIsPlanOwned() {
        // SRC and the bounded legacy policy expose an accidental prepared-route dispatch.
        val surface = Surface(6, 4, config = RenderConfig(preparedImageRoute = PreparedImageRoute.BOUNDED_NEAREST_1_TO_1))
        surface.canvas { drawImage(image(), fullDestination(), SamplingOptions.NEAREST, paint()) }
        val result = surface.render()
        assertContentEquals(W5eDecodedImageCpuOracle.nearest(6, 4, fullSource(), fullDestination()), result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(1, result.stats.drawCallCount)
    }

    @Test fun sourceRectAndNegativeDestinationPreservePixelCenters() {
        // Cropping src before projection changes the outer columns; dropping the flip reverses every row.
        val src = RectF32.ofLTRB(-1f, 0f, 4f, 2f)
        val dst = RectF32.ofLTRB(6f, 0f, 0f, 4f)
        val surface = Surface(6, 4)
        surface.canvas { drawImageRect(image(), src, dst, SamplingOptions.NEAREST, paint()) }
        assertContentEquals(W5eDecodedImageCpuOracle.nearest(6, 4, src, dst), surface.render().pixels)
    }

    @Test fun capturedPixelsIgnoreLaterSourceMutation() {
        // Keeping the mutable producer instead of its capture snapshot changes both replayed frames.
        val bytes = W5eDecodedImageCpuOracle.pixels()
        val source = Image.fromPixels(3, 2, bytes, alphaType = AlphaType.PREMUL)
        val recorder = PictureRecorder()
        recorder.beginRecording(fullDestination()).drawImage(source, fullDestination(), SamplingOptions.NEAREST, paint())
        val picture = recorder.finishRecordingAsPicture()
        bytes.fill(0)
        val surface = Surface(6, 4)
        surface.canvas { picture.playback(this) }
        repeat(2) {
            assertContentEquals(W5eDecodedImageCpuOracle.nearest(6, 4, fullSource(), fullDestination()), surface.render().pixels)
        }
    }

    @Test fun unsupportedExternalResourceStaysPreAdmission() {
        val surface = Surface(6, 4)
        surface.canvas { drawImage(Image.placeholder(3, 2), fullDestination(), SamplingOptions.NEAREST) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.image.pixels_missing", failure.message.orEmpty().substringBefore(':'))
    }

    @Test fun unboundedProjectiveImageCoordinatesRefuseAndRecover() {
        // The inverse denominator crosses zero within the rendered device domain.
        val surface = Surface(6, 4)
        surface.canvas {
            concat(Matrix3x3F32(sx = 1.5f, tx = 1f, persp0 = 1f, persp2 = 0f))
            drawImage(image(), fullDestination(), SamplingOptions.NEAREST, paint())
        }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.image.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        assertRecovery()
    }

    @Test fun failedPromotedDrawRecoversOnSameRuntime() {
        // A public insufficient frame budget must terminate ownership and leave the runtime usable.
        val surface = Surface(6, 4, config = RenderConfig(frameLocalBudgetBytes = 1L))
        surface.canvas { drawImage(image(), fullDestination(), SamplingOptions.NEAREST, paint()) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("resource.material.image.frame-budget", failure.message.orEmpty().substringBefore(':'))
        assertRecovery()
    }

    private fun assertRecovery() {
        val healthy = Surface(6, 4)
        healthy.canvas { drawImage(image(), fullDestination(), SamplingOptions.NEAREST, paint()) }
        assertContentEquals(W5eDecodedImageCpuOracle.nearest(6, 4, fullSource(), fullDestination()), healthy.render().pixels)
    }
    private fun image() = Image.fromPixels(3, 2, W5eDecodedImageCpuOracle.pixels(), alphaType = AlphaType.PREMUL)
    private fun paint() = Paint(color = ColorARGB.Black, antiAlias = false, blendMode = BlendMode.SRC)
    private fun fullSource() = RectF32.ofLTRB(0f, 0f, 3f, 2f)
    private fun fullDestination() = RectF32.ofLTRB(0f, 0f, 6f, 4f)

}
