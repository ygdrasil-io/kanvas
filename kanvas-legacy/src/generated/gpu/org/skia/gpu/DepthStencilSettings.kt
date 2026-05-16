package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DepthStencilSettings {
 *     // Per-face settings for stencil
 *     struct Face {
 *         constexpr Face() = default;
 *         constexpr Face(StencilOp stencilFail,
 *                        StencilOp depthFail,
 *                        StencilOp dsPass,
 *                        CompareOp compare,
 *                        uint32_t readMask,
 *                        uint32_t writeMask)
 *                 : fStencilFailOp(stencilFail)
 *                 , fDepthFailOp(depthFail)
 *                 , fDepthStencilPassOp(dsPass)
 *                 , fCompareOp(compare)
 *                 , fReadMask(readMask)
 *                 , fWriteMask(writeMask) {}
 *
 *         StencilOp fStencilFailOp = StencilOp::kKeep;
 *         StencilOp fDepthFailOp = StencilOp::kKeep;
 *         StencilOp fDepthStencilPassOp = StencilOp::kKeep;
 *         CompareOp fCompareOp = CompareOp::kAlways;
 *         uint32_t fReadMask = 0xffffffff;
 *         uint32_t fWriteMask = 0xffffffff;
 *
 *         constexpr bool operator==(const Face& that) const {
 *             return this->fStencilFailOp == that.fStencilFailOp &&
 *                    this->fDepthFailOp == that.fDepthFailOp &&
 *                    this->fDepthStencilPassOp == that.fDepthStencilPassOp &&
 *                    this->fCompareOp == that.fCompareOp &&
 *                    this->fReadMask == that.fReadMask &&
 *                    this->fWriteMask == that.fWriteMask;
 *         }
 *     };
 *
 *     constexpr DepthStencilSettings() = default;
 *     constexpr DepthStencilSettings(Face front,
 *                                    Face back,
 *                                    uint32_t stencilRef,
 *                                    bool stencilTest,
 *                                    CompareOp depthCompare,
 *                                    bool depthTest,
 *                                    bool depthWrite)
 *             : fFrontStencil(front)
 *             , fBackStencil(back)
 *             , fStencilReferenceValue(stencilRef)
 *             , fDepthCompareOp(depthCompare)
 *             , fStencilTestEnabled(stencilTest)
 *             , fDepthTestEnabled(depthTest)
 *             , fDepthWriteEnabled(depthWrite) {}
 *
 *     constexpr bool operator==(const DepthStencilSettings& that) const {
 *         return this->fFrontStencil == that.fFrontStencil &&
 *                this->fBackStencil == that.fBackStencil &&
 *                this->fStencilReferenceValue == that.fStencilReferenceValue &&
 *                this->fDepthCompareOp == that.fDepthCompareOp &&
 *                this->fStencilTestEnabled == that.fStencilTestEnabled &&
 *                this->fDepthTestEnabled == that.fDepthTestEnabled &&
 *                this->fDepthWriteEnabled == that.fDepthWriteEnabled;
 *     }
 *
 *     Face fFrontStencil;
 *     Face fBackStencil;
 *     uint32_t fStencilReferenceValue = 0;
 *     CompareOp fDepthCompareOp = CompareOp::kAlways;
 *     bool fStencilTestEnabled = false;
 *     bool fDepthTestEnabled = false;
 *     bool fDepthWriteEnabled = false;
 * }
 * ```
 */
public data class DepthStencilSettings public constructor(
  /**
   * C++ original:
   * ```cpp
   * Face fFrontStencil
   * ```
   */
  public var fFrontStencil: Face,
  /**
   * C++ original:
   * ```cpp
   * Face fBackStencil
   * ```
   */
  public var fBackStencil: Face,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fStencilReferenceValue
   * ```
   */
  public var fStencilReferenceValue: Int,
  /**
   * C++ original:
   * ```cpp
   * CompareOp fDepthCompareOp = CompareOp::kAlways
   * ```
   */
  public var fDepthCompareOp: CompareOp,
  /**
   * C++ original:
   * ```cpp
   * bool fStencilTestEnabled = false
   * ```
   */
  public var fStencilTestEnabled: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fDepthTestEnabled = false
   * ```
   */
  public var fDepthTestEnabled: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fDepthWriteEnabled = false
   * ```
   */
  public var fDepthWriteEnabled: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator==(const DepthStencilSettings& that) const {
   *         return this->fFrontStencil == that.fFrontStencil &&
   *                this->fBackStencil == that.fBackStencil &&
   *                this->fStencilReferenceValue == that.fStencilReferenceValue &&
   *                this->fDepthCompareOp == that.fDepthCompareOp &&
   *                this->fStencilTestEnabled == that.fStencilTestEnabled &&
   *                this->fDepthTestEnabled == that.fDepthTestEnabled &&
   *                this->fDepthWriteEnabled == that.fDepthWriteEnabled;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public data class Face public constructor(
    public var fStencilFailOp: StencilOp,
    public var fDepthFailOp: StencilOp,
    public var fDepthStencilPassOp: StencilOp,
    public var fCompareOp: CompareOp,
    public var fReadMask: Int,
    public var fWriteMask: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}
