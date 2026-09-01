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
    /** Carrier-local exact parameters retained for transactional projected cuts. */
    val startEdgeParameterF64: Double,
    val endEdgeParameterF64: Double,
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

/**
 * Explicit source-contour evidence retained for a carrier with no F32 dimension.
 *
 * `Rays` are found by walking the declared contour, including source-span boundaries and the
 * closing seam.  `EntireContour` and `Unresolved` deliberately remain distinct: the former can
 * receive a whole-contour selection proof after face selection, whereas the latter is never an
 * excuse to silently omit a dependency.
 */
internal sealed interface PathCollapsedAdjacencyF64F32 {
    data class Rays(
        val incomingDirectionF64: Vector2F64,
        val outgoingDirectionF64: Vector2F64,
        val incomingSourceSpanIdI64: Long,
        val incomingSectionIndexI32: Int,
        val outgoingSourceSpanIdI64: Long,
        val outgoingSectionIndexI32: Int,
    ) : PathCollapsedAdjacencyF64F32

    data object EntireContour : PathCollapsedAdjacencyF64F32

    data object Unresolved : PathCollapsedAdjacencyF64F32
}

/** A source span with no F32 dimension.  It remains observable to the arrangement. */
internal data class PathCollapsedIncidenceF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
    val hybridVertexF64F32: PathHybridVertexF64F32,
    val adjacencyF64F32: PathCollapsedAdjacencyF64F32,
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
    /** Immutable source provenance gate for the post-DCEL self-closed absence audit. */
    val hasSelfClosedSourcePrimitiveF64: Boolean,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val aliasGroupsF32: List<PathAliasGroupF32>,
    val projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    /** Operand-local source intervals that lacked a representable F32 section proof. */
    val operandLocalCollapsedSectionsF64F32: List<PathOperandLocalCollapsedSectionF64F32>,
)

private data class PathHybridVertexSeedF64F32(
    val identityF64: PathVertexIdentityF64,
    /** Canonical proof point used only for deterministic ordering; carriers retain each evaluation. */
    var sourcePointF64: Point2F64,
    val originalPointsF32: MutableList<Point2F32>,
    val incidentCandidatesF32: MutableList<Point2F32>,
    val incidentSourceSpanIdsI64: MutableList<Long>,
    val witnessesF64: MutableList<PathContactWitnessF64>,
)

internal data class PathProjectedSourceSpanF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
    val startVertexIndexI32: Int,
    val endVertexIndexI32: Int,
    val projectedEdgeF64: PathInputEdgeF64,
)

internal data class PathProjectedCoincidenceProposalF64F32(
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val firstSpanF64: PathProjectedSourceSpanF64F32,
    val secondSpanF64: PathProjectedSourceSpanF64F32,
    val startPointF32: Point2F32,
    val endPointF32: Point2F32,
    val firstClaimF64: PathProjectedSpanClaimF64,
    val secondClaimF64: PathProjectedSpanClaimF64,
)

/**
 * Immutable proposal group for one exact Point witness.  It is a validation transaction only:
 * no projected coincidence ID, alias, canonical vertex, or half-edge exists until every group
 * and every cross-group claim has passed.
 */
private data class PathProjectedCoincidenceTransactionF64F32(
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
)

/** Source-local key used only to reinsert already validated projected cuts into one carrier. */
private data class PathProjectedCarrierKeyF64F32(
    val sourceSpanIdI64: Long,
    val sourceSectionIndexI32: Int,
)

/**
 * A staged strict projected bound.  Its identity is structural: the canonical Point witness,
 * the original input-carrier incidence, and the exact carrier parameter are all retained before
 * an F32 representative is checked.  In particular, [representativePointF32] never exists here
 * as an authority for identity construction.
 */
private data class PathProjectedCutF64F32(
    val witnessIdI64: Long,
    /** Canonical projected event shared by every propagated carrier occurrence. */
    val eventKeyF64F32: PathProjectedEventKeyF64F32?,
    val sourceSpanIdI64: Long,
    val sourceSectionIndexI32: Int,
    val inputEdgeIdI32: Int,
    val edgeParameterF64: Double,
    val sourceParameterF64: Double,
    val identityF64: PathVertexIdentityF64,
    val canonicalPointF64: Point2F64,
    val incidencePointF64: Point2F64,
)

/**
 * Structural identity of one canonical projected event.  Its ordered endpoint identities are
 * exact source/projection provenance, while the witness scopes the only authority permitted to
 * propagate that event across an n-way rail component.
 */
private data class PathProjectedEventKeyF64F32(
    val witnessIdI64: Long,
    val endpointIdentitiesF64: List<PathVertexIdentityF64>,
)

/**
 * Immutable commit plan for projected claims.  The source topology is untouched while this plan
 * is counted, grouped, and validated; only a completely accepted plan may subdivide carriers.
 */
private data class PathProjectedClaimMaterializationF64F32(
    val proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    /** Immutable, bounded pre-commit cuts; carrier insertion waits for transaction validation. */
    val stagedCutsF64F32: List<PathProjectedCutF64F32>,
)

/**
 * Count-stage inventory for strict projected bounds.  It intentionally contains only canonical
 * structural identities: this is the separately candidate-work-bounded index required to count
 * n-way events.  Full cut records, per-cut validation groups, and carrier partitions are not
 * allocated until the public `maxIntersections` gate has admitted the exact event count.
 */
private data class PathProjectedClaimCutIdentityPlanF64F32(
    val newCutIdentitiesF64: Set<PathVertexIdentityF64>,
    val strictCutOccurrenceCountI64: Long,
)

/**
 * A projected point contact which has no direct witness yet may be the exact continuation of a
 * local rail already proposed by a Point witness.  It is deliberately only a deferred
 * observation: it cannot create an alias, claim, cut, or ID by itself.
 */
private data class PathDeferredProjectedEndpointContactF64F32(
    val firstSpanF64: PathProjectedSourceSpanF64F32,
    val firstParameterF64: Double,
    val secondSpanF64: PathProjectedSourceSpanF64F32,
    val secondParameterF64: Double,
    /** Exact full cross-operand source-component proof; this observation still publishes nothing. */
    val hasCompleteExactOppositeComponentF64: Boolean,
)

/**
 * Exact Point-witness lookup for the projected broad phase. The index is keyed solely by
 * source-span provenance and the original registry identity; a rounded coordinate is never a
 * lookup key. In particular, a candidate may observe a witness only when both of its source
 * spans carry that same witness ID.
 */
private data class PathPointWitnessIndexF64F32(
    val witnessesBySourceSpanIdI64: Map<Long, List<PathContactWitnessF64.PointF64>>,
    val witnessIdsBySourceSpanIdI64: Map<Long, Set<Long>>,
    val witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64.PointF64>>,
)

private data class PathOverlapWitnessIncidenceReferenceF64F32(
    val witnessF64: PathContactWitnessF64.OverlapF64,
    val incidenceF64: PathOverlapWitnessIncidenceF64,
)

/**
 * Direct exact-overlap incidence lookup; projected coordinates never enter this registry.
 *
 * Each edge list is ordered by the registry witness identity.  That identity is a lookup key for
 * an already-canonical atomic interval, never a geometric tie-break: equal geometry must have
 * been canonicalized by the source registry before this index exists.
 */
private data class PathOverlapWitnessIndexF64F32(
    val orderedIncidencesByInputEdgeIdI32: Map<Int, List<PathOverlapWitnessIncidenceReferenceF64F32>>,
)

/** Proven exact correspondence between two complete, oppositely-owned source contours. */
private data class PathExactOppositeContourComponentF64F32(
    val firstContourIndexI32: Int,
    val secondContourIndexI32: Int,
    /** `+1` means every matched exact rail has the same traversal; `-1` means every one reverses it. */
    val orientationI32: Int,
)

/** Source-only contour key used while proving a complete cross-operand overlap component. */
private data class PathExactSourceContourKeyF64F32(
    val operand: PathOperand,
    val contourIndexI32: Int,
)

/** Registry carrier identity; a projected coordinate is deliberately absent from this key. */
private data class PathExactSourceCarrierKeyF64F32(
    val sourceSpanIdI64: Long,
    val sectionIndexI32: Int,
)

private data class PathExactOppositeCarrierMatchF64F32(
    val counterpartCarrierKeyF64F32: PathExactSourceCarrierKeyF64F32,
    val orientationI32: Int,
)

/**
 * A deferred projected endpoint observation may be harmless only when its entire source contour
 * belongs to one bidirectionally complete exact-overlap component in the other operand.  This is
 * stricter than finding two unrelated local overlap witnesses: every carrier must have one
 * reciprocal full-interval rail, and all of those rails must share one exact orientation
 * relation (uniformly forward or uniformly reversed).
 */
private data class PathExactOppositeContourComponentPlanF64F32(
    val componentsF64F32: List<PathExactOppositeContourComponentF64F32>,
) {
    fun coversDeferredSourceComponentF64F32(
        firstF64F32: PathProjectedSourceSpanF64F32,
        secondF64F32: PathProjectedSourceSpanF64F32,
        candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    ): Boolean {
        val firstSourceSpanF64 = firstF64F32.sourceSpanF64
        val secondSourceSpanF64 = secondF64F32.sourceSpanF64
        preflightHybridLinearF64F32(componentsF64F32.size.toLong(), candidateWorkBudgetI32)
        return componentsF64F32.any { componentF64F32 ->
            componentF64F32.containsSourceContourF64F32(firstSourceSpanF64) &&
                componentF64F32.containsSourceContourF64F32(secondSourceSpanF64)
        }
    }
}

