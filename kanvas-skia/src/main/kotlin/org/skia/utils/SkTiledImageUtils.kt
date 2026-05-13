package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Mirrors Skia's
 * [`SkTiledImageUtils`](https://github.com/google/skia/blob/main/include/core/SkTiledImageUtils.h)
 * namespace (R1 port).
 *
 * `DrawImage` / `DrawImageRect` are drop-in replacements for the matching
 * [SkCanvas] calls. Upstream, they exist to break up oversized
 * SkBitmap-backed images into smaller tiles that fit on the GPU before
 * forwarding to `SkCanvas::drawImageRect` ; for CPU-backed or already-GPU
 * images they fall through to the canvas directly.
 *
 * **R1 implementation note** — kanvas-skia is a pure CPU rasterizer, so
 * there's no GPU upload step to worry about and no need to tile. We
 * simply delegate to [SkCanvas.drawImage] / [SkCanvas.drawImageRect].
 * The public signatures match upstream so calling code can be lifted
 * verbatim ; if a future GPU backend lands, tiling can be added behind
 * this object without breaking callers.
 */
public object SkTiledImageUtils {

    /**
     * Mirrors `SkTiledImageUtils::DrawImage(canvas, image, x, y, sampling, paint, constraint)`.
     * For a null [image] this is a no-op (matches upstream).
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
        canvas.drawImage(image, x, y, sampling, paint)
    }

    /**
     * Mirrors `SkTiledImageUtils::DrawImageRect(canvas, image, src, dst, sampling, paint, constraint)`.
     * For a null [image] this is a no-op (matches upstream).
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
        canvas.drawImageRect(image, src, dst, sampling, paint, constraint)
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
