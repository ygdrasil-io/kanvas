package org.skia.modules

import kotlin.Any
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class TritoneAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<TritoneAdapter> Make(const skjson::ArrayValue& jprops,
 *                                       sk_sp<sksg::RenderNode> layer,
 *                                       const AnimationBuilder& abuilder) {
 *         return sk_sp<TritoneAdapter>(new TritoneAdapter(jprops, std::move(layer), abuilder));
 *     }
 *
 *     const auto& node() const { return fCF; }
 *
 * private:
 *     TritoneAdapter(const skjson::ArrayValue& jprops,
 *                    sk_sp<sksg::RenderNode> layer,
 *                    const AnimationBuilder& abuilder)
 *         : fLoColorNode(sksg::Color::Make(SK_ColorBLACK))
 *         , fMiColorNode(sksg::Color::Make(SK_ColorBLACK))
 *         , fHiColorNode(sksg::Color::Make(SK_ColorBLACK))
 *         , fCF(sksg::GradientColorFilter::Make(std::move(layer),
 *                                               { fLoColorNode, fMiColorNode, fHiColorNode })) {
 *         enum : size_t {
 *                 kHiColor_Index = 0,
 *                 kMiColor_Index = 1,
 *                 kLoColor_Index = 2,
 *             kBlendAmount_Index = 3,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(    kHiColor_Index, fHiColor)
 *             .bind(    kMiColor_Index, fMiColor)
 *             .bind(    kLoColor_Index, fLoColor)
 *             .bind(kBlendAmount_Index, fWeight );
 *     }
 *
 *     void onSync() override {
 *         fLoColorNode->setColor(fLoColor);
 *         fMiColorNode->setColor(fMiColor);
 *         fHiColorNode->setColor(fHiColor);
 *
 *         // 100-based, inverted
 *         fCF->setWeight((100 - fWeight) / 100);
 *     }
 *
 *     const sk_sp<sksg::Color> fLoColorNode,
 *                              fMiColorNode,
 *                              fHiColorNode;
 *     const sk_sp<sksg::GradientColorFilter> fCF;
 *
 *     ColorValue  fLoColor,
 *                 fMiColor,
 *                 fHiColor;
 *     ScalarValue fWeight = 0;
 * }
 * ```
 */
public class TritoneAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color> fLoColorNode
   * ```
   */
  private val fLoColorNode: SkSp<Color> = TODO("Initialize fLoColorNode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color> fLoColorNode,
   *                              fMiColorNode
   * ```
   */
  private val fMiColorNode: SkSp<Color> = TODO("Initialize fMiColorNode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color> fLoColorNode,
   *                              fMiColorNode,
   *                              fHiColorNode
   * ```
   */
  private val fHiColorNode: SkSp<Color> = TODO("Initialize fHiColorNode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::GradientColorFilter> fCF
   * ```
   */
  private val fCF: SkSp<GradientColorFilter> = TODO("Initialize fCF")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fLoColor
   * ```
   */
  private var fLoColor: ColorValue = TODO("Initialize fLoColor")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fLoColor,
   *                 fMiColor
   * ```
   */
  private var fMiColor: ColorValue = TODO("Initialize fMiColor")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fLoColor,
   *                 fMiColor,
   *                 fHiColor
   * ```
   */
  private var fHiColor: ColorValue = TODO("Initialize fHiColor")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fWeight = 0
   * ```
   */
  private var fWeight: ScalarValue = TODO("Initialize fWeight")

  /**
   * C++ original:
   * ```cpp
   * const auto& node() const { return fCF; }
   * ```
   */
  public fun node(): Any {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         fLoColorNode->setColor(fLoColor);
   *         fMiColorNode->setColor(fMiColor);
   *         fHiColorNode->setColor(fHiColor);
   *
   *         // 100-based, inverted
   *         fCF->setWeight((100 - fWeight) / 100);
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
     * static sk_sp<TritoneAdapter> Make(const skjson::ArrayValue& jprops,
     *                                       sk_sp<sksg::RenderNode> layer,
     *                                       const AnimationBuilder& abuilder) {
     *         return sk_sp<TritoneAdapter>(new TritoneAdapter(jprops, std::move(layer), abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder,
    ): SkSp<TritoneAdapter> {
      TODO("Implement make")
    }
  }
}
