package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkCanvasVirtualEnforcer
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
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaceProps
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.ImageSetEntry
import undefined.Lattice
import undefined.PointMode
import undefined.QuadAAFlags
import undefined.SkColor4f
import undefined.SrcRectConstraint

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPaintFilterCanvas : public SkCanvasVirtualEnforcer<SkNWayCanvas> {
 * public:
 *     /**
 *      * The new SkPaintFilterCanvas is configured for forwarding to the
 *      * specified canvas.  Also copies the target canvas matrix and clip bounds.
 *      */
 *     explicit SkPaintFilterCanvas(SkCanvas* canvas);
 *
 *     enum Type {
 *         kPicture_Type,
 *     };
 *
 *     // Forwarded to the wrapped canvas.
 *     SkISize getBaseLayerSize() const override { return proxy()->getBaseLayerSize(); }
 *     GrRecordingContext* recordingContext() const override { return proxy()->recordingContext(); }
 * protected:
 *     /**
 *      *  Called with the paint that will be used to draw the specified type.
 *      *  The implementation may modify the paint as they wish.
 *      *
 *      *  The result bool is used to determine whether the draw op is to be
 *      *  executed (true) or skipped (false).
 *      *
 *      *  Note: The base implementation calls onFilter() for top-level/explicit paints only.
 *      *        To also filter encapsulated paints (e.g. SkPicture, SkTextBlob), clients may need to
 *      *        override the relevant methods (i.e. drawPicture, drawTextBlob).
 *      */
 *     virtual bool onFilter(SkPaint& paint) const = 0;
 *
 *     void onDrawPaint(const SkPaint&) override;
 *     void onDrawBehind(const SkPaint&) override;
 *     void onDrawPoints(PointMode, size_t count, const SkPoint pts[], const SkPaint&) override;
 *     void onDrawRect(const SkRect&, const SkPaint&) override;
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override;
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override;
 *     void onDrawOval(const SkRect&, const SkPaint&) override;
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override;
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
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override;
 *     void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                              const SkPoint texCoords[4], SkBlendMode,
 *                              const SkPaint& paint) override;
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *
 *     void onDrawGlyphRunList(const sktext::GlyphRunList&, const SkPaint&) override;
 *     void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
 *                         const SkPaint& paint) override;
 *     void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) override;
 *     void onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) override;
 *
 *     void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&,
 *                           SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[], int count, const SkPoint[], const SkMatrix[],
 *                                const SkSamplingOptions&,const SkPaint*, SrcRectConstraint) override;
 *
 *     // Forwarded to the wrapped canvas.
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo&, const SkSurfaceProps&) override;
 *     bool onPeekPixels(SkPixmap* pixmap) override;
 *     bool onAccessTopLayerPixels(SkPixmap* pixmap) override;
 *     SkImageInfo onImageInfo() const override;
 *     bool onGetProps(SkSurfaceProps* props, bool top) const override;
 *
 * private:
 *     class AutoPaintFilter;
 *
 *     SkCanvas* proxy() const { SkASSERT(fList.size() == 1); return fList[0]; }
 *
 *     SkPaintFilterCanvas* internal_private_asPaintFilterCanvas() const override {
 *         return const_cast<SkPaintFilterCanvas*>(this);
 *     }
 *
 *     friend class SkAndroidFrameworkUtils;
 * }
 * ```
 */
public abstract class SkPaintFilterCanvas public constructor(
  canvas: SkCanvas?,
) : SkCanvasVirtualEnforcer(),
    SkNWayCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkISize getBaseLayerSize() const override { return proxy()->getBaseLayerSize(); }
   * ```
   */
  public override fun getBaseLayerSize(): Int {
    TODO("Implement getBaseLayerSize")
  }

  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext* recordingContext() const override { return proxy()->recordingContext(); }
   * ```
   */
  public override fun recordingContext(): GrRecordingContext {
    TODO("Implement recordingContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onFilter(SkPaint& paint) const = 0
   * ```
   */
  protected abstract fun onFilter(paint: SkPaint): Boolean

  /**
   * C++ original:
   * ```cpp
   * void SkPaintFilterCanvas::onDrawPaint(const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawPaint(apf.paint());
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
   * void SkPaintFilterCanvas::onDrawBehind(const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawBehind(apf.paint());
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
   * void SkPaintFilterCanvas::onDrawPoints(PointMode mode, size_t count, const SkPoint pts[],
   *                                        const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawPoints(mode, count, pts, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawRect(rect, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawRRect(rrect, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
   *                                        const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawDRRect(outer, inner, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawRegion(region, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawOval(const SkRect& rect, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawOval(rect, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawArc(const SkRect& rect, SkScalar startAngle, SkScalar sweepAngle,
   *                                     bool useCenter, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawArc(rect, startAngle, sweepAngle, useCenter, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawPath(path, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawImage2(const SkImage* image, SkScalar left, SkScalar top,
   *                                        const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawImage2(image, left, top, sampling, &apf.paint());
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
   * void SkPaintFilterCanvas::onDrawImageRect2(const SkImage* image, const SkRect& src,
   *                                            const SkRect& dst, const SkSamplingOptions& sampling,
   *                                            const SkPaint* paint, SrcRectConstraint constraint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawImageRect2(image, src, dst, sampling, &apf.paint(), constraint);
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
   * void SkPaintFilterCanvas::onDrawImageLattice2(const SkImage* image, const Lattice& lattice,
   *                                               const SkRect& dst, SkFilterMode filter,
   *                                               const SkPaint* paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawImageLattice2(image, lattice, dst, filter, &apf.paint());
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
   * void SkPaintFilterCanvas::onDrawAtlas2(const SkImage* image, const SkRSXform xform[],
   *                                        const SkRect tex[], const SkColor colors[], int count,
   *                                        SkBlendMode bmode, const SkSamplingOptions& sampling,
   *                                        const SkRect* cull, const SkPaint* paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawAtlas2(image, xform, tex, colors, count, bmode, sampling, cull,
   *                                          &apf.paint());
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
   * void SkPaintFilterCanvas::onDrawVerticesObject(const SkVertices* vertices,
   *                                                SkBlendMode bmode, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawVerticesObject(vertices, bmode, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                                       const SkPoint texCoords[4], SkBlendMode bmode,
   *                                       const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawPatch(cubics, colors, texCoords, bmode, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawPicture(const SkPicture* picture, const SkMatrix* m,
   *                                         const SkPaint* originalPaint) {
   *     AutoPaintFilter apf(this, originalPaint);
   *     if (apf.shouldDraw()) {
   *         const SkPaint* newPaint = &apf.paint();
   *
   *         // Passing a paint (-vs- passing null) makes drawPicture draw into a layer...
   *         // much slower, and can produce different blending. Thus we should only do this
   *         // if the filter's effect actually impacts the picture.
   *         if (originalPaint == nullptr) {
   *             if (   newPaint->getAlphaf()      == 1.0f
   *                 && newPaint->getColorFilter() == nullptr
   *                 && newPaint->getImageFilter() == nullptr
   *                 && newPaint->asBlendMode()    == SkBlendMode::kSrcOver) {
   *                 // restore the original nullptr
   *                 newPaint = nullptr;
   *             }
   *         }
   *         this->SkNWayCanvas::onDrawPicture(picture, m, newPaint);
   *     }
   * }
   * ```
   */
  protected override fun onDrawPicture(
    picture: SkPicture?,
    m: SkMatrix?,
    originalPaint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPaintFilterCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     // There is no paint to filter in this case, but we can still filter on type.
   *     // Subclasses need to unroll the drawable explicity (by overriding this method) in
   *     // order to actually filter nested content.
   *     AutoPaintFilter apf(this, nullptr);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawDrawable(drawable, matrix);
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
   * void SkPaintFilterCanvas::onDrawGlyphRunList(
   *         const sktext::GlyphRunList& list, const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawGlyphRunList(list, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
   *                                          const SkPaint& paint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawTextBlob(blob, x, y, apf.paint());
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
   * void SkPaintFilterCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) {
   *     this->SkNWayCanvas::onDrawAnnotation(rect, key, value);
   * }
   * ```
   */
  protected override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPaintFilterCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     this->SkNWayCanvas::onDrawShadowRec(path, rec);
   * }
   * ```
   */
  protected override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPaintFilterCanvas::onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
   *                                            QuadAAFlags aa, const SkColor4f& color, SkBlendMode mode) {
   *     SkPaint paint;
   *     paint.setColor(color);
   *     paint.setBlendMode(mode);
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawEdgeAAQuad(rect, clip, aa, apf.paint().getColor4f(),
   *                                              apf.paint().getBlendMode_or(SkBlendMode::kSrcOver));
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
   * void SkPaintFilterCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[], int count,
   *                                                 const SkPoint dstClips[],
   *                                                 const SkMatrix preViewMatrices[],
   *                                                 const SkSamplingOptions& sampling,
   *                                                 const SkPaint* paint,
   *                                                 SrcRectConstraint constraint) {
   *     AutoPaintFilter apf(this, paint);
   *     if (apf.shouldDraw()) {
   *         this->SkNWayCanvas::onDrawEdgeAAImageSet2(
   *                 set, count, dstClips, preViewMatrices, sampling, &apf.paint(), constraint);
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

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkPaintFilterCanvas::onNewSurface(const SkImageInfo& info,
   *                                                    const SkSurfaceProps& props) {
   *     return this->proxy()->makeSurface(info, &props);
   * }
   * ```
   */
  protected override fun onNewSurface(info: SkImageInfo, props: SkSurfaceProps): Int {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPaintFilterCanvas::onPeekPixels(SkPixmap* pixmap) {
   *     return this->proxy()->peekPixels(pixmap);
   * }
   * ```
   */
  protected override fun onPeekPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPaintFilterCanvas::onAccessTopLayerPixels(SkPixmap* pixmap) {
   *     SkImageInfo info;
   *     size_t rowBytes;
   *
   *     void* addr = this->proxy()->accessTopLayerPixels(&info, &rowBytes);
   *     if (!addr) {
   *         return false;
   *     }
   *
   *     pixmap->reset(info, addr, rowBytes);
   *     return true;
   * }
   * ```
   */
  protected override fun onAccessTopLayerPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement onAccessTopLayerPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkPaintFilterCanvas::onImageInfo() const {
   *     return this->proxy()->imageInfo();
   * }
   * ```
   */
  protected override fun onImageInfo(): Int {
    TODO("Implement onImageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPaintFilterCanvas::onGetProps(SkSurfaceProps* props, bool top) const {
   *     if (props) {
   *         *props = top ? this->proxy()->getTopProps() : this->proxy()->getBaseProps();
   *     }
   *     return true;
   * }
   * ```
   */
  protected override fun onGetProps(props: SkSurfaceProps?, top: Boolean): Boolean {
    TODO("Implement onGetProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* proxy() const { SkASSERT(fList.size() == 1); return fList[0]; }
   * ```
   */
  private fun proxy(): Int {
    TODO("Implement proxy")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaintFilterCanvas* internal_private_asPaintFilterCanvas() const override {
   *         return const_cast<SkPaintFilterCanvas*>(this);
   *     }
   * ```
   */
  public override fun internalPrivateAsPaintFilterCanvas(): SkPaintFilterCanvas {
    TODO("Implement internalPrivateAsPaintFilterCanvas")
  }

  public enum class Type {
    kPicture_Type,
  }
}
