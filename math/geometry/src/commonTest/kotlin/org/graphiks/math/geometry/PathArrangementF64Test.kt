package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PathArrangementF64Test {
    @Test
    fun `unary winding keeps a clockwise outer contour and counter clockwise hole`() {
        val contours = arrangementFromInputEdgesF64(squareWithHoleEdgesF64())
            .unaryBoundary(FillRule.WINDING)
        val reversedOuter = arrangementFromInputEdgesF64(
            closedContourEdgesF64(
                squarePointsF64(20.0, 0.0, 30.0, 10.0).reversed(),
                PathOperand.FIRST,
                20,
            ),
        ).unaryBoundary(FillRule.WINDING)
        val signedZeroOuter = arrangementFromInputEdgesF64(
            closedContourEdgesF64(
                listOf(
                    Point2F64(-0.0, -0.0),
                    Point2F64(10.0, -0.0),
                    Point2F64(10.0, 10.0),
                    Point2F64(-0.0, 10.0),
                ),
                PathOperand.FIRST,
                30,
            ),
        ).unaryBoundary(FillRule.WINDING)
        val result = projectContoursF64ToPathF32(contours)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 1f)))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(5f, 5f)))
        assertEquals(
            listOf(1, -1),
            contours.map { signedAreaF64(it.vertices.map { vertex -> vertex.point }).compareTo(0.0) },
        )
        assertTrue(contours.flatMap { it.vertices }.any { it.originalPointF32 == Point2F32(0f, 0f) })
        assertTrue(
            signedAreaF64(reversedOuter.single().vertices.map { vertex -> vertex.point }) > 0.0,
        )
        val signedZeroVertex = assertNotNull(
            signedZeroOuter.flatMap { contour -> contour.vertices }.firstOrNull {
                it.point == Point2F64(0.0, 0.0)
            },
        )
        val signedZeroProvenance = assertNotNull(signedZeroVertex.originalPointF32)
        assertEquals((-0.0f).toRawBits(), signedZeroProvenance.x.toRawBits())
        assertEquals((-0.0f).toRawBits(), signedZeroProvenance.y.toRawBits())
    }

    @Test
    fun `containment forest propagates winding through holes islands and disconnected roots`() {
        val input =
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 20.0, 20.0), PathOperand.FIRST, 0) +
                closedContourEdgesF64(
                    listOf(
                        Point2F64(4.0, 4.0),
                        Point2F64(4.0, 16.0),
                        Point2F64(16.0, 16.0),
                        Point2F64(16.0, 4.0),
                    ),
                    PathOperand.FIRST,
                    10,
                ) +
                closedContourEdgesF64(squarePointsF64(7.0, 7.0, 13.0, 13.0), PathOperand.FIRST, 20) +
                closedContourEdgesF64(squarePointsF64(30.0, 0.0, 40.0, 10.0), PathOperand.FIRST, 30)

        val contours = arrangementFromInputEdgesF64(input).unaryBoundary(FillRule.WINDING)
        val result = projectContoursF64ToPathF32(contours)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 1f)))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(5f, 5f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(8f, 8f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(35f, 5f)))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(25f, 5f)))
        assertEquals(
            listOf(1, -1, 1, 1),
            contours.map { signedAreaF64(it.vertices.map { vertex -> vertex.point }).compareTo(0.0) },
        )
        assertEquals(
            contourSnapshotF64(contours),
            contourSnapshotF64(arrangementFromInputEdgesF64(input.reversed()).unaryBoundary(FillRule.WINDING)),
        )
    }

    @Test
    fun `split self crossing contour yields two canonical filled lobes`() {
        val contours = arrangementFromInputEdgesF64(
            closedContourEdgesF64(
                listOf(
                    Point2F64(0.0, 0.0),
                    Point2F64(8.0, 8.0),
                    Point2F64(0.0, 8.0),
                    Point2F64(8.0, 0.0),
                ),
                PathOperand.FIRST,
                0,
            ),
        ).unaryBoundary(FillRule.WINDING)
        val result = projectContoursF64ToPathF32(contours)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(4f, 2f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(4f, 6f)))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(1f, 4f)))
        assertTrue(contours.all { signedAreaF64(it.vertices.map { vertex -> vertex.point }) > 0.0 })
    }

    @Test
    fun `coincident contours retain winding duplicates and cancel opposed contributions`() {
        val clockwise = squarePointsF64(0.0, 0.0, 10.0, 10.0)
        val counterClockwise = clockwise.reversed()
        val sameDirection = arrangementFromInputEdgesF64(
            closedContourEdgesF64(clockwise, PathOperand.FIRST, sourceIdStart = 0) +
                closedContourEdgesF64(clockwise, PathOperand.FIRST, sourceIdStart = 10),
        )
        val opposedDirection = arrangementFromInputEdgesF64(
            closedContourEdgesF64(clockwise, PathOperand.FIRST, sourceIdStart = 0) +
                closedContourEdgesF64(counterClockwise, PathOperand.FIRST, sourceIdStart = 10),
        )
        val opposedOperands = arrangementFromInputEdgesF64(
            closedContourEdgesF64(clockwise, PathOperand.FIRST, sourceIdStart = 0) +
                closedContourEdgesF64(counterClockwise, PathOperand.SECOND, sourceIdStart = 10),
        )

        val winding = projectContoursF64ToPathF32(sameDirection.unaryBoundary(FillRule.WINDING))
        val evenOdd = projectContoursF64ToPathF32(sameDirection.unaryBoundary(FillRule.EVEN_ODD))
        val cancelled = projectContoursF64ToPathF32(opposedDirection.unaryBoundary(FillRule.WINDING))
        val intersection = projectContoursF64ToPathF32(
            opposedOperands.boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.INTERSECT),
        )

        assertTrue(PathAnalysisF32.contains(winding, Point2F32(5f, 5f)))
        assertFalse(PathAnalysisF32.contains(evenOdd, Point2F32(5f, 5f)))
        assertFalse(PathAnalysisF32.contains(cancelled, Point2F32(5f, 5f)))
        assertTrue(PathAnalysisF32.contains(intersection, Point2F32(5f, 5f)))
    }

    @Test
    fun `difference inherits the weighted winding of a containing theta face`() {
        val split = splitPathEdgesF64(
            closedContourEdgesF64(
                trianglePointsF64(0.0, 0.0, 10.0, 0.0, 5.0, 10.0),
                PathOperand.FIRST,
                0,
            ) +
                closedContourEdgesF64(
                    trianglePointsF64(0.0, 0.0, 10.0, 0.0, 5.0, 6.0),
                    PathOperand.FIRST,
                    10,
                ) +
                closedContourEdgesF64(
                    squarePointsF64(4.0, 1.0, 6.0, 3.0),
                    PathOperand.SECOND,
                    20,
                ),
            PathOpsLimitsI32(),
        )

        val result = projectContoursF64ToPathF32(
            PathArrangementF64.build(split, PathOpsLimitsI32())
                .boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.DIFFERENCE),
        )

        assertFalse(PathAnalysisF32.contains(result, Point2F32(5f, 2f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(3f, 5f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(5f, 8f)))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(11f, 2f)))
    }

    @Test
    fun `canonical contour retains an exact nonzero area after rounded shoelace cancellation`() {
        val first = Point2F64(-0.49999999999999445, -0.49999999999999445)
        val second = Point2F64(0.49999999999999445, 0.4999999999999951)
        val third = Point2F64(0.4999999999999999, 0.5000000000000006)
        val inputPoints = listOf(first, second, third)

        assertEquals(-1, signedAreaSignF64(inputPoints + inputPoints.first()))
        assertEquals(0.0, signedAreaF64(inputPoints))

        val contour = PathArrangementF64.build(
            splitPathEdgesF64(
                closedContourEdgesF64(inputPoints, PathOperand.FIRST, 0),
                PathOpsLimitsI32(),
            ),
            PathOpsLimitsI32(),
        ).unaryBoundary(FillRule.WINDING).single()
        val outputPoints = contour.vertices.map { it.point }

        assertEquals(3, outputPoints.size)
        assertEquals(inputPoints.toSet(), outputPoints.toSet())
        assertEquals(1, signedAreaSignF64(outputPoints + outputPoints.first()))
        assertEquals(first.x, outputPoints.minOf { it.x })
        assertEquals(third.x, outputPoints.maxOf { it.x })
        assertEquals(first.y, outputPoints.minOf { it.y })
        assertEquals(third.y, outputPoints.maxOf { it.y })
    }

    @Test
    fun `all boolean operations follow their truth tables after canonical splitting`() {
        val first = trianglePointsF64(0.0, 0.0, 8.0, 0.0, 4.0, 8.0)
        val second = trianglePointsF64(2.0, -1.0, 10.0, -1.0, 6.0, 7.0)

        assertBooleanTruthTablesF64(first, second)
        assertBooleanTruthTablesF64(first.reversed(), second.reversed())
    }

    @Test
    fun `touching and collinear contours remain geometrically valid after splitting`() {
        val pointTouch = arrangementFromInputEdgesF64(
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 4.0, 4.0), PathOperand.FIRST, 0) +
                closedContourEdgesF64(squarePointsF64(4.0, 4.0, 8.0, 8.0), PathOperand.SECOND, 10),
        ).boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.UNION)
        val sharedEdge = arrangementFromInputEdgesF64(
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 4.0, 4.0), PathOperand.FIRST, 0) +
                closedContourEdgesF64(squarePointsF64(4.0, 0.0, 8.0, 4.0), PathOperand.SECOND, 10),
        ).boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.UNION)
        val partialOverlap = arrangementFromInputEdgesF64(
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 6.0, 4.0), PathOperand.FIRST, 0) +
                closedContourEdgesF64(squarePointsF64(3.0, 0.0, 9.0, 4.0), PathOperand.SECOND, 10),
        ).boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.UNION)
        val tJunction = arrangementFromInputEdgesF64(
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 8.0, 4.0), PathOperand.FIRST, 0) +
                closedContourEdgesF64(squarePointsF64(2.0, 4.0, 6.0, 8.0), PathOperand.SECOND, 10),
        ).boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.UNION)

        assertContourGeometryF64(
            pointTouch,
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            Point2F32(2f, 2f),
            Point2F32(6f, 6f),
        )
        assertContourGeometryF64(
            sharedEdge,
            RectF32.ofLTRB(0f, 0f, 8f, 4f),
            Point2F32(2f, 2f),
            Point2F32(6f, 2f),
        )
        assertContourGeometryF64(
            partialOverlap,
            RectF32.ofLTRB(0f, 0f, 9f, 4f),
            Point2F32(1f, 2f),
            Point2F32(8f, 2f),
        )
        assertContourGeometryF64(
            tJunction,
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            Point2F32(4f, 2f),
            Point2F32(4f, 6f),
        )
        assertFalse(PathAnalysisF32.contains(projectContoursF64ToPathF32(tJunction), Point2F32(1f, 7f)))
    }

    @Test
    fun `canonical contours are deterministic across split edge order`() {
        val inputs = closedContourEdgesF64(squarePointsF64(0.0, 0.0, 8.0, 8.0), PathOperand.FIRST, 0) +
            closedContourEdgesF64(squarePointsF64(4.0, 0.0, 12.0, 8.0), PathOperand.SECOND, 10)

        val ordered = arrangementFromInputEdgesF64(inputs)
            .boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.XOR)
        val reversed = arrangementFromInputEdgesF64(inputs.reversed())
            .boundary(FillRule.WINDING, FillRule.WINDING, PathBooleanOp.XOR)

        assertEquals(contourSnapshotF64(ordered), contourSnapshotF64(reversed))
    }

    @Test
    fun `arrangement limits and inconsistent identity geometry fail deterministically`() {
        val square = splitPathEdgesF64(
            closedContourEdgesF64(squarePointsF64(0.0, 0.0, 4.0, 4.0), PathOperand.FIRST, 0),
            PathOpsLimitsI32(),
        )

        val vertexError = assertFailsWith<IllegalStateException> {
            PathArrangementF64.build(square, PathOpsLimitsI32(maxVertices = 3))
        }
        val halfEdgeError = assertFailsWith<IllegalStateException> {
            PathArrangementF64.build(square, PathOpsLimitsI32(maxHalfEdges = 7))
        }
        val independentBudgets = PathArrangementF64.build(
            square,
            PathOpsLimitsI32(maxIntersections = 1, maxCandidateProbes = 1),
        ).unaryBoundary(FillRule.WINDING)

        val sharedIdentity = PathVertexIdentityF64(listOf(0, 1), mapOf(0 to 0.0, 1 to 1.0), Point2F32(0f, 0f))
        val inconsistent = listOf(
            PathSplitEdgeF64(
                sourceId = 0,
                operand = PathOperand.FIRST,
                startIdentity = sharedIdentity,
                endIdentity = PathVertexIdentityF64(listOf(0), mapOf(0 to 1.0), Point2F32(1f, 0f)),
                start = Point2F64(0.0, 0.0),
                end = Point2F64(1.0, 0.0),
                windingDelta = 1,
            ),
            PathSplitEdgeF64(
                sourceId = 1,
                operand = PathOperand.FIRST,
                startIdentity = PathVertexIdentityF64(listOf(1), mapOf(1 to 0.0), Point2F32(0f, 1f)),
                endIdentity = sharedIdentity,
                start = Point2F64(0.0, 1.0),
                end = Point2F64(0.0, 2.0),
                windingDelta = 1,
            ),
        )
        val inconsistentError = assertFailsWith<IllegalStateException> {
            PathArrangementF64.build(inconsistent, PathOpsLimitsI32())
        }
        val degenerateError = assertFailsWith<IllegalStateException> {
            PathArrangementF64.build(
                listOf(
                    square.first().copy(
                        endIdentity = square.first().startIdentity,
                        end = square.first().start,
                    ),
                ),
                PathOpsLimitsI32(),
            )
        }
        val openCycleError = assertFailsWith<IllegalStateException> {
            PathArrangementF64.build(listOf(square.first()), PathOpsLimitsI32())
        }

        assertEquals("path-vertex-limit", vertexError.message)
        assertEquals("path-half-edge-limit", halfEdgeError.message)
        assertEquals("path-arrangement-inconsistent", inconsistentError.message)
        assertEquals("path-arrangement-inconsistent", degenerateError.message)
        assertEquals("path-arrangement-inconsistent", openCycleError.message)
        assertTrue(
            PathAnalysisF32.contains(projectContoursF64ToPathF32(independentBudgets), Point2F32(2f, 2f)),
        )
    }

    private fun assertBooleanTruthTablesF64(first: List<Point2F64>, second: List<Point2F64>) {
        val arrangement = arrangementFromInputEdgesF64(
            closedContourEdgesF64(first, PathOperand.FIRST, sourceIdStart = 0) +
                closedContourEdgesF64(second, PathOperand.SECOND, sourceIdStart = 10),
        )
        val probes = listOf(
            BooleanProbeF64(Point2F32(1f, 1f), inFirst = true, inSecond = false),
            BooleanProbeF64(Point2F32(9f, 0f), inFirst = false, inSecond = true),
            BooleanProbeF64(Point2F32(5f, 2f), inFirst = true, inSecond = true),
            BooleanProbeF64(Point2F32(-1f, 5f), inFirst = false, inSecond = false),
        )

        listOf(FillRule.WINDING, FillRule.EVEN_ODD).forEach { fillRule ->
            PathBooleanOp.entries.forEach { operation ->
                val result = projectContoursF64ToPathF32(
                    arrangement.boundary(fillRule, fillRule, operation),
                )
                probes.forEach { probe ->
                    assertEquals(
                        expectedMembershipF64(operation, probe.inFirst, probe.inSecond),
                        PathAnalysisF32.contains(result, probe.point),
                        "$fillRule $operation at ${probe.point}",
                    )
                }
            }
        }
    }
}

