package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CPU-side pixel oracles for prepared-image correctness tests.
 *
 * Every function is a pure-Kotlin reference: no GPU, no WGSL, no shader helpers.
 * The implementations match the WebGPU texture-sampling behaviour that the
 * WGSL shader and upload pipeline are expected to produce under the colour
 * contract:
 *
 * - Source colour : [org.graphiks.kanvas.surface.GPUColorFormat.RGBA8_UNORM_SRGB]
 * - Source A8/coverage : RGBA8Unorm
 * - Upload colour : StraightEncodedSrgb
 * - Shader : LinearPremul
 * - Output logic : EncodedPremulSrgb
 */
object GPUPreparedImagePixelOracle {

    enum class SampleKind { NEAREST, LINEAR }

    // ---- Sampling oracles ----------------------------------------------------

    /**
     * Nearest-neighbour sample from a raw RGBA byte buffer.
     *
     * UV coordinates are in the range [0, 1] where (0,0) is top-left and
     * (1,1) is bottom-right.  Values outside [0, 1] are clamped.
     */
    fun nearestSample(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): ByteArray {
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val x = (cu * (width - 1) + 0.5f).toInt().coerceIn(0, width - 1)
        val y = (cv * (height - 1) + 0.5f).toInt().coerceIn(0, height - 1)
        val offset = (y * width + x) * 4
        return bytes.copyOfRange(offset, offset + 4)
    }

    /**
     * Bilinear sample from a raw RGBA byte buffer.
     *
     * Linearly interpolates the four nearest texels. UV clamping is the same
     * as [nearestSample].
     */
    fun linearSample(
        bytes: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): ByteArray {
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val fx = cu * (width - 1)
        val fy = cv * (height - 1)
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)
        val wx1 = fx - x0
        val wy1 = fy - y0
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
     * to that rect, then remapped back to full-image UV, then passed to the
     * chosen [sample] kind.
     */
    fun sourceRectSample(
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
        val cu = u.coerceIn(srcL, srcR)
        val cv = v.coerceIn(srcT, srcB)
        val imageU = (cu - srcL) / (srcR - srcL)
        val imageV = (cv - srcT) / (srcB - srcT)
        return when (sample) {
            SampleKind.NEAREST -> nearestSample(bytes, width, height, imageU, imageV)
            SampleKind.LINEAR -> linearSample(bytes, width, height, imageU, imageV)
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
    fun applyTint(
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
    fun exactMatch(a: ByteArray, b: ByteArray): Boolean = a.contentEquals(b)

    /**
     * Returns true when the maximum per-channel delta between [a] and [b]
     * is at most 1 (one LSB).  This is the accepted tolerance for bilinear
     * filtering comparisons.
     */
    fun linearMatch(a: ByteArray, b: ByteArray): Boolean =
        a.indices.all { i -> abs(a[i].toInt().and(0xFF) - b[i].toInt().and(0xFF)) <= 1 }

    // ---- Internal helpers -----------------------------------------------------

    private fun readChannelFloats(bytes: ByteArray, width: Int, x: Int, y: Int): FloatArray {
        val offset = (y * width + x) * 4
        return FloatArray(4) { c -> bytes[offset + c].toInt().and(0xFF).toFloat() }
    }
}
