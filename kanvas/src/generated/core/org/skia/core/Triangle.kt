package org.skia.core

import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct Triangle : public SkPathRaw {
 *     Triangle(SkSpan<const SkPoint> threePoints, const SkRect& bounds);
 * }
 * ```
 */
public open class Triangle public constructor(
  threePoints: SkSpan<SkPoint>,
  bounds: SkRect,
) : SkPathRaw(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO())
