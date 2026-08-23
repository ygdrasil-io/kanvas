package org.graphiks.math.geometry

import org.graphiks.math.geometry.PathOpsEpsilon.almostDEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.almostEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.almostEqualUlpsNoNormalCheck
import org.graphiks.math.geometry.PathOpsEpsilon.almostPEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.approximatelyEqual
import org.graphiks.math.geometry.PathOpsEpsilon.approximatelyZero
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyEqual
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyZeroWhenComparedTo
import org.graphiks.math.vector.Vector2F64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal fun Point2F64.pathOpsApproximatelyDEquals(other: Point2F64): Boolean {
    if (approximatelyEqual(x, other.x) && approximatelyEqual(y, other.y)) return true
    if (!roughlyEqualUlps(x, other.x) || !roughlyEqualUlps(y, other.y)) return false
    val dist = pathOpsDistanceTo(other)
    val tiniest = min(min(min(x, other.x), y), other.y)
    var largest = max(max(max(x, other.x), y), other.y)
    largest = max(largest, -tiniest)
    return almostDEqualUlps(largest, largest + dist)
}

internal fun Point2F64.pathOpsApproximatelyDEquals(other: Point2F32): Boolean =
    pathOpsApproximatelyDEquals(other.toPoint2F64())

internal fun Point2F64.pathOpsApproximatelyEquals(other: Point2F64): Boolean {
    if (approximatelyEqual(x, other.x) && approximatelyEqual(y, other.y)) return true
    if (!roughlyEqualUlps(x, other.x) || !roughlyEqualUlps(y, other.y)) return false
    val dist = pathOpsDistanceTo(other)
    val tiniest = min(min(min(x, other.x), y), other.y)
    var largest = max(max(max(x, other.x), y), other.y)
    largest = max(largest, -tiniest)
    return almostPEqualUlps(largest, largest + dist)
}

internal fun Point2F64.pathOpsApproximatelyEquals(other: Point2F32): Boolean =
    pathOpsApproximatelyEquals(other.toPoint2F64())

internal fun Point2F64.pathOpsRoughlyEquals(other: Point2F64): Boolean {
    if (roughlyEqual(x, other.x) && roughlyEqual(y, other.y)) return true
    val dist = pathOpsDistanceTo(other)
    val tiniest = min(min(min(x, other.x), y), other.y)
    var largest = max(max(max(x, other.x), y), other.y)
    largest = max(largest, -tiniest)
    return roughlyEqualUlps(largest, largest + dist)
}

internal fun Point2F64.pathOpsApproximatelyZero(): Boolean =
    approximatelyZero(x) && approximatelyZero(y)

internal fun Vector2F64.pathOpsCrossCheck(other: Vector2F64): Double {
    val xy = x * other.y
    val yx = y * other.x
    return if (almostEqualUlps(xy, yx)) 0.0 else xy - yx
}

internal fun Vector2F64.pathOpsCrossNoNormalCheck(other: Vector2F64): Double {
    val xy = x * other.y
    val yx = y * other.x
    return if (almostEqualUlpsNoNormalCheck(xy, yx)) 0.0 else xy - yx
}

internal fun pathOpsApproximatelyEquals(a: Point2F32, b: Point2F32): Boolean {
    if (approximatelyEqual(a.x.toDouble(), b.x.toDouble()) &&
        approximatelyEqual(a.y.toDouble(), b.y.toDouble())
    ) {
        return true
    }
    if (!roughlyEqualUlps(a.x, b.x) || !roughlyEqualUlps(a.y, b.y)) return false
    val dA = a.toPoint2F64()
    val dB = b.toPoint2F64()
    val dist = dA.pathOpsDistanceTo(dB)
    val tiniest = min(min(min(a.x, b.x), a.y), b.y)
    var largest = max(max(max(a.x, b.x), a.y), b.y)
    largest = max(largest, -tiniest)
    return almostDEqualUlps(largest.toDouble(), largest.toDouble() + dist)
}

internal fun pathOpsRoughlyEquals(a: Point2F32, b: Point2F32): Boolean {
    if (!roughlyEqualUlps(a.x, b.x) && !roughlyEqualUlps(a.y, b.y)) return false
    val dA = a.toPoint2F64()
    val dB = b.toPoint2F64()
    val dist = dA.pathOpsDistanceTo(dB)
    val tiniest = min(min(min(a.x, b.x), a.y), b.y)
    var largest = max(max(max(a.x, b.x), a.y), b.y)
    largest = max(largest, -tiniest)
    return almostDEqualUlps(largest.toDouble(), largest.toDouble() + dist)
}

internal fun pathOpsWayRoughlyEquals(a: Point2F32, b: Point2F32): Boolean {
    val largest = max(
        abs(a.x),
        max(abs(a.y), max(abs(b.x), abs(b.y))),
    )
    val dx = a.x - b.x
    val dy = a.y - b.y
    val largestDiff = max(abs(dx), abs(dy))
    return roughlyZeroWhenComparedTo(largestDiff.toDouble(), largest.toDouble())
}

private fun Point2F64.pathOpsDistanceTo(other: Point2F64): Double {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}
