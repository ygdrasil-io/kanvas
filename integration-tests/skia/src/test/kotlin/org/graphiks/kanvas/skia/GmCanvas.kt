package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class GmCanvas(
    private val inner: Canvas,
    val width: Int,
    val height: Int,
) {
    private val transformStack = mutableListOf<Transform>()
    private var currentTransform = Transform()

    private data class Transform(
        val tx: Float = 0f,
        val ty: Float = 0f,
        val sx: Float = 1f,
        val sy: Float = 1f,
    )

    fun save() {
        transformStack.add(currentTransform)
    }

    fun restore() {
        currentTransform = transformStack.removeLast()
    }

    fun translate(dx: Float, dy: Float) {
        currentTransform = currentTransform.copy(
            tx = currentTransform.tx + dx * currentTransform.sx,
            ty = currentTransform.ty + dy * currentTransform.sy,
        )
    }

    fun scale(sx: Float, sy: Float) {
        currentTransform = currentTransform.copy(
            sx = currentTransform.sx * sx,
            sy = currentTransform.sy * sy,
        )
    }

    fun clipRect(rect: Rect) {
        // no-op stub
    }

    fun drawRect(rect: Rect, paint: Paint) {
        if (currentTransform == Transform()) {
            inner.drawRect(rect, paint)
        } else {
            val t = currentTransform
            val path = Path {
                moveTo(rect.left * t.sx + t.tx, rect.top * t.sy + t.ty)
                lineTo(rect.right * t.sx + t.tx, rect.top * t.sy + t.ty)
                lineTo(rect.right * t.sx + t.tx, rect.bottom * t.sy + t.ty)
                lineTo(rect.left * t.sx + t.tx, rect.bottom * t.sy + t.ty)
                close()
            }
            inner.drawPath(path, paint)
        }
    }

    fun drawPath(path: Path, paint: Paint) {
        if (currentTransform == Transform()) {
            inner.drawPath(path, paint)
        } else {
            val t = currentTransform
            inner.drawPath(path.transform(t.tx, t.ty, t.sx, t.sy), paint)
        }
    }

    fun drawColor(r: Float, g: Float, b: Float, a: Float = 1f) {
        drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(r, g, b, a)),
        )
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path { }
        path.addCircle(cx, cy, radius)
        drawPath(path, paint)
    }

    fun drawOval(rect: Rect, paint: Paint) {
        val path = Path { }
        path.addOval(rect)
        drawPath(path, paint)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        drawPath(Path { moveTo(x1, y1); lineTo(x2, y2) }, paint)
    }

    fun drawArc(rect: Rect, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
        val cx = rect.left + rect.width / 2f
        val cy = rect.top + rect.height / 2f
        val rx = rect.width / 2f
        val ry = rect.height / 2f
        val startRad = Math.toRadians(startAngle.toDouble()).toFloat()
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()
        val x1 = cx + rx * cos(startRad)
        val y1 = cy + ry * sin(startRad)
        val x2 = cx + rx * cos(endRad)
        val y2 = cy + ry * sin(endRad)
        val largeArc = kotlin.math.abs(sweepAngle) > 180f
        val sweep = sweepAngle > 0f

        val path = Path {
            if (useCenter) moveTo(cx, cy) else moveTo(x1, y1)
            arcTo(rx, ry, 0f, largeArc, sweep, x2, y2)
            if (useCenter) close()
        }
        drawPath(path, paint)
    }

    fun drawImage(image: org.graphiks.kanvas.image.Image, rect: Rect, paint: Paint? = null) {
        if (currentTransform == Transform()) {
            inner.drawImage(image, rect, paint)
        } else {
            val t = currentTransform
            val transformed = Rect(
                rect.left * t.sx + t.tx,
                rect.top * t.sy + t.ty,
                rect.right * t.sx + t.tx,
                rect.bottom * t.sy + t.ty,
            )
            inner.drawImage(image, transformed, paint)
        }
    }
}
