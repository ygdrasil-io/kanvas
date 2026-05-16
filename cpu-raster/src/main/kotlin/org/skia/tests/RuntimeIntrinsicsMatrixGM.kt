package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsMatrix
import org.skia.math.SkISize
import org.skia.tests.RuntimeIntrinsicsPlotHelper.columnsToWidth
import org.skia.tests.RuntimeIntrinsicsPlotHelper.drawShaderCell
import org.skia.tests.RuntimeIntrinsicsPlotHelper.kPadding
import org.skia.tests.RuntimeIntrinsicsPlotHelper.nextRow
import org.skia.tests.RuntimeIntrinsicsPlotHelper.rowsToHeight

/**
 * Port of Skia's
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_matrix)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * 3-column × 2-row grid. Row 1 : `matrixCompMult` for 2×2 / 3×3 /
 * 4×4. Row 2 : `inverse` for 2×2 / 3×3 / 4×4. Each cell renders
 * a 2D colour map where `(p.x, p.y)` selects a single matrix cell
 * via the upstream `selN` partitioning.
 *
 * Resolves through the
 * [SkBuiltinShaderEffectsIntrinsicsMatrix]
 * cluster (Phase D2.4.c.5).
 */
public class RuntimeIntrinsicsMatrixGM : GM() {

    override fun getName(): String = "runtime_intrinsics_matrix"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(3),
        rowsToHeight(2),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        // Row 1 — matrixCompMult (3 dims). Random pairs picked by
        // upstream so that each element of compMult lies in [0, 1].
        plotCompMult(c, dim = 2,
            m1 = floatArrayOf(1.00f, 0.0f, 2.0f, 0.5f),
            m2 = floatArrayOf(0.75f, 2.0f, 0.2f, 1.2f),
            label = "compMult(2x2)")
        plotCompMult(c, dim = 3,
            m1 = floatArrayOf(1.00f, 0.0f, 2.0f, 0.5f, -1.0f, -2.0f, -0.5f, 4.00f, 0.25f),
            m2 = floatArrayOf(0.75f, 2.0f, 0.2f, 1.2f, -0.8f, -0.1f, -1.8f, 0.25f, 2.00f),
            label = "compMult(3x3)")
        plotCompMult(c, dim = 4,
            m1 = floatArrayOf(
                1.00f, 0.0f, 2.0f, 0.5f, -1.0f, -2.0f, -0.5f, 4.00f, 0.25f, 0.05f,
                10.00f, -0.66f, -1.0f, -0.5f, 0.5f, 0.66f),
            m2 = floatArrayOf(
                0.75f, 2.0f, 0.2f, 1.2f, -0.8f, -0.1f, -1.8f, 0.25f, 2.00f, 2.00f,
                0.03f, -1.00f, -1.0f, -0.5f, 1.7f, 0.66f),
            label = "compMult(4x4)")
        nextRow(c)

        // Row 2 — inverse (3 dims). Random invertible matrices
        // picked by upstream so that each element of inverse(m)
        // lies in [-1, 1] ; rendered with scale = 0.5 + bias = 0.5
        // to put the [-1, 1] range inside the [0, 1] colour space.
        plotInverse(c, dim = 2,
            m = floatArrayOf(
                1.20f, 0.68f,
                -0.27f, -1.55f),
            label = "inverse(2x2)")
        plotInverse(c, dim = 3,
            m = floatArrayOf(
                -1.13f, -2.96f, -0.14f,
                1.45f, -1.88f, -1.02f,
                -2.54f, -2.58f, -1.17f),
            label = "inverse(3x3)")
        plotInverse(c, dim = 4,
            m = floatArrayOf(
                -1.51f, -3.95f, -0.19f, 1.93f,
                -2.51f, -1.35f, -3.39f, -3.45f,
                -1.56f, 1.61f, -0.22f, -1.08f,
                -2.81f, -2.14f, -0.09f, 3.00f),
            label = "inverse(4x4)")
        nextRow(c)
    }

    private fun plotCompMult(c: SkCanvas, dim: Int, m1: FloatArray, m2: FloatArray, label: String) {
        require(m1.size == dim * dim) { "m1 must be ${dim * dim} floats, got ${m1.size}" }
        require(m2.size == dim * dim) { "m2 must be ${dim * dim} floats, got ${m2.size}" }
        val sksl = SkBuiltinShaderEffectsIntrinsicsMatrix.makeMatrixCompMultSksl(dim)
        drawShaderCell(c, label, sksl) { builder ->
            builder.uniform("m1").set(m1)
            builder.uniform("m2").set(m2)
        }
    }

    private fun plotInverse(c: SkCanvas, dim: Int, m: FloatArray, label: String) {
        require(m.size == dim * dim) { "m must be ${dim * dim} floats, got ${m.size}" }
        val sksl = SkBuiltinShaderEffectsIntrinsicsMatrix.makeMatrixInverseSksl(dim)
        drawShaderCell(c, label, sksl) { builder ->
            builder.uniform("scale").set(0.5f)
            builder.uniform("bias").set(0.5f)
            builder.uniform("m").set(m)
        }
    }
}
