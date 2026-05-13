package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of upstream Skia's
 * [`gm/patharcto.cpp`](https://github.com/google/skia/blob/main/gm/patharcto.cpp)
 * `DEF_SIMPLE_GM(shallow_angle_path_arcto, …, 300, 300)`.
 *
 * Repro for crbug.com/982968 — a curvy triangle whose corners are extremely
 * shallow-angle tangent arcs. The bug was that the original
 * `SkPath::arcTo(p1, p2, radius)` did its trig in 32-bit floats, which
 * dropped enough precision on these huge radii (up to ~700 000 px) that the
 * arc collapsed into a near-straight "flag pole". The fix promoted the
 * inner math to doubles ; `:kanvas-skia`'s `SkPathBuilder.arcTo(p1, p2,
 * radius)` already keeps the chord / cosh / sinh / d calculations in
 * `Double`, so this GM serves as a regression pin.
 *
 * Reference : `shallow_angle_path_arcto.png`, 300 × 300, white background.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(shallow_angle_path_arcto, canvas, 300, 300) {
 *     SkPathBuilder path;
 *     SkPaint paint;
 *     paint.setStyle(SkPaint::kStroke_Style);
 *
 *     path.moveTo(313.44189096331155f, 106.6009423589212f)
 *         .arcTo({284.3113082008462f, 207.1407719157063f},
 *                {255.15053777129728f, 307.6718505416374f},
 *                697212.0011054524f)
 *         .lineTo(255.15053777129728f, 307.6718505416374f)
 *         .arcTo({340.4737465981018f, 252.6907319346971f},
 *                {433.54333477716153f, 212.18116363345337f},
 *                1251.2484277907251f)
 *         .lineTo(433.54333477716153f, 212.18116363345337f)
 *         .arcTo({350.19513833839466f, 185.89280014838369f},
 *                {313.44189096331155f, 106.6009423589212f},
 *                198.03116885327813f);
 *
 *     canvas->translate(-200, -50);
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class ShallowAnglePathArcToGM : GM() {

    override fun getName(): String = "shallow_angle_path_arcto"

    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
        }

        val path = SkPathBuilder()
            .moveTo(313.44189096331155f, 106.6009423589212f)
            .arcTo(
                284.3113082008462f, 207.1407719157063f,
                255.15053777129728f, 307.6718505416374f,
                697212.0011054524f,
            )
            .lineTo(255.15053777129728f, 307.6718505416374f)
            .arcTo(
                340.4737465981018f, 252.6907319346971f,
                433.54333477716153f, 212.18116363345337f,
                1251.2484277907251f,
            )
            .lineTo(433.54333477716153f, 212.18116363345337f)
            .arcTo(
                350.19513833839466f, 185.89280014838369f,
                313.44189096331155f, 106.6009423589212f,
                198.03116885327813f,
            )
            .detach()

        c.translate(-200f, -50f)
        c.drawPath(path, paint)
    }
}
