package org.skia.core

import org.skia.foundation.SkPixmap

/**
 * C++ original:
 * ```cpp
 * class SkRasterBlitter : public SkBlitter {
 * public:
 *     SkRasterBlitter(const SkPixmap& device) : fDevice(device) {}
 *
 * protected:
 *     const SkPixmap fDevice;
 * }
 * ```
 */
public open class SkRasterBlitter public constructor(
  device: SkPixmap,
) : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * const SkPixmap fDevice
   * ```
   */
  protected val fDevice: SkPixmap = TODO("Initialize fDevice")
}
