package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer

/**
 * D2.4.c.5 — Hand-ported matrix intrinsic runtime effects.
 *
 * Maps the 6 plot calls of
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_matrix)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * (matrixCompMult × 3 dims + inverse × 3 dims).
 *
 * **Distinct SkSL template** — unlike c.1-c.4 which all share
 * `make_unary_sksl_1d`, the matrix GMs use two specialised templates :
 *
 *  1. `make_matrix_comp_mult_sksl(N)` — uniforms : `floatNxN m1, m2` ;
 *     output : per-pixel `matrixCompMult(m1, m2)[k][l]` selected via
 *     `selN(p.x)` × `selN(p.y)` bin indicators.
 *  2. `make_matrix_inverse_sksl(N)` — uniforms : `float scale, bias`
 *     + `floatNxN m` ; output : per-pixel `inverse(m)[k][l] * scale + bias`.
 *
 * Both call into a `SKSL_MATRIX_SELECTORS` block that defines
 * `sel2 / sel3 / sel4 : float → vecN` partitioning `[0, 1]` into `N`
 * equal slices. Each slice activates one component (= 1) of the
 * vector ; the others are 0. The shader then dot-products the
 * matrix column at `selN(p.x)` index against the row indicator
 * `selN(p.y)`, yielding the single matrix cell `M[k][l]`.
 *
 * **Matrix layout** : SkSL `floatNxN` is column-major (matches
 * GLSL convention) and tightly packed in our parser
 * (`SkRuntimeEffectSignatureParser` sets `Uniform.type.sizeBytes`
 * = `4 × N²`, no std140 vec3 → vec4 column padding). So a 3×3
 * uniform takes 36 bytes ; a 4×4 takes 64. Element `M[k][l]` (column
 * k, row l) lives at byte offset `(k*N + l) * 4`.
 *
 * **GLSL `inverse(M)`** is implemented in [matrixInverse] via
 * Gauss-Jordan elimination with partial pivoting (numerical
 * stability for matrices upstream picks specifically as
 * "invertible with elements in `[-1, 1]`"). Reproduces
 * `kotlin.math.Float` precision ; tolerance vs GLSL hardware is
 * within a few ulps.
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsMatrixGM`](../../../../tests/RuntimeIntrinsicsMatrixGM.kt) :
 *    the `runtime_intrinsics_matrix` `DEF_SIMPLE_GM`.
 */
public object SkBuiltinShaderEffectsIntrinsicsMatrix {

    /**
     * SkSL template — `matrixCompMult` per-cell plot at dim N.
     * Mirrors upstream `make_matrix_comp_mult_sksl(int dim)`.
     */
    public fun makeMatrixCompMultSksl(dim: Int): String {
        require(dim in 2..4) { "matrixCompMult dim must be in 2..4 ; got $dim" }
        val typeTok = "float${dim}x$dim"
        val vecTok = "float$dim"
        val sel = "sel$dim"
        return "uniform $typeTok m1;" +
            "uniform $typeTok m2;" +
            SKSL_MATRIX_SELECTORS +
            "half4 main(float2 p) {" +
            "    $vecTok colSel = $sel(p.x);" +
            "    $vecTok rowSel = $sel(p.y);" +
            "    $vecTok col = matrixCompMult(m1, m2) * colSel;" +
            "    float  v = dot(col, rowSel);" +
            "    return v.xxx1;" +
            "}"
    }

    /**
     * SkSL template — `inverse(m)` per-cell plot at dim N.
     * Mirrors upstream `make_matrix_inverse_sksl(int dim)`.
     */
    public fun makeMatrixInverseSksl(dim: Int): String {
        require(dim in 2..4) { "inverse dim must be in 2..4 ; got $dim" }
        val typeTok = "float${dim}x$dim"
        val vecTok = "float$dim"
        val sel = "sel$dim"
        return "uniform float scale; uniform float bias;" +
            "uniform $typeTok m;" +
            SKSL_MATRIX_SELECTORS +
            "half4 main(float2 p) {" +
            "    $vecTok colSel = $sel(p.x);" +
            "    $vecTok rowSel = $sel(p.y);" +
            "    $vecTok col = inverse(m) * colSel;" +
            "    float  v = dot(col, rowSel) * scale + bias;" +
            "    return v.xxx1;" +
            "}"
    }

