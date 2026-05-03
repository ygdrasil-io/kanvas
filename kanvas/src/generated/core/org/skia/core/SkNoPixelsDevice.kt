package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.utils.Slug

/**
 * C++ original:
 * ```cpp
 * class SkNoPixelsDevice : public SkDevice {
 * public:
 *     SkNoPixelsDevice(const SkIRect& bounds, const SkSurfaceProps& props);
 *     SkNoPixelsDevice(const SkIRect& bounds, const SkSurfaceProps& props,
 *                      sk_sp<SkColorSpace> colorSpace);
 *
 *     // Returns false if the device could not be reset; this should only be called on a root device.
 *     bool resetForNextPicture(const SkIRect& bounds);
 *
 *     // SkNoPixelsDevice tracks the clip conservatively in order to respond to some queries as
 *     // accurately as possible while emphasizing performance
 *     void pushClipStack() override;
 *     void popClipStack() override;
 *     void clipRect(const SkRect& rect, SkClipOp op, bool aa) override;
 *     void clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) override;
 *     void clipPath(const SkPath& path, SkClipOp op, bool aa) override;
 *     void clipRegion(const SkRegion& globalRgn, SkClipOp op) override;
 *     void replaceClip(const SkIRect& rect) override;
 *     bool isClipAntiAliased() const override { return this->clip().fIsAA; }
 *     bool isClipEmpty() const override { return this->devClipBounds().isEmpty(); }
 *     bool isClipRect() const override { return this->clip().fIsRect && !this->isClipEmpty(); }
 *     bool isClipWideOpen() const override {
 *         return this->clip().fIsRect &&
 *                this->devClipBounds() == this->bounds();
 *     }
 *     void android_utils_clipAsRgn(SkRegion* rgn) const override {
 *         rgn->setRect(this->devClipBounds());
 *     }
 *     SkIRect devClipBounds() const override { return this->clip().fClipBounds; }
 *
 * protected:
 *
 *     void drawPaint(const SkPaint& paint) override {}
 *     void drawPoints(SkCanvas::PointMode, SkSpan<const SkPoint>, const SkPaint&) override {}
 *     void drawImageRect(const SkImage*, const SkRect*, const SkRect&,
 *                        const SkSamplingOptions&, const SkPaint&,
 *                        SkCanvas::SrcRectConstraint) override {}
 *     void drawRect(const SkRect&, const SkPaint&) override {}
 *     void drawOval(const SkRect&, const SkPaint&) override {}
 *     void drawRRect(const SkRRect&, const SkPaint&) override {}
 *     void drawPath(const SkPath&, const SkPaint&) override {}
 *     void drawDevice(SkDevice*, const SkSamplingOptions&, const SkPaint&) override {}
 *     void drawVertices(const SkVertices*, sk_sp<SkBlender>, const SkPaint&, bool) override {}
 *     void drawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&) override {}
 *
 *     void drawSlug(SkCanvas*, const sktext::gpu::Slug*, const SkPaint&) override {}
 *     void onDrawGlyphRunList(SkCanvas*, const sktext::GlyphRunList&, const SkPaint&) override {}
 *
 *     bool isNoPixelsDevice() const override { return true; }
 *
 * private:
 *     struct ClipState {
 *         SkIRect fClipBounds;
 *         int fDeferredSaveCount;
 *         bool fIsAA;
 *         bool fIsRect;
 *
 *         ClipState(const SkIRect& bounds, bool isAA, bool isRect)
 *                 : fClipBounds(bounds)
 *                 , fDeferredSaveCount(0)
 *                 , fIsAA(isAA)
 *                 , fIsRect(isRect) {}
 *
 *         void op(SkClipOp op, const SkM44& transform, const SkRect& bounds,
 *                 bool isAA, bool fillsBounds);
 *     };
 *
 *     void onClipShader(sk_sp<SkShader> shader) override;
 *
 *     const ClipState& clip() const { return fClipStack.back(); }
 *     ClipState& writableClip();
 *
 *     skia_private::STArray<4, ClipState> fClipStack;
 * }
 * ```
 */
