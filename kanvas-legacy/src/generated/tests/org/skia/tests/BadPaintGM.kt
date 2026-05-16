package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BadPaintGM : public skiagm::GM {
 *  public:
 *     BadPaintGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("badpaint"); }
 *
 *     SkISize getISize() override { return SkISize::Make(100, 100); }
 *
 *     void onOnceBeforeDraw() override {
 *         SkBitmap emptyBmp;
 *
 *         SkBitmap blueBmp;
 *         blueBmp.allocN32Pixels(10, 10);
 *         blueBmp.eraseColor(SK_ColorBLUE);
 *
 *         SkMatrix badMatrix;
 *         badMatrix.setAll(0, 0, 0, 0, 0, 0, 0, 0, 0);
 *
 *         // Empty bitmap.
 *         fPaints.push_back().setColor(SK_ColorGREEN);
 *         fPaints.back().setShader(emptyBmp.makeShader(SkSamplingOptions()));
 *
 *         // Non-invertible local matrix.
 *         fPaints.push_back().setColor(SK_ColorGREEN);
 *         fPaints.back().setShader(blueBmp.makeShader(SkSamplingOptions(), badMatrix));
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRect rect = SkRect::MakeXYWH(10, 10, 80, 80);
 *         for (int i = 0; i < fPaints.size(); ++i) {
 *             canvas->drawRect(rect, fPaints[i]);
 *         }
 *     }
 *
 * private:
 *     TArray<SkPaint> fPaints;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BadPaintGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<SkPaint> fPaints
   * ```
   */
  private var fPaints: Int = TODO("Initialize fPaints")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("badpaint"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(100, 100); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkBitmap emptyBmp;
   *
   *         SkBitmap blueBmp;
   *         blueBmp.allocN32Pixels(10, 10);
   *         blueBmp.eraseColor(SK_ColorBLUE);
   *
   *         SkMatrix badMatrix;
   *         badMatrix.setAll(0, 0, 0, 0, 0, 0, 0, 0, 0);
   *
   *         // Empty bitmap.
   *         fPaints.push_back().setColor(SK_ColorGREEN);
   *         fPaints.back().setShader(emptyBmp.makeShader(SkSamplingOptions()));
   *
   *         // Non-invertible local matrix.
   *         fPaints.push_back().setColor(SK_ColorGREEN);
   *         fPaints.back().setShader(blueBmp.makeShader(SkSamplingOptions(), badMatrix));
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRect rect = SkRect::MakeXYWH(10, 10, 80, 80);
   *         for (int i = 0; i < fPaints.size(); ++i) {
   *             canvas->drawRect(rect, fPaints[i]);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
