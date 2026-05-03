package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TallStretchedBitmapsGM : public skiagm::GM {
 * public:
 *     TallStretchedBitmapsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("tall_stretched_bitmaps"); }
 *
 *     SkISize getISize() override { return SkISize::Make(730, 690); }
 *
 *     void onOnceBeforeDraw() override {
 *         for (size_t i = 0; i < std::size(fTallBmps); ++i) {
 *             int h = SkToInt((4 + i) * 1024);
 *
 *             fTallBmps[i].fItemCnt = make_bm(&fTallBmps[i].fBmp, h);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(1.3f, 1.3f);
 *         for (size_t i = 0; i < std::size(fTallBmps); ++i) {
 *             SkASSERT(fTallBmps[i].fItemCnt > 10);
 *             SkBitmap bmp = fTallBmps[i].fBmp;
 *             // Draw the last 10 elements of the bitmap.
 *             int startItem = fTallBmps[i].fItemCnt - 10;
 *             int itemHeight = bmp.height() / fTallBmps[i].fItemCnt;
 *             SkIRect subRect = SkIRect::MakeLTRB(0, startItem * itemHeight,
 *                                                bmp.width(), bmp.height());
 *             SkRect dstRect = SkRect::MakeWH(SkIntToScalar(bmp.width()), 10.f * itemHeight);
 *             SkTiledImageUtils::DrawImageRect(canvas, bmp.asImage(),
 *                                              SkRect::Make(subRect), dstRect,
 *                                              SkSamplingOptions(SkFilterMode::kLinear), nullptr,
 *                                              SkCanvas::kStrict_SrcRectConstraint);
 *             canvas->translate(SkIntToScalar(bmp.width() + 10), 0);
 *         }
 *     }
 *
 * private:
 *     struct {
 *         SkBitmap fBmp;
 *         int      fItemCnt;
 *     } fTallBmps[8];
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TallStretchedBitmapsGM public constructor() : GM() {
  public var fBmp: SkBitmap = TODO("Initialize fBmp")

  public var fItemCnt: Int = TODO("Initialize fItemCnt")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("tall_stretched_bitmaps"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(730, 690); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (size_t i = 0; i < std::size(fTallBmps); ++i) {
   *             int h = SkToInt((4 + i) * 1024);
   *
   *             fTallBmps[i].fItemCnt = make_bm(&fTallBmps[i].fBmp, h);
   *         }
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
   *         canvas->scale(1.3f, 1.3f);
   *         for (size_t i = 0; i < std::size(fTallBmps); ++i) {
   *             SkASSERT(fTallBmps[i].fItemCnt > 10);
   *             SkBitmap bmp = fTallBmps[i].fBmp;
   *             // Draw the last 10 elements of the bitmap.
   *             int startItem = fTallBmps[i].fItemCnt - 10;
   *             int itemHeight = bmp.height() / fTallBmps[i].fItemCnt;
   *             SkIRect subRect = SkIRect::MakeLTRB(0, startItem * itemHeight,
   *                                                bmp.width(), bmp.height());
   *             SkRect dstRect = SkRect::MakeWH(SkIntToScalar(bmp.width()), 10.f * itemHeight);
   *             SkTiledImageUtils::DrawImageRect(canvas, bmp.asImage(),
   *                                              SkRect::Make(subRect), dstRect,
   *                                              SkSamplingOptions(SkFilterMode::kLinear), nullptr,
   *                                              SkCanvas::kStrict_SrcRectConstraint);
   *             canvas->translate(SkIntToScalar(bmp.width() + 10), 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
