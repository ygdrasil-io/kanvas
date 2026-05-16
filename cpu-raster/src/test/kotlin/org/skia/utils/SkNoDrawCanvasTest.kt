package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkDrawable
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkFont
import org.skia.foundation.SkVertices
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * R-suivi.10 — verify that [SkNoDrawCanvas] overrides every draw entry
 * point as a no-op. Each test invokes a draw call and checks that
 * the canvas's observable state (save-count, callback invocations)
 * stays as if no work was done.
 */
class SkNoDrawCanvasTest {

    @Test
    fun `drawAtlas on SkNoDrawCanvas is a no-op`() {
        val canvas = SkNoDrawCanvas(width = 8, height = 8)
        val img = SkImage(4, 4, IntArray(16) { SK_ColorWHITE })
        // Should not throw, should not draw anything, should not even
        // observe the side-effects of decomposing into drawPath quads.
        canvas.drawAtlas(
            image = img,
            xform = arrayOf(SkRSXform.Identity),
            src = arrayOf(SkRect.MakeWH(4f, 4f)),
            colors = null,
            blendMode = SkBlendMode.kSrcOver,
            sampling = SkSamplingOptions.Default,
            cullRect = null,
            paint = SkPaint(SK_ColorBLACK),
        )
        // Save count stays at 1 (initial frame) — drawAtlas must not
        // open save / restore scopes on its own.
        assertEquals(1, canvas.getSaveCount())
    }

    @Test
    fun `drawTextBlob on SkNoDrawCanvas is a no-op`() {
        val canvas = SkNoDrawCanvas(width = 16, height = 16)
        val builder = SkTextBlobBuilder()
        builder.allocRun(SkFont(), count = 1, x = 0f, y = 12f)
        val blob = builder.make()!!
        canvas.drawTextBlob(blob, 0f, 0f, SkPaint(SK_ColorBLACK))
        assertEquals(1, canvas.getSaveCount())
    }

    @Test
    fun `drawDrawable on SkNoDrawCanvas does not invoke onDraw`() {
        val canvas = SkNoDrawCanvas(width = 8, height = 8)
        var invoked = false
        val drawable = object : SkDrawable() {
            override fun onDraw(canvas: org.skia.core.SkCanvas) {
                invoked = true
            }
            override fun onGetBounds(): SkRect = SkRect.MakeWH(1f, 1f)
        }
        canvas.drawDrawable(drawable, null)
        // SkNoDrawCanvas overrides drawDrawable to a no-op so the
        // drawable's onDraw must NOT fire.
        assertEquals(false, invoked)
    }

    @Test
    fun `drawAnnotation on SkNoDrawCanvas is a no-op`() {
        val canvas = SkNoDrawCanvas(width = 4, height = 4)
        // Should not throw — the default SkCanvas impl already drops
        // the annotation, but the override avoids any subclass hook
        // that might want to record it.
        canvas.drawAnnotation(SkRect.MakeWH(2f, 2f), "url", null)
    }

    @Test
    fun `drawVertices on SkNoDrawCanvas is a no-op`() {
        val canvas = SkNoDrawCanvas(width = 8, height = 8)
        val verts = SkVertices.MakeCopy(
            mode = SkVertices.VertexMode.kTriangles,
            positions = arrayOf(SkPoint(0f, 0f), SkPoint(8f, 0f), SkPoint(0f, 8f)),
        )
        canvas.drawVertices(verts, SkBlendMode.kSrcOver, SkPaint(SK_ColorBLACK))
    }

    @Test
    fun `save and restore stack remains accurate on SkNoDrawCanvas`() {
        // SkNoDrawCanvas intentionally does NOT override save / restore
        // / translate / scale — analysis passes must observe a coherent
        // matrix + save-count stack.
        val canvas = SkNoDrawCanvas(width = 100, height = 100)
        assertEquals(1, canvas.getSaveCount())
        canvas.save()
        assertEquals(2, canvas.getSaveCount())
        canvas.translate(10f, 10f)
        canvas.save()
        canvas.scale(2f, 2f)
        assertEquals(3, canvas.getSaveCount())
        canvas.restore()
        canvas.restore()
        assertEquals(1, canvas.getSaveCount())
    }
}
