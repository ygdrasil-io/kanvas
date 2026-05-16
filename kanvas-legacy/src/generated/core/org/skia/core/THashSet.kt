package org.skia.core

import HashT
import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename HashT = SkGoodHash>
 * class THashSet {
 * public:
 *     // Allow default construction and assignment.
 *     THashSet() = default;
 *
 *     THashSet(THashSet<T, HashT>&& that) = default;
 *     THashSet(const THashSet<T, HashT>& that) = default;
 *
 *     THashSet<T, HashT>& operator=(THashSet<T, HashT>&& that) = default;
 *     THashSet<T, HashT>& operator=(const THashSet<T, HashT>& that) = default;
 *
 *     // Construct with an initializer list of Ts.
 *     THashSet(std::initializer_list<T> vals) {
 *         int capacity = vals.size() >= 4 ? SkNextPow2(vals.size() * 4 / 3)
 *                                         : 4;
 *         fTable.resize(capacity);
 *         for (const T& val : vals) {
 *             fTable.set(val);
 *         }
 *     }
 *
 *     // Clear the set.
 *     void reset() { fTable.reset(); }
 *
 *     // How many items are in the set?
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
 *     // Exchange two hash sets.
 *     void swap(THashSet& that) { fTable.swap(that.fTable); }
 *     void swap(THashSet&& that) { fTable.swap(std::move(that.fTable)); }
 *
 *     // Copy an item into the set.
 *     void add(T item) { fTable.set(std::move(item)); }
 *
 *     // Is this item in the set?
 *     bool contains(const T& item) const { return SkToBool(this->find(item)); }
 *
 *     // If an item equal to this is in the set, return a pointer to it, otherwise null.
 *     // This pointer remains valid until the next call to add().
 *     const T* find(const T& item) const { return fTable.find(item); }
 *
 *     // Remove the item in the set equal to this.
 *     void remove(const T& item) {
 *         SkASSERT(this->contains(item));
 *         fTable.remove(item);
 *     }
 *
 *     // Call fn on every item in the set.  You may not mutate anything.
 *     template <typename Fn>  // f(T), f(const T&)
 *     void foreach (Fn&& fn) const {
 *         fTable.foreach(fn);
 *     }
 *
 * private:
 *     struct Traits {
 *         static const T& GetKey(const T& item) { return item; }
 *         static auto Hash(const T& item) { return HashT()(item); }
 *     };
 *
 * public:
 *     using Iter = typename THashTable<T, T, Traits>::template Iter<T>;
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
 *     THashTable<T, T, Traits> fTable;
 * }
 * ```
 */
public data class THashSet<T, HashT> public constructor(
  /**
   * C++ original:
   * ```cpp
   * THashTable<T, T, Traits> fTable
   * ```
   */
  private var fTable: THashTable<T, T, undefined.Traits>,
) {
  /**
   * C++ original:
   * ```cpp
   * THashSet<T, HashT>& operator=(THashSet<T, HashT>&& that) = default
   * ```
   */
  public fun assign(that: THashSet<T, HashT>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * THashSet<T, HashT>& operator=(const THashSet<T, HashT>& that) = default
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
  public fun swap(that: THashSet<T, HashT>) {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashSet& that) { fTable.swap(that.fTable); }
   * ```
   */
  public fun add(item: T) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashSet&& that) { fTable.swap(std::move(that.fTable)); }
   * ```
   */
  public fun contains(item: T): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(T item) { fTable.set(std::move(item)); }
   * ```
   */
  public fun find(item: T): T {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const T& item) const { return SkToBool(this->find(item)); }
   * ```
   */
  public fun remove(item: T) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * const T* find(const T& item) const { return fTable.find(item); }
   * ```
   */
  public fun <Fn> foreach(param0: T) {
    TODO("Implement foreach")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(const T& item) {
   *         SkASSERT(this->contains(item));
   *         fTable.remove(item);
   *     }
   * ```
   */
  public fun begin(): THashSetIter {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(T), f(const T&)
   *     void foreach (Fn&& fn) const {
   *         fTable.foreach(fn);
   *     }
   * ```
   */
  public fun end(): THashSetIter {
    TODO("Implement end")
  }

  public open class Traits {
    public companion object {
      public fun getKey(item: T): T {
        TODO("Implement getKey")
      }

      public fun hash(item: T): Any {
        TODO("Implement hash")
      }
    }
  }
}
