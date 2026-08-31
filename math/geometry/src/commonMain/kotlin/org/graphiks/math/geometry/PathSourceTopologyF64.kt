package org.graphiks.math.geometry

internal data class PathSourceLocationF64(
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
    val originalPointF32: Point2F32?,
    val vertexIdentityF64: PathVertexIdentityF64?,
)

// A section is the only bridge back to the pre-arrangement source. The input-edge ID and both
// endpoint identities are registry keys; later code must never recreate them from coordinates.
internal data class PathFlattenedSectionF64(
    val inputEdgeIdI32: Int,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val startParameterF64: Double,
    val endParameterF64: Double,
    val startIdentityF64: PathVertexIdentityF64,
    val endIdentityF64: PathVertexIdentityF64,
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
    val witnessIdI64: Long

    data class PointF64(
        override val witnessIdI64: Long,
        val vertexIdentityF64: PathVertexIdentityF64,
        val pointF64: Point2F64,
        val incidentSourceSpanIdsI64: List<Long>,
    ) : PathContactWitnessF64

    data class OverlapF64(
        override val witnessIdI64: Long,
        val startVertexIdentityF64: PathVertexIdentityF64,
        val endVertexIdentityF64: PathVertexIdentityF64,
        val startPointF64: Point2F64,
        val endPointF64: Point2F64,
        val firstSourceSpanIdsI64: List<Long>,
        val secondSourceSpanIdsI64: List<Long>,
        val firstStartParameterF64: Double,
        val firstEndParameterF64: Double,
        val secondStartParameterF64: Double,
        val secondEndParameterF64: Double,
    ) : PathContactWitnessF64
}

// Temporary Task-1 bridge. It follows a source section to a legacy boundary half-edge and is
// intentionally deleted with the legacy arrangement in Task 4.
internal data class PathLegacySectionProvenanceF64(
    val sourceSpanIdI64: Long,
    val sectionIndexI32: Int,
    val sourceSectionF64: PathFlattenedSectionF64,
    val sourceSpanStartLocationF64: PathSourceLocationF64,
    val sourceSpanEndLocationF64: PathSourceLocationF64,
    val startLocationF64: PathSourceLocationF64,
    val endLocationF64: PathSourceLocationF64,
    val contactWitnessesF64: List<PathContactWitnessF64>,
)

internal data class PathSourceTopologyF64(
    val sourceSpansF64: List<PathSourceSpanF64>,
    val contactWitnessesF64: List<PathContactWitnessF64>,
)

// This index is built once from the authoritative spans and sections. It deliberately keys only
// registry identities, input-edge IDs, and source parameters; no projected or reconstructed
// coordinate ever participates in locating a witness.
private data class PathSourceSectionReferenceF64(
    val sourceSpanF64: PathSourceSpanF64,
    val sectionF64: PathFlattenedSectionF64,
)

private data class PathSourceTopologyIndexF64(
    val sectionsByInputEdgeIdI32: Map<Int, List<PathSourceSectionReferenceF64>>,
    val spanIdsByEndpointIdentityF64: Map<PathVertexIdentityF64, List<Long>>,
)

private data class PathLegacySectionProvenanceIndexF64(
    val contactWitnessesBySourceSpanIdI64: Map<Long, List<PathContactWitnessF64>>,
)

private data class PathUnidentifiedSourceSpanF64(
    val operand: PathOperand,
    val contourIndexI32: Int,
    val startLocationF64: PathSourceLocationF64,
    val endLocationF64: PathSourceLocationF64,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val flattenedSectionsF64: List<PathFlattenedSectionF64>,
    val windingDeltaI32: Int,
)

private sealed interface PathUnidentifiedContactWitnessF64 {
    data class PointF64(
        val vertexIdentityF64: PathVertexIdentityF64,
        val pointF64: Point2F64,
        val incidentSourceSpanIdsI64: List<Long>,
    ) : PathUnidentifiedContactWitnessF64

