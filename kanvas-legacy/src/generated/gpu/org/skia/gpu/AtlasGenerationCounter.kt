package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class AtlasGenerationCounter {
 * public:
 *     inline static constexpr uint64_t kInvalidGeneration = 0;
 *     uint64_t next() {
 *         return fGeneration++;
 *     }
 *
 * private:
 *     uint64_t fGeneration{1};
 * }
 * ```
 */
public open class AtlasGenerationCounter {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr uint64_t kInvalidGeneration = 0
   * ```
   */
  private var fGeneration: Int = TODO("Initialize fGeneration")

  /**
   * C++ original:
   * ```cpp
   * uint64_t next() {
   *         return fGeneration++;
   *     }
   * ```
   */
  public fun next(): Int {
    TODO("Implement next")
  }

  public companion object {
    public val kInvalidGeneration: Int = TODO("Initialize kInvalidGeneration")
  }
}
