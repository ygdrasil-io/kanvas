package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFuncIRI {
 * public:
 *     enum class Type {
 *         kNone,
 *         kIRI,
 *     };
 *
 *     SkSVGFuncIRI() : fType(Type::kNone) {}
 *     explicit SkSVGFuncIRI(Type t) : fType(t) {}
 *     explicit SkSVGFuncIRI(SkSVGIRI&& iri) : fType(Type::kIRI), fIRI(std::move(iri)) {}
 *
 *     bool operator==(const SkSVGFuncIRI& other) const {
 *         return fType == other.fType && fIRI == other.fIRI;
 *     }
 *     bool operator!=(const SkSVGFuncIRI& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *     const SkSVGIRI& iri() const { SkASSERT(fType == Type::kIRI); return fIRI; }
 *
 * private:
 *     Type           fType;
 *     SkSVGIRI       fIRI;
 * }
 * ```
 */
public data class SkSVGFuncIRI public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type           fType
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * SkSVGIRI       fIRI
   * ```
   */
  private var fIRI: SkSVGIRI,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGFuncIRI& other) const {
   *         return fType == other.fType && fIRI == other.fIRI;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFuncIRI& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
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
    kIRI,
  }
}
