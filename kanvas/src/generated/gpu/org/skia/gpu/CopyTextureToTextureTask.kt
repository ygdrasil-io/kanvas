package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class CopyTextureToTextureTask final : public Task {
 * public:
 *     static sk_sp<CopyTextureToTextureTask> Make(sk_sp<TextureProxy> srcProxy,
 *                                                 SkIRect srcRect,
 *                                                 sk_sp<TextureProxy> dstProxy,
 *                                                 SkIPoint dstPoint,
 *                                                 int dstLevel = 0);
 *
 *     ~CopyTextureToTextureTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
 *                       bool readsOnly) override {
 *         // Only visit fDstProxy if readsOnly is false; fSrcProxy is the only texture being read.
 *         return visitor(fSrcProxy.get()) && (readsOnly || visitor(fDstProxy.get()));
 *     }
 *
 * #if defined(SK_DUMP_TASKS)
 *     void dump(int index, const char* prefix) const override {
 *         if (index >= 0) {
 *             SkDebugf("%s%d: Copy TtoT Task: Src=%p Dst=%p\n", prefix, index, fSrcProxy.get(),
 *                                                               fDstProxy.get());
 *         } else {
 *             SkDebugf("%sCopy TtoT Task: Src=%p Dst=%p\n", prefix, fSrcProxy.get(), fDstProxy.get());
 *         }
 *     }
 * #endif
 *
 * private:
 *     CopyTextureToTextureTask(sk_sp<TextureProxy> srcProxy,
 *                              SkIRect srcRect,
 *                              sk_sp<TextureProxy> dstProxy,
 *                              SkIPoint dstPoint,
 *                              int dstLevel);
 *
 *     sk_sp<TextureProxy> fSrcProxy;
 *     SkIRect fSrcRect;
 *     sk_sp<TextureProxy> fDstProxy;
 *     SkIPoint fDstPoint;
 *     int fDstLevel;
 * }
 * ```
 */
public abstract class CopyTextureToTextureTask public constructor(
  srcProxy: SkSp<TextureProxy>,
  srcRect: SkIRect,
  dstProxy: SkSp<TextureProxy>,
  dstPoint: SkIPoint,
  dstLevel: Int,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * CopyTextureToTextureTask(sk_sp<TextureProxy> srcProxy,
   *                              SkIRect srcRect,
   *                              sk_sp<TextureProxy> dstProxy,
   *                              SkIPoint dstPoint,
   *                              int dstLevel)
   * ```
   */
  private var skSp: CopyTextureToTextureTask = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fSrcProxy
   * ```
   */
  private var fSrcProxy: Int = TODO("Initialize fSrcProxy")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fSrcRect
   * ```
   */
  private var fSrcRect: Int = TODO("Initialize fSrcRect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fDstProxy
   * ```
   */
  private var fDstProxy: Int = TODO("Initialize fDstProxy")

  /**
   * C++ original:
   * ```cpp
   * SkIPoint fDstPoint
   * ```
   */
  private var fDstPoint: Int = TODO("Initialize fDstPoint")

  /**
   * C++ original:
   * ```cpp
   * int fDstLevel
   * ```
   */
  private var fDstLevel: Int = TODO("Initialize fDstLevel")

  /**
   * C++ original:
   * ```cpp
   * Task::Status CopyTextureToTextureTask::prepareResources(ResourceProvider* resourceProvider,
   *                                                         ScratchResourceManager*,
   *                                                         sk_sp<const RuntimeEffectDictionary>) {
   *     // Do not instantiate the src proxy. If the source texture hasn't been instantiated yet, it
   *     // means there was no prior task that could have initialized its contents so propagating the
   *     // undefined contents to the dst does not make sense.
   *     // TODO(b/333729316): Assert that fSrcProxy is instantiated or lazy; right now it may not be
   *     // instantatiated if this is a dst readback copy for a scratch Device. In that case, a
   *     // RenderPassTask will immediately follow this copy task and instantiate the source proxy so
   *     // that addCommands() has a texture to operate on. That said, the texture's contents will be
   *     // undefined when the copy is executed ideally it just shouldn't happen.
   *
   *     // TODO: The copy is also a consumer of the source, so it should participate in returning
   *     // scratch resources like RenderPassTask does. For now, though, all copy tasks side step reuse
   *     // entirely and they cannot participate until they've been moved into scoping tasks like
   *     // DrawTask first. In particular, for texture-to-texture copies, they should be scoped to not
   *     // invoke pending listeners for a subsequent RenderPassTask.
   *
   *     // TODO: Use the scratch resource manager to instantiate fDstProxy, although the details of when
   *     // that texture can be returned need to be worked out. While brittle, all current use cases
   *     // of scratch texture-to-texture copies have the dst used immediately by the next task, so it
   *     // could just add a pending listener that returns the texture w/o any read counting.
   *     if (!TextureProxy::InstantiateIfNotLazy(resourceProvider, fDstProxy.get())) {
   *         SKGPU_LOG_E("Could not instantiate dst texture proxy for CopyTextureToTextureTask!");
   *         return Status::kFail;
   *     }
   *     return Status::kSuccess;
   * }
   * ```
   */
  public override fun prepareResources(
    resourceProvider: ResourceProvider?,
    param1: ScratchResourceManager?,
    param2: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status CopyTextureToTextureTask::addCommands(Context*,
   *                                                    CommandBuffer* commandBuffer,
   *                                                    ReplayTargetData) {
   *     // prepareResources() doesn't instantiate the source assuming that a prior task will have do so
   *     // as part of initializing the texture contents.
   *     SkASSERT(fSrcProxy->isInstantiated());
   *     if (commandBuffer->copyTextureToTexture(fSrcProxy->refTexture(),
   *                                             fSrcRect,
   *                                             fDstProxy->refTexture(),
   *                                             fDstPoint,
   *                                             fDstLevel)) {
   *         // TODO(b/332681367): The calling context should be able to specify whether or not this copy
   *         // is a repeatable operation (e.g. dst readback copy for blending) or one time (e.g. client
   *         // asked for a copy of an image or surface).
   *         return Status::kSuccess;
   *     } else {
   *         return Status::kFail;
   *     }
   * }
   * ```
   */
  public override fun addCommands(
    param0: Context?,
    commandBuffer: CommandBuffer?,
    param2: ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
   *                       bool readsOnly) override {
   *         // Only visit fDstProxy if readsOnly is false; fSrcProxy is the only texture being read.
   *         return visitor(fSrcProxy.get()) && (readsOnly || visitor(fDstProxy.get()));
   *     }
   * ```
   */
  public override fun visitProxies(visitor: (TextureProxy?) -> Boolean, readsOnly: Boolean): Boolean {
    TODO("Implement visitProxies")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<CopyTextureToTextureTask> CopyTextureToTextureTask::Make(sk_sp<TextureProxy> srcProxy,
     *                                                                SkIRect srcRect,
     *                                                                sk_sp<TextureProxy> dstProxy,
     *                                                                SkIPoint dstPoint,
     *                                                                int dstLevel) {
     *     if (!srcProxy || !dstProxy) {
     *         return nullptr;
     *     }
     *     return sk_sp<CopyTextureToTextureTask>(new CopyTextureToTextureTask(std::move(srcProxy),
     *                                                                         srcRect,
     *                                                                         std::move(dstProxy),
     *                                                                         dstPoint,
     *                                                                         dstLevel));
     * }
     * ```
     */
    public fun make(
      srcProxy: SkSp<TextureProxy>,
      srcRect: SkIRect,
      dstProxy: SkSp<TextureProxy>,
      dstPoint: SkIPoint,
      dstLevel: Int = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
