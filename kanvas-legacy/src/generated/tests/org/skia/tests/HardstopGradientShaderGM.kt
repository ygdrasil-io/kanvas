package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HardstopGradientShaderGM : public skiagm::GM {
 * public:
 *     HardstopGradientShaderGM() {
 *
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("hardstop_gradients"); }
 *
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPoint points[2];
 *
 *         SkColor4f colors[] = {
 *             SkColors::kRed,
 *             SkColors::kGreen,
 *             SkColors::kBlue,
 *             SkColors::kYellow,
 *             SkColors::kMagenta,
 *         };
 *
 *         SkScalar row3[] = {0.00f, 0.25f, 1.00f};
 *         SkScalar row4[] = {0.00f, 0.25f, 0.50f, 0.50f, 1.00f};
 *         SkScalar row5[] = {0.00f, 0.50f, 0.50f, 1.00f};
 *         SkScalar row6[] = {0.00f, 0.00f, 1.00f};
 *         SkScalar row7[] = {0.00f, 1.00f, 1.00f};
 *         SkScalar row8[] = {0.00f, 0.30f, 0.30f, 1.00f};
 *
 *         SkScalar* positions[NUM_ROWS] = {
 *             nullptr,
 *             nullptr,
 *             row3,
 *             row4,
 *             row5,
 *             row6,
 *             row7,
 *             row8,
 *         };
 *
 *         int numGradientColors[NUM_ROWS] = {
 *             2,
 *             3,
 *             3,
 *             5,
 *             4,
 *             3,
 *             3,
 *             4,
 *         };
 *
 *         SkTileMode tilemodes[NUM_COLS] = {
 *             SkTileMode::kClamp,
 *             SkTileMode::kRepeat,
 *             SkTileMode::kMirror,
 *         };
 *
 *         for (int cellRow = 0; cellRow < NUM_ROWS; cellRow++) {
 *             for (int cellCol = 0; cellCol < NUM_COLS; cellCol++) {
 *                 create_gradient_points(cellRow, cellCol, points);
 *
 *                 size_t n = numGradientColors[cellRow];
 *                 SkSpan<const float> pos;
 *                 if (positions[cellRow]) {
 *                     pos = {positions[cellRow], n};
 *                 }
 *                 auto shader = SkShaders::LinearGradient(
 *                                 points, {{{colors, n}, pos, tilemodes[cellCol]}, {}});
 *
 *                 shade_rect(canvas, shader, cellRow, cellCol);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class HardstopGradientShaderGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hardstop_gradients"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(512, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPoint points[2];
   *
   *         SkColor4f colors[] = {
   *             SkColors::kRed,
   *             SkColors::kGreen,
   *             SkColors::kBlue,
   *             SkColors::kYellow,
   *             SkColors::kMagenta,
   *         };
   *
   *         SkScalar row3[] = {0.00f, 0.25f, 1.00f};
   *         SkScalar row4[] = {0.00f, 0.25f, 0.50f, 0.50f, 1.00f};
   *         SkScalar row5[] = {0.00f, 0.50f, 0.50f, 1.00f};
   *         SkScalar row6[] = {0.00f, 0.00f, 1.00f};
   *         SkScalar row7[] = {0.00f, 1.00f, 1.00f};
   *         SkScalar row8[] = {0.00f, 0.30f, 0.30f, 1.00f};
   *
   *         SkScalar* positions[NUM_ROWS] = {
   *             nullptr,
   *             nullptr,
   *             row3,
   *             row4,
   *             row5,
   *             row6,
   *             row7,
   *             row8,
   *         };
   *
   *         int numGradientColors[NUM_ROWS] = {
   *             2,
   *             3,
   *             3,
   *             5,
   *             4,
   *             3,
   *             3,
   *             4,
   *         };
   *
   *         SkTileMode tilemodes[NUM_COLS] = {
   *             SkTileMode::kClamp,
   *             SkTileMode::kRepeat,
   *             SkTileMode::kMirror,
   *         };
   *
   *         for (int cellRow = 0; cellRow < NUM_ROWS; cellRow++) {
   *             for (int cellCol = 0; cellCol < NUM_COLS; cellCol++) {
   *                 create_gradient_points(cellRow, cellCol, points);
   *
   *                 size_t n = numGradientColors[cellRow];
   *                 SkSpan<const float> pos;
   *                 if (positions[cellRow]) {
   *                     pos = {positions[cellRow], n};
   *                 }
   *                 auto shader = SkShaders::LinearGradient(
   *                                 points, {{{colors, n}, pos, tilemodes[cellCol]}, {}});
   *
   *                 shade_rect(canvas, shader, cellRow, cellCol);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
