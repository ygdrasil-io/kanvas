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
    fun `an intersection one e minus sixteen from an endpoint stays interior and splits`() {
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0))
        val second = inputEdgeF64(1, Point2F64(1e-16, -1.0), Point2F64(1e-16, 1.0))

        val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))
        val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32())
        val interiorIdentities = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (point, _) -> point == Point2F64(1e-16, 0.0) }.map { it.second }

        assertEquals(Point2F64(1e-16, 0.0), intersection.point)
        assertEquals(1e-16, intersection.firstT)
        assertEquals(0.5, intersection.secondT)
        assertEquals(2, split.count { it.sourceId == first.id })
        assertEquals(first.startIdentity, split.single { it.sourceId == first.id && it.start == first.start }.startIdentity)
        assertEquals(1, interiorIdentities.distinct().size)
        assertEquals(listOf(0, 1), interiorIdentities.first().incidentEdgeIds)
        assertEquals(mapOf(0 to 1e-16, 1 to 0.5), interiorIdentities.first().parameterByEdgeId)
        assertNull(interiorIdentities.first().originalPointF32)
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
            assertEquals(mapOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), split.groupingBy { it.sourceId }.eachCount())
            assertEquals(8, intersectionIdentities.size)
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
        val intersectionIdentities = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (point, _) -> point == Point2F64(1.0, 0.0) }.map { it.second }

        assertEquals(first.startIdentity, split.single { it.sourceId == 0 && it.start == first.start }.startIdentity)
        assertEquals(first.endIdentity, split.single { it.sourceId == 0 && it.end == first.end }.endIdentity)
        assertEquals(second.endIdentity, split.single { it.sourceId == 1 && it.end == second.end }.endIdentity)
        assertEquals(3, intersectionIdentities.size)
        assertEquals(1, intersectionIdentities.distinct().size)
        assertEquals(
            PathVertexIdentityF64(
                incidentEdgeIds = listOf(0, 1),
                parameterByEdgeId = mapOf(0 to 0.5, 1 to 0.0),
                originalPointF32 = Point2F32(1f, 0f),
            ),
            intersectionIdentities.first(),
        )
    }

    @Test
    fun `splitting rejects duplicate source ids across operands`() {
        val error = assertFailsWith<IllegalArgumentException> {
            splitPathEdgesF64(
                listOf(
                    inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(2.0, 0.0), PathOperand.FIRST),
                    inputEdgeF64(0, Point2F64(1.0, -1.0), Point2F64(1.0, 1.0), PathOperand.SECOND),
                ),
                PathOpsLimitsI32(),
            )
        }

        assertEquals("path-edge-id-duplicate", error.message)
    }

    @Test
    fun `splitting rejects malformed source endpoint identities`() {
        val edge = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0))
        val malformedStart = edge.copy(
            startIdentity = PathVertexIdentityF64(listOf(0), mapOf(0 to 1e-16), Point2F32(0f, 0f)),
        )
        val malformedEnd = edge.copy(
            endIdentity = PathVertexIdentityF64(emptyList(), emptyMap(), Point2F32(1f, 0f)),
        )

        val startError = assertFailsWith<IllegalArgumentException> {
            splitPathEdgesF64(listOf(malformedStart), PathOpsLimitsI32())
        }
        val endError = assertFailsWith<IllegalArgumentException> {
            splitPathEdgesF64(listOf(malformedEnd), PathOpsLimitsI32())
        }

        assertEquals("path-edge-start-identity", startError.message)
        assertEquals("path-edge-end-identity", endError.message)
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
    fun `collinear segments separated by one e minus sixteen remain disjoint`() {
        assertNull(
            intersectPathEdgesF64(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
                inputEdgeF64(1, Point2F64(-1.0, 0.0), Point2F64(-1e-16, 0.0)),
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
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)).copy(windingDelta = 2)
        val second = inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0), PathOperand.SECOND).copy(windingDelta = -3)
        val split = splitPathEdgesF64(
            listOf(first, second),
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
        assertEquals(
            listOf(
                Triple(0, PathOperand.FIRST, 2),
                Triple(0, PathOperand.FIRST, 2),
                Triple(1, PathOperand.SECOND, -3),
                Triple(1, PathOperand.SECOND, -3),
            ),
            split.map { Triple(it.sourceId, it.operand, it.windingDelta) },
        )
        val overlapStart = split.single { it.sourceId == 0 && it.end == Point2F64(4.0, 0.0) }.endIdentity
        val overlapEnd = split.single { it.sourceId == 0 && it.end == Point2F64(10.0, 0.0) }.endIdentity
        assertEquals(overlapStart, split.single { it.sourceId == 1 && it.start == Point2F64(4.0, 0.0) }.startIdentity)
        assertEquals(overlapEnd, split.single { it.sourceId == 1 && it.end == Point2F64(10.0, 0.0) }.endIdentity)
        assertEquals(PathVertexIdentityF64(listOf(0, 1), mapOf(0 to 0.4, 1 to 0.0), Point2F32(4f, 0f)), overlapStart)
        assertEquals(PathVertexIdentityF64(listOf(0, 1), mapOf(0 to 1.0, 1 to 0.75), Point2F32(10f, 0f)), overlapEnd)
        assertTrue(split.all { it.start != it.end })
    }

    @Test
    fun `splitting has the same canonical result after input permutation`() {
        val edges = listOf(
            inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
            inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0), PathOperand.SECOND),
            inputEdgeF64(2, Point2F64(6.0, -2.0), Point2F64(6.0, 2.0), PathOperand.SECOND),
        )

        val ordered = canonicalSplitEdgesF64(splitPathEdgesF64(edges, PathOpsLimitsI32()))
        val permuted = canonicalSplitEdgesF64(splitPathEdgesF64(listOf(edges[2], edges[0], edges[1]), PathOpsLimitsI32()))

        assertEquals(ordered, permuted)
    }

    @Test
    fun `nearby independent crossings retain distinct identities`() {
        val firstCenter = Point2F64(0.0, 0.0)
        val secondCenter = Point2F64(1e-16, 0.0)
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(-2e-17, 0.0), Point2F64(2e-17, 0.0)),
                inputEdgeF64(1, Point2F64(0.0, -2e-17), Point2F64(0.0, 2e-17)),
                inputEdgeF64(2, Point2F64(8e-17, 0.0), Point2F64(12e-17, 0.0)),
                inputEdgeF64(3, Point2F64(1e-16, -2e-17), Point2F64(1e-16, 2e-17)),
            ),
            PathOpsLimitsI32(),
        )
        val firstIdentities = identitiesAtPointF64(split, firstCenter)
        val secondIdentities = identitiesAtPointF64(split, secondCenter)

        assertEquals(1, firstIdentities.distinct().size)
        assertEquals(1, secondIdentities.distinct().size)
        assertEquals(listOf(0, 1), firstIdentities.first().incidentEdgeIds)
        assertEquals(listOf(2, 3), secondIdentities.first().incidentEdgeIds)
        assertTrue(firstIdentities.first() != secondIdentities.first())
    }

    @Test
    fun `splitting rejects more than the deterministic intersection limit`() {
        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(fourEdgesWithThreeDistinctCrossingsF64(), PathOpsLimitsI32(maxIntersections = 2))
        }

        assertEquals("path-intersection-limit", error.message)
    }

    @Test
    fun `intersection budget permits one large canonical crossing and rejects a distinct one`() {
        val concurrent = concurrentPathEdgesF64(64)

        val oneIntersection = splitPathEdgesF64(concurrent, PathOpsLimitsI32(maxIntersections = 1))
        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(
                concurrent + listOf(
                    inputEdgeF64(64, Point2F64(9.0, -1.0), Point2F64(11.0, 1.0)),
                    inputEdgeF64(65, Point2F64(9.0, 1.0), Point2F64(11.0, -1.0)),
                ),
                PathOpsLimitsI32(maxIntersections = 1),
            )
        }

        assertEquals(128, oneIntersection.size)
        assertEquals("path-intersection-limit", error.message)
    }

    @Test
    fun `intersection budget is applied after order independent canonical unions`() {
        val edges = numericallyConcurrentPathEdgesF64()
        val permutations = listOf(
            listOf(edges[0], edges[1], edges[2]),
            listOf(edges[0], edges[2], edges[1]),
            listOf(edges[1], edges[0], edges[2]),
            listOf(edges[1], edges[2], edges[0]),
            listOf(edges[2], edges[0], edges[1]),
            listOf(edges[2], edges[1], edges[0]),
        )

        val splitByPermutation = permutations.map { permutation ->
            splitPathEdgesF64(permutation, PathOpsLimitsI32(maxIntersections = 1))
        }
        val snapshot = canonicalSplitEdgesF64(splitByPermutation.first())

        splitByPermutation.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            val intersectionIdentities = split.flatMap { edge ->
                listOf(edge.startIdentity, edge.endIdentity)
            }.filter { it.incidentEdgeIds == listOf(0, 1, 2) }
            assertTrue(intersectionIdentities.isNotEmpty())
            assertEquals(1, intersectionIdentities.distinct().size)
        }
    }

    @Test
    fun `nontransitive ULP parameter chains have one canonical intersection in either order`() {
        val edges = nontransitivelyNumericallyConcurrentPathEdgesF64()
        val splitByPermutation = listOf(
            listOf(edges[0], edges[1], edges[2], edges[3]),
            listOf(edges[0], edges[1], edges[3], edges[2]),
        ).map { permutation ->
            splitPathEdgesF64(permutation, PathOpsLimitsI32(maxIntersections = 1))
        }
        val snapshot = canonicalSplitEdgesF64(splitByPermutation.first())

        splitByPermutation.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(mapOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), split.groupingBy { it.sourceId }.eachCount())
            val identities = split.flatMap { edge ->
                listOf(edge.startIdentity, edge.endIdentity)
            }.filter { it.incidentEdgeIds == listOf(0, 1, 2, 3) }
            assertEquals(8, identities.size)
            assertEquals(1, identities.distinct().size)
        }
    }

    @Test
    fun `shared endpoints fit an exact four half edge budget`() {
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0))
        val second = inputEdgeF64(1, Point2F64(1.0, 0.0), Point2F64(1.0, 1.0))

        val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32(maxHalfEdges = 4))

        assertEquals(
            listOf(
                Triple(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
                Triple(1, Point2F64(1.0, 0.0), Point2F64(1.0, 1.0)),
            ),
            split.map { Triple(it.sourceId, it.start, it.end) },
        )
        val sharedIdentities = identitiesAtPointF64(split, Point2F64(1.0, 0.0))
        assertEquals(1, sharedIdentities.distinct().size)
        assertEquals(listOf(0, 1), sharedIdentities.first().incidentEdgeIds)
        assertEquals(mapOf(0 to 1.0, 1 to 0.0), sharedIdentities.first().parameterByEdgeId)
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

private fun concurrentPathEdgesF64(count: Int): List<PathInputEdgeF64> = List(count) { index ->
    inputEdgeF64(
        index,
        Point2F64(-1.0, -index.toDouble()),
        Point2F64(1.0, index.toDouble()),
    )
}

private fun numericallyConcurrentPathEdgesF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(-171_000_000_000.0, -93_000_000_000.0)),
    inputEdgeF64(1, Point2F64(-150_000_000_000.0, -78_000_000_000.0), Point2F64(36_000_000_000.0, 16_000_000_000.0)),
    inputEdgeF64(2, Point2F64(-55_000_000_000.0, -11_000_000_000.0), Point2F64(-59_000_000_000.0, -51_000_000_000.0)),
)

