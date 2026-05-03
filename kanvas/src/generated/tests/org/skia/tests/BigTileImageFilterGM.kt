package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BigTileImageFilterGM : public GM {
 * public:
 *     BigTileImageFilterGM() {
 *         this->setBGColor(0xFF000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("bigtileimagefilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onOnceBeforeDraw() override {
 *         fRedImage = create_circle_texture(kBitmapSize, SK_ColorRED);
 *         fGreenImage = create_circle_texture(kBitmapSize, SK_ColorGREEN);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *
 *         {
 *             SkPaint p;
 *
 *             const SkRect bound = SkRect::MakeIWH(kWidth, kHeight);
 *             sk_sp<SkImageFilter> imageSource(SkImageFilters::Image(fRedImage,
 *                                                                    SkFilterMode::kLinear));
 *
 *             sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
 *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize), SkRect::MakeIWH(kWidth, kHeight),
 *                     std::move(imageSource)));
 *
 *             p.setImageFilter(std::move(tif));
 *
 *             canvas->saveLayer(&bound, &p);
 *             canvas->restore();
 *         }
 *
 *         {
 *             SkPaint p2;
 *
 *             const SkRect bound2 = SkRect::MakeIWH(kBitmapSize, kBitmapSize);
 *
 *             sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
 *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize),
 *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize),
 *                     nullptr));
 *
 *             p2.setImageFilter(std::move(tif));
 *
 *             canvas->translate(320, 320);
 *             canvas->saveLayer(&bound2, &p2);
 *             canvas->setMatrix(SkMatrix::I());
 *
 *             SkRect bound3 = SkRect::MakeXYWH(320, 320,
 *                                              SkIntToScalar(kBitmapSize),
 *                                              SkIntToScalar(kBitmapSize));
 *             canvas->drawImageRect(fGreenImage.get(), bound2, bound3, SkSamplingOptions(), nullptr,
 *                                   SkCanvas::kStrict_SrcRectConstraint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kWidth = 512;
 *     inline static constexpr int kHeight = 512;
 *     inline static constexpr int kBitmapSize = 64;
 *
 *     sk_sp<SkImage> fRedImage;
 *     sk_sp<SkImage> fGreenImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BigTileImageFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kWidth = 512
   * ```
   */
  private var fRedImage: SkSp<SkImage> = TODO("Initialize fRedImage")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kHeight = 512
   * ```
   */
  private var fGreenImage: SkSp<SkImage> = TODO("Initialize fGreenImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bigtileimagefilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fRedImage = create_circle_texture(kBitmapSize, SK_ColorRED);
   *         fGreenImage = create_circle_texture(kBitmapSize, SK_ColorGREEN);
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
   *         canvas->clear(SK_ColorBLACK);
   *
   *         {
   *             SkPaint p;
   *
   *             const SkRect bound = SkRect::MakeIWH(kWidth, kHeight);
   *             sk_sp<SkImageFilter> imageSource(SkImageFilters::Image(fRedImage,
   *                                                                    SkFilterMode::kLinear));
   *
   *             sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
   *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize), SkRect::MakeIWH(kWidth, kHeight),
   *                     std::move(imageSource)));
   *
   *             p.setImageFilter(std::move(tif));
   *
   *             canvas->saveLayer(&bound, &p);
   *             canvas->restore();
   *         }
   *
   *         {
   *             SkPaint p2;
   *
   *             const SkRect bound2 = SkRect::MakeIWH(kBitmapSize, kBitmapSize);
   *
   *             sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
   *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize),
   *                     SkRect::MakeIWH(kBitmapSize, kBitmapSize),
   *                     nullptr));
   *
   *             p2.setImageFilter(std::move(tif));
   *
   *             canvas->translate(320, 320);
   *             canvas->saveLayer(&bound2, &p2);
   *             canvas->setMatrix(SkMatrix::I());
   *
   *             SkRect bound3 = SkRect::MakeXYWH(320, 320,
   *                                              SkIntToScalar(kBitmapSize),
   *                                              SkIntToScalar(kBitmapSize));
   *             canvas->drawImageRect(fGreenImage.get(), bound2, bound3, SkSamplingOptions(), nullptr,
   *                                   SkCanvas::kStrict_SrcRectConstraint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")

    private val kBitmapSize: Int = TODO("Initialize kBitmapSize")
  }
}
