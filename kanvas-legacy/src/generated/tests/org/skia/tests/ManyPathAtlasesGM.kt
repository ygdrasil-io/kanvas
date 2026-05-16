package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ManyPathAtlasesGM : public GM {
 * public:
 *     ManyPathAtlasesGM(int maxAtlasSize) : fMaxAtlasSize(maxAtlasSize) {}
 * private:
 *     SkString getName() const override {
 *         return SkStringPrintf("manypathatlases_%i", fMaxAtlasSize);
 *     }
 *     SkISize getISize() override { return SkISize::Make(128, 128); }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* ctxOptions) override {
 *         // This will test the case where the atlas runs out of room if fMaxAtlasSize is small.
 *         ctxOptions->fMaxTextureAtlasSize = fMaxAtlasSize;
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
 *         options->fMaxPathAtlasTextureSize = fMaxAtlasSize;
 *     }
 * #endif
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SkColors::kYellow);
 *
 * #if defined(SK_GANESH)
 *         // Flush the context to make the DAG empty. This will test the case where we try to add an
 *         // atlas task to an empty DAG.
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext) {
 *             dContext->flush();
 *         }
 * #endif
 *
 *         SkPath clip = SkPathBuilder().moveTo(-50, 20)
 *                                      .cubicTo(-50, -20, 50, -20, 50, 40)
 *                                      .cubicTo(20, 0, -20, 0, -50, 20)
 *                                      .transform(SkMatrix::Translate(64, 70))
 *                                      .detach();
 *         for (int i = 0; i < 4; ++i) {
 *             SkPath rotatedClip = clip.makeTransform(SkMatrix::RotateDeg(30 * i + 128, {64, 70}));
 *             rotatedClip.setIsVolatile(true);
 *             canvas->clipPath(rotatedClip, SkClipOp::kDifference, true);
 *         }
 *         SkPath path = SkPathBuilder().moveTo(20, 0)
 *                                      .lineTo(108, 0).cubicTo(108, 20, 108, 20, 128, 20)
 *                                      .lineTo(128, 108).cubicTo(108, 108, 108, 108, 108, 128)
 *                                      .lineTo(20, 128).cubicTo(20, 108, 20, 108, 0, 108)
 *                                      .lineTo(0, 20).cubicTo(20, 20, 20, 20, 20, 0)
 *                                      .detach();
 *         path.setIsVolatile(true);
 *         SkPaint teal;
 *         teal.setColor4f({.03f, .91f, .87f, 1});
 *         teal.setAntiAlias(true);
 *         canvas->drawPath(path, teal);
 *     }
 *
 *     const int fMaxAtlasSize;
 * }
 * ```
 */
public open class ManyPathAtlasesGM public constructor(
  maxAtlasSize: Int,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const int fMaxAtlasSize
   * ```
   */
  private val fMaxAtlasSize: Int = TODO("Initialize fMaxAtlasSize")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("manypathatlases_%i", fMaxAtlasSize);
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(128, 128); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
   *         options->fMaxPathAtlasTextureSize = fMaxAtlasSize;
   *     }
   * ```
   */
  public override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SkColors::kYellow);
   *
   * #if defined(SK_GANESH)
   *         // Flush the context to make the DAG empty. This will test the case where we try to add an
   *         // atlas task to an empty DAG.
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext) {
   *             dContext->flush();
   *         }
   * #endif
   *
   *         SkPath clip = SkPathBuilder().moveTo(-50, 20)
   *                                      .cubicTo(-50, -20, 50, -20, 50, 40)
   *                                      .cubicTo(20, 0, -20, 0, -50, 20)
   *                                      .transform(SkMatrix::Translate(64, 70))
   *                                      .detach();
   *         for (int i = 0; i < 4; ++i) {
   *             SkPath rotatedClip = clip.makeTransform(SkMatrix::RotateDeg(30 * i + 128, {64, 70}));
   *             rotatedClip.setIsVolatile(true);
   *             canvas->clipPath(rotatedClip, SkClipOp::kDifference, true);
   *         }
   *         SkPath path = SkPathBuilder().moveTo(20, 0)
   *                                      .lineTo(108, 0).cubicTo(108, 20, 108, 20, 128, 20)
   *                                      .lineTo(128, 108).cubicTo(108, 108, 108, 108, 108, 128)
   *                                      .lineTo(20, 128).cubicTo(20, 108, 20, 108, 0, 108)
   *                                      .lineTo(0, 20).cubicTo(20, 20, 20, 20, 20, 0)
   *                                      .detach();
   *         path.setIsVolatile(true);
   *         SkPaint teal;
   *         teal.setColor4f({.03f, .91f, .87f, 1});
   *         teal.setAntiAlias(true);
   *         canvas->drawPath(path, teal);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
