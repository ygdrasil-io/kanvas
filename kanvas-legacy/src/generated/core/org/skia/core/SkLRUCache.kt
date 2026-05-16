package org.skia.core

import kotlin.Int
import kotlin.Unit
import undefined.Fn
import undefined.K1
import undefined.V1

/**
 * C++ original:
 * ```cpp
 * template <typename K, typename V, typename HashK = SkGoodHash, typename PurgeCB = SkNoOpPurge>
 * class SkLRUCache {
 * private:
 *     struct Entry {
 *         template<typename K1, typename V1>
 *         Entry(K1&& key, V1&& value)
 *             : fKey(std::forward<K1>(key))
 *             , fValue(std::forward<V1>(value)) {}
 *
 *         const K fKey;
 *         V fValue;
 *
 *         SK_DECLARE_INTERNAL_LLIST_INTERFACE(Entry);
 *     };
 *
 * public:
 *     explicit SkLRUCache(int maxCount, void* context = nullptr)
 *             : fMaxCount(maxCount)
 *             , fContext(context) {}
 *     SkLRUCache() = delete;
 *
 *     ~SkLRUCache() {
 *         Entry* node = fLRU.head();
 *         while (node) {
 *             fLRU.remove(node);
 *             delete node;
 *             node = fLRU.head();
 *         }
 *     }
 *
 *     // Make noncopyable
 *     SkLRUCache(const SkLRUCache&) = delete;
 *     SkLRUCache& operator=(const SkLRUCache&) = delete;
 *
 *     V* find(const K& key) {
 *         Entry** value = fMap.find(key);
 *         if (!value) {
 *             return nullptr;
 *         }
 *         Entry* entry = *value;
 *         if (entry != fLRU.head()) {
 *             fLRU.remove(entry);
 *             fLRU.addToHead(entry);
 *         } // else it's already at head position, don't need to do anything
 *         return &entry->fValue;
 *     }
 *
 *     V* insert(Entry* entry) {
 *         fMap.set(entry);
 *         fLRU.addToHead(entry);
 *         while (fMap.count() > fMaxCount) {
 *             this->remove(fLRU.tail()->fKey);
 *         }
 *         return &entry->fValue;
 *     }
 *
 *     template<typename K1, typename V1>
 *     V* insert(K1&& key, V1&& value) {
 *         SkASSERT(!this->find(key));
 *         return this->insert(new Entry(std::forward<K1>(key), std::forward<V1>(value)));
 *     }
 *
 *     template<typename K1, typename V1>
 *     V* insert_or_update(K1&& key, V1&& value) {
 *         if (V* found = this->find(key)) {
 *             *found = std::forward<V1>(value);
 *             return found;
 *         }
 *         return this->insert(new Entry(std::forward<K1>(key), std::forward<V1>(value)));
 *     }
 *
 *     int count() const {
 *         return fMap.count();
 *     }
 *
 *     template <typename Fn>  // f(const K*, V*)
 *     void foreach(Fn&& fn) {
 *         typename SkTInternalLList<Entry>::Iter iter;
 *         for (Entry* e = iter.init(fLRU, SkTInternalLList<Entry>::Iter::kHead_IterStart); e;
 *              e = iter.next()) {
 *             fn(&e->fKey, &e->fValue);
 *         }
 *     }
 *
 *     void reset() {
 *         fMap.reset();
 *         for (Entry* e = fLRU.head(); e; e = fLRU.head()) {
 *             fLRU.remove(e);
 *             delete e;
 *         }
 *     }
 *
 *     void remove(const K& key) {
 *         Entry** value = fMap.find(key);
 *         SkASSERT(value);
 *         Entry* entry = *value;
 *         SkASSERT(key == entry->fKey);
 *         PurgeCB()(fContext, key, &entry->fValue);
 *         fMap.remove(key);
 *         fLRU.remove(entry);
 *         delete entry;
 *     }
 *
 * private:
 *     struct Traits {
 *         static const K& GetKey(Entry* e) {
 *             return e->fKey;
 *         }
 *
 *         static uint32_t Hash(const K& k) {
 *             return HashK()(k);
 *         }
 *     };
 *
 *     int                                         fMaxCount;
 *     skia_private::THashTable<Entry*, K, Traits> fMap;
 *     SkTInternalLList<Entry>                     fLRU;
 *     void*                                       fContext;
 * }
 * ```
 */
