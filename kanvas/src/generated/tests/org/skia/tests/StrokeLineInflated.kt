package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class StrokeLineInflated : public SkPathEffectBase {
 * public:
 *     StrokeLineInflated(float strokeWidth, float pxInflate)
 *             : fRadius(strokeWidth / 2.f), fPxInflate(pxInflate) {}
 *
 *     bool onNeedsCTM() const final { return true; }
 *
 *     bool onFilterPath(SkPathBuilder* dst,
 *                       const SkPath& src,
 *                       SkStrokeRec* rec,
 *                       const SkRect* cullR,
 *                       const SkMatrix& ctm) const final {
 *         SkSpan<const SkPoint> pts = src.points();
 *         SkASSERT(pts.size() == 2);
 *
 *         SkMatrix invCtm;
 *         if (!ctm.invert(&invCtm)) {
 *             return false;
 *         }
 *
 *         // For a line segment, we can just map the (scaled) normal vector to pixel-space,
 *         // increase its length by the desired number of pixels, and then map back to canvas space.
 *         SkPoint n = {pts[0].fY - pts[1].fY, pts[1].fX - pts[0].fX};
 *         if (!n.setLength(fRadius)) {
 *             return false;
 *         }
 *
 *         SkPoint mappedN = ctm.mapVector(n.fX, n.fY);
 *         if (!mappedN.setLength(mappedN.length() + fPxInflate)) {
 *             return false;
 *         }
 *         n = invCtm.mapVector(mappedN.fX, mappedN.fY);
 *
 *         dst->moveTo(pts[0] + n);
 *         dst->lineTo(pts[1] + n);
 *         dst->lineTo(pts[1] - n);
 *         dst->lineTo(pts[0] - n);
 *         dst->close();
 *
 *         rec->setFillStyle();
 *
 *         return true;
 *     }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const final {}
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(StrokeLineInflated)
 *
 *     bool computeFastBounds(SkRect* bounds) const final { return false; }
 *
 *     const float fRadius;
 *     const float fPxInflate;
 * }
 * ```
 */
public open class StrokeLineInflated public constructor(
  strokeWidth: Float,
  pxInflate: Float,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * const float fRadius
   * ```
   */
  private val fRadius: Float = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * const float fPxInflate
   * ```
   */
  private val fPxInflate: Float = TODO("Initialize fPxInflate")

  /**
   * C++ original:
   * ```cpp
   * bool onNeedsCTM() const final { return true; }
   * ```
   */
  public override fun onNeedsCTM(): Boolean {
    TODO("Implement onNeedsCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* dst,
   *                       const SkPath& src,
   *                       SkStrokeRec* rec,
   *                       const SkRect* cullR,
   *                       const SkMatrix& ctm) const final {
   *         SkSpan<const SkPoint> pts = src.points();
   *         SkASSERT(pts.size() == 2);
   *
   *         SkMatrix invCtm;
   *         if (!ctm.invert(&invCtm)) {
   *             return false;
   *         }
   *
   *         // For a line segment, we can just map the (scaled) normal vector to pixel-space,
   *         // increase its length by the desired number of pixels, and then map back to canvas space.
   *         SkPoint n = {pts[0].fY - pts[1].fY, pts[1].fX - pts[0].fX};
   *         if (!n.setLength(fRadius)) {
   *             return false;
   *         }
   *
   *         SkPoint mappedN = ctm.mapVector(n.fX, n.fY);
   *         if (!mappedN.setLength(mappedN.length() + fPxInflate)) {
   *             return false;
   *         }
   *         n = invCtm.mapVector(mappedN.fX, mappedN.fY);
   *
   *         dst->moveTo(pts[0] + n);
   *         dst->lineTo(pts[1] + n);
   *         dst->lineTo(pts[1] - n);
   *         dst->lineTo(pts[0] - n);
   *         dst->close();
   *
   *         rec->setFillStyle();
   *
   *         return true;
   *     }
   * ```
   */
  public override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
    cullR: SkRect?,
    ctm: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer&) const final {}
   * ```
   */
  protected fun flatten(param0: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect* bounds) const final { return false; }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> StrokeLineInflated::CreateProc(SkReadBuffer&) { return nullptr; }
   * ```
   */
  public fun createProc(param0: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
