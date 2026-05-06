package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/distantclip.cpp::DistantClipGM` (100 × 100).
 *
 * Probe for clip-path culling at extreme vertical offsets : record a
 * picture spanning a 35000-tall canvas with a `clipPath(rrect)` at the
 * bottom that lets green draws through a 1000×1000 region. Replay
 * that picture into another picture (same size) so the
 * record→play→record→play chain doesn't lose the green draw. Finally
 * play the second picture with `translate(kExtents/2, -(kOffset −
 * kExtents/2))` to bring the green region into the visible 100×100
 * window.
 *
 * Bug : if the rasteriser erroneously decides the clipPath is empty
 * (the bbox is far below the visible area), it skips the
 * `drawColor(GREEN)`, leaving the canvas red.
 */
public class DistantClipGM : GM() {

    override fun getName(): String = "distantclip"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kOffset = 35000f
        val kExtents = 1000f

        // First picture : red bg, then a clipPath(rrect) at the bottom
        // that fills the inside green.
        val recorder = SkPictureRecorder()
        val rec = recorder.beginRecording(kExtents, kOffset + kExtents)
        rec.drawColor(SK_ColorRED)
        rec.save()
        val r = SkRect.MakeXYWH(-kExtents, kOffset - kExtents, 2 * kExtents, 2 * kExtents)
        rec.clipPath(SkPath.RRect(SkRRect.MakeRectXY(r, 5f, 5f)), doAntiAlias = true)
        rec.drawColor(SK_ColorGREEN)
        rec.restore()
        val pict = recorder.finishRecordingAsPicture()

        // Replay into a second picture of the same dimensions.
        pict.playback(recorder.beginRecording(pict.cullRect.width(), pict.cullRect.height()))
        val pict2 = recorder.finishRecordingAsPicture()

        // Play the green region into the visible window.
        c.save()
        c.translate(kExtents / 2, -(kOffset - kExtents / 2))
        pict2.playback(c)
        c.restore()
    }
}
