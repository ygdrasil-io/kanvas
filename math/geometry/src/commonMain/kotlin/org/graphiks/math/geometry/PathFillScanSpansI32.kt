package org.graphiks.math.geometry

/** One non-empty, device-pixel scanline interval `[leftI32, rightI32)`. */
public data class PathFillScanSpanI32(
    public val leftI32: Int,
    public val rightI32: Int,
    public val yI32: Int,
) {
    init {
        require(leftI32 < rightI32)
    }
}

/** Immutable fixed-point raster authority for one direct path-fill triangle. */
public sealed class PathFillScanSpansI32 protected constructor(domainI32: RectI32) {
    private val domainSnapshotI32: RectI32 = domainI32.copy()

    init {
        require(domainSnapshotI32.isSorted())
    }

    /** The target-domain rectangle retained with this device-space authority. */
    public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()

    public abstract val spanCountI32: Int

    public abstract fun copySpansI32(): List<PathFillScanSpanI32>

    /** Geometry selected upstream, whose finite device domain has no covered scanline. */
    public class Empty internal constructor(domainI32: RectI32) : PathFillScanSpansI32(domainI32) {
        override val spanCountI32: Int = 0

        override fun copySpansI32(): List<PathFillScanSpanI32> = emptyList()
    }

    /** Geometry selected upstream, with one ordered non-empty interval per covered scanline. */
    public class NonEmpty internal constructor(
        domainI32: RectI32,
        spansI32: List<PathFillScanSpanI32>,
    ) : PathFillScanSpansI32(domainI32) {
        private val spansSnapshotI32: List<PathFillScanSpanI32> = spansI32.toList()

        init {
            require(spansSnapshotI32.isNotEmpty())
            require(spansSnapshotI32.zipWithNext().all { (first, second) -> first.yI32 < second.yI32 })
            val domainSnapshotI32 = copyDomainI32()
            require(spansSnapshotI32.all { spanI32 ->
                spanI32.leftI32 >= domainSnapshotI32.left &&
                    spanI32.rightI32 <= domainSnapshotI32.right &&
                    spanI32.yI32 >= domainSnapshotI32.top &&
                    spanI32.yI32 < domainSnapshotI32.bottom
            })
        }

        override val spanCountI32: Int get() = spansSnapshotI32.size

        override fun copySpansI32(): List<PathFillScanSpanI32> = spansSnapshotI32.toList()
    }

    /**
     * Rebases the retained device authority into one target exactly once.
     *
     * The result is refused, rather than clamped, when either the retained domain or one unit-high
     * scissor cannot be represented in the target's local I32 coordinates.
     */
    public fun localScissorsI32OrNull(
        originDeviceI32: Point2I32,
        targetExtentI32: SizeI32,
    ): PathFillScanScissorsI32? {
        if (targetExtentI32.isEmpty()) return null
        val localDomainI32 = copyDomainI32().subtractOriginCheckedI32OrNull(originDeviceI32) ?: return null
        if (!localDomainI32.isWithinExtentI32(targetExtentI32)) return null

        val localScissorsI32 = ArrayList<RectI32>(spanCountI32)
        for (spanI32 in copySpansI32()) {
            val deviceBottomI32 = checkedScanAddI32(spanI32.yI32, 1) ?: return null
            val localLeftI32 = checkedScanSubtractI32(spanI32.leftI32, originDeviceI32.x) ?: return null
            val localRightI32 = checkedScanSubtractI32(spanI32.rightI32, originDeviceI32.x) ?: return null
            val localTopI32 = checkedScanSubtractI32(spanI32.yI32, originDeviceI32.y) ?: return null
            val localBottomI32 = checkedScanSubtractI32(deviceBottomI32, originDeviceI32.y) ?: return null
            val expectedLocalBottomI32 = checkedScanAddI32(localTopI32, 1) ?: return null
            if (localRightI32 <= localLeftI32 || localBottomI32 != expectedLocalBottomI32) return null
            val localScissorI32 = RectI32(localLeftI32, localTopI32, localRightI32, localBottomI32)
            if (!localScissorI32.isWithinExtentI32(targetExtentI32)) return null
            localScissorsI32 += localScissorI32
        }
        return PathFillScanScissorsI32(originDeviceI32, localDomainI32, localScissorsI32)
    }
}

