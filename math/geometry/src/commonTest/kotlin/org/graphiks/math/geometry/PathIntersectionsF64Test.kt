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
    fun `equal exact witnesses resolve wide shared parameters to one canonical cut`() {
        val edges = eightF64ConcurrentPathEdgesF64()
        val values = edges.drop(1).map { edge ->
            assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges.first(), edge)).firstT
        }
        val expectedFirstParameter = values.minOrNull()!!

        assertTrue(values.maxOf { it.toBits() } - values.minOf { it.toBits() } > 16L)
        testedInputEdgeOrdersF64(edges).forEach { permutation ->
            val split = splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 32),
            )
            val identity = split.flatMap { edge -> listOf(edge.startIdentity, edge.endIdentity) }
                .filter { candidate -> candidate.incidentEdgeIds == (0..7).toList() }
                .distinct()
                .single()

            assertEquals(expectedFirstParameter, identity.parameterByEdgeId.getValue(0))
        }
    }

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
            startIdentityF64 = PathVertexIdentityF64(listOf(0), mapOf(0 to 1e-16), Point2F32(0f, 0f)),
        )
        val malformedEnd = edge.copy(
            endIdentityF64 = PathVertexIdentityF64(emptyList(), emptyMap(), Point2F32(1f, 0f)),
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
    fun `splitting retains a tangent endpoint contact`() {
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
                inputEdgeF64(1, Point2F64(1.0, 0.0), Point2F64(1.0, 1.0)),
            ),
            PathOpsLimitsI32(),
        )

        val contactIdentities = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (point, _) -> point == Point2F64(1.0, 0.0) }.map { it.second }

        assertEquals(2, split.size)
        assertEquals(1, contactIdentities.distinct().size)
        assertEquals(listOf(0, 1), contactIdentities.first().incidentEdgeIds)
    }

    @Test
    fun `splitting keeps a one e minus sixteen gap disjoint`() {
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
                inputEdgeF64(1, Point2F64(-1.0, 0.0), Point2F64(-1e-16, 0.0)),
            ),
            PathOpsLimitsI32(),
        )

        assertEquals(2, split.size)
        assertEquals(mapOf(0 to 1, 1 to 1), split.groupingBy { it.sourceId }.eachCount())
    }

    @Test
    fun `splitting retains collinear overlap boundaries`() {
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
                inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0)),
            ),
            PathOpsLimitsI32(),
        )

        assertEquals(4, split.size)
        assertEquals(mapOf(0 to 2, 1 to 2), split.groupingBy { it.sourceId }.eachCount())
        assertTrue(split.any { it.start == Point2F64(4.0, 0.0) || it.end == Point2F64(4.0, 0.0) })
        assertTrue(split.any { it.start == Point2F64(10.0, 0.0) || it.end == Point2F64(10.0, 0.0) })
    }

    @Test
    fun `splitting retains a proper crossing`() {
        val split = splitPathEdgesF64(
            listOf(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(2.0, 2.0)),
                inputEdgeF64(1, Point2F64(0.0, 2.0), Point2F64(2.0, 0.0)),
            ),
            PathOpsLimitsI32(),
        )

        val crossingIdentities = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (point, _) -> point == Point2F64(1.0, 1.0) }.map { it.second }

        assertEquals(4, split.size)
        assertEquals(mapOf(0 to 2, 1 to 2), split.groupingBy { it.sourceId }.eachCount())
        assertEquals(1, crossingIdentities.distinct().size)
        assertEquals(listOf(0, 1), crossingIdentities.first().incidentEdgeIds)
    }

    @Test
    fun `AABB keeps a one ULP endpoint contact recognized by the robust kernel`() {
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0))
        val second = inputEdgeF64(
            1,
            Point2F64(Double.fromBits(1.0.toBits() + 1L), 0.0),
            Point2F64(2.0, 0.0),
        )

        val direct = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))
        val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32())
        val contactIdentities = identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct()

        assertEquals(Point2F64(1.0, 0.0), direct.point)
        assertEquals(1.0, direct.firstT)
        assertEquals(0.0, direct.secondT)
        assertEquals(1, contactIdentities.size)
        assertEquals(listOf(0, 1), contactIdentities.single().incidentEdgeIds)
        assertEquals(mapOf(0 to 1.0, 1 to 0.0), contactIdentities.single().parameterByEdgeId)
    }

    @Test
    fun `AABB preserves kernel snap contacts across Y negative zero and exponent boundaries`() {
        val nextUpOne = Double.fromBits(1.0.toBits() + 1L)
        val nextAfterNegativeOne = -Double.fromBits(1.0.toBits() - 1L)
        val minimumNormal = Double.fromBits(0x0010_0000_0000_0000L)
        val nextMinimumNormal = Double.fromBits(minimumNormal.toBits() + 1L)
        val cases = listOf(
            Triple(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(0.0, 1.0)),
                inputEdgeF64(1, Point2F64(0.0, nextUpOne), Point2F64(0.0, 2.0)),
                Point2F64(0.0, 1.0),
            ),
            Triple(
                inputEdgeF64(0, Point2F64(-2.0, 0.0), Point2F64(-1.0, 0.0)),
                inputEdgeF64(1, Point2F64(nextAfterNegativeOne, 0.0), Point2F64(0.0, 0.0)),
                Point2F64(-1.0, 0.0),
            ),
            Triple(
                inputEdgeF64(0, Point2F64(-1.0, 0.0), Point2F64(-0.0, 0.0)),
                inputEdgeF64(1, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
                Point2F64(0.0, 0.0),
            ),
            Triple(
                inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(minimumNormal, 0.0)),
                inputEdgeF64(1, Point2F64(nextMinimumNormal, 0.0), Point2F64(minimumNormal * 2.0, 0.0)),
                Point2F64(minimumNormal, 0.0),
            ),
        )

        cases.forEach { (first, second, contact) ->
            val direct = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))
            val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32())
            val identities = identitiesAtPointF64(split, contact).distinct()

            assertEquals(contact, direct.point)
            assertEquals(1, identities.size)
            assertEquals(listOf(0, 1), identities.single().incidentEdgeIds)
        }
    }

    @Test
    fun `overlapping nonintersecting AABBs consume the candidate work budget in every order`() {
        val edges = overlappingAabbNonintersectingPathEdgesF64(count = 128)
        val orders = listOf(edges, edges.reversed(), edges.drop(47) + edges.take(47))
        val sufficientLimits = PathOpsLimitsI32(maxCandidateProbes = 1_000_000)
        val expected = canonicalSplitEdgesF64(splitPathEdgesF64(orders.first(), sufficientLimits))

        orders.forEach { order ->
            val error = assertFailsWith<IllegalStateException> {
                splitPathEdgesF64(order, PathOpsLimitsI32(maxCandidateProbes = 1))
            }
            assertEquals("path-candidate-limit", error.message)

            val split = splitPathEdgesF64(order, sufficientLimits)
            assertEquals(expected, canonicalSplitEdgesF64(split))
            assertEquals((0 until 128).toList(), split.map { it.sourceId })
        }
    }

    @Test
    fun `known non collinear endpoints retain their candidate work limit`() {
        val edges = openContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(1.0, 0.0),
                Point2F64(1.0, 1.0),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(edges, PathOpsLimitsI32(maxCandidateProbes = 1))
        }
        val split = splitPathEdgesF64(edges, PathOpsLimitsI32(maxCandidateProbes = 64))
        val shared = identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct().single()

        assertEquals("path-candidate-limit", error.message)
        assertEquals(listOf(0, 1), shared.incidentEdgeIds)
        assertEquals(mapOf(0 to 1.0, 1 to 0.0), shared.parameterByEdgeId)
    }

    @Test
    fun `known endpoint no op debits classification before examining its relation`() {
        val edges = openContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(1.0, 0.0),
                Point2F64(1.0, 1.0),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(edges, PathOpsLimitsI32(maxCandidateProbes = 9))
        }
        // Canonical input/component sorts are now preflighted before the no-op classifier runs;
        // retain a deliberately ample successful budget while the preceding tiny budget proves
        // that the unified ledger rejects before any relation can be published.
        val split = splitPathEdgesF64(edges, PathOpsLimitsI32(maxCandidateProbes = 64))
        val shared = identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct().single()

        assertEquals("path-candidate-limit", error.message)
        assertEquals(listOf(0, 1), shared.incidentEdgeIds)
        assertEquals(mapOf(0 to 1.0, 1 to 0.0), shared.parameterByEdgeId)
    }

    @Test
    fun `known endpoint identities do not consume a distinct intersection limit`() {
        val knownEndpointEdges = openContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(1.0, 0.0),
                Point2F64(1.0, 1.0),
            ),
        )
        val crossing = inputEdgeF64(2, Point2F64(0.5, -1.0), Point2F64(0.5, 1.0))

        val split = splitPathEdgesF64(
            knownEndpointEdges + crossing,
            PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 256),
        )

        assertEquals(listOf(0, 1), identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct().single().incidentEdgeIds)
        assertEquals(listOf(0, 2), identitiesAtPointF64(split, Point2F64(0.5, 0.0)).distinct().single().incidentEdgeIds)
    }

    @Test
    fun `mixed disjoint touching crossing and overlapping boxes retain canonical splits through permutations`() {
        val edges = listOf(
            inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
            inputEdgeF64(1, Point2F64(10.0, 0.0), Point2F64(10.0, 3.0)),
            inputEdgeF64(2, Point2F64(5.0, -2.0), Point2F64(5.0, 2.0)),
            inputEdgeF64(3, Point2F64(3.0, 0.0), Point2F64(7.0, 0.0)),
            inputEdgeF64(4, Point2F64(20.0, 20.0), Point2F64(24.0, 24.0)),
            inputEdgeF64(5, Point2F64(20.0, 21.0), Point2F64(24.0, 25.0)),
            inputEdgeF64(6, Point2F64(30.0, 0.0), Point2F64(32.0, 0.0)),
        )
        val splits = testedInputEdgeOrdersF64(edges).map { order ->
            splitPathEdgesF64(order, PathOpsLimitsI32())
        }
        val expected = canonicalSplitEdgesF64(splits.first())

        splits.forEach { split ->
            assertEquals(expected, canonicalSplitEdgesF64(split))
            assertEquals(listOf(0, 1), identitiesAtPointF64(split, Point2F64(10.0, 0.0)).distinct().single().incidentEdgeIds)
            assertEquals(listOf(0, 2, 3), identitiesAtPointF64(split, Point2F64(5.0, 0.0)).distinct().single().incidentEdgeIds)
            assertEquals(listOf(0, 3), identitiesAtPointF64(split, Point2F64(3.0, 0.0)).distinct().single().incidentEdgeIds)
            assertEquals(listOf(0, 3), identitiesAtPointF64(split, Point2F64(7.0, 0.0)).distinct().single().incidentEdgeIds)
        }
    }

    @Test
    fun `adjacent non collinear contour edges retain their known shared identity through permutations`() {
        val edges = openContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(1.0, 0.0),
                Point2F64(1.0, 1.0),
            ),
        )
        val splits = testedInputEdgeOrdersF64(edges).map { order -> splitPathEdgesF64(order, PathOpsLimitsI32()) }
        val expected = canonicalSplitEdgesF64(splits.first())

        splits.forEach { split ->
            val shared = identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct().single()
            assertEquals(expected, canonicalSplitEdgesF64(split))
            assertEquals(2, split.size)
            assertEquals(listOf(0, 1), shared.incidentEdgeIds)
            assertEquals(mapOf(0 to 1.0, 1 to 0.0), shared.parameterByEdgeId)
        }
    }

    @Test
    fun `nonadjacent self crossing contour edges still split through permutations`() {
        val edges = closedContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(2.0, 2.0),
                Point2F64(0.0, 2.0),
                Point2F64(2.0, 0.0),
            ),
        )
        val splits = testedInputEdgeOrdersF64(edges).map { order -> splitPathEdgesF64(order, PathOpsLimitsI32()) }
        val expected = canonicalSplitEdgesF64(splits.first())

        splits.forEach { split ->
            val crossing = identitiesAtPointF64(split, Point2F64(1.0, 1.0)).distinct().single()
            assertEquals(expected, canonicalSplitEdgesF64(split))
            assertEquals(mapOf(0 to 2, 1 to 1, 2 to 2, 3 to 1), split.groupingBy { it.sourceId }.eachCount())
            assertEquals(listOf(0, 2), crossing.incidentEdgeIds)
        }
    }

    @Test
    fun `collinear contiguous and reversed contour edges retain normal split semantics`() {
        val contiguous = openContourInputEdgesF64(
            listOf(
                Point2F64(0.0, 0.0),
                Point2F64(1.0, 0.0),
                Point2F64(2.0, 0.0),
            ),
        )
        val contact = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(contiguous[0], contiguous[1]))
        val contiguousSplits = testedInputEdgeOrdersF64(contiguous).map { order -> splitPathEdgesF64(order, PathOpsLimitsI32()) }
        val contiguousExpected = canonicalSplitEdgesF64(contiguousSplits.first())

        assertEquals(Point2F64(1.0, 0.0), contact.point)
        contiguousSplits.forEach { split ->
            assertEquals(contiguousExpected, canonicalSplitEdgesF64(split))
            assertEquals(2, split.size)
            assertEquals(listOf(0, 1), identitiesAtPointF64(split, Point2F64(1.0, 0.0)).distinct().single().incidentEdgeIds)
        }

        val reversed = closedContourInputEdgesF64(listOf(Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)))
        assertIs<PathIntersectionF64.OverlapF64>(intersectPathEdgesF64(reversed[0], reversed[1]))
        val reversedSplits = testedInputEdgeOrdersF64(reversed).map { order -> splitPathEdgesF64(order, PathOpsLimitsI32()) }
        val reversedExpected = canonicalSplitEdgesF64(reversedSplits.first())

        reversedSplits.forEach { split ->
            assertEquals(reversedExpected, canonicalSplitEdgesF64(split))
            assertEquals(mapOf(0 to 1, 1 to 1), split.groupingBy { it.sourceId }.eachCount())
        }
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
        val first = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)).copy(windingDeltaI32 = 2)
        val second = inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0), PathOperand.SECOND).copy(windingDeltaI32 = -3)
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
    fun `same direct signature completes the five edge canonical identity`() {
        val edges = sameDirectSignatureFiveEdgePathEdgesF64()
        val expectedParameters = mapOf(
            0 to 0.5,
            1 to 0.5,
            2 to Double.fromBits(1.0.toBits() - 20L),
            3 to 0.5,
            4 to Double.fromBits(1.0.toBits() - 20L),
        )

        testedInputEdgeOrdersF64(edges).forEach { permutation ->
            val split = splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 1),
            )
            val endpoints = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity.incidentEdgeIds == (0..4).toList() }

            assertEquals(10, split.size)
            assertEquals((0..4).associateWith { 2 }, split.groupingBy { it.sourceId }.eachCount())
            assertEquals(10, endpoints.size)
            assertEquals(setOf(Point2F64(0.0, 0.0)), endpoints.map { it.first }.toSet())
            assertEquals(1, endpoints.map { it.second }.distinct().size)
            assertEquals(expectedParameters, endpoints.first().second.parameterByEdgeId)
            assertNull(endpoints.first().second.originalPointF32)
        }
    }

    @Test
    fun `signed zero pseudo edge is topologically degenerate with canonical emitted geometry`() {
        val vertical = inputEdgeF64(1, Point2F64(0.0, -1.0), Point2F64(0.0, 1.0))
        val positiveZero = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(0.0, 0.0))
        val signedZero = inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(-0.0, 0.0))
        val limits = PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 4)

        val positiveSplit = splitPathEdgesF64(listOf(positiveZero, vertical), limits)
        val signedSplit = splitPathEdgesF64(listOf(signedZero, vertical), limits)
        val signedIntersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(signedZero, vertical))
        val signedEndpoints = signedSplit.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (point, _) -> point == Point2F64(0.0, 0.0) }

        assertEquals(canonicalSplitEdgesF64(positiveSplit), canonicalSplitEdgesF64(signedSplit))
        assertEquals(2, signedSplit.size)
        assertEquals(setOf(1), signedSplit.map { it.sourceId }.toSet())
        assertEquals(Point2F64(0.0, 0.0), signedIntersection.point)
        assertEquals(0.0, signedIntersection.firstT)
        assertEquals(0.5, signedIntersection.secondT)
        assertEquals(2, signedEndpoints.size)
        assertEquals(setOf(Point2F64(0.0, 0.0)), signedEndpoints.map { it.first }.toSet())
        assertEquals(1, signedEndpoints.map { it.second }.distinct().size)
        assertEquals(listOf(0, 1), signedEndpoints.first().second.incidentEdgeIds)
        assertEquals(mapOf(0 to 0.0, 1 to 0.5), signedEndpoints.first().second.parameterByEdgeId)
    }

    @Test
    fun `high degree concurrence charges candidate work before unbounded profile rebuilding`() {
        val edges = concurrentPathEdgesF64(64)
        val orders = listOf(
            edges,
            edges.reversed(),
            edges.drop(17) + edges.take(17),
        )
        val sufficientLimits = PathOpsLimitsI32(
            maxIntersections = 1,
            maxCandidateProbes = 1_000_000,
        )
        val expected = canonicalSplitEdgesF64(splitPathEdgesF64(orders.first(), sufficientLimits))

        orders.forEach { order ->
            val error = assertFailsWith<IllegalStateException> {
                splitPathEdgesF64(
                    order,
                    PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 20_000),
                )
            }

            assertEquals("path-candidate-limit", error.message)
        }
        orders.forEach { order ->
            val split = splitPathEdgesF64(order, sufficientLimits)
            val identities = split.flatMap { edge -> listOf(edge.startIdentity, edge.endIdentity) }
                .filter { identity -> identity.incidentEdgeIds == (0 until 64).toList() }

            assertEquals(expected, canonicalSplitEdgesF64(split))
            assertEquals(128, split.size)
            assertEquals(1, identities.distinct().size)
        }
    }

    @Test
    fun `candidate probe limit fails deterministically across input orders and source relabeling`() {
        listOf(
            sameDirectSignatureFiveEdgePathEdgesF64() to allIntPermutationsF64((0..4).toList()),
            conflictingUlpBridgePathEdgesF64() to allIntPermutationsF64((0..3).toList()),
        ).forEach { (edges, labelPermutations) ->
            labelPermutations.forEach { labels ->
                val relabeled = relabelPathEdgesF64(edges, labels)
                testedInputEdgeOrdersF64(relabeled).forEach { permutation ->
                    val error = assertFailsWith<IllegalStateException> {
                        splitPathEdgesF64(
                            permutation,
                            PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 1),
                        )
                    }

                    assertEquals("path-candidate-limit", error.message)
                }
            }
        }
    }

    @Test
    fun `sufficient candidate budget preserves atomic geometry through every source ID relabeling`() {
        val fiveEdges = sameDirectSignatureFiveEdgePathEdgesF64()
        val bridgeEdges = conflictingUlpBridgePathEdgesF64()
        val fiveSnapshot = canonicalGeometrySplitEdgesF64(
            splitPathEdgesF64(fiveEdges, PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 4_096)),
            fiveEdges.mapIndexed { index, edge -> edge.id to index }.toMap(),
        )
        val bridgeSnapshot = canonicalGeometrySplitEdgesF64(
            splitPathEdgesF64(bridgeEdges, PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 4_096)),
            bridgeEdges.mapIndexed { index, edge -> edge.id to index }.toMap(),
        )

        allIntPermutationsF64((0..4).toList()).forEach { labels ->
            val relabeled = relabelPathEdgesF64(fiveEdges, labels)
            val geometryBySourceId = labels.indices.associate { index -> labels[index] to index }
            testedInputEdgeOrdersF64(relabeled).forEach { permutation ->
                val split = splitPathEdgesF64(
                    permutation,
                    PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 4_096),
                )

                assertEquals(fiveSnapshot, canonicalGeometrySplitEdgesF64(split, geometryBySourceId))
            }
        }
        allIntPermutationsF64((0..3).toList()).forEach { labels ->
            val relabeled = relabelPathEdgesF64(bridgeEdges, labels)
            val geometryBySourceId = labels.indices.associate { index -> labels[index] to index }
            testedInputEdgeOrdersF64(relabeled).forEach { permutation ->
                val split = splitPathEdgesF64(
                    permutation,
                    PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 4_096),
                )

                assertEquals(bridgeSnapshot, canonicalGeometrySplitEdgesF64(split, geometryBySourceId))
            }
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

    @Test
    fun `numerically concurrent intersections fit exact half edge capacity in every order`() {
        val permutations = inputEdgePermutationsF64(nontransitivelyNumericallyConcurrentPathEdgesF64())

        listOf(16, 22).forEach { maxHalfEdges ->
            val splits = permutations.map { permutation ->
                splitPathEdgesF64(
                    permutation,
                    PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = maxHalfEdges),
                )
            }
            val snapshot = canonicalSplitEdgesF64(splits.first())

            splits.forEach { split ->
                assertEquals(snapshot, canonicalSplitEdgesF64(split))
                assertEquals(8, split.size)
                assertEquals(mapOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), split.groupingBy { it.sourceId }.eachCount())
            }
        }
    }

    @Test
    fun `direct ULP clusters preserve distinct crossings along one carrier`() {
        val edges = carrierWithDirectUlpChainCrossingsF64()
        val splits = inputEdgePermutationsF64(edges).map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 2, maxHalfEdges = 20),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())
        val firstPoint = Point2F64(Double.fromBits(0.5.toBits()), 0.0)
        val lastPoint = Point2F64(Double.fromBits(0.5.toBits() + 30L), 0.0)

        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(9, split.size)
            assertTrue(split.count { it.sourceId == 0 } >= 3)
            val firstIdentity = identitiesAtPointF64(split, firstPoint).distinct().single()
            val lastIdentity = identitiesAtPointF64(split, lastPoint).distinct().single()
            assertEquals(listOf(0, 1, 2), firstIdentity.incidentEdgeIds)
            assertEquals(listOf(0, 3), lastIdentity.incidentEdgeIds)
            assertTrue(firstIdentity != lastIdentity)
        }

        val error = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(
                edges,
                PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 20),
            )
        }

        assertEquals("path-intersection-limit", error.message)
    }

    @Test
    fun `eight F64 concurrent segments fit their exact final half edge capacity in every tested order`() {
        val center = Point2F64(0.0, 0.0)
        val edges = eightF64ConcurrentPathEdgesF64()
        val splits = testedInputEdgeOrdersF64(edges).map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 32),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())

        edges.forEach { edge ->
            assertEquals(0, OrientationPredicateF64.sign(edge.start, edge.end, center))
        }
        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(16, split.size)
            assertEquals((0..7).associateWith { 2 }, split.groupingBy { it.sourceId }.eachCount())
            val centerIdentities = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity.incidentEdgeIds == (0..7).toList() }
            assertEquals(16, centerIdentities.size)
            assertEquals(1, centerIdentities.map { it.second }.distinct().size)
            assertEquals(setOf(center), centerIdentities.map { it.first }.toSet())
        }
    }

    @Test
    fun `old eight edge exact concurrency uses its declared canonical center geometry`() {
        val center = Point2F64(-57_000_000_000.0, -31_000_000_000.0)
        val edges = oldEightF64ConcurrentPathEdgesF64()
        val splits = testedInputEdgeOrdersF64(edges).map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 32),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())

        edges.forEach { edge ->
            assertEquals(0, OrientationPredicateF64.sign(edge.start, edge.end, center))
        }
        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(16, split.size)
            assertEquals((0..7).associateWith { 2 }, split.groupingBy { it.sourceId }.eachCount())
            val centerEndpoints = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity.incidentEdgeIds == (0..7).toList() }
            assertEquals(16, centerEndpoints.size)
            assertEquals(1, centerEndpoints.map { it.second }.distinct().size)
            assertEquals(setOf(center), centerEndpoints.map { it.first }.toSet())
        }
    }

    @Test
    fun `coincident carriers cannot corroborate a transitive ULP chain`() {
        val edges = coincidentCarriersWithUlpChainCrossingsF64()
        val permutations = inputEdgePermutationsF64(edges)
        val splits = permutations.map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 4, maxHalfEdges = 24),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())
        val firstPoint = Point2F64(Double.fromBits(0.5.toBits()), 0.0)
        val lastPoint = Point2F64(Double.fromBits(0.5.toBits() + 30L), 0.0)

        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(12, split.size)
            assertEquals(mapOf(0 to 3, 1 to 3, 2 to 2, 3 to 2, 4 to 2), split.groupingBy { it.sourceId }.eachCount())
            val intersectionIdentities = split.flatMap { edge ->
                listOf(edge.startIdentity, edge.endIdentity)
            }.filter { it.incidentEdgeIds.size > 1 }.distinct()
            assertEquals(4, intersectionIdentities.size)

            val firstIdentity = identitiesAtPointF64(split, firstPoint).distinct().single()
            val lastIdentity = identitiesAtPointF64(split, lastPoint).distinct().single()
            assertEquals(listOf(0, 1, 2, 3), firstIdentity.incidentEdgeIds)
            assertEquals(listOf(0, 1, 4), lastIdentity.incidentEdgeIds)
            assertTrue(firstIdentity != lastIdentity)
        }

        permutations.forEach { permutation ->
            val error = assertFailsWith<IllegalStateException> {
                splitPathEdgesF64(
                    permutation,
                    PathOpsLimitsI32(maxIntersections = 3, maxHalfEdges = 24),
                )
            }
            assertEquals("path-intersection-limit", error.message)
        }
    }

    @Test
    fun `coincident carriers retain a bounded ULP cluster when the chain is visited first`() {
        val edges = chainFirstCoincidentCarriersWithUlpCrossingsF64()
        val permutations = inputEdgePermutationsF64(edges)
        val canonicalGeometry = canonicalGeometrySplitEdgesF64(
            splitPathEdgesF64(
                coincidentCarriersWithUlpChainCrossingsF64(),
                PathOpsLimitsI32(maxIntersections = 4, maxHalfEdges = 24),
            ),
            (0..4).associateWith { it },
        )
        val geometryBySourceId = mapOf(2 to 0, 3 to 1, 4 to 2, 0 to 3, 1 to 4)
        val splits = permutations.map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 4, maxHalfEdges = 24),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())
        val firstPoint = Point2F64(Double.fromBits(0.5.toBits()), 0.0)

        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(canonicalGeometry, canonicalGeometrySplitEdgesF64(split, geometryBySourceId))
            assertEquals(12, split.size)
            assertEquals(mapOf(0 to 2, 1 to 2, 2 to 3, 3 to 3, 4 to 2), split.groupingBy { it.sourceId }.eachCount())
            assertEquals(4, split.flatMap { edge -> listOf(edge.startIdentity, edge.endIdentity) }
                .filter { it.incidentEdgeIds.size > 1 }
                .distinct()
                .size)

            val firstIdentity = identitiesAtPointF64(split, firstPoint).distinct().single()
            val lastIdentity = split.flatMap { edge -> listOf(edge.startIdentity, edge.endIdentity) }
                .filter { identity -> identity.incidentEdgeIds.size > 1 && 1 in identity.incidentEdgeIds }
                .distinct()
                .single()
            val lastVertexPoints = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity == lastIdentity }
                .map { it.first }
                .toSet()
            assertEquals(listOf(0, 2, 3, 4), firstIdentity.incidentEdgeIds)
            assertTrue(1 in lastIdentity.incidentEdgeIds)
            assertTrue(firstIdentity != lastIdentity)
            assertEquals(1, lastVertexPoints.size)
        }

        permutations.forEach { permutation ->
            val error = assertFailsWith<IllegalStateException> {
                splitPathEdgesF64(
                    permutation,
                    PathOpsLimitsI32(maxIntersections = 3, maxHalfEdges = 24),
                )
            }
            assertEquals("path-intersection-limit", error.message)
        }
    }

    @Test
    fun `many near ULP clusters retain canonical cuts and exact limits`() {
        val edges = manyNearUlpCarrierCrossingsF64()
        val splits = testedInputEdgeOrdersF64(edges).map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 8, maxHalfEdges = 82),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())
        val middle = 0.5
        val middleBits = middle.toBits()

        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(41, split.size)
            assertEquals(
                buildMap {
                    put(0, 9)
                    (1..16).forEach { edgeId -> put(edgeId, 2) }
                },
                split.groupingBy { it.sourceId }.eachCount(),
            )

            val identities = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity.incidentEdgeIds.size > 1 }
                .groupBy { (_, identity) -> identity }
            assertEquals(8, identities.size)

            (0 until 8).forEach { clusterIndex ->
                val firstEdgeId = clusterIndex * 2 + 1
                val secondEdgeId = firstEdgeId + 1
                val expectedPoint = Point2F64(Double.fromBits(middleBits + clusterIndex * 30L), 0.0)
                val identity = identities.keys.single { candidate ->
                    candidate.incidentEdgeIds == listOf(0, firstEdgeId, secondEdgeId)
                }
                val endpoints = identities.getValue(identity)

                assertEquals(setOf(expectedPoint), endpoints.map { it.first }.toSet())
                assertEquals(expectedPoint.x, identity.parameterByEdgeId.getValue(0))
                assertEquals(0.5, identity.parameterByEdgeId.getValue(firstEdgeId))
                assertEquals(0.5, identity.parameterByEdgeId.getValue(secondEdgeId))
            }
        }

        val intersectionError = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(edges, PathOpsLimitsI32(maxIntersections = 7, maxHalfEdges = 82))
        }
        assertEquals("path-intersection-limit", intersectionError.message)
    }

    @Test
    fun `conflicting ULP bridges close as one canonical atomic intersection`() {
        val edges = conflictingUlpBridgePathEdgesF64()
        val first = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges[0], edges[2]))
        val second = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges[1], edges[3]))
        val bridge = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges[2], edges[3]))
        val expectedIdentity = PathVertexIdentityF64(
            incidentEdgeIds = listOf(0, 1, 2, 4),
            parameterByEdgeId = mapOf(
                0 to first.firstT,
                1 to second.firstT,
                2 to bridge.firstT,
                4 to second.secondT,
            ),
            originalPointF32 = edges[0].startIdentity.originalPointF32,
        )
        val splits = testedInputEdgeOrdersF64(edges).map { permutation ->
            splitPathEdgesF64(
                permutation,
                PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 14),
            )
        }
        val snapshot = canonicalSplitEdgesF64(splits.first())

        splits.forEach { split ->
            assertEquals(snapshot, canonicalSplitEdgesF64(split))
            assertEquals(7, split.size)
            assertEquals(mapOf(0 to 1, 1 to 2, 2 to 2, 4 to 2), split.groupingBy { it.sourceId }.eachCount())
            val identities = split.flatMap { edge ->
                listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
            }.filter { (_, identity) -> identity.incidentEdgeIds.size > 1 }
                .groupBy { (_, identity) -> identity }

            assertEquals(setOf(expectedIdentity), identities.keys)
            val endpoints = identities.getValue(expectedIdentity)
            assertEquals(setOf(second.point), endpoints.map { it.first }.toSet())
            assertEquals(1, endpoints.map { it.second }.distinct().size)
        }

    }

    @Test
    fun `proper split cuts retain kernel parameters at one canonical vertex`() {
        val center = 1.5
        val ulp = Double.fromBits(center.toBits() + 1L) - center
        fun local(x: Double, y: Double): Point2F64 = Point2F64(center + x * ulp, center + y * ulp)

        val first = inputEdgeF64(0, local(-4.0, -4.0), local(0.0, 1.0))
        val second = inputEdgeF64(1, local(-4.0, -1.0), local(1.0, 1.0))
        val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))
        val split = splitPathEdgesF64(listOf(first, second), PathOpsLimitsI32(maxHalfEdges = 8))
        val centralEndpoints = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (_, identity) -> identity.incidentEdgeIds == listOf(0, 1) }

        assertEquals(Point2F64(center, center), intersection.point)
        assertEquals(15.0 / 17.0, intersection.firstT)
        assertEquals(12.0 / 17.0, intersection.secondT)
        assertEquals(4, centralEndpoints.size)
        assertEquals(setOf(intersection.point), centralEndpoints.map { it.first }.toSet())
        assertEquals(1, centralEndpoints.map { it.second }.distinct().size)
        assertEquals(
            mapOf(0 to 15.0 / 17.0, 1 to 12.0 / 17.0),
            centralEndpoints.first().second.parameterByEdgeId,
        )
    }

    @Test
    fun `distinct exact witnesses sharing an F64 projection remain separate intersections`() {
        val edges = roundedWitnessProjectionCollisionPathEdgesF64()
        val firstWitness = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges[0], edges[1]))
        val secondWitness = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(edges[2], edges[3]))

        assertEquals(firstWitness.point, secondWitness.point)
        assertNull(intersectPathEdgesF64(edges[0], edges[2]))
        assertNull(intersectPathEdgesF64(edges[0], edges[3]))
        assertNull(intersectPathEdgesF64(edges[1], edges[2]))
        assertNull(intersectPathEdgesF64(edges[1], edges[3]))

        val limitError = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(edges, PathOpsLimitsI32(maxIntersections = 1, maxHalfEdges = 16))
        }
        val split = splitPathEdgesF64(edges, PathOpsLimitsI32(maxIntersections = 2, maxHalfEdges = 16))
        val identitiesByIncidentEdges = split.flatMap { edge ->
            listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
        }.filter { (_, identity) -> identity.incidentEdgeIds.size > 1 }
            .groupBy { (_, identity) -> identity.incidentEdgeIds }

        assertEquals("path-intersection-limit", limitError.message)
        assertEquals(8, split.size)
        assertEquals(setOf(listOf(0, 1), listOf(2, 3)), identitiesByIncidentEdges.keys)
        assertEquals(
            mapOf(0 to 15.0 / 17.0, 1 to 12.0 / 17.0),
            identitiesByIncidentEdges.getValue(listOf(0, 1)).first().second.parameterByEdgeId,
        )
        assertEquals(
            mapOf(2 to 3.0 / 5.0, 3 to 2.0 / 5.0),
            identitiesByIncidentEdges.getValue(listOf(2, 3)).first().second.parameterByEdgeId,
        )
        identitiesByIncidentEdges.values.forEach { endpoints ->
            assertEquals(1, endpoints.map { it.first }.toSet().size)
            assertEquals(1, endpoints.map { it.second }.distinct().size)
        }
    }

    @Test
    fun `a dense grid retains every exact crossing at its final half edge capacity`() {
        val linesPerDirection = 16
        val intersectionCount = linesPerDirection * linesPerDirection
        val splitEdgeCount = linesPerDirection * 2 * (linesPerDirection + 1)
        val edges = denseGridPathEdgesF64(linesPerDirection)
        val split = splitPathEdgesF64(
            edges,
            PathOpsLimitsI32(maxIntersections = intersectionCount, maxHalfEdges = splitEdgeCount * 2),
        )
        val limitError = assertFailsWith<IllegalStateException> {
            splitPathEdgesF64(
                edges,
                PathOpsLimitsI32(maxIntersections = intersectionCount - 1, maxHalfEdges = splitEdgeCount * 2),
            )
        }

        assertEquals(splitEdgeCount, split.size)
        assertEquals((0 until linesPerDirection * 2).associateWith { linesPerDirection + 1 }, split.groupingBy { it.sourceId }.eachCount())
        assertEquals(
            intersectionCount,
            split.flatMap { edge -> listOf(edge.startIdentity, edge.endIdentity) }
                .filter { identity -> identity.incidentEdgeIds.size == 2 }
                .distinct()
                .size,
        )
        assertEquals("path-intersection-limit", limitError.message)
    }

}

