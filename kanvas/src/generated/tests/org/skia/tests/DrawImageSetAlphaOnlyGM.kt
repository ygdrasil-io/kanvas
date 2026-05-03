package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class DrawImageSetAlphaOnlyGM : public GM {
 * private:
 *     SkString getName() const override { return SkString("draw_image_set_alpha_only"); }
 *     SkISize getISize() override { return {kM * kTileW, 2 * kN * kTileH}; }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString*, GraphiteTestContext*) override {
 *         auto recorder = canvas->baseRecorder();
 *         static constexpr SkColor4f kColors[] = {SkColors::kBlue, SkColors::kTransparent,
 *                                               SkColors::kRed,  SkColors::kTransparent};
 *         static constexpr SkColor kBGColor = SkColorSetARGB(128, 128, 128, 128);
 *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet, kBGColor);
 *
 *         // Modify the alpha of the entries, decreasing by column, and convert even rows to
 *         // alpha-only textures.
 *         sk_sp<SkColorSpace> alphaSpace = SkColorSpace::MakeSRGB();
 *         for (int y = 0; y < kN; ++y) {
 *             for (int x = 0; x < kM; ++x) {
 *                 int i = y * kM + x;
 *                 fSet[i].fAlpha = (kM - x) / (float) kM;
 *                 if (y % 2 == 0) {
 *                     fSet[i].fImage = fSet[i].fImage->makeColorTypeAndColorSpace(
 *                             recorder, kAlpha_8_SkColorType, alphaSpace, {});
 *                 }
 *             }
 *         }
 *         return skiagm::DrawResult::kOk;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         ToolUtils::draw_checkerboard(canvas, SK_ColorGRAY, SK_ColorDKGRAY, 25);
 *
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *         paint.setColor4f({0.2f, 0.8f, 0.4f, 1.f}); // colorizes even rows, no effect on odd rows
 *
 *         // Top rows use experimental edge set API
 *         canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr,
 *                                                 SkSamplingOptions(SkFilterMode::kLinear), &paint,
 *                                                 SkCanvas::kFast_SrcRectConstraint);
 *
 *         canvas->translate(0.f, kN * kTileH);
 *
 *         // Bottom rows draw each image from the set using the regular API
 *         for (int y = 0; y < kN; ++y) {
 *             for (int x = 0; x < kM; ++x) {
 *                 int i = y * kM + x;
 *                 SkPaint entryPaint = paint;
 *                 entryPaint.setAlphaf(fSet[i].fAlpha * paint.getAlphaf());
 *                 sk_sp<SkImage> orig = sk_ref_sp(const_cast<SkImage*>(fSet[i].fImage.get()));
 *                 canvas->drawImageRect(ToolUtils::MakeTextureImage(canvas, std::move(orig)),
 *                                       fSet[i].fSrcRect, fSet[i].fDstRect,
 *                                       SkSamplingOptions(), &entryPaint,
 *                                       SkCanvas::kFast_SrcRectConstraint);
 *             }
 *         }
 *     }
 *
 *     inline static constexpr int kM = 4;
 *     inline static constexpr int kN = 4;
 *     inline static constexpr int kTileW = 50;
 *     inline static constexpr int kTileH = 50;
 *     SkCanvas::ImageSetEntry fSet[kM * kN];
 * }
 * ```
 */
public open class DrawImageSetAlphaOnlyGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kM = 4
   * ```
   */
  private var fSet: Array<SkCanvas.ImageSetEntry> = TODO("Initialize fSet")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("draw_image_set_alpha_only"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {kM * kTileW, 2 * kN * kTileH}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString*, GraphiteTestContext*) override {
   *         auto recorder = canvas->baseRecorder();
   *         static constexpr SkColor4f kColors[] = {SkColors::kBlue, SkColors::kTransparent,
   *                                               SkColors::kRed,  SkColors::kTransparent};
   *         static constexpr SkColor kBGColor = SkColorSetARGB(128, 128, 128, 128);
   *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet, kBGColor);
   *
   *         // Modify the alpha of the entries, decreasing by column, and convert even rows to
   *         // alpha-only textures.
   *         sk_sp<SkColorSpace> alphaSpace = SkColorSpace::MakeSRGB();
   *         for (int y = 0; y < kN; ++y) {
   *             for (int x = 0; x < kM; ++x) {
   *                 int i = y * kM + x;
   *                 fSet[i].fAlpha = (kM - x) / (float) kM;
   *                 if (y % 2 == 0) {
   *                     fSet[i].fImage = fSet[i].fImage->makeColorTypeAndColorSpace(
   *                             recorder, kAlpha_8_SkColorType, alphaSpace, {});
   *                 }
   *             }
   *         }
   *         return skiagm::DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onGpuSetup(
    canvas: SkCanvas?,
    param1: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         ToolUtils::draw_checkerboard(canvas, SK_ColorGRAY, SK_ColorDKGRAY, 25);
   *
   *         SkPaint paint;
   *         paint.setBlendMode(SkBlendMode::kSrcOver);
   *         paint.setColor4f({0.2f, 0.8f, 0.4f, 1.f}); // colorizes even rows, no effect on odd rows
   *
   *         // Top rows use experimental edge set API
   *         canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr,
   *                                                 SkSamplingOptions(SkFilterMode::kLinear), &paint,
   *                                                 SkCanvas::kFast_SrcRectConstraint);
   *
   *         canvas->translate(0.f, kN * kTileH);
   *
   *         // Bottom rows draw each image from the set using the regular API
   *         for (int y = 0; y < kN; ++y) {
   *             for (int x = 0; x < kM; ++x) {
   *                 int i = y * kM + x;
   *                 SkPaint entryPaint = paint;
   *                 entryPaint.setAlphaf(fSet[i].fAlpha * paint.getAlphaf());
   *                 sk_sp<SkImage> orig = sk_ref_sp(const_cast<SkImage*>(fSet[i].fImage.get()));
   *                 canvas->drawImageRect(ToolUtils::MakeTextureImage(canvas, std::move(orig)),
   *                                       fSet[i].fSrcRect, fSet[i].fDstRect,
   *                                       SkSamplingOptions(), &entryPaint,
   *                                       SkCanvas::kFast_SrcRectConstraint);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kM: Int = TODO("Initialize kM")

    private val kN: Int = TODO("Initialize kN")

    private val kTileW: Int = TODO("Initialize kTileW")

    private val kTileH: Int = TODO("Initialize kTileH")
  }
}
