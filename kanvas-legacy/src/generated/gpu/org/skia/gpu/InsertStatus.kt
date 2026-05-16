package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class InsertStatus {
 * public:
 *     // Do not refer to V directly; use these constants as if InsertStatus were a class enum, e.g.
 *     // InsertStatus::kSuccess.
 *     enum V {
 *         // Everything successfully added to underlying CommandBuffer
 *         kSuccess,
 *         // Recording or InsertRecordingInfo invalid, no CB changes
 *         kInvalidRecording,
 *         // Promise image instantiation failed, no CB changes
 *         kPromiseImageInstantiationFailed,
 *         // Internal failure, CB partially modified, state unrecoverable or unknown (e.g. dependent
 *         // texture uploads for future Recordings may or may not get executed)
 *         kAddCommandsFailed,
 *         // Internal failure, shader pipeline compilation failed (driver issue, or disk corruption),
 *         // state unrecoverable.
 *         kAsyncShaderCompilesFailed
 *     };
 *
 *     constexpr InsertStatus() : fValue(kSuccess) {}
 *     /*implicit*/ constexpr InsertStatus(V v) : fValue(v) {}
 *
 *     operator InsertStatus::V() const {
 *         return fValue;
 *     }
 *
 *     // Assist migration from old bool return value of insertRecording; kSuccess is true,
 *     // all other error statuses are false.
 *     // NOTE: This is intentionally not explicit so that InsertStatus can be assigned correctly to
 *     // a bool or returned as a bool, since these are not boolean contexts that automatically apply
 *     // explicit bool operators (e.g. inside an if condition).
 *     operator bool() const {
 *         return fValue == kSuccess;
 *     }
 *
 * private:
 *     V fValue;
 * }
 * ```
 */
public data class InsertStatus public constructor(
  /**
   * C++ original:
   * ```cpp
   * V fValue
   * ```
   */
  private var fValue: V,
) {
  public enum class V {
    kSuccess,
    kInvalidRecording,
    kPromiseImageInstantiationFailed,
    kAddCommandsFailed,
    kAsyncShaderCompilesFailed,
  }
}
