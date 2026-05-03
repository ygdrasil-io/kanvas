package org.skia.core

import kotlin.Any
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.UByte
import kotlin.UInt
import kotlin.UShort
import kotlin.Unit

public typealias Mask2 = Vec2<UInt>

/**
 * C++ original:
 * ```cpp
 * template <int N, typename T>
 * struct alignas(N*sizeof(T)) Vec {
 *     static_assert((N & (N-1)) == 0,        "N must be a power of 2.");
 *     static_assert(sizeof(T) >= alignof(T), "What kind of unusual T is this?");
 *
 *     // Methods belong here in the class declaration of Vec only if:
 *     //   - they must be here, like constructors or operator[];
 *     //   - they'll definitely never want a specialized implementation.
 *     // Other operations on Vec should be defined outside the type.
 *
 *     SKVX_ALWAYS_INLINE Vec() = default;
 *     SKVX_ALWAYS_INLINE Vec(T s) : lo(s), hi(s) {}
 *
 *     // NOTE: Vec{x} produces x000..., whereas Vec(x) produces xxxx.... since this constructor fills
 *     // unspecified lanes with 0s, whereas the single T constructor fills all lanes with the value.
 *     SKVX_ALWAYS_INLINE Vec(std::initializer_list<T> xs) {
 *         T vals[N] = {0};
 *         assert(xs.size() <= (size_t)N);
 *         memcpy(vals, xs.begin(), std::min(xs.size(), (size_t)N)*sizeof(T));
 *
 *         this->lo = Vec<N/2,T>::Load(vals +   0);
 *         this->hi = Vec<N/2,T>::Load(vals + N/2);
 *     }
 *
 *     SKVX_ALWAYS_INLINE T  operator[](int i) const { return i<N/2 ? this->lo[i] : this->hi[i-N/2]; }
 *     SKVX_ALWAYS_INLINE T& operator[](int i)       { return i<N/2 ? this->lo[i] : this->hi[i-N/2]; }
 *
 *     SKVX_ALWAYS_INLINE static Vec Load(const void* ptr) {
 *         return sk_unaligned_load<Vec>(ptr);
 *     }
 *     SKVX_ALWAYS_INLINE void store(void* ptr) const {
 *         // Note: Calling sk_unaligned_store produces slightly worse code here, for some reason
 *         memcpy(ptr, this, sizeof(Vec));
 *     }
 *
 *     Vec<N/2,T> lo, hi;
 * }
 * ```
 */
public data class Vec2<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * Vec<N/2,T> lo
   * ```
   */
  private var lo: Any,
  /**
   * C++ original:
   * ```cpp
   * Vec<N/2,T> lo, hi
   * ```
   */
  private var hi: Any,
) {
  /**
   * C++ original:
   * ```cpp
   * SKVX_ALWAYS_INLINE T  operator[](int i) const { return i<N/2 ? this->lo[i] : this->hi[i-N/2]; }
   * ```
   */
  private operator fun `get`(i: Int): T {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SKVX_ALWAYS_INLINE T& operator[](int i)       { return i<N/2 ? this->lo[i] : this->hi[i-N/2]; }
   * ```
   */
  private fun store(ptr: Unit?) {
    TODO("Implement store")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SKVX_ALWAYS_INLINE static Vec Load(const void* ptr) {
     *         return sk_unaligned_load<Vec>(ptr);
     *     }
     * ```
     */
    private fun load(ptr: Unit?): Any {
      TODO("Implement load")
    }
  }
}

public typealias Float2 = Vec2<Float>

public typealias Double2 = Vec2<Double>

public typealias Byte2 = Vec2<UByte>

public typealias Int2 = Vec2<Int>

public typealias Ushort2 = Vec2<UShort>

public typealias Uint2 = Vec2<UInt>

public typealias Long2 = Vec2<Long>

public typealias Half2 = Vec2<UShort>
