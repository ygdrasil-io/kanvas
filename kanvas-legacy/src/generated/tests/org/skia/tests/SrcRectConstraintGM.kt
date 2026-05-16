package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SrcRectConstraintGM : public skiagm::GM {
 * public:
 *     SrcRectConstraintGM(const char* shortName, SkCanvas::SrcRectConstraint constraint, bool manual)
 *         : fShortName(shortName)
 *         , fConstraint(constraint)
 *         , fManual(manual) {
 *         // Make sure GPU SkSurfaces can be created for this GM.
 *         SkASSERT(this->getISize().width() <= kMaxTextureSize &&
 *                  this->getISize().height() <= kMaxTextureSize);
 *     }
 *
 * protected:
 *     SkString getName() const override { return fShortName; }
 *     SkISize getISize() override { return SkISize::Make(800, 1000); }
 *
 *     void drawImage(SkCanvas* canvas, sk_sp<SkImage> image, SkRect srcRect, SkRect dstRect,
 *                    const SkSamplingOptions& sampling, SkPaint* paint) {
 *         if (fManual) {
 *             SkTiledImageUtils::DrawImageRect(canvas, image.get(), srcRect, dstRect,
 *                                              sampling, paint, fConstraint);
 *         } else {
 *             canvas->drawImageRect(image.get(), srcRect, dstRect, sampling, paint, fConstraint);
 *         }
 *     }
 *
 *     // Draw the area of interest of the small image
 *     void drawCase1(SkCanvas* canvas, int transX, int transY, bool aa,
 *                    const SkSamplingOptions& sampling) {
 *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
 *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(aa);
 *
 *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
 *     }
 *
 *     // Draw the area of interest of the large image
 *     void drawCase2(SkCanvas* canvas, int transX, int transY, bool aa,
 *                    const SkSamplingOptions& sampling) {
 *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
 *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(aa);
 *
 *         this->drawImage(canvas, fBigImage, fBigSrcRect, dst, sampling, &paint);
 *     }
 *
 *     // Draw upper-left 1/4 of the area of interest of the large image
 *     void drawCase3(SkCanvas* canvas, int transX, int transY, bool aa,
 *                    const SkSamplingOptions& sampling) {
 *         SkRect src = SkRect::MakeXYWH(fBigSrcRect.fLeft,
 *                                       fBigSrcRect.fTop,
 *                                       fBigSrcRect.width()/2,
 *                                       fBigSrcRect.height()/2);
 *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
 *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(aa);
 *
 *         this->drawImage(canvas, fBigImage, src, dst, sampling, &paint);
 *     }
 *
 *     // Draw the area of interest of the small image with a normal blur
 *     void drawCase4(SkCanvas* canvas, int transX, int transY, bool aa,
 *                    const SkSamplingOptions& sampling) {
 *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
 *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
 *
 *         SkPaint paint;
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
 *                                                    SkBlurMask::ConvertRadiusToSigma(3)));
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(aa);
 *
 *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
 *     }
 *
 *     // Draw the area of interest of the small image with a outer blur
 *     void drawCase5(SkCanvas* canvas, int transX, int transY, bool aa,
 *                    const SkSamplingOptions& sampling) {
 *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
 *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
 *
 *         SkPaint paint;
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kOuter_SkBlurStyle,
 *                                                    SkBlurMask::ConvertRadiusToSigma(7)));
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(aa);
 *
 *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fSmallImage) {
 *             std::tie(fBigImage, fBigSrcRect) = make_ringed_image(canvas,
 *                                                                  2*kMaxTextureSize,
 *                                                                  2*kMaxTextureSize);
 *             std::tie(fSmallImage, fSmallSrcRect) = make_ringed_image(canvas,
 *                                                                      kSmallSize, kSmallSize);
 *         }
 *
 *         canvas->clear(SK_ColorGRAY);
 *         std::vector<SkMatrix> matrices;
 *         // Draw with identity
 *         matrices.push_back(SkMatrix::I());
 *
 *         // Draw with rotation and scale down in x, up in y.
 *         SkMatrix m;
 *         constexpr SkScalar kBottom = SkIntToScalar(kRow4Y + kBlockSize + kBlockSpacing);
 *         m.setTranslate(0, kBottom);
 *         m.preRotate(15.f, 0, kBottom + kBlockSpacing);
 *         m.preScale(0.71f, 1.22f);
 *         matrices.push_back(m);
 *
 *         // Align the next set with the middle of the previous in y, translated to the right in x.
 *         SkPoint corners[] = {{0, 0}, {0, kBottom}, {kWidth, kBottom}, {kWidth, 0}};
 *         matrices.back().mapPoints(corners);
 *         m.setTranslate(std::max({corners[0].fX, corners[1].fX, corners[2].fX, corners[3].fX}),
 *                        (corners[0].fY + corners[1].fY + corners[2].fY + corners[3].fY) / 4);
 *         m.preScale(0.2f, 0.2f);
 *         matrices.push_back(m);
 *
 *         const SkSamplingOptions none(SkFilterMode::kNearest);
 *         const SkSamplingOptions  low(SkFilterMode::kLinear);
 *         const SkSamplingOptions high(SkCubicResampler::Mitchell());
 *
 *         SkScalar maxX = 0;
 *         for (bool antiAlias : {false, true}) {
 *             canvas->save();
 *             canvas->translate(maxX, 0);
 *             for (const SkMatrix& matrix : matrices) {
 *                 canvas->save();
 *                 canvas->concat(matrix);
 *
 *                 // First draw a column with no filtering
 *                 this->drawCase1(canvas, kCol0X, kRow0Y, antiAlias, none);
 *                 this->drawCase2(canvas, kCol0X, kRow1Y, antiAlias, none);
 *                 this->drawCase3(canvas, kCol0X, kRow2Y, antiAlias, none);
 *                 this->drawCase4(canvas, kCol0X, kRow3Y, antiAlias, none);
 *                 this->drawCase5(canvas, kCol0X, kRow4Y, antiAlias, none);
 *
 *                 // Then draw a column with low filtering
 *                 this->drawCase1(canvas, kCol1X, kRow0Y, antiAlias, low);
 *                 this->drawCase2(canvas, kCol1X, kRow1Y, antiAlias, low);
 *                 this->drawCase3(canvas, kCol1X, kRow2Y, antiAlias, low);
 *                 this->drawCase4(canvas, kCol1X, kRow3Y, antiAlias, low);
 *                 this->drawCase5(canvas, kCol1X, kRow4Y, antiAlias, low);
 *
 *                 // Then draw a column with high filtering. Skip it if in kStrict mode and MIP
 *                 // mapping will be used. On GPU we allow bleeding at non-base levels because
 *                 // building a new MIP chain for the subset is expensive.
 *                 SkScalar scales[2];
 *                 SkAssertResult(matrix.getMinMaxScales(scales));
 *                 if (fConstraint != SkCanvas::kStrict_SrcRectConstraint || scales[0] >= 1.f) {
 *                     this->drawCase1(canvas, kCol2X, kRow0Y, antiAlias, high);
 *                     this->drawCase2(canvas, kCol2X, kRow1Y, antiAlias, high);
 *                     this->drawCase3(canvas, kCol2X, kRow2Y, antiAlias, high);
 *                     this->drawCase4(canvas, kCol2X, kRow3Y, antiAlias, high);
 *                     this->drawCase5(canvas, kCol2X, kRow4Y, antiAlias, high);
 *                 }
 *
 *                 SkPoint innerCorners[] = {{0, 0}, {0, kBottom}, {kWidth, kBottom}, {kWidth, 0}};
 *                 matrix.mapPoints(innerCorners);
 *                 SkScalar x = kBlockSize + std::max({innerCorners[0].fX, innerCorners[1].fX,
 *                                                     innerCorners[2].fX, innerCorners[3].fX});
 *                 maxX = std::max(maxX, x);
 *                 canvas->restore();
 *             }
 *             canvas->restore();
 *         }
 *     }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* options) override {
 *         options->fMaxTextureSizeOverride = kMaxTextureSize;
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
 *         SkASSERT(options->fOptionsPriv);
 *         options->fOptionsPriv->fMaxTextureSizeOverride = kMaxTextureSize;
 *     }
 * #endif
 *
 * private:
 *     inline static constexpr int kBlockSize = 70;
 *     inline static constexpr int kBlockSpacing = 12;
 *
 *     inline static constexpr int kCol0X = kBlockSpacing;
 *     inline static constexpr int kCol1X = 2*kBlockSpacing + kBlockSize;
 *     inline static constexpr int kCol2X = 3*kBlockSpacing + 2*kBlockSize;
 *     inline static constexpr int kWidth = 4*kBlockSpacing + 3*kBlockSize;
 *
 *     inline static constexpr int kRow0Y = kBlockSpacing;
 *     inline static constexpr int kRow1Y = 2*kBlockSpacing + kBlockSize;
 *     inline static constexpr int kRow2Y = 3*kBlockSpacing + 2*kBlockSize;
 *     inline static constexpr int kRow3Y = 4*kBlockSpacing + 3*kBlockSize;
 *     inline static constexpr int kRow4Y = 5*kBlockSpacing + 4*kBlockSize;
 *
 *     inline static constexpr int kSmallSize = 6;
 *     // This must be at least as large as the GM width and height so that a surface can be made, and
 *     // a power-of-2 to account for any approx-fitting that the backend may perform.
 *     inline static constexpr int kMaxTextureSize = 1024;
 *
 *     SkString fShortName;
 *     sk_sp<SkImage> fBigImage;
 *     sk_sp<SkImage> fSmallImage;
 *     SkRect fBigSrcRect;
 *     SkRect fSmallSrcRect;
 *     SkCanvas::SrcRectConstraint fConstraint;
 *     bool fManual;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SrcRectConstraintGM public constructor(
  shortName: String?,
  constraint: SkCanvas.SrcRectConstraint,
  manual: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kBlockSize = 70
   * ```
   */
  private var fShortName: String = TODO("Initialize fShortName")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kBlockSpacing = 12
   * ```
   */
  private var fBigImage: SkSp<SkImage> = TODO("Initialize fBigImage")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCol0X = kBlockSpacing
   * ```
   */
  private var fSmallImage: SkSp<SkImage> = TODO("Initialize fSmallImage")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCol1X = 2*kBlockSpacing + kBlockSize
   * ```
   */
  private var fBigSrcRect: SkRect = TODO("Initialize fBigSrcRect")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCol2X = 3*kBlockSpacing + 2*kBlockSize
   * ```
   */
  private var fSmallSrcRect: SkRect = TODO("Initialize fSmallSrcRect")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kWidth = 4*kBlockSpacing + 3*kBlockSize
   * ```
   */
  private var fConstraint: SkCanvas.SrcRectConstraint = TODO("Initialize fConstraint")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kRow0Y = kBlockSpacing
   * ```
   */
  private var fManual: Boolean = TODO("Initialize fManual")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fShortName; }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 1000); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImage(SkCanvas* canvas, sk_sp<SkImage> image, SkRect srcRect, SkRect dstRect,
   *                    const SkSamplingOptions& sampling, SkPaint* paint) {
   *         if (fManual) {
   *             SkTiledImageUtils::DrawImageRect(canvas, image.get(), srcRect, dstRect,
   *                                              sampling, paint, fConstraint);
   *         } else {
   *             canvas->drawImageRect(image.get(), srcRect, dstRect, sampling, paint, fConstraint);
   *         }
   *     }
   * ```
   */
  protected fun drawImage(
    canvas: SkCanvas?,
    image: SkSp<SkImage>,
    srcRect: SkRect,
    dstRect: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement drawImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCase1(SkCanvas* canvas, int transX, int transY, bool aa,
   *                    const SkSamplingOptions& sampling) {
   *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
   *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setAntiAlias(aa);
   *
   *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
   *     }
   * ```
   */
  protected fun drawCase1(
    canvas: SkCanvas?,
    transX: Int,
    transY: Int,
    aa: Boolean,
    sampling: SkSamplingOptions,
  ) {
    TODO("Implement drawCase1")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCase2(SkCanvas* canvas, int transX, int transY, bool aa,
   *                    const SkSamplingOptions& sampling) {
   *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
   *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setAntiAlias(aa);
   *
   *         this->drawImage(canvas, fBigImage, fBigSrcRect, dst, sampling, &paint);
   *     }
   * ```
   */
  protected fun drawCase2(
    canvas: SkCanvas?,
    transX: Int,
    transY: Int,
    aa: Boolean,
    sampling: SkSamplingOptions,
  ) {
    TODO("Implement drawCase2")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCase3(SkCanvas* canvas, int transX, int transY, bool aa,
   *                    const SkSamplingOptions& sampling) {
   *         SkRect src = SkRect::MakeXYWH(fBigSrcRect.fLeft,
   *                                       fBigSrcRect.fTop,
   *                                       fBigSrcRect.width()/2,
   *                                       fBigSrcRect.height()/2);
   *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
   *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setAntiAlias(aa);
   *
   *         this->drawImage(canvas, fBigImage, src, dst, sampling, &paint);
   *     }
   * ```
   */
  protected fun drawCase3(
    canvas: SkCanvas?,
    transX: Int,
    transY: Int,
    aa: Boolean,
    sampling: SkSamplingOptions,
  ) {
    TODO("Implement drawCase3")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCase4(SkCanvas* canvas, int transX, int transY, bool aa,
   *                    const SkSamplingOptions& sampling) {
   *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
   *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
   *
   *         SkPaint paint;
   *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
   *                                                    SkBlurMask::ConvertRadiusToSigma(3)));
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setAntiAlias(aa);
   *
   *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
   *     }
   * ```
   */
  protected fun drawCase4(
    canvas: SkCanvas?,
    transX: Int,
    transY: Int,
    aa: Boolean,
    sampling: SkSamplingOptions,
  ) {
    TODO("Implement drawCase4")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCase5(SkCanvas* canvas, int transX, int transY, bool aa,
   *                    const SkSamplingOptions& sampling) {
   *         SkRect dst = SkRect::MakeXYWH(SkIntToScalar(transX), SkIntToScalar(transY),
   *                                       SkIntToScalar(kBlockSize), SkIntToScalar(kBlockSize));
   *
   *         SkPaint paint;
   *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kOuter_SkBlurStyle,
   *                                                    SkBlurMask::ConvertRadiusToSigma(7)));
   *         paint.setColor(SK_ColorBLUE);
   *         paint.setAntiAlias(aa);
   *
   *         this->drawImage(canvas, fSmallImage, fSmallSrcRect, dst, sampling, &paint);
   *     }
   * ```
   */
  protected fun drawCase5(
    canvas: SkCanvas?,
    transX: Int,
    transY: Int,
    aa: Boolean,
    sampling: SkSamplingOptions,
  ) {
    TODO("Implement drawCase5")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (!fSmallImage) {
   *             std::tie(fBigImage, fBigSrcRect) = make_ringed_image(canvas,
   *                                                                  2*kMaxTextureSize,
   *                                                                  2*kMaxTextureSize);
   *             std::tie(fSmallImage, fSmallSrcRect) = make_ringed_image(canvas,
   *                                                                      kSmallSize, kSmallSize);
   *         }
   *
   *         canvas->clear(SK_ColorGRAY);
   *         std::vector<SkMatrix> matrices;
   *         // Draw with identity
   *         matrices.push_back(SkMatrix::I());
   *
   *         // Draw with rotation and scale down in x, up in y.
   *         SkMatrix m;
   *         constexpr SkScalar kBottom = SkIntToScalar(kRow4Y + kBlockSize + kBlockSpacing);
   *         m.setTranslate(0, kBottom);
   *         m.preRotate(15.f, 0, kBottom + kBlockSpacing);
   *         m.preScale(0.71f, 1.22f);
   *         matrices.push_back(m);
   *
   *         // Align the next set with the middle of the previous in y, translated to the right in x.
   *         SkPoint corners[] = {{0, 0}, {0, kBottom}, {kWidth, kBottom}, {kWidth, 0}};
   *         matrices.back().mapPoints(corners);
   *         m.setTranslate(std::max({corners[0].fX, corners[1].fX, corners[2].fX, corners[3].fX}),
   *                        (corners[0].fY + corners[1].fY + corners[2].fY + corners[3].fY) / 4);
   *         m.preScale(0.2f, 0.2f);
   *         matrices.push_back(m);
   *
   *         const SkSamplingOptions none(SkFilterMode::kNearest);
   *         const SkSamplingOptions  low(SkFilterMode::kLinear);
   *         const SkSamplingOptions high(SkCubicResampler::Mitchell());
   *
   *         SkScalar maxX = 0;
   *         for (bool antiAlias : {false, true}) {
   *             canvas->save();
   *             canvas->translate(maxX, 0);
   *             for (const SkMatrix& matrix : matrices) {
   *                 canvas->save();
   *                 canvas->concat(matrix);
   *
   *                 // First draw a column with no filtering
   *                 this->drawCase1(canvas, kCol0X, kRow0Y, antiAlias, none);
   *                 this->drawCase2(canvas, kCol0X, kRow1Y, antiAlias, none);
   *                 this->drawCase3(canvas, kCol0X, kRow2Y, antiAlias, none);
   *                 this->drawCase4(canvas, kCol0X, kRow3Y, antiAlias, none);
   *                 this->drawCase5(canvas, kCol0X, kRow4Y, antiAlias, none);
   *
   *                 // Then draw a column with low filtering
   *                 this->drawCase1(canvas, kCol1X, kRow0Y, antiAlias, low);
   *                 this->drawCase2(canvas, kCol1X, kRow1Y, antiAlias, low);
   *                 this->drawCase3(canvas, kCol1X, kRow2Y, antiAlias, low);
   *                 this->drawCase4(canvas, kCol1X, kRow3Y, antiAlias, low);
   *                 this->drawCase5(canvas, kCol1X, kRow4Y, antiAlias, low);
   *
   *                 // Then draw a column with high filtering. Skip it if in kStrict mode and MIP
   *                 // mapping will be used. On GPU we allow bleeding at non-base levels because
   *                 // building a new MIP chain for the subset is expensive.
   *                 SkScalar scales[2];
   *                 SkAssertResult(matrix.getMinMaxScales(scales));
   *                 if (fConstraint != SkCanvas::kStrict_SrcRectConstraint || scales[0] >= 1.f) {
   *                     this->drawCase1(canvas, kCol2X, kRow0Y, antiAlias, high);
   *                     this->drawCase2(canvas, kCol2X, kRow1Y, antiAlias, high);
   *                     this->drawCase3(canvas, kCol2X, kRow2Y, antiAlias, high);
   *                     this->drawCase4(canvas, kCol2X, kRow3Y, antiAlias, high);
   *                     this->drawCase5(canvas, kCol2X, kRow4Y, antiAlias, high);
   *                 }
   *
   *                 SkPoint innerCorners[] = {{0, 0}, {0, kBottom}, {kWidth, kBottom}, {kWidth, 0}};
   *                 matrix.mapPoints(innerCorners);
   *                 SkScalar x = kBlockSize + std::max({innerCorners[0].fX, innerCorners[1].fX,
   *                                                     innerCorners[2].fX, innerCorners[3].fX});
   *                 maxX = std::max(maxX, x);
   *                 canvas->restore();
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
   *         SkASSERT(options->fOptionsPriv);
   *         options->fOptionsPriv->fMaxTextureSizeOverride = kMaxTextureSize;
   *     }
   * ```
   */
  protected override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  public companion object {
    private val kBlockSize: Int = TODO("Initialize kBlockSize")

    private val kBlockSpacing: Int = TODO("Initialize kBlockSpacing")

    private val kCol0X: Int = TODO("Initialize kCol0X")

    private val kCol1X: Int = TODO("Initialize kCol1X")

    private val kCol2X: Int = TODO("Initialize kCol2X")

    private val kWidth: Int = TODO("Initialize kWidth")

    private val kRow0Y: Int = TODO("Initialize kRow0Y")

    private val kRow1Y: Int = TODO("Initialize kRow1Y")

    private val kRow2Y: Int = TODO("Initialize kRow2Y")

    private val kRow3Y: Int = TODO("Initialize kRow3Y")

    private val kRow4Y: Int = TODO("Initialize kRow4Y")

    private val kSmallSize: Int = TODO("Initialize kSmallSize")

    private val kMaxTextureSize: Int = TODO("Initialize kMaxTextureSize")
  }
}
