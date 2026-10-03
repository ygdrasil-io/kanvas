package org.graphiks.math.geometry

/** Returns the exact I32 value of a finite F32 coordinate, or null when it cannot be represented. */
public fun coordinateF32ToExactI32OrNull(valueF32: Float): Int? {
    if (!valueF32.isFinite()) return null
    val valueI64 = valueF32.toLong()
    if (valueI64 !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    if (valueI64.toFloat() != valueF32) return null
    return valueI64.toInt()
}

/** Projects a finite, exact-integral, non-empty F32 rectangle into I32 edge coordinates. */
public fun RectF32.toExactRectI32OrNull(): RectI32? {
    val leftI32 = coordinateF32ToExactI32OrNull(left) ?: return null
    val topI32 = coordinateF32ToExactI32OrNull(top) ?: return null
    val rightI32 = coordinateF32ToExactI32OrNull(right) ?: return null
    val bottomI32 = coordinateF32ToExactI32OrNull(bottom) ?: return null
    return RectI32(leftI32, topI32, rightI32, bottomI32).takeUnless { it.isEmpty64() }
}
