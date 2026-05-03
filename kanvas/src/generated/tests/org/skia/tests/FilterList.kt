package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class FilterList {
 * public:
 *     FilterList(const sk_sp<SkImageFilter>& input, const SkIRect* cropRect = nullptr) {
 *         static const SkScalar kBlurSigma = SkIntToScalar(5);
 *
 *         SkPoint3 location = SkPoint3::Make(0, 0, SK_Scalar1);
 *         {
 *             sk_sp<SkColorFilter> cf(SkColorFilters::Blend(SK_ColorRED, SkBlendMode::kSrcIn));
 *
 *             this->addFilter("color filter",
 *                     SkImageFilters::ColorFilter(std::move(cf), input, cropRect));
 *         }
 *         {
 *             sk_sp<SkImage> gradientImage(make_gradient_circle(64, 64).asImage());
 *             sk_sp<SkImageFilter> gradientSource(SkImageFilters::Image(std::move(gradientImage),
 *                                                                       SkFilterMode::kNearest));
 *
 *             this->addFilter("displacement map",
 *                     SkImageFilters::DisplacementMap(SkColorChannel::kR, SkColorChannel::kB, 20.0f,
 *                                                     std::move(gradientSource), input, cropRect));
 *         }
 *         this->addFilter("blur", SkImageFilters::Blur(SK_Scalar1, SK_Scalar1, input, cropRect));
 *         this->addFilter("drop shadow", SkImageFilters::DropShadow(
 *                 SK_Scalar1, SK_Scalar1, SK_Scalar1, SK_Scalar1, SK_ColorGREEN, input, cropRect));
 *         this->addFilter("diffuse lighting",
 *                 SkImageFilters::PointLitDiffuse(location, SK_ColorGREEN, 0, 0, input, cropRect));
 *         this->addFilter("specular lighting",
 *                 SkImageFilters::PointLitSpecular(location, SK_ColorGREEN, 0, 0, 0, input,
 *                                                    cropRect));
 *         {
 *             SkScalar kernel[9] = {
 *                 SkIntToScalar(1), SkIntToScalar(1), SkIntToScalar(1),
 *                 SkIntToScalar(1), SkIntToScalar(-7), SkIntToScalar(1),
 *                 SkIntToScalar(1), SkIntToScalar(1), SkIntToScalar(1),
 *             };
 *             const SkISize kernelSize = SkISize::Make(3, 3);
 *             const SkScalar gain = SK_Scalar1, bias = 0;
 *
 *             // This filter needs a saveLayer bc it is in repeat mode
 *             this->addFilter("matrix convolution",
 *                             SkImageFilters::MatrixConvolution(
 *                                     kernelSize, kernel, gain, bias, SkIPoint::Make(1, 1),
 *                                     SkTileMode::kRepeat, false, input, cropRect),
 *                             true);
 *         }
 *         this->addFilter("merge", SkImageFilters::Merge(input, input, cropRect));
 *
 *         {
 *             sk_sp<SkShader> greenColorShader = SkShaders::Color(SK_ColorGREEN);
 *
 *             SkIRect leftSideCropRect = SkIRect::MakeXYWH(0, 0, 32, 64);
 *             sk_sp<SkImageFilter> shaderFilterLeft(SkImageFilters::Shader(greenColorShader,
 *                                                                          &leftSideCropRect));
 *             SkIRect rightSideCropRect = SkIRect::MakeXYWH(32, 0, 32, 64);
 *             sk_sp<SkImageFilter> shaderFilterRight(SkImageFilters::Shader(greenColorShader,
 *                                                                           &rightSideCropRect));
 *
 *
 *             this->addFilter("merge with disjoint inputs", SkImageFilters::Merge(
 *                     std::move(shaderFilterLeft), std::move(shaderFilterRight), cropRect));
 *         }
 *
 *         this->addFilter("offset", SkImageFilters::Offset(SK_Scalar1, SK_Scalar1, input, cropRect));
 *         this->addFilter("dilate", SkImageFilters::Dilate(3, 2, input, cropRect));
 *         this->addFilter("erode", SkImageFilters::Erode(2, 3, input, cropRect));
 *         this->addFilter("tile", SkImageFilters::Tile(SkRect::MakeXYWH(0, 0, 50, 50),
 *                                                      cropRect ? SkRect::Make(*cropRect)
 *                                                               : SkRect::MakeXYWH(0, 0, 100, 100),
 *                                                      input));
 *
 *         if (!cropRect) {
 *             SkMatrix matrix;
 *
 *             matrix.setTranslate(SK_Scalar1, SK_Scalar1);
 *             matrix.postRotate(SkIntToScalar(45), SK_Scalar1, SK_Scalar1);
 *
 *             this->addFilter("matrix",
 *                     SkImageFilters::MatrixTransform(matrix,
 *                                                     SkSamplingOptions(SkFilterMode::kLinear),
 *                                                     input));
 *         }
 *         {
 *             sk_sp<SkImageFilter> blur(SkImageFilters::Blur(kBlurSigma, kBlurSigma, input));
 *
 *             this->addFilter("blur and offset", SkImageFilters::Offset(
 *                     kBlurSigma, kBlurSigma, std::move(blur), cropRect));
 *         }
 *         {
 *             SkPictureRecorder recorder;
 *             SkCanvas* recordingCanvas = recorder.beginRecording(64, 64);
 *
 *             SkPaint greenPaint;
 *             greenPaint.setColor(SK_ColorGREEN);
 *             recordingCanvas->drawRect(SkRect::Make(SkIRect::MakeXYWH(10, 10, 30, 20)), greenPaint);
 *             sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
 *             sk_sp<SkImageFilter> pictureFilter(SkImageFilters::Picture(std::move(picture)));
 *
 *             this->addFilter("picture and blur", SkImageFilters::Blur(
 *                     kBlurSigma, kBlurSigma, std::move(pictureFilter), cropRect));
 *         }
 *         {
 *             sk_sp<SkImageFilter> paintFilter(SkImageFilters::Shader(
 *                     SkShaders::MakeTurbulence(SK_Scalar1, SK_Scalar1, 1, 0)));
 *
 *             this->addFilter("paint and blur", SkImageFilters::Blur(
 *                     kBlurSigma, kBlurSigma,  std::move(paintFilter), cropRect));
 *         }
 *         this->addFilter("blend", SkImageFilters::Blend(
 *                 SkBlendMode::kSrc, input, input, cropRect));
 *     }
 *     int count() const { return fFilters.size(); }
 *     SkImageFilter* getFilter(int index) const { return fFilters[index].fFilter.get(); }
 *     const char* getName(int index) const { return fFilters[index].fName; }
 *     bool needsSaveLayer(int index) const { return fFilters[index].fNeedsSaveLayer; }
 * private:
 *     struct Filter {
 *         Filter() : fName(nullptr), fNeedsSaveLayer(false) {}
 *         Filter(const char* name, sk_sp<SkImageFilter> filter, bool needsSaveLayer)
 *             : fName(name)
 *             , fFilter(std::move(filter))
 *             , fNeedsSaveLayer(needsSaveLayer) {
 *         }
 *         const char*                 fName;
 *         sk_sp<SkImageFilter>        fFilter;
 *         bool                        fNeedsSaveLayer;
 *     };
 *     void addFilter(const char* name, sk_sp<SkImageFilter> filter, bool needsSaveLayer = false) {
 *         fFilters.push_back(Filter(name, std::move(filter), needsSaveLayer));
 *     }
 *
 *     TArray<Filter> fFilters;
 * }
 * ```
 */
public data class FilterList public constructor(
  /**
   * C++ original:
   * ```cpp
   * TArray<Filter> fFilters
   * ```
   */
  private var fFilters: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int count() const { return fFilters.size(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageFilter* getFilter(int index) const { return fFilters[index].fFilter.get(); }
   * ```
   */
  public fun getFilter(index: Int): SkImageFilter {
    TODO("Implement getFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getName(int index) const { return fFilters[index].fName; }
   * ```
   */
  public fun getName(index: Int): Char {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needsSaveLayer(int index) const { return fFilters[index].fNeedsSaveLayer; }
   * ```
   */
  public fun needsSaveLayer(index: Int): Boolean {
    TODO("Implement needsSaveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void addFilter(const char* name, sk_sp<SkImageFilter> filter, bool needsSaveLayer = false) {
   *         fFilters.push_back(Filter(name, std::move(filter), needsSaveLayer));
   *     }
   * ```
   */
  private fun addFilter(
    name: String?,
    filter: SkSp<SkImageFilter>,
    needsSaveLayer: Boolean = TODO(),
  ) {
    TODO("Implement addFilter")
  }

  public data class Filter public constructor(
    public val fName: String?,
    public var fFilter: SkSp<SkImageFilter>,
    public var fNeedsSaveLayer: Boolean,
  )
}
