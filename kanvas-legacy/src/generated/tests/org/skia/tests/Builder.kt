package org.skia.tests

import org.skia.foundation.SkBlendMode

/**
 * C++ original:
 * ```cpp
 * class Builder {
 * public:
 *     Builder() {}
 *
 *     // Shaders
 *     Builder& hwImg(ImgColorInfo ci, ImgTileModeOptions tmOptions = kNone);
 *     Builder& yuv(YUVSamplingOptions options);
 *     Builder& linearGrad(LinearGradientOptions options);
 *     Builder& blend();
 *
 *     // ColorFilters
 *     Builder& matrixCF();
 *     Builder& porterDuffCF();
 *
 *     // Blendmodes
 *     Builder& clear()   { return this->addBlendMode(SkBlendMode::kClear);   }
 *     Builder& dstIn()   { return this->addBlendMode(SkBlendMode::kDstIn);   }
 *     Builder& src()     { return this->addBlendMode(SkBlendMode::kSrc);     }
 *     Builder& srcOver() { return this->addBlendMode(SkBlendMode::kSrcOver); }
 *
 *     // Misc settings
 *     Builder& transparent() { fPaintOptions.setPaintColorIsOpaque(false); return *this; }
 *     Builder& dither()      { fPaintOptions.setDither(true);              return *this; }
 *
 *     operator skgpu::graphite::PaintOptions() const { return fPaintOptions; }
 *
 * private:
 *     skgpu::graphite::PaintOptions fPaintOptions;
 *
 *     Builder& addBlendMode(SkBlendMode bm) {
 *         fPaintOptions.addBlendMode(bm);
 *         return *this;
 *     }
 * }
 * ```
 */
