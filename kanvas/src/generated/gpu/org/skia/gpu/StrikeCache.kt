package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import org.skia.core.SkStrikeSpec
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class StrikeCache {
 * public:
 *     ~StrikeCache();
 *
 *     // The user of the cache may hold a long-lived ref to the returned strike.
 *     sk_sp<TextStrike> findOrCreateStrike(const SkStrikeSpec& strikeSpec);
 *
 *     void freeAll();
 *
 * private:
 *     friend class TextStrike;  // for TextStrike::getGlyph
 *     sk_sp<TextStrike> internalFindStrikeOrNull(const SkDescriptor& desc);
 *     sk_sp<TextStrike> generateStrike(const SkStrikeSpec& strikeSpec);
 *
 *     void internalRemoveStrike(TextStrike* strike);
 *     void internalAttachToHead(sk_sp<TextStrike> strike);
 *
 *     // Checkout budgets, modulated by the specified min-bytes-needed-to-purge,
 *     // and attempt to purge caches to match.
 *     // Returns number of bytes freed.
 *     size_t internalPurge(size_t minBytesNeeded = 0);
 *
 *     // A simple accounting of what each glyph cache reports and the strike cache total.
 *     void validate() const;
 *
 *     TextStrike* fHead{nullptr};
 *     TextStrike* fTail{nullptr};
 *
 *     struct HashTraits {
 *         static const SkDescriptor& GetKey(const sk_sp<TextStrike>& strike);
 *         static uint32_t Hash(const SkDescriptor& strikeSpec);
 *     };
 *     using StrikeHash = skia_private::THashTable<sk_sp<TextStrike>, const SkDescriptor&, HashTraits>;
 *
 *     StrikeHash fCache;
 *
 *     size_t  fCacheSizeLimit{SK_DEFAULT_GPU_FONT_CACHE_LIMIT};
 *     size_t  fTotalMemoryUsed{0};
 *     int32_t fCacheCountLimit{SK_DEFAULT_GPU_FONT_CACHE_COUNT_LIMIT};
 *     int32_t fCacheCount{0};
 * }
 * ```
 */
public abstract class StrikeCache {
  /**
   * C++ original:
   * ```cpp
   * TextStrike* fHead{nullptr}
   * ```
   */
  private var fHead: TextStrike? = TODO("Initialize fHead")

  /**
   * C++ original:
   * ```cpp
   * TextStrike* fTail{nullptr}
   * ```
   */
  private var fTail: TextStrike? = TODO("Initialize fTail")

  /**
   * C++ original:
   * ```cpp
   * StrikeHash fCache
   * ```
   */
  private var fCache: Int = TODO("Initialize fCache")

  /**
   * C++ original:
   * ```cpp
   * size_t  fCacheSizeLimit
   * ```
   */
  private var fCacheSizeLimit: Int = TODO("Initialize fCacheSizeLimit")

  /**
   * C++ original:
   * ```cpp
   * size_t  fTotalMemoryUsed
   * ```
   */
  private var fTotalMemoryUsed: Int = TODO("Initialize fTotalMemoryUsed")

  /**
   * C++ original:
   * ```cpp
   * int32_t fCacheCountLimit
   * ```
   */
  private var fCacheCountLimit: Int = TODO("Initialize fCacheCountLimit")

  /**
   * C++ original:
   * ```cpp
   * int32_t fCacheCount
   * ```
   */
  private var fCacheCount: Int = TODO("Initialize fCacheCount")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextStrike> StrikeCache::findOrCreateStrike(const SkStrikeSpec& strikeSpec) {
   *     if (sk_sp<TextStrike>* cached = fCache.find(strikeSpec.descriptor())) {
   *         return *cached;
   *     }
   *     sk_sp<TextStrike> strike = this->generateStrike(strikeSpec);
   *     this->internalPurge();
   *
   *     return strike;
   * }
   * ```
   */
  public fun findOrCreateStrike(strikeSpec: SkStrikeSpec): Int {
    TODO("Implement findOrCreateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void StrikeCache::freeAll() {
   *     this->internalPurge(fTotalMemoryUsed);
   * }
   * ```
   */
  public fun freeAll() {
    TODO("Implement freeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextStrike> StrikeCache::internalFindStrikeOrNull(const SkDescriptor& desc) {
   *     // Check head because it is likely the strike we are looking for.
   *     if (fHead != nullptr && fHead->getDescriptor() == desc) { return sk_ref_sp(fHead); }
   *
   *     // Do the heavy search looking for the strike.
   *     sk_sp<TextStrike>* strikeHandle = fCache.find(desc);
   *     if (strikeHandle == nullptr) { return nullptr; }
   *     TextStrike* strikePtr = strikeHandle->get();
   *     SkASSERT(strikePtr != nullptr);
   *     if (fHead != strikePtr) {
   *         // Make most recently used
   *         strikePtr->fPrev->fNext = strikePtr->fNext;
   *         if (strikePtr->fNext != nullptr) {
   *             strikePtr->fNext->fPrev = strikePtr->fPrev;
   *         } else {
   *             fTail = strikePtr->fPrev;
   *         }
   *         fHead->fPrev = strikePtr;
   *         strikePtr->fNext = fHead;
   *         strikePtr->fPrev = nullptr;
   *         fHead = strikePtr;
   *     }
   *     return sk_ref_sp(strikePtr);
   * }
   * ```
   */
  private fun internalFindStrikeOrNull(desc: SkDescriptor): Int {
    TODO("Implement internalFindStrikeOrNull")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextStrike> StrikeCache::generateStrike(const SkStrikeSpec& strikeSpec) {
   *     sk_sp<TextStrike> strike = sk_make_sp<TextStrike>(this, strikeSpec);
   *     this->internalAttachToHead(strike);
   *     return strike;
   * }
   * ```
   */
  private fun generateStrike(strikeSpec: SkStrikeSpec): Int {
    TODO("Implement generateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void StrikeCache::internalRemoveStrike(TextStrike* strike) {
   *     SkASSERT(fCacheCount > 0);
   *     fCacheCount -= 1;
   *     fTotalMemoryUsed -= strike->fMemoryUsed;
   *
   *     if (strike->fPrev) {
   *         strike->fPrev->fNext = strike->fNext;
   *     } else {
   *         fHead = strike->fNext;
   *     }
   *     if (strike->fNext) {
   *         strike->fNext->fPrev = strike->fPrev;
   *     } else {
   *         fTail = strike->fPrev;
   *     }
   *
   *     strike->fPrev = strike->fNext = nullptr;
   *     strike->fRemoved = true;
   *     fCache.remove(strike->getDescriptor());
   * }
   * ```
   */
  private fun internalRemoveStrike(strike: TextStrike?) {
    TODO("Implement internalRemoveStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void StrikeCache::internalAttachToHead(sk_sp<TextStrike> strike) {
   *     SkASSERT(fCache.find(strike->getDescriptor()) == nullptr);
   *     TextStrike* strikePtr = strike.get();
   *     fCache.set(std::move(strike));
   *     SkASSERT(nullptr == strikePtr->fPrev && nullptr == strikePtr->fNext);
   *
   *     fCacheCount += 1;
   *     fTotalMemoryUsed += strikePtr->fMemoryUsed;
   *
   *     if (fHead != nullptr) {
   *         fHead->fPrev = strikePtr;
   *         strikePtr->fNext = fHead;
   *     }
   *
   *     if (fTail == nullptr) {
   *         fTail = strikePtr;
   *     }
   *
   *     fHead = strikePtr; // Transfer ownership of strike to the cache list.
   * }
   * ```
   */
  private fun internalAttachToHead(strike: SkSp<TextStrike>) {
    TODO("Implement internalAttachToHead")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t StrikeCache::internalPurge(size_t minBytesNeeded) {
   *     size_t bytesNeeded = 0;
   *     if (fTotalMemoryUsed > fCacheSizeLimit) {
   *         bytesNeeded = fTotalMemoryUsed - fCacheSizeLimit;
   *     }
   *     bytesNeeded = std::max(bytesNeeded, minBytesNeeded);
   *     if (bytesNeeded) {
   *         // no small purges!
   *         bytesNeeded = std::max(bytesNeeded, fTotalMemoryUsed >> 2);
   *     }
   *
   *     int countNeeded = 0;
   *     if (fCacheCount > fCacheCountLimit) {
   *         countNeeded = fCacheCount - fCacheCountLimit;
   *         // no small purges!
   *         countNeeded = std::max(countNeeded, fCacheCount >> 2);
   *     }
   *
   *     // early exit
   *     if (!countNeeded && !bytesNeeded) {
   *         return 0;
   *     }
   *
   *     TRACE_EVENT2_ALWAYS("skia.gpu.cache", "StrikeCache::internalPurge",
   *                         "totalMemoryUsed", fTotalMemoryUsed, "cacheCount", fCacheCount);
   *
   *     size_t  bytesFreed = 0;
   *     int     countFreed = 0;
   *
   *     // Start at the tail and proceed backwards deleting; the list is in LRU
   *     // order, with unimportant entries at the tail.
   *     TextStrike* strike = fTail;
   *     while (strike != nullptr && (bytesFreed < bytesNeeded || countFreed < countNeeded)) {
   *         TextStrike* prev = strike->fPrev;
   *
   *         bytesFreed += strike->fMemoryUsed;
   *         countFreed += 1;
   *         this->internalRemoveStrike(strike);
   *
   *         strike = prev;
   *     }
   *
   *     this->validate();
   *
   * #ifdef SPEW_PURGE_STATUS
   *     if (countFreed) {
   *         SkDebugf("purging %dK from font cache [%d entries]\n",
   *                  (int)(bytesFreed >> 10), countFreed);
   *     }
   * #endif
   *
   *     return bytesFreed;
   * }
   * ```
   */
  private abstract fun internalPurge(minBytesNeeded: ULong = TODO()): Int

  /**
   * C++ original:
   * ```cpp
   * void StrikeCache::validate() const {
   * #ifdef SK_DEBUG
   *     size_t computedBytes = 0;
   *     int computedCount = 0;
   *
   *     const TextStrike* strike = fHead;
   *     while (strike != nullptr) {
   *         computedBytes += strike->fMemoryUsed;
   *         computedCount += 1;
   *         SkASSERT(fCache.findOrNull(strike->getDescriptor()) != nullptr);
   *         strike = strike->fNext;
   *     }
   *
   *     if (fCacheCount != computedCount) {
   *         SkDebugf("fCacheCount: %d, computedCount: %d", fCacheCount, computedCount);
   *         SK_ABORT("fCacheCount != computedCount");
   *     }
   *     if (fTotalMemoryUsed != computedBytes) {
   *         SkDebugf("fTotalMemoryUsed: %zu, computedBytes: %zu", fTotalMemoryUsed, computedBytes);
   *         SK_ABORT("fTotalMemoryUsed == computedBytes");
   *     }
   * #endif
   * }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  public open class HashTraits {
    public companion object {
      public fun getKey(strike: SkSp<TextStrike>): Int {
        TODO("Implement getKey")
      }

      public fun hash(strikeSpec: SkDescriptor): Int {
        TODO("Implement hash")
      }
    }
  }
}
