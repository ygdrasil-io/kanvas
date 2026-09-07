package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillFlatteningPolicyF64
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.vector.Vector2F64

/** Stable invalid-scene facts produced before a projective fill snapshot is published. */
public enum class PathProjectiveInvalidSceneReason {
    NonFiniteMatrix,
    NonFiniteProjection,
    PerspectiveHorizonCrossing,
}

/** Stable bounded-work facts for projective fill preparation. */
public enum class PathProjectiveResourceLimitReason {
    FlatteningDidNotConverge,
    PathWorkLimit,
    FrameWorkLimit,
    SnapshotByteLimit,
    RasterBoundsOverflow,
}

/** Transactional result of projecting a source path into device-space F64 fill commands. */
public sealed interface PathProjectivePreparationResult {
    public data class Ready(
        public val inputF64: PathFillInputF64,
        public val transformClass: PathTransformClass,
        public val pathWorkUsageAfterI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathProjectivePreparationResult

    public data class Empty(
        public val pathWorkUsageAfterI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathProjectivePreparationResult

    public data class InvalidScene(public val reason: PathProjectiveInvalidSceneReason) : PathProjectivePreparationResult

    public data class ResourceLimitExceeded(public val reason: PathProjectiveResourceLimitReason) :
        PathProjectivePreparationResult
}

/**
 * Evaluates a source [PathF32] parametrically under this homogeneous matrix.
 *
 * Every accepted curve interval has an outward-rounded W interval excluding zero.  The emitted
 * snapshot is therefore device-space only and contains no latent projective evaluation.
 */
public fun Matrix3x3F64.prepareProjectedPathFillInputF64(
    path: PathF32,
    policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    workPolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathProjectivePreparationResult {
    if (!isFinite()) return PathProjectivePreparationResult.InvalidScene(PathProjectiveInvalidSceneReason.NonFiniteMatrix)
    return try {
        PathProjectiveFillPreparerF64(
            matrixF64 = this,
            pathF32 = path,
            policyF64 = policyF64,
            workPolicyF64 = workPolicyF64,
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        ).prepare()
    } catch (abort: PathProjectiveInvalidAbort) {
        PathProjectivePreparationResult.InvalidScene(abort.reason)
    } catch (abort: PathProjectiveResourceAbort) {
        PathProjectivePreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

private class PathProjectiveInvalidAbort(
    val reason: PathProjectiveInvalidSceneReason,
) : RuntimeException()

private class PathProjectiveResourceAbort(
    val reason: PathProjectiveResourceLimitReason,
) : RuntimeException()

private class PathProjectiveWorkLedgerF64(
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    private val limitsI32: PathStrokeLimitsI32,
    private val limitsI64: PathStrokeLimitsI64,
) {
    private var pathUsageI64: PathStrokeWorkUsageI64 = pathWorkUsageBeforeI64
    private var frameUsageI64: PathStrokeWorkUsageI64 = frameWorkUsageBeforeI64

    init {
        requireWithinLimits(pathUsageI64, isPath = true)
        requireWithinLimits(frameUsageI64, isPath = false)
    }

    fun debitBeforeWorkI64(deltaI64: PathStrokeWorkUsageI64) {
        val nextPathI64 = checkedAddUsageI64(pathUsageI64, deltaI64)
        val nextFrameI64 = checkedAddUsageI64(frameUsageI64, deltaI64)
        requireWithinLimits(nextPathI64, isPath = true)
        requireWithinLimits(nextFrameI64, isPath = false)
        pathUsageI64 = nextPathI64
        frameUsageI64 = nextFrameI64
    }

    fun pathSnapshotI64(): PathStrokeWorkUsageI64 = pathUsageI64

    fun frameSnapshotI64(): PathStrokeWorkUsageI64 = frameUsageI64

    private fun requireWithinLimits(usageI64: PathStrokeWorkUsageI64, isPath: Boolean) {
        val maximumWorkI64 = if (isPath) {
            limitsI32.maxAttemptedGeometryUnitsPerPathI32.toLong()
        } else {
            limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toLong()
        }
        if (usageI64.attemptedGeometryUnitCountI64 > maximumWorkI64) {
            throw PathProjectiveResourceAbort(
                if (isPath) PathProjectiveResourceLimitReason.PathWorkLimit
                else PathProjectiveResourceLimitReason.FrameWorkLimit,
            )
        }
        val maximumBytesI64 = if (isPath) limitsI64.maxSnapshotByteCountPerPathI64
        else limitsI64.maxSnapshotByteCountPerFrameI64
        if (usageI64.snapshotByteCountI64 > maximumBytesI64) {
            throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.SnapshotByteLimit)
        }
    }
}

private fun checkedAddUsageI64(
    firstI64: PathStrokeWorkUsageI64,
    secondI64: PathStrokeWorkUsageI64,
): PathStrokeWorkUsageI64 = try {
    PathStrokeWorkUsageI64(
        attemptedGeometryUnitCountI64 = checkedProjectiveAddI64(
            firstI64.attemptedGeometryUnitCountI64,
            secondI64.attemptedGeometryUnitCountI64,
        ),
        emittedVertexCountI64 = checkedProjectiveAddI64(firstI64.emittedVertexCountI64, secondI64.emittedVertexCountI64),
        emittedIndexCountI64 = checkedProjectiveAddI64(firstI64.emittedIndexCountI64, secondI64.emittedIndexCountI64),
        snapshotByteCountI64 = checkedProjectiveAddI64(firstI64.snapshotByteCountI64, secondI64.snapshotByteCountI64),
    )
} catch (_: IllegalStateException) {
    throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.RasterBoundsOverflow)
}

private fun checkedProjectiveAddI64(firstI64: Long, secondI64: Long): Long {
    if (firstI64 < 0L || secondI64 < 0L || firstI64 > Long.MAX_VALUE - secondI64) {
        throw IllegalStateException("projective-work-overflow")
    }
    return firstI64 + secondI64
}

private class PathProjectiveFillPreparerF64(
    private val matrixF64: Matrix3x3F64,
    private val pathF32: PathF32,
    private val policyF64: PathFillFlatteningPolicyF64,
    workPolicyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
) {
    private val ledgerI64 = PathProjectiveWorkLedgerF64(
        pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        limitsI32 = workPolicyF64.limitsI32,
        limitsI64 = workPolicyF64.limitsI64,
    )
    private lateinit var inputF64: PathFillInputF64
    private lateinit var outputF64: MutableList<PathFillSegmentF64>
    private var currentPointF64: Point2F64? = null
    private var contourStartF64: Point2F64? = null
    private var hasDrawableSegment: Boolean = false

    fun prepare(): PathProjectivePreparationResult {
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        inputF64 = PathFillInputF64.fromPathF32(pathF32)
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        outputF64 = mutableListOf()
        inputF64.forEach { segmentF64 ->
            if (!isFiniteProjectiveInputSegmentF64(segmentF64)) {
                abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
            }
            when (segmentF64) {
                is PathFillSegmentF64.MoveTo -> beginContour(segmentF64.point)
                is PathFillSegmentF64.LineTo -> {
                    ensureContour()
                    appendPrimitiveF64(ProjectiveLinePrimitiveF64(requireNotNull(currentPointF64), segmentF64.point))
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.QuadTo -> {
                    ensureContour()
                    appendPrimitiveF64(
                        ProjectiveBezierPrimitiveF64(
                            listOf(requireNotNull(currentPointF64), segmentF64.control, segmentF64.point),
                        ),
                    )
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.CubicTo -> {
                    ensureContour()
                    appendPrimitiveF64(
                        ProjectiveBezierPrimitiveF64(
                            listOf(
                                requireNotNull(currentPointF64),
                                segmentF64.control1,
                                segmentF64.control2,
                                segmentF64.point,
                            ),
                        ),
                    )
                    currentPointF64 = segmentF64.point
                }

                is PathFillSegmentF64.ArcTo -> {
                    ensureContour()
                    val startF64 = requireNotNull(currentPointF64)
                    if (startF64 != segmentF64.point) {
                        appendPrimitiveF64(ProjectiveSvgArcPrimitiveF64.of(startF64, segmentF64))
                    }
                    currentPointF64 = segmentF64.point
                }

                PathFillSegmentF64.Close -> closeContour()
            }
        }
        if (!hasDrawableSegment) {
            return PathProjectivePreparationResult.Empty(ledgerI64.pathSnapshotI64(), ledgerI64.frameSnapshotI64())
        }
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        return PathProjectivePreparationResult.Ready(
            inputF64 = PathFillInputF64.of(inputF64.fillRule, outputF64),
            transformClass = matrixF64.classifyPathTransform(),
            pathWorkUsageAfterI64 = ledgerI64.pathSnapshotI64(),
            frameWorkUsageAfterI64 = ledgerI64.frameSnapshotI64(),
        )
    }

    private fun beginContour(sourcePointF64: Point2F64) {
        appendMoveF64(sourcePointF64)
        currentPointF64 = sourcePointF64
        contourStartF64 = sourcePointF64
    }

    private fun ensureContour() {
        if (currentPointF64 == null) beginContour(Point2F64.Origin)
    }

    private fun closeContour() {
        val startF64 = contourStartF64 ?: return
        val currentF64 = currentPointF64 ?: return
        if (currentF64 != startF64) appendPrimitiveF64(ProjectiveLinePrimitiveF64(currentF64, startF64))
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 8L))
        outputF64 += PathFillSegmentF64.Close
        currentPointF64 = startF64
    }

    private fun appendMoveF64(sourcePointF64: Point2F64) {
        ledgerI64.debitBeforeWorkI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 32L),
        )
        outputF64 += PathFillSegmentF64.MoveTo(projectPointF64(sourcePointF64))
    }

    private fun appendPrimitiveF64(primitiveF64: ProjectiveFillPrimitiveF64) {
        appendPrimitiveIntervalF64(primitiveF64, 0.0, 1.0, depthI32 = 0)
    }

    private fun appendPrimitiveIntervalF64(
        primitiveF64: ProjectiveFillPrimitiveF64,
        startParameterF64: Double,
        endParameterF64: Double,
        depthI32: Int,
    ) {
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L))
        val wIntervalF64 = primitiveF64.wIntervalF64(matrixF64, startParameterF64, endParameterF64)
            ?: abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        if (wIntervalF64.containsZeroF64()) {
            val middleParameterF64 = splitParameterF64(startParameterF64, endParameterF64)
            ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 3L))
            val startW = homogeneousWAtF64(primitiveF64, startParameterF64)
            val middleW = homogeneousWAtF64(primitiveF64, middleParameterF64)
            val endW = homogeneousWAtF64(primitiveF64, endParameterF64)
            if (startW == 0.0 || middleW == 0.0 || endW == 0.0 ||
                (startW < 0.0) != (middleW < 0.0) || (middleW < 0.0) != (endW < 0.0)
            ) {
                abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
            }
            if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
                abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
            }
            appendPrimitiveIntervalF64(primitiveF64, startParameterF64, middleParameterF64, depthI32 + 1)
            appendPrimitiveIntervalF64(primitiveF64, middleParameterF64, endParameterF64, depthI32 + 1)
            return
        }

        val middleParameterF64 = splitParameterF64(startParameterF64, endParameterF64)
        val firstQuarterParameterF64 = splitParameterF64(startParameterF64, middleParameterF64)
        val thirdQuarterParameterF64 = splitParameterF64(middleParameterF64, endParameterF64)
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 5L))
        val startF64 = projectPointF64(primitiveF64.pointAtF64(startParameterF64))
        val firstQuarterF64 = projectPointF64(primitiveF64.pointAtF64(firstQuarterParameterF64))
        val middleF64 = projectPointF64(primitiveF64.pointAtF64(middleParameterF64))
        val thirdQuarterF64 = projectPointF64(primitiveF64.pointAtF64(thirdQuarterParameterF64))
        val endF64 = projectPointF64(primitiveF64.pointAtF64(endParameterF64))
        val sagittaF64 = max(
            max(
                distanceToChordF64(firstQuarterF64, startF64, endF64),
                distanceToChordF64(middleF64, startF64, endF64),
            ),
            distanceToChordF64(thirdQuarterF64, startF64, endF64),
        )
        if (!sagittaF64.isFinite()) abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        if (sagittaF64 > policyF64.maximumSagittaErrorF64) {
            if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
                throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.FlatteningDidNotConverge)
            }
            appendPrimitiveIntervalF64(primitiveF64, startParameterF64, middleParameterF64, depthI32 + 1)
            appendPrimitiveIntervalF64(primitiveF64, middleParameterF64, endParameterF64, depthI32 + 1)
            return
        }
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 32L))
        outputF64 += PathFillSegmentF64.LineTo(endF64)
        hasDrawableSegment = true
    }

    private fun homogeneousWAtF64(primitiveF64: ProjectiveFillPrimitiveF64, parameterF64: Double): Double {
        val pointF64 = primitiveF64.pointAtF64(parameterF64)
        val homogeneousF64 = matrixF64.projectHomogeneousPointF64(pointF64)
            ?: abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        return homogeneousF64.wF64
    }

    private fun projectPointF64(pointF64: Point2F64): Point2F64 {
        val homogeneousF64 = matrixF64.projectHomogeneousPointF64(pointF64)
            ?: abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        if (homogeneousF64.wF64 == 0.0) abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
        val projectedF64 = projectFiniteHomogeneousPointF64(homogeneousF64)
            ?: abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        if (!projectedF64.x.isF32RepresentableF64() || !projectedF64.y.isF32RepresentableF64()) {
            throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.RasterBoundsOverflow)
        }
        return projectedF64
    }

    private fun abortInvalid(reason: PathProjectiveInvalidSceneReason): Nothing = throw PathProjectiveInvalidAbort(reason)
}