    public fun registerAll() {
        for (dim in 2..4) {
            SkRuntimeEffectDispatch.register(makeMatrixCompMultSksl(dim)) {
                MatrixCompMultImpl(dim)
            }
            SkRuntimeEffectDispatch.register(makeMatrixInverseSksl(dim)) {
                MatrixInverseImpl(dim)
            }
        }
    }

    // ─── selN bin selector ───────────────────────────────────────────

    /**
     * The "selN" partitioning function from upstream's
     * `SKSL_MATRIX_SELECTORS` macro. Returns the **index** of the
     * single component that is 1 (others 0) for `x ∈ [0, ∞)`.
     *
     * Edge values `0.33`, `0.66`, `0.5`, `0.25`, `0.75` are
     * upstream literals (not exact `1/3` / `1/4` etc. — preserve
     * fidelity).
     */
    internal fun selN(x: Float, n: Int): Int = when (n) {
        2 -> if (x < 0.5f) 0 else 1
        3 -> when {
            x < 0.33f -> 0
            x < 0.66f -> 1
            else -> 2
        }
        4 -> when {
            x < 0.25f -> 0
            x < 0.5f -> 1
            x < 0.75f -> 2
            else -> 3
        }
        else -> error("unsupported dim $n")
    }

    // ─── Impls ───────────────────────────────────────────────────────

    /**
     * Per-pixel impl for `matrixCompMult(m1, m2)` at a fixed
     * dimension `dim ∈ {2, 3, 4}`. Output at `(p.x, p.y)` is the
     * single cell `m1[k][l] * m2[k][l]` where `k = selN(p.x, dim)`
     * and `l = selN(p.y, dim)`.
     *
     * Uniform layout (column-major, tightly-packed — matches
     * `SkRuntimeEffectSignatureParser`'s alignment math) :
     *  - `m1` at offset 0 — `4 * dim²` bytes.
     *  - `m2` at next 16-byte-aligned offset — `4 * dim²` bytes.
     */
    public class MatrixCompMultImpl(public val dim: Int) : SkRuntimeImpl {
        private val matBytes = 4 * dim * dim
        private val m1Offset = 0
        private val m2Offset = ((matBytes + 15) / 16) * 16  // next 16-aligned

        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "m1", offset = m1Offset,
                type = matrixType(dim), count = 1, flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "m2", offset = m2Offset,
                type = matrixType(dim), count = 1, flags = 0,
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
            val k = selN(p.fX, dim)
            val l = selN(p.fY, dim)

            val cellOffset = (k * dim + l) * 4
            uniforms.position(m1Offset + cellOffset)
            val a = uniforms.float
            uniforms.position(m2Offset + cellOffset)
            val b = uniforms.float
            val v = a * b

