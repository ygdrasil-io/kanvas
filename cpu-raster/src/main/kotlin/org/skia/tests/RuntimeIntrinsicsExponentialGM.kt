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
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_exponential)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * 2-column × 5-row grid of exponential intrinsics. Each cell
 * resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsExponential]
 * cluster (Phase D2.4.c.2) — registered against the same
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton as the trig cluster, just with different math lambdas.
 *
 * Same drift sources as `RuntimeIntrinsicsTrigGM` (text labels,
 * sub-surface sRGB→Rec.2020 composite, polyline AA).
 */
public class RuntimeIntrinsicsExponentialGM : GM() {

    override fun getName(): String = "runtime_intrinsics_exponential"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(2),
        rowsToHeight(5),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        // Row 1 — pow with constant exponent.
        plot(c, "pow(x, 3)", 0f, 8f, 0f, 500f)
        plot(c, "pow(x, -3)", 0f, 4f, 0f, 10f)
        nextRow(c)

        // Row 2 — pow with constant base.
        plot(c, "pow(0.9, x)", -10f, 10f, 0f, 3f)
        plot(c, "pow(1.1, x)", -10f, 10f, 0f, 3f)
        nextRow(c)

        // Row 3 — exp / log (natural).
        plot(c, "exp(x)", -1f, 7f, 0f, 1000f)
        plot(c, "log(x)", 0f, 2.5f, -4f, 1f)
        nextRow(c)

        // Row 4 — exp2 / log2.
        plot(c, "exp2(x)", -1f, 7f, 0f, 130f)
        plot(c, "log2(x)", 0f, 4f, -4f, 2f)
        nextRow(c)

        // Row 5 — sqrt / inversesqrt.
        plot(c, "sqrt(x)", 0f, 25f, 0f, 5f)
        plot(c, "inversesqrt(x)", 0f, 25f, 0.2f, 4f)
        nextRow(c)
    }
}
