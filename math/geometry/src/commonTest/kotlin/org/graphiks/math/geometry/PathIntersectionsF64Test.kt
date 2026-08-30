package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathIntersectionsF64Test {
    @Test
    fun `proper crossings retain their F64 point and parameters after translation`() {
        listOf(0.0, 3_000.0, 1e12).forEach { translation ->
            val first = inputEdgeF64(
                0,
                Point2F64(translation - 1.0, translation - 1.0),
                Point2F64(translation + 1.0, translation + 1.0),
            )
            val second = inputEdgeF64(
                1,
                Point2F64(translation - 1.0, translation + 1.0),
                Point2F64(translation + 1.0, translation - 1.0),
            )

            val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))

            assertEquals(Point2F64(translation, translation), intersection.point)
            assertWithinFourUlpsF64(0.5, intersection.firstT)
            assertWithinFourUlpsF64(0.5, intersection.secondT)
        }
    }

    @Test
    fun `shared endpoints keep endpoint parameters after translation`() {
        listOf(0.0, 3_000.0, 1e12).forEach { translation ->
            val first = inputEdgeF64(0, Point2F64(translation, translation), Point2F64(translation + 1.0, translation))
            val second = inputEdgeF64(1, Point2F64(translation + 1.0, translation), Point2F64(translation + 1.0, translation + 1.0))

            val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))

            assertEquals(Point2F64(translation + 1.0, translation), intersection.point)
            assertWithinFourUlpsF64(1.0, intersection.firstT)
            assertWithinFourUlpsF64(0.0, intersection.secondT)
        }
    }

    @Test
    fun `T junctions retain interior and endpoint parameters after translation`() {
        listOf(0.0, 3_000.0, 1e12).forEach { translation ->
            val first = inputEdgeF64(0, Point2F64(translation, translation), Point2F64(translation + 2.0, translation))
            val second = inputEdgeF64(1, Point2F64(translation + 1.0, translation), Point2F64(translation + 1.0, translation + 1.0))

            val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))

            assertEquals(Point2F64(translation + 1.0, translation), intersection.point)
            assertWithinFourUlpsF64(0.5, intersection.firstT)
            assertWithinFourUlpsF64(0.0, intersection.secondT)
        }
    }

    @Test
    fun `a four edge crossing shares one canonical vertex identity after translation`() {
        listOf(0.0, 3_000.0, 1e12).forEach { translation ->
            val edges = listOf(
                inputEdgeF64(0, Point2F64(translation - 1.0, translation - 1.0), Point2F64(translation + 1.0, translation + 1.0)),
                inputEdgeF64(1, Point2F64(translation - 1.0, translation + 1.0), Point2F64(translation + 1.0, translation - 1.0)),
                inputEdgeF64(2, Point2F64(translation - 1.0, translation), Point2F64(translation + 1.0, translation)),
                inputEdgeF64(3, Point2F64(translation, translation - 1.0), Point2F64(translation, translation + 1.0)),
            )

            val split = splitPathEdgesF64(edges, PathOpsLimitsI32())
            val intersectionIdentities = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (point, _) -> point == Point2F64(translation, translation) }.map { it.second }

            assertEquals(8, split.size)
            assertEquals(1, intersectionIdentities.distinct().size)
            val identity = intersectionIdentities.first()
            assertEquals(listOf(0, 1, 2, 3), identity.incidentEdgeIds)
            assertEquals(mapOf(0 to 0.5, 1 to 0.5, 2 to 0.5, 3 to 0.5), identity.parameterByEdgeId)
            assertNull(identity.originalPointF32)
        }
    }

    @Test
    fun `splitting reuses endpoint identities and retains their original F32 point`() {
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(2.0, 0.0))
        val second = inputEdgeF64(1, Point2F64(1.0, 0.0), Point2F64(1.0, 1.0))

        val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32())

        assertEquals(first.startIdentity, split.single { it.sourceId == 0 && it.start == first.start }.startIdentity)
        assertEquals(first.endIdentity, split.single { it.sourceId == 0 && it.end == first.end }.endIdentity)
        assertEquals(second.endIdentity, split.single { it.sourceId == 1 && it.end == second.end }.endIdentity)
    }

    @Test
    fun `collinear disjoint segments have no intersection`() {
        assertNull(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(2.0, 0.0)),
                inputEdgeF64(1, Point2F64(3.0, 0.0), Point2F64(5.0, 0.0)),
            ),
        )
    }

    @Test
    fun `collinear contact is represented by a point`() {
        val intersection = assertIs<PathIntersectionF64.PointF64>(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(4.0, 0.0)),
                inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(7.0, 0.0)),
            ),
        )

        assertEquals(Point2F64(4.0, 0.0), intersection.point)
        assertEquals(1.0, intersection.firstT)
        assertEquals(0.0, intersection.secondT)
    }

    @Test
    fun `partial collinear overlap reports ordered ranges`() {
        val overlap = assertIs<PathIntersectionF64.OverlapF64>(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
                inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0)),
            ),
        )

        assertEquals(Point2F64(4.0, 0.0), overlap.start)
        assertEquals(Point2F64(10.0, 0.0), overlap.end)
        assertEquals(0.4..1.0, overlap.firstRange)
        assertEquals(0.0..0.75, overlap.secondRange)
    }

    @Test
    fun `full reversed collinear overlap orders both parameter ranges`() {
        val overlap = assertIs<PathIntersectionF64.OverlapF64>(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
                inputEdgeF64(1, Point2F64(10.0, 0.0), Point2F64(0.0, 0.0)),
            ),
        )

        assertEquals(Point2F64(0.0, 0.0), overlap.start)
        assertEquals(Point2F64(10.0, 0.0), overlap.end)
        assertEquals(0.0..1.0, overlap.firstRange)
        assertEquals(0.0..1.0, overlap.secondRange)
    }

    @Test
    fun `contained collinear overlap reports the contained range`() {
        val overlap = assertIs<PathIntersectionF64.OverlapF64>(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
                inputEdgeF64(1, Point2F64(3.0, 0.0), Point2F64(7.0, 0.0)),
            ),
        )

        assertEquals(Point2F64(3.0, 0.0), overlap.start)
        assertEquals(Point2F64(7.0, 0.0), overlap.end)
        assertEquals(0.3..0.7, overlap.firstRange)
        assertEquals(0.0..1.0, overlap.secondRange)
    }

    @Test
    fun `splitting an overlap emits each nonzero contributor subedge once`() {
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
                inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0), PathOperand.SECOND),
            ),
            PathOpsLimitsI32(),
        )

        assertEquals(
            listOf(
                Triple(0, Point2F64(0.0, 0.0), Point2F64(4.0, 0.0)),
                Triple(0, Point2F64(4.0, 0.0), Point2F64(10.0, 0.0)),
                Triple(1, Point2F64(4.0, 0.0), Point2F64(10.0, 0.0)),
                Triple(1, Point2F64(10.0, 0.0), Point2F64(12.0, 0.0)),
            ),
            split.map { Triple(it.sourceId, it.start, it.end) },
        )
        assertTrue(split.all { it.start != it.end })
    }

    @Test
    fun `splitting rejects more than the deterministic intersection limit`() {
        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(fourEdgesWithThreeDistinctCrossingsF64(), PathOpsLimitsI32(maxIntersections = 2))
        }

        assertEquals("path-intersection-limit", error.message)
    }
}

