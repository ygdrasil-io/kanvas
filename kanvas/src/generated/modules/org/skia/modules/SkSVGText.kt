package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkSVGText final : public SkSVGTextContainer {
 * public:
 *     static sk_sp<SkSVGText> Make() { return sk_sp<SkSVGText>(new SkSVGText()); }
 *
 * private:
 *     SkSVGText() : INHERITED(SkSVGTag::kText) {}
 *
 *     void onRender(const SkSVGRenderContext&) const override;
 *
 *     SkRect onTransformableObjectBoundingBox(const SkSVGRenderContext&) const override;
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *
 *     using INHERITED = SkSVGTextContainer;
 * }
 * ```
 */
public class SkSVGText public constructor() : SkSVGTextContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkSVGText::onRender(const SkSVGRenderContext& ctx) const {
   *     const SkSVGTextContext::ShapedTextCallback render_text = [](const SkSVGRenderContext& ctx,
   *                                                                 const sk_sp<SkTextBlob>& blob,
   *                                                                 const SkPaint* fill,
   *                                                                 const SkPaint* stroke) {
   *         if (fill) {
   *             ctx.canvas()->drawTextBlob(blob, 0, 0, *fill);
   *         }
   *         if (stroke) {
   *             ctx.canvas()->drawTextBlob(blob, 0, 0, *stroke);
   *         }
   *     };
   *
   *     // Root <text> nodes establish a text layout context.
   *     SkSVGTextContext tctx(ctx, render_text);
   *
   *     this->onShapeText(ctx, &tctx, this->getXmlSpace());
   * }
   * ```
   */
  public override fun onRender(ctx: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGText::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     SkRect bounds = SkRect::MakeEmpty();
   *
   *     const SkSVGTextContext::ShapedTextCallback compute_bounds =
   *         [&bounds](const SkSVGRenderContext& ctx, const sk_sp<SkTextBlob>& blob, const SkPaint*,
   *                   const SkPaint*) {
   *             if (!blob) {
   *                 return;
   *             }
   *
   *             AutoSTArray<64, SkRect> glyphBounds;
   *
   *             for (SkTextBlobRunIterator it(blob.get()); !it.done(); it.next()) {
   *                 const auto nglyphs = it.glyphCount();
   *                 glyphBounds.reset(SkToInt(nglyphs));
   *                 it.font().getBounds({it.glyphs(), nglyphs}, {glyphBounds.get(), nglyphs}, nullptr);
   *
   *                 SkASSERT(it.positioning() == SkTextBlobRunIterator::kRSXform_Positioning);
   *                 SkMatrix m;
   *                 for (uint32_t i = 0; i < it.glyphCount(); ++i) {
   *                     m.setRSXform(it.xforms()[i]);
   *                     bounds.join(m.mapRect(glyphBounds[i]));
   *                 }
   *             }
   *         };
   *
   *     {
   *         SkSVGTextContext tctx(ctx, compute_bounds);
   *         this->onShapeText(ctx, &tctx, this->getXmlSpace());
   *     }
   *
   *     return bounds;
   * }
   * ```
   */
  public override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGText::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPathBuilder builder;
   *
   *     const SkSVGTextContext::ShapedTextCallback as_path =
   *         [&builder](const SkSVGRenderContext& ctx, const sk_sp<SkTextBlob>& blob, const SkPaint*,
   *                    const SkPaint*) {
   *             if (!blob) {
   *                 return;
   *             }
   *
   *             for (SkTextBlobRunIterator it(blob.get()); !it.done(); it.next()) {
   *                 struct GetPathsCtx {
   *                     SkPathBuilder&   builder;
   *                     const SkRSXform* xform;
   *                 } get_paths_ctx {builder, it.xforms()};
   *
   *                 it.font().getPaths({it.glyphs(), it.glyphCount()}, [](const SkPath* path,
   *                                                                       const SkMatrix& matrix,
   *                                                                       void* raw_ctx) {
   *                     auto* get_paths_ctx = static_cast<GetPathsCtx*>(raw_ctx);
   *                     const auto& glyph_rsx = *get_paths_ctx->xform++;
   *
   *                     if (!path) {
   *                         return;
   *                     }
   *
   *                     SkMatrix glyph_matrix;
   *                     glyph_matrix.setRSXform(glyph_rsx);
   *                     glyph_matrix.preConcat(matrix);
   *
   *                     get_paths_ctx->builder.addPath(path->makeTransform(glyph_matrix));
   *                 }, &get_paths_ctx);
   *             }
   *         };
   *
   *     {
   *         SkSVGTextContext tctx(ctx, as_path);
   *         this->onShapeText(ctx, &tctx, this->getXmlSpace());
   *     }
   *
   *     return this->mapToParent(builder.detach());
   * }
   * ```
   */
  public override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGText> Make() { return sk_sp<SkSVGText>(new SkSVGText()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
