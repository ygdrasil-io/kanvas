package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class PictureImageFilterGM : public skiagm::GM {
 * public:
 *     PictureImageFilterGM() { }
 *
 * protected:
 *     SkString getName() const override { return SkString("pictureimagefilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(600, 300); }
 *
 *     void onOnceBeforeDraw() override {
 *         fPicture = make_picture();
 *         fLCDPicture = make_LCD_picture();
 *     }
 *
 *     sk_sp<SkImageFilter> make(sk_sp<SkPicture> pic, SkRect r, const SkSamplingOptions& sampling) {
 *         SkISize dim = { SkScalarRoundToInt(r.width()), SkScalarRoundToInt(r.height()) };
 *         auto img = SkImages::DeferredFromPicture(
 *                 pic, dim, nullptr, nullptr, SkImages::BitDepth::kU8, SkColorSpace::MakeSRGB());
 *         return SkImageFilters::Image(img, r, r, sampling);
 *     }
 *     sk_sp<SkImageFilter> make(const SkSamplingOptions& sampling) {
 *         return make(fPicture, fPicture->cullRect(), sampling);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorGRAY);
 *         {
 *             SkRect srcRect = SkRect::MakeXYWH(20, 20, 30, 30);
 *             SkRect emptyRect = SkRect::MakeXYWH(20, 20, 0, 0);
 *             SkRect bounds = SkRect::MakeXYWH(0, 0, 100, 100);
 *             sk_sp<SkImageFilter> pictureSource(SkImageFilters::Picture(fPicture));
 *             sk_sp<SkImageFilter> pictureSourceSrcRect(SkImageFilters::Picture(fPicture, srcRect));
 *             sk_sp<SkImageFilter> pictureSourceEmptyRect(SkImageFilters::Picture(fPicture,
 *                                                                                 emptyRect));
 *             sk_sp<SkImageFilter> pictureSourceResampled = make(SkSamplingOptions(SkFilterMode::kLinear));
 *             sk_sp<SkImageFilter> pictureSourcePixelated = make(SkSamplingOptions());
 *
 *             canvas->save();
 *             // Draw the picture unscaled.
 *             fill_rect_filtered(canvas, bounds, pictureSource);
 *             canvas->translate(SkIntToScalar(100), 0);
 *
 *             // Draw an unscaled subset of the source picture.
 *             fill_rect_filtered(canvas, bounds, pictureSourceSrcRect);
 *             canvas->translate(SkIntToScalar(100), 0);
 *
 *             // Draw the picture to an empty rect (should draw nothing).
 *             fill_rect_filtered(canvas, bounds, pictureSourceEmptyRect);
 *             canvas->translate(SkIntToScalar(100), 0);
 *
 *             // Draw the LCD picture to a layer
 *             {
 *                 SkPaint stroke;
 *                 stroke.setStyle(SkPaint::kStroke_Style);
 *
 *                 canvas->drawRect(bounds, stroke);
 *
 *                 SkPaint paint;
 *                 paint.setImageFilter(make(fLCDPicture, fPicture->cullRect(), SkSamplingOptions()));
 *
 *                 canvas->scale(4, 4);
 *                 canvas->translate(-0.9f*srcRect.fLeft, -2.45f*srcRect.fTop);
 *
 *                 canvas->saveLayer(&bounds, &paint);
 *                 canvas->restore();
 *             }
 *
 *             canvas->restore();
 *
 *             // Draw the picture scaled
 *             canvas->translate(0, SkIntToScalar(100));
 *             canvas->scale(200 / srcRect.width(), 200 / srcRect.height());
 *             canvas->translate(-srcRect.fLeft, -srcRect.fTop);
 *             fill_rect_filtered(canvas, srcRect, pictureSource);
 *
 *             // Draw the picture scaled, but rasterized at original resolution
 *             canvas->translate(srcRect.width(), 0);
 *             fill_rect_filtered(canvas, srcRect, pictureSourceResampled);
 *
 *             // Draw the picture scaled, pixelated
 *             canvas->translate(srcRect.width(), 0);
 *             fill_rect_filtered(canvas, srcRect, pictureSourcePixelated);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkPicture> fPicture;
 *     sk_sp<SkPicture> fLCDPicture;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PictureImageFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fLCDPicture
   * ```
   */
  private var fLCDPicture: SkSp<SkPicture> = TODO("Initialize fLCDPicture")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pictureimagefilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(600, 300); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fPicture = make_picture();
   *         fLCDPicture = make_LCD_picture();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> make(sk_sp<SkPicture> pic, SkRect r, const SkSamplingOptions& sampling) {
   *         SkISize dim = { SkScalarRoundToInt(r.width()), SkScalarRoundToInt(r.height()) };
   *         auto img = SkImages::DeferredFromPicture(
   *                 pic, dim, nullptr, nullptr, SkImages::BitDepth::kU8, SkColorSpace::MakeSRGB());
   *         return SkImageFilters::Image(img, r, r, sampling);
   *     }
   * ```
   */
  protected fun make(
    pic: SkSp<SkPicture>,
    r: SkRect,
    sampling: SkSamplingOptions,
  ): SkSp<SkImageFilter> {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> make(const SkSamplingOptions& sampling) {
   *         return make(fPicture, fPicture->cullRect(), sampling);
   *     }
   * ```
   */
  protected fun make(sampling: SkSamplingOptions): SkSp<SkImageFilter> {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorGRAY);
   *         {
   *             SkRect srcRect = SkRect::MakeXYWH(20, 20, 30, 30);
   *             SkRect emptyRect = SkRect::MakeXYWH(20, 20, 0, 0);
   *             SkRect bounds = SkRect::MakeXYWH(0, 0, 100, 100);
   *             sk_sp<SkImageFilter> pictureSource(SkImageFilters::Picture(fPicture));
   *             sk_sp<SkImageFilter> pictureSourceSrcRect(SkImageFilters::Picture(fPicture, srcRect));
   *             sk_sp<SkImageFilter> pictureSourceEmptyRect(SkImageFilters::Picture(fPicture,
   *                                                                                 emptyRect));
   *             sk_sp<SkImageFilter> pictureSourceResampled = make(SkSamplingOptions(SkFilterMode::kLinear));
   *             sk_sp<SkImageFilter> pictureSourcePixelated = make(SkSamplingOptions());
   *
   *             canvas->save();
   *             // Draw the picture unscaled.
   *             fill_rect_filtered(canvas, bounds, pictureSource);
   *             canvas->translate(SkIntToScalar(100), 0);
   *
   *             // Draw an unscaled subset of the source picture.
   *             fill_rect_filtered(canvas, bounds, pictureSourceSrcRect);
   *             canvas->translate(SkIntToScalar(100), 0);
   *
   *             // Draw the picture to an empty rect (should draw nothing).
   *             fill_rect_filtered(canvas, bounds, pictureSourceEmptyRect);
   *             canvas->translate(SkIntToScalar(100), 0);
   *
   *             // Draw the LCD picture to a layer
   *             {
   *                 SkPaint stroke;
   *                 stroke.setStyle(SkPaint::kStroke_Style);
   *
   *                 canvas->drawRect(bounds, stroke);
   *
   *                 SkPaint paint;
   *                 paint.setImageFilter(make(fLCDPicture, fPicture->cullRect(), SkSamplingOptions()));
   *
   *                 canvas->scale(4, 4);
   *                 canvas->translate(-0.9f*srcRect.fLeft, -2.45f*srcRect.fTop);
   *
   *                 canvas->saveLayer(&bounds, &paint);
   *                 canvas->restore();
   *             }
   *
   *             canvas->restore();
   *
   *             // Draw the picture scaled
   *             canvas->translate(0, SkIntToScalar(100));
   *             canvas->scale(200 / srcRect.width(), 200 / srcRect.height());
   *             canvas->translate(-srcRect.fLeft, -srcRect.fTop);
   *             fill_rect_filtered(canvas, srcRect, pictureSource);
   *
   *             // Draw the picture scaled, but rasterized at original resolution
   *             canvas->translate(srcRect.width(), 0);
   *             fill_rect_filtered(canvas, srcRect, pictureSourceResampled);
   *
   *             // Draw the picture scaled, pixelated
   *             canvas->translate(srcRect.width(), 0);
   *             fill_rect_filtered(canvas, srcRect, pictureSourcePixelated);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
