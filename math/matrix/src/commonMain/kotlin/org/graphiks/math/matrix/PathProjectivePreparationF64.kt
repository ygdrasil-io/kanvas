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
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.Point2F32
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
        val nextPathI64 = checkedAddUsageI64(
            firstI64 = pathUsageI64,
            secondI64 = deltaI64,
            workLimitReason = PathProjectiveResourceLimitReason.PathWorkLimit,
        )
        val nextFrameI64 = checkedAddUsageI64(
            firstI64 = frameUsageI64,
            secondI64 = deltaI64,
            workLimitReason = PathProjectiveResourceLimitReason.FrameWorkLimit,
        )
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
    workLimitReason: PathProjectiveResourceLimitReason,
): PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(
    attemptedGeometryUnitCountI64 = checkedProjectiveAddI64(
        firstI64.attemptedGeometryUnitCountI64,
        secondI64.attemptedGeometryUnitCountI64,
        workLimitReason,
    ),
    emittedVertexCountI64 = checkedProjectiveAddI64(
        firstI64.emittedVertexCountI64,
        secondI64.emittedVertexCountI64,
        workLimitReason,
    ),
    emittedIndexCountI64 = checkedProjectiveAddI64(
        firstI64.emittedIndexCountI64,
        secondI64.emittedIndexCountI64,
        workLimitReason,
    ),
    snapshotByteCountI64 = checkedProjectiveAddI64(
        firstI64.snapshotByteCountI64,
        secondI64.snapshotByteCountI64,
        PathProjectiveResourceLimitReason.SnapshotByteLimit,
    ),
)

