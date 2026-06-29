package org.graphiks.kanvas.codec

import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkData
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
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
 * result via [org.skia.foundation.SkPixmap.scalePixels] /
 * [org.skia.foundation.SkPixmap.extractSubset]. Upstream's "smart
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
        public val subset: SkIRect? = null,
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
    public fun getInfo(): SkImageInfo = codec.getInfo()

    /**
     * Mirrors `AndroidCodec::getICCProfile()`. The upstream returns a
     * raw `skcms_ICCProfile*` ; the Kotlin port surfaces the parsed
     * [SkColorSpace] from [getInfo] for ergonomic parity with the rest
     * of `:kanvas-skia` (callers that need the raw profile can reach
     * through `codec().getICCProfile()`).
     */
    public fun getICCProfile(): SkColorSpace? = codec.getInfo().colorSpace

    /** Mirrors `AndroidCodec::getEncodedFormat()`. */
    public fun getEncodedFormat(): SkEncodedImageFormat = codec.getEncodedFormat()

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
    public fun computeSampleSize(size: SkISize): Int {
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
            SkEncodedImageFormat.kJPEG -> when {
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
    public fun getSampledDimensions(sampleSize: Int): SkISize {
        val info = getInfo()
        if (sampleSize <= 1) return SkISize.Make(info.width, info.height)
        val w = maxOf(1, info.width / sampleSize)
        val h = maxOf(1, info.height / sampleSize)
        return SkISize.Make(w, h)
    }

    /**
     * Mirrors `bool AndroidCodec::getSupportedSubset(SkIRect* desiredSubset)`.
     *
     * Returns the largest supported subset contained in [desiredSubset],
     * or `null` if [desiredSubset] doesn't intersect the source bounds.
     *
     * **R3 simplification :** the codec can crop arbitrary axis-aligned
     * rects (the actual decode reads the full frame and a post-decode
     * subset is taken via [org.skia.foundation.SkPixmap.extractSubset]).
     * The returned rect is the input clamped to `[0, 0, w, h]`.
     */
    public fun getSupportedSubset(desiredSubset: SkIRect): SkIRect? {
        val info = getInfo()
        val srcBounds = SkIRect.MakeWH(info.width, info.height)
        val l = maxOf(desiredSubset.left, 0)
        val t = maxOf(desiredSubset.top, 0)
        val r = minOf(desiredSubset.right, srcBounds.right)
        val b = minOf(desiredSubset.bottom, srcBounds.bottom)
        if (l >= r || t >= b) return null
        return SkIRect.MakeLTRB(l, t, r, b)
    }

    /**
     * Mirrors `SkISize AndroidCodec::getSampledSubsetDimensions(int sampleSize, const SkIRect& subset)`.
     *
     * Returns the size of `subset` after integer-down division by
     * [sampleSize], clamped to `1` per axis. If [subset] is empty, the
     * returned size is `(1, 1)`.
     */
    public fun getSampledSubsetDimensions(sampleSize: Int, subset: SkIRect): SkISize {
        val s = maxOf(1, sampleSize)
        val w = maxOf(1, subset.width() / s)
        val h = maxOf(1, subset.height() / s)
        return SkISize.Make(w, h)
    }

    /**
     * Mirrors `Codec::Result AndroidCodec::getAndroidPixels(const SkImageInfo&, void*, size_t, const AndroidOptions*)`.
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
     *     [rowBytes] and the colour types kanvas-skia's [SkBitmap] knows
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
     *    the wire (`SkImageInfo::minRowBytes` accounts for this).
     *  - **kRGBA_F16Norm** : not supported on this path — Android never
     *    asks for F16 (see [AndroidCodec] kdoc). Returns
     *    [Codec.Result.kInvalidConversion].
     */
    public fun getAndroidPixels(
        info: SkImageInfo,
        pixels: ByteBuffer,
        rowBytes: Int,
        options: AndroidOptions = AndroidOptions(),
    ): Codec.Result {
        if (info.width <= 0 || info.height <= 0) return Codec.Result.kInvalidParameters
        if (rowBytes < info.minRowBytes()) return Codec.Result.kInvalidParameters
        if (options.sampleSize < 1) return Codec.Result.kInvalidParameters
        val bpp = info.bytesPerPixel()
        val requiredBytes = (info.height - 1).toLong() * rowBytes + info.width.toLong() * bpp
        if (pixels.limit().toLong() < requiredBytes) return Codec.Result.kInvalidParameters
        val srcInfo = codec.getInfo()
        if (srcInfo.width <= 0 || srcInfo.height <= 0) return Codec.Result.kInvalidInput

        // F16 isn't carried on this path — the Android pipeline never
        // requests it, and the byte-encoding contract above doesn't
        // cover the float layout.
        if (info.colorType == SkColorType.kRGBA_F16Norm) {
            return Codec.Result.kInvalidConversion
        }

        // 1) Clamp the requested subset to source bounds (matches
        //    upstream's "best-effort crop" — if the rect lies fully
        //    outside the source, that's kInvalidParameters).
        val subset = options.subset?.let { req ->
            val l = maxOf(req.left, 0)
            val t = maxOf(req.top, 0)
            val r = minOf(req.right, srcInfo.width)
            val b = minOf(req.bottom, srcInfo.height)
            if (l >= r || t >= b) return Codec.Result.kInvalidParameters
            SkIRect.MakeLTRB(l, t, r, b)
        } ?: SkIRect.MakeWH(srcInfo.width, srcInfo.height)

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
        val fullBitmap = SkBitmap(
            width = srcInfo.width,
            height = srcInfo.height,
            colorSpace = srcInfo.colorSpace,
            colorType = srcInfo.colorType,
        )
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
                val c = fullBitmap.getPixel(sx, sy)
                writePixelToBuffer(view, rowStart + dx * bpp, info.colorType, c)
            }
        }
        return Codec.Result.kSuccess
    }

    /**
     * Write a single [SkColor] value into [buf] at byte offset [off],
     * encoded for the wire-format dictated by [colorType]. Mirrors
     * the per-pixel `Store` lambda upstream's `SkSwizzler` would emit
     * for the same destination type.
     */
    private fun writePixelToBuffer(
        buf: ByteBuffer,
        off: Int,
        colorType: SkColorType,
        c: Int,
    ) {
        when (colorType) {
            SkColorType.kRGBA_8888 -> {
                // Wire order : R G B A.
                buf.put(off, SkColorGetR(c).toByte())
                buf.put(off + 1, SkColorGetG(c).toByte())
                buf.put(off + 2, SkColorGetB(c).toByte())
                buf.put(off + 3, SkColorGetA(c).toByte())
            }
            SkColorType.kBGRA_8888 -> {
                // Wire order : B G R A.
                buf.put(off, SkColorGetB(c).toByte())
                buf.put(off + 1, SkColorGetG(c).toByte())
                buf.put(off + 2, SkColorGetR(c).toByte())
                buf.put(off + 3, SkColorGetA(c).toByte())
            }
            SkColorType.kAlpha_8 -> buf.put(off, SkColorGetA(c).toByte())
            SkColorType.kGray_8 -> {
                // Rec.601 luminance — matches SkBitmap.setPixel's
                // quantisation for kGray_8.
                val r = SkColorGetR(c)
                val g = SkColorGetG(c)
                val b = SkColorGetB(c)
                val l = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                buf.put(off, l.toByte())
            }
            SkColorType.kRGB_565 -> {
                val r5 = (SkColorGetR(c) * 31 + 127) / 255
                val g6 = (SkColorGetG(c) * 63 + 127) / 255
                val b5 = (SkColorGetB(c) * 31 + 127) / 255
                val packed = ((r5 and 0x1F) shl 11) or ((g6 and 0x3F) shl 5) or (b5 and 0x1F)
                // LE on the wire.
                buf.put(off, (packed and 0xFF).toByte())
                buf.put(off + 1, ((packed ushr 8) and 0xFF).toByte())
            }
            SkColorType.kARGB_4444 -> {
                // Premul ARGB 4-bit-per-channel, packed RGBA in upstream's
                // `kARGB_4444_SkColorType` wire layout : R<<12 | G<<8 | B<<4 | A.
                val a = SkColorGetA(c) / 255f
                fun q(v: Int): Int = (((v / 255f) * a) * 15f + 0.5f).toInt().coerceIn(0, 15)
                val rN = q(SkColorGetR(c))
                val gN = q(SkColorGetG(c))
                val bN = q(SkColorGetB(c))
                val aN = (a * 15f + 0.5f).toInt().coerceIn(0, 15)
                val packed = (rN shl 12) or (gN shl 8) or (bN shl 4) or aN
                buf.put(off, (packed and 0xFF).toByte())
                buf.put(off + 1, ((packed ushr 8) and 0xFF).toByte())
            }
            else -> {
                // Unknown / unsupported colour types : no-op. Caller
                // already filtered F16 at the entry guard, so the only
                // way to land here is a future enum addition.
            }
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

        /** [SkData] overload — mirrors the `sk_sp<const SkData>` upstream factory. */
        public fun MakeFromData(data: SkData): AndroidCodec? = MakeFromData(data.toByteArray())
    }
}
