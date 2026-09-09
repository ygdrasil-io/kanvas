package org.graphiks.math.matrix

import org.graphiks.math.geometry.ClipDeviceGeometryF64
import org.graphiks.math.geometry.ClipDeviceInputF64
import org.graphiks.math.geometry.ClipOperation
import org.graphiks.math.geometry.ClipPreparationInvalidSceneReason
import org.graphiks.math.geometry.ClipPreparationPolicyF64
import org.graphiks.math.geometry.ClipPreparationResourceLimitReason
import org.graphiks.math.geometry.ClipStackPreparationResult
import org.graphiks.math.geometry.ClipWorkUsageI64
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.geometry.RRectF64
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.prepareClipStackGeometryF32
import org.graphiks.math.geometry.toPathFillInputF64

/** Source geometry accepted by the matrix-only transformation façade. */
public sealed interface ClipTransformGeometryF64 {
    public class Rect(rectF64: RectF64) : ClipTransformGeometryF64 {
        private val snapshotF64 = rectF64.copyF64()
        public fun copyRectF64(): RectF64 = snapshotF64.copyF64()
    }
    public class RRect(rrectF64: RRectF64) : ClipTransformGeometryF64 {
        private val snapshotF64 = rrectF64.copyF64()
        public fun copyRRectF64(): RRectF64 = snapshotF64.copyF64()
    }
    public class Path(public val pathF32: PathF32) : ClipTransformGeometryF64
}

/** Immutable source clip plus its F64 homogeneous transform. */
public class ClipTransformInputF64 private constructor(
    internal val geometryF64: ClipTransformGeometryF64,
    public val matrixF64: Matrix3x3F64,
    public val operation: ClipOperation,
    public val antiAlias: Boolean,
) {
    public companion object {
        public fun of(geometryF64: ClipTransformGeometryF64, matrixF64: Matrix3x3F64, operation: ClipOperation, antiAlias: Boolean = true): ClipTransformInputF64 =
            ClipTransformInputF64(geometryF64.snapshotF64(), matrixF64, operation, antiAlias)
    }
}

/**
 * Transforms source clips and hands device-only snapshots to `:math:geometry`.  Projection costs
 * are debited before each mapping and are passed to geometry both as stack/frame snapshots and as
 * an entry-local baseline, so geometry cannot reset any cumulative limit.
 */
