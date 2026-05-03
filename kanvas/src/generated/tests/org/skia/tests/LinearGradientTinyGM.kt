package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class LinearGradientTinyGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("linear_gradient_tiny"); }
 *
 *     SkISize getISize() override { return {600, 500}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkScalar kRectSize = 100;
 *         const unsigned kStopCount = 3;
 *         const SkColor4f colors[kStopCount] = { SkColors::kGreen, SkColors::kRed, SkColors::kGreen };
 *         const struct {
 *             SkPoint pts[2];
 *             SkScalar pos[kStopCount];
 *         } configs[] = {
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.999999f,    1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.000001f,    1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.999999999f, 1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.000000001f, 1 }},
 *
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.999999f,    1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.000001f,    1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.999999999f, 1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.000000001f, 1 }},
 *
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0.00001f, 0) }, { 0, 0.5f, 1 }},
 *             { { SkPoint::Make(9.99999f, 0), SkPoint::Make(10, 0) },       { 0, 0.5f, 1 }},
 *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 0.00001f) }, { 0, 0.5f, 1 }},
 *             { { SkPoint::Make(0, 9.99999f), SkPoint::Make(0, 10) },       { 0, 0.5f, 1 }},
 *         };
 *
 *         SkPaint paint;
 *         for (unsigned i = 0; i < std::size(configs); ++i) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *             paint.setShader(SkShaders::LinearGradient(configs[i].pts,
 *                                                       {{colors, configs[i].pos, SkTileMode::kClamp},
 *                                                        {}}));
 *             canvas->translate(kRectSize * ((i % 4) * 1.5f + 0.25f),
 *                               kRectSize * ((i / 4) * 1.5f + 0.25f));
 *
 *             canvas->drawRect(SkRect::MakeWH(kRectSize, kRectSize), paint);
 *         }
 *     }
 * }
 * ```
 */
public open class LinearGradientTinyGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("linear_gradient_tiny"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {600, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkScalar kRectSize = 100;
   *         const unsigned kStopCount = 3;
   *         const SkColor4f colors[kStopCount] = { SkColors::kGreen, SkColors::kRed, SkColors::kGreen };
   *         const struct {
   *             SkPoint pts[2];
   *             SkScalar pos[kStopCount];
   *         } configs[] = {
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.999999f,    1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.000001f,    1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.999999999f, 1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(10, 0) },       { 0, 0.000000001f, 1 }},
   *
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.999999f,    1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.000001f,    1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.999999999f, 1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 10) },       { 0, 0.000000001f, 1 }},
   *
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0.00001f, 0) }, { 0, 0.5f, 1 }},
   *             { { SkPoint::Make(9.99999f, 0), SkPoint::Make(10, 0) },       { 0, 0.5f, 1 }},
   *             { { SkPoint::Make(0, 0),        SkPoint::Make(0, 0.00001f) }, { 0, 0.5f, 1 }},
   *             { { SkPoint::Make(0, 9.99999f), SkPoint::Make(0, 10) },       { 0, 0.5f, 1 }},
   *         };
   *
   *         SkPaint paint;
   *         for (unsigned i = 0; i < std::size(configs); ++i) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *             paint.setShader(SkShaders::LinearGradient(configs[i].pts,
   *                                                       {{colors, configs[i].pos, SkTileMode::kClamp},
   *                                                        {}}));
   *             canvas->translate(kRectSize * ((i % 4) * 1.5f + 0.25f),
   *                               kRectSize * ((i / 4) * 1.5f + 0.25f));
   *
   *             canvas->drawRect(SkRect::MakeWH(kRectSize, kRectSize), paint);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
