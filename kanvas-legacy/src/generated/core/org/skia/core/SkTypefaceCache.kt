package org.skia.core

import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class SkTypefaceCache {
 * public:
 *     SkTypefaceCache();
 *
 *     /**
 *      * Callback for FindByProc. Returns true if the given typeface is a match
 *      * for the given context. The passed typeface is owned by the cache and is
 *      * not additionally ref()ed. The typeface may be in the disposed state.
 *      */
 *     typedef bool(*FindProc)(SkTypeface*, void* context);
 *
 *     /**
 *      *  Add a typeface to the cache. Later, if we need to purge the cache,
 *      *  typefaces uniquely owned by the cache will be unref()ed.
 *      */
 *     void add(sk_sp<SkTypeface>);
 *
 *     /**
 *      *  Iterate through the cache, calling proc(typeface, ctx) for each typeface.
 *      *  If proc returns true, then return that typeface.
 *      *  If it never returns true, return nullptr.
 *      */
 *     sk_sp<SkTypeface> findByProcAndRef(FindProc proc, void* ctx) const;
 *
 *     /**
 *      *  This will unref all of the typefaces in the cache for which the cache
 *      *  is the only owner. Normally this is handled automatically as needed.
 *      *  This function is exposed for clients that explicitly want to purge the
 *      *  cache (e.g. to look for leaks).
 *      */
 *     void purgeAll();
 *
 *     /**
 *      *  Helper: returns a unique typefaceID to pass to the constructor of
 *      *  your subclass of SkTypeface
 *      */
 *     static SkTypefaceID NewTypefaceID();
 *
 *     // These are static wrappers around a global instance of a cache.
 *
 *     static void Add(sk_sp<SkTypeface>);
 *     static sk_sp<SkTypeface> FindByProcAndRef(FindProc proc, void* ctx);
 *     static void PurgeAll();
 *
 *     /**
 *      *  Debugging only: dumps the status of the typefaces in the cache
 *      */
 *     static void Dump();
 *
 * private:
 *     static SkTypefaceCache& Get();
 *
 *     void purge(int count);
 *
 *     skia_private::TArray<sk_sp<SkTypeface>> fTypefaces;
 * }
 * ```
 */
public data class SkTypefaceCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkTypeface>> fTypefaces
   * ```
   */
  private var fTypefaces: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkTypefaceCache::add(sk_sp<SkTypeface> face) {
   *     SkASSERT_RELEASE(face);
   *     const auto limit = SkGraphics::GetTypefaceCacheCountLimit();
   *
   *     if (fTypefaces.size() >= limit) {
   *         this->purge(limit >> 2);
   *     }
   *     if (limit > 0) {
   *         fTypefaces.emplace_back(std::move(face));
   *     }
   * }
   * ```
   */
  public fun add(face: SkSp<SkTypeface>) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkTypefaceCache::findByProcAndRef(FindProc proc, void* ctx) const {
   *     for (const sk_sp<SkTypeface>& typeface : fTypefaces) {
   *         if (proc(typeface.get(), ctx)) {
   *             return typeface;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun findByProcAndRef(proc: SkTypefaceCacheFindProc, ctx: Unit?): SkSp<SkTypeface> {
    TODO("Implement findByProcAndRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTypefaceCache::purgeAll() {
   *     this->purge(fTypefaces.size());
   * }
   * ```
   */
  public fun purgeAll() {
    TODO("Implement purgeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTypefaceCache::purge(int numToPurge) {
   *     int count = fTypefaces.size();
   *     int i = 0;
   *     while (i < count) {
   *         if (fTypefaces[i]->unique()) {
   *             fTypefaces.removeShuffle(i);
   *             --count;
   *             if (--numToPurge == 0) {
   *                 return;
   *             }
   *         } else {
   *             ++i;
   *         }
   *     }
   * }
   * ```
   */
  private fun purge(count: Int) {
    TODO("Implement purge")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkTypefaceID SkTypefaceCache::NewTypefaceID() {
     *     static std::atomic<int32_t> nextID{1};
     *     return nextID.fetch_add(1, std::memory_order_relaxed);
     * }
     * ```
     */
    public fun newTypefaceID(): Int {
      TODO("Implement newTypefaceID")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkTypefaceCache::Add(sk_sp<SkTypeface> face) {
     *     SkAutoMutexExclusive ama(typeface_cache_mutex());
     *     Get().add(std::move(face));
     * }
     * ```
     */
    public fun add(face: SkSp<SkTypeface>) {
      TODO("Implement add")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTypeface> SkTypefaceCache::FindByProcAndRef(FindProc proc, void* ctx) {
     *     SkAutoMutexExclusive ama(typeface_cache_mutex());
     *     return Get().findByProcAndRef(proc, ctx);
     * }
     * ```
     */
    public fun findByProcAndRef(proc: SkTypefaceCacheFindProc, ctx: Unit?): SkSp<SkTypeface> {
      TODO("Implement findByProcAndRef")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkTypefaceCache::PurgeAll() {
     *     SkAutoMutexExclusive ama(typeface_cache_mutex());
     *     Get().purgeAll();
     * }
     * ```
     */
    public fun purgeAll() {
      TODO("Implement purgeAll")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkTypefaceCache::Dump() {
     * #ifdef SK_DEBUG
     *     (void)Get().findByProcAndRef(DumpProc, nullptr);
     * #endif
     * }
     * ```
     */
    public fun dump() {
      TODO("Implement dump")
    }

    /**
     * C++ original:
     * ```cpp
     * SkTypefaceCache& SkTypefaceCache::Get() {
     *     static SkTypefaceCache gCache;
     *     return gCache;
     * }
     * ```
     */
    private fun `get`(): SkTypefaceCache {
      TODO("Implement get")
    }
  }
}
