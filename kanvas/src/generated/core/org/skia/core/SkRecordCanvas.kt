package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.utils.SkNoDrawCanvas
import org.skia.utils.Slug
import undefined.Args
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
 * class SkRecordCanvas final : public SkCanvasVirtualEnforcer<SkNoDrawCanvas> {
 * public:
 *     // Does not take ownership of the SkRecord.
 *     SkRecordCanvas(SkRecord*, int width, int height);  // TODO: remove
 *     SkRecordCanvas(SkRecord*, const SkRect& bounds);
 *
 *     void reset(SkRecord*, const SkRect& bounds);
 *
 *     size_t approxBytesUsedBySubPictures() const { return fApproxBytesUsedBySubPictures; }
 *
 *     SkDrawableList* getDrawableList() const { return fDrawableList.get(); }
 *     std::unique_ptr<SkDrawableList> detachDrawableList() { return std::move(fDrawableList); }
 *
 *     // Make SkRecordCanvas forget entirely about its SkRecord*; all calls to SkRecordCanvas will
 *     // fail.
 *     void forgetRecord();
 *
 *     void willSave() override;
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec&) override;
 *     bool onDoSaveBehind(const SkRect*) override;
 *     void willRestore() override {}
 *     void didRestore() override;
 *     SkRecorder* baseRecorder() const override {
 *         // TODO(kjlubick) this class should implement SkRecorder (or maybe Record should).
 *         return skcpu::Recorder::TODO();
 *     }
 *
 *     void didConcat44(const SkM44&) override;
 *     void didSetM44(const SkM44&) override;
 *     void didScale(SkScalar, SkScalar) override;
 *     void didTranslate(SkScalar, SkScalar) override;
 *
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *     void onDrawTextBlob(const SkTextBlob* blob,
 *                         SkScalar x,
 *                         SkScalar y,
 *                         const SkPaint& paint) override;
 *     void onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) override;
 *     void onDrawGlyphRunList(const sktext::GlyphRunList& glyphRunList,
 *                             const SkPaint& paint) override;
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
 *
 *     void onDrawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&) override;
 *
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override;
 *
 *     void onClipRect(const SkRect& rect, SkClipOp, ClipEdgeStyle) override;
 *     void onClipRRect(const SkRRect& rrect, SkClipOp, ClipEdgeStyle) override;
 *     void onClipPath(const SkPath& path, SkClipOp, ClipEdgeStyle) override;
 *     void onClipShader(sk_sp<SkShader>, SkClipOp) override;
 *     void onClipRegion(const SkRegion& deviceRgn, SkClipOp) override;
 *     void onResetClip() override;
 *
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *
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
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo&, const SkSurfaceProps&) override;
 *
 * private:
 *     template <typename T> T* copy(const T*);
 *
 *     template <typename T> T* copy(const T[], size_t count);
 *
 *     template <typename T, typename... Args> void append(Args&&...);
 *
 *     size_t fApproxBytesUsedBySubPictures;
 *     SkRecord* fRecord;
 *     std::unique_ptr<SkDrawableList> fDrawableList;
 * }
 * ```
 */
public class SkRecordCanvas public constructor(
  record: SkRecord?,
  width: Int,
  height: Int,
) : SkCanvasVirtualEnforcer(TODO(), TODO()),
    SkNoDrawCanvas {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T, typename... Args> void append(Args&&...);
   *
   *     size_t fApproxBytesUsedBySubPictures
   * ```
   */
  private var fApproxBytesUsedBySubPictures: ULong =
      TODO("Initialize fApproxBytesUsedBySubPictures")

  /**
   * C++ original:
   * ```cpp
   * SkRecord* fRecord
   * ```
   */
  private var fRecord: SkRecord? = TODO("Initialize fRecord")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkDrawableList> fDrawableList
   * ```
   */
  private var fDrawableList: Int = TODO("Initialize fDrawableList")

  /**
   * C++ original:
   * ```cpp
   * SkRecordCanvas::SkRecordCanvas(SkRecord* record, int width, int height)
   *         : SkCanvasVirtualEnforcer<SkNoDrawCanvas>(width, height)
   *         , fApproxBytesUsedBySubPictures(0)
   *         , fRecord(record) {
   *     SkASSERT(this->imageInfo().width() >= 0 && this->imageInfo().height() >= 0);
   * }
   * ```
   */
  public constructor(record: SkRecord?, bounds: SkRect) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::reset(SkRecord* record, const SkRect& bounds) {
   *     this->forgetRecord();
   *     fRecord = record;
   *     this->resetCanvas(safe_picture_bounds(bounds));
   *     SkASSERT(this->imageInfo().width() >= 0 && this->imageInfo().height() >= 0);
   * }
   * ```
   */
  public fun reset(record: SkRecord?, bounds: SkRect) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t approxBytesUsedBySubPictures() const { return fApproxBytesUsedBySubPictures; }
   * ```
   */
  public fun approxBytesUsedBySubPictures(): ULong {
    TODO("Implement approxBytesUsedBySubPictures")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDrawableList* getDrawableList() const { return fDrawableList.get(); }
   * ```
   */
  public fun getDrawableList(): SkDrawableList {
    TODO("Implement getDrawableList")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkDrawableList> detachDrawableList() { return std::move(fDrawableList); }
   * ```
   */
  public fun detachDrawableList(): Int {
    TODO("Implement detachDrawableList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::forgetRecord() {
   *     fDrawableList.reset(nullptr);
   *     fApproxBytesUsedBySubPictures = 0;
   *     fRecord = nullptr;
   * }
   * ```
   */
  public fun forgetRecord() {
    TODO("Implement forgetRecord")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::willSave() { this->append<SkRecords::Save>(); }
   * ```
   */
  public override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy SkRecordCanvas::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     AutoTArray<sk_sp<SkImageFilter>> filters(rec.fFilters.size());
   *     for (size_t i = 0; i < rec.fFilters.size(); ++i) {
   *         filters[i] = rec.fFilters[i];
   *     }
   *
   *     this->append<SkRecords::SaveLayer>(this->copy(rec.fBounds),
   *                                        this->copy(rec.fPaint),
   *                                        sk_ref_sp(rec.fBackdrop),
   *                                        rec.fSaveLayerFlags,
   *                                        SkCanvasPriv::GetBackdropScaleFactor(rec),
   *                                        rec.fBackdropTileMode,
   *                                        std::move(filters));
   *     return SkCanvas::kNoLayer_SaveLayerStrategy;
   * }
   * ```
   */
  public override fun getSaveLayerStrategy(rec: SaveLayerRec): Int {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRecordCanvas::onDoSaveBehind(const SkRect* subset) {
   *     this->append<SkRecords::SaveBehind>(this->copy(subset));
   *     return false;
   * }
   * ```
   */
  public override fun onDoSaveBehind(subset: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void willRestore() override {}
   * ```
   */
  public override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::didRestore() { this->append<SkRecords::Restore>(this->getTotalMatrix()); }
   * ```
   */
  public override fun didRestore() {
    TODO("Implement didRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* baseRecorder() const override {
   *         // TODO(kjlubick) this class should implement SkRecorder (or maybe Record should).
   *         return skcpu::Recorder::TODO();
   *     }
   * ```
   */
  public override fun baseRecorder(): SkRecorder {
    TODO("Implement baseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::didConcat44(const SkM44& m) { this->append<SkRecords::Concat44>(m); }
   * ```
   */
  public override fun didConcat44(m: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::didSetM44(const SkM44& m) { this->append<SkRecords::SetM44>(m); }
   * ```
   */
  public override fun didSetM44(m: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::didScale(SkScalar sx, SkScalar sy) { this->append<SkRecords::Scale>(sx, sy); }
   * ```
   */
  public override fun didScale(sx: SkScalar, sy: SkScalar) {
    TODO("Implement didScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::didTranslate(SkScalar dx, SkScalar dy) {
   *     this->append<SkRecords::Translate>(dx, dy);
   * }
   * ```
   */
  public override fun didTranslate(dx: SkScalar, dy: SkScalar) {
    TODO("Implement didTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawDRRect(const SkRRect& outer,
   *                                   const SkRRect& inner,
   *                                   const SkPaint& paint) {
   *     this->append<SkRecords::DrawDRRect>(paint, outer, inner);
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
   * void SkRecordCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     if (!fDrawableList) {
   *         fDrawableList = std::make_unique<SkDrawableList>();
   *     }
   *     fDrawableList->append(drawable);
   *     this->append<SkRecords::DrawDrawable>(
   *             this->copy(matrix), drawable->getBounds(), fDrawableList->count() - 1);
   * }
   * ```
   */
  public override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawTextBlob(const SkTextBlob* blob,
   *                                     SkScalar x,
   *                                     SkScalar y,
   *                                     const SkPaint& paint) {
   *     this->append<SkRecords::DrawTextBlob>(paint, sk_ref_sp(blob), x, y);
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
   * void SkRecordCanvas::onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) {
   *     this->append<SkRecords::DrawSlug>(paint, sk_ref_sp(slug));
   * }
   * ```
   */
  public override fun onDrawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement onDrawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawGlyphRunList(const sktext::GlyphRunList& glyphRunList,
   *                                         const SkPaint& paint) {
   *     sk_sp<SkTextBlob> blob = sk_ref_sp(glyphRunList.blob());
   *     if (glyphRunList.blob() == nullptr) {
   *         blob = glyphRunList.makeBlob();
   *     }
   *
   *     this->onDrawTextBlob(blob.get(), glyphRunList.origin().x(), glyphRunList.origin().y(), paint);
   * }
   * ```
   */
  public override fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: SkPaint) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawPatch(const SkPoint cubics[12],
   *                                  const SkColor colors[4],
   *                                  const SkPoint texCoords[4],
   *                                  SkBlendMode bmode,
   *                                  const SkPaint& paint) {
   *     this->append<SkRecords::DrawPatch>(
   *             paint,
   *             cubics ? this->copy(cubics, SkPatchUtils::kNumCtrlPts) : nullptr,
   *             colors ? this->copy(colors, SkPatchUtils::kNumCorners) : nullptr,
   *             texCoords ? this->copy(texCoords, SkPatchUtils::kNumCorners) : nullptr,
   *             bmode);
   * }
   * ```
   */
  public override fun onDrawPatch(
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
   * template <> char* SkRecordCanvas::copy(const char* src) { return this->copy(src, strlen(src) + 1); }
   *
   * void SkRecordCanvas::onDrawPaint(const SkPaint& paint) {
   *     this->append<SkRecords::DrawPaint>(paint);
   * }
   * ```
   */
  public override fun onDrawPaint(src: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawBehind(const SkPaint& paint) {
   *     this->append<SkRecords::DrawBehind>(paint);
   * }
   * ```
   */
  public override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawPoints(PointMode mode,
   *                                   size_t count,
   *                                   const SkPoint pts[],
   *                                   const SkPaint& paint) {
   *     this->append<SkRecords::DrawPoints>(paint, mode, SkToUInt(count), this->copy(pts, count));
   * }
   * ```
   */
  public override fun onDrawPoints(
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
   * void SkRecordCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     this->append<SkRecords::DrawRect>(paint, rect);
   * }
   * ```
   */
  public override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     this->append<SkRecords::DrawRegion>(paint, region);
   * }
   * ```
   */
  public override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
   *     this->append<SkRecords::DrawOval>(paint, oval);
   * }
   * ```
   */
  public override fun onDrawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawArc(const SkRect& oval,
   *                                SkScalar startAngle,
   *                                SkScalar sweepAngle,
   *                                bool useCenter,
   *                                const SkPaint& paint) {
   *     this->append<SkRecords::DrawArc>(paint, oval, startAngle, sweepAngle, useCenter);
   * }
   * ```
   */
  public override fun onDrawArc(
    oval: SkRect,
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
   * void SkRecordCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     this->append<SkRecords::DrawRRect>(paint, rrect);
   * }
   * ```
   */
  public override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     this->append<SkRecords::DrawPath>(paint, path);
   * }
   * ```
   */
  public override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawImage2(const SkImage* image,
   *                                   SkScalar x,
   *                                   SkScalar y,
   *                                   const SkSamplingOptions& sampling,
   *                                   const SkPaint* paint) {
   *     this->append<SkRecords::DrawImage>(this->copy(paint), sk_ref_sp(image), x, y, sampling);
   * }
   * ```
   */
  public override fun onDrawImage2(
    image: SkImage?,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawImageRect2(const SkImage* image,
   *                                       const SkRect& src,
   *                                       const SkRect& dst,
   *                                       const SkSamplingOptions& sampling,
   *                                       const SkPaint* paint,
   *                                       SrcRectConstraint constraint) {
   *     this->append<SkRecords::DrawImageRect>(
   *             this->copy(paint), sk_ref_sp(image), src, dst, sampling, constraint);
   * }
   * ```
   */
  public override fun onDrawImageRect2(
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
   * void SkRecordCanvas::onDrawImageLattice2(const SkImage* image,
   *                                          const Lattice& lattice,
   *                                          const SkRect& dst,
   *                                          SkFilterMode filter,
   *                                          const SkPaint* paint) {
   *     int flagCount = lattice.fRectTypes ? (lattice.fXCount + 1) * (lattice.fYCount + 1) : 0;
   *     SkASSERT(lattice.fBounds);
   *     this->append<SkRecords::DrawImageLattice>(this->copy(paint),
   *                                               sk_ref_sp(image),
   *                                               lattice.fXCount,
   *                                               this->copy(lattice.fXDivs, lattice.fXCount),
   *                                               lattice.fYCount,
   *                                               this->copy(lattice.fYDivs, lattice.fYCount),
   *                                               flagCount,
   *                                               this->copy(lattice.fRectTypes, flagCount),
   *                                               this->copy(lattice.fColors, flagCount),
   *                                               *lattice.fBounds,
   *                                               dst,
   *                                               filter);
   * }
   * ```
   */
  public override fun onDrawImageLattice2(
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
   * void SkRecordCanvas::onDrawAtlas2(const SkImage* atlas,
   *                                   const SkRSXform xform[],
   *                                   const SkRect tex[],
   *                                   const SkColor colors[],
   *                                   int count,
   *                                   SkBlendMode mode,
   *                                   const SkSamplingOptions& sampling,
   *                                   const SkRect* cull,
   *                                   const SkPaint* paint) {
   *     this->append<SkRecords::DrawAtlas>(this->copy(paint),
   *                                        sk_ref_sp(atlas),
   *                                        this->copy(xform, count),
   *                                        this->copy(tex, count),
   *                                        this->copy(colors, count),
   *                                        (unsigned)count,
   *                                        mode,
   *                                        sampling,
   *                                        this->copy(cull));
   * }
   * ```
   */
  public override fun onDrawAtlas2(
    atlas: SkImage?,
    xform: Array<SkRSXform>,
    tex: Array<SkRect>,
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
   * void SkRecordCanvas::onDrawVerticesObject(const SkVertices* vertices,
   *                                           SkBlendMode bmode,
   *                                           const SkPaint& paint) {
   *     this->append<SkRecords::DrawVertices>(
   *             paint, sk_ref_sp(const_cast<SkVertices*>(vertices)), bmode);
   * }
   * ```
   */
  public override fun onDrawVerticesObject(
    vertices: SkVertices?,
    bmode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawMesh(const SkMesh& mesh,
   *                                 sk_sp<SkBlender> blender,
   *                                 const SkPaint& paint) {
   *     this->append<SkRecords::DrawMesh>(paint, mesh, std::move(blender));
   * }
   * ```
   */
  public override fun onDrawMesh(
    mesh: SkMesh,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawMesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     this->append<SkRecords::DrawShadowRec>(path, rec);
   * }
   * ```
   */
  public override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     INHERITED(onClipRect, rect, op, edgeStyle);
   *     SkRecords::ClipOpAndAA opAA(op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->append<SkRecords::ClipRect>(rect, opAA);
   * }
   * ```
   */
  public override fun onClipRect(
    rect: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     INHERITED(onClipRRect, rrect, op, edgeStyle);
   *     SkRecords::ClipOpAndAA opAA(op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->append<SkRecords::ClipRRect>(rrect, opAA);
   * }
   * ```
   */
  public override fun onClipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     INHERITED(onClipPath, path, op, edgeStyle);
   *     SkRecords::ClipOpAndAA opAA(op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->append<SkRecords::ClipPath>(path, opAA);
   * }
   * ```
   */
  public override fun onClipPath(
    path: SkPath,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onClipShader(sk_sp<SkShader> cs, SkClipOp op) {
   *     INHERITED(onClipShader, cs, op);
   *     this->append<SkRecords::ClipShader>(std::move(cs), op);
   * }
   * ```
   */
  public override fun onClipShader(cs: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onClipRegion(const SkRegion& deviceRgn, SkClipOp op) {
   *     INHERITED(onClipRegion, deviceRgn, op);
   *     this->append<SkRecords::ClipRegion>(deviceRgn, op);
   * }
   * ```
   */
  public override fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onResetClip() {
   *     INHERITED(onResetClip);
   *     this->append<SkRecords::ResetClip>();
   * }
   * ```
   */
  public override fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawPicture(const SkPicture* pic,
   *                                    const SkMatrix* matrix,
   *                                    const SkPaint* paint) {
   *     fApproxBytesUsedBySubPictures += pic->approximateBytesUsed();
   *     this->append<SkRecords::DrawPicture>(
   *             this->copy(paint), sk_ref_sp(pic), matrix ? *matrix : SkMatrix::I());
   * }
   * ```
   */
  public override fun onDrawPicture(
    pic: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) {
   *     this->append<SkRecords::DrawAnnotation>(rect, SkString(key), sk_ref_sp(value));
   * }
   * ```
   */
  public override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::onDrawEdgeAAQuad(const SkRect& rect,
   *                                       const SkPoint clip[4],
   *                                       QuadAAFlags aa,
   *                                       const SkColor4f& color,
   *                                       SkBlendMode mode) {
   *     this->append<SkRecords::DrawEdgeAAQuad>(rect, this->copy(clip, 4), aa, color, mode);
   * }
   * ```
   */
  public override fun onDrawEdgeAAQuad(
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
   * void SkRecordCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[],
   *                                            int count,
   *                                            const SkPoint dstClips[],
   *                                            const SkMatrix preViewMatrices[],
   *                                            const SkSamplingOptions& sampling,
   *                                            const SkPaint* paint,
   *                                            SrcRectConstraint constraint) {
   *     int totalDstClipCount, totalMatrixCount;
   *     SkCanvasPriv::GetDstClipAndMatrixCounts(set, count, &totalDstClipCount, &totalMatrixCount);
   *
   *     AutoTArray<ImageSetEntry> setCopy(count);
   *     for (int i = 0; i < count; ++i) {
   *         setCopy[i] = set[i];
   *     }
   *
   *     this->append<SkRecords::DrawEdgeAAImageSet>(this->copy(paint),
   *                                                 std::move(setCopy),
   *                                                 count,
   *                                                 this->copy(dstClips, totalDstClipCount),
   *                                                 this->copy(preViewMatrices, totalMatrixCount),
   *                                                 sampling,
   *                                                 constraint);
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
   * sk_sp<SkSurface> SkRecordCanvas::onNewSurface(const SkImageInfo&, const SkSurfaceProps&) {
   *     return nullptr;
   * }
   * ```
   */
  public override fun onNewSurface(param0: SkImageInfo, param1: SkSurfaceProps): SkSp<SkSurface> {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * template <> char* SkRecordCanvas::copy(const char* src) { return this->copy(src, strlen(src) + 1); }
   * ```
   */
  private fun copy(src: T?): T {
    TODO("Implement copy")
  }

  /**
   * C++ original:
   * ```cpp
   * template <> char* SkRecordCanvas::copy(const char src[], size_t count) {
   *     if (nullptr == src) {
   *         return nullptr;
   *     }
   *     char* dst = fRecord->alloc<char>(count);
   *     memcpy(dst, src, count);
   *     return dst;
   * }
   * ```
   */
  private fun copy(src: Array<T>, count: ULong): T {
    TODO("Implement copy")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordCanvas::append(Args&&... args) {
   *     new (fRecord->append<T>()) T{std::forward<Args>(args)...};
   * }
   * ```
   */
  private fun append(args: Args) {
    TODO("Implement append")
  }
}
