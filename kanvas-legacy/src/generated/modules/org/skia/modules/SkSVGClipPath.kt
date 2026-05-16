package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.gpu.Type
import undefined.ClipPathUnits

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGClipPath final : public SkSVGHiddenContainer {
 * public:
 *     static sk_sp<SkSVGClipPath> Make() {
 *         return sk_sp<SkSVGClipPath>(new SkSVGClipPath());
 *     }
 *
 *     SVG_ATTR(ClipPathUnits, SkSVGObjectBoundingBoxUnits,
 *              SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kUserSpaceOnUse))
 *
 * private:
 *     friend class SkSVGRenderContext;
 *
 *     SkSVGClipPath();
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     SkPath resolveClip(const SkSVGRenderContext&) const;
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public class SkSVGClipPath public constructor() : SkSVGHiddenContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(ClipPathUnits, SkSVGObjectBoundingBoxUnits,
   *              SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kUserSpaceOnUse))
   * ```
   */
  public fun svgATTR(
    param0: ClipPathUnits,
    param1: SkSVGObjectBoundingBoxUnits,
    param2: (Type.KUserSpaceOnUse) -> SkSVGObjectBoundingBoxUnits,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGClipPath::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setClipPathUnits(
   *                 SkSVGAttributeParser::parse<SkSVGObjectBoundingBoxUnits>("clipPathUnits", n, v));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGClipPath::resolveClip(const SkSVGRenderContext& ctx) const {
   *     auto clip = this->asPath(ctx);
   *
   *     const auto obbt = ctx.transformForCurrentOBB(fClipPathUnits);
   *     const auto m = SkMatrix::Translate(obbt.offset.x, obbt.offset.y)
   *                  * SkMatrix::Scale(obbt.scale.x, obbt.scale.y);
   *     return clip.makeTransform(m);
   * }
   * ```
   */
  private fun resolveClip(ctx: SkSVGRenderContext): Int {
    TODO("Implement resolveClip")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGClipPath> Make() {
     *         return sk_sp<SkSVGClipPath>(new SkSVGClipPath());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
