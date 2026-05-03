package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct RecorderOptionsPriv {
 *     // Override the default buffer sizes of the DrawBufferManager using this option.
 *     std::optional<DrawBufferManager::Options> fDbmOptions = std::nullopt;
 * }
 * ```
 */
public data class RecorderOptionsPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::optional<DrawBufferManager::Options> fDbmOptions
   * ```
   */
  public var fDbmOptions: Int,
)
