package org.skia.tests

import kotlin.Int
import org.skia.core.SkSBlockAllocator

/**
 * C++ original:
 * ```cpp
 * class BlockAllocatorTestAccess {
 * public:
 *     template<size_t N>
 *     static size_t ScratchBlockSize(SkSBlockAllocator<N>& pool) {
 *         return (size_t) pool->scratchBlockSize();
 *     }
 * }
 * ```
 */
public open class BlockAllocatorTestAccess {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template<size_t N>
     *     static size_t ScratchBlockSize(SkSBlockAllocator<N>& pool) {
     *         return (size_t) pool->scratchBlockSize();
     *     }
     * ```
     */
    public fun <N> scratchBlockSize(pool: SkSBlockAllocator<N>): Int {
      TODO("Implement scratchBlockSize")
    }
  }
}
