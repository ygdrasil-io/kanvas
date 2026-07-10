package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PathTest {
    @Test
    fun `Path moveTo lineTo close`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(50f, 100f)
            close()
        }
        assertEquals(FillType.WINDING, path.fillType)
    }

    @Test
    fun `Path DSL construction`() {
        val path = Path {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            close()
        }
        assertEquals(FillType.WINDING, path.fillType)
    }

    @Test
    fun `Path addRect`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 80f))
        assertTrue(path.verbs().size >= 5) // 4 lines + close
    }

    @Test
    fun `Path addOval`() {
        val path = Path().addOval(Rect.fromLTRB(0f, 0f, 100f, 80f))
        assertTrue(path.verbs().size >= 5) // 4 cubics + close
    }

    @Test
    fun `Path addCircle`() {
        val path = Path().addCircle(50f, 50f, 30f)
        assertTrue(path.verbs().size >= 5) // 4 cubics + close
    }

    @Test
    fun `Path addRRect`() {
        val path = Path().addRRect(RRect(Rect.fromLTRB(0f, 0f, 100f, 80f), 10f))
        assertTrue(path.verbs().size >= 5) // multiple lines + arcs + close
    }

    @Test
    fun `Path transform`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        val moved = path.transform(5f, 5f, 1f, 1f)
        assertTrue(moved is Path)
    }

    @Test
    fun `Path transform preserves arc metadata while transforming endpoint`() {
        val path = Path().apply {
            moveTo(10f, 0f)
            arcTo(10f, 20f, 30f, largeArc = true, sweep = false, x = 0f, y = 20f)
        }

        val transformed = path.transform(100f, 50f, 2f, 2f)
        val points = transformed.points()

        assertEquals(Point(120f, 50f), points[0])
        assertEquals(Point(20f, 40f), points[1])
        assertEquals(Point(30f, 1f), points[2])
        assertEquals(Point(0f, 0f), points[3])
        assertEquals(Point(100f, 90f), points[4])
    }

    @Test
    fun `Path transform canonicalizes arc ellipse under non uniform scale`() {
        val path = Path().apply {
            moveTo(10f, 0f)
            arcTo(10f, 10f, 45f, largeArc = false, sweep = true, x = 0f, y = 10f)
        }

        val transformed = path.transform(Matrix33.scale(2f, 1f))
        val points = transformed.points()

        assertEquals(Point(20f, 0f), points[0])
        assertEquals(20f, points[1].x, 0.001f)
        assertEquals(10f, points[1].y, 0.001f)
        assertEquals(0f, points[2].x, 0.001f)
        assertEquals(0f, points[2].y)
        assertEquals(Point(1f, 0f), points[3])
        assertEquals(Point(0f, 10f), points[4])
    }

    @Test
    fun `Path transform canonicalizes tiny arc ellipse under skew`() {
        val radius = 0.0005f
        val path = Path().apply {
            moveTo(radius, 0f)
            arcTo(radius, radius, 0f, largeArc = false, sweep = true, x = 0f, y = radius)
        }

        val transformed = path.transform(Matrix33.skew(1f, 0f))
        val points = transformed.points()

        assertEquals(0.000809f, points[1].x, 0.000001f)
        assertEquals(0.000309f, points[1].y, 0.000001f)
        assertEquals(31.717f, points[2].x, 0.001f)
        assertEquals(Point(1f, 0f), points[3])
    }

    @Test
    fun `Path transform flips arc sweep when matrix mirrors winding`() {
        val path = Path().apply {
            moveTo(5f, 0f)
            arcTo(5f, 8f, 15f, largeArc = true, sweep = true, x = 4f, y = 6f)
        }

        val transformed = path.transform(Matrix33.scale(-1f, 1f))
        val points = transformed.points()

        assertEquals(Point(-5f, 0f), points[0])
        assertEquals(Point(5f, 8f), points[1])
        assertEquals(1f, points[2].y)
        assertEquals(Point(0f, 0f), points[3])
        assertEquals(Point(-4f, 6f), points[4])
    }

    @Test
    fun `Path internal verbs access`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 10f)
        }
        assertEquals(2, path.verbs().size)
    }
}
