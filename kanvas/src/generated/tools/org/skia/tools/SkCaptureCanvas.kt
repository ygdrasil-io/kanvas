package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.core.SkDrawShadowRec
import org.skia.core.SkFilterMode
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
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
import org.skia.utils.SkNWayCanvas
import org.skia.utils.Slug
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
 * class SkCaptureCanvas : public SkNWayCanvas {
 * public:
 *     SkCaptureCanvas(SkCanvas*, SkCaptureManager*);
 *     ~SkCaptureCanvas() override;
 *
 *     sk_sp<SkPicture> snapPicture();
 *
 * protected:
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
 *     void onDrawTextBlob(const SkTextBlob* blob,
 *                         SkScalar x,
 *                         SkScalar y,
 *                         const SkPaint& paint) override;
 *     void onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) override;
 *     void onDrawPatch(const SkPoint cubics[12],
 *                      const SkColor colors[4],
 *                      const SkPoint texCoords[4],
 *                      SkBlendMode,
 *                      const SkPaint& paint) override;
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
 *     void onDrawImage2(
 *             const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&, const SkPaint*) override;
 *     void onDrawImageRect2(const SkImage*,
 *                           const SkRect&,
 *                           const SkRect&,
 *                           const SkSamplingOptions&,
 *                           const SkPaint*,
 *                           SrcRectConstraint) override;
 *     void onDrawImageLattice2(
 *             const SkImage*, const Lattice&, const SkRect&, SkFilterMode, const SkPaint*) override;
 *     void onDrawAtlas2(const SkImage*,
 *                       const SkRSXform[],
 *                       const SkRect[],
 *                       const SkColor[],
 *                       int,
 *                       SkBlendMode,
 *                       const SkSamplingOptions&,
 *                       const SkRect*,
 *                       const SkPaint*) override;
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
 *     void onDrawEdgeAAQuad(
 *             const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&, SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[],
 *                                int count,
 *                                const SkPoint[],
 *                                const SkMatrix[],
 *                                const SkSamplingOptions&,
 *                                const SkPaint*,
 *                                SrcRectConstraint) override;
 *
 * private:
 *     void pollCapturingStatus();
 *     void attachRecordingCanvas();
 *     void detachRecordingCanvas();
 *
 *     void onSurfaceDelete() override;
 *
 *     bool fCapturing = false;
 *     SkPictureRecorder fRecorder;
 *     SkCanvas* fBaseCanvas = nullptr;
 *     SkCaptureManager* fManager = nullptr;
 *
 *     // Hide NWay management functions as clients shouldn't be using these directly.
 *     void addCanvas(SkCanvas* canvas) override {SkNWayCanvas::addCanvas(canvas);}
 *     void removeCanvas(SkCanvas* canvas) override {SkNWayCanvas::removeCanvas(canvas);}
 *     void removeAll() override {SkNWayCanvas::removeAll();}
 * }
 * ```
 */
public open class SkCaptureCanvas public constructor(
  canvas: SkCanvas?,
  manager: SkCaptureManager?,
) : SkNWayCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool fCapturing = false
   * ```
   */
  private var fCapturing: Boolean = TODO("Initialize fCapturing")

  /**
   * C++ original:
   * ```cpp
   * SkPictureRecorder fRecorder
   * ```
   */
  private var fRecorder: SkPictureRecorder = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* fBaseCanvas = nullptr
   * ```
   */
  private var fBaseCanvas: SkCanvas? = TODO("Initialize fBaseCanvas")

  /**
   * C++ original:
   * ```cpp
   * SkCaptureManager* fManager = nullptr
   * ```
   */
  private var fManager: SkCaptureManager? = TODO("Initialize fManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkCaptureCanvas::snapPicture() {
   *     if (!fCapturing) {
   *         return nullptr;
   *     }
   *     this->detachRecordingCanvas(); // remove the stale recording canvas before the recorder finishes
   *     auto skp = fRecorder.finishRecordingAsPicture();
   *     this->attachRecordingCanvas();
   *     return skp;
   * }
   * ```
   */
  public fun snapPicture(): SkSp<SkPicture> {
    TODO("Implement snapPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::willSave() {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::willSave();
   * }
   * ```
   */
  protected override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy SkCaptureCanvas::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     this->pollCapturingStatus();
   *     return this->SkNWayCanvas::getSaveLayerStrategy(rec);
   * }
   * ```
   */
  protected override fun getSaveLayerStrategy(rec: SaveLayerRec): Int {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCaptureCanvas::onDoSaveBehind(const SkRect* bounds) {
   *     this->pollCapturingStatus();
   *     return this->SkNWayCanvas::onDoSaveBehind(bounds);
   * }
   * ```
   */
  protected override fun onDoSaveBehind(bounds: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::willRestore() {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::willRestore();
   * }
   * ```
   */
  protected override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::didConcat44(const SkM44& m) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::didConcat44(m);
   * }
   * ```
   */
  protected override fun didConcat44(m: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::didSetM44(const SkM44& matrix) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::didSetM44(matrix);
   * }
   * ```
   */
  protected override fun didSetM44(matrix: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::didScale(SkScalar x, SkScalar y) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::didScale(x, y);
   * }
   * ```
   */
  protected override fun didScale(x: SkScalar, y: SkScalar) {
    TODO("Implement didScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::didTranslate(SkScalar x, SkScalar y) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::didTranslate(x, y);
   * }
   * ```
   */
  protected override fun didTranslate(x: SkScalar, y: SkScalar) {
    TODO("Implement didTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawDRRect(const SkRRect& outer,
   *                                    const SkRRect& inner,
   *                                    const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawDRRect(outer, inner, paint);
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
   * void SkCaptureCanvas::onDrawGlyphRunList(const sktext::GlyphRunList& list, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawGlyphRunList(list, paint);
   * }
   * ```
   */
  protected override fun onDrawGlyphRunList(list: GlyphRunList, paint: SkPaint) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawTextBlob(const SkTextBlob* blob,
   *                                      SkScalar x,
   *                                      SkScalar y,
   *                                      const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawTextBlob(blob, x, y, paint);
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
   * void SkCaptureCanvas::onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawSlug(slug, paint);
   * }
   * ```
   */
  protected override fun onDrawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement onDrawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawPatch(const SkPoint cubics[12],
   *                                   const SkColor colors[4],
   *                                   const SkPoint texCoords[4],
   *                                   SkBlendMode bmode,
   *                                   const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawPatch(cubics, colors, texCoords, bmode, paint);
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
   * void SkCaptureCanvas::onDrawPaint(const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawPaint(paint);
   * }
   * ```
   */
  protected override fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawBehind(const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawBehind(paint);
   * }
   * ```
   */
  protected override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawPoints(PointMode mode,
   *                                    size_t count,
   *                                    const SkPoint pts[],
   *                                    const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawPoints(mode, count, pts, paint);
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
   * void SkCaptureCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawRect(rect, paint);
   * }
   * ```
   */
  protected override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawRegion(region, paint);
   * }
   * ```
   */
  protected override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawOval(const SkRect& rect, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawOval(rect, paint);
   * }
   * ```
   */
  protected override fun onDrawOval(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawArc(const SkRect& rect,
   *                                 SkScalar startAngle,
   *                                 SkScalar sweepAngle,
   *                                 bool useCenter,
   *                                 const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawArc(rect, startAngle, sweepAngle, useCenter, paint);
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
   * void SkCaptureCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawRRect(rrect, paint);
   * }
   * ```
   */
  protected override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawPath(path, paint);
   * }
   * ```
   */
  protected override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawImage2(const SkImage* image,
   *                                    SkScalar left,
   *                                    SkScalar top,
   *                                    const SkSamplingOptions& sampling,
   *                                    const SkPaint* paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawImage2(image, left, top, sampling, paint);
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
   * void SkCaptureCanvas::onDrawImageRect2(const SkImage* image,
   *                                        const SkRect& src,
   *                                        const SkRect& dst,
   *                                        const SkSamplingOptions& sampling,
   *                                        const SkPaint* paint,
   *                                        SrcRectConstraint constraint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawImageRect2(image, src, dst, sampling, paint, constraint);
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
   * void SkCaptureCanvas::onDrawImageLattice2(const SkImage* image,
   *                                           const Lattice& lattice,
   *                                           const SkRect& dst,
   *                                           SkFilterMode filter,
   *                                           const SkPaint* paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawImageLattice2(image, lattice, dst, filter, paint);
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
   * void SkCaptureCanvas::onDrawAtlas2(const SkImage* image,
   *                                    const SkRSXform xform[],
   *                                    const SkRect tex[],
   *                                    const SkColor colors[],
   *                                    int count,
   *                                    SkBlendMode bmode,
   *                                    const SkSamplingOptions& sampling,
   *                                    const SkRect* cull,
   *                                    const SkPaint* paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawAtlas2(image, xform, tex, colors, count, bmode, sampling, cull, paint);
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
   * void SkCaptureCanvas::onDrawVerticesObject(const SkVertices* vertices,
   *                                            SkBlendMode bmode,
   *                                            const SkPaint& paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawVerticesObject(vertices, bmode, paint);
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
   * void SkCaptureCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     this->pollCapturingStatus();
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
   * void SkCaptureCanvas::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onClipRect(rect, op, edgeStyle);
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
   * void SkCaptureCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onClipRRect(rrect, op, edgeStyle);
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
   * void SkCaptureCanvas::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onClipPath(path, op, edgeStyle);
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
   * void SkCaptureCanvas::onClipShader(sk_sp<SkShader> sh, SkClipOp op) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onClipShader(sh, op);
   * }
   * ```
   */
  protected override fun onClipShader(sh: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onClipRegion(const SkRegion& deviceRgn, SkClipOp op) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onClipRegion(deviceRgn, op);
   * }
   * ```
   */
  protected override fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onResetClip() {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onResetClip();
   * }
   * ```
   */
  protected override fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawPicture(const SkPicture* picture,
   *                                     const SkMatrix* matrix,
   *                                     const SkPaint* paint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawPicture(picture, matrix, paint);
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
   * void SkCaptureCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawDrawable(drawable, matrix);
   * }
   * ```
   */
  protected override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* data) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawAnnotation(rect, key, data);
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
   * void SkCaptureCanvas::onDrawEdgeAAQuad(const SkRect& rect,
   *                                        const SkPoint clip[4],
   *                                        QuadAAFlags aa,
   *                                        const SkColor4f& color,
   *                                        SkBlendMode mode) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawEdgeAAQuad(rect, clip, aa, color, mode);
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
   * void SkCaptureCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[],
   *                                             int count,
   *                                             const SkPoint dstClips[],
   *                                             const SkMatrix preViewMatrices[],
   *                                             const SkSamplingOptions& sampling,
   *                                             const SkPaint* paint,
   *                                             SrcRectConstraint constraint) {
   *     this->pollCapturingStatus();
   *     this->SkNWayCanvas::onDrawEdgeAAImageSet2(
   *             set, count, dstClips, preViewMatrices, sampling, paint, constraint);
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
   * void SkCaptureCanvas::pollCapturingStatus() {
   *     bool shouldPoll = fManager->isCurrentlyCapturing();
   *     if (fCapturing != shouldPoll) {
   *         if (shouldPoll) {
   *             this->attachRecordingCanvas();
   *         } else {
   *             this->detachRecordingCanvas();
   *         }
   *         fCapturing = shouldPoll;
   *     }
   * }
   * ```
   */
  private fun pollCapturingStatus() {
    TODO("Implement pollCapturingStatus")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::attachRecordingCanvas() {
   *     SkASSERT(this->fList.size() == 1);
   *     this->addCanvas(fRecorder.beginRecording(fBaseCanvas->imageInfo().width(),
   *                                               fBaseCanvas->imageInfo().height()));
   * }
   * ```
   */
  private fun attachRecordingCanvas() {
    TODO("Implement attachRecordingCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::detachRecordingCanvas() {
   *     SkASSERT(this->fList.size() == 2);
   *     this->removeCanvas(fRecorder.getRecordingCanvas());
   * }
   * ```
   */
  private fun detachRecordingCanvas() {
    TODO("Implement detachRecordingCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCaptureCanvas::onSurfaceDelete() {
   *     // TODO (b/412351769): signal to the capture manager that this canvas's surface has been deleted
   * }
   * ```
   */
  public override fun onSurfaceDelete() {
    TODO("Implement onSurfaceDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void addCanvas(SkCanvas* canvas) override {SkNWayCanvas::addCanvas(canvas);}
   * ```
   */
  public override fun addCanvas(canvas: SkCanvas?) {
    TODO("Implement addCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void removeCanvas(SkCanvas* canvas) override {SkNWayCanvas::removeCanvas(canvas);}
   * ```
   */
  public override fun removeCanvas(canvas: SkCanvas?) {
    TODO("Implement removeCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void removeAll() override {SkNWayCanvas::removeAll();}
   * ```
   */
  public override fun removeAll() {
    TODO("Implement removeAll")
  }
}
