package org.graphiks.kanvas.surface.gpu

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * CPU-side pixel oracles for prepared-image correctness tests.
 *
 * Every function is a pure-Kotlin reference: no GPU, no WGSL, no shader helpers.
 * Raw RGBA helpers own only byte-layout, texel-center, clamp, and geometry
 * evidence — they are never a physical GPU colour oracle.
 *
 * Colour contract:
 *
 * - Source colour : [org.graphiks.kanvas.surface.GPUColorFormat.RGBA8_UNORM_SRGB]
 * - Source A8/coverage : RGBA8Unorm
 * - Upload colour : StraightEncodedSrgb
 * - Shader : LinearPremul
 * - Output logic : EncodedPremulSrgb
 */
object GPUPreparedImagePixelOracle {

    enum class SampleKind { NEAREST, LINEAR }

    // ---- Raw sampling oracles ------------------------------------------------

    /**
     * Nearest-neighbour sample from a raw RGBA byte buffer.
     *
     * UV coordinates are in the range [0, 1] where (0,0) is top-left and
     * (1,1) is bottom-right.  Values outside [0, 1] are clamped.
     * Texel selection uses the WebGPU texel-centre model:
     *     floor(u * width) for nearest.
     */
    fun rawRgbaNearestSample(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): ByteArray {
        requireRawRgba(bytes, width, height)
        requireFiniteUv(u, v)
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val x = floor(cu * width).toInt().coerceIn(0, width - 1)
        val y = floor(cv * height).toInt().coerceIn(0, height - 1)
        val offset = (y * width + x) * 4
        return bytes.copyOfRange(offset, offset + 4)
    }

