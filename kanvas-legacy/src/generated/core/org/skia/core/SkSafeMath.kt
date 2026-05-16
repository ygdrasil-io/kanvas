package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import undefined.TDst
import undefined.TSrc

/**
 * C++ original:
 * ```cpp
 * class SkSafeMath {
 * public:
 *     SkSafeMath() = default;
 *
 *     bool ok() const { return fOK; }
 *     explicit operator bool() const { return fOK; }
 *
 *     size_t mul(size_t x, size_t y) {
 *         return sizeof(size_t) == sizeof(uint64_t) ? mul64(x, y) : mul32(x, y);
 *     }
 *
 *     size_t add(size_t x, size_t y) {
 *         size_t result = x + y;
 *         fOK &= result >= x;
 *         return result;
 *     }
 *
 *     /**
 *      *  Return a + b, unless this result is an overflow/underflow. In those cases, fOK will
 *      *  be set to false, and it is undefined what this returns.
 *      */
 *     int addInt(int a, int b) {
 *         if (b < 0 && a < std::numeric_limits<int>::min() - b) {
 *             fOK = false;
 *             return a;
 *         } else if (b > 0 && a > std::numeric_limits<int>::max() - b) {
 *             fOK = false;
 *             return a;
 *         }
 *         return a + b;
 *     }
 *
 *     int mulInt(int x, int y) {
 *         int64_t result = (int64_t)x * (int64_t)y;
 *         if (result > std::numeric_limits<int>::max() || result < std::numeric_limits<int>::min()) {
 *             fOK = false;
 *             return x;
 *         }
 *         return (int)result;
 *     }
 *
 *     size_t alignUp(size_t x, size_t alignment) {
 *         SkASSERT(alignment && !(alignment & (alignment - 1)));
 *         return add(x, alignment - 1) & ~(alignment - 1);
 *     }
 *
 *     template <typename TDst, typename TSrc> TDst castTo(TSrc value) {
 *         if (!SkTFitsIn<TDst, TSrc>(value)) {
 *             fOK = false;
 *         }
 *         return static_cast<TDst>(value);
 *     }
 *
 *     // These saturate to their results
 *     static size_t Add(size_t x, size_t y);
 *     static size_t Mul(size_t x, size_t y);
 *     static size_t Align4(size_t x) {
 *         SkSafeMath safe;
 *         return safe.alignUp(x, 4);
 *     }
 *
 * private:
 *     uint32_t mul32(uint32_t x, uint32_t y) {
 *         uint64_t bx = x;
 *         uint64_t by = y;
 *         uint64_t result = bx * by;
 *         fOK &= result >> 32 == 0;
 *         // Overflow information is capture in fOK. Return the result modulo 2^32.
 *         return (uint32_t)result;
 *     }
 *
 *     uint64_t mul64(uint64_t x, uint64_t y) {
 *         if (x <= std::numeric_limits<uint64_t>::max() >> 32
 *             && y <= std::numeric_limits<uint64_t>::max() >> 32) {
 *             return x * y;
 *         } else {
 *             auto hi = [](uint64_t x) { return x >> 32; };
 *             auto lo = [](uint64_t x) { return x & 0xFFFFFFFF; };
 *
 *             uint64_t lx_ly = lo(x) * lo(y);
 *             uint64_t hx_ly = hi(x) * lo(y);
 *             uint64_t lx_hy = lo(x) * hi(y);
 *             uint64_t hx_hy = hi(x) * hi(y);
 *             uint64_t result = 0;
 *             result = this->add(lx_ly, (hx_ly << 32));
 *             result = this->add(result, (lx_hy << 32));
 *             fOK &= (hx_hy + (hx_ly >> 32) + (lx_hy >> 32)) == 0;
 *
 *             #if defined(SK_DEBUG) && defined(__clang__) && defined(__x86_64__)
 *                 auto double_check = (unsigned __int128)x * y;
 *                 SkASSERT(result == (double_check & 0xFFFFFFFFFFFFFFFF));
 *                 SkASSERT(!fOK || (double_check >> 64 == 0));
 *             #endif
 *
 *             return result;
 *         }
 *     }
 *     bool fOK = true;
 * }
 * ```
 */
public data class SkSafeMath public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fOK = true
   * ```
   */
  private var fOK: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool ok() const { return fOK; }
   * ```
   */
  public fun ok(): Boolean {
    TODO("Implement ok")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t mul(size_t x, size_t y) {
   *         return sizeof(size_t) == sizeof(uint64_t) ? mul64(x, y) : mul32(x, y);
   *     }
   * ```
   */
  public fun mul(x: ULong, y: ULong): Int {
    TODO("Implement mul")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t add(size_t x, size_t y) {
   *         size_t result = x + y;
   *         fOK &= result >= x;
   *         return result;
   *     }
   * ```
   */
  public fun add(x: ULong, y: ULong): Int {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * int addInt(int a, int b) {
   *         if (b < 0 && a < std::numeric_limits<int>::min() - b) {
   *             fOK = false;
   *             return a;
   *         } else if (b > 0 && a > std::numeric_limits<int>::max() - b) {
   *             fOK = false;
   *             return a;
   *         }
   *         return a + b;
   *     }
   * ```
   */
  public fun addInt(a: Int, b: Int): Int {
    TODO("Implement addInt")
  }

  /**
   * C++ original:
   * ```cpp
   * int mulInt(int x, int y) {
   *         int64_t result = (int64_t)x * (int64_t)y;
   *         if (result > std::numeric_limits<int>::max() || result < std::numeric_limits<int>::min()) {
   *             fOK = false;
   *             return x;
   *         }
   *         return (int)result;
   *     }
   * ```
   */
  public fun mulInt(x: Int, y: Int): Int {
    TODO("Implement mulInt")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t alignUp(size_t x, size_t alignment) {
   *         SkASSERT(alignment && !(alignment & (alignment - 1)));
   *         return add(x, alignment - 1) & ~(alignment - 1);
   *     }
   * ```
   */
  public fun alignUp(x: ULong, alignment: ULong): Int {
    TODO("Implement alignUp")
  }

  /**
   * C++ original:
   * ```cpp
   * TDst castTo(TSrc value) {
   *         if (!SkTFitsIn<TDst, TSrc>(value)) {
   *             fOK = false;
   *         }
   *         return static_cast<TDst>(value);
   *     }
   * ```
   */
  public fun castTo(`value`: TSrc): TDst {
    TODO("Implement castTo")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t mul32(uint32_t x, uint32_t y) {
   *         uint64_t bx = x;
   *         uint64_t by = y;
   *         uint64_t result = bx * by;
   *         fOK &= result >> 32 == 0;
   *         // Overflow information is capture in fOK. Return the result modulo 2^32.
   *         return (uint32_t)result;
   *     }
   * ```
   */
  private fun mul32(x: UInt, y: UInt): Int {
    TODO("Implement mul32")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t mul64(uint64_t x, uint64_t y) {
   *         if (x <= std::numeric_limits<uint64_t>::max() >> 32
   *             && y <= std::numeric_limits<uint64_t>::max() >> 32) {
   *             return x * y;
   *         } else {
   *             auto hi = [](uint64_t x) { return x >> 32; };
   *             auto lo = [](uint64_t x) { return x & 0xFFFFFFFF; };
   *
   *             uint64_t lx_ly = lo(x) * lo(y);
   *             uint64_t hx_ly = hi(x) * lo(y);
   *             uint64_t lx_hy = lo(x) * hi(y);
   *             uint64_t hx_hy = hi(x) * hi(y);
   *             uint64_t result = 0;
   *             result = this->add(lx_ly, (hx_ly << 32));
   *             result = this->add(result, (lx_hy << 32));
   *             fOK &= (hx_hy + (hx_ly >> 32) + (lx_hy >> 32)) == 0;
   *
   *             #if defined(SK_DEBUG) && defined(__clang__) && defined(__x86_64__)
   *                 auto double_check = (unsigned __int128)x * y;
   *                 SkASSERT(result == (double_check & 0xFFFFFFFFFFFFFFFF));
   *                 SkASSERT(!fOK || (double_check >> 64 == 0));
   *             #endif
   *
   *             return result;
   *         }
   *     }
   * ```
   */
  private fun mul64(x: ULong, y: ULong): Int {
    TODO("Implement mul64")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * size_t SkSafeMath::Add(size_t x, size_t y) {
     *     SkSafeMath tmp;
     *     size_t sum = tmp.add(x, y);
     *     return tmp.ok() ? sum : SIZE_MAX;
     * }
     * ```
     */
    public fun add(x: ULong, y: ULong): Int {
      TODO("Implement add")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkSafeMath::Mul(size_t x, size_t y) {
     *     SkSafeMath tmp;
     *     size_t prod = tmp.mul(x, y);
     *     return tmp.ok() ? prod : SIZE_MAX;
     * }
     * ```
     */
    public fun mul(x: ULong, y: ULong): Int {
      TODO("Implement mul")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t Align4(size_t x) {
     *         SkSafeMath safe;
     *         return safe.alignUp(x, 4);
     *     }
     * ```
     */
    public fun align4(x: ULong): Int {
      TODO("Implement align4")
    }
  }
}
