package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkCanvasVirtualEnforcer
import org.skia.core.SkClipOp
import org.skia.core.SkDrawShadowRec
import org.skia.core.SkFilterMode
import org.skia.core.SkPicture
import org.skia.core.SkTextBlob
import org.skia.core.SkVertices
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.ClipEdgeStyle
import undefined.ImageSetEntry
import undefined.Lattice
import undefined.PointMode
import undefined.QuadAAFlags
import undefined.SaveLayerRec
import undefined.SkColor4f
import undefined.SrcRectConstraint

/**
 * C++ original:
 * ```cpp
 * class SK_API SkNWayCanvas : public SkCanvasVirtualEnforcer<SkNoDrawCanvas> {
 * public:
 *     SkNWayCanvas(int width, int height);
 *     ~SkNWayCanvas() override;
 *
 *     virtual void addCanvas(SkCanvas*);
 *     virtual void removeCanvas(SkCanvas*);
 *     virtual void removeAll();
 *
 * protected:
 *     SkTDArray<SkCanvas*> fList;
 *
 *     void willSave() override;
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec&) override;
 *     bool onDoSaveBehind(const SkRect*) override;
 *     void willRestore() override;
 *
 *     void didConcat44(const SkM44&) override;
 *     void didSetM44(const SkM44&) override;
 *     void didScale(SkScalar, SkScalar) override;
 *     void didTranslate(SkScalar, SkScalar) override;
 *
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *     void onDrawGlyphRunList(const sktext::GlyphRunList&, const SkPaint&) override;
 *     void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
 *                         const SkPaint& paint) override;
 *     void onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) override;
 *     void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                      const SkPoint texCoords[4], SkBlendMode, const SkPaint& paint) override;
 *
 *     void onDrawPaint(const SkPaint&) override;
 *     void onDrawBehind(const SkPaint&) override;
 *     void onDrawPoints(PointMode, size_t count, const SkPoint pts[], const SkPaint&) override;
 *     void onDrawRect(const SkRect&, const SkPaint&) override;
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override;
 *     void onDrawOval(const SkRect&, const SkPaint&) override;
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override;
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override;
 *     void onDrawPath(const SkPath&, const SkPaint&) override;
 *
 *     void onDrawImage2(const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&,
 *                       const SkPaint*) override;
 *     void onDrawImageRect2(const SkImage*, const SkRect&, const SkRect&, const SkSamplingOptions&,
 *                           const SkPaint*, SrcRectConstraint) override;
 *     void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect&, SkFilterMode,
 *                              const SkPaint*) override;
 *     void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect[], const SkColor[], int,
 *                   SkBlendMode, const SkSamplingOptions&, const SkRect*, const SkPaint*) override;
 *
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override;
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override;
 *
 *     void onClipRect(const SkRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipRRect(const SkRRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipPath(const SkPath&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipShader(sk_sp<SkShader>, SkClipOp) override;
 *     void onClipRegion(const SkRegion&, SkClipOp) override;
 *     void onResetClip() override;
 *
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *     void onDrawAnnotation(const SkRect&, const char[], SkData*) override;
 *
 *     void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&,
 *                           SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[], int count, const SkPoint[], const SkMatrix[],
 *                                const SkSamplingOptions&,const SkPaint*, SrcRectConstraint) override;
 *     class Iter;
 * private:
 *     using INHERITED = SkCanvasVirtualEnforcer<SkNoDrawCanvas>;
 * }
 * ```
 */