private fun checkedProjectiveAddI64(
    firstI64: Long,
    secondI64: Long,
    overflowReason: PathProjectiveResourceLimitReason,
): Long {
    if (firstI64 < 0L || secondI64 < 0L || firstI64 > Long.MAX_VALUE - secondI64) {
        throw PathProjectiveResourceAbort(overflowReason)
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
    private lateinit var outputF64: MutableList<PathFillSegmentF64>
    private var currentPointF64: Point2F64? = null
    private var contourStartF64: Point2F64? = null
    private var hasDrawableSegment: Boolean = false

    fun prepare(): PathProjectivePreparationResult {
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        outputF64 = mutableListOf()
        pathF32.forEach { segmentF32 ->
            ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L))
            when (segmentF32) {
                is PathSegmentF32.MoveTo -> beginContour(exactProjectivePointF64(segmentF32.point))
                is PathSegmentF32.LineTo -> {
                    ensureContour()
                    val endF64 = exactProjectivePointF64(segmentF32.point)
                    appendPrimitiveF64(ProjectiveLinePrimitiveF64(requireNotNull(currentPointF64), endF64))
                    currentPointF64 = endF64
                }

                is PathSegmentF32.QuadTo -> {
                    ensureContour()
                    val controlF64 = exactProjectivePointF64(segmentF32.control)
                    val endF64 = exactProjectivePointF64(segmentF32.point)
                    appendPrimitiveF64(
                        ProjectiveBezierPrimitiveF64(
                            arrayOf(requireNotNull(currentPointF64), controlF64, endF64),
                        ),
                    )
                    currentPointF64 = endF64
                }

                is PathSegmentF32.CubicTo -> {
                    ensureContour()
                    val control1F64 = exactProjectivePointF64(segmentF32.control1)
                    val control2F64 = exactProjectivePointF64(segmentF32.control2)
                    val endF64 = exactProjectivePointF64(segmentF32.point)
                    appendPrimitiveF64(
                        ProjectiveBezierPrimitiveF64(
                            arrayOf(
                                requireNotNull(currentPointF64),
                                control1F64,
                                control2F64,
                                endF64,
                            ),
                        ),
                    )
                    currentPointF64 = endF64
                }

                is PathSegmentF32.ArcTo -> {
                    ensureContour()
                    val startF64 = requireNotNull(currentPointF64)
                    val endF64 = exactProjectivePointF64(segmentF32.point)
                    val radiusF64 = Vector2F64(
                        exactProjectiveF64(segmentF32.radius.x),
                        exactProjectiveF64(segmentF32.radius.y),
                    )
                    val rotationF64 = exactProjectiveF64(segmentF32.xAxisRotation)
                    if (!radiusF64.x.isFinite() || !radiusF64.y.isFinite() || !rotationF64.isFinite()) {
                        abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
                    }
                    if (startF64 != endF64) {
                        appendPrimitiveF64(
                            ProjectiveSvgArcPrimitiveF64.of(
                                startF64 = startF64,
                                endF64 = endF64,
                                radiusF64 = radiusF64,
                                xAxisRotationDegreesF64 = rotationF64,
                                largeArc = segmentF32.largeArc,
                                sweep = segmentF32.sweep,
                            ),
                        )
                    }
                    currentPointF64 = endF64
                }

                PathSegmentF32.Close -> closeContour()
            }
        }
        if (!hasDrawableSegment) {
            return PathProjectivePreparationResult.Empty(ledgerI64.pathSnapshotI64(), ledgerI64.frameSnapshotI64())
        }
        ledgerI64.debitBeforeWorkI64(
            PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L + outputF64.size.toLong() * 8L),
        )
        return PathProjectivePreparationResult.Ready(
            inputF64 = PathFillInputF64.of(pathF32.fillRule, outputF64),
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
        if (currentPointF64 == null) {
            ledgerI64.debitBeforeWorkI64(
                PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 32L),
            )
            beginContour(Point2F64.Origin)
        }
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
            PathStrokeWorkUsageI64(snapshotByteCountI64 = 32L),
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
        val controlsF64 = when (val controlsResultF64 = primitiveF64.homogeneousControlsF64(
            matrixF64,
            startParameterF64,
            endParameterF64,
        )) {
            is ProjectiveHomogeneousControlsResultF64.Ready -> controlsResultF64.controlsF64
            ProjectiveHomogeneousControlsResultF64.NeedsSubdivision -> {
                subdividePrimitiveIntervalF64(
                    primitiveF64,
                    startParameterF64,
                    endParameterF64,
                    depthI32,
                    horizonAtLimit = false,
                )
                return
            }
            ProjectiveHomogeneousControlsResultF64.NonFinite -> abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        }
        when (certifyWSignF64(controlsF64)) {
            ProjectiveWCertificateF64.Horizon -> abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
            ProjectiveWCertificateF64.NeedsSubdivision -> {
                subdividePrimitiveIntervalF64(
                    primitiveF64,
                    startParameterF64,
                    endParameterF64,
                    depthI32,
                    horizonAtLimit = true,
                )
                return
            }
            ProjectiveWCertificateF64.StrictlySeparated -> Unit
        }
        ledgerI64.debitBeforeWorkI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = controlsF64.size.toLong()),
        )
        val projectedControlsF64 = controlsF64.map(::projectControlPointF64)
        val errorBoundF64 = projectedControlHullErrorBoundF64(projectedControlsF64)
        if (!errorBoundF64.isFinite()) abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
        if (errorBoundF64 > policyF64.maximumSagittaErrorF64) {
            subdividePrimitiveIntervalF64(
                primitiveF64,
                startParameterF64,
                endParameterF64,
                depthI32,
                horizonAtLimit = false,
            )
            return
        }
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 32L))
        outputF64 += PathFillSegmentF64.LineTo(projectOutputControlPointF64(controlsF64.last()))
        hasDrawableSegment = true
    }

    private fun subdividePrimitiveIntervalF64(
        primitiveF64: ProjectiveFillPrimitiveF64,
        startParameterF64: Double,
        endParameterF64: Double,
        depthI32: Int,
        horizonAtLimit: Boolean,
    ) {
        if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
            if (horizonAtLimit) abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
            throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.FlatteningDidNotConverge)
        }
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L))
        val middleParameterF64 = splitParameterF64(startParameterF64, endParameterF64)
        appendPrimitiveIntervalF64(primitiveF64, startParameterF64, middleParameterF64, depthI32 + 1)
        appendPrimitiveIntervalF64(primitiveF64, middleParameterF64, endParameterF64, depthI32 + 1)
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

    private fun projectOutputControlPointF64(controlF64: ProjectiveHomogeneousPointF64): Point2F64 {
        val projectedF64 = projectControlPointF64(controlF64)
        if (!projectedF64.x.isF32RepresentableF64() || !projectedF64.y.isF32RepresentableF64()) {
            throw PathProjectiveResourceAbort(PathProjectiveResourceLimitReason.RasterBoundsOverflow)
        }
        return projectedF64
    }

    private fun abortInvalid(reason: PathProjectiveInvalidSceneReason): Nothing = throw PathProjectiveInvalidAbort(reason)
}

