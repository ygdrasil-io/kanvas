package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlender
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import undefined.Blender
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class BlenderEffect final : public EffectNode {
 * public:
 *     ~BlenderEffect() override;
 *
 *     static sk_sp<BlenderEffect> Make(sk_sp<RenderNode> child, sk_sp<SkBlender> = nullptr);
 *
 *     SG_ATTRIBUTE(Blender, sk_sp<SkBlender>, fBlender)
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 * private:
 *     BlenderEffect(sk_sp<RenderNode>, sk_sp<SkBlender>);
 *
 *     sk_sp<SkBlender> fBlender;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class BlenderEffect public constructor(
  child: SkSp<RenderNode>,
  blender: SkSp<SkBlender>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * BlenderEffect(sk_sp<RenderNode>, sk_sp<SkBlender>)
   * ```
   */
  private var skSp: BlenderEffect = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBlender> fBlender
   * ```
   */
  private var fBlender: Int = TODO("Initialize fBlender")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Blender, sk_sp<SkBlender>, fBlender)
   * ```
   */
  public fun sgATTRIBUTE(param0: Blender, param1: SkSp<SkBlender>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* BlenderEffect::onNodeAt(const SkPoint& p) const {
   *     // TODO: we likely need to do something more sophisticated than delegate to descendants here.
   *     return this->INHERITED::onNodeAt(p);
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): RenderNode {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * void BlenderEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx).modulateBlender(fBlender);
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
     * sk_sp<BlenderEffect> BlenderEffect::Make(sk_sp<RenderNode> child, sk_sp<SkBlender> blender) {
     *     return child ? sk_sp<BlenderEffect>(new BlenderEffect(std::move(child), std::move(blender)))
     *                  : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, blender: SkSp<SkBlender> = TODO()): Int {
      TODO("Implement make")
    }
  }
}
