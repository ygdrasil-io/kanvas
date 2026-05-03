package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class CopyBufferToBufferTask final : public Task {
 * public:
 *     // The srcBuffer for this Task is always a transfer buffer which is owned by the
 *     // UploadBufferManager. Thus we don't have to take a ref to it as the UploadBufferManager will
 *     // handle its refs and passing them to the Recording.
 *     static sk_sp<CopyBufferToBufferTask> Make(const Buffer* srcBuffer,
 *                                               size_t srcOffset,
 *                                               sk_sp<Buffer> dstBuffer,
 *                                               size_t dstOffset,
 *                                               size_t size);
 *
 *     ~CopyBufferToBufferTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 * #if defined(SK_DUMP_TASKS)
 *     void dump(int index, const char* prefix) const override {
 *         if (index >= 0) {
 *             SkDebugf("%s%d: Copy BtoB Task: Src=%p Dst=%p\n", prefix, index, fSrcBuffer,
 *                 fDstBuffer.get());
 *         } else {
 *             SkDebugf("%sCopy BtoB Task: Src=%p Dst=%p\n", prefix, fSrcBuffer, fDstBuffer.get());
 *         }
 *     }
 * #endif
 *
 * private:
 *     CopyBufferToBufferTask(const Buffer* srcBuffer,
 *                            size_t srcOffset,
 *                            sk_sp<Buffer> dstBuffer,
 *                            size_t dstOffset,
 *                            size_t size);
 *
 *     const Buffer* fSrcBuffer;
 *     size_t        fSrcOffset;
 *     sk_sp<Buffer> fDstBuffer;
 *     size_t        fDstOffset;
 *     size_t        fSize;
 * }
 * ```
 */
public class CopyBufferToBufferTask public constructor(
  srcBuffer: Buffer?,
  srcOffset: ULong,
  dstBuffer: SkSp<Buffer>,
  dstOffset: ULong,
  size: ULong,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * const Buffer* fSrcBuffer
   * ```
   */
  private val fSrcBuffer: Buffer? = TODO("Initialize fSrcBuffer")

  /**
   * C++ original:
   * ```cpp
   * size_t        fSrcOffset
   * ```
   */
  private var fSrcOffset: Int = TODO("Initialize fSrcOffset")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Buffer> fDstBuffer
   * ```
   */
  private var fDstBuffer: Int = TODO("Initialize fDstBuffer")

  /**
   * C++ original:
   * ```cpp
   * size_t        fDstOffset
   * ```
   */
  private var fDstOffset: Int = TODO("Initialize fDstOffset")

  /**
   * C++ original:
   * ```cpp
   * size_t        fSize
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * Task::Status CopyBufferToBufferTask::prepareResources(ResourceProvider*,
   *                                                       ScratchResourceManager*,
   *                                                       sk_sp<const RuntimeEffectDictionary>) {
   *     return Status::kSuccess;
   * }
   * ```
   */
  public override fun prepareResources(
    param0: ResourceProvider?,
    param1: ScratchResourceManager?,
    param2: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status CopyBufferToBufferTask::addCommands(Context*,
   *                                                  CommandBuffer* commandBuffer,
   *                                                  ReplayTargetData) {
   *     if (commandBuffer->copyBufferToBuffer(fSrcBuffer, fSrcOffset, fDstBuffer, fDstOffset, fSize)) {
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

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<CopyBufferToBufferTask> CopyBufferToBufferTask::Make(const Buffer* srcBuffer,
     *                                                            size_t srcOffset,
     *                                                            sk_sp<Buffer> dstBuffer,
     *                                                            size_t dstOffset,
     *                                                            size_t size) {
     *     SkASSERT(srcBuffer);
     *     SkASSERT(size <= srcBuffer->size() - srcOffset);
     *     SkASSERT(dstBuffer);
     *     SkASSERT(size <= dstBuffer->size() - dstOffset);
     *     return sk_sp<CopyBufferToBufferTask>(new CopyBufferToBufferTask(srcBuffer,
     *                                                                     srcOffset,
     *                                                                     std::move(dstBuffer),
     *                                                                     dstOffset,
     *                                                                     size));
     * }
     * ```
     */
    public fun make(
      srcBuffer: Buffer?,
      srcOffset: ULong,
      dstBuffer: SkSp<Buffer>,
      dstOffset: ULong,
      size: ULong,
    ): Int {
      TODO("Implement make")
    }
  }
}
