package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class TransformEffectAdapter final : public DiscardableAdapterBase<TransformEffectAdapter,
 *                                                                    sksg::OpacityEffect> {
 * public:
 *     TransformEffectAdapter(const AnimationBuilder& abuilder,
 *                            const skjson::ObjectValue* jopacity,
 *                            const skjson::ObjectValue* jscale_uniform,
 *                            const skjson::ObjectValue* jscale_width,
 *                            const skjson::ObjectValue* jscale_height,
 *                            sk_sp<TransformAdapter2D> tadapter,
 *                            sk_sp<sksg::RenderNode> child)
 *         : INHERITED(sksg::OpacityEffect::Make(std::move(child)))
 *         , fTransformAdapter(std::move(tadapter)) {
 *         this->bind(abuilder, jopacity      , fOpacity     );
 *         this->bind(abuilder, jscale_uniform, fUniformScale);
 *         this->bind(abuilder, jscale_width  , fScaleWidth  );
 *         this->bind(abuilder, jscale_height , fScaleHeight );
 *
 *         this->attachDiscardableAdapter(fTransformAdapter);
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setOpacity(fOpacity * 0.01f);
 *
 *         // In uniform mode, the scale is based solely in ScaleHeight.
 *         const auto scale = SkVector::Make(SkScalarRoundToInt(fUniformScale) ? fScaleHeight
 *                                                                             : fScaleWidth,
 *                                           fScaleHeight);
 *
 *         // NB: this triggers an transform adapter -> SG sync.
 *         fTransformAdapter->setScale(scale);
 *     }
 *
 *     const sk_sp<TransformAdapter2D> fTransformAdapter;
 *
 *     ScalarValue fOpacity      = 100,
 *                 fUniformScale =   0, // bool
 *                 fScaleWidth   = 100,
 *                 fScaleHeight  = 100;
 *
 *     using INHERITED = DiscardableAdapterBase<TransformEffectAdapter, sksg::OpacityEffect>;
 * }
 * ```
 */
public class TransformEffectAdapter public constructor(
  abuilder: AnimationBuilder,
  jopacity: ObjectValue?,
  jscaleUniform: ObjectValue?,
  jscaleWidth: ObjectValue?,
  jscaleHeight: ObjectValue?,
  tadapter: SkSp<TransformAdapter2D>,
  child: SkSp<RenderNode>,
) : DiscardableAdapterBase(TODO()),
    TransformEffectAdapter,
    OpacityEffect {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<TransformAdapter2D> fTransformAdapter
   * ```
   */
  private val fTransformAdapter: SkSp<TransformAdapter2D> = TODO("Initialize fTransformAdapter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity      = 100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity      = 100,
   *                 fUniformScale =   0
   * ```
   */
  private var fUniformScale: ScalarValue = TODO("Initialize fUniformScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity      = 100,
   *                 fUniformScale =   0, // bool
   *                 fScaleWidth   = 100
   * ```
   */
  private var fScaleWidth: ScalarValue = TODO("Initialize fScaleWidth")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity      = 100,
   *                 fUniformScale =   0, // bool
   *                 fScaleWidth   = 100,
   *                 fScaleHeight  = 100
   * ```
   */
  private var fScaleHeight: ScalarValue = TODO("Initialize fScaleHeight")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setOpacity(fOpacity * 0.01f);
   *
   *         // In uniform mode, the scale is based solely in ScaleHeight.
   *         const auto scale = SkVector::Make(SkScalarRoundToInt(fUniformScale) ? fScaleHeight
   *                                                                             : fScaleWidth,
   *                                           fScaleHeight);
   *
   *         // NB: this triggers an transform adapter -> SG sync.
   *         fTransformAdapter->setScale(scale);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
