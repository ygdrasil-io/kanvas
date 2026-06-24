package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StencilCoverExecutorTest {

    @Test
    fun `star fill via stencil cover reports correct stats`() {
        val tessellator = PathTessellator()
        val path = makeStar(
            centerX = 100f, centerY = 100f,
            outerRadius = 50f, innerRadius = 25f, points = 5,
        )
        val flat = tessellator.flatten(path)
        val tri = tessellator.triangulate(flat)

        val executor = StencilCoverExecutor()
        val stats = executor.execute(tri, Point(0.92f, 0.18f))

        assertEquals(1, stats.stencilPassCount)
        assertEquals(1, stats.coverPassCount)
        assertEquals(2, stats.totalDrawCalls)
        assertEquals(tri.triangleCount, stats.triangleCount)
        assertTrue(stats.vertexCount > 0)
    }

    @Test
    fun `stencil buffer diagnostics are emitted between passes`() {
        val executor = StencilCoverExecutor()
        val diagnostics = executor.stencilStateDiagnostics()

        assertEquals(10, diagnostics.size)
        assertContains(diagnostics.joinToString(" "), "stencil-write")
        assertContains(diagnostics.joinToString(" "), "cover-resolve")
        assertContains(diagnostics.joinToString(" "), "twoPass=true")
    }

    @Test
    fun `empty triangle list is refused`() {
        val executor = StencilCoverExecutor()
        val empty = TriangleList(emptyList(), emptyList())
        val ex = assertFailsWith<IllegalArgumentException> {
            executor.execute(empty, Point(1f, 0f))
        }
        assertContains(ex.message!!, "at least one triangle")
    }

    @Test
    fun `circle fill via stencil cover produces passes`() {
        val tessellator = PathTessellator(tolerance = 0.5f)
        val path = makeCircle(100f, 100f, 50f)
        val flat = tessellator.flatten(path)
        val tri = tessellator.triangulate(flat)

        val executor = StencilCoverExecutor()
        val stats = executor.execute(tri, Point(0f, 0f))

        assertEquals(2, stats.totalDrawCalls)
        assertTrue(stats.triangleCount > 0)
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
}
