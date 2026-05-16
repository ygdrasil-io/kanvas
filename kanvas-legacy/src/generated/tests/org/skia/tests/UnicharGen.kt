package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class UnicharGen {
 *         SkUnichar fU;
 *         const int fStep;
 *     public:
 *         UnicharGen(int step) : fU(0), fStep(step) {}
 *
 *         SkUnichar next() {
 *             fU += fStep;
 *             return fU;
 *         }
 *     }
 * ```
 */
public data class UnicharGen public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkUnichar fU
   * ```
   */
  private var fU: SkUnichar,
  /**
   * C++ original:
   * ```cpp
   * const int fStep
   * ```
   */
  private val fStep: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkUnichar next() {
   *             fU += fStep;
   *             return fU;
   *         }
   * ```
   */
  public fun next(): SkUnichar {
    TODO("Implement next")
  }
}
