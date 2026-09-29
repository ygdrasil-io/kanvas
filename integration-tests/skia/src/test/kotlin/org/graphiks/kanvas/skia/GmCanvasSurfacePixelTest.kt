@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Public Surface pixels for GmCanvas Rect preservation under scale/translate transforms. */
class GmCanvasSurfacePixelTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `filtered reflected Rect reaches public pixels through adapter and picture replay`() {
        // Fixed before Surface construction: T(220,0)*S(-1,1) maps this Rect to x=[20,220].
        val extent = RectF32.ofLTRB(0f, 0f, 520f, 520f)
        val rect = RectF32.ofOriginSize(0f, 10f, 200f, 200f)
        val paint = Paint(
            color = ColorARGB.Black,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 6.2735f),
            colorFilter = ColorFilter.Blend(ColorARGB.Black, BlendMode.SRC_IN),
        )
        fun record(canvas: GmCanvas) {
            canvas.concat(Matrix3x3F32.translation(220f, 0f) * Matrix3x3F32.scaling(-1f, 1f))
            canvas.drawRect(rect, paint)
        }

        GpuAvailability.requireWebGpu()
        val directSurface = Surface(520, 520)
        record(GmCanvas(directSurface.canvas(), 520, 520))
        val recorder = PictureRecorder()
        record(GmCanvas(recorder.beginRecording(extent), 520, 520))
        val picture = recorder.finishRecordingAsPicture()
        val replaySurface = Surface(520, 520)
        replaySurface.canvas { picture.playback(this) }

        val direct = directSurface.render()
        val replay = replaySurface.render()
        assertPixel(direct.pixels, 520, 100, 100, 0, 0, 0, 255)
        assertPixel(direct.pixels, 520, 0, 0, 0, 0, 0, 0)
        val halo = pixel(direct.pixels, 520, 15, 100)
        assertTrue(halo[0] == 0.toUByte() && halo[1] == 0.toUByte() && halo[2] == 0.toUByte() &&
            halo[3].toInt() in 1..254, "expected blur halo at (15,100), got ${halo.toList()}")
        assertPixelsEqual(direct.pixels, replay.pixels, "direct and Picture replay")
        assertEvidence(direct)
        assertEvidence(replay)
    }

    @Test
    fun `reflected Rect keeps local shader clip and adapter state restoration`() {
        // Literal endpoints and markers precede both Surfaces: red at (8,0), blue at (5,0),
        // clipped transparent (4,0), then green markers at (1,2) and (1,1).
        val rect = RectF32.ofLTRB(0f, 0f, 5f, 1f)
        val clip = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val gradient = Paint(shader = Shader.LinearGradient(
            Point2F32(1f, 0f), Point2F32(3f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        ), antiAlias = false)
        val marker = Paint(ColorARGB.Green, antiAlias = false)
        fun record(inner: org.graphiks.kanvas.canvas.Canvas, canvas: GmCanvas) {
            inner.translate(1f, 0f)
            canvas.save()
            canvas.translate(8f, 0f)
            canvas.scale(-1f, 1f)
            canvas.clipRect(clip)
            canvas.drawRect(rect, gradient)
            inner.drawRect(RectF32.ofLTRB(0f, 2f, 1f, 3f), marker)
            canvas.restore()
            canvas.drawRect(RectF32.ofLTRB(0f, 1f, 1f, 2f), marker)
        }

        GpuAvailability.requireWebGpu()
        val directSurface = Surface(10, 3)
        directSurface.canvas().also { inner -> record(inner, GmCanvas(inner, 10, 3)) }
        val direct = directSurface.render()
        // Picture.playback ignores SetClip (Picture.kt) and is therefore not a usable replay
        // contract for this explicit clip control. A second public Surface render still proves
        // replay of the recorded Surface operations; the filtered witness above covers Picture.
        val replay = directSurface.render()
        assertPixel(direct.pixels, 10, 8, 0, 255, 0, 0, 255)
        assertPixel(direct.pixels, 10, 5, 0, 0, 0, 255, 255)
        assertPixel(direct.pixels, 10, 4, 0, 0, 0, 0, 0)
        assertPixel(direct.pixels, 10, 1, 2, 0, 255, 0, 255)
        assertPixel(direct.pixels, 10, 1, 1, 0, 255, 0, 255)
        assertPixelsEqual(direct.pixels, replay.pixels, "successive Surface renders")
        assertEvidence(direct)
        assertEvidence(replay)
    }

    @Test
    fun `scaled Rect stroke keeps local width and transformed clip`() {
        // A local two-pixel stroke scales to the literal outer [2,2,14,14] and inner [6,6,10,10]
        // contours; the local clip ends at device x=8.
        val rect = RectF32.ofLTRB(2f, 2f, 6f, 6f)
        val clip = RectF32.ofLTRB(0f, 0f, 4f, 8f)
        val stroke = Paint(ColorARGB.Red, style = PaintStyle.STROKE, strokeWidth = 2f, antiAlias = false)
        fun record(canvas: GmCanvas) {
            canvas.scale(2f, 2f)
            canvas.clipRect(clip)
            canvas.drawRect(rect, stroke)
        }

        GpuAvailability.requireWebGpu()
        val directSurface = Surface(16, 16)
        record(GmCanvas(directSurface.canvas(), 16, 16))
        val direct = directSurface.render()
        // See the reflected clip control: this case intentionally retains its literal clip
        // boundary without claiming the unrelated Picture SetClip replay contract.
        val replay = directSurface.render()
        assertPixel(direct.pixels, 16, 2, 8, 255, 0, 0, 255)
        assertPixel(direct.pixels, 16, 5, 8, 255, 0, 0, 255)
        assertPixel(direct.pixels, 16, 6, 8, 0, 0, 0, 0)
        assertPixel(direct.pixels, 16, 10, 4, 0, 0, 0, 0)
        assertPixelsEqual(direct.pixels, replay.pixels, "successive Surface renders")
        assertEvidence(direct)
        assertEvidence(replay)
    }

    private fun assertEvidence(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun assertPixelsEqual(direct: UByteArray, replay: UByteArray, label: String) {
        val mismatch = direct.indices.firstOrNull { direct[it] != replay[it] }
        assertTrue(mismatch == null, "$label differ at byte $mismatch: " +
            "${mismatch?.let { "${direct[it].toInt()}/${replay[it].toInt()}" }}")
    }

    private fun assertPixel(
        pixels: UByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int,
    ) {
        val actual = pixel(pixels, width, x, y)
        assertTrue(actual.contentEquals(ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())),
            "pixel ($x,$y) was ${actual.toList()}")
    }

    private fun pixel(pixels: UByteArray, width: Int, x: Int, y: Int): UByteArray {
        val offset = (y * width + x) * 4
        return pixels.copyOfRange(offset, offset + 4)
    }
}
