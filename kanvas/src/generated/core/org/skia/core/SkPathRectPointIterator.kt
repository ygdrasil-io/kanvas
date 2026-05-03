package org.skia.core

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkPathDirection
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkPath_RectPointIterator : public SkPath_PointIterator<4> {
 * public:
 *     SkPath_RectPointIterator(const SkRect& rect, SkPathDirection dir, unsigned startIndex)
 *         : SkPath_PointIterator(dir, startIndex) {
 *
 *         fPts[0] = SkPoint::Make(rect.fLeft, rect.fTop);
 *         fPts[1] = SkPoint::Make(rect.fRight, rect.fTop);
 *         fPts[2] = SkPoint::Make(rect.fRight, rect.fBottom);
 *         fPts[3] = SkPoint::Make(rect.fLeft, rect.fBottom);
 *     }
 * }
 * ```
 */
public open class SkPathRectPointIterator public constructor(
  rect: SkRect,
  dir: SkPathDirection,
  startIndex: UInt,
) : SkPathPointIterator(TODO(), TODO()),
    Int
