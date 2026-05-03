package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class Uniform {
 * public:
 *     static constexpr int kNonArray = 0;
 *
 *     constexpr Uniform(const char* name, SkSLType type, int count = kNonArray)
 *             : Uniform(name, type, count, /*isPaintColor=*/false) {}
 *
 *     /*
 *      * The paint color uniform is treated special and will only be added to the uniform block
 *      * once. Its name will not be mangled.
 *      */
 *     static constexpr Uniform PaintColor() {
 *         return Uniform("paintColor", SkSLType::kFloat4,
 *                        Uniform::kNonArray, /*isPaintColor=*/true);
 *     }
 *
 *     constexpr Uniform(const Uniform&) = default;
 *     Uniform& operator=(const Uniform&) = default;
 *
 *     constexpr const char* name()  const { return fName; }
 *     constexpr SkSLType    type()  const { return static_cast<SkSLType>(fType);  }
 *     constexpr int         count() const { return static_cast<int>(fCount); }
 *
 *     constexpr bool isPaintColor() const { return static_cast<bool>(fIsPaintColor); }
 *
 * private:
 *     constexpr Uniform(const char* name, SkSLType type, int count, bool isPaintColor)
 *             : fName(name)
 *             , fType(static_cast<unsigned>(type))
 *             , fIsPaintColor(static_cast<unsigned>(isPaintColor))
 *             , fCount(static_cast<unsigned>(count)) {
 *         SkASSERT(SkSLTypeCanBeUniformValue(type));
 *         SkASSERT(count >= 0);
 *     }
 *
 *     const char* fName;
 *
 *     // Uniform definitions for all encountered SkRuntimeEffects are stored permanently in the
 *     // ShaderCodeDictionary as part of the stable ShaderSnippet and code ID assigned to the
 *     // effect, including de-duplicating equivalent or re-created SkRuntimeEffects with the same
 *     // SkSL. To help keep this memory overhead as low as possible, we pack the Uniform fields
 *     // as tightly as possible.
 *     uint32_t    fType         : 6;
 *     uint32_t    fIsPaintColor : 1;
 *     uint32_t    fCount        : 25;
 *
 *     static_assert(kSkSLTypeCount <= (1 << 6));
 * }
 * ```
 */
public data class Uniform public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNonArray = 0
   * ```
   */
  private val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  private var fType: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fType
   * ```
   */
  private var fIsPaintColor: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fIsPaintColor
   * ```
   */
  private var fCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Uniform& operator=(const Uniform&) = default
   * ```
   */
  public fun assign(param0: Uniform) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr const char* name()  const { return fName; }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSLType    type()  const { return static_cast<SkSLType>(fType);  }
   * ```
   */
  public fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int         count() const { return static_cast<int>(fCount); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool isPaintColor() const { return static_cast<bool>(fIsPaintColor); }
   * ```
   */
  public fun isPaintColor(): Boolean {
    TODO("Implement isPaintColor")
  }

  public companion object {
    public val kNonArray: Int = TODO("Initialize kNonArray")

    /**
     * C++ original:
     * ```cpp
     * static constexpr Uniform PaintColor() {
     *         return Uniform("paintColor", SkSLType::kFloat4,
     *                        Uniform::kNonArray, /*isPaintColor=*/true);
     *     }
     * ```
     */
    public fun paintColor(): Uniform {
      TODO("Implement paintColor")
    }
  }
}
