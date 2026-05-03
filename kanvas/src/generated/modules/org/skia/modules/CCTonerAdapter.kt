package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkColor
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class CCTonerAdapter final : public DiscardableAdapterBase<CCTonerAdapter,
 *                                                            sksg::GradientColorFilter> {
 *     public:
 *         CCTonerAdapter(const skjson::ArrayValue& jprops,
 *                        sk_sp<sksg::RenderNode> layer,
 *                        const AnimationBuilder& abuilder,
 *                        std::vector<sk_sp<sksg::Color>> colorNodes)
 *             : INHERITED(sksg::GradientColorFilter::Make(std::move(layer), colorNodes))
 *             , fColorNodes(std::move(colorNodes))
 *         {
 *             enum : size_t {
 *                 kTone_Index        = 0,
 *                 kHiColor_Index     = 1,
 *                 kBrightColor_Index = 2,
 *                 kMidColor_Index    = 3,
 *                 kDarkColor_Index   = 4,
 *                 kShadowColor_Index = 5,
 *                 kBlendAmount_Index = 6,
 *             };
 *
 *
 *             EffectBinder(jprops, abuilder, this)
 *                 .bind(       kTone_Index, fTone)
 *                 .bind(    kHiColor_Index, fHighlights)
 *                 .bind(kBrightColor_Index, fBrights)
 *                 .bind(   kMidColor_Index, fMidtones)
 *                 .bind(  kDarkColor_Index, fDarktones)
 *                 .bind(kShadowColor_Index, fShadows);
 *
 *         }
 *     private:
 *         static SkColor lerpColor(SkColor c0, SkColor c1, float t) {
 *             const auto c0_4f = Sk4f_fromL32(c0),
 *                        c1_4f = Sk4f_fromL32(c1),
 *                        c_4f = c0_4f + (c1_4f - c0_4f) * t;
 *
 *             return Sk4f_toL32(c_4f);
 *         }
 *
 *         void onSync() override {
 *             switch (SkScalarRoundToInt(fTone)) {
 *                 // duotone
 *                 case 1: fColorNodes.at(0)->setColor(fShadows);
 *                         fColorNodes.at(1)->setColor(lerpColor(fShadows, fHighlights, 0.25));
 *                         fColorNodes.at(2)->setColor(lerpColor(fShadows, fHighlights, 0.5));
 *                         fColorNodes.at(3)->setColor(lerpColor(fShadows, fHighlights, 0.75));
 *                         fColorNodes.at(4)->setColor(fHighlights);
 *                         break;
 *                 // tritone
 *                 case 2: fColorNodes.at(0)->setColor(fShadows);
 *                         fColorNodes.at(1)->setColor(lerpColor(fShadows, fMidtones, 0.5));
 *                         fColorNodes.at(2)->setColor(fMidtones);
 *                         fColorNodes.at(3)->setColor(lerpColor(fMidtones, fHighlights, 0.5));
 *                         fColorNodes.at(4)->setColor(fHighlights);
 *                         break;
 *                 // pentone
 *                 case 3: fColorNodes.at(0)->setColor(fShadows);
 *                         fColorNodes.at(1)->setColor(fDarktones);
 *                         fColorNodes.at(2)->setColor(fMidtones);
 *                         fColorNodes.at(3)->setColor(fBrights);
 *                         fColorNodes.at(4)->setColor(fHighlights);
 *                         break;
 *                 // solid
 *                 default: fColorNodes.at(0)->setColor(fMidtones);
 *                         fColorNodes.at(1)->setColor(fMidtones);
 *                         fColorNodes.at(2)->setColor(fMidtones);
 *                         fColorNodes.at(3)->setColor(fMidtones);
 *                         fColorNodes.at(4)->setColor(fMidtones);
 *                         break;
 *             }
 *
 *             this->node()->setWeight((100-fBlend)/100);
 *         }
 *
 *
 *         const std::vector<sk_sp<sksg::Color>> fColorNodes;
 *
 *         ScalarValue fTone = 0;
 *         ColorValue  fHighlights,
 *                     fBrights,
 *                     fMidtones,
 *                     fDarktones,
 *                     fShadows;
 *         ScalarValue fBlend = 0;
 *
 *         using INHERITED = DiscardableAdapterBase<CCTonerAdapter, sksg::GradientColorFilter>;
 * }
 * ```
 */
public class CCTonerAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
  colorNodes: List<SkSp<Color>>,
) : DiscardableAdapterBase(TODO()),
    CCTonerAdapter,
    GradientColorFilter {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<sk_sp<sksg::Color>> fColorNodes
   * ```
   */
  private val fColorNodes: Int = TODO("Initialize fColorNodes")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTone = 0
   * ```
   */
  private var fTone: ScalarValue = TODO("Initialize fTone")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fHighlights
   * ```
   */
  private var fHighlights: ColorValue = TODO("Initialize fHighlights")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fHighlights,
   *                     fBrights
   * ```
   */
  private var fBrights: ColorValue = TODO("Initialize fBrights")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fHighlights,
   *                     fBrights,
   *                     fMidtones
   * ```
   */
  private var fMidtones: ColorValue = TODO("Initialize fMidtones")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fHighlights,
   *                     fBrights,
   *                     fMidtones,
   *                     fDarktones
   * ```
   */
  private var fDarktones: ColorValue = TODO("Initialize fDarktones")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fHighlights,
   *                     fBrights,
   *                     fMidtones,
   *                     fDarktones,
   *                     fShadows
   * ```
   */
  private var fShadows: ColorValue = TODO("Initialize fShadows")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlend = 0
   * ```
   */
  private var fBlend: ScalarValue = TODO("Initialize fBlend")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *             switch (SkScalarRoundToInt(fTone)) {
   *                 // duotone
   *                 case 1: fColorNodes.at(0)->setColor(fShadows);
   *                         fColorNodes.at(1)->setColor(lerpColor(fShadows, fHighlights, 0.25));
   *                         fColorNodes.at(2)->setColor(lerpColor(fShadows, fHighlights, 0.5));
   *                         fColorNodes.at(3)->setColor(lerpColor(fShadows, fHighlights, 0.75));
   *                         fColorNodes.at(4)->setColor(fHighlights);
   *                         break;
   *                 // tritone
   *                 case 2: fColorNodes.at(0)->setColor(fShadows);
   *                         fColorNodes.at(1)->setColor(lerpColor(fShadows, fMidtones, 0.5));
   *                         fColorNodes.at(2)->setColor(fMidtones);
   *                         fColorNodes.at(3)->setColor(lerpColor(fMidtones, fHighlights, 0.5));
   *                         fColorNodes.at(4)->setColor(fHighlights);
   *                         break;
   *                 // pentone
   *                 case 3: fColorNodes.at(0)->setColor(fShadows);
   *                         fColorNodes.at(1)->setColor(fDarktones);
   *                         fColorNodes.at(2)->setColor(fMidtones);
   *                         fColorNodes.at(3)->setColor(fBrights);
   *                         fColorNodes.at(4)->setColor(fHighlights);
   *                         break;
   *                 // solid
   *                 default: fColorNodes.at(0)->setColor(fMidtones);
   *                         fColorNodes.at(1)->setColor(fMidtones);
   *                         fColorNodes.at(2)->setColor(fMidtones);
   *                         fColorNodes.at(3)->setColor(fMidtones);
   *                         fColorNodes.at(4)->setColor(fMidtones);
   *                         break;
   *             }
   *
   *             this->node()->setWeight((100-fBlend)/100);
   *         }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkColor lerpColor(SkColor c0, SkColor c1, float t) {
     *             const auto c0_4f = Sk4f_fromL32(c0),
     *                        c1_4f = Sk4f_fromL32(c1),
     *                        c_4f = c0_4f + (c1_4f - c0_4f) * t;
     *
     *             return Sk4f_toL32(c_4f);
     *         }
     * ```
     */
    public override fun lerpColor(
      c0: SkColor,
      c1: SkColor,
      t: Float,
    ): SkColor {
      TODO("Implement lerpColor")
    }
  }
}
