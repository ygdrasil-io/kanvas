package org.skia.gpu

import kotlin.Unit
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class SubRunInitializer {
 * public:
 *     SubRunInitializer(void* memory) : fMemory{memory} { SkASSERT(memory != nullptr); }
 *     ~SubRunInitializer() {
 *         ::operator delete(fMemory);
 *     }
 *     template <typename... Args>
 *     T* initialize(Args&&... args) {
 *         // Warn on more than one initialization.
 *         SkASSERT(fMemory != nullptr);
 *         return new (std::exchange(fMemory, nullptr)) T(std::forward<Args>(args)...);
 *     }
 *
 * private:
 *     void* fMemory;
 * }
 * ```
 */
public data class SubRunInitializer<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * void* fMemory
   * ```
   */
  private var fMemory: Unit?,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename... Args>
   *     T* initialize(Args&&... args) {
   *         // Warn on more than one initialization.
   *         SkASSERT(fMemory != nullptr);
   *         return new (std::exchange(fMemory, nullptr)) T(std::forward<Args>(args)...);
   *     }
   * ```
   */
  public fun <Args> initialize(args: Args): T {
    TODO("Implement initialize")
  }
}
