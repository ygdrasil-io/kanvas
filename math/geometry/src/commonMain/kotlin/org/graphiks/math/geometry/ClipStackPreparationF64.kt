package org.graphiks.math.geometry

import kotlin.math.ceil
import kotlin.math.floor

/** Path strategy and clip budgets used to prepare a device clip stack. */
public data class ClipPreparationPolicyF64(
    public val pathPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    public val limitsI32: ClipPreparationLimitsI32 = ClipPreparationLimitsI32(),
    public val limitsI64: ClipPreparationLimitsI64 = ClipPreparationLimitsI64(),
)

/** Transactional result: failed preparations publish neither entries nor work snapshots. */
public sealed interface ClipStackPreparationResult {
    public class Ready(
        entriesF32: List<ClipPreparedEntryF32>,
        public val stackWorkUsageAfterI64: ClipWorkUsageI64,
        public val frameWorkUsageAfterI64: ClipWorkUsageI64,
    ) : ClipStackPreparationResult {
        public val entriesF32: List<ClipPreparedEntryF32> = ReadOnlyClipPreparedEntriesF32(entriesF32)
    }

    public data class InvalidScene(public val reason: ClipPreparationInvalidSceneReason) : ClipStackPreparationResult
    public data class ResourceLimitExceeded(public val reason: ClipPreparationResourceLimitReason) : ClipStackPreparationResult
}

public enum class ClipPreparationInvalidSceneReason { NonFiniteGeometry, PathPreparation }

/**
 * Converts immutable device F64 inputs to bounded device F32 snapshots.  An entry ledger starts
 * from its projection-only cost, while the shared ledger starts from the supplied stack/frame
 * snapshots; no limit is reset between stages.
 */
