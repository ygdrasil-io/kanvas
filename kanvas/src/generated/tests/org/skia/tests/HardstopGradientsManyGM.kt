package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HardstopGradientsManyGM : public skiagm::GM {
 * public:
 *     HardstopGradientsManyGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("hardstop_gradients_many"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static constexpr SkPoint points[] = {
 *             SkPoint::Make(0,      kRectHeight / 2),
 *             SkPoint::Make(kWidth, kRectHeight / 2),
 *         };
 *
 *         std::vector<SkColor4f> colors;
 *         std::vector<float> positions;
 *
 *         for (int row = 1; row <= kNumRows; ++row) {
 *             // Assemble a gradient containing a blue-to-white blend, repeated N times per row.
 *             colors.push_back(SkColors::kBlue);
 *             colors.push_back(SkColors::kWhite);
 *
 *             positions = {0.0f};
 *             for (int pos = 1; pos < row; ++pos) {
 *                 float place = SkScalar(pos) / SkScalar(row);
 *                 positions.push_back(place);
 *                 positions.push_back(place);
 *             }
 *             positions.push_back(1.0f);
 *             SkASSERT(positions.size() == colors.size());
 *
 *             // Draw it.
 *             auto shader = SkShaders::LinearGradient(points,
 *                                                     {{colors, positions, SkTileMode::kClamp}, {}});
 *             SkPaint paint;
 *             paint.setShader(shader);
 *             canvas->drawRect(SkRect::MakeXYWH(0, kPadHeight, kWidth, kRectHeight), paint);
 *
 *             canvas->translate(0, kCellHeight);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class HardstopGradientsManyGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hardstop_gradients_many"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static constexpr SkPoint points[] = {
   *             SkPoint::Make(0,      kRectHeight / 2),
   *             SkPoint::Make(kWidth, kRectHeight / 2),
   *         };
   *
   *         std::vector<SkColor4f> colors;
   *         std::vector<float> positions;
   *
   *         for (int row = 1; row <= kNumRows; ++row) {
   *             // Assemble a gradient containing a blue-to-white blend, repeated N times per row.
   *             colors.push_back(SkColors::kBlue);
   *             colors.push_back(SkColors::kWhite);
   *
   *             positions = {0.0f};
   *             for (int pos = 1; pos < row; ++pos) {
   *                 float place = SkScalar(pos) / SkScalar(row);
   *                 positions.push_back(place);
   *                 positions.push_back(place);
   *             }
   *             positions.push_back(1.0f);
   *             SkASSERT(positions.size() == colors.size());
   *
   *             // Draw it.
   *             auto shader = SkShaders::LinearGradient(points,
   *                                                     {{colors, positions, SkTileMode::kClamp}, {}});
   *             SkPaint paint;
   *             paint.setShader(shader);
   *             canvas->drawRect(SkRect::MakeXYWH(0, kPadHeight, kWidth, kRectHeight), paint);
   *
   *             canvas->translate(0, kCellHeight);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
