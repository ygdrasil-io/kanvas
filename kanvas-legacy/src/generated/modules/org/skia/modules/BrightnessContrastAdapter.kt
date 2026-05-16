package org.skia.modules

import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class BrightnessContrastAdapter final : public DiscardableAdapterBase<BrightnessContrastAdapter,
 *                                                                       sksg::ExternalColorFilter> {
 * public:
 *     BrightnessContrastAdapter(const skjson::ArrayValue& jprops,
 *                               const AnimationBuilder& abuilder,
 *                               sk_sp<sksg::RenderNode> layer)
 *         : INHERITED(sksg::ExternalColorFilter::Make(std::move(layer)))
 *         , fBrightnessEffect(SkRuntimeEffect::MakeForColorFilter(SkString(BRIGHTNESS_EFFECT)).effect)
 *         , fContrastEffect(SkRuntimeEffect::MakeForColorFilter(SkString(CONTRAST_EFFECT)).effect) {
 *         SkASSERT(fBrightnessEffect);
 *         SkASSERT(fContrastEffect);
 *
 *         enum : size_t {
 *             kBrightness_Index = 0,
 *               kContrast_Index = 1,
 *              kUseLegacy_Index = 2,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(kBrightness_Index, fBrightness)
 *             .bind(  kContrast_Index, fContrast  )
 *             .bind( kUseLegacy_Index, fUseLegacy );
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setColorFilter(SkScalarRoundToInt(fUseLegacy)
 *                                         ? this->makeLegacyCF()
 *                                         : this->makeCF());
 *     }
 *
 *     sk_sp<SkColorFilter> makeLegacyCF() const {
 *         // In 'legacy' mode, brightness is
 *         //
 *         //   - in the [-100..100] range
 *         //   - applied component-wise as a direct offset (255-based)
 *         //   - (neutral value: 0)
 *         //   - transfer function: https://www.desmos.com/calculator/zne0oqwwzb
 *         //
 *         // while contrast is
 *         //
 *         //   - in the [-100..100] range
 *         //   - applied as a component-wise linear transformation (scale+offset), such that
 *         //
 *         //       -100 always yields mid-gray: contrast(x, -100) == 0.5
 *         //          0 is the neutral value:   contrast(x,    0) == x
 *         //        100 always yields white:    contrast(x,  100) == 1
 *         //
 *         //   - transfer function: https://www.desmos.com/calculator/x5rxzhowhs
 *         //
 *
 *         // Normalize to [-1..1]
 *         const auto brightness = SkTPin(fBrightness, -100.0f, 100.0f) / 255, // [-100/255 .. 100/255]
 *                    contrast   = SkTPin(fContrast  , -100.0f, 100.0f) / 100; // [      -1 ..       1]
 *
 *         // The component scale is derived from contrast:
 *         //
 *         //   Contrast[-1 .. 0] -> Scale[0 .. 1]
 *         //   Contrast( 0 .. 1] -> Scale(1 .. +inf)
 *         const auto S = contrast > 0
 *             ? 1 / std::max(1 - contrast, SK_ScalarNearlyZero)
 *             : 1 + contrast;
 *
 *         // The component offset is derived from both brightness and contrast:
 *         //
 *         //   Brightness[-100/255 .. 100/255] -> Offset[-100/255 .. 100/255]
 *         //   Contrast  [      -1 ..       0] -> Offset[     0.5 ..       0]
 *         //   Contrast  (       0 ..       1] -> Offset(       0 ..    -inf)
 *         //
 *         // Why do these pre/post compose depending on contrast scale, you ask?
 *         // Because AE - that's why!
 *         const auto B = 0.5f * (1 - S) + brightness * std::max(S, 1.0f);
 *
 *         const float cm[] = {
 *             S, 0, 0, 0, B,
 *             0, S, 0, 0, B,
 *             0, 0, S, 0, B,
 *             0, 0, 0, 1, 0,
 *         };
 *
 *         return SkColorFilters::Matrix(cm);
 *     }
 *
 *     sk_sp<SkColorFilter> makeCF() const {
 *         const auto brightness = SkTPin(fBrightness, -150.0f, 150.0f) / 150, // [-1.0 .. 1]
 *                      contrast = SkTPin(fContrast  ,  -50.0f, 100.0f) / 100; // [-0.5 .. 1]
 *
 *         auto b_eff = SkScalarNearlyZero(brightness)
 *                    ? nullptr
 *                    : fBrightnessEffect->makeColorFilter(make_brightness_coeffs(brightness)),
 *              c_eff = SkScalarNearlyZero(fContrast)
 *                    ? nullptr
 *                    : fContrastEffect->makeColorFilter(make_contrast_coeffs(contrast));
 *
 *         return SkColorFilters::Compose(std::move(c_eff), std::move(b_eff));
 *     }
 *
 *     const sk_sp<SkRuntimeEffect> fBrightnessEffect,
 *                                    fContrastEffect;
 *
 *     ScalarValue fBrightness = 0,
 *                 fContrast   = 0,
 *                 fUseLegacy  = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<BrightnessContrastAdapter, sksg::ExternalColorFilter>;
 * }
 * ```
 */
public class BrightnessContrastAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  layer: SkSp<RenderNode>,
) : DiscardableAdapterBase(TODO()),
    BrightnessContrastAdapter,
    ExternalColorFilter {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkRuntimeEffect> fBrightnessEffect
   * ```
   */
  private val fBrightnessEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fBrightnessEffect")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkRuntimeEffect> fBrightnessEffect,
   *                                    fContrastEffect
   * ```
   */
  private val fContrastEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fContrastEffect")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBrightness = 0
   * ```
   */
  private var fBrightness: ScalarValue = TODO("Initialize fBrightness")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBrightness = 0,
   *                 fContrast   = 0
   * ```
   */
  private var fContrast: ScalarValue = TODO("Initialize fContrast")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBrightness = 0,
   *                 fContrast   = 0,
   *                 fUseLegacy  = 0
   * ```
   */
  private var fUseLegacy: ScalarValue = TODO("Initialize fUseLegacy")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setColorFilter(SkScalarRoundToInt(fUseLegacy)
   *                                         ? this->makeLegacyCF()
   *                                         : this->makeCF());
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> makeLegacyCF() const {
   *         // In 'legacy' mode, brightness is
   *         //
   *         //   - in the [-100..100] range
   *         //   - applied component-wise as a direct offset (255-based)
   *         //   - (neutral value: 0)
   *         //   - transfer function: https://www.desmos.com/calculator/zne0oqwwzb
   *         //
   *         // while contrast is
   *         //
   *         //   - in the [-100..100] range
   *         //   - applied as a component-wise linear transformation (scale+offset), such that
   *         //
   *         //       -100 always yields mid-gray: contrast(x, -100) == 0.5
   *         //          0 is the neutral value:   contrast(x,    0) == x
   *         //        100 always yields white:    contrast(x,  100) == 1
   *         //
   *         //   - transfer function: https://www.desmos.com/calculator/x5rxzhowhs
   *         //
   *
   *         // Normalize to [-1..1]
   *         const auto brightness = SkTPin(fBrightness, -100.0f, 100.0f) / 255, // [-100/255 .. 100/255]
   *                    contrast   = SkTPin(fContrast  , -100.0f, 100.0f) / 100; // [      -1 ..       1]
   *
   *         // The component scale is derived from contrast:
   *         //
   *         //   Contrast[-1 .. 0] -> Scale[0 .. 1]
   *         //   Contrast( 0 .. 1] -> Scale(1 .. +inf)
   *         const auto S = contrast > 0
   *             ? 1 / std::max(1 - contrast, SK_ScalarNearlyZero)
   *             : 1 + contrast;
   *
   *         // The component offset is derived from both brightness and contrast:
   *         //
   *         //   Brightness[-100/255 .. 100/255] -> Offset[-100/255 .. 100/255]
   *         //   Contrast  [      -1 ..       0] -> Offset[     0.5 ..       0]
   *         //   Contrast  (       0 ..       1] -> Offset(       0 ..    -inf)
   *         //
   *         // Why do these pre/post compose depending on contrast scale, you ask?
   *         // Because AE - that's why!
   *         const auto B = 0.5f * (1 - S) + brightness * std::max(S, 1.0f);
   *
   *         const float cm[] = {
   *             S, 0, 0, 0, B,
   *             0, S, 0, 0, B,
   *             0, 0, S, 0, B,
   *             0, 0, 0, 1, 0,
   *         };
   *
   *         return SkColorFilters::Matrix(cm);
   *     }
   * ```
   */
  public override fun makeLegacyCF(): SkSp<SkColorFilter> {
    TODO("Implement makeLegacyCF")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> makeCF() const {
   *         const auto brightness = SkTPin(fBrightness, -150.0f, 150.0f) / 150, // [-1.0 .. 1]
   *                      contrast = SkTPin(fContrast  ,  -50.0f, 100.0f) / 100; // [-0.5 .. 1]
   *
   *         auto b_eff = SkScalarNearlyZero(brightness)
   *                    ? nullptr
   *                    : fBrightnessEffect->makeColorFilter(make_brightness_coeffs(brightness)),
   *              c_eff = SkScalarNearlyZero(fContrast)
   *                    ? nullptr
   *                    : fContrastEffect->makeColorFilter(make_contrast_coeffs(contrast));
   *
   *         return SkColorFilters::Compose(std::move(c_eff), std::move(b_eff));
   *     }
   * ```
   */
  public override fun makeCF(): SkSp<SkColorFilter> {
    TODO("Implement makeCF")
  }
}
