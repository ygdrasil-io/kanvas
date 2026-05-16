package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RRectBlurGM : public skiagm::GM {
 * public:
 *     RRectBlurGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("rrect_blurs"); }
 *
 *     static constexpr int kWidth = 300;
 *     static constexpr int kHeight = 400;
 *     // how much to exagerate the diffs
 *     static constexpr int kDiffMaginification = 16;
 *     static constexpr bool kPrintDiffMetrics = false;
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     static void draw_blurry_rrect(
 *             SkCanvas* canvas, int cellY, sk_sp<SkMaskFilter> mf, SkColor color, const SkRRect& rr) {
 *         const int kCellSize = 100;
 *         SkPaint rrectPaint;
 *         rrectPaint.setColor(color);
 *         rrectPaint.setMaskFilter(mf);
 *
 *         const int paddingX = (kCellSize - rr.width()) / 2;
 *         const int paddingY = (kCellSize - rr.height()) / 2;
 *         const SkRRect left = rr.makeOffset(paddingX, paddingY + cellY);
 *         canvas->drawRRect(left, rrectPaint);
 *
 *         const SkRRect right = rr.makeOffset(2 * kCellSize + paddingX, paddingY + cellY);
 *         canvas->drawPath(SkPath::RRect(right), rrectPaint);
 *
 *         // In an ideal world, there would be no diffs at all between the two drawing
 *         // methods. The point of this gm is to show those differences and allow us to
 *         // measure the differences.
 *         SkBitmap leftBitmap;
 *         leftBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
 *         SkImageInfo infoLeft = leftBitmap.info();
 *         if (!canvas->readPixels(infoLeft,
 *                                 leftBitmap.pixmap().writable_addr(),
 *                                 infoLeft.minRowBytes(),
 *                                 0,
 *                                 cellY)) {
 *             return;
 *         }
 *
 *         SkBitmap rightBitmap;
 *         rightBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
 *         SkImageInfo infoRight = rightBitmap.info();
 *         if (!canvas->readPixels(infoRight,
 *                                 rightBitmap.pixmap().writable_addr(),
 *                                 infoRight.minRowBytes(),
 *                                 2 * kCellSize,
 *                                 cellY)) {
 *             return;
 *         }
 *
 *         int diffPixels = 0;
 *         SkBitmap diffBitmap;
 *         diffBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
 *         for (int y = 0; y < kCellSize; ++y) {
 *             for (int x = 0; x < kCellSize; ++x) {
 *                 SkColor leftColor = leftBitmap.getColor(x, y);
 *                 SkColor rightColor = rightBitmap.getColor(x, y);
 *                 // Add up the diffs in the 4 channels, then treat that as how bright
 *                 // to draw the diff
 *                 int diff = abs((int)(SkColorGetA(leftColor) - SkColorGetA(rightColor))) +
 *                            abs((int)(SkColorGetR(leftColor) - SkColorGetR(rightColor))) +
 *                            abs((int)(SkColorGetG(leftColor) - SkColorGetG(rightColor))) +
 *                            abs((int)(SkColorGetB(leftColor) - SkColorGetB(rightColor)));
 *                 SkASSERT(diff >= 0);
 *                 const U8CPU grey = std::min(diff * kDiffMaginification, 255);
 *                 if (grey > 0) {
 *                     diffPixels++;
 *                 }
 *                 *diffBitmap.pixmap().writable_addr32(x, y) = SkColorSetARGB(0xFF, grey, grey, grey);
 *             }
 *         }
 *         if (kPrintDiffMetrics) {
 *             SkDebugf("%d pixels diff\n", diffPixels);
 *         }
 *
 *         canvas->writePixels(diffBitmap, kCellSize, cellY);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Because of the read/write pixels, this doesn't draw right if viewer zooms in.
 *         canvas->resetMatrix();
 *         canvas->clear(SK_ColorDKGRAY);
 *
 *         draw_blurry_rrect(canvas, 0,
 *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 1.0f, false /*=respectCTM*/),
 *                           SK_ColorWHITE,
 *                           SkRRect::MakeRectXY(SkRect::MakeWH(50, 50), 10, 15));
 *
 *         draw_blurry_rrect(canvas, 100,
 *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 0.5f, false /*=respectCTM*/),
 *                           SK_ColorYELLOW,
 *                           SkRRect::MakeRectXY(SkRect::MakeWH(60, 80), 3.1f, 1.5f));
 *
 *         SkRRect rr;
 *         rr.setNinePatch(SkRect::MakeWH(70, 80),
 *                         5,   // left
 *                         10,  // top
 *                         13,  // right
 *                         7);  // bottom
 *         draw_blurry_rrect(canvas, 200,
 *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 2.5f, false /*=respectCTM*/),
 *                           SkColorSetARGB(255, 200, 100, 30),
 *                           rr);
 *
 *         SkVector radii[4] = {{0, 0}, {20, 1}, {10, 30}, {30, 30}};
 *         rr.setRectRadii(SkRect::MakeWH(90, 90), radii);
 *         draw_blurry_rrect(canvas, 300,
 *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 1.1f, false /*=respectCTM*/),
 *                           SkColorSetARGB(255, 35, 120, 220),
 *                           rr);
 *
 *         // labels after to avoid contaminating the diffs
 *         SkPaint labelPaint;
 *         labelPaint.setColor(SK_ColorWHITE);
 *         labelPaint.setAntiAlias(true);
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         canvas->drawString("drawRRect", 15, 15, font, labelPaint);
 *         canvas->drawString("diff", 140, 15, font, labelPaint);
 *         canvas->drawString("drawPath", 220, 15, font, labelPaint);
 *         canvas->drawLine(100, 0, 100, kHeight, labelPaint);
 *         canvas->drawLine(200, 0, 200, kHeight, labelPaint);
 *         canvas->drawLine(0, 100, kWidth, 100, labelPaint);
 *         canvas->drawLine(0, 200, kWidth, 200, labelPaint);
 *         canvas->drawLine(0, 300, kWidth, 300, labelPaint);
 *     }
 * }
 * ```
 */
public open class RRectBlurGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("rrect_blurs"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // Because of the read/write pixels, this doesn't draw right if viewer zooms in.
   *         canvas->resetMatrix();
   *         canvas->clear(SK_ColorDKGRAY);
   *
   *         draw_blurry_rrect(canvas, 0,
   *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 1.0f, false /*=respectCTM*/),
   *                           SK_ColorWHITE,
   *                           SkRRect::MakeRectXY(SkRect::MakeWH(50, 50), 10, 15));
   *
   *         draw_blurry_rrect(canvas, 100,
   *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 0.5f, false /*=respectCTM*/),
   *                           SK_ColorYELLOW,
   *                           SkRRect::MakeRectXY(SkRect::MakeWH(60, 80), 3.1f, 1.5f));
   *
   *         SkRRect rr;
   *         rr.setNinePatch(SkRect::MakeWH(70, 80),
   *                         5,   // left
   *                         10,  // top
   *                         13,  // right
   *                         7);  // bottom
   *         draw_blurry_rrect(canvas, 200,
   *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 2.5f, false /*=respectCTM*/),
   *                           SkColorSetARGB(255, 200, 100, 30),
   *                           rr);
   *
   *         SkVector radii[4] = {{0, 0}, {20, 1}, {10, 30}, {30, 30}};
   *         rr.setRectRadii(SkRect::MakeWH(90, 90), radii);
   *         draw_blurry_rrect(canvas, 300,
   *                           SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 1.1f, false /*=respectCTM*/),
   *                           SkColorSetARGB(255, 35, 120, 220),
   *                           rr);
   *
   *         // labels after to avoid contaminating the diffs
   *         SkPaint labelPaint;
   *         labelPaint.setColor(SK_ColorWHITE);
   *         labelPaint.setAntiAlias(true);
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         canvas->drawString("drawRRect", 15, 15, font, labelPaint);
   *         canvas->drawString("diff", 140, 15, font, labelPaint);
   *         canvas->drawString("drawPath", 220, 15, font, labelPaint);
   *         canvas->drawLine(100, 0, 100, kHeight, labelPaint);
   *         canvas->drawLine(200, 0, 200, kHeight, labelPaint);
   *         canvas->drawLine(0, 100, kWidth, 100, labelPaint);
   *         canvas->drawLine(0, 200, kWidth, 200, labelPaint);
   *         canvas->drawLine(0, 300, kWidth, 300, labelPaint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    protected val kWidth: Int = TODO("Initialize kWidth")

    protected val kHeight: Int = TODO("Initialize kHeight")

    protected val kDiffMaginification: Int = TODO("Initialize kDiffMaginification")

    protected val kPrintDiffMetrics: Boolean = TODO("Initialize kPrintDiffMetrics")

    /**
     * C++ original:
     * ```cpp
     * static void draw_blurry_rrect(
     *             SkCanvas* canvas, int cellY, sk_sp<SkMaskFilter> mf, SkColor color, const SkRRect& rr) {
     *         const int kCellSize = 100;
     *         SkPaint rrectPaint;
     *         rrectPaint.setColor(color);
     *         rrectPaint.setMaskFilter(mf);
     *
     *         const int paddingX = (kCellSize - rr.width()) / 2;
     *         const int paddingY = (kCellSize - rr.height()) / 2;
     *         const SkRRect left = rr.makeOffset(paddingX, paddingY + cellY);
     *         canvas->drawRRect(left, rrectPaint);
     *
     *         const SkRRect right = rr.makeOffset(2 * kCellSize + paddingX, paddingY + cellY);
     *         canvas->drawPath(SkPath::RRect(right), rrectPaint);
     *
     *         // In an ideal world, there would be no diffs at all between the two drawing
     *         // methods. The point of this gm is to show those differences and allow us to
     *         // measure the differences.
     *         SkBitmap leftBitmap;
     *         leftBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
     *         SkImageInfo infoLeft = leftBitmap.info();
     *         if (!canvas->readPixels(infoLeft,
     *                                 leftBitmap.pixmap().writable_addr(),
     *                                 infoLeft.minRowBytes(),
     *                                 0,
     *                                 cellY)) {
     *             return;
     *         }
     *
     *         SkBitmap rightBitmap;
     *         rightBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
     *         SkImageInfo infoRight = rightBitmap.info();
     *         if (!canvas->readPixels(infoRight,
     *                                 rightBitmap.pixmap().writable_addr(),
     *                                 infoRight.minRowBytes(),
     *                                 2 * kCellSize,
     *                                 cellY)) {
     *             return;
     *         }
     *
     *         int diffPixels = 0;
     *         SkBitmap diffBitmap;
     *         diffBitmap.allocPixels(SkImageInfo::MakeN32Premul(kCellSize, kCellSize));
     *         for (int y = 0; y < kCellSize; ++y) {
     *             for (int x = 0; x < kCellSize; ++x) {
     *                 SkColor leftColor = leftBitmap.getColor(x, y);
     *                 SkColor rightColor = rightBitmap.getColor(x, y);
     *                 // Add up the diffs in the 4 channels, then treat that as how bright
     *                 // to draw the diff
     *                 int diff = abs((int)(SkColorGetA(leftColor) - SkColorGetA(rightColor))) +
     *                            abs((int)(SkColorGetR(leftColor) - SkColorGetR(rightColor))) +
     *                            abs((int)(SkColorGetG(leftColor) - SkColorGetG(rightColor))) +
     *                            abs((int)(SkColorGetB(leftColor) - SkColorGetB(rightColor)));
     *                 SkASSERT(diff >= 0);
     *                 const U8CPU grey = std::min(diff * kDiffMaginification, 255);
     *                 if (grey > 0) {
     *                     diffPixels++;
     *                 }
     *                 *diffBitmap.pixmap().writable_addr32(x, y) = SkColorSetARGB(0xFF, grey, grey, grey);
     *             }
     *         }
     *         if (kPrintDiffMetrics) {
     *             SkDebugf("%d pixels diff\n", diffPixels);
     *         }
     *
     *         canvas->writePixels(diffBitmap, kCellSize, cellY);
     *     }
     * ```
     */
    protected fun drawBlurryRrect(
      canvas: SkCanvas?,
      cellY: Int,
      mf: SkSp<SkMaskFilter>,
      color: SkColor,
      rr: SkRRect,
    ) {
      TODO("Implement drawBlurryRrect")
    }
  }
}
