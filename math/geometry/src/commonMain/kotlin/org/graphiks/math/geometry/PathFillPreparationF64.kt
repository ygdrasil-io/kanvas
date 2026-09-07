package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

public enum class PathFillInvalidSceneReason {
    NonFiniteInput,
    NonFiniteProjection,
}

public enum class PathFillResourceLimitReason {
    FlatteningDidNotConverge,
    PathAttemptedEdgeLimit,
    FrameAttemptedEdgeLimit,
    WindingStencilEdgeLimit,
    RasterBoundsOverflow,
    HostSizeOverflow,
}

public sealed interface PathFillPreparationResult {
    public data class Ready(
        public val geometryF32: PathFillGeometryF32,
        public val attemptedEdgeCountI32: Int,
    ) : PathFillPreparationResult

    public data class Empty(
        public val attemptedEdgeCountI32: Int,
    ) : PathFillPreparationResult

    public data class InvalidScene(
        public val reason: PathFillInvalidSceneReason,
    ) : PathFillPreparationResult

    public data class ResourceLimitExceeded(
        public val reason: PathFillResourceLimitReason,
    ) : PathFillPreparationResult
}

/** Fill preparation outcome carrying the shared W4d stroke-work snapshots. */
public sealed interface PathFillWithStrokeWorkPreparationResult {
    public data class Ready(
        public val geometryF32: PathFillGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathFillWithStrokeWorkPreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathFillWithStrokeWorkPreparationResult

    public data class InvalidScene(
        public val reason: PathFillInvalidSceneReason,
    ) : PathFillWithStrokeWorkPreparationResult

    public data class ResourceLimitExceeded(
        public val reason: PathStrokeResourceLimitReason,
    ) : PathFillWithStrokeWorkPreparationResult
}

/**
 * Produces the sole device-space geometry authority for a W4c path fill.
 *
 * Work is accumulated privately and published only after all input, flattening,
 * bounds, and resource checks succeed.
 */
public fun preparePathFillGeometryF32(
    inputF64: PathFillInputF64,
    policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    frameAttemptedEdgesBeforeI32: Int = 0,
): PathFillPreparationResult {
    if (!inputF64.all(::isFinitePathFillInputSegmentF64)) {
        return PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteInput)
    }
    if (frameAttemptedEdgesBeforeI32 < 0 ||
        frameAttemptedEdgesBeforeI32 > policyF64.limitsI32.maxAttemptedEdgesPerFrameI32
    ) {
        return PathFillPreparationResult.ResourceLimitExceeded(PathFillResourceLimitReason.FrameAttemptedEdgeLimit)
    }

    return try {
        PathFillPreparerF64(inputF64, policyF64, frameAttemptedEdgesBeforeI32).prepare()
    } catch (abort: PathFillPreparationAbort) {
        abort.result
    }
}

/**
 * Produces W4c-compatible fill geometry while accounting every retained output through the
 * stroke work ledger used by mixed W4d frames.
 */
