package org.skia.foundation

/**
 * Compositing mode controlling how a source colour combines with the
 * destination pixel. Mirrors Skia's `SkBlendMode` — all 29 modes from
 * upstream, in the same declaration order so [ordinal] and `kLast*Mode`
 * markers stay aligned with the C++ side.
 *
 * Mathematical notation in the per-mode comments:
 * - `s`, `d` are the source / destination **premultiplied** colour vectors
 *   (RGB pre-multiplied by their alpha); `sa`, `da` are the alpha channels.
 * - `r` is the resulting premultiplied colour.
 * - `rc` / `ra` denote the colour and alpha components when they follow
 *   different formulas (typical of HSL / Darken / Lighten / Difference).
 *
 * **`:kanvas-skia`'s rasterizer currently implements only [kSrcOver]**
 * (the SkPaint default). The other modes are listed so that GMs reading
 * `paint.setBlendMode(...)` can be ported faithfully and a future blend
 * pipeline can dispatch on this enum.
 */
public enum class SkBlendMode {
    /** `r = 0` */
    kClear,

    /** `r = s` */
    kSrc,

    /** `r = d` */
    kDst,

    /** `r = s + (1-sa)*d` — the default Skia paint blend. */
    kSrcOver,

    /** `r = d + (1-da)*s` */
    kDstOver,

    /** `r = s * da` */
    kSrcIn,

    /** `r = d * sa` */
    kDstIn,

    /** `r = s * (1-da)` */
    kSrcOut,

    /** `r = d * (1-sa)` */
    kDstOut,

    /** `r = s*da + d*(1-sa)` */
    kSrcATop,

    /** `r = d*sa + s*(1-da)` */
    kDstATop,

    /** `r = s*(1-da) + d*(1-sa)` */
    kXor,

    /** `r = min(s + d, 1)` */
    kPlus,

    /** `r = s*d` */
    kModulate,

    /** `r = s + d - s*d` */
    kScreen,

    /** Multiply or screen, depending on destination. */
    kOverlay,

    /** `rc = s + d - max(s*da, d*sa)`, `ra = kSrcOver` */
    kDarken,

    /** `rc = s + d - min(s*da, d*sa)`, `ra = kSrcOver` */
    kLighten,

    /** Brighten destination to reflect source. */
    kColorDodge,

    /** Darken destination to reflect source. */
    kColorBurn,

    /** Multiply or screen, depending on source. */
    kHardLight,

    /** Lighten or darken, depending on source. */
    kSoftLight,

    /** `rc = s + d - 2*min(s*da, d*sa)`, `ra = kSrcOver` */
    kDifference,

    /** `rc = s + d - 2*s*d`, `ra = kSrcOver` */
    kExclusion,

    /** `r = s*(1-da) + d*(1-sa) + s*d` */
    kMultiply,

    /** Hue of source with saturation and luminosity of destination. */
    kHue,

    /** Saturation of source with hue and luminosity of destination. */
    kSaturation,

    /** Hue and saturation of source with luminosity of destination. */
    kColor,

    /** Luminosity of source with hue and saturation of destination. */
    kLuminosity,
    ;

    public companion object {
        /** Last Porter-Duff coefficient mode (`<= kLastCoeffMode` upstream). */
        public val kLastCoeffMode: SkBlendMode = kScreen

        /** Last mode that operates separately on each colour component. */
        public val kLastSeparableMode: SkBlendMode = kMultiply

        /** Last valid mode (matches upstream `kLastMode`). */
        public val kLastMode: SkBlendMode = kLuminosity

        /** Total count of blend modes (matches upstream `kSkBlendModeCount`). */
        public val kSkBlendModeCount: Int = entries.size
    }
}

/**
 * Mirrors Skia's `const char* SkBlendMode_Name(SkBlendMode)`
 * (`src/core/SkBlendMode.cpp`). Returns the canonical CamelCase name
 * (without the `k` prefix) — used by GM labels (`xfermodes`,
 * `aaxfermodes`, `androidblendmodes`).
 */
public fun SkBlendMode_Name(mode: SkBlendMode): String = when (mode) {
    SkBlendMode.kClear -> "Clear"
    SkBlendMode.kSrc -> "Src"
    SkBlendMode.kDst -> "Dst"
    SkBlendMode.kSrcOver -> "SrcOver"
    SkBlendMode.kDstOver -> "DstOver"
    SkBlendMode.kSrcIn -> "SrcIn"
    SkBlendMode.kDstIn -> "DstIn"
    SkBlendMode.kSrcOut -> "SrcOut"
    SkBlendMode.kDstOut -> "DstOut"
    SkBlendMode.kSrcATop -> "SrcATop"
    SkBlendMode.kDstATop -> "DstATop"
    SkBlendMode.kXor -> "Xor"
    SkBlendMode.kPlus -> "Plus"
    SkBlendMode.kModulate -> "Modulate"
    SkBlendMode.kScreen -> "Screen"
    SkBlendMode.kOverlay -> "Overlay"
    SkBlendMode.kDarken -> "Darken"
    SkBlendMode.kLighten -> "Lighten"
    SkBlendMode.kColorDodge -> "ColorDodge"
    SkBlendMode.kColorBurn -> "ColorBurn"
    SkBlendMode.kHardLight -> "HardLight"
    SkBlendMode.kSoftLight -> "SoftLight"
    SkBlendMode.kDifference -> "Difference"
    SkBlendMode.kExclusion -> "Exclusion"
    SkBlendMode.kMultiply -> "Multiply"
    SkBlendMode.kHue -> "Hue"
    SkBlendMode.kSaturation -> "Saturation"
    SkBlendMode.kColor -> "Color"
    SkBlendMode.kLuminosity -> "Luminosity"
}
