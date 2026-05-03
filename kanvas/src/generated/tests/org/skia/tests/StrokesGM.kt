package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class StrokesGM : public skiagm::GM {
 * public:
 *     StrokesGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokes_round"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
 *         options->fMaxPathAtlasTextureSize = 0;
 *         options->fAllowMultipleAtlasTextures = false;
 *     }
 * #endif
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(SkIntToScalar(9)/2);
 *
 *         for (int y = 0; y < 2; y++) {
 *             paint.setAntiAlias(!!y);
 *             SkAutoCanvasRestore acr(canvas, true);
 *             canvas->translate(0, SH * y);
 *             canvas->clipRect(SkRect::MakeLTRB(
 *                                               SkIntToScalar(2), SkIntToScalar(2)
 *                                               , SW - SkIntToScalar(2), SH - SkIntToScalar(2)
 *                                               ));
 *
 *             SkRandom rand;
 *             for (int i = 0; i < N; i++) {
 *                 SkRect r;
 *                 rnd_rect(&r, &paint, rand);
 *                 canvas->drawOval(r, paint);
 *                 rnd_rect(&r, &paint, rand);
 *                 canvas->drawRoundRect(r, r.width()/4, r.height()/4, paint);
 *                 rnd_rect(&r, &paint, rand);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class StrokesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokes_round"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
   *         options->fMaxPathAtlasTextureSize = 0;
   *         options->fAllowMultipleAtlasTextures = false;
   *     }
   * ```
   */
  protected override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(SkIntToScalar(9)/2);
   *
   *         for (int y = 0; y < 2; y++) {
   *             paint.setAntiAlias(!!y);
   *             SkAutoCanvasRestore acr(canvas, true);
   *             canvas->translate(0, SH * y);
   *             canvas->clipRect(SkRect::MakeLTRB(
   *                                               SkIntToScalar(2), SkIntToScalar(2)
   *                                               , SW - SkIntToScalar(2), SH - SkIntToScalar(2)
   *                                               ));
   *
   *             SkRandom rand;
   *             for (int i = 0; i < N; i++) {
   *                 SkRect r;
   *                 rnd_rect(&r, &paint, rand);
   *                 canvas->drawOval(r, paint);
   *                 rnd_rect(&r, &paint, rand);
   *                 canvas->drawRoundRect(r, r.width()/4, r.height()/4, paint);
   *                 rnd_rect(&r, &paint, rand);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
