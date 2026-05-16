package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API RenderPassProperties {
 *     bool operator==(const RenderPassProperties& other) const {
 *         return fDSFlags == other.fDSFlags &&
 *                fDstCT == other.fDstCT &&
 *                fRequiresMSAA == other.fRequiresMSAA &&
 *                SkColorSpace::Equals(fDstCS.get(), other.fDstCS.get());
 *     }
 *     bool operator!= (const RenderPassProperties& other) const { return !(*this == other); }
 *
 *     DepthStencilFlags   fDSFlags      = DepthStencilFlags::kNone;
 *     SkColorType         fDstCT        = kRGBA_8888_SkColorType;
 *     sk_sp<SkColorSpace> fDstCS        = nullptr;
 *     bool                fRequiresMSAA = false;
 * }
 * ```
 */
public data class RenderPassProperties public constructor(
  /**
   * C++ original:
   * ```cpp
   * DepthStencilFlags   fDSFlags
   * ```
   */
  public var fDSFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColorType         fDstCT
   * ```
   */
  public var fDstCT: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fDstCS
   * ```
   */
  public var fDstCS: Int,
  /**
   * C++ original:
   * ```cpp
   * bool                fRequiresMSAA = false
   * ```
   */
  public var fRequiresMSAA: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const RenderPassProperties& other) const {
   *         return fDSFlags == other.fDSFlags &&
   *                fDstCT == other.fDstCT &&
   *                fRequiresMSAA == other.fRequiresMSAA &&
   *                SkColorSpace::Equals(fDstCS.get(), other.fDstCS.get());
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
