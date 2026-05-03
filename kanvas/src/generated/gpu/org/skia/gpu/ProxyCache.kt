package org.skia.gpu

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import skgpu.StdSteadyClock.time_point
import undefined.CreateEntryFn
import undefined.GPUGeneratorFn

/**
 * C++ original:
 * ```cpp
 * class ProxyCache {
 * public:
 *     ProxyCache(uint32_t recorderID);
 *     ~ProxyCache();
 *
 *     sk_sp<TextureProxy> findOrCreateCachedProxy(Recorder*,
 *                                                 const SkBitmap&,
 *                                                 std::string_view label);
 *
 *     // Find or create a cached TextureProxy that's associated with an externally managed UniqueKey.
 *     // If there is not a cached proxy available, the bitmap generator function will be called with
 *     // the provided context argument. The successfully generated bitmap is then uploaded to a
 *     // a new texture proxy on the Recorder and cached. If the bitmap generation fails, null is
 *     // returned.
 *     //
 *     // The texture proxy's label defaults to the tag of the unique key if not otherwise provided.
 *     using GeneratorContext = const void*;
 *     using BitmapGeneratorFn = SkBitmap (*) (GeneratorContext);
 *     sk_sp<TextureProxy> findOrCreateCachedProxy(Recorder* recorder,
 *                                                 const UniqueKey& key,
 *                                                 GeneratorContext context,
 *                                                 BitmapGeneratorFn fn,
 *                                                 std::string_view label = {});
 *
 *     // As above but returns a GPU image instead of a CPU bitmap for the proxy's content.
 *     using GPUGeneratorFn = sk_sp<Image> (*) (Recorder*, GeneratorContext);
 *     sk_sp<TextureProxy> findOrCreateCachedProxy(Recorder* recorder,
 *                                                 const UniqueKey& key,
 *                                                 GeneratorContext context,
 *                                                 GPUGeneratorFn fn,
 *                                                 std::string_view label = {});
 *
 *     void purgeAll();
 *
 * #if defined(GPU_TEST_UTILS)
 *     int numCached() const;
 *     sk_sp<TextureProxy> find(const SkBitmap&);
 *     void forceProcessInvalidKeyMsgs();
 *     void forceFreeUniquelyHeld();
 *     void forcePurgeProxiesNotUsedSince(skgpu::StdSteadyClock::time_point purgeTime);
 * #endif
 *
 * private:
 *     friend class ResourceCache; // for freeUniquelyHeld
 *
 *     void processInvalidKeyMsgs();
 *     void freeUniquelyHeld();
 *     void purgeProxiesNotUsedSince(const skgpu::StdSteadyClock::time_point* purgeTime);
 *     void removeEntriesAndListeners(SkSpan<const UniqueKey> toRemove);
 *
 *     struct UniqueKeyHash {
 *         uint32_t operator()(const UniqueKey& key) const;
 *     };
 *     struct CacheEntry {
 *         sk_sp<TextureProxy> fProxy;
 *         sk_sp<SkIDChangeListener> fListener; // null if source bitmap won't change
 *     };
 *
 *     template <typename CreateEntryFn> // = CacheEntry (*) (std::string_view)
 *     sk_sp<TextureProxy> findOrCreateCacheEntry(const UniqueKey& key,
 *                                                std::string_view label,
 *                                                CreateEntryFn);
 *
 *     skia_private::THashMap<UniqueKey, CacheEntry, UniqueKeyHash> fCache;
 *     SkMessageBus<UniqueKeyInvalidatedMsg_Graphite, uint32_t>::Inbox fInvalidUniqueKeyInbox;
 * }
 * ```
 */
