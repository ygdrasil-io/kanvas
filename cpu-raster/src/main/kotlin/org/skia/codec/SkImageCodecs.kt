package org.skia.codec

import org.skia.foundation.SkImage
import java.nio.ByteBuffer

/**
 * Codec-backed factory methods for [SkImage]. Split from
 * [org.skia.foundation.SkImages] so that `foundation` no longer imports
 * from `codec` (cycle break preparing the `:cpu-raster` Gradle module
 * extraction).
 *
 * Mirrors the subset of Skia's `SkImages::*` factories that need an
 * [SkCodec] decoder.
 */
public object SkImageCodecs {

    /**
     * Mirrors Skia's
     * `SkImages::DeferredFromEncodedData(sk_sp<const SkData>,
     * std::optional<SkAlphaType>)`.
     *
     * Decodes the encoded byte stream [encoded] (PNG / JPEG / GIF /
     * BMP / WBMP / WEBP — see [SkCodec.MakeFromData] for the registered
     * formats) into a fresh raster [SkImage]. Returns `null` when no
     * registered codec matches the leading bytes, or when the decode
     * itself fails. Despite the upstream name ("deferred"), the raster
     * backend eagerly decodes — there is no JIT decode-on-draw path.
     *
     * The alpha-type parameter from upstream is omitted ; we use the
     * codec's natural alpha type (matches `std::nullopt` upstream).
     */
    public fun DeferredFromEncodedData(encoded: ByteBuffer): SkImage? {
        // Materialise the ByteBuffer to a ByteArray without mutating the
        // caller's read cursor.
        val view = encoded.duplicate()
        val bytes = ByteArray(view.remaining())
        view.get(bytes)
        val codec = SkCodec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || bitmap == null) return null
        return SkImage.Make(bitmap)
    }
}
