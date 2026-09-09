package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.floor

/**
 * W5a's public numeric gate: each channel must be a singleton or two adjacent
 * UNORM8 codes after the explicitly modeled F32 arithmetic.
 */
internal object WgslFloatEnvelopeV1Oracle {
    fun admissibleUnorm8(valueF32: Float): IntRange {
        val scaled = valueF32.coerceIn(0f, 1f) * 255f
        val lower = floor(scaled.toDouble()).toInt().coerceIn(0, 255)
        val upper = ceil(scaled.toDouble()).toInt().coerceIn(0, 255)
        require(upper - lower <= 1) { "WgslFloatEnvelopeV1 must be singleton or adjacent" }
        return lower..upper
    }

    fun assertAdmits(expectedLinearPremul: FloatArray, observedRgba8: UByteArray) {
        require(expectedLinearPremul.size == 4)
        require(observedRgba8.size == 4)
        expectedLinearPremul.indices.forEach { channel ->
            require(observedRgba8[channel].toInt() in admissibleUnorm8(expectedLinearPremul[channel])) {
                "channel=$channel observed=${observedRgba8[channel]} expected=${admissibleUnorm8(expectedLinearPremul[channel])}"
            }
        }
    }
}
