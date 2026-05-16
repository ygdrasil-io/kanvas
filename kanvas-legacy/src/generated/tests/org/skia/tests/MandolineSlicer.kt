package org.skia.tests

import kotlin.Float
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.foundation.SkPath
import org.skia.math.SkPoint
import org.skia.math.SkRandom

/**
 * C++ original:
 * ```cpp
 * class MandolineSlicer {
 * public:
 *     inline static constexpr int kDefaultSubdivisions = 10;
 *
 *     MandolineSlicer(SkPoint anchorPt) {
 *         this->reset(anchorPt);
 *     }
 *
 *     void reset(SkPoint anchorPt) {
 *         fBuilder.reset();
 *
 *         // see https://skia-review.googlesource.com/c/skia/+/1055736
 *         fBuilder.setIsVolatile(true);
 *
 *         fLastPt = fAnchorPt = anchorPt;
 *     }
 *
 *     void sliceLine(SkPoint pt, int numSubdivisions = kDefaultSubdivisions) {
 *         if (numSubdivisions <= 0) {
 *             fBuilder.moveTo(fAnchorPt);
 *             fBuilder.lineTo(fLastPt);
 *             fBuilder.lineTo(pt);
 *             fBuilder.close();
 *             fLastPt = pt;
 *             return;
 *         }
 *         float T = this->chooseChopT(numSubdivisions);
 *         if (0 == T) {
 *             return;
 *         }
 *         SkPoint midpt = fLastPt * (1 - T) + pt * T;
 *         this->sliceLine(midpt, numSubdivisions - 1);
 *         this->sliceLine(pt, numSubdivisions - 1);
 *     }
 *
 *     void sliceQuadratic(SkPoint p1, SkPoint p2, int numSubdivisions = kDefaultSubdivisions) {
 *         if (numSubdivisions <= 0) {
 *             fBuilder.moveTo(fAnchorPt);
 *             fBuilder.lineTo(fLastPt);
 *             fBuilder.quadTo(p1, p2);
 *             fBuilder.close();
 *             fLastPt = p2;
 *             return;
 *         }
 *         float T = this->chooseChopT(numSubdivisions);
 *         if (0 == T) {
 *             return;
 *         }
 *         SkPoint P[3] = {fLastPt, p1, p2}, PP[5];
 *         SkChopQuadAt(P, PP, T);
 *         this->sliceQuadratic(PP[1], PP[2], numSubdivisions - 1);
 *         this->sliceQuadratic(PP[3], PP[4], numSubdivisions - 1);
 *     }
 *
 *     void sliceCubic(SkPoint p1, SkPoint p2, SkPoint p3,
 *                     int numSubdivisions = kDefaultSubdivisions) {
 *         if (numSubdivisions <= 0) {
 *             fBuilder.moveTo(fAnchorPt);
 *             fBuilder.lineTo(fLastPt);
 *             fBuilder.cubicTo(p1, p2, p3);
 *             fBuilder.close();
 *             fLastPt = p3;
 *             return;
 *         }
 *         float T = this->chooseChopT(numSubdivisions);
 *         if (0 == T) {
 *             return;
 *         }
 *         SkPoint P[4] = {fLastPt, p1, p2, p3}, PP[7];
 *         SkChopCubicAt(P, PP, T);
 *         this->sliceCubic(PP[1], PP[2], PP[3], numSubdivisions - 1);
 *         this->sliceCubic(PP[4], PP[5], PP[6], numSubdivisions - 1);
 *     }
 *
 *     void sliceConic(SkPoint p1, SkPoint p2, float w, int numSubdivisions = kDefaultSubdivisions) {
 *         if (numSubdivisions <= 0) {
 *             fBuilder.moveTo(fAnchorPt);
 *             fBuilder.lineTo(fLastPt);
 *             fBuilder.conicTo(p1, p2, w);
 *             fBuilder.close();
 *             fLastPt = p2;
 *             return;
 *         }
 *         float T = this->chooseChopT(numSubdivisions);
 *         if (0 == T) {
 *             return;
 *         }
 *         SkConic conic(fLastPt, p1, p2, w), halves[2];
 *         if (!conic.chopAt(T, halves)) {
 *             SK_ABORT("SkConic::chopAt failed");
 *         }
 *         this->sliceConic(halves[0].fPts[1], halves[0].fPts[2], halves[0].fW, numSubdivisions - 1);
 *         this->sliceConic(halves[1].fPts[1], halves[1].fPts[2], halves[1].fW, numSubdivisions - 1);
 *     }
 *
 *     SkPath path() { return fBuilder.snapshot(); }
 *
 * private:
 *     float chooseChopT(int numSubdivisions) {
 *         SkASSERT(numSubdivisions > 0);
 *         if (numSubdivisions > 1) {
 *             return .5f;
 *         }
 *         float T = (0 == fRand.nextU() % 10) ? 0 : scalbnf(1, -(int)fRand.nextRangeU(10, 149));
 *         SkASSERT(T >= 0 && T < 1);
 *         return T;
 *     }
 *
 *     SkRandom fRand;
 *     SkPathBuilder fBuilder;
 *     SkPoint fAnchorPt;
 *     SkPoint fLastPt;
 * }
 * ```
 */
