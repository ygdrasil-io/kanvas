package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkIVector

/**
 * C++ original:
 * ```cpp
 * class RecordingPriv {
 * public:
 *     TextureProxy* deferredTargetProxy();
 *     const Texture* setupDeferredTarget(ResourceProvider*,
 *                                        Surface* targetSurface,
 *                                        SkIVector targetTranslation,
 *                                        SkIRect targetClip);
 *
 *     bool hasVolatileLazyProxies() const;
 *     bool instantiateVolatileLazyProxies(ResourceProvider*);
 *     void deinstantiateVolatileLazyProxies();
 *
 *     bool hasNonVolatileLazyProxies() const;
 *     bool instantiateNonVolatileLazyProxies(ResourceProvider*);
 *
 *     void setFailureResultForFinishedProcs();
 *
 *     bool prepareResources(ResourceProvider*,
 *                           ScratchResourceManager*,
 *                           sk_sp<const RuntimeEffectDictionary>);
 *
 *     bool addCommands(Context*,
 *                      CommandBuffer*,
 *                      const Texture* replayTarget,
 *                      SkIVector targetTranslation,
 *                      SkIRect targetClip);
 *     // This will eventually lead to adding a Usage Ref on the CommandBuffer. For now that is fine
 *     // since the only Resource's we are reffing here are Buffers. However, if we ever want to track
 *     // Textures or GPU only Buffers as well, we should keep a second list for Refs that we want to
 *     // put CommandBuffer refs on.
 *     void addResourceRef(sk_sp<Resource> resource);
 *
 *     TaskList* taskList() { return fRecording->fRootTaskList.get(); }
 *
 *     uint32_t recorderID() const { return fRecording->fRecorderID; }
 *     uint32_t uniqueID() const { return fRecording->fUniqueID; }
 *
 * #if defined(GPU_TEST_UTILS)
 *     bool isTargetProxyInstantiated() const;
 *     int numVolatilePromiseImages() const;
 *     int numNonVolatilePromiseImages() const;
 *     bool hasTasks() const;
 * #endif
 *
 * private:
 *     explicit RecordingPriv(Recording* recorder) : fRecording(recorder) {}
 *     RecordingPriv& operator=(const RecordingPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const RecordingPriv* operator&() const = delete;
 *     RecordingPriv* operator&() = delete;
 *
 *     Recording* fRecording;
 *
 *     friend class Recording;  // to construct/copy this type.
 * }
 * ```
 */
