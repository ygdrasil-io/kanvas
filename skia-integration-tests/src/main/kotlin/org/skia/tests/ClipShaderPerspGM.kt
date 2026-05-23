package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of `DEF_SIMPLE_GM(clip_shader_persp, canvas, 1370, 1030)` in
 * `gm/complexclip.cpp`.
 *
 * Draws a 3×2 grid of cells, each applying a perspective matrix and two
 * nested clip-shaders (one from an image, one from a radial gradient) in
 * varying orders, with or without local-matrix wrappers, to prove that the
 * clip-shader pipeline respects the perspective transform regardless of
 * when `concat(persp)` is called relative to the two `clipShader` calls.
 *
 * **STUB.POLY_TO_POLY** — The perspective matrix is computed via
 * `SkMatrix::setPolyToPoly(srcQuad, dstQuad)` which maps the four corners
 * of the rose image to a trapezoid. [SkMatrix.setPolyToPoly] is not yet
 * implemented in :math (full 4-point homogeneous solve deferred; see
 * [SkMatrix.setPolyToPoly] KDoc for rationale). All call sites are wired
 * through [SkMatrix.setPolyToPoly] so that `grep 'TODO("STUB.POLY_TO_POLY")'`
 * in `:kanvas-skia` finds them automatically once the stub is lifted.
 *
 * Reference image: `clip_shader_persp.png`, 1370 × 1030.
 */
public class ClipShaderPerspGM : GM() {

    override fun getName(): String = "clip_shader_persp"
    override fun getISize(): SkISize = SkISize.Make(1370, 1030)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val img = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return

        // Build the perspective matrix that maps the image quad to a trapezoid.
        // Upstream: SkMatrix persp; SkAssertResult(persp.setPolyToPoly(src, dst));
        val src = arrayOf(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(img.width.toFloat(), 0f),
            SkPoint.Make(img.width.toFloat(), img.height.toFloat()),
            SkPoint.Make(0f, img.height.toFloat()),
        )
        val dst = arrayOf(
            SkPoint.Make(0f, 80f),
            SkPoint.Make(img.width + 28f, -100f),
            SkPoint.Make(img.width - 28f, img.height + 100f),
            SkPoint.Make(0f, img.height - 80f),
        )
        // STUB.POLY_TO_POLY — throws NotImplementedError; @Disabled test captures it.
        @Suppress("UNUSED_VARIABLE")
        val persp: SkMatrix? = SkMatrix.setPolyToPoly(src, dst)

        // Body is unreachable (TODO above throws), but is listed here
        // so grep("STUB.POLY_TO_POLY") surfaces this file as a live call site.
        c.save()
        c.restore()
    }
}