private fun nontransitivelyNumericallyConcurrentPathEdgesF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(-171_000_000_000.0, -93_000_000_000.0)),
    inputEdgeF64(1, Point2F64(1_415_330_052_664.0, 1_622_017_207_416.0), Point2F64(-952_434_877_576.0, -1_036_324_355_144.0)),
    inputEdgeF64(2, Point2F64(-55_000_000_000.0, -11_000_000_000.0), Point2F64(-59_000_000_000.0, -51_000_000_000.0)),
    inputEdgeF64(3, Point2F64(-75_807_216_848.0, -42_008_068_428.0), Point2F64(3_681_567_830_732.0, 2_157_224_384_077.0)),
)

private data class SplitEdgeSnapshotF64(
    val sourceId: Int,
    val operand: PathOperand,
    val windingDelta: Int,
    val start: Point2F64,
    val end: Point2F64,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
)

private fun canonicalSplitEdgesF64(edges: List<PathSplitEdgeF64>): List<SplitEdgeSnapshotF64> = edges
    .sortedWith(
        compareBy<PathSplitEdgeF64> { it.sourceId }
            .thenBy { it.start.x }
            .thenBy { it.start.y }
            .thenBy { it.end.x }
            .thenBy { it.end.y },
    )
    .map { edge ->
        SplitEdgeSnapshotF64(
            sourceId = edge.sourceId,
            operand = edge.operand,
            windingDelta = edge.windingDelta,
            start = edge.start,
            end = edge.end,
            startIdentity = edge.startIdentity,
            endIdentity = edge.endIdentity,
        )
    }

private fun identitiesAtPointF64(edges: List<PathSplitEdgeF64>, point: Point2F64): List<PathVertexIdentityF64> = edges.flatMap { edge ->
    listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
}.filter { (candidate, _) -> candidate == point }.map { it.second }

private fun assertWithinFourUlpsF64(expected: Double, actual: Double) {
    assertTrue(abs(expected.toBits() - actual.toBits()) <= 4L, "Expected $expected within four ULPs, got $actual")
}