private fun splitParameterF64(startParameterF64: Double, endParameterF64: Double): Double {
    val middleF64 = startParameterF64 + (endParameterF64 - startParameterF64) * 0.5
    if (!middleF64.isFinite() || middleF64 <= startParameterF64 || middleF64 >= endParameterF64) {
        throw PathProjectiveInvalidAbort(PathProjectiveInvalidSceneReason.NonFiniteProjection)
    }
    return middleF64
}

private fun Double.isF32RepresentableF64(): Boolean =
    isFinite() && abs(this) <= Float.MAX_VALUE.toDouble()

private fun distanceToChordF64(pointF64: Point2F64, startF64: Point2F64, endF64: Point2F64): Double {
    val chordXF64 = endF64.x - startF64.x
    val chordYF64 = endF64.y - startF64.y
    val pointXF64 = pointF64.x - startF64.x
    val pointYF64 = pointF64.y - startF64.y
    val chordLengthSquaredF64 = chordXF64 * chordXF64 + chordYF64 * chordYF64
    if (!chordLengthSquaredF64.isFinite()) return Double.NaN
    return if (chordLengthSquaredF64 == 0.0) {
        sqrt(pointXF64 * pointXF64 + pointYF64 * pointYF64)
    } else {
        abs(pointXF64 * chordYF64 - pointYF64 * chordXF64) / sqrt(chordLengthSquaredF64)
    }
}

private fun isFiniteProjectiveInputSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo -> segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo ->
        segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() && segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}

private sealed interface ProjectiveFillPrimitiveF64 {
    fun pointAtF64(parameterF64: Double): Point2F64

    fun wIntervalF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): PathProjectiveIntervalF64?
}

private class ProjectiveLinePrimitiveF64(
    private val startF64: Point2F64,
    private val endF64: Point2F64,
) : ProjectiveFillPrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = interpolatedProjectivePointF64(startF64, endF64, parameterF64)

    override fun wIntervalF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): PathProjectiveIntervalF64? = projectiveBezierWIntervalF64(
        matrixF64,
        listOf(startF64, endF64),
        startParameterF64,
        endParameterF64,
    )
}

private class ProjectiveBezierPrimitiveF64(
    private val controlsF64: List<Point2F64>,
) : ProjectiveFillPrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = bezierPointAtF64(controlsF64, parameterF64)

    override fun wIntervalF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): PathProjectiveIntervalF64? = projectiveBezierWIntervalF64(
        matrixF64,
        controlsF64,
        startParameterF64,
        endParameterF64,
    )
}

private fun projectiveBezierWIntervalF64(
    matrixF64: Matrix3x3F64,
    controlsF64: List<Point2F64>,
    startParameterF64: Double,
    endParameterF64: Double,
): PathProjectiveIntervalF64? {
    val restrictedControlsF64 = bezierControlsOnIntervalF64(controlsF64, startParameterF64, endParameterF64)
    val intervalsF64 = restrictedControlsF64.map { pointF64 ->
        when (val resultF64 = matrixF64.projectiveWIntervalForPointF64(pointF64)) {
            is PathProjectiveIntervalResultF64.Ready -> resultF64.intervalF64
            PathProjectiveIntervalResultF64.NonFinite -> return null
        }
    }
    return projectiveHullIntervalF64(intervalsF64)
}

