package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class KawaseBlurRT : public skiagm::GM {
 * public:
 *     KawaseBlurRT() {}
 *     SkString getName() const override { return SkString("kawase_blur_rt"); }
 *     SkISize getISize() override { return {1280, 768}; }
 *
 *     void onOnceBeforeDraw() override {
 *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawImage(fMandrill, 0, 0);
 *         canvas->translate(256, 0);
 *         KawaseBlurFilter blurFilter;
 *         blurFilter.draw(canvas, fMandrill, 45);
 *         canvas->translate(512, 0);
 *         blurFilter.draw(canvas, fMandrill, 55);
 *     }
 *
 * private:
 *     sk_sp<SkImage> fMandrill;
 * }
 * ```
 */
public open class KawaseBlurRT public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMandrill
   * ```
   */
  private var fMandrill: SkSp<SkImage> = TODO("Initialize fMandrill")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("kawase_blur_rt"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1280, 768}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fMandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawImage(fMandrill, 0, 0);
   *         canvas->translate(256, 0);
   *         KawaseBlurFilter blurFilter;
   *         blurFilter.draw(canvas, fMandrill, 45);
   *         canvas->translate(512, 0);
   *         blurFilter.draw(canvas, fMandrill, 55);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
