package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class StrokedLinesGM : public GM {
 * public:
 *     StrokedLinesGM(bool useDrawPath) : fUseDrawPath(useDrawPath) {
 *         this->setBGColor(ToolUtils::color_to_565(0xFF1A65D7));
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         // To preserve history, useDrawPath==true has no suffix.
 *         SkString name{"strokedlines"};
 *         if (!fUseDrawPath) {
 *             name.append("_drawPoints");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kNumColumns * (2*kRadius+2*kPad), kNumRows * (2*kRadius+2*kPad));
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         // paints
 *         {
 *             // basic white
 *             SkPaint p;
 *             p.setColor(SK_ColorWHITE);
 *             fPaints.push_back(p);
 *         }
 *         {
 *             // gradient
 *             SkColor4f colors[] = { SkColors::kRed, SkColors::kGreen };
 *             SkPoint pts[] = { {-kRadius-kPad, -kRadius-kPad }, { kRadius+kPad, kRadius+kPad } };
 *
 *             SkPaint p;
 *             p.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}}));
 *
 *             fPaints.push_back(p);
 *         }
 *         {
 *             // dashing
 *             SkScalar intervals[] = { kStrokeWidth, kStrokeWidth };
 *             SkPaint p;
 *             p.setColor(SK_ColorWHITE);
 *             p.setPathEffect(SkDashPathEffect::Make(intervals, kStrokeWidth));
 *
 *             fPaints.push_back(p);
 *         }
 *         {
 *             // Bitmap shader
 *             SkBitmap bm;
 *             bm.allocN32Pixels(2, 2);
 *             *bm.getAddr32(0, 0) = *bm.getAddr32(1, 1) = 0xFFFFFFFF;
 *             *bm.getAddr32(1, 0) = *bm.getAddr32(0, 1) = 0x0;
 *
 *             SkMatrix m;
 *             m.setRotate(12.0f);
 *             m.preScale(3.0f, 3.0f);
 *
 *             SkPaint p;
 *             p.setShader(bm.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                       SkSamplingOptions(), m));
 *             fPaints.push_back(p);
 *         }
 *         {
 *             // blur
 *             SkPaint p;
 *             p.setColor(SK_ColorWHITE);
 *             p.setMaskFilter(SkMaskFilter::MakeBlur(kOuter_SkBlurStyle, 3.0f));
 *             fPaints.push_back(p);
 *         }
 *
 *         // matrices
 *         {
 *             // rotation
 *             SkMatrix m;
 *             m.setRotate(12.0f);
 *
 *             fMatrices.push_back(m);
 *         }
 *         {
 *             // skew
 *             SkMatrix m;
 *             m.setSkew(0.3f, 0.5f);
 *
 *             fMatrices.push_back(m);
 *         }
 *         {
 *             // perspective
 *             SkMatrix m;
 *             m.reset();
 *             m.setPerspX(-SK_Scalar1 / 300);
 *             m.setPerspY(SK_Scalar1 / 300);
 *
 *             fMatrices.push_back(m);
 *         }
 *
 *         SkASSERT(kNumRows == fPaints.size() + fMatrices.size());
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(0, kRadius+kPad);
 *
 *         for (int i = 0; i < fPaints.size(); ++i) {
 *             int saveCount = canvas->save();
 *             draw_row(canvas, fPaints[i], SkMatrix::I(), fUseDrawPath);
 *             canvas->restoreToCount(saveCount);
 *
 *             canvas->translate(0, 2*(kRadius+kPad));
 *         }
 *
 *         for (int i = 0; i < fMatrices.size(); ++i) {
 *             int saveCount = canvas->save();
 *             draw_row(canvas, fPaints[0], fMatrices[i], fUseDrawPath);
 *             canvas->restoreToCount(saveCount);
 *
 *             canvas->translate(0, 2*(kRadius+kPad));
 *         }
 *     }
 *
 * private:
 *     TArray<SkPaint> fPaints;
 *     TArray<SkMatrix> fMatrices;
 *
 *     const bool fUseDrawPath;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class StrokedLinesGM public constructor(
  useDrawPath: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<SkPaint> fPaints
   * ```
   */
  private var fPaints: Int = TODO("Initialize fPaints")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkMatrix> fMatrices
   * ```
   */
  private var fMatrices: Int = TODO("Initialize fMatrices")

  /**
   * C++ original:
   * ```cpp
   * const bool fUseDrawPath
   * ```
   */
  private val fUseDrawPath: Boolean = TODO("Initialize fUseDrawPath")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         // To preserve history, useDrawPath==true has no suffix.
   *         SkString name{"strokedlines"};
   *         if (!fUseDrawPath) {
   *             name.append("_drawPoints");
   *         }
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
   * SkISize getISize() override {
   *         return SkISize::Make(kNumColumns * (2*kRadius+2*kPad), kNumRows * (2*kRadius+2*kPad));
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
   *         // paints
   *         {
   *             // basic white
   *             SkPaint p;
   *             p.setColor(SK_ColorWHITE);
   *             fPaints.push_back(p);
   *         }
   *         {
   *             // gradient
   *             SkColor4f colors[] = { SkColors::kRed, SkColors::kGreen };
   *             SkPoint pts[] = { {-kRadius-kPad, -kRadius-kPad }, { kRadius+kPad, kRadius+kPad } };
   *
   *             SkPaint p;
   *             p.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}}));
   *
   *             fPaints.push_back(p);
   *         }
   *         {
   *             // dashing
   *             SkScalar intervals[] = { kStrokeWidth, kStrokeWidth };
   *             SkPaint p;
   *             p.setColor(SK_ColorWHITE);
   *             p.setPathEffect(SkDashPathEffect::Make(intervals, kStrokeWidth));
   *
   *             fPaints.push_back(p);
   *         }
   *         {
   *             // Bitmap shader
   *             SkBitmap bm;
   *             bm.allocN32Pixels(2, 2);
   *             *bm.getAddr32(0, 0) = *bm.getAddr32(1, 1) = 0xFFFFFFFF;
   *             *bm.getAddr32(1, 0) = *bm.getAddr32(0, 1) = 0x0;
   *
   *             SkMatrix m;
   *             m.setRotate(12.0f);
   *             m.preScale(3.0f, 3.0f);
   *
   *             SkPaint p;
   *             p.setShader(bm.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                       SkSamplingOptions(), m));
   *             fPaints.push_back(p);
   *         }
   *         {
   *             // blur
   *             SkPaint p;
   *             p.setColor(SK_ColorWHITE);
   *             p.setMaskFilter(SkMaskFilter::MakeBlur(kOuter_SkBlurStyle, 3.0f));
   *             fPaints.push_back(p);
   *         }
   *
   *         // matrices
   *         {
   *             // rotation
   *             SkMatrix m;
   *             m.setRotate(12.0f);
   *
   *             fMatrices.push_back(m);
   *         }
   *         {
   *             // skew
   *             SkMatrix m;
   *             m.setSkew(0.3f, 0.5f);
   *
   *             fMatrices.push_back(m);
   *         }
   *         {
   *             // perspective
   *             SkMatrix m;
   *             m.reset();
   *             m.setPerspX(-SK_Scalar1 / 300);
   *             m.setPerspY(SK_Scalar1 / 300);
   *
   *             fMatrices.push_back(m);
   *         }
   *
   *         SkASSERT(kNumRows == fPaints.size() + fMatrices.size());
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
   *         canvas->translate(0, kRadius+kPad);
   *
   *         for (int i = 0; i < fPaints.size(); ++i) {
   *             int saveCount = canvas->save();
   *             draw_row(canvas, fPaints[i], SkMatrix::I(), fUseDrawPath);
   *             canvas->restoreToCount(saveCount);
   *
   *             canvas->translate(0, 2*(kRadius+kPad));
   *         }
   *
   *         for (int i = 0; i < fMatrices.size(); ++i) {
   *             int saveCount = canvas->save();
   *             draw_row(canvas, fPaints[0], fMatrices[i], fUseDrawPath);
   *             canvas->restoreToCount(saveCount);
   *
   *             canvas->translate(0, 2*(kRadius+kPad));
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
