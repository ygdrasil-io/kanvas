package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnalyticGradientShaderGM : public skiagm::GM {
 * public:
 *     AnalyticGradientShaderGM() {
 *
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("analytic_gradients"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1024, 512); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkPoint points[2] = { SkPoint::Make(0, 0), SkPoint::Make(RECT_WIDTH, 0.0) };
 *
 *         for (int cellRow = 0; cellRow < NUM_ROWS; cellRow++) {
 *             // Each interval has 4 different color counts, one per mode
 *             const int* colorCounts = INTERVAL_COLOR_COUNTS[cellRow]; // Has len = 4
 *
 *             for (int cellCol = 0; cellCol < NUM_COLS; cellCol++) {
 *                 // create_gradient_points(cellRow, cellCol, points);
 *
 *                 // Get the color count dependent on interval and mode
 *                 size_t colorCount = colorCounts[cellCol];
 *                 // Get the positions given the mode
 *                 const int* layout = M_POSITIONS[cellCol];
 *
 *                 // Collect positions and colors specific to the interval+mode normalizing the
 *                 // position based on the interval count (== cellRow+1)
 *                 std::vector<SkColor4f> colors(colorCount);
 *                 std::vector<float> positions(colorCount);
 *                 for (size_t i = 0; i < colorCount; i++) {
 *                     positions[i] = SkIntToScalar(layout[i]) / (cellRow + 1);
 *                     colors[i] = SkColor4f::FromColor(COLORS[i % COLOR_COUNT]);
 *                 }
 *
 *                 SkGradient grad = {{colors, positions, SkTileMode::kClamp, nullptr}, {}};
 *                 auto shader = SkShaders::LinearGradient(points, grad);
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
public open class AnalyticGradientShaderGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("analytic_gradients"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1024, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkPoint points[2] = { SkPoint::Make(0, 0), SkPoint::Make(RECT_WIDTH, 0.0) };
   *
   *         for (int cellRow = 0; cellRow < NUM_ROWS; cellRow++) {
   *             // Each interval has 4 different color counts, one per mode
   *             const int* colorCounts = INTERVAL_COLOR_COUNTS[cellRow]; // Has len = 4
   *
   *             for (int cellCol = 0; cellCol < NUM_COLS; cellCol++) {
   *                 // create_gradient_points(cellRow, cellCol, points);
   *
   *                 // Get the color count dependent on interval and mode
   *                 size_t colorCount = colorCounts[cellCol];
   *                 // Get the positions given the mode
   *                 const int* layout = M_POSITIONS[cellCol];
   *
   *                 // Collect positions and colors specific to the interval+mode normalizing the
   *                 // position based on the interval count (== cellRow+1)
   *                 std::vector<SkColor4f> colors(colorCount);
   *                 std::vector<float> positions(colorCount);
   *                 for (size_t i = 0; i < colorCount; i++) {
   *                     positions[i] = SkIntToScalar(layout[i]) / (cellRow + 1);
   *                     colors[i] = SkColor4f::FromColor(COLORS[i % COLOR_COUNT]);
   *                 }
   *
   *                 SkGradient grad = {{colors, positions, SkTileMode::kClamp, nullptr}, {}};
   *                 auto shader = SkShaders::LinearGradient(points, grad);
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
