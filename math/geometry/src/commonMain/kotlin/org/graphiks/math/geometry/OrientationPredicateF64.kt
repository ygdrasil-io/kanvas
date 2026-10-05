package org.graphiks.math.geometry

import kotlin.math.abs

internal object OrientationPredicateF64 {
    private const val CCW_ERROR_BOUND_A: Double =
        (3.0 + 16.0 * PathPredicatesF64.EPSILON_F64) * PathPredicatesF64.EPSILON_F64

    fun sign(a: Point2F64, b: Point2F64, c: Point2F64): Int {
        val acx = a.x - c.x
        val bcx = b.x - c.x
        val acy = a.y - c.y
        val bcy = b.y - c.y
        val left = acx * bcy
        val right = acy * bcx
        val determinant = left - right
        val errorBound = CCW_ERROR_BOUND_A * (abs(left) + abs(right))

        if (determinant > 0.0 && determinant >= errorBound) return 1
        if (determinant < 0.0 && -determinant >= errorBound) return -1

        val exactLeft = ExpansionF64.product(ExpansionF64.twoDiff(a.x, c.x), ExpansionF64.twoDiff(b.y, c.y))
        val exactRight = ExpansionF64.product(ExpansionF64.twoDiff(a.y, c.y), ExpansionF64.twoDiff(b.x, c.x))
        return ExpansionF64.sign(ExpansionF64.expansionDiff(exactLeft, exactRight))
    }
}

internal object ExpansionF64 {
    private const val SPLITTER_F64: Double = 134_217_729.0

    fun twoSum(a: Double, b: Double): DoubleArray {
        val sum = a + b
        val bVirtual = sum - a
        val aVirtual = sum - bVirtual
        val bRoundoff = b - bVirtual
        val aRoundoff = a - aVirtual
        return doubleArrayOf(aRoundoff + bRoundoff, sum)
    }

    fun twoDiff(a: Double, b: Double): DoubleArray {
        val difference = a - b
        val bVirtual = a - difference
        val aVirtual = difference + bVirtual
        val bRoundoff = bVirtual - b
        val aRoundoff = a - aVirtual
        return doubleArrayOf(aRoundoff + bRoundoff, difference)
    }

    fun twoProduct(a: Double, b: Double): DoubleArray {
        val product = a * b
        val (aHigh, aLow) = split(a)
        val (bHigh, bLow) = split(b)
        val error1 = product - aHigh * bHigh
        val error2 = error1 - aLow * bHigh
        val error3 = error2 - aHigh * bLow
        return doubleArrayOf(aLow * bLow - error3, product)
    }

    fun expansionSum(first: DoubleArray, second: DoubleArray): DoubleArray {
        var result = first
        second.forEach { component -> result = grow(result, component) }
        return result
    }

    fun expansionDiff(first: DoubleArray, second: DoubleArray): DoubleArray =
        expansionSum(first, DoubleArray(second.size) { index -> -second[index] })

    fun sign(expansion: DoubleArray): Int {
        for (index in expansion.lastIndex downTo 0) {
            when {
                expansion[index] > 0.0 -> return 1
                expansion[index] < 0.0 -> return -1
            }
        }
        return 0
    }

    fun product(first: DoubleArray, second: DoubleArray): DoubleArray {
        var result = doubleArrayOf()
        first.forEach { firstComponent ->
            second.forEach { secondComponent ->
                result = expansionSum(result, twoProduct(firstComponent, secondComponent))
            }
        }
        return result
    }

    private fun split(value: Double): Pair<Double, Double> {
        val scaled = SPLITTER_F64 * value
        val high = scaled - (scaled - value)
        return high to value - high
    }

    private fun grow(expansion: DoubleArray, value: Double): DoubleArray {
        if (expansion.isEmpty()) return doubleArrayOf(value)

        val result = DoubleArray(expansion.size + 1)
        var resultSize = 0
        var sum = value
        expansion.forEach { component ->
            val (roundoff, next) = twoSum(sum, component)
            if (roundoff != 0.0) result[resultSize++] = roundoff
            sum = next
        }
        if (sum != 0.0 || resultSize == 0) result[resultSize++] = sum
        return result.copyOf(resultSize)
    }
}
