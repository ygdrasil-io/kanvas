package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import undefined.Offset

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGStop : public SkSVGHiddenContainer {
 * public:
 *     static constexpr SkSVGTag tag = SkSVGTag::kStop;
 *
 *     static sk_sp<SkSVGStop> Make() {
 *         return sk_sp<SkSVGStop>(new SkSVGStop());
 *     }
 *
 *     SVG_ATTR(Offset, SkSVGLength, SkSVGLength(0, SkSVGLength::Unit::kPercentage))
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGStop();
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public open class SkSVGStop public constructor() : SkSVGHiddenContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Offset, SkSVGLength, SkSVGLength(0, SkSVGLength::Unit::kPercentage))
   * ```
   */
  public fun svgATTR(
    param0: Offset,
    param1: SkSVGLength,
    param2: (Int, SkSVGLength.Unit.KPercentage) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGStop::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setOffset(SkSVGAttributeParser::parse<SkSVGLength>("offset", n, v));
   * }
   * ```
   */
  public fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    public val tag: Int = TODO("Initialize tag")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGStop> Make() {
     *         return sk_sp<SkSVGStop>(new SkSVGStop());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