/** Immutable local scissor payload, rebased from one [PathFillScanSpansI32] authority. */
public class PathFillScanScissorsI32 internal constructor(
    originDeviceI32: Point2I32,
    domainI32: RectI32,
    scissorsI32: List<RectI32>,
) {
    private val originDeviceSnapshotI32 = Point2I32(originDeviceI32.x, originDeviceI32.y)
    private val domainSnapshotI32 = domainI32.copy()
    private val scissorsSnapshotI32 = scissorsI32.map(RectI32::copy)

    init {
        require(domainSnapshotI32.isSorted())
        require(scissorsSnapshotI32.all { scissorI32 ->
            !scissorI32.isEmpty && scissorI32.height64() == 1L &&
                domainSnapshotI32.containsNoEmptyCheck(scissorI32)
        })
    }

    public val scissorCountI32: Int get() = scissorsSnapshotI32.size

    public fun copyOriginDeviceI32(): Point2I32 =
        Point2I32(originDeviceSnapshotI32.x, originDeviceSnapshotI32.y)

    public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()

    public fun copyScissorsI32(): List<RectI32> = scissorsSnapshotI32.map(RectI32::copy)
}

public enum class PathFillScanRefusalReasonI32 { NumericRange, SpanLimit }

public sealed interface PathFillScanPreparationI32 {
    public data class Ready(public val spansI32: PathFillScanSpansI32) : PathFillScanPreparationI32

    public data class Refused(public val reason: PathFillScanRefusalReasonI32) : PathFillScanPreparationI32
}

/**
 * Replays Skia's non-AA `SkEdge::setLine`/`walk_edges` fixed-point scan conversion.
 * F32 vertices first become FDot6 through truncation toward zero; every following operation is
 * integral and checked, so common JVM and JS executions make the same span decision.
 */
public fun preparePathFillScanSpansI32(
    triangleF32: PathFillDirectTriangleF32,
    domainI32: RectI32,
    maxSpanCountI32: Int,
): PathFillScanPreparationI32 {
    val verticesF32 = triangleF32.copyVerticesF32()
    val indicesI32 = triangleF32.copyIndicesI32()
    if (indicesI32.any { it !in 0 until verticesF32.size / 2 }) return scanNumericRangeRefusal()
    val verticesFDot6 = IntArray(6)
    for (indexI32 in verticesFDot6.indices) {
        verticesFDot6[indexI32] = truncateF32ToFDot6I32OrNull(verticesF32[indexI32])
            ?: return scanNumericRangeRefusal()
    }
    if (maxSpanCountI32 < 0) return scanSpanLimitRefusal()
    if (domainI32.isEmpty) return PathFillScanPreparationI32.Ready(PathFillScanSpansI32.Empty(domainI32))

    val points = indicesI32.map { indexI32 ->
        ScanPointFDot6(verticesFDot6[indexI32 * 2], verticesFDot6[indexI32 * 2 + 1])
    }
    val triangleTopI32 = points.minOf { pointI32 -> roundFDot6ToI32(pointI32.yI32) }
    val triangleBottomI32 = points.maxOf { pointI32 -> roundFDot6ToI32(pointI32.yI32) }
    if (triangleTopI32 >= triangleBottomI32) {
        return PathFillScanPreparationI32.Ready(PathFillScanSpansI32.Empty(domainI32))
    }
    val edges = ArrayList<ScanEdgeI32>(3)
    for ((first, second) in listOf(points[0] to points[1], points[1] to points[2], points[2] to points[0])) {
        if (first.yI32 == second.yI32) continue
        if (roundFDot6ToI32(first.yI32) == roundFDot6ToI32(second.yI32)) continue
        edges += ScanEdgeI32.fromPointsOrNull(first, second) ?: return scanNumericRangeRefusal()
    }
    if (edges.isEmpty()) return PathFillScanPreparationI32.Ready(PathFillScanSpansI32.Empty(domainI32))

    val firstScanI32 = maxOf(domainI32.top, edges.minOf { it.topI32 })
    val endExclusiveI32 = minOf(domainI32.bottom, edges.maxOf { it.bottomI32 })
    if (firstScanI32 >= endExclusiveI32) return PathFillScanPreparationI32.Ready(PathFillScanSpansI32.Empty(domainI32))

    val walkers = ArrayList<ScanEdgeWalkerI32>(edges.size)
    for (edge in edges) walkers += ScanEdgeWalkerI32.createOrNull(edge, firstScanI32) ?: return scanNumericRangeRefusal()
    val spans = ArrayList<PathFillScanSpanI32>()
    var scanYI32 = firstScanI32
    while (scanYI32 < endExclusiveI32) {
        val active = walkers.filter { it.isActiveAt(scanYI32) }
        if (active.size != 2) return scanNumericRangeRefusal()
        val firstXFixedI64 = active[0].currentXFixedI64 ?: return scanNumericRangeRefusal()
        val secondXFixedI64 = active[1].currentXFixedI64 ?: return scanNumericRangeRefusal()
        val roundedFirstI32 = roundFixedToI32OrNull(firstXFixedI64) ?: return scanNumericRangeRefusal()
        val roundedSecondI32 = roundFixedToI32OrNull(secondXFixedI64) ?: return scanNumericRangeRefusal()
        val leftI32 = maxOf(minOf(roundedFirstI32, roundedSecondI32), domainI32.left)
        val rightI32 = minOf(maxOf(roundedFirstI32, roundedSecondI32), domainI32.right)
        if (leftI32 < rightI32) {
            if (spans.size >= maxSpanCountI32) return scanSpanLimitRefusal()
            spans += PathFillScanSpanI32(leftI32, rightI32, scanYI32)
        }
        for (walker in walkers) if (!walker.advanceAfter(scanYI32)) return scanNumericRangeRefusal()
        scanYI32 += 1
    }
    return PathFillScanPreparationI32.Ready(
        if (spans.isEmpty()) PathFillScanSpansI32.Empty(domainI32)
        else PathFillScanSpansI32.NonEmpty(domainI32, spans)
    )
}

