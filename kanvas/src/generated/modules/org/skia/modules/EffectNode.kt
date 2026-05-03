package org.skia.modules

import kotlin.Int
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class EffectNode : public RenderNode {
 * protected:
 *     explicit EffectNode(sk_sp<RenderNode>, uint32_t inval_traits = 0);
 *     ~EffectNode() override;
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 *     const sk_sp<RenderNode>& getChild() const { return fChild; }
 *
 * private:
 *     sk_sp<RenderNode> fChild;
 *
 *     using INHERITED = RenderNode;
 * }
 * ```
 */
public open class EffectNode public constructor(
  child: SkSp<RenderNode>,
  invalTraits: UInt,
) : RenderNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<RenderNode> fChild
   * ```
   */
  private var fChild: Int = TODO("Initialize fChild")

  /**
   * C++ original:
   * ```cpp
   * void EffectNode::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     fChild->render(canvas, ctx);
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* EffectNode::onNodeAt(const SkPoint& p) const {
   *     return fChild->nodeAt(p);
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect EffectNode::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     return fChild->revalidate(ic, ctm);
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<RenderNode>& getChild() const { return fChild; }
   * ```
   */
  protected fun getChild(): Int {
    TODO("Implement getChild")
  }
}
