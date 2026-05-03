package org.skia.core

import kotlin.Int
import org.skia.pdf.Key

/**
 * C++ original:
 * ```cpp
 * class SkTDynamicHash {
 * public:
 *     SkTDynamicHash() {}
 *
 *     // It is not safe to call set() or remove() while iterating with either foreach().
 *     // If you mutate the entries be very careful not to change the Key.
 *
 *     template <typename Fn>  // f(T*)
 *     void foreach(Fn&& fn) {
 *         fTable.foreach([&](T** entry) { fn(*entry); });
 *     }
 *     template <typename Fn>  // f(T) or f(const T&)
 *     void foreach(Fn&& fn) const {
 *         fTable.foreach([&](T* entry) { fn(*entry); });
 *     }
 *
 *     int count() const { return fTable.count(); }
 *
 *     size_t approxBytesUsed() const { return fTable.approxBytesUsed(); }
 *
 *     T* find(const Key& key) const { return fTable.findOrNull(key); }
 *
 *     void add(T* entry) { fTable.set(entry); }
 *     void remove(const Key& key) { fTable.remove(key); }
 *
 *     void rewind() { fTable.reset(); }
 *     void reset () { fTable.reset(); }
 *
 * private:
 *     struct AdaptedTraits {
 *         static const Key& GetKey(T* entry) { return Traits::GetKey(*entry); }
 *         static uint32_t Hash(const Key& key) { return Traits::Hash(key); }
 *     };
 *     skia_private::THashTable<T*, Key, AdaptedTraits> fTable;
 * }
 * ```
 */
public data class SkTDynamicHash<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashTable<T*, Key, AdaptedTraits> fTable
   * ```
   */
  private var fTable: THashTable<T?, Key, AdaptedTraits>,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(T*)
   *     void foreach(Fn&& fn) {
   *         fTable.foreach([&](T** entry) { fn(*entry); });
   *     }
   * ```
   */
  public fun <Fn> foreach(param0: T?) {
    TODO("Implement foreach")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(T) or f(const T&)
   *     void foreach(Fn&& fn) const {
   *         fTable.foreach([&](T* entry) { fn(*entry); });
   *     }
   * ```
   */
  public fun <Fn> foreach(param0: T) {
    TODO("Implement foreach")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fTable.count(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t approxBytesUsed() const { return fTable.approxBytesUsed(); }
   * ```
   */
  public fun approxBytesUsed(): Int {
    TODO("Implement approxBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * T* find(const Key& key) const { return fTable.findOrNull(key); }
   * ```
   */
  public fun find(key: Key): T {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(T* entry) { fTable.set(entry); }
   * ```
   */
  public fun add(entry: T?) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(const Key& key) { fTable.remove(key); }
   * ```
   */
  public fun remove(key: Key) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * void rewind() { fTable.reset(); }
   * ```
   */
  public fun rewind() {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset () { fTable.reset(); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  public open class AdaptedTraits {
    public companion object {
      public fun getKey(entry: T?): Key {
        TODO("Implement getKey")
      }

      public fun hash(key: Key): Int {
        TODO("Implement hash")
      }
    }
  }
}
