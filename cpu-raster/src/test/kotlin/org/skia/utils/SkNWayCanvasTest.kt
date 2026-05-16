package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkFont
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Tests for [SkNWayCanvas] — verify that draw + state ops fan out
 * to every registered child canvas.
 */
class SkNWayCanvasTest {

    private class CountingCanvas : SkCanvas(SkBitmap(4, 4)) {
        var drawRectCount = 0
        var numSave = 0
        var restoreCount = 0
        var drawAtlasCount = 0
        var drawTextBlobCount = 0
        var drawDrawableCount = 0
        var drawAnnotationCount = 0
        override fun drawRect(rect: SkRect, paint: SkPaint) {
            drawRectCount++
        }
        override fun save(): Int {
            numSave++
            return super.save()
        }
        override fun restore() {
            restoreCount++
            super.restore()
        }
        override fun drawAtlas(
            image: SkImage,
            xform: Array<SkRSXform>,
            src: Array<SkRect>,
            colors: IntArray?,
            blendMode: SkBlendMode,
            sampling: SkSamplingOptions,
            cullRect: SkRect?,
            paint: SkPaint?,
        ) {
            drawAtlasCount++
        }
        override fun drawTextBlob(
            blob: org.skia.foundation.SkTextBlob,
            x: Float,
            y: Float,
            paint: SkPaint,
        ) {
            drawTextBlobCount++
        }
        override fun drawDrawable(drawable: SkDrawable, matrix: SkMatrix?) {
            drawDrawableCount++
        }
        override fun drawAnnotation(rect: SkRect, key: String, value: ByteArray?) {
            drawAnnotationCount++
        }
    }

    /** No-op drawable used to invoke `drawDrawable` without recursing. */
    private class NoopDrawable : SkDrawable() {
        override fun onDraw(canvas: SkCanvas) {}
        override fun onGetBounds(): SkRect = SkRect.MakeWH(1f, 1f)
    }

    @Test
    fun `drawRect fans out to every registered canvas`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        nway.drawRect(SkRect.MakeWH(5f, 5f), SkPaint(SK_ColorRED))
        assertEquals(1, a.drawRectCount)
        assertEquals(1, b.drawRectCount)
    }

    @Test
    fun `save and restore fan out`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        nway.addCanvas(a)
        nway.save()
        nway.restore()
        assertEquals(1, a.numSave)
        assertEquals(1, a.restoreCount)
    }

    @Test
    fun `removeCanvas stops forwarding`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        nway.addCanvas(a)
        nway.drawRect(SkRect.MakeWH(1f, 1f), SkPaint(SK_ColorBLACK))
        nway.removeCanvas(a)
        nway.drawRect(SkRect.MakeWH(1f, 1f), SkPaint(SK_ColorBLACK))
        assertEquals(1, a.drawRectCount)
    }

    @Test
    fun `removeAll clears all children`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        nway.removeAll()
        nway.drawRect(SkRect.MakeWH(1f, 1f), SkPaint(SK_ColorBLACK))
        assertEquals(0, a.drawRectCount)
        assertEquals(0, b.drawRectCount)
    }

    // ─── R-suivi.3 — deferred draw overrides now forwarded ──────────────────

    @Test
    fun `drawAtlas fans out to every registered canvas`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        val img = SkImage(4, 4, IntArray(16) { SK_ColorWHITE })
        nway.drawAtlas(
            image = img,
            xform = arrayOf(SkRSXform.Identity),
            src = arrayOf(SkRect.MakeWH(4f, 4f)),
            colors = null,
            blendMode = SkBlendMode.kSrcOver,
            sampling = SkSamplingOptions.Default,
            cullRect = null,
            paint = null,
        )
        assertEquals(1, a.drawAtlasCount)
        assertEquals(1, b.drawAtlasCount)
    }

    @Test
    fun `drawTextBlob fans out to every registered canvas`() {
        val nway = SkNWayCanvas(20, 20)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        val builder = SkTextBlobBuilder()
        builder.allocRun(SkFont(), count = 1, x = 0f, y = 12f)
        val blob = builder.make()!!
        nway.drawTextBlob(blob, 0f, 0f, SkPaint(SK_ColorBLACK))
        assertEquals(1, a.drawTextBlobCount)
        assertEquals(1, b.drawTextBlobCount)
    }

    @Test
    fun `drawDrawable fans out to every registered canvas`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        nway.drawDrawable(NoopDrawable(), null)
        assertEquals(1, a.drawDrawableCount)
        assertEquals(1, b.drawDrawableCount)
    }

    @Test
    fun `drawAnnotation fans out to every registered canvas`() {
        val nway = SkNWayCanvas(10, 10)
        val a = CountingCanvas()
        val b = CountingCanvas()
        nway.addCanvas(a)
        nway.addCanvas(b)
        nway.drawAnnotation(SkRect.MakeWH(5f, 5f), "url", "https://skia.org".toByteArray())
        assertEquals(1, a.drawAnnotationCount)
        assertEquals(1, b.drawAnnotationCount)
    }
}