private fun bezierControlsOnIntervalF64(
    controlsF64: List<Point2F64>,
    startParameterF64: Double,
    endParameterF64: Double,
): List<Point2F64> {
    if (startParameterF64 == 0.0 && endParameterF64 == 1.0) return controlsF64
    val (_, afterStartF64) = splitBezierControlsF64(controlsF64, startParameterF64)
    val localEndF64 = (endParameterF64 - startParameterF64) / (1.0 - startParameterF64)
    return splitBezierControlsF64(afterStartF64, localEndF64).first
}

private fun splitBezierControlsF64(controlsF64: List<Point2F64>, parameterF64: Double): Pair<List<Point2F64>, List<Point2F64>> {
    var levelF64 = controlsF64
    val leftF64 = mutableListOf(levelF64.first())
    val rightF64 = mutableListOf(levelF64.last())
    while (levelF64.size > 1) {
        levelF64 = levelF64.zipWithNext { firstF64, secondF64 ->
            interpolatedProjectivePointF64(firstF64, secondF64, parameterF64)
        }
        leftF64 += levelF64.first()
        rightF64 += levelF64.last()
    }
    return leftF64 to rightF64.asReversed()
}

private fun bezierPointAtF64(controlsF64: List<Point2F64>, parameterF64: Double): Point2F64 {
    var levelF64 = controlsF64
    while (levelF64.size > 1) {
        levelF64 = levelF64.zipWithNext { firstF64, secondF64 ->
            interpolatedProjectivePointF64(firstF64, secondF64, parameterF64)
        }
    }
    return levelF64.single()
}

