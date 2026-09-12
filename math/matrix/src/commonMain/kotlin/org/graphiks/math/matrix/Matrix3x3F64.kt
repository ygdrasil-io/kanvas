package org.graphiks.math.matrix

/**
 * Immutable F64 snapshot of a 3 × 3 homogeneous transform matrix.
 *
 * Coefficients use the same row-major layout as [Matrix3x3F32]:
 * `[sx, kx, tx, ky, sy, ty, persp0, persp1, persp2]`.
 */
public data class Matrix3x3F64(
    public val sxF64: Double = 1.0,
    public val kxF64: Double = 0.0,
    public val txF64: Double = 0.0,
    public val kyF64: Double = 0.0,
    public val syF64: Double = 1.0,
    public val tyF64: Double = 0.0,
    public val persp0F64: Double = 0.0,
    public val persp1F64: Double = 0.0,
    public val persp2F64: Double = 1.0,
)

/** The path-transform handling required by the matrix coefficients. */
public enum class PathTransformClass {
    Identity,
    AxisAlignedAffine,
    GeneralAffine,
    Perspective,
}

/**
 * Returns whether all matrix coefficients are finite.
 *
 * Callers validate this at the operation boundary appropriate to their result;
 * this snapshot deliberately retains non-finite values rather than coercing
 * them to another transform.
 */
public fun Matrix3x3F64.isFinite(): Boolean =
    sxF64.isFinite() && kxF64.isFinite() && txF64.isFinite() &&
        kyF64.isFinite() && syF64.isFinite() && tyF64.isFinite() &&
        persp0F64.isFinite() && persp1F64.isFinite() && persp2F64.isFinite()

/** Cofactor inversion in F64, with one checked F32 projection at the public boundary. */
public fun Matrix3x3F64.invertToMatrix3x3F32OrNull(): Matrix3x3F32? {
    if (!isFinite()) return null
    val aF64 = syF64 * persp2F64 - tyF64 * persp1F64
    val bF64 = tyF64 * persp0F64 - kyF64 * persp2F64
    val cF64 = kyF64 * persp1F64 - syF64 * persp0F64
    val determinantF64 = sxF64 * aF64 + kxF64 * bF64 + txF64 * cF64
    if (!determinantF64.isFinite() || determinantF64 == 0.0) return null
    val inverseF64 = doubleArrayOf(
        aF64, txF64 * persp1F64 - kxF64 * persp2F64, kxF64 * tyF64 - txF64 * syF64,
        bF64, sxF64 * persp2F64 - txF64 * persp0F64, txF64 * kyF64 - sxF64 * tyF64,
        cF64, kxF64 * persp0F64 - sxF64 * persp1F64, sxF64 * syF64 - kxF64 * kyF64,
    )
    val projectedF32 = inverseF64.map { coefficientF64 ->
        // JS Float values are Numbers; materialize the IEEE F32 projection on both targets.
        val valueF32 = Float.fromBits((coefficientF64 / determinantF64).toFloat().toRawBits())
        if (!valueF32.isFinite()) return null
        if (valueF32 == 0f) 0f else valueF32
    }
    return Matrix3x3F32(projectedF32[0], projectedF32[1], projectedF32[2], projectedF32[3],
        projectedF32[4], projectedF32[5], projectedF32[6], projectedF32[7], projectedF32[8])
}

/**
 * Classifies this transform with exact IEEE-754 comparisons.
 *
 * Signed zero is equivalent to canonical `+0.0` for these comparisons; no
 * tolerance is applied to near-zero affine or perspective coefficients.
 */
public fun Matrix3x3F64.classifyPathTransform(): PathTransformClass = when {
    persp0F64 != 0.0 || persp1F64 != 0.0 || persp2F64 != 1.0 -> PathTransformClass.Perspective
    kxF64 != 0.0 || kyF64 != 0.0 -> PathTransformClass.GeneralAffine
    sxF64 != 1.0 || syF64 != 1.0 || txF64 != 0.0 || tyF64 != 0.0 -> PathTransformClass.AxisAlignedAffine
    else -> PathTransformClass.Identity
}

/**
 * Widens this matrix to an immutable F64 snapshot without losing any F32
 * coefficient bits, while canonicalizing signed zero to `+0.0`.
 */
public fun Matrix3x3F32.toMatrix3x3F64(): Matrix3x3F64 = Matrix3x3F64(
    sxF64 = canonicalMatrixCoefficientF64(sx),
    kxF64 = canonicalMatrixCoefficientF64(kx),
    txF64 = canonicalMatrixCoefficientF64(tx),
    kyF64 = canonicalMatrixCoefficientF64(ky),
    syF64 = canonicalMatrixCoefficientF64(sy),
    tyF64 = canonicalMatrixCoefficientF64(ty),
    persp0F64 = canonicalMatrixCoefficientF64(persp0),
    persp1F64 = canonicalMatrixCoefficientF64(persp1),
    persp2F64 = canonicalMatrixCoefficientF64(persp2),
)

private fun canonicalMatrixCoefficientF64(valueF32: Float): Double {
    val valueF64 = Float.fromBits(valueF32.toRawBits()).toDouble()
    return if (valueF64 == 0.0) 0.0 else valueF64
}
