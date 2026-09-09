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
            finiteInteriorF64.differenceDeviceOutlineF64(
                requireNotNull(deviceStrokeOutlineF64),
                styleF64,
                policyF64,
                ledgerI64,
            )
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
    styleF64: PathStrokeStyleF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathFillInputF64 {
    val normalizedOutlineF64 = deviceStrokeOutlineF64.withFiniteFillRule(ledgerI64)
    val limitsI32 = inverseTopologyLimitsI32(policyF64)
    val debitI64 = PathTopologyWorkDebitI64(ledgerI64::debitTopologyBeforeEmissionI64)
    val differenceF32 = if (styleF64.widthF64 == PathStrokeWidthF64.Hairline) {
        normalizedOutlineF64.copyTwoClosedStrokeBoundariesF64(ledgerI64)?.let { (outerF64, innerF64) ->
            val finiteTopologyF32 = toInverseTopologyPathF32(ledgerI64)
            val outsideOutlineF32 = PathOpsF32.op(
                finiteTopologyF32, outerF64.toInverseTopologyPathF32(ledgerI64), PathBooleanOp.DIFFERENCE,
                limitsI32, debitI64,
            )
            val insideInnerF32 = PathOpsF32.op(
                finiteTopologyF32, innerF64.toInverseTopologyPathF32(ledgerI64), PathBooleanOp.INTERSECT,
                limitsI32, debitI64,
            )
            PathOpsF32.op(outsideOutlineF32, insideInnerF32, PathBooleanOp.UNION, limitsI32, debitI64)
        }
    } else {
        null
    } ?: PathOpsF32.op(
        first = toInverseTopologyPathF32(ledgerI64),
        second = normalizedOutlineF64.toInverseTopologyPathF32(ledgerI64),
        op = PathBooleanOp.DIFFERENCE,
        limits = limitsI32,
        topologyWorkDebitI64 = debitI64,
    )
    return PathFillInputF64.fromPathF32(differenceF32, ledgerI64::debitBeforeEmissionI64)
}

private fun inverseTopologyLimitsI32(policyF64: PathStrokePolicyF64): PathOpsLimitsI32 = PathOpsLimitsI32(
    maxSubdivisionDepth = policyF64.limitsI32.maxSubdivisionDepthI32.coerceAtLeast(1),
    maxFlattenedEdgesPerOperand = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
    maxIntersections = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
    maxVertices = policyF64.limitsI32.maxEmittedVertexCountPerPathI32,
    maxHalfEdges = policyF64.limitsI32.maxEmittedIndexCountPerPathI32,
    maxCandidateProbes = policyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32,
)

