package org.skia.core

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkPathDirection
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkPath_OvalPointIterator : public SkPath_PointIterator<4> {
 * public:
 *     SkPath_OvalPointIterator(const SkRect& oval, SkPathDirection dir, unsigned startIndex)
 *         : SkPath_PointIterator(dir, startIndex) {
 *
 *         const SkScalar cx = oval.centerX();
 *         const SkScalar cy = oval.centerY();
 *
 *         fPts[0] = SkPoint::Make(cx, oval.fTop);
 *         fPts[1] = SkPoint::Make(oval.fRight, cy);
 *         fPts[2] = SkPoint::Make(cx, oval.fBottom);
 *         fPts[3] = SkPoint::Make(oval.fLeft, cy);
 *     }
 * }
 * ```
 */
public open class SkPathOvalPointIterator public constructor(
  oval: SkRect,
  dir: SkPathDirection,
  startIndex: UInt,
) : SkPathPointIterator(TODO(), TODO()),
    Int
