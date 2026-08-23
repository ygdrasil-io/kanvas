package org.graphiks.kanvas.codec

import org.skia.foundation.SkImage
import java.nio.ByteBuffer

/**
 * Codec-backed factory methods for [SkImage]. Kept in the codec layer so
 * `foundation` does not import from `codec`; encoded-image factories remain
 * owned by their decoder implementations.
 *
 * Mirrors the subset of upstream Skia's `SkImages::*` factories that need an
 * [Codec] decoder.
 */
public object ImageCodecs {

    /**
     * Mirrors Skia's
     * `SkImages::DeferredFromEncodedData(sk_sp<const SkData>,
     * std::optional<AlphaType>)`.
     *
     * Decodes the encoded byte stream [encoded] (PNG / JPEG / GIF /
     * BMP / WBMP / WEBP — see [Codec.MakeFromData] for the registered
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
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || bitmap == null) return null
        return SkImage.Make(bitmap)
    }
}
