package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.gpu.CallbackResult
import org.skia.gpu.Context

/**
 * C++ original:
 * ```cpp
 * class FlushFinishTracker : public SkRefCnt {
 * public:
 *     static void FlushFinished(void* finishedContext) {
 *         auto tracker = static_cast<FlushFinishTracker*>(finishedContext);
 *         tracker->setFinished();
 *         tracker->unref();
 *     }
 *
 *     static void FlushFinishedResult(void* finishedContext, skgpu::CallbackResult) {
 *         FlushFinished(finishedContext);
 *     }
 *
 * #if defined(SK_GANESH)
 *     explicit FlushFinishTracker(GrDirectContext* context) : fContext(context) {}
 * #endif
 * #if defined(SK_GRAPHITE)
 *     explicit FlushFinishTracker(skgpu::graphite::Context* context) : fGraphiteContext(context) {}
 * #endif
 *
 *     void setFinished() { fIsFinished = true; }
 *
 *     void waitTillFinished(std::function<void()> tick = {});
 *
 * private:
 * #if defined(SK_GANESH)
 *     GrDirectContext* fContext = nullptr;
 * #endif
 * #if defined(SK_GRAPHITE)
 *     skgpu::graphite::Context*  fGraphiteContext = nullptr;
 * #endif
 *
 *     // Currently we don't have the this bool be atomic cause all current uses of this class happen
 *     // on a single thread. In other words we call flush, checkAsyncWorkCompletion, and
 *     // waitTillFinished all on the same thread. If we ever want to support the flushing and waiting
 *     // to happen on different threads then we should make this atomic.
 *     bool fIsFinished = false;
 * }
 * ```
 */
public open class FlushFinishTracker public constructor(
  context: Context?,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Context*  fGraphiteContext = nullptr
   * ```
   */
  private var fGraphiteContext: Context? = TODO("Initialize fGraphiteContext")

  /**
   * C++ original:
   * ```cpp
   * bool fIsFinished = false
   * ```
   */
  private var fIsFinished: Boolean = TODO("Initialize fIsFinished")

  /**
   * C++ original:
   * ```cpp
   * void setFinished() { fIsFinished = true; }
   * ```
   */
  public fun setFinished() {
    TODO("Implement setFinished")
  }

  /**
   * C++ original:
   * ```cpp
   * void waitTillFinished(std::function<void()> tick = {})
   * ```
   */
  public fun waitTillFinished(param0: Int) {
    TODO("Implement waitTillFinished")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void FlushFinished(void* finishedContext) {
     *         auto tracker = static_cast<FlushFinishTracker*>(finishedContext);
     *         tracker->setFinished();
     *         tracker->unref();
     *     }
     * ```
     */
    public fun flushFinished(finishedContext: Unit?) {
      TODO("Implement flushFinished")
    }

    /**
     * C++ original:
     * ```cpp
     * static void FlushFinishedResult(void* finishedContext, skgpu::CallbackResult) {
     *         FlushFinished(finishedContext);
     *     }
     * ```
     */
    public fun flushFinishedResult(finishedContext: Unit?, param1: CallbackResult) {
      TODO("Implement flushFinishedResult")
    }
  }
}
