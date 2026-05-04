package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/scaledrects.cpp` (`ScaledRectsGM`).
 *
 * Stresses three things at once: full 3x3 matrix application via
 * `SkMatrix::MakeAll(...)` (now landed in Phase 4b), per-paint blend
 * mode dispatch (`kPlus` — Phase 6 entry), and clip-rect intersection
 * with rotated/skewed device-space draws.
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("scaledrects"); }
 * SkISize getISize() override { return SkISize::Make(128, 64); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->clipRect(SkRect::MakeXYWH(10, 50, 100, 10));
 *
 *     {
 *         SkPaint blue;
 *         blue.setColor(SK_ColorBLUE);
 *         canvas->setMatrix(SkMatrix::MakeAll( 3.0f, -0.5f, 0.0f,
 *                                             -0.5f, -3.0f, 0.0f,
 *                                              0.0f,  0.0f, 1.0f));
 *         canvas->drawRect(SkRect::MakeXYWH(-1000, -1000, 2000, 2000), blue);
 *     }
 *     {
 *         SkPaint red;
 *         red.setColor(SK_ColorRED);
 *         red.setBlendMode(SkBlendMode::kPlus);
 *         canvas->setMatrix(SkMatrix::MakeAll(3000.0f,  -500.0f, 0.0f,
 *                                             -500.0f, -3000.0f, 0.0f,
 *                                                0.0f,     0.0f, 1.0f));
 *         canvas->drawRect(SkRect::MakeXYWH(-1, -1, 2, 2), red);
 *     }
 * }
 * ```
 *
 * The blue rect covers the entire canvas after `setMatrix` (it spans
 * 2000x2000 source units, mapped through `(3, -0.5; -0.5, -3)`), filling
 * the clipped row `(10, 50)–(110, 60)` with solid blue. The red rect is
 * a 2x2 source-space square centered at the origin, scaled by `(3000,
 * -3000)` with a small skew — it lights up a magenta band where it
 * overlaps the blue (kPlus saturates red+blue → magenta).
 *
 * Reference image: `scaledrects.png`, 128×64, BG `0xFFCCCCCC`.
 */
public class ScaledRectsGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    override fun getName(): String = "scaledrects"
    override fun getISize(): SkISize = SkISize.Make(128, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Skia's `setBGColor(0xFFCCCCCC)` is a non-trivial colour, but our
        // harness `eraseColor` writes raw bytes and skips the sRGB → working-
        // space transform. Route through `drawPaint` so the BG goes through
        // the device's colour-space pipeline (same workaround used by
        // PathInteriorGM / ArcOfZorroGM / ClampedGradientsGM).
        c.drawPaint(SkPaint().apply { color = 0xFFCCCCCC.toInt() })

        c.clipRect(SkRect.MakeXYWH(10f, 50f, 100f, 10f))

        run {
            val blue = SkPaint().apply { color = SK_ColorBLUE }
            c.setMatrix(SkMatrix.MakeAll(
                 3.0f, -0.5f, 0.0f,
                -0.5f, -3.0f, 0.0f,
            ))
            c.drawRect(SkRect.MakeXYWH(-1000f, -1000f, 2000f, 2000f), blue)
        }

        run {
            val red = SkPaint().apply {
                color = SK_ColorRED
                blendMode = SkBlendMode.kPlus
            }
            c.setMatrix(SkMatrix.MakeAll(
                 3000.0f,  -500.0f, 0.0f,
                 -500.0f, -3000.0f, 0.0f,
            ))
            c.drawRect(SkRect.MakeXYWH(-1f, -1f, 2f, 2f), red)
        }
    }
}