private fun inputEdgeF64(
    id: Int,
    start: Point2F64,
    end: Point2F64,
    operand: PathOperand = PathOperand.FIRST,
): PathInputEdgeF64 = PathInputEdgeF64(
    idI32 = id,
    operand = operand,
    contourIndexI32 = 0,
    sourceSegmentIndexI32 = 0,
    sourceStartParameterF64 = 0.0,
    sourceEndParameterF64 = 1.0,
    startIdentityF64 = PathVertexIdentityF64(listOf(id), mapOf(id to 0.0), Point2F32(start.x.toFloat(), start.y.toFloat())),
    endIdentityF64 = PathVertexIdentityF64(listOf(id), mapOf(id to 1.0), Point2F32(end.x.toFloat(), end.y.toFloat())),
    startPointF64 = start,
    endPointF64 = end,
    windingDeltaI32 = 1,
)

private fun openContourInputEdgesF64(points: List<Point2F64>): List<PathInputEdgeF64> {
    require(points.size >= 2)
    val identities = points.indices.map { vertexIndex ->
        val incidentEdgeIds = when (vertexIndex) {
            0 -> listOf(0)
            points.lastIndex -> listOf(points.lastIndex - 1)
            else -> listOf(vertexIndex - 1, vertexIndex)
        }
        PathVertexIdentityF64(
            incidentEdgeIds = incidentEdgeIds,
            parameterByEdgeId = incidentEdgeIds.associateWith { edgeId -> if (edgeId == vertexIndex) 0.0 else 1.0 },
            originalPointF32 = Point2F32(points[vertexIndex].x.toFloat(), points[vertexIndex].y.toFloat()),
        )
    }
    return points.zipWithNext().mapIndexed { edgeIndex, (start, end) ->
        PathInputEdgeF64(
            idI32 = edgeIndex,
            operand = PathOperand.FIRST,
            contourIndexI32 = 0,
            sourceSegmentIndexI32 = edgeIndex,
            sourceStartParameterF64 = 0.0,
            sourceEndParameterF64 = 1.0,
            startIdentityF64 = identities[edgeIndex],
            endIdentityF64 = identities[edgeIndex + 1],
            startPointF64 = start,
            endPointF64 = end,
            windingDeltaI32 = 1,
        )
    }
}

