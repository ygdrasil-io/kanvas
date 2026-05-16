package org.skia.gpu

import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PipelineManager {
 * public:
 *     PipelineManager();
 *     ~PipelineManager();
 *
 *     GraphicsPipelineHandle createHandle(
 *             SharedContext*,
 *             const GraphicsPipelineDesc&,
 *             const RenderPassDesc&,
 *             SkEnumBitMask<PipelineCreationFlags>);
 *
 *     void startPipelineCreationTask(SharedContext*,
 *                                    sk_sp<const RuntimeEffectDictionary>,
 *                                    const GraphicsPipelineHandle&);
 *
 *     sk_sp<GraphicsPipeline> resolveHandle(const GraphicsPipelineHandle&);
 *
 * #if defined(GPU_TEST_UTILS)
 *     struct Stats {
 *         // The number of times we find a pre-existing task for a Pipeline
 *         int fNumPreemptivelyFoundTasks = 0;
 *         int fNumTasksCreated = 0;
 *         int fNumTaskCreationRaces = 0;
 *     };
 *
 *     Stats getStats() const SK_EXCLUDES(fSpinLock);
 * #endif
 *
 * private:
 *     mutable SkSpinlock fSpinLock;
 *
 *     sk_sp<PipelineCreationTask> findTask(const UniqueKey& pipelineKey) SK_EXCLUDES(fSpinLock);
 *
 *     sk_sp<PipelineCreationTask> findOrCreateTask(
 *             const UniqueKey& pipelineKey,
 *             const GraphicsPipelineDesc&,
 *             const RenderPassDesc&,
 *             SkEnumBitMask<PipelineCreationFlags>) SK_EXCLUDES(fSpinLock);
 *
 *     void removeTask(PipelineCreationTask*) SK_EXCLUDES(fSpinLock);
 *
 *     struct Traits {
 *         static const UniqueKey& GetKey(const sk_sp<PipelineCreationTask>&);
 *         static uint32_t Hash(const UniqueKey& pipelineKey);
 *     };
 *     using TaskMap = skia_private::THashTable<sk_sp<PipelineCreationTask>, UniqueKey, Traits>;
 *
 *     TaskMap fActiveTasks SK_GUARDED_BY(fSpinLock);
 *
 * #if defined(GPU_TEST_UTILS)
 *     Stats fStats SK_GUARDED_BY(fSpinLock);
 * #endif
 * }
 * ```
 */
public data class PipelineManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fSpinLock: Int,
  /**
   * C++ original:
   * ```cpp
   * TaskMap fActiveTasks
   * ```
   */
  private var fActiveTasks: Int,
  /**
   * C++ original:
   * ```cpp
   * Stats fStats
   * ```
   */
  private var fStats: Stats,
) {
  /**
   * C++ original:
   * ```cpp
   * GraphicsPipelineHandle PipelineManager::createHandle(
   *         SharedContext* sharedContext,
   *         const GraphicsPipelineDesc& pipelineDesc,
   *         const RenderPassDesc& renderPassDesc,
   *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags) {
   *     GlobalCache* globalCache = sharedContext->globalCache();
   *     const Caps* caps = sharedContext->caps();
   *
   *     UniqueKey pipelineKey = caps->makeGraphicsPipelineKey(pipelineDesc, renderPassDesc);
   *
   *     if (sk_sp<PipelineCreationTask> task = this->findTask(pipelineKey)) {
   *         // There is a task in progress to compile this Pipeline so it can't be ready yet (i.e.,
   *         // it isn't in the Pipeline Cache).
   *         return GraphicsPipelineHandle(std::move(task));
   *     }
   *
   *     sk_sp<GraphicsPipeline> pipeline = globalCache->findGraphicsPipeline(
   *             pipelineKey,
   *             pipelineCreationFlags);
   *     if (pipeline) {
   *         return GraphicsPipelineHandle(std::move(pipeline));
   *     }
   *
   *     sk_sp<PipelineCreationTask> task = this->findOrCreateTask(pipelineKey,
   *                                                               pipelineDesc,
   *                                                               renderPassDesc,
   *                                                               pipelineCreationFlags);
   *     return GraphicsPipelineHandle(std::move(task));
   * }
   * ```
   */
  public fun createHandle(
    sharedContext: SharedContext?,
    pipelineDesc: GraphicsPipelineDesc,
    renderPassDesc: RenderPassDesc,
    pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
  ): GraphicsPipelineHandle {
    TODO("Implement createHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * void PipelineManager::startPipelineCreationTask(SharedContext* sharedContext,
   *                                                 sk_sp<const RuntimeEffectDictionary> runtimeDict,
   *                                                 const GraphicsPipelineHandle& handle) {
   *     if (std::holds_alternative<sk_sp<GraphicsPipeline>>(handle.fTaskOrPipeline)) {
   *         return;
   *     }
   *
   *     sk_sp<PipelineCreationTask> task =
   *             std::get<sk_sp<PipelineCreationTask>>(handle.fTaskOrPipeline);
   *
   *     sk_sp<GraphicsPipeline> pipeline = sharedContext->findOrCreateGraphicsPipeline(
   *             runtimeDict.get(),
   *             task->fPipelineKey,
   *             task->fGraphicsPipelineDesc,
   *             task->fRenderPassDesc,
   *             task->fPipelineCreationFlags);
   *
   *     if (!pipeline) {
   *         SKGPU_LOG_W("Failed to create GraphicsPipeline!");
   *     }
   *
   *     if (!task->fCompleted.exchange(true)) {
   *         task->fPipeline = pipeline;
   *         this->removeTask(task.get());
   *     }
   * }
   * ```
   */
  public fun startPipelineCreationTask(
    sharedContext: SharedContext?,
    runtimeDict: SkSp<RuntimeEffectDictionary>,
    handle: GraphicsPipelineHandle,
  ) {
    TODO("Implement startPipelineCreationTask")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GraphicsPipeline> PipelineManager::resolveHandle(const GraphicsPipelineHandle& handle) {
   *     if (std::holds_alternative<sk_sp<GraphicsPipeline>>(handle.fTaskOrPipeline)) {
   *         return std::get<sk_sp<GraphicsPipeline>>(handle.fTaskOrPipeline);
   *     }
   *
   *     // Since 'fTaskOrPipeline' doesn't hold a pipeline the pipeline must not have existed when
   *     // the handle was created so a compilation task must've been created to compile it
   *     sk_sp<PipelineCreationTask> task =
   *             std::get<sk_sp<PipelineCreationTask>>(handle.fTaskOrPipeline);
   *
   *     // For the non-threaded version of the PipelineManager, whenever a thread gets here it
   *     // will already have blindly executed the task (in DrawPass::prepareResources).
   *     SkASSERT(task->fCompleted);
   *     return task->fPipeline;
   * }
   * ```
   */
  public fun resolveHandle(handle: GraphicsPipelineHandle): Int {
    TODO("Implement resolveHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * PipelineManager::Stats PipelineManager::getStats() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return fStats;
   * }
   * ```
   */
  public fun getStats(): Stats {
    TODO("Implement getStats")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PipelineCreationTask> PipelineManager::findTask(const UniqueKey& pipelineKey) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<PipelineCreationTask> task = fActiveTasks.findOrNull(pipelineKey);
   *
   * #if defined(GPU_TEST_UTILS)
   *     if (task) {
   *         fStats.fNumPreemptivelyFoundTasks++;
   *     }
   * #endif
   *
   *     return task;
   * }
   * ```
   */
  private fun findTask(pipelineKey: UniqueKey): Int {
    TODO("Implement findTask")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PipelineCreationTask> PipelineManager::findOrCreateTask(
   *         const UniqueKey& pipelineKey,
   *         const GraphicsPipelineDesc& pipelineDesc,
   *         const RenderPassDesc& renderPassDesc,
   *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<PipelineCreationTask>* task = fActiveTasks.find(pipelineKey);
   *     if (task) {
   *         // There is a race in createHandle from when we first check for a task; then, failing that,
   *         // check for an existing pipeline; then, failing that, try to create a new task. Thus,
   *         // we can sometimes find our task here.
   * #if defined(GPU_TEST_UTILS)
   *         fStats.fNumTaskCreationRaces++;
   * #endif
   *         return *task;
   *     }
   *
   * #if defined(GPU_TEST_UTILS)
   *     fStats.fNumTasksCreated++;
   * #endif
   *
   *     sk_sp<PipelineCreationTask> newTask = sk_sp<PipelineCreationTask>(
   *             new PipelineCreationTask(pipelineKey,
   *                                      pipelineDesc,
   *                                      renderPassDesc,
   *                                      pipelineCreationFlags));
   *     fActiveTasks.set(newTask);
   *     return newTask;
   * }
   * ```
   */
  private fun findOrCreateTask(
    pipelineKey: UniqueKey,
    pipelineDesc: GraphicsPipelineDesc,
    renderPassDesc: RenderPassDesc,
    pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
  ): Int {
    TODO("Implement findOrCreateTask")
  }

  /**
   * C++ original:
   * ```cpp
   * void PipelineManager::removeTask(PipelineCreationTask* task) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     // TODO(robertphillips): this guard is only necessary in the non-threaded version of
   *     // the PipelineManager
   *     if (fActiveTasks.findOrNull(task->fPipelineKey)) {
   *         fActiveTasks.remove(task->fPipelineKey);
   *     }
   * }
   * ```
   */
  private fun removeTask(task: PipelineCreationTask?) {
    TODO("Implement removeTask")
  }

  public data class Stats public constructor(
    public var fNumPreemptivelyFoundTasks: Int,
    public var fNumTasksCreated: Int,
    public var fNumTaskCreationRaces: Int,
  )

  public open class Traits {
    public companion object {
      public fun getKey(task: SkSp<PipelineCreationTask>): UniqueKey {
        TODO("Implement getKey")
      }

      public fun hash(pipelineKey: UniqueKey): Int {
        TODO("Implement hash")
      }
    }
  }
}
