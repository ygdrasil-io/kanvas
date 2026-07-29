package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUPreparedCompositeCaptureTest {

    private val identity33 = Matrix33.identity()

    @Test
    fun `unmatched restore refuses without partial expansion`() {
        val result = capture(listOf(DisplayOp.EndLayer))
        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.layer.unbalanced", refused.code)
        assertEquals(0, refused.operationIndex)
    }

    @Test
    fun `unclosed begin layer refuses`() {
        val result = capture(listOf(
            DisplayOp.BeginLayer(SaveLayerRec(null, null)),
        ))
        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.layer.unbalanced", refused.code)
    }

    @Test
    fun `balanced begin end layer creates save layer scope`() {
        val ops = listOf(
            DisplayOp.BeginLayer(SaveLayerRec(null, null)),
            DisplayOp.EndLayer,
        )
        val result = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val saveLayerScopes = ready.capture.scopes.values.filter {
            it.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer
        }
        assertEquals(1, saveLayerScopes.size)
    }

    @Test
    fun `empty frame capture produces root scope only`() {
        val result = capture(emptyList())
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        assertEquals(1, ready.capture.scopes.size)
        assertEquals(ready.capture.rootScopeId, ready.capture.scopes.keys.single())
    }

    @Test
    fun `capture preserves exact operation order`() {
        val ops = listOf(
            simpleRect(0f, 0f, 5f, 5f),
            DisplayOp.BeginLayer(SaveLayerRec(null, null)),
            simpleRect(5f, 5f, 10f, 10f),
            DisplayOp.EndLayer,
            simpleRect(10f, 10f, 15f, 15f),
        )
        val result = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val rootEntries = ready.capture.scopes[ready.capture.rootScopeId]!!.entries
        assertEquals(3, rootEntries.size)
        assertTrue(rootEntries[0] is GPUPreparedCompositeEntry.Draw)
        assertTrue(rootEntries[1] is GPUPreparedCompositeEntry.Scope)
        assertTrue(rootEntries[2] is GPUPreparedCompositeEntry.Draw)
    }

    @Test
    fun `depth budget exceeded refuses before allocation`() {
        val ops = mutableListOf<DisplayOp>()
        for (i in 0..5) {
            ops.add(DisplayOp.BeginLayer(SaveLayerRec(null, null)))
        }
        for (i in 0..5) {
            ops.add(DisplayOp.EndLayer)
        }
        val result = capture(ops, maxNestingDepth = 3)
        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.layer.budget", refused.code)
    }

    @Test
    fun `painted picture becomes synthetic child scope`() {
        val innerPicture = recordPicture { canvas ->
            canvas.drawRect(Rect(0f, 0f, 10f, 10f), Paint())
            canvas.drawRect(Rect(10f, 10f, 20f, 20f), Paint())
        }
        val ops = listOf(
            DisplayOp.DrawPicture(innerPicture, Paint(), identity33, ClipStack.WideOpen),
        )
        val result = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val child = ready.capture.scopes.values.firstOrNull {
            it.sourceKind == GPUPreparedCompositeScopeKind.PaintedPicture
        }
        assertTrue(child != null, "expected painted picture scope")
        assertTrue(child!!.entries.size >= 2, "expected at least 2 entries from expanded picture")
    }

    @Test
    fun `unpainted picture expands inline without synthetic scope`() {
        val picture = recordPicture { canvas ->
            canvas.drawRect(Rect(0f, 0f, 10f, 10f), Paint())
        }
        val ops = listOf(
            DisplayOp.DrawPicture(picture, null, identity33, ClipStack.WideOpen),
        )
        val result = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        assertEquals(
            listOf(GPUPreparedCompositeScopeKind.Root),
            ready.capture.scopes.values.map { it.sourceKind },
        )
        assertTrue(ready.capture.expandedOperations.size >= 1)
    }

    @Test
    fun `captured operation snapshot is typed not Any`() {
        val ops = listOf(simpleRect(0f, 0f, 5f, 5f))
        val result = capture(ops)
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val op = ready.capture.expandedOperations.first()
        val snapshot = op.snapshot
        assertIs<GPUPreparedOperationSnapshot>(snapshot)
        assertTrue(snapshot.identityFragment().isNotBlank())
    }

    private fun capture(
        ops: List<DisplayOp>,
        maxRecursionDepth: Int = 10,
        maxNestingDepth: Int = 10,
        maxExpandedOps: Int = 1000,
    ): GPUPreparedCompositeCaptureResult {
        val limits = GPUPreparedCompositeCaptureLimits(
            maxRecursionDepth = maxRecursionDepth,
            maxNestingDepth = maxNestingDepth,
            maxExpandedOps = maxExpandedOps,
        )
        return GPUPreparedCompositeCapturer.capture(ops, limits)
    }

    private fun simpleRect(x: Float, y: Float, w: Float, h: Float): DisplayOp {
        return DisplayOp.DrawRect(Rect(x, y, w, h), Paint(), identity33, ClipStack.WideOpen)
    }

    private fun recordPicture(block: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect(0f, 0f, 100f, 100f))
        block(canvas)
        return recorder.finishRecordingAsPicture()
    }
}
