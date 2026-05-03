package org.skia.gpu

import kotlin.Int
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class KeyContextWithLocalMatrix : public KeyContext {
 * public:
 *     KeyContextWithLocalMatrix(const KeyContext& other, const SkMatrix& childLM)
 *             : KeyContext(other) {
 *         if (fLocalMatrix) {
 *             fStorage = SkMatrix::Concat(childLM, *fLocalMatrix);
 *         } else {
 *             fStorage = childLM;
 *         }
 *
 *         fLocalMatrix = &fStorage;
 *     }
 *
 * private:
 *     KeyContextWithLocalMatrix(const KeyContextWithLocalMatrix&) = delete;
 *     KeyContextWithLocalMatrix& operator=(const KeyContextWithLocalMatrix&) = delete;
 *
 *     SkMatrix fStorage;
 * }
 * ```
 */
public open class KeyContextWithLocalMatrix public constructor(
  other: KeyContext,
  childLM: SkMatrix,
) : KeyContext(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fStorage
   * ```
   */
  private var fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * KeyContextWithLocalMatrix(const KeyContext& other, const SkMatrix& childLM)
   *             : KeyContext(other) {
   *         if (fLocalMatrix) {
   *             fStorage = SkMatrix::Concat(childLM, *fLocalMatrix);
   *         } else {
   *             fStorage = childLM;
   *         }
   *
   *         fLocalMatrix = &fStorage;
   *     }
   * ```
   */
  public constructor(param0: KeyContextWithLocalMatrix) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContextWithLocalMatrix& operator=(const KeyContextWithLocalMatrix&) = delete
   * ```
   */
  private fun assign(param0: KeyContextWithLocalMatrix) {
    TODO("Implement assign")
  }
}
