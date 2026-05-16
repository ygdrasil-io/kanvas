package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkYUVAPixmaps
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class YUVtoRGBSubsetEffect : public GM {
 * public:
 *     YUVtoRGBSubsetEffect() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("yuv_to_rgb_subset_effect"); }
 *
 *     SkISize getISize() override { return {1310, 540}; }
 *
 *     void makePixmaps() {
 *         SkYUVAInfo yuvaInfo = SkYUVAInfo({8, 8},
 *                                          SkYUVAInfo::PlaneConfig::kY_U_V,
 *                                          SkYUVAInfo::Subsampling::k420,
 *                                          kJPEG_Full_SkYUVColorSpace);
 *         SkColorType colorTypes[] = {kAlpha_8_SkColorType,
 *                                     kAlpha_8_SkColorType,
 *                                     kAlpha_8_SkColorType};
 *         SkYUVAPixmapInfo pmapInfo(yuvaInfo, colorTypes, nullptr);
 *         fPixmaps = SkYUVAPixmaps::Allocate(pmapInfo);
 *
 *         unsigned char innerY[16] = {149, 160, 130, 105,
 *                                     160, 130, 105, 149,
 *                                     130, 105, 149, 160,
 *                                     105, 149, 160, 130};
 *         unsigned char innerU[4] = {43, 75, 145, 200};
 *         unsigned char innerV[4] = {88, 180, 200, 43};
 *         int outerYUV[] = {128, 128, 128};
 *         SkBitmap bitmaps[3];
 *         for (int i = 0; i < 3; ++i) {
 *             bitmaps[i].installPixels(fPixmaps.plane(i));
 *             bitmaps[i].eraseColor(SkColorSetARGB(outerYUV[i], 0, 0, 0));
 *         }
 *         SkPixmap innerYPM(SkImageInfo::MakeA8(4, 4), innerY, 4);
 *         SkPixmap innerUPM(SkImageInfo::MakeA8(2, 2), innerU, 2);
 *         SkPixmap innerVPM(SkImageInfo::MakeA8(2, 2), innerV, 2);
 *         bitmaps[0].writePixels(innerYPM, 2, 2);
 *         bitmaps[1].writePixels(innerUPM, 1, 1);
 *         bitmaps[2].writePixels(innerVPM, 1, 1);
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 *         skgpu::graphite::Recorder* recorder = nullptr;
 *         GrDirectContext* context = nullptr;
 *
 * #if defined(SK_GRAPHITE)
 *         recorder = canvas->recorder();
 * #endif
 * #if defined(SK_GANESH)
 *         context = GrAsDirectContext(canvas->recordingContext());
 * #endif
 *
 *         if (!context && !recorder) {
 *             return DrawResult::kSkip;
 *         }
 *
 *         if (!fPixmaps.isValid()) {
 *             this->makePixmaps();
 *         }
 *
 *         auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(fPixmaps);
 * #if defined(SK_GRAPHITE)
 *         if (recorder) {
 *             fYUVImage = lazyYUV->refImage(recorder, sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
 *         }
 * #endif
 * #if defined(SK_GANESH)
 *         if (context) {
 *             fYUVImage = lazyYUV->refImage(context, sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
 *         }
 * #endif
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override { fYUVImage.reset(); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkRecorder* recorder = canvas->baseRecorder();
 *         if (!recorder) {
 *             *errorMsg = kErrorMsg_DrawSkippedGpuOnly;
 *             return DrawResult::kSkip;
 *         }
 *
 *         if (!fYUVImage) {
 *             *errorMsg = "No valid YUV image generated -- skipping";
 *             return DrawResult::kSkip;
 *         }
 *
 *         static const SkFilterMode kFilters[] = {SkFilterMode::kNearest,
 *                                                 SkFilterMode::kLinear};
 *         static const SkIRect kColorRect = SkIRect::MakeLTRB(2, 2, 6, 6);
 *
 *         // Outset to visualize wrap modes.
 *         SkRect rect = SkRect::Make(fYUVImage->dimensions());
 *         rect = rect.makeOutset(fYUVImage->width()/2.f, fYUVImage->height()/2.f);
 *
 *         SkScalar y = kTestPad;
 *         // Rows are filter modes.
 *         for (uint32_t i = 0; i < std::size(kFilters); ++i) {
 *             SkScalar x = kTestPad;
 *             // Columns are non-subsetted followed by subsetted with each TileMode in a row
 *             for (uint32_t j = 0; j < kSkTileModeCount + 1; ++j) {
 *                 SkMatrix ctm = SkMatrix::Translate(x, y);
 *                 ctm.postScale(10.f, 10.f);
 *
 *                 const SkIRect* subset = j > 0 ? &kColorRect : nullptr;
 *
 *                 auto tm = SkTileMode::kClamp;
 *                 if (j > 0) {
 *                     tm = static_cast<SkTileMode>(j - 1);
 *                 }
 *
 *                 canvas->save();
 *                 canvas->concat(ctm);
 *                 SkSamplingOptions sampling(kFilters[i]);
 *                 SkPaint paint;
 *                 // Draw black rectangle in background so rendering with Decal tilemode matches
 *                 // the previously used ClampToBorder wrapmode.
 *                 paint.setColor(SK_ColorBLACK);
 *                 canvas->drawRect(rect, paint);
 *                 if (subset) {
 *                     sk_sp<SkImage> subsetImg = fYUVImage->makeSubset(recorder, *subset, {false});
 *                     SkASSERT(subsetImg);
 *                     paint.setShader(subsetImg->makeShader(tm, tm,
 *                                                           sampling, SkMatrix::Translate(2, 2)));
 *                 } else {
 *                     paint.setShader(fYUVImage->makeShader(tm, tm,
 *                                                           sampling, SkMatrix::I()));
 *                 }
 *                 canvas->drawRect(rect, paint);
 *                 canvas->restore();
 *                 x += rect.width() + kTestPad;
 *             }
 *
 *             y += rect.height() + kTestPad;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     SkYUVAPixmaps fPixmaps;
 *     sk_sp<SkImage> fYUVImage;
 *
 *     inline static constexpr SkScalar kTestPad = 10.f;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class YUVtoRGBSubsetEffect public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps fPixmaps
   * ```
   */
  private var fPixmaps: SkYUVAPixmaps = TODO("Initialize fPixmaps")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fYUVImage
   * ```
   */
  private var fYUVImage: SkSp<SkImage> = TODO("Initialize fYUVImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("yuv_to_rgb_subset_effect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1310, 540}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void makePixmaps() {
   *         SkYUVAInfo yuvaInfo = SkYUVAInfo({8, 8},
   *                                          SkYUVAInfo::PlaneConfig::kY_U_V,
   *                                          SkYUVAInfo::Subsampling::k420,
   *                                          kJPEG_Full_SkYUVColorSpace);
   *         SkColorType colorTypes[] = {kAlpha_8_SkColorType,
   *                                     kAlpha_8_SkColorType,
   *                                     kAlpha_8_SkColorType};
   *         SkYUVAPixmapInfo pmapInfo(yuvaInfo, colorTypes, nullptr);
   *         fPixmaps = SkYUVAPixmaps::Allocate(pmapInfo);
   *
   *         unsigned char innerY[16] = {149, 160, 130, 105,
   *                                     160, 130, 105, 149,
   *                                     130, 105, 149, 160,
   *                                     105, 149, 160, 130};
   *         unsigned char innerU[4] = {43, 75, 145, 200};
   *         unsigned char innerV[4] = {88, 180, 200, 43};
   *         int outerYUV[] = {128, 128, 128};
   *         SkBitmap bitmaps[3];
   *         for (int i = 0; i < 3; ++i) {
   *             bitmaps[i].installPixels(fPixmaps.plane(i));
   *             bitmaps[i].eraseColor(SkColorSetARGB(outerYUV[i], 0, 0, 0));
   *         }
   *         SkPixmap innerYPM(SkImageInfo::MakeA8(4, 4), innerY, 4);
   *         SkPixmap innerUPM(SkImageInfo::MakeA8(2, 2), innerU, 2);
   *         SkPixmap innerVPM(SkImageInfo::MakeA8(2, 2), innerV, 2);
   *         bitmaps[0].writePixels(innerYPM, 2, 2);
   *         bitmaps[1].writePixels(innerUPM, 1, 1);
   *         bitmaps[2].writePixels(innerVPM, 1, 1);
   *     }
   * ```
   */
  protected fun makePixmaps() {
    TODO("Implement makePixmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   *         skgpu::graphite::Recorder* recorder = nullptr;
   *         GrDirectContext* context = nullptr;
   *
   * #if defined(SK_GRAPHITE)
   *         recorder = canvas->recorder();
   * #endif
   * #if defined(SK_GANESH)
   *         context = GrAsDirectContext(canvas->recordingContext());
   * #endif
   *
   *         if (!context && !recorder) {
   *             return DrawResult::kSkip;
   *         }
   *
   *         if (!fPixmaps.isValid()) {
   *             this->makePixmaps();
   *         }
   *
   *         auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(fPixmaps);
   * #if defined(SK_GRAPHITE)
   *         if (recorder) {
   *             fYUVImage = lazyYUV->refImage(recorder, sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
   *         }
   * #endif
   * #if defined(SK_GANESH)
   *         if (context) {
   *             fYUVImage = lazyYUV->refImage(context, sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
   *         }
   * #endif
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onGpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override { fYUVImage.reset(); }
   * ```
   */
  protected override fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkRecorder* recorder = canvas->baseRecorder();
   *         if (!recorder) {
   *             *errorMsg = kErrorMsg_DrawSkippedGpuOnly;
   *             return DrawResult::kSkip;
   *         }
   *
   *         if (!fYUVImage) {
   *             *errorMsg = "No valid YUV image generated -- skipping";
   *             return DrawResult::kSkip;
   *         }
   *
   *         static const SkFilterMode kFilters[] = {SkFilterMode::kNearest,
   *                                                 SkFilterMode::kLinear};
   *         static const SkIRect kColorRect = SkIRect::MakeLTRB(2, 2, 6, 6);
   *
   *         // Outset to visualize wrap modes.
   *         SkRect rect = SkRect::Make(fYUVImage->dimensions());
   *         rect = rect.makeOutset(fYUVImage->width()/2.f, fYUVImage->height()/2.f);
   *
   *         SkScalar y = kTestPad;
   *         // Rows are filter modes.
   *         for (uint32_t i = 0; i < std::size(kFilters); ++i) {
   *             SkScalar x = kTestPad;
   *             // Columns are non-subsetted followed by subsetted with each TileMode in a row
   *             for (uint32_t j = 0; j < kSkTileModeCount + 1; ++j) {
   *                 SkMatrix ctm = SkMatrix::Translate(x, y);
   *                 ctm.postScale(10.f, 10.f);
   *
   *                 const SkIRect* subset = j > 0 ? &kColorRect : nullptr;
   *
   *                 auto tm = SkTileMode::kClamp;
   *                 if (j > 0) {
   *                     tm = static_cast<SkTileMode>(j - 1);
   *                 }
   *
   *                 canvas->save();
   *                 canvas->concat(ctm);
   *                 SkSamplingOptions sampling(kFilters[i]);
   *                 SkPaint paint;
   *                 // Draw black rectangle in background so rendering with Decal tilemode matches
   *                 // the previously used ClampToBorder wrapmode.
   *                 paint.setColor(SK_ColorBLACK);
   *                 canvas->drawRect(rect, paint);
   *                 if (subset) {
   *                     sk_sp<SkImage> subsetImg = fYUVImage->makeSubset(recorder, *subset, {false});
   *                     SkASSERT(subsetImg);
   *                     paint.setShader(subsetImg->makeShader(tm, tm,
   *                                                           sampling, SkMatrix::Translate(2, 2)));
   *                 } else {
   *                     paint.setShader(fYUVImage->makeShader(tm, tm,
   *                                                           sampling, SkMatrix::I()));
   *                 }
   *                 canvas->drawRect(rect, paint);
   *                 canvas->restore();
   *                 x += rect.width() + kTestPad;
   *             }
   *
   *             y += rect.height() + kTestPad;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kTestPad: SkScalar = TODO("Initialize kTestPad")
  }
}
