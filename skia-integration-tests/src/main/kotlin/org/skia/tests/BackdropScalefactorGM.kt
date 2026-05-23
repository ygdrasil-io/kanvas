package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/backdrop.cpp::DEF_SIMPLE_GM(backdrop_scalefactor, 768, 1024)`.
 *
 * 3x4 grid : same row pattern as [BackdropHintrectClippingGM], but the
 * three columns vary the intermediate scale factor passed to
 * `ScaledBackdropLayer` : `1.0`, `0.25`, `0.1`. kanvas-skia's public
 * API exposes only the non-scaled [SaveLayerRec], so all three columns
 * render with `scaleFactor == 1` here — the scale factor is a
 * Ganesh-internal quality knob (downsample → blur → upsample) with no
 * surface in the public CPU raster API.
 *
 * Shared draw logic lives in `BackdropHintrectClippingGM.kt`
 * ([backdropDoDraw] / [backdropMakeShader]).
 */
public class BackdropScalefactorGM : GM() {

    override fun getName(): String = "backdrop_scalefactor"
    override fun getISize(): SkISize = SkISize.Make(768, 1024)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        for (useHintRect in booleanArrayOf(false, true)) {
            for (useClip in booleanArrayOf(false, true)) {
                c.save()
                backdropDoDraw(c, useClip, useHintRect, 1.0f)
                c.translate(256f, 0f)
                backdropDoDraw(c, useClip, useHintRect, 0.25f)
                c.translate(256f, 0f)
                backdropDoDraw(c, useClip, useHintRect, 0.1f)
                c.restore()

                c.translate(0f, 256f)
            }
        }
    }
}
