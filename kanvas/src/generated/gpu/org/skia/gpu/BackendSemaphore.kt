package org.skia.gpu

import kotlin.Boolean
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API BackendSemaphore {
 * public:
 *     BackendSemaphore();
 *
 *     BackendSemaphore(const BackendSemaphore&);
 *
 *     ~BackendSemaphore();
 *
 *     BackendSemaphore& operator=(const BackendSemaphore&);
 *
 *     bool isValid() const { return fIsValid; }
 *     BackendApi backend() const { return fBackend; }
 *
 * private:
 *     friend class BackendSemaphoreData;
 *     friend class BackendSemaphorePriv;
 *
 *     // Size determined by looking at the BackendSemaphoreData subclasses, then
 *     // guessing-and-checking. Compiler will complain if this is too small - in that case, just
 *     // increase the number.
 *     inline constexpr static size_t kMaxSubclassSize = 24;
 *     using AnyBackendSemaphoreData = SkAnySubclass<BackendSemaphoreData, kMaxSubclassSize>;
 *
 *     template <typename SomeBackendSemaphoreData>
 *     BackendSemaphore(BackendApi backend, const SomeBackendSemaphoreData& data)
 *             : fBackend(backend), fIsValid(true) {
 *         fSemaphoreData.emplace<SomeBackendSemaphoreData>(data);
 *     }
 *
 *     BackendApi fBackend;
 *     AnyBackendSemaphoreData fSemaphoreData;
 *
 *     bool fIsValid = false;
 * }
 * ```
 */
public data class BackendSemaphore public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline constexpr static size_t kMaxSubclassSize = 24
   * ```
   */
  private var fBackend: BackendApi,
  /**
   * C++ original:
   * ```cpp
   * BackendApi fBackend
   * ```
   */
  private var fSemaphoreData: BackendSemaphoreData,
  /**
   * C++ original:
   * ```cpp
   * AnyBackendSemaphoreData fSemaphoreData
   * ```
   */
  private var fIsValid: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * BackendSemaphore& BackendSemaphore::operator=(const BackendSemaphore& that) {
   *     if (!that.isValid()) {
   *         fIsValid = false;
   *         return *this;
   *     }
   *     SkASSERT(!this->isValid() || this->backend() == that.backend());
   *     fIsValid = true;
   *     fBackend = that.fBackend;
   *
   *     switch (that.backend()) {
   *         case BackendApi::kDawn:
   *             SK_ABORT("Unsupported Backend");
   *         case BackendApi::kMetal:
   *         case BackendApi::kVulkan:
   *             fSemaphoreData.reset();
   *             that.fSemaphoreData->copyTo(fSemaphoreData);
   *             break;
   *         default:
   *             SK_ABORT("Unsupported Backend");
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: BackendSemaphore) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fIsValid; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi backend() const { return fBackend; }
   * ```
   */
  public fun backend(): BackendApi {
    TODO("Implement backend")
  }

  public companion object {
    private val kMaxSubclassSize: ULong = TODO("Initialize kMaxSubclassSize")
  }
}
