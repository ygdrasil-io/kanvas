package org.graphiks.kanvas.skia

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.PaintStyle
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.StrokeCap
import org.graphiks.kanvas.StrokeJoin
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
        // no-op stub — Kanvas Canvas does not expose GPU-side clip.
    }

    fun drawRect(rect: Rect, paint: Paint) {
        if (currentTransform == Transform()) {
            inner.drawRect(rect, paint)
        } else {
            val t = currentTransform
            val path = Path().apply {
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
        drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply {
            this.r = r; this.g = g; this.b = b; this.a = a
        })
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        drawPath(Path().addCircle(cx, cy, radius), paint)
    }

    fun drawOval(rect: Rect, paint: Paint) {
        drawPath(Path().addOval(rect), paint)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, paint)
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

        val path = Path()
        if (useCenter) path.moveTo(cx, cy) else path.moveTo(x1, y1)
        path.arcTo(rx, ry, 0f, largeArc, sweep, x2, y2)
        if (useCenter) path.close()
        drawPath(path, paint)
    }

    fun drawImage(image: org.graphiks.kanvas.Image, rect: Rect, paint: Paint? = null) {
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
