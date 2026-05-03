package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkSurface
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkIVector
import undefined.DispatchGroupSpan
import undefined.DrawPassList

/**
 * C++ original:
 * ```cpp
 * class CommandBuffer {
 * public:
 *     using DrawPassList = skia_private::TArray<std::unique_ptr<DrawPass>>;
 *     using DispatchGroupSpan = SkSpan<const std::unique_ptr<DispatchGroup>>;
 *
 *     virtual ~CommandBuffer();
 *
 * #ifdef SK_DEBUG
 *     bool hasWork() { return fHasWork; }
 * #endif
 *
 *     // Takes a Usage ref on the Resource that will be released when the command buffer has finished
 *     // execution.
 *     void trackResource(sk_sp<Resource> resource);
 *     // Takes a CommandBuffer ref on the Resource that will be released when the command buffer has
 *     // finished execution. This allows a Resource to be returned to ResourceCache for reuse while
 *     // the CommandBuffer is still executing on the GPU. This is most commonly used for Textures or
 *     // Buffers which are only accessed via commands on a command buffer.
 *     void trackCommandBufferResource(sk_sp<Resource> resource);
 *     // Release all tracked Resources
 *     void resetCommandBuffer();
 *
 *     // If any work is needed to create new resources for a fresh command buffer do that here.
 *     virtual bool setNewCommandBufferResources() = 0;
 *
 *     virtual bool startTimerQuery() { SK_ABORT("Timer query unsupported."); }
 *     virtual void endTimerQuery() { SK_ABORT("Timer query unsupported."); }
 *     virtual std::optional<GpuStats> gpuStats() { return {}; }
 *
 *     void addFinishedProc(sk_sp<RefCntedCallback> finishedProc);
 *     void callFinishedProcs(bool success);
 *
 *     virtual void addWaitSemaphores(size_t numWaitSemaphores,
 *                                    const BackendSemaphore* waitSemaphores) {}
 *     virtual void addSignalSemaphores(size_t numWaitSemaphores,
 *                                      const BackendSemaphore* signalSemaphores) {}
 *     virtual void prepareSurfaceForStateUpdate(SkSurface* targetSurface,
 *                                               const MutableTextureState* newState) {}
 *
 *     void addBuffersToAsyncMapOnSubmit(SkSpan<const sk_sp<Buffer>>);
 *     SkSpan<const sk_sp<Buffer>> buffersToAsyncMapOnSubmit() const;
 *
 *     // If any recorded draw requires a dst texture copy for blending, that texture must be provided
 *     // in `dstCopy`; otherwise it should be null. The `dstReadBounds` are in the same coordinate
 *     // space of the logical viewport *before* any replay translation is applied.
 *     //
 *     // The logical viewport is always (0,0,viewportDims) and matches the "device" coordinate space
 *     // of the higher-level SkDevices that recorded the rendering operations. The actual viewport
 *     // is automatically adjusted by the replay translation.
 *     //
 *     // If the RenderPassTask allocates a smaller color texture than the resolve texture, it can pass
 *     // a non-zero `resolveOffset` which is the the offset for resolving:
 *     // - The color texture's (0, 0, w, h) region.
 *     // - And store in the resolve texture's (resolveOffset.x, resolveOffset.y, w, h) region.
 *     bool addRenderPass(const RenderPassDesc&,
 *                        sk_sp<Texture> colorTexture,
 *                        sk_sp<Texture> resolveTexture,
 *                        sk_sp<Texture> depthStencilTexture,
 *                        const Texture* dstCopy,
 *                        SkIRect dstReadBounds,
 *                        SkIPoint resolveOffset,
 *                        SkISize viewportDims,
 *                        const DrawPassList& drawPasses);
 *
 *     bool addComputePass(DispatchGroupSpan dispatchGroups);
 *
 *     //---------------------------------------------------------------
 *     // Can only be used outside renderpasses
 *     //---------------------------------------------------------------
 *     bool copyBufferToBuffer(const Buffer* srcBuffer,
 *                             size_t srcOffset,
 *                             sk_sp<Buffer> dstBuffer,
 *                             size_t dstOffset,
 *                             size_t size);
 *     bool copyTextureToBuffer(sk_sp<Texture>,
 *                              SkIRect srcRect,
 *                              sk_sp<Buffer>,
 *                              size_t bufferOffset,
 *                              size_t bufferRowBytes);
 *     bool copyBufferToTexture(const Buffer*,
 *                              sk_sp<Texture>,
 *                              const BufferTextureCopyData*,
 *                              int count);
 *     bool copyTextureToTexture(sk_sp<Texture> src,
 *                               SkIRect srcRect,
 *                               sk_sp<Texture> dst,
 *                               SkIPoint dstPoint,
 *                               int mipLevel);
 *     bool synchronizeBufferToCpu(sk_sp<Buffer>);
 *     bool clearBuffer(const Buffer* buffer, size_t offset, size_t size);
 *
 *     // This sets a translation and clip to be applied to any subsequently added command, assuming
 *     // these commands are part of a transformed replay of a Graphite recording. Returns whether the
 *     // clip and render target bounds have an intersection; if not, no draws need be replayed.
 *     bool setReplayTranslationAndClip(const SkIVector& translation,
 *                                      const SkIRect& clip,
 *                                      const SkIRect& renderTargetBounds);
 *
 *     Protected isProtected() const { return fIsProtected; }
 *
 * protected:
 *     CommandBuffer(Protected);
 *
 *     // These are the color attachment bounds, intersected with any clip provided on replay.
 *     SkIRect fRenderPassBounds;
 *     // This is also the origin of the logical viewport relative to the target texture's (0,0) pixel.
 *     SkIVector fReplayTranslation;
 *
 *     // The texture to use for implementing DstReadStrategy::kTextureCopy for the current render
 *     // pass. This is a bare pointer since the CopyTask that initializes the texture's contents
 *     // will have tracked the resource on the CommandBuffer already.
 *     std::pair<const Texture*, const Sampler*> fDstCopy;
 *     // Already includes replay translation and respects final color attachment bounds, but with
 *     // dimensions that equal fDstCopy's width and height.
 *     SkIRect fDstReadBounds;
 *
 *     Protected fIsProtected;
 *
 * private:
 *     // Release all tracked Resources
 *     void releaseResources();
 *
 *     // Subclasses will hold their backend-specific ResourceProvider directly to avoid virtual calls
 *     // and access backend-specific behavior, but they can reflect it back to the base CommandBuffer
 *     // if it needs to make generic resources.
 *     virtual ResourceProvider* resourceProvider() const = 0;
 *
 *     virtual void onResetCommandBuffer() = 0;
 *
 *     // Renderpass, viewport bounds have already been adjusted by the replay translation. The render
 *     // pass bounds has been intersected with the color attachment bounds.
 *     virtual bool onAddRenderPass(const RenderPassDesc&,
 *                                  SkIRect renderPassBounds,
 *                                  const Texture* colorTexture,
 *                                  const Texture* resolveTexture,
 *                                  const Texture* depthStencilTexture,
 *                                  SkIPoint resolveOffset,
 *                                  SkIRect viewport,
 *                                  const DrawPassList& drawPasses) = 0;
 *
 *     virtual bool onAddComputePass(DispatchGroupSpan dispatchGroups) = 0;
 *
 *     virtual bool onCopyBufferToBuffer(const Buffer* srcBuffer,
 *                                       size_t srcOffset,
 *                                       const Buffer* dstBuffer,
 *                                       size_t dstOffset,
 *                                       size_t size) = 0;
 *     virtual bool onCopyTextureToBuffer(const Texture*,
 *                                        SkIRect srcRect,
 *                                        const Buffer*,
 *                                        size_t bufferOffset,
 *                                        size_t bufferRowBytes) = 0;
 *     virtual bool onCopyBufferToTexture(const Buffer*,
 *                                        const Texture*,
 *                                        const BufferTextureCopyData*,
 *                                        int count) = 0;
 *     virtual bool onCopyTextureToTexture(const Texture* src,
 *                                         SkIRect srcRect,
 *                                         const Texture* dst,
 *                                         SkIPoint dstPoint,
 *                                         int mipLevel) = 0;
 *     virtual bool onSynchronizeBufferToCpu(const Buffer*, bool* outDidResultInWork) = 0;
 *     virtual bool onClearBuffer(const Buffer*, size_t offset, size_t size) = 0;
 *
 * #ifdef SK_DEBUG
 *     bool fHasWork = false;
 * #endif
 *     inline static constexpr int kInitialTrackedResourcesCount = 32;
 *     template <typename T>
 *     using TrackedResourceArray = skia_private::STArray<kInitialTrackedResourcesCount, T>;
 *     TrackedResourceArray<sk_sp<Resource>> fTrackedUsageResources;
 *     TrackedResourceArray<gr_cb<Resource>> fCommandBufferResources;
 *     skia_private::TArray<sk_sp<RefCntedCallback>> fFinishedProcs;
 *     skia_private::TArray<sk_sp<Buffer>> fBuffersToAsyncMap;
 * }
 * ```
 */
