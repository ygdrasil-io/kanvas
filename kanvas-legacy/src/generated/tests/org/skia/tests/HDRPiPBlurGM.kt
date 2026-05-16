package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HDRPiPBlurGM : public skiagm::GM {
 *     // This GM is fragment bound, so when benchmarking, use a large texture size. Use a smaller
 *     // size for DM to reduce device load.
 *     static constexpr SkISize kFullSize{2560, 1440};
 *     static constexpr SkISize kNonBenchSize{640, 360};
 * public:
 *     HDRPiPBlurGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("hdr-pip-blur"); }
 *
 *     SkISize getISize() override {
 *         if (this->getMode() == kBench_Mode) {
 *             return kFullSize;
 *         } else {
 *             return kNonBenchSize;
 *         }
 *     }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         // When running in DM, all offscreen passes and the final rendering will be small.
 *         SkIRect screenBounds = SkIRect::MakeSize(this->getMode() == kGM_Mode ? kNonBenchSize
 *                                                                              : kFullSize);
 *
 *         sk_sp<SkImage> input;
 *         {
 *             // The main surface is RGBA8 but with a wider gamut sRGB colorspace.
 *             sk_sp<SkSurface> content = canvas->makeSurface(
 *                     canvas->imageInfo().makeWH(screenBounds.width(), screenBounds.height())
 *                                        .makeColorType(kRGBA_8888_SkColorType)
 *                                        .makeColorSpace(
 *                                                 SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020,
 *                                                                       SkNamedGamut::kDisplayP3)));
 *             if (!content) {
 *                 // This occurs in DDL dm configs
 *                 *errorMsg = "Could not create offscreen surface";
 *                 return DrawResult::kSkip;
 *             }
 *
 *             SkCanvas* c = content->getCanvas();
 *             c->clear(SkColors::kDkGray);
 *
 *             SkMatrix toScreenBounds = SkMatrix::RectToRectOrIdentity(SkRect::Make(kFullSize),
 *                                                                      SkRect::Make(screenBounds));
 *             c->concat(toScreenBounds);
 *
 *             // Now render everything to `c` as if it were a kFullSize image.
 *             c->drawImageRect(fBackgroundImage.get(),
 *                              SkRect::Make(fBackgroundImage->bounds()),
 *                              SkRect::Make(kFullSize),
 *                              SkFilterMode::kLinear,
 *                              &fPaint,
 *                              SkCanvas::kFast_SrcRectConstraint);
 *
 *             SkRRect pip = SkRRect::MakeRectXY(SkRect::MakeXYWH(1500.f, 700.f, 800.f, 600.f),
 *                                               20.f, 20.f);
 *             c->drawRRect(pip, fPaint);
 *             c->save();
 *                 c->clipRRect(pip, true);
 *                 c->drawImageRect(fPiPImage.get(),
 *                                  SkRect::MakeIWH(fPiPImage->width(), fPiPImage->height()),
 *                                  pip.rect(),
 *                                  SkFilterMode::kLinear,
 *                                  &fPaint,
 *                                  SkCanvas::kFast_SrcRectConstraint);
 *             c->restore();
 *             input = content->makeTemporaryImage();
 *         }
 *
 *         canvas->save();
 *
 *         if (this->getMode() == kSample_Mode) {
 *             // For viewer, the offscreen passes operate at full resolution, but we draw smaller to
 *             // fit into the window. This lets overall frame times match nanobench, but it looks like
 *             // what dm produces.
 *             SkMatrix toViewBounds = SkMatrix::RectToRectOrIdentity(SkRect::Make(screenBounds),
 *                                                                    SkRect::Make(kNonBenchSize));
 *             canvas->concat(toViewBounds);
 *         }
 *
 *         canvas->clipIRect(screenBounds);
 *         canvas->drawImage(input.get(), 0.f, 0.f, SkFilterMode::kLinear, &fPaint);
 *
 *         // Explicitly blur the input image
 *         // NOTE: Skip calling MakeWithFilter and comment out the last `drawImageRect` call to
 *         // disable the shade blur.
 *         SkIRect outSubset;
 *         SkIPoint outOffset;
 *         sk_sp<SkImage> blur;
 * #if defined(SK_GRAPHITE)
 *         if (canvas->recorder()) {
 *             blur = SkImages::MakeWithFilter(canvas->recorder(),
 *                                             input,
 *                                             fShadeBlur.get(),
 *                                             screenBounds, screenBounds,
 *                                             &outSubset, &outOffset);
 *         } else
 * #endif
 * #if defined(SK_GANESH)
 *         if (canvas->recordingContext()) {
 *             blur = SkImages::MakeWithFilter(canvas->recordingContext(),
 *                                             input,
 *                                             fShadeBlur.get(),
 *                                             screenBounds, screenBounds,
 *                                             &outSubset, &outOffset);
 *         } else
 * #endif
 *         {
 *             blur = SkImages::MakeWithFilter(input,
 *                                             fShadeBlur.get(),
 *                                             screenBounds, screenBounds,
 *                                             &outSubset, &outOffset);
 *         }
 *
 *         SkPaint fadedBlur;
 *         fadedBlur.setAlphaf(0.9f);
 *         canvas->drawImageRect(blur.get(),
 *                               SkRect::Make(outSubset),
 *                               SkRect::MakeXYWH(outOffset.x(), outOffset.y(),
 *                                                outSubset.width(), outSubset.height()),
 *                               SkFilterMode::kLinear,
 *                               &fadedBlur,
 *                               SkCanvas::kFast_SrcRectConstraint);
 *
 *         canvas->restore();
 *         return DrawResult::kOk;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fPaint.setColor4f({ 0.0f, 0.0f, 0.0f, 1.f });
 *
 *         SkColorMatrix cm;
 *         cm.setSaturation(1.5f);
 *         fPaint.setColorFilter(SkColorFilters::Matrix(cm));
 *
 *         auto recorder = skcpu::Recorder::TODO();
 *
 *         // The background image is standard sRGB
 *         fBackgroundImage = ToolUtils::GetResourceAsImage("images/yellow_rose.png")
 *                                     ->makeRasterImage(nullptr)
 *                                     ->makeColorSpace(recorder, SkColorSpace::MakeSRGB(),
 *                                         {});
 *         // The pip will be PQ-ish HDR
 *         fPiPImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png")
 *                              ->makeRasterImage(nullptr)
 *                              ->makeColorSpace(recorder,
 *                                               SkColorSpace::MakeRGB(SkNamedTransferFn::kPQ,
 *                                                                     SkNamedGamut::kRec2020),
 *                                               {});
 *
 *         const float sigma = 32.f;
 *         const bool scaleSigma = this->getMode() == kGM_Mode;
 *         const float sigmaX = sigma * (scaleSigma ? kNonBenchSize.width() : kFullSize.width()) /
 *                                      (float) kFullSize.width();
 *         const float sigmaY = sigma * (scaleSigma ? kNonBenchSize.height() : kFullSize.height()) /
 *                                      (float) kFullSize.height();
 *         fShadeBlur = SkImageFilters::Blur(sigmaX, sigmaY, SkTileMode::kClamp, nullptr);
 *     }
 *
 * private:
 *     sk_sp<SkImage> fBackgroundImage;
 *     sk_sp<SkImage> fPiPImage;
 *
 *     sk_sp<SkImageFilter> fShadeBlur;
 *     SkPaint fPaint;
 * }
 * ```
 */
public open class HDRPiPBlurGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkISize kFullSize{2560, 1440}
   * ```
   */
  private var fBackgroundImage: SkSp<SkImage> = TODO("Initialize fBackgroundImage")

  /**
   * C++ original:
   * ```cpp
   * static constexpr SkISize kNonBenchSize{640, 360}
   * ```
   */
  private var fPiPImage: SkSp<SkImage> = TODO("Initialize fPiPImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fBackgroundImage
   * ```
   */
  private var fShadeBlur: SkSp<SkImageFilter> = TODO("Initialize fShadeBlur")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fPiPImage
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hdr-pip-blur"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         if (this->getMode() == kBench_Mode) {
   *             return kFullSize;
   *         } else {
   *             return kNonBenchSize;
   *         }
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         // When running in DM, all offscreen passes and the final rendering will be small.
   *         SkIRect screenBounds = SkIRect::MakeSize(this->getMode() == kGM_Mode ? kNonBenchSize
   *                                                                              : kFullSize);
   *
   *         sk_sp<SkImage> input;
   *         {
   *             // The main surface is RGBA8 but with a wider gamut sRGB colorspace.
   *             sk_sp<SkSurface> content = canvas->makeSurface(
   *                     canvas->imageInfo().makeWH(screenBounds.width(), screenBounds.height())
   *                                        .makeColorType(kRGBA_8888_SkColorType)
   *                                        .makeColorSpace(
   *                                                 SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020,
   *                                                                       SkNamedGamut::kDisplayP3)));
   *             if (!content) {
   *                 // This occurs in DDL dm configs
   *                 *errorMsg = "Could not create offscreen surface";
   *                 return DrawResult::kSkip;
   *             }
   *
   *             SkCanvas* c = content->getCanvas();
   *             c->clear(SkColors::kDkGray);
   *
   *             SkMatrix toScreenBounds = SkMatrix::RectToRectOrIdentity(SkRect::Make(kFullSize),
   *                                                                      SkRect::Make(screenBounds));
   *             c->concat(toScreenBounds);
   *
   *             // Now render everything to `c` as if it were a kFullSize image.
   *             c->drawImageRect(fBackgroundImage.get(),
   *                              SkRect::Make(fBackgroundImage->bounds()),
   *                              SkRect::Make(kFullSize),
   *                              SkFilterMode::kLinear,
   *                              &fPaint,
   *                              SkCanvas::kFast_SrcRectConstraint);
   *
   *             SkRRect pip = SkRRect::MakeRectXY(SkRect::MakeXYWH(1500.f, 700.f, 800.f, 600.f),
   *                                               20.f, 20.f);
   *             c->drawRRect(pip, fPaint);
   *             c->save();
   *                 c->clipRRect(pip, true);
   *                 c->drawImageRect(fPiPImage.get(),
   *                                  SkRect::MakeIWH(fPiPImage->width(), fPiPImage->height()),
   *                                  pip.rect(),
   *                                  SkFilterMode::kLinear,
   *                                  &fPaint,
   *                                  SkCanvas::kFast_SrcRectConstraint);
   *             c->restore();
   *             input = content->makeTemporaryImage();
   *         }
   *
   *         canvas->save();
   *
   *         if (this->getMode() == kSample_Mode) {
   *             // For viewer, the offscreen passes operate at full resolution, but we draw smaller to
   *             // fit into the window. This lets overall frame times match nanobench, but it looks like
   *             // what dm produces.
   *             SkMatrix toViewBounds = SkMatrix::RectToRectOrIdentity(SkRect::Make(screenBounds),
   *                                                                    SkRect::Make(kNonBenchSize));
   *             canvas->concat(toViewBounds);
   *         }
   *
   *         canvas->clipIRect(screenBounds);
   *         canvas->drawImage(input.get(), 0.f, 0.f, SkFilterMode::kLinear, &fPaint);
   *
   *         // Explicitly blur the input image
   *         // NOTE: Skip calling MakeWithFilter and comment out the last `drawImageRect` call to
   *         // disable the shade blur.
   *         SkIRect outSubset;
   *         SkIPoint outOffset;
   *         sk_sp<SkImage> blur;
   * #if defined(SK_GRAPHITE)
   *         if (canvas->recorder()) {
   *             blur = SkImages::MakeWithFilter(canvas->recorder(),
   *                                             input,
   *                                             fShadeBlur.get(),
   *                                             screenBounds, screenBounds,
   *                                             &outSubset, &outOffset);
   *         } else
   * #endif
   * #if defined(SK_GANESH)
   *         if (canvas->recordingContext()) {
   *             blur = SkImages::MakeWithFilter(canvas->recordingContext(),
   *                                             input,
   *                                             fShadeBlur.get(),
   *                                             screenBounds, screenBounds,
   *                                             &outSubset, &outOffset);
   *         } else
   * #endif
   *         {
   *             blur = SkImages::MakeWithFilter(input,
   *                                             fShadeBlur.get(),
   *                                             screenBounds, screenBounds,
   *                                             &outSubset, &outOffset);
   *         }
   *
   *         SkPaint fadedBlur;
   *         fadedBlur.setAlphaf(0.9f);
   *         canvas->drawImageRect(blur.get(),
   *                               SkRect::Make(outSubset),
   *                               SkRect::MakeXYWH(outOffset.x(), outOffset.y(),
   *                                                outSubset.width(), outSubset.height()),
   *                               SkFilterMode::kLinear,
   *                               &fadedBlur,
   *                               SkCanvas::kFast_SrcRectConstraint);
   *
   *         canvas->restore();
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fPaint.setColor4f({ 0.0f, 0.0f, 0.0f, 1.f });
   *
   *         SkColorMatrix cm;
   *         cm.setSaturation(1.5f);
   *         fPaint.setColorFilter(SkColorFilters::Matrix(cm));
   *
   *         auto recorder = skcpu::Recorder::TODO();
   *
   *         // The background image is standard sRGB
   *         fBackgroundImage = ToolUtils::GetResourceAsImage("images/yellow_rose.png")
   *                                     ->makeRasterImage(nullptr)
   *                                     ->makeColorSpace(recorder, SkColorSpace::MakeSRGB(),
   *                                         {});
   *         // The pip will be PQ-ish HDR
   *         fPiPImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png")
   *                              ->makeRasterImage(nullptr)
   *                              ->makeColorSpace(recorder,
   *                                               SkColorSpace::MakeRGB(SkNamedTransferFn::kPQ,
   *                                                                     SkNamedGamut::kRec2020),
   *                                               {});
   *
   *         const float sigma = 32.f;
   *         const bool scaleSigma = this->getMode() == kGM_Mode;
   *         const float sigmaX = sigma * (scaleSigma ? kNonBenchSize.width() : kFullSize.width()) /
   *                                      (float) kFullSize.width();
   *         const float sigmaY = sigma * (scaleSigma ? kNonBenchSize.height() : kFullSize.height()) /
   *                                      (float) kFullSize.height();
   *         fShadeBlur = SkImageFilters::Blur(sigmaX, sigmaY, SkTileMode::kClamp, nullptr);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  public companion object {
    private val kFullSize: SkISize = TODO("Initialize kFullSize")

    private val kNonBenchSize: SkISize = TODO("Initialize kNonBenchSize")
  }
}