public data class RecordingPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * Recording* fRecording
   * ```
   */
  private var fRecording: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * TextureProxy* RecordingPriv::deferredTargetProxy() {
   *     return fRecording->fTargetProxyData ? fRecording->fTargetProxyData->lazyProxy() : nullptr;
   * }
   * ```
   */
  public fun deferredTargetProxy(): TextureProxy {
    TODO("Implement deferredTargetProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * const Texture* RecordingPriv::setupDeferredTarget(ResourceProvider* resourceProvider,
   *                                                   Surface* targetSurface,
   *                                                   SkIVector targetTranslation,
   *                                                   SkIRect targetClip) {
   *     SkASSERT(targetSurface && fRecording->fTargetProxyData);
   *
   *     TextureProxy* surfaceTexture = targetSurface->backingTextureProxy();
   *     SkASSERT(surfaceTexture->isInstantiated());
   *
   *     const TextureProxy* targetProxy = fRecording->fTargetProxyData->lazyProxy();
   *     if (surfaceTexture->mipmapped() != targetProxy->mipmapped()) {
   *         SKGPU_LOG_E("Deferred canvas mipmap settings don't match instantiating target's.");
   *         return nullptr;
   *     }
   *
   *     // If the deferred canvas's texture proxy is not fully lazy, that means we used it for draws
   *     // that require specific dimensions and no translation. The only time this happens is when a
   *     // client requests a mipmapped deferred canvas and we automatically insert commands to
   *     // regenerate mipmaps.
   *     if (!targetProxy->isFullyLazy()) {
   *         SkASSERT(targetProxy->mipmapped() == skgpu::Mipmapped::kYes);
   *         if (targetProxy->dimensions() != surfaceTexture->dimensions()) {
   *             SKGPU_LOG_E(
   *                     "Deferred canvas dimensions don't match instantiating target's dimensions.");
   *             return nullptr;
   *         }
   *         if (!targetTranslation.isZero()) {
   *             SKGPU_LOG_E(
   *                     "Replay translation is not allowed when replaying draws to a mipmapped "
   *                     "deferred canvas.");
   *             return nullptr;
   *         }
   *         if (!targetClip.isEmpty()) {
   *             SKGPU_LOG_E(
   *                     "Replay clip is not allowed when replaying draws to a mipmapped deferred "
   *                     "canvas.");
   *             return nullptr;
   *         }
   *     }
   *
   *     if (!fRecording->fTargetProxyData->lazyInstantiate(resourceProvider,
   *                                                        surfaceTexture->refTexture())) {
   *         SKGPU_LOG_E("Could not instantiate deferred texture proxy.");
   *         return nullptr;
   *     }
   *     return surfaceTexture->texture();
   * }
   * ```
   */
  public fun setupDeferredTarget(
    resourceProvider: ResourceProvider?,
    targetSurface: Surface?,
    targetTranslation: SkIVector,
    targetClip: SkIRect,
  ): Texture {
    TODO("Implement setupDeferredTarget")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::hasVolatileLazyProxies() const {
   *     return !fRecording->fVolatileLazyProxies.empty();
   * }
   * ```
   */
  public fun hasVolatileLazyProxies(): Boolean {
    TODO("Implement hasVolatileLazyProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::instantiateVolatileLazyProxies(ResourceProvider* resourceProvider) {
   *     SkASSERT(this->hasVolatileLazyProxies());
   *
   *     for (const auto& proxy : fRecording->fVolatileLazyProxies) {
   *         if (!proxy->lazyInstantiate(resourceProvider)) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun instantiateVolatileLazyProxies(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement instantiateVolatileLazyProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecordingPriv::deinstantiateVolatileLazyProxies() {
   *     if (!this->hasVolatileLazyProxies()) {
   *         return;
   *     }
   *
   *     for (const auto& proxy : fRecording->fVolatileLazyProxies) {
   *         SkASSERT(proxy->isVolatile());
   *         proxy->deinstantiate();
   *     }
   * }
   * ```
   */
  public fun deinstantiateVolatileLazyProxies() {
    TODO("Implement deinstantiateVolatileLazyProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::hasNonVolatileLazyProxies() const {
   *     return !fRecording->fNonVolatileLazyProxies.empty();
   * }
   * ```
   */
  public fun hasNonVolatileLazyProxies(): Boolean {
    TODO("Implement hasNonVolatileLazyProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::instantiateNonVolatileLazyProxies(ResourceProvider* resourceProvider) {
   *     SkASSERT(this->hasNonVolatileLazyProxies());
   *
   *     for (const auto& proxy : fRecording->fNonVolatileLazyProxies) {
   *         if (!proxy->lazyInstantiate(resourceProvider)) {
   *             return false;
   *         }
   *     }
   *
   *     // Note: once all the lazy proxies have been instantiated, that's it - there are no more
   *     // chances to instantiate.
   *     fRecording->fNonVolatileLazyProxies.clear();
   *     return true;
   * }
   * ```
   */
  public fun instantiateNonVolatileLazyProxies(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement instantiateNonVolatileLazyProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecordingPriv::setFailureResultForFinishedProcs() {
   *     for (int i = 0; i < fRecording->fFinishedProcs.size(); ++i) {
   *         fRecording->fFinishedProcs[i]->setFailureResult();
   *     }
   *     fRecording->fFinishedProcs.clear();
   * }
   * ```
   */
  public fun setFailureResultForFinishedProcs() {
    TODO("Implement setFailureResultForFinishedProcs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::prepareResources(ResourceProvider* resourceProvider,
   *                                      ScratchResourceManager* scratchManager,
   *                                      sk_sp<const RuntimeEffectDictionary> rteDict) {
   *     Task::Status status = fRecording->fRootTaskList->prepareResources(
   *             resourceProvider, scratchManager, rteDict);
   *     if (status == Task::Status::kSuccess) {
   *         fRecording->fRootTaskList->visitProxies([&](const TextureProxy* proxy) {
   *             if (proxy->isLazy()) {
   *                 if (proxy->isVolatile()) {
   *                     fRecording->fVolatileLazyProxies.insert(sk_ref_sp(proxy));
   *                 } else {
   *                     fRecording->fNonVolatileLazyProxies.insert(sk_ref_sp(proxy));
   *                 }
   *             }
   *             return true;
   *         }, /*readsOnly=*/false);
   *     }
   *
   *     return status != Task::Status::kFail;
   * }
   * ```
   */
  public fun prepareResources(
    resourceProvider: ResourceProvider?,
    scratchManager: ScratchResourceManager?,
    rteDict: SkSp<RuntimeEffectDictionary>,
  ): Boolean {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::addCommands(Context* context,
   *                                 CommandBuffer* commandBuffer,
   *                                 const Texture* replayTarget,
   *                                 SkIVector targetTranslation,
   *                                 SkIRect targetClip) {
   *     for (size_t i = 0; i < fRecording->fExtraResourceRefs.size(); ++i) {
   *         commandBuffer->trackCommandBufferResource(fRecording->fExtraResourceRefs[i]);
   *     }
   *
   *     // There's no need to differentiate kSuccess and kDiscard at the root list level; if every task
   *     // is discarded, the Recording will automatically be a no-op on replay while still correctly
   *     // notifying any finish procs the client may have added.
   *     if (fRecording->fRootTaskList->addCommands(
   *                 context, commandBuffer, {replayTarget, targetTranslation, targetClip}) ==
   *         Task::Status::kFail) {
   *         return false;
   *     }
   *     for (int i = 0; i < fRecording->fFinishedProcs.size(); ++i) {
   *         commandBuffer->addFinishedProc(std::move(fRecording->fFinishedProcs[i]));
   *     }
   *     fRecording->fFinishedProcs.clear();
   *
   *     return true;
   * }
   * ```
   */
  public fun addCommands(
    context: Context?,
    commandBuffer: CommandBuffer?,
    replayTarget: Texture?,
    targetTranslation: SkIVector,
    targetClip: SkIRect,
  ): Boolean {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecordingPriv::addResourceRef(sk_sp<Resource> resource) {
   *     fRecording->fExtraResourceRefs.push_back(std::move(resource));
   * }
   * ```
   */
  public fun addResourceRef(resource: SkSp<Resource>) {
    TODO("Implement addResourceRef")
  }

  /**
   * C++ original:
   * ```cpp
   * TaskList* taskList() { return fRecording->fRootTaskList.get(); }
   * ```
   */
  public fun taskList(): TaskList {
    TODO("Implement taskList")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t recorderID() const { return fRecording->fRecorderID; }
   * ```
   */
  public fun recorderID(): Int {
    TODO("Implement recorderID")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fRecording->fUniqueID; }
   * ```
   */
  public fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::isTargetProxyInstantiated() const {
   *     return fRecording->fTargetProxyData->lazyProxy()->isInstantiated();
   * }
   * ```
   */
  public fun isTargetProxyInstantiated(): Boolean {
    TODO("Implement isTargetProxyInstantiated")
  }

  /**
   * C++ original:
   * ```cpp
   * int RecordingPriv::numVolatilePromiseImages() const {
   *     return fRecording->fVolatileLazyProxies.size();
   * }
   * ```
   */
  public fun numVolatilePromiseImages(): Int {
    TODO("Implement numVolatilePromiseImages")
  }

  /**
   * C++ original:
   * ```cpp
   * int RecordingPriv::numNonVolatilePromiseImages() const {
   *     return fRecording->fNonVolatileLazyProxies.size();
   * }
   * ```
   */
  public fun numNonVolatilePromiseImages(): Int {
    TODO("Implement numNonVolatilePromiseImages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecordingPriv::hasTasks() const {
   *     return fRecording->fRootTaskList->hasTasks();
   * }
   * ```
   */
  public fun hasTasks(): Boolean {
    TODO("Implement hasTasks")
  }

  /**
   * C++ original:
   * ```cpp
   * RecordingPriv& operator=(const RecordingPriv&) = delete
   * ```
   */
  private fun assign(param0: RecordingPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const RecordingPriv* operator&() const = delete
   * ```
   */
  private fun addressOf(): RecordingPriv {
    TODO("Implement addressOf")
  }
}
