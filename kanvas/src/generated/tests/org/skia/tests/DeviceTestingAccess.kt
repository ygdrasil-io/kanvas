package org.skia.tests

import org.skia.core.SkDevice
import org.skia.core.SkSpecialImage
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DeviceTestingAccess {
 * public:
 *     static sk_sp<SkSpecialImage> SnapSpecial(SkDevice* dev) {
 *         return dev->snapSpecial();
 *     }
 * }
 * ```
 */
public open class DeviceTestingAccess {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSpecialImage> SnapSpecial(SkDevice* dev) {
     *         return dev->snapSpecial();
     *     }
     * ```
     */
    public fun snapSpecial(dev: SkDevice?): SkSp<SkSpecialImage> {
      TODO("Implement snapSpecial")
    }
  }
}
