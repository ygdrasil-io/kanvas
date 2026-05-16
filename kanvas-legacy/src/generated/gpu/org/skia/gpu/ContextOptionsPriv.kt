package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ContextOptionsPriv {
 *
 *     int  fMaxTextureSizeOverride = SK_MaxS32;
 *
 *     /**
 *      * If true, will store a pointer in Recorder that points back to the Context
 *      * that created it. Used by readPixels() and other methods that normally require a Context.
 *      */
 *     bool fStoreContextRefInRecorder = false;
 *
 *     /**
 *      * Override Caps' default strategy heuristics to prioritize this one if set *and* is supported.
 *      */
 *     std::optional<PathRendererStrategy> fPathRendererStrategy;
 * }
 * ```
 */
public data class ContextOptionsPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * int  fMaxTextureSizeOverride = SK_MaxS32
   * ```
   */
  public var fMaxTextureSizeOverride: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fStoreContextRefInRecorder = false
   * ```
   */
  public var fStoreContextRefInRecorder: Boolean,
  /**
   * C++ original:
   * ```cpp
   * std::optional<PathRendererStrategy> fPathRendererStrategy
   * ```
   */
  public var fPathRendererStrategy: Int,
)
