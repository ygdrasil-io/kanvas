package org.skia.gpu

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.initializer_list
import org.skia.core.SkEnumBitMask
import org.skia.core.SkTextureCompressionType
import org.skia.foundation.SkColorType
import org.skia.math.SkISize
import undefined.ResourceType
import wgpu.Device

/**
 * C++ original:
 * ```cpp
 * class DawnCaps final : public Caps {
 * public:
 *     DawnCaps(const DawnBackendContext&, const ContextOptions&);
 *     ~DawnCaps() override;
 *
 *     bool useAsyncPipelineCreation() const { return fUseAsyncPipelineCreation; }
 *     bool allowScopedErrorChecks() const { return fAllowScopedErrorChecks; }
 *
 *     // If this has no value then loading the resolve texture via a LoadOp is not supported.
 *     std::optional<wgpu::LoadOp> resolveTextureLoadOp() const {
 *         return fSupportedResolveTextureLoadOp;
 *     }
 *     bool supportsPartialLoadResolve() const { return fSupportsPartialLoadResolve; }
 *
 *     bool isSampleCountSupported(TextureFormat, SampleCount requestedSampleCount) const override;
 *     TextureFormat getDepthStencilFormat(SkEnumBitMask<DepthStencilFlags>) const override;
 *
 *     TextureInfo getDefaultAttachmentTextureInfo(AttachmentDesc,
 *                                                 Protected,
 *                                                 Discardable) const override;
 *     TextureInfo getDefaultSampledTextureInfo(SkColorType,
 *                                              Mipmapped,
 *                                              Protected,
 *                                              Renderable) const override;
 *     TextureInfo getTextureInfoForSampledCopy(const TextureInfo&, Mipmapped) const override;
 *     TextureInfo getDefaultCompressedTextureInfo(SkTextureCompressionType,
 *                                                 Mipmapped,
 *                                                 Protected) const override;
 *     TextureInfo getDefaultStorageTextureInfo(SkColorType) const override;
 *     SkISize getDepthAttachmentDimensions(const TextureInfo&,
 *                                          const SkISize colorAttachmentDimensions) const override;
 *     UniqueKey makeGraphicsPipelineKey(const GraphicsPipelineDesc&,
 *                                       const RenderPassDesc&) const override;
 *     bool extractGraphicsDescs(const UniqueKey&,
 *                               GraphicsPipelineDesc*,
 *                               RenderPassDesc*,
 *                               const RendererProvider*) const override;
 *     UniqueKey makeComputePipelineKey(const ComputePipelineDesc&) const override;
 *     ImmutableSamplerInfo getImmutableSamplerInfo(const TextureInfo&) const override;
 *     std::string toString(const ImmutableSamplerInfo&) const override;
 *
 *     bool isRenderable(const TextureInfo&) const override;
 *     bool isStorage(const TextureInfo&) const override;
 *
 *     bool loadOpAffectsMSAAPipelines() const override {
 *         return fSupportedResolveTextureLoadOp.has_value();
 *     }
 *
 *     void buildKeyForTexture(SkISize dimensions,
 *                             const TextureInfo&,
 *                             ResourceType,
 *                             GraphiteResourceKey*) const override;
 *     // Compute render pass desc's key as 32 bits key. The key has room for additional flag which can
 *     // optionally be provided.
 *     uint32_t getRenderPassDescKeyForPipeline(const RenderPassDesc&,
 *                                              bool additionalFlag = false) const;
 *
 *     bool supportsCommandBufferTimestamps() const { return fSupportsCommandBufferTimestamps; }
 *
 *     // Whether we should emulate load/resolve with separate render passes.
 *     // TODO(b/399640773): This is currently used until Dawn supports true partial resolve feature
 *     // that can resolve a MSAA texture to a resolve texture with different size.
 *     bool emulateLoadStoreResolve() const { return fEmulateLoadStoreResolve; }
 *
 *     // Check whether the texture is texturable, ignoring its sample count. This is needed
 *     // instead of isTextureable() because graphite frontend treats multisampled textures as
 *     // non-textureable.
 *     bool isTexturableIgnoreSampleCount(const TextureInfo& info) const;
 *
 * private:
 *     const ColorTypeInfo* getColorTypeInfo(SkColorType, const TextureInfo&) const override;
 *     bool onIsTexturable(const TextureInfo&) const override;
 *     bool supportsWritePixels(const TextureInfo&) const override;
 *     bool supportsReadPixels(const TextureInfo&) const override;
 *     std::pair<SkColorType, bool /*isRGBFormat*/> supportedWritePixelsColorType(
 *             SkColorType dstColorType,
 *             const TextureInfo& dstTextureInfo,
 *             SkColorType srcColorType) const override;
 *     std::pair<SkColorType, bool /*isRGBFormat*/> supportedReadPixelsColorType(
 *             SkColorType srcColorType,
 *             const TextureInfo& srcTextureInfo,
 *             SkColorType dstColorType) const override;
 *
 *     void initCaps(const DawnBackendContext&, const ContextOptions&);
 *     void initShaderCaps(const wgpu::Device&);
 *     void initFormatTable(const wgpu::Device&);
 *
 *     wgpu::TextureFormat getFormatFromColorType(SkColorType colorType) const {
 *         int idx = static_cast<int>(colorType);
 *         return fColorTypeToFormatTable[idx];
 *     }
 *
 *     struct FormatInfo {
 *         uint32_t colorTypeFlags(SkColorType colorType) const {
 *             for (int i = 0; i < fColorTypeInfoCount; ++i) {
 *                 if (fColorTypeInfos[i].fColorType == colorType) {
 *                     return fColorTypeInfos[i].fFlags;
 *                 }
 *             }
 *             return 0;
 *         }
 *
 *         enum {
 *             kTexturable_Flag  = 0x01,
 *             kRenderable_Flag  = 0x02, // Render attachment (color or depth/stencil)
 *             kMSAA_Flag        = 0x04,
 *             kResolve_Flag     = 0x08,
 *             kStorage_Flag     = 0x10,
 *         };
 *         static const uint16_t kAllFlags =
 *                 kTexturable_Flag | kRenderable_Flag | kMSAA_Flag | kResolve_Flag | kStorage_Flag;
 *
 *         uint16_t fFlags = 0;
 *
 *         std::unique_ptr<ColorTypeInfo[]> fColorTypeInfos;
 *         int fColorTypeInfoCount = 0;
 *     };
 *     // Size here must be at least the size of kFormats in DawnCaps.cpp.
 *     static constexpr size_t kFormatCount = 17;
 *     std::array<FormatInfo, kFormatCount> fFormatTable;
 *
 *     static size_t GetFormatIndex(wgpu::TextureFormat format);
 *     const FormatInfo& getFormatInfo(wgpu::TextureFormat format) const {
 *         size_t index = GetFormatIndex(format);
 *         return fFormatTable[index];
 *     }
 *
 *     wgpu::TextureFormat fColorTypeToFormatTable[kSkColorTypeCnt];
 *     void setColorType(SkColorType, std::initializer_list<wgpu::TextureFormat> formats);
 *
 *     // When supported, this value will hold the TransientAttachment usage symbol that is only
 *     // defined in Dawn native builds and not EMSCRIPTEN but this avoids having to #define guard it.
 *     wgpu::TextureUsage fSupportedTransientAttachmentUsage = wgpu::TextureUsage::None;
 *     // When supported this holds the ExpandResolveTexture load op, otherwise holds no value.
 *     std::optional<wgpu::LoadOp> fSupportedResolveTextureLoadOp;
 *     // When 'fSupportedResolveTextureLoadOp' is supported, it by default performs full size expand
 *     // and resolve. With this feature, we can do that partially according to the actual damage
 *     // region.
 *     bool fSupportsPartialLoadResolve = false;
 *
 *     bool fEmulateLoadStoreResolve = false;
 *
 *     bool fUseAsyncPipelineCreation = true;
 *     bool fAllowScopedErrorChecks = true;
 *
 *     bool fSupportsCommandBufferTimestamps = false;
 * }
 * ```
 */
public class DawnCaps public constructor(
  param0: DawnBackendContext,
  param1: ContextOptions,
) : Caps() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kFormatCount = 17
   * ```
   */
  private var fFormatTable: Array<FormatInfo> = TODO("Initialize fFormatTable")

  /**
   * C++ original:
   * ```cpp
   * std::array<FormatInfo, kFormatCount> fFormatTable
   * ```
   */
  private var fColorTypeToFormatTable: Int = TODO("Initialize fColorTypeToFormatTable")

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureFormat fColorTypeToFormatTable
   * ```
   */
  private var fSupportedTransientAttachmentUsage: Int =
      TODO("Initialize fSupportedTransientAttachmentUsage")

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureUsage fSupportedTransientAttachmentUsage
   * ```
   */
  private var fSupportedResolveTextureLoadOp: Int =
      TODO("Initialize fSupportedResolveTextureLoadOp")

  /**
   * C++ original:
   * ```cpp
   * std::optional<wgpu::LoadOp> fSupportedResolveTextureLoadOp
   * ```
   */
  private var fSupportsPartialLoadResolve: Boolean = TODO("Initialize fSupportsPartialLoadResolve")

  /**
   * C++ original:
   * ```cpp
   * bool fSupportsPartialLoadResolve = false
   * ```
   */
  private var fEmulateLoadStoreResolve: Boolean = TODO("Initialize fEmulateLoadStoreResolve")

  /**
   * C++ original:
   * ```cpp
   * bool fEmulateLoadStoreResolve = false
   * ```
   */
  private var fUseAsyncPipelineCreation: Boolean = TODO("Initialize fUseAsyncPipelineCreation")

  /**
   * C++ original:
   * ```cpp
   * bool fUseAsyncPipelineCreation = true
   * ```
   */
  private var fAllowScopedErrorChecks: Boolean = TODO("Initialize fAllowScopedErrorChecks")

  /**
   * C++ original:
   * ```cpp
   * bool fAllowScopedErrorChecks = true
   * ```
   */
  private var fSupportsCommandBufferTimestamps: Boolean =
      TODO("Initialize fSupportsCommandBufferTimestamps")

  /**
   * C++ original:
   * ```cpp
   * bool useAsyncPipelineCreation() const { return fUseAsyncPipelineCreation; }
   * ```
   */
  public fun useAsyncPipelineCreation(): Boolean {
    TODO("Implement useAsyncPipelineCreation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowScopedErrorChecks() const { return fAllowScopedErrorChecks; }
   * ```
   */
  public fun allowScopedErrorChecks(): Boolean {
    TODO("Implement allowScopedErrorChecks")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<wgpu::LoadOp> resolveTextureLoadOp() const {
   *         return fSupportedResolveTextureLoadOp;
   *     }
   * ```
   */
  public fun resolveTextureLoadOp(): Int {
    TODO("Implement resolveTextureLoadOp")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsPartialLoadResolve() const { return fSupportsPartialLoadResolve; }
   * ```
   */
  public fun supportsPartialLoadResolve(): Boolean {
    TODO("Implement supportsPartialLoadResolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSampleCountSupported(TextureFormat, SampleCount requestedSampleCount) const override
   * ```
   */
  public override fun isSampleCountSupported(param0: TextureFormat, requestedSampleCount: SampleCount): Boolean {
    TODO("Implement isSampleCountSupported")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureFormat getDepthStencilFormat(SkEnumBitMask<DepthStencilFlags>) const override
   * ```
   */
  public override fun getDepthStencilFormat(param0: SkEnumBitMask<DepthStencilFlags>): Int {
    TODO("Implement getDepthStencilFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureInfo getDefaultAttachmentTextureInfo(AttachmentDesc,
   *                                                 Protected,
   *                                                 Discardable) const override
   * ```
   */
  public override fun getDefaultAttachmentTextureInfo(
    param0: AttachmentDesc,
    param1: Protected,
    param2: Discardable,
  ): Int {
    TODO("Implement getDefaultAttachmentTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureInfo getDefaultSampledTextureInfo(SkColorType,
   *                                              Mipmapped,
   *                                              Protected,
   *                                              Renderable) const override
   * ```
   */
  public override fun getDefaultSampledTextureInfo(
    param0: SkColorType,
    param1: Mipmapped,
    param2: Protected,
    param3: Renderable,
  ): Int {
    TODO("Implement getDefaultSampledTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureInfo getTextureInfoForSampledCopy(const TextureInfo&, Mipmapped) const override
   * ```
   */
  public override fun getTextureInfoForSampledCopy(param0: TextureInfo, param1: Mipmapped): Int {
    TODO("Implement getTextureInfoForSampledCopy")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureInfo getDefaultCompressedTextureInfo(SkTextureCompressionType,
   *                                                 Mipmapped,
   *                                                 Protected) const override
   * ```
   */
  public override fun getDefaultCompressedTextureInfo(
    param0: SkTextureCompressionType,
    param1: Mipmapped,
    param2: Protected,
  ): Int {
    TODO("Implement getDefaultCompressedTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureInfo getDefaultStorageTextureInfo(SkColorType) const override
   * ```
   */
  public override fun getDefaultStorageTextureInfo(param0: SkColorType): Int {
    TODO("Implement getDefaultStorageTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getDepthAttachmentDimensions(const TextureInfo&,
   *                                          const SkISize colorAttachmentDimensions) const override
   * ```
   */
  public override fun getDepthAttachmentDimensions(param0: TextureInfo, colorAttachmentDimensions: SkISize): Int {
    TODO("Implement getDepthAttachmentDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * UniqueKey makeGraphicsPipelineKey(const GraphicsPipelineDesc&,
   *                                       const RenderPassDesc&) const override
   * ```
   */
  public override fun makeGraphicsPipelineKey(param0: GraphicsPipelineDesc, param1: RenderPassDesc): Int {
    TODO("Implement makeGraphicsPipelineKey")
  }

  /**
   * C++ original:
   * ```cpp
   * bool extractGraphicsDescs(const UniqueKey&,
   *                               GraphicsPipelineDesc*,
   *                               RenderPassDesc*,
   *                               const RendererProvider*) const override
   * ```
   */
  public override fun extractGraphicsDescs(
    param0: UniqueKey,
    param1: GraphicsPipelineDesc?,
    param2: RenderPassDesc?,
    param3: RendererProvider?,
  ): Boolean {
    TODO("Implement extractGraphicsDescs")
  }

  /**
   * C++ original:
   * ```cpp
   * UniqueKey makeComputePipelineKey(const ComputePipelineDesc&) const override
   * ```
   */
  public override fun makeComputePipelineKey(param0: ComputePipelineDesc): Int {
    TODO("Implement makeComputePipelineKey")
  }

  /**
   * C++ original:
   * ```cpp
   * ImmutableSamplerInfo getImmutableSamplerInfo(const TextureInfo&) const override
   * ```
   */
  public override fun getImmutableSamplerInfo(param0: TextureInfo): Int {
    TODO("Implement getImmutableSamplerInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string toString(const ImmutableSamplerInfo&) const override
   * ```
   */
  public override fun toString(param0: ImmutableSamplerInfo): Int {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRenderable(const TextureInfo&) const override
   * ```
   */
  public override fun isRenderable(param0: TextureInfo): Boolean {
    TODO("Implement isRenderable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isStorage(const TextureInfo&) const override
   * ```
   */
  public override fun isStorage(param0: TextureInfo): Boolean {
    TODO("Implement isStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool loadOpAffectsMSAAPipelines() const override {
   *         return fSupportedResolveTextureLoadOp.has_value();
   *     }
   * ```
   */
  public override fun loadOpAffectsMSAAPipelines(): Boolean {
    TODO("Implement loadOpAffectsMSAAPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * void buildKeyForTexture(SkISize dimensions,
   *                             const TextureInfo&,
   *                             ResourceType,
   *                             GraphiteResourceKey*) const override
   * ```
   */
  public override fun buildKeyForTexture(
    dimensions: SkISize,
    param1: TextureInfo,
    param2: ResourceType,
    param3: GraphiteResourceKey?,
  ) {
    TODO("Implement buildKeyForTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getRenderPassDescKeyForPipeline(const RenderPassDesc&,
   *                                              bool additionalFlag = false) const
   * ```
   */
  public fun getRenderPassDescKeyForPipeline(param0: RenderPassDesc, additionalFlag: Boolean = TODO()): UInt {
    TODO("Implement getRenderPassDescKeyForPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsCommandBufferTimestamps() const { return fSupportsCommandBufferTimestamps; }
   * ```
   */
  public fun supportsCommandBufferTimestamps(): Boolean {
    TODO("Implement supportsCommandBufferTimestamps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool emulateLoadStoreResolve() const { return fEmulateLoadStoreResolve; }
   * ```
   */
  public fun emulateLoadStoreResolve(): Boolean {
    TODO("Implement emulateLoadStoreResolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTexturableIgnoreSampleCount(const TextureInfo& info) const
   * ```
   */
  public fun isTexturableIgnoreSampleCount(info: TextureInfo): Boolean {
    TODO("Implement isTexturableIgnoreSampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const ColorTypeInfo* getColorTypeInfo(SkColorType, const TextureInfo&) const override
   * ```
   */
  public override fun getColorTypeInfo(param0: SkColorType, param1: TextureInfo): Int {
    TODO("Implement getColorTypeInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onIsTexturable(const TextureInfo&) const override
   * ```
   */
  public override fun onIsTexturable(param0: TextureInfo): Boolean {
    TODO("Implement onIsTexturable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsWritePixels(const TextureInfo&) const override
   * ```
   */
  public override fun supportsWritePixels(param0: TextureInfo): Boolean {
    TODO("Implement supportsWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsReadPixels(const TextureInfo&) const override
   * ```
   */
  public override fun supportsReadPixels(param0: TextureInfo): Boolean {
    TODO("Implement supportsReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<SkColorType, bool /*isRGBFormat*/> supportedWritePixelsColorType(
   *             SkColorType dstColorType,
   *             const TextureInfo& dstTextureInfo,
   *             SkColorType srcColorType) const override
   * ```
   */
  public override fun supportedWritePixelsColorType(
    dstColorType: SkColorType,
    dstTextureInfo: TextureInfo,
    srcColorType: SkColorType,
  ): Int {
    TODO("Implement supportedWritePixelsColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<SkColorType, bool /*isRGBFormat*/> supportedReadPixelsColorType(
   *             SkColorType srcColorType,
   *             const TextureInfo& srcTextureInfo,
   *             SkColorType dstColorType) const override
   * ```
   */
  public override fun supportedReadPixelsColorType(
    srcColorType: SkColorType,
    srcTextureInfo: TextureInfo,
    dstColorType: SkColorType,
  ): Int {
    TODO("Implement supportedReadPixelsColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * void initCaps(const DawnBackendContext&, const ContextOptions&)
   * ```
   */
  private fun initCaps(param0: DawnBackendContext, param1: ContextOptions) {
    TODO("Implement initCaps")
  }

  /**
   * C++ original:
   * ```cpp
   * void initShaderCaps(const wgpu::Device&)
   * ```
   */
  private fun initShaderCaps(param0: Device) {
    TODO("Implement initShaderCaps")
  }

  /**
   * C++ original:
   * ```cpp
   * void initFormatTable(const wgpu::Device&)
   * ```
   */
  private fun initFormatTable(param0: Device) {
    TODO("Implement initFormatTable")
  }

  /**
   * C++ original:
   * ```cpp
   * wgpu::TextureFormat getFormatFromColorType(SkColorType colorType) const {
   *         int idx = static_cast<int>(colorType);
   *         return fColorTypeToFormatTable[idx];
   *     }
   * ```
   */
  private fun getFormatFromColorType(colorType: SkColorType): Int {
    TODO("Implement getFormatFromColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * const FormatInfo& getFormatInfo(wgpu::TextureFormat format) const {
   *         size_t index = GetFormatIndex(format);
   *         return fFormatTable[index];
   *     }
   * ```
   */
  private fun getFormatInfo(format: wgpu.TextureFormat): FormatInfo {
    TODO("Implement getFormatInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void setColorType(SkColorType, std::initializer_list<wgpu::TextureFormat> formats)
   * ```
   */
  private fun setColorType(param0: SkColorType, formats: initializer_list<wgpu.TextureFormat>) {
    TODO("Implement setColorType")
  }

  public data class FormatInfo public constructor(
    public var fFlags: UShort,
    public var fColorTypeInfos: Int,
    public var fColorTypeInfoCount: Int,
  ) {
    public fun colorTypeFlags(colorType: SkColorType): UInt {
      TODO("Implement colorTypeFlags")
    }

    public companion object {
      public val kTexturableFlag: Int = TODO("Initialize kTexturableFlag")

      public val kRenderableFlag: Int = TODO("Initialize kRenderableFlag")

      public val kMSAAFlag: Int = TODO("Initialize kMSAAFlag")

      public val kResolveFlag: Int = TODO("Initialize kResolveFlag")

      public val kStorageFlag: Int = TODO("Initialize kStorageFlag")

      public val kAllFlags: UShort = TODO("Initialize kAllFlags")
    }
  }

  public companion object {
    private val kFormatCount: ULong = TODO("Initialize kFormatCount")

    /**
     * C++ original:
     * ```cpp
     * static size_t GetFormatIndex(wgpu::TextureFormat format)
     * ```
     */
    private fun getFormatIndex(format: wgpu.TextureFormat): ULong {
      TODO("Implement getFormatIndex")
    }
  }
}
