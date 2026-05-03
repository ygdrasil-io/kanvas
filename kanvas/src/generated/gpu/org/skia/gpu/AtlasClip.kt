package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AtlasClip {
 *     SkIRect             fMaskBounds;
 *     SkIPoint            fOutPos;
 *     sk_sp<TextureProxy> fAtlasTexture;
 *
 *     bool isEmpty() const { return !SkToBool(fAtlasTexture.get()); }
 * }
 * ```
 */
public data class AtlasClip public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkIRect             fMaskBounds
   * ```
   */
  public var fMaskBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIPoint            fOutPos
   * ```
   */
  public var fOutPos: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fAtlasTexture
   * ```
   */
  public var fAtlasTexture: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return !SkToBool(fAtlasTexture.get()); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }
}
