package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct SK_API RecorderOptions final {
 *     RecorderOptions();
 *     RecorderOptions(const RecorderOptions&);
 *     ~RecorderOptions();
 *
 *     sk_sp<ImageProvider> fImageProvider;
 *
 *     static constexpr size_t kDefaultRecorderBudget = 256 * (1 << 20);
 *     // What is the budget for GPU resources allocated and held by this Recorder.
 *     size_t fGpuBudgetInBytes = kDefaultRecorderBudget;
 *     // If Recordings are known to be played back in the order they are recorded, then Graphite
 *     // may be able to make certain assumptions that improve performance. This is often the case
 *     // if the content being drawn triggers the use of internal atlasing in Graphite (e.g. text).
 *     std::optional<bool> fRequireOrderedRecordings;
 *
 *     // Private options that are only meant for testing within Skia's tools.
 *     RecorderOptionsPriv* fRecorderOptionsPriv = nullptr;
 * }
 * ```
 */
public data class RecorderOptions public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageProvider> fImageProvider
   * ```
   */
  public var fImageProvider: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kDefaultRecorderBudget = 256 * (1 << 20)
   * ```
   */
  public var fGpuBudgetInBytes: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fGpuBudgetInBytes = kDefaultRecorderBudget
   * ```
   */
  public var fRequireOrderedRecordings: Boolean?,
  /**
   * C++ original:
   * ```cpp
   * std::optional<bool> fRequireOrderedRecordings
   * ```
   */
  public var fRecorderOptionsPriv: RecorderOptionsPriv?,
) {
  public companion object {
    public val kDefaultRecorderBudget: ULong = TODO("Initialize kDefaultRecorderBudget")
  }
}
