package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkWStream
import org.skia.gpu.Recorder
import org.skia.tools.SkCommandLineConfigGraphite
import org.skia.tools.TestOptions

/**
 * C++ original:
 * ```cpp
 * class GraphiteSink : public Sink {
 * public:
 *     GraphiteSink(const SkCommandLineConfigGraphite*, const skiatest::graphite::TestOptions&);
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     bool serial() const override { return true; }
 *     const char* fileExtension() const override { return "png"; }
 *     SinkFlags flags() const override { return SinkFlags{SinkFlags::kGPU, SinkFlags::kDirect}; }
 *     void setColorSpace(sk_sp<SkColorSpace> colorSpace) override { fColorSpace = colorSpace; }
 *     SkColorInfo colorInfo() const override {
 *         return SkColorInfo(fColorType, fAlphaType, fColorSpace);
 *     }
 *
 * protected:
 *     sk_sp<SkSurface> makeSurface(skgpu::graphite::Recorder*, const Src&) const;
 *
 *     skiatest::graphite::TestOptions fOptions;
 *     skgpu::ContextType fContextType;
 *     SkColorType fColorType;
 *     SkAlphaType fAlphaType;
 *     sk_sp<SkColorSpace> fColorSpace;
 * }
 * ```
 */
public open class GraphiteSink public constructor(
  param0: SkCommandLineConfigGraphite,
  param1: TestOptions,
) : Sink() {
  /**
   * C++ original:
   * ```cpp
   * skiatest::graphite::TestOptions fOptions
   * ```
   */
  protected var fOptions: Int = TODO("Initialize fOptions")

  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType fContextType
   * ```
   */
  protected var fContextType: Int = TODO("Initialize fContextType")

  /**
   * C++ original:
   * ```cpp
   * SkColorType fColorType
   * ```
   */
  protected var fColorType: Int = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType fAlphaType
   * ```
   */
  protected var fAlphaType: Int = TODO("Initialize fAlphaType")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fColorSpace
   * ```
   */
  protected var fColorSpace: Int = TODO("Initialize fColorSpace")

  /**
   * C++ original:
   * ```cpp
   * GraphiteSink(const SkCommandLineConfigGraphite*, const skiatest::graphite::TestOptions&)
   * ```
   */
  public constructor(config: SkCommandLineConfigGraphite?, options: TestOptions) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result GraphiteSink::draw(const Src& src,
   *                           SkBitmap* dst,
   *                           SkWStream* dstStream,
   *                           SkString* log) const {
   *     skiatest::graphite::TestOptions options = fOptions;
   *     // If we've copied context options from an external source we can't trust that the
   *     // priv pointer is still in scope, so assume it should be NULL and set our own up.
   *     SkASSERT(!options.fContextOptions.fOptionsPriv);
   *     skgpu::graphite::ContextOptionsPriv optionsPriv;
   *     options.fContextOptions.fOptionsPriv = &optionsPriv;
   *
   *     // We don't expect the src to mess with the more esoteric options
   *     SkDEBUGCODE(auto cache = options.fContextOptions.fPersistentPipelineStorage);
   *     SkDEBUGCODE(auto exec = options.fContextOptions.fExecutor);
   *     SkDEBUGCODE(auto cbContext = options.fContextOptions.fPipelineCallbackContext);
   *     SkDEBUGCODE(auto cb1 = options.fContextOptions.fPipelineCallback);
   *     SkDEBUGCODE(auto cb2 = options.fContextOptions.fPipelineCachingCallback);
   *     src.modifyGraphiteContextOptions(&options.fContextOptions);
   *     SkASSERT(cache == options.fContextOptions.fPersistentPipelineStorage);
   *     SkASSERT(exec == options.fContextOptions.fExecutor);
   *     SkASSERT(cbContext == options.fContextOptions.fPipelineCallbackContext);
   *     SkASSERT(cb1 == options.fContextOptions.fPipelineCallback);
   *     SkASSERT(cb2 == options.fContextOptions.fPipelineCachingCallback);
   *
   *     skiatest::graphite::ContextFactory factory(options);
   *     skiatest::graphite::ContextInfo ctxInfo = factory.getContextInfo(fContextType);
   *     skgpu::graphite::Context* context = ctxInfo.fContext;
   *     if (!context) {
   *         return Result::Fatal("Could not create a context.");
   *     }
   *
   *     std::unique_ptr<skgpu::graphite::Recorder> recorder =
   *                                 context->makeRecorder(ToolUtils::CreateTestingRecorderOptions());
   *     if (!recorder) {
   *         return Result::Fatal("Could not create a recorder.");
   *     }
   *
   *     {
   *         sk_sp<SkSurface> surface = this->makeSurface(recorder.get(), src);
   *         if (!surface) {
   *             return Result::Fatal("Could not create a surface.");
   *         }
   *         dst->allocPixels(surface->imageInfo());
   *         Result result = src.draw(surface->getCanvas(), ctxInfo.fTestContext);
   *         if (!result.isOk()) {
   *             return result;
   *         }
   *
   *         SkPixmap pm;
   *         if (!dst->peekPixels(&pm) ||
   *             !surface->readPixels(pm, 0, 0)) {
   *             return Result::Fatal("Could not readback from surface.");
   *         }
   *     }
   *
   *     std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
   *     if (!recording) {
   *         return Result::Fatal("Could not create a recording.");
   *     }
   *
   *     skgpu::graphite::InsertRecordingInfo info;
   *     info.fRecording = recording.get();
   *     if (!context->insertRecording(info)) {
   *         return Result::Fatal("Context::insertRecording failed.");
   *     }
   *     ctxInfo.fTestContext->syncedSubmit(context);
   *
   *     if (options.fContextOptions.fPersistentPipelineStorage) {
   *         context->syncPipelineData();
   *     }
   *
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    dst: SkBitmap?,
    dstStream: SkWStream?,
    log: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool serial() const override { return true; }
   * ```
   */
  public override fun serial(): Boolean {
    TODO("Implement serial")
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
   * SinkFlags flags() const override { return SinkFlags{SinkFlags::kGPU, SinkFlags::kDirect}; }
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
   *         return SkColorInfo(fColorType, fAlphaType, fColorSpace);
   *     }
   * ```
   */
  public override fun colorInfo(): Int {
    TODO("Implement colorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> GraphiteSink::makeSurface(skgpu::graphite::Recorder* recorder,
   *                                            const Src& src) const {
   *     SkSurfaceProps props(0, kRGB_H_SkPixelGeometry);
   *     src.modifySurfaceProps(&props);
   *     auto ii = SkImageInfo::Make(src.size(), this->colorInfo());
   *
   * #if defined(SK_DAWN)
   *     if (fOptions.fUseWGPUTextureView) {
   *         return sk_gpu_test::MakeBackendTextureViewSurface(recorder,
   *                                                           ii,
   *                                                           skgpu::Mipmapped::kNo,
   *                                                           skgpu::Protected::kNo,
   *                                                           &props);
   *     }
   * #endif // SK_DAWN
   *
   *     return SkSurfaces::RenderTarget(recorder, ii, skgpu::Mipmapped::kNo, &props);
   * }
   * ```
   */
  protected fun makeSurface(recorder: Recorder?, src: Src): Int {
    TODO("Implement makeSurface")
  }
}
