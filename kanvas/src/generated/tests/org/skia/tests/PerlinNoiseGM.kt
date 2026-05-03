package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class PerlinNoiseGM : public skiagm::GM {
 *     static constexpr SkISize kSize = {80, 80};
 *
 *     SkString getName() const override { return SkString("perlinnoise"); }
 *
 *     SkISize getISize() override { return {220, 620}; }
 *
 *     void drawRect(SkCanvas* canvas, SkPoint pt, const SkPaint& paint, const SkISize& size) {
 *         canvas->save();
 *         canvas->translate(pt.fX, pt.fY);
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(size.width()), SkIntToScalar(size.height()));
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *     }
 *
 *     void test(SkCanvas* canvas, SkPoint pt, Type type, bool stitch,
 *               SkVector baseFrequency, int numOctaves, float seed, SkISize tileSize = {40, 40}) {
 *         sk_sp<SkShader> shader = noise_shader(type,
 *                                               baseFrequency.fX,
 *                                               baseFrequency.fY,
 *                                               numOctaves,
 *                                               seed,
 *                                               stitch,
 *                                               tileSize);
 *         SkPaint paint;
 *         paint.setShader(std::move(shader));
 *         if (stitch) {
 *             this->drawRect(canvas, pt, paint, tileSize);
 *             pt.fX += tileSize.width();
 *             this->drawRect(canvas, pt, paint, tileSize);
 *             pt.fY += tileSize.height();
 *             this->drawRect(canvas, pt, paint, tileSize);
 *             pt.fX -= tileSize.width();
 *             this->drawRect(canvas, pt, paint, tileSize);
 *         } else {
 *             this->drawRect(canvas, pt, paint, kSize);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->test(canvas, SkPoint{  0,   0}, Type::kFractalNoise, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/0, /*seed=*/0);
 *         this->test(canvas, SkPoint{100,   0}, Type::kTurbulence, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/0, /*seed=*/0);
 *
 *         this->test(canvas, SkPoint{  0, 100}, Type::kFractalNoise, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/2, /*seed=*/0);
 *         this->test(canvas, SkPoint{100, 100}, Type::kFractalNoise, /*stitch=*/true,
 *                    SkVector{0.05f, 0.1f}, /*numOctaves=*/1, /*seed=*/0);
 *
 *         this->test(canvas, SkPoint{  0, 200}, Type::kTurbulence, /*stitch=*/true,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/1, /*seed=*/0);
 *         this->test(canvas, SkPoint{100, 200}, Type::kTurbulence, /*stitch=*/false,
 *                    SkVector{0.2f, 0.4f}, /*numOctaves=*/5, /*seed=*/0);
 *
 *         this->test(canvas, SkPoint{  0, 300}, Type::kFractalNoise, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/3, /*seed=*/1);
 *         this->test(canvas, SkPoint{100, 300}, Type::kFractalNoise, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/3, /*seed=*/4);
 *
 *         canvas->save();
 *         canvas->scale(0.75f, 1.0f);
 *
 *         this->test(canvas, SkPoint{  0, 400}, Type::kFractalNoise, /*stitch=*/false,
 *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/2, /*seed=*/0);
 *         this->test(canvas, SkPoint{100, 400}, Type::kFractalNoise, /*stitch=*/true,
 *                    SkVector{0.1f, 0.05f}, /*numOctaves=*/1, /*seed=*/0);
 *
 *         canvas->restore();
 *
 *         // Matches Chromium test case in svg/filters/feTurbulence-tiled.svg
 *         this->test(canvas, SkPoint{  0, 500}, Type::kTurbulence, /*stitch=*/true,
 *                    SkVector{0.03f, 0.03f}, /*numOctaves=*/1, /*seed=*/0, /*tileSize=*/{50, 50});
 *
 *         // Matches Chromium test case in css3/filters/effect-reference.html
 *         this->test(canvas, SkPoint{120, 500}, Type::kTurbulence, /*stitch=*/false,
 *                    SkVector{0.05f, 0.05f}, /*numOctaves=*/2, /*seed=*/0);
 *
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PerlinNoiseGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("perlinnoise"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {220, 620}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRect(SkCanvas* canvas, SkPoint pt, const SkPaint& paint, const SkISize& size) {
   *         canvas->save();
   *         canvas->translate(pt.fX, pt.fY);
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(size.width()), SkIntToScalar(size.height()));
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawRect(
    canvas: SkCanvas?,
    pt: SkPoint,
    paint: SkPaint,
    size: SkISize,
  ) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void test(SkCanvas* canvas, SkPoint pt, Type type, bool stitch,
   *               SkVector baseFrequency, int numOctaves, float seed, SkISize tileSize = {40, 40}) {
   *         sk_sp<SkShader> shader = noise_shader(type,
   *                                               baseFrequency.fX,
   *                                               baseFrequency.fY,
   *                                               numOctaves,
   *                                               seed,
   *                                               stitch,
   *                                               tileSize);
   *         SkPaint paint;
   *         paint.setShader(std::move(shader));
   *         if (stitch) {
   *             this->drawRect(canvas, pt, paint, tileSize);
   *             pt.fX += tileSize.width();
   *             this->drawRect(canvas, pt, paint, tileSize);
   *             pt.fY += tileSize.height();
   *             this->drawRect(canvas, pt, paint, tileSize);
   *             pt.fX -= tileSize.width();
   *             this->drawRect(canvas, pt, paint, tileSize);
   *         } else {
   *             this->drawRect(canvas, pt, paint, kSize);
   *         }
   *     }
   * ```
   */
  private fun test(
    param0: SkCanvas?,
    param1: SkPoint,
    param2: Type,
    param3: Boolean,
    param4: SkVector,
    param5: Int,
    param6: Float,
    param7: SkISize,
  ) {
    TODO("Implement test")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->test(canvas, SkPoint{  0,   0}, Type::kFractalNoise, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/0, /*seed=*/0);
   *         this->test(canvas, SkPoint{100,   0}, Type::kTurbulence, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/0, /*seed=*/0);
   *
   *         this->test(canvas, SkPoint{  0, 100}, Type::kFractalNoise, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/2, /*seed=*/0);
   *         this->test(canvas, SkPoint{100, 100}, Type::kFractalNoise, /*stitch=*/true,
   *                    SkVector{0.05f, 0.1f}, /*numOctaves=*/1, /*seed=*/0);
   *
   *         this->test(canvas, SkPoint{  0, 200}, Type::kTurbulence, /*stitch=*/true,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/1, /*seed=*/0);
   *         this->test(canvas, SkPoint{100, 200}, Type::kTurbulence, /*stitch=*/false,
   *                    SkVector{0.2f, 0.4f}, /*numOctaves=*/5, /*seed=*/0);
   *
   *         this->test(canvas, SkPoint{  0, 300}, Type::kFractalNoise, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/3, /*seed=*/1);
   *         this->test(canvas, SkPoint{100, 300}, Type::kFractalNoise, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/3, /*seed=*/4);
   *
   *         canvas->save();
   *         canvas->scale(0.75f, 1.0f);
   *
   *         this->test(canvas, SkPoint{  0, 400}, Type::kFractalNoise, /*stitch=*/false,
   *                    SkVector{0.1f, 0.1f}, /*numOctaves=*/2, /*seed=*/0);
   *         this->test(canvas, SkPoint{100, 400}, Type::kFractalNoise, /*stitch=*/true,
   *                    SkVector{0.1f, 0.05f}, /*numOctaves=*/1, /*seed=*/0);
   *
   *         canvas->restore();
   *
   *         // Matches Chromium test case in svg/filters/feTurbulence-tiled.svg
   *         this->test(canvas, SkPoint{  0, 500}, Type::kTurbulence, /*stitch=*/true,
   *                    SkVector{0.03f, 0.03f}, /*numOctaves=*/1, /*seed=*/0, /*tileSize=*/{50, 50});
   *
   *         // Matches Chromium test case in css3/filters/effect-reference.html
   *         this->test(canvas, SkPoint{120, 500}, Type::kTurbulence, /*stitch=*/false,
   *                    SkVector{0.05f, 0.05f}, /*numOctaves=*/2, /*seed=*/0);
   *
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kSize: SkISize = TODO("Initialize kSize")
  }
}
