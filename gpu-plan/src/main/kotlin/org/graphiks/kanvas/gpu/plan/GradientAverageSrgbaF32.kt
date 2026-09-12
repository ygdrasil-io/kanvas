package org.graphiks.kanvas.gpu.plan

import java.math.BigInteger

/** One final roundTiesToEven conversion of the exact straight-sRGB integral. */
public data class GradientAverageSrgbaF32(
    public val redF32: Float,
    public val greenF32: Float,
    public val blueF32: Float,
    public val alphaF32: Float,
)

internal fun GradientStopSlabPlanV1.exactAverageSrgbaF32(): GradientAverageSrgbaF32 {
    val stops = copyStops()
    require(stops.first().positionF32 == 0f && stops.last().positionF32 == 1f)
    val channelsF32 = (0..3).map { channelI32 ->
        val accumulator = ExactBinaryTrapezoids()
        for ((left, right) in stops.zipWithNext()) {
            fun GradientStopPlanV1.channelF32(): Float = with(straightSrgbF32) {
                when (channelI32) { 0 -> red; 1 -> green; 2 -> blue; else -> alpha }
            }
            accumulator.add(left.positionF32, right.positionF32, left.channelF32(), right.channelF32())
        }
        accumulator.roundF32()
    }
    return GradientAverageSrgbaF32(channelsF32[0], channelsF32[1], channelsF32[2], channelsF32[3])
}

/** Every finite F32 is an integer times 2^-149; trapezoids share 2^-299.
 * Keeping that common denominator makes all differences, products and sums exact,
 * including a zero-width hard stop. BigInteger bit strings have no fixed-width suffix.
 */
private class ExactBinaryTrapezoids {
    private var integralBits: BigInteger = BigInteger.ZERO

    private fun units(valueF32: Float): BigInteger {
        require(valueF32.isFinite())
        val bitsI32 = valueF32.toRawBits()
        val exponentI32 = (bitsI32 ushr 23) and 255
        val significandI32 = (bitsI32 and 0x7fffff) or if (exponentI32 == 0) 0 else 0x800000
        val magnitudeBits = BigInteger.valueOf(significandI32.toLong()).shiftLeft(maxOf(0, exponentI32 - 1))
        return if (bitsI32 < 0) magnitudeBits.negate() else magnitudeBits
    }

    fun add(leftPositionF32: Float, rightPositionF32: Float, leftChannelF32: Float, rightChannelF32: Float) {
        integralBits += (units(rightPositionF32) - units(leftPositionF32)) *
            (units(leftChannelF32) + units(rightChannelF32))
    }

    fun roundF32(): Float {
        if (integralBits.signum() == 0) return 0f
        val signI32 = if (integralBits.signum() < 0) Int.MIN_VALUE else 0
        val magnitudeBits = integralBits.abs()
        // A normal retains 24 significant bits; subnormals retain multiples of 2^-149.
        var shiftI32 = maxOf(magnitudeBits.bitLength() - 24, 150)
        var roundedBits = magnitudeBits.shiftRight(shiftI32)
        val remainderBits = magnitudeBits - roundedBits.shiftLeft(shiftI32)
        val halfBits = BigInteger.ONE.shiftLeft(shiftI32 - 1)
        if (remainderBits > halfBits || remainderBits == halfBits && roundedBits.testBit(0)) roundedBits += BigInteger.ONE
        if (roundedBits.bitLength() > 24) {
            roundedBits = roundedBits.shiftRight(1)
            shiftI32++
        }
        if (roundedBits.bitLength() < 24) return Float.fromBits(signI32 or roundedBits.toInt())
        val exponentI32 = shiftI32 - 299 + 23 + 127
        require(exponentI32 in 1..254)
        return Float.fromBits(signI32 or (exponentI32 shl 23) or (roundedBits.toInt() and 0x7fffff))
    }
}
