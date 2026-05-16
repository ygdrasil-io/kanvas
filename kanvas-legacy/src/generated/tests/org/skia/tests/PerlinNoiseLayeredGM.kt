package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PerlinNoiseLayeredGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("perlinnoise_layered"); }
 *
 *     SkISize getISize() override { return {500, 500}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const sk_sp<SkImageFilter> perlin = SkImageFilters::ColorFilter(
 *                 SkColorFilters::Matrix(SkColorMatrix()),
 *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(0.3f, 0.3f, 1, 4)));
 *
 *         const SkPaint paint;
 *         canvas->saveLayer(nullptr, &paint);
 *         {
 *             SkPaint p;
 *             p.setImageFilter(perlin);
 *             canvas->drawPaint(p);
 *         }
 *         canvas->restore();
 *
 *         canvas->saveLayer(nullptr, nullptr);
 *         {
 *             SkPaint p;
 *             p.setImageFilter(perlin);
 *             canvas->drawPaint(p);
 *         }
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class PerlinNoiseLayeredGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("perlinnoise_layered"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {500, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const sk_sp<SkImageFilter> perlin = SkImageFilters::ColorFilter(
   *                 SkColorFilters::Matrix(SkColorMatrix()),
   *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(0.3f, 0.3f, 1, 4)));
   *
   *         const SkPaint paint;
   *         canvas->saveLayer(nullptr, &paint);
   *         {
   *             SkPaint p;
   *             p.setImageFilter(perlin);
   *             canvas->drawPaint(p);
   *         }
   *         canvas->restore();
   *
   *         canvas->saveLayer(nullptr, nullptr);
   *         {
   *             SkPaint p;
   *             p.setImageFilter(perlin);
   *             canvas->drawPaint(p);
   *         }
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
