package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import undefined.In

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeMergeNode : public SkSVGHiddenContainer {
 * public:
 *     static constexpr SkSVGTag tag = SkSVGTag::kFeMergeNode;
 *
 *     static sk_sp<SkSVGFeMergeNode> Make() {
 *         return sk_sp<SkSVGFeMergeNode>(new SkSVGFeMergeNode());
 *     }
 *
 *     SVG_ATTR(In, SkSVGFeInputType, SkSVGFeInputType())
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGFeMergeNode() : INHERITED(tag) {}
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public open class SkSVGFeMergeNode public constructor() : SkSVGHiddenContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(In, SkSVGFeInputType, SkSVGFeInputType())
   * ```
   */
  public fun svgATTR(
    param0: In,
    param1: SkSVGFeInputType,
    param2: () -> SkSVGFeInputType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeMergeNode::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setIn(SkSVGAttributeParser::parse<SkSVGFeInputType>("in", name, value));
   * }
   * ```
   */
  public fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    public val tag: Int = TODO("Initialize tag")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeMergeNode> Make() {
     *         return sk_sp<SkSVGFeMergeNode>(new SkSVGFeMergeNode());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
