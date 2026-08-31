package org.graphiks.math.geometry

internal data class PathSourceLocationF64(
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
    val originalPointF32: Point2F32?,
    val vertexIdentityF64: PathVertexIdentityF64?,
)

internal data class PathFlattenedSectionF64(
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

internal data class PathSourceSpanF64(
    val sourceSpanIdI64: Long,
    val operand: PathOperand,
    val contourIndexI32: Int,
    val startLocationF64: PathSourceLocationF64,
    val endLocationF64: PathSourceLocationF64,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val flattenedSectionsF64: List<PathFlattenedSectionF64>,
    val windingDeltaI32: Int,
)

internal sealed interface PathContactWitnessF64 {
    data class PointF64(
        val vertexIdentityF64: PathVertexIdentityF64,
        val pointF64: Point2F64,
        val incidentSourceSpanIdsI64: List<Long>,
    ) : PathContactWitnessF64

    data class OverlapF64(
        val startVertexIdentityF64: PathVertexIdentityF64,
        val endVertexIdentityF64: PathVertexIdentityF64,
        val firstSourceSpanIdsI64: List<Long>,
        val secondSourceSpanIdsI64: List<Long>,
        val firstStartParameterF64: Double,
        val firstEndParameterF64: Double,
        val secondStartParameterF64: Double,
        val secondEndParameterF64: Double,
    ) : PathContactWitnessF64
}

internal data class PathSourceTopologyF64(
    val sourceSpansF64: List<PathSourceSpanF64>,
    val contactWitnessesF64: List<PathContactWitnessF64>,
)

internal fun splitPathSourceTopologyF64(
    edgesF64: List<PathInputEdgeF64>,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceTopologyF64 {
    val splitEdgesF64 = splitPathEdgesF64(edgesF64, limitsI32, candidateWorkBudgetI32)
    val orderedEdgesF64 = splitEdgesF64.sortedWith(
        compareBy<PathSplitEdgeF64>({ it.operand.ordinal }, { it.contourIndexI32 }, { it.sourceSegmentIndexI32 }, { it.sourceStartParameterF64 }),
    )
    val spansF64 = mutableListOf<PathSourceSpanF64>()
    var nextSpanIdI64 = 0L
    var indexI32 = 0
    while (indexI32 < orderedEdgesF64.size) {
        val firstF64 = orderedEdgesF64[indexI32]
        val sectionsF64 = mutableListOf<PathFlattenedSectionF64>()
        var lastF64 = firstF64
        sectionsF64 += firstF64.toPathFlattenedSectionF64()
        indexI32 += 1
        while (indexI32 < orderedEdgesF64.size) {
            val nextF64 = orderedEdgesF64[indexI32]
            if (
                nextF64.sourceSegmentIndexI32 != firstF64.sourceSegmentIndexI32 ||
                    nextF64.operand != firstF64.operand ||
                    nextF64.contourIndexI32 != firstF64.contourIndexI32 ||
                    nextF64.sourceStartParameterF64 != lastF64.sourceEndParameterF64 ||
                    nextF64.startIsExactEventF64
            ) break
            sectionsF64 += nextF64.toPathFlattenedSectionF64()
            lastF64 = nextF64
            indexI32 += 1
        }
        spansF64 += PathSourceSpanF64(
            sourceSpanIdI64 = nextSpanIdI64++,
            operand = firstF64.operand,
            contourIndexI32 = firstF64.contourIndexI32,
            startLocationF64 = firstF64.startLocationF64(),
            endLocationF64 = lastF64.endLocationF64(),
            startPointF64 = firstF64.start,
            endPointF64 = lastF64.end,
            flattenedSectionsF64 = sectionsF64,
            windingDeltaI32 = firstF64.windingDelta,
        )
    }
    // The split registry has already canonicalized all exact components. Index its endpoint
    // identities directly: this creates one witness for an n-way event and never re-runs the
    // kernel or matches a coordinate back to an identity.
    val endpointsByIdentityF64 = linkedMapOf<PathVertexIdentityF64, MutableList<Pair<Point2F64, PathSplitEdgeF64>>>()
    splitEdgesF64.forEach { edgeF64 ->
        candidateWorkBudgetI32.consume()
        if (edgeF64.startIsExactEventF64) {
            endpointsByIdentityF64.getOrPut(edgeF64.startIdentity) { mutableListOf() } += edgeF64.start to edgeF64
        }
        candidateWorkBudgetI32.consume()
        if (edgeF64.endIsExactEventF64) {
            endpointsByIdentityF64.getOrPut(edgeF64.endIdentity) { mutableListOf() } += edgeF64.end to edgeF64
        }
    }
    val contactsF64 = endpointsByIdentityF64.entries
        .filter { (identityF64, endpointsF64) -> identityF64.incidentEdgeIds.size > 1 && endpointsF64.isNotEmpty() }
        .sortedWith(
            Comparator { firstF64, secondF64 ->
                compareValues(pathVertexIdentitySemanticKeyF64(firstF64.key), pathVertexIdentitySemanticKeyF64(secondF64.key))
            },
        )
        .map { (identityF64, endpointsF64) ->
            PathContactWitnessF64.PointF64(
                vertexIdentityF64 = identityF64,
                pointF64 = endpointsF64.first().first,
                incidentSourceSpanIdsI64 = spansF64.filter { spanF64 ->
                    spanF64.startLocationF64.vertexIdentityF64 == identityF64 ||
                        spanF64.endLocationF64.vertexIdentityF64 == identityF64
                }.map(PathSourceSpanF64::sourceSpanIdI64).sorted(),
            )
        }
    return PathSourceTopologyF64(spansF64, contactsF64)
}

// TODO(Task 3): delete once PathArrangementF64F32 consumes [PathSourceTopologyF64] directly.
// This adapter retains source-derived data; it never tries to recover provenance from coordinates.
internal fun PathSourceTopologyF64.toPathSplitEdgesF64ForLegacyArrangement(): List<PathSplitEdgeF64> {
    // TODO(Task 3): the hybrid arrangement consumes spans directly.  Until then, section order
    // is the sole source of legacy edges: coordinates are copied from sections, never searched
    // or used to recover provenance.
    val orderedSpansF64 = sourceSpansF64.sortedBy(PathSourceSpanF64::sourceSpanIdI64)
    val edgeCountI32 = orderedSpansF64.sumOf { spanF64 -> spanF64.flattenedSectionsF64.size }
    val edgeIdsBySpanF64 = mutableMapOf<Long, IntRange>()
    var nextEdgeIdI32 = 0
    orderedSpansF64.forEach { spanF64 ->
        val endExclusiveI32 = nextEdgeIdI32 + spanF64.flattenedSectionsF64.size
        edgeIdsBySpanF64[spanF64.sourceSpanIdI64] = nextEdgeIdI32 until endExclusiveI32
        nextEdgeIdI32 = endExclusiveI32
    }
    return buildList(edgeCountI32) {
        orderedSpansF64.forEach { spanF64 ->
            val edgeIdsI32 = edgeIdsBySpanF64.getValue(spanF64.sourceSpanIdI64)
            spanF64.flattenedSectionsF64.forEachIndexed { sectionIndexI32, sectionF64 ->
                val edgeIdI32 = edgeIdsI32.first + sectionIndexI32
                val startIdentityF64 = when (sectionIndexI32) {
                    0 -> requireNotNull(spanF64.startLocationF64.vertexIdentityF64)
                    else -> legacySectionIdentityF64(edgeIdsI32.first + sectionIndexI32 - 1, edgeIdI32)
                }
                val endIdentityF64 = when (sectionIndexI32) {
                    edgeIdsI32.last - edgeIdsI32.first -> requireNotNull(spanF64.endLocationF64.vertexIdentityF64)
                    else -> legacySectionIdentityF64(edgeIdI32, edgeIdI32 + 1)
                }
                add(
                    PathSplitEdgeF64(
                        sourceId = edgeIdI32,
                        operand = spanF64.operand,
                        contourIndexI32 = spanF64.contourIndexI32,
                        sourceSegmentIndexI32 = spanF64.startLocationF64.sourceSegmentIndexI32,
                        sourceStartParameterF64 = sectionF64.startParameterF64,
                        sourceEndParameterF64 = sectionF64.endParameterF64,
                        startIdentity = startIdentityF64,
                        endIdentity = endIdentityF64,
                        start = sectionF64.startPointF64,
                        end = sectionF64.endPointF64,
                        windingDelta = spanF64.windingDeltaI32,
                    ),
                )
            }
        }
    }
}

private fun legacySectionIdentityF64(firstEdgeIdI32: Int, secondEdgeIdI32: Int): PathVertexIdentityF64 =
    PathVertexIdentityF64(
        incidentEdgeIds = listOf(firstEdgeIdI32, secondEdgeIdI32).sorted(),
        parameterByEdgeId = mapOf(firstEdgeIdI32 to 1.0, secondEdgeIdI32 to 0.0),
        originalPointF32 = null,
    )

private fun PathSplitEdgeF64.toPathFlattenedSectionF64(): PathFlattenedSectionF64 = PathFlattenedSectionF64(
    startPointF64 = start,
    endPointF64 = end,
    startParameterF64 = sourceStartParameterF64,
    endParameterF64 = sourceEndParameterF64,
)

private fun PathSplitEdgeF64.startLocationF64(): PathSourceLocationF64 = PathSourceLocationF64(
    sourceSegmentIndexI32 = sourceSegmentIndexI32,
    parameterF64 = sourceStartParameterF64,
    originalPointF32 = startIdentity.originalPointF32,
    vertexIdentityF64 = startIdentity,
)

private fun PathSplitEdgeF64.endLocationF64(): PathSourceLocationF64 = PathSourceLocationF64(
    sourceSegmentIndexI32 = sourceSegmentIndexI32,
    parameterF64 = sourceEndParameterF64,
    originalPointF32 = endIdentity.originalPointF32,
    vertexIdentityF64 = endIdentity,
)

private fun pathVertexIdentitySemanticKeyF64(identityF64: PathVertexIdentityF64): String =
    identityF64.incidentEdgeIds.sorted().joinToString(",") + ":" +
        identityF64.parameterByEdgeId.entries.sortedBy { entry -> entry.key }
            .joinToString(",") { entry -> entry.value.toBits().toString() }
