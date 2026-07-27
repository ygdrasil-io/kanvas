package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

    // ---- Internal helpers -----------------------------------------------------

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
