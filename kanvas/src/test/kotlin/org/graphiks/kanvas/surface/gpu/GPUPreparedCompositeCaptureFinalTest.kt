package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedCompositeCaptureFinalTest {

    private val id33 = Matrix3x3F32.Identity
    private val black = Paint(color = Color.fromArgb(255, 0, 0, 0))
    private val red = Paint(color = Color.fromArgb(255, 255, 0, 0))

    @Test
    fun `two different rects produce different identities`() {
        val r1 = capture(listOf(simpleRect(0f, 0f, 5f, 5f)))
        val r2 = capture(listOf(simpleRect(10f, 10f, 20f, 20f)))
        val id1 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r1)).capture.expandedOperations[0].identity
        val id2 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r2)).capture.expandedOperations[0].identity
        assertNotEquals(id1, id2)
    }

    @Test
    fun `same rect same paint produces identical identity`() {
        val r1 = capture(listOf(simpleRect(0f, 0f, 5f, 5f)))
        val r2 = capture(listOf(simpleRect(0f, 0f, 5f, 5f)))
        val id1 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r1)).capture.expandedOperations[0].identity
        val id2 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r2)).capture.expandedOperations[0].identity
        assertEquals(id1, id2)
    }

    @Test
    fun `different paints produce different identities`() {
        val r1 = capture(listOf(simpleRect(0f, 0f, 5f, 5f, black)))
        val r2 = capture(listOf(simpleRect(0f, 0f, 5f, 5f, red)))
        val id1 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r1)).capture.expandedOperations[0].identity
        val id2 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r2)).capture.expandedOperations[0].identity
        assertNotEquals(id1, id2)
    }

    @Test
    fun `painted picture is counted exactly once`() {
        val inner = recordPic { it.drawRect(RectF32(0f, 0f, 10f, 10f), black) }
        val r = capture(listOf(DisplayOp.DrawPicture(inner, red, id33, ClipStack.WideOpen)))
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        val painted = ready.capture.scopes.values.filter { it.sourceKind == GPUPreparedCompositeScopeKind.PaintedPicture }
        assertEquals(1, painted.size)
    }

    @Test
    fun `unpainted picture expands without creating synthetic scope`() {
        val inner = recordPic { it.drawRect(RectF32(0f, 0f, 1f, 1f), black) }
        val r = capture(listOf(DisplayOp.DrawPicture(inner, null, id33, ClipStack.WideOpen)))
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        assertEquals(
            listOf(GPUPreparedCompositeScopeKind.Root),
            ready.capture.scopes.values.map { it.sourceKind },
        )
    }

    @Test
    fun `budget exceeded with ordinary draws is refused`() {
        val ops = (0..12).map { simpleRect(it.toFloat(), 0f, it.toFloat() + 1, 1f) }
        assertIs<GPUPreparedCompositeCaptureResult.Refused>(capture(ops, maxExpandedOps = 10))
    }

    @Test
    fun `budget at exact limit is accepted`() {
        assertIs<GPUPreparedCompositeCaptureResult.Ready>(capture(
            (0..4).map { simpleRect(it.toFloat(), 0f, it.toFloat() + 1, 1f) }, maxExpandedOps = 5))
    }

    @Test
    fun `picture self-cycle is refused`() {
        val operations = mutableListOf<DisplayOp>()
        val picture = Picture(RectF32(0f, 0f, 1f, 1f), operations)
        operations += DisplayOp.DrawPicture(picture, null, id33, ClipStack.WideOpen)

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(
            capture(listOf(DisplayOp.DrawPicture(picture, null, id33, ClipStack.WideOpen))),
        )

        assertEquals(GPUPreparedCompositeRefusalCodes.PICTURE_CYCLE, refused.code)
    }

    @Test
    fun `unbalanced begin layer is refused`() {
        val r = capture(listOf(DisplayOp.BeginLayer(SaveLayerRec(null, null))))
        assertEquals("unsupported.composite.layer.unbalanced", (assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)).code)
    }

    @Test
    fun `orphan end layer is refused`() {
        val r = capture(listOf(DisplayOp.EndLayer))
        assertEquals("unsupported.composite.layer.unbalanced", (assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)).code)
    }

    private fun capture(ops: List<DisplayOp>, maxExpandedOps: Int = 100): GPUPreparedCompositeCaptureResult =
        GPUPreparedCompositeCapturer.capture(ops, GPUPreparedCompositeCaptureLimits(maxExpandedOps = maxExpandedOps))

    private fun simpleRect(x: Float, y: Float, w: Float, h: Float, paint: Paint = black) =
        DisplayOp.DrawRect(RectF32(x, y, w, h), paint, id33, ClipStack.WideOpen)

    private fun recordPic(block: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Picture {
        val rec = PictureRecorder()
        val c = rec.beginRecording(RectF32(0f, 0f, 100f, 100f))
        block(c)
        return rec.finishRecordingAsPicture()
    }
}
