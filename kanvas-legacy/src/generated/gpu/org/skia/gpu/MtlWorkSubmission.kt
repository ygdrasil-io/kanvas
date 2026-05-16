package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class MtlWorkSubmission final : public GpuWorkSubmission {
 * public:
 *     MtlWorkSubmission(std::unique_ptr<CommandBuffer> cmdBuffer, QueueManager* queueManager)
 *         : GpuWorkSubmission(std::move(cmdBuffer), queueManager) {}
 *     ~MtlWorkSubmission() override {}
 *
 * private:
 *     bool onIsFinished(const SharedContext*) override {
 *         return static_cast<MtlCommandBuffer*>(this->commandBuffer())->isFinished();
 *     }
 *     void onWaitUntilFinished(const SharedContext*) override {
 *         return static_cast<MtlCommandBuffer*>(this->commandBuffer())->waitUntilFinished();
 *     }
 * }
 * ```
 */
public class MtlWorkSubmission public constructor(
  cmdBuffer: CommandBuffer?,
  queueManager: QueueManager?,
) : GpuWorkSubmission(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool onIsFinished(const SharedContext*) override {
   *         return static_cast<MtlCommandBuffer*>(this->commandBuffer())->isFinished();
   *     }
   * ```
   */
  public override fun onIsFinished(param0: SharedContext?): Boolean {
    TODO("Implement onIsFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * void onWaitUntilFinished(const SharedContext*) override {
   *         return static_cast<MtlCommandBuffer*>(this->commandBuffer())->waitUntilFinished();
   *     }
   * ```
   */
  public override fun onWaitUntilFinished(param0: SharedContext?) {
    TODO("Implement onWaitUntilFinished")
  }
}
