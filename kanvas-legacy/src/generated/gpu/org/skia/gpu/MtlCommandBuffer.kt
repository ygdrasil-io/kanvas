package org.skia.gpu

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import undefined.DispatchGroupSpan
import undefined.DrawPassList

/**
 * C++ original:
 * ```cpp
 * class MtlCommandBuffer final : public CommandBuffer {
 * public:
 *     static std::unique_ptr<MtlCommandBuffer> Make(id<MTLCommandQueue>,
 *                                                   const MtlSharedContext*,
 *                                                   MtlResourceProvider*);
 *     ~MtlCommandBuffer() override;
 *
 *     bool setNewCommandBufferResources() override;
 *
 *     void addWaitSemaphores(size_t numWaitSemaphores,
 *                            const BackendSemaphore* waitSemaphores) override;
 *     void addSignalSemaphores(size_t numSignalSemaphores,
 *                              const BackendSemaphore* signalSemaphores) override;
 *
 *     bool isFinished() {
 *         return (*fCommandBuffer).status == MTLCommandBufferStatusCompleted ||
 *                (*fCommandBuffer).status == MTLCommandBufferStatusError;
 *
 *     }
 *     void waitUntilFinished() {
 *         // TODO: it's not clear what do to if status is Enqueued. Commit and then wait?
 *         if ((*fCommandBuffer).status == MTLCommandBufferStatusScheduled ||
 *             (*fCommandBuffer).status == MTLCommandBufferStatusCommitted) {
 *             [(*fCommandBuffer) waitUntilCompleted];
 *         }
 *         if (!this->isFinished()) {
 *             SKGPU_LOG_E("Unfinished command buffer status: %d",
 *                         (int)(*fCommandBuffer).status);
 *             SkASSERT(false);
 *         }
 *     }
 *     bool commit();
 *
 * private:
 *     MtlCommandBuffer(id<MTLCommandQueue>,
 *                      const MtlSharedContext* sharedContext,
 *                      MtlResourceProvider* resourceProvider);
 *
 *     ResourceProvider* resourceProvider() const override { return fResourceProvider; }
 *
 *     bool createNewMTLCommandBuffer();
 *
 *     void onResetCommandBuffer() override;
 *
 *     bool onAddRenderPass(const RenderPassDesc&,
 *                          SkIRect renderPassBounds,
 *                          const Texture* colorTexture,
 *                          const Texture* resolveTexture,
 *                          const Texture* depthStencilTexture,
 *                          SkIPoint resolveOffset,
 *                          SkIRect viewport,
 *                          const DrawPassList&) override;
 *     bool onAddComputePass(DispatchGroupSpan) override;
 *
 *     // Methods for populating a MTLRenderCommandEncoder:
 *     bool beginRenderPass(const RenderPassDesc&,
 *                          const Texture* colorTexture,
 *                          const Texture* resolveTexture,
 *                          const Texture* depthStencilTexture);
 *     void endRenderPass();
 *
 *     [[nodiscard]] bool addDrawPass(DrawPass*);
 *
 *     void updateIntrinsicUniforms(SkIRect viewport);
 *
 *     void bindGraphicsPipeline(const GraphicsPipeline*);
 *     void setBlendConstants(std::array<float, 4> blendConstants);
 *
 *     void bindUniformBuffer(const BindBufferInfo& info, UniformSlot);
 *     void bindInputBuffer(const Buffer* buffer, size_t offset, uint32_t bindingIndex);
 *     void bindIndexBuffer(const Buffer* indexBuffer, size_t offset);
 *     void bindIndirectBuffer(const Buffer* indirectBuffer, size_t offset);
 *
 *     void bindTextureAndSampler(const Texture*, const Sampler*, unsigned int bindIndex);
 *
 *     void setScissor(const Scissor&);
 *     void setViewport(float x, float y, float width, float height,
 *                      float minDepth, float maxDepth);
 *
 *     void draw(PrimitiveType type, unsigned int baseVertex, unsigned int vertexCount);
 *     void drawIndexed(PrimitiveType type, unsigned int baseIndex, unsigned int indexCount,
 *                      unsigned int baseVertex);
 *     void drawInstanced(PrimitiveType type,
 *                        unsigned int baseVertex, unsigned int vertexCount,
 *                        unsigned int baseInstance, unsigned int instanceCount);
 *     void drawIndexedInstanced(PrimitiveType type, unsigned int baseIndex,
 *                               unsigned int indexCount, unsigned int baseVertex,
 *                               unsigned int baseInstance, unsigned int instanceCount);
 *     void drawIndirect(PrimitiveType type);
 *     void drawIndexedIndirect(PrimitiveType type);
 *
 *     // Methods for populating a MTLComputeCommandEncoder:
 *     void beginComputePass();
 *     void bindComputePipeline(const ComputePipeline*);
 *     void bindBuffer(const Buffer* buffer, unsigned int offset, unsigned int index);
 *     void bindTexture(const Texture* texture, unsigned int index);
 *     void bindSampler(const Sampler* sampler, unsigned int index);
 *     void dispatchThreadgroups(const WorkgroupSize& globalSize, const WorkgroupSize& localSize);
 *     void dispatchThreadgroupsIndirect(const WorkgroupSize& localSize,
 *                                       const Buffer* indirectBuffer,
 *                                       size_t indirectBufferOffset);
 *     void endComputePass();
 *
 *     // Methods for populating a MTLBlitCommandEncoder:
 *     bool onCopyBufferToBuffer(const Buffer* srcBuffer,
 *                               size_t srcOffset,
 *                               const Buffer* dstBuffer,
 *                               size_t dstOffset,
 *                               size_t size) override;
 *     bool onCopyTextureToBuffer(const Texture*,
 *                                SkIRect srcRect,
 *                                const Buffer*,
 *                                size_t bufferOffset,
 *                                size_t bufferRowBytes) override;
 *     bool onCopyBufferToTexture(const Buffer*,
 *                                const Texture*,
 *                                const BufferTextureCopyData* copyData,
 *                                int count) override;
 *     bool onCopyTextureToTexture(const Texture* src,
 *                                 SkIRect srcRect,
 *                                 const Texture* dst,
 *                                 SkIPoint dstPoint,
 *                                 int mipLevel) override;
 *     bool onSynchronizeBufferToCpu(const Buffer*, bool* outDidResultInWork) override;
 *     bool onClearBuffer(const Buffer*, size_t offset, size_t size) override;
 *
 *     MtlBlitCommandEncoder* getBlitCommandEncoder();
 *     void endBlitCommandEncoder();
 *
 *     sk_cfp<id<MTLCommandBuffer>> fCommandBuffer;
 *     sk_sp<MtlRenderCommandEncoder> fActiveRenderCommandEncoder;
 *     sk_sp<MtlComputeCommandEncoder> fActiveComputeCommandEncoder;
 *     sk_sp<MtlBlitCommandEncoder> fActiveBlitCommandEncoder;
 *
 *     id<MTLBuffer> fCurrentIndexBuffer;
 *     id<MTLBuffer> fCurrentIndirectBuffer;
 *     size_t fCurrentIndexBufferOffset = 0;
 *     size_t fCurrentIndirectBufferOffset = 0;
 *
 *     // The command buffer will outlive the MtlQueueManager which owns the MTLCommandQueue.
 *     id<MTLCommandQueue> fQueue;
 *     const MtlSharedContext* fSharedContext;
 *     MtlResourceProvider* fResourceProvider;
 *
 *     // If true, the draw commands being added are entirely offscreen and can be skipped.
 *     // This can happen if a recording is being replayed with a transform that moves the recorded
 *     // commands outside of the render target bounds.
 *     bool fDrawIsOffscreen = false;
 * }
 * ```
 */
