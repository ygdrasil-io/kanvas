package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Immutable source-centerline spans, grouped by their drawable contour. */
public class PathStrokeCenterlineF64 private constructor(
    contoursF64: List<List<PathStrokePrimitiveSpanF64>>,
    closedContours: BooleanArray,
) {
    private val contoursSnapshotF64: List<List<PathStrokePrimitiveSpanF64>> = contoursF64.map { it.toList() }
    private val closedContoursSnapshot: BooleanArray = closedContours.copyOf()

    init {
        require(contoursSnapshotF64.size == closedContoursSnapshot.size)
    }

    public val contourCountI32: Int
        get() = contoursSnapshotF64.size

    public fun copyContourSpansF64(indexI32: Int): List<PathStrokePrimitiveSpanF64> =
        contoursSnapshotF64[indexI32].toList()

    /** Internal read-only view that avoids an unbudgeted defensive-copy round trip. */
    internal fun contourSpansViewF64(indexI32: Int): List<PathStrokePrimitiveSpanF64> =
        contoursSnapshotF64[indexI32]

    public fun isContourClosed(indexI32: Int): Boolean = closedContoursSnapshot[indexI32]

    internal companion object {
        internal fun of(
            contoursF64: List<List<PathStrokePrimitiveSpanF64>>,
            closedContours: BooleanArray,
        ): PathStrokeCenterlineF64 = PathStrokeCenterlineF64(contoursF64, closedContours)
    }
}

