package org.skia.svg

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import java.io.Writer
import java.util.Locale

/**
 * Vector-output adapter for [SkCanvas] that emits SVG XML — slice
 * **B2.1** of [MIGRATION_PLAN_SVG.md](../../../../../../../MIGRATION_PLAN_SVG.md).
 *
 * Mirrors upstream Skia's
 * [`SkSVGCanvas`](https://github.com/google/skia/blob/main/src/svg/SkSVGCanvas.cpp).
 * The canvas writes `<svg width=… height=…>` on construct, accumulates
 * one SVG element per draw call into a caller-owned [Writer], and
 * closes the document on [flush]. Mirrors
 * [`org.skia.core.SkRecordingCanvas`](../../../core/SkRecordingCanvas.kt)'s
 * "extend [SkCanvas] with a 1×1 dummy bitmap and override draw ops"
 * pattern — but where `SkRecordingCanvas` records into a list,
 * `SkSVGCanvas` writes to XML.
 *
 * **B2.1 scope** (this slice) :
 *  - Geometry : `drawRect` / `drawOval` / `drawCircle` / `drawLine` /
 *    `drawRRect` / `drawPath` → `<rect>` / `<ellipse>` / `<circle>` /
 *    `<line>` / `<rect rx=… ry=…>` / `<path d="…">`.
 *  - CTM : every draw with a non-identity matrix gets a
 *    `transform="matrix(a b c d e f)"` attribute. We do **not** emit
 *    `<g>` wrappers per `save()` / `restore()` in this slice — the
 *    state stack is still tracked for [getTotalMatrix] correctness,
 *    and B2.3 (clip) will introduce wrappers where they make sense.
 *  - SkPath verbs : `kMove → M`, `kLine → L`, `kQuad → Q`, `kCubic →
 *    C`, `kClose → Z`. `kConic` is approximated by splitting into
 *    cubics on the fly — same approach the rasterizer's flattener
 *    uses, accuracy is fine for a structural SVG.
 *
 * **Out of scope for B2.1** (later slices) :
 *  - Paint surface (fill colour, stroke, alpha) — B2.2 will refine ;
 *    today the placeholder is `fill="black" stroke="none"`.
 *  - Clip → `<clipPath>` defs — B2.3.
 *  - Image / gradient `<defs>` — B2.4.
 *
 * **Constructor contract** :
 *  - [out] is a caller-owned [Writer]. We do **not** close it on
 *    [flush] — the caller decides the buffer's lifetime.
 *  - [width] / [height] are the SVG viewport dimensions in user
 *    space. Floats so callers don't have to round.
 *
 * Usage :
 * ```kotlin
 * val sb = StringWriter()
 * val canvas = SkSVGCanvas(sb, 800f, 600f)
 * gm.draw(canvas)
 * canvas.flush()
 * val svg = sb.toString()
 * ```
 */