public abstract class CommandBuffer public constructor(
  isProtected: Protected,
) {
  /**
   * C++ original:
   * ```cpp
   * SkIRect fRenderPassBounds
   * ```
   */
  protected var fRenderPassBounds: Int = TODO("Initialize fRenderPassBounds")

  /**
   * C++ original:
   * ```cpp
   * SkIVector fReplayTranslation
   * ```
   */
  protected var fReplayTranslation: Int = TODO("Initialize fReplayTranslation")

  /**
   * C++ original:
   * ```cpp
   * std::pair<const Texture*, const Sampler*> fDstCopy
   * ```
   */
  protected var fDstCopy: Int = TODO("Initialize fDstCopy")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fDstReadBounds
   * ```
   */
  protected var fDstReadBounds: Int = TODO("Initialize fDstReadBounds")

  /**
   * C++ original:
   * ```cpp
   * Protected fIsProtected
   * ```
   */
  protected var fIsProtected: Int = TODO("Initialize fIsProtected")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kInitialTrackedResourcesCount = 32
   * ```
   */
  private var fTrackedUsageResources: Int = TODO("Initialize fTrackedUsageResources")

  /**
   * C++ original:
   * ```cpp
   * TrackedResourceArray<sk_sp<Resource>> fTrackedUsageResources
   * ```
   */
  private var fCommandBufferResources: Int = TODO("Initialize fCommandBufferResources")

  /**
   * C++ original:
   * ```cpp
   * TrackedResourceArray<gr_cb<Resource>> fCommandBufferResources
   * ```
   */
  private var fFinishedProcs: Int = TODO("Initialize fFinishedProcs")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<RefCntedCallback>> fFinishedProcs
   * ```
   */
  private var fBuffersToAsyncMap: Int = TODO("Initialize fBuffersToAsyncMap")

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::trackResource(sk_sp<Resource> resource) {
   *     fTrackedUsageResources.push_back(std::move(resource));
   * }
   * ```
   */
  public fun trackResource(resource: SkSp<Resource>) {
    TODO("Implement trackResource")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::trackCommandBufferResource(sk_sp<Resource> resource) {
   *     fCommandBufferResources.push_back(std::move(resource));
   * }
   * ```
   */
  public fun trackCommandBufferResource(resource: SkSp<Resource>) {
    TODO("Implement trackCommandBufferResource")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::resetCommandBuffer() {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *
   *     // The dst copy texture and sampler are kept alive by the tracked resources, so reset these
   *     // before we release their refs. Assuming we don't go idle and free lots of resources, we'll
   *     // get the same cached sampler the next time we need a dst copy.
   *     fDstCopy = {nullptr, nullptr};
   *     this->releaseResources();
   *     this->onResetCommandBuffer();
   *     fBuffersToAsyncMap.clear();
   * }
   * ```
   */
  public fun resetCommandBuffer() {
    TODO("Implement resetCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool setNewCommandBufferResources() = 0
   * ```
   */
  public abstract fun setNewCommandBufferResources(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool startTimerQuery() { SK_ABORT("Timer query unsupported."); }
   * ```
   */
  public open fun startTimerQuery(): Boolean {
    TODO("Implement startTimerQuery")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void endTimerQuery() { SK_ABORT("Timer query unsupported."); }
   * ```
   */
  public open fun endTimerQuery() {
    TODO("Implement endTimerQuery")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<GpuStats> gpuStats() { return {}; }
   * ```
   */
  public open fun gpuStats(): Int {
    TODO("Implement gpuStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::addFinishedProc(sk_sp<RefCntedCallback> finishedProc) {
   *     fFinishedProcs.push_back(std::move(finishedProc));
   * }
   * ```
   */
  public fun addFinishedProc(finishedProc: SkSp<RefCntedCallback>) {
    TODO("Implement addFinishedProc")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::callFinishedProcs(bool success) {
   *     if (!success) {
   *         for (int i = 0; i < fFinishedProcs.size(); ++i) {
   *             fFinishedProcs[i]->setFailureResult();
   *         }
   *     } else {
   *         if (auto stats = this->gpuStats()) {
   *             for (int i = 0; i < fFinishedProcs.size(); ++i) {
   *                 if (fFinishedProcs[i]->receivesGpuStats()) {
   *                     fFinishedProcs[i]->setStats(*stats);
   *                 }
   *             }
   *         }
   *     }
   *     fFinishedProcs.clear();
   * }
   * ```
   */
  public fun callFinishedProcs(success: Boolean) {
    TODO("Implement callFinishedProcs")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void addWaitSemaphores(size_t numWaitSemaphores,
   *                                    const BackendSemaphore* waitSemaphores) {}
   * ```
   */
  public open fun addWaitSemaphores(numWaitSemaphores: ULong, waitSemaphores: BackendSemaphore?) {
    TODO("Implement addWaitSemaphores")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void addSignalSemaphores(size_t numWaitSemaphores,
   *                                      const BackendSemaphore* signalSemaphores) {}
   * ```
   */
  public open fun addSignalSemaphores(numWaitSemaphores: ULong, signalSemaphores: BackendSemaphore?) {
    TODO("Implement addSignalSemaphores")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void prepareSurfaceForStateUpdate(SkSurface* targetSurface,
   *                                               const MutableTextureState* newState) {}
   * ```
   */
  public open fun prepareSurfaceForStateUpdate(targetSurface: SkSurface?, newState: MutableTextureState?) {
    TODO("Implement prepareSurfaceForStateUpdate")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::addBuffersToAsyncMapOnSubmit(SkSpan<const sk_sp<Buffer>> buffers) {
   *     for (size_t i = 0; i < buffers.size(); ++i) {
   *         SkASSERT(buffers[i]);
   *         fBuffersToAsyncMap.push_back(buffers[i]);
   *     }
   * }
   * ```
   */
  public fun addBuffersToAsyncMapOnSubmit(buffers: SkSpan<SkSp<Buffer>>) {
    TODO("Implement addBuffersToAsyncMapOnSubmit")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<Buffer>> CommandBuffer::buffersToAsyncMapOnSubmit() const {
   *     return fBuffersToAsyncMap;
   * }
   * ```
   */
  public fun buffersToAsyncMapOnSubmit(): Int {
    TODO("Implement buffersToAsyncMapOnSubmit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::addRenderPass(const RenderPassDesc& renderPassDesc,
   *                                   sk_sp<Texture> colorTexture,
   *                                   sk_sp<Texture> resolveTexture,
   *                                   sk_sp<Texture> depthStencilTexture,
   *                                   const Texture* dstCopy,
   *                                   SkIRect dstReadBounds,
   *                                   SkIPoint resolveOffset,
   *                                   SkISize viewportDims,
   *                                   const DrawPassList& drawPasses) {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *
   *     SkIRect renderPassBounds;
   *     for (const auto& drawPass : drawPasses) {
   *         renderPassBounds.join(drawPass->bounds());
   *     }
   *     if (renderPassDesc.fColorAttachment.fLoadOp == LoadOp::kClear) {
   *         renderPassBounds.join(fRenderPassBounds);
   *     }
   *     renderPassBounds.offset(fReplayTranslation.x(), fReplayTranslation.y());
   *     if (!renderPassBounds.intersect(fRenderPassBounds)) {
   *         // The entire RenderPass is offscreen given the replay translation so skip adding the pass
   *         // at all
   *         return true;
   *     }
   *
   *     dstReadBounds.offset(fReplayTranslation);
   *     if (!dstReadBounds.intersect(fRenderPassBounds)) {
   *         // The draws within the RenderPass that would sample from the dstCopy have been translated
   *         // off screen. Set the bounds to empty and let the GPU clipping do its job.
   *         dstReadBounds = SkIRect::MakeEmpty();
   *     }
   *     // Save the dstCopy texture so that it can be embedded into texture bind commands later on.
   *     // Stash the texture's full dimensions on the rect so we can calculate normalized coords later.
   *     fDstCopy.first = dstCopy;
   *     fDstReadBounds = dstCopy ? SkIRect::MakePtSize(dstReadBounds.topLeft(), dstCopy->dimensions())
   *                              : SkIRect::MakeEmpty();
   *     if (dstCopy && !fDstCopy.second) {
   *         // Only lookup the sampler the first time we require a dstCopy. The texture can change
   *         // on subsequent passes but it will always use the same nearest neighbor sampling.
   *         sk_sp<Sampler> nearestNeighbor = this->resourceProvider()->findOrCreateCompatibleSampler(
   *                 {SkFilterMode::kNearest, SkTileMode::kClamp});
   *         fDstCopy.second = nearestNeighbor.get();
   *         this->trackResource(std::move(nearestNeighbor));
   *     }
   *
   *     // We don't intersect the viewport with the render pass bounds or target size because it just
   *     // defines a linear transform, which we don't want to change just because a portion of it maps
   *     // to a region that gets clipped.
   *     SkIRect viewport = SkIRect::MakePtSize(fReplayTranslation, viewportDims);
   *     if (!this->onAddRenderPass(renderPassDesc,
   *                                renderPassBounds,
   *                                colorTexture.get(),
   *                                resolveTexture.get(),
   *                                depthStencilTexture.get(),
   *                                resolveOffset,
   *                                viewport,
   *                                drawPasses)) {
   *         return false;
   *     }
   *
   *     if (colorTexture) {
   *         this->trackCommandBufferResource(std::move(colorTexture));
   *     }
   *     if (resolveTexture) {
   *         this->trackCommandBufferResource(std::move(resolveTexture));
   *     }
   *     if (depthStencilTexture) {
   *         this->trackCommandBufferResource(std::move(depthStencilTexture));
   *     }
   *     // We just assume if you are adding a render pass that the render pass will actually do work. In
   *     // theory we could have a discard load that doesn't submit any draws, clears, etc. But hopefully
   *     // something so trivial would be caught before getting here.
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun addRenderPass(
    renderPassDesc: RenderPassDesc,
    colorTexture: SkSp<Texture>,
    resolveTexture: SkSp<Texture>,
    depthStencilTexture: SkSp<Texture>,
    dstCopy: Texture?,
    dstReadBounds: SkIRect,
    resolveOffset: SkIPoint,
    viewportDims: SkISize,
    drawPasses: DrawPassList,
  ): Boolean {
    TODO("Implement addRenderPass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::addComputePass(DispatchGroupSpan dispatchGroups) {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *
   *     if (!this->onAddComputePass(dispatchGroups)) {
   *         return false;
   *     }
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun addComputePass(dispatchGroups: DispatchGroupSpan): Boolean {
    TODO("Implement addComputePass")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::copyBufferToBuffer(const Buffer* srcBuffer,
   *                                        size_t srcOffset,
   *                                        sk_sp<Buffer> dstBuffer,
   *                                        size_t dstOffset,
   *                                        size_t size) {
   *     SkASSERT(srcBuffer);
   *     SkASSERT(dstBuffer);
   *
   *     if (!this->onCopyBufferToBuffer(srcBuffer, srcOffset, dstBuffer.get(), dstOffset, size)) {
   *         return false;
   *     }
   *
   *     this->trackCommandBufferResource(std::move(dstBuffer));
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun copyBufferToBuffer(
    srcBuffer: Buffer?,
    srcOffset: ULong,
    dstBuffer: SkSp<Buffer>,
    dstOffset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement copyBufferToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::copyTextureToBuffer(sk_sp<Texture> texture,
   *                                         SkIRect srcRect,
   *                                         sk_sp<Buffer> buffer,
   *                                         size_t bufferOffset,
   *                                         size_t bufferRowBytes) {
   *     SkASSERT(texture);
   *     SkASSERT(buffer);
   *
   *     if (!this->onCopyTextureToBuffer(texture.get(), srcRect, buffer.get(), bufferOffset,
   *                                      bufferRowBytes)) {
   *         return false;
   *     }
   *
   *     this->trackCommandBufferResource(std::move(texture));
   *     this->trackCommandBufferResource(std::move(buffer));
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun copyTextureToBuffer(
    texture: SkSp<Texture>,
    srcRect: SkIRect,
    buffer: SkSp<Buffer>,
    bufferOffset: ULong,
    bufferRowBytes: ULong,
  ): Boolean {
    TODO("Implement copyTextureToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::copyBufferToTexture(const Buffer* buffer,
   *                                         sk_sp<Texture> texture,
   *                                         const BufferTextureCopyData* copyData,
   *                                         int count) {
   *     SkASSERT(buffer);
   *     SkASSERT(texture);
   *     SkASSERT(count > 0 && copyData);
   *
   *     if (!this->onCopyBufferToTexture(buffer, texture.get(), copyData, count)) {
   *         return false;
   *     }
   *
   *     this->trackCommandBufferResource(std::move(texture));
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun copyBufferToTexture(
    buffer: Buffer?,
    texture: SkSp<Texture>,
    copyData: BufferTextureCopyData?,
    count: Int,
  ): Boolean {
    TODO("Implement copyBufferToTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::copyTextureToTexture(sk_sp<Texture> src,
   *                                          SkIRect srcRect,
   *                                          sk_sp<Texture> dst,
   *                                          SkIPoint dstPoint,
   *                                          int mipLevel) {
   *     SkASSERT(src);
   *     SkASSERT(dst);
   *     if (src->textureInfo().isProtected() == Protected::kYes &&
   *         dst->textureInfo().isProtected() != Protected::kYes) {
   *         SKGPU_LOG_E("Can't copy from protected memory to non-protected");
   *         return false;
   *     }
   *
   *     if (!this->onCopyTextureToTexture(src.get(), srcRect, dst.get(), dstPoint, mipLevel)) {
   *         return false;
   *     }
   *
   *     this->trackCommandBufferResource(std::move(src));
   *     this->trackCommandBufferResource(std::move(dst));
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun copyTextureToTexture(
    src: SkSp<Texture>,
    srcRect: SkIRect,
    dst: SkSp<Texture>,
    dstPoint: SkIPoint,
    mipLevel: Int,
  ): Boolean {
    TODO("Implement copyTextureToTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::synchronizeBufferToCpu(sk_sp<Buffer> buffer) {
   *     SkASSERT(buffer);
   *
   *     bool didResultInWork = false;
   *     if (!this->onSynchronizeBufferToCpu(buffer.get(), &didResultInWork)) {
   *         return false;
   *     }
   *
   *     if (didResultInWork) {
   *         this->trackCommandBufferResource(std::move(buffer));
   *         SkDEBUGCODE(fHasWork = true;)
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun synchronizeBufferToCpu(buffer: SkSp<Buffer>): Boolean {
    TODO("Implement synchronizeBufferToCpu")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::clearBuffer(const Buffer* buffer, size_t offset, size_t size) {
   *     SkASSERT(buffer);
   *
   *     if (!this->onClearBuffer(buffer, offset, size)) {
   *         return false;
   *     }
   *
   *     SkDEBUGCODE(fHasWork = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun clearBuffer(
    buffer: Buffer?,
    offset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement clearBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CommandBuffer::setReplayTranslationAndClip(const SkIVector& translation,
   *                                                 const SkIRect& clip,
   *                                                 const SkIRect& renderTargetBounds) {
   *     fReplayTranslation = translation;
   *     fRenderPassBounds = renderTargetBounds;
   *
   *     // If a replay clip is defined, we intersect it with the render target bounds.
   *     if (!clip.isEmpty()) {
   *         if (!fRenderPassBounds.intersect(clip.makeOffset(translation))) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun setReplayTranslationAndClip(
    translation: SkIVector,
    clip: SkIRect,
    renderTargetBounds: SkIRect,
  ): Boolean {
    TODO("Implement setReplayTranslationAndClip")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const { return fIsProtected; }
   * ```
   */
  public fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * void CommandBuffer::releaseResources() {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *
   *     fTrackedUsageResources.clear();
   *     fCommandBufferResources.clear();
   * }
   * ```
   */
  private fun releaseResources() {
    TODO("Implement releaseResources")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual ResourceProvider* resourceProvider() const = 0
   * ```
   */
  private abstract fun resourceProvider(): ResourceProvider

  /**
   * C++ original:
   * ```cpp
   * virtual void onResetCommandBuffer() = 0
   * ```
   */
  private abstract fun onResetCommandBuffer()

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAddRenderPass(const RenderPassDesc&,
   *                                  SkIRect renderPassBounds,
   *                                  const Texture* colorTexture,
   *                                  const Texture* resolveTexture,
   *                                  const Texture* depthStencilTexture,
   *                                  SkIPoint resolveOffset,
   *                                  SkIRect viewport,
   *                                  const DrawPassList& drawPasses) = 0
   * ```
   */
  private abstract fun onAddRenderPass(
    param0: RenderPassDesc,
    renderPassBounds: SkIRect,
    colorTexture: Texture?,
    resolveTexture: Texture?,
    depthStencilTexture: Texture?,
    resolveOffset: SkIPoint,
    viewport: SkIRect,
    drawPasses: DrawPassList,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAddComputePass(DispatchGroupSpan dispatchGroups) = 0
   * ```
   */
  private abstract fun onAddComputePass(dispatchGroups: DispatchGroupSpan): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCopyBufferToBuffer(const Buffer* srcBuffer,
   *                                       size_t srcOffset,
   *                                       const Buffer* dstBuffer,
   *                                       size_t dstOffset,
   *                                       size_t size) = 0
   * ```
   */
  private abstract fun onCopyBufferToBuffer(
    srcBuffer: Buffer?,
    srcOffset: ULong,
    dstBuffer: Buffer?,
    dstOffset: ULong,
    size: ULong,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCopyTextureToBuffer(const Texture*,
   *                                        SkIRect srcRect,
   *                                        const Buffer*,
   *                                        size_t bufferOffset,
   *                                        size_t bufferRowBytes) = 0
   * ```
   */
  private abstract fun onCopyTextureToBuffer(
    param0: Texture?,
    srcRect: SkIRect,
    param2: Buffer?,
    bufferOffset: ULong,
    bufferRowBytes: ULong,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCopyBufferToTexture(const Buffer*,
   *                                        const Texture*,
   *                                        const BufferTextureCopyData*,
   *                                        int count) = 0
   * ```
   */
  private abstract fun onCopyBufferToTexture(
    param0: Buffer?,
    param1: Texture?,
    param2: BufferTextureCopyData?,
    count: Int,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCopyTextureToTexture(const Texture* src,
   *                                         SkIRect srcRect,
   *                                         const Texture* dst,
   *                                         SkIPoint dstPoint,
   *                                         int mipLevel) = 0
   * ```
   */
  private abstract fun onCopyTextureToTexture(
    src: Texture?,
    srcRect: SkIRect,
    dst: Texture?,
    dstPoint: SkIPoint,
    mipLevel: Int,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onSynchronizeBufferToCpu(const Buffer*, bool* outDidResultInWork) = 0
   * ```
   */
  private abstract fun onSynchronizeBufferToCpu(param0: Buffer?, outDidResultInWork: Boolean?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onClearBuffer(const Buffer*, size_t offset, size_t size) = 0
   * ```
   */
  private abstract fun onClearBuffer(
    param0: Buffer?,
    offset: ULong,
    size: ULong,
  ): Boolean

  public companion object {
    private val kInitialTrackedResourcesCount: Int =
        TODO("Initialize kInitialTrackedResourcesCount")
  }
}
