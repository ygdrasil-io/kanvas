package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.ULong
import kotlin.collections.List
import org.skia.foundation.SkPath
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Contours {
 *     class Iterator {
 *     public:
 *         using value_type = Contour;
 *         using difference_type = ptrdiff_t;
 *         using pointer = value_type*;
 *         using reference = value_type;
 *         using iterator_category = std::input_iterator_tag;
 *         Iterator(const Contours& contours, size_t index)
 *                 : fContours{contours}
 *                 , fIndex{index} { }
 *         Iterator(const Iterator& that) : Iterator{ that.fContours, that.fIndex } { }
 *         Iterator& operator++() { ++fIndex; return *this; }
 *         Iterator operator++(int) { Iterator tmp(*this); operator++(); return tmp; }
 *         bool operator==(const Iterator& rhs) const { return fIndex == rhs.fIndex; }
 *         bool operator!=(const Iterator& rhs) const { return fIndex != rhs.fIndex; }
 *         value_type operator*() { return fContours[fIndex]; }
 *         friend difference_type operator-(Iterator lhs, Iterator rhs) {
 *             return lhs.fIndex - rhs.fIndex;
 *         }
 *
 *     private:
 *         const Contours& fContours;
 *         size_t fIndex = 0;
 *     };
 * public:
 *     static constexpr double kScaleFactor = 1024;
 *     static Contours Make(SkPath path);
 *
 *     Contour operator[](size_t i) const {
 *         SkASSERT(i < fContours.size());
 *         auto& [bounds, end] = fContours[i];
 *         int32_t start = i == 0 ? 0 : fContours[i-1].end;
 *         SkSpan<const Point> points{&fPoints[start], SkToSizeT(end - start)};
 *         return {points, bounds};
 *     }
 *
 *     Iterator begin() const {
 *         return Iterator{*this, 0};
 *     }
 *
 *     Iterator end() const {
 *         return Iterator{*this, fContours.size()};
 *     }
 *
 *     size_t size() const {
 *         return fContours.size();
 *     }
 *
 *     bool empty() const {
 *         return fContours.empty();
 *     }
 *
 *     std::vector<myers::Segment> segments() const;
 *
 * private:
 *     static constexpr SkIRect kEmptyRect = SkIRect::MakeLTRB(INT_MAX, INT_MAX, INT_MIN, INT_MIN);
 *     struct CompactContour {
 *         SkIRect bounds;
 *         int32_t end;
 *     };
 *
 *     static Point RoundSkPoint(SkPoint p);
 *     bool currentContourIsEmpty() const;
 *     void addPointToCurrentContour(SkPoint p);
 *     void moveToStartOfContour(SkPoint p);
 *     void closeContourIfNeeded();
 *
 *     Point fContourStart;
 *     SkIRect fContourBounds = kEmptyRect;
 *
 *     std::vector<Point> fPoints;
 *     std::vector<CompactContour> fContours;
 * }
 * ```
 */
public data class Contours public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr double kScaleFactor = 1024
   * ```
   */
  private var fContourStart: Point,
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkIRect kEmptyRect
   * ```
   */
  private var fContourBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * Point fContourStart
   * ```
   */
  private var fPoints: List<Point>,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fContourBounds
   * ```
   */
  private var fContours: List<CompactContour>,
) {
  /**
   * C++ original:
   * ```cpp
   * Contour operator[](size_t i) const {
   *         SkASSERT(i < fContours.size());
   *         auto& [bounds, end] = fContours[i];
   *         int32_t start = i == 0 ? 0 : fContours[i-1].end;
   *         SkSpan<const Point> points{&fPoints[start], SkToSizeT(end - start)};
   *         return {points, bounds};
   *     }
   * ```
   */
  public operator fun `get`(i: ULong): Contour {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * Iterator begin() const {
   *         return Iterator{*this, 0};
   *     }
   * ```
   */
  public fun begin(): Iterator {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * Iterator end() const {
   *         return Iterator{*this, fContours.size()};
   *     }
   * ```
   */
  public fun end(): Iterator {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const {
   *         return fContours.size();
   *     }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const {
   *         return fContours.empty();
   *     }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<myers::Segment> Contours::segments() const {
   *     SK_ABORT("Not implemented");
   * }
   * ```
   */
  public fun segments(): List<Segment> {
    TODO("Implement segments")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Contours::currentContourIsEmpty() const {
   *     int32_t lastEnd = fContours.empty() ? 0 : fContours.back().end;
   *     return lastEnd == SkToS32(fPoints.size());
   * }
   * ```
   */
  private fun currentContourIsEmpty(): Boolean {
    TODO("Implement currentContourIsEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void Contours::addPointToCurrentContour(SkPoint p) {
   *     if (this->currentContourIsEmpty()) {
   *         fPoints.push_back(fContourStart);
   *         fContourBounds = extend_rect(fContourBounds, fContourStart);
   *     }
   *     Point point = RoundSkPoint(p);
   *     fPoints.push_back(point);
   *     fContourBounds = extend_rect(fContourBounds, point);
   * }
   * ```
   */
  private fun addPointToCurrentContour(p: SkPoint) {
    TODO("Implement addPointToCurrentContour")
  }

  /**
   * C++ original:
   * ```cpp
   * void Contours::moveToStartOfContour(SkPoint p) {
   *     fContourStart = RoundSkPoint(p);
   * }
   * ```
   */
  private fun moveToStartOfContour(p: SkPoint) {
    TODO("Implement moveToStartOfContour")
  }

  /**
   * C++ original:
   * ```cpp
   * void Contours::closeContourIfNeeded() {
   *     if (this->currentContourIsEmpty()) {
   *         // The current contour is empty. Don't record it.
   *         return;
   *     }
   *     fContours.push_back({fContourBounds, SkToS32(fPoints.size())});
   *     fContourBounds = kEmptyRect;
   * }
   * ```
   */
  private fun closeContourIfNeeded() {
    TODO("Implement closeContourIfNeeded")
  }

  public data class Iterator public constructor(
    private val fContours: Contours,
    private var fIndex: ULong,
  ) {
    public operator fun inc(): undefined.Iterator {
      TODO("Implement inc")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public data class CompactContour public constructor(
    public var bounds: Int,
    public var end: Int,
  )

  public companion object {
    public val kScaleFactor: Double = TODO("Initialize kScaleFactor")

    private val kEmptyRect: Int = TODO("Initialize kEmptyRect")

    /**
     * C++ original:
     * ```cpp
     * Contours Contours::Make(SkPath path) {
     *     SkPath::Iter iter(path, false);
     *     Contours contours;
     *     while (auto rec = iter.next()) {
     *         SkSpan<const SkPoint> pts = rec->fPoints;
     *         switch (rec->fVerb) {
     *             case SkPathVerb::kConic: {
     *                 SK_ABORT("Not implemented");
     *                 break;
     *             }
     *             case SkPathVerb::kMove:
     *                 contours.closeContourIfNeeded();
     *                 contours.moveToStartOfContour(pts[0]);
     *                 break;
     *             case SkPathVerb::kLine: {
     *                 contours.addPointToCurrentContour(pts[1]);
     *                 break;
     *             }
     *             case SkPathVerb::kQuad: {
     *                 SK_ABORT("Not implemented");
     *                 break;
     *             }
     *             case SkPathVerb::kCubic: {
     *                 SK_ABORT("Not implemented");
     *                 break;
     *             }
     *             case SkPathVerb::kClose: {
     *                 contours.closeContourIfNeeded();
     *                 break;
     *             }
     *         }
     *     }
     *
     *     // Close the remaining contour.
     *     contours.closeContourIfNeeded();
     *
     *     return contours;
     * }
     * ```
     */
    public fun make(path: SkPath): Contours {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * Point Contours::RoundSkPoint(SkPoint p) {
     *     return {SkScalarRoundToInt(p.x() * kScaleFactor), SkScalarRoundToInt(p.y() * kScaleFactor)};
     * }
     * ```
     */
    private fun roundSkPoint(p: SkPoint): Point {
      TODO("Implement roundSkPoint")
    }
  }
}
