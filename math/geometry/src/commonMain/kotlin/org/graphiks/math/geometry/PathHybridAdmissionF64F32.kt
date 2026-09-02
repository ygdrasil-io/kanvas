package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64

/** Immutable public-operation input retained before any flattening or proxy planning. */
internal data class PathOperandInputF32(
    val operand: PathOperand,
    val pathF32: PathF32,
)

internal sealed interface PathSourceAdmissionF64F32 {
    data object Accepted : PathSourceAdmissionF64F32

    data object Unsupported : PathSourceAdmissionF64F32
}

/** Immutable post-broad-phase evidence; it contains no mutable planner closure. */
internal data class PathHybridProjectionObservationF64F32(
    val exactContactWitnessesF64: List<PathContactWitnessF64>,
    val endpointOnlyProjectedRelationsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    val deferredEndpointContactsF64F32: List<PathDeferredProjectedEndpointObservationF64F32>,
    val strictInteriorCutRequirementCountI32: Int,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    val operandLocalCollapsedSectionCountI32: Int,
    val unsupportedProjectedContactCountI32: Int,
    val canonicalSourceEventCountI32: Int,
)

internal data class PathDeferredProjectedEndpointObservationF64F32(
    val firstSourceSpanIdI64: Long,
    val firstSourceSectionIndexI32: Int,
    val firstParameterF64: Double,
    val secondSourceSpanIdI64: Long,
    val secondSourceSectionIndexI32: Int,
    val secondParameterF64: Double,
)

internal sealed interface PathHybridAdmissionF64F32 {
    data class Accepted(
        val exactPlanF64F32: PathAcceptedExactPlanF64F32,
    ) : PathHybridAdmissionF64F32

    data object Unsupported : PathHybridAdmissionF64F32
}

internal class PathAcceptedExactPlanF64F32 private constructor(
    internal val endpointOnlyProjectedRelationsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
    internal val canonicalSourceEventCountI32: Int,
) {
    internal companion object {
        fun fromValidatedRelationsF64F32(
            committedEndpointOnlyProjectedRelationsF64F32: List<PathProjectedCoincidenceProposalF64F32>,
            canonicalSourceEventCountI32: Int,
            candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
        ): PathHybridAdmissionF64F32 {
            if (canonicalSourceEventCountI32 < 0) return PathHybridAdmissionF64F32.Unsupported
            candidateWorkBudgetI32.consumePreflightI64(
                committedEndpointOnlyProjectedRelationsF64F32.size.toLong(),
            )
            return PathHybridAdmissionF64F32.Accepted(
                PathAcceptedExactPlanF64F32(
                    endpointOnlyProjectedRelationsF64F32 =
                        committedEndpointOnlyProjectedRelationsF64F32.toList(),
                    canonicalSourceEventCountI32 = canonicalSourceEventCountI32,
                ),
            )
        }
    }
}

/**
 * Validates immutable broad-phase findings without publishing aliases, cuts, or carrier changes.
 * It intentionally completes every bounded scan before returning Unsupported so ledger exhaustion
 * remains observable before a capability rejection discovered early in the same scan.
 */