private fun PathExactOppositeContourComponentF64F32.containsSourceContourF64F32(sourceSpanF64: PathSourceSpanF64): Boolean =
    when (sourceSpanF64.operand) {
        PathOperand.FIRST -> firstContourIndexI32 == sourceSpanF64.contourIndexI32
        PathOperand.SECOND -> secondContourIndexI32 == sourceSpanF64.contourIndexI32
    }

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
    val carrierSectionCountI64 = countHybridCarrierSectionsF64F32(
        sourceSpansF64,
        candidateWorkBudgetI32,
    )
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
                preflightHybridLinearF64F32(
                    checkedPathWorkMultiplyI64(
                        checkedPathWorkAddI64(
                            witnessF64.startVertexIdentitiesF64.size.toLong(),
                            witnessF64.endVertexIdentitiesF64.size.toLong(),
                        ),
                        2L,
                    ),
                    candidateWorkBudgetI32,
                )
                witnessF64.startVertexIdentitiesF64.forEach { identityF64 ->
                    witnessesByIdentityF64.getOrPut(identityF64) { mutableListOf() } += witnessF64
                }
                witnessF64.endVertexIdentitiesF64.forEach { identityF64 ->
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
            canonicalPointF64 = sourceSectionF64.startPointF64,
            incidencePointF64 = sourceSectionF64.startIncidencePointF64,
            normalizationF64 = normalizationF64,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        addHybridVertexSeedF64F32(
            seedsByIdentityF64 = seedsByIdentityF64,
            sourceSpanF64 = sourceSpanF64,
            locationF64 = sourceSectionLocationF64(sourceSpanF64, sourceSectionF64, atStart = false),
            canonicalPointF64 = sourceSectionF64.endPointF64,
            incidencePointF64 = sourceSectionF64.endIncidencePointF64,
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
    val verticesF64F32 = ArrayList<PathHybridVertexF64F32>(orderedSeedsF64F32.size)
    orderedSeedsF64F32.forEachIndexed { indexI32, seedF64F32 ->
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(seedF64F32.originalPointsF32.size.toLong(), 2L),
                checkedPathWorkMultiplyI64(seedF64F32.incidentCandidatesF32.size.toLong(), 2L),
            ),
            candidateWorkBudgetI32,
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
        val representativePointF32 = chooseRepresentativePointF32(
            originalPointsF32 = seedF64F32.originalPointsF32,
            incidentCandidatesF32 = seedF64F32.incidentCandidatesF32,
            incidentSourceSpanIdsI64 = seedF64F32.incidentSourceSpanIdsI64,
            witnessesF64 = witnessesF64,
        )
        vertexIndexByIdentityF64[seedF64F32.identityF64] = indexI32
        verticesF64F32 += PathHybridVertexF64F32(
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
    carrierSectionsF64F32.forEachIndexed { sourceIndexI32, carrierSectionF64F32 ->
        val sourceSpanF64 = carrierSectionF64F32.sourceSpanF64
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        val startIdentityF64 = sourceSectionF64.startIdentityF64
        val endIdentityF64 = sourceSectionF64.endIdentityF64
        val startVertexIndexI32 = vertexIndexByIdentityF64.getValue(startIdentityF64)
        val endVertexIndexI32 = vertexIndexByIdentityF64.getValue(endIdentityF64)
        val startVertexF64F32 = verticesF64F32[startVertexIndexI32]
        val endVertexF64F32 = verticesF64F32[endVertexIndexI32]
        if (sameHybridPointF32(startVertexF64F32.representativePointF32, endVertexF64F32.representativePointF32)) {
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
    val collapsedIncidencesF64F32 = collectCollapsedHybridIncidencesF64F32(
        carrierSectionsF64F32 = carrierSectionsF64F32,
        verticesF64F32 = verticesF64F32,
        vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )

    // A zero-F32-length carrier is retained until face selection.  At this point the topology
    // cannot know whether it contributes to a selected boundary at all: rejecting here would
    // incorrectly reject an unrelated retained face, while omitting it would silently publish a
    // partial contour.  The arrangement makes the explicit KEEP/DROP/REJECT decision after
    // winding and before trace emission.

    val pointWitnessIndexF64F32 = buildPointWitnessIndexF64F32(
        witnessesF64 = sourceTopologyF64.contactWitnessesF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    val overlapWitnessIndexF64F32 = buildOverlapWitnessIndexF64F32(
        witnessesF64 = sourceTopologyF64.contactWitnessesF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    val proposalsF64F32 = mutableListOf<PathProjectedCoincidenceProposalF64F32>()
    val deferredEndpointContactsF64F32 = mutableListOf<PathDeferredProjectedEndpointObservationF64F32>()
    var strictInteriorCutRequirementCountI32 = 0
    var unsupportedProjectedContactCountI32 = 0
    fun processProjectedSpanContactF64F32(
        firstF64F32: PathProjectedSourceSpanF64F32,
        secondF64F32: PathProjectedSourceSpanF64F32,
        projectedContactF64: PathIntersectionF64,
    ) {
        if (
            isSameExactSourceEventF64F32(
                firstF64F32,
                secondF64F32,
                projectedContactF64,
                verticesF64F32,
                vertexIndexByIdentityF64,
            )
        ) {
            return
        }
        val pointWitnessF64 = localPointWitnessForProjectedPairF64F32(
            firstF64F32,
            secondF64F32,
            projectedContactF64,
            pointWitnessIndexF64F32,
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
                        overlapWitnessIndexF64F32,
                        candidateWorkBudgetI32,
                    )
                ) {
                    if (!isProjectedEndpointContactF64F32(projectedContactF64)) {
                        unsupportedProjectedContactCountI32 += 1
                    } else {
                        // Endpoint coordinates remain only immutable observations; the admitted
                        // route never builds deferred full-cover authority from them.
                        deferredEndpointContactsF64F32 += PathDeferredProjectedEndpointObservationF64F32(
                            firstSourceSpanIdI64 = firstF64F32.sourceSpanF64.sourceSpanIdI64,
                            firstSourceSectionIndexI32 = firstF64F32.sectionIndexI32,
                            firstParameterF64 = projectedContactF64.firstT,
                            secondSourceSpanIdI64 = secondF64F32.sourceSpanF64.sourceSpanIdI64,
                            secondSourceSectionIndexI32 = secondF64F32.sectionIndexI32,
                            secondParameterF64 = projectedContactF64.secondT,
                        )
                    }
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
                    )
                    val secondClaimF64 = projectedOverlapClaimF64F32(
                        witnessF64 = pointWitnessF64,
                        projectedSpanF64F32 = secondF64F32,
                        startParameterF64 = projectedContactF64.secondStartParameter,
                        endParameterF64 = projectedContactF64.secondEndParameter,
                    )
                    if (firstClaimF64 == null || secondClaimF64 == null) {
                        unsupportedProjectedContactCountI32 += 1
                    } else if (
                        firstClaimF64.startVertexIdentityF64 == null ||
                            firstClaimF64.endVertexIdentityF64 == null ||
                            secondClaimF64.startVertexIdentityF64 == null ||
                            secondClaimF64.endVertexIdentityF64 == null
                    ) {
                        strictInteriorCutRequirementCountI32 += 1
                    } else {
                        proposalsF64F32 += PathProjectedCoincidenceProposalF64F32(
                            pointWitnessF64 = pointWitnessF64,
                            firstSpanF64 = firstF64F32,
                            secondSpanF64 = secondF64F32,
                            startPointF32 = projectedContactF64.start.toPoint2F32(),
                            endPointF32 = projectedContactF64.end.toPoint2F32(),
                            firstClaimF64 = firstClaimF64,
                            secondClaimF64 = secondClaimF64,
                        )
                    }
                } else if (!exactOverlapSupportsProjectedOverlapF64F32(
                        firstF64F32,
                        secondF64F32,
                        projectedContactF64,
                        overlapWitnessIndexF64F32,
                        candidateWorkBudgetI32,
                    )
                ) {
                    unsupportedProjectedContactCountI32 += 1
                }
            }
        }
    }

    // The AABB index itself is conservative; this debit covers its deterministic construction
    // and immutable projected-edge view before candidate callbacks can run.
    val projectedCandidateEdgesF64 = projectedSpansF64F32.map(PathProjectedSourceSpanF64F32::projectedEdgeF64)
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(projectedCandidateEdgesF64.size.toLong(), 6L),
        candidateWorkBudgetI32,
    )
    // The shared AABB walker debits only the candidates it actually emits. Do not reserve a
    // second all-pairs envelope here: that would charge both culled pairs and the same emitted
    // candidate a second time, and makes a finely flattened curve exhaust its public budget
    // before geometry is examined.
    forEachPathEdgeCandidatePairF64(projectedCandidateEdgesF64, candidateWorkBudgetI32) {
            firstCandidateIndexI32,
            secondCandidateIndexI32,
            ->
        val projectedContactF64 = intersectPathEdgesF64(
            projectedCandidateEdgesF64[firstCandidateIndexI32],
            projectedCandidateEdgesF64[secondCandidateIndexI32],
        ) ?: return@forEachPathEdgeCandidatePairF64
        processProjectedSpanContactF64F32(
            projectedSpansF64F32[firstCandidateIndexI32],
            projectedSpansF64F32[secondCandidateIndexI32],
            projectedContactF64,
        )
    }

    val unresolvedDeferredEndpointContactsF64F32 = unresolvedDeferredEndpointObservationsF64F32(
        deferredEndpointContactsF64F32 = deferredEndpointContactsF64F32,
        proposalsF64F32 = proposalsF64F32,
        projectedSpansF64F32 = projectedSpansF64F32,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )

    val observationCopyCostI64 = checkedPathWorkAddI64(
        checkedPathWorkAddI64(
            sourceTopologyF64.contactWitnessesF64.size.toLong(),
            proposalsF64F32.size.toLong(),
        ),
        checkedPathWorkAddI64(
            unresolvedDeferredEndpointContactsF64F32.size.toLong(),
            collapsedIncidencesF64F32.size.toLong(),
        ),
    )
    preflightHybridLinearF64F32(observationCopyCostI64, candidateWorkBudgetI32)
    val observationF64F32 = PathHybridProjectionObservationF64F32(
        exactContactWitnessesF64 = sourceTopologyF64.contactWitnessesF64.toList(),
        endpointOnlyProjectedRelationsF64F32 = proposalsF64F32.toList(),
        deferredEndpointContactsF64F32 = unresolvedDeferredEndpointContactsF64F32.toList(),
        strictInteriorCutRequirementCountI32 = strictInteriorCutRequirementCountI32,
        collapsedIncidencesF64F32 = collapsedIncidencesF64F32.toList(),
        operandLocalCollapsedSectionCountI32 = sourceTopologyF64.operandLocalCollapsedSectionsF64F32.size,
        unsupportedProjectedContactCountI32 = unsupportedProjectedContactCountI32,
        canonicalSourceEventCountI32 = sourceTopologyF64.intersectionEventCountI32,
    )
    if (!supportsPathHybridProjectionObservationF64F32(observationF64F32, candidateWorkBudgetI32)) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    candidateWorkBudgetI32.requireRemainingAtLeast(
        projectedTransactionValidationUpperBoundI64F32(
            proposalCountI32 = observationF64F32.endpointOnlyProjectedRelationsF64F32.size,
            sourceSpanCountI32 = sourceTopologyF64.sourceSpansF64.size,
            witnessCountI32 = sourceTopologyF64.contactWitnessesF64.size,
        ),
    )
    val committedProposalsF64F32 = validateAndOrderProjectedCoincidenceTransactionsF64F32(
        proposalsF64F32 = observationF64F32.endpointOnlyProjectedRelationsF64F32,
        pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
        witnessesByIdentityF64 = witnessesByIdentityF64,
        verticesF64F32 = verticesF64F32,
        vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    val exactPlanF64F32 = when (
        val admissionF64F32 = PathAcceptedExactPlanF64F32.fromValidatedRelationsF64F32(
            committedEndpointOnlyProjectedRelationsF64F32 = committedProposalsF64F32,
            canonicalSourceEventCountI32 = observationF64F32.canonicalSourceEventCountI32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    ) {
        PathHybridAdmissionF64F32.Unsupported ->
            throw IllegalStateException("path-f32-projection-collapse")

        is PathHybridAdmissionF64F32.Accepted -> admissionF64F32.exactPlanF64F32
    }
    // The only accepted relation is endpoint-only.  Do not construct aliases from a hidden
    // strict cut even if an upstream invariant were to regress.
    if (exactPlanF64F32.endpointOnlyProjectedRelationsF64F32.any { proposalF64F32 ->
            proposalF64F32.firstClaimF64.startVertexIdentityF64 == null ||
                proposalF64F32.firstClaimF64.endVertexIdentityF64 == null ||
                proposalF64F32.secondClaimF64.startVertexIdentityF64 == null ||
                proposalF64F32.secondClaimF64.endVertexIdentityF64 == null
        }
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    candidateWorkBudgetI32.consume()
    if (exactPlanF64F32.canonicalSourceEventCountI32 > limitsI32.maxIntersections) {
        throw IllegalStateException("path-intersection-limit")
    }
    val projectedCoincidencesF32 = assignProjectedCoincidencesF64F32(
        exactPlanF64F32.endpointOnlyProjectedRelationsF64F32,
        candidateWorkBudgetI32,
    )
    val aliasGroupsF32 = buildHybridAliasGroupsF32(
        verticesF64F32 = verticesF64F32,
        projectedCoincidencesF32 = projectedCoincidencesF32,
        projectedCutAliasGroupsF64F32 = emptyList(),
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    return PathHybridTopologyF64F32(
        verticesF64F32 = verticesF64F32,
        sourceSpansF64 = sourceSpansF64,
        hasSelfClosedSourcePrimitiveF64 = sourceTopologyF64.hasSelfClosedSourcePrimitiveF64,
        carrierSectionsF64F32 = carrierSectionsF64F32,
        aliasGroupsF32 = aliasGroupsF32,
        projectedCoincidencesF32 = projectedCoincidencesF32,
        collapsedIncidencesF64F32 = emptyList(),
        operandLocalCollapsedSectionsF64F32 = emptyList(),
    )
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
 * A projected rail retains its direct section endpoint identities and exact source bounds as a
 * proposal.  Whether that proposal is locally authorized is deliberately decided only by the
 * transaction validator: later sections of the same span may be a contiguous continuation, but
 * they must never become authority merely because their F32 endpoints match.
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
            startEdgeParameterF64 = startParameterF64,
            endEdgeParameterF64 = endParameterF64,
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
            startEdgeParameterF64 = endParameterF64,
            endEdgeParameterF64 = startParameterF64,
            startVertexIdentityF64 = endIdentityF64,
            endVertexIdentityF64 = startIdentityF64,
        )
    }
}

/**
 * Keeps only endpoint observations that are not the immediate continuation of an already proved
 * endpoint-only relation.  This is a local identity-and-parameter proof; unlike the retired
 * deferred validator it neither consults nor permits a full-cover component.
 */
private fun unresolvedDeferredEndpointObservationsF64F32(
    deferredEndpointContactsF64F32: List<PathDeferredProjectedEndpointObservationF64F32>,
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    projectedSpansF64F32: List<PathProjectedSourceSpanF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathDeferredProjectedEndpointObservationF64F32> {
    val deferredContactCountI64 = deferredEndpointContactsF64F32.size.toLong()
    val unresolvedCapacityI32 = checkedPathCapacityI32(
        deferredContactCountI64,
        "path-candidate-limit",
    )
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalsF64F32.size.toLong(), 8L),
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(projectedSpansF64F32.size.toLong(), 2L),
                // Reserve the full deferred buffer and its worst-case one-copy result before
                // allocating it. The later immutable observation copy is a separate allocation.
                deferredContactCountI64,
            ),
        ),
        candidateWorkBudgetI32,
    )
    val spansByCarrierF64F32 = mutableMapOf<PathProjectedCarrierKeyF64F32, PathProjectedSourceSpanF64F32>()
    projectedSpansF64F32.forEach { spanF64F32 ->
        val keyF64F32 = PathProjectedCarrierKeyF64F32(
            spanF64F32.sourceSpanF64.sourceSpanIdI64,
            spanF64F32.sectionIndexI32,
        )
        if (spansByCarrierF64F32.put(keyF64F32, spanF64F32) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    val relaysByEndpointPairF64F32 = mutableMapOf<
        PathExactEndpointIdentityPairF64F32,
        MutableList<PathEndpointOnlyAdmissionRelayF64F32>,
        >()
    proposalsF64F32.forEach { proposalF64F32 ->
        addEndpointOnlyAdmissionRelayF64F32(
            relaysByEndpointPairF64F32 = relaysByEndpointPairF64F32,
            proposalF64F32 = proposalF64F32,
            firstAtStart = true,
        )
        addEndpointOnlyAdmissionRelayF64F32(
            relaysByEndpointPairF64F32 = relaysByEndpointPairF64F32,
            proposalF64F32 = proposalF64F32,
            firstAtStart = false,
        )
    }
    val unresolvedF64F32 = ArrayList<PathDeferredProjectedEndpointObservationF64F32>(unresolvedCapacityI32)
    deferredEndpointContactsF64F32.forEach { deferredF64F32 ->
        val firstSpanF64F32 = spansByCarrierF64F32[
            PathProjectedCarrierKeyF64F32(
                deferredF64F32.firstSourceSpanIdI64,
                deferredF64F32.firstSourceSectionIndexI32,
            )
        ]
        val secondSpanF64F32 = spansByCarrierF64F32[
            PathProjectedCarrierKeyF64F32(
                deferredF64F32.secondSourceSpanIdI64,
                deferredF64F32.secondSourceSectionIndexI32,
            )
        ]
        val firstIdentityF64 = firstSpanF64F32?.let { spanF64F32 ->
            projectedSectionEndpointIdentityF64F32(spanF64F32, deferredF64F32.firstParameterF64)
        }
        val secondIdentityF64 = secondSpanF64F32?.let { spanF64F32 ->
            projectedSectionEndpointIdentityF64F32(spanF64F32, deferredF64F32.secondParameterF64)
        }
        val relaysF64F32 = if (firstIdentityF64 != null && secondIdentityF64 != null) {
            relaysByEndpointPairF64F32[
                PathExactEndpointIdentityPairF64F32(firstIdentityF64, secondIdentityF64)
            ].orEmpty()
        } else {
            emptyList()
        }
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(relaysF64F32.size.toLong(), 1L),
            candidateWorkBudgetI32,
        )
        var resolvedF64F32 = false
        if (firstSpanF64F32 != null && secondSpanF64F32 != null) {
            relaysF64F32.forEach { relayF64F32 ->
                if (
                    isSourceAdjacentToClaimEndpointF64F32(
                        deferredSpanF64 = firstSpanF64F32,
                        deferredParameterF64 = deferredF64F32.firstParameterF64,
                        claimedSpanF64 = relayF64F32.firstSpanF64,
                        claimF64 = relayF64F32.firstClaimF64,
                        claimAtStart = relayF64F32.firstAtStart,
                    ) && isSourceAdjacentToClaimEndpointF64F32(
                        deferredSpanF64 = secondSpanF64F32,
                        deferredParameterF64 = deferredF64F32.secondParameterF64,
                        claimedSpanF64 = relayF64F32.secondSpanF64,
                        claimF64 = relayF64F32.secondClaimF64,
                        claimAtStart = relayF64F32.firstAtStart,
                    )
                ) {
                    resolvedF64F32 = true
                }
            }
        }
        if (!resolvedF64F32) unresolvedF64F32 += deferredF64F32
    }
    return unresolvedF64F32
}

private data class PathEndpointOnlyAdmissionRelayF64F32(
    val firstSpanF64: PathProjectedSourceSpanF64F32,
    val firstClaimF64: PathProjectedSpanClaimF64,
    val firstAtStart: Boolean,
    val secondSpanF64: PathProjectedSourceSpanF64F32,
    val secondClaimF64: PathProjectedSpanClaimF64,
)

private fun addEndpointOnlyAdmissionRelayF64F32(
    relaysByEndpointPairF64F32: MutableMap<
        PathExactEndpointIdentityPairF64F32,
        MutableList<PathEndpointOnlyAdmissionRelayF64F32>,
        >,
    proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    firstAtStart: Boolean,
) {
    val firstIdentityF64 = if (firstAtStart) {
        proposalF64F32.firstClaimF64.startVertexIdentityF64
    } else {
        proposalF64F32.firstClaimF64.endVertexIdentityF64
    } ?: throw IllegalStateException("path-f32-projection-collapse")
    val secondIdentityF64 = if (firstAtStart) {
        proposalF64F32.secondClaimF64.startVertexIdentityF64
    } else {
        proposalF64F32.secondClaimF64.endVertexIdentityF64
    } ?: throw IllegalStateException("path-f32-projection-collapse")
    val relayF64F32 = PathEndpointOnlyAdmissionRelayF64F32(
        firstSpanF64 = proposalF64F32.firstSpanF64,
        firstClaimF64 = proposalF64F32.firstClaimF64,
        firstAtStart = firstAtStart,
        secondSpanF64 = proposalF64F32.secondSpanF64,
        secondClaimF64 = proposalF64F32.secondClaimF64,
    )
    relaysByEndpointPairF64F32.getOrPut(
        PathExactEndpointIdentityPairF64F32(firstIdentityF64, secondIdentityF64),
    ) { mutableListOf() } += relayF64F32
    relaysByEndpointPairF64F32.getOrPut(
        PathExactEndpointIdentityPairF64F32(secondIdentityF64, firstIdentityF64),
    ) { mutableListOf() } += PathEndpointOnlyAdmissionRelayF64F32(
        firstSpanF64 = proposalF64F32.secondSpanF64,
        firstClaimF64 = proposalF64F32.secondClaimF64,
        firstAtStart = firstAtStart,
        secondSpanF64 = proposalF64F32.firstSpanF64,
        secondClaimF64 = proposalF64F32.firstClaimF64,
    )
}

private const val projectedCutIdentityNamespaceI32: Int = 3

/**
 * Builds every prospective strict bound before source carriers are subdivided.  The initial
 * projected carriers are only a local read-only probe: this function groups every new cut,
 * checks the combined source/hybrid intersection ceiling, and fills claim endpoint identities
 * before a final carrier, alias, vertex, or DCEL object can be published.
 */
private fun materializeProjectedClaimPlanF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    sourceTopologyF64: PathSourceTopologyF64,
    normalizationF64: PathNormalizationF64,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathProjectedClaimMaterializationF64F32 {
    if (proposalsF64F32.isEmpty()) {
        preflightHybridLinearF64F32(1L, candidateWorkBudgetI32)
        return PathProjectedClaimMaterializationF64F32(emptyList(), emptyList())
    }
    val proposalCountI64 = proposalsF64F32.size.toLong()
    val endpointCountI64 = checkedPathWorkMultiplyI64(proposalCountI64, 4L)
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(endpointCountI64, 4L),
            proposalCountI64,
        ),
        candidateWorkBudgetI32,
    )
    // Endpoint identities are derived before the count stage: strict endpoints receive their
    // structural projected identity here, while physical cut records stay deferred.  No
    // carrier/vertex/DCEL allocation has happened yet.
    val materializedProposalsF64F32 = proposalsF64F32.map { proposalF64F32 ->
        proposalF64F32.copy(
            firstClaimF64 = materializeProjectedClaimEndpointIdentitiesF64F32(
                claimF64 = proposalF64F32.firstClaimF64,
                projectedSpanF64F32 = proposalF64F32.firstSpanF64,
                witnessF64 = proposalF64F32.pointWitnessF64,
            ),
            secondClaimF64 = materializeProjectedClaimEndpointIdentitiesF64F32(
                claimF64 = proposalF64F32.secondClaimF64,
                projectedSpanF64F32 = proposalF64F32.secondSpanF64,
                witnessF64 = proposalF64F32.pointWitnessF64,
            ),
        )
    }
    // This separately debited candidate-bounded raw endpoint scan builds only the exact identity
    // index needed for canonical n-way counting; a physical `PathProjectedCutF64F32` is
    // deliberately deferred until after `maxIntersections` passes.
    val cutIdentityPlanF64F32 = collectProjectedClaimCutIdentityPlanF64F32(
        proposalsF64F32 = materializedProposalsF64F32,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    // The canonical group joins pairwise n-way relations through their shared structural
    // identities; a rounded projected point is never a group key.  This is the exact logical
    // event count, not an approximation by witnesses or by individual carrier incidences.
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalCountI64, 14L),
            checkedPathWorkMultiplyI64(cutIdentityPlanF64F32.newCutIdentitiesF64.size.toLong(), 6L),
        ),
        candidateWorkBudgetI32,
    )
    // Every endpoint relation with distinct exact identities is a projected event, whether it
    // inserts a physical cut or already lies on a carrier endpoint.  Only an exact common
    // identity is a no-op.  N-way/propagated occurrences share one structural event key and are
    // therefore charged once.
    val projectedEventPlanF64F32 = canonicalProjectedCutGroupCountF64F32(
        proposalsF64F32 = materializedProposalsF64F32,
        newCutIdentitiesF64 = cutIdentityPlanF64F32.newCutIdentitiesF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    val newCanonicalCutGroupCountI64 = projectedEventPlanF64F32.eventKeysF64F32.size.toLong()
    val totalIntersectionGroupsI64 = checkedPathWorkAddI64(
        sourceTopologyF64.intersectionEventCountI32.toLong(),
        newCanonicalCutGroupCountI64,
    )
    if (totalIntersectionGroupsI64 > limitsI32.maxIntersections.toLong()) {
        throw IllegalStateException("path-intersection-limit")
    }

    // The exact total is admitted before physical cut/per-identity-group allocation.  The
    // count-stage relation index above is separately bounded by candidate work; final carrier
    // insertion remains outside this function, after every witness transaction and deferred-
    // contact conflict has been validated by the caller.
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(cutIdentityPlanF64F32.strictCutOccurrenceCountI64, 3L),
            cutIdentityPlanF64F32.newCutIdentitiesF64.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    val eventKeyedCutsF64F32 = materializeProjectedClaimEndpointCutsF64F32(
        proposalsF64F32 = materializedProposalsF64F32,
        normalizationF64 = normalizationF64,
        eventKeyByCutIdentityF64 = projectedEventPlanF64F32.eventKeyByCutIdentityF64,
        expectedStrictCutOccurrenceCountI64 = cutIdentityPlanF64F32.strictCutOccurrenceCountI64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    // A structural identity is the canonical n-way cut group.  This validation is intentionally
    // after the public event gate and before any source carrier, alias, vertex, or DCEL mutation.
    validateProjectedCutIdentityGroupsF64F32(
        stagedCutsF64F32 = eventKeyedCutsF64F32,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )

    return PathProjectedClaimMaterializationF64F32(
        proposalsF64F32 = materializedProposalsF64F32,
        stagedCutsF64F32 = eventKeyedCutsF64F32,
    )
}

/**
 * One endpoint relation of a projected rail.  The two exact identities are normalized only so
 * that `A~B` and `B~A` share a bookkeeping node; no F32 coordinate, carrier index, or input
 * order participates in this key.
 */
private data class PathProjectedEndpointRelationF64F32(
    val firstIdentityF64: PathVertexIdentityF64,
    val secondIdentityF64: PathVertexIdentityF64,
)

private data class PathProjectedEndpointRelationNodeF64F32(
    val witnessF64: PathContactWitnessF64.PointF64,
    val relationF64F32: PathProjectedEndpointRelationF64F32,
    val hasNewCut: Boolean,
)

private data class PathProjectedEventPlanF64F32(
    /** One structural key for every debited canonical projected endpoint event. */
    val eventKeysF64F32: List<PathProjectedEventKeyF64F32>,
    val eventKeyByCutIdentityF64: Map<PathVertexIdentityF64, PathProjectedEventKeyF64F32>,
)

/** One proposal edge in the structural endpoint-continuation graph. */
private data class PathProjectedEndpointOccurrenceF64F32(
    val startNodeI32: Int,
    val endNodeI32: Int,
    val railPairKeyF64F32: PathProjectedCoincidencePairKeyF64F32,
)

private data class PathProjectedCutAliasGroupF64F32(
    val witnessF64: PathContactWitnessF64.PointF64,
    val representativePointF32: Point2F32,
    val vertexIdentitiesF64: List<PathVertexIdentityF64>,
)

private data class PathProjectedCutPartitionPlanF64F32(
    val cutsByCarrierF64F32: Map<PathProjectedCarrierKeyF64F32, List<PathProjectedCutF64F32>>,
    val aliasGroupsF64F32: List<PathProjectedCutAliasGroupF64F32>,
)

/** A small local union-find used only by the immutable pre-commit plan. */
private class PathProjectedCutGroupUnionI32(sizeI32: Int) {
    private val parentsI32 = IntArray(sizeI32) { indexI32 -> indexI32 }

    fun find(indexI32: Int): Int {
        var cursorI32 = indexI32
        while (parentsI32[cursorI32] != cursorI32) {
            cursorI32 = parentsI32[cursorI32]
        }
        var rewriteI32 = indexI32
        while (parentsI32[rewriteI32] != rewriteI32) {
            val nextI32 = parentsI32[rewriteI32]
            parentsI32[rewriteI32] = cursorI32
            rewriteI32 = nextI32
        }
        return cursorI32
    }

    fun union(firstI32: Int, secondI32: Int) {
        val firstRootI32 = find(firstI32)
        val secondRootI32 = find(secondI32)
        if (firstRootI32 != secondRootI32) parentsI32[secondRootI32] = firstRootI32
    }
}

private fun canonicalProjectedCutGroupCountF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    newCutIdentitiesF64: Set<PathVertexIdentityF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathProjectedEventPlanF64F32 {
    val proposalCountI64 = proposalsF64F32.size.toLong()
    val endpointCapacityI64 = checkedPathWorkMultiplyI64(proposalCountI64, 2L)
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(endpointCapacityI64, 12L),
            checkedPathWorkMultiplyI64(proposalCountI64, 4L),
        ),
        candidateWorkBudgetI32,
    )
    val endpointNodesF64F32 = ArrayList<PathProjectedEndpointRelationNodeF64F32>(
        checkedPathCapacityI32(endpointCapacityI64, "path-candidate-limit"),
    )
    val endpointNodeByRelationF64F32 = mutableMapOf<PathProjectedEndpointRelationF64F32, Int>()
    val firstNodeByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    val endpointOccurrencesF64F32 = ArrayList<PathProjectedEndpointOccurrenceF64F32>(
        checkedPathCapacityI32(proposalCountI64, "path-candidate-limit"),
    )

    fun endpointNodeI32(
        witnessF64: PathContactWitnessF64.PointF64,
        firstIdentityF64: PathVertexIdentityF64?,
        secondIdentityF64: PathVertexIdentityF64?,
    ): Int {
        val relationF64F32 = projectedEndpointRelationF64F32(firstIdentityF64, secondIdentityF64)
        val existingIndexI32 = endpointNodeByRelationF64F32[relationF64F32]
        if (existingIndexI32 != null) {
            if (endpointNodesF64F32[existingIndexI32].witnessF64 != witnessF64) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            return existingIndexI32
        }
        val indexI32 = endpointNodesF64F32.size
        endpointNodeByRelationF64F32[relationF64F32] = indexI32
        endpointNodesF64F32 += PathProjectedEndpointRelationNodeF64F32(
            witnessF64 = witnessF64,
            relationF64F32 = relationF64F32,
            hasNewCut =
                relationF64F32.firstIdentityF64 in newCutIdentitiesF64 ||
                    relationF64F32.secondIdentityF64 in newCutIdentitiesF64,
        )
        return indexI32
    }

    proposalsF64F32.forEach { proposalF64F32 ->
        val startNodeI32 = endpointNodeI32(
            proposalF64F32.pointWitnessF64,
            proposalF64F32.firstClaimF64.startVertexIdentityF64,
            proposalF64F32.secondClaimF64.startVertexIdentityF64,
        )
        val endNodeI32 = endpointNodeI32(
            proposalF64F32.pointWitnessF64,
            proposalF64F32.firstClaimF64.endVertexIdentityF64,
            proposalF64F32.secondClaimF64.endVertexIdentityF64,
        )
        val firstSourceSpanIdI64 = proposalF64F32.firstClaimF64.sourceSpanIdI64
        val secondSourceSpanIdI64 = proposalF64F32.secondClaimF64.sourceSpanIdI64
        if (firstSourceSpanIdI64 == secondSourceSpanIdI64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        endpointOccurrencesF64F32 += PathProjectedEndpointOccurrenceF64F32(
            startNodeI32 = startNodeI32,
            endNodeI32 = endNodeI32,
            railPairKeyF64F32 = PathProjectedCoincidencePairKeyF64F32(
                witnessIdI64 = proposalF64F32.pointWitnessF64.witnessIdI64,
                lowerSourceSpanIdI64 = minOf(firstSourceSpanIdI64, secondSourceSpanIdI64),
                upperSourceSpanIdI64 = maxOf(firstSourceSpanIdI64, secondSourceSpanIdI64),
            ),
        )
    }
    if (endpointNodesF64F32.isEmpty()) {
        return PathProjectedEventPlanF64F32(emptyList(), emptyMap())
    }

    // Relation nodes which share an exact endpoint identity are one n-way event.  This joins
    // A~B and A~C without letting an operand, contour, segment, or F32 representative choose a
    // winner.  The separate occurrence graph below distinguishes a literal endpoint relation
    // from a degree-two flattening continuation of that same rail pair.
    val groupsI32 = PathProjectedCutGroupUnionI32(endpointNodesF64F32.size)
    endpointNodesF64F32.forEachIndexed { nodeIndexI32, nodeF64F32 ->
        listOf(nodeF64F32.relationF64F32.firstIdentityF64, nodeF64F32.relationF64F32.secondIdentityF64)
            .distinct()
            .forEach { identityF64 ->
                val previousNodeIndexI32 = firstNodeByIdentityF64.put(identityF64, nodeIndexI32)
                if (previousNodeIndexI32 != null) {
                    if (endpointNodesF64F32[previousNodeIndexI32].witnessF64 != nodeF64F32.witnessF64) {
                        throw IllegalStateException("path-f32-projection-collapse")
                    }
                    groupsI32.union(previousNodeIndexI32, nodeIndexI32)
                }
            }
    }

    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(endpointNodesF64F32.size.toLong(), 12L),
            checkedPathWorkMultiplyI64(endpointOccurrencesF64F32.size.toLong(), 8L),
        ),
        candidateWorkBudgetI32,
    )
    val degreeByRootI32 = IntArray(endpointNodesF64F32.size)
    val railPairsByRootI32 = mutableMapOf<Int, MutableSet<PathProjectedCoincidencePairKeyF64F32>>()
    val occurrenceComponentsI32 = PathProjectedCutGroupUnionI32(endpointNodesF64F32.size)
    endpointOccurrencesF64F32.forEach { occurrenceF64F32 ->
        val startRootI32 = groupsI32.find(occurrenceF64F32.startNodeI32)
        val endRootI32 = groupsI32.find(occurrenceF64F32.endNodeI32)
        degreeByRootI32[startRootI32] += 1
        degreeByRootI32[endRootI32] += 1
        railPairsByRootI32.getOrPut(startRootI32) { linkedSetOf() } += occurrenceF64F32.railPairKeyF64F32
        railPairsByRootI32.getOrPut(endRootI32) { linkedSetOf() } += occurrenceF64F32.railPairKeyF64F32
        occurrenceComponentsI32.union(startRootI32, endRootI32)
    }
    val hasNewCutByRootI32 = BooleanArray(endpointNodesF64F32.size)
    val hasDistinctIdentitiesByRootI32 = BooleanArray(endpointNodesF64F32.size)
    val identitiesByRootI32 = mutableMapOf<Int, MutableSet<PathVertexIdentityF64>>()
    endpointNodesF64F32.forEachIndexed { nodeIndexI32, nodeF64F32 ->
        val rootI32 = groupsI32.find(nodeIndexI32)
        hasNewCutByRootI32[rootI32] = hasNewCutByRootI32[rootI32] || nodeF64F32.hasNewCut
        hasDistinctIdentitiesByRootI32[rootI32] = hasDistinctIdentitiesByRootI32[rootI32] ||
            nodeF64F32.relationF64F32.firstIdentityF64 != nodeF64F32.relationF64F32.secondIdentityF64
        identitiesByRootI32.getOrPut(rootI32) { linkedSetOf() }.apply {
            add(nodeF64F32.relationF64F32.firstIdentityF64)
            add(nodeF64F32.relationF64F32.secondIdentityF64)
        }
    }

    val selectedEventByRootI32 = BooleanArray(endpointNodesF64F32.size)
    val fallbackRootByOccurrenceComponentI32 = mutableMapOf<Int, Int>()
    val hasSelectedEventByOccurrenceComponentI32 = mutableSetOf<Int>()
    endpointNodesF64F32.indices.forEach { nodeIndexI32 ->
        if (groupsI32.find(nodeIndexI32) != nodeIndexI32) return@forEach
        val occurrenceComponentI32 = occurrenceComponentsI32.find(nodeIndexI32)
        val isDistinctRelation = hasDistinctIdentitiesByRootI32[nodeIndexI32]
        val isFlatteningContinuation =
            !hasNewCutByRootI32[nodeIndexI32] &&
                degreeByRootI32[nodeIndexI32] == 2 &&
                railPairsByRootI32[nodeIndexI32]?.size == 1
        val isSemanticEvent =
            (hasNewCutByRootI32[nodeIndexI32] || isDistinctRelation) && !isFlatteningContinuation
        if (isSemanticEvent) {
            selectedEventByRootI32[nodeIndexI32] = true
            hasSelectedEventByOccurrenceComponentI32 += occurrenceComponentI32
        }
        if (hasNewCutByRootI32[nodeIndexI32] || isDistinctRelation) {
            val previousRootI32 = fallbackRootByOccurrenceComponentI32[occurrenceComponentI32]
            if (
                previousRootI32 == null ||
                    compareProjectedEndpointRelationsF64F32(
                        endpointNodesF64F32[nodeIndexI32].relationF64F32,
                        endpointNodesF64F32[previousRootI32].relationF64F32,
                    ) < 0
            ) {
                fallbackRootByOccurrenceComponentI32[occurrenceComponentI32] = nodeIndexI32
            }
        }
    }
    fallbackRootByOccurrenceComponentI32.forEach { (occurrenceComponentI32, rootI32) ->
        if (occurrenceComponentI32 !in hasSelectedEventByOccurrenceComponentI32) {
            // A closed same-pair continuation has no degree-one terminal.  It still contains a
            // distinct relation, so select one deterministic structural event instead of letting
            // an entire projected cycle become a free no-op.
            selectedEventByRootI32[rootI32] = true
        }
    }

    val eventKeysF64F32 = ArrayList<PathProjectedEventKeyF64F32>(
        checkedPathCapacityI32(endpointNodesF64F32.size.toLong(), "path-candidate-limit"),
    )
    val eventKeyByCutIdentityF64 = mutableMapOf<PathVertexIdentityF64, PathProjectedEventKeyF64F32>()
    endpointNodesF64F32.indices.forEach { nodeIndexI32 ->
        if (groupsI32.find(nodeIndexI32) != nodeIndexI32 || !selectedEventByRootI32[nodeIndexI32]) {
            return@forEach
        }
        val rootIdentitiesF64 = identitiesByRootI32[nodeIndexI32]
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val orderedIdentitiesF64 = sortedHybridF64F32(
            rootIdentitiesF64.toList(),
            candidateWorkBudgetI32,
            ::comparePathVertexIdentitiesStructuralF64,
        )
        val eventKeyF64F32 = PathProjectedEventKeyF64F32(
            witnessIdI64 = endpointNodesF64F32[nodeIndexI32].witnessF64.witnessIdI64,
            endpointIdentitiesF64 = orderedIdentitiesF64,
        )
        eventKeysF64F32 += eventKeyF64F32
        if (!hasNewCutByRootI32[nodeIndexI32]) return@forEach
        orderedIdentitiesF64.forEach { identityF64 ->
            if (identityF64 !in newCutIdentitiesF64) return@forEach
            val previousEventKeyF64F32 = eventKeyByCutIdentityF64.put(identityF64, eventKeyF64F32)
            if (previousEventKeyF64F32 != null && previousEventKeyF64F32 != eventKeyF64F32) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
    if (eventKeyByCutIdentityF64.keys != newCutIdentitiesF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return PathProjectedEventPlanF64F32(
        eventKeysF64F32 = sortedHybridF64F32(
            eventKeysF64F32,
            candidateWorkBudgetI32,
            ::compareProjectedEventKeysF64F32,
        ),
        eventKeyByCutIdentityF64 = eventKeyByCutIdentityF64,
    )
}

private fun projectedEndpointRelationF64F32(
    firstIdentityF64: PathVertexIdentityF64?,
    secondIdentityF64: PathVertexIdentityF64?,
): PathProjectedEndpointRelationF64F32 {
    if (firstIdentityF64 == null || secondIdentityF64 == null) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return if (comparePathVertexIdentitiesStructuralF64(firstIdentityF64, secondIdentityF64) <= 0) {
        PathProjectedEndpointRelationF64F32(firstIdentityF64, secondIdentityF64)
    } else {
        PathProjectedEndpointRelationF64F32(secondIdentityF64, firstIdentityF64)
    }
}

private fun compareProjectedEndpointRelationsF64F32(
    firstF64F32: PathProjectedEndpointRelationF64F32,
    secondF64F32: PathProjectedEndpointRelationF64F32,
): Int = comparePathVertexIdentitiesStructuralF64(
    firstF64F32.firstIdentityF64,
    secondF64F32.firstIdentityF64,
).takeIf { it != 0 }
    ?: comparePathVertexIdentitiesStructuralF64(
        firstF64F32.secondIdentityF64,
        secondF64F32.secondIdentityF64,
    )

private fun compareProjectedEventKeysF64F32(
    firstF64F32: PathProjectedEventKeyF64F32,
    secondF64F32: PathProjectedEventKeyF64F32,
): Int {
    firstF64F32.witnessIdI64.compareTo(secondF64F32.witnessIdI64).takeIf { it != 0 }?.let { return it }
    firstF64F32.endpointIdentitiesF64.size.compareTo(secondF64F32.endpointIdentitiesF64.size)
        .takeIf { it != 0 }
        ?.let { return it }
    firstF64F32.endpointIdentitiesF64.indices.forEach { indexI32 ->
        comparePathVertexIdentitiesStructuralF64(
            firstF64F32.endpointIdentitiesF64[indexI32],
            secondF64F32.endpointIdentitiesF64[indexI32],
        ).takeIf { it != 0 }?.let { return it }
    }
    return 0
}

/**
 * The proposal's F32 endpoints are only an already-observed contact check.  The edge parameter
 * decides which endpoint is being validated, so reversed source traversal cannot accidentally
 * attach a structural cut to the wrong side of the rail.
 */
private fun projectedClaimEndpointRepresentativePointF32(
    proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    claimF64: PathProjectedSpanClaimF64,
    atStart: Boolean,
): Point2F32 {
    val edgeParameterF64 = if (atStart) claimF64.startEdgeParameterF64 else claimF64.endEdgeParameterF64
    val projectedPointF32 = projectedSectionPointAtCutF64F32(
        projectedSpanF64F32.projectedEdgeF64.startPointF64,
        projectedSpanF64F32.projectedEdgeF64.endPointF64,
        edgeParameterF64,
    ).toPoint2F32()
    val matchesStart = sameHybridPointF32(projectedPointF32, proposalF64F32.startPointF32)
    val matchesEnd = sameHybridPointF32(projectedPointF32, proposalF64F32.endPointF32)
    if (matchesStart == matchesEnd) throw IllegalStateException("path-f32-projection-collapse")
    return if (matchesStart) proposalF64F32.startPointF32 else proposalF64F32.endPointF32
}

private inline fun forEachProjectedClaimEndpointF64F32(
    proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    actionF64F32: (
        claimF64: PathProjectedSpanClaimF64,
        projectedSpanF64F32: PathProjectedSourceSpanF64F32,
        atStart: Boolean,
    ) -> Unit,
) {
    actionF64F32(proposalF64F32.firstClaimF64, proposalF64F32.firstSpanF64, true)
    actionF64F32(proposalF64F32.firstClaimF64, proposalF64F32.firstSpanF64, false)
    actionF64F32(proposalF64F32.secondClaimF64, proposalF64F32.secondSpanF64, true)
    actionF64F32(proposalF64F32.secondClaimF64, proposalF64F32.secondSpanF64, false)
}

/**
 * First phase of the projected-cut materializer.  The resulting set is a compact canonical
 * identity index, not a carrier or cut allocation.  Its fixed endpoint scan is debited before
 * reading any proposal, so it remains independently bounded by `maxCandidateProbes` while the
 * event ceiling is still unknown.
 */
private fun collectProjectedClaimCutIdentityPlanF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathProjectedClaimCutIdentityPlanF64F32 {
    val endpointCountI64 = checkedPathWorkMultiplyI64(proposalsF64F32.size.toLong(), 4L)
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(endpointCountI64, 2L),
        candidateWorkBudgetI32,
    )
    val newCutIdentitiesF64 = linkedSetOf<PathVertexIdentityF64>()
    var strictCutOccurrenceCountI64 = 0L
    proposalsF64F32.forEach { proposalF64F32 ->
        forEachProjectedClaimEndpointF64F32(proposalF64F32) { claimF64, projectedSpanF64F32, atStart ->
            val identityF64 = projectedClaimEndpointStrictIdentityF64F32(
                claimF64 = claimF64,
                projectedSpanF64F32 = projectedSpanF64F32,
                witnessF64 = proposalF64F32.pointWitnessF64,
                atStart = atStart,
            ) ?: return@forEachProjectedClaimEndpointF64F32
            strictCutOccurrenceCountI64 = checkedPathWorkAddI64(strictCutOccurrenceCountI64, 1L)
            newCutIdentitiesF64 += identityF64
        }
    }
    return PathProjectedClaimCutIdentityPlanF64F32(
        newCutIdentitiesF64 = newCutIdentitiesF64,
        strictCutOccurrenceCountI64 = strictCutOccurrenceCountI64,
    )
}

/**
 * Second phase of the projected-cut materializer.  This is intentionally called only after the
 * exact source-plus-projected event total has passed `maxIntersections`; no raw continuation can
 * make physical cut records appear before that public gate.
 */
private fun materializeProjectedClaimEndpointCutsF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    normalizationF64: PathNormalizationF64,
    eventKeyByCutIdentityF64: Map<PathVertexIdentityF64, PathProjectedEventKeyF64F32>,
    expectedStrictCutOccurrenceCountI64: Long,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathProjectedCutF64F32> {
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(expectedStrictCutOccurrenceCountI64, 2L),
        candidateWorkBudgetI32,
    )
    val stagedCutsF64F32 = ArrayList<PathProjectedCutF64F32>(
        checkedPathCapacityI32(expectedStrictCutOccurrenceCountI64, "path-candidate-limit"),
    )
    proposalsF64F32.forEach { proposalF64F32 ->
        forEachProjectedClaimEndpointF64F32(proposalF64F32) { claimF64, projectedSpanF64F32, atStart ->
            addProjectedClaimEndpointCutF64F32(
                stagedCutsF64F32 = stagedCutsF64F32,
                claimF64 = claimF64,
                projectedSpanF64F32 = projectedSpanF64F32,
                witnessF64 = proposalF64F32.pointWitnessF64,
                expectedPointF32 = projectedClaimEndpointRepresentativePointF32(
                    proposalF64F32 = proposalF64F32,
                    projectedSpanF64F32 = projectedSpanF64F32,
                    claimF64 = claimF64,
                    atStart = atStart,
                ),
                atStart = atStart,
                normalizationF64 = normalizationF64,
                eventKeyByCutIdentityF64 = eventKeyByCutIdentityF64,
            )
        }
    }
    if (stagedCutsF64F32.size.toLong() != expectedStrictCutOccurrenceCountI64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return stagedCutsF64F32
}

/**
 * A structural identity is the canonical n-way cut group.  This validation is deliberately
 * post-limit and pre-publication: a coordinate, contour label, carrier index, or input order
 * never resolves a disagreement inside one claimed identity group.
 */
private fun validateProjectedCutIdentityGroupsF64F32(
    stagedCutsF64F32: List<PathProjectedCutF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(stagedCutsF64F32.size.toLong(), 5L),
        candidateWorkBudgetI32,
    )
    val cutsByIdentityF64 = linkedMapOf<PathVertexIdentityF64, MutableList<PathProjectedCutF64F32>>()
    stagedCutsF64F32.forEach { cutF64F32 ->
        cutsByIdentityF64.getOrPut(cutF64F32.identityF64) { mutableListOf() } += cutF64F32
    }
    preflightHybridLinearF64F32(cutsByIdentityF64.size.toLong(), candidateWorkBudgetI32)
    cutsByIdentityF64.values.forEach { groupF64F32 ->
        val canonicalCutF64F32 = groupF64F32.firstOrNull()
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        preflightHybridLinearF64F32(groupF64F32.size.toLong(), candidateWorkBudgetI32)
        groupF64F32.drop(1).forEach { cutF64F32 ->
            if (
                cutF64F32.witnessIdI64 != canonicalCutF64F32.witnessIdI64 ||
                    cutF64F32.inputEdgeIdI32 != canonicalCutF64F32.inputEdgeIdI32 ||
                    cutF64F32.edgeParameterF64 != canonicalCutF64F32.edgeParameterF64 ||
                    cutF64F32.sourceParameterF64 != canonicalCutF64F32.sourceParameterF64 ||
                    !sameHybridPointF64(cutF64F32.canonicalPointF64, canonicalCutF64F32.canonicalPointF64) ||
                    !sameHybridPointF64(cutF64F32.incidencePointF64, canonicalCutF64F32.incidencePointF64)
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
}

private fun addProjectedClaimEndpointCutF64F32(
    stagedCutsF64F32: MutableList<PathProjectedCutF64F32>,
    claimF64: PathProjectedSpanClaimF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    expectedPointF32: Point2F32,
    atStart: Boolean,
    normalizationF64: PathNormalizationF64,
    eventKeyByCutIdentityF64: Map<PathVertexIdentityF64, PathProjectedEventKeyF64F32>,
) {
    val edgeParameterF64 = if (atStart) claimF64.startEdgeParameterF64 else claimF64.endEdgeParameterF64
    val sourceParameterF64 = if (atStart) claimF64.startParameterF64 else claimF64.endParameterF64
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    val projectedIdentityF64 = projectedClaimEndpointStrictIdentityF64F32(
        claimF64 = claimF64,
        projectedSpanF64F32 = projectedSpanF64F32,
        witnessF64 = witnessF64,
        atStart = atStart,
    ) ?: return
    val canonicalPointF64 = projectedSectionPointAtCutF64F32(
        sourceSectionF64.startPointF64,
        sourceSectionF64.endPointF64,
        edgeParameterF64,
    )
    val incidencePointF64 = projectedSectionPointAtCutF64F32(
        sourceSectionF64.startIncidencePointF64,
        sourceSectionF64.endIncidencePointF64,
        edgeParameterF64,
    )
    val representativePointF32 = normalizationF64.denormalize(incidencePointF64)
    if (!sameHybridPointF32(representativePointF32, expectedPointF32)) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    stagedCutsF64F32 += PathProjectedCutF64F32(
        witnessIdI64 = witnessF64.witnessIdI64,
        eventKeyF64F32 = eventKeyByCutIdentityF64[projectedIdentityF64]
            ?: throw IllegalStateException("path-f32-projection-collapse"),
        sourceSpanIdI64 = projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
        sourceSectionIndexI32 = projectedSpanF64F32.sectionIndexI32,
        inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
        edgeParameterF64 = edgeParameterF64,
        sourceParameterF64 = sourceParameterF64,
        identityF64 = projectedIdentityF64,
        canonicalPointF64 = canonicalPointF64,
        incidencePointF64 = incidencePointF64,
    )
}

/**
 * Validates the exact provenance of one endpoint without allocating a physical cut.  The
 * materialized claim identity is the authority; F32 is intentionally deferred to the embedding
 * validation performed after the public intersection gate.
 */
private fun projectedClaimEndpointStrictIdentityF64F32(
    claimF64: PathProjectedSpanClaimF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    atStart: Boolean,
): PathVertexIdentityF64? {
    val edgeParameterF64 = if (atStart) claimF64.startEdgeParameterF64 else claimF64.endEdgeParameterF64
    val sourceParameterF64 = if (atStart) claimF64.startParameterF64 else claimF64.endParameterF64
    val suppliedIdentityF64 = if (atStart) claimF64.startVertexIdentityF64 else claimF64.endVertexIdentityF64
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    val endpointIdentityF64 = projectedSectionEndpointIdentityF64F32(projectedSpanF64F32, edgeParameterF64)
    if (endpointIdentityF64 != null) {
        if (suppliedIdentityF64 != null && suppliedIdentityF64 != endpointIdentityF64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        return null
    }
    if (
        edgeParameterF64 <= 0.0 || edgeParameterF64 >= 1.0 ||
            claimF64.witnessIdI64 != witnessF64.witnessIdI64 ||
            claimF64.sourceSpanIdI64 != projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64 ||
            claimF64.sourceSectionIndexI32 != projectedSpanF64F32.sectionIndexI32 ||
            claimF64.inputEdgeIdI32 != sourceSectionF64.inputEdgeIdI32
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val materializedSourceParameterF64 = projectedSourceParameterAtSectionCutF64F32(
        sourceSectionF64,
        edgeParameterF64,
    )
    if (materializedSourceParameterF64 != sourceParameterF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val projectedIdentityF64 = projectedCutIdentityF64F32(
        witnessF64 = witnessF64,
        sourceSectionF64 = sourceSectionF64,
        edgeParameterF64 = edgeParameterF64,
    )
    if (suppliedIdentityF64 != null && suppliedIdentityF64 != projectedIdentityF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return projectedIdentityF64
}

private fun materializeProjectedClaimEndpointIdentitiesF64F32(
    claimF64: PathProjectedSpanClaimF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
): PathProjectedSpanClaimF64 = claimF64.copy(
    startVertexIdentityF64 = projectedClaimEndpointIdentityF64F32(
        claimF64 = claimF64,
        projectedSpanF64F32 = projectedSpanF64F32,
        witnessF64 = witnessF64,
        atStart = true,
    ),
    endVertexIdentityF64 = projectedClaimEndpointIdentityF64F32(
        claimF64 = claimF64,
        projectedSpanF64F32 = projectedSpanF64F32,
        witnessF64 = witnessF64,
        atStart = false,
    ),
)

private fun projectedClaimEndpointIdentityF64F32(
    claimF64: PathProjectedSpanClaimF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    atStart: Boolean,
): PathVertexIdentityF64 {
    val edgeParameterF64 = if (atStart) claimF64.startEdgeParameterF64 else claimF64.endEdgeParameterF64
    val suppliedIdentityF64 = if (atStart) claimF64.startVertexIdentityF64 else claimF64.endVertexIdentityF64
    val endpointIdentityF64 = projectedSectionEndpointIdentityF64F32(projectedSpanF64F32, edgeParameterF64)
    if (endpointIdentityF64 != null) {
        if (suppliedIdentityF64 != null && suppliedIdentityF64 != endpointIdentityF64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        return endpointIdentityF64
    }
    if (edgeParameterF64 <= 0.0 || edgeParameterF64 >= 1.0 || suppliedIdentityF64 != null) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return projectedCutIdentityF64F32(
        witnessF64 = witnessF64,
        sourceSectionF64 = projectedSpanF64F32.sourceSectionF64,
        edgeParameterF64 = edgeParameterF64,
    )
}

private fun projectedCutIdentityF64F32(
    witnessF64: PathContactWitnessF64.PointF64,
    sourceSectionF64: PathFlattenedSectionF64,
    edgeParameterF64: Double,
): PathVertexIdentityF64 {
    val inputEdgeParameterF64 = projectedInputEdgeParameterAtSectionCutF64F32(sourceSectionF64, edgeParameterF64)
    return PathVertexIdentityF64(
        incidentEdgeIds = listOf(sourceSectionF64.inputEdgeIdI32),
        parameterByEdgeId = mapOf(sourceSectionF64.inputEdgeIdI32 to inputEdgeParameterF64),
        originalPointF32 = null,
        namespaceI32 = projectedCutIdentityNamespaceI32,
        identityScopeI64 = witnessF64.witnessIdI64,
    )
}

private fun projectedInputEdgeParameterAtSectionCutF64F32(
    sourceSectionF64: PathFlattenedSectionF64,
    edgeParameterF64: Double,
): Double {
    val startInputParameterF64 = sourceSectionF64.startIdentityF64.parameterByEdgeId[sourceSectionF64.inputEdgeIdI32]
        ?: throw IllegalStateException("path-f32-projection-collapse")
    val endInputParameterF64 = sourceSectionF64.endIdentityF64.parameterByEdgeId[sourceSectionF64.inputEdgeIdI32]
        ?: throw IllegalStateException("path-f32-projection-collapse")
    return startInputParameterF64 + (endInputParameterF64 - startInputParameterF64) * edgeParameterF64
}

private fun projectedSourceParameterAtSectionCutF64F32(
    sourceSectionF64: PathFlattenedSectionF64,
    edgeParameterF64: Double,
): Double = sourceSectionF64.startParameterF64 +
    (sourceSectionF64.endParameterF64 - sourceSectionF64.startParameterF64) * edgeParameterF64

private fun projectedSectionPointAtCutF64F32(
    startPointF64: Point2F64,
    endPointF64: Point2F64,
    edgeParameterF64: Double,
): Point2F64 = Point2F64(
    x = startPointF64.x + (endPointF64.x - startPointF64.x) * edgeParameterF64,
    y = startPointF64.y + (endPointF64.y - startPointF64.y) * edgeParameterF64,
)

/** Applies an already validated cut plan to immutable source-span copies. */
private data class PathProjectedCoincidenceRailSideF64F32(
    val proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    val firstSide: Boolean,
)

private data class PathProjectedCutRelayF64F32(
    val eventKeyF64F32: PathProjectedEventKeyF64F32,
    val witnessF64: PathContactWitnessF64.PointF64,
    val projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    val edgeParameterF64: Double,
    val sourceParameterF64: Double,
    val identityF64: PathVertexIdentityF64,
    val canonicalPointF64: Point2F64,
    val incidencePointF64: Point2F64,
)

private data class PathProjectedCutRelayKeyF64F32(
    val eventKeyF64F32: PathProjectedEventKeyF64F32,
    val sourceSpanIdI64: Long,
    val sourceSectionIndexI32: Int,
    val edgeParameterF64: Double,
)

/**
 * Propagates each admitted strict bound over every directly validated rail relation.  The queue
 * carries the original semantic event key, so a target occurrence is a partition member rather
 * than a second intersection; a different event reaching the same exact carrier parameter is a
 * conflict and is rejected when the final carrier map is built.
 */
private fun propagateProjectedCutPartitionsF64F32(
    stagedCutsF64F32: List<PathProjectedCutF64F32>,
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    normalizationF64: PathNormalizationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathProjectedCutPartitionPlanF64F32 {
    if (stagedCutsF64F32.isEmpty()) {
        preflightHybridLinearF64F32(1L, candidateWorkBudgetI32)
        return PathProjectedCutPartitionPlanF64F32(emptyMap(), emptyList())
    }
    val proposalCountI64 = proposalsF64F32.size.toLong()
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalCountI64, 12L),
            checkedPathWorkMultiplyI64(stagedCutsF64F32.size.toLong(), 9L),
        ),
        candidateWorkBudgetI32,
    )
    val witnessByIdI64 = mutableMapOf<Long, PathContactWitnessF64.PointF64>()
    val spanByCarrierF64F32 = mutableMapOf<PathProjectedCarrierKeyF64F32, PathProjectedSourceSpanF64F32>()
    val sidesByCarrierF64F32 = mutableMapOf<
        PathProjectedCarrierKeyF64F32,
        MutableList<PathProjectedCoincidenceRailSideF64F32>,
        >()
    fun addSideF64F32(
        proposalF64F32: PathProjectedCoincidenceProposalF64F32,
        firstSide: Boolean,
    ) {
        val projectedSpanF64F32 = if (firstSide) proposalF64F32.firstSpanF64 else proposalF64F32.secondSpanF64
        val keyF64F32 = PathProjectedCarrierKeyF64F32(
            sourceSpanIdI64 = projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
            sourceSectionIndexI32 = projectedSpanF64F32.sectionIndexI32,
        )
        val previousSpanF64F32 = spanByCarrierF64F32.put(keyF64F32, projectedSpanF64F32)
        if (previousSpanF64F32 != null && previousSpanF64F32 != projectedSpanF64F32) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        sidesByCarrierF64F32.getOrPut(keyF64F32) { mutableListOf() } +=
            PathProjectedCoincidenceRailSideF64F32(proposalF64F32, firstSide)
    }
    proposalsF64F32.forEach { proposalF64F32 ->
        val previousWitnessF64 = witnessByIdI64.put(
            proposalF64F32.pointWitnessF64.witnessIdI64,
            proposalF64F32.pointWitnessF64,
        )
        if (previousWitnessF64 != null && previousWitnessF64 != proposalF64F32.pointWitnessF64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        addSideF64F32(proposalF64F32, firstSide = true)
        addSideF64F32(proposalF64F32, firstSide = false)
    }

    val allCutsF64F32 = ArrayList<PathProjectedCutF64F32>(
        checkedPathCapacityI32(stagedCutsF64F32.size.toLong(), "path-candidate-limit"),
    )
    val relaysF64F32 = ArrayList<PathProjectedCutRelayF64F32>(
        checkedPathCapacityI32(stagedCutsF64F32.size.toLong(), "path-candidate-limit"),
    )
    val relayByKeyF64F32 = linkedMapOf<PathProjectedCutRelayKeyF64F32, PathProjectedCutRelayF64F32>()
    fun enqueueRelayF64F32(relayF64F32: PathProjectedCutRelayF64F32): Boolean {
        // This debit is before the seen-set mutation and potential queue growth.  A queue can
        // grow only through candidate-work already reserved by this operation.
        preflightHybridLinearF64F32(1L, candidateWorkBudgetI32)
        val keyF64F32 = PathProjectedCutRelayKeyF64F32(
            eventKeyF64F32 = relayF64F32.eventKeyF64F32,
            sourceSpanIdI64 = relayF64F32.projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
            sourceSectionIndexI32 = relayF64F32.projectedSpanF64F32.sectionIndexI32,
            edgeParameterF64 = canonicalProjectedRelayParameterF64F32(relayF64F32.edgeParameterF64),
        )
        val previousRelayF64F32 = relayByKeyF64F32[keyF64F32]
        if (previousRelayF64F32 == null) {
            relayByKeyF64F32[keyF64F32] = relayF64F32
            relaysF64F32 += relayF64F32
            return true
        }
        if (
            previousRelayF64F32.identityF64 != relayF64F32.identityF64 ||
                previousRelayF64F32.sourceParameterF64 != relayF64F32.sourceParameterF64 ||
                !sameHybridPointF64(previousRelayF64F32.canonicalPointF64, relayF64F32.canonicalPointF64) ||
                !sameHybridPointF64(previousRelayF64F32.incidencePointF64, relayF64F32.incidencePointF64)
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        return false
    }
    stagedCutsF64F32.forEach { cutF64F32 ->
        val eventKeyF64F32 = cutF64F32.eventKeyF64F32
            ?: throw IllegalStateException("path-f32-projection-collapse")
        val witnessF64 = witnessByIdI64[cutF64F32.witnessIdI64]
            ?: throw IllegalStateException("path-f32-projection-collapse")
        if (eventKeyF64F32.witnessIdI64 != witnessF64.witnessIdI64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val projectedSpanF64F32 = spanByCarrierF64F32[
            PathProjectedCarrierKeyF64F32(cutF64F32.sourceSpanIdI64, cutF64F32.sourceSectionIndexI32)
        ] ?: throw IllegalStateException("path-f32-projection-collapse")
        if (
            projectedSourceParameterAtSectionCutF64F32(
                projectedSpanF64F32.sourceSectionF64,
                cutF64F32.edgeParameterF64,
            ) != cutF64F32.sourceParameterF64
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val initialRelayF64F32 = PathProjectedCutRelayF64F32(
            eventKeyF64F32 = eventKeyF64F32,
            witnessF64 = witnessF64,
            projectedSpanF64F32 = projectedSpanF64F32,
            edgeParameterF64 = cutF64F32.edgeParameterF64,
            sourceParameterF64 = cutF64F32.sourceParameterF64,
            identityF64 = cutF64F32.identityF64,
            canonicalPointF64 = cutF64F32.canonicalPointF64,
            incidencePointF64 = cutF64F32.incidencePointF64,
        )
        if (enqueueRelayF64F32(initialRelayF64F32)) {
            allCutsF64F32 += cutF64F32
        }
    }

    var relayIndexI32 = 0
    while (relayIndexI32 < relaysF64F32.size) {
        val relayF64F32 = relaysF64F32[relayIndexI32]
        relayIndexI32 += 1
        val relayCarrierKeyF64F32 = PathProjectedCarrierKeyF64F32(
            relayF64F32.projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
            relayF64F32.projectedSpanF64F32.sectionIndexI32,
        )
        val sidesF64F32 = sidesByCarrierF64F32[relayCarrierKeyF64F32].orEmpty()
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(sidesF64F32.size.toLong(), 14L),
                2L,
            ),
            candidateWorkBudgetI32,
        )
        sidesF64F32.forEach { sideF64F32 ->
            val sourceClaimF64 = if (sideF64F32.firstSide) {
                sideF64F32.proposalF64F32.firstClaimF64
            } else {
                sideF64F32.proposalF64F32.secondClaimF64
            }
            if (
                relayF64F32.sourceParameterF64 < sourceClaimF64.startParameterF64 ||
                    relayF64F32.sourceParameterF64 > sourceClaimF64.endParameterF64
            ) {
                return@forEach
            }
            val targetClaimF64 = if (sideF64F32.firstSide) {
                sideF64F32.proposalF64F32.secondClaimF64
            } else {
                sideF64F32.proposalF64F32.firstClaimF64
            }
            val targetSpanF64F32 = if (sideF64F32.firstSide) {
                sideF64F32.proposalF64F32.secondSpanF64
            } else {
                sideF64F32.proposalF64F32.firstSpanF64
            }
            val targetRelayF64F32 = propagateProjectedCutRelayF64F32(
                relayF64F32 = relayF64F32,
                sourceClaimF64 = sourceClaimF64,
                targetClaimF64 = targetClaimF64,
                targetSpanF64F32 = targetSpanF64F32,
                proposalF64F32 = sideF64F32.proposalF64F32,
                normalizationF64 = normalizationF64,
            )
            val targetWasNewF64F32 = enqueueRelayF64F32(targetRelayF64F32)
            if (
                targetWasNewF64F32 &&
                    targetRelayF64F32.edgeParameterF64 > 0.0 && targetRelayF64F32.edgeParameterF64 < 1.0
            ) {
                preflightHybridLinearF64F32(1L, candidateWorkBudgetI32)
                allCutsF64F32 += PathProjectedCutF64F32(
                    witnessIdI64 = targetRelayF64F32.witnessF64.witnessIdI64,
                    eventKeyF64F32 = targetRelayF64F32.eventKeyF64F32,
                    sourceSpanIdI64 = targetRelayF64F32.projectedSpanF64F32.sourceSpanF64.sourceSpanIdI64,
                    sourceSectionIndexI32 = targetRelayF64F32.projectedSpanF64F32.sectionIndexI32,
                    inputEdgeIdI32 = targetRelayF64F32.projectedSpanF64F32.sourceSectionF64.inputEdgeIdI32,
                    edgeParameterF64 = targetRelayF64F32.edgeParameterF64,
                    sourceParameterF64 = targetRelayF64F32.sourceParameterF64,
                    identityF64 = targetRelayF64F32.identityF64,
                    canonicalPointF64 = targetRelayF64F32.canonicalPointF64,
                    incidencePointF64 = targetRelayF64F32.incidencePointF64,
                )
            }
        }
    }

    val cutsByCarrierF64F32 = materializeProjectedCutPartitionsF64F32(
        stagedCutsF64F32 = allCutsF64F32,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    return PathProjectedCutPartitionPlanF64F32(
        cutsByCarrierF64F32 = cutsByCarrierF64F32,
        aliasGroupsF64F32 = projectedCutAliasGroupsF64F32(
            cutsF64F32 = allCutsF64F32,
            witnessByIdI64 = witnessByIdI64,
            normalizationF64 = normalizationF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        ),
    )
}

private fun canonicalProjectedRelayParameterF64F32(parameterF64: Double): Double = if (parameterF64 == 0.0) 0.0 else parameterF64

private fun propagateProjectedCutRelayF64F32(
    relayF64F32: PathProjectedCutRelayF64F32,
    sourceClaimF64: PathProjectedSpanClaimF64,
    targetClaimF64: PathProjectedSpanClaimF64,
    targetSpanF64F32: PathProjectedSourceSpanF64F32,
    proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    normalizationF64: PathNormalizationF64,
): PathProjectedCutRelayF64F32 {
    val sourceSpanF64F32 = relayF64F32.projectedSpanF64F32
    if (
        sourceClaimF64.sourceSpanIdI64 != sourceSpanF64F32.sourceSpanF64.sourceSpanIdI64 ||
            sourceClaimF64.sourceSectionIndexI32 != sourceSpanF64F32.sectionIndexI32 ||
            sourceClaimF64.inputEdgeIdI32 != sourceSpanF64F32.sourceSectionF64.inputEdgeIdI32 ||
            targetClaimF64.sourceSpanIdI64 != targetSpanF64F32.sourceSpanF64.sourceSpanIdI64 ||
            targetClaimF64.sourceSectionIndexI32 != targetSpanF64F32.sectionIndexI32 ||
            targetClaimF64.inputEdgeIdI32 != targetSpanF64F32.sourceSectionF64.inputEdgeIdI32
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val sourceEdgeDeltaF64 = sourceClaimF64.endEdgeParameterF64 - sourceClaimF64.startEdgeParameterF64
    if (sourceEdgeDeltaF64 == 0.0 || !sourceEdgeDeltaF64.isFinite()) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val sourceFractionF64 = (relayF64F32.edgeParameterF64 - sourceClaimF64.startEdgeParameterF64) / sourceEdgeDeltaF64
    if (!sourceFractionF64.isFinite() || sourceFractionF64 < 0.0 || sourceFractionF64 > 1.0) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    if (
        projectedSourceParameterAtSectionCutF64F32(sourceSpanF64F32.sourceSectionF64, relayF64F32.edgeParameterF64) !=
            relayF64F32.sourceParameterF64
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val sourceStartPointF32 = projectedSectionPointAtCutF64F32(
        sourceSpanF64F32.projectedEdgeF64.startPointF64,
        sourceSpanF64F32.projectedEdgeF64.endPointF64,
        sourceClaimF64.startEdgeParameterF64,
    ).toPoint2F32()
    val sourceEndPointF32 = projectedSectionPointAtCutF64F32(
        sourceSpanF64F32.projectedEdgeF64.startPointF64,
        sourceSpanF64F32.projectedEdgeF64.endPointF64,
        sourceClaimF64.endEdgeParameterF64,
    ).toPoint2F32()
    val sourceStartsAtProposalStart = sameHybridPointF32(sourceStartPointF32, proposalF64F32.startPointF32) &&
        sameHybridPointF32(sourceEndPointF32, proposalF64F32.endPointF32)
    val sourceStartsAtProposalEnd = sameHybridPointF32(sourceStartPointF32, proposalF64F32.endPointF32) &&
        sameHybridPointF32(sourceEndPointF32, proposalF64F32.startPointF32)
    if (sourceStartsAtProposalStart == sourceStartsAtProposalEnd) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val projectedFractionF64 = if (sourceStartsAtProposalStart) sourceFractionF64 else 1.0 - sourceFractionF64
    val targetStartPointF32 = projectedSectionPointAtCutF64F32(
        targetSpanF64F32.projectedEdgeF64.startPointF64,
        targetSpanF64F32.projectedEdgeF64.endPointF64,
        targetClaimF64.startEdgeParameterF64,
    ).toPoint2F32()
    val targetEndPointF32 = projectedSectionPointAtCutF64F32(
        targetSpanF64F32.projectedEdgeF64.startPointF64,
        targetSpanF64F32.projectedEdgeF64.endPointF64,
        targetClaimF64.endEdgeParameterF64,
    ).toPoint2F32()
    val targetStartsAtProposalStart = sameHybridPointF32(targetStartPointF32, proposalF64F32.startPointF32) &&
        sameHybridPointF32(targetEndPointF32, proposalF64F32.endPointF32)
    val targetStartsAtProposalEnd = sameHybridPointF32(targetStartPointF32, proposalF64F32.endPointF32) &&
        sameHybridPointF32(targetEndPointF32, proposalF64F32.startPointF32)
    if (targetStartsAtProposalStart == targetStartsAtProposalEnd) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val targetFractionF64 = if (targetStartsAtProposalStart) projectedFractionF64 else 1.0 - projectedFractionF64
    val targetEdgeParameterF64 = targetClaimF64.startEdgeParameterF64 +
        (targetClaimF64.endEdgeParameterF64 - targetClaimF64.startEdgeParameterF64) * targetFractionF64
    if (!targetEdgeParameterF64.isFinite() || targetEdgeParameterF64 < 0.0 || targetEdgeParameterF64 > 1.0) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val targetSourceParameterF64 = projectedSourceParameterAtSectionCutF64F32(
        targetSpanF64F32.sourceSectionF64,
        targetEdgeParameterF64,
    )
    if (
        targetSourceParameterF64 < targetClaimF64.startParameterF64 ||
            targetSourceParameterF64 > targetClaimF64.endParameterF64
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val targetCanonicalPointF64 = projectedSectionPointAtCutF64F32(
        targetSpanF64F32.sourceSectionF64.startPointF64,
        targetSpanF64F32.sourceSectionF64.endPointF64,
        targetEdgeParameterF64,
    )
    val targetIncidencePointF64 = projectedSectionPointAtCutF64F32(
        targetSpanF64F32.sourceSectionF64.startIncidencePointF64,
        targetSpanF64F32.sourceSectionF64.endIncidencePointF64,
        targetEdgeParameterF64,
    )
    if (
        !sameHybridPointF32(
            normalizationF64.denormalize(targetIncidencePointF64),
            normalizationF64.denormalize(relayF64F32.incidencePointF64),
        )
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val targetIdentityF64 = projectedSectionEndpointIdentityF64F32(targetSpanF64F32, targetEdgeParameterF64)
        ?: projectedCutIdentityF64F32(
            witnessF64 = relayF64F32.witnessF64,
            sourceSectionF64 = targetSpanF64F32.sourceSectionF64,
            edgeParameterF64 = targetEdgeParameterF64,
        )
    return PathProjectedCutRelayF64F32(
        eventKeyF64F32 = relayF64F32.eventKeyF64F32,
        witnessF64 = relayF64F32.witnessF64,
        projectedSpanF64F32 = targetSpanF64F32,
        edgeParameterF64 = targetEdgeParameterF64,
        sourceParameterF64 = targetSourceParameterF64,
        identityF64 = targetIdentityF64,
        canonicalPointF64 = targetCanonicalPointF64,
        incidencePointF64 = targetIncidencePointF64,
    )
}

private fun projectedCutAliasGroupsF64F32(
    cutsF64F32: List<PathProjectedCutF64F32>,
    witnessByIdI64: Map<Long, PathContactWitnessF64.PointF64>,
    normalizationF64: PathNormalizationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathProjectedCutAliasGroupF64F32> {
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(cutsF64F32.size.toLong(), 6L),
        candidateWorkBudgetI32,
    )
    val identitiesByEventKeyF64F32 = linkedMapOf<PathProjectedEventKeyF64F32, MutableSet<PathVertexIdentityF64>>()
    val representativeByEventKeyF64F32 = linkedMapOf<PathProjectedEventKeyF64F32, Point2F32>()
    cutsF64F32.forEach { cutF64F32 ->
        val eventKeyF64F32 = cutF64F32.eventKeyF64F32
            ?: throw IllegalStateException("path-f32-projection-collapse")
        val witnessF64 = witnessByIdI64[eventKeyF64F32.witnessIdI64]
            ?: throw IllegalStateException("path-f32-projection-collapse")
        if (cutF64F32.witnessIdI64 != witnessF64.witnessIdI64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val representativePointF32 = normalizationF64.denormalize(cutF64F32.incidencePointF64)
        val previousPointF32 = representativeByEventKeyF64F32.put(eventKeyF64F32, representativePointF32)
        if (previousPointF32 != null && !sameHybridPointF32(previousPointF32, representativePointF32)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        identitiesByEventKeyF64F32.getOrPut(eventKeyF64F32) { linkedSetOf() } += cutF64F32.identityF64
    }
    preflightHybridLinearF64F32(identitiesByEventKeyF64F32.size.toLong(), candidateWorkBudgetI32)
    val groupsF64F32 = identitiesByEventKeyF64F32.mapNotNull { (eventKeyF64F32, identitiesF64) ->
        if (identitiesF64.size < 2) return@mapNotNull null
        val orderedIdentitiesF64 = sortedHybridF64F32(
            identitiesF64.toList(),
            candidateWorkBudgetI32,
            ::comparePathVertexIdentitiesStructuralF64,
        )
        PathProjectedCutAliasGroupF64F32(
            witnessF64 = witnessByIdI64[eventKeyF64F32.witnessIdI64]
                ?: throw IllegalStateException("path-f32-projection-collapse"),
            representativePointF32 = representativeByEventKeyF64F32[eventKeyF64F32]
                ?: throw IllegalStateException("path-arrangement-inconsistent"),
            vertexIdentitiesF64 = orderedIdentitiesF64,
        )
    }
    return sortedHybridF64F32(groupsF64F32, candidateWorkBudgetI32) { firstF64F32, secondF64F32 ->
        compareHybridPointWitnessesF64(firstF64F32.witnessF64, secondF64F32.witnessF64)
            .takeIf { it != 0 } ?: firstF64F32.vertexIdentitiesF64.size.compareTo(secondF64F32.vertexIdentitiesF64.size)
    }
}

/**
 * Converts the already admitted immutable cut occurrences into carrier-local sorted partitions.
 * This is intentionally after transaction validation; an occurrence without an event key would
 * mean a hidden post-limit event and is rejected rather than allocated.
 */
private fun materializeProjectedCutPartitionsF64F32(
    stagedCutsF64F32: List<PathProjectedCutF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Map<PathProjectedCarrierKeyF64F32, List<PathProjectedCutF64F32>> {
    if (stagedCutsF64F32.isEmpty()) {
        preflightHybridLinearF64F32(1L, candidateWorkBudgetI32)
        return emptyMap()
    }
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(stagedCutsF64F32.size.toLong(), 8L),
        candidateWorkBudgetI32,
    )
    val mutableCutsByCarrierF64F32 = linkedMapOf<PathProjectedCarrierKeyF64F32, MutableList<PathProjectedCutF64F32>>()
    val cutByCarrierAndEdgeParameterF64F32 = linkedMapOf<
        PathProjectedCarrierKeyF64F32,
        MutableMap<Double, PathProjectedCutF64F32>,
        >()
    stagedCutsF64F32.forEach { cutF64F32 ->
        if (cutF64F32.eventKeyF64F32 == null) throw IllegalStateException("path-f32-projection-collapse")
        val keyF64F32 = PathProjectedCarrierKeyF64F32(
            sourceSpanIdI64 = cutF64F32.sourceSpanIdI64,
            sourceSectionIndexI32 = cutF64F32.sourceSectionIndexI32,
        )
        val cutsForCarrierF64F32 = mutableCutsByCarrierF64F32.getOrPut(keyF64F32) { mutableListOf() }
        val duplicateF64F32 = cutByCarrierAndEdgeParameterF64F32
            .getOrPut(keyF64F32) { mutableMapOf() }
            .put(cutF64F32.edgeParameterF64, cutF64F32)
        when {
            duplicateF64F32 == null -> cutsForCarrierF64F32 += cutF64F32
            duplicateF64F32.identityF64 != cutF64F32.identityF64 ||
                duplicateF64F32.eventKeyF64F32 != cutF64F32.eventKeyF64F32 ->
                throw IllegalStateException("path-f32-projection-collapse")
        }
    }
    preflightHybridLinearF64F32(mutableCutsByCarrierF64F32.size.toLong(), candidateWorkBudgetI32)
    val cutsByCarrierF64F32 = linkedMapOf<PathProjectedCarrierKeyF64F32, List<PathProjectedCutF64F32>>()
    mutableCutsByCarrierF64F32.forEach { (keyF64F32, cutsForCarrierF64F32) ->
        cutsByCarrierF64F32[keyF64F32] = sortedHybridF64F32(
            cutsForCarrierF64F32,
            candidateWorkBudgetI32,
        ) { firstF64F32, secondF64F32 ->
            firstF64F32.edgeParameterF64.compareTo(secondF64F32.edgeParameterF64)
        }
    }
    return cutsByCarrierF64F32
}

private fun materializeProjectedSourceSpansF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    cutsByCarrierF64F32: Map<PathProjectedCarrierKeyF64F32, List<PathProjectedCutF64F32>>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathSourceSpanF64> {
    if (cutsByCarrierF64F32.isEmpty()) return sourceSpansF64
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var materializedSectionCountI64 = 0L
    val materializedSectionCountBySpanIdI64 = mutableMapOf<Long, Long>()
    sourceSpansF64.forEach { sourceSpanF64 ->
        var spanSectionCountI64 = sourceSpanF64.flattenedSectionsF64.size.toLong()
        sourceSpanF64.flattenedSectionsF64.indices.forEach { sectionIndexI32 ->
            val cutCountI64 = cutsByCarrierF64F32[
                PathProjectedCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)
            ].orEmpty().size.toLong()
            spanSectionCountI64 = checkedPathWorkAddI64(spanSectionCountI64, cutCountI64)
        }
        materializedSectionCountI64 = checkedPathWorkAddI64(materializedSectionCountI64, spanSectionCountI64)
        if (materializedSectionCountBySpanIdI64.put(sourceSpanF64.sourceSpanIdI64, spanSectionCountI64) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(materializedSectionCountI64, 5L),
            checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 2L),
        ),
        candidateWorkBudgetI32,
    )
    val materializedSpansF64 = ArrayList<PathSourceSpanF64>(
        checkedPathCapacityI32(sourceSpansF64.size.toLong(), "path-candidate-limit"),
    )
    sourceSpansF64.forEach { sourceSpanF64 ->
        val sectionsF64 = ArrayList<PathFlattenedSectionF64>(
            checkedPathCapacityI32(
                materializedSectionCountBySpanIdI64.getValue(sourceSpanF64.sourceSpanIdI64),
                "path-candidate-limit",
            ),
        )
        sourceSpanF64.flattenedSectionsF64.forEachIndexed { sectionIndexI32, sourceSectionF64 ->
            val cutsF64F32 = cutsByCarrierF64F32[
                PathProjectedCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)
            ].orEmpty()
            if (cutsF64F32.isEmpty()) {
                sectionsF64 += sourceSectionF64
                return@forEachIndexed
            }
            var startEdgeParameterF64 = 0.0
            var startSourceParameterF64 = sourceSectionF64.startParameterF64
            var startIdentityF64 = sourceSectionF64.startIdentityF64
            var startPointF64 = sourceSectionF64.startPointF64
            var startIncidencePointF64 = sourceSectionF64.startIncidencePointF64
            cutsF64F32.forEach { cutF64F32 ->
                if (
                    cutF64F32.edgeParameterF64 <= startEdgeParameterF64 ||
                        cutF64F32.edgeParameterF64 >= 1.0 ||
                        cutF64F32.sourceParameterF64 != projectedSourceParameterAtSectionCutF64F32(
                            sourceSectionF64,
                            cutF64F32.edgeParameterF64,
                        )
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (sameHybridPointF64(startPointF64, cutF64F32.canonicalPointF64)) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                sectionsF64 += PathFlattenedSectionF64(
                    inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
                    startPointF64 = startPointF64,
                    endPointF64 = cutF64F32.canonicalPointF64,
                    startIncidencePointF64 = startIncidencePointF64,
                    endIncidencePointF64 = cutF64F32.incidencePointF64,
                    startParameterF64 = startSourceParameterF64,
                    endParameterF64 = cutF64F32.sourceParameterF64,
                    startIdentityF64 = startIdentityF64,
                    endIdentityF64 = cutF64F32.identityF64,
                )
                startEdgeParameterF64 = cutF64F32.edgeParameterF64
                startSourceParameterF64 = cutF64F32.sourceParameterF64
                startIdentityF64 = cutF64F32.identityF64
                startPointF64 = cutF64F32.canonicalPointF64
                startIncidencePointF64 = cutF64F32.incidencePointF64
            }
            if (sameHybridPointF64(startPointF64, sourceSectionF64.endPointF64)) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            sectionsF64 += PathFlattenedSectionF64(
                inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
                startPointF64 = startPointF64,
                endPointF64 = sourceSectionF64.endPointF64,
                startIncidencePointF64 = startIncidencePointF64,
                endIncidencePointF64 = sourceSectionF64.endIncidencePointF64,
                startParameterF64 = startSourceParameterF64,
                endParameterF64 = sourceSectionF64.endParameterF64,
                startIdentityF64 = startIdentityF64,
                endIdentityF64 = sourceSectionF64.endIdentityF64,
            )
        }
        materializedSpansF64 += sourceSpanF64.copy(flattenedSectionsF64 = sectionsF64)
    }
    return materializedSpansF64
}

private data class PathMaterializedHybridCarrierTopologyF64F32(
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
)

/**
 * Rebuilds only the local immutable carrier/vertex staging after a cut plan has been accepted.
 * The pre-plan vertex list remains private to the broad-phase probe and is never published.
 */
private fun buildMaterializedHybridCarrierTopologyF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    initialVerticesF64F32: List<PathHybridVertexF64F32>,
    cutsByCarrierF64F32: Map<PathProjectedCarrierKeyF64F32, List<PathProjectedCutF64F32>>,
    normalizationF64: PathNormalizationF64,
    pointWitnessesF64: List<PathContactWitnessF64.PointF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathMaterializedHybridCarrierTopologyF64F32 {
    val carrierSectionCountI64 = countHybridCarrierSectionsF64F32(sourceSpansF64, candidateWorkBudgetI32)
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionCountI64, 2L),
        candidateWorkBudgetI32,
    )
    val carrierSectionsF64F32 = ArrayList<PathHybridCarrierSectionF64F32>(
        checkedPathCapacityI32(carrierSectionCountI64, "path-half-edge-limit"),
    )
    sourceSpansF64.forEach { sourceSpanF64 ->
        sourceSpanF64.flattenedSectionsF64.forEachIndexed { sectionIndexI32, sourceSectionF64 ->
            carrierSectionsF64F32 += PathHybridCarrierSectionF64F32(
                sourceSpanF64 = sourceSpanF64,
                sourceSectionF64 = sourceSectionF64,
                sectionIndexI32 = sectionIndexI32,
            )
        }
    }

    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(initialVerticesF64F32.size.toLong(), 2L),
            checkedPathWorkMultiplyI64(cutsByCarrierF64F32.size.toLong(), 3L),
        ),
        candidateWorkBudgetI32,
    )
    val existingByIdentityF64 = mutableMapOf<PathVertexIdentityF64, PathHybridVertexF64F32>()
    initialVerticesF64F32.forEach { vertexF64F32 ->
        if (existingByIdentityF64.put(vertexF64F32.vertexIdentityF64, vertexF64F32) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    val witnessByIdI64 = mutableMapOf<Long, PathContactWitnessF64.PointF64>()
    pointWitnessesF64.forEach { witnessF64 ->
        val previousF64 = witnessByIdI64.put(witnessF64.witnessIdI64, witnessF64)
        if (previousF64 != null && previousF64 != witnessF64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
    var cutOccurrenceCountI64 = 0L
    cutsByCarrierF64F32.values.forEach { cutsF64F32 ->
        cutOccurrenceCountI64 = checkedPathWorkAddI64(cutOccurrenceCountI64, cutsF64F32.size.toLong())
    }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(cutOccurrenceCountI64, 5L),
            pointWitnessesF64.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    val cutsByIdentityF64 = linkedMapOf<PathVertexIdentityF64, MutableList<PathProjectedCutF64F32>>()
    cutsByCarrierF64F32.values.forEach { cutsF64F32 ->
        cutsF64F32.forEach { cutF64F32 ->
            cutsByIdentityF64.getOrPut(cutF64F32.identityF64) { mutableListOf() } += cutF64F32
        }
    }
    preflightHybridLinearF64F32(cutsByIdentityF64.size.toLong(), candidateWorkBudgetI32)
    // This is only a transient materializer capacity.  `maxVertices` is enforced after exact
    // alias canonicalization in `PathArrangementF64F32`, where Task 2 defined the public
    // canonical-vertex count; rejecting this raw pre-alias multiset would be too early.
    val transientVertexCountI64 = checkedPathWorkAddI64(
        initialVerticesF64F32.size.toLong(),
        cutsByIdentityF64.size.toLong(),
    )
    val materializedVerticesF64F32 = ArrayList<PathHybridVertexF64F32>(
        checkedPathCapacityI32(
            transientVertexCountI64,
            "path-candidate-limit",
        ),
    )
    materializedVerticesF64F32 += initialVerticesF64F32
    cutsByIdentityF64.forEach { (identityF64, cutsF64F32) ->
        if (identityF64 in existingByIdentityF64) throw IllegalStateException("path-f32-projection-collapse")
        val canonicalCutF64F32 = cutsF64F32.firstOrNull()
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val witnessF64 = witnessByIdI64[canonicalCutF64F32.witnessIdI64]
            ?: throw IllegalStateException("path-f32-projection-collapse")
        val representativePointF32 = normalizationF64.denormalize(canonicalCutF64F32.incidencePointF64)
        preflightHybridLinearF64F32(cutsF64F32.size.toLong(), candidateWorkBudgetI32)
        val incidentSourceSpanIdsI64 = ArrayList<Long>(cutsF64F32.size)
        cutsF64F32.forEach { cutF64F32 ->
            if (
                cutF64F32.witnessIdI64 != canonicalCutF64F32.witnessIdI64 ||
                    !sameHybridPointF64(cutF64F32.canonicalPointF64, canonicalCutF64F32.canonicalPointF64) ||
                    !sameHybridPointF64(cutF64F32.incidencePointF64, canonicalCutF64F32.incidencePointF64) ||
                    !sameHybridPointF32(normalizationF64.denormalize(cutF64F32.incidencePointF64), representativePointF32)
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            incidentSourceSpanIdsI64 += cutF64F32.sourceSpanIdI64
        }
        materializedVerticesF64F32 += PathHybridVertexF64F32(
            sourcePointF64 = canonicalCutF64F32.canonicalPointF64,
            representativePointF32 = representativePointF32,
            originalPointF32 = null,
            vertexIdentityF64 = identityF64,
            incidentSourceSpanIdsI64 = sortedHybridLongsI64(incidentSourceSpanIdsI64, candidateWorkBudgetI32),
            contactWitnessF64 = witnessF64,
        )
    }
    val orderedVerticesF64F32 = sortedHybridF64F32(
        materializedVerticesF64F32,
        candidateWorkBudgetI32,
        ::compareHybridVerticesF64F32,
    )
    val vertexIndexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(orderedVerticesF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    orderedVerticesF64F32.forEachIndexed { indexI32, vertexF64F32 ->
        if (vertexIndexByIdentityF64.put(vertexF64F32.vertexIdentityF64, indexI32) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    val collapsedIncidencesF64F32 = collectCollapsedHybridIncidencesF64F32(
        carrierSectionsF64F32 = carrierSectionsF64F32,
        verticesF64F32 = orderedVerticesF64F32,
        vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
    return PathMaterializedHybridCarrierTopologyF64F32(
        verticesF64F32 = orderedVerticesF64F32,
        carrierSectionsF64F32 = carrierSectionsF64F32,
        collapsedIncidencesF64F32 = collapsedIncidencesF64F32,
    )
}

private data class PathCollapsedContourKeyF64F32(
    val operand: PathOperand,
    val contourIndexI32: Int,
)

private data class PathCollapsedCarrierStateF64F32(
    val carrierSectionF64F32: PathHybridCarrierSectionF64F32,
    val startVertexF64F32: PathHybridVertexF64F32,
    val collapsed: Boolean,
)

private fun collectCollapsedHybridIncidencesF64F32(
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathCollapsedIncidenceF64F32> {
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 18L),
        candidateWorkBudgetI32,
    )
    val carrierStatesF64F32 = ArrayList<PathCollapsedCarrierStateF64F32>(
        checkedPathCapacityI32(carrierSectionsF64F32.size.toLong(), "path-candidate-limit"),
    )
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        val startVertexF64F32 = verticesF64F32[
            vertexIndexByIdentityF64[sourceSectionF64.startIdentityF64]
                ?: throw IllegalStateException("path-arrangement-inconsistent")
        ]
        val endVertexF64F32 = verticesF64F32[
            vertexIndexByIdentityF64[sourceSectionF64.endIdentityF64]
                ?: throw IllegalStateException("path-arrangement-inconsistent")
        ]
        carrierStatesF64F32 += PathCollapsedCarrierStateF64F32(
            carrierSectionF64F32 = carrierSectionF64F32,
            startVertexF64F32 = startVertexF64F32,
            collapsed = sameHybridPointF32(
                startVertexF64F32.representativePointF32,
                endVertexF64F32.representativePointF32,
            ),
        )
    }
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierStatesF64F32.size.toLong(), 5L),
        candidateWorkBudgetI32,
    )
    val statesByContourF64F32 = mutableMapOf<
        PathCollapsedContourKeyF64F32,
        MutableList<PathCollapsedCarrierStateF64F32>,
        >()
    carrierStatesF64F32.forEach { stateF64F32 ->
        val sourceSpanF64 = stateF64F32.carrierSectionF64F32.sourceSpanF64
        statesByContourF64F32.getOrPut(
            PathCollapsedContourKeyF64F32(sourceSpanF64.operand, sourceSpanF64.contourIndexI32),
        ) { mutableListOf() } += stateF64F32
    }
    val collapsedIncidencesF64F32 = ArrayList<PathCollapsedIncidenceF64F32>(
        checkedPathCapacityI32(carrierStatesF64F32.size.toLong(), "path-candidate-limit"),
    )
    statesByContourF64F32.values.forEach { statesF64F32 ->
        val orderedStatesF64F32 = sortedHybridF64F32(
            statesF64F32,
            candidateWorkBudgetI32,
            ::compareCollapsedCarrierContourOrderF64F32,
        )
        preflightHybridLinearF64F32(
            // This covers the order check, collapsed-count scan, and ordinary emission pass.  If
            // the scan proves that a collapsed run needs neighbours, the table-specific debit is
            // made below before allocating or filling either table.  A contour without collapsed
            // sections must not be charged for tables it never constructs.
            checkedPathWorkMultiplyI64(orderedStatesF64F32.size.toLong(), 5L),
            candidateWorkBudgetI32,
        )
        orderedStatesF64F32.zipWithNext().forEach { (firstF64F32, secondF64F32) ->
            if (compareCollapsedCarrierContourOrderF64F32(firstF64F32, secondF64F32) == 0) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
        var collapsedCountI32 = 0
        var firstNonCollapsedIndexI32 = -1
        orderedStatesF64F32.forEachIndexed { stateIndexI32, stateF64F32 ->
            if (stateF64F32.collapsed) {
                collapsedCountI32 += 1
            } else if (firstNonCollapsedIndexI32 < 0) {
                firstNonCollapsedIndexI32 = stateIndexI32
            }
        }
        val adjacencyByStateIndexF64F32 = if (
            collapsedCountI32 != 0 && collapsedCountI32 != orderedStatesF64F32.size
        ) {
            // The two circular neighbour tables replace the former per-collapsed-span
            // backwards/forwards searches.  This reserve happens only after the count proves
            // that the tables are required, and before their allocation or either O(N) pass.
            preflightHybridLinearF64F32(
                checkedPathWorkMultiplyI64(orderedStatesF64F32.size.toLong(), 9L),
                candidateWorkBudgetI32,
            )
            val adjacencyByStateIndexF64F32 = arrayOfNulls<PathCollapsedAdjacencyF64F32>(
                orderedStatesF64F32.size,
            )
            val anchorIndexI32 = firstNonCollapsedIndexI32
            if (anchorIndexI32 < 0) throw IllegalStateException("path-arrangement-inconsistent")
            val previousNonCollapsedIndexI32 = IntArray(orderedStatesF64F32.size)
            val nextNonCollapsedIndexI32 = IntArray(orderedStatesF64F32.size)
            var previousIndexI32 = anchorIndexI32
            for (offsetI32 in 1..orderedStatesF64F32.size) {
                val stateIndexI32 = (anchorIndexI32 + offsetI32) % orderedStatesF64F32.size
                previousNonCollapsedIndexI32[stateIndexI32] = previousIndexI32
                if (!orderedStatesF64F32[stateIndexI32].collapsed) previousIndexI32 = stateIndexI32
            }
            var nextIndexI32 = anchorIndexI32
            for (offsetI32 in 1..orderedStatesF64F32.size) {
                val stateIndexI32 = (anchorIndexI32 - offsetI32 + orderedStatesF64F32.size) % orderedStatesF64F32.size
                nextNonCollapsedIndexI32[stateIndexI32] = nextIndexI32
                if (!orderedStatesF64F32[stateIndexI32].collapsed) nextIndexI32 = stateIndexI32
            }
            orderedStatesF64F32.forEachIndexed { stateIndexI32, stateF64F32 ->
                if (!stateF64F32.collapsed) return@forEachIndexed
                adjacencyByStateIndexF64F32[stateIndexI32] = collapsedContourRaysF64F32(
                    incomingStateF64F32 = orderedStatesF64F32[previousNonCollapsedIndexI32[stateIndexI32]],
                    outgoingStateF64F32 = orderedStatesF64F32[nextNonCollapsedIndexI32[stateIndexI32]],
                )
            }
            adjacencyByStateIndexF64F32
        } else {
            null
        }
        orderedStatesF64F32.forEachIndexed { stateIndexI32, stateF64F32 ->
            if (!stateF64F32.collapsed) return@forEachIndexed
            val adjacencyF64F32 = when {
                collapsedCountI32 == orderedStatesF64F32.size -> PathCollapsedAdjacencyF64F32.EntireContour
                else -> adjacencyByStateIndexF64F32?.get(stateIndexI32)
                    ?: throw IllegalStateException("path-arrangement-inconsistent")
            }
            val carrierSectionF64F32 = stateF64F32.carrierSectionF64F32
            collapsedIncidencesF64F32 += PathCollapsedIncidenceF64F32(
                sourceSpanF64 = carrierSectionF64F32.sourceSpanF64,
                sourceSectionF64 = carrierSectionF64F32.sourceSectionF64,
                sectionIndexI32 = carrierSectionF64F32.sectionIndexI32,
                hybridVertexF64F32 = stateF64F32.startVertexF64F32,
                adjacencyF64F32 = adjacencyF64F32,
            )
        }
    }
    return collapsedIncidencesF64F32
}

/** Source labels here navigate one declared contour only; they never resolve a geometric tie. */
private fun compareCollapsedCarrierContourOrderF64F32(
    firstF64F32: PathCollapsedCarrierStateF64F32,
    secondF64F32: PathCollapsedCarrierStateF64F32,
): Int {
    val firstCarrierF64F32 = firstF64F32.carrierSectionF64F32
    val secondCarrierF64F32 = secondF64F32.carrierSectionF64F32
    firstCarrierF64F32.sourceSpanF64.startLocationF64.sourceSegmentIndexI32.compareTo(
        secondCarrierF64F32.sourceSpanF64.startLocationF64.sourceSegmentIndexI32,
    ).takeIf { it != 0 }?.let { return it }
    firstCarrierF64F32.sourceSectionF64.startParameterF64.compareTo(
        secondCarrierF64F32.sourceSectionF64.startParameterF64,
    ).takeIf { it != 0 }?.let { return it }
    return firstCarrierF64F32.sourceSectionF64.endParameterF64.compareTo(
        secondCarrierF64F32.sourceSectionF64.endParameterF64,
    )
}

private fun collapsedContourRaysF64F32(
    incomingStateF64F32: PathCollapsedCarrierStateF64F32,
    outgoingStateF64F32: PathCollapsedCarrierStateF64F32,
): PathCollapsedAdjacencyF64F32 {
    if (incomingStateF64F32.collapsed || outgoingStateF64F32.collapsed) {
        return PathCollapsedAdjacencyF64F32.Unresolved
    }
    val incomingCarrierF64F32 = incomingStateF64F32.carrierSectionF64F32
    val outgoingCarrierF64F32 = outgoingStateF64F32.carrierSectionF64F32
    val incomingDirectionF64 = incomingCarrierF64F32.sourceSectionF64.startIncidencePointF64 -
        incomingCarrierF64F32.sourceSectionF64.endIncidencePointF64
    val outgoingDirectionF64 = outgoingCarrierF64F32.sourceSectionF64.endIncidencePointF64 -
        outgoingCarrierF64F32.sourceSectionF64.startIncidencePointF64
    if (
        (incomingDirectionF64.x == 0.0 && incomingDirectionF64.y == 0.0) ||
            (outgoingDirectionF64.x == 0.0 && outgoingDirectionF64.y == 0.0)
    ) {
        return PathCollapsedAdjacencyF64F32.Unresolved
    }
    return PathCollapsedAdjacencyF64F32.Rays(
        incomingDirectionF64 = incomingDirectionF64,
        outgoingDirectionF64 = outgoingDirectionF64,
        incomingSourceSpanIdI64 = incomingCarrierF64F32.sourceSpanF64.sourceSpanIdI64,
        incomingSectionIndexI32 = incomingCarrierF64F32.sectionIndexI32,
        outgoingSourceSpanIdI64 = outgoingCarrierF64F32.sourceSpanF64.sourceSpanIdI64,
        outgoingSectionIndexI32 = outgoingCarrierF64F32.sectionIndexI32,
    )
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
    val overlapWitnessInputsF64 = ArrayList<PathContactWitnessF64.OverlapF64>(witnessesF64.size)
    witnessesF64.forEach { witnessF64 ->
        if (witnessF64 is PathContactWitnessF64.OverlapF64) overlapWitnessInputsF64 += witnessF64
    }
    val overlapWitnessesF64 = sortedHybridF64F32(
        overlapWitnessInputsF64,
        candidateWorkBudgetI32,
        ::compareHybridWitnessesF64,
    )
    // The exact capacity pass is separate from overlap filtering and is charged before it reads
    // each incidence count.  No `sumOf`/allocation can otherwise hide work before the ledger.
    preflightHybridLinearF64F32(overlapWitnessesF64.size.toLong(), candidateWorkBudgetI32)
    var claimCountI64 = 0L
    overlapWitnessesF64.forEach { witnessF64 ->
        claimCountI64 = checkedPathWorkAddI64(claimCountI64, witnessF64.incidencesF64.size.toLong())
    }
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
    canonicalPointF64: Point2F64,
    incidencePointF64: Point2F64,
    normalizationF64: PathNormalizationF64,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val identityF64 = locationF64.vertexIdentityF64 ?: throw IllegalStateException("path-arrangement-inconsistent")
    val candidatePointF32 = normalizationF64.denormalize(incidencePointF64)
    if (!candidatePointF32.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
    val seedF64F32 = seedsByIdentityF64[identityF64]
    if (seedF64F32 == null) {
        seedsByIdentityF64[identityF64] = PathHybridVertexSeedF64F32(
            identityF64 = identityF64,
            sourcePointF64 = canonicalPointF64,
            originalPointsF32 = mutableListOf<Point2F32>().also { pointsF32 -> locationF64.originalPointF32?.let(pointsF32::add) },
            incidentCandidatesF32 = mutableListOf(candidatePointF32),
            incidentSourceSpanIdsI64 = mutableListOf(sourceSpanF64.sourceSpanIdI64),
            witnessesF64 = witnessesByIdentityF64[identityF64]?.toMutableList() ?: mutableListOf(),
        )
        return
    }
    // A registry identity can be incident to several independently evaluated F64 cuts.  The
    // point above is only a deterministic proof/order representative; replacing every carrier
    // endpoint with it would hide the per-incidence evaluation that the hybrid lattice must
    // validate below.
    if (compareHybridPointsF64(canonicalPointF64, seedF64F32.sourcePointF64) < 0) {
        seedF64F32.sourcePointF64 = canonicalPointF64
    }
    locationF64.originalPointF32?.let(seedF64F32.originalPointsF32::add)
    seedF64F32.incidentCandidatesF32 += candidatePointF32
    seedF64F32.incidentSourceSpanIdsI64 += sourceSpanF64.sourceSpanIdI64
}

/** Implements the representative priority specified for the hybrid lattice boundary. */
private fun chooseRepresentativePointF32(
    originalPointsF32: List<Point2F32>,
    incidentCandidatesF32: List<Point2F32>,
    incidentSourceSpanIdsI64: List<Long>,
    witnessesF64: List<PathContactWitnessF64>,
): Point2F32 {
    if (originalPointsF32.isNotEmpty()) {
        val firstF32 = originalPointsF32.first()
        var selectedF32 = firstF32
        for (pointIndexI32 in 1 until originalPointsF32.size) {
            val pointF32 = originalPointsF32[pointIndexI32]
            if (!sameHybridPointF32(firstF32, pointF32)) throw IllegalStateException("path-f32-projection-collapse")
            if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
        }
        validateRepresentativeCandidateF64F32(
            selectedF32 = selectedF32,
            incidentCandidatesF32 = incidentCandidatesF32,
            incidentSourceSpanIdsI64 = incidentSourceSpanIdsI64,
            witnessesF64 = witnessesF64,
            requiresSharedWitnessF64 = false,
            originalAuthoritativeF32 = true,
        )
        return selectedF32
    }
    if (incidentCandidatesF32.isEmpty()) throw IllegalStateException("path-f32-projection-collapse")
    val firstF32 = incidentCandidatesF32.first()
    var selectedF32 = firstF32
    var requiresSharedWitnessF64 = false
    for (pointIndexI32 in 1 until incidentCandidatesF32.size) {
        val pointF32 = incidentCandidatesF32[pointIndexI32]
        if (!sameHybridPointF32(firstF32, pointF32)) requiresSharedWitnessF64 = true
        if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
    }
    validateRepresentativeCandidateF64F32(
        selectedF32 = selectedF32,
        incidentCandidatesF32 = incidentCandidatesF32,
        incidentSourceSpanIdsI64 = incidentSourceSpanIdsI64,
        witnessesF64 = witnessesF64,
        requiresSharedWitnessF64 = requiresSharedWitnessF64,
        originalAuthoritativeF32 = false,
    )
    return selectedF32
}

/**
 * The canonical candidate is always one evaluation produced by an incident carrier.  If those
 * evaluations quantize differently, only one exact witness may bridge them; later DCEL ray
 * validation checks every retained carrier direction against the chosen F32 embedding.
 */
private fun validateRepresentativeCandidateF64F32(
    selectedF32: Point2F32,
    incidentCandidatesF32: List<Point2F32>,
    incidentSourceSpanIdsI64: List<Long>,
    witnessesF64: List<PathContactWitnessF64>,
    requiresSharedWitnessF64: Boolean,
    originalAuthoritativeF32: Boolean,
) {
    if (originalAuthoritativeF32) {
        // An original F32 input bit pattern is authoritative even when re-evaluating the
        // normalized F64 carrier lands on its neighbouring lattice point. The DCEL later checks
        // every retained source direction against the resulting embedding.
        return
    }
    // `selectedF32` was selected from this list by the preceding deterministic scan; do not
    // re-scan it only to reconstruct that fact after the caller's preflight.
    if (!requiresSharedWitnessF64) return
    val witnessF64 = witnessesF64.singleOrNull() ?: throw IllegalStateException("path-f32-projection-collapse")
    incidentSourceSpanIdsI64.forEach { sourceSpanIdI64 ->
        val covered = when (witnessF64) {
            is PathContactWitnessF64.PointF64 -> sourceSpanIdI64 in witnessF64.incidentSourceSpanIdsI64
            is PathContactWitnessF64.OverlapF64 ->
                sourceSpanIdI64 in witnessF64.firstSourceSpanIdsI64 ||
                    sourceSpanIdI64 in witnessF64.secondSourceSpanIdsI64
        }
        if (!covered) throw IllegalStateException("path-f32-projection-collapse")
    }
}

private fun chooseOriginalPointF32(originalPointsF32: List<Point2F32>): Point2F32? {
    var selectedF32 = originalPointsF32.firstOrNull() ?: return null
    for (pointIndexI32 in 1 until originalPointsF32.size) {
        val pointF32 = originalPointsF32[pointIndexI32]
        if (compareHybridPointsF32(pointF32, selectedF32) < 0) selectedF32 = pointF32
    }
    return selectedF32
}

/**
 * Build the Point-witness lookup once before the projected AABB walk. Each exact witness
 * reserves its complete incidence list immediately before that list is inspected, so the debit
 * is deterministic from source topology while remaining proportional to actual incidences.
 */
private fun buildPointWitnessIndexF64F32(
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathPointWitnessIndexF64F32 {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    val witnessesBySourceSpanIdI64 = linkedMapOf<Long, MutableList<PathContactWitnessF64.PointF64>>()
    val witnessIdsBySourceSpanIdI64 = linkedMapOf<Long, MutableSet<Long>>()
    val witnessesByIdentityF64 = linkedMapOf<PathVertexIdentityF64, MutableList<PathContactWitnessF64.PointF64>>()
    witnessesF64.forEach { witnessF64 ->
        if (witnessF64 !is PathContactWitnessF64.PointF64) return@forEach
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(witnessF64.incidentSourceSpanIdsI64.size.toLong(), 3L),
                3L,
            ),
            candidateWorkBudgetI32,
        )
        witnessesByIdentityF64.getOrPut(witnessF64.vertexIdentityF64) { mutableListOf() } += witnessF64
        witnessF64.incidentSourceSpanIdsI64.forEach { sourceSpanIdI64 ->
            witnessesBySourceSpanIdI64.getOrPut(sourceSpanIdI64) { mutableListOf() } += witnessF64
            witnessIdsBySourceSpanIdI64.getOrPut(sourceSpanIdI64) { linkedSetOf() } += witnessF64.witnessIdI64
        }
    }
    // Every per-span list has been appended in source witness order only.  Rebuild it in the
    // semantic point order once, before the broad phase, so a backend's map traversal can never
    // influence a subsequent lookup.
    preflightHybridLinearF64F32(witnessesBySourceSpanIdI64.size.toLong(), candidateWorkBudgetI32)
    witnessesBySourceSpanIdI64.values.forEach { witnessesForSpanF64 ->
        val orderedF64 = sortedHybridF64F32(
            witnessesForSpanF64,
            candidateWorkBudgetI32,
            ::compareHybridPointWitnessesF64,
        )
        witnessesForSpanF64.clear()
        witnessesForSpanF64 += orderedF64
    }
    return PathPointWitnessIndexF64F32(
        witnessesBySourceSpanIdI64 = witnessesBySourceSpanIdI64,
        witnessIdsBySourceSpanIdI64 = witnessIdsBySourceSpanIdI64,
        witnessesByIdentityF64 = witnessesByIdentityF64,
    )
}

/**
 * Materialize the exact-overlap incidence registry once. The projected broad phase can then
 * query only the two source carriers under consideration instead of rescanning every exact
 * overlap component for every candidate.
 */
private fun buildOverlapWitnessIndexF64F32(
    witnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathOverlapWitnessIndexF64F32 {
    preflightHybridLinearF64F32(witnessesF64.size.toLong(), candidateWorkBudgetI32)
    var overlapReferenceCountI64 = 0L
    witnessesF64.forEach { witnessF64 ->
        if (witnessF64 is PathContactWitnessF64.OverlapF64) {
            overlapReferenceCountI64 = checkedPathWorkAddI64(
                overlapReferenceCountI64,
                witnessF64.incidencesF64.size.toLong(),
            )
        }
    }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(overlapReferenceCountI64, 2L),
            witnessesF64.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    val referenceCountByInputEdgeIdI32 = mutableMapOf<Int, Int>()
    witnessesF64.forEach { witnessF64 ->
        if (witnessF64 !is PathContactWitnessF64.OverlapF64) return@forEach
        witnessF64.incidencesF64.forEach { incidenceF64 ->
            val currentCountI32 = referenceCountByInputEdgeIdI32[incidenceF64.inputEdgeIdI32] ?: 0
            referenceCountByInputEdgeIdI32[incidenceF64.inputEdgeIdI32] = checkedPathCapacityI32(
                checkedPathWorkAddI64(currentCountI32.toLong(), 1L),
                "path-candidate-limit",
            )
        }
    }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(referenceCountByInputEdgeIdI32.size.toLong(), 2L),
                overlapReferenceCountI64,
            ),
            witnessesF64.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    val mutableReferencesByInputEdgeIdI32 = mutableMapOf<Int, MutableList<PathOverlapWitnessIncidenceReferenceF64F32>>()
    referenceCountByInputEdgeIdI32.forEach { (inputEdgeIdI32, referenceCountI32) ->
        mutableReferencesByInputEdgeIdI32[inputEdgeIdI32] = ArrayList(referenceCountI32)
    }
    witnessesF64.forEach { witnessF64 ->
        if (witnessF64 !is PathContactWitnessF64.OverlapF64) return@forEach
        witnessF64.incidencesF64.forEach { incidenceF64 ->
            mutableReferencesByInputEdgeIdI32.getValue(incidenceF64.inputEdgeIdI32) +=
                PathOverlapWitnessIncidenceReferenceF64F32(witnessF64, incidenceF64)
        }
    }
    preflightHybridLinearF64F32(referenceCountByInputEdgeIdI32.size.toLong(), candidateWorkBudgetI32)
    val orderedIncidencesByInputEdgeIdI32 = mutableMapOf<Int, List<PathOverlapWitnessIncidenceReferenceF64F32>>()
    mutableReferencesByInputEdgeIdI32.forEach { (inputEdgeIdI32, referencesF64F32) ->
        val orderedReferencesF64F32 = sortedHybridF64F32(
            referencesF64F32,
            candidateWorkBudgetI32,
            ::compareOverlapWitnessIncidenceReferencesF64F32,
        )
        preflightHybridLinearF64F32(orderedReferencesF64F32.size.toLong(), candidateWorkBudgetI32)
        var referenceIndexI32 = 1
        while (referenceIndexI32 < orderedReferencesF64F32.size) {
            val firstF64F32 = orderedReferencesF64F32[referenceIndexI32 - 1]
            val secondF64F32 = orderedReferencesF64F32[referenceIndexI32]
            if (firstF64F32.witnessF64.witnessIdI64 == secondF64F32.witnessF64.witnessIdI64) {
                throw IllegalStateException("path-arrangement-inconsistent")
            }
            referenceIndexI32 += 1
        }
        orderedIncidencesByInputEdgeIdI32[inputEdgeIdI32] = orderedReferencesF64F32
    }
    return PathOverlapWitnessIndexF64F32(orderedIncidencesByInputEdgeIdI32)
}

/**
 * Finds only complete opposite-operand overlap components.  A local relation is insufficient:
 * each exact carrier must have one reciprocal full-section counterpart, the source-parameter
 * intervals must agree exactly, and every rail in the contour pair must share one orientation.
 * The resulting plan is a validation gate for deferred projected endpoint observations; it
 * creates neither a projected alias nor a cut.
 */
private fun completeExactOppositeContourComponentsF64F32(
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    overlapWitnessIndexF64F32: PathOverlapWitnessIndexF64F32,
    contactWitnessesF64: List<PathContactWitnessF64>,
    hasSelfClosedSourcePrimitiveF64: Boolean,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathExactOppositeContourComponentPlanF64F32 {
    if (!hasSelfClosedSourcePrimitiveF64 || carrierSectionsF64F32.isEmpty()) {
        return PathExactOppositeContourComponentPlanF64F32(emptyList())
    }
    // Deferred projected endpoint contacts can only be discharged by an exact overlap that
    // crosses operands. Same-operand duplicate carriers still need their source registry
    // evidence, but cannot form the required opposite contour component. Prove that absence
    // from source-owned incidences before allocating the complete reciprocal-cover index.
    if (!hasCrossOperandOverlapWitnessF64F32(
            carrierSectionsF64F32 = carrierSectionsF64F32,
            contactWitnessesF64 = contactWitnessesF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    ) {
        return PathExactOppositeContourComponentPlanF64F32(emptyList())
    }
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 12L),
        candidateWorkBudgetI32,
    )
    val carrierByKeyF64F32 = mutableMapOf<PathExactSourceCarrierKeyF64F32, PathHybridCarrierSectionF64F32>()
    val carriersByInputEdgeIdI32 = mutableMapOf<Int, MutableList<PathHybridCarrierSectionF64F32>>()
    val carriersByContourKeyF64F32 = mutableMapOf<
        PathExactSourceContourKeyF64F32,
        MutableList<PathExactSourceCarrierKeyF64F32>,
        >()
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val carrierKeyF64F32 = carrierSectionF64F32.toExactSourceCarrierKeyF64F32()
        if (carrierByKeyF64F32.put(carrierKeyF64F32, carrierSectionF64F32) != null) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        carriersByInputEdgeIdI32.getOrPut(carrierSectionF64F32.sourceSectionF64.inputEdgeIdI32) {
            mutableListOf()
        } += carrierSectionF64F32
        carriersByContourKeyF64F32.getOrPut(carrierSectionF64F32.toExactSourceContourKeyF64F32()) {
            mutableListOf()
        } += carrierKeyF64F32
    }

    // Count every registry-reference and exact-kernel pairing before allocating the match map.
    // The `64` envelope covers both interval proofs and the two-source-section exact predicate.
    var relationWorkI64 = 0L
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val referencesF64F32 = overlapWitnessIndexF64F32.orderedIncidencesByInputEdgeIdI32[
            carrierSectionF64F32.sourceSectionF64.inputEdgeIdI32
        ].orEmpty()
        relationWorkI64 = checkedPathWorkAddI64(relationWorkI64, 2L)
        referencesF64F32.forEach { referenceF64F32 ->
            relationWorkI64 = checkedPathWorkAddI64(
                relationWorkI64,
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(referenceF64F32.witnessF64.incidencesF64.size.toLong(), 2L),
                    4L,
                ),
            )
            referenceF64F32.witnessF64.incidencesF64.forEach { counterpartIncidenceF64 ->
                relationWorkI64 = checkedPathWorkAddI64(
                    relationWorkI64,
                    checkedPathWorkMultiplyI64(
                        carriersByInputEdgeIdI32[counterpartIncidenceF64.inputEdgeIdI32].orEmpty().size.toLong(),
                        64L,
                    ),
                )
            }
        }
    }
    preflightHybridLinearF64F32(relationWorkI64, candidateWorkBudgetI32)
    val matchByCarrierKeyF64F32 = mutableMapOf<PathExactSourceCarrierKeyF64F32, PathExactOppositeCarrierMatchF64F32>()
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val matchF64F32 = uniqueFullExactOppositeCarrierMatchF64F32(
            carrierSectionF64F32 = carrierSectionF64F32,
            referencesF64F32 = overlapWitnessIndexF64F32.orderedIncidencesByInputEdgeIdI32[
                carrierSectionF64F32.sourceSectionF64.inputEdgeIdI32
            ].orEmpty(),
            carriersByInputEdgeIdI32 = carriersByInputEdgeIdI32,
        )
        if (matchF64F32 != null) {
            matchByCarrierKeyF64F32[carrierSectionF64F32.toExactSourceCarrierKeyF64F32()] = matchF64F32
        }
    }

    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 18L),
        candidateWorkBudgetI32,
    )
    val componentsF64F32 = mutableListOf<PathExactOppositeContourComponentF64F32>()
    carriersByContourKeyF64F32.forEach { (firstContourKeyF64F32, firstCarrierKeysF64F32) ->
        if (firstContourKeyF64F32.operand != PathOperand.FIRST) return@forEach
        var counterpartContourKeyF64F32: PathExactSourceContourKeyF64F32? = null
        var componentOrientationI32 = 0
        val counterpartCarrierKeysF64F32 = mutableSetOf<PathExactSourceCarrierKeyF64F32>()
        var complete = true
        firstCarrierKeysF64F32.forEach { firstCarrierKeyF64F32 ->
            val matchF64F32 = matchByCarrierKeyF64F32[firstCarrierKeyF64F32]
            if (matchF64F32 == null) {
                complete = false
                return@forEach
            }
            val counterpartCarrierSectionF64F32 = carrierByKeyF64F32[matchF64F32.counterpartCarrierKeyF64F32]
            if (counterpartCarrierSectionF64F32 == null) {
                complete = false
                return@forEach
            }
            val nextContourKeyF64F32 = counterpartCarrierSectionF64F32.toExactSourceContourKeyF64F32()
            if (
                nextContourKeyF64F32.operand != PathOperand.SECOND ||
                    (counterpartContourKeyF64F32 != null && counterpartContourKeyF64F32 != nextContourKeyF64F32) ||
                    !counterpartCarrierKeysF64F32.add(matchF64F32.counterpartCarrierKeyF64F32)
            ) {
                complete = false
                return@forEach
            }
            val reciprocalF64F32 = matchByCarrierKeyF64F32[matchF64F32.counterpartCarrierKeyF64F32]
            if (
                reciprocalF64F32 == null ||
                    reciprocalF64F32.counterpartCarrierKeyF64F32 != firstCarrierKeyF64F32 ||
                    reciprocalF64F32.orientationI32 != matchF64F32.orientationI32
            ) {
                complete = false
                return@forEach
            }
            counterpartContourKeyF64F32 = nextContourKeyF64F32
            if (componentOrientationI32 == 0) {
                componentOrientationI32 = matchF64F32.orientationI32
            } else if (componentOrientationI32 != matchF64F32.orientationI32) {
                complete = false
            }
        }
        val resolvedCounterpartContourKeyF64F32 = counterpartContourKeyF64F32
        val expectedCounterpartCarrierKeysF64F32 = resolvedCounterpartContourKeyF64F32?.let(carriersByContourKeyF64F32::get)
        if (
            !complete || resolvedCounterpartContourKeyF64F32 == null || componentOrientationI32 == 0 ||
                expectedCounterpartCarrierKeysF64F32 == null ||
                counterpartCarrierKeysF64F32.size != firstCarrierKeysF64F32.size ||
                counterpartCarrierKeysF64F32.size != expectedCounterpartCarrierKeysF64F32.size ||
                !counterpartCarrierKeysF64F32.containsAll(expectedCounterpartCarrierKeysF64F32)
        ) {
            return@forEach
        }
        componentsF64F32 += PathExactOppositeContourComponentF64F32(
            firstContourIndexI32 = firstContourKeyF64F32.contourIndexI32,
            secondContourIndexI32 = resolvedCounterpartContourKeyF64F32.contourIndexI32,
            orientationI32 = componentOrientationI32,
        )
    }
    val orderedComponentsF64F32 = sortedHybridF64F32(
        componentsF64F32,
        candidateWorkBudgetI32,
    ) { firstF64F32, secondF64F32 ->
        firstF64F32.firstContourIndexI32.compareTo(secondF64F32.firstContourIndexI32)
            .takeIf { it != 0 }
            ?: firstF64F32.secondContourIndexI32.compareTo(secondF64F32.secondContourIndexI32)
            .takeIf { it != 0 }
            ?: firstF64F32.orientationI32.compareTo(secondF64F32.orientationI32)
    }
    return PathExactOppositeContourComponentPlanF64F32(orderedComponentsF64F32)
}

/**
 * Determines whether the source registry contains any exact overlap witness spanning both
 * operands. This is a coarse eligibility proof only: a `true` result still runs the complete
 * reciprocal contour-cover validation below. A `false` result is conclusive because projected
 * contacts are never permitted to manufacture an exact source overlap.
 */
private fun hasCrossOperandOverlapWitnessF64F32(
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    contactWitnessesF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    var overlapIncidenceCountI64 = 0L
    contactWitnessesF64.forEach { witnessF64 ->
        if (witnessF64 is PathContactWitnessF64.OverlapF64) {
            overlapIncidenceCountI64 = checkedPathWorkAddI64(
                overlapIncidenceCountI64,
                witnessF64.incidencesF64.size.toLong(),
            )
        }
    }
    // Count and reserve the complete source-only classification before its map allocation.
    // The scan cannot depend on projected geometry, backend iteration order, or early-return
    // timing: it has one debit for every carrier, witness dispatch, and exact incidence.
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                carrierSectionsF64F32.size.toLong(),
                contactWitnessesF64.size.toLong(),
            ),
            overlapIncidenceCountI64,
        ),
        candidateWorkBudgetI32,
    )
    val operandByInputEdgeIdI32 = mutableMapOf<Int, PathOperand>()
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val inputEdgeIdI32 = carrierSectionF64F32.sourceSectionF64.inputEdgeIdI32
        val operand = carrierSectionF64F32.sourceSpanF64.operand
        val previousOperand = operandByInputEdgeIdI32.put(inputEdgeIdI32, operand)
        if (previousOperand != null && previousOperand != operand) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
    }
    contactWitnessesF64.forEach { witnessF64 ->
        if (witnessF64 !is PathContactWitnessF64.OverlapF64) return@forEach
        var hasFirstOperand = false
        var hasSecondOperand = false
        witnessF64.incidencesF64.forEach { incidenceF64 ->
            when (operandByInputEdgeIdI32[incidenceF64.inputEdgeIdI32]
                ?: throw IllegalStateException("path-arrangement-inconsistent")) {
                PathOperand.FIRST -> hasFirstOperand = true
                PathOperand.SECOND -> hasSecondOperand = true
            }
        }
        if (hasFirstOperand && hasSecondOperand) return true
    }
    return false
}

private fun PathHybridCarrierSectionF64F32.toExactSourceCarrierKeyF64F32(): PathExactSourceCarrierKeyF64F32 =
    PathExactSourceCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)

private fun PathHybridCarrierSectionF64F32.toExactSourceContourKeyF64F32(): PathExactSourceContourKeyF64F32 =
    PathExactSourceContourKeyF64F32(sourceSpanF64.operand, sourceSpanF64.contourIndexI32)

private fun uniqueFullExactOppositeCarrierMatchF64F32(
    carrierSectionF64F32: PathHybridCarrierSectionF64F32,
    referencesF64F32: List<PathOverlapWitnessIncidenceReferenceF64F32>,
    carriersByInputEdgeIdI32: Map<Int, List<PathHybridCarrierSectionF64F32>>,
): PathExactOppositeCarrierMatchF64F32? {
    var selectedMatchF64F32: PathExactOppositeCarrierMatchF64F32? = null
    var ambiguous = false
    referencesF64F32.forEach { referenceF64F32 ->
        if (!overlapIncidenceCoversFullSourceCarrierF64F32(referenceF64F32.incidenceF64, carrierSectionF64F32)) {
            return@forEach
        }
        referenceF64F32.witnessF64.incidencesF64.forEach { counterpartIncidenceF64 ->
            if (
                counterpartIncidenceF64.inputEdgeIdI32 == referenceF64F32.incidenceF64.inputEdgeIdI32 ||
                    counterpartIncidenceF64.inputEdgeIdI32 == carrierSectionF64F32.sourceSectionF64.inputEdgeIdI32
            ) {
                return@forEach
            }
            carriersByInputEdgeIdI32[counterpartIncidenceF64.inputEdgeIdI32].orEmpty().forEach { counterpartCarrierSectionF64F32 ->
                val orientationI32 = fullExactOppositeCarrierOrientationF64F32(
                    firstCarrierSectionF64F32 = carrierSectionF64F32,
                    firstIncidenceF64 = referenceF64F32.incidenceF64,
                    secondCarrierSectionF64F32 = counterpartCarrierSectionF64F32,
                    secondIncidenceF64 = counterpartIncidenceF64,
                ) ?: return@forEach
                val candidateMatchF64F32 = PathExactOppositeCarrierMatchF64F32(
                    counterpartCarrierKeyF64F32 = counterpartCarrierSectionF64F32.toExactSourceCarrierKeyF64F32(),
                    orientationI32 = orientationI32,
                )
                val previousMatchF64F32 = selectedMatchF64F32
                if (previousMatchF64F32 == null) {
                    selectedMatchF64F32 = candidateMatchF64F32
                } else if (previousMatchF64F32 != candidateMatchF64F32) {
                    ambiguous = true
                }
            }
        }
    }
    return selectedMatchF64F32?.takeUnless { ambiguous }
}

private fun fullExactOppositeCarrierOrientationF64F32(
    firstCarrierSectionF64F32: PathHybridCarrierSectionF64F32,
    firstIncidenceF64: PathOverlapWitnessIncidenceF64,
    secondCarrierSectionF64F32: PathHybridCarrierSectionF64F32,
    secondIncidenceF64: PathOverlapWitnessIncidenceF64,
): Int? {
    if (firstCarrierSectionF64F32.sourceSpanF64.operand == secondCarrierSectionF64F32.sourceSpanF64.operand) return null
    if (
        !overlapIncidenceCoversFullSourceCarrierF64F32(firstIncidenceF64, firstCarrierSectionF64F32) ||
            !overlapIncidenceCoversFullSourceCarrierF64F32(secondIncidenceF64, secondCarrierSectionF64F32)
    ) {
        return null
    }
    val firstSectionF64 = firstCarrierSectionF64F32.sourceSectionF64
    val secondSectionF64 = secondCarrierSectionF64F32.sourceSectionF64
    val overlapF64 = intersectPathEdgesF64(
        exactSourceCarrierEdgeF64F32(firstCarrierSectionF64F32),
        exactSourceCarrierEdgeF64F32(secondCarrierSectionF64F32),
    ) as? PathIntersectionF64.OverlapF64 ?: return null
    if (overlapF64.firstStartParameter != 0.0 || overlapF64.firstEndParameter != 1.0) return null
    val orientationI32 = when {
        overlapF64.secondStartParameter == 0.0 && overlapF64.secondEndParameter == 1.0 -> 1
        overlapF64.secondStartParameter == 1.0 && overlapF64.secondEndParameter == 0.0 -> -1
        else -> return null
    }
    val firstLowerParameterF64 = minOf(firstSectionF64.startParameterF64, firstSectionF64.endParameterF64)
    val firstUpperParameterF64 = maxOf(firstSectionF64.startParameterF64, firstSectionF64.endParameterF64)
    val secondLowerParameterF64 = minOf(secondSectionF64.startParameterF64, secondSectionF64.endParameterF64)
    val secondUpperParameterF64 = maxOf(secondSectionF64.startParameterF64, secondSectionF64.endParameterF64)
    val hasMatchingExactInterval = when (orientationI32) {
        1 -> firstLowerParameterF64 == secondLowerParameterF64 &&
            firstUpperParameterF64 == secondUpperParameterF64
        -1 -> firstLowerParameterF64 == 1.0 - secondUpperParameterF64 &&
            firstUpperParameterF64 == 1.0 - secondLowerParameterF64
        else -> false
    }
    return orientationI32.takeIf { hasMatchingExactInterval }
}

private fun overlapIncidenceCoversFullSourceCarrierF64F32(
    incidenceF64: PathOverlapWitnessIncidenceF64,
    carrierSectionF64F32: PathHybridCarrierSectionF64F32,
): Boolean {
    val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
    if (
        incidenceF64.inputEdgeIdI32 != sourceSectionF64.inputEdgeIdI32 ||
            carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64 !in incidenceF64.sourceSpanIdsI64 ||
            incidenceF64.inputEdgeIdI32 !in sourceSectionF64.startIdentityF64.parameterByEdgeId ||
            incidenceF64.inputEdgeIdI32 !in sourceSectionF64.endIdentityF64.parameterByEdgeId
    ) {
        return false
    }
    val lowerIncidenceParameterF64 = minOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
    val upperIncidenceParameterF64 = maxOf(incidenceF64.startParameterF64, incidenceF64.endParameterF64)
    return sourceSectionF64.startParameterF64 >= lowerIncidenceParameterF64 &&
        sourceSectionF64.startParameterF64 <= upperIncidenceParameterF64 &&
        sourceSectionF64.endParameterF64 >= lowerIncidenceParameterF64 &&
        sourceSectionF64.endParameterF64 <= upperIncidenceParameterF64
}

/** A carrier already retains the exact source section consumed by the overlap predicate. */
private fun exactSourceCarrierEdgeF64F32(carrierSectionF64F32: PathHybridCarrierSectionF64F32): PathInputEdgeF64 {
    val sourceSpanF64 = carrierSectionF64F32.sourceSpanF64
    val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
    return PathInputEdgeF64(
        idI32 = sourceSectionF64.inputEdgeIdI32,
        operand = sourceSpanF64.operand,
        contourIndexI32 = sourceSpanF64.contourIndexI32,
        sourceSegmentIndexI32 = sourceSpanF64.startLocationF64.sourceSegmentIndexI32,
        sourceStartParameterF64 = sourceSectionF64.startParameterF64,
        sourceEndParameterF64 = sourceSectionF64.endParameterF64,
        startIdentityF64 = sourceSectionF64.startIdentityF64,
        endIdentityF64 = sourceSectionF64.endIdentityF64,
        startPointF64 = sourceSectionF64.startIncidencePointF64,
        endPointF64 = sourceSectionF64.endIncidencePointF64,
        windingDeltaI32 = sourceSpanF64.windingDeltaI32,
    )
}

private fun compareOverlapWitnessIncidenceReferencesF64F32(
    firstF64F32: PathOverlapWitnessIncidenceReferenceF64F32,
    secondF64F32: PathOverlapWitnessIncidenceReferenceF64F32,
): Int = firstF64F32.witnessF64.witnessIdI64.compareTo(secondF64F32.witnessF64.witnessIdI64)

/**
 * Returns the one exact Point witness jointly incident to two source spans.  A second exact
 * witness is an ambiguity rather than an ordering opportunity, so no witness/edge label can
 * choose a winner.
 */
private fun uniqueSharedPointWitnessF64F32(
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    firstSourceSpanIdI64: Long,
    secondSourceSpanIdI64: Long,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathContactWitnessF64.PointF64? {
    val firstWitnessesF64 = pointWitnessIndexF64F32.witnessesBySourceSpanIdI64[firstSourceSpanIdI64].orEmpty()
    val secondWitnessIdsI64 = pointWitnessIndexF64F32.witnessIdsBySourceSpanIdI64[secondSourceSpanIdI64].orEmpty()
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(firstWitnessesF64.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var selectedF64: PathContactWitnessF64.PointF64? = null
    firstWitnessesF64.forEach { witnessF64 ->
        if (witnessF64.witnessIdI64 !in secondWitnessIdsI64) return@forEach
        val previousF64 = selectedF64
        if (previousF64 != null && previousF64.witnessIdI64 != witnessF64.witnessIdI64) {
            return null
        }
        selectedF64 = witnessF64
    }
    return selectedF64
}

private fun localPointWitnessForProjectedPairF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathContactWitnessF64.PointF64? {
    val witnessF64 = uniqueSharedPointWitnessF64F32(
        pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
        firstSourceSpanIdI64 = firstF64F32.sourceSpanF64.sourceSpanIdI64,
        secondSourceSpanIdI64 = secondF64F32.sourceSpanF64.sourceSpanIdI64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    ) ?: return null
    // A Point witness may connect separate source rails, including a complete n-way component,
    // but it cannot turn two non-adjacent sections of one uninterrupted source span into a
    // coincidence.  Such a projected overlap is precisely a lost intra-primitive relation; it
    // needs an independently materialized source event and therefore conservatively rejects.
    if (firstF64F32.sourceSpanF64.sourceSpanIdI64 == secondF64F32.sourceSpanF64.sourceSpanIdI64) return null
    val pointContactF64 = projectedContactF64 as? PathIntersectionF64.PointF64 ?: return witnessF64
    val projectedPointsF32 = projectedContactPointsF32(projectedContactF64)
    // A Point witness is scoped to the exact source-section relation that created it.  A source
    // span can contain many flattened sections, so span membership alone would let an unrelated
    // projected intra-contour collision borrow a cross-operand/n-way witness.  Re-run the exact
    // kernel on precisely these two original sections before the witness can admit a claim.
    // This is a proof check only; F32 supplies neither the relation nor an identity.
    preflightHybridLinearF64F32(9L, candidateWorkBudgetI32)
    if (!exactSourceSectionPointWitnessSupportsProjectedContactF64F32(
            firstF64F32 = firstF64F32,
            secondF64F32 = secondF64F32,
            pointWitnessF64 = witnessF64,
            projectedContactF64 = pointContactF64,
        )
    ) {
        return null
    }
    if (!projectedSpanTouchesPointWitnessF64F32(firstF64F32, witnessF64, projectedContactF64, first = true)) return null
    if (!projectedSpanTouchesPointWitnessF64F32(secondF64F32, witnessF64, projectedContactF64, first = false)) return null
    val witnessVertexIndexI32 = vertexIndexByIdentityF64[witnessF64.vertexIdentityF64] ?: return null
    val witnessPointF32 = verticesF64F32[witnessVertexIndexI32].representativePointF32
    return witnessF64.takeIf { witnessF64 ->
        projectedPointsF32.any { pointF32 -> sameHybridPointF32(pointF32, witnessPointF32) }
    }
}

/**
 * A source-span witness is an n-way incidence summary, not permission to combine arbitrary
 * sections of that span.  This proof keeps the Point path pair-local: the original F64 sections
 * must meet at the witness itself and at the same carrier endpoints observed in the projected
 * pair.  It deliberately has no coordinate-based F32 fallback.
 */
private fun exactSourceSectionPointWitnessSupportsProjectedContactF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    pointWitnessF64: PathContactWitnessF64.PointF64,
    projectedContactF64: PathIntersectionF64.PointF64,
): Boolean {
    val sourceContactF64 = intersectPathEdgesF64(
        exactSourceSectionEdgeF64F32(firstF64F32),
        exactSourceSectionEdgeF64F32(secondF64F32),
    ) as? PathIntersectionF64.PointF64 ?: return false
    return sameHybridPointF64(sourceContactF64.point, pointWitnessF64.pointF64) &&
        sourceContactF64.firstT == projectedContactF64.firstT &&
        sourceContactF64.secondT == projectedContactF64.secondT
}

private fun exactSourceSectionEdgeF64F32(
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
): PathInputEdgeF64 {
    val sourceSpanF64 = projectedSpanF64F32.sourceSpanF64
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    return PathInputEdgeF64(
        idI32 = sourceSectionF64.inputEdgeIdI32,
        operand = sourceSpanF64.operand,
        contourIndexI32 = sourceSpanF64.contourIndexI32,
        sourceSegmentIndexI32 = sourceSpanF64.startLocationF64.sourceSegmentIndexI32,
        sourceStartParameterF64 = sourceSectionF64.startParameterF64,
        sourceEndParameterF64 = sourceSectionF64.endParameterF64,
        startIdentityF64 = sourceSectionF64.startIdentityF64,
        endIdentityF64 = sourceSectionF64.endIdentityF64,
        startPointF64 = sourceSectionF64.startIncidencePointF64,
        endPointF64 = sourceSectionF64.endIncidencePointF64,
        windingDeltaI32 = sourceSpanF64.windingDeltaI32,
    )
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

private fun isProjectedEndpointContactF64F32(projectedContactF64: PathIntersectionF64.PointF64): Boolean =
    (projectedContactF64.firstT == 0.0 || projectedContactF64.firstT == 1.0) &&
        (projectedContactF64.secondT == 0.0 || projectedContactF64.secondT == 1.0)

private fun exactOverlapSupportsProjectedPointF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64.PointF64,
    overlapWitnessIndexF64F32: PathOverlapWitnessIndexF64F32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean = overlapWitnessIndexSupportsProjectedContactF64F32(
    overlapWitnessIndexF64F32 = overlapWitnessIndexF64F32,
    firstF64F32 = firstF64F32,
    firstStartParameterF64 = projectedContactF64.firstT,
    firstEndParameterF64 = projectedContactF64.firstT,
    secondF64F32 = secondF64F32,
    secondStartParameterF64 = projectedContactF64.secondT,
    secondEndParameterF64 = projectedContactF64.secondT,
    candidateWorkBudgetI32 = candidateWorkBudgetI32,
)

private fun exactOverlapSupportsProjectedOverlapF64F32(
    firstF64F32: PathProjectedSourceSpanF64F32,
    secondF64F32: PathProjectedSourceSpanF64F32,
    projectedContactF64: PathIntersectionF64.OverlapF64,
    overlapWitnessIndexF64F32: PathOverlapWitnessIndexF64F32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean = overlapWitnessIndexSupportsProjectedContactF64F32(
    overlapWitnessIndexF64F32 = overlapWitnessIndexF64F32,
    firstF64F32 = firstF64F32,
    firstStartParameterF64 = projectedContactF64.firstStartParameter,
    firstEndParameterF64 = projectedContactF64.firstEndParameter,
    secondF64F32 = secondF64F32,
    secondStartParameterF64 = projectedContactF64.secondStartParameter,
    secondEndParameterF64 = projectedContactF64.secondEndParameter,
    candidateWorkBudgetI32 = candidateWorkBudgetI32,
)

private fun overlapWitnessIndexSupportsProjectedContactF64F32(
    overlapWitnessIndexF64F32: PathOverlapWitnessIndexF64F32,
    firstF64F32: PathProjectedSourceSpanF64F32,
    firstStartParameterF64: Double,
    firstEndParameterF64: Double,
    secondF64F32: PathProjectedSourceSpanF64F32,
    secondStartParameterF64: Double,
    secondEndParameterF64: Double,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    // Charge the two exact registry lookups before reading either list.  The subsequent
    // candidate-dependent scan is charged separately from their measured canonical sizes.
    preflightHybridLinearF64F32(2L, candidateWorkBudgetI32)
    val firstReferencesF64F32 = overlapWitnessIndexF64F32.orderedIncidencesByInputEdgeIdI32[
        firstF64F32.sourceSectionF64.inputEdgeIdI32
    ].orEmpty()
    val secondReferencesF64F32 = overlapWitnessIndexF64F32.orderedIncidencesByInputEdgeIdI32[
        secondF64F32.sourceSectionF64.inputEdgeIdI32
    ].orEmpty()
    // Reserve every possible join iteration before the first pair is read.  Each iteration
    // visits two references, compares their witness IDs, may compare edge IDs, and may execute
    // both full interval-coverage chains.  The `R1 + R2` envelope remains linear while covering
    // arbitrarily many common but non-covering atomic witnesses before a later covering one.
    val joinReferenceWorkI64 = checkedPathWorkAddI64(
        firstReferencesF64F32.size.toLong(),
        secondReferencesF64F32.size.toLong(),
    )
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(joinReferenceWorkI64, 8L),
        candidateWorkBudgetI32,
    )
    var firstIndexI32 = 0
    var secondIndexI32 = 0
    while (firstIndexI32 < firstReferencesF64F32.size && secondIndexI32 < secondReferencesF64F32.size) {
        val firstReferenceF64F32 = firstReferencesF64F32[firstIndexI32]
        val secondReferenceF64F32 = secondReferencesF64F32[secondIndexI32]
        when {
            firstReferenceF64F32.witnessF64.witnessIdI64 < secondReferenceF64F32.witnessF64.witnessIdI64 -> {
                firstIndexI32 += 1
            }
            firstReferenceF64F32.witnessF64.witnessIdI64 > secondReferenceF64F32.witnessF64.witnessIdI64 -> {
                secondIndexI32 += 1
            }
            else -> {
                if (
                    firstReferenceF64F32.incidenceF64.inputEdgeIdI32 != secondReferenceF64F32.incidenceF64.inputEdgeIdI32 &&
                    overlapIncidenceCoversProjectedRailF64F32(
                        firstReferenceF64F32.incidenceF64,
                        firstF64F32,
                        firstStartParameterF64,
                        firstEndParameterF64,
                    ) && overlapIncidenceCoversProjectedRailF64F32(
                        secondReferenceF64F32.incidenceF64,
                        secondF64F32,
                        secondStartParameterF64,
                        secondEndParameterF64,
                    )
                ) {
                    return true
                }
                // This atomic witness may cover a different sub-rail of either source edge.
                // Advance both ordered lists and keep looking; returning false here would turn a
                // valid later atomic interval into an order-dependent rejection.
                firstIndexI32 += 1
                secondIndexI32 += 1
            }
        }
    }
    return false
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
    val orderedF64F32 = sortedHybridF64F32(
        proposalsF64F32,
        candidateWorkBudgetI32,
        ::compareProjectedCoincidenceTransactionProposalsF64F32,
    )
    var nextIdI64 = 0L
    var previousF64F32: PathProjectedCoincidenceProposalF64F32? = null
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(orderedF64F32.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
    return orderedF64F32.map { proposalF64F32 ->
        val previous = previousF64F32
        if (
            previous != null &&
                compareProjectedCoincidenceTransactionProposalsF64F32(previous, proposalF64F32) != 0
        ) {
            nextIdI64 += 1L
        }
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

private data class PathProjectedCoincidencePairKeyF64F32(
    val witnessIdI64: Long,
    val lowerSourceSpanIdI64: Long,
    val upperSourceSpanIdI64: Long,
)

private data class PathProjectedClaimProposalSideF64F32(
    val claimF64: PathProjectedSpanClaimF64,
    val proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    val firstSide: Boolean,
)

/**
 * A Point witness may seed exactly one local projected run.  The run is checked as a complete
 * transaction rather than accepted candidate by candidate: each side must start or end at the
 * witness, every following source section must be monotone and adjacent, and no exact event,
 * seam, original endpoint, or non-coincident gap may be crossed.  This is provenance-only; the
 * F32 point is checked only against the already selected representative at the anchor.
 */
private fun validateLocalProjectedCoincidenceChainsF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalsF64F32.size.toLong(), 12L),
            2L,
        ),
        candidateWorkBudgetI32,
    )
    val proposalsByPairF64F32 = linkedMapOf<
        PathProjectedCoincidencePairKeyF64F32,
        MutableList<PathProjectedCoincidenceProposalF64F32>,
        >()
    proposalsF64F32.forEach { proposalF64F32 ->
        val firstSourceSpanIdI64 = proposalF64F32.firstClaimF64.sourceSpanIdI64
        val secondSourceSpanIdI64 = proposalF64F32.secondClaimF64.sourceSpanIdI64
        if (firstSourceSpanIdI64 == secondSourceSpanIdI64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val keyF64F32 = PathProjectedCoincidencePairKeyF64F32(
            witnessIdI64 = proposalF64F32.pointWitnessF64.witnessIdI64,
            lowerSourceSpanIdI64 = minOf(firstSourceSpanIdI64, secondSourceSpanIdI64),
            upperSourceSpanIdI64 = maxOf(firstSourceSpanIdI64, secondSourceSpanIdI64),
        )
        proposalsByPairF64F32.getOrPut(keyF64F32) { mutableListOf() } += proposalF64F32
    }
    proposalsByPairF64F32.values.forEach { pairProposalsF64F32 ->
        val witnessF64 = pairProposalsF64F32.firstOrNull()?.pointWitnessF64
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        if (pairProposalsF64F32.any { proposalF64F32 ->
                proposalF64F32.pointWitnessF64.witnessIdI64 != witnessF64.witnessIdI64
            }
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val proposalsBySourceSpanIdI64 = linkedMapOf<Long, MutableList<PathProjectedClaimProposalSideF64F32>>()
        pairProposalsF64F32.forEach { proposalF64F32 ->
            proposalsBySourceSpanIdI64.getOrPut(proposalF64F32.firstClaimF64.sourceSpanIdI64) { mutableListOf() } +=
                PathProjectedClaimProposalSideF64F32(
                    claimF64 = proposalF64F32.firstClaimF64,
                    proposalF64F32 = proposalF64F32,
                    firstSide = true,
                )
            proposalsBySourceSpanIdI64.getOrPut(proposalF64F32.secondClaimF64.sourceSpanIdI64) { mutableListOf() } +=
                PathProjectedClaimProposalSideF64F32(
                    claimF64 = proposalF64F32.secondClaimF64,
                    proposalF64F32 = proposalF64F32,
                    firstSide = false,
                )
        }
        if (proposalsBySourceSpanIdI64.size != 2) throw IllegalStateException("path-f32-projection-collapse")
        proposalsBySourceSpanIdI64.forEach { (sourceSpanIdI64, sidesF64F32) ->
            validateLocalProjectedCoincidenceChainSideF64F32(
                sourceSpanIdI64 = sourceSpanIdI64,
                sidesF64F32 = sidesF64F32,
                witnessF64 = witnessF64,
                pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
                witnessesByIdentityF64 = witnessesByIdentityF64,
                verticesF64F32 = verticesF64F32,
                vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )
        }
    }
}

private fun validateLocalProjectedCoincidenceChainSideF64F32(
    sourceSpanIdI64: Long,
    sidesF64F32: List<PathProjectedClaimProposalSideF64F32>,
    witnessF64: PathContactWitnessF64.PointF64,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(sidesF64F32.size.toLong(), 9L),
            witnessF64.incidentSourceSpanIdsI64.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    if (sourceSpanIdI64 !in witnessF64.incidentSourceSpanIdsI64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val orderedSidesF64F32 = sortedHybridF64F32(
        sidesF64F32,
        candidateWorkBudgetI32,
        ::compareProjectedClaimProposalSidesF64F32,
    )
    val firstSideF64F32 = orderedSidesF64F32.firstOrNull()
        ?: throw IllegalStateException("path-arrangement-inconsistent")
    var previousSideF64F32 = firstSideF64F32
    orderedSidesF64F32.drop(1).forEach { sideF64F32 ->
        val previousClaimF64 = previousSideF64F32.claimF64
        val claimF64 = sideF64F32.claimF64
        if (
            previousClaimF64.startParameterF64 == claimF64.startParameterF64 &&
                previousClaimF64.endParameterF64 == claimF64.endParameterF64
        ) {
            if (
                previousClaimF64.startVertexIdentityF64 != claimF64.startVertexIdentityF64 ||
                    previousClaimF64.endVertexIdentityF64 != claimF64.endVertexIdentityF64 ||
                    previousClaimF64.sourceSectionIndexI32 != claimF64.sourceSectionIndexI32
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            return@forEach
        }
        if (
            previousClaimF64.endParameterF64 != claimF64.startParameterF64 ||
                previousClaimF64.endVertexIdentityF64 != claimF64.startVertexIdentityF64 ||
                claimF64.sourceSectionIndexI32 != previousClaimF64.sourceSectionIndexI32 + 1
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        rejectForeignLocalProjectedCoincidenceJoinF64F32(
            identityF64 = previousClaimF64.endVertexIdentityF64
                ?: throw IllegalStateException("path-f32-projection-collapse"),
            witnessF64 = witnessF64,
            pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        previousSideF64F32 = sideF64F32
    }
    val lowerAnchored = firstSideF64F32.claimF64.startVertexIdentityF64 == witnessF64.vertexIdentityF64
    val upperAnchored = previousSideF64F32.claimF64.endVertexIdentityF64 == witnessF64.vertexIdentityF64
    if (!lowerAnchored && !upperAnchored) throw IllegalStateException("path-f32-projection-collapse")
    val witnessVertexIndexI32 = vertexIndexByIdentityF64[witnessF64.vertexIdentityF64]
        ?: throw IllegalStateException("path-arrangement-inconsistent")
    val witnessPointF32 = verticesF64F32[witnessVertexIndexI32].representativePointF32
    val hasRepresentativeAnchor = orderedSidesF64F32.any { sideF64F32 ->
        directPointWitnessAnchorF64F32(sideF64F32, witnessF64, witnessPointF32)
    }
    if (!hasRepresentativeAnchor) throw IllegalStateException("path-f32-projection-collapse")
}

private fun compareProjectedClaimProposalSidesF64F32(
    firstF64F32: PathProjectedClaimProposalSideF64F32,
    secondF64F32: PathProjectedClaimProposalSideF64F32,
): Int {
    firstF64F32.claimF64.startParameterF64.compareTo(secondF64F32.claimF64.startParameterF64)
        .takeIf { it != 0 }?.let { return it }
    return firstF64F32.claimF64.endParameterF64.compareTo(secondF64F32.claimF64.endParameterF64)
}

private fun directPointWitnessAnchorF64F32(
    sideF64F32: PathProjectedClaimProposalSideF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    witnessPointF32: Point2F32,
): Boolean {
    val firstClaimF64 = sideF64F32.proposalF64F32.firstClaimF64
    val secondClaimF64 = sideF64F32.proposalF64F32.secondClaimF64
    val sharedAtStart =
        firstClaimF64.startVertexIdentityF64 == witnessF64.vertexIdentityF64 &&
            secondClaimF64.startVertexIdentityF64 == witnessF64.vertexIdentityF64
    val sharedAtEnd =
        firstClaimF64.endVertexIdentityF64 == witnessF64.vertexIdentityF64 &&
            secondClaimF64.endVertexIdentityF64 == witnessF64.vertexIdentityF64
    if (!sharedAtStart && !sharedAtEnd) return false
    val proposalF64F32 = sideF64F32.proposalF64F32
    return sameHybridPointF32(proposalF64F32.startPointF32, witnessPointF32) ||
        sameHybridPointF32(proposalF64F32.endPointF32, witnessPointF32)
}

private fun rejectForeignLocalProjectedCoincidenceJoinF64F32(
    identityF64: PathVertexIdentityF64,
    witnessF64: PathContactWitnessF64.PointF64,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    if (identityF64 == witnessF64.vertexIdentityF64) return
    if (identityF64.originalPointF32 != null) throw IllegalStateException("path-f32-projection-collapse")
    val pointWitnessesF64 = pointWitnessIndexF64F32.witnessesByIdentityF64[identityF64].orEmpty()
    val exactWitnessesF64 = witnessesByIdentityF64[identityF64].orEmpty()
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(pointWitnessesF64.size.toLong(), exactWitnessesF64.size.toLong()),
            2L,
        ),
        candidateWorkBudgetI32,
    )
    if (pointWitnessesF64.any { pointWitnessF64 -> pointWitnessF64.witnessIdI64 != witnessF64.witnessIdI64 }) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    if (exactWitnessesF64.any { exactWitnessF64 ->
            exactWitnessF64 !is PathContactWitnessF64.PointF64 || exactWitnessF64.witnessIdI64 != witnessF64.witnessIdI64
        }
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
}

/**
 * Canonicalizes the proposal order and validates one complete Point-witness transaction at a
 * time.  The returned list remains only a proposal list: callers may not assign coincidence IDs
 * or build aliases until this function and the deferred-contact pass both return.
 */
private fun validateAndOrderProjectedCoincidenceTransactionsF64F32(
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathProjectedCoincidenceProposalF64F32> {
    if (proposalsF64F32.isEmpty()) {
        // Preserve the two deterministic empty-pass debits that this transaction replaces:
        // local-chain validation (2) and source-span claim validation (1).  Without them a
        // no-proposal operation would gain backend-independent budget credit merely because the
        // implementation now has an explicit transaction object.
        preflightHybridLinearF64F32(3L, candidateWorkBudgetI32)
        return emptyList()
    }
    val proposalCountI64 = proposalsF64F32.size.toLong()
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalCountI64, 14L),
            3L,
        ),
        candidateWorkBudgetI32,
    )
    val orderedProposalsF64F32 = sortedHybridF64F32(
        proposalsF64F32,
        candidateWorkBudgetI32,
        ::compareProjectedCoincidenceTransactionProposalsF64F32,
    )
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(orderedProposalsF64F32.size.toLong(), 4L),
        candidateWorkBudgetI32,
    )
    val canonicalProposalsF64F32 = canonicalProjectedProposalSemanticGroupsF64F32(orderedProposalsF64F32)

    val witnessByIdI64 = mutableMapOf<Long, PathContactWitnessF64.PointF64>()
    canonicalProposalsF64F32.forEach { proposalF64F32 ->
        val previousWitnessF64 = witnessByIdI64.put(
            proposalF64F32.pointWitnessF64.witnessIdI64,
            proposalF64F32.pointWitnessF64,
        )
        if (previousWitnessF64 != null && previousWitnessF64 != proposalF64F32.pointWitnessF64) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }

    // A canonical source registry owns one Point witness for an exact F64 location.  A second
    // witness at that same semantic key would make a sort tie an authority decision, so reject
    // rather than use a witness ID or candidate order as a hidden tie-break.
    val transactionsF64F32 = ArrayList<PathProjectedCoincidenceTransactionF64F32>(
        checkedPathCapacityI32(canonicalProposalsF64F32.size.toLong(), "path-candidate-limit"),
    )
    var transactionStartI32 = 0
    while (transactionStartI32 < canonicalProposalsF64F32.size) {
        val witnessF64 = canonicalProposalsF64F32[transactionStartI32].pointWitnessF64
        var transactionEndI32 = transactionStartI32 + 1
        while (
            transactionEndI32 < canonicalProposalsF64F32.size &&
                compareProjectedPointWitnessSemanticF64(
                    witnessF64,
                    canonicalProposalsF64F32[transactionEndI32].pointWitnessF64,
                ) == 0
        ) {
            if (canonicalProposalsF64F32[transactionEndI32].pointWitnessF64.witnessIdI64 != witnessF64.witnessIdI64) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            transactionEndI32 += 1
        }
        transactionsF64F32 += PathProjectedCoincidenceTransactionF64F32(
            pointWitnessF64 = witnessF64,
            proposalsF64F32 = canonicalProposalsF64F32.subList(transactionStartI32, transactionEndI32),
        )
        transactionStartI32 = transactionEndI32
    }

    transactionsF64F32.forEach { transactionF64F32 ->
        validateProjectedCoincidenceTransactionF64F32(
            transactionF64F32 = transactionF64F32,
            pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
            witnessesByIdentityF64 = witnessesByIdentityF64,
            verticesF64F32 = verticesF64F32,
            vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    }

    validateProjectedCoincidenceClaimsF64F32(canonicalProposalsF64F32, candidateWorkBudgetI32)
    return canonicalProposalsF64F32
}

/**
 * A source/operand label may navigate ownership later, but it may not resolve a geometric
 * comparator tie.  Exact duplicate proposals are indifferent and collapse to one transaction
 * member; any other equal-semantic group is rejected before IDs, aliases, or DCEL state exist.
 */
private fun canonicalProjectedProposalSemanticGroupsF64F32(
    orderedProposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
): List<PathProjectedCoincidenceProposalF64F32> {
    if (orderedProposalsF64F32.isEmpty()) return emptyList()
    val canonicalProposalsF64F32 = ArrayList<PathProjectedCoincidenceProposalF64F32>(orderedProposalsF64F32.size)
    var previousF64F32 = orderedProposalsF64F32.first()
    canonicalProposalsF64F32 += previousF64F32
    orderedProposalsF64F32.drop(1).forEach { proposalF64F32 ->
        if (compareProjectedCoincidenceTransactionProposalsF64F32(previousF64F32, proposalF64F32) == 0) {
            if (proposalF64F32 != previousF64F32) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            return@forEach
        }
        canonicalProposalsF64F32 += proposalF64F32
        previousF64F32 = proposalF64F32
    }
    return canonicalProposalsF64F32
}

/** Validates all pair relations carried by one n-way Point witness as one immutable unit. */
private fun validateProjectedCoincidenceTransactionF64F32(
    transactionF64F32: PathProjectedCoincidenceTransactionF64F32,
    pointWitnessIndexF64F32: PathPointWitnessIndexF64F32,
    witnessesByIdentityF64: Map<PathVertexIdentityF64, List<PathContactWitnessF64>>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val proposalCountI64 = transactionF64F32.proposalsF64F32.size.toLong()
    if (proposalCountI64 == 0L) throw IllegalStateException("path-arrangement-inconsistent")
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(checkedPathWorkMultiplyI64(proposalCountI64, 18L), 2L),
        candidateWorkBudgetI32,
    )
    transactionF64F32.proposalsF64F32.forEach { proposalF64F32 ->
        if (
            proposalF64F32.pointWitnessF64 != transactionF64F32.pointWitnessF64
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        validateProjectedClaimMaterializationF64F32(
            claimF64 = proposalF64F32.firstClaimF64,
            projectedSpanF64F32 = proposalF64F32.firstSpanF64,
            witnessF64 = transactionF64F32.pointWitnessF64,
            vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        )
        validateProjectedClaimMaterializationF64F32(
            claimF64 = proposalF64F32.secondClaimF64,
            projectedSpanF64F32 = proposalF64F32.secondSpanF64,
            witnessF64 = transactionF64F32.pointWitnessF64,
            vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        )
    }
    validateLocalProjectedCoincidenceChainsF64F32(
        proposalsF64F32 = transactionF64F32.proposalsF64F32,
        pointWitnessIndexF64F32 = pointWitnessIndexF64F32,
        witnessesByIdentityF64 = witnessesByIdentityF64,
        verticesF64F32 = verticesF64F32,
        vertexIndexByIdentityF64 = vertexIndexByIdentityF64,
        candidateWorkBudgetI32 = candidateWorkBudgetI32,
    )
}

/**
 * Validates an endpoint against the immutable materialization plan.  Endpoint-only claims are
 * true no-ops only when their exact identity is already present; a strict bound must carry the
 * projected structural identity derived from its witness/incidence/parameter, never an F32 point.
 */
private fun validateProjectedClaimMaterializationF64F32(
    claimF64: PathProjectedSpanClaimF64,
    projectedSpanF64F32: PathProjectedSourceSpanF64F32,
    witnessF64: PathContactWitnessF64.PointF64,
    vertexIndexByIdentityF64: Map<PathVertexIdentityF64, Int>,
) {
    val startIdentityF64 = claimF64.startVertexIdentityF64
        ?: throw IllegalStateException("path-f32-projection-collapse")
    val endIdentityF64 = claimF64.endVertexIdentityF64
        ?: throw IllegalStateException("path-f32-projection-collapse")
    val sourceSpanF64 = projectedSpanF64F32.sourceSpanF64
    val sourceSectionF64 = projectedSpanF64F32.sourceSectionF64
    if (
        claimF64.witnessIdI64 != witnessF64.witnessIdI64 ||
            claimF64.sourceSpanIdI64 != sourceSpanF64.sourceSpanIdI64 ||
            claimF64.sourceSectionIndexI32 != projectedSpanF64F32.sectionIndexI32 ||
            claimF64.inputEdgeIdI32 != sourceSectionF64.inputEdgeIdI32 ||
            claimF64.startParameterF64 >= claimF64.endParameterF64 ||
            claimF64.startEdgeParameterF64 !in 0.0..1.0 ||
            claimF64.endEdgeParameterF64 !in 0.0..1.0
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    if (
        sourceParameterAtEdgeCutF64(projectedSpanF64F32.projectedEdgeF64, claimF64.startEdgeParameterF64) !=
            claimF64.startParameterF64 ||
            sourceParameterAtEdgeCutF64(projectedSpanF64F32.projectedEdgeF64, claimF64.endEdgeParameterF64) !=
                claimF64.endParameterF64 ||
            projectedClaimEndpointIdentityF64F32(
                claimF64 = claimF64,
                projectedSpanF64F32 = projectedSpanF64F32,
                witnessF64 = witnessF64,
                atStart = true,
            ) != startIdentityF64 ||
            projectedClaimEndpointIdentityF64F32(
                claimF64 = claimF64,
                projectedSpanF64F32 = projectedSpanF64F32,
                witnessF64 = witnessF64,
                atStart = false,
            ) != endIdentityF64 ||
            (
                (claimF64.startEdgeParameterF64 == 0.0 || claimF64.startEdgeParameterF64 == 1.0) &&
                    startIdentityF64 !in vertexIndexByIdentityF64
                ) ||
            (
                (claimF64.endEdgeParameterF64 == 0.0 || claimF64.endEdgeParameterF64 == 1.0) &&
                    endIdentityF64 !in vertexIndexByIdentityF64
                )
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
}

/**
 * Validates the complete claim multiset before [PathProjectedCoincidenceF32] receives an ID or
 * [PathAliasGroupF32] can merge any vertices.  The sweep is source-span-local, so a long run of
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
                if (activeF64.endVertexIdentityF64 != claimF64.startVertexIdentityF64) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                if (claimF64.endParameterF64 >= activeF64.endParameterF64) activeClaimF64 = claimF64
            }
            else -> activeClaimF64 = claimF64
        }
    }
}

private data class PathExactEndpointIdentityPairF64F32(
    val firstIdentityF64: PathVertexIdentityF64,
    val secondIdentityF64: PathVertexIdentityF64,
)

private data class PathValidatedCoincidenceEndpointRelayF64F32(
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val firstSpanF64: PathProjectedSourceSpanF64F32,
    val firstClaimF64: PathProjectedSpanClaimF64,
    val firstAtStart: Boolean,
    val secondSpanF64: PathProjectedSourceSpanF64F32,
    val secondClaimF64: PathProjectedSpanClaimF64,
    val secondAtStart: Boolean,
)

/**
 * An endpoint observation is harmless only when it is the next source continuation of both
 * branches already joined by one validated local Point-witness rail.  This is intentionally an
 * identity-and-parameter proof: neither the projected coordinate nor a broad-phase bucket can
 * grant this exception, and the observation publishes no additional alias or claim.
 */
private fun validateDeferredEndpointContactsF64F32(
    deferredEndpointContactsF64F32: List<PathDeferredProjectedEndpointContactF64F32>,
    proposalsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalsF64F32.size.toLong(), 12L),
            checkedPathWorkMultiplyI64(deferredEndpointContactsF64F32.size.toLong(), 3L),
        ),
        candidateWorkBudgetI32,
    )
    val relaysByEndpointPairF64F32 = mutableMapOf<
        PathExactEndpointIdentityPairF64F32,
        MutableList<PathValidatedCoincidenceEndpointRelayF64F32>,
        >()
    proposalsF64F32.forEach { proposalF64F32 ->
        addValidatedCoincidenceEndpointRelayF64F32(
            relaysByEndpointPairF64F32 = relaysByEndpointPairF64F32,
            proposalF64F32 = proposalF64F32,
            firstAtStart = true,
            secondAtStart = true,
        )
        addValidatedCoincidenceEndpointRelayF64F32(
            relaysByEndpointPairF64F32 = relaysByEndpointPairF64F32,
            proposalF64F32 = proposalF64F32,
            firstAtStart = false,
            secondAtStart = false,
        )
    }
    deferredEndpointContactsF64F32.forEach { deferredF64F32 ->
        val firstIdentityF64 = projectedSectionEndpointIdentityF64F32(
            deferredF64F32.firstSpanF64,
            deferredF64F32.firstParameterF64,
        ) ?: throw IllegalStateException("path-f32-projection-collapse")
        val secondIdentityF64 = projectedSectionEndpointIdentityF64F32(
            deferredF64F32.secondSpanF64,
            deferredF64F32.secondParameterF64,
        ) ?: throw IllegalStateException("path-f32-projection-collapse")
        val relaysF64F32 = relaysByEndpointPairF64F32[
            PathExactEndpointIdentityPairF64F32(firstIdentityF64, secondIdentityF64)
        ].orEmpty()
        preflightHybridLinearF64F32(
            checkedPathWorkAddI64(relaysF64F32.size.toLong(), 1L),
            candidateWorkBudgetI32,
        )
        var hasAdjacentRelay = false
        relaysF64F32.forEach { relayF64F32 ->
            if (
                isSourceAdjacentToClaimEndpointF64F32(
                    deferredSpanF64 = deferredF64F32.firstSpanF64,
                    deferredParameterF64 = deferredF64F32.firstParameterF64,
                    claimedSpanF64 = relayF64F32.firstSpanF64,
                    claimF64 = relayF64F32.firstClaimF64,
                    claimAtStart = relayF64F32.firstAtStart,
                ) && isSourceAdjacentToClaimEndpointF64F32(
                    deferredSpanF64 = deferredF64F32.secondSpanF64,
                    deferredParameterF64 = deferredF64F32.secondParameterF64,
                    claimedSpanF64 = relayF64F32.secondSpanF64,
                    claimF64 = relayF64F32.secondClaimF64,
                    claimAtStart = relayF64F32.secondAtStart,
                )
            ) {
                hasAdjacentRelay = true
            }
        }
        if (
            !hasAdjacentRelay &&
                !(proposalsF64F32.isEmpty() && deferredF64F32.hasCompleteExactOppositeComponentF64)
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
}

private fun addValidatedCoincidenceEndpointRelayF64F32(
    relaysByEndpointPairF64F32: MutableMap<
        PathExactEndpointIdentityPairF64F32,
        MutableList<PathValidatedCoincidenceEndpointRelayF64F32>,
        >,
    proposalF64F32: PathProjectedCoincidenceProposalF64F32,
    firstAtStart: Boolean,
    secondAtStart: Boolean,
) {
    val firstIdentityF64 = if (firstAtStart) {
        proposalF64F32.firstClaimF64.startVertexIdentityF64
    } else {
        proposalF64F32.firstClaimF64.endVertexIdentityF64
    } ?: throw IllegalStateException("path-f32-projection-collapse")
    val secondIdentityF64 = if (secondAtStart) {
        proposalF64F32.secondClaimF64.startVertexIdentityF64
    } else {
        proposalF64F32.secondClaimF64.endVertexIdentityF64
    } ?: throw IllegalStateException("path-f32-projection-collapse")
    val relayF64F32 = PathValidatedCoincidenceEndpointRelayF64F32(
        pointWitnessF64 = proposalF64F32.pointWitnessF64,
        firstSpanF64 = proposalF64F32.firstSpanF64,
        firstClaimF64 = proposalF64F32.firstClaimF64,
        firstAtStart = firstAtStart,
        secondSpanF64 = proposalF64F32.secondSpanF64,
        secondClaimF64 = proposalF64F32.secondClaimF64,
        secondAtStart = secondAtStart,
    )
    relaysByEndpointPairF64F32.getOrPut(
        PathExactEndpointIdentityPairF64F32(firstIdentityF64, secondIdentityF64),
    ) { mutableListOf() } += relayF64F32
    relaysByEndpointPairF64F32.getOrPut(
        PathExactEndpointIdentityPairF64F32(secondIdentityF64, firstIdentityF64),
    ) { mutableListOf() } += PathValidatedCoincidenceEndpointRelayF64F32(
        pointWitnessF64 = proposalF64F32.pointWitnessF64,
        firstSpanF64 = proposalF64F32.secondSpanF64,
        firstClaimF64 = proposalF64F32.secondClaimF64,
        firstAtStart = secondAtStart,
        secondSpanF64 = proposalF64F32.firstSpanF64,
        secondClaimF64 = proposalF64F32.firstClaimF64,
        secondAtStart = firstAtStart,
    )
}

private fun isSourceAdjacentToClaimEndpointF64F32(
    deferredSpanF64: PathProjectedSourceSpanF64F32,
    deferredParameterF64: Double,
    claimedSpanF64: PathProjectedSourceSpanF64F32,
    claimF64: PathProjectedSpanClaimF64,
    claimAtStart: Boolean,
): Boolean {
    val deferredIdentityF64 = projectedSectionEndpointIdentityF64F32(
        deferredSpanF64,
        deferredParameterF64,
    ) ?: return false
    val claimIdentityF64 = if (claimAtStart) claimF64.startVertexIdentityF64 else claimF64.endVertexIdentityF64
        ?: return false
    if (deferredIdentityF64 != claimIdentityF64) return false
    if (
        deferredSpanF64.sourceSpanF64.operand != claimedSpanF64.sourceSpanF64.operand ||
            deferredSpanF64.sourceSpanF64.contourIndexI32 != claimedSpanF64.sourceSpanF64.contourIndexI32
    ) {
        return false
    }
    if (
        deferredSpanF64.sourceSpanF64.sourceSpanIdI64 == claimedSpanF64.sourceSpanF64.sourceSpanIdI64 &&
            kotlin.math.abs(deferredSpanF64.sectionIndexI32 - claimedSpanF64.sectionIndexI32) == 1
    ) {
        return true
    }
    val deferredSourceParameterF64 = sourceParameterAtEdgeCutF64(
        deferredSpanF64.projectedEdgeF64,
        deferredParameterF64,
    )
    val claimedSourceParameterF64 = if (claimAtStart) claimF64.startParameterF64 else claimF64.endParameterF64
    val deferredSegmentIndexI32 = deferredSpanF64.sourceSpanF64.startLocationF64.sourceSegmentIndexI32
    val claimedSegmentIndexI32 = claimedSpanF64.sourceSpanF64.startLocationF64.sourceSegmentIndexI32
    if (deferredSegmentIndexI32 == claimedSegmentIndexI32) {
        return deferredSourceParameterF64 == claimedSourceParameterF64
    }
    return (
        claimedSourceParameterF64 == 1.0 && deferredSourceParameterF64 == 0.0 &&
            deferredSegmentIndexI32 == claimedSegmentIndexI32 + 1
        ) || (
        deferredSourceParameterF64 == 1.0 && claimedSourceParameterF64 == 0.0 &&
            claimedSegmentIndexI32 == deferredSegmentIndexI32 + 1
        )
}

private fun sameProjectedClaimCarrierF64F32(
    firstF64: PathProjectedSpanClaimF64,
    secondF64: PathProjectedSpanClaimF64,
): Boolean = firstF64.sourceSpanIdI64 == secondF64.sourceSpanIdI64

private fun compareProjectedClaimsF64F32(
    firstF64: PathProjectedSpanClaimF64,
    secondF64: PathProjectedSpanClaimF64,
): Int {
    firstF64.sourceSpanIdI64.compareTo(secondF64.sourceSpanIdI64).takeIf { it != 0 }?.let { return it }
    firstF64.startParameterF64.compareTo(secondF64.startParameterF64).takeIf { it != 0 }?.let { return it }
    firstF64.endParameterF64.compareTo(secondF64.endParameterF64).takeIf { it != 0 }?.let { return it }
    firstF64.sourceSectionIndexI32.compareTo(secondF64.sourceSectionIndexI32).takeIf { it != 0 }?.let { return it }
    firstF64.startEdgeParameterF64.compareTo(secondF64.startEdgeParameterF64).takeIf { it != 0 }?.let { return it }
    return firstF64.endEdgeParameterF64.compareTo(secondF64.endEdgeParameterF64)
}

/**
 * Proposal ordering is semantic: an exact F64 witness first, then the canonical source-span
 * provenance of each of its two sides, then the exact source interval.  IDs only remain in
 * ownership validation below; they never decide an otherwise geometric proposal order.
 */
private fun compareProjectedCoincidenceTransactionProposalsF64F32(
    firstF64F32: PathProjectedCoincidenceProposalF64F32,
    secondF64F32: PathProjectedCoincidenceProposalF64F32,
): Int {
    compareProjectedPointWitnessSemanticF64(firstF64F32.pointWitnessF64, secondF64F32.pointWitnessF64)
        .takeIf { it != 0 }?.let { return it }
    val firstOrderI32 = compareProjectedProposalSidesSemanticF64F32(
        firstF64F32.firstSpanF64,
        firstF64F32.firstClaimF64,
        firstF64F32.secondSpanF64,
        firstF64F32.secondClaimF64,
    )
    val secondOrderI32 = compareProjectedProposalSidesSemanticF64F32(
        secondF64F32.firstSpanF64,
        secondF64F32.firstClaimF64,
        secondF64F32.secondSpanF64,
        secondF64F32.secondClaimF64,
    )
    // A side tie is not a whole-transaction tie.  Keep comparing the ordered pair so the sort
    // returns zero only for a fully semantically equivalent relation; the group validator then
    // decides whether distinct provenance is an exact duplicate or must reject.
    val firstPrimarySpanF64F32 = if (firstOrderI32 <= 0) firstF64F32.firstSpanF64 else firstF64F32.secondSpanF64
    val firstPrimaryClaimF64 = if (firstOrderI32 <= 0) firstF64F32.firstClaimF64 else firstF64F32.secondClaimF64
    val firstSecondarySpanF64F32 = if (firstOrderI32 <= 0) firstF64F32.secondSpanF64 else firstF64F32.firstSpanF64
    val firstSecondaryClaimF64 = if (firstOrderI32 <= 0) firstF64F32.secondClaimF64 else firstF64F32.firstClaimF64
    val secondPrimarySpanF64F32 = if (secondOrderI32 <= 0) secondF64F32.firstSpanF64 else secondF64F32.secondSpanF64
    val secondPrimaryClaimF64 = if (secondOrderI32 <= 0) secondF64F32.firstClaimF64 else secondF64F32.secondClaimF64
    val secondSecondarySpanF64F32 = if (secondOrderI32 <= 0) secondF64F32.secondSpanF64 else secondF64F32.firstSpanF64
    val secondSecondaryClaimF64 = if (secondOrderI32 <= 0) secondF64F32.secondClaimF64 else secondF64F32.firstClaimF64
    compareProjectedProposalSidesSemanticF64F32(
        firstPrimarySpanF64F32,
        firstPrimaryClaimF64,
        secondPrimarySpanF64F32,
        secondPrimaryClaimF64,
    )
        .takeIf { it != 0 }?.let { return it }
    return compareProjectedProposalSidesSemanticF64F32(
        firstSecondarySpanF64F32,
        firstSecondaryClaimF64,
        secondSecondarySpanF64F32,
        secondSecondaryClaimF64,
    )
}

private fun compareProjectedProposalSidesSemanticF64F32(
    firstSpanF64F32: PathProjectedSourceSpanF64F32,
    firstClaimF64: PathProjectedSpanClaimF64,
    secondSpanF64F32: PathProjectedSourceSpanF64F32,
    secondClaimF64: PathProjectedSpanClaimF64,
): Int {
    compareProjectedSourceSpansSemanticF64F32(
        firstSpanF64F32.sourceSpanF64,
        secondSpanF64F32.sourceSpanF64,
    ).takeIf { it != 0 }?.let { return it }
    firstClaimF64.startParameterF64.compareTo(secondClaimF64.startParameterF64)
        .takeIf { it != 0 }?.let { return it }
    return firstClaimF64.endParameterF64.compareTo(secondClaimF64.endParameterF64)
}

private fun compareProjectedPointWitnessSemanticF64(
    firstF64: PathContactWitnessF64.PointF64,
    secondF64: PathContactWitnessF64.PointF64,
): Int = compareHybridPointsF64(firstF64.pointF64, secondF64.pointF64)

private fun compareProjectedSourceSpansSemanticF64F32(
    firstF64: PathSourceSpanF64,
    secondF64: PathSourceSpanF64,
): Int = compareHybridSourceSpansF64(firstF64, secondF64)

private fun buildHybridAliasGroupsF32(
    verticesF64F32: List<PathHybridVertexF64F32>,
    projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    projectedCutAliasGroupsF64F32: List<PathProjectedCutAliasGroupF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathAliasGroupF32> {
    // A claim already carries the only exact endpoint identities permitted to become aliases.
    // Do not rediscover vertices by a rounded coordinate or by a source-span membership scan:
    // that would give one point witness authority over remote sections with the same F32 image.
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(verticesF64F32.size.toLong(), 2L),
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(projectedCoincidencesF32.size.toLong(), 12L),
                checkedPathWorkMultiplyI64(projectedCutAliasGroupsF64F32.size.toLong(), 4L),
            ),
        ),
        candidateWorkBudgetI32,
    )
    var projectedCutAliasIdentityCountI64 = 0L
    projectedCutAliasGroupsF64F32.forEach { aliasGroupF64F32 ->
        projectedCutAliasIdentityCountI64 = checkedPathWorkAddI64(
            projectedCutAliasIdentityCountI64,
            aliasGroupF64F32.vertexIdentitiesF64.size.toLong(),
        )
    }
    preflightHybridLinearF64F32(
        checkedPathWorkMultiplyI64(projectedCutAliasIdentityCountI64, 5L),
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
    // Count the already-built direct alias groups in a separately debited pass; `sumOf` would
    // inspect every set before the shared ledger had admitted that scan.
    preflightHybridLinearF64F32(groupedIdentitiesF64.size.toLong(), candidateWorkBudgetI32)
    var identityCountI64 = 0L
    groupedIdentitiesF64.values.forEach { identitiesF64 ->
        identityCountI64 = checkedPathWorkAddI64(identityCountI64, identitiesF64.size.toLong())
    }
    preflightHybridLinearF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(identityCountI64, 3L),
            checkedPathWorkMultiplyI64(groupedIdentitiesF64.size.toLong(), 2L),
        ),
        candidateWorkBudgetI32,
    )
    val groupCountI64 = checkedPathWorkAddI64(
        groupedIdentitiesF64.size.toLong(),
        projectedCutAliasGroupsF64F32.size.toLong(),
    )
    preflightHybridLinearF64F32(groupCountI64, candidateWorkBudgetI32)
    val groupsF32 = ArrayList<PathAliasGroupF32>(
        checkedPathCapacityI32(groupCountI64, "path-candidate-limit"),
    )
    groupedIdentitiesF64.forEach { (keyF64F32, identitiesF64) ->
        val orderedIdentitiesF64 = sortedHybridF64F32(
            identitiesF64.toList(),
            candidateWorkBudgetI32,
            ::comparePathVertexIdentitiesStructuralF64,
        )
        val orderedVerticesF64F32 = orderedIdentitiesF64.map { identityF64 ->
            vertexByIdentityF64[identityF64] ?: throw IllegalStateException("path-arrangement-inconsistent")
        }
        groupsF32 += PathAliasGroupF32(
            // The key is entirely structural.  F32 selects only the already validated
            // embedding carried by these exact endpoint identities.
            representativePointF32 = orderedVerticesF64F32.firstOrNull()?.representativePointF32
                ?: throw IllegalStateException("path-arrangement-inconsistent"),
            vertexIdentitiesF64 = orderedIdentitiesF64,
            contactWitnessF64 = keyF64F32.witnessF64,
        )
    }
    projectedCutAliasGroupsF64F32.forEach { aliasGroupF64F32 ->
        if (aliasGroupF64F32.vertexIdentitiesF64.size < 2) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        aliasGroupF64F32.vertexIdentitiesF64.forEach { identityF64 ->
            val vertexF64F32 = vertexByIdentityF64[identityF64]
                ?: throw IllegalStateException("path-arrangement-inconsistent")
            if (!sameHybridPointF32(vertexF64F32.representativePointF32, aliasGroupF64F32.representativePointF32)) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
        groupsF32 += PathAliasGroupF32(
            // The event group is structural (witness + exact identities).  The F32 value has
            // merely verified that these already-proved incidences share one renderable point.
            representativePointF32 = aliasGroupF64F32.representativePointF32,
            vertexIdentitiesF64 = aliasGroupF64F32.vertexIdentitiesF64,
            contactWitnessF64 = aliasGroupF64F32.witnessF64,
        )
    }
    return sortedHybridF64F32(groupsF32, candidateWorkBudgetI32, ::compareAliasGroupsF32)
}

private data class PathDirectAliasKeyF64F32(
    val witnessF64: PathContactWitnessF64.PointF64,
    val firstIdentityF64: PathVertexIdentityF64,
    val secondIdentityF64: PathVertexIdentityF64,
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
    val orderedIdentitiesF64 = if (comparePathVertexIdentitiesStructuralF64(firstIdentity, secondIdentity) <= 0) {
        firstIdentity to secondIdentity
    } else {
        secondIdentity to firstIdentity
    }
    groupedIdentitiesF64.getOrPut(
        PathDirectAliasKeyF64F32(
            witnessF64 = witnessF64,
            firstIdentityF64 = orderedIdentitiesF64.first,
            secondIdentityF64 = orderedIdentitiesF64.second,
        ),
    ) {
        linkedSetOf()
    }.apply {
        add(firstIdentity)
        add(secondIdentity)
    }
}

private fun comparePathVertexIdentitiesStructuralF64(
    firstF64: PathVertexIdentityF64,
    secondF64: PathVertexIdentityF64,
): Int {
    if (firstF64 == secondF64) return 0
    firstF64.namespaceI32.compareTo(secondF64.namespaceI32).takeIf { it != 0 }?.let { return it }
    firstF64.identityScopeI64.compareTo(secondF64.identityScopeI64).takeIf { it != 0 }?.let { return it }
    firstF64.incidentEdgeIds.size.compareTo(secondF64.incidentEdgeIds.size).takeIf { it != 0 }?.let { return it }
    firstF64.incidentEdgeIds.indices.forEach { indexI32 ->
        val firstEdgeIdI32 = firstF64.incidentEdgeIds[indexI32]
        val secondEdgeIdI32 = secondF64.incidentEdgeIds[indexI32]
        firstEdgeIdI32.compareTo(secondEdgeIdI32).takeIf { it != 0 }?.let { return it }
        val firstParameterF64 = firstF64.parameterByEdgeId[firstEdgeIdI32]
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val secondParameterF64 = secondF64.parameterByEdgeId[secondEdgeIdI32]
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        firstParameterF64.compareTo(secondParameterF64).takeIf { it != 0 }?.let { return it }
    }
    val firstPointF32 = firstF64.originalPointF32
    val secondPointF32 = secondF64.originalPointF32
    when {
        firstPointF32 == null && secondPointF32 != null -> return -1
        firstPointF32 != null && secondPointF32 == null -> return 1
        firstPointF32 != null && secondPointF32 != null -> {
            firstPointF32.x.toRawBits().compareTo(secondPointF32.x.toRawBits()).takeIf { it != 0 }?.let { return it }
            firstPointF32.y.toRawBits().compareTo(secondPointF32.y.toRawBits()).takeIf { it != 0 }?.let { return it }
        }
    }
    // Two non-equal identities with the same structural fields would make an ordering tie an
    // authority decision.  Reject rather than smuggle a source ID into geometric ordering.
    throw IllegalStateException("path-f32-projection-collapse")
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

private fun compareAliasGroupsF32(firstF32: PathAliasGroupF32, secondF32: PathAliasGroupF32): Int {
    compareHybridWitnessesF64(firstF32.contactWitnessF64, secondF32.contactWitnessF64)
        .takeIf { it != 0 }?.let { return it }
    firstF32.vertexIdentitiesF64.size.compareTo(secondF32.vertexIdentitiesF64.size)
        .takeIf { it != 0 }?.let { return it }
    firstF32.vertexIdentitiesF64.indices.forEach { indexI32 ->
        comparePathVertexIdentitiesStructuralF64(
            firstF32.vertexIdentitiesF64[indexI32],
            secondF32.vertexIdentitiesF64[indexI32],
        ).takeIf { it != 0 }?.let { return it }
    }
    return 0
}

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

private fun projectedTransactionValidationUpperBoundI64F32(
    proposalCountI32: Int,
    sourceSpanCountI32: Int,
    witnessCountI32: Int,
): Long {
    val proposalCountI64 = proposalCountI32.toLong()
    val sourceSpanCountI64 = sourceSpanCountI32.toLong()
    val witnessCountI64 = witnessCountI32.toLong()
    val sideCountI64 = checkedPathWorkMultiplyI64(proposalCountI64, 2L)
    return checkedPathWorkAddI64(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalCountI64, 128L),
            checkedPathWorkMultiplyI64(sideCountI64, sourceSpanCountI64),
        ),
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(
                checkedPathWorkMultiplyI64(proposalCountI64, 4L),
                witnessCountI64,
            ),
            checkedPathWorkAddI64(
                deterministicSortCostI64F32(proposalCountI32),
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(
                        deterministicSortCostI64F32(
                            checkedPathCapacityI32(sideCountI64, "path-candidate-limit"),
                        ),
                        2L,
                    ),
                    4L,
                ),
            ),
        ),
    )
}

/** Counts carrier storage in a separately charged pass before its exact-capacity allocation. */
private fun countHybridCarrierSectionsF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Long {
    preflightHybridLinearF64F32(sourceSpansF64.size.toLong(), candidateWorkBudgetI32)
    var countI64 = 0L
    sourceSpansF64.forEach { sourceSpanF64 ->
        countI64 = checkedPathWorkAddI64(countI64, sourceSpanF64.flattenedSectionsF64.size.toLong())
    }
    return countI64
}
