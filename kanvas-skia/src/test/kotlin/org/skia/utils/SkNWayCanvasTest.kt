package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
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
}
