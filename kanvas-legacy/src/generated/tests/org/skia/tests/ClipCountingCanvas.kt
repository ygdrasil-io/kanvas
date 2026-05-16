package org.skia.tests

import kotlin.Int
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.math.SkRect
import undefined.ClipEdgeStyle

/**
 * C++ original:
 * ```cpp
 * class ClipCountingCanvas : public SkCanvas {
 * public:
 *     ClipCountingCanvas(int width, int height)
 *         : INHERITED(width, height)
 *         , fClipCount(0){
 *     }
 *
 *     void onClipRect(const SkRect& r, SkClipOp op, ClipEdgeStyle edgeStyle) override {
 *         fClipCount += 1;
 *         this->INHERITED::onClipRect(r, op, edgeStyle);
 *     }
 *
 *     void onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle)override {
 *         fClipCount += 1;
 *         this->INHERITED::onClipRRect(rrect, op, edgeStyle);
 *     }
 *
 *     void onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) override {
 *         fClipCount += 1;
 *         this->INHERITED::onClipPath(path, op, edgeStyle);
 *     }
 *
 *     void onClipRegion(const SkRegion& deviceRgn, SkClipOp op) override {
 *         fClipCount += 1;
 *         this->INHERITED::onClipRegion(deviceRgn, op);
 *     }
 *
 *     unsigned getClipCount() const { return fClipCount; }
 *
 * private:
 *     unsigned fClipCount;
 *
 *     using INHERITED = SkCanvas;
 * }
 * ```
 */
public open class ClipCountingCanvas public constructor(
  width: Int,
  height: Int,
) : SkCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * unsigned fClipCount
   * ```
   */
  private var fClipCount: UInt = TODO("Initialize fClipCount")

  /**
   * C++ original:
   * ```cpp
   * void onClipRect(const SkRect& r, SkClipOp op, ClipEdgeStyle edgeStyle) override {
   *         fClipCount += 1;
   *         this->INHERITED::onClipRect(r, op, edgeStyle);
   *     }
   * ```
   */
  public override fun onClipRect(
    r: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle)override {
   *         fClipCount += 1;
   *         this->INHERITED::onClipRRect(rrect, op, edgeStyle);
   *     }
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
   * void onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) override {
   *         fClipCount += 1;
   *         this->INHERITED::onClipPath(path, op, edgeStyle);
   *     }
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
   * void onClipRegion(const SkRegion& deviceRgn, SkClipOp op) override {
   *         fClipCount += 1;
   *         this->INHERITED::onClipRegion(deviceRgn, op);
   *     }
   * ```
   */
  public override fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned getClipCount() const { return fClipCount; }
   * ```
   */
  public fun getClipCount(): UInt {
    TODO("Implement getClipCount")
  }
}
