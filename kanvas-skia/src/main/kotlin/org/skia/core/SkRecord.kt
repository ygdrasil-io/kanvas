package org.skia.core

import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * One recorded canvas op. The closed hierarchy mirrors Skia's
 * `SkRecord` slot types — each variant captures the exact arguments
 * needed to replay the call against a live [SkCanvas].
 *
 * Mutable arguments ([SkPaint], [SkFont]) are deep-copied at recording
 * time so subsequent client-side mutation of the original instances
 * does not leak into the recorded picture. Immutable arguments
 * ([SkPath], [SkRect], [SkMatrix], [SkRRect], [SkImage]) are kept by
 * reference — they're either `data class`es, immutable values, or
 * snapshots whose underlying state cannot change.
 *
 * Adding a new canvas op : add a new sealed `class`, then handle it
 * in [SkRecordingCanvas]'s override and [SkPicture.playback]'s
 * dispatch. Both files will fail to compile if a variant is forgotten —
 * Kotlin's exhaustiveness check on sealed `when` is the safety net.
 */
public sealed class SkRecord {
    // -- State / matrix / clip ------------------------------------------------
    public object Save : SkRecord()
    public object Restore : SkRecord()
    public class SaveLayer(
        public val bounds: SkRect?,
        public val paint: SkPaint?,
    ) : SkRecord()
    public class Translate(public val dx: SkScalar, public val dy: SkScalar) : SkRecord()
    public class Scale(public val sx: SkScalar, public val sy: SkScalar) : SkRecord()
    public class Rotate(public val deg: SkScalar) : SkRecord()
    public class RotatePivot(
        public val deg: SkScalar,
        public val px: SkScalar,
        public val py: SkScalar,
    ) : SkRecord()
    public class Skew(public val sx: SkScalar, public val sy: SkScalar) : SkRecord()
    public class Concat(public val matrix: SkMatrix) : SkRecord()
    public class SetMatrix(public val matrix: SkMatrix) : SkRecord()
    public object ResetMatrix : SkRecord()
    public class ClipRect(public val rect: SkRect, public val doAntiAlias: Boolean) : SkRecord()

    // -- Draw -----------------------------------------------------------------
    public class DrawPaint(public val paint: SkPaint) : SkRecord()
    public class DrawColor(public val color: SkColor, public val mode: SkBlendMode) : SkRecord()
    public class DrawRect(public val rect: SkRect, public val paint: SkPaint) : SkRecord()
    public class DrawOval(public val oval: SkRect, public val paint: SkPaint) : SkRecord()
    public class DrawCircle(
        public val cx: SkScalar,
        public val cy: SkScalar,
        public val radius: SkScalar,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawRRect(public val rrect: SkRRect, public val paint: SkPaint) : SkRecord()
    public class DrawRoundRect(
        public val rect: SkRect,
        public val rx: SkScalar,
        public val ry: SkScalar,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawDRRect(
        public val outer: SkRRect,
        public val inner: SkRRect,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawLine(
        public val x0: SkScalar,
        public val y0: SkScalar,
        public val x1: SkScalar,
        public val y1: SkScalar,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawArc(
        public val oval: SkRect,
        public val startAngleDeg: SkScalar,
        public val sweepAngleDeg: SkScalar,
        public val useCenter: Boolean,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawPath(public val path: SkPath, public val paint: SkPaint) : SkRecord()
    public class DrawImage(
        public val image: SkImage,
        public val x: SkScalar,
        public val y: SkScalar,
        public val sampling: SkSamplingOptions,
        public val paint: SkPaint?,
    ) : SkRecord()
    public class DrawImageRect(
        public val image: SkImage,
        public val src: SkRect,
        public val dst: SkRect,
        public val sampling: SkSamplingOptions,
        public val paint: SkPaint?,
        public val constraint: SrcRectConstraint,
    ) : SkRecord()
    public class DrawString(
        public val str: String,
        public val x: SkScalar,
        public val y: SkScalar,
        public val font: SkFont,
        public val paint: SkPaint,
    ) : SkRecord()
    public class DrawSimpleText(
        public val text: String,
        public val byteLength: Int,
        public val encoding: SkTextEncoding,
        public val x: SkScalar,
        public val y: SkScalar,
        public val font: SkFont,
        public val paint: SkPaint,
    ) : SkRecord()

    /** Phase I1 — `SkCanvas::drawTextBlob`. */
    public class DrawTextBlob(
        public val blob: org.skia.foundation.SkTextBlob,
        public val x: SkScalar,
        public val y: SkScalar,
        public val paint: SkPaint,
    ) : SkRecord()

    /**
     * R-suivi.22 — `SkCanvas::drawPicture`. Carries a reference to the
     * sub-picture (immutable, share-safe), the optional pre-multiply
     * matrix, and an optional paint for `saveLayer`-style composition.
     *
     * Recording a sub-picture as a *reference* (rather than flattening
     * its ops into the outer record list at recording time) is what
     * lets [SkPicture.serialize] route the sub-picture through
     * [org.skia.foundation.SkSerialProcs.picture] — the proc fires
     * once per `DrawPicture` op encountered.
     */
    public class DrawPicture(
        public val picture: org.skia.core.SkPicture,
        public val matrix: SkMatrix?,
        public val paint: SkPaint?,
    ) : SkRecord()
}
