package org.graphiks.kanvas.geometry

import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.vector.Vector2F32

import org.graphiks.kanvas.types.*
import org.graphiks.math.matrix.Matrix3x3F32
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
        assertEquals(
            listOf(
                PathCommand.Move(Point2F32(120f, 50f)),
                PathCommand.ArcTo(
                    radius = Vector2F32(20f, 40f),
                    xAxisRotation = 30f,
                    largeArc = true,
                    sweep = false,
                    endpoint = Point2F32(100f, 90f),
                ),
            ),
            transformed.commands(),
        )
    }

    @Test
    fun `Path transform canonicalizes arc ellipse under non uniform scale`() {
        val path = Path().apply {
            moveTo(10f, 0f)
            arcTo(10f, 10f, 45f, largeArc = false, sweep = true, x = 0f, y = 10f)
        }

        val transformed = path.transform(Matrix3x3F32.scaling(2f, 1f))
        val commands = transformed.commands()

        assertEquals(PathCommand.Move(Point2F32(20f, 0f)), commands[0])
        val arc = commands[1] as PathCommand.ArcTo
        assertEquals(20f, arc.radius.x, 0.001f)
        assertEquals(10f, arc.radius.y, 0.001f)
        assertEquals(0f, arc.xAxisRotation, 0.001f)
        assertTrue(arc.sweep)
        assertEquals(Point2F32(0f, 10f), arc.endpoint)
    }

    @Test
    fun `Path transform canonicalizes tiny arc ellipse under skew`() {
        val radius = 0.0005f
        val path = Path().apply {
            moveTo(radius, 0f)
            arcTo(radius, radius, 0f, largeArc = false, sweep = true, x = 0f, y = radius)
        }

        val transformed = path.transform(Matrix3x3F32.skewing(1f, 0f))
        val arc = transformed.commands()[1] as PathCommand.ArcTo

        assertEquals(0.000809f, arc.radius.x, 0.000001f)
        assertEquals(0.000309f, arc.radius.y, 0.000001f)
        assertEquals(31.717f, arc.xAxisRotation, 0.001f)
        assertTrue(arc.sweep)
    }

    @Test
    fun `Path transform flips arc sweep when matrix mirrors winding`() {
        val path = Path().apply {
            moveTo(5f, 0f)
            arcTo(5f, 8f, 15f, largeArc = true, sweep = true, x = 4f, y = 6f)
        }

        val transformed = path.transform(Matrix3x3F32.scaling(-1f, 1f))
        assertEquals(PathCommand.Move(Point2F32(-5f, 0f)), transformed.commands()[0])
        val arc = transformed.commands()[1] as PathCommand.ArcTo
        assertEquals(Vector2F32(5f, 8f), arc.radius)
        assertTrue(arc.largeArc)
        assertTrue(!arc.sweep)
        assertEquals(Point2F32(-4f, 6f), arc.endpoint)
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
