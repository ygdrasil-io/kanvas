package org.skia.effects.runtime

// D2.1 — façade + dispatch only ; the makeShader / makeColorFilter
// / makeBlender bindings land in D2.2 (wires SkRuntimeShader /
// SkRuntimeColorFilter / SkRuntimeBlender) ; SkData and the
// Builder helper land in D2.3.

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

    /**
     * Lazily resolves the registered [SkRuntimeImpl]. The factory was
     * captured at construction time — here we instantiate on first
     * call. Subsequent calls return the same instance (effects are
     * intended to be reused across draws).
     */
    internal val impl: SkRuntimeImpl by lazy { implFactory() }

    // makeShader / makeColorFilter / makeBlender are added in D2.2
    // (bindings) once SkRuntimeShader / SkRuntimeColorFilter /
    // SkRuntimeBlender exist. The SkData uniform-buffer parameter
    // lands in D2.3. D2.1 ships only the façade + reflection +
    // dispatch — see [`MIGRATION_PLAN_D2_RUNTIME_EFFECT.md`].

    // ─── Factories (Companion) ────────────────────────────────────────

    public companion object {

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
