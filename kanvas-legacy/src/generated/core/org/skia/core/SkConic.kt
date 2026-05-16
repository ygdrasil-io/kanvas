package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SkConic {
 *     SkConic() {}
 *     SkConic(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2, SkScalar w) {
 *         this->set(p0, p1, p2, w);
 *     }
 *
 *     SkConic(const SkPoint pts[3], SkScalar w) {
 *         this->set(pts, w);
 *     }
 *
 *     SkPoint  fPts[3];
 *     SkScalar fW;
 *
 *     void set(const SkPoint pts[3], SkScalar w) {
 *         memcpy(fPts, pts, 3 * sizeof(SkPoint));
 *         this->setW(w);
 *     }
 *
 *     void set(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2, SkScalar w) {
 *         fPts[0] = p0;
 *         fPts[1] = p1;
 *         fPts[2] = p2;
 *         this->setW(w);
 *     }
 *
 *     void setW(SkScalar w) {
 *         if (SkIsFinite(w)) {
 *             SkASSERT(w > 0);
 *         }
 *
 *         // Guard against bad weights by forcing them to 1.
 *         fW = w > 0 && SkIsFinite(w) ? w : 1;
 *     }
 *
 *     /**
 *      *  Given a t-value [0...1] return its position and/or tangent.
 *      *  If pos is not null, return its position at the t-value.
 *      *  If tangent is not null, return its tangent at the t-value. NOTE the
 *      *  tangent value's length is arbitrary, and only its direction should
 *      *  be used.
 *      */
 *     void evalAt(SkScalar t, SkPoint* pos, SkVector* tangent = nullptr) const;
 *     [[nodiscard]] bool chopAt(SkScalar t, SkConic dst[2]) const;
 *     void chopAt(SkScalar t1, SkScalar t2, SkConic* dst) const;
 *     void chop(SkConic dst[2]) const;
 *
 *     SkPoint evalAt(SkScalar t) const;
 *     SkVector evalTangentAt(SkScalar t) const;
 *
 *     void computeAsQuadError(SkVector* err) const;
 *     bool asQuadTol(SkScalar tol) const;
 *
 *     /**
 *      *  return the power-of-2 number of quads needed to approximate this conic
 *      *  with a sequence of quads. Will be >= 0.
 *      */
 *     int SK_SPI computeQuadPOW2(SkScalar tol) const;
 *
 *     /**
 *      *  Chop this conic into N quads, stored continguously in pts[], where
 *      *  N = 1 << pow2. The amount of storage needed is (1 + 2 * N)
 *      */
 *     [[nodiscard]] int SK_SPI chopIntoQuadsPOW2(SkPoint pts[], int pow2) const;
 *
 *     float findMidTangent() const;
 *     bool findXExtrema(SkScalar* t) const;
 *     bool findYExtrema(SkScalar* t) const;
 *     bool chopAtXExtrema(SkConic dst[2]) const;
 *     bool chopAtYExtrema(SkConic dst[2]) const;
 *
 *     void computeTightBounds(SkRect* bounds) const;
 *     void computeFastBounds(SkRect* bounds) const;
 *
 *     /** Find the parameter value where the conic takes on its maximum curvature.
 *      *
 *      *  @param t   output scalar for max curvature.  Will be unchanged if
 *      *             max curvature outside 0..1 range.
 *      *
 *      *  @return  true if max curvature found inside 0..1 range, false otherwise
 *      */
 * //    bool findMaxCurvature(SkScalar* t) const;  // unimplemented
 *
 *     static SkScalar TransformW(const SkPoint[3], SkScalar w, const SkMatrix&);
 *
 *     enum {
 *         kMaxConicsForArc = 5
 *     };
 *     static int BuildUnitArc(const SkVector& start, const SkVector& stop, SkPathDirection,
 *                             const SkMatrix*, SkConic conics[kMaxConicsForArc]);
 * }
 * ```
 */
public data class SkConic public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fPts[3]
   * ```
   */
  public var fPts: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fW
   * ```
   */
  public var fW: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(const SkPoint pts[3], SkScalar w) {
   *         memcpy(fPts, pts, 3 * sizeof(SkPoint));
   *         this->setW(w);
   *     }
   * ```
   */
  public fun `set`(pts: Array<SkPoint>, w: SkScalar) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2, SkScalar w) {
   *         fPts[0] = p0;
   *         fPts[1] = p1;
   *         fPts[2] = p2;
   *         this->setW(w);
   *     }
   * ```
   */
  public fun `set`(
    p0: SkPoint,
    p1: SkPoint,
    p2: SkPoint,
    w: SkScalar,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void setW(SkScalar w) {
   *         if (SkIsFinite(w)) {
   *             SkASSERT(w > 0);
   *         }
   *
   *         // Guard against bad weights by forcing them to 1.
   *         fW = w > 0 && SkIsFinite(w) ? w : 1;
   *     }
   * ```
   */
  public fun setW(w: SkScalar) {
    TODO("Implement setW")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::evalAt(SkScalar t, SkPoint* pt, SkVector* tangent) const {
   *     SkASSERT(t >= 0 && t <= SK_Scalar1);
   *
   *     if (pt) {
   *         *pt = this->evalAt(t);
   *     }
   *     if (tangent) {
   *         *tangent = this->evalTangentAt(t);
   *     }
   * }
   * ```
   */
  public fun evalAt(
    t: SkScalar,
    pos: SkPoint?,
    tangent: SkVector? = TODO(),
  ) {
    TODO("Implement evalAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::chopAt(SkScalar t, SkConic dst[2]) const {
   *     SkPoint3 tmp[3], tmp2[3];
   *
   *     ratquad_mapTo3D(fPts, fW, tmp);
   *
   *     p3d_interp(&tmp[0].fX, &tmp2[0].fX, t);
   *     p3d_interp(&tmp[0].fY, &tmp2[0].fY, t);
   *     p3d_interp(&tmp[0].fZ, &tmp2[0].fZ, t);
   *
   *     dst[0].fPts[0] = fPts[0];
   *     dst[0].fPts[1] = project_down(tmp2[0]);
   *     dst[0].fPts[2] = project_down(tmp2[1]); dst[1].fPts[0] = dst[0].fPts[2];
   *     dst[1].fPts[1] = project_down(tmp2[2]);
   *     dst[1].fPts[2] = fPts[2];
   *
   *     // to put in "standard form", where w0 and w2 are both 1, we compute the
   *     // new w1 as sqrt(w1*w1/w0*w2)
   *     // or
   *     // w1 /= sqrt(w0*w2)
   *     //
   *     // However, in our case, we know that for dst[0]:
   *     //     w0 == 1, and for dst[1], w2 == 1
   *     //
   *     SkScalar root = SkScalarSqrt(tmp2[1].fZ);
   *     dst[0].fW = tmp2[0].fZ / root;
   *     dst[1].fW = tmp2[2].fZ / root;
   *     SkASSERT(sizeof(dst[0]) == sizeof(SkScalar) * 7);
   *     SkASSERT(0 == offsetof(SkConic, fPts[0].fX));
   *     return SkIsFinite(&dst[0].fPts[0].fX, 7 * 2);
   * }
   * ```
   */
  public fun chopAt(t: SkScalar, dst: Array<SkConic>): Boolean {
    TODO("Implement chopAt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::chopAt(SkScalar t1, SkScalar t2, SkConic* dst) const {
   *     if (0 == t1 || 1 == t2) {
   *         if (0 == t1 && 1 == t2) {
   *             *dst = *this;
   *             return;
   *         } else {
   *             SkConic pair[2];
   *             if (this->chopAt(t1 ? t1 : t2, pair)) {
   *                 *dst = pair[SkToBool(t1)];
   *                 return;
   *             }
   *         }
   *     }
   *     SkConicCoeff coeff(*this);
   *     float2 tt1(t1);
   *     float2 aXY = coeff.fNumer.eval(tt1);
   *     float2 aZZ = coeff.fDenom.eval(tt1);
   *     float2 midTT((t1 + t2) / 2);
   *     float2 dXY = coeff.fNumer.eval(midTT);
   *     float2 dZZ = coeff.fDenom.eval(midTT);
   *     float2 tt2(t2);
   *     float2 cXY = coeff.fNumer.eval(tt2);
   *     float2 cZZ = coeff.fDenom.eval(tt2);
   *     float2 bXY = times_2(dXY) - (aXY + cXY) * 0.5f;
   *     float2 bZZ = times_2(dZZ) - (aZZ + cZZ) * 0.5f;
   *     dst->fPts[0] = to_point(aXY / aZZ);
   *     dst->fPts[1] = to_point(bXY / bZZ);
   *     dst->fPts[2] = to_point(cXY / cZZ);
   *     float2 ww = bZZ / sqrt(aZZ * cZZ);
   *     dst->fW = ww[0];
   * }
   * ```
   */
  public fun chopAt(
    t1: SkScalar,
    t2: SkScalar,
    dst: SkConic?,
  ) {
    TODO("Implement chopAt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::chop(SkConic * SK_RESTRICT dst) const {
   *
   *     // Observe that scale will always be smaller than 1 because fW > 0.
   *     const float scale = SkScalarInvert(SK_Scalar1 + fW);
   *
   *     // The subdivided control points below are the sums of the following three terms. Because the
   *     // terms are multiplied by something <1, and the resulting control points lie within the
   *     // control points of the original then the terms and the sums below will not overflow. Note
   *     // that fW * scale approaches 1 as fW becomes very large.
   *     float2 t0 = from_point(fPts[0]) * scale;
   *     float2 t1 = from_point(fPts[1]) * (fW * scale);
   *     float2 t2 = from_point(fPts[2]) * scale;
   *
   *     // Calculate the subdivided control points
   *     const SkPoint p1 = to_point(t0 + t1);
   *     const SkPoint p3 = to_point(t1 + t2);
   *
   *     // p2 = (t0 + 2*t1 + t2) / 2. Divide the terms by 2 before the sum to keep the sum for p2
   *     // from overflowing.
   *     const SkPoint p2 = to_point(0.5f * t0 + t1 + 0.5f * t2);
   *
   *     SkASSERT(p1.isFinite() && p2.isFinite() && p3.isFinite());
   *
   *     dst[0].fPts[0] = fPts[0];
   *     dst[0].fPts[1] = p1;
   *     dst[0].fPts[2] = p2;
   *     dst[1].fPts[0] = p2;
   *     dst[1].fPts[1] = p3;
   *     dst[1].fPts[2] = fPts[2];
   *
   *     // Update w.
   *     dst[0].fW = dst[1].fW = subdivide_w_value(fW);
   * }
   * ```
   */
  public fun chop(dst: Array<SkConic>) {
    TODO("Implement chop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint SkConic::evalAt(SkScalar t) const {
   *     return to_point(SkConicCoeff(*this).eval(t));
   * }
   * ```
   */
  public fun evalAt(t: SkScalar): SkPoint {
    TODO("Implement evalAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector SkConic::evalTangentAt(SkScalar t) const {
   *     // The derivative equation returns a zero tangent vector when t is 0 or 1,
   *     // and the control point is equal to the end point.
   *     // In this case, use the conic endpoints to compute the tangent.
   *     if ((t == 0 && fPts[0] == fPts[1]) || (t == 1 && fPts[1] == fPts[2])) {
   *         return fPts[2] - fPts[0];
   *     }
   *     float2 p0 = from_point(fPts[0]);
   *     float2 p1 = from_point(fPts[1]);
   *     float2 p2 = from_point(fPts[2]);
   *     float2 ww(fW);
   *
   *     float2 p20 = p2 - p0;
   *     float2 p10 = p1 - p0;
   *
   *     float2 C = ww * p10;
   *     float2 A = ww * p20 - p20;
   *     float2 B = p20 - C - C;
   *
   *     return to_vector(SkQuadCoeff(A, B, C).eval(t));
   * }
   * ```
   */
  public fun evalTangentAt(t: SkScalar): SkVector {
    TODO("Implement evalTangentAt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::computeAsQuadError(SkVector* err) const {
   *     AS_QUAD_ERROR_SETUP
   *     err->set(x, y);
   * }
   * ```
   */
  public fun computeAsQuadError(err: SkVector?) {
    TODO("Implement computeAsQuadError")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::asQuadTol(SkScalar tol) const {
   *     AS_QUAD_ERROR_SETUP
   *     return (x * x + y * y) <= tol * tol;
   * }
   * ```
   */
  public fun asQuadTol(tol: SkScalar): Boolean {
    TODO("Implement asQuadTol")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkConic::computeQuadPOW2(SkScalar tol) const {
   *     if (tol < 0 || !SkIsFinite(tol) || !SkPointPriv::AreFinite(fPts, 3) || bad_conic_w(fW)) {
   *         return 0;
   *     }
   *
   *     AS_QUAD_ERROR_SETUP
   *
   *     SkScalar error = SkScalarSqrt(x * x + y * y);
   *     int pow2;
   *     for (pow2 = 0; pow2 < kMaxConicToQuadPOW2; ++pow2) {
   *         if (error <= tol) {
   *             break;
   *         }
   *         error *= 0.25f;
   *     }
   *     // float version -- using ceil gives the same results as the above.
   *     if ((false)) {
   *         SkScalar err = SkScalarSqrt(x * x + y * y);
   *         if (err <= tol) {
   *             return 0;
   *         }
   *         SkScalar tol2 = tol * tol;
   *         if (tol2 == 0) {
   *             return kMaxConicToQuadPOW2;
   *         }
   *         SkScalar fpow2 = SkScalarLog2((x * x + y * y) / tol2) * 0.25f;
   *         int altPow2 = SkScalarCeilToInt(fpow2);
   *         if (altPow2 != pow2) {
   *             SkDebugf("pow2 %d altPow2 %d fbits %g err %g tol %g\n", pow2, altPow2, fpow2, err, tol);
   *         }
   *         pow2 = altPow2;
   *     }
   *     return pow2;
   * }
   * ```
   */
  public fun computeQuadPOW2(tol: SkScalar): Int {
    TODO("Implement computeQuadPOW2")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkConic::chopIntoQuadsPOW2(SkPoint pts[], int pow2) const {
   *     SkASSERT(pow2 >= 0 && pow2 <= kMaxConicToQuadPOW2);
   *
   *     if (bad_conic_w(fW)) {
   *         pow2 = 0;
   *     }
   *
   *     *pts = fPts[0];
   *     SkDEBUGCODE(SkPoint* endPts);
   *     if (pow2 == kMaxConicToQuadPOW2) {  // If an extreme weight generates many quads ...
   *         SkConic dst[2];
   *         this->chop(dst);
   *         // check to see if the first chop generates a pair of lines
   *         if (SkPointPriv::EqualsWithinTolerance(dst[0].fPts[1], dst[0].fPts[2]) &&
   *                 SkPointPriv::EqualsWithinTolerance(dst[1].fPts[0], dst[1].fPts[1])) {
   *             pts[1] = pts[2] = pts[3] = dst[0].fPts[1];  // set ctrl == end to make lines
   *             pts[4] = dst[1].fPts[2];
   *             pow2 = 1;
   *             SkDEBUGCODE(endPts = &pts[5]);
   *             goto commonFinitePtCheck;
   *         }
   *     }
   *     SkDEBUGCODE(endPts = ) subdivide(*this, pts + 1, pow2);
   * commonFinitePtCheck:
   *     const int quadCount = 1 << pow2;
   *     const int ptCount = 2 * quadCount + 1;
   *     SkASSERT(endPts - pts == ptCount);
   *     if (!SkPointPriv::AreFinite(pts, ptCount)) {
   *         // if we generated a non-finite, pin ourselves to the middle of the hull,
   *         // as our first and last are already on the first/last pts of the hull.
   *         for (int i = 1; i < ptCount - 1; ++i) {
   *             pts[i] = fPts[1];
   *         }
   *     }
   *     return 1 << pow2;
   * }
   * ```
   */
  public fun chopIntoQuadsPOW2(pts: Array<SkPoint>, pow2: Int): Int {
    TODO("Implement chopIntoQuadsPOW2")
  }

  /**
   * C++ original:
   * ```cpp
   * float SkConic::findMidTangent() const {
   *     // Tangents point in the direction of increasing T, so tan0 and -tan1 both point toward the
   *     // midtangent. The bisector of tan0 and -tan1 is orthogonal to the midtangent:
   *     //
   *     //     bisector dot midtangent = 0
   *     //
   *     SkVector tan0 = fPts[1] - fPts[0];
   *     SkVector tan1 = fPts[2] - fPts[1];
   *     SkVector bisector = SkFindBisector(tan0, -tan1);
   *
   *     // Start by finding the tangent function's power basis coefficients. These define a tangent
   *     // direction (scaled by some uniform value) as:
   *     //                                                |T^2|
   *     //     Tangent_Direction(T) = dx,dy = |A  B  C| * |T  |
   *     //                                    |.  .  .|   |1  |
   *     //
   *     // The derivative of a conic has a cumbersome order-4 denominator. However, this isn't necessary
   *     // if we are only interested in a vector in the same *direction* as a given tangent line. Since
   *     // the denominator scales dx and dy uniformly, we can throw it out completely after evaluating
   *     // the derivative with the standard quotient rule. This leaves us with a simpler quadratic
   *     // function that we use to find a tangent.
   *     SkVector A = (fPts[2] - fPts[0]) * (fW - 1);
   *     SkVector B = (fPts[2] - fPts[0]) - (fPts[1] - fPts[0]) * (fW*2);
   *     SkVector C = (fPts[1] - fPts[0]) * fW;
   *
   *     // Now solve for "bisector dot midtangent = 0":
   *     //
   *     //                            |T^2|
   *     //     bisector * |A  B  C| * |T  | = 0
   *     //                |.  .  .|   |1  |
   *     //
   *     float a = bisector.dot(A);
   *     float b = bisector.dot(B);
   *     float c = bisector.dot(C);
   *     return solve_quadratic_equation_for_midtangent(a, b, c);
   * }
   * ```
   */
  public fun findMidTangent(): Float {
    TODO("Implement findMidTangent")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::findXExtrema(SkScalar* t) const {
   *     return conic_find_extrema(&fPts[0].fX, fW, t);
   * }
   * ```
   */
  public fun findXExtrema(t: SkScalar?): Boolean {
    TODO("Implement findXExtrema")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::findYExtrema(SkScalar* t) const {
   *     return conic_find_extrema(&fPts[0].fY, fW, t);
   * }
   * ```
   */
  public fun findYExtrema(t: SkScalar?): Boolean {
    TODO("Implement findYExtrema")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::chopAtXExtrema(SkConic dst[2]) const {
   *     SkScalar t;
   *     if (this->findXExtrema(&t)) {
   *         if (!this->chopAt(t, dst)) {
   *             // if chop can't return finite values, don't chop
   *             return false;
   *         }
   *         // now clean-up the middle, since we know t was meant to be at
   *         // an X-extrema
   *         SkScalar value = dst[0].fPts[2].fX;
   *         dst[0].fPts[1].fX = value;
   *         dst[1].fPts[0].fX = value;
   *         dst[1].fPts[1].fX = value;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun chopAtXExtrema(dst: Array<SkConic>): Boolean {
    TODO("Implement chopAtXExtrema")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConic::chopAtYExtrema(SkConic dst[2]) const {
   *     SkScalar t;
   *     if (this->findYExtrema(&t)) {
   *         if (!this->chopAt(t, dst)) {
   *             // if chop can't return finite values, don't chop
   *             return false;
   *         }
   *         // now clean-up the middle, since we know t was meant to be at
   *         // an Y-extrema
   *         SkScalar value = dst[0].fPts[2].fY;
   *         dst[0].fPts[1].fY = value;
   *         dst[1].fPts[0].fY = value;
   *         dst[1].fPts[1].fY = value;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun chopAtYExtrema(dst: Array<SkConic>): Boolean {
    TODO("Implement chopAtYExtrema")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::computeTightBounds(SkRect* bounds) const {
   *     SkPoint pts[4];
   *     pts[0] = fPts[0];
   *     pts[1] = fPts[2];
   *     size_t count = 2;
   *
   *     SkScalar t;
   *     if (this->findXExtrema(&t)) {
   *         this->evalAt(t, &pts[count++]);
   *     }
   *     if (this->findYExtrema(&t)) {
   *         this->evalAt(t, &pts[count++]);
   *     }
   *     *bounds = SkRect::BoundsOrEmpty({pts, count});
   * }
   * ```
   */
  public fun computeTightBounds(bounds: SkRect?) {
    TODO("Implement computeTightBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConic::computeFastBounds(SkRect* bounds) const {
   *     *bounds = SkRect::BoundsOrEmpty(fPts);
   * }
   * ```
   */
  public fun computeFastBounds(bounds: SkRect?) {
    TODO("Implement computeFastBounds")
  }

  public companion object {
    public val kMaxConicsForArc: Int = TODO("Initialize kMaxConicsForArc")

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkConic::TransformW(const SkPoint pts[3], SkScalar w, const SkMatrix& matrix) {
     *     if (!matrix.hasPerspective()) {
     *         return w;
     *     }
     *
     *     SkPoint3 src[3], dst[3];
     *
     *     ratquad_mapTo3D(pts, w, src);
     *
     *     matrix.mapHomogeneousPoints(dst, src);
     *
     *     // w' = sqrt(w1*w1/w0*w2)
     *     // use doubles temporarily, to handle small numer/denom
     *     double w0 = dst[0].fZ;
     *     double w1 = dst[1].fZ;
     *     double w2 = dst[2].fZ;
     *     return sk_double_to_float(sqrt(sk_ieee_double_divide(w1 * w1, w0 * w2)));
     * }
     * ```
     */
    public fun transformW(
      pts: Array<SkPoint>,
      w: SkScalar,
      matrix: SkMatrix,
    ): SkScalar {
      TODO("Implement transformW")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkConic::BuildUnitArc(const SkVector& uStart, const SkVector& uStop, SkPathDirection dir,
     *                           const SkMatrix* userMatrix, SkConic dst[kMaxConicsForArc]) {
     *     // rotate by x,y so that uStart is (1.0)
     *     SkScalar x = SkPoint::DotProduct(uStart, uStop);
     *     SkScalar y = SkPoint::CrossProduct(uStart, uStop);
     *
     *     SkScalar absY = SkScalarAbs(y);
     *
     *     // check for (effectively) coincident vectors
     *     // this can happen if our angle is nearly 0 or nearly 180 (y == 0)
     *     // ... we use the dot-prod to distinguish between 0 and 180 (x > 0)
     *     if (absY <= SK_ScalarNearlyZero && x > 0 && ((y >= 0 && SkPathDirection::kCW == dir) ||
     *                                                  (y <= 0 && SkPathDirection::kCCW == dir))) {
     *         return 0;
     *     }
     *
     *     if (dir == SkPathDirection::kCCW) {
     *         y = -y;
     *     }
     *
     *     // We decide to use 1-conic per quadrant of a circle. What quadrant does [xy] lie in?
     *     //      0 == [0  .. 90)
     *     //      1 == [90 ..180)
     *     //      2 == [180..270)
     *     //      3 == [270..360)
     *     //
     *     int quadrant = 0;
     *     if (0 == y) {
     *         quadrant = 2;        // 180
     *         SkASSERT(SkScalarAbs(x + SK_Scalar1) <= SK_ScalarNearlyZero);
     *     } else if (0 == x) {
     *         SkASSERT(absY - SK_Scalar1 <= SK_ScalarNearlyZero);
     *         quadrant = y > 0 ? 1 : 3; // 90 : 270
     *     } else {
     *         if (y < 0) {
     *             quadrant += 2;
     *         }
     *         if ((x < 0) != (y < 0)) {
     *             quadrant += 1;
     *         }
     *     }
     *
     *     const SkPoint quadrantPts[] = {
     *         { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
     *     };
     *     const SkScalar quadrantWeight = SK_ScalarRoot2Over2;
     *
     *     int conicCount = quadrant;
     *     for (int i = 0; i < conicCount; ++i) {
     *         dst[i].set(&quadrantPts[i * 2], quadrantWeight);
     *     }
     *
     *     // Now compute any remaing (sub-90-degree) arc for the last conic
     *     const SkPoint finalP = { x, y };
     *     const SkPoint& lastQ = quadrantPts[quadrant * 2];  // will already be a unit-vector
     *     const SkScalar dot = SkVector::DotProduct(lastQ, finalP);
     *     if (SkIsNaN(dot)) {
     *         return 0;
     *     }
     *     SkASSERT(0 <= dot && dot <= SK_Scalar1 + SK_ScalarNearlyZero);
     *
     *     if (dot < 1) {
     *         SkVector offCurve = { lastQ.x() + x, lastQ.y() + y };
     *         // compute the bisector vector, and then rescale to be the off-curve point.
     *         // we compute its length from cos(theta/2) = length / 1, using half-angle identity we get
     *         // length = sqrt(2 / (1 + cos(theta)). We already have cos() when to computed the dot.
     *         // This is nice, since our computed weight is cos(theta/2) as well!
     *         //
     *         const SkScalar cosThetaOver2 = SkScalarSqrt((1 + dot) / 2);
     *         offCurve.setLength(SkScalarInvert(cosThetaOver2));
     *         if (!SkPointPriv::EqualsWithinTolerance(lastQ, offCurve)) {
     *             dst[conicCount].set(lastQ, offCurve, finalP, cosThetaOver2);
     *             conicCount += 1;
     *         }
     *     }
     *
     *     // now handle counter-clockwise and the initial unitStart rotation
     *     SkMatrix    matrix;
     *     matrix.setSinCos(uStart.fY, uStart.fX);
     *     if (dir == SkPathDirection::kCCW) {
     *         matrix.preScale(SK_Scalar1, -SK_Scalar1);
     *     }
     *     if (userMatrix) {
     *         matrix.postConcat(*userMatrix);
     *     }
     *     for (int i = 0; i < conicCount; ++i) {
     *         matrix.mapPoints(dst[i].fPts);
     *     }
     *     return conicCount;
     * }
     * ```
     */
    public fun buildUnitArc(
      start: SkVector,
      stop: SkVector,
      dir: SkPathDirection,
      userMatrix: SkMatrix?,
      conics: Array<SkConic>,
    ): Int {
      TODO("Implement buildUnitArc")
    }
  }
}
