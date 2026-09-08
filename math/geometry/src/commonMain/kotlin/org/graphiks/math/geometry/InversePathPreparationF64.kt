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
    if (!finiteFillF64.all(::isFiniteInverseInputSegmentF64) ||
        (deviceStrokeOutlineF64 != null && !deviceStrokeOutlineF64.all(::isFiniteInverseInputSegmentF64))
    ) {
        return InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }
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
        val finiteInteriorF64 = finiteFillF64.withFiniteFillRule()
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

            is PathFillPreparationResult.Empty -> InversePathPreparationResult.Ready(
                geometryF32 = InversePathGeometryF32.of(InverseInteriorCoverageF32.Zero, domainI32),
                pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
            )

            is PathFillPreparationResult.InvalidScene ->
                InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)

            is PathFillPreparationResult.ResourceLimitExceeded ->
                InversePathPreparationResult.ResourceLimitExceeded(fillResult.reason.toPathStrokeResourceLimitReason())
        }
    } catch (abort: PathStrokeResourceLimitAbort) {
        InversePathPreparationResult.ResourceLimitExceeded(abort.reason)
    } catch (error: IllegalStateException) {
        if (error.isInverseTopologyLimit()) {
            InversePathPreparationResult.ResourceLimitExceeded(PathStrokeResourceLimitReason.TopologyLimit)
        } else {
            throw error
        }
    }
}

private fun PathFillInputF64.withFiniteFillRule(): PathFillInputF64 = PathFillInputF64.of(
    when (fillRule) {
        FillRule.WINDING, FillRule.INVERSE_WINDING -> FillRule.WINDING
        FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
    },
    asSequence().toList(),
)

