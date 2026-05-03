package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.ULong
import undefined.SignedT

public typealias TextRange = SkRange<ULong>

public typealias BlockRange = SkRange<ULong>

public typealias ClusterRange = SkRange<ULong>

public typealias GraphemeRange = SkRange<GraphemeIndex>

public typealias GlyphRange = SkRange<GlyphIndex>

/**
 * C++ original:
 * ```cpp
 * struct SkRange {
 *     SkRange() : start(), end() {}
 *     SkRange(T s, T e) : start(s), end(e) {}
 *
 *     using SignedT = std::make_signed_t<T>;
 *
 *     T start, end;
 *
 *     bool operator==(const SkRange<T>& other) const {
 *         return start == other.start && end == other.end;
 *     }
 *
 *     T width() const { return end - start; }
 *
 *     void Shift(SignedT delta) {
 *         start += delta;
 *         end += delta;
 *     }
 *
 *     bool contains(SkRange<size_t> other) const {
 *         return start <= other.start && end >= other.end;
 *     }
 *
 *     bool intersects(SkRange<size_t> other) const {
 *         return std::max(start, other.start) <= std::min(end, other.end);
 *     }
 *
 *     SkRange<size_t> intersection(SkRange<size_t> other) const {
 *         return SkRange<size_t>(std::max(start, other.start), std::min(end, other.end));
 *     }
 *
 *     bool empty() const {
 *         return start == EMPTY_INDEX && end == EMPTY_INDEX;
 *     }
 * }
 * ```
 */
public data class SkRange<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T start
   * ```
   */
  private var start: T,
  /**
   * C++ original:
   * ```cpp
   * T start, end
   * ```
   */
  private var end: T,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkRange<T>& other) const {
   *         return start == other.start && end == other.end;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * T width() const { return end - start; }
   * ```
   */
  private fun width(): T {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * void Shift(SignedT delta) {
   *         start += delta;
   *         end += delta;
   *     }
   * ```
   */
  private fun shift(delta: SignedT) {
    TODO("Implement shift")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(SkRange<size_t> other) const {
   *         return start <= other.start && end >= other.end;
   *     }
   * ```
   */
  private fun contains(other: SkRange<ULong>): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersects(SkRange<size_t> other) const {
   *         return std::max(start, other.start) <= std::min(end, other.end);
   *     }
   * ```
   */
  private fun intersects(other: SkRange<ULong>): Boolean {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRange<size_t> intersection(SkRange<size_t> other) const {
   *         return SkRange<size_t>(std::max(start, other.start), std::min(end, other.end));
   *     }
   * ```
   */
  private fun intersection(other: SkRange<ULong>): SkRange<ULong> {
    TODO("Implement intersection")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const {
   *         return start == EMPTY_INDEX && end == EMPTY_INDEX;
   *     }
   * ```
   */
  private fun empty(): Boolean {
    TODO("Implement empty")
  }
}