    data class OverlapF64(
        val startVertexIdentityF64: PathVertexIdentityF64,
        val endVertexIdentityF64: PathVertexIdentityF64,
        val startPointF64: Point2F64,
        val endPointF64: Point2F64,
        val firstSourceSpanIdsI64: List<Long>,
        val secondSourceSpanIdsI64: List<Long>,
        val firstStartParameterF64: Double,
        val firstEndParameterF64: Double,
        val secondStartParameterF64: Double,
        val secondEndParameterF64: Double,
    ) : PathUnidentifiedContactWitnessF64
}

internal fun splitPathSourceTopologyF64(
    edgesF64: List<PathInputEdgeF64>,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceTopologyF64 {
    val splitTopologyF64 = splitPathTopologyF64(edgesF64, limitsI32, candidateWorkBudgetI32)
    val spansF64 = assignSourceSpanIdsF64(
        mergeSourceSpansF64(splitTopologyF64.splitEdgesF64, candidateWorkBudgetI32),
        candidateWorkBudgetI32,
    )
    val sourceTopologyIndexF64 = buildPathSourceTopologyIndexF64(spansF64, candidateWorkBudgetI32)
    val contactsF64 = assignContactWitnessIdsF64(
        buildContactWitnessesF64(splitTopologyF64, sourceTopologyIndexF64, candidateWorkBudgetI32),
        candidateWorkBudgetI32,
    )
    return PathSourceTopologyF64(spansF64, contactsF64)
}

private fun buildPathSourceTopologyIndexF64(
    spansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceTopologyIndexF64 {
    val sectionsByInputEdgeIdI32 = mutableMapOf<Int, MutableList<PathSourceSectionReferenceF64>>()
    val spanIdsByEndpointIdentityF64 = mutableMapOf<PathVertexIdentityF64, MutableSet<Long>>()
    spansF64.forEach { spanF64 ->
        listOfNotNull(
            spanF64.startLocationF64.vertexIdentityF64,
            spanF64.endLocationF64.vertexIdentityF64,
        ).forEach { identityF64 ->
            candidateWorkBudgetI32.consume()
            spanIdsByEndpointIdentityF64.getOrPut(identityF64) { linkedSetOf() } += spanF64.sourceSpanIdI64
        }
        spanF64.flattenedSectionsF64.forEach { sectionF64 ->
            candidateWorkBudgetI32.consume()
            sectionsByInputEdgeIdI32.getOrPut(sectionF64.inputEdgeIdI32) { mutableListOf() } +=
                PathSourceSectionReferenceF64(spanF64, sectionF64)
        }
    }
    return PathSourceTopologyIndexF64(
        sectionsByInputEdgeIdI32 = sectionsByInputEdgeIdI32.mapValues { (_, referencesF64) ->
            referencesF64.sortedWith(
                Comparator { firstF64, secondF64 ->
                    compareSourceParametersF64(
                        firstF64.sectionF64.startParameterF64,
                        secondF64.sectionF64.startParameterF64,
                        candidateWorkBudgetI32,
                    )
                },
            )
        },
        spanIdsByEndpointIdentityF64 = spanIdsByEndpointIdentityF64.mapValues { (_, idsI64) -> idsI64.sorted() },
    )
}

private fun mergeSourceSpansF64(
    splitEdgesF64: List<PathSplitEdgeF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathUnidentifiedSourceSpanF64> {
    // Labels reconnect the declared source chain only. They are excluded from the later semantic
    // sort that assigns the I64 source-span identity.
    val groupedEdgesF64 = splitEdgesF64.sortedWith(
        Comparator { firstF64, secondF64 ->
            compareSourceSpanGroupingEdgesF64(firstF64, secondF64, candidateWorkBudgetI32)
        },
    )
    val spansF64 = mutableListOf<PathUnidentifiedSourceSpanF64>()
    var indexI32 = 0
    while (indexI32 < groupedEdgesF64.size) {
        candidateWorkBudgetI32.consume()
        val firstF64 = groupedEdgesF64[indexI32]
        candidateWorkBudgetI32.consume()
        val sectionsF64 = mutableListOf(firstF64.toPathFlattenedSectionF64())
        var lastF64 = firstF64
        indexI32 += 1
        while (indexI32 < groupedEdgesF64.size) {
            candidateWorkBudgetI32.consume()
            val nextF64 = groupedEdgesF64[indexI32]
            if (!canMergeSourceSectionsF64(firstF64, lastF64, nextF64, candidateWorkBudgetI32)) break
            candidateWorkBudgetI32.consume()
            sectionsF64 += nextF64.toPathFlattenedSectionF64()
            lastF64 = nextF64
            indexI32 += 1
        }
        candidateWorkBudgetI32.consume()
        spansF64 += PathUnidentifiedSourceSpanF64(
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
    return spansF64
}

private fun canMergeSourceSectionsF64(
    firstF64: PathSplitEdgeF64,
    lastF64: PathSplitEdgeF64,
    nextF64: PathSplitEdgeF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    candidateWorkBudgetI32.consume()
    if (nextF64.operand != firstF64.operand) return false
    candidateWorkBudgetI32.consume()
    if (nextF64.contourIndexI32 != firstF64.contourIndexI32) return false
    candidateWorkBudgetI32.consume()
    if (nextF64.sourceSegmentIndexI32 != firstF64.sourceSegmentIndexI32) return false
    candidateWorkBudgetI32.consume()
    if (!sameSourceParameterF64(nextF64.sourceStartParameterF64, lastF64.sourceEndParameterF64)) return false
    candidateWorkBudgetI32.consume()
    return !nextF64.startIsExactEventF64
}

private fun compareSourceSpanGroupingEdgesF64(
    firstF64: PathSplitEdgeF64,
    secondF64: PathSplitEdgeF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Int {
    candidateWorkBudgetI32.consume()
    firstF64.operand.ordinal.compareTo(secondF64.operand.ordinal).takeIf { it != 0 }?.let { return it }
    candidateWorkBudgetI32.consume()
    firstF64.contourIndexI32.compareTo(secondF64.contourIndexI32).takeIf { it != 0 }?.let { return it }
    candidateWorkBudgetI32.consume()
    firstF64.sourceSegmentIndexI32.compareTo(secondF64.sourceSegmentIndexI32).takeIf { it != 0 }?.let { return it }
    return compareSourceParametersF64(
        firstF64.sourceStartParameterF64,
        secondF64.sourceStartParameterF64,
        candidateWorkBudgetI32,
    )
}

private fun assignSourceSpanIdsF64(
    spansF64: List<PathUnidentifiedSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathSourceSpanF64> {
    val orderedF64 = spansF64.sortedWith(
        Comparator { firstF64, secondF64 ->
            compareSourceSpansSemanticallyF64(firstF64, secondF64, candidateWorkBudgetI32)
        },
    )
    var nextIdI64 = 0L
    var previousF64: PathUnidentifiedSourceSpanF64? = null
    return orderedF64.map { spanF64 ->
        val previous = previousF64
        if (previous != null) {
            candidateWorkBudgetI32.consume()
            if (compareSourceSpansSemanticallyF64(previous, spanF64, candidateWorkBudgetI32) != 0) nextIdI64 += 1L
        }
        previousF64 = spanF64
        PathSourceSpanF64(
            sourceSpanIdI64 = nextIdI64,
            operand = spanF64.operand,
            contourIndexI32 = spanF64.contourIndexI32,
            startLocationF64 = spanF64.startLocationF64,
            endLocationF64 = spanF64.endLocationF64,
            startPointF64 = spanF64.startPointF64,
            endPointF64 = spanF64.endPointF64,
            flattenedSectionsF64 = spanF64.flattenedSectionsF64,
            windingDeltaI32 = spanF64.windingDeltaI32,
        )
    }
}

private fun compareSourceSpansSemanticallyF64(
    firstF64: PathUnidentifiedSourceSpanF64,
    secondF64: PathUnidentifiedSourceSpanF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Int {
    comparePointsSemanticallyF64(firstF64.startPointF64, secondF64.startPointF64, candidateWorkBudgetI32)
        .takeIf { it != 0 }?.let { return it }
    comparePointsSemanticallyF64(firstF64.endPointF64, secondF64.endPointF64, candidateWorkBudgetI32)
        .takeIf { it != 0 }?.let { return it }
    candidateWorkBudgetI32.consume()
    firstF64.windingDeltaI32.compareTo(secondF64.windingDeltaI32).takeIf { it != 0 }?.let { return it }
    candidateWorkBudgetI32.consume()
    firstF64.flattenedSectionsF64.size.compareTo(secondF64.flattenedSectionsF64.size).takeIf { it != 0 }?.let { return it }
    firstF64.flattenedSectionsF64.indices.forEach { indexI32 ->
        val firstSectionF64 = firstF64.flattenedSectionsF64[indexI32]
        val secondSectionF64 = secondF64.flattenedSectionsF64[indexI32]
        comparePointsSemanticallyF64(firstSectionF64.startPointF64, secondSectionF64.startPointF64, candidateWorkBudgetI32)
            .takeIf { it != 0 }?.let { return it }
        comparePointsSemanticallyF64(firstSectionF64.endPointF64, secondSectionF64.endPointF64, candidateWorkBudgetI32)
            .takeIf { it != 0 }?.let { return it }
        compareSourceParametersF64(firstSectionF64.startParameterF64, secondSectionF64.startParameterF64, candidateWorkBudgetI32)
            .takeIf { it != 0 }?.let { return it }
        compareSourceParametersF64(firstSectionF64.endParameterF64, secondSectionF64.endParameterF64, candidateWorkBudgetI32)
            .takeIf { it != 0 }?.let { return it }
    }
    return 0
}

private fun buildContactWitnessesF64(
    splitTopologyF64: PathSplitTopologyF64,
    sourceTopologyIndexF64: PathSourceTopologyIndexF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathUnidentifiedContactWitnessF64> {
    val overlapsF64 = splitTopologyF64.overlapContactsF64.map { contactF64 ->
        val startIdentityF64 = sourceEndpointIdentityF64(
            sourceTopologyIndexF64,
            contactF64.firstInputEdgeIdI32,
            contactF64.firstStartParameterF64,
            candidateWorkBudgetI32,
        )
        val endIdentityF64 = sourceEndpointIdentityF64(
            sourceTopologyIndexF64,
            contactF64.firstInputEdgeIdI32,
            contactF64.firstEndParameterF64,
            candidateWorkBudgetI32,
        )
        val firstSourceSpanIdsI64 = traversedSourceSpanIdsF64(
            sourceTopologyIndexF64,
            contactF64.firstInputEdgeIdI32,
            contactF64.firstStartParameterF64,
            contactF64.firstEndParameterF64,
            candidateWorkBudgetI32,
        )
        val secondSourceSpanIdsI64 = traversedSourceSpanIdsF64(
            sourceTopologyIndexF64,
            contactF64.secondInputEdgeIdI32,
            contactF64.secondStartParameterF64,
            contactF64.secondEndParameterF64,
            candidateWorkBudgetI32,
        )
        if (firstSourceSpanIdsI64.isEmpty() || secondSourceSpanIdsI64.isEmpty()) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        PathUnidentifiedContactWitnessF64.OverlapF64(
            startVertexIdentityF64 = startIdentityF64,
            endVertexIdentityF64 = endIdentityF64,
            startPointF64 = contactF64.startPointF64,
            endPointF64 = contactF64.endPointF64,
            firstSourceSpanIdsI64 = firstSourceSpanIdsI64,
            secondSourceSpanIdsI64 = secondSourceSpanIdsI64,
            firstStartParameterF64 = contactF64.firstStartParameterF64,
            firstEndParameterF64 = contactF64.firstEndParameterF64,
            secondStartParameterF64 = contactF64.secondStartParameterF64,
            secondEndParameterF64 = contactF64.secondEndParameterF64,
        )
    }
    val pointsF64 = splitTopologyF64.pointContactsF64.map { contactF64 ->
        candidateWorkBudgetI32.consume()
        val incidentSpanIdsI64 = sourceTopologyIndexF64.spanIdsByEndpointIdentityF64
            .getValue(contactF64.vertexIdentityF64)
        if (incidentSpanIdsI64.isEmpty()) throw IllegalStateException("path-arrangement-inconsistent")
        PathUnidentifiedContactWitnessF64.PointF64(
            vertexIdentityF64 = contactF64.vertexIdentityF64,
            pointF64 = contactF64.pointF64,
            incidentSourceSpanIdsI64 = incidentSpanIdsI64,
        )
    }
    return pointsF64 + overlapsF64
}

private fun sourceEndpointIdentityF64(
    sourceTopologyIndexF64: PathSourceTopologyIndexF64,
    inputEdgeIdI32: Int,
    parameterF64: Double,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathVertexIdentityF64 {
    val sectionsF64 = sourceTopologyIndexF64.sectionsByInputEdgeIdI32.getValue(inputEdgeIdI32)
    sectionsF64.forEach { referenceF64 ->
        val sectionF64 = referenceF64.sectionF64
        candidateWorkBudgetI32.consume()
        if (sameSourceParameterF64(sectionF64.startParameterF64, parameterF64)) return sectionF64.startIdentityF64
        candidateWorkBudgetI32.consume()
        if (sameSourceParameterF64(sectionF64.endParameterF64, parameterF64)) return sectionF64.endIdentityF64
    }
    throw IllegalStateException("path-arrangement-inconsistent")
}

private fun traversedSourceSpanIdsF64(
    sourceTopologyIndexF64: PathSourceTopologyIndexF64,
    inputEdgeIdI32: Int,
    firstParameterF64: Double,
    secondParameterF64: Double,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<Long> {
    val minimumF64 = minOf(firstParameterF64, secondParameterF64)
    val maximumF64 = maxOf(firstParameterF64, secondParameterF64)
    val sectionsF64 = sourceTopologyIndexF64.sectionsByInputEdgeIdI32.getValue(inputEdgeIdI32)
    val traversedSpanIdsI64 = linkedSetOf<Long>()
    sectionsF64.forEach { referenceF64 ->
        val sectionF64 = referenceF64.sectionF64
        candidateWorkBudgetI32.consume()
        val sectionMinimumF64 = minOf(sectionF64.startParameterF64, sectionF64.endParameterF64)
        val sectionMaximumF64 = maxOf(sectionF64.startParameterF64, sectionF64.endParameterF64)
        if (sectionMinimumF64 <= maximumF64 && sectionMaximumF64 >= minimumF64) {
            candidateWorkBudgetI32.consume()
            traversedSpanIdsI64 += referenceF64.sourceSpanF64.sourceSpanIdI64
        }
    }
    return traversedSpanIdsI64.sorted()
}

private fun assignContactWitnessIdsF64(
    contactsF64: List<PathUnidentifiedContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathContactWitnessF64> {
    val orderedF64 = contactsF64.sortedWith(
        Comparator { firstF64, secondF64 ->
            compareContactWitnessesSemanticallyF64(firstF64, secondF64, candidateWorkBudgetI32)
        },
    )
    var nextIdI64 = 0L
    var previousF64: PathUnidentifiedContactWitnessF64? = null
    return orderedF64.map { contactF64 ->
        val previous = previousF64
        if (previous != null) {
            candidateWorkBudgetI32.consume()
            if (compareContactWitnessesSemanticallyF64(previous, contactF64, candidateWorkBudgetI32) != 0) nextIdI64 += 1L
        }
        previousF64 = contactF64
        when (contactF64) {
            is PathUnidentifiedContactWitnessF64.PointF64 -> PathContactWitnessF64.PointF64(
                witnessIdI64 = nextIdI64,
                vertexIdentityF64 = contactF64.vertexIdentityF64,
                pointF64 = contactF64.pointF64,
                incidentSourceSpanIdsI64 = contactF64.incidentSourceSpanIdsI64,
            )
            is PathUnidentifiedContactWitnessF64.OverlapF64 -> PathContactWitnessF64.OverlapF64(
                witnessIdI64 = nextIdI64,
                startVertexIdentityF64 = contactF64.startVertexIdentityF64,
                endVertexIdentityF64 = contactF64.endVertexIdentityF64,
                startPointF64 = contactF64.startPointF64,
                endPointF64 = contactF64.endPointF64,
                firstSourceSpanIdsI64 = contactF64.firstSourceSpanIdsI64,
                secondSourceSpanIdsI64 = contactF64.secondSourceSpanIdsI64,
                firstStartParameterF64 = contactF64.firstStartParameterF64,
                firstEndParameterF64 = contactF64.firstEndParameterF64,
                secondStartParameterF64 = contactF64.secondStartParameterF64,
                secondEndParameterF64 = contactF64.secondEndParameterF64,
            )
        }
    }
}

private fun buildPathLegacySectionProvenanceIndexF64(
    sourceSpansF64: List<PathSourceSpanF64>,
    contactsF64: List<PathContactWitnessF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathLegacySectionProvenanceIndexF64 {
    val contactsBySourceSpanIdI64 = linkedMapOf<Long, LinkedHashMap<Long, PathContactWitnessF64>>()
    sourceSpansF64.forEach { spanF64 ->
        candidateWorkBudgetI32.consume()
        contactsBySourceSpanIdI64[spanF64.sourceSpanIdI64] = linkedMapOf()
    }
    contactsF64.forEach { contactF64 ->
        candidateWorkBudgetI32.consume()
        fun addWitnessForSpans(sourceSpanIdsI64: List<Long>) {
            sourceSpanIdsI64.forEach { sourceSpanIdI64 ->
                candidateWorkBudgetI32.consume()
                val witnessesByIdI64 = contactsBySourceSpanIdI64.getValue(sourceSpanIdI64)
                if (!witnessesByIdI64.containsKey(contactF64.witnessIdI64)) {
                    candidateWorkBudgetI32.consume()
                    witnessesByIdI64[contactF64.witnessIdI64] = contactF64
                }
            }
        }
        when (contactF64) {
            is PathContactWitnessF64.PointF64 -> addWitnessForSpans(contactF64.incidentSourceSpanIdsI64)
            is PathContactWitnessF64.OverlapF64 -> {
                addWitnessForSpans(contactF64.firstSourceSpanIdsI64)
                addWitnessForSpans(contactF64.secondSourceSpanIdsI64)
            }
        }
    }
    val witnessesBySourceSpanIdI64 = linkedMapOf<Long, List<PathContactWitnessF64>>()
    contactsBySourceSpanIdI64.forEach { (sourceSpanIdI64, contactsByIdI64) ->
        candidateWorkBudgetI32.consume()
        val witnessesF64 = mutableListOf<PathContactWitnessF64>()
        contactsByIdI64.values.forEach { witnessF64 ->
            candidateWorkBudgetI32.consume()
            witnessesF64 += witnessF64
        }
        witnessesBySourceSpanIdI64[sourceSpanIdI64] = witnessesF64
    }
    return PathLegacySectionProvenanceIndexF64(witnessesBySourceSpanIdI64)
}

private fun compareContactWitnessesSemanticallyF64(
    firstF64: PathUnidentifiedContactWitnessF64,
    secondF64: PathUnidentifiedContactWitnessF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Int = when {
    firstF64 is PathUnidentifiedContactWitnessF64.PointF64 && secondF64 is PathUnidentifiedContactWitnessF64.PointF64 ->
        comparePointsSemanticallyF64(firstF64.pointF64, secondF64.pointF64, candidateWorkBudgetI32)
    firstF64 is PathUnidentifiedContactWitnessF64.PointF64 -> -1
    secondF64 is PathUnidentifiedContactWitnessF64.PointF64 -> 1
    firstF64 is PathUnidentifiedContactWitnessF64.OverlapF64 && secondF64 is PathUnidentifiedContactWitnessF64.OverlapF64 -> {
        comparePointsSemanticallyF64(firstF64.startPointF64, secondF64.startPointF64, candidateWorkBudgetI32)
            .takeIf { it != 0 } ?: comparePointsSemanticallyF64(
            firstF64.endPointF64,
            secondF64.endPointF64,
            candidateWorkBudgetI32,
        ).takeIf { it != 0 } ?: compareSourceParametersF64(
            firstF64.firstStartParameterF64,
            secondF64.firstStartParameterF64,
            candidateWorkBudgetI32,
        ).takeIf { it != 0 } ?: compareSourceParametersF64(
            firstF64.firstEndParameterF64,
            secondF64.firstEndParameterF64,
            candidateWorkBudgetI32,
        ).takeIf { it != 0 } ?: compareSourceParametersF64(
            firstF64.secondStartParameterF64,
            secondF64.secondStartParameterF64,
            candidateWorkBudgetI32,
        ).takeIf { it != 0 } ?: compareSourceParametersF64(
            firstF64.secondEndParameterF64,
            secondF64.secondEndParameterF64,
            candidateWorkBudgetI32,
        )
    }
    else -> 0
}

// TODO(Task 4): delete once PathArrangementF64F32 consumes [PathSourceTopologyF64] directly.
// Every legacy edge is materially derived from a span section. The complete namespace remap keeps
// source endpoints and synthetic section joints disjoint.
internal fun PathSourceTopologyF64.toPathSplitEdgesF64ForLegacyArrangement(
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathSplitEdgeF64> {
    val legacySectionProvenanceIndexF64 = buildPathLegacySectionProvenanceIndexF64(
        sourceSpansF64,
        contactWitnessesF64,
        candidateWorkBudgetI32,
    )
    val endpointIdentitiesF64 = linkedSetOf<PathVertexIdentityF64>()
    sourceSpansF64.forEach { spanF64 ->
        listOfNotNull(
            spanF64.startLocationF64.vertexIdentityF64,
            spanF64.endLocationF64.vertexIdentityF64,
        ).forEach { identityF64 ->
            candidateWorkBudgetI32.consume()
            endpointIdentitiesF64 += identityF64
        }
    }
    val remappedEndpointIdentitiesF64 = linkedMapOf<PathVertexIdentityF64, PathVertexIdentityF64>()
    endpointIdentitiesF64.forEach { identityF64 ->
        candidateWorkBudgetI32.consume()
        remappedEndpointIdentitiesF64[identityF64] = identityF64.copy(
            namespaceI32 = legacyEndpointIdentityNamespaceI32,
        )
    }
    return buildList {
        sourceSpansF64.forEach { spanF64 ->
            candidateWorkBudgetI32.consume()
            val sectionContactsF64 = legacySectionProvenanceIndexF64.contactWitnessesBySourceSpanIdI64
                .getValue(spanF64.sourceSpanIdI64)
            spanF64.flattenedSectionsF64.forEachIndexed { sectionIndexI32, sectionF64 ->
                candidateWorkBudgetI32.consume()
                val startIdentityF64 = if (sectionIndexI32 == 0) {
                    val sourceIdentityF64 = spanF64.startLocationF64.vertexIdentityF64
                        ?: throw IllegalStateException("path-arrangement-inconsistent")
                    remappedEndpointIdentitiesF64.getValue(sourceIdentityF64)
                } else {
                    legacySectionIdentityF64(spanF64.sourceSpanIdI64, sectionIndexI32 - 1, sectionIndexI32)
                }
                val endIdentityF64 = if (sectionIndexI32 == spanF64.flattenedSectionsF64.lastIndex) {
                    val sourceIdentityF64 = spanF64.endLocationF64.vertexIdentityF64
                        ?: throw IllegalStateException("path-arrangement-inconsistent")
                    remappedEndpointIdentitiesF64.getValue(sourceIdentityF64)
                } else {
                    legacySectionIdentityF64(spanF64.sourceSpanIdI64, sectionIndexI32, sectionIndexI32 + 1)
                }
                add(
                    PathSplitEdgeF64(
                        sourceId = sectionF64.inputEdgeIdI32,
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
                        legacySectionProvenanceF64 = PathLegacySectionProvenanceF64(
                            sourceSpanIdI64 = spanF64.sourceSpanIdI64,
                            sectionIndexI32 = sectionIndexI32,
                            sourceSectionF64 = sectionF64,
                            sourceSpanStartLocationF64 = spanF64.startLocationF64,
                            sourceSpanEndLocationF64 = spanF64.endLocationF64,
                            startLocationF64 = if (sectionIndexI32 == 0) spanF64.startLocationF64 else PathSourceLocationF64(
                                sourceSegmentIndexI32 = spanF64.startLocationF64.sourceSegmentIndexI32,
                                parameterF64 = sectionF64.startParameterF64,
                                originalPointF32 = sectionF64.startIdentityF64.originalPointF32,
                                vertexIdentityF64 = sectionF64.startIdentityF64,
                            ),
                            endLocationF64 = if (sectionIndexI32 == spanF64.flattenedSectionsF64.lastIndex) spanF64.endLocationF64 else PathSourceLocationF64(
                                sourceSegmentIndexI32 = spanF64.endLocationF64.sourceSegmentIndexI32,
                                parameterF64 = sectionF64.endParameterF64,
                                originalPointF32 = sectionF64.endIdentityF64.originalPointF32,
                                vertexIdentityF64 = sectionF64.endIdentityF64,
                            ),
                            contactWitnessesF64 = sectionContactsF64,
                        ),
                    ),
                )
            }
        }
    }
}

private fun PathSplitEdgeF64.toPathFlattenedSectionF64(): PathFlattenedSectionF64 = PathFlattenedSectionF64(
    inputEdgeIdI32 = sourceId,
    startPointF64 = start,
    endPointF64 = end,
    startParameterF64 = sourceStartParameterF64,
    endParameterF64 = sourceEndParameterF64,
    startIdentityF64 = startIdentity,
    endIdentityF64 = endIdentity,
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

private fun comparePointsSemanticallyF64(
    firstF64: Point2F64,
    secondF64: Point2F64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Int {
    candidateWorkBudgetI32.consume()
    when {
        firstF64.x < secondF64.x -> return -1
        firstF64.x > secondF64.x -> return 1
    }
    candidateWorkBudgetI32.consume()
    return when {
        firstF64.y < secondF64.y -> -1
        firstF64.y > secondF64.y -> 1
        else -> 0
    }
}

private fun compareSourceParametersF64(
    firstF64: Double,
    secondF64: Double,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Int {
    candidateWorkBudgetI32.consume()
    return when {
        firstF64 < secondF64 -> -1
        firstF64 > secondF64 -> 1
        else -> 0
    }
}

private fun sameSourceParameterF64(firstF64: Double, secondF64: Double): Boolean =
    PathPredicatesF64.almostEqualUlps(firstF64, secondF64, maxUlps = 16, nearZeroMaxUlps = 0)

private const val legacyEndpointIdentityNamespaceI32: Int = 1
private const val legacySectionIdentityNamespaceI32: Int = 2

private fun legacySectionIdentityF64(
    sourceSpanIdI64: Long,
    firstSectionIndexI32: Int,
    secondSectionIndexI32: Int,
): PathVertexIdentityF64 {
    return PathVertexIdentityF64(
        incidentEdgeIds = listOf(firstSectionIndexI32, secondSectionIndexI32).sorted(),
        parameterByEdgeId = mapOf(firstSectionIndexI32 to 1.0, secondSectionIndexI32 to 0.0),
        originalPointF32 = null,
        namespaceI32 = legacySectionIdentityNamespaceI32,
        identityScopeI64 = sourceSpanIdI64,
    )
}
