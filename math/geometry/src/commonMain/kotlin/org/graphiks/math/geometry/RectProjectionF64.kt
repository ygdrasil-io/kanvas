package org.graphiks.math.geometry

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.math.vector.Vector2I32

/** Projects a finite, sorted F64 rectangle outwards into the checked I32 texel domain. */
public fun RectF64.roundOutToRectI32OrNull(): RectI32? {
    if (!isFinite() || isEmpty) return null
    val leftF64 = floor(left)
    val topF64 = floor(top)
    val rightF64 = ceil(right)
    val bottomF64 = ceil(bottom)
    if (!leftF64.isFinite() || !topF64.isFinite() || !rightF64.isFinite() || !bottomF64.isFinite()) return null
    if (leftF64 < Int.MIN_VALUE.toDouble() || leftF64 > Int.MAX_VALUE.toDouble() ||
        topF64 < Int.MIN_VALUE.toDouble() || topF64 > Int.MAX_VALUE.toDouble() ||
        rightF64 < Int.MIN_VALUE.toDouble() || rightF64 > Int.MAX_VALUE.toDouble() ||
        bottomF64 < Int.MIN_VALUE.toDouble() || bottomF64 > Int.MAX_VALUE.toDouble()) return null
    val widthI64 = rightF64.toLong() - leftF64.toLong()
    val heightI64 = bottomF64.toLong() - topF64.toLong()
    if (widthI64 !in 1L..Int.MAX_VALUE.toLong() || heightI64 !in 1L..Int.MAX_VALUE.toLong()) return null
    return RectI32(
        leftF64.toInt(),
        topF64.toInt(),
        rightF64.toInt(),
        bottomF64.toInt(),
    ).takeUnless { it.isEmpty }
}

/** Checked non-saturating translation for resource-space rectangle conversion. */
public fun RectI32.translateCheckedOrNull(delta: Vector2I32): RectI32? {
    fun translated(edgeI32: Int, deltaI32: Int): Int? {
        val valueI64 = edgeI32.toLong() + deltaI32.toLong()
        return valueI64.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
    }
    return RectI32(
        translated(left, delta.x) ?: return null,
        translated(top, delta.y) ?: return null,
        translated(right, delta.x) ?: return null,
        translated(bottom, delta.y) ?: return null,
    )
}
