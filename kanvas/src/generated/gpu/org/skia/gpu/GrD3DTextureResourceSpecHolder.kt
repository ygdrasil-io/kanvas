package org.skia.gpu

import GrD3DTextureResourceSpec
import kotlin.UInt
import undefined.GrD3DSurfaceInfo

/**
 * C++ original:
 * ```cpp
 * struct GrD3DTextureResourceSpecHolder {
 * public:
 *     GrD3DTextureResourceSpecHolder(const GrD3DSurfaceInfo&);
 *
 *     void cleanup();
 *
 *     GrD3DSurfaceInfo getSurfaceInfo(uint32_t sampleCount,
 *                                     uint32_t levelCount,
 *                                     skgpu::Protected isProtected) const;
 *
 * private:
 *     GrD3DTextureResourceSpec* fSpec;
 * }
 * ```
 */
public data class GrD3DTextureResourceSpecHolder public constructor(
  /**
   * C++ original:
   * ```cpp
   * GrD3DTextureResourceSpec* fSpec
   * ```
   */
  private var fSpec: GrD3DTextureResourceSpec?,
) {
  /**
   * C++ original:
   * ```cpp
   * void cleanup()
   * ```
   */
  public fun cleanup() {
    TODO("Implement cleanup")
  }

  /**
   * C++ original:
   * ```cpp
   * GrD3DSurfaceInfo getSurfaceInfo(uint32_t sampleCount,
   *                                     uint32_t levelCount,
   *                                     skgpu::Protected isProtected) const
   * ```
   */
  public fun getSurfaceInfo(
    sampleCount: UInt,
    levelCount: UInt,
    isProtected: Protected,
  ): GrD3DSurfaceInfo {
    TODO("Implement getSurfaceInfo")
  }
}
