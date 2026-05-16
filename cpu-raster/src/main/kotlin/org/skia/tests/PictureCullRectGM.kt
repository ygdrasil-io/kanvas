package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/picture.cpp::PictureCullRectGM` (`picture_cull_rect`,
 * 120 × 120).
 *
 * Records a picture whose bounds top-left starts at `y = 80` (rect +
 * oval at `(0, 80, 100, 100)`), then replays under a `clipRect(0, 60,
 * 120, 120)` and a `translate(10, 10)`.
 *
 * Originally a regression for `skbug.com/40040654` — DM's
 * `--config serialize-8888` lost the cull-rect's non-zero top-left
 * corner during serialize-deserialize round-trip.
 */
public class PictureCullRectGM : GM() {

    override fun getName(): String = "picture_cull_rect"
    override fun getISize(): SkISize = SkISize.Make(120, 120)

    private val picture: SkPicture by lazy { makePicture() }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clipRect(SkRect.MakeLTRB(0f, 60f, 120f, 120f))
        c.translate(10f, 10f)
        picture.playback(c)
    }

    private fun makePicture(): SkPicture {
        val rec = SkPictureRecorder()
        // Upstream uses `SkRTreeFactory` for BBH ; we don't expose that
        // path optimisation, but the playback is identical.
        val recCanvas = rec.beginRecording(100f, 100f)

        val paint = SkPaint().apply {
            isAntiAlias = false
            color = 0x800000FF.toInt()
        }
        val rect = SkRect.MakeLTRB(0f, 80f, 100f, 100f)
        // Two ops so the picture is "complex enough" to trigger upstream's
        // cull-rect / BBH paths (irrelevant for our straightforward
        // playback but kept for fidelity).
        recCanvas.drawRect(rect, paint)
        recCanvas.drawOval(rect, paint)

        return rec.finishRecordingAsPicture()
    }
}
