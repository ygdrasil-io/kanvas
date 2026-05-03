package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkScan {
 * public:
 *     /*
 *      *  Draws count-1 line segments, one at a time:
 *      *      line(pts[0], pts[1])
 *      *      line(pts[1], pts[2])
 *      *      line(......, pts[count - 1])
 *      */
 *     typedef void (*HairRgnProc)(SkSpan<const SkPoint>, const SkRegion*, SkBlitter*);
 *     typedef void (*HairRCProc)(SkSpan<const SkPoint>, const SkRasterClip&, SkBlitter*);
 *
 *     // Paths of a certain size cannot be anti-aliased unless externally tiled (handled by SkDraw).
 *     // SkBitmapDevice automatically tiles, SkAAClip does not so SkRasterClipStack converts AA clips
 *     // to BW clips if that's the case. SkRegion uses this to know when to tile and union smaller
 *     // SkRegions together.
 *     static bool PathRequiresTiling(const SkIRect& bounds);
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *     // rasterclip
 *
 *     static void FillIRect(const SkIRect&, const SkRasterClip&, SkBlitter*);
 *     static void FillXRect(const SkXRect&, const SkRasterClip&, SkBlitter*);
 *     static void FillRect(const SkRect&, const SkRasterClip&, SkBlitter*);
 *     static void AntiFillRect(const SkRect&, const SkRasterClip&, SkBlitter*);
 *     static void AntiFillXRect(const SkXRect&, const SkRasterClip&, SkBlitter*);
 *
 *     static void FillPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void FillPath(const SkPathRaw&, const SkRegion& clip, SkBlitter*);
 *     static void AntiFillPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *
 *     static void FrameRect(const SkRect&, const SkPoint& strokeSize,
 *                           const SkRasterClip&, SkBlitter*);
 *     static void AntiFrameRect(const SkRect&, const SkPoint& strokeSize,
 *                               const SkRasterClip&, SkBlitter*);
 *     static void FillTriangle(const SkPoint pts[], const SkRasterClip&, SkBlitter*);
 *     static void HairLine(SkSpan<const SkPoint>, const SkRasterClip&, SkBlitter*);
 *     static void AntiHairLine(SkSpan<const SkPoint>, const SkRasterClip&, SkBlitter*);
 *     static void HairRect(const SkRect&, const SkRasterClip&, SkBlitter*);
 *     static void AntiHairRect(const SkRect&, const SkRasterClip&, SkBlitter*);
 *
 *     static void HairPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void AntiHairPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void HairSquarePath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void AntiHairSquarePath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void HairRoundPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *     static void AntiHairRoundPath(const SkPathRaw&, const SkRasterClip&, SkBlitter*);
 *
 * private:
 *     friend class SkAAClip;
 *     friend class SkRegion;
 *
 *     static void FillIRect(const SkIRect&, const SkRegion* clip, SkBlitter*);
 *     static void FillXRect(const SkXRect&, const SkRegion* clip, SkBlitter*);
 *     static void FillRect(const SkRect&, const SkRegion* clip, SkBlitter*);
 *     static void AntiFillRect(const SkRect&, const SkRegion* clip, SkBlitter*);
 *     static void AntiFillXRect(const SkXRect&, const SkRegion*, SkBlitter*);
 *     static void AntiFillPath(const SkPathRaw&, const SkRegion& clip, SkBlitter*, bool forceRLE);
 *     static void FillTriangle(const SkPoint pts[], const SkRegion*, SkBlitter*);
 *
 *     static void AntiFrameRect(const SkRect&, const SkPoint& strokeSize,
 *                               const SkRegion*, SkBlitter*);
 *     static void HairLineRgn(SkSpan<const SkPoint>, const SkRegion*, SkBlitter*);
 *     static void AntiHairLineRgn(SkSpan<const SkPoint>, const SkRegion*, SkBlitter*);
 *     static void AAAFillPath(const SkPathRaw&, SkBlitter* blitter, const SkIRect& pathIR,
 *                             const SkIRect& clipBounds, bool forceRLE);
 * }
 * ```
 */
public open class SkScan {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkScan::PathRequiresTiling(const SkIRect& bounds) {
     *     SkRegion out;  // ignored
     *     return clip_to_limit(SkRegion(bounds), &out);
     * }
     * ```
     */
    public fun pathRequiresTiling(bounds: SkIRect): Boolean {
      TODO("Implement pathRequiresTiling")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillIRect(const SkIRect& r, const SkRasterClip& clip,
     *                        SkBlitter* blitter) {
     *     if (clip.isEmpty() || r.isEmpty()) {
     *         return;
     *     }
     *
     *     if (clip.isBW()) {
     *         FillIRect(r, &clip.bwRgn(), blitter);
     *         return;
     *     }
     *
     *     SkAAClipBlitterWrapper wrapper(clip, blitter);
     *     FillIRect(r, &wrapper.getRgn(), wrapper.getBlitter());
     * }
     * ```
     */
    public fun fillIRect(
      r: SkIRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillIRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillXRect(const SkXRect& xr, const SkRasterClip& clip,
     *                        SkBlitter* blitter) {
     *     if (clip.isEmpty() || xr.isEmpty()) {
     *         return;
     *     }
     *
     *     if (clip.isBW()) {
     *         FillXRect(xr, &clip.bwRgn(), blitter);
     *         return;
     *     }
     *
     *     SkAAClipBlitterWrapper wrapper(clip, blitter);
     *     FillXRect(xr, &wrapper.getRgn(), wrapper.getBlitter());
     * }
     * ```
     */
    public fun fillXRect(
      xr: SkXRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillXRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillRect(const SkRect& r, const SkRasterClip& clip,
     *                       SkBlitter* blitter) {
     *     if (clip.isEmpty() || r.isEmpty()) {
     *         return;
     *     }
     *
     *     if (clip.isBW()) {
     *         FillRect(r, &clip.bwRgn(), blitter);
     *         return;
     *     }
     *
     *     SkAAClipBlitterWrapper wrapper(clip, blitter);
     *     FillRect(r, &wrapper.getRgn(), wrapper.getBlitter());
     * }
     * ```
     */
    public fun fillRect(
      r: SkRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillRect(const SkRect& r, const SkRasterClip& clip,
     *                           SkBlitter* blitter) {
     *     if (clip.isBW()) {
     *         AntiFillRect(r, &clip.bwRgn(), blitter);
     *     } else {
     *         SkAAClipBlitterWrapper wrap(clip, blitter);
     *         AntiFillRect(r, &wrap.getRgn(), wrap.getBlitter());
     *     }
     * }
     * ```
     */
    public fun antiFillRect(
      r: SkRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFillRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillXRect(const SkXRect& xr, const SkRasterClip& clip,
     *                            SkBlitter* blitter) {
     *     if (clip.isBW()) {
     *         AntiFillXRect(xr, &clip.bwRgn(), blitter);
     *     } else {
     *         SkIRect outerBounds;
     *         XRect_roundOut(xr, &outerBounds);
     *
     *         if (clip.quickContains(outerBounds)) {
     *             AntiFillXRect(xr, nullptr, blitter);
     *         } else {
     *             SkAAClipBlitterWrapper wrapper(clip, blitter);
     *             AntiFillXRect(xr, &wrapper.getRgn(), wrapper.getBlitter());
     *         }
     *     }
     * }
     * ```
     */
    public fun antiFillXRect(
      xr: SkXRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFillXRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     if (clip.isEmpty()) {
     *         return;
     *     }
     *
     *     if (clip.isBW()) {
     *         SkScan::FillPath(raw, clip.bwRgn(), blitter);
     *     } else {
     *         SkRegion        tmp;
     *         SkAAClipBlitter aaBlitter;
     *
     *         tmp.setRect(clip.getBounds());
     *         aaBlitter.init(blitter, &clip.aaRgn());
     *         SkScan::FillPath(raw, tmp, &aaBlitter);
     *     }
     * }
     * ```
     */
    public fun fillPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillPath(const SkPathRaw& raw, const SkRegion& origClip, SkBlitter* blitter) {
     *     if (origClip.isEmpty()) {
     *         return;
     *     }
     *
     *     // Our edges are fixed-point, and don't like the bounds of the clip to
     *     // exceed that. Here we trim the clip just so we don't overflow later on
     *     const SkRegion* clipPtr = &origClip;
     *     SkRegion finiteClip;
     *     if (clip_to_limit(origClip, &finiteClip)) {
     *         if (finiteClip.isEmpty()) {
     *             return;
     *         }
     *         clipPtr = &finiteClip;
     *     }
     *     // don't reference "origClip" any more, just use clipPtr
     *
     *
     *     SkRect bounds = raw.bounds();
     *     bool irPreClipped = false;
     *     if (!SkRectPriv::MakeLargeS32().contains(bounds)) {
     *         if (!bounds.intersect(SkRectPriv::MakeLargeS32())) {
     *             bounds.setEmpty();
     *         }
     *         irPreClipped = true;
     *     }
     *
     *     SkIRect ir = conservative_round_to_int(bounds);
     *     if (ir.isEmpty()) {
     *         if (raw.isInverseFillType()) {
     *             blitter->blitRegion(*clipPtr);
     *         }
     *         return;
     *     }
     *
     *     SkScanClipper clipper(blitter, clipPtr, ir, raw.isInverseFillType(), irPreClipped);
     *
     *     blitter = clipper.getBlitter();
     *     if (blitter) {
     *         // we have to keep our calls to blitter in sorted order, so we
     *         // must blit the above section first, then the middle, then the bottom.
     *         if (raw.isInverseFillType()) {
     *             sk_blit_above(blitter, ir, *clipPtr);
     *         }
     *         SkASSERT(clipper.getClipRect() == nullptr ||
     *                 *clipper.getClipRect() == clipPtr->getBounds());
     *         sk_fill_path(raw, clipPtr->getBounds(), blitter, ir.fTop, ir.fBottom,
     *                      clipper.getClipRect() == nullptr);
     *         if (raw.isInverseFillType()) {
     *             sk_blit_below(blitter, ir, *clipPtr);
     *         }
     *     } else {
     *         // what does it mean to not have a blitter if path.isInverseFillType???
     *     }
     * }
     * ```
     */
    public fun fillPath(
      raw: SkPathRaw,
      clip: SkRegion,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     SkASSERT(raw.bounds().isFinite());
     *     if (clip.isEmpty()) {
     *         return;
     *     }
     *
     *     if (clip.isBW()) {
     *         AntiFillPath(raw, clip.bwRgn(), blitter, false);
     *     } else {
     *         SkRegion        tmp;
     *         SkAAClipBlitter aaBlitter;
     *
     *         tmp.setRect(clip.getBounds());
     *         aaBlitter.init(blitter, &clip.aaRgn());
     *         AntiFillPath(raw, tmp, &aaBlitter, true); // SkAAClipBlitter can blitMask, why forceRLE?
     *     }
     * }
     * ```
     */
    public fun antiFillPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFillPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FrameRect(const SkRect& r, const SkPoint& strokeSize,
     *                        const SkRasterClip& clip, SkBlitter* blitter) {
     *     SkASSERT(strokeSize.fX >= 0 && strokeSize.fY >= 0);
     *
     *     if (strokeSize.fX < 0 || strokeSize.fY < 0) {
     *         return;
     *     }
     *
     *     const SkScalar dx = strokeSize.fX;
     *     const SkScalar dy = strokeSize.fY;
     *     SkScalar rx = SkScalarHalf(dx);
     *     SkScalar ry = SkScalarHalf(dy);
     *     SkRect   outer, tmp;
     *
     *     outer.setLTRB(r.fLeft - rx, r.fTop - ry, r.fRight + rx, r.fBottom + ry);
     *
     *     if (r.width() <= dx || r.height() <= dy) {
     *         // If we're empty on either axis, we remove the outset amount, to be sure
     *         // we stroke the same way a polygon would (i.e. it would just see a "line"
     *         // and not extend it for the miter join).
     *         if (r.width() == 0) {
     *             outer.fTop = r.fTop;
     *             outer.fBottom = r.fBottom;
     *         }
     *         if (r.height() == 0) {
     *             outer.fLeft = r.fLeft;
     *             outer.fRight = r.fRight;
     *         }
     *         SkScan::FillRect(outer, clip, blitter);
     *         return;
     *     }
     *
     *     tmp.setLTRB(outer.fLeft, outer.fTop, outer.fRight, outer.fTop + dy);
     *     SkScan::FillRect(tmp, clip, blitter);
     *     tmp.fTop = outer.fBottom - dy;
     *     tmp.fBottom = outer.fBottom;
     *     SkScan::FillRect(tmp, clip, blitter);
     *
     *     tmp.setLTRB(outer.fLeft, outer.fTop + dy, outer.fLeft + dx, outer.fBottom - dy);
     *     SkScan::FillRect(tmp, clip, blitter);
     *     tmp.fLeft = outer.fRight - dx;
     *     tmp.fRight = outer.fRight;
     *     SkScan::FillRect(tmp, clip, blitter);
     * }
     * ```
     */
    public fun frameRect(
      r: SkRect,
      strokeSize: SkPoint,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement frameRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFrameRect(const SkRect& r, const SkPoint& strokeSize,
     *                            const SkRasterClip& clip, SkBlitter* blitter) {
     *     if (clip.isBW()) {
     *         AntiFrameRect(r, strokeSize, &clip.bwRgn(), blitter);
     *     } else {
     *         SkAAClipBlitterWrapper wrap(clip, blitter);
     *         AntiFrameRect(r, strokeSize, &wrap.getRgn(), wrap.getBlitter());
     *     }
     * }
     * ```
     */
    public fun antiFrameRect(
      r: SkRect,
      strokeSize: SkPoint,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFrameRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillTriangle(const SkPoint pts[], const SkRasterClip& clip,
     *                           SkBlitter* blitter) {
     *     if (clip.isEmpty()) {
     *         return;
     *     }
     *
     *     const auto r = SkRect::Bounds({pts, 3});
     *     if (!r) {
     *         return;
     *     }
     *
     *     // If r is too large (larger than can easily fit in SkFixed) then we need perform geometric
     *     // clipping. This is a bit of work, so we just call the general FillPath() to handle it.
     *     // Use FixedMax/2 as the limit so we can subtract two edges and still store that in Fixed.
     *     const SkScalar limit = SK_MaxS16 >> 1;
     *     if (!SkRect::MakeLTRB(-limit, -limit, limit, limit).contains(*r)) {
     *         SkPathRawShapes::Triangle tri({pts, 3}, *r);
     *         FillPath(tri, clip, blitter);
     *         return;
     *     }
     *
     *     SkIRect ir = conservative_round_to_int(*r);
     *     if (ir.isEmpty() || !SkIRect::Intersects(ir, clip.getBounds())) {
     *         return;
     *     }
     *
     *     SkAAClipBlitterWrapper wrap;
     *     const SkRegion* clipRgn;
     *     if (clip.isBW()) {
     *         clipRgn = &clip.bwRgn();
     *     } else {
     *         wrap.init(clip, blitter);
     *         clipRgn = &wrap.getRgn();
     *         blitter = wrap.getBlitter();
     *     }
     *
     *     SkScanClipper clipper(blitter, clipRgn, ir);
     *     blitter = clipper.getBlitter();
     *     if (blitter) {
     *         sk_fill_triangle(pts, clipper.getClipRect(), blitter, ir);
     *     }
     * }
     * ```
     */
    public fun fillTriangle(
      pts: Array<SkPoint>,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillTriangle")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairLine(SkSpan<const SkPoint> pts, const SkRasterClip& clip, SkBlitter* blitter) {
     *     if (clip.isBW()) {
     *         HairLineRgn(pts, &clip.bwRgn(), blitter);
     *     } else {
     *         const SkRegion* clipRgn = nullptr;
     *
     *         const auto r = SkRect::BoundsOrEmpty(pts).makeOutset(SK_ScalarHalf, SK_ScalarHalf);
     *
     *         SkAAClipBlitterWrapper wrap;
     *         if (!clip.quickContains(r.roundOut())) {
     *             wrap.init(clip, blitter);
     *             blitter = wrap.getBlitter();
     *             clipRgn = &wrap.getRgn();
     *         }
     *         HairLineRgn(pts, clipRgn, blitter);
     *     }
     * }
     * ```
     */
    public fun hairLine(
      pts: SkSpan<SkPoint>,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement hairLine")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairLine(SkSpan<const SkPoint> pts, const SkRasterClip& clip, SkBlitter* blitter) {
     *     if (clip.isBW()) {
     *         AntiHairLineRgn(pts, &clip.bwRgn(), blitter);
     *     } else {
     *         const SkRegion* clipRgn = nullptr;
     *
     *         const auto r = SkRect::BoundsOrEmpty(pts);
     *
     *         SkAAClipBlitterWrapper wrap;
     *         if (!clip.quickContains(r.roundOut().makeOutset(1, 1))) {
     *             wrap.init(clip, blitter);
     *             blitter = wrap.getBlitter();
     *             clipRgn = &wrap.getRgn();
     *         }
     *         AntiHairLineRgn(pts, clipRgn, blitter);
     *     }
     * }
     * ```
     */
    public fun antiHairLine(
      pts: SkSpan<SkPoint>,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairLine")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairRect(const SkRect& rect, const SkRasterClip& clip, SkBlitter* blitter) {
     *     SkAAClipBlitterWrapper wrapper;
     *     SkBlitterClipper clipper;
     *     // Create the enclosing bounds of the hairrect. i.e. we will stroke the interior of r.
     *     SkIRect r = SkIRect::MakeLTRB(SkScalarFloorToInt(rect.fLeft),
     *                                   SkScalarFloorToInt(rect.fTop),
     *                                   SkScalarFloorToInt(rect.fRight + 1),
     *                                   SkScalarFloorToInt(rect.fBottom + 1));
     *
     *     // Note: r might be crazy big, if rect was huge, possibly getting pinned to max/min s32.
     *     // We need to trim it back to something reasonable before we can query its width etc.
     *     // since r.fRight - r.fLeft might wrap around to negative even if fRight > fLeft.
     *     //
     *     // We outset the clip bounds by 1 before intersecting, since r is being stroked and not filled
     *     // so we don't want to pin an edge of it to the clip. The intersect's job is mostly to just
     *     // get the actual edge values into a reasonable range (e.g. so width() can't overflow).
     *     if (!r.intersect(clip.getBounds().makeOutset(1, 1))) {
     *         return;
     *     }
     *
     *     if (clip.quickReject(r)) {
     *         return;
     *     }
     *     if (!clip.quickContains(r)) {
     *         const SkRegion* clipRgn;
     *         if (clip.isBW()) {
     *             clipRgn = &clip.bwRgn();
     *         } else {
     *             wrapper.init(clip, blitter);
     *             clipRgn = &wrapper.getRgn();
     *             blitter = wrapper.getBlitter();
     *         }
     *         blitter = clipper.apply(blitter, clipRgn);
     *     }
     *
     *     int width = r.width();
     *     int height = r.height();
     *
     *     if ((width | height) == 0) {
     *         return;
     *     }
     *     if (width <= 2 || height <= 2) {
     *         blitter->blitRect(r.fLeft, r.fTop, width, height);
     *         return;
     *     }
     *     // if we get here, we know we have 4 segments to draw
     *     blitter->blitH(r.fLeft, r.fTop, width);                     // top
     *     blitter->blitRect(r.fLeft, r.fTop + 1, 1, height - 2);      // left
     *     blitter->blitRect(r.fRight - 1, r.fTop + 1, 1, height - 2); // right
     *     blitter->blitH(r.fLeft, r.fBottom - 1, width);              // bottom
     * }
     * ```
     */
    public fun hairRect(
      rect: SkRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement hairRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairRect(const SkRect& rect, const SkRasterClip& clip,
     *                           SkBlitter* blitter) {
     *     SkPoint pts[5];
     *
     *     pts[0].set(rect.fLeft, rect.fTop);
     *     pts[1].set(rect.fRight, rect.fTop);
     *     pts[2].set(rect.fRight, rect.fBottom);
     *     pts[3].set(rect.fLeft, rect.fBottom);
     *     pts[4] = pts[0];
     *     SkScan::AntiHairLine(pts, clip, blitter);
     * }
     * ```
     */
    public fun antiHairRect(
      rect: SkRect,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kButt_Cap>(raw, clip, blitter, SkScan::HairLineRgn);
     * }
     * ```
     */
    public fun hairPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement hairPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kButt_Cap>(raw, clip, blitter, SkScan::AntiHairLineRgn);
     * }
     * ```
     */
    public fun antiHairPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairSquarePath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kSquare_Cap>(raw, clip, blitter, SkScan::HairLineRgn);
     * }
     * ```
     */
    public fun hairSquarePath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement hairSquarePath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairSquarePath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kSquare_Cap>(raw, clip, blitter, SkScan::AntiHairLineRgn);
     * }
     * ```
     */
    public fun antiHairSquarePath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairSquarePath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairRoundPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kRound_Cap>(raw, clip, blitter, SkScan::HairLineRgn);
     * }
     * ```
     */
    public fun hairRoundPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement hairRoundPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairRoundPath(const SkPathRaw& raw, const SkRasterClip& clip, SkBlitter* blitter) {
     *     hair_path<SkPaint::kRound_Cap>(raw, clip, blitter, SkScan::AntiHairLineRgn);
     * }
     * ```
     */
    public fun antiHairRoundPath(
      raw: SkPathRaw,
      clip: SkRasterClip,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairRoundPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillIRect(const SkIRect& r, const SkRegion* clip,
     *                        SkBlitter* blitter) {
     *     if (!r.isEmpty()) {
     *         if (clip) {
     *             if (clip->isRect()) {
     *                 const SkIRect& clipBounds = clip->getBounds();
     *
     *                 if (clipBounds.contains(r)) {
     *                     blitrect(blitter, r);
     *                 } else {
     *                     SkIRect rr = r;
     *                     if (rr.intersect(clipBounds)) {
     *                         blitrect(blitter, rr);
     *                     }
     *                 }
     *             } else {
     *                 SkRegion::Cliperator    cliper(*clip, r);
     *                 const SkIRect&          rr = cliper.rect();
     *
     *                 while (!cliper.done()) {
     *                     blitrect(blitter, rr);
     *                     cliper.next();
     *                 }
     *             }
     *         } else {
     *             blitrect(blitter, r);
     *         }
     *     }
     * }
     * ```
     */
    private fun fillIRect(
      r: SkIRect,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillIRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillXRect(const SkXRect& xr, const SkRegion* clip,
     *                        SkBlitter* blitter) {
     *     SkIRect r;
     *
     *     XRect_round(xr, &r);
     *     SkScan::FillIRect(r, clip, blitter);
     * }
     * ```
     */
    private fun fillXRect(
      xr: SkXRect,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillXRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::FillRect(const SkRect& r, const SkRegion* clip,
     *                        SkBlitter* blitter) {
     *     SkIRect ir;
     *
     *     r.round(&ir);
     *     SkScan::FillIRect(ir, clip, blitter);
     * }
     * ```
     */
    private fun fillRect(
      r: SkRect,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement fillRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillRect(const SkRect& origR, const SkRegion* clip,
     *                           SkBlitter* blitter) {
     *     if (clip) {
     *         SkRect newR;
     *         newR.set(clip->getBounds());
     *         if (!newR.intersect(origR)) {
     *             return;
     *         }
     *
     *         const SkIRect outerBounds = newR.roundOut();
     *
     *         if (clip->isRect()) {
     *             antifillrect(newR, blitter);
     *         } else {
     *             SkRegion::Cliperator clipper(*clip, outerBounds);
     *             while (!clipper.done()) {
     *                 newR.set(clipper.rect());
     *                 if (newR.intersect(origR)) {
     *                     antifillrect(newR, blitter);
     *                 }
     *                 clipper.next();
     *             }
     *         }
     *     } else {
     *         antifillrect(origR, blitter);
     *     }
     * }
     * ```
     */
    private fun antiFillRect(
      origR: SkRect,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFillRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillXRect(const SkXRect& xr, const SkRegion* clip,
     *                           SkBlitter* blitter) {
     *     if (nullptr == clip) {
     *         antifillrect(xr, blitter);
     *     } else {
     *         SkIRect outerBounds;
     *         XRect_roundOut(xr, &outerBounds);
     *
     *         if (clip->isRect()) {
     *             const SkIRect& clipBounds = clip->getBounds();
     *
     *             if (clipBounds.contains(outerBounds)) {
     *                 antifillrect(xr, blitter);
     *             } else {
     *                 SkXRect tmpR;
     *                 // this keeps our original edges fractional
     *                 XRect_set(&tmpR, clipBounds);
     *                 if (tmpR.intersect(xr)) {
     *                     antifillrect(tmpR, blitter);
     *                 }
     *             }
     *         } else {
     *             SkRegion::Cliperator clipper(*clip, outerBounds);
     *             const SkIRect&       rr = clipper.rect();
     *
     *             while (!clipper.done()) {
     *                 SkXRect  tmpR;
     *
     *                 // this keeps our original edges fractional
     *                 XRect_set(&tmpR, rr);
     *                 if (tmpR.intersect(xr)) {
     *                     antifillrect(tmpR, blitter);
     *                 }
     *                 clipper.next();
     *             }
     *         }
     *     }
     * }
     * ```
     */
    public fun antiFillXRect(
      xr: SkXRect,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFillXRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFillPath(const SkPathRaw& path, const SkRegion& origClip,
     *                           SkBlitter* blitter, bool forceRLE) {
     *     if (origClip.isEmpty()) {
     *         return;
     *     }
     *
     *     const bool isInverse = path.isInverseFillType();
     *     SkIRect ir = safeRoundOut(path.bounds());
     *     if (ir.isEmpty()) {
     *         if (isInverse) {
     *             blitter->blitRegion(origClip);
     *         }
     *         return;
     *     }
     *
     *     // If the intersection of the path bounds and the clip bounds
     *     // will overflow 32767 when << by SHIFT, we can't supersample,
     *     // so draw without antialiasing.
     *     SkIRect clippedIR;
     *     if (isInverse) {
     *        // If the path is an inverse fill, it's going to fill the entire
     *        // clip, and we care whether the entire clip exceeds our limits.
     *        clippedIR = origClip.getBounds();
     *     } else {
     *        if (!clippedIR.intersect(ir, origClip.getBounds())) {
     *            return;
     *        }
     *     }
     *     if (rect_overflows_short_shift(clippedIR, SK_SUPERSAMPLE_SHIFT)) {
     *         SkScan::FillPath(path, origClip, blitter);
     *         return;
     *     }
     *
     *     // Our antialiasing can't handle a clip larger than 32767, so we restrict
     *     // the clip to that limit here. (the runs[] uses int16_t for its index).
     *     //
     *     // A more general solution (one that could also eliminate the need to
     *     // disable aa based on ir bounds (see overflows_short_shift) would be
     *     // to tile the clip/target...
     *     SkRegion tmpClipStorage;
     *     const SkRegion* clipRgn = &origClip;
     *     {
     *         static const int32_t kMaxClipCoord = 32767;
     *         const SkIRect& bounds = origClip.getBounds();
     *         if (bounds.fRight > kMaxClipCoord || bounds.fBottom > kMaxClipCoord) {
     *             SkIRect limit = { 0, 0, kMaxClipCoord, kMaxClipCoord };
     *             tmpClipStorage.op(origClip, limit, SkRegion::kIntersect_Op);
     *             clipRgn = &tmpClipStorage;
     *         }
     *     }
     *     // for here down, use clipRgn, not origClip
     *
     *     SkScanClipper   clipper(blitter, clipRgn, ir);
     *
     *     if (clipper.getBlitter() == nullptr) { // clipped out
     *         if (isInverse) {
     *             blitter->blitRegion(*clipRgn);
     *         }
     *         return;
     *     }
     *
     *     SkASSERT(clipper.getClipRect() == nullptr ||
     *             *clipper.getClipRect() == clipRgn->getBounds());
     *
     *     // now use the (possibly wrapped) blitter
     *     blitter = clipper.getBlitter();
     *
     *     if (isInverse) {
     *         sk_blit_above(blitter, ir, *clipRgn);
     *     }
     *
     *     SkScan::AAAFillPath(path, blitter, ir, clipRgn->getBounds(), forceRLE);
     *
     *     if (isInverse) {
     *         sk_blit_below(blitter, ir, *clipRgn);
     *     }
     * }
     * ```
     */
    public fun antiFillPath(
      path: SkPathRaw,
      clip: SkRegion,
      blitter: SkBlitter?,
      forceRLE: Boolean,
    ) {
      TODO("Implement antiFillPath")
    }

    /**
     * C++ original:
     * ```cpp
     * static void FillTriangle(const SkPoint pts[], const SkRegion*, SkBlitter*)
     * ```
     */
    public fun fillTriangle(
      pts: Array<SkPoint>,
      param1: SkRegion?,
      param2: SkBlitter?,
    ) {
      TODO("Implement fillTriangle")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiFrameRect(const SkRect& r, const SkPoint& strokeSize,
     *                            const SkRegion* clip, SkBlitter* blitter) {
     *     SkASSERT(strokeSize.fX >= 0 && strokeSize.fY >= 0);
     *
     *     SkScalar rx = SkScalarHalf(strokeSize.fX);
     *     SkScalar ry = SkScalarHalf(strokeSize.fY);
     *
     *     // If we're empty on either axis, we remove the outset amount, to be sure
     *     // we stroke the same way a polygon would (i.e. it would just see a "line"
     *     // and not extend it for the miter join).
     *     if (r.width() == 0) {
     *         ry = 0;
     *     }
     *     if (r.height() == 0) {
     *         rx = 0;
     *     }
     *
     *     // outset by the radius
     *     FDot8 outerL = SkScalarToFDot8(r.fLeft - rx);
     *     FDot8 outerT = SkScalarToFDot8(r.fTop - ry);
     *     FDot8 outerR = SkScalarToFDot8(r.fRight + rx);
     *     FDot8 outerB = SkScalarToFDot8(r.fBottom + ry);
     *
     *     SkIRect outer;
     *     // set outer to the outer rect of the outer section
     *     outer.setLTRB(FDot8Floor(outerL), FDot8Floor(outerT), FDot8Ceil(outerR), FDot8Ceil(outerB));
     *
     *
     *     SkBlitterClipper clipper;
     *     if (clip) {
     *         if (clip->quickReject(outer)) {
     *             return;
     *         }
     *         if (!clip->contains(outer)) {
     *             blitter = clipper.apply(blitter, clip, &outer);
     *         }
     *         // now we can ignore clip for the rest of the function
     *     }
     *
     *     // in case we lost a bit with diameter/2
     *     rx = strokeSize.fX - rx;
     *     ry = strokeSize.fY - ry;
     *
     *     // inset by the radius
     *     FDot8 innerL = SkScalarToFDot8(r.fLeft + rx);
     *     FDot8 innerT = SkScalarToFDot8(r.fTop + ry);
     *     FDot8 innerR = SkScalarToFDot8(r.fRight - rx);
     *     FDot8 innerB = SkScalarToFDot8(r.fBottom - ry);
     *
     *     // For sub-unit strokes, tweak the hulls such that one of the edges coincides with the pixel
     *     // edge. This ensures that the general rect stroking logic below
     *     //   a) doesn't blit the same scanline twice
     *     //   b) computes the correct coverage when both edges fall within the same pixel
     *     if (strokeSize.fX < 1 || strokeSize.fY < 1) {
     *         align_thin_stroke(outerL, innerL);
     *         align_thin_stroke(outerT, innerT);
     *         align_thin_stroke(innerR, outerR);
     *         align_thin_stroke(innerB, outerB);
     *     }
     *
     *     // stroke the outer hull
     *     antifilldot8(outerL, outerT, outerR, outerB, blitter, false);
     *
     *     // set outer to the outer rect of the middle section
     *     outer.setLTRB(FDot8Ceil(outerL), FDot8Ceil(outerT), FDot8Floor(outerR), FDot8Floor(outerB));
     *
     *     if (innerL >= innerR || innerT >= innerB) {
     *         fillcheckrect(outer.fLeft, outer.fTop, outer.fRight, outer.fBottom,
     *                       blitter);
     *     } else {
     *         SkIRect inner;
     *         // set inner to the inner rect of the middle section
     *         inner.setLTRB(FDot8Floor(innerL), FDot8Floor(innerT), FDot8Ceil(innerR), FDot8Ceil(innerB));
     *
     *         // draw the frame in 4 pieces
     *         fillcheckrect(outer.fLeft, outer.fTop, outer.fRight, inner.fTop,
     *                       blitter);
     *         fillcheckrect(outer.fLeft, inner.fTop, inner.fLeft, inner.fBottom,
     *                       blitter);
     *         fillcheckrect(inner.fRight, inner.fTop, outer.fRight, inner.fBottom,
     *                       blitter);
     *         fillcheckrect(outer.fLeft, inner.fBottom, outer.fRight, outer.fBottom,
     *                       blitter);
     *
     *         // now stroke the inner rect, which is similar to antifilldot8() except that
     *         // it treats the fractional coordinates with the inverse bias (since its
     *         // inner).
     *         innerstrokedot8(innerL, innerT, innerR, innerB, blitter);
     *     }
     * }
     * ```
     */
    public fun antiFrameRect(
      r: SkRect,
      strokeSize: SkPoint,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiFrameRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::HairLineRgn(SkSpan<const SkPoint> src, const SkRegion* clip, SkBlitter* origBlitter) {
     *     if (src.empty()) {
     *         return;
     *     }
     *     SkBlitterClipper    clipper;
     *     SkIRect clipR, ptsR;
     *
     *     const SkScalar max = SkIntToScalar(32767);
     *     const SkRect fixedBounds = SkRect::MakeLTRB(-max, -max, max, max);
     *
     *     SkRect clipBounds;
     *     if (clip) {
     *         clipBounds.set(clip->getBounds());
     *     }
     *
     *     for (size_t i = 0; i < src.size() - 1; ++i) {
     *         SkBlitter* blitter = origBlitter;
     *
     *         SkPoint pts[2];
     *
     *         // We have to pre-clip the line to fit in a SkFixed, so we just chop
     *         // the line. TODO find a way to actually draw beyond that range.
     *         if (!SkLineClipper::IntersectLine(&src[i], fixedBounds, pts)) {
     *             continue;
     *         }
     *
     *         // Perform a clip in scalar space, so we catch huge values which might
     *         // be missed after we convert to SkFDot6 (overflow)
     *         if (clip && !SkLineClipper::IntersectLine(pts, clipBounds, pts)) {
     *             continue;
     *         }
     *
     *         SkFDot6 x0 = SkScalarToFDot6(pts[0].fX);
     *         SkFDot6 y0 = SkScalarToFDot6(pts[0].fY);
     *         SkFDot6 x1 = SkScalarToFDot6(pts[1].fX);
     *         SkFDot6 y1 = SkScalarToFDot6(pts[1].fY);
     *
     *         SkASSERT(canConvertFDot6ToFixed(x0));
     *         SkASSERT(canConvertFDot6ToFixed(y0));
     *         SkASSERT(canConvertFDot6ToFixed(x1));
     *         SkASSERT(canConvertFDot6ToFixed(y1));
     *
     *         if (clip) {
     *             // now perform clipping again, as the rounding to dot6 can wiggle us
     *             // our rects are really dot6 rects, but since we've already used
     *             // lineclipper, we know they will fit in 32bits (26.6)
     *             const SkIRect& bounds = clip->getBounds();
     *
     *             clipR.setLTRB(SkIntToFDot6(bounds.fLeft), SkIntToFDot6(bounds.fTop),
     *                           SkIntToFDot6(bounds.fRight), SkIntToFDot6(bounds.fBottom));
     *             ptsR.setLTRB(x0, y0, x1, y1);
     *             ptsR.sort();
     *
     *             // outset the right and bottom, to account for how hairlines are
     *             // actually drawn, which may hit the pixel to the right or below of
     *             // the coordinate
     *             ptsR.fRight += SK_FDot6One;
     *             ptsR.fBottom += SK_FDot6One;
     *
     *             if (!SkIRect::Intersects(ptsR, clipR)) {
     *                 continue;
     *             }
     *             if (!clip->isRect() || !clipR.contains(ptsR)) {
     *                 blitter = clipper.apply(origBlitter, clip);
     *             }
     *         }
     *
     *         SkFDot6 dx = x1 - x0;
     *         SkFDot6 dy = y1 - y0;
     *
     *         if (SkAbs32(dx) > SkAbs32(dy)) { // mostly horizontal
     *             if (x0 > x1) {   // we want to go left-to-right
     *                 using std::swap;
     *                 swap(x0, x1);
     *                 swap(y0, y1);
     *             }
     *             int ix0 = SkFDot6Round(x0);
     *             int ix1 = SkFDot6Round(x1);
     *             if (ix0 == ix1) {// too short to draw
     *                 continue;
     *             }
     * #if defined(SK_BUILD_FOR_FUZZER)
     *             if ((ix1 - ix0) > 100000 || (ix1 - ix0) < 0) {
     *                 continue; // too big to draw
     *             }
     * #endif
     *             SkFixed slope = SkFixedDiv(dy, dx);
     *             SkFixed startY = SkFDot6ToFixed(y0) + (slope * ((32 - x0) & 63) >> 6);
     *
     *             horiline(ix0, ix1, startY, slope, blitter);
     *         } else {              // mostly vertical
     *             if (y0 > y1) {   // we want to go top-to-bottom
     *                 using std::swap;
     *                 swap(x0, x1);
     *                 swap(y0, y1);
     *             }
     *             int iy0 = SkFDot6Round(y0);
     *             int iy1 = SkFDot6Round(y1);
     *             if (iy0 == iy1) { // too short to draw
     *                 continue;
     *             }
     * #if defined(SK_BUILD_FOR_FUZZER)
     *             if ((iy1 - iy0) > 100000 || (iy1 - iy0) < 0) {
     *                 continue; // too big to draw
     *             }
     * #endif
     *             SkFixed slope = SkFixedDiv(dx, dy);
     *             SkFixed startX = SkFDot6ToFixed(x0) + (slope * ((32 - y0) & 63) >> 6);
     *
     *             vertline(iy0, iy1, startX, slope, blitter);
     *         }
     *     }
     * }
     * ```
     */
    private fun hairLineRgn(
      src: SkSpan<SkPoint>,
      clip: SkRegion?,
      origBlitter: SkBlitter?,
    ) {
      TODO("Implement hairLineRgn")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AntiHairLineRgn(SkSpan<const SkPoint> src, const SkRegion* clip, SkBlitter* blitter) {
     *     if (src.empty() || (clip && clip->isEmpty())) {
     *         return;
     *     }
     *
     *     SkASSERT(clip == nullptr || !clip->getBounds().isEmpty());
     *
     * #ifdef TEST_GAMMA
     *     build_gamma_table();
     * #endif
     *
     *     const SkScalar max = SkIntToScalar(32767);
     *     const SkRect fixedBounds = SkRect::MakeLTRB(-max, -max, max, max);
     *
     *     SkRect clipBounds;
     *     if (clip) {
     *         clipBounds.set(clip->getBounds());
     *         /*  We perform integral clipping later on, but we do a scalar clip first
     *          to ensure that our coordinates are expressible in fixed/integers.
     *
     *          antialiased hairlines can draw up to 1/2 of a pixel outside of
     *          their bounds, so we need to outset the clip before calling the
     *          clipper. To make the numerics safer, we outset by a whole pixel,
     *          since the 1/2 pixel boundary is important to the antihair blitter,
     *          we don't want to risk numerical fate by chopping on that edge.
     *          */
     *         clipBounds.outset(SK_Scalar1, SK_Scalar1);
     *     }
     *
     *     for (size_t i = 0; i < src.size() - 1; ++i) {
     *         SkPoint pts[2];
     *
     *         // We have to pre-clip the line to fit in a SkFixed, so we just chop
     *         // the line. TODO find a way to actually draw beyond that range.
     *         if (!SkLineClipper::IntersectLine(&src[i], fixedBounds, pts)) {
     *             continue;
     *         }
     *
     *         if (clip && !SkLineClipper::IntersectLine(pts, clipBounds, pts)) {
     *             continue;
     *         }
     *
     *         SkFDot6 x0 = SkScalarToFDot6(pts[0].fX);
     *         SkFDot6 y0 = SkScalarToFDot6(pts[0].fY);
     *         SkFDot6 x1 = SkScalarToFDot6(pts[1].fX);
     *         SkFDot6 y1 = SkScalarToFDot6(pts[1].fY);
     *
     *         if (clip) {
     *             SkFDot6 left = std::min(x0, x1);
     *             SkFDot6 top = std::min(y0, y1);
     *             SkFDot6 right = std::max(x0, x1);
     *             SkFDot6 bottom = std::max(y0, y1);
     *             SkIRect ir;
     *
     *             ir.setLTRB(SkFDot6Floor(left) - 1,
     *                        SkFDot6Floor(top) - 1,
     *                        SkFDot6Ceil(right) + 1,
     *                        SkFDot6Ceil(bottom) + 1);
     *
     *             if (clip->quickReject(ir)) {
     *                 continue;
     *             }
     *             if (!clip->quickContains(ir)) {
     *                 SkRegion::Cliperator iter(*clip, ir);
     *                 const SkIRect*       r = &iter.rect();
     *
     *                 while (!iter.done()) {
     *                     do_anti_hairline(x0, y0, x1, y1, r, blitter);
     *                     iter.next();
     *                 }
     *                 continue;
     *             }
     *             // fall through to no-clip case
     *         }
     *         do_anti_hairline(x0, y0, x1, y1, nullptr, blitter);
     *     }
     * }
     * ```
     */
    private fun antiHairLineRgn(
      src: SkSpan<SkPoint>,
      clip: SkRegion?,
      blitter: SkBlitter?,
    ) {
      TODO("Implement antiHairLineRgn")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScan::AAAFillPath(const SkPathRaw&  path,
     *                          SkBlitter*     blitter,
     *                          const SkIRect& ir,
     *                          const SkIRect& clipBounds,
     *                          bool           forceRLE) {
     *     bool containedInClip = clipBounds.contains(ir);
     *     bool isInverse       = path.isInverseFillType();
     *
     *     // The mask blitter (where we store intermediate alpha values directly in a mask, and then call
     *     // the real blitter once in the end to blit the whole mask) is faster than the RLE blitter when
     *     // the blit region is small enough (i.e., CanHandleRect(ir)). When isInverse is true, the blit
     *     // region is no longer the rectangle ir so we won't use the mask blitter. The caller may also
     *     // use the forceRLE flag to force not using the mask blitter. Also, when the path is a simple
     *     // rect, preparing a mask and blitting it might have too much overhead. Hence we'll use
     *     // blitFatAntiRect to avoid the mask and its overhead.
     *     if (MaskAdditiveBlitter::CanHandleRect(ir) && !isInverse && !forceRLE) {
     *         // blitFatAntiRect is slower than the normal AAA flow without MaskAdditiveBlitter.
     *         // Hence only tryBlitFatAntiRect when MaskAdditiveBlitter would have been used.
     *         if (!try_blit_fat_anti_rect(blitter, path, clipBounds)) {
     *             MaskAdditiveBlitter additiveBlitter(blitter, ir, clipBounds, isInverse);
     *             aaa_fill_path(path,
     *                           clipBounds,
     *                           &additiveBlitter,
     *                           ir.fTop,
     *                           ir.fBottom,
     *                           containedInClip,
     *                           true,
     *                           forceRLE);
     *         }
     *     } else if (!isInverse && path.isKnownToBeConvex()) {
     *         // If the filling area is convex (i.e., path.isConvex && !isInverse), our simpler
     *         // aaa_walk_convex_edges won't generate alphas above 255. Hence we don't need
     *         // SafeRLEAdditiveBlitter (which is slow due to clamping). The basic RLE blitter
     *         // RunBasedAdditiveBlitter would suffice.
     *         RunBasedAdditiveBlitter additiveBlitter(blitter, ir, clipBounds, isInverse);
     *         aaa_fill_path(path,
     *                       clipBounds,
     *                       &additiveBlitter,
     *                       ir.fTop,
     *                       ir.fBottom,
     *                       containedInClip,
     *                       false,
     *                       forceRLE);
     *     } else {
     *         // If the filling area might not be convex, the more involved aaa_walk_edges would
     *         // be called and we have to clamp the alpha downto 255. The SafeRLEAdditiveBlitter
     *         // does that at a cost of performance.
     *         SafeRLEAdditiveBlitter additiveBlitter(blitter, ir, clipBounds, isInverse);
     *         aaa_fill_path(path,
     *                       clipBounds,
     *                       &additiveBlitter,
     *                       ir.fTop,
     *                       ir.fBottom,
     *                       containedInClip,
     *                       false,
     *                       forceRLE);
     *     }
     * }
     * ```
     */
    private fun aAAFillPath(
      path: SkPathRaw,
      blitter: SkBlitter?,
      pathIR: SkIRect,
      clipBounds: SkIRect,
      forceRLE: Boolean,
    ) {
      TODO("Implement aAAFillPath")
    }
  }
}
