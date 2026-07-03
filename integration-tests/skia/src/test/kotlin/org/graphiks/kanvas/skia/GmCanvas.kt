package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.TextBlob
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

class GmCanvas(
    private val inner: Canvas,
    val width: Int,
    val height: Int,
) {
    private val transformStack = mutableListOf<Matrix33>()
    private val clipStack = mutableListOf<Rect?>()
    private val layerStack = mutableListOf<Boolean>()
    private var currentTransform = Matrix33.identity()
    private var currentClip: Rect? = null

    fun save() {
        transformStack.add(currentTransform)
        clipStack.add(currentClip)
        layerStack.add(false)
    }

    fun saveLayer(bounds: Rect? = null, paint: Paint? = null) {
        transformStack.add(currentTransform)
        clipStack.add(currentClip)
        layerStack.add(true)
        inner.saveLayer(bounds, paint)
    }

    fun restore() {
        currentTransform = transformStack.removeLast()
        currentClip = clipStack.removeLast()
        if (layerStack.removeLast()) {
            inner.restore()
        }
    }

    fun translate(dx: Float, dy: Float) {
        currentTransform = currentTransform * Matrix33.translate(dx, dy)
    }

    fun scale(sx: Float, sy: Float) {
        currentTransform = currentTransform * Matrix33.scale(sx, sy)
    }

    fun rotate(degrees: Float) {
        currentTransform = currentTransform * Matrix33.rotate(degrees)
    }

    fun skew(sx: Float, sy: Float) {
        currentTransform = currentTransform * Matrix33.skew(sx, sy)
    }

    fun concat(matrix: Matrix33) {
        currentTransform = currentTransform * matrix
    }

    fun setMatrix(matrix: Matrix33) {
        inner.setMatrix(matrix)
    }

    fun resetMatrix() {
        inner.resetMatrix()
    }

    fun clipRect(rect: Rect) {
        currentClip = if (currentClip != null) {
            intersectRects(currentClip!!, rect)
        } else {
            rect
        }
    }

    fun clipPath(path: Path, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        inner.clipPath(path, op, antiAlias)
    }

    fun clipRRect(rrect: RRect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        inner.clipRRect(rrect, op, antiAlias)
    }

    fun quickReject(rect: Rect): Boolean = inner.quickReject(rect)

    fun quickReject(path: Path): Boolean = inner.quickReject(path)

    private fun Matrix33.isIdentity(): Boolean =
        scaleX == 1f && skewX == 0f && transX == 0f &&
        skewY == 0f && scaleY == 1f && transY == 0f &&
        persp0 == 0f && persp1 == 0f && persp2 == 1f

    private fun transformRect(clip: Rect): Rect? {
        val t = currentTransform
        val p0 = t * Point(clip.left, clip.top)
        val p1 = t * Point(clip.right, clip.top)
        val p2 = t * Point(clip.right, clip.bottom)
        val p3 = t * Point(clip.left, clip.bottom)
        val l = min(min(p0.x, p1.x), min(p2.x, p3.x))
        val tp = min(min(p0.y, p1.y), min(p2.y, p3.y))
        val r = max(max(p0.x, p1.x), max(p2.x, p3.x))
        val b = max(max(p0.y, p1.y), max(p2.y, p3.y))
        return if (l < r && tp < b) Rect(l, tp, r, b) else null
    }

    private inline fun withClip(block: () -> Unit) {
        val clip = currentClip
        if (clip == null) {
            block()
            return
        }
        val innerRect = transformRect(clip) ?: return
        inner.save()
        inner.clipRect(innerRect)
        block()
        inner.restore()
    }

    fun drawRect(rect: Rect, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawRect(rect, paint)
            } else {
                val t = currentTransform
                val p0 = t * Point(rect.left, rect.top)
                val p1 = t * Point(rect.right, rect.top)
                val p2 = t * Point(rect.right, rect.bottom)
                val p3 = t * Point(rect.left, rect.bottom)
                val path = Path {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                inner.drawPath(path, paint)
            }
        }
    }

    fun drawPath(path: Path, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawPath(path, paint)
            } else {
                inner.drawPath(path.transform(currentTransform), paint)
            }
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

    fun drawRRect(rrect: RRect, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawRRect(rrect, paint)
            } else {
                val path = Path { }
                path.addRRect(rrect)
                inner.drawPath(path.transform(currentTransform), paint)
            }
        }
    }

    fun drawDRRect(outer: RRect, innerRect: RRect, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                this.inner.drawDRRect(outer, innerRect, paint)
            } else {
                val outerPath = Path { }.apply { addRRect(outer) }
                val ip = Path { }.apply { addRRect(innerRect) }
                val p = Path { }
                p.addPath(outerPath)
                p.addPath(ip)
                this.inner.drawPath(p.transform(currentTransform), paint)
            }
        }
    }

    fun drawPoints(mode: PointMode, points: List<Point>, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawPoints(mode, points, paint)
            } else {
                val transformed = points.map { currentTransform * it }
                inner.drawPoints(mode, transformed, paint)
            }
        }
    }

    fun drawPoint(x: Float, y: Float, paint: Paint) {
        withClip {
            val pt = currentTransform * Point(x, y)
            inner.drawPoint(pt.x, pt.y, paint)
        }
    }

    fun drawVertices(vertices: Vertices, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawVertices(vertices, paint)
            } else {
                val transformed = vertices.positions.map { currentTransform * it }
                inner.drawVertices(vertices.copy(positions = transformed), paint)
            }
        }
    }

    fun drawImage(image: Image, rect: Rect, paint: Paint? = null) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawImage(image, rect, paint)
            } else {
                val t = currentTransform
                val p0 = t * Point(rect.left, rect.top)
                val p1 = t * Point(rect.right, rect.bottom)
                val left = min(p0.x, p1.x)
                val top = min(p0.y, p1.y)
                val right = max(p0.x, p1.x)
                val bottom = max(p0.y, p1.y)
                inner.drawImage(image, Rect(left, top, right, bottom), paint)
            }
        }
    }

    fun drawImageRect(image: Image, src: Rect, dst: Rect, paint: Paint? = null) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawImageRect(image, src, dst, paint)
            } else {
                val t = currentTransform
                val p0 = t * Point(dst.left, dst.top)
                val p1 = t * Point(dst.right, dst.bottom)
                val left = min(p0.x, p1.x)
                val top = min(p0.y, p1.y)
                val right = max(p0.x, p1.x)
                val bottom = max(p0.y, p1.y)
                inner.drawImageRect(image, src, Rect(left, top, right, bottom), paint)
            }
        }
    }

    fun drawImageNine(image: Image, center: Rect, dst: Rect, paint: Paint? = null) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawImageNine(image, center, dst, paint)
            } else {
                val p0 = currentTransform * Point(dst.left, dst.top)
                val p1 = currentTransform * Point(dst.right, dst.bottom)
                val tdst = Rect(min(p0.x, p1.x), min(p0.y, p1.y), max(p0.x, p1.x), max(p0.y, p1.y))
                inner.drawImageNine(image, center, tdst, paint)
            }
        }
    }

    fun drawImageLattice(image: Image, lattice: Lattice, dst: Rect, paint: Paint? = null) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawImageLattice(image, lattice, dst, paint)
            } else {
                val p0 = currentTransform * Point(dst.left, dst.top)
                val p1 = currentTransform * Point(dst.right, dst.bottom)
                val tdst = Rect(min(p0.x, p1.x), min(p0.y, p1.y), max(p0.x, p1.x), max(p0.y, p1.y))
                inner.drawImageLattice(image, lattice, tdst, paint)
            }
        }
    }

    fun drawAtlas(
        atlas: Image,
        transforms: List<Matrix33>,
        texRects: List<Rect>,
        colors: List<Color>? = null,
        blendMode: BlendMode = BlendMode.SRC_OVER,
        paint: Paint? = null,
    ) {
        withClip {
            inner.drawAtlas(atlas, transforms, texRects, colors, blendMode, paint)
        }
    }

    fun drawString(str: String, x: Float, y: Float, font: Font, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawString(str, x, y, font, paint)
            } else {
                inner.save()
                inner.concat(currentTransform)
                inner.drawString(str, x, y, font, paint)
                inner.restore()
            }
        }
    }

    fun drawSimpleText(text: String, x: Float, y: Float, font: Font, paint: Paint) {
        drawString(text, x, y, font, paint)
    }

    fun drawGlyphs(glyphIds: List<Int>, positions: List<Point>, font: Font, paint: Paint) {
        require(glyphIds.size == positions.size)
        for (i in glyphIds.indices) {
            val gid = glyphIds[i]
            val pos = positions[i]
            val glyphPath = font.typeface.getGlyphPath(gid, font.size) ?: continue
            val offsetPath = glyphPath.transform(pos.x, pos.y, 1f, 1f)
            drawPath(offsetPath, paint)
        }
    }

    fun drawTextBlob(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawText(blob, x, y, paint)
            } else {
                inner.save()
                inner.concat(currentTransform)
                inner.drawText(blob, x, y, paint)
                inner.restore()
            }
        }
    }

    fun drawPicture(picture: Picture, paint: Paint? = null) {
        withClip {
            inner.drawPicture(picture, paint)
        }
    }

    private companion object {
        private fun intersectRects(a: Rect, b: Rect): Rect? {
            val l = max(a.left, b.left)
            val t = max(a.top, b.top)
            val r = min(a.right, b.right)
            val bt = min(a.bottom, b.bottom)
            return if (l < r && t < bt) Rect(l, t, r, bt) else null
        }
    }
}
