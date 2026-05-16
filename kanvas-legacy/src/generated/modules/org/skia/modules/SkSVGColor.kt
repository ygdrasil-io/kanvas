package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkNVRefCnt
import undefined.Vars

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGColor {
 * public:
 *     enum class Type {
 *         kCurrentColor,
 *         kColor,
 *         kICCColor,
 *     };
 *     using Vars = std::vector<SkString>;
 *
 *     SkSVGColor() : SkSVGColor(SK_ColorBLACK) {}
 *     explicit SkSVGColor(const SkSVGColorType& c) : fType(Type::kColor), fColor(c), fVars(nullptr) {}
 *     explicit SkSVGColor(Type t, Vars&& vars)
 *         : fType(t), fColor(SK_ColorBLACK)
 *         , fVars(vars.empty() ? nullptr : new RefCntVars(std::move(vars))) {}
 *     explicit SkSVGColor(const SkSVGColorType& c, Vars&& vars)
 *         : fType(Type::kColor), fColor(c)
 *         , fVars(vars.empty() ? nullptr : new RefCntVars(std::move(vars))) {}
 *
 *     SkSVGColor(const SkSVGColor&)            = default;
 *     SkSVGColor& operator=(const SkSVGColor&) = default;
 *     SkSVGColor(SkSVGColor&&)                 = default;
 *     SkSVGColor& operator=(SkSVGColor&&)      = default;
 *
 *     bool operator==(const SkSVGColor& other) const {
 *         return fType == other.fType && fColor == other.fColor && fVars == other.fVars;
 *     }
 *     bool operator!=(const SkSVGColor& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *     const SkSVGColorType& color() const { SkASSERT(fType == Type::kColor); return fColor; }
 *     SkSpan<const SkString> vars() const {
 *         return fVars ? SkSpan<const SkString>(fVars->fData) : SkSpan<const SkString>();
 *     }
 *     SkSpan<SkString> vars()       {
 *         return fVars ? SkSpan<SkString>(fVars->fData) : SkSpan<SkString>();
 *     }
 *
 * private:
 *     Type fType;
 *     SkSVGColorType fColor;
 *     struct RefCntVars : public SkNVRefCnt<RefCntVars> {
 *         RefCntVars(Vars&& vars) : fData(std::move(vars)) {}
 *         Vars fData;
 *     };
 *     sk_sp<RefCntVars> fVars;
 * }
 * ```
 */
public data class SkSVGColor public constructor(
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
   * SkSVGColorType fColor
   * ```
   */
  private var fColor: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<RefCntVars> fVars
   * ```
   */
  private var fVars: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGColor& operator=(const SkSVGColor&) = default
   * ```
   */
  public fun assign(param0: SkSVGColor) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGColor& operator=(SkSVGColor&&)      = default
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGColor& other) const {
   *         return fType == other.fType && fColor == other.fColor && fVars == other.fVars;
   *     }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGColor& other) const { return !(*this == other); }
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
  public fun vars(): Int {
    TODO("Implement vars")
  }

  public open class RefCntVars public constructor(
    public var fData: Int,
  ) : SkNVRefCnt(TODO()),
      undefined.RefCntVars {
    public constructor(vars: Vars) : this() {
      TODO("Implement constructor")
    }
  }

  public enum class Type {
    kCurrentColor,
    kColor,
    kICCColor,
  }
}
