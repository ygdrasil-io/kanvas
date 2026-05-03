package org.skia.effects

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SkGradient {
 * public:
 *     struct Interpolation {
 *         enum class InPremul : bool { kNo = false, kYes = true };
 *
 *         enum class ColorSpace : uint8_t {
 *             // Default Skia behavior: interpolate in the color space of the destination surface
 *             kDestination,
 *
 *             // https://www.w3.org/TR/css-color-4/#interpolation-space
 *             kSRGBLinear,
 *             kLab,
 *             kOKLab,
 *             // This is the same as kOKLab, except it has a simplified version of the CSS gamut
 *             // mapping algorithm (https://www.w3.org/TR/css-color-4/#css-gamut-mapping)
 *             // into Rec2020 space applied to it.
 *             // Warning: This space is experimental and should not be used in production.
 *             kOKLabGamutMap,
 *             kLCH,
 *             kOKLCH,
 *             // This is the same as kOKLCH, except it has the same gamut mapping applied to it
 *             // as kOKLabGamutMap does.
 *             // Warning: This space is experimental and should not be used in production.
 *             kOKLCHGamutMap,
 *             kSRGB,
 *             kHSL,
 *             kHWB,
 *
 *             kDisplayP3,
 *             kRec2020,
 *             kProphotoRGB,
 *             kA98RGB,
 *
 *             kLastColorSpace = kA98RGB,
 *         };
 *         static constexpr int kColorSpaceCount = static_cast<int>(ColorSpace::kLastColorSpace) + 1;
 *
 *         enum class HueMethod : uint8_t {
 *             // https://www.w3.org/TR/css-color-4/#hue-interpolation
 *             kShorter,
 *             kLonger,
 *             kIncreasing,
 *             kDecreasing,
 *
 *             kLastHueMethod = kDecreasing,
 *         };
 *         static constexpr int kHueMethodCount = static_cast<int>(HueMethod::kLastHueMethod) + 1;
 *
 *         InPremul fInPremul = InPremul::kNo;
 *         ColorSpace fColorSpace = ColorSpace::kDestination;
 *         HueMethod fHueMethod = HueMethod::kShorter;  // Only relevant for LCH, OKLCH, HSL, or HWB
 *
 *         // legacy compatibility -- remove when we can
 *         static Interpolation FromFlags(uint32_t flags) {
 *             return {flags & 1 ? InPremul::kYes : InPremul::kNo,
 *                     ColorSpace::kDestination,
 *                     HueMethod::kShorter};
 *         }
 *     };
 *
 *     /**
 *      *  Specification for the colors in a gradient.
 *      *
 *      *  @param  colors  The span of colors for the gradient.
 *      *  @param  pos     Relative positions of each color across the gradient. If empty,
 *      *                  the the colors are distributed evenly. If this is not null, the values
 *      *                  must lie between 0.0 and 1.0, and be strictly increasing. If the first
 *      *                  value is not 0.0, then an additional color stop is added at position 0.0,
 *      *                  with the same color as colors[0]. If the the last value is less than 1.0,
 *      *                  then an additional color stop is added at position 1.0, with the same color
 *      *                  as colors[count - 1].
 *      *  @param mode     Tiling mode for the gradient.
 *      *  @param cs       Optional colorspace associated with the span of colors. If this is null,
 *      *                  the colors are treated as sRGB.
 *      */
 *     class Colors {
 *     public:
 *         Colors() {}
 *         Colors(SkSpan<const SkColor4f> colors,
 *                SkSpan<const float> pos,
 *                SkTileMode mode,
 *                sk_sp<SkColorSpace> cs = nullptr)
 *                 : fColors(colors), fPos(pos), fColorSpace(std::move(cs)), fTileMode(mode) {
 *             SkASSERT(fPos.size() == 0 || fPos.size() == fColors.size());
 *
 *             // throw away inconsistent inputs
 *             if (fPos.size() != fColors.size()) {
 *                 fPos = {};
 *             }
 *         }
 *
 *         Colors(SkSpan<const SkColor4f> colors, SkTileMode tm, sk_sp<SkColorSpace> cs = nullptr)
 *                 : Colors(colors, {}, tm, std::move(cs)) {}
 *
 *         SkSpan<const SkColor4f> colors() const { return fColors; }
 *         SkSpan<const float> positions() const { return fPos; }
 *         const sk_sp<SkColorSpace>& colorSpace() const { return fColorSpace; }
 *         SkTileMode tileMode() const { return fTileMode; }
 *
 *     private:
 *         SkSpan<const SkColor4f> fColors;
 *         SkSpan<const float> fPos;
 *         sk_sp<SkColorSpace> fColorSpace;
 *         SkTileMode fTileMode = SkTileMode::kClamp;
 *     };
 *
 *     SkGradient() {}
 *     SkGradient(const Colors& colors, const Interpolation& interp)
 *             : fColors(colors), fInterpolation(interp) {}
 *
 *     const Colors& colors() const { return fColors; }
 *     const Interpolation& interpolation() const { return fInterpolation; }
 *
 * private:
 *     Colors fColors;
 *     Interpolation fInterpolation;
 * }
 * ```
 */
public data class SkGradient public constructor(
  /**
   * C++ original:
   * ```cpp
   * Colors fColors
   * ```
   */
  private var fColors: Colors,
  /**
   * C++ original:
   * ```cpp
   * Interpolation fInterpolation
   * ```
   */
  private var fInterpolation: Interpolation,
) {
  /**
   * C++ original:
   * ```cpp
   * const Colors& colors() const { return fColors; }
   * ```
   */
  private fun colors(): Colors {
    TODO("Implement colors")
  }

  /**
   * C++ original:
   * ```cpp
   * const Interpolation& interpolation() const { return fInterpolation; }
   * ```
   */
  private fun interpolation(): Interpolation {
    TODO("Implement interpolation")
  }

  public data class Interpolation public constructor(
    public var fInPremul: org.skia.gpu.Interpolation.InPremul,
    public var fColorSpace: org.skia.gpu.Interpolation.ColorSpace,
    public var fHueMethod: org.skia.gpu.Interpolation.HueMethod,
  ) {
    public enum class InPremul {
      kNo,
      kYes,
    }

    public enum class ColorSpace {
      kDestination,
      kSRGBLinear,
      kLab,
      kOKLab,
      kOKLabGamutMap,
      kLCH,
      kOKLCH,
      kOKLCHGamutMap,
      kSRGB,
      kHSL,
      kHWB,
      kDisplayP3,
      kRec2020,
      kProphotoRGB,
      kA98RGB,
      kLastColorSpace,
    }

    public enum class HueMethod {
      kShorter,
      kLonger,
      kIncreasing,
      kDecreasing,
      kLastHueMethod,
    }

    public companion object {
      public val kColorSpaceCount: Int = TODO("Initialize kColorSpaceCount")

      public val kHueMethodCount: Int = TODO("Initialize kHueMethodCount")

      public fun fromFlags(flags: UInt): org.skia.gpu.Interpolation {
        TODO("Implement fromFlags")
      }
    }
  }

  public data class Colors public constructor() {
    public fun fPos(param0: Int): undefined.Colors {
      TODO("Implement fPos")
    }

    public fun fColorSpace(param0: Int): undefined.Colors {
      TODO("Implement fColorSpace")
    }

    public fun fTileMode(param0: Int): undefined.Colors {
      TODO("Implement fTileMode")
    }
  }
}