/** Decomposes a two-boundary closed stroke into its exact outer and inner operands. */
private fun PathFillInputF64.copyTwoClosedStrokeBoundariesF64(
    ledgerI64: PathStrokeWorkLedgerI64,
): Pair<PathFillInputF64, PathFillInputF64>? {
    var completedContourCountI32 = 0
    var currentStartI32 = -1
    var firstStartI32 = -1
    var firstEndI32 = -1
    var secondStartI32 = -1
    var secondEndI32 = -1
    for (indexI32 in 0 until segmentCountI32) when (val segmentF64 = segmentAtI32(indexI32)) {
        is PathFillSegmentF64.MoveTo -> {
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            if (currentStartI32 >= 0 || completedContourCountI32 >= 2) return null
            currentStartI32 = indexI32
        }
        is PathFillSegmentF64.LineTo -> {
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            if (currentStartI32 < 0) return null
        }
        PathFillSegmentF64.Close -> {
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            if (currentStartI32 < 0) return null
            when (completedContourCountI32) {
                0 -> {
                    firstStartI32 = currentStartI32
                    firstEndI32 = indexI32 + 1
                }
                1 -> {
                    secondStartI32 = currentStartI32
                    secondEndI32 = indexI32 + 1
                }
            }
            completedContourCountI32 += 1
            currentStartI32 = -1
        }
        else -> return null
    }
    if (completedContourCountI32 != 2 || currentStartI32 >= 0 || firstStartI32 < 0 || secondStartI32 < 0) return null

    val firstPointsF64 = copyClosedContourPointsF64(firstStartI32, firstEndI32, ledgerI64) ?: return null
    val secondPointsF64 = copyClosedContourPointsF64(secondStartI32, secondEndI32, ledgerI64) ?: return null
    val firstOrientationI32 = firstPointsF64.signedClosedAreaSignF64(ledgerI64)
    val secondOrientationI32 = secondPointsF64.signedClosedAreaSignF64(ledgerI64)
    if (firstOrientationI32 == 0 || secondOrientationI32 == 0 || firstOrientationI32 == secondOrientationI32) return null
    if (!firstPointsF64.isSimpleClosedContourF64(ledgerI64) || !secondPointsF64.isSimpleClosedContourF64(ledgerI64)) return null
    if (firstPointsF64.touchesBoundaryF64(secondPointsF64, ledgerI64)) return null
    val firstContainsSecond = firstPointsF64.strictlyContainsContourF64(secondPointsF64, ledgerI64)
    val secondContainsFirst = secondPointsF64.strictlyContainsContourF64(firstPointsF64, ledgerI64)
    val outerStartI32: Int
    val outerEndI32: Int
    val innerStartI32: Int
    val innerEndI32: Int
    when {
        firstContainsSecond && !secondContainsFirst -> {
            outerStartI32 = firstStartI32
            outerEndI32 = firstEndI32
            innerStartI32 = secondStartI32
            innerEndI32 = secondEndI32
        }
        secondContainsFirst && !firstContainsSecond -> {
            outerStartI32 = secondStartI32
            outerEndI32 = secondEndI32
            innerStartI32 = firstStartI32
            innerEndI32 = firstEndI32
        }
        else -> return null
    }

    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 32L))
    fun copyRangeF64(startI32: Int, endI32: Int): PathFillInputF64 {
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        val segmentsF64 = mutableListOf<PathFillSegmentF64>()
        for (indexI32 in startI32 until endI32) {
            ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
            segmentsF64 += segmentAtI32(indexI32)
        }
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        return PathFillInputF64.of(FillRule.WINDING, segmentsF64)
    }
    return copyRangeF64(outerStartI32, outerEndI32) to copyRangeF64(innerStartI32, innerEndI32)
}

private fun PathFillInputF64.copyClosedContourPointsF64(
    startI32: Int,
    endI32: Int,
    ledgerI64: PathStrokeWorkLedgerI64,
): List<Point2F64>? {
    val vertexCountI32 = endI32 - startI32 - 1
    if (vertexCountI32 < 3 || vertexCountI32 == Int.MAX_VALUE) return null
    ledgerI64.debitBeforeEmissionI64(
        PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L + (vertexCountI32.toLong() + 1L) * 16L),
    )
    val pointsF64 = ArrayList<Point2F64>(vertexCountI32 + 1)
    for (indexI32 in startI32 until endI32 - 1) {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val pointF64 = when (val segmentF64 = segmentAtI32(indexI32)) {
            is PathFillSegmentF64.MoveTo -> segmentF64.point
            is PathFillSegmentF64.LineTo -> segmentF64.point
            else -> return null
        }
        pointsF64 += pointF64
    }
    pointsF64 += pointsF64.first()
    return pointsF64
}

private fun List<Point2F64>.signedClosedAreaSignF64(ledgerI64: PathStrokeWorkLedgerI64): Int {
    for (edgeIndexI32 in 0 until size - 1) ledgerI64.debitTopologyBeforeEmissionI64(1L)
    return signedAreaSignF64(this)
}

private fun List<Point2F64>.isSimpleClosedContourF64(ledgerI64: PathStrokeWorkLedgerI64): Boolean {
    val edgeCountI32 = size - 1
    for (firstEdgeI32 in 0 until edgeCountI32) {
        if (sameInverseTopologicalPointF64(this[firstEdgeI32], this[firstEdgeI32 + 1])) return false
        for (secondEdgeI32 in firstEdgeI32 + 1 until edgeCountI32) {
            val areAdjacent = secondEdgeI32 == firstEdgeI32 + 1 ||
                (firstEdgeI32 == 0 && secondEdgeI32 == edgeCountI32 - 1)
            if (areAdjacent) continue
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            if (segmentsTouchF64(
                    this[firstEdgeI32], this[firstEdgeI32 + 1], this[secondEdgeI32], this[secondEdgeI32 + 1],
                )
            ) return false
        }
    }
    return true
}

