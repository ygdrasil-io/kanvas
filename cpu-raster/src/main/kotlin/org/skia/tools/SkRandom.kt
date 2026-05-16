package org.skia.tools

import org.graphiks.math.SkScalar

/**
 * Bit-compatible port of Skia's [`SkRandom`](https://github.com/google/skia/blob/main/include/utils/SkRandom.h).
 *
 * Two-stream multiply-with-carry. Same constants and same `nextU` mixing as
 * upstream so GM tests that consume `SkRandom` (notably `SimpleRectGM`) draw
 * the same sequence of rects/colours as the reference.
 *
 * C++ unsigned arithmetic is reproduced with Kotlin `Int` + `ushr`; signed
 * `+` / `*` overflow yields the same low-32 bit pattern as `uint32_t`.
 */
public class SkRandom(seed: Int = 0) {
    private var fK: Int = 0
    private var fJ: Int = 0

    init { setSeed(seed) }

    public fun setSeed(seed: Int) {
        fK = nextLCG(seed)
        if (fK == 0) fK = nextLCG(fK)
        fJ = nextLCG(fK)
        if (fJ == 0) fJ = nextLCG(fJ)
    }

    public fun nextU(): Int {
        fK = kKMul * (fK and 0xFFFF) + (fK ushr 16)
        fJ = kJMul * (fJ and 0xFFFF) + (fJ ushr 16)
        return ((fK shl 16) or (fK ushr 16)) + fJ
    }

    public fun nextS(): Int = nextU()

    public fun nextF(): Float =
        Float.fromBits(0x3F800000 or (nextU() ushr 9)) - 1.0f

    public fun nextRangeF(min: Float, max: Float): Float = min + nextF() * (max - min)

    public fun nextBits(bitCount: Int): Int {
        require(bitCount in 1..32)
        return nextU() ushr (32 - bitCount)
    }

    public fun nextRangeU(min: Int, max: Int): Int {
        val range = max - min + 1
        return if (range == 0) nextU()
        else min + ((nextU().toLong() and 0xFFFFFFFFL) % range.toLong().and(0xFFFFFFFFL)).toInt()
    }

    public fun nextULessThan(count: Int): Int = nextRangeU(0, count - 1)

    /** Uniform float in `[0, 1)` derived from the upper 16 bits of `nextU` (matches Skia's `SkFixedToScalar(nextUFixed1())`). */
    public fun nextUScalar1(): SkScalar = (nextU() ushr 16).toFloat() / 65536.0f

    public fun nextRangeScalar(min: SkScalar, max: SkScalar): SkScalar =
        nextUScalar1() * (max - min) + min

    /** Uniform float in `[-1, 1)` derived from `nextS() >> 15` as `SkFixed`. */
    public fun nextSScalar1(): SkScalar = (nextS() shr 15).toFloat() / 65536.0f

    public fun nextBool(): Boolean = (nextU().toLong() and 0xFFFFFFFFL) >= 0x80000000L

    public fun nextBiasedBool(fractionTrue: SkScalar): Boolean = nextUScalar1() <= fractionTrue

    private fun nextLCG(seed: Int): Int = kMul * seed + kAdd

    private companion object {
        // Numerical Recipes in C, p. 284 — LCG that bootstraps the seed.
        const val kMul: Int = 1664525
        const val kAdd: Int = 1013904223
        // Multiply-with-carry constants.
        const val kKMul: Int = 30345
        const val kJMul: Int = 18000
    }
}
