package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkLineClipper {
 * public:
 *     enum {
 *         kMaxPoints = 4,
 *         kMaxClippedLineSegments = kMaxPoints - 1
 *     };
 *
 *     /*  Clip the line pts[0]...pts[1] against clip, ignoring segments that
 *         lie completely above or below the clip. For portions to the left or
 *         right, turn those into vertical line segments that are aligned to the
 *         edge of the clip.
 *
 *         Return the number of line segments that result, and store the end-points
 *         of those segments sequentially in lines as follows:
 *             1st segment: lines[0]..lines[1]
 *             2nd segment: lines[1]..lines[2]
 *             3rd segment: lines[2]..lines[3]
 *      */
 *     static int ClipLine(const SkPoint pts[2], const SkRect& clip,
 *                         SkPoint lines[kMaxPoints], bool canCullToTheRight);
 *
 *     /*  Intersect the line segment against the rect. If there is a non-empty
 *         resulting segment, return true and set dst[] to that segment. If not,
 *         return false and ignore dst[].
 *
 *         ClipLine is specialized for scan-conversion, as it adds vertical
 *         segments on the sides to show where the line extended beyond the
 *         left or right sides. IntersectLine does not.
 *      */
 *     static bool IntersectLine(const SkPoint src[2], const SkRect& clip, SkPoint dst[2]);
 * }
 * ```
 */
public open class SkLineClipper {
  public companion object {
    public val kMaxPoints: Int = TODO("Initialize kMaxPoints")

    public val kMaxClippedLineSegments: Int = TODO("Initialize kMaxClippedLineSegments")

    /**
     * C++ original:
     * ```cpp
     * int SkLineClipper::ClipLine(const SkPoint pts[2], const SkRect& clip, SkPoint lines[kMaxPoints],
     *                             bool canCullToTheRight) {
     *     int index0, index1;
     *
     *     if (pts[0].fY < pts[1].fY) {
     *         index0 = 0;
     *         index1 = 1;
     *     } else {
     *         index0 = 1;
     *         index1 = 0;
     *     }
     *
     *     // Check if we're completely clipped out in Y (above or below
     *
     *     if (pts[index1].fY <= clip.fTop) {  // we're above the clip
     *         return 0;
     *     }
     *     if (pts[index0].fY >= clip.fBottom) {  // we're below the clip
     *         return 0;
     *     }
     *
     *     // Chop in Y to produce a single segment, stored in tmp[0..1]
     *
     *     SkPoint tmp[2];
     *     memcpy(tmp, pts, sizeof(tmp));
     *
     *     // now compute intersections
     *     if (pts[index0].fY < clip.fTop) {
     *         tmp[index0].set(sect_with_horizontal(pts, clip.fTop), clip.fTop);
     *         SkASSERT(is_between_unsorted(tmp[index0].fX, pts[0].fX, pts[1].fX));
     *     }
     *     if (tmp[index1].fY > clip.fBottom) {
     *         tmp[index1].set(sect_with_horizontal(pts, clip.fBottom), clip.fBottom);
     *         SkASSERT(is_between_unsorted(tmp[index1].fX, pts[0].fX, pts[1].fX));
     *     }
     *
     *     // Chop it into 1..3 segments that are wholly within the clip in X.
     *
     *     // temp storage for up to 3 segments
     *     SkPoint resultStorage[kMaxPoints];
     *     SkPoint* result;    // points to our results, either tmp or resultStorage
     *     int lineCount = 1;
     *     bool reverse;
     *
     *     if (pts[0].fX < pts[1].fX) {
     *         index0 = 0;
     *         index1 = 1;
     *         reverse = false;
     *     } else {
     *         index0 = 1;
     *         index1 = 0;
     *         reverse = true;
     *     }
     *
     *     if (tmp[index1].fX <= clip.fLeft) {  // wholly to the left
     *         tmp[0].fX = tmp[1].fX = clip.fLeft;
     *         result = tmp;
     *         reverse = false;
     *     } else if (tmp[index0].fX >= clip.fRight) {    // wholly to the right
     *         if (canCullToTheRight) {
     *             return 0;
     *         }
     *         tmp[0].fX = tmp[1].fX = clip.fRight;
     *         result = tmp;
     *         reverse = false;
     *     } else {
     *         result = resultStorage;
     *         SkPoint* r = result;
     *
     *         if (tmp[index0].fX < clip.fLeft) {
     *             r->set(clip.fLeft, tmp[index0].fY);
     *             r += 1;
     *             r->set(clip.fLeft, sect_clamp_with_vertical(tmp, clip.fLeft));
     *             SkASSERT(is_between_unsorted(r->fY, tmp[0].fY, tmp[1].fY));
     *         } else {
     *             *r = tmp[index0];
     *         }
     *         r += 1;
     *
     *         if (tmp[index1].fX > clip.fRight) {
     *             r->set(clip.fRight, sect_clamp_with_vertical(tmp, clip.fRight));
     *             SkASSERT(is_between_unsorted(r->fY, tmp[0].fY, tmp[1].fY));
     *             r += 1;
     *             r->set(clip.fRight, tmp[index1].fY);
     *         } else {
     *             *r = tmp[index1];
     *         }
     *
     *         lineCount = SkToInt(r - result);
     *     }
     *
     *     // Now copy the results into the caller's lines[] parameter
     *     if (reverse) {
     *         // copy the pts in reverse order to maintain winding order
     *         for (int i = 0; i <= lineCount; i++) {
     *             lines[lineCount - i] = result[i];
     *         }
     *     } else {
     *         memcpy(lines, result, (lineCount + 1) * sizeof(SkPoint));
     *     }
     *     return lineCount;
     * }
     * ```
     */
    public fun clipLine(
      pts: Array<SkPoint>,
      clip: SkRect,
      lines: Array<SkPoint>,
      canCullToTheRight: Boolean,
    ): Int {
      TODO("Implement clipLine")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkLineClipper::IntersectLine(const SkPoint src[2], const SkRect& clip,
     *                                   SkPoint dst[2]) {
     *     SkRect bounds;
     *
     *     bounds.set(src[0], src[1]);
     *     if (containsNoEmptyCheck(clip, bounds)) {
     *         if (src != dst) {
     *             memcpy(dst, src, 2 * sizeof(SkPoint));
     *         }
     *         return true;
     *     }
     *     // check for no overlap, and only permit coincident edges if the line
     *     // and the edge are colinear
     *     if (nestedLT(bounds.fRight, clip.fLeft, bounds.width()) ||
     *         nestedLT(clip.fRight, bounds.fLeft, bounds.width()) ||
     *         nestedLT(bounds.fBottom, clip.fTop, bounds.height()) ||
     *         nestedLT(clip.fBottom, bounds.fTop, bounds.height())) {
     *         return false;
     *     }
     *
     *     int index0, index1;
     *
     *     if (src[0].fY < src[1].fY) {
     *         index0 = 0;
     *         index1 = 1;
     *     } else {
     *         index0 = 1;
     *         index1 = 0;
     *     }
     *
     *     SkPoint tmp[2];
     *     memcpy(tmp, src, sizeof(tmp));
     *
     *     // now compute Y intersections
     *     if (tmp[index0].fY < clip.fTop) {
     *         tmp[index0].set(sect_with_horizontal(src, clip.fTop), clip.fTop);
     *     }
     *     if (tmp[index1].fY > clip.fBottom) {
     *         tmp[index1].set(sect_with_horizontal(src, clip.fBottom), clip.fBottom);
     *     }
     *
     *     if (tmp[0].fX < tmp[1].fX) {
     *         index0 = 0;
     *         index1 = 1;
     *     } else {
     *         index0 = 1;
     *         index1 = 0;
     *     }
     *
     *     // check for quick-reject in X again, now that we may have been chopped
     *     if ((tmp[index1].fX <= clip.fLeft || tmp[index0].fX >= clip.fRight)) {
     *         // usually we will return false, but we don't if the line is vertical and coincident
     *         // with the clip.
     *         if (tmp[0].fX != tmp[1].fX || tmp[0].fX < clip.fLeft || tmp[0].fX > clip.fRight) {
     *             return false;
     *         }
     *     }
     *
     *     if (tmp[index0].fX < clip.fLeft) {
     *         tmp[index0].set(clip.fLeft, sect_with_vertical(tmp, clip.fLeft));
     *     }
     *     if (tmp[index1].fX > clip.fRight) {
     *         tmp[index1].set(clip.fRight, sect_with_vertical(tmp, clip.fRight));
     *     }
     * #ifdef SK_DEBUG
     *     bounds.set(tmp[0], tmp[1]);
     *     SkASSERT(containsNoEmptyCheck(clip, bounds));
     * #endif
     *     memcpy(dst, tmp, sizeof(tmp));
     *     return true;
     * }
     * ```
     */
    public fun intersectLine(
      src: Array<SkPoint>,
      clip: SkRect,
      dst: Array<SkPoint>,
    ): Boolean {
      TODO("Implement intersectLine")
    }
  }
}
