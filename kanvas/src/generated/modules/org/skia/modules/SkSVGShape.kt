package org.skia.modules

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkPathFillType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGShape : public SkSVGTransformableNode {
 * public:
 *     void appendChild(sk_sp<SkSVGNode>) override;
 *
 * protected:
 *     explicit SkSVGShape(SkSVGTag);
 *
 *     void onRender(const SkSVGRenderContext&) const final;
 *
 *     virtual void onDraw(SkCanvas*, const SkSVGLengthContext&, const SkPaint&,
 *                         SkPathFillType) const = 0;
 *
 * private:
 *     using INHERITED = SkSVGTransformableNode;
 * }
 * ```
 */
public abstract class SkSVGShape public constructor(
  t: SkSVGTag,
) : SkSVGTransformableNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkSVGShape::appendChild(sk_sp<SkSVGNode>) {
   *     SkDEBUGF("cannot append child nodes to an SVG shape.\n");
   * }
   * ```
   */
  public override fun appendChild(param0: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGShape::onRender(const SkSVGRenderContext& ctx) const {
   *     const auto fillType = ctx.presentationContext().fInherited.fFillRule->asFillType();
   *
   *     const auto fillPaint = ctx.fillPaint(),
   *              strokePaint = ctx.strokePaint();
   *
   *     // TODO: this approach forces duplicate geometry resolution in onDraw(); refactor to avoid.
   *     if (fillPaint.has_value()) {
   *         this->onDraw(ctx.canvas(), ctx.lengthContext(), *fillPaint, fillType);
   *     }
   *
   *     if (strokePaint.has_value()) {
   *         this->onDraw(ctx.canvas(), ctx.lengthContext(), *strokePaint, fillType);
   *     }
   * }
   * ```
   */
  protected fun onRender(ctx: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onDraw(SkCanvas*, const SkSVGLengthContext&, const SkPaint&,
   *                         SkPathFillType) const = 0
   * ```
   */
  protected abstract fun onDraw(
    param0: SkCanvas?,
    param1: SkSVGLengthContext,
    param2: SkPaint,
    param3: SkPathFillType,
  )
}
