package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BitmapRectRounding : public skiagm::GM {
 *     SkBitmap fBM;
 *
 * public:
 *     BitmapRectRounding() {}
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("bitmaprect_rounding");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onOnceBeforeDraw() override {
 *         fBM.allocN32Pixels(10, 10);
 *         fBM.eraseColor(SK_ColorBLUE);
 *     }
 *
 *     // This choice of coordinates and matrix land the bottom edge of the clip (and bitmap dst)
 *     // at exactly 1/2 pixel boundary. However, drawBitmapRect may lose precision along the way.
 *     // If it does, we may see a red-line at the bottom, instead of the bitmap exactly matching
 *     // the clip (in which case we should see all blue).
 *     // The correct image should be all blue.
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorRED);
 *
 *         const SkRect r = SkRect::MakeXYWH(1, 1, 110, 114);
 *         canvas->scale(0.9f, 0.9f);
 *
 *         // the drawRect shows the same problem as clipRect(r) followed by drawcolor(red)
 *         canvas->drawRect(r, paint);
 *         canvas->drawImageRect(fBM.asImage(), r, SkSamplingOptions());
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BitmapRectRounding public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBM
   * ```
   */
  private var fBM: SkBitmap = TODO("Initialize fBM")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("bitmaprect_rounding");
   *         return str;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fBM.allocN32Pixels(10, 10);
   *         fBM.eraseColor(SK_ColorBLUE);
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
   *         SkPaint paint;
   *         paint.setColor(SK_ColorRED);
   *
   *         const SkRect r = SkRect::MakeXYWH(1, 1, 110, 114);
   *         canvas->scale(0.9f, 0.9f);
   *
   *         // the drawRect shows the same problem as clipRect(r) followed by drawcolor(red)
   *         canvas->drawRect(r, paint);
   *         canvas->drawImageRect(fBM.asImage(), r, SkSamplingOptions());
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
