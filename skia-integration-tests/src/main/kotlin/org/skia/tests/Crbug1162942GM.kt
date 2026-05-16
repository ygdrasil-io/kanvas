package org.skia.tests

import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1162942.cpp` (`DEF_SIMPLE_GM(crbug_1162942, canvas, 620, 200)`).
 *
 * Repro for `crbug.com/1162942`: when the 2D projection of a perspective
 * quad is inset, the 2D geometry can degenerate to a triangle because an
 * inset vertex crosses the opposite edge. Upstream's fix forces both
 * edges meeting at a "replaced" vertex to AA whenever either was AA, so
 * that solving for the 3D point that projects onto the 2D point has
 * enough degrees of freedom. This is GPU-side reasoning — on raster the
 * code path is the all-or-nothing AA shortcut in
 * [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad].
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_1162942, canvas, 620, 200) {
 *     SkMatrix ctm = SkMatrix::MakeAll(SkBits2Float(0x3FCC7F75), ...);
 *     ctm.postTranslate(-1500.f, -325.f);
 *     SkPoint pts[4] = {{...}, {...}, {...}, {...}};
 *     const auto bounds = SkRect::BoundsOrEmpty(pts);
 *     canvas->clear(SK_ColorWHITE);
 *     SkCanvas::QuadAAFlags flags[] = {
 *             (SkCanvas::QuadAAFlags) (kTop_QuadAAFlag    | kLeft_QuadAAFlag ),
 *             (SkCanvas::QuadAAFlags) (kBottom_QuadAAFlag | kRight_QuadAAFlag),
 *             (SkCanvas::QuadAAFlags) (kBottom_QuadAAFlag),
 *             (SkCanvas::QuadAAFlags) (kRight_QuadAAFlag),
 *             (SkCanvas::QuadAAFlags) (kRight_QuadAAFlag  | kLeft_QuadAAFlag),
 *             (SkCanvas::QuadAAFlags) (kTop_QuadAAFlag    | kBottom_QuadAAFlag),
 *     };
 *     SkColor color = SK_ColorGREEN;
 *     for (auto aaFlags : flags) {
 *         canvas->save();
 *         canvas->concat(ctm);
 *         canvas->experimental_DrawEdgeAAQuad(bounds, pts, aaFlags, color, SkBlendMode::kSrcOver);
 *         color = nibble_rotate(color);
 *         canvas->restore();
 *         canvas->translate(0, 25);
 *     }
 * }
 * ```
 *
 * Coordinates are bit-exact (`Float.fromBits` for parity with upstream's
 * `SkBits2Float`).
 */
public class Crbug1162942GM : GM() {
    override fun getName(): String = "crbug_1162942"
    override fun getISize(): SkISize = SkISize.Make(620, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val ctm = SkMatrix.MakeAll(
            Float.fromBits(0x3FCC7F75),
            Float.fromBits(0x3D5784FC),
            Float.fromBits(0x44C48C99),
            Float.fromBits(0x3F699F7F),
            Float.fromBits(0x3E0A0D37),
            Float.fromBits(0x43908518),
            Float.fromBits(0x3AA17423),
            Float.fromBits(0x3A6CCDC3),
            Float.fromBits(0x3F2EFEEC),
        )
        ctm.postTranslate(-1500f, -325f)

        val pts = arrayOf(
            SkPoint(Float.fromBits(0x3F39778B), Float.fromBits(0x43FF7FFC)),
            SkPoint(Float.fromBits(0x00000000), Float.fromBits(0x43FF7FFA)),
            SkPoint(Float.fromBits(0xB83B055E.toInt()), Float.fromBits(0x42500003)),
            SkPoint(Float.fromBits(0x3F39776F), Float.fromBits(0x4250000D)),
        )
        val bounds = SkRect.Bounds(pts) ?: SkRect.MakeEmpty()

        c.clear(SK_ColorWHITE)

        val flags = intArrayOf(
            QuadAAFlags.kTop_QuadAAFlag or QuadAAFlags.kLeft_QuadAAFlag,
            QuadAAFlags.kBottom_QuadAAFlag or QuadAAFlags.kRight_QuadAAFlag,
            QuadAAFlags.kBottom_QuadAAFlag,
            QuadAAFlags.kRight_QuadAAFlag,
            QuadAAFlags.kRight_QuadAAFlag or QuadAAFlags.kLeft_QuadAAFlag,
            QuadAAFlags.kTop_QuadAAFlag or QuadAAFlags.kBottom_QuadAAFlag,
        )

        var color = SK_ColorGREEN
        for (aaFlags in flags) {
            c.save()
            c.concat(ctm)
            c.experimental_DrawEdgeAAQuad(bounds, pts, aaFlags, color, SkBlendMode.kSrcOver)
            val rgb = color and 0x00FFFFFF
            color = 0xFF000000.toInt() or (rgb shl 4) or (rgb ushr 20)
            c.restore()
            c.translate(0f, 25f)
        }
    }
}
