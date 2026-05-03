package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.foundation.SkNoncopyable
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.gpu.ganesh.GrDirectContext
import undefined.GpuTimer

/**
 * C++ original:
 * ```cpp
 * class TestContext : public SkNoncopyable {
 * public:
 *     virtual ~TestContext();
 *
 *     bool fenceSyncSupport() const { return fFenceSupport; }
 *
 *     bool gpuTimingSupport() const { return fGpuTimer != nullptr; }
 *     GpuTimer* gpuTimer() const { SkASSERT(fGpuTimer); return fGpuTimer.get(); }
 *
 *     bool getMaxGpuFrameLag(int *maxFrameLag) const {
 *         if (!this->fenceSyncSupport()) {
 *             return false;
 *         }
 *         *maxFrameLag = kMaxFrameLag;
 *         return true;
 *     }
 *
 *     void makeNotCurrent() const;
 *     void makeCurrent() const;
 *
 *     /**
 *      * Like makeCurrent() but this returns an object that will restore the previous current
 *      * context in its destructor. Useful to undo the effect making this current before returning to
 *      * a caller that doesn't expect the current context to be changed underneath it.
 *      *
 *      * The returned object restores the current context of the same type (e.g. egl, glx, ...) in its
 *      * destructor. It is undefined behavior if that context is destroyed before the destructor
 *      * executes. If the concept of a current context doesn't make sense for this context type then
 *      * the returned object's destructor is a no-op.
 *      */
 *     [[nodiscard]] SkScopeExit makeCurrentAndAutoRestore() const;
 *
 *     virtual GrBackendApi backend() = 0;
 *
 *     virtual sk_sp<GrDirectContext> makeContext(const GrContextOptions&);
 *
 *     /**
 *      * This will flush work to the GPU. Additionally, if the platform supports fence syncs, we will
 *      * add a finished callback to our flush call. We allow ourselves to have kMaxFrameLag number of
 *      * unfinished flushes active on the GPU at a time. If we have 2 outstanding flushes then we will
 *      * wait on the CPU until one has finished.
 *      */
 *     void flushAndWaitOnSync(GrDirectContext* context);
 *
 *     /**
 *      * This notifies the context that we are deliberately testing abandoning
 *      * the context. It is useful for debugging contexts that would otherwise
 *      * test that GPU resources are properly deleted. It also allows a debugging
 *      * context to test that further API calls are not made by Skia GPU code.
 *      */
 *     virtual void testAbandon();
 *
 *     /** Flush and wait until all GPU work is finished. */
 *     void flushAndSyncCpu(GrDirectContext*);
 *
 * protected:
 *     bool fFenceSupport = false;
 *
 *     std::unique_ptr<GpuTimer>  fGpuTimer;
 *
 *     TestContext();
 *
 *     /** This should destroy the 3D context. */
 *     virtual void teardown();
 *
 *     virtual void onPlatformMakeNotCurrent() const = 0;
 *     virtual void onPlatformMakeCurrent() const = 0;
 *     /**
 *      * Subclasses should implement such that the returned function will cause the current context
 *      * of this type to be made current again when it is called. It should additionally be the
 *      * case that if "this" is already current when this is called, then "this" is destroyed (thereby
 *      * setting the null context as current), and then the std::function is called the null context
 *      * should remain current.
 *      */
 *     virtual std::function<void()> onPlatformGetAutoContextRestore() const = 0;
 *
 * private:
 *     enum {
 *         kMaxFrameLag = 3
 *     };
 *
 *     sk_sp<FlushFinishTracker> fFinishTrackers[kMaxFrameLag - 1];
 *     int fCurrentFlushIdx = 0;
 *
 *     using INHERITED = SkNoncopyable;
 * }
 * ```
 */
public abstract class TestContext public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * bool fFenceSupport = false
   * ```
   */
  protected var fFenceSupport: Boolean = TODO("Initialize fFenceSupport")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<GpuTimer>  fGpuTimer
   * ```
   */
  protected var fGpuTimer: Int = TODO("Initialize fGpuTimer")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FlushFinishTracker> fFinishTrackers[kMaxFrameLag - 1]
   * ```
   */
  private var fFinishTrackers: IntArray = TODO("Initialize fFinishTrackers")

  /**
   * C++ original:
   * ```cpp
   * int fCurrentFlushIdx = 0
   * ```
   */
  private var fCurrentFlushIdx: Int = TODO("Initialize fCurrentFlushIdx")

  /**
   * C++ original:
   * ```cpp
   * bool fenceSyncSupport() const { return fFenceSupport; }
   * ```
   */
  public fun fenceSyncSupport(): Boolean {
    TODO("Implement fenceSyncSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * bool gpuTimingSupport() const { return fGpuTimer != nullptr; }
   * ```
   */
  public fun gpuTimingSupport(): Boolean {
    TODO("Implement gpuTimingSupport")
  }

  /**
   * C++ original:
   * ```cpp
   * GpuTimer* gpuTimer() const { SkASSERT(fGpuTimer); return fGpuTimer.get(); }
   * ```
   */
  public fun gpuTimer(): GpuTimer {
    TODO("Implement gpuTimer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getMaxGpuFrameLag(int *maxFrameLag) const {
   *         if (!this->fenceSyncSupport()) {
   *             return false;
   *         }
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
   * void makeNotCurrent() const
   * ```
   */
  public fun makeNotCurrent() {
    TODO("Implement makeNotCurrent")
  }

  /**
   * C++ original:
   * ```cpp
   * void makeCurrent() const
   * ```
   */
  public fun makeCurrent() {
    TODO("Implement makeCurrent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScopeExit makeCurrentAndAutoRestore() const
   * ```
   */
  public fun makeCurrentAndAutoRestore(): Int {
    TODO("Implement makeCurrentAndAutoRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrBackendApi backend() = 0
   * ```
   */
  public abstract fun backend(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<GrDirectContext> makeContext(const GrContextOptions&)
   * ```
   */
  public open fun makeContext(param0: GrContextOptions): Int {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void flushAndWaitOnSync(GrDirectContext* context)
   * ```
   */
  public fun flushAndWaitOnSync(context: GrDirectContext?) {
    TODO("Implement flushAndWaitOnSync")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void testAbandon()
   * ```
   */
  public open fun testAbandon() {
    TODO("Implement testAbandon")
  }

  /**
   * C++ original:
   * ```cpp
   * void flushAndSyncCpu(GrDirectContext*)
   * ```
   */
  public fun flushAndSyncCpu(param0: GrDirectContext?) {
    TODO("Implement flushAndSyncCpu")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void teardown()
   * ```
   */
  protected open fun teardown() {
    TODO("Implement teardown")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onPlatformMakeNotCurrent() const = 0
   * ```
   */
  protected abstract fun onPlatformMakeNotCurrent()

  /**
   * C++ original:
   * ```cpp
   * virtual void onPlatformMakeCurrent() const = 0
   * ```
   */
  protected abstract fun onPlatformMakeCurrent()

  /**
   * C++ original:
   * ```cpp
   * virtual std::function<void()> onPlatformGetAutoContextRestore() const = 0
   * ```
   */
  protected abstract fun onPlatformGetAutoContextRestore(): Int

  public companion object {
    public val kMaxFrameLag: Int = TODO("Initialize kMaxFrameLag")
  }
}
