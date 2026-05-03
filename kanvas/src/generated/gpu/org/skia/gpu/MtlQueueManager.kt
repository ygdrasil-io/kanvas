package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class MtlQueueManager : public QueueManager {
 * public:
 *     MtlQueueManager(sk_cfp<id<MTLCommandQueue>> queue, const SharedContext*);
 *     ~MtlQueueManager() override {}
 *
 * private:
 *     const MtlSharedContext* mtlSharedContext() const;
 *
 *     std::unique_ptr<CommandBuffer> getNewCommandBuffer(ResourceProvider*, Protected) override;
 *     OutstandingSubmission onSubmitToGpu(const SubmitInfo&) override;
 *
 * #if defined(GPU_TEST_UTILS)
 *     void startCapture() override;
 *     void stopCapture() override;
 * #endif
 *
 *     sk_cfp<id<MTLCommandQueue>> fQueue;
 * }
 * ```
 */
public open class MtlQueueManager public constructor(
  sharedContext: SharedContext?,
) : QueueManager(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLCommandQueue>> fQueue
   * ```
   */
  private var fQueue: Int = TODO("Initialize fQueue")

  /**
   * C++ original:
   * ```cpp
   * const MtlSharedContext* MtlQueueManager::mtlSharedContext() const {
   *     return static_cast<const MtlSharedContext*>(fSharedContext);
   * }
   * ```
   */
  private fun mtlSharedContext(): MtlSharedContext {
    TODO("Implement mtlSharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<CommandBuffer> MtlQueueManager::getNewCommandBuffer(
   *         ResourceProvider* resourceProvider, Protected) {
   *     MtlResourceProvider* mtlResourceProvider = static_cast<MtlResourceProvider*>(resourceProvider);
   *     auto cmdBuffer = MtlCommandBuffer::Make(fQueue.get(),
   *                                             this->mtlSharedContext(),
   *                                             mtlResourceProvider);
   *     return cmdBuffer;
   * }
   * ```
   */
  public override fun getNewCommandBuffer(resourceProvider: ResourceProvider?, param1: Protected): Int {
    TODO("Implement getNewCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * QueueManager::OutstandingSubmission MtlQueueManager::onSubmitToGpu(const SubmitInfo&) {
   *     SkASSERT(fCurrentCommandBuffer);
   *     MtlCommandBuffer* mtlCmdBuffer = static_cast<MtlCommandBuffer*>(fCurrentCommandBuffer.get());
   *     if (!mtlCmdBuffer->commit()) {
   *         fCurrentCommandBuffer->callFinishedProcs(/*success=*/false);
   *         return nullptr;
   *     }
   *
   *     std::unique_ptr<GpuWorkSubmission> submission(
   *             new MtlWorkSubmission(std::move(fCurrentCommandBuffer), this));
   *     return submission;
   * }
   * ```
   */
  public override fun onSubmitToGpu(param0: SubmitInfo): Int {
    TODO("Implement onSubmitToGpu")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlQueueManager::startCapture() {
   *     // TODO: add newer Metal interface as well
   *     MTLCaptureManager* captureManager = [MTLCaptureManager sharedCaptureManager];
   *     if (captureManager.isCapturing) {
   *         return;
   *     }
   *     MTLCaptureDescriptor* captureDescriptor = [[MTLCaptureDescriptor alloc] init];
   *     captureDescriptor.captureObject = fQueue.get();
   *
   *     NSError *error;
   *     if (![captureManager startCaptureWithDescriptor: captureDescriptor error:&error]) {
   *         NSLog(@"Failed to start capture, error %@", error);
   *     }
   * }
   * ```
   */
  public override fun startCapture() {
    TODO("Implement startCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlQueueManager::stopCapture() {
   *     MTLCaptureManager* captureManager = [MTLCaptureManager sharedCaptureManager];
   *     if (captureManager.isCapturing) {
   *         [captureManager stopCapture];
   *     }
   * }
   * ```
   */
  public override fun stopCapture() {
    TODO("Implement stopCapture")
  }
}