private fun closedContourInputEdgesF64(points: List<Point2F64>): List<PathInputEdgeF64> {
    require(points.size >= 2)
    val identities = points.indices.map { vertexIndex ->
        val previousEdgeId = if (vertexIndex == 0) points.lastIndex else vertexIndex - 1
        val nextEdgeId = vertexIndex
        val incidentEdgeIds = listOf(previousEdgeId, nextEdgeId).sorted()
        PathVertexIdentityF64(
            incidentEdgeIds = incidentEdgeIds,
            parameterByEdgeId = mapOf(previousEdgeId to 1.0, nextEdgeId to 0.0),
            originalPointF32 = Point2F32(points[vertexIndex].x.toFloat(), points[vertexIndex].y.toFloat()),
        )
    }
    return points.indices.map { edgeIndex ->
        PathInputEdgeF64(
            idI32 = edgeIndex,
            operand = PathOperand.FIRST,
            contourIndexI32 = 0,
            sourceSegmentIndexI32 = edgeIndex,
            sourceStartParameterF64 = 0.0,
            sourceEndParameterF64 = 1.0,
            startIdentityF64 = identities[edgeIndex],
            endIdentityF64 = identities[(edgeIndex + 1) % points.size],
            startPointF64 = points[edgeIndex],
            endPointF64 = points[(edgeIndex + 1) % points.size],
            windingDeltaI32 = 1,
        )
    }
}

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

