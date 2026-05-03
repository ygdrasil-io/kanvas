package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSurfaceProps {
 * public:
 *     enum Flags {
 *         kDefault_Flag = 0,
 *         kUseDeviceIndependentFonts_Flag = 1 << 0,
 *         // Use internal MSAA to render to non-MSAA GPU surfaces.
 *         kDynamicMSAA_Flag = 1 << 1,
 *         // If set, all rendering will have dithering enabled
 *         // Currently this only impacts GPU backends
 *         kAlwaysDither_Flag = 1 << 2,
 *         // The surface will preserve transparent draws (instead of skipping them).
 *         kPreservesTransparentDraws_Flag = 1 << 3,
 *     };
 *
 *     /** No flags, unknown pixel geometry, platform-default contrast/gamma. */
 *     SkSurfaceProps();
 *     /** TODO(kschmi): Remove this constructor and replace with the one below. **/
 *     SkSurfaceProps(uint32_t flags, SkPixelGeometry);
 *     /** Specified pixel geometry, text contrast, and gamma **/
 *     SkSurfaceProps(uint32_t flags, SkPixelGeometry, SkScalar textContrast, SkScalar textGamma);
 *
 *     SkSurfaceProps(const SkSurfaceProps&) = default;
 *     SkSurfaceProps& operator=(const SkSurfaceProps&) = default;
 *
 *     SkSurfaceProps cloneWithPixelGeometry(SkPixelGeometry newPixelGeometry) const {
 *         return SkSurfaceProps(fFlags, newPixelGeometry, fTextContrast, fTextGamma);
 *     }
 *
 *     static constexpr SkScalar kMaxContrastInclusive = 1;
 *     static constexpr SkScalar kMinContrastInclusive = 0;
 *     static constexpr SkScalar kMaxGammaExclusive = 4;
 *     static constexpr SkScalar kMinGammaInclusive = 0;
 *
 *     uint32_t flags() const { return fFlags; }
 *     SkPixelGeometry pixelGeometry() const { return fPixelGeometry; }
 *     SkScalar textContrast() const { return fTextContrast; }
 *     SkScalar textGamma() const { return fTextGamma; }
 *
 *     bool isUseDeviceIndependentFonts() const {
 *         return SkToBool(fFlags & kUseDeviceIndependentFonts_Flag);
 *     }
 *
 *     bool isAlwaysDither() const {
 *         return SkToBool(fFlags & kAlwaysDither_Flag);
 *     }
 *
 *     bool preservesTransparentDraws() const {
 *         return SkToBool(fFlags & kPreservesTransparentDraws_Flag);
 *     }
 *
 *     bool operator==(const SkSurfaceProps& that) const {
 *         return fFlags == that.fFlags && fPixelGeometry == that.fPixelGeometry &&
 *         fTextContrast == that.fTextContrast && fTextGamma == that.fTextGamma;
 *     }
 *
 *     bool operator!=(const SkSurfaceProps& that) const {
 *         return !(*this == that);
 *     }
 *
 * private:
 *     uint32_t        fFlags;
 *     SkPixelGeometry fPixelGeometry;
 *
 *     // This gamma value is specifically about blending of mask coverage.
 *     // The surface also has a color space, but that applies to the colors.
 *     SkScalar fTextContrast;
 *     SkScalar fTextGamma;
 * }
 * ```
 */
public data class SkSurfaceProps public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kMaxContrastInclusive = 1
   * ```
   */
  private var fFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kMinContrastInclusive = 0
   * ```
   */
  private var fPixelGeometry: SkPixelGeometry,
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kMaxGammaExclusive = 4
   * ```
   */
  private var fTextContrast: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kMinGammaInclusive = 0
   * ```
   */
  private var fTextGamma: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps& operator=(const SkSurfaceProps&) = default
   * ```
   */
  public fun assign(param0: SkSurfaceProps) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps cloneWithPixelGeometry(SkPixelGeometry newPixelGeometry) const {
   *         return SkSurfaceProps(fFlags, newPixelGeometry, fTextContrast, fTextGamma);
   *     }
   * ```
   */
  public fun cloneWithPixelGeometry(newPixelGeometry: SkPixelGeometry): SkSurfaceProps {
    TODO("Implement cloneWithPixelGeometry")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t flags() const { return fFlags; }
   * ```
   */
  public fun flags(): Int {
    TODO("Implement flags")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixelGeometry pixelGeometry() const { return fPixelGeometry; }
   * ```
   */
  public fun pixelGeometry(): SkPixelGeometry {
    TODO("Implement pixelGeometry")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar textContrast() const { return fTextContrast; }
   * ```
   */
  public fun textContrast(): Int {
    TODO("Implement textContrast")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar textGamma() const { return fTextGamma; }
   * ```
   */
  public fun textGamma(): Int {
    TODO("Implement textGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isUseDeviceIndependentFonts() const {
   *         return SkToBool(fFlags & kUseDeviceIndependentFonts_Flag);
   *     }
   * ```
   */
  public fun isUseDeviceIndependentFonts(): Boolean {
    TODO("Implement isUseDeviceIndependentFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAlwaysDither() const {
   *         return SkToBool(fFlags & kAlwaysDither_Flag);
   *     }
   * ```
   */
  public fun isAlwaysDither(): Boolean {
    TODO("Implement isAlwaysDither")
  }

  /**
   * C++ original:
   * ```cpp
   * bool preservesTransparentDraws() const {
   *         return SkToBool(fFlags & kPreservesTransparentDraws_Flag);
   *     }
   * ```
   */
  public fun preservesTransparentDraws(): Boolean {
    TODO("Implement preservesTransparentDraws")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSurfaceProps& that) const {
   *         return fFlags == that.fFlags && fPixelGeometry == that.fPixelGeometry &&
   *         fTextContrast == that.fTextContrast && fTextGamma == that.fTextGamma;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public enum class Flags {
    kDefault_Flag,
    kUseDeviceIndependentFonts_Flag,
    kDynamicMSAA_Flag,
    kAlwaysDither_Flag,
    kPreservesTransparentDraws_Flag,
  }

  public companion object {
    public val kMaxContrastInclusive: Int = TODO("Initialize kMaxContrastInclusive")

    public val kMinContrastInclusive: Int = TODO("Initialize kMinContrastInclusive")

    public val kMaxGammaExclusive: Int = TODO("Initialize kMaxGammaExclusive")

    public val kMinGammaInclusive: Int = TODO("Initialize kMinGammaInclusive")
  }
}
