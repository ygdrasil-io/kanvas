package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class CopyTextureToBufferTask final : public Task {
 * public:
 *     static sk_sp<CopyTextureToBufferTask> Make(sk_sp<TextureProxy>,
 *                                                SkIRect srcRect,
 *                                                sk_sp<Buffer>,
 *                                                size_t bufferOffset,
 *                                                size_t bufferRowBytes);
 *
 *     ~CopyTextureToBufferTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
 *                       bool readsOnly) override {
 *         // The texture is the source of the copy, so it's always read
 *         return visitor(fTextureProxy.get());
 *     }
 *
 * #if defined(SK_DUMP_TASKS)
 *     void dump(int index, const char* prefix) const override {
 *         if (index >= 0) {
 *             SkDebugf("%s%d: Copy TtoB Task: Texture=%p Buffer=%p\n", prefix, index,
 *                 fTextureProxy.get(), fBuffer.get());
 *         } else {
 *             SkDebugf("%sCopy TtoB Task: Texture=%p Buffer=%p\n", prefix, fTextureProxy.get(),
 *                 fBuffer.get());
 *         }
 *     }
 * #endif
 *
 * private:
 *     CopyTextureToBufferTask(sk_sp<TextureProxy>,
 *                             SkIRect srcRect,
 *                             sk_sp<Buffer>,
 *                             size_t bufferOffset,
 *                             size_t bufferRowBytes);
 *
 *     sk_sp<TextureProxy> fTextureProxy;
 *     SkIRect fSrcRect;
 *     sk_sp<Buffer> fBuffer;
 *     size_t fBufferOffset;
 *     size_t fBufferRowBytes;
 * }
 * ```
 */
public class CopyTextureToBufferTask public constructor(
  textureProxy: SkSp<TextureProxy>,
  srcRect: SkIRect,
  buffer: SkSp<Buffer>,
  bufferOffset: ULong,
  bufferRowBytes: ULong,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * CopyTextureToBufferTask(sk_sp<TextureProxy>,
   *                             SkIRect srcRect,
   *                             sk_sp<Buffer>,
   *                             size_t bufferOffset,
   *                             size_t bufferRowBytes)
   * ```
   */
  private var skSp: CopyTextureToBufferTask = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTextureProxy
   * ```
   */
  private var fTextureProxy: Int = TODO("Initialize fTextureProxy")

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
   * sk_sp<Buffer> fBuffer
   * ```
   */
  private var fBuffer: Int = TODO("Initialize fBuffer")

  /**
   * C++ original:
   * ```cpp
   * size_t fBufferOffset
   * ```
   */
  private var fBufferOffset: Int = TODO("Initialize fBufferOffset")

  /**
   * C++ original:
   * ```cpp
   * size_t fBufferRowBytes
   * ```
   */
  private var fBufferRowBytes: Int = TODO("Initialize fBufferRowBytes")

  /**
   * C++ original:
   * ```cpp
   * Task::Status CopyTextureToBufferTask::prepareResources(ResourceProvider* resourceProvider,
   *                                                        ScratchResourceManager*,
   *                                                        sk_sp<const RuntimeEffectDictionary>) {
   *     // If the source texture hasn't been instantiated yet, it means there was no prior task that
   *     // could have initialized its contents so a readback to a buffer does not make sense.
   *     SkASSERT(fTextureProxy->isInstantiated() || fTextureProxy->isLazy());
   *     // TODO: The copy is also a consumer of the source, so it should participate in returning
   *     // scratch resources like RenderPassTask does. For now, though, all copy tasks side step reuse
   *     // entirely and they cannot participate until they've been moved into scoping tasks like
   *     // DrawTask first.
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
   * Task::Status CopyTextureToBufferTask::addCommands(Context*,
   *                                                   CommandBuffer* commandBuffer,
   *                                                   ReplayTargetData) {
   *     if (commandBuffer->copyTextureToBuffer(fTextureProxy->refTexture(),
   *                                            fSrcRect,
   *                                            std::move(fBuffer),
   *                                            fBufferOffset,
   *                                            fBufferRowBytes)) {
   *         // TODO(b/332681367): CopyTextureToBuffer is currently only used for readback operations,
   *         // which are a one-time event. Should this just default to returning kDiscard?
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
   *         // The texture is the source of the copy, so it's always read
   *         return visitor(fTextureProxy.get());
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
     * sk_sp<CopyTextureToBufferTask> CopyTextureToBufferTask::Make(sk_sp<TextureProxy> textureProxy,
     *                                                              SkIRect srcRect,
     *                                                              sk_sp<Buffer> buffer,
     *                                                              size_t bufferOffset,
     *                                                              size_t bufferRowBytes) {
     *     if (!textureProxy) {
     *         return nullptr;
     *     }
     *     return sk_sp<CopyTextureToBufferTask>(new CopyTextureToBufferTask(std::move(textureProxy),
     *                                                                       srcRect,
     *                                                                       std::move(buffer),
     *                                                                       bufferOffset,
     *                                                                       bufferRowBytes));
     * }
     * ```
     */
    public fun make(
      textureProxy: SkSp<TextureProxy>,
      srcRect: SkIRect,
      buffer: SkSp<Buffer>,
      bufferOffset: ULong,
      bufferRowBytes: ULong,
    ): Int {
      TODO("Implement make")
    }
  }
}
