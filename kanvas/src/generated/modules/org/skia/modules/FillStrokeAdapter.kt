package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class FillStrokeAdapter final : public DiscardableAdapterBase<FillStrokeAdapter, sksg::PaintNode> {
 * public:
 *     enum class Type { kFill, kStroke };
 *
 *     FillStrokeAdapter(const skjson::ObjectValue& jpaint,
 *                       const AnimationBuilder& abuilder,
 *                       sk_sp<sksg::PaintNode> paint_node,
 *                       sk_sp<AnimatablePropertyContainer> gradient_adapter,
 *                       Type type)
 *         : INHERITED(std::move(paint_node))
 *         , fShaderType(gradient_adapter ? ShaderType::kGradient : ShaderType::kColor) {
 *
 *         this->attachDiscardableAdapter(std::move(gradient_adapter));
 *
 *         this->bind(abuilder, jpaint["o"], fOpacity);
 *
 *         this->node()->setAntiAlias(true);
 *
 *         if (type == Type::kStroke) {
 *             this->bind(abuilder, jpaint["w"], fStrokeWidth);
 *
 *             this->node()->setStyle(SkPaint::kStroke_Style);
 *             this->node()->setStrokeMiter(ParseDefault<SkScalar>(jpaint["ml"], 4.0f));
 *
 *             static constexpr SkPaint::Join gJoins[] = {
 *                 SkPaint::kMiter_Join,
 *                 SkPaint::kRound_Join,
 *                 SkPaint::kBevel_Join,
 *             };
 *             this->node()->setStrokeJoin(
 *                         gJoins[std::min<size_t>(ParseDefault<size_t>(jpaint["lj"], 1) - 1,
 *                                               std::size(gJoins) - 1)]);
 *
 *             static constexpr SkPaint::Cap gCaps[] = {
 *                 SkPaint::kButt_Cap,
 *                 SkPaint::kRound_Cap,
 *                 SkPaint::kSquare_Cap,
 *             };
 *             this->node()->setStrokeCap(
 *                         gCaps[std::min<size_t>(ParseDefault<size_t>(jpaint["lc"], 1) - 1,
 *                                              std::size(gCaps) - 1)]);
 *         }
 *
 *         if (fShaderType == ShaderType::kColor) {
 *             this->bind(abuilder, jpaint["c"], fColor);
 *         }
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setOpacity(fOpacity * 0.01f);
 *         this->node()->setStrokeWidth(fStrokeWidth);
 *
 *         if (fShaderType == ShaderType::kColor) {
 *             auto* color_node = static_cast<sksg::Color*>(this->node().get());
 *             color_node->setColor(fColor);
 *         }
 *     }
 *
 *     enum class ShaderType { kColor, kGradient };
 *
 *     const ShaderType fShaderType;
 *
 *     ColorValue       fColor;
 *     ScalarValue      fOpacity     = 100,
 *                      fStrokeWidth = 1;
 *
 *     using INHERITED = DiscardableAdapterBase<FillStrokeAdapter, sksg::PaintNode>;
 * }
 * ```
 */
public class FillStrokeAdapter public constructor(
  jpaint: ObjectValue,
  abuilder: AnimationBuilder,
  paintNode: SkSp<PaintNode>,
  gradientAdapter: SkSp<AnimatablePropertyContainer>,
  type: Type,
) : DiscardableAdapterBase(TODO()),
    FillStrokeAdapter,
    PaintNode {
  /**
   * C++ original:
   * ```cpp
   * const ShaderType fShaderType
   * ```
   */
  private val fShaderType: ShaderType = TODO("Initialize fShaderType")

  /**
   * C++ original:
   * ```cpp
   * ColorValue       fColor
   * ```
   */
  private var fColor: ColorValue = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue      fOpacity     = 100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue      fOpacity     = 100,
   *                      fStrokeWidth = 1
   * ```
   */
  private var fStrokeWidth: ScalarValue = TODO("Initialize fStrokeWidth")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setOpacity(fOpacity * 0.01f);
   *         this->node()->setStrokeWidth(fStrokeWidth);
   *
   *         if (fShaderType == ShaderType::kColor) {
   *             auto* color_node = static_cast<sksg::Color*>(this->node().get());
   *             color_node->setColor(fColor);
   *         }
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public enum class Type {
    kFill,
    kStroke,
  }

  public enum class ShaderType {
    kColor,
    kGradient,
  }
}
