package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathOpsHybridTopologyF32Test {
    @Test
    fun `strict source parameter ULP boundary rejects the sixteenth step including signed zero`() {
        val oneF64 = 1.0
        val fifteenUlpsF64 = Double.fromBits(oneF64.toRawBits() + 15L)
        val sixteenUlpsF64 = Double.fromBits(oneF64.toRawBits() + 16L)

        assertTrue(PathPredicatesF64.almostEqualUlps(oneF64, fifteenUlpsF64, maxUlps = 16, nearZeroMaxUlps = 0))
        assertFalse(PathPredicatesF64.almostEqualUlps(oneF64, sixteenUlpsF64, maxUlps = 16, nearZeroMaxUlps = 0))
        assertTrue(PathPredicatesF64.almostEqualUlps(-0.0, 0.0, maxUlps = 16, nearZeroMaxUlps = 0))
        assertFalse(
            PathPredicatesF64.almostEqualUlps(
                0.0,
                Double.fromBits(16L),
                maxUlps = 16,
                nearZeroMaxUlps = 0,
            ),
        )
    }

    @Test
    fun `hybrid simplify preserves a flattened quadratic carrier instead of its endpoint chord`() {
        val curved = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 2f, 2f, 0f)
            .lineTo(2f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()

        val result = PathOpsF32.simplify(curved)

        // The quadratic reaches y = 1 at x = 1, so this literal probe is inside the source
        // region but would be outside the forbidden endpoint chord from (0, 0) to (2, 0).
        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, .5f)))
        assertEquals(false, PathAnalysisF32.contains(result, Point2F32(1f, 1.25f)))
    }

    @Test
    fun `point witness permits only its two local projected branches through the hybrid arrangement`() {
        // These two F64 rays share one exact source event, while their other endpoints remain
        // distinct F32 values.  The public result must therefore retain the local junction
        // without granting it authority over a remote projected endpoint.
        val e = 2.0.pow(-22)
        val lower = normalizedContourF64(
            0.0 to 1.0,
            1.0 to 1.0 - e,
            2.0 to -1.0,
            0.0 to -1.0,
        )
        val upper = normalizedContourF64(
            0.0 to 1.0,
            1.0 to 1.0 + e,
            2.0 to 3.0,
            0.0 to 3.0,
        )

        val result = projectTogetherF64(lower, upper)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 0f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 2f)))
    }

    @Test
    fun `same projected branches without their exact source witness reject`() {
        val e = 2.0.pow(-25)
        val lower = normalizedContourF64(
            0.0 to 1.0 - e,
            1.0 to 1.0 - e,
            2.0 to -1.0,
            0.0 to -1.0,
        )
        val upper = normalizedContourF64(
            0.0 to 1.0 + e,
            1.0 to 1.0 + e,
            2.0 to 3.0,
            0.0 to 3.0,
        )

        val error = assertFailsWith<IllegalStateException> { projectTogetherF64(lower, upper) }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projected endpoint contact without an exact witness rejects before the hybrid DCEL`() {
        val e = 2.0.pow(-25)
        val left = normalizedContourF64(
            0.0 to 1.0 - e,
            1.0 to 1.0 - e,
            1.0 to -1.0,
            0.0 to -1.0,
        )
        val right = normalizedContourF64(
            1.0 to 1.0 + e,
            2.0 to 1.0 + e,
            2.0 to 3.0,
            1.0 to 3.0,
        )

        val error = assertFailsWith<IllegalStateException> { projectTogetherF64(left, right) }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `adjacent backtracking projected overlap without exact proof rejects`() {
        // These are distinct F64 rays at the shared vertex, but both y offsets round to the
        // same F32 zero rail.  The only exact contact is the adjacent endpoint.
        val e = 2.0.pow(-150)
        val contour = normalizedContourF64(
            0.0 to 0.0,
            2.0 to e,
            0.0 to 2.0 * e,
            0.0 to -1.0,
        )

        val error = assertFailsWith<IllegalStateException> { projectOneF64(contour) }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `staggered n way exact overlaps stay atomic across operands and contour relabeling`() {
        val leftF32 = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val middleF32 = RectF32.ofLTRB(1f, 0f, 5f, 1f)
        val rightF32 = RectF32.ofLTRB(2f, 0f, 6f, 1f)

        fun path(vararg rectanglesF32: RectF32): PathF32 {
            val builder = PathBuilder()
            rectanglesF32.forEach(builder::addRect)
            return builder.build()
        }

        val variants = listOf(
            path(leftF32, rightF32) to path(middleF32),
            path(rightF32, leftF32) to path(middleF32),
            path(middleF32) to path(leftF32, rightF32),
        )

        variants.forEach { (firstF32, secondF32) ->
            val result = PathOpsF32.op(firstF32, secondF32, PathBooleanOp.UNION)

            listOf(.5f, 1.5f, 3f, 4.5f, 5.5f).forEach { xF32 ->
                assertTrue(PathAnalysisF32.contains(result, Point2F32(xF32, .5f)))
            }
            assertEquals(false, PathAnalysisF32.contains(result, Point2F32(-.25f, .5f)))
            assertEquals(false, PathAnalysisF32.contains(result, Point2F32(6.25f, .5f)))
            assertEquals(RectF32.ofLTRB(0f, 0f, 6f, 1f), PathAnalysisF32.bounds(result))
        }
    }

    @Test
    fun `exact n way overlap aggregates ties independently of fixture relabeling`() {
        val first = normalizedContourF64(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0)
        val second = normalizedContourF64(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0)
        val third = normalizedContourF64(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0)
        val probes = listOf(Point2F32(1f, 1f), Point2F32(3f, 1f))

        val results = listOf(
            projectTogetherF64(first, second, third),
            projectTogetherF64(third, first, second),
            projectTogetherF64(second, third, first),
        )

        results.forEach { result ->
            assertEquals(true, PathAnalysisF32.contains(result, probes[0]))
            assertEquals(false, PathAnalysisF32.contains(result, probes[1]))
        }
    }

    @Test
    fun `hybrid representative preserves the semantic signed zero original bits`() {
        val source = PathBuilder()
            .moveTo(-0.0f, -0.0f)
            .lineTo(2.0f, -0.0f)
            .lineTo(0.0f, 2.0f)
            .close()
            .build()

        val result = PathOpsF32.simplify(source)
        val verticesF32 = pathVerticesF32(result)

        assertTrue(verticesF32.any { pointF32 -> pointF32.x.toRawBits() == (-0.0f).toRawBits() })
        assertTrue(verticesF32.any { pointF32 -> pointF32.y.toRawBits() == (-0.0f).toRawBits() })
    }

    @Test
    fun `hybrid arrangement debits the exact candidate budget frontier deterministically`() {
        val first = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val second = PathBuilder().addRect(RectF32.ofLTRB(1f, 0f, 3f, 2f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                first,
                second,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32 - 1),
            )
        }
        val atBoundary = PathOpsF32.op(
            first,
            second,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )

        assertEquals("path-candidate-limit", belowError.message)
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(.5f, 1f)))
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(2.5f, 1f)))
    }

    @Test
    fun `hybrid budget frontier is invariant when canonical inputs are permuted`() {
        val left = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val right = PathBuilder().addRect(RectF32.ofLTRB(1f, 0f, 3f, 2f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                right,
                left,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32 - 1),
            )
        }
        val forward = PathOpsF32.op(
            left,
            right,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )
        val reverse = PathOpsF32.op(
            right,
            left,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )

        assertEquals("path-candidate-limit", belowError.message)
        assertEquals(forward, reverse)
        listOf(Point2F32(.5f, 1f), Point2F32(1.5f, 1f), Point2F32(2.5f, 1f)).forEach { probeF32 ->
            assertEquals(PathAnalysisF32.contains(forward, probeF32), PathAnalysisF32.contains(reverse, probeF32))
        }
    }

    @Test
    fun `collinear subdivision crossing remains observable through the public operation`() {
        val first = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(2f, 0f, 1f, 0f)
            .lineTo(1f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()
        val second = PathBuilder()
            .addRect(RectF32.ofLTRB(1.24f, -0.5f, 1.26f, 0.5f))
            .build()

        val result = PathOpsF32.op(first, second, PathBooleanOp.UNION)

        listOf(
            Point2F32(0.1f, -0.9f),
            Point2F32(0.5f, -0.5f),
            Point2F32(0.9f, -0.5f),
            Point2F32(1.25f, -0.25f),
            Point2F32(1.25f, 0.25f),
        ).forEach { probe ->
            assertEquals(
                PathAnalysisF32.contains(first, probe) || PathAnalysisF32.contains(second, probe),
                PathAnalysisF32.contains(result, probe),
            )
        }
    }

    @Test
    fun `distant point witness on the same source spans never certifies an F32 overlap`() {
        val e = 2.0.pow(-25)
        val lower = normalizedContourF64(
            0.0 to 1.0, 1.0 to 1.0 - e, 2.0 to 1.0 - e / 2.0,
            2.0 to -1.0, 0.0 to -1.0,
        )
        val upper = normalizedContourF64(
            0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0 + e / 2.0,
            2.0 to 3.0, 0.0 to 3.0,
        )

        // The source contours meet only at (0, 1); their rounded boundary rails overlap.
        assertTrue(PathAnalysisF32.contains(projectOneF64(lower), Point2F32(1f, .5f)))
        assertTrue(PathAnalysisF32.contains(projectOneF64(upper), Point2F32(1f, 1.5f)))

        val error = assertFailsWith<IllegalStateException> { projectTogetherF64(lower, upper) }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `distinct witnesses cannot consume one another`() {
        val e = 2.0.pow(-25)
        val main = normalizedContourWithSourceLocationsF64(
            sourceLocationsF64 = listOf(
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 6, parameterF64 = 1.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 0, parameterF64 = 1.0 / 3.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 0, parameterF64 = 2.0 / 3.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 0, parameterF64 = 1.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 1, parameterF64 = 1.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 2, parameterF64 = 1.0),
                ProjectionSourceLocationF64(sourceSegmentIndexI32 = 3, parameterF64 = 1.0),
            ),
            0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0, 3.0 to 1.0 - e,
            3.0 to -1.0, 1.5 to -2.0, 0.0 to -1.0,
        )
        val firstTouch = normalizedContourF64(0.0 to 1.0, -0.4 to 2.0, 0.4 to 2.0)
        val secondTouch = normalizedContourF64(2.0 to 1.0, 1.6 to 2.0, 2.4 to 2.0)

        val mainBefore = projectOneF64(main)
        assertTrue(PathAnalysisF32.contains(mainBefore, Point2F32(1.5f, 0f)))

        // Swapping the contour order changes the raw input IDs assigned by the fixture.  The
        // exact source claims stay disjoint, so all equivalent relabelings must keep every region.
        listOf(
            projectTogetherF64(main, firstTouch, secondTouch),
            projectTogetherF64(secondTouch, main, firstTouch),
            projectTogetherF64(firstTouch, secondTouch, main),
        ).forEach { result ->
            assertTrue(PathAnalysisF32.contains(result, Point2F32(1.5f, 0f)))
            assertTrue(PathAnalysisF32.contains(result, Point2F32(0f, 1.5f)))
            assertTrue(PathAnalysisF32.contains(result, Point2F32(2f, 1.5f)))
        }

        // A traced projection has no mutable input/output bridge: reusing the original main
        // contour after all relabelings must retain its original region.
        assertTrue(PathAnalysisF32.contains(projectOneF64(main), Point2F32(1.5f, 0f)))
    }

    @Test
    fun `atomic n way exact witness intervals do not create pairwise claim conflicts`() {
        val lower = normalizedContourF64(
            0.0 to 0.0,
            4.0 to 0.0,
            4.0 to 1.0,
            0.0 to 1.0,
        )
        val first = normalizedContourF64(
            0.0 to 1.0,
            3.0 to 1.0,
            0.5 to 3.0,
        )
        val second = normalizedContourF64(
            1.0 to 1.0,
            4.0 to 1.0,
            3.5 to 3.0,
        )

        val result = projectTogetherF64(lower, first, second)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(2f, .5f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(.5f, 2f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(3.5f, 2f)))
        assertEquals(false, PathAnalysisF32.contains(result, Point2F32(-.25f, .5f)))
    }

    @Test
    fun `collapsed hybrid incidence rejects instead of silently skipping a partial contour`() {
        val contour = normalizedContourF64(
            0.0 to 0.0,
            1.0 to 0.0,
            1.0 + 2.0.pow(-25) to 1.0e-46,
            2.0 to 0.0,
            2.0 to 1.0,
            0.0 to 1.0,
        )

        val error = assertFailsWith<IllegalStateException> { projectOneF64(contour) }

        assertEquals("path-f32-projection-collapse", error.message)
    }
}

private val identityNormalizationF64 =
    PathNormalizationF64(origin = Point2F64(0.0, 0.0), scale = 1.0)

private data class ProjectionSourceLocationF64(
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
)

private data class TracedProjectionContourF64(
    val pointsF64: List<Point2F64>,
    val sourceLocationsF64: List<ProjectionSourceLocationF64>,
)

private fun normalizedContourF64(vararg coordinatesF64: Pair<Double, Double>): TracedProjectionContourF64 =
    normalizedContourWithSourceLocationsF64(
        sourceLocationsF64 = coordinatesF64.indices.map { indexI32 ->
            ProjectionSourceLocationF64(sourceSegmentIndexI32 = indexI32, parameterF64 = 1.0)
        },
        *coordinatesF64,
    )

private fun normalizedContourWithSourceLocationsF64(
    sourceLocationsF64: List<ProjectionSourceLocationF64>,
    vararg coordinatesF64: Pair<Double, Double>,
): TracedProjectionContourF64 {
    require(sourceLocationsF64.size == coordinatesF64.size)
    return TracedProjectionContourF64(
        pointsF64 = coordinatesF64.map { (xF64, yF64) -> Point2F64(xF64, yF64) },
        sourceLocationsF64 = sourceLocationsF64,
    )
}

private fun projectOneF64(contourF64: TracedProjectionContourF64): PathF32 =
    projectTracedContoursF64(listOf(contourF64))

private fun projectTogetherF64(vararg contoursF64: TracedProjectionContourF64): PathF32 =
    projectTracedContoursF64(contoursF64.toList())

private fun projectTracedContoursF64(contoursF64: List<TracedProjectionContourF64>): PathF32 {
    val limitsI32 = PathOpsLimitsI32()
    val edgesF64 = mutableListOf<PathInputEdgeF64>()
    contoursF64.forEachIndexed { contourIndexI32, tracedContourF64 ->
        val verticesF64 = tracedContourF64.pointsF64
        val sourceLocationsF64 = tracedContourF64.sourceLocationsF64
        val firstEdgeIdI32 = edgesF64.size
        verticesF64.indices.forEach { vertexIndexI32 ->
            val edgeIdI32 = firstEdgeIdI32 + vertexIndexI32
            val previousEdgeIdI32 = firstEdgeIdI32 + (vertexIndexI32 - 1 + verticesF64.size) % verticesF64.size
            val identityF64 = PathVertexIdentityF64(
                incidentEdgeIds = listOf(previousEdgeIdI32, edgeIdI32).sorted(),
                parameterByEdgeId = mapOf(previousEdgeIdI32 to 1.0, edgeIdI32 to 0.0),
                originalPointF32 = null,
            )
            val nextIndexI32 = (vertexIndexI32 + 1) % verticesF64.size
            edgesF64 += PathInputEdgeF64(
                idI32 = edgeIdI32,
                operand = PathOperand.FIRST,
                contourIndexI32 = contourIndexI32,
                sourceSegmentIndexI32 = sourceLocationsF64[nextIndexI32].sourceSegmentIndexI32,
                sourceStartParameterF64 = if (
                    sourceLocationsF64[vertexIndexI32].sourceSegmentIndexI32 ==
                        sourceLocationsF64[nextIndexI32].sourceSegmentIndexI32
                ) {
                    sourceLocationsF64[vertexIndexI32].parameterF64
                } else {
                    0.0
                },
                sourceEndParameterF64 = sourceLocationsF64[nextIndexI32].parameterF64,
                startIdentityF64 = identityF64,
                endIdentityF64 = PathVertexIdentityF64(
                    incidentEdgeIds = listOf(edgeIdI32, firstEdgeIdI32 + nextIndexI32).sorted(),
                parameterByEdgeId = mapOf(edgeIdI32 to 1.0, firstEdgeIdI32 + nextIndexI32 to 0.0),
                    originalPointF32 = null,
                ),
                startPointF64 = verticesF64[vertexIndexI32],
                endPointF64 = verticesF64[nextIndexI32],
                windingDeltaI32 = 1,
            )
        }
    }
    return projectSourceEdgesThroughHybridF64F32(
        edgesF64 = edgesF64,
        normalizationF64 = identityNormalizationF64,
        fillRule = FillRule.WINDING,
        limitsI32 = limitsI32,
    )
}

private fun projectUnderThresholdWitnessFixtureF32(): PathF32 {
    val scaleF64 = 1.0e-8
    val tinyF64 = 1.0e-46
    val runF64 = normalizedContourF64(
        0.0 to 0.0,
        scaleF64 to tinyF64,
        2.0 * scaleF64 to -tinyF64,
        2.0 * scaleF64 to scaleF64,
    )
    val touchF64 = normalizedContourF64(
        0.0 to 0.0,
        -scaleF64 to scaleF64,
        -scaleF64 to -scaleF64,
    )
    return projectTogetherF64(runF64, touchF64)
}

private fun pathVerticesF32(path: PathF32): List<Point2F32> = buildList {
    path.forEach { segmentF32 ->
        when (segmentF32) {
            is PathSegmentF32.MoveTo -> add(segmentF32.point)
            is PathSegmentF32.LineTo -> add(segmentF32.point)
            is PathSegmentF32.QuadTo -> add(segmentF32.point)
            is PathSegmentF32.CubicTo -> add(segmentF32.point)
            is PathSegmentF32.ArcTo -> add(segmentF32.point)
            PathSegmentF32.Close -> Unit
        }
    }
}

// Hand-derived fixed ledger for two four-edge rectangles. The eight input edges become twelve
// canonical source spans/carriers at the two shared vertical cuts, with nine exact witnesses and
// eight hybrid vertices. The audited deterministic phase totals are source registry/index 1_360,
// projection/claims 1_341, DCEL 1_186, and boundary extraction + Booth + writer 442 = 4_329.
// Keeping the sum literal makes a newly introduced traversal or comparator debit visible at
// `limit - 1` instead of rediscovering a backend-local threshold by probing it in the test.
private const val overlappingRectanglesHybridBudgetI32 = 4_329
