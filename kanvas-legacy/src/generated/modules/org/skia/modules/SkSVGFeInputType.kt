package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeInputType {
 * public:
 *     enum class Type {
 *         kSourceGraphic,
 *         kSourceAlpha,
 *         kBackgroundImage,
 *         kBackgroundAlpha,
 *         kFillPaint,
 *         kStrokePaint,
 *         kFilterPrimitiveReference,
 *         kUnspecified,
 *     };
 *
 *     SkSVGFeInputType() : fType(Type::kUnspecified) {}
 *     explicit SkSVGFeInputType(Type t) : fType(t) {}
 *     explicit SkSVGFeInputType(const SkSVGStringType& id)
 *             : fType(Type::kFilterPrimitiveReference), fId(id) {}
 *
 *     bool operator==(const SkSVGFeInputType& other) const {
 *         return fType == other.fType && fId == other.fId;
 *     }
 *     bool operator!=(const SkSVGFeInputType& other) const { return !(*this == other); }
 *
 *     const SkString& id() const {
 *         SkASSERT(fType == Type::kFilterPrimitiveReference);
 *         return fId;
 *     }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 *     SkString fId;
 * }
 * ```
 */
public data class SkSVGFeInputType public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * SkString fId
   * ```
   */
  private var fId: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGFeInputType& other) const {
   *         return fType == other.fType && fId == other.fId;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFeInputType& other) const { return !(*this == other); }
   * ```
   */
  public fun id(): Int {
    TODO("Implement id")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& id() const {
   *         SkASSERT(fType == Type::kFilterPrimitiveReference);
   *         return fId;
   *     }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kSourceGraphic,
    kSourceAlpha,
    kBackgroundImage,
    kBackgroundAlpha,
    kFillPaint,
    kStrokePaint,
    kFilterPrimitiveReference,
    kUnspecified,
  }
}
