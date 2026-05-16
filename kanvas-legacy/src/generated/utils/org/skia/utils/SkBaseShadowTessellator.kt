package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UShort
import org.skia.core.SkVertices
import org.skia.foundation.SkColor
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkBaseShadowTessellator {
 * public:
 *     SkBaseShadowTessellator(const SkPoint3& zPlaneParams, const SkRect& bounds, bool transparent);
 *     virtual ~SkBaseShadowTessellator() {}
 *
 *     sk_sp<SkVertices> releaseVertices() {
 *         if (!fSucceeded) {
 *             return nullptr;
 *         }
 *         return SkVertices::MakeCopy(SkVertices::kTriangles_VertexMode, this->vertexCount(),
 *                                     fPositions.begin(), nullptr, fColors.begin(),
 *                                     this->indexCount(), fIndices.begin());
 *     }
 *
 * protected:
 *     inline static constexpr auto kMinHeight = 0.1f;
 *     inline static constexpr auto kPenumbraColor = SK_ColorTRANSPARENT;
 *     inline static constexpr auto kUmbraColor = SK_ColorBLACK;
 *
 *     int vertexCount() const { return fPositions.size(); }
 *     int indexCount() const { return fIndices.size(); }
 *
 *     // initialization methods
 *     bool accumulateCentroid(const SkPoint& c, const SkPoint& n);
 *     bool checkConvexity(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2);
 *     void finishPathPolygon();
 *
 *     // convex shadow methods
 *     bool computeConvexShadow(SkScalar inset, SkScalar outset, bool doClip);
 *     void computeClipVectorsAndTestCentroid();
 *     bool clipUmbraPoint(const SkPoint& umbraPoint, const SkPoint& centroid, SkPoint* clipPoint);
 *     void addEdge(const SkVector& nextPoint, const SkVector& nextNormal, SkColor umbraColor,
 *                  const SkTDArray<SkPoint>& umbraPolygon, bool lastEdge, bool doClip);
 *     bool addInnerPoint(const SkPoint& pathPoint, SkColor umbraColor,
 *                        const SkTDArray<SkPoint>& umbraPolygon, int* currUmbraIndex);
 *     int getClosestUmbraIndex(const SkPoint& point, const SkTDArray<SkPoint>& umbraPolygon);
 *
 *     // concave shadow methods
 *     bool computeConcaveShadow(SkScalar inset, SkScalar outset);
 *     void stitchConcaveRings(const SkTDArray<SkPoint>& umbraPolygon,
 *                             SkTDArray<int>* umbraIndices,
 *                             const SkTDArray<SkPoint>& penumbraPolygon,
 *                             SkTDArray<int>* penumbraIndices);
 *
 *     void handleLine(const SkPoint& p);
 *     void handleLine(const SkMatrix& m, SkPoint* p);
 *
 *     void handleQuad(const SkPoint pts[3]);
 *     void handleQuad(const SkMatrix& m, SkPoint pts[3]);
 *
 *     void handleCubic(const SkMatrix& m, SkPoint pts[4]);
 *
 *     void handleConic(const SkMatrix& m, SkPoint pts[3], SkScalar w);
 *
 *     bool addArc(const SkVector& nextNormal, SkScalar offset, bool finishArc);
 *
 *     void appendTriangle(uint16_t index0, uint16_t index1, uint16_t index2);
 *     void appendQuad(uint16_t index0, uint16_t index1, uint16_t index2, uint16_t index3);
 *
 *     SkScalar heightFunc(SkScalar x, SkScalar y) {
 *         return fZPlaneParams.fX*x + fZPlaneParams.fY*y + fZPlaneParams.fZ;
 *     }
 *
 *     SkPoint3            fZPlaneParams;
 *
 *     // temporary buffer
 *     SkTDArray<SkPoint>  fPointBuffer;
 *
 *     SkTDArray<SkPoint>  fPositions;
 *     SkTDArray<SkColor>  fColors;
 *     SkTDArray<uint16_t> fIndices;
 *
 *     SkTDArray<SkPoint>   fPathPolygon;
 *     SkTDArray<SkPoint>   fClipPolygon;
 *     SkTDArray<SkVector>  fClipVectors;
 *
 *     SkRect              fPathBounds;
 *     SkPoint             fCentroid;
 *     SkScalar            fArea;
 *     SkScalar            fLastArea;
 *     SkScalar            fLastCross;
 *
 *     int                 fFirstVertexIndex;
 *     SkVector            fFirstOutset;
 *     SkPoint             fFirstPoint;
 *
 *     bool                fSucceeded;
 *     bool                fTransparent;
 *     bool                fIsConvex;
 *     bool                fValidUmbra;
 *
 *     SkScalar            fDirection;
 *     int                 fPrevUmbraIndex;
 *     int                 fCurrUmbraIndex;
 *     int                 fCurrClipIndex;
 *     bool                fPrevUmbraOutside;
 *     bool                fFirstUmbraOutside;
 *     SkVector            fPrevOutset;
 *     SkPoint             fPrevPoint;
 * }
 * ```
 */
public open class SkBaseShadowTessellator public constructor(
  zPlaneParams: SkPoint3,
  bounds: SkRect,
  transparent: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr auto kMinHeight = 0.1f
   * ```
   */
  protected var fZPlaneParams: SkPoint3 = TODO("Initialize fZPlaneParams")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr auto kPenumbraColor = SK_ColorTRANSPARENT
   * ```
   */
  protected var fPointBuffer: SkTDArray<SkPoint> = TODO("Initialize fPointBuffer")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr auto kUmbraColor = SK_ColorBLACK
   * ```
   */
  protected var fPositions: SkTDArray<SkPoint> = TODO("Initialize fPositions")

  /**
   * C++ original:
   * ```cpp
   * SkPoint3            fZPlaneParams
   * ```
   */
  protected var fColors: SkTDArray<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>  fPointBuffer
   * ```
   */
  protected var fIndices: SkTDArray<UShort> = TODO("Initialize fIndices")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>  fPositions
   * ```
   */
  protected var fPathPolygon: SkTDArray<SkPoint> = TODO("Initialize fPathPolygon")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkColor>  fColors
   * ```
   */
  protected var fClipPolygon: SkTDArray<SkPoint> = TODO("Initialize fClipPolygon")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<uint16_t> fIndices
   * ```
   */
  protected var fClipVectors: SkTDArray<SkVector> = TODO("Initialize fClipVectors")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>   fPathPolygon
   * ```
   */
  protected var fPathBounds: SkRect = TODO("Initialize fPathBounds")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>   fClipPolygon
   * ```
   */
  protected var fCentroid: SkPoint = TODO("Initialize fCentroid")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkVector>  fClipVectors
   * ```
   */
  protected var fArea: SkScalar = TODO("Initialize fArea")

  /**
   * C++ original:
   * ```cpp
   * SkRect              fPathBounds
   * ```
   */
  protected var fLastArea: SkScalar = TODO("Initialize fLastArea")

  /**
   * C++ original:
   * ```cpp
   * SkPoint             fCentroid
   * ```
   */
  protected var fLastCross: SkScalar = TODO("Initialize fLastCross")

  /**
   * C++ original:
   * ```cpp
   * SkScalar            fArea
   * ```
   */
  protected var fFirstVertexIndex: Int = TODO("Initialize fFirstVertexIndex")

  /**
   * C++ original:
   * ```cpp
   * SkScalar            fLastArea
   * ```
   */
  protected var fFirstOutset: SkVector = TODO("Initialize fFirstOutset")

  /**
   * C++ original:
   * ```cpp
   * SkScalar            fLastCross
   * ```
   */
  protected var fFirstPoint: SkPoint = TODO("Initialize fFirstPoint")

  /**
   * C++ original:
   * ```cpp
   * int                 fFirstVertexIndex
   * ```
   */
  protected var fSucceeded: Boolean = TODO("Initialize fSucceeded")

  /**
   * C++ original:
   * ```cpp
   * SkVector            fFirstOutset
   * ```
   */
  protected var fTransparent: Boolean = TODO("Initialize fTransparent")

  /**
   * C++ original:
   * ```cpp
   * SkPoint             fFirstPoint
   * ```
   */
  protected var fIsConvex: Boolean = TODO("Initialize fIsConvex")

  /**
   * C++ original:
   * ```cpp
   * bool                fSucceeded
   * ```
   */
  protected var fValidUmbra: Boolean = TODO("Initialize fValidUmbra")

  /**
   * C++ original:
   * ```cpp
   * bool                fTransparent
   * ```
   */
  protected var fDirection: SkScalar = TODO("Initialize fDirection")

  /**
   * C++ original:
   * ```cpp
   * bool                fIsConvex
   * ```
   */
  protected var fPrevUmbraIndex: Int = TODO("Initialize fPrevUmbraIndex")

  /**
   * C++ original:
   * ```cpp
   * bool                fValidUmbra
   * ```
   */
  protected var fCurrUmbraIndex: Int = TODO("Initialize fCurrUmbraIndex")

  /**
   * C++ original:
   * ```cpp
   * SkScalar            fDirection
   * ```
   */
  protected var fCurrClipIndex: Int = TODO("Initialize fCurrClipIndex")

  /**
   * C++ original:
   * ```cpp
   * int                 fPrevUmbraIndex
   * ```
   */
  protected var fPrevUmbraOutside: Boolean = TODO("Initialize fPrevUmbraOutside")

  /**
   * C++ original:
   * ```cpp
   * int                 fCurrUmbraIndex
   * ```
   */
  protected var fFirstUmbraOutside: Boolean = TODO("Initialize fFirstUmbraOutside")

  /**
   * C++ original:
   * ```cpp
   * int                 fCurrClipIndex
   * ```
   */
  protected var fPrevOutset: SkVector = TODO("Initialize fPrevOutset")

  /**
   * C++ original:
   * ```cpp
   * bool                fPrevUmbraOutside
   * ```
   */
  protected var fPrevPoint: SkPoint = TODO("Initialize fPrevPoint")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> releaseVertices() {
   *         if (!fSucceeded) {
   *             return nullptr;
   *         }
   *         return SkVertices::MakeCopy(SkVertices::kTriangles_VertexMode, this->vertexCount(),
   *                                     fPositions.begin(), nullptr, fColors.begin(),
   *                                     this->indexCount(), fIndices.begin());
   *     }
   * ```
   */
  public fun releaseVertices(): SkSp<SkVertices> {
    TODO("Implement releaseVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * int vertexCount() const { return fPositions.size(); }
   * ```
   */
  protected fun vertexCount(): Int {
    TODO("Implement vertexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int indexCount() const { return fIndices.size(); }
   * ```
   */
  protected fun indexCount(): Int {
    TODO("Implement indexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::accumulateCentroid(const SkPoint& curr, const SkPoint& next) {
   *     if (duplicate_pt(curr, next)) {
   *         return false;
   *     }
   *
   *     SkASSERT(!fPathPolygon.empty());
   *     SkVector v0 = curr - fPathPolygon[0];
   *     SkVector v1 = next - fPathPolygon[0];
   *     SkScalar quadArea = v0.cross(v1);
   *     fCentroid.fX += (v0.fX + v1.fX) * quadArea;
   *     fCentroid.fY += (v0.fY + v1.fY) * quadArea;
   *     fArea += quadArea;
   *     // convexity check
   *     if (quadArea*fLastArea < 0) {
   *         fIsConvex = false;
   *     }
   *     if (0 != quadArea) {
   *         fLastArea = quadArea;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  protected fun accumulateCentroid(c: SkPoint, n: SkPoint): Boolean {
    TODO("Implement accumulateCentroid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::checkConvexity(const SkPoint& p0,
   *                                              const SkPoint& p1,
   *                                              const SkPoint& p2) {
   *     SkScalar cross = perp_dot(p0, p1, p2);
   *     // skip collinear point
   *     if (SkScalarNearlyZero(cross)) {
   *         return false;
   *     }
   *
   *     // check for convexity
   *     if (fLastCross*cross < 0) {
   *         fIsConvex = false;
   *     }
   *     if (0 != cross) {
   *         fLastCross = cross;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  protected fun checkConvexity(
    p0: SkPoint,
    p1: SkPoint,
    p2: SkPoint,
  ): Boolean {
    TODO("Implement checkConvexity")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::finishPathPolygon() {
   *     if (fPathPolygon.size() > 1) {
   *         if (!this->accumulateCentroid(fPathPolygon[fPathPolygon.size() - 1], fPathPolygon[0])) {
   *             // remove coincident point
   *             fPathPolygon.pop_back();
   *         }
   *     }
   *
   *     if (fPathPolygon.size() > 2) {
   *         // do this before the final convexity check, so we use the correct fPathPolygon[0]
   *         fCentroid *= sk_ieee_float_divide(1, 3 * fArea);
   *         fCentroid += fPathPolygon[0];
   *         if (!checkConvexity(fPathPolygon[fPathPolygon.size() - 2],
   *                             fPathPolygon[fPathPolygon.size() - 1],
   *                             fPathPolygon[0])) {
   *             // remove collinear point
   *             fPathPolygon[0] = fPathPolygon[fPathPolygon.size() - 1];
   *             fPathPolygon.pop_back();
   *         }
   *     }
   *
   *     // if area is positive, winding is ccw
   *     fDirection = fArea > 0 ? -1 : 1;
   * }
   * ```
   */
  protected fun finishPathPolygon() {
    TODO("Implement finishPathPolygon")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::computeConvexShadow(SkScalar inset, SkScalar outset, bool doClip) {
   *     if (doClip) {
   *         this->computeClipVectorsAndTestCentroid();
   *     }
   *
   *     // adjust inset distance and umbra color if necessary
   *     auto umbraColor = kUmbraColor;
   *     SkScalar minDistSq = SkPointPriv::DistanceToLineSegmentBetweenSqd(fCentroid,
   *                                                                       fPathPolygon[0],
   *                                                                       fPathPolygon[1]);
   *     for (int i = 1; i < fPathPolygon.size(); ++i) {
   *         int j = i + 1;
   *         if (i == fPathPolygon.size() - 1) {
   *             j = 0;
   *         }
   *         SkPoint currPoint = fPathPolygon[i];
   *         SkPoint nextPoint = fPathPolygon[j];
   *         SkScalar distSq = SkPointPriv::DistanceToLineSegmentBetweenSqd(fCentroid, currPoint,
   *                                                                        nextPoint);
   *         if (distSq < minDistSq) {
   *             minDistSq = distSq;
   *         }
   *     }
   *
   *     SkTDArray<SkPoint> insetPolygon;
   *     if (inset > SK_ScalarNearlyZero) {
   *         static constexpr auto kTolerance = 1.0e-2f;
   *         if (minDistSq < (inset + kTolerance)*(inset + kTolerance)) {
   *             // if the umbra would collapse, we back off a bit on inner blur and adjust the alpha
   *             auto newInset = SkScalarSqrt(minDistSq) - kTolerance;
   *             auto ratio = 128 * (newInset / inset + 1);
   *             SkASSERT(SkIsFinite(ratio));
   *             // they aren't PMColors, but the interpolation algorithm is the same
   *             umbraColor = SkPMLerp(kUmbraColor, kPenumbraColor, (unsigned)ratio);
   *             inset = newInset;
   *         }
   *
   *         // generate inner ring
   *         if (!SkInsetConvexPolygon(&fPathPolygon[0], fPathPolygon.size(), inset,
   *                                   &insetPolygon)) {
   *             // not ideal, but in this case we'll inset using the centroid
   *             fValidUmbra = false;
   *         }
   *     }
   *     const SkTDArray<SkPoint>& umbraPolygon = (inset > SK_ScalarNearlyZero) ? insetPolygon
   *                                                                            : fPathPolygon;
   *
   *     // walk around the path polygon, generate outer ring and connect to inner ring
   *     if (fTransparent) {
   *         fPositions.push_back(fCentroid);
   *         fColors.push_back(umbraColor);
   *     }
   *     fCurrUmbraIndex = 0;
   *
   *     // initial setup
   *     // add first quad
   *     int polyCount = fPathPolygon.size();
   *     if (!compute_normal(fPathPolygon[polyCount - 1], fPathPolygon[0], fDirection, &fFirstOutset)) {
   *         // polygon should be sanitized by this point, so this is unrecoverable
   *         return false;
   *     }
   *
   *     fFirstOutset *= outset;
   *     fFirstPoint = fPathPolygon[polyCount - 1];
   *     fFirstVertexIndex = fPositions.size();
   *     fPrevOutset = fFirstOutset;
   *     fPrevPoint = fFirstPoint;
   *     fPrevUmbraIndex = -1;
   *
   *     this->addInnerPoint(fFirstPoint, umbraColor, umbraPolygon, &fPrevUmbraIndex);
   *
   *     if (!fTransparent && doClip) {
   *         SkPoint clipPoint;
   *         bool isOutside = this->clipUmbraPoint(fPositions[fFirstVertexIndex],
   *                                               fCentroid, &clipPoint);
   *         if (isOutside) {
   *             fPositions.push_back(clipPoint);
   *             fColors.push_back(umbraColor);
   *         }
   *         fPrevUmbraOutside = isOutside;
   *         fFirstUmbraOutside = isOutside;
   *     }
   *
   *     SkPoint newPoint = fFirstPoint + fFirstOutset;
   *     fPositions.push_back(newPoint);
   *     fColors.push_back(kPenumbraColor);
   *     this->addEdge(fPathPolygon[0], fFirstOutset, umbraColor, umbraPolygon, false, doClip);
   *
   *     for (int i = 1; i < polyCount; ++i) {
   *         SkVector normal;
   *         if (!compute_normal(fPrevPoint, fPathPolygon[i], fDirection, &normal)) {
   *             return false;
   *         }
   *         normal *= outset;
   *         this->addArc(normal, outset, true);
   *         this->addEdge(fPathPolygon[i], normal, umbraColor, umbraPolygon,
   *                       i == polyCount - 1, doClip);
   *     }
   *     SkASSERT(this->indexCount());
   *
   *     // final fan
   *     SkASSERT(fPositions.size() >= 3);
   *     if (this->addArc(fFirstOutset, outset, false)) {
   *         if (fFirstUmbraOutside) {
   *             this->appendTriangle(fFirstVertexIndex, fPositions.size() - 1,
   *                                  fFirstVertexIndex + 2);
   *         } else {
   *             this->appendTriangle(fFirstVertexIndex, fPositions.size() - 1,
   *                                  fFirstVertexIndex + 1);
   *         }
   *     } else {
   *         // no arc added, fix up by setting first penumbra point position to last one
   *         if (fFirstUmbraOutside) {
   *             fPositions[fFirstVertexIndex + 2] = fPositions[fPositions.size() - 1];
   *         } else {
   *             fPositions[fFirstVertexIndex + 1] = fPositions[fPositions.size() - 1];
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  protected fun computeConvexShadow(
    inset: SkScalar,
    outset: SkScalar,
    doClip: Boolean,
  ): Boolean {
    TODO("Implement computeConvexShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::computeClipVectorsAndTestCentroid() {
   *     SkASSERT(fClipPolygon.size() >= 3);
   *     fCurrClipIndex = fClipPolygon.size() - 1;
   *
   *     // init clip vectors
   *     SkVector v0 = fClipPolygon[1] - fClipPolygon[0];
   *     SkVector v1 = fClipPolygon[2] - fClipPolygon[0];
   *     fClipVectors.push_back(v0);
   *
   *     // init centroid check
   *     bool hiddenCentroid = true;
   *     v1 = fCentroid - fClipPolygon[0];
   *     SkScalar initCross = v0.cross(v1);
   *
   *     for (int p = 1; p < fClipPolygon.size(); ++p) {
   *         // add to clip vectors
   *         v0 = fClipPolygon[(p + 1) % fClipPolygon.size()] - fClipPolygon[p];
   *         fClipVectors.push_back(v0);
   *         // Determine if transformed centroid is inside clipPolygon.
   *         v1 = fCentroid - fClipPolygon[p];
   *         if (initCross*v0.cross(v1) <= 0) {
   *             hiddenCentroid = false;
   *         }
   *     }
   *     SkASSERT(fClipVectors.size() == fClipPolygon.size());
   *
   *     fTransparent = fTransparent || !hiddenCentroid;
   * }
   * ```
   */
  protected fun computeClipVectorsAndTestCentroid() {
    TODO("Implement computeClipVectorsAndTestCentroid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::clipUmbraPoint(const SkPoint& umbraPoint, const SkPoint& centroid,
   *                                              SkPoint* clipPoint) {
   *     SkVector segmentVector = centroid - umbraPoint;
   *
   *     int startClipPoint = fCurrClipIndex;
   *     do {
   *         SkVector dp = umbraPoint - fClipPolygon[fCurrClipIndex];
   *         SkScalar denom = fClipVectors[fCurrClipIndex].cross(segmentVector);
   *         SkScalar t_num = dp.cross(segmentVector);
   *         // if line segments are nearly parallel
   *         if (SkScalarNearlyZero(denom)) {
   *             // and collinear
   *             if (SkScalarNearlyZero(t_num)) {
   *                 return false;
   *             }
   *             // otherwise are separate, will try the next poly segment
   *             // else if crossing lies within poly segment
   *         } else if (t_num >= 0 && t_num <= denom) {
   *             SkScalar s_num = dp.cross(fClipVectors[fCurrClipIndex]);
   *             // if umbra point is inside the clip polygon
   *             if (s_num >= 0 && s_num <= denom) {
   *                 segmentVector *= s_num / denom;
   *                 *clipPoint = umbraPoint + segmentVector;
   *                 return true;
   *             }
   *         }
   *         fCurrClipIndex = (fCurrClipIndex + 1) % fClipPolygon.size();
   *     } while (fCurrClipIndex != startClipPoint);
   *
   *     return false;
   * }
   * ```
   */
  protected fun clipUmbraPoint(
    umbraPoint: SkPoint,
    centroid: SkPoint,
    clipPoint: SkPoint?,
  ): Boolean {
    TODO("Implement clipUmbraPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::addEdge(const SkPoint& nextPoint, const SkVector& nextNormal,
   *                                       SkColor umbraColor, const SkTDArray<SkPoint>& umbraPolygon,
   *                                       bool lastEdge, bool doClip) {
   *     // add next umbra point
   *     int currUmbraIndex;
   *     bool duplicate;
   *     if (lastEdge) {
   *         duplicate = false;
   *         currUmbraIndex = fFirstVertexIndex;
   *         fPrevPoint = nextPoint;
   *     } else {
   *         duplicate = this->addInnerPoint(nextPoint, umbraColor, umbraPolygon, &currUmbraIndex);
   *     }
   *     int prevPenumbraIndex = duplicate || (currUmbraIndex == fFirstVertexIndex)
   *         ? fPositions.size() - 1
   *         : fPositions.size() - 2;
   *     if (!duplicate) {
   *         // add to center fan if transparent or centroid showing
   *         if (fTransparent) {
   *             this->appendTriangle(0, fPrevUmbraIndex, currUmbraIndex);
   *             // otherwise add to clip ring
   *         } else if (doClip) {
   *             SkPoint clipPoint;
   *             bool isOutside = lastEdge ? fFirstUmbraOutside
   *                 : this->clipUmbraPoint(fPositions[currUmbraIndex], fCentroid,
   *                                        &clipPoint);
   *             if (isOutside) {
   *                 if (!lastEdge) {
   *                     fPositions.push_back(clipPoint);
   *                     fColors.push_back(umbraColor);
   *                 }
   *                 this->appendTriangle(fPrevUmbraIndex, currUmbraIndex, currUmbraIndex + 1);
   *                 if (fPrevUmbraOutside) {
   *                     // fill out quad
   *                     this->appendTriangle(fPrevUmbraIndex, currUmbraIndex + 1,
   *                                          fPrevUmbraIndex + 1);
   *                 }
   *             } else if (fPrevUmbraOutside) {
   *                 // add tri
   *                 this->appendTriangle(fPrevUmbraIndex, currUmbraIndex, fPrevUmbraIndex + 1);
   *             }
   *
   *             fPrevUmbraOutside = isOutside;
   *         }
   *     }
   *
   *     // add next penumbra point and quad
   *     SkPoint newPoint = nextPoint + nextNormal;
   *     fPositions.push_back(newPoint);
   *     fColors.push_back(kPenumbraColor);
   *
   *     if (!duplicate) {
   *         this->appendTriangle(fPrevUmbraIndex, prevPenumbraIndex, currUmbraIndex);
   *     }
   *     this->appendTriangle(prevPenumbraIndex, fPositions.size() - 1, currUmbraIndex);
   *
   *     fPrevUmbraIndex = currUmbraIndex;
   *     fPrevOutset = nextNormal;
   * }
   * ```
   */
  protected fun addEdge(
    nextPoint: SkVector,
    nextNormal: SkVector,
    umbraColor: SkColor,
    umbraPolygon: SkTDArray<SkPoint>,
    lastEdge: Boolean,
    doClip: Boolean,
  ) {
    TODO("Implement addEdge")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::addInnerPoint(const SkPoint& pathPoint, SkColor umbraColor,
   *                                             const SkTDArray<SkPoint>& umbraPolygon,
   *                                             int* currUmbraIndex) {
   *     SkPoint umbraPoint;
   *     if (!fValidUmbra) {
   *         SkVector v = fCentroid - pathPoint;
   *         v *= 0.95f;
   *         umbraPoint = pathPoint + v;
   *     } else {
   *         umbraPoint = umbraPolygon[this->getClosestUmbraIndex(pathPoint, umbraPolygon)];
   *     }
   *
   *     fPrevPoint = pathPoint;
   *
   *     // merge "close" points
   *     if (fPrevUmbraIndex == -1 ||
   *         !duplicate_pt(umbraPoint, fPositions[fPrevUmbraIndex])) {
   *         // if we've wrapped around, don't add a new point
   *         if (fPrevUmbraIndex >= 0 && duplicate_pt(umbraPoint, fPositions[fFirstVertexIndex])) {
   *             *currUmbraIndex = fFirstVertexIndex;
   *         } else {
   *             *currUmbraIndex = fPositions.size();
   *             fPositions.push_back(umbraPoint);
   *             fColors.push_back(umbraColor);
   *         }
   *         return false;
   *     } else {
   *         *currUmbraIndex = fPrevUmbraIndex;
   *         return true;
   *     }
   * }
   * ```
   */
  protected fun addInnerPoint(
    pathPoint: SkPoint,
    umbraColor: SkColor,
    umbraPolygon: SkTDArray<SkPoint>,
    currUmbraIndex: Int?,
  ): Boolean {
    TODO("Implement addInnerPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkBaseShadowTessellator::getClosestUmbraIndex(const SkPoint& p,
   *                                                   const SkTDArray<SkPoint>& umbraPolygon) {
   *     SkScalar minDistance = SkPointPriv::DistanceToSqd(p, umbraPolygon[fCurrUmbraIndex]);
   *     int index = fCurrUmbraIndex;
   *     int dir = 1;
   *     int next = (index + dir) % umbraPolygon.size();
   *
   *     // init travel direction
   *     SkScalar distance = SkPointPriv::DistanceToSqd(p, umbraPolygon[next]);
   *     if (distance < minDistance) {
   *         index = next;
   *         minDistance = distance;
   *     } else {
   *         dir = umbraPolygon.size() - 1;
   *     }
   *
   *     // iterate until we find a point that increases the distance
   *     next = (index + dir) % umbraPolygon.size();
   *     distance = SkPointPriv::DistanceToSqd(p, umbraPolygon[next]);
   *     while (distance < minDistance) {
   *         index = next;
   *         minDistance = distance;
   *         next = (index + dir) % umbraPolygon.size();
   *         distance = SkPointPriv::DistanceToSqd(p, umbraPolygon[next]);
   *     }
   *
   *     fCurrUmbraIndex = index;
   *     return index;
   * }
   * ```
   */
  protected fun getClosestUmbraIndex(point: SkPoint, umbraPolygon: SkTDArray<SkPoint>): Int {
    TODO("Implement getClosestUmbraIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::computeConcaveShadow(SkScalar inset, SkScalar outset) {
   *     if (!SkIsSimplePolygon(&fPathPolygon[0], fPathPolygon.size())) {
   *         return false;
   *     }
   *
   *     // shouldn't inset more than the half bounds of the polygon
   *     inset = std::min(inset, std::min(SkTAbs(SkRectPriv::HalfWidth(fPathBounds)),
   *                                      SkTAbs(SkRectPriv::HalfHeight(fPathBounds))));
   *     // generate inner ring
   *     SkTDArray<SkPoint> umbraPolygon;
   *     SkTDArray<int> umbraIndices;
   *     umbraIndices.reserve(fPathPolygon.size());
   *     if (!SkOffsetSimplePolygon(&fPathPolygon[0], fPathPolygon.size(), fPathBounds, inset,
   *                                &umbraPolygon, &umbraIndices)) {
   *         // TODO: figure out how to handle this case
   *         return false;
   *     }
   *
   *     // generate outer ring
   *     SkTDArray<SkPoint> penumbraPolygon;
   *     SkTDArray<int> penumbraIndices;
   *     penumbraPolygon.reserve(umbraPolygon.size());
   *     penumbraIndices.reserve(umbraPolygon.size());
   *     if (!SkOffsetSimplePolygon(&fPathPolygon[0], fPathPolygon.size(), fPathBounds, -outset,
   *                                &penumbraPolygon, &penumbraIndices)) {
   *         // TODO: figure out how to handle this case
   *         return false;
   *     }
   *
   *     if (umbraPolygon.empty() || penumbraPolygon.empty()) {
   *         return false;
   *     }
   *
   *     // attach the rings together
   *     this->stitchConcaveRings(umbraPolygon, &umbraIndices, penumbraPolygon, &penumbraIndices);
   *
   *     return true;
   * }
   * ```
   */
  protected fun computeConcaveShadow(inset: SkScalar, outset: SkScalar): Boolean {
    TODO("Implement computeConcaveShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::stitchConcaveRings(const SkTDArray<SkPoint>& umbraPolygon,
   *                                                  SkTDArray<int>* umbraIndices,
   *                                                  const SkTDArray<SkPoint>& penumbraPolygon,
   *                                                  SkTDArray<int>* penumbraIndices) {
   *     // TODO: only create and fill indexMap when fTransparent is true?
   *     AutoSTMalloc<64, uint16_t> indexMap(umbraPolygon.size());
   *
   *     // find minimum indices
   *     int minIndex = 0;
   *     int min = (*penumbraIndices)[0];
   *     for (int i = 1; i < (*penumbraIndices).size(); ++i) {
   *         if ((*penumbraIndices)[i] < min) {
   *             min = (*penumbraIndices)[i];
   *             minIndex = i;
   *         }
   *     }
   *     int currPenumbra = minIndex;
   *
   *     minIndex = 0;
   *     min = (*umbraIndices)[0];
   *     for (int i = 1; i < (*umbraIndices).size(); ++i) {
   *         if ((*umbraIndices)[i] < min) {
   *             min = (*umbraIndices)[i];
   *             minIndex = i;
   *         }
   *     }
   *     int currUmbra = minIndex;
   *
   *     // now find a case where the indices are equal (there should be at least one)
   *     int maxPenumbraIndex = fPathPolygon.size() - 1;
   *     int maxUmbraIndex = fPathPolygon.size() - 1;
   *     while ((*penumbraIndices)[currPenumbra] != (*umbraIndices)[currUmbra]) {
   *         if ((*penumbraIndices)[currPenumbra] < (*umbraIndices)[currUmbra]) {
   *             (*penumbraIndices)[currPenumbra] += fPathPolygon.size();
   *             maxPenumbraIndex = (*penumbraIndices)[currPenumbra];
   *             currPenumbra = (currPenumbra + 1) % penumbraPolygon.size();
   *         } else {
   *             (*umbraIndices)[currUmbra] += fPathPolygon.size();
   *             maxUmbraIndex = (*umbraIndices)[currUmbra];
   *             currUmbra = (currUmbra + 1) % umbraPolygon.size();
   *         }
   *     }
   *
   *     fPositions.push_back(penumbraPolygon[currPenumbra]);
   *     fColors.push_back(kPenumbraColor);
   *     int prevPenumbraIndex = 0;
   *     fPositions.push_back(umbraPolygon[currUmbra]);
   *     fColors.push_back(kUmbraColor);
   *     fPrevUmbraIndex = 1;
   *     indexMap[currUmbra] = 1;
   *
   *     int nextPenumbra = (currPenumbra + 1) % penumbraPolygon.size();
   *     int nextUmbra = (currUmbra + 1) % umbraPolygon.size();
   *     while ((*penumbraIndices)[nextPenumbra] <= maxPenumbraIndex ||
   *            (*umbraIndices)[nextUmbra] <= maxUmbraIndex) {
   *
   *         if ((*umbraIndices)[nextUmbra] == (*penumbraIndices)[nextPenumbra]) {
   *             // advance both one step
   *             fPositions.push_back(penumbraPolygon[nextPenumbra]);
   *             fColors.push_back(kPenumbraColor);
   *             int currPenumbraIndex = fPositions.size() - 1;
   *
   *             fPositions.push_back(umbraPolygon[nextUmbra]);
   *             fColors.push_back(kUmbraColor);
   *             int currUmbraIndex = fPositions.size() - 1;
   *             indexMap[nextUmbra] = currUmbraIndex;
   *
   *             this->appendQuad(prevPenumbraIndex, currPenumbraIndex,
   *                              fPrevUmbraIndex, currUmbraIndex);
   *
   *             prevPenumbraIndex = currPenumbraIndex;
   *             (*penumbraIndices)[currPenumbra] += fPathPolygon.size();
   *             currPenumbra = nextPenumbra;
   *             nextPenumbra = (currPenumbra + 1) % penumbraPolygon.size();
   *
   *             fPrevUmbraIndex = currUmbraIndex;
   *             (*umbraIndices)[currUmbra] += fPathPolygon.size();
   *             currUmbra = nextUmbra;
   *             nextUmbra = (currUmbra + 1) % umbraPolygon.size();
   *         }
   *
   *         while ((*penumbraIndices)[nextPenumbra] < (*umbraIndices)[nextUmbra] &&
   *                (*penumbraIndices)[nextPenumbra] <= maxPenumbraIndex) {
   *             // fill out penumbra arc
   *             fPositions.push_back(penumbraPolygon[nextPenumbra]);
   *             fColors.push_back(kPenumbraColor);
   *             int currPenumbraIndex = fPositions.size() - 1;
   *
   *             this->appendTriangle(prevPenumbraIndex, currPenumbraIndex, fPrevUmbraIndex);
   *
   *             prevPenumbraIndex = currPenumbraIndex;
   *             // this ensures the ordering when we wrap around
   *             (*penumbraIndices)[currPenumbra] += fPathPolygon.size();
   *             currPenumbra = nextPenumbra;
   *             nextPenumbra = (currPenumbra + 1) % penumbraPolygon.size();
   *         }
   *
   *         while ((*umbraIndices)[nextUmbra] < (*penumbraIndices)[nextPenumbra] &&
   *                (*umbraIndices)[nextUmbra] <= maxUmbraIndex) {
   *             // fill out umbra arc
   *             fPositions.push_back(umbraPolygon[nextUmbra]);
   *             fColors.push_back(kUmbraColor);
   *             int currUmbraIndex = fPositions.size() - 1;
   *             indexMap[nextUmbra] = currUmbraIndex;
   *
   *             this->appendTriangle(fPrevUmbraIndex, prevPenumbraIndex, currUmbraIndex);
   *
   *             fPrevUmbraIndex = currUmbraIndex;
   *             // this ensures the ordering when we wrap around
   *             (*umbraIndices)[currUmbra] += fPathPolygon.size();
   *             currUmbra = nextUmbra;
   *             nextUmbra = (currUmbra + 1) % umbraPolygon.size();
   *         }
   *     }
   *     // finish up by advancing both one step
   *     fPositions.push_back(penumbraPolygon[nextPenumbra]);
   *     fColors.push_back(kPenumbraColor);
   *     int currPenumbraIndex = fPositions.size() - 1;
   *
   *     fPositions.push_back(umbraPolygon[nextUmbra]);
   *     fColors.push_back(kUmbraColor);
   *     int currUmbraIndex = fPositions.size() - 1;
   *     indexMap[nextUmbra] = currUmbraIndex;
   *
   *     this->appendQuad(prevPenumbraIndex, currPenumbraIndex,
   *                      fPrevUmbraIndex, currUmbraIndex);
   *
   *     if (fTransparent) {
   *         SkTriangulateSimplePolygon(umbraPolygon.begin(), indexMap, umbraPolygon.size(),
   *                                    &fIndices);
   *     }
   * }
   * ```
   */
  protected fun stitchConcaveRings(
    umbraPolygon: SkTDArray<SkPoint>,
    umbraIndices: SkTDArray<Int>?,
    penumbraPolygon: SkTDArray<SkPoint>,
    penumbraIndices: SkTDArray<Int>?,
  ) {
    TODO("Implement stitchConcaveRings")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleLine(const SkPoint& p) {
   *     SkPoint pSanitized;
   *     sanitize_point(p, &pSanitized);
   *
   *     if (!fPathPolygon.empty()) {
   *         if (!this->accumulateCentroid(fPathPolygon[fPathPolygon.size() - 1], pSanitized)) {
   *             // skip coincident point
   *             return;
   *         }
   *     }
   *
   *     if (fPathPolygon.size() > 1) {
   *         if (!checkConvexity(fPathPolygon[fPathPolygon.size() - 2],
   *                             fPathPolygon[fPathPolygon.size() - 1],
   *                             pSanitized)) {
   *             // remove collinear point
   *             fPathPolygon.pop_back();
   *             // it's possible that the previous point is coincident with the new one now
   *             if (duplicate_pt(fPathPolygon[fPathPolygon.size() - 1], pSanitized)) {
   *                 fPathPolygon.pop_back();
   *             }
   *         }
   *     }
   *
   *     fPathPolygon.push_back(pSanitized);
   * }
   * ```
   */
  protected fun handleLine(p: SkPoint) {
    TODO("Implement handleLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleLine(const SkMatrix& m, SkPoint* p) {
   *     *p = m.mapPoint(*p);
   *
   *     this->handleLine(*p);
   * }
   * ```
   */
  protected fun handleLine(m: SkMatrix, p: SkPoint?) {
    TODO("Implement handleLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleQuad(const SkPoint pts[3]) {
   * #if defined(SK_GANESH)
   *     // check for degeneracy
   *     SkVector v0 = pts[1] - pts[0];
   *     SkVector v1 = pts[2] - pts[0];
   *     if (SkScalarNearlyZero(v0.cross(v1))) {
   *         return;
   *     }
   *     // TODO: Pull PathUtils out of Ganesh?
   *     int maxCount = GrPathUtils::quadraticPointCount(pts, kQuadTolerance);
   *     fPointBuffer.resize(maxCount);
   *     SkPoint* target = fPointBuffer.begin();
   *     int count = GrPathUtils::generateQuadraticPoints(pts[0], pts[1], pts[2],
   *                                                      kQuadToleranceSqd, &target, maxCount);
   *     fPointBuffer.resize(count);
   *     for (int i = 0; i < count; i++) {
   *         this->handleLine(fPointBuffer[i]);
   *     }
   * #else
   *     // for now, just to draw something
   *     this->handleLine(pts[1]);
   *     this->handleLine(pts[2]);
   * #endif
   * }
   * ```
   */
  protected fun handleQuad(pts: Array<SkPoint>) {
    TODO("Implement handleQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleQuad(const SkMatrix& m, SkPoint pts[3]) {
   *     m.mapPoints({pts, 3});
   *     this->handleQuad(pts);
   * }
   * ```
   */
  protected fun handleQuad(m: SkMatrix, pts: Array<SkPoint>) {
    TODO("Implement handleQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleCubic(const SkMatrix& m, SkPoint pts[4]) {
   *     m.mapPoints({pts, 4});
   * #if defined(SK_GANESH)
   *     // TODO: Pull PathUtils out of Ganesh?
   *     int maxCount = GrPathUtils::cubicPointCount(pts, kCubicTolerance);
   *     fPointBuffer.resize(maxCount);
   *     SkPoint* target = fPointBuffer.begin();
   *     int count = GrPathUtils::generateCubicPoints(pts[0], pts[1], pts[2], pts[3],
   *                                                  kCubicToleranceSqd, &target, maxCount);
   *     fPointBuffer.resize(count);
   *     for (int i = 0; i < count; i++) {
   *         this->handleLine(fPointBuffer[i]);
   *     }
   * #else
   *     // for now, just to draw something
   *     this->handleLine(pts[1]);
   *     this->handleLine(pts[2]);
   *     this->handleLine(pts[3]);
   * #endif
   * }
   * ```
   */
  protected fun handleCubic(m: SkMatrix, pts: Array<SkPoint>) {
    TODO("Implement handleCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::handleConic(const SkMatrix& m, SkPoint pts[3], SkScalar w) {
   *     if (m.hasPerspective()) {
   *         w = SkConic::TransformW(pts, w, m);
   *     }
   *     m.mapPoints({pts, 3});
   *     SkAutoConicToQuads quadder;
   *     const SkPoint* quads = quadder.computeQuads(pts, w, kConicTolerance);
   *     SkPoint lastPoint = *(quads++);
   *     int count = quadder.countQuads();
   *     for (int i = 0; i < count; ++i) {
   *         SkPoint quadPts[3];
   *         quadPts[0] = lastPoint;
   *         quadPts[1] = quads[0];
   *         quadPts[2] = i == count - 1 ? pts[2] : quads[1];
   *         this->handleQuad(quadPts);
   *         lastPoint = quadPts[2];
   *         quads += 2;
   *     }
   * }
   * ```
   */
  protected fun handleConic(
    m: SkMatrix,
    pts: Array<SkPoint>,
    w: SkScalar,
  ) {
    TODO("Implement handleConic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBaseShadowTessellator::addArc(const SkVector& nextNormal, SkScalar offset, bool finishArc) {
   *     // fill in fan from previous quad
   *     SkScalar rotSin, rotCos;
   *     int numSteps;
   *     if (!SkComputeRadialSteps(fPrevOutset, nextNormal, offset, &rotSin, &rotCos, &numSteps)) {
   *         // recover as best we can
   *         numSteps = 0;
   *     }
   *     SkVector prevNormal = fPrevOutset;
   *     for (int i = 0; i < numSteps-1; ++i) {
   *         SkVector currNormal;
   *         currNormal.fX = prevNormal.fX*rotCos - prevNormal.fY*rotSin;
   *         currNormal.fY = prevNormal.fY*rotCos + prevNormal.fX*rotSin;
   *         fPositions.push_back(fPrevPoint + currNormal);
   *         fColors.push_back(kPenumbraColor);
   *         this->appendTriangle(fPrevUmbraIndex, fPositions.size() - 1, fPositions.size() - 2);
   *
   *         prevNormal = currNormal;
   *     }
   *     if (finishArc && numSteps) {
   *         fPositions.push_back(fPrevPoint + nextNormal);
   *         fColors.push_back(kPenumbraColor);
   *         this->appendTriangle(fPrevUmbraIndex, fPositions.size() - 1, fPositions.size() - 2);
   *     }
   *     fPrevOutset = nextNormal;
   *
   *     return (numSteps > 0);
   * }
   * ```
   */
  protected fun addArc(
    nextNormal: SkVector,
    offset: SkScalar,
    finishArc: Boolean,
  ): Boolean {
    TODO("Implement addArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::appendTriangle(uint16_t index0, uint16_t index1, uint16_t index2) {
   *     auto indices = fIndices.append(3);
   *
   *     indices[0] = index0;
   *     indices[1] = index1;
   *     indices[2] = index2;
   * }
   * ```
   */
  protected fun appendTriangle(
    index0: UShort,
    index1: UShort,
    index2: UShort,
  ) {
    TODO("Implement appendTriangle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBaseShadowTessellator::appendQuad(uint16_t index0, uint16_t index1,
   *                                          uint16_t index2, uint16_t index3) {
   *     auto indices = fIndices.append(6);
   *
   *     indices[0] = index0;
   *     indices[1] = index1;
   *     indices[2] = index2;
   *
   *     indices[3] = index2;
   *     indices[4] = index1;
   *     indices[5] = index3;
   * }
   * ```
   */
  protected fun appendQuad(
    index0: UShort,
    index1: UShort,
    index2: UShort,
    index3: UShort,
  ) {
    TODO("Implement appendQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar heightFunc(SkScalar x, SkScalar y) {
   *         return fZPlaneParams.fX*x + fZPlaneParams.fY*y + fZPlaneParams.fZ;
   *     }
   * ```
   */
  protected fun heightFunc(x: SkScalar, y: SkScalar): SkScalar {
    TODO("Implement heightFunc")
  }

  public companion object {
    protected val kMinHeight: Float = TODO("Initialize kMinHeight")

    protected val kPenumbraColor: SkColor = TODO("Initialize kPenumbraColor")

    protected val kUmbraColor: SkColor = TODO("Initialize kUmbraColor")
  }
}

public typealias SkAmbientShadowTessellatorINHERITED = SkBaseShadowTessellator

public typealias SkSpotShadowTessellatorINHERITED = SkBaseShadowTessellator
