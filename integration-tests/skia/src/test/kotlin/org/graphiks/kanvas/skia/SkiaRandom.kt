package org.graphiks.kanvas.skia

/**
 * Test-local port of Skia's `SkRandom` stream.
 *
 * The generator uses a one-step LCG seed expansion followed by the
 * Marsaglia multiply-with-carry stream used by Skia's GM helpers.
 */
class SkiaRandom(seed: UInt = 0u) {
    private var k: UInt = expandSeed(seed).let { if (it == 0u) expandSeed(it) else it }
    private var j: UInt = expandSeed(k).let { if (it == 0u) expandSeed(it) else it }

    fun nextU(): UInt {
        k = K_MUL * (k and 0xffffu) + (k shr 16)
        j = J_MUL * (j and 0xffffu) + (j shr 16)
        return ((k shl 16) or (k shr 16)) + j
    }

    fun nextS(): Int = nextU().toInt()

    fun nextF(): Float {
        val bits = 0x3f800000u or (nextU() shr 9)
        return Float.fromBits(bits.toInt()) - 1f
    }

    private fun expandSeed(seed: UInt): UInt = LCG_MUL * seed + LCG_ADD

    private companion object {
        private const val LCG_MUL: UInt = 1664525u
        private const val LCG_ADD: UInt = 1013904223u
        private const val K_MUL: UInt = 30345u
        private const val J_MUL: UInt = 18000u
    }
}
