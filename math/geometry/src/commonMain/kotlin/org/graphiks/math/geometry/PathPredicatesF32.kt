package org.graphiks.math.geometry

import kotlin.math.abs

internal object PathPredicatesF32 {
    const val EPSILON_F32: Float = 1.1920928955078125e-7f

    fun almostEqualUlps(a: Float, b: Float, maxUlps: Int = 16): Boolean {
        if (maxUlps < 0 || !a.isFinite() || !b.isFinite()) return false
        if (a == b) return true
        val nearZeroLimit = EPSILON_F32 * maxUlps / 2f
        if (abs(a) <= nearZeroLimit && abs(b) <= nearZeroLimit) return true
        if ((a < 0f) != (b < 0f)) return false
        return abs(orderedBits(a).toLong() - orderedBits(b).toLong()) < maxUlps.toLong()
    }

    private fun orderedBits(value: Float): Int {
        val bits = value.toRawBits()
        return if (bits < 0) Int.MIN_VALUE - bits else bits
    }
}
