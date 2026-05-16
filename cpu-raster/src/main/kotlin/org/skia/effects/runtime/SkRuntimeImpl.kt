package org.skia.effects.runtime

import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
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
 *    dstColor, uniforms, children)`. No side effects ; no state
 *    across calls. Each runtime-effect instance carries a snapshot
 *    of its uniforms / children, but per-pixel calls are independent.
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
 * Implementations check the kind via the [flags] / declared signature
 * and branch accordingly ; passing the wrong tuple is a programming
 * error (the bindings layer enforces the contract at the call site).
 */
public interface SkRuntimeImpl {
    /** Reflection — every [SkRuntimeEffect.Uniform] declared by the
     *  SkSL signature, in declaration order. */
    public val uniforms: List<SkRuntimeEffect.Uniform>

    /** Reflection — every [SkRuntimeEffect.Child] declared by the
     *  SkSL signature. Indices match the order of `child.eval(...)`
     *  resolvers in [shade]. */
    public val children: List<SkRuntimeEffect.Child>

    /**
     * `or`-bitmask of [SkRuntimeEffect.Flags]. Tells the rasterizer
     * what optimisations are safe (e.g. an alpha-unchanged flag
     * lets the AA-clip coverage path skip the zero-alpha-src
     * early-out).
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
     * @param uniforms read-only byte buffer carrying the uniforms
     *   in the layout described by [uniforms].
     * @param children one [ChildResolver] per declared
     *   [SkRuntimeEffect.Child], in declaration order. The
     *   resolver's variant ([ChildResolver.Shader] /
     *   [ChildResolver.ColorFilter] / [ChildResolver.Blender])
     *   matches the [SkRuntimeEffect.ChildType] declared in the
     *   SkSL signature ; the binding layer guarantees that
     *   correspondence so impls can pattern-match safely.
     */
    public fun shade(
        coords: SkPoint?,
        srcColor: SkColor4f?,
        dstColor: SkColor4f?,
        uniforms: ByteBuffer,
        children: Array<ChildResolver>,
    ): SkColor4f
}

/**
 * Polymorphic resolver passed to a [SkRuntimeImpl] for each child
 * slot declared by the SkSL signature. Mirrors upstream SkSL's
 * `child.eval(...)` polymorphism : a shader child's `eval` takes a
 * `vec2` coordinate ; a color filter's takes a `vec4` colour ; a
 * blender's takes two `vec4` colours.
 *
 * **Type-safe vs the upstream `SkSL::ChildPtr`** : upstream pattern-
 * matches `ChildPtr` at SkSL-codegen time ; we pattern-match the
 * sealed interface in Kotlin at impl-eval time. Same outcome ; no
 * cast-from-Any in the impl body.
 */
public sealed interface ChildResolver {
    /**
     * Shader child : `child.eval(localCoord)` returns the shader's
     * colour at that local-space coordinate.
     */
    public class Shader(public val sample: (SkPoint) -> SkColor4f) : ChildResolver

    /**
     * Color filter child : `child.eval(inputColor)` applies the
     * filter to the supplied colour.
     */
    public class ColorFilter(public val apply: (SkColor4f) -> SkColor4f) : ChildResolver

    /**
     * Blender child : `child.eval(src, dst)` evaluates the blender.
     */
    public class Blender(public val blend: (SkColor4f, SkColor4f) -> SkColor4f) : ChildResolver
}
