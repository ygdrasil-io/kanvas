package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkPathMeasurePriv {
 * public:
 *     static size_t CountSegments(const SkPathMeasure&);
 * }
 * ```
 */
public open class SkPathMeasurePriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * size_t SkPathMeasurePriv::CountSegments(const SkPathMeasure& meas) {
     *     if (auto cntr = meas.currentMeasure()) {
     *         return cntr->fSegments.size();
     *     }
     *     return 0;
     * }
     * ```
     */
    public fun countSegments(meas: SkPathMeasure): Int {
      TODO("Implement countSegments")
    }
  }
}
