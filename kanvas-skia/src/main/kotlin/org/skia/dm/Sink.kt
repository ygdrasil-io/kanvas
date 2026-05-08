package org.skia.dm

import org.skia.foundation.SkBitmap
import org.skia.tests.GM

/**
 * Mirrors Skia's `dm/DMSrcSink.h::Sink` — the abstract output target a
 * GM is rendered into. Each concrete sink picks a configuration
 * (raster `8888`, raster `F16`, picture replay, PDF, SVG, …) and
 * exposes its [tag] for the DM report.
 *
 * Upstream's signature is roughly :
 * ```cpp
 * struct Sink {
 *     virtual Result draw(const Src&, SkBitmap*, SkWStream*, SkString* log) const = 0;
 *     virtual const char* fileExtension() const = 0;
 *     virtual SinkFlags flags() const = 0;
 * };
 * ```
 * — i.e. the caller passes the output containers, and the sink writes
 * into the one matching its kind. We collapse that into a [Result]
 * sum type so each sink owns its output buffer end-to-end, which is
 * cleaner in Kotlin and avoids the `nullptr` dance.
 *
 * Slice D4.1 shipped the interface plus the two raster sinks
 * ([RasterSink8888], [RasterSinkF16]). Slice D4.2 added
 * [PictureSink]. Slice **B2.5** of the SVG mini plan adds
 * [SvgSink] alongside the [Result.Bytes] variant for vector
 * outputs.
 */
public interface Sink {

    /**
     * Short label identifying this sink in the DM report. Mirrors
     * upstream's `--config 8888` / `--config f16` / `--config pic-8888`
     * / `--config svg` convention so reports can be diffed
     * line-for-line.
     */
    public val tag: String

    /**
     * File extension of the encoded output : `"png"` for raster sinks,
     * `"svg"` for [SvgSink]. Mirrors upstream's
     * `Sink::fileExtension()` — the [Runner] consumes this when
     * building the per-result `RunRecord.extension` field.
     */
    public val fileExtension: String get() = "png"

    /**
     * Render [src] into this sink's configuration. Raster sinks
     * return [Result.Ok] with the produced [SkBitmap] ; vector sinks
     * (B2.5 [SvgSink]) return [Result.Bytes] with the encoded
     * payload ; failures return [Result.Error] with a human-readable
     * message.
     *
     * Concrete sinks **must** allocate their own output buffer —
     * this matches upstream's `Sink::draw` allocating its own
     * `SkSurface::MakeRasterN32(...)` or `SkSVGCanvas::Make(stream)`.
     */
    public fun draw(src: GM): Result

    /**
     * Result of a [draw] call. Sealed so each future sink kind can
     * extend it without breaking exhaustive `when` checks. The plan
     * sketch in [MIGRATION_PLAN_RASTER_COMPLETION.md § D4](../../../../../../../MIGRATION_PLAN_RASTER_COMPLETION.md)
     * flagged `Bytes(bytes, mimeType)` as a future extension ; that
     * future is now (B2.5).
     */
    public sealed class Result {
        /** Successful raster render. [bitmap] holds the rasterised pixels. */
        public data class Ok(val bitmap: SkBitmap) : Result()

        /**
         * Successful vector render (SVG, future PDF if revived). The
         * caller hashes [bytes] directly into the DM report (no
         * PNG re-encode), and writes the payload to disk under a
         * filename ending in [SkSVGCanvas]'s — well, this is a
         * sealed-class data carrier and doesn't reach into the
         * canvas — the matching sink declares its [Sink.fileExtension]
         * so [Runner] knows the suffix.
         *
         * [mimeType] is the MIME type for the bytes (e.g.
         * `"image/svg+xml"`). It is **not** currently used by
         * [Runner] but kept on the variant so a future HTTP / S3
         * upload path has something semantic to set as
         * `Content-Type`.
         */
        public data class Bytes(val bytes: ByteArray, val mimeType: String) : Result() {
            // ByteArray.equals is reference-only, so we override to
            // compare by content for the data-class equals contract.
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Bytes) return false
                return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
            }

            override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()

            override fun toString(): String =
                "Sink.Result.Bytes(${bytes.size} bytes, mimeType=$mimeType)"
        }

        /** Render failed (configuration error, exception during draw). */
        public data class Error(val message: String) : Result()
    }
}
