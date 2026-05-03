package org.skia.core

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct MemoryCtxPatch {
 *     std::byte scratch[kMaxScratchPerPatch];
 *
 *     MemoryCtxInfo info;
 *     void* backup;  // Remembers context->pixels so we can restore it
 * }
 * ```
 */
public data class MemoryCtxPatch public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::byte scratch[kMaxScratchPerPatch]
   * ```
   */
  public var scratch: Int,
  /**
   * C++ original:
   * ```cpp
   * MemoryCtxInfo info
   * ```
   */
  public var info: MemoryCtxInfo,
  /**
   * C++ original:
   * ```cpp
   * void* backup
   * ```
   */
  public var backup: Unit?,
)
