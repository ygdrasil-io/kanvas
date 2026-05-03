package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGHiddenContainer : public SkSVGContainer {
 * protected:
 *     explicit SkSVGHiddenContainer(SkSVGTag t) : INHERITED(t) {}
 *
 *     void onRender(const SkSVGRenderContext&) const final {}
 *
 * private:
 *     using INHERITED = SkSVGContainer;
 * }
 * ```
 */
public open class SkSVGHiddenContainer public constructor(
  t: SkSVGTag,
) : SkSVGContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void onRender(const SkSVGRenderContext&) const final {}
   * ```
   */
  protected override fun onRender(param0: SkSVGRenderContext) {
    TODO("Implement onRender")
  }
}