private fun sameDirectSignatureFiveEdgePathEdgesF64(): List<PathInputEdgeF64> {
    val middle = 0.5
    val ulp = Double.fromBits(middle.toBits() + 1L) - middle
    val y = 40.0 * ulp
    val fifthT = Double.fromBits(middle.toBits() + 15L)
    val fifthX = 2.0 * fifthT - 1.0
    val nearZero = Double.MIN_VALUE
    return listOf(
        inputEdgeF64(0, Point2F64(-0.5, y), Point2F64(0.5, y)),
        inputEdgeF64(1, Point2F64(-1.0, 0.0), Point2F64(1.0, 0.0)),
        inputEdgeF64(2, Point2F64(0.0, -1.0), Point2F64(0.0, y / 2.0)),
        inputEdgeF64(3, Point2F64(nearZero, -2.0), Point2F64(nearZero, 2.0)),
        inputEdgeF64(4, Point2F64(fifthX, -1.0), Point2F64(fifthX, y / 2.0)),
    )
}

private fun carrierWithDirectUlpChainCrossingsF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    inputEdgeF64(1, Point2F64(Double.fromBits(0.5.toBits()), -1.0), Point2F64(Double.fromBits(0.5.toBits()), 1.0)),
    inputEdgeF64(2, Point2F64(Double.fromBits(0.5.toBits() + 15L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 15L), 1.0)),
    inputEdgeF64(3, Point2F64(Double.fromBits(0.5.toBits() + 30L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 30L), 1.0)),
)

