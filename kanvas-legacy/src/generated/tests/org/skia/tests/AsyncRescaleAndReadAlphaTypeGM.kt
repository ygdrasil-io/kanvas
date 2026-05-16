package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AsyncRescaleAndReadAlphaTypeGM : public AsyncReadGMBase {
 * public:
 *     AsyncRescaleAndReadAlphaTypeGM() : AsyncReadGMBase("async_rescale_and_read_alpha_type") {}
 *
 *     SkISize getISize() override { return {512, 512}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         GrDirectContext* dContext = nullptr;
 * #if defined(SK_GANESH)
 *         dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (!dContext && canvas->recordingContext()) {
 *             *errorMsg = "Not supported in DDL mode";
 *             return skiagm::DrawResult::kSkip;
 *         }
 * #endif
 *
 *         if (canvas->recorder()) {
 *             *errorMsg = "Reading to unpremul not supported in Graphite.";
 *             return skiagm::DrawResult::kSkip;
 *         }
 *
 *         auto upmII = SkImageInfo::Make(200, 200, kRGBA_8888_SkColorType, kUnpremul_SkAlphaType);
 *
 *         auto pmII = upmII.makeAlphaType(kPremul_SkAlphaType);
 *
 *         auto upmSurf = SkSurfaces::Raster(upmII);
 *         auto pmSurf = SkSurfaces::Raster(pmII);
 *
 *         SkColor4f colors[] = {
 *                 {.3f, .3f, .3f, .3f},
 *                 {1.f, .2f, .6f, .9f},
 *                 {0.f, .1f, 1.f, .1f},
 *                 {.7f, .8f, .2f, .7f},
 *         };
 *         auto shader = SkShaders::RadialGradient({100, 100}, 230,
 *                                                 {{colors, {}, SkTileMode::kRepeat}, {}});
 *         SkPaint paint;
 *         paint.setShader(std::move(shader));
 *
 *         upmSurf->getCanvas()->drawPaint(paint);
 *         pmSurf ->getCanvas()->drawPaint(paint);
 *
 *         auto pmImg  =  pmSurf->makeImageSnapshot();
 *         auto upmImg = upmSurf->makeImageSnapshot();
 *
 *         auto imageOrResult = convert_image_to_source<ReadSource::kImage>(canvas,
 *                                                                          std::move(pmImg),
 *                                                                          errorMsg);
 *         if (const auto* dr = std::get_if<skiagm::DrawResult>(&imageOrResult)) {
 *             return *dr;
 *         }
 *         pmImg = std::move(std::get<sk_sp<SkImage>>(imageOrResult));
 *
 *         imageOrResult = convert_image_to_source<ReadSource::kImage>(canvas,
 *                                                                     std::move(upmImg),
 *                                                                     errorMsg);
 *         if (const auto* dr = std::get_if<skiagm::DrawResult>(&imageOrResult)) {
 *             return *dr;
 *         }
 *         upmImg = std::move(std::get<sk_sp<SkImage>>(imageOrResult));
 *
 *         int size = 256;
 *
 *         ToolUtils::draw_checkerboard(canvas, SK_ColorWHITE, SK_ColorBLACK, 32);
 *
 *         for (const auto& img : {pmImg, upmImg}) {
 *             canvas->save();
 *             for (auto readAT : {kPremul_SkAlphaType, kUnpremul_SkAlphaType}) {
 *                 auto readInfo = img->imageInfo().makeAlphaType(readAT).makeWH(size, size);
 *                 auto result =
 *                         readAndScaleRGBA<ReadSource::kImage>(img.get(),
 *                                                              SkIRect::MakeSize(img->dimensions()),
 *                                                              dContext,
 *                                                              canvas->recorder(),
 *                                                              readInfo,
 *                                                              SkImage::RescaleGamma::kSrc,
 *                                                              SkImage::RescaleMode::kRepeatedCubic);
 *                 if (!result) {
 *                     *errorMsg = "async readback failed";
 *                     return skiagm::DrawResult::kFail;
 *                 }
 *                 canvas->drawImage(result, 0, 0);
 *                 canvas->translate(size, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, size);
 *         }
 *         return skiagm::DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class AsyncRescaleAndReadAlphaTypeGM public constructor() : AsyncReadGMBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {512, 512}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         GrDirectContext* dContext = nullptr;
   * #if defined(SK_GANESH)
   *         dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (!dContext && canvas->recordingContext()) {
   *             *errorMsg = "Not supported in DDL mode";
   *             return skiagm::DrawResult::kSkip;
   *         }
   * #endif
   *
   *         if (canvas->recorder()) {
   *             *errorMsg = "Reading to unpremul not supported in Graphite.";
   *             return skiagm::DrawResult::kSkip;
   *         }
   *
   *         auto upmII = SkImageInfo::Make(200, 200, kRGBA_8888_SkColorType, kUnpremul_SkAlphaType);
   *
   *         auto pmII = upmII.makeAlphaType(kPremul_SkAlphaType);
   *
   *         auto upmSurf = SkSurfaces::Raster(upmII);
   *         auto pmSurf = SkSurfaces::Raster(pmII);
   *
   *         SkColor4f colors[] = {
   *                 {.3f, .3f, .3f, .3f},
   *                 {1.f, .2f, .6f, .9f},
   *                 {0.f, .1f, 1.f, .1f},
   *                 {.7f, .8f, .2f, .7f},
   *         };
   *         auto shader = SkShaders::RadialGradient({100, 100}, 230,
   *                                                 {{colors, {}, SkTileMode::kRepeat}, {}});
   *         SkPaint paint;
   *         paint.setShader(std::move(shader));
   *
   *         upmSurf->getCanvas()->drawPaint(paint);
   *         pmSurf ->getCanvas()->drawPaint(paint);
   *
   *         auto pmImg  =  pmSurf->makeImageSnapshot();
   *         auto upmImg = upmSurf->makeImageSnapshot();
   *
   *         auto imageOrResult = convert_image_to_source<ReadSource::kImage>(canvas,
   *                                                                          std::move(pmImg),
   *                                                                          errorMsg);
   *         if (const auto* dr = std::get_if<skiagm::DrawResult>(&imageOrResult)) {
   *             return *dr;
   *         }
   *         pmImg = std::move(std::get<sk_sp<SkImage>>(imageOrResult));
   *
   *         imageOrResult = convert_image_to_source<ReadSource::kImage>(canvas,
   *                                                                     std::move(upmImg),
   *                                                                     errorMsg);
   *         if (const auto* dr = std::get_if<skiagm::DrawResult>(&imageOrResult)) {
   *             return *dr;
   *         }
   *         upmImg = std::move(std::get<sk_sp<SkImage>>(imageOrResult));
   *
   *         int size = 256;
   *
   *         ToolUtils::draw_checkerboard(canvas, SK_ColorWHITE, SK_ColorBLACK, 32);
   *
   *         for (const auto& img : {pmImg, upmImg}) {
   *             canvas->save();
   *             for (auto readAT : {kPremul_SkAlphaType, kUnpremul_SkAlphaType}) {
   *                 auto readInfo = img->imageInfo().makeAlphaType(readAT).makeWH(size, size);
   *                 auto result =
   *                         readAndScaleRGBA<ReadSource::kImage>(img.get(),
   *                                                              SkIRect::MakeSize(img->dimensions()),
   *                                                              dContext,
   *                                                              canvas->recorder(),
   *                                                              readInfo,
   *                                                              SkImage::RescaleGamma::kSrc,
   *                                                              SkImage::RescaleMode::kRepeatedCubic);
   *                 if (!result) {
   *                     *errorMsg = "async readback failed";
   *                     return skiagm::DrawResult::kFail;
   *                 }
   *                 canvas->drawImage(result, 0, 0);
   *                 canvas->translate(size, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, size);
   *         }
   *         return skiagm::DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
