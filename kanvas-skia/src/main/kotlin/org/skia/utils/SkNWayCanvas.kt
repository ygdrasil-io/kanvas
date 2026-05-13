package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Iso-aligned port of Skia's `SkNWayCanvas`
 * ([include/utils/SkNWayCanvas.h](https://github.com/google/skia/blob/main/include/utils/SkNWayCanvas.h)).
 *
 * A canvas that **forwards every draw call** to a list of child
 * canvases. Useful for debugging GMs — feed a GM into an
 * `SkNWayCanvas` that fans out to a real raster sink and a
 * recording / overdraw / debug canvas in parallel.
 *
 * The N-way canvas inherits from [SkNoDrawCanvas] so its own state
 * stack tracks CTM / clip, but it never rasterises pixels itself —
 * each forwarded draw is handled by the child canvases (which keep
 * their own state stacks; the canvas list is responsible for
 * mirroring transforms via [save] / [restore] etc.).
 *
 * Implementation note: in this R1 port we override the **commonly
 * used** draw / state methods (matches the "minimal set" suggested
 * in the porting plan — `onDrawPaint`, `onDrawRect`, `onDrawOval`,
 * `onDrawPath`, `onDrawImage`, …). Less-used overrides
 * (`onDrawSlug`, `onDrawShadowRec`, image-set quads) are flagged
 * inline as TODO for follow-up work — they are not part of the
 * GM-port critical path.
 */
public open class SkNWayCanvas(width: Int, height: Int) : SkNoDrawCanvas(width, height) {

    private val children: MutableList<SkCanvas> = mutableListOf()

    /** Register [canvas] to receive every subsequent draw + state op. */
    public open fun addCanvas(canvas: SkCanvas) {
        children += canvas
    }

    /** Stop forwarding to [canvas]. No-op if it was not registered. */
    public open fun removeCanvas(canvas: SkCanvas) {
        children -= canvas
    }

    /** Unregister all child canvases. */
    public open fun removeAll() {
        children.clear()
    }

    // ─── State ops — must be mirrored so child canvases' matrix / clip
    //               stacks stay in sync.

    override fun save(): Int {
        children.forEach { it.save() }
        return super.save()
    }

    override fun restore() {
        children.forEach { it.restore() }
        super.restore()
    }

    override fun translate(dx: SkScalar, dy: SkScalar) {
        children.forEach { it.translate(dx, dy) }
        super.translate(dx, dy)
    }

    override fun scale(sx: SkScalar, sy: SkScalar) {
        children.forEach { it.scale(sx, sy) }
        super.scale(sx, sy)
    }

    override fun rotate(deg: SkScalar) {
        children.forEach { it.rotate(deg) }
        super.rotate(deg)
    }

    override fun skew(sx: SkScalar, sy: SkScalar) {
        children.forEach { it.skew(sx, sy) }
        super.skew(sx, sy)
    }

    override fun concat(mat: org.skia.math.SkMatrix) {
        children.forEach { it.concat(mat) }
        super.concat(mat)
    }

    override fun setMatrix(mat: org.skia.math.SkMatrix) {
        children.forEach { it.setMatrix(mat) }
        super.setMatrix(mat)
    }

    override fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        children.forEach { it.clipRect(rect, doAntiAlias) }
        super.clipRect(rect, doAntiAlias)
    }

    override fun clipPath(path: SkPath, doAntiAlias: Boolean) {
        children.forEach { it.clipPath(path, doAntiAlias) }
        super.clipPath(path, doAntiAlias)
    }

    override fun clipRRect(rrect: SkRRect, doAntiAlias: Boolean) {
        children.forEach { it.clipRRect(rrect, doAntiAlias) }
        super.clipRRect(rrect, doAntiAlias)
    }

    // ─── Draw ops — each forwards to every child canvas.

    override fun drawPaint(paint: SkPaint) {
        children.forEach { it.drawPaint(paint) }
    }

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        children.forEach { it.drawRect(rect, paint) }
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        children.forEach { it.drawOval(oval, paint) }
    }

    override fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        children.forEach { it.drawCircle(cx, cy, radius, paint) }
    }

    override fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        children.forEach { it.drawLine(x0, y0, x1, y1, paint) }
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        children.forEach { it.drawPath(path, paint) }
    }

    override fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        children.forEach { it.drawRRect(rrect, paint) }
    }

    override fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {
        children.forEach { it.drawRoundRect(rect, rx, ry, paint) }
    }

    override fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {
        children.forEach { it.drawDRRect(outer, inner, paint) }
    }

    override fun drawArc(
        oval: SkRect,
        startAngle: SkScalar,
        sweepAngle: SkScalar,
        useCenter: Boolean,
        paint: SkPaint,
    ) {
        children.forEach { it.drawArc(oval, startAngle, sweepAngle, useCenter, paint) }
    }

    override fun drawPoints(mode: PointMode, points: Array<SkPoint>, paint: SkPaint) {
        children.forEach { it.drawPoints(mode, points, paint) }
    }

    override fun drawImage(
        image: SkImage,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
    ) {
        children.forEach { it.drawImage(image, x, y, sampling, paint) }
    }

    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
    ) {
        children.forEach { it.drawImageRect(image, src, dst, sampling, paint, constraint) }
    }

    override fun drawColor(color: SkColor, mode: SkBlendMode) {
        children.forEach { it.drawColor(color, mode) }
    }

    override fun drawPatch(
        cubics: Array<SkPoint>,
        colors: IntArray?,
        texCoords: Array<SkPoint>?,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {
        children.forEach { it.drawPatch(cubics, colors, texCoords, blendMode, paint) }
    }

    // TODO(R2): forward drawAtlas, drawVertices, drawTextBlob, drawDrawable,
    //           drawAnnotation, drawShadow, drawSlug, image-lattice / image-set
    //           overloads. Not currently used by the GM critical path.
}
