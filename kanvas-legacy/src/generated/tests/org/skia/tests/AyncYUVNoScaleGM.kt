package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AyncYUVNoScaleGM : public AsyncReadGMBase {
 * public:
 *     AyncYUVNoScaleGM() : AsyncReadGMBase("async_yuv_no_scale") {}
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         auto surface = canvas->getSurface();
 *         if (!surface) {
 *             *errorMsg = "Not supported on recording/vector backends.";
 *             return skiagm::DrawResult::kSkip;
 *         }
 *
 *         GrDirectContext* dContext = nullptr;
 * #if defined(SK_GANESH)
 *         dContext = GrAsDirectContext(surface->recordingContext());
 *         if (!dContext && surface->recordingContext()) {
 *             *errorMsg = "Not supported in DDL mode";
 *             return skiagm::DrawResult::kSkip;
 *         }
 * #endif
 *
 *         auto image = ToolUtils::GetResourceAsImage("images/yellow_rose.webp");
 *         if (!image) {
 *             return skiagm::DrawResult::kFail;
 *         }
 *
 *         static constexpr SkIPoint kOffset = {15, 12};
 *         SkISize evenSz = {image->width() & ~0b1, image->height() & ~0b1};
 *         canvas->drawImage(image.get(), kOffset.fX, kOffset.fY);
 *
 *         skgpu::graphite::Recorder* recorder = canvas->recorder();
 *         SkScopeExit scopeExit;
 *         auto yuvImage = readAndScaleYUVA<ReadSource::kSurface>(surface,
 *                                                                SkIRect::MakePtSize(kOffset, evenSz),
 *                                                                evenSz,
 *                                                                /*readAlpha=*/false,
 *                                                                dContext,
 *                                                                recorder,
 *                                                                kRec601_SkYUVColorSpace,
 *                                                                SkImage::RescaleGamma::kSrc,
 *                                                                SkImage::RescaleMode::kNearest,
 *                                                                &scopeExit);
 *
 *         canvas->clear(SK_ColorWHITE);
 *         canvas->drawImage(yuvImage.get(), 0, 0);
 *
 *         return skiagm::DrawResult::kOk;
 *     }
 *     SkISize getISize() override { return {400, 300}; }
 * }
 * ```
 */
public open class AyncYUVNoScaleGM public constructor() : AsyncReadGMBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         auto surface = canvas->getSurface();
   *         if (!surface) {
   *             *errorMsg = "Not supported on recording/vector backends.";
   *             return skiagm::DrawResult::kSkip;
   *         }
   *
   *         GrDirectContext* dContext = nullptr;
   * #if defined(SK_GANESH)
   *         dContext = GrAsDirectContext(surface->recordingContext());
   *         if (!dContext && surface->recordingContext()) {
   *             *errorMsg = "Not supported in DDL mode";
   *             return skiagm::DrawResult::kSkip;
   *         }
   * #endif
   *
   *         auto image = ToolUtils::GetResourceAsImage("images/yellow_rose.webp");
   *         if (!image) {
   *             return skiagm::DrawResult::kFail;
   *         }
   *
   *         static constexpr SkIPoint kOffset = {15, 12};
   *         SkISize evenSz = {image->width() & ~0b1, image->height() & ~0b1};
   *         canvas->drawImage(image.get(), kOffset.fX, kOffset.fY);
   *
   *         skgpu::graphite::Recorder* recorder = canvas->recorder();
   *         SkScopeExit scopeExit;
   *         auto yuvImage = readAndScaleYUVA<ReadSource::kSurface>(surface,
   *                                                                SkIRect::MakePtSize(kOffset, evenSz),
   *                                                                evenSz,
   *                                                                /*readAlpha=*/false,
   *                                                                dContext,
   *                                                                recorder,
   *                                                                kRec601_SkYUVColorSpace,
   *                                                                SkImage::RescaleGamma::kSrc,
   *                                                                SkImage::RescaleMode::kNearest,
   *                                                                &scopeExit);
   *
   *         canvas->clear(SK_ColorWHITE);
   *         canvas->drawImage(yuvImage.get(), 0, 0);
   *
   *         return skiagm::DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {400, 300}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }
}
