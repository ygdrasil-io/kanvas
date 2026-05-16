package org.skia.tests

import kotlin.Float
import kotlin.String
import kotlin.initializer_list
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FillrectGradientGM : public skiagm::GM {
 * public:
 *     FillrectGradientGM() {}
 *
 * protected:
 *     struct GradientStop {
 *         float pos;
 *         SkColor color;
 *     };
 *
 *     SkString getName() const override { return SkString("fillrect_gradient"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kNumColumns * (kCellSize + kPadSize),
 *                              kNumRows * (kCellSize + kPadSize));
 *     }
 *
 *     void drawGradient(SkCanvas* canvas, std::initializer_list<GradientStop> stops) {
 *         std::vector<SkColor4f> colors;
 *         std::vector<SkScalar> positions;
 *         colors.reserve(stops.size());
 *         positions.reserve(stops.size());
 *
 *         for (const GradientStop& stop : stops) {
 *             colors.push_back(SkColor4f::FromColor(stop.color));
 *             positions.push_back(stop.pos);
 *         }
 *
 *         static constexpr SkPoint points[] = {
 *             SkPoint::Make(kCellSize, 0),
 *             SkPoint::Make(kCellSize, kCellSize),
 *         };
 *
 *         // Draw the gradient linearly.
 *         sk_sp<SkShader> shader = SkShaders::LinearGradient(points,
 *                                                    {{colors, positions, SkTileMode::kClamp}, {}});
 *         SkPaint paint;
 *         paint.setShader(shader);
 *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kCellSize, kCellSize), paint);
 *
 *         canvas->save();
 *         canvas->translate(kCellSize + kPadSize, 0);
 *
 *         // Draw the gradient radially.
 *         shader = SkShaders::RadialGradient(SkPoint::Make(kCellSize / 2, kCellSize / 2),
 *                                            kCellSize / 2,
 *                                            {{colors, positions, SkTileMode::kClamp}, {}});
 *         paint.setShader(shader);
 *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kCellSize, kCellSize), paint);
 *
 *         canvas->restore();
 *         canvas->translate(0, kCellSize + kPadSize);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Simple gradient: Green to white
 *         this->drawGradient(canvas, {{0.0f, SK_ColorGREEN}, {1.0f, SK_ColorWHITE}});
 *
 *         // Multiple sections: Green to white to red
 *         this->drawGradient(canvas,
 *                            {{0.0f, SK_ColorGREEN}, {0.5f, SK_ColorWHITE}, {1.0f, SK_ColorRED}});
 *
 *         // No stops at 0.0 or 1.0: Larger green to white to larger red
 *         this->drawGradient(canvas,
 *                            {{0.4f, SK_ColorGREEN}, {0.5f, SK_ColorWHITE}, {0.6f, SK_ColorRED}});
 *
 *         // Only one stop, at zero: Solid red
 *         this->drawGradient(canvas, {{0.0f, SK_ColorRED}});
 *
 *         // Only one stop, at 1.0: Solid red
 *         this->drawGradient(canvas, {{1.0f, SK_ColorRED}});
 *
 *         // Only one stop, in the middle: Solid red
 *         this->drawGradient(canvas, {{0.5f, SK_ColorRED}});
 *
 *         // Disjoint gradients (multiple stops at the same offset)
 *         // Blue to white in the top (inner) half, red to yellow in the bottom (outer) half
 *         this->drawGradient(canvas,
 *                            {{0.0f, SK_ColorBLUE},
 *                             {0.5f, SK_ColorWHITE},
 *                             {0.5f, SK_ColorRED},
 *                             {1.0f, SK_ColorYELLOW}});
 *
 *         // Ignored stops: Blue to white, red to yellow (same as previous)
 *         this->drawGradient(canvas,
 *                            {{0.0f, SK_ColorBLUE},
 *                             {0.5f, SK_ColorWHITE},
 *                             {0.5f, SK_ColorGRAY},
 *                             {0.5f, SK_ColorCYAN},
 *                             {0.5f, SK_ColorRED},
 *                             {1.0f, SK_ColorYELLOW}});
 *
 *         // Unsorted stops: Blue to white, red to yellow
 *         // Unlike Chrome, we don't sort the stops, so this renders differently than the prior cell.
 *         this->drawGradient(canvas,
 *                            {{0.5f, SK_ColorWHITE},
 *                             {0.5f, SK_ColorGRAY},
 *                             {1.0f, SK_ColorYELLOW},
 *                             {0.5f, SK_ColorCYAN},
 *                             {0.5f, SK_ColorRED},
 *                             {0.0f, SK_ColorBLUE}});
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class FillrectGradientGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fillrect_gradient"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(kNumColumns * (kCellSize + kPadSize),
   *                              kNumRows * (kCellSize + kPadSize));
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawGradient(SkCanvas* canvas, std::initializer_list<GradientStop> stops) {
   *         std::vector<SkColor4f> colors;
   *         std::vector<SkScalar> positions;
   *         colors.reserve(stops.size());
   *         positions.reserve(stops.size());
   *
   *         for (const GradientStop& stop : stops) {
   *             colors.push_back(SkColor4f::FromColor(stop.color));
   *             positions.push_back(stop.pos);
   *         }
   *
   *         static constexpr SkPoint points[] = {
   *             SkPoint::Make(kCellSize, 0),
   *             SkPoint::Make(kCellSize, kCellSize),
   *         };
   *
   *         // Draw the gradient linearly.
   *         sk_sp<SkShader> shader = SkShaders::LinearGradient(points,
   *                                                    {{colors, positions, SkTileMode::kClamp}, {}});
   *         SkPaint paint;
   *         paint.setShader(shader);
   *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kCellSize, kCellSize), paint);
   *
   *         canvas->save();
   *         canvas->translate(kCellSize + kPadSize, 0);
   *
   *         // Draw the gradient radially.
   *         shader = SkShaders::RadialGradient(SkPoint::Make(kCellSize / 2, kCellSize / 2),
   *                                            kCellSize / 2,
   *                                            {{colors, positions, SkTileMode::kClamp}, {}});
   *         paint.setShader(shader);
   *         canvas->drawRect(SkRect::MakeXYWH(0, 0, kCellSize, kCellSize), paint);
   *
   *         canvas->restore();
   *         canvas->translate(0, kCellSize + kPadSize);
   *     }
   * ```
   */
  protected fun drawGradient(canvas: SkCanvas?, stops: initializer_list<GradientStop>) {
    TODO("Implement drawGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // Simple gradient: Green to white
   *         this->drawGradient(canvas, {{0.0f, SK_ColorGREEN}, {1.0f, SK_ColorWHITE}});
   *
   *         // Multiple sections: Green to white to red
   *         this->drawGradient(canvas,
   *                            {{0.0f, SK_ColorGREEN}, {0.5f, SK_ColorWHITE}, {1.0f, SK_ColorRED}});
   *
   *         // No stops at 0.0 or 1.0: Larger green to white to larger red
   *         this->drawGradient(canvas,
   *                            {{0.4f, SK_ColorGREEN}, {0.5f, SK_ColorWHITE}, {0.6f, SK_ColorRED}});
   *
   *         // Only one stop, at zero: Solid red
   *         this->drawGradient(canvas, {{0.0f, SK_ColorRED}});
   *
   *         // Only one stop, at 1.0: Solid red
   *         this->drawGradient(canvas, {{1.0f, SK_ColorRED}});
   *
   *         // Only one stop, in the middle: Solid red
   *         this->drawGradient(canvas, {{0.5f, SK_ColorRED}});
   *
   *         // Disjoint gradients (multiple stops at the same offset)
   *         // Blue to white in the top (inner) half, red to yellow in the bottom (outer) half
   *         this->drawGradient(canvas,
   *                            {{0.0f, SK_ColorBLUE},
   *                             {0.5f, SK_ColorWHITE},
   *                             {0.5f, SK_ColorRED},
   *                             {1.0f, SK_ColorYELLOW}});
   *
   *         // Ignored stops: Blue to white, red to yellow (same as previous)
   *         this->drawGradient(canvas,
   *                            {{0.0f, SK_ColorBLUE},
   *                             {0.5f, SK_ColorWHITE},
   *                             {0.5f, SK_ColorGRAY},
   *                             {0.5f, SK_ColorCYAN},
   *                             {0.5f, SK_ColorRED},
   *                             {1.0f, SK_ColorYELLOW}});
   *
   *         // Unsorted stops: Blue to white, red to yellow
   *         // Unlike Chrome, we don't sort the stops, so this renders differently than the prior cell.
   *         this->drawGradient(canvas,
   *                            {{0.5f, SK_ColorWHITE},
   *                             {0.5f, SK_ColorGRAY},
   *                             {1.0f, SK_ColorYELLOW},
   *                             {0.5f, SK_ColorCYAN},
   *                             {0.5f, SK_ColorRED},
   *                             {0.0f, SK_ColorBLUE}});
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public data class GradientStop public constructor(
    public var pos: Float,
    public var color: SkColor,
  )
}
