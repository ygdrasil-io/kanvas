package org.graphiks.kanvas.canvas

import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32

import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.GlyphPaintProvider
import org.graphiks.kanvas.text.PreparedTextOutline
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapAxisAligned
import org.graphiks.math.matrix.mapAxisAlignedRect

/**
 * A immediate-mode style 2D drawing surface that records drawing operations into
 * a [DisplayListBuffer] for deferred playback.
 *
 * [Canvas] manages a stack of transform and clip state, offering a Skia-like API
 * for drawing rectangles, paths, images, and text. All operations are appended to
 * the internal buffer for subsequent rendering by a pipeline consumer.
 */
class Canvas internal constructor(private val buffer: DisplayListBuffer) {
    private var currentTransform = Matrix3x3F32.Identity
    /** Clip exposed to Canvas queries such as [quickReject] and [localClipBounds]. */
    private var currentClip: ClipStack = ClipStack.WideOpen
    /** Clip recorded on child DisplayOps; an outer saveLayer clip is deferred to its restore. */
    private var currentRecordedClip: ClipStack = ClipStack.WideOpen
    private var saveStack = mutableListOf<Pair<CanvasState, Boolean>>() // (state, isLayer)

    /** The current transform matrix. */
    val matrix: Matrix3x3F32 get() = currentTransform

    /** The number of states on the save stack. */
    val saveCount: Int get() = saveStack.size

    /**
     * The local clip bounds, expressed in the current coordinate system.
     *
     * Returns the single device-rect clip mapped back through an invertible
     * scale/translate CTM. Returns [RectF32.Empty] when the clip is wide-open,
     * complex, or cannot be represented conservatively as a local axis-aligned
     * rectangle.
     */
    val localClipBounds: RectF32
        get() = when (val clip = currentClip) {
            ClipStack.WideOpen -> RectF32.Empty
            is ClipStack.DeviceRect -> currentTransform
                .takeIf(Matrix3x3F32::isScaleTranslate)
                ?.invert()
                ?.mapAxisAlignedRect(clip.rect)
                ?: RectF32.Empty
            is ClipStack.Complex -> RectF32.Empty
        }

    /**
     * Return true if [rect] is fully outside the current clip.
     * Returns false for complex clips (conservative: may draw).
     */
    fun quickReject(rect: RectF32): Boolean {
        if (currentClip is ClipStack.WideOpen) return false
        if (currentClip is ClipStack.DeviceRect) {
            val c = (currentClip as ClipStack.DeviceRect).rect
            val deviceRect = currentTransform
                .takeIf(Matrix3x3F32::isScaleTranslate)
                ?.mapAxisAlignedRect(rect)
                ?: return false
            return deviceRect.right <= c.left || deviceRect.left >= c.right ||
                   deviceRect.bottom <= c.top || deviceRect.top >= c.bottom
        }
        return false
    }

    /** Return true if [path]'s bounds are fully outside the current clip. */
    fun quickReject(path: Path): Boolean = path.computeBounds()?.let(::quickReject) ?: false

    /** True if the current clip region is empty (nothing visible). */
    val isClipEmpty: Boolean get() = currentClip.isEmpty

    /** True if the current clip is a single axis-aligned rectangle. */
    val isClipRect: Boolean get() = currentClip.isRect