private fun interpolatedProjectivePointF64(firstF64: Point2F64, secondF64: Point2F64, parameterF64: Double): Point2F64 = Point2F64(
    x = firstF64.x * (1.0 - parameterF64) + secondF64.x * parameterF64,
    y = firstF64.y * (1.0 - parameterF64) + secondF64.y * parameterF64,
)

private class ProjectiveSvgArcPrimitiveF64 private constructor(
    private val fallbackF64: ProjectiveLinePrimitiveF64?,
    private val centerF64: ProjectiveArcCenterF64?,
) : ProjectiveFillPrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = fallbackF64?.pointAtF64(parameterF64)
        ?: requireNotNull(centerF64).pointAtF64(parameterF64)

    override fun wIntervalF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): PathProjectiveIntervalF64? {
        fallbackF64?.let { return it.wIntervalF64(matrixF64, startParameterF64, endParameterF64) }
        return requireNotNull(centerF64).wIntervalF64(matrixF64, startParameterF64, endParameterF64)
    }

    companion object {
        fun of(startF64: Point2F64, segmentF64: PathFillSegmentF64.ArcTo): ProjectiveSvgArcPrimitiveF64 {
            val centerF64 = projectiveArcCenterF64(
                startF64 = startF64,
                endF64 = segmentF64.point,
                radiusF64 = segmentF64.radius,
                xAxisRotationDegreesF64 = segmentF64.xAxisRotationDegreesF64,
                largeArc = segmentF64.largeArc,
                sweep = segmentF64.sweep,
            )
            return ProjectiveSvgArcPrimitiveF64(
                fallbackF64 = if (centerF64 == null) ProjectiveLinePrimitiveF64(startF64, segmentF64.point) else null,
                centerF64 = centerF64,
            )
        }
    }
}

