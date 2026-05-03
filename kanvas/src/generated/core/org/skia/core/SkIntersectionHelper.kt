package org.skia.core

import kotlin.Boolean
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkIntersectionHelper {
 * public:
 *     enum SegmentType {
 *         kHorizontalLine_Segment = -1,
 *         kVerticalLine_Segment = 0,
 *         kLine_Segment = SkPath::kLine_Verb,
 *         kQuad_Segment = SkPath::kQuad_Verb,
 *         kConic_Segment = SkPath::kConic_Verb,
 *         kCubic_Segment = SkPath::kCubic_Verb,
 *     };
 *
 *     bool advance() {
 *         fSegment = fSegment->next();
 *         return fSegment != nullptr;
 *     }
 *
 *     SkScalar bottom() const {
 *         return bounds().fBottom;
 *     }
 *
 *     const SkPathOpsBounds& bounds() const {
 *         return fSegment->bounds();
 *     }
 *
 *     SkOpContour* contour() const {
 *         return fSegment->contour();
 *     }
 *
 *     void init(SkOpContour* contour) {
 *         fSegment = contour->first();
 *     }
 *
 *     SkScalar left() const {
 *         return bounds().fLeft;
 *     }
 *
 *     const SkPoint* pts() const {
 *         return fSegment->pts();
 *     }
 *
 *     SkScalar right() const {
 *         return bounds().fRight;
 *     }
 *
 *     SkOpSegment* segment() const {
 *         return fSegment;
 *     }
 *
 *     SegmentType segmentType() const {
 *         SegmentType type = (SegmentType) fSegment->verb();
 *         if (type != kLine_Segment) {
 *             return type;
 *         }
 *         if (fSegment->isHorizontal()) {
 *             return kHorizontalLine_Segment;
 *         }
 *         if (fSegment->isVertical()) {
 *             return kVerticalLine_Segment;
 *         }
 *         return kLine_Segment;
 *     }
 *
 *     bool startAfter(const SkIntersectionHelper& after) {
 *         fSegment = after.fSegment->next();
 *         return fSegment != nullptr;
 *     }
 *
 *     SkScalar top() const {
 *         return bounds().fTop;
 *     }
 *
 *     SkScalar weight() const {
 *         return fSegment->weight();
 *     }
 *
 *     SkScalar x() const {
 *         return bounds().fLeft;
 *     }
 *
 *     bool xFlipped() const {
 *         return x() != pts()[0].fX;
 *     }
 *
 *     SkScalar y() const {
 *         return bounds().fTop;
 *     }
 *
 *     bool yFlipped() const {
 *         return y() != pts()[0].fY;
 *     }
 *
 * private:
 *     SkOpSegment* fSegment;
 * }
 * ```
 */
public data class SkIntersectionHelper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* fSegment
   * ```
   */
  private var fSegment: SkOpSegment?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool advance() {
   *         fSegment = fSegment->next();
   *         return fSegment != nullptr;
   *     }
   * ```
   */
  public fun advance(): Boolean {
    TODO("Implement advance")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar bottom() const {
   *         return bounds().fBottom;
   *     }
   * ```
   */
  public fun bottom(): SkScalar {
    TODO("Implement bottom")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPathOpsBounds& bounds() const {
   *         return fSegment->bounds();
   *     }
   * ```
   */
  public fun bounds(): SkPathOpsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* contour() const {
   *         return fSegment->contour();
   *     }
   * ```
   */
  public fun contour(): SkOpContour {
    TODO("Implement contour")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(SkOpContour* contour) {
   *         fSegment = contour->first();
   *     }
   * ```
   */
  public fun `init`(contour: SkOpContour?) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar left() const {
   *         return bounds().fLeft;
   *     }
   * ```
   */
  public fun left(): SkScalar {
    TODO("Implement left")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* pts() const {
   *         return fSegment->pts();
   *     }
   * ```
   */
  public fun pts(): SkPoint {
    TODO("Implement pts")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar right() const {
   *         return bounds().fRight;
   *     }
   * ```
   */
  public fun right(): SkScalar {
    TODO("Implement right")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* segment() const {
   *         return fSegment;
   *     }
   * ```
   */
  public fun segment(): SkOpSegment {
    TODO("Implement segment")
  }

  /**
   * C++ original:
   * ```cpp
   * SegmentType segmentType() const {
   *         SegmentType type = (SegmentType) fSegment->verb();
   *         if (type != kLine_Segment) {
   *             return type;
   *         }
   *         if (fSegment->isHorizontal()) {
   *             return kHorizontalLine_Segment;
   *         }
   *         if (fSegment->isVertical()) {
   *             return kVerticalLine_Segment;
   *         }
   *         return kLine_Segment;
   *     }
   * ```
   */
  public fun segmentType(): SegmentType {
    TODO("Implement segmentType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool startAfter(const SkIntersectionHelper& after) {
   *         fSegment = after.fSegment->next();
   *         return fSegment != nullptr;
   *     }
   * ```
   */
  public fun startAfter(after: SkIntersectionHelper): Boolean {
    TODO("Implement startAfter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar top() const {
   *         return bounds().fTop;
   *     }
   * ```
   */
  public fun top(): SkScalar {
    TODO("Implement top")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar weight() const {
   *         return fSegment->weight();
   *     }
   * ```
   */
  public fun weight(): SkScalar {
    TODO("Implement weight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar x() const {
   *         return bounds().fLeft;
   *     }
   * ```
   */
  public fun x(): SkScalar {
    TODO("Implement x")
  }

  /**
   * C++ original:
   * ```cpp
   * bool xFlipped() const {
   *         return x() != pts()[0].fX;
   *     }
   * ```
   */
  public fun xFlipped(): Boolean {
    TODO("Implement xFlipped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar y() const {
   *         return bounds().fTop;
   *     }
   * ```
   */
  public fun y(): SkScalar {
    TODO("Implement y")
  }

  /**
   * C++ original:
   * ```cpp
   * bool yFlipped() const {
   *         return y() != pts()[0].fY;
   *     }
   * ```
   */
  public fun yFlipped(): Boolean {
    TODO("Implement yFlipped")
  }

  public enum class SegmentType {
    kHorizontalLine_Segment,
    kVerticalLine_Segment,
    kLine_Segment,
    kQuad_Segment,
    kConic_Segment,
    kCubic_Segment,
  }
}
