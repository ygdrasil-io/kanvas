package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class GradientRampEffectAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<GradientRampEffectAdapter> Make(const skjson::ArrayValue& jprops,
 *                                                  sk_sp<sksg::RenderNode> layer,
 *                                                  const AnimationBuilder* abuilder) {
 *         return sk_sp<GradientRampEffectAdapter>(new GradientRampEffectAdapter(jprops,
 *                                                                               std::move(layer),
 *                                                                               abuilder));
 *     }
 *
 *     sk_sp<sksg::RenderNode> node() const { return fShaderEffect; }
 *
 * private:
 *     GradientRampEffectAdapter(const skjson::ArrayValue& jprops,
 *                               sk_sp<sksg::RenderNode> layer,
 *                               const AnimationBuilder* abuilder)
 *         : fShaderEffect(sksg::ShaderEffect::Make(std::move(layer))) {
 *         enum : size_t {
 *              kStartPoint_Index = 0,
 *              kStartColor_Index = 1,
 *                kEndPoint_Index = 2,
 *                kEndColor_Index = 3,
 *               kRampShape_Index = 4,
 *             kRampScatter_Index = 5,
 *              kBlendRatio_Index = 6,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *                 .bind( kStartPoint_Index, fStartPoint)
 *                 .bind( kStartColor_Index, fStartColor)
 *                 .bind(   kEndPoint_Index, fEndPoint  )
 *                 .bind(   kEndColor_Index, fEndColor  )
 *                 .bind(  kRampShape_Index, fShape     )
 *                 .bind(kRampScatter_Index, fScatter   )
 *                 .bind( kBlendRatio_Index, fBlend     );
 *     }
 *
 *     enum class InstanceType {
 *         kNone,
 *         kLinear,
 *         kRadial,
 *     };
 *
 *     void onSync() override {
 *         // This adapter manages a SG fragment with the following structure:
 *         //
 *         // - ShaderEffect [fRoot]
 *         //     \  GradientShader [fGradient]
 *         //     \  child/wrapped fragment
 *         //
 *         // The gradient shader is updated based on the (animatable) instance type (linear/radial).
 *
 *         auto update_gradient = [this] (InstanceType new_type) {
 *             if (new_type != fInstanceType) {
 *                 fGradient = new_type == InstanceType::kLinear
 *                         ? sk_sp<sksg::Gradient>(sksg::LinearGradient::Make())
 *                         : sk_sp<sksg::Gradient>(sksg::RadialGradient::Make());
 *
 *                 fShaderEffect->setShader(fGradient);
 *                 fInstanceType = new_type;
 *             }
 *
 *             fGradient->setColorStops({{0, fStartColor},
 *                                       {1,   fEndColor}});
 *         };
 *
 *         static constexpr int kLinearShapeValue = 1;
 *         const auto instance_type = (SkScalarRoundToInt(fShape) == kLinearShapeValue)
 *                 ? InstanceType::kLinear
 *                 : InstanceType::kRadial;
 *
 *         // Sync the gradient shader instance if needed.
 *         update_gradient(instance_type);
 *
 *         // Sync instance-dependent gradient params.
 *         const auto start_point = SkPoint{fStartPoint.x, fStartPoint.y},
 *                      end_point = SkPoint{  fEndPoint.x,   fEndPoint.y};
 *         if (instance_type == InstanceType::kLinear) {
 *             auto* lg = static_cast<sksg::LinearGradient*>(fGradient.get());
 *             lg->setStartPoint(start_point);
 *             lg->setEndPoint(end_point);
 *         } else {
 *             SkASSERT(instance_type == InstanceType::kRadial);
 *
 *             auto* rg = static_cast<sksg::RadialGradient*>(fGradient.get());
 *             rg->setStartCenter(start_point);
 *             rg->setEndCenter(start_point);
 *             rg->setEndRadius(SkPoint::Distance(start_point, end_point));
 *         }
 *
 *         // TODO: blend, scatter
 *     }
 *
 *     const sk_sp<sksg::ShaderEffect> fShaderEffect;
 *     sk_sp<sksg::Gradient>           fGradient;
 *
 *     InstanceType              fInstanceType = InstanceType::kNone;
 *
 *     ColorValue  fStartColor,
 *                 fEndColor;
 *     Vec2Value   fStartPoint = {0,0},
 *                 fEndPoint   = {0,0};
 *     ScalarValue fBlend   = 0,
 *                 fScatter = 0,
 *                 fShape   = 0; // 1 -> linear, 7 -> radial (?!)
 * }
 * ```
 */
public class GradientRampEffectAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder?,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::ShaderEffect> fShaderEffect
   * ```
   */
  private val fShaderEffect: SkSp<ShaderEffect> = TODO("Initialize fShaderEffect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Gradient>           fGradient
   * ```
   */
  private var fGradient: SkSp<Gradient> = TODO("Initialize fGradient")

  /**
   * C++ original:
   * ```cpp
   * InstanceType              fInstanceType = InstanceType::kNone
   * ```
   */
  private var fInstanceType: InstanceType = TODO("Initialize fInstanceType")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fStartColor
   * ```
   */
  private var fStartColor: ColorValue = TODO("Initialize fStartColor")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fStartColor,
   *                 fEndColor
   * ```
   */
  private var fEndColor: ColorValue = TODO("Initialize fEndColor")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fStartPoint = {0,0}
   * ```
   */
  private var fStartPoint: Vec2Value = TODO("Initialize fStartPoint")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fStartPoint = {0,0},
   *                 fEndPoint   = {0,0}
   * ```
   */
  private var fEndPoint: Vec2Value = TODO("Initialize fEndPoint")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlend   = 0
   * ```
   */
  private var fBlend: ScalarValue = TODO("Initialize fBlend")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlend   = 0,
   *                 fScatter = 0
   * ```
   */
  private var fScatter: ScalarValue = TODO("Initialize fScatter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlend   = 0,
   *                 fScatter = 0,
   *                 fShape   = 0
   * ```
   */
  private var fShape: ScalarValue = TODO("Initialize fShape")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> node() const { return fShaderEffect; }
   * ```
   */
  public fun node(): SkSp<RenderNode> {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         // This adapter manages a SG fragment with the following structure:
   *         //
   *         // - ShaderEffect [fRoot]
   *         //     \  GradientShader [fGradient]
   *         //     \  child/wrapped fragment
   *         //
   *         // The gradient shader is updated based on the (animatable) instance type (linear/radial).
   *
   *         auto update_gradient = [this] (InstanceType new_type) {
   *             if (new_type != fInstanceType) {
   *                 fGradient = new_type == InstanceType::kLinear
   *                         ? sk_sp<sksg::Gradient>(sksg::LinearGradient::Make())
   *                         : sk_sp<sksg::Gradient>(sksg::RadialGradient::Make());
   *
   *                 fShaderEffect->setShader(fGradient);
   *                 fInstanceType = new_type;
   *             }
   *
   *             fGradient->setColorStops({{0, fStartColor},
   *                                       {1,   fEndColor}});
   *         };
   *
   *         static constexpr int kLinearShapeValue = 1;
   *         const auto instance_type = (SkScalarRoundToInt(fShape) == kLinearShapeValue)
   *                 ? InstanceType::kLinear
   *                 : InstanceType::kRadial;
   *
   *         // Sync the gradient shader instance if needed.
   *         update_gradient(instance_type);
   *
   *         // Sync instance-dependent gradient params.
   *         const auto start_point = SkPoint{fStartPoint.x, fStartPoint.y},
   *                      end_point = SkPoint{  fEndPoint.x,   fEndPoint.y};
   *         if (instance_type == InstanceType::kLinear) {
   *             auto* lg = static_cast<sksg::LinearGradient*>(fGradient.get());
   *             lg->setStartPoint(start_point);
   *             lg->setEndPoint(end_point);
   *         } else {
   *             SkASSERT(instance_type == InstanceType::kRadial);
   *
   *             auto* rg = static_cast<sksg::RadialGradient*>(fGradient.get());
   *             rg->setStartCenter(start_point);
   *             rg->setEndCenter(start_point);
   *             rg->setEndRadius(SkPoint::Distance(start_point, end_point));
   *         }
   *
   *         // TODO: blend, scatter
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public enum class InstanceType {
    kNone,
    kLinear,
    kRadial,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GradientRampEffectAdapter> Make(const skjson::ArrayValue& jprops,
     *                                                  sk_sp<sksg::RenderNode> layer,
     *                                                  const AnimationBuilder* abuilder) {
     *         return sk_sp<GradientRampEffectAdapter>(new GradientRampEffectAdapter(jprops,
     *                                                                               std::move(layer),
     *                                                                               abuilder));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      layer: SkSp<RenderNode>,
      abuilder: AnimationBuilder?,
    ): SkSp<GradientRampEffectAdapter> {
      TODO("Implement make")
    }
  }
}