public class MtlCommandBuffer public constructor(
  sharedContext: MtlSharedContext?,
  resourceProvider: MtlResourceProvider?,
) : CommandBuffer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLCommandBuffer>> fCommandBuffer
   * ```
   */
  private var fCommandBuffer: Int = TODO("Initialize fCommandBuffer")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MtlRenderCommandEncoder> fActiveRenderCommandEncoder
   * ```
   */
  private var fActiveRenderCommandEncoder: Int = TODO("Initialize fActiveRenderCommandEncoder")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MtlComputeCommandEncoder> fActiveComputeCommandEncoder
   * ```
   */
  private var fActiveComputeCommandEncoder: Int = TODO("Initialize fActiveComputeCommandEncoder")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MtlBlitCommandEncoder> fActiveBlitCommandEncoder
   * ```
   */
  private var fActiveBlitCommandEncoder: Int = TODO("Initialize fActiveBlitCommandEncoder")

  /**
   * C++ original:
   * ```cpp
   * id<MTLBuffer> fCurrentIndexBuffer
   * ```
   */
  private var fCurrentIndexBuffer: Int = TODO("Initialize fCurrentIndexBuffer")

  /**
   * C++ original:
   * ```cpp
   * id<MTLBuffer> fCurrentIndirectBuffer
   * ```
   */
  private var fCurrentIndirectBuffer: Int = TODO("Initialize fCurrentIndirectBuffer")

  /**
   * C++ original:
   * ```cpp
   * size_t fCurrentIndexBufferOffset
   * ```
   */
  private var fCurrentIndexBufferOffset: Int = TODO("Initialize fCurrentIndexBufferOffset")

  /**
   * C++ original:
   * ```cpp
   * size_t fCurrentIndirectBufferOffset
   * ```
   */
  private var fCurrentIndirectBufferOffset: Int = TODO("Initialize fCurrentIndirectBufferOffset")

  /**
   * C++ original:
   * ```cpp
   * id<MTLCommandQueue> fQueue
   * ```
   */
  private var fQueue: Int = TODO("Initialize fQueue")

  /**
   * C++ original:
   * ```cpp
   * const MtlSharedContext* fSharedContext
   * ```
   */
  private val fSharedContext: MtlSharedContext? = TODO("Initialize fSharedContext")

  /**
   * C++ original:
   * ```cpp
   * MtlResourceProvider* fResourceProvider
   * ```
   */
  private var fResourceProvider: Int? = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * bool fDrawIsOffscreen = false
   * ```
   */
  private var fDrawIsOffscreen: Boolean = TODO("Initialize fDrawIsOffscreen")

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::setNewCommandBufferResources() {
   *     return this->createNewMTLCommandBuffer();
   * }
   * ```
   */
  public override fun setNewCommandBufferResources(): Boolean {
    TODO("Implement setNewCommandBufferResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::addWaitSemaphores(size_t numWaitSemaphores,
   *                                          const BackendSemaphore* waitSemaphores) {
   *     if (!waitSemaphores) {
   *         SkASSERT(numWaitSemaphores == 0);
   *         return;
   *     }
   *
   *     // Can only insert events with no active encoder
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *     this->endBlitCommandEncoder();
   *     if (@available(macOS 10.14, iOS 12.0, tvOS 12.0, *)) {
   *         for (size_t i = 0; i < numWaitSemaphores; ++i) {
   *             auto semaphore = waitSemaphores[i];
   *             if (semaphore.isValid() && semaphore.backend() == BackendApi::kMetal) {
   *                 id<MTLEvent> mtlEvent =
   *                         (__bridge id<MTLEvent>)BackendSemaphores::GetMtlEvent(semaphore);
   *                 [(*fCommandBuffer) encodeWaitForEvent:mtlEvent
   *                                                 value:BackendSemaphores::GetMtlValue(semaphore)];
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public override fun addWaitSemaphores(numWaitSemaphores: ULong, waitSemaphores: BackendSemaphore?) {
    TODO("Implement addWaitSemaphores")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::addSignalSemaphores(size_t numSignalSemaphores,
   *                                            const BackendSemaphore* signalSemaphores) {
   *     if (!signalSemaphores) {
   *         SkASSERT(numSignalSemaphores == 0);
   *         return;
   *     }
   *
   *     // Can only insert events with no active encoder
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *     this->endBlitCommandEncoder();
   *
   *     if (@available(macOS 10.14, iOS 12.0, tvOS 12.0, *)) {
   *         for (size_t i = 0; i < numSignalSemaphores; ++i) {
   *             auto semaphore = signalSemaphores[i];
   *             if (semaphore.isValid() && semaphore.backend() == BackendApi::kMetal) {
   *                 id<MTLEvent> mtlEvent = (__bridge id<MTLEvent>)BackendSemaphores::GetMtlEvent;
   *                 [(*fCommandBuffer) encodeSignalEvent:mtlEvent
   *                                                value:BackendSemaphores::GetMtlValue(semaphore)];
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public override fun addSignalSemaphores(numSignalSemaphores: ULong, signalSemaphores: BackendSemaphore?) {
    TODO("Implement addSignalSemaphores")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinished() {
   *         return (*fCommandBuffer).status == MTLCommandBufferStatusCompleted ||
   *                (*fCommandBuffer).status == MTLCommandBufferStatusError;
   *
   *     }
   * ```
   */
  public fun isFinished(): Boolean {
    TODO("Implement isFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * void waitUntilFinished() {
   *         // TODO: it's not clear what do to if status is Enqueued. Commit and then wait?
   *         if ((*fCommandBuffer).status == MTLCommandBufferStatusScheduled ||
   *             (*fCommandBuffer).status == MTLCommandBufferStatusCommitted) {
   *             [(*fCommandBuffer) waitUntilCompleted];
   *         }
   *         if (!this->isFinished()) {
   *             SKGPU_LOG_E("Unfinished command buffer status: %d",
   *                         (int)(*fCommandBuffer).status);
   *             SkASSERT(false);
   *         }
   *     }
   * ```
   */
  public fun waitUntilFinished() {
    TODO("Implement waitUntilFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::commit() {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *     this->endBlitCommandEncoder();
   *     [(*fCommandBuffer) commit];
   *
   *     if ((*fCommandBuffer).status == MTLCommandBufferStatusError) {
   *         NSString* description = (*fCommandBuffer).error.localizedDescription;
   *         const char* errorString = [description UTF8String];
   *         SKGPU_LOG_E("Failure submitting command buffer: %s", errorString);
   *     }
   *
   *     return ((*fCommandBuffer).status != MTLCommandBufferStatusError);
   * }
   * ```
   */
  public fun commit(): Boolean {
    TODO("Implement commit")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* resourceProvider() const override { return fResourceProvider; }
   * ```
   */
  public override fun resourceProvider(): Int {
    TODO("Implement resourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::createNewMTLCommandBuffer() {
   *     SkASSERT(fCommandBuffer == nil);
   *
   *     // Inserting a pool here so the autorelease occurs when we return and the
   *     // only remaining ref is the retain below.
   *     @autoreleasepool {
   *         if (@available(macOS 11.0, iOS 14.0, tvOS 14.0, *)) {
   *             sk_cfp<MTLCommandBufferDescriptor*> desc([[MTLCommandBufferDescriptor alloc] init]);
   *             (*desc).retainedReferences = NO;
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *             (*desc).errorOptions = MTLCommandBufferErrorOptionEncoderExecutionStatus;
   * #endif
   *             // We add a retain here because the command buffer is set to autorelease (not alloc or copy)
   *             fCommandBuffer.reset([[fQueue commandBufferWithDescriptor:desc.get()] retain]);
   *         } else {
   *             // We add a retain here because the command buffer is set to autorelease (not alloc or copy)
   *             fCommandBuffer.reset([[fQueue commandBufferWithUnretainedReferences] retain]);
   *         }
   *     }
   *     return fCommandBuffer != nil;
   * }
   * ```
   */
  private fun createNewMTLCommandBuffer(): Boolean {
    TODO("Implement createNewMTLCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::onResetCommandBuffer() {
   *     fCommandBuffer.reset();
   *     fActiveRenderCommandEncoder.reset();
   *     fActiveComputeCommandEncoder.reset();
   *     fActiveBlitCommandEncoder.reset();
   *     fCurrentIndexBuffer = nil;
   *     fCurrentIndexBufferOffset = 0;
   * }
   * ```
   */
  public override fun onResetCommandBuffer() {
    TODO("Implement onResetCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onAddRenderPass(const RenderPassDesc& renderPassDesc,
   *                                        SkIRect renderPassBounds,
   *                                        const Texture* colorTexture,
   *                                        const Texture* resolveTexture,
   *                                        const Texture* depthStencilTexture,
   *                                        SkIPoint resolveOffset,
   *                                        SkIRect viewport,
   *                                        const DrawPassList& drawPasses) {
   *     SkASSERT(resolveOffset.isZero());
   *     if (!this->beginRenderPass(renderPassDesc, colorTexture, resolveTexture, depthStencilTexture)) {
   *         return false;
   *     }
   *
   *     this->setViewport(viewport.x(), viewport.y(), viewport.width(), viewport.height(), 0, 1);
   *     this->updateIntrinsicUniforms(viewport);
   *
   *     for (const auto& drawPass : drawPasses) {
   *         if (!this->addDrawPass(drawPass.get())) SK_UNLIKELY {
   *             this->endRenderPass();
   *             return false;
   *         }
   *     }
   *
   *     this->endRenderPass();
   *     return true;
   * }
   * ```
   */
  public override fun onAddRenderPass(
    renderPassDesc: RenderPassDesc,
    renderPassBounds: SkIRect,
    colorTexture: Texture?,
    resolveTexture: Texture?,
    depthStencilTexture: Texture?,
    resolveOffset: SkIPoint,
    viewport: SkIRect,
    drawPasses: DrawPassList,
  ): Boolean {
    TODO("Implement onAddRenderPass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onAddComputePass(DispatchGroupSpan groups) {
   *     this->beginComputePass();
   *     for (const auto& group : groups) {
   *         group->addResourceRefs(this);
   *         for (const auto& dispatch : group->dispatches()) {
   *             this->bindComputePipeline(group->getPipeline(dispatch.fPipelineIndex));
   *             for (const ResourceBinding& binding : dispatch.fBindings) {
   *                 if (const BindBufferInfo* buffer = std::get_if<BindBufferInfo>(&binding.fResource)) {
   *                     this->bindBuffer(buffer->fBuffer, buffer->fOffset, binding.fIndex);
   *                 } else if (const TextureIndex* texIdx =
   *                                    std::get_if<TextureIndex>(&binding.fResource)) {
   *                     SkASSERT(texIdx);
   *                     this->bindTexture(group->getTexture(texIdx->fValue), binding.fIndex);
   *                 } else {
   *                     const SamplerIndex* samplerIdx = std::get_if<SamplerIndex>(&binding.fResource);
   *                     SkASSERT(samplerIdx);
   *                     this->bindSampler(group->getSampler(samplerIdx->fValue), binding.fIndex);
   *                 }
   *             }
   *             SkASSERT(fActiveComputeCommandEncoder);
   *             for (const ComputeStep::WorkgroupBufferDesc& wgBuf : dispatch.fWorkgroupBuffers) {
   *                 fActiveComputeCommandEncoder->setThreadgroupMemoryLength(
   *                         SkAlignTo(wgBuf.size, 16u),
   *                         wgBuf.index);
   *             }
   *             if (const WorkgroupSize* globalSize =
   *                         std::get_if<WorkgroupSize>(&dispatch.fGlobalSizeOrIndirect)) {
   *                 this->dispatchThreadgroups(*globalSize, dispatch.fLocalSize);
   *             } else {
   *                 SkASSERT(std::holds_alternative<BindBufferInfo>(dispatch.fGlobalSizeOrIndirect));
   *                 const BindBufferInfo& indirect =
   *                         *std::get_if<BindBufferInfo>(&dispatch.fGlobalSizeOrIndirect);
   *                 this->dispatchThreadgroupsIndirect(
   *                         dispatch.fLocalSize, indirect.fBuffer, indirect.fOffset);
   *             }
   *         }
   *     }
   *     this->endComputePass();
   *     return true;
   * }
   * ```
   */
  public override fun onAddComputePass(groups: DispatchGroupSpan): Boolean {
    TODO("Implement onAddComputePass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::beginRenderPass(const RenderPassDesc& renderPassDesc,
   *                                        const Texture* colorTexture,
   *                                        const Texture* resolveTexture,
   *                                        const Texture* depthStencilTexture) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *     this->endBlitCommandEncoder();
   *
   *     const static MTLLoadAction mtlLoadAction[] {
   *         MTLLoadActionLoad,
   *         MTLLoadActionClear,
   *         MTLLoadActionDontCare
   *     };
   *     static_assert((int)LoadOp::kLoad == 0);
   *     static_assert((int)LoadOp::kClear == 1);
   *     static_assert((int)LoadOp::kDiscard == 2);
   *     static_assert(std::size(mtlLoadAction) == kLoadOpCount);
   *
   *     const static MTLStoreAction mtlStoreAction[] {
   *         MTLStoreActionStore,
   *         MTLStoreActionDontCare
   *     };
   *     static_assert((int)StoreOp::kStore == 0);
   *     static_assert((int)StoreOp::kDiscard == 1);
   *     static_assert(std::size(mtlStoreAction) == kStoreOpCount);
   *
   *     sk_cfp<MTLRenderPassDescriptor*> descriptor([[MTLRenderPassDescriptor alloc] init]);
   *     // Validate attachment descs and textures
   *     const auto& colorInfo = renderPassDesc.fColorAttachment;
   *     const auto& resolveInfo = renderPassDesc.fColorResolveAttachment;
   *     const auto& depthStencilInfo = renderPassDesc.fDepthStencilAttachment;
   *     SkASSERT(colorTexture ? colorInfo.isCompatible(colorTexture->textureInfo())
   *                           : colorInfo.fFormat == TextureFormat::kUnsupported);
   *     SkASSERT(resolveTexture ? resolveInfo.isCompatible(resolveTexture->textureInfo())
   *                             : resolveInfo.fFormat == TextureFormat::kUnsupported);
   *     SkASSERT(depthStencilTexture ? depthStencilInfo.isCompatible(depthStencilTexture->textureInfo())
   *                                  : depthStencilInfo.fFormat == TextureFormat::kUnsupported);
   *
   *     // Set up color attachment.
   *     bool loadMSAAFromResolve = false;
   *     if (colorTexture) {
   *         auto colorAttachment = (*descriptor).colorAttachments[0];
   *         colorAttachment.texture = ((const MtlTexture*)colorTexture)->mtlTexture();
   *         const std::array<float, 4>& clearColor = renderPassDesc.fClearColor;
   *         colorAttachment.clearColor =
   *                 MTLClearColorMake(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
   *         colorAttachment.loadAction = mtlLoadAction[static_cast<int>(colorInfo.fLoadOp)];
   *         colorAttachment.storeAction = mtlStoreAction[static_cast<int>(colorInfo.fStoreOp)];
   *
   *         // Set up resolve attachment
   *         if (resolveTexture) {
   *             SkASSERT(resolveInfo.fStoreOp == StoreOp::kStore);
   *
   *             colorAttachment.resolveTexture = ((const MtlTexture*)resolveTexture)->mtlTexture();
   *             // Inclusion of a resolve texture implies the client wants to finish the
   *             // renderpass with a resolve.
   *             if (@available(macOS 10.12, iOS 10.0, tvOS 10.0, *)) {
   *                 SkASSERT(colorAttachment.storeAction == MTLStoreActionDontCare);
   *                 colorAttachment.storeAction = MTLStoreActionMultisampleResolve;
   *             } else {
   *                 // We expect at least Metal 2
   *                 // TODO: Add error output
   *                 SkASSERT(false);
   *             }
   *             // But it also means we have to load the resolve texture into the MSAA color attachment
   *             loadMSAAFromResolve = resolveInfo.fLoadOp == LoadOp::kLoad;
   *             // TODO: If the color resolve texture is read-only we can use a private (vs. memoryless)
   *             // msaa attachment that's coupled to the framebuffer and the StoreAndMultisampleResolve
   *             // action instead of loading as a draw.
   *         }
   *     }
   *
   *     // Set up stencil/depth attachment
   *     if (depthStencilTexture) {
   *         id<MTLTexture> mtlTexture = ((const MtlTexture*)depthStencilTexture)->mtlTexture();
   *         if (TextureFormatHasDepth(depthStencilInfo.fFormat)) {
   *             auto depthAttachment = (*descriptor).depthAttachment;
   *             depthAttachment.texture = mtlTexture;
   *             depthAttachment.clearDepth = renderPassDesc.fClearDepth;
   *             depthAttachment.loadAction =
   *                      mtlLoadAction[static_cast<int>(depthStencilInfo.fLoadOp)];
   *             depthAttachment.storeAction =
   *                      mtlStoreAction[static_cast<int>(depthStencilInfo.fStoreOp)];
   *         }
   *         if (TextureFormatHasStencil(depthStencilInfo.fFormat)) {
   *             auto stencilAttachment = (*descriptor).stencilAttachment;
   *             stencilAttachment.texture = mtlTexture;
   *             stencilAttachment.clearStencil = renderPassDesc.fClearStencil;
   *             stencilAttachment.loadAction =
   *                      mtlLoadAction[static_cast<int>(depthStencilInfo.fLoadOp)];
   *             stencilAttachment.storeAction =
   *                      mtlStoreAction[static_cast<int>(depthStencilInfo.fStoreOp)];
   *         }
   *     }
   *
   *     fActiveRenderCommandEncoder = MtlRenderCommandEncoder::Make(fSharedContext,
   *                                                                 fCommandBuffer.get(),
   *                                                                 descriptor.get());
   *     this->trackCommandBufferResource(fActiveRenderCommandEncoder);
   *
   *     if (loadMSAAFromResolve) {
   *         // Manually load the contents of the resolve texture into the MSAA attachment as a draw,
   *         // so the actual load op for the MSAA attachment had better have been discard.
   *         SkASSERT(colorInfo.fLoadOp == LoadOp::kDiscard);
   *         auto loadPipeline = fResourceProvider->findOrCreateLoadMSAAPipeline(renderPassDesc);
   *         if (!loadPipeline) {
   *             SKGPU_LOG_E("Unable to create pipeline to load resolve texture into MSAA attachment");
   *             return false;
   *         }
   *         this->bindGraphicsPipeline(loadPipeline.get());
   *         // The load msaa pipeline takes no uniforms, no vertex/instance attributes and only uses
   *         // one texture that does not require a sampler.
   *         fActiveRenderCommandEncoder->setFragmentTexture(
   *                 ((const MtlTexture*) resolveTexture)->mtlTexture(), 0);
   *         this->draw(PrimitiveType::kTriangleStrip, 0, 4);
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun beginRenderPass(
    renderPassDesc: RenderPassDesc,
    colorTexture: Texture?,
    resolveTexture: Texture?,
    depthStencilTexture: Texture?,
  ): Boolean {
    TODO("Implement beginRenderPass")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::endRenderPass() {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *     fActiveRenderCommandEncoder->endEncoding();
   *     fActiveRenderCommandEncoder.reset();
   *     fDrawIsOffscreen = false;
   * }
   * ```
   */
  private fun endRenderPass() {
    TODO("Implement endRenderPass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::addDrawPass(DrawPass* drawPass) {
   *     const SkIRect replayedBounds = drawPass->bounds().makeOffset(fReplayTranslation.x(),
   *                                                                  fReplayTranslation.y());
   *     if (!SkIRect::Intersects(replayedBounds, fRenderPassBounds)) {
   *         // The entire DrawPass is offscreen given the replay translation so skip adding any
   *         // commands. When the DrawPass is partially offscreen individual draw commands will be
   *         // culled while preserving state changing commands.
   *         return true;
   *     }
   *
   *     // If there is gradient data to bind, it must be done prior to draws.
   *     if (drawPass->floatStorageManager()->hasData()) {
   *         this->bindUniformBuffer(drawPass->floatStorageManager()->getBufferInfo(),
   *                                 UniformSlot::kGradient);
   *     }
   *
   *     if (!drawPass->addResourceRefs(fResourceProvider, this)) SK_UNLIKELY {
   *         return false;
   *     }
   *
   *     for (auto[type, cmdPtr] : drawPass->commands()) {
   *         // Skip draw commands if they'd be offscreen.
   *         if (fDrawIsOffscreen) {
   *             switch (type) {
   *                 case DrawPassCommands::Type::kDraw:
   *                 case DrawPassCommands::Type::kDrawIndexed:
   *                 case DrawPassCommands::Type::kDrawInstanced:
   *                 case DrawPassCommands::Type::kDrawIndexedInstanced:
   *                     continue;
   *                 default:
   *                     break;
   *             }
   *         }
   *
   *         switch (type) {
   *             case DrawPassCommands::Type::kBindGraphicsPipeline: {
   *                 auto bgp = static_cast<DrawPassCommands::BindGraphicsPipeline*>(cmdPtr);
   *                 this->bindGraphicsPipeline(drawPass->getPipeline(bgp->fPipelineIndex));
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kSetBlendConstants: {
   *                 auto sbc = static_cast<DrawPassCommands::SetBlendConstants*>(cmdPtr);
   *                 this->setBlendConstants(sbc->fBlendConstants);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindUniformBuffer: {
   *                 auto bub = static_cast<DrawPassCommands::BindUniformBuffer*>(cmdPtr);
   *                 this->bindUniformBuffer(bub->fInfo, bub->fSlot);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindStaticDataBuffer: {
   *                 auto bdb = static_cast<DrawPassCommands::BindStaticDataBuffer*>(cmdPtr);
   *                 this->bindInputBuffer(bdb->fStaticData.fBuffer, bdb->fStaticData.fOffset,
   *                                       MtlGraphicsPipeline::kStaticDataBufferIndex);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindAppendDataBuffer: {
   *                 auto bdb = static_cast<DrawPassCommands::BindAppendDataBuffer*>(cmdPtr);
   *                 this->bindInputBuffer(bdb->fAppendData.fBuffer, bdb->fAppendData.fOffset,
   *                                       MtlGraphicsPipeline::kAppendDataBufferIndex);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindIndexBuffer: {
   *                 auto bdb = static_cast<DrawPassCommands::BindIndexBuffer*>(cmdPtr);
   *                 this->bindIndexBuffer(
   *                         bdb->fIndices.fBuffer, bdb->fIndices.fOffset);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindIndirectBuffer: {
   *                 auto bdb = static_cast<DrawPassCommands::BindIndirectBuffer*>(cmdPtr);
   *                 this->bindIndirectBuffer(
   *                         bdb->fIndirect.fBuffer, bdb->fIndirect.fOffset);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kBindTexturesAndSamplers: {
   *                 auto bts = static_cast<DrawPassCommands::BindTexturesAndSamplers*>(cmdPtr);
   *                 for (int j = 0; j < bts->fNumTexSamplers; ++j) {
   *                     // immutable samplers don't exist in metal
   *                     SkASSERT(!bts->fSamplers[j].isImmutable());
   *                     this->bindTextureAndSampler(bts->fTextures[j]->texture(),
   *                                                 fSharedContext->globalCache()->getDynamicSampler(
   *                                                         bts->fSamplers[j]),
   *                                                 j);
   *                 }
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kSetScissor: {
   *                 auto ss = static_cast<DrawPassCommands::SetScissor*>(cmdPtr);
   *                 this->setScissor(ss->fScissor);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDraw: {
   *                 auto draw = static_cast<DrawPassCommands::Draw*>(cmdPtr);
   *                 this->draw(draw->fType, draw->fBaseVertex, draw->fVertexCount);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDrawIndexed: {
   *                 auto draw = static_cast<DrawPassCommands::DrawIndexed*>(cmdPtr);
   *                 this->drawIndexed(draw->fType,
   *                                   draw->fBaseIndex,
   *                                   draw->fIndexCount,
   *                                   draw->fBaseVertex);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDrawInstanced: {
   *                 auto draw = static_cast<DrawPassCommands::DrawInstanced*>(cmdPtr);
   *                 this->drawInstanced(draw->fType,
   *                                     draw->fBaseVertex,
   *                                     draw->fVertexCount,
   *                                     draw->fBaseInstance,
   *                                     draw->fInstanceCount);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDrawIndexedInstanced: {
   *                 auto draw = static_cast<DrawPassCommands::DrawIndexedInstanced*>(cmdPtr);
   *                 this->drawIndexedInstanced(draw->fType,
   *                                            draw->fBaseIndex,
   *                                            draw->fIndexCount,
   *                                            draw->fBaseVertex,
   *                                            draw->fBaseInstance,
   *                                            draw->fInstanceCount);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDrawIndirect: {
   *                 auto draw = static_cast<DrawPassCommands::DrawIndirect*>(cmdPtr);
   *                 this->drawIndirect(draw->fType);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kDrawIndexedIndirect: {
   *                 auto draw = static_cast<DrawPassCommands::DrawIndexedIndirect*>(cmdPtr);
   *                 this->drawIndexedIndirect(draw->fType);
   *                 break;
   *             }
   *             case DrawPassCommands::Type::kAddBarrier: {
   *                 SKGPU_LOG_E("MtlCommandBuffer does not support the addition of barriers.");
   *                 break;
   *             }
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun addDrawPass(drawPass: DrawPass?): Boolean {
    TODO("Implement addDrawPass")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::updateIntrinsicUniforms(SkIRect viewport) {
   *     UniformManager intrinsicValues{Layout::kMetal};
   *     CollectIntrinsicUniforms(fSharedContext->caps(), viewport, fDstReadBounds, &intrinsicValues);
   *     SkSpan<const char> bytes = intrinsicValues.finish();
   *     fActiveRenderCommandEncoder->setVertexBytes(
   *             bytes.data(), bytes.size_bytes(), MtlGraphicsPipeline::kIntrinsicUniformBufferIndex);
   *     fActiveRenderCommandEncoder->setFragmentBytes(
   *             bytes.data(), bytes.size_bytes(), MtlGraphicsPipeline::kIntrinsicUniformBufferIndex);
   * }
   * ```
   */
  private fun updateIntrinsicUniforms(viewport: SkIRect) {
    TODO("Implement updateIntrinsicUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindGraphicsPipeline(const GraphicsPipeline* graphicsPipeline) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     auto mtlPipeline = static_cast<const MtlGraphicsPipeline*>(graphicsPipeline);
   *     auto pipelineState = mtlPipeline->mtlPipelineState();
   *     fActiveRenderCommandEncoder->setRenderPipelineState(pipelineState);
   *     auto depthStencilState = mtlPipeline->mtlDepthStencilState();
   *     fActiveRenderCommandEncoder->setDepthStencilState(depthStencilState);
   *     uint32_t stencilRefValue = mtlPipeline->stencilReferenceValue();
   *     fActiveRenderCommandEncoder->setStencilReferenceValue(stencilRefValue);
   *
   *     if (graphicsPipeline->dstReadStrategy() == DstReadStrategy::kTextureCopy) {
   *         // The last texture binding is reserved for the dstCopy texture, which is not included in
   *         // the list on each BindTexturesAndSamplers command. We can set it once now and any
   *         // subsequent BindTexturesAndSamplers commands in a DrawPass will set the other N-1.
   *         SkASSERT(fDstCopy.first && fDstCopy.second);
   *         const int textureIndex = graphicsPipeline->numFragTexturesAndSamplers() - 1;
   *         this->bindTextureAndSampler(fDstCopy.first, fDstCopy.second, textureIndex);
   *     }
   * }
   * ```
   */
  private fun bindGraphicsPipeline(graphicsPipeline: GraphicsPipeline?) {
    TODO("Implement bindGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::setBlendConstants(std::array<float, 4> blendConstants) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     fActiveRenderCommandEncoder->setBlendColor(blendConstants);
   * }
   * ```
   */
  private fun setBlendConstants(blendConstants: Array<Float>) {
    TODO("Implement setBlendConstants")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindUniformBuffer(const BindBufferInfo& info, UniformSlot slot) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     id<MTLBuffer> mtlBuffer = info.fBuffer ?
   *             static_cast<const MtlBuffer*>(info.fBuffer)->mtlBuffer() : nullptr;
   *
   *     unsigned int bufferIndex;
   *     switch(slot) {
   *         case UniformSlot::kCombinedUniforms:
   *             bufferIndex = MtlGraphicsPipeline::kCombinedUniformIndex;
   *             break;
   *         case UniformSlot::kGradient:
   *             bufferIndex = MtlGraphicsPipeline::kGradientBufferIndex;
   *             break;
   *     }
   *
   *     fActiveRenderCommandEncoder->setVertexBuffer(mtlBuffer, info.fOffset, bufferIndex);
   *     fActiveRenderCommandEncoder->setFragmentBuffer(mtlBuffer, info.fOffset, bufferIndex);
   * }
   * ```
   */
  private fun bindUniformBuffer(info: BindBufferInfo, slot: UniformSlot) {
    TODO("Implement bindUniformBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindInputBuffer(const Buffer* buffer, size_t offset, uint32_t bindingIndex) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *     if (buffer) {
   *         id<MTLBuffer> mtlBuffer = static_cast<const MtlBuffer*>(buffer)->mtlBuffer();
   *         SkASSERT((offset & 0b11) == 0);
   *         fActiveRenderCommandEncoder->setVertexBuffer(mtlBuffer, offset, bindingIndex);
   *     }
   * }
   * ```
   */
  private fun bindInputBuffer(
    buffer: Buffer?,
    offset: ULong,
    bindingIndex: UInt,
  ) {
    TODO("Implement bindInputBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindIndexBuffer(const Buffer* indexBuffer, size_t offset) {
   *     if (indexBuffer) {
   *         fCurrentIndexBuffer = static_cast<const MtlBuffer*>(indexBuffer)->mtlBuffer();
   *         fCurrentIndexBufferOffset = offset;
   *     } else {
   *         fCurrentIndexBuffer = nil;
   *         fCurrentIndexBufferOffset = 0;
   *     }
   * }
   * ```
   */
  private fun bindIndexBuffer(indexBuffer: Buffer?, offset: ULong) {
    TODO("Implement bindIndexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindIndirectBuffer(const Buffer* indirectBuffer, size_t offset) {
   *     if (indirectBuffer) {
   *         fCurrentIndirectBuffer = static_cast<const MtlBuffer*>(indirectBuffer)->mtlBuffer();
   *         fCurrentIndirectBufferOffset = offset;
   *     } else {
   *         fCurrentIndirectBuffer = nil;
   *         fCurrentIndirectBufferOffset = 0;
   *     }
   * }
   * ```
   */
  private fun bindIndirectBuffer(indirectBuffer: Buffer?, offset: ULong) {
    TODO("Implement bindIndirectBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindTextureAndSampler(const Texture* texture,
   *                                              const Sampler* sampler,
   *                                              unsigned int bindIndex) {
   *     SkASSERT(texture && sampler);
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     id<MTLTexture> mtlTexture = ((const MtlTexture*)texture)->mtlTexture();
   *     id<MTLSamplerState> mtlSamplerState = ((const MtlSampler*)sampler)->mtlSamplerState();
   *     fActiveRenderCommandEncoder->setFragmentTexture(mtlTexture, bindIndex);
   *     fActiveRenderCommandEncoder->setFragmentSamplerState(mtlSamplerState, bindIndex);
   * }
   * ```
   */
  private fun bindTextureAndSampler(
    texture: Texture?,
    sampler: Sampler?,
    bindIndex: UInt,
  ) {
    TODO("Implement bindTextureAndSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::setScissor(const Scissor& scissor) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     SkIRect rect = scissor.getRect(fReplayTranslation, fRenderPassBounds);
   *     fDrawIsOffscreen = rect.isEmpty();
   *
   *     fActiveRenderCommandEncoder->setScissorRect({
   *             static_cast<unsigned int>(rect.x()),
   *             static_cast<unsigned int>(rect.y()),
   *             static_cast<unsigned int>(rect.width()),
   *             static_cast<unsigned int>(rect.height()),
   *     });
   * }
   * ```
   */
  private fun setScissor(scissor: Scissor) {
    TODO("Implement setScissor")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::setViewport(float x, float y, float width, float height,
   *                                    float minDepth, float maxDepth) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *     MTLViewport viewport = {x,
   *                             y,
   *                             width,
   *                             height,
   *                             minDepth,
   *                             maxDepth};
   *     fActiveRenderCommandEncoder->setViewport(viewport);
   * }
   * ```
   */
  private fun setViewport(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    minDepth: Float,
    maxDepth: Float,
  ) {
    TODO("Implement setViewport")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::draw(PrimitiveType type,
   *                             unsigned int baseVertex,
   *                             unsigned int vertexCount) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *
   *     fActiveRenderCommandEncoder->drawPrimitives(mtlPrimitiveType, baseVertex, vertexCount);
   * }
   * ```
   */
  private fun draw(
    type: PrimitiveType,
    baseVertex: UInt,
    vertexCount: UInt,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::drawIndexed(PrimitiveType type, unsigned int baseIndex,
   *                                    unsigned int indexCount, unsigned int baseVertex) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
   *         auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *         size_t indexOffset =  fCurrentIndexBufferOffset + sizeof(uint16_t )* baseIndex;
   *         // Use the "instance" variant witha count of 1 so that we can pass in a base vertex
   *         // instead of rebinding a vertex buffer offset.
   *         fActiveRenderCommandEncoder->drawIndexedPrimitives(mtlPrimitiveType, indexCount,
   *                                                            MTLIndexTypeUInt16, fCurrentIndexBuffer,
   *                                                            indexOffset, 1, baseVertex, 0);
   *
   *     } else {
   *         SKGPU_LOG_E("Skipping unsupported draw call.");
   *     }
   * }
   * ```
   */
  private fun drawIndexed(
    type: PrimitiveType,
    baseIndex: UInt,
    indexCount: UInt,
    baseVertex: UInt,
  ) {
    TODO("Implement drawIndexed")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::drawInstanced(PrimitiveType type, unsigned int baseVertex,
   *                                      unsigned int vertexCount, unsigned int baseInstance,
   *                                      unsigned int instanceCount) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *
   *     // This ordering is correct
   *     fActiveRenderCommandEncoder->drawPrimitives(mtlPrimitiveType, baseVertex, vertexCount,
   *                                                 instanceCount, baseInstance);
   * }
   * ```
   */
  private fun drawInstanced(
    type: PrimitiveType,
    baseVertex: UInt,
    vertexCount: UInt,
    baseInstance: UInt,
    instanceCount: UInt,
  ) {
    TODO("Implement drawInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::drawIndexedInstanced(PrimitiveType type,
   *                                             unsigned int baseIndex,
   *                                             unsigned int indexCount,
   *                                             unsigned int baseVertex,
   *                                             unsigned int baseInstance,
   *                                             unsigned int instanceCount) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *
   *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
   *         auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *         size_t indexOffset =  fCurrentIndexBufferOffset + sizeof(uint16_t) * baseIndex;
   *         fActiveRenderCommandEncoder->drawIndexedPrimitives(mtlPrimitiveType, indexCount,
   *                                                            MTLIndexTypeUInt16, fCurrentIndexBuffer,
   *                                                            indexOffset, instanceCount,
   *                                                            baseVertex, baseInstance);
   *     } else {
   *         SKGPU_LOG_E("Skipping unsupported draw call.");
   *     }
   * }
   * ```
   */
  private fun drawIndexedInstanced(
    type: PrimitiveType,
    baseIndex: UInt,
    indexCount: UInt,
    baseVertex: UInt,
    baseInstance: UInt,
    instanceCount: UInt,
  ) {
    TODO("Implement drawIndexedInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::drawIndirect(PrimitiveType type) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *     SkASSERT(fCurrentIndirectBuffer);
   *
   *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
   *         auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *         fActiveRenderCommandEncoder->drawPrimitives(
   *                 mtlPrimitiveType, fCurrentIndirectBuffer, fCurrentIndirectBufferOffset);
   *     } else {
   *         SKGPU_LOG_E("Skipping unsupported draw call.");
   *     }
   * }
   * ```
   */
  private fun drawIndirect(type: PrimitiveType) {
    TODO("Implement drawIndirect")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::drawIndexedIndirect(PrimitiveType type) {
   *     SkASSERT(fActiveRenderCommandEncoder);
   *     SkASSERT(fCurrentIndirectBuffer);
   *
   *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
   *         auto mtlPrimitiveType = graphite_to_mtl_primitive(type);
   *         fActiveRenderCommandEncoder->drawIndexedPrimitives(mtlPrimitiveType,
   *                                                            MTLIndexTypeUInt32,
   *                                                            fCurrentIndexBuffer,
   *                                                            fCurrentIndexBufferOffset,
   *                                                            fCurrentIndirectBuffer,
   *                                                            fCurrentIndirectBufferOffset);
   *     } else {
   *         SKGPU_LOG_E("Skipping unsupported draw call.");
   *     }
   * }
   * ```
   */
  private fun drawIndexedIndirect(type: PrimitiveType) {
    TODO("Implement drawIndexedIndirect")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::beginComputePass() {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *     this->endBlitCommandEncoder();
   *     fActiveComputeCommandEncoder = MtlComputeCommandEncoder::Make(fSharedContext,
   *                                                                   fCommandBuffer.get());
   * }
   * ```
   */
  private fun beginComputePass() {
    TODO("Implement beginComputePass")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindComputePipeline(const ComputePipeline* computePipeline) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *
   *     auto mtlPipeline = static_cast<const MtlComputePipeline*>(computePipeline);
   *     fActiveComputeCommandEncoder->setComputePipelineState(mtlPipeline->mtlPipelineState());
   * }
   * ```
   */
  private fun bindComputePipeline(computePipeline: ComputePipeline?) {
    TODO("Implement bindComputePipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindBuffer(const Buffer* buffer, unsigned int offset, unsigned int index) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *
   *     id<MTLBuffer> mtlBuffer = buffer ? static_cast<const MtlBuffer*>(buffer)->mtlBuffer() : nil;
   *     fActiveComputeCommandEncoder->setBuffer(mtlBuffer, offset, index);
   * }
   * ```
   */
  private fun bindBuffer(
    buffer: Buffer?,
    offset: UInt,
    index: UInt,
  ) {
    TODO("Implement bindBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindTexture(const Texture* texture, unsigned int index) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *
   *     id<MTLTexture> mtlTexture =
   *             texture ? static_cast<const MtlTexture*>(texture)->mtlTexture() : nil;
   *     fActiveComputeCommandEncoder->setTexture(mtlTexture, index);
   * }
   * ```
   */
  private fun bindTexture(texture: Texture?, index: UInt) {
    TODO("Implement bindTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::bindSampler(const Sampler* sampler, unsigned int index) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *
   *     id<MTLSamplerState> mtlSamplerState =
   *             sampler ? static_cast<const MtlSampler*>(sampler)->mtlSamplerState() : nil;
   *     fActiveComputeCommandEncoder->setSamplerState(mtlSamplerState, index);
   * }
   * ```
   */
  private fun bindSampler(sampler: Sampler?, index: UInt) {
    TODO("Implement bindSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::dispatchThreadgroups(const WorkgroupSize& globalSize,
   *                                             const WorkgroupSize& localSize) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *     fActiveComputeCommandEncoder->dispatchThreadgroups(globalSize, localSize);
   * }
   * ```
   */
  private fun dispatchThreadgroups(globalSize: WorkgroupSize, localSize: WorkgroupSize) {
    TODO("Implement dispatchThreadgroups")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::dispatchThreadgroupsIndirect(const WorkgroupSize& localSize,
   *                                                     const Buffer* indirectBuffer,
   *                                                     size_t indirectBufferOffset) {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *
   *     id<MTLBuffer> mtlIndirectBuffer = static_cast<const MtlBuffer*>(indirectBuffer)->mtlBuffer();
   *     fActiveComputeCommandEncoder->dispatchThreadgroupsWithIndirectBuffer(
   *             mtlIndirectBuffer, indirectBufferOffset, localSize);
   * }
   * ```
   */
  private fun dispatchThreadgroupsIndirect(
    localSize: WorkgroupSize,
    indirectBuffer: Buffer?,
    indirectBufferOffset: ULong,
  ) {
    TODO("Implement dispatchThreadgroupsIndirect")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::endComputePass() {
   *     SkASSERT(fActiveComputeCommandEncoder);
   *     fActiveComputeCommandEncoder->endEncoding();
   *     fActiveComputeCommandEncoder.reset();
   * }
   * ```
   */
  private fun endComputePass() {
    TODO("Implement endComputePass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onCopyBufferToBuffer(const Buffer* srcBuffer,
   *                                             size_t srcOffset,
   *                                             const Buffer* dstBuffer,
   *                                             size_t dstOffset,
   *                                             size_t size) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     id<MTLBuffer> mtlSrcBuffer = static_cast<const MtlBuffer*>(srcBuffer)->mtlBuffer();
   *     id<MTLBuffer> mtlDstBuffer = static_cast<const MtlBuffer*>(dstBuffer)->mtlBuffer();
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->pushDebugGroup(@"copyBufferToBuffer");
   * #endif
   *     blitCmdEncoder->copyBufferToBuffer(mtlSrcBuffer, srcOffset, mtlDstBuffer, dstOffset, size);
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->popDebugGroup();
   * #endif
   *     return true;
   * }
   * ```
   */
  public override fun onCopyBufferToBuffer(
    srcBuffer: Buffer?,
    srcOffset: ULong,
    dstBuffer: Buffer?,
    dstOffset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement onCopyBufferToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onCopyTextureToBuffer(const Texture* texture,
   *                                              SkIRect srcRect,
   *                                              const Buffer* buffer,
   *                                              size_t bufferOffset,
   *                                              size_t bufferRowBytes) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     if (!check_max_blit_width(srcRect.width())) {
   *         return false;
   *     }
   *
   *     id<MTLTexture> mtlTexture = static_cast<const MtlTexture*>(texture)->mtlTexture();
   *     id<MTLBuffer> mtlBuffer = static_cast<const MtlBuffer*>(buffer)->mtlBuffer();
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->pushDebugGroup(@"copyTextureToBuffer");
   * #endif
   *     blitCmdEncoder->copyFromTexture(mtlTexture, srcRect, mtlBuffer, bufferOffset, bufferRowBytes);
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->popDebugGroup();
   * #endif
   *     return true;
   * }
   * ```
   */
  public override fun onCopyTextureToBuffer(
    texture: Texture?,
    srcRect: SkIRect,
    buffer: Buffer?,
    bufferOffset: ULong,
    bufferRowBytes: ULong,
  ): Boolean {
    TODO("Implement onCopyTextureToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onCopyBufferToTexture(const Buffer* buffer,
   *                                              const Texture* texture,
   *                                              const BufferTextureCopyData* copyData,
   *                                              int count) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     id<MTLBuffer> mtlBuffer = static_cast<const MtlBuffer*>(buffer)->mtlBuffer();
   *     id<MTLTexture> mtlTexture = static_cast<const MtlTexture*>(texture)->mtlTexture();
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->pushDebugGroup(@"copyBufferToTexture");
   * #endif
   *     for (int i = 0; i < count; ++i) {
   *         if (!check_max_blit_width(copyData[i].fRect.width())) {
   *             return false;
   *         }
   *
   *         blitCmdEncoder->copyFromBuffer(mtlBuffer,
   *                                        copyData[i].fBufferOffset,
   *                                        copyData[i].fBufferRowBytes,
   *                                        mtlTexture,
   *                                        copyData[i].fRect,
   *                                        copyData[i].fMipLevel);
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->popDebugGroup();
   * #endif
   *     return true;
   * }
   * ```
   */
  public override fun onCopyBufferToTexture(
    buffer: Buffer?,
    texture: Texture?,
    copyData: BufferTextureCopyData?,
    count: Int,
  ): Boolean {
    TODO("Implement onCopyBufferToTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onCopyTextureToTexture(const Texture* src,
   *                                               SkIRect srcRect,
   *                                               const Texture* dst,
   *                                               SkIPoint dstPoint,
   *                                               int mipLevel) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     id<MTLTexture> srcMtlTexture = static_cast<const MtlTexture*>(src)->mtlTexture();
   *     id<MTLTexture> dstMtlTexture = static_cast<const MtlTexture*>(dst)->mtlTexture();
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->pushDebugGroup(@"copyTextureToTexture");
   * #endif
   *
   *     blitCmdEncoder->copyTextureToTexture(srcMtlTexture, srcRect, dstMtlTexture, dstPoint, mipLevel);
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->popDebugGroup();
   * #endif
   *     return true;
   * }
   * ```
   */
  public override fun onCopyTextureToTexture(
    src: Texture?,
    srcRect: SkIRect,
    dst: Texture?,
    dstPoint: SkIPoint,
    mipLevel: Int,
  ): Boolean {
    TODO("Implement onCopyTextureToTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onSynchronizeBufferToCpu(const Buffer* buffer, bool* outDidResultInWork) {
   * #ifdef SK_BUILD_FOR_MAC
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     id<MTLBuffer> mtlBuffer = static_cast<const MtlBuffer*>(buffer)->mtlBuffer();
   *     if ([mtlBuffer storageMode] != MTLStorageModeManaged) {
   *         *outDidResultInWork = false;
   *         return true;
   *     }
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->pushDebugGroup(@"synchronizeToCpu");
   * #endif
   *     blitCmdEncoder->synchronizeResource(mtlBuffer);
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     blitCmdEncoder->popDebugGroup();
   * #endif
   *
   *     *outDidResultInWork = true;
   *     return true;
   * #else   // SK_BUILD_FOR_MAC
   *     // Explicit synchronization is never necessary on builds that are not macOS since we never use
   *     // discrete GPUs with managed mode buffers outside of macOS.
   *     *outDidResultInWork = false;
   *     return true;
   * #endif  // SK_BUILD_FOR_MAC
   * }
   * ```
   */
  public override fun onSynchronizeBufferToCpu(buffer: Buffer?, outDidResultInWork: Boolean?): Boolean {
    TODO("Implement onSynchronizeBufferToCpu")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MtlCommandBuffer::onClearBuffer(const Buffer* buffer, size_t offset, size_t size) {
   *     SkASSERT(!fActiveRenderCommandEncoder);
   *     SkASSERT(!fActiveComputeCommandEncoder);
   *
   *     MtlBlitCommandEncoder* blitCmdEncoder = this->getBlitCommandEncoder();
   *     if (!blitCmdEncoder) {
   *         return false;
   *     }
   *
   *     id<MTLBuffer> mtlBuffer = static_cast<const MtlBuffer*>(buffer)->mtlBuffer();
   *     blitCmdEncoder->fillBuffer(mtlBuffer, offset, size, 0);
   *
   *     return true;
   * }
   * ```
   */
  public override fun onClearBuffer(
    buffer: Buffer?,
    offset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement onClearBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * MtlBlitCommandEncoder* MtlCommandBuffer::getBlitCommandEncoder() {
   *     if (fActiveBlitCommandEncoder) {
   *         return fActiveBlitCommandEncoder.get();
   *     }
   *
   *     fActiveBlitCommandEncoder = MtlBlitCommandEncoder::Make(fSharedContext, fCommandBuffer.get());
   *
   *     if (!fActiveBlitCommandEncoder) {
   *         return nullptr;
   *     }
   *
   *     // We add the ref on the command buffer for the BlitCommandEncoder now so that we don't need
   *     // to add a ref for every copy we do.
   *     this->trackCommandBufferResource(fActiveBlitCommandEncoder);
   *     return fActiveBlitCommandEncoder.get();
   * }
   * ```
   */
  private fun getBlitCommandEncoder(): MtlBlitCommandEncoder {
    TODO("Implement getBlitCommandEncoder")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlCommandBuffer::endBlitCommandEncoder() {
   *     if (fActiveBlitCommandEncoder) {
   *         fActiveBlitCommandEncoder->endEncoding();
   *         fActiveBlitCommandEncoder.reset();
   *     }
   * }
   * ```
   */
  private fun endBlitCommandEncoder() {
    TODO("Implement endBlitCommandEncoder")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<MtlCommandBuffer> MtlCommandBuffer::Make(id<MTLCommandQueue> queue,
     *                                                          const MtlSharedContext* sharedContext,
     *                                                          MtlResourceProvider* resourceProvider) {
     *     auto commandBuffer = std::unique_ptr<MtlCommandBuffer>(
     *             new MtlCommandBuffer(queue, sharedContext, resourceProvider));
     *     if (!commandBuffer) {
     *         return nullptr;
     *     }
     *     if (!commandBuffer->createNewMTLCommandBuffer()) {
     *         return nullptr;
     *     }
     *     return commandBuffer;
     * }
     * ```
     */
    public fun make(sharedContext: MtlSharedContext?, resourceProvider: MtlResourceProvider?): Int {
      TODO("Implement make")
    }
  }
}
