@file:JvmName("Skcms")

package org.skia.skcms

import kotlin.math.pow

/**
 * Bit-compatible port of the slice of `skcms` we need for SkColorSpace +
 * SkColorSpaceXformSteps. Phase 1: sRGBish transfer functions only,
 * float-based 3x3 matrix arithmetic, no ICC parsing.
 *
 * Function names match upstream `skcms_*` so `Functions.kt` (the generated
 * 154 KB ledger of every skcms function with C++ source in Javadoc) stays
 * directly applicable as spec.
 */

/**
 * Classify a transfer function. Phase 1 returns either `sRGBish` or
 * `Invalid`; PQ/HLG are flagged Invalid until we add HDR support.
 *
 * Spec: `static skcms_TFType classify(...)` in upstream skcms.cc.
 */
public fun classify(tf: SkcmsTransferFunction): SkcmsTFType {
    if (tf.g < 0f) {
        // Sentinel negative g encodes PQ/HLG variants. Differ for now.
        return SkcmsTFType.Invalid
    }
    val sum = tf.a + tf.b + tf.c + tf.d + tf.e + tf.f + tf.g
    val finite = !sum.isNaN() && !sum.isInfinite()
    if (finite && tf.a >= 0f && tf.c >= 0f && tf.d >= 0f && tf.g >= 0f &&
        tf.a * tf.d + tf.b >= 0f) {
        return SkcmsTFType.sRGBish
    }
    return SkcmsTFType.Invalid
}

/**
 * Evaluate a transfer function at `x`. Mirrors the sRGBish branch of
 * `skcms_TransferFunction_eval` upstream (signed extension, piecewise).
 */
public fun skcmsTransferFunctionEval(tf: SkcmsTransferFunction, x: Float): Float {
    val sign = if (x < 0f) -1f else 1f
    val ax = x * sign
    return when (classify(tf)) {
        SkcmsTFType.sRGBish ->
            sign * if (ax < tf.d) tf.c * ax + tf.f
                   else (tf.a * ax + tf.b).pow(tf.g) + tf.e
        else -> 0f
    }
}

/**
 * Invert an sRGBish transfer function. Mirrors `skcms_TransferFunction_invert`
 * upstream — including the final tweak that pins `inv(eval(1.0)) == 1.0`,
 * which is what gives us bit-stable round-trips.
 *
 * Returns `null` if the input is not invertible (PQ, HLG, discontinuous, or
 * results in a negative `a`).
 */
public fun skcmsTransferFunctionInvert(src: SkcmsTransferFunction): SkcmsTransferFunction? {
    if (classify(src) != SkcmsTFType.sRGBish) return null

    // New threshold = src(d) from either side; both must agree.
    val dL = src.c * src.d + src.f
    val dR = (src.a * src.d + src.b).pow(src.g) + src.e
    if (kotlin.math.abs(dL - dR) > 1f / 512f) return null
    var newD = dL

    // Linear branch inverse, only if there is one (d > 0).
    var newC = 0f
    var newF = 0f
    if (newD > 0f) {
        newC = 1f / src.c
        newF = -src.f / src.c
    }

    // Power branch:  x = (1/a)*(y - e)^(1/g) - b/a
    //   = (k*y - k*e)^(1/g) - b/a   with k = (1/a)^g
    val k: Float = src.a.toDouble().pow(-src.g.toDouble()).toFloat()
    var newG = 1f / src.g
    var newA = k
    var newB = -k * src.e
    var newE = -src.b / src.a

    if (newA < 0f) return null
    // Clamp slight negative ad+b
    if (newA * newD + newB < 0f) {
        newB = -newA * newD
    }

    var inv = SkcmsTransferFunction(newG, newA, newB, newC, newD, newE, newF)
    if (classify(inv) != SkcmsTFType.sRGBish) return null

    // Pin inv(src(1.0)) == 1.0
    val s = skcmsTransferFunctionEval(src, 1f)
    if (s.isNaN() || s.isInfinite()) return null
    val sign = if (s < 0f) -1f else 1f
    val absS = s * sign
    inv = if (absS < newD) {
        SkcmsTransferFunction(newG, newA, newB, newC, newD, newE,
            f = 1f - sign * newC * absS)
    } else {
        SkcmsTransferFunction(newG, newA, newB, newC, newD,
            e = 1f - sign * (newA * absS + newB).pow(newG),
            f = newF)
    }
    return if (classify(inv) == SkcmsTFType.sRGBish) inv else null
}

