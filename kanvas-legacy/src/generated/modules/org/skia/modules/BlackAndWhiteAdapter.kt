package org.skia.modules

import kotlin.Array
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class BlackAndWhiteAdapter final : public DiscardableAdapterBase<BlackAndWhiteAdapter,
 *                                                                  sksg::ExternalColorFilter> {
 * public:
 *     BlackAndWhiteAdapter(const skjson::ArrayValue& jprops,
 *                          const AnimationBuilder& abuilder,
 *                          sk_sp<sksg::RenderNode> layer)
 *         : INHERITED(sksg::ExternalColorFilter::Make(std::move(layer)))
 *         , fEffect(make_effect())
 *     {
 *         SkASSERT(fEffect);
 *
 *         enum : size_t {
 *                 kReds_Index = 0,
 *              kYellows_Index = 1,
 *               kGreens_Index = 2,
 *                kCyans_Index = 3,
 *                kBlues_Index = 4,
 *             kMagentas_Index = 5,
 *             // TODO
 *             //    kTint_Index = 6,
 *             // kTintColorIndex = 7,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(    kReds_Index, fCoeffs[0])
 *             .bind( kYellows_Index, fCoeffs[1])
 *             .bind(  kGreens_Index, fCoeffs[2])
 *             .bind(   kCyans_Index, fCoeffs[3])
 *             .bind(   kBlues_Index, fCoeffs[4])
 *             .bind(kMagentas_Index, fCoeffs[5]);
 *     }
 *
 * private:
 *     void onSync() override {
 *         struct {
 *             float normalized_coeffs[6];
 *         } coeffs = {
 *             (fCoeffs[0] ) / 100,
 *             (fCoeffs[1] ) / 100,
 *             (fCoeffs[2] ) / 100,
 *             (fCoeffs[3] ) / 100,
 *             (fCoeffs[4] ) / 100,
 *             (fCoeffs[5] ) / 100,
 *         };
 *
 *         this->node()->setColorFilter(
 *                 fEffect->makeColorFilter(SkData::MakeWithCopy(&coeffs, sizeof(coeffs))));
 *     }
 *
 *     const sk_sp<SkRuntimeEffect> fEffect;
 *
 *     ScalarValue                  fCoeffs[6];
 *
 *     using INHERITED = DiscardableAdapterBase<BlackAndWhiteAdapter, sksg::ExternalColorFilter>;
 * }
 * ```
 */
public class BlackAndWhiteAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  layer: SkSp<RenderNode>,
) : DiscardableAdapterBase(TODO()),
    BlackAndWhiteAdapter,
    ExternalColorFilter {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkRuntimeEffect> fEffect
   * ```
   */
  private val fEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue                  fCoeffs[6]
   * ```
   */
  private var fCoeffs: Array<ScalarValue> = TODO("Initialize fCoeffs")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         struct {
   *             float normalized_coeffs[6];
   *         } coeffs = {
   *             (fCoeffs[0] ) / 100,
   *             (fCoeffs[1] ) / 100,
   *             (fCoeffs[2] ) / 100,
   *             (fCoeffs[3] ) / 100,
   *             (fCoeffs[4] ) / 100,
   *             (fCoeffs[5] ) / 100,
   *         };
   *
   *         this->node()->setColorFilter(
   *                 fEffect->makeColorFilter(SkData::MakeWithCopy(&coeffs, sizeof(coeffs))));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
