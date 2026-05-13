package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkCapabilities`
 * ([include/core/SkCapabilities.h](https://github.com/google/skia/blob/main/include/core/SkCapabilities.h)).
 *
 * A small bag of feature flags describing what the active backend
 * can do. Used by SkSL and the runtime-effect machinery to decide
 * whether to enable certain shader features (sample locations,
 * dual-source blending, …). Only the **raster** backend exists in
 * this port, and it advertises the bare minimum.
 *
 * **R1 status — minimal stub.** [RasterBackend] returns a singleton
 * with conservative defaults: SkSL `100` (the lowest version, no
 * extensions), no sample-location support, no dual-source blending.
 */
public class SkCapabilities private constructor(
    /** SkSL language version the backend can compile. */
    public val skslVersion: SkSLVersion,
    /** Does the backend report per-fragment sample locations? */
    public val fSupportSampleLocations: Boolean,
    /** Does the backend support dual-source blending? */
    public val fDualSourceBlendingSupport: Boolean,
) {

    public companion object {
        private val RASTER = SkCapabilities(
            skslVersion = SkSLVersion.k100,
            fSupportSampleLocations = false,
            fDualSourceBlendingSupport = false,
        )

        /** Capabilities for the CPU/raster backend. */
        public fun RasterBackend(): SkCapabilities = RASTER
    }

    /** SkSL language version. Mirrors `SkSL::Version` in upstream. */
    public enum class SkSLVersion {
        /** SkSL 1.00 — basic version, no `#extension`s. */
        k100,
        /** SkSL 3.00 — adds storage buffers, atomics, etc. */
        k300,
    }
}
