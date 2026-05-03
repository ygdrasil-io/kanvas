package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class PerspShadersGM : public GM {
 * public:
 *     PerspShadersGM(bool doAA) : fDoAA(doAA) { }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name;
 *         name.printf("persp_shaders_%s",
 *                      fDoAA ? "aa" : "bw");
 *         return name;
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kCellSize*kNumCols, kCellSize*kNumRows);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fBitmapImage = ToolUtils::create_checkerboard_image(
 *                 kCellSize, kCellSize, SK_ColorBLUE, SK_ColorYELLOW, kCellSize / 10);
 *
 *         SkPoint pts1[] = {
 *             { 0, 0 },
 *             { SkIntToScalar(kCellSize), SkIntToScalar(kCellSize) }
 *         };
 *         SkPoint pts2[] = {
 *             { 0, 0 },
 *             { 0, SkIntToScalar(kCellSize) }
 *         };
 *         constexpr SkColor4f colors[] = {
 *             SkColors::kRed, SkColors::kGreen, SkColors::kRed, SkColors::kGreen, SkColors::kRed
 *         };
 *         constexpr SkScalar pos[] = { 0, 0.25f, 0.5f, 0.75f, SK_Scalar1 };
 *
 *         fLinearGrad1 = SkShaders::LinearGradient(pts1, {{colors, pos, SkTileMode::kClamp}, {}});
 *         fLinearGrad2 = SkShaders::LinearGradient(pts2, {{colors, pos, SkTileMode::kClamp}, {}});
 *
 *         fPerspMatrix.reset();
 *         fPerspMatrix.setPerspY(SK_Scalar1 / 50);
 *
 *         fPath = SkPathBuilder()
 *                 .moveTo(0, 0)
 *                 .lineTo(0, SkIntToScalar(kCellSize))
 *                 .lineTo(kCellSize/2.0f, kCellSize/2.0f)
 *                 .lineTo(SkIntToScalar(kCellSize), SkIntToScalar(kCellSize))
 *                 .lineTo(SkIntToScalar(kCellSize), 0)
 *                 .close()
 *                 .detach();
 *     }
 *
 *     void drawRow(SkCanvas* canvas, const SkSamplingOptions& sampling) {
 *         SkPaint filterPaint;
 *         filterPaint.setAntiAlias(fDoAA);
 *
 *         SkPaint pathPaint;
 *         pathPaint.setShader(fBitmapImage->makeShader(sampling));
 *         pathPaint.setAntiAlias(fDoAA);
 *
 *         SkPaint gradPaint1;
 *         gradPaint1.setShader(fLinearGrad1);
 *         gradPaint1.setAntiAlias(fDoAA);
 *         SkPaint gradPaint2;
 *         gradPaint2.setShader(fLinearGrad2);
 *         gradPaint2.setAntiAlias(fDoAA);
 *
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(kCellSize), SkIntToScalar(kCellSize));
 *
 *         canvas->save();
 *
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawImageRect(fBitmapImage, r, sampling, &filterPaint);
 *         canvas->restore();
 *
 *         canvas->translate(SkIntToScalar(kCellSize), 0);
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawImage(fImage.get(), 0, 0, sampling, &filterPaint);
 *         canvas->restore();
 *
 *         canvas->translate(SkIntToScalar(kCellSize), 0);
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawRect(r, pathPaint);
 *         canvas->restore();
 *
 *         canvas->translate(SkIntToScalar(kCellSize), 0);
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawPath(fPath, pathPaint);
 *         canvas->restore();
 *
 *         canvas->translate(SkIntToScalar(kCellSize), 0);
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawRect(r, gradPaint1);
 *         canvas->restore();
 *
 *         canvas->translate(SkIntToScalar(kCellSize), 0);
 *         canvas->save();
 *         canvas->concat(fPerspMatrix);
 *         canvas->drawPath(fPath, gradPaint2);
 *         canvas->restore();
 *
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
 *             fImage = make_image(canvas, kCellSize, kCellSize);
 *         }
 *
 *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kNearest));
 *         canvas->translate(0, SkIntToScalar(kCellSize));
 *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kLinear));
 *         canvas->translate(0, SkIntToScalar(kCellSize));
 *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kLinear,
 *                                                 SkMipmapMode::kNearest));
 *         canvas->translate(0, SkIntToScalar(kCellSize));
 *         this->drawRow(canvas, SkSamplingOptions(SkCubicResampler::Mitchell()));
 *         canvas->translate(0, SkIntToScalar(kCellSize));
 *         this->drawRow(canvas, SkSamplingOptions::Aniso(16));
 *         canvas->translate(0, SkIntToScalar(kCellSize));
 *     }
 * private:
 *     inline static constexpr int kCellSize = 50;
 *     inline static constexpr int kNumRows = 5;
 *     inline static constexpr int kNumCols = 6;
 *
 *     bool            fDoAA;
 *     SkPath          fPath;
 *     sk_sp<SkShader> fLinearGrad1;
 *     sk_sp<SkShader> fLinearGrad2;
 *     SkMatrix        fPerspMatrix;
 *     sk_sp<SkImage>  fImage;
 *     sk_sp<SkImage>  fBitmapImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PerspShadersGM public constructor(
  doAA: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCellSize = 50
   * ```
   */
  private var fDoAA: Boolean = TODO("Initialize fDoAA")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumRows = 5
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumCols = 6
   * ```
   */
  private var fLinearGrad1: SkSp<SkShader> = TODO("Initialize fLinearGrad1")

  /**
   * C++ original:
   * ```cpp
   * bool            fDoAA
   * ```
   */
  private var fLinearGrad2: SkSp<SkShader> = TODO("Initialize fLinearGrad2")

  /**
   * C++ original:
   * ```cpp
   * SkPath          fPath
   * ```
   */
  private var fPerspMatrix: SkMatrix = TODO("Initialize fPerspMatrix")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fLinearGrad1
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fLinearGrad2
   * ```
   */
  private var fBitmapImage: SkSp<SkImage> = TODO("Initialize fBitmapImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name;
   *         name.printf("persp_shaders_%s",
   *                      fDoAA ? "aa" : "bw");
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
   *         return SkISize::Make(kCellSize*kNumCols, kCellSize*kNumRows);
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
   *         fBitmapImage = ToolUtils::create_checkerboard_image(
   *                 kCellSize, kCellSize, SK_ColorBLUE, SK_ColorYELLOW, kCellSize / 10);
   *
   *         SkPoint pts1[] = {
   *             { 0, 0 },
   *             { SkIntToScalar(kCellSize), SkIntToScalar(kCellSize) }
   *         };
   *         SkPoint pts2[] = {
   *             { 0, 0 },
   *             { 0, SkIntToScalar(kCellSize) }
   *         };
   *         constexpr SkColor4f colors[] = {
   *             SkColors::kRed, SkColors::kGreen, SkColors::kRed, SkColors::kGreen, SkColors::kRed
   *         };
   *         constexpr SkScalar pos[] = { 0, 0.25f, 0.5f, 0.75f, SK_Scalar1 };
   *
   *         fLinearGrad1 = SkShaders::LinearGradient(pts1, {{colors, pos, SkTileMode::kClamp}, {}});
   *         fLinearGrad2 = SkShaders::LinearGradient(pts2, {{colors, pos, SkTileMode::kClamp}, {}});
   *
   *         fPerspMatrix.reset();
   *         fPerspMatrix.setPerspY(SK_Scalar1 / 50);
   *
   *         fPath = SkPathBuilder()
   *                 .moveTo(0, 0)
   *                 .lineTo(0, SkIntToScalar(kCellSize))
   *                 .lineTo(kCellSize/2.0f, kCellSize/2.0f)
   *                 .lineTo(SkIntToScalar(kCellSize), SkIntToScalar(kCellSize))
   *                 .lineTo(SkIntToScalar(kCellSize), 0)
   *                 .close()
   *                 .detach();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRow(SkCanvas* canvas, const SkSamplingOptions& sampling) {
   *         SkPaint filterPaint;
   *         filterPaint.setAntiAlias(fDoAA);
   *
   *         SkPaint pathPaint;
   *         pathPaint.setShader(fBitmapImage->makeShader(sampling));
   *         pathPaint.setAntiAlias(fDoAA);
   *
   *         SkPaint gradPaint1;
   *         gradPaint1.setShader(fLinearGrad1);
   *         gradPaint1.setAntiAlias(fDoAA);
   *         SkPaint gradPaint2;
   *         gradPaint2.setShader(fLinearGrad2);
   *         gradPaint2.setAntiAlias(fDoAA);
   *
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(kCellSize), SkIntToScalar(kCellSize));
   *
   *         canvas->save();
   *
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawImageRect(fBitmapImage, r, sampling, &filterPaint);
   *         canvas->restore();
   *
   *         canvas->translate(SkIntToScalar(kCellSize), 0);
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawImage(fImage.get(), 0, 0, sampling, &filterPaint);
   *         canvas->restore();
   *
   *         canvas->translate(SkIntToScalar(kCellSize), 0);
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawRect(r, pathPaint);
   *         canvas->restore();
   *
   *         canvas->translate(SkIntToScalar(kCellSize), 0);
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawPath(fPath, pathPaint);
   *         canvas->restore();
   *
   *         canvas->translate(SkIntToScalar(kCellSize), 0);
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawRect(r, gradPaint1);
   *         canvas->restore();
   *
   *         canvas->translate(SkIntToScalar(kCellSize), 0);
   *         canvas->save();
   *         canvas->concat(fPerspMatrix);
   *         canvas->drawPath(fPath, gradPaint2);
   *         canvas->restore();
   *
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun drawRow(canvas: SkCanvas?, sampling: SkSamplingOptions) {
    TODO("Implement drawRow")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (!fImage || !fImage->isValid(canvas->baseRecorder())) {
   *             fImage = make_image(canvas, kCellSize, kCellSize);
   *         }
   *
   *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kNearest));
   *         canvas->translate(0, SkIntToScalar(kCellSize));
   *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kLinear));
   *         canvas->translate(0, SkIntToScalar(kCellSize));
   *         this->drawRow(canvas, SkSamplingOptions(SkFilterMode::kLinear,
   *                                                 SkMipmapMode::kNearest));
   *         canvas->translate(0, SkIntToScalar(kCellSize));
   *         this->drawRow(canvas, SkSamplingOptions(SkCubicResampler::Mitchell()));
   *         canvas->translate(0, SkIntToScalar(kCellSize));
   *         this->drawRow(canvas, SkSamplingOptions::Aniso(16));
   *         canvas->translate(0, SkIntToScalar(kCellSize));
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kCellSize: Int = TODO("Initialize kCellSize")

    private val kNumRows: Int = TODO("Initialize kNumRows")

    private val kNumCols: Int = TODO("Initialize kNumCols")
  }
}
