package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class MaskShaderEffect final : public EffectNode {
 * public:
 *     static sk_sp<MaskShaderEffect> Make(sk_sp<RenderNode>, sk_sp<SkShader> = nullptr);
 *
 *     SG_ATTRIBUTE(Shader, sk_sp<SkShader>, fShader)
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *
 * private:
 *     MaskShaderEffect(sk_sp<RenderNode>, sk_sp<SkShader>);
 *
 *     sk_sp<SkShader> fShader;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class MaskShaderEffect public constructor(
  child: SkSp<RenderNode>,
  sh: SkSp<SkShader>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * MaskShaderEffect(sk_sp<RenderNode>, sk_sp<SkShader>)
   * ```
   */
  private var skSp: MaskShaderEffect = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: Int = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Shader, sk_sp<SkShader>, fShader)
   * ```
   */
  public fun sgATTRIBUTE(param0: Shader, param1: SkSp<SkShader>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void MaskShaderEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx)
   *             .modulateMaskShader(fShader, canvas->getTotalMatrix());
   *
   *     this->INHERITED::onRender(canvas, local_ctx);
   * }
   * ```
   */
  public fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<MaskShaderEffect> MaskShaderEffect::Make(sk_sp<RenderNode> child, sk_sp<SkShader> sh) {
     *     return child ? sk_sp<MaskShaderEffect>(new MaskShaderEffect(std::move(child), std::move(sh)))
     *                  : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, sh: SkSp<SkShader> = TODO()): Int {
      TODO("Implement make")
    }
  }
}
