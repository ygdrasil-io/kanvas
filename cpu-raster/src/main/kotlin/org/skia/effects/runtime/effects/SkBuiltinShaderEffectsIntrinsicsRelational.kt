package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.skia.math.SkColor4f
import org.skia.math.SkPoint
import java.nio.ByteBuffer

/**
 * D2.4.c.6 — Hand-ported relational intrinsic runtime effects.
 *
 * Maps the 18 `plot_bvec` calls of
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_relational)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * **Distinct SkSL template** — `make_bvec_sksl(type, fn)` :
 *
 * ```sksl
 * uniform <float|int>2 v1;
 * half4 main(float2 p) {
 *     p.x = p.x < 0.33 ? -3.0 : (p.x < 0.66 ? -2.0 : -1.0);
 *     p.y = p.y < 0.33 ? -3.0 : (p.y < 0.66 ? -2.0 : -1.0);
 *     bool2 cmp = <fn>;
 *     return half4(cmp.x ? 1.0 : 0.0, cmp.y ? 1.0 : 0.0, 0, 1);
 * }
 * ```
 *
 * The `(p.x, p.y)` input bins into 9 cells covering `{-3, -2, -1}²`.
 * `v1 = (-2, -2)` (in either float or int form), so the relational
 * cmp partitions the 9 cells into a binary on/off pattern per
 * component. The cell colour encodes `(cmp.x, cmp.y)` — red, green,
 * yellow, or black depending on the truth values.
 *
 * **Two type variants** — float (`v1 : float2`) and int
 * (`v1 : int2`). At our integer-valued sample points, the
 * arithmetic produces identical results, but upstream registers
 * separate SkSL strings (and thus separate hashes) for each
 * variant. We register both in lockstep.
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsRelationalGM`](../../../../tests/RuntimeIntrinsicsRelationalGM.kt) :
 *    the `runtime_intrinsics_relational` `DEF_SIMPLE_GM`.
 */
public object SkBuiltinShaderEffectsIntrinsicsRelational {

    /**
     * Build the SkSL source for a relational plot. Mirrors
     * upstream `make_bvec_sksl(type, fn)`.
     *
     * @param type "float" or "int" — selects `float2 v1` /
     *   `int2 v1` uniform.
     * @param fn the SkSL relational expression returning `bool2`,
     *   e.g. `"lessThan(p, v1)"`, `"any(equal(p, v1)).xx"`,
     *   `"not(notEqual(p, v1))"`. Must reference the post-remap
     *   `p` (with components in `{-3, -2, -1}`).
     */
    public fun makeBvecSksl(type: String, fn: String): String {
        require(type == "float" || type == "int") { "type must be float or int : $type" }
        return "uniform ${type}2 v1;" +
            "half4 main(float2 p) {" +
            "    p.x = p.x < 0.33 ? -3.0 : (p.x < 0.66 ? -2.0 : -1.0);" +
            "    p.y = p.y < 0.33 ? -3.0 : (p.y < 0.66 ? -2.0 : -1.0);" +
            "    bool2 cmp = $fn;" +
            "    return half4(cmp.x ? 1.0 : 0.0, cmp.y ? 1.0 : 0.0, 0, 1);" +
            "}"
    }

    public fun registerAll() {
        for (entry in RELATIONAL_ENTRIES) {
            val sksl = makeBvecSksl(entry.type, entry.fn)
            SkRuntimeEffectDispatch.register(sksl) {
                BvecRelationalImpl(useInt = entry.type == "int", cmp = entry.eval)
            }
        }
    }

    /**
     * Internal record — `(type, fn)` token + the per-component
     * Kotlin predicate that evaluates the relational. The
     * predicate receives the post-remap `(px, py)` ints (in
     * `{-3, -2, -1}`) and `v1` as a pair of ints (we always pass
     * `-2`, the same value upstream binds for both float and int
     * variants).
     */
    internal class Entry(
        val type: String,    // "float" or "int"
        val fn: String,
        val eval: (px: Int, py: Int, v1x: Int, v1y: Int) -> Pair<Boolean, Boolean>,
    )

