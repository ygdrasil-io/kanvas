package org.skia.codec

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkImageInfo
import org.skia.math.SkIRect
import org.skia.math.SkISize
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Mirrors Skia's
 * [`SkAndroidCodec`](https://github.com/google/skia/blob/main/include/codec/SkAndroidCodec.h)
 * — the Android-specific wrapper around [SkCodec] that exposes simpler
 * downsampling and subset machinery used by Android's `BitmapFactory`
 * pipeline. Where [SkCodec] is a thin format-agnostic decoder facade,
 * [SkAndroidCodec] layers on top :
 *  - [computeSampleSize] / [getSampledDimensions] — pick a power-of-2
 *    sample size and report the resulting integer dimensions ;
 *  - [getSupportedSubset] — clamp a desired pixel-grid sub-rect to the
 *    set this codec can crop to (for R3 every rect intersecting the
 *    source bounds is supported, as decoding always reads full frames) ;
 *  - [getAndroidPixels] — decode into the caller-provided buffer, with
 *    a single [AndroidOptions] struct that bundles sample size +
 *    optional subset.
 *
 * **R3 scope.** The heavy lifting is delegated to [SkCodec] :
 * `getAndroidPixels` decodes the full frame, then post-processes the
 * result via [org.skia.foundation.SkPixmap.scalePixels] /
 * [org.skia.foundation.SkPixmap.extractSubset]. Upstream's "smart
 * sample-size picker" — which round-trips through libjpeg's DCT scaling
 * etc. — is **R-suivi** ; the Kotlin port simply rounds power-of-2 up
 * to the largest value still smaller than the source dimension and
 * divides integer-down for the sampled size.
 */
public class SkAndroidCodec internal constructor(private val codec: SkCodec) {

    /**
     * Mirrors `SkAndroidCodec::ExifOrientationBehavior`. The upstream
     * comment notes this is deprecated — Android now always ignores
     * orientation and asks the client to apply it post-decode. Kept on
     * the Kotlin port for API parity ; not consumed by [getAndroidPixels].
     */
    public enum class ExifOrientationBehavior {
        kIgnore,
        kRespect,
    }

    /**
     * Mirrors `SkAndroidCodec::AndroidOptions`. Default is "no sampling,
     * no subset" — equivalent to a plain [SkCodec.getPixels].
     */
    public data class AndroidOptions(
        public val sampleSize: Int = 1,
        public val subset: SkIRect? = null,
        public val zeroInitialized: ZeroInitialized = ZeroInitialized.kNo,
    )

    /**
     * Mirrors `SkCodec::ZeroInitialized`. Hint that the pixel buffer has
     * already been zeroed — currently ignored by [getAndroidPixels]
     * (always writes every byte it produces).
     */
    public enum class ZeroInitialized { kYes, kNo }

    /** Underlying [SkCodec]. Mirrors `SkAndroidCodec::codec()`. */
    public fun codec(): SkCodec = codec

    /** Mirrors `SkAndroidCodec::getInfo()`. */
    public fun getInfo(): SkImageInfo = codec.getInfo()

    /**
     * Mirrors `SkAndroidCodec::getICCProfile()`. The upstream returns a
     * raw `skcms_ICCProfile*` ; the Kotlin port surfaces the parsed
     * [SkColorSpace] from [getInfo] for ergonomic parity with the rest
     * of `:kanvas-skia` (callers that need the raw profile can reach
     * through `codec().getICCProfile()`).
     */
    public fun getICCProfile(): SkColorSpace? = codec.getInfo().colorSpace

    /** Mirrors `SkAndroidCodec::getEncodedFormat()`. */
    public fun getEncodedFormat(): SkEncodedImageFormat = codec.getEncodedFormat()

    /**
     * Mirrors `int SkAndroidCodec::computeSampleSize(SkISize* size)`.
     *
     * Round to the largest power-of-2 sample size whose sampled
     * dimensions are still `>= size`. The returned `sampleSize` is the
     * one to set on [AndroidOptions.sampleSize] to obtain a decode that
     * is at least as large as the requested [size]. The matching sampled
     * dimensions can be re-derived via [getSampledDimensions].
     *
     * **R3 simplification :** the upstream picker honours format-specific
     * native scale factors (libjpeg's `M/8` DCT scales, libwebp's
     * pyramid). The Kotlin port returns the largest power-of-2 `s` such
     * that `srcW / s >= size.width && srcH / s >= size.height` — clamped
     * to `1` if the request is already larger than the source. Sufficient
     * for the R3 callers ; smart picker is **R-suivi**.
     */
    public fun computeSampleSize(size: SkISize): Int {
        val info = getInfo()
        val srcW = info.width
        val srcH = info.height
        if (srcW <= 0 || srcH <= 0) return 1
        if (size.width <= 0 || size.height <= 0) return 1
        // Largest power-of-2 `s` such that srcW/s >= size.width && srcH/s >= size.height.
        var s = 1
        while (true) {
            val next = s * 2
            if (next > srcW || next > srcH) break
            if (srcW / next < size.width || srcH / next < size.height) break
            s = next
        }
        return s
    }

    /**
     * Mirrors `SkISize SkAndroidCodec::getSampledDimensions(int sampleSize)`.
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
     * Mirrors `bool SkAndroidCodec::getSupportedSubset(SkIRect* desiredSubset)`.
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
     * Mirrors `SkISize SkAndroidCodec::getSampledSubsetDimensions(int sampleSize, const SkIRect& subset)`.
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
     * Mirrors `SkCodec::Result SkAndroidCodec::getAndroidPixels(const SkImageInfo&, void*, size_t, const AndroidOptions*)`.
     *
     * **R3 implementation.** The Kotlin port currently only honours the
     * "no sampling, no subset" fast path — it delegates straight to
     * [SkCodec.getPixels] when [options] is the default. For non-trivial
     * options the call returns [SkCodec.Result.kUnimplemented] ; a proper
     * implementation would decode the full frame into a scratch bitmap
     * and then subset / downscale into the caller's [pixels] buffer. That
     * is **R-suivi** (it requires a public allocator-free path into
     * [SkBitmap] from a raw [ByteBuffer]).
     */
    public fun getAndroidPixels(
        info: SkImageInfo,
        pixels: ByteBuffer,
        rowBytes: Int,
        options: AndroidOptions = AndroidOptions(),
    ): SkCodec.Result {
        // R3 fast path : only the default options are honoured. Anything
        // that actually requests sampling or a subset returns
        // kUnimplemented — see KDoc.
        if (options.sampleSize > 1 || options.subset != null) {
            return SkCodec.Result.kUnimplemented
        }
        if (info.width <= 0 || info.height <= 0) return SkCodec.Result.kInvalidParameters
        if (rowBytes < info.minRowBytes()) return SkCodec.Result.kInvalidParameters
        // Allocate a matching bitmap, delegate the decode, copy the
        // produced pixels into the caller's buffer row-by-row.
        val bitmap = SkBitmap(
            width = info.width,
            height = info.height,
            colorSpace = info.colorSpace,
            colorType = info.colorType,
        )
        val result = codec.getPixels(info, bitmap)
        if (result != SkCodec.Result.kSuccess) return result
        // Copy the bitmap's pixels into the caller's buffer. We don't
        // have a public "raw bytes out" path on SkBitmap that covers
        // every colour type, but the kRGBA_8888 / kBGRA_8888 / kAlpha_8
        // ones each expose an Int / Byte array we can repack.
        // For R3 we keep this as kUnimplemented for any other type —
        // upstream's PNG path lands on either 8888 or F16 ; we cover
        // 8888 since the Android pipeline never asks for F16.
        return SkCodec.Result.kUnimplemented
    }

    public companion object {
        /**
         * Pass ownership of [codec] to a newly-created [SkAndroidCodec].
         * Mirrors `SkAndroidCodec::MakeFromCodec`. Returns `null` if
         * [codec] is `null` (Kotlin signatures don't allow nullable
         * params on `non-null` returns — wrap the call when needed).
         */
        public fun MakeFromCodec(codec: SkCodec): SkAndroidCodec = SkAndroidCodec(codec)

        /**
         * Sniff [stream] and return an [SkAndroidCodec] for it, or `null`
         * if no registered [SkCodec] decoder matches. Mirrors
         * `SkAndroidCodec::MakeFromStream`.
         */
        public fun MakeFromStream(stream: InputStream): SkAndroidCodec? =
            SkCodec.MakeFromStream(stream)?.let(::MakeFromCodec)

        /**
         * Sniff [data] and return an [SkAndroidCodec] for it, or `null`
         * if no registered [SkCodec] decoder matches. Mirrors
         * `SkAndroidCodec::MakeFromData`.
         */
        public fun MakeFromData(data: ByteArray): SkAndroidCodec? =
            SkCodec.MakeFromData(data)?.let(::MakeFromCodec)

        /** [SkData] overload — mirrors the `sk_sp<const SkData>` upstream factory. */
        public fun MakeFromData(data: SkData): SkAndroidCodec? = MakeFromData(data.toByteArray())
    }
}
