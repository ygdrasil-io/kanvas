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
import org.graphiks.math.geometry.toPathFillSegmentF64
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
    return prepareProjectedPathFillInputF64(
        path, policyF64, workPolicyF64, pathWorkUsageBeforeI64, frameWorkUsageBeforeI64, {},
    )
}

internal fun Matrix3x3F64.prepareProjectedPathFillInputF64(
    path: PathF32,
    policyF64: PathFillFlatteningPolicyF64,
    workPolicyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    beforeWorkDebitI64: (PathStrokeWorkUsageI64) -> Unit,
): PathProjectivePreparationResult {
    if (!isFinite()) return PathProjectivePreparationResult.InvalidScene(PathProjectiveInvalidSceneReason.NonFiniteMatrix)
    return try {
        PathProjectiveFillPreparerF64(
            matrixF64 = this, pathF32 = path, inputF64 = null,
            policyF64 = policyF64, workPolicyF64 = workPolicyF64,
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            beforeWorkDebitI64 = beforeWorkDebitI64,
        ).prepare()
    } catch (abort: PathProjectiveInvalidAbort) {
        PathProjectivePreparationResult.InvalidScene(abort.reason)
    } catch (abort: PathProjectiveResourceAbort) {
        PathProjectivePreparationResult.ResourceLimitExceeded(abort.reason)
    }
}