            return SkColor4f(v, v, v, 1f)
        }
    }

    /**
     * Per-pixel impl for `inverse(m) * scale + bias` at a fixed
     * dimension `dim ∈ {2, 3, 4}`. Inverts the matrix once per
     * draw (cached in [setupInverse]) — note : because [shade] is
     * the per-pixel hot path and there's no per-effect setup hook,
     * the inverse is recomputed on every pixel call. Acceptable
     * for the GM (matrix size ≤ 4×4) ; if a real-world effect
     * needed this, we'd memoise via a per-byte-buffer-identity
     * cache, but this is a test-only port.
     *
     * Uniform layout :
     *  - `scale` at offset 0 — 4 bytes.
     *  - `bias` at offset 4 — 4 bytes.
     *  - `m` at offset 16 (next 16-aligned) — `4 * dim²` bytes.
     */
    public class MatrixInverseImpl(public val dim: Int) : SkRuntimeImpl {
        private val mOffset = 16

        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "scale", offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat, count = 1, flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "bias", offset = 4,
                type = SkRuntimeEffect.Uniform.Type.kFloat, count = 1, flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "m", offset = mOffset,
                type = matrixType(dim), count = 1, flags = 0,
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
            val scale = uniforms.float
            val bias = uniforms.float

            uniforms.position(mOffset)
            val m = FloatArray(dim * dim)
            for (i in m.indices) m[i] = uniforms.float

            val inv = matrixInverse(m, dim)
                ?: return SkColor4f(bias, bias, bias, 1f) // singular → fall through to bias
            val k = selN(p.fX, dim)
            val l = selN(p.fY, dim)

            val cellValue = inv[k * dim + l]
            val v = cellValue * scale + bias

            return SkColor4f(v, v, v, 1f)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun matrixType(dim: Int): SkRuntimeEffect.Uniform.Type = when (dim) {
        2 -> SkRuntimeEffect.Uniform.Type.kFloat2x2
        3 -> SkRuntimeEffect.Uniform.Type.kFloat3x3
        4 -> SkRuntimeEffect.Uniform.Type.kFloat4x4
        else -> error("unsupported matrix dim $dim")
    }

    /**
     * Generic NxN matrix inverse via Gauss-Jordan elimination with
     * partial pivoting. Input / output are column-major flat
     * arrays of size `n²`. Returns `null` for singular matrices.
     */
    internal fun matrixInverse(src: FloatArray, n: Int): FloatArray? {
        // Build augmented [A | I] in row-major scratch — easier
        // to drive Gauss-Jordan that way. Convert to/from
        // column-major at the boundaries.
        val a = Array(n) { row -> DoubleArray(2 * n) }
        for (col in 0 until n) {
            for (row in 0 until n) {
                a[row][col] = src[col * n + row].toDouble()
            }
        }
        // Identity on the right.
        for (row in 0 until n) {
            a[row][n + row] = 1.0
        }
        // Gauss-Jordan with partial pivoting.
        for (col in 0 until n) {
            // Find pivot row (largest absolute value at column `col`).
            var pivotRow = col
            var pivotVal = kotlin.math.abs(a[col][col])
            for (row in col + 1 until n) {
                val v = kotlin.math.abs(a[row][col])
                if (v > pivotVal) {
                    pivotRow = row
                    pivotVal = v
                }
            }
            if (pivotVal < 1e-9) return null  // singular
            if (pivotRow != col) {
                val tmp = a[col]
                a[col] = a[pivotRow]
                a[pivotRow] = tmp
            }
            // Normalize pivot row.
            val pivot = a[col][col]
            for (j in 0 until 2 * n) a[col][j] /= pivot
            // Eliminate other rows.
            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col]
                if (factor == 0.0) continue
                for (j in 0 until 2 * n) {
                    a[row][j] -= factor * a[col][j]
                }
            }
        }
        // Extract right half = inverse, convert back to column-major.
        val out = FloatArray(n * n)
        for (col in 0 until n) {
            for (row in 0 until n) {
                out[col * n + row] = a[row][n + col].toFloat()
            }
        }
        return out
    }

    /**
     * The `SKSL_MATRIX_SELECTORS` block from upstream's macro.
     * Inlined verbatim into both [makeMatrixCompMultSksl] and
     * [makeMatrixInverseSksl] so the canonical hash matches the
     * exact upstream output.
     */
    private const val SKSL_MATRIX_SELECTORS: String =
        "inline float2 sel2(float x) {" +
            "    return float2(" +
            "      x <  0.5 ? 1 : 0," +
            "      x >= 0.5 ? 1 : 0);" +
            "}" +
            "inline float3 sel3(float x) {" +
            "    return float3(" +
            "      x <  0.33             ? 1 : 0," +
            "      x >= 0.33 && x < 0.66 ? 1 : 0," +
            "      x >= 0.66             ? 1 : 0);" +
            "}" +
            "inline float4 sel4(float x) {" +
            "    return float4(" +
            "      x <  0.25             ? 1 : 0," +
            "      x >= 0.25 && x < 0.5  ? 1 : 0," +
            "      x >= 0.5  && x < 0.75 ? 1 : 0," +
            "      x >= 0.75             ? 1 : 0);" +
            "}"

    init { registerAll() }
}