public fun preparePathFillGeometryWithStrokeWorkF32(
    inputF64: PathFillInputF64,
    fillPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    strokePolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathFillWithStrokeWorkPreparationResult {
    if (!inputF64.all(::isFinitePathFillInputSegmentF64)) {
        return PathFillWithStrokeWorkPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteInput)
    }

    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            limitsI32 = strokePolicyF64.limitsI32,
            limitsI64 = strokePolicyF64.limitsI64,
        )
        when (
            val result = preparePathFillGeometryWithStrokeWorkF32(
                inputF64 = inputF64,
                fillPolicyF64 = fillPolicyF64,
                ledgerI64 = ledgerI64,
            )
        ) {
            is PathFillPreparationResult.Ready -> PathFillWithStrokeWorkPreparationResult.Ready(
                geometryF32 = result.geometryF32,
                pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
            )

            is PathFillPreparationResult.Empty -> PathFillWithStrokeWorkPreparationResult.Empty(
                pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64(),
                frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64(),
            )

            is PathFillPreparationResult.InvalidScene ->
                PathFillWithStrokeWorkPreparationResult.InvalidScene(result.reason)

            is PathFillPreparationResult.ResourceLimitExceeded ->
                PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded(
                    result.reason.toPathStrokeResourceLimitReason(),
                )
        }
    } catch (abort: PathStrokeResourceLimitAbort) {
        PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

/**
 * Maps one source segment only after charging it to the same W4d ledger that
 * finalizes the device fill.  This prevents planner-side whole-path mapping
 * from escaping the transactional frame budget.
 */
public fun prepareMappedPathFillGeometryWithStrokeWorkF32(
    inputF64: PathFillInputF64,
    deviceSegmentMapperF64: PathStrokeDeviceFillSegmentMapperF64,
    fillPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    strokePolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathFillWithStrokeWorkPreparationResult {
    if (!inputF64.all(::isFinitePathFillInputSegmentF64)) {
        return PathFillWithStrokeWorkPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteInput)
    }
    return try {
        val ledgerI64 = PathStrokeWorkLedgerI64(
            pathWorkUsageBeforeI64 = PathStrokeWorkUsageI64(),
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            limitsI32 = strokePolicyF64.limitsI32,
            limitsI64 = strokePolicyF64.limitsI64,
        )
        val mapped = buildList {
            inputF64.forEach { source ->
                ledgerI64.debitTopologyBeforeEmissionI64(1L)
                add(deviceSegmentMapperF64.mapDeviceFillSegmentF64(source) ?: throw PathFillDeviceMappingAbort())
            }
        }
        val mappedInput = PathFillInputF64.of(inputF64.fillRule, mapped)
        when (val result = preparePathFillGeometryWithStrokeWorkF32(mappedInput, fillPolicyF64, ledgerI64)) {
            is PathFillPreparationResult.Ready -> PathFillWithStrokeWorkPreparationResult.Ready(
                result.geometryF32, ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
            is PathFillPreparationResult.Empty -> PathFillWithStrokeWorkPreparationResult.Empty(
                ledgerI64.snapshotPathUsageI64(), ledgerI64.snapshotFrameUsageAfterI64(),
            )
            is PathFillPreparationResult.InvalidScene ->
                PathFillWithStrokeWorkPreparationResult.InvalidScene(result.reason)
            is PathFillPreparationResult.ResourceLimitExceeded ->
                PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded(result.reason.toPathStrokeResourceLimitReason())
        }
    } catch (abort: PathStrokeResourceLimitAbort) {
        PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded(abort.reason)
    } catch (_: PathFillDeviceMappingAbort) {
        PathFillWithStrokeWorkPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection)
    }
}

private class PathFillDeviceMappingAbort : RuntimeException()

/** Internal finalizer overload for a stroke pipeline already holding one transactional ledger. */
internal fun preparePathFillGeometryWithStrokeWorkF32(
    inputF64: PathFillInputF64,
    fillPolicyF64: PathFillFlatteningPolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathFillPreparationResult {
    if (!inputF64.all(::isFinitePathFillInputSegmentF64)) {
        return PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteInput)
    }
    return try {
        PathFillPreparerF64(
            inputF64 = inputF64,
            policyF64 = fillPolicyF64,
            frameAttemptedEdgesBeforeI32 = 0,
            strokeWorkLedgerI64 = ledgerI64,
        ).prepare()
    } catch (abort: PathFillPreparationAbort) {
        abort.result
    }
}

private class PathFillPreparationAbort(
    val result: PathFillPreparationResult,
) : RuntimeException()

private data class PreparedPathFillContourF32(
    val verticesF32: List<Point2F32>,
)

private class PathFillPreparerF64(
    private val inputF64: PathFillInputF64,
    private val policyF64: PathFillFlatteningPolicyF64,
    private val frameAttemptedEdgesBeforeI32: Int,
    private val strokeWorkLedgerI64: PathStrokeWorkLedgerI64? = null,
) {
    private val retainedContoursF32 = mutableListOf<PreparedPathFillContourF32>()
    private var contourVerticesF32 = mutableListOf<Point2F32>()
    private var currentPointF64: Point2F64? = null
    private var contourStartF64: Point2F64? = null
    private var contourClosed: Boolean = false
    private var attemptedEdgeCountI32: Int = 0
    private var sourceIsLineOnly: Boolean = true

    fun prepare(): PathFillPreparationResult {
        inputF64.forEach { segment ->
            when (segment) {
                is PathFillSegmentF64.MoveTo -> {
                    finishContour()
                    beginContour(segment.point)
                }

                is PathFillSegmentF64.LineTo -> {
                    ensureContour()
                    contourClosed = false
                    attemptEdge(segment.point)
                    currentPointF64 = segment.point
                }

                is PathFillSegmentF64.QuadTo -> {
                    sourceIsLineOnly = false
                    ensureContour()
                    contourClosed = false
                    val start = requireNotNull(currentPointF64)
                    flattenQuad(start, segment.control, segment.point)
                    currentPointF64 = segment.point
                }

                is PathFillSegmentF64.CubicTo -> {
                    sourceIsLineOnly = false
                    ensureContour()
                    contourClosed = false
                    val start = requireNotNull(currentPointF64)
                    flattenCubic(start, segment.control1, segment.control2, segment.point)
                    currentPointF64 = segment.point
                }

                is PathFillSegmentF64.ArcTo -> {
                    sourceIsLineOnly = false
                    ensureContour()
                    val start = requireNotNull(currentPointF64)
                    if (!samePointF64(start, segment.point)) {
                        contourClosed = false
                        flattenArc(start, segment)
                    }
                    currentPointF64 = segment.point
                }

                PathFillSegmentF64.Close -> closeContour()
            }
        }
        finishContour()

        if (retainedContoursF32.isEmpty()) {
            return PathFillPreparationResult.Empty(attemptedEdgeCountI32)
        }

        return PathFillPreparationResult.Ready(
            geometryF32 = buildGeometry(),
            attemptedEdgeCountI32 = attemptedEdgeCountI32,
        )
    }

    private fun ensureContour() {
        if (currentPointF64 == null) beginContour(Point2F64.Origin)
    }

    private fun beginContour(start: Point2F64) {
        contourVerticesF32 = mutableListOf()
        appendProjectedVertex(start)
        currentPointF64 = start
        contourStartF64 = start
        contourClosed = false
    }

    private fun closeContour() {
        val current = currentPointF64 ?: return
        val start = contourStartF64 ?: return
        if (!contourClosed) {
            attemptEdge(start)
            currentPointF64 = start
            contourClosed = true
        } else {
            currentPointF64 = current
        }
    }

    private fun finishContour() {
        val current = currentPointF64
        val start = contourStartF64
        if (current != null && start != null && !contourClosed) {
            attemptEdge(start)
        }
        retainCurrentContour()
        currentPointF64 = null
        contourStartF64 = null
        contourClosed = false
        contourVerticesF32 = mutableListOf()
    }

    private fun retainCurrentContour() {
        if (contourVerticesF32.size > 1 && samePointF32(contourVerticesF32.first(), contourVerticesF32.last())) {
            contourVerticesF32.removeAt(contourVerticesF32.lastIndex)
        }
        if (contourVerticesF32.size < 3) return
        if (distinctVertexCountI32(contourVerticesF32) < 3) return
        if (isEntirelyCollinearF32(contourVerticesF32)) return
        retainedContoursF32 += PreparedPathFillContourF32(contourVerticesF32.toList())
    }

    private fun attemptEdge(end: Point2F64) {
        debitAttempt()
        appendProjectedVertex(end)
    }

    private fun debitAttempt() {
        strokeWorkLedgerI64?.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L),
        )
        val nextPathAttemptCountI64 = attemptedEdgeCountI32.toLong() + 1L
        if (nextPathAttemptCountI64 > policyF64.limitsI32.maxAttemptedEdgesPerPathI32.toLong()) {
            abortResource(PathFillResourceLimitReason.PathAttemptedEdgeLimit)
        }
        val nextFrameAttemptCountI64 = frameAttemptedEdgesBeforeI32.toLong() + nextPathAttemptCountI64
        if (nextFrameAttemptCountI64 > policyF64.limitsI32.maxAttemptedEdgesPerFrameI32.toLong()) {
            abortResource(PathFillResourceLimitReason.FrameAttemptedEdgeLimit)
        }
        attemptedEdgeCountI32 = nextPathAttemptCountI64.toInt()
    }

    private fun appendProjectedVertex(pointF64: Point2F64) {
        if (!pointF64.isFinite()) abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
        val projected = Point2F32(
            canonicalPathFillF32(pointF64.x),
            canonicalPathFillF32(pointF64.y),
        )
        if (contourVerticesF32.lastOrNull()?.let { samePointF32(it, projected) } != true) {
            contourVerticesF32 += projected
        }
    }

    private fun flattenQuad(start: Point2F64, control: Point2F64, end: Point2F64) {
        val chordX = end.x - start.x
        val chordY = end.y - start.y
        val controlX = control.x - start.x
        val controlY = control.y - start.y
        val cross = controlX * chordY - controlY * chordX
        if (!chordX.isFinite() || !chordY.isFinite() || !controlX.isFinite() || !controlY.isFinite() || !cross.isFinite()) {
            abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
        }
        if (cross == 0.0) {
            val derivativeDeltaX = start.x - control.x * 2.0 + end.x
            val derivativeDeltaY = start.y - control.y * 2.0 + end.y
            val denominator = derivativeDeltaX * derivativeDeltaX + derivativeDeltaY * derivativeDeltaY
            if (!derivativeDeltaX.isFinite() || !derivativeDeltaY.isFinite() || !denominator.isFinite()) {
                abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
            }
            if (denominator != 0.0) {
                val parameter = -(controlX * derivativeDeltaX + controlY * derivativeDeltaY) / denominator
                if (!parameter.isFinite()) abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
                if (parameter > 0.0 && parameter < 1.0) {
                    attemptEdge(quadPointF64(start, control, end, parameter))
                }
            }
            attemptEdge(end)
            return
        }
        flattenQuadRecursive(start, control, end, depthI32 = 0)
    }

    private fun flattenQuadRecursive(
        start: Point2F64,
        control: Point2F64,
        end: Point2F64,
        depthI32: Int,
    ) {
        val sagitta = pathFillPointToSegmentDistanceF64(control, start, end)
        if (sagitta <= policyF64.maximumSagittaErrorF64) {
            attemptEdge(end)
            return
        }
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            abortResource(PathFillResourceLimitReason.FlatteningDidNotConverge)
        }
        val startControl = midpointPathFillF64(start, control)
        val controlEnd = midpointPathFillF64(control, end)
        val middle = midpointPathFillF64(startControl, controlEnd)
        flattenQuadRecursive(start, startControl, middle, depthI32 + 1)
        flattenQuadRecursive(middle, controlEnd, end, depthI32 + 1)
    }

    private fun flattenCubic(start: Point2F64, control1: Point2F64, control2: Point2F64, end: Point2F64) {
        flattenCubicRecursive(start, control1, control2, end, depthI32 = 0)
    }

    private fun flattenCubicRecursive(
        start: Point2F64,
        control1: Point2F64,
        control2: Point2F64,
        end: Point2F64,
        depthI32: Int,
    ) {
        val firstSagitta = pathFillPointToSegmentDistanceF64(control1, start, end)
        val secondSagitta = pathFillPointToSegmentDistanceF64(control2, start, end)
        if (maxOf(firstSagitta, secondSagitta) <= policyF64.maximumSagittaErrorF64) {
            attemptEdge(end)
            return
        }
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            abortResource(PathFillResourceLimitReason.FlatteningDidNotConverge)
        }
        val startControl1 = midpointPathFillF64(start, control1)
        val controls = midpointPathFillF64(control1, control2)
        val control2End = midpointPathFillF64(control2, end)
        val leftControl = midpointPathFillF64(startControl1, controls)
        val rightControl = midpointPathFillF64(controls, control2End)
        val middle = midpointPathFillF64(leftControl, rightControl)
        flattenCubicRecursive(start, startControl1, leftControl, middle, depthI32 + 1)
        flattenCubicRecursive(middle, rightControl, control2End, end, depthI32 + 1)
    }

    private fun flattenArc(start: Point2F64, segment: PathFillSegmentF64.ArcTo) {
        val endpoint = ArcEndpointF64(
            start = start,
            end = segment.point,
            radius = segment.radius,
            xAxisRotationDegrees = segment.xAxisRotationDegreesF64,
            largeArc = segment.largeArc,
            sweep = segment.sweep,
        )
        val arc = arcCenterF64(endpoint)
        if (arc == null || arc.sweepAngle == 0.0) {
            attemptEdge(segment.point)
            return
        }
        if (!arc.center.isFinite() || !arc.radiusX.isFinite() || !arc.radiusY.isFinite() ||
            !arc.rotationRadians.isFinite() || !arc.startAngle.isFinite() || !arc.sweepAngle.isFinite()
        ) {
            abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
        }
        flattenArcRecursive(arc, start, segment.point, 0.0, 1.0, depthI32 = 0)
    }

    private fun flattenArcRecursive(
        arc: ArcCenterF64,
        start: Point2F64,
        end: Point2F64,
        startT: Double,
        endT: Double,
        depthI32: Int,
    ) {
        if (isArcChordDeviationWithinToleranceF64(arc, startT, endT, policyF64.maximumSagittaErrorF64)) {
            attemptEdge(end)
            return
        }
        val middleT = (startT + endT) * 0.5
        val middle = arc.pointAt(middleT)
        if (!middle.isFinite()) abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            abortResource(PathFillResourceLimitReason.FlatteningDidNotConverge)
        }
        flattenArcRecursive(arc, start, middle, startT, middleT, depthI32 + 1)
        flattenArcRecursive(arc, middle, end, middleT, endT, depthI32 + 1)
    }

    private fun buildGeometry(): PathFillGeometryF32 {
        val emittedEdgeCountI64 = retainedContoursF32.sumOf { it.verticesF32.size.toLong() }
        if (emittedEdgeCountI64 <= 0L || emittedEdgeCountI64 > Int.MAX_VALUE.toLong()) {
            abortResource(PathFillResourceLimitReason.HostSizeOverflow)
        }
        val emittedEdgeCountI32 = emittedEdgeCountI64.toInt()
        val scissor = conservativeScissorI32(retainedContoursF32)
        val directContour = retainedContoursF32.singleOrNull()
        val isDirect = inputF64.fillRule == FillRule.WINDING && sourceIsLineOnly &&
            directContour != null && directContour.verticesF32.size == 3

        if (isDirect) {
            val vertices = directContour.verticesF32
            debitFinalGeometryBeforeEmission(
                vertexCountI64 = 3L,
                indexCountI64 = 3L,
                snapshotByteCountI64 = directPathFillSnapshotByteCostI64(),
            )
            return PathFillGeometryF32(
                fillRule = inputF64.fillRule,
                attemptedEdgeCountI32 = attemptedEdgeCountI32,
                emittedNonZeroClosedEdgeCountI32 = emittedEdgeCountI32,
                conservativeScissorI32 = scissor,
                directTriangleF32 = PathFillDirectTriangleF32(
                    floatArrayOf(
                        vertices[0].x, vertices[0].y,
                        vertices[1].x, vertices[1].y,
                        vertices[2].x, vertices[2].y,
                    ),
                    intArrayOf(0, 1, 2),
                ),
                stencilEdgeFanF32 = null,
            )
        }

        if (inputF64.fillRule == FillRule.WINDING && emittedEdgeCountI32 > 255) {
            abortResource(PathFillResourceLimitReason.WindingStencilEdgeLimit)
        }
        if (emittedEdgeCountI64 * 6L > Int.MAX_VALUE.toLong() ||
            emittedEdgeCountI64 * 3L > Int.MAX_VALUE.toLong()
        ) {
            abortResource(PathFillResourceLimitReason.HostSizeOverflow)
        }

        debitFinalGeometryBeforeEmission(
            vertexCountI64 = emittedEdgeCountI64 * 3L + 4L,
            indexCountI64 = emittedEdgeCountI64 * 3L + 6L,
            snapshotByteCountI64 = stencilPathFillSnapshotByteCostI64(
                edgeCountI64 = emittedEdgeCountI64,
                contourCountI64 = retainedContoursF32.size.toLong(),
            ),
        )

        val fan = createStencilEdgeFanF32(emittedEdgeCountI32)
        return PathFillGeometryF32(
            fillRule = inputF64.fillRule,
            attemptedEdgeCountI32 = attemptedEdgeCountI32,
            emittedNonZeroClosedEdgeCountI32 = emittedEdgeCountI32,
            conservativeScissorI32 = scissor,
            directTriangleF32 = null,
            stencilEdgeFanF32 = fan,
        )
    }

    private fun createStencilEdgeFanF32(edgeCountI32: Int): PathStencilEdgeFanF32 {
        val vertices = FloatArray(edgeCountI32 * 6)
        val indices = IntArray(edgeCountI32 * 3)
        val contourStarts = IntArray(retainedContoursF32.size)
        var vertexOffsetI32 = 0
        var indexOffsetI32 = 0
        var edgeOffsetI32 = 0
        var indexValueI32 = 0

        retainedContoursF32.forEachIndexed { contourIndexI32, contour ->
            contourStarts[contourIndexI32] = edgeOffsetI32
            contour.verticesF32.forEachIndexed { vertexIndexI32, current ->
                val next = contour.verticesF32[(vertexIndexI32 + 1) % contour.verticesF32.size]
                vertices[vertexOffsetI32++] = 0f
                vertices[vertexOffsetI32++] = 0f
                vertices[vertexOffsetI32++] = current.x
                vertices[vertexOffsetI32++] = current.y
                vertices[vertexOffsetI32++] = next.x
                vertices[vertexOffsetI32++] = next.y
                indices[indexOffsetI32++] = indexValueI32++
                indices[indexOffsetI32++] = indexValueI32++
                indices[indexOffsetI32++] = indexValueI32++
                edgeOffsetI32 += 1
            }
        }
        return PathStencilEdgeFanF32(vertices, indices, contourStarts)
    }

    private fun debitFinalGeometryBeforeEmission(
        vertexCountI64: Long,
        indexCountI64: Long,
        snapshotByteCountI64: Long,
    ) {
        strokeWorkLedgerI64?.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(
                emittedVertexCountI64 = vertexCountI64,
                emittedIndexCountI64 = indexCountI64,
                snapshotByteCountI64 = snapshotByteCountI64,
            ),
        )
    }
}

