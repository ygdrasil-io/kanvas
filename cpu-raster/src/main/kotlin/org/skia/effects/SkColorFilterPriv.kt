package org.skia.effects

import org.graphiks.math.SkColor4f
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorFilter

/**
 * R-final.S **STUB.COLOR_FILTER_PRIV** — surface stub for Skia's
 * `src/core/SkColorFilterPriv.h::SkColorFilterPriv::WithWorkingFormat`.
 *
 * `WithWorkingFormat` wraps an inner [SkColorFilter] so that it runs
 * in a caller-specified working colour space (transfer function +
 * gamut matrix + alpha type). The device unpremultiplies the source
 * pixel into the target colour space, invokes the inner filter, then
 * re-premultiplies and converts back to the device colour space.
 *
 * `:kanvas-skia`'s raster pipeline does not yet model working-format
 * colour-space conversions per filter ; implementing this path requires
 * hooking `SkColorSpaceXformSteps` into the per-pixel filter call,
 * which is deferred. The API is exposed here so that the
 * `AlternateLuma` port (see
 * [`AlternateLumaGM`](../../../../tests/AlternateLumaGM.kt)) compiles
 * and references the documented surface.
 *
 * A future implementation can replace [withWorkingFormat]'s body
 * without touching call sites.
 */
@Suppress("UNUSED_PARAMETER")
public object SkColorFilterPriv {

    /**
     * Mirrors `SkColorFilterPriv::MakeGaussian()`
     * (`src/core/SkColorFilterPriv.h`).
     *
     * Creates a Gaussian (soft-light) color filter used internally by
     * `SkShadowUtils` for shadow edge falloff. Upstream implements this
     * as a raster-pipeline `gauss_a_to_rgba` stage: evaluate a quartic
     * approximation from source alpha, then copy that premultiplied value
     * into RGBA. Kanvas color filters return non-premultiplied values, so
     * the equivalent local result is white RGB with the Gaussian alpha.
     */
    public fun makeGaussian(): SkColorFilter = GaussianColorFilter

    /**
     * Mirrors `sk_sp<SkColorFilter> SkColorFilterPriv::WithWorkingFormat(
     *     sk_sp<SkColorFilter> child,
     *     const skcms_TransferFunction* tf,
     *     const skcms_Matrix3x3* gamut,
     *     const SkAlphaType* at)`
     * (`src/core/SkColorFilterPriv.h`).
     *
     * Wraps [child] so it runs in the working colour space described by
     * ([tf], [gamut], [at]). The device converts each pixel into that
     * space before passing it to [child], and converts the result back
     * to the device space afterwards.
     *
     * @throws NotImplementedError always — pending implementation.
     */
    public fun withWorkingFormat(
        child: SkColorFilter,
        tf: SkcmsTransferFunction,
        gamut: SkcmsMatrix3x3,
        at: SkAlphaType,
    ): SkColorFilter = throw NotImplementedError(
        "STUB.COLOR_FILTER_PRIV: SkColorFilterPriv::WithWorkingFormat requires " +
            "per-pixel colour-space xform steps in the raster pipeline — " +
            "see API_FINALIZATION_PLAN.md.",
    )

    private object GaussianColorFilter : SkColorFilter() {
        override fun filterColor4f(src: SkColor4f): SkColor4f {
            val a = gaussianAlpha(src.fA)
            return SkColor4f(1f, 1f, 1f, a)
        }

        private fun gaussianAlpha(a: Float): Float =
            a * (
                a * (
                    a * (
                        a * -2.26661229133605957031f +
                            2.89795351028442382812f
                    ) + 0.21345567703247070312f
                ) + 0.15489584207534790039f
            ) + 0.00030726194381713867f
    }
}
