package org.graphiks.kanvas.types

import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

class Matrix33 private constructor(private val values: FloatArray) {
    val scaleX: Float get() = values[0]
    val skewX: Float get() = values[1]
    val transX: Float get() = values[2]
    val skewY: Float get() = values[3]
    val scaleY: Float get() = values[4]
    val transY: Float get() = values[5]
    val persp0: Float get() = values[6]
    val persp1: Float get() = values[7]
    val persp2: Float get() = values[8]

    companion object {
        fun identity() = Matrix33(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        fun translate(x: Float, y: Float) = Matrix33(floatArrayOf(1f, 0f, x, 0f, 1f, y, 0f, 0f, 1f))

        fun scale(sx: Float, sy: Float) = Matrix33(floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f))

        fun rotate(degrees: Float): Matrix33 {
            val r = degrees * PI.toFloat() / 180f
            val c = cos(r)
            val s = sin(r)
            return Matrix33(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        }

        fun skew(kx: Float, ky: Float) = Matrix33(floatArrayOf(1f, kx, 0f, ky, 1f, 0f, 0f, 0f, 1f))

        fun makeAll(
            sx: Float, kx: Float, tx: Float,
            ky: Float, sy: Float, ty: Float,
        ): Matrix33 = Matrix33(floatArrayOf(sx, kx, tx, ky, sy, ty, 0f, 0f, 1f))

        fun makeAll(
            sx: Float, kx: Float, tx: Float,
            ky: Float, sy: Float, ty: Float,
            p0: Float, p1: Float, p2: Float,
        ): Matrix33 = Matrix33(floatArrayOf(sx, kx, tx, ky, sy, ty, p0, p1, p2))
    }

    operator fun times(other: Matrix33): Matrix33 {
        val a = values
        val b = other.values
        val result = FloatArray(9)
        for (i in 0..2) for (j in 0..2) {
            result[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
        }
        return Matrix33(result)
    }

    operator fun times(point: Point): Point {
        val x = values[0] * point.x + values[1] * point.y + values[2]
        val y = values[3] * point.x + values[4] * point.y + values[5]
        return Point(x, y)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix33) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}
