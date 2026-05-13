package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkRecorder`
 * ([include/core/SkRecorder.h](https://github.com/google/skia/blob/main/include/core/SkRecorder.h)).
 *
 * In upstream Skia, an `SkRecorder` is a backend-specific recording
 * context : the **CPU** recorder lives in `skcpu::Recorder`
 * (`include/core/SkCPURecorder.h`), the **Ganesh** GPU recorder in
 * `GrRecordingContext`, the **Graphite** GPU recorder in
 * `skgpu::graphite::Recorder`. Newer GMs and several `SkImage` /
 * `SkSurface` factories take an `SkRecorder*` so they can defer
 * back-end work onto the right pipeline.
 *
 * Kanvas-skia is raster-only, so the type hierarchy here is little
 * more than a marker for upstream call sites that need *something* to
 * pass : [type] always reports [Type.kRaster], and the GPU enum
 * variant exists only so call sites can pattern-match exhaustively.
 *
 * **R3.9 status — marker base class.** [resourceCache] is a stub
 * returning `null` (upstream returns a `GrResourceCache*` or
 * equivalent for GPU recorders — meaningless on the CPU).
 */
public abstract class SkRecorder {

    /**
     * Mirrors `virtual Type type() const = 0` (`SkRecorder.h:34`).
     * Identifies which backend pipeline this recorder feeds.
     */
    public abstract fun type(): Type

    /**
     * Mirrors `virtual void* resourceCache() = 0` (used by upstream
     * GPU back-ends to expose their cache). Raster recorders have no
     * cache to expose — kanvas-skia's stub returns `null`.
     */
    public open fun resourceCache(): Any? = null

    /**
     * Backend tag for the recorder. Upstream's enum has three values
     * (`kCPU` / `kGanesh` / `kGraphite`) — kanvas-skia collapses the
     * two GPU variants into [kGpu] since none of them is implemented.
     */
    public enum class Type {
        /** Raster / CPU recorder ([SkCPURecorder]). */
        kRaster,

        /** Any GPU recorder (Ganesh / Graphite) — not implemented in raster-only kanvas-skia. */
        kGpu,
    }
}

/**
 * Iso-aligned port of Skia's `skcpu::Recorder`
 * ([include/core/SkCPURecorder.h](https://github.com/google/skia/blob/main/include/core/SkCPURecorder.h)).
 *
 * Concrete [SkRecorder] for the CPU raster pipeline. Upstream this
 * exposes `makeBitmapSurface(info, rowBytes, props)` so callers can
 * allocate raster surfaces through the recorder ; kanvas-skia already
 * exposes that via `SkSurfaces.Raster` so the CPU recorder is
 * essentially a marker. Instances are typically obtained through
 * [SkCPUContext.makeRecorder].
 */
public class SkCPURecorder : SkRecorder() {
    override fun type(): Type = Type.kRaster
}

/**
 * Iso-aligned port of Skia's `skcpu::Context`
 * ([include/core/SkCPUContext.h](https://github.com/google/skia/blob/main/include/core/SkCPUContext.h)).
 *
 * Upstream `skcpu::Context` is the root object that hands out CPU
 * [SkCPURecorder] instances. Its constructor takes an `Options` POD
 * (empty in current upstream). Kanvas-skia mirrors the shape but the
 * recorder itself is a marker, so this is essentially a factory
 * facade.
 *
 * Use [Make] to obtain an instance, then [makeRecorder] to get a
 * fresh CPU recorder.
 */
public class SkCPUContext private constructor() {

    /**
     * Mirrors `std::unique_ptr<Recorder> makeRecorder() const`
     * (`SkCPUContext.h:21`). Returns a fresh [SkCPURecorder] —
     * recorders are cheap, so each call gets a new one.
     */
    public fun makeRecorder(): SkCPURecorder = SkCPURecorder()

    public companion object {
        /**
         * Mirrors `static std::unique_ptr<const Context> Make()`
         * (`SkCPUContext.h:24`). Returns a fresh CPU context with
         * default options.
         */
        public fun Make(): SkCPUContext = SkCPUContext()
    }
}
