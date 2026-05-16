package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ScaledRectsGM : public GM {
 * public:
 *     ScaledRectsGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("scaledrects"); }
 *
 *     SkISize getISize() override { return SkISize::Make(128, 64); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clipRect(SkRect::MakeXYWH(10, 50, 100, 10));
 *
 *         {
 *             SkPaint blue;
 *             blue.setColor(SK_ColorBLUE);
 *
 *             canvas->setMatrix(SkMatrix::MakeAll( 3.0f, -0.5f, 0.0f,
 *                                                 -0.5f, -3.0f, 0.0f,
 *                                                  0.0f,  0.0f, 1.0f));
 *
 *             canvas->drawRect(SkRect::MakeXYWH(-1000, -1000, 2000, 2000), blue);
 *         }
 *
 *         {
 *             SkPaint red;
 *             red.setColor(SK_ColorRED);
 *             red.setBlendMode(SkBlendMode::kPlus);
 *
 *             canvas->setMatrix(SkMatrix::MakeAll(3000.0f,  -500.0f, 0.0f,
 *                                                 -500.0f, -3000.0f, 0.0f,
 *                                                    0.0f,     0.0f, 1.0f));
 *
 *             canvas->drawRect(SkRect::MakeXYWH(-1, -1, 2, 2), red);
 *         }
 *     }
 * }
 * ```
 */
public open class ScaledRectsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("scaledrects"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(128, 64); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clipRect(SkRect::MakeXYWH(10, 50, 100, 10));
   *
   *         {
   *             SkPaint blue;
   *             blue.setColor(SK_ColorBLUE);
   *
   *             canvas->setMatrix(SkMatrix::MakeAll( 3.0f, -0.5f, 0.0f,
   *                                                 -0.5f, -3.0f, 0.0f,
   *                                                  0.0f,  0.0f, 1.0f));
   *
   *             canvas->drawRect(SkRect::MakeXYWH(-1000, -1000, 2000, 2000), blue);
   *         }
   *
   *         {
   *             SkPaint red;
   *             red.setColor(SK_ColorRED);
   *             red.setBlendMode(SkBlendMode::kPlus);
   *
   *             canvas->setMatrix(SkMatrix::MakeAll(3000.0f,  -500.0f, 0.0f,
   *                                                 -500.0f, -3000.0f, 0.0f,
   *                                                    0.0f,     0.0f, 1.0f));
   *
   *             canvas->drawRect(SkRect::MakeXYWH(-1, -1, 2, 2), red);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