/** F64-source overload used by transformed F64 clips; no source coordinate is narrowed. */
internal fun Matrix3x3F64.prepareProjectedPathFillInputF64(
    inputF64: PathFillInputF64,
    policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    workPolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    beforeWorkDebitI64: (PathStrokeWorkUsageI64) -> Unit = {},
): PathProjectivePreparationResult {
    if (!isFinite()) return PathProjectivePreparationResult.InvalidScene(PathProjectiveInvalidSceneReason.NonFiniteMatrix)
    return try {
        PathProjectiveFillPreparerF64(
            matrixF64 = this,
            pathF32 = null,
            inputF64 = inputF64,
            policyF64 = policyF64,
            workPolicyF64 = workPolicyF64,
            pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            beforeWorkDebitI64 = beforeWorkDebitI64,
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
    private val beforeWorkDebitI64: (PathStrokeWorkUsageI64) -> Unit,
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
        beforeWorkDebitI64(deltaI64)
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
    private val pathF32: PathF32?,
    private val inputF64: PathFillInputF64?,
    private val policyF64: PathFillFlatteningPolicyF64,
    workPolicyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    beforeWorkDebitI64: (PathStrokeWorkUsageI64) -> Unit,
) {
    private val ledgerI64 = PathProjectiveWorkLedgerF64(
        pathWorkUsageBeforeI64 = pathWorkUsageBeforeI64,
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        limitsI32 = workPolicyF64.limitsI32,
        limitsI64 = workPolicyF64.limitsI64,
        beforeWorkDebitI64 = beforeWorkDebitI64,
    )
    private lateinit var outputF64: MutableList<PathFillSegmentF64>
    private var currentPointF64: Point2F64? = null
    private var contourStartF64: Point2F64? = null
    private var hasDrawableSegment: Boolean = false

    fun prepare(): PathProjectivePreparationResult {
        ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        outputF64 = mutableListOf()
        fun debitSourceSegment() {
            ledgerI64.debitBeforeWorkI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L))
        }
        fun process(segmentF64: PathFillSegmentF64) {
            when (segmentF64) {
                is PathFillSegmentF64.MoveTo -> beginContour(segmentF64.point)
                is PathFillSegmentF64.LineTo -> {
                    ensureContour()
                    val endF64 = segmentF64.point
                    appendPrimitiveF64(ProjectiveLinePrimitiveF64(requireNotNull(currentPointF64), endF64))
                    currentPointF64 = endF64
                }

                is PathFillSegmentF64.QuadTo -> {
                    ensureContour()
                    val controlF64 = segmentF64.control
                    val endF64 = segmentF64.point
                    appendPrimitiveF64(
                        ProjectiveBezierPrimitiveF64(
                            arrayOf(requireNotNull(currentPointF64), controlF64, endF64),
                        ),
                    )
                    currentPointF64 = endF64
                }

                is PathFillSegmentF64.CubicTo -> {
                    ensureContour()
                    val control1F64 = segmentF64.control1
                    val control2F64 = segmentF64.control2
                    val endF64 = segmentF64.point
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

                is PathFillSegmentF64.ArcTo -> {
                    ensureContour()
                    val startF64 = requireNotNull(currentPointF64)
                    val endF64 = segmentF64.point
                    val radiusF64 = segmentF64.radius
                    val rotationF64 = segmentF64.xAxisRotationDegreesF64
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
                                largeArc = segmentF64.largeArc,
                                sweep = segmentF64.sweep,
                            ),
                        )
                    }
                    currentPointF64 = endF64
                }

                PathFillSegmentF64.Close -> closeContour()
            }
        }
        inputF64?.forEach { segmentF64 -> debitSourceSegment(); process(segmentF64) }
        pathF32?.forEach { segmentF32 ->
            debitSourceSegment()
            process(segmentF32.toPathFillSegmentF64())
        }
        if (!hasDrawableSegment) {
            return PathProjectivePreparationResult.Empty(ledgerI64.pathSnapshotI64(), ledgerI64.frameSnapshotI64())
        }
        ledgerI64.debitBeforeWorkI64(
            PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L + outputF64.size.toLong() * 8L),
        )
        return PathProjectivePreparationResult.Ready(
            inputF64 = PathFillInputF64.of(inputF64?.fillRule ?: requireNotNull(pathF32).fillRule, outputF64),
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
                when (primitiveF64.horizonCertificateF64(matrixF64, startParameterF64, endParameterF64)) {
                    ProjectiveHorizonCertificateF64.Crossing -> {
                        abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
                    }
                    ProjectiveHorizonCertificateF64.NonFinite -> {
                        abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
                    }
                    ProjectiveHorizonCertificateF64.Separated,
                    ProjectiveHorizonCertificateF64.Unknown,
                    -> Unit
                }
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
            ProjectiveWCertificateF64.NonFinite -> abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
            ProjectiveWCertificateF64.NeedsSubdivision -> {
                subdividePrimitiveIntervalF64(
                    primitiveF64,
                    startParameterF64,
                    endParameterF64,
                    depthI32,
                    // Alternating Bernstein controls need isolation; even sign variation alone
                    // is not a root proof and must not be upgraded to a Horizon at the limit.
                    horizonAtLimit = false,
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
        when (projectiveWSignF64(ProjectiveCompensatedF64(
                homogeneousF64.wF64,
                homogeneousF64.wResidualF64,
                homogeneousF64.wUncertaintyF64,
                homogeneousF64.wLowerTailF64,
                homogeneousF64.wUpperTailF64,
                homogeneousF64.wSubnormalUnitsF64,
                homogeneousF64.wSubnormalResidualUnitsF64,
            ))) {
            ProjectiveWSignF64.Root -> abortInvalid(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing)
            ProjectiveWSignF64.Unknown,
            ProjectiveWSignF64.NonFinite,
            -> abortInvalid(PathProjectiveInvalidSceneReason.NonFiniteProjection)
            ProjectiveWSignF64.Positive,
            ProjectiveWSignF64.Negative,
            -> Unit
        }
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
    if (controlsF64.isEmpty() || controlsF64.any {
            !it.wF64.isFinite() || !it.wResidualF64.isFinite() || !it.wUncertaintyF64.isFinite() ||
                !it.wLowerTailF64.isFinite() || !it.wUpperTailF64.isFinite()
        }
    ) {
        return ProjectiveWCertificateF64.NonFinite
    }
    val signsF64 = controlsF64.map { controlF64 -> projectiveWSignF64(
            ProjectiveCompensatedF64(
                controlF64.wF64,
                controlF64.wResidualF64,
                controlF64.wUncertaintyF64,
            controlF64.wLowerTailF64,
            controlF64.wUpperTailF64,
            controlF64.wSubnormalUnitsF64,
            controlF64.wSubnormalResidualUnitsF64,
            ),
        )
    }
    if (signsF64.any { it == ProjectiveWSignF64.NonFinite }) return ProjectiveWCertificateF64.NonFinite
    if (signsF64.first() == ProjectiveWSignF64.Root || signsF64.last() == ProjectiveWSignF64.Root) {
        return ProjectiveWCertificateF64.Horizon
    }
    if ((signsF64.first() == ProjectiveWSignF64.Positive && signsF64.last() == ProjectiveWSignF64.Negative) ||
        (signsF64.first() == ProjectiveWSignF64.Negative && signsF64.last() == ProjectiveWSignF64.Positive)
    ) {
        // Continuity makes unlike strict endpoint signs a whole-interval root certificate.
        return ProjectiveWCertificateF64.Horizon
    }
    if (signsF64.all { it == ProjectiveWSignF64.Positive } || signsF64.all { it == ProjectiveWSignF64.Negative }) {
        return ProjectiveWCertificateF64.StrictlySeparated
    }
    if (signsF64.any { it == ProjectiveWSignF64.Unknown }) return ProjectiveWCertificateF64.NeedsSubdivision
    // Bernstein's variation-diminishing property makes an odd number of strict sign variations
    // a root certificate on this whole interval.  It catches non-dyadic roots without relying on
    // a sampled parameter or an epsilon comparison.
    // An internal zero Bernstein control is not a polynomial root.  Variation count ignores it;
    // only a certified endpoint zero or a certificate below may publish a horizon.
    val strictSignsF64 = signsF64.filter {
        it == ProjectiveWSignF64.Positive || it == ProjectiveWSignF64.Negative
    }
    val signVariationCountI32 = strictSignsF64.zipWithNext().count { (firstF64, secondF64) -> firstF64 != secondF64 }
    if (signVariationCountI32 % 2 == 1) return ProjectiveWCertificateF64.Horizon
    if (projectiveBezierRootCertificateF64(controlsF64.map { controlF64 ->
            ProjectiveCompensatedF64(
                controlF64.wF64,
                controlF64.wResidualF64,
                controlF64.wUncertaintyF64,
                controlF64.wLowerTailF64,
                controlF64.wUpperTailF64,
                controlF64.wSubnormalUnitsF64,
                controlF64.wSubnormalResidualUnitsF64,
            )
        })
    ) {
        return ProjectiveWCertificateF64.Horizon
    }
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
        val projectionParameterF64 = ((pointXF64 * chordXF64 + pointYF64 * chordYF64) / chordLengthSquaredF64)
            .coerceIn(0.0, 1.0)
        val nearestXF64 = startF64.x + chordXF64 * projectionParameterF64
        val nearestYF64 = startF64.y + chordYF64 * projectionParameterF64
        sqrt((pointF64.x - nearestXF64) * (pointF64.x - nearestXF64) +
            (pointF64.y - nearestYF64) * (pointF64.y - nearestYF64))
    }
}

private sealed interface ProjectiveFillPrimitiveF64 {
    fun homogeneousControlsF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHomogeneousControlsResultF64

    /** Detects a root before a primitive that is not yet representable as one rational conic is split. */
    fun horizonCertificateF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHorizonCertificateF64 = ProjectiveHorizonCertificateF64.Unknown
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
    NonFinite,
}

private enum class ProjectiveHorizonCertificateF64 {
    Crossing,
    Separated,
    Unknown,
    NonFinite,
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
): ProjectiveHomogeneousPointF64 {
    val wF64 = interpolateProjectiveCompensatedF64(
        ProjectiveCompensatedF64(
            firstF64.wF64, firstF64.wResidualF64, firstF64.wUncertaintyF64,
            firstF64.wLowerTailF64, firstF64.wUpperTailF64,
            firstF64.wSubnormalUnitsF64,
            firstF64.wSubnormalResidualUnitsF64,
        ),
        ProjectiveCompensatedF64(
            secondF64.wF64, secondF64.wResidualF64, secondF64.wUncertaintyF64,
            secondF64.wLowerTailF64, secondF64.wUpperTailF64,
            secondF64.wSubnormalUnitsF64,
            secondF64.wSubnormalResidualUnitsF64,
        ),
        parameterF64,
    ) ?: return ProjectiveHomogeneousPointF64(Double.NaN, Double.NaN, Double.NaN)
    val xF64 = interpolateProjectiveCompensatedF64(firstF64.xExpansionF64(), secondF64.xExpansionF64(), parameterF64)
        ?: return ProjectiveHomogeneousPointF64(Double.NaN, Double.NaN, Double.NaN)
    val yF64 = interpolateProjectiveCompensatedF64(firstF64.yExpansionF64(), secondF64.yExpansionF64(), parameterF64)
        ?: return ProjectiveHomogeneousPointF64(Double.NaN, Double.NaN, Double.NaN)
    return ProjectiveHomogeneousPointF64(
        xF64 = xF64.leadingF64,
        yF64 = yF64.leadingF64,
        wF64 = wF64.leadingF64,
        wResidualF64 = wF64.residualF64,
        wUncertaintyF64 = wF64.uncertaintyF64,
        wLowerTailF64 = wF64.lowerTailF64,
        wUpperTailF64 = wF64.upperTailF64,
        wSubnormalUnitsF64 = wF64.subnormalUnitsF64,
        wSubnormalResidualUnitsF64 = wF64.subnormalResidualUnitsF64,
        xResidualF64 = xF64.residualF64,
        xUncertaintyF64 = xF64.uncertaintyF64,
        xLowerTailF64 = xF64.lowerTailF64,
        xUpperTailF64 = xF64.upperTailF64,
        xSubnormalUnitsF64 = xF64.subnormalUnitsF64,
        xSubnormalResidualUnitsF64 = xF64.subnormalResidualUnitsF64,
        yResidualF64 = yF64.residualF64,
        yUncertaintyF64 = yF64.uncertaintyF64,
        yLowerTailF64 = yF64.lowerTailF64,
        yUpperTailF64 = yF64.upperTailF64,
        ySubnormalUnitsF64 = yF64.subnormalUnitsF64,
        ySubnormalResidualUnitsF64 = yF64.subnormalResidualUnitsF64,
    )
}

private fun ProjectiveHomogeneousPointF64.xExpansionF64(): ProjectiveCompensatedF64 = ProjectiveCompensatedF64(
    xF64, xResidualF64, xUncertaintyF64, xLowerTailF64, xUpperTailF64,
    xSubnormalUnitsF64, xSubnormalResidualUnitsF64,
)

private fun ProjectiveHomogeneousPointF64.yExpansionF64(): ProjectiveCompensatedF64 = ProjectiveCompensatedF64(
    yF64, yResidualF64, yUncertaintyF64, yLowerTailF64, yUpperTailF64,
    ySubnormalUnitsF64, ySubnormalResidualUnitsF64,
)

private fun effectiveProjectiveWF64(pointF64: ProjectiveHomogeneousPointF64): Double =
    projectiveCompensatedValueF64(
        ProjectiveCompensatedF64(
            pointF64.wF64, pointF64.wResidualF64, pointF64.wUncertaintyF64,
            pointF64.wLowerTailF64, pointF64.wUpperTailF64,
            pointF64.wSubnormalUnitsF64,
            pointF64.wSubnormalResidualUnitsF64,
        ),
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

    override fun horizonCertificateF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHorizonCertificateF64 = fallbackF64?.horizonCertificateF64(
        matrixF64,
        startParameterF64,
        endParameterF64,
    ) ?: requireNotNull(centerF64).horizonCertificateF64(matrixF64, startParameterF64, endParameterF64)

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

    /**
     * W around an ellipse is a sinusoid plus a constant. Its extrema are therefore the endpoints
     * and the two stationary angles, which certify a true root even before an arc is split into
     * rational conics.
     */
    fun horizonCertificateF64(
        matrixF64: Matrix3x3F64,
        startParameterF64: Double,
        endParameterF64: Double,
    ): ProjectiveHorizonCertificateF64 {
        val cosRotationF64 = cos(rotationRadiansF64)
        val sinRotationF64 = sin(rotationRadiansF64)
        val cosineCoefficientF64 = matrixF64.persp0F64 * radiusXF64 * cosRotationF64 +
            matrixF64.persp1F64 * radiusXF64 * sinRotationF64
        val sineCoefficientF64 = -matrixF64.persp0F64 * radiusYF64 * sinRotationF64 +
            matrixF64.persp1F64 * radiusYF64 * cosRotationF64
        if (!cosineCoefficientF64.isFinite() || !sineCoefficientF64.isFinite()) {
            return ProjectiveHorizonCertificateF64.NonFinite
        }
        val extremumAngleF64 = atan2(sineCoefficientF64, cosineCoefficientF64)
        val parametersF64 = buildList {
            add(startParameterF64)
            add(endParameterF64)
            listOf(extremumAngleF64, extremumAngleF64 + PI).forEach { angleF64 ->
                parameterForAngleOrNullF64(angleF64)
                    ?.takeIf { it > startParameterF64 && it < endParameterF64 }
                    ?.let(::add)
            }
        }.sorted()
        val signsF64 = parametersF64.map { parameterF64 ->
            val pointF64 = pointAtF64(parameterF64)
            val homogeneousF64 = matrixF64.projectHomogeneousPointF64(pointF64)
                ?: return ProjectiveHorizonCertificateF64.NonFinite
            projectiveWSignF64(
                ProjectiveCompensatedF64(
                    homogeneousF64.wF64,
                    homogeneousF64.wResidualF64,
                    homogeneousF64.wUncertaintyF64,
                    homogeneousF64.wLowerTailF64,
                    homogeneousF64.wUpperTailF64,
                    homogeneousF64.wSubnormalUnitsF64,
                    homogeneousF64.wSubnormalResidualUnitsF64,
                ),
            )
        }
        if (signsF64.any { it == ProjectiveWSignF64.NonFinite }) return ProjectiveHorizonCertificateF64.NonFinite
        if (signsF64.any { it == ProjectiveWSignF64.Root }) return ProjectiveHorizonCertificateF64.Crossing
        if (signsF64.zipWithNext().any { (firstF64, secondF64) ->
                (firstF64 == ProjectiveWSignF64.Positive && secondF64 == ProjectiveWSignF64.Negative) ||
                    (firstF64 == ProjectiveWSignF64.Negative && secondF64 == ProjectiveWSignF64.Positive)
            }
        ) {
            return ProjectiveHorizonCertificateF64.Crossing
        }
        return if (signsF64.all { it == ProjectiveWSignF64.Positive } ||
            signsF64.all { it == ProjectiveWSignF64.Negative }
        ) ProjectiveHorizonCertificateF64.Separated else ProjectiveHorizonCertificateF64.Unknown
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
