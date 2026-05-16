package org.skia.core

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class Optional {
 * public:
 *     Optional() : fPtr(nullptr) {}
 *     Optional(T* ptr) : fPtr(ptr) {}
 *     Optional(Optional&& o) : fPtr(o.fPtr) {
 *         o.fPtr = nullptr;
 *     }
 *     ~Optional() { if (fPtr) fPtr->~T(); }
 *
 *     ACT_AS_PTR(fPtr)
 * private:
 *     T* fPtr;
 *     Optional(const Optional&) = delete;
 *     Optional& operator=(const Optional&) = delete;
 * }
 * ```
 */
public data class Optional<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T* fPtr
   * ```
   */
  private var fPtr: T,
) {
  /**
   * C++ original:
   * ```cpp
   * Optional& operator=(const Optional&) = delete
   * ```
   */
  private fun assign(param0: Optional<T>) {
    TODO("Implement assign")
  }
}
