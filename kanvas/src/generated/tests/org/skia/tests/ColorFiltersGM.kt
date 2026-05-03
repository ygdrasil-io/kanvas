package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ColorFiltersGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("lightingcolorfilter"); }
 *
 *     SkISize getISize() override { return {620, 430}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRect r = {0, 0, 600, 50};
 *
 *         SkPaint paint;
 *         paint.setShader(make_shader(r));
 *
 *         const struct {
 *             InstallPaint    fProc;
 *             uint32_t        fData0, fData1;
 *         } rec[] = {
 *             { install_nothing, 0, 0 },
 *             { install_lighting, 0xFF0000, 0 },
 *             { install_lighting, 0x00FF00, 0 },
 *             { install_lighting, 0x0000FF, 0 },
 *             { install_lighting, 0x000000, 0xFF0000 },
 *             { install_lighting, 0x000000, 0x00FF00 },
 *             { install_lighting, 0x000000, 0x0000FF },
 *         };
 *
 *         canvas->translate(10, 10);
 *         for (size_t i = 0; i < std::size(rec); ++i) {
 *             rec[i].fProc(&paint, rec[i].fData0, rec[i].fData1);
 *             canvas->drawRect(r, paint);
 *             canvas->translate(0, r.height() + 10);
 *         }
 *     }
 * }
 * ```
 */
public open class ColorFiltersGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lightingcolorfilter"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {620, 430}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRect r = {0, 0, 600, 50};
   *
   *         SkPaint paint;
   *         paint.setShader(make_shader(r));
   *
   *         const struct {
   *             InstallPaint    fProc;
   *             uint32_t        fData0, fData1;
   *         } rec[] = {
   *             { install_nothing, 0, 0 },
   *             { install_lighting, 0xFF0000, 0 },
   *             { install_lighting, 0x00FF00, 0 },
   *             { install_lighting, 0x0000FF, 0 },
   *             { install_lighting, 0x000000, 0xFF0000 },
   *             { install_lighting, 0x000000, 0x00FF00 },
   *             { install_lighting, 0x000000, 0x0000FF },
   *         };
   *
   *         canvas->translate(10, 10);
   *         for (size_t i = 0; i < std::size(rec); ++i) {
   *             rec[i].fProc(&paint, rec[i].fData0, rec[i].fData1);
   *             canvas->drawRect(r, paint);
   *             canvas->translate(0, r.height() + 10);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
