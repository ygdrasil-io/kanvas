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
 * Port of Skia's `gm/crbug_1167277.cpp` (`DEF_SIMPLE_GM(crbug_1167277, canvas, 230, 320)`).
 *
 * Repro for `crbug.com/1167277`: a degenerate-inset quad that would
 * produce a triangle whose third point lands far outside the original
 * quad. The CPU raster doesn't exercise that GPU-side AA inset path —
 * upstream's `SkDevice::drawEdgeAAQuad` shortcuts to non-AA whenever
 * any edge is non-AA — so this GM mainly stresses the
 * `experimental_DrawEdgeAAQuad` plumbing through a perspective CTM and a
 * non-trivial 4-point clip.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_1167277, canvas, 230, 320) {
 *     canvas->translate(-1250, -900);
 *     SkMatrix ctm = SkMatrix::MakeAll(SkBits2Float(0xbf8fcfae), ...);
 *     SkRect rect = {0, 0, 17, 196};
 *     SkPoint clip[4] = { ... };
 *     SkColor color = SK_ColorGREEN;
 *     for (int flags = 0; flags < kAll_QuadAAFlags; ++flags) {
 *         canvas->save();
 *         canvas->concat(ctm);
 *         canvas->experimental_DrawEdgeAAQuad(rect, clip,
 *             (SkCanvas::QuadAAFlags)flags, color, SkBlendMode::kSrcOver);
 *         canvas->restore();
 *         canvas->translate(5, 0);
 *         color = nibble_rotate(color);
 *     }
 * }
 * ```
 *
 * Coordinates are bit-exact (`Float.fromBits` for parity with upstream's
 * `SkBits2Float`).
 */
public class Crbug1167277GM : GM() {
    override fun getName(): String = "crbug_1167277"
    override fun getISize(): SkISize = SkISize.Make(230, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.translate(-1250f, -900f)

        val ctm = SkMatrix.MakeAll(
            Float.fromBits(0xbf8fcfae.toInt()),
            Float.fromBits(0xbeae25ee.toInt()),
            Float.fromBits(0x449ca6db),
            Float.fromBits(0x3c9dc40f),
            Float.fromBits(0xbf950e35.toInt()),
            Float.fromBits(0x4487da43),
            Float.fromBits(0xb8d4d6bc.toInt()),
            Float.fromBits(0xb92fbb29.toInt()),
            Float.fromBits(0x3f6f605c),
        )
        val rect = SkRect.MakeLTRB(
            Float.fromBits(0x00000000),
            Float.fromBits(0x00000000),
            Float.fromBits(0x41880000),
            Float.fromBits(0x43440000),
        )
        val clip = arrayOf(
            SkPoint(Float.fromBits(0x3ef434a2), Float.fromBits(0x43440004)),
            SkPoint(Float.fromBits(0x00000000), Float.fromBits(0x43440009)),
            SkPoint(Float.fromBits(0x38ef605d), Float.fromBits(0x38ef605d)),
            SkPoint(Float.fromBits(0x3ef436e3), Float.fromBits(0x396f5d30)),
        )

        var color = SK_ColorGREEN
        // Skia source iterates `flags < kAll_QuadAAFlags` (= 15) — i.e. 0..14
        // inclusive (15 combinations, one per non-full mask).
        for (flags in 0 until QuadAAFlags.kAll_QuadAAFlags) {
            c.save()
            c.concat(ctm)
            c.experimental_DrawEdgeAAQuad(rect, clip, flags, color, SkBlendMode.kSrcOver)
            c.restore()
            c.translate(5f, 0f)
            // Skia's per-iteration colour rotation: ARGB → A | ((rgb << 4) | (rgb >> 20)).
            val rgb = color and 0x00FFFFFF
            color = 0xFF000000.toInt() or (rgb shl 4) or (rgb ushr 20)
        }
    }
}
