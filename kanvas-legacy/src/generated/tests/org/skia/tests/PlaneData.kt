package org.skia.tests

import org.skia.foundation.SkBitmap

/**
 * C++ original:
 * ```cpp
 * struct PlaneData {
 *    SkBitmap fYFull;
 *    SkBitmap fUFull;
 *    SkBitmap fVFull;
 *    SkBitmap fAFull;
 *    SkBitmap fUQuarter; // 2x2 downsampled U channel
 *    SkBitmap fVQuarter; // 2x2 downsampled V channel
 *
 *    SkBitmap fFull;
 *    SkBitmap fQuarter; // 2x2 downsampled YUVA
 * }
 * ```
 */
public data class PlaneData public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fYFull
   * ```
   */
  public var fYFull: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fUFull
   * ```
   */
  public var fUFull: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fVFull
   * ```
   */
  public var fVFull: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fAFull
   * ```
   */
  public var fAFull: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fUQuarter
   * ```
   */
  public var fUQuarter: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fVQuarter
   * ```
   */
  public var fVQuarter: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fFull
   * ```
   */
  public var fFull: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fQuarter
   * ```
   */
  public var fQuarter: SkBitmap,
)
