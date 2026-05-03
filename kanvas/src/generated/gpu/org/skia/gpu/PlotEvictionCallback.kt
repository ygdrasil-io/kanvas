package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class PlotEvictionCallback {
 * public:
 *     virtual ~PlotEvictionCallback() = default;
 *     virtual void evict(PlotLocator) = 0;
 * }
 * ```
 */
public abstract class PlotEvictionCallback {
  /**
   * C++ original:
   * ```cpp
   * virtual void evict(PlotLocator) = 0
   * ```
   */
  public abstract fun evict(param0: PlotLocator)
}
