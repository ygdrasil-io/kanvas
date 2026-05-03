package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkPMColor4f

/**
 * C++ original:
 * ```cpp
 * struct BlendInfo {
 *     SkDEBUGCODE(SkString dump() const;)
 *
 *     bool operator==(const BlendInfo& other) const {
 *         return fEquation == other.fEquation &&
 *                fSrcBlend == other.fSrcBlend &&
 *                fDstBlend == other.fDstBlend &&
 *                fBlendConstant == other.fBlendConstant &&
 *                fWritesColor == other.fWritesColor;
 *     }
 *
 *     skgpu::BlendEquation fEquation = skgpu::BlendEquation::kAdd;
 *     skgpu::BlendCoeff    fSrcBlend = skgpu::BlendCoeff::kOne;
 *     skgpu::BlendCoeff    fDstBlend = skgpu::BlendCoeff::kZero;
 *     SkPMColor4f          fBlendConstant = SK_PMColor4fTRANSPARENT;
 *     bool                 fWritesColor = true;
 * }
 * ```
 */
public data class BlendInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendEquation fEquation = skgpu::BlendEquation::kAdd
   * ```
   */
  public var fEquation: BlendEquation,
  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendCoeff    fSrcBlend = skgpu::BlendCoeff::kOne
   * ```
   */
  public var fSrcBlend: BlendCoeff,
  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendCoeff    fDstBlend = skgpu::BlendCoeff::kZero
   * ```
   */
  public var fDstBlend: BlendCoeff,
  /**
   * C++ original:
   * ```cpp
   * SkPMColor4f          fBlendConstant
   * ```
   */
  public var fBlendConstant: SkPMColor4f,
  /**
   * C++ original:
   * ```cpp
   * bool                 fWritesColor = true
   * ```
   */
  public var fWritesColor: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const BlendInfo& other) const {
   *         return fEquation == other.fEquation &&
   *                fSrcBlend == other.fSrcBlend &&
   *                fDstBlend == other.fDstBlend &&
   *                fBlendConstant == other.fBlendConstant &&
   *                fWritesColor == other.fWritesColor;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString BlendInfo::dump() const {
   *     SkString out;
   *     out.printf("writes_color(%d) equation(%s) src_coeff(%s) dst_coeff:(%s) const(0x%08x)",
   *                fWritesColor, equation_string(fEquation), coeff_string(fSrcBlend),
   *                coeff_string(fDstBlend), fBlendConstant.toBytes_RGBA());
   *     return out;
   * }
   * ```
   */
  public fun dump(): String {
    TODO("Implement dump")
  }
}