private data class ScanPointFDot6(val xI32: Int, val yI32: Int)

private class ScanEdgeI32 private constructor(
    val topI32: Int,
    val bottomI32: Int,
    private val initialXFixedI64: Long,
    val slopeFixedI32: Int,
) {
    fun initialXAtScanlineOrNull(scanYI32: Int): Long? {
        if (scanYI32 < topI32 || scanYI32 >= bottomI32) return null
        val scanDeltaI64 = scanYI32.toLong() - topI32.toLong()
        return checkedScanAddI64(initialXFixedI64, checkedScanMultiplyI64(scanDeltaI64, slopeFixedI32.toLong()) ?: return null)
    }

    companion object {
        fun fromPointsOrNull(first: ScanPointFDot6, second: ScanPointFDot6): ScanEdgeI32? {
            if (first.yI32 == second.yI32) return null
            val (top, bottom) = if (first.yI32 < second.yI32) first to second else second to first
            val topScanI32 = roundFDot6ToI32(top.yI32)
            val bottomScanI32 = roundFDot6ToI32(bottom.yI32)
            if (topScanI32 >= bottomScanI32) return null
            val dxI64 = bottom.xI32.toLong() - top.xI32.toLong()
            val dyI64 = bottom.yI32.toLong() - top.yI32.toLong()
            val slopeNumeratorI64 = checkedScanMultiplyI64(dxI64, SCAN_FIXED_ONE_I64) ?: return null
            val slopeI64 = slopeNumeratorI64 / dyI64
            if (slopeI64 !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            val sampleYFDot6I64 = checkedScanAddI64(
                checkedScanMultiplyI64(topScanI32.toLong(), SCAN_FDOT6_ONE_I64) ?: return null,
                SCAN_FDOT6_HALF_I64,
            ) ?: return null
            val deltaYFDot6I64 = sampleYFDot6I64 - top.yI32.toLong()
            val slopeProductI64 = checkedScanMultiplyI64(slopeI64, deltaYFDot6I64) ?: return null
            val initialDeltaXFDot6I64 = slopeProductI64 shr SCAN_FIXED_SHIFT_I32
            val initialXFDot6I64 = checkedScanAddI64(top.xI32.toLong(), initialDeltaXFDot6I64)
                ?: return null
            val initialXFixedI64 = checkedScanMultiplyI64(initialXFDot6I64, SCAN_FDOT6_TO_FIXED_I64)
                ?: return null
            return ScanEdgeI32(topScanI32, bottomScanI32, initialXFixedI64, slopeI64.toInt())
        }
    }
}

private class ScanEdgeWalkerI32 private constructor(
    private val edge: ScanEdgeI32,
    var currentXFixedI64: Long?,
) {
    fun isActiveAt(scanYI32: Int): Boolean {
        if (scanYI32 !in edge.topI32 until edge.bottomI32) return false
        if (currentXFixedI64 == null) currentXFixedI64 = edge.initialXAtScanlineOrNull(scanYI32)
        return currentXFixedI64 != null
    }

    fun advanceAfter(scanYI32: Int): Boolean {
        if (!isActiveAt(scanYI32)) return true
        currentXFixedI64 = checkedScanAddI64(currentXFixedI64 ?: return false, edge.slopeFixedI32.toLong())
        return currentXFixedI64 != null
    }

    companion object {
        fun createOrNull(edge: ScanEdgeI32, firstScanI32: Int): ScanEdgeWalkerI32? {
            if (firstScanI32 < edge.topI32) return ScanEdgeWalkerI32(edge, null)
            if (firstScanI32 >= edge.bottomI32) return ScanEdgeWalkerI32(edge, null)
            return ScanEdgeWalkerI32(edge, edge.initialXAtScanlineOrNull(firstScanI32))
        }
    }
}

private fun truncateF32ToFDot6I32OrNull(valueF32: Float): Int? {
    if (!valueF32.isFinite()) return null
    val scaledF64 = valueF32.toDouble() * SCAN_FDOT6_ONE_I64.toDouble()
    if (scaledF64 < Int.MIN_VALUE.toDouble() || scaledF64 > Int.MAX_VALUE.toDouble()) return null
    return scaledF64.toInt()
}

private fun roundFDot6ToI32(valueFDot6I32: Int): Int =
    ((valueFDot6I32.toLong() + SCAN_FDOT6_HALF_I64) shr SCAN_FDOT6_SHIFT_I32).toInt()

private fun roundFixedToI32OrNull(valueFixedI64: Long): Int? {
    val roundedI64 = checkedScanAddI64(valueFixedI64, SCAN_FIXED_HALF_I64) ?: return null
    val resultI64 = roundedI64 shr SCAN_FIXED_SHIFT_I32
    return resultI64.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

private fun RectI32.subtractOriginCheckedI32OrNull(originDeviceI32: Point2I32): RectI32? = RectI32(
    checkedScanSubtractI32(left, originDeviceI32.x) ?: return null,
    checkedScanSubtractI32(top, originDeviceI32.y) ?: return null,
    checkedScanSubtractI32(right, originDeviceI32.x) ?: return null,
    checkedScanSubtractI32(bottom, originDeviceI32.y) ?: return null,
)

private fun RectI32.isWithinExtentI32(targetExtentI32: SizeI32): Boolean =
    left >= 0 && top >= 0 && right >= left && bottom >= top &&
        right <= targetExtentI32.width && bottom <= targetExtentI32.height

private fun checkedScanAddI32(firstI32: Int, secondI32: Int): Int? {
    val resultI64 = firstI32.toLong() + secondI32.toLong()
    return resultI64.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

private fun checkedScanSubtractI32(firstI32: Int, secondI32: Int): Int? {
    val resultI64 = firstI32.toLong() - secondI32.toLong()
    return resultI64.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

private fun checkedScanAddI64(firstI64: Long, secondI64: Long): Long? = when {
    secondI64 > 0L && firstI64 > Long.MAX_VALUE - secondI64 -> null
    secondI64 < 0L && firstI64 < Long.MIN_VALUE - secondI64 -> null
    else -> firstI64 + secondI64
}

private fun checkedScanMultiplyI64(firstI64: Long, secondI64: Long): Long? {
    if (firstI64 == 0L || secondI64 == 0L) return 0L
    if (firstI64 == -1L && secondI64 == Long.MIN_VALUE || secondI64 == -1L && firstI64 == Long.MIN_VALUE) return null
    val resultI64 = firstI64 * secondI64
    return if (resultI64 / secondI64 == firstI64) resultI64 else null
}

private fun scanNumericRangeRefusal(): PathFillScanPreparationI32 =
    PathFillScanPreparationI32.Refused(PathFillScanRefusalReasonI32.NumericRange)

private fun scanSpanLimitRefusal(): PathFillScanPreparationI32 =
    PathFillScanPreparationI32.Refused(PathFillScanRefusalReasonI32.SpanLimit)

private const val SCAN_FDOT6_SHIFT_I32: Int = 6
private const val SCAN_FIXED_SHIFT_I32: Int = 16
private const val SCAN_FDOT6_ONE_I64: Long = 64L
private const val SCAN_FDOT6_HALF_I64: Long = 32L
private const val SCAN_FDOT6_TO_FIXED_I64: Long = 1024L
private const val SCAN_FIXED_ONE_I64: Long = 65_536L
private const val SCAN_FIXED_HALF_I64: Long = 32_768L
