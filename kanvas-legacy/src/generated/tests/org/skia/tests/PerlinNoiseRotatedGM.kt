package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PerlinNoiseRotatedGM : public skiagm::GM {
 *     static constexpr SkISize kCellSize = { 100, 100 };
 *     static constexpr SkISize kRectSize = { 60, 60 };
 *     static constexpr int kPad = 10;
 *     static constexpr int kCellsX = 3;
 *     static constexpr int kCellsY = 2;
 *
 *     SkString getName() const override { return SkString("perlinnoise_rotated"); }
 *
 *     SkISize getISize() override {
 *         return {2 * kPad + kCellsX * kCellSize.width(), 2 * kPad + kCellsY * kCellSize.height()};
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint outline;
 *         outline.setColor(SK_ColorBLACK);
 *         outline.setStrokeWidth(2.0f);
 *         outline.setStyle(SkPaint::kStroke_Style);
 *         outline.setAntiAlias(true);
 *
 *         const SkRect kRectToDraw = SkRect::MakeWH(kRectSize.width(), kRectSize.height());
 *         const SkRect kMarker = SkRect::MakeWH(5, 5);
 *
 *         float yOffset = kPad;
 *         for (auto type : { Type::kFractalNoise, Type::kTurbulence }) {
 *             float xOffset = kPad;
 *
 *             SkPaint p;
 *             p.setShader(noise_shader(type, 0.05f, 0.05f, 1, 0, false, kRectSize));
 *
 *             for (float rotation : {0.0f, 10.0f, 80.0f}) {
 *                 int saveCount = canvas->save();
 *                 canvas->translate(xOffset, yOffset);
 *
 *                 canvas->drawRect(SkRect::MakeWH(kCellSize.fWidth, kCellSize.fHeight), outline);
 *
 *                 canvas->save();
 *
 *                 canvas->translate(kCellSize.fWidth / 2.0f, kCellSize.fHeight / 2.0f);
 *                 canvas->rotate(rotation);
 *                 canvas->translate(-kRectSize.fWidth/2.0f, -kRectSize.fHeight/2.0f);
 *
 *                 canvas->drawRect(kRectToDraw, p);
 *
 *                 canvas->drawRect(kRectToDraw, outline);
 *                 canvas->drawRect(kMarker, outline);
 *
 *                 canvas->restoreToCount(saveCount);
 *
 *                 xOffset += kCellSize.width();
 *             }
 *
 *             yOffset += kCellSize.height();
 *         }
 *     }
 * }
 * ```
 */
public open class PerlinNoiseRotatedGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("perlinnoise_rotated"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return {2 * kPad + kCellsX * kCellSize.width(), 2 * kPad + kCellsY * kCellSize.height()};
   *     }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint outline;
   *         outline.setColor(SK_ColorBLACK);
   *         outline.setStrokeWidth(2.0f);
   *         outline.setStyle(SkPaint::kStroke_Style);
   *         outline.setAntiAlias(true);
   *
   *         const SkRect kRectToDraw = SkRect::MakeWH(kRectSize.width(), kRectSize.height());
   *         const SkRect kMarker = SkRect::MakeWH(5, 5);
   *
   *         float yOffset = kPad;
   *         for (auto type : { Type::kFractalNoise, Type::kTurbulence }) {
   *             float xOffset = kPad;
   *
   *             SkPaint p;
   *             p.setShader(noise_shader(type, 0.05f, 0.05f, 1, 0, false, kRectSize));
   *
   *             for (float rotation : {0.0f, 10.0f, 80.0f}) {
   *                 int saveCount = canvas->save();
   *                 canvas->translate(xOffset, yOffset);
   *
   *                 canvas->drawRect(SkRect::MakeWH(kCellSize.fWidth, kCellSize.fHeight), outline);
   *
   *                 canvas->save();
   *
   *                 canvas->translate(kCellSize.fWidth / 2.0f, kCellSize.fHeight / 2.0f);
   *                 canvas->rotate(rotation);
   *                 canvas->translate(-kRectSize.fWidth/2.0f, -kRectSize.fHeight/2.0f);
   *
   *                 canvas->drawRect(kRectToDraw, p);
   *
   *                 canvas->drawRect(kRectToDraw, outline);
   *                 canvas->drawRect(kMarker, outline);
   *
   *                 canvas->restoreToCount(saveCount);
   *
   *                 xOffset += kCellSize.width();
   *             }
   *
   *             yOffset += kCellSize.height();
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kCellSize: SkISize = TODO("Initialize kCellSize")

    private val kRectSize: SkISize = TODO("Initialize kRectSize")

    private val kPad: Int = TODO("Initialize kPad")

    private val kCellsX: Int = TODO("Initialize kCellsX")

    private val kCellsY: Int = TODO("Initialize kCellsY")
  }
}
