package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.utils.SkNWayCanvas
import undefined.ImageSetEntry
import undefined.Lattice
import undefined.PointMode
import undefined.SkColor4f
import undefined.SrcRectConstraint

/**
 * C++ original:
 * ```cpp
 * class SK_API SkOverdrawCanvas : public SkCanvasVirtualEnforcer<SkNWayCanvas> {
 * public:
 *     /* Does not take ownership of canvas */
 *     explicit SkOverdrawCanvas(SkCanvas*);
 *
 *     void onDrawTextBlob(const SkTextBlob*, SkScalar, SkScalar, const SkPaint&) override;
 *     void onDrawGlyphRunList(
 *             const sktext::GlyphRunList& glyphRunList, const SkPaint& paint) override;
 *     void onDrawPatch(const SkPoint[12], const SkColor[4], const SkPoint[4], SkBlendMode,
 *                      const SkPaint&) override;
 *     void onDrawPaint(const SkPaint&) override;
 *     void onDrawBehind(const SkPaint& paint) override;
 *     void onDrawRect(const SkRect&, const SkPaint&) override;
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override;
 *     void onDrawOval(const SkRect&, const SkPaint&) override;
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override;
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override;
 *     void onDrawPoints(PointMode, size_t, const SkPoint[], const SkPaint&) override;
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override;
 *     void onDrawPath(const SkPath&, const SkPaint&) override;
 *
 *     void onDrawImage2(const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&,
 *                       const SkPaint*) override;
 *     void onDrawImageRect2(const SkImage*, const SkRect&, const SkRect&, const SkSamplingOptions&,
 *                           const SkPaint*, SrcRectConstraint) override;
 *     void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect&, SkFilterMode,
 *                              const SkPaint*) override;
 *     void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect[], const SkColor[], int,
 *                      SkBlendMode, const SkSamplingOptions&, const SkRect*, const SkPaint*) override;
 *
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *
 *     void onDrawAnnotation(const SkRect&, const char key[], SkData* value) override;
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override;
 *
 *     void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], SkCanvas::QuadAAFlags, const SkColor4f&,
 *                           SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[], int count, const SkPoint[], const SkMatrix[],
 *                                const SkSamplingOptions&,const SkPaint*, SrcRectConstraint) override;
 *
 * private:
 *     inline SkPaint overdrawPaint(const SkPaint& paint);
 *
 *     SkPaint   fPaint;
 *
 *     using INHERITED = SkCanvasVirtualEnforcer<SkNWayCanvas>;
 * }
 * ```
 */
public open class SkOverdrawCanvas public constructor(
  param0: SkCanvas,
) : SkCanvasVirtualEnforcer(),
    SkNWayCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkPaint   fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * explicit SkOverdrawCanvas(SkCanvas*)
   * ```
   */
  public constructor(canvas: SkCanvas?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawTextBlob(
   *         const SkTextBlob* blob, SkScalar x, SkScalar y, const SkPaint& paint) {
   *     sktext::GlyphRunBuilder b;
   *     auto glyphRunList = b.blobToGlyphRunList(*blob, {x, y});
   *     this->onDrawGlyphRunList(glyphRunList, paint);
   * }
   * ```
   */
  public override fun onDrawTextBlob(
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
   * void SkOverdrawCanvas::onDrawGlyphRunList(const sktext::GlyphRunList& glyphRunList,
   *                                           const SkPaint& paint) {
   *     SkSurfaceProps props;
   *     this->getProps(&props);
   *     TextDevice device{this, props};
   *
   *     device.drawGlyphRunList(this, glyphRunList, paint);
   * }
   * ```
   */
  public override fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: SkPaint) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                                    const SkPoint texCoords[4], SkBlendMode blendMode,
   *                                    const SkPaint&) {
   *     fList[0]->onDrawPatch(cubics, colors, texCoords, blendMode, fPaint);
   * }
   * ```
   */
  public override fun onDrawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    blendMode: SkBlendMode,
    param4: SkPaint,
  ) {
    TODO("Implement onDrawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawPaint(const SkPaint& paint) {
   *     if (0 == paint.getColor() && !paint.getColorFilter() && !paint.getShader()) {
   *         // This is a clear, ignore it.
   *     } else {
   *         fList[0]->onDrawPaint(this->overdrawPaint(paint));
   *     }
   * }
   * ```
   */
  public override fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawBehind(const SkPaint& paint) {
   *     fList[0]->onDrawBehind(this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     fList[0]->onDrawRect(rect, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     fList[0]->onDrawRegion(region, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
   *     fList[0]->onDrawOval(oval, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawArc(const SkRect& arc, SkScalar startAngle, SkScalar sweepAngle,
   *                                  bool useCenter, const SkPaint& paint) {
   *     fList[0]->onDrawArc(arc, startAngle, sweepAngle, useCenter, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawArc(
    arc: SkRect,
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
   * void SkOverdrawCanvas::onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
   *                                     const SkPaint& paint) {
   *     fList[0]->onDrawDRRect(outer, inner, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawRRect(const SkRRect& rect, const SkPaint& paint) {
   *     fList[0]->onDrawRRect(rect, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawRRect(rect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawPoints(PointMode mode, size_t count, const SkPoint points[],
   *                                     const SkPaint& paint) {
   *     fList[0]->onDrawPoints(mode, count, points, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawPoints(
    mode: PointMode,
    count: ULong,
    points: Array<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawVerticesObject(const SkVertices* vertices,
   *                                             SkBlendMode blendMode, const SkPaint& paint) {
   *     fList[0]->onDrawVerticesObject(vertices, blendMode, this->overdrawPaint(paint));
   * }
   * ```
   */
  public override fun onDrawVerticesObject(
    vertices: SkVertices?,
    blendMode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     fList[0]->onDrawPath(path, fPaint);
   * }
   * ```
   */
  public override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawImage2(const SkImage* image, SkScalar x, SkScalar y,
   *                                     const SkSamplingOptions&, const SkPaint*) {
   *     fList[0]->onDrawRect(SkRect::MakeXYWH(x, y, image->width(), image->height()), fPaint);
   * }
   * ```
   */
  public override fun onDrawImage2(
    image: SkImage?,
    x: SkScalar,
    y: SkScalar,
    param3: SkSamplingOptions,
    param4: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawImageRect2(const SkImage* image, const SkRect& src, const SkRect& dst,
   *                                         const SkSamplingOptions&, const SkPaint*, SrcRectConstraint) {
   *     fList[0]->onDrawRect(dst, fPaint);
   * }
   * ```
   */
  public override fun onDrawImageRect2(
    image: SkImage?,
    src: SkRect,
    dst: SkRect,
    param3: SkSamplingOptions,
    param4: SkPaint?,
    param5: SrcRectConstraint,
  ) {
    TODO("Implement onDrawImageRect2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawImageLattice2(const SkImage* image, const Lattice& lattice,
   *                                            const SkRect& dst, SkFilterMode, const SkPaint*) {
   *     SkIRect bounds;
   *     Lattice latticePlusBounds = lattice;
   *     if (!latticePlusBounds.fBounds) {
   *         bounds = SkIRect::MakeWH(image->width(), image->height());
   *         latticePlusBounds.fBounds = &bounds;
   *     }
   *
   *     if (SkLatticeIter::Valid(image->width(), image->height(), latticePlusBounds)) {
   *         SkLatticeIter iter(latticePlusBounds, dst);
   *
   *         SkRect ignored, iterDst;
   *         while (iter.next(&ignored, &iterDst)) {
   *             fList[0]->onDrawRect(iterDst, fPaint);
   *         }
   *     } else {
   *         fList[0]->onDrawRect(dst, fPaint);
   *     }
   * }
   * ```
   */
  public override fun onDrawImageLattice2(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
    param3: SkFilterMode,
    param4: SkPaint?,
  ) {
    TODO("Implement onDrawImageLattice2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawAtlas2(const SkImage* image, const SkRSXform xform[],
   *                                     const SkRect texs[], const SkColor colors[], int count,
   *                                     SkBlendMode mode, const SkSamplingOptions& sampling,
   *                                     const SkRect* cull, const SkPaint* paint) {
   *     SkPaint* paintPtr = &fPaint;
   *     SkPaint storage;
   *     if (paint) {
   *         storage = this->overdrawPaint(*paint);
   *         paintPtr = &storage;
   *     }
   *
   *     fList[0]->onDrawAtlas2(image, xform, texs, colors, count, mode, sampling, cull, paintPtr);
   * }
   * ```
   */
  public override fun onDrawAtlas2(
    image: SkImage?,
    xform: Array<SkRSXform>,
    texs: Array<SkRect>,
    colors: Array<SkColor>,
    count: Int,
    mode: SkBlendMode,
    sampling: SkSamplingOptions,
    cull: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     drawable->draw(this, matrix);
   * }
   * ```
   */
  public override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) {
   *     SkASSERT(false);
   * }
   * ```
   */
  public override fun onDrawPicture(
    param0: SkPicture?,
    param1: SkMatrix?,
    param2: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawAnnotation(const SkRect&, const char[], SkData*) {}
   * ```
   */
  public override fun onDrawAnnotation(
    param0: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     SkRect bounds;
   *     SkDrawShadowMetrics::GetLocalBounds(path, rec, this->getTotalMatrix(), &bounds);
   *     fList[0]->onDrawRect(bounds, fPaint);
   * }
   * ```
   */
  public override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
   *                                         QuadAAFlags aa, const SkColor4f& color, SkBlendMode mode) {
   *     if (clip) {
   *         fList[0]->onDrawPath(SkPath::Polygon({clip, 4}, true), fPaint);
   *     } else {
   *         fList[0]->onDrawRect(rect, fPaint);
   *     }
   * }
   * ```
   */
  public override fun onDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aa: SkCanvas.QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement onDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOverdrawCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[], int count,
   *                                              const SkPoint dstClips[],
   *                                              const SkMatrix preViewMatrices[],
   *                                              const SkSamplingOptions& sampling,
   *                                              const SkPaint* paint,
   *                                              SrcRectConstraint constraint) {
   *     int clipIndex = 0;
   *     for (int i = 0; i < count; ++i) {
   *         if (set[i].fMatrixIndex >= 0) {
   *             fList[0]->save();
   *             fList[0]->concat(preViewMatrices[set[i].fMatrixIndex]);
   *         }
   *         if (set[i].fHasClip) {
   *             fList[0]->onDrawPath(SkPath::Polygon({dstClips + clipIndex, 4}, true), fPaint);
   *             clipIndex += 4;
   *         } else {
   *             fList[0]->onDrawRect(set[i].fDstRect, fPaint);
   *         }
   *         if (set[i].fMatrixIndex >= 0) {
   *             fList[0]->restore();
   *         }
   *     }
   * }
   * ```
   */
  public override fun onDrawEdgeAAImageSet2(
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

  /**
   * C++ original:
   * ```cpp
   * inline SkPaint SkOverdrawCanvas::overdrawPaint(const SkPaint& paint) {
   *     SkPaint newPaint = fPaint;
   *     newPaint.setStyle(paint.getStyle());
   *     newPaint.setStrokeWidth(paint.getStrokeWidth());
   *     return newPaint;
   * }
   * ```
   */
  private fun overdrawPaint(paint: SkPaint): Int {
    TODO("Implement overdrawPaint")
  }
}
