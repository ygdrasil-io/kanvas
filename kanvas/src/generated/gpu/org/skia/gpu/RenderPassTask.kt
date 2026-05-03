package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import undefined.ReplayTargetData
import DrawPassList as DrawPassList_
import undefined.DrawPassList as UndefinedDrawPassList

/**
 * C++ original:
 * ```cpp
 * class RenderPassTask final : public Task {
 * public:
 *     using DrawPassList = skia_private::STArray<1, std::unique_ptr<DrawPass>>;
 *
 *     // dstCopy should only be provided if the draw passes require a texture copy
 *     // for dst reads and must cover the union of all `DrawPass::dstReadBounds()` values in the
 *     // render pass. It is assumed that the copy's (0,0) texel matches the top-left corner of the
 *     // pass's dst copy bounds. The copy can be larger than the required bounds.
 *     static sk_sp<RenderPassTask> Make(DrawPassList,
 *                                       const RenderPassDesc&,
 *                                       sk_sp<TextureProxy> target,
 *                                       sk_sp<TextureProxy> dstCopy,
 *                                       SkIRect dstReadBounds);
 *
 *     ~RenderPassTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     bool visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor) override;
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
 *                       bool readsOnly) override;
 *
 *     SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "RenderPass Task"; })
 *
 * private:
 *     RenderPassTask(DrawPassList,
 *                    const RenderPassDesc&,
 *                    sk_sp<TextureProxy> target,
 *                    sk_sp<TextureProxy> dstCopy,
 *                    SkIRect dstReadBounds);
 *
 *     DrawPassList fDrawPasses;
 *     RenderPassDesc fRenderPassDesc;
 *     sk_sp<TextureProxy> fTarget;
 *
 *     sk_sp<TextureProxy> fDstCopy;
 *     SkIRect fDstReadBounds;
 * }
 * ```
 */
