package org.graphiks.math.geometry

/** Typed device-outline preparation seam for matrix-owned transformed path draws. */
public sealed interface PathStrokeDeviceOutlinePreparationResult {
    public data class Ready(
        public val deviceOutlineF64: PathFillInputF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeDeviceOutlinePreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeDeviceOutlinePreparationResult

    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : PathStrokeDeviceOutlinePreparationResult

    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : PathStrokeDeviceOutlinePreparationResult
}

/**
 * Produces a finite device-space outline while continuing the caller's immutable work snapshots.
 * Matrix owns transform selection; this seam owns only stroke geometry and bounded publication.
 */
public fun preparePathStrokeDeviceOutlineF64(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): PathStrokeDeviceOutlinePreparationResult {
    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64, frameWorkUsageBeforeI64, policyF64.limitsI32, policyF64.limitsI64,
        )
        val sourceFillF64 = PathFillInputF64.fromPathF32(sourcePathF32, ledgerI64::debitBeforeEmissionI64)
        if (!sourceFillF64.all(::isFiniteStrokeDeviceOutlineSegmentF64)) {
            return PathStrokeDeviceOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
        }
        val widthF64 = styleF64.widthF64
        if (widthF64 is PathStrokeWidthF64.Finite && widthF64.valueF64 == 0.0) {
            return PathStrokeDeviceOutlinePreparationResult.Empty(
                ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
        }
        val centerlineF64 = preparePathStrokeCenterlinesF64(sourceFillF64, styleF64.dashF64, policyF64, ledgerI64)
            ?: return PathStrokeDeviceOutlinePreparationResult.Empty(
                ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
        val outlineAndProjectionF64 = when (widthF64) {
            is PathStrokeWidthF64.Finite -> {
                val outlineF64 = prepareFinitePathStrokeOutlineF64(centerlineF64, styleF64, policyF64, ledgerI64)
                    ?: return PathStrokeDeviceOutlinePreparationResult.Empty(
                        ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
                    )
                outlineF64 to projectionF64
            }
            PathStrokeWidthF64.Hairline -> {
                val outlineF64 = prepareProjectedHairlineOutlineF64(centerlineF64, styleF64, projectionF64, policyF64, ledgerI64)
                    ?: return PathStrokeDeviceOutlinePreparationResult.Empty(
                        ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
                    )
                outlineF64 to IdentityStrokeDeviceOutlineProjectionF64
            }
        }
        val deviceOutlineF64 = materializeProjectedStrokeOutlineInputF64(
            outlineAndProjectionF64.first, outlineAndProjectionF64.second, policyF64, ledgerI64,
        ) ?: return PathStrokeDeviceOutlinePreparationResult.Empty(
            ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
        )
        PathStrokeDeviceOutlinePreparationResult.Ready(
            deviceOutlineF64, ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
        )
    } catch (_: PathStrokeInvalidInputAbort) {
        PathStrokeDeviceOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (_: PathStrokeOutlineInvalidAbort) {
        PathStrokeDeviceOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (abort: PathStrokeProjectionAbort) {
        PathStrokeDeviceOutlinePreparationResult.InvalidScene(abort.reason)
    } catch (abort: PathStrokeResourceLimitAbort) {
        PathStrokeDeviceOutlinePreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

private object IdentityStrokeDeviceOutlineProjectionF64 : PathStrokeProjectionF64 {
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

private fun isFiniteStrokeDeviceOutlineSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo -> segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() && segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}