internal fun supportsPathHybridProjectionObservationF64F32(
    observationF64F32: PathHybridProjectionObservationF64F32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    val proposalCountI64 = observationF64F32.endpointOnlyProjectedRelationsF64F32.size.toLong()
    val validationCostI64 = checkedPathWorkAddI64(
        checkedPathWorkAddI64(
            observationF64F32.exactContactWitnessesF64.size.toLong(),
            checkedPathWorkMultiplyI64(proposalCountI64, 6L),
        ),
        checkedPathWorkAddI64(
            observationF64F32.deferredEndpointContactsF64F32.size.toLong(),
            observationF64F32.collapsedIncidencesF64F32.size.toLong(),
        ),
    )
    candidateWorkBudgetI32.consumePreflightI64(validationCostI64)

    var unsupportedF64F32 =
        observationF64F32.strictInteriorCutRequirementCountI32 != 0 ||
            observationF64F32.operandLocalCollapsedSectionCountI32 != 0 ||
            observationF64F32.unsupportedProjectedContactCountI32 != 0 ||
            observationF64F32.canonicalSourceEventCountI32 < 0
    val pointWitnessesByIdI64 = mutableMapOf<Long, PathContactWitnessF64.PointF64>()
    observationF64F32.exactContactWitnessesF64.forEach { witnessF64 ->
        when (witnessF64) {
            is PathContactWitnessF64.PointF64 -> {
                if (pointWitnessesByIdI64.put(witnessF64.witnessIdI64, witnessF64) != null) {
                    unsupportedF64F32 = true
                }
            }

            is PathContactWitnessF64.OverlapF64 -> Unit
        }
    }
    observationF64F32.endpointOnlyProjectedRelationsF64F32.forEach { proposalF64F32 ->
        val firstClaimF64 = proposalF64F32.firstClaimF64
        val secondClaimF64 = proposalF64F32.secondClaimF64
        val witnessF64 = pointWitnessesByIdI64[proposalF64F32.pointWitnessF64.witnessIdI64]
        if (
            witnessF64 != proposalF64F32.pointWitnessF64 ||
                firstClaimF64.witnessIdI64 != proposalF64F32.pointWitnessF64.witnessIdI64 ||
                secondClaimF64.witnessIdI64 != proposalF64F32.pointWitnessF64.witnessIdI64 ||
                firstClaimF64.sourceSpanIdI64 == secondClaimF64.sourceSpanIdI64 ||
                !isEndpointOnlyProjectedClaimF64F32(firstClaimF64, proposalF64F32.firstSpanF64) ||
                !isEndpointOnlyProjectedClaimF64F32(secondClaimF64, proposalF64F32.secondSpanF64)
        ) {
            unsupportedF64F32 = true
        }
    }
    observationF64F32.deferredEndpointContactsF64F32.forEach { deferredF64F32 ->
        // Deferred endpoint identity never becomes an alias authority in the conservative domain.
        if (
            deferredF64F32.firstSourceSpanIdI64 < 0L ||
                deferredF64F32.secondSourceSpanIdI64 < 0L ||
                deferredF64F32.firstSourceSectionIndexI32 < 0 ||
                deferredF64F32.secondSourceSectionIndexI32 < 0 ||
                !deferredF64F32.firstParameterF64.isFinite() ||
                !deferredF64F32.secondParameterF64.isFinite()
        ) {
            unsupportedF64F32 = true
        }
        unsupportedF64F32 = true
    }
    observationF64F32.collapsedIncidencesF64F32.forEach { _ -> unsupportedF64F32 = true }
    return !unsupportedF64F32
}

private fun isEndpointOnlyProjectedClaimF64F32(
    claimF64: PathProjectedSpanClaimF64,
    spanF64F32: PathProjectedSourceSpanF64F32,
): Boolean =
    claimF64.sourceSpanIdI64 == spanF64F32.sourceSpanF64.sourceSpanIdI64 &&
        claimF64.sourceSectionIndexI32 == spanF64F32.sectionIndexI32 &&
        claimF64.inputEdgeIdI32 == spanF64F32.sourceSectionF64.inputEdgeIdI32 &&
        claimF64.startVertexIdentityF64 != null &&
        claimF64.endVertexIdentityF64 != null &&
        claimF64.startParameterF64.isFinite() &&
        claimF64.endParameterF64.isFinite() &&
        claimF64.startEdgeParameterF64.isFinite() &&
        claimF64.endEdgeParameterF64.isFinite() &&
        claimF64.startParameterF64 <= claimF64.endParameterF64 &&
        claimF64.startEdgeParameterF64 >= 0.0 &&
        claimF64.endEdgeParameterF64 <= 1.0 &&
        claimF64.startEdgeParameterF64 <= claimF64.endEdgeParameterF64

/**
 * Detects duplicated complete self-closed curve primitives from immutable source commands.
 *
 * This is deliberately a capability boundary, not a geometric canonicalizer.  In particular,
 * line segments, open curves, and exact inter-operand overlaps are not candidates here.
 */
