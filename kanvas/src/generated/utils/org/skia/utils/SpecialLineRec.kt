package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkPath
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SpecialLineRec {
 * public:
 *     bool init(const SkPath& src, SkPathBuilder* dst, SkStrokeRec* rec,
 *               int intervalCount, SkScalar intervalLength) {
 *         if (rec->isHairlineStyle() || !src.isLine(fPts)) {
 *             return false;
 *         }
 *
 *         // can relax this in the future, if we handle square and round caps
 *         if (SkPaint::kButt_Cap != rec->getCap()) {
 *             return false;
 *         }
 *
 *         SkScalar pathLength = SkPoint::Distance(fPts[0], fPts[1]);
 *
 *         fTangent = fPts[1] - fPts[0];
 *         if (fTangent.isZero()) {
 *             return false;
 *         }
 *
 *         fPathLength = pathLength;
 *         fTangent.scale(sk_ieee_float_divide(1.0f, pathLength));
 *         if (!SkIsFinite(fTangent.fX, fTangent.fY)) {
 *             return false;
 *         }
 *         SkPointPriv::RotateCCW(fTangent, &fNormal);
 *         fNormal.scale(SkScalarHalf(rec->getWidth()));
 *
 *         // now estimate how many quads will be added to the path
 *         //     resulting segments = pathLen * intervalCount / intervalLen
 *         //     resulting points = 4 * segments
 *
 *         SkScalar ptCount = pathLength * intervalCount / (float)intervalLength;
 *         ptCount = std::min(ptCount, SkDashPath::kMaxDashCount);
 *         if (SkIsNaN(ptCount)) {
 *             return false;
 *         }
 *         int n = SkScalarCeilToInt(ptCount) << 2;
 *         dst->incReserve(n);
 *
 *         // we will take care of the stroking
 *         rec->setFillStyle();
 *         return true;
 *     }
 *
 *     void addSegment(SkScalar d0, SkScalar d1, SkPathBuilder* path) const {
 *         SkASSERT(d0 <= fPathLength);
 *         // clamp the segment to our length
 *         if (d1 > fPathLength) {
 *             d1 = fPathLength;
 *         }
 *
 *         SkScalar x0 = fPts[0].fX + fTangent.fX * d0;
 *         SkScalar x1 = fPts[0].fX + fTangent.fX * d1;
 *         SkScalar y0 = fPts[0].fY + fTangent.fY * d0;
 *         SkScalar y1 = fPts[0].fY + fTangent.fY * d1;
 *
 *         SkPoint pts[4];
 *         pts[0].set(x0 + fNormal.fX, y0 + fNormal.fY);   // moveTo
 *         pts[1].set(x1 + fNormal.fX, y1 + fNormal.fY);   // lineTo
 *         pts[2].set(x1 - fNormal.fX, y1 - fNormal.fY);   // lineTo
 *         pts[3].set(x0 - fNormal.fX, y0 - fNormal.fY);   // lineTo
 *
 *         path->addPolygon(pts, false);
 *     }
 *
 * private:
 *     SkPoint fPts[2];
 *     SkVector fTangent;
 *     SkVector fNormal;
 *     SkScalar fPathLength;
 * }
 * ```
 */
public data class SpecialLineRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fPts[2]
   * ```
   */
  private var fPts: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkVector fTangent
   * ```
   */
  private var fTangent: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector fNormal
   * ```
   */
  private var fNormal: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fPathLength
   * ```
   */
  private var fPathLength: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * bool init(const SkPath& src, SkPathBuilder* dst, SkStrokeRec* rec,
   *               int intervalCount, SkScalar intervalLength) {
   *         if (rec->isHairlineStyle() || !src.isLine(fPts)) {
   *             return false;
   *         }
   *
   *         // can relax this in the future, if we handle square and round caps
   *         if (SkPaint::kButt_Cap != rec->getCap()) {
   *             return false;
   *         }
   *
   *         SkScalar pathLength = SkPoint::Distance(fPts[0], fPts[1]);
   *
   *         fTangent = fPts[1] - fPts[0];
   *         if (fTangent.isZero()) {
   *             return false;
   *         }
   *
   *         fPathLength = pathLength;
   *         fTangent.scale(sk_ieee_float_divide(1.0f, pathLength));
   *         if (!SkIsFinite(fTangent.fX, fTangent.fY)) {
   *             return false;
   *         }
   *         SkPointPriv::RotateCCW(fTangent, &fNormal);
   *         fNormal.scale(SkScalarHalf(rec->getWidth()));
   *
   *         // now estimate how many quads will be added to the path
   *         //     resulting segments = pathLen * intervalCount / intervalLen
   *         //     resulting points = 4 * segments
   *
   *         SkScalar ptCount = pathLength * intervalCount / (float)intervalLength;
   *         ptCount = std::min(ptCount, SkDashPath::kMaxDashCount);
   *         if (SkIsNaN(ptCount)) {
   *             return false;
   *         }
   *         int n = SkScalarCeilToInt(ptCount) << 2;
   *         dst->incReserve(n);
   *
   *         // we will take care of the stroking
   *         rec->setFillStyle();
   *         return true;
   *     }
   * ```
   */
  public fun `init`(
    src: SkPath,
    dst: SkPathBuilder?,
    rec: SkStrokeRec?,
    intervalCount: Int,
    intervalLength: SkScalar,
  ): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void addSegment(SkScalar d0, SkScalar d1, SkPathBuilder* path) const {
   *         SkASSERT(d0 <= fPathLength);
   *         // clamp the segment to our length
   *         if (d1 > fPathLength) {
   *             d1 = fPathLength;
   *         }
   *
   *         SkScalar x0 = fPts[0].fX + fTangent.fX * d0;
   *         SkScalar x1 = fPts[0].fX + fTangent.fX * d1;
   *         SkScalar y0 = fPts[0].fY + fTangent.fY * d0;
   *         SkScalar y1 = fPts[0].fY + fTangent.fY * d1;
   *
   *         SkPoint pts[4];
   *         pts[0].set(x0 + fNormal.fX, y0 + fNormal.fY);   // moveTo
   *         pts[1].set(x1 + fNormal.fX, y1 + fNormal.fY);   // lineTo
   *         pts[2].set(x1 - fNormal.fX, y1 - fNormal.fY);   // lineTo
   *         pts[3].set(x0 - fNormal.fX, y0 - fNormal.fY);   // lineTo
   *
   *         path->addPolygon(pts, false);
   *     }
   * ```
   */
  public fun addSegment(
    d0: SkScalar,
    d1: SkScalar,
    path: SkPathBuilder?,
  ) {
    TODO("Implement addSegment")
  }
}
