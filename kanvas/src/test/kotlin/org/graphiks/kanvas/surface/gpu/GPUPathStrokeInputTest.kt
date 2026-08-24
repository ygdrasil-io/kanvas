package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb as GpuPathVerb
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUPathStrokeInputTest {
    @Test
    fun `hairline expansion stays one device pixel under uniform scale`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f),
            contourStarts = listOf(0),
            strokeWidth = 0f,
            transform = GPUTransformFacts.affine(
                scaleX = 2f,
                skewX = 0f,
                skewY = 0f,
                scaleY = 2f,
            ),
        )
        val xs = stroke.vertices.filterIndexed { index, _ -> index % 2 == 0 }
        val ys = stroke.vertices.filterIndexed { index, _ -> index % 2 == 1 }

        assertEquals(StrokeGeometryCoordinateSpace.DEVICE, stroke.coordinateSpace)
        assertEquals(0f, xs.min(), 1e-6f)
        assertEquals(20f, xs.max(), 1e-6f)
        assertEquals(-0.5f, ys.min(), 1e-6f)
        assertEquals(0.5f, ys.max(), 1e-6f)
    }

    @Test
    fun `hairline expansion uses exact device normals under nonuniform shear`() {
        val start = 3f to 4f
        val end = 23f to 9f
        val directionX = end.first - start.first
        val directionY = end.second - start.second
        val directionLength = sqrt(directionX * directionX + directionY * directionY)
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f),
            contourStarts = listOf(0),
            strokeWidth = 0f,
            transform = GPUTransformFacts.affine(
                scaleX = 2f,
                skewX = 1f,
                skewY = 0.5f,
                scaleY = 3f,
                translateX = start.first,
                translateY = start.second,
            ),
        )

        val signedDistances = stroke.vertices.chunked(2).map { (x, y) ->
            ((x - start.first) * directionY - (y - start.second) * directionX) /
                directionLength
        }
        val longitudinalPositions = stroke.vertices.chunked(2).map { (x, y) ->
            ((x - start.first) * directionX + (y - start.second) * directionY) /
                directionLength
        }

        assertTrue(signedDistances.all { distance -> abs(abs(distance) - 0.5f) < 1e-5f })
        assertTrue(longitudinalPositions.all { position ->
            abs(position) < 1e-5f || abs(position - directionLength) < 1e-5f
        })
    }

    @Test
    fun `open bevel joins cover the exterior of left and right turns`() {
        val left = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f, 10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.BEVEL,
        )
        val right = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f, 10f, -10f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.BEVEL,
        )

        assertEquals(
            setOf(10f to 0f, 10f to -1f, 11f to 0f),
            left.lastContourPoints(),
        )
        assertEquals(
            setOf(10f to 0f, 10f to 1f, 11f to 0f),
            right.lastContourPoints(),
        )
    }

    @Test
    fun `open miter join uses the exterior point and falls back to exterior bevel`() {
        val admitted = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f, 10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
            miterLimit = 2f,
        )
        val fallback = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 10f, 0f, 10f, 10f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
            miterLimit = 1f,
        )

        assertPointSetEqualsWithinTolerance(
            setOf(10f to -1f, 11f to -1f, 11f to 0f),
            admitted.lastContourPoints(),
        )
        assertEquals(
            setOf(10f to 0f, 10f to -1f, 11f to 0f),
            fallback.lastContourPoints(),
        )
    }

    @Test
    fun `closed joins cover exterior corners for bevel miter and fallback`() {
        val square = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f,
            0f, 0f,
        )
        val bevel = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.BEVEL,
        )
        val admitted = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
            miterLimit = 2f,
        )
        val fallback = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
            miterLimit = 1f,
        )

        assertEquals(
            setOf(0f to 0f, -1f to 0f, 0f to -1f),
            bevel.contourPoints(contourIndex = 8),
        )
        assertEquals(
            setOf(0f to 0f, -1f to 0f, 0f to -1f),
            fallback.contourPoints(contourIndex = 8),
        )
        assertPointSetEqualsWithinTolerance(
            setOf(
                -1f to -1f,
                1f to 1f,
                11f to -1f,
                9f to 1f,
                11f to 11f,
                9f to 9f,
                -1f to 11f,
                1f to 9f,
            ),
            admitted.vertices.chunked(2).map { (x, y) -> x to y }.toSet(),
        )
    }

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
        val path = Path().addRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 8f), radius = 2f))

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 64)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.size > 9)
        assertEquals(flat.first(), flat.last())
    }

    @Test
    fun `rounded rect conversion clamps oversized radii inside rect bounds`() {
        val rect = RectF32.ofLTRB(0f, 0f, 78f, 38f)
        val path = Path().addRRect(RRectF32.of(rect, radius = 400f))

        val flat = PathTessellator(tolerance = 0.25f, maxVertices = 128)
            .flatten(path.toPathTessellatorData())

        assertTrue(flat.isNotEmpty())
        assertTrue(flat.all { it.x in rect.left - 0.01f..rect.right + 0.01f })
        assertTrue(flat.all { it.y in rect.top - 0.01f..rect.bottom + 0.01f })
    }

    @Test
    fun `move close round stroke emits cap geometry through path flattening`() {
        val path = Path().apply {
            moveTo(10f, 10f)
            close()
        }
        val flattened = PathTessellator().flattenWithContours(path.toPathTessellatorData())

        val stroke = strokeToFillGeometry(
            contourVertices = flattened.points.flatMap { listOf(it.x, it.y) },
            contourStarts = flattened.contourStarts,
            strokeWidth = 10f,
            capStyle = StrokeCap.ROUND,
        )

        assertEquals(listOf(Point(10f, 10f)), flattened.points)
        assertEquals(listOf(0), flattened.contourStarts)
        assertTrue(stroke.vertices.isNotEmpty())
        assertEquals(stroke.vertices.size / 2, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
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
    fun `dash runs stay continuous through corners and honor phase`() {
        val cornerRun = applyDashRuns(
            points = listOf(0f to 0f, 5f to 0f, 5f to 5f),
            dashArray = floatArrayOf(8f, 4f),
            phase = 0f,
            closed = false,
        )
        val phasedRuns = applyDashRuns(
            points = listOf(0f to 0f, 12f to 0f),
            dashArray = floatArrayOf(5f, 5f),
            phase = 2f,
            closed = false,
        )

        assertEquals(
            listOf(
                DashRun(
                    points = listOf(0f to 0f, 5f to 0f, 5f to 3f),
                    closed = false,
                ),
            ),
            cornerRun,
        )
        assertEquals(
            listOf(
                DashRun(points = listOf(0f to 0f, 3f to 0f), closed = false),
                DashRun(points = listOf(8f to 0f, 12f to 0f), closed = false),
            ),
            phasedRuns,
        )
    }

    @Test
    fun `closed dash runs merge across explicit and implicit seams without duplicates`() {
        val implicitSquare = listOf(
            0f to 0f,
            10f to 0f,
            10f to 10f,
            0f to 10f,
        )
        val expected = listOf(
            DashRun(
                points = listOf(0f to 2f, 0f to 0f, 3f to 0f),
                closed = false,
            ),
            DashRun(
                points = listOf(8f to 0f, 10f to 0f, 10f to 8f),
                closed = false,
            ),
            DashRun(
                points = listOf(7f to 10f, 0f to 10f, 0f to 7f),
                closed = false,
            ),
        )

        val implicit = applyDashRuns(
            points = implicitSquare,
            dashArray = floatArrayOf(10f, 5f),
            phase = 7f,
            closed = true,
        )
        val explicit = applyDashRuns(
            points = implicitSquare + (0f to 0f),
            dashArray = floatArrayOf(10f, 5f),
            phase = 7f,
            closed = true,
        )
        val fullyOn = applyDashRuns(
            points = implicitSquare,
            dashArray = floatArrayOf(100f, 5f),
            phase = 0f,
            closed = true,
        )
        val implicitStroke = strokeToFillGeometry(
            contourVertices = implicitSquare.flatMap { point ->
                listOf(point.first, point.second)
            },
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(10f, 5f),
            dashPhase = 7f,
            closedContours = setOf(0),
        )
        val explicitStroke = strokeToFillGeometry(
            contourVertices = (implicitSquare + (0f to 0f)).flatMap { point ->
                listOf(point.first, point.second)
            },
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(10f, 5f),
            dashPhase = 7f,
        )

        assertEquals(expected, implicit)
        assertEquals(expected, explicit)
        assertEquals(1, implicit.first().points.count { point -> point == (0f to 0f) })
        assertEquals(
            listOf(
                DashRun(
                    points = implicitSquare + (0f to 0f),
                    closed = true,
                ),
            ),
            fullyOn,
        )
        assertEquals(explicitStroke.contourStarts, implicitStroke.contourStarts)
        assertEquals(explicitStroke.vertices, implicitStroke.vertices)
    }

    @Test
    fun `continuous dashed corner keeps endpoint square caps and an exterior join`() {
        val stroke = strokeToFillGeometry(
            contourVertices = listOf(0f, 0f, 5f, 0f, 5f, 5f),
            contourStarts = listOf(0),
            strokeWidth = 2f,
            dashArray = floatArrayOf(8f, 4f),
            capStyle = StrokeCap.SQUARE,
            joinStyle = StrokeJoin.BEVEL,
        )

        assertEquals(
            setOf(-1f to -1f, -1f to 1f, 0f to 1f, 0f to -1f),
            stroke.contourPoints(contourIndex = 1),
        )
        assertEquals(
            setOf(4f to 3f, 6f to 3f, 6f to 4f, 4f to 4f),
            stroke.contourPoints(contourIndex = 3),
        )
        assertEquals(
            setOf(5f to 0f, 5f to -1f, 6f to 0f),
            stroke.lastContourPoints(),
        )
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
    fun `dashed closed stroke geometry emits separate edge contours instead of one filled fan`() {
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
        assertEquals(
            listOf(
                setOf(0f to -1f, 0f to 1f, 5f to 1f, 5f to -1f),
                setOf(11f to 0f, 9f to 0f, 9f to 5f, 11f to 5f),
                setOf(10f to 11f, 10f to 9f, 5f to 9f, 5f to 11f),
                setOf(-1f to 10f, 1f to 10f, 1f to 5f, -1f to 5f),
            ),
            stroke.contourStarts.dropLast(1).indices.map { contourIndex ->
                stroke.contourPoints(contourIndex)
            },
        )
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
        assertEquals(
            listOf(
                setOf(0f to -1f, 0f to 1f, 4f to 1f, 4f to -1f),
                setOf(-1f to -1f, -1f to 1f, 0f to 1f, 0f to -1f),
                setOf(4f to -1f, 4f to 1f, 5f to 1f, 5f to -1f),
                setOf(8f to -1f, 8f to 1f, 10f to 1f, 10f to -1f),
                setOf(7f to -1f, 7f to 1f, 8f to 1f, 8f to -1f),
                setOf(10f to -1f, 10f to 1f, 11f to 1f, 11f to -1f),
            ),
            stroke.contourStarts.dropLast(1).indices.map { contourIndex ->
                stroke.contourPoints(contourIndex)
            },
        )
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

    private fun StrokeGeometry.lastContourPoints(): Set<Pair<Float, Float>> =
        contourPoints(contourStarts.lastIndex - 1)

    private fun StrokeGeometry.contourPoints(contourIndex: Int): Set<Pair<Float, Float>> {
        val start = contourStarts[contourIndex]
        val end = contourStarts[contourIndex + 1]
        return vertices.subList(start * 2, end * 2)
            .chunked(2)
            .map { (x, y) -> x to y }
            .toSet()
    }

    private fun assertPointSetEqualsWithinTolerance(
        expected: Set<Pair<Float, Float>>,
        actual: Set<Pair<Float, Float>>,
        tolerance: Float = 1e-5f,
    ) {
        assertEquals(expected.size, actual.size)
        assertTrue(expected.all { expectedPoint ->
            actual.any { actualPoint ->
                abs(expectedPoint.first - actualPoint.first) <= tolerance &&
                    abs(expectedPoint.second - actualPoint.second) <= tolerance
            }
        })
    }
}
