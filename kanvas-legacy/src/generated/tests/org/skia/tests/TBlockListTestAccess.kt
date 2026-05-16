package org.skia.tests

import kotlin.Int
import org.skia.core.SkTBlockList

/**
 * C++ original:
 * ```cpp
 * class TBlockListTestAccess {
 * public:
 *     template<int N>
 *     static size_t ScratchBlockSize(SkTBlockList<C, N>& list) {
 *         return (size_t) list.fAllocator->scratchBlockSize();
 *     }
 *
 *     template<int N>
 *     static size_t TotalSize(SkTBlockList<C, N>& list) {
 *         return list.fAllocator->totalSize();
 *     }
 *
 *     static constexpr size_t kAddressAlign = SkBlockAllocator::kAddressAlign;
 * }
 * ```
 */
public open class TBlockListTestAccess {
  public companion object {
    public val kAddressAlign: Int = TODO("Initialize kAddressAlign")

    /**
     * C++ original:
     * ```cpp
     *     template<int N>
     *     static size_t ScratchBlockSize(SkTBlockList<C, N>& list) {
     *         return (size_t) list.fAllocator->scratchBlockSize();
     *     }
     * ```
     */
    public fun <N> scratchBlockSize(list: SkTBlockList<C, N>): Int {
      TODO("Implement scratchBlockSize")
    }

    /**
     * C++ original:
     * ```cpp
     *     template<int N>
     *     static size_t TotalSize(SkTBlockList<C, N>& list) {
     *         return list.fAllocator->totalSize();
     *     }
     * ```
     */
    public fun <N> totalSize(list: SkTBlockList<C, N>): Int {
      TODO("Implement totalSize")
    }
  }
}
