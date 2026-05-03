package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkSVGTextFragment : public SkSVGTransformableNode {
 * public:
 *     void renderText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const;
 *
 * protected:
 *     explicit SkSVGTextFragment(SkSVGTag t) : INHERITED(t) {}
 *
 *     virtual void onShapeText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const = 0;
 *
 *     // Text nodes other than the root <text> element are not rendered directly.
 *     void onRender(const SkSVGRenderContext&) const override {}
 *
 * private:
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *
 *     using INHERITED = SkSVGTransformableNode;
 * }
 * ```
 */
public abstract class SkSVGTextFragment public constructor(
  t: SkSVGTag,
) : SkSVGTransformableNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextFragment::renderText(const SkSVGRenderContext& ctx, SkSVGTextContext* tctx,
   *                                    SkSVGXmlSpace xs) const {
   *     // N.B.: unlike regular elements, text fragments do not establish a new OBB scope -- they
   *     // always defer to the root <text> element for OBB resolution.
   *     SkSVGRenderContext localContext(ctx);
   *
   *     if (this->onPrepareToRender(&localContext)) {
   *         this->onShapeText(localContext, tctx, xs);
   *     }
   * }
   * ```
   */
  public fun renderText(
    ctx: SkSVGRenderContext,
    tctx: SkSVGTextContext?,
    xs: SkSVGXmlSpace,
  ) {
    TODO("Implement renderText")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onShapeText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const = 0
   * ```
   */
  protected abstract fun onShapeText(
    param0: SkSVGRenderContext,
    param1: SkSVGTextContext?,
    param2: SkSVGXmlSpace,
  )

  /**
   * C++ original:
   * ```cpp
   * void onRender(const SkSVGRenderContext&) const override {}
   * ```
   */
  protected override fun onRender(param0: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGTextFragment::onAsPath(const SkSVGRenderContext&) const {
   *     // TODO
   *     return SkPath();
   * }
   * ```
   */
  public override fun onAsPath(param0: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }
}

public typealias SkSVGTextContainerINHERITED = SkSVGTextFragment

public typealias SkSVGTextLiteralINHERITED = SkSVGTextFragment
