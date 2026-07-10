package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
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
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontMetrics
import org.graphiks.kanvas.text.FontMetricsProvider
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.math.SkColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

enum class TextAlign(val factor: Float) {
    LEFT(0f),
    CENTER(0.5f),
    RIGHT(1f),
}

private const val PORTABLE_FONT_RESOURCE = "fonts/LiberationSans-Regular.ttf"
private const val PORTABLE_FONT_FALLBACK_RESOURCE = "fonts/liberation/LiberationSans-Regular.ttf"

private val portableTypeface: Typeface by lazy {
    Typefaces.fromResource(PORTABLE_FONT_RESOURCE)
        ?: Typefaces.fromResource(PORTABLE_FONT_FALLBACK_RESOURCE)
        ?: MissingPortableTypeface
}

private object MissingPortableTypeface : Typeface, FontMetricsProvider {
    override val fontName: String = "portable-font-missing-stub"

    override fun glyphIdForCodepoint(codepoint: Int): Int = codepoint

    override fun getAdvance(glyphId: Int, fontSize: Float): Float = fontSize * 0.5f

    override fun getGlyphPath(glyphId: Int, fontSize: Float): Path? = null

    override fun getMetrics(size: Float): FontMetrics = FontMetrics(
        ascent = -0.8f * size,
        descent = 0.2f * size,
        leading = 0f,
        xHeight = 0.5f * size,
        capHeight = 0.7f * size,
    )
}

private fun alignedTextX(text: String, x: Float, font: Font, alignment: Float): Float =
    x - font.measureText(text) * alignment

fun portableFont(size: Float, bold: Boolean = false): Font = Font(
    typeface = portableTypeface,
    size = size,
    subpixel = true,
    isEmbolden = bold,
)

