package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.*
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.paint.BlendMode

/**
 * A immediate-mode style 2D drawing surface that records drawing operations into
 * a [DisplayListBuffer] for deferred playback.
 *
 * [Canvas] manages a stack of transform and clip state, offering a Skia-like API
 * for drawing rectangles, paths, images, and text. All operations are appended to
 * the internal buffer for subsequent rendering by a pipeline consumer.
 */
class Canvas internal constructor(private val buffer: DisplayListBuffer) {
    private var currentTransform = Matrix33.identity()
    private var currentClip: ClipStack = ClipStack.WideOpen
    private var saveStack = mutableListOf<Pair<CanvasState, Boolean>>() // (state, isLayer)

    /** The current transform matrix. */
    val matrix: Matrix33 get() = currentTransform

    /** The number of states on the save stack. */
    val saveCount: Int get() = saveStack.size

    /**
     * The local clip bounds, expressed in the current coordinate system.
     *
     * Returns [Rect.EMPTY] when the clip is wide-open, the device rect when
     * clipping to a single axis-aligned rectangle, or [Rect.EMPTY] for complex
     * clip stacks.
     */
    val localClipBounds: Rect
        get() = when (val clip = currentClip) {
            ClipStack.WideOpen -> Rect.EMPTY
            is ClipStack.DeviceRect -> clip.rect
            is ClipStack.Complex -> Rect.EMPTY
        }

    /**
     * Return true if [rect] is fully outside the current clip.
     * Returns false for complex clips (conservative: may draw).
     */
    fun quickReject(rect: Rect): Boolean {
        if (currentClip is ClipStack.WideOpen) return false
        if (currentClip is ClipStack.DeviceRect) {
            val c = (currentClip as ClipStack.DeviceRect).rect
            return rect.right <= c.left || rect.left >= c.right ||
                   rect.bottom <= c.top || rect.top >= c.bottom
        }
        return false
    }

    /** Return true if [path]'s bounds are fully outside the current clip. */
    fun quickReject(path: Path): Boolean {
        // Compute conservative bounds from path points
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (pt in path.points()) {
            if (pt.x < minX) minX = pt.x; if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x; if (pt.y > maxY) maxY = pt.y
        }
        if (minX == Float.MAX_VALUE) return false
        return quickReject(Rect.fromLTRB(minX, minY, maxX, maxY))
    }

    /** True if the current clip region is empty (nothing visible). */
    val isClipEmpty: Boolean get() = currentClip.isEmpty

    /** True if the current clip is a single axis-aligned rectangle. */
    val isClipRect: Boolean get() = currentClip.isRect

    /** Draw an axis-aligned rectangle filled/stroked with [paint]. */
    fun drawRect(rect: Rect, paint: Paint) {
        buffer.append(DisplayOp.DrawRect(rect, paint, currentTransform, currentClip))
    }

    /** Draw a rounded rectangle filled/stroked with [paint]. */
    fun drawRRect(rrect: RRect, paint: Paint) {
        buffer.append(DisplayOp.DrawRRect(rrect, paint, currentTransform, currentClip))
    }

    /** Draw an arbitrary [path] filled/stroked with [paint]. */
    fun drawPath(path: Path, paint: Paint) {
        buffer.append(DisplayOp.DrawPath(path, paint, currentTransform, currentClip))
    }

    /**
     * Draw an [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImage(image: Image, dst: Rect, paint: Paint? = null) {
        val src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }

    /**
     * Draw a sub-region [src] of [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImageRect(image: Image, src: Rect, dst: Rect, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }

    /** Draw a [TextBlob] at the given position with [paint]. */
    fun drawText(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        buffer.append(DisplayOp.DrawText(blob, x, y, paint, currentTransform, currentClip))
    }

    /** Draw a string at (x, y) using [font], rendered with [paint]. */
    fun drawString(str: String, x: Float, y: Float, font: Font, paint: Paint) {
        val blob = font.toTextBlob(str, 0f, 0f)
        drawText(blob, x, y, paint)
    }

