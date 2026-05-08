package org.skia.effects.runtime

import org.skia.foundation.SkColor4f
import org.skia.math.SkPoint
import java.nio.ByteBuffer

/**
 * Internal contract implemented by every hand-ported runtime effect
 * registered with [SkRuntimeEffectDispatch]. Exactly one Kotlin
 * subclass per upstream SkSL program — see
 * [`MIGRATION_PLAN_D2_RUNTIME_EFFECT.md`](../../../../../../../../MIGRATION_PLAN_D2_RUNTIME_EFFECT.md)
 * § D2.4.a-d for the per-effect catalogue.
 *
 * **Conceptual layer** : same as `SkLinearGradient.shadeRow(...)` /
 * `SkBitmapShader.sampleAt(...)` — a per-pixel Kotlin function the
 * raster pipeline drives. The only difference vs the existing
 * shaders is that the dispatch is **by canonical SkSL hash** instead
 * of by Kotlin type, so a ported GM can use upstream's
 * `SkRuntimeEffect.MakeForShader(skslString)` API verbatim.
 *
 * **Subclass contract** : implementations must
 *  - declare the [uniforms] / [children] / [flags] that match the
 *    SkSL signature (parsed once by [SkRuntimeEffect] at registration
 *    time and validated against this list to catch drift) ;
 *  - implement [shade] as a pure function of `(coords, srcColor,
 *    dstColor, uniforms, childResolvers)`. No side effects ; no
 *    state across calls. Each runtime-effect instance carries a
 *    snapshot of its uniforms / children, but per-pixel calls are
 *    independent.
 *
 * The `coords` / `srcColor` / `dstColor` arguments are mutually
 * exclusive depending on the effect kind :
 *
 *  | Kind         | coords       | srcColor      | dstColor      |
 *  |--------------|:------------:|:-------------:|:-------------:|
 *  | shader       | non-null     | null          | null          |
 *  | colorFilter  | null         | non-null      | null          |
 *  | blender      | null         | non-null      | non-null      |
 *
 * Implementations must check the kind via the [flags] field and
 * branch accordingly ; passing the wrong tuple is a programming
 * error (the bindings layer in D2.2 enforces the contract at the
 * call site).
 */
public interface SkRuntimeImpl {
    /**
     * Reflection — every [SkRuntimeEffect.Uniform] declared by the
     * SkSL signature, in declaration order. The list is consumed by
     * [SkRuntimeEffect] for `findUniform(name)` queries and by the
     * D2.3 [SkRuntimeEffectBuilder] for type-checked uniform writes.
     */
    public val uniforms: List<SkRuntimeEffect.Uniform>

    /**
     * Reflection — every [SkRuntimeEffect.Child] declared by the
     * SkSL signature. Indices match the order of `child.eval(...)`
     * resolvers in [shade].
     */
    public val children: List<SkRuntimeEffect.Child>

    /**
     * `or`-bitmask of [SkRuntimeEffect.Flags]. Tells the rasterizer
     * what optimisations are safe (e.g.
     * [SkRuntimeEffect.kAlphaUnchanged_Flag] lets the AA-clip
     * coverage path skip the zero-alpha-src early-out — same
     * optimisation the legacy `dispatchBlend` does for `SkBlendMode`
     * via `modeAffectsZeroAlphaSrc`).
     */
    public val flags: Int

    /**
     * Per-pixel evaluation. Returns the unpremultiplied [SkColor4f]
     * the rasterizer should write at the call site.
     *
     * @param coords device-space sample point for shaders ; `null`
     *   for color filters and blenders.
     * @param srcColor unpremul source colour for color filters and
     *   blenders ; `null` for shaders.
     * @param dstColor unpremul destination colour for blenders ;
     *   `null` for shaders and color filters.
     * @param uniforms read-only byte buffer carrying the uniforms in
     *   the layout described by [uniforms]. Each implementation
     *   reads the values it needs via offset + type.
     * @param childResolvers one resolver per [Child], in declaration
     *   order. Each resolver evaluates the child shader at a query
     *   point and returns the resulting colour. Color-filter children
     *   are passed as `(_) -> filtered_input` (same colour at every
     *   query point) ; blender children as `(_) -> blended_pair`.
     */
    public fun shade(
        coords: SkPoint?,
        srcColor: SkColor4f?,
        dstColor: SkColor4f?,
        uniforms: ByteBuffer,
        childResolvers: Array<(SkPoint) -> SkColor4f>,
    ): SkColor4f
}
