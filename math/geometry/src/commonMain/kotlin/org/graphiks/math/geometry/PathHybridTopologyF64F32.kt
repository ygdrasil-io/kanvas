package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64

/** A source event together with the only F32 coordinate allowed to represent it. */
internal data class PathHybridVertexF64F32(
    val sourcePointF64: Point2F64,
    val representativePointF32: Point2F32,
    val originalPointF32: Point2F32?,
    val vertexIdentityF64: PathVertexIdentityF64,
    val incidentSourceSpanIdsI64: List<Long>,
    val contactWitnessF64: PathContactWitnessF64?,
)

/** Exact-witness-scoped aliases.  A coordinate equality alone never creates one. */
internal data class PathAliasGroupF32(
    val representativePointF32: Point2F32,
    val vertexIdentitiesF64: List<PathVertexIdentityF64>,
    val contactWitnessF64: PathContactWitnessF64,
)

internal data class PathProjectedSpanClaimF64(
    val sourceSpanIdI64: Long,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

/** A locally proved F64 point event whose two incident spans become one F32 rail. */
internal data class PathProjectedCoincidenceF32(
    val projectedCoincidenceIdI64: Long,
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val firstSourceSpanIdI64: Long,
    val secondSourceSpanIdI64: Long,
    val startPointF32: Point2F32,
    val endPointF32: Point2F32,
    val firstClaimF64: PathProjectedSpanClaimF64,
    val secondClaimF64: PathProjectedSpanClaimF64,
)

/** A source span with no F32 dimension.  It remains observable to the arrangement. */
internal data class PathCollapsedIncidenceF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val hybridVertexF64F32: PathHybridVertexF64F32,
    val incomingDirectionF64: Vector2F64,
    val outgoingDirectionF64: Vector2F64,
)

internal data class PathHybridTopologyF64F32(
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val sourceSpansF64: List<PathSourceSpanF64>,
    val aliasGroupsF32: List<PathAliasGroupF32>,
    val projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
)

private data class PathHybridVertexSeedF64F32(
    val identityF64: PathVertexIdentityF64,
    val sourcePointF64: Point2F64,
    val originalPointsF32: MutableList<Point2F32>,
    val incidentCandidatesF32: MutableList<Point2F32>,
    val incidentSourceSpanIdsI64: MutableList<Long>,
    val witnessesF64: MutableList<PathContactWitnessF64>,
)

private data class PathProjectedSourceSpanF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val startVertexIndexI32: Int,
    val endVertexIndexI32: Int,
    val projectedEdgeF64: PathInputEdgeF64,
)

private data class PathProjectedCoincidenceProposalF64F32(
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val firstSpanF64: PathProjectedSourceSpanF64F32,
    val secondSpanF64: PathProjectedSourceSpanF64F32,
    val startPointF32: Point2F32,
    val endPointF32: Point2F32,
)

/**
 * Builds the projection-visible source topology before any face or winding work.  The input
 * source topology remains the sole authority for exact parameters and witnesses; this function
 * only selects lattice representatives and classifies every projected contact conservatively.
 */
