package org.skia.foundation


import org.skia.math.SkColorSetARGB
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Procedural noise shader — generates fractal-noise / turbulence
 * patterns matching Skia's `SkPerlinNoiseShader`. Unlike the gradient
 * shaders, this one synthesises colour per pixel from a deterministic
 * permutation table seeded by [seed]; there are no input stops.
 *
 * Mirrors:
 *   - `src/shaders/SkPerlinNoiseShaderImpl.h::PaintingData` for the
 *     lattice / noise table generation (4-channel, 256-entry SVG-spec
 *     pseudo-random sequence — see `feTurbulence` in
 *     [https://www.w3.org/TR/SVG11/filters.html#feTurbulenceElement]).
 *   - `src/opts/SkRasterPipeline_opts.h::perlin_noise` (HIGHP_STAGE) for
 *     the per-pixel inner loop: bilinear interpolation of dot-product
 *     gradients at the 4 lattice corners, Hermite smoothing, octave
 *     accumulation with `ratio = 0.5^octave`, then either
 *     `noise * 0.5 + 0.5` (FractalNoise) or `abs(noise)` (Turbulence).
 *
 * **Colour-space handling note** (Phase TBD): the noise output is
 * naturally in linear-light floats and is treated here as already in
 * the bitmap's working colour space (no [SkColorSpaceXformSteps] applied
 * — would need an "input is linear" entry-point that this codebase
 * doesn't yet expose). For canvases whose working space differs from
 * sRGB-linear (e.g., Rec.2020-linear), this introduces a gamut shift
 * versus upstream. Acceptable for visual parity since the noise PNG
 * references are mostly grays.
 */
public class SkPerlinNoiseShader internal constructor(
    private val type: Type,
    private val baseFrequencyX: SkScalar,
    private val baseFrequencyY: SkScalar,
    private val numOctaves: Int,
    private val seed: SkScalar,
    private val tileSize: SkISize,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    /** Two flavours: `kFractalNoise = noise·.5 + .5`, `kTurbulence = |noise|`. */
    public enum class Type { kFractalNoise, kTurbulence }

    private val stitchTiles: Boolean = !tileSize.isEmpty()
    private val paintingData: PaintingData = PaintingData(
        tileSize, seed, baseFrequencyX, baseFrequencyY,
    )

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        // No stop xform — see class docstring on colour-space handling.
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count) dst[i] = 0
            return
        }
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        val rgba = FloatArray(4)
        for (i in 0 until count) {
            computeNoise(lx, ly, rgba)
            // Premul (algorithm output is straight-alpha) + quantize.
            val a = rgba[3]
            val pa = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
            val pr = (rgba[0] * a * 255f + 0.5f).toInt().coerceIn(0, 255)
            val pg = (rgba[1] * a * 255f + 0.5f).toInt().coerceIn(0, 255)
            val pb = (rgba[2] * a * 255f + 0.5f).toInt().coerceIn(0, 255)
            dst[i] = SkColorSetARGB(pa, pr, pg, pb)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count * 4) dst[i] = 0f
            return
        }
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        val rgba = FloatArray(4)
        var di = 0
        for (i in 0 until count) {
            computeNoise(lx, ly, rgba)
            val a = rgba[3]
            dst[di]     = rgba[0] * a
            dst[di + 1] = rgba[1] * a
            dst[di + 2] = rgba[2] * a
            dst[di + 3] = a
            di += 4
            lx += stepX
            ly += stepY
        }
    }

    /**
     * Per-pixel noise computation — bilinear-interpolated gradient
     * lookups across [numOctaves] frequency doublings, accumulated with
     * geometric-decay weight (`ratio = 0.5^octave`). Output is
     * straight-alpha RGBA in `[0, 1]` (premul is applied by the caller).
     *
     * Mirrors `perlin_noise` HIGHP_STAGE byte-for-byte; comments map back
     * to the C++ source.
     */
    private fun computeNoise(localX: Float, localY: Float, out: FloatArray) {
        // Upstream `perlin_noise` raster-pipeline stage adds another `+0.5`
        // on top of the `seed_shader`-injected pixel centre, so the
        // effective sampling position is `(devX + 1, devY + 1) * baseFreq`
        // under an identity local-matrix. Matching that offset is critical
        // for byte-for-byte parity with the upstream reference PNGs — a
        // 0.5/baseFreq drift in noise-space accumulates a few percent
        // pixel mismatch over an 80-px tile.
        var noiseX = (localX + 0.5f) * paintingData.baseFrequencyX
        var noiseY = (localY + 0.5f) * paintingData.baseFrequencyY
        out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 0f
        var stitchDataX = paintingData.stitchDataX.toFloat()
        var stitchDataY = paintingData.stitchDataY.toFloat()
        var ratio = 1f

        val color = FloatArray(4)
        for (octave in 0 until numOctaves) {
            var floorValX = floor(noiseX)
            var floorValY = floor(noiseY)
            var ceilValX  = floorValX + 1f
            var ceilValY  = floorValY + 1f
            val fractValX = noiseX - floorValX
            val fractValY = noiseY - floorValY

            if (stitchTiles) {
                if (floorValX >= stitchDataX) floorValX -= stitchDataX
                if (floorValY >= stitchDataY) floorValY -= stitchDataY
                if (ceilValX  >= stitchDataX) ceilValX  -= stitchDataX
                if (ceilValY  >= stitchDataY) ceilValY  -= stitchDataY
            }

            // `iround(floorValX)` upstream — values are integer-valued floats
            // (sums of `latticeIdxX + floorValY`, both integer-valued). Round
            // defends against any sub-ulp drift under stitching subtractions.
            val latticeIdxX = paintingData.latticeSelector[floorValX.roundToInt() and 0xFF] and 0xFF
            val latticeIdxY = paintingData.latticeSelector[ceilValX .roundToInt() and 0xFF] and 0xFF
            val b00 = (latticeIdxX + floorValY.roundToInt()) and 0xFF
            val b10 = (latticeIdxY + floorValY.roundToInt()) and 0xFF
            val b01 = (latticeIdxX + ceilValY .roundToInt()) and 0xFF
            val b11 = (latticeIdxY + ceilValY .roundToInt()) and 0xFF

            // Hermite (cubic ease-in-out) smoothing of the fractional part.
            val smoothX = fractValX * fractValX * (3f - 2f * fractValX)
            val smoothY = fractValY * fractValY * (3f - 2f * fractValY)

            for (channel in 0 until 4) {
                val noiseCh = paintingData.noise[channel]
                var u = perlinDot(noiseCh, b00, fractValX,        fractValY)
                var v = perlinDot(noiseCh, b10, fractValX - 1f,   fractValY)
                val A = lerp(u, v, smoothX)
                u = perlinDot(noiseCh, b01, fractValX,        fractValY - 1f)
                v = perlinDot(noiseCh, b11, fractValX - 1f,   fractValY - 1f)
                val B = lerp(u, v, smoothX)
                color[channel] = lerp(A, B, smoothY)
            }

            if (type == Type.kTurbulence) {
                color[0] = abs(color[0])
                color[1] = abs(color[1])
                color[2] = abs(color[2])
                color[3] = abs(color[3])
            }

            out[0] += color[0] * ratio
            out[1] += color[1] * ratio
            out[2] += color[2] * ratio
            out[3] += color[3] * ratio

            noiseX *= 2f
            noiseY *= 2f
            stitchDataX *= 2f
            stitchDataY *= 2f
            ratio *= 0.5f
        }

        if (type == Type.kFractalNoise) {
            // FractalNoise: signed → unsigned via `n·.5 + .5`. Matches upstream's
            // `r = mad(r, 0.5f, 0.5f)` ladder.
            out[0] = out[0] * 0.5f + 0.5f
            out[1] = out[1] * 0.5f + 0.5f
            out[2] = out[2] * 0.5f + 0.5f
            out[3] = out[3] * 0.5f + 0.5f
        }

        // Clamp RGB to [0, 1]; alpha to [0, 1]. Mirrors `clamp_01_(r) * a` etc.
        // Note: alpha is clamped *before* RGB is multiplied by it (the caller
        // does the premul), so we keep them in straight-alpha space here.
        out[0] = out[0].coerceIn(0f, 1f)
        out[1] = out[1].coerceIn(0f, 1f)
        out[2] = out[2].coerceIn(0f, 1f)
        out[3] = out[3].coerceIn(0f, 1f)
    }

    public companion object {
        public const val kMaxOctaves: Int = 255

        /**
         * Mirror of `SkShaders::MakeFractalNoise`. With `numOctaves = 0`
         * upstream short-circuits to a `[.5, .5, .5, .5]` solid colour
         * — we replicate that by keeping a 1-octave path that the
         * compute loop simply skips when `numOctaves == 0`, then the
         * `r·.5 + .5` final step lands on `.5`.
         */
        public fun MakeFractalNoise(
            baseFrequencyX: SkScalar,
            baseFrequencyY: SkScalar,
            numOctaves: Int,
            seed: SkScalar,
            tileSize: SkISize? = null,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkPerlinNoiseShader {
            requireValidInput(baseFrequencyX, baseFrequencyY, numOctaves, tileSize, seed)
            return SkPerlinNoiseShader(
                Type.kFractalNoise, baseFrequencyX, baseFrequencyY, numOctaves, seed,
                tileSize ?: SkISize.MakeEmpty(), localMatrix,
            )
        }

        /**
         * Mirror of `SkShaders::MakeTurbulence`. With `numOctaves = 0`
         * upstream returns transparent black; we replicate via the
         * compute loop short-circuit (zero-octave loop body never runs,
         * Turbulence skips the `+ .5` finalize step → output is `(0,0,0,0)`).
         */
        public fun MakeTurbulence(
            baseFrequencyX: SkScalar,
            baseFrequencyY: SkScalar,
            numOctaves: Int,
            seed: SkScalar,
            tileSize: SkISize? = null,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkPerlinNoiseShader {
            requireValidInput(baseFrequencyX, baseFrequencyY, numOctaves, tileSize, seed)
            return SkPerlinNoiseShader(
                Type.kTurbulence, baseFrequencyX, baseFrequencyY, numOctaves, seed,
                tileSize ?: SkISize.MakeEmpty(), localMatrix,
            )
        }

        private fun requireValidInput(
            baseX: SkScalar, baseY: SkScalar, numOctaves: Int,
            tileSize: SkISize?, seed: SkScalar,
        ) {
            require(baseX >= 0f && baseY >= 0f) { "base frequencies must be ≥ 0" }
            require(numOctaves in 0..kMaxOctaves) { "numOctaves out of [0, $kMaxOctaves]" }
            if (tileSize != null) {
                require(tileSize.width >= 0 && tileSize.height >= 0) { "tile size must be ≥ 0" }
            }
            require(seed.isFinite()) { "seed must be finite" }
        }
    }
}

/**
 * SVG `feTurbulence`-spec deterministic painting data. Built once per
 * [SkPerlinNoiseShader] (the `SkOnce` cache in upstream); here we just
 * stash it as a field since shaders are immutable in this port.
 *
 * Mirrors `SkPerlinNoiseShaderImpl.h::PaintingData::init` byte-for-byte.
 */
private class PaintingData(
    tileSize: SkISize,
    seed: SkScalar,
    baseX: SkScalar,
    baseY: SkScalar,
) {
    /**
     * Permuted identity table — `[0..255]` shuffled by the same LCG that
     * SVG `feTurbulence` mandates (see [random]).
     */
    val latticeSelector: IntArray = IntArray(kBlockSize)

    /** `noise[channel][index][component]` — 16-bit unsigned encoding of
     *  unit-length 2D gradient vectors; component `c` decodes to
     *  `2·c/65535 − 1 ∈ [-1, 1]`. */
    val noise: Array<Array<IntArray>> = Array(4) { Array(kBlockSize) { IntArray(2) } }

    /** Stitch-adjusted base frequencies (`stitch()` may snap them). */
    var baseFrequencyX: Float = baseX
        private set
    var baseFrequencyY: Float = baseY
        private set

    /** `(tileWidth · stitched.fX, tileHeight · stitched.fY)` rounded — used
     *  as the wrap modulus in [SkPerlinNoiseShader.computeNoise]. Zero
     *  when not stitching. */
    var stitchDataX: Int = 0
        private set
    var stitchDataY: Int = 0
        private set

    private var randomSeed: Int = 0

    init {
        // SVG spec: truncate the seed (don't round). Then clamp into
        // `[1, kRandMaximum - 1]` per the `feTurbulence` algorithm.
        randomSeed = seed.toInt()
        if (randomSeed <= 0) {
            randomSeed = -(randomSeed % (kRandMaximum - 1)) + 1
        }
        if (randomSeed > kRandMaximum - 1) {
            randomSeed = kRandMaximum - 1
        }

        // Init: latticeSelector = identity, noise = random pairs in [0, 2·kBlockSize).
        for (channel in 0 until 4) {
            for (i in 0 until kBlockSize) {
                latticeSelector[i] = i
                noise[channel][i][0] = random() % (2 * kBlockSize)
                noise[channel][i][1] = random() % (2 * kBlockSize)
            }
        }

        // Fisher-Yates-style shuffle of latticeSelector (downwards from
        // `kBlockSize-1`, swap with random index in `[0, kBlockSize)`).
        for (i in kBlockSize - 1 downTo 1) {
            val k = latticeSelector[i]
            val j = random() % kBlockSize
            latticeSelector[i] = latticeSelector[j]
            latticeSelector[j] = k
        }

        // Permute noise table by the now-shuffled latticeSelector. (Two
        // passes of "random index" effectively decorrelates the two
        // sources of entropy — see SVG spec.)
        val noiseCopy = Array(4) { ch -> Array(kBlockSize) { i -> intArrayOf(noise[ch][i][0], noise[ch][i][1]) } }
        for (i in 0 until kBlockSize) {
            for (channel in 0 until 4) {
                noise[channel][i][0] = noiseCopy[channel][latticeSelector[i]][0]
                noise[channel][i][1] = noiseCopy[channel][latticeSelector[i]][1]
            }
        }

        // Replace each `noise[channel][i]` pair with a unit-length
        // gradient encoded as 16-bit unsigned: `(comp + 1) · kHalfMax16Bits`.
        // This is the format `compute_perlin_vector` decodes back.
        val invBlockSize = 1f / kBlockSize.toFloat()
        for (channel in 0 until 4) {
            for (i in 0 until kBlockSize) {
                var gx = (noise[channel][i][0] - kBlockSize) * invBlockSize
                var gy = (noise[channel][i][1] - kBlockSize) * invBlockSize
                val len = kotlin.math.sqrt(gx * gx + gy * gy)
                if (len > 0f) { gx /= len; gy /= len }
                noise[channel][i][0] = ((gx + 1f) * kHalfMax16Bits).roundToInt().coerceIn(0, 65535)
                noise[channel][i][1] = ((gy + 1f) * kHalfMax16Bits).roundToInt().coerceIn(0, 65535)
            }
        }

        // If stitching is enabled, snap base frequencies so tile borders
        // are continuous, then derive stitch wrap positions.
        if (!tileSize.isEmpty()) {
            stitch(tileSize)
        }
    }

    /** SVG `feTurbulence` linear-congruential PRNG (a = 16807, m = 2³¹ − 1). */
    private fun random(): Int {
        val result = kRandAmplitude * (randomSeed % kRandQ) - kRandR * (randomSeed / kRandQ)
        randomSeed = if (result <= 0) result + kRandMaximum else result
        return randomSeed
    }

    /**
     * Adjust [baseFrequencyX] / [Y] to the nearest fraction `k/tileSize`
     * and capture the wrap modulus. Tile borders only stitch when the
     * frequency hits an integer multiple of `1/tileSize`.
     */
    private fun stitch(tileSize: SkISize) {
        val tileW = tileSize.width.toFloat()
        val tileH = tileSize.height.toFloat()
        if (baseFrequencyX > 0f) {
            val low = floor(tileW * baseFrequencyX) / tileW
            val high = kotlin.math.ceil(tileW * baseFrequencyX) / tileW
            baseFrequencyX = if (low > 0f && (baseFrequencyX / low) < (high / baseFrequencyX)) low else high
        }
        if (baseFrequencyY > 0f) {
            val low = floor(tileH * baseFrequencyY) / tileH
            val high = kotlin.math.ceil(tileH * baseFrequencyY) / tileH
            baseFrequencyY = if (low > 0f && (baseFrequencyY / low) < (high / baseFrequencyY)) low else high
        }
        stitchDataX = (tileW * baseFrequencyX).roundToInt().coerceAtMost(Int.MAX_VALUE - kPerlinNoise)
        stitchDataY = (tileH * baseFrequencyY).roundToInt().coerceAtMost(Int.MAX_VALUE - kPerlinNoise)
    }

    private companion object {
        const val kBlockSize: Int = 256
        const val kRandMaximum: Int = Int.MAX_VALUE  // 2³¹ − 1
        const val kRandAmplitude: Int = 16807         // 7⁵
        const val kRandQ: Int = 127773                // m / a
        const val kRandR: Int = 2836                  // m % a
        const val kHalfMax16Bits: Float = 32767.5f
        const val kPerlinNoise: Int = 4096
    }
}

/**
 * Decode the 16-bit-unsigned-encoded gradient vector at `noise[index]`,
 * dot-multiply with `(x, y)`. Mirrors `compute_perlin_vector` in the
 * raster pipeline op.
 */
private fun perlinDot(noiseChannel: Array<IntArray>, index: Int, x: Float, y: Float): Float {
    val sample = noiseChannel[index]
    val vx = sample[0] * (2f / 65535f) - 1f
    val vy = sample[1] * (2f / 65535f) - 1f
    return vx * x + vy * y
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
