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
 * Slice D4.1 ships the interface plus the two raster sinks
 * ([RasterSink8888], [RasterSinkF16]) that drive the existing
 * `runGmTest` flow through the new abstraction. Picture / PDF / SVG
 * sinks come in later slices (D4.2 / D4.5).
 */
public interface Sink {

    /**
     * Short label identifying this sink in the DM report. Mirrors
     * upstream's `--config 8888` / `--config f16` / `--config pic-8888`
     * convention so reports can be diffed line-for-line.
     */
    public val tag: String

    /**
     * Render [src] into this sink's configuration. Returns either a
     * [Result.Ok] carrying the produced [SkBitmap] (for raster /
     * replay-to-raster sinks) or a [Result.Error] with a human-readable
     * message describing the failure mode.
     *
     * Concrete sinks **must** allocate their own output bitmap — this
     * matches upstream's `Sink::draw` allocating its own
     * `SkSurface::MakeRasterN32(...)`.
     */
    public fun draw(src: GM): Result

    /**
     * Result of a [draw] call. Sealed so future PDF / SVG sinks can
     * extend it without breaking exhaustive `when` checks (likely
     * additions in later slices : `Bytes(bytes, mimeType)` for vector
     * outputs, or `Stream` for streaming consumers).
     */
    public sealed class Result {
        /** Successful render. [bitmap] holds the rasterised pixels. */
        public data class Ok(val bitmap: SkBitmap) : Result()

        /** Render failed (configuration error, exception during draw). */
        public data class Error(val message: String) : Result()
    }
}
