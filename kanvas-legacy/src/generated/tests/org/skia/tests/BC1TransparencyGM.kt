package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class BC1TransparencyGM : public GM {
 * public:
 *     BC1TransparencyGM() {
 *         this->setBGColor(SK_ColorGREEN);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("bc1_transparency"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kImgWidth + 2 * kPad, 2 * kImgHeight + 3 * kPad);
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 * #if defined(SK_GANESH)
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext && dContext->abandoned()) {
 *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
 *             // if forbidden.
 *             return DrawResult::kSkip;
 *         }
 * #else
 *         constexpr GrDirectContext* dContext = nullptr;
 * #endif
 *
 *         sk_sp<SkData> bc1Data = make_compressed_data();
 *
 *         fRGBImage = data_to_img(dContext, bc1Data, SkTextureCompressionType::kBC1_RGB8_UNORM);
 *         fRGBAImage = data_to_img(dContext, std::move(bc1Data),
 *                                  SkTextureCompressionType::kBC1_RGBA8_UNORM);
 *         if (!fRGBImage || !fRGBAImage) {
 *             *errorMsg = "Failed to create BC1 images.";
 *             return DrawResult::kFail;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override {
 *         fRGBImage = nullptr;
 *         fRGBAImage = nullptr;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         draw_image(canvas, fRGBImage, kPad, kPad);
 *         draw_image(canvas, fRGBAImage, kPad, 2 * kPad + kImgHeight);
 *     }
 *
 * private:
 *     sk_sp<SkImage> fRGBImage;
 *     sk_sp<SkImage> fRGBAImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BC1TransparencyGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fRGBImage
   * ```
   */
  private var fRGBImage: SkSp<SkImage> = TODO("Initialize fRGBImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fRGBAImage
   * ```
   */
  private var fRGBAImage: SkSp<SkImage> = TODO("Initialize fRGBAImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bc1_transparency"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(kImgWidth + 2 * kPad, 2 * kImgHeight + 3 * kPad);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   * #if defined(SK_GANESH)
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext && dContext->abandoned()) {
   *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
   *             // if forbidden.
   *             return DrawResult::kSkip;
   *         }
   * #else
   *         constexpr GrDirectContext* dContext = nullptr;
   * #endif
   *
   *         sk_sp<SkData> bc1Data = make_compressed_data();
   *
   *         fRGBImage = data_to_img(dContext, bc1Data, SkTextureCompressionType::kBC1_RGB8_UNORM);
   *         fRGBAImage = data_to_img(dContext, std::move(bc1Data),
   *                                  SkTextureCompressionType::kBC1_RGBA8_UNORM);
   *         if (!fRGBImage || !fRGBAImage) {
   *             *errorMsg = "Failed to create BC1 images.";
   *             return DrawResult::kFail;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onGpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override {
   *         fRGBImage = nullptr;
   *         fRGBAImage = nullptr;
   *     }
   * ```
   */
  protected override fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         draw_image(canvas, fRGBImage, kPad, kPad);
   *         draw_image(canvas, fRGBAImage, kPad, 2 * kPad + kImgHeight);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
