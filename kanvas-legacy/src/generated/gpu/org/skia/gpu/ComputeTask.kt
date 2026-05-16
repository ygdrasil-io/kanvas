package org.skia.gpu

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import undefined.ReplayTargetData
import DispatchGroupList as DispatchGroupList_
import undefined.DispatchGroupList as UndefinedDispatchGroupList

/**
 * C++ original:
 * ```cpp
 * class ComputeTask final : public Task {
 * public:
 *     using DispatchGroupList = skia_private::STArray<1, std::unique_ptr<DispatchGroup>>;
 *
 *     static sk_sp<ComputeTask> Make(DispatchGroupList dispatchGroups);
 *
 *     ~ComputeTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     // TODO: Traverse child tasks too!
 *     SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Compute Task"; })
 *
 * private:
 *     explicit ComputeTask(DispatchGroupList dispatchGroups);
 *
 *     DispatchGroupList fDispatchGroups;
 *
 *     // Every element of this list is a task that must execute before the DispatchGroup stored at the
 *     // same array index. Child tasks are allowed to be a nullptr to represent NOP (i.e. the
 *     // corresponding DispatchGroup doesn't have any pre-tasks).
 *     skia_private::TArray<sk_sp<Task>> fChildTasks;
 * }
 * ```
 */
public class ComputeTask public constructor(
  dispatchGroups: UndefinedDispatchGroupList,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * DispatchGroupList fDispatchGroups
   * ```
   */
  private var fDispatchGroups: Int = TODO("Initialize fDispatchGroups")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<Task>> fChildTasks
   * ```
   */
  private var fChildTasks: Int = TODO("Initialize fChildTasks")

  /**
   * C++ original:
   * ```cpp
   * Task::Status ComputeTask::prepareResources(ResourceProvider* provider,
   *                                            ScratchResourceManager* scratchManager,
   *                                            sk_sp<const RuntimeEffectDictionary> rtd) {
   *     for (auto& child : fChildTasks) {
   *         if (child) {
   *             Status status = child->prepareResources(provider, scratchManager, rtd);
   *             if (status == Status::kFail) {
   *                 return Status::kFail;
   *             } else if (status == Status::kDiscard) {
   *                 child.reset();
   *             }
   *         }
   *     }
   *     for (const auto& group : fDispatchGroups) {
   *         // TODO: Allow ComputeTasks to instantiate with scratch textures and return them.
   *         if (!group->prepareResources(provider)) {
   *             return Status::kFail;
   *         }
   *     }
   *     return Status::kSuccess;
   * }
   * ```
   */
  public override fun prepareResources(
    provider: ResourceProvider?,
    scratchManager: ScratchResourceManager?,
    rtd: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status ComputeTask::addCommands(Context* ctx,
   *                                       CommandBuffer* commandBuffer,
   *                                       ReplayTargetData rtd) {
   *     if (fDispatchGroups.empty()) {
   *         return Status::kDiscard;
   *     }
   *     SkASSERT(fDispatchGroups.size() == fChildTasks.size());
   *     const std::unique_ptr<DispatchGroup>* currentSpanPtr = &fDispatchGroups[0];
   *     size_t currentSpanSize = 0u;
   *     for (int i = 0; i < fDispatchGroups.size(); ++i) {
   *         // If the next DispatchGroup has a dependent task, then encode the accumulated span as a
   *         // compute pass now. CommandBuffer encodes each compute pass with a separate encoder, so
   *         // the dependent task can use a non-compute encoder if needed.
   *         Task* child = fChildTasks[i].get();
   *         if (child) {
   *             if (currentSpanSize > 0u) {
   *                 if (!commandBuffer->addComputePass({currentSpanPtr, currentSpanSize})) {
   *                     return Status::kFail;
   *                 }
   *                 currentSpanPtr = &fDispatchGroups[i];
   *                 currentSpanSize = 0u;
   *             }
   *
   *             Status status = child->addCommands(ctx, commandBuffer, rtd);
   *             if (status == Status::kFail) {
   *                 return Status::kFail;
   *             } else if (status == Status::kDiscard) {
   *                 fChildTasks[i].reset();
   *             }
   *         }
   *         currentSpanSize++;
   *     }
   *     return (currentSpanSize == 0u ||
   *             commandBuffer->addComputePass({currentSpanPtr, currentSpanSize})) ? Status::kSuccess
   *                                                                               : Status::kFail;
   * }
   * ```
   */
  public override fun addCommands(
    ctx: Context?,
    commandBuffer: CommandBuffer?,
    rtd: ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Compute Task"; })
   * ```
   */
  public override fun skDUMPTASKSCODE(param0: () -> String?): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ComputeTask> ComputeTask::Make(DispatchGroupList dispatchGroups) {
     *     return sk_sp<ComputeTask>(new ComputeTask(std::move(dispatchGroups)));
     * }
     * ```
     */
    public fun make(dispatchGroups: DispatchGroupList_): Int {
      TODO("Implement make")
    }
  }
}
