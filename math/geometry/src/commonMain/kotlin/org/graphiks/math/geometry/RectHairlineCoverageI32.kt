package org.graphiks.math.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * Returns the disjoint, one-device-pixel interior border of an integer Rect hairline.
 *
 * The input right/bottom coordinates identify the last covered device pixel, so the outer
 * half-open coverage is `[left, top, right + 1, bottom + 1)`.  Every construction and
 * intersection is performed in I64 before a clipped result is narrowed back to [RectI32].
 */
public fun rectHairlineCoverageBandsI32(
    deviceRectI32: RectI32,
    clipI32: RectI32,
): List<RectI32> {
    require(!deviceRectI32.isEmpty64()) { "Rect hairline requires a nonempty ordered device rect" }
    require(!clipI32.isEmpty64()) { "Rect hairline requires a nonempty ordered clip rect" }

    val leftI64 = deviceRectI32.left.toLong()
    val topI64 = deviceRectI32.top.toLong()
    val rightExclusiveI64 = deviceRectI32.right.toLong() + 1L
    val bottomExclusiveI64 = deviceRectI32.bottom.toLong() + 1L
    val middleTopI64 = topI64 + 1L
    val middleBottomI64 = bottomExclusiveI64 - 1L
    val clip = RectI64(
        clipI32.left.toLong(), clipI32.top.toLong(), clipI32.right.toLong(), clipI32.bottom.toLong(),
    )

    return buildList(4) {
        clippedBandI32(RectI64(leftI64, topI64, rightExclusiveI64, middleTopI64), clip)?.let(::add)
        clippedBandI32(RectI64(leftI64, middleBottomI64, rightExclusiveI64, bottomExclusiveI64), clip)?.let(::add)
        clippedBandI32(RectI64(leftI64, middleTopI64, leftI64 + 1L, middleBottomI64), clip)?.let(::add)
        clippedBandI32(RectI64(rightExclusiveI64 - 1L, middleTopI64, rightExclusiveI64, middleBottomI64), clip)?.let(::add)
    }
}

private data class RectI64(
    val leftI64: Long,
    val topI64: Long,
    val rightI64: Long,
    val bottomI64: Long,
)

private fun clippedBandI32(bandI64: RectI64, clipI64: RectI64): RectI32? {
    val leftI64 = max(bandI64.leftI64, clipI64.leftI64)
    val topI64 = max(bandI64.topI64, clipI64.topI64)
    val rightI64 = min(bandI64.rightI64, clipI64.rightI64)
    val bottomI64 = min(bandI64.bottomI64, clipI64.bottomI64)
    if (leftI64 >= rightI64 || topI64 >= bottomI64) return null
    return RectI32(leftI64.toInt(), topI64.toInt(), rightI64.toInt(), bottomI64.toInt())
}
