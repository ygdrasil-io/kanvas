package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API DawnBackendContext {
 *     wgpu::Instance fInstance;
 *     wgpu::Device fDevice;
 *     wgpu::Queue fQueue;
 *     // See comment on DawnTickFunction.
 *     DawnTickFunction* fTick =
 * #if defined(__EMSCRIPTEN__)
 *             nullptr;
 * #else
 *             DawnNativeProcessEventsFunction;
 * #endif
 * }
 * ```
 */
public data class DawnBackendContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * wgpu::Instance fInstance
   * ```
   */
  public var fInstance: Int,
  /**
   * C++ original:
   * ```cpp
   * wgpu::Device fDevice
   * ```
   */
  public var fDevice: Int,
  /**
   * C++ original:
   * ```cpp
   * wgpu::Queue fQueue
   * ```
   */
  public var fQueue: Int,
  /**
   * C++ original:
   * ```cpp
   * DawnTickFunction* fTick =
   * #if defined(__EMSCRIPTEN__)
   *             nullptr;
   * #else
   *             DawnNativeProcessEventsFunction
   * ```
   */
  public var fTick: DawnTickFunction?,
)
