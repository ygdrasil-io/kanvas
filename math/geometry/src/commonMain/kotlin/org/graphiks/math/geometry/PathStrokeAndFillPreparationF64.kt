package org.graphiks.math.geometry

/** Result of the bounded topology pass used to combine a device fill and device stroke. */
internal sealed interface PathTopologyUnionResult {
    data class Ready(val inputF64: PathFillInputF64) : PathTopologyUnionResult

    data object Limit : PathTopologyUnionResult
}

/**
 * Produces the one winding geometry authority for `StrokeAndFill` after both operands reached
 * device space.  The topology budget delegates every existing candidate debit to the one stroke
 * ledger, so broad phase, exact intersections, and boundary emission share the same limit.
 */
internal fun preparePathStrokeAndFillUnionF64(
    deviceFillInputF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokePreparationResult = when (
    val unionF64 = unionClosedContoursF64(
        deviceFillInputF64 = deviceFillInputF64,
        deviceStrokeOutlineF64 = deviceStrokeOutlineF64,
        policyF64 = policyF64,
        topologyWorkDebitI64 = PathTopologyWorkDebitI64(ledgerI64::debitTopologyBeforeEmissionI64),
    )
) {
    is PathTopologyUnionResult.Ready -> finalizePathStrokeFillInputF32(
        inputF64 = unionF64.inputF64,
        policyF64 = policyF64,
        ledgerI64 = ledgerI64,
    )

    PathTopologyUnionResult.Limit ->
        PathStrokePreparationResult.ResourceLimitExceeded(PathStrokeResourceLimitReason.TopologyLimit)
}

private fun unionClosedContoursF64(
    deviceFillInputF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    topologyWorkDebitI64: PathTopologyWorkDebitI64,
): PathTopologyUnionResult = try {
    val unionF32 = PathOpsF32.op(
        first = deviceFillInputF64.toTopologyPathF32(),
        second = deviceStrokeOutlineF64.toTopologyPathF32(),
        op = PathBooleanOp.UNION,
        limits = policyF64.topologyLimitsI32(),
        topologyWorkDebitI64 = topologyWorkDebitI64,
    )
    PathTopologyUnionResult.Ready(PathFillInputF64.fromPathF32(unionF32))
} catch (_: PathStrokeResourceLimitAbort) {
    PathTopologyUnionResult.Limit
} catch (error: IllegalStateException) {
    if (error.isExpectedPathTopologyLimit()) {
        PathTopologyUnionResult.Limit
    } else {
        throw error
    }
}

private fun IllegalStateException.isExpectedPathTopologyLimit(): Boolean = when (message) {
    "path-candidate-limit",
    "path-intersection-limit",
    "path-vertex-limit",
    "path-half-edge-limit",
    "path-flattening-limit",
    "path-flattening-convergence",
    "path-f32-projection-collapse" -> true

    else -> false
}

private fun PathStrokePolicyF64.topologyLimitsI32(): PathOpsLimitsI32 = PathOpsLimitsI32(
    maxSubdivisionDepth = limitsI32.maxSubdivisionDepthI32.coerceAtLeast(1),
    maxFlattenedEdgesPerOperand = limitsI32.maxAttemptedGeometryUnitsPerPathI32,
    maxIntersections = limitsI32.maxAttemptedGeometryUnitsPerPathI32,
    maxVertices = limitsI32.maxEmittedVertexCountPerPathI32,
    maxHalfEdges = limitsI32.maxEmittedIndexCountPerPathI32,
    maxCandidateProbes = limitsI32.maxAttemptedGeometryUnitsPerPathI32,
)

private fun PathFillInputF64.toTopologyPathF32(): PathF32 {
    val builderF32 = PathBuilder(fillRule)
    forEach { segmentF64 ->
        when (segmentF64) {
            is PathFillSegmentF64.MoveTo -> builderF32.moveTo(
                segmentF64.point.x.toTopologyF32(),
                segmentF64.point.y.toTopologyF32(),
            )

            is PathFillSegmentF64.LineTo -> builderF32.lineTo(
                segmentF64.point.x.toTopologyF32(),
                segmentF64.point.y.toTopologyF32(),
            )

            is PathFillSegmentF64.QuadTo -> builderF32.quadTo(
                segmentF64.control.x.toTopologyF32(),
                segmentF64.control.y.toTopologyF32(),
                segmentF64.point.x.toTopologyF32(),
                segmentF64.point.y.toTopologyF32(),
            )

            is PathFillSegmentF64.CubicTo -> builderF32.cubicTo(
                segmentF64.control1.x.toTopologyF32(),
                segmentF64.control1.y.toTopologyF32(),
                segmentF64.control2.x.toTopologyF32(),
                segmentF64.control2.y.toTopologyF32(),
                segmentF64.point.x.toTopologyF32(),
                segmentF64.point.y.toTopologyF32(),
            )

            is PathFillSegmentF64.ArcTo -> builderF32.arcTo(
                segmentF64.radius.x.toTopologyF32(),
                segmentF64.radius.y.toTopologyF32(),
                segmentF64.xAxisRotationDegreesF64.toTopologyF32(),
                segmentF64.largeArc,
                segmentF64.sweep,
                segmentF64.point.x.toTopologyF32(),
                segmentF64.point.y.toTopologyF32(),
            )

            PathFillSegmentF64.Close -> builderF32.close()
        }
    }
    return builderF32.build()
}

private fun Double.toTopologyF32(): Float {
    val valueF32 = Float.fromBits(toFloat().toRawBits())
    if (!valueF32.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return if (valueF32 == 0f) 0f else valueF32
}
