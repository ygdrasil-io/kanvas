package org.skia.effects.runtime

import org.skia.foundation.SkBlender
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import org.skia.math.SkMatrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Convenience builder around a [SkRuntimeEffect] that exposes named
 * accessors for `uniform` variables and child slots, then collates
 * the bound state into a final [SkShader] / [SkColorFilter] /
 * [SkBlender] via [makeShader] / [makeColorFilter] / [makeBlender].
 *
 * Mirrors Skia's
 * [`SkRuntimeEffectBuilder`](https://github.com/google/skia/blob/main/include/effects/SkRuntimeEffect.h)
 * — same usage shape :
 *
 * ```kotlin
 * val effect: SkRuntimeEffect = SkRuntimeEffect.MakeForShader(sksl).effect!!
 * val builder = SkRuntimeEffectBuilder(effect)
 * builder.uniform("scale").set(3.14f)
 * builder.uniform("tint").set(SkColor4f(1f, 0f, 0f, 1f))
 * builder.child("source").set(mySrcShader)
 * val shader = builder.makeShader()
 * ```
 *
 * **Why use the Builder instead of the raw [SkRuntimeEffect.makeShader]
 * factory** :
 *
 *  - **Named writes** — uniform values get placed at the right byte
 *    offset automatically (no manual ByteBuffer arithmetic).
 *  - **Type checking** — [UniformAccessor.set] validates that the
 *    Kotlin value matches the declared [SkRuntimeEffect.Uniform.Type]
 *    and the right slot size (mismatch throws
 *    [IllegalArgumentException]).
 *  - **Child slot validation** — [ChildAccessor] enforces the
 *    declared [SkRuntimeEffect.ChildType] (a `shader` slot won't
 *    accept a [SkColorFilter], etc.).
 *  - **Reusable** — set uniforms once, build many shaders with
 *    different local matrices.
 *
 * **Mutability** : the internal uniform [ByteBuffer] is mutated in
 * place by [UniformAccessor.set]. Snapshots are taken on each
 * [makeShader] / [makeColorFilter] / [makeBlender] call (defensive
 * copy of the bytes) so subsequent edits to the builder don't leak
 * into already-built effects.
 *
 * **Thread-safety** : not thread-safe. Build from one thread ;
 * the resulting [SkShader] / [SkColorFilter] / [SkBlender] is
 * immutable and safe to share.
 */
public class SkRuntimeEffectBuilder(
    /** The compile-time effect this builder targets. */
    public val effect: SkRuntimeEffect,
    /** Optional initial uniform block. Defaults to a zero-initialised
     *  buffer of `effect.uniformSize` bytes. */
    initialUniforms: SkData? = null,
) {
    /**
     * Mutable uniform byte buffer. Sized at `effect.uniformSize` ;
     * accessors write into it at the right offsets.
     */
    private val uniformBytes: ByteArray = run {
        val size = effect.uniformSize
        if (initialUniforms == null) {
            ByteArray(size)
        } else {
            require(initialUniforms.size == size) {
                "initialUniforms size ${initialUniforms.size} doesn't match effect.uniformSize $size"
            }
            initialUniforms.toByteArray()
        }
    }

    /** Per-child-slot bound values, one of [SkShader] / [SkColorFilter] /
     *  [SkBlender] — typed by the slot's declared
     *  [SkRuntimeEffect.ChildType]. `null` slot → resolved as the
     *  per-type identity (transparent shader, identity filter,
     *  dst-only blender) at build time. */
    private val children: Array<Any?> = arrayOfNulls(effect.children().size)

    // ─── Uniform / child accessors ────────────────────────────────────

    /**
     * Get a typed accessor for the uniform named [name].
     *
     * Throws [IllegalArgumentException] if no uniform with that name
     * exists on the [effect]. Failing fast helps catch typos early ;
     * upstream Skia silently no-ops on missing names in release
     * builds, but for our use case (hand-ported GMs with known SkSL
     * sources) early failure is preferable.
     */
    public fun uniform(name: String): UniformAccessor {
        val u = effect.findUniform(name)
            ?: throw IllegalArgumentException(
                "No uniform '$name' on effect ; declared uniforms : " +
                    effect.uniforms().joinToString { it.name }.ifEmpty { "(none)" },
            )
        return UniformAccessor(u, this)
    }

    /**
     * Get a typed accessor for the child slot named [name].
     *
     * Throws [IllegalArgumentException] if no child with that name
     * exists on the [effect].
     */
    public fun child(name: String): ChildAccessor {
        val c = effect.findChild(name)
            ?: throw IllegalArgumentException(
                "No child '$name' on effect ; declared children : " +
                    effect.children().joinToString { "${it.name}:${it.type}" }
                        .ifEmpty { "(none)" },
            )
        return ChildAccessor(c, this)
    }

    // ─── Build paths ──────────────────────────────────────────────────

    /**
     * Build an [SkShader] from the currently-bound uniforms / children.
     * Mirrors `SkRuntimeEffectBuilder::makeShader(localMatrix)`.
     *
     * Returns `null` if the underlying [effect] is not a shader effect
     * (i.e. [SkRuntimeEffect.allowShader] is `false`).
     */
    public fun makeShader(localMatrix: SkMatrix? = null): SkShader? {
        if (!effect.allowShader()) return null
        val children = childArrayOf<SkShader>()
        return effect.makeShader(currentUniforms(), children, localMatrix)
    }

    /**
     * Build an [SkColorFilter] from the currently-bound state.
     * Mirrors `SkRuntimeEffectBuilder::makeColorFilter()`.
     */
    public fun makeColorFilter(): SkColorFilter? {
        if (!effect.allowColorFilter()) return null
        val children = childArrayOf<SkColorFilter>()
        return effect.makeColorFilter(currentUniforms(), children)
    }

    /**
     * Build an [SkBlender] from the currently-bound state.
     * Mirrors `SkRuntimeEffectBuilder::makeBlender()`.
     */
    public fun makeBlender(): SkBlender? {
        if (!effect.allowBlender()) return null
        val children = childArrayOf<SkBlender>()
        return effect.makeBlender(currentUniforms(), children)
    }

    /**
     * Snapshot the current uniform bytes as an immutable [SkData].
     * Each `makeXxx` call snapshots independently so the caller can
     * mutate the builder afterwards without affecting already-built
     * effects.
     */
    private fun currentUniforms(): SkData =
        if (uniformBytes.isEmpty()) SkData.EMPTY else SkData.MakeWithCopy(uniformBytes)

    /**
     * Build a typed children array of the requested kind, slotting
     * `null` for any slot of a different type. Used by
     * [makeShader] / [makeColorFilter] / [makeBlender] which each
     * pass their type-specific array to the corresponding
     * `effect.makeXxx`.
     *
     * **Strictness** : if a slot is bound to a value whose Kotlin
     * type doesn't match the declared [SkRuntimeEffect.ChildType],
     * the inappropriate-type slot returns `null` here. The
     * underlying `effect.makeXxx` then either rejects the wiring
     * (children-count mismatch is rare since we always pass exactly
     * `effect.children.size` slots) or runs successfully if the
     * remaining slots are valid.
     *
     * Practical implication : binding a `SkShader` to a slot
     * declared as `colorFilter` is a no-op at build time (the
     * binding's [makeColorFilter] sees `null` for that slot,
     * resolved to identity). The strict path is enforced by
     * [ChildAccessor.set] which already rejects type-mismatched
     * assignments — so reaching this fallback is only possible if
     * the slot was never written.
     */
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> childArrayOf(): Array<T?> {
        val out = arrayOfNulls<T>(children.size)
        for (i in children.indices) {
            val v = children[i]
            if (v is T) out[i] = v
        }
        return out as Array<T?>
    }

    // ─── Internal — uniform byte writes ───────────────────────────────

    /**
     * Write [bytes] into the internal uniform buffer at byte offset
     * [offset]. Length [length] bytes. Used by [UniformAccessor.set]
     * after the type-check passes.
     */
    internal fun writeUniformBytes(offset: Int, bytes: ByteArray, length: Int) {
        require(offset + length <= uniformBytes.size) {
            "uniform write out of range : offset=$offset, length=$length, capacity=${uniformBytes.size}"
        }
        System.arraycopy(bytes, 0, uniformBytes, offset, length)
    }

    /** Read-back of the bound bytes (test surface). */
    internal fun uniformBytesSnapshot(): ByteArray = uniformBytes.copyOf()

    /** Bind a child value at slot [index] (test surface — the
     *  public path is via [ChildAccessor.set]). */
    internal fun setChildSlot(index: Int, value: Any?) {
        children[index] = value
    }

    /** Read-back of a child slot (test surface). */
    internal fun childSlot(index: Int): Any? = children[index]

    // ─── Accessor helpers ─────────────────────────────────────────────

    /**
     * Type-checked uniform writer. Each `set` overload validates
     * that the supplied Kotlin value matches the declared SkSL
     * type ; mismatch throws [IllegalArgumentException].
     *
     * Type mapping :
     *  - [SkRuntimeEffect.Uniform.Type.kFloat] → `set(Float)` /
     *    `set(FloatArray)` (size 1).
     *  - `kFloat2` → `set(FloatArray)` (size 2) ; or use
     *    [SkRuntimeEffect.Uniform.Type.kFloat3] / `kFloat4` for
     *    higher arity.
     *  - `kFloat4` ↔ [SkColor4f] : convenience overload converts
     *    the colour's `(R, G, B, A)` to a 4-float vector. Useful
     *    for `layout(color) uniform vec4 ...` declarations.
     *  - `kFloat3x3` / `kFloat2x2` / `kFloat4x4` → `set(SkMatrix)`
     *    (3×3 only ; 2×2 / 4×4 take a [FloatArray]).
     *  - `kInt*` → `set(Int)` / `set(IntArray)`.
     *
     * Array-typed uniforms (`uniform float foo[5];`) accept
     * `FloatArray(5)` — the size must match `count × type-stride`.
     */
    public class UniformAccessor internal constructor(
        public val variable: SkRuntimeEffect.Uniform,
        private val owner: SkRuntimeEffectBuilder,
    ) {
        /** Set a scalar [Float]. Variable must be of type
         *  [SkRuntimeEffect.Uniform.Type.kFloat] with `count == 1`. */
        public fun set(value: Float) {
            requireType(SkRuntimeEffect.Uniform.Type.kFloat, count = 1)
            writeFloats(floatArrayOf(value))
        }

        /** Set a scalar [Int]. Variable must be of type
         *  [SkRuntimeEffect.Uniform.Type.kInt] with `count == 1`. */
        public fun set(value: Int) {
            requireType(SkRuntimeEffect.Uniform.Type.kInt, count = 1)
            writeInts(intArrayOf(value))
        }

        /**
         * Set a [FloatArray] for any `kFloat*` / matrix uniform.
         * The array's size must equal `type.sizeBytes / 4 × count` ;
         * mismatch throws.
         */
        public fun set(values: FloatArray) {
            require(variable.type !in INT_TYPES) {
                "uniform '${variable.name}' is integer-typed (${variable.type}) ; use set(IntArray) or set(Int)"
            }
            val expectedSize = variable.sizeInBytes() / 4
            require(values.size == expectedSize) {
                "uniform '${variable.name}' expects $expectedSize floats (type ${variable.type}, count ${variable.count}), got ${values.size}"
            }
            writeFloats(values)
        }

        /**
         * Set an [IntArray] for any `kInt*` uniform. Size must
         * match `type.sizeBytes / 4 × count`.
         */
        public fun set(values: IntArray) {
            require(variable.type in INT_TYPES) {
                "uniform '${variable.name}' is float-typed (${variable.type}) ; use set(FloatArray) or set(Float)"
            }
            val expectedSize = variable.sizeInBytes() / 4
            require(values.size == expectedSize) {
                "uniform '${variable.name}' expects $expectedSize ints (type ${variable.type}, count ${variable.count}), got ${values.size}"
            }
            writeInts(values)
        }

        /**
         * Set a [SkColor4f] for a `vec4`-typed uniform. Convenience
         * for `layout(color) uniform vec4 ...` declarations —
         * upstream Skia auto-transforms colour-tagged uniforms to
         * the working colour space ; we just write the float
         * components verbatim (the impl is responsible for any
         * colour-space adjustment, matching upstream's
         * `kColor_Flag` semantics).
         */
        public fun set(value: SkColor4f) {
            requireType(SkRuntimeEffect.Uniform.Type.kFloat4, count = 1)
            writeFloats(floatArrayOf(value.fR, value.fG, value.fB, value.fA))
        }

        /**
         * Set a [SkMatrix] for a `mat3` / `float3x3` / `half3x3`
         * uniform. The matrix is written column-major (matches
         * Skia's `SkMatrix → 9 floats` storage convention).
         */
        public fun set(value: SkMatrix) {
            requireType(SkRuntimeEffect.Uniform.Type.kFloat3x3, count = 1)
            // Column-major: [sx, ky, persp0, kx, sy, persp1, tx, ty, persp2].
            writeFloats(
                floatArrayOf(
                    value.sx, value.ky, value.persp0,
                    value.kx, value.sy, value.persp1,
                    value.tx, value.ty, value.persp2,
                ),
            )
        }

        private fun requireType(expected: SkRuntimeEffect.Uniform.Type, count: Int) {
            require(variable.type == expected && variable.count == count) {
                "uniform '${variable.name}' is type ${variable.type}×${variable.count}, " +
                    "but value is type $expected×$count"
            }
        }

        private fun writeFloats(values: FloatArray) {
            val bytes = ByteArray(values.size * 4)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            for (v in values) buf.putFloat(v)
            owner.writeUniformBytes(variable.offset, bytes, bytes.size)
        }

        private fun writeInts(values: IntArray) {
            val bytes = ByteArray(values.size * 4)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            for (v in values) buf.putInt(v)
            owner.writeUniformBytes(variable.offset, bytes, bytes.size)
        }

        private companion object {
            val INT_TYPES = setOf(
                SkRuntimeEffect.Uniform.Type.kInt,
                SkRuntimeEffect.Uniform.Type.kInt2,
                SkRuntimeEffect.Uniform.Type.kInt3,
                SkRuntimeEffect.Uniform.Type.kInt4,
            )
        }
    }

    /**
     * Type-checked child slot writer. Each `set` overload validates
     * that the slot's declared [SkRuntimeEffect.ChildType] matches
     * the supplied Kotlin type ; mismatch throws
     * [IllegalArgumentException].
     */
    public class ChildAccessor internal constructor(
        public val child: SkRuntimeEffect.Child,
        private val owner: SkRuntimeEffectBuilder,
    ) {
        /** Bind a [SkShader] to a `shader`-typed child slot. */
        public fun set(value: SkShader?) {
            requireType(SkRuntimeEffect.ChildType.kShader)
            owner.setChildSlot(child.index, value)
        }

        /** Bind a [SkColorFilter] to a `colorFilter`-typed child slot. */
        public fun set(value: SkColorFilter?) {
            requireType(SkRuntimeEffect.ChildType.kColorFilter)
            owner.setChildSlot(child.index, value)
        }

        /** Bind a [SkBlender] to a `blender`-typed child slot. */
        public fun set(value: SkBlender?) {
            requireType(SkRuntimeEffect.ChildType.kBlender)
            owner.setChildSlot(child.index, value)
        }

        private fun requireType(expected: SkRuntimeEffect.ChildType) {
            require(child.type == expected) {
                "child '${child.name}' is declared as ${child.type}, " +
                    "can't bind a $expected"
            }
        }
    }
}
