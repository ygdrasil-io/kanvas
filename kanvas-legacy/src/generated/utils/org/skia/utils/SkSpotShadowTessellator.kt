package org.skia.utils

import kotlin.Boolean
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SkSpotShadowTessellator : public SkBaseShadowTessellator {
 * public:
 *     SkSpotShadowTessellator(const SkPath& path, const SkMatrix& ctm,
 *                             const SkPoint3& zPlaneParams, const SkPoint3& lightPos,
 *                             SkScalar lightRadius, bool transparent, bool directional);
 *
 * private:
 *     bool computeClipAndPathPolygons(const SkPath& path, const SkMatrix& ctm,
 *                                     const SkMatrix& shadowTransform);
 *     void addToClip(const SkVector& nextPoint);
 *
 *     using INHERITED = SkBaseShadowTessellator;
 * }
 * ```
 */
public open class SkSpotShadowTessellator public constructor(
  path: SkPath,
  ctm: SkMatrix,
  zPlaneParams: SkPoint3,
  lightPos: SkPoint3,
  lightRadius: SkScalar,
  transparent: Boolean,
  directional: Boolean,
) : SkBaseShadowTessellator(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool SkSpotShadowTessellator::computeClipAndPathPolygons(const SkPath& path, const SkMatrix& ctm,
   *                                                          const SkMatrix& shadowTransform) {
   *
   *     fPathPolygon.reserve(path.countPoints());
   *     fClipPolygon.reserve(path.countPoints());
   *
   *     // Walk around the path and compute clip polygon and path polygon.
   *     // Will also accumulate sum of areas for centroid.
   *     // For Bezier curves, we compute additional interior points on curve.
   *     SkPath::Iter iter(path, true);
   *     SkPoint clipPts[4];
   *
   *     // coefficients to compute cubic Bezier at t = 5/16
   *     static constexpr SkScalar kA = 0.32495117187f;
   *     static constexpr SkScalar kB = 0.44311523437f;
   *     static constexpr SkScalar kC = 0.20141601562f;
   *     static constexpr SkScalar kD = 0.03051757812f;
   *
   *     SkPoint curvePoint;
   *     bool closeSeen = false;
   *     bool verbSeen = false;
   *     while (auto rec = iter.next()) {
   *         if (closeSeen) {
   *             return false;
   *         }
   *         SkPoint pts[4];             // max needed
   *         spancpy(pts, rec->fPoints); // need a writable copy
   *         switch (rec->fVerb) {
   *             case SkPathVerb::kLine:
   *                 clipPts[0] = ctm.mapPoint(pts[1]);
   *                 this->addToClip(clipPts[0]);
   *                 this->handleLine(shadowTransform, &pts[1]);
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 ctm.mapPoints({clipPts, 3}, {pts, 3});
   *                 // point at t = 1/2
   *                 curvePoint.fX = 0.25f*clipPts[0].fX + 0.5f*clipPts[1].fX + 0.25f*clipPts[2].fX;
   *                 curvePoint.fY = 0.25f*clipPts[0].fY + 0.5f*clipPts[1].fY + 0.25f*clipPts[2].fY;
   *                 this->addToClip(curvePoint);
   *                 this->addToClip(clipPts[2]);
   *                 this->handleQuad(shadowTransform, pts);
   *                 break;
   *             case SkPathVerb::kConic: {
   *                 ctm.mapPoints({clipPts, 3}, {pts, 3});
   *                 const float w = rec->conicWeight();
   *                 // point at t = 1/2
   *                 curvePoint.fX = 0.25f*clipPts[0].fX + w*0.5f*clipPts[1].fX + 0.25f*clipPts[2].fX;
   *                 curvePoint.fY = 0.25f*clipPts[0].fY + w*0.5f*clipPts[1].fY + 0.25f*clipPts[2].fY;
   *                 curvePoint *= SkScalarInvert(0.5f + 0.5f*w);
   *                 this->addToClip(curvePoint);
   *                 this->addToClip(clipPts[2]);
   *                 this->handleConic(shadowTransform, pts, w);
   *             } break;
   *             case SkPathVerb::kCubic:
   *                 ctm.mapPoints({clipPts, 4}, {pts, 4});
   *                 // point at t = 5/16
   *                 curvePoint.fX = kA*clipPts[0].fX + kB*clipPts[1].fX
   *                               + kC*clipPts[2].fX + kD*clipPts[3].fX;
   *                 curvePoint.fY = kA*clipPts[0].fY + kB*clipPts[1].fY
   *                               + kC*clipPts[2].fY + kD*clipPts[3].fY;
   *                 this->addToClip(curvePoint);
   *                 // point at t = 11/16
   *                 curvePoint.fX = kD*clipPts[0].fX + kC*clipPts[1].fX
   *                               + kB*clipPts[2].fX + kA*clipPts[3].fX;
   *                 curvePoint.fY = kD*clipPts[0].fY + kC*clipPts[1].fY
   *                               + kB*clipPts[2].fY + kA*clipPts[3].fY;
   *                 this->addToClip(curvePoint);
   *                 this->addToClip(clipPts[3]);
   *                 this->handleCubic(shadowTransform, pts);
   *                 break;
   *             case SkPathVerb::kMove:
   *                 if (verbSeen) {
   *                     return false;
   *                 }
   *                 break;
   *             case SkPathVerb::kClose:
   *                 closeSeen = true;
   *                 break;
   *         }
   *         verbSeen = true;
   *     }
   *
   *     this->finishPathPolygon();
   *     return true;
   * }
   * ```
   */
  private fun computeClipAndPathPolygons(
    path: SkPath,
    ctm: SkMatrix,
    shadowTransform: SkMatrix,
  ): Boolean {
    TODO("Implement computeClipAndPathPolygons")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpotShadowTessellator::addToClip(const SkPoint& point) {
   *     if (fClipPolygon.empty() || !duplicate_pt(point, fClipPolygon[fClipPolygon.size() - 1])) {
   *         fClipPolygon.push_back(point);
   *     }
   * }
   * ```
   */
  private fun addToClip(nextPoint: SkVector) {
    TODO("Implement addToClip")
  }
}
