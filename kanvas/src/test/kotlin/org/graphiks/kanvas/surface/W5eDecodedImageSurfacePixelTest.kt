@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
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
