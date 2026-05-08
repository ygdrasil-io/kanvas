package org.skia.foundation

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkEmbossMaskFilter`](https://github.com/google/skia/blob/main/src/effects/SkEmbossMaskFilter.h)
 * — a 3D-emboss coverage filter that lights the source mask with a
 * directional light and outputs three planes :
 *
 *  - **alpha** : the original coverage, unchanged. Carries the
 *    path's edge AA verbatim.
 *  - **multiply** : per-pixel `(0..255)` factor applied to the
 *    paint's RGB. Combines the [Light]'s ambient term with the
 *    Lambertian (`L · N`) diffuse term computed from the alpha
 *    gradient, so flat regions glow at ambient and slopes facing
 *    the light brighten toward 255.
 *  - **additive** : per-pixel `(0..255)` term added after the
 *    multiply for the specular highlight. Computed from a
 *    raised-cosine peak around the perfect-mirror reflection
 *    direction.
 *
 * The composite executed by the device is :
 * ```
 *   dst.rgb = clamp((paint.rgb × multiply) / 255 + additive, 0, 255)
 *   dst.a   = (paint.a × alpha) / 255
 * ```
 *
 * **Algorithm** (port of `src/effects/SkEmbossMask.cpp`) :
 * 1. Inner-blur the source coverage via [SkBlurMaskFilter] with
 *    [SkBlurStyle.kInner] — the blur produces the height-field
 *    gradient that "shapes" the emboss surface, while the original
 *    coverage stays sharp at the edges.
 * 2. For each pixel `(x, y)`, compute the gradient
 *    `(nx, ny) = (height(x+1, y) − height(x−1, y),
 *                 height(x, y+1) − height(x, y−1))` and the
 *    surface normal at that pixel as `(nx, ny, kDelta)`.
 * 3. Project the light direction onto the normal :
 *    `numer = lx·nx + ly·ny + lz·kDelta`,
 *    `denom = sqrt(nx² + ny² + kDelta²)`,
 *    `dot = numer / denom` (the cosine of the angle between light
 *    and normal, in `[0, 1]` if the surface faces the light).
 * 4. Diffuse : `multiply = clamp(ambient + dot, 0, 255)`.
 * 5. Specular : `hilite = (2·dot − lz)·lz` (the Phong-style
 *    raised-cosine peak), then `additive = hilite^(specular ÷ 16)`
 *    via repeated `div255` multiplication, matching upstream's
 *    fixed-point falloff curve.
 * 6. The original (unblurred) [src] alpha is carried through
 *    unchanged so the path's edge AA is preserved exactly.
 *
 * `kDelta = 32` is upstream's empirically chosen "vertical scale"
 * for the height field — small enough to give visibly different
 * shading per gradient direction, large enough to keep the dot
 * product well-conditioned even on near-flat regions.
 *
 * **API parity with upstream** : `Make(blurSigma, light)` mirrors
 * `SkEmbossMaskFilter::Make` ; the [Light] data class mirrors the
 * upstream `Light` struct (3-component direction + 8-bit ambient +
 * 4.4 fixed-point specular exponent stored as `0..255`).
 */
public class SkEmbossMaskFilter private constructor(
    private val blurSigma: Float,
    private val direction: FloatArray, // normalised 3-vector
    private val ambient: Int,          // [0, 255]
    private val specular: Int,         // 4.4 fixed-point in [0, 255]
) : SkMaskFilter() {

    override val format: Format get() = Format.k3D

    private val innerBlur: SkMaskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kInner, blurSigma)
        ?: error("blurSigma > 0 was validated by Make ; SkBlurMaskFilter should never return null here")

    override fun margin(): Int = ceil(3.0 * blurSigma).toInt().coerceAtLeast(1)

    /**
     * Filters that override [format] to [Format.k3D] are routed
     * through [filterMask3D] by [org.skia.core.SkBitmapDevice]. The
     * single-plane variant returns the original coverage so legacy
     * call sites that haven't been updated to dispatch on [format]
     * still see a sensible alpha mask.
     */
    override fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray = src.copyOf()

    override fun filterMask3D(src: ByteArray, w: Int, h: Int): Sk3DMask {
        require(src.size == w * h) { "src.size (${src.size}) != $w × $h" }
        // 1. Inner-blur the coverage to build the height field used
        //    by the gradient calculation. We deliberately do **not**
        //    use the result as the output alpha — the mask's edge
        //    coverage stays the original [src] so the AA quality
        //    of the ported path is preserved.
        val height = innerBlur.filterMask(src, w, h)
        // 2-5. Per-pixel emboss : compute multiply + additive from
        //      the height field's gradient and the light direction.
        val multiply = ByteArray(w * h)
        val additive = ByteArray(w * h)
        emboss(height, multiply, additive, w, h)
        // 6. Carry the original coverage through unchanged.
        return Sk3DMask(alpha = src.copyOf(), multiply = multiply, additive = additive)
    }

    private fun emboss(height: ByteArray, mul: ByteArray, add: ByteArray, w: Int, h: Int) {
        val lx = direction[0]
        val ly = direction[1]
        val lz = direction[2]
        val lzScaled = lz * 256f // mirrors upstream's `lz >> 8` of fixed-point
        val kDelta = 32f
        val kDeltaSq = kDelta * kDelta
        val specShift = specular ushr 4 // upstream's `specular >> 4`
        val maxX = w - 1
        val maxY = h - 1

        for (y in 0..maxY) {
            val rowOffset = y * w
            val prevRowOffset = if (y > 0) (y - 1) * w else rowOffset
            val nextRowOffset = if (y < maxY) (y + 1) * w else rowOffset
            for (x in 0..maxX) {
                // Horizontal alpha gradient (right - left).
                val xNext = if (x < maxX) x + 1 else x
                val xPrev = if (x > 0) x - 1 else x
                val nx = ((height[rowOffset + xNext].toInt() and 0xFF) -
                    (height[rowOffset + xPrev].toInt() and 0xFF)).toFloat()
                // Vertical alpha gradient (down - up).
                val ny = ((height[nextRowOffset + x].toInt() and 0xFF) -
                    (height[prevRowOffset + x].toInt() and 0xFF)).toFloat()

                val numer = lx * nx + ly * ny + lz * kDelta
                var m = ambient
                var a = 0
                if (numer > 0f) {
                    val denom = sqrt(nx * nx + ny * ny + kDeltaSq)
                    // Upstream's `dot >>= 8` after computing `numer/denom`
                    // in fixed-point ; in float the equivalent is `dot * 256`
                    // so the sum with `ambient` (already in `0..255`) lands
                    // in the same range.
                    val dot = ((numer / denom) * 256f).toInt().coerceAtMost(255)
                    m = (m + dot).coerceAtMost(255)

                    // Specular raised-cosine peak around the perfect-
                    // mirror reflection direction. Upstream's formula
                    // `(2·dot − lzScaled) · lzScaled >> 8` carries the
                    // 256-scale factor through, so we repeat it here.
                    val hiliteRaw = ((2 * dot - lzScaled) * lzScaled / 256f).toInt()
                    if (hiliteRaw > 0) {
                        val hilite = hiliteRaw.coerceAtMost(255)
                        // `additive = hilite^(specular÷16)` via the
                        // div255-based exponentiation upstream uses.
                        // Each iteration : `add = (add * hilite + 127) / 255`.
                        var addLocal = hilite
                        var i = specShift
                        while (i > 0) {
                            addLocal = (addLocal * hilite + 127) / 255
                            i--
                        }
                        a = addLocal
                    }
                }
                mul[rowOffset + x] = m.toByte()
                add[rowOffset + x] = a.toByte()
            }
        }
    }

    /**
     * Mirrors upstream's `SkEmbossMaskFilter::Light` struct (modulo
     * the `fPad` byte, which exists only for serialisation
     * alignment in upstream and isn't surfaced here).
     */
    public data class Light(
        /**
         * Direction vector toward the light source (not yet
         * normalised — [SkEmbossMaskFilter.Make] normalises before
         * storing). Length-3 array `(x, y, z)`. Positive `z`
         * points "out of the screen" toward the viewer.
         */
        val direction: FloatArray,
        /** Ambient term in `[0, 255]`. Floors the multiply plane. */
        val ambient: Int,
        /**
         * Specular exponent in 4.4 fixed-point — value `0..255`
         * where `0..15` ≈ matte, `16..63` ≈ shiny, `64..255` ≈
         * mirror-like. Upstream stores this as a single byte ;
         * the falloff is computed via repeated `div255`
         * multiplication of the raised-cosine peak.
         */
        val specular: Int,
    ) {
        init {
            require(direction.size == 3) {
                "Light.direction must be a 3-vector ; got size ${direction.size}"
            }
        }

        // ByteArray-style equality contract (data class default uses
        // reference equality on FloatArray).
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Light) return false
            return direction.contentEquals(other.direction) &&
                ambient == other.ambient &&
                specular == other.specular
        }

        override fun hashCode(): Int {
            var r = direction.contentHashCode()
            r = 31 * r + ambient
            r = 31 * r + specular
            return r
        }
    }

    public companion object {
        /**
         * Mirrors `SkEmbossMaskFilter::Make(blurSigma, light)`.
         *
         *  - `blurSigma <= 0` or non-finite returns `null` (no-op
         *    filter ; matches upstream's `SkIsFinite` guard).
         *  - A degenerate light direction (zero-length vector)
         *    returns `null`.
         *  - Otherwise the light direction is normalised and a
         *    fresh [SkEmbossMaskFilter] is returned with its
         *    `ambient` / `specular` clamped to `[0, 255]`.
         */
        public fun Make(blurSigma: Float, light: Light): SkMaskFilter? {
            if (!blurSigma.isFinite() || blurSigma <= 0f) return null
            val d = light.direction
            val lenSq = d[0] * d[0] + d[1] * d[1] + d[2] * d[2]
            if (lenSq <= 0f || !lenSq.isFinite()) return null
            val len = sqrt(lenSq)
            val normalised = floatArrayOf(d[0] / len, d[1] / len, d[2] / len)
            return SkEmbossMaskFilter(
                blurSigma = blurSigma,
                direction = normalised,
                ambient = light.ambient.coerceIn(0, 255),
                specular = light.specular.coerceIn(0, 255),
            )
        }
    }
}
