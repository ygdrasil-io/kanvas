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

/** Checked F64 translation retained until the caller chooses its outward I32 texel projection. */
public fun RectF64.translateF64OrNull(dxF64: Double, dyF64: Double): RectF64? {
    if (!isFinite() || isEmpty || !dxF64.isFinite() || !dyF64.isFinite()) return null
    return RectF64(left + dxF64, top + dyF64, right + dxF64, bottom + dyF64)
        .takeIf { it.isFinite() && !it.isEmpty }
}

/** Finite, non-empty intersection retained in F64 until its owner seals texels. */
public fun RectF64.intersectF64OrNull(other: RectF64): RectF64? {
    if (!isFinite() || !other.isFinite() || isEmpty || other.isEmpty) return null
    return RectF64(
        maxOf(left, other.left),
        maxOf(top, other.top),
        minOf(right, other.right),
        minOf(bottom, other.bottom),
    ).takeUnless { it.isEmpty || !it.isFinite() }
}

/**
 * Expands finite F64 content by the finite three-sigma blur support before outward I32
 * projection.  The caller owns any later clip or target intersection.
 */
public fun RectF64.expandForBlurF64OrNull(sigmaXF32: Float, sigmaYF32: Float): RectF64? {
    if (!isFinite() || isEmpty || !sigmaXF32.isFinite() || !sigmaYF32.isFinite() ||
        sigmaXF32 < 0f || sigmaYF32 < 0f) return null
    val supportXF64 = sigmaXF32.toDouble() * 3.0
    val supportYF64 = sigmaYF32.toDouble() * 3.0
    if (!supportXF64.isFinite() || !supportYF64.isFinite()) return null
    return RectF64(
        left - supportXF64,
        top - supportYF64,
        right + supportXF64,
        bottom + supportYF64,
    ).takeIf { it.isFinite() && !it.isEmpty }
}

/** Expands finite F64 morphology support before the caller seals outward I32 texels. */
public fun RectF64.expandForMorphologyF64OrNull(radiusXF64: Double, radiusYF64: Double): RectF64? {
    if (!isFinite() || isEmpty || !radiusXF64.isFinite() || !radiusYF64.isFinite() ||
        radiusXF64 < 0.0 || radiusYF64 < 0.0) return null
    return RectF64(left - radiusXF64, top - radiusYF64, right + radiusXF64, bottom + radiusYF64)
        .takeIf { it.isFinite() && !it.isEmpty }
}

/** Contracts known F64 morphology content; an empty contraction is represented as null. */
public fun RectF64.insetForMorphologyF64OrNull(radiusXF64: Double, radiusYF64: Double): RectF64? {
    if (!isFinite() || isEmpty || !radiusXF64.isFinite() || !radiusYF64.isFinite() ||
        radiusXF64 < 0.0 || radiusYF64 < 0.0) return null
    return RectF64(left + radiusXF64, top + radiusYF64, right - radiusXF64, bottom - radiusYF64)
        .takeIf { it.isFinite() && !it.isEmpty }
}

/** Skia's maximum morphology kernel radius after nearest-integer device quantization. */
public const val MORPHOLOGY_MAX_RADIUS_TEXELS_I32: Int = 256

/** Seals one finite morphology radius to Skia's nearest, capped integer tap extent. */
public fun morphologyRadiusTexelsI32OrNull(radiusF64: Double): Int? {
    if (!radiusF64.isFinite() || radiusF64 < 0.0) return null
    return minOf(floor(radiusF64 + 0.5), MORPHOLOGY_MAX_RADIUS_TEXELS_I32.toDouble()).toInt()
}

/** Rebase device texels at a target origin through finite F64 subtraction and checked I32 seal. */
public fun RectI32.rebaseAtOriginI32OrNull(originDeviceI32: Point2I32): RectI32? =
    RectF64(
        left.toDouble() - originDeviceI32.x.toDouble(),
        top.toDouble() - originDeviceI32.y.toDouble(),
        right.toDouble() - originDeviceI32.x.toDouble(),
        bottom.toDouble() - originDeviceI32.y.toDouble(),
    ).roundOutToRectI32OrNull()
