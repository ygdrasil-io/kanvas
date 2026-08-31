package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathOpsHybridTopologyF32Test {
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
    fun `an F64 point witness never certifies an F32 overlap`() {
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
    fun `overlapping exact witness claims reject atomically`() {
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

        val error = assertFailsWith<IllegalStateException> {
            projectTogetherF64(lower, first, second)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `under threshold witness drops only its collapsed source contour`() {
        val result = projectUnderThresholdWitnessFixtureF32()

        assertTrue(PathAnalysisF32.contains(result, Point2F32(-0.5e-8f, 0f)))
    }
}

private val identityNormalizationF64 =
    PathNormalizationF64(origin = Point2F64(0.0, 0.0), scale = 1.0)

private data class ProjectionSourceLocationF64(
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
)

private data class TracedProjectionContourF64(
    val contourF64: PathContourF64,
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
        contourF64 = PathContourF64(
            coordinatesF64.map { (xF64, yF64) ->
                PathContourVertexF64(Point2F64(xF64, yF64), originalPointF32 = null)
            },
        ),
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
    val inputEdgeIdsByContourI32 = mutableListOf<List<Int>>()
    contoursF64.forEachIndexed { contourIndexI32, tracedContourF64 ->
        val verticesF64 = tracedContourF64.contourF64.vertices
        val sourceLocationsF64 = tracedContourF64.sourceLocationsF64
        val firstEdgeIdI32 = edgesF64.size
        inputEdgeIdsByContourI32 += verticesF64.indices.map { firstEdgeIdI32 + it }
        verticesF64.indices.forEach { vertexIndexI32 ->
            val edgeIdI32 = firstEdgeIdI32 + vertexIndexI32
            val previousEdgeIdI32 = firstEdgeIdI32 + (vertexIndexI32 - 1 + verticesF64.size) % verticesF64.size
            val identityF64 = PathVertexIdentityF64(
                incidentEdgeIds = listOf(previousEdgeIdI32, edgeIdI32).sorted(),
                parameterByEdgeId = mapOf(previousEdgeIdI32 to 1.0, edgeIdI32 to 0.0),
                originalPointF32 = verticesF64[vertexIndexI32].originalPointF32,
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
                    originalPointF32 = verticesF64[nextIndexI32].originalPointF32,
                ),
                startPointF64 = verticesF64[vertexIndexI32].point,
                endPointF64 = verticesF64[nextIndexI32].point,
                windingDeltaI32 = 1,
            )
        }
    }
    val candidateWorkBudgetI32 = PathCandidateWorkBudgetI32(limitsI32.maxCandidateProbes)
    val topologyF64 = splitPathSourceTopologyF64(edgesF64, limitsI32, candidateWorkBudgetI32)
    val sectionsByInputEdgeIdI32 = topologyF64.toPathSplitEdgesF64ForLegacyArrangement(candidateWorkBudgetI32)
        .groupBy(PathSplitEdgeF64::sourceId)
    val tracedContoursF64 = inputEdgeIdsByContourI32.map { inputEdgeIdsI32 ->
        PathContourF64(
            inputEdgeIdsI32.flatMap { inputEdgeIdI32 ->
                sectionsByInputEdgeIdI32.getValue(inputEdgeIdI32)
                    .sortedBy(PathSplitEdgeF64::sourceStartParameterF64)
                    .map { sectionF64 ->
                        val provenanceF64 = sectionF64.legacySectionProvenanceF64
                            ?: throw IllegalStateException("path-arrangement-inconsistent")
                        PathContourVertexF64(
                            point = sectionF64.start,
                            originalPointF32 = sectionF64.startIdentity.originalPointF32,
                            legacySectionProvenancesF64 = listOf(provenanceF64),
                        )
                    }
            },
        )
    }
    return projectContoursF64ToPathF32(
        contours = tracedContoursF64,
        normalization = identityNormalizationF64,
        fillRule = FillRule.WINDING,
        candidateWorkBudget = candidateWorkBudgetI32,
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
