package org.skia.tests

import org.skia.gpu.PlotEvictionCallback
import org.skia.gpu.PlotLocator

/**
 * C++ original:
 * ```cpp
 * class PlotEvictionCounter : public skgpu::PlotEvictionCallback {
 * public:
 *     void evict(skgpu::PlotLocator) override {
 *         ++gEvictCount;
 *     }
 * }
 * ```
 */
public open class PlotEvictionCounter : PlotEvictionCallback() {
  /**
   * C++ original:
   * ```cpp
   * void evict(skgpu::PlotLocator) override {
   *         ++gEvictCount;
   *     }
   * ```
   */
  public override fun evict(param0: PlotLocator) {
    TODO("Implement evict")
  }
}
