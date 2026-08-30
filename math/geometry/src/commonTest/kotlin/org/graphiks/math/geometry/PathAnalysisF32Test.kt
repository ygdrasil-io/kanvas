package org.graphiks.math.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
    fun `bounds include rotated elliptical arc extrema`() {
        val radiusX = 9f
        val radiusY = 4f
        val rotationDegrees = 30f
        val rotationRadians = rotationDegrees.toDouble() * PI / 180.0
        val startX = (radiusX * cos(rotationRadians)).toFloat()
        val startY = (radiusX * sin(rotationRadians)).toFloat()
        val path = PathBuilder()
            .moveTo(startX, startY)
            .arcTo(radiusX, radiusY, rotationDegrees, false, true, -startX, -startY)
            .arcTo(radiusX, radiusY, rotationDegrees, false, true, startX, startY)
            .build()

        val bounds = requireNotNull(PathAnalysisF32.bounds(path))
        val expectedX = sqrt((radiusX * cos(rotationRadians)).pow(2) + (radiusY * sin(rotationRadians)).pow(2))
        val expectedY = sqrt((radiusX * sin(rotationRadians)).pow(2) + (radiusY * cos(rotationRadians)).pow(2))

        assertTrue(abs(bounds.left + expectedX) < 1e-3)
        assertTrue(abs(bounds.right - expectedX) < 1e-3)
        assertTrue(abs(bounds.top + expectedY) < 1e-3)
        assertTrue(abs(bounds.bottom - expectedY) < 1e-3)
    }

    @Test
    fun `close resets the current point before a following quadratic`() {
        val path = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(20f, 0f)
            .close()
            .quadTo(-100f, -100f, 0f, 0f)
            .build()

        val bounds = requireNotNull(PathAnalysisF32.bounds(path))

        assertEquals(-50f, bounds.left)
        assertEquals(-50f, bounds.top)
    }

    @Test
    fun `bounds retain finite quadratic and cubic extrema near Float maximum`() {
        val maximum = Float.MAX_VALUE
        val quadratic = PathBuilder().moveTo(maximum, 0f).quadTo(-maximum, 0f, maximum, 0f).build()
        val cubic = PathBuilder().moveTo(maximum, 0f).cubicTo(-maximum, 0f, -maximum, 0f, maximum, 0f).build()

        assertEquals(0f, requireNotNull(PathAnalysisF32.bounds(quadratic)).left)
        assertEquals(-maximum / 2f, requireNotNull(PathAnalysisF32.bounds(cubic)).left)
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
    fun `contains keeps every boundary outside and closes fills implicitly`() {
        val rect = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
        listOf(Point2F32(0f, 5f), Point2F32(10f, 5f), Point2F32(5f, 0f), Point2F32(5f, 10f))
            .forEach { assertFalse(PathAnalysisF32.contains(rect, it)) }

        val openTriangle = PathBuilder().moveTo(10f, 0f).lineTo(0f, 0f).lineTo(0f, 10f).build()
        assertTrue(PathAnalysisF32.contains(openTriangle, Point2F32(8f, 1f)))
        assertEquals(0, PathAnalysisF32.topology(openTriangle).closedContourCount)

        val inverse = PathBuilder(FillRule.INVERSE_WINDING).addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
        assertFalse(PathAnalysisF32.contains(inverse, Point2F32(0f, 5f)))
    }

    @Test
    fun `contains keeps a non dyadic quadratic boundary outside for normal and inverse fill`() {
        fun path(fillRule: FillRule): PathF32 = PathBuilder(fillRule)
            .moveTo(0f, 0f)
            .quadTo(0f, 9f, 9f, 0f)
            .close()
            .build()
        val boundary = Point2F32(1f, 4f)

        assertFalse(PathAnalysisF32.contains(path(FillRule.WINDING), boundary))
        assertFalse(PathAnalysisF32.contains(path(FillRule.INVERSE_WINDING), boundary))
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
    fun `shape recognizers reject retraced and noncanonical geometry`() {
        val retraced = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).lineTo(0f, 0f).lineTo(10f, 0f).close().build()
        val noncanonicalOval = PathBuilder()
            .moveTo(10f, 5f)
            .cubicTo(10f, 8f, 8f, 10f, 5f, 10f)
            .cubicTo(2f, 10f, 0f, 8f, 0f, 5f)
            .cubicTo(0f, 2f, 2f, 0f, 5f, 0f)
            .cubicTo(8f, 0f, 10f, 2f, 10f, 5f)
            .close()
            .build()
        val wrongArc = PathBuilder()
            .moveTo(2f, 0f).lineTo(8f, 0f)
            .arcTo(2f, 2f, 20f, false, false, 10f, 2f)
            .lineTo(10f, 8f).arcTo(2f, 2f, 0f, false, true, 8f, 10f)
            .lineTo(2f, 10f).arcTo(2f, 2f, 0f, false, true, 0f, 8f)
            .lineTo(0f, 2f).arcTo(2f, 2f, 0f, false, true, 2f, 0f)
            .close()
            .build()

        assertNull(PathAnalysisF32.rect(retraced))
        assertNull(PathAnalysisF32.oval(noncanonicalOval))
        assertNull(PathAnalysisF32.rrect(wrongArc))
    }

    @Test
    fun `rounded rectangle recognizer rejects overlapping raw corner radii`() {
        val overlapping = PathBuilder()
            .moveTo(8f, 0f).lineTo(2f, 0f)
            .arcTo(8f, 8f, 0f, false, true, 10f, 8f)
            .lineTo(10f, 2f).arcTo(8f, 8f, 0f, false, true, 2f, 10f)
            .lineTo(8f, 10f).arcTo(8f, 8f, 0f, false, true, 0f, 2f)
            .lineTo(0f, 8f).arcTo(8f, 8f, 0f, false, true, 8f, 0f)
            .close()
            .build()

        assertNull(PathAnalysisF32.rrect(overlapping))
    }

    @Test
    fun `topology keeps a nonzero orientation through product cancellation`() {
        val epsilon = 2.0.pow(-23).toFloat()
        val path = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(1f + epsilon, 1f + 2f * epsilon)
            .lineTo(1f, 1f + epsilon)
            .close()
            .build()

        assertEquals(ContourOrientation.CLOCKWISE, PathAnalysisF32.topology(path).orientation)
    }

    @Test
    fun `convexity keeps nearly collinear turns deterministic`() {
        val path = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(1e20f, 1e20f + 3e13f)
            .lineTo(2e20f, 2e20f)
            .lineTo(2e20f, 0f)
            .close()
            .build()

        assertTrue(PathAnalysisF32.isConvex(path))
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
            PathTopologyI32(2, 2, ContourOrientation.CLOCKWISE, inverseFill = false),
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
            PathTopologyI32(2, 2, ContourOrientation.MIXED, inverseFill = true),
            PathAnalysisF32.topology(mixed),
        )
    }
}
