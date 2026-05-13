package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import java.io.OutputStream

/**
 * R2.20 surface mirror of upstream's
 * [`SkWebpEncoder`](https://github.com/google/skia/blob/main/include/encode/SkWebpEncoder.h).
 *
 * Sits beside [SkPngEncoder] / [SkJpegEncoder] in the
 * `org.skia.encode` package and follows the same shape : a Kotlin
 * `object` carrying the static `Encode` entry points and a
 * Kotlin-idiomatic [Options] data class mapped onto upstream's
 * `SkWebpEncoder::Options`.
 *
 * **Status — R2 stub** : the JVM has no built-in WebP encoder.
 * `javax.imageio` only ships WebP **decoder** support (registered
 * by the TwelveMonkeys `imageio-webp` artefact — see
 * `kanvas-skia/build.gradle.kts`), and bringing a libwebp-quality
 * encoder online means either binding to a native library or
 * porting the VP8 / VP8L bitstream encoder (several thousand lines
 * of bit-exact arithmetic). Neither is in scope for the R2 batch.
 *
 * Every [Encode] overload therefore returns `null`. The surface is
 * shipped so call sites that just want to *spell* the encoder
 * (e.g. `SkImage::encodeToData(format = kWEBP)`) compile against
 * a real type, and so a future encoder slice can fill in the body
 * without touching call-site code.
 *
 * Tracked as a follow-up under "R-suivi : WebP encoder body".
 */
public object SkWebpEncoder {

    /**
     * Mirror of `SkWebpEncoder::Compression`. Matches the upstream
     * libwebp dispatch — lossy (VP8) vs lossless (VP8L).
     */
    public enum class Compression {
        /** VP8 lossy compression. Default. */
        kLossy,

        /** VP8L lossless compression. */
        kLossless,
    }

    /**
     * Mirror of `SkWebpEncoder::Options`.
     *
     *  - [quality] is `0.0..100.0` ;
     *  - if [compression] is [Compression.kLossy], [quality] is the
     *    visual quality (lower → smaller file) ;
     *  - if [compression] is [Compression.kLossless], [quality] is
     *    the encoder effort (lower → faster, larger file).
     *
     * Both fields are accepted today but the encoder body is a stub —
     * see class kdoc.
     */
    public data class Options(
        val compression: Compression = Compression.kLossy,
        val quality: Float = 100.0f,
    ) {
        init {
            require(quality in 0.0f..100.0f) {
                "quality must be in [0.0, 100.0], got $quality"
            }
        }
    }

    private val defaultOptions = Options()

    /**
     * Encode [image]'s pixels into the WebP byte stream. R2 stub —
     * returns `null` unconditionally. Mirrors
     * `sk_sp<SkData> SkWebpEncoder::Encode(GrDirectContext*,
     *     const SkImage*, const Options&)`.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Encode(image: SkImage, options: Options = defaultOptions): ByteArray? = null

    /**
     * Encode [bitmap]'s pixels into the WebP byte stream. R2 stub —
     * returns `null` unconditionally. Mirrors
     * `sk_sp<SkData> SkWebpEncoder::Encode(const SkPixmap&,
     *     const Options&)`.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? = null

    /**
     * Encode [bitmap]'s pixels into [dst]. R2 stub — returns `false`
     * unconditionally. Mirrors `bool SkWebpEncoder::Encode(SkWStream*,
     *     const SkPixmap&, const Options&)`.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Encode(
        dst: OutputStream,
        bitmap: SkBitmap,
        options: Options = defaultOptions,
    ): Boolean = false
}
