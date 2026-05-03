package org.skia.gpu

import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct SubmitInfo {
 *     SyncToCpu fSync = SyncToCpu::kNo;
 *     MarkFrameBoundary fMarkBoundary = MarkFrameBoundary::kNo;
 *     uint64_t fFrameID = 0;
 *
 *     constexpr SubmitInfo() = default;
 *
 *     constexpr SubmitInfo(SyncToCpu sync)
 *         : fSync(sync)
 *         , fMarkBoundary(MarkFrameBoundary::kNo)
 *         , fFrameID(0) {}
 *
 *     constexpr SubmitInfo(SyncToCpu sync, uint64_t frameID)
 *         : fSync(sync)
 *         , fMarkBoundary(MarkFrameBoundary::kYes)
 *         , fFrameID(frameID) {}
 * }
 * ```
 */
public data class SubmitInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SyncToCpu fSync = SyncToCpu::kNo
   * ```
   */
  public var fSync: SyncToCpu,
  /**
   * C++ original:
   * ```cpp
   * MarkFrameBoundary fMarkBoundary = MarkFrameBoundary::kNo
   * ```
   */
  public var fMarkBoundary: MarkFrameBoundary,
  /**
   * C++ original:
   * ```cpp
   * uint64_t fFrameID = 0
   * ```
   */
  public var fFrameID: ULong,
)
