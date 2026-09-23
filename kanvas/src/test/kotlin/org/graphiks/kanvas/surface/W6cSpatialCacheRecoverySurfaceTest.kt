@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public B/B−1 and recovery evidence for one W6c spatial result. */
class W6cSpatialCacheRecoverySurfaceTest {
    @Test
    fun coldSpatialPlanAcceptsExactBudgetBoundary() {
        // Five 1×1 RGBA8 targets (5 × 4: root, layer, source hand-off, frozen
        // Crop result, and terminal composite), two documented 16-byte W6
        // uniform rows (geometry/restore and source/filter), and one aligned
        // readback row (256). This is derived before a Surface is constructed.
        val admitted = cropSurface(spatialBudgetB)
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), admitted.render().pixels)

        val refused = cropSurface(spatialBudgetB - 1L)
        assertBudgetRefusalKeepsSentinel(refused)
    }

    @Test
    fun warmSpatialReplayKeepsPessimisticBudgetBoundary() {
        val surface = cropSurface(spatialBudgetB)
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
        surface.discardRecordedOperations()
        recordCrop(surface, ColorARGB.Red)
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)

        val refused = cropSurface(spatialBudgetB - 1L)
        assertBudgetRefusalKeepsSentinel(refused)
    }

    @Test
    fun changedPictureSourceGenerationMissesSpatialCache() {
        val red = sourcePicture(ColorARGB.Red)
        val blue = sourcePicture(ColorARGB.Blue)
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), renderPicture(red))
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), renderPicture(blue))
    }

    @Test
    fun changedDesiredOutputOrClipMissesSpatialCache() {
        val surface = Surface(2, 1)
        surface.canvas {
            clipRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), antiAlias = false)
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(RectF32.ofLTRB(0f, 0f, 2f, 1f)))))
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u), surface.render().pixels)
    }

    @Test
    fun lateSpatialRefusalLeavesSentinelAndSameSurfaceRecovers() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val surface = Surface(1, 1)
        val shared = ImageFilter.ColorFilter(org.graphiks.kanvas.paint.ColorFilter.Luma)
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.Red, imageFilter = ImageFilter.Crop(bounds), antiAlias = false))
            drawRect(bounds, Paint(ColorARGB.Red, imageFilter = ImageFilter.Merge(List(256) { shared }), antiAlias = false))
        }
        val sentinel = UByteArray(4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6b.filter.native_capability:") == true, failure.message ?: "missing diagnostic")
        assertContentEquals(UByteArray(4) { 0x5au }, sentinel)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
    }

    private fun cropSurface(frameLocalBudgetBytes: Long): Surface =
        Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = frameLocalBudgetBytes)).also { surface ->
            recordCrop(surface, ColorARGB.Red)
        }

    private fun recordCrop(surface: Surface, color: ColorARGB) = surface.canvas {
        saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(RectF32.ofLTRB(0f, 0f, 1f, 1f)))))
        drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(color, antiAlias = false))
        restore()
    }

    private fun sourcePicture(color: ColorARGB): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f)).drawRect(
            RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(color, imageFilter = ImageFilter.Crop(RectF32.ofLTRB(0f, 0f, 1f, 1f)), antiAlias = false))
    }.finishRecordingAsPicture()

    private fun renderPicture(picture: Picture): UByteArray = Surface(1, 1).also { surface ->
        surface.canvas { picture.playback(this) }
    }.render().pixels

    private fun assertBudgetRefusalKeepsSentinel(surface: Surface) {
        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            failure.message ?: "missing budget diagnostic")
        assertContentEquals(before, sentinel)
    }

    private companion object {
        const val spatialBudgetB: Long = 5L * 4L + 2L * 16L + 256L
    }
}
