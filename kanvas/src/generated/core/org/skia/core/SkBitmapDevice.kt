package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import undefined.CreateInfo

/**
 * C++ original:
 * ```cpp
 * class SkBitmapDevice final : public SkDevice {
 * public:
 *     /**
 *      *  Construct a new device with the specified bitmap as its backend. It is
 *      *  valid for the bitmap to have no pixels associated with it. In that case,
 *      *  any drawing to this device will have no effect.
 *      */
 *     explicit SkBitmapDevice(const SkBitmap& bitmap);
 *
 *     /**
 *      *  Construct a new device with the specified bitmap as its backend. It is
 *      *  valid for the bitmap to have no pixels associated with it. In that case,
 *      *  any drawing to this device will have no effect.
 *      */
 *     SkBitmapDevice(const SkBitmap& bitmap, const SkSurfaceProps& surfaceProps,
 *                    void* externalHandle = nullptr);
 *
 *     SkBitmapDevice(skcpu::RecorderImpl*, const SkBitmap& bitmap);
 *     SkBitmapDevice(skcpu::RecorderImpl*,
 *                    const SkBitmap& bitmap,
 *                    const SkSurfaceProps& surfaceProps,
 *                    void* externalHandle = nullptr);
 *
 *     static sk_sp<SkBitmapDevice> Create(const SkImageInfo&, const SkSurfaceProps&,
 *                                         SkRasterHandleAllocator* = nullptr);
 *
 *     void drawPaint(const SkPaint& paint) override;
 *     void drawPoints(SkCanvas::PointMode, SkSpan<const SkPoint>, const SkPaint&) override;
 *     void drawRect(const SkRect& r, const SkPaint& paint) override;
 *     void drawOval(const SkRect& oval, const SkPaint& paint) override;
 *     void drawRRect(const SkRRect& rr, const SkPaint& paint) override;
 *
 *     void drawPath(const SkPath&, const SkPaint&) override;
 *
 *     void drawImageRect(const SkImage*, const SkRect* src, const SkRect& dst,
 *                        const SkSamplingOptions&, const SkPaint&,
 *                        SkCanvas::SrcRectConstraint) override;
 *
 *     void drawVertices(const SkVertices*, sk_sp<SkBlender>, const SkPaint&, bool) override;
 *     // Implemented in src/sksl/SkBitmapDevice_mesh.cpp
 *     void drawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&) override;
 *
 *     void drawAtlas(SkSpan<const SkRSXform>, SkSpan<const SkRect>, SkSpan<const SkColor>,
 *                    sk_sp<SkBlender>, const SkPaint&) override;
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *
 *     void pushClipStack() override;
 *     void popClipStack() override;
 *     void clipRect(const SkRect& rect, SkClipOp, bool aa) override;
 *     void clipRRect(const SkRRect& rrect, SkClipOp, bool aa) override;
 *     void clipPath(const SkPath& path, SkClipOp, bool aa) override;
 *     void clipRegion(const SkRegion& deviceRgn, SkClipOp) override;
 *     void replaceClip(const SkIRect& rect) override;
 *     bool isClipAntiAliased() const override;
 *     bool isClipEmpty() const override;
 *     bool isClipRect() const override;
 *     bool isClipWideOpen() const override;
 *     void android_utils_clipAsRgn(SkRegion*) const override;
 *     SkIRect devClipBounds() const override;
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *
 *     void drawSpecial(SkSpecialImage*, const SkMatrix&, const SkSamplingOptions&,
 *                      const SkPaint&, SkCanvas::SrcRectConstraint) override;
 *
 *     void drawCoverageMask(const SkSpecialImage*, const SkMatrix&, const SkSamplingOptions&,
 *                           const SkPaint&) override;
 *
 *     bool drawBlurredRRect(const SkRRect&, const SkPaint&, float) override;
 *
 *     sk_sp<SkSpecialImage> snapSpecial(const SkIRect&, bool forceCopy = false) override;
 *
 *     sk_sp<SkDevice> createDevice(const CreateInfo&, const SkPaint*) override;
 *
 *     sk_sp<SkSurface> makeSurface(const SkImageInfo&, const SkSurfaceProps&) override;
 *
 *     void setImmutable() override { fBitmap.setImmutable(); }
 *
 *     void* getRasterHandle() const override { return fRasterHandle; }
 *
 *     SkRecorder* baseRecorder() const override { return fRecorder; }
 *
 * private:
 *     friend class SkDrawTiler;
 *     friend class SkSurface_Raster;
 *
 *     class BDDraw;
 *
 *     // Used to change the backend's pixels (and possibly config/rowbytes) but cannot change the
 *     // width/height, so there should be no change to any clip information.
 *     void replaceBitmapBackendForRasterSurface(const SkBitmap&);
 *
 *     void onClipShader(sk_sp<SkShader>) override;
 *
 *     void onDrawGlyphRunList(SkCanvas*, const sktext::GlyphRunList&, const SkPaint& paint) override;
 *
 *     bool onReadPixels(const SkPixmap&, int x, int y) override;
 *     bool onWritePixels(const SkPixmap&, int, int) override;
 *     bool onPeekPixels(SkPixmap*) override;
 *     bool onAccessPixels(SkPixmap*) override;
 *
 *     void drawBitmap(const SkBitmap&, const SkMatrix&, const SkRect* dstOrNull,
 *                     const SkSamplingOptions&, const SkPaint&);
 *
 *     void* fRasterHandle = nullptr;
 *     skcpu::RecorderImpl* fRecorder = nullptr;
 *     SkBitmap fBitmap;
 *     SkRasterClipStack fRCStack;
 *     skcpu::GlyphRunListPainter fGlyphPainter;
 * }
 * ```
 */
public class SkBitmapDevice public constructor(
  bitmap: SkBitmap,
) : SkDevice() {
  /**
   * C++ original:
   * ```cpp
   * void* fRasterHandle = nullptr
   * ```
   */
  private var fRasterHandle: Unit? = TODO("Initialize fRasterHandle")

  /**
   * C++ original:
   * ```cpp
   * skcpu::RecorderImpl* fRecorder = nullptr
   * ```
   */
  private var fRecorder: RecorderImpl? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * SkRasterClipStack fRCStack
   * ```
   */
  private var fRCStack: SkRasterClipStack = TODO("Initialize fRCStack")

  /**
   * C++ original:
   * ```cpp
   * skcpu::GlyphRunListPainter fGlyphPainter
   * ```
   */
  private var fGlyphPainter: GlyphRunListPainter = TODO("Initialize fGlyphPainter")

  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice::SkBitmapDevice(const SkBitmap& bitmap)
   *         : SkBitmapDevice(asRRI(skcpu::Recorder::TODO()), bitmap) {}
   * ```
   */
  public constructor(
    bitmap: SkBitmap,
    surfaceProps: SkSurfaceProps,
    externalHandle: Unit? = TODO(),
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice(const SkBitmap& bitmap, const SkSurfaceProps& surfaceProps,
   *                    void* externalHandle = nullptr)
   * ```
   */
  public constructor(recorder: RecorderImpl?, bitmap: SkBitmap) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice::SkBitmapDevice(skcpu::RecorderImpl* recorder, const SkBitmap& bitmap)
   *         : SkDevice(bitmap.info(), SkSurfaceProps())
   *         , fRecorder(recorder)
   *         , fBitmap(bitmap)
   *         , fRCStack(bitmap.width(), bitmap.height())
   *         , fGlyphPainter(this->surfaceProps(), bitmap.colorType(), bitmap.colorSpace()) {
   *     SkASSERT(valid_for_bitmap_device(bitmap.info(), nullptr));
   * }
   * ```
   */
  public constructor(
    param0: RecorderImpl,
    bitmap: SkBitmap,
    surfaceProps: SkSurfaceProps,
    externalHandle: Unit?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice(skcpu::RecorderImpl*,
   *                    const SkBitmap& bitmap,
   *                    const SkSurfaceProps& surfaceProps,
   *                    void* externalHandle = nullptr)
   * ```
   */
  public constructor(
    bitmap: SkBitmap,
    surfaceProps: SkSurfaceProps,
    hndl: SkRasterHandleAllocatorHandle,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice::SkBitmapDevice(const SkBitmap& bitmap,
   *                                const SkSurfaceProps& surfaceProps,
   *                                SkRasterHandleAllocator::Handle hndl)
   *         : SkBitmapDevice(asRRI(skcpu::Recorder::TODO()), bitmap, surfaceProps, hndl) {}
   * ```
   */
  public constructor(
    recorder: RecorderImpl?,
    bitmap: SkBitmap,
    surfaceProps: SkSurfaceProps,
    hndl: SkRasterHandleAllocatorHandle,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawPaint(const SkPaint& paint) {
   *     BDDraw(this).drawPaint(paint);
   * }
   * ```
   */
  public override fun drawPaint(paint: SkPaint) {
    TODO("Implement drawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawPoints(SkCanvas::PointMode mode, SkSpan<const SkPoint> pts,
   *                                 const SkPaint& paint) {
   *     LOOP_TILER( drawPoints(mode, pts, paint, nullptr), nullptr)
   * }
   * ```
   */
  public override fun drawPoints(
    mode: SkCanvas.PointMode,
    pts: SkSpan<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement drawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawRect(const SkRect& r, const SkPaint& paint) {
   *     LOOP_TILER( drawRect(r, paint), Bounder(r, paint))
   * }
   * ```
   */
  public override fun drawRect(r: SkRect, paint: SkPaint) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawOval(const SkRect& oval, const SkPaint& paint) {
   *     LOOP_TILER( drawOval(oval, paint), Bounder(oval, paint))
   * }
   * ```
   */
  public override fun drawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement drawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     LOOP_TILER( drawRRect(rrect, paint), Bounder(rrect.getBounds(), paint))
   * }
   * ```
   */
  public override fun drawRRect(rr: SkRRect, paint: SkPaint) {
    TODO("Implement drawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawPath(const SkPath& path, const SkPaint& paint) {
   *     const SkRect* bounds = nullptr;
   *     if (SkDrawTiler::NeedsTiling(this) && !path.isInverseFillType()) {
   *         bounds = &path.getBounds();
   *     }
   *     SkDrawTiler tiler(this, bounds ? Bounder(*bounds, paint).bounds() : nullptr);
   *     while (const skcpu::Draw* draw = tiler.next()) {
   *         draw->drawPath(path, paint, nullptr);
   *     }
   * }
   * ```
   */
  public override fun drawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement drawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawImageRect(const SkImage* image, const SkRect* src, const SkRect& dst,
   *                                    const SkSamplingOptions& sampling, const SkPaint& paint,
   *                                    SkCanvas::SrcRectConstraint constraint) {
   *     SkASSERT(dst.isFinite());
   *     SkASSERT(dst.isSorted());
   *
   *     SkBitmap bitmap;
   *     // TODO: Elevate direct context requirement to public API and remove cheat.
   *     auto dContext = as_IB(image)->directContext();
   *     if (!as_IB(image)->getROPixels(dContext, &bitmap)) {
   *         return;
   *     }
   *
   *     SkRect      bitmapBounds, tmpSrc, tmpDst;
   *     SkBitmap    tmpBitmap;
   *
   *     bitmapBounds.setIWH(bitmap.width(), bitmap.height());
   *
   *     // Compute matrix from the two rectangles
   *     if (src) {
   *         tmpSrc = *src;
   *     } else {
   *         tmpSrc = bitmapBounds;
   *     }
   *     SkMatrix matrix = SkMatrix::RectToRectOrIdentity(tmpSrc, dst);
   *
   *     const SkRect* dstPtr = &dst;
   *     const SkBitmap* bitmapPtr = &bitmap;
   *
   *     // clip the tmpSrc to the bounds of the bitmap, and recompute dstRect if
   *     // needed (if the src was clipped). No check needed if src==null.
   *     bool srcIsSubset = false;
   *     if (src) {
   *         if (!bitmapBounds.contains(*src)) {
   *             if (!tmpSrc.intersect(bitmapBounds)) {
   *                 return; // nothing to draw
   *             }
   *             // recompute dst, based on the smaller tmpSrc
   *             matrix.mapRect(&tmpDst, tmpSrc);
   *             if (!tmpDst.isFinite()) {
   *                 return;
   *             }
   *             dstPtr = &tmpDst;
   *         }
   *         srcIsSubset = !tmpSrc.contains(bitmapBounds);
   *     }
   *
   *     if (srcIsSubset &&
   *         SkCanvas::kFast_SrcRectConstraint == constraint &&
   *         sampling != SkSamplingOptions()) {
   *         // src is smaller than the bounds of the bitmap, and we are filtering, so we don't know
   *         // how much more of the bitmap we need, so we can't use extractSubset or drawBitmap,
   *         // but we must use a shader w/ dst bounds (which can access all of the bitmap needed).
   *         goto USE_SHADER;
   *     }
   *
   *     if (srcIsSubset) {
   *         // since we may need to clamp to the borders of the src rect within
   *         // the bitmap, we extract a subset.
   *         const SkIRect srcIR = tmpSrc.roundOut();
   *         if (!bitmap.extractSubset(&tmpBitmap, srcIR)) {
   *             return;
   *         }
   *         bitmapPtr = &tmpBitmap;
   *
   *         // Since we did an extract, we need to adjust the matrix accordingly
   *         SkScalar dx = 0, dy = 0;
   *         if (srcIR.fLeft > 0) {
   *             dx = SkIntToScalar(srcIR.fLeft);
   *         }
   *         if (srcIR.fTop > 0) {
   *             dy = SkIntToScalar(srcIR.fTop);
   *         }
   *         if (dx || dy) {
   *             matrix.preTranslate(dx, dy);
   *         }
   *
   * #ifdef SK_DRAWBITMAPRECT_FAST_OFFSET
   *         SkRect extractedBitmapBounds = SkRect::MakeXYWH(dx, dy,
   *                                                         SkIntToScalar(bitmapPtr->width()),
   *                                                         SkIntToScalar(bitmapPtr->height()));
   * #else
   *         SkRect extractedBitmapBounds;
   *         extractedBitmapBounds.setIWH(bitmapPtr->width(), bitmapPtr->height());
   * #endif
   *         if (extractedBitmapBounds == tmpSrc) {
   *             // no fractional part in src, we can just call drawBitmap
   *             goto USE_DRAWBITMAP;
   *         }
   *     } else {
   *         USE_DRAWBITMAP:
   *         // We can go faster by just calling drawBitmap, which will concat the
   *         // matrix with the CTM, and try to call drawSprite if it can. If not,
   *         // it will make a shader and call drawRect, as we do below.
   *         if (CanApplyDstMatrixAsCTM(matrix, paint)) {
   *             this->drawBitmap(*bitmapPtr, matrix, dstPtr, sampling, paint);
   *             return;
   *         }
   *     }
   *
   *     USE_SHADER:
   *
   *     // construct a shader, so we can call drawRect with the dst
   *     auto s = SkMakeBitmapShaderForPaint(paint, *bitmapPtr, SkTileMode::kClamp, SkTileMode::kClamp,
   *                                         sampling, &matrix, kNever_SkCopyPixelsMode);
   *     if (!s) {
   *         return;
   *     }
   *
   *     SkPaint paintWithShader(paint);
   *     paintWithShader.setStyle(SkPaint::kFill_Style);
   *     paintWithShader.setShader(std::move(s));
   *
   *     // Call ourself, in case the subclass wanted to share this setup code
   *     // but handle the drawRect code themselves.
   *     this->drawRect(*dstPtr, paintWithShader);
   * }
   * ```
   */
  public override fun drawImageRect(
    image: SkImage?,
    src: SkRect?,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint,
    constraint: SkCanvas.SrcRectConstraint,
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawVertices(const SkVertices* vertices,
   *                                   sk_sp<SkBlender> blender,
   *                                   const SkPaint& paint,
   *                                   bool skipColorXform) {
   * #ifdef SK_LEGACY_IGNORE_DRAW_VERTICES_BLEND_WITH_NO_SHADER
   *     if (!paint.getShader()) {
   *         blender = SkBlender::Mode(SkBlendMode::kDst);
   *     }
   * #endif
   *     BDDraw(this).drawVertices(vertices, std::move(blender), paint, skipColorXform);
   * }
   * ```
   */
  public override fun drawVertices(
    vertices: SkVertices?,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
    skipColorXform: Boolean,
  ) {
    TODO("Implement drawVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&) {
   *     // TODO: Implement, maybe with a subclass of BitmapDevice that has SkSL support.
   * }
   * ```
   */
  public override fun drawMesh(
    param0: SkMesh,
    param1: SkSp<SkBlender>,
    param2: SkPaint,
  ) {
    TODO("Implement drawMesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawAtlas(SkSpan<const SkRSXform> xform,
   *                                SkSpan<const SkRect> tex,
   *                                SkSpan<const SkColor> colors,
   *                                sk_sp<SkBlender> blender,
   *                                const SkPaint& paint) {
   *     BDDraw(this).drawAtlas(xform, tex, colors, std::move(blender), paint);
   * }
   * ```
   */
  public override fun drawAtlas(
    xform: SkSpan<SkRSXform>,
    tex: SkSpan<SkRect>,
    colors: SkSpan<SkColor>,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement drawAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::pushClipStack() {
   *     fRCStack.save();
   * }
   * ```
   */
  public override fun pushClipStack() {
    TODO("Implement pushClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::popClipStack() {
   *     fRCStack.restore();
   * }
   * ```
   */
  public override fun popClipStack() {
    TODO("Implement popClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::clipRect(const SkRect& rect, SkClipOp op, bool aa) {
   *     fRCStack.clipRect(this->localToDevice(), rect, op, aa);
   * }
   * ```
   */
  public override fun clipRect(
    rect: SkRect,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) {
   *     fRCStack.clipRRect(this->localToDevice(), rrect, op, aa);
   * }
   * ```
   */
  public override fun clipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::clipPath(const SkPath& path, SkClipOp op, bool aa) {
   *     fRCStack.clipPath(this->localToDevice(), path, op, aa);
   * }
   * ```
   */
  public override fun clipPath(
    path: SkPath,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::clipRegion(const SkRegion& rgn, SkClipOp op) {
   *     SkIPoint origin = this->getOrigin();
   *     SkRegion tmp;
   *     const SkRegion* ptr = &rgn;
   *     if (origin.fX | origin.fY) {
   *         // translate from "global/canvas" coordinates to relative to this device
   *         rgn.translate(-origin.fX, -origin.fY, &tmp);
   *         ptr = &tmp;
   *     }
   *     fRCStack.clipRegion(*ptr, op);
   * }
   * ```
   */
  public override fun clipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement clipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::replaceClip(const SkIRect& rect) {
   *     // Transform from "global/canvas" coordinates to relative to this device
   *     SkRect deviceRect = SkMatrixPriv::MapRect(this->globalToDevice(), SkRect::Make(rect));
   *     fRCStack.replaceClip(deviceRect.round());
   * }
   * ```
   */
  public override fun replaceClip(rect: SkIRect) {
    TODO("Implement replaceClip")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::isClipAntiAliased() const {
   *     const SkRasterClip& rc = fRCStack.rc();
   *     return !rc.isEmpty() && rc.isAA();
   * }
   * ```
   */
  public override fun isClipAntiAliased(): Boolean {
    TODO("Implement isClipAntiAliased")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::isClipEmpty() const {
   *     return fRCStack.rc().isEmpty();
   * }
   * ```
   */
  public override fun isClipEmpty(): Boolean {
    TODO("Implement isClipEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::isClipRect() const {
   *     const SkRasterClip& rc = fRCStack.rc();
   *     return !rc.isEmpty() && rc.isRect() && !SkToBool(rc.clipShader());
   * }
   * ```
   */
  public override fun isClipRect(): Boolean {
    TODO("Implement isClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::isClipWideOpen() const {
   *     const SkRasterClip& rc = fRCStack.rc();
   *     // If we're AA, we can't be wide-open (we would represent that as BW)
   *     return rc.isBW() && rc.bwRgn().isRect() &&
   *            rc.bwRgn().getBounds() == SkIRect{0, 0, this->width(), this->height()};
   * }
   * ```
   */
  public override fun isClipWideOpen(): Boolean {
    TODO("Implement isClipWideOpen")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::android_utils_clipAsRgn(SkRegion* rgn) const {
   *     const SkRasterClip& rc = fRCStack.rc();
   *     if (rc.isAA()) {
   *         rgn->setRect(   rc.getBounds());
   *     } else {
   *         *rgn = rc.bwRgn();
   *     }
   * }
   * ```
   */
  public override fun androidUtilsClipAsRgn(rgn: SkRegion?) {
    TODO("Implement androidUtilsClipAsRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect SkBitmapDevice::devClipBounds() const {
   *     return fRCStack.rc().getBounds();
   * }
   * ```
   */
  public override fun devClipBounds(): SkIRect {
    TODO("Implement devClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawSpecial(SkSpecialImage* src,
   *                                  const SkMatrix& localToDevice,
   *                                  const SkSamplingOptions& sampling,
   *                                  const SkPaint& paint,
   *                                  SkCanvas::SrcRectConstraint) {
   *     SkASSERT(!paint.getImageFilter());
   *     SkASSERT(!paint.getMaskFilter());
   *     SkASSERT(!src->isGaneshBacked());
   *     SkASSERT(!src->isGraphiteBacked());
   *
   *     SkBitmap resultBM;
   *     if (SkSpecialImages::AsBitmap(src, &resultBM)) {
   *         skcpu::Draw draw;
   *         if (!this->accessPixels(&draw.fDst)) {
   *           return; // no pixels to draw to so skip it
   *         }
   *         draw.fCTM = &localToDevice;
   *         draw.fRC = &fRCStack.rc();
   *         draw.drawBitmap(resultBM, SkMatrix::I(), nullptr, sampling, paint);
   *     }
   * }
   * ```
   */
  public override fun drawSpecial(
    src: SkSpecialImage?,
    localToDevice: SkMatrix,
    sampling: SkSamplingOptions,
    paint: SkPaint,
    param4: SkCanvas.SrcRectConstraint,
  ) {
    TODO("Implement drawSpecial")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawCoverageMask(const SkSpecialImage* mask,
   *                                       const SkMatrix& maskToDevice,
   *                                       const SkSamplingOptions& sampling,
   *                                       const SkPaint& paint) {
   *     SkASSERT(!mask->isGaneshBacked());
   *     SkASSERT(!mask->isGraphiteBacked());
   *
   *     SkBitmap maskBM;
   *     if (!SkSpecialImages::AsBitmap(mask, &maskBM)) {
   *         return;
   *     }
   *
   *     skcpu::Draw draw;
   *     if (!this->accessPixels(&draw.fDst)) {
   *       return; // no pixels to draw to so skip it
   *     }
   *     draw.fRC = &fRCStack.rc();
   *     draw.fCTM = &maskToDevice;
   *     draw.drawBitmapAsMask(maskBM, sampling, paint, &this->localToDevice());
   * }
   * ```
   */
  public override fun drawCoverageMask(
    mask: SkSpecialImage?,
    maskToDevice: SkMatrix,
    sampling: SkSamplingOptions,
    paint: SkPaint,
  ) {
    TODO("Implement drawCoverageMask")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::drawBlurredRRect(const SkRRect& rrect, const SkPaint& paint, float) {
   *     SkASSERT(paint.getMaskFilter()
   *              && as_MFB(paint.getMaskFilter())->type() == SkMaskFilterBase::Type::kBlur);
   *
   *     SkDrawTiler tiler(this, Bounder(rrect.getBounds(), paint));
   *     // Check if the tiler only needs one iteration (common case). If there are multiple
   *     // tiles, we just return false and fall back to the general mask filter path as we
   *     // don't want to be in the scenario where only a subset fail/succeed.
   *     if (!tiler.needsTiling()) {
   *         if (const skcpu::Draw* draw = tiler.next()) {
   *             return draw->drawRRectNinePatch(rrect, paint);
   *         }
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public override fun drawBlurredRRect(
    rrect: SkRRect,
    paint: SkPaint,
    param2: Float,
  ): Boolean {
    TODO("Implement drawBlurredRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkBitmapDevice::snapSpecial(const SkIRect& bounds, bool forceCopy) {
   *     if (forceCopy) {
   *         return SkSpecialImages::CopyFromRaster(bounds, fBitmap, this->surfaceProps());
   *     } else {
   *         return SkSpecialImages::MakeFromRaster(bounds, fBitmap, this->surfaceProps());
   *     }
   * }
   * ```
   */
  public override fun snapSpecial(bounds: SkIRect, forceCopy: Boolean = TODO()): SkSp<SkSpecialImage> {
    TODO("Implement snapSpecial")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> SkBitmapDevice::createDevice(const CreateInfo& cinfo, const SkPaint* layerPaint) {
   *     const SkSurfaceProps surfaceProps =
   *         this->surfaceProps().cloneWithPixelGeometry(cinfo.fPixelGeometry);
   *
   *     // Need to force L32 for now if we have an image filter.
   *     // If filters ever support other colortypes, e.g. F16, we can modify this check.
   *     SkImageInfo info = cinfo.fInfo;
   *     if (layerPaint && layerPaint->getImageFilter()) {
   *         // TODO: can we query the imagefilter, to see if it can handle floats (so we don't always
   *         //       use N32 when the layer itself was float)?
   *         info = info.makeColorType(kN32_SkColorType);
   *     }
   *
   *     return SkBitmapDevice::Create(info, surfaceProps, cinfo.fAllocator);
   * }
   * ```
   */
  public override fun createDevice(cinfo: CreateInfo, layerPaint: SkPaint?): SkSp<SkDevice> {
    TODO("Implement createDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkBitmapDevice::makeSurface(const SkImageInfo& info, const SkSurfaceProps& props) {
   *     return SkSurfaces::Raster(info, &props);
   * }
   * ```
   */
  public override fun makeSurface(info: SkImageInfo, props: SkSurfaceProps): SkSp<SkSurface> {
    TODO("Implement makeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * void setImmutable() override { fBitmap.setImmutable(); }
   * ```
   */
  public override fun setImmutable() {
    TODO("Implement setImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * void* getRasterHandle() const override { return fRasterHandle; }
   * ```
   */
  public override fun getRasterHandle() {
    TODO("Implement getRasterHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* baseRecorder() const override { return fRecorder; }
   * ```
   */
  public override fun baseRecorder(): SkRecorder {
    TODO("Implement baseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::replaceBitmapBackendForRasterSurface(const SkBitmap& bm) {
   *     SkASSERT(bm.width() == fBitmap.width());
   *     SkASSERT(bm.height() == fBitmap.height());
   *     fBitmap = bm;   // intent is to use bm's pixelRef (and rowbytes/config)
   * }
   * ```
   */
  private fun replaceBitmapBackendForRasterSurface(bm: SkBitmap) {
    TODO("Implement replaceBitmapBackendForRasterSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::onClipShader(sk_sp<SkShader> sh) {
   *     fRCStack.clipShader(std::move(sh));
   * }
   * ```
   */
  public override fun onClipShader(sh: SkSp<SkShader>) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::onDrawGlyphRunList(SkCanvas* canvas,
   *                                         const sktext::GlyphRunList& glyphRunList,
   *                                         const SkPaint& paint) {
   *     SkASSERT(!glyphRunList.hasRSXForm());
   *     LOOP_TILER( drawGlyphRunList(canvas, &fGlyphPainter, glyphRunList, paint), nullptr )
   * }
   * ```
   */
  public override fun onDrawGlyphRunList(
    canvas: SkCanvas?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::onReadPixels(const SkPixmap& pm, int x, int y) {
   *     return fBitmap.readPixels(pm, x, y);
   * }
   * ```
   */
  public override fun onReadPixels(
    pm: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement onReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::onWritePixels(const SkPixmap& pm, int x, int y) {
   *     // since we don't stop creating un-pixeled devices yet, check for no pixels here
   *     if (nullptr == fBitmap.getPixels()) {
   *         return false;
   *     }
   *
   *     if (fBitmap.writePixels(pm, x, y)) {
   *         fBitmap.notifyPixelsChanged();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onWritePixels(
    pm: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement onWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::onPeekPixels(SkPixmap* pmap) {
   *     const SkImageInfo info = fBitmap.info();
   *     if (fBitmap.getPixels() && (kUnknown_SkColorType != info.colorType())) {
   *         pmap->reset(fBitmap.info(), fBitmap.getPixels(), fBitmap.rowBytes());
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onPeekPixels(pmap: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapDevice::onAccessPixels(SkPixmap* pmap) {
   *     if (this->onPeekPixels(pmap)) {
   *         fBitmap.notifyPixelsChanged();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onAccessPixels(pmap: SkPixmap?): Boolean {
    TODO("Implement onAccessPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBitmapDevice::drawBitmap(const SkBitmap& bitmap, const SkMatrix& matrix,
   *                                 const SkRect* dstOrNull, const SkSamplingOptions& sampling,
   *                                 const SkPaint& paint) {
   *     const SkRect* bounds = dstOrNull;
   *     SkRect storage;
   *     if (!bounds && SkDrawTiler::NeedsTiling(this)) {
   *         matrix.mapRect(&storage, SkRect::MakeIWH(bitmap.width(), bitmap.height()));
   *         Bounder b(storage, paint);
   *         if (b.hasBounds()) {
   *             storage = *b.bounds();
   *             bounds = &storage;
   *         }
   *     }
   *     LOOP_TILER(drawBitmap(bitmap, matrix, dstOrNull, sampling, paint), bounds)
   * }
   * ```
   */
  private fun drawBitmap(
    bitmap: SkBitmap,
    matrix: SkMatrix,
    dstOrNull: SkRect?,
    sampling: SkSamplingOptions,
    paint: SkPaint,
  ) {
    TODO("Implement drawBitmap")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkBitmapDevice> SkBitmapDevice::Create(const SkImageInfo& origInfo,
     *                                              const SkSurfaceProps& surfaceProps,
     *                                              SkRasterHandleAllocator* allocator) {
     *     SkAlphaType newAT = origInfo.alphaType();
     *     if (!valid_for_bitmap_device(origInfo, &newAT)) {
     *         return nullptr;
     *     }
     *
     *     SkRasterHandleAllocator::Handle hndl = nullptr;
     *     const SkImageInfo info = origInfo.makeAlphaType(newAT);
     *     SkBitmap bitmap;
     *
     *     if (kUnknown_SkColorType == info.colorType()) {
     *         if (!bitmap.setInfo(info)) {
     *             return nullptr;
     *         }
     *     } else if (allocator) {
     *         hndl = allocator->allocBitmap(info, &bitmap);
     *         if (!hndl) {
     *             return nullptr;
     *         }
     *     } else if (info.isOpaque()) {
     *         // If this bitmap is opaque, we don't have any sensible default color,
     *         // so we just return uninitialized pixels.
     *         if (!bitmap.tryAllocPixels(info)) {
     *             return nullptr;
     *         }
     *     } else {
     *         // This bitmap has transparency, so we'll zero the pixels (to transparent).
     *         // We use the flag as a faster alloc-then-eraseColor(SK_ColorTRANSPARENT).
     *         if (!bitmap.tryAllocPixelsFlags(info, SkBitmap::kZeroPixels_AllocFlag)) {
     *             return nullptr;
     *         }
     *     }
     *
     *     return sk_make_sp<SkBitmapDevice>(bitmap, surfaceProps, hndl);
     * }
     * ```
     */
    public fun create(
      origInfo: SkImageInfo,
      surfaceProps: SkSurfaceProps,
      allocator: SkRasterHandleAllocator? = TODO(),
    ): SkSp<SkBitmapDevice> {
      TODO("Implement create")
    }
  }
}
