package org.skia.effects.runtime.effects

import org.skia.effects.runtime.SkRuntimeEffectDispatch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * D2.4.c.4 — Hand-ported geometric intrinsic runtime effects.
 *
 * Maps the 16 unary intrinsic plots that
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_geometric)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * lays out across a 4×5 grid : `length` (2 forms), `distance`
 * (2 forms), `dot` (2 forms), `cross` (3 component plots),
 * `normalize` (3 forms), `faceforward`, `reflect` (2 forms),
 * `refract` (2 component plots).
 *
 * **GLSL semantics recap** :
 *  - `length(v)` — euclidean length. For scalar, `length(x) ≡ abs(x)`.
 *  - `distance(a, b) = length(a - b)`.
 *  - `dot(a, b)` — dot product.
 *  - `cross(a, b)` — 3D cross product (returns `vec3`).
 *  - `normalize(v) = v / length(v)`. For scalar, `normalize(x) ≡ sign(x)`.
 *  - `faceforward(N, I, Nref)` — returns `N` if `dot(Nref, I) < 0`,
 *    else `-N`. Used to flip a normal so it faces the incident ray.
 *  - `reflect(I, N) = I - 2*dot(N, I)*N`.
 *  - `refract(I, N, eta)` — Snell's law. Returns 0 if total internal
 *    reflection (`k < 0` where `k = 1 - eta²*(1 - dot(N, I)²)`),
 *    otherwise `eta*I - (eta*dot(N, I) + sqrt(k))*N`.
 *
 * **Swizzle decoding** : SkSL allows literal-component swizzles
 * like `v1.x0` (= `vec2(v1.x, 0)`), `v1.0x` (= `vec2(0, v1.x)`),
 * `p.xy1` (= `vec3(p.x, p.y, 1)`). Each entry's lambda decodes
 * the swizzle by hand into Kotlin scalar arithmetic — the math
 * collapses to a closed form once all the swizzles + `v1 = (1,1)`
 * substitutions resolve.
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsGeometricGM`](../../../../tests/RuntimeIntrinsicsGeometricGM.kt) :
 *    the `runtime_intrinsics_geometric` `DEF_SIMPLE_GM`.
 */
public object SkBuiltinShaderEffectsIntrinsicsGeometric {

    public fun registerAll() {
        for (entry in GEOMETRIC_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig
                .makeUnarySksl1d(entry.fn, requireES3 = false)
            SkRuntimeEffectDispatch.registerBuiltinIfAbsent(sksl) {
                SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl(entry.eval)
            }
        }
    }

    internal class Entry(
        val fn: String,
        val eval: (SkBuiltinShaderEffectsIntrinsicsTrig.IntrinsicContext) -> Float,
    )

    private fun length2(x: Float, y: Float): Float = sqrt(x * x + y * y)

    internal val GEOMETRIC_ENTRIES: List<Entry> = listOf(
        // Row 1 — length / distance
        Entry("length(x)") { ctx -> abs(ctx.x) },
        Entry("length(p)") { ctx -> length2(ctx.px, ctx.py) },
        Entry("distance(x, 0)") { ctx -> abs(ctx.x) },
        // distance(p, v1) where v1 = (1, 1) → sqrt((px-1)² + (py-1)²)
        Entry("distance(p, v1)") { ctx -> length2(ctx.px - 1f, ctx.py - 1f) },

        // Row 2 — dot
        Entry("dot(x, 2)") { ctx -> ctx.x * 2f },
        // dot(p, p.y1) where p.y1 = (py, 1) → px*py + py*1
        Entry("dot(p, p.y1)") { ctx -> ctx.px * ctx.py + ctx.py },

        // Row 3 — cross(p.xy1, p.y1x), where p.xy1 = (px, py, 1)
        // and p.y1x = (py, 1, px). 3D cross :
        //   .x = a.y*b.z - a.z*b.y = py*px - 1*1 = px*py - 1
        //   .y = a.z*b.x - a.x*b.z = 1*py - px*px = py - px²
        //   .z = a.x*b.y - a.y*b.x = px*1 - py*py = px - py²
        Entry("cross(p.xy1, p.y1x).x") { ctx -> ctx.px * ctx.py - 1f },
        Entry("cross(p.xy1, p.y1x).y") { ctx -> ctx.py - ctx.px * ctx.px },
        Entry("cross(p.xy1, p.y1x).z") { ctx -> ctx.px - ctx.py * ctx.py },

        // Row 4 — normalize / faceforward
        Entry("normalize(x)") { ctx ->
            // normalize(x) for scalar ≡ x / abs(x) ≡ sign(x). At
            // x = 0 GLSL returns NaN ; we mirror that via 0/0.
            sign(ctx.x)
        },
        Entry("normalize(p).x") { ctx ->
            val len = length2(ctx.px, ctx.py)
            ctx.px / len
        },
        Entry("normalize(p).y") { ctx ->
            val len = length2(ctx.px, ctx.py)
            ctx.py / len
        },
        // faceforward(v1, p.x0, v1.x0) where v1 = (1, 1),
        // p.x0 = (px, 0), v1.x0 = (1, 0) → Nref · I = 1*px + 0 = px.
        // If px < 0 return N = v1 (.x = 1) ; else return -N (.x = -1).
        Entry("faceforward(v1, p.x0, v1.x0).x") { ctx ->
            if (ctx.px < 0f) 1f else -1f
        },

        // Row 5 — reflect / refract
        // reflect(p.x1, v1.0x) where p.x1 = (px, 1), v1.0x = (0, 1).
        // dot(N, I) = 0*px + 1*1 = 1.
        // reflect = I - 2*1*N = (px - 0, 1 - 2) = (px, -1). .x = px.
        Entry("reflect(p.x1, v1.0x).x") { ctx -> ctx.px },
        // reflect(p.x1, normalize(v1)).y where N = (1, 1)/√2.
        // dot(N, I) = (px + 1)/√2.
        // reflect = I - 2*((px+1)/√2)*(1/√2, 1/√2)
        //         = (px - (px+1), 1 - (px+1)) = (-1, -px). .y = -px.
        Entry("reflect(p.x1, normalize(v1)).y") { ctx -> -ctx.px },
        // refract(v1.x0, v1.0x, x) — I = (1, 0), N = (0, 1), eta = x.
        // dot(N, I) = 0. k = 1 - x² * (1 - 0) = 1 - x².
        // If k < 0 (|x| > 1) : refract = (0, 0).
        // Else : refract = x*I - (0 + sqrt(1 - x²)) * N
        //                 = (x, -sqrt(1 - x²)).
        Entry("refract(v1.x0, v1.0x, x).x") { ctx ->
            val k = 1f - ctx.x * ctx.x
            if (k < 0f) 0f else ctx.x
        },
        Entry("refract(v1.x0, v1.0x, x).y") { ctx ->
            val k = 1f - ctx.x * ctx.x
            if (k < 0f) 0f else -sqrt(k)
        },
    )

    init { registerAll() }
}
