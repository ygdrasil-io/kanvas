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
    val witnessIdI64: Long,
    val sourceSpanIdI64: Long,
    val sourceSectionIndexI32: Int,
    val inputEdgeIdI32: Int,
    val startParameterF64: Double,
    val endParameterF64: Double,
    val startVertexIdentityF64: PathVertexIdentityF64?,
    val endVertexIdentityF64: PathVertexIdentityF64?,
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
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
    val hybridVertexF64F32: PathHybridVertexF64F32,
    val incomingDirectionF64: Vector2F64,
    val outgoingDirectionF64: Vector2F64,
)

/**
 * One geometric carrier retained from a source span.  It deliberately does not receive its own
 * topology identity: all sections continue to belong to [sourceSpanF64].
 */
internal data class PathHybridCarrierSectionF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
)

internal data class PathHybridTopologyF64F32(
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val sourceSpansF64: List<PathSourceSpanF64>,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
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
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
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
    val firstClaimF64: PathProjectedSpanClaimF64,
    val secondClaimF64: PathProjectedSpanClaimF64,
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
    val carrierSectionCountI64 = sourceSpansF64.sumOf { sourceSpanF64 ->
        sourceSpanF64.flattenedSectionsF64.size.toLong()
    }
    // `maxHalfEdges` applies to the canonical DCEL count, not this raw carrier multiset: a
    // valid exact overlap may aggregate several carriers onto one final half-edge.  The source
    // flattening limits already bound this temporary collection; only its JVM/JS capacity needs
    // an explicit checked conversion here.
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionCountI64, 2L),
        candidateWorkBudgetI32,
    )
    val carrierSectionsF64F32 = ArrayList<PathHybridCarrierSectionF64F32>(
        checkedPathCapacityI32(carrierSectionCountI64, "path-half-edge-limit"),
    )
    sourceSpansF64.forEach { sourceSpanF64 ->
        if (sourceSpanF64.flattenedSectionsF64.isEmpty()) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        sourceSpanF64.flattenedSectionsF64.forEachIndexed { sectionIndexI32, sourceSectionF64 ->
            carrierSectionsF64F32 += PathHybridCarrierSectionF64F32(
                sourceSpanF64 = sourceSpanF64,
                sourceSectionF64 = sourceSectionF64,
                sectionIndexI32 = sectionIndexI32,
            )
        }
    }

    // An overlap is a complete exact source proof, not a collection of pairwise permissions.
    // Validate all of its span intervals before a projected edge can consume any of them.  This
    // is deliberately before the F32 broad phase: a later rejection must not leave a partially
    // published alias component behind.
    validateHybridOverlapClaimsF64(sourceTopologyF64.contactWitnessesF64, candidateWorkBudgetI32)

    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(sourceTopologyF64.contactWitnessesF64.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
    val witnessesByIdentityF64 = mutableMapOf<PathVertexIdentityF64, MutableList<PathContactWitnessF64>>()
    sourceTopologyF64.contactWitnessesF64.forEach { witnessF64 ->
        when (witnessF64) {
            is PathContactWitnessF64.PointF64 -> {
                witnessesByIdentityF64.getOrPut(witnessF64.vertexIdentityF64) { mutableListOf() } += witnessF64
            }
            is PathContactWitnessF64.OverlapF64 -> {
                val endpointIdentitiesF64 = witnessF64.startVertexIdentitiesF64 + witnessF64.endVertexIdentitiesF64
                preflightHybridLinearF64F32(
                    checkedPathWorkMultiplyI64(endpointIdentitiesF64.size.toLong(), 2L),
                    candidateWorkBudgetI32,
                )
                endpointIdentitiesF64.forEach { identityF64 ->
                    witnessesByIdentityF64.getOrPut(identityF64) { mutableListOf() } += witnessF64
                }
            }
        }
    }

    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 10L),
        candidateWorkBudgetI32,
    )
    val seedsByIdentityF64 = mutableMapOf<PathVertexIdentityF64, PathHybridVertexSeedF64F32>()
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val sourceSpanF64 = carrierSectionF64F32.sourceSpanF64
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        addHybridVertexSeedF64F32(
            seedsByIdentityF64 = seedsByIdentityF64,
            sourceSpanF64 = sourceSpanF64,
            locationF64 = sourceSectionLocationF64(sourceSpanF64, sourceSectionF64, atStart = true),
            pointF64 = sourceSectionF64.startPointF64,
            normalizationF64 = normalizationF64,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        addHybridVertexSeedF64F32(
            seedsByIdentityF64 = seedsByIdentityF64,
            sourceSpanF64 = sourceSpanF64,
            locationF64 = sourceSectionLocationF64(sourceSpanF64, sourceSectionF64, atStart = false),
            pointF64 = sourceSectionF64.endPointF64,
            normalizationF64 = normalizationF64,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    }
    preflightHybridLinearF64F32(seedsByIdentityF64.size.toLong(), candidateWorkBudgetI32)
    val seedValuesF64F32 = seedsByIdentityF64.values.toList()
    val orderedSeedsF64F32 = sortedHybridF64F32(
        seedValuesF64F32,
        candidateWorkBudgetI32,
        ::compareHybridVertexSeedsF64F32,
    )
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(orderedSeedsF64F32.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
    val vertexIndexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    val verticesF64F32 = orderedSeedsF64F32.mapIndexed { indexI32, seedF64F32 ->
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(seedF64F32.originalPointsF32.size.toLong(), 2L),
                checkedPathWorkMultiplyI64(seedF64F32.incidentCandidatesF32.size.toLong(), 2L),
            ),
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

    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 8L),
        candidateWorkBudgetI32,
    )
    val projectedSpansF64F32 = mutableListOf<PathProjectedSourceSpanF64F32>()
    val collapsedIncidencesF64F32 = mutableListOf<PathCollapsedIncidenceF64F32>()
    carrierSectionsF64F32.forEachIndexed { sourceIndexI32, carrierSectionF64F32 ->
        val sourceSpanF64 = carrierSectionF64F32.sourceSpanF64
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        val startIdentityF64 = sourceSectionF64.startIdentityF64
        val endIdentityF64 = sourceSectionF64.endIdentityF64
        val startVertexIndexI32 = vertexIndexByIdentityF64.getValue(startIdentityF64)
        val endVertexIndexI32 = vertexIndexByIdentityF64.getValue(endIdentityF64)
        val startVertexF64F32 = verticesF64F32[startVertexIndexI32]
        val endVertexF64F32 = verticesF64F32[endVertexIndexI32]
        val directionF64 = sourceSectionF64.endPointF64 - sourceSectionF64.startPointF64
        if (sameHybridPointF32(startVertexF64F32.representativePointF32, endVertexF64F32.representativePointF32)) {
            collapsedIncidencesF64F32 += PathCollapsedIncidenceF64F32(
                sourceSpanF64 = sourceSpanF64,
                sourceSectionF64 = sourceSectionF64,
                sectionIndexI32 = carrierSectionF64F32.sectionIndexI32,
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
            sourceStartParameterF64 = sourceSectionF64.startParameterF64,
            sourceEndParameterF64 = sourceSectionF64.endParameterF64,
            startIdentityF64 = startIdentityF64,
            endIdentityF64 = endIdentityF64,
            startPointF64 = startVertexF64F32.representativePointF32.toPoint2F64(),
            endPointF64 = endVertexF64F32.representativePointF32.toPoint2F64(),
            windingDeltaI32 = sourceSpanF64.windingDeltaI32,
        )
        projectedSpansF64F32 += PathProjectedSourceSpanF64F32(
            sourceSpanF64 = sourceSpanF64,
            sourceSectionF64 = sourceSectionF64,
            sectionIndexI32 = carrierSectionF64F32.sectionIndexI32,
            startVertexIndexI32 = startVertexIndexI32,
            endVertexIndexI32 = endVertexIndexI32,
            projectedEdgeF64 = projectedEdgeF64,
        )
    }

    // A zero-F32-length carrier normally rejects: omitting it would publish a partial contour.
    // The single safe disposition is an internal flattening interval (strict source parameters,
    // no original F32 endpoint and no exact event).  It has no representable F32 extent, so the
    // arrangement explicitly aliases and consumes that exact adjacent pair below.  In particular
    // an input endpoint or a precise F64 fixture can never take this route.
    if (collapsedIncidencesF64F32.any { collapsedF64F32 ->
            !isIntrinsicFlatteningCollapseF64F32(collapsedF64F32, sourceTopologyF64.contactWitnessesF64)
        }
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }

    // The AABB index itself is conservative; this debit covers its deterministic construction
    // and immutable projected-edge view before candidate callbacks can run.
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(projectedSpansF64F32.size.toLong(), 6L),
        candidateWorkBudgetI32,
    )
    // The shared AABB walker debits only the candidates it actually emits.  Do not reserve a
    // second all-pairs envelope here: that would charge both culled pairs and the same emitted
    // candidate a second time, and makes a finely flattened curve exhaust its public budget
    // before geometry is examined.
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
                        projectedContactF64,
                        sourceTopologyF64.contactWitnessesF64,
                        candidateWorkBudgetI32,
                    )
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
            }
            is PathIntersectionF64.OverlapF64 -> {
                // A point event can make two distinct local branches share a projected rail only
                // when both retain the same projected traversal.  A backtracking neighbour is a
                // different F32 relation and needs an exact overlap witness of its own.
                if (pointWitnessF64 != null && projectedOverlapKeepsTraversalF64F32(firstF64F32, secondF64F32)) {
                    val firstClaimF64 = projectedOverlapClaimF64F32(
                        witnessF64 = pointWitnessF64,
                        projectedSpanF64F32 = firstF64F32,
                        startParameterF64 = projectedContactF64.firstStartParameter,
                        endParameterF64 = projectedContactF64.firstEndParameter,
                    ) ?: throw IllegalStateException("path-f32-projection-collapse")
                    val secondClaimF64 = projectedOverlapClaimF64F32(
                        witnessF64 = pointWitnessF64,
                        projectedSpanF64F32 = secondF64F32,
                        startParameterF64 = projectedContactF64.secondStartParameter,
                        endParameterF64 = projectedContactF64.secondEndParameter,
                    ) ?: throw IllegalStateException("path-f32-projection-collapse")
                    proposalsF64F32 += PathProjectedCoincidenceProposalF64F32(
                        pointWitnessF64 = pointWitnessF64,
                        firstSpanF64 = firstF64F32,
                        secondSpanF64 = secondF64F32,
                        startPointF32 = projectedContactF64.start.toPoint2F32(),
                        endPointF32 = projectedContactF64.end.toPoint2F32(),
                        firstClaimF64 = firstClaimF64,
                        secondClaimF64 = secondClaimF64,
                    )
                } else if (!exactOverlapSupportsProjectedOverlapF64F32(
                        firstF64F32,
                        secondF64F32,
                        projectedContactF64,
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
        carrierSectionsF64F32 = carrierSectionsF64F32,
        aliasGroupsF32 = aliasGroupsF32,
        projectedCoincidencesF32 = projectedCoincidencesF32,
        collapsedIncidencesF64F32 = run {
            preflightHybridLinearF64F32(collapsedIncidencesF64F32.size.toLong(), candidateWorkBudgetI32)
            collapsedIncidencesF64F32.toList()
        },
    )
}

private fun isIntrinsicFlatteningCollapseF64F32(
    collapsedF64F32: PathCollapsedIncidenceF64F32,
    witnessesF64: List<PathContactWitnessF64>,
): Boolean {
    val sourceSpanF64 = collapsedF64F32.sourceSpanF64
    val sectionF64 = collapsedF64F32.sourceSectionF64
    // Only an internal, ordered continuation of this *same* source span may be consumed.  A
    // source endpoint, seam or one-sided fragment would otherwise turn an F64 carrier loss into
    // an invisible cross-span alias.
    val sectionIndexI32 = collapsedF64F32.sectionIndexI32
    if (sectionIndexI32 <= 0 || sectionIndexI32 >= sourceSpanF64.flattenedSectionsF64.lastIndex) return false
    val previousSectionF64 = sourceSpanF64.flattenedSectionsF64[sectionIndexI32 - 1]
    val nextSectionF64 = sourceSpanF64.flattenedSectionsF64[sectionIndexI32 + 1]
    if (
        previousSectionF64.endIdentityF64 != sectionF64.startIdentityF64 ||
            nextSectionF64.startIdentityF64 != sectionF64.endIdentityF64
    ) {
        return false
    }
    val minimumParameterF64 = minOf(sectionF64.startParameterF64, sectionF64.endParameterF64)
    val maximumParameterF64 = maxOf(sectionF64.startParameterF64, sectionF64.endParameterF64)
    if (minimumParameterF64 <= 0.0 || maximumParameterF64 >= 1.0) return false
    val previousMaximumParameterF64 = maxOf(previousSectionF64.startParameterF64, previousSectionF64.endParameterF64)
    val nextMinimumParameterF64 = minOf(nextSectionF64.startParameterF64, nextSectionF64.endParameterF64)
    if (previousMaximumParameterF64 > minimumParameterF64 || maximumParameterF64 > nextMinimumParameterF64) return false
    if (sectionF64.startIdentityF64.originalPointF32 != null || sectionF64.endIdentityF64.originalPointF32 != null) return false
    val hasForeignExactWitnessF64 = witnessesF64.any { witnessF64 ->
        when (witnessF64) {
            is PathContactWitnessF64.PointF64 ->
                witnessF64.vertexIdentityF64 == sectionF64.startIdentityF64 ||
                    witnessF64.vertexIdentityF64 == sectionF64.endIdentityF64
            is PathContactWitnessF64.OverlapF64 ->
                sectionF64.startIdentityF64 in witnessF64.startVertexIdentitiesF64 ||
                    sectionF64.startIdentityF64 in witnessF64.endVertexIdentitiesF64 ||
                    sectionF64.endIdentityF64 in witnessF64.startVertexIdentitiesF64 ||
                    sectionF64.endIdentityF64 in witnessF64.endVertexIdentitiesF64
        }
    }
    return !hasForeignExactWitnessF64
}

private fun sourceSectionLocationF64(
    sourceSpanF64: PathSourceSpanF64,
    sourceSectionF64: PathFlattenedSectionF64,
    atStart: Boolean,
): PathSourceLocationF64 {
    if (atStart && sourceSectionF64.startIdentityF64 == sourceSpanF64.startLocationF64.vertexIdentityF64) {
        return sourceSpanF64.startLocationF64
    }
    if (!atStart && sourceSectionF64.endIdentityF64 == sourceSpanF64.endLocationF64.vertexIdentityF64) {
        return sourceSpanF64.endLocationF64
    }
    return PathSourceLocationF64(
        sourceSegmentIndexI32 = sourceSpanF64.startLocationF64.sourceSegmentIndexI32,
        parameterF64 = if (atStart) sourceSectionF64.startParameterF64 else sourceSectionF64.endParameterF64,
        originalPointF32 = if (atStart) sourceSectionF64.startIdentityF64.originalPointF32 else sourceSectionF64.endIdentityF64.originalPointF32,
        vertexIdentityF64 = if (atStart) sourceSectionF64.startIdentityF64 else sourceSectionF64.endIdentityF64,
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

/**
 * A projected rail is only publishable when both of its exact source bounds are registry
 * vertices.  The partial parameters remain in the claim, but an interior endpoint without a
 * direct identity has no authority to create an alias in Task 2.
 */
private fun projectedOverlapClaimF64F32(
    witnessF64: PathContactWitnessF64.PointF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    startParameterF64: Double,
    endParameterF64: Double,
): PathProjectedSpanClaimF64? {
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    val startIdentityF64 = when (startParameterF64) {
        0.0 -> sourceSectionF64.startIdentityF64
        1.0 -> sourceSectionF64.endIdentityF64
        else -> null
    }
    val endIdentityF64 = when (endParameterF64) {
        0.0 -> sourceSectionF64.startIdentityF64
        1.0 -> sourceSectionF64.endIdentityF64
        else -> null
    }
    if (startIdentityF64 == null || endIdentityF64 == null) return null
    if (witnessF64.vertexIdentityF64 != startIdentityF64 && witnessF64.vertexIdentityF64 != endIdentityF64) return null
    val startSourceParameterF64 = sourceParameterAtEdgeCutF64(
        projectedSpanF64F32.projectedEdgeF64,
        startParameterF64,
    )
    val endSourceParameterF64 = sourceParameterAtEdgeCutF64(
        projectedSpanF64F32.projectedEdgeF64,
        endParameterF64,
    )
    return if (startSourceParameterF64 <= endSourceParameterF64) {
        PathProjectedSpanClaimF64(
            witnessIdI64 = witnessF64.witnessIdI64,
            sourceSpanIdI64 = projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
            sourceSectionIndexI32 = projectedSpanF64F32.sectionIndexI32,
            inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
            startParameterF64 = startSourceParameterF64,
            endParameterF64 = endSourceParameterF64,
            startVertexIdentityF64 = startIdentityF64,
            endVertexIdentityF64 = endIdentityF64,
        )
    } else {
        PathProjectedSpanClaimF64(
            witnessIdI64 = witnessF64.witnessIdI64,
            sourceSpanIdI64 = projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
            sourceSectionIndexI32 = projectedSpanF64F32.sectionIndexI32,
            inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
            startParameterF64 = endSourceParameterF64,
            endParameterF64 = startSourceParameterF64,
            startVertexIdentityF64 = endIdentityF64,
            endVertexIdentityF64 = startIdentityF64,
        )
    }
}

private data class PathHybridWitnessCarrierClaimF64(
    val witnessIdI64: Long,
    val inputEdgeIdI32: Int,
    val startParameterF64: Double,
    val endParameterF64: Double,
    val startVertexIdentityF64: PathVertexIdentityF64,
    val endVertexIdentityF64: PathVertexIdentityF64,
)

/**
 * An exact input carrier may be consumed by at most one geometrically distinct overlap interior.
 * The registry incidence supplies both raw carrier identity and exact endpoint identities, so a
 * post-split source span cannot hide a competing claim.  The scan is linear after canonical
 * ordering and never re-identifies an endpoint through a coordinate or ULP neighbourhood.
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
    val claimCountI64 = overlapWitnessesF64.sumOf { witnessF64 -> witnessF64.incidencesF64.size.toLong() }
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(claimCountI64, 4L),
        candidateWorkBudgetI32,
    )
    val claimsF64 = ArrayList<PathHybridWitnessCarrierClaimF64>(
        checkedPathCapacityI32(claimCountI64, "path-candidate-limit"),
    )
    overlapWitnessesF64.forEach { witnessF64 ->
        witnessF64.incidencesF64.forEach { incidenceF64 ->
            if (incidenceF64.startParameterF64 <= incidenceF64.endParameterF64) {
                claimsF64 += PathHybridWitnessCarrierClaimF64(
                    witnessIdI64 = witnessF64.witnessIdI64,
                    inputEdgeIdI32 = incidenceF64.inputEdgeIdI32,
                    startParameterF64 = incidenceF64.startParameterF64,
                    endParameterF64 = incidenceF64.endParameterF64,
                    startVertexIdentityF64 = incidenceF64.startVertexIdentityF64,
                    endVertexIdentityF64 = incidenceF64.endVertexIdentityF64,
                )
            } else {
                claimsF64 += PathHybridWitnessCarrierClaimF64(
                    witnessIdI64 = witnessF64.witnessIdI64,
                    inputEdgeIdI32 = incidenceF64.inputEdgeIdI32,
                    startParameterF64 = incidenceF64.endParameterF64,
                    endParameterF64 = incidenceF64.startParameterF64,
                    startVertexIdentityF64 = incidenceF64.endVertexIdentityF64,
                    endVertexIdentityF64 = incidenceF64.startVertexIdentityF64,
                )
            }
        }
    }
    val orderedClaimsF64 = sortedHybridF64F32(claimsF64, candidateWorkBudgetI32, ::compareHybridWitnessCarrierClaimsF64)
    var activeClaimF64: PathHybridWitnessCarrierClaimF64? = null
    orderedClaimsF64.forEach { claimF64 ->
        val activeF64 = activeClaimF64
        if (activeF64 == null || activeF64.inputEdgeIdI32 != claimF64.inputEdgeIdI32) {
            activeClaimF64 = claimF64
            return@forEach
        }
        when {
            claimF64.startParameterF64 < activeF64.endParameterF64 -> {
                if (claimF64.witnessIdI64 != activeF64.witnessIdI64) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (claimF64.endParameterF64 > activeF64.endParameterF64) activeClaimF64 = claimF64
            }
            claimF64.startParameterF64 == activeF64.endParameterF64 -> {
                if (
                    claimF64.witnessIdI64 != activeF64.witnessIdI64 &&
                        claimF64.startVertexIdentityF64 != activeF64.endVertexIdentityF64
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (claimF64.endParameterF64 >= activeF64.endParameterF64) activeClaimF64 = claimF64
            }
            else -> activeClaimF64 = claimF64
        }
    }
}

private fun compareHybridWitnessCarrierClaimsF64(
    firstF64: PathHybridWitnessCarrierClaimF64,
    secondF64: PathHybridWitnessCarrierClaimF64,
): Int {
    firstF64.inputEdgeIdI32.compareTo(secondF64.inputEdgeIdI32).takeIf { it != 0 }?.let { return it }
    firstF64.startParameterF64.compareTo(secondF64.startParameterF64).takeIf { it != 0 }?.let { return it }
    return firstF64.endParameterF64.compareTo(secondF64.endParameterF64)
}

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
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(checkedPathWorkMultiplyI64(pointWitnessesF64.size.toLong(), 6L), 2L),
        candidateWorkBudgetI32,
    )
    pointWitnessesF64.forEach { witnessF64 ->
        if (!projectedSpanTouchesPointWitnessF64F32(firstF64F32, witnessF64, projectedContactF64, first = true)) return@forEach
        if (!projectedSpanTouchesPointWitnessF64F32(secondF64F32, witnessF64, projectedContactF64, first = false)) return@forEach
        val witnessVertexIndexI32 = vertexIndexByIdentityF64[witnessF64.vertexIdentityF64] ?: return@forEach
        val witnessPointF32 = verticesF64F32[witnessVertexIndexI32].representativePointF32
        if (projectedPointsF32.none { pointF32 -> sameHybridPointF32(pointF32, witnessPointF32) }) return@forEach
        return witnessF64
    }
    return null
}

private fun projectedSpanTouchesPointWitnessF64F32(
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    projectedContactF64: PathIntersectionF64,
    first: Boolean,
): Boolean {
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    val endpointTouchesWitness = sourceSectionF64.startIdentityF64 == witnessF64.vertexIdentityF64 ||
        sourceSectionF64.endIdentityF64 == witnessF64.vertexIdentityF64
    if (!endpointTouchesWitness) return false
    val parameterF64 = when (projectedContactF64) {
        is PathIntersectionF64.PointF64 -> if (first) projectedContactF64.firstT else projectedContactF64.secondT
        is PathIntersectionF64.OverlapF64 -> return true
    }
    return projectedSectionEndpointIdentityF64F32(projectedSpanF64F32, parameterF64) == witnessF64.vertexIdentityF64
}

private fun isSameExactSourceEventF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
): Boolean {
    if (projectedContactF64 !is PathIntersectionF64.PointF64) return false
    val firstIdentityF64 = projectedSectionEndpointIdentityF64F32(firstF64F32, projectedContactF64.firstT)
        ?: return false
    val secondIdentityF64 = projectedSectionEndpointIdentityF64F32(secondF64F32, projectedContactF64.secondT)
        ?: return false
    if (firstIdentityF64 != secondIdentityF64) return false
    val representativeF32 = verticesF64F32[vertexIndexByIdentityF64[firstIdentityF64] ?: return false].representativePointF32
    return sameHybridPointF32(projectedContactF64.point.toPoint2F32(), representativeF32)
}

private fun projectedSectionEndpointIdentityF64F32(
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    parameterF64: Double,
): PathVertexIdentityF64? = when (parameterF64) {
    0.0 -> projectedSpanF64F32.sourceSectionF64.startIdentityF64
    1.0 -> projectedSpanF64F32.sourceSectionF64.endIdentityF64
    else -> null
}

private fun exactOverlapSupportsProjectedPointF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64.PointF64,
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val overlapWitnessesF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.OverlapF64>()
    preflightOverlapWitnessLookupF64F32(overlapWitnessesF64, candidateWorkBudgetI32)
    return overlapWitnessesF64.any { witnessF64 ->
        overlapWitnessCoversProjectedPointF64F32(
            witnessF64,
            firstF64F32,
            projectedContactF64.firstT,
            secondF64F32,
            projectedContactF64.secondT,
        )
    }
}

private fun exactOverlapSupportsProjectedOverlapF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64.OverlapF64,
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val overlapWitnessesF64 = witnessesF64.filterIsInstance<PathContactWitnessF64.OverlapF64>()
    preflightOverlapWitnessLookupF64F32(overlapWitnessesF64, candidateWorkBudgetI32)
    return overlapWitnessesF64.any { witnessF64 ->
        overlapWitnessCoversProjectedRailF64F32(
            witnessF64,
            firstF64F32,
            projectedContactF64.firstStartParameter,
            projectedContactF64.firstEndParameter,
            secondF64F32,
            projectedContactF64.secondStartParameter,
            projectedContactF64.secondEndParameter,
        )
    }
}

private fun preflightOverlapWitnessLookupF64F32(
    overlapWitnessesF64: List<PathContactWitnessF64.OverlapF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val unitsI64 = overlapWitnessesF64.fold(0L) { totalI64, witnessF64 ->
        val incidenceCountI64 = witnessF64.incidencesF64.size.toLong()
        val spanCountI64 = witnessF64.incidencesF64.fold(0L) { spanTotalI64, incidenceF64 ->
            checkedPathWorkAddI64(spanTotalI64, incidenceF64.sourceSpanIdsI64.size.toLong())
        }
        checkedPathWorkAddI64(
            totalI64,
            checkedPathWorkAddI64(
                1L,
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(incidenceCountI64, incidenceCountI64),
                    checkedPathWorkMultiplyI64(spanCountI64, 2L),
                ),
            ),
        )
    }
    preflightHybridLinearF64F32(unitsI64, candidateWorkBudgetI32)
}

private fun overlapWitnessCoversProjectedPointF64F32(
    witnessF64: PathContactWitnessF64.OverlapF64,
    firstF64F32: PathProjectedSourceSpanF64F32,
    firstParameterF64: Double,
    secondF64F32: PathProjectedSourceSpanF64F32,
    secondParameterF64: Double,
): Boolean = witnessF64.incidencesF64.indices.any { firstIndexI32 ->
    witnessF64.incidencesF64.indices.any { secondIndexI32 ->
        firstIndexI32 != secondIndexI32 &&
            overlapIncidenceCoversProjectedEndpointF64F32(
                witnessF64.incidencesF64[firstIndexI32],
                firstF64F32,
                firstParameterF64,
            ) &&
            overlapIncidenceCoversProjectedEndpointF64F32(
                witnessF64.incidencesF64[secondIndexI32],
                secondF64F32,
                secondParameterF64,
            )
    }
}

private fun overlapWitnessCoversProjectedRailF64F32(
    witnessF64: PathContactWitnessF64.OverlapF64,
    firstF64F32: PathProjectedSourceSpanF64F32,
    firstStartParameterF64: Double,
    firstEndParameterF64: Double,
    secondF64F32: PathProjectedSourceSpanF64F32,
    secondStartParameterF64: Double,
    secondEndParameterF64: Double,
): Boolean = witnessF64.incidencesF64.indices.any { firstIndexI32 ->
    witnessF64.incidencesF64.indices.any { secondIndexI32 ->
        firstIndexI32 != secondIndexI32 &&
            overlapIncidenceCoversProjectedRailF64F32(
                witnessF64.incidencesF64[firstIndexI32],
                firstF64F32,
                firstStartParameterF64,
                firstEndParameterF64,
            ) &&
            overlapIncidenceCoversProjectedRailF64F32(
                witnessF64.incidencesF64[secondIndexI32],
                secondF64F32,
                secondStartParameterF64,
                secondEndParameterF64,
            )
    }
}

private fun overlapIncidenceCoversProjectedEndpointF64F32(
    incidenceF64: PathOverlapWitnessIncidenceF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    parameterF64: Double,
): Boolean {
    if (incidenceF64.inputEdgeIdI32 != projectedSpanF64F32.sourceSectionF64.inputEdgeIdI32) return false
    return projectedEndpointIsWithinOverlapIncidenceF64F32(incidenceF64, projectedSpanF64F32, parameterF64)
}

private fun overlapIncidenceCoversProjectedRailF64F32(
    incidenceF64: PathOverlapWitnessIncidenceF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    startParameterF64: Double,
    endParameterF64: Double,
): Boolean {
    if (incidenceF64.inputEdgeIdI32 != projectedSpanF64F32.sourceSectionF64.inputEdgeIdI32) return false
    return projectedEndpointIsWithinOverlapIncidenceF64F32(incidenceF64, projectedSpanF64F32, startParameterF64) &&
        projectedEndpointIsWithinOverlapIncidenceF64F32(incidenceF64, projectedSpanF64F32, endParameterF64)
}

/**
 * An exact overlap witness owns an atomic source interval, not necessarily one whole post-split
 * carrier.  A projected sub-rail is therefore valid only when each of its *direct* section
 * endpoints retains the input-edge parameter recorded by that incidence and lies inside its
 * exact bounds.  This permits a cut subinterval without granting distant authority on a span.
 */
private fun projectedEndpointIsWithinOverlapIncidenceF64F32(
    incidenceF64: PathOverlapWitnessIncidenceF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    parameterF64: Double,
): Boolean {
    val endpointIdentityF64 = projectedSectionEndpointIdentityF64F32(projectedSpanF64F32, parameterF64) ?: return false
    val endpointSourceParameterF64 = when (parameterF64) {
        0.0 -> projectedSpanF64F32.sourceSectionF64.startParameterF64
        1.0 -> projectedSpanF64F32.sourceSectionF64.endParameterF64
        else -> return false
    }
    // The identity stores the carrier-local parameter while the source section stores the
    // original segment parameter.  Their direct association is the edge-ID membership; the
    // source interval below supplies the exact partial bound without converting either through a
    // coordinate or an ULP lookup.
    if (incidenceF64.inputEdgeIdI32 !in endpointIdentityF64.parameterByEdgeId) return false
    val minimumIncidenceParameterF64 = minOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
    val maximumIncidenceParameterF64 = maxOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
    return endpointSourceParameterF64 >= minimumIncidenceParameterF64 &&
        endpointSourceParameterF64 <= maximumIncidenceParameterF64
}

private fun assignProjectedCoincidencesF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathProjectedCoincidenceF32> {
    validateProjectedCoincidenceClaimsF64F32(proposalsF64F32, candidateWorkBudgetI32)
    val orderedF64F32 = sortedHybridF64F32(
        proposalsF64F32,
        candidateWorkBudgetI32,
        ::compareProjectedCoincidenceProposalsF64F32,
    )
    var nextIdI64 = 0L
    var previousF64F32: PathProjectedCoincidenceProposalF64F32? = null
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(orderedF64F32.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
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
            firstClaimF64 = proposalF64F32.firstClaimF64,
            secondClaimF64 = proposalF64F32.secondClaimF64,
        )
    }
}

/**
 * Validates the complete claim multiset before [PathProjectedCoincidenceF32] receives an ID or
 * [PathAliasGroupF32] can merge any vertices.  The sweep is carrier-local, so a long run of
 * claims is linear after the deterministic sort rather than a pairwise post-publication scan.
 */
private fun validateProjectedCoincidenceClaimsF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val claimCountI64 = checkedPathWorkMultiplyI64(proposalsF64F32.size.toLong(), 2L)
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(checkedPathWorkMultiplyI64(claimCountI64, 5L), 1L),
        candidateWorkBudgetI32,
    )
    val claimsF64 = ArrayList<PathProjectedSpanClaimF64>(
        checkedPathCapacityI32(claimCountI64, "path-candidate-limit"),
    )
    proposalsF64F32.forEach { proposalF64F32 ->
        claimsF64 += proposalF64F32.firstClaimF64
        claimsF64 += proposalF64F32.secondClaimF64
    }
    val orderedClaimsF64 = sortedHybridF64F32(claimsF64, candidateWorkBudgetI32, ::compareProjectedClaimsF64F32)
    var activeClaimF64: PathProjectedSpanClaimF64? = null
    orderedClaimsF64.forEach { claimF64 ->
        if (claimF64.startVertexIdentityF64 == null || claimF64.endVertexIdentityF64 == null) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val activeF64 = activeClaimF64
        if (activeF64 == null || !sameProjectedClaimCarrierF64F32(activeF64, claimF64)) {
            activeClaimF64 = claimF64
            return@forEach
        }
        when {
            claimF64.startParameterF64 < activeF64.endParameterF64 -> {
                if (claimF64.witnessIdI64 != activeF64.witnessIdI64) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (claimF64.endParameterF64 > activeF64.endParameterF64) activeClaimF64 = claimF64
            }
            claimF64.startParameterF64 == activeF64.endParameterF64 -> {
                if (
                    claimF64.witnessIdI64 != activeF64.witnessIdI64 &&
                        activeF64.endVertexIdentityF64 != claimF64.startVertexIdentityF64
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (claimF64.endParameterF64 >= activeF64.endParameterF64) activeClaimF64 = claimF64
            }
            else -> activeClaimF64 = claimF64
        }
    }
}

private fun sameProjectedClaimCarrierF64F32(
    firstF64: PathProjectedSpanClaimF64,
    secondF64: PathProjectedSpanClaimF64,
): Boolean = firstF64.inputEdgeIdI32 == secondF64.inputEdgeIdI32

private fun compareProjectedClaimsF64F32(
    firstF64: PathProjectedSpanClaimF64,
    secondF64: PathProjectedSpanClaimF64,
): Int {
    firstF64.inputEdgeIdI32.compareTo(secondF64.inputEdgeIdI32).takeIf { it != 0 }?.let { return it }
    firstF64.startParameterF64.compareTo(secondF64.startParameterF64).takeIf { it != 0 }?.let { return it }
    firstF64.endParameterF64.compareTo(secondF64.endParameterF64).takeIf { it != 0 }?.let { return it }
    return firstF64.witnessIdI64.compareTo(secondF64.witnessIdI64)
}

private fun buildHybridAliasGroupsF32(
    verticesF64F32: List<PathHybridVertexF64F32>,
    projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathAliasGroupF32> {
    // A claim already carries the only exact endpoint identities permitted to become aliases.
    // Do not rediscover vertices by a rounded coordinate or by a source-span membership scan:
    // that would give one point witness authority over remote sections with the same F32 image.
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(verticesF64F32.size.toLong(), 2L),
            checkedPathWorkMultiplyI64(projectedCoincidencesF32.size.toLong(), 12L),
        ),
        candidateWorkBudgetI32,
    )
    val vertexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, PathHybridVertexF64F32>()
    verticesF64F32.forEach { vertexF64F32 ->
        if (vertexByIdentityF64.put(vertexF64F32.vertexIdentityF64, vertexF64F32) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    val groupedIdentitiesF64 = linkedMapOf<PathDirectAliasKeyF64F32, MutableSet<PathVertexIdentityF64>>()
    projectedCoincidencesF32.forEach { coincidenceF32 ->
        addDirectAliasEndpointF64F32(
            groupedIdentitiesF64 = groupedIdentitiesF64,
            pointF32 = coincidenceF32.startPointF32,
            witnessF64 = coincidenceF32.pointWitnessF64,
            firstIdentityF64 = coincidenceF32.firstClaimF64.startVertexIdentityF64,
            secondIdentityF64 = coincidenceF32.secondClaimF64.startVertexIdentityF64,
            vertexByIdentityF64 = vertexByIdentityF64,
        )
        addDirectAliasEndpointF64F32(
            groupedIdentitiesF64 = groupedIdentitiesF64,
            pointF32 = coincidenceF32.endPointF32,
            witnessF64 = coincidenceF32.pointWitnessF64,
            firstIdentityF64 = coincidenceF32.firstClaimF64.endVertexIdentityF64,
            secondIdentityF64 = coincidenceF32.secondClaimF64.endVertexIdentityF64,
            vertexByIdentityF64 = vertexByIdentityF64,
        )
    }
    val identityCountI64 = groupedIdentitiesF64.values.sumOf { identitiesF64 -> identitiesF64.size.toLong() }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(identityCountI64, 3L),
            checkedPathWorkMultiplyI64(groupedIdentitiesF64.size.toLong(), 2L),
        ),
        candidateWorkBudgetI32,
    )
    val groupsF32 = groupedIdentitiesF64.map { (keyF64F32, identitiesF64) ->
        val orderedVerticesF64F32 = sortedHybridF64F32(
            identitiesF64.map { identityF64 ->
                vertexByIdentityF64[identityF64] ?: throw IllegalStateException("path-arrangement-inconsistent")
            },
            candidateWorkBudgetI32,
            ::compareHybridVerticesF64F32,
        )
        PathAliasGroupF32(
            representativePointF32 = keyF64F32.representativePointF32,
            vertexIdentitiesF64 = orderedVerticesF64F32.map(PathHybridVertexF64F32::vertexIdentityF64),
            contactWitnessF64 = keyF64F32.witnessF64,
        )
    }
    return sortedHybridF64F32(groupsF32, candidateWorkBudgetI32, ::compareAliasGroupsF32)
}