    /** Draw an axis-aligned rectangle filled/stroked with [paint]. */
    fun drawRect(rect: RectF32, paint: Paint) {
        buffer.append(DisplayOp.DrawRect(rect, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a rounded rectangle filled/stroked with [paint]. */
    fun drawRRect(rrect: RRectF32, paint: Paint) {
        buffer.append(DisplayOp.DrawRRect(rrect, paint, currentTransform, currentRecordedClip))
    }

    /** Draw an arbitrary [path] filled/stroked with [paint]. */
    fun drawPath(path: Path, paint: Paint) {
        buffer.append(DisplayOp.DrawPath(path, paint, currentTransform, currentRecordedClip))
    }

    internal fun drawPath(
        path: Path,
        paint: Paint,
        sourceOperation: DrawPathSourceOperation,
    ) {
        buffer.append(
            DisplayOp.DrawPath.withSourceOperation(
                path = path,
                paint = paint,
                transform = currentTransform,
                clip = currentRecordedClip,
                sourceOperation = sourceOperation,
            ),
        )
    }

    /**
     * Draw an [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImage(image: Image, dst: RectF32, paint: Paint? = null) {
        val src = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentRecordedClip))
    }

    /**
     * Draw an [image] scaled to fill [dst] with an explicit sampling policy.
     *
     * The policy is recorded on the image shader so GPU lowering can either
     * select the matching native sampler or report its stable unsupported
     * diagnostic.
     */
    fun drawImage(
        image: Image,
        dst: RectF32,
        sampling: SamplingOptions,
        paint: Paint? = null,
    ) {
        val src = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val samplingPaint = (paint ?: Paint()).copy(shader = image.makeShader(sampling = sampling))
        buffer.append(DisplayOp.DrawImage(image, src, dst, samplingPaint, currentTransform, currentRecordedClip))
    }

    /**
     * Draw a sub-region [src] of [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImageRect(image: Image, src: RectF32, dst: RectF32, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a [TextBlob] at the given position with [paint]. */
    fun drawText(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        val typeface = blob.typeface
        if (
            typeface != null && (
                typeface is GlyphPaintProvider ||
                    (typeface as? FontTypeface)?.usesCffOutlines == true ||
                    blob.variationCoordinates.isNotEmpty()
                )
        ) {
            val outlinedGlyphs = mutableListOf<DisplayOp.DrawPath>()
            var unresolvedGlyph = false
            blob.glyphRuns.forEach { run ->
                run.glyphs.indices.forEach { index ->
                    val glyphId = run.glyphs[index].toInt()
                    val position = run.positions[index]
                    val path = when (
                        val outline = (typeface as? FontTypeface)?.preparedTextOutline(
                            glyphId = glyphId,
                            fontSize = run.fontSize,
                            variationCoordinates = blob.variationCoordinates,
                        )
                    ) {
                        is PreparedTextOutline.ProvenNonEmpty -> outline.path
                        PreparedTextOutline.ProvenEmpty -> return@forEach
                        PreparedTextOutline.Unavailable -> {
                            unresolvedGlyph = true
                            return@forEach
                        }
                        null -> typeface.getGlyphPath(
                                glyphId,
                                run.fontSize,
                                blob.variationCoordinates,
                            ) ?: run {
                                unresolvedGlyph = true
                                return@forEach
                            }
                    }
                    if (path.isEmpty()) return@forEach
                    val glyphPaint = (typeface as? GlyphPaintProvider)?.paintForGlyph(glyphId) ?: paint
                    outlinedGlyphs +=
                        DisplayOp.DrawPath.withSourceOperation(
                            path = path.transform(x + position.x, y + position.y, 1f, 1f),
                            paint = glyphPaint,
                            transform = currentTransform,
                            clip = currentRecordedClip,
                            sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
                        )
                }
            }
            if (!unresolvedGlyph) {
                outlinedGlyphs.forEach(buffer::append)
                return
            }
        }
        buffer.append(DisplayOp.DrawText(blob, x, y, paint, currentTransform, currentRecordedClip))
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
    fun drawColor(color: ColorARGB, mode: BlendMode = BlendMode.SRC_OVER) {
        buffer.append(DisplayOp.DrawColor(color, mode, currentTransform, currentRecordedClip))
    }

    /** Overwrite the entire canvas with [color]. */
    fun clear(color: ColorARGB) {
        buffer.append(DisplayOp.Clear(color))
    }

    /** Draw a single point at (x, y). */
    fun drawPoint(x: Float, y: Float, paint: Paint) {
        buffer.append(DisplayOp.DrawPoint(x, y, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a list of [points] with the given point [mode]. */
    fun drawPoints(mode: PointMode, points: List<Point2F32>, paint: Paint) {
        buffer.append(DisplayOp.DrawPoints(mode, points, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a double rounded rectangle (outer fill, inner hole). */
    fun drawDRRect(outer: RRectF32, inner: RRectF32, paint: Paint) {
        buffer.append(DisplayOp.DrawDRRect(outer, inner, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a 9-patch [image] with [center] defining corner sizes, scaled to [dst]. */
    fun drawImageNine(image: Image, center: RectF32, dst: RectF32, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImageNine(image, center, dst, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a lattice [image] over a grid defined by [lattice], scaled to [dst]. */
    fun drawImageLattice(
        image: Image,
        lattice: Lattice,
        dst: RectF32,
        paint: Paint? = null,
        sampling: SamplingOptions = SamplingOptions.LINEAR,
    ) {
        buffer.append(DisplayOp.DrawImageLattice(image, lattice, dst, paint, currentTransform, currentRecordedClip, sampling))
    }

    /** Draw a pre-recorded [picture] with optional [paint] modulation. */
    fun drawPicture(picture: Picture, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawPicture(picture, paint, currentTransform, currentRecordedClip))
    }

    /** Draw a triangle mesh from [vertices]. */
    fun drawVertices(vertices: Vertices, paint: Paint) {
        buffer.append(DisplayOp.DrawVertices(vertices, paint, currentTransform, currentRecordedClip))
    }

    fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null) {
        if (mesh.program != null) {
            buffer.append(DisplayOp.DrawMesh(mesh, paint, blendMode, currentTransform, currentRecordedClip))
        } else {
            drawVertices(mesh.vertices, paint.copy(blendMode = blendMode ?: paint.blendMode))
        }
    }

    /** Batch-draw sprites from [atlas] texture. */
    fun drawAtlas(atlas: Image, transforms: List<Matrix3x3F32>, texRects: List<RectF32>, colors: List<ColorARGB>? = null, blendMode: BlendMode = BlendMode.SRC_OVER, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawAtlas(atlas, transforms, texRects, colors, blendMode, paint, currentTransform, currentRecordedClip))
    }

    /** Add a metadata annotation (no visual output). */
    fun drawAnnotation(rect: RectF32, key: String, value: String) {
        buffer.append(DisplayOp.Annotation(rect, key, value))
    }

    /**
     * Save the current transform and clip state onto the stack.
     *
     * @return The new save count (depth of the save stack).
     */
    fun save(): Int {
        saveStack.add(CanvasState(currentTransform, currentClip, currentRecordedClip) to false)
        return saveStack.size
    }

    /**
     * Save state and begin a new layer (offscreen render target).
     *
     * @param bounds Optional bounds for the offscreen surface.
     * @param paint  Optional [Paint] to apply when compositing the layer back.
     * @return The new save count.
     */
    fun saveLayer(bounds: RectF32? = null, paint: Paint? = null): Int {
        return saveLayer(SaveLayerRec(bounds, paint))
    }

    /** Save state and begin a layer described by [rec], including an optional backdrop filter. */
    fun saveLayer(rec: SaveLayerRec): Int {
        val compositeClip = (rec.compositeClip ?: ClipStack.WideOpen)
            .intersectWith(currentRecordedClip)
            .takeUnless { it == ClipStack.WideOpen }
        val layerRec = if (compositeClip == rec.compositeClip) rec else rec.copy(compositeClip = compositeClip)
        buffer.append(DisplayOp.BeginLayer(layerRec, currentTransform))
        saveStack.add(CanvasState(currentTransform, currentClip, currentRecordedClip) to true)
        // Keep the semantic clip visible to Canvas APIs, but defer it from children to the layer
        // restore. This applies an AA coverage F exactly once and preserves parent pixels outside.
        currentRecordedClip = ClipStack.WideOpen
        return saveStack.size
    }

    /** Restore the most recently saved state and end the current layer if one was active. */
    fun restore() {
        if (saveStack.isNotEmpty()) {
            val (state, isLayer) = saveStack.removeLast()
            currentTransform = state.transform
            currentClip = state.clip
            currentRecordedClip = state.recordedClip
            if (isLayer) buffer.append(DisplayOp.EndLayer)
        }
    }

    /**
     * Repeatedly [restore] until the save stack depth reaches [count].
     *
     * Has no effect if [count] is negative or greater than or equal to
     * [saveCount]. A negative count is invalid for this public Canvas API and
     * is deliberately ignored before any state or GPU-recording work occurs.
     */
    fun restoreToCount(count: Int) {
        if (count < 0) return
        while (saveStack.size > count) restore()
    }

    fun flushAndSnapshot(bounds: RectF32): Image {
        buffer.append(DisplayOp.FlushAndSnapshot(bounds))
        return Image.placeholder(bounds.width().toInt(), bounds.height().toInt())
    }

    /** Pre-concatenate a translation by (x, y) into the current transform. */
    fun translate(x: Float, y: Float) { concat(Matrix3x3F32.translation(x, y)) }

    /** Pre-concatenate a scale by (sx, sy) into the current transform. */
    fun scale(sx: Float, sy: Float) { concat(Matrix3x3F32.scaling(sx, sy)) }

    /**
     * Pre-concatenate a rotation of [degrees] about an optional pivot point (px, py).
     *
     * When the pivot is omitted the rotation is about the origin.
     */
    fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f) {
        if (px == 0f && py == 0f) { concat(Matrix3x3F32.rotation(degrees)) }
        else { translate(px, py); concat(Matrix3x3F32.rotation(degrees)); translate(-px, -py) }
    }

    /** Pre-concatenate a skew by (sx, sy) into the current transform. */
    fun skew(sx: Float, sy: Float) { concat(Matrix3x3F32.skewing(sx, sy)) }

    /**
     * Pre-concatenate [matrix] with the current transform.
     *
     * The new transform becomes `currentTransform * matrix`.
     */
    fun concat(matrix: Matrix3x3F32) {
        currentTransform = currentTransform * matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Replace the current transform with [matrix]. */
    fun setMatrix(matrix: Matrix3x3F32) {
        currentTransform = matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Reset the current transform to the identity matrix. */
    fun resetMatrix() { setMatrix(Matrix3x3F32.Identity) }

    /**
     * Intersect the current clip with an axis-aligned rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRect(rect: RectF32, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = captureClipRect(rect, op, antiAlias)
        currentClip = appendClip(currentClip, newOp, allowDeviceRect = true)
        currentRecordedClip = appendClip(currentRecordedClip, newOp, allowDeviceRect = true)
        buffer.append(DisplayOp.SetClip(currentRecordedClip))
    }

    /**
     * Intersect the current clip with a rounded rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRRect(rrect: RRectF32, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = captureClipRRect(rrect, op, antiAlias)
        currentClip = appendClip(currentClip, newOp, allowDeviceRect = false)
        currentRecordedClip = appendClip(currentRecordedClip, newOp, allowDeviceRect = false)
        buffer.append(DisplayOp.SetClip(currentRecordedClip))
    }

    /**
     * Intersect the current clip with an arbitrary [path].
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipPath(path: Path, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        val newOp = captureClipPath(path, op, antiAlias)
        currentClip = appendClip(currentClip, newOp, allowDeviceRect = false)
        currentRecordedClip = appendClip(currentRecordedClip, newOp, allowDeviceRect = false)
        buffer.append(DisplayOp.SetClip(currentRecordedClip))
    }

    private fun captureClipRect(rect: RectF32, op: ClipOp, antiAlias: Boolean): ClipStackOp {
        val transformClass = currentTransform.captureTransformClass()
        return when {
        transformClass.isTerminalClipTransformClass() ->
            ClipStackOp.PathOp(
                Path().addRect(rect), op, antiAlias,
                perspectiveCaptureRefusal = transformClass == "perspective",
                transformClass = transformClass,
            )
        currentTransform.isScaleTranslate() ->
            ClipStackOp.RectOp(currentTransform.mapAxisAlignedRect(rect), op, antiAlias)
        !currentTransform.hasPerspective() ->
            ClipStackOp.PathOp(
                Path().addRect(rect).transform(currentTransform),
                op,
                antiAlias,
                transformClass = currentTransform.captureTransformClass(),
            )
        else ->
            ClipStackOp.PathOp(
                Path().addRect(rect),
                op,
                antiAlias,
                perspectiveCaptureRefusal = true,
                transformClass = "perspective",
            )
        }
    }

    private fun captureClipRRect(rrect: RRectF32, op: ClipOp, antiAlias: Boolean): ClipStackOp {
        val transformClass = currentTransform.captureTransformClass()
        return when {
        transformClass.isTerminalClipTransformClass() ->
            ClipStackOp.PathOp(
                Path().addRRect(rrect), op, antiAlias,
                perspectiveCaptureRefusal = transformClass == "perspective",
                transformClass = transformClass,
            )
        currentTransform.isScaleTranslate() ->
            ClipStackOp.RRectOp(
                rrect = rrect.mapAxisAligned(currentTransform),
                op = op,
                antiAlias = antiAlias,
                transformClass = transformClass,
            )
        !currentTransform.hasPerspective() ->
            ClipStackOp.PathOp(
                Path().addRRect(rrect).transform(currentTransform),
                op,
                antiAlias,
                transformClass = currentTransform.captureTransformClass(),
            )
        else ->
            ClipStackOp.PathOp(
                Path().addRRect(rrect),
                op,
                antiAlias,
                perspectiveCaptureRefusal = true,
                transformClass = "perspective",
            )
        }
    }

    private fun captureClipPath(path: Path, op: ClipOp, antiAlias: Boolean): ClipStackOp {
        val transformClass = currentTransform.captureTransformClass()
        val terminalCapture = transformClass.isTerminalClipTransformClass()
        return ClipStackOp.PathOp(
            if (terminalCapture) path else path.transform(currentTransform),
            op,
            antiAlias,
            perspectiveCaptureRefusal = transformClass == "perspective",
            transformClass = transformClass,
        )
    }

    private fun appendClip(
        previous: ClipStack,
        newOp: ClipStackOp,
        allowDeviceRect: Boolean,
    ): ClipStack = when (previous) {
        ClipStack.WideOpen -> if (allowDeviceRect && newOp is ClipStackOp.RectOp && newOp.op == ClipOp.INTERSECT) {
            ClipStack.DeviceRect(newOp.rect, newOp.antiAlias)
        } else {
            ClipStack.Complex(listOf(newOp))
        }
        is ClipStack.DeviceRect -> ClipStack.Complex(
            listOf(ClipStackOp.RectOp(previous.rect, ClipOp.INTERSECT, previous.antiAlias), newOp),
        )
        is ClipStack.Complex -> ClipStack.Complex(previous.ops + newOp)
    }

    private data class CanvasState(
        val transform: Matrix3x3F32,
        val clip: ClipStack,
        val recordedClip: ClipStack,
    )
}

private fun Matrix3x3F32.captureTransformClass(): String = when {
    !floatValues().all(Float::isFinite) -> "non-finite"
    hasPerspective() -> "perspective"
    sx * sy - kx * ky == 0f -> "singular-affine"
    this == Matrix3x3F32.Identity -> "identity"
    kx == 0f && ky == 0f && sx == 1f && sy == 1f -> "translate"
    kx == 0f && ky == 0f && sx == sy && sx > 0f -> "uniform-positive-scale-translate"
    kx == 0f && ky == 0f && tx == 0f && ty == 0f -> "scale"
    kx == 0f && ky == 0f -> "scale-translate"
    else -> "affine"
}

private fun Matrix3x3F32.floatValues(): FloatArray = floatArrayOf(sx, kx, tx, ky, sy, ty, persp0, persp1, persp2)

private fun String.isTerminalClipTransformClass(): Boolean =
    this == "non-finite" || this == "singular-affine" || this == "perspective"
