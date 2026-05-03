package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageSourceGM : public GM {
 * public:
 *     ImageSourceGM(const char* suffix, const SkSamplingOptions& sampling)
 *         : fSuffix(suffix), fSampling(sampling) {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("imagesrc2_");
 *         name.append(fSuffix);
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(256, 256); }
 *
 *     // Create an image with high frequency vertical stripes
 *     void onOnceBeforeDraw() override {
 *         constexpr SkPMColor gColors[] = {
 *             SK_ColorRED,     SK_ColorGRAY,
 *             SK_ColorGREEN,   SK_ColorGRAY,
 *             SK_ColorBLUE,    SK_ColorGRAY,
 *             SK_ColorCYAN,    SK_ColorGRAY,
 *             SK_ColorMAGENTA, SK_ColorGRAY,
 *             SK_ColorYELLOW,  SK_ColorGRAY,
 *             SK_ColorWHITE,   SK_ColorGRAY,
 *         };
 *
 *         auto surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(kImageSize, kImageSize)));
 *         SkCanvas* canvas = surface->getCanvas();
 *
 *         int curColor = 0;
 *
 *         for (int x = 0; x < kImageSize; x += 3) {
 *             SkRect r = SkRect::MakeXYWH(SkIntToScalar(x), SkIntToScalar(0),
 *                                         SkIntToScalar(3), SkIntToScalar(kImageSize));
 *             SkPaint p;
 *             p.setColor(gColors[curColor]);
 *             canvas->drawRect(r, p);
 *
 *             curColor = (curColor+1) % std::size(gColors);
 *         }
 *
 *         fImage = surface->makeImageSnapshot();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkRect srcRect = SkRect::MakeLTRB(0, 0,
 *                                                 SkIntToScalar(kImageSize),
 *                                                 SkIntToScalar(kImageSize));
 *         const SkRect dstRect = SkRect::MakeLTRB(0.75f, 0.75f, 225.75f, 225.75f);
 *
 *         SkPaint p;
 *         p.setImageFilter(SkImageFilters::Image(fImage, srcRect, dstRect, fSampling));
 *
 *         canvas->saveLayer(nullptr, &p);
 *         canvas->restore();
 *     }
 *
 * private:
 *     inline static constexpr int kImageSize = 503;
 *
 *     SkString          fSuffix;
 *     SkSamplingOptions fSampling;
 *     sk_sp<SkImage>    fImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageSourceGM public constructor(
  suffix: String?,
  sampling: SkSamplingOptions,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageSize = 503
   * ```
   */
  private var fSuffix: String = TODO("Initialize fSuffix")

  /**
   * C++ original:
   * ```cpp
   * SkString          fSuffix
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions fSampling
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("imagesrc2_");
   *         name.append(fSuffix);
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(256, 256); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         constexpr SkPMColor gColors[] = {
   *             SK_ColorRED,     SK_ColorGRAY,
   *             SK_ColorGREEN,   SK_ColorGRAY,
   *             SK_ColorBLUE,    SK_ColorGRAY,
   *             SK_ColorCYAN,    SK_ColorGRAY,
   *             SK_ColorMAGENTA, SK_ColorGRAY,
   *             SK_ColorYELLOW,  SK_ColorGRAY,
   *             SK_ColorWHITE,   SK_ColorGRAY,
   *         };
   *
   *         auto surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(kImageSize, kImageSize)));
   *         SkCanvas* canvas = surface->getCanvas();
   *
   *         int curColor = 0;
   *
   *         for (int x = 0; x < kImageSize; x += 3) {
   *             SkRect r = SkRect::MakeXYWH(SkIntToScalar(x), SkIntToScalar(0),
   *                                         SkIntToScalar(3), SkIntToScalar(kImageSize));
   *             SkPaint p;
   *             p.setColor(gColors[curColor]);
   *             canvas->drawRect(r, p);
   *
   *             curColor = (curColor+1) % std::size(gColors);
   *         }
   *
   *         fImage = surface->makeImageSnapshot();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkRect srcRect = SkRect::MakeLTRB(0, 0,
   *                                                 SkIntToScalar(kImageSize),
   *                                                 SkIntToScalar(kImageSize));
   *         const SkRect dstRect = SkRect::MakeLTRB(0.75f, 0.75f, 225.75f, 225.75f);
   *
   *         SkPaint p;
   *         p.setImageFilter(SkImageFilters::Image(fImage, srcRect, dstRect, fSampling));
   *
   *         canvas->saveLayer(nullptr, &p);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kImageSize: Int = TODO("Initialize kImageSize")
  }
}
