package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object PathPredicatesF64 {
    const val EPSILON_F64: Double = 2.220446049250313e-16

    fun onSegment(point: Point2F64, start: Point2F64, end: Point2F64): Boolean {
        if (!point.isFinite() || !start.isFinite() || !end.isFinite()) return false
        if (OrientationPredicateF64.sign(start, end, point) != 0) return false
        return point.x in min(start.x, end.x)..max(start.x, end.x) &&
            point.y in min(start.y, end.y)..max(start.y, end.y)
    }

    fun almostEqualUlps(a: Double, b: Double, maxUlps: Int = 16): Boolean {
        if (maxUlps < 0 || !a.isFinite() || !b.isFinite()) return false
        if (a == b) return true
        val nearZeroLimit = EPSILON_F64 * maxUlps / 2.0
        if (abs(a) <= nearZeroLimit && abs(b) <= nearZeroLimit) return true
        if ((a < 0.0) != (b < 0.0)) return false
        return abs(orderedBits(a) - orderedBits(b)) < maxUlps.toLong()
    }

    private fun orderedBits(value: Double): Long {
        val bits = value.toRawBits()
        return if (bits < 0L) Long.MIN_VALUE - bits else bits
    }
}