private fun inputEdgeF64(
    id: Int,
    start: Point2F64,
    end: Point2F64,
    operand: PathOperand = PathOperand.FIRST,
): PathInputEdgeF64 = PathInputEdgeF64(
    id = id,
    operand = operand,
    contourIndex = 0,
    startIdentity = PathVertexIdentityF64(listOf(id), mapOf(id to 0.0), Point2F32(start.x.toFloat(), start.y.toFloat())),
    endIdentity = PathVertexIdentityF64(listOf(id), mapOf(id to 1.0), Point2F32(end.x.toFloat(), end.y.toFloat())),
    start = start,
    end = end,
    windingDelta = 1,
)

private fun fourEdgesWithThreeDistinctCrossingsF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(-2.0, 0.0), Point2F64(2.0, 0.0)),
    inputEdgeF64(1, Point2F64(-1.0, -1.0), Point2F64(-1.0, 1.0)),
    inputEdgeF64(2, Point2F64(0.0, -1.0), Point2F64(0.0, 1.0)),
    inputEdgeF64(3, Point2F64(1.0, -1.0), Point2F64(1.0, 1.0)),
)

private fun assertWithinFourUlpsF64(expected: Double, actual: Double) {
    assertTrue(abs(expected.toBits() - actual.toBits()) <= 4L, "Expected $expected within four ULPs, got $actual")
}
