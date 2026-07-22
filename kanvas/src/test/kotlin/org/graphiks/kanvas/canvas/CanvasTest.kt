package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertIs

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
    @Test
    fun `clip geometry is frozen when captured not when a later draw occurs`() {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        canvas.translate(10f, 5f)
        canvas.clipRect(Rect.fromXYWH(0f, 0f, 4f, 6f), antiAlias = false)
        canvas.translate(100f, 100f)

        val clip = buffer.ops().filterIsInstance<DisplayOp.SetClip>().last().clip
        assertEquals(
            Rect.fromXYWH(10f, 5f, 4f, 6f),
            assertIs<ClipStack.DeviceRect>(clip).rect,
        )
    }

    @Test
    fun `rotated clip rect is captured as a device path`() {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        canvas.rotate(45f)
        canvas.clipRect(Rect(2f, 2f, 10f, 10f), antiAlias = true)

        val clip = buffer.ops().filterIsInstance<DisplayOp.SetClip>().single().clip
        val element = assertIs<ClipStack.Complex>(clip).ops.single()
        assertIs<ClipStackOp.PathOp>(element)
    }

    @Test
    fun `saveLayer defers outer clip while nested layer records only inner clip`() {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        val outer = Rect(2f, 2f, 14f, 14f)
        val inner = Rect(4f, 4f, 12f, 12f)
        val draw = Rect(0f, 0f, 16f, 16f)

        canvas.clipRect(outer, antiAlias = false)
        canvas.saveLayer()
        // Canvas queries retain the semantic outer clip while the first layer child is unclipped.
        assertEquals(outer, canvas.localClipBounds)
        canvas.clipRect(inner, antiAlias = false)
        canvas.drawRect(draw, Paint.fill(Color.RED))
        canvas.saveLayer()
        canvas.drawRect(draw, Paint.fill(Color.GREEN))
        canvas.restore()
        canvas.drawRect(draw, Paint.fill(Color.BLUE))
        canvas.restore()
        canvas.drawRect(draw, Paint.fill(Color.WHITE))

        val begins = buffer.ops().filterIsInstance<DisplayOp.BeginLayer>()
        assertEquals(outer, assertIs<ClipStack.DeviceRect>(begins[0].rec.compositeClip).rect)
        assertEquals(inner, assertIs<ClipStack.DeviceRect>(begins[1].rec.compositeClip).rect)

        val draws = buffer.ops().filterIsInstance<DisplayOp.DrawRect>()
        assertEquals(inner, assertIs<ClipStack.DeviceRect>(draws[0].clip).rect)
        assertEquals(ClipStack.WideOpen, draws[1].clip)
        assertEquals(inner, assertIs<ClipStack.DeviceRect>(draws[2].clip).rect)
        assertEquals(outer, assertIs<ClipStack.DeviceRect>(draws[3].clip).rect)
    }

    @Test
    fun `save restore freezes the ordered clip snapshot on each draw`() {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        val outer = Rect(1.25f, 2.5f, 15.75f, 16.5f)
        val inner = RRect(Rect(3f, 4f, 13f, 14f), radius = 2f)
        val draw = Rect(0f, 0f, 20f, 20f)

        canvas.clipRect(outer, antiAlias = true)
        canvas.save()
        canvas.clipRRect(inner, antiAlias = false)
        canvas.drawRect(draw, Paint.fill(Color.RED))
        canvas.restore()
        canvas.drawRect(draw, Paint.fill(Color.BLUE))

        val draws = buffer.ops().filterIsInstance<DisplayOp.DrawRect>()
        val nested = assertIs<ClipStack.Complex>(draws[0].clip)
        assertEquals(2, nested.ops.size)
        assertIs<ClipStackOp.RectOp>(nested.ops[0])
        assertIs<ClipStackOp.RRectOp>(nested.ops[1])
        assertEquals(outer, assertIs<ClipStack.DeviceRect>(draws[1].clip).rect)
        assertEquals(2, assertIs<ClipStack.Complex>(draws[0].clip).ops.size)
    }

    @Test fun `Canvas resetMatrix`() { val b = TestBuffer(); val c = Canvas(b); c.translate(100f, 200f); c.resetMatrix(); assertEquals(Matrix33.identity(), c.matrix) }
}
