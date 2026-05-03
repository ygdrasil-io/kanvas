package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeMerge : public SkSVGFe {
 * public:
 *     static constexpr SkSVGTag tag = SkSVGTag::kFeMerge;
 *
 *     static sk_sp<SkSVGFeMerge> Make() { return sk_sp<SkSVGFeMerge>(new SkSVGFeMerge()); }
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override;
 *
 * private:
 *     SkSVGFeMerge() : INHERITED(tag) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeMerge public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeMerge::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                      const SkSVGFilterContext& fctx) const {
   *     const SkSVGColorspace colorspace = this->resolveColorspace(ctx, fctx);
   *
   *     skia_private::STArray<8, sk_sp<SkImageFilter>> merge_node_filters;
   *     merge_node_filters.reserve(fChildren.size());
   *
   *     this->forEachChild<SkSVGFeMergeNode>([&](const SkSVGFeMergeNode* child) {
   *         merge_node_filters.push_back(fctx.resolveInput(ctx, child->getIn(), colorspace));
   *     });
   *
   *     return SkImageFilters::Merge(merge_node_filters.data(),
   *                                  merge_node_filters.size(),
   *                                  this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  protected override fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement onMakeImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> SkSVGFeMerge::getInputs() const {
   *     std::vector<SkSVGFeInputType> inputs;
   *     inputs.reserve(fChildren.size());
   *
   *     this->forEachChild<SkSVGFeMergeNode>([&](const SkSVGFeMergeNode* child) {
   *         inputs.push_back(child->getIn());
   *     });
   *
   *     return inputs;
   * }
   * ```
   */
  protected override fun getInputs(): Int {
    TODO("Implement getInputs")
  }

  public companion object {
    public val tag: Int = TODO("Initialize tag")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeMerge> Make() { return sk_sp<SkSVGFeMerge>(new SkSVGFeMerge()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
