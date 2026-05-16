package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkEdgeClipper {
 * public:
 *     explicit SkEdgeClipper(bool canCullToTheRight) : fCanCullToTheRight(canCullToTheRight) {}
 *
 *     bool clipLine(SkPoint p0, SkPoint p1, const SkRect& clip);
 *     bool clipQuad(const SkPoint pts[3], const SkRect& clip);
 *     bool clipCubic(const SkPoint pts[4], const SkRect& clip);
 *
 *     std::optional<SkPathVerb> next(SkPoint pts[]);
 *
 *     bool canCullToTheRight() const { return fCanCullToTheRight; }
 *
 *     /**
 *      *  Clips each segment from the path, and passes the result (in a clipper) to the
 *      *  consume proc.
 *      */
 *     static void ClipPath(const SkPathRaw&, const SkRect& clip, bool canCullToTheRight,
 *                          void (*consume)(SkEdgeClipper*, bool newCtr, void* ctx), void* ctx);
 *
 * private:
 *     SkPoint*    fCurrPoint;
 *     SkPathVerb* fCurrVerb, *fCurrVerbStop;
 *     const bool  fCanCullToTheRight;
 *
 *     enum {
 *         kMaxVerbs = 18,  // max curvature in X and Y split cubic into 9 pieces, * (line + cubic)
 *         kMaxPoints = 54  // 2 lines + 1 cubic require 6 points; times 9 pieces
 *     };
 *     SkPoint     fPoints[kMaxPoints];
 *     SkPathVerb  fVerbs[kMaxVerbs];
 *
 *     void clipMonoQuad(const SkPoint srcPts[3], const SkRect& clip);
 *     void clipMonoCubic(const SkPoint srcPts[4], const SkRect& clip);
 *     void appendLine(SkPoint p0, SkPoint p1);
 *     void appendVLine(SkScalar x, SkScalar y0, SkScalar y1, bool reverse);
 *     void appendQuad(const SkPoint pts[3], bool reverse);
 *     void appendCubic(const SkPoint pts[4], bool reverse);
 * }
 * ```
 */
public data class SkEdgeClipper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint*    fCurrPoint
   * ```
   */
  private var fCurrPoint: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * SkPathVerb* fCurrVerb
   * ```
   */
  private var fCurrVerb: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * SkPathVerb* fCurrVerb, *fCurrVerbStop
   * ```
   */
  private var fCurrVerbStop: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * const bool  fCanCullToTheRight
   * ```
   */
  private val fCanCullToTheRight: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fPoints[kMaxPoints]
   * ```
   */
  private var fPoints: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkPathVerb  fVerbs[kMaxVerbs]
   * ```
   */
  private var fVerbs: Array<SkPathVerb>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkEdgeClipper::clipLine(SkPoint p0, SkPoint p1, const SkRect& clip) {
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *
   *     SkPoint lines[SkLineClipper::kMaxPoints];
   *     const SkPoint pts[] = { p0, p1 };
   *     int lineCount = SkLineClipper::ClipLine(pts, clip, lines, fCanCullToTheRight);
   *     for (int i = 0; i < lineCount; i++) {
   *         this->appendLine(lines[i], lines[i + 1]);
   *     }
   *
   *     fCurrVerbStop = fCurrVerb;
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *     return fCurrVerbStop != fCurrVerb;
   * }
   * ```
   */
  public fun clipLine(
    p0: SkPoint,
    p1: SkPoint,
    clip: SkRect,
  ): Boolean {
    TODO("Implement clipLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEdgeClipper::clipQuad(const SkPoint srcPts[3], const SkRect& clip) {
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *
   *     const SkRect bounds = SkRect::BoundsOrEmpty({srcPts, 3});
   *
   *     if (!quick_reject(bounds, clip)) {
   *         SkPoint monoY[5];
   *         int countY = SkChopQuadAtYExtrema(srcPts, monoY);
   *         for (int y = 0; y <= countY; y++) {
   *             SkPoint monoX[5];
   *             int countX = SkChopQuadAtXExtrema(&monoY[y * 2], monoX);
   *             for (int x = 0; x <= countX; x++) {
   *                 this->clipMonoQuad(&monoX[x * 2], clip);
   *                 SkASSERT(fCurrVerb - fVerbs < kMaxVerbs);
   *                 SkASSERT(fCurrPoint - fPoints <= kMaxPoints);
   *             }
   *         }
   *     }
   *
   *     fCurrVerbStop = fCurrVerb;
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *     return fCurrVerbStop != fCurrVerb;
   * }
   * ```
   */
  public fun clipQuad(pts: Array<SkPoint>, clip: SkRect): Boolean {
    TODO("Implement clipQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEdgeClipper::clipCubic(const SkPoint srcPts[4], const SkRect& clip) {
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *
   *     const SkRect bounds = compute_cubic_bounds(srcPts);
   *     // check if we're clipped out vertically
   *     if (bounds.fBottom > clip.fTop && bounds.fTop < clip.fBottom) {
   *         if (too_big_for_reliable_float_math(bounds)) {
   *             // can't safely clip the cubic, so we give up and draw a line (which we can safely clip)
   *             //
   *             // If we rewrote chopcubicat*extrema and chopmonocubic using doubles, we could very
   *             // likely always handle the cubic safely, but (it seems) at a big loss in speed, so
   *             // we'd only want to take that alternate impl if needed. Perhaps a TODO to try it.
   *             //
   *             return this->clipLine(srcPts[0], srcPts[3], clip);
   *         } else {
   *             SkPoint monoY[10];
   *             int countY = SkChopCubicAtYExtrema(srcPts, monoY);
   *             for (int y = 0; y <= countY; y++) {
   *                 SkPoint monoX[10];
   *                 int countX = SkChopCubicAtXExtrema(&monoY[y * 3], monoX);
   *                 for (int x = 0; x <= countX; x++) {
   *                     this->clipMonoCubic(&monoX[x * 3], clip);
   *                     SkASSERT(fCurrVerb - fVerbs < kMaxVerbs);
   *                     SkASSERT(fCurrPoint - fPoints <= kMaxPoints);
   *                 }
   *             }
   *         }
   *     }
   *
   *     fCurrVerbStop = fCurrVerb;
   *     fCurrPoint = fPoints;
   *     fCurrVerb = fVerbs;
   *     return fCurrVerbStop != fCurrVerb;
   * }
   * ```
   */
  public fun clipCubic(pts: Array<SkPoint>, clip: SkRect): Boolean {
    TODO("Implement clipCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathVerb> SkEdgeClipper::next(SkPoint pts[]) {
   *     SkASSERT(fCurrVerb <= fCurrVerbStop);
   *     if (fCurrVerb >= fCurrVerbStop) {
   *         return {};
   *     }
   *
   *     auto verb = *fCurrVerb++;
   *     switch (verb) {
   *         case SkPathVerb::kLine:
   *             memcpy(pts, fCurrPoint, 2 * sizeof(SkPoint));
   *             fCurrPoint += 2;
   *             break;
   *         case SkPathVerb::kQuad:
   *             memcpy(pts, fCurrPoint, 3 * sizeof(SkPoint));
   *             fCurrPoint += 3;
   *             break;
   *         case SkPathVerb::kCubic:
   *             memcpy(pts, fCurrPoint, 4 * sizeof(SkPoint));
   *             fCurrPoint += 4;
   *             break;
   *         default:
   *             SkDEBUGFAIL("unexpected verb in quadclippper2 iter");
   *             break;
   *     }
   *     return verb;
   * }
   * ```
   */
  public fun next(pts: Array<SkPoint>): Int {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canCullToTheRight() const { return fCanCullToTheRight; }
   * ```
   */
  public fun canCullToTheRight(): Boolean {
    TODO("Implement canCullToTheRight")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::clipMonoQuad(const SkPoint srcPts[3], const SkRect& clip) {
   *     SkPoint pts[3];
   *     bool reverse = sort_increasing_Y(pts, srcPts, 3);
   *
   *     // are we completely above or below
   *     if (pts[2].fY <= clip.fTop || pts[0].fY >= clip.fBottom) {
   *         return;
   *     }
   *
   *     // Now chop so that pts is contained within clip in Y
   *     chop_quad_in_Y(pts, clip);
   *
   *     if (pts[0].fX > pts[2].fX) {
   *         using std::swap;
   *         swap(pts[0], pts[2]);
   *         reverse = !reverse;
   *     }
   *     SkASSERT(pts[0].fX <= pts[1].fX);
   *     SkASSERT(pts[1].fX <= pts[2].fX);
   *
   *     // Now chop in X has needed, and record the segments
   *
   *     if (pts[2].fX <= clip.fLeft) {  // wholly to the left
   *         this->appendVLine(clip.fLeft, pts[0].fY, pts[2].fY, reverse);
   *         return;
   *     }
   *     if (pts[0].fX >= clip.fRight) {  // wholly to the right
   *         if (!this->canCullToTheRight()) {
   *             this->appendVLine(clip.fRight, pts[0].fY, pts[2].fY, reverse);
   *         }
   *         return;
   *     }
   *
   *     SkScalar t;
   *     SkPoint tmp[5]; // for SkChopQuadAt
   *
   *     // are we partially to the left
   *     if (pts[0].fX < clip.fLeft) {
   *         if (chopMonoQuadAtX(pts, clip.fLeft, &t)) {
   *             SkChopQuadAt(pts, tmp, t);
   *             this->appendVLine(clip.fLeft, tmp[0].fY, tmp[2].fY, reverse);
   *             // clamp to clean up imprecise numerics in the chop
   *             tmp[2].fX = clip.fLeft;
   *             clamp_ge(tmp[3].fX, clip.fLeft);
   *
   *             pts[0] = tmp[2];
   *             pts[1] = tmp[3];
   *         } else {
   *             // if chopMonoQuadAtY failed, then we may have hit inexact numerics
   *             // so we just clamp against the left
   *             this->appendVLine(clip.fLeft, pts[0].fY, pts[2].fY, reverse);
   *             return;
   *         }
   *     }
   *
   *     // are we partially to the right
   *     if (pts[2].fX > clip.fRight) {
   *         if (chopMonoQuadAtX(pts, clip.fRight, &t)) {
   *             SkChopQuadAt(pts, tmp, t);
   *             // clamp to clean up imprecise numerics in the chop
   *             clamp_le(tmp[1].fX, clip.fRight);
   *             tmp[2].fX = clip.fRight;
   *
   *             this->appendQuad(tmp, reverse);
   *             this->appendVLine(clip.fRight, tmp[2].fY, tmp[4].fY, reverse);
   *         } else {
   *             // if chopMonoQuadAtY failed, then we may have hit inexact numerics
   *             // so we just clamp against the right
   *             pts[1].fX = std::min(pts[1].fX, clip.fRight);
   *             pts[2].fX = std::min(pts[2].fX, clip.fRight);
   *             this->appendQuad(pts, reverse);
   *         }
   *     } else {    // wholly inside the clip
   *         this->appendQuad(pts, reverse);
   *     }
   * }
   * ```
   */
  private fun clipMonoQuad(srcPts: Array<SkPoint>, clip: SkRect) {
    TODO("Implement clipMonoQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::clipMonoCubic(const SkPoint src[4], const SkRect& clip) {
   *     SkPoint pts[4];
   *     bool reverse = sort_increasing_Y(pts, src, 4);
   *
   *     // are we completely above or below
   *     if (pts[3].fY <= clip.fTop || pts[0].fY >= clip.fBottom) {
   *         return;
   *     }
   *
   *     // Now chop so that pts is contained within clip in Y
   *     chop_cubic_in_Y(pts, clip);
   *
   *     if (pts[0].fX > pts[3].fX) {
   *         using std::swap;
   *         swap(pts[0], pts[3]);
   *         swap(pts[1], pts[2]);
   *         reverse = !reverse;
   *     }
   *
   *     // Now chop in X has needed, and record the segments
   *
   *     if (pts[3].fX <= clip.fLeft) {  // wholly to the left
   *         this->appendVLine(clip.fLeft, pts[0].fY, pts[3].fY, reverse);
   *         return;
   *     }
   *     if (pts[0].fX >= clip.fRight) {  // wholly to the right
   *         if (!this->canCullToTheRight()) {
   *             this->appendVLine(clip.fRight, pts[0].fY, pts[3].fY, reverse);
   *         }
   *         return;
   *     }
   *
   *     // are we partially to the left
   *     if (pts[0].fX < clip.fLeft) {
   *         SkPoint tmp[7];
   *         chop_mono_cubic_at_x(pts, clip.fLeft, tmp);
   *         this->appendVLine(clip.fLeft, tmp[0].fY, tmp[3].fY, reverse);
   *
   *         // tmp[3, 4].fX should all be to the right of clip.fLeft.
   *         // Since we can't trust the numerics of
   *         // the chopper, we force those conditions now
   *         tmp[3].fX = clip.fLeft;
   *         clamp_ge(tmp[4].fX, clip.fLeft);
   *
   *         pts[0] = tmp[3];
   *         pts[1] = tmp[4];
   *         pts[2] = tmp[5];
   *     }
   *
   *     // are we partially to the right
   *     if (pts[3].fX > clip.fRight) {
   *         SkPoint tmp[7];
   *         chop_mono_cubic_at_x(pts, clip.fRight, tmp);
   *         tmp[3].fX = clip.fRight;
   *         clamp_le(tmp[2].fX, clip.fRight);
   *
   *         this->appendCubic(tmp, reverse);
   *         this->appendVLine(clip.fRight, tmp[3].fY, tmp[6].fY, reverse);
   *     } else {    // wholly inside the clip
   *         this->appendCubic(pts, reverse);
   *     }
   * }
   * ```
   */
  private fun clipMonoCubic(srcPts: Array<SkPoint>, clip: SkRect) {
    TODO("Implement clipMonoCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::appendLine(SkPoint p0, SkPoint p1) {
   *     *fCurrVerb++ = SkPathVerb::kLine;
   *     fCurrPoint[0] = p0;
   *     fCurrPoint[1] = p1;
   *     fCurrPoint += 2;
   * }
   * ```
   */
  private fun appendLine(p0: SkPoint, p1: SkPoint) {
    TODO("Implement appendLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::appendVLine(SkScalar x, SkScalar y0, SkScalar y1, bool reverse) {
   *     *fCurrVerb++ = SkPathVerb::kLine;
   *
   *     if (reverse) {
   *         using std::swap;
   *         swap(y0, y1);
   *     }
   *     fCurrPoint[0].set(x, y0);
   *     fCurrPoint[1].set(x, y1);
   *     fCurrPoint += 2;
   * }
   * ```
   */
  private fun appendVLine(
    x: SkScalar,
    y0: SkScalar,
    y1: SkScalar,
    reverse: Boolean,
  ) {
    TODO("Implement appendVLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::appendQuad(const SkPoint pts[3], bool reverse) {
   *     *fCurrVerb++ = SkPathVerb::kQuad;
   *
   *     if (reverse) {
   *         fCurrPoint[0] = pts[2];
   *         fCurrPoint[2] = pts[0];
   *     } else {
   *         fCurrPoint[0] = pts[0];
   *         fCurrPoint[2] = pts[2];
   *     }
   *     fCurrPoint[1] = pts[1];
   *     fCurrPoint += 3;
   * }
   * ```
   */
  private fun appendQuad(pts: Array<SkPoint>, reverse: Boolean) {
    TODO("Implement appendQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEdgeClipper::appendCubic(const SkPoint pts[4], bool reverse) {
   *     *fCurrVerb++ = SkPathVerb::kCubic;
   *
   *     if (reverse) {
   *         for (int i = 0; i < 4; i++) {
   *             fCurrPoint[i] = pts[3 - i];
   *         }
   *     } else {
   *         memcpy(fCurrPoint, pts, 4 * sizeof(SkPoint));
   *     }
   *     fCurrPoint += 4;
   * }
   * ```
   */
  private fun appendCubic(pts: Array<SkPoint>, reverse: Boolean) {
    TODO("Implement appendCubic")
  }

  public companion object {
    public val kMaxVerbs: Int = TODO("Initialize kMaxVerbs")

    public val kMaxPoints: Int = TODO("Initialize kMaxPoints")

    /**
     * C++ original:
     * ```cpp
     * void SkEdgeClipper::ClipPath(const SkPathRaw& raw, const SkRect& clip, bool canCullToTheRight,
     *                              void (*consume)(SkEdgeClipper*, bool newCtr, void* ctx), void* ctx) {
     *     SkAutoConicToQuads quadder;
     *     constexpr float kConicTol = 0.25f;
     *
     *     SkPathEdgeIter iter(raw);
     *     SkEdgeClipper clipper(canCullToTheRight);
     *
     *     while (auto e = iter.next()) {
     *         switch (e.fEdge) {
     *             case SkPathEdgeIter::Edge::kLine:
     *                 if (clipper.clipLine(e.fPts[0], e.fPts[1], clip)) {
     *                     consume(&clipper, e.fIsNewContour, ctx);
     *                 }
     *                 break;
     *             case SkPathEdgeIter::Edge::kQuad:
     *                 if (clipper.clipQuad(e.fPts, clip)) {
     *                     consume(&clipper, e.fIsNewContour, ctx);
     *                 }
     *                 break;
     *             case SkPathEdgeIter::Edge::kConic: {
     *                 const SkPoint* quadPts =
     *                         quadder.computeQuads(e.fPts, iter.conicWeight(), kConicTol);
     *                 for (int i = 0; i < quadder.countQuads(); ++i) {
     *                     if (clipper.clipQuad(quadPts, clip)) {
     *                         consume(&clipper, e.fIsNewContour, ctx);
     *                     }
     *                     quadPts += 2;
     *                 }
     *             } break;
     *             case SkPathEdgeIter::Edge::kCubic:
     *                 if (clipper.clipCubic(e.fPts, clip)) {
     *                     consume(&clipper, e.fIsNewContour, ctx);
     *                 }
     *                 break;
     *             default:
     *                 SkDEBUGFAIL("Unknown edge type");
     *                 break;
     *         }
     *     }
     * }
     * ```
     */
    public fun clipPath(
      raw: SkPathRaw,
      clip: SkRect,
      canCullToTheRight: Boolean,
      param3: (
        Any?,
        Boolean,
        Int,
      ) -> Unit,
      ctx: Unit?,
    ) {
      TODO("Implement clipPath")
    }
  }
}