private fun eightF64ConcurrentPathEdgesF64(): List<PathInputEdgeF64> = listOf(
    exactConcurrentInputEdgeF64(0, 2_275_535_826_587.0, 1_592_878_575_529.0),
    exactConcurrentInputEdgeF64(1, 1_893_538_709_832.0, 1_321_951_988_864.0),
    exactConcurrentInputEdgeF64(2, 2_863_538_957_285.0, 2_014_034_134_915.0),
    exactConcurrentInputEdgeF64(3, 1_526_851_020_400.0, 1_092_354_929_865.0),
    exactConcurrentInputEdgeF64(4, 1_963_627_654_428.0, 1_441_821_573_300.0),
    exactConcurrentInputEdgeF64(5, 2_744_320_922_812.0, 2_085_393_519_275.0),
    exactConcurrentInputEdgeF64(6, 1_500_530_391_888.0, 1_187_711_768_496.0),
    exactConcurrentInputEdgeF64(7, 1_069_227_830_468.0, 887_097_502_517.0),
)

private fun oldEightF64ConcurrentPathEdgesF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(-427_127_110_378.0, -290_088_570_126.0), Point2F64(1_848_408_716_209.0, 1_302_784_005_403.0)),
    inputEdgeF64(1, Point2F64(-1_008_828_116_934.0, -695_507_709_143.0), Point2F64(884_710_595_898.0, 626_444_279_721.0)),
    inputEdgeF64(2, Point2F64(-1_228_379_463_033.0, -854_875_023_127.0), Point2F64(1_635_159_494_252.0, 1_159_159_112_788.0)),
    inputEdgeF64(3, Point2F64(-727_220_864_160.0, -510_497_673_046.0), Point2F64(799_630_156_240.0, 581_860_906_819.0)),
    inputEdgeF64(4, Point2F64(-1_229_384_540_415.0, -891_840_047_125.0), Point2F64(734_243_114_013.0, 549_981_526_175.0)),
    inputEdgeF64(5, Point2F64(-2_013_840_513_390.0, -1_517_571_079_105.0), Point2F64(730_484_049_422.0, 567_235_270_129.0)),
    inputEdgeF64(6, Point2F64(-424_434_395_539.0, -321_834_599_613.0), Point2F64(1_076_095_996_349.0, 865_877_168_883.0)),
    inputEdgeF64(7, Point2F64(-847_250_316_948.0, -686_643_546_637.0), Point2F64(221_972_843_520.0, 200_454_186_880.0)),
)

