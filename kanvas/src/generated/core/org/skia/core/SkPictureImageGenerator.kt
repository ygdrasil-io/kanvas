package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.codec.Options
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkPictureImageGenerator : public SkImageGenerator {
 * public:
 *     SkPictureImageGenerator(const SkImageInfo&, sk_sp<SkPicture>, const SkMatrix*,
 *                             const SkPaint*, const SkSurfaceProps&);
 *
 * protected:
 *     bool onGetPixels(const SkImageInfo&, void* pixels, size_t rowBytes, const Options&) override;
 *
 * private:
 *     sk_sp<SkPicture>        fPicture;
 *     SkMatrix                fMatrix;
 *     std::optional<SkPaint>  fPaint;
 *     const SkSurfaceProps    fProps;
 *
 *     friend class SkImage_Picture;
 * }
 * ```
 */
public open class SkPictureImageGenerator public constructor(
  info: SkImageInfo,
  picture: SkSp<SkPicture>,
  matrix: SkMatrix?,
  paint: SkPaint?,
  props: SkSurfaceProps,
) : SkImageGenerator(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture>        fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix                fMatrix
   * ```
   */
  private var fMatrix: SkMatrix = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>  fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps    fProps
   * ```
   */
  private val fProps: SkSurfaceProps = TODO("Initialize fProps")

  /**
   * C++ original:
   * ```cpp
   * bool SkPictureImageGenerator::onGetPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
   *                                           const Options& opts) {
   *     std::unique_ptr<SkCanvas> canvas = SkCanvas::MakeRasterDirect(info, pixels, rowBytes, &fProps);
   *     if (!canvas) {
   *         return false;
   *     }
   *     canvas->clear(0);
   *     canvas->drawPicture(fPicture, &fMatrix, SkOptAddressOrNull(fPaint));
   *     return true;
   * }
   * ```
   */
  protected override fun onGetPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    opts: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }
}
