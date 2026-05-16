package org.skia.tests

import org.skia.gpu.Context
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * struct GraphiteInfo {
 *     skgpu::graphite::Context* context = nullptr;
 *     skgpu::graphite::Recorder* recorder = nullptr;
 * }
 * ```
 */
public data class GraphiteInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Context* context = nullptr
   * ```
   */
  public var context: Context?,
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* recorder = nullptr
   * ```
   */
  public var recorder: Recorder?,
)
