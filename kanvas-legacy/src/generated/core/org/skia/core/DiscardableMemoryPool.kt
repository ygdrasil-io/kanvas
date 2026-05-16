package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import undefined.SkDiscardableMemory
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class DiscardableMemoryPool : public SkDiscardableMemoryPool {
 * public:
 *     DiscardableMemoryPool(size_t budget);
 *     ~DiscardableMemoryPool() override;
 *
 *     std::unique_ptr<SkDiscardableMemory> make(size_t bytes);
 *     SkDiscardableMemory* create(size_t bytes) override {
 *         return this->make(bytes).release();  // TODO: change API
 *     }
 *
 *     size_t getRAMUsed() override;
 *     void setRAMBudget(size_t budget) override;
 *     size_t getRAMBudget() override { return fBudget; }
 *
 *     /** purges all unlocked DMs */
 *     void dumpPool() override;
 *
 *     #if SK_LAZY_CACHE_STATS  // Defined in SkDiscardableMemoryPool.h
 *     int getCacheHits() override { return fCacheHits; }
 *     int getCacheMisses() override { return fCacheMisses; }
 *     void resetCacheHitsAndMisses() override {
 *         fCacheHits = fCacheMisses = 0;
 *     }
 *     int          fCacheHits;
 *     int          fCacheMisses;
 *     #endif  // SK_LAZY_CACHE_STATS
 *
 * private:
 *     SkMutex      fMutex;
 *     size_t       fBudget;
 *     size_t       fUsed;
 *     SkTInternalLList<PoolDiscardableMemory> fList;
 *
 *     /** Function called to free memory if needed */
 *     void dumpDownTo(size_t budget);
 *     /** called by DiscardableMemoryPool upon destruction */
 *     void removeFromPool(PoolDiscardableMemory* dm);
 *     /** called by DiscardableMemoryPool::lock() */
 *     bool lock(PoolDiscardableMemory* dm);
 *     /** called by DiscardableMemoryPool::unlock() */
 *     void unlock(PoolDiscardableMemory* dm);
 *
 *     friend class PoolDiscardableMemory;
 *
 *     using INHERITED = SkDiscardableMemory::Factory;
 * }
 * ```
 */
public open class DiscardableMemoryPool public constructor(
  budget: ULong,
) : SkDiscardableMemoryPool() {
  /**
   * C++ original:
   * ```cpp
   * int          fCacheHits
   * ```
   */
  public var fCacheHits: Int = TODO("Initialize fCacheHits")

  /**
   * C++ original:
   * ```cpp
   * int          fCacheMisses
   * ```
   */
  public var fCacheMisses: Int = TODO("Initialize fCacheMisses")

  /**
   * C++ original:
   * ```cpp
   * SkMutex      fMutex
   * ```
   */
  private var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * size_t       fBudget
   * ```
   */
  private var fBudget: Int = TODO("Initialize fBudget")

  /**
   * C++ original:
   * ```cpp
   * size_t       fUsed
   * ```
   */
  private var fUsed: Int = TODO("Initialize fUsed")

  /**
   * C++ original:
   * ```cpp
   * SkTInternalLList<PoolDiscardableMemory> fList
   * ```
   */
  private var fList: SkTInternalLList<PoolDiscardableMemory> = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkDiscardableMemory> DiscardableMemoryPool::make(size_t bytes) {
   *     UniqueVoidPtr addr(sk_malloc_canfail(bytes));
   *     if (nullptr == addr) {
   *         return nullptr;
   *     }
   *     auto dm = std::make_unique<PoolDiscardableMemory>(sk_ref_sp(this), std::move(addr), bytes);
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     fList.addToHead(dm.get());
   *     fUsed += bytes;
   *     this->dumpDownTo(fBudget);
   *     return dm;
   * }
   * ```
   */
  public fun make(bytes: ULong): Int {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* create(size_t bytes) override {
   *         return this->make(bytes).release();  // TODO: change API
   *     }
   * ```
   */
  public override fun create(bytes: ULong): SkDiscardableMemory {
    TODO("Implement create")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t DiscardableMemoryPool::getRAMUsed() {
   *     return fUsed;
   * }
   * ```
   */
  public override fun getRAMUsed(): Int {
    TODO("Implement getRAMUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void DiscardableMemoryPool::setRAMBudget(size_t budget) {
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     fBudget = budget;
   *     this->dumpDownTo(fBudget);
   * }
   * ```
   */
  public override fun setRAMBudget(budget: ULong) {
    TODO("Implement setRAMBudget")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getRAMBudget() override { return fBudget; }
   * ```
   */
  public override fun getRAMBudget(): Int {
    TODO("Implement getRAMBudget")
  }

  /**
   * C++ original:
   * ```cpp
   * void DiscardableMemoryPool::dumpPool() {
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     this->dumpDownTo(0);
   * }
   * ```
   */
  public override fun dumpPool() {
    TODO("Implement dumpPool")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCacheHits() override { return fCacheHits; }
   * ```
   */
  public override fun getCacheHits(): Int {
    TODO("Implement getCacheHits")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCacheMisses() override { return fCacheMisses; }
   * ```
   */
  public override fun getCacheMisses(): Int {
    TODO("Implement getCacheMisses")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCacheHitsAndMisses() override {
   *         fCacheHits = fCacheMisses = 0;
   *     }
   * ```
   */
  public override fun resetCacheHitsAndMisses() {
    TODO("Implement resetCacheHitsAndMisses")
  }

  /**
   * C++ original:
   * ```cpp
   * void DiscardableMemoryPool::dumpDownTo(size_t budget) {
   *     fMutex.assertHeld();
   *     if (fUsed <= budget) {
   *         return;
   *     }
   *     using Iter = SkTInternalLList<PoolDiscardableMemory>::Iter;
   *     Iter iter;
   *     PoolDiscardableMemory* cur = iter.init(fList, Iter::kTail_IterStart);
   *     while ((fUsed > budget) && (cur)) {
   *         if (!cur->fLocked) {
   *             PoolDiscardableMemory* dm = cur;
   *             SkASSERT(dm->fPointer != nullptr);
   *             dm->fPointer = nullptr;
   *             SkASSERT(fUsed >= dm->fBytes);
   *             fUsed -= dm->fBytes;
   *             cur = iter.prev();
   *             // Purged DMs are taken out of the list.  This saves times
   *             // looking them up.  Purged DMs are NOT deleted.
   *             fList.remove(dm);
   *         } else {
   *             cur = iter.prev();
   *         }
   *     }
   * }
   * ```
   */
  private fun dumpDownTo(budget: ULong) {
    TODO("Implement dumpDownTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void DiscardableMemoryPool::removeFromPool(PoolDiscardableMemory* dm) {
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     // This is called by dm's destructor.
   *     if (dm->fPointer != nullptr) {
   *         SkASSERT(fUsed >= dm->fBytes);
   *         fUsed -= dm->fBytes;
   *         fList.remove(dm);
   *     } else {
   *         SkASSERT(!fList.isInList(dm));
   *     }
   * }
   * ```
   */
  private fun removeFromPool(dm: PoolDiscardableMemory?) {
    TODO("Implement removeFromPool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DiscardableMemoryPool::lock(PoolDiscardableMemory* dm) {
   *     SkASSERT(dm != nullptr);
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     if (nullptr == dm->fPointer) {
   *         // May have been purged while waiting for lock.
   *         #if SK_LAZY_CACHE_STATS
   *         ++fCacheMisses;
   *         #endif  // SK_LAZY_CACHE_STATS
   *         return false;
   *     }
   *     dm->fLocked = true;
   *     fList.remove(dm);
   *     fList.addToHead(dm);
   *     #if SK_LAZY_CACHE_STATS
   *     ++fCacheHits;
   *     #endif  // SK_LAZY_CACHE_STATS
   *     return true;
   * }
   * ```
   */
  private fun lock(dm: PoolDiscardableMemory?): Boolean {
    TODO("Implement lock")
  }

  /**
   * C++ original:
   * ```cpp
   * void DiscardableMemoryPool::unlock(PoolDiscardableMemory* dm) {
   *     SkASSERT(dm != nullptr);
   *     SkAutoMutexExclusive autoMutexAcquire(fMutex);
   *     dm->fLocked = false;
   *     this->dumpDownTo(fBudget);
   * }
   * ```
   */
  private fun unlock(dm: PoolDiscardableMemory?) {
    TODO("Implement unlock")
  }
}
