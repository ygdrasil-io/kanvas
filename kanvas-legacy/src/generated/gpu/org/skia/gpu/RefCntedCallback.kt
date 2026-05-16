package org.skia.gpu

import Args
import kotlin.Any
import kotlin.Boolean
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class RefCntedCallback : public SkNVRefCnt<RefCntedCallback> {
 * public:
 *     using Context                 = AutoCallback::Context;
 *     using Callback                = AutoCallback::Callback;
 *     using CallbackWithStats       = AutoCallback::CallbackWithStats;
 *     using ResultCallback          = AutoCallback::ResultCallback;
 *     using ResultCallbackWithStats = AutoCallback::ResultCallbackWithStats;
 *
 *     static sk_sp<RefCntedCallback> Make(Callback proc, Context ctx) { return MakeImpl(proc, ctx); }
 *
 *     static sk_sp<RefCntedCallback> Make(CallbackWithStats proc, Context ctx) {
 *         return MakeImpl(proc, ctx);
 *     }
 *
 *     static sk_sp<RefCntedCallback> Make(ResultCallback proc, Context ctx) {
 *         return MakeImpl(proc, ctx);
 *     }
 *
 *     static sk_sp<RefCntedCallback> Make(ResultCallbackWithStats proc, Context ctx) {
 *         return MakeImpl(proc, ctx);
 *     }
 *
 *     static sk_sp<RefCntedCallback> Make(AutoCallback&& callback) {
 *         if (!callback) {
 *             return nullptr;
 *         }
 *         return sk_sp<RefCntedCallback>(new RefCntedCallback(std::move(callback)));
 *     }
 *
 *     Context context() const { return fCallback.context(); }
 *
 *     bool receivesGpuStats() const { return fCallback.receivesGpuStats(); }
 *
 *     void setFailureResult() { fCallback.setFailureResult(); }
 *
 *     void setStats(const GpuStats& stats) { fCallback.setStats(stats); }
 *
 * private:
 *     template <typename R, typename... Args>
 *     static sk_sp<RefCntedCallback> MakeImpl(R proc(Args...), Context ctx) {
 *         if (!proc) {
 *             return nullptr;
 *         }
 *         return sk_sp<RefCntedCallback>(new RefCntedCallback({proc, ctx}));
 *     }
 *
 *     RefCntedCallback(AutoCallback callback) : fCallback(std::move(callback)) {}
 *
 *     AutoCallback fCallback;
 * }
 * ```
 */
public open class RefCntedCallback public constructor(
  callback: AutoCallback,
) : SkNVRefCnt(),
    RefCntedCallback {
  /**
   * C++ original:
   * ```cpp
   * AutoCallback fCallback
   * ```
   */
  private var fCallback: AutoCallback = TODO("Initialize fCallback")

  /**
   * C++ original:
   * ```cpp
   * Context context() const { return fCallback.context(); }
   * ```
   */
  public override fun context(): RefCntedCallbackContext {
    TODO("Implement context")
  }

  /**
   * C++ original:
   * ```cpp
   * bool receivesGpuStats() const { return fCallback.receivesGpuStats(); }
   * ```
   */
  public override fun receivesGpuStats(): Boolean {
    TODO("Implement receivesGpuStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFailureResult() { fCallback.setFailureResult(); }
   * ```
   */
  public override fun setFailureResult() {
    TODO("Implement setFailureResult")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStats(const GpuStats& stats) { fCallback.setStats(stats); }
   * ```
   */
  public override fun setStats(stats: GpuStats) {
    TODO("Implement setStats")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RefCntedCallback> Make(Callback proc, Context ctx) { return MakeImpl(proc, ctx); }
     * ```
     */
    public override fun make(proc: RefCntedCallbackCallback, ctx: RefCntedCallbackContext): SkSp<RefCntedCallback> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RefCntedCallback> Make(CallbackWithStats proc, Context ctx) {
     *         return MakeImpl(proc, ctx);
     *     }
     * ```
     */
    public override fun make(proc: RefCntedCallbackCallbackWithStats, ctx: RefCntedCallbackContext): SkSp<RefCntedCallback> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RefCntedCallback> Make(ResultCallback proc, Context ctx) {
     *         return MakeImpl(proc, ctx);
     *     }
     * ```
     */
    public override fun make(proc: RefCntedCallbackResultCallback, ctx: RefCntedCallbackContext): SkSp<RefCntedCallback> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RefCntedCallback> Make(ResultCallbackWithStats proc, Context ctx) {
     *         return MakeImpl(proc, ctx);
     *     }
     * ```
     */
    public override fun make(proc: RefCntedCallbackResultCallbackWithStats, ctx: RefCntedCallbackContext): SkSp<RefCntedCallback> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<RefCntedCallback> Make(AutoCallback&& callback) {
     *         if (!callback) {
     *             return nullptr;
     *         }
     *         return sk_sp<RefCntedCallback>(new RefCntedCallback(std::move(callback)));
     *     }
     * ```
     */
    public override fun make(callback: AutoCallback): SkSp<RefCntedCallback> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename R, typename... Args>
     *     static sk_sp<RefCntedCallback> MakeImpl(R proc(Args...), Context ctx) {
     *         if (!proc) {
     *             return nullptr;
     *         }
     *         return sk_sp<RefCntedCallback>(new RefCntedCallback({proc, ctx}));
     *     }
     * ```
     */
    public override fun <R, Args> makeImpl(param0: (Args) -> Any, ctx: RefCntedCallbackContext): SkSp<RefCntedCallback> {
      TODO("Implement makeImpl")
    }
  }
}
