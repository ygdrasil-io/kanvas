package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb as GpuPathVerb
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUPathStrokeInputTest {
    @Test
    fun `path tessellator conversion preserves arcTo as flattened line segments`() {
        val path = Path().apply {
            moveTo(1f, 0f)
            arcTo(1f, 1f, 0f, largeArc = false, sweep = true, x = 0f, y = 1f)
        }

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 64)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.size > 2)
        assertEquals(0f, flat.last().x, 1e-5f)
        assertEquals(1f, flat.last().y, 1e-5f)
    }

    @Test
    fun `large radius tiny sweep uses finite bounded intermediate points`() {
        val linePoints = Path().apply {
            moveTo(0f, 0f)
            arcTo(1_000_000f, 1_000_000f, 0f, largeArc = false, sweep = true, x = 10_000f, y = 0f)
        }.toPathTessellatorData().verbs
            .filterIsInstance<GpuPathVerb.LineTo>()
            .map { it.p }

        assertTrue(linePoints.size in 2..64)
        assertTrue(linePoints.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(linePoints.all { it.x in -1f..10_001f && it.y in -20f..1f })
        assertEquals(Point(10_000f, 0f), linePoints.last())
    }

    @Test
    fun `arc sweep direction selects opposite sides of the chord`() {
        fun intermediateY(sweep: Boolean): List<Float> = Path().apply {
            moveTo(0f, 0f)
            arcTo(10f, 10f, 0f, largeArc = false, sweep = sweep, x = 10f, y = 0f)
        }.toPathTessellatorData().verbs
            .filterIsInstance<GpuPathVerb.LineTo>()
            .dropLast(1)
            .map { it.p.y }

        val clockwise = intermediateY(sweep = true)
        val counterClockwise = intermediateY(sweep = false)

        assertTrue(clockwise.isNotEmpty() && clockwise.all { it < 0f })
        assertTrue(counterClockwise.isNotEmpty() && counterClockwise.all { it > 0f })
    }

    @Test
    fun `path tessellator conversion keeps degenerate arcs as endpoint lines`() {
        val zeroRadius = Path().apply {
            moveTo(1f, 2f)
            arcTo(0f, 1f, 0f, largeArc = false, sweep = true, x = 3f, y = 4f)
        }.toPathTessellatorData().verbs.last()
        val coincidentEndpoint = Path().apply {
            moveTo(1f, 2f)
            arcTo(1f, 1f, 0f, largeArc = false, sweep = true, x = 1f, y = 2f)
        }.toPathTessellatorData().verbs.last()

        assertEquals(GpuPathVerb.LineTo(Point(3f, 4f)), zeroRadius)
        assertEquals(GpuPathVerb.LineTo(Point(1f, 2f)), coincidentEndpoint)
    }

    @Test
    fun `rounded rect conversion keeps curved corners and closes the contour`() {
        val path = Path().addRRect(RRect(Rect.fromLTRB(0f, 0f, 10f, 8f), radius = 2f))

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 64)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.size > 9)
        assertEquals(flat.first(), flat.last())
    }

    @Test
    fun `rounded rect conversion clamps oversized radii inside rect bounds`() {
        val rect = Rect.fromLTRB(0f, 0f, 78f, 38f)
        val path = Path().addRRect(RRect(rect, radius = 400f))

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 128)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.isNotEmpty())
        assertTrue(flat.all { it.x in rect.left - 0.01f..rect.right + 0.01f })
        assertTrue(flat.all { it.y in rect.top - 0.01f..rect.bottom + 0.01f })
    }

    @Test
    fun `large sweep arc stays inside its ellipse bounds`() {
        val radius = 45f
        val sweepRad = 355.0 * PI / 180.0
        val path = Path().apply {
            moveTo(radius, 0f)
            arcTo(
                rx = radius,
                ry = radius,
                xAxisRotation = 0f,
                largeArc = true,
                sweep = true,
                x = (radius * cos(sweepRad)).toFloat(),
                y = (radius * sin(sweepRad)).toFloat(),
            )
            close()
        }

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 128)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.size > 8)
        assertTrue(flat.all { it.x in -radius - 0.01f..radius + 0.01f })
        assertTrue(flat.all { it.y in -radius - 0.01f..radius + 0.01f })
    }

    @Test
    fun `dash application carries interval progress across polyline segments`() {
        val dashed = applyDash(
            points = listOf(
                0f to 0f,
                3f to 0f,
                6f to 0f,
                9f to 0f,
                12f to 0f,
            ),
            dashArray = floatArrayOf(5f, 5f),
            phase = 0f,
        )

        val coveredLength = dashed.chunked(2).sumOf { (start, end) ->
            kotlin.math.abs(end.first - start.first).toDouble()
        }

        assertEquals(7.0, coveredLength, 1e-6)
        assertEquals(0f, dashed.first().first)
        assertEquals(12f, dashed.last().first)
        assertTrue(dashed.chunked(2).all { (start, end) ->
            end.first <= 5f || start.first >= 10f
        })
    }

    @Test
    fun `stroked paths keep flattened contour vertices instead of fill triangles`() {
        val flattened = listOf(
            Point(0f, 0f),
            Point(10f, 0f),
            Point(10f, 10f),
            Point(0f, 10f),
            Point(0f, 0f),
        )
        val fillTriangles = listOf(
            Point(0f, 0f),
            Point(10f, 0f),
            Point(10f, 10f),
            Point(0f, 0f),
            Point(10f, 10f),
            Point(0f, 10f),
        )

        val selected = selectPathVerticesForCommand(
            isStroke = true,
            flattened = flattened,
            triangulated = fillTriangles,
        )

        assertEquals(flattened, selected)
    }

    @Test
    fun `closed stroke geometry emits triangle contours instead of one filled fan`() {
        val square = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f,
            0f, 0f,
        )

        val stroke = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
        )

        assertEquals(24, stroke.vertices.size / 2)
        assertEquals((0..24 step 3).toList(), stroke.contourStarts)
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `dashed closed stroke geometry emits triangle contours instead of one filled fan`() {
        val square = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f,
            0f, 0f,
        )

        val stroke = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(5f, 5f),
        )

        val vertexCount = stroke.vertices.size / 2
        assertTrue(vertexCount > 3)
        assertEquals(vertexCount, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `dashed square caps extend each dash segment along tangent`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(4f, 4f),
            capStyle = StrokeCap.SQUARE,
        )

        val xs = stroke.vertices.filterIndexed { index, _ -> index % 2 == 0 }
        assertTrue(xs.min() < 0f)
        assertTrue(xs.max() > 8f)
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `zero length round stroke without dash emits cap geometry`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(10f, 10f, 10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            capStyle = StrokeCap.ROUND,
        )

        assertTrue(stroke.vertices.isNotEmpty())
        assertEquals(stroke.vertices.size / 2, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `tiny round stroke preserves caps around a very short segment`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(10f, 10f, 10.05f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            capStyle = StrokeCap.ROUND,
        )

        assertEquals(18, stroke.vertices.size / 2)
        assertEquals(listOf(0, 4, 11, 18), stroke.contourStarts)
    }

    @Test
    fun `short diagonal round stroke above Euclidean threshold keeps segment caps`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(10f, 10f, 10f + 0.95e-6f, 10f + 0.95e-6f),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            capStyle = StrokeCap.ROUND,
        )

        assertEquals(18, stroke.vertices.size / 2)
        assertEquals(listOf(0, 4, 11, 18), stroke.contourStarts)
    }

    @Test
    fun `round stroke does not collapse when only non-consecutive points are near`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(
                10f, 10f,
                10f + 0.75e-6f, 10f,
                10f + 3e-6f, 10f,
            ),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            capStyle = StrokeCap.ROUND,
        )

        val hasNonDegenerateTriangle = stroke.vertices.chunked(6).any { triangle ->
            val ax = triangle[0]
            val ay = triangle[1]
            val bx = triangle[2]
            val by = triangle[3]
            val cx = triangle[4]
            val cy = triangle[5]
            kotlin.math.abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax)) > 1e-6f
        }

        assertTrue(stroke.vertices.size / 2 != 36)
        assertTrue(hasNonDegenerateTriangle)
    }

    @Test
    fun `dashed zero length round stroke emits cap geometry`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(10f, 10f, 10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            dashArray = floatArrayOf(1f, 5f),
            capStyle = StrokeCap.ROUND,
        )

        assertTrue(stroke.vertices.isNotEmpty())
        assertEquals(stroke.vertices.size / 2, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `dashed single point round stroke emits cap geometry`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 4f,
            dashArray = floatArrayOf(1f, 5f),
            capStyle = StrokeCap.ROUND,
        )

        assertTrue(stroke.vertices.isNotEmpty())
        assertEquals(stroke.vertices.size / 2, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }

    @Test
    fun `dashed zero length non round strokes emit no geometry`() {
        for (cap in listOf(StrokeCap.BUTT, StrokeCap.SQUARE)) {
            val stroke = strokeToFillGeometry(
                contourVertices = listOf(10f, 10f),
                contourStarts = listOf(0),
                strokeWidth = 4f,
                dashArray = floatArrayOf(1f, 5f),
                capStyle = cap,
            )

            assertTrue(stroke.vertices.isEmpty())
            assertEquals(listOf(0), stroke.contourStarts)
        }
    }
}
