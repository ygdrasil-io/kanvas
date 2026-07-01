package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import org.graphiks.kanvas.picture.PictureRecorder
import kotlin.math.PI

/** Draw an oval inscribed in [rect] filled/stroked with [paint]. */
fun Canvas.drawOval(rect: Rect, paint: Paint) {
    this.drawPath(Path().addOval(rect), paint)
}

/** Draw a circle centered at (cx, cy) with the given [radius] filled/stroked with [paint]. */
fun Canvas.drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
    this.drawPath(Path().addCircle(cx, cy, radius), paint)
}

/**
 * Draw an arc of an ellipse inscribed in [rect].
 *
 * @param startAngle Starting angle in degrees.
 * @param sweepAngle Angular sweep in degrees (positive for clockwise).
 * @param useCenter  When true, draws a pie-slice shape (radii to center).
 */
fun Canvas.drawArc(rect: Rect, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
    val path = Path()
    val cx = rect.center.x; val cy = rect.center.y
    val rx = rect.width / 2f; val ry = rect.height / 2f
    val startRad = startAngle * PI.toFloat() / 180f
    val sweepRad = sweepAngle * PI.toFloat() / 180f
    val endRad = startRad + sweepRad
    val largeArc = kotlin.math.abs(sweepAngle) >= 180f
    val sweep = sweepAngle > 0f
    val sx = cx + rx * kotlin.math.cos(startRad)
    val sy = cy + ry * kotlin.math.sin(startRad)
    val ex = cx + rx * kotlin.math.cos(endRad)
    val ey = cy + ry * kotlin.math.sin(endRad)
    if (useCenter) path.moveTo(cx, cy)
    path.moveTo(sx, sy)
    path.arcTo(rx, ry, 0f, largeArc, sweep, ex, ey)
    if (useCenter) path.close()
    drawPath(path, paint)
}

/** Draw a line from (x0, y0) to (x1, y1) with the given stroke [paint]. */
fun Canvas.drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
    this.drawPath(Path().apply { moveTo(x0, y0); lineTo(x1, y1) }, paint)
}

/** Draw a rectangle with rounded corners using corner radii (rx, ry). */
fun Canvas.drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint) {
    val r = CornerRadii(rx, ry)
    drawRRect(RRect(rect, r, r, r, r), paint)
}

/**
 * Draw a Coons patch defined by 12 control points (4 cubic curves).
 *
 * @param cubics 12 control points for the 4 boundary curves (top, right, bottom, left).
 * @param colors Optional corner colors for interpolation (top-left, top-right, bottom-right, bottom-left).
 * @param texCoords Optional texture coordinates at each corner.
 * @param paint The paint used for filling.
 */
