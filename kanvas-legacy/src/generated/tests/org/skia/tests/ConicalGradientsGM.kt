package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ConicalGradientsGM : public GM {
 * public:
 *     ConicalGradientsGM(GradCaseType gradCaseType, bool dither,
 *                        SkTileMode mode = SkTileMode::kClamp)
 *         : fGradCaseType(gradCaseType)
 *         , fDither(dither)
 *         , fMode(mode) {
 *         fName.printf("gradients_2pt_conical_%s%s", gGradCases[gradCaseType].fName,
 *                      fDither ? "" : "_nodither");
 *         switch (mode) {
 *         case SkTileMode::kRepeat:
 *             fName.appendf("_repeat");
 *             break;
 *         case SkTileMode::kMirror:
 *             fName.appendf("_mirror");
 *             break;
 *         default:
 *             break;
 *         }
 *     }
 *
 * private:
 *     void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 *
 *     SkString getName() const override { return fName; }
 *
 *     SkISize getISize() override { return {840, 815}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPoint pts[2] = {
 *             { 0, 0 },
 *             { SkIntToScalar(100), SkIntToScalar(100) }
 *         };
 *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *
 *         const GradMaker* gradMaker = gGradCases[fGradCaseType].fMaker;
 *         const int count = gGradCases[fGradCaseType].fCount;
 *
 *         for (size_t i = 0; i < std::size(gGradData); i++) {
 *             canvas->save();
 *             for (int j = 0; j < count; j++) {
 *                 SkMatrix scale = SkMatrix::I();
 *
 *                 if (i == 3) { // if the clamp case
 *                     scale.setScale(0.5f, 0.5f);
 *                     scale.postTranslate(25.f, 25.f);
 *                 }
 *
 *                 paint.setShader(gradMaker[j](pts, gGradData[i], fMode, scale));
 *                 canvas->drawRect(r, paint);
 *                 canvas->translate(0, SkIntToScalar(120));
 *             }
 *             canvas->restore();
 *             canvas->translate(SkIntToScalar(120), 0);
 *         }
 *     }
 *
 * private:
 *     GradCaseType fGradCaseType;
 *     SkString fName;
 *     bool fDither;
 *     SkTileMode fMode;
 * }
 * ```
 */
public open class ConicalGradientsGM public constructor(
  gradCaseType: GradCaseType,
  dither: Boolean,
  mode: SkTileMode = TODO(),
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * GradCaseType fGradCaseType
   * ```
   */
  private var fGradCaseType: GradCaseType = TODO("Initialize fGradCaseType")

  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fMode
   * ```
   */
  private var fMode: SkTileMode = TODO("Initialize fMode")

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
   * SkString getName() const override { return fName; }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {840, 815}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         SkPoint pts[2] = {
   *             { 0, 0 },
   *             { SkIntToScalar(100), SkIntToScalar(100) }
   *         };
   *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *
   *         const GradMaker* gradMaker = gGradCases[fGradCaseType].fMaker;
   *         const int count = gGradCases[fGradCaseType].fCount;
   *
   *         for (size_t i = 0; i < std::size(gGradData); i++) {
   *             canvas->save();
   *             for (int j = 0; j < count; j++) {
   *                 SkMatrix scale = SkMatrix::I();
   *
   *                 if (i == 3) { // if the clamp case
   *                     scale.setScale(0.5f, 0.5f);
   *                     scale.postTranslate(25.f, 25.f);
   *                 }
   *
   *                 paint.setShader(gradMaker[j](pts, gGradData[i], fMode, scale));
   *                 canvas->drawRect(r, paint);
   *                 canvas->translate(0, SkIntToScalar(120));
   *             }
   *             canvas->restore();
   *             canvas->translate(SkIntToScalar(120), 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
