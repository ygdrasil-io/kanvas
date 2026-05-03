package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPathDirection
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkStroke {
 * public:
 *     SkStroke();
 *     explicit SkStroke(const SkPaint&);
 *     SkStroke(const SkPaint&, SkScalar width);   // width overrides paint.getStrokeWidth()
 *
 *     SkPaint::Cap getCap() const { return (SkPaint::Cap)fCap; }
 *     void         setCap(SkPaint::Cap);
 *
 *     SkPaint::Join getJoin() const { return (SkPaint::Join)fJoin; }
 *     void          setJoin(SkPaint::Join);
 *
 *     void    setMiterLimit(SkScalar);
 *     void    setWidth(SkScalar);
 *
 *     bool    getDoFill() const { return SkToBool(fDoFill); }
 *     void    setDoFill(bool doFill) { fDoFill = SkToU8(doFill); }
 *
 *     /**
 *      *  ResScale is the "intended" resolution for the output.
 *      *      Default is 1.0.
 *      *      Larger values (res > 1) indicate that the result should be more precise, since it will
 *      *          be zoomed up, and small errors will be magnified.
 *      *      Smaller values (0 < res < 1) indicate that the result can be less precise, since it will
 *      *          be zoomed down, and small errors may be invisible.
 *      */
 *     SkScalar getResScale() const { return fResScale; }
 *     void setResScale(SkScalar rs) {
 *         SkASSERT(rs > 0 && std::isfinite(rs));
 *         fResScale = rs;
 *     }
 *
 *     /**
 *      *  Stroke the specified rect, winding it in the specified direction..
 *      */
 *     void    strokeRect(const SkRect& rect, SkPathBuilder* result,
 *                        SkPathDirection = SkPathDirection::kCW) const;
 *     void    strokePath(const SkPath& path, SkPathBuilder*) const;
 *
 *     ////////////////////////////////////////////////////////////////
 *
 * private:
 *     SkScalar    fWidth, fMiterLimit;
 *     SkScalar    fResScale;
 *     uint8_t     fCap, fJoin;
 *     bool        fDoFill;
 *
 *     friend class SkPaint;
 * }
 * ```
 */
public data class SkStroke public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fWidth
   * ```
   */
  private var fWidth: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fWidth, fMiterLimit
   * ```
   */
  private var fMiterLimit: SkScalar,
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
   * uint8_t     fCap
   * ```
   */
  private var fCap: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t     fCap, fJoin
   * ```
   */
  private var fJoin: Int,
  /**
   * C++ original:
   * ```cpp
   * bool        fDoFill
   * ```
   */
  private var fDoFill: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPaint::Cap getCap() const { return (SkPaint::Cap)fCap; }
   * ```
   */
  public fun getCap(): SkPaint.Cap {
    TODO("Implement getCap")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::setCap(SkPaint::Cap cap) {
   *     SkASSERT((unsigned)cap < SkPaint::kCapCount);
   *     fCap = SkToU8(cap);
   * }
   * ```
   */
  public fun setCap(cap: SkPaint.Cap) {
    TODO("Implement setCap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join getJoin() const { return (SkPaint::Join)fJoin; }
   * ```
   */
  public fun getJoin(): SkPaint.Join {
    TODO("Implement getJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::setJoin(SkPaint::Join join) {
   *     SkASSERT((unsigned)join < SkPaint::kJoinCount);
   *     fJoin = SkToU8(join);
   * }
   * ```
   */
  public fun setJoin(join: SkPaint.Join) {
    TODO("Implement setJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::setMiterLimit(SkScalar miterLimit) {
   *     SkASSERT(miterLimit >= 0);
   *     fMiterLimit = miterLimit;
   * }
   * ```
   */
  public fun setMiterLimit(miterLimit: SkScalar) {
    TODO("Implement setMiterLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::setWidth(SkScalar width) {
   *     SkASSERT(width >= 0);
   *     fWidth = width;
   * }
   * ```
   */
  public fun setWidth(width: SkScalar) {
    TODO("Implement setWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * bool    getDoFill() const { return SkToBool(fDoFill); }
   * ```
   */
  public fun getDoFill(): Boolean {
    TODO("Implement getDoFill")
  }

  /**
   * C++ original:
   * ```cpp
   * void    setDoFill(bool doFill) { fDoFill = SkToU8(doFill); }
   * ```
   */
  public fun setDoFill(doFill: Boolean) {
    TODO("Implement setDoFill")
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
   * void setResScale(SkScalar rs) {
   *         SkASSERT(rs > 0 && std::isfinite(rs));
   *         fResScale = rs;
   *     }
   * ```
   */
  public fun setResScale(rs: SkScalar) {
    TODO("Implement setResScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::strokeRect(const SkRect& origRect, SkPathBuilder* dst,
   *                           SkPathDirection dir) const {
   *     SkASSERT(dst != nullptr);
   *     dst->reset();
   *
   *     SkScalar radius = SkScalarHalf(fWidth);
   *     if (radius <= 0) {
   *         return;
   *     }
   *
   *     SkScalar rw = origRect.width();
   *     SkScalar rh = origRect.height();
   *     if ((rw < 0) ^ (rh < 0)) {
   *         dir = reverse_direction(dir);
   *     }
   *     SkRect rect(origRect);
   *     rect.sort();
   *     // reassign these, now that we know they'll be >= 0
   *     rw = rect.width();
   *     rh = rect.height();
   *
   *     SkRect r(rect);
   *     r.outset(radius, radius);
   *
   *     SkPaint::Join join = (SkPaint::Join)fJoin;
   *     if (SkPaint::kMiter_Join == join && fMiterLimit < SK_ScalarSqrt2) {
   *         join = SkPaint::kBevel_Join;
   *     }
   *
   *     switch (join) {
   *         case SkPaint::kMiter_Join:
   *             dst->addRect(r, dir);
   *             break;
   *         case SkPaint::kBevel_Join:
   *             addBevel(dst, rect, r, dir);
   *             break;
   *         case SkPaint::kRound_Join:
   *             dst->addRRect(SkRRect::MakeRectXY(r, radius, radius), dir);
   *             break;
   *         default:
   *             break;
   *     }
   *
   *     if (fWidth < std::min(rw, rh) && !fDoFill) {
   *         r = rect;
   *         r.inset(radius, radius);
   *         dst->addRect(r, reverse_direction(dir));
   *     }
   * }
   * ```
   */
  public fun strokeRect(
    rect: SkRect,
    result: SkPathBuilder?,
    dir: SkPathDirection = TODO(),
  ) {
    TODO("Implement strokeRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStroke::strokePath(const SkPath& src, SkPathBuilder* dst) const {
   *     SkASSERT(dst);
   *
   *     SkScalar radius = SkScalarHalf(fWidth);
   *
   *     if (radius <= 0) {
   *         return;
   *     }
   *
   *     const auto raw = SkPathPriv::Raw(src, SkResolveConvexity::kNo);
   *     if (!raw) {
   *         return;
   *     }
   *
   *     // If src is really a rect, call our specialty strokeRect() method
   *     {
   *         SkRect rect;
   *         bool isClosed = false;
   *         SkPathDirection dir;
   *         if (src.isRect(&rect, &isClosed, &dir) && isClosed) {
   *             this->strokeRect(rect, dst, dir);
   *             // our answer should preserve the inverseness of the src
   *             if (src.isInverseFillType()) {
   *                 SkASSERT(!dst->isInverseFillType());
   *                 dst->toggleInverseFillType();
   *             }
   *             return;
   *         }
   *     }
   *
   *     // We can always ignore centers for stroke and fill convex line-only paths
   *     // TODO: remove the line-only restriction
   *     bool ignoreCenter = fDoFill && (src.getSegmentMasks() == SkPath::kLine_SegmentMask) &&
   *                         src.isLastContourClosed() && src.isConvex();
   *
   *     SkPathStroker   stroker(src, radius, fMiterLimit, this->getCap(), this->getJoin(),
   *                             fResScale, ignoreCenter);
   *
   *     SkPath::Iter iter(src, false);
   *     SkPathVerb   lastSegment = SkPathVerb::kMove;
   *     while (auto rec = iter.next()) {
   *         SkSpan<const SkPoint> pts = rec->fPoints;
   *         switch (rec->fVerb) {
   *             case SkPathVerb::kMove:
   *                 stroker.moveTo(pts[0]);
   *                 break;
   *             case SkPathVerb::kLine:
   *                 stroker.lineTo(pts[1], &iter);
   *                 lastSegment = SkPathVerb::kLine;
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 stroker.quadTo(pts[1], pts[2]);
   *                 lastSegment = SkPathVerb::kQuad;
   *                 break;
   *             case SkPathVerb::kConic: {
   *                 stroker.conicTo(pts[1], pts[2], rec->conicWeight());
   *                 lastSegment = SkPathVerb::kConic;
   *             } break;
   *             case SkPathVerb::kCubic:
   *                 stroker.cubicTo(pts[1], pts[2], pts[3]);
   *                 lastSegment = SkPathVerb::kCubic;
   *                 break;
   *             case SkPathVerb::kClose:
   *                 if (SkPaint::kButt_Cap != this->getCap()) {
   *                     /* If the stroke consists of a moveTo followed by a close, treat it
   *                        as if it were followed by a zero-length line. Lines without length
   *                        can have square and round end caps. */
   *                     if (stroker.hasOnlyMoveTo()) {
   *                         stroker.lineTo(stroker.moveToPt());
   *                         goto ZERO_LENGTH;
   *                     }
   *                     /* If the stroke consists of a moveTo followed by one or more zero-length
   *                        verbs, then followed by a close, treat is as if it were followed by a
   *                        zero-length line. Lines without length can have square & round end caps. */
   *                     if (stroker.isCurrentContourEmpty()) {
   *                 ZERO_LENGTH:
   *                         lastSegment = SkPathVerb::kLine;
   *                         break;
   *                     }
   *                 }
   *                 stroker.close(lastSegment == SkPathVerb::kLine);
   *                 break;
   *         }
   *     }
   *     stroker.done(dst, lastSegment == SkPathVerb::kLine);
   *
   *     if (fDoFill && !ignoreCenter) {
   *         auto d = SkPathPriv::ComputeFirstDirection(*raw);
   *         if (d == SkPathFirstDirection::kCCW) {
   *             dst->privateReverseAddPath(src);
   *         } else {
   *             dst->addPath(src);
   *         }
   *     } else {
   *         //  Seems like we can assume that a 2-point src would always result in
   *         //  a convex stroke, but testing has proved otherwise.
   *         //  TODO: fix the stroker to make this assumption true (without making
   *         //  it slower that the work that will be done in computeConvexity())
   * #if 0
   *         // this test results in a non-convex stroke :(
   *         static void test(SkCanvas* canvas) {
   *             SkPoint pts[] = { 146.333328,  192.333328, 300.333344, 293.333344 };
   *             SkPaint paint;
   *             paint.setStrokeWidth(7);
   *             paint.setStrokeCap(SkPaint::kRound_Cap);
   *             canvas->drawLine(pts[0].fX, pts[0].fY, pts[1].fX, pts[1].fY, paint);
   *         }
   * #endif
   * #if 0
   *         if (2 == src.countPoints()) {
   *             dst->setIsConvex(true);
   *         }
   * #endif
   *     }
   *
   *     // our answer should preserve the inverseness of the src
   *     if (src.isInverseFillType()) {
   *         SkASSERT(!dst->isInverseFillType());
   *         dst->toggleInverseFillType();
   *     }
   * }
   * ```
   */
  public fun strokePath(path: SkPath, dst: SkPathBuilder?) {
    TODO("Implement strokePath")
  }
}
