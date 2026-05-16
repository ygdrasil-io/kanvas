package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TiledScaledBitmapGM : public GM {
 * public:
 *
 *     TiledScaledBitmapGM() {
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("tiledscaledbitmap"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1016, 616); }
 *
 *     static SkBitmap make_bm(int width, int height) {
 *         SkBitmap bm;
 *         bm.allocN32Pixels(width, height);
 *         bm.eraseColor(SK_ColorTRANSPARENT);
 *         SkCanvas canvas(bm);
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         canvas.drawCircle(width/2.f, height/2.f, width/4.f, paint);
 *         return bm;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fBitmap = make_bm(360, 288);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *
 *         paint.setAntiAlias(true);
 *
 *         SkMatrix mat;
 *         mat.setScale(121.f/360.f, 93.f/288.f);
 *         mat.postTranslate(-72, -72);
 *
 *         paint.setShader(fBitmap.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                            SkSamplingOptions(SkCubicResampler::Mitchell()), mat));
 *         canvas->drawRect({ 8, 8, 1008, 608 }, paint);
 *     }
 *
 * private:
 *     SkBitmap fBitmap;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TiledScaledBitmapGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("tiledscaledbitmap"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1016, 616); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fBitmap = make_bm(360, 288);
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
   *
   *         paint.setAntiAlias(true);
   *
   *         SkMatrix mat;
   *         mat.setScale(121.f/360.f, 93.f/288.f);
   *         mat.postTranslate(-72, -72);
   *
   *         paint.setShader(fBitmap.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                            SkSamplingOptions(SkCubicResampler::Mitchell()), mat));
   *         canvas->drawRect({ 8, 8, 1008, 608 }, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkBitmap make_bm(int width, int height) {
     *         SkBitmap bm;
     *         bm.allocN32Pixels(width, height);
     *         bm.eraseColor(SK_ColorTRANSPARENT);
     *         SkCanvas canvas(bm);
     *         SkPaint paint;
     *         paint.setAntiAlias(true);
     *         canvas.drawCircle(width/2.f, height/2.f, width/4.f, paint);
     *         return bm;
     *     }
     * ```
     */
    protected fun makeBm(width: Int, height: Int): SkBitmap {
      TODO("Implement makeBm")
    }
  }
}
