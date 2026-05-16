package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct EmbossCtx {
 *     MemoryCtx mul, add;
 * }
 * ```
 */
public data class EmbossCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * MemoryCtx mul
   * ```
   */
  public var mul: MemoryCtx,
  /**
   * C++ original:
   * ```cpp
   * MemoryCtx mul, add
   * ```
   */
  public var add: MemoryCtx,
)
