package org.graphiks.math

internal object SkMathScalar {
    fun dot2(ax: Float, ay: Float, bx: Float, by: Float): Float =
        ax * bx + ay * by

    fun dot3(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
        ax * bx + ay * by + az * bz

    fun dot4(
        ax: Float,
        ay: Float,
        az: Float,
        aw: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bw: Float,
    ): Float = ax * bx + ay * by + az * bz + aw * bw

    fun m44Concat(a: FloatArray, b: FloatArray, out: FloatArray) {
        val a00 = a[0];  val a10 = a[1];  val a20 = a[2];  val a30 = a[3]
        val a01 = a[4];  val a11 = a[5];  val a21 = a[6];  val a31 = a[7]
        val a02 = a[8];  val a12 = a[9];  val a22 = a[10]; val a32 = a[11]
        val a03 = a[12]; val a13 = a[13]; val a23 = a[14]; val a33 = a[15]

        for (c in 0..3) {
            val b0 = b[c * 4 + 0]
            val b1 = b[c * 4 + 1]
            val b2 = b[c * 4 + 2]
            val b3 = b[c * 4 + 3]
            out[c * 4 + 0] = a00 * b0 + a01 * b1 + a02 * b2 + a03 * b3
            out[c * 4 + 1] = a10 * b0 + a11 * b1 + a12 * b2 + a13 * b3
            out[c * 4 + 2] = a20 * b0 + a21 * b1 + a22 * b2 + a23 * b3
            out[c * 4 + 3] = a30 * b0 + a31 * b1 + a32 * b2 + a33 * b3
        }
    }
}