    /** Measure the advance width of [str] when set in [font]. */
    fun measureText(str: String, font: Font): Float {
        return font.measureText(str)
    }

    /** Fill the entire canvas with [color] using optional [mode] (default: SRC_OVER). */
    fun drawColor(color: Color, mode: BlendMode = BlendMode.SRC_OVER) {
        buffer.append(DisplayOp.DrawColor(color, mode, currentTransform, currentClip))
    }

    /** Overwrite the entire canvas with [color]. */
    fun clear(color: Color) {
        buffer.append(DisplayOp.Clear(color))
    }

    /** Draw a single point at (x, y). */
    fun drawPoint(x: Float, y: Float, paint: Paint) {
        buffer.append(DisplayOp.DrawPoint(x, y, paint, currentTransform, currentClip))
    }

    /** Draw a list of [points] with the given point [mode]. */
    fun drawPoints(mode: PointMode, points: List<Point>, paint: Paint) {
        buffer.append(DisplayOp.DrawPoints(mode, points, paint, currentTransform, currentClip))
    }

    /** Draw a double rounded rectangle (outer fill, inner hole). */
    fun drawDRRect(outer: RRect, inner: RRect, paint: Paint) {
        buffer.append(DisplayOp.DrawDRRect(outer, inner, paint, currentTransform, currentClip))
    }

