package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScaffoldCanvasTest {
    private fun dummyImage() = Image(100, 100, sourceId = "test-img")
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

    @Test
    fun `drawImageNine emits DrawImageNine op`() {
        val (canvas, buffer) = testCanvas()
        val img = dummyImage()
        val center = Rect.fromLTRB(30f, 30f, 70f, 70f)
        val dst = Rect.fromLTRB(0f, 0f, 200f, 200f)
        canvas.drawImageNine(img, center, dst)
        val op = buffer.ops().first() as DisplayOp.DrawImageNine
        assertEquals(img, op.image)
        assertEquals(center, op.center)
        assertEquals(dst, op.dst)
    }

    @Test
    fun `drawImageNine with paint emits op with paint`() {
        val (canvas, buffer) = testCanvas()
        val img = dummyImage()
        canvas.drawImageNine(img, Rect.fromLTRB(30f, 30f, 70f, 70f), Rect.fromLTRB(0f, 0f, 200f, 200f), Paint.fill(Color.BLUE))
        val op = buffer.ops().first() as DisplayOp.DrawImageNine
        assertNotNull(op.paint)
    }

    @Test
    fun `drawImageNine with empty center rect still emits op`() {
        val (canvas, buffer) = testCanvas()
        val img = dummyImage()
        val empty = Rect.fromLTRB(0f, 0f, 0f, 0f)
        canvas.drawImageNine(img, empty, Rect.fromLTRB(0f, 0f, 200f, 200f))
        val op = buffer.ops().first() as DisplayOp.DrawImageNine
        assertEquals(0f, op.center.width)
        assertEquals(0f, op.center.height)
    }

    @Test
    fun `drawImageLattice emits DrawImageLattice op`() {
        val (canvas, buffer) = testCanvas()
        val img = dummyImage()
        val lattice = Lattice(xDivs = listOf(25, 75), yDivs = listOf(25, 75))
        val dst = Rect.fromLTRB(0f, 0f, 200f, 200f)
        canvas.drawImageLattice(img, lattice, dst)
        val op = buffer.ops().first() as DisplayOp.DrawImageLattice
        assertEquals(img, op.image)
        assertEquals(listOf(25, 75), op.lattice.xDivs)
        assertEquals(listOf(25, 75), op.lattice.yDivs)
        assertEquals(dst, op.dst)
    }

    @Test
    fun `drawVertices emits DrawVertices op`() {
        val (canvas, buffer) = testCanvas()
        val positions = listOf(Point(0f, 0f), Point(100f, 0f), Point(50f, 100f))
        val vertices = Vertices(VertexMode.TRIANGLES, positions)
        canvas.drawVertices(vertices, Paint.fill(Color.RED))
        val op = buffer.ops().first() as DisplayOp.DrawVertices
        assertEquals(VertexMode.TRIANGLES, op.vertices.mode)
        assertEquals(3, op.vertices.positions.size)
    }

    @Test
    fun `drawVertices with colors and indices emits DrawVertices op`() {
        val (canvas, buffer) = testCanvas()
        val positions = listOf(Point(0f, 0f), Point(100f, 0f), Point(50f, 100f))
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val indices = listOf(0, 1, 2)
        val vertices = Vertices(VertexMode.TRIANGLES, positions, colors = colors, indices = indices)
        canvas.drawVertices(vertices, Paint.fill(Color.WHITE))
        val op = buffer.ops().first() as DisplayOp.DrawVertices
        assertEquals(3, op.vertices.colors?.size)
        assertEquals(3, op.vertices.indices?.size)
    }

    @Test
    fun `drawAtlas emits DrawAtlas op`() {
        val (canvas, buffer) = testCanvas()
        val atlas = dummyImage()
        val transforms = listOf(Matrix33.identity(), Matrix33.translate(100f, 0f))
        val texRects = listOf(Rect.fromLTRB(0f, 0f, 50f, 50f), Rect.fromLTRB(50f, 0f, 100f, 50f))
        canvas.drawAtlas(atlas, transforms, texRects)
        val op = buffer.ops().first() as DisplayOp.DrawAtlas
        assertEquals(atlas, op.atlas)
        assertEquals(2, op.transforms.size)
        assertEquals(2, op.texRects.size)
    }

    @Test
    fun `drawAtlas with empty lists emits op with empty lists`() {
        val (canvas, buffer) = testCanvas()
        canvas.drawAtlas(dummyImage(), emptyList(), emptyList())
        val op = buffer.ops().first() as DisplayOp.DrawAtlas
        assertTrue(op.transforms.isEmpty())
        assertTrue(op.texRects.isEmpty())
    }

    @Test
    fun `drawAtlas with colors and blendMode emits op`() {
        val (canvas, buffer) = testCanvas()
        val atlas = dummyImage()
        val transforms = listOf(Matrix33.identity())
        val texRects = listOf(Rect.fromLTRB(0f, 0f, 50f, 50f))
        val colors = listOf(Color.RED)
        canvas.drawAtlas(atlas, transforms, texRects, colors, BlendMode.SRC_IN)
        val op = buffer.ops().first() as DisplayOp.DrawAtlas
        assertEquals(BlendMode.SRC_IN, op.blendMode)
        assertEquals(1, op.colors?.size)
    }

    @Test
    fun `drawPatch emits DrawVertices op`() {
        val (canvas, buffer) = testCanvas()
        val cubics = listOf(
            Point(0f, 0f), Point(33f, 0f), Point(66f, 0f), Point(100f, 0f),
            Point(100f, 33f), Point(100f, 66f), Point(100f, 100f), Point(100f, 100f),
            Point(66f, 100f), Point(33f, 100f), Point(0f, 100f), Point(0f, 100f),
        )
        canvas.drawPatch(cubics, paint = Paint.fill(Color.GREEN))
        val op = buffer.ops().first() as DisplayOp.DrawVertices
        assertEquals(VertexMode.TRIANGLES, op.vertices.mode)
        assertTrue(op.vertices.indices?.isNotEmpty() == true)
    }

    @Test
    fun `drawPatch with colors emits DrawVertices`() {
        val (canvas, buffer) = testCanvas()
        val cubics = listOf(
            Point(0f, 0f), Point(33f, 0f), Point(66f, 0f), Point(100f, 0f),
            Point(100f, 33f), Point(100f, 66f), Point(100f, 100f), Point(100f, 100f),
            Point(66f, 100f), Point(33f, 100f), Point(0f, 100f), Point(0f, 100f),
        )
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        canvas.drawPatch(cubics, colors = colors, paint = Paint.fill(Color.GREEN))
        val op = buffer.ops().first() as DisplayOp.DrawVertices
        assertEquals(25, op.vertices.colors?.size)
    }

    @Test
    fun `drawPatch with fewer than 12 points throws exception`() {
        val (canvas, _) = testCanvas()
        val short = listOf(Point(0f, 0f), Point(100f, 0f), Point(100f, 100f), Point(0f, 100f))
        assertFailsWith<IndexOutOfBoundsException> {
            canvas.drawPatch(short, paint = Paint.fill(Color.RED))
        }
    }

    @Test
    fun `drawAnnotation emits Annotation op`() {
        val (canvas, buffer) = testCanvas()
        val rect = Rect.fromLTRB(0f, 0f, 100f, 100f)
        canvas.drawAnnotation(rect, "author", "test-user")
        val op = buffer.ops().first() as DisplayOp.Annotation
        assertEquals(rect, op.rect)
        assertEquals("author", op.key)
        assertEquals("test-user", op.value)
    }

    @Test
    fun `drawAnnotation with empty strings emits op`() {
        val (canvas, buffer) = testCanvas()
        canvas.drawAnnotation(Rect.fromLTRB(0f, 0f, 0f, 0f), "", "")
        val op = buffer.ops().first() as DisplayOp.Annotation
        assertEquals("", op.key)
        assertEquals("", op.value)
    }
}

private class TestDisplayListBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
