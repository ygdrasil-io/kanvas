package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class ResizeGM : public GM {
 * public:
 *     ResizeGM() {
 *         this->setBGColor(0x00000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("resizeimagefilter"); }
 *
 *     void draw(SkCanvas* canvas,
 *               const SkRect& rect,
 *               const SkSize& deviceSize,
 *               const SkSamplingOptions& sampling,
 *               sk_sp<SkImageFilter> input) {
 *         SkRect dstRect;
 *         canvas->getLocalToDeviceAs3x3().mapRect(&dstRect, rect);
 *         canvas->save();
 *         SkScalar deviceScaleX = deviceSize.width() / dstRect.width();
 *         SkScalar deviceScaleY = deviceSize.height() / dstRect.height();
 *         canvas->translate(rect.x(), rect.y());
 *         canvas->scale(deviceScaleX, deviceScaleY);
 *         canvas->translate(-rect.x(), -rect.y());
 *         SkMatrix matrix;
 *         matrix.setScale(SkScalarInvert(deviceScaleX), SkScalarInvert(deviceScaleY));
 *         sk_sp<SkImageFilter> filter(SkImageFilters::MatrixTransform(matrix,
 *                                                                     sampling,
 *                                                                     std::move(input)));
 *         SkPaint filteredPaint;
 *         filteredPaint.setImageFilter(std::move(filter));
 *         canvas->saveLayer(&rect, &filteredPaint);
 *         SkPaint paint;
 *         paint.setColor(0xFF00FF00);
 *         SkRect ovalRect = rect;
 *         ovalRect.inset(SkIntToScalar(4), SkIntToScalar(4));
 *         canvas->drawOval(ovalRect, paint);
 *         canvas->restore(); // for saveLayer
 *         canvas->restore();
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(630, 100); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *
 *         const SkSamplingOptions samplings[] = {
 *             SkSamplingOptions(),
 *             SkSamplingOptions(SkFilterMode::kLinear),
 *             SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear),
 *             SkSamplingOptions(SkCubicResampler::Mitchell()),
 *             SkSamplingOptions::Aniso(16),
 *         };
 *         const SkRect srcRect = SkRect::MakeWH(96, 96);
 *         const SkSize deviceSize = SkSize::Make(16, 16);
 *
 *         for (const auto& sampling : samplings) {
 *             this->draw(canvas, srcRect, deviceSize, sampling, nullptr);
 *             canvas->translate(srcRect.width() + SkIntToScalar(10), 0);
 *         }
 *
 *         {
 *             sk_sp<SkSurface> surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(16, 16)));
 *             SkCanvas* surfaceCanvas = surface->getCanvas();
 *             surfaceCanvas->clear(0x000000);
 *             {
 *                 SkPaint paint;
 *                 paint.setColor(0xFF00FF00);
 *                 SkRect ovalRect = SkRect::MakeWH(16, 16);
 *                 ovalRect.inset(SkIntToScalar(2)/3, SkIntToScalar(2)/3);
 *                 surfaceCanvas->drawOval(ovalRect, paint);
 *             }
 *             sk_sp<SkImage> image(surface->makeImageSnapshot());
 *             SkRect inRect = SkRect::MakeXYWH(-4, -4, 20, 20);
 *             SkRect outRect = SkRect::MakeXYWH(-24, -24, 120, 120);
 *             sk_sp<SkImageFilter> source(
 *                 SkImageFilters::Image(std::move(image), inRect, outRect,
 *                                       SkSamplingOptions({1/3.0f, 1/3.0f})));
 *             this->draw(canvas, srcRect, deviceSize, samplings[3], std::move(source));
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ResizeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("resizeimagefilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas,
   *               const SkRect& rect,
   *               const SkSize& deviceSize,
   *               const SkSamplingOptions& sampling,
   *               sk_sp<SkImageFilter> input) {
   *         SkRect dstRect;
   *         canvas->getLocalToDeviceAs3x3().mapRect(&dstRect, rect);
   *         canvas->save();
   *         SkScalar deviceScaleX = deviceSize.width() / dstRect.width();
   *         SkScalar deviceScaleY = deviceSize.height() / dstRect.height();
   *         canvas->translate(rect.x(), rect.y());
   *         canvas->scale(deviceScaleX, deviceScaleY);
   *         canvas->translate(-rect.x(), -rect.y());
   *         SkMatrix matrix;
   *         matrix.setScale(SkScalarInvert(deviceScaleX), SkScalarInvert(deviceScaleY));
   *         sk_sp<SkImageFilter> filter(SkImageFilters::MatrixTransform(matrix,
   *                                                                     sampling,
   *                                                                     std::move(input)));
   *         SkPaint filteredPaint;
   *         filteredPaint.setImageFilter(std::move(filter));
   *         canvas->saveLayer(&rect, &filteredPaint);
   *         SkPaint paint;
   *         paint.setColor(0xFF00FF00);
   *         SkRect ovalRect = rect;
   *         ovalRect.inset(SkIntToScalar(4), SkIntToScalar(4));
   *         canvas->drawOval(ovalRect, paint);
   *         canvas->restore(); // for saveLayer
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun draw(
    canvas: SkCanvas?,
    rect: SkRect,
    deviceSize: SkSize,
    sampling: SkSamplingOptions,
    input: SkSp<SkImageFilter>,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(630, 100); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorBLACK);
   *
   *         const SkSamplingOptions samplings[] = {
   *             SkSamplingOptions(),
   *             SkSamplingOptions(SkFilterMode::kLinear),
   *             SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear),
   *             SkSamplingOptions(SkCubicResampler::Mitchell()),
   *             SkSamplingOptions::Aniso(16),
   *         };
   *         const SkRect srcRect = SkRect::MakeWH(96, 96);
   *         const SkSize deviceSize = SkSize::Make(16, 16);
   *
   *         for (const auto& sampling : samplings) {
   *             this->draw(canvas, srcRect, deviceSize, sampling, nullptr);
   *             canvas->translate(srcRect.width() + SkIntToScalar(10), 0);
   *         }
   *
   *         {
   *             sk_sp<SkSurface> surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(16, 16)));
   *             SkCanvas* surfaceCanvas = surface->getCanvas();
   *             surfaceCanvas->clear(0x000000);
   *             {
   *                 SkPaint paint;
   *                 paint.setColor(0xFF00FF00);
   *                 SkRect ovalRect = SkRect::MakeWH(16, 16);
   *                 ovalRect.inset(SkIntToScalar(2)/3, SkIntToScalar(2)/3);
   *                 surfaceCanvas->drawOval(ovalRect, paint);
   *             }
   *             sk_sp<SkImage> image(surface->makeImageSnapshot());
   *             SkRect inRect = SkRect::MakeXYWH(-4, -4, 20, 20);
   *             SkRect outRect = SkRect::MakeXYWH(-24, -24, 120, 120);
   *             sk_sp<SkImageFilter> source(
   *                 SkImageFilters::Image(std::move(image), inRect, outRect,
   *                                       SkSamplingOptions({1/3.0f, 1/3.0f})));
   *             this->draw(canvas, srcRect, deviceSize, samplings[3], std::move(source));
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
