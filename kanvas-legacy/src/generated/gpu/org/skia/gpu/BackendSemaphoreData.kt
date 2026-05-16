package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class BackendSemaphoreData {
 * public:
 *     virtual ~BackendSemaphoreData();
 *
 * #if defined(SK_DEBUG)
 *     virtual skgpu::BackendApi type() const = 0;
 * #endif
 * protected:
 *     BackendSemaphoreData() = default;
 *     BackendSemaphoreData(const BackendSemaphoreData&) = default;
 *
 *     using AnyBackendSemaphoreData = BackendSemaphore::AnyBackendSemaphoreData;
 *
 * private:
 *     friend class BackendSemaphore;
 *
 *     virtual void copyTo(AnyBackendSemaphoreData& dstData) const = 0;
 * }
 * ```
 */
public abstract class BackendSemaphoreData public constructor() {
  /**
   * C++ original:
   * ```cpp
   * BackendSemaphoreData() = default
   * ```
   */
  public constructor(param0: BackendSemaphoreData) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void copyTo(AnyBackendSemaphoreData& dstData) const = 0
   * ```
   */
  private abstract fun copyTo(dstData: BackendSemaphoreDataAnyBackendSemaphoreData)
}
