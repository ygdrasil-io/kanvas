package org.graphiks.math.geometry

/** Outcome of bounded inverse path preparation. */
public sealed interface InversePathPreparationResult {
    public data class Ready(
        public val geometryF32: InversePathGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : InversePathPreparationResult

    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : InversePathPreparationResult

    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : InversePathPreparationResult
}

/**
 * Finalizes a device-space inverse path over [domainI32].  Inversion never enters the finite
 * fill worker: that worker only prepares the interior to subtract from the finite domain.
 */
public fun prepareInversePathGeometryF32(
    finiteFillF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64?,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): InversePathPreparationResult {
    if (mode == InversePathDrawMode.StrokeAndFill && styleF64 == null) {
        return InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    }

    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            limitsI32 = policyF64.limitsI32,
            limitsI64 = policyF64.limitsI64,
        )
        if (!finiteFillF64.all(::isFiniteInverseInputSegmentF64) ||
            (deviceStrokeOutlineF64 != null && !deviceStrokeOutlineF64.all(::isFiniteInverseInputSegmentF64))
        ) {
            return InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
        }
        val finiteInteriorF64 = finiteFillF64.withFiniteFillRule(ledgerI64)
        val shouldSubtractOutline = mode == InversePathDrawMode.StrokeAndFill &&
            deviceStrokeOutlineF64 != null &&
            (styleF64!!.widthF64 !is PathStrokeWidthF64.Finite ||
                styleF64.widthF64.valueF64 != 0.0)
        val interiorInputF64 = if (shouldSubtractOutline) {
            finiteInteriorF64.differenceDeviceOutlineF64(requireNotNull(deviceStrokeOutlineF64), policyF64, ledgerI64)
        } else {
            finiteInteriorF64
        }
        when (
            val fillResult = preparePathFillGeometryWithStrokeWorkF32(
                inputF64 = interiorInputF64,
                fillPolicyF64 = PathFillFlatteningPolicyF64(policyF64.maximumSagittaErrorF64),
                ledgerI64 = ledgerI64,
            )
        ) {
            is PathFillPreparationResult.Ready -> {
                ledgerI64.debitBeforeEmissionI64(
                    PathStrokeWorkUsageI64(snapshotByteCountI64 = inversePublicationBytesI64(fillResult.geometryF32)),
                )
                InversePathPreparationResult.Ready(
                    geometryF32 = InversePathGeometryF32.of(
                        InverseInteriorCoverageF32.Geometry.of(fillResult.geometryF32),
                        domainI32,
                    ),
                    pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                    frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
                )
            }

            is PathFillPreparationResult.Empty -> {
                ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                InversePathPreparationResult.Ready(
                    geometryF32 = InversePathGeometryF32.of(InverseInteriorCoverageF32.Zero, domainI32),
                    pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                    frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
                )
            }

            is PathFillPreparationResult.InvalidScene ->
                InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)

            is PathFillPreparationResult.ResourceLimitExceeded ->
                InversePathPreparationResult.ResourceLimitExceeded(fillResult.reason.toPathStrokeResourceLimitReason())
        }
    } catch (abort: PathStrokeResourceLimitAbort) {
        InversePathPreparationResult.ResourceLimitExceeded(abort.reason)
    } catch (_: InversePathConversionAbort) {
        InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (error: IllegalStateException) {
        if (error.isInverseTopologyLimit()) {
            InversePathPreparationResult.ResourceLimitExceeded(PathStrokeResourceLimitReason.TopologyLimit)
        } else {
            throw error
        }
    }
}

private fun PathFillInputF64.withFiniteFillRule(ledgerI64: PathStrokeWorkLedgerI64): PathFillInputF64 {
    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = inverseInputSnapshotBytesI64()))
    return PathFillInputF64.of(
        when (fillRule) {
            FillRule.WINDING, FillRule.INVERSE_WINDING -> FillRule.WINDING
            FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
        },
        asSequence().toList(),
    )
}

