package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import org.skia.core.SkSurface

/**
 * C++ original:
 * ```cpp
 * struct InsertRecordingInfo {
 *     Recording* fRecording = nullptr;
 *
 *     SkSurface* fTargetSurface = nullptr;
 *     SkIVector fTargetTranslation = {0, 0};
 *     SkIRect fTargetClip = {0, 0, 0, 0};
 *     MutableTextureState* fTargetTextureState = nullptr;
 *
 *     size_t fNumWaitSemaphores = 0;
 *     BackendSemaphore* fWaitSemaphores = nullptr;
 *     size_t fNumSignalSemaphores = 0;
 *     BackendSemaphore* fSignalSemaphores = nullptr;
 *
 *     GpuStatsFlags fGpuStatsFlags = GpuStatsFlags::kNone;
 *     GpuFinishedContext fFinishedContext = nullptr;
 *     GpuFinishedProc fFinishedProc = nullptr;
 *     GpuFinishedWithStatsProc fFinishedWithStatsProc = nullptr;
 *
 *     // For unit testing purposes, this can be used to induce a known failure status from
 *     // Context::insertRecording(). When this set to anything other than kSuccess, insertRecording()
 *     // will operate as normal until the first condition that would normally return the simulated
 *     // status is encountered. At that point, operations are treated as if that condition had failed.
 *     // This leaves the Context in a state consistent with encountering the InsertStatus in a normal
 *     // application.
 *     //
 *     // NOTE: If the simulated failure status is one of the later error codes but the inserted
 *     // Recording would fail with an earlier error code normally, that error is still returned.
 *     InsertStatus fSimulatedStatus = InsertStatus::kSuccess;
 * }
 * ```
 */
public data class InsertRecordingInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * Recording* fRecording = nullptr
   * ```
   */
  public var fRecording: Recording?,
  /**
   * C++ original:
   * ```cpp
   * SkSurface* fTargetSurface = nullptr
   * ```
   */
  public var fTargetSurface: SkSurface?,
  /**
   * C++ original:
   * ```cpp
   * SkIVector fTargetTranslation
   * ```
   */
  public var fTargetTranslation: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fTargetClip
   * ```
   */
  public var fTargetClip: Int,
  /**
   * C++ original:
   * ```cpp
   * MutableTextureState* fTargetTextureState = nullptr
   * ```
   */
  public var fTargetTextureState: MutableTextureState?,
  /**
   * C++ original:
   * ```cpp
   * size_t fNumWaitSemaphores = 0
   * ```
   */
  public var fNumWaitSemaphores: ULong,
  /**
   * C++ original:
   * ```cpp
   * BackendSemaphore* fWaitSemaphores = nullptr
   * ```
   */
  public var fWaitSemaphores: BackendSemaphore?,
  /**
   * C++ original:
   * ```cpp
   * size_t fNumSignalSemaphores = 0
   * ```
   */
  public var fNumSignalSemaphores: ULong,
  /**
   * C++ original:
   * ```cpp
   * BackendSemaphore* fSignalSemaphores = nullptr
   * ```
   */
  public var fSignalSemaphores: BackendSemaphore?,
  /**
   * C++ original:
   * ```cpp
   * GpuStatsFlags fGpuStatsFlags
   * ```
   */
  public var fGpuStatsFlags: Int,
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
   * InsertStatus fSimulatedStatus = InsertStatus::kSuccess
   * ```
   */
  public var fSimulatedStatus: InsertStatus,
)
