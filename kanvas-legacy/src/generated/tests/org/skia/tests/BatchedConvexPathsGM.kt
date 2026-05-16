package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BatchedConvexPathsGM : public GM {
 * private:
 *     SkString getName() const override { return SkString("batchedconvexpaths"); }
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* ctxOptions) override {
 *         // Ensure our paths don't go through the atlas path renderer.
 *         ctxOptions->fGpuPathRenderers &= ~GpuPathRenderers::kAtlas;
 *     }
 * #endif
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         canvas->clear(SK_ColorBLACK);
 *         for (uint32_t i = 0; i < 10; ++i) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *
 *             int numPoints = (i + 3) * 3;
 *             SkPathBuilder builder;
 *             builder.moveTo(1, 0);
 *             for (float j = 1; j < numPoints; j += 3) {
 *                 constexpr float k2PI = SK_ScalarPI * 2;
 *                 builder.cubicTo(cosf(j/numPoints * k2PI), sinf(j/numPoints * k2PI),
 *                                 cosf((j+1)/numPoints * k2PI), sinf((j+1)/numPoints * k2PI),
 *                                 j+2 == numPoints ? 1 : cosf((j+2)/numPoints * k2PI),
 *                                 j+2 == numPoints ? 0 : sinf((j+2)/numPoints * k2PI));
 *             }
 *             float scale = 256 - i*24;
 *             canvas->translate(scale + (256 - scale) * .33f, scale + (256 - scale) * .33f);
 *             canvas->scale(scale, scale);
 *
 *             SkPaint paint;
 *             paint.setColor(((i + 123458383u) * 285018463u) | 0xff808080);
 *             paint.setAlphaf(0.3f);
 *             paint.setAntiAlias(true);
 *
 *             canvas->drawPath(builder.detach(), paint);
 *         }
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class BatchedConvexPathsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("batchedconvexpaths"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(512, 512); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         canvas->clear(SK_ColorBLACK);
   *         for (uint32_t i = 0; i < 10; ++i) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *
   *             int numPoints = (i + 3) * 3;
   *             SkPathBuilder builder;
   *             builder.moveTo(1, 0);
   *             for (float j = 1; j < numPoints; j += 3) {
   *                 constexpr float k2PI = SK_ScalarPI * 2;
   *                 builder.cubicTo(cosf(j/numPoints * k2PI), sinf(j/numPoints * k2PI),
   *                                 cosf((j+1)/numPoints * k2PI), sinf((j+1)/numPoints * k2PI),
   *                                 j+2 == numPoints ? 1 : cosf((j+2)/numPoints * k2PI),
   *                                 j+2 == numPoints ? 0 : sinf((j+2)/numPoints * k2PI));
   *             }
   *             float scale = 256 - i*24;
   *             canvas->translate(scale + (256 - scale) * .33f, scale + (256 - scale) * .33f);
   *             canvas->scale(scale, scale);
   *
   *             SkPaint paint;
   *             paint.setColor(((i + 123458383u) * 285018463u) | 0xff808080);
   *             paint.setAlphaf(0.3f);
   *             paint.setAntiAlias(true);
   *
   *             canvas->drawPath(builder.detach(), paint);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