private data class ProjectiveArcCenterF64(
    val centerF64: Point2F64,
    val radiusXF64: Double,
    val radiusYF64: Double,
    val rotationRadiansF64: Double,
    val startAngleF64: Double,
    val sweepAngleF64: Double,
) {
    fun pointAtF64(parameterF64: Double): Point2F64 {
        val angleF64 = startAngleF64 + sweepAngleF64 * parameterF64
        val cosAngleF64 = cos(angleF64)
        val sinAngleF64 = sin(angleF64)
        val cosRotationF64 = cos(rotationRadiansF64)
        val sinRotationF64 = sin(rotationRadiansF64)
        return Point2F64(
            centerF64.x + radiusXF64 * cosRotationF64 * cosAngleF64 - radiusYF64 * sinRotationF64 * sinAngleF64,
            centerF64.y + radiusXF64 * sinRotationF64 * cosAngleF64 + radiusYF64 * cosRotationF64 * sinAngleF64,
        )
    }

    fun wIntervalF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): PathProjectiveIntervalF64? {
        val cosRotationF64 = cos(rotationRadiansF64)
        val sinRotationF64 = sin(rotationRadiansF64)
        val cosineCoefficientF64 = matrixF64.persp0F64 * radiusXF64 * cosRotationF64 +
            matrixF64.persp1F64 * radiusXF64 * sinRotationF64
        val sineCoefficientF64 = -matrixF64.persp0F64 * radiusYF64 * sinRotationF64 +
            matrixF64.persp1F64 * radiusYF64 * cosRotationF64
        val extremumAngleF64 = atan2(sineCoefficientF64, cosineCoefficientF64)
        val parametersF64 = buildList {
            add(startParameterF64)
            add(endParameterF64)
            listOf(extremumAngleF64, extremumAngleF64 + PI).forEach { angleF64 ->
                parameterForAngleOrNullF64(angleF64)?.takeIf { it > startParameterF64 && it < endParameterF64 }?.let(::add)
            }
        }
        val intervalsF64 = parametersF64.map { parameterF64 ->
            when (val resultF64 = matrixF64.projectiveWIntervalForPointF64(pointAtF64(parameterF64))) {
                is PathProjectiveIntervalResultF64.Ready -> resultF64.intervalF64
                PathProjectiveIntervalResultF64.NonFinite -> return null
            }
        }
        return projectiveHullIntervalF64(intervalsF64)
    }

    private fun parameterForAngleOrNullF64(angleF64: Double): Double? {
        if (sweepAngleF64 == 0.0) return null
        val signedDistanceF64 = if (sweepAngleF64 > 0.0) {
            positiveProjectiveAngleF64(angleF64 - startAngleF64)
        } else {
            -positiveProjectiveAngleF64(startAngleF64 - angleF64)
        }
        return (signedDistanceF64 / sweepAngleF64).takeIf { it in 0.0..1.0 }
    }
}

