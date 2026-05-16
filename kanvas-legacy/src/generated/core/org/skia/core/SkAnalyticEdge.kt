package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkAnalyticEdge {
 *     // Similar to SkEdge, the conic edges will be converted to quadratic edges
 *     enum class Type : int8_t {
 *         kLine,
 *         kQuad,
 *         kCubic,
 *     };
 *     enum class Winding : int8_t {
 *         kCW = 1,    // clockwise
 *         kCCW = -1,  // counter clockwise
 *     };
 *
 *     SkAnalyticEdge* fNext;
 *     SkAnalyticEdge* fPrev;
 *
 *     SkFixed fX;
 *     SkFixed fDX;
 *     SkFixed fUpperX;        // The x value when y = fUpperY
 *     SkFixed fY;             // The current y
 *     SkFixed fUpperY;        // The upper bound of y (our edge is from y = fUpperY to y = fLowerY)
 *     SkFixed fLowerY;        // The lower bound of y (our edge is from y = fUpperY to y = fLowerY)
 *     SkFixed fDY;            // abs(1/fDX); may be SK_MaxS32 when fDX is close to 0.
 *                             // fDY is only used for blitting trapezoids.
 *
 *     Type fEdgeType;          // Remembers the *initial* edge type
 *
 *     int8_t  fCurveCount;    // only used by kQuad(+) and kCubic(-)
 *     uint8_t fCurveShift;    // appled to all Dx/DDx/DDDx except for fCubicDShift exception
 *     Winding fWinding;
 *
 *     static constexpr int kDefaultAccuracy = 2;  // default accuracy for snapping
 *
 *     static inline SkFixed SnapY(SkFixed y) {
 *         constexpr int accuracy = kDefaultAccuracy;
 *         // This approach is safer than left shift, round, then right shift
 *         return ((unsigned)y + (SK_Fixed1 >> (accuracy + 1))) >> (16 - accuracy) << (16 - accuracy);
 *     }
 *
 *     // Update fX, fY of this edge so fY = y
 *     inline void goY(SkFixed y) {
 *         if (y == fY + SK_Fixed1) {
 *             fX = fX + fDX;
 *             fY = y;
 *         } else if (y != fY) {
 *             // Drop lower digits as our alpha only has 8 bits
 *             // (fDX and y - fUpperY may be greater than SK_Fixed1)
 *             fX = fUpperX + SkFixedMul(fDX, y - fUpperY);
 *             fY = y;
 *         }
 *     }
 *
 *     inline void goY(SkFixed y, int yShift) {
 *         SkASSERT(yShift >= 0 && yShift <= kDefaultAccuracy);
 *         SkASSERT(fDX == 0 || y - fY == SK_Fixed1 >> yShift);
 *         fY = y;
 *         fX += fDX >> yShift;
 *     }
 *
 *     bool setLine(const SkPoint& p0, const SkPoint& p1);
 *     bool updateLine(SkFixed ax, SkFixed ay, SkFixed bx, SkFixed by, SkFixed slope);
 *
 *     // return true if we're NOT done with this edge
 *     bool update(SkFixed last_y);
 *
 * #ifdef SK_DEBUG
 *     void dump() const {
 *         SkDebugf("edge: upperY:%d lowerY:%d y:%g x:%g dx:%g w:%d\n",
 *                  fUpperY,
 *                  fLowerY,
 *                  SkFixedToFloat(fY),
 *                  SkFixedToFloat(fX),
 *                  SkFixedToFloat(fDX),
 *                  static_cast<int8_t>(fWinding));
 *     }
 *
 *     void validate() const {
 *          SkASSERT(fPrev && fNext);
 *          SkASSERT(fPrev->fNext == this);
 *          SkASSERT(fNext->fPrev == this);
 *
 *          SkASSERT(fUpperY < fLowerY);
 *          SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
 *     }
 * #endif
 * }
 * ```
 */
public open class SkAnalyticEdge public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkAnalyticEdge* fNext
   * ```
   */
  public var fNext: SkAnalyticEdge?,
  /**
   * C++ original:
   * ```cpp
   * SkAnalyticEdge* fPrev
   * ```
   */
  public var fPrev: SkAnalyticEdge?,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fX
   * ```
   */
  public var fX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fDX
   * ```
   */
  public var fDX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fUpperX
   * ```
   */
  public var fUpperX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fY
   * ```
   */
  public var fY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fUpperY
   * ```
   */
  public var fUpperY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fLowerY
   * ```
   */
  public var fLowerY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fDY
   * ```
   */
  public var fDY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * Type fEdgeType
   * ```
   */
  public var fEdgeType: Type,
  /**
   * C++ original:
   * ```cpp
   * int8_t  fCurveCount
   * ```
   */
  public var fCurveCount: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fCurveShift
   * ```
   */
  public var fCurveShift: Int,
  /**
   * C++ original:
   * ```cpp
   * Winding fWinding
   * ```
   */
  public var fWinding: Winding,
) {
  /**
   * C++ original:
   * ```cpp
   * inline void goY(SkFixed y) {
   *         if (y == fY + SK_Fixed1) {
   *             fX = fX + fDX;
   *             fY = y;
   *         } else if (y != fY) {
   *             // Drop lower digits as our alpha only has 8 bits
   *             // (fDX and y - fUpperY may be greater than SK_Fixed1)
   *             fX = fUpperX + SkFixedMul(fDX, y - fUpperY);
   *             fY = y;
   *         }
   *     }
   * ```
   */
  public fun goY(y: SkFixed) {
    TODO("Implement goY")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void goY(SkFixed y, int yShift) {
   *         SkASSERT(yShift >= 0 && yShift <= kDefaultAccuracy);
   *         SkASSERT(fDX == 0 || y - fY == SK_Fixed1 >> yShift);
   *         fY = y;
   *         fX += fDX >> yShift;
   *     }
   * ```
   */
  public fun goY(y: SkFixed, yShift: Int) {
    TODO("Implement goY")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticEdge::setLine(const SkPoint& p0, const SkPoint& p1) {
   *     // We must set X/Y using the same way (e.g., times 4, to FDot6, then to Fixed) as Quads/Cubics.
   *     // Otherwise the order of the edge might be wrong due to precision limit.
   *     constexpr int accuracy = kDefaultAccuracy;
   * #ifdef SK_RASTERIZE_EVEN_ROUNDING
   *     SkFixed x0 = SkFDot6ToFixed(SkScalarRoundToFDot6(p0.fX, accuracy)) >> accuracy;
   *     SkFixed y0 = SnapY(SkFDot6ToFixed(SkScalarRoundToFDot6(p0.fY, accuracy)) >> accuracy);
   *     SkFixed x1 = SkFDot6ToFixed(SkScalarRoundToFDot6(p1.fX, accuracy)) >> accuracy;
   *     SkFixed y1 = SnapY(SkFDot6ToFixed(SkScalarRoundToFDot6(p1.fY, accuracy)) >> accuracy);
   * #else
   *     constexpr int multiplier = (1 << kDefaultAccuracy);
   *     SkFixed x0 = SkFDot6ToFixed(SkScalarToFDot6(p0.fX * multiplier)) >> accuracy;
   *     SkFixed y0 = SnapY(SkFDot6ToFixed(SkScalarToFDot6(p0.fY * multiplier)) >> accuracy);
   *     SkFixed x1 = SkFDot6ToFixed(SkScalarToFDot6(p1.fX * multiplier)) >> accuracy;
   *     SkFixed y1 = SnapY(SkFDot6ToFixed(SkScalarToFDot6(p1.fY * multiplier)) >> accuracy);
   * #endif
   *
   *     Winding winding = Winding::kCW;
   *
   *     if (y0 > y1) {
   *         using std::swap;
   *         swap(x0, x1);
   *         swap(y0, y1);
   *         winding = Winding::kCCW;
   *     }
   *
   *     // are we a zero-height line?
   *     SkFDot6 dy = SkFixedToFDot6(y1 - y0);
   *     if (dy == 0) {
   *         return false;
   *     }
   *     SkFDot6 dx = SkFixedToFDot6(x1 - x0);
   *     SkFixed slope = quick_div(dx, dy);
   *     SkFixed absSlope = SkAbs32(slope);
   *
   *     fX          = x0;
   *     fDX         = slope;
   *     fUpperX     = x0;
   *     fY          = y0;
   *     fUpperY     = y0;
   *     fLowerY     = y1;
   *     fDY         = dx == 0 || slope == 0 ? SK_MaxS32 : absSlope < kInverseTableSize
   *                                                     ? quick_inverse(absSlope)
   *                                                     : SkAbs32(quick_div(dy, dx));
   *     fEdgeType   = Type::kLine;
   *     fCurveCount = 0;
   *     fWinding    = winding;
   *     fCurveShift = 0;
   *
   *     return true;
   * }
   * ```
   */
  public fun setLine(p0: SkPoint, p1: SkPoint): Boolean {
    TODO("Implement setLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticEdge::updateLine(SkFixed x0, SkFixed y0, SkFixed x1, SkFixed y1, SkFixed slope) {
   *     // Since we send in the slope, we can no longer snap y inside this function.
   *     // If we don't send in the slope, or we do some more sophisticated snapping, this function
   *     // could be a performance bottleneck.
   *     SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
   *     SkASSERT(fCurveCount != 0);
   *
   *     // We don't chop at y extrema for cubics so the y is not guaranteed to be increasing for them.
   *     // In that case, we have to swap x/y and negate the winding.
   *     if (y0 > y1) {
   *         using std::swap;
   *         swap(x0, x1);
   *         swap(y0, y1);
   *         fWinding = swap_winding(fWinding);
   *     }
   *
   *     SkASSERT(y0 <= y1);
   *
   *     SkFDot6 dx = SkFixedToFDot6(x1 - x0);
   *     SkFDot6 dy = SkFixedToFDot6(y1 - y0);
   *
   *     // are we a zero-height line?
   *     if (dy == 0) {
   *         return false;
   *     }
   *
   *     SkASSERT(slope < SK_MaxS32);
   *
   *     SkFDot6     absSlope = SkAbs32(SkFixedToFDot6(slope));
   *     fX          = x0;
   *     fDX         = slope;
   *     fUpperX     = x0;
   *     fY          = y0;
   *     fUpperY     = y0;
   *     fLowerY     = y1;
   *     fDY         = (dx == 0 || slope == 0)
   *                   ? SK_MaxS32
   *                   : absSlope < kInverseTableSize
   *                     ? quick_inverse(absSlope)
   *                     : SkAbs32(quick_div(dy, dx));
   *
   *     return true;
   * }
   * ```
   */
  public fun updateLine(
    ax: SkFixed,
    ay: SkFixed,
    bx: SkFixed,
    `by`: SkFixed,
    slope: SkFixed,
  ): Boolean {
    TODO("Implement updateLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticEdge::update(SkFixed last_y) {
   *     SkASSERT(last_y >= fLowerY); // we shouldn't update edge if last_y < fLowerY
   *     if (fCurveCount < 0) {
   *         return static_cast<SkAnalyticCubicEdge*>(this)->updateCubic();
   *     } else if (fCurveCount > 0) {
   *         return static_cast<SkAnalyticQuadraticEdge*>(this)->updateQuadratic();
   *     }
   *     return false;
   * }
   * ```
   */
  public fun update(lastY: SkFixed): Boolean {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const {
   *         SkDebugf("edge: upperY:%d lowerY:%d y:%g x:%g dx:%g w:%d\n",
   *                  fUpperY,
   *                  fLowerY,
   *                  SkFixedToFloat(fY),
   *                  SkFixedToFloat(fX),
   *                  SkFixedToFloat(fDX),
   *                  static_cast<int8_t>(fWinding));
   *     }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *          SkASSERT(fPrev && fNext);
   *          SkASSERT(fPrev->fNext == this);
   *          SkASSERT(fNext->fPrev == this);
   *
   *          SkASSERT(fUpperY < fLowerY);
   *          SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
   *     }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  public enum class Type {
    kLine,
    kQuad,
    kCubic,
  }

  public enum class Winding {
    kCW,
    kCCW,
  }

  public companion object {
    public val kDefaultAccuracy: Int = TODO("Initialize kDefaultAccuracy")

    /**
     * C++ original:
     * ```cpp
     * static inline SkFixed SnapY(SkFixed y) {
     *         constexpr int accuracy = kDefaultAccuracy;
     *         // This approach is safer than left shift, round, then right shift
     *         return ((unsigned)y + (SK_Fixed1 >> (accuracy + 1))) >> (16 - accuracy) << (16 - accuracy);
     *     }
     * ```
     */
    public fun snapY(y: SkFixed): SkFixed {
      TODO("Implement snapY")
    }
  }
}