private data class BooleanProbeF64(
    val point: Point2F32,
    val inFirst: Boolean,
    val inSecond: Boolean,
)

private fun arrangementFromInputEdgesF64(input: List<PathInputEdgeF64>): PathArrangementF64 {
    val split = splitPathEdgesF64(input, PathOpsLimitsI32())
    return PathArrangementF64.build(split, PathOpsLimitsI32())
}

private fun closedContourEdgesF64(
    points: List<Point2F64>,
    operand: PathOperand,
    sourceIdStart: Int,
): List<PathInputEdgeF64> {
    require(points.size >= 3)
    val ids = points.indices.map { sourceIdStart + it }
    val identities = points.indices.map { index ->
        val previous = ids[(index - 1 + ids.size) % ids.size]
        val next = ids[index]
        PathVertexIdentityF64(
            incidentEdgeIds = listOf(previous, next).sorted(),
            parameterByEdgeId = mapOf(previous to 1.0, next to 0.0),
            originalPointF32 = Point2F32(points[index].x.toFloat(), points[index].y.toFloat()),
        )
    }
    return points.indices.map { index ->
        PathInputEdgeF64(
            idI32 = ids[index],
            operand = operand,
            contourIndexI32 = sourceIdStart,
            sourceSegmentIndexI32 = index,
            sourceStartParameterF64 = 0.0,
            sourceEndParameterF64 = 1.0,
            startIdentityF64 = identities[index],
            endIdentityF64 = identities[(index + 1) % points.size],
            startPointF64 = points[index],
            endPointF64 = points[(index + 1) % points.size],
            windingDeltaI32 = 1,
        )
    }
}

