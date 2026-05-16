package org.skia.skcms

import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct Ctx {
 *     const void* fArg;
 *     operator NoCtx()                    { return NoCtx{}; }
 *     template <typename T> operator T*() { return (const T*)fArg; }
 * }
 * ```
 */
public data class Ctx public constructor(
  /**
   * C++ original:
   * ```cpp
   * const void* fArg
   * ```
   */
  public val fArg: Unit?,
)
