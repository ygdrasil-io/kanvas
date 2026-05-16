package org.skia.foundation


import org.skia.math.SkColor
import org.skia.math.SkColor4f
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
/**
 * Mirrors Skia's
 * [`SkColorFilter`](https://github.com/google/skia/blob/main/include/core/SkColorFilter.h)
 * — an immutable function `SkColor4f → SkColor4f` applied to every
 * source pixel after rasterisation but before the [SkPaint.blendMode]
 * composite.
 *
 * In the kanvas-skia raster pipeline the per-pixel sequence is :
 *
 * ```
 *   shader (or paint.color) → coverage → colorFilter → blendMode → dst
 * ```
 *
 * Filters operate on **non-premultiplied** floating-point colour in
 * `[0, 1]`. The device handles premul/unpremul boundaries on either
 * side : input pixels are unpremultiplied before [filterColor4f],
 * the result is re-premultiplied (× output alpha) before the blend.
 *
 * Filters are **immutable** — implementations may share state, never
 * mutate it after construction. This lets the device cache the output
 * of [filterColor4f] when the source colour is constant (solid-paint
 * draws), evaluating the filter exactly once instead of per-pixel.
 *
 * Construct via the [SkColorFilters] factory or via concrete
 * implementations like [SkLumaColorFilter].
 */
public abstract class SkColorFilter {

    /**
     * Apply the filter to a single colour. Both input and output are
     * **non-premultiplied** [SkColor4f] in the destination working
     * colour space. Implementations may produce out-of-`[0, 1]` values
     * (matrix filters with negative coefficients, additive lighting,
     * etc.) — the device clamps before storing into the bitmap.
     */
    public abstract fun filterColor4f(src: SkColor4f): SkColor4f

    /**
     * Convenience overload : decodes an 8-bit [SkColor], delegates to
     * [filterColor4f], encodes back. Useful for one-off colour
     * transforms outside the rasteriser hot path.
     */
    public open fun filterColor(c: SkColor): SkColor {
        val src = SkColor4f(
            SkColorGetR(c) / 255f,
            SkColorGetG(c) / 255f,
            SkColorGetB(c) / 255f,
            SkColorGetA(c) / 255f,
        )
        val out = filterColor4f(src)
        return SkColorSetARGB(
            (out.fA.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fR.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fG.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fB.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
        )
    }

    /**
     * Mirrors Skia's `SkColorFilter::makeComposed`. Returns a new
     * filter that applies [inner] first, then `this` to the result.
     */
    public open fun makeComposed(inner: SkColorFilter): SkColorFilter =
        SkComposeColorFilter(outer = this, inner = inner)

    /**
     * Mirrors Skia's `SkColorFilter::isAlphaUnchanged`. `true` if this
     * filter never modifies the alpha channel of its input —
     * optimisation hint that lets the device skip the unpremul/repremul
     * round-trip in some hot paths. Default `false` ; concrete
     * implementations override when known.
     */
    public open fun isAlphaUnchanged(): Boolean = false

    /**
     * R-final.3 — mirrors Skia's
     * [`SkColorFilter::makeWithWorkingColorSpace`](https://github.com/google/skia/blob/main/src/effects/colorfilters/SkWorkingFormatColorFilter.cpp).
     *
     * Returns a wrapper filter that runs `this` in the supplied
     * [workingCS] instead of the bitmap device's nominal working colour
     * space.
     *
     * Pipeline (per pixel) :
     *
     * ```
     *   inputCS → workingCS  (xform forward)
     *   filterColor4f(c)     (child, executed in workingCS)
     *   workingCS → inputCS  (xform back)
     * ```
     *
     * Where `inputCS` is the nominal colour space the filter sees its
     * input in. In the kanvas-skia raster pipeline that's **sRGB** —
     * `SkBitmapDevice.inDeviceColorSpace` runs the filter on the paint
     * colour *before* the sRGB → bitmap-CS xform, and the image-shaded
     * branches apply the filter to the source-image-CS pixel before the
     * deferred `transformPaintColor` runs. So choosing sRGB as the
     * wrapper's nominal "dst CS" matches upstream's `rec.fDstCS ?:
     * SkColorSpace::MakeSRGB()` fallback path while keeping the
     * implementation independent of the bitmap device's working CS.
     *
     * Identity short-circuits :
     *  - [workingCS] equals sRGB ⇒ no xform at either end ⇒ returns
     *    `this` directly.
     */
    public open fun makeWithWorkingColorSpace(workingCS: SkColorSpace): SkColorFilter {
        if (SkColorSpace.equals(workingCS, SkColorSpace.makeSRGB())) return this
        return SkWorkingFormatColorFilter(this, workingCS)
    }
}

/**
 * R-final.3 — wrapper that runs the [child] filter in [workingCS]
 * instead of sRGB.
 *
 * The wrapper assumes its input colour is in **sRGB**. Upstream's
 * implementation routes the actual canvas dst CS through
 * `rec.fDstCS`, defaulting to sRGB when the recording sink doesn't
 * advertise one ; in our raster pipeline the colour filter receives
 * its input pre-`transformPaintColor`, i.e. still in sRGB, so the
 * "default sRGB" branch is the only one we ever hit.
 *
 * Mirrors `SkWorkingFormatColorFilter` (`src/effects/colorfilters/SkWorkingFormatColorFilter.cpp`).
 */
internal class SkWorkingFormatColorFilter(
    private val child: SkColorFilter,
    private val workingCS: SkColorSpace,
) : SkColorFilter() {

    private val srgbToWorking: org.skia.core.SkColorSpaceXformSteps =
        org.skia.core.SkColorSpaceXformSteps(
            src = SkColorSpace.makeSRGB(),
            srcAT = org.skia.core.SkAlphaType.kUnpremul,
            dst = workingCS,
            dstAT = org.skia.core.SkAlphaType.kUnpremul,
        )

    private val workingToSrgb: org.skia.core.SkColorSpaceXformSteps =
        org.skia.core.SkColorSpaceXformSteps(
            src = workingCS,
            srcAT = org.skia.core.SkAlphaType.kUnpremul,
            dst = SkColorSpace.makeSRGB(),
            dstAT = org.skia.core.SkAlphaType.kUnpremul,
        )

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val rgba = floatArrayOf(src.fR, src.fG, src.fB, src.fA)
        srgbToWorking.apply(rgba)
        val mid = child.filterColor4f(SkColor4f(rgba[0], rgba[1], rgba[2], rgba[3]))
        rgba[0] = mid.fR; rgba[1] = mid.fG; rgba[2] = mid.fB; rgba[3] = mid.fA
        workingToSrgb.apply(rgba)
        return SkColor4f(rgba[0], rgba[1], rgba[2], rgba[3])
    }

    override fun isAlphaUnchanged(): Boolean = child.isAlphaUnchanged()

    /**
     * Re-wrapping a [SkWorkingFormatColorFilter] with a different
     * [SkColorSpace] should fold rather than nest — same trick the
     * shader [SkShader.makeWithLocalMatrix] uses. Returns a fresh
     * wrapper around the same [child] but with the new working CS.
     */
    override fun makeWithWorkingColorSpace(workingCS: SkColorSpace): SkColorFilter {
        if (SkColorSpace.equals(workingCS, SkColorSpace.makeSRGB())) return child
        return SkWorkingFormatColorFilter(child, workingCS)
    }
}
