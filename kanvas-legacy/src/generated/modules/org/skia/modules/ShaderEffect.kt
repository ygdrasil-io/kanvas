package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class ShaderEffect final : public EffectNode {
 * public:
 *     ~ShaderEffect() override;
 *
 *     static sk_sp<ShaderEffect> Make(sk_sp<RenderNode> child, sk_sp<Shader> shader = nullptr);
 *
 *     void setShader(sk_sp<Shader>);
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     ShaderEffect(sk_sp<RenderNode> child, sk_sp<Shader> shader);
 *
 *     sk_sp<Shader> fShader;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class ShaderEffect public constructor(
  child: SkSp<RenderNode>,
  shader: SkSp<Shader>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ShaderEffect(sk_sp<RenderNode> child, sk_sp<Shader> shader)
   * ```
   */
  private var skSp: ShaderEffect = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Shader> fShader
   * ```
   */
  private var fShader: Int = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * void ShaderEffect::setShader(sk_sp<Shader> sh) {
   *     if (fShader) {
   *         this->unobserveInval(fShader);
   *     }
   *
   *     fShader = std::move(sh);
   *
   *     if (fShader) {
   *         this->observeInval(fShader);
   *     }
   * }
   * ```
   */
  public fun setShader(sh: SkSp<Shader>) {
    TODO("Implement setShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaderEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx)
   *             .modulateShader(fShader ? fShader->getShader() : nullptr, canvas->getTotalMatrix());
   *
   *     this->INHERITED::onRender(canvas, local_ctx);
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect ShaderEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     if (fShader) {
   *         fShader->revalidate(ic, ctm);
   *     }
   *
   *     return this->INHERITED::onRevalidate(ic, ctm);
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ShaderEffect> ShaderEffect::Make(sk_sp<RenderNode> child, sk_sp<Shader> shader) {
     *     return child ? sk_sp<ShaderEffect>(new ShaderEffect(std::move(child), std::move(shader)))
     *                  : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, shader: SkSp<Shader> = TODO()): Int {
      TODO("Implement make")
    }
  }
}