private fun isFinitePathFillInputSegmentF64(segment: PathFillSegmentF64): Boolean = when (segment) {
    is PathFillSegmentF64.MoveTo -> segment.point.isFinite()
    is PathFillSegmentF64.LineTo -> segment.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segment.control.isFinite() && segment.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segment.control1.isFinite() && segment.control2.isFinite() && segment.point.isFinite()
    is PathFillSegmentF64.ArcTo -> segment.radius.isFinite() && segment.xAxisRotationDegreesF64.isFinite() && segment.point.isFinite()
    PathFillSegmentF64.Close -> true
}

private fun samePointF64(first: Point2F64, second: Point2F64): Boolean = first.x == second.x && first.y == second.y

private fun samePointF32(first: Point2F32, second: Point2F32): Boolean = first.x == second.x && first.y == second.y

private fun canonicalPathFillF32(value: Double): Float {
    val projected = Float.fromBits(value.toFloat().toRawBits())
    if (!projected.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    return projected
}

private fun midpointPathFillF64(first: Point2F64, second: Point2F64): Point2F64 {
    val midpoint = first.midpointTo(second)
    if (!midpoint.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    return midpoint
}

private fun quadPointF64(start: Point2F64, control: Point2F64, end: Point2F64, parameter: Double): Point2F64 {
    val inverse = 1.0 - parameter
    val result = Point2F64(
        start.x * inverse * inverse + control.x * 2.0 * inverse * parameter + end.x * parameter * parameter,
        start.y * inverse * inverse + control.y * 2.0 * inverse * parameter + end.y * parameter * parameter,
    )
    if (!result.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    return result
}

private fun isArcChordDeviationWithinToleranceF64(
    arc: ArcCenterF64,
    startT: Double,
    endT: Double,
    maximumSagittaErrorF64: Double,
): Boolean {
    val angularSpanF64 = abs(arc.sweepAngle * (endT - startT))
    if (!angularSpanF64.isFinite()) abortInvalid(PathFillInvalidSceneReason.NonFiniteProjection)

    // For a rotated ellipse p(theta), ||p''(theta)|| is at most max(rx, ry).
    // The linear-interpolation error over the entire angular interval is bounded
    // by max(rx, ry) * angularSpan^2 / 8, not merely its angular midpoint.
    val maximumRadiusF64 = maxOf(arc.radiusX, arc.radiusY)
    val deviationBoundF64 = maximumRadiusF64 * angularSpanF64 * angularSpanF64 * 0.125
    return deviationBoundF64.isFinite() && deviationBoundF64 <= maximumSagittaErrorF64
}

private fun pathFillPointToSegmentDistanceF64(point: Point2F64, start: Point2F64, end: Point2F64): Double {
    val deltaX = end.x - start.x
    val deltaY = end.y - start.y
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (!deltaX.isFinite() || !deltaY.isFinite() || !lengthSquared.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    if (lengthSquared == 0.0) return stableHypotF64(point.x - start.x, point.y - start.y)
    val projection = ((point.x - start.x) * deltaX + (point.y - start.y) * deltaY) / lengthSquared
    if (!projection.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    val clampedProjection = projection.coerceIn(0.0, 1.0)
    val distance = stableHypotF64(
        point.x - (start.x + clampedProjection * deltaX),
        point.y - (start.y + clampedProjection * deltaY),
    )
    if (!distance.isFinite()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.InvalidScene(PathFillInvalidSceneReason.NonFiniteProjection),
        )
    }
    return distance
}

private fun distinctVertexCountI32(vertices: List<Point2F32>): Int {
    val keys = HashSet<Long>()
    vertices.forEach { point -> keys += pathFillPointKeyI64(point) }
    return keys.size
}

private fun pathFillPointKeyI64(point: Point2F32): Long {
    val xBits = if (point.x == 0f) 0 else point.x.toRawBits()
    val yBits = if (point.y == 0f) 0 else point.y.toRawBits()
    return (xBits.toLong() shl 32) xor (yBits.toLong() and 0xffff_ffffL)
}

private fun isEntirelyCollinearF32(vertices: List<Point2F32>): Boolean {
    val first = vertices.first().toPoint2F64()
    val second = vertices.firstOrNull { !samePointF32(vertices.first(), it) }?.toPoint2F64() ?: return true
    return vertices.all { point -> OrientationPredicateF64.sign(first, second, point.toPoint2F64()) == 0 }
}

private fun conservativeScissorI32(contours: List<PreparedPathFillContourF32>): RectI32 {
    var left = Double.POSITIVE_INFINITY
    var top = Double.POSITIVE_INFINITY
    var right = Double.NEGATIVE_INFINITY
    var bottom = Double.NEGATIVE_INFINITY
    contours.forEach { contour ->
        contour.verticesF32.forEach { point ->
            left = minOf(left, point.x.toDouble())
            top = minOf(top, point.y.toDouble())
            right = maxOf(right, point.x.toDouble())
            bottom = maxOf(bottom, point.y.toDouble())
        }
    }
    val roundedLeft = floor(left)
    val roundedTop = floor(top)
    val roundedRight = ceil(right)
    val roundedBottom = ceil(bottom)
    if (!roundedLeft.isFinite() || !roundedTop.isFinite() || !roundedRight.isFinite() || !roundedBottom.isFinite() ||
        roundedLeft < Int.MIN_VALUE.toDouble() || roundedTop < Int.MIN_VALUE.toDouble() ||
        roundedRight > Int.MAX_VALUE.toDouble() || roundedBottom > Int.MAX_VALUE.toDouble()
    ) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.ResourceLimitExceeded(PathFillResourceLimitReason.RasterBoundsOverflow),
        )
    }
    val result = RectI32(roundedLeft.toInt(), roundedTop.toInt(), roundedRight.toInt(), roundedBottom.toInt())
    val widthI64 = result.width64()
    val heightI64 = result.height64()
    if (widthI64 <= 0L || heightI64 <= 0L || widthI64 > Int.MAX_VALUE.toLong() || heightI64 > Int.MAX_VALUE.toLong()) {
        throw PathFillPreparationAbort(
            PathFillPreparationResult.ResourceLimitExceeded(PathFillResourceLimitReason.RasterBoundsOverflow),
        )
    }
    return result
}

private fun abortInvalid(reason: PathFillInvalidSceneReason): Nothing =
    throw PathFillPreparationAbort(PathFillPreparationResult.InvalidScene(reason))

private fun abortResource(reason: PathFillResourceLimitReason): Nothing =
    throw PathFillPreparationAbort(PathFillPreparationResult.ResourceLimitExceeded(reason))

internal fun PathFillResourceLimitReason.toPathStrokeResourceLimitReason(): PathStrokeResourceLimitReason = when (this) {
    PathFillResourceLimitReason.FlatteningDidNotConverge -> PathStrokeResourceLimitReason.FlatteningDidNotConverge
    PathFillResourceLimitReason.PathAttemptedEdgeLimit -> PathStrokeResourceLimitReason.PathWorkLimit
    PathFillResourceLimitReason.FrameAttemptedEdgeLimit -> PathStrokeResourceLimitReason.FrameWorkLimit
    PathFillResourceLimitReason.WindingStencilEdgeLimit -> PathStrokeResourceLimitReason.VertexLimit
    PathFillResourceLimitReason.RasterBoundsOverflow -> PathStrokeResourceLimitReason.RasterBoundsOverflow
    PathFillResourceLimitReason.HostSizeOverflow -> PathStrokeResourceLimitReason.HostSizeOverflow
}

private fun directPathFillSnapshotByteCostI64(): Long = 52L

private fun stencilPathFillSnapshotByteCostI64(edgeCountI64: Long, contourCountI64: Long): Long {
    if (edgeCountI64 < 0L || contourCountI64 < 0L || edgeCountI64 > (Long.MAX_VALUE - 16L) / 36L ||
        contourCountI64 > (Long.MAX_VALUE - 16L - edgeCountI64 * 36L) / 4L
    ) {
        throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
    }
    return 16L + edgeCountI64 * 36L + contourCountI64 * 4L
}
