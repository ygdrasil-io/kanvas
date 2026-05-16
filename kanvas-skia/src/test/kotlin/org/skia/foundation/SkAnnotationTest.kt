package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Tests for the free `SkAnnotate*` functions in
 * [`SkAnnotation`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkAnnotation.kt).
 *
 * The raster sink already ignores `drawAnnotation` — these tests
 * just verify the helpers call through with the right key/value
 * pairing by using a recording subclass of [SkCanvas].
 */
class SkAnnotationTest {

    private class RecordingCanvas : SkCanvas(SkBitmap(1, 1)) {
        data class Annot(val rect: SkRect, val key: String, val value: ByteArray?)
        val annotations = mutableListOf<Annot>()
        override fun drawAnnotation(rect: SkRect, key: String, value: ByteArray?) {
            annotations += Annot(rect, key, value)
        }
    }

    @Test
    fun `SkAnnotateRectWithURL routes through drawAnnotation with url key`() {
        val canvas = RecordingCanvas()
        val rect = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val url = SkData.MakeWithCopy("https://skia.org".toByteArray())
        SkAnnotateRectWithURL(canvas, rect, url)
        assertEquals(1, canvas.annotations.size)
        assertEquals(SK_ANNOTATION_URL_KEY, canvas.annotations[0].key)
        assertEquals(rect, canvas.annotations[0].rect)
        assertEquals("https://skia.org", canvas.annotations[0].value!!.decodeToString())
    }

    @Test
    fun `SkAnnotateNamedDestination collapses point to zero-sized rect`() {
        val canvas = RecordingCanvas()
        val nameData = SkData.MakeWithCopy("anchor".toByteArray())
        SkAnnotateNamedDestination(canvas, SkPoint(5f, 7f), nameData)
        assertEquals(1, canvas.annotations.size)
        val a = canvas.annotations[0]
        assertEquals(SK_ANNOTATION_DEFINE_NAMED_DEST_KEY, a.key)
        assertEquals(5f, a.rect.left)
        assertEquals(7f, a.rect.top)
        assertEquals(5f, a.rect.right)
        assertEquals(7f, a.rect.bottom)
    }

    @Test
    fun `SkAnnotateLinkToDestination uses link-to-named-dest key`() {
        val canvas = RecordingCanvas()
        SkAnnotateLinkToDestination(canvas, SkRect.MakeWH(20f, 20f), null)
        assertEquals(1, canvas.annotations.size)
        assertEquals(SK_ANNOTATION_LINK_TO_NAMED_DEST_KEY, canvas.annotations[0].key)
        assertNull(canvas.annotations[0].value)
    }

    @Test
    fun `default SkCanvas drawAnnotation is silent`() {
        // Smoke check : the raster sink no-ops, so calling the helpers
        // on a vanilla SkCanvas must not throw.
        val canvas = SkCanvas(SkBitmap(1, 1))
        SkAnnotateRectWithURL(canvas, SkRect.MakeWH(1f, 1f), null)
    }
}