private fun squareWithHoleEdgesF64(): List<PathInputEdgeF64> =
    closedContourEdgesF64(squarePointsF64(0.0, 0.0, 10.0, 10.0), PathOperand.FIRST, 0) +
        closedContourEdgesF64(
            listOf(
                Point2F64(3.0, 3.0),
                Point2F64(3.0, 7.0),
                Point2F64(7.0, 7.0),
                Point2F64(7.0, 3.0),
            ),
            PathOperand.FIRST,
            10,
        )

private fun squarePointsF64(left: Double, top: Double, right: Double, bottom: Double): List<Point2F64> = listOf(
    Point2F64(left, top),
    Point2F64(right, top),
    Point2F64(right, bottom),
    Point2F64(left, bottom),
)

private fun trianglePointsF64(
    firstX: Double,
    firstY: Double,
    secondX: Double,
    secondY: Double,
    thirdX: Double,
    thirdY: Double,
): List<Point2F64> = listOf(
    Point2F64(firstX, firstY),
    Point2F64(secondX, secondY),
    Point2F64(thirdX, thirdY),
)

private fun projectContoursF64ToPathF32(contours: List<PathContourF64>): PathF32 {
    val builder = PathBuilder(FillRule.WINDING)
    contours.forEach { contour ->
        val vertices = contour.vertices
        if (vertices.size < 3) return@forEach
        builder.moveTo(vertices.first().point.x.toFloat(), vertices.first().point.y.toFloat())
        vertices.drop(1).forEach { vertex -> builder.lineTo(vertex.point.x.toFloat(), vertex.point.y.toFloat()) }
        builder.close()
    }
    return builder.build()
}

