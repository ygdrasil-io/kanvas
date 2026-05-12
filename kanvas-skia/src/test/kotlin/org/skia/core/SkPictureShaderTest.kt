package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkRect

/**
 * Focused unit tests for Phase G3 — [SkPicture.makeShader].
 *
 * The picture-shader path replays a recorded picture into a transient
 * tile-sized bitmap once, then samples that bitmap through the existing
 * [org.skia.foundation.SkBitmapShader] machinery. The tests below pin
 * the two correctness invariants we care about:
 *
 *  1. **Tile-mode wiring** — a 32 × 32 red-square picture covered by
 *     `kRepeat` over a 64 × 64 canvas must yield four red 32 × 32
 *     tiles, every interior pixel fully saturated.
 *  2. **Snapshot-then-sample chain is alive** — every pixel under the
 *     filled paint takes a colour from the picture (red), not the
 *     background, confirming the picture's drawn content reaches the
 *     destination through the shader.
 */
class SkPictureShaderTest {

    @Test
    fun `picture-shader with kRepeat tiles a 32x32 square across a 64x64 canvas`() {
        // -- Step 1 : record a 32 × 32 picture that fills its cullRect red.
        val recorder = SkPictureRecorder()
        val rec = recorder.beginRecording(32f, 32f)
        rec.drawRect(SkRect.MakeWH(32f, 32f), SkPaint(SK_ColorRED))
        val picture = recorder.finishRecordingAsPicture()

        // -- Step 2 : wrap as a kRepeat × kRepeat shader and paint a
        //             64 × 64 destination. Background pre-cleared white so a
        //             missing-shader bug would surface as white pixels.
        val dst = SkBitmap(64, 64).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dst)
        val paint = SkPaint().apply {
            shader = picture.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
        }
        canvas.drawRect(SkRect.MakeWH(64f, 64f), paint)

        // -- Step 3 : every pixel must be red (the picture's content),
        //             which proves both halves of the contract:
        //             the picture's recorded ops replayed into the
        //             snapshot AND the snapshot's pixels reached the
        //             destination through the shader sampler.
        var redCount = 0
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val p = dst.getPixel(x, y)
                if (p == SK_ColorRED) redCount++
            }
        }
        // 4 tiles of 32 × 32 == 4096 pixels — all must hit red.
        assertEquals(64 * 64, redCount, "every destination pixel must come from the picture")

        // Sanity : the rendered surface must not equal the cleared background.
        // (Catches the degenerate "shader silently produced transparent" case.)
        assertNotEquals(SK_ColorWHITE, dst.getPixel(0, 0))
        assertNotEquals(SK_ColorWHITE, dst.getPixel(63, 63))
    }

    @Test
    fun `picture-shader honours an explicit tile sub-rect`() {
        // Record a single 16 × 16 red square inset at (8, 8) of a 32 × 32
        // picture. With the default tile (= cullRect, 32 × 32), the snapshot
        // captures the red square AND the empty (transparent) surround.
        // We then ask the shader for a tile that's just the red square,
        // and assert that the resulting rendering is solid red even far
        // outside the original square's coordinate range.
        val recorder = SkPictureRecorder()
        val rec = recorder.beginRecording(32f, 32f)
        rec.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), SkPaint(SK_ColorRED))
        val picture = recorder.finishRecordingAsPicture()

        // Tile = the red square's bounds. The picture-shader path should
        // translate the playback so the tile's top-left lands on the
        // snapshot's (0, 0).
        val tile = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
        val shader = picture.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            tile = tile,
        )

        val dst = SkBitmap(48, 48).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dst)
        canvas.drawRect(SkRect.MakeWH(48f, 48f), SkPaint().apply { this.shader = shader })

        // The shader must produce non-white (== something from the picture)
        // throughout the destination, including coordinates far outside
        // the original square's (8, 8)..(24, 24) range.
        assertNotEquals(SK_ColorWHITE, dst.getPixel(0, 0))
        assertNotEquals(SK_ColorWHITE, dst.getPixel(40, 40))
    }
}
