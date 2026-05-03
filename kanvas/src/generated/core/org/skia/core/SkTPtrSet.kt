package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkTPtrSet : public SkPtrSet {
 * public:
 *     uint32_t find(T ptr) {
 *         return this->INHERITED::find((void*)ptr);
 *     }
 *     uint32_t add(T ptr) {
 *         return this->INHERITED::add((void*)ptr);
 *     }
 *
 *     void copyToArray(T* array) const {
 *         this->INHERITED::copyToArray((void**)array);
 *     }
 *
 * private:
 *     using INHERITED = SkPtrSet;
 * }
 * ```
 */
public open class SkTPtrSet<T> : SkPtrSet() {
  /**
   * C++ original:
   * ```cpp
   * uint32_t find(T ptr) {
   *         return this->INHERITED::find((void*)ptr);
   *     }
   * ```
   */
  public fun find(ptr: T): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t add(T ptr) {
   *         return this->INHERITED::add((void*)ptr);
   *     }
   * ```
   */
  public fun add(ptr: T): Int {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyToArray(T* array) const {
   *         this->INHERITED::copyToArray((void**)array);
   *     }
   * ```
   */
  public fun copyToArray(array: T) {
    TODO("Implement copyToArray")
  }
}
