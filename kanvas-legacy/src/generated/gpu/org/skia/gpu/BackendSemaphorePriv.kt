package org.skia.gpu

import SomeBackendSemaphoreData
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class BackendSemaphorePriv {
 * public:
 *     template <typename SomeBackendSemaphoreData>
 *     static BackendSemaphore Make(BackendApi backend, const SomeBackendSemaphoreData& textureData) {
 *         return BackendSemaphore(backend, textureData);
 *     }
 *
 *     static const BackendSemaphoreData* GetData(const BackendSemaphore& info) {
 *         return info.fSemaphoreData.get();
 *     }
 * }
 * ```
 */
public open class BackendSemaphorePriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template <typename SomeBackendSemaphoreData>
     *     static BackendSemaphore Make(BackendApi backend, const SomeBackendSemaphoreData& textureData) {
     *         return BackendSemaphore(backend, textureData);
     *     }
     * ```
     */
    public fun <SomeBackendSemaphoreData> make(backend: BackendApi, textureData: SomeBackendSemaphoreData): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static const BackendSemaphoreData* GetData(const BackendSemaphore& info) {
     *         return info.fSemaphoreData.get();
     *     }
     * ```
     */
    public fun getData(info: BackendSemaphore): BackendSemaphoreData {
      TODO("Implement getData")
    }
  }
}
