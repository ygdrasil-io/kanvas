package org.graphiks.math.geometry

/** Expands a finite non-empty rectangle by finite directional sampling halos in F64. */
public fun RectF64.expandSamplingHaloF64OrNull(
    leftF64: Double,
    topF64: Double,
    rightF64: Double,
    bottomF64: Double,
): RectF64? {
    if (!isFinite() || isEmpty || !leftF64.isFinite() || !topF64.isFinite() ||
        !rightF64.isFinite() || !bottomF64.isFinite() ||
        leftF64 < 0.0 || topF64 < 0.0 || rightF64 < 0.0 || bottomF64 < 0.0) return null
    return RectF64(left - leftF64, top - topF64, right + rightF64, bottom + bottomF64)
        .takeIf { it.isFinite() && !it.isEmpty }
}
