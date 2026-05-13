package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Recording variant of [SkCanvas] — every state and draw op is
 * appended to an internal [SkRecord] list instead of being rasterised.
 *
 * Constructed by [SkPictureRecorder.beginRecording]; the only public
 * surface is the inherited [SkCanvas] API. State ops (save / translate
 * / clipRect …) are recorded **and** applied to the parent's state
 * stack so that intra-recording queries like [getTotalMatrix] return
 * sensible values (matches Skia's recording-canvas contract). Draw
 * ops are recorded only — no pixels are produced.
 *
 * The dummy backing bitmap is `1 × 1` to keep allocations cheap; the
 * recorder's true [width] / [height] come from the bounds passed to
 * [SkPictureRecorder.beginRecording] and are exposed via the open
 * [SkCanvas.width] / [SkCanvas.height] overrides below.
 */
internal class SkRecordingCanvas(
    private val recordedWidth: Int,
    private val recordedHeight: Int,
    internal val records: MutableList<SkRecord>,
) : SkCanvas(SkBitmap(1, 1)) {

    override val width: Int get() = recordedWidth
    override val height: Int get() = recordedHeight

    // -- State / matrix / clip -------------------------------------------------
    // We delegate to super so that getTotalMatrix / getSaveCount / clip-aware
    // queries remain accurate during recording (Skia's recording canvas does
    // the same — it's a "stateful" recorder, not a blind appender).

    override fun save(): Int {
        records += SkRecord.Save
        return super.save()
    }

    override fun restore() {
        records += SkRecord.Restore
        super.restore()
    }

    override fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
        records += SkRecord.SaveLayer(bounds, paint?.copy())
        return super.saveLayer(bounds, paint)
    }

    override fun saveLayer(bounds: SkRect?, paint: SkPaint?, flags: SaveLayerFlags): Int {
        // Flags are accepted by upstream but ignored in our raster path; record
        // the no-flags variant so playback uses the same semantic.
        records += SkRecord.SaveLayer(bounds, paint?.copy())
        return super.saveLayer(bounds, paint, flags)
    }

    override fun translate(dx: SkScalar, dy: SkScalar) {
        records += SkRecord.Translate(dx, dy)
        super.translate(dx, dy)
    }

    override fun scale(sx: SkScalar, sy: SkScalar) {
        records += SkRecord.Scale(sx, sy)
        super.scale(sx, sy)
    }

    override fun rotate(deg: SkScalar) {
        records += SkRecord.Rotate(deg)
        super.rotate(deg)
    }

    override fun rotate(deg: SkScalar, px: SkScalar, py: SkScalar) {
        records += SkRecord.RotatePivot(deg, px, py)
        super.rotate(deg, px, py)
    }

    override fun skew(sx: SkScalar, sy: SkScalar) {
        records += SkRecord.Skew(sx, sy)
        super.skew(sx, sy)
    }

    override fun concat(mat: SkMatrix) {
        records += SkRecord.Concat(mat)
        super.concat(mat)
    }

    override fun setMatrix(mat: SkMatrix) {
        records += SkRecord.SetMatrix(mat)
        super.setMatrix(mat)
    }

    override fun resetMatrix() {
        records += SkRecord.ResetMatrix
        super.resetMatrix()
    }

    override fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        records += SkRecord.ClipRect(rect, doAntiAlias)
        super.clipRect(rect, doAntiAlias)
    }

    // The single-arg overload defaults to doAntiAlias=false; recording it as
    // ClipRect(rect, false) is semantically identical and round-trips through
    // playback.
    override fun clipRect(rect: SkRect) {
        records += SkRecord.ClipRect(rect, doAntiAlias = false)
        super.clipRect(rect)
    }

    // -- Draw ops --------------------------------------------------------------
    // Draw ops only record; they do not delegate to super (the dummy 1×1
    // bitmap would silently absorb writes, but the record list is the source
    // of truth). Mutable arguments (paint, font) are deep-copied so subsequent
    // client mutations don't bleed into the picture.

    override fun drawPaint(paint: SkPaint) {
        records += SkRecord.DrawPaint(paint.copy())
    }

    override fun drawColor(color: SkColor, mode: SkBlendMode) {
        records += SkRecord.DrawColor(color, mode)
    }

    override fun clear(color: SkColor) {
        records += SkRecord.DrawColor(color, SkBlendMode.kSrc)
    }

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        records += SkRecord.DrawRect(rect, paint.copy())
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        records += SkRecord.DrawOval(oval, paint.copy())
    }

    override fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        records += SkRecord.DrawCircle(cx, cy, radius, paint.copy())
    }

    override fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        records += SkRecord.DrawRRect(rrect, paint.copy())
    }

    override fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {
        records += SkRecord.DrawRoundRect(rect, rx, ry, paint.copy())
    }

    override fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {
        records += SkRecord.DrawDRRect(outer, inner, paint.copy())
    }

    override fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        records += SkRecord.DrawLine(x0, y0, x1, y1, paint.copy())
    }

    override fun drawArc(
        oval: SkRect,
        startAngleDeg: SkScalar,
        sweepAngleDeg: SkScalar,
        useCenter: Boolean,
        paint: SkPaint,
    ) {
        records += SkRecord.DrawArc(oval, startAngleDeg, sweepAngleDeg, useCenter, paint.copy())
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        records += SkRecord.DrawPath(path, paint.copy())
    }

    override fun drawImage(
        image: SkImage,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
    ) {
        records += SkRecord.DrawImage(image, x, y, sampling, paint?.copy())
    }

    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
    ) {
        records += SkRecord.DrawImageRect(image, src, dst, sampling, paint?.copy(), constraint)
    }

    override fun drawString(str: String, x: SkScalar, y: SkScalar, font: SkFont, paint: SkPaint) {
        records += SkRecord.DrawString(str, x, y, font.copy(), paint.copy())
    }

    override fun drawSimpleText(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {
        records += SkRecord.DrawSimpleText(text, byteLength, encoding, x, y, font.copy(), paint.copy())
    }

    override fun drawTextBlob(
        blob: org.skia.foundation.SkTextBlob,
        x: SkScalar,
        y: SkScalar,
        paint: SkPaint,
    ) {
        // Phase I1 — record by reference. SkTextBlob is immutable so a
        // shared reference between record and replay is safe ; we copy
        // only the paint (mutable).
        records += SkRecord.DrawTextBlob(blob, x, y, paint.copy())
    }

    override fun drawPicture(picture: SkPicture, matrix: SkMatrix?, paint: SkPaint?) {
        // R-suivi.22 — record a *reference* to the sub-picture rather
        // than flattening its ops into our own record list. Preserves
        // the sub-picture identity so [SkPicture.serialize] can route
        // it through [SkSerialProcs.picture]. The base-class default
        // would otherwise call `picture.playback(this)`, which would
        // re-enter our recording overrides and inline every nested op
        // — losing the sub-picture boundary.
        records += SkRecord.DrawPicture(picture, matrix, paint?.copy())
    }
}
