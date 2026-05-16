package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.gpu.Context
import org.skia.gpu.Recording

/**
 * C++ original:
 * ```cpp
 * class GraphiteTestContext {
 * public:
 *     GraphiteTestContext(const GraphiteTestContext&) = delete;
 *     GraphiteTestContext& operator=(const GraphiteTestContext&) = delete;
 *
 *     virtual ~GraphiteTestContext();
 *
 *     virtual skgpu::BackendApi backend() = 0;
 *
 *     virtual skgpu::ContextType contextType() = 0;
 *
 *     virtual std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) = 0;
 *
 *     bool getMaxGpuFrameLag(int *maxFrameLag) const {
 *         *maxFrameLag = kMaxFrameLag;
 *         return true;
 *     }
 *
 *     /**
 *      * This will insert a Recording and submit work to the GPU. Additionally, we will add a finished
 *      * callback to our insert recording call. We allow ourselves to have kMaxFrameLag number of
 *      * unfinished flushes active on the GPU at a time. If we have 2 outstanding flushes then we will
 *      * wait on the CPU until one has finished.
 *      */
 *     void submitRecordingAndWaitOnSync(skgpu::graphite::Context*, skgpu::graphite::Recording*);
 *
 *     /**
 *      * Allow the GPU API to make or detect forward progress on submitted work. For most APIs this is
 *      * a no-op as the API can do this on another thread.
 *      */
 *     virtual void tick() {}
 *
 *     /**
 *      * If the context supports CPU/GPU sync'ing this calls submit with skgpu::SyncToCpu::kYes.
 *      * Otherwise it calls it with kNo in a busy loop.
 *      */
 *     void syncedSubmit(skgpu::graphite::Context*);
 *
 * protected:
 *     static constexpr int kMaxFrameLag = 3;
 *
 *     sk_sp<sk_gpu_test::FlushFinishTracker> fFinishTrackers[kMaxFrameLag - 1];
 *     int fCurrentFlushIdx = 0;
 *
 *     GraphiteTestContext();
 * }
 * ```
 */
public abstract class GraphiteTestContext public constructor(
  param0: GraphiteTestContext,
) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxFrameLag = 3
   * ```
   */
  protected var fFinishTrackers: Array<SkSp<FlushFinishTracker>> =
      TODO("Initialize fFinishTrackers")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sk_gpu_test::FlushFinishTracker> fFinishTrackers[kMaxFrameLag - 1]
   * ```
   */
  protected var fCurrentFlushIdx: Int = TODO("Initialize fCurrentFlushIdx")

  /**
   * C++ original:
   * ```cpp
   * GraphiteTestContext(const GraphiteTestContext&) = delete
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * GraphiteTestContext& operator=(const GraphiteTestContext&) = delete
   * ```
   */
  public fun assign(param0: GraphiteTestContext) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual skgpu::BackendApi backend() = 0
   * ```
   */
  public abstract fun backend(): BackendApi

  /**
   * C++ original:
   * ```cpp
   * virtual skgpu::ContextType contextType() = 0
   * ```
   */
  public abstract fun contextType(): ContextType

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) = 0
   * ```
   */
  public abstract fun makeContext(param0: TestOptions): Int

  /**
   * C++ original:
   * ```cpp
   * bool getMaxGpuFrameLag(int *maxFrameLag) const {
   *         *maxFrameLag = kMaxFrameLag;
   *         return true;
   *     }
   * ```
   */
  public fun getMaxGpuFrameLag(maxFrameLag: Int?): Boolean {
    TODO("Implement getMaxGpuFrameLag")
  }

  /**
   * C++ original:
   * ```cpp
   * void GraphiteTestContext::submitRecordingAndWaitOnSync(skgpu::graphite::Context* context,
   *                                                        skgpu::graphite::Recording* recording) {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *     SkASSERT(context);
   *     SkASSERT(recording);
   *
   *     if (fFinishTrackers[fCurrentFlushIdx]) {
   *         fFinishTrackers[fCurrentFlushIdx]->waitTillFinished([this] { tick(); });
   *     }
   *
   *     fFinishTrackers[fCurrentFlushIdx].reset(new sk_gpu_test::FlushFinishTracker(context));
   *
   *     // We add an additional ref to the current flush tracker here. This ref is owned by the finish
   *     // callback on the flush call. The finish callback will unref the tracker when called.
   *     fFinishTrackers[fCurrentFlushIdx]->ref();
   *
   *     skgpu::graphite::InsertRecordingInfo info;
   *     info.fRecording = recording;
   *     info.fFinishedContext = fFinishTrackers[fCurrentFlushIdx].get();
   *     info.fFinishedProc = sk_gpu_test::FlushFinishTracker::FlushFinishedResult;
   *     context->insertRecording(info);
   *
   *     context->submit(skgpu::graphite::SyncToCpu::kNo);
   *
   *     fCurrentFlushIdx = (fCurrentFlushIdx + 1) % std::size(fFinishTrackers);
   * }
   * ```
   */
  public fun submitRecordingAndWaitOnSync(context: Context?, recording: Recording?) {
    TODO("Implement submitRecordingAndWaitOnSync")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void tick() {}
   * ```
   */
  public open fun tick() {
    TODO("Implement tick")
  }

  /**
   * C++ original:
   * ```cpp
   * void GraphiteTestContext::syncedSubmit(skgpu::graphite::Context* context) {
   *     skgpu::graphite::SyncToCpu sync = context->priv().caps()->allowCpuSync()
   *                                               ? skgpu::graphite::SyncToCpu::kYes
   *                                               : skgpu::graphite::SyncToCpu::kNo;
   *     context->submit(sync);
   *     if (sync == skgpu::graphite::SyncToCpu::kNo) {
   *         while (context->hasUnfinishedGpuWork()) {
   *             this->tick();
   *             context->checkAsyncWorkCompletion();
   *         }
   *     }
   * }
   * ```
   */
  public fun syncedSubmit(context: Context?) {
    TODO("Implement syncedSubmit")
  }

  public companion object {
    protected val kMaxFrameLag: Int = TODO("Initialize kMaxFrameLag")
  }
}
