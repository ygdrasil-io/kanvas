package org.skia.modules

import kotlin.Float
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * template <class T>
 * class SK_API ExpressionEvaluator : public SkRefCnt {
 * public:
 *     // Evaluate the expression at the current time.
 *     virtual T evaluate(float t) = 0;
 * }
 * ```
 */
public abstract class ExpressionEvaluator<T> : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual T evaluate(float t) = 0
   * ```
   */
  public abstract fun evaluate(t: Float): T
}
