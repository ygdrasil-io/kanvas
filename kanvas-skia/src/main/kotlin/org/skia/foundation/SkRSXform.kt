package org.skia.foundation

import kotlin.math.cos
import kotlin.math.sin

/**
 * Iso-aligned port of Skia's
 * [`SkRSXform`](https://github.com/google/skia/blob/main/include/core/SkRSXform.h).
 *
 * A combined Rotation + Scale + Translate transform encoded as four
 * floats. Used by [org.skia.core.SkCanvas.drawAtlas] to position
 * sprites without paying the cost of a full 3×3 [org.skia.math.SkMatrix]
 * per draw. The 2×2 affine part is `[[fSCos, -fSSin], [fSSin, fSCos]]`
 * (a uniform-scale + rotation), and the post-transform translation
 * is `(fTx, fTy)`.
 *
 * Mapping a source point `(x, y)` :
 * ```
 * dst.x = fSCos * x − fSSin * y + fTx
 * dst.y = fSSin * x + fSCos * y + fTy
 * ```
 *
 * For `drawAtlas`, the input source coordinates are the absolute
 * pixel positions inside the atlas image (not relative to the src
 * rect's origin) — Skia pre-shifts via [Make] so the rotation /
 * scale pivots around the source rect's top-left.
 */
public data class SkRSXform(
    public val fSCos: Float,
    public val fSSin: Float,
    public val fTx: Float,
    public val fTy: Float,
) {

    public companion object {
        /** Identity transform — no scale, no rotation, no translation. */
        public val Identity: SkRSXform = SkRSXform(1f, 0f, 0f, 0f)

        /** Mirrors `SkRSXform::Make(scos, ssin, tx, ty)`. */
        public fun Make(scos: Float, ssin: Float, tx: Float, ty: Float): SkRSXform =
            SkRSXform(scos, ssin, tx, ty)

        /**
         * Mirrors `SkRSXform::MakeFromRadians(scale, radians, tx, ty,
         * anchorX, anchorY)`. Builds an `RSXform` for a uniform-scale
         * + rotation pivoting around `(anchorX, anchorY)` in source
         * space, then translated to land that pivot at `(tx, ty)` in
         * destination space.
         */
        public fun MakeFromRadians(
            scale: Float, radians: Float,
            tx: Float, ty: Float,
            anchorX: Float, anchorY: Float,
        ): SkRSXform {
            val s = scale * sin(radians)
            val c = scale * cos(radians)
            return SkRSXform(c, s, tx + -c * anchorX + s * anchorY, ty + -s * anchorX - c * anchorY)
        }
    }
}
