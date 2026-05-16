package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGIRI {
 * public:
 *     enum class Type {
 *         kLocal,
 *         kNonlocal,
 *         kDataURI,
 *     };
 *
 *     SkSVGIRI() : fType(Type::kLocal) {}
 *     SkSVGIRI(Type t, const SkSVGStringType& iri) : fType(t), fIRI(iri) {}
 *
 *     Type type() const { return fType; }
 *     const SkSVGStringType& iri() const { return fIRI; }
 *
 *     bool operator==(const SkSVGIRI& other) const {
 *         return fType == other.fType && fIRI == other.fIRI;
 *     }
 *     bool operator!=(const SkSVGIRI& other) const { return !(*this == other); }
 *
 * private:
 *     Type fType;
 *     SkSVGStringType fIRI;
 * }
 * ```
 */
public data class SkSVGIRI public constructor(
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
   * SkSVGStringType fIRI
   * ```
   */
  private var fIRI: Int,
) {
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
   * const SkSVGStringType& iri() const { return fIRI; }
   * ```
   */
  public fun iri(): Int {
    TODO("Implement iri")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGIRI& other) const {
   *         return fType == other.fType && fIRI == other.fIRI;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public enum class Type {
    kLocal,
    kNonlocal,
    kDataURI,
  }
}
