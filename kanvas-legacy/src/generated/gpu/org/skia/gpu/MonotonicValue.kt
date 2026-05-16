package org.skia.gpu

import Sequence
import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template<typename Sequence>
 * class MonotonicValue {
 * public:
 *     static constexpr MonotonicValue First() { return 0;      }
 *     static constexpr MonotonicValue Last()  { return 0xffff; }
 *
 *     MonotonicValue() = default;
 *     MonotonicValue(const MonotonicValue& o) = default;
 *
 *     MonotonicValue& operator=(const MonotonicValue& o) = default;
 *
 *     bool operator< (MonotonicValue o) const { return fIndex <  o.fIndex; }
 *     bool operator<=(MonotonicValue o) const { return fIndex <= o.fIndex; }
 *
 *     bool operator> (MonotonicValue o) const { return fIndex >  o.fIndex; }
 *     bool operator>=(MonotonicValue o) const { return fIndex >= o.fIndex; }
 *
 *     bool operator==(MonotonicValue o) const { return fIndex == o.fIndex; }
 *     bool operator!=(MonotonicValue o) const { return fIndex != o.fIndex; }
 *
 *     uint16_t bits() const { return fIndex; }
 *
 *     // Get the next value in the sequence after this one
 *     MonotonicValue next() const { return fIndex + 1; }
 *
 * private:
 *     friend class DrawOrder; // For depth/stencil co-opting
 *
 *     constexpr MonotonicValue(uint16_t index) : fIndex(index) {}
 *
 *     uint16_t fIndex;
 * }
 * ```
 */
public data class MonotonicValue<Sequence> public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint16_t fIndex
   * ```
   */
  private var fIndex: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * MonotonicValue& operator=(const MonotonicValue& o) = default
   * ```
   */
  public fun assign(o: MonotonicValue<Sequence>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator< (MonotonicValue o) const { return fIndex <  o.fIndex; }
   * ```
   */
  public operator fun compareTo(o: MonotonicValue<Sequence>): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator<=(MonotonicValue o) const { return fIndex <= o.fIndex; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator> (MonotonicValue o) const { return fIndex >  o.fIndex; }
   * ```
   */
  public fun bits(): Int {
    TODO("Implement bits")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator>=(MonotonicValue o) const { return fIndex >= o.fIndex; }
   * ```
   */
  public fun next(): MonotonicValue<Sequence> {
    TODO("Implement next")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr MonotonicValue First() { return 0;      }
     * ```
     */
    public fun first(): MonotonicValue<Sequence> {
      TODO("Implement first")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr MonotonicValue Last()  { return 0xffff; }
     * ```
     */
    public fun last(): MonotonicValue<Sequence> {
      TODO("Implement last")
    }
  }
}

public typealias CompressedPaintersOrder = MonotonicValue<CompressedPaintersOrderSequence>

public typealias DisjointStencilIndex = MonotonicValue<DisjointStencilIndexSequence>

public typealias PaintersDepth = MonotonicValue<PaintersDepthSequence>