public open class Builder public constructor() {
  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::hwImg(ImgColorInfo ci, ImgTileModeOptions tmOptions) {
   *     static const SkColorInfo kAlphaInfo(kAlpha_8_SkColorType,
   *                                         kUnpremul_SkAlphaType,
   *                                         nullptr);
   *     static const SkColorInfo kAlphaSRGBInfo(kAlpha_8_SkColorType,
   *                                             kUnpremul_SkAlphaType,
   *                                             SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB,
   *                                                                   SkNamedGamut::kAdobeRGB));
   *     static const SkColorInfo kPremulInfo(kRGBA_8888_SkColorType,
   *                                          kPremul_SkAlphaType,
   *                                          nullptr);
   *     static const SkColorInfo kSRGBInfo(kRGBA_8888_SkColorType,
   *                                        kPremul_SkAlphaType,
   *                                        SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB,
   *                                                              SkNamedGamut::kAdobeRGB));
   *
   *     SkSpan<const SkColorInfo> ciSpan;
   *     switch (ci) {
   *         case kAlpha:     ciSpan = { &kAlphaInfo, 1 };    break;
   *         case kAlphaSRGB: ciSpan = { &kAlphaSRGBInfo, 1}; break;
   *         case kPremul:    ciSpan = { &kPremulInfo, 1 };   break;
   *         case kSRGB:      ciSpan = { &kSRGBInfo, 1 };     break;
   *     }
   *
   *     static const SkTileMode kClampTM  = SkTileMode::kClamp;
   *     static const SkTileMode kRepeatTM = SkTileMode::kRepeat;
   *
   *     SkSpan<const SkTileMode> tmSpan;
   *     switch (tmOptions) {
   *         case kNone:                               break;
   *         case kClamp:  tmSpan = { &kClampTM,  1 }; break;
   *         case kRepeat: tmSpan = { &kRepeatTM, 1 }; break;
   *     }
   *
   *     sk_sp<PrecompileShader> img = PrecompileShaders::Image(ImageShaderFlags::kExcludeCubic,
   *                                                            ciSpan,
   *                                                            tmSpan);
   *     fPaintOptions.setShaders(SKSPAN_INIT_ONE(std::move(img)));
   *     return *this;
   * }
   * ```
   */
  public fun hwImg(ci: ImgColorInfo, tmOptions: ImgTileModeOptions = TODO()): Builder {
    TODO("Implement hwImg")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::yuv(YUVSamplingOptions options) {
   *     static const SkColorInfo kSRGBInfo(kRGBA_8888_SkColorType,
   *                                        kPremul_SkAlphaType,
   *                                        SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB,
   *                                                              SkNamedGamut::kAdobeRGB));
   *
   *     YUVImageShaderFlags flags = YUVImageShaderFlags::kNone;
   *     switch (options) {
   *         case kNoCubic:     flags = YUVImageShaderFlags::kExcludeCubic;           break;
   *         case kHWAndShader: flags = YUVImageShaderFlags::kNoCubicNoNonSwizzledHW; break;
   *     }
   *
   *     sk_sp<PrecompileShader> img = PrecompileShaders::YUVImage(flags, { &kSRGBInfo, 1 });
   *     fPaintOptions.setShaders(SKSPAN_INIT_ONE(std::move(img)));
   *     return *this;
   * }
   * ```
   */
  public fun yuv(options: YUVSamplingOptions): Builder {
    TODO("Implement yuv")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::linearGrad(LinearGradientOptions options) {
   *     sk_sp<PrecompileShader> gradient;
   *
   *     if (options == kSmall) {
   *         gradient = PrecompileShaders::LinearGradient(GradientShaderFlags::kSmall);
   *     } else if (options == kComplex) {
   *         gradient = PrecompileShaders::LinearGradient(
   *                 GradientShaderFlags::kNoLarge,
   *                 { SkGradient::Interpolation::InPremul::kNo,
   *                   SkGradient::Interpolation::ColorSpace::kSRGB,
   *                   SkGradient::Interpolation::HueMethod::kShorter });
   *     }
   *
   *     fPaintOptions.setShaders(SKSPAN_INIT_ONE(std::move(gradient)));
   *     return *this;
   * }
   * ```
   */
  public fun linearGrad(options: LinearGradientOptions): Builder {
    TODO("Implement linearGrad")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::blend() {
   *     SkColorInfo ci { kRGBA_8888_SkColorType, kPremul_SkAlphaType, nullptr };
   *     sk_sp<PrecompileShader> img = PrecompileShaders::Image(ImageShaderFlags::kExcludeCubic,
   *                                                            { &ci, 1 },
   *                                                            {});
   *     SkBlendMode kBlendModes = SkBlendMode::kPlus;
   *     fPaintOptions.setShaders({{ PrecompileShaders::Blend({ &kBlendModes, 1 },
   *                                                         SKSPAN_INIT_ONE(std::move(img)),
   *                                                         SKSPAN_INIT_ONE(PrecompileShaders::Color())) }});
   *     return *this;
   * }
   * ```
   */
  public fun blend(): Builder {
    TODO("Implement blend")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::matrixCF() {
   *     fPaintOptions.setColorFilters(SKSPAN_INIT_ONE(PrecompileColorFilters::Matrix()));
   *     return *this;
   * }
   * ```
   */
  public fun matrixCF(): Builder {
    TODO("Implement matrixCF")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& Builder::porterDuffCF() {
   *     fPaintOptions.setColorFilters(SKSPAN_INIT_ONE( PrecompileColorFilters::Blend(SKSPAN_INIT_ONE(SkBlendMode::kSrcOver)) ));
   *     return *this;
   * }
   * ```
   */
  public fun porterDuffCF(): Builder {
    TODO("Implement porterDuffCF")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& clear()   { return this->addBlendMode(SkBlendMode::kClear);   }
   * ```
   */
  public fun clear(): Builder {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& dstIn()   { return this->addBlendMode(SkBlendMode::kDstIn);   }
   * ```
   */
  public fun dstIn(): Builder {
    TODO("Implement dstIn")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& src()     { return this->addBlendMode(SkBlendMode::kSrc);     }
   * ```
   */
  public fun src(): Builder {
    TODO("Implement src")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& srcOver() { return this->addBlendMode(SkBlendMode::kSrcOver); }
   * ```
   */
  public fun srcOver(): Builder {
    TODO("Implement srcOver")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& transparent() { fPaintOptions.setPaintColorIsOpaque(false); return *this; }
   * ```
   */
  public fun transparent(): Builder {
    TODO("Implement transparent")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& dither()      { fPaintOptions.setDither(true);              return *this; }
   * ```
   */
  public fun dither(): Builder {
    TODO("Implement dither")
  }

  /**
   * C++ original:
   * ```cpp
   * Builder& addBlendMode(SkBlendMode bm) {
   *         fPaintOptions.addBlendMode(bm);
   *         return *this;
   *     }
   * ```
   */
  private fun addBlendMode(bm: SkBlendMode): Builder {
    TODO("Implement addBlendMode")
  }
}
