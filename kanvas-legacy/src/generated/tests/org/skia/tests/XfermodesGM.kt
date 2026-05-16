package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class XfermodesGM : public skiagm::GM {
 *     SkBitmap    fBG;
 *     SkBitmap    fSrcB, fDstB, fTransparent;
 *
 *     /* The srcType argument indicates what to draw for the source part. Skia
 *      * uses the implied shape of the drawing command and these modes
 *      * demonstrate that.
 *      */
 *     void draw_mode(SkCanvas* canvas, SkBlendMode mode, SrcType srcType, SkScalar x, SkScalar y) {
 *         SkPaint p;
 *         SkSamplingOptions sampling;
 *         SkMatrix m;
 *         bool restoreNeeded = false;
 *         m.setTranslate(x, y);
 *
 *         canvas->drawImage(fSrcB.asImage(), x, y, sampling, &p);
 *         p.setBlendMode(mode);
 *         switch (srcType) {
 *             case kSmallTransparentImage_SrcType: {
 *                 m.postScale(SK_ScalarHalf, SK_ScalarHalf, x, y);
 *
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->concat(m);
 *                 canvas->drawImage(fTransparent.asImage(), 0, 0, sampling, &p);
 *                 break;
 *             }
 *             case kQuarterClearInLayer_SrcType: {
 *                 SkRect bounds = SkRect::MakeXYWH(x, y, SkIntToScalar(W),
 *                                                  SkIntToScalar(H));
 *                 canvas->saveLayer(&bounds, &p);
 *                 restoreNeeded = true;
 *                 p.setBlendMode(SkBlendMode::kSrcOver);
 *                 [[fallthrough]];
 *             }
 *             case kQuarterClear_SrcType: {
 *                 SkScalar halfW = SkIntToScalar(W) / 2;
 *                 SkScalar halfH = SkIntToScalar(H) / 2;
 *                 p.setColor(ToolUtils::color_to_565(0xFF66AAFF));
 *                 SkRect r = SkRect::MakeXYWH(x + halfW, y, halfW,
 *                                             SkIntToScalar(H));
 *                 canvas->drawRect(r, p);
 *                 p.setColor(ToolUtils::color_to_565(0xFFAA66FF));
 *                 r = SkRect::MakeXYWH(x, y + halfH, SkIntToScalar(W), halfH);
 *                 canvas->drawRect(r, p);
 *                 break;
 *             }
 *             case kRectangleWithMask_SrcType: {
 *                 canvas->save();
 *                 restoreNeeded = true;
 *                 SkScalar w = SkIntToScalar(W);
 *                 SkScalar h = SkIntToScalar(H);
 *                 SkRect r = SkRect::MakeXYWH(x, y + h / 4, w, h * 23 / 60);
 *                 canvas->clipRect(r);
 *                 [[fallthrough]];
 *             }
 *             case kRectangle_SrcType: {
 *                 SkScalar w = SkIntToScalar(W);
 *                 SkScalar h = SkIntToScalar(H);
 *                 SkRect r = SkRect::MakeXYWH(x + w / 3, y + h / 3,
 *                                             w * 37 / 60, h * 37 / 60);
 *                 p.setColor(ToolUtils::color_to_565(0xFF66AAFF));
 *                 canvas->drawRect(r, p);
 *                 break;
 *             }
 *             case kSmallRectangleImageWithAlpha_SrcType:
 *                 m.postScale(SK_ScalarHalf, SK_ScalarHalf, x, y);
 *                 [[fallthrough]];
 *             case kRectangleImageWithAlpha_SrcType:
 *                 p.setAlpha(0x88);
 *                 [[fallthrough]];
 *             case kRectangleImage_SrcType: {
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->concat(m);
 *                 canvas->drawImage(fDstB.asImage(), 0, 0, sampling, &p);
 *                 break;
 *             }
 *             default:
 *                 break;
 *         }
 *
 *         if (restoreNeeded) {
 *             canvas->restore();
 *         }
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fBG.installPixels(SkImageInfo::Make(2, 2, kARGB_4444_SkColorType,
 *                                             kOpaque_SkAlphaType),
 *                           gData, 4);
 *
 *         make_bitmaps(W, H, &fSrcB, &fDstB, &fTransparent);
 *     }
 *
 * public:
 *     const static int W = 64;
 *     const static int H = 64;
 *     XfermodesGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("xfermodes"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1990, 570); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
 *
 *         const SkScalar w = SkIntToScalar(W);
 *         const SkScalar h = SkIntToScalar(H);
 *         SkMatrix m;
 *         m.setScale(SkIntToScalar(6), SkIntToScalar(6));
 *         auto s = fBG.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                 SkSamplingOptions(), m);
 *
 *         SkPaint labelP;
 *         labelP.setAntiAlias(true);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         const int kWrap = 5;
 *
 *         SkScalar x0 = 0;
 *         SkScalar y0 = 0;
 *         for (int sourceType = 1; sourceType & kAll_SrcType; sourceType <<= 1) {
 *             SkScalar x = x0, y = y0;
 *             for (size_t i = 0; i < std::size(gModes); i++) {
 *                 if ((gModes[i].fSourceTypeMask & sourceType) == 0) {
 *                     continue;
 *                 }
 *                 SkRect r{ x, y, x+w, y+h };
 *
 *                 SkPaint p;
 *                 p.setStyle(SkPaint::kFill_Style);
 *                 p.setShader(s);
 *                 canvas->drawRect(r, p);
 *
 *                 canvas->saveLayer(&r, nullptr);
 *                 draw_mode(canvas, gModes[i].fMode, static_cast<SrcType>(sourceType),
 *                           r.fLeft, r.fTop);
 *                 canvas->restore();
 *
 *                 r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
 *                 p.setStyle(SkPaint::kStroke_Style);
 *                 p.setShader(nullptr);
 *                 canvas->drawRect(r, p);
 *
 * #if 1
 *                 const char* label = SkBlendMode_Name(gModes[i].fMode);
 *                 SkTextUtils::DrawString(canvas, label, x + w/2, y - font.getSize()/2,
 *                                         font, labelP, SkTextUtils::kCenter_Align);
 * #endif
 *                 x += w + SkIntToScalar(10);
 *                 if ((i % kWrap) == kWrap - 1) {
 *                     x = x0;
 *                     y += h + SkIntToScalar(30);
 *                 }
 *             }
 *             if (y < 320) {
 *                 if (x > x0) {
 *                     y += h + SkIntToScalar(30);
 *                 }
 *                 y0 = y;
 *             } else {
 *                 x0 += SkIntToScalar(400);
 *                 y0 = 0;
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class XfermodesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fBG
   * ```
   */
  private var fBG: SkBitmap = TODO("Initialize fBG")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fSrcB
   * ```
   */
  private var fSrcB: SkBitmap = TODO("Initialize fSrcB")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fSrcB, fDstB
   * ```
   */
  private var fDstB: SkBitmap = TODO("Initialize fDstB")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fSrcB, fDstB, fTransparent
   * ```
   */
  private var fTransparent: SkBitmap = TODO("Initialize fTransparent")

  /**
   * C++ original:
   * ```cpp
   * void draw_mode(SkCanvas* canvas, SkBlendMode mode, SrcType srcType, SkScalar x, SkScalar y) {
   *         SkPaint p;
   *         SkSamplingOptions sampling;
   *         SkMatrix m;
   *         bool restoreNeeded = false;
   *         m.setTranslate(x, y);
   *
   *         canvas->drawImage(fSrcB.asImage(), x, y, sampling, &p);
   *         p.setBlendMode(mode);
   *         switch (srcType) {
   *             case kSmallTransparentImage_SrcType: {
   *                 m.postScale(SK_ScalarHalf, SK_ScalarHalf, x, y);
   *
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->concat(m);
   *                 canvas->drawImage(fTransparent.asImage(), 0, 0, sampling, &p);
   *                 break;
   *             }
   *             case kQuarterClearInLayer_SrcType: {
   *                 SkRect bounds = SkRect::MakeXYWH(x, y, SkIntToScalar(W),
   *                                                  SkIntToScalar(H));
   *                 canvas->saveLayer(&bounds, &p);
   *                 restoreNeeded = true;
   *                 p.setBlendMode(SkBlendMode::kSrcOver);
   *                 [[fallthrough]];
   *             }
   *             case kQuarterClear_SrcType: {
   *                 SkScalar halfW = SkIntToScalar(W) / 2;
   *                 SkScalar halfH = SkIntToScalar(H) / 2;
   *                 p.setColor(ToolUtils::color_to_565(0xFF66AAFF));
   *                 SkRect r = SkRect::MakeXYWH(x + halfW, y, halfW,
   *                                             SkIntToScalar(H));
   *                 canvas->drawRect(r, p);
   *                 p.setColor(ToolUtils::color_to_565(0xFFAA66FF));
   *                 r = SkRect::MakeXYWH(x, y + halfH, SkIntToScalar(W), halfH);
   *                 canvas->drawRect(r, p);
   *                 break;
   *             }
   *             case kRectangleWithMask_SrcType: {
   *                 canvas->save();
   *                 restoreNeeded = true;
   *                 SkScalar w = SkIntToScalar(W);
   *                 SkScalar h = SkIntToScalar(H);
   *                 SkRect r = SkRect::MakeXYWH(x, y + h / 4, w, h * 23 / 60);
   *                 canvas->clipRect(r);
   *                 [[fallthrough]];
   *             }
   *             case kRectangle_SrcType: {
   *                 SkScalar w = SkIntToScalar(W);
   *                 SkScalar h = SkIntToScalar(H);
   *                 SkRect r = SkRect::MakeXYWH(x + w / 3, y + h / 3,
   *                                             w * 37 / 60, h * 37 / 60);
   *                 p.setColor(ToolUtils::color_to_565(0xFF66AAFF));
   *                 canvas->drawRect(r, p);
   *                 break;
   *             }
   *             case kSmallRectangleImageWithAlpha_SrcType:
   *                 m.postScale(SK_ScalarHalf, SK_ScalarHalf, x, y);
   *                 [[fallthrough]];
   *             case kRectangleImageWithAlpha_SrcType:
   *                 p.setAlpha(0x88);
   *                 [[fallthrough]];
   *             case kRectangleImage_SrcType: {
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->concat(m);
   *                 canvas->drawImage(fDstB.asImage(), 0, 0, sampling, &p);
   *                 break;
   *             }
   *             default:
   *                 break;
   *         }
   *
   *         if (restoreNeeded) {
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  private fun drawMode(
    canvas: SkCanvas?,
    mode: SkBlendMode,
    srcType: SrcType,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement drawMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fBG.installPixels(SkImageInfo::Make(2, 2, kARGB_4444_SkColorType,
   *                                             kOpaque_SkAlphaType),
   *                           gData, 4);
   *
   *         make_bitmaps(W, H, &fSrcB, &fDstB, &fTransparent);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("xfermodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1990, 570); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
   *
   *         const SkScalar w = SkIntToScalar(W);
   *         const SkScalar h = SkIntToScalar(H);
   *         SkMatrix m;
   *         m.setScale(SkIntToScalar(6), SkIntToScalar(6));
   *         auto s = fBG.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                 SkSamplingOptions(), m);
   *
   *         SkPaint labelP;
   *         labelP.setAntiAlias(true);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         const int kWrap = 5;
   *
   *         SkScalar x0 = 0;
   *         SkScalar y0 = 0;
   *         for (int sourceType = 1; sourceType & kAll_SrcType; sourceType <<= 1) {
   *             SkScalar x = x0, y = y0;
   *             for (size_t i = 0; i < std::size(gModes); i++) {
   *                 if ((gModes[i].fSourceTypeMask & sourceType) == 0) {
   *                     continue;
   *                 }
   *                 SkRect r{ x, y, x+w, y+h };
   *
   *                 SkPaint p;
   *                 p.setStyle(SkPaint::kFill_Style);
   *                 p.setShader(s);
   *                 canvas->drawRect(r, p);
   *
   *                 canvas->saveLayer(&r, nullptr);
   *                 draw_mode(canvas, gModes[i].fMode, static_cast<SrcType>(sourceType),
   *                           r.fLeft, r.fTop);
   *                 canvas->restore();
   *
   *                 r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
   *                 p.setStyle(SkPaint::kStroke_Style);
   *                 p.setShader(nullptr);
   *                 canvas->drawRect(r, p);
   *
   * #if 1
   *                 const char* label = SkBlendMode_Name(gModes[i].fMode);
   *                 SkTextUtils::DrawString(canvas, label, x + w/2, y - font.getSize()/2,
   *                                         font, labelP, SkTextUtils::kCenter_Align);
   * #endif
   *                 x += w + SkIntToScalar(10);
   *                 if ((i % kWrap) == kWrap - 1) {
   *                     x = x0;
   *                     y += h + SkIntToScalar(30);
   *                 }
   *             }
   *             if (y < 320) {
   *                 if (x > x0) {
   *                     y += h + SkIntToScalar(30);
   *                 }
   *                 y0 = y;
   *             } else {
   *                 x0 += SkIntToScalar(400);
   *                 y0 = 0;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    public val w: Int = TODO("Initialize w")

    public val h: Int = TODO("Initialize h")
  }
}