private fun PathFillInputF64.differenceDeviceOutlineF64(
    deviceStrokeOutlineF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathFillInputF64 {
    val differenceF32 = PathOpsF32.op(
        first = toInverseTopologyPathF32(),
        second = deviceStrokeOutlineF64.withFiniteFillRule().toInverseTopologyPathF32(),
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
    return PathFillInputF64.fromPathF32(differenceF32)
}

private fun PathFillInputF64.toInverseTopologyPathF32(): PathF32 {
    val builderF32 = PathBuilder(fillRule)
    forEach { segmentF64 ->
        when (segmentF64) {
            is PathFillSegmentF64.MoveTo -> builderF32.moveTo(segmentF64.point.x.toFloat(), segmentF64.point.y.toFloat())
            is PathFillSegmentF64.LineTo -> builderF32.lineTo(segmentF64.point.x.toFloat(), segmentF64.point.y.toFloat())
            is PathFillSegmentF64.QuadTo -> builderF32.quadTo(
                segmentF64.control.x.toFloat(), segmentF64.control.y.toFloat(), segmentF64.point.x.toFloat(), segmentF64.point.y.toFloat(),
            )
            is PathFillSegmentF64.CubicTo -> builderF32.cubicTo(
                segmentF64.control1.x.toFloat(), segmentF64.control1.y.toFloat(), segmentF64.control2.x.toFloat(), segmentF64.control2.y.toFloat(),
                segmentF64.point.x.toFloat(), segmentF64.point.y.toFloat(),
            )
            is PathFillSegmentF64.ArcTo -> builderF32.arcTo(
                segmentF64.radius.x.toFloat(), segmentF64.radius.y.toFloat(), segmentF64.xAxisRotationDegreesF64.toFloat(),
                segmentF64.largeArc, segmentF64.sweep, segmentF64.point.x.toFloat(), segmentF64.point.y.toFloat(),
            )
            PathFillSegmentF64.Close -> builderF32.close()
        }
    }
    return builderF32.build()
}

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
    return 16L
}

private fun IllegalStateException.isInverseTopologyLimit(): Boolean = message in setOf(
    "path-candidate-limit", "path-intersection-limit", "path-vertex-limit", "path-half-edge-limit",
    "path-flattening-limit", "path-flattening-convergence", "path-f32-projection-collapse",
)

/** Device outline materialization used by the matrix inverse-draw facade. */
public sealed interface InverseDeviceStrokeOutlinePreparationResult {
    public data class Ready(
        public val deviceOutlineF64: PathFillInputF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : InverseDeviceStrokeOutlinePreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : InverseDeviceStrokeOutlinePreparationResult

    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : InverseDeviceStrokeOutlinePreparationResult

    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) :
        InverseDeviceStrokeOutlinePreparationResult
}

/**
 * Materializes the device stroke outline with the caller's continuing work ledger. Finite
 * widths expand before projection; hairlines project their centerline before one-pixel expansion.
 */
public fun prepareInverseDeviceStrokeOutlineF64(
    sourceFillF64: PathFillInputF64,
    styleF64: PathStrokeStyleF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InverseDeviceStrokeOutlinePreparationResult {
    if (!sourceFillF64.all(::isFiniteInverseInputSegmentF64)) {
        return InverseDeviceStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }
    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64,
            policyF64.limitsI32,
            policyF64.limitsI64,
        )
        if (styleF64.widthF64 is PathStrokeWidthF64.Finite && styleF64.widthF64.valueF64 == 0.0) {
            return InverseDeviceStrokeOutlinePreparationResult.Empty(
                ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
        }
        val centerlineF64 = preparePathStrokeCenterlinesF64(sourceFillF64, styleF64.dashF64, policyF64, ledgerI64)
            ?: return InverseDeviceStrokeOutlinePreparationResult.Empty(
                ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
        val outlineAndProjectionF64 = when (styleF64.widthF64) {
            is PathStrokeWidthF64.Finite -> {
                val outlineF64 = prepareFinitePathStrokeOutlineF64(centerlineF64, styleF64, policyF64, ledgerI64)
                    ?: return InverseDeviceStrokeOutlinePreparationResult.Empty(
                        ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
                    )
                outlineF64 to projectionF64
            }

            PathStrokeWidthF64.Hairline -> {
                val outlineF64 = prepareProjectedHairlineOutlineF64(centerlineF64, styleF64, projectionF64, policyF64, ledgerI64)
                    ?: return InverseDeviceStrokeOutlinePreparationResult.Empty(
                        ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
                    )
                outlineF64 to InverseIdentityStrokeProjectionF64
            }
        }
        val deviceOutlineF64 = materializeProjectedStrokeOutlineInputF64(
            outlineAndProjectionF64.first,
            outlineAndProjectionF64.second,
            policyF64,
            ledgerI64,
        ) ?: return InverseDeviceStrokeOutlinePreparationResult.Empty(
            ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
        )
        InverseDeviceStrokeOutlinePreparationResult.Ready(
            deviceOutlineF64,
            ledgerI64.snapshotPathUsageI64(),
            ledgerI64.snapshotFrameUsageAfterI64(),
        )
    } catch (_: PathStrokeInvalidInputAbort) {
        InverseDeviceStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (_: PathStrokeOutlineInvalidAbort) {
        InverseDeviceStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (abort: PathStrokeProjectionAbort) {
        InverseDeviceStrokeOutlinePreparationResult.InvalidScene(abort.reason)
    } catch (abort: PathStrokeResourceLimitAbort) {
        InverseDeviceStrokeOutlinePreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

private object InverseIdentityStrokeProjectionF64 : PathStrokeProjectionF64 {
    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
        pointF64.takeIf(Point2F64::isFinite)?.let(PathStrokeProjectionPointResultF64::Ready)
            ?: PathStrokeProjectionPointResultF64.NonFinite

    override fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64 = intervalF64.sourceSagittaUpperBoundF64
        .takeIf { it.isFinite() && it >= 0.0 }
        ?.let(PathStrokeProjectionIntervalResultF64::Bounded)
        ?: PathStrokeProjectionIntervalResultF64.NonFinite
}