private fun List<Point2F64>.touchesBoundaryF64(
    otherF64: List<Point2F64>,
    ledgerI64: PathStrokeWorkLedgerI64,
): Boolean {
    for (firstEdgeI32 in 0 until size - 1) {
        for (secondEdgeI32 in 0 until otherF64.size - 1) {
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            if (segmentsTouchF64(
                    this[firstEdgeI32], this[firstEdgeI32 + 1], otherF64[secondEdgeI32], otherF64[secondEdgeI32 + 1],
                )
            ) return true
        }
    }
    return false
}

private fun List<Point2F64>.strictlyContainsContourF64(
    innerF64: List<Point2F64>,
    ledgerI64: PathStrokeWorkLedgerI64,
): Boolean {
    for (pointIndexI32 in 0 until innerF64.size - 1) {
        if (!strictlyContainsPointF64(innerF64[pointIndexI32], ledgerI64)) return false
    }
    return true
}

private fun List<Point2F64>.strictlyContainsPointF64(
    pointF64: Point2F64,
    ledgerI64: PathStrokeWorkLedgerI64,
): Boolean {
    var windingI32 = 0
    for (edgeIndexI32 in 0 until size - 1) {
        val startF64 = this[edgeIndexI32]
        val endF64 = this[edgeIndexI32 + 1]
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        if (PathPredicatesF64.onSegment(pointF64, startF64, endF64)) return false
        val startAtOrBelow = startF64.y <= pointF64.y
        val endAbove = endF64.y > pointF64.y
        val endAtOrBelow = endF64.y <= pointF64.y
        if (startAtOrBelow && endAbove && OrientationPredicateF64.sign(startF64, endF64, pointF64) > 0) {
            windingI32 += 1
        } else if (!startAtOrBelow && endAtOrBelow && OrientationPredicateF64.sign(startF64, endF64, pointF64) < 0) {
            windingI32 -= 1
        }
    }
    return windingI32 != 0
}

private fun segmentsTouchF64(
    firstStartF64: Point2F64,
    firstEndF64: Point2F64,
    secondStartF64: Point2F64,
    secondEndF64: Point2F64,
): Boolean {
    val firstStartSideI32 = OrientationPredicateF64.sign(firstStartF64, firstEndF64, secondStartF64)
    val firstEndSideI32 = OrientationPredicateF64.sign(firstStartF64, firstEndF64, secondEndF64)
    val secondStartSideI32 = OrientationPredicateF64.sign(secondStartF64, secondEndF64, firstStartF64)
    val secondEndSideI32 = OrientationPredicateF64.sign(secondStartF64, secondEndF64, firstEndF64)
    return (firstStartSideI32 == 0 && PathPredicatesF64.onSegment(secondStartF64, firstStartF64, firstEndF64)) ||
        (firstEndSideI32 == 0 && PathPredicatesF64.onSegment(secondEndF64, firstStartF64, firstEndF64)) ||
        (secondStartSideI32 == 0 && PathPredicatesF64.onSegment(firstStartF64, secondStartF64, secondEndF64)) ||
        (secondEndSideI32 == 0 && PathPredicatesF64.onSegment(firstEndF64, secondStartF64, secondEndF64)) ||
        (firstStartSideI32 != firstEndSideI32 && secondStartSideI32 != secondEndSideI32)
}

private fun sameInverseTopologicalPointF64(firstF64: Point2F64, secondF64: Point2F64): Boolean =
    firstF64.x == secondF64.x && firstF64.y == secondF64.y

private fun PathFillInputF64.toInverseTopologyPathF32(ledgerI64: PathStrokeWorkLedgerI64): PathF32 {
    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val builderF32 = PathBuilder(fillRule)
    forEach { segmentF64 ->
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
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
    val snapshotByteCostI64 = try {
        geometryF32.snapshotByteCostI64
    } catch (_: IllegalStateException) {
        throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
    }
    if (snapshotByteCostI64 > Long.MAX_VALUE - inversePublicationOverheadBytesI64) {
        throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
    }
    return snapshotByteCostI64 + inversePublicationOverheadBytesI64
}

/** The inverse wrapper retains its own domain and interior-coverage object beside the copied payload. */
private const val inversePublicationOverheadBytesI64: Long = 32L

private fun PathFillInputF64.inverseInputSnapshotBytesI64(): Long =
    16L + segmentCountI32.toLong() * 16L

private fun IllegalStateException.isInverseTopologyLimit(): Boolean = message in setOf(
    "path-candidate-limit", "path-intersection-limit", "path-vertex-limit", "path-half-edge-limit",
    "path-flattening-limit", "path-flattening-convergence", "path-f32-projection-collapse",
)
