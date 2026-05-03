package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkSpan
import org.skia.math.SkPathFillType
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct SkPathRaw {
 *     SkSpan<const SkPoint>    fPoints;
 *     SkSpan<const SkPathVerb> fVerbs;
 *     SkSpan<const float>      fConics;
 *     SkRect                   fBounds;
 *     SkPathFillType           fFillType;
 *     SkPathConvexity          fConvexity;
 *     uint8_t                  fSegmentMask;  // See SkPath::SegmentMask
 *
 *     SkSpan<const SkPoint> points() const { return fPoints; }
 *     SkSpan<const SkPathVerb> verbs() const { return fVerbs; }
 *     SkSpan<const float> conics() const { return fConics; }
 *     SkRect bounds() const { return fBounds; }
 *     SkPathFillType fillType() const { return fFillType; }
 *     SkPathConvexity convexity() const { return fConvexity; }
 *     unsigned segmentMasks() const { return fSegmentMask; }
 *
 *     bool empty() const { return fVerbs.empty(); }
 *     bool isInverseFillType() const { return SkPathFillType_IsInverse(fFillType); }
 *     bool isKnownToBeConvex() const { return SkPathConvexity_IsConvex(fConvexity); }
 *
 *     std::optional<SkRect> isRect() const;
 *
 *     SkPathIter iter() const { return {fPoints, fVerbs, fConics}; }
 *
 *     static SkPathRaw Empty(SkPathFillType ft = SkPathFillType::kDefault) {
 *         return {
 *             {}, {}, {}, SkRect::MakeEmpty(), ft, SkPathConvexity::kConvex_Degenerate, 0,
 *         };
 *     }
 * }
 * ```
 */
public open class SkPathRaw public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint>    fPoints
   * ```
   */
  public val fPoints: SkSpan<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPathVerb> fVerbs
   * ```
   */
  public val fVerbs: SkSpan<SkPathVerb>,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float>      fConics
   * ```
   */
  public val fConics: SkSpan<Float>,
  /**
   * C++ original:
   * ```cpp
   * SkRect                   fBounds
   * ```
   */
  public var fBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * SkPathFillType           fFillType
   * ```
   */
  public var fFillType: SkPathFillType,
  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity          fConvexity
   * ```
   */
  public var fConvexity: SkPathConvexity,
  /**
   * C++ original:
   * ```cpp
   * uint8_t                  fSegmentMask
   * ```
   */
  public var fSegmentMask: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> points() const { return fPoints; }
   * ```
   */
  public fun points(): SkSpan<SkPoint> {
    TODO("Implement points")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPathVerb> verbs() const { return fVerbs; }
   * ```
   */
  public fun verbs(): SkSpan<SkPathVerb> {
    TODO("Implement verbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> conics() const { return fConics; }
   * ```
   */
  public fun conics(): SkSpan<Float> {
    TODO("Implement conics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect bounds() const { return fBounds; }
   * ```
   */
  public fun bounds(): SkRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathFillType fillType() const { return fFillType; }
   * ```
   */
  public fun fillType(): SkPathFillType {
    TODO("Implement fillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity convexity() const { return fConvexity; }
   * ```
   */
  public fun convexity(): SkPathConvexity {
    TODO("Implement convexity")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned segmentMasks() const { return fSegmentMask; }
   * ```
   */
  public fun segmentMasks(): UInt {
    TODO("Implement segmentMasks")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fVerbs.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isInverseFillType() const { return SkPathFillType_IsInverse(fFillType); }
   * ```
   */
  public fun isInverseFillType(): Boolean {
    TODO("Implement isInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isKnownToBeConvex() const { return SkPathConvexity_IsConvex(fConvexity); }
   * ```
   */
  public fun isKnownToBeConvex(): Boolean {
    TODO("Implement isKnownToBeConvex")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRect> SkPathRaw::isRect() const {
   *     if (auto rc = SkPathPriv::IsRectContour(fPoints, fVerbs, fSegmentMask, false)) {
   *         return rc->fRect;
   *     }
   *     return {};
   * }
   * ```
   */
  public fun isRect(): Int {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter iter() const { return {fPoints, fVerbs, fConics}; }
   * ```
   */
  public fun iter(): SkPathIter {
    TODO("Implement iter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkPathRaw Empty(SkPathFillType ft = SkPathFillType::kDefault) {
     *         return {
     *             {}, {}, {}, SkRect::MakeEmpty(), ft, SkPathConvexity::kConvex_Degenerate, 0,
     *         };
     *     }
     * ```
     */
    public fun empty(ft: SkPathFillType = TODO()): SkPathRaw {
      TODO("Implement empty")
    }
  }
}
