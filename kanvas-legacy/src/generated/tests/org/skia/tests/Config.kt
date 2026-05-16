package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct Config {
 *     ConcatPerspective fConcat;
 *     ClipOrder         fOrder;
 *     LocalMatrix       fLM;
 * }
 * ```
 */
public data class Config public constructor(
  /**
   * C++ original:
   * ```cpp
   * ConcatPerspective fConcat
   * ```
   */
  public var fConcat: ConcatPerspective,
  /**
   * C++ original:
   * ```cpp
   * ClipOrder         fOrder
   * ```
   */
  public var fOrder: ClipOrder,
  /**
   * C++ original:
   * ```cpp
   * LocalMatrix       fLM
   * ```
   */
  public var fLM: LocalMatrix,
)
