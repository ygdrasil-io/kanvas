package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class MatrixConvolutionGM : public GM {
 * public:
 *     MatrixConvolutionGM(SkColor colorOne, SkColor colorTwo, KernelFixture kernelFixture, const char* nameSuffix)
 *             : fNameSuffix(nameSuffix),
 *               fKernelFixture(kernelFixture) {
 *         this->setBGColor(0x00000000);
 *         fColors[0] = SkColor4f::FromColor(colorOne);
 *         fColors[1] = SkColor4f::FromColor(colorTwo);
 *     }
 *
 * protected:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkStringPrintf("matrixconvolution%s", fNameSuffix); }
 *
 *     void makeBitmap() {
 *         // Draw our bitmap in N32, so legacy devices get "premul" values they understand
 *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(80, 80));
 *         SkPaint paint;
 *         paint.setColor(0xFFFFFFFF);
 *         SkPoint pts[2] = { {0, 0},
 *                            {0, 80.0f} };
 *         SkScalar pos[2] = { 0, 80.0f };
 *         paint.setShader(SkShaders::LinearGradient(pts, {{fColors, pos, SkTileMode::kClamp}, {}}));
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 180.0f);
 *         surf->getCanvas()->drawString("e", -10.0f, 80.0f, font, paint);
 *         fImage = surf->makeImageSnapshot();
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(500, 300); }
 *
 *     sk_sp<SkImageFilter> makeFilter(const SkIPoint &kernelOffsetIn,
 *                                     SkTileMode tileMode,
 *                                     bool convolveAlpha) {
 *         // The kernelOffset is specified in a 0..2 coordinate space.
 *         float normalizedXOffset = kernelOffsetIn.fX / 2.0f;
 *         float normalizedYOffset = kernelOffsetIn.fY / 2.0f;
 *         // Must provide a cropping geometry in order for 'tileMode' to be well defined.
 *         SkIRect tileBoundary = fImage->bounds();
 *         switch (fKernelFixture) {
 *             case kBasic_KernelFixture: {
 *                 SkIPoint kernelOffset {SkScalarRoundToInt(2*normalizedXOffset),
 *                                        SkScalarRoundToInt(2*normalizedYOffset)};
 *                 // All 1s except center value, which is -7 (sum of 1).
 *                 std::vector<SkScalar> kernel(9, SkIntToScalar(1));
 *                 kernel[4] = SkIntToScalar(-7);
 *                 return SkImageFilters::MatrixConvolution(
 *                         {3,3}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
 *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
 *             }
 *             case kLarge_KernelFixture: {
 *                 SkIPoint kernelOffset {SkScalarRoundToInt(6*normalizedXOffset),
 *                                        SkScalarRoundToInt(6*normalizedYOffset)};
 *                 // This ensures the texture fallback path will be taken
 *                 static_assert(49 > skgpu::kMaxBlurSamples);
 *                 // All 1s except center value, which is -47 (sum of 1).
 *                 std::vector<SkScalar> kernel(49, SkIntToScalar(1));
 *                 kernel[24] = SkIntToScalar(-47);
 *                 return SkImageFilters::MatrixConvolution(
 *                         {7,7}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
 *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
 *             }
 *             case kLarger_KernelFixture: {
 *                 SkIPoint kernelOffset {SkScalarRoundToInt(127*normalizedXOffset), 0};
 *                 // This ensures the texture fallback path will be taken
 *                 static_assert(128 > skgpu::kMaxBlurSamples);
 *                 std::vector<float> kernel(128, 0.0f);
 *                 kernel[64] = 0.5f;
 *                 kernel[65] = -0.5f;
 *                 return SkImageFilters::MatrixConvolution(
 *                         {128,1}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
 *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
 *             }
 *             case kLargest_KernelFixture: {
 *                 SkIPoint kernelOffset {0, SkScalarRoundToInt(254*normalizedYOffset)};
 *                 // This ensures the texture fallback path will be taken
 *                 static_assert(255 > skgpu::kMaxBlurSamples);
 *                 std::vector<float> kernel(255, 0.0f);
 *                 kernel[126] = 0.5f;
 *                 kernel[128] = -0.5f;
 *                 return SkImageFilters::MatrixConvolution(
 *                         {1,255}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
 *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
 *             }
 *             default:
 *                 return nullptr;
 *         }
 *     }
 *
 *     void draw(SkCanvas* canvas, int x, int y, const SkIPoint& kernelOffset,
 *               SkTileMode tileMode, bool convolveAlpha,
 *               const SkIRect* cropRect = nullptr) {
 *         SkPaint paint;
 *         auto filter = this->makeFilter(kernelOffset, tileMode, convolveAlpha);
 *         if (cropRect) {
 *             filter = SkImageFilters::Crop(SkRect::Make(*cropRect), std::move(filter));
 *         }
 *         paint.setImageFilter(std::move(filter));
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
 *         canvas->drawImage(fImage, 0, 0, {}, &paint);
 *         canvas->restore();
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         this->makeBitmap();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *         SkIPoint kernelOffset = SkIPoint::Make(1, 0);
 *         for (int x = 10; x < 310; x += 100) {
 *             this->draw(canvas, x, 10, kernelOffset, SkTileMode::kClamp, true);
 *             this->draw(canvas, x, 110, kernelOffset, SkTileMode::kDecal, true);
 *             this->draw(canvas, x, 210, kernelOffset, SkTileMode::kRepeat, true);
 *             kernelOffset.fY++;
 *         }
 *         kernelOffset.fY = 1;
 *         SkIRect smallRect = SkIRect::MakeXYWH(10, 5, 60, 60);
 *         this->draw(canvas, 310, 10, kernelOffset, SkTileMode::kClamp, true, &smallRect);
 *         this->draw(canvas, 310, 110, kernelOffset, SkTileMode::kDecal, true, &smallRect);
 *         this->draw(canvas, 310, 210, kernelOffset, SkTileMode::kRepeat, true, &smallRect);
 *
 *         this->draw(canvas, 410, 10, kernelOffset, SkTileMode::kClamp, false);
 *         this->draw(canvas, 410, 110, kernelOffset, SkTileMode::kDecal, false);
 *         this->draw(canvas, 410, 210, kernelOffset, SkTileMode::kRepeat, false);
 *     }
 *
 * private:
 *     sk_sp<SkImage> fImage;
 *     SkColor4f fColors[2];
 *     const char* fNameSuffix;
 *     KernelFixture fKernelFixture;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class MatrixConvolutionGM public constructor(
  colorOne: SkColor,
  colorTwo: SkColor,
  kernelFixture: KernelFixture,
  nameSuffix: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColors[2]
   * ```
   */
  private var fColors: Array<SkColor4f> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * const char* fNameSuffix
   * ```
   */
  private val fNameSuffix: String? = TODO("Initialize fNameSuffix")

  /**
   * C++ original:
   * ```cpp
   * KernelFixture fKernelFixture
   * ```
   */
  private var fKernelFixture: KernelFixture = TODO("Initialize fKernelFixture")

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkStringPrintf("matrixconvolution%s", fNameSuffix); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void makeBitmap() {
   *         // Draw our bitmap in N32, so legacy devices get "premul" values they understand
   *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(80, 80));
   *         SkPaint paint;
   *         paint.setColor(0xFFFFFFFF);
   *         SkPoint pts[2] = { {0, 0},
   *                            {0, 80.0f} };
   *         SkScalar pos[2] = { 0, 80.0f };
   *         paint.setShader(SkShaders::LinearGradient(pts, {{fColors, pos, SkTileMode::kClamp}, {}}));
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 180.0f);
   *         surf->getCanvas()->drawString("e", -10.0f, 80.0f, font, paint);
   *         fImage = surf->makeImageSnapshot();
   *     }
   * ```
   */
  protected fun makeBitmap() {
    TODO("Implement makeBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 300); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> makeFilter(const SkIPoint &kernelOffsetIn,
   *                                     SkTileMode tileMode,
   *                                     bool convolveAlpha) {
   *         // The kernelOffset is specified in a 0..2 coordinate space.
   *         float normalizedXOffset = kernelOffsetIn.fX / 2.0f;
   *         float normalizedYOffset = kernelOffsetIn.fY / 2.0f;
   *         // Must provide a cropping geometry in order for 'tileMode' to be well defined.
   *         SkIRect tileBoundary = fImage->bounds();
   *         switch (fKernelFixture) {
   *             case kBasic_KernelFixture: {
   *                 SkIPoint kernelOffset {SkScalarRoundToInt(2*normalizedXOffset),
   *                                        SkScalarRoundToInt(2*normalizedYOffset)};
   *                 // All 1s except center value, which is -7 (sum of 1).
   *                 std::vector<SkScalar> kernel(9, SkIntToScalar(1));
   *                 kernel[4] = SkIntToScalar(-7);
   *                 return SkImageFilters::MatrixConvolution(
   *                         {3,3}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
   *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
   *             }
   *             case kLarge_KernelFixture: {
   *                 SkIPoint kernelOffset {SkScalarRoundToInt(6*normalizedXOffset),
   *                                        SkScalarRoundToInt(6*normalizedYOffset)};
   *                 // This ensures the texture fallback path will be taken
   *                 static_assert(49 > skgpu::kMaxBlurSamples);
   *                 // All 1s except center value, which is -47 (sum of 1).
   *                 std::vector<SkScalar> kernel(49, SkIntToScalar(1));
   *                 kernel[24] = SkIntToScalar(-47);
   *                 return SkImageFilters::MatrixConvolution(
   *                         {7,7}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
   *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
   *             }
   *             case kLarger_KernelFixture: {
   *                 SkIPoint kernelOffset {SkScalarRoundToInt(127*normalizedXOffset), 0};
   *                 // This ensures the texture fallback path will be taken
   *                 static_assert(128 > skgpu::kMaxBlurSamples);
   *                 std::vector<float> kernel(128, 0.0f);
   *                 kernel[64] = 0.5f;
   *                 kernel[65] = -0.5f;
   *                 return SkImageFilters::MatrixConvolution(
   *                         {128,1}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
   *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
   *             }
   *             case kLargest_KernelFixture: {
   *                 SkIPoint kernelOffset {0, SkScalarRoundToInt(254*normalizedYOffset)};
   *                 // This ensures the texture fallback path will be taken
   *                 static_assert(255 > skgpu::kMaxBlurSamples);
   *                 std::vector<float> kernel(255, 0.0f);
   *                 kernel[126] = 0.5f;
   *                 kernel[128] = -0.5f;
   *                 return SkImageFilters::MatrixConvolution(
   *                         {1,255}, kernel.data(), /* gain= */ 0.3f, /* bias= */ 100.0f,
   *                         kernelOffset, tileMode, convolveAlpha, nullptr, tileBoundary);
   *             }
   *             default:
   *                 return nullptr;
   *         }
   *     }
   * ```
   */
  protected fun makeFilter(
    kernelOffsetIn: SkIPoint,
    tileMode: SkTileMode,
    convolveAlpha: Boolean,
  ): SkSp<SkImageFilter> {
    TODO("Implement makeFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas, int x, int y, const SkIPoint& kernelOffset,
   *               SkTileMode tileMode, bool convolveAlpha,
   *               const SkIRect* cropRect = nullptr) {
   *         SkPaint paint;
   *         auto filter = this->makeFilter(kernelOffset, tileMode, convolveAlpha);
   *         if (cropRect) {
   *             filter = SkImageFilters::Crop(SkRect::Make(*cropRect), std::move(filter));
   *         }
   *         paint.setImageFilter(std::move(filter));
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
   *         canvas->drawImage(fImage, 0, 0, {}, &paint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun draw(
    canvas: SkCanvas?,
    x: Int,
    y: Int,
    kernelOffset: SkIPoint,
    tileMode: SkTileMode,
    convolveAlpha: Boolean,
    cropRect: SkIRect? = TODO(),
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->makeBitmap();
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
   *         canvas->clear(SK_ColorBLACK);
   *         SkIPoint kernelOffset = SkIPoint::Make(1, 0);
   *         for (int x = 10; x < 310; x += 100) {
   *             this->draw(canvas, x, 10, kernelOffset, SkTileMode::kClamp, true);
   *             this->draw(canvas, x, 110, kernelOffset, SkTileMode::kDecal, true);
   *             this->draw(canvas, x, 210, kernelOffset, SkTileMode::kRepeat, true);
   *             kernelOffset.fY++;
   *         }
   *         kernelOffset.fY = 1;
   *         SkIRect smallRect = SkIRect::MakeXYWH(10, 5, 60, 60);
   *         this->draw(canvas, 310, 10, kernelOffset, SkTileMode::kClamp, true, &smallRect);
   *         this->draw(canvas, 310, 110, kernelOffset, SkTileMode::kDecal, true, &smallRect);
   *         this->draw(canvas, 310, 210, kernelOffset, SkTileMode::kRepeat, true, &smallRect);
   *
   *         this->draw(canvas, 410, 10, kernelOffset, SkTileMode::kClamp, false);
   *         this->draw(canvas, 410, 110, kernelOffset, SkTileMode::kDecal, false);
   *         this->draw(canvas, 410, 210, kernelOffset, SkTileMode::kRepeat, false);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
