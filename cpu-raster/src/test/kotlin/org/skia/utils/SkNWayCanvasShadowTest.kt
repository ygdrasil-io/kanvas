package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkLattice
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextSlug
import org.skia.foundation.SkFont
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkPoint3
import org.graphiks.math.SkRect

/**
 * R-suivi.50 — verify [SkNWayCanvas] forwards the four new SkCanvas
 * virtuals ([SkCanvas.drawShadow], [SkCanvas.drawSlug],
 * [SkCanvas.drawImageLattice], [SkCanvas.drawPicture]) to every
 * registered child canvas.
 */
class SkNWayCanvasShadowTest {

    private class CountingCanvas : SkCanvas(SkBitmap(4, 4)) {
        var shadowCalls = 0
        var slugCalls = 0
        var latticeCalls = 0
        var pictureCalls = 0
        var lastShadowFlags = -1
        var lastSlugOrigin: SkPoint? = null
        var lastLatticeDst: SkRect? = null
        var lastPictureMatrix: SkMatrix? = null

        override fun drawShadow(
            path: SkPath,
            zPlaneParams: SkPoint3,
            lightPos: SkPoint3,
            lightRadius: Float,
            ambientColor: Int,
            spotColor: Int,
            flags: Int,
        ) {
            shadowCalls++
            lastShadowFlags = flags
        }

        override fun drawSlug(slug: SkTextSlug, origin: SkPoint) {
            slugCalls++
            lastSlugOrigin = origin
        }

        override fun drawImageLattice(
            image: SkImage,
            lattice: SkLattice,
            dst: SkRect,
            filterMode: SkFilterMode,
            paint: SkPaint?,
        ) {
            latticeCalls++
            lastLatticeDst = dst
        }

        override fun drawPicture(
            picture: SkPicture,
            matrix: SkMatrix?,
            paint: SkPaint?,
        ) {
            pictureCalls++
            lastPictureMatrix = matrix
        }
    }

    @Test
    fun `drawShadow fans out to every child`() {
        val n = SkNWayCanvas(40, 40)
        val a = CountingCanvas()
        val b = CountingCanvas()
        n.addCanvas(a)
        n.addCanvas(b)

        n.drawShadow(
            path = SkPath.Rect(SkRect.MakeWH(10f, 10f)),
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(20f, 20f, 100f),
            lightRadius = 20f,
            ambientColor = SK_ColorBLACK,
            spotColor = SK_ColorBLACK,
            flags = 7,
        )

        assertEquals(1, a.shadowCalls)
        assertEquals(1, b.shadowCalls)
        assertEquals(7, a.lastShadowFlags)
        assertEquals(7, b.lastShadowFlags)
    }

    @Test
    fun `drawSlug fans out to every child`() {
        val n = SkNWayCanvas(40, 40)
        val a = CountingCanvas()
        val b = CountingCanvas()
        n.addCanvas(a)
        n.addCanvas(b)

        val builder = SkTextBlobBuilder()
        builder.allocRun(SkFont(), 0, 0f, 0f)
        val blob = builder.make()!!
        val slug = SkTextSlug(blob, SkPaint(SK_ColorRED), SkPoint(1f, 2f))

        n.drawSlug(slug, SkPoint(3f, 4f))

        assertEquals(1, a.slugCalls)
        assertEquals(1, b.slugCalls)
        assertEquals(3f, a.lastSlugOrigin!!.fX)
        assertEquals(4f, a.lastSlugOrigin!!.fY)
    }

    @Test
    fun `drawImageLattice fans out to every child`() {
        val n = SkNWayCanvas(40, 40)
        val a = CountingCanvas()
        val b = CountingCanvas()
        n.addCanvas(a)
        n.addCanvas(b)

        val image = SkBitmap(8, 8).also { it.eraseColor(SK_ColorRED) }.asImage()
        val lattice = SkLattice(xDivs = intArrayOf(2, 6), yDivs = intArrayOf(2, 6))
        val dst = SkRect.MakeWH(20f, 20f)

        n.drawImageLattice(image, lattice, dst, SkFilterMode.kLinear, null)

        assertEquals(1, a.latticeCalls)
        assertEquals(1, b.latticeCalls)
        assertSame(dst, a.lastLatticeDst)
    }

    @Test
    fun `drawPicture fans out to every child`() {
        val n = SkNWayCanvas(40, 40)
        val a = CountingCanvas()
        val b = CountingCanvas()
        n.addCanvas(a)
        n.addCanvas(b)

        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeWH(10f, 10f))
        recCanvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorRED))
        val picture = rec.finishRecordingAsPicture()

        val matrix = SkMatrix.MakeTrans(5f, 5f)
        n.drawPicture(picture, matrix, null)

        assertEquals(1, a.pictureCalls)
        assertEquals(1, b.pictureCalls)
        assertNotNull(a.lastPictureMatrix)
        assertNotNull(b.lastPictureMatrix)
    }
}
