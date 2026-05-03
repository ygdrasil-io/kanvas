package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DrawPass {
 * public:
 *     ~DrawPass();
 *
 *     // Defined relative to the top-left corner of the surface the DrawPass renders to, and is
 *     // contained within its dimensions.
 *     const SkIRect&      bounds() const { return fBounds;       }
 *     TextureProxy* target() const { return fTarget.get(); }
 *     FloatStorageManager* floatStorageManager() const { return fFloatStorageManager.get(); }
 *     std::pair<LoadOp, StoreOp> ops() const { return fOps; }
 *     std::array<float, 4> clearColor() const { return fClearColor; }
 *
 *     size_t vertexBufferSize()  const { return 0; }
 *     size_t uniformBufferSize() const { return 0; }
 *
 *     // Instantiate and prepare any resources used by the DrawPass that require the Recorder's
 *     // ResourceProvider. This includes things likes GraphicsPipelines, sampled Textures, Samplers,
 *     // etc.
 *     // Note that, due to possible threaded compilation, the Pipelines are not guaranteed to be
 *     // complete until Context::insertRecording time.
 *     bool prepareResources(ResourceProvider*,
 *                           sk_sp<const RuntimeEffectDictionary>,
 *                           const RenderPassDesc&);
 *
 *     DrawPassCommands::List::Iter commands() const {
 *         return fCommandList.commands();
 *     }
 *
 *     const GraphicsPipeline* getPipeline(size_t index) const {
 *         return fFullPipelines[index].get();
 *     }
 *
 *     // Proxies are always valid but may not be instantiated until after prepareResources() is called
 *     SkSpan<const sk_sp<TextureProxy>> sampledTextures() const { return fSampledTextures; }
 *     // Not valid until after prepareResources() is called
 *     SkSpan<const sk_sp<GraphicsPipeline>> pipelines() const { return fFullPipelines; }
 *
 *     [[nodiscard]] bool addResourceRefs(ResourceProvider*, CommandBuffer*);
 *
 * private:
 *     friend class DrawList; // For the constructor
 *
 *     DrawPass(sk_sp<TextureProxy> target,
 *              std::pair<LoadOp, StoreOp> ops,
 *              std::array<float, 4> clearColor,
 *              sk_sp<FloatStorageManager> floatStorageManager);
 *
 *     DrawPassCommands::List fCommandList;
 *
 *     sk_sp<TextureProxy> fTarget;
 *     SkIRect fBounds;
 *
 *     std::pair<LoadOp, StoreOp> fOps;
 *     std::array<float, 4> fClearColor;
 *
 *     // The pipelines are referenced by index in BindGraphicsPipeline, but that will index into
 *     // an array of actual GraphicsPipelines (i.e., fFullPipelines).
 *     skia_private::TArray<GraphicsPipelineDesc> fPipelineDescs;
 *     skia_private::TArray<float> fPipelineDrawAreas;
 *
 *     // These resources all get instantiated during prepareResources.
 *     skia_private::TArray<GraphicsPipelineHandle> fPipelineHandles;
 *     skia_private::TArray<sk_sp<TextureProxy>> fSampledTextures;
 *
 *     // These get resolved (from the GraphicsPipelineHandles) in prepareResources
 *     skia_private::TArray<sk_sp<GraphicsPipeline>> fFullPipelines;
 *
 *     sk_sp<FloatStorageManager> fFloatStorageManager;
 * }
 * ```
 */
public data class DrawPass public constructor(
  /**
   * C++ original:
   * ```cpp
   * DrawPass(sk_sp<TextureProxy> target,
   *              std::pair<LoadOp, StoreOp> ops,
   *              std::array<float, 4> clearColor,
   *              sk_sp<FloatStorageManager> floatStorageManager)
   * ```
   */
  private var skSp: DrawPass,
  /**
   * C++ original:
   * ```cpp
   * DrawPassCommands::List fCommandList
   * ```
   */
  private var fCommandList: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTarget
   * ```
   */
  private var fTarget: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fBounds
   * ```
   */
  private var fBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * std::pair<LoadOp, StoreOp> fOps
   * ```
   */
  private var fOps: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<float, 4> fClearColor
   * ```
   */
  private var fClearColor: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<GraphicsPipelineDesc> fPipelineDescs
   * ```
   */
  private var fPipelineDescs: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<float> fPipelineDrawAreas
   * ```
   */
  private var fPipelineDrawAreas: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<GraphicsPipelineHandle> fPipelineHandles
   * ```
   */
  private var fPipelineHandles: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<FloatStorageManager> fFloatStorageManager
   * ```
   */
  private var fFloatStorageManager: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkIRect&      bounds() const { return fBounds;       }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy* target() const { return fTarget.get(); }
   * ```
   */
  public fun target(): TextureProxy {
    TODO("Implement target")
  }

  /**
   * C++ original:
   * ```cpp
   * FloatStorageManager* floatStorageManager() const { return fFloatStorageManager.get(); }
   * ```
   */
  public fun floatStorageManager(): FloatStorageManager {
    TODO("Implement floatStorageManager")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<LoadOp, StoreOp> ops() const { return fOps; }
   * ```
   */
  public fun ops(): Int {
    TODO("Implement ops")
  }

  /**
   * C++ original:
   * ```cpp
   * std::array<float, 4> clearColor() const { return fClearColor; }
   * ```
   */
  public fun clearColor(): Int {
    TODO("Implement clearColor")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t vertexBufferSize()  const { return 0; }
   * ```
   */
  public fun vertexBufferSize(): Int {
    TODO("Implement vertexBufferSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t uniformBufferSize() const { return 0; }
   * ```
   */
  public fun uniformBufferSize(): Int {
    TODO("Implement uniformBufferSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawPass::prepareResources(ResourceProvider* resourceProvider,
   *                                 sk_sp<const RuntimeEffectDictionary> runtimeDict,
   *                                 const RenderPassDesc& renderPassDesc) {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *
   *     fPipelineHandles.reserve(fPipelineDescs.size());
   *     for (const GraphicsPipelineDesc& pipelineDesc : fPipelineDescs) {
   *         fPipelineHandles.push_back(
   *                 resourceProvider->createGraphicsPipelineHandle(pipelineDesc,
   *                                                                renderPassDesc,
   *                                                                PipelineCreationFlags::kNone));
   *         resourceProvider->startPipelineCreationTask(runtimeDict, fPipelineHandles.back());
   *     }
   *
   *     // The DrawPass may be long lived on a Recording and we no longer need the GraphicPipelineDescs
   *     // once we've created pipeline handles, so we drop the storage for them here.
   *     fPipelineDescs.clear();
   *
   *     // TODO(robertphillips): move this resolvePipeline loop to addResourceRefs
   *     fFullPipelines.reserve(fPipelineHandles.size());
   *     for (const GraphicsPipelineHandle& handle : fPipelineHandles) {
   *         sk_sp<GraphicsPipeline> pipeline = resourceProvider->resolveHandle(handle);
   *         if (!pipeline) {
   *             SKGPU_LOG_W("Failed to create GraphicsPipeline for draw in RenderPass. Dropping pass!");
   *             return false;
   *         }
   *         fFullPipelines.push_back(std::move(pipeline));
   *     }
   *     fPipelineHandles.clear();
   *
   *     for (int i = 0; i < fSampledTextures.size(); ++i) {
   *         // It should not have been possible to draw an Image that has an invalid texture info
   *         SkASSERT(fSampledTextures[i]->textureInfo().isValid());
   *         // Tasks should have been ordered to instantiate any scratch textures already, or any
   *         // client-owned image will have been instantiated at creation. However, if a TextureProxy
   *         // was cached for reuse across Recordings, it's possible that the initializing Recording
   *         // failed, leaving the TextureProxy in a bad state (and currently with no way to reconstruct
   *         // the tasks required to initialize it).
   *         // TODO(b/409888039): Once TextureProxies track their dependendent tasks to include in all
   *         // Recordings, this "should" be able to changed to asserts.
   *         if (!fSampledTextures[i]->isInstantiated() && !fSampledTextures[i]->isLazy()) {
   *             SKGPU_LOG_W("Cannot sample from an uninstantiated TextureProxy, label %s",
   *                         fSampledTextures[i]->label());
   *             return false;
   *         }
   *     }
   *
   *     // TODO(robertphillips): when fFullHandles resolution is moved to addResourceRefs, this will
   *     // either need to move there as well, or the label will have to be available on the
   *     // GraphicsPipelineHandle (plausible since we either have the pipeline with its label, or we
   *     // likely calculated the label as part of triggering a cache miss).
   *     {
   *         TRACE_EVENT0_ALWAYS("skia.shaders", "GraphitePipelineUse");
   *         TRACE_EVENT0_ALWAYS("skia.shaders", TRACE_STR_COPY(renderPassDesc.toString().c_str()));
   *         for (int i = 0 ; i < fFullPipelines.size(); ++i) {
   *             TRACE_EVENT_INSTANT1_ALWAYS(
   *                     "skia.shaders",
   *                     TRACE_STR_COPY(fFullPipelines[i]->getLabel()),
   *                     TRACE_EVENT_SCOPE_THREAD,
   *                     "area", sk_float_saturate2int(fPipelineDrawAreas[i]));
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun prepareResources(
    resourceProvider: ResourceProvider?,
    runtimeDict: SkSp<RuntimeEffectDictionary>,
    renderPassDesc: RenderPassDesc,
  ): Boolean {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawPassCommands::List::Iter commands() const {
   *         return fCommandList.commands();
   *     }
   * ```
   */
  public fun commands(): Int {
    TODO("Implement commands")
  }

  /**
   * C++ original:
   * ```cpp
   * const GraphicsPipeline* getPipeline(size_t index) const {
   *         return fFullPipelines[index].get();
   *     }
   * ```
   */
  public fun getPipeline(index: ULong): GraphicsPipeline {
    TODO("Implement getPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<TextureProxy>> sampledTextures() const { return fSampledTextures; }
   * ```
   */
  public fun sampledTextures(): Int {
    TODO("Implement sampledTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<GraphicsPipeline>> pipelines() const { return fFullPipelines; }
   * ```
   */
  public fun pipelines(): Int {
    TODO("Implement pipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawPass::addResourceRefs(ResourceProvider* resourceProvider,
   *                                CommandBuffer* commandBuffer) {
   *     for (int i = 0; i < fFullPipelines.size(); ++i) {
   *         commandBuffer->trackResource(fFullPipelines[i]);
   *     }
   *     for (int i = 0; i < fSampledTextures.size(); ++i) {
   *         commandBuffer->trackCommandBufferResource(fSampledTextures[i]->refTexture());
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun addResourceRefs(resourceProvider: ResourceProvider?, commandBuffer: CommandBuffer?): Boolean {
    TODO("Implement addResourceRefs")
  }
}
