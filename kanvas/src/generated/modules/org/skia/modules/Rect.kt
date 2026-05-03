package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class Rect final : public GeometryNode {
 * public:
 *     static sk_sp<Rect> Make()                { return sk_sp<Rect>(new Rect(SkRect::MakeEmpty())); }
 *     static sk_sp<Rect> Make(const SkRect& r) { return sk_sp<Rect>(new Rect(r)); }
 *
 *     SG_ATTRIBUTE(L, SkScalar, fRect.fLeft  )
 *     SG_ATTRIBUTE(T, SkScalar, fRect.fTop   )
 *     SG_ATTRIBUTE(R, SkScalar, fRect.fRight )
 *     SG_ATTRIBUTE(B, SkScalar, fRect.fBottom)
 *
 *     SG_MAPPED_ATTRIBUTE(Direction        , SkPathDirection, fAttrContaier)
 *     SG_MAPPED_ATTRIBUTE(InitialPointIndex, uint8_t        , fAttrContaier)
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
 *     explicit Rect(const SkRect&);
 *
 *     SkRect   fRect;
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
public class Rect public constructor(
  rect: SkRect,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * SkRect   fRect
   * ```
   */
  private var fRect: Rect = TODO("Initialize fRect")

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
   * SG_ATTRIBUTE(L, SkScalar, fRect.fLeft  )
   * ```
   */
  public fun sgATTRIBUTE(param0: L, param1: SkScalar): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void Rect::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawRect(fRect, paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Rect::onContains(const SkPoint& p) const {
   *     return fRect.contains(p.x(), p.y());
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Rect::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     return fRect;
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Rect {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Rect::onAsPath() const {
   *     return SkPath::Rect(fRect, this->getDirection(), this->getInitialPointIndex());
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void Rect::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipRect(fRect, SkClipOp::kIntersect, antiAlias);
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
     * static sk_sp<Rect> Make()                { return sk_sp<Rect>(new Rect(SkRect::MakeEmpty())); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Rect> Make(const SkRect& r) { return sk_sp<Rect>(new Rect(r)); }
     * ```
     */
    public fun make(r: SkRect): Int {
      TODO("Implement make")
    }
  }
}
