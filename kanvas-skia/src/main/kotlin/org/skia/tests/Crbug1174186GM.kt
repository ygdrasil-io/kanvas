package org.skia.tests

import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SkBlendMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1174186.cpp` (`DEF_SIMPLE_GM(crbug_1174186, canvas, 1200, 1200)`).
 *
 * Repro for `crbug.com/1174186`: outsetting of very-thin, nearly-line
 * quads for AA would diverge wildly and paint outside the quad's
 * footprint. Upstream's fix is to drop AA in this case, which makes
 * these quads practically invisible (pixel centres rarely fall inside
 * such a thin shape). Our CPU port goes through
 * `experimental_DrawEdgeAAQuad` → `drawPath(SkPath.Polygon)` so the
 * "almost-invisible quad" outcome falls out naturally — the polygon's
 * AA fill rasterises at most one or two sub-pixels per draw, mostly a
 * no-op against the white BG.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_1174186, canvas, 1200, 1200) {
 *     auto m = SkMatrix::MakeAll(SkBits2Float(0x24480629), ...);
 *     SkPoint pts[] = {{...}, {...}, {...}, {...}};
 *     SkColor color = SK_ColorGREEN;
 *     canvas->translate(-500, 0);
 *     for (int i = 0; i < 10; ++i) {
 *         for (int flags = 0; flags < kAll_QuadAAFlags; ++flags) {
 *             canvas->save();
 *             canvas->concat(m);
 *             canvas->experimental_DrawEdgeAAQuad(SkRect::MakeWH(1000, 1000),
 *                 pts, (QuadAAFlags)flags, color, SkBlendMode::kSrcOver);
 *             canvas->restore();
 *             canvas->translate(5.1f, 0);
 *             color = nibble_rotate(color);
 *         }
 *     }
 * }
 * ```
 *
 * Coordinates are bit-exact (`Float.fromBits` for parity with upstream's
 * `SkBits2Float`).
 */
public class Crbug1174186GM : GM() {
    override fun getName(): String = "crbug_1174186"
    override fun getISize(): SkISize = SkISize.Make(1200, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val m = SkMatrix.MakeAll(
            Float.fromBits(0x24480629),
            Float.fromBits(0xbf3555c2.toInt()),
            Float.fromBits(0x4377d67b),
            Float.fromBits(0x23a61d51),
            Float.fromBits(0x3f34b400),
            Float.fromBits(0x4453f572),
            Float.fromBits(0x00000000),
            Float.fromBits(0x00000000),
            Float.fromBits(0x3f800000),
        )
        val pts = arrayOf(
            SkPoint(Float.fromBits(0x3f7ffff2), Float.fromBits(0x43483d60)),
            SkPoint(Float.fromBits(0x00000000), Float.fromBits(0x43483d60)),
            SkPoint(Float.fromBits(0x00000000), Float.fromBits(0x4311a628)),
            SkPoint(Float.fromBits(0x3f800000), Float.fromBits(0x43130f8c)),
        )
        val rect = SkRect.MakeWH(1000f, 1000f)

        var color = SK_ColorGREEN
        c.translate(-500f, 0f)
        for (i in 0 until 10) {
            for (flags in 0 until QuadAAFlags.kAll_QuadAAFlags) {
                c.save()
                c.concat(m)
                c.experimental_DrawEdgeAAQuad(rect, pts, flags, color, SkBlendMode.kSrcOver)
                c.restore()
                c.translate(5.1f, 0f)
                val rgb = color and 0x00FFFFFF
                color = 0xFF000000.toInt() or (rgb shl 4) or (rgb ushr 20)
            }
        }
    }
}
