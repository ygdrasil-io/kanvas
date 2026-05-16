package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RuntimeColorFilterGM : public skiagm::GM {
 * public:
 *     RuntimeColorFilterGM() = default;
 *
 * protected:
 *     SkString getName() const override { return SkString("runtimecolorfilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(256 * 3, 256 * 2); }
 *
 *     void onOnceBeforeDraw() override {
 *         fImg = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto draw_filter = [&](const char* src) {
 *             auto [effect, err] = SkRuntimeEffect::MakeForColorFilter(SkString(src));
 *             if (!effect) {
 *                 SkDebugf("%s\n%s\n", src, err.c_str());
 *             }
 *             SkASSERT(effect);
 *             SkPaint p;
 *             p.setColorFilter(effect->makeColorFilter(nullptr));
 *             canvas->drawImage(fImg, 0, 0, SkSamplingOptions(), &p);
 *             canvas->translate(256, 0);
 *         };
 *
 *         for (const char* src : {gNoop, gLumaSrc}) {
 *             draw_filter(src);
 *         }
 *         canvas->translate(-256*2, 256);
 *         for (const char* src : {gTernary, gIfs, gEarlyReturn}) {
 *             draw_filter(src);
 *         }
 *     }
 *
 *     sk_sp<SkImage> fImg;
 * }
 * ```
 */
public open class RuntimeColorFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg
   * ```
   */
  protected var fImg: SkSp<SkImage> = TODO("Initialize fImg")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("runtimecolorfilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(256 * 3, 256 * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fImg = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
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
   *         auto draw_filter = [&](const char* src) {
   *             auto [effect, err] = SkRuntimeEffect::MakeForColorFilter(SkString(src));
   *             if (!effect) {
   *                 SkDebugf("%s\n%s\n", src, err.c_str());
   *             }
   *             SkASSERT(effect);
   *             SkPaint p;
   *             p.setColorFilter(effect->makeColorFilter(nullptr));
   *             canvas->drawImage(fImg, 0, 0, SkSamplingOptions(), &p);
   *             canvas->translate(256, 0);
   *         };
   *
   *         for (const char* src : {gNoop, gLumaSrc}) {
   *             draw_filter(src);
   *         }
   *         canvas->translate(-256*2, 256);
   *         for (const char* src : {gTernary, gIfs, gEarlyReturn}) {
   *             draw_filter(src);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
