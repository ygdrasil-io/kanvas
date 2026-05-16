package org.skia.modules

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkSLShaderAdapter final : public DiscardableAdapterBase<SkSLShaderAdapter,
 *                                                               SkSLShaderNode>,
 *                                 public SkSLEffectBase {
 * public:
 *     SkSLShaderAdapter(const skjson::ArrayValue& jprops,
 *                       const AnimationBuilder& abuilder,
 *                       sk_sp<SkSLShaderNode> node)
 *         : DiscardableAdapterBase<SkSLShaderAdapter, SkSLShaderNode>(std::move(node))
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
 *         sk_sp<SkShader> shader =
 *                 fEffect->makeShader(buildUniformData(), buildChildrenData(this->node()));
 *         this->node()->setShader(std::move(shader));
 *     }
 * }
 * ```
 */
public class SkSLShaderAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  node: SkSp<SkSLShaderNode>,
) : DiscardableAdapterBase(TODO()),
    SkSLShaderAdapter,
    SkSLShaderNode,
    SkSLEffectBase {
  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         if (!fEffect) {
   *             return;
   *         }
   *         sk_sp<SkShader> shader =
   *                 fEffect->makeShader(buildUniformData(), buildChildrenData(this->node()));
   *         this->node()->setShader(std::move(shader));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
