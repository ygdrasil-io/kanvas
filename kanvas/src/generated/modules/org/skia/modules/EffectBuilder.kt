package org.skia.modules

import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkSp
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class EffectBuilder final : public SkNoncopyable {
 * public:
 *     EffectBuilder(const AnimationBuilder*, const SkSize&, CompositionBuilder*);
 *
 *     sk_sp<sksg::RenderNode> attachEffects(const skjson::ArrayValue&,
 *                                           sk_sp<sksg::RenderNode>) const;
 *
 *     sk_sp<sksg::RenderNode> attachStyles(const skjson::ArrayValue&,
 *                                          sk_sp<sksg::RenderNode>) const;
 *
 *     static const skjson::Value& GetPropValue(const skjson::ArrayValue& jprops, size_t prop_index);
 *
 *     struct LayerContent {
 *         sk_sp<sksg::RenderNode> fContent;
 *         SkSize                  fSize;
 *     };
 *     LayerContent getLayerContent(int layer_index) const;
 *
 * private:
 *     using EffectBuilderT = sk_sp<sksg::RenderNode>(EffectBuilder::*)(const skjson::ArrayValue&,
 *                                                                      sk_sp<sksg::RenderNode>) const;
 *
 *     sk_sp<sksg::RenderNode> attachBlackAndWhiteEffect     (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachBrightnessContrastEffect(const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachBulgeEffect            (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachCornerPinEffect         (const skjson::ArrayValue&,
 *                                                             sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachCCTonerEffect           (const skjson::ArrayValue&,
 *                                                             sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachDirectionalBlurEffect   (const skjson::ArrayValue&,
 *                                                             sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachDisplacementMapEffect   (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachDropShadowEffect        (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachFillEffect              (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachFractalNoiseEffect      (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachGaussianBlurEffect      (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachGradientEffect          (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachHueSaturationEffect     (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachInvertEffect            (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachEasyLevelsEffect        (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachLinearWipeEffect        (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachMotionTileEffect        (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachProLevelsEffect         (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachRadialWipeEffect        (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachSharpenEffect            (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachShiftChannelsEffect     (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachSkSLColorFilter         (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachSkSLShader              (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachSphereEffect            (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachThresholdEffect         (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachTintEffect              (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachTransformEffect         (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachTritoneEffect           (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachVenetianBlindsEffect    (const skjson::ArrayValue&,
 *                                                            sk_sp<sksg::RenderNode>) const;
 *
 *     sk_sp<sksg::RenderNode> attachDropShadowStyle(const skjson::ObjectValue&,
 *                                                   sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachInnerShadowStyle(const skjson::ObjectValue&,
 *                                                    sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachInnerGlowStyle(const skjson::ObjectValue&,
 *                                                  sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::RenderNode> attachOuterGlowStyle(const skjson::ObjectValue&,
 *                                                  sk_sp<sksg::RenderNode>) const;
 *
 *     EffectBuilderT findBuilder(const skjson::ObjectValue&) const;
 *
 *     const AnimationBuilder* fBuilder;
 *     CompositionBuilder*     fCompBuilder;
 *     const SkSize            fLayerSize;
 * }
 * ```
 */
public class EffectBuilder public constructor(
  param0: AnimationBuilder,
  param1: SkSize,
  param2: CompositionBuilder,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const AnimationBuilder* fBuilder
   * ```
   */
  private val fBuilder: AnimationBuilder? = TODO("Initialize fBuilder")

  /**
   * C++ original:
   * ```cpp
   * CompositionBuilder*     fCompBuilder
   * ```
   */
  private var fCompBuilder: CompositionBuilder? = TODO("Initialize fCompBuilder")

  /**
   * C++ original:
   * ```cpp
   * const SkSize            fLayerSize
   * ```
   */
  private val fLayerSize: Int = TODO("Initialize fLayerSize")

  /**
   * C++ original:
   * ```cpp
   * EffectBuilder(const AnimationBuilder*, const SkSize&, CompositionBuilder*)
   * ```
   */
  public constructor(
    abuilder: AnimationBuilder?,
    layerSize: SkSize,
    cbuilder: CompositionBuilder?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachEffects(const skjson::ArrayValue& jeffects,
   *                                                      sk_sp<sksg::RenderNode> layer) const {
   *     if (!layer) {
   *         return nullptr;
   *     }
   *
   *     for (const skjson::ObjectValue* jeffect : jeffects) {
   *         if (!jeffect) {
   *             continue;
   *         }
   *
   *         const auto builder = this->findBuilder(*jeffect);
   *         const skjson::ArrayValue* jprops = (*jeffect)["ef"];
   *         if (!builder || !jprops) {
   *             continue;
   *         }
   *
   *         const AnimationBuilder::AutoPropertyTracker apt(fBuilder, *jeffect, PropertyObserver::NodeType::EFFECT);
   *         layer = (this->*builder)(*jprops, std::move(layer));
   *
   *         if (!layer) {
   *             fBuilder->log(Logger::Level::kError, jeffect, "Invalid layer effect.");
   *             return nullptr;
   *         }
   *     }
   *
   *     return layer;
   * }
   * ```
   */
  public fun attachEffects(jeffects: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachEffects")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachStyles(const skjson::ArrayValue& jstyles,
   *                                                      sk_sp<sksg::RenderNode> layer) const {
   * #if !defined(SKOTTIE_DISABLE_STYLES)
   *     if (!layer) {
   *         return nullptr;
   *     }
   *
   *     using StyleBuilder =
   *         sk_sp<sksg::RenderNode> (EffectBuilder::*)(const skjson::ObjectValue&,
   *                                                    sk_sp<sksg::RenderNode>) const;
   *     static constexpr StyleBuilder gStyleBuilders[] = {
   *         nullptr,                                 // 'ty': 0 -> stroke
   *         &EffectBuilder::attachDropShadowStyle,   // 'ty': 1 -> drop shadow
   *         &EffectBuilder::attachInnerShadowStyle,  // 'ty': 2 -> inner shadow
   *         &EffectBuilder::attachOuterGlowStyle,    // 'ty': 3 -> outer glow
   *         &EffectBuilder::attachInnerGlowStyle,    // 'ty': 4 -> inner glow
   *     };
   *
   *     for (const skjson::ObjectValue* jstyle : jstyles) {
   *         if (!jstyle) {
   *             continue;
   *         }
   *
   *         const auto style_type =
   *                 ParseDefault<size_t>((*jstyle)["ty"], std::numeric_limits<size_t>::max());
   *         auto builder = style_type < std::size(gStyleBuilders) ? gStyleBuilders[style_type]
   *                                                               : nullptr;
   *
   *         if (!builder) {
   *             fBuilder->log(Logger::Level::kWarning, jstyle, "Unsupported layer style.");
   *             continue;
   *         }
   *
   *         layer = (this->*builder)(*jstyle, std::move(layer));
   *     }
   * #endif // !defined(SKOTTIE_DISABLE_STYLES)
   *
   *     return layer;
   * }
   * ```
   */
  public fun attachStyles(jstyles: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * EffectBuilder::LayerContent EffectBuilder::getLayerContent(int layer_index) const {
   *     if (LayerBuilder* lbuilder = fCompBuilder->layerBuilder(layer_index)) {
   *         return { lbuilder->getContentTree(*fBuilder, fCompBuilder), lbuilder->size() };
   *     }
   *
   *     return { nullptr, {0, 0} };
   * }
   * ```
   */
  public fun getLayerContent(layerIndex: Int): LayerContent {
    TODO("Implement getLayerContent")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachBlackAndWhiteEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<BlackAndWhiteAdapter>(jprops,
   *                                                                     *fBuilder,
   *                                                                     std::move(layer));
   * }
   * ```
   */
  private fun attachBlackAndWhiteEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachBlackAndWhiteEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachBrightnessContrastEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<BrightnessContrastAdapter>(jprops,
   *                                                                          *fBuilder,
   *                                                                          std::move(layer));
   * }
   * ```
   */
  private fun attachBrightnessContrastEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachBrightnessContrastEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachBulgeEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     auto shaderNode = sk_make_sp<BulgeNode>(std::move(layer), fLayerSize);
   *     return fBuilder->attachDiscardableAdapter<BulgeEffectAdapter>(jprops, *fBuilder,
   *                                                                   std::move(shaderNode));
   * }
   * ```
   */
  private fun attachBulgeEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachBulgeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachCornerPinEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     sk_sp<sksg::Matrix<SkMatrix>> matrix_node =
   *             fBuilder->attachDiscardableAdapter<CornerPinAdapter>(jprops, *fBuilder, fLayerSize);
   *
   *     return sksg::TransformEffect::Make(std::move(layer), std::move(matrix_node));
   * }
   * ```
   */
  private fun attachCornerPinEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachCornerPinEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachCCTonerEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     std::vector<sk_sp<sksg::Color>> colorNodes = {
   *         sksg::Color::Make(SK_ColorRED),
   *         sksg::Color::Make(SK_ColorRED),
   *         sksg::Color::Make(SK_ColorRED),
   *         sksg::Color::Make(SK_ColorRED),
   *         sksg::Color::Make(SK_ColorRED)
   *     };
   *     return fBuilder->attachDiscardableAdapter<CCTonerAdapter>(jprops,
   *                                                               std::move(layer),
   *                                                               *fBuilder,
   *                                                               std::move(colorNodes));
   * }
   * ```
   */
  private fun attachCCTonerEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachCCTonerEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachDirectionalBlurEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     auto imageFilterNode = fBuilder->attachDiscardableAdapter<DirectionalBlurAdapter>(jprops,
   *                                                                       *fBuilder);
   *     return sksg::ImageFilterEffect::Make(std::move(layer), std::move(imageFilterNode));
   * }
   * ```
   */
  private fun attachDirectionalBlurEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachDirectionalBlurEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachDisplacementMapEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     const LayerContent displ = DisplacementMapAdapter::GetDisplacementSource(jprops, this);
   *
   *     auto displ_node = DisplacementNode::Make(layer,
   *                                              fLayerSize,
   *                                              std::move(displ.fContent),
   *                                              displ.fSize);
   *
   *     if (!displ_node) {
   *         return layer;
   *     }
   *
   *     return fBuilder->attachDiscardableAdapter<DisplacementMapAdapter>(jprops,
   *                                                                       fBuilder,
   *                                                                       std::move(displ_node));
   * }
   * ```
   */
  private fun attachDisplacementMapEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachDisplacementMapEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachDropShadowEffect(const skjson::ArrayValue& jprops,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<DropShadowAdapter>(jprops,
   *                                                                  std::move(layer),
   *                                                                  *fBuilder);
   * }
   * ```
   */
  private fun attachDropShadowEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachDropShadowEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachFillEffect(const skjson::ArrayValue& jprops,
   *                                                         sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<FillAdapter>(jprops, std::move(layer), *fBuilder);
   * }
   * ```
   */
  private fun attachFillEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachFillEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachFractalNoiseEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     auto fractal_noise = sk_make_sp<FractalNoiseNode>(std::move(layer));
   *
   *     return fBuilder->attachDiscardableAdapter<FractalNoiseAdapter>(jprops, fBuilder,
   *                                                                    std::move(fractal_noise));
   * }
   * ```
   */
  private fun attachFractalNoiseEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachFractalNoiseEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachGaussianBlurEffect(
   *         const skjson::ArrayValue& jprops,
   *         sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<GaussianBlurEffectAdapter>(jprops,
   *                                                                          std::move(layer),
   *                                                                          fBuilder);
   * }
   * ```
   */
  private fun attachGaussianBlurEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachGaussianBlurEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachGradientEffect(const skjson::ArrayValue& jprops,
   *                                                             sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<GradientRampEffectAdapter>(jprops,
   *                                                                          std::move(layer),
   *                                                                          fBuilder);
   * }
   * ```
   */
  private fun attachGradientEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachGradientEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachHueSaturationEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<HueSaturationEffectAdapter>(jprops,
   *                                                                           std::move(layer),
   *                                                                           fBuilder);
   * }
   * ```
   */
  private fun attachHueSaturationEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachHueSaturationEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachInvertEffect(const skjson::ArrayValue& jprops,
   *                                                           sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<InvertEffectAdapter>(jprops,
   *                                                                    std::move(layer),
   *                                                                    fBuilder);
   * }
   * ```
   */
  private fun attachInvertEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachInvertEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachEasyLevelsEffect(const skjson::ArrayValue& jprops,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<EasyLevelsEffectAdapter>(jprops,
   *                                                                        std::move(layer),
   *                                                                        fBuilder);
   * }
   * ```
   */
  private fun attachEasyLevelsEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachEasyLevelsEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachLinearWipeEffect(const skjson::ArrayValue& jprops,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<LinearWipeAdapter>(jprops,
   *                                                                  std::move(layer),
   *                                                                  fLayerSize,
   *                                                                  fBuilder);
   * }
   * ```
   */
  private fun attachLinearWipeEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachLinearWipeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachMotionTileEffect(const skjson::ArrayValue& jprops,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<MotionTileAdapter>(jprops,
   *                                                                  std::move(layer),
   *                                                                  *fBuilder,
   *                                                                  fLayerSize);
   * }
   * ```
   */
  private fun attachMotionTileEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachMotionTileEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachProLevelsEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<ProLevelsEffectAdapter>(jprops,
   *                                                                       std::move(layer),
   *                                                                       fBuilder);
   * }
   * ```
   */
  private fun attachProLevelsEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachProLevelsEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachRadialWipeEffect(const skjson::ArrayValue& jprops,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<RadialWipeAdapter>(jprops,
   *                                                                  std::move(layer),
   *                                                                  *fBuilder);
   * }
   * ```
   */
  private fun attachRadialWipeEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachRadialWipeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachSharpenEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     auto imageFilterNode = fBuilder->attachDiscardableAdapter<SharpenAdapter>(jprops,
   *                                                                               *fBuilder);
   *     return sksg::ImageFilterEffect::Make(std::move(layer), std::move(imageFilterNode));
   * }
   * ```
   */
  private fun attachSharpenEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachSharpenEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachShiftChannelsEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<ShiftChannelsEffectAdapter>(jprops,
   *                                                                           std::move(layer),
   *                                                                           fBuilder);
   * }
   * ```
   */
  private fun attachShiftChannelsEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachShiftChannelsEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachSkSLColorFilter(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   * #if defined(SK_ENABLE_SKOTTIE_SKSLEFFECT)
   *     auto cfNode = sksg::ExternalColorFilter::Make(std::move(layer));
   *     return fBuilder->attachDiscardableAdapter<SkSLColorFilterAdapter>(jprops, *fBuilder,
   *                                                                       std::move(cfNode));
   * #else
   *     return layer;
   * #endif
   * }
   * ```
   */
  private fun attachSkSLColorFilter(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachSkSLColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachSkSLShader(const skjson::ArrayValue& jprops,
   *                                                         sk_sp<sksg::RenderNode> layer) const {
   * #if defined(SK_ENABLE_SKOTTIE_SKSLEFFECT)
   *     auto shaderNode = sk_make_sp<SkSLShaderNode>(std::move(layer), fLayerSize);
   *     return fBuilder->attachDiscardableAdapter<SkSLShaderAdapter>(jprops, *fBuilder,
   *                                                                  std::move(shaderNode));
   * #else
   *     return layer;
   * #endif
   * }
   * ```
   */
  private fun attachSkSLShader(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachSkSLShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachSphereEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     auto sphere = sk_make_sp<SphereNode>(std::move(layer), fLayerSize);
   *
   *     return fBuilder->attachDiscardableAdapter<SphereAdapter>(jprops, fBuilder, std::move(sphere));
   * }
   * ```
   */
  private fun attachSphereEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachSphereEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachThresholdEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<ThresholdAdapter>(jprops,
   *                                                                 std::move(layer),
   *                                                                 *fBuilder);
   * }
   * ```
   */
  private fun attachThresholdEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachThresholdEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachTintEffect(const skjson::ArrayValue& jprops,
   *                                                         sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<TintAdapter>(jprops, std::move(layer), *fBuilder);
   * }
   * ```
   */
  private fun attachTintEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachTintEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachTransformEffect(const skjson::ArrayValue& jprops,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     enum : size_t {
   *         kAnchorPoint_Index            =  0,
   *         kPosition_Index               =  1,
   *         kUniformScale_Index           =  2,
   *         kScaleHeight_Index            =  3,
   *         kScaleWidth_Index             =  4,
   *         kSkew_Index                   =  5,
   *         kSkewAxis_Index               =  6,
   *         kRotation_Index               =  7,
   *         kOpacity_Index                =  8,
   *         // kUseCompShutterAngle_Index =  9,
   *         // kShutterAngle_Index        = 10,
   *         // kSampling_Index            = 11,
   *     };
   *
   *     auto transform_adapter = TransformAdapter2D::Make(*fBuilder,
   *                                                       GetPropValue(jprops, kAnchorPoint_Index),
   *                                                       GetPropValue(jprops, kPosition_Index),
   *                                                       nullptr, // scale is handled externally
   *                                                       GetPropValue(jprops, kRotation_Index),
   *                                                       GetPropValue(jprops, kSkew_Index),
   *                                                       GetPropValue(jprops, kSkewAxis_Index));
   *     if (!transform_adapter) {
   *         return nullptr;
   *     }
   *
   *     auto transform_effect_node = sksg::TransformEffect::Make(std::move(layer),
   *                                                              transform_adapter->node());
   *     return fBuilder->attachDiscardableAdapter<TransformEffectAdapter>
   *             (*fBuilder,
   *              GetPropValue(jprops, kOpacity_Index),
   *              GetPropValue(jprops, kUniformScale_Index),
   *              GetPropValue(jprops, kScaleWidth_Index),
   *              GetPropValue(jprops, kScaleHeight_Index),
   *              std::move(transform_adapter),
   *              std::move(transform_effect_node)
   *              );
   * }
   * ```
   */
  private fun attachTransformEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachTransformEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachTritoneEffect(const skjson::ArrayValue& jprops,
   *                                                            sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<TritoneAdapter>(jprops, std::move(layer), *fBuilder);
   * }
   * ```
   */
  private fun attachTritoneEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachTritoneEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachVenetianBlindsEffect(
   *         const skjson::ArrayValue& jprops, sk_sp<sksg::RenderNode> layer) const {
   *     return fBuilder->attachDiscardableAdapter<VenetianBlindsAdapter>(jprops,
   *                                                                      std::move(layer),
   *                                                                      fLayerSize,
   *                                                                      fBuilder);
   * }
   * ```
   */
  private fun attachVenetianBlindsEffect(jprops: ArrayValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachVenetianBlindsEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachDropShadowStyle(const skjson::ObjectValue& jstyle,
   *                                                              sk_sp<sksg::RenderNode> layer) const {
   *     return make_shadow_effect(jstyle, *fBuilder, std::move(layer),
   *                               ShadowAdapter::Type::kDropShadow);
   * }
   * ```
   */
  private fun attachDropShadowStyle(jstyle: ObjectValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachDropShadowStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachInnerShadowStyle(const skjson::ObjectValue& jstyle,
   *                                                               sk_sp<sksg::RenderNode> layer) const {
   *     return make_shadow_effect(jstyle, *fBuilder, std::move(layer),
   *                               ShadowAdapter::Type::kInnerShadow);
   * }
   * ```
   */
  private fun attachInnerShadowStyle(jstyle: ObjectValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachInnerShadowStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachInnerGlowStyle(const skjson::ObjectValue& jstyle,
   *                                                             sk_sp<sksg::RenderNode> layer) const {
   *     return make_glow_effect(jstyle, *fBuilder, std::move(layer), GlowAdapter::Type::kInnerGlow);
   * }
   * ```
   */
  private fun attachInnerGlowStyle(jstyle: ObjectValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachInnerGlowStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> EffectBuilder::attachOuterGlowStyle(const skjson::ObjectValue& jstyle,
   *                                                             sk_sp<sksg::RenderNode> layer) const {
   *     return make_glow_effect(jstyle, *fBuilder, std::move(layer), GlowAdapter::Type::kOuterGlow);
   * }
   * ```
   */
  private fun attachOuterGlowStyle(jstyle: ObjectValue, layer: SkSp<RenderNode>): Int {
    TODO("Implement attachOuterGlowStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * EffectBuilder::EffectBuilderT EffectBuilder::findBuilder(const skjson::ObjectValue& jeffect) const {
   *     static constexpr struct BuilderInfo {
   *         const char*    fName;
   *         EffectBuilderT fBuilder;
   *     } gBuilderInfo[] = {
   *         // alphabetized for binary search lookup
   *         { "ADBE Black&White"            , &EffectBuilder::attachBlackAndWhiteEffect      },
   *         { "ADBE Brightness & Contrast 2", &EffectBuilder::attachBrightnessContrastEffect },
   *         { "ADBE Bulge"                  , &EffectBuilder::attachBulgeEffect              },
   *         { "ADBE Corner Pin"             , &EffectBuilder::attachCornerPinEffect          },
   *         { "ADBE Displacement Map"       , &EffectBuilder::attachDisplacementMapEffect    },
   *         { "ADBE Drop Shadow"            , &EffectBuilder::attachDropShadowEffect         },
   *         { "ADBE Easy Levels2"           , &EffectBuilder::attachEasyLevelsEffect         },
   *         { "ADBE Fill"                   , &EffectBuilder::attachFillEffect               },
   *         { "ADBE Fractal Noise"          , &EffectBuilder::attachFractalNoiseEffect       },
   *         { "ADBE Gaussian Blur 2"        , &EffectBuilder::attachGaussianBlurEffect       },
   *         { "ADBE Geometry2"              , &EffectBuilder::attachTransformEffect          },
   *         { "ADBE HUE SATURATION"         , &EffectBuilder::attachHueSaturationEffect      },
   *         { "ADBE Invert"                 , &EffectBuilder::attachInvertEffect             },
   *         { "ADBE Linear Wipe"            , &EffectBuilder::attachLinearWipeEffect         },
   *         { "ADBE Motion Blur"            , &EffectBuilder::attachDirectionalBlurEffect    },
   *         { "ADBE Pro Levels2"            , &EffectBuilder::attachProLevelsEffect          },
   *         { "ADBE Radial Wipe"            , &EffectBuilder::attachRadialWipeEffect         },
   *         { "ADBE Ramp"                   , &EffectBuilder::attachGradientEffect           },
   *         { "ADBE Sharpen"                , &EffectBuilder::attachSharpenEffect            },
   *         { "ADBE Shift Channels"         , &EffectBuilder::attachShiftChannelsEffect      },
   *         { "ADBE Threshold2"             , &EffectBuilder::attachThresholdEffect          },
   *         { "ADBE Tile"                   , &EffectBuilder::attachMotionTileEffect         },
   *         { "ADBE Tint"                   , &EffectBuilder::attachTintEffect               },
   *         { "ADBE Tritone"                , &EffectBuilder::attachTritoneEffect            },
   *         { "ADBE Venetian Blinds"        , &EffectBuilder::attachVenetianBlindsEffect     },
   *         { "CC Sphere"                   , &EffectBuilder::attachSphereEffect             },
   *         { "CC Toner"                    , &EffectBuilder::attachCCTonerEffect            },
   *         { "SkSL Color Filter"           , &EffectBuilder::attachSkSLColorFilter          },
   *         { "SkSL Shader"                 , &EffectBuilder::attachSkSLShader               },
   *     };
   *
   *     const skjson::StringValue* mn = jeffect["mn"];
   *     if (mn) {
   *         const BuilderInfo key { mn->begin(), nullptr };
   *         const auto* binfo = std::lower_bound(std::begin(gBuilderInfo),
   *                                              std::end  (gBuilderInfo),
   *                                              key,
   *                                              [](const BuilderInfo& a, const BuilderInfo& b) {
   *                                                  return strcmp(a.fName, b.fName) < 0;
   *                                              });
   *         if (binfo != std::end(gBuilderInfo) && !strcmp(binfo->fName, key.fName)) {
   *             return binfo->fBuilder;
   *         }
   *     }
   *
   *     // Some legacy clients rely solely on the 'ty' field and generate (non-BM) JSON
   *     // without a valid 'mn' string.  TODO: we should update them and remove this fallback.
   *     enum : int32_t {
   *         kTint_Effect         = 20,
   *         kFill_Effect         = 21,
   *         kTritone_Effect      = 23,
   *         kDropShadow_Effect   = 25,
   *         kRadialWipe_Effect   = 26,
   *         kGaussianBlur_Effect = 29,
   *     };
   *
   *     switch (ParseDefault<int>(jeffect["ty"], -1)) {
   *         case         kTint_Effect: return &EffectBuilder::attachTintEffect;
   *         case         kFill_Effect: return &EffectBuilder::attachFillEffect;
   *         case      kTritone_Effect: return &EffectBuilder::attachTritoneEffect;
   *         case   kDropShadow_Effect: return &EffectBuilder::attachDropShadowEffect;
   *         case   kRadialWipe_Effect: return &EffectBuilder::attachRadialWipeEffect;
   *         case kGaussianBlur_Effect: return &EffectBuilder::attachGaussianBlurEffect;
   *         default: break;
   *     }
   *
   *     fBuilder->log(Logger::Level::kWarning, &jeffect,
   *                   "Unsupported layer effect: %s", mn ? mn->begin() : "(unknown)");
   *
   *     return nullptr;
   * }
   * ```
   */
  private fun findBuilder(jeffect: ObjectValue): Int {
    TODO("Implement findBuilder")
  }

  public data class LayerContent public constructor(
    public var fContent: Int,
    public var fSize: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * const skjson::Value& EffectBuilder::GetPropValue(const skjson::ArrayValue& jprops,
     *                                                  size_t prop_index) {
     *     static skjson::NullValue kNull;
     *
     *     if (prop_index >= jprops.size()) {
     *         return kNull;
     *     }
     *
     *     const skjson::ObjectValue* jprop = jprops[prop_index];
     *
     *     return jprop ? (*jprop)["v"] : kNull;
     * }
     * ```
     */
    public fun getPropValue(jprops: ArrayValue, propIndex: ULong): Int {
      TODO("Implement getPropValue")
    }
  }
}