/** Restores the exact Float payload at the JS boundary before any projective arithmetic. */
private fun exactProjectiveF64(valueF32: Float): Double = Float.fromBits(valueF32.toRawBits()).toDouble()

private fun exactProjectivePointF64(pointF32: Point2F32): Point2F64 {
    val pointF64 = Point2F64(exactProjectiveF64(pointF32.x), exactProjectiveF64(pointF32.y))
    if (!pointF64.isFinite()) throw PathProjectiveInvalidAbort(PathProjectiveInvalidSceneReason.NonFiniteProjection)
    return pointF64
}

private fun projectControlPointF64(controlF64: ProjectiveHomogeneousPointF64): Point2F64 =
    projectFiniteHomogeneousPointF64(controlF64)
        ?: throw PathProjectiveInvalidAbort(PathProjectiveInvalidSceneReason.NonFiniteProjection)

/**
 * A rational Bezier with a sign-separated denominator is contained by the convex hull of its
 * divided homogeneous controls. The maximum control-to-chord distance is thus a whole-interval
 * upper bound, rather than a sampled estimate.
 */
private fun projectedControlHullErrorBoundF64(controlsF64: List<Point2F64>): Double {
    if (controlsF64.size < 2) return Double.NaN
    val startF64 = controlsF64.first()
    val endF64 = controlsF64.last()
    return controlsF64.maxOf { controlF64 -> distanceToChordF64(controlF64, startF64, endF64) }
}

