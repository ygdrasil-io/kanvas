package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class GaussianBlurEffectAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<GaussianBlurEffectAdapter> Make(const skjson::ArrayValue& jprops,
 *                                                  sk_sp<sksg::RenderNode> layer,
 *                                                  const AnimationBuilder* abuilder) {
 *         return sk_sp<GaussianBlurEffectAdapter>(new GaussianBlurEffectAdapter(jprops,
 *                                                                               std::move(layer),
 *                                                                               abuilder));
 *     }
 *
 *     const sk_sp<sksg::RenderNode>& node() const { return fImageFilterEffect; }
 *
 * private:
 *     GaussianBlurEffectAdapter(const skjson::ArrayValue& jprops,
 *                               sk_sp<sksg::RenderNode> layer,
 *                               const AnimationBuilder* abuilder)
 *         : fBlur(sksg::BlurImageFilter::Make())
 *         , fImageFilterEffect(sksg::ImageFilterEffect::Make(std::move(layer), fBlur)) {
 *         enum : size_t {
 *             kBlurriness_Index = 0,
 *             kDimensions_Index = 1,
 *             kRepeatEdge_Index = 2,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *                 .bind(kBlurriness_Index, fBlurriness)
 *                 .bind(kDimensions_Index, fDimensions)
 *                 .bind(kRepeatEdge_Index, fRepeatEdge);
 *     }
 *
 *     void onSync() override {
 *         static constexpr SkVector kDimensionsMap[] = {
 *             { 1, 1 }, // 1 -> horizontal and vertical
 *             { 1, 0 }, // 2 -> horizontal
 *             { 0, 1 }, // 3 -> vertical
 *         };
 *
 *         const auto dim_index = SkTPin<size_t>(static_cast<size_t>(fDimensions),
 *                                               1, std::size(kDimensionsMap)) - 1;
 *
 *         const auto sigma = fBlurriness * kBlurSizeToSigma;
 *
 *         fBlur->setSigma({ sigma * kDimensionsMap[dim_index].x(),
 *                           sigma * kDimensionsMap[dim_index].y() });
 *
 *         // 0 -> repeat edge pixels: off
 *         // 1 -> repeat edge pixels: on
 *         const auto repeat_edge = static_cast<bool>(fRepeatEdge);
 *
 *         // Repeat edge pixels implies two things:
 *         //  - the blur uses kClamp tiling
 *         //  - the output is cropped to content size
 *         fBlur->setTileMode(repeat_edge
 *             ? SkTileMode::kClamp
 *             : SkTileMode::kDecal);
 *         static_cast<sksg::ImageFilterEffect*>(fImageFilterEffect.get())->setCropping(repeat_edge
 *             ? sksg::ImageFilterEffect::Cropping::kContent
 *             : sksg::ImageFilterEffect::Cropping::kNone);
 *     }
 *
 *     const sk_sp<sksg::BlurImageFilter> fBlur;
 *     const sk_sp<sksg::RenderNode>      fImageFilterEffect;
 *
 *     ScalarValue fBlurriness = 0, // Controls the blur sigma.
 *                 fDimensions = 1, // 1 -> horizontal & vertical, 2 -> horizontal, 3 -> vertical
 *                 fRepeatEdge = 0; // 0 -> clamp, 1 -> repeat
 * }
 * ```
 */
public class GaussianBlurEffectAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder?,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::BlurImageFilter> fBlur
   * ```
   */
  private val fBlur: SkSp<BlurImageFilter> = TODO("Initialize fBlur")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode>      fImageFilterEffect
   * ```
   */
  private val fImageFilterEffect: SkSp<RenderNode> = TODO("Initialize fImageFilterEffect")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlurriness = 0
   * ```
   */
  private var fBlurriness: ScalarValue = TODO("Initialize fBlurriness")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlurriness = 0, // Controls the blur sigma.
   *                 fDimensions = 1
   * ```
   */
  private var fDimensions: ScalarValue = TODO("Initialize fDimensions")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlurriness = 0, // Controls the blur sigma.
   *                 fDimensions = 1, // 1 -> horizontal & vertical, 2 -> horizontal, 3 -> vertical
   *                 fRepeatEdge = 0
   * ```
   */
  private var fRepeatEdge: ScalarValue = TODO("Initialize fRepeatEdge")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode>& node() const { return fImageFilterEffect; }
   * ```
   */
  public fun node(): SkSp<RenderNode> {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         static constexpr SkVector kDimensionsMap[] = {
   *             { 1, 1 }, // 1 -> horizontal and vertical
   *             { 1, 0 }, // 2 -> horizontal
   *             { 0, 1 }, // 3 -> vertical
   *         };
   *
   *         const auto dim_index = SkTPin<size_t>(static_cast<size_t>(fDimensions),
   *                                               1, std::size(kDimensionsMap)) - 1;
   *
   *         const auto sigma = fBlurriness * kBlurSizeToSigma;
   *
   *         fBlur->setSigma({ sigma * kDimensionsMap[dim_index].x(),
   *                           sigma * kDimensionsMap[dim_index].y() });
   *
   *         // 0 -> repeat edge pixels: off
   *         // 1 -> repeat edge pixels: on
   *         const auto repeat_edge = static_cast<bool>(fRepeatEdge);
   *
   *         // Repeat edge pixels implies two things:
   *         //  - the blur uses kClamp tiling
   *         //  - the output is cropped to content size
   *         fBlur->setTileMode(repeat_edge
   *             ? SkTileMode::kClamp
   *             : SkTileMode::kDecal);
   *         static_cast<sksg::ImageFilterEffect*>(fImageFilterEffect.get())->setCropping(repeat_edge
   *             ? sksg::ImageFilterEffect::Cropping::kContent
   *             : sksg::ImageFilterEffect::Cropping::kNone);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GaussianBlurEffectAdapter> Make(const skjson::ArrayValue& jprops,
     *                                                  sk_sp<sksg::RenderNode> layer,
     *                                                  const AnimationBuilder* abuilder) {
     *         return sk_sp<GaussianBlurEffectAdapter>(new GaussianBlurEffectAdapter(jprops,
     *                                                                               std::move(layer),
     *                                                                               abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder?,
    ): SkSp<GaussianBlurEffectAdapter> {
      TODO("Implement make")
    }
  }
}
