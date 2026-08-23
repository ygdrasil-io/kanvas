package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.kanvas.color.icc.IccProfile
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors Skia's
 * [`AndroidCodec`](https://github.com/google/skia/blob/main/include/codec/AndroidCodec.h)
 * — the Android-specific wrapper around [Codec] that exposes simpler
 * downsampling and subset machinery used by Android's `BitmapFactory`
 * pipeline. Where [Codec] is a thin format-agnostic decoder facade,
 * [AndroidCodec] layers on top :
 *  - [computeSampleSize] / [getSampledDimensions] — pick a power-of-2
 *    sample size and report the resulting integer dimensions ;
 *  - [getSupportedSubset] — clamp a desired pixel-grid sub-rect to the
 *    set this codec can crop to (for R3 every rect intersecting the
 *    source bounds is supported, as decoding always reads full frames) ;
 *  - [getAndroidPixels] — decode into the caller-provided buffer, with
 *    a single [AndroidOptions] struct that bundles sample size +
 *    optional subset.
 *
 * **R3 scope.** The heavy lifting is delegated to [Codec] :
 * `getAndroidPixels` decodes the full frame, then post-processes the
 * result through explicit Kanvas [Bitmap] pixel access. Upstream's "smart
 * sample-size picker" — which round-trips through libjpeg's DCT scaling
 * etc. — is **R-suivi** ; the Kotlin port simply rounds power-of-2 up
 * to the largest value still smaller than the source dimension and
 * divides integer-down for the sampled size.
 */
public class AndroidCodec internal constructor(private val codec: Codec) {

    /**
     * Mirrors `AndroidCodec::ExifOrientationBehavior`. The upstream
     * comment notes this is deprecated — Android now always ignores
     * orientation and asks the client to apply it post-decode. Kept on
     * the Kotlin port for API parity ; not consumed by [getAndroidPixels].
     */
    public enum class ExifOrientationBehavior {
        kIgnore,
        kRespect,
    }

    /**
     * Mirrors `AndroidCodec::AndroidOptions`. Default is "no sampling,
     * no subset" — equivalent to a plain [Codec.getPixels].
     */
    public data class AndroidOptions(
        public val sampleSize: Int = 1,
        public val subset: RectI32? = null,
        public val zeroInitialized: ZeroInitialized = ZeroInitialized.kNo,
    )

    /**
     * Mirrors `Codec::ZeroInitialized`. Hint that the pixel buffer has
     * already been zeroed — currently ignored by [getAndroidPixels]
     * (always writes every byte it produces).
     */
    public enum class ZeroInitialized { kYes, kNo }

    /** Underlying [Codec]. Mirrors `AndroidCodec::codec()`. */
    public fun codec(): Codec = codec

    /** Mirrors `AndroidCodec::getInfo()`. */
    public fun getInfo(): ImageInfo = codec.getInfo()

    /**
     * Mirrors `AndroidCodec::getICCProfile()`. The upstream returns a
     * raw `skcms_ICCProfile*`; the Kotlin port returns immutable embedded ICC
     * provenance directly.
     */
    public fun getICCProfile(): IccProfile? = codec.getICCProfile()

    /** Mirrors `AndroidCodec::getEncodedFormat()`. */
    public fun getEncodedFormat(): EncodedImageFormat = codec.getEncodedFormat()

    /**
     * Mirrors `int AndroidCodec::computeSampleSize(SkISize* size)`.
     *
     * Round to the largest power-of-2 sample size whose sampled
     * dimensions are still `>= size`. The returned `sampleSize` is the
     * one to set on [AndroidOptions.sampleSize] to obtain a decode that
     * is at least as large as the requested [size]. The matching sampled
     * dimensions can be re-derived via [getSampledDimensions].
     *
     * **R-suivi.35 — format-aware picker.** Upstream honours format-specific
     * native scale factors so the codec can avoid post-decode downsampling
     * when the encoder supports it natively :
     *  - **JPEG** : libjpeg's DCT can scale by `M/8` for `M ∈ {1, 2, 4,
     *    8}`, so the picker clamps the answer to one of `{1, 2, 4, 8}`.
     *    Anything beyond `8` is also clamped to `8` (the maximum
     *    libjpeg-native shrink ; post-decode resampling handles the rest).
     *  - **WEBP** : the encoder writes a fixed image but `libwebp` exposes
     *    arbitrary integer downscale via the config's `scaled_width`
     *    field. We still report a power-of-2 since [getSampledDimensions]
     *    is the consumer ; smaller pow-2 == bigger output (less shrink).
     *  - **PNG / GIF / BMP / WBMP / others** : no native scaling, return
     *    the largest power-of-2 that satisfies the size constraint
     *    (matches the pre-R-suivi.35 behaviour).
     *
     * The fall-back is the same algorithm as before : the largest
     * power-of-2 `s` such that `srcW / s >= size.width && srcH / s >=
     * size.height`, clamped to `1` if the request is already larger than
     * the source.
     */
    public fun computeSampleSize(size: SizeI32): Int {
        val info = getInfo()
        val srcW = info.width
        val srcH = info.height
        if (srcW <= 0 || srcH <= 0) return 1
        if (size.width <= 0 || size.height <= 0) return 1

        // Generic power-of-2 pick first — the JPEG branch may further
        // clamp the result down to {1, 2, 4, 8}.
        var s = 1
        while (true) {
            val next = s * 2
            if (next > srcW || next > srcH) break
            if (srcW / next < size.width || srcH / next < size.height) break
            s = next
        }

        return when (getEncodedFormat()) {
            // libjpeg native DCT scales : M/8 for M ∈ {1, 2, 4, 8}. We
            // clamp the generic pow-2 pick to that set ; values > 8 are
            // capped at 8 (post-decode resampling can take it the rest
            // of the way down for callers using getAndroidPixels).
            EncodedImageFormat.JPEG -> when {
                s >= 8 -> 8
                s >= 4 -> 4
                s >= 2 -> 2
                else -> 1
            }
            // Every other format : generic power-of-2 picker.
            else -> s
        }
    }

    /**
     * Mirrors `SkISize AndroidCodec::getSampledDimensions(int sampleSize)`.
     *
     * Integer-down division, clamped to a minimum of `1` per axis (the
     * upstream contract is "always recommend a non-zero output"). When
     * [sampleSize] is `<= 1` the source dimensions are returned as-is.
     */
    public fun getSampledDimensions(sampleSize: Int): SizeI32 {
        val info = getInfo()
        if (sampleSize <= 1) return SizeI32.of(info.width, info.height)
        val w = maxOf(1, info.width / sampleSize)
        val h = maxOf(1, info.height / sampleSize)
        return SizeI32.of(w, h)
    }

    /**
     * Mirrors `bool AndroidCodec::getSupportedSubset(SkIRect* desiredSubset)`.
     *
     * Returns the largest supported subset contained in [desiredSubset],
     * or `null` if [desiredSubset] doesn't intersect the source bounds.
     *
     * **R3 simplification :** the codec can crop arbitrary axis-aligned
     * rects (the actual decode reads the full frame and a post-decode
     * subset is taken from the decoded Kanvas bitmap).
     * The returned rect is the input clamped to `[0, 0, w, h]`.
     */
    public fun getSupportedSubset(desiredSubset: RectI32): RectI32? {
        val info = getInfo()
        val srcBounds = RectI32.ofSize(info.width, info.height)
        val l = maxOf(desiredSubset.left, 0)
        val t = maxOf(desiredSubset.top, 0)
        val r = minOf(desiredSubset.right, srcBounds.right)
        val b = minOf(desiredSubset.bottom, srcBounds.bottom)
        if (l >= r || t >= b) return null
        return RectI32.ofLTRB(l, t, r, b)
    }

    /**
     * Mirrors `SkISize AndroidCodec::getSampledSubsetDimensions(int sampleSize, const SkIRect& subset)`.
     *
     * Returns the size of `subset` after integer-down division by
     * [sampleSize], clamped to `1` per axis. If [subset] is empty, the
     * returned size is `(1, 1)`.
     */
    public fun getSampledSubsetDimensions(sampleSize: Int, subset: RectI32): SizeI32 {
        val s = maxOf(1, sampleSize)
        val w = maxOf(1, subset.width() / s)
        val h = maxOf(1, subset.height() / s)
        return SizeI32.of(w, h)
    }

    /**
     * Mirrors `Codec::Result AndroidCodec::getAndroidPixels(const ImageInfo&, void*, size_t, const AndroidOptions*)`.
     *
     * **R-suivi.34 implementation.** Decodes the full frame via the
     * wrapped [Codec.getPixels], then post-processes :
     *  1. **Subset** ([AndroidOptions.subset] non-`null`) — crop the
     *     decoded frame to the requested rect (clamped to source bounds).
     *  2. **Downsample** ([AndroidOptions.sampleSize] > 1) — pick every
     *     `sampleSize`-th pixel along each axis (nearest-neighbour). The
     *     output dimensions match [getSampledSubsetDimensions] (or
     *     [getSampledDimensions] when no subset is set).
     *  3. **Write** to the caller's [pixels] [ByteBuffer], honouring
     *     [rowBytes] and the colour types Kanvas' [Bitmap] knows
     *     how to read (8888 / BGRA / 565 / 4444 / Alpha-8 / Gray-8).
     *
     * The caller's [info] must match the **post-sampling** size : its
     * `width / height` is what the produced bitmap would have been if
     * decoded through [getSampledSubsetDimensions]. If they don't match,
     * the result is [Codec.Result.kInvalidParameters].
     *
     * Pixel format on the wire :
     *  - **kRGBA_8888 / kBGRA_8888** : 4 bytes per pixel in `R G B A` (or
     *    `B G R A`) order. Matches the upstream contract — the encoded
     *    byte order is **format-specific**, not the host-endian packed
     *    Int we keep internally.
     *  - **kAlpha_8 / kGray_8** : 1 byte per pixel.
     *  - **kRGB_565 / kARGB_4444** : 2 bytes per pixel, little-endian on
     *    the wire (`ImageInfo.minRowBytes` accounts for this).
     *  - **kRGBA_F16Norm** : not supported on this path — Android never
     *    asks for F16 (see [AndroidCodec] kdoc). Returns
     *    [Codec.Result.kInvalidConversion].
     */
    public fun getAndroidPixels(
        info: ImageInfo,
        pixels: ByteBuffer,
        rowBytes: Int,
        options: AndroidOptions = AndroidOptions(),
    ): Codec.Result {
        if (info.width <= 0 || info.height <= 0) return Codec.Result.kInvalidParameters
        if (rowBytes.toLong() < info.minRowBytesLong()) return Codec.Result.kInvalidParameters
        if (options.sampleSize < 1) return Codec.Result.kInvalidParameters
        val bpp = info.bytesPerPixel()
        val requiredBytes = info.computeByteSizeOrNull(rowBytes.toLong())
            ?: return Codec.Result.kInvalidParameters
        if (pixels.limit().toLong() < requiredBytes) return Codec.Result.kInvalidParameters
        val srcInfo = codec.getInfo()
        if (srcInfo.width <= 0 || srcInfo.height <= 0) return Codec.Result.kInvalidInput

        // F16 isn't carried on this path — the Android pipeline never
        // requests it, and the byte-encoding contract above doesn't
        // cover the float layout.
        if (info.colorType == ColorType.RGBA_F16 || info.colorType == ColorType.RGBA_F16_NORM) {
            return Codec.Result.kInvalidConversion
        }
        if (info.colorType !in SUPPORTED_WIRE_COLOR_TYPES) return Codec.Result.kInvalidConversion

        // 1) Clamp the requested subset to source bounds (matches
        //    upstream's "best-effort crop" — if the rect lies fully
        //    outside the source, that's kInvalidParameters).
        val subset = options.subset?.let { req ->
            val l = maxOf(req.left, 0)
            val t = maxOf(req.top, 0)
            val r = minOf(req.right, srcInfo.width)
            val b = minOf(req.bottom, srcInfo.height)
            if (l >= r || t >= b) return Codec.Result.kInvalidParameters
            RectI32.ofLTRB(l, t, r, b)
        } ?: RectI32.ofSize(srcInfo.width, srcInfo.height)

        // 2) Verify the caller's `info` dimensions match the
        //    post-sample-size output. Upstream surfaces a mismatch as
        //    kInvalidParameters too.
        val s = options.sampleSize
        val expectedW = maxOf(1, subset.width() / s)
        val expectedH = maxOf(1, subset.height() / s)
        if (info.width != expectedW || info.height != expectedH) {
            return Codec.Result.kInvalidParameters
        }

        // 3) Decode the full source frame into a scratch bitmap. The
        //    decode happens at the source's natural colour type — we
        //    only convert at write-back time so the codec can pick its
        //    fast path.
        val fullBitmap = Bitmap(srcInfo)
        val decodeResult = codec.getPixels(srcInfo, fullBitmap)
        if (decodeResult != Codec.Result.kSuccess) return decodeResult

        // 4) Walk the destination grid, mapping each `(dx, dy)` to its
        //    source coordinate `(subset.left + dx * s, subset.top + dy
        //    * s)` and writing the colour-type-converted byte sequence
        //    to the caller's buffer.
        val view = pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        for (dy in 0 until info.height) {
            val sy = subset.top + dy * s
            val rowStart = dy * rowBytes
            for (dx in 0 until info.width) {
                val sx = subset.left + dx * s
                val c = fullBitmap.getArgb(sx, sy)
                writePixelToBuffer(view, rowStart + dx * bpp, info.colorType, c)
            }
        }
        return Codec.Result.kSuccess
    }

    /**
     * Write a single ARGB value into [buf] at byte offset [off],
     * encoded for the wire-format dictated by [colorType]. Mirrors
     * the per-pixel `Store` lambda upstream's `SkSwizzler` would emit
     * for the same destination type.
     */
    private fun writePixelToBuffer(
        buf: ByteBuffer,
        off: Int,
        colorType: ColorType,
        c: Int,
    ) {
        val color = ColorARGB.fromPackedInt(c)
        when (colorType) {
            ColorType.RGBA_8888 -> {
                // Wire order : R G B A.
                buf.put(off, color.red.toByte())
                buf.put(off + 1, color.green.toByte())
                buf.put(off + 2, color.blue.toByte())
                buf.put(off + 3, color.alpha.toByte())
            }
            ColorType.BGRA_8888 -> {
                // Wire order : B G R A.
                buf.put(off, color.blue.toByte())
                buf.put(off + 1, color.green.toByte())
                buf.put(off + 2, color.red.toByte())
                buf.put(off + 3, color.alpha.toByte())
            }
            ColorType.ALPHA_8 -> buf.put(off, color.alpha.toByte())
            ColorType.GRAY_8 -> {
                // Rec.601 luminance — matches Kanvas Bitmap quantisation.
                // quantisation for kGray_8.
                val r = color.red
                val g = color.green
                val b = color.blue
                val l = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                buf.put(off, l.toByte())
            }
            ColorType.RGB_565 -> {
                val r5 = (color.red * 31 + 127) / 255
                val g6 = (color.green * 63 + 127) / 255
                val b5 = (color.blue * 31 + 127) / 255
                val packed = ((r5 and 0x1F) shl 11) or ((g6 and 0x3F) shl 5) or (b5 and 0x1F)
                // LE on the wire.
                buf.put(off, (packed and 0xFF).toByte())
                buf.put(off + 1, ((packed ushr 8) and 0xFF).toByte())
            }
            ColorType.ARGB_4444 -> {
                // Premul ARGB 4-bit-per-channel, packed A R G B in the
                // canonical ARGB_4444 wire layout: A<<12 | R<<8 | G<<4 | B.
                val a = color.alpha / 255f
                fun q(v: Int): Int = (((v / 255f) * a) * 15f + 0.5f).toInt().coerceIn(0, 15)
                val rN = q(color.red)
                val gN = q(color.green)
                val bN = q(color.blue)
                val aN = (a * 15f + 0.5f).toInt().coerceIn(0, 15)
                val packed = (aN shl 12) or (rN shl 8) or (gN shl 4) or bN
                buf.put(off, (packed and 0xFF).toByte())
                buf.put(off + 1, ((packed ushr 8) and 0xFF).toByte())
            }
            else -> error("unsupported Android wire color type: $colorType")
        }
    }

    public companion object {
        /**
         * Pass ownership of [codec] to a newly-created [AndroidCodec].
         * Mirrors `AndroidCodec::MakeFromCodec`. Returns `null` if
         * [codec] is `null` (Kotlin signatures don't allow nullable
         * params on `non-null` returns — wrap the call when needed).
         */
        public fun MakeFromCodec(codec: Codec): AndroidCodec = AndroidCodec(codec)

        /**
         * Sniff [stream] and return an [AndroidCodec] for it, or `null`
         * if no registered [Codec] decoder matches. Mirrors
         * `AndroidCodec::MakeFromStream`.
         */
        public fun MakeFromStream(stream: InputStream): AndroidCodec? =
            Codec.MakeFromStream(stream)?.let(::MakeFromCodec)

        /**
         * Sniff [data] and return an [AndroidCodec] for it, or `null`
         * if no registered [Codec] decoder matches. Mirrors
         * `AndroidCodec::MakeFromData`.
         */
        public fun MakeFromData(data: ByteArray): AndroidCodec? =
            Codec.MakeFromData(data)?.let(::MakeFromCodec)

        private val SUPPORTED_WIRE_COLOR_TYPES = setOf(
            ColorType.RGBA_8888,
            ColorType.BGRA_8888,
            ColorType.ALPHA_8,
            ColorType.GRAY_8,
            ColorType.RGB_565,
            ColorType.ARGB_4444,
        )
    }
}
