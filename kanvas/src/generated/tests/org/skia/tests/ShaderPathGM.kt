package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ShaderPathGM : public GM {
 * public:
 *     ShaderPathGM() {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("shaderpath"); }
 *
 *     SkISize getISize() override { return SkISize::Make(820, 930); }
 *
 *     void onOnceBeforeDraw() override {
 *         makebm(&fBmp, kPointSize / 4, kPointSize / 4);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint bmpPaint;
 *         bmpPaint.setAntiAlias(true);
 *         bmpPaint.setAlphaf(0.5f);
 *         SkSamplingOptions sampling(SkFilterMode::kLinear);
 *
 *         canvas->drawImage(fBmp.asImage(), 5.f, 5.f, sampling, &bmpPaint);
 *
 *         SkPaint outlinePaint;
 *         outlinePaint.setStyle(SkPaint::kStroke_Style);
 *         outlinePaint.setStrokeWidth(0.f);
 *
 *         canvas->translate(15.f, 15.f);
 *         canvas->scale(2.f, 2.f);
 *
 *         constexpr SkTileMode kTileModes[] = {
 *             SkTileMode::kRepeat,
 *             SkTileMode::kMirror,
 *         };
 *
 *         // position the baseline of the first path
 *         canvas->translate(0.f, 2.25);
 *
 *         SkPath path = SkPathBuilder()
 *                       .moveTo(0, 40)
 *                       .cubicTo(10, 70, 20, 10, 30, 40)
 *                       .detach();
 *
 *         canvas->save();
 *         int i = 0;
 *         for (size_t tm0 = 0; tm0 < std::size(kTileModes); ++tm0) {
 *             for (size_t tm1 = 0; tm1 < std::size(kTileModes); ++tm1) {
 *                 SkMatrix localM;
 *                 localM.setTranslate(5.f, 5.f);
 *                 localM.postRotate(20);
 *                 localM.postScale(1.15f, .85f);
 *
 *                 SkPaint fillPaint;
 *                 fillPaint.setAntiAlias(true);
 *                 fillPaint.setShader(fBmp.makeShader(kTileModes[tm0], kTileModes[tm1],
 *                                                     sampling, localM));
 *
 *                 canvas->drawPath(path, fillPaint);
 *                 canvas->drawPath(path, outlinePaint);
 *                 canvas->translate(50.f, 0.f);
 *                 ++i;
 *                 if (!(i % 2)) {
 *                     canvas->restore();
 *                     canvas->translate(0, 22.5f);
 *                     canvas->save();
 *                 }
 *             }
 *         }
 *         canvas->restore();
 *     }
 *
 * private:
 *     SkBitmap fBmp;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ShaderPathGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBmp
   * ```
   */
  private var fBmp: SkBitmap = TODO("Initialize fBmp")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("shaderpath"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(820, 930); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         makebm(&fBmp, kPointSize / 4, kPointSize / 4);
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
   *
   *         SkPaint bmpPaint;
   *         bmpPaint.setAntiAlias(true);
   *         bmpPaint.setAlphaf(0.5f);
   *         SkSamplingOptions sampling(SkFilterMode::kLinear);
   *
   *         canvas->drawImage(fBmp.asImage(), 5.f, 5.f, sampling, &bmpPaint);
   *
   *         SkPaint outlinePaint;
   *         outlinePaint.setStyle(SkPaint::kStroke_Style);
   *         outlinePaint.setStrokeWidth(0.f);
   *
   *         canvas->translate(15.f, 15.f);
   *         canvas->scale(2.f, 2.f);
   *
   *         constexpr SkTileMode kTileModes[] = {
   *             SkTileMode::kRepeat,
   *             SkTileMode::kMirror,
   *         };
   *
   *         // position the baseline of the first path
   *         canvas->translate(0.f, 2.25);
   *
   *         SkPath path = SkPathBuilder()
   *                       .moveTo(0, 40)
   *                       .cubicTo(10, 70, 20, 10, 30, 40)
   *                       .detach();
   *
   *         canvas->save();
   *         int i = 0;
   *         for (size_t tm0 = 0; tm0 < std::size(kTileModes); ++tm0) {
   *             for (size_t tm1 = 0; tm1 < std::size(kTileModes); ++tm1) {
   *                 SkMatrix localM;
   *                 localM.setTranslate(5.f, 5.f);
   *                 localM.postRotate(20);
   *                 localM.postScale(1.15f, .85f);
   *
   *                 SkPaint fillPaint;
   *                 fillPaint.setAntiAlias(true);
   *                 fillPaint.setShader(fBmp.makeShader(kTileModes[tm0], kTileModes[tm1],
   *                                                     sampling, localM));
   *
   *                 canvas->drawPath(path, fillPaint);
   *                 canvas->drawPath(path, outlinePaint);
   *                 canvas->translate(50.f, 0.f);
   *                 ++i;
   *                 if (!(i % 2)) {
   *                     canvas->restore();
   *                     canvas->translate(0, 22.5f);
   *                     canvas->save();
   *                 }
   *             }
   *         }
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
