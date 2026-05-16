package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of Skia's `gm/bug6643.cpp::bug6643`.
 *
 * **Exercises [org.skia.core.SkPicture.makeShader]** — the Phase G3
 * primitive. A tiny picture (one `drawPaint` with a sweep gradient
 * around the canvas centre) is recorded once, then re-used as a
 * tiled shader to fill the destination canvas. The reference is
 * dominated by the gradient itself (the picture-shader path is just
 * a one-tile cover at the GM's chosen 200 × 200 size, so the
 * `kRepeat` tile mode is exercised only at the edge case).
 *
 * Upstream uses `Interpolation::InPremul::kYes` to interpolate the
 * `transparent → green → transparent` stop sequence in premul space
 * — this avoids the classic "fade through black" artifact that
 * non-premul interpolation would produce at the transparent stop.
 * Our [SkSweepGradient] already premultiplies stops during the
 * F16 setup path, so the premul-interpolation semantics fall out
 * naturally without an explicit toggle.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(bug6643, canvas, 200, 200) {
 *     const SkColor4f colors[] = {
 *         SkColors::kTransparent, SkColors::kGreen, SkColors::kTransparent
 *     };
 *     SkPaint p;
 *     p.setAntiAlias(true);
 *     p.setShader(SkShaders::SweepGradient({100, 100},
 *                                          {{colors, {}, SkTileMode::kClamp},
 *                                           {SkGradient::Interpolation::InPremul::kYes}}));
 *     SkPictureRecorder recorder;
 *     recorder.beginRecording(200, 200)->drawPaint(p);
 *     p.setShader(recorder.finishRecordingAsPicture()->makeShader(
 *         SkTileMode::kRepeat, SkTileMode::kRepeat,
 *         SkFilterMode::kNearest, nullptr, nullptr));
 *     canvas->drawColor(SK_ColorWHITE);
 *     canvas->drawPaint(p);
 * }
 * ```
 */
public class Bug6643GM : GM() {
    override fun getName(): String = "bug6643"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Sweep gradient: transparent → green → transparent around (100, 100).
        val gradient = SkSweepGradient.Make(
            center = SkPoint(100f, 100f),
            colors = intArrayOf(SK_ColorTRANSPARENT, SK_ColorGREEN, SK_ColorTRANSPARENT),
            positions = null, // evenly spaced — matches upstream's default
            tileMode = SkTileMode.kClamp,
        )

        // Step 1 — record a single full-cover drawPaint into a 200 × 200 picture.
        val recorder = SkPictureRecorder()
        val recCanvas = recorder.beginRecording(200f, 200f)
        recCanvas.drawPaint(SkPaint().apply {
            isAntiAlias = true
            shader = gradient
        })
        val picture = recorder.finishRecordingAsPicture()

        // Step 2 — promote the picture into a tiled shader and paint the canvas.
        val pictureShader = picture.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            filter = SkFilterMode.kNearest,
            localMatrix = null,
            tile = null, // defaults to the picture's cullRect (200 × 200)
        )

        c.drawColor(SK_ColorWHITE)
        c.drawPaint(SkPaint().apply {
            isAntiAlias = true
            shader = pictureShader
        })
    }
}
