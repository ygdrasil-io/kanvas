package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class TransformEffect final : public EffectNode {
 * public:
 *     static sk_sp<TransformEffect> Make(sk_sp<RenderNode> child, sk_sp<Transform> transform) {
 *         return child && transform
 *             ? sk_sp<TransformEffect>(new TransformEffect(std::move(child), std::move(transform)))
 *             : nullptr;
 *     }
 *
 *     static sk_sp<TransformEffect> Make(sk_sp<RenderNode> child, const SkMatrix& m) {
 *         return Make(std::move(child), Matrix<SkMatrix>::Make(m));
 *     }
 *
 *     ~TransformEffect() override;
 *
 *     const sk_sp<Transform>& getTransform() const { return fTransform; }
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     TransformEffect(sk_sp<RenderNode>, sk_sp<Transform>);
 *
 *     const sk_sp<Transform> fTransform;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class TransformEffect public constructor(
  child: SkSp<RenderNode>,
  transform: SkSp<Transform>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Transform> fTransform
   * ```
   */
  private val fTransform: Int = TODO("Initialize fTransform")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Transform>& getTransform() const { return fTransform; }
   * ```
   */
  public fun getTransform(): Int {
    TODO("Implement getTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     SkAutoCanvasRestore acr(canvas, true);
   *     canvas->concat(TransformPriv::As<SkM44>(fTransform));
   *
   *     this->INHERITED::onRender(canvas, ctx);
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* TransformEffect::onNodeAt(const SkPoint& p) const {
   *     const auto p4 = TransformPriv::As<SkM44>(fTransform).map(p.fX, p.fY, 0, 0);
   *
   *     return this->INHERITED::onNodeAt({p4.x, p4.y});
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect TransformEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     // We don't care about matrix reval results.
   *     fTransform->revalidate(ic, ctm);
   *
   *     // TODO: need to update all the reval plumbing for m44.
   *     const auto m = TransformPriv::As<SkMatrix>(fTransform);
   *     auto bounds = this->INHERITED::onRevalidate(ic, SkMatrix::Concat(ctm, m));
   *     m.mapRect(&bounds);
   *
   *     return bounds;
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
     * static sk_sp<TransformEffect> Make(sk_sp<RenderNode> child, sk_sp<Transform> transform) {
     *         return child && transform
     *             ? sk_sp<TransformEffect>(new TransformEffect(std::move(child), std::move(transform)))
     *             : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, transform: SkSp<Transform>): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<TransformEffect> Make(sk_sp<RenderNode> child, const SkMatrix& m) {
     *         return Make(std::move(child), Matrix<SkMatrix>::Make(m));
     *     }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, m: SkMatrix): Int {
      TODO("Implement make")
    }
  }
}
