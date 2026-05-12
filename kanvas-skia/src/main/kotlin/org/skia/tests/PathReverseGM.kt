package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.pathops.internal.reverseAddPath

/**
 * Port of Skia's `gm/pathreverse.cpp` —
 * `DEF_SIMPLE_GM_BG_NAME(pathreverse, canvas, 640, 480, SK_ColorWHITE,
 *  SkString("path-reverse"))`.
 *
 * Reference PNG: `path-reverse.png` (the macro's 5th argument supplies the
 * GM's registered name; the Kotlin port reflects this via [getName]).
 *
 * The GM exercises `SkPathPriv::ReverseAddPath` — appending a source path
 * onto an [SkPathBuilder] with its verb stream walked back-to-front. Each
 * row draws the source path on the left and its reverse on the right
 * (translated `+150` on x). Successive rows accumulate verbs on the
 * builder so the reverse path grows: rect alone, rect + offset rect,
 * a moveTo+lineTo opener plus two ovals, and finally a synthetic
 * Hiragino Maru Gothic Pro "e" glyph outline (cubic-only).
 *
 * `reverseAddPath` lives at `org.skia.pathops.internal.reverseAddPath` —
 * a top-level helper since [SkPathBuilder] does not (yet) expose a
 * native reverse-add hook.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG_NAME(pathreverse, canvas, 640, 480, SK_ColorWHITE,
 *                       SkString("path-reverse")) {
 *     SkRect r = { 10, 10, 100, 60 };
 *     SkPathBuilder builder;
 *
 *     builder.addRect(r); test_rev(canvas, builder.snapshot());
 *
 *     canvas->translate(0, 100);
 *     builder.offset(20, 20);
 *     builder.addRect(r); test_rev(canvas, builder.detach());
 *
 *     canvas->translate(0, 100);
 *     builder.moveTo(10, 10); builder.lineTo(30, 30);
 *     builder.addOval(r);
 *     r.offset(50, 20);
 *     builder.addOval(r);
 *     test_rev(canvas, builder.detach());
 *
 *     SkPath path = hiragino_maru_goth_pro_e();
 *     canvas->translate(0, 100);
 *     test_rev(canvas, path);
 * }
 * ```
 */
public class PathReverseGM : GM() {
    override fun getName(): String = "path-reverse"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Mirrors `SkRect r = { 10, 10, 100, 60 };`
        var r = SkRect.MakeLTRB(10f, 10f, 100f, 60f)

        val builder = SkPathBuilder()

        builder.addRect(r); testRev(c, builder.snapshot())

        c.translate(0f, 100f)
        builder.offset(20f, 20f)
        builder.addRect(r); testRev(c, builder.detach())

        c.translate(0f, 100f)
        builder.moveTo(10f, 10f); builder.lineTo(30f, 30f)
        builder.addOval(r)
        // Mirrors `r.offset(50, 20)` — SkRect is immutable here so we
        // rebuild the offset rect explicitly.
        r = SkRect.MakeLTRB(r.left + 50f, r.top + 20f, r.right + 50f, r.bottom + 20f)
        builder.addOval(r)
        testRev(c, builder.detach())

        val path = hiraginoMaruGothProE()
        c.translate(0f, 100f)
        testRev(c, path)
    }

    /** Mirrors `static void test_path(SkCanvas*, const SkPath&)`. */
    private fun testPath(canvas: SkCanvas, path: SkPath) {
        val paint = SkPaint()
        paint.isAntiAlias = true
        canvas.drawPath(path, paint)

        paint.style = SkPaint.Style.kStroke_Style
        paint.color = SK_ColorRED
        canvas.drawPath(path, paint)
    }

    /** Mirrors `static void test_rev(SkCanvas*, const SkPath&)`. */
    private fun testRev(canvas: SkCanvas, path: SkPath) {
        testPath(canvas, path)

        val rev = SkPathBuilder()
        reverseAddPath(rev, path)
        canvas.save()
        canvas.translate(150f, 0f)
        testPath(canvas, rev.detach())
        canvas.restore()
    }

    /**
     * Mirrors the upstream `hiragino_maru_goth_pro_e()` helper —
     * a hard-coded path approximating the "e" glyph from Hiragino Maru
     * Gothic Pro, captured once on Mac via `getTextPath` then duplicated
     * here so the test runs cross-platform without that font.
     */
    private fun hiraginoMaruGothProE(): SkPath {
        val path = SkPathBuilder()
        path.moveTo(98.6f, 24.7f)
        path.cubicTo(101.7f, 24.7f, 103.6f, 22.8f, 103.6f, 19.2f)
        path.cubicTo(103.6f, 18.9f, 103.6f, 18.7f, 103.6f, 18.4f)
        path.cubicTo(102.6f, 5.3f, 94.4f, -6.1f, 79.8f, -6.1f)
        path.cubicTo(63.5f, -6.1f, 54.5f, 6f, 54.5f, 23.3f)
        path.cubicTo(54.5f, 40.6f, 64f, 52.2f, 80.4f, 52.2f)
        path.cubicTo(93.4f, 52.2f, 99.2f, 45.6f, 102.4f, 39f)
        path.cubicTo(102.8f, 38.4f, 102.9f, 37.8f, 102.9f, 37.2f)
        path.cubicTo(102.9f, 35.4f, 101.5f, 34.2f, 99.8f, 33.7f)
        path.cubicTo(99.1f, 33.5f, 98.4f, 33.3f, 97.7f, 33.3f)
        path.cubicTo(96.3f, 33.3f, 95f, 34f, 94.1f, 35.8f)
        path.cubicTo(91.7f, 41.1f, 87.7f, 44.7f, 80.5f, 44.7f)
        path.cubicTo(69.7f, 44.7f, 63.6f, 37f, 63.4f, 24.7f)
        path.lineTo(98.6f, 24.7f)
        path.close()
        path.moveTo(63.7f, 17.4f)
        path.cubicTo(65f, 7.6f, 70.2f, 1.2f, 79.8f, 1.2f)
        path.cubicTo(89f, 1.2f, 93.3f, 8.5f, 94.5f, 15.6f)
        path.cubicTo(94.5f, 15.8f, 94.5f, 16f, 94.5f, 16.1f)
        path.cubicTo(94.5f, 17f, 94.1f, 17.4f, 93f, 17.4f)
        path.lineTo(63.7f, 17.4f)
        path.close()
        return path.detach()
    }
}
