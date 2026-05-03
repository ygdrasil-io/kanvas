package org.skia.tests

import kotlin.Any
import kotlin.String
import org.skia.foundation.SkPixmap

public enum class Result {
  kFail,
  kSuccess,
  kExcusedFailure,
}

public typealias GraphiteReadSrcFn = (
  T,
  Any,
  SkPixmap,
) -> Result

/**
 * C++ original:
 * ```cpp
 * class Result {
 * public:
 *     enum class Status : int { Ok, Fatal, Skip };
 *
 *     Result(Status status, SkString msg) : fMsg(std::move(msg)), fStatus(status) {}
 *
 *     Result(const Result&)            = default;
 *     Result& operator=(const Result&) = default;
 *
 *     static Result Ok() { return Result{Status::Ok, {}}; }
 *
 *     static Result Fatal(const char* fmt, ...) SK_PRINTF_LIKE(1, 2) {
 *         SkString msg;
 *         va_list args;
 *         va_start(args, fmt);
 *         msg.printVAList(fmt, args);
 *         va_end(args);
 *
 *         return Result{Status::Fatal, std::move(msg)};
 *     }
 *
 *     static Result Skip(const char* fmt, ...) SK_PRINTF_LIKE(1, 2) {
 *         SkString msg;
 *         va_list args;
 *         va_start(args, fmt);
 *         msg.printVAList(fmt, args);
 *         va_end(args);
 *
 *         return Result{Status::Skip, std::move(msg)};
 *     }
 *
 *     bool isOk() { return fStatus == Status::Ok; }
 *     bool isFatal() { return fStatus == Status::Fatal; }
 *     bool isSkip() { return fStatus == Status::Skip; }
 *
 *     const char* c_str() const { return fMsg.c_str(); }
 *     Status status() const { return fStatus; }
 *
 * private:
 *     SkString fMsg;
 *     Status   fStatus;
 * }
 * ```
 */
public open class Result public constructor(
  status: Status,
  msg: String,
) {
  /**
   * C++ original:
   * ```cpp
   * Status   fStatus
   * ```
   */
  private var fStatus: Status = TODO("Initialize fStatus")

  /**
   * C++ original:
   * ```cpp
   * Result(Status status, SkString msg) : fMsg(std::move(msg)), fStatus(status) {}
   * ```
   */
  public constructor(param0: Result) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result& operator=(const Result&) = default
   * ```
   */
  public fun assign(param0: Result) {
    TODO("Implement assign")
  }

  public enum class Status {
    Ok,
    Fatal,
    Skip,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Result Ok() { return Result{Status::Ok, {}}; }
     * ```
     */
    public fun ok(): Result {
      TODO("Implement ok")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result Fatal(const char* fmt, ...)
     * ```
     */
    public fun fatal(fmt: String?, param1: Any): Result {
      TODO("Implement fatal")
    }
  }
}
