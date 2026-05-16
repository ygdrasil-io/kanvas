package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class AutoCallback {
 * public:
 *     using Context                 = void*;
 *     using Callback                = void (*)(Context);
 *     using CallbackWithStats       = void (*)(Context, const GpuStats&);
 *     using ResultCallback          = void (*)(Context, CallbackResult);
 *     using ResultCallbackWithStats = void (*)(Context, CallbackResult, const GpuStats&);
 *
 *     AutoCallback() = default;
 *     AutoCallback(const AutoCallback&) = delete;
 *     AutoCallback(AutoCallback&& that) { *this = std::move(that); }
 *
 *     AutoCallback(Callback proc, Context ctx) : fReleaseProc(proc), fReleaseCtx(ctx) {}
 *     AutoCallback(CallbackWithStats proc, Context ctx)
 *             : fReleaseWithStatsProc(proc), fReleaseCtx(ctx) {}
 *     AutoCallback(ResultCallback proc, Context ctx) : fResultReleaseProc(proc), fReleaseCtx(ctx) {}
 *     AutoCallback(ResultCallbackWithStats proc, Context ctx)
 *             : fResultReleaseWithStatsProc(proc), fReleaseCtx(ctx) {}
 *
 *     ~AutoCallback() {
 *         SkASSERT(this->operator bool() || true);  // run assert in the operator
 *
 *         if (fResultReleaseWithStatsProc) {
 *             fResultReleaseWithStatsProc(fReleaseCtx, fResult, fGpuStats);
 *         } else if (fReleaseWithStatsProc) {
 *             fReleaseWithStatsProc(fReleaseCtx, fGpuStats);
 *         } else if (fResultReleaseProc) {
 *             fResultReleaseProc(fReleaseCtx, fResult);
 *         } else if (fReleaseProc) {
 *             fReleaseProc(fReleaseCtx);
 *         }
 *     }
 *
 *     AutoCallback& operator=(const AutoCallback&) = delete;
 *     AutoCallback& operator=(AutoCallback&& that) {
 *         fReleaseCtx                 = that.fReleaseCtx;
 *         fReleaseProc                = that.fReleaseProc;
 *         fReleaseWithStatsProc       = that.fReleaseWithStatsProc;
 *         fResultReleaseProc          = that.fResultReleaseProc;
 *         fResultReleaseWithStatsProc = that.fResultReleaseWithStatsProc;
 *         fResult                     = that.fResult;
 *         fGpuStats                   = that.fGpuStats;
 *
 *         that.fReleaseProc                = nullptr;
 *         that.fReleaseWithStatsProc       = nullptr;
 *         that.fResultReleaseProc          = nullptr;
 *         that.fResultReleaseWithStatsProc = nullptr;
 *         return *this;
 *     }
 *
 *     Context context() const { return fReleaseCtx; }
 *
 *     bool receivesGpuStats() const { return fReleaseWithStatsProc || fResultReleaseWithStatsProc; }
 *
 *     void setFailureResult() {
 *         SkASSERT(fResultReleaseProc || fResultReleaseWithStatsProc);
 *         // Shouldn't really be calling this multiple times.
 *         SkASSERT(fResult == CallbackResult::kSuccess);
 *         fResult = CallbackResult::kFailed;
 *     }
 *
 *     void setStats(const GpuStats& stats) {
 *         SkASSERT(this->receivesGpuStats());
 *         fGpuStats = stats;
 *     }
 *
 *     explicit operator bool() const {
 *         auto toInt = [](auto p) { return p ? 1U : 0U; };
 *         auto total = toInt(fReleaseProc) + toInt(fReleaseWithStatsProc) + toInt(fResultReleaseProc);
 *         SkASSERT(total <= 1);
 *         return total == 1;
 *     }
 *
 * private:
 *     Callback                fReleaseProc                = nullptr;
 *     CallbackWithStats       fReleaseWithStatsProc       = nullptr;
 *     ResultCallback          fResultReleaseProc          = nullptr;
 *     ResultCallbackWithStats fResultReleaseWithStatsProc = nullptr;
 *
 *     Context        fReleaseCtx = nullptr;
 *     CallbackResult fResult     = CallbackResult::kSuccess;
 *     GpuStats       fGpuStats   = {};
 * }
 * ```
 */
public data class AutoCallback public constructor(
  /**
   * C++ original:
   * ```cpp
   * Callback                fReleaseProc                = nullptr
   * ```
   */
  private var fReleaseProc: AutoCallbackCallback,
  /**
   * C++ original:
   * ```cpp
   * CallbackWithStats       fReleaseWithStatsProc       = nullptr
   * ```
   */
  private var fReleaseWithStatsProc: AutoCallbackCallbackWithStats,
  /**
   * C++ original:
   * ```cpp
   * ResultCallback          fResultReleaseProc          = nullptr
   * ```
   */
  private var fResultReleaseProc: AutoCallbackResultCallback,
  /**
   * C++ original:
   * ```cpp
   * ResultCallbackWithStats fResultReleaseWithStatsProc = nullptr
   * ```
   */
  private var fResultReleaseWithStatsProc: AutoCallbackResultCallbackWithStats,
  /**
   * C++ original:
   * ```cpp
   * Context        fReleaseCtx = nullptr
   * ```
   */
  private var fReleaseCtx: AutoCallbackContext,
  /**
   * C++ original:
   * ```cpp
   * CallbackResult fResult     = CallbackResult::kSuccess
   * ```
   */
  private var fResult: CallbackResult,
  /**
   * C++ original:
   * ```cpp
   * GpuStats       fGpuStats
   * ```
   */
  private var fGpuStats: GpuStats,
) {
  /**
   * C++ original:
   * ```cpp
   * AutoCallback& operator=(const AutoCallback&) = delete
   * ```
   */
  public fun assign(param0: AutoCallback) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * AutoCallback& operator=(AutoCallback&& that) {
   *         fReleaseCtx                 = that.fReleaseCtx;
   *         fReleaseProc                = that.fReleaseProc;
   *         fReleaseWithStatsProc       = that.fReleaseWithStatsProc;
   *         fResultReleaseProc          = that.fResultReleaseProc;
   *         fResultReleaseWithStatsProc = that.fResultReleaseWithStatsProc;
   *         fResult                     = that.fResult;
   *         fGpuStats                   = that.fGpuStats;
   *
   *         that.fReleaseProc                = nullptr;
   *         that.fReleaseWithStatsProc       = nullptr;
   *         that.fResultReleaseProc          = nullptr;
   *         that.fResultReleaseWithStatsProc = nullptr;
   *         return *this;
   *     }
   * ```
   */
  public fun context(): AutoCallbackContext {
    TODO("Implement context")
  }

  /**
   * C++ original:
   * ```cpp
   * Context context() const { return fReleaseCtx; }
   * ```
   */
  public fun receivesGpuStats(): Boolean {
    TODO("Implement receivesGpuStats")
  }

  /**
   * C++ original:
   * ```cpp
   * bool receivesGpuStats() const { return fReleaseWithStatsProc || fResultReleaseWithStatsProc; }
   * ```
   */
  public fun setFailureResult() {
    TODO("Implement setFailureResult")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFailureResult() {
   *         SkASSERT(fResultReleaseProc || fResultReleaseWithStatsProc);
   *         // Shouldn't really be calling this multiple times.
   *         SkASSERT(fResult == CallbackResult::kSuccess);
   *         fResult = CallbackResult::kFailed;
   *     }
   * ```
   */
  public fun setStats(stats: GpuStats) {
    TODO("Implement setStats")
  }
}