private fun certifyWSignF64(controlsF64: List<ProjectiveHomogeneousPointF64>): ProjectiveWCertificateF64 {
    if (controlsF64.isEmpty() || controlsF64.any { !it.wF64.isFinite() }) return ProjectiveWCertificateF64.Horizon
    val weightsF64 = controlsF64.map(ProjectiveHomogeneousPointF64::wF64)
    if (weightsF64.first() == 0.0 || weightsF64.last() == 0.0) return ProjectiveWCertificateF64.Horizon
    if (weightsF64.all { it > 0.0 } || weightsF64.all { it < 0.0 }) {
        return ProjectiveWCertificateF64.StrictlySeparated
    }
    // Bernstein's variation-diminishing property makes an odd number of strict sign variations
    // a root certificate on this whole interval.  It catches non-dyadic roots without relying on
    // a sampled parameter or an epsilon comparison.
    val signsF64 = weightsF64.filter { it != 0.0 }.map { if (it > 0.0) 1 else -1 }
    val signVariationCountI32 = signsF64.zipWithNext().count { (firstI32, secondI32) -> firstI32 != secondI32 }
    if (signVariationCountI32 % 2 == 1) return ProjectiveWCertificateF64.Horizon
    return ProjectiveWCertificateF64.NeedsSubdivision
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

private sealed interface ProjectiveFillPrimitiveF64 {
    fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64
}

private sealed interface ProjectiveHomogeneousControlsResultF64 {
    data class Ready(val controlsF64: List<ProjectiveHomogeneousPointF64>) : ProjectiveHomogeneousControlsResultF64

    data object NeedsSubdivision : ProjectiveHomogeneousControlsResultF64

    data object NonFinite : ProjectiveHomogeneousControlsResultF64
}

private enum class ProjectiveWCertificateF64 {
    StrictlySeparated,
    NeedsSubdivision,
    Horizon,
}

private class ProjectiveLinePrimitiveF64(
    private val startF64: Point2F64,
    private val endF64: Point2F64,
) : ProjectiveFillPrimitiveF64 {
    override fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64 = projectiveBezierControlsF64(
        matrixF64 = matrixF64,
        controlsF64 = listOf(startF64, endF64),
        startParameterF64 = startParameterF64,
        endParameterF64 = endParameterF64,
    )
}

private class ProjectiveBezierPrimitiveF64(
    private val controlsF64: Array<Point2F64>,
) : ProjectiveFillPrimitiveF64 {
    override fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64 = projectiveBezierControlsF64(
        matrixF64 = matrixF64,
        controlsF64 = controlsF64.asList(),
        startParameterF64 = startParameterF64,
        endParameterF64 = endParameterF64,
    )
}

private fun projectiveBezierControlsF64(
    matrixF64: Matrix3x3F64,
    controlsF64: List<Point2F64>,
    startParameterF64: Double,
    endParameterF64: Double,
): ProjectiveHomogeneousControlsResultF64 {
    val homogeneousControlsF64 = controlsF64.map { pointF64 -> matrixF64.projectHomogeneousPointF64(pointF64) }
    if (homogeneousControlsF64.any { it == null }) return ProjectiveHomogeneousControlsResultF64.NonFinite
    return ProjectiveHomogeneousControlsResultF64.Ready(
        homogeneousControlsOnIntervalF64(
            homogeneousControlsF64.filterNotNull(),
            startParameterF64,
            endParameterF64,
        ),
    )
}

private fun homogeneousControlsOnIntervalF64(
    controlsF64: List<ProjectiveHomogeneousPointF64>,
    startParameterF64: Double,
    endParameterF64: Double,
): List<ProjectiveHomogeneousPointF64> {
    if (startParameterF64 == 0.0 && endParameterF64 == 1.0) return controlsF64
    val (_, afterStartF64) = splitHomogeneousControlsF64(controlsF64, startParameterF64)
    val localEndF64 = (endParameterF64 - startParameterF64) / (1.0 - startParameterF64)
    return splitHomogeneousControlsF64(afterStartF64, localEndF64).first
}

private fun splitHomogeneousControlsF64(
    controlsF64: List<ProjectiveHomogeneousPointF64>,
    parameterF64: Double,
): Pair<List<ProjectiveHomogeneousPointF64>, List<ProjectiveHomogeneousPointF64>> {
    var levelF64 = controlsF64
    val leftF64 = mutableListOf(levelF64.first())
    val rightF64 = mutableListOf(levelF64.last())
    while (levelF64.size > 1) {
        levelF64 = levelF64.zipWithNext { firstF64, secondF64 -> interpolatedHomogeneousPointF64(firstF64, secondF64, parameterF64) }
        leftF64 += levelF64.first()
        rightF64 += levelF64.last()
    }
    return leftF64 to rightF64.asReversed()
}

private fun interpolatedHomogeneousPointF64(
    firstF64: ProjectiveHomogeneousPointF64,
    secondF64: ProjectiveHomogeneousPointF64,
    parameterF64: Double,
): ProjectiveHomogeneousPointF64 = ProjectiveHomogeneousPointF64(
    xF64 = firstF64.xF64 * (1.0 - parameterF64) + secondF64.xF64 * parameterF64,
    yF64 = firstF64.yF64 * (1.0 - parameterF64) + secondF64.yF64 * parameterF64,
    wF64 = firstF64.wF64 * (1.0 - parameterF64) + secondF64.wF64 * parameterF64,
)

private class ProjectiveSvgArcPrimitiveF64 private constructor(
    private val fallbackF64: ProjectiveLinePrimitiveF64?,
    private val centerF64: ProjectiveArcCenterF64?,
) : ProjectiveFillPrimitiveF64 {
    override fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64 = fallbackF64?.homogeneousControlsF64(
        matrixF64,
        startParameterF64,
        endParameterF64,
    ) ?: requireNotNull(centerF64).homogeneousControlsF64(matrixF64, startParameterF64, endParameterF64)

    companion object {
        fun of(
            startF64: Point2F64,
            endF64: Point2F64,
            radiusF64: Vector2F64,
            xAxisRotationDegreesF64: Double,
            largeArc: Boolean,
            sweep: Boolean,
        ): ProjectiveSvgArcPrimitiveF64 {
            val centerF64 = projectiveArcCenterF64(
                startF64 = startF64,
                endF64 = endF64,
                radiusF64 = radiusF64,
                xAxisRotationDegreesF64 = xAxisRotationDegreesF64,
                largeArc = largeArc,
                sweep = sweep,
            )
            return ProjectiveSvgArcPrimitiveF64(
                fallbackF64 = if (centerF64 == null) ProjectiveLinePrimitiveF64(startF64, endF64) else null,
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
    private fun pointAtF64(parameterF64: Double): Point2F64 {
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

    fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64 {
        val angularSpanF64 = abs(sweepAngleF64 * (endParameterF64 - startParameterF64))
        if (angularSpanF64 > PI * 0.5) return ProjectiveHomogeneousControlsResultF64.NeedsSubdivision
        val intervalStartAngleF64 = this.startAngleF64 + sweepAngleF64 * startParameterF64
        val intervalEndAngleF64 = this.startAngleF64 + sweepAngleF64 * endParameterF64
        val halfSpanF64 = (intervalEndAngleF64 - intervalStartAngleF64) * 0.5
        val middleAngleF64 = (intervalStartAngleF64 + intervalEndAngleF64) * 0.5
        val middleWeightF64 = cos(halfSpanF64)
        if (!middleWeightF64.isFinite() || middleWeightF64 <= 0.0) {
            return ProjectiveHomogeneousControlsResultF64.NeedsSubdivision
        }
        val cosRotationF64 = cos(rotationRadiansF64)
        val sinRotationF64 = sin(rotationRadiansF64)
        val middlePointF64 = Point2F64(
            centerF64.x * middleWeightF64 + radiusXF64 * cosRotationF64 * cos(middleAngleF64) -
                radiusYF64 * sinRotationF64 * sin(middleAngleF64),
            centerF64.y * middleWeightF64 + radiusXF64 * sinRotationF64 * cos(middleAngleF64) +
                radiusYF64 * cosRotationF64 * sin(middleAngleF64),
        )
        val controlsF64 = listOf(
            projectArcControlF64(matrixF64, pointAtF64(startParameterF64), 1.0),
            projectArcControlF64(matrixF64, middlePointF64, middleWeightF64),
            projectArcControlF64(matrixF64, pointAtF64(endParameterF64), 1.0),
        )
        return if (controlsF64.any { it == null }) {
            ProjectiveHomogeneousControlsResultF64.NonFinite
        }
        else ProjectiveHomogeneousControlsResultF64.Ready(controlsF64.filterNotNull())
    }
}

private fun projectArcControlF64(
    matrixF64: Matrix3x3F64,
    pointF64: Point2F64,
    weightF64: Double,
): ProjectiveHomogeneousPointF64? = matrixF64.projectHomogeneousCoordinatesF64(
    xF64 = pointF64.x,
    yF64 = pointF64.y,
    wF64 = weightF64,
)

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