public fun prepareClipStackGeometryF32(
    entriesF64: List<ClipDeviceInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult = try {
    val ledgerI64 = ClipWorkLedgerI64(stackWorkUsageBeforeI64, frameWorkUsageBeforeI64, policyF64)
    val preparedF32 = entriesF64.map { inputF64 ->
        val entryLedgerI64 = ledgerI64.beginEntryI64(inputF64.entryWorkUsageBeforeGeometryI64)
        prepareSingleClipGeometryF32(inputF64, targetDomainI32, policyF64, entryLedgerI64)
    }
    ClipStackPreparationResult.Ready(
        preparedF32,
        ledgerI64.snapshotStackUsageAfterI64(),
        ledgerI64.snapshotFrameUsageAfterI64(),
    )
} catch (abort: ClipPreparationAbort) {
    ClipStackPreparationResult.ResourceLimitExceeded(abort.reason)
} catch (_: ClipInvalidAbort) {
    ClipStackPreparationResult.InvalidScene(ClipPreparationInvalidSceneReason.NonFiniteGeometry)
}

private fun prepareSingleClipGeometryF32(
    inputF64: ClipDeviceInputF64,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64,
    ledgerI64: ClipEntryWorkLedgerI64,
): ClipPreparedEntryF32 = when (val sourceF64 = inputF64.geometryF64) {
    is ClipDeviceGeometryF64.Rect -> prepareRectClipF32(inputF64, sourceF64.copyRectF64(), targetDomainI32, ledgerI64)
    is ClipDeviceGeometryF64.RRect -> prepareRRectClipF32(inputF64, sourceF64.copyRRectF64(), targetDomainI32, ledgerI64)
    is ClipDeviceGeometryF64.Path -> preparePathClipF32(inputF64, sourceF64.inputF64, targetDomainI32, policyF64, ledgerI64)
}

private fun prepareRectClipF32(
    inputF64: ClipDeviceInputF64,
    rectF64: RectF64,
    targetDomainI32: RectI32,
    ledgerI64: ClipEntryWorkLedgerI64,
): ClipPreparedEntryF32 {
    if (!rectF64.isFinite()) throw ClipInvalidAbort()
    val rectF32 = rectF64.toRectF32OrNull() ?: throw ClipInvalidAbort()
    val scissorI32 = rectF64.conservativeScissorI32(targetDomainI32)
    if (!rectF64.isEmpty) ledgerI64.debitBeforeEmissionI64(ClipWorkUsageI64(4L, 4L, 6L, 16L))
    return ClipPreparedEntryF32(
        if (rectF64.isEmpty) ClipGeometryF32.Empty else ClipGeometryF32.Rect(rectF32),
        inputF64.operation, inputF64.antiAlias, inverseFill = false, scissorI32,
    )
}

private fun prepareRRectClipF32(
    inputF64: ClipDeviceInputF64,
    rrectF64: RRectF64,
    targetDomainI32: RectI32,
    ledgerI64: ClipEntryWorkLedgerI64,
): ClipPreparedEntryF32 {
    if (!rrectF64.isFinite()) throw ClipInvalidAbort()
    val boundsF64 = rrectF64.copyRectF64()
    val boundsF32 = boundsF64.toRectF32OrNull() ?: throw ClipInvalidAbort()
    val radiiF32 = listOf(rrectF64.topLeft, rrectF64.topRight, rrectF64.bottomRight, rrectF64.bottomLeft).map {
        CornerRadiiF32(it.xF64.toFiniteF32OrNull() ?: throw ClipInvalidAbort(), it.yF64.toFiniteF32OrNull() ?: throw ClipInvalidAbort())
    }
    val scissorI32 = boundsF64.conservativeScissorI32(targetDomainI32)
    if (!boundsF64.isEmpty) ledgerI64.debitBeforeEmissionI64(ClipWorkUsageI64(4L, 4L, 6L, 48L))
    val geometryF32 = if (boundsF64.isEmpty) ClipGeometryF32.Empty else ClipGeometryF32.RRect(
        RRectF32.of(
            boundsF32, radiiF32[0], radiiF32[1], radiiF32[2], radiiF32[3],
        ),
    )
    return ClipPreparedEntryF32(geometryF32, inputF64.operation, inputF64.antiAlias, false, scissorI32)
}

private fun preparePathClipF32(
    inputF64: ClipDeviceInputF64,
    pathF64: PathFillInputF64,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64,
    ledgerI64: ClipEntryWorkLedgerI64,
): ClipPreparedEntryF32 {
    val minimumEdgesI64 = estimatedPathEdgesI64(pathF64)
    ledgerI64.preflightBeforeEmissionI64(pathFinalGeometryUpperBoundI64(policyF64))
    ledgerI64.debitBeforeEmissionI64(ClipWorkUsageI64(attemptedEdgeCountI64 = minimumEdgesI64))
    val prepared = preparePathFillGeometryF32(pathF64, policyF64.pathPolicyF64)
    return when (prepared) {
        is PathFillPreparationResult.Ready -> {
            val remainingEdgesI64 = prepared.attemptedEdgeCountI32.toLong() - minimumEdgesI64
            ledgerI64.debitBeforeEmissionI64(
                ClipWorkUsageI64(
                    attemptedEdgeCountI64 = remainingEdgesI64.coerceAtLeast(0L),
                    emittedVertexCountI64 = prepared.geometryF32.vertexCostI64,
                    emittedIndexCountI64 = prepared.geometryF32.indexCostI64,
                    snapshotByteCountI64 = prepared.geometryF32.snapshotByteCostI64,
                ),
            )
            ClipPreparedEntryF32(
                ClipGeometryF32.Path(prepared.geometryF32), inputF64.operation, inputF64.antiAlias,
                pathF64.fillRule.isInverseFill(), if (pathF64.fillRule.isInverseFill()) targetDomainI32.copyClipRectI32() else prepared.geometryF32.copyConservativeScissorI32().intersectClipDomainI32(targetDomainI32),
            )
        }

        is PathFillPreparationResult.Empty -> {
            ledgerI64.debitBeforeEmissionI64(ClipWorkUsageI64(attemptedEdgeCountI64 = prepared.attemptedEdgeCountI32.toLong() - minimumEdgesI64))
            ClipPreparedEntryF32(ClipGeometryF32.Empty, inputF64.operation, inputF64.antiAlias, pathF64.fillRule.isInverseFill(), if (pathF64.fillRule.isInverseFill()) targetDomainI32.copyClipRectI32() else RectI32.Empty)
        }

        is PathFillPreparationResult.InvalidScene -> throw ClipInvalidAbort()
        is PathFillPreparationResult.ResourceLimitExceeded -> throw ClipPreparationAbort(
            ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit,
        )
    }
}

private class ClipInvalidAbort : RuntimeException()

private fun RectF64.conservativeScissorI32(targetDomainI32: RectI32): RectI32 {
    if (isEmpty) return RectI32.Empty
    val leftI32 = floor(left).coerceIn(targetDomainI32.left.toDouble(), targetDomainI32.right.toDouble()).toInt()
    val topI32 = floor(top).coerceIn(targetDomainI32.top.toDouble(), targetDomainI32.bottom.toDouble()).toInt()
    val rightI32 = ceil(right).coerceIn(targetDomainI32.left.toDouble(), targetDomainI32.right.toDouble()).toInt()
    val bottomI32 = ceil(bottom).coerceIn(targetDomainI32.top.toDouble(), targetDomainI32.bottom.toDouble()).toInt()
    return RectI32(leftI32, topI32, rightI32, bottomI32)
}

private fun RectI32.intersectClipDomainI32(domainI32: RectI32): RectI32 {
    val leftI32 = maxOf(left, domainI32.left)
    val topI32 = maxOf(top, domainI32.top)
    val rightI32 = minOf(right, domainI32.right)
    val bottomI32 = minOf(bottom, domainI32.bottom)
    return if (leftI32 < rightI32 && topI32 < bottomI32) RectI32(leftI32, topI32, rightI32, bottomI32) else RectI32.Empty
}

private fun FillRule.isInverseFill(): Boolean = this == FillRule.INVERSE_WINDING || this == FillRule.INVERSE_EVEN_ODD

private fun pathFinalGeometryUpperBoundI64(policyF64: ClipPreparationPolicyF64): ClipWorkUsageI64 {
    val edgeCountI64 = policyF64.pathPolicyF64.limitsI32.maxAttemptedEdgesPerPathI32.toLong()
    return ClipWorkUsageI64(0L, edgeCountI64 * 3L + 4L, edgeCountI64 * 3L + 6L, edgeCountI64 * 40L + 16L)
}

private fun Double.toFiniteF32OrNull(): Float? = takeIf { it.isFinite() && kotlin.math.abs(it) <= Float.MAX_VALUE.toDouble() }?.toFloat()
private fun RectF64.toRectF32OrNull(): RectF32? {
    val leftF32 = left.toFiniteF32OrNull() ?: return null; val topF32 = top.toFiniteF32OrNull() ?: return null
    val rightF32 = right.toFiniteF32OrNull() ?: return null; val bottomF32 = bottom.toFiniteF32OrNull() ?: return null
    return RectF32(leftF32, topF32, rightF32, bottomF32)
}
private fun RectI32.copyClipRectI32(): RectI32 = RectI32(left, top, right, bottom)

private class ReadOnlyClipPreparedEntriesF32(entriesF32: Collection<ClipPreparedEntryF32>) : AbstractList<ClipPreparedEntryF32>() {
    private val valuesF32: Array<ClipPreparedEntryF32> = entriesF32.toTypedArray()
    override val size: Int get() = valuesF32.size
    override fun get(index: Int): ClipPreparedEntryF32 = valuesF32[index]
}

private fun estimatedPathEdgesI64(pathF64: PathFillInputF64): Long {
    var resultI64 = 0L
    var hasContour = false
    pathF64.forEach { segmentF64 ->
        when (segmentF64) {
            is PathFillSegmentF64.MoveTo -> hasContour = true
            is PathFillSegmentF64.LineTo,
            is PathFillSegmentF64.QuadTo,
            is PathFillSegmentF64.CubicTo,
            is PathFillSegmentF64.ArcTo,
            PathFillSegmentF64.Close,
            -> if (hasContour) resultI64++
        }
    }
    return resultI64
}
