package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathTessellatorTest {

    @Test
    fun `empty path produces empty result`() {
        val tessellator = PathTessellator()
        val path = PathData(emptyList(), emptyList())
        val flat = tessellator.flatten(path)
        assertEquals(0, flat.size)
        val tri = tessellator.triangulate(flat)
        assertEquals(0, tri.vertices.size)
        assertEquals(0, tri.indices.size)
        assertEquals(0, tri.triangleCount)
    }

    @Test
    fun `flatten with contours preserves moveTo contour boundaries`() {
        val tessellator = PathTessellator()
        val firstMove = Point(0f, 0f)
        val secondMove = Point(20f, 20f)
        val path = PathData(
            verbs = listOf(
                PathVerb.MoveTo(firstMove),
                PathVerb.LineTo(Point(10f, 0f)),
                PathVerb.LineTo(Point(5f, 10f)),
                PathVerb.Close,
                PathVerb.MoveTo(secondMove),
                PathVerb.LineTo(Point(30f, 20f)),
                PathVerb.LineTo(Point(25f, 30f)),
                PathVerb.Close,
            ),
            points = emptyList(),
        )

        val flattened = tessellator.flattenWithContours(path)

        assertEquals(listOf(0, 4), flattened.contourStarts)
        assertEquals(firstMove, flattened.points[0])
        assertEquals(secondMove, flattened.points[4])
    }

    @Test
    fun `empty move contours do not emit ghost points`() {
        val tessellator = PathTessellator()
        val moveOnlyPath = PathData(
            verbs = listOf(PathVerb.MoveTo(Point(1f, 1f))),
            points = emptyList(),
        )
        val moveOnly = tessellator.flattenWithContours(moveOnlyPath)
        val consecutiveMoves = tessellator.flattenWithContours(
            PathData(
                verbs = listOf(
                    PathVerb.MoveTo(Point(1f, 1f)),
                    PathVerb.MoveTo(Point(2f, 2f)),
                    PathVerb.LineTo(Point(3f, 2f)),
                ),
                points = emptyList(),
            ),
        )
        val trailingMove = tessellator.flattenWithContours(
            PathData(
                verbs = listOf(
                    PathVerb.MoveTo(Point(1f, 1f)),
                    PathVerb.LineTo(Point(2f, 1f)),
                    PathVerb.MoveTo(Point(3f, 3f)),
                ),
                points = emptyList(),
            ),
        )
        val moveThenClose = tessellator.flattenWithContours(
            PathData(
                verbs = listOf(PathVerb.MoveTo(Point(1f, 1f)), PathVerb.Close),
                points = emptyList(),
            ),
        )

        assertEquals(FlattenedPath(emptyList(), emptyList()), moveOnly)
        assertEquals(emptyList(), tessellator.flatten(moveOnlyPath))
        assertEquals(
            FlattenedPath(
                points = listOf(Point(2f, 2f), Point(3f, 2f)),
                contourStarts = listOf(0),
            ),
            consecutiveMoves,
        )
        assertEquals(
            FlattenedPath(
                points = listOf(Point(1f, 1f), Point(2f, 1f)),
                contourStarts = listOf(0),
            ),
            trailingMove,
        )
        assertEquals(FlattenedPath(emptyList(), emptyList()), moveThenClose)
    }

    @Test
    fun `drawing after close starts an implicit contour at the closed contour start`() {
        val tessellator = PathTessellator()
        val path = PathData(
            verbs = listOf(
                PathVerb.MoveTo(Point(1f, 1f)),
                PathVerb.LineTo(Point(3f, 1f)),
                PathVerb.Close,
                PathVerb.LineTo(Point(5f, 1f)),
            ),
            points = emptyList(),
        )

        val flattened = tessellator.flattenWithContours(path)

        assertEquals(
            listOf(
                Point(1f, 1f),
                Point(3f, 1f),
                Point(1f, 1f),
                Point(1f, 1f),
                Point(5f, 1f),
            ),
            flattened.points,
        )
        assertEquals(listOf(0, 3), flattened.contourStarts)
    }

    @Test
    fun `implicit line quadratic and cubic contours start and close at origin`() {
        val tessellator = PathTessellator(tolerance = 10f)
        val origin = Point(0f, 0f)
        val paths = listOf(
            PathData(listOf(PathVerb.LineTo(Point(10f, 0f)), PathVerb.Close), emptyList()),
            PathData(
                listOf(PathVerb.QuadTo(Point(5f, 10f), Point(10f, 0f)), PathVerb.Close),
                emptyList(),
            ),
            PathData(
                listOf(
                    PathVerb.CubicTo(
                        Point(3f, 10f),
                        Point(7f, 10f),
                        Point(10f, 0f),
                    ),
                    PathVerb.Close,
                ),
                emptyList(),
            ),
        )

        paths.forEach { path ->
            val flattened = tessellator.flattenWithContours(path)

            assertEquals(listOf(0), flattened.contourStarts)
            assertEquals(origin, flattened.points.first())
            assertEquals(origin, flattened.points.last())
        }
    }

    @Test
    fun `flatten rejects a point beyond the vertex budget`() {
        val tessellator = PathTessellator(maxVertices = 2)
        val path = PathData(
            verbs = listOf(
                PathVerb.MoveTo(Point(0f, 0f)),
                PathVerb.LineTo(Point(1f, 0f)),
                PathVerb.LineTo(Point(2f, 0f)),
            ),
            points = emptyList(),
        )

        val ex = assertFailsWith<IllegalStateException> {
            tessellator.flatten(path)
        }

        assertContains(ex.message!!, "exceeds budget")
    }

    @Test
    fun `implicit contour start obeys the vertex budget`() {
        val tessellator = PathTessellator(maxVertices = 0)
        val path = PathData(
            verbs = listOf(PathVerb.LineTo(Point(1f, 0f))),
            points = emptyList(),
        )

        val ex = assertFailsWith<IllegalStateException> {
            tessellator.flatten(path)
        }

        assertContains(ex.message!!, "exceeds budget")
    }

    @Test
    fun `plain flatten returns flattened path points`() {
        val tessellator = PathTessellator()
        val path = makeStar(
            centerX = 100f, centerY = 100f,
            outerRadius = 50f, innerRadius = 25f, points = 5,
        )

        assertEquals(tessellator.flattenWithContours(path).points, tessellator.flatten(path))
    }

    @Test
    fun `circle approximation stays within vertex budget`() {
        val tessellator = PathTessellator(tolerance = 0.5f)
        val path = makeCircle(centerX = 100f, centerY = 100f, radius = 50f)
        val flat = tessellator.flatten(path)
        assertTrue(flat.size <= 256, "Circle has ${flat.size} vertices, expected <= 256")
        assertTrue(flat.size > 4, "Circle should have more than 4 vertices")
    }

    @Test
    fun `star path flattens to vertices and triangulates`() {
        val tessellator = PathTessellator()
        val path = makeStar(
            centerX = 100f, centerY = 100f,
            outerRadius = 50f, innerRadius = 25f, points = 5,
        )
        val flat = tessellator.flatten(path)
        assertTrue(flat.size > 3, "Star should have >3 vertices")
        val tri = tessellator.triangulate(flat)
        assertTrue(tri.triangleCount > 0, "Star should produce at least one triangle")
        assertTrue(tri.indices.all { it in tri.vertices.indices },
            "All indices should reference valid vertices")
        assertTrue(tri.indices.size % 3 == 0,
            "Triangle indices should be multiples of 3")
    }

    @Test
    fun `path exceeding 256 vertices throws`() {
        val tessellator = PathTessellator(tolerance = 0.001f, maxVertices = 256)
        val path = PathData(
            verbs = listOf(
                PathVerb.CubicTo(
                    c1 = Point(0f, 1000f), c2 = Point(1000f, 1000f),
                    p = Point(1000f, 0f),
                ),
            ),
            points = emptyList(),
        )
        val ex = assertFailsWith<IllegalStateException> {
            tessellator.flatten(path)
        }
        assertContains(ex.message!!, "exceeds budget")
    }

    @Test
    fun `triangulate path exceeding max vertices throws`() {
        val tessellator = PathTessellator(maxVertices = 4)
        val points = listOf(
            Point(0f, 0f), Point(10f, 0f), Point(10f, 10f),
            Point(0f, 10f), Point(0f, 0f),
        )
        val ex = assertFailsWith<IllegalStateException> {
            tessellator.triangulate(points)
        }
        assertContains(ex.message!!, "exceeds budget")
    }

    @Test
    fun `triangulate less than 3 points returns empty`() {
        val tessellator = PathTessellator()
        val points = listOf(Point(0f, 0f), Point(10f, 0f))
        val tri = tessellator.triangulate(points)
        assertEquals(0, tri.vertices.size)
        assertEquals(0, tri.indices.size)
    }

    @Test
    fun `line to starts an implicit contour at origin`() {
        val tessellator = PathTessellator()
        val path = PathData(
            verbs = listOf(PathVerb.LineTo(Point(10f, 0f)), PathVerb.LineTo(Point(10f, 10f))),
            points = emptyList(),
        )
        val flat = tessellator.flatten(path)
        assertEquals(
            listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f)),
            flat,
        )
    }

    @Test
    fun `quadratic bezier flattens correctly`() {
        val tessellator = PathTessellator(tolerance = 1f)
        val path = PathData(
            verbs = listOf(
                PathVerb.QuadTo(c = Point(50f, 100f), p = Point(100f, 0f)),
            ),
            points = emptyList(),
        )
        val flat = tessellator.flatten(path)
        assertTrue(flat.size >= 2, "Quadratic bezier should produce at least 2 points")
        assertEquals(Point(100f, 0f), flat.last(),
            "Quadratic bezier should end at end point")
    }

    @Test
    fun `cubic bezier flattens correctly`() {
        val tessellator = PathTessellator(tolerance = 1f)
        val path = PathData(
            verbs = listOf(
                PathVerb.CubicTo(
                    c1 = Point(30f, 100f), c2 = Point(70f, 100f),
                    p = Point(100f, 0f),
                ),
            ),
            points = emptyList(),
        )
        val flat = tessellator.flatten(path)
        assertTrue(flat.size >= 2, "Cubic bezier should produce at least 2 points")
        assertEquals(Point(100f, 0f), flat.last(),
            "Cubic bezier should end at end point")
    }

    @Test
    fun `triangle produces single triangle via fan`() {
        val tessellator = PathTessellator()
        val points = listOf(Point(0f, 0f), Point(10f, 0f), Point(5f, 10f))
        val tri = tessellator.triangulate(points)
        assertEquals(1, tri.triangleCount)
        assertEquals(3, tri.indices.size)
    }

    @Test
    fun `rectangle produces two triangles via fan`() {
        val tessellator = PathTessellator()
        val points = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f))
        val tri = tessellator.triangulate(points)
        assertEquals(2, tri.triangleCount)
        assertEquals(6, tri.indices.size)
    }

    private fun makeCircle(centerX: Float, centerY: Float, radius: Float): PathData {
        val n = 64
        val pts = (0 until n).map { i ->
            val angle = 2.0 * kotlin.math.PI * i / n
            Point(
                centerX + radius * kotlin.math.cos(angle).toFloat(),
                centerY + radius * kotlin.math.sin(angle).toFloat(),
            )
        }
        return PathData(
            verbs = pts.map { PathVerb.LineTo(it) } + listOf(PathVerb.Close),
            points = emptyList(),
        )
    }

    private fun makeStar(
        centerX: Float, centerY: Float,
        outerRadius: Float, innerRadius: Float, points: Int,
    ): PathData {
        val pts = mutableListOf<Point>()
        for (i in 0 until points * 2) {
            val angle = kotlin.math.PI * i / points - kotlin.math.PI / 2
            val r = if (i % 2 == 0) outerRadius else innerRadius
            pts.add(
                Point(
                    centerX + r * kotlin.math.cos(angle).toFloat(),
                    centerY + r * kotlin.math.sin(angle).toFloat(),
                ),
            )
        }
        return PathData(
            verbs = pts.map { PathVerb.LineTo(it) } + listOf(PathVerb.Close),
            points = emptyList(),
        )
    }

    private fun triangleArea(a: Point, b: Point, c: Point): Float =
        ((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) * 0.5f
}