    /** Draw a 9-patch [image] with [center] defining corner sizes, scaled to [dst]. */
    fun drawImageNine(image: Image, center: Rect, dst: Rect, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImageNine(image, center, dst, paint, currentTransform, currentClip))
    }

    /** Draw a lattice [image] over a grid defined by [lattice], scaled to [dst]. */
    fun drawImageLattice(image: Image, lattice: Lattice, dst: Rect, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImageLattice(image, lattice, dst, paint, currentTransform, currentClip))
    }

    /** Draw a pre-recorded [picture] with optional [paint] modulation. */
    fun drawPicture(picture: Picture, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawPicture(picture, paint, currentTransform, currentClip))
    }

    /** Draw a triangle mesh from [vertices]. */
    fun drawVertices(vertices: Vertices, paint: Paint) {
        buffer.append(DisplayOp.DrawVertices(vertices, paint, currentTransform, currentClip))
    }

    fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null) {
        if (mesh.program != null) {
            buffer.append(DisplayOp.DrawMesh(mesh, paint, blendMode, currentTransform, currentClip))
        } else {
            drawVertices(mesh.vertices, paint)
        }
    }

    /** Batch-draw sprites from [atlas] texture. */
    fun drawAtlas(atlas: Image, transforms: List<Matrix33>, texRects: List<Rect>, colors: List<Color>? = null, blendMode: BlendMode = BlendMode.SRC_OVER, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawAtlas(atlas, transforms, texRects, colors, blendMode, paint, currentTransform, currentClip))
    }

    /** Add a metadata annotation (no visual output). */
    fun drawAnnotation(rect: Rect, key: String, value: String) {
        buffer.append(DisplayOp.Annotation(rect, key, value))
    }

    /**
     * Save the current transform and clip state onto the stack.
     *
     * @return The new save count (depth of the save stack).
     */
    fun save(): Int {
        saveStack.add(CanvasState(currentTransform, currentClip) to false)
        return saveStack.size
    }

    /**
     * Save state and begin a new layer (offscreen render target).
     *
     * @param bounds Optional bounds for the offscreen surface.
     * @param paint  Optional [Paint] to apply when compositing the layer back.
     * @return The new save count.
     */
    fun saveLayer(bounds: Rect? = null, paint: Paint? = null): Int {
        return saveLayer(SaveLayerRec(bounds, paint))
    }

    /** Save state and begin a layer described by [rec], including an optional backdrop filter. */
    fun saveLayer(rec: SaveLayerRec): Int {
        buffer.append(DisplayOp.BeginLayer(rec))
        saveStack.add(CanvasState(currentTransform, currentClip) to true)
        return saveStack.size
    }

    /** Restore the most recently saved state and end the current layer if one was active. */
    fun restore() {
        if (saveStack.isNotEmpty()) {
            val (state, isLayer) = saveStack.removeLast()
            currentTransform = state.transform
            currentClip = state.clip
            if (isLayer) buffer.append(DisplayOp.EndLayer)
        }
    }

    /**
     * Repeatedly [restore] until the save stack depth reaches [count].
     *
     * Has no effect if [count] is greater than or equal to [saveCount].
     */
    fun restoreToCount(count: Int) {
        while (saveStack.size > count) restore()
    }

    fun flushAndSnapshot(bounds: Rect): Image {
        buffer.append(DisplayOp.FlushAndSnapshot(bounds))
        return Image.placeholder(bounds.width.toInt(), bounds.height.toInt())
    }

    /** Pre-concatenate a translation by (x, y) into the current transform. */
    fun translate(x: Float, y: Float) { concat(Matrix33.translate(x, y)) }

    /** Pre-concatenate a scale by (sx, sy) into the current transform. */
    fun scale(sx: Float, sy: Float) { concat(Matrix33.scale(sx, sy)) }

    /**
     * Pre-concatenate a rotation of [degrees] about an optional pivot point (px, py).
     *
     * When the pivot is omitted the rotation is about the origin.
     */
    fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f) {
        if (px == 0f && py == 0f) { concat(Matrix33.rotate(degrees)) }
        else { translate(px, py); concat(Matrix33.rotate(degrees)); translate(-px, -py) }
    }

    /** Pre-concatenate a skew by (sx, sy) into the current transform. */
    fun skew(sx: Float, sy: Float) { concat(Matrix33.skew(sx, sy)) }

    /**
     * Pre-concatenate [matrix] with the current transform.
     *
     * The new transform becomes `currentTransform * matrix`.
     */
    fun concat(matrix: Matrix33) {
        currentTransform = currentTransform * matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Replace the current transform with [matrix]. */
    fun setMatrix(matrix: Matrix33) {
        currentTransform = matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Reset the current transform to the identity matrix. */
    fun resetMatrix() { setMatrix(Matrix33.identity()) }

    /**
     * Intersect the current clip with an axis-aligned rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRect(rect: Rect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = ClipStackOp.RectOp(rect, op, antiAlias)
        val prevClip = currentClip
        currentClip = when (prevClip) {
            ClipStack.WideOpen -> if (op == ClipOp.INTERSECT) ClipStack.DeviceRect(rect, antiAlias) else ClipStack.Complex(listOf(newOp))
            is ClipStack.DeviceRect -> ClipStack.Complex(listOf(ClipStackOp.RectOp(prevClip.rect, ClipOp.INTERSECT, prevClip.antiAlias), newOp))
            is ClipStack.Complex -> ClipStack.Complex(prevClip.ops + newOp)
        }
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    /**
     * Intersect the current clip with a rounded rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRRect(rrect: RRect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = ClipStackOp.RRectOp(rrect, op, antiAlias)
        val prevClip = currentClip
        currentClip = when (prevClip) {
            ClipStack.WideOpen -> ClipStack.Complex(listOf(newOp))
            is ClipStack.DeviceRect -> ClipStack.Complex(listOf(ClipStackOp.RectOp(prevClip.rect, ClipOp.INTERSECT, prevClip.antiAlias), newOp))
            is ClipStack.Complex -> ClipStack.Complex(prevClip.ops + newOp)
        }
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    /**
     * Intersect the current clip with an arbitrary [path].
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipPath(path: Path, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = ClipStackOp.PathOp(path, op, antiAlias)
        val prevClip = currentClip
        currentClip = when (prevClip) {
            ClipStack.WideOpen -> ClipStack.Complex(listOf(newOp))
            is ClipStack.DeviceRect -> ClipStack.Complex(listOf(ClipStackOp.RectOp(prevClip.rect, ClipOp.INTERSECT, prevClip.antiAlias), newOp))
            is ClipStack.Complex -> ClipStack.Complex(prevClip.ops + newOp)
        }
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    private data class CanvasState(val transform: Matrix33, val clip: ClipStack)
}