private fun roundedWitnessProjectionCollisionPathEdgesF64(): List<PathInputEdgeF64> {
    val center = 1.5
    val ulp = Double.fromBits(center.toBits() + 1L) - center
    fun local(x: Double, y: Double): Point2F64 = Point2F64(center + x * ulp, center + y * ulp)
    return listOf(
        inputEdgeF64(0, local(-4.0, -4.0), local(0.0, 1.0)),
        inputEdgeF64(1, local(-4.0, -1.0), local(1.0, 1.0)),
        inputEdgeF64(2, local(-3.0, -4.0), local(2.0, 2.0)),
        inputEdgeF64(3, local(-2.0, -2.0), local(3.0, 2.0)),
    )
}

private fun denseGridPathEdgesF64(linesPerDirection: Int): List<PathInputEdgeF64> =
    List(linesPerDirection) { index ->
        inputEdgeF64(
            id = index,
            start = Point2F64(-1.0, index.toDouble()),
            end = Point2F64(linesPerDirection.toDouble(), index.toDouble()),
        )
    } + List(linesPerDirection) { index ->
        inputEdgeF64(
            id = linesPerDirection + index,
            start = Point2F64(index.toDouble(), -1.0),
            end = Point2F64(index.toDouble(), linesPerDirection.toDouble()),
        )
    }

