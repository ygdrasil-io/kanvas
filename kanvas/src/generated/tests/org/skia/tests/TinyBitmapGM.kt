package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TinyBitmapGM : public skiagm::GM {
 *     void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 *
 *     SkString getName() const override { return SkString("tinybitmap"); }
 *
 *     SkISize getISize() override { return SkISize::Make(100, 100); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBitmap bm;
 *         bm.allocN32Pixels(1, 1);
 *         *bm.getAddr32(0, 0) = SkPackARGB32(0x80, 0x80, 0, 0);
 *         SkPaint paint;
 *         paint.setAlphaf(0.5f);
 *         paint.setShader(bm.makeShader(SkTileMode::kRepeat, SkTileMode::kMirror,
 *                                       SkSamplingOptions()));
 *         canvas->drawPaint(paint);
 *     }
 * }
 * ```
 */
public open class TinyBitmapGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("tinybitmap"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(100, 100); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBitmap bm;
   *         bm.allocN32Pixels(1, 1);
   *         *bm.getAddr32(0, 0) = SkPackARGB32(0x80, 0x80, 0, 0);
   *         SkPaint paint;
   *         paint.setAlphaf(0.5f);
   *         paint.setShader(bm.makeShader(SkTileMode::kRepeat, SkTileMode::kMirror,
   *                                       SkSamplingOptions()));
   *         canvas->drawPaint(paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
