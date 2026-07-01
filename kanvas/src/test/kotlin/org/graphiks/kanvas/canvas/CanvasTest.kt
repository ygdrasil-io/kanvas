package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class TestBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
    fun count() = ops.size
}

class CanvasTest {
    @Test fun `Canvas records drawRect`() { val b = TestBuffer(); Canvas(b).drawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED)); assertEquals(1, b.count()) }
    @Test fun `Canvas save does not emit EndLayer`() { val b = TestBuffer(); val c = Canvas(b); c.save(); c.drawRect(Rect.fromLTRB(0f,0f,10f,10f), Paint.fill(Color.WHITE)); c.restore(); assertTrue(b.ops().none { it is DisplayOp.EndLayer }) }
    @Test fun `Canvas saveLayer emits EndLayer`() { val b = TestBuffer(); val c = Canvas(b); c.saveLayer(); c.drawRect(Rect.fromLTRB(0f,0f,10f,10f), Paint.fill(Color.WHITE)); c.restore(); assertTrue(b.ops().any { it is DisplayOp.EndLayer }) }
    @Test fun `Canvas translate`() { val b = TestBuffer(); val c = Canvas(b); c.translate(10f, 20f); assertEquals(10f, c.matrix.transX); assertEquals(20f, c.matrix.transY) }
    @Test fun `Canvas draws bake transform`() { val b = TestBuffer(); val c = Canvas(b); c.translate(10f, 0f); c.drawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED)); assertEquals(10f, (b.ops().filterIsInstance<DisplayOp.DrawRect>().first()).transform.transX) }
    @Test fun `Canvas clipRect`() { val b = TestBuffer(); val c = Canvas(b); c.clipRect(Rect.fromLTRB(0f,0f,50f,50f)); assertEquals(Rect.fromLTRB(0f,0f,50f,50f), c.localClipBounds) }
    @Test fun `Canvas resetMatrix`() { val b = TestBuffer(); val c = Canvas(b); c.translate(100f, 200f); c.resetMatrix(); assertEquals(Matrix33.identity(), c.matrix) }
}
