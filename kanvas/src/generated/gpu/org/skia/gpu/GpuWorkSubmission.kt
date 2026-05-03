package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class GpuWorkSubmission {
 * public:
 *     virtual ~GpuWorkSubmission();
 *
 *     bool isFinished(const SharedContext* sharedContext);
 *     void waitUntilFinished(const SharedContext* sharedContext);
 *
 * protected:
 *     CommandBuffer* commandBuffer() { return fCommandBuffer.get(); }
 *
 *     GpuWorkSubmission(std::unique_ptr<CommandBuffer> cmdBuffer, QueueManager* queueManager);
 *
 * private:
 *     virtual bool onIsFinished(const SharedContext* sharedContext) = 0;
 *     virtual void onWaitUntilFinished(const SharedContext* sharedContext) = 0;
 *
 *     std::unique_ptr<CommandBuffer> fCommandBuffer;
 *     sk_sp<SkRefCnt> fOutstandingAsyncMapCounter;
 *     QueueManager* fQueueManager;
 * }
 * ```
 */
public abstract class GpuWorkSubmission public constructor(
  cmdBuffer: CommandBuffer?,
  queueManager: QueueManager?,
) {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<CommandBuffer> fCommandBuffer
   * ```
   */
  private var fCommandBuffer: Int = TODO("Initialize fCommandBuffer")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRefCnt> fOutstandingAsyncMapCounter
   * ```
   */
  private var fOutstandingAsyncMapCounter: Int = TODO("Initialize fOutstandingAsyncMapCounter")

  /**
   * C++ original:
   * ```cpp
   * QueueManager* fQueueManager
   * ```
   */
  private var fQueueManager: QueueManager? = TODO("Initialize fQueueManager")

  /**
   * C++ original:
   * ```cpp
   * bool GpuWorkSubmission::isFinished(const SharedContext* sharedContext) {
   *     return this->onIsFinished(sharedContext) &&
   *            (!fOutstandingAsyncMapCounter || fOutstandingAsyncMapCounter->unique());
   * }
   * ```
   */
  public fun isFinished(sharedContext: SharedContext?): Boolean {
    TODO("Implement isFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * void GpuWorkSubmission::waitUntilFinished(const SharedContext* sharedContext) {
   *     this->onWaitUntilFinished(sharedContext);
   *     if (fOutstandingAsyncMapCounter) {
   *         while (!fOutstandingAsyncMapCounter->unique()) {
   *             fQueueManager->tick();
   *         }
   *     }
   * }
   * ```
   */
  public fun waitUntilFinished(sharedContext: SharedContext?) {
    TODO("Implement waitUntilFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * CommandBuffer* commandBuffer() { return fCommandBuffer.get(); }
   * ```
   */
  protected fun commandBuffer(): CommandBuffer {
    TODO("Implement commandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsFinished(const SharedContext* sharedContext) = 0
   * ```
   */
  private abstract fun onIsFinished(sharedContext: SharedContext?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void onWaitUntilFinished(const SharedContext* sharedContext) = 0
   * ```
   */
  private abstract fun onWaitUntilFinished(sharedContext: SharedContext?)
}
