package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import undefined.SkSVGStringType

/**
 * C++ original:
 * ```cpp
 * class SkSVGTextLiteral final : public SkSVGTextFragment {
 * public:
 *     static sk_sp<SkSVGTextLiteral> Make() {
 *         return sk_sp<SkSVGTextLiteral>(new SkSVGTextLiteral());
 *     }
 *
 *     SVG_ATTR(Text, SkSVGStringType, SkSVGStringType())
 *
 * private:
 *     SkSVGTextLiteral() : INHERITED(SkSVGTag::kTextLiteral) {}
 *
 *     void onShapeText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const override;
 *
 *     void appendChild(sk_sp<SkSVGNode>) override {}
 *
 *     using INHERITED = SkSVGTextFragment;
 * }
 * ```
 */
public class SkSVGTextLiteral : SkSVGTextFragment() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Text, SkSVGStringType, SkSVGStringType())
   * ```
   */
  public fun svgATTR(
    param0: Text,
    param1: SkSVGStringType,
    param2: () -> SkSVGStringType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendChild(sk_sp<SkSVGNode>) override {}
   * ```
   */
  public override fun appendChild(param0: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextLiteral::onShapeText(const SkSVGRenderContext& ctx, SkSVGTextContext* tctx,
   *                                    SkSVGXmlSpace xs) const {
   *     SkASSERT(tctx);
   *
   *     tctx->shapeFragment(this->getText(), ctx, xs);
   * }
   * ```
   */
  public fun onShapeText(
    ctx: SkSVGRenderContext,
    tctx: SkSVGTextContext?,
    xs: SkSVGXmlSpace,
  ) {
    TODO("Implement onShapeText")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGTextLiteral> Make() {
     *         return sk_sp<SkSVGTextLiteral>(new SkSVGTextLiteral());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
