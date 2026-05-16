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
 * class Draw : public RenderNode {
 * public:
 *     static sk_sp<Draw> Make(sk_sp<GeometryNode> geo, sk_sp<PaintNode> paint) {
 *         return (geo && paint) ? sk_sp<Draw>(new Draw(std::move(geo), std::move(paint))) : nullptr;
 *     }
 *
 * protected:
 *     Draw(sk_sp<GeometryNode>, sk_sp<PaintNode> paint);
 *     ~Draw() override;
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     sk_sp<GeometryNode> fGeometry;
 *     sk_sp<PaintNode>    fPaint;
 *
 *     using INHERITED = RenderNode;
 * }
 * ```
 */
public open class Draw public constructor(
  geometry: SkSp<GeometryNode>,
  paint: SkSp<PaintNode>,
) : RenderNode() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<GeometryNode> fGeometry
   * ```
   */
  private var fGeometry: Int = TODO("Initialize fGeometry")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PaintNode>    fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void Draw::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     auto paint = fPaint->makePaint();
   *     if (ctx) {
   *         ctx->modulatePaint(canvas->getTotalMatrix(), &paint);
   *     }
   *
   *     const auto skipDraw = paint.nothingToDraw() ||
   *             (paint.getStyle() == SkPaint::kStroke_Style && paint.getStrokeWidth() <= 0);
   *
   *     if (!skipDraw) {
   *         fGeometry->draw(canvas, paint);
   *     }
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* Draw::onNodeAt(const SkPoint& p) const {
   *     const auto paint = fPaint->makePaint();
   *
   *     if (!paint.getAlpha()) {
   *         return nullptr;
   *     }
   *
   *     if (paint.getStyle() == SkPaint::Style::kFill_Style && fGeometry->contains(p)) {
   *         return this;
   *     }
   *
   *     SkPathBuilder stroke_path;
   *     if (!skpathutils::FillPathWithPaint(fGeometry->asPath(), paint, &stroke_path)) {
   *         return nullptr;
   *     }
   *
   *     // todo: can we shared code (via SkPathRaw) for the impl of contains() in builder?
   *     return stroke_path.detach().contains(p.x(), p.y()) ? this : nullptr;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Draw::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     auto bounds = fGeometry->revalidate(ic, ctm);
   *     fPaint->revalidate(ic, ctm);
   *
   *     const auto paint = fPaint->makePaint();
   *     SkASSERT(paint.canComputeFastBounds());
   *
   *     return paint.computeFastBounds(bounds, &bounds);
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
     * static sk_sp<Draw> Make(sk_sp<GeometryNode> geo, sk_sp<PaintNode> paint) {
     *         return (geo && paint) ? sk_sp<Draw>(new Draw(std::move(geo), std::move(paint))) : nullptr;
     *     }
     * ```
     */
    public fun make(geo: SkSp<GeometryNode>, paint: SkSp<PaintNode>): Int {
      TODO("Implement make")
    }
  }
}
