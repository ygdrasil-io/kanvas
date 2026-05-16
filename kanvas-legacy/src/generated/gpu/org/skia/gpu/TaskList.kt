package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkSp
import undefined.Fn

/**
 * C++ original:
 * ```cpp
 * class TaskList {
 * public:
 *     TaskList() = default;
 *
 *     void add(TaskList&& tasks) { fTasks.move_back(tasks.fTasks); }
 *     void add(sk_sp<Task> task) { fTasks.emplace_back(std::move(task)); }
 *     void reset() { fTasks.clear(); }
 *
 *     int size() const { return fTasks.size(); }
 *     bool hasTasks() const { return !fTasks.empty(); }
 *
 *     // Returns kSuccess if no child task failed and at least one child didn't return kDiscard.
 *     // Returns kDiscard if all children were discarded.
 *     // Returns kFail if any child failed.
 *     // Automatically removes tasks from its list if they return kDiscard.
 *     Task::Status prepareResources(ResourceProvider*,
 *                                   ScratchResourceManager*,
 *                                   sk_sp<const RuntimeEffectDictionary>);
 *     Task::Status addCommands(Context*, CommandBuffer*, Task::ReplayTargetData);
 *
 *     bool visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor);
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor, bool readsOnly);
 *
 *     SK_DUMP_TASKS_CODE(
 *             void visit(const std::function<void(const Task* task, bool isLast)>& visitor) const;)
 * private:
 *     template <typename Fn> // (Task*)->Status
 *     Task::Status visitTasks(Fn);
 *
 *     skia_private::TArray<sk_sp<Task>> fTasks;
 * }
 * ```
 */
public data class TaskList public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<Task>> fTasks
   * ```
   */
  private var fTasks: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void add(TaskList&& tasks) { fTasks.move_back(tasks.fTasks); }
   * ```
   */
  public fun add(tasks: TaskList) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(sk_sp<Task> task) { fTasks.emplace_back(std::move(task)); }
   * ```
   */
  public fun add(task: SkSp<Task>) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() { fTasks.clear(); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * int size() const { return fTasks.size(); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasTasks() const { return !fTasks.empty(); }
   * ```
   */
  public fun hasTasks(): Boolean {
    TODO("Implement hasTasks")
  }

  /**
   * C++ original:
   * ```cpp
   * Status TaskList::prepareResources(ResourceProvider* resourceProvider,
   *                                   ScratchResourceManager* scratchManager,
   *                                   sk_sp<const RuntimeEffectDictionary> runtimeDict) {
   *     TRACE_EVENT1("skia.gpu", TRACE_FUNC, "# tasks", fTasks.size());
   *     scratchManager->pushScope();
   *     Status status = this->visitTasks([&](Task* task) {
   *         return task->prepareResources(resourceProvider, scratchManager, runtimeDict);
   *     });
   *     scratchManager->popScope();
   *     return status;
   * }
   * ```
   */
  public fun prepareResources(
    resourceProvider: ResourceProvider?,
    scratchManager: ScratchResourceManager?,
    runtimeDict: SkSp<RuntimeEffectDictionary>,
  ): Int {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Status TaskList::addCommands(Context* context,
   *                              CommandBuffer* commandBuffer,
   *                              Task::ReplayTargetData replayData) {
   *     TRACE_EVENT1("skia.gpu", TRACE_FUNC, "# tasks", fTasks.size());
   *     return this->visitTasks([&](Task* task) {
   *         return task->addCommands(context, commandBuffer, replayData);
   *     });
   * }
   * ```
   */
  public fun addCommands(
    context: Context?,
    commandBuffer: CommandBuffer?,
    replayData: Task.ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TaskList::visitPipelines(const std::function<bool(const GraphicsPipeline*)>& visitor) {
   *     Status status = this->visitTasks([&](Task* task) {
   *         return task->visitPipelines(visitor) ? Status::kSuccess : Status::kFail;
   *     });
   *     // Map back to simple bool (treat kDiscard as true too, no pipelines to visit means all
   *     // pipelines were visited).
   *     return status != Status::kFail;
   * }
   * ```
   */
  public fun visitPipelines(visitor: (GraphicsPipeline?) -> Boolean): Boolean {
    TODO("Implement visitPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TaskList::visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
   *                             bool readsOnly) {
   *     Status status = this->visitTasks([&](Task* task) {
   *         return task->visitProxies(visitor, readsOnly) ? Status::kSuccess : Status::kFail;
   *     });
   *     // Map back to simple bool (treat kDiscard as true too, no pipelines to visit means all
   *     // pipelines were visited).
   *     return status != Status::kFail;
   * }
   * ```
   */
  public fun visitProxies(visitor: (TextureProxy?) -> Boolean, readsOnly: Boolean): Boolean {
    TODO("Implement visitProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_DUMP_TASKS_CODE(
   *             void visit(const std::function<void(const Task* task, bool isLast)>& visitor) const;)
   * ```
   */
  public fun skDUMPTASKSCODE(param0: (Int) -> Unit): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename Fn>
   * Status TaskList::visitTasks(Fn fn) {
   *     int discardCount = 0;
   *     for (sk_sp<Task>& task: fTasks) {
   *         if (!task) {
   *             discardCount++;
   *             continue; // Skip over discarded tasks
   *         }
   *
   *         Status status = fn(task.get());
   *         if (status == Status::kFail) {
   *             return Status::kFail;
   *         } else if (status == Status::kDiscard) {
   *             task.reset();
   *             discardCount++;
   *         }
   *     }
   *
   *     return discardCount == fTasks.size() ? Status::kDiscard : Status::kSuccess;
   * }
   * ```
   */
  public fun visitTasks(fn: Fn): Status {
    TODO("Implement visitTasks")
  }
}
