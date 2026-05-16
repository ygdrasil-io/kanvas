package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GraphiteReplayGM : public GM {
 * public:
 *     GraphiteReplayGM() = default;
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SK_ColorBLACK);
 *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
 *     }
 *
 *     SkString getName() const override { return SkString("graphite-replay"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kTileWidth * 3, kTileHeight * 2); }
 *
 *     bool onAnimate(double nanos) override {
 *         fStartX = kTileWidth * (1.0f + sinf(nanos * 1e-9)) * 0.5f;
 *         return true;
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 * #if defined(SK_GRAPHITE)
 *         skgpu::graphite::Recorder* recorder = canvas->recorder();
 *         if (recorder) {
 *             this->drawGraphite(canvas, recorder);
 *             return DrawResult::kOk;
 *         }
 * #endif
 *         return this->drawNonGraphite(canvas, errorMsg);
 *     }
 *
 * private:
 *     static constexpr int kImageSize = 128;
 *     static constexpr int kPadding = 2;
 *     static constexpr int kPaddedImageSize = kImageSize + kPadding * 2;
 *     static constexpr int kTileWidth = kPaddedImageSize * 2;
 *     static constexpr int kTileHeight = kPaddedImageSize * 2;
 *
 *     float fStartX = 0.0f;
 *
 *     sk_sp<SkImage> fImage;
 *
 *     void drawContent(SkCanvas* canvas, int y) {
 *         SkPaint gradientPaint;
 *         constexpr SkPoint points[2] = {{0.0f, 0.0f}, {kImageSize, kImageSize}};
 *         constexpr SkColor4f colors[4] = {
 *                 SkColors::kRed, SkColors::kGreen, SkColors::kBlue, SkColors::kRed};
 *         gradientPaint.setShader(SkShaders::LinearGradient(
 *                 points, {{colors, {}, SkTileMode::kClamp}, {}}));
 *
 *         // Draw image.
 *         canvas->drawImage(fImage, kPadding, kPadding + y);
 *
 *         // Draw gradient.
 *         canvas->save();
 *         canvas->translate(kPaddedImageSize + kPadding, kPadding + y);
 *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kImageSize, kImageSize), gradientPaint);
 *         canvas->restore();
 *     }
 *
 *     void drawTile(SkCanvas* canvas) {
 *         // Clip off the right 1/4 of the tile, after clearing.
 *         canvas->clear(SkColors::kRed);
 *         canvas->clipIRect(SkIRect::MakeWH(3 * kTileWidth / 4, kTileHeight));
 *
 *         // Draw content directly.
 *         drawContent(canvas, 0);
 *
 *         // Draw content to a saved layer.
 *         SkPaint pAlpha;
 *         pAlpha.setAlphaf(0.5f);
 *         canvas->saveLayer(nullptr, &pAlpha);
 *         drawContent(canvas, kPaddedImageSize);
 *         canvas->restore();
 *     }
 *
 * #if defined(SK_GRAPHITE)
 *     void drawGraphite(SkCanvas* canvas, skgpu::graphite::Recorder* canvasRecorder) {
 *         SkImageInfo tileImageInfo =
 *                 canvas->imageInfo().makeDimensions(SkISize::Make(kTileWidth, kTileHeight));
 *         skgpu::graphite::TextureInfo textureInfo =
 *                 static_cast<skgpu::graphite::Surface*>(canvas->getSurface())
 *                         ->backingTextureProxy()
 *                         ->textureInfo();
 *
 *         skgpu::graphite::Context* context = canvasRecorder->priv().context();
 *         std::unique_ptr<skgpu::graphite::Recorder> recorder =
 *                 context->makeRecorder(ToolUtils::CreateTestingRecorderOptions());
 *         SkCanvas* recordingCanvas = recorder->makeDeferredCanvas(tileImageInfo, textureInfo);
 *         this->drawTile(recordingCanvas);
 *         std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
 *
 *         // Flush the initial clear added by MakeGraphite.
 *         std::unique_ptr<skgpu::graphite::Recording> canvasRecording = canvasRecorder->snap();
 *         context->insertRecording({canvasRecording.get()});
 *
 *         for (int y = 0; y < 2; ++y) {
 *             for (int x = 0; x < 2; ++x) {
 *                 context->insertRecording(
 *                         {recording.get(),
 *                          canvas->getSurface(),
 *                          {x * kTileWidth + SkScalarRoundToInt(fStartX), y * kTileHeight}});
 *             }
 *         }
 *     }
 * #endif
 *
 *     DrawResult drawNonGraphite(SkCanvas* canvas, SkString* errorMsg) {
 *         SkImageInfo tileImageInfo =
 *                 canvas->imageInfo().makeDimensions(SkISize::Make(kTileWidth, kTileHeight));
 *
 *         sk_sp<SkSurface> imageSurface = canvas->makeSurface(tileImageInfo);
 *         if (!imageSurface) {
 *             *errorMsg = "Cannot create new SkSurface.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         SkCanvas* imageCanvas = imageSurface->getCanvas();
 *         this->drawTile(imageCanvas);
 *         sk_sp<SkImage> image = imageSurface->makeImageSnapshot();
 *
 *         for (int y = 0; y < 2; ++y) {
 *             for (int x = 0; x < 2; ++x) {
 *                 canvas->drawImage(image, x * kTileWidth + fStartX, y * kTileHeight);
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class GraphiteReplayGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kImageSize = 128
   * ```
   */
  private var fStartX: Float = TODO("Initialize fStartX")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kPadding = 2
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SK_ColorBLACK);
   *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("graphite-replay"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kTileWidth * 3, kTileHeight * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fStartX = kTileWidth * (1.0f + sinf(nanos * 1e-9)) * 0.5f;
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   * #if defined(SK_GRAPHITE)
   *         skgpu::graphite::Recorder* recorder = canvas->recorder();
   *         if (recorder) {
   *             this->drawGraphite(canvas, recorder);
   *             return DrawResult::kOk;
   *         }
   * #endif
   *         return this->drawNonGraphite(canvas, errorMsg);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawContent(SkCanvas* canvas, int y) {
   *         SkPaint gradientPaint;
   *         constexpr SkPoint points[2] = {{0.0f, 0.0f}, {kImageSize, kImageSize}};
   *         constexpr SkColor4f colors[4] = {
   *                 SkColors::kRed, SkColors::kGreen, SkColors::kBlue, SkColors::kRed};
   *         gradientPaint.setShader(SkShaders::LinearGradient(
   *                 points, {{colors, {}, SkTileMode::kClamp}, {}}));
   *
   *         // Draw image.
   *         canvas->drawImage(fImage, kPadding, kPadding + y);
   *
   *         // Draw gradient.
   *         canvas->save();
   *         canvas->translate(kPaddedImageSize + kPadding, kPadding + y);
   *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kImageSize, kImageSize), gradientPaint);
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun drawContent(canvas: SkCanvas?, y: Int) {
    TODO("Implement drawContent")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTile(SkCanvas* canvas) {
   *         // Clip off the right 1/4 of the tile, after clearing.
   *         canvas->clear(SkColors::kRed);
   *         canvas->clipIRect(SkIRect::MakeWH(3 * kTileWidth / 4, kTileHeight));
   *
   *         // Draw content directly.
   *         drawContent(canvas, 0);
   *
   *         // Draw content to a saved layer.
   *         SkPaint pAlpha;
   *         pAlpha.setAlphaf(0.5f);
   *         canvas->saveLayer(nullptr, &pAlpha);
   *         drawContent(canvas, kPaddedImageSize);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawTile(canvas: SkCanvas?) {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawGraphite(SkCanvas* canvas, skgpu::graphite::Recorder* canvasRecorder) {
   *         SkImageInfo tileImageInfo =
   *                 canvas->imageInfo().makeDimensions(SkISize::Make(kTileWidth, kTileHeight));
   *         skgpu::graphite::TextureInfo textureInfo =
   *                 static_cast<skgpu::graphite::Surface*>(canvas->getSurface())
   *                         ->backingTextureProxy()
   *                         ->textureInfo();
   *
   *         skgpu::graphite::Context* context = canvasRecorder->priv().context();
   *         std::unique_ptr<skgpu::graphite::Recorder> recorder =
   *                 context->makeRecorder(ToolUtils::CreateTestingRecorderOptions());
   *         SkCanvas* recordingCanvas = recorder->makeDeferredCanvas(tileImageInfo, textureInfo);
   *         this->drawTile(recordingCanvas);
   *         std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
   *
   *         // Flush the initial clear added by MakeGraphite.
   *         std::unique_ptr<skgpu::graphite::Recording> canvasRecording = canvasRecorder->snap();
   *         context->insertRecording({canvasRecording.get()});
   *
   *         for (int y = 0; y < 2; ++y) {
   *             for (int x = 0; x < 2; ++x) {
   *                 context->insertRecording(
   *                         {recording.get(),
   *                          canvas->getSurface(),
   *                          {x * kTileWidth + SkScalarRoundToInt(fStartX), y * kTileHeight}});
   *             }
   *         }
   *     }
   * ```
   */
  private fun drawGraphite(canvas: SkCanvas?, canvasRecorder: Recorder?) {
    TODO("Implement drawGraphite")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult drawNonGraphite(SkCanvas* canvas, SkString* errorMsg) {
   *         SkImageInfo tileImageInfo =
   *                 canvas->imageInfo().makeDimensions(SkISize::Make(kTileWidth, kTileHeight));
   *
   *         sk_sp<SkSurface> imageSurface = canvas->makeSurface(tileImageInfo);
   *         if (!imageSurface) {
   *             *errorMsg = "Cannot create new SkSurface.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         SkCanvas* imageCanvas = imageSurface->getCanvas();
   *         this->drawTile(imageCanvas);
   *         sk_sp<SkImage> image = imageSurface->makeImageSnapshot();
   *
   *         for (int y = 0; y < 2; ++y) {
   *             for (int x = 0; x < 2; ++x) {
   *                 canvas->drawImage(image, x * kTileWidth + fStartX, y * kTileHeight);
   *             }
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  private fun drawNonGraphite(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement drawNonGraphite")
  }

  public companion object {
    private val kImageSize: Int = TODO("Initialize kImageSize")

    private val kPadding: Int = TODO("Initialize kPadding")

    private val kPaddedImageSize: Int = TODO("Initialize kPaddedImageSize")

    private val kTileWidth: Int = TODO("Initialize kTileWidth")

    private val kTileHeight: Int = TODO("Initialize kTileHeight")
  }
}
