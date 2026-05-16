package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BigBlursGM : public GM {
 * public:
 *     BigBlursGM() {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("bigblurs"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr int kBig = 65536;
 *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(4));
 *
 *         const SkRect bigRect = SkRect::MakeWH(SkIntToScalar(kBig), SkIntToScalar(kBig));
 *         SkRect insetRect = bigRect;
 *         insetRect.inset(20, 20);
 *
 *         SkPath rectori = SkPathBuilder().addRect(bigRect)
 *                                         .addRect(insetRect, SkPathDirection::kCCW)
 *                                         .detach();
 *
 *         // The blur extends 3*kSigma out from the big rect.
 *         // Offset the close-up windows so we get the entire blur
 *         const SkScalar kLeftTopPad  = 3*kSigma;   // use on left & up of big rect
 *         const SkScalar kRightBotPad = kCloseUpSize-3*kSigma; // use on right and bot sides
 *
 *         // UL hand corners of the rendered closeups
 *         const SkPoint origins[] = {
 *             { -kLeftTopPad,          -kLeftTopPad           }, // UL
 *             {  kBig-kRightBotPad,    -kLeftTopPad           }, // UR
 *             {  kBig-kRightBotPad,     kBig-kRightBotPad     }, // LR
 *             { -kLeftTopPad,           kBig-kRightBotPad     }, // LL
 *             {  kBig/2-kCloseUpSize/2, kBig/2-kCloseUpSize/2 }, // center
 *         };
 *
 *         SkPaint outlinePaint;
 *         outlinePaint.setColor(SK_ColorRED);
 *         outlinePaint.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPaint blurPaint;
 *         blurPaint.setAntiAlias(true);
 *         blurPaint.setColor(SK_ColorBLACK);
 *
 *         int desiredX = 0, desiredY = 0;
 *
 *         for (int i = 0; i < 2; ++i) {
 *             for (int j = 0; j <= kLastEnum_SkBlurStyle; ++j) {
 *                 blurPaint.setMaskFilter(SkMaskFilter::MakeBlur((SkBlurStyle)j, kSigma));
 *
 *                 for (int k = 0; k < (int)std::size(origins); ++k) {
 *                     canvas->save();
 *
 *                     SkRect clipRect = SkRect::MakeXYWH(SkIntToScalar(desiredX),
 *                                                        SkIntToScalar(desiredY),
 *                                                        SkIntToScalar(kCloseUpSize),
 *                                                        SkIntToScalar(kCloseUpSize));
 *
 *                     canvas->clipRect(clipRect);
 *
 *                     canvas->translate(desiredX-origins[k].fX,
 *                                       desiredY-origins[k].fY);
 *
 *                     if (0 == i) {
 *                         canvas->drawRect(bigRect, blurPaint);
 *                     } else {
 *                         canvas->drawPath(rectori, blurPaint);
 *                     }
 *                     canvas->restore();
 *                     canvas->drawRect(clipRect, outlinePaint);
 *
 *                     desiredX += kCloseUpSize;
 *                 }
 *
 *                 desiredX = 0;
 *                 desiredY += kCloseUpSize;
 *             }
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kCloseUpSize = 64;
 *     inline static constexpr int kWidth = 5 * kCloseUpSize;
 *     inline static constexpr int kHeight = 2 * (kLastEnum_SkBlurStyle + 1) * kCloseUpSize;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BigBlursGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bigblurs"); }
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
   *         constexpr int kBig = 65536;
   *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(4));
   *
   *         const SkRect bigRect = SkRect::MakeWH(SkIntToScalar(kBig), SkIntToScalar(kBig));
   *         SkRect insetRect = bigRect;
   *         insetRect.inset(20, 20);
   *
   *         SkPath rectori = SkPathBuilder().addRect(bigRect)
   *                                         .addRect(insetRect, SkPathDirection::kCCW)
   *                                         .detach();
   *
   *         // The blur extends 3*kSigma out from the big rect.
   *         // Offset the close-up windows so we get the entire blur
   *         const SkScalar kLeftTopPad  = 3*kSigma;   // use on left & up of big rect
   *         const SkScalar kRightBotPad = kCloseUpSize-3*kSigma; // use on right and bot sides
   *
   *         // UL hand corners of the rendered closeups
   *         const SkPoint origins[] = {
   *             { -kLeftTopPad,          -kLeftTopPad           }, // UL
   *             {  kBig-kRightBotPad,    -kLeftTopPad           }, // UR
   *             {  kBig-kRightBotPad,     kBig-kRightBotPad     }, // LR
   *             { -kLeftTopPad,           kBig-kRightBotPad     }, // LL
   *             {  kBig/2-kCloseUpSize/2, kBig/2-kCloseUpSize/2 }, // center
   *         };
   *
   *         SkPaint outlinePaint;
   *         outlinePaint.setColor(SK_ColorRED);
   *         outlinePaint.setStyle(SkPaint::kStroke_Style);
   *
   *         SkPaint blurPaint;
   *         blurPaint.setAntiAlias(true);
   *         blurPaint.setColor(SK_ColorBLACK);
   *
   *         int desiredX = 0, desiredY = 0;
   *
   *         for (int i = 0; i < 2; ++i) {
   *             for (int j = 0; j <= kLastEnum_SkBlurStyle; ++j) {
   *                 blurPaint.setMaskFilter(SkMaskFilter::MakeBlur((SkBlurStyle)j, kSigma));
   *
   *                 for (int k = 0; k < (int)std::size(origins); ++k) {
   *                     canvas->save();
   *
   *                     SkRect clipRect = SkRect::MakeXYWH(SkIntToScalar(desiredX),
   *                                                        SkIntToScalar(desiredY),
   *                                                        SkIntToScalar(kCloseUpSize),
   *                                                        SkIntToScalar(kCloseUpSize));
   *
   *                     canvas->clipRect(clipRect);
   *
   *                     canvas->translate(desiredX-origins[k].fX,
   *                                       desiredY-origins[k].fY);
   *
   *                     if (0 == i) {
   *                         canvas->drawRect(bigRect, blurPaint);
   *                     } else {
   *                         canvas->drawPath(rectori, blurPaint);
   *                     }
   *                     canvas->restore();
   *                     canvas->drawRect(clipRect, outlinePaint);
   *
   *                     desiredX += kCloseUpSize;
   *                 }
   *
   *                 desiredX = 0;
   *                 desiredY += kCloseUpSize;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kCloseUpSize: Int = TODO("Initialize kCloseUpSize")

    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")
  }
}
