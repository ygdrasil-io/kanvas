package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScaffoldCanvasTest {
    private fun testCanvas(): Pair<Canvas, TestDisplayListBuffer> {
        val buffer = TestDisplayListBuffer()
        return Canvas(buffer) to buffer
    }

    @Test
    fun `drawColor emits DrawColor op`() {
        val (canvas, buffer) = testCanvas()
        canvas.drawColor(Color.RED)
        val ops = buffer.ops()
        assertEquals(1, ops.size)
        assertTrue(ops[0] is DisplayOp.DrawColor)
    }

    @Test
    fun `clear emits Clear op`() {
        val (canvas, buffer) = testCanvas()
        canvas.clear(Color.WHITE)
        val ops = buffer.ops()
        assertEquals(1, ops.size)
        assertTrue(ops[0] is DisplayOp.Clear)
    }

    @Test
    fun `drawPoint emits DrawPoint op`() {
        val (canvas, buffer) = testCanvas()
        canvas.drawPoint(5f, 7f, Paint.fill(Color.BLUE))
        val op = buffer.ops().first() as DisplayOp.DrawPoint
        assertEquals(5f, op.x)
        assertEquals(7f, op.y)
    }

    @Test
    fun `drawPoints emits DrawPoints op`() {
        val (canvas, buffer) = testCanvas()
        canvas.drawPoints(PointMode.LINES, listOf(Point(0f, 0f), Point(10f, 10f)), Paint.fill(Color.RED))
        val op = buffer.ops().first() as DisplayOp.DrawPoints
        assertEquals(PointMode.LINES, op.mode)
        assertEquals(2, op.points.size)
    }

    @Test
    fun `drawDRRect emits DrawDRRect op`() {
        val (canvas, buffer) = testCanvas()
        val outer = RRect(Rect.fromLTRB(0f, 0f, 100f, 100f), CornerRadii(10f, 10f))
        val inner = RRect(Rect.fromLTRB(20f, 20f, 80f, 80f), CornerRadii(5f, 5f))
        canvas.drawDRRect(outer, inner, Paint.fill(Color.GREEN))
        val op = buffer.ops().first() as DisplayOp.DrawDRRect
        assertEquals(outer, op.outer)
        assertEquals(inner, op.inner)
    }

    @Test
    fun `isClipRect returns true for device rect clip`() {
        val (canvas, _) = testCanvas()
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 100f, 100f))
        assertTrue(canvas.isClipRect)
    }

    @Test
    fun `isClipEmpty returns false for WideOpen`() {
        val (canvas, _) = testCanvas()
        assertFalse(canvas.isClipEmpty)
    }

    @Test
    fun `quickReject returns false for WideOpen clip`() {
        val (canvas, _) = testCanvas()
        assertFalse(canvas.quickReject(Rect.fromLTRB(0f, 0f, 100f, 100f)))
    }

    @Test
    fun `quickReject returns true for rect fully outside device rect clip`() {
        val (canvas, _) = testCanvas()
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 50f, 50f))
        assertTrue(canvas.quickReject(Rect.fromLTRB(60f, 60f, 100f, 100f)))
    }

    @Test
    fun `quickReject returns false for overlapping rect`() {
        val (canvas, _) = testCanvas()
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 50f, 50f))
        assertFalse(canvas.quickReject(Rect.fromLTRB(25f, 25f, 100f, 100f)))
    }
}

private class TestDisplayListBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
