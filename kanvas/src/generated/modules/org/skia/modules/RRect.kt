package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class RRect final : public GeometryNode {
 * public:
 *     static sk_sp<RRect> Make()                  { return sk_sp<RRect>(new RRect(SkRRect())); }
 *     static sk_sp<RRect> Make(const SkRRect& rr) { return sk_sp<RRect>(new RRect(rr)); }
 *
 *     SG_ATTRIBUTE(RRect, SkRRect, fRRect)
 *
 *     SG_MAPPED_ATTRIBUTE(Direction        , SkPathDirection, fAttrContaier)
 *     SG_MAPPED_ATTRIBUTE(InitialPointIndex, uint8_t          , fAttrContaier)
 *
 * protected:
 *     void onClip(SkCanvas*, bool antiAlias) const override;
 *     void onDraw(SkCanvas*, const SkPaint&) const override;
 *     bool onContains(const SkPoint&)        const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *     SkPath onAsPath() const override;
 *
 * private:
 *     explicit RRect(const SkRRect&);
 *
 *     SkRRect fRRect;
 *
 *     struct AttrContainer {
 *         uint8_t fDirection         : 1;
 *         uint8_t fInitialPointIndex : 2;
 *
 *         SkPathDirection getDirection() const {
 *             return static_cast<SkPathDirection>(fDirection);
 *         }
 *         void setDirection(SkPathDirection dir) { fDirection = SkTo<uint8_t>(dir); }
 *
 *         uint8_t getInitialPointIndex() const { return fInitialPointIndex; }
 *         void setInitialPointIndex(uint8_t idx) { fInitialPointIndex = idx; }
 *     };
 *     AttrContainer fAttrContaier = { (int)SkPathDirection::kCW, 0 };
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public class RRect public constructor(
  rr: SkRRect,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * SkRRect fRRect
   * ```
   */
  private var fRRect: Int = TODO("Initialize fRRect")

  /**
   * C++ original:
   * ```cpp
   * AttrContainer fAttrContaier
   * ```
   */
  private var fAttrContaier: AttrContainer = TODO("Initialize fAttrContaier")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(RRect, SkRRect, fRRect)
   * ```
   */
  public fun sgATTRIBUTE(param0: RRect, param1: SkRRect): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void RRect::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawRRect(fRRect, paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RRect::onContains(const SkPoint& p) const {
   *     if (!fRRect.rect().contains(p.x(), p.y())) {
   *         return false;
   *     }
   *
   *     if (fRRect.isRect()) {
   *         return true;
   *     }
   *
   *     // TODO: no SkRRect::contains(x, y)
   *     return fRRect.contains(SkRect::MakeLTRB(p.x() - SK_ScalarNearlyZero,
   *                                             p.y() - SK_ScalarNearlyZero,
   *                                             p.x() + SK_ScalarNearlyZero,
   *                                             p.y() + SK_ScalarNearlyZero));
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect RRect::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     return fRRect.getBounds();
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath RRect::onAsPath() const {
   *     return SkPath::RRect(fRRect, this->getDirection(), this->getInitialPointIndex());
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void RRect::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipRRect(fRRect, SkClipOp::kIntersect, antiAlias);
   * }
   * ```
   */
  public override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  public data class AttrContainer public constructor(
    public var fDirection: UByte,
    public var fInitialPointIndex: UByte,
  ) {
    public fun getDirection(): Int {
      TODO("Implement getDirection")
    }

    public fun setDirection(dir: SkPathDirection) {
      TODO("Implement setDirection")
    }

    public fun getInitialPointIndex(): UByte {
      TODO("Implement getInitialPointIndex")
    }

    public fun setInitialPointIndex(idx: UByte) {
      TODO("Implement setInitialPointIndex")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RRect> Make()                  { return sk_sp<RRect>(new RRect(SkRRect())); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RRect> Make(const SkRRect& rr) { return sk_sp<RRect>(new RRect(rr)); }
     * ```
     */
    public fun make(rr: SkRRect): Int {
      TODO("Implement make")
    }
  }
}