private data class PathDirectAliasKeyF64F32(
    val witnessF64: PathContactWitnessF64.PointF64,
    val representativePointF32: Point2F32,
)

private fun addDirectAliasEndpointF64F32(
    groupedIdentitiesF64: MutableMap<PathDirectAliasKeyF64F32, MutableSet<PathVertexIdentityF64>>,
    pointF32: Point2F32,
    witnessF64: PathContactWitnessF64.PointF64,
    firstIdentityF64: PathVertexIdentityF64?,
    secondIdentityF64: PathVertexIdentityF64?,
    vertexByIdentityF64: Map<PathVertexIdentityF64, PathHybridVertexF64F32>,
) {
    val firstIdentity = firstIdentityF64 ?: throw IllegalStateException("path-f32-projection-collapse")
    val secondIdentity = secondIdentityF64 ?: throw IllegalStateException("path-f32-projection-collapse")
    val firstVertexF64F32 = vertexByIdentityF64[firstIdentity]
        ?: throw IllegalStateException("path-arrangement-inconsistent")
    val secondVertexF64F32 = vertexByIdentityF64[secondIdentity]
        ?: throw IllegalStateException("path-arrangement-inconsistent")
    if (
        !sameHybridPointF32(firstVertexF64F32.representativePointF32, pointF32) ||
            !sameHybridPointF32(secondVertexF64F32.representativePointF32, pointF32)
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val canonicalPointF32 = canonicalHybridAliasPointF32(pointF32)
    groupedIdentitiesF64.getOrPut(PathDirectAliasKeyF64F32(witnessF64, canonicalPointF32)) {
        linkedSetOf()
    }.apply {
        add(firstIdentity)
        add(secondIdentity)
    }
}

private fun canonicalHybridAliasPointF32(pointF32: Point2F32): Point2F32 = Point2F32(
    x = if (pointF32.x == 0f) 0f else pointF32.x,
    y = if (pointF32.y == 0f) 0f else pointF32.y,
)

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

private fun compareHybridVerticesF64F32(firstF64F32: PathHybridVertexF64F32, secondF64F32: PathHybridVertexF64F32): Int =
    compareHybridPointsF32(firstF64F32.representativePointF32, secondF64F32.representativePointF32)
        .takeIf { it != 0 } ?: compareHybridPointsF64(firstF64F32.sourcePointF64, secondF64F32.sourcePointF64)

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
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(comparisonsI64, sizeI32.toLong()),
        candidateWorkBudgetI32,
    )
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
    return checkedPathWorkMultiplyI64(sizeI64, levelsI64)
}

private fun preflightHybridLinearF64F32(unitsI64: Long, candidateWorkBudgetI32: PathCandidateWorkBudgetI32) {
    candidateWorkBudgetI32.consumePreflightI64(unitsI64)
}
