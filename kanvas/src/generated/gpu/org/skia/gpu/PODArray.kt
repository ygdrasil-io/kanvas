package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class PODArray {
 * public:
 *     PODArray() {}
 *     PODArray(T* ptr) : fPtr(ptr) {}
 *     // Default copy and assign.
 *
 *     ACT_AS_PTR(fPtr)
 * private:
 *     T* fPtr;
 * }
 * ```
 */
public data class PODArray<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T* fPtr
   * ```
   */
  private var fPtr: T,
)
