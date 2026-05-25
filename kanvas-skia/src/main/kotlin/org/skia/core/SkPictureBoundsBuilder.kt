package org.skia.core

import org.skia.foundation.SkPaint
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Computes per-op device-space bounds for the records of an
 * [SkPicture]. Walks the recording's record list once, tracking the
 * recording-time CTM through a matrix stack, and produces an array
 * of bounds — one per record, indexed identically to
 * `records[i] -> bounds[i]`.
 *
 * **Used by [SkPictureRecorder.finishRecordingAsPicture]** when an
 * [SkBBHFactory] is supplied : the bounds are bulk-loaded into the
 * factory's hierarchy, and at playback the picture queries the
 * hierarchy with the canvas's local clip to skip ops that don't
 * intersect.
 *
 * **Bounds policy** :
 *  - Geometric draws (rect / oval / circle / line / path / image)
 *    receive **tight** bounds — the local-space geometry mapped
 *    through the recording-time CTM, optionally outset by half the
 *    paint's stroke width when the paint is stroked.
 *  - Whole-canvas draws (`drawPaint`, `drawColor`) and text-class
 *    draws (`drawString`, `drawSimpleText`, `drawTextBlob`) receive
 *    the picture's [cullRect] — they always intersect any sub-rect
 *    query, so they are never culled. (Text bounds are non-trivial
 *    to compute precisely without rasterising glyphs ; conservative
 *    is correct.)
 *  - **State ops** (`save` / `restore` / `translate` / `scale` /
 *    `rotate` / `skew` / `concat` / `setMatrix` / `resetMatrix` /
 *    `clipRect`) likewise receive [cullRect] — they must replay in
 *    full so the CTM / clip stack stays in lockstep with the picture.
 *
 * **Effect on the BBH** : because state ops always carry [cullRect]
 * bounds, the R-tree's interior-node bounds collapse toward
 * [cullRect] near the root. But leaf-level pruning still works :
 * draw ops with tight bounds are correctly skipped, and result
 * size scales with the number of intersecting *draw* ops, not with
 * total record count. State ops are O(stateCount) in the result,
 * which is typically much smaller than the draw count for normal
 * pictures.
 */
internal object SkPictureBoundsBuilder {

    /**
     * Build per-op bounds for [records], in device-coordinates of
     * the recording canvas's own frame (CTM tracked from identity).
     * The returned array has one entry per record ; `bounds[i]` is
     * the index-`i` op's bounding box.
     */
    fun build(records: List<SkRecord>, cullRect: SkRect): Array<SkRect> {
        val n = records.size
        val out = Array(n) { SkRect.MakeEmpty() }
        // Matrix stack mirrors the recording's save / restore stack.
        // We start with identity (the recording canvas's initial CTM).
        val mat = ArrayDeque<SkMatrix>().apply { addLast(SkMatrix.Identity) }

        fun current(): SkMatrix = mat.last()
        fun setTop(m: SkMatrix) {
            mat.removeLast()
            mat.addLast(m)
        }

        for (i in records.indices) {
            val r = records[i]
            when (r) {
                // ─── State ops — always replay (cullRect bounds) ─────────
                SkRecord.Save -> {
                    mat.addLast(current())
                    out[i] = cullRect
                }
                SkRecord.Restore -> {
                    if (mat.size > 1) mat.removeLast()
                    out[i] = cullRect
                }
                is SkRecord.SaveLayer -> {
                    mat.addLast(current())
                    out[i] = cullRect
                }
                is SkRecord.Translate -> {
                    setTop(current().preTranslate(r.dx, r.dy))
                    out[i] = cullRect
                }
                is SkRecord.Scale -> {
                    setTop(current().preScale(r.sx, r.sy))
                    out[i] = cullRect
                }
                is SkRecord.Rotate -> {
                    setTop(current().preRotate(r.deg))
                    out[i] = cullRect
                }
                is SkRecord.RotatePivot -> {
                    setTop(current().preRotate(r.deg, r.px, r.py))
                    out[i] = cullRect
                }
                is SkRecord.Skew -> {
                    setTop(current().preSkew(r.sx, r.sy))
                    out[i] = cullRect
                }
                is SkRecord.Concat -> {
                    setTop(current().preConcat(r.matrix))
                    out[i] = cullRect
                }
                is SkRecord.SetMatrix -> {
                    setTop(r.matrix)
                    out[i] = cullRect
                }
                SkRecord.ResetMatrix -> {
                    setTop(SkMatrix.Identity)
                    out[i] = cullRect
                }
                is SkRecord.ClipRect -> {
                    out[i] = cullRect
                }

                // ─── Whole-canvas draws — always replay ──────────────────
                is SkRecord.DrawPaint -> out[i] = cullRect
                is SkRecord.DrawColor -> out[i] = cullRect

                // ─── Geometric draws — tight bounds, mapped through CTM ──
                is SkRecord.DrawRect -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.rect))
                is SkRecord.DrawOval -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.oval))
                is SkRecord.DrawCircle -> {
                    val rect = SkRect.MakeLTRB(
                        r.cx - r.radius, r.cy - r.radius,
                        r.cx + r.radius, r.cy + r.radius,
                    )
                    out[i] = current().mapRect(adjustForPaint(r.paint, rect))
                }
                is SkRecord.DrawRRect -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.rrect.getBounds()))
                is SkRecord.DrawRoundRect -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.rect))
                is SkRecord.DrawDRRect -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.outer.getBounds()))
                is SkRecord.DrawArc -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.oval))
                is SkRecord.DrawLine -> {
                    val rect = SkRect.MakeLTRB(
                        minOf(r.x0, r.x1), minOf(r.y0, r.y1),
                        maxOf(r.x0, r.x1), maxOf(r.y0, r.y1),
                    )
                    out[i] = current().mapRect(adjustForPaint(r.paint, rect))
                }
                is SkRecord.DrawPath -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.path.computeBounds()))
                is SkRecord.DrawImage -> {
                    val rect = SkRect.MakeXYWH(
                        r.x, r.y,
                        r.image.width.toFloat(),
                        r.image.height.toFloat(),
                    )
                    out[i] = current().mapRect(rect)
                }
                is SkRecord.DrawImageRect -> out[i] = current().mapRect(r.dst)

                // ─── Text — too tricky for tight bounds without glyph
                //         metrics ; conservative cullRect is correct.
                is SkRecord.DrawString -> out[i] = cullRect
                is SkRecord.DrawSimpleText -> out[i] = cullRect
                is SkRecord.DrawTextBlob -> out[i] = cullRect
                is SkRecord.DrawMesh -> out[i] =
                    current().mapRect(adjustForPaint(r.paint, r.mesh.bounds()))
                is SkRecord.DrawPicture -> out[i] = cullRect
            }
        }
        return out
    }

    /**
     * Outset a stroked rect by half the paint's stroke width. Fill-only
     * paints return [rect] unchanged. Mirrors Skia's
     * `paint.computeFastBounds(rect)` which Op-rules apply for stroke +
     * fill coverage prior to BBH insertion.
     */
    private fun adjustForPaint(paint: SkPaint, rect: SkRect): SkRect {
        if (paint.style == SkPaint.Style.kFill_Style) return rect
        val halfStroke = paint.strokeWidth * 0.5f
        return SkRect.MakeLTRB(
            rect.left - halfStroke,
            rect.top - halfStroke,
            rect.right + halfStroke,
            rect.bottom + halfStroke,
        )
    }
}