public data class ProxyCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<UniqueKey, CacheEntry, UniqueKeyHash> fCache
   * ```
   */
  private var fCache: Int,
  /**
   * C++ original:
   * ```cpp
   * SkMessageBus<UniqueKeyInvalidatedMsg_Graphite, uint32_t>::Inbox fInvalidUniqueKeyInbox
   * ```
   */
  private var fInvalidUniqueKeyInbox: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> ProxyCache::findOrCreateCachedProxy(Recorder* recorder,
   *                                                         const SkBitmap& bitmap,
   *                                                         std::string_view label) {
   *
   *     skgpu::UniqueKey key;
   *     make_bitmap_key(&key, bitmap);
   *     return this->findOrCreateCachedProxy(
   *             recorder, key, &bitmap,
   *             [](const void* context) { return *static_cast<const SkBitmap*>(context); },
   *             label);
   * }
   * ```
   */
  public fun findOrCreateCachedProxy(
    recorder: Recorder?,
    bitmap: SkBitmap,
    label: String,
  ): Int {
    TODO("Implement findOrCreateCachedProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> findOrCreateCachedProxy(Recorder* recorder,
   *                                                 const UniqueKey& key,
   *                                                 GeneratorContext context,
   *                                                 BitmapGeneratorFn fn,
   *                                                 std::string_view label = {})
   * ```
   */
  public fun findOrCreateCachedProxy(
    param0: Recorder?,
    param1: UniqueKey,
    param2: ProxyCacheGeneratorContext,
    param3: ProxyCacheBitmapGeneratorFn,
    param4: Int,
  ): Int {
    TODO("Implement findOrCreateCachedProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> findOrCreateCachedProxy(Recorder* recorder,
   *                                                 const UniqueKey& key,
   *                                                 GeneratorContext context,
   *                                                 GPUGeneratorFn fn,
   *                                                 std::string_view label = {})
   * ```
   */
  public fun findOrCreateCachedProxy(
    param0: Recorder?,
    param1: UniqueKey,
    param2: ProxyCacheGeneratorContext,
    param3: Int,
    param4: Int,
  ): Int {
    TODO("Implement findOrCreateCachedProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::purgeAll() {
   *     // removeEntriesAndListeners() without having to copy out all of the keys
   *     fCache.foreach([](const skgpu::UniqueKey&, const CacheEntry* entry) {
   *         if (entry->fListener) {
   *             entry->fListener->markShouldDeregister();
   *         }
   *     });
   *     fCache.reset();
   * }
   * ```
   */
  public fun purgeAll() {
    TODO("Implement purgeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * int ProxyCache::numCached() const {
   *     return fCache.count();
   * }
   * ```
   */
  public fun numCached(): Int {
    TODO("Implement numCached")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> ProxyCache::find(const SkBitmap& bitmap) {
   *
   *     skgpu::UniqueKey key;
   *
   *     make_bitmap_key(&key, bitmap);
   *
   *     if (CacheEntry* cached = fCache.find(key)) {
   *         SkASSERT(cached->fProxy);
   *         return cached->fProxy;
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun find(bitmap: SkBitmap): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::forceProcessInvalidKeyMsgs() {
   *     this->processInvalidKeyMsgs();
   * }
   * ```
   */
  public fun forceProcessInvalidKeyMsgs() {
    TODO("Implement forceProcessInvalidKeyMsgs")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::forceFreeUniquelyHeld() {
   *     this->freeUniquelyHeld();
   * }
   * ```
   */
  public fun forceFreeUniquelyHeld() {
    TODO("Implement forceFreeUniquelyHeld")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::forcePurgeProxiesNotUsedSince(skgpu::StdSteadyClock::time_point purgeTime) {
   *     this->purgeProxiesNotUsedSince(&purgeTime);
   * }
   * ```
   */
  public fun forcePurgeProxiesNotUsedSince(purgeTime: time_point) {
    TODO("Implement forcePurgeProxiesNotUsedSince")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::processInvalidKeyMsgs() {
   *     TArray<skgpu::UniqueKeyInvalidatedMsg_Graphite> invalidKeyMsgs;
   *     fInvalidUniqueKeyInbox.poll(&invalidKeyMsgs);
   *
   *     if (!invalidKeyMsgs.empty()) {
   *         for (int i = 0; i < invalidKeyMsgs.size(); ++i) {
   *             // NOTE(crbug.com/1480570): A change listener is only invoked once, so we shouldn't see
   *             // an invalid message added twice for the same entry. However, we can remove the entry
   *             // due to other reasons while the bitmap listener owner is still alive. While we mark
   *             // the listener to de-register itself, there is still a race where we could decide to
   *             // remove the entry on one thread but haven't de-registered it yet, then another thread
   *             // cleans up the bitmap and posts a message, then the first thread removes the entry the
   *             // posted message also wants to remove.
   *             if (fCache.find(invalidKeyMsgs[i].key())) {
   *                 fCache.remove(invalidKeyMsgs[i].key());
   *             }
   *         }
   *     }
   * }
   * ```
   */
  private fun processInvalidKeyMsgs() {
    TODO("Implement processInvalidKeyMsgs")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::freeUniquelyHeld() {
   *     this->processInvalidKeyMsgs();
   *
   *     skia_private::TArray<skgpu::UniqueKey> toRemove;
   *
   *     fCache.foreach([&](const skgpu::UniqueKey& key, const CacheEntry* entry) {
   *         SkASSERT(entry->fProxy);
   *         if (entry->fProxy->unique()) {
   *             toRemove.push_back(key);
   *         }
   *     });
   *
   *     this->removeEntriesAndListeners(toRemove);
   * }
   * ```
   */
  private fun freeUniquelyHeld() {
    TODO("Implement freeUniquelyHeld")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::purgeProxiesNotUsedSince(const skgpu::StdSteadyClock::time_point* purgeTime) {
   *     this->processInvalidKeyMsgs();
   *
   *     skia_private::TArray<skgpu::UniqueKey> toRemove;
   *
   *     fCache.foreach([&](const skgpu::UniqueKey& key, const CacheEntry* entry) {
   *         SkASSERT(entry->fProxy);
   *         if (Resource* resource = entry->fProxy->texture();
   *             resource &&
   *             (!purgeTime || resource->lastAccessTime() < *purgeTime)) {
   *             toRemove.push_back(key);
   *         }
   *     });
   *
   *     this->removeEntriesAndListeners(toRemove);
   * }
   * ```
   */
  private fun purgeProxiesNotUsedSince(purgeTime: time_point?) {
    TODO("Implement purgeProxiesNotUsedSince")
  }

  /**
   * C++ original:
   * ```cpp
   * void ProxyCache::removeEntriesAndListeners(SkSpan<const UniqueKey> toRemove) {
   *     // This assumes that the entry removal is coming from not polling the invalid key
   *     // messages, so it's necessary to mark the listeners as done. Removing the listeners also means
   *     // we don't leak change listeners if the bitmap is ever re-cached.
   *     for (const UniqueKey& k : toRemove) {
   *         CacheEntry* e = fCache.find(k);
   *         if (e->fListener) {
   *             e->fListener->markShouldDeregister();
   *         }
   *         fCache.remove(k);
   *     }
   * }
   * ```
   */
  private fun removeEntriesAndListeners(toRemove: SkSpan<UniqueKey>) {
    TODO("Implement removeEntriesAndListeners")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename CreateEntryFn>
   * sk_sp<TextureProxy> ProxyCache::findOrCreateCacheEntry(const UniqueKey& key,
   *                                                        std::string_view label,
   *                                                        CreateEntryFn fn) {
   *     this->processInvalidKeyMsgs();
   *
   *     if (CacheEntry* cached = fCache.find(key)) {
   *         SkASSERT(cached->fProxy);
   *         if (Resource* resource = cached->fProxy->texture()) {
   *             resource->updateAccessTime();
   *         }
   *         return cached->fProxy;
   *     }
   *
   *     CacheEntry newEntry = fn(label.empty() ? key.tag() : label);
   *     if (newEntry.fProxy) {
   *         // Success, add it to the cache
   *         fCache.set(key, newEntry);
   *     }
   *     return newEntry.fProxy;
   * }
   * ```
   */
  private fun <CreateEntryFn> findOrCreateCacheEntry(
    key: UniqueKey,
    label: Int,
    fn: CreateEntryFn,
  ): Int {
    TODO("Implement findOrCreateCacheEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> ProxyCache::findOrCreateCachedProxy(Recorder* recorder,
   *                                                         const UniqueKey& key,
   *                                                         GeneratorContext context,
   *                                                         BitmapGeneratorFn generator,
   *                                                         std::string_view label) {
   *     return this->findOrCreateCacheEntry(key, label, [&](std::string_view finalLabel) {
   *         SkBitmap bitmap = generator(context);
   *         if (bitmap.empty()) {
   *             return CacheEntry{};
   *         }
   *
   *         auto [ view, ct ] = MakeBitmapProxyView(recorder, bitmap, nullptr, Mipmapped::kNo,
   *                                                 Budgeted::kYes, finalLabel);
   *         if (!view) {
   *             return CacheEntry{};
   *         }
   *         // Since if the bitmap is held by more than just this function call (e.g. it likely came
   *         // from findOrCreateCachedProxy() that takes an existing SkBitmap), it's worth adding a
   *         // listener to remove them from the cache automatically when no one holds on to it anymore.
   *         // NOTE: We add listeners even if the bitmap is immutable because the listener triggers when
   *         // the bitmap is destroyed. We avoid leaking listeners when the proxy cache is purged due
   *         // to unuse by marking old listeners for cleanup.
   *         sk_sp<SkIDChangeListener> listener = nullptr;
   *         const bool addListener = !bitmap.pixelRef()->unique();
   *         if (addListener) {
   *             listener = make_unique_key_invalidation_listener(key, recorder->priv().uniqueID());
   *             bitmap.pixelRef()->addGenIDChangeListener(listener);
   *         }
   *
   *         return CacheEntry{view.refProxy(), std::move(listener)};
   *     });
   * }
   * ```
   */
  public fun findOrCreateCachedProxy(
    recorder: Recorder?,
    key: UniqueKey,
    context: ProxyCacheGeneratorContext,
    generator: ProxyCacheBitmapGeneratorFn,
    label: String,
  ): SkSp<TextureProxy> {
    TODO("Implement findOrCreateCachedProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> ProxyCache::findOrCreateCachedProxy(Recorder* recorder,
   *                                                         const UniqueKey& key,
   *                                                         GeneratorContext context,
   *                                                         GPUGeneratorFn fn,
   *                                                         std::string_view label) {
   *     return this->findOrCreateCacheEntry(key, label, [&](std::string_view finalLabel) {
   *         sk_sp<Image> textureImage = fn(recorder, context);
   *         if (!textureImage) {
   *             return CacheEntry{};
   *         }
   *
   *         // Force `textureImage`'s TextureProxy to be instantiated so that it's not treated by a
   *         // Recorder as a scratch image that can have a temporary scratch texture assignment.
   *         textureImage->textureProxyView().proxy()->instantiate(recorder->priv().resourceProvider());
   *         // Flush pending work defining the image's content, which also adds these tasks to the root
   *         // task list.
   *         // TODO(b/409888039): These added tasks need to be preserved so that later Recordings that
   *         // get cache hits in the ProxyCache can also initialize the texture if they are added out
   *         // of order relative to this triggering Recording.
   *         textureImage->notifyInUse(recorder, /*drawContext=*/nullptr);
   *
   *         // GPU created proxys never have SkIDChangeListeners.
   *         return CacheEntry{textureImage->textureProxyView().refProxy(),
   *                           /*listener=*/nullptr};
   *     });
   * }
   * ```
   */
  public fun findOrCreateCachedProxy(
    recorder: Recorder?,
    key: UniqueKey,
    context: ProxyCacheGeneratorContext,
    fn: GPUGeneratorFn,
    label: String,
  ): SkSp<TextureProxy> {
    TODO("Implement findOrCreateCachedProxy")
  }

  public open class UniqueKeyHash {
    public operator fun invoke(key: UniqueKey): Int {
      TODO("Implement invoke")
    }
  }

  public data class CacheEntry public constructor(
    public var fProxy: Int,
    public var fListener: Int,
  )
}
