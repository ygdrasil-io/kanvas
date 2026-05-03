package org.skia.core

import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * class SkAlignedSTStorage {
 * public:
 *     SkAlignedSTStorage() {}
 *     SkAlignedSTStorage(SkAlignedSTStorage&&) = delete;
 *     SkAlignedSTStorage(const SkAlignedSTStorage&) = delete;
 *     SkAlignedSTStorage& operator=(SkAlignedSTStorage&&) = delete;
 *     SkAlignedSTStorage& operator=(const SkAlignedSTStorage&) = delete;
 *
 *     // Returns void* because this object does not initialize the
 *     // memory. Use placement new for types that require a constructor.
 *     void* get() { return fStorage; }
 *     const void* get() const { return fStorage; }
 *
 *     // Act as a container of bytes because the storage is uninitialized.
 *     std::byte* data() { return fStorage; }
 *     const std::byte* data() const { return fStorage; }
 *     size_t size() const { return std::size(fStorage); }
 *
 * private:
 *     alignas(T) std::byte fStorage[sizeof(T) * N];
 * }
 * ```
 */
public data class SkAlignedSTStorage1<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::byte fStorage[sizeof(T) * N]
   * ```
   */
  private var fStorage: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * SkAlignedSTStorage& operator=(SkAlignedSTStorage&&) = delete
   * ```
   */
  public fun assign(param0: SkAlignedSTStorage<T>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlignedSTStorage& operator=(const SkAlignedSTStorage&) = delete
   * ```
   */
  public fun `get`() {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void* get() { return fStorage; }
   * ```
   */
  public fun `data`(): Int {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* get() const { return fStorage; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }
}
