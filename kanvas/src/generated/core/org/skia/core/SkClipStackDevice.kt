package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.LongArray
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkClipStackDevice : public SkDevice {
 * public:
 *     SkClipStackDevice(const SkImageInfo& info, const SkSurfaceProps& props)
 *         : SkDevice(info, props)
 *         , fClipStack(fStorage, sizeof(fStorage))
 *     {}
 *
 *     SkClipStack& cs() { return fClipStack; }
 *     const SkClipStack& cs() const { return fClipStack; }
 *
 *     void pushClipStack() override;
 *     void popClipStack() override;
 *
 *     void clipRect(const SkRect& rect, SkClipOp, bool aa) override;
 *     void clipRRect(const SkRRect& rrect, SkClipOp, bool aa) override;
 *     void clipPath(const SkPath& path, SkClipOp, bool aa) override;
 *     void clipRegion(const SkRegion& deviceRgn, SkClipOp) override;
 *
 *     void replaceClip(const SkIRect& rect) override;
 *
 *     bool isClipAntiAliased() const override;
 *     bool isClipWideOpen() const override;
 *     bool isClipEmpty() const override;
 *     bool isClipRect() const override;
 *
 *     void android_utils_clipAsRgn(SkRegion*) const override;
 *
 *     SkIRect devClipBounds() const override;
 *
 * private:
 *     // empirically determined, adjust as needed to reduce mallocs
 *     static constexpr int kPreallocCount = 16;
 *
 *     void onClipShader(sk_sp<SkShader>) override;
 *
 *     intptr_t fStorage[kPreallocCount * sizeof(SkClipStack::Element) / sizeof(intptr_t)];
 *     SkClipStack fClipStack;
 * }
 * ```
 */
public open class SkClipStackDevice public constructor(
  info: SkImageInfo,
  props: SkSurfaceProps,
) : SkDevice(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kPreallocCount = 16
   * ```
   */
  private var fStorage: LongArray = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * intptr_t fStorage[kPreallocCount * sizeof(SkClipStack::Element) / sizeof(intptr_t)]
   * ```
   */
  private var fClipStack: SkClipStack = TODO("Initialize fClipStack")

  /**
   * C++ original:
   * ```cpp
   * SkClipStack& cs() { return fClipStack; }
   * ```
   */
  public fun cs(): SkClipStack {
    TODO("Implement cs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkClipStack& cs() const { return fClipStack; }
   * ```
   */
  public override fun pushClipStack() {
    TODO("Implement pushClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStackDevice::pushClipStack() {
   *     fClipStack.save();
   * }
   * ```
   */
  public override fun popClipStack() {
    TODO("Implement popClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStackDevice::popClipStack() {
   *     fClipStack.restore();
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
   * void SkClipStackDevice::clipRect(const SkRect& rect, SkClipOp op, bool aa) {
   *     fClipStack.clipRect(rect, this->localToDevice(), op, aa);
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
   * void SkClipStackDevice::clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) {
   *     fClipStack.clipRRect(rrect, this->localToDevice(), op, aa);
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
   * void SkClipStackDevice::clipPath(const SkPath& path, SkClipOp op, bool aa) {
   *     fClipStack.clipPath(path, this->localToDevice(), op, aa);
   * }
   * ```
   */
  public override fun clipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement clipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStackDevice::clipRegion(const SkRegion& rgn, SkClipOp op) {
   *     SkIPoint origin = this->getOrigin();
   *     SkRegion tmp;
   *     SkPathBuilder builder;
   *     rgn.addBoundaryPath(&builder);
   *     builder.transform(SkMatrix::Translate(-origin));
   *     fClipStack.clipPath(builder.detach(), SkMatrix::I(), op, false);
   * }
   * ```
   */
  public override fun replaceClip(rect: SkIRect) {
    TODO("Implement replaceClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStackDevice::replaceClip(const SkIRect& rect) {
   *     SkRect deviceRect = SkMatrixPriv::MapRect(this->globalToDevice(), SkRect::Make(rect));
   *     fClipStack.replaceClip(deviceRect, /*doAA=*/false);
   * }
   * ```
   */
  public override fun isClipAntiAliased(): Boolean {
    TODO("Implement isClipAntiAliased")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStackDevice::isClipAntiAliased() const {
   *     SkClipStack::B2TIter        iter(fClipStack);
   *     const SkClipStack::Element* element;
   *
   *     while ((element = iter.next()) != nullptr) {
   *         if (element->isAA()) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun isClipWideOpen(): Boolean {
    TODO("Implement isClipWideOpen")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStackDevice::isClipWideOpen() const {
   *     return fClipStack.quickContains(SkRect::MakeIWH(this->width(), this->height()));
   * }
   * ```
   */
  public override fun isClipEmpty(): Boolean {
    TODO("Implement isClipEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStackDevice::isClipEmpty() const {
   *     return fClipStack.isEmpty(SkIRect::MakeWH(this->width(), this->height()));
   * }
   * ```
   */
  public override fun isClipRect(): Boolean {
    TODO("Implement isClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStackDevice::isClipRect() const {
   *     if (this->isClipWideOpen()) {
   *         return true;
   *     } else if (this->isClipEmpty()) {
   *         return false;
   *     }
   *
   *     SkClipStack::BoundsType boundType;
   *     bool isIntersectionOfRects;
   *     SkRect bounds;
   *     fClipStack.getBounds(&bounds, &boundType, &isIntersectionOfRects);
   *     return isIntersectionOfRects && boundType == SkClipStack::kNormal_BoundsType;
   * }
   * ```
   */
  public override fun androidUtilsClipAsRgn(rgn: SkRegion?) {
    TODO("Implement androidUtilsClipAsRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStackDevice::android_utils_clipAsRgn(SkRegion* rgn) const {
   *     SkClipStack::BoundsType boundType;
   *     bool isIntersectionOfRects;
   *     SkRect bounds;
   *     fClipStack.getBounds(&bounds, &boundType, &isIntersectionOfRects);
   *     if (isIntersectionOfRects && SkClipStack::kNormal_BoundsType == boundType) {
   *         rgn->setRect(bounds.round());
   *     } else {
   *         SkRegion boundsRgn({0, 0, this->width(), this->height()});
   *
   *         *rgn = boundsRgn;
   *         SkClipStack::B2TIter iter(fClipStack);
   *         while (auto elem = iter.next()) {
   *             SkPath tmpPath = elem->asDeviceSpacePath();
   *             SkRegion tmpRgn;
   *             tmpRgn.setPath(tmpPath, boundsRgn);
   *             if (elem->isReplaceOp()) {
   *                 // All replace elements are rectangles
   *                 // TODO: SkClipStack can be simplified to be I,D,R ops now, which means element
   *                 // iteration can be from top of the stack to the most recent replace element.
   *                 // When that's done, this loop will be simplifiable.
   *                 rgn->setRect(elem->getDeviceSpaceRect().round());
   *             } else {
   *                 rgn->op(tmpRgn, static_cast<SkRegion::Op>(elem->getOp()));
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public override fun devClipBounds(): SkIRect {
    TODO("Implement devClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect SkClipStackDevice::devClipBounds() const {
   *     SkIRect r = fClipStack.bounds(this->imageInfo().bounds()).roundOut();
   *     if (!r.isEmpty()) {
   *         SkASSERT(this->imageInfo().bounds().contains(r));
   *     }
   *     return r;
   * }
   * ```
   */
  public override fun onClipShader(shader: SkSp<SkShader>) {
    TODO("Implement onClipShader")
  }

  public companion object {
    private val kPreallocCount: Int = TODO("Initialize kPreallocCount")
  }
}
