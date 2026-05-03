package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGPaint {
 * public:
 *     enum class Type {
 *         kNone,
 *         kColor,
 *         kIRI,
 *     };
 *
 *     SkSVGPaint() : fType(Type::kNone), fColor(SK_ColorBLACK) {}
 *     explicit SkSVGPaint(Type t) : fType(t), fColor(SK_ColorBLACK) {}
 *     explicit SkSVGPaint(SkSVGColor c) : fType(Type::kColor), fColor(std::move(c)) {}
 *     SkSVGPaint(const SkSVGIRI& iri, SkSVGColor fallback_color)
 *         : fType(Type::kIRI), fColor(std::move(fallback_color)), fIRI(iri) {}
 *
 *     SkSVGPaint(const SkSVGPaint&)            = default;
 *     SkSVGPaint& operator=(const SkSVGPaint&) = default;
 *     SkSVGPaint(SkSVGPaint&&)                 = default;
 *     SkSVGPaint& operator=(SkSVGPaint&&)      = default;
 *
 *     bool operator==(const SkSVGPaint& other) const {
 *         return fType == other.fType && fColor == other.fColor && fIRI == other.fIRI;
 *     }
 *     bool operator!=(const SkSVGPaint& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *     const SkSVGColor& color() const {
 *         SkASSERT(fType == Type::kColor || fType == Type::kIRI);
 *         return fColor;
 *     }
 *     const SkSVGIRI& iri() const { SkASSERT(fType == Type::kIRI); return fIRI; }
 *
 * private:
 *     Type fType;
 *
 *     // Logical union.
 *     SkSVGColor fColor;
 *     SkSVGIRI   fIRI;
 * }
 * ```
 */
public data class SkSVGPaint public constructor(
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
   * SkSVGColor fColor
   * ```
   */
  private var fColor: SkSVGColor,
  /**
   * C++ original:
   * ```cpp
   * SkSVGIRI   fIRI
   * ```
   */
  private var fIRI: SkSVGIRI,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGPaint& operator=(const SkSVGPaint&) = default
   * ```
   */
  public fun assign(param0: SkSVGPaint) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGPaint& operator=(SkSVGPaint&&)      = default
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGPaint& other) const {
   *         return fType == other.fType && fColor == other.fColor && fIRI == other.fIRI;
   *     }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGPaint& other) const { return !(*this == other); }
   * ```
   */
  public fun color(): SkSVGColor {
    TODO("Implement color")
  }

  /**
   * C++ original:
   * ```cpp
   * Type type() const { return fType; }
   * ```
   */
  public fun iri(): SkSVGIRI {
    TODO("Implement iri")
  }

  public enum class Type {
    kNone,
    kColor,
    kIRI,
  }
}
