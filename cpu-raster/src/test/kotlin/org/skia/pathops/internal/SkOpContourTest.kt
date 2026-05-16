package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkOpContour] / [SkOpContourHead] / [SkOpContourBuilder]
 * (Phase D1.2.e).
 */
class SkOpContourTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    // ─── SkOpContour ───────────────────────────────────────────────

    @Test
    fun `default contour has count 0 and null tail`() {
        val c = SkOpContour()
        assertEquals(0, c.count())
        assertNull(c.fTail)
        assertNull(c.next())
        assertFalse(c.done())
        assertFalse(c.operand())
        assertFalse(c.isXor())
    }

    @Test
    fun `init sets operand and xor flags`() {
        val c = SkOpContour()
        c.init(operand = true, isXor = true)
        assertTrue(c.operand())
        assertTrue(c.isXor())
    }

    @Test
    fun `addLine populates head segment on first call`() {
        val c = SkOpContour()
        val seg = c.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        assertEquals(1, c.count())
        assertSame(c.fHead, seg)
        assertSame(seg, c.fTail)
        assertSame(c, seg.contour())
    }

    @Test
    fun `addLine on second call allocates a fresh segment and links it`() {
        val c = SkOpContour()
        val seg1 = c.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        val seg2 = c.addLine(arrayOf(pt(10f, 0f), pt(10f, 10f)))
        assertEquals(2, c.count())
        assertSame(c.fHead, seg1)
        assertSame(seg2, c.fTail)
        assertSame(seg2, seg1.next())
        assertSame(seg1, seg2.prev())
    }

    @Test
    fun `addQuad addCubic addConic populate the segment with the right verb`() {
        val c = SkOpContour()
        c.addQuad(arrayOf(pt(0f, 0f), pt(5f, 10f), pt(10f, 0f)))
        c.addCubic(arrayOf(pt(10f, 0f), pt(10f, 10f), pt(20f, 10f), pt(20f, 0f)))
        c.addConic(arrayOf(pt(20f, 0f), pt(25f, 10f), pt(30f, 0f)), 0.7071f)
        assertEquals(3, c.count())
    }

    @Test
    fun `setBounds unions all segment bounds`() {
        val c = SkOpContour()
        c.addLine(arrayOf(pt(0f, 0f), pt(10f, 5f)))
        c.addLine(arrayOf(pt(10f, 5f), pt(0f, -5f)))
        c.setBounds()
        val b = c.bounds()
        assertEquals(0f, b.left)
        assertEquals(-5f, b.top)
        assertEquals(10f, b.right)
        assertEquals(5f, b.bottom)
    }

    @Test
    fun `complete is alias for setBounds`() {
        val c = SkOpContour()
        c.addLine(arrayOf(pt(0f, 0f), pt(10f, 5f)))
        c.complete()
        assertEquals(10f, c.bounds().right)
    }

    @Test
    fun `joinSegments forms a closed loop via tail to head opp links`() {
        val c = SkOpContour()
        val s1 = c.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        val s2 = c.addLine(arrayOf(pt(10f, 0f), pt(10f, 10f)))
        val s3 = c.addLine(arrayOf(pt(10f, 10f), pt(0f, 0f)))
        c.joinSegments()
        // s1.tail.ptT.next now points at s2.head.ptT.
        assertSame(s2.head().ptT(), s1.tail().ptT().next())
        // s3.tail.ptT.next now points at s1.head.ptT (loops back).
        assertSame(s1.head().ptT(), s3.tail().ptT().next())
    }

    @Test
    fun `start and end return first and last control points`() {
        val c = SkOpContour()
        c.addLine(arrayOf(pt(1f, 2f), pt(10f, 0f)))
        c.addLine(arrayOf(pt(10f, 0f), pt(15f, 17f)))
        assertEquals(pt(1f, 2f), c.start())
        assertEquals(pt(15f, 17f), c.end())
    }

    @Test
    fun `addLine rejects coincident endpoints`() {
        val c = SkOpContour()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            c.addLine(arrayOf(pt(5f, 5f), pt(5f, 5f)))
        }
    }

    @Test
    fun `setReverse and resetReverse roundtrip`() {
        val c = SkOpContour()
        c.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)))
        c.setReverse()
        assertTrue(c.reversed())
        c.resetReverse()
        assertFalse(c.reversed())
    }

    @Test
    fun `compareTo orders by bounds top then left`() {
        val a = SkOpContour(); a.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f))); a.complete()
        val b = SkOpContour(); b.addLine(arrayOf(pt(5f, 5f), pt(15f, 5f))); b.complete()
        val c = SkOpContour(); c.addLine(arrayOf(pt(2f, 0f), pt(12f, 0f))); c.complete()
        // a.top=0, c.top=0, b.top=5 → a < c (tied top, a.left=0 < c.left=2) ; both < b.
        assertTrue(a < b); assertTrue(c < b); assertTrue(a < c)
    }

    // ─── SkOpContourHead ───────────────────────────────────────────

    @Test
    fun `appendContour links a fresh contour at the tail`() {
        val head = SkOpContourHead()
        val a = head.appendContour()
        val b = head.appendContour()
        assertSame(a, head.next())
        assertSame(b, a.next())
        assertNull(b.next())
    }

    @Test
    fun `joinAllSegments walks every contour and joins its segments`() {
        val head = SkOpContourHead()
        head.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        head.addLine(arrayOf(pt(10f, 0f), pt(10f, 10f)))
        val c2 = head.appendContour()
        c2.addLine(arrayOf(pt(20f, 0f), pt(30f, 0f)))
        c2.addLine(arrayOf(pt(30f, 0f), pt(30f, 10f)))
        head.joinAllSegments()
        // Both contours should have their segments joined ; spot-check one.
        val s = head.first()
        assertSame(s.next()!!.head().ptT(), s.tail().ptT().next())
    }

    @Test
    fun `remove unlinks the tail contour`() {
        val head = SkOpContourHead()
        val a = head.appendContour()
        val b = head.appendContour()
        head.remove(b)
        assertSame(a, head.next())
        assertNull(a.next())
    }

    // ─── SkOpContourBuilder ────────────────────────────────────────

    @Test
    fun `builder addLine flushes to the contour on flush`() {
        val c = SkOpContour()
        val b = SkOpContourBuilder(c)
        b.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        // Buffered — no segment yet.
        assertEquals(0, c.count())
        b.flush()
        assertEquals(1, c.count())
    }

    @Test
    fun `builder cancels exact-opposite consecutive lines`() {
        val c = SkOpContour()
        val b = SkOpContourBuilder(c)
        b.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        b.addLine(arrayOf(pt(10f, 0f), pt(0f, 0f))) // opposite — cancels both
        b.flush()
        assertEquals(0, c.count())
    }

    @Test
    fun `builder addQuad flushes any buffered line first`() {
        val c = SkOpContour()
        val b = SkOpContourBuilder(c)
        b.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)))
        b.addQuad(arrayOf(pt(10f, 0f), pt(15f, 10f), pt(20f, 0f)))
        // Both should now be in the contour : 1 line + 1 quad = 2 segments.
        assertEquals(2, c.count())
    }

    @Test
    fun `builder addCurve dispatches by verb`() {
        val c = SkOpContour()
        val b = SkOpContourBuilder(c)
        b.addCurve(SkOpSegment.SegVerb.kCubic, arrayOf(
            pt(0f, 0f), pt(0f, 10f), pt(10f, 10f), pt(10f, 0f),
        ))
        b.flush()
        assertEquals(1, c.count())
    }

    @Test
    fun `setContour flushes pending line then switches`() {
        val c1 = SkOpContour()
        val c2 = SkOpContour()
        val b = SkOpContourBuilder(c1)
        b.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f))) // buffered into c1's queue
        b.setContour(c2)
        // Before switch, the pending line was flushed to c1.
        assertEquals(1, c1.count())
        assertEquals(0, c2.count())
        assertSame(c2, b.contour())
    }
}
