package org.skia.foundation

/**
 * A custom blend function for the Skia raster pipeline. Mirrors
 * Skia's
 * [`SkBlender`](https://github.com/google/skia/blob/main/include/core/SkBlender.h) :
 * a `(src, dst) → result` function that combines a source colour
 * (the result of the paint pipeline) with a destination colour
 * (read from the canvas) into the final pixel.
 *
 * **Use cases** :
 *  - Wrap a stock [SkBlendMode] as a first-class object so it can
 *    sit in the paint slot beside a future custom-SkSL blender —
 *    the project's
 *    [WGSL-templates strategy](MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl)
 *    treats every shader / colour filter / blender as a hand-ported
 *    type, and `SkBlender` is the dispatch hook.
 *  - Carry an [`Arithmetic`](SkBlenders) blender (`k1·src·dst +
 *    k2·src + k3·dst + k4`) — the first stable custom blender Skia
 *    ships out of the box, used by `arithmode.cpp` upstream.
 *  - Carry an effect-specific custom blender produced by
 *    [SkRuntimeEffect.MakeForBlender] (D2.1+) without further
 *    plumbing in [SkPaint] / [org.skia.core.SkBitmapDevice].
 *
 * **Pixel contract** : both [src] and [dst] are unpremultiplied
 * [SkColor4f] in the device colour space. Implementations that
 * need premul / linear-light intermediates premultiply
 * internally and return an unpremul result. The raster pipeline
 * reads `dst` from the device bitmap, dispatches to the
 * blender's [blend], then writes the returned colour back. F16
 * devices (`kRGBA_F16Norm`) keep float precision through the
 * round-trip ; 8-bit devices (`kRGBA_8888` / `kARGB_4444`)
 * quantise on write — accept a small precision loss vs the
 * legacy 8-bit `SkBlendMode` path, which the existing fast paths
 * still serve when the blender is a [SkBlendModeBlender] (i.e.
 * `Mode(...)` factory output).
 *
 * **Subclassing** :
 *  - Most callers should use [SkBlender.Mode] (legacy blend mode
 *    wrapper) or one of the factories on [SkBlenders].
 *  - Custom subclasses implement [blend] with the (src, dst) →
 *    result function. They are auto-detected by
 *    [`SkBitmapDevice`](../core/SkBitmapDevice.kt) which routes
 *    through [blend] instead of the legacy [SkBlendMode] fast
 *    paths.
 *
 * **Equality** : two blenders are equal iff they describe the
 * same operation. `Mode(kSrcOver) == Mode(kSrcOver)` ;
 * `Arithmetic(0,0,0,1, true) == Arithmetic(0,0,0,1, true)` ; a
 * fresh anonymous subclass is never equal to anything but itself.
 */
public abstract class SkBlender protected constructor() {
    /**
     * Compute the blended colour at one pixel. Both [src] and
     * [dst] are **unpremultiplied** floats in `[0, 1]` (extended
     * range allowed for F16 callers). The returned colour is also
     * unpremul ; the raster pipeline takes care of any
     * device-format conversion on write.
     */
    public abstract fun blend(src: SkColor4f, dst: SkColor4f): SkColor4f

    public companion object {
        /**
         * Wrap an [SkBlendMode] as a [SkBlender] for first-class
         * use in the paint slot. Mirrors Skia's
         * `SkBlender::Mode(SkBlendMode)`.
         *
         * The returned blender is a [SkBlendModeBlender] —
         * recognised by [`SkBitmapDevice`](../core/SkBitmapDevice.kt)
         * which uses the legacy 8-bit blend mode fast paths
         * (preserves the per-mode pixel-bit-iso baseline) instead
         * of routing through [SkColor4f] conversion.
         */
        public fun Mode(mode: SkBlendMode): SkBlender = SkBlendModeBlender(mode)
    }
}

/**
 * Concrete [SkBlender] wrapping a stock [SkBlendMode]. Its only
 * role is to carry a [SkBlendMode] tag through the
 * `paint.blender` slot ; [`SkBitmapDevice`](../core/SkBitmapDevice.kt)
 * detects this subtype and uses the legacy blend-mode fast paths
 * (8-bit byte arithmetic, F16 SrcOver shortcut, etc.) without
 * paying the [SkColor4f] round-trip.
 *
 * The [blend] override is a fall-back for callers that route
 * through the abstract [SkBlender] interface directly (e.g.
 * unit tests, a future image-filter sub-pipeline). It bridges
 * via [SkColor4f.toSkColor] / [SkColor4f.FromColor] and the
 * package-private blend table — accept ~1 ulp of quantisation
 * vs the legacy fast path.
 */
public class SkBlendModeBlender internal constructor(
    public val mode: SkBlendMode,
) : SkBlender() {
    /**
     * Closed-form blend for the four trivial modes used by the
     * D2.0 plumbing tests (Clear, Src, Dst, SrcOver). The full
     * blend matrix lives inside
     * [`SkBitmapDevice`](../core/SkBitmapDevice.kt) and is
     * dispatched on the [SkBlendMode] tag rather than this method
     * — the device detects the [SkBlendModeBlender] subtype and
     * routes through the legacy fast paths to preserve
     * pixel-bit-iso parity with pre-D2 outputs.
     *
     * Other modes throw [UnsupportedOperationException] : direct
     * use of [blend] for non-trivial modes would require copying
     * ~600 LOC of blend formulas out of `SkBitmapDevice`, a
     * refactor deliberately out of scope for D2.0. Subsequent
     * slices may relocate the formulas to a foundation helper if
     * a use case demands it.
     */
    override fun blend(src: SkColor4f, dst: SkColor4f): SkColor4f =
        when (mode) {
            SkBlendMode.kClear -> SkColor4f(0f, 0f, 0f, 0f)
            SkBlendMode.kSrc -> src.copy()
            SkBlendMode.kDst -> dst.copy()
            SkBlendMode.kSrcOver -> {
                // Standard SrcOver in unpremul space :
                //   outA = sa + da · (1 - sa)
                //   outRGB = (sRGB · sa + dRGB · da · (1 - sa)) / outA
                val sa = src.fA.coerceIn(0f, 1f)
                val da = dst.fA.coerceIn(0f, 1f)
                val invSa = 1f - sa
                val outA = sa + da * invSa
                if (outA <= 0f) {
                    SkColor4f(0f, 0f, 0f, 0f)
                } else {
                    val outR = (src.fR * sa + dst.fR * da * invSa) / outA
                    val outG = (src.fG * sa + dst.fG * da * invSa) / outA
                    val outB = (src.fB * sa + dst.fB * da * invSa) / outA
                    SkColor4f(outR, outG, outB, outA)
                }
            }
            else -> throw UnsupportedOperationException(
                "SkBlendModeBlender.blend(SkColor4f, SkColor4f) only " +
                    "implements the trivial modes (Clear / Src / Dst / SrcOver) " +
                    "for direct use ; route through paint.blender on a " +
                    "SkBitmapDevice for the full blend-mode matrix. " +
                    "Got: $mode",
            )
        }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SkBlendModeBlender && mode == other.mode)

    override fun hashCode(): Int = mode.hashCode()

    override fun toString(): String = "SkBlendModeBlender($mode)"
}
