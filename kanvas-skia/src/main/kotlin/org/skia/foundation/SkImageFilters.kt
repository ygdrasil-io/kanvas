package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorChannel
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's
 * [`SkImageFilters`](https://github.com/google/skia/blob/main/include/effects/SkImageFilters.h)
 * factory namespace — the canonical set of [SkImageFilter] builders.
 *
 * **Phase 7d.1 ships** :
 *  - [Offset] — translate the image by `(dx, dy)` device pixels.
 *  - [ColorFilter] — apply an [SkColorFilter] per pixel.
 *  - [Compose] — chain `outer(inner(image))`.
 *
 * **Phase 7d.2 will add** : `Blur`, `MatrixTransform`, `DropShadow`,
 * and a port of `gm/imagefiltersbase.cpp` for end-to-end validation.
 */
public object SkImageFilters {

    /**
     * Mirrors Skia's `SkImageFilters::Offset(dx, dy, input)` — shifts
     * the image by `(dx, dy)` device pixels. The offset scales with
     * the canvas's max scale at draw time so a constant `(dx, dy)`
     * displacement stays visually-equivalent under different CTMs.
     *
     * `input == null` is the identity input (the source image passed
     * to `filterImage`). Otherwise [input]'s output is offset.
     */
    public fun Offset(dx: Float, dy: Float, input: SkImageFilter? = null): SkImageFilter =
        SkOffsetImageFilter(dx, dy, input)

    /**
     * Mirrors Skia's `SkImageFilters::ColorFilter(cf, input)` —
     * applies [cf] to every pixel of the (possibly chained) input
     * image. Identical math to `paint.colorFilter` but applied
     * **before** the blend instead of post-blend, which lets effect
     * chains compose colour operations with structural transforms
     * (offset, blur, etc.).
     */
    public fun ColorFilter(cf: SkColorFilter, input: SkImageFilter? = null): SkImageFilter =
        SkColorFilterImageFilter(cf, input)

    /**
     * Mirrors Skia's `SkImageFilters::Compose(outer, inner)` — chains
     * `outer.filterImage(inner.filterImage(src))`. Same null-handling
     * convention as [SkPathEffect.MakeCompose] :
     *  - `outer == null` ⇒ returns [inner] (or null if both are null).
     *  - `inner == null` ⇒ returns [outer].
     *  - both null ⇒ returns null.
     */
    public fun Compose(outer: SkImageFilter?, inner: SkImageFilter?): SkImageFilter? {
        if (outer == null) return inner
        if (inner == null) return outer
        return SkComposeImageFilter(outer, inner)
    }

    /**
     * Phase 7d.2 — Gaussian blur. Output grows by `±ceil(3·σ)` per
     * axis. `sigma <= 0` returns input unchanged.
     *
     * Phase G6 — adds [tileMode] (matches upstream `SkImageFilters::
     * Blur(sigmaX, sigmaY, tileMode, input, cropRect)`). The blur
     * kernel samples outside the input image's bounds using the
     * supplied [tileMode] : `kDecal` ⇒ transparent black, `kClamp` ⇒
     * clamp to the nearest edge texel, `kRepeat` / `kMirror` ⇒ the
     * usual periodic tilings.
     *
     * The optional [cropRect] restricts the filter's output bounds —
     * implemented as `Crop(cropRect, kDecal, blur(...))`. Upstream
     * uses cropRect as the filter's `getOutputBounds()` hint as well
     * as a hard clip ; here we just hard-clip the output to match
     * the visual result.
     */
    public fun Blur(
        sigmaX: Float,
        sigmaY: Float = sigmaX,
        tileMode: SkTileMode = SkTileMode.kDecal,
        input: SkImageFilter? = null,
        cropRect: SkIRect? = null,
    ): SkImageFilter? {
        if (!sigmaX.isFinite() || !sigmaY.isFinite()) {
            // Even a no-op blur honours an explicit cropRect.
            if (cropRect != null && input != null) {
                return Crop(SkRect.Make(cropRect), SkTileMode.kDecal, input)
            }
            return input
        }
        val blur: SkImageFilter? = if (sigmaX <= 0f && sigmaY <= 0f) {
            input
        } else {
            SkBlurImageFilter(
                sigmaX.coerceAtLeast(0f),
                sigmaY.coerceAtLeast(0f),
                tileMode,
                input,
            )
        }
        if (cropRect == null) return blur
        return Crop(SkRect.Make(cropRect), SkTileMode.kDecal, blur)
    }

    /**
     * Legacy 3-arg overload preserved for source-compat with existing
     * call sites (Phase 7d.2 signature). Equivalent to
     * `Blur(sigmaX, sigmaY, SkTileMode.kDecal, input)`.
     */
    public fun Blur(
        sigmaX: Float,
        sigmaY: Float,
        input: SkImageFilter?,
    ): SkImageFilter? = Blur(sigmaX, sigmaY, SkTileMode.kDecal, input, null)

    /**
     * Phase 7d.2 — apply 2-D affine matrix with sampling. Output is
     * matrix-mapped bbox of the input. Non-invertible returns input.
     */
    public fun MatrixTransform(
        matrix: SkMatrix,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        input: SkImageFilter? = null,
    ): SkImageFilter? {
        if (matrix.invert() == null) return input
        return SkMatrixTransformImageFilter(matrix, sampling, input)
    }

    /**
     * Phase 7d.2 — drop-shadow recipe. Composites a blurred + tinted
     * shadow behind the input via SrcOver.
     */
    public fun DropShadow(
        dx: Float, dy: Float,
        sigmaX: Float, sigmaY: Float,
        color: SkColor,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkDropShadowImageFilter(dx, dy, sigmaX, sigmaY, color, input)

    // ─── C1.1 — Source / passthrough wrappers ────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::Image(image, srcRect, dstRect,
     * sampling)` — wraps a static [SkImage] as the filter input. The
     * `src` parameter passed to [SkImageFilter.filterImage] is
     * ignored ; the output is `image` cropped to [srcRect] and
     * placed at [dstRect].
     *
     * Mainly useful as the *source* of a filter chain — pair with
     * [Compose] / [Blend] / [Merge] / arithmetic filters that need
     * an explicit input texture rather than the rasterised draw.
     */
    public fun Image(
        image: SkImage,
        srcRect: SkRect,
        dstRect: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
    ): SkImageFilter = SkImageImageFilter(image, srcRect, dstRect, sampling)

    /**
     * Convenience overload — wraps the full image into a filter at
     * its native bounds.
     */
    public fun Image(
        image: SkImage,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
    ): SkImageFilter {
        val full = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        return Image(image, full, full, sampling)
    }

    /**
     * Mirrors Skia's `SkImageFilters::Picture(pic, targetRect)` —
     * replays an [SkPicture] into a bitmap of [targetRect]'s size,
     * then exposes that bitmap as the filter's input. The `src`
     * argument to [SkImageFilter.filterImage] is ignored.
     *
     * The picture's local-space origin is mapped to the bitmap's
     * top-left via a `(-targetRect.left, -targetRect.top)` translate
     * so a picture recorded at `(50, 50)` and a target rect of
     * `(40, 40, 100, 100)` lands the recorded ops at the right
     * spot in the output bitmap.
     */
    public fun Picture(pic: SkPicture, targetRect: SkRect): SkImageFilter =
        SkPictureImageFilter(pic, targetRect)

    /**
     * Convenience overload — uses the picture's recorded
     * [SkPicture.cullRect] as the target rect.
     */
    public fun Picture(pic: SkPicture): SkImageFilter = Picture(pic, pic.cullRect)

    /**
     * Mirrors Skia's `SkImageFilters::Shader(shader, dither)` — fills
     * a buffer with [shader] sampled at every pixel, returns that
     * buffer as the filter input. The buffer's size matches the
     * `src` image passed to [SkImageFilter.filterImage] (the chain's
     * "evaluation context"), so this filter is always paired with
     * something that defines size — typically as the source of a
     * [Crop] / [Blend] / arithmetic chain on top of a real
     * `saveLayer` rasterisation.
     *
     * [dither] is plumbed for source-compat with upstream call sites
     * but currently advisory : the F16 raster path is already
     * 16-bit per channel and doesn't need dithering, and the 8888
     * path applies the project's standard dither.
     */
    public fun Shader(
        shader: SkShader,
        @Suppress("UNUSED_PARAMETER") dither: Boolean = false,
    ): SkImageFilter = SkShaderImageFilter(shader)

    /**
     * Mirrors Skia's `SkImageFilters::Empty()` — a transparent-black
     * filter input. Useful as a placeholder in `Merge` / `Compose`
     * chains under construction, and to test that downstream filters
     * handle a fully-transparent input correctly.
     */
    public fun Empty(): SkImageFilter = SkEmptyImageFilter

    // `RuntimeShader(...)` factories have moved to
    // [org.skia.effects.runtime.SkRuntimeImageFilters] so that
    // `org.skia.foundation` no longer imports from `effects.runtime`
    // (cycle break preparing the :cpu-raster Gradle module extraction).

    /**
     * Mirrors Skia's `SkImageFilters::Crop(rect, tileMode, input)` —
     * constrains [input]'s output to [rect], with [tileMode]
     * dictating how out-of-rect samples are treated.
     *
     *  - [SkTileMode.kClamp] : every pixel inside `rect` keeps the
     *    input's value at the closest clamped coord ; outside `rect`,
     *    the result is transparent black.
     *  - [SkTileMode.kRepeat] : tile the input's contribution across
     *    the rect's interior.
     *  - [SkTileMode.kMirror] : tile with mirroring at every period
     *    boundary.
     *  - [SkTileMode.kDecal] (default) : pass through inside `rect`,
     *    transparent outside — the strict "crop" semantics.
     *
     * `input == null` is the identity input (the rasterised source
     * image) — equivalent to upstream's "crop the implicit source".
     */
    public fun Crop(
        rect: SkRect,
        tileMode: SkTileMode = SkTileMode.kDecal,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkCropImageFilter(rect, tileMode, input)

    /** Convenience overload — `Crop(rect, kDecal, input)`. */
    public fun Crop(rect: SkRect, input: SkImageFilter? = null): SkImageFilter =
        Crop(rect, SkTileMode.kDecal, input)

    // ─── C1.2 — Tile + Magnifier ─────────────────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::Tile(srcRect, dstRect, input)` —
     * replicates [input] sampled in [srcRect] across [dstRect]. The
     * implicit tile mode is kRepeat ; for clamp / mirror / decal use
     * [Crop] instead.
     *
     * Output dimensions match `dstRect`. Empty `srcRect` yields a
     * transparent-black `dstRect`-sized output, matching upstream's
     * "empty tile" semantic.
     */
    public fun Tile(srcRect: SkRect, dstRect: SkRect, input: SkImageFilter? = null): SkImageFilter =
        SkTileImageFilter(srcRect, dstRect, input)

    /**
     * Mirrors Skia's `SkImageFilters::Magnifier(lensBounds, zoom,
     * inset, sampling, input)` — radial-ish lens distortion. Inside
     * [lensBounds], the input is magnified by [zoomAmount] around
     * the lens centre ; pixels within [inset] of the lens edge fade
     * smoothly back to the un-magnified pass-through.
     *
     * `zoomAmount <= 0` is treated as a no-op (returns input
     * unchanged). `inset` is pinned to `>= 0.0001f` to avoid
     * division-by-zero in the blend factor.
     */
    public fun Magnifier(
        lensBounds: SkRect,
        zoomAmount: SkScalar,
        inset: SkScalar,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkMagnifierImageFilter(lensBounds, zoomAmount, inset, sampling, input)

    // ─── C1.3 — Arithmetic family (Arithmetic / Blend / Merge / DropShadowOnly) ─

    /**
     * Mirrors Skia's `SkImageFilters::Arithmetic(k1, k2, k3, k4,
     * enforcePMColor, bg, fg)` — per-pixel `result = k1·src·dst +
     * k2·src + k3·dst + k4` per channel, where `src = fg` and
     * `dst = bg`. The four `k*` coefficients form a linear blend
     * with a multiplicative cross-term ; common recipes include
     * `(0, 1, -1, 0)` for "bg minus fg" and `(0, 0.5, 0.5, 0)`
     * for "average".
     *
     * `enforcePMColor = true` clamps the result to a valid
     * premultiplied colour after the formula (`max(r, g, b) ≤ a`).
     * `null bg` / `null fg` use the rasterised source as that
     * input — equivalent to upstream's "implicit source" convention.
     *
     * Output bbox = union of `bg` and `fg` bboxes.
     */
    public fun Arithmetic(
        k1: SkScalar, k2: SkScalar, k3: SkScalar, k4: SkScalar,
        enforcePMColor: Boolean = true,
        bg: SkImageFilter? = null,
        fg: SkImageFilter? = null,
    ): SkImageFilter = SkArithmeticImageFilter(k1, k2, k3, k4, enforcePMColor, bg, fg)

    /**
     * Mirrors Skia's `SkImageFilters::Blend(mode, bg, fg)` — applies
     * an [SkBlendMode] per pixel, treating `fg` as `src` and `bg`
     * as `dst`. Output bbox = union of `bg` and `fg` bboxes.
     *
     * Distinct from `Compose` (which chains filters sequentially) :
     * `Blend` evaluates two inputs in parallel and composites the
     * pair through a blend equation. Equivalent to `saveLayer` +
     * `paint.blendMode = mode` between two `drawImageRect` calls.
     */
    public fun Blend(
        mode: SkBlendMode,
        bg: SkImageFilter? = null,
        fg: SkImageFilter? = null,
    ): SkImageFilter = SkBlendImageFilter(mode, bg, fg)

    /**
     * Mirrors Skia's `SkImageFilters::Merge(filters[N])` —
     * SrcOver-composites `filters` left-to-right, so `filters[0]`
     * is the bottom layer and `filters[N-1]` is the top. Output
     * bbox = union of every input's bbox.
     *
     * `null` entries are treated as the rasterised source. Empty
     * list is equivalent to [Empty].
     */
    public fun Merge(vararg filters: SkImageFilter?): SkImageFilter =
        SkMergeImageFilter(filters.toList())

    /**
     * Mirrors Skia's `SkImageFilters::DropShadowOnly(dx, dy, σx,
     * σy, color, input)` — produces just the blurred drop shadow
     * without compositing the original [input] on top. Equivalent
     * to extracting only the shadow layer from [DropShadow].
     *
     * Same parameter semantics as [DropShadow] : `(dx, dy)` is the
     * device-space offset, `(σx, σy)` is the per-axis Gaussian
     * blur sigma, [color] is the shadow tint applied via SrcIn to
     * the blurred input alpha.
     */
    public fun DropShadowOnly(
        dx: Float, dy: Float,
        sigmaX: Float, sigmaY: Float,
        color: SkColor,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkDropShadowOnlyImageFilter(dx, dy, sigmaX, sigmaY, color, input)

    // ─── C1.4 — Morphology (Erode / Dilate) ──────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::Erode(rx, ry, input)` —
     * morphological erosion : each output pixel is the per-channel
     * **minimum** over a `(2·rx + 1) × (2·ry + 1)` rectangular
     * neighbourhood of [input]. OOB samples are treated as
     * transparent black, so the output shrinks at the input's edges.
     *
     * `rx == 0 && ry == 0` is a no-op (returns input unchanged).
     * Negative radii are coerced to 0.
     */
    public fun Erode(rx: Int, ry: Int, input: SkImageFilter? = null): SkImageFilter =
        SkMorphologyImageFilter(SkMorphologyImageFilter.Op.kErode, rx.coerceAtLeast(0), ry.coerceAtLeast(0), input)

    /**
     * Mirrors Skia's `SkImageFilters::Dilate(rx, ry, input)` —
     * morphological dilation : each output pixel is the per-channel
     * **maximum** over a `(2·rx + 1) × (2·ry + 1)` rectangular
     * neighbourhood of [input]. The output bbox is the input bbox
     * expanded by `(rx, ry)` on each side to capture the dilated
     * pixels that didn't exist in the input.
     *
     * `rx == 0 && ry == 0` is a no-op. Negative radii are coerced to 0.
     */
    public fun Dilate(rx: Int, ry: Int, input: SkImageFilter? = null): SkImageFilter =
        SkMorphologyImageFilter(SkMorphologyImageFilter.Op.kDilate, rx.coerceAtLeast(0), ry.coerceAtLeast(0), input)

    // ─── C1.5 — DisplacementMap ──────────────────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::DisplacementMap(xCh, yCh,
     * scale, displacement, color)` — for each pixel `(x, y)` in
     * the output, read the corresponding pixel from [displacement],
     * extract its [xChannelSelector] and [yChannelSelector] channels
     * as floats `c ∈ [0, 1]`, centre at zero (`c - 0.5`), multiply
     * by [scale] to get an offset `(dx, dy)`, and sample [color] at
     * `(x + dx, y + dy)`.
     *
     * `null` displacement filter ⇒ the rasterised source is used as
     * the displacement map. Same convention for [color].
     *
     * Output bbox = [color] filter's bbox. OOB samples on the
     * colour input return transparent black (kDecal).
     */
    public fun DisplacementMap(
        xChannelSelector: SkColorChannel,
        yChannelSelector: SkColorChannel,
        scale: SkScalar,
        displacement: SkImageFilter? = null,
        color: SkImageFilter? = null,
    ): SkImageFilter = SkDisplacementMapImageFilter(
        xChannelSelector, yChannelSelector, scale, displacement, color,
    )

    // ─── C1.6 — MatrixConvolution ────────────────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::MatrixConvolution(kSize, kernel,
     * gain, bias, kernelOffset, tileMode, convolveAlpha, input)` —
     * applies a 2D kernel convolution per pixel :
     *
     * ```
     * out(x, y) = gain · Σ kernel[i, j] · in(x + i - kernelOffset.x,
     *                                       y + j - kernelOffset.y)
     *           + bias
     * ```
     *
     * where the kernel is an `kernelSize.width × kernelSize.height`
     * grid of floats stored row-major in `kernel`.
     *
     * - `tileMode` — boundary mode for OOB samples (kDecal /
     *   kClamp / kRepeat / kMirror).
     * - `convolveAlpha = false` skips the alpha channel (alpha is
     *   passed through from the input). When `true`, alpha is also
     *   convolved.
     * - The convolution is applied in **non-premul** space ; bias
     *   and gain are applied per-channel before clamping back to
     *   `[0, 255]`.
     *
     * Throws if `kernel.size != kernelSize.width * kernelSize.height`.
     */
    public fun MatrixConvolution(
        kernelSize: SkISize,
        kernel: FloatArray,
        gain: SkScalar,
        bias: SkScalar,
        kernelOffset: SkIPoint,
        tileMode: SkTileMode,
        convolveAlpha: Boolean,
        input: SkImageFilter? = null,
    ): SkImageFilter {
        require(kernel.size == kernelSize.width * kernelSize.height) {
            "Kernel size mismatch : expected ${kernelSize.width * kernelSize.height} entries " +
                "for ${kernelSize.width}×${kernelSize.height} kernel, got ${kernel.size}"
        }
        return SkMatrixConvolutionImageFilter(
            kernelSize, kernel.copyOf(), gain, bias, kernelOffset,
            tileMode, convolveAlpha, input,
        )
    }

    // ─── C1.7 — Lighting (6 variants : 3 diffuse + 3 specular) ────────

    /**
     * Mirrors Skia's `SkImageFilters::DistantLitDiffuse(direction,
     * lightColor, surfaceScale, kd, input)` — Phong-Lambert diffuse
     * shading with a directional (parallel) light. The input's alpha
     * channel is treated as a height map ; the surface normal is
     * derived per-pixel via a 3×3 Sobel kernel scaled by [surfaceScale].
     *
     * Per pixel : `out_rgb = kd · max(0, N · L) · lightColor` ;
     * `out_a = max(out_r, out_g, out_b)`.
     *
     * - [direction] : unit vector pointing **toward** the light (the
     *   direction from surface to light).
     * - [surfaceScale] : multiplier on the normal's z component ;
     *   higher values make the surface appear "deeper" (steeper
     *   normals).
     * - [kd] : diffuse reflection coefficient (typical range 0..1).
     */
    public fun DistantLitDiffuse(
        direction: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        kd: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Distant(direction.normalized3()),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = kd, isDiffuse = true, shininess = 1f, input = input,
    )

    /** Point-light diffuse — same as [DistantLitDiffuse] but with a 3-D point light at [location]. */
    public fun PointLitDiffuse(
        location: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        kd: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Point(location.copyOf3()),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = kd, isDiffuse = true, shininess = 1f, input = input,
    )

    /**
     * Spot-light diffuse — point light at [location] aimed at [target],
     * with a cosine-cutoff cone of half-angle [cutoffAngle] (radians)
     * and a power-of-cosine falloff exponent [falloffExponent].
     */
    public fun SpotLitDiffuse(
        location: FloatArray,
        target: FloatArray,
        falloffExponent: SkScalar,
        cutoffAngle: SkScalar,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        kd: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Spot(location.copyOf3(), target.copyOf3(), falloffExponent, cutoffAngle),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = kd, isDiffuse = true, shininess = 1f, input = input,
    )

    /**
     * Mirrors Skia's `SkImageFilters::DistantLitSpecular(direction,
     * lightColor, surfaceScale, ks, shininess, input)` — Blinn-Phong
     * specular shading with a directional light.
     *
     * Per pixel : `out_rgb = ks · max(0, N · H)^shininess · lightColor`
     * where `H = normalize(L + V)` and `V = (0, 0, 1)` (eye looks
     * straight down at the surface) ; `out_a = max(out_r, out_g, out_b)`.
     */
    public fun DistantLitSpecular(
        direction: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        ks: SkScalar,
        shininess: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Distant(direction.normalized3()),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = ks, isDiffuse = false, shininess = shininess, input = input,
    )

    /** Point-light specular — counterpart of [DistantLitSpecular] with a point light. */
    public fun PointLitSpecular(
        location: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        ks: SkScalar,
        shininess: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Point(location.copyOf3()),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = ks, isDiffuse = false, shininess = shininess, input = input,
    )

    /** Spot-light specular — counterpart of [SpotLitDiffuse] for the Blinn-Phong specular term. */
    public fun SpotLitSpecular(
        location: FloatArray,
        target: FloatArray,
        falloffExponent: SkScalar,
        cutoffAngle: SkScalar,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        ks: SkScalar,
        shininess: SkScalar,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkLightingImageFilter(
        light = SkLight.Spot(location.copyOf3(), target.copyOf3(), falloffExponent, cutoffAngle),
        lightColor = lightColor, surfaceScale = surfaceScale,
        kdOrKs = ks, isDiffuse = false, shininess = shininess, input = input,
    )

    // ─── R2.15 — cropRect overloads ───────────────────────────────────
    //
    // Upstream `SkImageFilters.h` exposes a trailing `const CropRect&
    // cropRect = {}` parameter on most factories. Per the upstream
    // header comment (l.154-158) :
    //
    //   "The optional CropRect argument for many of the factories is
    //    equivalent to creating the filter without a CropRect and then
    //    wrapping it in ::Crop(rect, kDecal). Explicitly adding the
    //    CropRect to the filter graph allows for early bounds-querying
    //    optimisations, but otherwise the two are semantically
    //    equivalent."
    //
    // Each overload below preserves the existing factory's behaviour
    // when `cropRect == null` and wraps the result in `Crop(cropRect,
    // kDecal, filter)` otherwise. The existing (non-cropRect) overloads
    // are kept intact for backwards source-compat.
    //
    // `Blur` already exposes cropRect natively (see line 82+), so it's
    // not duplicated here.

    /** Mirrors `SkImageFilters::Offset(dx, dy, input, cropRect)`. */
    public fun Offset(
        dx: Float, dy: Float,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = Offset(dx, dy, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::ColorFilter(cf, input, cropRect)`. */
    public fun ColorFilter(
        cf: SkColorFilter,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = ColorFilter(cf, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::Compose(outer, inner, cropRect)`. */
    public fun Compose(
        outer: SkImageFilter?,
        inner: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter? {
        val filter = Compose(outer, inner)
        if (cropRect == null) return filter
        return Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::MatrixTransform(matrix, sampling, input, cropRect)`. */
    public fun MatrixTransform(
        matrix: SkMatrix,
        sampling: SkSamplingOptions,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter? {
        val filter = MatrixTransform(matrix, sampling, input)
        if (cropRect == null) return filter
        return Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::DropShadow(dx, dy, σx, σy, color, input, cropRect)`. */
    public fun DropShadow(
        dx: Float, dy: Float,
        sigmaX: Float, sigmaY: Float,
        color: SkColor,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = DropShadow(dx, dy, sigmaX, sigmaY, color, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::Magnifier(lensBounds, zoom, inset, sampling, input, cropRect)`. */
    public fun Magnifier(
        lensBounds: SkRect,
        zoomAmount: SkScalar,
        inset: SkScalar,
        sampling: SkSamplingOptions,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = Magnifier(lensBounds, zoomAmount, inset, sampling, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::Blend(mode, bg, fg, cropRect)`. */
    public fun Blend(
        mode: SkBlendMode,
        bg: SkImageFilter?,
        fg: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = Blend(mode, bg, fg)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::Erode(rx, ry, input, cropRect)`. */
    public fun Erode(
        rx: Int, ry: Int,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = Erode(rx, ry, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /** Mirrors `SkImageFilters::Dilate(rx, ry, input, cropRect)`. */
    public fun Dilate(
        rx: Int, ry: Int,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = Dilate(rx, ry, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /**
     * Mirrors `SkImageFilters::DisplacementMap(xCh, yCh, scale,
     * displacement, color, cropRect)`.
     */
    public fun DisplacementMap(
        xChannelSelector: SkColorChannel,
        yChannelSelector: SkColorChannel,
        scale: SkScalar,
        displacement: SkImageFilter?,
        color: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = DisplacementMap(xChannelSelector, yChannelSelector, scale, displacement, color)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /**
     * Mirrors `SkImageFilters::MatrixConvolution(kernelSize, kernel,
     * gain, bias, kernelOffset, tileMode, convolveAlpha, input,
     * cropRect)`.
     */
    public fun MatrixConvolution(
        kernelSize: SkISize,
        kernel: FloatArray,
        gain: SkScalar,
        bias: SkScalar,
        kernelOffset: SkIPoint,
        tileMode: SkTileMode,
        convolveAlpha: Boolean,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = MatrixConvolution(
            kernelSize, kernel, gain, bias, kernelOffset,
            tileMode, convolveAlpha, input,
        )
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /**
     * Mirrors `SkImageFilters::PointLitDiffuse(location, lightColor,
     * surfaceScale, kd, input, cropRect)`.
     */
    public fun PointLitDiffuse(
        location: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        kd: SkScalar,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = PointLitDiffuse(location, lightColor, surfaceScale, kd, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }

    /**
     * Mirrors `SkImageFilters::PointLitSpecular(location, lightColor,
     * surfaceScale, ks, shininess, input, cropRect)`.
     */
    public fun PointLitSpecular(
        location: FloatArray,
        lightColor: SkColor,
        surfaceScale: SkScalar,
        ks: SkScalar,
        shininess: SkScalar,
        input: SkImageFilter?,
        cropRect: SkRect?,
    ): SkImageFilter {
        val filter = PointLitSpecular(location, lightColor, surfaceScale, ks, shininess, input)
        return if (cropRect == null) filter else Crop(cropRect, SkTileMode.kDecal, filter)
    }
}

// -- Phase C1.7 — Lighting helpers (Float3 ops, light shapes) ----------------

/**
 * Light shape sealed hierarchy — picks how the per-pixel light
 * direction L is computed from the surface point.
 */
internal sealed class SkLight {
    /** Parallel light : L is a constant 3-D unit vector. */
    internal class Distant(val dir: FloatArray) : SkLight()

    /** Point light at [location] : `L(p) = normalize(location - p)`. */
    internal class Point(val location: FloatArray) : SkLight()

    /**
     * Spot light : point light at [location] aimed at [target] with a
     * cosine-cutoff cone of half-angle [cutoffAngle] radians and a
     * cosine-power falloff [falloffExponent].
     */
    internal class Spot(
        val location: FloatArray,
        val target: FloatArray,
        val falloffExponent: Float,
        val cutoffAngle: Float,
    ) : SkLight()
}

/** Returns a copy of the (3-D) [this] array, padded with zeros if shorter. */
internal fun FloatArray.copyOf3(): FloatArray {
    val out = FloatArray(3)
    for (i in 0 until minOf(3, size)) out[i] = this[i]
    return out
}

/** Returns a 3-D unit-length copy of [this] ; defaults to (0, 0, 1) if zero-length. */
internal fun FloatArray.normalized3(): FloatArray {
    val v = copyOf3()
    val mag2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2]
    if (mag2 < 1e-12f) {
        return floatArrayOf(0f, 0f, 1f)
    }
    val invMag = 1f / kotlin.math.sqrt(mag2)
    v[0] *= invMag; v[1] *= invMag; v[2] *= invMag
    return v
}

// -- Internal concrete implementations --------------------------------------

/**
 * `Offset` — translates the (possibly chained) input by `(dx, dy)`
 * device pixels. The displacement scales with the canvas's max
 * scale (so a 10-px offset stays 10 px under different CTMs from
 * the device's perspective). Image pixels themselves are unchanged.
 */
internal class SkOffsetImageFilter(
    private val dx: Float,
    private val dy: Float,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-offset -- read-only views for backend
    // extractors. Same pattern as [SkBlurImageFilter.exposedSigmaX] :
    // keeps the concrete class internal while letting GPU backends
    // introspect the (dx, dy, input) tuple they fold into the layer
    // composite uniform.
    internal val exposedDx: Float get() = dx
    internal val exposedDy: Float get() = dy
    internal val exposedInput: SkImageFilter? get() = input
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        val sx = (dx * scale + 0.5f).toInt()
        val sy = (dy * scale + 0.5f).toInt()
        return FilterResult(
            image = upstream.image,
            offsetX = upstream.offsetX + sx,
            offsetY = upstream.offsetY + sy,
        )
    }

    /**
     * Phase R1-C — translate the upstream bounds by (dx, dy). Mirrors
     * Skia's `SkOffsetImageFilter::onFilterNodeBounds`
     * (`src/effects/imagefilters/SkOffsetImageFilter.cpp`).
     */
    override fun computeFastBounds(src: org.graphiks.math.SkRect): org.graphiks.math.SkRect {
        val base = input?.computeFastBounds(src) ?: src
        return org.graphiks.math.SkRect.MakeLTRB(
            base.left + dx, base.top + dy,
            base.right + dx, base.bottom + dy,
        )
    }
}

/**
 * `ColorFilter` — applies [cf] to each pixel of the (possibly chained)
 * input image. Allocates a new [SkImage] sized to the input ; the
 * filter math runs in non-premul `SkColor` space (Phase 7d.1 ;
 * Phase 7e' linear-sRGB wrapper not extended here yet).
 */
internal class SkColorFilterImageFilter(
    private val cf: SkColorFilter,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter -- read-only views for backend
    // extractors. See `SkBlurImageFilter.exposedSigmaX` for the
    // pattern rationale.
    internal val exposedColorFilter: SkColorFilter get() = cf
    internal val exposedInput: SkImageFilter? get() = input
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val w = srcImg.width
        val h = srcImg.height
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val px = srcImg.peekPixel(x, y)
                outPixels[rowOff + x] = cf.filterColor(px)
            }
        }
        return FilterResult(
            image = SkImage(w, h, outPixels),
            offsetX = upstream.offsetX,
            offsetY = upstream.offsetY,
        )
    }
}

/**
 * `Compose` — chained `outer(inner(src))`. The combined offset is
 * `inner.offset + outer.offset` (with [outer] applied to [inner]'s
 * output image, so its own offset stacks on top).
 */
internal class SkComposeImageFilter(
    private val outer: SkImageFilter,
    private val inner: SkImageFilter,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-compose -- read-only views for backend
    // extractors. Same pattern as [SkBlurImageFilter.exposedSigmaX] /
    // [SkColorFilterImageFilter.exposedColorFilter] : keeps the concrete
    // class internal while letting backends walk the filter tree.
    internal val exposedOuter: SkImageFilter get() = outer
    internal val exposedInner: SkImageFilter get() = inner
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val midResult = inner.filterImage(src, ctm)
        val outResult = outer.filterImage(midResult.image, ctm)
        return FilterResult(
            image = outResult.image,
            offsetX = midResult.offsetX + outResult.offsetX,
            offsetY = midResult.offsetY + outResult.offsetY,
        )
    }

    /**
     * Phase R1-C — `outer(inner(src))` bounds. Mirrors Skia's
     * `SkComposeImageFilter::computeFastBounds`
     * (`src/effects/imagefilters/SkComposeImageFilter.cpp`).
     */
    override fun computeFastBounds(src: org.graphiks.math.SkRect): org.graphiks.math.SkRect =
        outer.computeFastBounds(inner.computeFastBounds(src))
}

// -- Phase 7d.2 ----------------------------------------------------------

internal class SkBlurImageFilter(
    private val sigmaX: Float,
    private val sigmaY: Float,
    private val tileMode: SkTileMode,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter -- read-only views for backend
    // extractors. Same pattern as `SkBlendColorFilter.exposedColour` /
    // `SkMatrixColorFilter.exposedMatrix` : keeps the concrete class
    // internal while letting backends introspect the parameters they
    // can express in their fragment-side pipeline.
    internal val exposedSigmaX: Float get() = sigmaX
    internal val exposedSigmaY: Float get() = sigmaY
    internal val exposedTileMode: SkTileMode get() = tileMode
    internal val exposedInput: SkImageFilter? get() = input
    // Legacy 3-arg constructor for internal call sites that pre-date G6.
    internal constructor(sigmaX: Float, sigmaY: Float, input: SkImageFilter?) :
        this(sigmaX, sigmaY, SkTileMode.kDecal, input)

    private val radiusX: Int = kotlin.math.ceil(3.0 * sigmaX).toInt().coerceAtLeast(0)
    private val radiusY: Int = kotlin.math.ceil(3.0 * sigmaY).toInt().coerceAtLeast(0)
    private val kernelX: FloatArray = gaussianKernel1D(sigmaX, radiusX)
    private val kernelY: FloatArray = gaussianKernel1D(sigmaY, radiusY)

    /**
     * Phase R1-C — inflate the upstream bounds by `±radius` per axis
     * (the kernel's `3σ` extent). Mirrors Skia's
     * `SkBlurImageFilter::onFilterNodeBounds`
     * (`src/effects/imagefilters/SkBlurImageFilter.cpp`). Used by
     * `gm/filterfastbounds.cpp`.
     */
    override fun computeFastBounds(src: org.graphiks.math.SkRect): org.graphiks.math.SkRect {
        val base = input?.computeFastBounds(src) ?: src
        return org.graphiks.math.SkRect.MakeLTRB(
            base.left - radiusX, base.top - radiusY,
            base.right + radiusX, base.bottom + radiusY,
        )
    }

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val srcW = srcImg.width; val srcH = srcImg.height
        if (radiusX == 0 && radiusY == 0) return upstream
        // Under kClamp / kRepeat / kMirror the kernel "fills in" beyond
        // the input's edges, so growing the output by ±radius doesn't
        // bring any extra information — keep the output the same size as
        // the input. Only kDecal needs the extra band to capture the
        // alpha falloff at the edges.
        val growX = if (tileMode == SkTileMode.kDecal) radiusX else 0
        val growY = if (tileMode == SkTileMode.kDecal) radiusY else 0
        val outW = srcW + 2 * growX
        val outH = srcH + 2 * growY
        val tmp = IntArray(outW * srcH)
        for (y in 0 until srcH) for (xOut in 0 until outW) {
            tmp[y * outW + xOut] = sampleH(srcImg, xOut - growX, y, kernelX, radiusX)
        }
        val outBuf = IntArray(outW * outH)
        for (yOut in 0 until outH) for (xOut in 0 until outW) {
            outBuf[yOut * outW + xOut] = sampleV(tmp, outW, srcH, xOut, yOut - growY, kernelY, radiusY)
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX - growX, upstream.offsetY - growY)
    }

    private fun sampleH(src: SkImage, cx: Int, cy: Int, kernel: FloatArray, radius: Int): Int {
        var aF = 0f; var rF = 0f; var gF = 0f; var bF = 0f
        for (k in -radius..radius) {
            val sx = cx + k
            val px = sampleImageWithTileMode(src, sx, cy, tileMode)
            val w = kernel[k + radius]
            val a = ((px ushr 24) and 0xFF) / 255f
            aF += a * w; rF += ((px ushr 16) and 0xFF) / 255f * a * w
            gF += ((px ushr 8) and 0xFF) / 255f * a * w; bF += (px and 0xFF) / 255f * a * w
        }
        return packPremulFloat(aF, rF, gF, bF)
    }

    private fun sampleV(tmp: IntArray, w: Int, h: Int, cx: Int, cy: Int, kernel: FloatArray, radius: Int): Int {
        var aF = 0f; var rF = 0f; var gF = 0f; var bF = 0f
        for (k in -radius..radius) {
            val sy = cy + k
            val px = sampleTmpWithTileMode(tmp, w, h, cx, sy, tileMode)
            val ws = kernel[k + radius]
            val a = ((px ushr 24) and 0xFF) / 255f
            aF += a * ws; rF += ((px ushr 16) and 0xFF) / 255f * a * ws
            gF += ((px ushr 8) and 0xFF) / 255f * a * ws; bF += (px and 0xFF) / 255f * a * ws
        }
        return packPremulFloat(aF, rF, gF, bF)
    }

    private fun sampleTmpWithTileMode(
        tmp: IntArray, w: Int, h: Int, cx: Int, cy: Int, mode: SkTileMode,
    ): Int {
        if (cx in 0 until w && cy in 0 until h) return tmp[cy * w + cx]
        if (w == 0 || h == 0) return 0
        return when (mode) {
            SkTileMode.kDecal -> 0
            SkTileMode.kClamp -> tmp[cy.coerceIn(0, h - 1) * w + cx.coerceIn(0, w - 1)]
            SkTileMode.kRepeat -> tmp[positiveModInternal(cy, h) * w + positiveModInternal(cx, w)]
            SkTileMode.kMirror -> tmp[mirrorModInternal(cy, h) * w + mirrorModInternal(cx, w)]
        }
    }

    private fun positiveModInternal(n: Int, m: Int): Int { val r = n % m; return if (r < 0) r + m else r }
    private fun mirrorModInternal(n: Int, m: Int): Int {
        if (m == 0) return 0
        val twoM = 2 * m
        var r = n % twoM; if (r < 0) r += twoM
        return if (r < m) r else twoM - 1 - r
    }

    private fun packPremulFloat(aF: Float, rF: Float, gF: Float, bF: Float): Int {
        val outA = (aF * 255f + 0.5f).toInt().coerceIn(0, 255)
        if (outA == 0) return 0
        val invA = 1f / aF
        val outR = (rF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (gF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (bF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private companion object {
        fun gaussianKernel1D(sigma: Float, radius: Int): FloatArray {
            if (radius == 0) return floatArrayOf(1f)
            val size = 2 * radius + 1
            val k = FloatArray(size)
            val twoSigmaSq = 2f * sigma * sigma
            var sum = 0f
            for (i in 0 until size) {
                val x = (i - radius).toFloat()
                val v = kotlin.math.exp(-(x * x) / twoSigmaSq.toDouble()).toFloat()
                k[i] = v; sum += v
            }
            for (i in 0 until size) k[i] /= sum
            return k
        }
    }
}

internal class SkMatrixTransformImageFilter(
    private val matrix: SkMatrix,
    private val sampling: SkSamplingOptions,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-matrixTransform -- read-only views
    // for backend extractors. Same pattern as `SkBlurImageFilter
    // .exposedSigmaX` / `SkOffsetImageFilter.exposedDx` : keeps the
    // concrete class internal while letting GPU backends introspect the
    // (matrix, sampling, input) tuple they fold into the layer composite
    // uniform's 2x3 affine slot.
    internal val exposedMatrix: SkMatrix get() = matrix
    internal val exposedSampling: SkSamplingOptions get() = sampling
    internal val exposedInput: SkImageFilter? get() = input
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val mappedRect = matrix.mapRect(
            org.graphiks.math.SkRect.MakeWH(srcImg.width.toFloat(), srcImg.height.toFloat())
        )
        val outLeft = kotlin.math.floor(mappedRect.left.toDouble()).toInt()
        val outTop = kotlin.math.floor(mappedRect.top.toDouble()).toInt()
        val outRight = kotlin.math.ceil(mappedRect.right.toDouble()).toInt()
        val outBottom = kotlin.math.ceil(mappedRect.bottom.toDouble()).toInt()
        val outW = (outRight - outLeft).coerceAtLeast(1)
        val outH = (outBottom - outTop).coerceAtLeast(1)
        val invMatrix = matrix.invert() ?: return upstream
        val outBuf = IntArray(outW * outH)
        for (y in 0 until outH) for (x in 0 until outW) {
            val (sx, sy) = invMatrix.mapXY(outLeft + x + 0.5f, outTop + y + 0.5f)
            outBuf[y * outW + x] = sample(srcImg, sx, sy)
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX + outLeft, upstream.offsetY + outTop)
    }

    private fun sample(img: SkImage, sx: Float, sy: Float): Int = when (sampling.filter) {
        SkFilterMode.kNearest -> {
            val ix = kotlin.math.floor(sx.toDouble()).toInt()
            val iy = kotlin.math.floor(sy.toDouble()).toInt()
            if (ix in 0 until img.width && iy in 0 until img.height) img.peekPixel(ix, iy) else 0
        }
        SkFilterMode.kLinear -> {
            val fx = sx - 0.5f; val fy = sy - 0.5f
            val ix0 = kotlin.math.floor(fx.toDouble()).toInt()
            val iy0 = kotlin.math.floor(fy.toDouble()).toInt()
            val tx = (fx - kotlin.math.floor(fx.toDouble()).toFloat()).coerceIn(0f, 1f)
            val ty = (fy - kotlin.math.floor(fy.toDouble()).toFloat()).coerceIn(0f, 1f)
            bilerp(peek(img, ix0, iy0), peek(img, ix0 + 1, iy0),
                peek(img, ix0, iy0 + 1), peek(img, ix0 + 1, iy0 + 1), tx, ty)
        }
    }

    private fun peek(img: SkImage, x: Int, y: Int): Int =
        if (x in 0 until img.width && y in 0 until img.height) img.peekPixel(x, y) else 0

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val itx = 1f - tx; val ity = 1f - ty
        val w00 = itx * ity; val w10 = tx * ity; val w01 = itx * ty; val w11 = tx * ty
        fun ch(shift: Int): Int {
            val v = ((c00 ushr shift) and 0xFF) * w00 + ((c10 ushr shift) and 0xFF) * w10 +
                ((c01 ushr shift) and 0xFF) * w01 + ((c11 ushr shift) and 0xFF) * w11
            return (v + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

internal class SkDropShadowImageFilter(
    private val dx: Float, private val dy: Float,
    private val sigmaX: Float, private val sigmaY: Float,
    private val color: SkColor,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-dropshadow -- read-only views for
    // backend extractors. Mirrors [SkBlurImageFilter.exposedSigmaX] :
    // keeps the concrete class internal while letting GPU backends
    // introspect the (dx, dy, sigmaX, sigmaY, color, input) tuple they
    // need to render the shadow + composite the original.
    internal val exposedDx: Float get() = dx
    internal val exposedDy: Float get() = dy
    internal val exposedSigmaX: Float get() = sigmaX
    internal val exposedSigmaY: Float get() = sigmaY
    internal val exposedColor: SkColor get() = color
    internal val exposedInput: SkImageFilter? get() = input
    /**
     * Phase R1-C — output covers `src ∪ (src offset by (dx, dy) and
     * inflated by ±3σ)`. Mirrors Skia's
     * `SkDropShadowImageFilter::onFilterNodeBounds`
     * (`src/effects/imagefilters/SkDropShadowImageFilter.cpp`).
     */
    override fun computeFastBounds(src: org.graphiks.math.SkRect): org.graphiks.math.SkRect {
        val base = input?.computeFastBounds(src) ?: src
        val rx = kotlin.math.ceil(3.0 * sigmaX).toFloat()
        val ry = kotlin.math.ceil(3.0 * sigmaY).toFloat()
        val sl = base.left + dx - rx
        val st = base.top + dy - ry
        val sr = base.right + dx + rx
        val sb = base.bottom + dy + ry
        return org.graphiks.math.SkRect.MakeLTRB(
            minOf(base.left, sl), minOf(base.top, st),
            maxOf(base.right, sr), maxOf(base.bottom, sb),
        )
    }

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val tinted = SkColorFilterImageFilter(SkColorFilters.Blend(color, SkBlendMode.kSrcIn), null)
            .filterImage(srcImg, ctm).image
        val blurred = SkBlurImageFilter(sigmaX, sigmaY, null).filterImage(tinted, ctm)
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        val sdx = (dx * scale + 0.5f).toInt(); val sdy = (dy * scale + 0.5f).toInt()
        val shadowImg = blurred.image
        val sox = blurred.offsetX + sdx; val soy = blurred.offsetY + sdy
        val uL = minOf(0, sox); val uT = minOf(0, soy)
        val uR = maxOf(srcImg.width, sox + shadowImg.width)
        val uB = maxOf(srcImg.height, soy + shadowImg.height)
        val outW = uR - uL; val outH = uB - uT
        val outBuf = IntArray(outW * outH)
        for (y in 0 until shadowImg.height) {
            val dY = soy + y - uT; if (dY !in 0 until outH) continue
            for (x in 0 until shadowImg.width) {
                val dX = sox + x - uL; if (dX !in 0 until outW) continue
                outBuf[dY * outW + dX] = shadowImg.peekPixel(x, y)
            }
        }
        for (y in 0 until srcImg.height) {
            val dY = y - uT; if (dY !in 0 until outH) continue
            for (x in 0 until srcImg.width) {
                val dX = x - uL; if (dX !in 0 until outW) continue
                val sp = srcImg.peekPixel(x, y); val sa = (sp ushr 24) and 0xFF
                if (sa == 0xFF) outBuf[dY * outW + dX] = sp
                else if (sa > 0) outBuf[dY * outW + dX] = srcOver(sp, outBuf[dY * outW + dX])
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX + uL, upstream.offsetY + uT)
    }

    private fun srcOver(src: Int, dst: Int): Int {
        val sa = (src ushr 24) and 0xFF; if (sa == 0) return dst
        val da = (dst ushr 24) and 0xFF; val invSa = 255 - sa
        val outA = sa + (da * invSa + 127) / 255; if (outA == 0) return 0
        val sr = (src ushr 16) and 0xFF; val sg = (src ushr 8) and 0xFF; val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF; val dg = (dst ushr 8) and 0xFF; val db = dst and 0xFF
        val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
        val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
        val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
        return (outA shl 24) or (outR.coerceIn(0, 255) shl 16) or
            (outG.coerceIn(0, 255) shl 8) or outB.coerceIn(0, 255)
    }
}

// -- C1.1 source / passthrough wrappers ------------------------------------

/**
 * `Image` — wraps a static [SkImage] as the filter input. The
 * `src` arg to [filterImage] is ignored — this filter generates
 * its own source from the image-pixel data.
 *
 * Output is sized to [dstRect] ; pixels are sampled from
 * [srcRect] of [image] via [sampling]. Output offset is
 * `(dstRect.left, dstRect.top)` so the filter result lands at
 * the correct device-space position.
 *
 * Fast path : when `srcRect == image bounds && dstRect == image
 * bounds && sampling == kNearest`, the image is returned as-is
 * (no sampling allocation).
 */
internal class SkImageImageFilter(
    private val image: SkImage,
    private val srcRect: SkRect,
    private val dstRect: SkRect,
    private val sampling: SkSamplingOptions,
) : SkImageFilter() {

    /**
     * Phase R1-C — image source filters always output the
     * [dstRect] regardless of the input bounds. Mirrors Skia's
     * `SkImageImageFilter::onFilterNodeBounds`
     * (`src/effects/imagefilters/SkImageImageFilter.cpp`).
     */
    override fun computeFastBounds(src: SkRect): SkRect = dstRect

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val outW = kotlin.math.max(1, kotlin.math.ceil(dstRect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(dstRect.height().toDouble()).toInt())
        val outOffX = kotlin.math.floor(dstRect.left.toDouble()).toInt()
        val outOffY = kotlin.math.floor(dstRect.top.toDouble()).toInt()

        // Identity-shape fast path : the most common case (Image(image)
        // with no sub-rect remapping) skips the per-pixel sample loop.
        val fullSrc = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        if (srcRect == fullSrc && dstRect == fullSrc) {
            return FilterResult(image, outOffX, outOffY)
        }

        // General path : sample srcRect → dstRect via the requested
        // SkSamplingOptions filter mode. Identical math to
        // SkMatrixTransformImageFilter's bilerp / nearest, just
        // anchored on the dst-to-src remap implied by the two rects.
        val outBuf = IntArray(outW * outH)
        val sx = srcRect.width() / dstRect.width()
        val sy = srcRect.height() / dstRect.height()
        for (y in 0 until outH) {
            val srcY = srcRect.top + (y + 0.5f) * sy
            for (x in 0 until outW) {
                val srcX = srcRect.left + (x + 0.5f) * sx
                outBuf[y * outW + x] = sample(image, srcX, srcY)
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outOffX, outOffY)
    }

    private fun sample(img: SkImage, sx: Float, sy: Float): Int = when (sampling.filter) {
        SkFilterMode.kNearest -> {
            val ix = kotlin.math.floor(sx.toDouble()).toInt()
            val iy = kotlin.math.floor(sy.toDouble()).toInt()
            if (ix in 0 until img.width && iy in 0 until img.height) img.peekPixel(ix, iy) else 0
        }
        SkFilterMode.kLinear -> {
            val fx = sx - 0.5f; val fy = sy - 0.5f
            val ix0 = kotlin.math.floor(fx.toDouble()).toInt()
            val iy0 = kotlin.math.floor(fy.toDouble()).toInt()
            val tx = (fx - kotlin.math.floor(fx.toDouble()).toFloat()).coerceIn(0f, 1f)
            val ty = (fy - kotlin.math.floor(fy.toDouble()).toFloat()).coerceIn(0f, 1f)
            bilerp(peek(img, ix0, iy0), peek(img, ix0 + 1, iy0),
                peek(img, ix0, iy0 + 1), peek(img, ix0 + 1, iy0 + 1), tx, ty)
        }
    }

    private fun peek(img: SkImage, x: Int, y: Int): Int =
        if (x in 0 until img.width && y in 0 until img.height) img.peekPixel(x, y) else 0

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val itx = 1f - tx; val ity = 1f - ty
        val w00 = itx * ity; val w10 = tx * ity; val w01 = itx * ty; val w11 = tx * ty
        fun ch(shift: Int): Int {
            val v = ((c00 ushr shift) and 0xFF) * w00 + ((c10 ushr shift) and 0xFF) * w10 +
                ((c01 ushr shift) and 0xFF) * w01 + ((c11 ushr shift) and 0xFF) * w11
            return (v + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * `Picture` — replays a recorded [SkPicture] into a bitmap of
 * [targetRect]'s size, returns that bitmap as the filter input.
 * Mirrors upstream's `SkImageFilters::Picture`. The picture is
 * rendered at draw time (each `filterImage` call replays it
 * fresh — Skia does the same and clients are expected to wrap
 * with [Compose] etc. only when they want the replay
 * memoised).
 */
internal class SkPictureImageFilter(
    private val pic: SkPicture,
    private val targetRect: SkRect,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val outW = kotlin.math.max(1, kotlin.math.ceil(targetRect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(targetRect.height().toDouble()).toInt())
        // Allocate a fresh raster bitmap and replay the picture into
        // it, translated so the targetRect's top-left lands at (0, 0)
        // in the bitmap. The bitmap's color space defaults to sRGB ;
        // pictures recorded against a non-sRGB working space will
        // need a callsite that re-tags appropriately (B2.4-style
        // future enhancement).
        val bitmap = SkBitmap(outW, outH)
        val canvas = SkCanvas(bitmap)
        canvas.translate(-targetRect.left, -targetRect.top)
        pic.playback(canvas)
        return FilterResult(
            image = bitmap.asImage(),
            offsetX = kotlin.math.floor(targetRect.left.toDouble()).toInt(),
            offsetY = kotlin.math.floor(targetRect.top.toDouble()).toInt(),
        )
    }
}

/**
 * `Shader` — fills a buffer sized to the input `src`'s dimensions
 * with the shader's per-pixel output. Effectively the equivalent
 * of `drawPaint(SkPaint().apply { shader = … })` into a fresh
 * bitmap. The shader is sampled at pixel centres in the input's
 * local coordinate space.
 *
 * Output offset is `(0, 0)` — the shader fills the whole input
 * bbox, so the result aligns with `src`.
 */
internal class SkShaderImageFilter(
    private val shader: SkShader,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val w = src.width
        val h = src.height
        val bitmap = SkBitmap(w, h)
        // Drive the shader through SkBitmapDevice.drawPaint so it
        // sees the same pipeline (color-space xform, premul) as the
        // raster sinks. Identity CTM ; the shader's localMatrix
        // handles its own geometry.
        val device = SkBitmapDevice(bitmap)
        val paint = SkPaint().apply { this.shader = this@SkShaderImageFilter.shader }
        device.drawPaint(SkMatrix.Identity, org.graphiks.math.SkIRect.MakeWH(w, h), paint)
        return FilterResult(bitmap.asImage(), 0, 0)
    }
}

/**
 * `Empty` — placeholder filter returning a 1×1 transparent-black
 * image. Used as a default/initial value in `Merge` / `Compose`
 * chains under construction, and to test that downstream filters
 * handle fully-transparent inputs correctly.
 *
 * Singleton — there's no per-instance state.
 */
internal object SkEmptyImageFilter : SkImageFilter() {
    private val EMPTY_IMAGE = SkImage(1, 1, IntArray(1))
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult =
        FilterResult(EMPTY_IMAGE, 0, 0)
}

/**
 * `Crop` — constrain [input]'s output to [rect], with [tileMode]
 * dictating how out-of-rect samples are treated.
 *
 * Implementation : run [input] (or use `src` if input is null),
 * then walk the rect-sized output buffer ; for each output pixel,
 * the corresponding input pixel is either inside upstream's image
 * bounds (direct copy), or out-of-bounds (apply [tileMode] to
 * find the source pixel — clamp / repeat / mirror — or return
 * transparent for kDecal).
 *
 * Output bounds : [rect]. Output offset matches [rect]'s top-left.
 */
internal class SkCropImageFilter(
    private val rect: SkRect,
    private val tileMode: SkTileMode,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-crop -- read-only views for backend
    // extractors. Same pattern as `SkBlurImageFilter.exposedSigmaX`.
    internal val exposedRect: SkRect get() = rect
    internal val exposedTileMode: SkTileMode get() = tileMode
    internal val exposedInput: SkImageFilter? get() = input

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val srcOffX = upstream.offsetX
        val srcOffY = upstream.offsetY

        val outW = kotlin.math.max(1, kotlin.math.ceil(rect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(rect.height().toDouble()).toInt())
        val outOffX = kotlin.math.floor(rect.left.toDouble()).toInt()
        val outOffY = kotlin.math.floor(rect.top.toDouble()).toInt()
        val outBuf = IntArray(outW * outH)

        for (y in 0 until outH) {
            // Map output (x, y) to upstream image coords.
            // Output pixel (x, y) sits at device pos (outOffX + x, outOffY + y).
            // The upstream image's pixel (sx, sy) sits at device pos (srcOffX + sx, srcOffY + sy).
            // So sx = outOffX + x - srcOffX ; sy similarly.
            val sy = outOffY + y - srcOffY
            for (x in 0 until outW) {
                val sx = outOffX + x - srcOffX
                outBuf[y * outW + x] = sampleWithTileMode(srcImg, sx, sy)
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outOffX, outOffY)
    }

    private fun sampleWithTileMode(img: SkImage, sx: Int, sy: Int): Int =
        sampleImageWithTileMode(img, sx, sy, tileMode)
}

// -- C1.2 Tile + Magnifier ---------------------------------------------------

/**
 * `Tile` — replicates [input] sampled in [src] across [dst], with
 * implicit kRepeat tiling. Mirrors upstream Skia's
 * `SkImageFilters::Tile(src, dst, input)`.
 *
 * Output dimensions match `dst`. For each output pixel `(x, y)` in
 * the dst rect, we map back to a `(sx, sy)` in the src rect via
 * `sx = src.left + ((x - dst.left) * src.width / dst.width) mod
 * src.width`, then sample the upstream filter result at that
 * coordinate. The wrap-around uses the [positiveMod] helper shared
 * with [SkCropImageFilter]'s kRepeat path.
 *
 * Same identity-source convention as the rest of the family : `null`
 * input means "tile the rasterised source directly".
 */
internal class SkTileImageFilter(
    private val src: SkRect,
    private val dst: SkRect,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-tile -- read-only views for backend
    // extractors.
    internal val exposedSrc: SkRect get() = src
    internal val exposedDst: SkRect get() = dst
    internal val exposedInput: SkImageFilter? get() = input

    override fun filterImage(srcImg: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(srcImg, ctm) ?: FilterResult(srcImg, 0, 0)
        val upImg = upstream.image
        val upOffX = upstream.offsetX
        val upOffY = upstream.offsetY

        val outW = kotlin.math.max(1, kotlin.math.ceil(dst.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(dst.height().toDouble()).toInt())
        val outOffX = kotlin.math.floor(dst.left.toDouble()).toInt()
        val outOffY = kotlin.math.floor(dst.top.toDouble()).toInt()

        val srcW = kotlin.math.max(0, kotlin.math.ceil(src.width().toDouble()).toInt())
        val srcH = kotlin.math.max(0, kotlin.math.ceil(src.height().toDouble()).toInt())
        // Degenerate src ⇒ nothing to tile from. Fill with transparent
        // black, output sized to dst (matches upstream's "empty tile"
        // semantic).
        if (srcW == 0 || srcH == 0) {
            return FilterResult(SkImage(outW, outH, IntArray(outW * outH)), outOffX, outOffY)
        }

        // **Pure repeat tiling** : output (x, y) at dst-relative pos
        // (rx, ry) = (x, y) maps to src-relative pos
        // `(rx mod srcW, ry mod srcH)`. The src-relative pos is then
        // anchored at `src.origin` for the upstream lookup. No
        // scaling — `Tile` matches upstream's "tile across dst"
        // semantic, which is implicit kRepeat with no zoom.
        val srcLeftI = kotlin.math.floor(src.left.toDouble()).toInt()
        val srcTopI = kotlin.math.floor(src.top.toDouble()).toInt()
        val outBuf = IntArray(outW * outH)
        for (y in 0 until outH) {
            val syRel = positiveMod(y, srcH)
            val absSrcY = srcTopI + syRel
            val upY = absSrcY - upOffY
            for (x in 0 until outW) {
                val sxRel = positiveMod(x, srcW)
                val absSrcX = srcLeftI + sxRel
                val upX = absSrcX - upOffX
                outBuf[y * outW + x] = if (upX in 0 until upImg.width && upY in 0 until upImg.height)
                    upImg.peekPixel(upX, upY) else 0
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outOffX, outOffY)
    }
}

/**
 * `Magnifier` — radial-ish lens distortion. Mirrors upstream Skia's
 * `SkImageFilters::Magnifier(lensBounds, zoomAmount, inset, sampling, input)`.
 *
 * Algorithm per pixel `p = (x, y)` :
 *  - Outside [lensBounds] : pass through `input.eval(p)`.
 *  - Inside [lensBounds] : sample `input.eval(lensBounds.center +
 *    (p - lensBounds.center) / zoom)` — i.e. zoom into the lens
 *    centre by [zoomAmount]. The closer to the lens edge, the
 *    closer the sample is to `p` itself (smooth blend over a band
 *    of width [inset]).
 *
 * The smooth blend formula matches upstream Skia : for each pixel
 * `inside`, `t = clamp(min_distance_from_edge / inset, 0, 1)`
 * (distance to the nearest lens edge, normalised by the inset
 * width), then the sample coord is `lerp(p, magnified_p, t)`. So
 * exactly at the edge `t = 0` ⇒ pass-through ; in the centre
 * `t = 1` ⇒ full magnification.
 *
 * `sampling` is plumbed for source-compat ; the C1.2 implementation
 * uses kNearest. A future bilerp pass can swap in if a GM demands
 * sub-pixel precision.
 */
internal class SkMagnifierImageFilter(
    private val lensBounds: SkRect,
    private val zoom: SkScalar,
    private val inset: SkScalar,
    @Suppress("unused") private val sampling: SkSamplingOptions,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    // Phase G-saveLayer-imageFilter-magnifier -- read-only views for
    // backend extractors.
    internal val exposedLensBounds: SkRect get() = lensBounds
    internal val exposedZoom: SkScalar get() = zoom
    internal val exposedInset: SkScalar get() = inset
    internal val exposedInput: SkImageFilter? get() = input

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val upImg = upstream.image
        val upOffX = upstream.offsetX
        val upOffY = upstream.offsetY
        val outW = upImg.width
        val outH = upImg.height
        if (outW == 0 || outH == 0 || zoom <= 0f) return upstream

        val cx = (lensBounds.left + lensBounds.right) * 0.5f
        val cy = (lensBounds.top + lensBounds.bottom) * 0.5f
        val invZoom = 1f / zoom
        val insetSafe = inset.coerceAtLeast(0.0001f) // avoid div-by-0

        val outBuf = IntArray(outW * outH)
        for (y in 0 until outH) {
            // Map output (x, y) to device-space pos via upstream offset.
            val devY = (y + upOffY).toFloat()
            for (x in 0 until outW) {
                val devX = (x + upOffX).toFloat()
                val pixel = if (devX < lensBounds.left || devX >= lensBounds.right ||
                    devY < lensBounds.top || devY >= lensBounds.bottom
                ) {
                    // Outside the lens — passthrough.
                    upImg.peekPixel(x, y)
                } else {
                    // Inside the lens — compute the distance to the
                    // nearest lens edge for the inset blend factor.
                    val dLeft = devX - lensBounds.left
                    val dRight = lensBounds.right - devX
                    val dTop = devY - lensBounds.top
                    val dBottom = lensBounds.bottom - devY
                    val minEdgeDist = minOf(dLeft, dRight, dTop, dBottom)
                    val t = (minEdgeDist / insetSafe).coerceIn(0f, 1f)
                    // Sample coord lerped between identity and full magnification.
                    // Full magnification : sampleX = cx + (devX - cx) * invZoom.
                    val magX = cx + (devX - cx) * invZoom
                    val magY = cy + (devY - cy) * invZoom
                    val sampleX = devX + (magX - devX) * t
                    val sampleY = devY + (magY - devY) * t
                    // Translate back to upstream local coords + nearest sample.
                    val sxLocal = (sampleX - upOffX + 0.5f).toInt()
                    val syLocal = (sampleY - upOffY + 0.5f).toInt()
                    if (sxLocal in 0 until outW && syLocal in 0 until outH) {
                        upImg.peekPixel(sxLocal, syLocal)
                    } else {
                        0
                    }
                }
                outBuf[y * outW + x] = pixel
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), upOffX, upOffY)
    }
}

// -- Shared tile-mode helpers (factored from SkCropImageFilter for C1.2) ----

/** Sample [img] at integer coords with the given [tileMode] for OOB. */
private fun sampleImageWithTileMode(
    img: SkImage,
    sx: Int,
    sy: Int,
    tileMode: SkTileMode,
): Int {
    val w = img.width
    val h = img.height
    if (w == 0 || h == 0) return 0
    if (sx in 0 until w && sy in 0 until h) return img.peekPixel(sx, sy)
    return when (tileMode) {
        SkTileMode.kDecal -> 0
        SkTileMode.kClamp -> img.peekPixel(sx.coerceIn(0, w - 1), sy.coerceIn(0, h - 1))
        SkTileMode.kRepeat -> img.peekPixel(positiveMod(sx, w), positiveMod(sy, h))
        SkTileMode.kMirror -> img.peekPixel(mirrorMod(sx, w), mirrorMod(sy, h))
    }
}

/** Mathematical modulo : `((n % m) + m) % m` so result is in `[0, m)`. */
private fun positiveMod(n: Int, m: Int): Int {
    val r = n % m
    return if (r < 0) r + m else r
}

/**
 * Mirror-mod : period = `2*m`, where `[0, m)` is direct and `[m,
 * 2m)` mirrors back. Matches upstream Skia's `kMirror` tile mode
 * for raster shaders.
 */
private fun mirrorMod(n: Int, m: Int): Int {
    if (m == 0) return 0
    val twoM = 2 * m
    var r = n % twoM
    if (r < 0) r += twoM
    return if (r < m) r else twoM - 1 - r
}

// -- C1.3 Arithmetic family -----------------------------------------------

/**
 * Common helper for filters that combine two filter inputs (`bg`
 * and `fg`) with a per-pixel formula. Allocates an output sized to
 * the union of both bboxes, walks every pixel, and calls [combine]
 * with the two pre-aligned RGBA quads. Out-of-bounds samples for
 * either input default to transparent black.
 */
private inline fun combineTwoFilters(
    bg: SkImageFilter?,
    fg: SkImageFilter?,
    src: SkImage,
    ctm: SkMatrix,
    crossinline combine: (bgPx: Int, fgPx: Int) -> Int,
): SkImageFilter.FilterResult {
    val bgRes = bg?.filterImage(src, ctm) ?: SkImageFilter.FilterResult(src, 0, 0)
    val fgRes = fg?.filterImage(src, ctm) ?: SkImageFilter.FilterResult(src, 0, 0)
    return composeBboxes(bgRes, fgRes) { bgPx, fgPx -> combine(bgPx, fgPx) }
}

/** Compose two filter results into the union bbox, applying [combine] per pixel. */
private inline fun composeBboxes(
    bgRes: SkImageFilter.FilterResult,
    fgRes: SkImageFilter.FilterResult,
    crossinline combine: (bgPx: Int, fgPx: Int) -> Int,
): SkImageFilter.FilterResult {
    val bgImg = bgRes.image; val fgImg = fgRes.image
    val bgL = bgRes.offsetX; val bgT = bgRes.offsetY
    val fgL = fgRes.offsetX; val fgT = fgRes.offsetY
    val bgR = bgL + bgImg.width; val bgB = bgT + bgImg.height
    val fgR = fgL + fgImg.width; val fgB = fgT + fgImg.height

    val outL = minOf(bgL, fgL)
    val outT = minOf(bgT, fgT)
    val outR = maxOf(bgR, fgR)
    val outB = maxOf(bgB, fgB)
    val outW = (outR - outL).coerceAtLeast(1)
    val outH = (outB - outT).coerceAtLeast(1)
    val outBuf = IntArray(outW * outH)

    for (y in 0 until outH) {
        val devY = outT + y
        val bgY = devY - bgT
        val fgY = devY - fgT
        val bgYIn = bgY in 0 until bgImg.height
        val fgYIn = fgY in 0 until fgImg.height
        for (x in 0 until outW) {
            val devX = outL + x
            val bgX = devX - bgL
            val fgX = devX - fgL
            val bgPx = if (bgYIn && bgX in 0 until bgImg.width) bgImg.peekPixel(bgX, bgY) else 0
            val fgPx = if (fgYIn && fgX in 0 until fgImg.width) fgImg.peekPixel(fgX, fgY) else 0
            outBuf[y * outW + x] = combine(bgPx, fgPx)
        }
    }
    return SkImageFilter.FilterResult(SkImage(outW, outH, outBuf), outL, outT)
}

/**
 * `Arithmetic` — per-pixel `result = k1·src·dst + k2·src + k3·dst +
 * k4` per channel, where `src = fg` and `dst = bg`. Channels are
 * interpreted as **non-premul** floats in `[0, 1]` for the
 * arithmetic, then re-clamped back to byte range.
 *
 * `enforcePMColor` clamps each channel to `≤ alpha` after the
 * formula so the result is a valid premultiplied colour.
 */
internal class SkArithmeticImageFilter(
    private val k1: SkScalar,
    private val k2: SkScalar,
    private val k3: SkScalar,
    private val k4: SkScalar,
    private val enforcePMColor: Boolean,
    private val bg: SkImageFilter?,
    private val fg: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult =
        combineTwoFilters(bg, fg, src, ctm) { bgPx, fgPx -> arithmeticPixel(bgPx, fgPx) }

    private fun arithmeticPixel(bgPx: Int, fgPx: Int): Int {
        // The arithmetic formula is applied in **premultiplied** colour
        // space — matches upstream Skia's
        // SkArithmeticImageFilterImpl::onFilterImage and means recipes
        // like (0, 0, 0, k) produce a uniform-fill at premul intensity
        // `k`, which un-premuls correctly to (255, 255, 255, k·255).
        val ba = ((bgPx ushr 24) and 0xFF) / 255f
        val br = ((bgPx ushr 16) and 0xFF) / 255f * ba
        val bgC = ((bgPx ushr 8) and 0xFF) / 255f * ba
        val bb = (bgPx and 0xFF) / 255f * ba
        val fa = ((fgPx ushr 24) and 0xFF) / 255f
        val fr = ((fgPx ushr 16) and 0xFF) / 255f * fa
        val fgC = ((fgPx ushr 8) and 0xFF) / 255f * fa
        val fb = (fgPx and 0xFF) / 255f * fa
        var oa = (k1 * fa * ba + k2 * fa + k3 * ba + k4).coerceIn(0f, 1f)
        var or = k1 * fr * br + k2 * fr + k3 * br + k4
        var og = k1 * fgC * bgC + k2 * fgC + k3 * bgC + k4
        var ob = k1 * fb * bb + k2 * fb + k3 * bb + k4
        if (enforcePMColor) {
            or = or.coerceIn(0f, oa)
            og = og.coerceIn(0f, oa)
            ob = ob.coerceIn(0f, oa)
        } else {
            or = or.coerceIn(0f, 1f)
            og = og.coerceIn(0f, 1f)
            ob = ob.coerceIn(0f, 1f)
        }
        // Convert back to non-premul ARGB bytes for storage.
        val ai = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
        if (ai == 0) return 0
        val invA = 1f / oa
        val ri = (or * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gi = (og * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bi = (ob * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}

/**
 * `Blend` — per-pixel composition of two filter inputs through an
 * [SkBlendMode]. We delegate the actual blend math to a self-
 * contained [blendPixel] evaluator covering the canonical Porter-
 * Duff and separable blend modes.
 *
 * Per-pixel input convention : `bgPx = dst`, `fgPx = src` to match
 * upstream's "blend(src=fg, dst=bg)" convention.
 */
internal class SkBlendImageFilter(
    private val mode: SkBlendMode,
    private val bg: SkImageFilter?,
    private val fg: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult =
        combineTwoFilters(bg, fg, src, ctm) { bgPx, fgPx ->
            blendPixel(mode, fgPx, bgPx)
        }
}

/**
 * Per-pixel `SkBlendMode` evaluator. `srcPx` and `dstPx` are
 * non-premul ARGB ints. We convert to premul floats, apply the
 * mode's Porter-Duff (or HSL / "advanced" blend) formula, then
 * convert back to non-premul bytes.
 *
 * **Coverage** : the 9 Porter-Duff modes (Clear / Src / Dst /
 * SrcOver / DstOver / SrcIn / DstIn / SrcOut / DstOut / SrcAtop /
 * DstAtop / Xor / Plus / Modulate) plus the multiplicative
 * separable modes (Multiply / Screen / Darken / Lighten / Overlay).
 * Modes not on this list (`HardLight`, `SoftLight`, `Difference`,
 * `Exclusion`, `ColorDodge`, `ColorBurn`, the four HSL modes) fall
 * back to SrcOver for now — none are exercised by the C1.3 GMs.
 *
 * Math reference :
 * [Skia's `SkBlendMode.h`](https://github.com/google/skia/blob/main/include/core/SkBlendMode.h).
 */
private fun blendPixel(mode: SkBlendMode, srcPx: Int, dstPx: Int): Int {
    // Premul float decode.
    val sa = ((srcPx ushr 24) and 0xFF) / 255f
    val sr = ((srcPx ushr 16) and 0xFF) / 255f * sa
    val sg = ((srcPx ushr 8) and 0xFF) / 255f * sa
    val sb = (srcPx and 0xFF) / 255f * sa
    val da = ((dstPx ushr 24) and 0xFF) / 255f
    val dr = ((dstPx ushr 16) and 0xFF) / 255f * da
    val dg = ((dstPx ushr 8) and 0xFF) / 255f * da
    val db = (dstPx and 0xFF) / 255f * da

    val (oa, or, og, ob) = when (mode) {
        SkBlendMode.kClear -> floatArrayOf4(0f, 0f, 0f, 0f)
        SkBlendMode.kSrc -> floatArrayOf4(sa, sr, sg, sb)
        SkBlendMode.kDst -> floatArrayOf4(da, dr, dg, db)
        SkBlendMode.kSrcOver -> {
            val ia = 1f - sa
            floatArrayOf4(sa + da * ia, sr + dr * ia, sg + dg * ia, sb + db * ia)
        }
        SkBlendMode.kDstOver -> {
            val ia = 1f - da
            floatArrayOf4(da + sa * ia, dr + sr * ia, dg + sg * ia, db + sb * ia)
        }
        SkBlendMode.kSrcIn -> floatArrayOf4(sa * da, sr * da, sg * da, sb * da)
        SkBlendMode.kDstIn -> floatArrayOf4(da * sa, dr * sa, dg * sa, db * sa)
        SkBlendMode.kSrcOut -> {
            val ia = 1f - da
            floatArrayOf4(sa * ia, sr * ia, sg * ia, sb * ia)
        }
        SkBlendMode.kDstOut -> {
            val ia = 1f - sa
            floatArrayOf4(da * ia, dr * ia, dg * ia, db * ia)
        }
        SkBlendMode.kSrcATop -> {
            val ia = 1f - sa
            floatArrayOf4(da, sr * da + dr * ia, sg * da + dg * ia, sb * da + db * ia)
        }
        SkBlendMode.kDstATop -> {
            val ia = 1f - da
            floatArrayOf4(sa, dr * sa + sr * ia, dg * sa + sg * ia, db * sa + sb * ia)
        }
        SkBlendMode.kXor -> {
            val isa = 1f - sa; val ida = 1f - da
            floatArrayOf4(
                sa * ida + da * isa,
                sr * ida + dr * isa, sg * ida + dg * isa, sb * ida + db * isa,
            )
        }
        SkBlendMode.kPlus -> floatArrayOf4(
            (sa + da).coerceAtMost(1f),
            (sr + dr).coerceAtMost(1f),
            (sg + dg).coerceAtMost(1f),
            (sb + db).coerceAtMost(1f),
        )
        SkBlendMode.kModulate -> floatArrayOf4(sa * da, sr * dr, sg * dg, sb * db)
        SkBlendMode.kMultiply -> {
            val isa = 1f - sa; val ida = 1f - da
            floatArrayOf4(
                sa + da - sa * da,
                sr * ida + dr * isa + sr * dr,
                sg * ida + dg * isa + sg * dg,
                sb * ida + db * isa + sb * db,
            )
        }
        SkBlendMode.kScreen -> floatArrayOf4(
            sa + da - sa * da,
            sr + dr - sr * dr,
            sg + dg - sg * dg,
            sb + db - sb * db,
        )
        SkBlendMode.kDarken -> {
            val isa = 1f - sa; val ida = 1f - da
            floatArrayOf4(
                sa + da - sa * da,
                minOf(sr + dr * isa, dr + sr * ida),
                minOf(sg + dg * isa, dg + sg * ida),
                minOf(sb + db * isa, db + sb * ida),
            )
        }
        SkBlendMode.kLighten -> {
            val isa = 1f - sa; val ida = 1f - da
            floatArrayOf4(
                sa + da - sa * da,
                maxOf(sr + dr * isa, dr + sr * ida),
                maxOf(sg + dg * isa, dg + sg * ida),
                maxOf(sb + db * isa, db + sb * ida),
            )
        }
        else -> {
            // Fallback : SrcOver for unsupported modes.
            val ia = 1f - sa
            floatArrayOf4(sa + da * ia, sr + dr * ia, sg + dg * ia, sb + db * ia)
        }
    }

    // Convert back to non-premul bytes.
    val ai = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
    if (ai == 0) return 0
    val invA = 1f / oa
    val ri = (or * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (og * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (ob * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
}

/** Tiny destructuring helper — Kotlin's component1..N over IntArray would alloc. */
private fun floatArrayOf4(a: Float, r: Float, g: Float, b: Float): FloatArray =
    floatArrayOf(a, r, g, b)

private operator fun FloatArray.component1(): Float = this[0]
private operator fun FloatArray.component2(): Float = this[1]
private operator fun FloatArray.component3(): Float = this[2]
private operator fun FloatArray.component4(): Float = this[3]

/**
 * `Merge` — SrcOver-composites N filter inputs left-to-right.
 * `filters[0]` is the bottom, `filters.last()` is the top.
 *
 * Output bbox = union of every non-null input's bbox. Empty list
 * collapses to [SkEmptyImageFilter]'s 1×1 transparent black.
 */
internal class SkMergeImageFilter(
    private val filters: List<SkImageFilter?>,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        if (filters.isEmpty()) return SkEmptyImageFilter.filterImage(src, ctm)
        // Evaluate every input upfront so we can compute the union bbox.
        val results = filters.map { f -> f?.filterImage(src, ctm) ?: FilterResult(src, 0, 0) }
        var outL = Int.MAX_VALUE; var outT = Int.MAX_VALUE
        var outR = Int.MIN_VALUE; var outB = Int.MIN_VALUE
        for (r in results) {
            outL = minOf(outL, r.offsetX)
            outT = minOf(outT, r.offsetY)
            outR = maxOf(outR, r.offsetX + r.image.width)
            outB = maxOf(outB, r.offsetY + r.image.height)
        }
        val outW = (outR - outL).coerceAtLeast(1)
        val outH = (outB - outT).coerceAtLeast(1)
        val outBuf = IntArray(outW * outH)

        // Apply each input with SrcOver in order (bottom → top).
        for (r in results) {
            val img = r.image
            val l = r.offsetX; val t = r.offsetY
            for (y in 0 until img.height) {
                val outY = (t + y) - outT
                if (outY !in 0 until outH) continue
                for (x in 0 until img.width) {
                    val outX = (l + x) - outL
                    if (outX !in 0 until outW) continue
                    val srcPx = img.peekPixel(x, y)
                    val srcA = (srcPx ushr 24) and 0xFF
                    if (srcA == 0) continue
                    val idx = outY * outW + outX
                    outBuf[idx] = if (srcA == 0xFF) srcPx else srcOver(srcPx, outBuf[idx])
                }
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outL, outT)
    }

    /** SrcOver(src, dst) — non-premul-aware byte-arith implementation. */
    private fun srcOver(src: Int, dst: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        val da = (dst ushr 24) and 0xFF
        val invSa = 255 - sa
        val outA = sa + (da * invSa + 127) / 255
        if (outA == 0) return 0
        val sr = (src ushr 16) and 0xFF; val sg = (src ushr 8) and 0xFF; val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF; val dg = (dst ushr 8) and 0xFF; val db = dst and 0xFF
        val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
        val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
        val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
        return (outA shl 24) or (outR.coerceIn(0, 255) shl 16) or
            (outG.coerceIn(0, 255) shl 8) or outB.coerceIn(0, 255)
    }
}

/**
 * `DropShadowOnly` — produces just the blurred drop shadow without
 * the original input composited on top. Same parameter semantics
 * as [SkDropShadowImageFilter] ; reuses the SrcIn-tint + Gaussian
 * blur sub-pipeline. The output bbox is exactly the shadow bbox
 * (no union with the original input, since we don't draw it).
 */
internal class SkDropShadowOnlyImageFilter(
    private val dx: SkScalar, private val dy: SkScalar,
    private val sigmaX: SkScalar, private val sigmaY: SkScalar,
    private val color: SkColor,
    private val input: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        // Tint the upstream image to `color` via SrcIn (same recipe
        // as DropShadow).
        val tinted = SkColorFilterImageFilter(
            SkColorFilters.Blend(color, SkBlendMode.kSrcIn), null,
        ).filterImage(srcImg, ctm).image
        // Apply Gaussian blur to the tinted image.
        val blurred = SkBlurImageFilter(sigmaX, sigmaY, null).filterImage(tinted, ctm)
        // Translate by (dx, dy) scaled with CTM max-scale.
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        val sdx = (dx * scale + 0.5f).toInt()
        val sdy = (dy * scale + 0.5f).toInt()
        return FilterResult(
            image = blurred.image,
            offsetX = upstream.offsetX + blurred.offsetX + sdx,
            offsetY = upstream.offsetY + blurred.offsetY + sdy,
        )
    }
}

// -- C1.4 Morphology (Erode + Dilate) ---------------------------------------

/**
 * `SkMorphologyImageFilter` — shared implementation for `Erode`
 * (per-channel min) and `Dilate` (per-channel max). The kernel is
 * a `(2·rx + 1) × (2·ry + 1)` rectangle centred on each output
 * pixel.
 *
 * **Algorithm** : straightforward `O(W · H · (2·rx + 1) · (2·ry + 1))`
 * brute-force scan. Upstream Skia uses van Herk-Gil-Werman
 * (sliding-window deque) for `O(W · H)` independence from kernel
 * size ; we ship the simpler version because :
 *  - The test surface uses small radii (≤ 4 px).
 *  - The output is identical regardless of the algorithm.
 *  - A future PR can swap in van Herk if a GM exercises large radii.
 *
 * **Boundary semantic** : OOB samples are treated as transparent
 * black `(0, 0, 0, 0)`. For `Erode` (min) this means edges shrink ;
 * for `Dilate` (max) this means transparent samples don't pollute
 * the result, only push it outward where ANY in-bounds sample
 * exists.
 *
 * **Output bbox** :
 *  - `Erode` : same as input bbox (the eroded region is a subset).
 *  - `Dilate` : input bbox expanded by `(rx, ry)` on each side to
 *    accommodate the new dilated pixels.
 */
internal class SkMorphologyImageFilter(
    private val op: Op,
    private val rx: Int,
    private val ry: Int,
    private val input: SkImageFilter?,
) : SkImageFilter() {

    enum class Op { kErode, kDilate }

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        if (rx == 0 && ry == 0) return upstream

        val upImg = upstream.image
        val upW = upImg.width
        val upH = upImg.height
        if (upW == 0 || upH == 0) return upstream

        // Dilate expands by (rx, ry) ; erode keeps the input bbox.
        val padX = if (op == Op.kDilate) rx else 0
        val padY = if (op == Op.kDilate) ry else 0
        val outW = upW + 2 * padX
        val outH = upH + 2 * padY
        val outBuf = IntArray(outW * outH)

        val isErode = op == Op.kErode

        for (oy in 0 until outH) {
            // Map output (ox, oy) → upstream local centre (sxC, syC).
            val syC = oy - padY
            for (ox in 0 until outW) {
                val sxC = ox - padX

                // Init accumulators : 255 (max byte) for min, 0 for max.
                var bestA = if (isErode) 255 else 0
                var bestR = if (isErode) 255 else 0
                var bestG = if (isErode) 255 else 0
                var bestB = if (isErode) 255 else 0

                for (j in -ry..ry) {
                    val sy = syC + j
                    val syIn = sy in 0 until upH
                    for (i in -rx..rx) {
                        val sx = sxC + i
                        val sxIn = sx in 0 until upW
                        val px = if (sxIn && syIn) upImg.peekPixel(sx, sy) else 0
                        val a = (px ushr 24) and 0xFF
                        val r = (px ushr 16) and 0xFF
                        val g = (px ushr 8) and 0xFF
                        val b = px and 0xFF
                        if (isErode) {
                            if (a < bestA) bestA = a
                            if (r < bestR) bestR = r
                            if (g < bestG) bestG = g
                            if (b < bestB) bestB = b
                        } else {
                            if (a > bestA) bestA = a
                            if (r > bestR) bestR = r
                            if (g > bestG) bestG = g
                            if (b > bestB) bestB = b
                        }
                    }
                }
                outBuf[oy * outW + ox] = (bestA shl 24) or (bestR shl 16) or (bestG shl 8) or bestB
            }
        }

        return FilterResult(
            SkImage(outW, outH, outBuf),
            upstream.offsetX - padX,
            upstream.offsetY - padY,
        )
    }
}

// -- C1.5 DisplacementMap ----------------------------------------------------

/**
 * `SkDisplacementMapImageFilter` — for every output pixel `p`, read
 * the displacement filter's pixel at `p`, extract the configured
 * [xChannelSelector] / [yChannelSelector] channels (centred on
 * `0.5`), multiply by [scale] to derive an offset `(dx, dy)`, then
 * sample the [color] filter at `p + (dx, dy)`.
 *
 * **Boundary semantic** : OOB samples on the colour input return
 * transparent black (kDecal). This matches upstream Skia's default
 * behaviour without an explicit cropRect.
 *
 * **Output bbox** : the colour filter's bbox.
 */
internal class SkDisplacementMapImageFilter(
    private val xChannelSelector: SkColorChannel,
    private val yChannelSelector: SkColorChannel,
    private val scale: SkScalar,
    private val displacement: SkImageFilter?,
    private val color: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val displRes = displacement?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val colorRes = color?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val displImg = displRes.image
        val colorImg = colorRes.image
        val outImg = colorImg
        val outW = outImg.width
        val outH = outImg.height
        if (outW == 0 || outH == 0) return colorRes

        val outBuf = IntArray(outW * outH)

        // Scale is in **device pixels** ; multiply by ctm max-scale
        // for CTM-aware offsetting. Matches the convention used by
        // Offset / DropShadow.
        val scaledMag = scale * ctm.computeMaxScale().coerceAtLeast(1f)

        for (y in 0 until outH) {
            // Map output local (x, y) → device-space coords for displacement lookup.
            val devY = y + colorRes.offsetY
            val displLocalY = devY - displRes.offsetY
            for (x in 0 until outW) {
                val devX = x + colorRes.offsetX
                val displLocalX = devX - displRes.offsetX

                // Read displacement pixel ; OOB → 0 (transparent black).
                val displPx = if (displLocalX in 0 until displImg.width &&
                    displLocalY in 0 until displImg.height
                ) displImg.peekPixel(displLocalX, displLocalY) else 0

                val cx = extractChannel(displPx, xChannelSelector) / 255f
                val cy = extractChannel(displPx, yChannelSelector) / 255f
                // Centre at zero, then scale.
                val dx = scaledMag * (cx - 0.5f)
                val dy = scaledMag * (cy - 0.5f)

                // Sample colour at (x + dx, y + dy) using nearest-neighbour.
                val sxLocal = kotlin.math.round(x + dx).toInt()
                val syLocal = kotlin.math.round(y + dy).toInt()
                outBuf[y * outW + x] = if (sxLocal in 0 until outW && syLocal in 0 until outH)
                    colorImg.peekPixel(sxLocal, syLocal) else 0
            }
        }

        return FilterResult(SkImage(outW, outH, outBuf), colorRes.offsetX, colorRes.offsetY)
    }

    /** Extract the byte value of a single channel from a non-premul ARGB int. */
    private fun extractChannel(px: Int, ch: SkColorChannel): Int = when (ch) {
        SkColorChannel.kR -> (px ushr 16) and 0xFF
        SkColorChannel.kG -> (px ushr 8) and 0xFF
        SkColorChannel.kB -> px and 0xFF
        SkColorChannel.kA -> (px ushr 24) and 0xFF
    }
}

// -- C1.6 MatrixConvolution -------------------------------------------------

/**
 * `SkMatrixConvolutionImageFilter` — applies a 2D kernel
 * convolution per pixel.
 *
 * The kernel is a `kSize.width × kSize.height` grid of floats stored
 * row-major in [kernel] (so `kernel[j * width + i]` is the weight
 * applied to the input sample `(x + i - kCenter.x, y + j - kCenter.y)`).
 *
 * Operates in **non-premul** colour space. `convolveAlpha = false`
 * passes alpha through from the centre input pixel ; otherwise
 * alpha is convolved like the colour channels. OOB samples follow
 * [tileMode].
 */
internal class SkMatrixConvolutionImageFilter(
    private val kSize: SkISize,
    private val kernel: FloatArray,
    private val gain: SkScalar,
    private val bias: SkScalar,
    private val kCenter: SkIPoint,
    private val tileMode: SkTileMode,
    private val convolveAlpha: Boolean,
    private val input: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val upImg = upstream.image
        val upW = upImg.width
        val upH = upImg.height
        if (upW == 0 || upH == 0) return upstream

        val kW = kSize.width
        val kH = kSize.height
        val cx = kCenter.fX
        val cy = kCenter.fY
        val outBuf = IntArray(upW * upH)

        for (y in 0 until upH) {
            for (x in 0 until upW) {
                var accR = 0f; var accG = 0f; var accB = 0f; var accA = 0f
                for (j in 0 until kH) {
                    val sy = y + j - cy
                    for (i in 0 until kW) {
                        val sx = x + i - cx
                        val px = sampleImageWithTileMode(upImg, sx, sy, tileMode)
                        val w = kernel[j * kW + i]
                        val r = ((px ushr 16) and 0xFF).toFloat()
                        val g = ((px ushr 8) and 0xFF).toFloat()
                        val b = (px and 0xFF).toFloat()
                        val a = ((px ushr 24) and 0xFF).toFloat()
                        accR += w * r; accG += w * g; accB += w * b
                        if (convolveAlpha) accA += w * a
                    }
                }
                accR = gain * accR + bias
                accG = gain * accG + bias
                accB = gain * accB + bias
                val outR = accR.toInt().coerceIn(0, 255)
                val outG = accG.toInt().coerceIn(0, 255)
                val outB = accB.toInt().coerceIn(0, 255)
                val outA = if (convolveAlpha) {
                    (gain * accA + bias).toInt().coerceIn(0, 255)
                } else {
                    (upImg.peekPixel(x, y) ushr 24) and 0xFF
                }
                outBuf[y * upW + x] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        return FilterResult(SkImage(upW, upH, outBuf), upstream.offsetX, upstream.offsetY)
    }
}

// -- C1.7 Lighting (full surface : 6 variants) ------------------------------

/**
 * `SkLightingImageFilter` — shared implementation for all 6 lighting
 * variants. Treats [input]'s alpha channel as a height map, derives
 * per-pixel surface normals via a 3×3 Sobel kernel scaled by
 * [surfaceScale], then evaluates the Phong reflection model at each
 * pixel.
 *
 * **Per pixel** :
 *  1. `h(x, y) = α(x, y) / 255` — height from input alpha.
 *  2. Surface normal : `N = normalize(-Sobel_x · surfaceScale,
 *     -Sobel_y · surfaceScale, 1)`.
 *  3. Light direction `L` and modulation `m` from [light].
 *  4. Diffuse : `out_rgb = kdOrKs · max(0, N · L) · lightColor · m`.
 *     Specular : `H = normalize(L + V)` with `V = (0, 0, 1)` ;
 *     `out_rgb = kdOrKs · max(0, N · H)^shininess · lightColor · m`.
 *  5. Output alpha : `max(out_r, out_g, out_b)` (canonical Skia
 *     behaviour for lighting filters).
 */
internal class SkLightingImageFilter(
    private val light: SkLight,
    private val lightColor: SkColor,
    private val surfaceScale: SkScalar,
    private val kdOrKs: SkScalar,
    private val isDiffuse: Boolean,
    private val shininess: SkScalar,
    private val input: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val upImg = upstream.image
        val w = upImg.width
        val h = upImg.height
        if (w == 0 || h == 0) return upstream

        val outBuf = IntArray(w * h)
        val lightR = ((lightColor ushr 16) and 0xFF) / 255f
        val lightG = ((lightColor ushr 8) and 0xFF) / 255f
        val lightB = (lightColor and 0xFF) / 255f

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Height at the surface point ; treat OOB as 0.
                val pz = sampleAlpha(upImg, x, y) / 255f * surfaceScale
                val sx = sobelX(upImg, x, y) * surfaceScale
                val sy = sobelY(upImg, x, y) * surfaceScale
                // Normal = normalize((-sx, -sy, 1)).
                val nMag2 = sx * sx + sy * sy + 1f
                val nInvMag = 1f / kotlin.math.sqrt(nMag2)
                val nx = -sx * nInvMag
                val ny = -sy * nInvMag
                val nz = nInvMag

                // Light direction L and modulation m.
                val lAndM = computeLightDirAndMod(x.toFloat(), y.toFloat(), pz)
                val lx = lAndM[0]; val ly = lAndM[1]; val lz = lAndM[2]
                val mod = lAndM[3]
                if (mod <= 0f) {
                    outBuf[y * w + x] = 0
                    continue
                }

                val coef = if (isDiffuse) {
                    val nDotL = nx * lx + ny * ly + nz * lz
                    if (nDotL <= 0f) 0f else kdOrKs * nDotL
                } else {
                    // H = normalize(L + V) ; V = (0, 0, 1).
                    val hx = lx; val hy = ly; val hz = lz + 1f
                    val hMag2 = hx * hx + hy * hy + hz * hz
                    val hInv = if (hMag2 < 1e-12f) 0f else 1f / kotlin.math.sqrt(hMag2)
                    val hxN = hx * hInv; val hyN = hy * hInv; val hzN = hz * hInv
                    val nDotH = nx * hxN + ny * hyN + nz * hzN
                    if (nDotH <= 0f) 0f
                    else kdOrKs * floatPow(nDotH, shininess)
                }
                if (coef <= 0f) {
                    outBuf[y * w + x] = 0
                    continue
                }

                val rF = coef * lightR * mod
                val gF = coef * lightG * mod
                val bF = coef * lightB * mod
                val rB = (rF * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gB = (gF * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bB = (bF * 255f + 0.5f).toInt().coerceIn(0, 255)
                val aB = maxOf(rB, gB, bB)
                outBuf[y * w + x] = (aB shl 24) or (rB shl 16) or (gB shl 8) or bB
            }
        }

        return FilterResult(SkImage(w, h, outBuf), upstream.offsetX, upstream.offsetY)
    }

    /** `[lx, ly, lz, modulation]` per-pixel for the configured light. */
    private fun computeLightDirAndMod(px: Float, py: Float, pz: Float): FloatArray {
        return when (val l = light) {
            is SkLight.Distant -> floatArrayOf(l.dir[0], l.dir[1], l.dir[2], 1f)
            is SkLight.Point -> {
                val dx = l.location[0] - px
                val dy = l.location[1] - py
                val dz = l.location[2] - pz
                val mag2 = dx * dx + dy * dy + dz * dz
                if (mag2 < 1e-12f) floatArrayOf(0f, 0f, 1f, 0f)
                else {
                    val inv = 1f / kotlin.math.sqrt(mag2)
                    floatArrayOf(dx * inv, dy * inv, dz * inv, 1f)
                }
            }
            is SkLight.Spot -> {
                val dx = l.location[0] - px
                val dy = l.location[1] - py
                val dz = l.location[2] - pz
                val mag2 = dx * dx + dy * dy + dz * dz
                if (mag2 < 1e-12f) return floatArrayOf(0f, 0f, 1f, 0f)
                val inv = 1f / kotlin.math.sqrt(mag2)
                val lx = dx * inv; val ly = dy * inv; val lz = dz * inv
                // Spot axis : direction from location to target, normalised.
                val ax = l.target[0] - l.location[0]
                val ay = l.target[1] - l.location[1]
                val az = l.target[2] - l.location[2]
                val aMag2 = ax * ax + ay * ay + az * az
                if (aMag2 < 1e-12f) return floatArrayOf(lx, ly, lz, 1f)
                val aInv = 1f / kotlin.math.sqrt(aMag2)
                val axN = ax * aInv; val ayN = ay * aInv; val azN = az * aInv
                // Cosine of angle between (-L) and the spot axis.
                val cosOuter = -(lx * axN + ly * ayN + lz * azN)
                val cosCutoff = kotlin.math.cos(l.cutoffAngle)
                if (cosOuter < cosCutoff) {
                    floatArrayOf(lx, ly, lz, 0f)
                } else {
                    val mod = floatPow(cosOuter, l.falloffExponent)
                    floatArrayOf(lx, ly, lz, mod)
                }
            }
        }
    }

    /** `pow` for floats — exponent may be non-integer (specular shininess, spot falloff). */
    private fun floatPow(base: Float, exp: Float): Float =
        kotlin.math.exp(exp * kotlin.math.ln(base.coerceAtLeast(1e-12f)))

    /** Sobel X gradient of the alpha channel at `(x, y)` ; OOB samples → 0. */
    private fun sobelX(img: SkImage, x: Int, y: Int): Float {
        val a = (sampleAlpha(img, x + 1, y - 1) - sampleAlpha(img, x - 1, y - 1)) +
            2 * (sampleAlpha(img, x + 1, y) - sampleAlpha(img, x - 1, y)) +
            (sampleAlpha(img, x + 1, y + 1) - sampleAlpha(img, x - 1, y + 1))
        return a / (8f * 255f)
    }

    /** Sobel Y gradient of the alpha channel at `(x, y)` ; OOB samples → 0. */
    private fun sobelY(img: SkImage, x: Int, y: Int): Float {
        val a = (sampleAlpha(img, x - 1, y + 1) - sampleAlpha(img, x - 1, y - 1)) +
            2 * (sampleAlpha(img, x, y + 1) - sampleAlpha(img, x, y - 1)) +
            (sampleAlpha(img, x + 1, y + 1) - sampleAlpha(img, x + 1, y - 1))
        return a / (8f * 255f)
    }

    /** Read the alpha byte at `(x, y)`, returning 0 for OOB. */
    private fun sampleAlpha(img: SkImage, x: Int, y: Int): Int =
        if (x in 0 until img.width && y in 0 until img.height)
            (img.peekPixel(x, y) ushr 24) and 0xFF
        else 0
}

// -- Phase G-saveLayer-imageFilter -- introspection extractors ---------------
//
// Mirror the pattern of [SkColorFilter.asBlendModeFilter] /
// [SkColorFilter.asMatrixFilter] : let GPU backends inspect the two filter
// variants that the WebGPU layer composite scaffolding handles natively --
// `SkImageFilters.Blur` (deferred to follow-up, throw-on-encounter) and
// `SkImageFilters.ColorFilter(child = null)` (routed to the existing
// colorFilter packing). All other variants return `null` ; the GPU dispatch
// throws a clear "not yet supported" error for those.

/**
 * Read-only descriptor of an [SkImageFilters.Blur] filter -- the Gaussian
 * sigma per axis, the tile mode used for out-of-bounds samples, and the
 * optional child filter that feeds into the blur. Returned by
 * [SkImageFilter.asBlurImageFilter] when (and only when) the receiver is a
 * `Blur` filter.
 *
 * Backends that can express a separable Gaussian on a render target (e.g.
 * a multi-pass WebGPU fragment shader) use this to decide whether to fold
 * the filter in or throw / fall back. The kanvas-skia GPU backend's
 * scaffolding slice currently throws for any non-null Blur on the layer
 * paint -- this descriptor lets it surface `sigmaX` / `sigmaY` / `tileMode`
 * in the error message.
 */
public data class SkBlurImageFilterParams(
    /** Gaussian sigma in X (`>= 0`). */
    public val sigmaX: Float,
    /** Gaussian sigma in Y (`>= 0`). */
    public val sigmaY: Float,
    /** Tile mode used when the kernel samples beyond the source bounds. */
    public val tileMode: SkTileMode,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Read-only descriptor of an [SkImageFilters.ColorFilter] wrap -- the
 * inner [SkColorFilter] applied per pixel, and the optional child filter
 * that feeds into it. Returned by [SkImageFilter.asColorFilterImageFilter]
 * when (and only when) the receiver is a `ColorFilter` filter.
 *
 * The GPU layer-composite path uses this to unwrap the [colorFilter] and
 * route it through the existing layer paint colorFilter plumbing -- the
 * effective pixel result is identical because the composite step renders
 * the layer pixels through the same per-pixel filter pass, and no
 * geometric transform is applied. The [input] child must be `null` for
 * that routing to be exact (a non-null child means the filter chain has
 * structural transforms upstream of the colour transform, which the
 * scaffolding doesn't yet handle ; the GPU dispatch then throws).
 */
public data class SkColorFilterImageFilterParams(
    /** The inner [SkColorFilter] applied per pixel. */
    public val colorFilter: SkColorFilter,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.Blur] filter, or `null` if
 * the receiver is any other [SkImageFilter] variant (offset, colorFilter,
 * compose, drop-shadow, displacement, ...).
 *
 * Backends that can implement a Gaussian convolution on a render target
 * use this to decide whether to fold the filter in or fall through to the
 * CPU snapshot path. The kanvas-skia GPU scaffolding doesn't yet have a
 * fragment-side Gaussian kernel ; it uses this purely to surface a clear
 * error message when the layer paint carries a Blur.
 */
public fun SkImageFilter.asBlurImageFilter(): SkBlurImageFilterParams? {
    val f = this as? SkBlurImageFilter ?: return null
    return SkBlurImageFilterParams(
        sigmaX = f.exposedSigmaX,
        sigmaY = f.exposedSigmaY,
        tileMode = f.exposedTileMode,
        input = f.exposedInput,
    )
}

/**
 * Extract the parameters of an [SkImageFilters.ColorFilter] wrap, or
 * `null` if the receiver is any other [SkImageFilter] variant.
 *
 * Backends that can express a per-pixel [SkColorFilter] in their composite
 * fragment shader (e.g. the WebGPU layer-composite pipeline) use this to
 * unwrap the inner colour filter and route it through the existing
 * `paint.colorFilter` plumbing -- the per-pixel result is identical when
 * the wrap's [SkColorFilterImageFilterParams.input] is `null`.
 */
public fun SkImageFilter.asColorFilterImageFilter(): SkColorFilterImageFilterParams? {
    val f = this as? SkColorFilterImageFilter ?: return null
    return SkColorFilterImageFilterParams(
        colorFilter = f.exposedColorFilter,
        input = f.exposedInput,
    )
}

// -- Phase G-saveLayer-imageFilter-offset -- introspection extractor ---------
//
// Mirror of [asBlurImageFilter] for [SkImageFilters.Offset]. GPU backends
// that can fold a `(dx, dy)` translation into their layer-composite uniform
// (the WebGPU port reuses `layer_composite.wgsl`'s `dstOriginSize.xy` slot)
// use this to detect the filter and skip the throw-on-encounter gate.

/**
 * Read-only descriptor of an [SkImageFilters.Offset] filter -- the per-axis
 * displacement and the optional child filter. Returned by
 * [SkImageFilter.asOffsetImageFilter] when (and only when) the receiver is an
 * `Offset` filter.
 *
 * Backends that fold an `(dx, dy)` translation into their composite uniform
 * (no per-pixel sample remap, just a shifted dst origin) use this to detect
 * the filter without the upstream `Offset(0, 0)` no-op fast path that the
 * CPU raster handles internally.
 */
public data class SkOffsetImageFilterParams(
    /** Translation in device pixels along X. */
    public val dx: Float,
    /** Translation in device pixels along Y. */
    public val dy: Float,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Read-only descriptor of an [SkImageFilters.DropShadow] filter -- the
 * shadow offset, per-axis Gaussian sigma, shadow colour, and optional child
 * filter. Returned by [SkImageFilter.asDropShadowImageFilter] when (and
 * only when) the receiver is a `DropShadow` filter.
 *
 * The GPU layer-composite path uses this to render a colorized-blurred-
 * offset copy of the layer texture BEHIND the original layer content,
 * matching the `Blur(sigma) + Offset(dx, dy) + ColorFilter(color, kSrcIn)
 * + Compose(srcOver, original)` semantic of `SkDropShadowImageFilter`.
 */
public data class SkDropShadowImageFilterParams(
    /** Shadow displacement in device pixels along X. */
    public val dx: Float,
    /** Shadow displacement in device pixels along Y. */
    public val dy: Float,
    /** Gaussian sigma in X (`>= 0`). */
    public val sigmaX: Float,
    /** Gaussian sigma in Y (`>= 0`). */
    public val sigmaY: Float,
    /** Shadow colour (non-premul ARGB, applied via `SrcIn` on the alpha). */
    public val color: SkColor,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.Offset] filter, or `null` if
 * the receiver is any other [SkImageFilter] variant (blur, colorFilter,
 * compose, drop-shadow, ...).
 *
 * Backends that fold a `(dx, dy)` translation into their composite uniform
 * use this to detect the filter ; the kanvas-skia GPU backend reuses the
 * `layer_composite.wgsl` shader's existing `dstOriginSize.xy` slot and
 * simply shifts the dst origin -- no per-pixel UV remap.
 */
public fun SkImageFilter.asOffsetImageFilter(): SkOffsetImageFilterParams? {
    val f = this as? SkOffsetImageFilter ?: return null
    return SkOffsetImageFilterParams(
        dx = f.exposedDx,
        dy = f.exposedDy,
        input = f.exposedInput,
    )
}

/**
 * Extract the parameters of an [SkImageFilters.DropShadow] filter, or `null`
 * if the receiver is any other [SkImageFilter] variant.
 *
 * Backends that can render a colorized blurred shadow behind a layer (the
 * WebGPU port reuses the existing 3-pass blur pipeline followed by the layer
 * composite, with the shadow colorize folded in-shader) use this to detect
 * the filter and route through the dedicated 2-stage composite path.
 */
public fun SkImageFilter.asDropShadowImageFilter(): SkDropShadowImageFilterParams? {
    val f = this as? SkDropShadowImageFilter ?: return null
    return SkDropShadowImageFilterParams(
        dx = f.exposedDx,
        dy = f.exposedDy,
        sigmaX = f.exposedSigmaX,
        sigmaY = f.exposedSigmaY,
        color = f.exposedColor,
        input = f.exposedInput,
    )
}

/**
 * Read-only descriptor of an [SkImageFilters.Compose] node -- the two
 * child filters that the composition chains, in the order
 * `outer(inner(src))`. Returned by [SkImageFilter.asComposeImageFilter]
 * when (and only when) the receiver is a `Compose` filter.
 *
 * Backends that can walk an image-filter tree (e.g. the WebGPU layer
 * composite pipeline -- Phase G-saveLayer-imageFilter-compose) use this
 * to recursively resolve the children : apply [inner] to the layer
 * texture first, then apply [outer] to the result. Each child is itself
 * an [SkImageFilter], so the backend re-runs its dispatch on the
 * extracted children -- support for [SkImageFilters.Compose] then
 * follows directly from support for the leaf filter variants (Blur,
 * ColorFilter, ...).
 */
public data class SkComposeImageFilterParams(
    /** Outer filter applied last : `outer(inner(src))`. */
    public val outer: SkImageFilter,
    /** Inner filter applied first to the source image. */
    public val inner: SkImageFilter,
)

/**
 * Extract the parameters of an [SkImageFilters.Compose] node, or `null`
 * if the receiver is any other [SkImageFilter] variant (Blur, Offset,
 * ColorFilter, DropShadow, ...).
 *
 * Backends that can recursively resolve a filter tree use this to walk
 * `outer(inner(src))` -- the layer composite path applies [inner] to
 * the layer texture into a scratch render target, then applies [outer]
 * to that scratch, then composites onto the parent. The two children
 * are themselves [SkImageFilter]s so the dispatch recurses on them.
 */
public fun SkImageFilter.asComposeImageFilter(): SkComposeImageFilterParams? {
    val f = this as? SkComposeImageFilter ?: return null
    return SkComposeImageFilterParams(
        outer = f.exposedOuter,
        inner = f.exposedInner,
    )
}

// -- Phase G-saveLayer-imageFilter-crop -- introspection extractor ----------
//
// Mirror of [asBlurImageFilter] for [SkImageFilters.Crop]. GPU backends that
// can express a per-pixel UV-clamp / repeat / mirror / decal in their
// composite fragment shader use this to detect the filter and route through
// the dedicated branch.

/**
 * Read-only descriptor of an [SkImageFilters.Crop] filter -- the crop
 * rectangle, the tile mode dictating out-of-rect samples, and the optional
 * child filter. Returned by [SkImageFilter.asCropImageFilter] when (and only
 * when) the receiver is a `Crop` filter.
 *
 * The GPU layer-composite path uses this to fold a pure UV-clamp / UV-repeat
 * / UV-mirror / UV-decal into the composite fragment shader -- no scratch
 * texture, no extra pass.
 */
public data class SkCropImageFilterParams(
    /** Crop rectangle in device pixels. */
    public val rect: SkRect,
    /** Tile mode used when the sample lands outside [rect]. */
    public val tileMode: SkTileMode,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.Crop] filter, or `null` if
 * the receiver is any other [SkImageFilter] variant.
 */
public fun SkImageFilter.asCropImageFilter(): SkCropImageFilterParams? {
    val f = this as? SkCropImageFilter ?: return null
    return SkCropImageFilterParams(
        rect = f.exposedRect,
        tileMode = f.exposedTileMode,
        input = f.exposedInput,
    )
}

// -- Phase G-saveLayer-imageFilter-tile -- introspection extractor ----------

/**
 * Read-only descriptor of an [SkImageFilters.Tile] filter -- the source
 * sub-region, the destination rectangle, and the optional child filter.
 * Returned by [SkImageFilter.asTileImageFilter] when (and only when) the
 * receiver is a `Tile` filter.
 *
 * The GPU layer-composite path uses this to fold a pure UV-modulo across
 * [dst] into the composite fragment shader, sampling [src] in the source
 * image -- no scratch texture, no extra pass.
 */
public data class SkTileImageFilterParams(
    /** Source sub-region (replicated). */
    public val src: SkRect,
    /** Destination rectangle (tiled output extent). */
    public val dst: SkRect,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.Tile] filter, or `null` if
 * the receiver is any other [SkImageFilter] variant.
 */
public fun SkImageFilter.asTileImageFilter(): SkTileImageFilterParams? {
    val f = this as? SkTileImageFilter ?: return null
    return SkTileImageFilterParams(
        src = f.exposedSrc,
        dst = f.exposedDst,
        input = f.exposedInput,
    )
}

// -- Phase G-saveLayer-imageFilter-magnifier -- introspection extractor -----

/**
 * Read-only descriptor of an [SkImageFilters.Magnifier] filter -- the lens
 * rectangle, zoom amount, inset (soft transition zone), and optional child
 * filter. Returned by [SkImageFilter.asMagnifierImageFilter] when (and only
 * when) the receiver is a `Magnifier` filter.
 *
 * The GPU layer-composite path uses this to fold a per-pixel zoom-around-
 * lens-centre with an edge-blend over the inset band into a dedicated
 * fragment shader -- no scratch texture, no extra pass.
 */
public data class SkMagnifierImageFilterParams(
    /** Lens rectangle in device pixels. */
    public val lensBounds: SkRect,
    /** Zoom factor (`> 0` ; `<= 0` is a backend no-op). */
    public val zoomAmount: Float,
    /** Inset band width in pixels (`>= 0`). */
    public val inset: Float,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.Magnifier] filter, or `null`
 * if the receiver is any other [SkImageFilter] variant.
 */
public fun SkImageFilter.asMagnifierImageFilter(): SkMagnifierImageFilterParams? {
    val f = this as? SkMagnifierImageFilter ?: return null
    return SkMagnifierImageFilterParams(
        lensBounds = f.exposedLensBounds,
        zoomAmount = f.exposedZoom,
        inset = f.exposedInset,
        input = f.exposedInput,
    )
}

// -- Phase G-saveLayer-imageFilter-matrixTransform -- introspection ----------
//
// Mirror of [asOffsetImageFilter] for [SkImageFilters.MatrixTransform]. GPU
// backends that can fold an arbitrary 2x3 affine into their layer-composite
// uniform (the WebGPU port packs the inverse of the user's matrix into a pair
// of `vec4f` slots that the fragment shader applies to the device-pixel
// coords before sampling the layer texture) use this to detect the filter
// and skip the throw-on-encounter gate.

/**
 * Read-only descriptor of an [SkImageFilters.MatrixTransform] filter -- the
 * 3x3 (typically affine) user matrix, the sampling options the shader
 * honours when the inverse matrix lands on non-integer texel coords, and the
 * optional child filter. Returned by [SkImageFilter.asMatrixTransformImageFilter]
 * when (and only when) the receiver is a `MatrixTransform` filter.
 *
 * Backends that can express the inverse 2x3 in their composite fragment
 * shader (the WebGPU port packs `devToLayerRow0` / `devToLayerRow1` into the
 * layer composite uniform and the cover-quad geometry is the bounding box
 * of the matrix-mapped layer rect) use this to detect the filter and route
 * through the dedicated affine path. Perspective (`hasPerspective()`) is
 * out of scope for the first MatrixTransform slice -- the dispatch gate
 * throws a clear "deferred" error in that case.
 */
public data class SkMatrixTransformImageFilterParams(
    /** User-supplied 3x3 matrix applied to the input filter's output. */
    public val matrix: SkMatrix,
    /** Sampling options applied to the inverse-transformed texel coords. */
    public val sampling: SkSamplingOptions,
    /** Optional child filter ; `null` means the source image is the input. */
    public val input: SkImageFilter?,
)

/**
 * Extract the parameters of an [SkImageFilters.MatrixTransform] filter, or
 * `null` if the receiver is any other [SkImageFilter] variant (Blur, Offset,
 * ColorFilter, DropShadow, Compose, ...).
 *
 * Backends that fold an arbitrary affine transform into their composite
 * fragment shader use this to detect the filter ; the kanvas-skia WebGPU
 * backend packs the inverse 2x3 into the existing layer composite uniform
 * (two extra `vec4f` slots beyond the no-filter saveLayer payload) and
 * computes the cover-quad bbox by mapping the four corners of the layer
 * rect through the user's matrix.
 */
public fun SkImageFilter.asMatrixTransformImageFilter(): SkMatrixTransformImageFilterParams? {
    val f = this as? SkMatrixTransformImageFilter ?: return null
    return SkMatrixTransformImageFilterParams(
        matrix = f.exposedMatrix,
        sampling = f.exposedSampling,
        input = f.exposedInput,
    )
}
