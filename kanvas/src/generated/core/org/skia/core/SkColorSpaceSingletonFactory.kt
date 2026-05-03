package org.skia.core

import org.skia.modules.SkcmsMatrix3x3
import org.skia.modules.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * class SkColorSpaceSingletonFactory {
 * public:
 *     static SkColorSpace* Make(const skcms_TransferFunction& transferFn,
 *                               const skcms_Matrix3x3& to_xyz) {
 *         return new SkColorSpace(transferFn, to_xyz);
 *     }
 * }
 * ```
 */
public open class SkColorSpaceSingletonFactory {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkColorSpace* Make(const skcms_TransferFunction& transferFn,
     *                               const skcms_Matrix3x3& to_xyz) {
     *         return new SkColorSpace(transferFn, to_xyz);
     *     }
     * ```
     */
    public fun make(transferFn: SkcmsTransferFunction, toXyz: SkcmsMatrix3x3): SkColorSpace {
      TODO("Implement make")
    }
  }
}
