package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/workingspace.cpp::workingspace`
 * (`DEF_SIMPLE_GM_CAN_FAIL(workingspace, canvas, errorMsg, 200, 350)`).
 *
 * The GM draws a 4-column × 7-row grid of 40×40 cells that verify the
 * `makeWithWorkingColorSpace` API on both [SkColorFilter] and [SkShader].
 * Every cell should render green when the working-CS logic is correct.
 *
 * The upstream logic depends on six internal helper factories:
 *
 * - `raw_cf(color)` — `SkRuntimeEffect::MakeForColorFilter("uniform half4 c; half4 main(half4 color) { return c; }")` + `SkData::MakeWithCopy`.
 * - `managed_cf(color)` — same SkSL but with `layout(color)` on the uniform.
 * - `indirect_cf(color)` — a color filter whose body samples a `uniform shader s`
 *   child; requires `SkRuntimeEffect::makeColorFilter(nullptr, {shader_child})`,
 *   i.e. a color filter effect with a *shader-typed* child slot — not yet
 *   supported by the kanvas-skia [SkRuntimeEffect.makeColorFilter] overload.
 * - `mode_cf(color)` — `SkColorFilters::Blend(color, nullptr, kSrc)` with an
 *   [org.graphiks.math.SkColor4f] argument, which routes to the stubbed
 *   `STUB.COLOR4F_BLEND_CF` overload.
 * - `raw_shader(color)` / `managed_shader(color)` — same SkSL pattern as above
 *   but compiled via `MakeForShader`; the SkSL strings are not registered in
 *   [org.skia.effects.runtime.SkRuntimeEffectDispatch].
 *
 * **Missing API** : the three blocking gaps are
 *  1. `STUB.COLOR4F_BLEND_CF` — [org.skia.foundation.SkColorFilters.Blend]
 *     overload for [org.graphiks.math.SkColor4f] (mode_cf).
 *  2. `STUB.CF_SHADER_CHILD` — [SkRuntimeEffect.makeColorFilter] needs a
 *     variant that accepts shader-typed child slots (indirect_cf).
 *  3. `STUB.WORKING_SPACE_RT` — the SkSL programs for `raw_cf`, `managed_cf`,
 *     `raw_shader`, `managed_shader` are not registered in the dispatch table.
 *
 * TODO("STUB.WORKING_SPACE_RT")
 */
public class WorkingspaceGM : GM() {

    override fun getName(): String = "workingspace"
    override fun getISize(): SkISize = SkISize.Make(200, 350)

    override fun onDraw(canvas: SkCanvas?) {
        TODO(
            "STUB.WORKING_SPACE_RT: workingspace GM requires (1) SkColorFilters.Blend(SkColor4f, null, kSrc)" +
                " [STUB.COLOR4F_BLEND_CF], (2) SkRuntimeEffect.makeColorFilter with shader-typed child slots" +
                " [STUB.CF_SHADER_CHILD], and (3) registered SkSL impls in SkRuntimeEffectDispatch for the" +
                " raw/managed uniform-half4 color-filter and shader programs used by workingspace.cpp.",
        )
    }
}
