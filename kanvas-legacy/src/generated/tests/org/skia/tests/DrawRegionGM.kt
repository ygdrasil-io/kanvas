package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkRegion
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawRegionGM : public skiagm::GM {
 * public:
 *     DrawRegionGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("drawregion"); }
 *
 *     SkISize getISize() override { return SkISize::Make(500, 500); }
 *
 *     bool runAsBench() const override {
 *         return true;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         for (int x = 50; x < 250; x+=2) {
 *             for (int y = 50; y < 250; y+=2) {
 *                 fRegion.op({x, y, x + 1, y + 1}, SkRegion::kUnion_Op);
 *             }
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(10, 10);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setColor(0xFFFF00FF);
 *         canvas->drawRect(SkRect::MakeLTRB(50.0f, 50.0f, 250.0f, 250.0f), paint);
 *
 *         paint.setColor(0xFF00FFFF);
 *         canvas->drawRegion(fRegion, paint);
 *     }
 *
 *     SkRegion fRegion;
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawRegionGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkRegion fRegion
   * ```
   */
  protected var fRegion: SkRegion = TODO("Initialize fRegion")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("drawregion"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override {
   *         return true;
   *     }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (int x = 50; x < 250; x+=2) {
   *             for (int y = 50; y < 250; y+=2) {
   *                 fRegion.op({x, y, x + 1, y + 1}, SkRegion::kUnion_Op);
   *             }
   *         }
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
   *         canvas->translate(10, 10);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setColor(0xFFFF00FF);
   *         canvas->drawRect(SkRect::MakeLTRB(50.0f, 50.0f, 250.0f, 250.0f), paint);
   *
   *         paint.setColor(0xFF00FFFF);
   *         canvas->drawRegion(fRegion, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
