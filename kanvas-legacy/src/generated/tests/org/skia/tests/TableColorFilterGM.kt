package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TableColorFilterGM : public skiagm::GM {
 * public:
 *     TableColorFilterGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("tablecolorfilter"); }
 *
 *     SkISize getISize() override { return {700, 1650}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(0xFFDDDDDD);
 *         canvas->translate(20, 20);
 *
 *         static sk_sp<SkColorFilter> (*gColorFilterMakers[])() = {
 *             make_null_cf, make_cf0, make_cf1, make_cf2, make_cf3
 *         };
 *         static void (*gBitmapMakers[])(SkBitmap*) = { make_bm0, make_bm1 };
 *
 *         // This test will be done once for each bitmap with the results stacked vertically.
 *         // For a single bitmap the resulting image will be the following:
 *         //  - A first line with the original bitmap, followed by the image drawn once
 *         //  with each of the N color filters
 *         //  - N lines of the bitmap drawn N times, this will cover all N*N combinations of
 *         //  pair of color filters in order to test the collapsing of consecutive table
 *         //  color filters.
 *         //
 *         //  Here is a graphical representation of the result for 2 bitmaps and 2 filters
 *         //  with the number corresponding to the number of filters the bitmap goes through:
 *         //
 *         //  --bitmap1
 *         //  011
 *         //  22
 *         //  22
 *         //  --bitmap2
 *         //  011
 *         //  22
 *         //  22
 *
 *         SkScalar x = 0, y = 0;
 *         for (size_t bitmapMaker = 0; bitmapMaker < std::size(gBitmapMakers); ++bitmapMaker) {
 *             SkBitmap bm;
 *             gBitmapMakers[bitmapMaker](&bm);
 *
 *             SkScalar xOffset = SkScalar(bm.width() * 9 / 8);
 *             SkScalar yOffset = SkScalar(bm.height() * 9 / 8);
 *
 *             // Draw the first element of the first line
 *             x = 0;
 *             SkPaint paint;
 *             SkSamplingOptions sampling;
 *
 *             canvas->drawImage(bm.asImage(), x, y);
 *
 *             // Draws the rest of the first line for this bitmap
 *             // each draw being at xOffset of the previous one
 *             for (unsigned i = 1; i < std::size(gColorFilterMakers); ++i) {
 *                 x += xOffset;
 *                 paint.setColorFilter(gColorFilterMakers[i]());
 *                 canvas->drawImage(bm.asImage(), x, y, sampling, &paint);
 *             }
 *
 *             paint.setColorFilter(nullptr);
 *
 *             for (unsigned i = 0; i < std::size(gColorFilterMakers); ++i) {
 *                 sk_sp<SkColorFilter> colorFilter1(gColorFilterMakers[i]());
 *                 sk_sp<SkImageFilter> imageFilter1(SkImageFilters::ColorFilter(
 *                         std::move(colorFilter1), nullptr));
 *
 *                 // Move down to the next line and draw it
 *                 // each draw being at xOffset of the previous one
 *                 y += yOffset;
 *                 x = 0;
 *                 for (unsigned j = 1; j < std::size(gColorFilterMakers); ++j) {
 *                     sk_sp<SkColorFilter> colorFilter2(gColorFilterMakers[j]());
 *                     sk_sp<SkImageFilter> imageFilter2(SkImageFilters::ColorFilter(
 *                             std::move(colorFilter2), imageFilter1, nullptr));
 *                     paint.setImageFilter(std::move(imageFilter2));
 *                     canvas->drawImage(bm.asImage(), x, y, sampling, &paint);
 *                     x += xOffset;
 *                 }
 *             }
 *
 *             // Move down one line to the beginning of the block for next bitmap
 *             y += yOffset;
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TableColorFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("tablecolorfilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {700, 1650}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawColor(0xFFDDDDDD);
   *         canvas->translate(20, 20);
   *
   *         static sk_sp<SkColorFilter> (*gColorFilterMakers[])() = {
   *             make_null_cf, make_cf0, make_cf1, make_cf2, make_cf3
   *         };
   *         static void (*gBitmapMakers[])(SkBitmap*) = { make_bm0, make_bm1 };
   *
   *         // This test will be done once for each bitmap with the results stacked vertically.
   *         // For a single bitmap the resulting image will be the following:
   *         //  - A first line with the original bitmap, followed by the image drawn once
   *         //  with each of the N color filters
   *         //  - N lines of the bitmap drawn N times, this will cover all N*N combinations of
   *         //  pair of color filters in order to test the collapsing of consecutive table
   *         //  color filters.
   *         //
   *         //  Here is a graphical representation of the result for 2 bitmaps and 2 filters
   *         //  with the number corresponding to the number of filters the bitmap goes through:
   *         //
   *         //  --bitmap1
   *         //  011
   *         //  22
   *         //  22
   *         //  --bitmap2
   *         //  011
   *         //  22
   *         //  22
   *
   *         SkScalar x = 0, y = 0;
   *         for (size_t bitmapMaker = 0; bitmapMaker < std::size(gBitmapMakers); ++bitmapMaker) {
   *             SkBitmap bm;
   *             gBitmapMakers[bitmapMaker](&bm);
   *
   *             SkScalar xOffset = SkScalar(bm.width() * 9 / 8);
   *             SkScalar yOffset = SkScalar(bm.height() * 9 / 8);
   *
   *             // Draw the first element of the first line
   *             x = 0;
   *             SkPaint paint;
   *             SkSamplingOptions sampling;
   *
   *             canvas->drawImage(bm.asImage(), x, y);
   *
   *             // Draws the rest of the first line for this bitmap
   *             // each draw being at xOffset of the previous one
   *             for (unsigned i = 1; i < std::size(gColorFilterMakers); ++i) {
   *                 x += xOffset;
   *                 paint.setColorFilter(gColorFilterMakers[i]());
   *                 canvas->drawImage(bm.asImage(), x, y, sampling, &paint);
   *             }
   *
   *             paint.setColorFilter(nullptr);
   *
   *             for (unsigned i = 0; i < std::size(gColorFilterMakers); ++i) {
   *                 sk_sp<SkColorFilter> colorFilter1(gColorFilterMakers[i]());
   *                 sk_sp<SkImageFilter> imageFilter1(SkImageFilters::ColorFilter(
   *                         std::move(colorFilter1), nullptr));
   *
   *                 // Move down to the next line and draw it
   *                 // each draw being at xOffset of the previous one
   *                 y += yOffset;
   *                 x = 0;
   *                 for (unsigned j = 1; j < std::size(gColorFilterMakers); ++j) {
   *                     sk_sp<SkColorFilter> colorFilter2(gColorFilterMakers[j]());
   *                     sk_sp<SkImageFilter> imageFilter2(SkImageFilters::ColorFilter(
   *                             std::move(colorFilter2), imageFilter1, nullptr));
   *                     paint.setImageFilter(std::move(imageFilter2));
   *                     canvas->drawImage(bm.asImage(), x, y, sampling, &paint);
   *                     x += xOffset;
   *                 }
   *             }
   *
   *             // Move down one line to the beginning of the block for next bitmap
   *             y += yOffset;
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
