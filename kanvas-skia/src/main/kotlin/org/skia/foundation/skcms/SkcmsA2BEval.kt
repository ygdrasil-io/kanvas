@file:JvmName("SkcmsA2BEval")

package org.skia.foundation.skcms
import org.skia.math.SkcmsMatrix3x4

/**
 * Phase F4 of `MIGRATION_PLAN_COLORSPACE_PORT.md` — multi-dimensional
 * CLUT evaluation and the A2B / B2A pipeline composers.
 *
 * Mirror of:
 *  - `clut(input_channels, output_channels, grid_points, grid_8, grid_16, ...)`
 *    in [Transform_inl.h:685-764](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/Transform_inl.h).
 *  - The A2B / B2A op chain composition in
 *    [skcms.cc:2828-2925](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/skcms.cc).
 *
 * Scope: scalar reference implementation. Per-pixel float math; the
 * upstream SIMD path is not ported. CLUT interpolation handles 1 to 4
 * input channels (RGB or CMYK) and 3 or 4 output channels. ICC PCS
 * conversions (Lab → XYZ) live above this layer.
 *
 * Note on consumers: `SkColorSpace.make(profile)` doesn't currently
 * route through A2B / B2A. This phase ports the eval primitives so the
 * data structures landed in F1 + the parser hooks landed in F2 are no
 * longer dead weight, and so any future consumer (e.g. CMYK profile
 * support, an `SkRasterPipeline` port) can plug in without
 * re-implementing the algorithm.
 */

/**
 * N-dimensional grid lookup with 2^dim corner interpolation. Mirrors
 * `clut` in `Transform_inl.h:685-764`.
 *
 * @param inputChannels number of input dimensions (1..4).
 * @param outputChannels output channels per grid entry (3 or 4).
 * @param gridPoints per-dimension grid resolution; index `i` is the count
 *   for input dimension `i`.
 * @param grid8 / grid16 exactly one is non-null. `grid16` is big-endian
 *   uint16 entries, ICC convention.
 * @param values size-4 input/output buffer (`r`, `g`, `b`, `a`). On
 *   entry, `values[0..inputChannels-1]` is the unit-square address into
 *   the grid. On exit, `values[0..outputChannels-1]` is the interpolated
 *   sample. `values[outputChannels..]` is left untouched.
 */
public fun skcmsClut(
    inputChannels: Int,
    outputChannels: Int,
    gridPoints: IntArray,
    grid8: ByteArray?,
    grid16: ByteArray?,
    values: FloatArray,
) {
    require(inputChannels in 1..4) { "inputChannels must be 1..4, got $inputChannels" }
    require(outputChannels == 3 || outputChannels == 4) {
        "outputChannels must be 3 or 4, got $outputChannels"
    }
    require((grid8 == null) != (grid16 == null)) {
        "exactly one of grid8 / grid16 must be non-null"
    }

    val dim = inputChannels

    // index[i] = lo contribution for dimension i; index[i+4] = hi.
    val index = IntArray(8)
    val weight = FloatArray(8)
    var stride = 1
    for (i in dim - 1 downTo 0) {
        // x is where we logically want to sample the i-th dimension.
        val xi = values[i] * (gridPoints[i] - 1).toFloat()
        // Clamp to the valid grid range so out-of-band inputs don't read
        // off the end of the table. (Upstream SIMD relies on the caller
        // having clamped; the scalar port is more defensive.)
        val xClamped = xi.coerceIn(0f, (gridPoints[i] - 1).toFloat())
        val lo = xClamped.toInt()
        val hi = (minus1Ulp(xClamped + 1f)).toInt()
        index[i + 0] = lo * stride
        index[i + 4] = hi * stride
        stride *= gridPoints[i]

        val t = xClamped - lo.toFloat()
        weight[i + 0] = 1f - t
        weight[i + 4] = t
    }

    var outR = 0f
    var outG = 0f
    var outB = 0f
    var outA = 0f

    val combos = 1 shl dim
    val sample = FloatArray(4)
    for (combo in 0 until combos) {
        // Each (combo & N) bit picks lo or hi index/weight for dim N-1.
        // Start with channel 0 unconditionally, then accumulate.
        var ix = index[0 + ((combo and 1) shl 2)]
        var w = weight[0 + ((combo and 1) shl 2)]
        if (dim >= 2) {
            ix += index[1 + ((combo and 2) shl 1)]
            w *= weight[1 + ((combo and 2) shl 1)]
        }
        if (dim >= 3) {
            ix += index[2 + ((combo and 4))]
            w *= weight[2 + ((combo and 4))]
        }
        if (dim >= 4) {
            ix += index[3 + ((combo and 8) shr 1)]
            w *= weight[3 + ((combo and 8) shr 1)]
        }

        sampleClut(ix, outputChannels, grid8, grid16, sample)
        outR += w * sample[0]
        outG += w * sample[1]
        outB += w * sample[2]
        if (outputChannels == 4) outA += w * sample[3]
    }

    values[0] = outR
    values[1] = outG
    values[2] = outB
    if (outputChannels == 4) values[3] = outA
}

/**
 * Sample a single grid entry at flat offset [ix]. Writes
 * `outputChannels` floats into [out]. Mirror of `sample_clut_8` /
 * `sample_clut_16` in `Transform_inl.h:641-683`.
 */
