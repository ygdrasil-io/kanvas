package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Char

/**
 * C++ original:
 * ```cpp
 * class SampleUsage {
 * public:
 *     enum class Kind {
 *         // Child is never sampled
 *         kNone,
 *         // Child is only sampled at the same coordinates as the parent
 *         kPassThrough,
 *         // Child is sampled with a matrix whose value is uniform
 *         kUniformMatrix,
 *         // Child is sampled with sk_FragCoord.xy
 *         kFragCoord,
 *         // Child is sampled using explicit coordinates
 *         kExplicit,
 *     };
 *
 *     // Make a SampleUsage that corresponds to no sampling of the child at all
 *     SampleUsage() = default;
 *
 *     SampleUsage(Kind kind, bool hasPerspective) : fKind(kind), fHasPerspective(hasPerspective) {
 *         if (kind != Kind::kUniformMatrix) {
 *             SkASSERT(!fHasPerspective);
 *         }
 *     }
 *
 *     // Child is sampled with a matrix whose value is uniform. The name is fixed.
 *     static SampleUsage UniformMatrix(bool hasPerspective) {
 *         return SampleUsage(Kind::kUniformMatrix, hasPerspective);
 *     }
 *
 *     static SampleUsage Explicit() {
 *         return SampleUsage(Kind::kExplicit, false);
 *     }
 *
 *     static SampleUsage PassThrough() {
 *         return SampleUsage(Kind::kPassThrough, false);
 *     }
 *
 *     static SampleUsage FragCoord() { return SampleUsage(Kind::kFragCoord, false); }
 *
 *     bool operator==(const SampleUsage& that) const {
 *         return fKind == that.fKind && fHasPerspective == that.fHasPerspective;
 *     }
 *
 *     bool operator!=(const SampleUsage& that) const { return !(*this == that); }
 *
 *     // Arbitrary name used by all uniform sampling matrices
 *     static const char* MatrixUniformName() { return "matrix"; }
 *
 *     SampleUsage merge(const SampleUsage& other);
 *
 *     Kind kind() const { return fKind; }
 *
 *     bool hasPerspective() const { return fHasPerspective; }
 *
 *     bool isSampled()       const { return fKind != Kind::kNone; }
 *     bool isPassThrough()   const { return fKind == Kind::kPassThrough; }
 *     bool isExplicit()      const { return fKind == Kind::kExplicit; }
 *     bool isUniformMatrix() const { return fKind == Kind::kUniformMatrix; }
 *     bool isFragCoord()     const { return fKind == Kind::kFragCoord; }
 *
 * private:
 *     Kind fKind = Kind::kNone;
 *     bool fHasPerspective = false;  // Only valid if fKind is kUniformMatrix
 * }
 * ```
 */
public data class SampleUsage public constructor(
  /**
   * C++ original:
   * ```cpp
   * Kind fKind = Kind::kNone
   * ```
   */
  private var fKind: Kind,
  /**
   * C++ original:
   * ```cpp
   * bool fHasPerspective = false
   * ```
   */
  private var fHasPerspective: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SampleUsage& that) const {
   *         return fKind == that.fKind && fHasPerspective == that.fHasPerspective;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SampleUsage& that) const { return !(*this == that); }
   * ```
   */
  public fun merge(other: SampleUsage): SampleUsage {
    TODO("Implement merge")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleUsage SampleUsage::merge(const SampleUsage& other) {
   *     // This function is only used in Analysis::MergeSampleUsageVisitor to determine the combined
   *     // SampleUsage for a child fp/shader/etc. We should never see matrix sampling here.
   *     SkASSERT(fKind != Kind::kUniformMatrix && other.fKind != Kind::kUniformMatrix);
   *
   *     static_assert(Kind::kExplicit > Kind::kPassThrough);
   *     static_assert(Kind::kPassThrough > Kind::kNone);
   *     fKind = std::max(fKind, other.fKind);
   *
   *     return *this;
   * }
   * ```
   */
  public fun kind(): Kind {
    TODO("Implement kind")
  }

  /**
   * C++ original:
   * ```cpp
   * Kind kind() const { return fKind; }
   * ```
   */
  public fun hasPerspective(): Boolean {
    TODO("Implement hasPerspective")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPerspective() const { return fHasPerspective; }
   * ```
   */
  public fun isSampled(): Boolean {
    TODO("Implement isSampled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSampled()       const { return fKind != Kind::kNone; }
   * ```
   */
  public fun isPassThrough(): Boolean {
    TODO("Implement isPassThrough")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPassThrough()   const { return fKind == Kind::kPassThrough; }
   * ```
   */
  public fun isExplicit(): Boolean {
    TODO("Implement isExplicit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isExplicit()      const { return fKind == Kind::kExplicit; }
   * ```
   */
  public fun isUniformMatrix(): Boolean {
    TODO("Implement isUniformMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isUniformMatrix() const { return fKind == Kind::kUniformMatrix; }
   * ```
   */
  public fun isFragCoord(): Boolean {
    TODO("Implement isFragCoord")
  }

  public enum class Kind {
    kNone,
    kPassThrough,
    kUniformMatrix,
    kFragCoord,
    kExplicit,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SampleUsage UniformMatrix(bool hasPerspective) {
     *         return SampleUsage(Kind::kUniformMatrix, hasPerspective);
     *     }
     * ```
     */
    public fun uniformMatrix(hasPerspective: Boolean): SampleUsage {
      TODO("Implement uniformMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static SampleUsage Explicit() {
     *         return SampleUsage(Kind::kExplicit, false);
     *     }
     * ```
     */
    public fun explicit(): SampleUsage {
      TODO("Implement explicit")
    }

    /**
     * C++ original:
     * ```cpp
     * static SampleUsage PassThrough() {
     *         return SampleUsage(Kind::kPassThrough, false);
     *     }
     * ```
     */
    public fun passThrough(): SampleUsage {
      TODO("Implement passThrough")
    }

    /**
     * C++ original:
     * ```cpp
     * static SampleUsage FragCoord() { return SampleUsage(Kind::kFragCoord, false); }
     * ```
     */
    public fun fragCoord(): SampleUsage {
      TODO("Implement fragCoord")
    }

    /**
     * C++ original:
     * ```cpp
     * static const char* MatrixUniformName() { return "matrix"; }
     * ```
     */
    public fun matrixUniformName(): Char {
      TODO("Implement matrixUniformName")
    }
  }
}
