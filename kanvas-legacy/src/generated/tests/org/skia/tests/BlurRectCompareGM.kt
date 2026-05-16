package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BlurRectCompareGM : public GM {
 * protected:
 *     SkString getName() const override { return SkString("blurrect_compare"); }
 *
 *     SkISize getISize() override { return {900, 1220}; }
 *
 *     void onOnceBeforeDraw() override { this->prepareReferenceMasks(); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (canvas->imageInfo().colorType() == kUnknown_SkColorType) {
 *             *errorMsg = "Not supported when recording, relies on canvas->makeSurface()";
 *             return DrawResult::kSkip;
 *         }
 *
 *         int32_t ctxID = 0;
 * #if defined(SK_GANESH)
 *         if (auto rc = canvas->recordingContext()) {
 *             if (!rc->asDirectContext()) {
 *                 *errorMsg = "Not supported when recording, relies on canvas->makeSurface()";
 *                 return DrawResult::kSkip;
 *             }
 *             ctxID = rc->priv().contextID();
 *         }
 * #endif
 *
 *         if (fRecalcMasksForAnimation || !fActualMasks[0][0][0] || ctxID != fLastContextUniqueID) {
 *             if (fRecalcMasksForAnimation) {
 *                 // Sigma is changing so references must also be recalculated.
 *                 this->prepareReferenceMasks();
 *             }
 *             this->prepareActualMasks(canvas);
 *             this->prepareMaskDifferences(canvas);
 *             fLastContextUniqueID = ctxID;
 *             fRecalcMasksForAnimation = false;
 *         }
 *         canvas->clear(SK_ColorBLACK);
 *         static constexpr float kMargin = 30;
 *         float totalW = 0;
 *         for (auto w : kSizes) {
 *             totalW += w + kMargin;
 *         }
 *         canvas->translate(kMargin, kMargin);
 *         for (int mode = 0; mode < 3; ++mode) {
 *             canvas->save();
 *             for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
 *                 auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
 *                 for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
 *                     auto h = kSizes[heightIdx];
 *                     canvas->save();
 *                     for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
 *                         auto w = kSizes[widthIdx];
 *                         SkPaint paint;
 *                         paint.setColor(SK_ColorWHITE);
 *                         SkImage* img;
 *                         switch (mode) {
 *                             case 0:
 *                                 img = fReferenceMasks[sigmaIdx][heightIdx][widthIdx].get();
 *                                 break;
 *                             case 1:
 *                                 img = fActualMasks[sigmaIdx][heightIdx][widthIdx].get();
 *                                 break;
 *                             case 2:
 *                                 img = fMaskDifferences[sigmaIdx][heightIdx][widthIdx].get();
 *                                 // The error images are opaque, use kPlus so they are additive if
 *                                 // the overlap between test cases.
 *                                 paint.setBlendMode(SkBlendMode::kPlus);
 *                                 break;
 *                         }
 *                         auto pad = PadForSigma(sigma);
 *                         canvas->drawImage(img, -pad, -pad, SkSamplingOptions(), &paint);
 * #if 0  // Uncomment to hairline stroke around blurred rect in red on top of the blur result.
 *        // The rect is defined at integer coords. We inset by 1/2 pixel so our stroke lies on top
 *        // of the edge pixels.
 *                         SkPaint stroke;
 *                         stroke.setColor(SK_ColorRED);
 *                         stroke.setStrokeWidth(0.f);
 *                         stroke.setStyle(SkPaint::kStroke_Style);
 *                         canvas->drawRect(SkRect::MakeWH(w, h).makeInset(0.5, 0.5), stroke);
 * #endif
 *                         canvas->translate(w + kMargin, 0.f);
 *                     }
 *                     canvas->restore();
 *                     canvas->translate(0, h + kMargin);
 *                 }
 *             }
 *             canvas->restore();
 *             canvas->translate(totalW + 2 * kMargin, 0);
 *         }
 *         return DrawResult::kOk;
 *     }
 *     bool onAnimate(double nanos) override {
 *         fSigmaAnimationBoost = TimeUtils::SineWave(nanos, 5, 2.5f, 0.f, 2.f);
 *         fRecalcMasksForAnimation = true;
 *         return true;
 *     }
 *
 * private:
 *     void prepareReferenceMasks() {
 *         auto create_reference_mask = [](int w, int h, float sigma, int numSubpixels) {
 *             int pad = PadForSigma(sigma);
 *             int maskW = w + 2 * pad;
 *             int maskH = h + 2 * pad;
 *             // We'll do all our calculations at subpixel resolution, so adjust params
 *             w *= numSubpixels;
 *             h *= numSubpixels;
 *             sigma *= numSubpixels;
 *             auto scale = SK_ScalarRoot2Over2 / sigma;
 *             auto def_integral_approx = [scale](float a, float b) {
 *                 return 0.5f * (std::erf(b * scale) - std::erf(a * scale));
 *             };
 *             // Do the x-pass. Above/below rect are rows of zero. All rows that intersect the rect
 *             // are the same. The row is calculated and stored at subpixel resolution.
 *             SkASSERT(!(numSubpixels & 0b1));
 *             std::unique_ptr<float[]> row(new float[maskW * numSubpixels]);
 *             for (int col = 0; col < maskW * numSubpixels; ++col) {
 *                 // Compute distance to rect left in subpixel units
 *                 float ldiff = numSubpixels * pad - (col + 0.5f);
 *                 float rdiff = ldiff + w;
 *                 row[col] = def_integral_approx(ldiff, rdiff);
 *             }
 *             // y-pass
 *             SkBitmap bmp;
 *             bmp.allocPixels(SkImageInfo::MakeA8(maskW, maskH));
 *             std::unique_ptr<float[]> accums(new float[maskW]);
 *             const float accumScale = 1.f / (numSubpixels * numSubpixels);
 *             for (int y = 0; y < maskH; ++y) {
 *                 // Initialize subpixel accumulation buffer for this row.
 *                 std::fill_n(accums.get(), maskW, 0);
 *                 for (int ys = 0; ys < numSubpixels; ++ys) {
 *                     // At each subpixel we want to integrate over the kernel centered at the
 *                     // subpixel multiplied by the x-pass. The x-pass is zero above and below the
 *                     // rect and constant valued from rect top to rect bottom. So we can get the
 *                     // integral of just the kernel from rect top to rect bottom and multiply by
 *                     // the single x-pass value from our precomputed row.
 *                     float tdiff = numSubpixels * pad - (y * numSubpixels + ys + 0.5f);
 *                     float bdiff = tdiff + h;
 *                     auto integral = def_integral_approx(tdiff, bdiff);
 *                     for (int x = 0; x < maskW; ++x) {
 *                         for (int xs = 0; xs < numSubpixels; ++xs) {
 *                             int rowIdx = x * numSubpixels + xs;
 *                             accums[x] += integral * row[rowIdx];
 *                         }
 *                     }
 *                 }
 *                 for (int x = 0; x < maskW; ++x) {
 *                     auto result = accums[x] * accumScale;
 *                     *bmp.getAddr8(x, y) = SkToU8(sk_float_round2int(255.f * result));
 *                 }
 *             }
 *             return bmp.asImage();
 *         };
 *
 *         // Number of times to subsample (in both X and Y). If fRecalcMasksForAnimation is true
 *         // then we're animating, don't subsample as much to keep fps higher.
 *         const int numSubpixels = fRecalcMasksForAnimation ? 2 : 8;
 *
 *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
 *             auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
 *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
 *                 auto h = kSizes[heightIdx];
 *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
 *                     auto w = kSizes[widthIdx];
 *                     fReferenceMasks[sigmaIdx][heightIdx][widthIdx] =
 *                             create_reference_mask(w, h, sigma, numSubpixels);
 *                 }
 *             }
 *         }
 *     }
 *
 *     void prepareActualMasks(SkCanvas* canvas) {
 *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
 *             auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
 *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
 *                 auto h = kSizes[heightIdx];
 *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
 *                     auto w = kSizes[widthIdx];
 *                     auto pad = PadForSigma(sigma);
 *                     auto ii = SkImageInfo::MakeA8(w + 2 * pad, h + 2 * pad);
 *                     auto surf = canvas->makeSurface(ii);
 *                     if (!surf) {
 *                         // Some GPUs don't have renderable A8 :(
 *                         surf = canvas->makeSurface(ii.makeColorType(kRGBA_8888_SkColorType));
 *                         if (!surf) {
 *                             return;
 *                         }
 *                     }
 *                     auto rect = SkRect::MakeXYWH(pad, pad, w, h);
 *                     SkPaint paint;
 *                     // Color doesn't matter if we're rendering to A8 but does if we promoted to
 *                     // RGBA above.
 *                     paint.setColor(SK_ColorWHITE);
 *                     paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma));
 *                     surf->getCanvas()->drawRect(rect, paint);
 *                     fActualMasks[sigmaIdx][heightIdx][widthIdx] = surf->makeImageSnapshot();
 *                 }
 *             }
 *         }
 *     }
 *
 *     void prepareMaskDifferences(SkCanvas* canvas) {
 *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
 *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
 *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
 *                     const auto& r =  fReferenceMasks[sigmaIdx][heightIdx][widthIdx];
 *                     const auto& a =     fActualMasks[sigmaIdx][heightIdx][widthIdx];
 *                     auto& d       = fMaskDifferences[sigmaIdx][heightIdx][widthIdx];
 *                     // The actual image might not be present if we're on an abandoned GrContext.
 *                     if (!a) {
 *                         d.reset();
 *                         continue;
 *                     }
 *                     SkASSERT(r->width() == a->width());
 *                     SkASSERT(r->height() == a->height());
 *                     auto ii = SkImageInfo::Make(r->width(), r->height(),
 *                                                 kRGBA_8888_SkColorType, kPremul_SkAlphaType);
 *                     auto surf = canvas->makeSurface(ii);
 *                     if (!surf) {
 *                         return;
 *                     }
 *                     // We visualize the difference by turning both the alpha masks into opaque green
 *                     // images (where alpha becomes the green channel) and then perform a
 *                     // SkBlendMode::kDifference between them.
 *                     SkPaint filterPaint;
 *                     filterPaint.setColor(SK_ColorWHITE);
 *                     // Actually 8 * alpha becomes green to really highlight differences.
 *                     static constexpr float kGreenifyM[] = {0, 0, 0, 0, 0,
 *                                                            0, 0, 0, 8, 0,
 *                                                            0, 0, 0, 0, 0,
 *                                                            0, 0, 0, 0, 1};
 *                     auto greenifyCF = SkColorFilters::Matrix(kGreenifyM);
 *                     SkPaint paint;
 *                     paint.setBlendMode(SkBlendMode::kSrc);
 *                     paint.setColorFilter(std::move(greenifyCF));
 *                     surf->getCanvas()->drawImage(a, 0, 0, SkSamplingOptions(), &paint);
 *                     paint.setBlendMode(SkBlendMode::kDifference);
 *                     surf->getCanvas()->drawImage(r, 0, 0, SkSamplingOptions(), &paint);
 *                     d = surf->makeImageSnapshot();
 *                 }
 *             }
 *         }
 *     }
 *
 *     // Per side padding around mask images for a sigma. Make this overly generous to ensure bugs
 *     // related to big blurs are fully visible.
 *     static int PadForSigma(float sigma) { return sk_float_ceil2int(4 * sigma); }
 *
 *     inline static constexpr int kSizes[] = {1, 2, 4, 8, 16, 32};
 *     inline static constexpr float kSigmas[] = {0.5f, 1.2f, 2.3f, 3.9f, 7.4f};
 *     inline static constexpr size_t kNumSizes = std::size(kSizes);
 *     inline static constexpr size_t kNumSigmas = std::size(kSigmas);
 *
 *     sk_sp<SkImage> fReferenceMasks[kNumSigmas][kNumSizes][kNumSizes];
 *     sk_sp<SkImage> fActualMasks[kNumSigmas][kNumSizes][kNumSizes];
 *     sk_sp<SkImage> fMaskDifferences[kNumSigmas][kNumSizes][kNumSizes];
 *     int32_t fLastContextUniqueID;
 *     // These are used only when animating.
 *     float fSigmaAnimationBoost = 0;
 *     bool fRecalcMasksForAnimation = false;
 * }
 * ```
 */
public open class BlurRectCompareGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kSizes[] = {1, 2, 4, 8, 16, 32}
   * ```
   */
  private var fReferenceMasks: Int = TODO("Initialize fReferenceMasks")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr float kSigmas[] = {0.5f, 1.2f, 2.3f, 3.9f, 7.4f}
   * ```
   */
  private var fActualMasks: Int = TODO("Initialize fActualMasks")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kNumSizes = std::size(kSizes)
   * ```
   */
  private var fMaskDifferences: Int = TODO("Initialize fMaskDifferences")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kNumSigmas = std::size(kSigmas)
   * ```
   */
  private var fLastContextUniqueID: Int = TODO("Initialize fLastContextUniqueID")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fReferenceMasks[kNumSigmas][kNumSizes][kNumSizes]
   * ```
   */
  private var fSigmaAnimationBoost: Float = TODO("Initialize fSigmaAnimationBoost")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fActualMasks[kNumSigmas][kNumSizes][kNumSizes]
   * ```
   */
  private var fRecalcMasksForAnimation: Boolean = TODO("Initialize fRecalcMasksForAnimation")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("blurrect_compare"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {900, 1220}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->prepareReferenceMasks(); }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (canvas->imageInfo().colorType() == kUnknown_SkColorType) {
   *             *errorMsg = "Not supported when recording, relies on canvas->makeSurface()";
   *             return DrawResult::kSkip;
   *         }
   *
   *         int32_t ctxID = 0;
   * #if defined(SK_GANESH)
   *         if (auto rc = canvas->recordingContext()) {
   *             if (!rc->asDirectContext()) {
   *                 *errorMsg = "Not supported when recording, relies on canvas->makeSurface()";
   *                 return DrawResult::kSkip;
   *             }
   *             ctxID = rc->priv().contextID();
   *         }
   * #endif
   *
   *         if (fRecalcMasksForAnimation || !fActualMasks[0][0][0] || ctxID != fLastContextUniqueID) {
   *             if (fRecalcMasksForAnimation) {
   *                 // Sigma is changing so references must also be recalculated.
   *                 this->prepareReferenceMasks();
   *             }
   *             this->prepareActualMasks(canvas);
   *             this->prepareMaskDifferences(canvas);
   *             fLastContextUniqueID = ctxID;
   *             fRecalcMasksForAnimation = false;
   *         }
   *         canvas->clear(SK_ColorBLACK);
   *         static constexpr float kMargin = 30;
   *         float totalW = 0;
   *         for (auto w : kSizes) {
   *             totalW += w + kMargin;
   *         }
   *         canvas->translate(kMargin, kMargin);
   *         for (int mode = 0; mode < 3; ++mode) {
   *             canvas->save();
   *             for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
   *                 auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
   *                 for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
   *                     auto h = kSizes[heightIdx];
   *                     canvas->save();
   *                     for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
   *                         auto w = kSizes[widthIdx];
   *                         SkPaint paint;
   *                         paint.setColor(SK_ColorWHITE);
   *                         SkImage* img;
   *                         switch (mode) {
   *                             case 0:
   *                                 img = fReferenceMasks[sigmaIdx][heightIdx][widthIdx].get();
   *                                 break;
   *                             case 1:
   *                                 img = fActualMasks[sigmaIdx][heightIdx][widthIdx].get();
   *                                 break;
   *                             case 2:
   *                                 img = fMaskDifferences[sigmaIdx][heightIdx][widthIdx].get();
   *                                 // The error images are opaque, use kPlus so they are additive if
   *                                 // the overlap between test cases.
   *                                 paint.setBlendMode(SkBlendMode::kPlus);
   *                                 break;
   *                         }
   *                         auto pad = PadForSigma(sigma);
   *                         canvas->drawImage(img, -pad, -pad, SkSamplingOptions(), &paint);
   * #if 0  // Uncomment to hairline stroke around blurred rect in red on top of the blur result.
   *        // The rect is defined at integer coords. We inset by 1/2 pixel so our stroke lies on top
   *        // of the edge pixels.
   *                         SkPaint stroke;
   *                         stroke.setColor(SK_ColorRED);
   *                         stroke.setStrokeWidth(0.f);
   *                         stroke.setStyle(SkPaint::kStroke_Style);
   *                         canvas->drawRect(SkRect::MakeWH(w, h).makeInset(0.5, 0.5), stroke);
   * #endif
   *                         canvas->translate(w + kMargin, 0.f);
   *                     }
   *                     canvas->restore();
   *                     canvas->translate(0, h + kMargin);
   *                 }
   *             }
   *             canvas->restore();
   *             canvas->translate(totalW + 2 * kMargin, 0);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fSigmaAnimationBoost = TimeUtils::SineWave(nanos, 5, 2.5f, 0.f, 2.f);
   *         fRecalcMasksForAnimation = true;
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * void prepareReferenceMasks() {
   *         auto create_reference_mask = [](int w, int h, float sigma, int numSubpixels) {
   *             int pad = PadForSigma(sigma);
   *             int maskW = w + 2 * pad;
   *             int maskH = h + 2 * pad;
   *             // We'll do all our calculations at subpixel resolution, so adjust params
   *             w *= numSubpixels;
   *             h *= numSubpixels;
   *             sigma *= numSubpixels;
   *             auto scale = SK_ScalarRoot2Over2 / sigma;
   *             auto def_integral_approx = [scale](float a, float b) {
   *                 return 0.5f * (std::erf(b * scale) - std::erf(a * scale));
   *             };
   *             // Do the x-pass. Above/below rect are rows of zero. All rows that intersect the rect
   *             // are the same. The row is calculated and stored at subpixel resolution.
   *             SkASSERT(!(numSubpixels & 0b1));
   *             std::unique_ptr<float[]> row(new float[maskW * numSubpixels]);
   *             for (int col = 0; col < maskW * numSubpixels; ++col) {
   *                 // Compute distance to rect left in subpixel units
   *                 float ldiff = numSubpixels * pad - (col + 0.5f);
   *                 float rdiff = ldiff + w;
   *                 row[col] = def_integral_approx(ldiff, rdiff);
   *             }
   *             // y-pass
   *             SkBitmap bmp;
   *             bmp.allocPixels(SkImageInfo::MakeA8(maskW, maskH));
   *             std::unique_ptr<float[]> accums(new float[maskW]);
   *             const float accumScale = 1.f / (numSubpixels * numSubpixels);
   *             for (int y = 0; y < maskH; ++y) {
   *                 // Initialize subpixel accumulation buffer for this row.
   *                 std::fill_n(accums.get(), maskW, 0);
   *                 for (int ys = 0; ys < numSubpixels; ++ys) {
   *                     // At each subpixel we want to integrate over the kernel centered at the
   *                     // subpixel multiplied by the x-pass. The x-pass is zero above and below the
   *                     // rect and constant valued from rect top to rect bottom. So we can get the
   *                     // integral of just the kernel from rect top to rect bottom and multiply by
   *                     // the single x-pass value from our precomputed row.
   *                     float tdiff = numSubpixels * pad - (y * numSubpixels + ys + 0.5f);
   *                     float bdiff = tdiff + h;
   *                     auto integral = def_integral_approx(tdiff, bdiff);
   *                     for (int x = 0; x < maskW; ++x) {
   *                         for (int xs = 0; xs < numSubpixels; ++xs) {
   *                             int rowIdx = x * numSubpixels + xs;
   *                             accums[x] += integral * row[rowIdx];
   *                         }
   *                     }
   *                 }
   *                 for (int x = 0; x < maskW; ++x) {
   *                     auto result = accums[x] * accumScale;
   *                     *bmp.getAddr8(x, y) = SkToU8(sk_float_round2int(255.f * result));
   *                 }
   *             }
   *             return bmp.asImage();
   *         };
   *
   *         // Number of times to subsample (in both X and Y). If fRecalcMasksForAnimation is true
   *         // then we're animating, don't subsample as much to keep fps higher.
   *         const int numSubpixels = fRecalcMasksForAnimation ? 2 : 8;
   *
   *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
   *             auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
   *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
   *                 auto h = kSizes[heightIdx];
   *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
   *                     auto w = kSizes[widthIdx];
   *                     fReferenceMasks[sigmaIdx][heightIdx][widthIdx] =
   *                             create_reference_mask(w, h, sigma, numSubpixels);
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  private fun prepareReferenceMasks() {
    TODO("Implement prepareReferenceMasks")
  }

  /**
   * C++ original:
   * ```cpp
   * void prepareActualMasks(SkCanvas* canvas) {
   *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
   *             auto sigma = kSigmas[sigmaIdx] + fSigmaAnimationBoost;
   *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
   *                 auto h = kSizes[heightIdx];
   *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
   *                     auto w = kSizes[widthIdx];
   *                     auto pad = PadForSigma(sigma);
   *                     auto ii = SkImageInfo::MakeA8(w + 2 * pad, h + 2 * pad);
   *                     auto surf = canvas->makeSurface(ii);
   *                     if (!surf) {
   *                         // Some GPUs don't have renderable A8 :(
   *                         surf = canvas->makeSurface(ii.makeColorType(kRGBA_8888_SkColorType));
   *                         if (!surf) {
   *                             return;
   *                         }
   *                     }
   *                     auto rect = SkRect::MakeXYWH(pad, pad, w, h);
   *                     SkPaint paint;
   *                     // Color doesn't matter if we're rendering to A8 but does if we promoted to
   *                     // RGBA above.
   *                     paint.setColor(SK_ColorWHITE);
   *                     paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma));
   *                     surf->getCanvas()->drawRect(rect, paint);
   *                     fActualMasks[sigmaIdx][heightIdx][widthIdx] = surf->makeImageSnapshot();
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  private fun prepareActualMasks(canvas: SkCanvas?) {
    TODO("Implement prepareActualMasks")
  }

  /**
   * C++ original:
   * ```cpp
   * void prepareMaskDifferences(SkCanvas* canvas) {
   *         for (size_t sigmaIdx = 0; sigmaIdx < kNumSigmas; ++sigmaIdx) {
   *             for (size_t heightIdx = 0; heightIdx < kNumSizes; ++heightIdx) {
   *                 for (size_t widthIdx = 0; widthIdx < kNumSizes; ++widthIdx) {
   *                     const auto& r =  fReferenceMasks[sigmaIdx][heightIdx][widthIdx];
   *                     const auto& a =     fActualMasks[sigmaIdx][heightIdx][widthIdx];
   *                     auto& d       = fMaskDifferences[sigmaIdx][heightIdx][widthIdx];
   *                     // The actual image might not be present if we're on an abandoned GrContext.
   *                     if (!a) {
   *                         d.reset();
   *                         continue;
   *                     }
   *                     SkASSERT(r->width() == a->width());
   *                     SkASSERT(r->height() == a->height());
   *                     auto ii = SkImageInfo::Make(r->width(), r->height(),
   *                                                 kRGBA_8888_SkColorType, kPremul_SkAlphaType);
   *                     auto surf = canvas->makeSurface(ii);
   *                     if (!surf) {
   *                         return;
   *                     }
   *                     // We visualize the difference by turning both the alpha masks into opaque green
   *                     // images (where alpha becomes the green channel) and then perform a
   *                     // SkBlendMode::kDifference between them.
   *                     SkPaint filterPaint;
   *                     filterPaint.setColor(SK_ColorWHITE);
   *                     // Actually 8 * alpha becomes green to really highlight differences.
   *                     static constexpr float kGreenifyM[] = {0, 0, 0, 0, 0,
   *                                                            0, 0, 0, 8, 0,
   *                                                            0, 0, 0, 0, 0,
   *                                                            0, 0, 0, 0, 1};
   *                     auto greenifyCF = SkColorFilters::Matrix(kGreenifyM);
   *                     SkPaint paint;
   *                     paint.setBlendMode(SkBlendMode::kSrc);
   *                     paint.setColorFilter(std::move(greenifyCF));
   *                     surf->getCanvas()->drawImage(a, 0, 0, SkSamplingOptions(), &paint);
   *                     paint.setBlendMode(SkBlendMode::kDifference);
   *                     surf->getCanvas()->drawImage(r, 0, 0, SkSamplingOptions(), &paint);
   *                     d = surf->makeImageSnapshot();
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  private fun prepareMaskDifferences(canvas: SkCanvas?) {
    TODO("Implement prepareMaskDifferences")
  }

  public companion object {
    private val kSizes: IntArray = TODO("Initialize kSizes")

    private val kSigmas: FloatArray = TODO("Initialize kSigmas")

    private val kNumSizes: ULong = TODO("Initialize kNumSizes")

    private val kNumSigmas: ULong = TODO("Initialize kNumSigmas")

    /**
     * C++ original:
     * ```cpp
     * static int PadForSigma(float sigma) { return sk_float_ceil2int(4 * sigma); }
     * ```
     */
    private fun padForSigma(sigma: Float): Int {
      TODO("Implement padForSigma")
    }
  }
}
