package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

fun Canvas.drawOval(rect: Rect, paint: Paint) {
    this.drawPath(Path().addOval(rect), paint)
}
fun Canvas.drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
    this.drawPath(Path().addCircle(cx, cy, radius), paint)
}
fun Canvas.drawArc(rect: Rect, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
    val path = Path()
    val cx = rect.center.x; val cy = rect.center.y
    val rx = rect.width / 2f; val ry = rect.height / 2f
    path.moveTo(cx, cy)
    path.arcTo(rx, ry, 0f, false, sweepAngle > 0, cx + rx, cy)
    if (useCenter) path.close()
    this.drawPath(path, paint)
}
fun Canvas.drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
    this.drawPath(Path().apply { moveTo(x0, y0); lineTo(x1, y1) }, paint)
}
fun Canvas.drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint) {
    val rrect = RRect(rect, rx)
    this.drawRRect(rrect, paint)
}
fun Canvas.drawImage(image: Image, x: Float, y: Float, paint: Paint? = null) {
    this.drawImage(image, Rect.fromXYWH(x, y, image.width.toFloat(), image.height.toFloat()), paint)
}
inline fun Canvas.withSave(block: Canvas.() -> Unit) {
    this.save(); try { block() } finally { this.restore() }
}
inline fun Canvas.withSaveLayer(bounds: Rect? = null, paint: Paint? = null, block: Canvas.() -> Unit) {
    this.saveLayer(bounds, paint); try { block() } finally { this.restore() }
}
inline fun Canvas.withClipRect(rect: Rect, block: Canvas.() -> Unit) {
    this.clipRect(rect); try { block() } finally { }
}
inline fun Canvas.withClipPath(path: Path, block: Canvas.() -> Unit) {
    this.clipPath(path); try { block() } finally { }
}
inline fun Canvas.withTransform(block: Canvas.() -> Unit) {
    this.save(); try { block() } finally { this.restore() }
}