private fun sampleClut(
    ix: Int,
    outputChannels: Int,
    grid8: ByteArray?,
    grid16: ByteArray?,
    out: FloatArray,
) {
    if (grid8 != null) {
        val base = ix * outputChannels
        out[0] = (grid8[base].toInt() and 0xFF) * (1f / 255f)
        out[1] = (grid8[base + 1].toInt() and 0xFF) * (1f / 255f)
        out[2] = (grid8[base + 2].toInt() and 0xFF) * (1f / 255f)
        if (outputChannels == 4) {
            out[3] = (grid8[base + 3].toInt() and 0xFF) * (1f / 255f)
        }
    } else {
        val g16 = grid16!!
        val base = ix * outputChannels * 2
        out[0] = beU16(g16, base) * (1f / 65535f)
        out[1] = beU16(g16, base + 2) * (1f / 65535f)
        out[2] = beU16(g16, base + 4) * (1f / 65535f)
        if (outputChannels == 4) {
            out[3] = beU16(g16, base + 6) * (1f / 65535f)
        }
    }
}

private fun beU16(buf: ByteArray, off: Int): Float =
    (((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)).toFloat()

private fun applyCurves(curves: Array<SkcmsCurve?>, values: FloatArray, n: Int) {
    for (i in 0 until n) {
        val c = curves[i] ?: continue
        values[i] = evalCurve(c, values[i])
    }
}

private fun applyMatrix3x4(m: SkcmsMatrix3x4, values: FloatArray) {
    val r = values[0]; val g = values[1]; val b = values[2]
    for (i in 0 until 3) {
        values[i] = m.vals[i][0] * r + m.vals[i][1] * g + m.vals[i][2] * b + m.vals[i][3]
    }
}

/**
 * Evaluate the A2B pipeline on `values[0..3]` (rgba, with `a` only used
 * when input/output channels include alpha):
 *  1. **Input curves** — applied per channel (`a2b.inputChannels`).
 *  2. **CLUT** — `inputChannels` → `outputChannels` interpolation.
 *  3. **Matrix curves** — applied per matrix channel if `matrixChannels == 3`.
 *  4. **3×4 matrix** — affine on RGB; the 4th column is the offset.
 *  5. **Output curves** — applied per output channel if `outputChannels == 3`.
 *
 * Mirror of the src-A2B branch in `skcms.cc:2859-2887`.
 */
public fun evalA2b(a2b: SkcmsA2B, values: FloatArray) {
    require(values.size >= 4) { "values must be at least size 4" }

    if (a2b.inputChannels > 0) {
        applyCurves(a2b.inputCurves, values, a2b.inputChannels)
        // Clamp inputs to [0,1] before the grid lookup. Mirror the
        // `add_op(Op::clamp)` upstream issues right before clut_A2B.
        for (i in 0 until a2b.inputChannels) {
            values[i] = values[i].coerceIn(0f, 1f)
        }
        skcmsClut(
            a2b.inputChannels, a2b.outputChannels, a2b.gridPoints,
            a2b.grid8, a2b.grid16, values,
        )
        if (a2b.inputChannels == 4) {
            // CMYK is opaque (skcms.cc:2860 STAGE clut_A2B).
            values[3] = 1f
        }
    }

    if (a2b.matrixChannels == 3) {
        applyCurves(a2b.matrixCurves, values, 3)
        a2b.matrix?.let { applyMatrix3x4(it, values) }
    }

    if (a2b.outputChannels == 3) {
        applyCurves(a2b.outputCurves, values, 3)
    }
}

/**
 * Evaluate the B2A pipeline on `values[0..3]`:
 *  1. **Input curves** — per channel if `inputChannels == 3`.
 *  2. **Matrix curves + 3×4 matrix** — if `matrixChannels == 3`.
 *  3. **CLUT** — `inputChannels` → `outputChannels` (typical CMYK dst:
 *     3 → 4).
 *  4. **Output curves** — per output channel.
 *
 * Mirror of the dst-B2A branch in `skcms.cc:2912-2925`.
 */
public fun evalB2a(b2a: SkcmsB2A, values: FloatArray) {
    require(values.size >= 4) { "values must be at least size 4" }

    if (b2a.inputChannels == 3) {
        applyCurves(b2a.inputCurves, values, 3)
    }

    if (b2a.matrixChannels == 3) {
        b2a.matrix?.let { applyMatrix3x4(it, values) }
        applyCurves(b2a.matrixCurves, values, 3)
    }

    if (b2a.outputChannels > 0) {
        // Clamp inputs to [0,1] before the grid lookup.
        for (i in 0 until b2a.inputChannels.coerceAtLeast(3)) {
            values[i] = values[i].coerceIn(0f, 1f)
        }
        skcmsClut(
            // For B2A, the CLUT consumes the matrix-stage output (3
            // channels in the standard ICC v4 layout) and emits the
            // device-space values (`outputChannels`).
            inputChannels = if (b2a.inputChannels == 0) 3 else b2a.inputChannels,
            outputChannels = b2a.outputChannels,
            gridPoints = b2a.gridPoints,
            grid8 = b2a.grid8,
            grid16 = b2a.grid16,
            values = values,
        )
        applyCurves(b2a.outputCurves, values, b2a.outputChannels)
    }
}
