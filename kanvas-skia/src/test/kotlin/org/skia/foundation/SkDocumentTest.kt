package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SkRect

/**
 * Tests for the [SkDocument] abstract base — covers the begin/end/close
 * lifecycle machinery via a minimal fake backend that simply counts
 * callbacks. The PDF backend gets its own coverage in
 * [org.skia.docs.SkPDFDocumentTest].
 */
class SkDocumentTest {

    private class FakeDoc(private val onBegin: () -> SkCanvas) : SkDocument() {
        var beginCalls: Int = 0
        var endCalls: Int = 0
        var closeCalls: Int = 0
        var abortCalls: Int = 0
        var lastWidth: Float = 0f
        var lastHeight: Float = 0f
        var lastContent: SkRect? = null

        override fun onBeginPage(width: Float, height: Float, content: SkRect?): SkCanvas {
            beginCalls++
            lastWidth = width
            lastHeight = height
            lastContent = content
            return onBegin()
        }
        override fun onEndPage() { endCalls++ }
        override fun onClose() { closeCalls++ }
        override fun onAbort() { abortCalls++ }
    }

    @Test
    fun `begin returns canvas and endPage matches`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        val c = doc.beginPage(100f, 50f)
        assertEquals(canvas, c)
        assertEquals(1, doc.beginCalls)
        assertEquals(0, doc.endCalls)
        assertEquals(100f, doc.lastWidth)
        assertEquals(50f, doc.lastHeight)
        doc.endPage()
        assertEquals(1, doc.endCalls)
    }

    @Test
    fun `beginPage while in-page implicitly endsPage first`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        doc.beginPage(10f, 10f)
        doc.beginPage(20f, 20f)
        assertEquals(2, doc.beginCalls)
        assertEquals(1, doc.endCalls)
    }

    @Test
    fun `close flushes pending page and is idempotent`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        doc.beginPage(10f, 10f)
        doc.close()
        assertEquals(1, doc.endCalls)
        assertEquals(1, doc.closeCalls)
        doc.close()
        assertEquals(1, doc.closeCalls)
    }

    @Test
    fun `beginPage after close throws`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        doc.close()
        var threw = false
        try { doc.beginPage(1f, 1f) } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `abort calls onAbort and prevents further pages`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        doc.abort()
        assertEquals(1, doc.abortCalls)
        var threw = false
        try { doc.beginPage(1f, 1f) } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `content rectangle is forwarded to onBeginPage`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        val crop = SkRect.MakeLTRB(5f, 6f, 7f, 8f)
        doc.beginPage(50f, 50f, crop)
        assertNotNull(doc.lastContent)
        assertEquals(crop, doc.lastContent)
    }

    @Test
    fun `endPage with no active page is a no-op`() {
        val canvas = SkCanvas(SkBitmap(1, 1))
        val doc = FakeDoc(onBegin = { canvas })
        doc.endPage()
        assertEquals(0, doc.endCalls)
        assertFalse(doc.closeCalls > 0)
    }
}
