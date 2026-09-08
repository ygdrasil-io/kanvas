package org.graphiks.math.geometry

/** Selects whether the W4d caller requests a stroke alone or its later topological union. */
public enum class PathStrokeDrawMode {
    Stroke,
    StrokeAndFill,
}

/**
 * Maps one source fill command to its device-space command for `StrokeAndFill` preparation.
 *
 * Geometry owns the destination input and invokes this authority only after charging each source
 * command to its shared ledger. A `null` result rejects a non-finite mapping. The authority
 * cannot fan out commands and never receives the ledger.
 */
public fun interface PathStrokeDeviceFillSegmentMapperF64 {
    public fun mapDeviceFillSegmentF64(sourceSegmentF64: PathFillSegmentF64): PathFillSegmentF64?
}

/** Outcome of bounded stroke preparation in final device-space F32 geometry. */
public sealed interface PathStrokePreparationResult {
    public data class Ready(
        public val geometryF32: PathStrokeGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokePreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokePreparationResult

    public data class InvalidScene(
        public val reason: PathStrokeInvalidSceneReason,
    ) : PathStrokePreparationResult

    public data class ResourceLimitExceeded(
        public val reason: PathStrokeResourceLimitReason,
    ) : PathStrokePreparationResult
}

/**
 * Immutable geometry authority emitted by bounded stroke preparation.
 *
 * The retained fill geometry remains the only direct/stencil authority.  Every mutable payload
 * reachable through this value is copied when captured and copied again when read.
 */
public class PathStrokeGeometryF32 private constructor(
    fillGeometryF32: PathFillGeometryF32,
    conservativeBoundsF32: RectF32,
    public val workUsageI64: PathStrokeWorkUsageI64,
) {
    private val fillGeometrySnapshotF32: PathFillGeometryF32 = fillGeometryF32.copyStrokeSnapshotF32()
    private val conservativeBoundsSnapshotF32: RectF32 = conservativeBoundsF32.copyStrokeSnapshotF32()

    init {
        require(conservativeBoundsSnapshotF32.isFinite())
    }

    public val vertexCostI64: Long
        get() = fillGeometrySnapshotF32.vertexCostI64

    public val indexCostI64: Long
        get() = fillGeometrySnapshotF32.indexCostI64

    public val snapshotByteCostI64: Long
        get() = workUsageI64.snapshotByteCountI64

    public fun copyFillGeometryF32(): PathFillGeometryF32 = fillGeometrySnapshotF32.copyStrokeSnapshotF32()

    public fun copyConservativeBoundsF32(): RectF32 = conservativeBoundsSnapshotF32.copyStrokeSnapshotF32()

    public fun copyConservativeScissorI32(): RectI32 = fillGeometrySnapshotF32.copyConservativeScissorI32()

    internal companion object {
        internal fun of(
            fillGeometryF32: PathFillGeometryF32,
            conservativeBoundsF32: RectF32,
            workUsageI64: PathStrokeWorkUsageI64,
        ): PathStrokeGeometryF32 = PathStrokeGeometryF32(
            fillGeometryF32 = fillGeometryF32,
            conservativeBoundsF32 = conservativeBoundsF32,
            workUsageI64 = workUsageI64,
        )
    }
}

private fun PathFillGeometryF32.copyStrokeSnapshotF32(): PathFillGeometryF32 = PathFillGeometryF32(
    fillRule = fillRule,
    attemptedEdgeCountI32 = attemptedEdgeCountI32,
    emittedNonZeroClosedEdgeCountI32 = emittedNonZeroClosedEdgeCountI32,
    conservativeScissorI32 = copyConservativeScissorI32(),
    directTriangleF32 = copyDirectTriangleF32OrNull(),
    stencilEdgeFanF32 = copyStencilEdgeFanF32OrNull(),
)

private fun RectF32.copyStrokeSnapshotF32(): RectF32 = RectF32(left, top, right, bottom)

/**
 * Runs the source dash/outline pipeline and finalizes either the device stroke or one
 * topological `StrokeAndFill` union through the shared fill worker. `StrokeAndFill` callers
 * supply either a device-fill segment mapper, which geometry charges before every affine mapping,
 * or an already-certified device fill snapshot carrying [pathWorkUsageBeforeI64]. The latter
 * allows matrix-owned projective preparation to retain its transactional work ledger.
 */
public fun prepareProjectedPathStrokeGeometryF32(
    inputF64: PathFillInputF64,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    deviceFillSegmentMapperF64: PathStrokeDeviceFillSegmentMapperF64? = null,
    deviceFillInputF64: PathFillInputF64? = null,
): PathStrokePreparationResult {
    if (
        mode == PathStrokeDrawMode.StrokeAndFill &&
        (deviceFillSegmentMapperF64 == null) == (deviceFillInputF64 == null)
    ) {
        return PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    }
    if (mode == PathStrokeDrawMode.StrokeAndFill && inputF64.fillRule.isInverse()) {
        return PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    }
    if (!inputF64.all(::isFiniteStrokePipelineInputSegmentF64)) {
        return PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }
    if (deviceFillInputF64 != null && !deviceFillInputF64.all(::isFiniteStrokePipelineInputSegmentF64)) {
        return PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }

    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            limitsI32 = policyF64.limitsI32,
            limitsI64 = policyF64.limitsI64,
        )
        var didMaterializeDeviceFillInputF64 = false
        var materializedDeviceFillInputF64: PathFillInputF64? = null
        fun materializeDeviceFillInputF64(): PathFillInputF64 {
            if (didMaterializeDeviceFillInputF64) return requireNotNull(materializedDeviceFillInputF64)

            if (deviceFillInputF64 != null) {
                materializedDeviceFillInputF64 = deviceFillInputF64
                didMaterializeDeviceFillInputF64 = true
                return deviceFillInputF64
            }

            val mappedSegmentsF64 = mutableListOf<PathFillSegmentF64>()
            val mapperF64 = requireNotNull(deviceFillSegmentMapperF64)
            inputF64.forEach { sourceSegmentF64 ->
                ledgerI64.debitTopologyBeforeEmissionI64(1L)
                val mappedSegmentF64 = mapperF64.mapDeviceFillSegmentF64(sourceSegmentF64)
                    ?: throw PathStrokeDeviceFillSegmentMappingAbort()
                mappedSegmentsF64 += mappedSegmentF64
            }
            val deviceFillInputF64 = PathFillInputF64.of(inputF64.fillRule, mappedSegmentsF64)
            if (!deviceFillInputF64.all(::isFiniteStrokePipelineInputSegmentF64)) {
                throw PathStrokeDeviceFillSegmentMappingAbort()
            }
            materializedDeviceFillInputF64 = deviceFillInputF64
            didMaterializeDeviceFillInputF64 = true
            return deviceFillInputF64
        }
        fun finalizeDeviceFillOrEmpty(): PathStrokePreparationResult =
            if (mode == PathStrokeDrawMode.StrokeAndFill) {
                finalizePathStrokeFillInputF32(materializeDeviceFillInputF64(), policyF64, ledgerI64)
            } else {
                PathStrokePreparationResult.Empty(
                    pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                    frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
                )
            }
        if (
            mode == PathStrokeDrawMode.StrokeAndFill &&
            styleF64.widthF64 is PathStrokeWidthF64.Finite &&
            styleF64.widthF64.valueF64 == 0.0
        ) {
            return finalizePathStrokeFillInputF32(
                inputF64 = materializeDeviceFillInputF64(),
                policyF64 = policyF64,
                ledgerI64 = ledgerI64,
            )
        }
        val centerlineF64 = preparePathStrokeCenterlinesF64(
            inputF64 = inputF64,
            dashF64 = styleF64.dashF64,
            policyF64 = policyF64,
            ledgerI64 = ledgerI64,
        ) ?: return finalizeDeviceFillOrEmpty()

