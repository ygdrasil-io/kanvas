package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SkPathStroker {
 * public:
 *     SkPathStroker(const SkPath& src,
 *                   SkScalar radius, SkScalar miterLimit, SkPaint::Cap,
 *                   SkPaint::Join, SkScalar resScale,
 *                   bool canIgnoreCenter);
 *
 *     bool hasOnlyMoveTo() const { return 0 == fSegmentCount; }
 *     SkPoint moveToPt() const { return fFirstPt; }
 *
 *     void moveTo(const SkPoint&);
 *     void lineTo(const SkPoint&, const SkPath::Iter* iter = nullptr);
 *     void quadTo(const SkPoint&, const SkPoint&);
 *     void conicTo(const SkPoint&, const SkPoint&, SkScalar weight);
 *     void cubicTo(const SkPoint&, const SkPoint&, const SkPoint&);
 *     void close(bool isLine) { this->finishContour(true, isLine); }
 *
 *     void done(SkPathBuilder* dst, bool isLine) {
 *         this->finishContour(false, isLine);
 *         *dst = fOuter;
 *         fOuter.reset(); // is this needed? we used to "swap" it with dst
 *     }
 *
 *     SkScalar getResScale() const { return fResScale; }
 *
 *     bool isCurrentContourEmpty() const {
 *         return isZeroLengthSincePoint(fInner.points(), 0) &&
 *                isZeroLengthSincePoint(fOuter.points(), fFirstOuterPtIndexInContour);
 *     }
 *
 * private:
 *     SkScalar    fRadius;
 *     SkScalar    fInvMiterLimit;
 *     SkScalar    fResScale;
 *     SkScalar    fInvResScale;
 *     SkScalar    fInvResScaleSquared;
 *
 *     SkVector    fFirstNormal, fPrevNormal, fFirstUnitNormal, fPrevUnitNormal;
 *     SkPoint     fFirstPt, fPrevPt;  // on original path
 *     SkPoint     fFirstOuterPt;
 *     int         fFirstOuterPtIndexInContour;
 *     int         fSegmentCount;
 *     bool        fPrevIsLine;
 *     bool        fCanIgnoreCenter;
 *
 *     SkStrokerPriv::CapProc  fCapper;
 *     SkStrokerPriv::JoinProc fJoiner;
 *
 *     SkPathBuilder  fInner, fOuter, fCusper; // outer is our working answer, inner is temp
 *
 *     enum StrokeType {
 *         kOuter_StrokeType = 1,      // use sign-opposite values later to flip perpendicular axis
 *         kInner_StrokeType = -1
 *     } fStrokeType;
 *
 *     enum ResultType {
 *         kSplit_ResultType,          // the caller should split the quad stroke in two
 *         kDegenerate_ResultType,     // the caller should add a line
 *         kQuad_ResultType,           // the caller should (continue to try to) add a quad stroke
 *     };
 *
 *     enum ReductionType {
 *         kPoint_ReductionType,       // all curve points are practically identical
 *         kLine_ReductionType,        // the control point is on the line between the ends
 *         kQuad_ReductionType,        // the control point is outside the line between the ends
 *         kDegenerate_ReductionType,  // the control point is on the line but outside the ends
 *         kDegenerate2_ReductionType, // two control points are on the line but outside ends (cubic)
 *         kDegenerate3_ReductionType, // three areas of max curvature found (for cubic)
 *     };
 *
 *     enum IntersectRayType {
 *         kCtrlPt_RayType,
 *         kResultType_RayType,
 *     };
 *
 *     int fRecursionDepth;            // track stack depth to abort if numerics run amok
 *     bool fFoundTangents;            // do less work until tangents meet (cubic)
 *     bool fJoinCompleted;            // previous join was not degenerate
 *
 *     void addDegenerateLine(const SkQuadConstruct* );
 *     static ReductionType CheckConicLinear(const SkConic& , SkPoint* reduction);
 *     static ReductionType CheckCubicLinear(const SkPoint cubic[4], SkPoint reduction[3],
 *                                    const SkPoint** tanPtPtr);
 *     static ReductionType CheckQuadLinear(const SkPoint quad[3], SkPoint* reduction);
 *     ResultType compareQuadConic(const SkConic& , SkQuadConstruct* ) const;
 *     ResultType compareQuadCubic(const SkPoint cubic[4], SkQuadConstruct* );
 *     ResultType compareQuadQuad(const SkPoint quad[3], SkQuadConstruct* );
 *     void conicPerpRay(const SkConic& , SkScalar t, SkPoint* tPt, SkPoint* onPt,
 *                       SkVector* tangent) const;
 *     void conicQuadEnds(const SkConic& , SkQuadConstruct* ) const;
 *     bool conicStroke(const SkConic& , SkQuadConstruct* );
 *     bool cubicMidOnLine(const SkPoint cubic[4], const SkQuadConstruct* ) const;
 *     void cubicPerpRay(const SkPoint cubic[4], SkScalar t, SkPoint* tPt, SkPoint* onPt,
 *                       SkVector* tangent) const;
 *     void cubicQuadEnds(const SkPoint cubic[4], SkQuadConstruct* );
 *     void cubicQuadMid(const SkPoint cubic[4], const SkQuadConstruct* , SkPoint* mid) const;
 *     bool cubicStroke(const SkPoint cubic[4], SkQuadConstruct* );
 *     void init(StrokeType strokeType, SkQuadConstruct* , SkScalar tStart, SkScalar tEnd);
 *     ResultType intersectRay(SkQuadConstruct* , IntersectRayType  STROKER_DEBUG_PARAMS(int) ) const;
 *     bool ptInQuadBounds(const SkPoint quad[3], const SkPoint& pt) const;
 *     void quadPerpRay(const SkPoint quad[3], SkScalar t, SkPoint* tPt, SkPoint* onPt,
 *                      SkPoint* tangent) const;
 *     bool quadStroke(const SkPoint quad[3], SkQuadConstruct* );
 *     void setConicEndNormal(const SkConic& ,
 *                            const SkVector& normalAB, const SkVector& unitNormalAB,
 *                            SkVector* normalBC, SkVector* unitNormalBC);
 *     void setCubicEndNormal(const SkPoint cubic[4],
 *                            const SkVector& normalAB, const SkVector& unitNormalAB,
 *                            SkVector* normalCD, SkVector* unitNormalCD);
 *     void setQuadEndNormal(const SkPoint quad[3],
 *                           const SkVector& normalAB, const SkVector& unitNormalAB,
 *                           SkVector* normalBC, SkVector* unitNormalBC);
 *     void setRayPts(const SkPoint& tPt, SkVector* dxy, SkPoint* onPt, SkVector* tangent) const;
 *     static bool SlightAngle(SkQuadConstruct* );
 *     ResultType strokeCloseEnough(const SkPoint stroke[3], const SkPoint ray[2],
 *                                  SkQuadConstruct*  STROKER_DEBUG_PARAMS(int depth) ) const;
 *     ResultType tangentsMeet(const SkPoint cubic[4], SkQuadConstruct* );
 *
 *     void    finishContour(bool close, bool isLine);
 *     bool    preJoinTo(const SkPoint&, SkVector* normal, SkVector* unitNormal,
 *                       bool isLine);
 *     void    postJoinTo(const SkPoint&, const SkVector& normal,
 *                        const SkVector& unitNormal);
 *
 *     void    line_to(const SkPoint& currPt, const SkVector& normal);
 * }
 * ```
 */
public data class SkPathStroker public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fRadius
   * ```
   */
  private var fRadius: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fInvMiterLimit
   * ```
   */
  private var fInvMiterLimit: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fResScale
   * ```
   */
  private var fResScale: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fInvResScale
   * ```
   */
  private var fInvResScale: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fInvResScaleSquared
   * ```
   */
  private var fInvResScaleSquared: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkVector    fFirstNormal
   * ```
   */
  private var fFirstNormal: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector    fFirstNormal, fPrevNormal
   * ```
   */
  private var fPrevNormal: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector    fFirstNormal, fPrevNormal, fFirstUnitNormal
   * ```
   */
  private var fFirstUnitNormal: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector    fFirstNormal, fPrevNormal, fFirstUnitNormal, fPrevUnitNormal
   * ```
   */
  private var fPrevUnitNormal: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fFirstPt
   * ```
   */
  private var fFirstPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fFirstPt, fPrevPt
   * ```
   */
  private var fPrevPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fFirstOuterPt
   * ```
   */
  private var fFirstOuterPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * int         fFirstOuterPtIndexInContour
   * ```
   */
  private var fFirstOuterPtIndexInContour: Int,
  /**
   * C++ original:
   * ```cpp
   * int         fSegmentCount
   * ```
   */
  private var fSegmentCount: Int,
  /**
   * C++ original:
   * ```cpp
   * bool        fPrevIsLine
   * ```
   */
  private var fPrevIsLine: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool        fCanIgnoreCenter
   * ```
   */
  private var fCanIgnoreCenter: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkStrokerPriv::CapProc  fCapper
   * ```
   */
  private var fCapper: SkStrokerPrivCapProc,
  /**
   * C++ original:
   * ```cpp
   * SkStrokerPriv::JoinProc fJoiner
   * ```
   */
  private var fJoiner: SkStrokerPrivJoinProc,
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder  fInner
   * ```
   */
  private var fInner: SkPathBuilder,
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder  fInner, fOuter
   * ```
   */
  private var fOuter: SkPathBuilder,
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder  fInner, fOuter, fCusper
   * ```
   */
  private var fCusper: SkPathBuilder,
  /**
   * C++ original:
   * ```cpp
   * enum StrokeType {
   *         kOuter_StrokeType = 1,      // use sign-opposite values later to flip perpendicular axis
   *         kInner_StrokeType = -1
   *     } fStrokeType
   * ```
   */
  private var fStrokeType: StrokeType,
  /**
   * C++ original:
   * ```cpp
   * int fRecursionDepth
   * ```
   */
  private var fRecursionDepth: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fFoundTangents
   * ```
   */
  private var fFoundTangents: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fJoinCompleted
   * ```
   */
  private var fJoinCompleted: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool hasOnlyMoveTo() const { return 0 == fSegmentCount; }
   * ```
   */
  public fun hasOnlyMoveTo(): Boolean {
    TODO("Implement hasOnlyMoveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint moveToPt() const { return fFirstPt; }
   * ```
   */
  public fun moveToPt(): SkPoint {
    TODO("Implement moveToPt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::moveTo(const SkPoint& pt) {
   *     if (fSegmentCount > 0) {
   *         this->finishContour(false, false);
   *     }
   *     fSegmentCount = 0;
   *     fFirstPt = fPrevPt = pt;
   *     fJoinCompleted = false;
   * }
   * ```
   */
  public fun moveTo(pt: SkPoint) {
    TODO("Implement moveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::lineTo(const SkPoint& currPt, const SkPath::Iter* iter) {
   *     bool teenyLine = SkPointPriv::EqualsWithinTolerance(fPrevPt, currPt, SK_ScalarNearlyZero * fInvResScale);
   *     if (SkStrokerPriv::CapFactory(SkPaint::kButt_Cap) == fCapper && teenyLine) {
   *         return;
   *     }
   *     if (teenyLine && (fJoinCompleted || (iter && has_valid_tangent(iter)))) {
   *         return;
   *     }
   *     SkVector    normal, unitNormal;
   *
   *     if (!this->preJoinTo(currPt, &normal, &unitNormal, true)) {
   *         return;
   *     }
   *     this->line_to(currPt, normal);
   *     this->postJoinTo(currPt, normal, unitNormal);
   * }
   * ```
   */
  public fun lineTo(currPt: SkPoint, iter: SkPathIter? = TODO()) {
    TODO("Implement lineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::quadTo(const SkPoint& pt1, const SkPoint& pt2) {
   *     const SkPoint quad[3] = { fPrevPt, pt1, pt2 };
   *     SkPoint reduction;
   *     ReductionType reductionType = CheckQuadLinear(quad, &reduction);
   *     if (kPoint_ReductionType == reductionType) {
   *         /* If the stroke consists of a moveTo followed by a degenerate curve, treat it
   *             as if it were followed by a zero-length line. Lines without length
   *             can have square and round end caps. */
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     if (kLine_ReductionType == reductionType) {
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     if (kDegenerate_ReductionType == reductionType) {
   *         this->lineTo(reduction);
   *         SkStrokerPriv::JoinProc saveJoiner = fJoiner;
   *         fJoiner = SkStrokerPriv::JoinFactory(SkPaint::kRound_Join);
   *         this->lineTo(pt2);
   *         fJoiner = saveJoiner;
   *         return;
   *     }
   *     SkASSERT(kQuad_ReductionType == reductionType);
   *     SkVector normalAB, unitAB, normalBC, unitBC;
   *     if (!this->preJoinTo(pt1, &normalAB, &unitAB, false)) {
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     SkQuadConstruct quadPts;
   *     this->init(kOuter_StrokeType, &quadPts, 0, 1);
   *     (void) this->quadStroke(quad, &quadPts);
   *     this->init(kInner_StrokeType, &quadPts, 0, 1);
   *     (void) this->quadStroke(quad, &quadPts);
   *     this->setQuadEndNormal(quad, normalAB, unitAB, &normalBC, &unitBC);
   *
   *     this->postJoinTo(pt2, normalBC, unitBC);
   * }
   * ```
   */
  public fun quadTo(pt1: SkPoint, pt2: SkPoint) {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::conicTo(const SkPoint& pt1, const SkPoint& pt2, SkScalar weight) {
   *     const SkConic conic(fPrevPt, pt1, pt2, weight);
   *     SkPoint reduction;
   *     ReductionType reductionType = CheckConicLinear(conic, &reduction);
   *     if (kPoint_ReductionType == reductionType) {
   *         /* If the stroke consists of a moveTo followed by a degenerate curve, treat it
   *             as if it were followed by a zero-length line. Lines without length
   *             can have square and round end caps. */
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     if (kLine_ReductionType == reductionType) {
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     if (kDegenerate_ReductionType == reductionType) {
   *         this->lineTo(reduction);
   *         SkStrokerPriv::JoinProc saveJoiner = fJoiner;
   *         fJoiner = SkStrokerPriv::JoinFactory(SkPaint::kRound_Join);
   *         this->lineTo(pt2);
   *         fJoiner = saveJoiner;
   *         return;
   *     }
   *     SkASSERT(kQuad_ReductionType == reductionType);
   *     SkVector normalAB, unitAB, normalBC, unitBC;
   *     if (!this->preJoinTo(pt1, &normalAB, &unitAB, false)) {
   *         this->lineTo(pt2);
   *         return;
   *     }
   *     SkQuadConstruct quadPts;
   *     this->init(kOuter_StrokeType, &quadPts, 0, 1);
   *     (void) this->conicStroke(conic, &quadPts);
   *     this->init(kInner_StrokeType, &quadPts, 0, 1);
   *     (void) this->conicStroke(conic, &quadPts);
   *     this->setConicEndNormal(conic, normalAB, unitAB, &normalBC, &unitBC);
   *     this->postJoinTo(pt2, normalBC, unitBC);
   * }
   * ```
   */
  public fun conicTo(
    pt1: SkPoint,
    pt2: SkPoint,
    weight: SkScalar,
  ) {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::cubicTo(const SkPoint& pt1, const SkPoint& pt2,
   *                             const SkPoint& pt3) {
   *     const SkPoint cubic[4] = { fPrevPt, pt1, pt2, pt3 };
   *     SkPoint reduction[3];
   *     const SkPoint* tangentPt;
   *     ReductionType reductionType = CheckCubicLinear(cubic, reduction, &tangentPt);
   *     if (kPoint_ReductionType == reductionType) {
   *         /* If the stroke consists of a moveTo followed by a degenerate curve, treat it
   *             as if it were followed by a zero-length line. Lines without length
   *             can have square and round end caps. */
   *         this->lineTo(pt3);
   *         return;
   *     }
   *     if (kLine_ReductionType == reductionType) {
   *         this->lineTo(pt3);
   *         return;
   *     }
   *     if (kDegenerate_ReductionType <= reductionType && kDegenerate3_ReductionType >= reductionType) {
   *         this->lineTo(reduction[0]);
   *         SkStrokerPriv::JoinProc saveJoiner = fJoiner;
   *         fJoiner = SkStrokerPriv::JoinFactory(SkPaint::kRound_Join);
   *         if (kDegenerate2_ReductionType <= reductionType) {
   *             this->lineTo(reduction[1]);
   *         }
   *         if (kDegenerate3_ReductionType == reductionType) {
   *             this->lineTo(reduction[2]);
   *         }
   *         this->lineTo(pt3);
   *         fJoiner = saveJoiner;
   *         return;
   *     }
   *     SkASSERT(kQuad_ReductionType == reductionType);
   *     SkVector normalAB, unitAB, normalCD, unitCD;
   *     if (!this->preJoinTo(*tangentPt, &normalAB, &unitAB, false)) {
   *         this->lineTo(pt3);
   *         return;
   *     }
   *     SkScalar tValues[2];
   *     int count = SkFindCubicInflections(cubic, tValues);
   *     SkScalar lastT = 0;
   *     for (int index = 0; index <= count; ++index) {
   *         SkScalar nextT = index < count ? tValues[index] : 1;
   *         SkQuadConstruct quadPts;
   *         this->init(kOuter_StrokeType, &quadPts, lastT, nextT);
   *         (void) this->cubicStroke(cubic, &quadPts);
   *         this->init(kInner_StrokeType, &quadPts, lastT, nextT);
   *         (void) this->cubicStroke(cubic, &quadPts);
   *         lastT = nextT;
   *     }
   *     SkScalar cusp = SkFindCubicCusp(cubic);
   *     if (cusp > 0) {
   *         SkPoint cuspLoc;
   *         SkEvalCubicAt(cubic, cusp, &cuspLoc, nullptr, nullptr);
   *         fCusper.addCircle(cuspLoc.fX, cuspLoc.fY, fRadius);
   *     }
   *     // emit the join even if one stroke succeeded but the last one failed
   *     // this avoids reversing an inner stroke with a partial path followed by another moveto
   *     this->setCubicEndNormal(cubic, normalAB, unitAB, &normalCD, &unitCD);
   *
   *     this->postJoinTo(pt3, normalCD, unitCD);
   * }
   * ```
   */
  public fun cubicTo(
    pt1: SkPoint,
    pt2: SkPoint,
    pt3: SkPoint,
  ) {
    TODO("Implement cubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void close(bool isLine) { this->finishContour(true, isLine); }
   * ```
   */
  public fun close(isLine: Boolean) {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * void done(SkPathBuilder* dst, bool isLine) {
   *         this->finishContour(false, isLine);
   *         *dst = fOuter;
   *         fOuter.reset(); // is this needed? we used to "swap" it with dst
   *     }
   * ```
   */
  public fun done(dst: SkPathBuilder?, isLine: Boolean) {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getResScale() const { return fResScale; }
   * ```
   */
  public fun getResScale(): SkScalar {
    TODO("Implement getResScale")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCurrentContourEmpty() const {
   *         return isZeroLengthSincePoint(fInner.points(), 0) &&
   *                isZeroLengthSincePoint(fOuter.points(), fFirstOuterPtIndexInContour);
   *     }
   * ```
   */
  public fun isCurrentContourEmpty(): Boolean {
    TODO("Implement isCurrentContourEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::addDegenerateLine(const SkQuadConstruct* quadPts) {
   *     const SkPoint* quad = quadPts->fQuad;
   *     auto sink = fStrokeType == kOuter_StrokeType ? &fOuter : &fInner;
   *     sink->lineTo(quad[2]);
   * }
   * ```
   */
  private fun addDegenerateLine(quadPts: SkQuadConstruct?) {
    TODO("Implement addDegenerateLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::compareQuadConic(const SkConic& conic,
   *         SkQuadConstruct* quadPts) const {
   *     // get the quadratic approximation of the stroke
   *     this->conicQuadEnds(conic, quadPts);
   *     ResultType resultType = this->intersectRay(quadPts, kCtrlPt_RayType
   *             STROKER_DEBUG_PARAMS(fRecursionDepth) );
   *     if (resultType != kQuad_ResultType) {
   *         return resultType;
   *     }
   *     // project a ray from the curve to the stroke
   *     SkPoint ray[2];  // points near midpoint on quad, midpoint on conic
   *     this->conicPerpRay(conic, quadPts->fMidT, &ray[1], &ray[0], nullptr);
   *     return this->strokeCloseEnough(quadPts->fQuad, ray, quadPts
   *             STROKER_DEBUG_PARAMS(fRecursionDepth));
   * }
   * ```
   */
  private fun compareQuadConic(conic: SkConic, quadPts: SkQuadConstruct?): ResultType {
    TODO("Implement compareQuadConic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::compareQuadCubic(const SkPoint cubic[4],
   *         SkQuadConstruct* quadPts) {
   *     // get the quadratic approximation of the stroke
   *     this->cubicQuadEnds(cubic, quadPts);
   *     ResultType resultType = this->intersectRay(quadPts, kCtrlPt_RayType
   *             STROKER_DEBUG_PARAMS(fRecursionDepth) );
   *     if (resultType != kQuad_ResultType) {
   *         return resultType;
   *     }
   *     // project a ray from the curve to the stroke
   *     SkPoint ray[2];  // points near midpoint on quad, midpoint on cubic
   *     this->cubicPerpRay(cubic, quadPts->fMidT, &ray[1], &ray[0], nullptr);
   *     return this->strokeCloseEnough(quadPts->fQuad, ray, quadPts
   *             STROKER_DEBUG_PARAMS(fRecursionDepth));
   * }
   * ```
   */
  private fun compareQuadCubic(cubic: Array<SkPoint>, quadPts: SkQuadConstruct?): ResultType {
    TODO("Implement compareQuadCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::compareQuadQuad(const SkPoint quad[3],
   *         SkQuadConstruct* quadPts) {
   *     // get the quadratic approximation of the stroke
   *     if (!quadPts->fStartSet) {
   *         SkPoint quadStartPt;
   *         this->quadPerpRay(quad, quadPts->fStartT, &quadStartPt, &quadPts->fQuad[0],
   *                 &quadPts->fTangentStart);
   *         quadPts->fStartSet = true;
   *     }
   *     if (!quadPts->fEndSet) {
   *         SkPoint quadEndPt;
   *         this->quadPerpRay(quad, quadPts->fEndT, &quadEndPt, &quadPts->fQuad[2],
   *                 &quadPts->fTangentEnd);
   *         quadPts->fEndSet = true;
   *     }
   *     ResultType resultType = this->intersectRay(quadPts, kCtrlPt_RayType
   *             STROKER_DEBUG_PARAMS(fRecursionDepth));
   *     if (resultType != kQuad_ResultType) {
   *         return resultType;
   *     }
   *     // project a ray from the curve to the stroke
   *     SkPoint ray[2];
   *     this->quadPerpRay(quad, quadPts->fMidT, &ray[1], &ray[0], nullptr);
   *     return this->strokeCloseEnough(quadPts->fQuad, ray, quadPts
   *             STROKER_DEBUG_PARAMS(fRecursionDepth));
   * }
   * ```
   */
  private fun compareQuadQuad(quad: Array<SkPoint>, quadPts: SkQuadConstruct?): ResultType {
    TODO("Implement compareQuadQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::conicPerpRay(const SkConic& conic, SkScalar t, SkPoint* tPt, SkPoint* onPt,
   *         SkVector* tangent) const {
   *     SkVector dxy;
   *     conic.evalAt(t, tPt, &dxy);
   *     if (dxy.isZero()) {
   *         dxy = conic.fPts[2] - conic.fPts[0];
   *     }
   *     this->setRayPts(*tPt, &dxy, onPt, tangent);
   * }
   * ```
   */
  private fun conicPerpRay(
    conic: SkConic,
    t: SkScalar,
    tPt: SkPoint?,
    onPt: SkPoint?,
    tangent: SkVector?,
  ) {
    TODO("Implement conicPerpRay")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::conicQuadEnds(const SkConic& conic, SkQuadConstruct* quadPts) const {
   *     if (!quadPts->fStartSet) {
   *         SkPoint conicStartPt;
   *         this->conicPerpRay(conic, quadPts->fStartT, &conicStartPt, &quadPts->fQuad[0],
   *                 &quadPts->fTangentStart);
   *         quadPts->fStartSet = true;
   *     }
   *     if (!quadPts->fEndSet) {
   *         SkPoint conicEndPt;
   *         this->conicPerpRay(conic, quadPts->fEndT, &conicEndPt, &quadPts->fQuad[2],
   *                 &quadPts->fTangentEnd);
   *         quadPts->fEndSet = true;
   *     }
   * }
   * ```
   */
  private fun conicQuadEnds(conic: SkConic, quadPts: SkQuadConstruct?) {
    TODO("Implement conicQuadEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::conicStroke(const SkConic& conic, SkQuadConstruct* quadPts) {
   *     ResultType resultType = this->compareQuadConic(conic, quadPts);
   *     if (kQuad_ResultType == resultType) {
   *         const SkPoint* stroke = quadPts->fQuad;
   *         auto sink = fStrokeType == kOuter_StrokeType ? &fOuter : &fInner;
   *         sink->quadTo(stroke[1], stroke[2]);
   *         return true;
   *     }
   *     if (kDegenerate_ResultType == resultType) {
   *         addDegenerateLine(quadPts);
   *         return true;
   *     }
   * #if QUAD_STROKE_APPROX_EXTENDED_DEBUGGING
   *     SkDEBUGCODE(gMaxRecursion[kConic_RecursiveLimit] = std::max(gMaxRecursion[kConic_RecursiveLimit],
   *             fRecursionDepth + 1));
   * #endif
   *     if (++fRecursionDepth > kRecursiveLimits[kConic_RecursiveLimit]) {
   *         // If we stop making progress, just emit a line and move on
   *         addDegenerateLine(quadPts);
   *         return true;
   *     }
   *     SkQuadConstruct half;
   *     (void) half.initWithStart(quadPts);
   *     if (!this->conicStroke(conic, &half)) {
   *         return false;
   *     }
   *     (void) half.initWithEnd(quadPts);
   *     if (!this->conicStroke(conic, &half)) {
   *         return false;
   *     }
   *     --fRecursionDepth;
   *     return true;
   * }
   * ```
   */
  private fun conicStroke(conic: SkConic, quadPts: SkQuadConstruct?): Boolean {
    TODO("Implement conicStroke")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::cubicMidOnLine(const SkPoint cubic[4], const SkQuadConstruct* quadPts) const {
   *     SkPoint strokeMid;
   *     this->cubicQuadMid(cubic, quadPts, &strokeMid);
   *     SkScalar dist = pt_to_line(strokeMid, quadPts->fQuad[0], quadPts->fQuad[2]);
   *     return dist < fInvResScaleSquared;
   * }
   * ```
   */
  private fun cubicMidOnLine(cubic: Array<SkPoint>, quadPts: SkQuadConstruct?): Boolean {
    TODO("Implement cubicMidOnLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::cubicPerpRay(const SkPoint cubic[4], SkScalar t, SkPoint* tPt, SkPoint* onPt,
   *         SkVector* tangent) const {
   *     SkVector dxy;
   *     SkPoint chopped[7];
   *     SkEvalCubicAt(cubic, t, tPt, &dxy, nullptr);
   *     if (dxy.isZero()) {
   *         const SkPoint* cPts = cubic;
   *         if (SkScalarNearlyZero(t)) {
   *             dxy = cubic[2] - cubic[0];
   *         } else if (SkScalarNearlyZero(1 - t)) {
   *             dxy = cubic[3] - cubic[1];
   *         } else {
   *             // If the cubic inflection falls on the cusp, subdivide the cubic
   *             // to find the tangent at that point.
   *             SkChopCubicAt(cubic, chopped, t);
   *             dxy = chopped[3] - chopped[2];
   *             if (dxy.isZero()) {
   *                 dxy = chopped[3] - chopped[1];
   *                 cPts = chopped;
   *             }
   *         }
   *         if (dxy.isZero()) {
   *             dxy = cPts[3] - cPts[0];
   *         }
   *     }
   *     setRayPts(*tPt, &dxy, onPt, tangent);
   * }
   * ```
   */
  private fun cubicPerpRay(
    cubic: Array<SkPoint>,
    t: SkScalar,
    tPt: SkPoint?,
    onPt: SkPoint?,
    tangent: SkVector?,
  ) {
    TODO("Implement cubicPerpRay")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::cubicQuadEnds(const SkPoint cubic[4], SkQuadConstruct* quadPts) {
   *     if (!quadPts->fStartSet) {
   *         SkPoint cubicStartPt;
   *         this->cubicPerpRay(cubic, quadPts->fStartT, &cubicStartPt, &quadPts->fQuad[0],
   *                 &quadPts->fTangentStart);
   *         quadPts->fStartSet = true;
   *     }
   *     if (!quadPts->fEndSet) {
   *         SkPoint cubicEndPt;
   *         this->cubicPerpRay(cubic, quadPts->fEndT, &cubicEndPt, &quadPts->fQuad[2],
   *                 &quadPts->fTangentEnd);
   *         quadPts->fEndSet = true;
   *     }
   * }
   * ```
   */
  private fun cubicQuadEnds(cubic: Array<SkPoint>, quadPts: SkQuadConstruct?) {
    TODO("Implement cubicQuadEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::cubicQuadMid(const SkPoint cubic[4], const SkQuadConstruct* quadPts,
   *         SkPoint* mid) const {
   *     SkPoint cubicMidPt;
   *     this->cubicPerpRay(cubic, quadPts->fMidT, &cubicMidPt, mid, nullptr);
   * }
   * ```
   */
  private fun cubicQuadMid(
    cubic: Array<SkPoint>,
    quadPts: SkQuadConstruct?,
    mid: SkPoint?,
  ) {
    TODO("Implement cubicQuadMid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::cubicStroke(const SkPoint cubic[4], SkQuadConstruct* quadPts) {
   *     if (!fFoundTangents) {
   *         ResultType resultType = this->tangentsMeet(cubic, quadPts);
   *         if (kQuad_ResultType != resultType) {
   *             if ((kDegenerate_ResultType == resultType
   *                     || points_within_dist(quadPts->fQuad[0], quadPts->fQuad[2],
   *                     fInvResScale)) && cubicMidOnLine(cubic, quadPts)) {
   *                 addDegenerateLine(quadPts);
   *                 DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *                 return true;
   *             }
   *         } else {
   *             fFoundTangents = true;
   *         }
   *     }
   *     if (fFoundTangents) {
   *         ResultType resultType = this->compareQuadCubic(cubic, quadPts);
   *         if (kQuad_ResultType == resultType) {
   *             auto sink = fStrokeType == kOuter_StrokeType ? &fOuter : &fInner;
   *             const SkPoint* stroke = quadPts->fQuad;
   *             sink->quadTo(stroke[1], stroke[2]);
   *             DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *             return true;
   *         }
   *         if (kDegenerate_ResultType == resultType) {
   *             if (!quadPts->fOppositeTangents) {
   *               addDegenerateLine(quadPts);
   *               DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *               return true;
   *             }
   *         }
   *     }
   *     if (!quadPts->fQuad[2].isFinite()) {
   *         DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *         return false;  // just abort if projected quad isn't representable
   *     }
   * #if QUAD_STROKE_APPROX_EXTENDED_DEBUGGING
   *     SkDEBUGCODE(gMaxRecursion[fFoundTangents] = std::max(gMaxRecursion[fFoundTangents],
   *             fRecursionDepth + 1));
   * #endif
   *     if (++fRecursionDepth > kRecursiveLimits[fFoundTangents]) {
   *         DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *         // If we stop making progress, just emit a line and move on
   *         addDegenerateLine(quadPts);
   *         return true;
   *     }
   *     SkQuadConstruct half;
   *     if (!half.initWithStart(quadPts)) {
   *         addDegenerateLine(quadPts);
   *         DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *         --fRecursionDepth;
   *         return true;
   *     }
   *     if (!this->cubicStroke(cubic, &half)) {
   *         return false;
   *     }
   *     if (!half.initWithEnd(quadPts)) {
   *         addDegenerateLine(quadPts);
   *         DEBUG_CUBIC_RECURSION_TRACK_DEPTH(fRecursionDepth);
   *         --fRecursionDepth;
   *         return true;
   *     }
   *     if (!this->cubicStroke(cubic, &half)) {
   *         return false;
   *     }
   *     --fRecursionDepth;
   *     return true;
   * }
   * ```
   */
  private fun cubicStroke(cubic: Array<SkPoint>, quadPts: SkQuadConstruct?): Boolean {
    TODO("Implement cubicStroke")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::init(StrokeType strokeType, SkQuadConstruct* quadPts, SkScalar tStart,
   *         SkScalar tEnd) {
   *     fStrokeType = strokeType;
   *     fFoundTangents = false;
   *     quadPts->init(tStart, tEnd);
   * }
   * ```
   */
  private fun `init`(
    strokeType: StrokeType,
    quadPts: SkQuadConstruct?,
    tStart: SkScalar,
    tEnd: SkScalar,
  ) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::intersectRay(SkQuadConstruct* quadPts,
   *         IntersectRayType intersectRayType  STROKER_DEBUG_PARAMS(int depth)) const {
   *     const SkPoint& start = quadPts->fQuad[0];
   *     const SkPoint& end = quadPts->fQuad[2];
   *     SkVector aLen = quadPts->fTangentStart;
   *     SkVector bLen = quadPts->fTangentEnd;
   *     /* Slopes match when denom goes to zero:
   *                       axLen / ayLen ==                   bxLen / byLen
   *     (ayLen * byLen) * axLen / ayLen == (ayLen * byLen) * bxLen / byLen
   *              byLen  * axLen         ==  ayLen          * bxLen
   *              byLen  * axLen         -   ayLen          * bxLen         ( == denom )
   *      */
   *     SkScalar denom = aLen.cross(bLen);
   *     if (denom == 0 || !SkIsFinite(denom)) {
   *         quadPts->fOppositeTangents = aLen.dot(bLen) < 0;
   *         return STROKER_RESULT(kDegenerate_ResultType, depth, quadPts, "denom == 0");
   *     }
   *     quadPts->fOppositeTangents = false;
   *     SkVector ab0 = start - end;
   *     SkScalar numerA = bLen.cross(ab0);
   *     SkScalar numerB = aLen.cross(ab0);
   *     if ((numerA >= 0) == (numerB >= 0)) { // if the control point is outside the quad ends
   *         // if the perpendicular distances from the quad points to the opposite tangent line
   *         // are small, a straight line is good enough
   *         SkScalar dist1 = pt_to_tangent_line(start, end, quadPts->fTangentEnd);
   *         SkScalar dist2 = pt_to_tangent_line(end, start, quadPts->fTangentStart);
   *         if (std::max(dist1, dist2) <= fInvResScaleSquared) {
   *             return STROKER_RESULT(kDegenerate_ResultType, depth, quadPts,
   *                     "std::max(dist1=%g, dist2=%g) <= fInvResScaleSquared", dist1, dist2);
   *         }
   *         return STROKER_RESULT(kSplit_ResultType, depth, quadPts,
   *                 "(numerA=%g >= 0) == (numerB=%g >= 0)", numerA, numerB);
   *     }
   *     // check to see if the denominator is teeny relative to the numerator
   *     // if the offset by one will be lost, the ratio is too large
   *     numerA /= denom;
   *     bool validDivide = numerA > numerA - 1;
   *     if (validDivide) {
   *         if (kCtrlPt_RayType == intersectRayType) {
   *             SkPoint* ctrlPt = &quadPts->fQuad[1];
   *             // the intersection of the tangents need not be on the tangent segment
   *             // so 0 <= numerA <= 1 is not necessarily true
   *             *ctrlPt = start + quadPts->fTangentStart * numerA;
   *         }
   *         return STROKER_RESULT(kQuad_ResultType, depth, quadPts,
   *                 "(numerA=%g >= 0) != (numerB=%g >= 0)", numerA, numerB);
   *     }
   *     quadPts->fOppositeTangents = aLen.dot(bLen) < 0;
   *     // if the lines are parallel, straight line is good enough
   *     return STROKER_RESULT(kDegenerate_ResultType, depth, quadPts,
   *             "SkScalarNearlyZero(denom=%g)", denom);
   * }
   * ```
   */
  private fun intersectRay(quadPts: SkQuadConstruct?, param1: (Int) -> Any): ResultType {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::ptInQuadBounds(const SkPoint quad[3], const SkPoint& pt) const {
   *     SkScalar xMin = std::min({quad[0].fX, quad[1].fX, quad[2].fX});
   *     if (pt.fX + fInvResScale < xMin) {
   *         return false;
   *     }
   *     SkScalar xMax = std::max({quad[0].fX, quad[1].fX, quad[2].fX});
   *     if (pt.fX - fInvResScale > xMax) {
   *         return false;
   *     }
   *     SkScalar yMin = std::min({quad[0].fY, quad[1].fY, quad[2].fY});
   *     if (pt.fY + fInvResScale < yMin) {
   *         return false;
   *     }
   *     SkScalar yMax = std::max({quad[0].fY, quad[1].fY, quad[2].fY});
   *     if (pt.fY - fInvResScale > yMax) {
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  private fun ptInQuadBounds(quad: Array<SkPoint>, pt: SkPoint): Boolean {
    TODO("Implement ptInQuadBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::quadPerpRay(const SkPoint quad[3], SkScalar t, SkPoint* tPt, SkPoint* onPt,
   *         SkVector* tangent) const {
   *     SkVector dxy;
   *     SkEvalQuadAt(quad, t, tPt, &dxy);
   *     if (dxy.isZero()) {
   *         dxy = quad[2] - quad[0];
   *     }
   *     setRayPts(*tPt, &dxy, onPt, tangent);
   * }
   * ```
   */
  private fun quadPerpRay(
    quad: Array<SkPoint>,
    t: SkScalar,
    tPt: SkPoint?,
    onPt: SkPoint?,
    tangent: SkPoint?,
  ) {
    TODO("Implement quadPerpRay")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::quadStroke(const SkPoint quad[3], SkQuadConstruct* quadPts) {
   *     ResultType resultType = this->compareQuadQuad(quad, quadPts);
   *     if (kQuad_ResultType == resultType) {
   *         const SkPoint* stroke = quadPts->fQuad;
   *         auto sink = fStrokeType == kOuter_StrokeType ? &fOuter : &fInner;
   *         sink->quadTo(stroke[1], stroke[2]);
   *         return true;
   *     }
   *     if (kDegenerate_ResultType == resultType) {
   *         addDegenerateLine(quadPts);
   *         return true;
   *     }
   * #if QUAD_STROKE_APPROX_EXTENDED_DEBUGGING
   *     SkDEBUGCODE(gMaxRecursion[kQuad_RecursiveLimit] = std::max(gMaxRecursion[kQuad_RecursiveLimit],
   *             fRecursionDepth + 1));
   * #endif
   *     if (++fRecursionDepth > kRecursiveLimits[kQuad_RecursiveLimit]) {
   *         // If we stop making progress, just emit a line and move on
   *         addDegenerateLine(quadPts);
   *         return true;
   *     }
   *     SkQuadConstruct half;
   *     (void) half.initWithStart(quadPts);
   *     if (!this->quadStroke(quad, &half)) {
   *         return false;
   *     }
   *     (void) half.initWithEnd(quadPts);
   *     if (!this->quadStroke(quad, &half)) {
   *         return false;
   *     }
   *     --fRecursionDepth;
   *     return true;
   * }
   * ```
   */
  private fun quadStroke(quad: Array<SkPoint>, quadPts: SkQuadConstruct?): Boolean {
    TODO("Implement quadStroke")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::setConicEndNormal(const SkConic& conic, const SkVector& normalAB,
   *         const SkVector& unitNormalAB, SkVector* normalBC, SkVector* unitNormalBC) {
   *     setQuadEndNormal(conic.fPts, normalAB, unitNormalAB, normalBC, unitNormalBC);
   * }
   * ```
   */
  private fun setConicEndNormal(
    conic: SkConic,
    normalAB: SkVector,
    unitNormalAB: SkVector,
    normalBC: SkVector?,
    unitNormalBC: SkVector?,
  ) {
    TODO("Implement setConicEndNormal")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::setCubicEndNormal(const SkPoint cubic[4], const SkVector& normalAB,
   *         const SkVector& unitNormalAB, SkVector* normalCD, SkVector* unitNormalCD) {
   *     SkVector    ab = cubic[1] - cubic[0];
   *     SkVector    cd = cubic[3] - cubic[2];
   *
   *     bool    degenerateAB = degenerate_vector(ab);
   *     bool    degenerateCD = degenerate_vector(cd);
   *
   *     if (degenerateAB && degenerateCD) {
   *         goto DEGENERATE_NORMAL;
   *     }
   *
   *     if (degenerateAB) {
   *         ab = cubic[2] - cubic[0];
   *         degenerateAB = degenerate_vector(ab);
   *     }
   *     if (degenerateCD) {
   *         cd = cubic[3] - cubic[1];
   *         degenerateCD = degenerate_vector(cd);
   *     }
   *     if (degenerateAB || degenerateCD) {
   * DEGENERATE_NORMAL:
   *         *normalCD = normalAB;
   *         *unitNormalCD = unitNormalAB;
   *         return;
   *     }
   *     SkAssertResult(set_normal_unitnormal(cd, fRadius, normalCD, unitNormalCD));
   * }
   * ```
   */
  private fun setCubicEndNormal(
    cubic: Array<SkPoint>,
    normalAB: SkVector,
    unitNormalAB: SkVector,
    normalCD: SkVector?,
    unitNormalCD: SkVector?,
  ) {
    TODO("Implement setCubicEndNormal")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::setQuadEndNormal(const SkPoint quad[3], const SkVector& normalAB,
   *         const SkVector& unitNormalAB, SkVector* normalBC, SkVector* unitNormalBC) {
   *     if (!set_normal_unitnormal(quad[1], quad[2], fResScale, fRadius, normalBC, unitNormalBC)) {
   *         *normalBC = normalAB;
   *         *unitNormalBC = unitNormalAB;
   *     }
   * }
   * ```
   */
  private fun setQuadEndNormal(
    quad: Array<SkPoint>,
    normalAB: SkVector,
    unitNormalAB: SkVector,
    normalBC: SkVector?,
    unitNormalBC: SkVector?,
  ) {
    TODO("Implement setQuadEndNormal")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::setRayPts(const SkPoint& tPt, SkVector* dxy, SkPoint* onPt,
   *         SkVector* tangent) const {
   *     if (!dxy->setLength(fRadius)) {
   *         dxy->set(fRadius, 0);
   *     }
   *     SkScalar axisFlip = SkIntToScalar(fStrokeType);  // go opposite ways for outer, inner
   *     onPt->fX = tPt.fX + axisFlip * dxy->fY;
   *     onPt->fY = tPt.fY - axisFlip * dxy->fX;
   *     if (tangent) {
   *         *tangent = *dxy;
   *     }
   * }
   * ```
   */
  private fun setRayPts(
    tPt: SkPoint,
    dxy: SkVector?,
    onPt: SkPoint?,
    tangent: SkVector?,
  ) {
    TODO("Implement setRayPts")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::strokeCloseEnough(const SkPoint stroke[3],
   *         const SkPoint ray[2], SkQuadConstruct* quadPts  STROKER_DEBUG_PARAMS(int depth)) const {
   *     SkPoint strokeMid = SkEvalQuadAt(stroke, SK_ScalarHalf);
   *     // measure the distance from the curve to the quad-stroke midpoint, compare to radius
   *     if (points_within_dist(ray[0], strokeMid, fInvResScale)) {  // if the difference is small
   *         if (sharp_angle(quadPts->fQuad)) {
   *             return STROKER_RESULT(kSplit_ResultType, depth, quadPts,
   *                     "sharp_angle (1) =%g,%g, %g,%g, %g,%g",
   *                     quadPts->fQuad[0].fX, quadPts->fQuad[0].fY,
   *                     quadPts->fQuad[1].fX, quadPts->fQuad[1].fY,
   *                     quadPts->fQuad[2].fX, quadPts->fQuad[2].fY);
   *         }
   *         return STROKER_RESULT(kQuad_ResultType, depth, quadPts,
   *                 "points_within_dist(ray[0]=%g,%g, strokeMid=%g,%g, fInvResScale=%g)",
   *                 ray[0].fX, ray[0].fY, strokeMid.fX, strokeMid.fY, fInvResScale);
   *     }
   *     // measure the distance to quad's bounds (quick reject)
   *         // an alternative : look for point in triangle
   *     if (!ptInQuadBounds(stroke, ray[0])) {  // if far, subdivide
   *         return STROKER_RESULT(kSplit_ResultType, depth, quadPts,
   *                 "!pt_in_quad_bounds(stroke=(%g,%g %g,%g %g,%g), ray[0]=%g,%g)",
   *                 stroke[0].fX, stroke[0].fY, stroke[1].fX, stroke[1].fY, stroke[2].fX, stroke[2].fY,
   *                 ray[0].fX, ray[0].fY);
   *     }
   *     // measure the curve ray distance to the quad-stroke
   *     SkScalar roots[2];
   *     int rootCount = intersect_quad_ray(ray, stroke, roots);
   *     if (rootCount != 1) {
   *         return STROKER_RESULT(kSplit_ResultType, depth, quadPts,
   *                 "rootCount=%d != 1", rootCount);
   *     }
   *     SkPoint quadPt = SkEvalQuadAt(stroke, roots[0]);
   *     SkScalar error = fInvResScale * (SK_Scalar1 - SkScalarAbs(roots[0] - 0.5f) * 2);
   *     if (points_within_dist(ray[0], quadPt, error)) {  // if the difference is small, we're done
   *         if (sharp_angle(quadPts->fQuad)) {
   *             return STROKER_RESULT(kSplit_ResultType, depth, quadPts,
   *                     "sharp_angle (2) =%g,%g, %g,%g, %g,%g",
   *                     quadPts->fQuad[0].fX, quadPts->fQuad[0].fY,
   *                     quadPts->fQuad[1].fX, quadPts->fQuad[1].fY,
   *                     quadPts->fQuad[2].fX, quadPts->fQuad[2].fY);
   *         }
   *         return STROKER_RESULT(kQuad_ResultType, depth, quadPts,
   *                 "points_within_dist(ray[0]=%g,%g, quadPt=%g,%g, error=%g)",
   *                 ray[0].fX, ray[0].fY, quadPt.fX, quadPt.fY, error);
   *     }
   *     // otherwise, subdivide
   *     return STROKER_RESULT(kSplit_ResultType, depth, quadPts, "%s", "fall through");
   * }
   * ```
   */
  private fun strokeCloseEnough(
    stroke: Array<SkPoint>,
    ray: Array<SkPoint>,
    param2: (Int) -> Int,
  ): ResultType {
    TODO("Implement strokeCloseEnough")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathStroker::ResultType SkPathStroker::tangentsMeet(const SkPoint cubic[4],
   *         SkQuadConstruct* quadPts) {
   *     this->cubicQuadEnds(cubic, quadPts);
   *     return this->intersectRay(quadPts, kResultType_RayType  STROKER_DEBUG_PARAMS(fRecursionDepth));
   * }
   * ```
   */
  private fun tangentsMeet(cubic: Array<SkPoint>, quadPts: SkQuadConstruct?): ResultType {
    TODO("Implement tangentsMeet")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::finishContour(bool close, bool currIsLine) {
   *     if (fSegmentCount > 0) {
   *         if (close) {
   *             fJoiner(&fOuter, &fInner, fPrevUnitNormal, fPrevPt,
   *                     fFirstUnitNormal, fRadius, fInvMiterLimit,
   *                     fPrevIsLine, currIsLine);
   *             fOuter.close();
   *
   *             if (fCanIgnoreCenter) {
   *                 // If we can ignore the center just make sure the larger of the two paths
   *                 // is preserved and don't add the smaller one.
   *                 if (fInner.computeBounds().contains(fOuter.computeBounds())) {
   *                     fOuter = fInner;
   *                 }
   *             } else {
   *                 // now add fInner as its own contour
   *                 if (auto pt = fInner.getLastPt()) {
   *                     fOuter.moveTo(*pt);
   *                     fOuter.privateReversePathTo(fInner.detach()); // todo: take builder or raw
   *                     fOuter.close();
   *                 }
   *             }
   *         } else {    // add caps to start and end
   *             // cap the end
   *             if (auto pt = fInner.getLastPt()) {
   *                 fCapper(&fOuter, fPrevPt, fPrevNormal, *pt, currIsLine);
   *                 fOuter.privateReversePathTo(fInner.detach());
   *                 // cap the start
   *                 fCapper(&fOuter, fFirstPt, -fFirstNormal, fFirstOuterPt, fPrevIsLine);
   *                 fOuter.close();
   *             }
   *         }
   *         if (!fCusper.isEmpty()) {
   *             fOuter.addPath(fCusper.detach());
   *         }
   *     }
   *     fInner.reset();
   *     fSegmentCount = -1;
   *     fFirstOuterPtIndexInContour = fOuter.countPoints();
   * }
   * ```
   */
  private fun finishContour(close: Boolean, isLine: Boolean) {
    TODO("Implement finishContour")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathStroker::preJoinTo(const SkPoint& currPt, SkVector* normal,
   *                               SkVector* unitNormal, bool currIsLine) {
   *     SkASSERT(fSegmentCount >= 0);
   *
   *     if (!set_normal_unitnormal(fPrevPt, currPt, fResScale, fRadius, normal, unitNormal)) {
   *         if (SkStrokerPriv::CapFactory(SkPaint::kButt_Cap) == fCapper) {
   *             return false;
   *         }
   *         /* Square caps and round caps draw even if the segment length is zero.
   *            Since the zero length segment has no direction, set the orientation
   *            to upright as the default orientation */
   *         normal->set(fRadius, 0);
   *         unitNormal->set(1, 0);
   *     }
   *
   *     if (fSegmentCount == 0) {
   *         fFirstNormal = *normal;
   *         fFirstUnitNormal = *unitNormal;
   *         fFirstOuterPt = fPrevPt + *normal;
   *
   *         fOuter.moveTo(fFirstOuterPt);
   *         fInner.moveTo(fPrevPt - *normal);
   *     } else {    // we have a previous segment
   *         fJoiner(&fOuter, &fInner, fPrevUnitNormal, fPrevPt, *unitNormal,
   *                 fRadius, fInvMiterLimit, fPrevIsLine, currIsLine);
   *     }
   *     fPrevIsLine = currIsLine;
   *     return true;
   * }
   * ```
   */
  private fun preJoinTo(
    currPt: SkPoint,
    normal: SkVector?,
    unitNormal: SkVector?,
    isLine: Boolean,
  ): Boolean {
    TODO("Implement preJoinTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::postJoinTo(const SkPoint& currPt, const SkVector& normal,
   *                                const SkVector& unitNormal) {
   *     fJoinCompleted = true;
   *     fPrevPt = currPt;
   *     fPrevUnitNormal = unitNormal;
   *     fPrevNormal = normal;
   *     fSegmentCount += 1;
   * }
   * ```
   */
  private fun postJoinTo(
    currPt: SkPoint,
    normal: SkVector,
    unitNormal: SkVector,
  ) {
    TODO("Implement postJoinTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathStroker::line_to(const SkPoint& currPt, const SkVector& normal) {
   *     fOuter.lineTo(currPt + normal);
   *     fInner.lineTo(currPt - normal);
   * }
   * ```
   */
  private fun lineTo(currPt: SkPoint, normal: SkVector) {
    TODO("Implement lineTo")
  }

  public enum class StrokeType {
    kOuter_StrokeType,
    kInner_StrokeType,
  }

  public enum class ResultType {
    kSplit_ResultType,
    kDegenerate_ResultType,
    kQuad_ResultType,
  }

  public enum class ReductionType {
    kPoint_ReductionType,
    kLine_ReductionType,
    kQuad_ReductionType,
    kDegenerate_ReductionType,
    kDegenerate2_ReductionType,
    kDegenerate3_ReductionType,
  }

  public enum class IntersectRayType {
    kCtrlPt_RayType,
    kResultType_RayType,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkPathStroker::ReductionType SkPathStroker::CheckConicLinear(const SkConic& conic,
     *         SkPoint* reduction) {
     *     bool degenerateAB = degenerate_vector(conic.fPts[1] - conic.fPts[0]);
     *     bool degenerateBC = degenerate_vector(conic.fPts[2] - conic.fPts[1]);
     *     if (degenerateAB & degenerateBC) {
     *         return kPoint_ReductionType;
     *     }
     *     if (degenerateAB | degenerateBC) {
     *         return kLine_ReductionType;
     *     }
     *     if (!conic_in_line(conic)) {
     *         return kQuad_ReductionType;
     *     }
     *     // SkFindConicMaxCurvature would be a better solution, once we know how to
     *     // implement it. Quad curvature is a reasonable substitute
     *     SkScalar t = SkFindQuadMaxCurvature(conic.fPts);
     *     if (0 == t || SkIsNaN(t)) {
     *         return kLine_ReductionType;
     *     }
     *     conic.evalAt(t, reduction, nullptr);
     *     return kDegenerate_ReductionType;
     * }
     * ```
     */
    private fun checkConicLinear(conic: SkConic, reduction: SkPoint?): ReductionType {
      TODO("Implement checkConicLinear")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathStroker::ReductionType SkPathStroker::CheckCubicLinear(const SkPoint cubic[4],
     *         SkPoint reduction[3], const SkPoint** tangentPtPtr) {
     *     bool degenerateAB = degenerate_vector(cubic[1] - cubic[0]);
     *     bool degenerateBC = degenerate_vector(cubic[2] - cubic[1]);
     *     bool degenerateCD = degenerate_vector(cubic[3] - cubic[2]);
     *     if (degenerateAB & degenerateBC & degenerateCD) {
     *         return kPoint_ReductionType;
     *     }
     *     if (degenerateAB + degenerateBC + degenerateCD == 2) {
     *         return kLine_ReductionType;
     *     }
     *     if (!cubic_in_line(cubic)) {
     *         *tangentPtPtr = degenerateAB ? &cubic[2] : &cubic[1];
     *         return kQuad_ReductionType;
     *     }
     *     SkScalar tValues[3];
     *     int count = SkFindCubicMaxCurvature(cubic, tValues);
     *     int rCount = 0;
     *     // Now loop over the t-values, and reject any that evaluate to either end-point
     *     for (int index = 0; index < count; ++index) {
     *         SkScalar t = tValues[index];
     *         if (0 >= t || t >= 1) {
     *             continue;
     *         }
     *         SkEvalCubicAt(cubic, t, &reduction[rCount], nullptr, nullptr);
     *         if (reduction[rCount] != cubic[0] && reduction[rCount] != cubic[3]) {
     *             ++rCount;
     *         }
     *     }
     *     if (rCount == 0) {
     *         return kLine_ReductionType;
     *     }
     *     static_assert(kQuad_ReductionType + 1 == kDegenerate_ReductionType, "enum_out_of_whack");
     *     static_assert(kQuad_ReductionType + 2 == kDegenerate2_ReductionType, "enum_out_of_whack");
     *     static_assert(kQuad_ReductionType + 3 == kDegenerate3_ReductionType, "enum_out_of_whack");
     *
     *     return (ReductionType) (kQuad_ReductionType + rCount);
     * }
     * ```
     */
    private fun checkCubicLinear(
      cubic: Array<SkPoint>,
      reduction: Array<SkPoint>,
      tanPtPtr: Int?,
    ): ReductionType {
      TODO("Implement checkCubicLinear")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathStroker::ReductionType SkPathStroker::CheckQuadLinear(const SkPoint quad[3],
     *         SkPoint* reduction) {
     *     bool degenerateAB = degenerate_vector(quad[1] - quad[0]);
     *     bool degenerateBC = degenerate_vector(quad[2] - quad[1]);
     *     if (degenerateAB & degenerateBC) {
     *         return kPoint_ReductionType;
     *     }
     *     if (degenerateAB | degenerateBC) {
     *         return kLine_ReductionType;
     *     }
     *     if (!quad_in_line(quad)) {
     *         return kQuad_ReductionType;
     *     }
     *     SkScalar t = SkFindQuadMaxCurvature(quad);
     *     if (0 == t || 1 == t) {
     *         return kLine_ReductionType;
     *     }
     *     *reduction = SkEvalQuadAt(quad, t);
     *     return kDegenerate_ReductionType;
     * }
     * ```
     */
    private fun checkQuadLinear(quad: Array<SkPoint>, reduction: SkPoint?): ReductionType {
      TODO("Implement checkQuadLinear")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool SlightAngle(SkQuadConstruct* )
     * ```
     */
    private fun slightAngle(param0: SkQuadConstruct?): Boolean {
      TODO("Implement slightAngle")
    }
  }
}