/** Public outcome of bounded dash preparation before any stroke outline is emitted. */
public sealed interface PathStrokeCenterlinePreparationResult {
    public data class Ready(
        public val centerlineF64: PathStrokeCenterlineF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeCenterlinePreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeCenterlinePreparationResult

    public data class InvalidScene(
        public val reason: PathStrokeInvalidSceneReason,
    ) : PathStrokeCenterlinePreparationResult

    public data class ResourceLimitExceeded(
        public val reason: PathStrokeResourceLimitReason,
    ) : PathStrokeCenterlinePreparationResult
}

/**
 * Converts source path segments into bounded parametric centerline spans.
 *
 * Dash cutting is resolved in source arclength; no centerline polyline or
 * renderer-owned data is materialized at this stage.
 */
public fun preparePathStrokeCenterlinesF64(
    inputF64: PathFillInputF64,
    dashF64: PathStrokeDashF64?,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeCenterlinePreparationResult {
    if (!inputF64.all(::isFiniteStrokeInputSegmentF64)) {
        return PathStrokeCenterlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }

    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64 = PathStrokeWorkUsageI64(),
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            limitsI32 = policyF64.limitsI32,
            limitsI64 = policyF64.limitsI64,
        )
        val centerlineF64 = preparePathStrokeCenterlinesF64(inputF64, dashF64, policyF64, ledgerI64)
        val pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64()
        val frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64()
        if (centerlineF64 == null) {
            PathStrokeCenterlinePreparationResult.Empty(pathWorkUsageI64, frameWorkUsageAfterI64)
        } else {
            PathStrokeCenterlinePreparationResult.Ready(centerlineF64, pathWorkUsageI64, frameWorkUsageAfterI64)
        }
    } catch (_: PathStrokeInvalidInputAbort) {
        PathStrokeCenterlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    } catch (abort: PathStrokeResourceLimitAbort) {
        PathStrokeCenterlinePreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

/** Internal stage overload used by later stroke preparation stages sharing one ledger. */
internal fun preparePathStrokeCenterlinesF64(
    inputF64: PathFillInputF64,
    dashF64: PathStrokeDashF64?,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeCenterlineF64? = PathStrokeDashPreparerF64(inputF64, dashF64, policyF64, ledgerI64).prepare()

internal class PathStrokeInvalidInputAbort : RuntimeException()

private data class SourceStrokeContourF64(
    val primitivesF64: List<PathStrokePrimitiveF64>,
    val closed: Boolean,
)

private data class StrokeArcLengthLeafF64(
    val startParameterF64: Double,
    val endParameterF64: Double,
    val lengthF64: Double,
)

private data class StrokeArcLengthBoundsF64(
    val lowerLengthF64: Double,
    val upperLengthF64: Double,
    val isExactlyLinear: Boolean,
)

private data class DashedStrokeRunF64(
    val spansF64: List<PathStrokePrimitiveSpanF64>,
    val startDistanceF64: Double,
    val endDistanceF64: Double,
    val continuesThroughClosure: Boolean,
)

private data class MeasuredStrokePrimitiveF64(
    val primitiveF64: PathStrokePrimitiveF64,
    val leavesF64: List<StrokeArcLengthLeafF64>,
    val lengthF64: Double,
) {
    fun parameterAtDistanceF64(distanceF64: Double): Double {
        if (distanceF64 <= 0.0) return 0.0
        if (distanceF64 >= lengthF64) return 1.0

        var coveredF64 = 0.0
        leavesF64.forEachIndexed { indexI32, leafF64 ->
            val nextCoveredF64 = coveredF64 + leafF64.lengthF64
            if (distanceF64 <= nextCoveredF64 || indexI32 == leavesF64.lastIndex) {
                if (leafF64.lengthF64 == 0.0) return leafF64.startParameterF64
                val fractionF64 = ((distanceF64 - coveredF64) / leafF64.lengthF64).coerceIn(0.0, 1.0)
                return leafF64.startParameterF64 +
                    (leafF64.endParameterF64 - leafF64.startParameterF64) * fractionF64
            }
            coveredF64 = nextCoveredF64
        }
        return 1.0
    }
}

private class PathStrokeDashPreparerF64(
    private val inputF64: PathFillInputF64,
    private val dashF64: PathStrokeDashF64?,
    private val policyF64: PathStrokePolicyF64,
    private val ledgerI64: PathStrokeWorkLedgerI64,
) {
    private val retainedContoursF64 = mutableListOf<List<PathStrokePrimitiveSpanF64>>()
    private val retainedClosedContours = mutableListOf<Boolean>()
    private var sourcePrimitivesF64 = mutableListOf<PathStrokePrimitiveF64>()
    private var currentPointF64: Point2F64? = null
    private var contourStartF64: Point2F64? = null
    private var contourClosed: Boolean = false

    fun prepare(): PathStrokeCenterlineF64? {
        inputF64.forEach { segmentF64 ->
            when (segmentF64) {
                is PathFillSegmentF64.MoveTo -> {
                    finishSourceContour()
                    beginSourceContour(segmentF64.point)
                }

                is PathFillSegmentF64.LineTo -> {
                    ensureSourceContour()
                    contourClosed = false
                    appendPrimitive(PathStrokeLinePrimitiveF64(requireNotNull(currentPointF64), segmentF64.point))
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.QuadTo -> {
                    ensureSourceContour()
                    contourClosed = false
                    appendPrimitive(
                        PathStrokeQuadPrimitiveF64(
                            requireNotNull(currentPointF64),
                            segmentF64.control,
                            segmentF64.point,
                        ),
                    )
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.CubicTo -> {
                    ensureSourceContour()
                    contourClosed = false
                    appendPrimitive(
                        PathStrokeCubicPrimitiveF64(
                            requireNotNull(currentPointF64),
                            segmentF64.control1,
                            segmentF64.control2,
                            segmentF64.point,
                        ),
                    )
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.ArcTo -> {
                    ensureSourceContour()
                    contourClosed = false
                    val startF64 = requireNotNull(currentPointF64)
                    val arcF64 = arcCenterF64(
                        ArcEndpointF64(
                            start = startF64,
                            end = segmentF64.point,
                            radius = segmentF64.radius,
                            xAxisRotationDegrees = segmentF64.xAxisRotationDegreesF64,
                            largeArc = segmentF64.largeArc,
                            sweep = segmentF64.sweep,
                        ),
                    )
                    appendPrimitive(PathStrokeSvgArcPrimitiveF64(startF64, segmentF64.point, arcF64))
                    currentPointF64 = segmentF64.point
                }

                PathFillSegmentF64.Close -> closeSourceContour()
            }
        }
        finishSourceContour()

        if (retainedContoursF64.isEmpty()) return null
        return PathStrokeCenterlineF64.of(retainedContoursF64, retainedClosedContours.toBooleanArray())
    }

    private fun ensureSourceContour() {
        if (currentPointF64 == null) beginSourceContour(Point2F64.Origin)
    }

    private fun beginSourceContour(startF64: Point2F64) {
        sourcePrimitivesF64 = mutableListOf()
        currentPointF64 = startF64
        contourStartF64 = startF64
        contourClosed = false
    }

    private fun closeSourceContour() {
        val currentF64 = currentPointF64 ?: return
        val startF64 = contourStartF64 ?: return
        if (!contourClosed && !sameStrokePointF64(currentF64, startF64)) {
            appendPrimitive(PathStrokeLinePrimitiveF64(currentF64, startF64))
        }
        currentPointF64 = startF64
        contourClosed = true
    }

    private fun appendPrimitive(primitiveF64: PathStrokePrimitiveF64) {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        sourcePrimitivesF64 += primitiveF64
    }

    private fun finishSourceContour() {
        val currentF64 = currentPointF64
        if (currentF64 == null) return

        val sourceContourF64 = SourceStrokeContourF64(sourcePrimitivesF64.toList(), contourClosed)
        val primitiveCountI32 = sourceContourF64.primitivesF64.size
        val measurementErrorPerPrimitiveF64 = if (primitiveCountI32 == 0) {
            0.0
        } else {
            policyF64.maximumDashArcLengthErrorF64 / primitiveCountI32
        }
        val measuredPrimitivesF64 = sourceContourF64.primitivesF64.mapNotNull { primitiveF64 ->
            measurePrimitiveF64(primitiveF64, measurementErrorPerPrimitiveF64)
        }
        if (measuredPrimitivesF64.isNotEmpty()) {
            if (dashF64 == null) {
                retainUndashedContour(measuredPrimitivesF64, sourceContourF64.closed)
            } else {
                retainDashedContour(measuredPrimitivesF64, sourceContourF64.closed, dashF64)
            }
        }
        currentPointF64 = null
        contourStartF64 = null
        contourClosed = false
        sourcePrimitivesF64 = mutableListOf()
    }

    private fun measurePrimitiveF64(
        primitiveF64: PathStrokePrimitiveF64,
        measurementErrorBudgetF64: Double,
    ): MeasuredStrokePrimitiveF64? {
        val leavesF64 = mutableListOf<StrokeArcLengthLeafF64>()
        val startF64 = evaluatedPointF64(primitiveF64, 0.0)
        val endF64 = evaluatedPointF64(primitiveF64, 1.0)
        measurePrimitiveIntervalF64(
            primitiveF64,
            0.0,
            startF64,
            1.0,
            endF64,
            0,
            measurementErrorBudgetF64,
            leavesF64,
        )
        var lengthF64 = 0.0
        leavesF64.forEach { leafF64 ->
            lengthF64 += leafF64.lengthF64
            if (!lengthF64.isFinite()) throw PathStrokeInvalidInputAbort()
        }
        return if (lengthF64 == 0.0) null else MeasuredStrokePrimitiveF64(primitiveF64, leavesF64.toList(), lengthF64)
    }

    private fun measurePrimitiveIntervalF64(
        primitiveF64: PathStrokePrimitiveF64,
        startParameterF64: Double,
        startF64: Point2F64,
        endParameterF64: Double,
        endF64: Point2F64,
        depthI32: Int,
        measurementErrorBudgetF64: Double,
        leavesF64: MutableList<StrokeArcLengthLeafF64>,
    ) {
        val boundsF64 = arclengthBoundsF64(primitiveF64, startParameterF64, startF64, endParameterF64, endF64)
        val parameterWidthF64 = endParameterF64 - startParameterF64
        // This contour shares one measurement budget across all of its source
        // primitives. The sum of the leaf-bound widths is at most one quarter
        // of the public tolerance, so midpoint measurement error is at most an
        // eighth even for a dash cut that crosses several primitives. The
        // separate leaf-length bound limits local inversion without
        // materializing a centerline polyline.
        val intervalErrorBudgetF64 = measurementErrorBudgetF64 * parameterWidthF64 * 0.25
        val maximumLeafLengthF64 = policyF64.maximumDashArcLengthErrorF64 * 0.25
        if (boundsF64.isExactlyLinear ||
            (boundsF64.upperLengthF64 - boundsF64.lowerLengthF64 <= intervalErrorBudgetF64 &&
                boundsF64.upperLengthF64 <= maximumLeafLengthF64)
        ) {
            val lengthF64 = if (boundsF64.isExactlyLinear) {
                boundsF64.lowerLengthF64
            } else {
                (boundsF64.lowerLengthF64 + boundsF64.upperLengthF64) * 0.5
            }
            leavesF64 += StrokeArcLengthLeafF64(startParameterF64, endParameterF64, lengthF64)
            return
        }
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.FlatteningDidNotConverge)
        }
        val middleParameterF64 = (startParameterF64 + endParameterF64) * 0.5
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val middleF64 = evaluatedPointF64(primitiveF64, middleParameterF64)
        measurePrimitiveIntervalF64(
            primitiveF64,
            startParameterF64,
            startF64,
            middleParameterF64,
            middleF64,
            depthI32 + 1,
            measurementErrorBudgetF64,
            leavesF64,
        )
        measurePrimitiveIntervalF64(
            primitiveF64,
            middleParameterF64,
            middleF64,
            endParameterF64,
            endF64,
            depthI32 + 1,
            measurementErrorBudgetF64,
            leavesF64,
        )
    }

    private fun arclengthBoundsF64(
        primitiveF64: PathStrokePrimitiveF64,
        startParameterF64: Double,
        startF64: Point2F64,
        endParameterF64: Double,
        endF64: Point2F64,
    ): StrokeArcLengthBoundsF64 {
        val lowerLengthF64 = strokeDistanceF64(startF64, endF64)
        val maximumSpeedF64 = maximumDerivativeSpeedF64(primitiveF64, startParameterF64, endParameterF64)
        val upperLengthF64 = max(lowerLengthF64, maximumSpeedF64 * (endParameterF64 - startParameterF64))
        if (!upperLengthF64.isFinite()) throw PathStrokeInvalidInputAbort()
        return StrokeArcLengthBoundsF64(
            lowerLengthF64 = lowerLengthF64,
            upperLengthF64 = upperLengthF64,
            isExactlyLinear = primitiveF64 is PathStrokeLinePrimitiveF64 ||
                (primitiveF64 is PathStrokeSvgArcPrimitiveF64 && primitiveF64.arcF64 == null),
        )
    }

    private fun maximumDerivativeSpeedF64(
        primitiveF64: PathStrokePrimitiveF64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): Double {
        val speedF64 = when (primitiveF64) {
            is PathStrokeLinePrimitiveF64 -> strokeVectorLengthF64(primitiveF64.endF64 - primitiveF64.startF64)

            is PathStrokeQuadPrimitiveF64 -> max(
                evaluatedDerivativeLengthF64(primitiveF64, startParameterF64),
                evaluatedDerivativeLengthF64(primitiveF64, endParameterF64),
            )

            is PathStrokeCubicPrimitiveF64 -> {
                ledgerI64.debitTopologyBeforeEmissionI64(1L)
                val controlsF64 = cubicDerivativeControlsOnIntervalF64(
                    primitiveF64,
                    startParameterF64,
                    endParameterF64,
                )
                maxOf(
                    strokeVectorLengthF64(controlsF64.first),
                    strokeVectorLengthF64(controlsF64.second),
                    strokeVectorLengthF64(controlsF64.third),
                )
            }

            is PathStrokeSvgArcPrimitiveF64 -> primitiveF64.arcF64?.let { arcF64 ->
                abs(arcF64.sweepAngle) * max(arcF64.radiusX, arcF64.radiusY)
            } ?: strokeVectorLengthF64(primitiveF64.endF64 - primitiveF64.startF64)
        }
        if (!speedF64.isFinite()) throw PathStrokeInvalidInputAbort()
        return speedF64
    }

    private fun evaluatedDerivativeLengthF64(
        primitiveF64: PathStrokePrimitiveF64,
        parameterF64: Double,
    ): Double {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        return strokeVectorLengthF64(primitiveF64.derivativeAtF64(parameterF64))
    }

    private fun evaluatedPointF64(primitiveF64: PathStrokePrimitiveF64, parameterF64: Double): Point2F64 {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val pointF64 = primitiveF64.pointAtF64(parameterF64)
        if (!pointF64.isFinite()) throw PathStrokeInvalidInputAbort()
        return pointF64
    }

    private fun retainUndashedContour(
        measuredPrimitivesF64: List<MeasuredStrokePrimitiveF64>,
        closed: Boolean,
    ) {
        val spansF64 = mutableListOf<PathStrokePrimitiveSpanF64>()
        measuredPrimitivesF64.forEach { measuredF64 ->
            appendSpanF64(spansF64, measuredF64, 0.0, measuredF64.lengthF64)
        }
        retainContour(spansF64, closed)
    }

    private fun retainDashedContour(
        measuredPrimitivesF64: List<MeasuredStrokePrimitiveF64>,
        sourceClosed: Boolean,
        dashF64: PathStrokeDashF64,
    ) {
        var totalLengthF64 = 0.0
        measuredPrimitivesF64.forEach { measuredF64 ->
            totalLengthF64 += measuredF64.lengthF64
            if (!totalLengthF64.isFinite()) throw PathStrokeInvalidInputAbort()
        }
        if (totalLengthF64 == 0.0) return

        val cursorF64 = DashCursorF64(dashF64)
        val runsF64 = mutableListOf<DashedStrokeRunF64>()
        var distanceF64 = 0.0
        while (distanceF64 < totalLengthF64) {
            if (cursorF64.remainingLengthF64 == 0.0) {
                cursorF64.advance()
                continue
            }
            val nextDistanceF64 = min(totalLengthF64, distanceF64 + cursorF64.remainingLengthF64)
            if (nextDistanceF64 <= distanceF64) break
            if (cursorF64.isOn) {
                val spansF64 = mutableListOf<PathStrokePrimitiveSpanF64>()
                appendDashedRangeF64(measuredPrimitivesF64, distanceF64, nextDistanceF64, spansF64)
                if (spansF64.isNotEmpty()) {
                    ledgerI64.debitTopologyBeforeEmissionI64(1L)
                    runsF64 += DashedStrokeRunF64(
                        spansF64 = spansF64,
                        startDistanceF64 = distanceF64,
                        endDistanceF64 = nextDistanceF64,
                        continuesThroughClosure = nextDistanceF64 == totalLengthF64 &&
                            nextDistanceF64 < distanceF64 + cursorF64.remainingLengthF64,
                    )
                }
            }
            val consumedLengthF64 = nextDistanceF64 - distanceF64
            distanceF64 = nextDistanceF64
            cursorF64.consume(consumedLengthF64)
            if (cursorF64.remainingLengthF64 == 0.0) cursorF64.advance()
        }
        retainDashedRunsF64(runsF64, sourceClosed, totalLengthF64)
    }

    private fun retainDashedRunsF64(
        runsF64: List<DashedStrokeRunF64>,
        sourceClosed: Boolean,
        totalLengthF64: Double,
    ) {
        val firstRunF64 = runsF64.firstOrNull() ?: return
        val lastRunF64 = runsF64.last()
        val joinsClosure = sourceClosed && runsF64.size > 1 &&
            firstRunF64.startDistanceF64 == 0.0 && lastRunF64.endDistanceF64 == totalLengthF64 &&
            lastRunF64.continuesThroughClosure
        if (joinsClosure) {
            retainContour(lastRunF64.spansF64 + firstRunF64.spansF64, closed = false)
            for (indexI32 in 1 until runsF64.lastIndex) {
                retainContour(runsF64[indexI32].spansF64, closed = false)
            }
            return
        }
        runsF64.forEach { runF64 ->
            retainContour(
                runF64.spansF64,
                closed = sourceClosed && runF64.startDistanceF64 == 0.0 && runF64.endDistanceF64 == totalLengthF64,
            )
        }
    }

    private fun appendDashedRangeF64(
        measuredPrimitivesF64: List<MeasuredStrokePrimitiveF64>,
        startDistanceF64: Double,
        endDistanceF64: Double,
        destinationF64: MutableList<PathStrokePrimitiveSpanF64>,
    ) {
        var primitiveStartDistanceF64 = 0.0
        measuredPrimitivesF64.forEach { measuredF64 ->
            val primitiveEndDistanceF64 = primitiveStartDistanceF64 + measuredF64.lengthF64
            val overlapStartDistanceF64 = max(startDistanceF64, primitiveStartDistanceF64)
            val overlapEndDistanceF64 = min(endDistanceF64, primitiveEndDistanceF64)
            if (overlapStartDistanceF64 < overlapEndDistanceF64) {
                appendSpanF64(
                    destinationF64,
                    measuredF64,
                    overlapStartDistanceF64 - primitiveStartDistanceF64,
                    overlapEndDistanceF64 - primitiveStartDistanceF64,
                )
            }
            primitiveStartDistanceF64 = primitiveEndDistanceF64
        }
    }

    private fun appendSpanF64(
        destinationF64: MutableList<PathStrokePrimitiveSpanF64>,
        measuredF64: MeasuredStrokePrimitiveF64,
        startDistanceF64: Double,
        endDistanceF64: Double,
    ) {
        val startParameterF64 = measuredF64.parameterAtDistanceF64(startDistanceF64)
        val endParameterF64 = measuredF64.parameterAtDistanceF64(endDistanceF64)
        if (startParameterF64 >= endParameterF64) return
        ledgerI64.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(
                attemptedGeometryUnitCountI64 = 1L,
                snapshotByteCountI64 = 32L,
            ),
        )
        destinationF64 += PathStrokePrimitiveSpanF64(measuredF64.primitiveF64, startParameterF64, endParameterF64)
    }

    private fun retainContour(spansF64: List<PathStrokePrimitiveSpanF64>, closed: Boolean) {
        if (spansF64.isEmpty()) return
        retainedContoursF64 += spansF64.toList()
        retainedClosedContours += closed
    }
}

private class DashCursorF64(dashF64: PathStrokeDashF64) {
    private val intervalsF64: DoubleArray = dashF64.copyIntervalsF64()
    private var indexI32: Int = 0

    var remainingLengthF64: Double = 0.0
        private set

    val isOn: Boolean
        get() = indexI32 % 2 == 0

    init {
        var periodF64 = 0.0
        intervalsF64.forEach { intervalF64 -> periodF64 += intervalF64 }
        var phaseF64 = normalizedDashPhaseF64(dashF64.phaseF64, periodF64)
        var initialized = false
        while (!initialized) {
            val intervalF64 = intervalsF64[indexI32]
            if (intervalF64 == 0.0) {
                indexI32 = (indexI32 + 1) % intervalsF64.size
            } else if (phaseF64 < intervalF64) {
                remainingLengthF64 = intervalF64 - phaseF64
                initialized = true
            } else {
                phaseF64 -= intervalF64
                indexI32 = (indexI32 + 1) % intervalsF64.size
            }
        }
    }

    fun consume(lengthF64: Double) {
        remainingLengthF64 = max(0.0, remainingLengthF64 - lengthF64)
    }

    fun advance() {
        indexI32 = (indexI32 + 1) % intervalsF64.size
        remainingLengthF64 = intervalsF64[indexI32]
    }
}

private fun normalizedDashPhaseF64(phaseF64: Double, periodF64: Double): Double =
    ((phaseF64 % periodF64) + periodF64) % periodF64

private fun cubicDerivativeControlsOnIntervalF64(
    primitiveF64: PathStrokeCubicPrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): Triple<Vector2F64, Vector2F64, Vector2F64> {
    val firstF64 = Vector2F64(
        3.0 * (primitiveF64.control1F64.x - primitiveF64.startF64.x),
        3.0 * (primitiveF64.control1F64.y - primitiveF64.startF64.y),
    )
    val controlF64 = Vector2F64(
        3.0 * (primitiveF64.control2F64.x - primitiveF64.control1F64.x),
        3.0 * (primitiveF64.control2F64.y - primitiveF64.control1F64.y),
    )
    val lastF64 = Vector2F64(
        3.0 * (primitiveF64.endF64.x - primitiveF64.control2F64.x),
        3.0 * (primitiveF64.endF64.y - primitiveF64.control2F64.y),
    )
    val startF64 = quadraticVectorPointF64(firstF64, controlF64, lastF64, startParameterF64)
    val rightControlF64 = interpolatedStrokeVectorF64(controlF64, lastF64, startParameterF64)
    val remainingParameterF64 = 1.0 - startParameterF64
    val relativeEndParameterF64 = if (remainingParameterF64 == 0.0) {
        0.0
    } else {
        (endParameterF64 - startParameterF64) / remainingParameterF64
    }
    return Triple(
        startF64,
        interpolatedStrokeVectorF64(startF64, rightControlF64, relativeEndParameterF64),
        quadraticVectorPointF64(firstF64, controlF64, lastF64, endParameterF64),
    )
}

private fun quadraticVectorPointF64(
    firstF64: Vector2F64,
    controlF64: Vector2F64,
    lastF64: Vector2F64,
    parameterF64: Double,
): Vector2F64 {
    val inverseF64 = 1.0 - parameterF64
    return Vector2F64(
        firstF64.x * inverseF64 * inverseF64 + controlF64.x * 2.0 * inverseF64 * parameterF64 +
            lastF64.x * parameterF64 * parameterF64,
        firstF64.y * inverseF64 * inverseF64 + controlF64.y * 2.0 * inverseF64 * parameterF64 +
            lastF64.y * parameterF64 * parameterF64,
    )
}

private fun interpolatedStrokeVectorF64(
    firstF64: Vector2F64,
    secondF64: Vector2F64,
    parameterF64: Double,
): Vector2F64 = Vector2F64(
    firstF64.x + (secondF64.x - firstF64.x) * parameterF64,
    firstF64.y + (secondF64.y - firstF64.y) * parameterF64,
)

private fun isFiniteStrokeInputSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo -> segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() &&
        segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}

private fun sameStrokePointF64(firstF64: Point2F64, secondF64: Point2F64): Boolean =
    firstF64.x == secondF64.x && firstF64.y == secondF64.y

private fun strokeDistanceF64(firstF64: Point2F64, secondF64: Point2F64): Double {
    val distanceF64 = stableHypotF64(secondF64.x - firstF64.x, secondF64.y - firstF64.y)
    if (!distanceF64.isFinite()) throw PathStrokeInvalidInputAbort()
    return distanceF64
}

private fun strokeVectorLengthF64(vectorF64: Vector2F64): Double {
    val lengthF64 = stableHypotF64(vectorF64.x, vectorF64.y)
    if (!lengthF64.isFinite()) throw PathStrokeInvalidInputAbort()
    return lengthF64
}