        val outlineAndProjectionF64 = when (styleF64.widthF64) {
            is PathStrokeWidthF64.Finite -> {
                val outlineF64 = prepareFinitePathStrokeOutlineF64(
                    centerlineF64 = centerlineF64,
                    styleF64 = styleF64,
                    policyF64 = policyF64,
                    ledgerI64 = ledgerI64,
                ) ?: return finalizeDeviceFillOrEmpty()
                outlineF64 to projectionF64
            }

            PathStrokeWidthF64.Hairline -> {
                val outlineF64 = prepareProjectedHairlineOutlineF64(
                    centerlineF64 = centerlineF64,
                    styleF64 = styleF64,
                    projectionF64 = projectionF64,
                    policyF64 = policyF64,
                    ledgerI64 = ledgerI64,
                ) ?: return finalizeDeviceFillOrEmpty()
                outlineF64 to IdentityPathStrokeProjectionF64
            }
        }

        val deviceInputF64 = materializeProjectedStrokeOutlineInputF64(
            outlineF64 = outlineAndProjectionF64.first,
            projectionF64 = outlineAndProjectionF64.second,
            policyF64 = policyF64,
            ledgerI64 = ledgerI64,
        ) ?: return finalizeDeviceFillOrEmpty()

        if (mode == PathStrokeDrawMode.StrokeAndFill) {
            preparePathStrokeAndFillUnionF64(
                deviceFillInputF64 = materializeDeviceFillInputF64(),
                deviceStrokeOutlineF64 = deviceInputF64,
                policyF64 = policyF64,
                ledgerI64 = ledgerI64,
            )
        } else {
            finalizePathStrokeFillInputF32(deviceInputF64, policyF64, ledgerI64)
        }
    } catch (abort: PathStrokeResourceLimitAbort) {
        PathStrokePreparationResult.ResourceLimitExceeded(abort.reason)
    } catch (_: PathStrokeDeviceFillSegmentMappingAbort) {
        PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (_: PathStrokeInvalidInputAbort) {
        PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (_: PathStrokeOutlineInvalidAbort) {
        PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (abort: PathStrokeProjectionAbort) {
        PathStrokePreparationResult.InvalidScene(abort.reason)
    }
}

private class PathStrokeDeviceFillSegmentMappingAbort : RuntimeException()

internal fun finalizePathStrokeFillInputF32(
    inputF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokePreparationResult = when (
    val fillResult = preparePathFillGeometryWithStrokeWorkF32(
        inputF64 = inputF64,
        fillPolicyF64 = PathFillFlatteningPolicyF64(
            maximumSagittaErrorF64 = policyF64.maximumSagittaErrorF64,
        ),
        ledgerI64 = ledgerI64,
    )
) {
    is PathFillPreparationResult.Ready -> {
        val boundsF32 = fillResult.geometryF32.strokeConservativeBoundsF32()
        ledgerI64.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(
                snapshotByteCountI64 = checkedStrokeGeometryPublicationBytesI64(
                    fillResult.geometryF32.snapshotByteCostI64,
                ),
            ),
        )
        val pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64()
        val frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64()
        PathStrokePreparationResult.Ready(
            geometryF32 = PathStrokeGeometryF32.of(
                fillGeometryF32 = fillResult.geometryF32,
                conservativeBoundsF32 = boundsF32,
                workUsageI64 = pathWorkUsageI64,
            ),
            pathWorkUsageI64 = pathWorkUsageI64,
            frameWorkUsageAfterI64 = frameWorkUsageAfterI64,
        )
    }

    is PathFillPreparationResult.Empty -> PathStrokePreparationResult.Empty(
        pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
        frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
    )

    is PathFillPreparationResult.InvalidScene ->
        PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)

    is PathFillPreparationResult.ResourceLimitExceeded ->
        PathStrokePreparationResult.ResourceLimitExceeded(fillResult.reason.toPathStrokeResourceLimitReason())
}

private object IdentityPathStrokeProjectionF64 : PathStrokeProjectionF64 {
    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
        if (pointF64.isFinite()) PathStrokeProjectionPointResultF64.Ready(pointF64)
        else PathStrokeProjectionPointResultF64.NonFinite

    override fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64 = intervalF64.sourceSagittaUpperBoundF64
        .takeIf { it.isFinite() && it >= 0.0 }
        ?.let(PathStrokeProjectionIntervalResultF64::Bounded)
        ?: PathStrokeProjectionIntervalResultF64.NonFinite
}

internal fun materializeProjectedStrokeOutlineInputF64(
    outlineF64: PathStrokeOutlineF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathFillInputF64? {
    val segmentsF64 = mutableListOf<PathFillSegmentF64>()
    repeat(outlineF64.contourCountI32) { contourIndexI32 ->
        val intervalsF64 = outlineF64.copyContourIntervalsF64(contourIndexI32)
        val firstIntervalF64 = intervalsF64.firstOrNull() ?: return@repeat
        val firstPointF64 = projectStrokePointF64(
            projectionF64,
            firstIntervalF64.primitiveF64.pointAtF64(firstIntervalF64.startParameterF64),
        )
        segmentsF64 += PathFillSegmentF64.MoveTo(firstPointF64)
        intervalsF64.forEach { intervalF64 ->
            appendProjectedStrokeIntervalF64(
                destinationF64 = segmentsF64,
                intervalF64 = intervalF64,
                startParameterF64 = intervalF64.startParameterF64,
                endParameterF64 = intervalF64.endParameterF64,
                projectionF64 = projectionF64,
                policyF64 = policyF64,
                ledgerI64 = ledgerI64,
                depthI32 = 0,
            )
        }
        segmentsF64 += PathFillSegmentF64.Close
    }
    return PathFillInputF64.of(FillRule.WINDING, segmentsF64).takeIf { it.segmentCountI32 > 0 }
}

private fun appendProjectedStrokeIntervalF64(
    destinationF64: MutableList<PathFillSegmentF64>,
    intervalF64: PathStrokeOutlineIntervalF64,
    startParameterF64: Double,
    endParameterF64: Double,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
    depthI32: Int,
) {
    if (startParameterF64 >= endParameterF64) return
    ledgerI64.debitTopologyBeforeEmissionI64(1L)
    fun subdivideF64() {
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.FlatteningDidNotConverge)
        }
        val middleParameterF64 = startParameterF64 + (endParameterF64 - startParameterF64) * 0.5
        if (!middleParameterF64.isFinite() || middleParameterF64 <= startParameterF64 ||
            middleParameterF64 >= endParameterF64
        ) {
            throw PathStrokeProjectionAbort()
        }
        appendProjectedStrokeIntervalF64(
            destinationF64,
            intervalF64,
            startParameterF64,
            middleParameterF64,
            projectionF64,
            policyF64,
            ledgerI64,
            depthI32 + 1,
        )
        appendProjectedStrokeIntervalF64(
            destinationF64,
            intervalF64,
            middleParameterF64,
            endParameterF64,
            projectionF64,
            policyF64,
            ledgerI64,
            depthI32 + 1,
        )
    }

    val restrictedIntervalF64 = when (
        val restrictionF64 = restrictPathStrokeOutlineIntervalF64(
            intervalF64,
            startParameterF64,
            endParameterF64,
        )
    ) {
        is PathStrokeOutlineRestrictionResultF64.Ready -> restrictionF64.intervalF64
        PathStrokeOutlineRestrictionResultF64.NeedsSubdivision -> {
            subdivideF64()
            return
        }
    }
    when (val certificationF64 = projectionF64.certifyOutlineIntervalF64(restrictedIntervalF64)) {
        is PathStrokeProjectionIntervalResultF64.Bounded -> {
            val maximumSagittaF64 = certificationF64.maximumDeviceSagittaUpperBoundF64
            if (!maximumSagittaF64.isFinite() || maximumSagittaF64 < 0.0) {
                throw PathStrokeProjectionAbort()
            }
            if (maximumSagittaF64 > policyF64.maximumSagittaErrorF64) {
                subdivideF64()
            } else {
                val endPointF64 = projectStrokePointF64(
                    projectionF64,
                    intervalF64.primitiveF64.pointAtF64(endParameterF64),
                )
                destinationF64 += PathFillSegmentF64.LineTo(endPointF64)
            }
        }

        PathStrokeProjectionIntervalResultF64.HorizonCrossing ->
            throw PathStrokeProjectionAbort(PathStrokeInvalidSceneReason.ProjectionHorizonCrossing)

        PathStrokeProjectionIntervalResultF64.NonFinite,
        PathStrokeProjectionIntervalResultF64.Unbounded,
        -> throw PathStrokeProjectionAbort()
    }
}

