package org.skia.utils

import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SkCanvasLayerState {
 *     CanvasBackend type;
 *     int32_t x, y;
 *     int32_t width;
 *     int32_t height;
 *
 *     SkMCState mcState;
 *
 *     union {
 *         struct {
 *             RasterConfig config; // pixel format: a value from RasterConfigs.
 *             uint64_t rowBytes;   // Number of bytes from start of one line to next.
 *             void* pixels;        // The pixels, all (height * rowBytes) of them.
 *         } raster;
 *         struct {
 *             int32_t textureID;
 *         } gpu;
 *     };
 * }
 * ```
 */
public data class SkCanvasLayerState public constructor(
  /**
   * C++ original:
   * ```cpp
   * CanvasBackend type
   * ```
   */
  public var type: CanvasBackend,
  /**
   * C++ original:
   * ```cpp
   * int32_t x
   * ```
   */
  public var x: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t x, y
   * ```
   */
  public var y: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t width
   * ```
   */
  public var width: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t height
   * ```
   */
  public var height: Int,
  /**
   * C++ original:
   * ```cpp
   * SkMCState mcState
   * ```
   */
  public var mcState: SkMCState,
  public var config: RasterConfig,
  public var rowBytes: ULong,
  public var pixels: Unit?,
  public var textureID: Int,
)
