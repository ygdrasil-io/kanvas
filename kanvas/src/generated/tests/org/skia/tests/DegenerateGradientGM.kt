package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DegenerateGradientGM : public skiagm::GM {
 * public:
 *     DegenerateGradientGM() {
 *
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("degenerate_gradients"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(3 * TILE_GAP, 3 * TILE_GAP);
 *         draw_tile_header(canvas);
 *
 *         draw_row(canvas, "linear: empty, blue, blue, green", make_linear);
 *         draw_row(canvas, "radial:  empty, blue, blue, green", make_radial);
 *         draw_row(canvas, "sweep-0: empty, blue, blue, green", make_sweep_zero_ang);
 *         draw_row(canvas, "sweep-45: empty, blue, blue, red 45 degree sector then green",
 *                  make_sweep);
 *         draw_row(canvas, "2pt-conic-0: empty, blue, blue, green", make_2pt_conic_zero_rad);
 *         draw_row(canvas, "2pt-conic-1: empty, blue, blue, full red circle on green",
 *                  make_2pt_conic);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DegenerateGradientGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("degenerate_gradients"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 800); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(3 * TILE_GAP, 3 * TILE_GAP);
   *         draw_tile_header(canvas);
   *
   *         draw_row(canvas, "linear: empty, blue, blue, green", make_linear);
   *         draw_row(canvas, "radial:  empty, blue, blue, green", make_radial);
   *         draw_row(canvas, "sweep-0: empty, blue, blue, green", make_sweep_zero_ang);
   *         draw_row(canvas, "sweep-45: empty, blue, blue, red 45 degree sector then green",
   *                  make_sweep);
   *         draw_row(canvas, "2pt-conic-0: empty, blue, blue, green", make_2pt_conic_zero_rad);
   *         draw_row(canvas, "2pt-conic-1: empty, blue, blue, full red circle on green",
   *                  make_2pt_conic);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
