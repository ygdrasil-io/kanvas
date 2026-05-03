package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkImageFilterCache : public SkRefCnt {
 * public:
 *     static constexpr size_t kDefaultTransientSize = 32 * 1024 * 1024;
 *
 *     ~SkImageFilterCache() override {}
 *     static sk_sp<SkImageFilterCache> Create(size_t maxBytes);
 *
 *     // Whether to create the cache if it doesn't yet exist.
 *     enum class CreateIfNecessary : bool { kNo, kYes };
 *     static sk_sp<SkImageFilterCache> Get(CreateIfNecessary = CreateIfNecessary::kYes);
 *
 *     // Returns true on cache hit and updates 'result' to be the cached result. Returns false when
 *     // not in the cache, in which case 'result' is not modified.
 *     virtual bool get(const SkImageFilterCacheKey& key,
 *                      skif::FilterResult* result) const = 0;
 *     // 'filter' is included in the caching to allow the purging of all of an image filter's cached
 *     // results when it is destroyed.
 *     virtual void set(const SkImageFilterCacheKey& key, const SkImageFilter* filter,
 *                      const skif::FilterResult& result) = 0;
 *     virtual void purge() = 0;
 *     virtual void purgeByImageFilter(const SkImageFilter*) = 0;
 *     SkDEBUGCODE(virtual int count() const = 0;)
 * }
 * ```
 */
public abstract class SkImageFilterCache : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual bool get(const SkImageFilterCacheKey& key,
   *                      skif::FilterResult* result) const = 0
   * ```
   */
  public abstract fun `get`(key: SkImageFilterCacheKey, result: FilterResult?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void set(const SkImageFilterCacheKey& key, const SkImageFilter* filter,
   *                      const skif::FilterResult& result) = 0
   * ```
   */
  public abstract fun `set`(
    key: SkImageFilterCacheKey,
    filter: SkImageFilter?,
    result: FilterResult,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void purge() = 0
   * ```
   */
  public abstract fun purge()

  /**
   * C++ original:
   * ```cpp
   * virtual void purgeByImageFilter(const SkImageFilter*) = 0
   * ```
   */
  public abstract fun purgeByImageFilter(param0: SkImageFilter?)

  public enum class CreateIfNecessary {
    kNo,
    kYes,
  }

  public companion object {
    public val kDefaultTransientSize: Int = TODO("Initialize kDefaultTransientSize")

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilterCache> SkImageFilterCache::Create(size_t maxBytes) {
     *     return sk_make_sp<CacheImpl>(maxBytes);
     * }
     * ```
     */
    public fun create(maxBytes: ULong): SkSp<SkImageFilterCache> {
      TODO("Implement create")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilterCache> SkImageFilterCache::Get(CreateIfNecessary createIfNecessary) {
     *     static SkOnce once;
     *     static SkImageFilterCache* cache = nullptr;
     *
     *     if (createIfNecessary == CreateIfNecessary::kNo) {
     *         return sk_ref_sp(cache);
     *     }
     *     once([] { cache = SkImageFilterCache::Create(kDefaultCacheSize).release(); });
     *     return sk_ref_sp(cache);
     * }
     * ```
     */
    public fun `get`(createIfNecessary: CreateIfNecessary = TODO()): SkSp<SkImageFilterCache> {
      TODO("Implement get")
    }
  }
}
