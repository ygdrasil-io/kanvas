package org.skia.effects.runtime

import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import org.graphiks.math.SkMatrix

/**
 * Faithful port of Skia's
 * [`SkRuntimeEffect`](https://github.com/google/skia/blob/main/include/effects/SkRuntimeEffect.h)
 * — the compile-time façade that turns an SkSL source string into a
 * factory for [SkShader] / [SkColorFilter] / [SkBlender] instances.
 *
 * **Architecture** : `kanvas-skia` does not parse or interpret SkSL.
 * Each upstream SkSL program has a hand-ported [SkRuntimeImpl]
 * registered in [SkRuntimeEffectDispatch] under the canonical SkSL
 * hash. The [MakeForShader] / [MakeForColorFilter] / [MakeForBlender]
 * factories look up the matching impl ; on miss they return a
 * `Result(effect = null, errorText = "SkSL not registered: <hash>.
 * Add an entry to SkRuntimeEffectDispatch.")` so the failure path is
 * visible (the DM driver logs the missing hash and skips the GM
 * gracefully). See
 * [`MIGRATION_PLAN_D2_RUNTIME_EFFECT.md`](../../../../../../../../MIGRATION_PLAN_D2_RUNTIME_EFFECT.md)
 * for the full rationale ; the runtime-effect chantier follows the
 * same hand-port-per-shader-type strategy as the GPU plan
 * [§ G4](../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl).
 *
 * **Reflection** : at construction time, the SkSL signature is parsed
 * (top-level `uniform` / `child` declarations + the `main(...)` entry
 * arity). The parsed data feeds [uniforms] / [children] /
 * [findUniform] / [findChild] / [uniformSize] / [allowShader] /
 * [allowColorFilter] / [allowBlender] queries. **The function body
 * is not parsed** ; runtime evaluation happens via the registered
 * [SkRuntimeImpl].
 *
 * **Construction** : private — callers go through [MakeForShader] /
 * [MakeForColorFilter] / [MakeForBlender]. Each factory parses the
 * source, looks up the impl, validates the entry-point arity matches
 * the requested kind, and returns a [Result].
 */
