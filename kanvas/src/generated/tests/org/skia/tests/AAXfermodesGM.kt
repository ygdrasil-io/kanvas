package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AAXfermodesGM : public skiagm::GM {
 * public:
 *     AAXfermodesGM() {}
 *
 * protected:
 *     enum DrawingPass {
 *         kCheckerboard_Pass,
 *         kBackground_Pass,
 *         kShape_Pass
 *     };
 *
 *     SkString getName() const override { return SkString("aaxfermodes"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(2 * kMargin + 2 * kXfermodeTypeSpacing -
 *                              (kXfermodeTypeSpacing - (kLabelSpacing + 2 * kPaintSpacing)),
 *                              2 * kMargin + kTitleSpacing + kSubtitleSpacing +
 *                              (1 + (int)SkBlendMode::kLastCoeffMode) * kShapeSpacing);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fLabelFont.setTypeface(ToolUtils::DefaultPortableTypeface());
 *         fLabelFont.setSize(5 * kShapeSize/8);
 *         fLabelFont.setSubpixel(true);
 *
 *         constexpr SkScalar radius = -1.4f * kShapeSize/2;
 *         SkPoint pts[4] = {
 *             {-radius, 0},
 *             {0, -1.33f * radius},
 *             {radius, 0},
 *             {0, 1.33f * radius}
 *         };
 *         fOval = SkPathBuilder().moveTo(pts[0])
 *                                .quadTo(pts[1], pts[2])
 *                                .quadTo(pts[3], pts[0])
 *                                .detach();
 *
 *         fConcave = SkPathBuilder().moveTo(-radius, 0)
 *                                   .quadTo(0, 0, 0, -radius)
 *                                   .quadTo(0, 0, radius, 0)
 *                                   .quadTo(0, 0, 0, radius)
 *                                   .quadTo(0, 0, -radius, 0)
 *                                   .close()
 *                                   .detach();
 *     }
 *
 *     void draw_pass(SkCanvas* canvas, DrawingPass drawingPass) {
 *         SkRect clipRect =
 *                 { -kShapeSize*11/16, -kShapeSize*11/16, kShapeSize*11/16, kShapeSize*11/16 };
 *
 *         canvas->save();
 *         if (kCheckerboard_Pass == drawingPass) {
 *             canvas->translate(kMargin, kMargin);
 *         }
 *         canvas->translate(0, kTitleSpacing);
 *
 *         for (size_t xfermodeSet = 0; xfermodeSet < 2; xfermodeSet++) {
 *             size_t firstMode = ((size_t)SkBlendMode::kLastCoeffMode + 1) * xfermodeSet;
 *             canvas->save();
 *
 *             if (kShape_Pass == drawingPass) {
 *                 SkTextUtils::DrawString(canvas, "Src Unknown",
 *                         kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2,
 *                         kSubtitleSpacing / 2 + fLabelFont.getSize() / 3, fLabelFont, SkPaint(),
 *                                         SkTextUtils::kCenter_Align);
 *                 SkTextUtils::DrawString(canvas, "Src Opaque",
 *                         kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2 +
 *                         kPaintSpacing, kSubtitleSpacing / 2 + fLabelFont.getSize() / 3,
 *                                         fLabelFont, SkPaint(), SkTextUtils::kCenter_Align);
 *             }
 *
 *             canvas->translate(0, kSubtitleSpacing + kShapeSpacing/2);
 *
 *             for (size_t m = 0; m <= (size_t)SkBlendMode::kLastCoeffMode; m++) {
 *                 if (firstMode + m > (size_t)SkBlendMode::kLastMode) {
 *                     break;
 *                 }
 *                 SkBlendMode mode = static_cast<SkBlendMode>(firstMode + m);
 *                 canvas->save();
 *
 *                 if (kShape_Pass == drawingPass) {
 *                     this->drawModeName(canvas, mode);
 *                 }
 *                 canvas->translate(kLabelSpacing + kShapeSpacing/2, 0);
 *
 *                 for (size_t colorIdx = 0; colorIdx < std::size(kShapeColors); colorIdx++) {
 *                     SkPaint paint;
 *                     this->setupShapePaint(canvas, kShapeColors[colorIdx], mode, &paint);
 *                     SkASSERT(colorIdx == 0 || 255 == paint.getAlpha());
 *                     canvas->save();
 *
 *                     for (size_t shapeIdx = 0; shapeIdx <= kLast_Shape; shapeIdx++) {
 *                         if (kShape_Pass != drawingPass) {
 *                             canvas->save();
 *                             canvas->clipRect(clipRect);
 *                             if (kCheckerboard_Pass == drawingPass) {
 *                                 ToolUtils::draw_checkerboard(canvas, 0xffffffff, 0xffc6c3c6, 10);
 *                             } else {
 *                                 SkASSERT(kBackground_Pass == drawingPass);
 *                                 canvas->drawColor(kBGColor, SkBlendMode::kSrc);
 *                             }
 *                             canvas->restore();
 *                         } else {
 *                             this->drawShape(canvas, static_cast<Shape>(shapeIdx), paint, mode);
 *                         }
 *                         canvas->translate(kShapeTypeSpacing, 0);
 *                     }
 *
 *                     canvas->restore();
 *                     canvas->translate(kPaintSpacing, 0);
 *                 }
 *
 *                 canvas->restore();
 *                 canvas->translate(0, kShapeSpacing);
 *             }
 *
 *             canvas->restore();
 *             canvas->translate(kXfermodeTypeSpacing, 0);
 *         }
 *
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         draw_pass(canvas, kCheckerboard_Pass);
 *         canvas->saveLayer(nullptr, nullptr);
 *
 *         canvas->translate(kMargin, kMargin);
 *         draw_pass(canvas, kBackground_Pass);
 *
 *         SkFont titleFont(fLabelFont);
 *         titleFont.setSize(9 * titleFont.getSize() / 8);
 *         titleFont.setEmbolden(true);
 *         SkTextUtils::DrawString(canvas, "Porter Duff",
 *                                 kLabelSpacing + 4 * kShapeTypeSpacing,
 *                                 kTitleSpacing / 2 + titleFont.getSize() / 3, titleFont, SkPaint(),
 *                                 SkTextUtils::kCenter_Align);
 *         SkTextUtils::DrawString(canvas, "Advanced",
 *                                 kXfermodeTypeSpacing + kLabelSpacing + 4 * kShapeTypeSpacing,
 *                                 kTitleSpacing / 2 + titleFont.getSize() / 3, titleFont, SkPaint(),
 *                                 SkTextUtils::kCenter_Align);
 *
 *         draw_pass(canvas, kShape_Pass);
 *         canvas->restore();
 *     }
 *
 *     void drawModeName(SkCanvas* canvas, SkBlendMode mode) {
 *         const char* modeName = SkBlendMode_Name(mode);
 *         SkTextUtils::DrawString(canvas, modeName, kLabelSpacing - kShapeSize / 4,
 *                                 fLabelFont.getSize() / 4, fLabelFont, SkPaint(),
 *                                 SkTextUtils::kRight_Align);
 *     }
 *
 *     void setupShapePaint(SkCanvas* canvas, SkColor color, SkBlendMode mode, SkPaint* paint) {
 *         paint->setColor(color);
 *
 *         if (mode == SkBlendMode::kPlus) {
 *             // Check for overflow, otherwise we might get confusing AA artifacts.
 *             int maxSum = std::max(std::max(SkColorGetA(kBGColor) + SkColorGetA(color),
 *                                        SkColorGetR(kBGColor) + SkColorGetR(color)),
 *                                 std::max(SkColorGetG(kBGColor) + SkColorGetG(color),
 *                                        SkColorGetB(kBGColor) + SkColorGetB(color)));
 *
 *             if (maxSum > 255) {
 *                 SkPaint dimPaint;
 *                 dimPaint.setAntiAlias(false);
 *                 dimPaint.setBlendMode(SkBlendMode::kDstIn);
 *                 if (255 != paint->getAlpha()) {
 *                     // Dim the src and dst colors.
 *                     dimPaint.setARGB(255 * 255 / maxSum, 0, 0, 0);
 *                     paint->setAlpha(255 * paint->getAlpha() / maxSum);
 *                 } else {
 *                     // Just clear the dst, we need to preserve the paint's opacity.
 *                     dimPaint.setARGB(0, 0, 0, 0);
 *                 }
 *                 canvas->drawRect({ -kShapeSpacing/2, -kShapeSpacing/2,
 *                                    kShapeSpacing/2 + 3 * kShapeTypeSpacing, kShapeSpacing/2 },
 *                                  dimPaint);
 *             }
 *         }
 *     }
 *
 *     void drawShape(SkCanvas* canvas, Shape shape, const SkPaint& paint, SkBlendMode mode) {
 *         SkASSERT(mode <= SkBlendMode::kLastMode);
 *         SkPaint shapePaint(paint);
 *         shapePaint.setAntiAlias(kSquare_Shape != shape);
 *         shapePaint.setBlendMode(mode);
 *
 *         switch (shape) {
 *             case kSquare_Shape:
 *                 canvas->drawRect({ -kShapeSize/2, -kShapeSize/2, kShapeSize/2, kShapeSize/2 },
 *                                  shapePaint);
 *                 break;
 *
 *             case kDiamond_Shape:
 *                 canvas->save();
 *                 canvas->rotate(45);
 *                 canvas->drawRect({ -kShapeSize/2, -kShapeSize/2, kShapeSize/2, kShapeSize/2 },
 *                                  shapePaint);
 *                 canvas->restore();
 *                 break;
 *
 *             case kOval_Shape:
 *                 canvas->save();
 *                 canvas->rotate(static_cast<SkScalar>((511 * (int)mode + 257) % 360));
 *                 canvas->drawPath(fOval, shapePaint);
 *                 canvas->restore();
 *                 break;
 *
 *             case kConcave_Shape:
 *                 canvas->drawPath(fConcave, shapePaint);
 *                 break;
 *
 *             default:
 *                 SK_ABORT("Invalid shape.");
 *         }
 *     }
 *
 * private:
 *     SkFont    fLabelFont;
 *     SkPath    fOval;
 *     SkPath    fConcave;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class AAXfermodesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkFont    fLabelFont
   * ```
   */
  private var fLabelFont: SkFont = TODO("Initialize fLabelFont")

  /**
   * C++ original:
   * ```cpp
   * SkPath    fOval
   * ```
   */
  private var fOval: SkPath = TODO("Initialize fOval")

  /**
   * C++ original:
   * ```cpp
   * SkPath    fConcave
   * ```
   */
  private var fConcave: SkPath = TODO("Initialize fConcave")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("aaxfermodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(2 * kMargin + 2 * kXfermodeTypeSpacing -
   *                              (kXfermodeTypeSpacing - (kLabelSpacing + 2 * kPaintSpacing)),
   *                              2 * kMargin + kTitleSpacing + kSubtitleSpacing +
   *                              (1 + (int)SkBlendMode::kLastCoeffMode) * kShapeSpacing);
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
   *         fLabelFont.setTypeface(ToolUtils::DefaultPortableTypeface());
   *         fLabelFont.setSize(5 * kShapeSize/8);
   *         fLabelFont.setSubpixel(true);
   *
   *         constexpr SkScalar radius = -1.4f * kShapeSize/2;
   *         SkPoint pts[4] = {
   *             {-radius, 0},
   *             {0, -1.33f * radius},
   *             {radius, 0},
   *             {0, 1.33f * radius}
   *         };
   *         fOval = SkPathBuilder().moveTo(pts[0])
   *                                .quadTo(pts[1], pts[2])
   *                                .quadTo(pts[3], pts[0])
   *                                .detach();
   *
   *         fConcave = SkPathBuilder().moveTo(-radius, 0)
   *                                   .quadTo(0, 0, 0, -radius)
   *                                   .quadTo(0, 0, radius, 0)
   *                                   .quadTo(0, 0, 0, radius)
   *                                   .quadTo(0, 0, -radius, 0)
   *                                   .close()
   *                                   .detach();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw_pass(SkCanvas* canvas, DrawingPass drawingPass) {
   *         SkRect clipRect =
   *                 { -kShapeSize*11/16, -kShapeSize*11/16, kShapeSize*11/16, kShapeSize*11/16 };
   *
   *         canvas->save();
   *         if (kCheckerboard_Pass == drawingPass) {
   *             canvas->translate(kMargin, kMargin);
   *         }
   *         canvas->translate(0, kTitleSpacing);
   *
   *         for (size_t xfermodeSet = 0; xfermodeSet < 2; xfermodeSet++) {
   *             size_t firstMode = ((size_t)SkBlendMode::kLastCoeffMode + 1) * xfermodeSet;
   *             canvas->save();
   *
   *             if (kShape_Pass == drawingPass) {
   *                 SkTextUtils::DrawString(canvas, "Src Unknown",
   *                         kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2,
   *                         kSubtitleSpacing / 2 + fLabelFont.getSize() / 3, fLabelFont, SkPaint(),
   *                                         SkTextUtils::kCenter_Align);
   *                 SkTextUtils::DrawString(canvas, "Src Opaque",
   *                         kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2 +
   *                         kPaintSpacing, kSubtitleSpacing / 2 + fLabelFont.getSize() / 3,
   *                                         fLabelFont, SkPaint(), SkTextUtils::kCenter_Align);
   *             }
   *
   *             canvas->translate(0, kSubtitleSpacing + kShapeSpacing/2);
   *
   *             for (size_t m = 0; m <= (size_t)SkBlendMode::kLastCoeffMode; m++) {
   *                 if (firstMode + m > (size_t)SkBlendMode::kLastMode) {
   *                     break;
   *                 }
   *                 SkBlendMode mode = static_cast<SkBlendMode>(firstMode + m);
   *                 canvas->save();
   *
   *                 if (kShape_Pass == drawingPass) {
   *                     this->drawModeName(canvas, mode);
   *                 }
   *                 canvas->translate(kLabelSpacing + kShapeSpacing/2, 0);
   *
   *                 for (size_t colorIdx = 0; colorIdx < std::size(kShapeColors); colorIdx++) {
   *                     SkPaint paint;
   *                     this->setupShapePaint(canvas, kShapeColors[colorIdx], mode, &paint);
   *                     SkASSERT(colorIdx == 0 || 255 == paint.getAlpha());
   *                     canvas->save();
   *
   *                     for (size_t shapeIdx = 0; shapeIdx <= kLast_Shape; shapeIdx++) {
   *                         if (kShape_Pass != drawingPass) {
   *                             canvas->save();
   *                             canvas->clipRect(clipRect);
   *                             if (kCheckerboard_Pass == drawingPass) {
   *                                 ToolUtils::draw_checkerboard(canvas, 0xffffffff, 0xffc6c3c6, 10);
   *                             } else {
   *                                 SkASSERT(kBackground_Pass == drawingPass);
   *                                 canvas->drawColor(kBGColor, SkBlendMode::kSrc);
   *                             }
   *                             canvas->restore();
   *                         } else {
   *                             this->drawShape(canvas, static_cast<Shape>(shapeIdx), paint, mode);
   *                         }
   *                         canvas->translate(kShapeTypeSpacing, 0);
   *                     }
   *
   *                     canvas->restore();
   *                     canvas->translate(kPaintSpacing, 0);
   *                 }
   *
   *                 canvas->restore();
   *                 canvas->translate(0, kShapeSpacing);
   *             }
   *
   *             canvas->restore();
   *             canvas->translate(kXfermodeTypeSpacing, 0);
   *         }
   *
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun drawPass(canvas: SkCanvas?, drawingPass: DrawingPass) {
    TODO("Implement drawPass")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         draw_pass(canvas, kCheckerboard_Pass);
   *         canvas->saveLayer(nullptr, nullptr);
   *
   *         canvas->translate(kMargin, kMargin);
   *         draw_pass(canvas, kBackground_Pass);
   *
   *         SkFont titleFont(fLabelFont);
   *         titleFont.setSize(9 * titleFont.getSize() / 8);
   *         titleFont.setEmbolden(true);
   *         SkTextUtils::DrawString(canvas, "Porter Duff",
   *                                 kLabelSpacing + 4 * kShapeTypeSpacing,
   *                                 kTitleSpacing / 2 + titleFont.getSize() / 3, titleFont, SkPaint(),
   *                                 SkTextUtils::kCenter_Align);
   *         SkTextUtils::DrawString(canvas, "Advanced",
   *                                 kXfermodeTypeSpacing + kLabelSpacing + 4 * kShapeTypeSpacing,
   *                                 kTitleSpacing / 2 + titleFont.getSize() / 3, titleFont, SkPaint(),
   *                                 SkTextUtils::kCenter_Align);
   *
   *         draw_pass(canvas, kShape_Pass);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawModeName(SkCanvas* canvas, SkBlendMode mode) {
   *         const char* modeName = SkBlendMode_Name(mode);
   *         SkTextUtils::DrawString(canvas, modeName, kLabelSpacing - kShapeSize / 4,
   *                                 fLabelFont.getSize() / 4, fLabelFont, SkPaint(),
   *                                 SkTextUtils::kRight_Align);
   *     }
   * ```
   */
  protected fun drawModeName(canvas: SkCanvas?, mode: SkBlendMode) {
    TODO("Implement drawModeName")
  }

  /**
   * C++ original:
   * ```cpp
   * void setupShapePaint(SkCanvas* canvas, SkColor color, SkBlendMode mode, SkPaint* paint) {
   *         paint->setColor(color);
   *
   *         if (mode == SkBlendMode::kPlus) {
   *             // Check for overflow, otherwise we might get confusing AA artifacts.
   *             int maxSum = std::max(std::max(SkColorGetA(kBGColor) + SkColorGetA(color),
   *                                        SkColorGetR(kBGColor) + SkColorGetR(color)),
   *                                 std::max(SkColorGetG(kBGColor) + SkColorGetG(color),
   *                                        SkColorGetB(kBGColor) + SkColorGetB(color)));
   *
   *             if (maxSum > 255) {
   *                 SkPaint dimPaint;
   *                 dimPaint.setAntiAlias(false);
   *                 dimPaint.setBlendMode(SkBlendMode::kDstIn);
   *                 if (255 != paint->getAlpha()) {
   *                     // Dim the src and dst colors.
   *                     dimPaint.setARGB(255 * 255 / maxSum, 0, 0, 0);
   *                     paint->setAlpha(255 * paint->getAlpha() / maxSum);
   *                 } else {
   *                     // Just clear the dst, we need to preserve the paint's opacity.
   *                     dimPaint.setARGB(0, 0, 0, 0);
   *                 }
   *                 canvas->drawRect({ -kShapeSpacing/2, -kShapeSpacing/2,
   *                                    kShapeSpacing/2 + 3 * kShapeTypeSpacing, kShapeSpacing/2 },
   *                                  dimPaint);
   *             }
   *         }
   *     }
   * ```
   */
  protected fun setupShapePaint(
    canvas: SkCanvas?,
    color: SkColor,
    mode: SkBlendMode,
    paint: SkPaint?,
  ) {
    TODO("Implement setupShapePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawShape(SkCanvas* canvas, Shape shape, const SkPaint& paint, SkBlendMode mode) {
   *         SkASSERT(mode <= SkBlendMode::kLastMode);
   *         SkPaint shapePaint(paint);
   *         shapePaint.setAntiAlias(kSquare_Shape != shape);
   *         shapePaint.setBlendMode(mode);
   *
   *         switch (shape) {
   *             case kSquare_Shape:
   *                 canvas->drawRect({ -kShapeSize/2, -kShapeSize/2, kShapeSize/2, kShapeSize/2 },
   *                                  shapePaint);
   *                 break;
   *
   *             case kDiamond_Shape:
   *                 canvas->save();
   *                 canvas->rotate(45);
   *                 canvas->drawRect({ -kShapeSize/2, -kShapeSize/2, kShapeSize/2, kShapeSize/2 },
   *                                  shapePaint);
   *                 canvas->restore();
   *                 break;
   *
   *             case kOval_Shape:
   *                 canvas->save();
   *                 canvas->rotate(static_cast<SkScalar>((511 * (int)mode + 257) % 360));
   *                 canvas->drawPath(fOval, shapePaint);
   *                 canvas->restore();
   *                 break;
   *
   *             case kConcave_Shape:
   *                 canvas->drawPath(fConcave, shapePaint);
   *                 break;
   *
   *             default:
   *                 SK_ABORT("Invalid shape.");
   *         }
   *     }
   * ```
   */
  protected fun drawShape(
    canvas: SkCanvas?,
    shape: Shape,
    paint: SkPaint,
    mode: SkBlendMode,
  ) {
    TODO("Implement drawShape")
  }

  public enum class DrawingPass {
    kCheckerboard_Pass,
    kBackground_Pass,
    kShape_Pass,
  }
}
