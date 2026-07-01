package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
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
