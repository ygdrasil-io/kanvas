package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SimpleOffsetImageFilterGM : public skiagm::GM {
 * public:
 *     SimpleOffsetImageFilterGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("simple-offsetimagefilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 200); }
 *
 *     void doDraw(SkCanvas* canvas, const SkRect& r, sk_sp<SkImageFilter> imgf,
 *                 const SkIRect* cropR = nullptr, const SkRect* clipR = nullptr) {
 *         SkPaint p;
 *
 *         if (clipR) {
 *             p.setColor(0xFF00FF00);
 *             p.setStyle(SkPaint::kStroke_Style);
 *             canvas->drawRect(clipR->makeInset(SK_ScalarHalf, SK_ScalarHalf), p);
 *             p.setStyle(SkPaint::kFill_Style);
 *         }
 *
 *         // Visualize the crop rect for debugging
 *         if (imgf && cropR) {
 *             p.setColor(0x66FF00FF);
 *             p.setStyle(SkPaint::kStroke_Style);
 *
 *             SkRect cr = SkRect::Make(*cropR).makeInset(SK_ScalarHalf, SK_ScalarHalf);
 *             canvas->drawRect(cr, p);
 *             p.setStyle(SkPaint::kFill_Style);
 *         }
 *
 *         p.setColor(0x660000FF);
 *         canvas->drawRect(r, p);
 *
 *         if (clipR) {
 *             canvas->save();
 *             canvas->clipRect(*clipR);
 *         }
 *         if (imgf) {
 *             p.setImageFilter(std::move(imgf));
 *         }
 *         p.setColor(0x66FF0000);
 *         canvas->drawRect(r, p);
 *
 *         if (clipR) {
 *             canvas->restore();
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkIRect cr0 = SkIRect::MakeWH(40, 40);
 *         SkIRect cr1 = SkIRect::MakeWH(20, 20);
 *         SkIRect cr2 = SkIRect::MakeXYWH(40, 0, 40, 40);
 *         const SkRect r = SkRect::Make(cr0);
 *         const SkRect r2 = SkRect::Make(cr2);
 *
 *         canvas->translate(40, 40);
 *
 *         canvas->save();
 *         this->doDraw(canvas, r, nullptr);
 *
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr));
 *
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr, &cr0), &cr0);
 *
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr), /* cropR */ nullptr, &r);
 *
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr, &cr1), &cr1);
 *
 *         SkRect clipR = SkRect::MakeXYWH(40, 40, 40, 40);
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr), /* cropR */ nullptr, &clipR);
 *         canvas->restore();
 *
 *         // 2nd row
 *         canvas->translate(0, 80);
 *
 *         /*
 *          *  combos of clip and crop rects that align with src and dst
 *          */
 *
 *         // crop==clip==src
 *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr0), &cr0, &r);
 *
 *         // crop==src, clip==dst
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr0), &cr0, &r2);
 *
 *         // crop==dst, clip==src
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr2), &cr2, &r);
 *
 *         // crop==clip==dst
 *         canvas->translate(100, 0);
 *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr2), &cr2, &r2);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class SimpleOffsetImageFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("simple-offsetimagefilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void doDraw(SkCanvas* canvas, const SkRect& r, sk_sp<SkImageFilter> imgf,
   *                 const SkIRect* cropR = nullptr, const SkRect* clipR = nullptr) {
   *         SkPaint p;
   *
   *         if (clipR) {
   *             p.setColor(0xFF00FF00);
   *             p.setStyle(SkPaint::kStroke_Style);
   *             canvas->drawRect(clipR->makeInset(SK_ScalarHalf, SK_ScalarHalf), p);
   *             p.setStyle(SkPaint::kFill_Style);
   *         }
   *
   *         // Visualize the crop rect for debugging
   *         if (imgf && cropR) {
   *             p.setColor(0x66FF00FF);
   *             p.setStyle(SkPaint::kStroke_Style);
   *
   *             SkRect cr = SkRect::Make(*cropR).makeInset(SK_ScalarHalf, SK_ScalarHalf);
   *             canvas->drawRect(cr, p);
   *             p.setStyle(SkPaint::kFill_Style);
   *         }
   *
   *         p.setColor(0x660000FF);
   *         canvas->drawRect(r, p);
   *
   *         if (clipR) {
   *             canvas->save();
   *             canvas->clipRect(*clipR);
   *         }
   *         if (imgf) {
   *             p.setImageFilter(std::move(imgf));
   *         }
   *         p.setColor(0x66FF0000);
   *         canvas->drawRect(r, p);
   *
   *         if (clipR) {
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected fun doDraw(
    canvas: SkCanvas?,
    r: SkRect,
    imgf: SkSp<SkImageFilter>,
    cropR: SkIRect? = TODO(),
    clipR: SkRect? = TODO(),
  ) {
    TODO("Implement doDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkIRect cr0 = SkIRect::MakeWH(40, 40);
   *         SkIRect cr1 = SkIRect::MakeWH(20, 20);
   *         SkIRect cr2 = SkIRect::MakeXYWH(40, 0, 40, 40);
   *         const SkRect r = SkRect::Make(cr0);
   *         const SkRect r2 = SkRect::Make(cr2);
   *
   *         canvas->translate(40, 40);
   *
   *         canvas->save();
   *         this->doDraw(canvas, r, nullptr);
   *
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr));
   *
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr, &cr0), &cr0);
   *
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr), /* cropR */ nullptr, &r);
   *
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr, &cr1), &cr1);
   *
   *         SkRect clipR = SkRect::MakeXYWH(40, 40, 40, 40);
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(20, 20, nullptr), /* cropR */ nullptr, &clipR);
   *         canvas->restore();
   *
   *         // 2nd row
   *         canvas->translate(0, 80);
   *
   *         /*
   *          *  combos of clip and crop rects that align with src and dst
   *          */
   *
   *         // crop==clip==src
   *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr0), &cr0, &r);
   *
   *         // crop==src, clip==dst
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr0), &cr0, &r2);
   *
   *         // crop==dst, clip==src
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr2), &cr2, &r);
   *
   *         // crop==clip==dst
   *         canvas->translate(100, 0);
   *         this->doDraw(canvas, r, SkImageFilters::Offset(40, 0, nullptr, &cr2), &cr2, &r2);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
