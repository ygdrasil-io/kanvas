package org.skia.core

import HashK
import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename K, typename V, typename HashK = SkGoodHash>
 * class THashMap {
 * public:
 *     // Allow default construction and assignment.
 *     THashMap() = default;
 *
 *     THashMap(THashMap<K, V, HashK>&& that) = default;
 *     THashMap(const THashMap<K, V, HashK>& that) = default;
 *
 *     THashMap<K, V, HashK>& operator=(THashMap<K, V, HashK>&& that) = default;
 *     THashMap<K, V, HashK>& operator=(const THashMap<K, V, HashK>& that) = default;
 *
 *     // Construct with an initializer list of key-value pairs.
 *     struct Pair : public std::pair<K, V> {
 *         using std::pair<K, V>::pair;
 *         static const K& GetKey(const Pair& p) { return p.first; }
 *         static auto Hash(const K& key) { return HashK()(key); }
 *     };
 *
 *     THashMap(std::initializer_list<Pair> pairs) {
 *         int capacity = pairs.size() >= 4 ? SkNextPow2(pairs.size() * 4 / 3)
 *                                          : 4;
 *         fTable.resize(capacity);
 *         for (const Pair& p : pairs) {
 *             fTable.set(p);
 *         }
 *     }
 *
 *     // Clear the map.
 *     void reset() { fTable.reset(); }
 *
 *     // How many key/value pairs are in the table?
 *     int count() const { return fTable.count(); }
 *
 *     // Is empty?
 *     bool empty() const { return fTable.count() == 0; }
 *
 *     // Approximately how many bytes of memory do we use beyond sizeof(*this)?
 *     size_t approxBytesUsed() const { return fTable.approxBytesUsed(); }
 *
 *     // Reserve extra capacity.
 *     void reserve(int n) { fTable.reserve(n); }
 *
 *     // Exchange two hash maps.
 *     void swap(THashMap& that) { fTable.swap(that.fTable); }
 *     void swap(THashMap&& that) { fTable.swap(std::move(that.fTable)); }
 *
 *     // N.B. The pointers returned by set() and find() are valid only until the next call to set().
 *
 *     // Set key to val in the table, replacing any previous value with the same key.
 *     // We copy both key and val, and return a pointer to the value copy now in the table.
 *     V* set(K key, V val) {
 *         Pair* out = fTable.set({std::move(key), std::move(val)});
 *         return &out->second;
 *     }
 *
 *     // If there is key/value entry in the table with this key, return a pointer to the value.
 *     // If not, return null.
 *     V* find(const K& key) const {
 *         if (Pair* p = fTable.find(key)) {
 *             return &p->second;
 *         }
 *         return nullptr;
 *     }
 *
 *     V& operator[](const K& key) {
 *         if (V* val = this->find(key)) {
 *             return *val;
 *         }
 *         return *this->set(key, V{});
 *     }
 *
 *     // Removes the key/value entry in the table with this key. Asserts if the key is not present.
 *     void remove(const K& key) {
 *         fTable.remove(key);
 *     }
 *
 *     // If the key exists in the table, removes it and returns true. Otherwise, returns false.
 *     bool removeIfExists(const K& key) {
 *         return fTable.removeIfExists(key);
 *     }
 *
 *     // Call fn on every key/value pair in the table.  You may mutate the value but not the key.
 *     template <typename Fn,  // f(K, V*) or f(const K&, V*)
 *               std::enable_if_t<std::is_invocable_v<Fn, K, V*>>* = nullptr>
 *     void foreach(Fn&& fn) {
 *         fTable.foreach([&fn](Pair* p) { fn(p->first, &p->second); });
 *     }
 *
 *     // Call fn on every key/value pair in the table.  You may not mutate anything.
 *     template <typename Fn,  // f(K, V), f(const K&, V), f(K, const V&) or f(const K&, const V&).
 *               std::enable_if_t<std::is_invocable_v<Fn, K, V>>* = nullptr>
 *     void foreach(Fn&& fn) const {
 *         fTable.foreach([&fn](const Pair& p) { fn(p.first, p.second); });
 *     }
 *
 *     // Call fn on every key/value pair in the table.  You may not mutate anything.
 *     template <typename Fn,  // f(Pair), or f(const Pair&)
 *               std::enable_if_t<std::is_invocable_v<Fn, Pair>>* = nullptr>
 *     void foreach(Fn&& fn) const {
 *         fTable.foreach([&fn](const Pair& p) { fn(p); });
 *     }
 *
 *     // Dereferencing an iterator gives back a key-value pair, suitable for structured binding.
 *     using Iter = typename THashTable<Pair, K>::template Iter<std::pair<K, V>>;
 *
 *     Iter begin() const {
 *         return Iter::MakeBegin(&fTable);
 *     }
 *
 *     Iter end() const {
 *         return Iter::MakeEnd(&fTable);
 *     }
 *
 * private:
 *     THashTable<Pair, K> fTable;
 * }
 * ```
 */
public data class THashMap<K, V, HashK> public constructor(
  /**
   * C++ original:
   * ```cpp
   * THashTable<Pair, K> fTable
   * ```
   */
  private var fTable: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * THashMap<K, V, HashK>& operator=(THashMap<K, V, HashK>&& that) = default
   * ```
   */
  public fun assign(that: THashMap<K, V, HashK>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * THashMap<K, V, HashK>& operator=(const THashMap<K, V, HashK>& that) = default
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() { fTable.reset(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fTable.count(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fTable.count() == 0; }
   * ```
   */
  public fun approxBytesUsed(): Int {
    TODO("Implement approxBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t approxBytesUsed() const { return fTable.approxBytesUsed(); }
   * ```
   */
  public fun reserve(n: Int) {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void reserve(int n) { fTable.reserve(n); }
   * ```
   */
  public fun swap(that: THashMap<K, V, HashK>) {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashMap& that) { fTable.swap(that.fTable); }
   * ```
   */
  public fun `set`(key: K, `val`: V): V {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashMap&& that) { fTable.swap(std::move(that.fTable)); }
   * ```
   */
  public fun find(key: K): V {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * V* set(K key, V val) {
   *         Pair* out = fTable.set({std::move(key), std::move(val)});
   *         return &out->second;
   *     }
   * ```
   */
  public operator fun `get`(key: K): V {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * V* find(const K& key) const {
   *         if (Pair* p = fTable.find(key)) {
   *             return &p->second;
   *         }
   *         return nullptr;
   *     }
   * ```
   */
  public fun remove(key: K) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * V& operator[](const K& key) {
   *         if (V* val = this->find(key)) {
   *             return *val;
   *         }
   *         return *this->set(key, V{});
   *     }
   * ```
   */
  public fun removeIfExists(key: K): Boolean {
    TODO("Implement removeIfExists")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(const K& key) {
   *         fTable.remove(key);
   *     }
   * ```
   */
  public fun begin(): Int {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool removeIfExists(const K& key) {
   *         return fTable.removeIfExists(key);
   *     }
   * ```
   */
  public fun end(): Int {
    TODO("Implement end")
  }

  public open class Pair : kotlin.Pair<Any, Any>(), K, V {
    public companion object {
      public fun getKey(p: org.skia.tests.Pair): K {
        TODO("Implement getKey")
      }

      public fun hash(key: K): Any {
        TODO("Implement hash")
      }
    }
  }
}
