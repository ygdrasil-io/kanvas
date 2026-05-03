package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TestExtractAlphaGM : public skiagm::GM {
 *     void onOnceBeforeDraw() override {
 *         // Make a bitmap with per-pixels alpha (stroked circle)
 *         fBitmap.allocN32Pixels(100, 100);
 *         SkCanvas canvas(fBitmap);
 *         canvas.clear(0);
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(20);
 *
 *         canvas.drawCircle(50, 50, 39, paint);
 *
 *         fBitmap.extractAlpha(&fAlpha);
 *     }
 *
 * public:
 *     SkBitmap fBitmap, fAlpha;
 *
 * protected:
 *     SkString getName() const override { return SkString("extractalpha"); }
 *
 *     SkISize getISize() override { return SkISize::Make(540, 330); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setColor(SK_ColorRED);
 *
 *         SkSamplingOptions sampling(SkFilterMode::kLinear);
 *
 *         // should stay blue (ignore paint's color)
 *         canvas->drawImage(fBitmap.asImage(), 10, 10, sampling, &paint);
 *         // should draw red
 *         canvas->drawImage(fAlpha.asImage(), 120, 10, sampling, &paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TestExtractAlphaGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  public var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap, fAlpha
   * ```
   */
  public var fAlpha: SkBitmap = TODO("Initialize fAlpha")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // Make a bitmap with per-pixels alpha (stroked circle)
   *         fBitmap.allocN32Pixels(100, 100);
   *         SkCanvas canvas(fBitmap);
   *         canvas.clear(0);
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(20);
   *
   *         canvas.drawCircle(50, 50, 39, paint);
   *
   *         fBitmap.extractAlpha(&fAlpha);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("extractalpha"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(540, 330); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setColor(SK_ColorRED);
   *
   *         SkSamplingOptions sampling(SkFilterMode::kLinear);
   *
   *         // should stay blue (ignore paint's color)
   *         canvas->drawImage(fBitmap.asImage(), 10, 10, sampling, &paint);
   *         // should draw red
   *         canvas->drawImage(fAlpha.asImage(), 120, 10, sampling, &paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
