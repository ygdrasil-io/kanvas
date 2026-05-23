package org.graphiks.math

internal actual object SkMathBackend {
    internal actual fun dot2(ax: Float, ay: Float, bx: Float, by: Float): Float =
        SkMathScalar.dot2(ax, ay, bx, by)

    internal actual fun dot3(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
        SkMathScalar.dot3(ax, ay, az, bx, by, bz)

    internal actual fun dot4(
        ax: Float,
        ay: Float,
        az: Float,
        aw: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bw: Float,
    ): Float = SkMathScalar.dot4(ax, ay, az, aw, bx, by, bz, bw)

    internal actual fun m44Concat(a: FloatArray, b: FloatArray, out: FloatArray) {
        SkMathScalar.m44Concat(a, b, out)
    }
}
