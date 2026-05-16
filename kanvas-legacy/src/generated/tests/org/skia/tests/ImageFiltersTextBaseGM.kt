package org.skia.tests

import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersTextBaseGM : public skiagm::GM {
 *     SkString fSuffix;
 * public:
 *     ImageFiltersTextBaseGM(const char suffix[]) : fSuffix(suffix) {}
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name;
 *         name.printf("%s_%s", "textfilter", fSuffix.c_str());
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(512, 342); }
 *
 *     void drawWaterfall(SkCanvas* canvas, const SkPaint& paint) {
 *         static const SkFont::Edging kEdgings[3] = {
 *             SkFont::Edging::kAlias,
 *             SkFont::Edging::kAntiAlias,
 *             SkFont::Edging::kSubpixelAntiAlias,
 *         };
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 30);
 *
 *         SkAutoCanvasRestore acr(canvas, true);
 *         for (SkFont::Edging edging : kEdgings) {
 *             font.setEdging(edging);
 *             canvas->drawString("Hamburgefon", 0, 0, font, paint);
 *             canvas->translate(0, 40);
 *         }
 *     }
 *
 *     virtual void installFilter(SkPaint* paint) = 0;
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(20, 40);
 *
 *         for (int doSaveLayer = 0; doSaveLayer <= 1; ++doSaveLayer) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *             for (int useFilter = 0; useFilter <= 1; ++useFilter) {
 *                 SkAutoCanvasRestore acr2(canvas, true);
 *
 *                 SkPaint paint;
 *                 if (useFilter) {
 *                     this->installFilter(&paint);
 *                 }
 *                 if (doSaveLayer) {
 *                     canvas->saveLayer(nullptr, &paint);
 *                     paint.setImageFilter(nullptr);
 *                 }
 *                 this->drawWaterfall(canvas, paint);
 *
 *                 acr2.restore();
 *                 canvas->translate(250, 0);
 *             }
 *             acr.restore();
 *             canvas->translate(0, 200);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public abstract class ImageFiltersTextBaseGM public constructor(
  suffix: CharArray,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString fSuffix
   * ```
   */
  private var fSuffix: String = TODO("Initialize fSuffix")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name;
   *         name.printf("%s_%s", "textfilter", fSuffix.c_str());
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(512, 342); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawWaterfall(SkCanvas* canvas, const SkPaint& paint) {
   *         static const SkFont::Edging kEdgings[3] = {
   *             SkFont::Edging::kAlias,
   *             SkFont::Edging::kAntiAlias,
   *             SkFont::Edging::kSubpixelAntiAlias,
   *         };
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 30);
   *
   *         SkAutoCanvasRestore acr(canvas, true);
   *         for (SkFont::Edging edging : kEdgings) {
   *             font.setEdging(edging);
   *             canvas->drawString("Hamburgefon", 0, 0, font, paint);
   *             canvas->translate(0, 40);
   *         }
   *     }
   * ```
   */
  protected fun drawWaterfall(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement drawWaterfall")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void installFilter(SkPaint* paint) = 0
   * ```
   */
  protected abstract fun installFilter(paint: SkPaint?)

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(20, 40);
   *
   *         for (int doSaveLayer = 0; doSaveLayer <= 1; ++doSaveLayer) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *             for (int useFilter = 0; useFilter <= 1; ++useFilter) {
   *                 SkAutoCanvasRestore acr2(canvas, true);
   *
   *                 SkPaint paint;
   *                 if (useFilter) {
   *                     this->installFilter(&paint);
   *                 }
   *                 if (doSaveLayer) {
   *                     canvas->saveLayer(nullptr, &paint);
   *                     paint.setImageFilter(nullptr);
   *                 }
   *                 this->drawWaterfall(canvas, paint);
   *
   *                 acr2.restore();
   *                 canvas->translate(250, 0);
   *             }
   *             acr.restore();
   *             canvas->translate(0, 200);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
