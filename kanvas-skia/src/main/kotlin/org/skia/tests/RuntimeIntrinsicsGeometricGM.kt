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
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_geometric)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * 4-column × 5-row grid covering GLSL geometric functions :
 * `length` / `distance` / `dot` / `cross` / `normalize` /
 * `faceforward` / `reflect` / `refract`.
 *
 * Resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsGeometric]
 * cluster (Phase D2.4.c.4).
 */
public class RuntimeIntrinsicsGeometricGM : GM() {

    override fun getName(): String = "runtime_intrinsics_geometric"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(4),
        rowsToHeight(5),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        // Row 1 — length / distance
        plot(c, "length(x)", -1f, 1f, -0.5f, 1.5f)
        plot(c, "length(p)", 0f, 1f, 0.5f, 1.5f)
        plot(c, "distance(x, 0)", -1f, 1f, -0.5f, 1.5f)
        plot(c, "distance(p, v1)", 0f, 1f, 0.5f, 1.5f)
        nextRow(c)

        // Row 2 — dot
        plot(c, "dot(x, 2)", -1f, 1f, -2.5f, 2.5f)
        plot(c, "dot(p, p.y1)", -1f, 1f, -2.5f, 0.5f)
        nextRow(c)

        // Row 3 — cross (3 components)
        plot(c, "cross(p.xy1, p.y1x).x", 0f, 1f, -1f, 1f)
        plot(c, "cross(p.xy1, p.y1x).y", 0f, 1f, -1f, 1f)
        plot(c, "cross(p.xy1, p.y1x).z", 0f, 1f, -1f, 1f)
        nextRow(c)

        // Row 4 — normalize / faceforward
        plot(c, "normalize(x)", -2f, 2f, -1.5f, 1.5f)
        plot(c, "normalize(p).x", 0f, 2f, 0f, 1f)
        plot(c, "normalize(p).y", 0f, 2f, 0f, 1f)
        plot(c, "faceforward(v1, p.x0, v1.x0).x", -1f, 1f, -1.5f, 1.5f, label = "faceforward")
        nextRow(c)

        // Row 5 — reflect / refract
        plot(c, "reflect(p.x1, v1.0x).x", -1f, 1f, -1f, 1f, label = "reflect(horiz)")
        plot(c, "reflect(p.x1, normalize(v1)).y", -1f, 1f, -1f, 1f, label = "reflect(diag)")
        plot(c, "refract(v1.x0, v1.0x, x).x", 0f, 1f, -1f, 1f, label = "refract().x")
        plot(c, "refract(v1.x0, v1.0x, x).y", 0f, 1f, -1f, 1f, label = "refract().y")
        nextRow(c)
    }
}
