package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsRelational
import org.graphiks.math.SkISize
import org.skia.tests.RuntimeIntrinsicsPlotHelper.columnsToWidth
import org.skia.tests.RuntimeIntrinsicsPlotHelper.drawShaderCell
import org.skia.tests.RuntimeIntrinsicsPlotHelper.kPadding
import org.skia.tests.RuntimeIntrinsicsPlotHelper.nextRow
import org.skia.tests.RuntimeIntrinsicsPlotHelper.rowsToHeight

/**
 * Port of Skia's
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_relational)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * 4-column × 6-row grid (some rows have only 2 cells). Each cell
 * exercises one bvec relational expression — `lessThan` /
 * `lessThanEqual` / `greaterThan` / `greaterThanEqual` / `equal` /
 * `notEqual`, in float and int variants, plus bvec compositions
 * (`equal(le, ge)`), `not()` inverses, and `any` / `all` reductions
 * broadcast back to bool2.
 *
 * Resolves through
 * [SkBuiltinShaderEffectsIntrinsicsRelational]
 * cluster (Phase D2.4.c.6).
 */
public class RuntimeIntrinsicsRelationalGM : GM() {

    override fun getName(): String = "runtime_intrinsics_relational"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(4),
        rowsToHeight(6),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        // Row 1 — lessThan / lessThanEqual (float + int variants)
        plotBvec(c, "float", "lessThan(p, v1)", "lessThan")
        plotBvec(c, "int", "lessThan(int2(p), v1)", "lessThan(int)")
        plotBvec(c, "float", "lessThanEqual(p, v1)", "lessThanEqual")
        plotBvec(c, "int", "lessThanEqual(int2(p), v1)", "lessThanEqual(int)")
        nextRow(c)

        // Row 2 — greaterThan / greaterThanEqual (float + int)
        plotBvec(c, "float", "greaterThan(p, v1)", "greaterThan")
        plotBvec(c, "int", "greaterThan(int2(p), v1)", "greaterThan(int)")
        plotBvec(c, "float", "greaterThanEqual(p, v1)", "greaterThanEqual")
        plotBvec(c, "int", "greaterThanEqual(int2(p), v1)", "greaterThanEqual(int)")
        nextRow(c)

        // Row 3 — equal / notEqual (float + int)
        plotBvec(c, "float", "equal(p, v1)", "equal")
        plotBvec(c, "int", "equal(int2(p), v1)", "equal(int)")
        plotBvec(c, "float", "notEqual(p, v1)", "notEqual")
        plotBvec(c, "int", "notEqual(int2(p), v1)", "notEqual(int)")
        nextRow(c)

        // Row 4 — bvec compositions (only 2 cells)
        plotBvec(c, "float",
            "equal(   lessThanEqual(p, v1), greaterThanEqual(p, v1))",
            "equal(bvec)")
        plotBvec(c, "float",
            "notEqual(lessThanEqual(p, v1), greaterThanEqual(p, v1))",
            "notequal(bvec)")
        nextRow(c)

        // Row 5 — not() inverses (only 2 cells)
        plotBvec(c, "float", "not(notEqual(p, v1))", "not(notEqual)")
        plotBvec(c, "float", "not(equal(p, v1))", "not(equal)")
        nextRow(c)

        // Row 6 — any / all reductions (only 2 cells)
        plotBvec(c, "float", "bool2(any(equal(p, v1)))", "any(equal)")
        plotBvec(c, "float", "bool2(all(equal(p, v1)))", "all(equal)")
        nextRow(c)
    }

    private fun plotBvec(c: SkCanvas, type: String, fn: String, label: String) {
        val sksl = SkBuiltinShaderEffectsIntrinsicsRelational.makeBvecSksl(type, fn)
        drawShaderCell(c, label, sksl) { builder ->
            // v1 = (-2, -2) in either float2 or int2 form.
            if (type == "int") {
                builder.uniform("v1").set(intArrayOf(-2, -2))
            } else {
                builder.uniform("v1").set(floatArrayOf(-2f, -2f))
            }
        }
    }
}
