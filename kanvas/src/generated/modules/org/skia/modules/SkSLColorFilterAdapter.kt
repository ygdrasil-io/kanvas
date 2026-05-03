package org.skia.modules

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkSLColorFilterAdapter final : public DiscardableAdapterBase<SkSLColorFilterAdapter,
 *                                                              sksg::ExternalColorFilter>
 *                                    , public SkSLEffectBase {
 * public:
 *     SkSLColorFilterAdapter(const skjson::ArrayValue& jprops,
 *                       const AnimationBuilder& abuilder,
 *                       sk_sp<sksg::ExternalColorFilter> node)
 *         : DiscardableAdapterBase<SkSLColorFilterAdapter, sksg::ExternalColorFilter>(std::move(node))
 *         , SkSLEffectBase(jprops, abuilder)
 *     {
 *         this->bindUniforms(jprops, abuilder, this);
 *     }
 *
 * private:
 *     void onSync() override {
 *         if (!fEffect) {
 *             return;
 *         }
 *         auto cf = fEffect->makeColorFilter(buildUniformData());
 *         this->node()->setColorFilter(std::move(cf));
 *     }
 * }
 * ```
 */
public class SkSLColorFilterAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  node: SkSp<ExternalColorFilter>,
) : DiscardableAdapterBase(TODO()),
    SkSLColorFilterAdapter,
    ExternalColorFilter,
    SkSLEffectBase {
  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         if (!fEffect) {
   *             return;
   *         }
   *         auto cf = fEffect->makeColorFilter(buildUniformData());
   *         this->node()->setColorFilter(std::move(cf));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