public data class SkLRUCache<K, V, HashK, PurgeCB> public constructor(
  /**
   * C++ original:
   * ```cpp
   * int                                         fMaxCount
   * ```
   */
  private var fMaxCount: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashTable<Entry*, K, Traits> fMap
   * ```
   */
  private var fMap: THashTable<org.skia.core.Entry?, K, undefined.Traits>,
  /**
   * C++ original:
   * ```cpp
   * SkTInternalLList<Entry>                     fLRU
   * ```
   */
  private var fLRU: SkTInternalLList<org.skia.core.Entry>,
  /**
   * C++ original:
   * ```cpp
   * void*                                       fContext
   * ```
   */
  private var fContext: Unit?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkLRUCache& operator=(const SkLRUCache&) = delete
   * ```
   */
  public fun assign(param0: SkLRUCache<K, V, HashK, PurgeCB>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * V* find(const K& key) {
   *         Entry** value = fMap.find(key);
   *         if (!value) {
   *             return nullptr;
   *         }
   *         Entry* entry = *value;
   *         if (entry != fLRU.head()) {
   *             fLRU.remove(entry);
   *             fLRU.addToHead(entry);
   *         } // else it's already at head position, don't need to do anything
   *         return &entry->fValue;
   *     }
   * ```
   */
  public fun find(key: K): V {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * V* insert(Entry* entry) {
   *         fMap.set(entry);
   *         fLRU.addToHead(entry);
   *         while (fMap.count() > fMaxCount) {
   *             this->remove(fLRU.tail()->fKey);
   *         }
   *         return &entry->fValue;
   *     }
   * ```
   */
  public fun insert(entry: Entry?): V {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename K1, typename V1>
   *     V* insert(K1&& key, V1&& value) {
   *         SkASSERT(!this->find(key));
   *         return this->insert(new Entry(std::forward<K1>(key), std::forward<V1>(value)));
   *     }
   * ```
   */
  public fun <K1, V1> insert(key: K1, `value`: V1): V {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename K1, typename V1>
   *     V* insert_or_update(K1&& key, V1&& value) {
   *         if (V* found = this->find(key)) {
   *             *found = std::forward<V1>(value);
   *             return found;
   *         }
   *         return this->insert(new Entry(std::forward<K1>(key), std::forward<V1>(value)));
   *     }
   * ```
   */
  public fun <K1, V1> insertOrUpdate(key: K1, `value`: V1): V {
    TODO("Implement insertOrUpdate")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const {
   *         return fMap.count();
   *     }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(const K*, V*)
   *     void foreach(Fn&& fn) {
   *         typename SkTInternalLList<Entry>::Iter iter;
   *         for (Entry* e = iter.init(fLRU, SkTInternalLList<Entry>::Iter::kHead_IterStart); e;
   *              e = iter.next()) {
   *             fn(&e->fKey, &e->fValue);
   *         }
   *     }
   * ```
   */
  public fun <Fn> foreach(param0: Fn) {
    TODO("Implement foreach")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fMap.reset();
   *         for (Entry* e = fLRU.head(); e; e = fLRU.head()) {
   *             fLRU.remove(e);
   *             delete e;
   *         }
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(const K& key) {
   *         Entry** value = fMap.find(key);
   *         SkASSERT(value);
   *         Entry* entry = *value;
   *         SkASSERT(key == entry->fKey);
   *         PurgeCB()(fContext, key, &entry->fValue);
   *         fMap.remove(key);
   *         fLRU.remove(entry);
   *         delete entry;
   *     }
   * ```
   */
  public fun remove(key: K) {
    TODO("Implement remove")
  }

  public open class Entry<K, V, HashK, PurgeCB> public constructor(
    public val fKey: K,
    public var fValue: V,
  ) {
    public constructor(key: K1, `value`: V1) : this() {
      TODO("Implement constructor")
    }
  }

  public open class Traits {
    public companion object {
      public fun getKey(e: org.skia.core.Entry?): K {
        TODO("Implement getKey")
      }

      public fun hash(k: K): Int {
        TODO("Implement hash")
      }
    }
  }
}
