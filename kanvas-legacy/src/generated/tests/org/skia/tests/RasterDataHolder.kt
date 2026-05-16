package org.skia.tests

import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct RasterDataHolder {
 *     RasterDataHolder() : fReleaseCount(0) {}
 *     sk_sp<SkData> fData;
 *     int fReleaseCount;
 *     static void Release(const void* pixels, void* context) {
 *         RasterDataHolder* self = static_cast<RasterDataHolder*>(context);
 *         self->fReleaseCount++;
 *         self->fData.reset();
 *     }
 * }
 * ```
 */
public data class RasterDataHolder public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  public var fData: SkSp<SkData>,
  /**
   * C++ original:
   * ```cpp
   * int fReleaseCount
   * ```
   */
  public var fReleaseCount: Int,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void Release(const void* pixels, void* context) {
     *         RasterDataHolder* self = static_cast<RasterDataHolder*>(context);
     *         self->fReleaseCount++;
     *         self->fData.reset();
     *     }
     * ```
     */
    public fun release(pixels: Unit?, context: Unit?) {
      TODO("Implement release")
    }
  }
}
