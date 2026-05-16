package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Path final : public GeometryNode {
 * public:
 *     static sk_sp<Path> Make()                { return sk_sp<Path>(new Path(SkPath())); }
 *     static sk_sp<Path> Make(const SkPath& r) { return sk_sp<Path>(new Path(r)); }
 *
 *     SG_ATTRIBUTE(Path, SkPath, fPath)
 *
 *     // Temporarily inlined for SkPathFillType staging
 *     // SG_MAPPED_ATTRIBUTE(FillType, SkPathFillType, fPath)
 *
 *     SkPathFillType getFillType() const {
 *         return fPath.getFillType();
 *     }
 *
 *     void setFillType(SkPathFillType fillType) {
 *         if (fillType != fPath.getFillType()) {
 *             fPath.setFillType(fillType);
 *             this->invalidate();
 *         }
 *     }
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
 *     explicit Path(const SkPath&);
 *
 *     SkPath fPath;
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public class Path public constructor(
  path: SkPath,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Path, SkPath, fPath)
   * ```
   */
  public fun sgATTRIBUTE(param0: Path, param1: SkPath): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void Path::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawPath(fPath, paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Path::onContains(const SkPoint& p) const {
   *     return fPath.contains(p.x(), p.y());
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Path::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     const auto ft = fPath.getFillType();
   *     return (ft == SkPathFillType::kWinding || ft == SkPathFillType::kEvenOdd)
   *         // "Containing" fills have finite bounds.
   *         ? fPath.computeTightBounds()
   *         // Inverse fills are "infinite".
   *         : SkRectPriv::MakeLargeS32();
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Path::onAsPath() const {
   *     return fPath;
   * }
   * ```
   */
  protected override fun onAsPath(): Path {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void Path::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipPath(fPath, SkClipOp::kIntersect, antiAlias);
   * }
   * ```
   */
  public override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Path> Make()                { return sk_sp<Path>(new Path(SkPath())); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Path> Make(const SkPath& r) { return sk_sp<Path>(new Path(r)); }
     * ```
     */
    public fun make(r: SkPath): Int {
      TODO("Implement make")
    }
  }
}
