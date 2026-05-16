package org.skia.gpu

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class SynchronizeToCpuTask final : public Task {
 * public:
 *     static sk_sp<SynchronizeToCpuTask> Make(sk_sp<Buffer>);
 *     ~SynchronizeToCpuTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override {
 *         return Status::kSuccess;
 *     }
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Sync to CPU Task"; })
 *
 * private:
 *     explicit SynchronizeToCpuTask(sk_sp<Buffer> buffer) : fBuffer(std::move(buffer)) {}
 *
 *     sk_sp<Buffer> fBuffer;
 * }
 * ```
 */
public class SynchronizeToCpuTask : Task() {
  /**
   * C++ original:
   * ```cpp
   * Status prepareResources(ResourceProvider*,
   *                             ScratchResourceManager*,
   *                             sk_sp<const RuntimeEffectDictionary>) override {
   *         return Status::kSuccess;
   *     }
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
   * Task::Status SynchronizeToCpuTask::addCommands(Context*,
   *                                                CommandBuffer* commandBuffer,
   *                                                ReplayTargetData) {
   *     return commandBuffer->synchronizeBufferToCpu(std::move(fBuffer)) ? Status::kSuccess
   *                                                                      : Status::kFail;
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
   * SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Sync to CPU Task"; })
   * ```
   */
  public override fun skDUMPTASKSCODE(param0: () -> String?): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SynchronizeToCpuTask> SynchronizeToCpuTask::Make(sk_sp<Buffer> buffer) {
     *     return sk_sp<SynchronizeToCpuTask>(new SynchronizeToCpuTask(std::move(buffer)));
     * }
     * ```
     */
    public fun make(buffer: SkSp<Buffer>): Int {
      TODO("Implement make")
    }
  }
}
