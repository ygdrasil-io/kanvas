package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class DrawTask final : public Task, private ScratchResourceManager::PendingUseListener {
 * public:
 *     explicit DrawTask(sk_sp<TextureProxy> target);
 *
 *     ~DrawTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     bool visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor) override {
 *         return fChildTasks.visitPipelines(visitor);
 *     }
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
 *                       bool readsOnly) override {
 *         return fChildTasks.visitProxies(visitor, readsOnly);
 *     }
 *
 * private:
 *     friend class DrawContext; // for "addTask"
 *
 *     // DrawTask is modified directly by DrawContext for efficiency, but its task list will be
 *     // fixed once DrawContext snaps the task.
 *     void addTask(sk_sp<Task> task) { fChildTasks.add(std::move(task)); }
 *     bool hasTasks() const { return fChildTasks.hasTasks(); }
 *
 *     void onUseCompleted(ScratchResourceManager*) override;
 *
 *     sk_sp<TextureProxy> fTarget;
 *     TaskList fChildTasks;
 *
 *     // Once there is one DrawTask for a scratch device, whether or not the target is instantaited
 *     // will be equivalent to whether or not prepareResources() has been called already if the task
 *     // is referenced multiple times in a Recording. Right now, however, a scratch device can still
 *     // produce several DrawTasks (in which case they will see an instantiated proxy so should still
 *     // prepare their own resources instead of discarding themselves).
 *     bool fPrepared = false;
 *
 * #if defined(SK_DUMP_TASKS)
 *     void dump(int index, const char* prefix) const override {
 *         if (fTarget) {
 *             if (index >= 0) {
 *                 SkDebugf("%s%d: Draw Task=%p (Target=%p) (Label=%s)\n", prefix, index, this,
 *                          fTarget.get(), fTarget->label());
 *             } else {
 *                 SkDebugf("%sDraw Task=%p (Target=%p) (Label=%s)\n", prefix, this, fTarget.get(),
 *                          fTarget->label());
 *             }
 *         } else {
 *             if (index >= 0) {
 *                 SkDebugf("%s%d: Draw Task=%p (Target=%p)\n", prefix, index, this,
 *                          fTarget.get());
 *             } else {
 *                 SkDebugf("%sDraw Task=%p (Target=%p)\n", prefix, this, fTarget.get());
 *             }
 *         }
 *
 *         std::string childPrefix = prefix;
 *         static constexpr uint32_t kPrefixIncrement = 4;
 *         if (strlen(prefix) >= kPrefixIncrement) {
 *             const char* lastBranch = prefix + strlen(prefix) - kPrefixIncrement;
 *             if (strcmp(lastBranch, "│   ") == 0) {
 *                 childPrefix.replace(strlen(prefix) - kPrefixIncrement, kPrefixIncrement, "│   ");
 *             } else if (strcmp(lastBranch, "└── ") == 0) {
 *                 childPrefix.replace(strlen(prefix) - kPrefixIncrement, kPrefixIncrement, "    ");
 *             }
 *         }
 *
 *         fChildTasks.visit([&](const Task* task, bool isLast) {
 *             task->dump(-1, (childPrefix + (isLast ? "└── " : "│   ")).c_str());
 *         });
 *     }
 * #endif
 * }
 * ```
 */
public class DrawTask public constructor(
  target: SkSp<TextureProxy>,
) : Task(),
    ScratchResourceManager.PendingUseListener {
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
   * TaskList fChildTasks
   * ```
   */
  private var fChildTasks: Int = TODO("Initialize fChildTasks")

  /**
   * C++ original:
   * ```cpp
   * bool fPrepared = false
   * ```
   */
  private var fPrepared: Boolean = TODO("Initialize fPrepared")

  /**
   * C++ original:
   * ```cpp
   * Task::Status DrawTask::prepareResources(ResourceProvider* resourceProvider,
   *                                         ScratchResourceManager* scratchManager,
   *                                         sk_sp<const RuntimeEffectDictionary> rteDict) {
   *     const int pendingReadCount = scratchManager->pendingReadCount(fTarget.get());
   *     if (pendingReadCount) {
   *         // This DrawTask defines the content of a scratch device that has incremented the pending
   *         // read count before snap() was called. The target may have already been instantiated if
   *         // we've processed this task's children before.
   *         SkASSERT(!fTarget->isLazy());
   *         // Even though we may discard the task, we always want to mark it as in-use to track the
   *         // pending reads to know when to return the texture.;
   *         scratchManager->markResourceInUse(this);
   *
   *         if (fPrepared) {
   *             // If the task has already had prepareResources() called once, it should have had
   *             // its target instantiated.
   *             SkASSERT(fTarget->isInstantiated());
   *             // Return kDiscard so that this reference to the task is removed and the original
   *             // encounter in the graph will be the only time addCommands() is invoked.
   *             return Status::kDiscard;
   *         }
   *     } else {
   *         // A non-scratch DrawTask should only ever be in the task graph one time.
   *         SkASSERT(!fPrepared);
   *     }
   *
   *     fPrepared = true;
   *     // NOTE: This prepareResources() pushes a new scope for scratch resource management, which is
   *     // what we want since the child tasks are what will actually instantiate any scratch device and
   *     // trigger returns of any grand-child resources. The above markResourceInUse() should happen
   *     // above this so that pending returns are handled in caller's scope.
   *     return fChildTasks.prepareResources(resourceProvider, scratchManager, rteDict);
   * }
   * ```
   */
  public override fun prepareResources(
    resourceProvider: ResourceProvider?,
    scratchManager: ScratchResourceManager?,
    rteDict: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status DrawTask::addCommands(Context* ctx,
   *                                    CommandBuffer* commandBuffer,
   *                                    ReplayTargetData replayTarget) {
   *     SkASSERT(fTarget->isInstantiated());
   *     return fChildTasks.addCommands(ctx, commandBuffer, replayTarget);
   * }
   * ```
   */
  public override fun addCommands(
    ctx: Context?,
    commandBuffer: CommandBuffer?,
    replayTarget: ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * bool visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor) override {
   *         return fChildTasks.visitPipelines(visitor);
   *     }
   * ```
   */
  public override fun visitPipelines(visitor: (GraphicsPipeline?) -> Boolean): Boolean {
    TODO("Implement visitPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
   *                       bool readsOnly) override {
   *         return fChildTasks.visitProxies(visitor, readsOnly);
   *     }
   * ```
   */
  public override fun visitProxies(visitor: (TextureProxy?) -> Boolean, readsOnly: Boolean): Boolean {
    TODO("Implement visitProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * void addTask(sk_sp<Task> task) { fChildTasks.add(std::move(task)); }
   * ```
   */
  private fun addTask(task: SkSp<Task>) {
    TODO("Implement addTask")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasTasks() const { return fChildTasks.hasTasks(); }
   * ```
   */
  private fun hasTasks(): Boolean {
    TODO("Implement hasTasks")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawTask::onUseCompleted(ScratchResourceManager* scratchManager) {
   *     // Now that the render task has completed, actually decrement the read count of the target proxy
   *     // If the count hits zero, this was the last pending read that needed to use the DrawTask's
   *     // results so we can return the texture to the ScratchResourceManager for reuse.
   *     SkASSERT(!fTarget->isLazy() && fTarget->isInstantiated());
   *     SkASSERT(scratchManager->pendingReadCount(fTarget.get()) > 0);
   *     if (scratchManager->removePendingRead(fTarget.get())) {
   *         scratchManager->returnTexture(fTarget->refTexture());
   *     }
   * }
   * ```
   */
  public override fun onUseCompleted(scratchManager: ScratchResourceManager?) {
    TODO("Implement onUseCompleted")
  }
}
