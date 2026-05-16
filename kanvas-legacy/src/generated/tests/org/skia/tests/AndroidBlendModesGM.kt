package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AndroidBlendModesGM : public skiagm::GM {
 * public:
 *     AndroidBlendModesGM() {
 *         this->setBGColor(SK_ColorBLACK);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("androidblendmodes"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kNumCols * kBitmapSize, kNumRows * kBitmapSize);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         SkImageInfo ii = SkImageInfo::MakeN32Premul(kBitmapSize, kBitmapSize);
 *         {
 *             fCompositeSrc.allocPixels(ii);
 *             SkCanvas tmp(fCompositeSrc);
 *             tmp.clear(SK_ColorTRANSPARENT);
 *             SkPaint p;
 *             p.setAntiAlias(true);
 *             p.setColor(ToolUtils::color_to_565(kBlue));
 *             tmp.drawRect(SkRect::MakeLTRB(16, 96, 160, 240), p);
 *         }
 *
 *         {
 *             fCompositeDst.allocPixels(ii);
 *             SkCanvas tmp(fCompositeDst);
 *             tmp.clear(SK_ColorTRANSPARENT);
 *             SkPaint p;
 *             p.setAntiAlias(true);
 *             p.setColor(ToolUtils::color_to_565(kRed));
 *             tmp.drawCircle(160, 95, 80, p);
 *         }
 *     }
 *
 *     void drawTile(SkCanvas* canvas, int xOffset, int yOffset, SkBlendMode mode) {
 *         canvas->translate(xOffset, yOffset);
 *
 *         canvas->clipRect(SkRect::MakeXYWH(0, 0, 256, 256));
 *
 *         canvas->saveLayer(nullptr, nullptr);
 *
 *         SkPaint p;
 *         canvas->drawImage(fCompositeDst.asImage(), 0, 0, SkSamplingOptions(), &p);
 *         p.setBlendMode(mode);
 *         canvas->drawImage(fCompositeSrc.asImage(), 0, 0, SkSamplingOptions(), &p);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         ToolUtils::draw_checkerboard(canvas, kWhite, kGrey, 32);
 *
 *         int xOffset = 0, yOffset = 0;
 *
 *         // Android doesn't expose all the blend modes
 *         // Note that the Android documentation calls:
 *         //    Skia's kPlus,     add
 *         //    Skia's kModulate, multiply
 *         for (SkBlendMode mode : { SkBlendMode::kPlus /* add */, SkBlendMode::kClear,
 *                                   SkBlendMode::kDarken, SkBlendMode::kDst,
 *                                   SkBlendMode::kDstATop, SkBlendMode::kDstIn,
 *                                   SkBlendMode::kDstOut, SkBlendMode::kDstOver,
 *                                   SkBlendMode::kLighten, SkBlendMode::kModulate /* multiply */,
 *                                   SkBlendMode::kOverlay, SkBlendMode::kScreen,
 *                                   SkBlendMode::kSrc, SkBlendMode::kSrcATop,
 *                                   SkBlendMode::kSrcIn, SkBlendMode::kSrcOut,
 *                                   SkBlendMode::kSrcOver, SkBlendMode::kXor } ) {
 *
 *             int saveCount = canvas->save();
 *             this->drawTile(canvas, xOffset, yOffset, mode);
 *             canvas->restoreToCount(saveCount);
 *
 *             SkTextUtils::DrawString(canvas, SkBlendMode_Name(mode),
 *                                xOffset + kBitmapSize/2.0f,
 *                                yOffset + kBitmapSize,
 *                                font, SkPaint(), SkTextUtils::kCenter_Align);
 *
 *             xOffset += 256;
 *             if (xOffset >= 1024) {
 *                 xOffset = 0;
 *                 yOffset += 256;
 *             }
 *         }
 *     }
 *
 * private:
 *     static const int kBitmapSize = 256;
 *     static const int kNumRows = 5;
 *     static const int kNumCols = 4;
 *
 *     static const SkColor  kBlue  = SkColorSetARGB(255, 22, 150, 243);
 *     static const SkColor  kRed   = SkColorSetARGB(255, 233, 30, 99);
 *     static const SkColor  kWhite = SkColorSetARGB(255, 243, 243, 243);
 *     static const SkColor  kGrey  = SkColorSetARGB(255, 222, 222, 222);
 *
 *     SkBitmap fCompositeSrc;
 *     SkBitmap fCompositeDst;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class AndroidBlendModesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static const int kBitmapSize = 256
   * ```
   */
  private var fCompositeSrc: SkBitmap = TODO("Initialize fCompositeSrc")

  /**
   * C++ original:
   * ```cpp
   * static const int kNumRows = 5
   * ```
   */
  private var fCompositeDst: SkBitmap = TODO("Initialize fCompositeDst")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("androidblendmodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(kNumCols * kBitmapSize, kNumRows * kBitmapSize);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkImageInfo ii = SkImageInfo::MakeN32Premul(kBitmapSize, kBitmapSize);
   *         {
   *             fCompositeSrc.allocPixels(ii);
   *             SkCanvas tmp(fCompositeSrc);
   *             tmp.clear(SK_ColorTRANSPARENT);
   *             SkPaint p;
   *             p.setAntiAlias(true);
   *             p.setColor(ToolUtils::color_to_565(kBlue));
   *             tmp.drawRect(SkRect::MakeLTRB(16, 96, 160, 240), p);
   *         }
   *
   *         {
   *             fCompositeDst.allocPixels(ii);
   *             SkCanvas tmp(fCompositeDst);
   *             tmp.clear(SK_ColorTRANSPARENT);
   *             SkPaint p;
   *             p.setAntiAlias(true);
   *             p.setColor(ToolUtils::color_to_565(kRed));
   *             tmp.drawCircle(160, 95, 80, p);
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
   * void drawTile(SkCanvas* canvas, int xOffset, int yOffset, SkBlendMode mode) {
   *         canvas->translate(xOffset, yOffset);
   *
   *         canvas->clipRect(SkRect::MakeXYWH(0, 0, 256, 256));
   *
   *         canvas->saveLayer(nullptr, nullptr);
   *
   *         SkPaint p;
   *         canvas->drawImage(fCompositeDst.asImage(), 0, 0, SkSamplingOptions(), &p);
   *         p.setBlendMode(mode);
   *         canvas->drawImage(fCompositeSrc.asImage(), 0, 0, SkSamplingOptions(), &p);
   *     }
   * ```
   */
  protected fun drawTile(
    canvas: SkCanvas?,
    xOffset: Int,
    yOffset: Int,
    mode: SkBlendMode,
  ) {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         ToolUtils::draw_checkerboard(canvas, kWhite, kGrey, 32);
   *
   *         int xOffset = 0, yOffset = 0;
   *
   *         // Android doesn't expose all the blend modes
   *         // Note that the Android documentation calls:
   *         //    Skia's kPlus,     add
   *         //    Skia's kModulate, multiply
   *         for (SkBlendMode mode : { SkBlendMode::kPlus /* add */, SkBlendMode::kClear,
   *                                   SkBlendMode::kDarken, SkBlendMode::kDst,
   *                                   SkBlendMode::kDstATop, SkBlendMode::kDstIn,
   *                                   SkBlendMode::kDstOut, SkBlendMode::kDstOver,
   *                                   SkBlendMode::kLighten, SkBlendMode::kModulate /* multiply */,
   *                                   SkBlendMode::kOverlay, SkBlendMode::kScreen,
   *                                   SkBlendMode::kSrc, SkBlendMode::kSrcATop,
   *                                   SkBlendMode::kSrcIn, SkBlendMode::kSrcOut,
   *                                   SkBlendMode::kSrcOver, SkBlendMode::kXor } ) {
   *
   *             int saveCount = canvas->save();
   *             this->drawTile(canvas, xOffset, yOffset, mode);
   *             canvas->restoreToCount(saveCount);
   *
   *             SkTextUtils::DrawString(canvas, SkBlendMode_Name(mode),
   *                                xOffset + kBitmapSize/2.0f,
   *                                yOffset + kBitmapSize,
   *                                font, SkPaint(), SkTextUtils::kCenter_Align);
   *
   *             xOffset += 256;
   *             if (xOffset >= 1024) {
   *                 xOffset = 0;
   *                 yOffset += 256;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kBitmapSize: Int = TODO("Initialize kBitmapSize")

    private val kNumRows: Int = TODO("Initialize kNumRows")

    private val kNumCols: Int = TODO("Initialize kNumCols")

    private val kBlue: SkColor = TODO("Initialize kBlue")

    private val kRed: SkColor = TODO("Initialize kRed")

    private val kWhite: SkColor = TODO("Initialize kWhite")

    private val kGrey: SkColor = TODO("Initialize kGrey")
  }
}
