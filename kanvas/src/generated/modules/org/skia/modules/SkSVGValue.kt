package org.skia.modules

import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGValue : public SkNoncopyable {
 * public:
 *     enum class Type {
 *         kColor,
 *         kFilter,
 *         kLength,
 *         kNumber,
 *         kObjectBoundingBoxUnits,
 *         kPreserveAspectRatio,
 *         kStopColor,
 *         kString,
 *         kTransform,
 *         kViewBox,
 *     };
 *
 *     Type type() const { return fType; }
 *
 *     template <typename T>
 *     const T* as() const {
 *         return fType == T::TYPE ? static_cast<const T*>(this) : nullptr;
 *     }
 *
 * protected:
 *     explicit SkSVGValue(Type t) : fType(t) {}
 *
 * private:
 *     Type fType;
 *
 *     using INHERITED = SkNoncopyable;
 * }
 * ```
 */
public open class SkSVGValue public constructor(
  t: Type,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  private var fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * Type type() const { return fType; }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     const T* as() const {
   *         return fType == T::TYPE ? static_cast<const T*>(this) : nullptr;
   *     }
   * ```
   */
  public fun <T> `as`(): T {
    TODO("Implement as")
  }

  public enum class Type {
    kColor,
    kFilter,
    kLength,
    kNumber,
    kObjectBoundingBoxUnits,
    kPreserveAspectRatio,
    kStopColor,
    kString,
    kTransform,
    kViewBox,
  }
}

public typealias SkSVGWrapperValueINHERITED = SkSVGValue
