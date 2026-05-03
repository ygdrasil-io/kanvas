package org.skia.core

import kotlin.Boolean
import kotlin.UInt
import kotlin.ULong
import org.skia.pdf.Key
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class CacheImpl : public SkImageFilterCache {
 * public:
 *     typedef SkImageFilterCacheKey Key;
 *     CacheImpl(size_t maxBytes) : fMaxBytes(maxBytes), fCurrentBytes(0) { }
 *     ~CacheImpl() override {
 *         fLookup.foreach([&](Value* v) { delete v; });
 *     }
 *     struct Value {
 *         Value(const Key& key, const skif::FilterResult& image,
 *               const SkImageFilter* filter)
 *             : fKey(key), fImage(image), fFilter(filter) {}
 *
 *         Key fKey;
 *         skif::FilterResult fImage;
 *         const SkImageFilter* fFilter;
 *         static const Key& GetKey(const Value& v) {
 *             return v.fKey;
 *         }
 *         static uint32_t Hash(const Key& key) {
 *             return SkChecksum::Hash32(&key, sizeof(Key));
 *         }
 *         SK_DECLARE_INTERNAL_LLIST_INTERFACE(Value);
 *     };
 *
 *     bool get(const Key& key, skif::FilterResult* result) const override {
 *         SkASSERT(result);
 *
 *         SkAutoMutexExclusive mutex(fMutex);
 *         if (Value* v = fLookup.find(key)) {
 *             if (v != fLRU.head()) {
 *                 fLRU.remove(v);
 *                 fLRU.addToHead(v);
 *             }
 *
 *             *result = v->fImage;
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     void set(const Key& key, const SkImageFilter* filter,
 *              const skif::FilterResult& result) override {
 *         SkAutoMutexExclusive mutex(fMutex);
 *         if (Value* v = fLookup.find(key)) {
 *             this->removeInternal(v);
 *         }
 *         Value* v = new Value(key, result, filter);
 *         fLookup.add(v);
 *         fLRU.addToHead(v);
 *         fCurrentBytes += result.image() ? result.image()->getSize() : 0;
 *         if (auto* values = fImageFilterValues.find(filter)) {
 *             values->push_back(v);
 *         } else {
 *             fImageFilterValues.set(filter, {v});
 *         }
 *
 *         while (fCurrentBytes > fMaxBytes) {
 *             Value* tail = fLRU.tail();
 *             SkASSERT(tail);
 *             if (tail == v) {
 *                 break;
 *             }
 *             this->removeInternal(tail);
 *         }
 *     }
 *
 *     void purge() override {
 *         SkAutoMutexExclusive mutex(fMutex);
 *         while (fCurrentBytes > 0) {
 *             Value* tail = fLRU.tail();
 *             SkASSERT(tail);
 *             this->removeInternal(tail);
 *         }
 *     }
 *
 *     void purgeByImageFilter(const SkImageFilter* filter) override {
 *         SkAutoMutexExclusive mutex(fMutex);
 *         auto* values = fImageFilterValues.find(filter);
 *         if (!values) {
 *             return;
 *         }
 *         for (Value* v : *values) {
 *             // We set the filter to be null so that removeInternal() won't delete from values while
 *             // we're iterating over it.
 *             v->fFilter = nullptr;
 *             this->removeInternal(v);
 *         }
 *         fImageFilterValues.remove(filter);
 *     }
 *
 *     SkDEBUGCODE(int count() const override { return fLookup.count(); })
 * private:
 *     void removeInternal(Value* v) {
 *         if (v->fFilter) {
 *             if (auto* values = fImageFilterValues.find(v->fFilter)) {
 *                 if (values->size() == 1 && (*values)[0] == v) {
 *                     fImageFilterValues.remove(v->fFilter);
 *                 } else {
 *                     for (auto it = values->begin(); it != values->end(); ++it) {
 *                         if (*it == v) {
 *                             values->erase(it);
 *                             break;
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *         fCurrentBytes -= v->fImage.image() ? v->fImage.image()->getSize() : 0;
 *         fLRU.remove(v);
 *         fLookup.remove(v->fKey);
 *         delete v;
 *     }
 * private:
 *     SkTDynamicHash<Value, Key>                          fLookup;
 *     mutable SkTInternalLList<Value>                     fLRU;
 *     // Value* always points to an item in fLookup.
 *     THashMap<const SkImageFilter*, std::vector<Value*>> fImageFilterValues;
 *     size_t                                              fMaxBytes;
 *     size_t                                              fCurrentBytes;
 *     mutable SkMutex                                     fMutex;
 * }
 * ```
 */
public open class CacheImpl public constructor(
  maxBytes: ULong,
) : SkImageFilterCache() {
  /**
   * C++ original:
   * ```cpp
   * SkTDynamicHash<Value, Key>                          fLookup
   * ```
   */
  private var fLookup: SkTDynamicHash<org.skia.modules.Value, CacheImplKey> =
      TODO("Initialize fLookup")

  /**
   * C++ original:
   * ```cpp
   * mutable SkTInternalLList<Value>                     fLRU
   * ```
   */
  private var fLRU: SkTInternalLList<org.skia.modules.Value> = TODO("Initialize fLRU")

  /**
   * C++ original:
   * ```cpp
   * size_t                                              fMaxBytes
   * ```
   */
  private var fMaxBytes: ULong = TODO("Initialize fMaxBytes")

  /**
   * C++ original:
   * ```cpp
   * size_t                                              fCurrentBytes
   * ```
   */
  private var fCurrentBytes: ULong = TODO("Initialize fCurrentBytes")

  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex                                     fMutex
   * ```
   */
  private var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * bool get(const Key& key, skif::FilterResult* result) const override {
   *         SkASSERT(result);
   *
   *         SkAutoMutexExclusive mutex(fMutex);
   *         if (Value* v = fLookup.find(key)) {
   *             if (v != fLRU.head()) {
   *                 fLRU.remove(v);
   *                 fLRU.addToHead(v);
   *             }
   *
   *             *result = v->fImage;
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public override fun `get`(key: CacheImplKey, result: FilterResult?): Boolean {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const Key& key, const SkImageFilter* filter,
   *              const skif::FilterResult& result) override {
   *         SkAutoMutexExclusive mutex(fMutex);
   *         if (Value* v = fLookup.find(key)) {
   *             this->removeInternal(v);
   *         }
   *         Value* v = new Value(key, result, filter);
   *         fLookup.add(v);
   *         fLRU.addToHead(v);
   *         fCurrentBytes += result.image() ? result.image()->getSize() : 0;
   *         if (auto* values = fImageFilterValues.find(filter)) {
   *             values->push_back(v);
   *         } else {
   *             fImageFilterValues.set(filter, {v});
   *         }
   *
   *         while (fCurrentBytes > fMaxBytes) {
   *             Value* tail = fLRU.tail();
   *             SkASSERT(tail);
   *             if (tail == v) {
   *                 break;
   *             }
   *             this->removeInternal(tail);
   *         }
   *     }
   * ```
   */
  public override fun `set`(
    key: CacheImplKey,
    filter: SkImageFilter?,
    result: FilterResult,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void purge() override {
   *         SkAutoMutexExclusive mutex(fMutex);
   *         while (fCurrentBytes > 0) {
   *             Value* tail = fLRU.tail();
   *             SkASSERT(tail);
   *             this->removeInternal(tail);
   *         }
   *     }
   * ```
   */
  public override fun purge() {
    TODO("Implement purge")
  }

  /**
   * C++ original:
   * ```cpp
   * void purgeByImageFilter(const SkImageFilter* filter) override {
   *         SkAutoMutexExclusive mutex(fMutex);
   *         auto* values = fImageFilterValues.find(filter);
   *         if (!values) {
   *             return;
   *         }
   *         for (Value* v : *values) {
   *             // We set the filter to be null so that removeInternal() won't delete from values while
   *             // we're iterating over it.
   *             v->fFilter = nullptr;
   *             this->removeInternal(v);
   *         }
   *         fImageFilterValues.remove(filter);
   *     }
   * ```
   */
  public override fun purgeByImageFilter(filter: SkImageFilter?) {
    TODO("Implement purgeByImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void removeInternal(Value* v) {
   *         if (v->fFilter) {
   *             if (auto* values = fImageFilterValues.find(v->fFilter)) {
   *                 if (values->size() == 1 && (*values)[0] == v) {
   *                     fImageFilterValues.remove(v->fFilter);
   *                 } else {
   *                     for (auto it = values->begin(); it != values->end(); ++it) {
   *                         if (*it == v) {
   *                             values->erase(it);
   *                             break;
   *                         }
   *                     }
   *                 }
   *             }
   *         }
   *         fCurrentBytes -= v->fImage.image() ? v->fImage.image()->getSize() : 0;
   *         fLRU.remove(v);
   *         fLookup.remove(v->fKey);
   *         delete v;
   *     }
   * ```
   */
  private fun removeInternal(v: Value?) {
    TODO("Implement removeInternal")
  }

  public open class Value public constructor(
    public var fKey: Key,
    public var fImage: FilterResult,
    public val fFilter: SkImageFilter?,
  ) {
    public constructor(
      key: Key,
      image: FilterResult,
      filter: SkImageFilter?,
    ) : this() {
      TODO("Implement constructor")
    }

    public companion object {
      public fun getKey(v: org.skia.modules.Value): Key {
        TODO("Implement getKey")
      }

      public fun hash(key: Key): UInt {
        TODO("Implement hash")
      }
    }
  }
}