public open class SkNoPixelsDevice public constructor(
  bounds: SkIRect,
  props: SkSurfaceProps,
) : SkDevice() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<4, ClipState> fClipStack
   * ```
   */
  private var fClipStack: Int = TODO("Initialize fClipStack")

  /**
   * C++ original:
   * ```cpp
   * SkNoPixelsDevice::SkNoPixelsDevice(const SkIRect& bounds, const SkSurfaceProps& props)
   *     : SkNoPixelsDevice(bounds, props, nullptr) {}
   * ```
   */
  public constructor(
    bounds: SkIRect,
    props: SkSurfaceProps,
    colorSpace: SkSp<SkColorSpace>,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkNoPixelsDevice::resetForNextPicture(const SkIRect& bounds) {
   *     // Resetting should only happen on the root SkNoPixelsDevice, so its device-to-global
   *     // transform should be pixel aligned.
   *     SkASSERT(this->isPixelAlignedToGlobal());
   *     // We can only reset the device as long as its dimensions are not changing.
   *     if (bounds.width() != this->width() || bounds.height() != this->height()) {
   *         return false;
   *     }
   *
   *     // And the canvas should have restored back to the original save count.
   *     SkASSERT(fClipStack.size() == 1 && fClipStack[0].fDeferredSaveCount == 0);
   *     // But in the event that the clip was modified w/o a save(), reset the tracking state
   *     fClipStack[0].fClipBounds = this->bounds();
   *     fClipStack[0].fIsAA = false;
   *     fClipStack[0].fIsRect = true;
   *
   *     this->setOrigin(SkM44(), bounds.left(), bounds.top());
   *     return true;
   * }
   * ```
   */
  public fun resetForNextPicture(bounds: SkIRect): Boolean {
    TODO("Implement resetForNextPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNoPixelsDevice::pushClipStack() {
   *     SkASSERT(!fClipStack.empty());
   *     fClipStack.back().fDeferredSaveCount++;
   * }
   * ```
   */
  public override fun pushClipStack() {
    TODO("Implement pushClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNoPixelsDevice::popClipStack() {
   *     SkASSERT(!fClipStack.empty());
   *     if (fClipStack.back().fDeferredSaveCount > 0) {
   *         fClipStack.back().fDeferredSaveCount--;
   *     } else {
   *         fClipStack.pop_back();
   *         SkASSERT(!fClipStack.empty());
   *     }
   * }
   * ```
   */
  public override fun popClipStack() {
    TODO("Implement popClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNoPixelsDevice::clipRect(const SkRect& rect, SkClipOp op, bool aa) {
   *     this->writableClip().op(op, this->localToDevice44(), rect,
   *                             aa, /*fillsBounds=*/true);
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
   * void SkNoPixelsDevice::clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) {
   *     this->writableClip().op(op, this->localToDevice44(), rrect.getBounds(),
   *                             aa, /*fillsBounds=*/rrect.isRect());
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
   * void SkNoPixelsDevice::clipPath(const SkPath& path, SkClipOp op, bool aa) {
   *     // Toggle op if the path is inverse filled
   *     if (path.isInverseFillType()) {
   *         op = (op == SkClipOp::kDifference ? SkClipOp::kIntersect : SkClipOp::kDifference);
   *     }
   *     this->writableClip().op(op, this->localToDevice44(), path.getBounds(),
   *                             aa, /*fillsBounds=*/false);
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
   * void SkNoPixelsDevice::clipRegion(const SkRegion& globalRgn, SkClipOp op) {
   *     this->writableClip().op(op, this->globalToDevice(), SkRect::Make(globalRgn.getBounds()),
   *                             /*isAA=*/false, /*fillsBounds=*/globalRgn.isRect());
   * }
   * ```
   */
  public override fun clipRegion(globalRgn: SkRegion, op: SkClipOp) {
    TODO("Implement clipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNoPixelsDevice::replaceClip(const SkIRect& rect) {
   *     SkIRect deviceRect = SkMatrixPriv::MapRect(this->globalToDevice(), SkRect::Make(rect)).round();
   *     if (!deviceRect.intersect(this->bounds())) {
   *         deviceRect.setEmpty();
   *     }
   *     auto& clip = this->writableClip();
   *     clip.fClipBounds = deviceRect;
   *     clip.fIsRect = true;
   *     clip.fIsAA = false;
   * }
   * ```
   */
  public override fun replaceClip(rect: SkIRect) {
    TODO("Implement replaceClip")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClipAntiAliased() const override { return this->clip().fIsAA; }
   * ```
   */
  public override fun isClipAntiAliased(): Boolean {
    TODO("Implement isClipAntiAliased")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClipEmpty() const override { return this->devClipBounds().isEmpty(); }
   * ```
   */
  public override fun isClipEmpty(): Boolean {
    TODO("Implement isClipEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClipRect() const override { return this->clip().fIsRect && !this->isClipEmpty(); }
   * ```
   */
  public override fun isClipRect(): Boolean {
    TODO("Implement isClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClipWideOpen() const override {
   *         return this->clip().fIsRect &&
   *                this->devClipBounds() == this->bounds();
   *     }
   * ```
   */
  public override fun isClipWideOpen(): Boolean {
    TODO("Implement isClipWideOpen")
  }

  /**
   * C++ original:
   * ```cpp
   * void android_utils_clipAsRgn(SkRegion* rgn) const override {
   *         rgn->setRect(this->devClipBounds());
   *     }
   * ```
   */
  public override fun androidUtilsClipAsRgn(rgn: SkRegion?) {
    TODO("Implement androidUtilsClipAsRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect devClipBounds() const override { return this->clip().fClipBounds; }
   * ```
   */
  public override fun devClipBounds(): SkIRect {
    TODO("Implement devClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPaint(const SkPaint& paint) override {}
   * ```
   */
  protected override fun drawPaint(paint: SkPaint) {
    TODO("Implement drawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPoints(SkCanvas::PointMode, SkSpan<const SkPoint>, const SkPaint&) override {}
   * ```
   */
  protected override fun drawPoints(
    param0: SkCanvas.PointMode,
    param1: SkSpan<SkPoint>,
    param2: SkPaint,
  ) {
    TODO("Implement drawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageRect(const SkImage*, const SkRect*, const SkRect&,
   *                        const SkSamplingOptions&, const SkPaint&,
   *                        SkCanvas::SrcRectConstraint) override {}
   * ```
   */
  protected override fun drawImageRect(
    param0: SkImage?,
    param1: SkRect?,
    param2: SkRect,
    param3: SkSamplingOptions,
    param4: SkPaint,
    param5: SkCanvas.SrcRectConstraint,
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRect(const SkRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun drawRect(param0: SkRect, param1: SkPaint) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawOval(const SkRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun drawOval(param0: SkRect, param1: SkPaint) {
    TODO("Implement drawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRRect(const SkRRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun drawRRect(param0: SkRRect, param1: SkPaint) {
    TODO("Implement drawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPath(const SkPath&, const SkPaint&) override {}
   * ```
   */
  protected override fun drawPath(param0: SkPath, param1: SkPaint) {
    TODO("Implement drawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawDevice(SkDevice*, const SkSamplingOptions&, const SkPaint&) override {}
   * ```
   */
  protected override fun drawDevice(
    param0: SkDevice?,
    param1: SkSamplingOptions,
    param2: SkPaint,
  ) {
    TODO("Implement drawDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawVertices(const SkVertices*, sk_sp<SkBlender>, const SkPaint&, bool) override {}
   * ```
   */
  protected override fun drawVertices(
    param0: SkVertices?,
    param1: SkSp<SkBlender>,
    param2: SkPaint,
    param3: Boolean,
  ) {
    TODO("Implement drawVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&) override {}
   * ```
   */
  protected override fun drawMesh(
    param0: SkMesh,
    param1: SkSp<SkBlender>,
    param2: SkPaint,
  ) {
    TODO("Implement drawMesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawSlug(SkCanvas*, const sktext::gpu::Slug*, const SkPaint&) override {}
   * ```
   */
  protected override fun drawSlug(
    param0: SkCanvas?,
    param1: Slug?,
    param2: SkPaint,
  ) {
    TODO("Implement drawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawGlyphRunList(SkCanvas*, const sktext::GlyphRunList&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawGlyphRunList(
    param0: SkCanvas?,
    param1: GlyphRunList,
    param2: SkPaint,
  ) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isNoPixelsDevice() const override { return true; }
   * ```
   */
  protected override fun isNoPixelsDevice(): Boolean {
    TODO("Implement isNoPixelsDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkNoPixelsDevice::onClipShader(sk_sp<SkShader> shader) {
   *     this->writableClip().fIsRect = false;
   * }
   * ```
   */
  public override fun onClipShader(shader: SkSp<SkShader>) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * const ClipState& clip() const { return fClipStack.back(); }
   * ```
   */
  private fun clip(): ClipState {
    TODO("Implement clip")
  }

  /**
   * C++ original:
   * ```cpp
   * SkNoPixelsDevice::ClipState& SkNoPixelsDevice::writableClip() {
   *     SkASSERT(!fClipStack.empty());
   *     ClipState& current = fClipStack.back();
   *     if (current.fDeferredSaveCount > 0) {
   *         current.fDeferredSaveCount--;
   *         // Stash current state in case 'current' moves during a resize
   *         SkIRect bounds = current.fClipBounds;
   *         bool aa = current.fIsAA;
   *         bool rect = current.fIsRect;
   *         return fClipStack.emplace_back(bounds, aa, rect);
   *     } else {
   *         return current;
   *     }
   * }
   * ```
   */
  private fun writableClip(): ClipState {
    TODO("Implement writableClip")
  }

  public data class ClipState public constructor(
    public var fClipBounds: SkIRect,
    public var fDeferredSaveCount: Int,
    public var fIsAA: Boolean,
    public var fIsRect: Boolean,
  ) {
    public fun op(
      op: SkClipOp,
      transform: SkM44,
      bounds: SkRect,
      isAA: Boolean,
      fillsBounds: Boolean,
    ) {
      TODO("Implement op")
    }
  }
}