internal fun buildPathHybridTopologyF64F32(
    sourceTopologyF64: PathSourceTopologyF64,
    normalizationF64: PathNormalizationF64,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathHybridTopologyF64F32 {
    val sourceSpansF64 = sortedHybridF64F32(
        sourceTopologyF64.sourceSpansF64,
        candidateWorkBudgetI32,
        ::compareHybridSourceSpansF64,
    )
    if (sourceSpansF64.size > limitsI32.maxHalfEdges / 2) {
        throw IllegalStateException("path-half-edge-limit")
    }

    // An overlap is a complete exact source proof, not a collection of pairwise permissions.
    // Validate all of its span intervals before a projected edge can consume any of them.  This
    // is deliberately before the F32 broad phase: a later rejection must not leave a partially
    // published alias component behind.
    validateHybridOverlapClaimsF64(sourceTopologyF64.contactWitnessesF64, candidateWorkBudgetI32)

    preflightHybridLinearF64F32(sourceTopologyF64.contactWitnessesF64.size.toLong() * 3L, candidateWorkBudgetI32)
    val witnessesByIdentityF64 = mutableMapOf<PathVertexIdentityF64, MutableList<PathContactWitnessF64>>()
    sourceTopologyF64.contactWitnessesF64.forEach { witnessF64 ->
        when (witnessF64) {
            is PathContactWitnessF64.PointF64 -> {
                witnessesByIdentityF64.getOrPut(witnessF64.vertexIdentityF64) { mutableListOf() } += witnessF64
            }
            is PathContactWitnessF64.OverlapF64 -> {
                val endpointIdentitiesF64 = witnessF64.startVertexIdentitiesF64 + witnessF64.endVertexIdentitiesF64
                preflightHybridLinearF64F32(endpointIdentitiesF64.size.toLong() * 2L, candidateWorkBudgetI32)
                endpointIdentitiesF64.forEach { identityF64 ->
                    witnessesByIdentityF64.getOrPut(identityF64) { mutableListOf() } += witnessF64
                }
            }
        }
    }

    preflightHybridLinearF64F32(sourceSpansF64.size.toLong() * 10L, candidateWorkBudgetI32)
    val seedsByIdentityF64 = mutableMapOf<PathVertexIdentityF64, PathHybridVertexSeedF64F32>()
    sourceSpansF64.forEach { sourceSpanF64 ->
        addHybridVertexSeedF64F32(
            seedsByIdentityF64 = seedsByIdentityF64,
            sourceSpanF64 = sourceSpanF64,
            locationF64 = sourceSpanF64.startLocationF64,
            pointF64 = sourceSpanF64.startPointF64,
            normalizationF64 = normalizationF64,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        addHybridVertexSeedF64F32(
            seedsByIdentityF64 = seedsByIdentityF64,
            sourceSpanF64 = sourceSpanF64,
            locationF64 = sourceSpanF64.endLocationF64,
            pointF64 = sourceSpanF64.endPointF64,
            normalizationF64 = normalizationF64,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    }
    if (seedsByIdentityF64.size > limitsI32.maxVertices) throw IllegalStateException("path-vertex-limit")

    preflightHybridLinearF64F32(seedsByIdentityF64.size.toLong(), candidateWorkBudgetI32)
    val seedValuesF64F32 = seedsByIdentityF64.values.toList()
    val orderedSeedsF64F32 = sortedHybridF64F32(
        seedValuesF64F32,
        candidateWorkBudgetI32,
        ::compareHybridVertexSeedsF64F32,
    )
    preflightHybridLinearF64F32(orderedSeedsF64F32.size.toLong() * 3L, candidateWorkBudgetI32)
    val vertexIndexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    val verticesF64F32 = orderedSeedsF64F32.mapIndexed { indexI32, seedF64F32 ->
        preflightHybridLinearF64F32(
            seedF64F32.originalPointsF32.size.toLong() * 2L + seedF64F32.incidentCandidatesF32.size.toLong() * 2L,
            candidateWorkBudgetI32,
        )
        val representativePointF32 = chooseRepresentativePointF32(
            originalPointsF32 = seedF64F32.originalPointsF32,
            incidentCandidatesF32 = seedF64F32.incidentCandidatesF32,
        )
        preflightHybridLinearF64F32(seedF64F32.witnessesF64.size.toLong(), candidateWorkBudgetI32)
        val distinctWitnessesF64 = seedF64F32.witnessesF64.distinct()
        val witnessesF64 = sortedHybridF64F32(
            distinctWitnessesF64,
            candidateWorkBudgetI32,
            ::compareHybridWitnessesF64,
        )
        if (witnessesF64.size > 1 && compareHybridWitnessesF64(witnessesF64[0], witnessesF64[1]) == 0) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        vertexIndexByIdentityF64[seedF64F32.identityF64] = indexI32
        PathHybridVertexF64F32(
            sourcePointF64 = seedF64F32.sourcePointF64,
            representativePointF32 = representativePointF32,
            originalPointF32 = chooseOriginalPointF32(seedF64F32.originalPointsF32),
            vertexIdentityF64 = seedF64F32.identityF64,
            incidentSourceSpanIdsI64 = sortedHybridLongsI64(seedF64F32.incidentSourceSpanIdsI64, candidateWorkBudgetI32),
            contactWitnessF64 = witnessesF64.singleOrNull(),
        )
    }

    preflightHybridLinearF64F32(sourceSpansF64.size.toLong() * 8L, candidateWorkBudgetI32)
    val projectedSpansF64F32 = mutableListOf<PathProjectedSourceSpanF64F32>()
    val collapsedIncidencesF64F32 = mutableListOf<PathCollapsedIncidenceF64F32>()
    sourceSpansF64.forEachIndexed { sourceIndexI32, sourceSpanF64 ->
        val startIdentityF64 = sourceSpanF64.startLocationF64.vertexIdentityF64
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val endIdentityF64 = sourceSpanF64.endLocationF64.vertexIdentityF64
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val startVertexIndexI32 = vertexIndexByIdentityF64.getValue(startIdentityF64)
        val endVertexIndexI32 = vertexIndexByIdentityF64.getValue(endIdentityF64)
        val startVertexF64F32 = verticesF64F32[startVertexIndexI32]
        val endVertexF64F32 = verticesF64F32[endVertexIndexI32]
        val directionF64 = sourceSpanF64.endPointF64 - sourceSpanF64.startPointF64
        if (sameHybridPointF32(startVertexF64F32.representativePointF32, endVertexF64F32.representativePointF32)) {
            collapsedIncidencesF64F32 += PathCollapsedIncidenceF64F32(
                sourceSpanF64 = sourceSpanF64,
                hybridVertexF64F32 = startVertexF64F32,
                incomingDirectionF64 = -directionF64,
                outgoingDirectionF64 = directionF64,
            )
            return@forEachIndexed
        }
        val projectedEdgeF64 = PathInputEdgeF64(
            idI32 = sourceIndexI32,
            operand = sourceSpanF64.operand,
            contourIndexI32 = sourceSpanF64.contourIndexI32,
            sourceSegmentIndexI32 = sourceSpanF64.startLocationF64.sourceSegmentIndexI32,
            sourceStartParameterF64 = sourceSpanF64.startLocationF64.parameterF64,
            sourceEndParameterF64 = sourceSpanF64.endLocationF64.parameterF64,
            startIdentityF64 = startIdentityF64,
            endIdentityF64 = endIdentityF64,
            startPointF64 = startVertexF64F32.representativePointF32.toPoint2F64(),
            endPointF64 = endVertexF64F32.representativePointF32.toPoint2F64(),
            windingDeltaI32 = sourceSpanF64.windingDeltaI32,
        )
        projectedSpansF64F32 += PathProjectedSourceSpanF64F32(
            sourceSpanF64 = sourceSpanF64,
            startVertexIndexI32 = startVertexIndexI32,
            endVertexIndexI32 = endVertexIndexI32,
            projectedEdgeF64 = projectedEdgeF64,
        )
    }

    // The AABB index itself is conservative; this debit covers its deterministic construction
    // and immutable projected-edge view before candidate callbacks can run.
    preflightHybridLinearF64F32(projectedSpansF64F32.size.toLong() * 6L, candidateWorkBudgetI32)
    preflightProjectedPairClassificationF64F32(
        projectedSpanCountI32 = projectedSpansF64F32.size,
        witnessesF64 = sourceTopologyF64.contactWitnessesF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    val proposalsF64F32 = mutableListOf<PathProjectedCoincidenceProposalF64F32>()
    forEachPathEdgeCandidatePairF64(
        projectedSpansF64F32.map(PathProjectedSourceSpanF64F32::projectedEdgeF64),
        candidateWorkBudgetI32,
    ) { firstIndexI32, secondIndexI32 ->
        val firstF64F32 = projectedSpansF64F32[firstIndexI32]
        val secondF64F32 = projectedSpansF64F32[secondIndexI32]
        val projectedContactF64 = intersectPathEdgesF64(firstF64F32.projectedEdgeF64, secondF64F32.projectedEdgeF64)
            ?: return@forEachPathEdgeCandidatePairF64
        if (
            isSameExactSourceEventF64F32(
                firstF64F32,
                secondF64F32,
                projectedContactF64,
                verticesF64F32,
                vertexIndexByIdentityF64,
            )
        ) {
            return@forEachPathEdgeCandidatePairF64
        }
        val pointWitnessF64 = localPointWitnessForProjectedPairF64F32(
            firstF64F32,
            secondF64F32,
            projectedContactF64,
            sourceTopologyF64.contactWitnessesF64,
            verticesF64F32,
            vertexIndexByIdentityF64,
            candidateWorkBudgetI32,
        )
        when (projectedContactF64) {
            is PathIntersectionF64.PointF64 -> {
                if (pointWitnessF64 == null && !exactOverlapSupportsProjectedPointF64F32(
                        firstF64F32,
                        secondF64F32,
                        sourceTopologyF64.contactWitnessesF64,
                        candidateWorkBudgetI32,
                    ) && !isEndpointOnlyProjectedTouchF64F32(projectedContactF64)
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
            }
            is PathIntersectionF64.OverlapF64 -> {
                // A point event can make two distinct local branches share a projected rail only
                // when both retain the same projected traversal.  A backtracking neighbour is a
                // different F32 relation and needs an exact overlap witness of its own.
                if (pointWitnessF64 != null && projectedOverlapKeepsTraversalF64F32(firstF64F32, secondF64F32)) {
                    proposalsF64F32 += PathProjectedCoincidenceProposalF64F32(
                        pointWitnessF64 = pointWitnessF64,
                        firstSpanF64 = firstF64F32,
                        secondSpanF64 = secondF64F32,
                        startPointF32 = projectedContactF64.start.toPoint2F32(),
                        endPointF32 = projectedContactF64.end.toPoint2F32(),
                    )
                } else if (!exactOverlapSupportsProjectedOverlapF64F32(
                        firstF64F32,
                        secondF64F32,
                        sourceTopologyF64.contactWitnessesF64,
                        candidateWorkBudgetI32,
                    )
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
            }
        }
    }

    val projectedCoincidencesF32 = assignProjectedCoincidencesF64F32(proposalsF64F32, candidateWorkBudgetI32)
    val aliasGroupsF32 = buildHybridAliasGroupsF32(
        verticesF64F32 = verticesF64F32,
        projectedCoincidencesF32 = projectedCoincidencesF32,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    return PathHybridTopologyF64F32(
        verticesF64F32 = verticesF64F32,
        sourceSpansF64 = sourceSpansF64,
        aliasGroupsF32 = aliasGroupsF32,
        projectedCoincidencesF32 = projectedCoincidencesF32,
        collapsedIncidencesF64F32 = run {
            preflightHybridLinearF64F32(collapsedIncidencesF64F32.size.toLong(), candidateWorkBudgetI32)
            collapsedIncidencesF64F32.toList()
        },
    )
}

private fun projectedOverlapKeepsTraversalF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
): Boolean {
    val firstDirectionF64 = firstF64F32.projectedEdgeF64.endPointF64 - firstF64F32.projectedEdgeF64.startPointF64
    val secondDirectionF64 = secondF64F32.projectedEdgeF64.endPointF64 - secondF64F32.projectedEdgeF64.startPointF64
    val dotF64 = firstDirectionF64.x * secondDirectionF64.x + firstDirectionF64.y * secondDirectionF64.y
    return dotF64 > 0.0
}

private data class PathHybridWitnessSpanClaimF64(
    val witnessIdI64: Long,
    val sourceSpanIdI64: Long,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

/**
 * A source span may be consumed by at most one geometrically distinct overlap interior.  Exact
 * endpoint sharing is intentionally allowed; Task 3 adds endpoint identities to distinguish the
 * remaining adjacent cases.  The preflight is quadratic by design for this Task-2 ledger and is
 * paid once, independently of the platform sort/comparator implementation.
 */
private fun validateHybridOverlapClaimsF64(
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val overlapWitnessInputsF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.OverlapF64>()
    val overlapWitnessesF64 = sortedHybridF64F32(
        overlapWitnessInputsF64,
        candidateWorkBudgetI32,
        ::compareHybridWitnessesF64,
    )
    // The exact maximum is known from the witnesses before allocating the claim list.
    val claimCountI64 = overlapWitnessesF64.sumOf { witnessF64 ->
        witnessF64.incidencesF64.sumOf { incidenceF64 -> incidenceF64.sourceSpanIdsI64.size.toLong() }
    }
    preflightHybridLinearF64F32(claimCountI64 * claimCountI64 + claimCountI64 * 3L, candidateWorkBudgetI32)
    val claimsF64 = mutableListOf<PathHybridWitnessSpanClaimF64>()
    overlapWitnessesF64.forEach { witnessF64 ->
        witnessF64.incidencesF64.forEach { incidenceF64 ->
            val startF64 = minOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
            val endF64 = maxOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
            incidenceF64.sourceSpanIdsI64.forEach { sourceSpanIdI64 ->
                claimsF64 += PathHybridWitnessSpanClaimF64(
                    witnessIdI64 = witnessF64.witnessIdI64,
                    sourceSpanIdI64 = sourceSpanIdI64,
                    startParameterF64 = startF64,
                    endParameterF64 = endF64,
                )
            }
        }
    }
    claimsF64.indices.forEach { firstIndexI32 ->
        (firstIndexI32 + 1 until claimsF64.size).forEach { secondIndexI32 ->
            val firstF64 = claimsF64[firstIndexI32]
            val secondF64 = claimsF64[secondIndexI32]
            if (
                firstF64.witnessIdI64 != secondF64.witnessIdI64 &&
                    firstF64.sourceSpanIdI64 == secondF64.sourceSpanIdI64 &&
                    maxOf(firstF64.startParameterF64, secondF64.startParameterF64) <
                    minOf(firstF64.endParameterF64, secondF64.endParameterF64)
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
}

/**
 * The projected broad phase may visit a platform-independent subset of the canonical pairs.
 * Reserve the complete canonical-pair classification envelope before the AABB index or callback
 * can allocate a result.  This intentionally includes the witness scans below; their amount is
 * determined solely by the source witness multiset, never by a JVM/JS comparator invocation or
 * by the order in which the AABB tree happens to visit a pair.
 */
private fun preflightProjectedPairClassificationF64F32(
    projectedSpanCountI32: Int,
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val projectedSpanCountI64 = projectedSpanCountI32.toLong()
    val pairCountI64 = projectedSpanCountI64 * (projectedSpanCountI64 - 1L) / 2L
    var pointWitnessCountI64 = 0L
    var overlapLookupWorkI64 = 0L
    witnessesF64.forEach { witnessF64 ->
        when (witnessF64) {
            is PathContactWitnessF64.PointF64 -> pointWitnessCountI64 += 1L
            is PathContactWitnessF64.OverlapF64 -> {
                val incidenceCountI64 = witnessF64.incidencesF64.size.toLong()
                val spanCountI64 = witnessF64.incidencesF64.sumOf { incidenceF64 ->
                    incidenceF64.sourceSpanIdsI64.size.toLong()
                }
                overlapLookupWorkI64 += 1L + incidenceCountI64 * incidenceCountI64 + spanCountI64 * 2L
            }
        }
    }
    val pointWitnessWorkI64 = deterministicSortCostI64F32(pointWitnessCountI64.toInt()) +
        pointWitnessCountI64 * 7L + 3L
    val perPairI64 = 3L + pointWitnessWorkI64 + overlapLookupWorkI64 * 2L
    preflightHybridLinearF64F32(pairCountI64 * perPairI64, candidateWorkBudgetI32)
}

// A quantized endpoint-only touch does not join any half-edges.  It remains represented by two
// source vertices, unlike an overlap/crossing which must be justified before the DCEL is built.
private fun isEndpointOnlyProjectedTouchF64F32(projectedContactF64: PathIntersectionF64.PointF64): Boolean =
    (projectedContactF64.firstT == 0.0 || projectedContactF64.firstT == 1.0) &&
        (projectedContactF64.secondT == 0.0 || projectedContactF64.secondT == 1.0)

private fun addHybridVertexSeedF64F32(
    seedsByIdentityF64: MutableMap<PathVertexIdentityF64, PathHybridVertexSeedF64F32>,
    sourceSpanF64: PathSourceSpanF64,
    locationF64: PathSourceLocationF64,
    pointF64: Point2F64,
    normalizationF64: PathNormalizationF64,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val identityF64 = locationF64.vertexIdentityF64 ?: throw IllegalStateException("path-arrangement-inconsistent")
    val candidatePointF32 = normalizationF64.denormalize(pointF64)
    if (!candidatePointF32.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
    val seedF64F32 = seedsByIdentityF64[identityF64]
    if (seedF64F32 == null) {
        seedsByIdentityF64[identityF64] = PathHybridVertexSeedF64F32(
            identityF64 = identityF64,
            sourcePointF64 = pointF64,
            originalPointsF32 = mutableListOf<Point2F32>().also { pointsF32 -> locationF64.originalPointF32?.let(pointsF32::add) },
            incidentCandidatesF32 = mutableListOf(candidatePointF32),
            incidentSourceSpanIdsI64 = mutableListOf(sourceSpanF64.sourceSpanIdI64),
            witnessesF64 = witnessesByIdentityF64[identityF64]?.toMutableList() ?: mutableListOf(),
        )
        return
    }
    if (!sameHybridPointF64(seedF64F32.sourcePointF64, pointF64)) {
        throw IllegalStateException("path-arrangement-inconsistent")
    }
    locationF64.originalPointF32?.let(seedF64F32.originalPointsF32::add)
    seedF64F32.incidentCandidatesF32 += candidatePointF32
    seedF64F32.incidentSourceSpanIdsI64 += sourceSpanF64.sourceSpanIdI64
}

/** Implements the representative priority specified for the hybrid lattice boundary. */
private fun chooseRepresentativePointF32(
    originalPointsF32: List<Point2F32>,
    incidentCandidatesF32: List<Point2F32>,
): Point2F32 {
    if (originalPointsF32.isNotEmpty()) {
        val firstF32 = originalPointsF32.first()
        var selectedF32 = firstF32
        originalPointsF32.drop(1).forEach { pointF32 ->
            if (!sameHybridPointF32(firstF32, pointF32)) throw IllegalStateException("path-f32-projection-collapse")
            if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
        }
        return selectedF32
    }
    if (incidentCandidatesF32.isEmpty()) throw IllegalStateException("path-f32-projection-collapse")
    val firstF32 = incidentCandidatesF32.first()
    var selectedF32 = firstF32
    incidentCandidatesF32.drop(1).forEach { pointF32 ->
        if (!sameHybridPointF32(firstF32, pointF32)) throw IllegalStateException("path-f32-projection-collapse")
        if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
    }
    return selectedF32
}

private fun chooseOriginalPointF32(originalPointsF32: List<Point2F32>): Point2F32? {
    var selectedF32 = originalPointsF32.firstOrNull() ?: return null
    originalPointsF32.drop(1).forEach { pointF32 ->
        if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
    }
    return selectedF32
}

private fun localPointWitnessForProjectedPairF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64,
    witnessesF64: List<PathContactWitnessF64>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathContactWitnessF64.PointF64? {
    val projectedPointsF32 = projectedContactPointsF32(projectedContactF64)
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val pointWitnessInputsF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.PointF64>()
    val pointWitnessesF64 = sortedHybridF64F32(
        pointWitnessInputsF64,
        candidateWorkBudgetI32,
        ::compareHybridPointWitnessesF64,
    )
    preflightHybridLinearF64F32(pointWitnessesF64.size.toLong() * 6L + 2L, candidateWorkBudgetI32)
    pointWitnessesF64.forEach { witnessF64 ->
        if (firstF64F32.sourceSpanF64.sourceSpanIdI64 !in witnessF64.incidentSourceSpanIdsI64) return@forEach
        if (secondF64F32.sourceSpanF64.sourceSpanIdI64 !in witnessF64.incidentSourceSpanIdsI64) return@forEach
        if (!spanTouchesPointWitnessF64F32(firstF64F32.sourceSpanF64, witnessF64)) return@forEach
        if (!spanTouchesPointWitnessF64F32(secondF64F32.sourceSpanF64, witnessF64)) return@forEach
        val witnessVertexIndexI32 = vertexIndexByIdentityF64[witnessF64.vertexIdentityF64] ?: return@forEach
        val witnessPointF32 = verticesF64F32[witnessVertexIndexI32].representativePointF32
        if (projectedPointsF32.none { pointF32 -> sameHybridPointF32(pointF32, witnessPointF32) }) return@forEach
        return witnessF64
    }
    return null
}

private fun isSameExactSourceEventF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
): Boolean {
    if (projectedContactF64 !is PathIntersectionF64.PointF64) return false
    val sharedIdentityF64 = listOf(
        firstF64F32.sourceSpanF64.startLocationF64.vertexIdentityF64,
        firstF64F32.sourceSpanF64.endLocationF64.vertexIdentityF64,
    ).firstOrNull { identityF64 ->
        identityF64 != null &&
            (identityF64 == secondF64F32.sourceSpanF64.startLocationF64.vertexIdentityF64 ||
                identityF64 == secondF64F32.sourceSpanF64.endLocationF64.vertexIdentityF64)
    } ?: return false
    val representativeF32 = verticesF64F32[vertexIndexByIdentityF64[sharedIdentityF64] ?: return false].representativePointF32
    return sameHybridPointF32(projectedContactF64.point.toPoint2F32(), representativeF32)
}

private fun spanTouchesPointWitnessF64F32(
    sourceSpanF64: PathSourceSpanF64,
    witnessF64: PathContactWitnessF64.PointF64,
): Boolean = sourceSpanF64.startLocationF64.vertexIdentityF64 == witnessF64.vertexIdentityF64 ||
    sourceSpanF64.endLocationF64.vertexIdentityF64 == witnessF64.vertexIdentityF64

private fun exactOverlapSupportsProjectedPointF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val overlapWitnessesF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.OverlapF64>()
    preflightOverlapWitnessLookupF64F32(overlapWitnessesF64, candidateWorkBudgetI32)
    return overlapWitnessesF64.any { witnessF64 ->
        overlapWitnessContainsBothSpansF64F32(witnessF64, firstF64F32.sourceSpanF64, secondF64F32.sourceSpanF64)
    }
}

private fun exactOverlapSupportsProjectedOverlapF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val overlapWitnessesF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.OverlapF64>()
    preflightOverlapWitnessLookupF64F32(overlapWitnessesF64, candidateWorkBudgetI32)
    return overlapWitnessesF64.any { witnessF64 ->
        overlapWitnessContainsBothSpansF64F32(witnessF64, firstF64F32.sourceSpanF64, secondF64F32.sourceSpanF64)
    }
}

private fun preflightOverlapWitnessLookupF64F32(
    overlapWitnessesF64: List<PathContactWitnessF64.OverlapF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val unitsI64 = overlapWitnessesF64.sumOf { witnessF64 ->
        val incidenceCountI64 = witnessF64.incidencesF64.size.toLong()
        val spanCountI64 = witnessF64.incidencesF64.sumOf { incidenceF64 -> incidenceF64.sourceSpanIdsI64.size.toLong() }
        1L + incidenceCountI64 * incidenceCountI64 + spanCountI64 * 2L
    }
    preflightHybridLinearF64F32(unitsI64, candidateWorkBudgetI32)
}

private fun overlapWitnessContainsBothSpansF64F32(
    witnessF64: PathContactWitnessF64.OverlapF64,
    firstSpanF64: PathSourceSpanF64,
    secondSpanF64: PathSourceSpanF64,
): Boolean {
    val firstIdI64 = firstSpanF64.sourceSpanIdI64
    val secondIdI64 = secondSpanF64.sourceSpanIdI64
    return witnessF64.incidencesF64.indices.any { firstIndexI32 ->
        witnessF64.incidencesF64.indices.any { secondIndexI32 ->
            firstIndexI32 != secondIndexI32 &&
                firstIdI64 in witnessF64.incidencesF64[firstIndexI32].sourceSpanIdsI64 &&
                secondIdI64 in witnessF64.incidencesF64[secondIndexI32].sourceSpanIdsI64
        }
    }
}

private fun assignProjectedCoincidencesF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathProjectedCoincidenceF32> {
    val orderedF64F32 = sortedHybridF64F32(
        proposalsF64F32,
        candidateWorkBudgetI32,
        ::compareProjectedCoincidenceProposalsF64F32,
    )
    var nextIdI64 = 0L
    var previousF64F32: PathProjectedCoincidenceProposalF64F32? = null
    preflightHybridLinearF64F32(orderedF64F32.size.toLong() * 3L, candidateWorkBudgetI32)
    return orderedF64F32.map { proposalF64F32 ->
        val previous = previousF64F32
        if (previous != null && projectedCoincidenceComponentCompareF64F32(previous, proposalF64F32) != 0) nextIdI64 += 1L
        previousF64F32 = proposalF64F32
        PathProjectedCoincidenceF32(
            projectedCoincidenceIdI64 = nextIdI64,
            pointWitnessF64 = proposalF64F32.pointWitnessF64,
            firstSourceSpanIdI64 = proposalF64F32.firstSpanF64.sourceSpanF64.sourceSpanIdI64,
            secondSourceSpanIdI64 = proposalF64F32.secondSpanF64.sourceSpanF64.sourceSpanIdI64,
            startPointF32 = proposalF64F32.startPointF32,
            endPointF32 = proposalF64F32.endPointF32,
            firstClaimF64 = spanClaimF64(proposalF64F32.firstSpanF64.sourceSpanF64),
            secondClaimF64 = spanClaimF64(proposalF64F32.secondSpanF64.sourceSpanF64),
        )
    }
}

private fun spanClaimF64(sourceSpanF64: PathSourceSpanF64): PathProjectedSpanClaimF64 = PathProjectedSpanClaimF64(
    sourceSpanIdI64 = sourceSpanF64.sourceSpanIdI64,
    startParameterF64 = minOf(sourceSpanF64.startLocationF64.parameterF64, sourceSpanF64.endLocationF64.parameterF64),
    endParameterF64 = maxOf(sourceSpanF64.startLocationF64.parameterF64, sourceSpanF64.endLocationF64.parameterF64),
)

private fun buildHybridAliasGroupsF32(
    verticesF64F32: List<PathHybridVertexF64F32>,
    projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathAliasGroupF32> {
    preflightHybridLinearF64F32(projectedCoincidencesF32.size.toLong() * 2L, candidateWorkBudgetI32)
    val groupsF32 = mutableListOf<PathAliasGroupF32>()
    val groupedF32 = mutableMapOf<Long, MutableList<PathProjectedCoincidenceF32>>()
    projectedCoincidencesF32.forEach { coincidenceF32 ->
        groupedF32.getOrPut(coincidenceF32.projectedCoincidenceIdI64) { mutableListOf() } += coincidenceF32
    }
    val incidentSpanCountI64 = verticesF64F32.sumOf { vertexF64F32 ->
        vertexF64F32.incidentSourceSpanIdsI64.size.toLong()
    }
    preflightHybridLinearF64F32(
        projectedCoincidencesF32.size.toLong() * 4L +
            groupedF32.size.toLong() * (verticesF64F32.size.toLong() * 4L + incidentSpanCountI64 * 2L + 4L),
        candidateWorkBudgetI32,
    )
    groupedF32.values.forEach { coincidencesF32 ->
        val witnessF64 = coincidencesF32.first().pointWitnessF64
        val authorizedSpanIdsI64 = coincidencesF32.flatMap { coincidenceF32 ->
            listOf(coincidenceF32.firstSourceSpanIdI64, coincidenceF32.secondSourceSpanIdI64)
        }.toSet()
        val pointsF32 = listOf(coincidencesF32.first().startPointF32, coincidencesF32.first().endPointF32)
        pointsF32.forEach { pointF32 ->
            val identitiesF64 = mutableListOf<PathVertexIdentityF64>()
            verticesF64F32.forEach { vertexF64F32 ->
                if (
                    sameHybridPointF32(vertexF64F32.representativePointF32, pointF32) &&
                        vertexF64F32.incidentSourceSpanIdsI64.any { spanIdI64 -> spanIdI64 in authorizedSpanIdsI64 }
                ) {
                    identitiesF64 += vertexF64F32.vertexIdentityF64
                }
            }
            if (identitiesF64.isNotEmpty()) {
                groupsF32 += PathAliasGroupF32(
                    representativePointF32 = pointF32,
                    vertexIdentitiesF64 = identitiesF64,
                    contactWitnessF64 = witnessF64,
                )
            }
        }
    }
    return sortedHybridF64F32(groupsF32, candidateWorkBudgetI32, ::compareAliasGroupsF32)
}

private fun projectedContactPointsF32(projectedContactF64: PathIntersectionF64): List<Point2F32> = when (projectedContactF64) {
    is PathIntersectionF64.PointF64 -> listOf(projectedContactF64.point.toPoint2F32())
    is PathIntersectionF64.OverlapF64 -> listOf(projectedContactF64.start.toPoint2F32(), projectedContactF64.end.toPoint2F32())
}

private fun compareHybridSourceSpansF64(firstF64: PathSourceSpanF64, secondF64: PathSourceSpanF64): Int {
    compareHybridPointsF64(firstF64.startPointF64, secondF64.startPointF64).takeIf { it != 0 }?.let { return it }
    compareHybridPointsF64(firstF64.endPointF64, secondF64.endPointF64).takeIf { it != 0 }?.let { return it }
    firstF64.windingDeltaI32.compareTo(secondF64.windingDeltaI32).takeIf { it != 0 }?.let { return it }
    firstF64.flattenedSectionsF64.size.compareTo(secondF64.flattenedSectionsF64.size).takeIf { it != 0 }?.let { return it }
    return 0
}

private fun compareHybridVertexSeedsF64F32(firstF64F32: PathHybridVertexSeedF64F32, secondF64F32: PathHybridVertexSeedF64F32): Int =
    compareHybridPointsF64(firstF64F32.sourcePointF64, secondF64F32.sourcePointF64)

private fun compareHybridWitnessesF64(firstF64: PathContactWitnessF64, secondF64: PathContactWitnessF64): Int = when {
    firstF64 is PathContactWitnessF64.PointF64 && secondF64 is PathContactWitnessF64.PointF64 ->
        compareHybridPointsF64(firstF64.pointF64, secondF64.pointF64)
    firstF64 is PathContactWitnessF64.PointF64 -> -1
    secondF64 is PathContactWitnessF64.PointF64 -> 1
    firstF64 is PathContactWitnessF64.OverlapF64 && secondF64 is PathContactWitnessF64.OverlapF64 ->
        compareHybridPointsF64(firstF64.startPointF64, secondF64.startPointF64)
            .takeIf { it != 0 } ?: compareHybridPointsF64(firstF64.endPointF64, secondF64.endPointF64)
    else -> 0
}

private fun compareHybridPointWitnessesF64(
    firstF64: PathContactWitnessF64.PointF64,
    secondF64: PathContactWitnessF64.PointF64,
): Int = compareHybridPointsF64(firstF64.pointF64, secondF64.pointF64)

private fun compareProjectedCoincidenceProposalsF64F32(
    firstF64F32: PathProjectedCoincidenceProposalF64F32,
    secondF64F32: PathProjectedCoincidenceProposalF64F32,
): Int = projectedCoincidenceComponentCompareF64F32(firstF64F32, secondF64F32)

private fun projectedCoincidenceComponentCompareF64F32(
    firstF64F32: PathProjectedCoincidenceProposalF64F32,
    secondF64F32: PathProjectedCoincidenceProposalF64F32,
): Int {
    compareHybridPointsF64(firstF64F32.pointWitnessF64.pointF64, secondF64F32.pointWitnessF64.pointF64)
        .takeIf { it != 0 }?.let { return it }
    val firstStartF32 = minHybridPointF32(firstF64F32.startPointF32, firstF64F32.endPointF32)
    val firstEndF32 = maxHybridPointF32(firstF64F32.startPointF32, firstF64F32.endPointF32)
    val secondStartF32 = minHybridPointF32(secondF64F32.startPointF32, secondF64F32.endPointF32)
    val secondEndF32 = maxHybridPointF32(secondF64F32.startPointF32, secondF64F32.endPointF32)
    compareHybridPointsF32(firstStartF32, secondStartF32).takeIf { it != 0 }?.let { return it }
    return compareHybridPointsF32(firstEndF32, secondEndF32)
}

private fun compareAliasGroupsF32(firstF32: PathAliasGroupF32, secondF32: PathAliasGroupF32): Int =
    compareHybridPointsF32(firstF32.representativePointF32, secondF32.representativePointF32)
        .takeIf { it != 0 } ?: compareHybridWitnessesF64(firstF32.contactWitnessF64, secondF32.contactWitnessF64)

private fun compareHybridPointsF64(firstF64: Point2F64, secondF64: Point2F64): Int = when {
    firstF64.x < secondF64.x -> -1
    firstF64.x > secondF64.x -> 1
    firstF64.y < secondF64.y -> -1
    firstF64.y > secondF64.y -> 1
    else -> 0
}

private fun compareHybridPointsF32(firstF32: Point2F32, secondF32: Point2F32): Int {
    when {
        firstF32.x < secondF32.x -> return -1
        firstF32.x > secondF32.x -> return 1
        firstF32.y < secondF32.y -> return -1
        firstF32.y > secondF32.y -> return 1
    }
    firstF32.x.toRawBits().compareTo(secondF32.x.toRawBits()).takeIf { it != 0 }?.let { return it }
    return firstF32.y.toRawBits().compareTo(secondF32.y.toRawBits())
}

private fun minHybridPointF32(firstF32: Point2F32, secondF32: Point2F32): Point2F32 =
    if (compareHybridPointsF32(firstF32, secondF32) <= 0) firstF32 else secondF32

private fun maxHybridPointF32(firstF32: Point2F32, secondF32: Point2F32): Point2F32 =
    if (compareHybridPointsF32(firstF32, secondF32) >= 0) firstF32 else secondF32

private fun sameHybridPointF64(firstF64: Point2F64, secondF64: Point2F64): Boolean =
    firstF64.x == secondF64.x && firstF64.y == secondF64.y

private fun sameHybridPointF32(firstF32: Point2F32, secondF32: Point2F32): Boolean =
    firstF32.x == secondF32.x && firstF32.y == secondF32.y

private fun sortedHybridLongsI64(valuesI64: List<Long>, candidateWorkBudgetI32: PathCandidateWorkBudgetI32): List<Long> {
    preflightHybridLinearF64F32(valuesI64.size.toLong(), candidateWorkBudgetI32)
    val distinctValuesI64 = valuesI64.distinct()
    return sortedHybridF64F32(distinctValuesI64, candidateWorkBudgetI32) { firstI64, secondI64 -> firstI64.compareTo(secondI64) }
}

private fun <T> sortedHybridF64F32(
    values: List<T>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    compare: (T, T) -> Int,
): List<T> {
    val sizeI32 = values.size
    val comparisonsI64 = deterministicSortCostI64F32(sizeI32)
    preflightHybridLinearF64F32(comparisonsI64 + sizeI32.toLong(), candidateWorkBudgetI32)
    return values.sortedWith(Comparator(compare))
}

private fun deterministicSortCostI64F32(sizeI32: Int): Long {
    if (sizeI32 < 2) return 0L
    var widthI64 = 1L
    var levelsI64 = 0L
    val sizeI64 = sizeI32.toLong()
    while (widthI64 < sizeI64) {
        widthI64 = widthI64 shl 1
        levelsI64 += 1L
    }
    return sizeI64 * levelsI64
}

private fun preflightHybridLinearF64F32(unitsI64: Long, candidateWorkBudgetI32: PathCandidateWorkBudgetI32) {
    candidateWorkBudgetI32.consumePreflightI64(unitsI64)
}
