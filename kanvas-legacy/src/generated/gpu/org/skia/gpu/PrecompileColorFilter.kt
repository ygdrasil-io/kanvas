package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileColorFilter : public PrecompileBase {
 * public:
 *     /**
 *      *  This is the Precompile correlate to SkColorFilter::makeComposed.
 *      *
 *      *  The PrecompileColorFilters::Compose factory can be used to generate a set of color filters
 *      *  that would've been generated via multiple makeComposed calls. That is, rather than
 *      *  performing:
 *      *     sk_sp<PrecompileColorFilter> option1 = outer->makeComposed(colorFilter1);
 *      *     sk_sp<PrecompileColorFilter> option2 = outer->makeComposed(colorFilter2);
 *      *  one could call:
 *      *     sk_sp<PrecompileColorFilter> combinedOptions = Compose({ outer },
 *      *                                                            { colorFilter1, colorFilter2 });
 *      *  With an alternative use case one could also use the Compose factory thusly:
 *      *     sk_sp<PrecompileColorFilter> combinedOptions = Compose({ outer1, outer2 },
 *      *                                                            { innerColorFilter });
 *      */
 *     sk_sp<PrecompileColorFilter> makeComposed(sk_sp<PrecompileColorFilter> inner) const;
 *
 * protected:
 *     PrecompileColorFilter() : PrecompileBase(Type::kColorFilter) {}
 *     ~PrecompileColorFilter() override;
 * }
 * ```
 */
public open class PrecompileColorFilter public constructor() : PrecompileBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileColorFilter> PrecompileColorFilter::makeComposed(
   *         sk_sp<PrecompileColorFilter> inner) const {
   *     if (!inner) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     return PrecompileColorFilters::Compose({{ sk_ref_sp(this) }}, {{ std::move(inner) }});
   * }
   * ```
   */
  public fun makeComposed(`inner`: SkSp<PrecompileColorFilter>): Int {
    TODO("Implement makeComposed")
  }
}
