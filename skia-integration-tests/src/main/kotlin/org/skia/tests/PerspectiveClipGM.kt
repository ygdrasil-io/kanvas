package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/perspshaders.cpp::DEF_SIMPLE_GM(perspective_clip, …)`
 * (registered as `perspective_clip`, 800 × 800).
 *
 * Draws a random path twice:
 *  1. Filled with a flat grey color (`{0.75, 0.75, 0.75, 1}`).
 *  2. Under a crazy perspective matrix (derived from `halfplanes3`) with an
 *     image-shader paint built from `mandrill_128.png` × `Scale(3, 3)`.
 *
 * The purpose is to exercise the "half-plane" clipping path where part of
 * the geometry is "behind" the viewer under the perspective transform.
 *
 * C++ original:
 * ```cpp
 * static SkPath make_path() {
 *     SkRandom rand;
 *     auto rand_pt = [&rand]() {
 *         auto x = rand.nextF();
 *         auto y = rand.nextF();
 *         return SkPoint{x * 400, y * 400};
 *     };
 *     SkPathBuilder builder;
 *     for (int i = 0; i < 4; ++i) {
 *         SkPoint pts[6];
 *         for (auto& p : pts) { p = rand_pt(); }
 *         builder.moveTo(pts[0])
 *                .quadTo(pts[1], pts[2])
 *                .quadTo(pts[3], pts[4]).lineTo(pts[5]);
 *     }
 *     return builder.detach();
 * }
 *
 * DEF_SIMPLE_GM(perspective_clip, canvas, 800, 800) {
 *     SkPath path = make_path();
 *     auto shader = ToolUtils::GetResourceAsImage("images/mandrill_128.png")
 *                          ->makeShader(SkSamplingOptions(), SkMatrix::Scale(3, 3));
 *     SkPaint paint;
 *     paint.setColor({0.75, 0.75, 0.75, 1});
 *     canvas->drawPath(path, paint);
 *
 *     // A crazy perspective matrix derived from halfplanes3 — part of the
 *     // geometry is "behind" the viewer, exercising half-plane clipping.
 *     SkMatrix mx;
 *     const SkScalar array[] = {
 *         -1.7866f,  1.3357f, 273.0295f,
 *         -1.0820f,  1.3186f, 135.5196f,
 *         -0.0047f, -0.0015f,  2.1485f,
 *     };
 *     mx.set9(array);
 *
 *     paint.setShader(shader);
 *     canvas->concat(mx);
 *     canvas->drawPath(path, paint);
 * }
 * ```
 *
 * **Porting notes**:
 *  - [SkRandom] with default seed 0 matches upstream's `SkRandom rand;` —
 *    both use the same LCG seed initialisation, so the path geometry is
 *    bit-identical to the C++ reference.
 *  - `SkMatrix::Scale(3, 3)` maps to [SkMatrix.MakeScale].
 *  - `mx.set9(array)` maps to [SkMatrix.MakeFrom9] which is the canonical
 *    Kotlin API for constructing a full 3×3 perspective matrix from a flat
 *    9-element row-major array.
 *  - The mandrill shader load returns `null` when the classpath resource is
 *    missing; in that case the second draw is skipped gracefully.
 */
public class PerspectiveClipGM : GM() {

    override fun getName(): String = "perspective_clip"

    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val path = makePath()

        val greyPaint = SkPaint().apply {
            color = SkColorSetARGB(0xFF, 0xBF, 0xBF, 0xBF) // {0.75, 0.75, 0.75, 1}
        }
        c.drawPath(path, greyPaint)

        // Crazy perspective matrix derived from halfplanes3 — part of the geometry
        // is "behind" the viewer, exercising half-plane clipping.
        val array = floatArrayOf(
            -1.7866f,  1.3357f, 273.0295f,
            -1.0820f,  1.3186f, 135.5196f,
            -0.0047f, -0.0015f,   2.1485f,
        )
        val mx = SkMatrix.MakeFrom9(array)

        val img = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
        if (img != null) {
            val localMatrix = SkMatrix.MakeScale(3f, 3f)
            val shader = img.makeShader(SkSamplingOptions(), localMatrix)
            val shaderPaint = SkPaint().apply { this.shader = shader }
            c.save()
            c.concat(mx)
            c.drawPath(path, shaderPaint)
            c.restore()
        }
    }

    /**
     * Mirrors `make_path()` in `gm/perspshaders.cpp`. Produces a deterministic
     * path by using [SkRandom] with the default seed (0) — same as upstream's
     * `SkRandom rand;` with no explicit seed.
     *
     * Four iterations, each contributing:
     *  - `moveTo(pts[0])`
     *  - `quadTo(pts[1], pts[2])`
     *  - `quadTo(pts[3], pts[4])`
     *  - `lineTo(pts[5])`
     *
     * Each coordinate is `nextF() * 400` (range [0, 400)).
     */
    private fun makePath(): SkPath {
        val rand = SkRandom(seed = 0)
        fun randPt(): SkPoint {
            val x = rand.nextF()
            val y = rand.nextF()
            return SkPoint(x * 400f, y * 400f)
        }

        val builder = SkPathBuilder()
        for (i in 0 until 4) {
            val pts = Array(6) { randPt() }
            builder.moveTo(pts[0].fX, pts[0].fY)
                .quadTo(pts[1].fX, pts[1].fY, pts[2].fX, pts[2].fY)
                .quadTo(pts[3].fX, pts[3].fY, pts[4].fX, pts[4].fY)
                .lineTo(pts[5].fX, pts[5].fY)
        }
        return builder.detach()
    }
}
