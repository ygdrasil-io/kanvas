package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FilterBugGM : public skiagm::GM {
 * public:
 *     FilterBugGM() { this->setBGColor(SK_ColorRED); }
 *
 * protected:
 *     SkString getName() const override { return SkString("filterbug"); }
 *
 *     SkISize getISize() override { return SkISize::Make(150, 150); }
 *
 *     void onOnceBeforeDraw() override {
 *         // The top texture has 5 black rows on top and then 22 white rows on the bottom
 *         fTop = make_image(0, 5);
 *         // The bottom texture has 5 black rows on the bottom and then 22 white rows on the top
 *         fBot = make_image(22, 27);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static const SkSamplingOptions kSampling(SkCubicResampler::Mitchell());
 *         static const bool kDoAA = true;
 *
 *         {
 *             SkRect r1 = SkRect::MakeXYWH(50.0f, 0.0f, 50.0f, 50.0f);
 *             SkPaint p1;
 *             p1.setAntiAlias(kDoAA);
 *             SkMatrix localMat;
 *             localMat.setScaleTranslate(2.0f, 2.0f, 50.0f, 0.0f);
 *             p1.setShader(fTop->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                           kSampling, &localMat));
 *
 *             canvas->drawRect(r1, p1);
 *         }
 *
 *         {
 *             SkRect r2 = SkRect::MakeXYWH(50.0f, 50.0f, 50.0f, 36.0f);
 *
 *             SkPaint p2;
 *             p2.setColor(SK_ColorWHITE);
 *             p2.setAntiAlias(kDoAA);
 *
 *             canvas->drawRect(r2, p2);
 *         }
 *
 *         {
 *             SkRect r3 = SkRect::MakeXYWH(50.0f, 86.0f, 50.0f, 50.0f);
 *
 *             SkPaint p3;
 *             p3.setAntiAlias(kDoAA);
 *             SkMatrix localMat;
 *             localMat.setScaleTranslate(2.0f, 2.0f, 50.0f, 86.0f);
 *             p3.setShader(fBot->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                           kSampling, &localMat));
 *
 *             canvas->drawRect(r3, p3);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fTop;
 *     sk_sp<SkImage> fBot;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class FilterBugGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fTop
   * ```
   */
  private var fTop: SkSp<SkImage> = TODO("Initialize fTop")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fBot
   * ```
   */
  private var fBot: SkSp<SkImage> = TODO("Initialize fBot")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("filterbug"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(150, 150); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // The top texture has 5 black rows on top and then 22 white rows on the bottom
   *         fTop = make_image(0, 5);
   *         // The bottom texture has 5 black rows on the bottom and then 22 white rows on the top
   *         fBot = make_image(22, 27);
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
   *         static const SkSamplingOptions kSampling(SkCubicResampler::Mitchell());
   *         static const bool kDoAA = true;
   *
   *         {
   *             SkRect r1 = SkRect::MakeXYWH(50.0f, 0.0f, 50.0f, 50.0f);
   *             SkPaint p1;
   *             p1.setAntiAlias(kDoAA);
   *             SkMatrix localMat;
   *             localMat.setScaleTranslate(2.0f, 2.0f, 50.0f, 0.0f);
   *             p1.setShader(fTop->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                           kSampling, &localMat));
   *
   *             canvas->drawRect(r1, p1);
   *         }
   *
   *         {
   *             SkRect r2 = SkRect::MakeXYWH(50.0f, 50.0f, 50.0f, 36.0f);
   *
   *             SkPaint p2;
   *             p2.setColor(SK_ColorWHITE);
   *             p2.setAntiAlias(kDoAA);
   *
   *             canvas->drawRect(r2, p2);
   *         }
   *
   *         {
   *             SkRect r3 = SkRect::MakeXYWH(50.0f, 86.0f, 50.0f, 50.0f);
   *
   *             SkPaint p3;
   *             p3.setAntiAlias(kDoAA);
   *             SkMatrix localMat;
   *             localMat.setScaleTranslate(2.0f, 2.0f, 50.0f, 86.0f);
   *             p3.setShader(fBot->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                           kSampling, &localMat));
   *
   *             canvas->drawRect(r3, p3);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