class GmCanvas(
    private val inner: Canvas,
    val width: Int,
    val height: Int,
) {
    private val transformStack = mutableListOf<Matrix33>()
    private val clipStack = mutableListOf<Rect?>()
    private var currentTransform = Matrix33.identity()
    private var currentClip: Rect? = null

    fun save() {
        transformStack.add(currentTransform)
        clipStack.add(currentClip)
        inner.save()
    }

    fun saveLayer(bounds: Rect? = null, paint: Paint? = null) {
        transformStack.add(currentTransform)
        clipStack.add(currentClip)
        inner.saveLayer(bounds, paint)
    }

    fun saveLayer(rec: SaveLayerRec) {
        transformStack.add(currentTransform)
        clipStack.add(currentClip)
        inner.saveLayer(rec)
    }

    fun makeImageSnapshot(): Image {
        return inner.flushAndSnapshot(Rect(0f, 0f, width.toFloat(), height.toFloat()))
    }

    fun restore() {
        currentTransform = transformStack.removeLast()
        currentClip = clipStack.removeLast()
        inner.restore()
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
        val transformedPath = if (currentTransform.isIdentity()) path else path.transform(currentTransform)
        inner.clipPath(transformedPath, op, antiAlias)
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

    fun drawColor(
        r: Float,
        g: Float,
        b: Float,
        a: Float = 1f,
        mode: BlendMode = BlendMode.SRC_OVER,
    ) {
        withClip {
            inner.drawColor(Color.fromRGBA(r, g, b, a), mode)
        }
    }

    fun clear(color: Color) {
        inner.clear(color)
    }

    fun clear(color: SkColor) {
        clear(Color.fromArgbInt(color))
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

    fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null) {
        withClip {
            if (currentTransform.isIdentity()) {
                inner.drawMesh(mesh, paint, blendMode)
            } else {
                val transformed = mesh.vertices.positions.map { currentTransform * it }
                val transformedVerts = mesh.vertices.copy(positions = transformed)
                inner.drawMesh(mesh.copy(vertices = transformedVerts), paint, blendMode)
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

    fun drawStringAligned(
        str: String,
        x: Float,
        y: Float,
        font: Font,
        paint: Paint,
        alignment: Float = TextAlign.LEFT.factor,
    ) {
        drawString(str, alignedTextX(str, x, font, alignment), y, font, paint)
    }

    fun drawStringAligned(
        str: String,
        x: Float,
        y: Float,
        font: Font,
        paint: Paint,
        alignment: TextAlign,
    ) {
        drawStringAligned(str, x, y, font, paint, alignment.factor)
    }

    fun drawSimpleText(text: String, x: Float, y: Float, font: Font, paint: Paint) {
        drawString(text, x, y, font, paint)
    }

    /** Draw individual glyphs at explicit positions. Renders monochrome outlines
     * via the path pipeline; color glyph layers require the GPU text pipeline. */
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

    /**
     * Draw a Coons patch (cubic Bézier surface patch).
     *
     * @param cubics    12 control points forming 4 cubic Bézier edges:
     *                  [corner0, cp1_edge0, cp2_edge0, corner1, cp1_edge1, cp2_edge1,
     *                   corner2, cp1_edge2, cp2_edge2, corner3, cp1_edge3, cp2_edge3]
     * @param colors    4 corner colors for vertex interpolation (optional)
     * @param texCoords 4 corner texture coordinates (optional)
     * @param blendMode blend mode for the patch
     * @param paint     paint (shader is sampled at texCoords when provided)
     */
    fun drawPatch(
        cubics: List<Point>,
        colors: List<Color>? = null,
        texCoords: List<Point>? = null,
        blendMode: BlendMode = BlendMode.SRC_OVER,
        paint: Paint,
    ) {
        require(cubics.size == 12) { "drawPatch requires 12 control points" }
        val corners = listOf(cubics[0], cubics[3], cubics[6], cubics[9])
        val curves = listOf(
            CubicEdge(cubics[0], cubics[1], cubics[2], cubics[3]),
            CubicEdge(cubics[3], cubics[4], cubics[5], cubics[6]),
            CubicEdge(cubics[6], cubics[7], cubics[8], cubics[9]),
            CubicEdge(cubics[9], cubics[10], cubics[11], cubics[0]),
        )
        val divisions = 20
        val verts = mutableListOf<Point>()
        val vertColors = mutableListOf<Color>()
        val vertTexCoords = mutableListOf<Point>()
        val indices = mutableListOf<Int>()

        for (j in 0..divisions) {
            val v = j.toFloat() / divisions
            for (i in 0..divisions) {
                val u = i.toFloat() / divisions
                val pu = coonsPatchPoint(u, v, curves, corners)
                verts.add(currentTransform * pu)
                if (colors != null) {
                    vertColors.add(bilinearInterp(u, v, colors))
                }
                if (texCoords != null) {
                    vertTexCoords.add(bilinearInterp(u, v, texCoords))
                }
                if (i < divisions && j < divisions) {
                    val idx = j * (divisions + 1) + i
                    indices.add(idx)
                    indices.add(idx + 1)
                    indices.add(idx + divisions + 1)
                    indices.add(idx + 1)
                    indices.add(idx + divisions + 2)
                    indices.add(idx + divisions + 1)
                }
            }
        }

        val finalColors = vertColors.ifEmpty { null }
        val finalTexCoords = vertTexCoords.ifEmpty { null }
        val vertices = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = verts,
            texCoords = finalTexCoords,
            colors = finalColors,
            indices = indices,
        )

        val currentClip = currentClip
        if (currentClip != null) {
            val innerRect = transformRect(currentClip) ?: return
            inner.save()
            inner.clipRect(innerRect)
        }
        inner.drawVertices(vertices, paint)
        if (currentClip != null) {
            inner.restore()
        }
    }

    private fun coonsPatchPoint(
        u: Float, v: Float,
        curves: List<CubicEdge>,
        corners: List<Point>,
    ): Point {
        val top = curves[0].eval(u)
        val bottom = curves[2].eval(u)
        val left = curves[3].eval(v)
        val right = curves[1].eval(v)
        val bilinear = Point(
            corners[0].x * (1 - u) * (1 - v) + corners[1].x * u * (1 - v) +
            corners[2].x * u * v + corners[3].x * (1 - u) * v,
            corners[0].y * (1 - u) * (1 - v) + corners[1].y * u * (1 - v) +
            corners[2].y * u * v + corners[3].y * (1 - u) * v,
        )
        return Point(
            (1 - v) * top.x + v * bottom.x + (1 - u) * left.x + u * right.x - bilinear.x,
            (1 - v) * top.y + v * bottom.y + (1 - u) * left.y + u * right.y - bilinear.y,
        )
    }

    private fun Color.rf(): Float = ((packed shr 16) and 0xFFu).toFloat() / 255f
    private fun Color.gf(): Float = ((packed shr 8) and 0xFFu).toFloat() / 255f
    private fun Color.bf(): Float = (packed and 0xFFu).toFloat() / 255f
    private fun Color.af(): Float = ((packed shr 24) and 0xFFu).toFloat() / 255f

    private fun bilinearInterp(u: Float, v: Float, colors: List<Color>): Color {
        val c00 = colors[0]; val c10 = colors[1]
        val c01 = colors[3]; val c11 = colors[2]
        val a = (1 - u) * (1 - v) * c00.af() + u * (1 - v) * c10.af() +
                (1 - u) * v * c01.af() + u * v * c11.af()
        val r = (1 - u) * (1 - v) * c00.rf() + u * (1 - v) * c10.rf() +
                (1 - u) * v * c01.rf() + u * v * c11.rf()
        val g = (1 - u) * (1 - v) * c00.gf() + u * (1 - v) * c10.gf() +
                (1 - u) * v * c01.gf() + u * v * c11.gf()
        val b = (1 - u) * (1 - v) * c00.bf() + u * (1 - v) * c10.bf() +
                (1 - u) * v * c01.bf() + u * v * c11.bf()
        return Color.fromRGBA(r, g, b, a)
    }

    private fun bilinearInterp(u: Float, v: Float, texCoords: List<Point>): Point {
        val t00 = texCoords[0]; val t10 = texCoords[1]
        val t01 = texCoords[3]; val t11 = texCoords[2]
        return Point(
            (1 - u) * (1 - v) * t00.x + u * (1 - v) * t10.x +
            (1 - u) * v * t01.x + u * v * t11.x,
            (1 - u) * (1 - v) * t00.y + u * (1 - v) * t10.y +
            (1 - u) * v * t01.y + u * v * t11.y,
        )
    }

    private data class CubicEdge(val p0: Point, val p1: Point, val p2: Point, val p3: Point) {
        fun eval(t: Float): Point {
            val t1 = 1f - t
            return Point(
                t1 * t1 * t1 * p0.x + 3 * t1 * t1 * t * p1.x + 3 * t1 * t * t * p2.x + t * t * t * p3.x,
                t1 * t1 * t1 * p0.y + 3 * t1 * t1 * t * p1.y + 3 * t1 * t * t * p2.y + t * t * t * p3.y,
            )
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