private fun overlappingAabbNonintersectingPathEdgesF64(count: Int): List<PathInputEdgeF64> = List(count) { index ->
    val offset = index.toDouble() / (count * 2).toDouble()
    inputEdgeF64(
        id = index,
        start = Point2F64(0.0, offset),
        end = Point2F64(1.0, 1.0 + offset),
    )
}

private fun exactConcurrentInputEdgeF64(id: Int, directionX: Double, directionY: Double): PathInputEdgeF64 {
    val startScale = (id + 1).toDouble()
    val endScale = (id + 3).toDouble()
    return inputEdgeF64(
        id,
        Point2F64(-startScale * directionX, -startScale * directionY),
        Point2F64(endScale * directionX, endScale * directionY),
    )
}

private fun coincidentCarriersWithUlpChainCrossingsF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    inputEdgeF64(1, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    inputEdgeF64(2, Point2F64(Double.fromBits(0.5.toBits()), -1.0), Point2F64(Double.fromBits(0.5.toBits()), 1.0)),
    inputEdgeF64(3, Point2F64(Double.fromBits(0.5.toBits() + 15L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 15L), 1.0)),
    inputEdgeF64(4, Point2F64(Double.fromBits(0.5.toBits() + 30L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 30L), 1.0)),
)

private fun chainFirstCoincidentCarriersWithUlpCrossingsF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(2, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    inputEdgeF64(3, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    inputEdgeF64(4, Point2F64(Double.fromBits(0.5.toBits()), -1.0), Point2F64(Double.fromBits(0.5.toBits()), 1.0)),
    inputEdgeF64(0, Point2F64(Double.fromBits(0.5.toBits() + 15L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 15L), 1.0)),
    inputEdgeF64(1, Point2F64(Double.fromBits(0.5.toBits() + 30L), -1.0), Point2F64(Double.fromBits(0.5.toBits() + 30L), 1.0)),
)

private fun conflictingUlpBridgePathEdgesF64(): List<PathInputEdgeF64> {
    val middle = 0.5
    val ulp = Double.fromBits(middle.toBits() + 1L) - middle
    val firstX = Double.fromBits(middle.toBits())
    val secondX = Double.fromBits(middle.toBits() + 15L)
    val upperY = 60.0 * ulp
    return listOf(
        inputEdgeF64(0, Point2F64(secondX, upperY), Point2F64(secondX + 1.0, upperY)),
        inputEdgeF64(1, Point2F64(firstX, -1.0), Point2F64(firstX, 34.0 * ulp)),
        inputEdgeF64(2, Point2F64(secondX, -2.0), Point2F64(secondX, 2.0)),
        inputEdgeF64(4, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    )
}

private fun manyNearUlpCarrierCrossingsF64(): List<PathInputEdgeF64> {
    val middle = 0.5
    val middleBits = middle.toBits()
    return listOf(
        inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(1.0, 0.0)),
    ) + List(16) { index ->
        val x = Double.fromBits(middleBits + index * 15L)
        inputEdgeF64(index + 1, Point2F64(x, -1.0), Point2F64(x, 1.0))
    }
}

private fun testedInputEdgeOrdersF64(edges: List<PathInputEdgeF64>): List<List<PathInputEdgeF64>> = (
    edges.indices.map { offset -> edges.drop(offset) + edges.take(offset) } +
        edges.indices.map { offset -> (edges.drop(offset) + edges.take(offset)).reversed() }
).distinctBy { order -> order.map { it.id } }

private fun inputEdgePermutationsF64(edges: List<PathInputEdgeF64>): List<List<PathInputEdgeF64>> = when {
    edges.isEmpty() -> listOf(emptyList())
    else -> edges.indices.flatMap { index ->
        val remaining = edges.toMutableList().also { it.removeAt(index) }
        inputEdgePermutationsF64(remaining).map { permutation -> listOf(edges[index]) + permutation }
    }
}

private fun allIntPermutationsF64(values: List<Int>): List<List<Int>> = when {
    values.isEmpty() -> listOf(emptyList())
    else -> values.indices.flatMap { index ->
        val remaining = values.toMutableList().also { it.removeAt(index) }
        allIntPermutationsF64(remaining).map { permutation -> listOf(values[index]) + permutation }
    }
}

private fun relabelPathEdgesF64(edges: List<PathInputEdgeF64>, labels: List<Int>): List<PathInputEdgeF64> {
    check(edges.size == labels.size)
    val sourceIds = edges.mapIndexed { index, edge -> edge.id to labels[index] }.toMap()
    return edges.mapIndexed { index, edge ->
        edge.copy(
            idI32 = labels[index],
            startIdentityF64 = relabelPathVertexIdentityF64(edge.startIdentity, sourceIds),
            endIdentityF64 = relabelPathVertexIdentityF64(edge.endIdentity, sourceIds),
        )
    }
}

private fun relabelPathVertexIdentityF64(
    identity: PathVertexIdentityF64,
    sourceIds: Map<Int, Int>,
): PathVertexIdentityF64 = PathVertexIdentityF64(
    incidentEdgeIds = identity.incidentEdgeIds.map(sourceIds::getValue).sorted(),
    parameterByEdgeId = identity.parameterByEdgeId.entries.associate { (sourceId, parameter) ->
        sourceIds.getValue(sourceId) to parameter
    },
    originalPointF32 = identity.originalPointF32,
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

private fun canonicalGeometrySplitEdgesF64(
    edges: List<PathSplitEdgeF64>,
    geometryBySourceId: Map<Int, Int>,
): List<SplitEdgeSnapshotF64> = canonicalSplitEdgesF64(
    edges.map { edge ->
        edge.copy(
            sourceId = geometryBySourceId.getValue(edge.sourceId),
            startIdentity = relabelPathVertexIdentityF64(edge.startIdentity, geometryBySourceId),
            endIdentity = relabelPathVertexIdentityF64(edge.endIdentity, geometryBySourceId),
        )
    },
)

private fun identitiesAtPointF64(edges: List<PathSplitEdgeF64>, point: Point2F64): List<PathVertexIdentityF64> = edges.flatMap { edge ->
    listOf(edge.start to edge.startIdentity, edge.end to edge.endIdentity)
}.filter { (candidate, _) -> candidate == point }.map { it.second }

private fun assertWithinFourUlpsF64(expected: Double, actual: Double) {
    assertTrue(abs(expected.toBits() - actual.toBits()) <= 4L, "Expected $expected within four ULPs, got $actual")
}
