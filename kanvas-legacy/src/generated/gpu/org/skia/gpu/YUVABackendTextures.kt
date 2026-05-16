package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkYUVAInfo
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SK_API YUVABackendTextures {
 * public:
 *     static constexpr auto kMaxPlanes = SkYUVAInfo::kMaxPlanes;
 *
 *     YUVABackendTextures() = default;
 *     YUVABackendTextures(const YUVABackendTextures&) = delete;
 *     YUVABackendTextures& operator=(const YUVABackendTextures&) = delete;
 *
 *     /**
 *      * Initializes a YUVABackendTextures object from a set of textures that store the planes
 *      * indicated by the SkYUVAInfo. This will produce an invalid result (return false from
 *      * isValid()) if the passed texture formats' channels don't agree with SkYUVAInfo.
 *      */
 *     YUVABackendTextures(const SkYUVAInfo&,
 *                         SkSpan<const BackendTexture>);
 *     // DEPRECATED: No more need for a Recorder to construct YUVABackendTextureInfo
 *     YUVABackendTextures(Recorder*,
 *                         const SkYUVAInfo& yuvaInfo,
 *                         SkSpan<const BackendTexture> textures)
 *             : YUVABackendTextures(yuvaInfo, textures) {}
 *
 *     SkSpan<const BackendTexture> planeTextures() const {
 *         return SkSpan<const BackendTexture>(fPlaneTextures);
 *     }
 *
 *     /** BackendTexture for the ith plane, or invalid if i >= numPlanes() */
 *     BackendTexture planeTexture(int i) const {
 *         SkASSERT(i >= 0);
 *         return fPlaneTextures[static_cast<size_t>(i)];
 *     }
 *
 *     const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
 *
 *     SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
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
 *     std::array<BackendTexture, kMaxPlanes> fPlaneTextures;
 *     std::array<uint32_t, kMaxPlanes> fPlaneChannelMasks;
 * }
 * ```
 */
public open class YUVABackendTextures public constructor() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr auto kMaxPlanes
   * ```
   */
  private var fYUVAInfo: Int = TODO("Initialize fYUVAInfo")

  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo fYUVAInfo
   * ```
   */
  private var fPlaneTextures: Int = TODO("Initialize fPlaneTextures")

  /**
   * C++ original:
   * ```cpp
   * std::array<BackendTexture, kMaxPlanes> fPlaneTextures
   * ```
   */
  private var fPlaneChannelMasks: Int = TODO("Initialize fPlaneChannelMasks")

  /**
   * C++ original:
   * ```cpp
   * YUVABackendTextures() = default
   * ```
   */
  public constructor(param0: YUVABackendTextures) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * YUVABackendTextures(const YUVABackendTextures&) = delete
   * ```
   */
  public constructor(yuvaInfo: SkYUVAInfo, textures: SkSpan<BackendTexture>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * YUVABackendTextures(const SkYUVAInfo&,
   *                         SkSpan<const BackendTexture>)
   * ```
   */
  public constructor(
    param0: Recorder,
    yuvaInfo: SkYUVAInfo,
    textures: SkSpan<BackendTexture>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * YUVABackendTextures& operator=(const YUVABackendTextures&) = delete
   * ```
   */
  public fun assign(param0: YUVABackendTextures) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const BackendTexture> planeTextures() const {
   *         return SkSpan<const BackendTexture>(fPlaneTextures);
   *     }
   * ```
   */
  public fun planeTextures(): Int {
    TODO("Implement planeTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendTexture planeTexture(int i) const {
   *         SkASSERT(i >= 0);
   *         return fPlaneTextures[static_cast<size_t>(i)];
   *     }
   * ```
   */
  public fun planeTexture(i: Int): Int {
    TODO("Implement planeTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
   * ```
   */
  public fun yuvaInfo(): Int {
    TODO("Implement yuvaInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
   * ```
   */
  public fun yuvColorSpace(): Int {
    TODO("Implement yuvColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * int numPlanes() const { return fYUVAInfo.numPlanes(); }
   * ```
   */
  public fun numPlanes(): Int {
    TODO("Implement numPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fYUVAInfo.isValid(); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo::YUVALocations YUVABackendTextures::toYUVALocations() const {
   *     auto result = fYUVAInfo.toYUVALocations(fPlaneChannelMasks.data());
   *     SkDEBUGCODE(int numPlanes;)
   *     SkASSERT(SkYUVAInfo::YUVALocation::AreValidLocations(result, &numPlanes));
   *     SkASSERT(numPlanes == this->numPlanes());
   *     return result;
   * }
   * ```
   */
  public fun toYUVALocations(): Int {
    TODO("Implement toYUVALocations")
  }

  public companion object {
    public val kMaxPlanes: Any = TODO("Initialize kMaxPlanes")
  }
}
