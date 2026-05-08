package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * No-op canvas for analysis passes that need the state stack
 * (CTM, clip) but not the rasterised pixels — e.g. computing the
 * device-space bounds of a draw, dry-running a GM to time
 * `onDraw` overhead, or recording a draw stream's structure
 * without paying for compositing.
 *
 * Mirrors Skia's
 * [`SkNoDrawCanvas`](https://github.com/google/skia/blob/main/include/utils/SkNoDrawCanvas.h)
 * — every draw method is a no-op while [save] / [restore] /
 * [translate] / [scale] / [clip*] inherit from [SkCanvas] and
 * keep the matrix and clip stacks accurate. So
 * [getTotalMatrix] returns a sensible value at any point during
 * a draw walk.
 *
 * Backed by a `1 × 1` dummy [SkBitmap] (matches
 * [`org.skia.core.SkRecordingCanvas`]'s "extend SkCanvas without
 * a real surface" pattern). The dummy bitmap is never drawn
 * into — every draw method returns immediately.
 *
 * Usage :
 * ```kotlin
 * val canvas = SkNoDrawCanvas(width = 800, height = 600)
 * gm.draw(canvas)
 * val ctm = canvas.getTotalMatrix()  // valid, reflects the GM's last setMatrix
 * ```
 */
public open class SkNoDrawCanvas(
    width: Int,
    height: Int,
) : SkCanvas(SkBitmap(1, 1)) {

    private val canvasWidth: Int = kotlin.math.max(1, width)
    private val canvasHeight: Int = kotlin.math.max(1, height)

    override val width: Int get() = canvasWidth
    override val height: Int get() = canvasHeight

    // ─── Draw ops — every override is a no-op. The state stack and
    //               matrix / clip queries are inherited from SkCanvas
    //               and continue to work via the dummy device.

    override fun drawRect(rect: SkRect, paint: SkPaint) {}
    override fun drawOval(oval: SkRect, paint: SkPaint) {}
    override fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {}
    override fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {}
    override fun drawPath(path: SkPath, paint: SkPaint) {}
    override fun drawRRect(rrect: SkRRect, paint: SkPaint) {}
    override fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {}
    override fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {}
    override fun drawArc(
        oval: SkRect, startAngle: SkScalar, sweepAngle: SkScalar,
        useCenter: Boolean, paint: SkPaint,
    ) {}
    override fun drawPoints(
        mode: PointMode,
        points: Array<org.skia.math.SkPoint>,
        paint: SkPaint,
    ) {}
    override fun drawImage(
        image: SkImage,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
    ) {}
    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: org.skia.core.SrcRectConstraint,
    ) {}
    override fun drawColor(color: SkColor, mode: SkBlendMode) {}
    override fun drawPaint(paint: SkPaint) {}
    override fun drawPatch(
        cubics: Array<org.skia.math.SkPoint>,
        colors: IntArray?,
        texCoords: Array<org.skia.math.SkPoint>?,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {}
}
