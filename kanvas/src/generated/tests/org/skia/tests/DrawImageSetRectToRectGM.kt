package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawImageSetRectToRectGM : public GM {
 * private:
 *     SkString getName() const override { return SkString("draw_image_set_rect_to_rect"); }
 *     SkISize getISize() override { return {1250, 850}; }
 *     void onOnceBeforeDraw() override {
 *         static constexpr SkColor4f kColors[] = {SkColors::kBlue, SkColors::kWhite,
 *                                               SkColors::kRed,  SkColors::kWhite};
 *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         ToolUtils::draw_checkerboard(canvas, SK_ColorBLACK, SK_ColorWHITE, 50);
 *         static constexpr SkScalar kW = kM * kTileW;
 *         static constexpr SkScalar kH = kN * kTileH;
 *         SkMatrix matrices[5];
 *         // Identity
 *         matrices[0].reset();
 *         // 90 degree rotation
 *         matrices[1].setRotate(90, kW / 2.f, kH / 2.f);
 *         // Scaling
 *         matrices[2].setScale(2.f, 0.5f);
 *         // Mirror in x and y
 *         matrices[3].setScale(-1.f, -1.f);
 *         matrices[3].postTranslate(kW, kH);
 *         // Mirror in y, rotate, and scale.
 *         matrices[4].setScale(1.f, -1.f);
 *         matrices[4].postTranslate(0, kH);
 *         matrices[4].postRotate(90, kW / 2.f, kH / 2.f);
 *         matrices[4].postScale(2.f, 0.5f);
 *
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *
 *         static constexpr SkScalar kTranslate = std::max(kW, kH) * 2.f + 10.f;
 *         canvas->translate(5.f, 5.f);
 *         canvas->save();
 *         for (SkScalar frac : {0.f, 0.5f}) {
 *             canvas->save();
 *             canvas->translate(frac, frac);
 *             for (size_t m = 0; m < std::size(matrices); ++m) {
 *                 canvas->save();
 *                 canvas->concat(matrices[m]);
 *                 canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr,
 *                                                         SkSamplingOptions(SkFilterMode::kLinear),
 *                                                         &paint, SkCanvas::kFast_SrcRectConstraint);
 *                 canvas->restore();
 *                 canvas->translate(kTranslate, 0);
 *             }
 *             canvas->restore();
 *             canvas->restore();
 *             canvas->translate(0, kTranslate);
 *             canvas->save();
 *         }
 *         for (SkVector scale : {SkVector{2.f, 0.5f}, SkVector{0.5, 2.f}}) {
 *             SkCanvas::ImageSetEntry scaledSet[kM * kN];
 *             std::copy_n(fSet, kM * kN, scaledSet);
 *             for (int i = 0; i < kM * kN; ++i) {
 *                 scaledSet[i].fDstRect.fLeft *= scale.fX;
 *                 scaledSet[i].fDstRect.fTop *= scale.fY;
 *                 scaledSet[i].fDstRect.fRight *= scale.fX;
 *                 scaledSet[i].fDstRect.fBottom *= scale.fY;
 *                 scaledSet[i].fAlpha = 0 == (i % 3) ? 0.4f : 1.f;
 *             }
 *             for (size_t m = 0; m < std::size(matrices); ++m) {
 *                 canvas->save();
 *                 canvas->concat(matrices[m]);
 *                 canvas->experimental_DrawEdgeAAImageSet(scaledSet, kM * kN, nullptr, nullptr,
 *                                                         SkSamplingOptions(SkFilterMode::kLinear),
 *                                                         &paint, SkCanvas::kFast_SrcRectConstraint);
 *                 canvas->restore();
 *                 canvas->translate(kTranslate, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, kTranslate);
 *             canvas->save();
 *         }
 *     }
 *     inline static constexpr int kM = 2;
 *     inline static constexpr int kN = 2;
 *     inline static constexpr int kTileW = 40;
 *     inline static constexpr int kTileH = 50;
 *     SkCanvas::ImageSetEntry fSet[kM * kN];
 * }
 * ```
 */
public open class DrawImageSetRectToRectGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kM = 2
   * ```
   */
  private var fSet: Array<SkCanvas.ImageSetEntry> = TODO("Initialize fSet")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("draw_image_set_rect_to_rect"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1250, 850}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         static constexpr SkColor4f kColors[] = {SkColors::kBlue, SkColors::kWhite,
   *                                               SkColors::kRed,  SkColors::kWhite};
   *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         ToolUtils::draw_checkerboard(canvas, SK_ColorBLACK, SK_ColorWHITE, 50);
   *         static constexpr SkScalar kW = kM * kTileW;
   *         static constexpr SkScalar kH = kN * kTileH;
   *         SkMatrix matrices[5];
   *         // Identity
   *         matrices[0].reset();
   *         // 90 degree rotation
   *         matrices[1].setRotate(90, kW / 2.f, kH / 2.f);
   *         // Scaling
   *         matrices[2].setScale(2.f, 0.5f);
   *         // Mirror in x and y
   *         matrices[3].setScale(-1.f, -1.f);
   *         matrices[3].postTranslate(kW, kH);
   *         // Mirror in y, rotate, and scale.
   *         matrices[4].setScale(1.f, -1.f);
   *         matrices[4].postTranslate(0, kH);
   *         matrices[4].postRotate(90, kW / 2.f, kH / 2.f);
   *         matrices[4].postScale(2.f, 0.5f);
   *
   *         SkPaint paint;
   *         paint.setBlendMode(SkBlendMode::kSrcOver);
   *
   *         static constexpr SkScalar kTranslate = std::max(kW, kH) * 2.f + 10.f;
   *         canvas->translate(5.f, 5.f);
   *         canvas->save();
   *         for (SkScalar frac : {0.f, 0.5f}) {
   *             canvas->save();
   *             canvas->translate(frac, frac);
   *             for (size_t m = 0; m < std::size(matrices); ++m) {
   *                 canvas->save();
   *                 canvas->concat(matrices[m]);
   *                 canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr,
   *                                                         SkSamplingOptions(SkFilterMode::kLinear),
   *                                                         &paint, SkCanvas::kFast_SrcRectConstraint);
   *                 canvas->restore();
   *                 canvas->translate(kTranslate, 0);
   *             }
   *             canvas->restore();
   *             canvas->restore();
   *             canvas->translate(0, kTranslate);
   *             canvas->save();
   *         }
   *         for (SkVector scale : {SkVector{2.f, 0.5f}, SkVector{0.5, 2.f}}) {
   *             SkCanvas::ImageSetEntry scaledSet[kM * kN];
   *             std::copy_n(fSet, kM * kN, scaledSet);
   *             for (int i = 0; i < kM * kN; ++i) {
   *                 scaledSet[i].fDstRect.fLeft *= scale.fX;
   *                 scaledSet[i].fDstRect.fTop *= scale.fY;
   *                 scaledSet[i].fDstRect.fRight *= scale.fX;
   *                 scaledSet[i].fDstRect.fBottom *= scale.fY;
   *                 scaledSet[i].fAlpha = 0 == (i % 3) ? 0.4f : 1.f;
   *             }
   *             for (size_t m = 0; m < std::size(matrices); ++m) {
   *                 canvas->save();
   *                 canvas->concat(matrices[m]);
   *                 canvas->experimental_DrawEdgeAAImageSet(scaledSet, kM * kN, nullptr, nullptr,
   *                                                         SkSamplingOptions(SkFilterMode::kLinear),
   *                                                         &paint, SkCanvas::kFast_SrcRectConstraint);
   *                 canvas->restore();
   *                 canvas->translate(kTranslate, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, kTranslate);
   *             canvas->save();
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
