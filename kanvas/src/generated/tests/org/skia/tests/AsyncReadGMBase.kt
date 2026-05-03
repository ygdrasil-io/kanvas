package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkScopeExit
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp
import org.skia.foundation.SkYUVColorSpace
import org.skia.gpu.Recorder
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AsyncReadGMBase : public skiagm::GM {
 * public:
 *     AsyncReadGMBase(const char* name) : fName(name) {}
 *
 *     SkString getName() const override { return fName; }
 *
 * protected:
 *     // Does a rescale and read using Graphite, Ganesh, or CPU and returns the result as a pixmap
 *     // image.
 *     template <ReadSource ReadSource>
 *     sk_sp<SkImage> readAndScaleRGBA(Source<ReadSource>* src,
 *                                     SkIRect srcRect,
 *                                     GrDirectContext* direct,
 *                                     skgpu::graphite::Recorder* recorder,
 *                                     const SkImageInfo& ii,
 *                                     SkImage::RescaleGamma rescaleGamma,
 *                                     SkImage::RescaleMode rescaleMode) {
 *         auto* asyncContext = new AsyncContext();
 *         if (recorder) {
 * #if defined(SK_GRAPHITE)
 *             skgpu::graphite::Context* graphiteContext = recorder->priv().context();
 *             if (!graphiteContext) {
 *                 return nullptr;
 *             }
 *             // We need to flush the existing drawing commands before we try to read
 *             std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
 *             if (!recording) {
 *                 return nullptr;
 *             }
 *             skgpu::graphite::InsertRecordingInfo recordingInfo;
 *             recordingInfo.fRecording = recording.get();
 *             if (!graphiteContext->insertRecording(recordingInfo)) {
 *                 return nullptr;
 *             }
 *
 *             graphiteContext->asyncRescaleAndReadPixels(src,
 *                                                        ii,
 *                                                        srcRect,
 *                                                        rescaleGamma,
 *                                                        rescaleMode,
 *                                                        AsyncCallback,
 *                                                        asyncContext);
 *             graphiteContext->submit();
 *             while (!asyncContext->fCalled) {
 *                 graphiteContext->checkAsyncWorkCompletion();
 *                 if (this->graphiteTestContext()) {
 *                     this->graphiteTestContext()->tick();
 *                 }
 *             }
 * #endif
 *         } else {
 *             src->asyncRescaleAndReadPixels(ii,
 *                                            srcRect,
 *                                            rescaleGamma,
 *                                            rescaleMode,
 *                                            AsyncCallback,
 *                                            asyncContext);
 * #if defined(SK_GANESH)
 *             if (direct) {
 *                 direct->submit();
 *             }
 *             while (!asyncContext->fCalled) {
 *                 // Only GPU should actually be asynchronous.
 *                 SkASSERT(direct);
 *                 direct->checkAsyncWorkCompletion();
 *             }
 * #endif
 *         }
 *         SkASSERT(asyncContext->fCalled);
 *         if (!asyncContext->fResult) {
 *             return nullptr;
 *         }
 *         SkPixmap pixmap(ii, asyncContext->fResult->data(0), asyncContext->fResult->rowBytes(0));
 *         auto releasePixels = [](const void*, void* c) { delete static_cast<AsyncContext*>(c); };
 *         return SkImages::RasterFromPixmap(pixmap, releasePixels, asyncContext);
 *     }
 *
 *     // Does a YUV[A] rescale and read using Graphite or Ganesh (no CPU support) and returns the
 *     // result as a YUVA planar texture image.
 *     template <ReadSource ReadSource>
 *     sk_sp<SkImage> readAndScaleYUVA(Source<ReadSource>* src,
 *                                     SkIRect srcRect,
 *                                     SkISize resultSize,
 *                                     bool readAlpha,
 *                                     GrDirectContext* direct,
 *                                     skgpu::graphite::Recorder* recorder,
 *                                     SkYUVColorSpace yuvCS,
 *                                     SkImage::RescaleGamma rescaleGamma,
 *                                     SkImage::RescaleMode rescaleMode,
 *                                     SkScopeExit* cleanup) {
 *         SkASSERT(!(resultSize.width() & 0b1) && !(resultSize.height() & 0b1));
 *
 *         SkISize uvSize = {resultSize.width() / 2, resultSize.height() / 2};
 *         SkImageInfo yaII = SkImageInfo::Make(resultSize, kGray_8_SkColorType, kPremul_SkAlphaType);
 *         SkImageInfo uvII = SkImageInfo::Make(uvSize,     kGray_8_SkColorType, kPremul_SkAlphaType);
 *
 *         AsyncContext asyncContext;
 *         if (recorder) {
 * #if defined(SK_GRAPHITE)
 *             skgpu::graphite::Context* graphiteContext = recorder->priv().context();
 *             if (!graphiteContext) {
 *                 return nullptr;
 *             }
 *             // We need to flush the existing drawing commands before we try to read
 *             std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
 *             if (!recording) {
 *                 return nullptr;
 *             }
 *             skgpu::graphite::InsertRecordingInfo recordingInfo;
 *             recordingInfo.fRecording = recording.get();
 *             if (!graphiteContext->insertRecording(recordingInfo)) {
 *                 return nullptr;
 *             }
 *
 *             if (readAlpha) {
 *                 graphiteContext->asyncRescaleAndReadPixelsYUVA420(src,
 *                                                                   yuvCS,
 *                                                                   SkColorSpace::MakeSRGB(),
 *                                                                   srcRect,
 *                                                                   resultSize,
 *                                                                   rescaleGamma,
 *                                                                   rescaleMode,
 *                                                                   AsyncCallback,
 *                                                                   &asyncContext);
 *             } else {
 *                 graphiteContext->asyncRescaleAndReadPixelsYUV420(src,
 *                                                                  yuvCS,
 *                                                                  SkColorSpace::MakeSRGB(),
 *                                                                  srcRect,
 *                                                                  resultSize,
 *                                                                  rescaleGamma,
 *                                                                  rescaleMode,
 *                                                                  AsyncCallback,
 *                                                                  &asyncContext);
 *             }
 *             graphiteContext->submit();
 *             while (!asyncContext.fCalled) {
 *                 graphiteContext->checkAsyncWorkCompletion();
 *                 if (this->graphiteTestContext()) {
 *                     this->graphiteTestContext()->tick();
 *                 }
 *             }
 * #endif
 *         } else {
 *             if (readAlpha) {
 *                 src->asyncRescaleAndReadPixelsYUVA420(yuvCS,
 *                                                       SkColorSpace::MakeSRGB(),
 *                                                       srcRect,
 *                                                       resultSize,
 *                                                       rescaleGamma,
 *                                                       rescaleMode,
 *                                                       AsyncCallback,
 *                                                       &asyncContext);
 *             } else {
 *                 src->asyncRescaleAndReadPixelsYUV420(yuvCS,
 *                                                      SkColorSpace::MakeSRGB(),
 *                                                      srcRect,
 *                                                      resultSize,
 *                                                      rescaleGamma,
 *                                                      rescaleMode,
 *                                                      AsyncCallback,
 *                                                      &asyncContext);
 *             }
 * #if defined(SK_GANESH)
 *             if (direct) {
 *                 direct->submit();
 *             }
 *             while (!asyncContext.fCalled) {
 *                 // Only GPU should actually be asynchronous.
 *                 SkASSERT(direct);
 *                 direct->checkAsyncWorkCompletion();
 *             }
 * #endif
 *         }
 *         SkASSERT(asyncContext.fCalled);
 *         if (!asyncContext.fResult) {
 *             return nullptr;
 *         }
 *         auto planeConfig = readAlpha ? SkYUVAInfo::PlaneConfig::kY_U_V_A :
 *                                        SkYUVAInfo::PlaneConfig::kY_U_V;
 *         SkYUVAInfo yuvaInfo(resultSize,
 *                             planeConfig,
 *                             SkYUVAInfo::Subsampling::k420,
 *                             yuvCS);
 *         SkPixmap yuvPMs[4] = {
 *                 {yaII, asyncContext.fResult->data(0), asyncContext.fResult->rowBytes(0)},
 *                 {uvII, asyncContext.fResult->data(1), asyncContext.fResult->rowBytes(1)},
 *                 {uvII, asyncContext.fResult->data(2), asyncContext.fResult->rowBytes(2)},
 *                 {},
 *         };
 *         if (readAlpha) {
 *             yuvPMs[3] = {yaII, asyncContext.fResult->data(3), asyncContext.fResult->rowBytes(3)};
 *         }
 *         auto pixmaps = SkYUVAPixmaps::FromExternalPixmaps(yuvaInfo, yuvPMs);
 *         SkASSERT(pixmaps.isValid());
 *         auto lazyYUVImage = sk_gpu_test::LazyYUVImage::Make(pixmaps);
 *         SkASSERT(lazyYUVImage);
 * #if defined(SK_GRAPHITE)
 *         if (recorder) {
 *             return lazyYUVImage->refImage(recorder, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
 *         } else
 * #endif
 *         {
 *             return lazyYUVImage->refImage(direct, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
 *         }
 *     }
 *
 *     // Draws a 3x2 grid of rescales. The columns are none, low, and high filter quality. The rows
 *     // are rescale in src gamma and rescale in linear gamma.
 *     template <ReadSource ReadSource>
 *     skiagm::DrawResult drawRescaleGrid(SkCanvas* canvas,
 *                                        Source<ReadSource>* src,
 *                                        SkIRect srcRect,
 *                                        SkISize readSize,
 *                                        Type type,
 *                                        SkString* errorMsg,
 *                                        int pad = 0) {
 *         SkASSERT(canvas->imageInfo().colorType() != kUnknown_SkColorType);
 *
 *         GrDirectContext* direct = nullptr;
 * #if defined(SK_GANESH)
 *         direct = GrAsDirectContext(canvas->recordingContext());
 *         SkASSERT(direct || !canvas->recordingContext());
 * #endif
 *
 *         auto recorder = canvas->recorder();
 *
 *         SkYUVColorSpace yuvColorSpace = kRec601_SkYUVColorSpace;
 *         canvas->save();
 *         for (auto gamma : {SkImage::RescaleGamma::kSrc, SkImage::RescaleGamma::kLinear}) {
 *             canvas->save();
 *             for (auto mode : {SkImage::RescaleMode::kNearest,
 *                               SkImage::RescaleMode::kRepeatedLinear,
 *                               SkImage::RescaleMode::kRepeatedCubic}) {
 *                 SkScopeExit cleanup;
 *                 sk_sp<SkImage> result;
 *                 switch (type) {
 *                     case Type::kRGBA: {
 *                         const auto ii = canvas->imageInfo().makeDimensions(readSize);
 *                         result = readAndScaleRGBA<ReadSource>(src,
 *                                                               srcRect,
 *                                                               direct,
 *                                                               recorder,
 *                                                               ii,
 *                                                               gamma,
 *                                                               mode);
 *                         if (!result) {
 *                             errorMsg->printf("async read call failed.");
 *                             return skiagm::DrawResult::kFail;
 *                         }
 *                         break;
 *                     }
 *                     case Type::kYUV:
 *                     case Type::kYUVA:
 *                         result = readAndScaleYUVA<ReadSource>(src,
 *                                                               srcRect,
 *                                                               readSize,
 *                                                               /*readAlpha=*/type == Type::kYUVA,
 *                                                               direct,
 *                                                               recorder,
 *                                                               yuvColorSpace,
 *                                                               gamma,
 *                                                               mode,
 *                                                               &cleanup);
 *                         if (!result) {
 *                             errorMsg->printf("YUV[A]420 async call failed. Allowed for now.");
 *                             return skiagm::DrawResult::kSkip;
 *                         }
 *                         int nextCS = static_cast<int>(yuvColorSpace + 1) %
 *                                      (kLastEnum_SkYUVColorSpace + 1);
 *                         yuvColorSpace = static_cast<SkYUVColorSpace>(nextCS);
 *                         break;
 *                 }
 *                 canvas->drawImage(result, 0, 0);
 *                 canvas->translate(readSize.width() + pad, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, readSize.height() + pad);
 *         }
 *         canvas->restore();
 *         return skiagm::DrawResult::kOk;
 *     }
 *
 * private:
 *     struct AsyncContext {
 *         bool fCalled = false;
 *         std::unique_ptr<const SkImage::AsyncReadResult> fResult;
 *     };
 *
 *     // Making this a lambda in the test functions caused:
 *     //   "error: cannot compile this forwarded non-trivially copyable parameter yet"
 *     // on x86/Win/Clang bot, referring to 'result'.
 *     static void AsyncCallback(void* c, std::unique_ptr<const SkImage::AsyncReadResult> result) {
 *         auto context = static_cast<AsyncContext*>(c);
 *         context->fResult = std::move(result);
 *         context->fCalled = true;
 *     }
 *
 *     SkString fName;
 * }
 * ```
 */
public abstract class AsyncReadGMBase public constructor(
  name: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fName; }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <ReadSource ReadSource>
   *     sk_sp<SkImage> readAndScaleRGBA(Source<ReadSource>* src,
   *                                     SkIRect srcRect,
   *                                     GrDirectContext* direct,
   *                                     skgpu::graphite::Recorder* recorder,
   *                                     const SkImageInfo& ii,
   *                                     SkImage::RescaleGamma rescaleGamma,
   *                                     SkImage::RescaleMode rescaleMode) {
   *         auto* asyncContext = new AsyncContext();
   *         if (recorder) {
   * #if defined(SK_GRAPHITE)
   *             skgpu::graphite::Context* graphiteContext = recorder->priv().context();
   *             if (!graphiteContext) {
   *                 return nullptr;
   *             }
   *             // We need to flush the existing drawing commands before we try to read
   *             std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
   *             if (!recording) {
   *                 return nullptr;
   *             }
   *             skgpu::graphite::InsertRecordingInfo recordingInfo;
   *             recordingInfo.fRecording = recording.get();
   *             if (!graphiteContext->insertRecording(recordingInfo)) {
   *                 return nullptr;
   *             }
   *
   *             graphiteContext->asyncRescaleAndReadPixels(src,
   *                                                        ii,
   *                                                        srcRect,
   *                                                        rescaleGamma,
   *                                                        rescaleMode,
   *                                                        AsyncCallback,
   *                                                        asyncContext);
   *             graphiteContext->submit();
   *             while (!asyncContext->fCalled) {
   *                 graphiteContext->checkAsyncWorkCompletion();
   *                 if (this->graphiteTestContext()) {
   *                     this->graphiteTestContext()->tick();
   *                 }
   *             }
   * #endif
   *         } else {
   *             src->asyncRescaleAndReadPixels(ii,
   *                                            srcRect,
   *                                            rescaleGamma,
   *                                            rescaleMode,
   *                                            AsyncCallback,
   *                                            asyncContext);
   * #if defined(SK_GANESH)
   *             if (direct) {
   *                 direct->submit();
   *             }
   *             while (!asyncContext->fCalled) {
   *                 // Only GPU should actually be asynchronous.
   *                 SkASSERT(direct);
   *                 direct->checkAsyncWorkCompletion();
   *             }
   * #endif
   *         }
   *         SkASSERT(asyncContext->fCalled);
   *         if (!asyncContext->fResult) {
   *             return nullptr;
   *         }
   *         SkPixmap pixmap(ii, asyncContext->fResult->data(0), asyncContext->fResult->rowBytes(0));
   *         auto releasePixels = [](const void*, void* c) { delete static_cast<AsyncContext*>(c); };
   *         return SkImages::RasterFromPixmap(pixmap, releasePixels, asyncContext);
   *     }
   * ```
   */
  protected fun <ReadSource> readAndScaleRGBA(
    src: Source<ReadSource>?,
    srcRect: SkIRect,
    direct: GrDirectContext?,
    recorder: Recorder?,
    ii: SkImageInfo,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
  ): SkSp<SkImage> {
    TODO("Implement readAndScaleRGBA")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <ReadSource ReadSource>
   *     sk_sp<SkImage> readAndScaleYUVA(Source<ReadSource>* src,
   *                                     SkIRect srcRect,
   *                                     SkISize resultSize,
   *                                     bool readAlpha,
   *                                     GrDirectContext* direct,
   *                                     skgpu::graphite::Recorder* recorder,
   *                                     SkYUVColorSpace yuvCS,
   *                                     SkImage::RescaleGamma rescaleGamma,
   *                                     SkImage::RescaleMode rescaleMode,
   *                                     SkScopeExit* cleanup) {
   *         SkASSERT(!(resultSize.width() & 0b1) && !(resultSize.height() & 0b1));
   *
   *         SkISize uvSize = {resultSize.width() / 2, resultSize.height() / 2};
   *         SkImageInfo yaII = SkImageInfo::Make(resultSize, kGray_8_SkColorType, kPremul_SkAlphaType);
   *         SkImageInfo uvII = SkImageInfo::Make(uvSize,     kGray_8_SkColorType, kPremul_SkAlphaType);
   *
   *         AsyncContext asyncContext;
   *         if (recorder) {
   * #if defined(SK_GRAPHITE)
   *             skgpu::graphite::Context* graphiteContext = recorder->priv().context();
   *             if (!graphiteContext) {
   *                 return nullptr;
   *             }
   *             // We need to flush the existing drawing commands before we try to read
   *             std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
   *             if (!recording) {
   *                 return nullptr;
   *             }
   *             skgpu::graphite::InsertRecordingInfo recordingInfo;
   *             recordingInfo.fRecording = recording.get();
   *             if (!graphiteContext->insertRecording(recordingInfo)) {
   *                 return nullptr;
   *             }
   *
   *             if (readAlpha) {
   *                 graphiteContext->asyncRescaleAndReadPixelsYUVA420(src,
   *                                                                   yuvCS,
   *                                                                   SkColorSpace::MakeSRGB(),
   *                                                                   srcRect,
   *                                                                   resultSize,
   *                                                                   rescaleGamma,
   *                                                                   rescaleMode,
   *                                                                   AsyncCallback,
   *                                                                   &asyncContext);
   *             } else {
   *                 graphiteContext->asyncRescaleAndReadPixelsYUV420(src,
   *                                                                  yuvCS,
   *                                                                  SkColorSpace::MakeSRGB(),
   *                                                                  srcRect,
   *                                                                  resultSize,
   *                                                                  rescaleGamma,
   *                                                                  rescaleMode,
   *                                                                  AsyncCallback,
   *                                                                  &asyncContext);
   *             }
   *             graphiteContext->submit();
   *             while (!asyncContext.fCalled) {
   *                 graphiteContext->checkAsyncWorkCompletion();
   *                 if (this->graphiteTestContext()) {
   *                     this->graphiteTestContext()->tick();
   *                 }
   *             }
   * #endif
   *         } else {
   *             if (readAlpha) {
   *                 src->asyncRescaleAndReadPixelsYUVA420(yuvCS,
   *                                                       SkColorSpace::MakeSRGB(),
   *                                                       srcRect,
   *                                                       resultSize,
   *                                                       rescaleGamma,
   *                                                       rescaleMode,
   *                                                       AsyncCallback,
   *                                                       &asyncContext);
   *             } else {
   *                 src->asyncRescaleAndReadPixelsYUV420(yuvCS,
   *                                                      SkColorSpace::MakeSRGB(),
   *                                                      srcRect,
   *                                                      resultSize,
   *                                                      rescaleGamma,
   *                                                      rescaleMode,
   *                                                      AsyncCallback,
   *                                                      &asyncContext);
   *             }
   * #if defined(SK_GANESH)
   *             if (direct) {
   *                 direct->submit();
   *             }
   *             while (!asyncContext.fCalled) {
   *                 // Only GPU should actually be asynchronous.
   *                 SkASSERT(direct);
   *                 direct->checkAsyncWorkCompletion();
   *             }
   * #endif
   *         }
   *         SkASSERT(asyncContext.fCalled);
   *         if (!asyncContext.fResult) {
   *             return nullptr;
   *         }
   *         auto planeConfig = readAlpha ? SkYUVAInfo::PlaneConfig::kY_U_V_A :
   *                                        SkYUVAInfo::PlaneConfig::kY_U_V;
   *         SkYUVAInfo yuvaInfo(resultSize,
   *                             planeConfig,
   *                             SkYUVAInfo::Subsampling::k420,
   *                             yuvCS);
   *         SkPixmap yuvPMs[4] = {
   *                 {yaII, asyncContext.fResult->data(0), asyncContext.fResult->rowBytes(0)},
   *                 {uvII, asyncContext.fResult->data(1), asyncContext.fResult->rowBytes(1)},
   *                 {uvII, asyncContext.fResult->data(2), asyncContext.fResult->rowBytes(2)},
   *                 {},
   *         };
   *         if (readAlpha) {
   *             yuvPMs[3] = {yaII, asyncContext.fResult->data(3), asyncContext.fResult->rowBytes(3)};
   *         }
   *         auto pixmaps = SkYUVAPixmaps::FromExternalPixmaps(yuvaInfo, yuvPMs);
   *         SkASSERT(pixmaps.isValid());
   *         auto lazyYUVImage = sk_gpu_test::LazyYUVImage::Make(pixmaps);
   *         SkASSERT(lazyYUVImage);
   * #if defined(SK_GRAPHITE)
   *         if (recorder) {
   *             return lazyYUVImage->refImage(recorder, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
   *         } else
   * #endif
   *         {
   *             return lazyYUVImage->refImage(direct, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
   *         }
   *     }
   * ```
   */
  protected fun <ReadSource> readAndScaleYUVA(
    src: Source<ReadSource>?,
    srcRect: SkIRect,
    resultSize: SkISize,
    readAlpha: Boolean,
    direct: GrDirectContext?,
    recorder: Recorder?,
    yuvCS: SkYUVColorSpace,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    cleanup: SkScopeExit?,
  ): SkSp<SkImage> {
    TODO("Implement readAndScaleYUVA")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <ReadSource ReadSource>
   *     skiagm::DrawResult drawRescaleGrid(SkCanvas* canvas,
   *                                        Source<ReadSource>* src,
   *                                        SkIRect srcRect,
   *                                        SkISize readSize,
   *                                        Type type,
   *                                        SkString* errorMsg,
   *                                        int pad = 0) {
   *         SkASSERT(canvas->imageInfo().colorType() != kUnknown_SkColorType);
   *
   *         GrDirectContext* direct = nullptr;
   * #if defined(SK_GANESH)
   *         direct = GrAsDirectContext(canvas->recordingContext());
   *         SkASSERT(direct || !canvas->recordingContext());
   * #endif
   *
   *         auto recorder = canvas->recorder();
   *
   *         SkYUVColorSpace yuvColorSpace = kRec601_SkYUVColorSpace;
   *         canvas->save();
   *         for (auto gamma : {SkImage::RescaleGamma::kSrc, SkImage::RescaleGamma::kLinear}) {
   *             canvas->save();
   *             for (auto mode : {SkImage::RescaleMode::kNearest,
   *                               SkImage::RescaleMode::kRepeatedLinear,
   *                               SkImage::RescaleMode::kRepeatedCubic}) {
   *                 SkScopeExit cleanup;
   *                 sk_sp<SkImage> result;
   *                 switch (type) {
   *                     case Type::kRGBA: {
   *                         const auto ii = canvas->imageInfo().makeDimensions(readSize);
   *                         result = readAndScaleRGBA<ReadSource>(src,
   *                                                               srcRect,
   *                                                               direct,
   *                                                               recorder,
   *                                                               ii,
   *                                                               gamma,
   *                                                               mode);
   *                         if (!result) {
   *                             errorMsg->printf("async read call failed.");
   *                             return skiagm::DrawResult::kFail;
   *                         }
   *                         break;
   *                     }
   *                     case Type::kYUV:
   *                     case Type::kYUVA:
   *                         result = readAndScaleYUVA<ReadSource>(src,
   *                                                               srcRect,
   *                                                               readSize,
   *                                                               /*readAlpha=*/type == Type::kYUVA,
   *                                                               direct,
   *                                                               recorder,
   *                                                               yuvColorSpace,
   *                                                               gamma,
   *                                                               mode,
   *                                                               &cleanup);
   *                         if (!result) {
   *                             errorMsg->printf("YUV[A]420 async call failed. Allowed for now.");
   *                             return skiagm::DrawResult::kSkip;
   *                         }
   *                         int nextCS = static_cast<int>(yuvColorSpace + 1) %
   *                                      (kLastEnum_SkYUVColorSpace + 1);
   *                         yuvColorSpace = static_cast<SkYUVColorSpace>(nextCS);
   *                         break;
   *                 }
   *                 canvas->drawImage(result, 0, 0);
   *                 canvas->translate(readSize.width() + pad, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, readSize.height() + pad);
   *         }
   *         canvas->restore();
   *         return skiagm::DrawResult::kOk;
   *     }
   * ```
   */
  protected abstract fun <ReadSource> drawRescaleGrid(
    canvas: SkCanvas?,
    src: Source<ReadSource>?,
    srcRect: SkIRect,
    readSize: SkISize,
    type: Type,
    errorMsg: String?,
    pad: Int = TODO(),
  ): DrawResult

  public data class AsyncContext public constructor(
    public var fCalled: Boolean,
    public var fResult: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void AsyncCallback(void* c, std::unique_ptr<const SkImage::AsyncReadResult> result) {
     *         auto context = static_cast<AsyncContext*>(c);
     *         context->fResult = std::move(result);
     *         context->fCalled = true;
     *     }
     * ```
     */
    private fun asyncCallback(c: Unit?, result: SkImage.AsyncReadResult?) {
      TODO("Implement asyncCallback")
    }
  }
}
