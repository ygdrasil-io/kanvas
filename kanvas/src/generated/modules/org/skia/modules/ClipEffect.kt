package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class ClipEffect final : public EffectNode {
 * public:
 *     static sk_sp<ClipEffect> Make(sk_sp<RenderNode> child, sk_sp<GeometryNode> clip,
 *                                   bool aa = false, bool force_clip = false) {
 *         return (child && clip)
 *             ? sk_sp<ClipEffect>(new ClipEffect(std::move(child), std::move(clip), aa, force_clip))
 *             : nullptr;
 *     }
 *
 *     ~ClipEffect() override;
 *
 * protected:
 *     ClipEffect(sk_sp<RenderNode>, sk_sp<GeometryNode>, bool aa, bool force_clip);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     const sk_sp<GeometryNode> fClipNode;
 *     const bool                fAntiAlias,
 *                               fForceClip;
 *
 *     bool                      fNoop = false;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class ClipEffect public constructor(
  child: SkSp<RenderNode>,
  clip: SkSp<GeometryNode>,
  aa: Boolean,
  forceClip: Boolean,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<GeometryNode> fClipNode
   * ```
   */
  private val fClipNode: Int = TODO("Initialize fClipNode")

  /**
   * C++ original:
   * ```cpp
   * const bool                fAntiAlias
   * ```
   */
  private val fAntiAlias: Boolean = TODO("Initialize fAntiAlias")

  /**
   * C++ original:
   * ```cpp
   * const bool                fAntiAlias,
   *                               fForceClip
   * ```
   */
  private val fForceClip: Boolean = TODO("Initialize fForceClip")

  /**
   * C++ original:
   * ```cpp
   * bool                      fNoop = false
   * ```
   */
  private var fNoop: Boolean = TODO("Initialize fNoop")

  /**
   * C++ original:
   * ```cpp
   * void ClipEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     SkAutoCanvasRestore acr(canvas, !fNoop);
   *     if (!fNoop) {
   *         fClipNode->clip(canvas, fAntiAlias);
   *     }
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
   * const RenderNode* ClipEffect::onNodeAt(const SkPoint& p) const {
   *     return fClipNode->contains(p) ? this->INHERITED::onNodeAt(p) : nullptr;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect ClipEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     const auto clipBounds = fClipNode->revalidate(ic, ctm);
   *     auto childBounds = this->INHERITED::onRevalidate(ic, ctm);
   *
   *     // When the child node is fully contained within the clip, it is usually safe to elide.
   *     // An exception is clip-dependent sizing for saveLayer buffers, where the clip is always
   *     // significant.  For those cases, we provide a mechanism to disable elision.
   *     fNoop = !fForceClip && fClipNode->asPath().conservativelyContainsRect(childBounds);
   *
   *     return childBounds.intersect(clipBounds) ? childBounds : SkRect::MakeEmpty();
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
     * static sk_sp<ClipEffect> Make(sk_sp<RenderNode> child, sk_sp<GeometryNode> clip,
     *                                   bool aa = false, bool force_clip = false) {
     *         return (child && clip)
     *             ? sk_sp<ClipEffect>(new ClipEffect(std::move(child), std::move(clip), aa, force_clip))
     *             : nullptr;
     *     }
     * ```
     */
    public fun make(
      child: SkSp<RenderNode>,
      clip: SkSp<GeometryNode>,
      aa: Boolean = TODO(),
      forceClip: Boolean = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
