package org.graphiks.math.matrix

import org.graphiks.math.geometry.*

/** Conservative checked affine bounds for a retained source-space mesh. */
public fun TriangleMeshF32.rasterBoundsI32OrNull(matrixF32: Matrix3x3F32, targetI32: RectI32): RectI32? {
    if (!matrixF32.toMatrix3x3F64().isFinite() || matrixF32.hasPerspective()) return null
    val bounds = copyBoundsF32()
    val mapped = matrixF32.toMatrix3x3F64().mapRectBoundsF64OrNull(RectF64(bounds.left.toDouble(), bounds.top.toDouble(),
        bounds.right.toDouble(), bounds.bottom.toDouble())) ?: return null
    val rounded = mapped.roundOutToRectI32OrNull() ?: return null
    return rounded.takeIf { it.intersect(targetI32) }
}

/** Rebase only target translation; source coordinates and vertex attributes remain unchanged. */
public fun Matrix3x3F32.relativeToOriginI32OrNull(originI32: Point2I32): Matrix3x3F32? {
    if (!toMatrix3x3F64().isFinite() || hasPerspective()) return null
    fun translated(valueF32: Float, offsetI32: Int): Float? {
        val exactF64 = valueF32.toDouble() - offsetI32.toDouble()
        return exactF64.toFloat().takeIf { it.isFinite() && it.toDouble() == exactF64 }
    }
    return Matrix3x3F32.of(sx, kx, translated(tx, originI32.x) ?: return null,
        ky, sy, translated(ty, originI32.y) ?: return null, persp0, persp1, persp2)
}
