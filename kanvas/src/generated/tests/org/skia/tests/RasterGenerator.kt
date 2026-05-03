package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.codec.Options
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo

/**
 * C++ original:
 * ```cpp
 * class RasterGenerator : public SkImageGenerator {
 * public:
 *     RasterGenerator(const SkBitmap& bm) : SkImageGenerator(bm.info()), fBM(bm)
 *     {}
 *
 * protected:
 *     bool onGetPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
 *                      const Options&) override {
 *         SkASSERT(fBM.width() == info.width());
 *         SkASSERT(fBM.height() == info.height());
 *         return fBM.readPixels(info, pixels, rowBytes, 0, 0);
 *     }
 * private:
 *     SkBitmap fBM;
 * }
 * ```
 */
public open class RasterGenerator public constructor(
  bm: SkBitmap,
) : SkImageGenerator(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBM
   * ```
   */
  private var fBM: SkBitmap = TODO("Initialize fBM")

  /**
   * C++ original:
   * ```cpp
   * bool onGetPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
   *                      const Options&) override {
   *         SkASSERT(fBM.width() == info.width());
   *         SkASSERT(fBM.height() == info.height());
   *         return fBM.readPixels(info, pixels, rowBytes, 0, 0);
   *     }
   * ```
   */
  protected override fun onGetPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    param3: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }
}
