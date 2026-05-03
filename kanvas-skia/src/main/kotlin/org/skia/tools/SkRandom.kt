package org.skia.tools

import org.skia.math.SkScalar

/**
 * Lightweight LCG-style RNG. Bit-exact compatibility with Skia's `SkRandom`
 * is not a goal — random-driven GMs (e.g. SimpleRectGM) accept divergence
 * and target ≥ 95% similarity rather than 99%.
 */
public class SkRandom(seed: Long = 0L) {
    private var state: Long = if (seed == 0L) 0x12345678L else seed

    public fun nextU(): Int {
        state = (state * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
        return (state ushr 16).toInt()
    }

    public fun nextUScalar1(): SkScalar = (nextU() ushr 8).toFloat() / 0xFFFFFF.toFloat()

    public fun nextRangeU(min: Int, max: Int): Int {
        val span = max - min + 1
        return min + ((nextU() ushr 1) % span)
    }

    public fun nextRangeScalar(min: SkScalar, max: SkScalar): SkScalar =
        min + nextUScalar1() * (max - min)
}
