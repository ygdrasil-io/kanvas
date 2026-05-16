package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct RendererData {
 *     bool isSDF = false;
 *     bool isLCD = false;
 *     skgpu::MaskFormat maskFormat;
 * }
 * ```
 */
public data class RendererData public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool isSDF = false
   * ```
   */
  public var isSDF: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool isLCD = false
   * ```
   */
  public var isLCD: Boolean,
  /**
   * C++ original:
   * ```cpp
   * skgpu::MaskFormat maskFormat
   * ```
   */
  public var maskFormat: MaskFormat,
)
