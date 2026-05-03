package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.core.Backend
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.SkSpecialImage
import org.skia.core.TArray
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder
import org.skia.math.SkIPoint
import org.skia.math.SkISize
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class TestRunner {
 *     static constexpr SkColorType kColorType = kRGBA_8888_SkColorType;
 *     using ResolveMethod = FilterResultImageResolver::Method;
 * public:
 *     // Raster-backed TestRunner
 *     TestRunner(skiatest::Reporter* reporter)
 *             : fReporter(reporter)
 *             , fBackend(skif::MakeRasterBackend(/*surfaceProps=*/{}, kColorType)) {}
 *
 *     // Ganesh-backed TestRunner
 * #if defined(SK_GANESH)
 *     TestRunner(skiatest::Reporter* reporter, GrDirectContext* context)
 *             : fReporter(reporter)
 *             , fDirectContext(context)
 *             , fBackend(skif::MakeGaneshBackend(sk_ref_sp(context),
 *                                                kTopLeft_GrSurfaceOrigin,
 *                                                /*surfaceProps=*/{},
 *                                                kColorType)) {}
 * #endif
 *
 *     // Graphite-backed TestRunner
 * #if defined(SK_GRAPHITE)
 *     TestRunner(skiatest::Reporter* reporter, skgpu::graphite::Recorder* recorder)
 *             : fReporter(reporter)
 *             , fRecorder(recorder)
 *             , fBackend(skif::MakeGraphiteBackend(recorder, /*surfaceProps=*/{}, kColorType)) {}
 * #endif
 *
 *     // Let TestRunner be passed in to places that take a Reporter* or to REPORTER_ASSERT etc.
 *     operator skiatest::Reporter*() const { return fReporter; }
 *     skiatest::Reporter* operator->() const { return fReporter; }
 *
 *     skif::Backend* backend() const { return fBackend.get(); }
 *     sk_sp<skif::Backend> refBackend() const { return fBackend; }
 *
 *     bool compareImages(const skif::Context& ctx,
 *                        SkSpecialImage* expectedImage,
 *                        SkIPoint expectedOrigin,
 *                        const FilterResult& actual,
 *                        float allowedPercentImageDiff,
 *                        float allowedRGBTolerance,
 *                        int transparentCheckBorderPadding) {
 *         if (!expectedImage) {
 *             // For pathological desired outputs, we can't actually produce an expected image so
 *             // just carry on w/o validating.
 *             return true;
 *         }
 *
 *         SkBitmap expectedBM = this->readPixels(expectedImage);
 *
 *         auto compare = [&](ResolveMethod m) {
 *             return this->compareImages(ctx, expectedBM, expectedOrigin, actual, m,
 *                                        allowedPercentImageDiff,
 *                                        allowedRGBTolerance,
 *                                        transparentCheckBorderPadding);
 *         };
 *
 *         // Resolve actual using all 4 methods to ensure they are approximately equal to the expected
 *         // (which is used as a proxy for being approximately equal to each other).
 *         return compare(ResolveMethod::kImageAndOffset) &&
 *                compare(ResolveMethod::kDrawToCanvas) &&
 *                compare(ResolveMethod::kShader) &&
 *                compare(ResolveMethod::kClippedShader);
 *     }
 *
 *     bool validateOptimizedImage(const skif::Context& ctx, const FilterResult& actual) {
 *         FilterResultImageResolver expectedResolver{ResolveMethod::kStrictShader};
 *         auto [expectedImage, expectedOrigin] = expectedResolver.resolve(ctx, actual);
 *         SkBitmap expectedBM = this->readPixels(expectedImage.get());
 *         return this->compareImages(ctx, expectedBM, expectedOrigin, actual,
 *                                    ResolveMethod::kImageAndOffset,
 *                                    /*allowedPercentImageDiff=*/0.0f,
 *                                    /*allowedRGBTolerance=*/kDefaultRGBTolerance,
 *                                    /*transparentCheckBorderPadding=*/0);
 *     }
 *
 *     sk_sp<SkSpecialImage> createSourceImage(SkISize size, sk_sp<SkColorSpace> colorSpace) {
 *         sk_sp<SkDevice> sourceSurface = fBackend->makeDevice(size, std::move(colorSpace));
 *
 *         const SkColor colors[] = { SK_ColorMAGENTA,
 *                                    SK_ColorRED,
 *                                    SK_ColorYELLOW,
 *                                    SK_ColorGREEN,
 *                                    SK_ColorCYAN,
 *                                    SK_ColorBLUE };
 *         SkMatrix rotation = SkMatrix::RotateDeg(15.f, {size.width() / 2.f,
 *                                                        size.height() / 2.f});
 *
 *         SkCanvas canvas{sourceSurface};
 *         canvas.clear(SK_ColorBLACK);
 *         canvas.concat(rotation);
 *
 *         int color = 0;
 *         SkRect coverBounds;
 *         SkRect dstBounds = SkRect::Make(canvas.imageInfo().bounds());
 *         SkAssertResult(SkMatrixPriv::InverseMapRect(rotation, &coverBounds, dstBounds));
 *
 *         float sz = size.width() <= 16.f || size.height() <= 16.f ? 2.f : 8.f;
 *         for (float y = coverBounds.fTop; y < coverBounds.fBottom; y += sz) {
 *             for (float x = coverBounds.fLeft; x < coverBounds.fRight; x += sz) {
 *                 SkPaint p;
 *                 p.setColor(colors[(color++) % std::size(colors)]);
 *                 canvas.drawRect(SkRect::MakeXYWH(x, y, sz, sz), p);
 *             }
 *         }
 *
 *         return sourceSurface->snapSpecial(SkIRect::MakeSize(size));
 *     }
 *
 * private:
 *
 *     bool compareImages(const skif::Context& ctx, const SkBitmap& expected, SkIPoint expectedOrigin,
 *                        const FilterResult& actual, ResolveMethod method,
 *                        float allowedPercentImageDiff, float allowedRGBTolerance,
 *                        int transparentCheckBorderPadding) {
 *         FilterResultImageResolver resolver{method};
 *         auto [actualImage, actualOrigin] = resolver.resolve(ctx, actual);
 *
 *         SkBitmap actualBM = this->readPixels(actualImage.get()); // empty if actualImage is null
 *         TArray<SkIPoint> badPixels;
 *         if (!this->compareBitmaps(expected, expectedOrigin, actualBM, actualOrigin,
 *                                   allowedPercentImageDiff, allowedRGBTolerance,
 *                                   transparentCheckBorderPadding, &badPixels)) {
 *             if (!fLoggedErrorImage) {
 *                 SkDebugf("FilterResult comparison failed for method %s\n", resolver.methodName());
 *                 this->logBitmaps(expected, actualBM, badPixels);
 *                 fLoggedErrorImage = true;
 *             }
 *             return false;
 *         } else if (kLogAllBitmaps) {
 *             this->logBitmaps(expected, actualBM, badPixels);
 *         }
 *         return true;
 *     }
 *
 *
 *     bool compareBitmaps(const SkBitmap& expected,
 *                         SkIPoint expectedOrigin,
 *                         const SkBitmap& actual,
 *                         SkIPoint actualOrigin,
 *                         float allowedPercentImageDiff,
 *                         float allowedRGBTolerance,
 *                         int transparentCheckBorderPadding,
 *                         TArray<SkIPoint>* badPixels) {
 *         SkIRect excludeTransparentCheck; // region in expectedBM that can be non-transparent
 *         if (actual.empty()) {
 *             // A null image in a FilterResult is equivalent to transparent black, so we should
 *             // expect the contents of 'expectedImage' to be transparent black.
 *             excludeTransparentCheck = SkIRect::MakeEmpty();
 *         } else {
 *             // The actual image bounds should be contained in the expected image's bounds.
 *             SkIRect actualBounds = SkIRect::MakeXYWH(actualOrigin.x(), actualOrigin.y(),
 *                                                      actual.width(), actual.height());
 *             SkIRect expectedBounds = SkIRect::MakeXYWH(expectedOrigin.x(), expectedOrigin.y(),
 *                                                        expected.width(), expected.height());
 *             const bool contained = expectedBounds.contains(actualBounds);
 *             REPORTER_ASSERT(fReporter, contained,
 *                     "actual image [%d %d %d %d] not contained within expected [%d %d %d %d]",
 *                     actualBounds.fLeft, actualBounds.fTop,
 *                     actualBounds.fRight, actualBounds.fBottom,
 *                     expectedBounds.fLeft, expectedBounds.fTop,
 *                     expectedBounds.fRight, expectedBounds.fBottom);
 *             if (!contained) {
 *                 return false;
 *             }
 *
 *             // The actual pixels should match fairly closely with the expected, allowing for minor
 *             // differences from consolidating actions into a single render, etc.
 *             int errorCount = 0;
 *             float errorDelta = 0.f;
 *             SkIPoint offset = actualOrigin - expectedOrigin;
 *             for (int y = 0; y < actual.height(); ++y) {
 *                 for (int x = 0; x < actual.width(); ++x) {
 *                     SkIPoint ep = {x + offset.x(), y + offset.y()};
 *                     SkColor4f expectedColor = expected.getColor4f(ep.fX, ep.fY);
 *                     SkColor4f actualColor = actual.getColor4f(x, y);
 *                     if (actualColor != expectedColor) {
 *                         const float delta = this->approxColorDelta(
 *                                 this->boxFilter(actual, x, y),
 *                                 this->boxFilter(expected, ep.fX, ep.fY));
 *                         if (delta > allowedRGBTolerance) {
 *                             errorDelta += delta;
 *                             badPixels->push_back(ep);
 *                             errorCount++;
 *                         }
 *                     }
 *                 }
 *             }
 *
 *             const int totalCount = expected.width() * expected.height();
 *             const float percentError = 100.f * errorCount / (float) totalCount;
 *             const bool approxMatch = percentError <= allowedPercentImageDiff;
 *
 *             REPORTER_ASSERT(fReporter, approxMatch,
 *                             "%d pixels were too different from %d total (%f %% vs. %f %%), "
 *                             "average delta %f (vs. %f allowed)",
 *                             errorCount, totalCount, percentError, allowedPercentImageDiff,
 *                             errorDelta / errorCount, allowedRGBTolerance);
 *             if (!approxMatch) {
 *                 return false;
 *             }
 *
 *             // The expected pixels outside of the actual bounds should be transparent, otherwise
 *             // the actual image is not returning enough data.
 *             excludeTransparentCheck = actualBounds.makeOffset(-expectedOrigin);
 *             // Add per-test padding to the exclusion, which is used when there is upscaling in the
 *             // expected image that bleeds beyond the layer bounds, but is hard to enforce in the
 *             // simplified expectation rendering.
 *             excludeTransparentCheck.outset(transparentCheckBorderPadding,
 *                                            transparentCheckBorderPadding);
 *         }
 *
 *         int badTransparencyCount = 0;
 *         for (int y = 0; y < expected.height(); ++y) {
 *             for (int x = 0; x < expected.width(); ++x) {
 *                 if (!excludeTransparentCheck.isEmpty() && excludeTransparentCheck.contains(x, y)) {
 *                     continue;
 *                 }
 *
 *                 // If we are on the edge of the transparency exclusion bounds, allow pixels to be
 *                 // up to 2 off to account for sloppy GPU rendering (seen on some Android devices).
 *                 // This is still visually "transparent" and definitely make sure that
 *                 // off-transparency does not extend across the entire surface (tolerance = 0).
 *                 const bool onEdge = !excludeTransparentCheck.isEmpty() &&
 *                                     excludeTransparentCheck.makeOutset(1, 1).contains(x, y);
 *                 const float delta = this->approxColorDelta(expected.getColor4f(x, y),
 *                                                            SkColors::kTransparent);
 *                 if (delta > (onEdge ? kAATolerance : 0.f)) {
 *                     badPixels->push_back({x, y});
 *                     badTransparencyCount++;
 *                 }
 *             }
 *         }
 *
 *         REPORTER_ASSERT(fReporter, badTransparencyCount == 0, "Unexpected non-transparent pixels");
 *         return badTransparencyCount == 0;
 *     }
 *
 *     float approxColorDelta(const SkColor4f& a, const SkColor4f& b) const {
 *         SkPMColor4f apm = a.premul();
 *         SkPMColor4f bpm = b.premul();
 *         // Calculate red-mean, a lowcost approximation of color difference that gives reasonable
 *         // results for the types of acceptable differences resulting from collapsing compatible
 *         // SkSamplingOptions or slightly different AA on shape boundaries.
 *         // See https://www.compuphase.com/cmetric.htm
 *         float r = (apm.fR + bpm.fR) / 2.f;
 *         float dr = (apm.fR - bpm.fR);
 *         float dg = (apm.fG - bpm.fG);
 *         float db = (apm.fB - bpm.fB);
 *         return sqrt((2.f + r)*dr*dr + 4.f*dg*dg + (2.f + (1.f - r))*db*db);
 *     }
 *
 *     SkColor4f boxFilter(const SkBitmap& bm, int x, int y) const {
 *         static constexpr int kKernelOffset = kKernelSize / 2;
 *         SkPMColor4f sum = {0.f, 0.f, 0.f, 0.f};
 *         float netWeight = 0.f;
 *         for (int sy = y - kKernelOffset; sy <= y + kKernelOffset; ++sy) {
 *             for (int sx = x - kKernelOffset; sx <= x + kKernelOffset; ++sx) {
 *                 float weight = kFuzzyKernel[sy - y + kKernelOffset][sx - x + kKernelOffset];
 *
 *                 if (sx < 0 || sx >= bm.width() || sy < 0 || sy >= bm.height()) {
 *                     // Treat outside image as transparent black, this is necessary to get
 *                     // consistent comparisons between expected and actual images where the actual
 *                     // is cropped as tightly as possible.
 *                     netWeight += weight;
 *                     continue;
 *                 }
 *
 *                 SkPMColor4f c = bm.getColor4f(sx, sy).premul() * weight;
 *                 sum.fR += c.fR;
 *                 sum.fG += c.fG;
 *                 sum.fB += c.fB;
 *                 sum.fA += c.fA;
 *                 netWeight += weight;
 *             }
 *         }
 *         SkASSERT(netWeight > 0.f);
 *         return sum.unpremul() * (1.f / netWeight);
 *     }
 *
 *     SkBitmap readPixels(const SkSpecialImage* specialImage) const {
 *         if (!specialImage) {
 *             return SkBitmap(); // an empty bitmap
 *         }
 *
 *         [[maybe_unused]] int srcX = specialImage->subset().fLeft;
 *         [[maybe_unused]] int srcY = specialImage->subset().fTop;
 *         SkImageInfo ii = SkImageInfo::Make(specialImage->dimensions(),
 *                                            specialImage->colorInfo());
 *         SkBitmap bm;
 *         bm.allocPixels(ii);
 * #if defined(SK_GANESH)
 *         if (fDirectContext) {
 *             // Ganesh backed, just use the SkImage::readPixels API
 *             SkASSERT(specialImage->isGaneshBacked());
 *             sk_sp<SkImage> image = specialImage->asImage();
 *             SkAssertResult(image->readPixels(fDirectContext, bm.pixmap(), srcX, srcY));
 *         } else
 * #endif
 * #if defined(SK_GRAPHITE)
 *         if (fRecorder) {
 *             // Graphite backed, so use the private testing-only synchronous API
 *             SkASSERT(specialImage->isGraphiteBacked());
 *             auto view = skgpu::graphite::AsView(specialImage->asImage());
 *             auto proxyII = ii.makeWH(view.width(), view.height());
 *             SkAssertResult(fRecorder->priv().context()->priv().readPixels(
 *                     bm.pixmap(), view.proxy(), proxyII, srcX, srcY));
 *         } else
 * #endif
 *         {
 *             // Assume it's raster backed, so use AsBitmap directly
 *             SkAssertResult(SkSpecialImages::AsBitmap(specialImage, &bm));
 *         }
 *
 *         return bm;
 *     }
 *
 *     void logBitmaps(const SkBitmap& expected,
 *                     const SkBitmap& actual,
 *                     const TArray<SkIPoint>& badPixels) {
 *         SkString expectedURL;
 *         ToolUtils::BitmapToBase64DataURI(expected, &expectedURL);
 *         SkDebugf("Expected:\n%s\n\n", expectedURL.c_str());
 *
 *         if (!actual.empty()) {
 *             SkString actualURL;
 *             ToolUtils::BitmapToBase64DataURI(actual, &actualURL);
 *             SkDebugf("Actual:\n%s\n\n", actualURL.c_str());
 *         } else {
 *             SkDebugf("Actual: null (fully transparent)\n\n");
 *         }
 *
 *         if (!badPixels.empty()) {
 *             SkBitmap error = expected;
 *             error.allocPixels();
 *             SkAssertResult(expected.readPixels(error.pixmap()));
 *             for (auto p : badPixels) {
 *                 error.erase(SkColors::kRed, SkIRect::MakeXYWH(p.fX, p.fY, 1, 1));
 *             }
 *             SkString markedURL;
 *             ToolUtils::BitmapToBase64DataURI(error, &markedURL);
 *             SkDebugf("Errors:\n%s\n\n", markedURL.c_str());
 *         }
 *     }
 *
 *     skiatest::Reporter* fReporter;
 * #if defined(SK_GANESH)
 *     GrDirectContext* fDirectContext = nullptr;
 * #endif
 * #if defined(SK_GRAPHITE)
 *     skgpu::graphite::Recorder* fRecorder = nullptr;
 * #endif
 *
 *     sk_sp<skif::Backend> fBackend;
 *
 *     bool fLoggedErrorImage = false; // only do this once per test runner
 * }
 * ```
 */
public data class TestRunner public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkColorType kColorType = kRGBA_8888_SkColorType
   * ```
   */
  private var fReporter: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  private var fRecorder: Recorder?,
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* fRecorder = nullptr
   * ```
   */
  private var fBackend: SkSp<Backend>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<skif::Backend> fBackend
   * ```
   */
  private var fLoggedErrorImage: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* operator->() const { return fReporter; }
   * ```
   */
  public fun `get`(): Reporter {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::Backend* backend() const { return fBackend.get(); }
   * ```
   */
  public fun backend(): Backend {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skif::Backend> refBackend() const { return fBackend; }
   * ```
   */
  public fun refBackend(): SkSp<Backend> {
    TODO("Implement refBackend")
  }

  /**
   * C++ original:
   * ```cpp
   * bool compareImages(const skif::Context& ctx,
   *                        SkSpecialImage* expectedImage,
   *                        SkIPoint expectedOrigin,
   *                        const FilterResult& actual,
   *                        float allowedPercentImageDiff,
   *                        float allowedRGBTolerance,
   *                        int transparentCheckBorderPadding) {
   *         if (!expectedImage) {
   *             // For pathological desired outputs, we can't actually produce an expected image so
   *             // just carry on w/o validating.
   *             return true;
   *         }
   *
   *         SkBitmap expectedBM = this->readPixels(expectedImage);
   *
   *         auto compare = [&](ResolveMethod m) {
   *             return this->compareImages(ctx, expectedBM, expectedOrigin, actual, m,
   *                                        allowedPercentImageDiff,
   *                                        allowedRGBTolerance,
   *                                        transparentCheckBorderPadding);
   *         };
   *
   *         // Resolve actual using all 4 methods to ensure they are approximately equal to the expected
   *         // (which is used as a proxy for being approximately equal to each other).
   *         return compare(ResolveMethod::kImageAndOffset) &&
   *                compare(ResolveMethod::kDrawToCanvas) &&
   *                compare(ResolveMethod::kShader) &&
   *                compare(ResolveMethod::kClippedShader);
   *     }
   * ```
   */
  public fun compareImages(
    ctx: Context,
    expectedImage: SkSpecialImage?,
    expectedOrigin: SkIPoint,
    `actual`: FilterResult,
    allowedPercentImageDiff: Float,
    allowedRGBTolerance: Float,
    transparentCheckBorderPadding: Int,
  ): Boolean {
    TODO("Implement compareImages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool validateOptimizedImage(const skif::Context& ctx, const FilterResult& actual) {
   *         FilterResultImageResolver expectedResolver{ResolveMethod::kStrictShader};
   *         auto [expectedImage, expectedOrigin] = expectedResolver.resolve(ctx, actual);
   *         SkBitmap expectedBM = this->readPixels(expectedImage.get());
   *         return this->compareImages(ctx, expectedBM, expectedOrigin, actual,
   *                                    ResolveMethod::kImageAndOffset,
   *                                    /*allowedPercentImageDiff=*/0.0f,
   *                                    /*allowedRGBTolerance=*/kDefaultRGBTolerance,
   *                                    /*transparentCheckBorderPadding=*/0);
   *     }
   * ```
   */
  public fun validateOptimizedImage(ctx: Context, `actual`: FilterResult): Boolean {
    TODO("Implement validateOptimizedImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> createSourceImage(SkISize size, sk_sp<SkColorSpace> colorSpace) {
   *         sk_sp<SkDevice> sourceSurface = fBackend->makeDevice(size, std::move(colorSpace));
   *
   *         const SkColor colors[] = { SK_ColorMAGENTA,
   *                                    SK_ColorRED,
   *                                    SK_ColorYELLOW,
   *                                    SK_ColorGREEN,
   *                                    SK_ColorCYAN,
   *                                    SK_ColorBLUE };
   *         SkMatrix rotation = SkMatrix::RotateDeg(15.f, {size.width() / 2.f,
   *                                                        size.height() / 2.f});
   *
   *         SkCanvas canvas{sourceSurface};
   *         canvas.clear(SK_ColorBLACK);
   *         canvas.concat(rotation);
   *
   *         int color = 0;
   *         SkRect coverBounds;
   *         SkRect dstBounds = SkRect::Make(canvas.imageInfo().bounds());
   *         SkAssertResult(SkMatrixPriv::InverseMapRect(rotation, &coverBounds, dstBounds));
   *
   *         float sz = size.width() <= 16.f || size.height() <= 16.f ? 2.f : 8.f;
   *         for (float y = coverBounds.fTop; y < coverBounds.fBottom; y += sz) {
   *             for (float x = coverBounds.fLeft; x < coverBounds.fRight; x += sz) {
   *                 SkPaint p;
   *                 p.setColor(colors[(color++) % std::size(colors)]);
   *                 canvas.drawRect(SkRect::MakeXYWH(x, y, sz, sz), p);
   *             }
   *         }
   *
   *         return sourceSurface->snapSpecial(SkIRect::MakeSize(size));
   *     }
   * ```
   */
  public fun createSourceImage(size: SkISize, colorSpace: SkSp<SkColorSpace>): SkSp<SkSpecialImage> {
    TODO("Implement createSourceImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool compareImages(const skif::Context& ctx, const SkBitmap& expected, SkIPoint expectedOrigin,
   *                        const FilterResult& actual, ResolveMethod method,
   *                        float allowedPercentImageDiff, float allowedRGBTolerance,
   *                        int transparentCheckBorderPadding) {
   *         FilterResultImageResolver resolver{method};
   *         auto [actualImage, actualOrigin] = resolver.resolve(ctx, actual);
   *
   *         SkBitmap actualBM = this->readPixels(actualImage.get()); // empty if actualImage is null
   *         TArray<SkIPoint> badPixels;
   *         if (!this->compareBitmaps(expected, expectedOrigin, actualBM, actualOrigin,
   *                                   allowedPercentImageDiff, allowedRGBTolerance,
   *                                   transparentCheckBorderPadding, &badPixels)) {
   *             if (!fLoggedErrorImage) {
   *                 SkDebugf("FilterResult comparison failed for method %s\n", resolver.methodName());
   *                 this->logBitmaps(expected, actualBM, badPixels);
   *                 fLoggedErrorImage = true;
   *             }
   *             return false;
   *         } else if (kLogAllBitmaps) {
   *             this->logBitmaps(expected, actualBM, badPixels);
   *         }
   *         return true;
   *     }
   * ```
   */
  private fun compareImages(
    ctx: Context,
    expected: SkBitmap,
    expectedOrigin: SkIPoint,
    `actual`: FilterResult,
    method: TestRunnerResolveMethod,
    allowedPercentImageDiff: Float,
    allowedRGBTolerance: Float,
    transparentCheckBorderPadding: Int,
  ): Boolean {
    TODO("Implement compareImages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool compareBitmaps(const SkBitmap& expected,
   *                         SkIPoint expectedOrigin,
   *                         const SkBitmap& actual,
   *                         SkIPoint actualOrigin,
   *                         float allowedPercentImageDiff,
   *                         float allowedRGBTolerance,
   *                         int transparentCheckBorderPadding,
   *                         TArray<SkIPoint>* badPixels) {
   *         SkIRect excludeTransparentCheck; // region in expectedBM that can be non-transparent
   *         if (actual.empty()) {
   *             // A null image in a FilterResult is equivalent to transparent black, so we should
   *             // expect the contents of 'expectedImage' to be transparent black.
   *             excludeTransparentCheck = SkIRect::MakeEmpty();
   *         } else {
   *             // The actual image bounds should be contained in the expected image's bounds.
   *             SkIRect actualBounds = SkIRect::MakeXYWH(actualOrigin.x(), actualOrigin.y(),
   *                                                      actual.width(), actual.height());
   *             SkIRect expectedBounds = SkIRect::MakeXYWH(expectedOrigin.x(), expectedOrigin.y(),
   *                                                        expected.width(), expected.height());
   *             const bool contained = expectedBounds.contains(actualBounds);
   *             REPORTER_ASSERT(fReporter, contained,
   *                     "actual image [%d %d %d %d] not contained within expected [%d %d %d %d]",
   *                     actualBounds.fLeft, actualBounds.fTop,
   *                     actualBounds.fRight, actualBounds.fBottom,
   *                     expectedBounds.fLeft, expectedBounds.fTop,
   *                     expectedBounds.fRight, expectedBounds.fBottom);
   *             if (!contained) {
   *                 return false;
   *             }
   *
   *             // The actual pixels should match fairly closely with the expected, allowing for minor
   *             // differences from consolidating actions into a single render, etc.
   *             int errorCount = 0;
   *             float errorDelta = 0.f;
   *             SkIPoint offset = actualOrigin - expectedOrigin;
   *             for (int y = 0; y < actual.height(); ++y) {
   *                 for (int x = 0; x < actual.width(); ++x) {
   *                     SkIPoint ep = {x + offset.x(), y + offset.y()};
   *                     SkColor4f expectedColor = expected.getColor4f(ep.fX, ep.fY);
   *                     SkColor4f actualColor = actual.getColor4f(x, y);
   *                     if (actualColor != expectedColor) {
   *                         const float delta = this->approxColorDelta(
   *                                 this->boxFilter(actual, x, y),
   *                                 this->boxFilter(expected, ep.fX, ep.fY));
   *                         if (delta > allowedRGBTolerance) {
   *                             errorDelta += delta;
   *                             badPixels->push_back(ep);
   *                             errorCount++;
   *                         }
   *                     }
   *                 }
   *             }
   *
   *             const int totalCount = expected.width() * expected.height();
   *             const float percentError = 100.f * errorCount / (float) totalCount;
   *             const bool approxMatch = percentError <= allowedPercentImageDiff;
   *
   *             REPORTER_ASSERT(fReporter, approxMatch,
   *                             "%d pixels were too different from %d total (%f %% vs. %f %%), "
   *                             "average delta %f (vs. %f allowed)",
   *                             errorCount, totalCount, percentError, allowedPercentImageDiff,
   *                             errorDelta / errorCount, allowedRGBTolerance);
   *             if (!approxMatch) {
   *                 return false;
   *             }
   *
   *             // The expected pixels outside of the actual bounds should be transparent, otherwise
   *             // the actual image is not returning enough data.
   *             excludeTransparentCheck = actualBounds.makeOffset(-expectedOrigin);
   *             // Add per-test padding to the exclusion, which is used when there is upscaling in the
   *             // expected image that bleeds beyond the layer bounds, but is hard to enforce in the
   *             // simplified expectation rendering.
   *             excludeTransparentCheck.outset(transparentCheckBorderPadding,
   *                                            transparentCheckBorderPadding);
   *         }
   *
   *         int badTransparencyCount = 0;
   *         for (int y = 0; y < expected.height(); ++y) {
   *             for (int x = 0; x < expected.width(); ++x) {
   *                 if (!excludeTransparentCheck.isEmpty() && excludeTransparentCheck.contains(x, y)) {
   *                     continue;
   *                 }
   *
   *                 // If we are on the edge of the transparency exclusion bounds, allow pixels to be
   *                 // up to 2 off to account for sloppy GPU rendering (seen on some Android devices).
   *                 // This is still visually "transparent" and definitely make sure that
   *                 // off-transparency does not extend across the entire surface (tolerance = 0).
   *                 const bool onEdge = !excludeTransparentCheck.isEmpty() &&
   *                                     excludeTransparentCheck.makeOutset(1, 1).contains(x, y);
   *                 const float delta = this->approxColorDelta(expected.getColor4f(x, y),
   *                                                            SkColors::kTransparent);
   *                 if (delta > (onEdge ? kAATolerance : 0.f)) {
   *                     badPixels->push_back({x, y});
   *                     badTransparencyCount++;
   *                 }
   *             }
   *         }
   *
   *         REPORTER_ASSERT(fReporter, badTransparencyCount == 0, "Unexpected non-transparent pixels");
   *         return badTransparencyCount == 0;
   *     }
   * ```
   */
  private fun compareBitmaps(
    expected: SkBitmap,
    expectedOrigin: SkIPoint,
    `actual`: SkBitmap,
    actualOrigin: SkIPoint,
    allowedPercentImageDiff: Float,
    allowedRGBTolerance: Float,
    transparentCheckBorderPadding: Int,
    badPixels: TArray<SkIPoint>?,
  ): Boolean {
    TODO("Implement compareBitmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * float approxColorDelta(const SkColor4f& a, const SkColor4f& b) const {
   *         SkPMColor4f apm = a.premul();
   *         SkPMColor4f bpm = b.premul();
   *         // Calculate red-mean, a lowcost approximation of color difference that gives reasonable
   *         // results for the types of acceptable differences resulting from collapsing compatible
   *         // SkSamplingOptions or slightly different AA on shape boundaries.
   *         // See https://www.compuphase.com/cmetric.htm
   *         float r = (apm.fR + bpm.fR) / 2.f;
   *         float dr = (apm.fR - bpm.fR);
   *         float dg = (apm.fG - bpm.fG);
   *         float db = (apm.fB - bpm.fB);
   *         return sqrt((2.f + r)*dr*dr + 4.f*dg*dg + (2.f + (1.f - r))*db*db);
   *     }
   * ```
   */
  private fun approxColorDelta(a: SkColor4f, b: SkColor4f): Float {
    TODO("Implement approxColorDelta")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f boxFilter(const SkBitmap& bm, int x, int y) const {
   *         static constexpr int kKernelOffset = kKernelSize / 2;
   *         SkPMColor4f sum = {0.f, 0.f, 0.f, 0.f};
   *         float netWeight = 0.f;
   *         for (int sy = y - kKernelOffset; sy <= y + kKernelOffset; ++sy) {
   *             for (int sx = x - kKernelOffset; sx <= x + kKernelOffset; ++sx) {
   *                 float weight = kFuzzyKernel[sy - y + kKernelOffset][sx - x + kKernelOffset];
   *
   *                 if (sx < 0 || sx >= bm.width() || sy < 0 || sy >= bm.height()) {
   *                     // Treat outside image as transparent black, this is necessary to get
   *                     // consistent comparisons between expected and actual images where the actual
   *                     // is cropped as tightly as possible.
   *                     netWeight += weight;
   *                     continue;
   *                 }
   *
   *                 SkPMColor4f c = bm.getColor4f(sx, sy).premul() * weight;
   *                 sum.fR += c.fR;
   *                 sum.fG += c.fG;
   *                 sum.fB += c.fB;
   *                 sum.fA += c.fA;
   *                 netWeight += weight;
   *             }
   *         }
   *         SkASSERT(netWeight > 0.f);
   *         return sum.unpremul() * (1.f / netWeight);
   *     }
   * ```
   */
  private fun boxFilter(
    bm: SkBitmap,
    x: Int,
    y: Int,
  ): SkColor4f {
    TODO("Implement boxFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmap readPixels(const SkSpecialImage* specialImage) const {
   *         if (!specialImage) {
   *             return SkBitmap(); // an empty bitmap
   *         }
   *
   *         [[maybe_unused]] int srcX = specialImage->subset().fLeft;
   *         [[maybe_unused]] int srcY = specialImage->subset().fTop;
   *         SkImageInfo ii = SkImageInfo::Make(specialImage->dimensions(),
   *                                            specialImage->colorInfo());
   *         SkBitmap bm;
   *         bm.allocPixels(ii);
   * #if defined(SK_GANESH)
   *         if (fDirectContext) {
   *             // Ganesh backed, just use the SkImage::readPixels API
   *             SkASSERT(specialImage->isGaneshBacked());
   *             sk_sp<SkImage> image = specialImage->asImage();
   *             SkAssertResult(image->readPixels(fDirectContext, bm.pixmap(), srcX, srcY));
   *         } else
   * #endif
   * #if defined(SK_GRAPHITE)
   *         if (fRecorder) {
   *             // Graphite backed, so use the private testing-only synchronous API
   *             SkASSERT(specialImage->isGraphiteBacked());
   *             auto view = skgpu::graphite::AsView(specialImage->asImage());
   *             auto proxyII = ii.makeWH(view.width(), view.height());
   *             SkAssertResult(fRecorder->priv().context()->priv().readPixels(
   *                     bm.pixmap(), view.proxy(), proxyII, srcX, srcY));
   *         } else
   * #endif
   *         {
   *             // Assume it's raster backed, so use AsBitmap directly
   *             SkAssertResult(SkSpecialImages::AsBitmap(specialImage, &bm));
   *         }
   *
   *         return bm;
   *     }
   * ```
   */
  private fun readPixels(specialImage: SkSpecialImage?): SkBitmap {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void logBitmaps(const SkBitmap& expected,
   *                     const SkBitmap& actual,
   *                     const TArray<SkIPoint>& badPixels) {
   *         SkString expectedURL;
   *         ToolUtils::BitmapToBase64DataURI(expected, &expectedURL);
   *         SkDebugf("Expected:\n%s\n\n", expectedURL.c_str());
   *
   *         if (!actual.empty()) {
   *             SkString actualURL;
   *             ToolUtils::BitmapToBase64DataURI(actual, &actualURL);
   *             SkDebugf("Actual:\n%s\n\n", actualURL.c_str());
   *         } else {
   *             SkDebugf("Actual: null (fully transparent)\n\n");
   *         }
   *
   *         if (!badPixels.empty()) {
   *             SkBitmap error = expected;
   *             error.allocPixels();
   *             SkAssertResult(expected.readPixels(error.pixmap()));
   *             for (auto p : badPixels) {
   *                 error.erase(SkColors::kRed, SkIRect::MakeXYWH(p.fX, p.fY, 1, 1));
   *             }
   *             SkString markedURL;
   *             ToolUtils::BitmapToBase64DataURI(error, &markedURL);
   *             SkDebugf("Errors:\n%s\n\n", markedURL.c_str());
   *         }
   *     }
   * ```
   */
  private fun logBitmaps(
    expected: SkBitmap,
    `actual`: SkBitmap,
    badPixels: TArray<SkIPoint>,
  ) {
    TODO("Implement logBitmaps")
  }

  public companion object {
    private val kColorType: SkColorType = TODO("Initialize kColorType")
  }
}