public class SkRuntimeEffect private constructor(
    /** Canonical SkSL source as supplied by the caller. */
    private val sourceText: String,
    private val parsed: ParsedSignature,
    /** Kind that the matching impl was registered for. */
    private val kind: Kind,
    /** Lazily-instantiated impl ; resolved once on first use. */
    private val implFactory: () -> SkRuntimeImpl,
) {

    // ─── Reflection structs (mirror upstream verbatim) ────────────────

    /**
     * Reflection record for a top-level SkSL `uniform <type> <name>;`
     * declaration. Matches Skia's
     * [`SkRuntimeEffect::Uniform`](https://github.com/google/skia/blob/main/include/effects/SkRuntimeEffect.h)
     * field-for-field.
     *
     * @param name SkSL variable name.
     * @param offset byte offset into the uniform [SkData] block. Each
     *   uniform aligns to its own size up to 16 bytes (matches
     *   upstream's std140-ish layout).
     * @param type [Type] of the uniform — constrains the byte width.
     * @param count `1` for scalars / vectors / matrices, `>= 1` for
     *   array-typed uniforms (`uniform float foo[4];`).
     * @param flags bit-mask of [Flags] (currently [kArray_Flag],
     *   [kColor_Flag], [kHalfPrecision_Flag]).
     */
    public class Uniform(
        public val name: String,
        public val offset: Int,
        public val type: Type,
        public val count: Int,
        public val flags: Int,
    ) {
        public enum class Type {
            kFloat, kFloat2, kFloat3, kFloat4,
            kFloat2x2, kFloat3x3, kFloat4x4,
            kInt, kInt2, kInt3, kInt4,
            ;

            /** Byte width of one element of this type. Mirrors `SkRuntimeEffect::Uniform::sizeInBytes` for `count == 1`. */
            public val sizeBytes: Int
                get() = when (this) {
                    kFloat -> 4
                    kFloat2 -> 8
                    kFloat3 -> 12
                    kFloat4 -> 16
                    kFloat2x2 -> 16
                    kFloat3x3 -> 36
                    kFloat4x4 -> 64
                    kInt -> 4
                    kInt2 -> 8
                    kInt3 -> 12
                    kInt4 -> 16
                }
        }

        public fun isArray(): Boolean = (flags and kArray_Flag) != 0
        public fun isColor(): Boolean = (flags and kColor_Flag) != 0
        public fun isHalfPrecision(): Boolean = (flags and kHalfPrecision_Flag) != 0

        /** Total size in bytes of this uniform (type × count). */
        public fun sizeInBytes(): Int = type.sizeBytes * count

        public companion object {
            public const val kArray_Flag: Int = 0x1
            public const val kColor_Flag: Int = 0x2
            public const val kHalfPrecision_Flag: Int = 0x10
        }
    }

    /**
     * Reflection record for a top-level SkSL `uniform shader` /
     * `uniform colorFilter` / `uniform blender` declaration. Matches
     * Skia's
     * [`SkRuntimeEffect::Child`](https://github.com/google/skia/blob/main/include/effects/SkRuntimeEffect.h).
     */
    public class Child(
        public val name: String,
        public val type: ChildType,
        public val index: Int,
    )

    /** Mirrors upstream's `SkRuntimeEffect::ChildType`. */
    public enum class ChildType { kShader, kColorFilter, kBlender }

    /** Discriminator for the entry-point arity. */
    public enum class Kind { kShader, kColorFilter, kBlender }

    /**
     * Result wrapper for the [MakeForShader] / [MakeForColorFilter] /
     * [MakeForBlender] factories. On success [effect] is non-null and
     * [errorText] is empty ; on failure [effect] is null and
     * [errorText] carries the diagnostic.
     *
     * Mirrors upstream's `SkRuntimeEffect::Result`.
     */
    public class Result(
        public val effect: SkRuntimeEffect?,
        public val errorText: String,
    )

    // ─── Public reflection accessors ──────────────────────────────────

    public fun source(): String = sourceText

    public fun uniforms(): List<Uniform> = parsed.uniforms

    public fun children(): List<Child> = parsed.children

    /** Total bytes the caller must pass via [SkData] to [makeShader]
     *  / [makeColorFilter] / [makeBlender]. Sum of every uniform's
     *  [Uniform.sizeInBytes]. */
    public val uniformSize: Int get() = parsed.uniformSize

    public fun findUniform(name: String): Uniform? = parsed.uniforms.firstOrNull { it.name == name }
    public fun findChild(name: String): Child? = parsed.children.firstOrNull { it.name == name }

    public fun allowShader(): Boolean = kind == Kind.kShader
    public fun allowColorFilter(): Boolean = kind == Kind.kColorFilter
    public fun allowBlender(): Boolean = kind == Kind.kBlender

    public fun descriptor(): SkRuntimeEffectDescriptor? =
        SkRuntimeEffectDescriptorRegistry.lookup(sourceText)

    /**
     * Lazily resolves the registered [SkRuntimeImpl]. The factory was
     * captured at construction time — here we instantiate on first
     * call. Subsequent calls return the same instance (effects are
     * intended to be reused across draws).
     */
    internal val impl: SkRuntimeImpl by lazy { implFactory() }

    // ─── Builder paths (D2.2) ────────────────────────────────────────

    /**
     * Build an [SkShader] from this effect. Mirrors Skia's
     * `SkRuntimeEffect::makeShader(uniforms, children, localMatrix)`.
     *
     * Returns `null` if [allowShader] is `false` (this effect is a
     * color filter or blender) or if the supplied [children] count
     * doesn't match the number of declared child slots. Otherwise
     * returns a fresh [SkRuntimeShader] wrapping the registered
     * impl with a snapshot of [uniforms] and the resolver array.
     *
     * @param uniforms uniform values laid out per [parsed.uniforms]
     *   — exactly [uniformSize] bytes. Pass `null` or
     *   [SkData.EMPTY] for an effect with no uniforms.
     * @param children one [SkShader] per declared child slot, in
     *   declaration order. `null` slots are wired to a transparent-
     *   black resolver (matches upstream's missing-child fallback).
     * @param localMatrix optional shader-local matrix ; defaults to
     *   identity.
     */
    public fun makeShader(
        uniforms: SkData?,
        children: Array<SkShader?> = emptyArray(),
        localMatrix: SkMatrix? = null,
    ): SkShader? {
        if (!allowShader()) return null
        if (parsed.children.size != children.size) return null
        // Pre-set up children for sampling. We don't have the
        // canvas CTM here, so use identity ; SkBitmapDevice will
        // call [SkShader.setupForDraw] on the parent
        // SkRuntimeShader, which in turn re-setups the children
        // through [SkRuntimeShader.setupForDraw].
        val resolvers = SkRuntimeShader.buildShaderChildResolvers(
            declared = parsed.children,
            children = children,
            canvasCtm = SkMatrix.Identity,
            xform = identityXform,
        )
        return SkRuntimeShader(
            impl = impl,
            runtimeEffectDescriptor = descriptor(),
            uniformsBuffer = SkRuntimeShader.makeUniformsBuffer(uniforms),
            childResolvers = resolvers,
            localMatrix = localMatrix ?: SkMatrix.Identity,
        )
    }

    /**
     * Build an [SkColorFilter] from this effect. Mirrors Skia's
     * `SkRuntimeEffect::makeColorFilter(uniforms, children)`.
     *
     * Returns `null` if [allowColorFilter] is `false` or if the
     * supplied [children] count doesn't match.
     */
    public fun makeColorFilter(
        uniforms: SkData?,
        children: Array<SkColorFilter?> = emptyArray(),
    ): SkColorFilter? {
        if (!allowColorFilter()) return null
        if (parsed.children.size != children.size) return null
        val resolvers = Array<ChildResolver>(parsed.children.size) { i ->
            val decl = parsed.children[i]
            require(decl.type == SkRuntimeEffect.ChildType.kColorFilter) {
                "SkRuntimeColorFilter only accepts color-filter children (slot ${decl.name} declared as ${decl.type})"
            }
            val cf = children[i]
            if (cf == null) ChildResolver.ColorFilter { input -> input }
            else ChildResolver.ColorFilter { input -> cf.filterColor4f(input) }
        }
        return SkRuntimeColorFilter(
            impl = impl,
            runtimeEffectDescriptor = descriptor(),
            uniformsBuffer = SkRuntimeShader.makeUniformsBuffer(uniforms),
            childResolvers = resolvers,
        )
    }

    /**
     * Build an [SkBlender] from this effect. Mirrors Skia's
     * `SkRuntimeEffect::makeBlender(uniforms, children)`.
     *
     * Returns `null` if [allowBlender] is `false` or if the
     * supplied [children] count doesn't match.
     */
    public fun makeBlender(
        uniforms: SkData?,
        children: Array<SkBlender?> = emptyArray(),
    ): SkBlender? {
        if (!allowBlender()) return null
        if (parsed.children.size != children.size) return null
        val resolvers = Array<ChildResolver>(parsed.children.size) { i ->
            val decl = parsed.children[i]
            require(decl.type == SkRuntimeEffect.ChildType.kBlender) {
                "SkRuntimeBlender only accepts blender children (slot ${decl.name} declared as ${decl.type})"
            }
            val b = children[i]
            if (b == null) ChildResolver.Blender { _, dst -> dst }
            else ChildResolver.Blender { src, dst -> b.blend(src, dst) }
        }
        return SkRuntimeBlender(
            impl = impl,
            uniformsBuffer = SkRuntimeShader.makeUniformsBuffer(uniforms),
            childResolvers = resolvers,
        )
    }

    // ─── Factories (Companion) ────────────────────────────────────────

    public companion object {

        // ─── Effect flags (mirror upstream's SkRuntimeEffect::Flags) ─

        /** This effect uses `sk_FragCoord` or otherwise samples its
         *  coordinate input. Matches upstream `kUsesSampleCoords_Flag`. */
        public const val kUsesSampleCoords_Flag: Int = 0x001
        /** Effect is allowed in a color-filter context. */
        public const val kAllowColorFilter_Flag: Int = 0x002
        /** Effect is allowed in a shader context. */
        public const val kAllowShader_Flag: Int = 0x004
        /** Effect is allowed in a blender context. */
        public const val kAllowBlender_Flag: Int = 0x008
        /** Effect samples a child shader from outside `main(...)`. */
        public const val kSamplesOutsideMain_Flag: Int = 0x010
        /** Effect's output uses a colour-space transform. */
        public const val kUsesColorTransform_Flag: Int = 0x020
        /** Effect always produces a fully opaque output (alpha = 1). */
        public const val kAlwaysOpaque_Flag: Int = 0x040
        /** Effect leaves the input alpha unchanged — bindings can
         *  short-circuit some compositing optimisations. Mirrors
         *  upstream's `kAlphaUnchanged_Flag`. */
        public const val kAlphaUnchanged_Flag: Int = 0x080
        /** Effect must skip optimisation passes (debug-only). */
        public const val kDisableOptimization_Flag: Int = 0x100

        /**
         * Identity colour-space xform — every shader child setup
         * needs one, but the runtime-effect path doesn't pre-
         * transform colour stops, so the xform is a no-op. Cached
         * here to avoid re-allocating per `makeShader` call.
         */
        internal val identityXform: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
            src = SkColorSpace.makeSRGB(),
            srcAT = SkAlphaType.kUnpremul,
            dst = SkColorSpace.makeSRGB(),
            dstAT = SkAlphaType.kUnpremul,
        )

        /**
         * Compile [sksl] as a shader effect. Mirrors Skia's
         * `SkRuntimeEffect::MakeForShader(sksl)`.
         *
         * The SkSL must declare an entry point of the form
         * `vec4 main(vec2 coords)` (also accepts `half4 main(float2)`,
         * `float4 main(vec2)` — upstream's flexible aliases). The
         * impl is looked up by canonical SkSL hash in
         * [SkRuntimeEffectDispatch].
         */
        public fun MakeForShader(sksl: String): Result =
            makeFor(sksl, Kind.kShader)

        /**
         * Compile [sksl] as a color-filter effect. Mirrors
         * `SkRuntimeEffect::MakeForColorFilter(sksl)`.
         *
         * Entry point : `vec4 main(vec4 inColor)`.
         */
        public fun MakeForColorFilter(sksl: String): Result =
            makeFor(sksl, Kind.kColorFilter)

        /**
         * Compile [sksl] as a blender effect. Mirrors
         * `SkRuntimeEffect::MakeForBlender(sksl)`.
         *
         * Entry point : `vec4 main(vec4 srcColor, vec4 dstColor)`.
         */
        public fun MakeForBlender(sksl: String): Result =
            makeFor(sksl, Kind.kBlender)

        /**
         * Shared body for the three factories. Parses the SkSL
         * signature once, validates the entry-point arity matches
         * the requested [kind], and looks up the impl in
         * [SkRuntimeEffectDispatch].
         */
        private fun makeFor(sksl: String, kind: Kind): Result {
            // Ensure the registry is populated with every hand-ported
            // built-in effect before we look up. Idempotent — the
            // builtin objects' `init {}` blocks register on first
            // touch ; subsequent touches are no-ops.
            ensureBuiltinsLoaded()

            val parseResult = SkRuntimeEffectSignatureParser.parse(sksl)
            if (parseResult is SkRuntimeEffectSignatureParser.Outcome.Error) {
                return Result(null, parseResult.message)
            }
            val parsed = (parseResult as SkRuntimeEffectSignatureParser.Outcome.Ok).parsed
            // Validate entry-point arity matches the requested kind.
            if (parsed.kind != kind) {
                return Result(
                    null,
                    "SkSL entry point declares ${parsed.kind} but ${kindName(kind)} was requested",
                )
            }
            // Look up the impl by canonical hash.
            val factory = SkRuntimeEffectDispatch.lookup(sksl)
                ?: return Result(
                    null,
                    "SkSL not registered: ${
                        "0x" + SkRuntimeEffectDispatch.canonicalHash(sksl).toULong()
                            .toString(16).padStart(16, '0').uppercase()
                    }. Add an entry to SkRuntimeEffectDispatch.",
                )
            return Result(SkRuntimeEffect(sksl, parsed, kind, factory), "")
        }

        private fun kindName(k: Kind): String = when (k) {
            Kind.kShader -> "MakeForShader"
            Kind.kColorFilter -> "MakeForColorFilter"
            Kind.kBlender -> "MakeForBlender"
        }

        /**
         * Re-populates the dispatch table with every hand-ported
         * built-in effect. Called once per [makeFor] (cheap —
         * each builtin registration is a map lookup or insert).
         *
         * Idempotent : if a test calls
         * [SkRuntimeEffectDispatch.clearForTest] between cases,
         * the next [makeFor] re-registers everything. If the
         * registry is already populated, builtin registration skips
         * the existing entries.
         */
        private fun ensureBuiltinsLoaded() {
            org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsColorCube.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsExponential.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsCommon.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsGeometric.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsMatrix.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsRelational.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects.registerAll()
            org.skia.effects.runtime.effects.SkBuiltinShaderEffectsRtifImageFilters.registerAll()
        }
    }
}

// ─── Internal parser data classes ─────────────────────────────────────

/**
 * Output of [SkRuntimeEffectSignatureParser.parse]. Carries every
 * top-level uniform / child declaration plus the entry-point kind
 * detected from the `main(...)` signature.
 */
internal class ParsedSignature(
    val uniforms: List<SkRuntimeEffect.Uniform>,
    val children: List<SkRuntimeEffect.Child>,
    val kind: SkRuntimeEffect.Kind,
    val uniformSize: Int,
)
