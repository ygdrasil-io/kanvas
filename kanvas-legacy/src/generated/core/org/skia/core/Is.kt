package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class Is {
 * public:
 *     Is() : fPtr(nullptr) {}
 *
 *     typedef T type;
 *     type* get() { return fPtr; }
 *
 *     bool operator()(T* ptr) {
 *         fPtr = ptr;
 *         return true;
 *     }
 *
 *     template <typename U>
 *     bool operator()(U*) {
 *         fPtr = nullptr;
 *         return false;
 *     }
 *
 * private:
 *     type* fPtr;
 * }
 * ```
 */
public data class Is<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * type* fPtr
   * ```
   */
  private var fPtr: Istype?,
) {
  /**
   * C++ original:
   * ```cpp
   * type* get() { return fPtr; }
   * ```
   */
  public fun `get`(): Istype {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator()(T* ptr) {
   *         fPtr = ptr;
   *         return true;
   *     }
   * ```
   */
  public operator fun invoke(ptr: T): Boolean {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename U>
   *     bool operator()(U*) {
   *         fPtr = nullptr;
   *         return false;
   *     }
   * ```
   */
  public operator fun <U> invoke(param0: U?): Boolean {
    TODO("Implement invoke")
  }
}