public fun prepareTransformedClipStackGeometryF32(
    entriesF64: List<ClipTransformInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult = try {
    val ledgerI64 = MatrixClipWorkLedgerI64(stackWorkUsageBeforeI64, frameWorkUsageBeforeI64, policyF64)
    ledgerI64.preflightEntryCountBeforeProjectionI64(entriesF64.size.toLong())
    val deviceInputsF64 = entriesF64.map { inputF64 ->
        val entryI64 = ledgerI64.beginEntryI64()
        val geometryF64 = transformClipGeometryF64(inputF64.geometryF64, inputF64.matrixF64, entryI64, policyF64)
        ClipDeviceInputF64.of(geometryF64, inputF64.operation, inputF64.antiAlias, entryI64.snapshotI64())
    }
    prepareClipStackGeometryF32(
        deviceInputsF64, targetDomainI32, policyF64,
        ledgerI64.stackSnapshotI64(), ledgerI64.frameSnapshotI64(),
    )
} catch (abort: MatrixClipAbort) {
    ClipStackPreparationResult.ResourceLimitExceeded(abort.reason)
} catch (_: MatrixClipInvalidAbort) {
    ClipStackPreparationResult.InvalidScene(ClipPreparationInvalidSceneReason.NonFiniteGeometry)
}

private fun transformClipGeometryF64(sourceF64: ClipTransformGeometryF64, matrixF64: Matrix3x3F64, entryI64: MatrixClipEntryLedgerI64, policyF64: ClipPreparationPolicyF64): ClipDeviceGeometryF64 {
    if (!matrixF64.isFinite()) throw MatrixClipInvalidAbort()
    return when (sourceF64) {
        is ClipTransformGeometryF64.Rect -> transformRectF64(sourceF64, matrixF64, entryI64, policyF64)
        is ClipTransformGeometryF64.RRect -> transformRRectF64(sourceF64, matrixF64, entryI64, policyF64)
        is ClipTransformGeometryF64.Path -> transformPathF64(sourceF64.pathF32, matrixF64, entryI64, policyF64)
    }
}

private fun transformRectF64(sourceF64: ClipTransformGeometryF64.Rect, matrixF64: Matrix3x3F64, entryI64: MatrixClipEntryLedgerI64, policyF64: ClipPreparationPolicyF64): ClipDeviceGeometryF64 {
    val transformClassF64 = matrixF64.classifyPathTransform()
    entryI64.debitBeforeProjectionI64(ClipWorkUsageI64(snapshotByteCountI64 = rectSnapshotByteCountI64))
    val rectF64 = sourceF64.copyRectF64()
    if (!rectF64.isFinite()) throw MatrixClipInvalidAbort()
    if (transformClassF64 == PathTransformClass.Identity || transformClassF64 == PathTransformClass.AxisAlignedAffine) {
        entryI64.debitBeforeProjectionI64(ClipWorkUsageI64(attemptedEdgeCountI64 = 1L, snapshotByteCountI64 = 16L))
        val first = matrixF64.mapAffineClipPointF64(Point2F64(rectF64.left, rectF64.top))
        val second = matrixF64.mapAffineClipPointF64(Point2F64(rectF64.right, rectF64.bottom))
        return ClipDeviceGeometryF64.Rect(RectF64(minOf(first.x, second.x), minOf(first.y, second.y), maxOf(first.x, second.x), maxOf(first.y, second.y)))
    }
    return transformPathInputF64(
        rectF64.toPathFillInputF64 { workI64 -> entryI64.debitBeforeProjectionI64(workI64.toClipUsageI64()) },
        matrixF64, entryI64, policyF64,
    )
}

private fun transformRRectF64(sourceF64: ClipTransformGeometryF64.RRect, matrixF64: Matrix3x3F64, entryI64: MatrixClipEntryLedgerI64, policyF64: ClipPreparationPolicyF64): ClipDeviceGeometryF64 {
    val transformClassF64 = matrixF64.classifyPathTransform()
    entryI64.debitBeforeProjectionI64(ClipWorkUsageI64(snapshotByteCountI64 = rrectSnapshotByteCountI64))
    val sourceRRectF64 = sourceF64.copyRRectF64()
    if (!sourceRRectF64.isFinite()) throw MatrixClipInvalidAbort()
    // Normalize once before classifying the realization so typed and path branches share Skia's
    // exact F64 radii authority, including reflection remapping below.
    val rrectF64 = sourceRRectF64.normalizedForSkiaF64()
    if (transformClassF64 == PathTransformClass.Identity || transformClassF64 == PathTransformClass.AxisAlignedAffine) {
        entryI64.debitBeforeProjectionI64(ClipWorkUsageI64(snapshotByteCountI64 = rectSnapshotByteCountI64))
        val bounds = rrectF64.copyRectF64()
        entryI64.debitBeforeProjectionI64(ClipWorkUsageI64(attemptedEdgeCountI64 = 1L, snapshotByteCountI64 = 48L))
        val first = matrixF64.mapAffineClipPointF64(Point2F64(bounds.left, bounds.top))
        val second = matrixF64.mapAffineClipPointF64(Point2F64(bounds.right, bounds.bottom))
        val sx = kotlin.math.abs(matrixF64.sxF64); val sy = kotlin.math.abs(matrixF64.syF64)
        fun sourceRadiiF64(deviceLeft: Boolean, deviceTop: Boolean): org.graphiks.math.geometry.CornerRadiiF64 {
            val sourceLeft = if (matrixF64.sxF64 < 0.0) !deviceLeft else deviceLeft
            val sourceTop = if (matrixF64.syF64 < 0.0) !deviceTop else deviceTop
            return when {
                sourceLeft && sourceTop -> rrectF64.topLeft
                !sourceLeft && sourceTop -> rrectF64.topRight
                !sourceLeft -> rrectF64.bottomRight
                else -> rrectF64.bottomLeft
            }
        }
        fun mappedRadiiF64(deviceLeft: Boolean, deviceTop: Boolean): org.graphiks.math.geometry.CornerRadiiF64 {
            val source = sourceRadiiF64(deviceLeft, deviceTop)
            return org.graphiks.math.geometry.CornerRadiiF64.of(source.xF64 * sx, source.yF64 * sy)
        }
        return ClipDeviceGeometryF64.RRect(RRectF64.of(
            RectF64(minOf(first.x, second.x), minOf(first.y, second.y), maxOf(first.x, second.x), maxOf(first.y, second.y)),
            mappedRadiiF64(deviceLeft = true, deviceTop = true),
            mappedRadiiF64(deviceLeft = false, deviceTop = true),
            mappedRadiiF64(deviceLeft = false, deviceTop = false),
            mappedRadiiF64(deviceLeft = true, deviceTop = false),
        ))
    }
    return transformPathInputF64(
        rrectF64.toPathFillInputF64 { workI64 -> entryI64.debitBeforeProjectionI64(workI64.toClipUsageI64()) },
        matrixF64, entryI64, policyF64,
    )
}

private const val rectSnapshotByteCountI64: Long = 16L
private const val rrectSnapshotByteCountI64: Long = 48L

private fun transformPathF64(pathF32: PathF32, matrixF64: Matrix3x3F64, entryI64: MatrixClipEntryLedgerI64, policyF64: ClipPreparationPolicyF64): ClipDeviceGeometryF64 {
    if (matrixF64.classifyPathTransform() != PathTransformClass.Perspective) {
        return try {
            ClipDeviceGeometryF64.Path(matrixF64.mapAffinePathF32ToFillInputF64(pathF32, PathTransformWorkDebitI64 {
                entryI64.debitBeforeProjectionI64(it.toClipUsageI64())
            }))
        } catch (_: IllegalArgumentException) {
            throw MatrixClipInvalidAbort()
        }
    }
    val entryBeforeI64 = entryI64.snapshotI64()
    val frameBeforeI64 = entryI64.frameSnapshotI64()
    return when (val projectedF64 = matrixF64.prepareProjectedPathFillInputF64(
        pathF32, policyF64.pathPolicyF64, policyF64.toProjectiveWorkPolicyF64(),
        entryBeforeI64.toPathStrokeUsageI64(), frameBeforeI64.toPathStrokeUsageI64(),
        beforeWorkDebitI64 = { entryI64.debitBeforeProjectionI64(it.toClipUsageI64()) },
    )) {
        is PathProjectivePreparationResult.Ready -> ClipDeviceGeometryF64.Path(projectedF64.inputF64)
        is PathProjectivePreparationResult.Empty -> ClipDeviceGeometryF64.Path(PathFillInputF64.of(pathF32.fillRule, emptyList()))
        is PathProjectivePreparationResult.InvalidScene -> throw MatrixClipInvalidAbort()
        is PathProjectivePreparationResult.ResourceLimitExceeded -> throw MatrixClipAbort(ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit)
    }
}

private fun transformPathInputF64(inputF64: PathFillInputF64, matrixF64: Matrix3x3F64, entryI64: MatrixClipEntryLedgerI64, policyF64: ClipPreparationPolicyF64): ClipDeviceGeometryF64 {
    if (matrixF64.classifyPathTransform() != PathTransformClass.Perspective) {
        return try {
            ClipDeviceGeometryF64.Path(matrixF64.mapAffinePathFillInputF64(inputF64, PathTransformWorkDebitI64 {
                entryI64.debitBeforeProjectionI64(it.toClipUsageI64())
            }))
        } catch (_: IllegalArgumentException) {
            throw MatrixClipInvalidAbort()
        }
    }
    val entryBeforeI64 = entryI64.snapshotI64()
    val frameBeforeI64 = entryI64.frameSnapshotI64()
    return when (val projectedF64 = matrixF64.prepareProjectedPathFillInputF64(
        inputF64, policyF64.pathPolicyF64, policyF64.toProjectiveWorkPolicyF64(),
        entryBeforeI64.toPathStrokeUsageI64(), frameBeforeI64.toPathStrokeUsageI64(),
        beforeWorkDebitI64 = { entryI64.debitBeforeProjectionI64(it.toClipUsageI64()) },
    )) {
        is PathProjectivePreparationResult.Ready -> {
            ClipDeviceGeometryF64.Path(projectedF64.inputF64)
        }
        is PathProjectivePreparationResult.Empty -> {
            ClipDeviceGeometryF64.Path(PathFillInputF64.of(inputF64.fillRule, emptyList()))
        }
        is PathProjectivePreparationResult.InvalidScene -> throw MatrixClipInvalidAbort()
        is PathProjectivePreparationResult.ResourceLimitExceeded -> throw MatrixClipAbort(ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit)
    }
}

private fun Matrix3x3F64.mapAffineClipPointF64(pointF64: Point2F64): Point2F64 = Point2F64(sxF64 * pointF64.x + kxF64 * pointF64.y + txF64, kyF64 * pointF64.x + syF64 * pointF64.y + tyF64).also { if (!it.isFinite()) throw MatrixClipInvalidAbort() }

private fun ClipPreparationPolicyF64.toProjectiveWorkPolicyF64(): PathStrokePolicyF64 = PathStrokePolicyF64(
    limitsI32 = PathStrokeLimitsI32(
        maxAttemptedGeometryUnitsPerPathI32 = limitsI32.maxAttemptedEdgesPerEntryI32,
        maxAttemptedGeometryUnitsPerFrameI32 = limitsI32.maxAttemptedEdgesPerFrameI32,
        maxEmittedVertexCountPerPathI32 = limitsI32.maxEmittedVertexCountPerEntryI32,
        maxEmittedVertexCountPerFrameI32 = limitsI32.maxEmittedVertexCountPerFrameI32,
        maxEmittedIndexCountPerPathI32 = limitsI32.maxEmittedIndexCountPerEntryI32,
        maxEmittedIndexCountPerFrameI32 = limitsI32.maxEmittedIndexCountPerFrameI32,
    ),
    limitsI64 = PathStrokeLimitsI64(limitsI64.maxSnapshotByteCountPerEntryI64, limitsI64.maxSnapshotByteCountPerFrameI64),
)
private fun ClipWorkUsageI64.toPathStrokeUsageI64(): PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(attemptedEdgeCountI64, emittedVertexCountI64, emittedIndexCountI64, snapshotByteCountI64)
private fun PathStrokeWorkUsageI64.toClipUsageI64(): ClipWorkUsageI64 = ClipWorkUsageI64(attemptedGeometryUnitCountI64, emittedVertexCountI64, emittedIndexCountI64, snapshotByteCountI64)

private fun ClipTransformGeometryF64.snapshotF64(): ClipTransformGeometryF64 = when (this) {
    is ClipTransformGeometryF64.Rect -> ClipTransformGeometryF64.Rect(copyRectF64())
    is ClipTransformGeometryF64.RRect -> ClipTransformGeometryF64.RRect(copyRRectF64())
    is ClipTransformGeometryF64.Path -> ClipTransformGeometryF64.Path(pathF32)
}

private class MatrixClipInvalidAbort : RuntimeException()
private class MatrixClipAbort(val reason: ClipPreparationResourceLimitReason) : RuntimeException()

private class MatrixClipWorkLedgerI64(stackI64: ClipWorkUsageI64, frameI64: ClipWorkUsageI64, private val policyF64: ClipPreparationPolicyF64) {
    private var stackUsageI64 = stackI64; private var frameUsageI64 = frameI64
    init { checkUsageI64(stackI64, Scope.Stack); checkUsageI64(frameI64, Scope.Frame) }
    fun preflightEntryCountBeforeProjectionI64(entryCountI64: Long) {
        if (entryCountI64 < 0L) throw MatrixClipAbort(ClipPreparationResourceLimitReason.HostSizeOverflow)
        val deltaI64 = ClipWorkUsageI64(clipEntryCountI64 = entryCountI64)
        checkUsageI64(addI64(stackUsageI64, deltaI64), Scope.Stack)
        checkUsageI64(addI64(frameUsageI64, deltaI64), Scope.Frame)
    }
    fun beginEntryI64(): MatrixClipEntryLedgerI64 = MatrixClipEntryLedgerI64(this)
    fun stackSnapshotI64(): ClipWorkUsageI64 = stackUsageI64; fun frameSnapshotI64(): ClipWorkUsageI64 = frameUsageI64
    fun preflightI64(entryI64: MatrixClipEntryLedgerI64, deltaI64: ClipWorkUsageI64) { checkUsageI64(addI64(entryI64.usageI64, deltaI64), Scope.Entry); checkUsageI64(addI64(stackUsageI64, deltaI64), Scope.Stack); checkUsageI64(addI64(frameUsageI64, deltaI64), Scope.Frame) }
    fun debitI64(entryI64: MatrixClipEntryLedgerI64, deltaI64: ClipWorkUsageI64) {
        val nextEntry = addI64(entryI64.usageI64, deltaI64); val nextStack = addI64(stackUsageI64, deltaI64); val nextFrame = addI64(frameUsageI64, deltaI64)
        checkUsageI64(nextEntry, Scope.Entry); checkUsageI64(nextStack, Scope.Stack); checkUsageI64(nextFrame, Scope.Frame)
        entryI64.usageI64 = nextEntry; stackUsageI64 = nextStack; frameUsageI64 = nextFrame
    }
    private enum class Scope { Entry, Stack, Frame }
    private fun checkUsageI64(usage: ClipWorkUsageI64, scope: Scope) {
        val limits32 = policyF64.limitsI32; val limits64 = policyF64.limitsI64
        val attempted = when (scope) { Scope.Entry -> limits32.maxAttemptedEdgesPerEntryI32; Scope.Stack -> limits32.maxAttemptedEdgesPerStackI32; Scope.Frame -> limits32.maxAttemptedEdgesPerFrameI32 }
        val vertices = when (scope) { Scope.Entry -> limits32.maxEmittedVertexCountPerEntryI32; Scope.Stack -> limits32.maxEmittedVertexCountPerStackI32; Scope.Frame -> limits32.maxEmittedVertexCountPerFrameI32 }
        val indices = when (scope) { Scope.Entry -> limits32.maxEmittedIndexCountPerEntryI32; Scope.Stack -> limits32.maxEmittedIndexCountPerStackI32; Scope.Frame -> limits32.maxEmittedIndexCountPerFrameI32 }
        val bytes = when (scope) { Scope.Entry -> limits64.maxSnapshotByteCountPerEntryI64; Scope.Stack -> limits64.maxSnapshotByteCountPerStackI64; Scope.Frame -> limits64.maxSnapshotByteCountPerFrameI64 }
        val entries = when (scope) { Scope.Entry -> 0; Scope.Stack -> limits32.maxClipEntryCountPerStackI32; Scope.Frame -> limits32.maxClipEntryCountPerFrameI32 }
        if (scope != Scope.Entry && usage.clipEntryCountI64 > entries.toLong()) throw MatrixClipAbort(when (scope) { Scope.Stack -> ClipPreparationResourceLimitReason.StackEntryCountLimit; Scope.Frame -> ClipPreparationResourceLimitReason.FrameEntryCountLimit; Scope.Entry -> error("entry count is not a per-entry budget") })
        if (usage.attemptedEdgeCountI64 > attempted.toLong()) throw MatrixClipAbort(when (scope) { Scope.Entry -> ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit; Scope.Stack -> ClipPreparationResourceLimitReason.StackAttemptedEdgeLimit; Scope.Frame -> ClipPreparationResourceLimitReason.FrameAttemptedEdgeLimit })
        if (usage.emittedVertexCountI64 > vertices.toLong()) throw MatrixClipAbort(when (scope) { Scope.Entry -> ClipPreparationResourceLimitReason.EntryVertexLimit; Scope.Stack -> ClipPreparationResourceLimitReason.StackVertexLimit; Scope.Frame -> ClipPreparationResourceLimitReason.FrameVertexLimit })
        if (usage.emittedIndexCountI64 > indices.toLong()) throw MatrixClipAbort(when (scope) { Scope.Entry -> ClipPreparationResourceLimitReason.EntryIndexLimit; Scope.Stack -> ClipPreparationResourceLimitReason.StackIndexLimit; Scope.Frame -> ClipPreparationResourceLimitReason.FrameIndexLimit })
        if (usage.snapshotByteCountI64 > bytes) throw MatrixClipAbort(when (scope) { Scope.Entry -> ClipPreparationResourceLimitReason.EntrySnapshotByteLimit; Scope.Stack -> ClipPreparationResourceLimitReason.StackSnapshotByteLimit; Scope.Frame -> ClipPreparationResourceLimitReason.FrameSnapshotByteLimit })
    }
}
private class MatrixClipEntryLedgerI64(private val parentI64: MatrixClipWorkLedgerI64) { internal var usageI64 = ClipWorkUsageI64(); fun debitBeforeProjectionI64(deltaI64: ClipWorkUsageI64) = parentI64.debitI64(this, deltaI64); fun preflightBeforeProjectionI64(deltaI64: ClipWorkUsageI64) = parentI64.preflightI64(this, deltaI64); fun snapshotI64(): ClipWorkUsageI64 = usageI64; fun frameSnapshotI64(): ClipWorkUsageI64 = parentI64.frameSnapshotI64() }
private fun addI64(first: ClipWorkUsageI64, second: ClipWorkUsageI64): ClipWorkUsageI64 = try { ClipWorkUsageI64(addComponent(first.attemptedEdgeCountI64, second.attemptedEdgeCountI64), addComponent(first.emittedVertexCountI64, second.emittedVertexCountI64), addComponent(first.emittedIndexCountI64, second.emittedIndexCountI64), addComponent(first.snapshotByteCountI64, second.snapshotByteCountI64), addComponent(first.clipEntryCountI64, second.clipEntryCountI64)) } catch (_: IllegalStateException) { throw MatrixClipAbort(ClipPreparationResourceLimitReason.HostSizeOverflow) }
private fun addComponent(first: Long, second: Long): Long { if (first > Long.MAX_VALUE - second) throw IllegalStateException(); return first + second }
