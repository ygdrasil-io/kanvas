package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.TArray
import org.skia.foundation.SkSp
import undefined.ReplayTargetData

/**
 * C++ original:
 * ```cpp
 * class UploadTask final : public Task {
 * public:
 *     static sk_sp<UploadTask> Make(UploadList*);
 *     static sk_sp<UploadTask> Make(UploadInstance);
 *
 *     ~UploadTask() override;
 *
 *     Status prepareResources(ResourceProvider*,
 *                             ScratchResourceManager*,
 *                             sk_sp<const RuntimeEffectDictionary>) override;
 *
 *     Status addCommands(Context*, CommandBuffer*, ReplayTargetData) override;
 *
 *     bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
 *                       bool readsOnly) override {
 *         // Textures being uploaded to are never read from, so skip all visiting unless readsOnly
 *         // is false.
 *         if (!readsOnly) {
 *             for (int32_t i = 0; i < fInstances.size(); ++i) {
 *                 if (fInstances[i].isValid() && !visitor(fInstances[i].fTextureProxy.get())) {
 *                     return false;
 *                 }
 *             }
 *         }
 *         return true;
 *     }
 *
 *     SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Upload Task"; })
 *
 * private:
 *     UploadTask(skia_private::TArray<UploadInstance>&&);
 *     UploadTask(UploadInstance);
 *
 *     skia_private::STArray<1, UploadInstance> fInstances;
 * }
 * ```
 */
public class UploadTask public constructor(
  instance: UploadInstance,
) : Task() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<1, UploadInstance> fInstances
   * ```
   */
  private var fInstances: Int = TODO("Initialize fInstances")

  /**
   * C++ original:
   * ```cpp
   * UploadTask::UploadTask(UploadInstance instance) {
   *     fInstances.emplace_back(std::move(instance));
   * }
   * ```
   */
  public constructor(instances: TArray<UploadInstance>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status UploadTask::prepareResources(ResourceProvider* resourceProvider,
   *                                           ScratchResourceManager*,
   *                                           sk_sp<const RuntimeEffectDictionary>) {
   *     for (int i = 0; i < fInstances.size(); ++i) {
   *         // No upload should be invalidated before prepareResources() is called.
   *         SkASSERT(fInstances[i].isValid());
   *         if (!fInstances[i].prepareResources(resourceProvider)) {
   *             return Status::kFail;
   *         }
   *     }
   *
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
   * Task::Status UploadTask::addCommands(Context* context,
   *                                      CommandBuffer* commandBuffer,
   *                                      ReplayTargetData replayData) {
   *     int discardCount = 0;
   *     for (int i = 0; i < fInstances.size(); ++i) {
   *         if (!fInstances[i].isValid()) {
   *             discardCount++;
   *             continue;
   *         }
   *         Status status = fInstances[i].addCommand(context, commandBuffer, replayData);
   *         if (status == Status::kFail) {
   *             return Status::kFail;
   *         } else if (status == Status::kDiscard) {
   *             fInstances[i] = UploadInstance::Invalid();
   *             discardCount++;
   *         }
   *     }
   *
   *     if (discardCount == fInstances.size()) {
   *         return Status::kDiscard;
   *     } else {
   *         return Status::kSuccess;
   *     }
   * }
   * ```
   */
  public override fun addCommands(
    context: Context?,
    commandBuffer: CommandBuffer?,
    replayData: ReplayTargetData,
  ): Int {
    TODO("Implement addCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * bool visitProxies(const std::function<bool(const TextureProxy*)>& visitor,
   *                       bool readsOnly) override {
   *         // Textures being uploaded to are never read from, so skip all visiting unless readsOnly
   *         // is false.
   *         if (!readsOnly) {
   *             for (int32_t i = 0; i < fInstances.size(); ++i) {
   *                 if (fInstances[i].isValid() && !visitor(fInstances[i].fTextureProxy.get())) {
   *                     return false;
   *                 }
   *             }
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun visitProxies(visitor: (TextureProxy?) -> Boolean, readsOnly: Boolean): Boolean {
    TODO("Implement visitProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_DUMP_TASKS_CODE(const char* getTaskName() const override { return "Upload Task"; })
   * ```
   */
  public override fun skDUMPTASKSCODE(param0: () -> String?): Int {
    TODO("Implement skDUMPTASKSCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<UploadTask> UploadTask::Make(UploadList* uploadList) {
   *     SkASSERT(uploadList);
   *     if (!uploadList->size()) {
   *         return nullptr;
   *     }
   *     return sk_sp<UploadTask>(new UploadTask(std::move(uploadList->fInstances)));
   * }
   * ```
   */
  public fun make(uploadList: UploadList?): SkSp<UploadTask> {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<UploadTask> UploadTask::Make(UploadInstance instance) {
   *     if (!instance.isValid()) {
   *         return nullptr;
   *     }
   *     return sk_sp<UploadTask>(new UploadTask(std::move(instance)));
   * }
   * ```
   */
  public fun make(instance: UploadInstance): SkSp<UploadTask> {
    TODO("Implement make")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<UploadTask> Make(UploadList*)
     * ```
     */
    public fun make(param0: UploadList?): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<UploadTask> Make(UploadInstance)
     * ```
     */
    public fun make(param0: UploadInstance): Int {
      TODO("Implement make")
    }
  }
}
