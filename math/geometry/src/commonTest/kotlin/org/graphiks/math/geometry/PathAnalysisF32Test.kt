package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathAnalysisF32Test {
    @Test
    fun `bounds are tight for quadratic and cubic extrema`() {
        val path = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(10f, 20f, 20f, 0f)
            .cubicTo(20f, -20f, 40f, -20f, 40f, 0f)
            .build()

        val bounds = requireNotNull(PathAnalysisF32.bounds(path))

        assertEquals(RectF32.ofLTRB(0f, -15f, 40f, 10f), bounds)
    }

    @Test
    fun `contains honours winding even odd and inverse fill`() {
        val winding = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
        val evenOdd = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .addRect(RectF32.ofLTRB(2f, 2f, 8f, 8f))
            .build()
        val inverse = PathBuilder(FillRule.INVERSE_WINDING)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .build()

        assertTrue(PathAnalysisF32.contains(winding, Point2F32(5f, 5f)))
        assertFalse(PathAnalysisF32.contains(winding, Point2F32(12f, 5f)))
        assertFalse(PathAnalysisF32.contains(evenOdd, Point2F32(5f, 5f)))
        assertTrue(PathAnalysisF32.contains(inverse, Point2F32(12f, 5f)))
        assertFalse(PathAnalysisF32.contains(inverse, Point2F32(5f, 5f)))
    }

    @Test
    fun `recognizes canonical shapes and line without mutable out parameters`() {
        val rect = RectF32.ofLTRB(1f, 2f, 9f, 8f)
        val rrect = RRectF32.of(rect, radius = 2f)

        assertEquals(rect, PathAnalysisF32.rect(PathBuilder().addRect(rect).build()))
        assertEquals(rect, PathAnalysisF32.oval(PathBuilder().addOval(rect).build()))
        assertEquals(rrect, PathAnalysisF32.rrect(PathBuilder().addRRect(rrect).build()))
        assertEquals(Line2F32(Point2F32(1f, 2f), Point2F32(9f, 8f)), PathAnalysisF32.line(PathBuilder().moveTo(1f, 2f).lineTo(9f, 8f).build()))
        assertNull(PathAnalysisF32.rect(PathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).build()))
    }

    @Test
    fun `convexity interpolation and topology report geometric behavior`() {
        val square = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
        val concave = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).lineTo(5f, 5f).lineTo(10f, 10f).lineTo(0f, 10f).close().build()
        val twoContours = PathBuilder().addPath(square).addPath(square).build()

        assertTrue(PathAnalysisF32.isConvex(square))
        assertFalse(PathAnalysisF32.isConvex(concave))
        assertTrue(PathAnalysisF32.isInterpolatable(square, PathBuilder().addRect(RectF32.ofLTRB(1f, 1f, 9f, 9f)).build()))
        assertFalse(PathAnalysisF32.isInterpolatable(square, PathBuilder().addOval(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()))
        assertEquals(
            PathTopology(2, 2, ContourOrientation.CLOCKWISE, inverseFill = false),
            PathAnalysisF32.topology(twoContours),
        )
    }

    @Test
    fun `topology identifies mixed orientations and inverse fill`() {
        val mixed = PathBuilder(FillRule.INVERSE_EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .moveTo(20f, 0f).lineTo(20f, 10f).lineTo(30f, 10f).lineTo(30f, 0f).close()
            .build()

        assertEquals(
            PathTopology(2, 2, ContourOrientation.MIXED, inverseFill = true),
            PathAnalysisF32.topology(mixed),
        )
    }
}
