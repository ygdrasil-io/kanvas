package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API YUVABackendTextureInfo {
 * public:
 *     static constexpr auto kMaxPlanes = SkYUVAInfo::kMaxPlanes;
 *
 *     /** Default YUVABackendTextureInfo is invalid. */
 *     YUVABackendTextureInfo() = default;
 *     YUVABackendTextureInfo(const YUVABackendTextureInfo&) = default;
 *     YUVABackendTextureInfo& operator=(const YUVABackendTextureInfo&) = default;
 *
 *     /**
 *      * Initializes a YUVABackendTextureInfo to describe a set of textures that can store the
 *      * planes indicated by the SkYUVAInfo. The texture dimensions are taken from the SkYUVAInfo's
 *      * plane dimensions. All the described textures share a common origin. The planar image this
 *      * describes will be mip mapped if all the textures are individually mip mapped as indicated
 *      * by Mipmapped. This will produce an invalid result (return false from isValid()) if the
 *      * passed formats' channels don't agree with SkYUVAInfo.
 *      */
 *     YUVABackendTextureInfo(const SkYUVAInfo&,
 *                            SkSpan<const TextureInfo>,
 *                            Mipmapped);
 *     // DEPRECATED: No more need for a Recorder to construct YUVABackendTextureInfo
 *     YUVABackendTextureInfo(Recorder*,
 *                            const SkYUVAInfo& yuvaInfo,
 *                            SkSpan<const TextureInfo> textures,
 *                            Mipmapped mipmapped)
 *             : YUVABackendTextureInfo(yuvaInfo, textures, mipmapped) {}
 *
 *     bool operator==(const YUVABackendTextureInfo&) const;
 *     bool operator!=(const YUVABackendTextureInfo& that) const { return !(*this == that); }
 *
 *     /** TextureInfo for the ith plane, or invalid if i >= numPlanes() */
 *     const TextureInfo& planeTextureInfo(int i) const {
 *         SkASSERT(i >= 0);
 *         return fPlaneTextureInfos[static_cast<size_t>(i)];
 *     }
 *
 *     const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
 *
 *     SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
 *
 *     Mipmapped mipmapped() const { return fMipmapped; }
 *
 *     /** The number of planes, 0 if this YUVABackendTextureInfo is invalid. */
 *     int numPlanes() const { return fYUVAInfo.numPlanes(); }
 *
 *     /**
 *      * Returns true if this has been configured with a valid SkYUVAInfo with compatible texture
 *      * formats.
 *      */
 *     bool isValid() const { return fYUVAInfo.isValid(); }
 *
 *     /**
 *      * Computes a YUVALocations representation of the planar layout. The result is guaranteed to be
 *      * valid if this->isValid().
 *      */
 *     SkYUVAInfo::YUVALocations toYUVALocations() const;
 *
 * private:
 *     SkYUVAInfo fYUVAInfo;
 *     std::array<TextureInfo, kMaxPlanes> fPlaneTextureInfos;
 *     std::array<uint32_t, kMaxPlanes> fPlaneChannelMasks;
 *     Mipmapped fMipmapped = Mipmapped::kNo;
 * }
 * ```
 */
public data class YUVABackendTextureInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr auto kMaxPlanes
   * ```
   */
  private var fYUVAInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo fYUVAInfo
   * ```
   */
  private var fPlaneTextureInfos: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<TextureInfo, kMaxPlanes> fPlaneTextureInfos
   * ```
   */
  private var fPlaneChannelMasks: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<uint32_t, kMaxPlanes> fPlaneChannelMasks
   * ```
   */
  private var fMipmapped: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * YUVABackendTextureInfo& operator=(const YUVABackendTextureInfo&) = default
   * ```
   */
  public fun assign(param0: YUVABackendTextureInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool YUVABackendTextureInfo::operator==(const YUVABackendTextureInfo& that) const {
   *     if (fYUVAInfo != that.fYUVAInfo || fMipmapped != that.fMipmapped) {
   *         return false;
   *     }
   *     return fPlaneTextureInfos == that.fPlaneTextureInfos;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const YUVABackendTextureInfo& that) const { return !(*this == that); }
   * ```
   */
  public fun planeTextureInfo(i: Int): Int {
    TODO("Implement planeTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureInfo& planeTextureInfo(int i) const {
   *         SkASSERT(i >= 0);
   *         return fPlaneTextureInfos[static_cast<size_t>(i)];
   *     }
   * ```
   */
  public fun yuvaInfo(): Int {
    TODO("Implement yuvaInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
   * ```
   */
  public fun yuvColorSpace(): Int {
    TODO("Implement yuvColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
   * ```
   */
  public fun mipmapped(): Int {
    TODO("Implement mipmapped")
  }

  /**
   * C++ original:
   * ```cpp
   * Mipmapped mipmapped() const { return fMipmapped; }
   * ```
   */
  public fun numPlanes(): Int {
    TODO("Implement numPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * int numPlanes() const { return fYUVAInfo.numPlanes(); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fYUVAInfo.isValid(); }
   * ```
   */
  public fun toYUVALocations(): Int {
    TODO("Implement toYUVALocations")
  }

  public companion object {
    public val kMaxPlanes: Any = TODO("Initialize kMaxPlanes")
  }
}
