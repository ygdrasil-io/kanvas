package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct A8_RowBlitBWPair {
 *     SkBlendMode     mode;
 *     AlphaProc       oneProc;
 *     A8_RowBlitBW    bwProc;
 *     A8_RowBlitAA    aaProc;
 * }
 * ```
 */
public data class A8RowBlitBWPair public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode     mode
   * ```
   */
  public var mode: SkBlendMode,
  /**
   * C++ original:
   * ```cpp
   * AlphaProc       oneProc
   * ```
   */
  public var oneProc: AlphaProc,
  /**
   * C++ original:
   * ```cpp
   * A8_RowBlitBW    bwProc
   * ```
   */
  public var bwProc: A8RowBlitBW,
  /**
   * C++ original:
   * ```cpp
   * A8_RowBlitAA    aaProc
   * ```
   */
  public var aaProc: A8RowBlitAA,
)
