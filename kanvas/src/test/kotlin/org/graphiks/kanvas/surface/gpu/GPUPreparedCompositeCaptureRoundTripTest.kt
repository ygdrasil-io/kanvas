package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedCompositeCaptureRoundTripTest {

    private val identity33 = Matrix3x3F32.Identity
    private val opaqueBlack = ColorARGB.of(255, 0, 0, 0)
    private val opaqueWhite = ColorARGB.of(255, 255, 255, 255)
    private val halfAlphaRed = ColorARGB.of(128, 255, 0, 0)

    @Test
    fun `two rects of different geometry same index produce different identities`() {
        val ops = listOf(
            DisplayOp.DrawRect(RectF32(0f, 0f, 5f, 5f), Paint(color = opaqueBlack), identity33, ClipStack.WideOpen),
            DisplayOp.DrawRect(RectF32(10f, 10f, 20f, 20f), Paint(color = opaqueBlack), identity33, ClipStack.WideOpen),
        )
        val r = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        val id0 = ready.capture.expandedOperations[0].identity
        val id1 = ready.capture.expandedOperations[1].identity
        assertNotEquals(id0, id1, "different geometry must produce different identity")
    }

    @Test
    fun `two paints with different colors produce different identities`() {
        val ops = listOf(
            DisplayOp.DrawRect(RectF32(0f, 0f, 5f, 5f), Paint(color = opaqueBlack), identity33, ClipStack.WideOpen),
            DisplayOp.DrawRect(RectF32(0f, 0f, 5f, 5f), Paint(color = halfAlphaRed), identity33, ClipStack.WideOpen),
        )
        val r = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        assertNotEquals(
            ready.capture.expandedOperations[0].identity,
            ready.capture.expandedOperations[1].identity,
        )
    }

    @Test
    fun `identical operations produce the same identity`() {
        val p = Paint(color = opaqueWhite, blendMode = org.graphiks.kanvas.paint.BlendMode.SRC_OVER)
        val r1 = capture(listOf(DisplayOp.DrawRect(RectF32(0f, 0f, 5f, 5f), p, identity33, ClipStack.WideOpen)))
        val r2 = capture(listOf(DisplayOp.DrawRect(RectF32(0f, 0f, 5f, 5f), p, identity33, ClipStack.WideOpen)))
        val id1 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r1)).capture.expandedOperations[0].identity
        val id2 = (assertIs<GPUPreparedCompositeCaptureResult.Ready>(r2)).capture.expandedOperations[0].identity
        assertEquals(id1, id2)
    }

    @Test
    fun `nested picture operations do not share the same provenance`() {
        val inner = recordPicture { c -> c.drawRect(RectF32(0f, 0f, 2f, 2f), Paint()) }
        val outer = recordPicture { c -> c.drawPicture(inner, null) }
        val r = capture(listOf(DisplayOp.DrawPicture(outer, null, identity33, ClipStack.WideOpen)))
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        val provenances = ready.capture.expandedOperations.map { it.snapshot.identityFragment() }.distinct()
        assertTrue(provenances.size >= 2, "nested operations must have distinct provenances")
    }

    @Test
    fun `painted picture counted once as a scope and paint is preserved`() {
        val p = Paint(color = opaqueBlack)
        val inner = recordPicture { c -> c.drawRect(RectF32(0f, 0f, 10f, 10f), Paint()) }
        val ops = listOf(DisplayOp.DrawPicture(inner, p, identity33, ClipStack.WideOpen))
        val r = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
        val painted = ready.capture.scopes.values.filter {
            it.sourceKind == GPUPreparedCompositeScopeKind.PaintedPicture
        }
        assertEquals(1, painted.size, "painted picture must produce exactly one scope entry")
    }

    @Test
    fun `budget exceeded with only ordinary draws is refused`() {
        val ops = (0..100).map {
            DisplayOp.DrawRect(RectF32(it.toFloat(), 0f, it.toFloat() + 1, 1f), Paint(), identity33, ClipStack.WideOpen)
        }
        val r = capture(ops, maxExpandedOps = 50)
        assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)
    }

    @Test
    fun `budget at exact limit is accepted`() {
        val ops = (0 until 5).map {
            DisplayOp.DrawRect(RectF32(it.toFloat(), 0f, it.toFloat() + 1, 1f), Paint(), identity33, ClipStack.WideOpen)
        }
        val r = capture(ops, maxExpandedOps = 5)
        assertIs<GPUPreparedCompositeCaptureResult.Ready>(r)
    }

    @Test
    fun `budget limit plus one is refused`() {
        val ops = (0..7).map {
            DisplayOp.DrawRect(RectF32(it.toFloat(), 0f, it.toFloat() + 1, 1f), Paint(), identity33, ClipStack.WideOpen)
        }
        val r = capture(ops, maxExpandedOps = 7)
        assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)
    }

    @Test
    fun `unbalanced BeginLayer without EndLayer is refused`() {
        val ops = listOf(DisplayOp.BeginLayer(SaveLayerRec(null, null)))
        val r = capture(ops)
        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)
        assertEquals("unsupported.composite.layer.unbalanced", refused.code)
    }

    @Test
    fun `orphan EndLayer without BeginLayer is refused`() {
        val r = capture(listOf(DisplayOp.EndLayer))
        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(r)
        assertEquals("unsupported.composite.layer.unbalanced", refused.code)
    }

    private fun capture(
        ops: List<DisplayOp>,
        maxExpandedOps: Int = 100,
        maxRecursionDepth: Int = 10,
        maxNestingDepth: Int = 8,
    ): GPUPreparedCompositeCaptureResult {
        val limits = GPUPreparedCompositeCaptureLimits(maxRecursionDepth, maxNestingDepth, maxExpandedOps)
        return GPUPreparedCompositeCapturer.capture(ops, limits)
    }

    private fun recordPicture(block: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32(0f, 0f, 100f, 100f))
        block(canvas)
        return recorder.finishRecordingAsPicture()
    }
}
