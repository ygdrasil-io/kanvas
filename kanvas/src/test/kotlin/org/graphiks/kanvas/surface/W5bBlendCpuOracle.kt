@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.test.assertTrue
import org.graphiks.math.color.ColorARGB

/** Independent bounded linear-premultiplied oracle for public W5b pixel tests. */
internal object W5bBlendCpuOracle {
    fun assertDstOver(background: ColorARGB, backgroundOpacityF32: Float, foreground: ColorARGB, foregroundOpacityF32: Float, actual: UByteArray) =
        assertAdmits(dstOver(premul(background, backgroundOpacityF32), premul(foreground, foregroundOpacityF32)), actual)

    fun assertDst(background: ColorARGB, backgroundOpacityF32: Float, actual: UByteArray) =
        assertAdmits(premul(background, backgroundOpacityF32), actual)

    private fun dstOver(destination: List<Interval>, source: List<Interval>): List<Interval> =
        List(4) { index -> (destination[index] + source[index] * (one - destination[3])).clamp() }

    private fun premul(color: ColorARGB, opacityF32: Float): List<Interval> {
        val alpha = input(color.alpha) * input(opacityF32)
        return listOf(linear(input(color.red)) * alpha, linear(input(color.green)) * alpha, linear(input(color.blue)) * alpha, alpha)
    }

    private fun assertAdmits(premulRgba: List<Interval>, actual: UByteArray) {
        val admissible = listOf(srgb(premulRgba[0]), srgb(premulRgba[1]), srgb(premulRgba[2]), premulRgba[3]).map(::quantizedCodes)
        actual.indices.forEach { index ->
            assertTrue(actual[index].toInt() in admissible[index], "channel=$index admissible=${admissible[index]} actual=${actual.toList()}")
        }
    }

    private fun input(code: Int): Interval = interval(code / 255.0)
    private fun input(value: Float): Interval = interval(value.toDouble())
    private fun linear(value: Interval): Interval = if (value.hi <= .04045) value / 12.92 else if (value.lo >= .04045) ((value + .055) / 1.055).pow(2.4) else Interval(0.0, ((value.hi + .055) / 1.055).pow(2.4)).outward()
    private fun srgb(value: Interval): Interval = if (value.hi <= .0031308) value * 12.92 else if (value.lo >= .0031308) value.pow(1.0 / 2.4) * 1.055 - .055 else Interval(0.0, value.hi.pow(1.0 / 2.4) * 1.055 - .055).outward()

    private fun quantizedCodes(value: Interval): Set<Int> {
        val bounded = value.clamp()
        val first = ceil(bounded.lo * 255.0 - .5).toInt().coerceIn(0, 255)
        val last = floor(bounded.hi * 255.0 + .5).toInt().coerceIn(0, 255)
        val codes = (first..last).toSet()
        require(codes.size in 1..2 && last - first <= 1) { "Analytic RGBA8 envelope must be singleton or adjacent: $bounded -> $codes" }
        return codes
    }

    private data class Interval(val lo: Double, val hi: Double) {
        init { require(lo.isFinite() && hi.isFinite() && lo <= hi) }
        fun outward() = Interval(Math.nextDown(lo), Math.nextUp(hi))
        operator fun plus(other: Interval) = Interval(Math.nextDown(lo + other.lo), Math.nextUp(hi + other.hi))
        operator fun plus(other: Double) = Interval(Math.nextDown(lo + other), Math.nextUp(hi + other))
        operator fun minus(other: Double) = Interval(Math.nextDown(lo - other), Math.nextUp(hi - other))
        operator fun div(other: Double) = Interval(Math.nextDown(lo / other), Math.nextUp(hi / other))
        operator fun times(other: Interval) = Interval(Math.nextDown(lo * other.lo), Math.nextUp(hi * other.hi))
        operator fun times(other: Double) = Interval(Math.nextDown(lo * other), Math.nextUp(hi * other))
        fun pow(exponent: Double) = Interval(Math.nextDown(lo.pow(exponent)), Math.nextUp(hi.pow(exponent)))
        fun clamp() = Interval(lo.coerceIn(0.0, 1.0), hi.coerceIn(0.0, 1.0))
    }

    private fun interval(value: Double) = Interval(Math.nextDown(value), Math.nextUp(value))
    private val one = Interval(1.0, 1.0)
    private operator fun Interval.minus(other: Interval) = Interval(Math.nextDown(lo - other.hi), Math.nextUp(hi - other.lo))
}