internal fun admitPathSourcePrimitivesF64F32(
    inputsF32: List<PathOperandInputF32>,
    normalizationF64: PathNormalizationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceAdmissionF64F32 {
    var candidateCountI64 = 0L
    inputsF32.forEach { inputF32 ->
        scanSelfClosedSourcePrimitivesF64F32(
            inputF32 = inputF32,
            normalizationF64 = normalizationF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        ) { _, _, _ ->
            candidateCountI64 = checkedPathWorkAddI64(candidateCountI64, 1L)
        }
    }
    val candidateCountI32 = checkedPathCapacityI32(candidateCountI64, "path-candidate-limit")
    // Reserve source-key copies independently from both immutable command scans and sorting.
    candidateWorkBudgetI32.consumePreflightI64(candidateCountI64)
    val observationsF64F32 = ArrayList<PathSelfClosedPrimitiveObservationF64F32>(candidateCountI32)
    inputsF32.forEach { inputF32 ->
        scanSelfClosedSourcePrimitivesF64F32(
            inputF32 = inputF32,
            normalizationF64 = normalizationF64,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        ) { keyF64F32, contourIndexI32, sourceSegmentIndexI32 ->
            observationsF64F32 += PathSelfClosedPrimitiveObservationF64F32(
                keyF64F32 = keyF64F32,
                operand = inputF32.operand,
                contourIndexI32 = contourIndexI32,
                sourceSegmentIndexI32 = sourceSegmentIndexI32,
            )
        }
    }
    val sortCostI64 = deterministicSourceAdmissionSortCostI64F32(candidateCountI32)
    // `sortedWith` owns no ledger callbacks: preflight its comparator worst case and its copy.
    candidateWorkBudgetI32.consumePreflightI64(
        checkedPathWorkAddI64(sortCostI64, candidateCountI64),
    )
    val orderedF64F32 = observationsF64F32.sortedWith(
        Comparator { firstF64F32, secondF64F32 ->
            compareSelfClosedPrimitiveTopologyKeysF64F32(firstF64F32.keyF64F32, secondF64F32.keyF64F32)
        },
    )
    var unsupportedF64F32 = false
    for (indexI32 in 0 until orderedF64F32.size - 1) {
        val firstF64F32 = orderedF64F32[indexI32]
        val secondF64F32 = orderedF64F32[indexI32 + 1]
        // The adjacent revalidation is geometric only: source provenance is intentionally not
        // part of the key equality, but both observations were individually source-qualified.
        candidateWorkBudgetI32.consume()
        if (sameSelfClosedPrimitiveTopologyKeyF64F32(firstF64F32.keyF64F32, secondF64F32.keyF64F32)) {
            unsupportedF64F32 = true
        }
    }
    return if (unsupportedF64F32) PathSourceAdmissionF64F32.Unsupported else PathSourceAdmissionF64F32.Accepted
}

private data class PathSelfClosedPrimitiveObservationF64F32(
    val keyF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32,
    // Provenance is deliberately retained outside the geometric key.
    val operand: PathOperand,
    val contourIndexI32: Int,
    val sourceSegmentIndexI32: Int,
)

private sealed interface PathSelfClosedPrimitiveTopologyKeyF64F32 {
    data class QuadF64F32(
        val startF64: Point2F64,
        val controlF64: Point2F64,
        val endF64: Point2F64,
    ) : PathSelfClosedPrimitiveTopologyKeyF64F32

    data class CubicF64F32(
        val startF64: Point2F64,
        val control1F64: Point2F64,
        val control2F64: Point2F64,
        val endF64: Point2F64,
    ) : PathSelfClosedPrimitiveTopologyKeyF64F32

    data class ArcF64F32(
        val startF64: Point2F64,
        val radiusF64: Vector2F64,
        val xAxisRotationF64: Double,
        val largeArc: Boolean,
        val sweep: Boolean,
        val endF64: Point2F64,
    ) : PathSelfClosedPrimitiveTopologyKeyF64F32
}

private fun scanSelfClosedSourcePrimitivesF64F32(
    inputF32: PathOperandInputF32,
    normalizationF64: PathNormalizationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    emit: (PathSelfClosedPrimitiveTopologyKeyF64F32, Int, Int) -> Unit,
) {
    var currentPointF32: Point2F32? = null
    var contourStartPointF32: Point2F32? = null
    var contourIndexI32 = -1
    var sourceSegmentIndexI32 = 0
    inputF32.pathF32.forEach { segmentF32 ->
        candidateWorkBudgetI32.consume()
        when (segmentF32) {
            is PathSegmentF32.MoveTo -> {
                contourIndexI32 += 1
                currentPointF32 = segmentF32.point
                contourStartPointF32 = segmentF32.point
            }

            is PathSegmentF32.LineTo -> {
                currentPointF32 = segmentF32.point
                sourceSegmentIndexI32 += 1
            }

            is PathSegmentF32.QuadTo -> {
                val startPointF32 = currentPointF32
                if (startPointF32 != null && sameAdmissionTopologyPointF32(startPointF32, segmentF32.point)) {
                    val startF64 = normalizedAdmissionPointF64(normalizationF64, startPointF32)
                    val controlF64 = normalizedAdmissionPointF64(normalizationF64, segmentF32.control)
                    if (!sameAdmissionTopologyPointF64(startF64, controlF64)) {
                        emit(
                            canonicalSelfClosedQuadKeyF64F32(startF64, controlF64, startF64),
                            contourIndexI32,
                            sourceSegmentIndexI32,
                        )
                    }
                }
                currentPointF32 = segmentF32.point
                sourceSegmentIndexI32 += 1
            }

            is PathSegmentF32.CubicTo -> {
                val startPointF32 = currentPointF32
                if (startPointF32 != null && sameAdmissionTopologyPointF32(startPointF32, segmentF32.point)) {
                    val startF64 = normalizedAdmissionPointF64(normalizationF64, startPointF32)
                    val control1F64 = normalizedAdmissionPointF64(normalizationF64, segmentF32.control1)
                    val control2F64 = normalizedAdmissionPointF64(normalizationF64, segmentF32.control2)
                    if (
                        !sameAdmissionTopologyPointF64(startF64, control1F64) ||
                            !sameAdmissionTopologyPointF64(startF64, control2F64)
                    ) {
                        emit(
                            canonicalSelfClosedCubicKeyF64F32(startF64, control1F64, control2F64, startF64),
                            contourIndexI32,
                            sourceSegmentIndexI32,
                        )
                    }
                }
                currentPointF32 = segmentF32.point
                sourceSegmentIndexI32 += 1
            }

            is PathSegmentF32.ArcTo -> {
                val startPointF32 = currentPointF32
                if (startPointF32 != null && sameAdmissionTopologyPointF32(startPointF32, segmentF32.point)) {
                    val startF64 = normalizedAdmissionPointF64(normalizationF64, startPointF32)
                    val radiusF64 = normalizedAdmissionVectorF64(normalizationF64, segmentF32.radius)
                    val arcF64 = ArcEndpointF64(
                        start = startF64,
                        end = startF64,
                        radius = radiusF64,
                        xAxisRotationDegrees = canonicalAdmissionCoordinateF64(segmentF32.xAxisRotation.toDouble()),
                        largeArc = segmentF32.largeArc,
                        sweep = segmentF32.sweep,
                    )
                    val centerF64 = arcCenterF64(arcF64)
                    if (
                        centerF64 != null && centerF64.center.isFinite() &&
                            centerF64.radiusX.isFinite() && centerF64.radiusY.isFinite() &&
                            centerF64.sweepAngle.isFinite() && centerF64.sweepAngle != 0.0
                    ) {
                        emit(
                            canonicalSelfClosedArcKeyF64F32(
                                startF64 = startF64,
                                radiusF64 = radiusF64,
                                xAxisRotationF64 = arcF64.xAxisRotationDegrees,
                                largeArc = arcF64.largeArc,
                                sweep = arcF64.sweep,
                                endF64 = startF64,
                            ),
                            contourIndexI32,
                            sourceSegmentIndexI32,
                        )
                    }
                }
                currentPointF32 = segmentF32.point
                sourceSegmentIndexI32 += 1
            }

            PathSegmentF32.Close -> {
                currentPointF32 = contourStartPointF32
                sourceSegmentIndexI32 += 1
            }
        }
    }
}

private fun canonicalSelfClosedQuadKeyF64F32(
    startF64: Point2F64,
    controlF64: Point2F64,
    endF64: Point2F64,
): PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32 {
    val forwardF64F32 = PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32(startF64, controlF64, endF64)
    val reversedF64F32 = PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32(endF64, controlF64, startF64)
    return if (compareSelfClosedPrimitiveTopologyKeysF64F32(forwardF64F32, reversedF64F32) <= 0) {
        forwardF64F32
    } else {
        reversedF64F32
    }
}

private fun canonicalSelfClosedCubicKeyF64F32(
    startF64: Point2F64,
    control1F64: Point2F64,
    control2F64: Point2F64,
    endF64: Point2F64,
): PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32 {
    val forwardF64F32 = PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32(
        startF64,
        control1F64,
        control2F64,
        endF64,
    )
    val reversedF64F32 = PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32(
        endF64,
        control2F64,
        control1F64,
        startF64,
    )
    return if (compareSelfClosedPrimitiveTopologyKeysF64F32(forwardF64F32, reversedF64F32) <= 0) {
        forwardF64F32
    } else {
        reversedF64F32
    }
}

private fun canonicalSelfClosedArcKeyF64F32(
    startF64: Point2F64,
    radiusF64: Vector2F64,
    xAxisRotationF64: Double,
    largeArc: Boolean,
    sweep: Boolean,
    endF64: Point2F64,
): PathSelfClosedPrimitiveTopologyKeyF64F32.ArcF64F32 {
    val forwardF64F32 = PathSelfClosedPrimitiveTopologyKeyF64F32.ArcF64F32(
        startF64,
        radiusF64,
        xAxisRotationF64,
        largeArc,
        sweep,
        endF64,
    )
    val reversedF64F32 = forwardF64F32.copy(startF64 = endF64, sweep = !sweep, endF64 = startF64)
    return if (compareSelfClosedPrimitiveTopologyKeysF64F32(forwardF64F32, reversedF64F32) <= 0) {
        forwardF64F32
    } else {
        reversedF64F32
    }
}

private fun compareSelfClosedPrimitiveTopologyKeysF64F32(
    firstF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32,
    secondF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32,
): Int {
    val kindOrderI32 = selfClosedPrimitiveKindOrderI32(firstF64F32) - selfClosedPrimitiveKindOrderI32(secondF64F32)
    if (kindOrderI32 != 0) return kindOrderI32
    return when {
        firstF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32 &&
            secondF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32 ->
            compareAdmissionPointsF64(firstF64F32.startF64, secondF64F32.startF64)
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.controlF64, secondF64F32.controlF64) }
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.endF64, secondF64F32.endF64) }

        firstF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32 &&
            secondF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32 ->
            compareAdmissionPointsF64(firstF64F32.startF64, secondF64F32.startF64)
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.control1F64, secondF64F32.control1F64) }
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.control2F64, secondF64F32.control2F64) }
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.endF64, secondF64F32.endF64) }

        firstF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.ArcF64F32 &&
            secondF64F32 is PathSelfClosedPrimitiveTopologyKeyF64F32.ArcF64F32 ->
            compareAdmissionPointsF64(firstF64F32.startF64, secondF64F32.startF64)
                .thenAdmissionCompare { compareAdmissionVectorsF64(firstF64F32.radiusF64, secondF64F32.radiusF64) }
                .thenAdmissionCompare { compareAdmissionCoordinatesF64(firstF64F32.xAxisRotationF64, secondF64F32.xAxisRotationF64) }
                .thenAdmissionCompare { firstF64F32.largeArc.compareTo(secondF64F32.largeArc) }
                .thenAdmissionCompare { firstF64F32.sweep.compareTo(secondF64F32.sweep) }
                .thenAdmissionCompare { compareAdmissionPointsF64(firstF64F32.endF64, secondF64F32.endF64) }

        else -> error("unreachable primitive kind")
    }
}

