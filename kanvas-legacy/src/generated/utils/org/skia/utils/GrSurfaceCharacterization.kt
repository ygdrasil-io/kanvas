package org.skia.utils

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrBackendFormat

/**
 * C++ original:
 * ```cpp
 * class SK_API GrSurfaceCharacterization {
 * public:
 *     enum class Textureable : bool { kNo = false, kYes = true };
 *     enum class UsesGLFBO0 : bool { kNo = false, kYes = true };
 *     // This flag indicates that the backing VkImage for this Vulkan surface will have the
 *     // VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT set. This bit allows skia to handle advanced blends
 *     // more optimally in a shader by being able to directly read the dst values.
 *     enum class VkRTSupportsInputAttachment : bool { kNo = false, kYes = true };
 *     // This flag indicates if the surface is wrapping a raw Vulkan secondary command buffer.
 *     enum class VulkanSecondaryCBCompatible : bool { kNo = false, kYes = true };
 *
 *     GrSurfaceCharacterization()
 *             : fCacheMaxResourceBytes(0)
 *             , fOrigin(kBottomLeft_GrSurfaceOrigin)
 *             , fSampleCnt(0)
 *             , fIsTextureable(Textureable::kYes)
 *             , fIsMipmapped(skgpu::Mipmapped::kYes)
 *             , fUsesGLFBO0(UsesGLFBO0::kNo)
 *             , fVulkanSecondaryCBCompatible(VulkanSecondaryCBCompatible::kNo)
 *             , fIsProtected(skgpu::Protected::kNo)
 *             , fSurfaceProps() {}
 *
 *     GrSurfaceCharacterization(GrSurfaceCharacterization&&) = default;
 *     GrSurfaceCharacterization& operator=(GrSurfaceCharacterization&&) = default;
 *
 *     GrSurfaceCharacterization(const GrSurfaceCharacterization&) = default;
 *     GrSurfaceCharacterization& operator=(const GrSurfaceCharacterization& other) = default;
 *     bool operator==(const GrSurfaceCharacterization& other) const;
 *     bool operator!=(const GrSurfaceCharacterization& other) const {
 *         return !(*this == other);
 *     }
 *
 *     /*
 *      * Return a new surface characterization with the only difference being a different width
 *      * and height
 *      */
 *     GrSurfaceCharacterization createResized(int width, int height) const;
 *
 *     /*
 *      * Return a new surface characterization with only a replaced color space
 *      */
 *     GrSurfaceCharacterization createColorSpace(sk_sp<SkColorSpace>) const;
 *
 *     /*
 *      * Return a new surface characterization with the backend format replaced. A colorType
 *      * must also be supplied to indicate the interpretation of the new format.
 *      */
 *     GrSurfaceCharacterization createBackendFormat(SkColorType colorType,
 *                                                   const GrBackendFormat& backendFormat) const;
 *
 *     /*
 *      * Return a new surface characterization with just a different use of FBO0 (in GL)
 *      */
 *     GrSurfaceCharacterization createFBO0(bool usesGLFBO0) const;
 *
 *     GrContextThreadSafeProxy* contextInfo() const { return fContextInfo.get(); }
 *     sk_sp<GrContextThreadSafeProxy> refContextInfo() const { return fContextInfo; }
 *     size_t cacheMaxResourceBytes() const { return fCacheMaxResourceBytes; }
 *
 *     bool isValid() const { return kUnknown_SkColorType != fImageInfo.colorType(); }
 *
 *     const SkImageInfo& imageInfo() const { return fImageInfo; }
 *     const GrBackendFormat& backendFormat() const { return fBackendFormat; }
 *     GrSurfaceOrigin origin() const { return fOrigin; }
 *     SkISize dimensions() const { return fImageInfo.dimensions(); }
 *     int width() const { return fImageInfo.width(); }
 *     int height() const { return fImageInfo.height(); }
 *     SkColorType colorType() const { return fImageInfo.colorType(); }
 *     int sampleCount() const { return fSampleCnt; }
 *     bool isTextureable() const { return Textureable::kYes == fIsTextureable; }
 *     bool isMipMapped() const { return skgpu::Mipmapped::kYes == fIsMipmapped; }
 *     bool usesGLFBO0() const { return UsesGLFBO0::kYes == fUsesGLFBO0; }
 *     bool vkRTSupportsInputAttachment() const {
 *         return VkRTSupportsInputAttachment::kYes == fVkRTSupportsInputAttachment;
 *     }
 *     bool vulkanSecondaryCBCompatible() const {
 *         return VulkanSecondaryCBCompatible::kYes == fVulkanSecondaryCBCompatible;
 *     }
 *     skgpu::Protected isProtected() const { return fIsProtected; }
 *     SkColorSpace* colorSpace() const { return fImageInfo.colorSpace(); }
 *     sk_sp<SkColorSpace> refColorSpace() const { return fImageInfo.refColorSpace(); }
 *     const SkSurfaceProps& surfaceProps()const { return fSurfaceProps; }
 *
 * private:
 *     friend class SkSurface_Ganesh;           // for 'set' & 'config'
 *     friend class GrVkSecondaryCBDrawContext; // for 'set' & 'config'
 *     friend class GrContextThreadSafeProxy; // for private ctor
 *     friend class GrVkContextThreadSafeProxy;    // for private ctor
 *     friend class GrDeferredDisplayListRecorder; // for 'config'
 *     friend class SkSurface; // for 'config'
 *
 *     SkDEBUGCODE(void validate() const;)
 *
 *             GrSurfaceCharacterization(sk_sp<GrContextThreadSafeProxy> contextInfo,
 *                                       size_t cacheMaxResourceBytes,
 *                                       const SkImageInfo& ii,
 *                                       const GrBackendFormat& backendFormat,
 *                                       GrSurfaceOrigin origin,
 *                                       int sampleCnt,
 *                                       Textureable isTextureable,
 *                                       skgpu::Mipmapped isMipmapped,
 *                                       UsesGLFBO0 usesGLFBO0,
 *                                       VkRTSupportsInputAttachment vkRTSupportsInputAttachment,
 *                                       VulkanSecondaryCBCompatible vulkanSecondaryCBCompatible,
 *                                       skgpu::Protected isProtected,
 *                                       const SkSurfaceProps& surfaceProps)
 *             : fContextInfo(std::move(contextInfo))
 *             , fCacheMaxResourceBytes(cacheMaxResourceBytes)
 *             , fImageInfo(ii)
 *             , fBackendFormat(std::move(backendFormat))
 *             , fOrigin(origin)
 *             , fSampleCnt(sampleCnt)
 *             , fIsTextureable(isTextureable)
 *             , fIsMipmapped(isMipmapped)
 *             , fUsesGLFBO0(usesGLFBO0)
 *             , fVkRTSupportsInputAttachment(vkRTSupportsInputAttachment)
 *             , fVulkanSecondaryCBCompatible(vulkanSecondaryCBCompatible)
 *             , fIsProtected(isProtected)
 *             , fSurfaceProps(surfaceProps) {
 *         if (fSurfaceProps.flags() & SkSurfaceProps::kDynamicMSAA_Flag) {
 *             // Dynamic MSAA is not currently supported with DDL.
 *             *this = {};
 *         }
 *         SkDEBUGCODE(this->validate());
 *     }
 *
 *     void set(sk_sp<GrContextThreadSafeProxy> contextInfo,
 *              size_t cacheMaxResourceBytes,
 *              const SkImageInfo& ii,
 *              const GrBackendFormat& backendFormat,
 *              GrSurfaceOrigin origin,
 *              int sampleCnt,
 *              Textureable isTextureable,
 *              skgpu::Mipmapped isMipmapped,
 *              UsesGLFBO0 usesGLFBO0,
 *              VkRTSupportsInputAttachment vkRTSupportsInputAttachment,
 *              VulkanSecondaryCBCompatible vulkanSecondaryCBCompatible,
 *              skgpu::Protected isProtected,
 *              const SkSurfaceProps& surfaceProps) {
 *         if (surfaceProps.flags() & SkSurfaceProps::kDynamicMSAA_Flag) {
 *             // Dynamic MSAA is not currently supported with DDL.
 *             *this = {};
 *         } else {
 *             fContextInfo = std::move(contextInfo);
 *             fCacheMaxResourceBytes = cacheMaxResourceBytes;
 *
 *             fImageInfo = ii;
 *             fBackendFormat = std::move(backendFormat);
 *             fOrigin = origin;
 *             fSampleCnt = sampleCnt;
 *             fIsTextureable = isTextureable;
 *             fIsMipmapped = isMipmapped;
 *             fUsesGLFBO0 = usesGLFBO0;
 *             fVkRTSupportsInputAttachment = vkRTSupportsInputAttachment;
 *             fVulkanSecondaryCBCompatible = vulkanSecondaryCBCompatible;
 *             fIsProtected = isProtected;
 *             fSurfaceProps = surfaceProps;
 *         }
 *         SkDEBUGCODE(this->validate());
 *     }
 *
 *     sk_sp<GrContextThreadSafeProxy> fContextInfo;
 *     size_t                          fCacheMaxResourceBytes;
 *
 *     SkImageInfo                     fImageInfo;
 *     GrBackendFormat                 fBackendFormat;
 *     GrSurfaceOrigin                 fOrigin;
 *     int                             fSampleCnt;
 *     Textureable                     fIsTextureable;
 *     skgpu::Mipmapped                fIsMipmapped;
 *     UsesGLFBO0                      fUsesGLFBO0;
 *     VkRTSupportsInputAttachment     fVkRTSupportsInputAttachment;
 *     VulkanSecondaryCBCompatible     fVulkanSecondaryCBCompatible;
 *     skgpu::Protected                fIsProtected;
 *     SkSurfaceProps                  fSurfaceProps;
 * }
 * ```
 */
public data class GrSurfaceCharacterization public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t                          fCacheMaxResourceBytes
   * ```
   */
  private var fCacheMaxResourceBytes: ULong,
  /**
   * C++ original:
   * ```cpp
   * SkImageInfo                     fImageInfo
   * ```
   */
  private var fImageInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * GrBackendFormat                 fBackendFormat
   * ```
   */
  private var fBackendFormat: Int,
  /**
   * C++ original:
   * ```cpp
   * GrSurfaceOrigin                 fOrigin
   * ```
   */
  private var fOrigin: Int,
  /**
   * C++ original:
   * ```cpp
   * int                             fSampleCnt
   * ```
   */
  private var fSampleCnt: Int,
  /**
   * C++ original:
   * ```cpp
   * Textureable                     fIsTextureable
   * ```
   */
  private var fIsTextureable: Textureable,
  /**
   * C++ original:
   * ```cpp
   * skgpu::Mipmapped                fIsMipmapped
   * ```
   */
  private var fIsMipmapped: Int,
  /**
   * C++ original:
   * ```cpp
   * UsesGLFBO0                      fUsesGLFBO0
   * ```
   */
  private var fUsesGLFBO0: UsesGLFBO0,
  /**
   * C++ original:
   * ```cpp
   * VkRTSupportsInputAttachment     fVkRTSupportsInputAttachment
   * ```
   */
  private var fVkRTSupportsInputAttachment: VkRTSupportsInputAttachment,
  /**
   * C++ original:
   * ```cpp
   * VulkanSecondaryCBCompatible     fVulkanSecondaryCBCompatible
   * ```
   */
  private var fVulkanSecondaryCBCompatible: VulkanSecondaryCBCompatible,
  /**
   * C++ original:
   * ```cpp
   * skgpu::Protected                fIsProtected
   * ```
   */
  private var fIsProtected: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps                  fSurfaceProps
   * ```
   */
  private var fSurfaceProps: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization& operator=(GrSurfaceCharacterization&&) = default
   * ```
   */
  public fun assign(param0: GrSurfaceCharacterization) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization& operator=(const GrSurfaceCharacterization& other) = default
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const GrSurfaceCharacterization& other) const
   * ```
   */
  public fun createResized(width: Int, height: Int): GrSurfaceCharacterization {
    TODO("Implement createResized")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const GrSurfaceCharacterization& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public fun createColorSpace(param0: SkSp<SkColorSpace>): GrSurfaceCharacterization {
    TODO("Implement createColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization createResized(int width, int height) const
   * ```
   */
  public fun createBackendFormat(colorType: SkColorType, backendFormat: GrBackendFormat): GrSurfaceCharacterization {
    TODO("Implement createBackendFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization createColorSpace(sk_sp<SkColorSpace>) const
   * ```
   */
  public fun createFBO0(usesGLFBO0: Boolean): GrSurfaceCharacterization {
    TODO("Implement createFBO0")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization createBackendFormat(SkColorType colorType,
   *                                                   const GrBackendFormat& backendFormat) const
   * ```
   */
  public fun contextInfo(): Int {
    TODO("Implement contextInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceCharacterization createFBO0(bool usesGLFBO0) const
   * ```
   */
  public fun refContextInfo(): Int {
    TODO("Implement refContextInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * GrContextThreadSafeProxy* contextInfo() const { return fContextInfo.get(); }
   * ```
   */
  public fun cacheMaxResourceBytes(): ULong {
    TODO("Implement cacheMaxResourceBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrContextThreadSafeProxy> refContextInfo() const { return fContextInfo; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t cacheMaxResourceBytes() const { return fCacheMaxResourceBytes; }
   * ```
   */
  public fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return kUnknown_SkColorType != fImageInfo.colorType(); }
   * ```
   */
  public fun backendFormat(): Int {
    TODO("Implement backendFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& imageInfo() const { return fImageInfo; }
   * ```
   */
  public fun origin(): Int {
    TODO("Implement origin")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrBackendFormat& backendFormat() const { return fBackendFormat; }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * GrSurfaceOrigin origin() const { return fOrigin; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fImageInfo.dimensions(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fImageInfo.width(); }
   * ```
   */
  public fun colorType(): Int {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fImageInfo.height(); }
   * ```
   */
  public fun sampleCount(): Int {
    TODO("Implement sampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fImageInfo.colorType(); }
   * ```
   */
  public fun isTextureable(): Boolean {
    TODO("Implement isTextureable")
  }

  /**
   * C++ original:
   * ```cpp
   * int sampleCount() const { return fSampleCnt; }
   * ```
   */
  public fun isMipMapped(): Boolean {
    TODO("Implement isMipMapped")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTextureable() const { return Textureable::kYes == fIsTextureable; }
   * ```
   */
  public fun usesGLFBO0(): Boolean {
    TODO("Implement usesGLFBO0")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isMipMapped() const { return skgpu::Mipmapped::kYes == fIsMipmapped; }
   * ```
   */
  public fun vkRTSupportsInputAttachment(): Boolean {
    TODO("Implement vkRTSupportsInputAttachment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usesGLFBO0() const { return UsesGLFBO0::kYes == fUsesGLFBO0; }
   * ```
   */
  public fun vulkanSecondaryCBCompatible(): Boolean {
    TODO("Implement vulkanSecondaryCBCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * bool vkRTSupportsInputAttachment() const {
   *         return VkRTSupportsInputAttachment::kYes == fVkRTSupportsInputAttachment;
   *     }
   * ```
   */
  public fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * bool vulkanSecondaryCBCompatible() const {
   *         return VulkanSecondaryCBCompatible::kYes == fVulkanSecondaryCBCompatible;
   *     }
   * ```
   */
  public fun colorSpace(): Int {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Protected isProtected() const { return fIsProtected; }
   * ```
   */
  public fun refColorSpace(): Int {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* colorSpace() const { return fImageInfo.colorSpace(); }
   * ```
   */
  public fun surfaceProps(): Int {
    TODO("Implement surfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> refColorSpace() const { return fImageInfo.refColorSpace(); }
   * ```
   */
  private fun skDEBUGCODE(param0: () -> Unit): Int {
    TODO("Implement skDEBUGCODE")
  }

  public enum class Textureable {
    kNo,
    kYes,
  }

  public enum class UsesGLFBO0 {
    kNo,
    kYes,
  }

  public enum class VkRTSupportsInputAttachment {
    kNo,
    kYes,
  }

  public enum class VulkanSecondaryCBCompatible {
    kNo,
    kYes,
  }
}
