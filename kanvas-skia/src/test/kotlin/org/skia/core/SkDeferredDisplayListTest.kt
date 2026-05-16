package org.skia.core


import org.skia.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * Q4 verification suite for [SkSurfaceCharacterization] +
 * [SkDeferredDisplayList] + [SkDeferredDisplayListRecorder] +
 * [SkSurface.draw] (DDL playback).
 *
 * **Behaviour under test** :
 *  - [SkSurfaceCharacterization] equality is structural (two
 *    characterizations with the same `imageInfo` are equal).
 *  - [SkDeferredDisplayListRecorder] yields a working canvas before
 *    detach and `null` after.
 *  - [SkDeferredDisplayList.opCount] reflects the recording.
 *  - [SkSurface.draw] returns `true` and rasterises the recorded
 *    ops when the characterization matches.
 *  - [SkSurface.draw] returns `false` and leaves the surface
 *    untouched when the characterization mismatches (different
 *    dimensions, colour type, alpha type, or colour space).
 *  - Pixel parity : a DDL replayed onto a matching surface
 *    produces the same pixels as drawing directly into that
 *    surface.
 *  - Recording → many surfaces : the same DDL can replay onto
 *    multiple compatible surfaces.
 */
class SkDeferredDisplayListTest {

    private fun n32info(w: Int, h: Int): SkImageInfo =
        SkImageInfo.MakeN32Premul(w, h, SkColorSpace.makeSRGB())

    private fun makeSurface(w: Int = 40, h: Int = 40): SkSurface =
        SkSurface.MakeRaster(n32info(w, h)).also { it.canvas.clear(SK_ColorWHITE) }

    private fun paint(color: Int): SkPaint =
        SkPaint(color).apply { isAntiAlias = false }

    // ─── SkSurfaceCharacterization ─────────────────────────────────────

    @Test
    fun `Make rejects empty imageInfo`() {
        var caught = false
        try {
            SkSurfaceCharacterization.Make(n32info(0, 10))
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught, "Make must reject zero-width imageInfo")
    }

    @Test
    fun `Make accepts a valid imageInfo and exposes its fields`() {
        val info = n32info(40, 30)
        val c = SkSurfaceCharacterization.Make(info)
        assertEquals(40, c.width)
        assertEquals(30, c.height)
        assertEquals(SkColorType.kRGBA_8888, c.colorType)
    }

    @Test
    fun `From snaps a characterization off an existing surface`() {
        val surface = makeSurface(50, 60)
        val c = SkSurfaceCharacterization.From(surface)
        assertEquals(50, c.width)
        assertEquals(60, c.height)
        assertTrue(c.isCompatibleWith(surface))
    }