/**
 * Concatenate two 3x3 matrices: `m = a * b`. Mirrors `skcms_Matrix3x3_concat`
 * upstream (row-major, naive triple loop, float precision).
 */
public fun skcmsMatrix3x3Concat(a: SkcmsMatrix3x3, b: SkcmsMatrix3x3): SkcmsMatrix3x3 {
    val out = Array(3) { FloatArray(3) }
    for (r in 0 until 3) for (c in 0 until 3) {
        out[r][c] = a.vals[r][0] * b.vals[0][c] +
                    a.vals[r][1] * b.vals[1][c] +
                    a.vals[r][2] * b.vals[2][c]
    }
    return SkcmsMatrix3x3(out)
}

/**
 * Multiply 3x3 matrix by a 3-vector. Mirrors the local `mv_mul` helper at
 * [skcms.cc:1817-1823](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/skcms.cc).
 */
public fun skcmsMv3Mul(m: SkcmsMatrix3x3, v: FloatArray): FloatArray {
    require(v.size == 3) { "expected 3-vector, got ${v.size}" }
    return FloatArray(3) { row ->
        m.vals[row][0] * v[0] + m.vals[row][1] * v[1] + m.vals[row][2] * v[2]
    }
}

/**
 * Bradford chromatic adaptation matrix from white point `(wx, wy)` to D50.
 *
 * Output is the 3x3 transform that takes a `D_X` XYZ vector to its `D50`
 * equivalent. Mirrors `skcms_AdaptToXYZD50` upstream (skcms.cc:1826-1865).
 *
 * Returns `null` if the white point is not in `[0, 1]`.
 */
public fun skcmsAdaptToXYZD50(wx: Float, wy: Float): SkcmsMatrix3x3? {
    if (wx !in 0f..1f || wy !in 0f..1f) return null

    // Assumes Y = 1.
    val wXYZ = floatArrayOf(wx / wy, 1f, (1f - wx - wy) / wy)
    val wXYZD50 = floatArrayOf(0.96422f, 1.0f, 0.82521f)

    val xyzToLms = SkcmsMatrix3x3.of(
         0.8951f,  0.2664f, -0.1614f,
        -0.7502f,  1.7135f,  0.0367f,
         0.0389f, -0.0685f,  1.0296f,
    )
    val lmsToXyz = SkcmsMatrix3x3.of(
         0.9869929f, -0.1470543f, 0.1599627f,
         0.4323053f,  0.5183603f, 0.0492912f,
        -0.0085287f,  0.0400428f, 0.9684867f,
    )

    val srcCone = skcmsMv3Mul(xyzToLms, wXYZ)
    val dstCone = skcmsMv3Mul(xyzToLms, wXYZD50)

    val coneScale = SkcmsMatrix3x3.of(
        dstCone[0] / srcCone[0], 0f, 0f,
        0f, dstCone[1] / srcCone[1], 0f,
        0f, 0f, dstCone[2] / srcCone[2],
    )
    val tmp = skcmsMatrix3x3Concat(coneScale, xyzToLms)
    return skcmsMatrix3x3Concat(lmsToXyz, tmp)
}

/**
 * Convert chromaticity primaries + white point to a 3x3 matrix mapping
 * RGB to D50 XYZ. Mirrors `skcms_PrimariesToXYZD50` upstream
 * (skcms.cc:1867-1909). Returns `null` on out-of-range inputs or singular
 * primaries matrix.
 */
