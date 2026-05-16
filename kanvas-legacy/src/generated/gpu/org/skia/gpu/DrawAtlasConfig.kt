package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DrawAtlasConfig {
 * public:
 *     // The capabilities of the GPU define maxTextureSize. The client provides maxBytes, and this
 *     // represents the largest they want a single atlas texture to be. Due to multitexturing, we
 *     // may expand temporarily to use more space as needed.
 *     DrawAtlasConfig(int maxTextureSize, size_t maxBytes);
 *
 *     SkISize atlasDimensions(MaskFormat type) const;
 *     SkISize plotDimensions(MaskFormat type) const;
 *
 * private:
 *     // On some systems texture coordinates are represented using half-precision floating point
 *     // with 11 significant bits, which limits the largest atlas dimensions to 2048x2048.
 *     // For simplicity we'll use this constraint for all of our atlas textures.
 *     // This can be revisited later if we need larger atlases.
 *     inline static constexpr int kMaxAtlasDim = 2048;
 *
 *     SkISize fARGBDimensions;
 *     int     fMaxTextureSize;
 * }
 * ```
 */
public data class DrawAtlasConfig public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxAtlasDim = 2048
   * ```
   */
  private var fARGBDimensions: Int,
  /**
   * C++ original:
   * ```cpp
   * SkISize fARGBDimensions
   * ```
   */
  private var fMaxTextureSize: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkISize DrawAtlasConfig::atlasDimensions(MaskFormat type) const {
   *     if (MaskFormat::kA8 == type) {
   *         // A8 is always 2x the ARGB dimensions, clamped to the max allowed texture size
   *         return { std::min<int>(2 * fARGBDimensions.width(), fMaxTextureSize),
   *                  std::min<int>(2 * fARGBDimensions.height(), fMaxTextureSize) };
   *     } else {
   *         return fARGBDimensions;
   *     }
   * }
   * ```
   */
  public fun atlasDimensions(type: MaskFormat): Int {
    TODO("Implement atlasDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize DrawAtlasConfig::plotDimensions(MaskFormat type) const {
   *     if (MaskFormat::kA8 == type) {
   *         SkISize atlasDimensions = this->atlasDimensions(type);
   *         // For A8 we want to grow the plots at larger texture sizes to accept more of the
   *         // larger SDF glyphs. Since the largest SDF glyph can be 170x170 with padding, this
   *         // allows us to pack 3 in a 512x256 plot, or 9 in a 512x512 plot.
   *
   *         // This will give us 512x256 plots for 2048x1024, 512x512 plots for 2048x2048,
   *         // and 256x256 plots otherwise.
   *         int plotWidth = atlasDimensions.width() >= 2048 ? 512 : 256;
   *         int plotHeight = atlasDimensions.height() >= 2048 ? 512 : 256;
   *
   *         return { plotWidth, plotHeight };
   *     } else {
   *         // ARGB and LCD always use 256x256 plots -- this has been shown to be faster
   *         return { 256, 256 };
   *     }
   * }
   * ```
   */
  public fun plotDimensions(type: MaskFormat): Int {
    TODO("Implement plotDimensions")
  }

  public companion object {
    private val kMaxAtlasDim: Int = TODO("Initialize kMaxAtlasDim")
  }
}