    /**
     * Bilinear sample from a raw RGBA byte buffer.
     *
     * Uses WebGPU texel-centre coordinates:
     *     fx = u * width - 0.5
     *     fy = v * height - 0.5
     *
     * UV clamping is the same as [rawRgbaNearestSample].
     */
    fun rawRgbaLinearSample(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): ByteArray {
        requireRawRgba(bytes, width, height)
        requireFiniteUv(u, v)
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val fx = cu * width - 0.5f
        val fy = cv * height - 0.5f
        val rawX0 = floor(fx).toInt()
        val rawY0 = floor(fy).toInt()
        val wx1 = fx - rawX0
        val wy1 = fy - rawY0
        val x0 = rawX0.coerceIn(0, width - 1)
        val y0 = rawY0.coerceIn(0, height - 1)
        val x1 = (rawX0 + 1).coerceIn(0, width - 1)
        val y1 = (rawY0 + 1).coerceIn(0, height - 1)
        val wx0 = 1f - wx1
        val wy0 = 1f - wy1

        val p00 = readChannelFloats(bytes, width, x0, y0)
        val p10 = readChannelFloats(bytes, width, x1, y0)
        val p01 = readChannelFloats(bytes, width, x0, y1)
        val p11 = readChannelFloats(bytes, width, x1, y1)

        return ByteArray(4) { c ->
            val value = p00[c] * wx0 * wy0 +
                p10[c] * wx1 * wy0 +
                p01[c] * wx0 * wy1 +
                p11[c] * wx1 * wy1
            (value + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }

    // ---- Source-rect and UV clamp --------------------------------------------

    /**
     * Samples a [bytes] image using a source rectangle and UV normalised
     * coordinate, clamping UV to the source-rect bounds.
     *
     * The source rect ([srcL], [srcT], [srcR], [srcB]) is in normalised
     * image coordinates [0, 1].  The sample coordinate ([u], [v]) is clamped
     * to that rect, then passed directly to the chosen [sample] kind as
     * absolute full-image UV coordinates — it is never remapped to [0, 1].
     */
    fun rawRgbaSourceRectSample(
        bytes: ByteArray,
        width: Int,
        height: Int,
        srcL: Float,
        srcT: Float,
        srcR: Float,
        srcB: Float,
        u: Float,
        v: Float,
        sample: SampleKind,
    ): ByteArray {
        requireRawRgba(bytes, width, height)
        require(srcL.isFinite() && srcT.isFinite() && srcR.isFinite() && srcB.isFinite())
        require(srcL in 0f..1f && srcT in 0f..1f)
        require(srcR in 0f..1f && srcB in 0f..1f)
        require(srcL < srcR && srcT < srcB)
        requireFiniteUv(u, v)
        val imageU = u.coerceIn(srcL, srcR)
        val imageV = v.coerceIn(srcT, srcB)
        return when (sample) {
            SampleKind.NEAREST -> rawRgbaNearestSample(bytes, width, height, imageU, imageV)
            SampleKind.LINEAR -> rawRgbaLinearSample(bytes, width, height, imageU, imageV)
        }
    }

    // ---- Tint and paint alpha -------------------------------------------------

    /**
     * Applies a tint colour and paint-level alpha to a source pixel (RGBA,
     * premultiplied).  The tint is applied **exactly once**: each source
     * channel is multiplied by the corresponding tint component, and the
     * overall alpha is scaled by [paintAlpha].
     *
     * This matches `GPUPreparedImagePixelOracle`'s per-pixel behaviour:
     *   dst.R = src.R × tint.R
     *   dst.G = src.G × tint.G
     *   dst.B = src.B × tint.B
     *   dst.A = src.A × paintAlpha
     */
    fun rawRgbaApplyTint(
        srcRgba: ByteArray,
        tintRgba: FloatArray,
        paintAlpha: Float,
    ): ByteArray {
        val r = (srcRgba[0].toInt().and(0xFF) * tintRgba[0] + 0.5f).toInt().coerceIn(0, 255)
        val g = (srcRgba[1].toInt().and(0xFF) * tintRgba[1] + 0.5f).toInt().coerceIn(0, 255)
        val b = (srcRgba[2].toInt().and(0xFF) * tintRgba[2] + 0.5f).toInt().coerceIn(0, 255)
        val a = (srcRgba[3].toInt().and(0xFF) * paintAlpha + 0.5f).toInt().coerceIn(0, 255)
        return byteArrayOf(r.toByte(), g.toByte(), b.toByte(), a.toByte())
    }

    // ---- Comparison oracles ---------------------------------------------------

    /** Returns true when every channel of [a] equals the corresponding channel of [b]. */
    fun rawExactMatch(a: ByteArray, b: ByteArray): Boolean = a.contentEquals(b)

    /**
     * Returns the true unsigned maximum per-channel absolute difference
     * between [a] and [b].  Both arrays must be the same length.
     */
    fun maxChannelDelta(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size)
        return a.indices.maxOfOrNull { index ->
            kotlin.math.abs((a[index].toInt() and 0xff) - (b[index].toInt() and 0xff))
        } ?: 0
    }

    /**
     * Returns true when [maxChannelDelta] between [a] and [b] is at most 1
     * (one LSB).  This is the accepted tolerance for bilinear-filtering
     * comparisons against the physical colour oracle.
     */
    fun matchesWithinOneLsb(a: ByteArray, b: ByteArray): Boolean =
        maxChannelDelta(a, b) <= 1

    // ---- Physical colour oracle -----------------------------------------------

    /**
     * Computes the exact GPU colour pipeline for straight encoded sRGB upload
     * bytes, from texture upload through sampling, premultiplication, tint,
     * and sRGB store.
     *
     * The pipeline is:
     *   1. decode each texel from straight encoded sRGB → straight linear RGBA
     *   2. nearest or bilinear sample in straight linear space
     *   3. premultiply sampled RGB by sampled alpha
     *   4. apply one component-wise premultiplied tint/paint alpha
     *   5. encode back to sRGB for RGB, linear UNORM for alpha
     *   6. quantise to RGBA8
     *
     * [tintPremultipliedRgba] must be exactly 4 finite floats in [0,1]
     * with every RGB component ≤ alpha (premultiplied invariant).
     */
    fun sampleSrgbStraightToEncodedPremul(
        straightEncodedSrgb: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        sample: SampleKind,
        tintPremultipliedRgba: FloatArray,
    ): ByteArray {
        requireRawRgba(straightEncodedSrgb, width, height)
        requireFiniteUv(u, v)
        require(tintPremultipliedRgba.size == 4)
        require(tintPremultipliedRgba.all { it.isFinite() && it in 0f..1f })
        require(tintPremultipliedRgba[0] <= tintPremultipliedRgba[3])
        require(tintPremultipliedRgba[1] <= tintPremultipliedRgba[3])
        require(tintPremultipliedRgba[2] <= tintPremultipliedRgba[3])

        val sampled = when (sample) {
            SampleKind.NEAREST ->
                sampleSrgbNearest(straightEncodedSrgb, width, height, u, v)
            SampleKind.LINEAR ->
                sampleSrgbLinear(straightEncodedSrgb, width, height, u, v)
        }

        val alpha = sampled[3]
        val linearPremul = floatArrayOf(
            sampled[0] * alpha * tintPremultipliedRgba[0],
            sampled[1] * alpha * tintPremultipliedRgba[1],
            sampled[2] * alpha * tintPremultipliedRgba[2],
            alpha * tintPremultipliedRgba[3],
        )
        return byteArrayOf(
            quantizeUnorm(encodeSrgb(linearPremul[0].coerceIn(0f, 1f))),
            quantizeUnorm(encodeSrgb(linearPremul[1].coerceIn(0f, 1f))),
            quantizeUnorm(encodeSrgb(linearPremul[2].coerceIn(0f, 1f))),
            quantizeUnorm(linearPremul[3].coerceIn(0f, 1f)),
        )
    }

    // ---- Internal helpers -----------------------------------------------------
    // ---- sRGB transfer functions ----------------------------------------------

    private fun decodeSrgb(encoded: Float): Float =
        if (encoded <= 0.04045f) {
            encoded / 12.92f
        } else {
            ((encoded + 0.055f) / 1.055f).pow(2.4f)
        }

    private fun encodeSrgb(linear: Float): Float =
        if (linear <= 0.0031308f) {
            linear * 12.92f
        } else {
            1.055f * linear.pow(1f / 2.4f) - 0.055f
        }

    // ---- sRGB texel readers ----------------------------------------------------

    private fun readStraightLinearRgba(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
    ): FloatArray {
        val offset = (y * width + x) * 4
        return floatArrayOf(
            decodeSrgb((bytes[offset].toInt() and 0xff) / 255f),
            decodeSrgb((bytes[offset + 1].toInt() and 0xff) / 255f),
            decodeSrgb((bytes[offset + 2].toInt() and 0xff) / 255f),
            (bytes[offset + 3].toInt() and 0xff) / 255f,
        )
    }

    // ---- sRGB sampling ---------------------------------------------------------

    private fun sampleSrgbNearest(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): FloatArray {
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val x = floor(cu * width).toInt().coerceIn(0, width - 1)
        val y = floor(cv * height).toInt().coerceIn(0, height - 1)
        return readStraightLinearRgba(bytes, width, x, y)
    }

    private fun sampleSrgbLinear(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): FloatArray {
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val fx = cu * width - 0.5f
        val fy = cv * height - 0.5f
        val rawX0 = floor(fx).toInt()
        val rawY0 = floor(fy).toInt()
        val wx1 = fx - rawX0
        val wy1 = fy - rawY0
        val x0 = rawX0.coerceIn(0, width - 1)
        val y0 = rawY0.coerceIn(0, height - 1)
        val x1 = (rawX0 + 1).coerceIn(0, width - 1)
        val y1 = (rawY0 + 1).coerceIn(0, height - 1)
        val wx0 = 1f - wx1
        val wy0 = 1f - wy1

        val p00 = readStraightLinearRgba(bytes, width, x0, y0)
        val p10 = readStraightLinearRgba(bytes, width, x1, y0)
        val p01 = readStraightLinearRgba(bytes, width, x0, y1)
        val p11 = readStraightLinearRgba(bytes, width, x1, y1)

        return FloatArray(4) { c ->
            p00[c] * wx0 * wy0 +
                p10[c] * wx1 * wy0 +
                p01[c] * wx0 * wy1 +
                p11[c] * wx1 * wy1
        }
    }

    // ---- Quantisation ----------------------------------------------------------

    private fun quantizeUnorm(value: Float): Byte =
        (value * 255f).roundToInt().coerceIn(0, 255).toByte()

    // ---- Shared internal helpers -----------------------------------------------

    private fun requireRawRgba(bytes: ByteArray, width: Int, height: Int) {
        require(width > 0 && height > 0)
        require(bytes.size.toLong() == width.toLong() * height.toLong() * 4L)
    }

    private fun requireFiniteUv(u: Float, v: Float) {
        require(u.isFinite() && v.isFinite())
    }

    private fun readChannelFloats(bytes: ByteArray, width: Int, x: Int, y: Int): FloatArray {
        val offset = (y * width + x) * 4
        return FloatArray(4) { c -> bytes[offset + c].toInt().and(0xFF).toFloat() }
    }
}
