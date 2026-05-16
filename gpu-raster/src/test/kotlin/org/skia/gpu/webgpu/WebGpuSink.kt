package org.skia.gpu.webgpu

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.tests.GM
import kotlin.math.pow

/**
 * GPU equivalent of [org.skia.dm.RasterSinkF16] — runs a [GM] through
 * an [SkWebGpuDevice] sized to the GM's preferred size, then converts
 * the raw RGBA readback into an [SkBitmap] suitable for
 * [org.skia.testing.TestUtils.compareBitmapsDetailed] against the
 * reference PNG in `original-888/`.
 *
 * **G6.0 colorspace post-process.** The reference PNGs in
 * `original-888/` are encoded in **DM unified Rec.2020** (Rec.2020
 * primaries + Rec.2020 transfer function ; see
 * `TestUtils.DM_REFERENCE_COLOR_SPACE`). The GPU device renders into
 * a plain `RGBA8Unorm` texture whose bytes are sRGB-encoded sRGB
 * primaries. To align the comparison with the reference, this sink
 * applies a sRGB-encoded → linear-sRGB → linear-Rec.2020 →
 * Rec.2020-encoded byte transform per pixel before returning the
 * bitmap. Cross-tests bump as a result ; unit tests bypass this sink
 * and continue to compare raw GPU bytes.
 *
 * The full G6 plan moves this transform into the GPU pipeline
 * (F16 linear-Rec.2020 working space + present-pass encoding) ;
 * G6.0 is a CPU-side probe to validate the hypothesis cheaply.
 *
 * **Premul vs non-premul.** The GPU readback bytes are premultiplied
 * (consequence of the premul fragment output + SrcOver pipeline, see
 * G2.1). For GMs that only use opaque source colours (every cross-test
 * GM today), `premul == non-premul` byte-for-byte. GMs that paint
 * translucent sources will need a divide-by-alpha pre-step ; deferred
 * until a translucent-source GM enters the ratchet.
 */
public object WebGpuSink {

    /**
     * Render [gm] through an [SkWebGpuDevice] backed by [context],
     * then apply the sRGB → Rec.2020 colorspace transform (G6.0) and
     * return the resulting bitmap in `kRGBA_8888`.
     */
    public fun draw(context: WebGpuContext, gm: GM): SkBitmap {
        val size = gm.size()
        val w = size.width
        val h = size.height
        SkWebGpuDevice(context, w, h).use { device ->
            device.setBackground(gm.bgColor())
            val canvas = SkCanvas(device)
            gm.draw(canvas)
            val rgba = device.flush()
            return rgbaBytesToRec2020Bitmap(rgba, w, h)
        }
    }

    /**
     * Pack a row-major RGBA byte stream (the output of
     * [SkWebGpuDevice.flush]) into an [SkBitmap.pixels8888] ARGB int
     * array, applying the sRGB → Rec.2020 colorspace transform on the
     * way.
     *
     * Transform pipeline per pixel :
     *  1. byte / 255 → sRGB-encoded float
     *  2. sRGB transfer inverse (gamma decode) → linear sRGB
     *  3. 3×3 primaries matrix → linear Rec.2020
     *  4. Rec.2020 transfer (BT.2020 OETF) → Rec.2020-encoded float
     *  5. × 255 → byte
     *
     * Layout convention :
     * - input bytes : `R, G, B, A` per pixel
     * - output ints : `0xAARRGGBB` per pixel
     */
    private fun rgbaBytesToRec2020Bitmap(rgba: ByteArray, w: Int, h: Int): SkBitmap {
        require(rgba.size == w * h * 4) {
            "RGBA buffer size mismatch : expected ${w * h * 4} bytes for $w x $h, got ${rgba.size}"
        }
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until w * h) {
            val base = i * 4
            val rIn = (rgba[base].toInt() and 0xFF) / 255f
            val gIn = (rgba[base + 1].toInt() and 0xFF) / 255f
            val bIn = (rgba[base + 2].toInt() and 0xFF) / 255f
            val aIn = rgba[base + 3].toInt() and 0xFF

            // sRGB transfer inverse on each channel -> linear sRGB
            val rLin = srgbToLinear(rIn)
            val gLin = srgbToLinear(gIn)
            val bLin = srgbToLinear(bIn)

            // 3x3 sRGB primaries -> Rec.2020 primaries (in linear)
            val rRec = SRGB_TO_REC2020[0] * rLin + SRGB_TO_REC2020[1] * gLin + SRGB_TO_REC2020[2] * bLin
            val gRec = SRGB_TO_REC2020[3] * rLin + SRGB_TO_REC2020[4] * gLin + SRGB_TO_REC2020[5] * bLin
            val bRec = SRGB_TO_REC2020[6] * rLin + SRGB_TO_REC2020[7] * gLin + SRGB_TO_REC2020[8] * bLin

            // Rec.2020 OETF (forward transfer) -> encoded byte
            val rOut = (rec2020Encode(rRec).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val gOut = (rec2020Encode(gRec).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val bOut = (rec2020Encode(bRec).coerceIn(0f, 1f) * 255f + 0.5f).toInt()

            bitmap.pixels8888[i] = (aIn shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
        }
        return bitmap
    }

    /** sRGB transfer inverse : encoded byte / 255 → linear. */
    private fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).pow(2.4f)

    /**
     * BT.2020 OETF (encoder). Skia's `kRec2020` transfer function.
     *   V = 1.0993 * L^0.45 - 0.0993   for L >= 0.0181
     *   V = 4.5 * L                    for L < 0.0181
     */
    private fun rec2020Encode(c: Float): Float {
        val clamped = c.coerceAtLeast(0f)
        return if (clamped < 0.0181f) 4.5f * clamped
        else 1.0993f * clamped.pow(0.45f) - 0.0993f
    }

    /**
     * Linear-RGB primary transform : sRGB primaries → Rec.2020 primaries.
     * Standard ITU-R BT.2020 matrix derived from the inverse of the
     * Rec.2020 RGB-to-XYZ matrix composed with the sRGB XYZ-to-RGB
     * matrix. Row-major flat array.
     */
    private val SRGB_TO_REC2020 = floatArrayOf(
        0.62740f, 0.32928f, 0.04338f,
        0.06909f, 0.91954f, 0.01136f,
        0.01639f, 0.08801f, 0.89559f,
    )
}
