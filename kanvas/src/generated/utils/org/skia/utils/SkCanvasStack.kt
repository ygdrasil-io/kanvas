package org.skia.utils

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkIPoint
import org.skia.math.SkM44
import org.skia.math.SkRect
import undefined.ClipEdgeStyle

/**
 * C++ original:
 * ```cpp
 * class SkCanvasStack : public SkNWayCanvas {
 * public:
 *     SkCanvasStack(int width, int height);
 *     ~SkCanvasStack() override;
 *
 *     void pushCanvas(std::unique_ptr<SkCanvas>, const SkIPoint& origin);
 *     void removeAll() override;
 *
 *     /*
 *      * The following add/remove canvas methods are overrides from SkNWayCanvas
 *      * that do not make sense in the context of our CanvasStack, but since we
 *      * can share most of the other implementation of NWay we override those
 *      * methods to be no-ops.
 *      */
 *     void addCanvas(SkCanvas*) override { SkDEBUGFAIL("Invalid Op"); }
 *     void removeCanvas(SkCanvas*) override { SkDEBUGFAIL("Invalid Op"); }
 *
 * protected:
 *     void didSetM44(const SkM44&) override;
 *
 *     void onClipRect(const SkRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipRRect(const SkRRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipPath(const SkPath&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipShader(sk_sp<SkShader>, SkClipOp) override;
 *     void onClipRegion(const SkRegion&, SkClipOp) override;
 *
 * private:
 *     void clipToZOrderedBounds();
 *
 *     struct CanvasData {
 *         SkIPoint origin;
 *         SkRegion requiredClip;
 *         std::unique_ptr<SkCanvas> ownedCanvas;
 *
 *         static_assert(::sk_is_trivially_relocatable<decltype(origin)>::value);
 *         static_assert(::sk_is_trivially_relocatable<decltype(requiredClip)>::value);
 *         static_assert(::sk_is_trivially_relocatable<decltype(ownedCanvas)>::value);
 *
 *         using sk_is_trivially_relocatable = std::true_type;
 *     };
 *
 *     skia_private::TArray<CanvasData> fCanvasData;
 *
 *     using INHERITED = SkNWayCanvas;
 * }
 * ```
 */
public open class SkCanvasStack public constructor(
  width: Int,
  height: Int,
) : SkNWayCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<CanvasData> fCanvasData
   * ```
   */
  private var fCanvasData: Int = TODO("Initialize fCanvasData")

  /**
   * C++ original:
   * ```cpp
   * void pushCanvas(std::unique_ptr<SkCanvas>, const SkIPoint& origin)
   * ```
   */
  public fun pushCanvas(param0: SkCanvas?, origin: SkIPoint) {
    TODO("Implement pushCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::removeAll() {
   *     this->INHERITED::removeAll();   // call the baseclass *before* we actually delete the canvases
   *     fCanvasData.clear();
   * }
   * ```
   */
  public override fun removeAll() {
    TODO("Implement removeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void addCanvas(SkCanvas*) override { SkDEBUGFAIL("Invalid Op"); }
   * ```
   */
  public override fun addCanvas(param0: SkCanvas?) {
    TODO("Implement addCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void removeCanvas(SkCanvas*) override { SkDEBUGFAIL("Invalid Op"); }
   * ```
   */
  public override fun removeCanvas(param0: SkCanvas?) {
    TODO("Implement removeCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::didSetM44(const SkM44& mx) {
   *     SkASSERT(fList.size() == fCanvasData.size());
   *     for (int i = 0; i < fList.size(); ++i) {
   *         fList[i]->setMatrix(SkM44::Translate(SkIntToScalar(-fCanvasData[i].origin.x()),
   *                                              SkIntToScalar(-fCanvasData[i].origin.y())) * mx);
   *     }
   *     this->SkCanvas::didSetM44(mx);
   * }
   * ```
   */
  protected override fun didSetM44(mx: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::onClipRect(const SkRect& r, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->INHERITED::onClipRect(r, op, edgeStyle);
   *     this->clipToZOrderedBounds();
   * }
   * ```
   */
  protected override fun onClipRect(
    r: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::onClipRRect(const SkRRect& rr, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->INHERITED::onClipRRect(rr, op, edgeStyle);
   *     this->clipToZOrderedBounds();
   * }
   * ```
   */
  protected override fun onClipRRect(
    rr: SkRRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::onClipPath(const SkPath& p, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->INHERITED::onClipPath(p, op, edgeStyle);
   *     this->clipToZOrderedBounds();
   * }
   * ```
   */
  protected override fun onClipPath(
    p: SkPath,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::onClipShader(sk_sp<SkShader> cs, SkClipOp op) {
   *     this->INHERITED::onClipShader(std::move(cs), op);
   *     // we don't change the "bounds" of the clip, so we don't need to update zorder
   * }
   * ```
   */
  protected override fun onClipShader(cs: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::onClipRegion(const SkRegion& deviceRgn, SkClipOp op) {
   *     SkASSERT(fList.size() == fCanvasData.size());
   *     for (int i = 0; i < fList.size(); ++i) {
   *         SkRegion tempRegion;
   *         deviceRgn.translate(-fCanvasData[i].origin.x(),
   *                             -fCanvasData[i].origin.y(), &tempRegion);
   *         tempRegion.op(fCanvasData[i].requiredClip, SkRegion::kIntersect_Op);
   *         fList[i]->clipRegion(tempRegion, op);
   *     }
   *     this->SkCanvas::onClipRegion(deviceRgn, op);
   * }
   * ```
   */
  protected override fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvasStack::clipToZOrderedBounds() {
   *     SkASSERT(fList.size() == fCanvasData.size());
   *     for (int i = 0; i < fList.size(); ++i) {
   *         fList[i]->clipRegion(fCanvasData[i].requiredClip);
   *     }
   * }
   * ```
   */
  private fun clipToZOrderedBounds() {
    TODO("Implement clipToZOrderedBounds")
  }

  public data class CanvasData public constructor(
    public var origin: SkIPoint,
    public var requiredClip: SkRegion,
    public var ownedCanvas: Int,
  )
}