private fun sameSelfClosedPrimitiveTopologyKeyF64F32(
    firstF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32,
    secondF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32,
): Boolean = compareSelfClosedPrimitiveTopologyKeysF64F32(firstF64F32, secondF64F32) == 0

private fun selfClosedPrimitiveKindOrderI32(keyF64F32: PathSelfClosedPrimitiveTopologyKeyF64F32): Int = when (keyF64F32) {
    is PathSelfClosedPrimitiveTopologyKeyF64F32.QuadF64F32 -> 0
    is PathSelfClosedPrimitiveTopologyKeyF64F32.CubicF64F32 -> 1
    is PathSelfClosedPrimitiveTopologyKeyF64F32.ArcF64F32 -> 2
}

private inline fun Int.thenAdmissionCompare(next: () -> Int): Int = if (this != 0) this else next()

private fun compareAdmissionPointsF64(firstF64: Point2F64, secondF64: Point2F64): Int =
    compareAdmissionCoordinatesF64(firstF64.x, secondF64.x)
        .thenAdmissionCompare { compareAdmissionCoordinatesF64(firstF64.y, secondF64.y) }

private fun compareAdmissionVectorsF64(firstF64: Vector2F64, secondF64: Vector2F64): Int =
    compareAdmissionCoordinatesF64(firstF64.x, secondF64.x)
        .thenAdmissionCompare { compareAdmissionCoordinatesF64(firstF64.y, secondF64.y) }

