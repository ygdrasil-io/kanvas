package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Keyframe {
 *     // We can store scalar values inline; other types are stored externally,
 *     // and we track them by index.
 *     struct Value {
 *         enum class Type {
 *             kIndex,
 *             kScalar,
 *         };
 *
 *         union {
 *             uint32_t idx;
 *             float    flt;
 *         };
 *
 *         bool equals(const Value& other, Type ty) const {
 *             return ty == Type::kIndex
 *                 ? idx == other.idx
 *                 : flt == other.flt;
 *         }
 *     };
 *
 *     float    t;
 *     Value    v;
 *     uint32_t mapping; // Encodes the value interpolation in [KFRec_n .. KFRec_n+1):
 *                       //   0 -> constant
 *                       //   1 -> linear
 *                       //   n -> cubic: cubic_mappers[n-2]
 *
 *     inline static constexpr uint32_t kConstantMapping  = 0;
 *     inline static constexpr uint32_t kLinearMapping    = 1;
 *     inline static constexpr uint32_t kCubicIndexOffset = 2;
 * }
 * ```
 */
public data class Keyframe public constructor(
  /**
   * C++ original:
   * ```cpp
   * float    t
   * ```
   */
  public var t: Float,
  /**
   * C++ original:
   * ```cpp
   * Value    v
   * ```
   */
  public var v: Value,
  /**
   * C++ original:
   * ```cpp
   * uint32_t mapping
   * ```
   */
  public var mapping: Int,
) {
  public open class Value public constructor(
    private var idx: Int,
    private var flt: Float,
  ) {
    public override fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public enum class Type {
      kIndex,
      kScalar,
    }
  }

  public companion object {
    public val kConstantMapping: Int = TODO("Initialize kConstantMapping")

    public val kLinearMapping: Int = TODO("Initialize kLinearMapping")

    public val kCubicIndexOffset: Int = TODO("Initialize kCubicIndexOffset")
  }
}
