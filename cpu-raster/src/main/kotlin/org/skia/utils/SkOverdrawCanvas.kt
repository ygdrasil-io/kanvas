package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint

/**
 * Diagnostic canvas that counts how many times each pixel is
 * touched by a draw call. Mirrors Skia's
 * [`SkOverdrawCanvas`](https://github.com/google/skia/blob/main/include/core/SkOverdrawCanvas.h).
 *
 * **Use case** : detect overdraw — situations where a single output
 * pixel is rasterised multiple times (a translucent layer drawn
 * over an opaque background, for instance). Each draw call
 * increments the alpha channel of the destination by `1`. After
 * the GM completes, the destination bitmap's per-pixel alpha
 * encodes how many draws touched it. Skia's standard idiom maps
 * the count to a heat-map colour : 0 (no draws) → black, 1 →
 * dark blue, 2 → blue, … 5+ → red.
 *
 * **Mechanism** : every draw method substitutes the caller's paint
 * with [OVERDRAW_PAINT] — alpha = 1, blend = `kPlus` — and
 * forwards to the wrapped target. The target's pixel alpha
 * accumulates +1 per draw via the additive blend.
 *
 * State changes (save / restore / matrix / clip) are forwarded to
 * **both** the wrapper's local state stack (so [getTotalMatrix]
 * stays accurate for analysis) and the target.
 *
 * Usage :
 * ```kotlin
 * val target = SkCanvas(SkBitmap(...))  // black, alpha = 0
 * val overdraw = SkOverdrawCanvas(target)
 * gm.draw(overdraw)
 * // bitmap.getPixel(x, y) >> 24 == count of draws that touched (x, y)
 * ```
 *
 * **Caveats** :
 *  - Subclasses can specialise [overdrawPaint] if a different
 *    increment is wanted (e.g. coloured per-draw-class buckets).
 *  - The overdraw paint deliberately strips the caller's shader,
 *    colour filter, mask filter, image filter, path effect, AA flag.
 *    Counts are exactly +1 per draw regardless of the original
 *    paint complexity.
 */
public open class SkOverdrawCanvas(
    private val target: SkCanvas,
) : SkPaintFilterCanvas(target) {

    /**
     * Substitute the caller's paint with the overdraw paint. The
     * filter always returns `true` (every draw is forwarded ; the
     * counting comes from the substituted paint, not from skipping).
     */
    final override fun onFilter(paint: SkPaint): Boolean {
        // Mutate `paint` in place : the parent class passes a copy,
        // so we can stomp every field without affecting the caller's
        // original.
        paint.color = OVERDRAW_PAINT.color
        paint.blendMode = OVERDRAW_PAINT.blendMode
        paint.shader = null
        paint.colorFilter = null
        paint.maskFilter = null
        paint.imageFilter = null
        paint.pathEffect = null
        paint.isAntiAlias = false
        return true
    }

    public companion object {
        /**
         * The canonical overdraw paint : a fully-opaque-alpha-1 source
         * combined with the destination via [SkBlendMode.kPlus] to
         * accumulate `+1` per draw on the alpha channel.
         */
        public val OVERDRAW_PAINT: SkPaint = SkPaint(
            SkColorSetARGB(0x01, 0x00, 0x00, 0x00),
        ).apply {
            blendMode = SkBlendMode.kPlus
            isAntiAlias = false
        }
    }
}
