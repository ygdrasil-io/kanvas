package org.skia.modules

import kotlin.Any
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class FillAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<FillAdapter> Make(const skjson::ArrayValue& jprops,
 *                                    sk_sp<sksg::RenderNode> layer,
 *                                    const AnimationBuilder& abuilder) {
 *         return sk_sp<FillAdapter>(new FillAdapter(jprops, std::move(layer), abuilder));
 *     }
 *
 *     const auto& node() const { return fFilterNode; }
 *
 * private:
 *     FillAdapter(const skjson::ArrayValue& jprops,
 *                 sk_sp<sksg::RenderNode> layer,
 *                 const AnimationBuilder& abuilder)
 *         : fColorNode(sksg::Color::Make(SK_ColorBLACK))
 *         , fFilterNode(sksg::ModeColorFilter::Make(std::move(layer),
 *                                                   fColorNode,
 *                                                   SkBlendMode::kSrcIn)) {
 *         enum : size_t {
 *          // kFillMask_Index = 0,
 *          // kAllMasks_Index = 1,
 *                kColor_Index = 2,
 *          //   kInvert_Index = 3,
 *          // kHFeather_Index = 4,
 *          // kVFeather_Index = 5,
 *              kOpacity_Index = 6,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(  kColor_Index, fColor  )
 *             .bind(kOpacity_Index, fOpacity);
 *         abuilder.dispatchColorProperty(fColorNode);
 *     }
 *
 *     void onSync() override {
 *         auto c = static_cast<SkColor4f>(fColor);
 *         c.fA = SkTPin(fOpacity, 0.0f, 1.0f);
 *
 *         fColorNode->setColor(c.toSkColor());
 *     }
 *
 *     const sk_sp<sksg::Color>           fColorNode;
 *     const sk_sp<sksg::ModeColorFilter> fFilterNode;
 *
 *     ColorValue  fColor;
 *     ScalarValue fOpacity = 1;
 * }
 * ```
 */
public class FillAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Color>           fColorNode
   * ```
   */
  private val fColorNode: SkSp<Color> = TODO("Initialize fColorNode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::ModeColorFilter> fFilterNode
   * ```
   */
  private val fFilterNode: SkSp<ModeColorFilter> = TODO("Initialize fFilterNode")

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
   * ScalarValue fOpacity = 1
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

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
   *         auto c = static_cast<SkColor4f>(fColor);
   *         c.fA = SkTPin(fOpacity, 0.0f, 1.0f);
   *
   *         fColorNode->setColor(c.toSkColor());
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
     * static sk_sp<FillAdapter> Make(const skjson::ArrayValue& jprops,
     *                                    sk_sp<sksg::RenderNode> layer,
     *                                    const AnimationBuilder& abuilder) {
     *         return sk_sp<FillAdapter>(new FillAdapter(jprops, std::move(layer), abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder,
    ): SkSp<FillAdapter> {
      TODO("Implement make")
    }
  }
}
