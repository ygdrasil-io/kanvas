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
                nextF64.sourceId != firstF64.sourceId ||
                    nextF64.sourceSegmentIndexI32 != firstF64.sourceSegmentIndexI32 ||
                    nextF64.operand != firstF64.operand ||
                    nextF64.contourIndexI32 != firstF64.contourIndexI32 ||
                    nextF64.sourceStartParameterF64 != lastF64.sourceEndParameterF64 ||
                    nextF64.startIdentity != lastF64.endIdentity
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
    val contactsF64 = mutableListOf<PathContactWitnessF64>()
    edgesF64.indices.forEach { firstIndexI32 ->
        for (secondIndexI32 in firstIndexI32 + 1 until edgesF64.size) {
            candidateWorkBudgetI32.consume()
            when (val contactF64 = intersectPathEdgesF64(edgesF64[firstIndexI32], edgesF64[secondIndexI32])) {
                is PathIntersectionF64.PointF64 -> {
                    val identityF64 = splitEdgesF64.endpointIdentityAtF64(contactF64.point)
                    contactsF64 += PathContactWitnessF64.PointF64(
                        vertexIdentityF64 = identityF64,
                        pointF64 = contactF64.point,
                        incidentSourceSpanIdsI64 = spansF64.incidentToF64(edgesF64[firstIndexI32], contactF64.firstT) +
                            spansF64.incidentToF64(edgesF64[secondIndexI32], contactF64.secondT),
                    )
                }
                is PathIntersectionF64.OverlapF64 -> {
                    contactsF64 += PathContactWitnessF64.OverlapF64(
                        startVertexIdentityF64 = splitEdgesF64.endpointIdentityAtF64(contactF64.start),
                        endVertexIdentityF64 = splitEdgesF64.endpointIdentityAtF64(contactF64.end),
                        firstSourceSpanIdsI64 = spansF64.incidentToF64(edgesF64[firstIndexI32], contactF64.firstStartParameter),
                        secondSourceSpanIdsI64 = spansF64.incidentToF64(edgesF64[secondIndexI32], contactF64.secondStartParameter),
                        firstStartParameterF64 = sourceParameterAtEdgeCutF64(edgesF64[firstIndexI32], contactF64.firstStartParameter),
                        firstEndParameterF64 = sourceParameterAtEdgeCutF64(edgesF64[firstIndexI32], contactF64.firstEndParameter),
                        secondStartParameterF64 = sourceParameterAtEdgeCutF64(edgesF64[secondIndexI32], contactF64.secondStartParameter),
                        secondEndParameterF64 = sourceParameterAtEdgeCutF64(edgesF64[secondIndexI32], contactF64.secondEndParameter),
                    )
                }
                null -> Unit
            }
        }
    }
    return PathSourceTopologyF64(spansF64, contactsF64)
}

// TODO(Task 3): delete once PathArrangementF64F32 consumes [PathSourceTopologyF64] directly.
// This adapter retains source-derived data; it never tries to recover provenance from coordinates.
internal fun PathSourceTopologyF64.toPathSplitEdgesF64ForLegacyArrangement(): List<PathSplitEdgeF64> =
    sourceSpansF64.flatMap { spanF64 ->
        spanF64.flattenedSectionsF64.mapIndexed { sectionIndexI32, sectionF64 ->
            val sourceIdI32 = spanF64.startLocationF64.vertexIdentityF64?.incidentEdgeIds?.firstOrNull()
                ?: -(spanF64.sourceSpanIdI64.toInt() + sectionIndexI32 + 1)
            PathSplitEdgeF64(
                sourceId = sourceIdI32,
                operand = spanF64.operand,
                contourIndexI32 = spanF64.contourIndexI32,
                sourceSegmentIndexI32 = spanF64.startLocationF64.sourceSegmentIndexI32,
                sourceStartParameterF64 = sectionF64.startParameterF64,
                sourceEndParameterF64 = sectionF64.endParameterF64,
                startIdentity = spanF64.startLocationF64.vertexIdentityF64 ?: error("path-source-span-start-identity"),
                endIdentity = spanF64.endLocationF64.vertexIdentityF64 ?: error("path-source-span-end-identity"),
                start = sectionF64.startPointF64,
                end = sectionF64.endPointF64,
                windingDelta = spanF64.windingDeltaI32,
            )
        }
    }

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

private fun List<PathSplitEdgeF64>.endpointIdentityAtF64(pointF64: Point2F64): PathVertexIdentityF64 =
    asSequence()
        .flatMap { edgeF64 -> sequenceOf(edgeF64.start to edgeF64.startIdentity, edgeF64.end to edgeF64.endIdentity) }
        .filter { (candidateF64, _) -> candidateF64 == pointF64 }
        .map { (_, identityF64) -> identityF64 }
        .maxByOrNull { identityF64 -> identityF64.incidentEdgeIds.size }
        ?: error("path-source-witness-identity")

private fun List<PathSourceSpanF64>.incidentToF64(
    edgeF64: PathInputEdgeF64,
    edgeParameterF64: Double,
): List<Long> {
    val parameterF64 = sourceParameterAtEdgeCutF64(edgeF64, edgeParameterF64)
    return filter { spanF64 ->
        spanF64.operand == edgeF64.operand &&
            spanF64.contourIndexI32 == edgeF64.contourIndex &&
            spanF64.startLocationF64.sourceSegmentIndexI32 == edgeF64.sourceSegmentIndexI32 &&
            parameterF64 in spanF64.startLocationF64.parameterF64..spanF64.endLocationF64.parameterF64
    }.map(PathSourceSpanF64::sourceSpanIdI64)
}
