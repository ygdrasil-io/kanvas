package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct InsertFinishInfo {
 *     InsertFinishInfo() = default;
 *     InsertFinishInfo(GpuFinishedContext context, GpuFinishedProc proc)
 *             : fFinishedContext{context}, fFinishedProc{proc} {}
 *     InsertFinishInfo(GpuFinishedContext context, GpuFinishedWithStatsProc proc)
 *             : fFinishedContext{context}, fFinishedWithStatsProc{proc} {}
 *     GpuFinishedContext fFinishedContext = nullptr;
 *     GpuFinishedProc fFinishedProc = nullptr;
 *     GpuFinishedWithStatsProc fFinishedWithStatsProc = nullptr;
 *     GpuStatsFlags fGpuStatsFlags = GpuStatsFlags::kNone;
 * }
 * ```
 */
public data class InsertFinishInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * GpuFinishedContext fFinishedContext = nullptr
   * ```
   */
  public var fFinishedContext: GpuFinishedContext,
  /**
   * C++ original:
   * ```cpp
   * GpuFinishedProc fFinishedProc = nullptr
   * ```
   */
  public var fFinishedProc: GpuFinishedProc,
  /**
   * C++ original:
   * ```cpp
   * GpuFinishedWithStatsProc fFinishedWithStatsProc = nullptr
   * ```
   */
  public var fFinishedWithStatsProc: GpuFinishedWithStatsProc,
  /**
   * C++ original:
   * ```cpp
   * GpuStatsFlags fGpuStatsFlags
   * ```
   */
  public var fGpuStatsFlags: Int,
)
