package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class BoundsManager {
 * public:
 *     virtual ~BoundsManager() {}
 *
 *     virtual CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const = 0;
 *
 *     virtual void recordDraw(const Rect& bounds, CompressedPaintersOrder order) = 0;
 *
 *     virtual void reset() = 0;
 * }
 * ```
 */
public abstract class BoundsManager {
  /**
   * C++ original:
   * ```cpp
   * virtual CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const = 0
   * ```
   */
  public abstract fun getMostRecentDraw(bounds: Rect): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void recordDraw(const Rect& bounds, CompressedPaintersOrder order) = 0
   * ```
   */
  public abstract fun recordDraw(bounds: Rect, order: CompressedPaintersOrder)

  /**
   * C++ original:
   * ```cpp
   * virtual void reset() = 0
   * ```
   */
  public abstract fun reset()
}
