package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTypeface

/**
 * Phase I1 — Picture recording integration test for `drawTextBlob`.
 * Records a 1-run blob into a [SkPicture], plays it back, and
 * verifies pixel-equality with a direct `drawTextBlob` call.
 */
class SkPictureTextBlobTest {

    @Test
    fun `picture playback of drawTextBlob is pixel-identical to direct draw`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, count = 3, x = 10f, y = 30f)
        rec.glyphs[0] = 65; rec.glyphs[1] = 66; rec.glyphs[2] = 67  // ABC
        val blob = builder.make()!!

        val paint = SkPaint().apply { color = SK_ColorBLACK }

        // Direct draw.
        val direct = SkBitmap(60, 40, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
            .also { it.eraseColor(0xFFFFFFFF.toInt()) }
        SkCanvas(direct).drawTextBlob(blob, 0f, 0f, paint)

        // Recorded + replayed.
        val recorder = SkPictureRecorder()
        val recCanvas = recorder.beginRecording(60f, 40f)
        recCanvas.drawTextBlob(blob, 0f, 0f, paint)
        val picture = recorder.finishRecordingAsPicture()

        val replay = SkBitmap(60, 40, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
            .also { it.eraseColor(0xFFFFFFFF.toInt()) }
        picture.playback(SkCanvas(replay))

        // Pixel-identical comparison.
        assertEquals(direct.pixels8888.size, replay.pixels8888.size)
        for (i in direct.pixels8888.indices) {
            assertEquals(direct.pixels8888[i], replay.pixels8888[i]) {
                "pixel $i differs : direct=${"0x%08X".format(direct.pixels8888[i])} " +
                    "replay=${"0x%08X".format(replay.pixels8888[i])}"
            }
        }
    }
}
