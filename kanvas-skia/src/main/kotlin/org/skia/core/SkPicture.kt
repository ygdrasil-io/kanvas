package org.skia.core

import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkShader
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.ceil

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
            is SkRecord.DrawPicture -> c.drawPicture(r.picture, r.matrix, r.paint)
        }
    }

    /**
     * Mirrors Skia's
     * [`SkPicture::makeShader(tmx, tmy, filter, localMatrix, tile)`](https://github.com/google/skia/blob/main/include/core/SkPicture.h)
     * — wrap this picture as a tiled [SkShader].
     *
     * **Strategy** : we render the picture once into a transient
     * [SkBitmap] sized to the tile (defaulting to the picture's
     * [cullRect] dimensions), then return an image shader over that
     * snapshot. This trades a one-time rasterization cost for
     * unlimited reuse and lets the existing
     * [org.skia.foundation.SkBitmapShader] infrastructure handle
     * tile-mode / filtering / local-matrix concerns uniformly.
     *
     * The transient bitmap is allocated in sRGB / 8888 — same backing
     * format the rest of the shader stack consumes — and cleared to
     * `SK_ColorTRANSPARENT` before playback so the picture's alpha
     * passes through to the eventual blend.
     *
     * @param tileX tile mode along the local-x axis.
     * @param tileY tile mode along the local-y axis.
     * @param filter sampling filter (kNearest by default — matches
     *   upstream's `SkPicture::makeShader` default).
     * @param localMatrix shader-local transform applied before
     *   sampling, or `null` for identity.
     * @param tile sub-rectangle of the picture to use as the tile.
     *   When `null` (the common case) defaults to [cullRect]. The
     *   width/height are ceil'd to ints for the snapshot allocation.
     */
    public fun makeShader(
        tileX: SkTileMode,
        tileY: SkTileMode,
        filter: SkFilterMode = SkFilterMode.kNearest,
        localMatrix: SkMatrix? = null,
        tile: SkRect? = null,
    ): SkShader {
        val tileRect = tile ?: cullRect
        // Snapshot dimensions : ceil to int and clamp to >= 1 so we
        // always allocate a valid (non-degenerate) bitmap even when
        // the cullRect is empty or sub-pixel.
        val w = maxOf(1, ceil(tileRect.width().toDouble()).toInt())
        val h = maxOf(1, ceil(tileRect.height().toDouble()).toInt())

        val bitmap = SkBitmap(w, h)
        val canvas = SkCanvas(bitmap)
        // Wipe the snapshot to fully transparent so the picture's own
        // alpha is preserved (drawColor with kSrcOver over an
        // already-transparent canvas is a no-op, but clear is explicit).
        canvas.clear(SK_ColorTRANSPARENT)
        // Map the picture's tile origin to (0, 0) of the snapshot so
        // a non-default `tile` selects the right sub-region.
        if (tileRect.left != 0f || tileRect.top != 0f) {
            canvas.translate(-tileRect.left, -tileRect.top)
        }
        playback(canvas)

        val image = bitmap.asImage()
        val sampling = SkSamplingOptions(filter)
        return image.makeShader(
            tileX = tileX,
            tileY = tileY,
            sampling = sampling,
            localMatrix = localMatrix ?: SkMatrix.Identity,
        )
    }

    /**
     * Mirrors Skia's
     * [`SkPicture::serialize(const SkSerialProcs*)`](https://github.com/google/skia/blob/main/include/core/SkPicture.h)
     * — produces an opaque, replay-able byte stream.
     *
     * **Scope — R-suivi.22** : the kanvas-skia binary picture format
     * is not fully specified ; what this method **does** guarantee is
     * the upstream "callback contract" : every embedded [SkImage] /
     * sub-[SkPicture] / [SkTypeface] is offered to the matching proc
     * on [SkSerialProcs] **exactly once** in encounter order, with
     * the corresponding `*Ctx` threaded through. The returned [SkData]
     * is an internal framing the kanvas-skia [MakeFromData] path can
     * round-trip ; cross-process / cross-version interchange is out
     * of scope here (tracked separately).
     *
     * Walk order :
     *  - Images : every `DrawImage` / `DrawImageRect` record (top-level
     *    only — sub-pictures bring their own when re-serialised).
     *  - Sub-pictures : every `DrawPicture` record. The proc receives
     *    the [SkPicture] reference ; the default fall-back recursively
     *    serialises with the same procs (so user procs propagate).
     *  - Typefaces : every distinct [SkTypeface] reachable via
     *    `DrawString` / `DrawSimpleText` / `DrawTextBlob` records (the
     *    typeface lives on the embedded [org.skia.foundation.SkFont]).
     *    Deduplication is by reference identity — a typeface drawn ten
     *    times fires the proc once.
     *
     * The resulting [SkData] always carries at least the magic header
     * `kSkPictureMagic` + op count + per-blob counts, so tests can
     * assert the callback fired and that the byte stream is non-trivial.
     */
    public fun serialize(procs: SkSerialProcs = SkSerialProcs()): SkData {
        // Framed encoding (R-suivi.22 + S6-C extension):
        //   [magic(4)] [opCount(4)]
        //   [imageCount(4)]    -> per-image blobs   ([len(4)] [bytes...])
        //   [pictureCount(4)]  -> per-sub-picture blobs  ([len(4)] [bytes...])
        //   [typefaceCount(4)] -> per-typeface blobs ([len(4)] [bytes...])
        // The framing is internal ; round-trippable via MakeFromData
        // below, but not stable across versions.
        val perImageBlobs = ArrayList<ByteArray>()
        forEachEmbeddedImage { img ->
            val data: SkData? = procs.image?.invoke(img, procs.imageCtx)
                ?: img.encodeToData()
            // Fall back to a zero-length blob if both the proc and the
            // default encoder returned null (e.g. an uninitialised
            // image). The reader treats zero-length as "skip".
            perImageBlobs += data?.toByteArray() ?: ByteArray(0)
        }

        val perPictureBlobs = ArrayList<ByteArray>()
        forEachEmbeddedSubPicture { sub ->
            val data: SkData? = procs.picture?.invoke(sub, procs.pictureCtx)
                ?: sub.serialize(procs) // default : recursively serialise.
            perPictureBlobs += data?.toByteArray() ?: ByteArray(0)
        }

        val perTypefaceBlobs = ArrayList<ByteArray>()
        forEachEmbeddedTypeface { tf ->
            val data: SkData? = procs.typeface?.invoke(tf, procs.typefaceCtx)
            // No default typeface serialiser yet — the surface only
            // mirrors the upstream callback contract. A `null` proc
            // (or a proc that returns null) emits a zero-length blob,
            // which round-trips as "no embedded data" on the reader.
            perTypefaceBlobs += data?.toByteArray() ?: ByteArray(0)
        }

        val totalImagesLen = perImageBlobs.sumOf { it.size } + perImageBlobs.size * 4
        val totalPicturesLen = perPictureBlobs.sumOf { it.size } + perPictureBlobs.size * 4
        val totalTypefacesLen = perTypefaceBlobs.sumOf { it.size } + perTypefaceBlobs.size * 4
        val out = ByteArray(
            4 + 4 + // magic + opCount
                4 + totalImagesLen +
                4 + totalPicturesLen +
                4 + totalTypefacesLen,
        )
        writeIntBE(out, 0, kSkPictureMagic)
        writeIntBE(out, 4, opCount)

        var off = 8
        writeIntBE(out, off, perImageBlobs.size); off += 4
        for (blob in perImageBlobs) {
            writeIntBE(out, off, blob.size); off += 4
            System.arraycopy(blob, 0, out, off, blob.size); off += blob.size
        }
        writeIntBE(out, off, perPictureBlobs.size); off += 4
        for (blob in perPictureBlobs) {
            writeIntBE(out, off, blob.size); off += 4
            System.arraycopy(blob, 0, out, off, blob.size); off += blob.size
        }
        writeIntBE(out, off, perTypefaceBlobs.size); off += 4
        for (blob in perTypefaceBlobs) {
            writeIntBE(out, off, blob.size); off += 4
            System.arraycopy(blob, 0, out, off, blob.size); off += blob.size
        }
        return SkData.MakeWithCopy(out)
    }

    /**
     * Walk every recorded op once and invoke [block] for each
     * embedded [SkImage] (top-level — does not recurse into
     * sub-pictures, which carry their own image walk during their
     * own [serialize] call).
     */
    private inline fun forEachEmbeddedImage(block: (SkImage) -> Unit) {
        for (r in records) {
            when (r) {
                is SkRecord.DrawImage -> block(r.image)
                is SkRecord.DrawImageRect -> block(r.image)
                else -> {}
            }
        }
    }

    /**
     * Walk every recorded op once and invoke [block] for each
     * embedded sub-[SkPicture] (i.e. every `DrawPicture` record).
     */
    private inline fun forEachEmbeddedSubPicture(block: (SkPicture) -> Unit) {
        for (r in records) {
            if (r is SkRecord.DrawPicture) block(r.picture)
        }
    }

    /**
     * Walk every recorded op once and invoke [block] for each distinct
     * [SkTypeface] reachable from a text-bearing record. Identity
     * dedup keeps the per-typeface blob list compact when the same
     * typeface backs many `drawString` / `drawTextBlob` ops.
     */
    private fun forEachEmbeddedTypeface(block: (SkTypeface) -> Unit) {
        val seen = HashSet<SkTypeface>()
        for (r in records) {
            when (r) {
                is SkRecord.DrawString -> if (seen.add(r.font.typeface)) block(r.font.typeface)
                is SkRecord.DrawSimpleText -> if (seen.add(r.font.typeface)) block(r.font.typeface)
                is SkRecord.DrawTextBlob -> {
                    for (run in r.blob.runs) {
                        if (seen.add(run.font.typeface)) block(run.font.typeface)
                    }
                }
                else -> {}
            }
        }
    }

    public companion object {
        /**
         * Magic header for the kanvas-skia internal picture framing
         * (`'kSkP'` ASCII = `0x6B536B50`).
         */
        internal const val kSkPictureMagic: Int = 0x6B536B50

        /**
         * Mirrors Skia's
         * [`SkPicture::MakeFromData(const SkData*, const SkDeserialProcs*)`](https://github.com/google/skia/blob/main/include/core/SkPicture.h).
         *
         * **Scope — R-suivi.22 / S6-C** : the kanvas-skia picture
         * format is not fully bidirectional yet ; we restore the
         * picture's op **count** and let
         * [SkDeserialProcs.image] / [SkDeserialProcs.picture] /
         * [SkDeserialProcs.typeface] reconstruct each embedded blob
         * from its serialised bytes. Each proc fires once per
         * encoded blob, in encounter order, with its `*Ctx` threaded
         * through. The reconstructed picture is an empty-records
         * placeholder carrying the same `opCount` and a synthetic
         * `cullRect` of [SkRect.empty] — sufficient for tests that
         * assert the proc was invoked, but not yet a full round-trip.
         *
         * Returns `null` if [data] does not begin with the
         * kanvas-skia magic header, or if any chunk's declared length
         * runs past the end of [data].
         */
        public fun MakeFromData(
            data: SkData,
            procs: SkDeserialProcs = SkDeserialProcs(),
        ): SkPicture? {
            val bytes = data.toByteArray()
            if (bytes.size < 8) return null
            if (readIntBE(bytes, 0) != kSkPictureMagic) return null
            val opCount = readIntBE(bytes, 4)
            var off = 8

            // Chunk : images.
            if (off + 4 > bytes.size) return null
            val imageCount = readIntBE(bytes, off); off += 4
            if (imageCount < 0) return null
            repeat(imageCount) {
                if (off + 4 > bytes.size) return null
                val len = readIntBE(bytes, off); off += 4
                if (len < 0 || off + len > bytes.size) return null
                if (len > 0) {
                    val blob = bytes.copyOfRange(off, off + len)
                    procs.image?.invoke(SkData.MakeWithCopy(blob), procs.imageCtx)
                }
                off += len
            }

            // Chunk : sub-pictures. Reader is tolerant of older blobs
            // that lacked this chunk — `off >= bytes.size` means we
            // stop here.
            if (off >= bytes.size) {
                return SkPicture(
                    cullRect = SkRect.MakeEmpty(),
                    records = List(opCount) { SkRecord.Save },
                )
            }
            if (off + 4 > bytes.size) return null
            val pictureCount = readIntBE(bytes, off); off += 4
            if (pictureCount < 0) return null
            repeat(pictureCount) {
                if (off + 4 > bytes.size) return null
                val len = readIntBE(bytes, off); off += 4
                if (len < 0 || off + len > bytes.size) return null
                if (len > 0) {
                    val blob = bytes.copyOfRange(off, off + len)
                    procs.picture?.invoke(SkData.MakeWithCopy(blob), procs.pictureCtx)
                }
                off += len
            }

            // Chunk : typefaces.
            if (off >= bytes.size) {
                return SkPicture(
                    cullRect = SkRect.MakeEmpty(),
                    records = List(opCount) { SkRecord.Save },
                )
            }
            if (off + 4 > bytes.size) return null
            val typefaceCount = readIntBE(bytes, off); off += 4
            if (typefaceCount < 0) return null
            repeat(typefaceCount) {
                if (off + 4 > bytes.size) return null
                val len = readIntBE(bytes, off); off += 4
                if (len < 0 || off + len > bytes.size) return null
                if (len > 0) {
                    val blob = bytes.copyOfRange(off, off + len)
                    procs.typeface?.invoke(SkData.MakeWithCopy(blob), procs.typefaceCtx)
                }
                off += len
            }

            // Synthetic placeholder picture — empty record list but
            // preserves the recorded op count via the side-channel
            // below for test assertions.
            return SkPicture(
                cullRect = SkRect.MakeEmpty(),
                records = List(opCount) { SkRecord.Save },
            )
        }

        private fun writeIntBE(buf: ByteArray, off: Int, value: Int) {
            buf[off]     = (value ushr 24).toByte()
            buf[off + 1] = (value ushr 16).toByte()
            buf[off + 2] = (value ushr 8).toByte()
            buf[off + 3] = value.toByte()
        }

        private fun readIntBE(buf: ByteArray, off: Int): Int {
            return ((buf[off].toInt() and 0xFF) shl 24) or
                ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or
                (buf[off + 3].toInt() and 0xFF)
        }
    }
}