private fun projectStrokePointF64(
    projectionF64: PathStrokeProjectionF64,
    pointF64: Point2F64,
): Point2F64 = when (val resultF64 = projectionF64.projectPointF64(pointF64)) {
    is PathStrokeProjectionPointResultF64.Ready -> resultF64.pointF64.takeIf(Point2F64::isFinite)
        ?: throw PathStrokeProjectionAbort()
    PathStrokeProjectionPointResultF64.NonFinite -> throw PathStrokeProjectionAbort()
}

private fun PathFillGeometryF32.strokeConservativeBoundsF32(): RectF32 {
    var leftF32 = Float.POSITIVE_INFINITY
    var topF32 = Float.POSITIVE_INFINITY
    var rightF32 = Float.NEGATIVE_INFINITY
    var bottomF32 = Float.NEGATIVE_INFINITY

    fun include(pointF32: Point2F32) {
        leftF32 = minOf(leftF32, pointF32.x)
        topF32 = minOf(topF32, pointF32.y)
        rightF32 = maxOf(rightF32, pointF32.x)
        bottomF32 = maxOf(bottomF32, pointF32.y)
    }

    copyDirectTriangleF32OrNull()?.copyVerticesF32()?.let { verticesF32 ->
        for (offsetI32 in verticesF32.indices step 2) include(Point2F32(verticesF32[offsetI32], verticesF32[offsetI32 + 1]))
    } ?: copyStencilEdgeFanF32OrNull()?.copyVerticesF32()?.let { verticesF32 ->
        for (offsetI32 in verticesF32.indices step 6) {
            include(Point2F32(verticesF32[offsetI32 + 2], verticesF32[offsetI32 + 3]))
            include(Point2F32(verticesF32[offsetI32 + 4], verticesF32[offsetI32 + 5]))
        }
    }
    if (!leftF32.isFinite() || !topF32.isFinite() || !rightF32.isFinite() || !bottomF32.isFinite()) {
        throw PathStrokeOutlineInvalidAbort()
    }
    return RectF32(
        canonicalStrokeF32(leftF32),
        canonicalStrokeF32(topF32),
        canonicalStrokeF32(rightF32),
        canonicalStrokeF32(bottomF32),
    )
}

private fun isFiniteStrokePipelineInputSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo -> segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() && segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}

private fun checkedStrokeGeometryPublicationBytesI64(fillSnapshotByteCountI64: Long): Long {
    if (fillSnapshotByteCountI64 < 0L || fillSnapshotByteCountI64 > Long.MAX_VALUE - 16L) {
        throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
    }
    return fillSnapshotByteCountI64 + 16L
}

private fun canonicalStrokeF32(valueF32: Float): Float = if (valueF32 == 0f) 0f else valueF32
