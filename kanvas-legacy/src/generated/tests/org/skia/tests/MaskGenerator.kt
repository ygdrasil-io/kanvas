package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.codec.Options
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo

/**
 * C++ original:
 * ```cpp
 * class MaskGenerator final : public SkImageGenerator {
 * public:
 *     MaskGenerator(const SkImageInfo& info) : INHERITED(info) {}
 *
 *     bool onGetPixels(const SkImageInfo& info, void* pixels,
 *                      size_t rowBytes, const Options&) override {
 *         SkImageInfo surfaceInfo = info;
 *         if (kAlpha_8_SkColorType == info.colorType()) {
 *             surfaceInfo = surfaceInfo.makeColorSpace(nullptr);
 *         }
 *
 *         make_mask(SkSurfaces::WrapPixels(surfaceInfo, pixels, rowBytes));
 *         return true;
 *     }
 *
 * private:
 *     using INHERITED = SkImageGenerator;
 * }
 * ```
 */
public class MaskGenerator public constructor(
  info: SkImageInfo,
) : SkImageGenerator(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool onGetPixels(const SkImageInfo& info, void* pixels,
   *                      size_t rowBytes, const Options&) override {
   *         SkImageInfo surfaceInfo = info;
   *         if (kAlpha_8_SkColorType == info.colorType()) {
   *             surfaceInfo = surfaceInfo.makeColorSpace(nullptr);
   *         }
   *
   *         make_mask(SkSurfaces::WrapPixels(surfaceInfo, pixels, rowBytes));
   *         return true;
   *     }
   * ```
   */
  public override fun onGetPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    param3: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }
}
