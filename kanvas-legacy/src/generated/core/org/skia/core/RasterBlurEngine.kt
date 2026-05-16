package org.skia.core

import org.skia.foundation.SkColorType
import org.skia.math.SkSize
import undefined.Algorithm

/**
 * C++ original:
 * ```cpp
 * class RasterBlurEngine : public SkBlurEngine {
 * public:
 *     const Algorithm* findAlgorithm(SkSize sigma,  SkColorType colorType) const override {
 *         // The box blur doesn't actually care about channel order as long as it's 4 8-bit channels.
 *         const bool rgba8Blur = colorType == kRGBA_8888_SkColorType ||
 *                                colorType == kBGRA_8888_SkColorType;
 *         const bool a8Blur = colorType == kAlpha_8_SkColorType;
 *
 *         // For small sigmas, a8 and rgba blurs will use a gaussian blur, otherwise using
 *         // box blur approximation.
 *         if (a8Blur) {
 *             return &fA8BlurAlgorithm;
 *         } else if (rgba8Blur) {
 *             return &fRGBA8BlurAlgorithm;
 *         } else {
 *             return &fShaderBlurAlgorithm;
 *         }
 *     }
 *
 * private:
 *     // For non-A8 or non-8888, use the shader algorithm
 *     RasterShaderBlurAlgorithm fShaderBlurAlgorithm;
 *     // For large blurs with RGBA8 or BGRA8, use consecutive box blurs,
 *     // For small 8888 blurs, use gaussian blur
 *     Raster8888BlurAlgorithm fRGBA8BlurAlgorithm;
 *     // For any large blurs with A8, use consecutive box blurs,
 *     // For small a8 blurs use gaussian blur
 *     RasterA8BlurAlgorithm fA8BlurAlgorithm;
 * }
 * ```
 */
public open class RasterBlurEngine : SkBlurEngine() {
  /**
   * C++ original:
   * ```cpp
   * RasterShaderBlurAlgorithm fShaderBlurAlgorithm
   * ```
   */
  private var fShaderBlurAlgorithm: RasterShaderBlurAlgorithm =
      TODO("Initialize fShaderBlurAlgorithm")

  /**
   * C++ original:
   * ```cpp
   * Raster8888BlurAlgorithm fRGBA8BlurAlgorithm
   * ```
   */
  private var fRGBA8BlurAlgorithm: Raster8888BlurAlgorithm = TODO("Initialize fRGBA8BlurAlgorithm")

  /**
   * C++ original:
   * ```cpp
   * RasterA8BlurAlgorithm fA8BlurAlgorithm
   * ```
   */
  private var fA8BlurAlgorithm: RasterA8BlurAlgorithm = TODO("Initialize fA8BlurAlgorithm")

  /**
   * C++ original:
   * ```cpp
   * const Algorithm* findAlgorithm(SkSize sigma,  SkColorType colorType) const override {
   *         // The box blur doesn't actually care about channel order as long as it's 4 8-bit channels.
   *         const bool rgba8Blur = colorType == kRGBA_8888_SkColorType ||
   *                                colorType == kBGRA_8888_SkColorType;
   *         const bool a8Blur = colorType == kAlpha_8_SkColorType;
   *
   *         // For small sigmas, a8 and rgba blurs will use a gaussian blur, otherwise using
   *         // box blur approximation.
   *         if (a8Blur) {
   *             return &fA8BlurAlgorithm;
   *         } else if (rgba8Blur) {
   *             return &fRGBA8BlurAlgorithm;
   *         } else {
   *             return &fShaderBlurAlgorithm;
   *         }
   *     }
   * ```
   */
  public override fun findAlgorithm(sigma: SkSize, colorType: SkColorType): Algorithm {
    TODO("Implement findAlgorithm")
  }
}
