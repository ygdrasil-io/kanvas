package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkSp
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class ExternalColorFilter final : public EffectNode {
 * public:
 *     static sk_sp<ExternalColorFilter> Make(sk_sp<RenderNode> child);
 *
 *     ~ExternalColorFilter() override;
 *
 *     enum class Coverage {
 *         kNormal,       // the effect applies to the regular content coverage
 *         kBoundingBox,  // the effect applies to the full content bounding box
 *     };
 *
 *     SG_ATTRIBUTE(ColorFilter, sk_sp<SkColorFilter>, fColorFilter)
 *     SG_ATTRIBUTE(Coverage   , Coverage            , fCoverage   )
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *
 * private:
 *     explicit ExternalColorFilter(sk_sp<RenderNode>);
 *
 *     sk_sp<SkColorFilter> fColorFilter;
 *     Coverage             fCoverage = Coverage::kNormal;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class ExternalColorFilter public constructor(
  child: SkSp<RenderNode>,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * explicit ExternalColorFilter(sk_sp<RenderNode>)
   * ```
   */
  private var skSp: ExternalColorFilter = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fColorFilter
   * ```
   */
  private var fColorFilter: Int = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * Coverage             fCoverage = Coverage::kNormal
   * ```
   */
  private var fCoverage: Coverage = TODO("Initialize fCoverage")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(ColorFilter, sk_sp<SkColorFilter>, fColorFilter)
   * ```
   */
  public fun sgATTRIBUTE(param0: ColorFilter, param1: SkSp<SkColorFilter>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void ExternalColorFilter::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     auto local_ctx = ScopedRenderContext(canvas, ctx).modulateColorFilter(fColorFilter);
   *
   *     if (fCoverage == Coverage::kBoundingBox) {
   *         // For bounding box coverage, use a layer clipped to the content bounding box.
   *         canvas->save();
   *         canvas->clipRect(this->bounds(), /*doAntiAlias=*/ true);
   *         local_ctx.setIsolation(this->bounds(), canvas->getTotalMatrix(), /*do_isolate=*/ true);
   *     }
   *
   *     this->INHERITED::onRender(canvas, local_ctx);
   * }
   * ```
   */
  public fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  public enum class Coverage {
    kNormal,
    kBoundingBox,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ExternalColorFilter> ExternalColorFilter::Make(sk_sp<RenderNode> child) {
     *     return child ? sk_sp<ExternalColorFilter>(new ExternalColorFilter(std::move(child)))
     *                  : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>): Int {
      TODO("Implement make")
    }
  }
}
