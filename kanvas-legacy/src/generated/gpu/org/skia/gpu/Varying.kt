package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class Varying {
 * public:
 *     constexpr Varying() = default;
 *     constexpr Varying(const char* name,
 *                       SkSLType gpuType,
 *                       Interpolation interpolation = Interpolation::kPerspective)
 *             : fName(name)
 *             , fGPUType(gpuType)
 *             , fInterpolation(SkSLTypeIsIntegralType(gpuType) ? Interpolation::kFlat
 *                                                              : interpolation) {
 *         SkASSERT(name && gpuType != SkSLType::kVoid);
 *         SkASSERT(SkSLTypeVecLength(gpuType) >= 1); // Only scalar/vector types allowed as varyings.
 *         // Allow kPerspective for integer types since that's the default arg and will be replaced
 *         // with kFlat; but explicitly requesting kLinear for integer types is not allowed.
 *         SkASSERT(SkSLTypeIsFloatType(gpuType) || interpolation != Interpolation::kLinear);
 *     }
 *
 *     constexpr Varying(const Varying&) = default;
 *
 *     Varying& operator=(const Varying&) = default;
 *
 *     constexpr bool isInitialized() const { return fGPUType != SkSLType::kVoid; }
 *
 *     constexpr const char*   name()          const { return fName; }
 *     constexpr SkSLType      gpuType()       const { return fGPUType; }
 *     constexpr Interpolation interpolation() const { return fInterpolation; }
 *
 * private:
 *     const char* fName = nullptr;
 *     SkSLType fGPUType = SkSLType::kVoid;
 *     Interpolation fInterpolation = Interpolation::kPerspective;
 * }
 * ```
 */
public data class Varying public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fName = nullptr
   * ```
   */
  private val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * SkSLType fGPUType
   * ```
   */
  private var fGPUType: Int,
  /**
   * C++ original:
   * ```cpp
   * Interpolation fInterpolation = Interpolation::kPerspective
   * ```
   */
  private var fInterpolation: Interpolation,
) {
  /**
   * C++ original:
   * ```cpp
   * Varying& operator=(const Varying&) = default
   * ```
   */
  public fun assign(param0: Varying) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool isInitialized() const { return fGPUType != SkSLType::kVoid; }
   * ```
   */
  public fun isInitialized(): Boolean {
    TODO("Implement isInitialized")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr const char*   name()          const { return fName; }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSLType      gpuType()       const { return fGPUType; }
   * ```
   */
  public fun gpuType(): Int {
    TODO("Implement gpuType")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr Interpolation interpolation() const { return fInterpolation; }
   * ```
   */
  public fun interpolation(): Interpolation {
    TODO("Implement interpolation")
  }
}
