package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct Convexicator {
 *
 *     /** The direction returned is only valid if the path is determined convex */
 *     SkPathFirstDirection getFirstDirection() const { return fFirstDirection; }
 *
 *     void setMovePt(const SkPoint& pt) {
 *         fFirstPt = fLastPt = pt;
 *         fExpectedDir = kInvalid_DirChange;
 *     }
 *
 *     bool addPt(const SkPoint& pt) {
 *         if (fLastPt == pt) {
 *             return true;
 *         }
 *         // should only be true for first non-zero vector after setMovePt was called. It is possible
 *         // we doubled backed at the start so need to check if fLastVec is zero or not.
 *         if (fFirstPt == fLastPt && fExpectedDir == kInvalid_DirChange && fLastVec.equals(0,0)) {
 *             fLastVec = pt - fLastPt;
 *             fFirstVec = fLastVec;
 *         } else if (!this->addVec(pt - fLastPt)) {
 *             return false;
 *         }
 *         fLastPt = pt;
 *         return true;
 *     }
 *
 *     static bool IsConcaveBySign(const SkPoint points[], int count) {
 *         if (count <= 3) {
 *             // point, line, or triangle are always convex
 *             return false;
 *         }
 *
 *         const SkPoint* last = points + count;
 *         SkPoint currPt = *points++;
 *         SkPoint firstPt = currPt;
 *         int dxes = 0;
 *         int dyes = 0;
 *         int lastSx = kValueNeverReturnedBySign;
 *         int lastSy = kValueNeverReturnedBySign;
 *         for (int outerLoop = 0; outerLoop < 2; ++outerLoop ) {
 *             while (points != last) {
 *                 SkVector vec = *points - currPt;
 *                 if (!vec.isZero()) {
 *                     // give up if vector construction failed
 *                     if (!vec.isFinite()) {
 *                         return true;    // treat as concave
 *                     }
 *                     int sx = sign(vec.fX);
 *                     int sy = sign(vec.fY);
 *                     dxes += (sx != lastSx);
 *                     dyes += (sy != lastSy);
 *                     if (dxes > 3 || dyes > 3) {
 *                         return true;
 *                     }
 *                     lastSx = sx;
 *                     lastSy = sy;
 *                 }
 *                 currPt = *points++;
 *                 if (outerLoop) {
 *                     break;
 *                 }
 *             }
 *             points = &firstPt;
 *         }
 *         return false;  // that is, it may be convex, don't know yet
 *     }
 *
 *     bool close() {
 *         // If this was an explicit close, there was already a lineTo to fFirstPoint, so this
 *         // addPt() is a no-op. Otherwise, the addPt implicitly closes the contour. In either case,
 *         // we have to check the direction change along the first vector in case it is concave.
 *         return this->addPt(fFirstPt) && this->addVec(fFirstVec);
 *     }
 *
 *     bool isFinite() const {
 *         return fIsFinite;
 *     }
 *
 *     int reversals() const {
 *         return fReversals;
 *     }
 *
 * private:
 *     DirChange directionChange(const SkVector& curVec) {
 *         SkScalar cross = SkPoint::CrossProduct(fLastVec, curVec);
 *         if (!SkIsFinite(cross)) {
 *             return kUnknown_DirChange;
 *         }
 *         if (cross == 0) {
 *             return fLastVec.dot(curVec) < 0 ? kBackwards_DirChange : kStraight_DirChange;
 *         }
 *         return 1 == SkScalarSignAsInt(cross) ? kRight_DirChange : kLeft_DirChange;
 *     }
 *
 *     bool addVec(const SkVector& curVec) {
 *         DirChange dir = this->directionChange(curVec);
 *         switch (dir) {
 *             case kLeft_DirChange:       // fall through
 *             case kRight_DirChange:
 *                 if (kInvalid_DirChange == fExpectedDir) {
 *                     fExpectedDir = dir;
 *                     fFirstDirection = (kRight_DirChange == dir) ? SkPathFirstDirection::kCW
 *                                                                 : SkPathFirstDirection::kCCW;
 *                 } else if (dir != fExpectedDir) {
 *                     fFirstDirection = SkPathFirstDirection::kUnknown;
 *                     return false;
 *                 }
 *                 fLastVec = curVec;
 *                 break;
 *             case kStraight_DirChange:
 *                 break;
 *             case kBackwards_DirChange:
 *                 //  allow path to reverse direction twice
 *                 //    Given path.moveTo(0, 0); path.lineTo(1, 1);
 *                 //    - 1st reversal: direction change formed by line (0,0 1,1), line (1,1 0,0)
 *                 //    - 2nd reversal: direction change formed by line (1,1 0,0), line (0,0 1,1)
 *                 fLastVec = curVec;
 *                 return ++fReversals < 3;
 *             case kUnknown_DirChange:
 *                 return (fIsFinite = false);
 *             case kInvalid_DirChange:
 *                 SK_ABORT("Use of invalid direction change flag");
 *                 break;
 *         }
 *         return true;
 *     }
 *
 *     SkPoint              fFirstPt {0, 0};  // The first point of the contour, e.g. moveTo(x,y)
 *     SkVector             fFirstVec {0, 0}; // The direction leaving fFirstPt to the next vertex
 *
 *     SkPoint              fLastPt {0, 0};   // The last point passed to addPt()
 *     SkVector             fLastVec {0, 0};  // The direction that brought the path to fLastPt
 *
 *     DirChange            fExpectedDir { kInvalid_DirChange };
 *     SkPathFirstDirection fFirstDirection { SkPathFirstDirection::kUnknown };
 *     int                  fReversals { 0 };
 *     bool                 fIsFinite { true };
 * }
 * ```
 */
public data class Convexicator public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint              fFirstPt {0, 0}
   * ```
   */
  private var fFirstPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkVector             fFirstVec {0, 0}
   * ```
   */
  private var fFirstVec: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkPoint              fLastPt {0, 0}
   * ```
   */
  private var fLastPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkVector             fLastVec {0, 0}
   * ```
   */
  private var fLastVec: SkVector,
  /**
   * C++ original:
   * ```cpp
   * DirChange            fExpectedDir { kInvalid_DirChange }
   * ```
   */
  private var fExpectedDir: DirChange,
  /**
   * C++ original:
   * ```cpp
   * SkPathFirstDirection fFirstDirection { SkPathFirstDirection::kUnknown }
   * ```
   */
  private var fFirstDirection: SkPathFirstDirection,
  /**
   * C++ original:
   * ```cpp
   * int                  fReversals { 0 }
   * ```
   */
  private var fReversals: Int,
  /**
   * C++ original:
   * ```cpp
   * bool                 fIsFinite { true }
   * ```
   */
  private var fIsFinite: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPathFirstDirection getFirstDirection() const { return fFirstDirection; }
   * ```
   */
  public fun getFirstDirection(): SkPathFirstDirection {
    TODO("Implement getFirstDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMovePt(const SkPoint& pt) {
   *         fFirstPt = fLastPt = pt;
   *         fExpectedDir = kInvalid_DirChange;
   *     }
   * ```
   */
  public fun setMovePt(pt: SkPoint) {
    TODO("Implement setMovePt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool addPt(const SkPoint& pt) {
   *         if (fLastPt == pt) {
   *             return true;
   *         }
   *         // should only be true for first non-zero vector after setMovePt was called. It is possible
   *         // we doubled backed at the start so need to check if fLastVec is zero or not.
   *         if (fFirstPt == fLastPt && fExpectedDir == kInvalid_DirChange && fLastVec.equals(0,0)) {
   *             fLastVec = pt - fLastPt;
   *             fFirstVec = fLastVec;
   *         } else if (!this->addVec(pt - fLastPt)) {
   *             return false;
   *         }
   *         fLastPt = pt;
   *         return true;
   *     }
   * ```
   */
  public fun addPt(pt: SkPoint): Boolean {
    TODO("Implement addPt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool close() {
   *         // If this was an explicit close, there was already a lineTo to fFirstPoint, so this
   *         // addPt() is a no-op. Otherwise, the addPt implicitly closes the contour. In either case,
   *         // we have to check the direction change along the first vector in case it is concave.
   *         return this->addPt(fFirstPt) && this->addVec(fFirstVec);
   *     }
   * ```
   */
  public fun close(): Boolean {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const {
   *         return fIsFinite;
   *     }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * int reversals() const {
   *         return fReversals;
   *     }
   * ```
   */
  public fun reversals(): Int {
    TODO("Implement reversals")
  }

  /**
   * C++ original:
   * ```cpp
   * DirChange directionChange(const SkVector& curVec) {
   *         SkScalar cross = SkPoint::CrossProduct(fLastVec, curVec);
   *         if (!SkIsFinite(cross)) {
   *             return kUnknown_DirChange;
   *         }
   *         if (cross == 0) {
   *             return fLastVec.dot(curVec) < 0 ? kBackwards_DirChange : kStraight_DirChange;
   *         }
   *         return 1 == SkScalarSignAsInt(cross) ? kRight_DirChange : kLeft_DirChange;
   *     }
   * ```
   */
  private fun directionChange(curVec: SkVector): DirChange {
    TODO("Implement directionChange")
  }

  /**
   * C++ original:
   * ```cpp
   * bool addVec(const SkVector& curVec) {
   *         DirChange dir = this->directionChange(curVec);
   *         switch (dir) {
   *             case kLeft_DirChange:       // fall through
   *             case kRight_DirChange:
   *                 if (kInvalid_DirChange == fExpectedDir) {
   *                     fExpectedDir = dir;
   *                     fFirstDirection = (kRight_DirChange == dir) ? SkPathFirstDirection::kCW
   *                                                                 : SkPathFirstDirection::kCCW;
   *                 } else if (dir != fExpectedDir) {
   *                     fFirstDirection = SkPathFirstDirection::kUnknown;
   *                     return false;
   *                 }
   *                 fLastVec = curVec;
   *                 break;
   *             case kStraight_DirChange:
   *                 break;
   *             case kBackwards_DirChange:
   *                 //  allow path to reverse direction twice
   *                 //    Given path.moveTo(0, 0); path.lineTo(1, 1);
   *                 //    - 1st reversal: direction change formed by line (0,0 1,1), line (1,1 0,0)
   *                 //    - 2nd reversal: direction change formed by line (1,1 0,0), line (0,0 1,1)
   *                 fLastVec = curVec;
   *                 return ++fReversals < 3;
   *             case kUnknown_DirChange:
   *                 return (fIsFinite = false);
   *             case kInvalid_DirChange:
   *                 SK_ABORT("Use of invalid direction change flag");
   *                 break;
   *         }
   *         return true;
   *     }
   * ```
   */
  private fun addVec(curVec: SkVector): Boolean {
    TODO("Implement addVec")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsConcaveBySign(const SkPoint points[], int count) {
     *         if (count <= 3) {
     *             // point, line, or triangle are always convex
     *             return false;
     *         }
     *
     *         const SkPoint* last = points + count;
     *         SkPoint currPt = *points++;
     *         SkPoint firstPt = currPt;
     *         int dxes = 0;
     *         int dyes = 0;
     *         int lastSx = kValueNeverReturnedBySign;
     *         int lastSy = kValueNeverReturnedBySign;
     *         for (int outerLoop = 0; outerLoop < 2; ++outerLoop ) {
     *             while (points != last) {
     *                 SkVector vec = *points - currPt;
     *                 if (!vec.isZero()) {
     *                     // give up if vector construction failed
     *                     if (!vec.isFinite()) {
     *                         return true;    // treat as concave
     *                     }
     *                     int sx = sign(vec.fX);
     *                     int sy = sign(vec.fY);
     *                     dxes += (sx != lastSx);
     *                     dyes += (sy != lastSy);
     *                     if (dxes > 3 || dyes > 3) {
     *                         return true;
     *                     }
     *                     lastSx = sx;
     *                     lastSy = sy;
     *                 }
     *                 currPt = *points++;
     *                 if (outerLoop) {
     *                     break;
     *                 }
     *             }
     *             points = &firstPt;
     *         }
     *         return false;  // that is, it may be convex, don't know yet
     *     }
     * ```
     */
    public fun isConcaveBySign(points: Array<SkPoint>, count: Int): Boolean {
      TODO("Implement isConcaveBySign")
    }
  }
}
