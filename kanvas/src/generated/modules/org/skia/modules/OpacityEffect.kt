package org.skia.modules

import kotlin.Float
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.Opacity
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class OpacityEffect final : public EffectNode {
 * public:
 *     static sk_sp<OpacityEffect> Make(sk_sp<RenderNode> child, float opacity = 1) {
 *         return child ? sk_sp<OpacityEffect>(new OpacityEffect(std::move(child), opacity)) : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(Opacity, float, fOpacity)
 *
 * protected:
 *     OpacityEffect(sk_sp<RenderNode>, float);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     float fOpacity;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class OpacityEffect public constructor(
  child: SkSp<RenderNode>,
  opacity: Float,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * float fOpacity
   * ```
   */
  private var fOpacity: Float = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Opacity, float, fOpacity)
   * ```
   */
  public fun sgATTRIBUTE(param0: Opacity, param1: Float): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void OpacityEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     // opacity <= 0 disables rendering
   *     if (fOpacity <= 0)
   *         return;
   *
   *     // opacity >= 1 has no effect
   *     if (fOpacity >= 1) {
   *         this->INHERITED::onRender(canvas, ctx);
   *         return;
   *     }
   *
   *     const auto local_context = ScopedRenderContext(canvas, ctx).modulateOpacity(fOpacity);
   *
   *     this->INHERITED::onRender(canvas, local_context);
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* OpacityEffect::onNodeAt(const SkPoint& p) const {
   *     return (fOpacity > 0) ? this->INHERITED::onNodeAt(p) : nullptr;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect OpacityEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     // opacity <= 0 disables rendering AND revalidation for the sub-DAG
   *     return fOpacity > 0 ? this->INHERITED::onRevalidate(ic, ctm) : SkRect::MakeEmpty();
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
     * static sk_sp<OpacityEffect> Make(sk_sp<RenderNode> child, float opacity = 1) {
     *         return child ? sk_sp<OpacityEffect>(new OpacityEffect(std::move(child), opacity)) : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, opacity: Float = TODO()): Int {
      TODO("Implement make")
    }
  }
}
