package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkPoint

/**
 * Unit tests for [SkOpEdgeBuilder] (Phase D1.2.f).
 */
class SkOpEdgeBuilderTest {

    // ─── force_small_to_zero ───────────────────────────────────────

    @Test
    fun `forceSmallToZero clamps tiny x and y to 0`() {
        val tiny = SkPoint(fX = 1e-7f, fY = 1e-7f) // < FLT_EPSILON_ORDERABLE_ERR (1.9e-6f)
        val r = SkOpEdgeBuilder.forceSmallToZero(tiny)
        assertEquals(0f, r.fX); assertEquals(0f, r.fY)
    }

    @Test
    fun `forceSmallToZero leaves larger values intact`() {
        val r = SkOpEdgeBuilder.forceSmallToZero(SkPoint(fX = 1f, fY = 2f))
        assertEquals(1f, r.fX); assertEquals(2f, r.fY)
    }

    // ─── PathOpsMask constants ─────────────────────────────────────

    @Test
    fun `SkPathOpsMask constants match upstream values`() {
        assertEquals(-1, SkPathOpsMask.kWinding)
        assertEquals(0, SkPathOpsMask.kNo)
        assertEquals(1, SkPathOpsMask.kEvenOdd)
    }

    // ─── Construction + xorMask ────────────────────────────────────

    @Test
    fun `constructor reads winding fill type into both xor masks`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).close().detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertEquals(SkPathOpsMask.kWinding, builder.xorMask())
    }

    @Test
    fun `constructor reads even-odd fill type into both xor masks`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).close().detach()
            .makeFillType(SkPathFillType.kEvenOdd)
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertEquals(SkPathOpsMask.kEvenOdd, builder.xorMask())
    }

    // ─── finish() on a simple line-only contour ────────────────────

    @Test
    fun `finish builds a simple closed triangle as 3 line segments`() {
        // Triangle : (0,0) → (10,0) → (5,10) → close.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(5f, 10f)
            .close()
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertTrue(builder.finish())
        // The head contour should now have 3 line segments.
        assertEquals(3, head.count())
        assertEquals(SkOpSegment.SegVerb.kLine, head.first().verb())
    }

    @Test
    fun `finish auto-closes an unclosed line-only contour`() {
        // Same triangle but without explicit close.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(5f, 10f)
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertTrue(builder.finish())
        // closeContour adds the implicit closing line back to (0, 0) → 3 line segments.
        assertEquals(3, head.count())
    }

    @Test
    fun `finish with allowOpenContours skips the implicit close`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(5f, 10f)
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head, fAllowOpenContours = true)
        assertTrue(builder.finish())
        assertEquals(2, head.count()) // no implicit close — 2 line segments
    }

    @Test
    fun `finish on a quad-only contour produces a quad segment`() {
        // moveTo(0, 0), quadTo(50, 100, 100, 0), close.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(50f, 100f, 100f, 0f)
            .close()
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertTrue(builder.finish())
        // 1 quad + 1 implicit closing line = 2 segments.
        assertEquals(2, head.count())
        assertEquals(SkOpSegment.SegVerb.kQuad, head.first().verb())
        assertEquals(SkOpSegment.SegVerb.kLine, head.first().next()!!.verb())
    }

    @Test
    fun `finish on a cubic-only contour produces a cubic segment`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, 10f, 10f, 10f, 10f, 0f)
            .close()
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertTrue(builder.finish())
        assertEquals(2, head.count())
        assertEquals(SkOpSegment.SegVerb.kCubic, head.first().verb())
    }

    @Test
    fun `finish skips degenerate zero-length lines mid-contour`() {
        // Triangle with a degenerate line in the middle ; preFetch
        // should drop the degenerate one and produce a 3-segment triangle.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 0f) // degenerate — should be skipped
            .lineTo(5f, 10f)
            .close()
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertTrue(builder.finish())
        // 3 unique line segments (degenerate removed, closing line back to (0,0)).
        assertEquals(3, head.count())
    }

    @Test
    fun `finish on non-finite path returns false and sets unparseable`() {
        val path = SkPathBuilder()
            .moveTo(Float.POSITIVE_INFINITY, 0f)
            .lineTo(10f, 0f)
            .close()
            .detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(path, head)
        assertFalse(builder.finish())
        assertTrue(builder.fUnparseable)
    }

    // ─── addOperand for binary ops ─────────────────────────────────

    @Test
    fun `addOperand appends a second path's verbs as a separate contour`() {
        // Use real triangles so the auto-closing line doesn't cancel the
        // forward line (single-line "closed" contours collapse to 0
        // segments — they have zero enclosed area, which matches upstream).
        val subject = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(5f, 10f).close().detach()
        val operand = SkPathBuilder()
            .moveTo(0f, 20f).lineTo(10f, 20f).lineTo(5f, 30f).close().detach()
        val head = SkOpContourHead()
        val builder = SkOpEdgeBuilder(subject, head)
        builder.addOperand(operand)
        assertTrue(builder.finish())
        assertEquals(3, head.count())              // subject triangle
        assertEquals(3, head.next()?.count() ?: 0) // operand triangle
    }

    // ─── head() accessor ───────────────────────────────────────────

    @Test
    fun `head returns the same SkOpContourHead passed in the constructor`() {
        val head = SkOpContourHead()
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).close().detach()
        val builder = SkOpEdgeBuilder(path, head)
        assertSame(head, builder.head())
    }
}
