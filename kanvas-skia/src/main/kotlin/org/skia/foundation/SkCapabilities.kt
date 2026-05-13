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
 * **R-suivi.5 status — richer raster caps.** [RasterBackend] still
 * returns a singleton with all extension flags `false` and SkSL
 * `100` — but now exposes the full set of flags upstream's
 * `SkSL::ShaderCaps` carries, so call-sites that branch on
 * individual features (sample locations, MSAA resolves, dual-source
 * blending, fragment-coord access, …) compile against the same
 * boolean surface as upstream.
 */
public class SkCapabilities private constructor(
    /** SkSL language version the backend can compile. */
    public val skslVersion: SkSLVersion,
    /** Does the backend report per-fragment sample locations? */
    public val fSupportSampleLocations: Boolean,
    /** Are MSAA buffers resolved by the backend automatically? */
    public val fMSAAResolvesAutomatically: Boolean,
    /** Does the backend support dual-source blending? */
    public val fDualSourceBlendingSupport: Boolean,
    /** Does the backend support framebuffer fetch in shaders? */
    public val fFBFetchSupport: Boolean,
    /** Does the backend support `dFdx` / `dFdy` (shader derivatives)? */
    public val fShaderDerivativeSupport: Boolean,
    /** Does the backend support 32-bit integer types in SkSL? */
    public val fIntegerSupport: Boolean,
    /** Does the backend support flat (no-interpolation) varyings? */
    public val fFlatInterpolationSupport: Boolean,
    /** Are 32-bit float types available (always true on raster JVM)? */
    public val fFloatIs32Bits: Boolean,
    /** Does the backend support `gl_FragCoord` in fragment shaders? */
    public val fCanUseFragCoord: Boolean,
    /** Does the backend support `inverseHyperbolic*` (asinh/acosh/atanh)? */
    public val fInverseHyperbolicSupport: Boolean,
    /** Does the backend support non-square matrix types? */
    public val fNonsquareMatrixSupport: Boolean,
    /** Does the backend support sampling external (e.g. video) textures? */
    public val fExternalTextureSupport: Boolean,
    /** Does the backend support infinity / NaN in shaders? */
    public val fInfinitySupport: Boolean,
) {

    public companion object {
        private val RASTER = SkCapabilities(
            skslVersion = SkSLVersion.k100,
            // The raster (CPU) backend has no GPU-side feature support.
            fSupportSampleLocations = false,
            fMSAAResolvesAutomatically = false,
            fDualSourceBlendingSupport = false,
            fFBFetchSupport = false,
            fShaderDerivativeSupport = false,
            fIntegerSupport = false,
            fFlatInterpolationSupport = false,
            // JVM is always IEEE-754 32-bit float.
            fFloatIs32Bits = true,
            fCanUseFragCoord = false,
            fInverseHyperbolicSupport = false,
            fNonsquareMatrixSupport = false,
            fExternalTextureSupport = false,
            fInfinitySupport = true,
        )

        /** Capabilities for the CPU/raster backend. Always the same singleton. */
        public fun RasterBackend(): SkCapabilities = RASTER
    }

    /** SkSL language version. Mirrors `SkSL::Version` in upstream. */
    public enum class SkSLVersion {
        /** SkSL 1.00 — basic version, no `#extension`s (GLSL ES 1.0). */
        k100,
        /** SkSL 3.00 — adds storage buffers, atomics (GLSL ES 3.0). */
        k300,
        /** Desktop GLSL 3.30 — convenience alias commonly referenced by
         *  upstream tests; same generation as [k300] semantically. */
        k330,
    }
}
