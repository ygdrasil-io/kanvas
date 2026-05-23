package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.utils.SkParsePath

/**
 * Port of upstream Skia's
 * [`gm/patharcto.cpp`](https://github.com/google/skia/blob/main/gm/patharcto.cpp)
 * `DEF_SIMPLE_GM(arcto_skbug_9272, …, 150, 150)`.
 *
 * Regression for skbug.com/9272 — two SVG path strings parsed via
 * [SkParsePath.FromSVGString] that together form a closed-looking stroke.
 * The first path contains a very large-radius arc
 * (`r=1647300864`) that, before the fix, produced incorrect geometry.
 * Both paths are stroked with a black hairline.
 *
 * Reference image: `arcto_skbug_9272.png`, 150 × 150, white background.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(arcto_skbug_9272, canvas, 150, 150) {
 *     const char* str = "M66.652,65.509c0.663,-2 -0.166,-4.117 -2.117,-5.212 …";
 *     SkPath path = SkParsePath::FromSVGString(str).value_or(SkPath());
 *
 *     const char* str2 = "M10.156,30.995l4.881,2.63 …";
 *     SkPath path2 = SkParsePath::FromSVGString(str2).value_or(SkPath());
 *
 *     SkPaint paint;
 *     paint.setStyle(SkPaint::kStroke_Style);
 *     canvas->translate(30, 30);
 *     canvas->drawPath(path, paint);
 *     canvas->drawPath(path2, paint);
 * }
 * ```
 */
public class ArctoSkbug9272GM : GM() {

    override fun getName(): String = "arcto_skbug_9272"

    override fun getISize(): SkISize = SkISize.Make(150, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val str = "M66.652,65.509c0.663,-2 -0.166,-4.117 -2.117,-5.212 -0.673,-0.378 -1.36,-0.733 -2.04,-1.1a1647300864,1647300864 0,0 1,-31.287 -16.86c-5.39,-2.903 -10.78,-5.808 -16.171,-8.713 -1.626,-0.876 -3.253,-1.752 -4.88,-2.63 -1.224,-0.659 -2.4,-1.413 -3.851,-1.413 -1.135,0 -2.242,0.425 -3.049,1.197 0.08,-0.083 0.164,-0.164 0.248,-0.246l5.309,-5.13 9.37,-9.054 9.525,-9.204 5.903,-5.704C34.237,0.836 34.847,0.297 35.75,0.13c0.982,-0.182 1.862,0.127 2.703,0.592l6.23,3.452L55.76,10.31l11.951,6.62 9.02,4.996c1.74,0.963 4.168,1.854 4.205,4.21 0.011,0.678 -0.246,1.28 -0.474,1.9l-1.005,2.733 -5.665,15.42 -7.106,19.338 -0.034,-0.018z"
        val path = SkParsePath.FromSVGString(str) ?: return

        val str2 = "M10.156,30.995l4.881,2.63 16.17,8.713a1647300736,1647300736 0,0 0,31.287 16.86c0.68,0.366 1.368,0.721 2.041,1.1 2.242,1.257 3.002,3.864 1.72,6.094 -0.659,1.147 -1.296,2.31 -1.978,3.442 -1.276,2.117 -3.973,2.632 -6.102,1.536 -0.244,-0.125 -0.485,-0.259 -0.727,-0.388l-4.102,-2.19 -15.401,-8.225 -18.536,-9.9 -13.893,-7.419c-0.939,-0.501 -1.88,-0.998 -2.816,-1.504C1.2,40.935 0.087,39.5 0.004,37.75c-0.08,-1.672 1.078,-3.277 1.826,-4.702 0.248,-0.471 0.479,-0.958 0.75,-1.416 0.772,-1.306 2.224,-2.05 3.726,-2.05 1.45,0 2.627,0.754 3.85,1.414z"
        val path2 = SkParsePath.FromSVGString(str2) ?: return

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
        }

        c.translate(30f, 30f)
        c.drawPath(path, paint)
        c.drawPath(path2, paint)
    }
}