public class RenderPassTask public constructor(
  passes: UndefinedDrawPassList,
  desc: RenderPassDesc,
  target: SkSp<TextureProxy>,
  dstCopy: SkSp<TextureProxy>,
  dstReadBounds: SkIRect,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * DrawPassList fDrawPasses
   * ```
   */
  private var fDrawPasses: Int = TODO("Initialize fDrawPasses")

  /**
   * C++ original:
   * ```cpp
   * RenderPassDesc fRenderPassDesc
   * ```
   */
  private var fRenderPassDesc: Int = TODO("Initialize fRenderPassDesc")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTarget
   * ```
   */
  private var fTarget: Int = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fDstCopy
   * ```
   */
  private var fDstCopy: Int = TODO("Initialize fDstCopy")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fDstReadBounds
   * ```
   */
  private var fDstReadBounds: Int = TODO("Initialize fDstReadBounds")

  /**
   * C++ original:
   * ```cpp
   * Task::Status RenderPassTask::prepareResources(ResourceProvider* resourceProvider,
   *                                               ScratchResourceManager* scratchManager,
   *                                               sk_sp<const RuntimeEffectDictionary> runtimeDict) {
   *     SkASSERT(fTarget);
   *
   *     bool instantiated;
   *     if (scratchManager->pendingReadCount(fTarget.get()) == 0) {
   *         // TODO(b/389908339, b/338976898): If there are no pending reads on a scratch texture
   *         // instantiation request, it means that the scratch Device was caught by a
   *         // Recorder::flushTrackedDevices() event but hasn't actually been restored to its parent. In
   *         // this case, the eventual read of the surface will be in another Recording and it can't be
   *         // allocated as a true scratch resource.
   *         //
   *         // Without pending reads, DrawTask does not track its lifecycle to return the scratch
   *         // resource, so we need to match that and instantiate with a regular non-shareable resource.
   *         instantiated = TextureProxy::InstantiateIfNotLazy(resourceProvider, fTarget.get());
   *     } else {
   *         instantiated = TextureProxy::InstantiateIfNotLazy(scratchManager, fTarget.get());
   *     }
   *     if (!instantiated) {
   *         SKGPU_LOG_W("Failed to instantiate RenderPassTask target. Will not create renderpass!");
   *         SKGPU_LOG_W("Dimensions are (%d, %d).",
   *                     fTarget->dimensions().width(), fTarget->dimensions().height());
   *         return Status::kFail;
   *     }
   *
   *     // Assuming one draw pass per renderpasstask for now
   *     SkASSERT(fDrawPasses.size() == 1);
   *     for (const auto& drawPass: fDrawPasses) {
   *         if (!drawPass->prepareResources(resourceProvider, runtimeDict, fRenderPassDesc)) {
   *             return Status::kFail;
   *         }
   *     }
   *
   *     // Once all internal resources have been prepared and instantiated, reclaim any pending returns
   *     // from the scratch manager, since at the equivalent point in the task graph's addCommands()
   *     // phase, the renderpass will have sampled from any scratch textures and their contents no
   *     // longer have to be preserved.
   *     scratchManager->notifyResourcesConsumed();
   *     return Status::kSuccess;
   * }
   * ```
   */
  public override fun prepareResources(
    resourceProvider: ResourceProvider?,
    scratchManager: ScratchResourceManager?,
    runtimeDict: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status RenderPassTask::addCommands(Context* context,
   *                                          CommandBuffer* commandBuffer,
   *                                          ReplayTargetData replayData) {
   *     // TBD: Expose the surfaces that will need to be attached within the renderpass?
   *
   *     // Instantiate the target
   *     SkASSERT(fTarget && fTarget->isInstantiated());
   *     SkASSERT(!fDstCopy || fDstCopy->isInstantiated());
   *
   *     // Assuming one draw pass per renderpasstask for now
   *     SkASSERT(fDrawPasses.size() == 1);
   *     const auto& drawBounds = fDrawPasses[0]->bounds();
   *
   *     // Only apply the replay translation and clip if we're drawing to the final replay target.
   *     SkIVector replayTranslation = {0, 0};
   *     SkIRect replayClip = SkIRect::MakeEmpty();
   *     if (fTarget->texture() == replayData.fTarget) {
   *         replayTranslation = replayData.fTranslation;
   *         replayClip = replayData.fClip;
   *     }
   *
   *     // We don't instantiate the MSAA or DS attachments in prepareResources because we want to use
   *     // the discardable attachments from the Context.
   *     ResourceProvider* resourceProvider = context->priv().resourceProvider();
   *     sk_sp<Texture> colorAttachment;
   *     sk_sp<Texture> resolveAttachment;
   *     SkIPoint resolveOffset = SkIPoint::Make(0, 0);
   *     if (fRenderPassDesc.fColorResolveAttachment.fFormat != TextureFormat::kUnsupported) {
   *         // We always make color msaa attachments shareable. Between any render pass we discard
   *         // the values of the MSAA texture. Thus it is safe to be used by multiple different render
   *         // passes without worry of stomping on each other's data. CommandBuffer::addRenderPass is
   *         // responsible for loading this attachment with the resolve target's original contents.
   *         TextureInfo colorInfo = context->priv().caps()->getDefaultAttachmentTextureInfo(
   *                 fRenderPassDesc.fColorAttachment, fTarget->isProtected(), Discardable::kYes);
   *
   *         SkISize msaaSize;
   *         std::tie(msaaSize, resolveOffset) =
   *                 get_msaa_size_and_resolve_offset(fTarget->dimensions(),
   *                                                  drawBounds.makeOffset(replayTranslation),
   *                                                  *context->priv().caps(),
   *                                                  fRenderPassDesc.fColorAttachment.fLoadOp);
   *         colorAttachment = resourceProvider->findOrCreateShareableTexture(
   *                 msaaSize, colorInfo, "DiscardableMSAAAttachment");
   *         if (!colorAttachment) {
   *             SKGPU_LOG_W("Could not get Color attachment for RenderPassTask");
   *             return Status::kFail;
   *         }
   *         resolveAttachment = fTarget->refTexture();
   *     } else {
   *         colorAttachment = fTarget->refTexture();
   *     }
   *
   *     sk_sp<Texture> depthStencilAttachment;
   *     if (fRenderPassDesc.fDepthStencilAttachment.fFormat != TextureFormat::kUnsupported) {
   *         // We always make depth and stencil attachments shareable. Between any render pass the
   *         // values are reset. Thus it is safe to be used by multiple different render passes without
   *         // worry of stomping on each other's data.
   *         TextureInfo dsInfo = context->priv().caps()->getDefaultAttachmentTextureInfo(
   *                 fRenderPassDesc.fDepthStencilAttachment, fTarget->isProtected(), Discardable::kYes);
   *         SkISize dimensions = context->priv().caps()->getDepthAttachmentDimensions(
   *                 colorAttachment->textureInfo(), colorAttachment->dimensions());
   *
   *         depthStencilAttachment = resourceProvider->findOrCreateShareableTexture(
   *                 dimensions, dsInfo, "DepthStencilAttachment");
   *         if (!depthStencilAttachment) {
   *             SKGPU_LOG_W("Could not get DepthStencil attachment for RenderPassTask");
   *             return Status::kFail;
   *         }
   *     }
   *
   *     // The clip set here will intersect with the render target bounds, and then any scissor set
   *     // during this render pass. If there is no intersection between the clip and the render target
   *     // bounds, we can skip this entire render pass.
   *     // Note: if the MSAA texture is allocated smaller than the target texture, we need to apply an
   *     // additional translation (-resolveOffset) so that the draws' bounds' top left corner
   *     // will be at (0, 0) on the MSAA texture
   *     const SkIRect renderTargetBounds = SkIRect::MakeSize(colorAttachment->dimensions());
   *     if (!commandBuffer->setReplayTranslationAndClip(
   *                 replayTranslation - resolveOffset, replayClip, renderTargetBounds)) {
   *         return Status::kSuccess;
   *     }
   *
   *     // TODO(b/313629288) we always pass in the render target's dimensions as the viewport here.
   *     // Using the dimensions of the logical device that we're drawing to could reduce flakiness in
   *     // rendering.
   *     if (commandBuffer->addRenderPass(fRenderPassDesc,
   *                                      std::move(colorAttachment),
   *                                      std::move(resolveAttachment),
   *                                      std::move(depthStencilAttachment),
   *                                      fDstCopy ? fDstCopy->texture() : nullptr,
   *                                      fDstReadBounds,
   *                                      resolveOffset,
   *                                      fTarget->dimensions(),
   *                                      fDrawPasses)) {
   *         return Status::kSuccess;
   *     } else {
   *         return Status::kFail;
   *     }
   * }
   * ```
   */
  public override fun addCommands(
    context: Context?,
    commandBuffer: CommandBuffer?,
    replayData: ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RenderPassTask::visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor) {
   *     for (const std::unique_ptr<DrawPass>& pass : fDrawPasses) {
   *         for (const sk_sp<GraphicsPipeline>& pipeline : pass->pipelines()) {
   *             if (!visitor(pipeline.get())) {
   *                 return false;
   *             }
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public override fun visitPipelines(visitor: (GraphicsPipeline?) -> Boolean): Boolean {
    TODO("Implement visitPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RenderPassTask::visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
   *                                                            bool readsOnly) {
   *     for (const std::unique_ptr<DrawPass>& pass : fDrawPasses) {
   *         for (const sk_sp<TextureProxy>& proxy : pass->sampledTextures()) {
   *             if (!visitor(proxy.get())) {
   *                 return false;
   *             }
   *         }
   *
   *         if (fDstCopy && !visitor(fDstCopy.get())) {
   *             return false;
   *         }
   *
   *         // Skip visiting the target if we're only visiting read textures
   *         if (!readsOnly && fTarget && !visitor(fTarget.get())) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public override fun visitProxies(visitor: (TextureProxy?) -> Boolean, readsOnly: Boolean): Boolean {
    TODO("Implement visitProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "RenderPass Task"; })
   * ```
   */
  public override fun skDUMPTASKSCODE(param0: () -> String?): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<RenderPassTask> RenderPassTask::Make(DrawPassList passes,
     *                                            const RenderPassDesc& desc,
     *                                            sk_sp<TextureProxy> target,
     *                                            sk_sp<TextureProxy> dstCopy,
     *                                            SkIRect dstReadBounds) {
     *     // For now we have one DrawPass per RenderPassTask
     *     SkASSERT(passes.size() == 1);
     *     // If we have a dst copy texture, ensure it is big enough to cover the copy bounds that
     *     // will be sampled.
     *     SkASSERT(!dstCopy || (dstCopy->dimensions().width() >= dstReadBounds.width() &&
     *                           dstCopy->dimensions().height() >= dstReadBounds.height()));
     *     if (!target) {
     *         return nullptr;
     *     }
     *
     *     if (desc.fColorResolveAttachment.fFormat != TextureFormat::kUnsupported) {
     *         // The resolve attachment must match `target`, since that is what's resolved to.
     *         SkASSERT(desc.fColorResolveAttachment.isCompatible(target->textureInfo()));
     *         // The resolve attachment should be single sampled and not depth/stencil
     *         SkASSERT(desc.fColorResolveAttachment.fSampleCount == SampleCount::k1);
     *         SkASSERT(!TextureFormatIsDepthOrStencil(desc.fColorResolveAttachment.fFormat));
     *         // If there's a resolve attachment, the color attachment should have the same format and
     *         // more samples than the resolve.
     *         SkASSERT(desc.fColorAttachment.fFormat == desc.fColorResolveAttachment.fFormat);
     *         SkASSERT(desc.fColorAttachment.fSampleCount > SampleCount::k1);
     *         // The render pass's sample count must match the color attachment's sample count
     *         SkASSERT(desc.fSampleCount == desc.fColorAttachment.fSampleCount);
     *     } else {
     *         // The color attachment must match `target`, as it will be used to render directly into.
     *         SkASSERT(desc.fColorAttachment.isCompatible(target->textureInfo()));
     *         // The render pass's sample count must match or the color attachment's must be 1 and
     *         // the render pass has a higher sample count for msaa-render-to-single-sampled extensions.
     *         SkASSERT(desc.fColorAttachment.fSampleCount == desc.fSampleCount ||
     *                  (desc.fColorAttachment.fSampleCount == SampleCount::k1 &&
     *                   desc.fSampleCount > SampleCount::k1));
     *     }
     *
     *     if (desc.fDepthStencilAttachment.fFormat != TextureFormat::kUnsupported) {
     *         // The sample count for any depth/stencil buffer must match the color attachment
     *         SkASSERT(TextureFormatIsDepthOrStencil(desc.fDepthStencilAttachment.fFormat));
     *         SkASSERT(desc.fDepthStencilAttachment.fSampleCount == desc.fColorAttachment.fSampleCount);
     *     }
     *
     *     return sk_sp<RenderPassTask>(new RenderPassTask(std::move(passes),
     *                                                     desc,
     *                                                     std::move(target),
     *                                                     std::move(dstCopy),
     *                                                     dstReadBounds));
     * }
     * ```
     */
    public fun make(
      passes: DrawPassList_,
      desc: RenderPassDesc,
      target: SkSp<TextureProxy>,
      dstCopy: SkSp<TextureProxy>,
      dstReadBounds: SkIRect,
    ): Int {
      TODO("Implement make")
    }
  }
}
