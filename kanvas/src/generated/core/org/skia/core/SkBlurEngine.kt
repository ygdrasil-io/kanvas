package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkColorType
import org.skia.math.SkSize
import undefined.Algorithm

/**
 * C++ original:
 * ```cpp
 * class SkBlurEngine {
 * public:
 *     class Algorithm;
 *
 *     virtual ~SkBlurEngine() = default;
 *
 *     // Returns an Algorithm ideal for the requested 'sigma' that will support sampling an image of
 *     // the given 'colorType'. If the engine does not support the requested configuration, it returns
 *     // null. The engine maintains the lifetime of its algorithms, so the returned non-null
 *     // Algorithms live as long as the engine does.
 *     virtual const Algorithm* findAlgorithm(SkSize sigma,
 *                                            SkColorType colorType) const = 0;
 *
 *     // TODO: Consolidate common utility functions from SkBlurMask.h into this header.
 *
 *     // Any sigmas smaller than this are effectively an identity blur so can skip convolution at a
 *     // higher level. The value was chosen because it corresponds roughly to a radius of 1/10px, and
 *     // because 2*sigma^2 is slightly greater than SK_ScalarNearlyZero.
 *     static constexpr bool IsEffectivelyIdentity(float sigma) { return sigma <= 0.03f; }
 *
 *     // Convert from a sigma Gaussian standard deviation to a pixel radius such that pixels outside
 *     // the radius would have an insignificant contribution to the final blurred value.
 *     static int SigmaToRadius(float sigma) {
 *         // sk_float_ceil2int is not constexpr
 *         return IsEffectivelyIdentity(sigma) ? 0 : sk_float_ceil2int(3.f * sigma);
 *     }
 *
 *     // Get the default CPU-backed SkBlurEngine. This has specialized algorithms for 32-bit RGBA
 *     // and BGRA colors, and A8 alpha-only images when the sigma is large enough. For small blurs
 *     // and other color types, it uses SkShaderBlurAlgorithm backed by the raster pipeline.
 *     static const SkBlurEngine* GetRasterBlurEngine();
 *
 *     // TODO: These are internal functions of the raster blur engine but need to be public for legacy
 *     // code paths to invoke them directly.
 *
 *     // Calculate the successive box blur window for a given sigma. This is defined by the SVG spec:
 *     // https://drafts.fxtf.org/filter-effects/#feGaussianBlurElement
 *     //
 *     // NOTE: The successive box blur approximation is too inaccurate for cases where sigma < 2,
 *     // which works out to a window size of 4. If the window is smaller than this on both axes, the
 *     // successive box blur should not be used. If only one axis is this small, assume the
 *     // inaccuracies are hidden to avoid having to mix a shader-based blur and a box blur.
 *     static int BoxBlurWindow(float sigma) {
 *         int possibleWindow = sk_float_floor2int(sigma * 3 * sqrt(2 * SK_FloatPI) / 4 + 0.5f);
 *         return std::max(1, possibleWindow);
 *     }
 * }
 * ```
 */
public abstract class SkBlurEngine {
  /**
   * C++ original:
   * ```cpp
   * virtual const Algorithm* findAlgorithm(SkSize sigma,
   *                                            SkColorType colorType) const = 0
   * ```
   */
  public abstract fun findAlgorithm(sigma: SkSize, colorType: SkColorType): Algorithm

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr bool IsEffectivelyIdentity(float sigma) { return sigma <= 0.03f; }
     * ```
     */
    public fun isEffectivelyIdentity(sigma: Float): Boolean {
      TODO("Implement isEffectivelyIdentity")
    }

    /**
     * C++ original:
     * ```cpp
     * static int SigmaToRadius(float sigma) {
     *         // sk_float_ceil2int is not constexpr
     *         return IsEffectivelyIdentity(sigma) ? 0 : sk_float_ceil2int(3.f * sigma);
     *     }
     * ```
     */
    public fun sigmaToRadius(sigma: Float): Int {
      TODO("Implement sigmaToRadius")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkBlurEngine* SkBlurEngine::GetRasterBlurEngine() {
     *     static const RasterBlurEngine kInstance;
     *     return &kInstance;
     * }
     * ```
     */
    public fun getRasterBlurEngine(): SkBlurEngine {
      TODO("Implement getRasterBlurEngine")
    }

    /**
     * C++ original:
     * ```cpp
     * static int BoxBlurWindow(float sigma) {
     *         int possibleWindow = sk_float_floor2int(sigma * 3 * sqrt(2 * SK_FloatPI) / 4 + 0.5f);
     *         return std::max(1, possibleWindow);
     *     }
     * ```
     */
    public fun boxBlurWindow(sigma: Float): Int {
      TODO("Implement boxBlurWindow")
    }
  }
}