private fun PathFillInputF64.differenceDeviceOutlineF64(
    deviceStrokeOutlineF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathFillInputF64 {
    val differenceF32 = PathOpsF32.op(
        first = toInverseTopologyPathF32(ledgerI64),
        second = deviceStrokeOutlineF64.withFiniteFillRule(ledgerI64).toInverseTopologyPathF32(ledgerI64),
        op = PathBooleanOp.DIFFERENCE,
        limits = PathOpsLimitsI32(
            maxSubdivisionDepth = policyF64.limitsI32.maxSubdivisionDepthI32.coerceAtLeast(1),
            maxFlattenedEdgesPerOperand = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
            maxIntersections = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
            maxVertices = policyF64.limitsI32.maxEmittedVertexCountPerPathI32,
            maxHalfEdges = policyF64.limitsI32.maxEmittedIndexCountPerPathI32,
            maxCandidateProbes = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
        ),
        topologyWorkDebitI64 = PathTopologyWorkDebitI64(ledgerI64::debitTopologyBeforeEmissionI64),
    )
    return PathFillInputF64.fromPathF32(differenceF32, ledgerI64::debitBeforeEmissionI64)
}

private fun PathFillInputF64.toInverseTopologyPathF32(ledgerI64: PathStrokeWorkLedgerI64): PathF32 {
    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val builderF32 = PathBuilder(fillRule)
    forEach { segmentF64 ->
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        when (segmentF64) {
            is PathFillSegmentF64.MoveTo -> builderF32.moveTo(segmentF64.point.x.toInverseF32(), segmentF64.point.y.toInverseF32())
            is PathFillSegmentF64.LineTo -> builderF32.lineTo(segmentF64.point.x.toInverseF32(), segmentF64.point.y.toInverseF32())
            is PathFillSegmentF64.QuadTo -> builderF32.quadTo(
                segmentF64.control.x.toInverseF32(), segmentF64.control.y.toInverseF32(), segmentF64.point.x.toInverseF32(), segmentF64.point.y.toInverseF32(),
            )
            is PathFillSegmentF64.CubicTo -> builderF32.cubicTo(
                segmentF64.control1.x.toInverseF32(), segmentF64.control1.y.toInverseF32(), segmentF64.control2.x.toInverseF32(), segmentF64.control2.y.toInverseF32(),
                segmentF64.point.x.toInverseF32(), segmentF64.point.y.toInverseF32(),
            )
            is PathFillSegmentF64.ArcTo -> builderF32.arcTo(
                segmentF64.radius.x.toInverseF32(), segmentF64.radius.y.toInverseF32(), segmentF64.xAxisRotationDegreesF64.toInverseF32(),
                segmentF64.largeArc, segmentF64.sweep, segmentF64.point.x.toInverseF32(), segmentF64.point.y.toInverseF32(),
            )
            PathFillSegmentF64.Close -> builderF32.close()
        }
    }
    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    return builderF32.build()
}

private fun Double.toInverseF32(): Float {
    if (!isFinite() || this > Float.MAX_VALUE.toDouble() || this < -Float.MAX_VALUE.toDouble()) {
        throw InversePathConversionAbort()
    }
    return toFloat().takeIf(Float::isFinite) ?: throw InversePathConversionAbort()
}

private class InversePathConversionAbort : RuntimeException()

private fun isFiniteInverseInputSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo -> segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() && segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}

private fun inversePublicationBytesI64(geometryF32: PathFillGeometryF32): Long {
    if (geometryF32.snapshotByteCostI64 > Long.MAX_VALUE - 16L) {
        throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
    }
    return 32L
}

private fun PathFillInputF64.inverseInputSnapshotBytesI64(): Long =
    16L + segmentCountI32.toLong() * 16L

private fun IllegalStateException.isInverseTopologyLimit(): Boolean = message in setOf(
    "path-candidate-limit", "path-intersection-limit", "path-vertex-limit", "path-half-edge-limit",
    "path-flattening-limit", "path-flattening-convergence", "path-f32-projection-collapse",
)