private fun assertContourGeometryF64(
    contours: List<PathContourF64>,
    expectedBounds: RectF32,
    firstInside: Point2F32,
    secondInside: Point2F32,
) {
    val result = projectContoursF64ToPathF32(contours)
    assertTrue(PathAnalysisF32.contains(result, firstInside))
    assertTrue(PathAnalysisF32.contains(result, secondInside))
    assertEquals(expectedBounds, assertNotNull(PathAnalysisF32.bounds(result)))
    assertTrue(contours.all { abs(signedAreaF64(it.vertices.map { vertex -> vertex.point })) > 0.0 })
    contours.forEach { contour ->
        val points = contour.vertices.map { it.point }
        assertTrue(points.size >= 3)
        points.indices.forEach { index ->
            val previous = points[(index - 1 + points.size) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            assertFalse(current == next)
            assertFalse(
                OrientationPredicateF64.sign(previous, current, next) == 0 &&
                    PathPredicatesF64.onSegment(current, previous, next),
            )
        }
    }
}

private fun expectedMembershipF64(
    operation: PathBooleanOp,
    inFirst: Boolean,
    inSecond: Boolean,
): Boolean = when (operation) {
    PathBooleanOp.DIFFERENCE -> inFirst && !inSecond
    PathBooleanOp.INTERSECT -> inFirst && inSecond
    PathBooleanOp.UNION -> inFirst || inSecond
    PathBooleanOp.XOR -> inFirst != inSecond
    PathBooleanOp.REVERSE_DIFFERENCE -> inSecond && !inFirst
}

private fun signedAreaF64(points: List<Point2F64>): Double = points.indices.sumOf { index ->
    val first = points[index]
    val second = points[(index + 1) % points.size]
    first.x * second.y - first.y * second.x
} * 0.5

private fun contourSnapshotF64(contours: List<PathContourF64>): List<List<Pair<Point2F64, Point2F32?>>> =
    contours.map { contour -> contour.vertices.map { vertex -> vertex.point to vertex.originalPointF32 } }
