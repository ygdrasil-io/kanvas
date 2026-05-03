package org.skia.core

import SkDiscardableMemory.Factory
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkDiscardableMemoryPool : public SkDiscardableMemory::Factory {
 * public:
 *     virtual size_t getRAMUsed() = 0;
 *     virtual void setRAMBudget(size_t budget) = 0;
 *     virtual size_t getRAMBudget() = 0;
 *
 *     /** purges all unlocked DMs */
 *     virtual void dumpPool() = 0;
 *
 *     #if SK_LAZY_CACHE_STATS
 *     /**
 *      * These two values are a count of the number of successful and
 *      * failed calls to SkDiscardableMemory::lock() for all DMs managed
 *      * by this pool.
 *      */
 *     virtual int getCacheHits() = 0;
 *     virtual int getCacheMisses() = 0;
 *     virtual void resetCacheHitsAndMisses() = 0;
 *     #endif
 *
 *     /**
 *      *  This non-global pool can be used for unit tests to verify that
 *      *  the pool works.
 *      */
 *     static sk_sp<SkDiscardableMemoryPool> Make(size_t size);
 * }
 * ```
 */
public abstract class SkDiscardableMemoryPool : Factory() {
  /**
   * C++ original:
   * ```cpp
   * virtual size_t getRAMUsed() = 0
   * ```
   */
  public abstract fun getRAMUsed(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void setRAMBudget(size_t budget) = 0
   * ```
   */
  public abstract fun setRAMBudget(budget: ULong)

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getRAMBudget() = 0
   * ```
   */
  public abstract fun getRAMBudget(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void dumpPool() = 0
   * ```
   */
  public abstract fun dumpPool()

  /**
   * C++ original:
   * ```cpp
   * virtual int getCacheHits() = 0
   * ```
   */
  public abstract fun getCacheHits(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int getCacheMisses() = 0
   * ```
   */
  public abstract fun getCacheMisses(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void resetCacheHitsAndMisses() = 0
   * ```
   */
  public abstract fun resetCacheHitsAndMisses()

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkDiscardableMemoryPool> SkDiscardableMemoryPool::Make(size_t size) {
     *     return sk_make_sp<DiscardableMemoryPool>(size);
     * }
     * ```
     */
    public fun make(size: ULong): SkSp<SkDiscardableMemoryPool> {
      TODO("Implement make")
    }
  }
}