private fun compareAdmissionCoordinatesF64(firstF64: Double, secondF64: Double): Int = when {
    firstF64 == secondF64 -> 0
    firstF64 < secondF64 -> -1
    else -> 1
}

private fun normalizedAdmissionPointF64(normalizationF64: PathNormalizationF64, pointF32: Point2F32): Point2F64 =
    normalizationF64.normalize(pointF32).let { pointF64 ->
        Point2F64(
            canonicalAdmissionCoordinateF64(pointF64.x),
            canonicalAdmissionCoordinateF64(pointF64.y),
        )
    }

private fun normalizedAdmissionVectorF64(normalizationF64: PathNormalizationF64, vectorF32: org.graphiks.math.vector.Vector2F32): Vector2F64 =
    normalizationF64.normalizeVector(vectorF32).let { vectorF64 ->
        Vector2F64(
            canonicalAdmissionCoordinateF64(vectorF64.x),
            canonicalAdmissionCoordinateF64(vectorF64.y),
        )
    }

private fun canonicalAdmissionCoordinateF64(valueF64: Double): Double = if (valueF64 == 0.0) 0.0 else valueF64

private fun sameAdmissionTopologyPointF32(firstF32: Point2F32, secondF32: Point2F32): Boolean =
    firstF32.x == secondF32.x && firstF32.y == secondF32.y

private fun sameAdmissionTopologyPointF64(firstF64: Point2F64, secondF64: Point2F64): Boolean =
    firstF64.x == secondF64.x && firstF64.y == secondF64.y

private fun deterministicSourceAdmissionSortCostI64F32(sizeI32: Int): Long {
    if (sizeI32 < 2) return 0L
    var widthI64 = 1L
    var levelsI64 = 0L
    val sizeI64 = sizeI32.toLong()
    while (widthI64 < sizeI64) {
        widthI64 = checkedPathWorkMultiplyI64(widthI64, 2L)
        levelsI64 = checkedPathWorkAddI64(levelsI64, 1L)
    }
    return checkedPathWorkMultiplyI64(sizeI64, levelsI64)
}