public fun skcmsPrimariesToXYZD50(
    rx: Float, ry: Float,
    gx: Float, gy: Float,
    bx: Float, by: Float,
    wx: Float, wy: Float,
): SkcmsMatrix3x3? {
    if (rx !in 0f..1f || ry !in 0f..1f ||
        gx !in 0f..1f || gy !in 0f..1f ||
        bx !in 0f..1f || by !in 0f..1f ||
        wx !in 0f..1f || wy !in 0f..1f) return null

    val primaries = SkcmsMatrix3x3.of(
        rx, gx, bx,
        ry, gy, by,
        1f - rx - ry, 1f - gx - gy, 1f - bx - by,
    )
    val primariesInv = skcmsMatrix3x3Invert(primaries) ?: return null

    val wXYZ = floatArrayOf(wx / wy, 1f, (1f - wx - wy) / wy)
    val xyzScale = skcmsMv3Mul(primariesInv, wXYZ)

    val toXYZ = skcmsMatrix3x3Concat(
        primaries,
        SkcmsMatrix3x3.of(
            xyzScale[0], 0f, 0f,
            0f, xyzScale[1], 0f,
            0f, 0f, xyzScale[2],
        ),
    )

    val dxToD50 = skcmsAdaptToXYZD50(wx, wy) ?: return null
    return skcmsMatrix3x3Concat(dxToD50, toXYZ)
}

/**
 * Invert a 3x3 matrix in double precision then narrow to float. Mirrors
 * `skcms_Matrix3x3_invert` upstream — important to use doubles for the
 * intermediate determinant or wide-gamut profiles lose precision.
 *
 * Returns `null` if the matrix is singular or if any output cell is non-finite.
 */
public fun skcmsMatrix3x3Invert(src: SkcmsMatrix3x3): SkcmsMatrix3x3? {
    val a00 = src.vals[0][0].toDouble()
    val a01 = src.vals[1][0].toDouble()
    val a02 = src.vals[2][0].toDouble()
    val a10 = src.vals[0][1].toDouble()
    val a11 = src.vals[1][1].toDouble()
    val a12 = src.vals[2][1].toDouble()
    val a20 = src.vals[0][2].toDouble()
    val a21 = src.vals[1][2].toDouble()
    val a22 = src.vals[2][2].toDouble()

    var b0 = a00 * a11 - a01 * a10
    var b1 = a00 * a12 - a02 * a10
    var b2 = a01 * a12 - a02 * a11
    val b3 = a20
    val b4 = a21
    val b5 = a22

    val det = b0 * b5 - b1 * b4 + b2 * b3
    if (det == 0.0) return null
    val invDet = 1.0 / det
    if (!invDet.isFinite() || invDet > Float.MAX_VALUE.toDouble() || invDet < -Float.MAX_VALUE.toDouble()) {
        return null
    }
    b0 *= invDet; b1 *= invDet; b2 *= invDet
    val sb3 = b3 * invDet; val sb4 = b4 * invDet; val sb5 = b5 * invDet

    val out = Array(3) { FloatArray(3) }
    out[0][0] = (a11 * sb5 - a12 * sb4).toFloat()
    out[1][0] = (a02 * sb4 - a01 * sb5).toFloat()
    out[2][0] = (b2).toFloat()
    out[0][1] = (a12 * sb3 - a10 * sb5).toFloat()
    out[1][1] = (a00 * sb5 - a02 * sb3).toFloat()
    out[2][1] = (-b1).toFloat()
    out[0][2] = (a10 * sb4 - a11 * sb3).toFloat()
    out[1][2] = (a01 * sb3 - a00 * sb4).toFloat()
    out[2][2] = (b0).toFloat()

    for (r in 0 until 3) for (c in 0 until 3) {
        if (!out[r][c].isFinite()) return null
    }
    return SkcmsMatrix3x3(out)
}