public open class SkNWayCanvas public constructor(
  width: Int,
  height: Int,
) : SkCanvasVirtualEnforcer(),
    SkNoDrawCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkCanvas*> fList
   * ```
   */
  protected var fList: Int = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::addCanvas(SkCanvas* canvas) {
   *     if (!fList.empty()) {
   *         // We are using the nway canvas as a wrapper for the originally added canvas, and the device
   *         // on the nway may contradict calls for the device on this canvas. So, to add a second
   *         // canvas, the devices on the first canvas, and the nway base device must be different.
   *         SkASSERT(fList[0]->rootDevice() != this->rootDevice());
   *     }
   *     if (canvas) {
   *         *fList.append() = canvas;
   *     }
   * }
   * ```
   */
  public open fun addCanvas(canvas: SkCanvas?) {
    TODO("Implement addCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::removeCanvas(SkCanvas* canvas) {
   *     auto found = std::find(fList.begin(), fList.end(), canvas);
   *     if (found != fList.end()) {
   *         fList.removeShuffle(std::distance(fList.begin(), found));
   *     }
   * }
   * ```
   */
  public open fun removeCanvas(canvas: SkCanvas?) {
    TODO("Implement removeCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::removeAll() {
   *     fList.reset();
   * }
   * ```
   */
  public open fun removeAll() {
    TODO("Implement removeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::willSave() {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->save();
   *     }
   *
   *     this->INHERITED::willSave();
   * }
   * ```
   */
  protected override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy SkNWayCanvas::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->saveLayer(rec);
   *     }
   *
   *     this->INHERITED::getSaveLayerStrategy(rec);
   *     // No need for a layer.
   *     return kNoLayer_SaveLayerStrategy;
   * }
   * ```
   */
  protected override fun getSaveLayerStrategy(rec: SaveLayerRec): Int {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkNWayCanvas::onDoSaveBehind(const SkRect* bounds) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         SkCanvasPriv::SaveBehind(iter.get(), bounds);
   *     }
   *     this->INHERITED::onDoSaveBehind(bounds);
   *     return false;
   * }
   * ```
   */
  protected override fun onDoSaveBehind(bounds: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::willRestore() {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->restore();
   *     }
   *     this->INHERITED::willRestore();
   * }
   * ```
   */
  protected override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::didConcat44(const SkM44& m) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->concat(m);
   *     }
   * }
   * ```
   */
  protected override fun didConcat44(m: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::didSetM44(const SkM44& matrix) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->setMatrix(matrix);
   *     }
   * }
   * ```
   */
  protected override fun didSetM44(matrix: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::didScale(SkScalar x, SkScalar y) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->scale(x, y);
   *     }
   * }
   * ```
   */
  protected override fun didScale(x: SkScalar, y: SkScalar) {
    TODO("Implement didScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::didTranslate(SkScalar x, SkScalar y) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->translate(x, y);
   *     }
   * }
   * ```
   */
  protected override fun didTranslate(x: SkScalar, y: SkScalar) {
    TODO("Implement didTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawDRRect(outer, inner, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawGlyphRunList(const sktext::GlyphRunList& list,
   *                                       const SkPaint &paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->onDrawGlyphRunList(list, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawGlyphRunList(list: GlyphRunList, paint: SkPaint) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
   *                                   const SkPaint &paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawTextBlob(blob, x, y, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawTextBlob(
    blob: SkTextBlob?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawSlug(slug, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement onDrawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                                const SkPoint texCoords[4], SkBlendMode bmode,
   *                                const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawPatch(cubics, colors, texCoords, bmode, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    bmode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawPaint(const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawPaint(paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawBehind(const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         SkCanvasPriv::DrawBehind(iter.get(), paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawPoints(PointMode mode, size_t count, const SkPoint pts[],
   *                                 const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawPoints(mode, {pts, count}, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPoints(
    mode: PointMode,
    count: ULong,
    pts: Array<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawRect(rect, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawRegion(region, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawOval(const SkRect& rect, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawOval(rect, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawOval(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawArc(const SkRect& rect, SkScalar startAngle, SkScalar sweepAngle,
   *                              bool useCenter, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawArc(rect, startAngle, sweepAngle, useCenter, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawArc(
    rect: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawRRect(rrect, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawPath(path, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawImage2(const SkImage* image, SkScalar left, SkScalar top,
   *                                 const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawImage(image, left, top, sampling, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawImage2(
    image: SkImage?,
    left: SkScalar,
    top: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawImageRect2(const SkImage* image, const SkRect& src, const SkRect& dst,
   *                                     const SkSamplingOptions& sampling, const SkPaint* paint,
   *                                     SrcRectConstraint constraint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawImageRect(image, src, dst, sampling, paint, constraint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawImageRect2(
    image: SkImage?,
    src: SkRect,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawImageRect2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawImageLattice2(const SkImage* image, const Lattice& lattice,
   *                                        const SkRect& dst, SkFilterMode filter,
   *                                        const SkPaint* paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawImageLattice(image, lattice, dst, filter, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawImageLattice2(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImageLattice2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawAtlas2(const SkImage* image, const SkRSXform xform[], const SkRect tex[],
   *                                 const SkColor colors[], int count, SkBlendMode bmode,
   *                                 const SkSamplingOptions& sampling, const SkRect* cull,
   *                                 const SkPaint* paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawAtlas(image,
   *                         {xform, (size_t)count},
   *                         {tex, (size_t)count},
   *                         {colors, colors ? (size_t)count : 0},
   *                         bmode, sampling, cull, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawAtlas2(
    image: SkImage?,
    xform: Array<SkRSXform>,
    tex: Array<SkRect>,
    colors: Array<SkColor>,
    count: Int,
    bmode: SkBlendMode,
    sampling: SkSamplingOptions,
    cull: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawVerticesObject(const SkVertices* vertices,
   *                                         SkBlendMode bmode, const SkPaint& paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawVertices(vertices, bmode, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawVerticesObject(
    vertices: SkVertices?,
    bmode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->private_draw_shadow_rec(path, rec);
   *     }
   * }
   * ```
   */
  protected override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->clipRect(rect, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     }
   *     this->INHERITED::onClipRect(rect, op, edgeStyle);
   * }
   * ```
   */
  protected override fun onClipRect(
    rect: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->clipRRect(rrect, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     }
   *     this->INHERITED::onClipRRect(rrect, op, edgeStyle);
   * }
   * ```
   */
  protected override fun onClipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->clipPath(path, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     }
   *     this->INHERITED::onClipPath(path, op, edgeStyle);
   * }
   * ```
   */
  protected override fun onClipPath(
    path: SkPath,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onClipShader(sk_sp<SkShader> sh, SkClipOp op) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->clipShader(sh, op);
   *     }
   *     this->INHERITED::onClipShader(std::move(sh), op);
   * }
   * ```
   */
  protected override fun onClipShader(sh: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onClipRegion(const SkRegion& deviceRgn, SkClipOp op) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->clipRegion(deviceRgn, op);
   *     }
   *     this->INHERITED::onClipRegion(deviceRgn, op);
   * }
   * ```
   */
  protected override fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onResetClip() {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         SkCanvasPriv::ResetClip(iter.get());
   *     }
   *     this->INHERITED::onResetClip();
   * }
   * ```
   */
  protected override fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
   *                                  const SkPaint* paint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawPicture(picture, matrix, paint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawDrawable(drawable, matrix);
   *     }
   * }
   * ```
   */
  protected override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* data) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->drawAnnotation(rect, key, data);
   *     }
   * }
   * ```
   */
  protected override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `data`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
   *                                     QuadAAFlags aa, const SkColor4f& color, SkBlendMode mode) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->experimental_DrawEdgeAAQuad(rect, clip, aa, color, mode);
   *     }
   * }
   * ```
   */
  protected override fun onDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aa: QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement onDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNWayCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[], int count,
   *                                          const SkPoint dstClips[], const SkMatrix preViewMatrices[],
   *                                          const SkSamplingOptions& sampling, const SkPaint* paint,
   *                                          SrcRectConstraint constraint) {
   *     Iter iter(fList);
   *     while (iter.next()) {
   *         iter->experimental_DrawEdgeAAImageSet(
   *                 set, count, dstClips, preViewMatrices, sampling, paint, constraint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawEdgeAAImageSet2(
    `set`: Array<ImageSetEntry>,
    count: Int,
    dstClips: Array<SkPoint>,
    preViewMatrices: Array<SkMatrix>,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawEdgeAAImageSet2")
  }
}

public typealias SkCanvasStackINHERITED = SkNWayCanvas
