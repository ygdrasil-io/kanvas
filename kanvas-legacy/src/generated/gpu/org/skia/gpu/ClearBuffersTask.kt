package org.skia.gpu

import kotlin.Int
import kotlin.String
import org.skia.core.TArray
import org.skia.foundation.SkSp
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class ClearBuffersTask final : public Task {
 * public:
 *     static sk_sp<ClearBuffersTask> Make(skia_private::TArray<BindBufferInfo>);
 *     ~ClearBuffersTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override {
 *         return Status::kSuccess;
 *     }
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Clear Buffers Task"; })
 *
 * private:
 *     explicit ClearBuffersTask(skia_private::TArray<BindBufferInfo> clearList)
 *             : fClearList(std::move(clearList)) {}
 *
 *     skia_private::TArray<BindBufferInfo> fClearList;
 * }
 * ```
 */
public class ClearBuffersTask : Task() {
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
   * Task::Status ClearBuffersTask::addCommands(Context*,
   *                                            CommandBuffer* commandBuffer,
   *                                            ReplayTargetData) {
   *     bool result = true;
   *     for (const auto& c : fClearList) {
   *         result &= commandBuffer->clearBuffer(c.fBuffer, c.fOffset, c.fSize);
   *     }
   *     return result ? Status::kSuccess : Status::kFail;
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
   * SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Clear Buffers Task"; })
   * ```
   */
  public override fun skDUMPTASKSCODE(param0: () -> String?): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ClearBuffersTask> ClearBuffersTask::Make(skia_private::TArray<BindBufferInfo> clearList) {
     *     return sk_sp<ClearBuffersTask>(new ClearBuffersTask(std::move(clearList)));
     * }
     * ```
     */
    public fun make(clearList: TArray<BindBufferInfo>): Int {
      TODO("Implement make")
    }
  }
}