private fun projectiveArcCenterF64(
    startF64: Point2F64,
    endF64: Point2F64,
    radiusF64: Vector2F64,
    xAxisRotationDegreesF64: Double,
    largeArc: Boolean,
    sweep: Boolean,
): ProjectiveArcCenterF64? {
    var radiusXF64 = abs(radiusF64.x)
    var radiusYF64 = abs(radiusF64.y)
    if (!startF64.isFinite() || !endF64.isFinite() || !radiusXF64.isFinite() || !radiusYF64.isFinite() ||
        !xAxisRotationDegreesF64.isFinite() || radiusXF64 == 0.0 || radiusYF64 == 0.0 || startF64 == endF64
    ) return null
    val rotationRadiansF64 = (xAxisRotationDegreesF64 % 360.0) * PI / 180.0
    val cosRotationF64 = cos(rotationRadiansF64)
    val sinRotationF64 = sin(rotationRadiansF64)
    val halfDeltaXF64 = (startF64.x - endF64.x) * 0.5
    val halfDeltaYF64 = (startF64.y - endF64.y) * 0.5
    val startXF64 = cosRotationF64 * halfDeltaXF64 + sinRotationF64 * halfDeltaYF64
    val startYF64 = -sinRotationF64 * halfDeltaXF64 + cosRotationF64 * halfDeltaYF64
    val lambdaF64 = startXF64 * startXF64 / (radiusXF64 * radiusXF64) +
        startYF64 * startYF64 / (radiusYF64 * radiusYF64)
    if (!lambdaF64.isFinite()) return null
    if (lambdaF64 > 1.0) {
        val correctionF64 = sqrt(lambdaF64)
        radiusXF64 *= correctionF64
        radiusYF64 *= correctionF64
    }
    val radiusXSquaredF64 = radiusXF64 * radiusXF64
    val radiusYSquaredF64 = radiusYF64 * radiusYF64
    val startXSquaredF64 = startXF64 * startXF64
    val startYSquaredF64 = startYF64 * startYF64
    val denominatorF64 = radiusXSquaredF64 * startYSquaredF64 + radiusYSquaredF64 * startXSquaredF64
    val numeratorF64 = radiusXSquaredF64 * radiusYSquaredF64 - radiusXSquaredF64 * startYSquaredF64 -
        radiusYSquaredF64 * startXSquaredF64
    val factorF64 = if (denominatorF64 == 0.0) 0.0 else {
        (if (largeArc == sweep) -1.0 else 1.0) * sqrt(max(0.0, numeratorF64 / denominatorF64))
    }
    val centerXPrimeF64 = factorF64 * radiusXF64 * startYF64 / radiusYF64
    val centerYPrimeF64 = -factorF64 * radiusYF64 * startXF64 / radiusXF64
    val centerF64 = Point2F64(
        cosRotationF64 * centerXPrimeF64 - sinRotationF64 * centerYPrimeF64 + (startF64.x + endF64.x) * 0.5,
        sinRotationF64 * centerXPrimeF64 + cosRotationF64 * centerYPrimeF64 + (startF64.y + endF64.y) * 0.5,
    )
    val startAngleF64 = atan2((startYF64 - centerYPrimeF64) / radiusYF64, (startXF64 - centerXPrimeF64) / radiusXF64)
    val endAngleF64 = atan2((-startYF64 - centerYPrimeF64) / radiusYF64, (-startXF64 - centerXPrimeF64) / radiusXF64)
    var sweepAngleF64 = endAngleF64 - startAngleF64
    if (!sweep && sweepAngleF64 > 0.0) sweepAngleF64 -= 2.0 * PI
    if (sweep && sweepAngleF64 < 0.0) sweepAngleF64 += 2.0 * PI
    return ProjectiveArcCenterF64(
        centerF64,
        radiusXF64,
        radiusYF64,
        rotationRadiansF64,
        startAngleF64,
        sweepAngleF64,
    ).takeIf {
        it.centerF64.isFinite() && it.radiusXF64.isFinite() && it.radiusYF64.isFinite() &&
            it.rotationRadiansF64.isFinite() && it.startAngleF64.isFinite() && it.sweepAngleF64.isFinite()
    }
}

private fun positiveProjectiveAngleF64(angleF64: Double): Double {
    val resultF64 = angleF64 % (2.0 * PI)
    return if (resultF64 < 0.0) resultF64 + 2.0 * PI else resultF64
}
