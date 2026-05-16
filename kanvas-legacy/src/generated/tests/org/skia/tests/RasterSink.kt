package org.skia.tests

import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkSp
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class RasterSink : public Sink {
 * public:
 *     explicit RasterSink(SkColorType);
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "png"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kRaster, SinkFlags::kDirect }; }
 *     void setColorSpace(sk_sp<SkColorSpace> colorSpace) override { fColorSpace = colorSpace; }
 *
 *     SkColorInfo colorInfo() const override {
 *         // If there's an appropriate alpha type for this color type, use it, otherwise use premul.
 *         SkAlphaType alphaType = kPremul_SkAlphaType;
 *         (void)SkColorTypeValidateAlphaType(fColorType, alphaType, &alphaType);
 *
 *         return SkColorInfo(fColorType, alphaType, fColorSpace);
 *     }
 *
 * private:
 *     SkColorType         fColorType;
 *     sk_sp<SkColorSpace> fColorSpace;
 * }
 * ```
 */
public open class RasterSink public constructor(
  colorType: SkColorType,
) : Sink() {
  /**
   * C++ original:
   * ```cpp
   * SkColorType         fColorType
   * ```
   */
  private var fColorType: Int = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fColorSpace
   * ```
   */
  private var fColorSpace: Int = TODO("Initialize fColorSpace")

  /**
   * C++ original:
   * ```cpp
   * Result RasterSink::draw(const Src& src, SkBitmap* dst, SkWStream*, SkString*) const {
   *     const SkISize size = src.size();
   *     if (size.isEmpty()) {
   *         return Result(Result::Status::Skip,
   *                       SkStringPrintf("Skipping empty source: %s", src.name().c_str()));
   *     }
   *
   *     dst->allocPixelsFlags(SkImageInfo::Make(size, this->colorInfo()),
   *                           SkBitmap::kZeroPixels_AllocFlag);
   *
   *     SkSurfaceProps props(/*flags=*/0, kRGB_H_SkPixelGeometry);
   *     src.modifySurfaceProps(&props);
   *     auto surface = SkSurfaces::WrapPixels(dst->pixmap(), &props);
   *     return src.draw(surface->getCanvas(), /*GraphiteTestContext=*/nullptr);
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    dst: SkBitmap?,
    param2: SkWStream?,
    param3: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override { return "png"; }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * SinkFlags flags() const override { return SinkFlags{ SinkFlags::kRaster, SinkFlags::kDirect }; }
   * ```
   */
  public override fun flags(): SinkFlags {
    TODO("Implement flags")
  }

  /**
   * C++ original:
   * ```cpp
   * void setColorSpace(sk_sp<SkColorSpace> colorSpace) override { fColorSpace = colorSpace; }
   * ```
   */
  public override fun setColorSpace(colorSpace: SkSp<SkColorSpace>) {
    TODO("Implement setColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo colorInfo() const override {
   *         // If there's an appropriate alpha type for this color type, use it, otherwise use premul.
   *         SkAlphaType alphaType = kPremul_SkAlphaType;
   *         (void)SkColorTypeValidateAlphaType(fColorType, alphaType, &alphaType);
   *
   *         return SkColorInfo(fColorType, alphaType, fColorSpace);
   *     }
   * ```
   */
  public override fun colorInfo(): Int {
    TODO("Implement colorInfo")
  }
}
