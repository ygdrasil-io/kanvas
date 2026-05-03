package org.skia.core

import org.skia.gpu.SubRunControl

/**
 * C++ original:
 * ```cpp
 * struct SkStrikeDeviceInfo {
 *     const SkSurfaceProps fSurfaceProps;
 *     const SkScalerContextFlags fScalerContextFlags;
 *     // This is a pointer so this can be compiled without SK_GPU_SUPPORT.
 *     const sktext::gpu::SubRunControl* const fSubRunControl;
 * }
 * ```
 */
public data class SkStrikeDeviceInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fSurfaceProps
   * ```
   */
  public val fSurfaceProps: SkSurfaceProps,
  /**
   * C++ original:
   * ```cpp
   * const SkScalerContextFlags fScalerContextFlags
   * ```
   */
  public val fScalerContextFlags: SkScalerContextFlags,
  /**
   * C++ original:
   * ```cpp
   * const sktext::gpu::SubRunControl* const fSubRunControl
   * ```
   */
  public val fSubRunControl: SubRunControl?,
)
