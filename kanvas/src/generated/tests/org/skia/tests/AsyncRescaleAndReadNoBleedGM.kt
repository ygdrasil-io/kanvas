package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AsyncRescaleAndReadNoBleedGM : public AsyncReadGMBase {
 * public:
 *     AsyncRescaleAndReadNoBleedGM() : AsyncReadGMBase("async_rescale_and_read_no_bleed") {}
 *
 *     SkISize getISize() override { return {60, 60}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (canvas->imageInfo().colorType() == kUnknown_SkColorType) {
 *             *errorMsg = "Not supported on recording/vector backends.";
 *             return skiagm::DrawResult::kSkip;
 *         }
 *
 * #if defined(SK_GANESH)
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (!dContext && canvas->recordingContext()) {
 *             *errorMsg = "Not supported in DDL mode";
 *             return skiagm::DrawResult::kSkip;
 *         }
 * #endif
 *
 *         static constexpr int kBorder = 5;
 *         static constexpr int kInner = 5;
 *         const auto srcRect = SkIRect::MakeXYWH(kBorder, kBorder, kInner, kInner);
 *         auto surfaceII = SkImageInfo::Make(kInner + 2 * kBorder,
 *                                            kInner + 2 * kBorder,
 *                                            kRGBA_8888_SkColorType,
 *                                            kPremul_SkAlphaType,
 *                                            SkColorSpace::MakeSRGB());
 *         auto surface = canvas->makeSurface(surfaceII);
 *         if (!surface) {
 *             *errorMsg = "Could not create surface for image.";
 * #if defined(SK_GANESH)
 *             // When testing abandoned GrContext we expect surface creation to fail.
 *             if (canvas->recordingContext() && canvas->recordingContext()->abandoned()) {
 *                 return skiagm::DrawResult::kSkip;
 *             }
 * #endif
 *             return skiagm::DrawResult::kFail;
 *         }
 *         surface->getCanvas()->clear(SK_ColorRED);
 *         surface->getCanvas()->save();
 *         surface->getCanvas()->clipRect(SkRect::Make(srcRect), SkClipOp::kIntersect, false);
 *         surface->getCanvas()->clear(SK_ColorBLUE);
 *         surface->getCanvas()->restore();
 *         static constexpr int kPad = 2;
 *         canvas->translate(kPad, kPad);
 *         skiagm::DrawResult result;
 *         SkISize downSize = {static_cast<int>(kInner / 2), static_cast<int>(kInner / 2)};
 *         result = drawRescaleGrid<ReadSource::kSurface>(canvas,
 *                                                        surface.get(),
 *                                                        srcRect,
 *                                                        downSize,
 *                                                        Type::kRGBA,
 *                                                        errorMsg,
 *                                                        kPad);
 *         if (result != skiagm::DrawResult::kOk) {
 *             return result;
 *         }
 *         canvas->translate(0, 4 * downSize.height());
 *         SkISize upSize = {static_cast<int>(kInner * 3.5), static_cast<int>(kInner * 4.6)};
 *         result = drawRescaleGrid<ReadSource::kSurface>(canvas,
 *                                                        surface.get(),
 *                                                        srcRect,
 *                                                        upSize,
 *                                                        Type::kRGBA,
 *                                                        errorMsg,
 *                                                        kPad);
 *         if (result != skiagm::DrawResult::kOk) {
 *             return result;
 *         }
 *         return skiagm::DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class AsyncRescaleAndReadNoBleedGM public constructor() : AsyncReadGMBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {60, 60}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (canvas->imageInfo().colorType() == kUnknown_SkColorType) {
   *             *errorMsg = "Not supported on recording/vector backends.";
   *             return skiagm::DrawResult::kSkip;
   *         }
   *
   * #if defined(SK_GANESH)
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (!dContext && canvas->recordingContext()) {
   *             *errorMsg = "Not supported in DDL mode";
   *             return skiagm::DrawResult::kSkip;
   *         }
   * #endif
   *
   *         static constexpr int kBorder = 5;
   *         static constexpr int kInner = 5;
   *         const auto srcRect = SkIRect::MakeXYWH(kBorder, kBorder, kInner, kInner);
   *         auto surfaceII = SkImageInfo::Make(kInner + 2 * kBorder,
   *                                            kInner + 2 * kBorder,
   *                                            kRGBA_8888_SkColorType,
   *                                            kPremul_SkAlphaType,
   *                                            SkColorSpace::MakeSRGB());
   *         auto surface = canvas->makeSurface(surfaceII);
   *         if (!surface) {
   *             *errorMsg = "Could not create surface for image.";
   * #if defined(SK_GANESH)
   *             // When testing abandoned GrContext we expect surface creation to fail.
   *             if (canvas->recordingContext() && canvas->recordingContext()->abandoned()) {
   *                 return skiagm::DrawResult::kSkip;
   *             }
   * #endif
   *             return skiagm::DrawResult::kFail;
   *         }
   *         surface->getCanvas()->clear(SK_ColorRED);
   *         surface->getCanvas()->save();
   *         surface->getCanvas()->clipRect(SkRect::Make(srcRect), SkClipOp::kIntersect, false);
   *         surface->getCanvas()->clear(SK_ColorBLUE);
   *         surface->getCanvas()->restore();
   *         static constexpr int kPad = 2;
   *         canvas->translate(kPad, kPad);
   *         skiagm::DrawResult result;
   *         SkISize downSize = {static_cast<int>(kInner / 2), static_cast<int>(kInner / 2)};
   *         result = drawRescaleGrid<ReadSource::kSurface>(canvas,
   *                                                        surface.get(),
   *                                                        srcRect,
   *                                                        downSize,
   *                                                        Type::kRGBA,
   *                                                        errorMsg,
   *                                                        kPad);
   *         if (result != skiagm::DrawResult::kOk) {
   *             return result;
   *         }
   *         canvas->translate(0, 4 * downSize.height());
   *         SkISize upSize = {static_cast<int>(kInner * 3.5), static_cast<int>(kInner * 4.6)};
   *         result = drawRescaleGrid<ReadSource::kSurface>(canvas,
   *                                                        surface.get(),
   *                                                        srcRect,
   *                                                        upSize,
   *                                                        Type::kRGBA,
   *                                                        errorMsg,
   *                                                        kPad);
   *         if (result != skiagm::DrawResult::kOk) {
   *             return result;
   *         }
   *         return skiagm::DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
