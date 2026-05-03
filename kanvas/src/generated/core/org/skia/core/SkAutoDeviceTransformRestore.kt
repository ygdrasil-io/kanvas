package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoDeviceTransformRestore : SkNoncopyable {
 * public:
 *     SkAutoDeviceTransformRestore(SkDevice* device, const SkM44& localToDevice)
 *         : fDevice(device)
 *         , fPrevLocalToDevice(device->localToDevice())
 *     {
 *         fDevice->setLocalToDevice(localToDevice);
 *     }
 *     ~SkAutoDeviceTransformRestore() {
 *         fDevice->setLocalToDevice(fPrevLocalToDevice);
 *     }
 *
 * private:
 *     SkDevice* fDevice;
 *     const SkM44   fPrevLocalToDevice;
 * }
 * ```
 */
public open class SkAutoDeviceTransformRestore public constructor(
  device: SkDevice?,
  localToDevice: SkM44,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkDevice* fDevice
   * ```
   */
  private var fDevice: SkDevice? = TODO("Initialize fDevice")

  /**
   * C++ original:
   * ```cpp
   * const SkM44   fPrevLocalToDevice
   * ```
   */
  private val fPrevLocalToDevice: SkM44 = TODO("Initialize fPrevLocalToDevice")
}