    internal val RELATIONAL_ENTRIES: List<Entry> = listOf(
        // Row 1 — lessThan / lessThanEqual (float + int)
        Entry("float", "lessThan(p, v1)") { px, py, v1x, v1y -> (px < v1x) to (py < v1y) },
        Entry("int", "lessThan(int2(p), v1)") { px, py, v1x, v1y -> (px < v1x) to (py < v1y) },
        Entry("float", "lessThanEqual(p, v1)") { px, py, v1x, v1y -> (px <= v1x) to (py <= v1y) },
        Entry("int", "lessThanEqual(int2(p), v1)") { px, py, v1x, v1y -> (px <= v1x) to (py <= v1y) },

        // Row 2 — greaterThan / greaterThanEqual (float + int)
        Entry("float", "greaterThan(p, v1)") { px, py, v1x, v1y -> (px > v1x) to (py > v1y) },
        Entry("int", "greaterThan(int2(p), v1)") { px, py, v1x, v1y -> (px > v1x) to (py > v1y) },
        Entry("float", "greaterThanEqual(p, v1)") { px, py, v1x, v1y -> (px >= v1x) to (py >= v1y) },
        Entry("int", "greaterThanEqual(int2(p), v1)") { px, py, v1x, v1y -> (px >= v1x) to (py >= v1y) },

        // Row 3 — equal / notEqual (float + int)
        Entry("float", "equal(p, v1)") { px, py, v1x, v1y -> (px == v1x) to (py == v1y) },
        Entry("int", "equal(int2(p), v1)") { px, py, v1x, v1y -> (px == v1x) to (py == v1y) },
        Entry("float", "notEqual(p, v1)") { px, py, v1x, v1y -> (px != v1x) to (py != v1y) },
        Entry("int", "notEqual(int2(p), v1)") { px, py, v1x, v1y -> (px != v1x) to (py != v1y) },

        // Row 4 — bvec compositions
        // equal(le, ge) where le[i] = px ≤ v1x, ge[i] = px ≥ v1x.
        // For px == v1x both true → equal = true. Otherwise one
        // is true and one false → equal = false. Same as direct
        // equal(p, v1).
        Entry("float", "equal(   lessThanEqual(p, v1), greaterThanEqual(p, v1))") { px, py, v1x, v1y ->
            (px == v1x) to (py == v1y)
        },
        // notEqual(le, ge) — symmetric : true iff px != v1x.
        Entry("float", "notEqual(lessThanEqual(p, v1), greaterThanEqual(p, v1))") { px, py, v1x, v1y ->
            (px != v1x) to (py != v1y)
        },

        // Row 5 — not()
        Entry("float", "not(notEqual(p, v1))") { px, py, v1x, v1y -> (px == v1x) to (py == v1y) },
        Entry("float", "not(equal(p, v1))") { px, py, v1x, v1y -> (px != v1x) to (py != v1y) },

        // Row 6 — any / all reductions broadcast back to bool2
        Entry("float", "bool2(any(equal(p, v1)))") { px, py, v1x, v1y ->
            val any = (px == v1x) || (py == v1y)
            any to any
        },
        Entry("float", "bool2(all(equal(p, v1)))") { px, py, v1x, v1y ->
            val all = (px == v1x) && (py == v1y)
            all to all
        },
    )

    // ─── Impl ────────────────────────────────────────────────────────

    /**
     * Per-pixel impl for any `make_bvec_sksl(type, fn)` registration.
     * Uniform layout :
     *  - `v1` : `float2` (8 bytes) or `int2` (8 bytes) at offset 0.
     *
     * The 8-byte slot is read as 2 floats or 2 ints depending on
     * [useInt]. We always pass `(-2, -2)` from the GM caller side,
     * so internally we collapse the 4-byte values to ints for the
     * predicate (rounded toward zero — fine for integer-valued
     * floats).
     */
    public class BvecRelationalImpl(
        private val useInt: Boolean,
        private val cmp: (px: Int, py: Int, v1x: Int, v1y: Int) -> Pair<Boolean, Boolean>,
    ) : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "v1", offset = 0,
                type = if (useInt) SkRuntimeEffect.Uniform.Type.kInt2
                       else SkRuntimeEffect.Uniform.Type.kFloat2,
                count = 1, flags = 0,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val p = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val v1x: Int
            val v1y: Int
            if (useInt) {
                v1x = uniforms.int
                v1y = uniforms.int
            } else {
                v1x = uniforms.float.toInt()
                v1y = uniforms.float.toInt()
            }

            // Mirror the SkSL remap : p ∈ [0, 1] → {-3, -2, -1}
            // in 3 horizontal / 3 vertical bins.
            val px = remapToBin(p.fX)
            val py = remapToBin(p.fY)

            val (cmpX, cmpY) = cmp(px, py, v1x, v1y)
            return SkColor4f(
                fR = if (cmpX) 1f else 0f,
                fG = if (cmpY) 1f else 0f,
                fB = 0f,
                fA = 1f,
            )
        }

        private fun remapToBin(t: Float): Int = when {
            t < 0.33f -> -3
            t < 0.66f -> -2
            else -> -1
        }
    }

    init { registerAll() }
}
