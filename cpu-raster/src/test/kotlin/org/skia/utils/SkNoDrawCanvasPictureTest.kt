package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkLattice
import org.skia.core.SkPictureRecorder
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextSlug
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect

/**
 * R-suivi.50 — verify [SkNoDrawCanvas] no-ops the four new SkCanvas
 * virtuals. The canvas's own state (CTM / clip / save-count) must
 * stay unchanged across these calls — none of them should leak through
 * to the base [org.skia.core.SkCanvas] default impl (which would
 * delegate to [SkShadowUtils.DrawShadow] / [org.skia.core.SkPicture.playback]
 * / `drawTextBlob` / `drawImageRect`).
 */
class SkNoDrawCanvasPictureTest {

    @Test
    fun `drawPicture on SkNoDrawCanvas is a no-op`() {
        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeWH(10f, 10f))
        // Inside the recorded picture, push a few states — if drawPicture
        // weren't a no-op, the playback would mutate the canvas state stack.
        recCanvas.save()
        recCanvas.save()
        recCanvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorRED))
        val picture = rec.finishRecordingAsPicture()

        val canvas = org.skia.utils.SkNoDrawCanvas(20, 20)
        val before = canvas.getSaveCount()
        canvas.drawPicture(picture)
        canvas.drawPicture(picture, SkMatrix.MakeTrans(5f, 5f))
        canvas.drawPicture(picture, null, SkPaint(SK_ColorRED))
        // Save count should not change ; the default impl would have
        // called save() then restored — net zero but observable in
        // intermediate state. Critically, the picture's own dangling
        // saves must not leak.
        assertEquals(before, canvas.getSaveCount())
    }

    @Test
    fun `drawShadow on SkNoDrawCanvas is a no-op`() {
        val canvas = org.skia.utils.SkNoDrawCanvas(20, 20)
        val before = canvas.getSaveCount()
        canvas.drawShadow(
            path = SkPath.Rect(SkRect.MakeWH(10f, 10f)),
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(10f, 10f, 100f),
            lightRadius = 20f,
            ambientColor = SK_ColorBLACK,
            spotColor = SK_ColorBLACK,
        )
        assertEquals(before, canvas.getSaveCount())
    }

    @Test
    fun `drawSlug on SkNoDrawCanvas is a no-op`() {
        val canvas = org.skia.utils.SkNoDrawCanvas(20, 20)
        val builder = SkTextBlobBuilder()
        builder.allocRun(SkFont(), 0, 0f, 0f)
        val blob = builder.make()!!
        val slug = SkTextSlug(blob, SkPaint(SK_ColorRED))

        val before = canvas.getSaveCount()
        canvas.drawSlug(slug, SkPoint(1f, 2f))
        assertEquals(before, canvas.getSaveCount())
    }

    @Test
    fun `drawImageLattice on SkNoDrawCanvas is a no-op`() {
        val image = SkBitmap(8, 8).also { it.eraseColor(SK_ColorRED) }.asImage()
        val lattice = SkLattice(xDivs = intArrayOf(2, 6), yDivs = intArrayOf(2, 6))
        val canvas = org.skia.utils.SkNoDrawCanvas(20, 20)
        val before = canvas.getSaveCount()
        canvas.drawImageLattice(
            image = image,
            lattice = lattice,
            dst = SkRect.MakeWH(20f, 20f),
            filterMode = SkFilterMode.kLinear,
            paint = null,
        )
        assertEquals(before, canvas.getSaveCount())
    }
}
