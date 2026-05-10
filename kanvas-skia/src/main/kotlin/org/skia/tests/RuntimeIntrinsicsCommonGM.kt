package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.tests.RuntimeIntrinsicsPlotHelper.columnsToWidth
import org.skia.tests.RuntimeIntrinsicsPlotHelper.kPadding
import org.skia.tests.RuntimeIntrinsicsPlotHelper.nextRow
import org.skia.tests.RuntimeIntrinsicsPlotHelper.plot
import org.skia.tests.RuntimeIntrinsicsPlotHelper.rowsToHeight

/**
 * Port of Skia's
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_common)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * 6-column × 7-row grid covering the GLSL "common" function
 * family : abs / sign / floor / ceil / fract / mod / min / max /
 * clamp / saturate / mix / step / smoothstep + the row-7
 * componentwise `floor(p)` / `ceil(p)` plots.
 *
 * Resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsCommon]
 * cluster (Phase D2.4.c.3) which registers 31 SkSL hashes against
 * the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton.
 */
public class RuntimeIntrinsicsCommonGM : GM() {

    override fun getName(): String = "runtime_intrinsics_common"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(6),
        rowsToHeight(7),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        // Row 1 — abs / sign
        plot(c, "abs(x)", -10f, 10f, 0f, 10f)
        plot(c, "sign(x)", -1f, 1f, -1.5f, 1.5f)
        nextRow(c)

        // Row 2 — floor / ceil / fract / mod (3 forms)
        plot(c, "floor(x)", -3f, 3f, -4f, 4f)
        plot(c, "ceil(x)", -3f, 3f, -4f, 4f)
        plot(c, "fract(x)", -3f, 3f, 0f, 1f)
        plot(c, "mod(x, 2)", -4f, 4f, -2f, 2f, label = "mod(scalar)")
        plot(c, "mod(p, -2).x", -4f, 4f, -2f, 2f, label = "mod(mixed)")
        plot(c, "mod(p, v2).x", -4f, 4f, -2f, 2f, label = "mod(vector)")
        nextRow(c)

        // Row 3 — min / max (3 forms each)
        plot(c, "min(x, 1)", 0f, 2f, 0f, 2f, label = "min(scalar)")
        plot(c, "min(p, 1).x", 0f, 2f, 0f, 2f, label = "min(mixed)")
        plot(c, "min(p, v1).x", 0f, 2f, 0f, 2f, label = "min(vector)")
        plot(c, "max(x, 1)", 0f, 2f, 0f, 2f, label = "max(scalar)")
        plot(c, "max(p, 1).x", 0f, 2f, 0f, 2f, label = "max(mixed)")
        plot(c, "max(p, v1).x", 0f, 2f, 0f, 2f, label = "max(vector)")
        nextRow(c)

        // Row 4 — clamp (3 forms) + saturate
        plot(c, "clamp(x, 1, 2)", 0f, 3f, 0f, 3f, label = "clamp(scalar)")
        plot(c, "clamp(p, 1, 2).x", 0f, 3f, 0f, 3f, label = "clamp(mixed)")
        plot(c, "clamp(p, v1, v2).x", 0f, 3f, 0f, 3f, label = "clamp(vector)")
        plot(c, "saturate(x)", -1f, 2f, -0.5f, 1.5f)
        nextRow(c)

        // Row 5 — mix (3 forms)
        plot(c, "mix(1, 2, x)", -1f, 2f, 0f, 3f, label = "mix(scalar)")
        plot(c, "mix(v1, v2, x).x", -1f, 2f, 0f, 3f, label = "mix(mixed)")
        plot(c, "mix(v1, v2, p).x", -1f, 2f, 0f, 3f, label = "mix(vector)")
        nextRow(c)

        // Row 6 — step + smoothstep (3 forms each)
        plot(c, "step(1, x)", 0f, 2f, -0.5f, 1.5f, label = "step(scalar)")
        plot(c, "step(1, p).x", 0f, 2f, -0.5f, 1.5f, label = "step(mixed)")
        plot(c, "step(v1, p).x", 0f, 2f, -0.5f, 1.5f, label = "step(vector)")
        plot(c, "smoothstep(1, 2, x)", 0.5f, 2.5f, -0.5f, 1.5f, label = "smooth(scalar)")
        plot(c, "smoothstep(1, 2, p).x", 0.5f, 2.5f, -0.5f, 1.5f, label = "smooth(mixed)")
        plot(c, "smoothstep(v1, v2, p).x", 0.5f, 2.5f, -0.5f, 1.5f, label = "smooth(vector)")
        nextRow(c)

        // Row 7 — floor(p).x / ceil(p).x / floor(p).y / ceil(p).y
        plot(c, "floor(p).x", -3f, 3f, -4f, 4f)
        plot(c, "ceil(p).x", -3f, 3f, -4f, 4f)
        plot(c, "floor(p).y", -3f, 3f, -4f, 4f)
        plot(c, "ceil(p).y", -3f, 3f, -4f, 4f)
        nextRow(c)
    }
}
