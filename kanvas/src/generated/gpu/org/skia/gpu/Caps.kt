package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.SkEnumBitMask
import org.skia.core.SkTextureCompressionType
import org.skia.foundation.SkColorType
import org.skia.math.SkISize
import org.skia.sksl.ShaderCaps
import undefined.ResourceType

/**
 * C++ original:
 * ```cpp
 * class Caps {
 * public:
 *     virtual ~Caps();
 *
 *     const SkSL::ShaderCaps* shaderCaps() const { return fShaderCaps.get(); }
 *
 *     sk_sp<SkCapabilities> capabilities() const;
 *
 * #if defined(GPU_TEST_UTILS)
 *     std::string_view deviceName() const { return fDeviceName; }
 *
 *     std::optional<PathRendererStrategy> requestedPathRendererStrategy() const {
 *         return fRequestedPathRendererStrategy;
 *     }
 * #endif
 *
 *     /**
 *      * TODO(b/390473370): Once backends initialize a Caps-level format table, these will not need
 *      * to be virtual anymore:
 *      */
 *     virtual bool isSampleCountSupported(TextureFormat, SampleCount) const = 0;
 *     /* Return the TextureFormat that satisfies `dsFlags`. */
 *     virtual TextureFormat getDepthStencilFormat(SkEnumBitMask<DepthStencilFlags>) const = 0;
 *
 *     virtual TextureInfo getDefaultAttachmentTextureInfo(AttachmentDesc,
 *                                                         Protected,
 *                                                         Discardable) const = 0;
 *
 *     virtual TextureInfo getDefaultSampledTextureInfo(SkColorType,
 *                                                      Mipmapped,
 *                                                      Protected,
 *                                                      Renderable) const = 0;
 *
 *     virtual TextureInfo getTextureInfoForSampledCopy(const TextureInfo&,
 *                                                      Mipmapped) const = 0;
 *
 *     virtual TextureInfo getDefaultCompressedTextureInfo(SkTextureCompressionType,
 *                                                         Mipmapped,
 *                                                         Protected) const = 0;
 *
 *     virtual TextureInfo getDefaultStorageTextureInfo(SkColorType) const = 0;
 *
 *     /* Get required depth attachment dimensions for a givin color attachment info and dimensions. */
 *     virtual SkISize getDepthAttachmentDimensions(const TextureInfo&,
 *                                                  const SkISize colorAttachmentDimensions) const;
 *
 *     virtual UniqueKey makeGraphicsPipelineKey(const GraphicsPipelineDesc&,
 *                                               const RenderPassDesc&) const = 0;
 *     virtual UniqueKey makeComputePipelineKey(const ComputePipelineDesc&) const = 0;
 *
 *
 *     virtual bool extractGraphicsDescs(const UniqueKey&,
 *                                       GraphicsPipelineDesc*,
 *                                       RenderPassDesc*,
 *                                       const RendererProvider*) const { return false; }
 *
 *     bool areColorTypeAndTextureInfoCompatible(SkColorType, const TextureInfo&) const;
 *
 *     // Tries to return a sample count > 1 if needing MSAA to render into the target specification.
 *     // If the target is already multisampled, it will be that count; otherwise it will be the
 *     // highest supported sample count less than the configured max internal sample count.
 *     //
 *     // NOTE: If avoidMSAA() is true (either from ContextOptions or driver workarounds), the max
 *     // internal sample count is 1. In this case getCompatibleMSAASampleCount() returns k1 for single
 *     // sampled targets to show MSAA isn't supported.
 *     SampleCount getCompatibleMSAASampleCount(const TextureInfo&) const;
 *
 *     bool isTexturable(const TextureInfo&) const;
 *     virtual bool isRenderable(const TextureInfo&) const = 0;
 *     virtual bool isStorage(const TextureInfo&) const = 0;
 *
 *     virtual bool loadOpAffectsMSAAPipelines() const { return false; }
 *
 *     int maxTextureSize() const { return fMaxTextureSize; }
 *
 *     bool avoidMSAA() const {
 *         // Publicly, treat avoiding MSAA due to device issues or due to client option equivalently.
 *         return fAvoidMSAA || fMaxInternalSampleCount == SampleCount::k1;
 *     }
 *
 *     /**
 *      * Returns the maximum number of varyings allowed in a render pipeline. Note that this is the
 *      * number of varying variables, not the total number of varying scalars.
 *      */
 *     int maxVaryings() const { return fMaxVaryings; }
 *
 *     virtual void buildKeyForTexture(SkISize dimensions,
 *                                     const TextureInfo&,
 *                                     ResourceType,
 *                                     GraphiteResourceKey*) const = 0;
 *
 *     const ResourceBindingRequirements& resourceBindingRequirements() const {
 *         return fResourceBindingReqs;
 *     }
 *
 *     /**
 *      * Returns the required alignment in bytes for the offset into a uniform buffer when binding it
 *      * to a draw.
 *      */
 *     size_t requiredUniformBufferAlignment() const { return fRequiredUniformBufferAlignment; }
 *
 *     /**
 *      * Returns the required alignment in bytes for the offset into a storage buffer when binding it
 *      * to a draw.
 *      */
 *     size_t requiredStorageBufferAlignment() const { return fRequiredStorageBufferAlignment; }
 *
 *     /**
 *      * Returns the required alignment in bytes for the offset and size of copies involving a buffer.
 *      */
 *     size_t requiredTransferBufferAlignment() const { return fRequiredTransferBufferAlignment; }
 *
 *     /* Returns the aligned rowBytes when transferring to or from a Texture */
 *     size_t getAlignedTextureDataRowBytes(size_t rowBytes) const {
 *         return SkAlignTo(rowBytes, fTextureDataRowBytesAlignment);
 *     }
 *
 *     /**
 *      * Backends can optionally override this method to return meaningful sampler conversion info.
 *      * By default, simply return a default ImmutableSamplerInfo (e.g. no immutable sampler).
 *      */
 *     virtual ImmutableSamplerInfo getImmutableSamplerInfo(const TextureInfo&) const {
 *         return {};
 *     }
 *
 *     /* Returns a compressed label describing the immutable sampler for the Pipeline label */
 *     virtual std::string toString(const ImmutableSamplerInfo&) const { return ""; }
 *
 *     /**
 *      * Backends may have restrictions on what types of textures support Device::writePixels().
 *      * If this returns false then the caller should implement a fallback where a temporary texture
 *      * is created, pixels are written to it, and then that is copied or drawn into the surface.
 *      */
 *     virtual bool supportsWritePixels(const TextureInfo&) const = 0;
 *
 *     /**
 *      * Backends may have restrictions on what types of textures support Device::readPixels().
 *      * If this returns false then the caller should implement a fallback where a temporary texture
 *      * is created, the original texture is copied or drawn into it, and then pixels read from
 *      * the temporary texture.
 *      */
 *     virtual bool supportsReadPixels(const TextureInfo&) const = 0;
 *
 *     /**
 *      * Given a dst pixel config and a src color type what color type must the caller coax the
 *      * the data into in order to use writePixels.
 *      *
 *      * We currently don't have an SkColorType for a 3 channel RGB format. Additionally the current
 *      * implementation of raster pipeline requires power of 2 channels, so it is not easy to add such
 *      * an SkColorType. Thus we need to check for data that is 3 channels using the isRGBFormat
 *      * return value and handle it manually
 *      */
 *     virtual std::pair<SkColorType, bool /*isRGB888Format*/> supportedWritePixelsColorType(
 *             SkColorType dstColorType,
 *             const TextureInfo& dstTextureInfo,
 *             SkColorType srcColorType) const = 0;
 *
 *     /**
 *      * Given a src surface's color type and its texture info as well as a color type the caller
 *      * would like read into, this provides a legal color type that the caller can use for
 *      * readPixels. The returned color type may differ from the passed dstColorType, in
 *      * which case the caller must convert the read pixel data (see GrConvertPixels). When converting
 *      * to dstColorType the swizzle in the returned struct should be applied. The caller must check
 *      * the returned color type for kUnknown.
 *      *
 *      * We currently don't have an SkColorType for a 3 channel RGB format. Additionally the current
 *      * implementation of raster pipeline requires power of 2 channels, so it is not easy to add such
 *      * an SkColorType. Thus we need to check for data that is 3 channels using the isRGBFormat
 *      * return value and handle it manually
 *      */
 *     virtual std::pair<SkColorType, bool /*isRGBFormat*/> supportedReadPixelsColorType(
 *             SkColorType srcColorType,
 *             const TextureInfo& srcTextureInfo,
 *             SkColorType dstColorType) const = 0;
 *
 *     /**
 *      * Checks whether the passed color type is renderable. If so, the same color type is passed
 *      * back. If not, provides an alternative (perhaps lower bit depth and/or unorm instead of float)
 *      * color type that is supported or kUnknown if there no renderable fallback format.
 *      */
 *     SkColorType getRenderableColorType(SkColorType) const;
 *
 *     /**
 *      * Determines the orientation of the NDC coordinates emitted by the vertex stage relative to
 *      * both Skia's presumed top-left Y-down system and the viewport coordinates (which are also
 *      * always top-left, Y-down for all supported backends).)
 *      *
 *      * If true is returned, then (-1,-1) in normalized device coords maps to the top-left of the
 *      * configured viewport and positive Y points down. This aligns with Skia's conventions.
 *      * If false is returned, then (-1,-1) in NDC maps to the bottom-left of the viewport and
 *      * positive Y points up (so NDC is flipped relative to sk_Position and the viewport coords).
 *      *
 *      * There is no backend difference in handling the X axis so it's assumed -1 maps to the left
 *      * edge and +1 maps to the right edge.
 *      */
 *     bool ndcYAxisPointsDown() const { return fNDCYAxisPointsDown; }
 *
 *     bool clampToBorderSupport() const { return fClampToBorderSupport; }
 *
 *     bool protectedSupport() const { return fProtectedSupport; }
 *
 *     /* Supports BackendSemaphores */
 *     bool semaphoreSupport() const { return fSemaphoreSupport; }
 *
 *     /* If false then calling Context::submit with SyncToCpu::kYes is an error. */
 *     bool allowCpuSync() const { return fAllowCpuSync; }
 *
 *     /* Returns whether storage buffers are supported and to be preferred over uniform buffers. */
 *     bool storageBufferSupport() const { return fStorageBufferSupport; }
 *
 *     /**
 *      * The gradient buffer is an unsized float array so it is only optimal memory-wise to use it if
 *      * the storage buffer memory layout is std430 or in metal, which is also the only supported
 *      * way the data is packed.
 *      */
 *     bool gradientBufferSupport() const {
 *         return fStorageBufferSupport &&
 *                (fResourceBindingReqs.fStorageBufferLayout == Layout::kStd430 ||
 *                 fResourceBindingReqs.fStorageBufferLayout == Layout::kMetal);
 *     }
 *
 *     /* Returns whether a draw buffer can be mapped. */
 *     bool drawBufferCanBeMapped() const { return fDrawBufferCanBeMapped; }
 *
 * #if defined(GPU_TEST_UTILS)
 *     bool drawBufferCanBeMappedForReadback() const { return fDrawBufferCanBeMappedForReadback; }
 * #endif
 *
 *     /**
 *      * Returns whether using Buffer::asyncMap() must be used to map buffers. map() may only be
 *      * called after asyncMap() is called and will fail if the asynchronous map is not complete. This
 *      * excludes premapped buffers for which map() can be called freely until the first unmap() call.
 *      */
 *     bool bufferMapsAreAsync() const { return fBufferMapsAreAsync; }
 *
 *     /* Returns whether multisampled render to single sampled is supported. */
 *     bool msaaRenderToSingleSampledSupport() const { return fMSAARenderToSingleSampledSupport; }
 *
 *     /* Returns whether multisampled render to single sampled is supported for a given texture. */
 *     virtual bool msaaTextureRenderToSingleSampledSupport(const TextureInfo& info) const {
 *         return this->msaaRenderToSingleSampledSupport();
 *     }
 *
 *     /**
 *      * Returns whether a render pass can have MSAA/depth/stencil attachments and a resolve
 *      * attachment with mismatched sizes. Note: the MSAA attachment and the depth/stencil attachment
 *      * still need to match their sizes.
 *      * This also implies supporting partial load/resolve.
 *      */
 *     bool differentResolveAttachmentSizeSupport() const {
 *         return fDifferentResolveAttachmentSizeSupport;
 *     }
 *
 *     /* Returns whether compute shaders are supported. */
 *     bool computeSupport() const { return fComputeSupport; }
 *
 *     /**
 *      * Returns true if the given backend supports importing AHardwareBuffers. This will only
 *      * ever be supported on Android devices with API level >= 26.
 *      */
 *     bool supportsAHardwareBufferImages() const { return fSupportsAHardwareBufferImages; }
 *
 *     /**
 *      * Enum representing the capabilities of the fixed function blend unit.
 *      */
 *     enum BlendEquationSupport : uint8_t {
 *         kBasic = 0,           /* Default bare minimum support. Allows selecting the operator that
 *                                  combines src + dst terms.*/
 *         kAdvancedNoncoherent, /* Additional fixed function support for specific SVG/PDF blend modes.
 *                                  Requires blend barriers.*/
 *         kAdvancedCoherent     /* Advanced blend equation support that does not require blend
 *                                  barriers and permits overlap.*/
 *     };
 *     /**
 *      * Return the level of hardware advanced blend mode support.
 *      */
 *     BlendEquationSupport blendEquationSupport() const { return fBlendEqSupport; }
 *     /**
 *      * Simple helper for indicating whether the hardware supports advanced blend modes at all
 *      * (coherent or noncoherent).
 *      */
 *     bool supportsHardwareAdvancedBlending() const {
 *         return fBlendEqSupport > BlendEquationSupport::kBasic;
 *     }
 *
 *     /**
 *      * Returns the skgpu::Swizzle to use when sampling or reading back from a texture with the
 *      * passed in SkColorType and TextureInfo.
 *      */
 *     skgpu::Swizzle getReadSwizzle(SkColorType, const TextureInfo&) const;
 *
 *     /**
 *      * Returns the skgpu::Swizzle to use when writing colors to a surface with the passed in
 *      * SkColorType and TextureInfo.
 *      */
 *     skgpu::Swizzle getWriteSwizzle(SkColorType, const TextureInfo&) const;
 *
 *     /**
 *      * Includes the following dynamic state:
 *      *
 *      * * Line width, depth bias, depth bounds, stencil compare mask, stencil write mask and stencil
 *      *   reference.
 *      *   This set corresponds to Vulkan 1.0 dynamic state.  Blend constants does not depend on this
 *      *   flag as it is always dynamic with all graphite backends.
 *      *
 *      * * Depth test enable, depth write enable, depth compare op, depth bounds test enable, depth
 *      *   bias enable, stencil test enable and stencil op.
 *      *   This set corresponds to depth and stencil related state from VK_EXT_extended_dynamic_state
 *      *   and VK_EXT_extended_dynamic_state2.
 *      *
 *      * * Primitive topology and primitive restart enable.
 *      *   Note that the primitive topology _class_ is not dynamic.
 *      *   This set corresponds to input assembly state from VK_EXT_extended_dynamic_state and
 *      *   VK_EXT_extended_dynamic_state2.
 *      *
 *      * * Cull mode, front face and rasterizer discard.
 *      *   This set corresponds to rasterizer state from VK_EXT_extended_dynamic_state and
 *      *   VK_EXT_extended_dynamic_state2.
 *      */
 *     bool useBasicDynamicState() const { return fUseBasicDynamicState; }
 *     /**
 *      * Whether all vertex input state is dynamic.
 *      * This set corresponds to state from VK_EXT_vertex_input_dynamic_state.  This state is
 *      * equivalently pulled out of the shaders pipeline via VK_EXT_graphics_pipeline_library
 *      * (usePipelineLibraries()).
 *      */
 *     bool useVertexInputDynamicState() const { return fUseVertexInputDynamicState; }
 *     /**
 *      * Whether VK_EXT_graphics_pipeline_library should be used.  In this case, the "shaders" subset
 *      * of the pipeline is compiled separately, then fast-linked with the vertex input and fragment
 *      * output state to create the final library.  Currently, this is a detail of the Vulkan backend,
 *      * which helps VkPipelineCache hits (because the shaders pipeline hits the cache, and blend
 *      * state is patched in).  However, this is most useful once exposed to the front-end, such that
 *      * it can track the (fewer) shaders pipeline separately, have the complete pipelines point to
 *      * the shaders pipeline, avoid unnecessary cache look ups, and more.  (skbug.com/414645289)
 *      */
 *     bool usePipelineLibraries() const { return fUsePipelineLibraries; }
 *
 *     bool supportsHostImageCopy() const { return fSupportsHostImageCopy; }
 *
 *     skgpu::ShaderErrorHandler* shaderErrorHandler() const { return fShaderErrorHandler; }
 *
 *     /**
 *      * Returns what method of dst read a draw should use for obtaining the dst color. Backends can
 *      * use the default implementation or override this method as needed.
 *      */
 *     virtual DstReadStrategy getDstReadStrategy() const;
 *
 *     float minPathSizeForMSAA() const { return fMinMSAAPathSize; }
 *     float minDistanceFieldFontSize() const { return fMinDistanceFieldFontSize; }
 *     float glyphsAsPathsFontSize() const { return fGlyphsAsPathsFontSize; }
 *
 *     size_t glyphCacheTextureMaximumBytes() const { return fGlyphCacheTextureMaximumBytes; }
 *     int maxPathAtlasTextureSize() const { return fMaxPathAtlasTextureSize; }
 *
 *     bool allowMultipleAtlasTextures() const { return fAllowMultipleAtlasTextures; }
 *     bool supportBilerpFromGlyphAtlas() const { return fSupportBilerpFromGlyphAtlas; }
 *
 *     bool requireOrderedRecordings() const { return fRequireOrderedRecordings; }
 *
 *     /**
 *      * When uploading to a full compressed texture do we need to pad the size out to a multiple of
 *      * the block width and height.
 *      */
 *     bool fullCompressedUploadSizeMustAlignToBlockDims() const {
 *         return fFullCompressedUploadSizeMustAlignToBlockDims;
 *     }
 *
 *     sktext::gpu::SubRunControl getSubRunControl(bool useSDFTForSmallText) const;
 *
 *     bool setBackendLabels() const { return fSetBackendLabels; }
 *
 *     GpuStatsFlags supportedGpuStats() const { return fSupportedGpuStats; }
 *
 * protected:
 *     Caps();
 *
 *     /**
 *      * Subclasses must call this at the end of their init method in order to do final processing on
 *      * the caps.
 *      */
 *     void finishInitialization(const ContextOptions&);
 *
 * #if defined(GPU_TEST_UTILS)
 *     void setDeviceName(std::string n) {
 *         fDeviceName = std::move(n);
 *     }
 * #endif
 *
 *     /* ColorTypeInfo for a specific format. Used in format tables. */
 *     struct ColorTypeInfo {
 *         ColorTypeInfo() = default;
 *         ColorTypeInfo(SkColorType ct, SkColorType transferCt, uint32_t flags,
 *                       skgpu::Swizzle readSwizzle, skgpu::Swizzle writeSwizzle)
 *                 : fColorType(ct)
 *                 , fTransferColorType(transferCt)
 *                 , fFlags(flags)
 *                 , fReadSwizzle(readSwizzle)
 *                 , fWriteSwizzle(writeSwizzle) {}
 *
 *         SkColorType fColorType = kUnknown_SkColorType;
 *         SkColorType fTransferColorType = kUnknown_SkColorType;
 *         enum {
 *             kUploadData_Flag = 0x1,
 *             /**
 *              * Does Graphite itself support rendering to this colorType & format pair. Renderability
 *              * still additionally depends on if the format itself is renderable.
 *              */
 *             kRenderable_Flag = 0x2,
 *         };
 *         uint32_t fFlags = 0;
 *
 *         skgpu::Swizzle fReadSwizzle;
 *         skgpu::Swizzle fWriteSwizzle;
 *     };
 *
 *     int fMaxTextureSize = 0;
 *
 *     size_t fRequiredUniformBufferAlignment = 0;
 *     size_t fRequiredStorageBufferAlignment = 0;
 *     size_t fRequiredTransferBufferAlignment = 0;
 *     size_t fTextureDataRowBytesAlignment = 1;
 *
 *     int fMaxVaryings = 0;
 *
 *     std::unique_ptr<SkSL::ShaderCaps> fShaderCaps;
 *
 *     bool fNDCYAxisPointsDown = false; // Most backends have NDC +Y pointing up
 *     bool fClampToBorderSupport = true;
 *     bool fProtectedSupport = false;
 *     bool fSemaphoreSupport = false;
 *     bool fAllowCpuSync = true;
 *     bool fStorageBufferSupport = false;
 *     bool fDrawBufferCanBeMapped = true;
 *     bool fBufferMapsAreAsync = false;
 *     bool fMSAARenderToSingleSampledSupport = false;
 *     bool fDifferentResolveAttachmentSizeSupport = false;
 *     bool fAvoidMSAA = false;
 *
 *     bool fComputeSupport = false;
 *     bool fSupportsAHardwareBufferImages = false;
 *     bool fFullCompressedUploadSizeMustAlignToBlockDims = false;
 *
 *     // Dynamic state.  The granularity is less fine than Vulkan's, but there is still some
 *     // granularity to allow for some dynamic state to be disabled due to driver bugs without having
 *     // to disable everything.  Eventually, these can be used to create fewer pipelines in the first
 *     // place (b/414645289).
 *     bool fUseBasicDynamicState = false;
 *     bool fUseVertexInputDynamicState = false;
 *     bool fUsePipelineLibraries = false;
 *
 *     // Whether it's possible to upload data to images using the CPU (host) instead of the device.
 *     // Under certain circumstances, it's more efficient to upload data in this way instead of
 *     // through a staging buffer.
 *     bool fSupportsHostImageCopy = false;
 *
 * #if defined(GPU_TEST_UTILS)
 *     bool fDrawBufferCanBeMappedForReadback = true;
 * #endif
 *
 *     ResourceBindingRequirements fResourceBindingReqs;
 *     BlendEquationSupport fBlendEqSupport = BlendEquationSupport::kBasic;
 *
 *     GpuStatsFlags fSupportedGpuStats = GpuStatsFlags::kNone;
 *
 *     //////////////////////////////////////////////////////////////////////////////////////////
 *     // Client-provided Caps
 *
 *     /**
 *      * If present, use this object to report shader compilation failures. If not, report failures
 *      * via SkDebugf and assert.
 *      */
 *     ShaderErrorHandler* fShaderErrorHandler = nullptr;
 *
 * #if defined(GPU_TEST_UTILS)
 *     std::string fDeviceName;
 *     std::optional<PathRendererStrategy> fRequestedPathRendererStrategy;
 * #endif
 *
 *     // NOTE: This is a requested limit, the actual supported sample counts for a particular format
 *     // could be lower or higher.
 *     SampleCount fMaxInternalSampleCount = SampleCount::k4;
 *
 *     size_t fGlyphCacheTextureMaximumBytes = 2048 * 1024 * 4;
 *
 *     float fMinMSAAPathSize = 0;
 *     float fMinDistanceFieldFontSize = 18;
 *     float fGlyphsAsPathsFontSize = 324;
 *
 *     int fMaxPathAtlasTextureSize = 8192;
 *
 *     bool fAllowMultipleAtlasTextures = true;
 *     bool fSupportBilerpFromGlyphAtlas = false;
 *
 *     bool fRequireOrderedRecordings = false;
 *
 *     bool fSetBackendLabels = false;
 *
 * private:
 *     virtual bool onIsTexturable(const TextureInfo&) const = 0;
 *     virtual const ColorTypeInfo* getColorTypeInfo(SkColorType, const TextureInfo&) const = 0;
 *
 *     sk_sp<SkCapabilities> fCapabilities;
 * }
 * ```
 */
public abstract class Caps public constructor() {
  /**
   * C++ original:
   * ```cpp
   * int fMaxTextureSize = 0
   * ```
   */
  protected var fMaxTextureSize: Int = TODO("Initialize fMaxTextureSize")

  /**
   * C++ original:
   * ```cpp
   * size_t fRequiredUniformBufferAlignment
   * ```
   */
  protected var fRequiredUniformBufferAlignment: Int =
      TODO("Initialize fRequiredUniformBufferAlignment")

  /**
   * C++ original:
   * ```cpp
   * size_t fRequiredStorageBufferAlignment
   * ```
   */
  protected var fRequiredStorageBufferAlignment: Int =
      TODO("Initialize fRequiredStorageBufferAlignment")

  /**
   * C++ original:
   * ```cpp
   * size_t fRequiredTransferBufferAlignment
   * ```
   */
  protected var fRequiredTransferBufferAlignment: Int =
      TODO("Initialize fRequiredTransferBufferAlignment")

  /**
   * C++ original:
   * ```cpp
   * size_t fTextureDataRowBytesAlignment
   * ```
   */
  protected var fTextureDataRowBytesAlignment: Int =
      TODO("Initialize fTextureDataRowBytesAlignment")

  /**
   * C++ original:
   * ```cpp
   * int fMaxVaryings = 0
   * ```
   */
  protected var fMaxVaryings: Int = TODO("Initialize fMaxVaryings")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkSL::ShaderCaps> fShaderCaps
   * ```
   */
  protected var fShaderCaps: Int = TODO("Initialize fShaderCaps")

  /**
   * C++ original:
   * ```cpp
   * bool fNDCYAxisPointsDown = false
   * ```
   */
  protected var fNDCYAxisPointsDown: Boolean = TODO("Initialize fNDCYAxisPointsDown")

  /**
   * C++ original:
   * ```cpp
   * bool fClampToBorderSupport = true
   * ```
   */
  protected var fClampToBorderSupport: Boolean = TODO("Initialize fClampToBorderSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fProtectedSupport = false
   * ```
   */
  protected var fProtectedSupport: Boolean = TODO("Initialize fProtectedSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fSemaphoreSupport = false
   * ```
   */
  protected var fSemaphoreSupport: Boolean = TODO("Initialize fSemaphoreSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fAllowCpuSync = true
   * ```
   */
  protected var fAllowCpuSync: Boolean = TODO("Initialize fAllowCpuSync")

  /**
   * C++ original:
   * ```cpp
   * bool fStorageBufferSupport = false
   * ```
   */
  protected var fStorageBufferSupport: Boolean = TODO("Initialize fStorageBufferSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fDrawBufferCanBeMapped = true
   * ```
   */
  protected var fDrawBufferCanBeMapped: Boolean = TODO("Initialize fDrawBufferCanBeMapped")

  /**
   * C++ original:
   * ```cpp
   * bool fBufferMapsAreAsync = false
   * ```
   */
  protected var fBufferMapsAreAsync: Boolean = TODO("Initialize fBufferMapsAreAsync")

  /**
   * C++ original:
   * ```cpp
   * bool fMSAARenderToSingleSampledSupport = false
   * ```
   */
  protected var fMSAARenderToSingleSampledSupport: Boolean =
      TODO("Initialize fMSAARenderToSingleSampledSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fDifferentResolveAttachmentSizeSupport = false
   * ```
   */
  protected var fDifferentResolveAttachmentSizeSupport: Boolean =
      TODO("Initialize fDifferentResolveAttachmentSizeSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fAvoidMSAA = false
   * ```
   */
  protected var fAvoidMSAA: Boolean = TODO("Initialize fAvoidMSAA")

  /**
   * C++ original:
   * ```cpp
   * bool fComputeSupport = false
   * ```
   */
  protected var fComputeSupport: Boolean = TODO("Initialize fComputeSupport")

  /**
   * C++ original:
   * ```cpp
   * bool fSupportsAHardwareBufferImages = false
   * ```
   */
  protected var fSupportsAHardwareBufferImages: Boolean =
      TODO("Initialize fSupportsAHardwareBufferImages")

  /**
   * C++ original:
   * ```cpp
   * bool fFullCompressedUploadSizeMustAlignToBlockDims = false
   * ```
   */
  protected var fFullCompressedUploadSizeMustAlignToBlockDims: Boolean =
      TODO("Initialize fFullCompressedUploadSizeMustAlignToBlockDims")

  /**
   * C++ original:
   * ```cpp
   * bool fUseBasicDynamicState = false
   * ```
   */
  protected var fUseBasicDynamicState: Boolean = TODO("Initialize fUseBasicDynamicState")

  /**
   * C++ original:
   * ```cpp
   * bool fUseVertexInputDynamicState = false
   * ```
   */
  protected var fUseVertexInputDynamicState: Boolean =
      TODO("Initialize fUseVertexInputDynamicState")

  /**
   * C++ original:
   * ```cpp
   * bool fUsePipelineLibraries = false
   * ```
   */
  protected var fUsePipelineLibraries: Boolean = TODO("Initialize fUsePipelineLibraries")

  /**
   * C++ original:
   * ```cpp
   * bool fSupportsHostImageCopy = false
   * ```
   */
  protected var fSupportsHostImageCopy: Boolean = TODO("Initialize fSupportsHostImageCopy")

  /**
   * C++ original:
   * ```cpp
   * bool fDrawBufferCanBeMappedForReadback = true
   * ```
   */
  protected var fDrawBufferCanBeMappedForReadback: Boolean =
      TODO("Initialize fDrawBufferCanBeMappedForReadback")

  /**
   * C++ original:
   * ```cpp
   * ResourceBindingRequirements fResourceBindingReqs
   * ```
   */
  protected var fResourceBindingReqs: ResourceBindingRequirements =
      TODO("Initialize fResourceBindingReqs")

  /**
   * C++ original:
   * ```cpp
   * BlendEquationSupport fBlendEqSupport = BlendEquationSupport::kBasic
   * ```
   */
  protected var fBlendEqSupport: BlendEquationSupport = TODO("Initialize fBlendEqSupport")

  /**
   * C++ original:
   * ```cpp
   * GpuStatsFlags fSupportedGpuStats
   * ```
   */
  protected var fSupportedGpuStats: Int = TODO("Initialize fSupportedGpuStats")

  /**
   * C++ original:
   * ```cpp
   * ShaderErrorHandler* fShaderErrorHandler = nullptr
   * ```
   */
  protected var fShaderErrorHandler: ShaderErrorHandler? = TODO("Initialize fShaderErrorHandler")

  /**
   * C++ original:
   * ```cpp
   * std::string fDeviceName
   * ```
   */
  protected var fDeviceName: Int = TODO("Initialize fDeviceName")

  /**
   * C++ original:
   * ```cpp
   * std::optional<PathRendererStrategy> fRequestedPathRendererStrategy
   * ```
   */
  protected var fRequestedPathRendererStrategy: Int =
      TODO("Initialize fRequestedPathRendererStrategy")

  /**
   * C++ original:
   * ```cpp
   * SampleCount fMaxInternalSampleCount
   * ```
   */
  protected var fMaxInternalSampleCount: Int = TODO("Initialize fMaxInternalSampleCount")

  /**
   * C++ original:
   * ```cpp
   * size_t fGlyphCacheTextureMaximumBytes
   * ```
   */
  protected var fGlyphCacheTextureMaximumBytes: Int =
      TODO("Initialize fGlyphCacheTextureMaximumBytes")

  /**
   * C++ original:
   * ```cpp
   * float fMinMSAAPathSize = 0
   * ```
   */
  protected var fMinMSAAPathSize: Float = TODO("Initialize fMinMSAAPathSize")

  /**
   * C++ original:
   * ```cpp
   * float fMinDistanceFieldFontSize = 18
   * ```
   */
  protected var fMinDistanceFieldFontSize: Float = TODO("Initialize fMinDistanceFieldFontSize")

  /**
   * C++ original:
   * ```cpp
   * float fGlyphsAsPathsFontSize = 324
   * ```
   */
  protected var fGlyphsAsPathsFontSize: Float = TODO("Initialize fGlyphsAsPathsFontSize")

  /**
   * C++ original:
   * ```cpp
   * int fMaxPathAtlasTextureSize = 8192
   * ```
   */
  protected var fMaxPathAtlasTextureSize: Int = TODO("Initialize fMaxPathAtlasTextureSize")

  /**
   * C++ original:
   * ```cpp
   * bool fAllowMultipleAtlasTextures = true
   * ```
   */
  protected var fAllowMultipleAtlasTextures: Boolean =
      TODO("Initialize fAllowMultipleAtlasTextures")

  /**
   * C++ original:
   * ```cpp
   * bool fSupportBilerpFromGlyphAtlas = false
   * ```
   */
  protected var fSupportBilerpFromGlyphAtlas: Boolean =
      TODO("Initialize fSupportBilerpFromGlyphAtlas")

  /**
   * C++ original:
   * ```cpp
   * bool fRequireOrderedRecordings = false
   * ```
   */
  protected var fRequireOrderedRecordings: Boolean = TODO("Initialize fRequireOrderedRecordings")

  /**
   * C++ original:
   * ```cpp
   * bool fSetBackendLabels = false
   * ```
   */
  protected var fSetBackendLabels: Boolean = TODO("Initialize fSetBackendLabels")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCapabilities> fCapabilities
   * ```
   */
  private var fCapabilities: Int = TODO("Initialize fCapabilities")

  /**
   * C++ original:
   * ```cpp
   * const SkSL::ShaderCaps* shaderCaps() const { return fShaderCaps.get(); }
   * ```
   */
  public fun shaderCaps(): ShaderCaps {
    TODO("Implement shaderCaps")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCapabilities> Caps::capabilities() const { return fCapabilities; }
   * ```
   */
  public fun capabilities(): Int {
    TODO("Implement capabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string_view deviceName() const { return fDeviceName; }
   * ```
   */
  public fun deviceName(): Int {
    TODO("Implement deviceName")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<PathRendererStrategy> requestedPathRendererStrategy() const {
   *         return fRequestedPathRendererStrategy;
   *     }
   * ```
   */
  public fun requestedPathRendererStrategy(): Int {
    TODO("Implement requestedPathRendererStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isSampleCountSupported(TextureFormat, SampleCount) const = 0
   * ```
   */
  public abstract fun isSampleCountSupported(param0: TextureFormat, param1: SampleCount): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual TextureFormat getDepthStencilFormat(SkEnumBitMask<DepthStencilFlags>) const = 0
   * ```
   */
  public abstract fun getDepthStencilFormat(param0: SkEnumBitMask<DepthStencilFlags>): TextureFormat

  /**
   * C++ original:
   * ```cpp
   * virtual TextureInfo getDefaultAttachmentTextureInfo(AttachmentDesc,
   *                                                         Protected,
   *                                                         Discardable) const = 0
   * ```
   */
  public abstract fun getDefaultAttachmentTextureInfo(
    param0: AttachmentDesc,
    param1: Protected,
    param2: Discardable,
  ): TextureInfo

  /**
   * C++ original:
   * ```cpp
   * virtual TextureInfo getDefaultSampledTextureInfo(SkColorType,
   *                                                      Mipmapped,
   *                                                      Protected,
   *                                                      Renderable) const = 0
   * ```
   */
  public abstract fun getDefaultSampledTextureInfo(
    param0: SkColorType,
    param1: Mipmapped,
    param2: Protected,
    param3: Renderable,
  ): TextureInfo

  /**
   * C++ original:
   * ```cpp
   * virtual TextureInfo getTextureInfoForSampledCopy(const TextureInfo&,
   *                                                      Mipmapped) const = 0
   * ```
   */
  public abstract fun getTextureInfoForSampledCopy(param0: TextureInfo, param1: Mipmapped): TextureInfo

  /**
   * C++ original:
   * ```cpp
   * virtual TextureInfo getDefaultCompressedTextureInfo(SkTextureCompressionType,
   *                                                         Mipmapped,
   *                                                         Protected) const = 0
   * ```
   */
  public abstract fun getDefaultCompressedTextureInfo(
    param0: SkTextureCompressionType,
    param1: Mipmapped,
    param2: Protected,
  ): TextureInfo

  /**
   * C++ original:
   * ```cpp
   * virtual TextureInfo getDefaultStorageTextureInfo(SkColorType) const = 0
   * ```
   */
  public abstract fun getDefaultStorageTextureInfo(param0: SkColorType): TextureInfo

  /**
   * C++ original:
   * ```cpp
   * SkISize Caps::getDepthAttachmentDimensions(const TextureInfo& textureInfo,
   *                                            const SkISize colorAttachmentDimensions) const {
   *     return colorAttachmentDimensions;
   * }
   * ```
   */
  public open fun getDepthAttachmentDimensions(textureInfo: TextureInfo, colorAttachmentDimensions: SkISize): Int {
    TODO("Implement getDepthAttachmentDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual UniqueKey makeGraphicsPipelineKey(const GraphicsPipelineDesc&,
   *                                               const RenderPassDesc&) const = 0
   * ```
   */
  public abstract fun makeGraphicsPipelineKey(param0: GraphicsPipelineDesc, param1: RenderPassDesc): Int

  /**
   * C++ original:
   * ```cpp
   * virtual UniqueKey makeComputePipelineKey(const ComputePipelineDesc&) const = 0
   * ```
   */
  public abstract fun makeComputePipelineKey(param0: ComputePipelineDesc): Int

  /**
   * C++ original:
   * ```cpp
   * virtual bool extractGraphicsDescs(const UniqueKey&,
   *                                       GraphicsPipelineDesc*,
   *                                       RenderPassDesc*,
   *                                       const RendererProvider*) const { return false; }
   * ```
   */
  public open fun extractGraphicsDescs(
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
   * bool Caps::areColorTypeAndTextureInfoCompatible(SkColorType ct, const TextureInfo& info) const {
   *     // TODO: add SkTextureCompressionType handling
   *     // (can be handled by setting up the colorTypeInfo instead?)
   *
   *     return SkToBool(this->getColorTypeInfo(ct, info));
   * }
   * ```
   */
  public fun areColorTypeAndTextureInfoCompatible(ct: SkColorType, info: TextureInfo): Boolean {
    TODO("Implement areColorTypeAndTextureInfoCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleCount Caps::getCompatibleMSAASampleCount(const TextureInfo& info) const {
   *     if (info.sampleCount() > SampleCount::k1) {
   *         // Use the inherent sample count since it's already MSAA
   *         return info.sampleCount();
   *     } else if (!this->avoidMSAA()) {
   *         // The max internal sample count may be higher than what is universally supported for
   *         // every renderable TextureFormat, but unless avoidMSAA() was true, this should bottom out
   *         // at SampleCount::k4.
   *         TextureFormat format = TextureInfoPriv::ViewFormat(info);
   *         for (SampleCount s = fMaxInternalSampleCount;
   *              s > SampleCount::k1;
   *              s = static_cast<SampleCount>((uint8_t)s >> 1)) {
   *             if (this->isSampleCountSupported(format, s)) {
   *                 return s;
   *             }
   *         }
   *     }
   *
   *     // If we got here, MSAA has been disabled somehow (by ContextOption, driver workaround, or
   *     // no support for a particular TextureFormat).
   *     return SampleCount::k1;
   * }
   * ```
   */
  public fun getCompatibleMSAASampleCount(info: TextureInfo): Int {
    TODO("Implement getCompatibleMSAASampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Caps::isTexturable(const TextureInfo& info) const {
   *     if (info.sampleCount() > SampleCount::k1) {
   *         return false;
   *     }
   *     return this->onIsTexturable(info);
   * }
   * ```
   */
  public fun isTexturable(info: TextureInfo): Boolean {
    TODO("Implement isTexturable")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isRenderable(const TextureInfo&) const = 0
   * ```
   */
  public abstract fun isRenderable(param0: TextureInfo): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isStorage(const TextureInfo&) const = 0
   * ```
   */
  public abstract fun isStorage(param0: TextureInfo): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool loadOpAffectsMSAAPipelines() const { return false; }
   * ```
   */
  public open fun loadOpAffectsMSAAPipelines(): Boolean {
    TODO("Implement loadOpAffectsMSAAPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxTextureSize() const { return fMaxTextureSize; }
   * ```
   */
  public fun maxTextureSize(): Int {
    TODO("Implement maxTextureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool avoidMSAA() const {
   *         // Publicly, treat avoiding MSAA due to device issues or due to client option equivalently.
   *         return fAvoidMSAA || fMaxInternalSampleCount == SampleCount::k1;
   *     }
   * ```
   */
  public fun avoidMSAA(): Boolean {
    TODO("Implement avoidMSAA")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxVaryings() const { return fMaxVaryings; }
   * ```
   */
  public fun maxVaryings(): Int {
    TODO("Implement maxVaryings")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void buildKeyForTexture(SkISize dimensions,
   *                                     const TextureInfo&,
   *                                     ResourceType,
   *                                     GraphiteResourceKey*) const = 0
   * ```
   */
  public abstract fun buildKeyForTexture(
    dimensions: SkISize,
    param1: TextureInfo,
    param2: ResourceType,
    param3: GraphiteResourceKey?,
  )

  /**
   * C++ original:
   * ```cpp
   * const ResourceBindingRequirements& resourceBindingRequirements() const {
   *         return fResourceBindingReqs;
   *     }
   * ```
   */
  public fun resourceBindingRequirements(): ResourceBindingRequirements {
    TODO("Implement resourceBindingRequirements")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t requiredUniformBufferAlignment() const { return fRequiredUniformBufferAlignment; }
   * ```
   */
  public fun requiredUniformBufferAlignment(): Int {
    TODO("Implement requiredUniformBufferAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t requiredStorageBufferAlignment() const { return fRequiredStorageBufferAlignment; }
   * ```
   */
  public fun requiredStorageBufferAlignment(): Int {
    TODO("Implement requiredStorageBufferAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t requiredTransferBufferAlignment() const { return fRequiredTransferBufferAlignment; }
   * ```
   */
  public fun requiredTransferBufferAlignment(): Int {
    TODO("Implement requiredTransferBufferAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getAlignedTextureDataRowBytes(size_t rowBytes) const {
   *         return SkAlignTo(rowBytes, fTextureDataRowBytesAlignment);
   *     }
   * ```
   */
  public fun getAlignedTextureDataRowBytes(rowBytes: ULong): Int {
    TODO("Implement getAlignedTextureDataRowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual ImmutableSamplerInfo getImmutableSamplerInfo(const TextureInfo&) const {
   *         return {};
   *     }
   * ```
   */
  public open fun getImmutableSamplerInfo(param0: TextureInfo): Int {
    TODO("Implement getImmutableSamplerInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::string toString(const ImmutableSamplerInfo&) const { return ""; }
   * ```
   */
  public override fun toString(param0: ImmutableSamplerInfo): Int {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool supportsWritePixels(const TextureInfo&) const = 0
   * ```
   */
  public abstract fun supportsWritePixels(param0: TextureInfo): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool supportsReadPixels(const TextureInfo&) const = 0
   * ```
   */
  public abstract fun supportsReadPixels(param0: TextureInfo): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual std::pair<SkColorType, bool /*isRGB888Format*/> supportedWritePixelsColorType(
   *             SkColorType dstColorType,
   *             const TextureInfo& dstTextureInfo,
   *             SkColorType srcColorType) const = 0
   * ```
   */
  public abstract fun supportedWritePixelsColorType(
    dstColorType: SkColorType,
    dstTextureInfo: TextureInfo,
    srcColorType: SkColorType,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual std::pair<SkColorType, bool /*isRGBFormat*/> supportedReadPixelsColorType(
   *             SkColorType srcColorType,
   *             const TextureInfo& srcTextureInfo,
   *             SkColorType dstColorType) const = 0
   * ```
   */
  public abstract fun supportedReadPixelsColorType(
    srcColorType: SkColorType,
    srcTextureInfo: TextureInfo,
    dstColorType: SkColorType,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * SkColorType Caps::getRenderableColorType(SkColorType ct) const {
   *     do {
   *         auto texInfo = this->getDefaultSampledTextureInfo(ct,
   *                                                           Mipmapped::kNo,
   *                                                           Protected::kNo,
   *                                                           Renderable::kYes);
   *         // We continue to the fallback color type if there is no default renderable format
   *         if (texInfo.isValid() && this->isRenderable(texInfo)) {
   *             return ct;
   *         }
   *         ct = color_type_fallback(ct);
   *     } while (ct != kUnknown_SkColorType);
   *     return kUnknown_SkColorType;
   * }
   * ```
   */
  public fun getRenderableColorType(ct: SkColorType): Int {
    TODO("Implement getRenderableColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ndcYAxisPointsDown() const { return fNDCYAxisPointsDown; }
   * ```
   */
  public fun ndcYAxisPointsDown(): Boolean {
    TODO("Implement ndcYAxisPointsDown")
  }

  /**
   * C++ original:
   * ```cpp
   * bool clampToBorderSupport() const { return fClampToBorderSupport; }
   * ```
   */
  public fun clampToBorderSupport(): Boolean {
    TODO("Implement clampToBorderSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool protectedSupport() const { return fProtectedSupport; }
   * ```
   */
  public fun protectedSupport(): Boolean {
    TODO("Implement protectedSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool semaphoreSupport() const { return fSemaphoreSupport; }
   * ```
   */
  public fun semaphoreSupport(): Boolean {
    TODO("Implement semaphoreSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowCpuSync() const { return fAllowCpuSync; }
   * ```
   */
  public fun allowCpuSync(): Boolean {
    TODO("Implement allowCpuSync")
  }

  /**
   * C++ original:
   * ```cpp
   * bool storageBufferSupport() const { return fStorageBufferSupport; }
   * ```
   */
  public fun storageBufferSupport(): Boolean {
    TODO("Implement storageBufferSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool gradientBufferSupport() const {
   *         return fStorageBufferSupport &&
   *                (fResourceBindingReqs.fStorageBufferLayout == Layout::kStd430 ||
   *                 fResourceBindingReqs.fStorageBufferLayout == Layout::kMetal);
   *     }
   * ```
   */
  public fun gradientBufferSupport(): Boolean {
    TODO("Implement gradientBufferSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool drawBufferCanBeMapped() const { return fDrawBufferCanBeMapped; }
   * ```
   */
  public fun drawBufferCanBeMapped(): Boolean {
    TODO("Implement drawBufferCanBeMapped")
  }

  /**
   * C++ original:
   * ```cpp
   * bool drawBufferCanBeMappedForReadback() const { return fDrawBufferCanBeMappedForReadback; }
   * ```
   */
  public fun drawBufferCanBeMappedForReadback(): Boolean {
    TODO("Implement drawBufferCanBeMappedForReadback")
  }

  /**
   * C++ original:
   * ```cpp
   * bool bufferMapsAreAsync() const { return fBufferMapsAreAsync; }
   * ```
   */
  public fun bufferMapsAreAsync(): Boolean {
    TODO("Implement bufferMapsAreAsync")
  }

  /**
   * C++ original:
   * ```cpp
   * bool msaaRenderToSingleSampledSupport() const { return fMSAARenderToSingleSampledSupport; }
   * ```
   */
  public fun msaaRenderToSingleSampledSupport(): Boolean {
    TODO("Implement msaaRenderToSingleSampledSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool msaaTextureRenderToSingleSampledSupport(const TextureInfo& info) const {
   *         return this->msaaRenderToSingleSampledSupport();
   *     }
   * ```
   */
  public open fun msaaTextureRenderToSingleSampledSupport(info: TextureInfo): Boolean {
    TODO("Implement msaaTextureRenderToSingleSampledSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool differentResolveAttachmentSizeSupport() const {
   *         return fDifferentResolveAttachmentSizeSupport;
   *     }
   * ```
   */
  public fun differentResolveAttachmentSizeSupport(): Boolean {
    TODO("Implement differentResolveAttachmentSizeSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeSupport() const { return fComputeSupport; }
   * ```
   */
  public fun computeSupport(): Boolean {
    TODO("Implement computeSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsAHardwareBufferImages() const { return fSupportsAHardwareBufferImages; }
   * ```
   */
  public fun supportsAHardwareBufferImages(): Boolean {
    TODO("Implement supportsAHardwareBufferImages")
  }

  /**
   * C++ original:
   * ```cpp
   * BlendEquationSupport blendEquationSupport() const { return fBlendEqSupport; }
   * ```
   */
  public fun blendEquationSupport(): BlendEquationSupport {
    TODO("Implement blendEquationSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsHardwareAdvancedBlending() const {
   *         return fBlendEqSupport > BlendEquationSupport::kBasic;
   *     }
   * ```
   */
  public fun supportsHardwareAdvancedBlending(): Boolean {
    TODO("Implement supportsHardwareAdvancedBlending")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Swizzle Caps::getReadSwizzle(SkColorType ct, const TextureInfo& info) const {
   *     // TODO: add SkTextureCompressionType handling
   *     // (can be handled by setting up the colorTypeInfo instead?)
   *
   *     auto colorTypeInfo = this->getColorTypeInfo(ct, info);
   *     if (!colorTypeInfo) {
   *         SkDEBUGFAILF("Illegal color type (%d) and format combination.", static_cast<int>(ct));
   *         return {};
   *     }
   *
   *     return colorTypeInfo->fReadSwizzle;
   * }
   * ```
   */
  public fun getReadSwizzle(ct: SkColorType, info: TextureInfo): Int {
    TODO("Implement getReadSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Swizzle Caps::getWriteSwizzle(SkColorType ct, const TextureInfo& info) const {
   *     auto colorTypeInfo = this->getColorTypeInfo(ct, info);
   *     if (!colorTypeInfo) {
   *         SkDEBUGFAILF("Illegal color type (%d) and format combination.", static_cast<int>(ct));
   *         return {};
   *     }
   *
   *     return colorTypeInfo->fWriteSwizzle;
   * }
   * ```
   */
  public fun getWriteSwizzle(ct: SkColorType, info: TextureInfo): Int {
    TODO("Implement getWriteSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useBasicDynamicState() const { return fUseBasicDynamicState; }
   * ```
   */
  public fun useBasicDynamicState(): Boolean {
    TODO("Implement useBasicDynamicState")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useVertexInputDynamicState() const { return fUseVertexInputDynamicState; }
   * ```
   */
  public fun useVertexInputDynamicState(): Boolean {
    TODO("Implement useVertexInputDynamicState")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usePipelineLibraries() const { return fUsePipelineLibraries; }
   * ```
   */
  public fun usePipelineLibraries(): Boolean {
    TODO("Implement usePipelineLibraries")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsHostImageCopy() const { return fSupportsHostImageCopy; }
   * ```
   */
  public fun supportsHostImageCopy(): Boolean {
    TODO("Implement supportsHostImageCopy")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::ShaderErrorHandler* shaderErrorHandler() const { return fShaderErrorHandler; }
   * ```
   */
  public fun shaderErrorHandler(): ShaderErrorHandler {
    TODO("Implement shaderErrorHandler")
  }

  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy Caps::getDstReadStrategy() const {
   *     // TODO(b/238757201; b/383769988): Dst reads are currently only supported by FB fetch and
   *     // texture copy.
   *     if (this->shaderCaps()->fFBFetchSupport) {
   *         return DstReadStrategy::kFramebufferFetch;
   *     } else {
   *         return DstReadStrategy::kTextureCopy;
   *     }
   * }
   * ```
   */
  public open fun getDstReadStrategy(): Int {
    TODO("Implement getDstReadStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * float minPathSizeForMSAA() const { return fMinMSAAPathSize; }
   * ```
   */
  public fun minPathSizeForMSAA(): Float {
    TODO("Implement minPathSizeForMSAA")
  }

  /**
   * C++ original:
   * ```cpp
   * float minDistanceFieldFontSize() const { return fMinDistanceFieldFontSize; }
   * ```
   */
  public fun minDistanceFieldFontSize(): Float {
    TODO("Implement minDistanceFieldFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * float glyphsAsPathsFontSize() const { return fGlyphsAsPathsFontSize; }
   * ```
   */
  public fun glyphsAsPathsFontSize(): Float {
    TODO("Implement glyphsAsPathsFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t glyphCacheTextureMaximumBytes() const { return fGlyphCacheTextureMaximumBytes; }
   * ```
   */
  public fun glyphCacheTextureMaximumBytes(): Int {
    TODO("Implement glyphCacheTextureMaximumBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxPathAtlasTextureSize() const { return fMaxPathAtlasTextureSize; }
   * ```
   */
  public fun maxPathAtlasTextureSize(): Int {
    TODO("Implement maxPathAtlasTextureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowMultipleAtlasTextures() const { return fAllowMultipleAtlasTextures; }
   * ```
   */
  public fun allowMultipleAtlasTextures(): Boolean {
    TODO("Implement allowMultipleAtlasTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportBilerpFromGlyphAtlas() const { return fSupportBilerpFromGlyphAtlas; }
   * ```
   */
  public fun supportBilerpFromGlyphAtlas(): Boolean {
    TODO("Implement supportBilerpFromGlyphAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requireOrderedRecordings() const { return fRequireOrderedRecordings; }
   * ```
   */
  public fun requireOrderedRecordings(): Boolean {
    TODO("Implement requireOrderedRecordings")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fullCompressedUploadSizeMustAlignToBlockDims() const {
   *         return fFullCompressedUploadSizeMustAlignToBlockDims;
   *     }
   * ```
   */
  public fun fullCompressedUploadSizeMustAlignToBlockDims(): Boolean {
    TODO("Implement fullCompressedUploadSizeMustAlignToBlockDims")
  }

  /**
   * C++ original:
   * ```cpp
   * sktext::gpu::SubRunControl Caps::getSubRunControl(bool useSDFTForSmallText) const {
   * #if !defined(SK_DISABLE_SDF_TEXT)
   *     return sktext::gpu::SubRunControl{
   *             this->shaderCaps()->supportsDistanceFieldText(),
   *             useSDFTForSmallText,
   *             true, /*ableToUsePerspectiveSDFT*/
   *             this->minDistanceFieldFontSize(),
   *             this->glyphsAsPathsFontSize(),
   *             true /*forcePathAA*/};
   * #else
   *     return sktext::gpu::SubRunControl{/*forcePathAA=*/true};
   * #endif
   * }
   * ```
   */
  public fun getSubRunControl(useSDFTForSmallText: Boolean): Int {
    TODO("Implement getSubRunControl")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setBackendLabels() const { return fSetBackendLabels; }
   * ```
   */
  public fun setBackendLabels(): Boolean {
    TODO("Implement setBackendLabels")
  }

  /**
   * C++ original:
   * ```cpp
   * GpuStatsFlags supportedGpuStats() const { return fSupportedGpuStats; }
   * ```
   */
  public fun supportedGpuStats(): Int {
    TODO("Implement supportedGpuStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void Caps::finishInitialization(const ContextOptions& options) {
   *     fCapabilities->initSkCaps(fShaderCaps.get());
   *
   *     fMaxInternalSampleCount = options.fInternalMultisampleCount;
   *
   *     if (options.fShaderErrorHandler) {
   *         fShaderErrorHandler = options.fShaderErrorHandler;
   *     } else {
   *         fShaderErrorHandler = DefaultShaderErrorHandler();
   *     }
   *
   * #if defined(GPU_TEST_UTILS)
   *     if (options.fOptionsPriv) {
   *         fMaxTextureSize = std::min(fMaxTextureSize, options.fOptionsPriv->fMaxTextureSizeOverride);
   *         fRequestedPathRendererStrategy = options.fOptionsPriv->fPathRendererStrategy;
   *     }
   * #endif
   *     fGlyphCacheTextureMaximumBytes = options.fGlyphCacheTextureMaximumBytes;
   *     fMinMSAAPathSize = options.fMinimumPathSizeForMSAA;
   *     fMinDistanceFieldFontSize = options.fMinDistanceFieldFontSize;
   *     fGlyphsAsPathsFontSize = options.fGlyphsAsPathsFontSize;
   *     fMaxPathAtlasTextureSize = options.fMaxPathAtlasTextureSize;
   *     fAllowMultipleAtlasTextures = options.fAllowMultipleAtlasTextures;
   *     fSupportBilerpFromGlyphAtlas = options.fSupportBilerpFromGlyphAtlas;
   *     fRequireOrderedRecordings = options.fRequireOrderedRecordings;
   *     fSetBackendLabels = options.fSetBackendLabels;
   * }
   * ```
   */
  protected fun finishInitialization(options: ContextOptions) {
    TODO("Implement finishInitialization")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDeviceName(std::string n) {
   *         fDeviceName = std::move(n);
   *     }
   * ```
   */
  protected fun setDeviceName(n: String) {
    TODO("Implement setDeviceName")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsTexturable(const TextureInfo&) const = 0
   * ```
   */
  private abstract fun onIsTexturable(param0: TextureInfo): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual const ColorTypeInfo* getColorTypeInfo(SkColorType, const TextureInfo&) const = 0
   * ```
   */
  private abstract fun getColorTypeInfo(param0: SkColorType, param1: TextureInfo): ColorTypeInfo

  public data class ColorTypeInfo public constructor(
    public var fColorType: Int,
    public var fTransferColorType: Int,
    public var fFlags: Int,
    public var fReadSwizzle: Int,
    public var fWriteSwizzle: Int,
  ) {
    public companion object {
      public val kUploadDataFlag: Int = TODO("Initialize kUploadDataFlag")

      public val kRenderableFlag: Int = TODO("Initialize kRenderableFlag")
    }
  }

  public enum class BlendEquationSupport {
    kBasic,
    kAdvancedNoncoherent,
    kAdvancedCoherent,
  }
}
