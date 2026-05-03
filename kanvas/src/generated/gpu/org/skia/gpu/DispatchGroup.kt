package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class DispatchGroup final {
 * public:
 *     class Builder;
 *
 *     struct Dispatch {
 *         WorkgroupSize fLocalSize;
 *         std::variant<WorkgroupSize, BindBufferInfo> fGlobalSizeOrIndirect;
 *
 *         std::optional<WorkgroupSize> fGlobalDispatchSize;
 *         skia_private::TArray<ResourceBinding> fBindings;
 *         skia_private::TArray<ComputeStep::WorkgroupBufferDesc> fWorkgroupBuffers;
 *         int fPipelineIndex = 0;
 *     };
 *
 *     ~DispatchGroup();
 *
 *     const skia_private::TArray<Dispatch>& dispatches() const { return fDispatchList; }
 *
 *     const ComputePipeline* getPipeline(size_t index) const { return fPipelines[index].get(); }
 *     const Texture* getTexture(size_t index) const;
 *     const Sampler* getSampler(size_t index) const;
 *
 *     bool prepareResources(ResourceProvider*);
 *     void addResourceRefs(CommandBuffer*) const;
 *
 *     // Returns a single tasks that must execute before this DispatchGroup or nullptr if the group
 *     // has no task dependencies.
 *     sk_sp<Task> snapChildTask();
 *
 * private:
 *     friend class DispatchGroupBuilder;
 *
 *     DispatchGroup() = default;
 *
 *     // Disallow copy and move.
 *     DispatchGroup(const DispatchGroup&) = delete;
 *     DispatchGroup(DispatchGroup&&) = delete;
 *
 *     skia_private::TArray<Dispatch> fDispatchList;
 *
 *     // The list of all buffers that must be cleared before the dispatches.
 *     skia_private::TArray<BindBufferInfo> fClearList;
 *
 *     // Pipelines are referenced by index by each Dispatch in `fDispatchList`. They are stored as a
 *     // pipeline description until instantiated in `prepareResources()`.
 *     skia_private::TArray<ComputePipelineDesc> fPipelineDescs;
 *     skia_private::TArray<SamplerDesc> fSamplerDescs;
 *
 *     // Resources instantiated by `prepareResources()`
 *     skia_private::TArray<sk_sp<ComputePipeline>> fPipelines;
 *     skia_private::TArray<sk_sp<TextureProxy>> fTextures;
 *     skia_private::TArray<sk_sp<Sampler>> fSamplers;
 * }
 * ```
 */
public data class DispatchGroup public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Dispatch> fDispatchList
   * ```
   */
  private var fDispatchList: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<BindBufferInfo> fClearList
   * ```
   */
  private var fClearList: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<ComputePipelineDesc> fPipelineDescs
   * ```
   */
  private var fPipelineDescs: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SamplerDesc> fSamplerDescs
   * ```
   */
  private var fSamplerDescs: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<ComputePipeline>> fPipelines
   * ```
   */
  private var fPipelines: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<TextureProxy>> fTextures
   * ```
   */
  private var fTextures: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<Sampler>> fSamplers
   * ```
   */
  private var fSamplers: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<Dispatch>& dispatches() const { return fDispatchList; }
   * ```
   */
  public fun dispatches(): Int {
    TODO("Implement dispatches")
  }

  /**
   * C++ original:
   * ```cpp
   * const ComputePipeline* getPipeline(size_t index) const { return fPipelines[index].get(); }
   * ```
   */
  public fun getPipeline(index: ULong): ComputePipeline {
    TODO("Implement getPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * const Texture* DispatchGroup::getTexture(size_t index) const {
   *     SkASSERT(index < SkToSizeT(fTextures.size()));
   *     SkASSERT(fTextures[index]);
   *     SkASSERT(fTextures[index]->texture());
   *     return fTextures[index]->texture();
   * }
   * ```
   */
  public fun getTexture(index: ULong): Texture {
    TODO("Implement getTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * const Sampler* DispatchGroup::getSampler(size_t index) const {
   *     SkASSERT(index < SkToSizeT(fSamplers.size()));
   *     SkASSERT(fSamplers[index]);
   *     return fSamplers[index].get();
   * }
   * ```
   */
  public fun getSampler(index: ULong): Sampler {
    TODO("Implement getSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DispatchGroup::prepareResources(ResourceProvider* resourceProvider) {
   *     fPipelines.reserve(fPipelines.size() + fPipelineDescs.size());
   *     for (const ComputePipelineDesc& desc : fPipelineDescs) {
   *         auto pipeline = resourceProvider->findOrCreateComputePipeline(desc);
   *         if (!pipeline) {
   *             SKGPU_LOG_W("Failed to create ComputePipeline for dispatch group. Dropping group!");
   *             return false;
   *         }
   *         fPipelines.push_back(std::move(pipeline));
   *     }
   *
   *     for (int i = 0; i < fTextures.size(); ++i) {
   *         if (!fTextures[i]->textureInfo().isValid()) {
   *             SKGPU_LOG_W("Failed to validate bound texture. Dropping dispatch group!");
   *             return false;
   *         }
   *         if (!TextureProxy::InstantiateIfNotLazy(resourceProvider, fTextures[i].get())) {
   *             SKGPU_LOG_W("Failed to instantiate bound texture. Dropping dispatch group!");
   *             return false;
   *         }
   *     }
   *
   *     for (const SamplerDesc& desc : fSamplerDescs) {
   *         sk_sp<Sampler> sampler = resourceProvider->findOrCreateCompatibleSampler(desc);
   *         if (!sampler) {
   *             SKGPU_LOG_W("Failed to create sampler. Dropping dispatch group!");
   *             return false;
   *         }
   *         fSamplers.push_back(std::move(sampler));
   *     }
   *
   *     // The DispatchGroup may be long lived on a Recording and we no longer need the descriptors
   *     // once we've created pipelines.
   *     fPipelineDescs.clear();
   *     fSamplerDescs.clear();
   *
   *     return true;
   * }
   * ```
   */
  public fun prepareResources(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void DispatchGroup::addResourceRefs(CommandBuffer* commandBuffer) const {
   *     for (int i = 0; i < fPipelines.size(); ++i) {
   *         commandBuffer->trackResource(fPipelines[i]);
   *     }
   *     for (int i = 0; i < fTextures.size(); ++i) {
   *         commandBuffer->trackCommandBufferResource(fTextures[i]->refTexture());
   *     }
   * }
   * ```
   */
  public fun addResourceRefs(commandBuffer: CommandBuffer?) {
    TODO("Implement addResourceRefs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Task> DispatchGroup::snapChildTask() {
   *     if (fClearList.empty()) {
   *         return nullptr;
   *     }
   *     return ClearBuffersTask::Make(std::move(fClearList));
   * }
   * ```
   */
  public fun snapChildTask(): Int {
    TODO("Implement snapChildTask")
  }

  public data class Dispatch public constructor(
    public var fLocalSize: Int,
    public var fGlobalSizeOrIndirect: Int,
    public var fGlobalDispatchSize: Int,
    public var fBindings: Int,
    public var fWorkgroupBuffers: Int,
    public var fPipelineIndex: Int,
  )
}
