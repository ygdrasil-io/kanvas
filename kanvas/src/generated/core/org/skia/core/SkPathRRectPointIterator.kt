package org.skia.core

import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkRRect
import org.skia.math.SkPathDirection

/**
 * C++ original:
 * ```cpp
 * class SkPath_RRectPointIterator : public SkPath_PointIterator<8> {
 * public:
 *     SkPath_RRectPointIterator(const SkRRect& rrect, SkPathDirection dir, unsigned startIndex)
 *         : SkPath_PointIterator(dir, startIndex) {
 *
 *         const SkRect& bounds = rrect.getBounds();
 *         const SkScalar L = bounds.fLeft;
 *         const SkScalar T = bounds.fTop;
 *         const SkScalar R = bounds.fRight;
 *         const SkScalar B = bounds.fBottom;
 *
 *         fPts[0] = SkPoint::Make(L + rrect.radii(SkRRect::kUpperLeft_Corner).fX, T);
 *         fPts[1] = SkPoint::Make(R - rrect.radii(SkRRect::kUpperRight_Corner).fX, T);
 *         fPts[2] = SkPoint::Make(R, T + rrect.radii(SkRRect::kUpperRight_Corner).fY);
 *         fPts[3] = SkPoint::Make(R, B - rrect.radii(SkRRect::kLowerRight_Corner).fY);
 *         fPts[4] = SkPoint::Make(R - rrect.radii(SkRRect::kLowerRight_Corner).fX, B);
 *         fPts[5] = SkPoint::Make(L + rrect.radii(SkRRect::kLowerLeft_Corner).fX, B);
 *         fPts[6] = SkPoint::Make(L, B - rrect.radii(SkRRect::kLowerLeft_Corner).fY);
 *         fPts[7] = SkPoint::Make(L, T + rrect.radii(SkRRect::kUpperLeft_Corner).fY);
 *     }
 * }
 * ```
 */
public open class SkPathRRectPointIterator public constructor(
  rrect: SkRRect,
  dir: SkPathDirection,
  startIndex: UInt,
) : SkPathPointIterator(TODO(), TODO()),
    Int
