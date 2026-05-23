package org.graphiks.math

internal actual object SkMathBackend {
    private val simdEnabled: Boolean
        get() = java.lang.Boolean.getBoolean("org.graphiks.math.simd.enabled")

    internal actual fun dot2(ax: Float, ay: Float, bx: Float, by: Float): Float =
        if (simdEnabled) {
            SkMathSimdJvm.dot4(ax, ay, 0f, 0f, bx, by, 0f, 0f)
        } else {
            SkMathScalar.dot2(ax, ay, bx, by)
        }

    internal actual fun dot3(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
        if (simdEnabled) {
            SkMathSimdJvm.dot4(ax, ay, az, 0f, bx, by, bz, 0f)
        } else {
            SkMathScalar.dot3(ax, ay, az, bx, by, bz)
        }

    internal actual fun dot4(
        ax: Float,
        ay: Float,
        az: Float,
        aw: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bw: Float,
    ): Float = if (simdEnabled) {
        SkMathSimdJvm.dot4(ax, ay, az, aw, bx, by, bz, bw)
    } else {
        SkMathScalar.dot4(ax, ay, az, aw, bx, by, bz, bw)
    }

    internal actual fun m44Concat(a: FloatArray, b: FloatArray, out: FloatArray) {
        if (simdEnabled) {
            SkMathSimdJvm.m44Concat(a, b, out)
        } else {
            SkMathScalar.m44Concat(a, b, out)
        }
    }
}
