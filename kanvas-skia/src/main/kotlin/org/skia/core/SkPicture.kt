package org.skia.core

import org.skia.math.SkRect

/**
 * Mirrors Skia's
 * [`SkPicture`](https://github.com/google/skia/blob/main/include/core/SkPicture.h)
 * — an immutable, replay-able list of canvas operations.
 *
 * Pictures are produced by [SkPictureRecorder.finishRecordingAsPicture]
 * and consumed by [playback], which dispatches each [SkRecord] back
 * to a live [SkCanvas]. The same picture can be played into many
 * canvases / surfaces — the foundation primitive of the upstream-DM
 * sink architecture (record once, play into many backends).
 *
 * The [cullRect] is the recorder's declared bounds, useful as a hint
 * for clipping or culling at playback time. We don't enforce it
 * (matches Skia's contract — the cull rect is advisory, not a clip).
 *
 * **Phase Q3 — bounding-box-hierarchy cull** : when the recorder is
 * given an [SkBBHFactory] (e.g. [SkRTreeFactory]), the resulting
 * picture stores per-op bounds in the hierarchy. At [playback] we
 * query the hierarchy with the playback canvas's local clip and
 * dispatch only the recorded ops whose bounds intersect — turning
 * an O(N) walk into O(log N + K) for K = result size on tight
 * clips.
 */
public class SkPicture internal constructor(
    public val cullRect: SkRect,
    private val records: List<SkRecord>,
    /**
     * Bulk-loaded over the same op order as [records]. `null` when
     * the recording was started without an [SkBBHFactory] — playback
     * falls back to a linear walk in that case.
     */
    private val bbh: SkBBoxHierarchy? = null,
) {
    /** Number of recorded ops — useful for diagnostics and tests. */
    public val opCount: Int get() = records.size

    /**
     * `true` iff this picture carries a bounding-box hierarchy and
     * playback can cull non-intersecting ops. Useful for test
     * assertions ; not a public correctness guarantee.
     */
    public val hasBBH: Boolean get() = bbh != null

    /**
     * Replay every recorded op against [canvas], in order. Bounds set
     * up by save/saveLayer/clipRect/translate inside the picture do
     * **not** leak past the playback : we wrap the whole sequence in
     * a save/restoreToCount pair so the canvas's external state is
     * preserved (matches Skia's `SkPicture::playback` semantics).
     *
     * **Cull path** : if [bbh] is non-null and the canvas's local
     * clip is a strict sub-rect of [cullRect], we ask the hierarchy
     * which ops intersect and dispatch only those — preserving
     * insertion order (the BBH search returns sorted indices).
     * Otherwise (no BBH, or full-cover clip) we walk every record
     * linearly.
     */
    public fun playback(canvas: SkCanvas) {
        val rootCount = canvas.getSaveCount()
        canvas.save()
        try {
            val tree = bbh
            if (tree != null) {
                val query = canvas.getLocalClipBounds()
                if (query.isEmpty) {
                    // Empty clip — nothing to draw. Restore and return.
                    return
                }
                if (!query.contains(cullRect)) {
                    // Sub-rect clip — query the hierarchy.
                    val hits = tree.search(query)
                    for (i in hits) dispatch(canvas, records[i])
                    return
                }
                // Else fall through to the linear walk — no perf gain
                // from a search that would return every index.
            }
            for (r in records) dispatch(canvas, r)
        } finally {
            // Even if a record handler throws, restore to the depth we
            // saw at entry so the caller's state is intact.
            canvas.restoreToCount(rootCount)
        }
    }

    private fun dispatch(c: SkCanvas, r: SkRecord) {
        when (r) {
            // -- State -------------------------------------------------------
            SkRecord.Save -> c.save()
            SkRecord.Restore -> c.restore()
            is SkRecord.SaveLayer -> c.saveLayer(r.bounds, r.paint)
            is SkRecord.Translate -> c.translate(r.dx, r.dy)
            is SkRecord.Scale -> c.scale(r.sx, r.sy)
            is SkRecord.Rotate -> c.rotate(r.deg)
            is SkRecord.RotatePivot -> c.rotate(r.deg, r.px, r.py)
            is SkRecord.Skew -> c.skew(r.sx, r.sy)
            is SkRecord.Concat -> c.concat(r.matrix)
            is SkRecord.SetMatrix -> c.setMatrix(r.matrix)
            SkRecord.ResetMatrix -> c.resetMatrix()
            is SkRecord.ClipRect -> c.clipRect(r.rect, r.doAntiAlias)

            // -- Draw --------------------------------------------------------
            is SkRecord.DrawPaint -> c.drawPaint(r.paint)
            is SkRecord.DrawColor -> c.drawColor(r.color, r.mode)
            is SkRecord.DrawRect -> c.drawRect(r.rect, r.paint)
            is SkRecord.DrawOval -> c.drawOval(r.oval, r.paint)
            is SkRecord.DrawCircle -> c.drawCircle(r.cx, r.cy, r.radius, r.paint)
            is SkRecord.DrawRRect -> c.drawRRect(r.rrect, r.paint)
            is SkRecord.DrawRoundRect -> c.drawRoundRect(r.rect, r.rx, r.ry, r.paint)
            is SkRecord.DrawDRRect -> c.drawDRRect(r.outer, r.inner, r.paint)
            is SkRecord.DrawLine -> c.drawLine(r.x0, r.y0, r.x1, r.y1, r.paint)
            is SkRecord.DrawArc -> c.drawArc(r.oval, r.startAngleDeg, r.sweepAngleDeg, r.useCenter, r.paint)
            is SkRecord.DrawPath -> c.drawPath(r.path, r.paint)
            is SkRecord.DrawImage -> c.drawImage(r.image, r.x, r.y, r.sampling, r.paint)
            is SkRecord.DrawImageRect ->
                c.drawImageRect(r.image, r.src, r.dst, r.sampling, r.paint, r.constraint)
            is SkRecord.DrawString -> c.drawString(r.str, r.x, r.y, r.font, r.paint)
            is SkRecord.DrawSimpleText ->
                c.drawSimpleText(r.text, r.byteLength, r.encoding, r.x, r.y, r.font, r.paint)
            is SkRecord.DrawTextBlob -> c.drawTextBlob(r.blob, r.x, r.y, r.paint)
        }
    }
}
