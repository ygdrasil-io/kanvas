package org.skia.modules

import kotlin.Any
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class TintAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<TintAdapter> Make(const skjson::ArrayValue& jprops,
 *                                    sk_sp<sksg::RenderNode> layer,
 *                                    const AnimationBuilder& abuilder) {
 *         return sk_sp<TintAdapter>(new TintAdapter(jprops, std::move(layer), abuilder));
 *     }
 *
 *     const auto& node() const { return fFilterNode; }
 *
 * private:
 *     TintAdapter(const skjson::ArrayValue& jprops,
 *                 sk_sp<sksg::RenderNode> layer,
 *                 const AnimationBuilder& abuilder)
 *         : fColorNode0(sksg::Color::Make(SK_ColorBLACK))
 *         , fColorNode1(sksg::Color::Make(SK_ColorBLACK))
 *         , fFilterNode(sksg::GradientColorFilter::Make(std::move(layer), fColorNode0, fColorNode1)) {
 *
 *         enum : size_t {
 *             kMapBlackTo_Index = 0,
 *             kMapWhiteTo_Index = 1,
 *             kAmount_Index     = 2,
 *             // kOpacity_Index    = 3, // currently unused (not exported)
 *
 *             kMax_Index        = kAmount_Index,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(kMapBlackTo_Index, fMapBlackTo)
 *             .bind(kMapWhiteTo_Index, fMapWhiteTo)
 *             .bind(    kAmount_Index, fAmount    );
 *     }
 *
 *     void onSync() override {
 *         fColorNode0->setColor(fMapBlackTo);
 *         fColorNode1->setColor(fMapWhiteTo);
 *
 *         fFilterNode->setWeight(fAmount / 100); // 100-based
 *     }
 *
 *     const sk_sp<sksg::Color>               fColorNode0,
 *                                            fColorNode1;
 *     const sk_sp<sksg::GradientColorFilter> fFilterNode;
 *
 *     ColorValue  fMapBlackTo,
 *                 fMapWhiteTo;
 *     ScalarValue fAmount = 0;
 * }
 * ```
 */
public class TintAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color>               fColorNode0
   * ```
   */
  private val fColorNode0: SkSp<Color> = TODO("Initialize fColorNode0")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color>               fColorNode0,
   *                                            fColorNode1
   * ```
   */
  private val fColorNode1: SkSp<Color> = TODO("Initialize fColorNode1")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::GradientColorFilter> fFilterNode
   * ```
   */
  private val fFilterNode: SkSp<GradientColorFilter> = TODO("Initialize fFilterNode")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fMapBlackTo
   * ```
   */
  private var fMapBlackTo: ColorValue = TODO("Initialize fMapBlackTo")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fMapBlackTo,
   *                 fMapWhiteTo
   * ```
   */
  private var fMapWhiteTo: ColorValue = TODO("Initialize fMapWhiteTo")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fAmount = 0
   * ```
   */
  private var fAmount: ScalarValue = TODO("Initialize fAmount")

  /**
   * C++ original:
   * ```cpp
   * const auto& node() const { return fFilterNode; }
   * ```
   */
  public fun node(): Any {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         fColorNode0->setColor(fMapBlackTo);
   *         fColorNode1->setColor(fMapWhiteTo);
   *
   *         fFilterNode->setWeight(fAmount / 100); // 100-based
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
     * static sk_sp<TintAdapter> Make(const skjson::ArrayValue& jprops,
     *                                    sk_sp<sksg::RenderNode> layer,
     *                                    const AnimationBuilder& abuilder) {
     *         return sk_sp<TintAdapter>(new TintAdapter(jprops, std::move(layer), abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder,
    ): SkSp<TintAdapter> {
      TODO("Implement make")
    }
  }
}
