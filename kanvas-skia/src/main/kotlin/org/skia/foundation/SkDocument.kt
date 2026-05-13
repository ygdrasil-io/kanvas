package org.skia.foundation

import org.skia.core.SkCanvas
import org.skia.math.SkRect

/**
 * High-level API for creating a document-based canvas. Mirrors upstream
 * `include/core/SkDocument.h`. To use:
 *
 *  1. Create a document (`SkPDF.MakeDocument(...)`).
 *  2. For each "page" of content:
 *      a. `canvas = doc.beginPage(...)`
 *      b. draw into `canvas`
 *      c. `doc.endPage()`
 *  3. Close the document with `doc.close()`.
 *
 * After [close], the document can no longer add pages. [abort] stops
 * producing the document immediately; the output stream content must
 * be considered invalid.
 *
 * Phase R3.6 — pure Kotlin abstract base; backends provided by sub-
 * packages (e.g. [org.skia.docs.SkPDF]).
 */
public abstract class SkDocument {

    /** Lifecycle state of a document. */
    protected enum class State { kBetweenPages, kInPage, kClosed }

    /** Current lifecycle state. Subclasses inspect via [state]. */
    protected var state: State = State.kBetweenPages
        private set

    /**
     * Begin a new page for the document, returning the canvas that will
     * draw into the page. If a page is already active, [endPage] is
     * invoked first (matching upstream behaviour). Width and height are
     * in PDF point units (1pt = 1/72 inch).
     *
     * @param width   page width in points
     * @param height  page height in points
     * @param content optional crop / content rectangle (in points)
     */
    public fun beginPage(width: Float, height: Float, content: SkRect? = null): SkCanvas {
        check(state != State.kClosed) { "SkDocument: beginPage() after close()" }
        if (state == State.kInPage) endPage()
        val canvas = onBeginPage(width, height, content)
        state = State.kInPage
        return canvas
    }

    /**
     * Call when the content for the current page has been drawn. Has no
     * effect outside of an active page.
     */
    public fun endPage() {
        if (state == State.kInPage) {
            onEndPage()
            state = State.kBetweenPages
        }
    }

    /**
     * Call when all pages have been drawn. This flushes any pending
     * page and finalises the underlying stream. Idempotent.
     */
    public fun close() {
        if (state == State.kClosed) return
        if (state == State.kInPage) endPage()
        onClose()
        state = State.kClosed
    }

    /**
     * Stop producing the document immediately. After [abort] the
     * output stream must not be trusted. Idempotent.
     */
    public fun abort() {
        if (state == State.kClosed) return
        onAbort()
        state = State.kClosed
    }

    // ---- Subclass hooks -----------------------------------------

    protected abstract fun onBeginPage(width: Float, height: Float, content: SkRect?): SkCanvas
    protected abstract fun onEndPage()
    protected abstract fun onClose()
    protected abstract fun onAbort()
}

// R-suivi.42 — the local `SkWStreamMinimal` / `SkDynamicMemoryWStream`
// stand-ins were dropped now that the real
// [org.skia.foundation.stream.SkWStream] /
// [org.skia.foundation.stream.SkDynamicMemoryWStream] from PR #382 are on
// master. Backends should depend on `SkWStream` directly.
