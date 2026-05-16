package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.skia.core.SkLattice
import org.skia.core.SkPicture
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColor
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTextSlug
import org.skia.foundation.SkVertices
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
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

    // ─── R-suivi.10 — additional no-op overrides ──────────────────────────
    // Upstream's SkNoDrawCanvas overrides every SkCanvas virtual to be a
    // no-op so analysis passes never trigger rasterisation. The base
    // SkCanvas implementations would otherwise (e.g.) translate
    // `drawAtlas` into per-quad `drawPath` calls on the dummy device. We
    // short-circuit each remaining draw entry point here.

    override fun drawRegion(region: SkRegion, paint: SkPaint) {}

    override fun drawImageNine(
        image: SkImage,
        center: SkIRect,
        dst: SkRect,
        filterMode: SkFilterMode,
        paint: SkPaint?,
    ) {}

    override fun drawAtlas(
        image: SkImage,
        xform: Array<SkRSXform>,
        src: Array<SkRect>,
        colors: IntArray?,
        blendMode: SkBlendMode,
        sampling: SkSamplingOptions,
        cullRect: SkRect?,
        paint: SkPaint?,
    ) {}

    override fun drawVertices(
        vertices: SkVertices,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {}

    override fun drawString(
        str: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {}

    override fun drawSimpleText(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {}

    override fun drawTextBlob(
        blob: SkTextBlob,
        x: SkScalar,
        y: SkScalar,
        paint: SkPaint,
    ) {}

    override fun drawDrawable(drawable: SkDrawable, matrix: SkMatrix?) {}

    override fun drawDrawable(drawable: SkDrawable, x: SkScalar, y: SkScalar) {}

    override fun drawAnnotation(rect: SkRect, key: String, value: ByteArray?) {}

    // ─── R-suivi.50 — drawShadow / drawSlug / drawImageLattice / drawPicture ─
    // No-op overrides : analysis passes shouldn't trigger the base-class
    // delegation to SkShadowUtils / picture playback / drawTextBlob.

    override fun drawShadow(
        path: SkPath,
        zPlaneParams: SkPoint3,
        lightPos: SkPoint3,
        lightRadius: SkScalar,
        ambientColor: SkColor,
        spotColor: SkColor,
        flags: Int,
    ) {}

    override fun drawSlug(slug: SkTextSlug, origin: SkPoint) {}

    override fun drawImageLattice(
        image: SkImage,
        lattice: SkLattice,
        dst: SkRect,
        filterMode: SkFilterMode,
        paint: SkPaint?,
    ) {}

    override fun drawPicture(
        picture: SkPicture,
        matrix: SkMatrix?,
        paint: SkPaint?,
    ) {}

    // Note: clipRect / clipPath / clipRRect / clipRegion / clipShader are
    // *not* overridden — SkNoDrawCanvas keeps an accurate clip stack so
    // [getDeviceClipBounds] / [getLocalClipBounds] return meaningful
    // values during analysis passes, matching upstream's
    // "conservative clipping" contract.
}
