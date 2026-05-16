package org.skia.core

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkSp
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkStrikeCache final : public sktext::StrikeForGPUCacheInterface {
 * public:
 *     SkStrikeCache() = default;
 *
 *     static SkStrikeCache* GlobalStrikeCache();
 *
 *     sk_sp<SkStrike> findStrike(const SkDescriptor& desc) SK_EXCLUDES(fLock);
 *
 *     sk_sp<SkStrike> createStrike(
 *             const SkStrikeSpec& strikeSpec,
 *             SkFontMetrics* maybeMetrics = nullptr,
 *             std::unique_ptr<SkStrikePinner> = nullptr) SK_EXCLUDES(fLock);
 *
 *     sk_sp<SkStrike> findOrCreateStrike(const SkStrikeSpec& strikeSpec) SK_EXCLUDES(fLock);
 *
 *     sk_sp<sktext::StrikeForGPU> findOrCreateScopedStrike(
 *             const SkStrikeSpec& strikeSpec) override SK_EXCLUDES(fLock);
 *
 *     static void PurgeAll();
 *     static void Dump();
 *
 *     // Dump memory usage statistics of all the attaches caches in the process using the
 *     // SkTraceMemoryDump interface.
 *     static void DumpMemoryStatistics(SkTraceMemoryDump* dump);
 *
 *     void purgeAll() SK_EXCLUDES(fLock); // does not change budget
 *     void purgePinned(size_t minBytesNeeded = 0) SK_EXCLUDES(fLock);
 *
 *     int getCacheCountLimit() const SK_EXCLUDES(fLock);
 *     int setCacheCountLimit(int limit) SK_EXCLUDES(fLock);
 *     int getCacheCountUsed() const SK_EXCLUDES(fLock);
 *
 *     size_t getCacheSizeLimit() const SK_EXCLUDES(fLock);
 *     size_t setCacheSizeLimit(size_t limit) SK_EXCLUDES(fLock);
 *     size_t getTotalMemoryUsed() const SK_EXCLUDES(fLock);
 *
 * private:
 *     friend class SkStrike;  // for SkStrike::updateDelta
 *     static constexpr char kGlyphCacheDumpName[] = "skia/sk_glyph_cache";
 *     sk_sp<SkStrike> internalFindStrikeOrNull(const SkDescriptor& desc) SK_REQUIRES(fLock);
 *     sk_sp<SkStrike> internalCreateStrike(
 *             const SkStrikeSpec& strikeSpec,
 *             SkFontMetrics* maybeMetrics = nullptr,
 *             std::unique_ptr<SkStrikePinner> = nullptr) SK_REQUIRES(fLock);
 *
 *     // The following methods can only be called when mutex is already held.
 *     void internalRemoveStrike(SkStrike* strike) SK_REQUIRES(fLock);
 *     void internalAttachToHead(sk_sp<SkStrike> strike) SK_REQUIRES(fLock);
 *
 *     // Checkout budgets, modulated by the specified min-bytes-needed-to-purge,
 *     // and attempt to purge caches to match.
 *     // Returns number of bytes freed.
 *     size_t internalPurge(size_t minBytesNeeded = 0, bool checkPinners = false) SK_REQUIRES(fLock);
 *
 *     // A simple accounting of what each glyph cache reports and the strike cache total.
 *     void validate() const SK_REQUIRES(fLock);
 *
 *     void forEachStrike(std::function<void(const SkStrike&)> visitor) const SK_EXCLUDES(fLock);
 *
 *     mutable SkMutex fLock;
 *     SkStrike* fHead SK_GUARDED_BY(fLock) {nullptr};
 *     SkStrike* fTail SK_GUARDED_BY(fLock) {nullptr};
 *     struct StrikeTraits {
 *         static const SkDescriptor& GetKey(const sk_sp<SkStrike>& strike);
 *         static uint32_t Hash(const SkDescriptor& descriptor);
 *     };
 *     skia_private::THashTable<sk_sp<SkStrike>, SkDescriptor, StrikeTraits> fStrikeLookup
 *             SK_GUARDED_BY(fLock);
 *
 *     size_t  fCacheSizeLimit{SK_DEFAULT_FONT_CACHE_LIMIT};
 *     size_t  fTotalMemoryUsed SK_GUARDED_BY(fLock) {0};
 *     int32_t fCacheCountLimit{SK_DEFAULT_FONT_CACHE_COUNT_LIMIT};
 *     int32_t fCacheCount SK_GUARDED_BY(fLock) {0};
 *     int32_t fPinnerCount SK_GUARDED_BY(fLock) {0};
 * }
 * ```
 */
public abstract class SkStrikeCache public constructor() : StrikeForGPUCacheInterface() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr char kGlyphCacheDumpName[] = "skia/sk_glyph_cache"
   * ```
   */
  private var fLock: SkMutex = TODO("Initialize fLock")

  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex fLock
   * ```
   */
  private var fHead: SkStrike? = TODO("Initialize fHead")

  /**
   * C++ original:
   * ```cpp
   * SkStrike* fHead SK_GUARDED_BY(fLock) {nullptr}
   * ```
   */
  private var fTail: SkStrike? = TODO("Initialize fTail")

  /**
   * C++ original:
   * ```cpp
   * SkStrike* fTail SK_GUARDED_BY(fLock) {nullptr}
   * ```
   */
  private var fStrikeLookup: THashTable<SkSp<SkStrike>, SkDescriptor, StrikeTraits> =
      TODO("Initialize fStrikeLookup")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashTable<sk_sp<SkStrike>, SkDescriptor, StrikeTraits> fStrikeLookup
   * ```
   */
  private var fCacheSizeLimit: ULong = TODO("Initialize fCacheSizeLimit")

  /**
   * C++ original:
   * ```cpp
   * size_t  fCacheSizeLimit{SK_DEFAULT_FONT_CACHE_LIMIT}
   * ```
   */
  private var fTotalMemoryUsed: ULong = TODO("Initialize fTotalMemoryUsed")

  /**
   * C++ original:
   * ```cpp
   * size_t  fTotalMemoryUsed SK_GUARDED_BY(fLock) {0}
   * ```
   */
  private var fCacheCountLimit: Int = TODO("Initialize fCacheCountLimit")

  /**
   * C++ original:
   * ```cpp
   * int32_t fCacheCountLimit{SK_DEFAULT_FONT_CACHE_COUNT_LIMIT}
   * ```
   */
  private var fCacheCount: Int = TODO("Initialize fCacheCount")

  /**
   * C++ original:
   * ```cpp
   * int32_t fCacheCount SK_GUARDED_BY(fLock) {0}
   * ```
   */
  private var fPinnerCount: Int = TODO("Initialize fPinnerCount")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrike> SkStrikeCache::findStrike(const SkDescriptor& desc) {
   *     SkAutoMutexExclusive ac(fLock);
   *     sk_sp<SkStrike> result = this->internalFindStrikeOrNull(desc);
   *     this->internalPurge();
   *     return result;
   * }
   * ```
   */
  public fun findStrike(desc: SkDescriptor): SkSp<SkStrike> {
    TODO("Implement findStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrike> SkStrikeCache::createStrike(
   *         const SkStrikeSpec& strikeSpec,
   *         SkFontMetrics* maybeMetrics,
   *         std::unique_ptr<SkStrikePinner> pinner) {
   *     SkAutoMutexExclusive ac(fLock);
   *     return this->internalCreateStrike(strikeSpec, maybeMetrics, std::move(pinner));
   * }
   * ```
   */
  public fun createStrike(
    strikeSpec: SkStrikeSpec,
    maybeMetrics: SkFontMetrics? = TODO(),
    pinner: SkStrikePinner? = TODO(),
  ): SkSp<SkStrike> {
    TODO("Implement createStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * auto SkStrikeCache::findOrCreateStrike(const SkStrikeSpec& strikeSpec) -> sk_sp<SkStrike> {
   *     SkAutoMutexExclusive ac(fLock);
   *     sk_sp<SkStrike> strike = this->internalFindStrikeOrNull(strikeSpec.descriptor());
   *     if (strike == nullptr) {
   *         strike = this->internalCreateStrike(strikeSpec);
   *     }
   *     this->internalPurge();
   *     return strike;
   * }
   * ```
   */
  public fun findOrCreateStrike(strikeSpec: SkStrikeSpec): SkSp<SkStrike> {
    TODO("Implement findOrCreateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<StrikeForGPU> SkStrikeCache::findOrCreateScopedStrike(const SkStrikeSpec& strikeSpec) {
   *     return this->findOrCreateStrike(strikeSpec);
   * }
   * ```
   */
  public override fun findOrCreateScopedStrike(strikeSpec: SkStrikeSpec): SkSp<StrikeForGPU> {
    TODO("Implement findOrCreateScopedStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeCache::purgeAll() {
   *     SkAutoMutexExclusive ac(fLock);
   *     this->internalPurge(fTotalMemoryUsed, /* checkPinners= */ true);
   * }
   * ```
   */
  public fun purgeAll() {
    TODO("Implement purgeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeCache::purgePinned(size_t minBytesNeeded) {
   *     SkAutoMutexExclusive ac(fLock);
   *     this->internalPurge(minBytesNeeded, /* checkPinners= */ true);
   * }
   * ```
   */
  public abstract fun purgePinned(minBytesNeeded: ULong = TODO())

  /**
   * C++ original:
   * ```cpp
   * int SkStrikeCache::getCacheCountLimit() const {
   *     SkAutoMutexExclusive ac(fLock);
   *     return fCacheCountLimit;
   * }
   * ```
   */
  public fun getCacheCountLimit(): Int {
    TODO("Implement getCacheCountLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkStrikeCache::setCacheCountLimit(int newCount) {
   *     if (newCount < 0) {
   *         newCount = 0;
   *     }
   *
   *     SkAutoMutexExclusive ac(fLock);
   *
   *     int prevCount = fCacheCountLimit;
   *     fCacheCountLimit = newCount;
   *     this->internalPurge();
   *     return prevCount;
   * }
   * ```
   */
  public fun setCacheCountLimit(limit: Int): Int {
    TODO("Implement setCacheCountLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkStrikeCache::getCacheCountUsed() const {
   *     SkAutoMutexExclusive ac(fLock);
   *     return fCacheCount;
   * }
   * ```
   */
  public fun getCacheCountUsed(): Int {
    TODO("Implement getCacheCountUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t  SkStrikeCache::getCacheSizeLimit() const {
   *     SkAutoMutexExclusive ac(fLock);
   *     return fCacheSizeLimit;
   * }
   * ```
   */
  public fun getCacheSizeLimit(): ULong {
    TODO("Implement getCacheSizeLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkStrikeCache::setCacheSizeLimit(size_t newLimit) {
   *     SkAutoMutexExclusive ac(fLock);
   *
   *     size_t prevLimit = fCacheSizeLimit;
   *     fCacheSizeLimit = newLimit;
   *     this->internalPurge();
   *     return prevLimit;
   * }
   * ```
   */
  public fun setCacheSizeLimit(limit: ULong): ULong {
    TODO("Implement setCacheSizeLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkStrikeCache::getTotalMemoryUsed() const {
   *     SkAutoMutexExclusive ac(fLock);
   *     return fTotalMemoryUsed;
   * }
   * ```
   */
  public fun getTotalMemoryUsed(): ULong {
    TODO("Implement getTotalMemoryUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * auto SkStrikeCache::internalFindStrikeOrNull(const SkDescriptor& desc) -> sk_sp<SkStrike> {
   *
   *     // Check head because it is likely the strike we are looking for.
   *     if (fHead != nullptr && fHead->getDescriptor() == desc) { return sk_ref_sp(fHead); }
   *
   *     // Do the heavy search looking for the strike.
   *     sk_sp<SkStrike>* strikeHandle = fStrikeLookup.find(desc);
   *     if (strikeHandle == nullptr) { return nullptr; }
   *     SkStrike* strikePtr = strikeHandle->get();
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
  private fun internalFindStrikeOrNull(desc: SkDescriptor): SkSp<SkStrike> {
    TODO("Implement internalFindStrikeOrNull")
  }

  /**
   * C++ original:
   * ```cpp
   * auto SkStrikeCache::internalCreateStrike(
   *         const SkStrikeSpec& strikeSpec,
   *         SkFontMetrics* maybeMetrics,
   *         std::unique_ptr<SkStrikePinner> pinner) -> sk_sp<SkStrike> {
   *     std::unique_ptr<SkScalerContext> scaler = strikeSpec.createScalerContext();
   *     auto strike =
   *         sk_make_sp<SkStrike>(this, strikeSpec, std::move(scaler), maybeMetrics, std::move(pinner));
   *     this->internalAttachToHead(strike);
   *     return strike;
   * }
   * ```
   */
  private fun internalCreateStrike(
    strikeSpec: SkStrikeSpec,
    maybeMetrics: SkFontMetrics? = TODO(),
    pinner: SkStrikePinner? = TODO(),
  ): SkSp<SkStrike> {
    TODO("Implement internalCreateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeCache::internalRemoveStrike(SkStrike* strike) {
   *     SkASSERT(fCacheCount > 0);
   *     fCacheCount -= 1;
   *     fPinnerCount -= strike->fPinner != nullptr ? 1 : 0;
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
   *     fStrikeLookup.remove(strike->getDescriptor());
   * }
   * ```
   */
  private fun internalRemoveStrike(strike: SkStrike?) {
    TODO("Implement internalRemoveStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeCache::internalAttachToHead(sk_sp<SkStrike> strike) {
   *     SkASSERT(fStrikeLookup.find(strike->getDescriptor()) == nullptr);
   *     SkStrike* strikePtr = strike.get();
   *     fStrikeLookup.set(std::move(strike));
   *     SkASSERT(nullptr == strikePtr->fPrev && nullptr == strikePtr->fNext);
   *
   *     fCacheCount += 1;
   *     fPinnerCount += strikePtr->fPinner != nullptr ? 1 : 0;
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
  private fun internalAttachToHead(strike: SkSp<SkStrike>) {
    TODO("Implement internalAttachToHead")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkStrikeCache::internalPurge(size_t minBytesNeeded, bool checkPinners) {
   * #ifndef SK_STRIKE_CACHE_DOESNT_AUTO_CHECK_PINNERS
   *     // Temporarily default to checking pinners, for staging.
   *     checkPinners = true;
   * #endif
   *
   *     if (fPinnerCount == fCacheCount && !checkPinners)
   *         return 0;
   *
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
   *     size_t  bytesFreed = 0;
   *     int     countFreed = 0;
   *
   *     // Start at the tail and proceed backwards deleting; the list is in LRU
   *     // order, with unimportant entries at the tail.
   *     SkStrike* strike = fTail;
   *     while (strike != nullptr && (bytesFreed < bytesNeeded || countFreed < countNeeded)) {
   *         SkStrike* prev = strike->fPrev;
   *
   *         // Only delete if the strike is not pinned.
   *         if (strike->fPinner == nullptr || (checkPinners && strike->fPinner->canDelete())) {
   *             bytesFreed += strike->fMemoryUsed;
   *             countFreed += 1;
   *             this->internalRemoveStrike(strike);
   *         }
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
  private abstract fun internalPurge(minBytesNeeded: ULong = TODO(), checkPinners: Boolean = TODO()): ULong

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeCache::validate() const {
   * #ifdef SK_DEBUG
   *     size_t computedBytes = 0;
   *     int computedCount = 0;
   *
   *     const SkStrike* strike = fHead;
   *     while (strike != nullptr) {
   *         computedBytes += strike->fMemoryUsed;
   *         computedCount += 1;
   *         SkASSERT(fStrikeLookup.findOrNull(strike->getDescriptor()) != nullptr);
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

  /**
   * C++ original:
   * ```cpp
   * void forEachStrike(std::function<void(const SkStrike&)> visitor) const SK_EXCLUDES(fLock)
   * ```
   */
  private fun forEachStrike(visitor: (SkStrike) -> Unit) {
    TODO("Implement forEachStrike")
  }

  public open class StrikeTraits {
    public companion object {
      public fun getKey(strike: SkSp<SkStrike>): SkDescriptor {
        TODO("Implement getKey")
      }

      public fun hash(descriptor: SkDescriptor): UInt {
        TODO("Implement hash")
      }
    }
  }

  public companion object {
    private val kGlyphCacheDumpName: CharArray = TODO("Initialize kGlyphCacheDumpName")

    /**
     * C++ original:
     * ```cpp
     * SkStrikeCache* SkStrikeCache::GlobalStrikeCache() {
     *     if (gSkUseThreadLocalStrikeCaches_IAcknowledgeThisIsIncrediblyExperimental) {
     *         static thread_local auto* cache = new SkStrikeCache;
     *         return cache;
     *     }
     *     static auto* cache = new SkStrikeCache;
     *     return cache;
     * }
     * ```
     */
    public fun globalStrikeCache(): SkStrikeCache {
      TODO("Implement globalStrikeCache")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkStrikeCache::PurgeAll() {
     *     GlobalStrikeCache()->purgeAll();
     * }
     * ```
     */
    public fun purgeAll() {
      TODO("Implement purgeAll")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkStrikeCache::Dump() {
     *     SkDebugf("GlyphCache [     used    budget ]\n");
     *     SkDebugf("    bytes  [ %8zu  %8zu ]\n",
     *              SkGraphics::GetFontCacheUsed(), SkGraphics::GetFontCacheLimit());
     *     SkDebugf("    count  [ %8d  %8d ]\n",
     *              SkGraphics::GetFontCacheCountUsed(), SkGraphics::GetFontCacheCountLimit());
     *
     *     auto visitor = [](const SkStrike& strike) {
     *         strike.dump();
     *     };
     *
     *     GlobalStrikeCache()->forEachStrike(visitor);
     * }
     * ```
     */
    public fun dump() {
      TODO("Implement dump")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkStrikeCache::DumpMemoryStatistics(SkTraceMemoryDump* dump) {
     *     dump->dumpNumericValue(kGlyphCacheDumpName, "size", "bytes", SkGraphics::GetFontCacheUsed());
     *     dump->dumpNumericValue(kGlyphCacheDumpName, "budget_size", "bytes",
     *                            SkGraphics::GetFontCacheLimit());
     *     dump->dumpNumericValue(kGlyphCacheDumpName, "glyph_count", "objects",
     *                            SkGraphics::GetFontCacheCountUsed());
     *     dump->dumpNumericValue(kGlyphCacheDumpName, "budget_glyph_count", "objects",
     *                            SkGraphics::GetFontCacheCountLimit());
     *
     *     if (dump->getRequestedDetails() == SkTraceMemoryDump::kLight_LevelOfDetail) {
     *         dump->setMemoryBacking(kGlyphCacheDumpName, "malloc", nullptr);
     *         return;
     *     }
     *
     *     auto visitor = [&](const SkStrike& strike) {
     *         strike.dumpMemoryStatistics(dump);
     *     };
     *
     *     GlobalStrikeCache()->forEachStrike(visitor);
     * }
     * ```
     */
    public fun dumpMemoryStatistics(dump: SkTraceMemoryDump?) {
      TODO("Implement dumpMemoryStatistics")
    }
  }
}
