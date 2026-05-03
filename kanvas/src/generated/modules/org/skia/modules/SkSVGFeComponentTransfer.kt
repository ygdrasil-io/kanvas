package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeComponentTransfer final : public SkSVGFe {
 * public:
 *     static constexpr SkSVGTag tag = SkSVGTag::kFeComponentTransfer;
 *
 *     static sk_sp<SkSVGFeComponentTransfer> Make() {
 *         return sk_sp<SkSVGFeComponentTransfer>(new SkSVGFeComponentTransfer());
 *     }
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
 *
 * private:
 *     SkSVGFeComponentTransfer() : INHERITED(tag) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public class SkSVGFeComponentTransfer public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeComponentTransfer::onMakeImageFilter(
   *         const SkSVGRenderContext& ctx,
   *         const SkSVGFilterContext& fctx) const {
   *     std::vector<uint8_t> a_tbl, b_tbl, g_tbl, r_tbl;
   *
   *     for (const auto& child : fChildren) {
   *         switch (child->tag()) {
   *             case SkSVGTag::kFeFuncA:
   *                 a_tbl = static_cast<const SkSVGFeFunc*>(child.get())->getTable();
   *                 break;
   *             case SkSVGTag::kFeFuncB:
   *                 b_tbl = static_cast<const SkSVGFeFunc*>(child.get())->getTable();
   *                 break;
   *             case SkSVGTag::kFeFuncG:
   *                 g_tbl = static_cast<const SkSVGFeFunc*>(child.get())->getTable();
   *                 break;
   *             case SkSVGTag::kFeFuncR:
   *                 r_tbl = static_cast<const SkSVGFeFunc*>(child.get())->getTable();
   *                 break;
   *             default:
   *                 break;
   *         }
   *     }
   *     SkASSERT(a_tbl.empty() || a_tbl.size() == 256);
   *     SkASSERT(b_tbl.empty() || b_tbl.size() == 256);
   *     SkASSERT(g_tbl.empty() || g_tbl.size() == 256);
   *     SkASSERT(r_tbl.empty() || r_tbl.size() == 256);
   *
   *     const SkRect cropRect = this->resolveFilterSubregion(ctx, fctx);
   *     const sk_sp<SkImageFilter> input = fctx.resolveInput(ctx,
   *                                                          this->getIn(),
   *                                                          this->resolveColorspace(ctx, fctx));
   *
   *     const auto cf =  SkColorFilters::TableARGB(a_tbl.empty() ? nullptr : a_tbl.data(),
   *                                                r_tbl.empty() ? nullptr : r_tbl.data(),
   *                                                g_tbl.empty() ? nullptr : g_tbl.data(),
   *                                                b_tbl.empty() ? nullptr : b_tbl.data());
   *
   *     return SkImageFilters::ColorFilter(std::move(cf), std::move(input), cropRect);
   * }
   * ```
   */
  protected override fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement onMakeImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
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
     * static sk_sp<SkSVGFeComponentTransfer> Make() {
     *         return sk_sp<SkSVGFeComponentTransfer>(new SkSVGFeComponentTransfer());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
