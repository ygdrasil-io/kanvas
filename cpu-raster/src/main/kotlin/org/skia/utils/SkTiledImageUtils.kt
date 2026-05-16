package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Mirrors Skia's
 * [`SkTiledImageUtils`](https://github.com/google/skia/blob/main/include/core/SkTiledImageUtils.h)
 * namespace.
 *
 * `DrawImage` / `DrawImageRect` are drop-in replacements for the matching
 * [SkCanvas] calls. Upstream, they exist to break up oversized
 * SkBitmap-backed images into smaller tiles that fit on the GPU before
 * forwarding to `SkCanvas::drawImageRect` ; for CPU-backed or already-GPU
 * images they fall through to the canvas directly.
 *
 * **R-suivi.8 — real tiling.** Images larger than [DEFAULT_TILE_SIZE]
 * along either axis are split into [DEFAULT_TILE_SIZE]-sized chunks
 * before forwarding to [SkCanvas.drawImage] / [SkCanvas.drawImageRect].
 * Each tile is materialised via [SkImage.makeSubset] (R2.12) and drawn
 * at the corresponding offset.
 *
 * For images that fit within the tile budget the dispatch is a single
 * direct call to the canvas — no copying, no overhead.
 */
public object SkTiledImageUtils {

    /**
     * Default tile edge in source pixels — matches upstream's
     * `SkTiledImageUtils.cpp:kDefaultTileSize` (1024). Each emitted tile
     * is ≤ `DEFAULT_TILE_SIZE × DEFAULT_TILE_SIZE` source pixels (the
     * trailing row / column may be shorter when the image dimensions are
     * not exact multiples).
     */
    public const val DEFAULT_TILE_SIZE: Int = 1024

    /**
     * Mirrors `SkTiledImageUtils::DrawImage(canvas, image, x, y, sampling, paint, constraint)`.
     * For a null [image] this is a no-op (matches upstream).
     *
     * Images smaller than [DEFAULT_TILE_SIZE] in both axes forward
     * directly to [SkCanvas.drawImage]. Larger images are split into
     * [DEFAULT_TILE_SIZE]-sized tiles via [SkImage.makeSubset] and
     * drawn at the matching offset from `(x, y)`. The tiled output is
     * pixel-equivalent to a single `drawImage` call on a hypothetical
     * GPU backend that bounded its texture upload at [DEFAULT_TILE_SIZE]
     * — kanvas-skia uses the same path on the CPU so it's a no-op
     * on small images and a memory-saving on huge ones.
     */
    public fun DrawImage(
        canvas: SkCanvas,
        image: SkImage?,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
        @Suppress("UNUSED_PARAMETER")
        constraint: SrcRectConstraint = SrcRectConstraint.kFast,
    ) {
        if (image == null) return
        if (image.width <= DEFAULT_TILE_SIZE && image.height <= DEFAULT_TILE_SIZE) {
            canvas.drawImage(image, x, y, sampling, paint)
            return
        }
        // Tile the image into [DEFAULT_TILE_SIZE] chunks. Each tile is
        // drawn at `(x + tx, y + ty)` so the visual result matches a
        // single drawImage at `(x, y)`.
        var ty = 0
        while (ty < image.height) {
            val h = minOf(DEFAULT_TILE_SIZE, image.height - ty)
            var tx = 0
            while (tx < image.width) {
                val w = minOf(DEFAULT_TILE_SIZE, image.width - tx)
                val tile = image.makeSubset(SkIRect.MakeXYWH(tx, ty, w, h))
                if (tile != null) {
                    canvas.drawImage(tile, x + tx, y + ty, sampling, paint)
                }
                tx += DEFAULT_TILE_SIZE
            }
            ty += DEFAULT_TILE_SIZE
        }
    }

    /**
     * Mirrors `SkTiledImageUtils::DrawImageRect(canvas, image, src, dst, sampling, paint, constraint)`.
     * For a null [image] this is a no-op (matches upstream).
     *
     * The source rect's tile coverage is computed in source-pixel space ;
     * each covered tile contributes a `drawImageRect` call mapping its
     * sub-source to the matching sub-destination (preserving the
     * source → destination scale of the original call). For images
     * smaller than [DEFAULT_TILE_SIZE] the dispatch is a single direct
     * call to the canvas (the same fast path as [DrawImage]).
     */
    public fun DrawImageRect(
        canvas: SkCanvas,
        image: SkImage?,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
        constraint: SrcRectConstraint = SrcRectConstraint.kFast,
    ) {
        if (image == null) return
        if (image.width <= DEFAULT_TILE_SIZE && image.height <= DEFAULT_TILE_SIZE) {
            canvas.drawImageRect(image, src, dst, sampling, paint, constraint)
            return
        }
        // Source-to-destination scale (preserved per tile so each
        // sub-rect lands at the right place in [dst]).
        val srcW = src.width()
        val srcH = src.height()
        if (srcW <= 0f || srcH <= 0f) return
        val sx = dst.width() / srcW
        val sy = dst.height() / srcH

        // Walk the tile grid that intersects [src]. Tile (i, j) covers
        // image source rect [i*S, j*S, (i+1)*S, (j+1)*S], clamped to
        // image bounds.
        val firstCol = (src.left.toInt().coerceAtLeast(0)) / DEFAULT_TILE_SIZE
        val lastCol = ((src.right.toInt() - 1).coerceAtLeast(0)) / DEFAULT_TILE_SIZE
        val firstRow = (src.top.toInt().coerceAtLeast(0)) / DEFAULT_TILE_SIZE
        val lastRow = ((src.bottom.toInt() - 1).coerceAtLeast(0)) / DEFAULT_TILE_SIZE

        var row = firstRow
        while (row <= lastRow) {
            val tileTop = row * DEFAULT_TILE_SIZE
            val tileBottom = minOf(tileTop + DEFAULT_TILE_SIZE, image.height)
            if (tileTop >= image.height) break
            var col = firstCol
            while (col <= lastCol) {
                val tileLeft = col * DEFAULT_TILE_SIZE
                val tileRight = minOf(tileLeft + DEFAULT_TILE_SIZE, image.width)
                if (tileLeft >= image.width) {
                    col++
                    continue
                }
                // Intersect the tile with [src] in image-source space.
                val isectL = maxOf(src.left, tileLeft.toFloat())
                val isectT = maxOf(src.top, tileTop.toFloat())
                val isectR = minOf(src.right, tileRight.toFloat())
                val isectB = minOf(src.bottom, tileBottom.toFloat())
                if (isectL >= isectR || isectT >= isectB) {
                    col++
                    continue
                }
                // Materialise the tile via makeSubset (R2.12).
                val subset = image.makeSubset(
                    SkIRect.MakeLTRB(tileLeft, tileTop, tileRight, tileBottom),
                )
                if (subset != null) {
                    // Tile-local source coordinates.
                    val tileSrc = SkRect.MakeLTRB(
                        isectL - tileLeft,
                        isectT - tileTop,
                        isectR - tileLeft,
                        isectB - tileTop,
                    )
                    // Destination rect mapped through the original
                    // src → dst scale.
                    val tileDst = SkRect.MakeLTRB(
                        dst.left + (isectL - src.left) * sx,
                        dst.top + (isectT - src.top) * sy,
                        dst.left + (isectR - src.left) * sx,
                        dst.top + (isectB - src.top) * sy,
                    )
                    canvas.drawImageRect(subset, tileSrc, tileDst, sampling, paint, constraint)
                }
                col++
            }
            row++
        }
    }

    /**
     * `DrawImageRect` convenience overload : when only [dst] is supplied,
     * the source rect defaults to the entire image (Skia's `MakeIWH`
     * convention).
     */
    public fun DrawImageRect(
        canvas: SkCanvas,
        image: SkImage?,
        dst: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
        constraint: SrcRectConstraint = SrcRectConstraint.kFast,
    ) {
        if (image == null) return
        val src = SkRect.MakeIWH(image.width, image.height)
        DrawImageRect(canvas, image, src, dst, sampling, paint, constraint)
    }
}
