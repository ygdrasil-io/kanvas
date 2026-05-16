package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkIRect
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkEdge {
 * public:
 *     virtual ~SkEdge() = default;
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
 *     // Can be used to join edges together
 *     SkEdge* fNext;
 *     SkEdge* fPrev;
 *
 *     // The current line segment starts at (fX, fFirstY + 0.5). It has slope
 *     // fDxDy (yes, run over rise, because this is geared toward horizontal scanlines)
 *     // and stops once Y gets to fLastY + 0.5.
 *     SkFixed fX;
 *     SkFixed fDxDy;
 *
 *     // These are integers because they represent a discrete pixel. Mathematically, these are
 *     // treated as half way inside the pixel, so 6 -> 6.5.
 *     int32_t fFirstY;
 *     int32_t fLastY;
 *
 *     Type    fEdgeType;      // Remembers the *initial* edge type
 *     Winding fWinding;
 *
 *     // Represent a straight line with an optional clip. This will always be a single segment.
 *     // Returns false if the line has height 0.
 *     bool setLine(const SkPoint& p0, const SkPoint& p1, const SkIRect* clip);
 *     // call this version if you know you don't have a clip
 *     inline bool setLine(const SkPoint& p0, const SkPoint& p1);
 *
 *     bool hasNextSegment() const {
 *         return fSegmentCount != 0;
 *     }
 *     // Update fX, fDxDY, fFirstY, and fLastY to represent the segment. It will skip over
 *     // any lines that have a height of 0 pixels and return false if there were only 0-height
 *     // segments remaining. For quadratic and cubic curves this will involve forward-differencing
 *     // (see the subclasses for those values).
 *     virtual bool nextSegment();
 *
 *     uint8_t segmentsLeft() const {
 *         return fSegmentCount;
 *     }
 *
 * protected:
 *     inline bool updateLine(SkFixed ax, SkFixed ay, SkFixed bx, SkFixed by);
 *
 *     uint8_t fSegmentCount; // only non-zero for Quad and Cubics
 *     // How much to shift the derivatives to multiply by deltaT when doing forward-differencing.
 *     // For cubics, this is log_2(N) and for quadratics this is log_2(N) - 1.
 *     uint8_t fCurveShift;
 *
 * private:
 *     void chopLineWithClip(const SkIRect& clip);
 *
 * #if defined(SK_DEBUG)
 * public:
 *     virtual void dump() const;
 *     void validate() const {
 *         SkASSERT(fPrev && fNext);
 *         SkASSERT(fPrev->fNext == this);
 *         SkASSERT(fNext->fPrev == this);
 *
 *         SkASSERT(fFirstY <= fLastY);
 *         SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
 *     }
 * #endif
 * }
 * ```
 */
public open class SkEdge {
  /**
   * C++ original:
   * ```cpp
   * SkEdge* fNext
   * ```
   */
  public var fNext: SkEdge? = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * SkEdge* fPrev
   * ```
   */
  public var fPrev: SkEdge? = TODO("Initialize fPrev")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fX
   * ```
   */
  public var fX: SkFixed = TODO("Initialize fX")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fDxDy
   * ```
   */
  public var fDxDy: SkFixed = TODO("Initialize fDxDy")

  /**
   * C++ original:
   * ```cpp
   * int32_t fFirstY
   * ```
   */
  public var fFirstY: Int = TODO("Initialize fFirstY")

  /**
   * C++ original:
   * ```cpp
   * int32_t fLastY
   * ```
   */
  public var fLastY: Int = TODO("Initialize fLastY")

  /**
   * C++ original:
   * ```cpp
   * Type    fEdgeType
   * ```
   */
  public var fEdgeType: Type = TODO("Initialize fEdgeType")

  /**
   * C++ original:
   * ```cpp
   * Winding fWinding
   * ```
   */
  public var fWinding: Winding = TODO("Initialize fWinding")

  /**
   * C++ original:
   * ```cpp
   * uint8_t fSegmentCount
   * ```
   */
  protected var fSegmentCount: Int = TODO("Initialize fSegmentCount")

  /**
   * C++ original:
   * ```cpp
   * uint8_t fCurveShift
   * ```
   */
  protected var fCurveShift: Int = TODO("Initialize fCurveShift")

  /**
   * C++ original:
   * ```cpp
   * bool SkEdge::setLine(const SkPoint& p0, const SkPoint& p1, const SkIRect* clip) {
   *     SkFDot6 x0, y0, x1, y1;
   *
   * #ifdef SK_RASTERIZE_EVEN_ROUNDING
   *     x0 = SkScalarRoundToFDot6(p0.fX, 0);
   *     y0 = SkScalarRoundToFDot6(p0.fY, 0);
   *     x1 = SkScalarRoundToFDot6(p1.fX, 0);
   *     y1 = SkScalarRoundToFDot6(p1.fY, 0);
   * #else
   *     x0 = SkFloatToFDot6(p0.fX);
   *     y0 = SkFloatToFDot6(p0.fY);
   *     x1 = SkFloatToFDot6(p1.fX);
   *     y1 = SkFloatToFDot6(p1.fY);
   * #endif
   *
   *     Winding winding = Winding::kCW;
   *     if (y0 > y1) {
   *         std::swap(x0, x1);
   *         std::swap(y0, y1);
   *         winding = Winding::kCCW;
   *     }
   *
   *     int top = SkFDot6Round(y0);
   *     int bot = SkFDot6Round(y1);
   *
   *     // are we a zero-height line?
   *     if (top == bot) {
   *         return false;
   *     }
   *     // are we completely above or below the clip?
   *     if (clip && (top >= clip->fBottom || bot <= clip->fTop)) {
   *         return false;
   *     }
   *
   *     SkFixed slope = SkFDot6Div(x1 - x0, y1 - y0);
   *     const SkFDot6 dy  = SkEdge_Compute_DY(top, y0);
   *
   *     // Note that SkFixedMul(SkFixed, SkFDot6) produces results in SkFDot6
   *     fX          = SkFDot6ToFixed(x0 + SkFixedMul(slope, dy));
   *     fDxDy       = slope;
   *     fFirstY     = top;
   *     fLastY      = bot - 1;
   *     fEdgeType   = Type::kLine;
   *     fSegmentCount = 0;
   *     fWinding    = winding;
   *     fCurveShift = 0;
   *
   *     if (clip) {
   *         this->chopLineWithClip(*clip);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun setLine(
    p0: SkPoint,
    p1: SkPoint,
    clip: SkIRect?,
  ): Boolean {
    TODO("Implement setLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEdge::setLine(const SkPoint& p0, const SkPoint& p1) {
   *     SkFDot6 x0, y0, x1, y1;
   *
   * #if defined(SK_RASTERIZE_EVEN_ROUNDING)
   *     x0 = SkScalarRoundToFDot6(p0.fX, 0);
   *     y0 = SkScalarRoundToFDot6(p0.fY, 0);
   *     x1 = SkScalarRoundToFDot6(p1.fX, 0);
   *     y1 = SkScalarRoundToFDot6(p1.fY, 0);
   * #else
   *     x0 = SkFloatToFDot6(p0.fX);
   *     y0 = SkFloatToFDot6(p0.fY);
   *     x1 = SkFloatToFDot6(p1.fX);
   *     y1 = SkFloatToFDot6(p1.fY);
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
   *     int top = SkFDot6Round(y0);
   *     int bot = SkFDot6Round(y1);
   *
   *     // are we a zero-height line?
   *     if (top == bot) {
   *         return false;
   *     }
   *
   *     SkFixed slope = SkFDot6Div(x1 - x0, y1 - y0);
   *     const SkFDot6 dy  = SkEdge_Compute_DY(top, y0);
   *
   *     // Note that SkFixedMul(SkFixed, SkFDot6) produces results in SkFDot6
   *     fX          = SkFDot6ToFixed(x0 + SkFixedMul(slope, dy));
   *     fDxDy       = slope;
   *     fFirstY     = top;
   *     fLastY      = bot - 1;
   *     fEdgeType   = Type::kLine;
   *     fSegmentCount = 0;
   *     fWinding    = winding;
   *     fCurveShift = 0;
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
   * bool hasNextSegment() const {
   *         return fSegmentCount != 0;
   *     }
   * ```
   */
  public fun hasNextSegment(): Boolean {
    TODO("Implement hasNextSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEdge::nextSegment() {
   *     SkDEBUGFAILF("Shouldn't be asking a linear edge to go to the next curve.");
   *     return false;
   * }
   * ```
   */
  public open fun nextSegment(): Boolean {
    TODO("Implement nextSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t segmentsLeft() const {
   *         return fSegmentCount;
   *     }
   * ```
   */
  public fun segmentsLeft(): Int {
    TODO("Implement segmentsLeft")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEdge::updateLine(SkFixed xStart, SkFixed yStart, SkFixed xEnd, SkFixed yEnd) {
   *     SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
   *     SkASSERT(fSegmentCount != 0);
   *
   *     const SkFDot6 y0 = SkFixedToFDot6(yStart);
   *     const SkFDot6 y1 = SkFixedToFDot6(yEnd);
   *
   *     SkASSERT(y0 <= y1);
   *
   *     const int top = SkFDot6Round(y0);
   *     const int bot = SkFDot6Round(y1);
   *
   *     // are we a zero-height line?
   *     if (top == bot) {
   *         return false;
   *     }
   *
   *     const SkFDot6 x0 = SkFixedToFDot6(xStart);
   *     const SkFDot6 x1 = SkFixedToFDot6(xEnd);
   *
   *     SkFixed slope = SkFDot6Div(x1 - x0, y1 - y0);
   *     const SkFDot6 dy = SkEdge_Compute_DY(top, y0);
   *
   *     // We could do this math in fixed point, but it would potentially require some
   *     // rebaselining https://codereview.chromium.org/960353005/#msg6
   *     // Note that SkFixedMul(SkFixed, SkFDot6) produces results in SkFDot6
   *     fX          = SkFDot6ToFixed(x0 + SkFixedMul(slope, dy));
   *     fDxDy       = slope;
   *     fFirstY     = top;
   *     fLastY      = bot - 1;
   *
   *     return true;
   * }
   * ```
   */
  protected fun updateLine(
    ax: SkFixed,
    ay: SkFixed,
    bx: SkFixed,
    `by`: SkFixed,
  ): Boolean {
    TODO("Implement updateLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdge::chopLineWithClip(const SkIRect& clip)
   * {
   *     int top = fFirstY;
   *
   *     SkASSERT(top < clip.fBottom);
   *
   *     // clip the line to the top
   *     if (top < clip.fTop)
   *     {
   *         SkASSERT(fLastY >= clip.fTop);
   *         fX += fDxDy * (clip.fTop - top);
   *         fFirstY = clip.fTop;
   *     }
   * }
   * ```
   */
  private fun chopLineWithClip(clip: SkIRect) {
    TODO("Implement chopLineWithClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdge::dump() const {
   *     SkASSERT(fSegmentCount == 0);
   *     SkDebugf("line edge: firstY:%d lastY:%d x:%g dx/dy:%g\n"
   *              "\twinding:%d curveShift:%u\n",
   *              fFirstY,
   *              fLastY,
   *              SkFixedToFloat(fX),
   *              SkFixedToFloat(fDxDy),
   *              static_cast<int8_t>(fWinding),
   *              fCurveShift);
   * }
   * ```
   */
  public open fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *         SkASSERT(fPrev && fNext);
   *         SkASSERT(fPrev->fNext == this);
   *         SkASSERT(fNext->fPrev == this);
   *
   *         SkASSERT(fFirstY <= fLastY);
   *         SkASSERT(fWinding == Winding::kCW || fWinding == Winding::kCCW);
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
}