public data class MandolineSlicer public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kDefaultSubdivisions = 10
   * ```
   */
  private var fRand: SkRandom,
  /**
   * C++ original:
   * ```cpp
   * SkRandom fRand
   * ```
   */
  private var fBuilder: SkPathBuilder,
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder fBuilder
   * ```
   */
  private var fAnchorPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fAnchorPt
   * ```
   */
  private var fLastPt: SkPoint,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset(SkPoint anchorPt) {
   *         fBuilder.reset();
   *
   *         // see https://skia-review.googlesource.com/c/skia/+/1055736
   *         fBuilder.setIsVolatile(true);
   *
   *         fLastPt = fAnchorPt = anchorPt;
   *     }
   * ```
   */
  public fun reset(anchorPt: SkPoint) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void sliceLine(SkPoint pt, int numSubdivisions = kDefaultSubdivisions) {
   *         if (numSubdivisions <= 0) {
   *             fBuilder.moveTo(fAnchorPt);
   *             fBuilder.lineTo(fLastPt);
   *             fBuilder.lineTo(pt);
   *             fBuilder.close();
   *             fLastPt = pt;
   *             return;
   *         }
   *         float T = this->chooseChopT(numSubdivisions);
   *         if (0 == T) {
   *             return;
   *         }
   *         SkPoint midpt = fLastPt * (1 - T) + pt * T;
   *         this->sliceLine(midpt, numSubdivisions - 1);
   *         this->sliceLine(pt, numSubdivisions - 1);
   *     }
   * ```
   */
  public fun sliceLine(pt: SkPoint, numSubdivisions: Int = TODO()) {
    TODO("Implement sliceLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void sliceQuadratic(SkPoint p1, SkPoint p2, int numSubdivisions = kDefaultSubdivisions) {
   *         if (numSubdivisions <= 0) {
   *             fBuilder.moveTo(fAnchorPt);
   *             fBuilder.lineTo(fLastPt);
   *             fBuilder.quadTo(p1, p2);
   *             fBuilder.close();
   *             fLastPt = p2;
   *             return;
   *         }
   *         float T = this->chooseChopT(numSubdivisions);
   *         if (0 == T) {
   *             return;
   *         }
   *         SkPoint P[3] = {fLastPt, p1, p2}, PP[5];
   *         SkChopQuadAt(P, PP, T);
   *         this->sliceQuadratic(PP[1], PP[2], numSubdivisions - 1);
   *         this->sliceQuadratic(PP[3], PP[4], numSubdivisions - 1);
   *     }
   * ```
   */
  public fun sliceQuadratic(
    p1: SkPoint,
    p2: SkPoint,
    numSubdivisions: Int = TODO(),
  ) {
    TODO("Implement sliceQuadratic")
  }

  /**
   * C++ original:
   * ```cpp
   * void sliceCubic(SkPoint p1, SkPoint p2, SkPoint p3,
   *                     int numSubdivisions = kDefaultSubdivisions) {
   *         if (numSubdivisions <= 0) {
   *             fBuilder.moveTo(fAnchorPt);
   *             fBuilder.lineTo(fLastPt);
   *             fBuilder.cubicTo(p1, p2, p3);
   *             fBuilder.close();
   *             fLastPt = p3;
   *             return;
   *         }
   *         float T = this->chooseChopT(numSubdivisions);
   *         if (0 == T) {
   *             return;
   *         }
   *         SkPoint P[4] = {fLastPt, p1, p2, p3}, PP[7];
   *         SkChopCubicAt(P, PP, T);
   *         this->sliceCubic(PP[1], PP[2], PP[3], numSubdivisions - 1);
   *         this->sliceCubic(PP[4], PP[5], PP[6], numSubdivisions - 1);
   *     }
   * ```
   */
  public fun sliceCubic(
    p1: SkPoint,
    p2: SkPoint,
    p3: SkPoint,
    numSubdivisions: Int = TODO(),
  ) {
    TODO("Implement sliceCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * void sliceConic(SkPoint p1, SkPoint p2, float w, int numSubdivisions = kDefaultSubdivisions) {
   *         if (numSubdivisions <= 0) {
   *             fBuilder.moveTo(fAnchorPt);
   *             fBuilder.lineTo(fLastPt);
   *             fBuilder.conicTo(p1, p2, w);
   *             fBuilder.close();
   *             fLastPt = p2;
   *             return;
   *         }
   *         float T = this->chooseChopT(numSubdivisions);
   *         if (0 == T) {
   *             return;
   *         }
   *         SkConic conic(fLastPt, p1, p2, w), halves[2];
   *         if (!conic.chopAt(T, halves)) {
   *             SK_ABORT("SkConic::chopAt failed");
   *         }
   *         this->sliceConic(halves[0].fPts[1], halves[0].fPts[2], halves[0].fW, numSubdivisions - 1);
   *         this->sliceConic(halves[1].fPts[1], halves[1].fPts[2], halves[1].fW, numSubdivisions - 1);
   *     }
   * ```
   */
  public fun sliceConic(
    p1: SkPoint,
    p2: SkPoint,
    w: Float,
    numSubdivisions: Int = TODO(),
  ) {
    TODO("Implement sliceConic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath path() { return fBuilder.snapshot(); }
   * ```
   */
  public fun path(): SkPath {
    TODO("Implement path")
  }

  /**
   * C++ original:
   * ```cpp
   * float chooseChopT(int numSubdivisions) {
   *         SkASSERT(numSubdivisions > 0);
   *         if (numSubdivisions > 1) {
   *             return .5f;
   *         }
   *         float T = (0 == fRand.nextU() % 10) ? 0 : scalbnf(1, -(int)fRand.nextRangeU(10, 149));
   *         SkASSERT(T >= 0 && T < 1);
   *         return T;
   *     }
   * ```
   */
  private fun chooseChopT(numSubdivisions: Int): Float {
    TODO("Implement chooseChopT")
  }

  public companion object {
    public val kDefaultSubdivisions: Int = TODO("Initialize kDefaultSubdivisions")
  }
}
