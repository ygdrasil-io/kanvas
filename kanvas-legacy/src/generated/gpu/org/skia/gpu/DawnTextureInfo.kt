package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import undefined.WGPUTexture
import wgpu.TextureAspect
import wgpu.TextureFormat
import wgpu.TextureUsage
import wgpu.YCbCrVkDescriptor

/**
 * C++ original:
 * ```cpp
 * class SK_API DawnTextureInfo final : public TextureInfo::Data {
 * public:
 *     // wgpu::TextureDescriptor properties
 *     wgpu::TextureFormat fFormat = wgpu::TextureFormat::Undefined;
 *     // `fViewFormat` for multiplanar formats corresponds to the plane TextureView's format.
 *     wgpu::TextureFormat fViewFormat = wgpu::TextureFormat::Undefined;
 *     wgpu::TextureUsage fUsage = wgpu::TextureUsage::None;
 *     // TODO(b/308944094): Migrate aspect information to BackendTextureViews.
 *     wgpu::TextureAspect fAspect = wgpu::TextureAspect::All;
 *     uint32_t fSlice = 0;
 *
 * #if !defined(__EMSCRIPTEN__)
 *     // The descriptor of the YCbCr info (if any) for this texture. Dawn's YCbCr
 *     // sampling will be used for this texture if this info is set. Setting the
 *     // info is supported only on Android and only if using Vulkan as the
 *     // underlying GPU driver.
 *     wgpu::YCbCrVkDescriptor fYcbcrVkDescriptor = {};
 * #endif
 *
 *     wgpu::TextureFormat getViewFormat() const {
 *         return fViewFormat != wgpu::TextureFormat::Undefined ? fViewFormat : fFormat;
 *     }
 *
 *     DawnTextureInfo() = default;
 *
 *     explicit DawnTextureInfo(WGPUTexture texture);
 *
 *     DawnTextureInfo(SampleCount sampleCount,
 *                     Mipmapped mipmapped,
 *                     wgpu::TextureFormat format,
 *                     wgpu::TextureUsage usage,
 *                     wgpu::TextureAspect aspect)
 *             : DawnTextureInfo(sampleCount,
 *                               mipmapped,
 *                               /*format=*/format,
 *                               /*viewFormat=*/format,
 *                               usage,
 *                               aspect,
 *                               /*slice=*/0) {}
 *
 *     DawnTextureInfo(SampleCount sampleCount,
 *                     Mipmapped mipmapped,
 *                     wgpu::TextureFormat format,
 *                     wgpu::TextureFormat viewFormat,
 *                     wgpu::TextureUsage usage,
 *                     wgpu::TextureAspect aspect,
 *                     uint32_t slice)
 *             : Data(sampleCount, mipmapped)
 *             , fFormat(format)
 *             , fViewFormat(viewFormat)
 *             , fUsage(usage)
 *             , fAspect(aspect)
 *             , fSlice(slice) {}
 *
 * #if !defined(__EMSCRIPTEN__)
 *     DawnTextureInfo(SampleCount sampleCount,
 *                     Mipmapped mipmapped,
 *                     wgpu::TextureFormat format,
 *                     wgpu::TextureFormat viewFormat,
 *                     wgpu::TextureUsage usage,
 *                     wgpu::TextureAspect aspect,
 *                     uint32_t slice,
 *                     wgpu::YCbCrVkDescriptor ycbcrVkDescriptor)
 *             : Data(sampleCount, mipmapped)
 *             , fFormat(format)
 *             , fViewFormat(viewFormat)
 *             , fUsage(usage)
 *             , fAspect(aspect)
 *             , fSlice(slice)
 *             , fYcbcrVkDescriptor(ycbcrVkDescriptor) {}
 * #endif
 *
 * private:
 *     friend class TextureInfo;
 *     friend class TextureInfoPriv;
 *
 *     // Non-virtual template API for TextureInfo::Data accessed directly when backend type is known.
 *     static constexpr skgpu::BackendApi kBackend = skgpu::BackendApi::kDawn;
 *
 *     Protected isProtected() const { return Protected::kNo; }
 *     TextureFormat viewFormat() const;
 *
 *     // Virtual API when the specific backend type is not available.
 *     SkString toBackendString() const override;
 *
 *     void copyTo(TextureInfo::AnyTextureInfoData& dstData) const override {
 *         dstData.emplace<DawnTextureInfo>(*this);
 *     }
 *     bool isCompatible(const TextureInfo& that, bool requireExact) const override;
 * }
 * ```
 */
public class DawnTextureInfo public constructor() : TextureInfo.Data() {
  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureFormat fFormat
   * ```
   */
  public var fFormat: Int = TODO("Initialize fFormat")

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureFormat fViewFormat
   * ```
   */
  public var fViewFormat: Int = TODO("Initialize fViewFormat")

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureUsage fUsage
   * ```
   */
  public var fUsage: Int = TODO("Initialize fUsage")

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureAspect fAspect
   * ```
   */
  public var fAspect: Int = TODO("Initialize fAspect")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fSlice
   * ```
   */
  public var fSlice: Int = TODO("Initialize fSlice")

  /**
   * C++ original:
   * ```cpp
   * wgpu::YCbCrVkDescriptor fYcbcrVkDescriptor
   * ```
   */
  public var fYcbcrVkDescriptor: Int = TODO("Initialize fYcbcrVkDescriptor")

  /**
   * C++ original:
   * ```cpp
   * DawnTextureInfo() = default
   * ```
   */
  public constructor(texture: WGPUTexture) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit DawnTextureInfo(WGPUTexture texture)
   * ```
   */
  public constructor(
    sampleCount: SampleCount,
    mipmapped: Mipmapped,
    format: TextureFormat,
    usage: TextureUsage,
    aspect: TextureAspect,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * DawnTextureInfo(SampleCount sampleCount,
   *                     Mipmapped mipmapped,
   *                     wgpu::TextureFormat format,
   *                     wgpu::TextureUsage usage,
   *                     wgpu::TextureAspect aspect)
   *             : DawnTextureInfo(sampleCount,
   *                               mipmapped,
   *                               /*format=*/format,
   *                               /*viewFormat=*/format,
   *                               usage,
   *                               aspect,
   *                               /*slice=*/0) {}
   * ```
   */
  public constructor(
    sampleCount: SampleCount,
    mipmapped: Mipmapped,
    format: TextureFormat,
    viewFormat: TextureFormat,
    usage: TextureUsage,
    aspect: TextureAspect,
    slice: UInt,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * DawnTextureInfo(SampleCount sampleCount,
   *                     Mipmapped mipmapped,
   *                     wgpu::TextureFormat format,
   *                     wgpu::TextureFormat viewFormat,
   *                     wgpu::TextureUsage usage,
   *                     wgpu::TextureAspect aspect,
   *                     uint32_t slice)
   *             : Data(sampleCount, mipmapped)
   *             , fFormat(format)
   *             , fViewFormat(viewFormat)
   *             , fUsage(usage)
   *             , fAspect(aspect)
   *             , fSlice(slice) {}
   * ```
   */
  public constructor(
    sampleCount: SampleCount,
    mipmapped: Mipmapped,
    format: TextureFormat,
    viewFormat: TextureFormat,
    usage: TextureUsage,
    aspect: TextureAspect,
    slice: UInt,
    ycbcrVkDescriptor: YCbCrVkDescriptor,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureFormat getViewFormat() const {
   *         return fViewFormat != wgpu::TextureFormat::Undefined ? fViewFormat : fFormat;
   *     }
   * ```
   */
  public fun getViewFormat(): Int {
    TODO("Implement getViewFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const { return Protected::kNo; }
   * ```
   */
  private fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureFormat viewFormat() const
   * ```
   */
  private fun viewFormat(): Int {
    TODO("Implement viewFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toBackendString() const override
   * ```
   */
  public override fun toBackendString(): Int {
    TODO("Implement toBackendString")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyTo(TextureInfo::AnyTextureInfoData& dstData) const override {
   *         dstData.emplace<DawnTextureInfo>(*this);
   *     }
   * ```
   */
  public override fun copyTo(dstData: TextureInfo.AnyTextureInfoData) {
    TODO("Implement copyTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCompatible(const TextureInfo& that, bool requireExact) const override
   * ```
   */
  public override fun isCompatible(that: TextureInfo, requireExact: Boolean): Boolean {
    TODO("Implement isCompatible")
  }

  public companion object {
    private val kBackend: Int = TODO("Initialize kBackend")
  }
}
