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
 * class ColorFilter : public EffectNode {
 * protected:
 *     explicit ColorFilter(sk_sp<RenderNode>);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const final;
 *     const RenderNode* onNodeAt(const SkPoint&)     const final;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) final;
 *
 *     virtual sk_sp<SkColorFilter> onRevalidateFilter() = 0;
 *
 * private:
 *     sk_sp<SkColorFilter> fColorFilter;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public abstract class ColorFilter public constructor(
  child: SkSp<RenderNode>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * explicit ColorFilter(sk_sp<RenderNode>)
   * ```
   */
  protected var skSp: ColorFilter = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fColorFilter
   * ```
   */
  private var fColorFilter: Int = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * void ColorFilter::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx).modulateColorFilter(fColorFilter);
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
   * const RenderNode* ColorFilter::onNodeAt(const SkPoint& p) const {
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
   * SkRect ColorFilter::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     fColorFilter = this->onRevalidateFilter();
   *
   *     return this->INHERITED::onRevalidate(ic, ctm);
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkColorFilter> onRevalidateFilter() = 0
   * ```
   */
  protected abstract fun onRevalidateFilter(): Int
}

public typealias ModeColorFilterINHERITED = ColorFilter

public typealias GradientColorFilterINHERITED = ColorFilter
