package org.skia.codec

import org.skia.foundation.SkImage
import org.skia.foundation.stream.SkStream

/**
 * R-final.S **STUB.FFMPEG** — surface stub for upstream's
 * `SkVideoDecoder` (`modules/skvideo/include/SkVideoDecoder.h`). The
 * real implementation requires FFmpeg's `libavformat` /
 * `libavcodec` for container demuxing + frame decoding, which lives
 * outside a pure-JVM port.
 *
 * This stub exists so call sites in ported GMs (for example
 * `video_decoder`) compile and reference the documented public
 * surface; **every runtime entry-point throws [NotImplementedError]** with the
 * `STUB.FFMPEG` tag so the technical debt is visible the moment a
 * test attempts to exercise it. Consumers that need a working video
 * decoder must register their own JNI binding behind this surface
 * (e.g. via JavaCV / JNAerator on top of FFmpeg) — that work is out
 * of scope for `:kanvas-skia` itself.
 */
@Suppress("UNUSED_PARAMETER")
public object SkVideoDecoder {

    /**
     * Mirrors `SkVideoDecoder::MakeFromStream(std::unique_ptr<SkStream>)`.
     * Always throws [NotImplementedError] — see the class doc.
     */
    @Suppress("FunctionName")
    public fun MakeFromStream(stream: SkStream): SkVideoDecoderInstance =
        throw NotImplementedError(
            "STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI — " +
                "outside the portable codec matrix.",
        )

    /**
     * Mirrors `SkVideoDecoder::MakeFromData(sk_sp<SkData>)` — same
     * fate as [MakeFromStream].
     */
    @Suppress("FunctionName")
    public fun MakeFromData(bytes: ByteArray): SkVideoDecoderInstance =
        throw NotImplementedError(
            "STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI — " +
                "outside the portable codec matrix.",
        )
}

/**
 * Instance handle returned by [SkVideoDecoder.MakeFromStream] /
 * [SkVideoDecoder.MakeFromData]. Mirrors the public surface of
 * upstream's `SkVideoDecoder` instance methods.
 *
 * Constructible only from inside this file — but practically
 * unreachable, since both factories throw before returning.
 */
@Suppress("UNUSED_PARAMETER")
public class SkVideoDecoderInstance internal constructor() {

    /**
     * Mirrors `sk_sp<SkImage> SkVideoDecoder::nextImage()` — pulls
     * the next decoded frame, or returns `null` at end-of-stream.
     * Always throws.
     */
    public fun nextFrame(): SkImage? = throw NotImplementedError(
        "STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI.",
    )

    /** Mirrors `SkVideoDecoder::computeFrameRate()`. Always throws. */
    public val frameRate: Float
        get() = throw NotImplementedError(
            "STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI.",
        )

    /** Mirrors `SkVideoDecoder::duration()` (microseconds). Always throws. */
    public val duration: Long
        get() = throw NotImplementedError(
            "STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI.",
        )
}