    @Test
    fun `Two characterizations with identical imageInfo are equal`() {
        val a = SkSurfaceCharacterization.Make(n32info(40, 40))
        val b = SkSurfaceCharacterization.Make(n32info(40, 40))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Different dimensions produce non-equal characterizations`() {
        val a = SkSurfaceCharacterization.Make(n32info(40, 40))
        val b = SkSurfaceCharacterization.Make(n32info(40, 41))
        assertNotEquals(a, b)
    }

    @Test
    fun `isCompatibleWith returns false for a surface with different dimensions`() {
        val c = SkSurfaceCharacterization.Make(n32info(40, 40))
        val mismatched = makeSurface(40, 41)
        assertFalse(c.isCompatibleWith(mismatched))
    }

    // ─── SkDeferredDisplayListRecorder ─────────────────────────────────

    @Test
    fun `recorder vends a usable canvas before detach`() {
        val r = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(n32info(40, 40)))
        val c = r.getCanvas()
        assertNotNull(c)
        assertEquals(40, c!!.width)
        assertEquals(40, c.height)
    }

    @Test
    fun `recorder detach returns a DDL and seals the canvas`() {
        val r = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(n32info(40, 40)))
        r.getCanvas()!!.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val ddl = r.detach()
        assertNotNull(ddl)
        assertNull(r.getCanvas(), "canvas must be null after detach")
    }

    @Test
    fun `second detach throws`() {
        val r = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(n32info(40, 40)))
        r.detach()
        var caught = false
        try {
            r.detach()
        } catch (_: IllegalStateException) {
            caught = true
        }
        assertTrue(caught, "second detach must throw")
    }

    @Test
    fun `DDL opCount reflects the recorded ops`() {
        val r = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(n32info(40, 40)))
        val c = r.getCanvas()!!
        c.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        c.drawRect(SkRect.MakeWH(20f, 20f), paint(SK_ColorBLUE))
        val ddl = r.detach()
        assertEquals(2, ddl.opCount)
    }

    // ─── SkSurface.draw(ddl) ───────────────────────────────────────────

    @Test
    fun `surface draw returns true and rasterises when characterization matches`() {
        val info = n32info(40, 40)
        val ch = SkSurfaceCharacterization.Make(info)
        val recorder = SkDeferredDisplayListRecorder(ch)
        recorder.getCanvas()!!.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val ddl = recorder.detach()

        val surface = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        val ok = surface.draw(ddl)
        assertTrue(ok, "matching characterization → playback must succeed")

        val snap = surface.makeImageSnapshot()
        // (5, 5) is inside the recorded rect → red.
        assertEquals(SK_ColorRED, snap.peekPixel(5, 5),
            "DDL playback must rasterise the recorded rect")
        // (20, 20) is outside the recorded rect → original white.
        assertEquals(SK_ColorWHITE, snap.peekPixel(20, 20))
    }

    @Test
    fun `surface draw returns false and leaves surface untouched on mismatched dimensions`() {
        val recorderInfo = n32info(40, 40)
        val recorder = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(recorderInfo))
        recorder.getCanvas()!!.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val ddl = recorder.detach()

        // Surface dimensions differ : DDL playback must fail without touching pixels.
        val mismatched = SkSurface.MakeRaster(n32info(50, 50)).also { it.canvas.clear(SK_ColorWHITE) }
        val ok = mismatched.draw(ddl)
        assertFalse(ok, "mismatched dimensions → playback must fail")

        val snap = mismatched.makeImageSnapshot()
        // No DDL pixels should have landed — every pixel stays white.
        assertEquals(SK_ColorWHITE, snap.peekPixel(5, 5))
        assertEquals(SK_ColorWHITE, snap.peekPixel(25, 25))
    }

    @Test
    fun `surface draw is pixel-identical to drawing directly into the surface canvas`() {
        // Record the same scene into a DDL and into a direct canvas, then
        // compare every pixel. Establishes that DDL = identity transform.
        val info = n32info(40, 40)
        val recorder = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(info))
        val recCanvas = recorder.getCanvas()!!
        recCanvas.translate(5f, 5f)
        recCanvas.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        recCanvas.drawRect(SkRect.MakeXYWH(15f, 0f, 8f, 8f), paint(SK_ColorBLUE))
        val ddl = recorder.detach()

        val direct = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        direct.canvas.translate(5f, 5f)
        direct.canvas.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        direct.canvas.drawRect(SkRect.MakeXYWH(15f, 0f, 8f, 8f), paint(SK_ColorBLUE))

        val replayed = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        assertTrue(replayed.draw(ddl))

        val a = direct.makeImageSnapshot()
        val b = replayed.makeImageSnapshot()
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                assertEquals(
                    a.peekPixel(x, y), b.peekPixel(x, y),
                    "pixel ($x, $y) drift between direct and DDL playback",
                )
            }
        }
    }

    @Test
    fun `same DDL replays onto multiple compatible surfaces`() {
        val info = n32info(40, 40)
        val recorder = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(info))
        recorder.getCanvas()!!.drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val ddl = recorder.detach()

        // Two independent surfaces of identical signature.
        val surfaceA = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        val surfaceB = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        assertTrue(surfaceA.draw(ddl))
        assertTrue(surfaceB.draw(ddl))

        // Both surfaces show the same DDL output.
        assertEquals(SK_ColorRED, surfaceA.makeImageSnapshot().peekPixel(5, 5))
        assertEquals(SK_ColorRED, surfaceB.makeImageSnapshot().peekPixel(5, 5))
    }

    @Test
    fun `empty DDL is a no-op on its target surface`() {
        val info = n32info(40, 40)
        val recorder = SkDeferredDisplayListRecorder(SkSurfaceCharacterization.Make(info))
        // No draws on the recorder canvas.
        val ddl = recorder.detach()
        assertEquals(0, ddl.opCount)

        val surface = SkSurface.MakeRaster(info).also { it.canvas.clear(SK_ColorWHITE) }
        val before = surface.makeImageSnapshot()
        assertTrue(surface.draw(ddl), "empty DDL with matching characterization → succeeds")
        val after = surface.makeImageSnapshot()
        // Pixel-identical : empty DDL changes nothing.
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                assertEquals(before.peekPixel(x, y), after.peekPixel(x, y))
            }
        }
    }
}