fun Canvas.drawPatch(
    cubics: List<Point>,
    colors: List<Color>? = null,
    texCoords: List<Point>? = null,
    paint: Paint,
) {
    // Tessellate Coons patch into a triangle mesh (4x4 subdivision = 32 triangles)
    val w = 4
    val positions = mutableListOf<Point>()
    val vColors = colors?.let { mutableListOf<Color>() }
    val vTexs = texCoords?.let { mutableListOf<Point>() }
    val tl = colors?.getOrNull(0); val tr = colors?.getOrNull(1)
    val br = colors?.getOrNull(2); val bl = colors?.getOrNull(3)
    val ttl = texCoords?.getOrNull(0); val ttr = texCoords?.getOrNull(1)
    val tbr = texCoords?.getOrNull(2); val tbl = texCoords?.getOrNull(3)
    for (v in 0..w) {
        val vv = v.toFloat() / w
        for (u in 0..w) {
            val uu = u.toFloat() / w
            positions.add(evalCubicPatch(cubics, uu, vv))
            if (tl != null && tr != null && br != null && bl != null) {
                val ctl = Color.fromRGBA(tl.r, tl.g, tl.b, tl.a)
                val ctr = Color.fromRGBA(tr.r, tr.g, tr.b, tr.a)
                val cbr = Color.fromRGBA(br.r, br.g, br.b, br.a)
                val cbl = Color.fromRGBA(bl.r, bl.g, bl.b, bl.a)
                vColors!!.add(Color.fromRGBA(
                    lerp(lerp(ctl.r, ctr.r, uu), lerp(cbl.r, cbr.r, uu), vv),
                    lerp(lerp(ctl.g, ctr.g, uu), lerp(cbl.g, cbr.g, uu), vv),
                    lerp(lerp(ctl.b, ctr.b, uu), lerp(cbl.b, cbr.b, uu), vv),
                    lerp(lerp(ctl.a, ctr.a, uu), lerp(cbl.a, cbr.a, uu), vv),
                ))
            }
            if (ttl != null && ttr != null && tbr != null && tbl != null) {
                vTexs!!.add(Point(
                    lerp(lerp(ttl.x, ttr.x, uu), lerp(tbl.x, tbr.x, uu), vv),
                    lerp(lerp(ttl.y, ttr.y, uu), lerp(tbl.y, tbr.y, uu), vv),
                ))
            }
        }
    }
    val cols = w + 1
    val indices = mutableListOf<Int>()
    for (v in 0 until w) {
        for (u in 0 until w) {
            val a = v * cols + u; val b = a + 1; val c = a + cols; val d = c + 1
            indices.addAll(listOf(a, b, c, b, d, c))
        }
    }
    drawVertices(Vertices(VertexMode.TRIANGLES, positions, vTexs, vColors, indices), paint)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun evalCubicPatch(cubics: List<Point>, u: Float, v: Float): Point {
    val pts = cubics.toList()
    val omu = 1f - u; val omv = 1f - v
    val top = cubicAt(pts[0], pts[1], pts[2], pts[3], u)
    val bot = cubicAt(pts[9], pts[10], pts[11], pts[0], u) // reuse corner
    val left = cubicAt(pts[0], pts[6], pts[7], pts[3], v) // approximate
    val right = cubicAt(pts[3], pts[4], pts[5], pts[9], v) // approximate
    return Point(
        omv * top.x + v * bot.x,
        omv * top.y + v * bot.y,
    )
}

private fun cubicAt(p0: Point, p1: Point, p2: Point, p3: Point, t: Float): Point {
    val omt = 1f - t; val omt2 = omt * omt; val t2 = t * t
    return Point(
        omt2 * omt * p0.x + 3 * omt2 * t * p1.x + 3 * omt * t2 * p2.x + t2 * t * p3.x,
        omt2 * omt * p0.y + 3 * omt2 * t * p1.y + 3 * omt * t2 * p2.y + t2 * t * p3.y,
    )
}

/** Draw an [image] at position (x, y) using its natural dimensions. */
fun Canvas.drawImage(image: Image, x: Float, y: Float, paint: Paint? = null) {
    this.drawImage(image, Rect.fromXYWH(x, y, image.width.toFloat(), image.height.toFloat()), paint)
}

/**
 * Save the current state, execute [block], and restore.
 *
 * Equivalent to [Canvas.save] / [Canvas.restore] in a try/finally wrapper.
 */
inline fun Canvas.withSave(block: Canvas.() -> Unit) {
    this.save(); try { block() } finally { this.restore() }
}

/**
 * Save a new offscreen layer, execute [block], and restore.
 *
 * @param bounds Optional bounds for the layer surface.
 * @param paint  Optional compositing [Paint].
 */
inline fun Canvas.withSaveLayer(bounds: Rect? = null, paint: Paint? = null, block: Canvas.() -> Unit) {
    this.saveLayer(bounds, paint); try { block() } finally { this.restore() }
}

/** Push an axis-aligned rectangular clip, execute [block], then restore the previous clip. */
inline fun Canvas.withClipRect(rect: Rect, block: Canvas.() -> Unit) {
    save(); clipRect(rect); try { block() } finally { restore() }
}

/** Push a path clip, execute [block], then restore the previous clip. */
inline fun Canvas.withClipPath(path: Path, block: Canvas.() -> Unit) {
    save(); clipPath(path); try { block() } finally { restore() }
}

/**
 * Save the current transform, execute [block], and restore.
 *
 * The clip stack is saved as a side effect of [Canvas.save].
 */
inline fun Canvas.withTransform(block: Canvas.() -> Unit) {
    this.save(); try { block() } finally { this.restore() }
}

/**
 * Execute [block] on a temporary picture recorded within [bounds],
 * then draw the resulting picture onto this canvas.
 */
fun Canvas.withPicture(bounds: Rect, paint: Paint? = null, block: Canvas.() -> Unit) {
    val recorder = PictureRecorder()
    val pictureCanvas = recorder.beginRecording(bounds)
    pictureCanvas.block()
    drawPicture(recorder.finishRecordingAsPicture(), paint)
}