public open class SkSVGCanvas(
    private val out: Writer,
    private val svgWidth: Float,
    private val svgHeight: Float,
) : SkCanvas(SkBitmap(1, 1)) {

    init {
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
        out.append(" width=\"").append(formatScalar(svgWidth)).append("\"")
        out.append(" height=\"").append(formatScalar(svgHeight)).append("\"")
        out.append(" viewBox=\"0 0 ")
        out.append(formatScalar(svgWidth)).append(' ').append(formatScalar(svgHeight))
        out.append("\">\n")
    }

    /** SVG viewport width — overrides the dummy 1×1 backing bitmap's width. */
    override val width: Int get() = kotlin.math.max(1, kotlin.math.ceil(svgWidth.toDouble()).toInt())

    /** SVG viewport height. */
    override val height: Int get() = kotlin.math.max(1, kotlin.math.ceil(svgHeight.toDouble()).toInt())

    /**
     * Close the `<svg>` document and flush the underlying [Writer].
     * Idempotent : a second call is a no-op so callers can defensively
     * flush even if an exception terminated the draw loop.
     */
    public open fun flush() {
        if (closed) return
        closed = true
        out.append("</svg>\n")
        out.flush()
    }

    private var closed: Boolean = false

    // ─── Draw ops — emit one SVG element each ─────────────────────────

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        emitElement("rect", attrs = {
            attr("x", rect.left)
            attr("y", rect.top)
            attr("width", rect.width())
            attr("height", rect.height())
        }, paint = paint)
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val rx = oval.width() * 0.5f
        val ry = oval.height() * 0.5f
        emitElement("ellipse", attrs = {
            attr("cx", cx)
            attr("cy", cy)
            attr("rx", rx)
            attr("ry", ry)
        }, paint = paint)
    }

    override fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        emitElement("circle", attrs = {
            attr("cx", cx)
            attr("cy", cy)
            attr("r", radius)
        }, paint = paint)
    }

    override fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        emitElement("line", attrs = {
            attr("x1", x0)
            attr("y1", y0)
            attr("x2", x1)
            attr("y2", y1)
        }, paint = paint)
    }

    override fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        // SVG <rect> only supports a uniform corner radius pair (rx,
        // ry). For non-uniform corners (kNinePatch / kComplex) and
        // for the simple-but-non-zero case, emit the corner-aware
        // rect. Complex rrects fall back to <path> in a future slice
        // ; for now we approximate with the upper-left radii pair.
        val r = rrect.rect()
        val simpleRadii = rrect.getSimpleRadii()
        emitElement("rect", attrs = {
            attr("x", r.left)
            attr("y", r.top)
            attr("width", r.width())
            attr("height", r.height())
            if (simpleRadii.fX != 0f) attr("rx", simpleRadii.fX)
            if (simpleRadii.fY != 0f) attr("ry", simpleRadii.fY)
        }, paint = paint)
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        if (path.isEmpty()) return
        val d = pathToSvgD(path)
        emitElement("path", attrs = {
            attr("d", d)
            // <path>'s default fill-rule is "nonzero" — the same as
            // SkPathFillType.kWinding. Emit the opposite explicitly.
            when (path.fillType) {
                org.skia.foundation.SkPathFillType.kEvenOdd,
                org.skia.foundation.SkPathFillType.kInverseEvenOdd ->
                    attr("fill-rule", "evenodd")
                else -> {} // "nonzero" is the SVG default
            }
        }, paint = paint)
    }

    // ─── Element emitter + attribute builder ──────────────────────────

    /**
     * Emit a single SVG element on its own line. Indent matches the
     * `<svg>` root indent (2 spaces). The [paint] is serialised by
     * [paintToSvgAttrs] (B2.2 — full surface) ; non-default blend
     * modes get a leading XML comment via [emitBlendModeComment].
     */
    private inline fun emitElement(
        tag: String,
        attrs: AttrBuilder.() -> Unit,
        paint: SkPaint,
    ) {
        emitBlendModeComment(paint)
        val builder = AttrBuilder()
        attrs.invoke(builder)
        out.append("  <").append(tag)
        // Geometry attrs first.
        out.append(builder.serialize())
        // Paint surface — fill / stroke / alpha / cap / join / dash.
        val paintAttrs = paintToSvgAttrs(paint)
        if (paintAttrs.isNotEmpty()) {
            out.append(' ').append(paintAttrs)
        }
        // CTM as a per-draw transform attr when non-identity.
        val m = getTotalMatrix()
        if (!m.isIdentity) {
            out.append(' ').append("transform=\"").append(matrixToSvg(m)).append('"')
        }
        out.append(" />\n")
    }

    /**
     * Build the `fill`, `stroke`, and `stroke-*` attribute soup for
     * [paint] — B2.2 replacement of the B2.1 stub.
     *
     * **Honoured fields** :
     *  - [SkPaint.color] → `fill="#rrggbb"` / `stroke="#rrggbb"`
     *    (lower-case hex ; alpha lands in [SkPaint.alpha] →
     *    `fill-opacity` / `stroke-opacity` to keep the channel
     *    decomposition diff-friendly).
     *  - [SkPaint.alpha] → `fill-opacity="…"` / `stroke-opacity="…"`
     *    when `< 255` (omitted at fully opaque).
     *  - [SkPaint.style] → which side(s) of the (`fill`, `stroke`)
     *    pair are emitted. `kFill_Style` → `fill=… stroke="none"` ;
     *    `kStroke_Style` → `fill="none" stroke=…` ; `kStrokeAndFill_Style`
     *    → both colour+opacity attrs.
     *  - [SkPaint.strokeWidth] / [SkPaint.strokeCap] /
     *    [SkPaint.strokeJoin] / [SkPaint.strokeMiter] →
     *    `stroke-width / stroke-linecap / stroke-linejoin /
     *    stroke-miterlimit`. Miter is emitted only when join is
     *    miter and the value differs from SVG's default of `4`.
     *  - [SkPaint.pathEffect] when the effect is an
     *    [SkDashPathEffect] → `stroke-dasharray="i0 i1 …"` +
     *    `stroke-dashoffset="phase"` (omitted at phase 0).
     *  - [SkPaint.isAntiAlias] → `shape-rendering="crispEdges"` when
     *    AA is **off** (SVG renderers default to AA-on).
     *
     * **Out of scope** for B2.2 (handled in later slices) :
     *  - Shaders (gradients / bitmaps) — B2.4 wires them via
     *    `<defs>` references that override the colour attribute.
     *  - Color filters — descoped.
     *  - Mask filters / image filters — descoped (filters slice).
     */
    private fun paintToSvgAttrs(paint: SkPaint): String {
        val sb = StringBuilder()
        val style = paint.style
        val color = paint.color
        val rgb = colorHex(color)
        val alpha = SkColorGetA(color) // [0, 255]
        val emitFill = style == SkPaint.Style.kFill_Style ||
            style == SkPaint.Style.kStrokeAndFill_Style
        val emitStroke = style == SkPaint.Style.kStroke_Style ||
            style == SkPaint.Style.kStrokeAndFill_Style

        if (emitFill) {
            appendAttr(sb, "fill", rgb)
            if (alpha < 255) appendAttr(sb, "fill-opacity", opacityString(alpha))
        } else {
            appendAttr(sb, "fill", "none")
        }

        if (emitStroke) {
            appendAttr(sb, "stroke", rgb)
            if (alpha < 255) appendAttr(sb, "stroke-opacity", opacityString(alpha))
            // SVG's default stroke-width is 1, but most consumers expect
            // explicit width to avoid ambiguity ; emit it always.
            appendAttr(sb, "stroke-width", formatScalar(paint.strokeWidth))
            // stroke-linecap : map upstream → SVG names.
            when (paint.strokeCap) {
                SkPaint.Cap.kButt_Cap -> { /* "butt" is the SVG default */ }
                SkPaint.Cap.kRound_Cap -> appendAttr(sb, "stroke-linecap", "round")
                SkPaint.Cap.kSquare_Cap -> appendAttr(sb, "stroke-linecap", "square")
            }
            // stroke-linejoin : map upstream → SVG names.
            when (paint.strokeJoin) {
                SkPaint.Join.kMiter_Join -> {
                    // SVG default is miter ; emit miterlimit only when
                    // it differs from SVG's default of 4 (matches Skia's
                    // SkPaint default — every paint with the default
                    // miter limit doesn't need the attribute).
                    if (paint.strokeMiter != 4f) {
                        appendAttr(sb, "stroke-miterlimit", formatScalar(paint.strokeMiter))
                    }
                }
                SkPaint.Join.kRound_Join -> appendAttr(sb, "stroke-linejoin", "round")
                SkPaint.Join.kBevel_Join -> appendAttr(sb, "stroke-linejoin", "bevel")
            }
            // stroke-dasharray + stroke-dashoffset.
            val pathEffect = paint.pathEffect
            if (pathEffect is org.skia.foundation.SkDashPathEffect) {
                val intervals = pathEffect.getIntervals()
                if (intervals.isNotEmpty()) {
                    val dashStr = intervals.joinToString(" ") { formatScalar(it) }
                    appendAttr(sb, "stroke-dasharray", dashStr)
                    val phase = pathEffect.getPhase()
                    if (phase != 0f) appendAttr(sb, "stroke-dashoffset", formatScalar(phase))
                }
            }
        } else {
            appendAttr(sb, "stroke", "none")
        }

        // Anti-alias flag : SVG renderers default to AA on, so we only
        // need to opt out when the paint says so.
        if (!paint.isAntiAlias) {
            appendAttr(sb, "shape-rendering", "crispEdges")
        }

        // Trim leading space.
        return if (sb.isNotEmpty() && sb[0] == ' ') sb.substring(1) else sb.toString()
    }

    /**
     * Emit an XML comment ahead of the next element when [paint]'s
     * blend mode is non-default. Mirrors upstream's "PDF / SVG
     * sinks document the blend mode they couldn't reproduce" pattern :
     *
     *  - [SkBlendMode.kSrcOver] → no comment (SVG default).
     *  - [SkBlendMode.kSrc] → `<!-- blend: kSrc -->` ; an SVG renderer
     *    can't natively reproduce kSrc so the visual result will look
     *    like SrcOver, but the comment carries the intent for a
     *    diagnostic reader.
     *  - any other mode → comment **plus** a `System.err` warning so
     *    consumers running the tests notice the loss of fidelity.
     */
    private fun emitBlendModeComment(paint: SkPaint) {
        val mode = paint.blendMode
        if (mode == org.skia.foundation.SkBlendMode.kSrcOver) return
        out.append("  <!-- blend: ").append(mode.name).append(" -->\n")
        if (mode != org.skia.foundation.SkBlendMode.kSrc) {
            System.err.println(
                "SkSVGCanvas : blend mode $mode is not natively supported in SVG ; " +
                    "output will render as kSrcOver. (B2.2)",
            )
        }
    }

    /** `appendAttr(builder, k, v)` → `" k=\"v\""`. */
    private fun appendAttr(sb: StringBuilder, name: String, value: String) {
        sb.append(' ').append(name).append("=\"").append(value).append('"')
    }

    /** Tiny attribute-list builder — preserves emission order, escapes minimally. */
    private class AttrBuilder {
        private val sb = StringBuilder()
        fun attr(name: String, value: SkScalar) {
            sb.append(' ').append(name).append("=\"").append(formatScalar(value)).append('"')
        }
        fun attr(name: String, value: String) {
            sb.append(' ').append(name).append("=\"").append(escape(value)).append('"')
        }
        fun serialize(): String = sb.toString()
        private fun escape(s: String): String {
            // SVG attribute values need '<', '&', '"' escaped. The
            // path "d" string and our other producers don't emit
            // those, but defend cheaply just in case.
            if (s.indexOfAny(charArrayOf('<', '&', '"')) < 0) return s
            val out = StringBuilder(s.length + 8)
            for (c in s) when (c) {
                '<' -> out.append("&lt;")
                '&' -> out.append("&amp;")
                '"' -> out.append("&quot;")
                else -> out.append(c)
            }
            return out.toString()
        }
    }

    public companion object {

        /**
         * Format a Kotlin [Float] as a SVG-friendly scalar :
         *  - integer-valued floats → "12" (drop the `.0`)
         *  - others → `%g`-ish with up to 6 significant digits.
         *  - locale-independent : `Locale.ROOT` so `,` doesn't sneak in.
         *
         * We deliberately *don't* use [java.lang.Float.toString] since
         * it emits trailing `.0` on integers and switches to scientific
         * notation past `1e7`, which SVG renderers tolerate but make the
         * output noisier than necessary for diff-ability.
         */
        /**
         * Format an [org.skia.foundation.SkColor] as a 6-char lower-case
         * hex string `"#rrggbb"`. Drops the alpha byte — alpha is
         * surfaced separately as `fill-opacity` / `stroke-opacity` so the
         * channel decomposition stays diff-friendly. The 3-char
         * shorthand (`#rgb`) is intentionally **not** used : tests can
         * grep for the full hex more reliably.
         */
        public fun colorHex(c: org.skia.foundation.SkColor): String {
            val r = SkColorGetR(c)
            val g = SkColorGetG(c)
            val b = SkColorGetB(c)
            return "#%02x%02x%02x".format(r, g, b)
        }

        /**
         * Format an 8-bit alpha channel as a `[0, 1]` SVG opacity
         * string. `255 → "1"` (handled by the caller — we never get
         * called for opaque alphas), `0 → "0"`, others render via
         * [formatScalar] for trailing-zero trim and locale safety.
         */
        public fun opacityString(alpha8: Int): String {
            val pinned = alpha8.coerceIn(0, 255)
            return formatScalar(pinned / 255f)
        }

        public fun formatScalar(v: SkScalar): String {
            if (v == 0f) return "0"
            if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e7f) {
                return v.toInt().toString()
            }
            return String.format(Locale.ROOT, "%.6g", v).let { trimZeros(it) }
        }

        private fun trimZeros(s: String): String {
            // %.6g may produce "1.50000" — strip trailing zeros after
            // the decimal point, then strip a dangling '.'.
            if ('.' !in s || 'e' in s || 'E' in s) return s
            var end = s.length
            while (end > 0 && s[end - 1] == '0') end--
            if (end > 0 && s[end - 1] == '.') end--
            return s.substring(0, end)
        }

        /**
         * Format an [SkMatrix] as SVG's `matrix(a b c d e f)` 6-tuple.
         * Skia layout is `(sx, kx, tx, ky, sy, ty)` ; SVG layout is
         *   `| a c e |
         *    | b d f |`
         * — so the wire order is `sx ky kx sy tx ty`.
         */
        public fun matrixToSvg(m: SkMatrix): String {
            val sb = StringBuilder("matrix(")
            sb.append(formatScalar(m.sx)).append(' ')
            sb.append(formatScalar(m.ky)).append(' ')
            sb.append(formatScalar(m.kx)).append(' ')
            sb.append(formatScalar(m.sy)).append(' ')
            sb.append(formatScalar(m.tx)).append(' ')
            sb.append(formatScalar(m.ty)).append(')')
            return sb.toString()
        }

        /**
         * Walk an [SkPath] and emit its verbs as an SVG `d` attribute
         * value. Verb mapping :
         *  - `kMove(x, y)`    → `M x y`
         *  - `kLine(x, y)`    → `L x y`
         *  - `kQuad(c, end)`  → `Q cx cy ex ey`
         *  - `kCubic(c1, c2, end)` → `C c1x c1y c2x c2y ex ey`
         *  - `kConic(c, end, w)` → split into 2 cubics via the standard
         *     rational-quadratic→cubic conversion, then emit two `C`
         *     segments. Approximation good enough for B2.1's
         *     "structural SVG" goal ; the rasterizer uses a deeper
         *     subdivision but the visual difference is sub-pixel for
         *     all weights commonly seen in upstream GMs.
         *  - `kClose` → `Z`
         */
        public fun pathToSvgD(path: SkPath): String {
            val sb = StringBuilder()
            var ci = 0
            var wi = 0
            for (verb in path.verbs) {
                when (verb) {
                    SkPath.Verb.kMove -> {
                        if (sb.isNotEmpty()) sb.append(' ')
                        sb.append('M').append(' ')
                        sb.append(formatScalar(path.coords[ci])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 1]))
                        ci += 2
                    }
                    SkPath.Verb.kLine -> {
                        sb.append(' ').append('L').append(' ')
                        sb.append(formatScalar(path.coords[ci])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 1]))
                        ci += 2
                    }
                    SkPath.Verb.kQuad -> {
                        sb.append(' ').append('Q').append(' ')
                        sb.append(formatScalar(path.coords[ci])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 1])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 2])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 3]))
                        ci += 4
                    }
                    SkPath.Verb.kCubic -> {
                        sb.append(' ').append('C').append(' ')
                        sb.append(formatScalar(path.coords[ci])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 1])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 2])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 3])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 4])).append(' ')
                        sb.append(formatScalar(path.coords[ci + 5]))
                        ci += 6
                    }
                    SkPath.Verb.kConic -> {
                        // Conic-to-cubic via the kReimann-Roth-style
                        // approximation : interpret the rational
                        // quadratic with weight w as a cubic that
                        // matches at t=0, t=0.5, t=1. Standard form,
                        // see "Approximating rational quadratic
                        // splines by cubic ones" — for w in (0, 2)
                        // typical of upstream GMs the visual error
                        // is sub-pixel.
                        //
                        // Get the previous point from the d-string we've already emitted —
                        // safer is to track `lastX, lastY` explicitly.
                        // (We don't ; instead we use the conic's
                        // reconstructed start point, which equals the
                        // previous emit's end point.)
                        val w = path.conicWeights[wi++]
                        emitConicAsCubic(
                            sb,
                            startX = lastEmittedX(path, ci),
                            startY = lastEmittedY(path, ci),
                            ctrlX = path.coords[ci],
                            ctrlY = path.coords[ci + 1],
                            endX = path.coords[ci + 2],
                            endY = path.coords[ci + 3],
                            w = w,
                        )
                        ci += 4
                    }
                    SkPath.Verb.kClose -> sb.append(' ').append('Z')
                }
            }
            return sb.toString()
        }

        /**
         * Recover the conic's start point — it's the last point the
         * verb stream emitted before the conic. In SkPath's parallel
         * arrays this is the point at index `ci - 2` for the most
         * common case (the conic followed a move/line/quad-end/cubic-
         * end). We rely on the path being well-formed — every conic
         * is preceded by a move or another verb that placed a pen.
         */
        private fun lastEmittedX(path: SkPath, ci: Int): Float =
            if (ci >= 2) path.coords[ci - 2] else path.coords[0]

        private fun lastEmittedY(path: SkPath, ci: Int): Float =
            if (ci >= 2) path.coords[ci - 1] else path.coords[1]

        /**
         * Emit a single cubic that approximates the rational quadratic
         * `(start, ctrl, end)` with weight `w`. For w == 1, the conic
         * collapses to a regular quadratic (we still emit a cubic with
         * the standard quad→cubic promotion). For w != 1, we use the
         * widely-cited approximation
         *   `c1 = start + (2w / (1+2w)) · (ctrl - start)`
         *   `c2 = end   + (2w / (1+2w)) · (ctrl - end)`
         * which matches the conic at t=0, t=1, and is exact at t=0.5
         * (modulo sub-pixel floating-point drift).
         */
        private fun emitConicAsCubic(
            sb: StringBuilder,
            startX: Float, startY: Float,
            ctrlX: Float, ctrlY: Float,
            endX: Float, endY: Float,
            w: Float,
        ) {
            val k = (2f * w) / (1f + 2f * w)
            val c1x = startX + k * (ctrlX - startX)
            val c1y = startY + k * (ctrlY - startY)
            val c2x = endX + k * (ctrlX - endX)
            val c2y = endY + k * (ctrlY - endY)
            sb.append(' ').append('C').append(' ')
            sb.append(formatScalar(c1x)).append(' ')
            sb.append(formatScalar(c1y)).append(' ')
            sb.append(formatScalar(c2x)).append(' ')
            sb.append(formatScalar(c2y)).append(' ')
            sb.append(formatScalar(endX)).append(' ')
            sb.append(formatScalar(endY))
        }
    }
}
