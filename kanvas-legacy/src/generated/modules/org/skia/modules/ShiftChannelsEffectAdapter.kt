package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class ShiftChannelsEffectAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<ShiftChannelsEffectAdapter> Make(const skjson::ArrayValue& jprops,
 *                                                   sk_sp<sksg::RenderNode> layer,
 *                                                   const AnimationBuilder* abuilder) {
 *         return sk_sp<ShiftChannelsEffectAdapter>(
 *                     new ShiftChannelsEffectAdapter(jprops, std::move(layer), abuilder));
 *     }
 *
 *     const sk_sp<sksg::ExternalColorFilter>& node() const { return fColorFilter; }
 *
 * private:
 *     ShiftChannelsEffectAdapter(const skjson::ArrayValue& jprops,
 *                                sk_sp<sksg::RenderNode> layer,
 *                                const AnimationBuilder* abuilder)
 *         : fColorFilter(sksg::ExternalColorFilter::Make(std::move(layer))) {
 *         enum : size_t {
 *             kTakeAlphaFrom_Index = 0,
 *               kTakeRedFrom_Index = 1,
 *             kTakeGreenFrom_Index = 2,
 *              kTakeBlueFrom_Index = 3,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *                 .bind(  kTakeRedFrom_Index, fR)
 *                 .bind(kTakeGreenFrom_Index, fG)
 *                 .bind( kTakeBlueFrom_Index, fB)
 *                 .bind(kTakeAlphaFrom_Index, fA);
 *     }
 *
 *     enum class Source : uint8_t {
 *         kAlpha      = 1,
 *         kRed        = 2,
 *         kGreen      = 3,
 *         kBlue       = 4,
 *         kLuminance  = 5,
 *         kHue        = 6,
 *         kLightness  = 7,
 *         kSaturation = 8,
 *         kFullOn     = 9,
 *         kFullOff    = 10,
 *
 *         kMax        = kFullOff
 *     };
 *
 *     void onSync() override {
 *         // TODO: support for HSL sources will require a custom color filter.
 *
 *         static constexpr float gSourceCoeffs[][5] = {
 *             {             0,              0,              0, 1, 0}, // kAlpha
 *             {             1,              0,              0, 0, 0}, // kRed
 *             {             0,              1,              0, 0, 0}, // kGreen
 *             {             0,              0,              1, 0, 0}, // kBlue
 *             {SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B, 0, 0}, // kLuminance
 *             {             0,              0,              0, 0, 0}, // TODO: kHue
 *             {             0,              0,              0, 0, 0}, // TODO: kLightness
 *             {             0,              0,              0, 0, 0}, // TODO: kSaturation
 *             {             0,              0,              0, 0, 1}, // kFullOn
 *             {             0,              0,              0, 0, 0}, // kFullOff
 *         };
 *         static_assert(std::size(gSourceCoeffs) == static_cast<size_t>(Source::kMax), "");
 *
 *         auto coeffs = [](float src) {
 *             // Channel sources are encoded as Source enum values.
 *             // We map these onto our coeffs table.
 *             src = SkTPin(src, 1.0f, static_cast<float>(Source::kMax));
 *             return gSourceCoeffs[static_cast<size_t>(src) - 1];
 *         };
 *
 *         const float* rc = coeffs(fR);
 *         const float* gc = coeffs(fG);
 *         const float* bc = coeffs(fB);
 *         const float* ac = coeffs(fA);
 *
 *         const float cm[] = {
 *             rc[0], rc[1], rc[2], rc[3], rc[4],
 *             gc[0], gc[1], gc[2], gc[3], gc[4],
 *             bc[0], bc[1], bc[2], bc[3], bc[4],
 *             ac[0], ac[1], ac[2], ac[3], ac[4],
 *         };
 *
 *         fColorFilter->setColorFilter(SkColorFilters::Matrix(cm));
 *
 *         // When applied to the alpha channel, kFullOn expands the effect coverage
 *         // to the content bounding box.
 *         fColorFilter->setCoverage(fA == static_cast<float>(Source::kFullOn)
 *                                     ? sksg::ExternalColorFilter::Coverage::kBoundingBox
 *                                     : sksg::ExternalColorFilter::Coverage::kNormal);
 *     }
 *
 *     const sk_sp<sksg::ExternalColorFilter> fColorFilter;
 *
 *     ScalarValue fR = static_cast<float>(Source::kRed),
 *                 fG = static_cast<float>(Source::kGreen),
 *                 fB = static_cast<float>(Source::kBlue),
 *                 fA = static_cast<float>(Source::kAlpha);
 * }
 * ```
 */
public class ShiftChannelsEffectAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder?,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::ExternalColorFilter> fColorFilter
   * ```
   */
  private val fColorFilter: SkSp<ExternalColorFilter> = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fR = static_cast<float>(Source::kRed)
   * ```
   */
  private var fR: ScalarValue = TODO("Initialize fR")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fR = static_cast<float>(Source::kRed),
   *                 fG = static_cast<float>(Source::kGreen)
   * ```
   */
  private var fG: ScalarValue = TODO("Initialize fG")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fR = static_cast<float>(Source::kRed),
   *                 fG = static_cast<float>(Source::kGreen),
   *                 fB = static_cast<float>(Source::kBlue)
   * ```
   */
  private var fB: ScalarValue = TODO("Initialize fB")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fR = static_cast<float>(Source::kRed),
   *                 fG = static_cast<float>(Source::kGreen),
   *                 fB = static_cast<float>(Source::kBlue),
   *                 fA = static_cast<float>(Source::kAlpha)
   * ```
   */
  private var fA: ScalarValue = TODO("Initialize fA")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::ExternalColorFilter>& node() const { return fColorFilter; }
   * ```
   */
  public fun node(): SkSp<ExternalColorFilter> {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         // TODO: support for HSL sources will require a custom color filter.
   *
   *         static constexpr float gSourceCoeffs[][5] = {
   *             {             0,              0,              0, 1, 0}, // kAlpha
   *             {             1,              0,              0, 0, 0}, // kRed
   *             {             0,              1,              0, 0, 0}, // kGreen
   *             {             0,              0,              1, 0, 0}, // kBlue
   *             {SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B, 0, 0}, // kLuminance
   *             {             0,              0,              0, 0, 0}, // TODO: kHue
   *             {             0,              0,              0, 0, 0}, // TODO: kLightness
   *             {             0,              0,              0, 0, 0}, // TODO: kSaturation
   *             {             0,              0,              0, 0, 1}, // kFullOn
   *             {             0,              0,              0, 0, 0}, // kFullOff
   *         };
   *         static_assert(std::size(gSourceCoeffs) == static_cast<size_t>(Source::kMax), "");
   *
   *         auto coeffs = [](float src) {
   *             // Channel sources are encoded as Source enum values.
   *             // We map these onto our coeffs table.
   *             src = SkTPin(src, 1.0f, static_cast<float>(Source::kMax));
   *             return gSourceCoeffs[static_cast<size_t>(src) - 1];
   *         };
   *
   *         const float* rc = coeffs(fR);
   *         const float* gc = coeffs(fG);
   *         const float* bc = coeffs(fB);
   *         const float* ac = coeffs(fA);
   *
   *         const float cm[] = {
   *             rc[0], rc[1], rc[2], rc[3], rc[4],
   *             gc[0], gc[1], gc[2], gc[3], gc[4],
   *             bc[0], bc[1], bc[2], bc[3], bc[4],
   *             ac[0], ac[1], ac[2], ac[3], ac[4],
   *         };
   *
   *         fColorFilter->setColorFilter(SkColorFilters::Matrix(cm));
   *
   *         // When applied to the alpha channel, kFullOn expands the effect coverage
   *         // to the content bounding box.
   *         fColorFilter->setCoverage(fA == static_cast<float>(Source::kFullOn)
   *                                     ? sksg::ExternalColorFilter::Coverage::kBoundingBox
   *                                     : sksg::ExternalColorFilter::Coverage::kNormal);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public enum class Source {
    kAlpha,
    kRed,
    kGreen,
    kBlue,
    kLuminance,
    kHue,
    kLightness,
    kSaturation,
    kFullOn,
    kFullOff,
    kMax,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ShiftChannelsEffectAdapter> Make(const skjson::ArrayValue& jprops,
     *                                                   sk_sp<sksg::RenderNode> layer,
     *                                                   const AnimationBuilder* abuilder) {
     *         return sk_sp<ShiftChannelsEffectAdapter>(
     *                     new ShiftChannelsEffectAdapter(jprops, std::move(layer), abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder?,
    ): SkSp<ShiftChannelsEffectAdapter> {
      TODO("Implement make")
    }
  }
}
