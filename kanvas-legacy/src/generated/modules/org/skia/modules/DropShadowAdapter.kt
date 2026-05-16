package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class DropShadowAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<DropShadowAdapter> Make(const skjson::ArrayValue& jprops,
 *                                          sk_sp<sksg::RenderNode> layer,
 *                                          const AnimationBuilder& abuilder) {
 *         enum : size_t {
 *             kShadowColor_Index = 0,
 *                 kOpacity_Index = 1,
 *               kDirection_Index = 2,
 *                kDistance_Index = 3,
 *                kSoftness_Index = 4,
 *              kShadowOnly_Index = 5,
 *         };
 *
 *         sk_sp<DropShadowAdapter> adapter(new DropShadowAdapter(std::move(layer)));
 *
 *         EffectBinder(jprops, abuilder, adapter.get())
 *                 .bind(kShadowColor_Index, adapter->fColor    )
 *                 .bind(    kOpacity_Index, adapter->fOpacity  )
 *                 .bind(  kDirection_Index, adapter->fDirection)
 *                 .bind(   kDistance_Index, adapter->fDistance )
 *                 .bind(   kSoftness_Index, adapter->fSoftness )
 *                 .bind( kShadowOnly_Index, adapter->fShdwOnly );
 *
 *         return adapter;
 *     }
 *
 *     const sk_sp<sksg::RenderNode>& node() const { return fImageFilterEffect; }
 *
 * private:
 *     explicit DropShadowAdapter(sk_sp<sksg::RenderNode> layer)
 *         : fDropShadow(sksg::DropShadowImageFilter::Make())
 *         , fImageFilterEffect(sksg::ImageFilterEffect::Make(std::move(layer), fDropShadow)) {}
 *
 *     void onSync() override {
 *         // fColor -> RGB, fOpacity -> A
 *         const SkColor color = fColor;
 *         fDropShadow->setColor(SkColorSetA(color, SkTPin(SkScalarRoundToInt(fOpacity), 0, 255)));
 *
 *         // The offset is specified in terms of a bearing + distance.
 *         const auto rad = SkDegreesToRadians(90 - fDirection);
 *         fDropShadow->setOffset(SkVector::Make( fDistance * SkScalarCos(rad),
 *                                               -fDistance * SkScalarSin(rad)));
 *
 *         const auto sigma = fSoftness * kBlurSizeToSigma;
 *         fDropShadow->setSigma(SkVector::Make(sigma, sigma));
 *
 *         fDropShadow->setMode(SkToBool(fShdwOnly)
 *                                 ? sksg::DropShadowImageFilter::Mode::kShadowOnly
 *                                 : sksg::DropShadowImageFilter::Mode::kShadowAndForeground);
 *     }
 *
 *     const sk_sp<sksg::DropShadowImageFilter> fDropShadow;
 *     const sk_sp<sksg::RenderNode>            fImageFilterEffect;
 *
 *     ColorValue  fColor     = { 0, 0, 0, 1 };
 *     ScalarValue fOpacity   = 255,
 *                 fDirection = 0,
 *                 fDistance  = 0,
 *                 fSoftness  = 0,
 *                 fShdwOnly  = 0;
 * }
 * ```
 */
public class DropShadowAdapter public constructor(
  layer: SkSp<RenderNode>,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::DropShadowImageFilter> fDropShadow
   * ```
   */
  private val fDropShadow: SkSp<DropShadowImageFilter> = TODO("Initialize fDropShadow")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode>            fImageFilterEffect
   * ```
   */
  private val fImageFilterEffect: SkSp<RenderNode> = TODO("Initialize fImageFilterEffect")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fColor
   * ```
   */
  private var fColor: ColorValue = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity   = 255
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity   = 255,
   *                 fDirection = 0
   * ```
   */
  private var fDirection: ScalarValue = TODO("Initialize fDirection")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity   = 255,
   *                 fDirection = 0,
   *                 fDistance  = 0
   * ```
   */
  private var fDistance: ScalarValue = TODO("Initialize fDistance")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity   = 255,
   *                 fDirection = 0,
   *                 fDistance  = 0,
   *                 fSoftness  = 0
   * ```
   */
  private var fSoftness: ScalarValue = TODO("Initialize fSoftness")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity   = 255,
   *                 fDirection = 0,
   *                 fDistance  = 0,
   *                 fSoftness  = 0,
   *                 fShdwOnly  = 0
   * ```
   */
  private var fShdwOnly: ScalarValue = TODO("Initialize fShdwOnly")

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
   *         // fColor -> RGB, fOpacity -> A
   *         const SkColor color = fColor;
   *         fDropShadow->setColor(SkColorSetA(color, SkTPin(SkScalarRoundToInt(fOpacity), 0, 255)));
   *
   *         // The offset is specified in terms of a bearing + distance.
   *         const auto rad = SkDegreesToRadians(90 - fDirection);
   *         fDropShadow->setOffset(SkVector::Make( fDistance * SkScalarCos(rad),
   *                                               -fDistance * SkScalarSin(rad)));
   *
   *         const auto sigma = fSoftness * kBlurSizeToSigma;
   *         fDropShadow->setSigma(SkVector::Make(sigma, sigma));
   *
   *         fDropShadow->setMode(SkToBool(fShdwOnly)
   *                                 ? sksg::DropShadowImageFilter::Mode::kShadowOnly
   *                                 : sksg::DropShadowImageFilter::Mode::kShadowAndForeground);
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
     * static sk_sp<DropShadowAdapter> Make(const skjson::ArrayValue& jprops,
     *                                          sk_sp<sksg::RenderNode> layer,
     *                                          const AnimationBuilder& abuilder) {
     *         enum : size_t {
     *             kShadowColor_Index = 0,
     *                 kOpacity_Index = 1,
     *               kDirection_Index = 2,
     *                kDistance_Index = 3,
     *                kSoftness_Index = 4,
     *              kShadowOnly_Index = 5,
     *         };
     *
     *         sk_sp<DropShadowAdapter> adapter(new DropShadowAdapter(std::move(layer)));
     *
     *         EffectBinder(jprops, abuilder, adapter.get())
     *                 .bind(kShadowColor_Index, adapter->fColor    )
     *                 .bind(    kOpacity_Index, adapter->fOpacity  )
     *                 .bind(  kDirection_Index, adapter->fDirection)
     *                 .bind(   kDistance_Index, adapter->fDistance )
     *                 .bind(   kSoftness_Index, adapter->fSoftness )
     *                 .bind( kShadowOnly_Index, adapter->fShdwOnly );
     *
     *         return adapter;
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder,
    ): SkSp<DropShadowAdapter> {
      TODO("Implement make")
    }
  }
}
