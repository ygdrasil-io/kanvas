package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ConvexFanExecutorTest {

    @Test
    fun `circle fill via convex fan produces single pass`() {
        val tessellator = PathTessellator(tolerance = 0.5f)
        val path = makeCircle(100f, 100f, 50f)
        val flat = tessellator.flatten(path)
        val tri = tessellator.triangulate(flat)

        val executor = ConvexFanExecutor()
        val stats = executor.execute(tri)

        assertEquals(1, stats.drawCallCount)
        assertTrue(stats.singlePass)
        assertTrue(stats.triangleCount > 0)
    }

    @Test
    fun `convex fan performance is better than stencil cover`() {
        val tessellator = PathTessellator(tolerance = 1f)
        val path = makeRegularPolygon(100f, 100f, 50f, 8)
        val flat = tessellator.flatten(path)
        val tri = tessellator.triangulate(flat)

        val fanExecutor = ConvexFanExecutor()
        val fanStats = fanExecutor.execute(tri)

        val stencilExecutor = StencilCoverExecutor()
        val stencilStats = stencilExecutor.execute(tri)

        assertTrue(fanStats.drawCallCount < stencilStats.totalDrawCalls,
            "Convex fan should use fewer draw calls than stencil-cover")
        val perf = fanExecutor.performanceDiagnostics(fanStats, stencilStats)
        assertTrue(perf.isNotEmpty())
        assertContains(perf.first(), "fewer passes")
    }

    @Test
    fun `empty triangle list is refused`() {
        val executor = ConvexFanExecutor()
        val empty = TriangleList(emptyList(), emptyList())
        val ex = assertFailsWith<IllegalArgumentException> {
            executor.execute(empty)
        }
        assertContains(ex.message!!, "at least one triangle")
    }

    @Test
    fun `convexity detection returns true for regular polygon`() {
        val pts = listOf(
            Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f),
        )
        assertTrue(isPathConvex(pts))
    }

    @Test
    fun `convexity detection returns false for star`() {
        val pts = listOf(
            Point(0f, 0f), Point(5f, -10f), Point(10f, 0f), Point(0f, 10f), Point(10f, 10f),
        )
        assertFalse(isPathConvex(pts))
    }

    @Test
    fun `convexity detection returns false for less than 3 points`() {
        val pts = listOf(Point(0f, 0f), Point(10f, 0f))
        assertFalse(isPathConvex(pts))
    }

    @Test
    fun `convexity detection routes correctly to fan for convex paths`() {
        val octagon = makeRegularPolygon(100f, 100f, 50f, 8)
        val tessellator = PathTessellator(tolerance = 0.5f)
        val flat = tessellator.flatten(octagon)
        assertTrue(isPathConvex(flat), "Regular octagon should be convex")
    }

    private fun makeCircle(cx: Float, cy: Float, r: Float): PathData {
        val n = 64
        val pts = (0 until n).map { i ->
            val angle = 2.0 * kotlin.math.PI * i / n
            Point(
                cx + r * kotlin.math.cos(angle).toFloat(),
                cy + r * kotlin.math.sin(angle).toFloat(),
            )
        }
        return PathData(
            verbs = pts.map { PathVerb.LineTo(it) } + listOf(PathVerb.Close),
            points = emptyList(),
        )
    }

    private fun makeRegularPolygon(
        cx: Float, cy: Float, r: Float, sides: Int,
    ): PathData {
        val pts = (0 until sides).map { i ->
            val angle = 2.0 * kotlin.math.PI * i / sides - kotlin.math.PI / 2
            Point(
                cx + r * kotlin.math.cos(angle).toFloat(),
                cy + r * kotlin.math.sin(angle).toFloat(),
            )
        }
        return PathData(
            verbs = listOf(PathVerb.MoveTo(pts.first())) +
                pts.drop(1).map { PathVerb.LineTo(it) } +
                listOf(PathVerb.Close),
            points = emptyList(),
        )
    }
}
