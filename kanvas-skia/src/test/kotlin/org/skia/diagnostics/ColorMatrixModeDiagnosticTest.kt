package org.skia.diagnostics

import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.skcms.SkNamedTransferFn
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.skcms.skcmsTransferFunctionEval
import org.skia.foundation.skcms.skcmsTransferFunctionInvert
import org.graphiks.kanvas.skia.gm.composite.ColorMatrixGm
import org.skia.testing.TestUtils
import java.io.File
import kotlin.math.abs

/**
 * **Q5 Linear sRGB diagnostic** —
 * see [MIGRATION_PLAN_RASTER_COMPLETION.md § Q5](../../../../../../../MIGRATION_PLAN_RASTER_COMPLETION.md).
 *
 * Question : Skia's
 * [`gm/colormatrix.cpp`](https://github.com/google/skia/blob/main/gm/colormatrix.cpp)
 * applies a 4×5 colour matrix to a source image. **Where in the
 * pipeline does upstream Skia evaluate the matrix — in encoded sRGB
 * (matrix × encoded RGB) or in linear sRGB (encode(matrix ×
 * decode(encoded)))?**
 *
 * The Phase 7e' attempt (decode → matrix → encode wrapper) regressed
 * `ColorMatrixGm` from **69 % → 49 %** vs upstream, suggesting the
 * Linear hypothesis was wrong. The current code path applies the
 * matrix in encoded sRGB and floors at **65 %** with an actual score
 * of **69.28 %**. The residual ~30 % gap was attributed in the
 * `ColorMatrixTest` kdoc to "the encoded-vs-linear sRGB gamma curve
 * difference (Skia evaluates the matrix in linear sRGB)".
 *
 * That kdoc and the Phase 7e' regression telemetry **disagree on
 * the truth**. This test settles which one is right by sampling
 * representative pixels per `(cell, source-bitmap)` pair, computing
 * both the encoded and linear expected outputs from the input + the
 * matrix, and comparing each against the upstream PNG reference.
 *
 * **Output** : a markdown table written to
 * `kanvas-skia/build/q5-colormatrix-mode-diagnostic.md` with one
 * row per cell, plus a global summary (encoded-wins / linear-wins
 * / tie counts and aggregate channel distances). Stdout also gets
 * the summary.
 *
 * **Test outcome** : the assertion is intentionally lax — the test
 * always passes. The point is the markdown report, not a pass/fail
 * signal. Tooling-only ; not a regression test.
 *
 * **Findings (2026-05-08)** : see the report file ;
 * [Q5 in MIGRATION_PLAN_RASTER_COMPLETION.md](../../../../../../../MIGRATION_PLAN_RASTER_COMPLETION.md)
 * captures the conclusion + next-step recommendation.
 */
class ColorMatrixModeDiagnosticTest {

    @Test
    fun `Q5 — diagnose linear vs encoded sRGB on ColorMatrixGm`() {
        val gm = ColorMatrixGm()
        val reference = TestUtils.loadReferenceBitmap(gm.name)
            ?: error("missing original-888/colormatrix.png reference")
        // Recreate the source bitmaps via the *same* code path the GM
        // uses. We can't reach into the private ColorMatrixGm helpers,
        // so we replicate the recipe here — reference-quality match
        // because the GM's bitmaps are pure data (no sub-pixel
        // rasterisation, no shaders other than the gradient).
        val solidImg = makeSolidImage(64, 64)
        val transparentImg = makeTransparentImage(64, 64)

        val cells = buildList {
            // First row : solid (64×64 RG-gradient, opaque).
            add(Cell(0, 0, ColorMatrixGm.identityMatrix(), solidImg, "solid / identity"))
            add(Cell(80, 0, ColorMatrixGm.saturationMatrix(0.0f), solidImg, "solid / sat=0.0"))
            add(Cell(160, 0, ColorMatrixGm.saturationMatrix(0.5f), solidImg, "solid / sat=0.5"))
            add(Cell(240, 0, ColorMatrixGm.saturationMatrix(1.0f), solidImg, "solid / sat=1.0"))
            add(Cell(320, 0, ColorMatrixGm.saturationMatrix(2.0f), solidImg, "solid / sat=2.0"))
            add(Cell(400, 0, ColorMatrixGm.redToAlphaWhiteMatrix(), solidImg, "solid / red→α"))
            // Second row : transparent (alpha-gradient).
            add(Cell(0, 80, ColorMatrixGm.identityMatrix(), transparentImg, "α-grad / identity"))
            add(Cell(80, 80, ColorMatrixGm.saturationMatrix(0.0f), transparentImg, "α-grad / sat=0.0"))
            add(Cell(160, 80, ColorMatrixGm.saturationMatrix(0.5f), transparentImg, "α-grad / sat=0.5"))
            add(Cell(240, 80, ColorMatrixGm.saturationMatrix(1.0f), transparentImg, "α-grad / sat=1.0"))
            add(Cell(320, 80, ColorMatrixGm.saturationMatrix(2.0f), transparentImg, "α-grad / sat=2.0"))
            add(Cell(400, 80, ColorMatrixGm.redToAlphaWhiteMatrix(), transparentImg, "α-grad / red→α"))
        }

        // Sample positions inside each 64×64 cell — corners + centre.
        val samples = listOf(8, 32, 56)

        val rows = mutableListOf<String>()
        rows += "| Cell | Encoded Σ | Linear Σ | Verdict |"
        rows += "|------|----------:|---------:|---------|"
        var encodedWins = 0
        var linearWins = 0
        var ties = 0
        var encodedTotal = 0L
        var linearTotal = 0L

        for (cell in cells) {
            var enc = 0
            var lin = 0
            for (sx in samples) for (sy in samples) {
                val input = cell.image[sy * 64 + sx]
                val ref = reference.getPixel(cell.x + sx, cell.y + sy)
                val encodedExpected = applyMatrixEncoded(cell.matrix, input)
                val linearExpected = applyMatrixLinear(cell.matrix, input)
                enc += channelDistance(encodedExpected, ref)
                lin += channelDistance(linearExpected, ref)
            }
            encodedTotal += enc
            linearTotal += lin
            val verdict = when {
                lin < enc -> { linearWins++; "**linear** wins by ${enc - lin}" }
                enc < lin -> { encodedWins++; "**encoded** wins by ${lin - enc}" }
                else -> { ties++; "tie" }
            }
            rows += "| ${cell.label} | $enc | $lin | $verdict |"
        }

        val summary = buildString {
            append("# Q5 — ColorMatrixGm linear-vs-encoded sRGB diagnostic\n\n")
            append("**Method** : per-cell sample 9 pixels (3×3 grid at offsets 8/32/56) ; ")
            append("for each, compute the encoded-mode expected ")
            append("(`out = matrix × encoded_input`) and the linear-mode expected ")
            append("(`out = encode(matrix × decode(encoded_input))`) ")
            append("from the 4×5 colour matrix + the source bitmap's pixel value, ")
            append("then sum the per-channel L1 distance against the upstream PNG ")
            append("reference (`original-888/colormatrix.png`). Lower Σ = closer to ")
            append("upstream's actual output.\n\n")
            append("**Per-cell results** (Σ over 9 samples × 4 channels = 36 values, ")
            append("each in `[0, 255]`) :\n\n")
            for (row in rows) appendLine(row)
            appendLine()
            append("**Summary** :\n\n")
            append("- encoded-mode wins : **$encodedWins** cells\n")
            append("- linear-mode wins : **$linearWins** cells\n")
            append("- ties : $ties\n")
            append("- aggregate Σ over 12 cells : encoded = $encodedTotal, linear = $linearTotal ")
            append("(lower = closer to upstream)\n\n")
            val (winner, why) = when {
                encodedWins > linearWins -> "encoded sRGB" to "$encodedWins of 12 cells favour the encoded interpretation, with aggregate Σ $encodedTotal vs $linearTotal"
                linearWins > encodedWins -> "linear sRGB" to "$linearWins of 12 cells favour the linear interpretation, with aggregate Σ $linearTotal vs $encodedTotal"
                else -> "neither (tie)" to "the two modes are equally close ; the gap likely lies elsewhere (sampling, alpha-modulation order, working-space xform)"
            }
            append("**Diagnostic** : upstream Skia appears to apply the colour matrix in **$winner**. ")
            append("Rationale : $why.\n\n")
            append("**Implication** : ")
            when {
                encodedWins > linearWins ->
                    append("the current encoded-sRGB application path is closer to upstream than the Phase 7e' linear wrapper that was tried + reverted. The 30 % residual gap (current 69 % score vs ratchet 95 %+) is **not** the gamma curve — the gap is elsewhere (sampling precision, alpha-channel ordering, working-space xform).\n")
                linearWins > encodedWins ->
                    append("upstream evaluates the matrix in linear sRGB. The Phase 7e' regression must have been a different bug (e.g. wrong matrix application in the wrapper) — re-attempt the linear pipeline with corrected wrapping.\n")
                else ->
                    append("neither pure linear nor pure encoded is dominant. Per-cell behaviour suggests a more nuanced upstream pipeline ; spot-investigate the cells with the largest residuals.\n")
            }
        }

        // Persist to disk for future reference.
        val outFile = File("build/q5-colormatrix-mode-diagnostic.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(summary)
        println(summary)
    }

    // ─── Source bitmap recreation (kept in sync with ColorMatrixGm) ──

    /**
     * 64×64 RGBA pixels with R = x*255/64, G = y*255/64, B = 0,
     * A = 0xFF. Matches the GM's `createSolidBitmap` recipe
     * exactly — both produce identical bytes.
     */
    private fun makeSolidImage(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = x * 255 / width
                val g = y * 255 / height
                pixels[y * width + x] = SkColorSetARGB(0xFF, r, g, 0)
            }
        }
        return pixels
    }

    /**
     * 64×64 black-to-white linear gradient sweeping alpha from 0 to 1
     * along the diagonal (top-left = 0x00000000, bottom-right =
     * 0xFFFFFFFF). The GM uses `SkLinearGradient` ; we synthesise the
     * same straight-line interpolation directly so the test doesn't
     * depend on the gradient code path.
     */
    private fun makeTransparentImage(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Diagonal parameter ∈ [0, 1] : for the upstream
                // gradient with endpoints (0,0) and (w,h), the
                // parametric t at (x, y) is the projection onto the
                // direction vector (w, h). Pre-computed as
                // `(x*w + y*h) / (w² + h²)`.
                val t = ((x.toFloat() * width + y.toFloat() * height) /
                    (width * width + height * height).toFloat()).coerceIn(0f, 1f)
                val v = (t * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[y * width + x] = SkColorSetARGB(v, v, v, v)
            }
        }
        return pixels
    }

    private data class Cell(
        val x: Int,
        val y: Int,
        val matrix: FloatArray,
        val image: IntArray,
        val label: String,
    )

    // ─── Matrix application — encoded vs linear sRGB ─────────────────

    /**
     * Apply the 4×5 [matrix] directly to the encoded-sRGB channels of
     * [input]. Mirrors what Skia's pipeline does today **on our
     * side** : the matrix coefficients multiply the byte-level RGBA
     * values without first decoding the gamma. Output is also
     * encoded-sRGB.
     */
    private fun applyMatrixEncoded(matrix: FloatArray, input: Int): Int {
        val r = SkColorGetR(input) / 255f
        val g = SkColorGetG(input) / 255f
        val b = SkColorGetB(input) / 255f
        val a = SkColorGetA(input) / 255f
        val (or, og, ob, oa) = applyMatrix4x5(matrix, r, g, b, a)
        return SkColorSetARGB(toByte(oa), toByte(or), toByte(og), toByte(ob))
    }

    /**
     * Apply the matrix in **linear sRGB** : decode the input through
     * the sRGB transfer fn, multiply, re-encode.  Mirrors what the
     * Phase 7e' attempt was supposed to do.
     *
     * Alpha is **not** linearised — only RGB. Matches upstream's
     * "alpha is encoded but doesn't go through the gamma curve"
     * convention, since alpha is by definition linear (0 = fully
     * transparent, 1 = fully opaque, no gamma).
     */
    private fun applyMatrixLinear(matrix: FloatArray, input: Int): Int {
        val rEnc = SkColorGetR(input) / 255f
        val gEnc = SkColorGetG(input) / 255f
        val bEnc = SkColorGetB(input) / 255f
        val a = SkColorGetA(input) / 255f
        val rLin = decodeSrgb(rEnc)
        val gLin = decodeSrgb(gEnc)
        val bLin = decodeSrgb(bEnc)
        val (or, og, ob, oa) = applyMatrix4x5(matrix, rLin, gLin, bLin, a)
        // Re-encode RGB ; alpha stays linear.
        val orEnc = encodeSrgb(or.coerceIn(0f, 1f))
        val ogEnc = encodeSrgb(og.coerceIn(0f, 1f))
        val obEnc = encodeSrgb(ob.coerceIn(0f, 1f))
        return SkColorSetARGB(toByte(oa), toByte(orEnc), toByte(ogEnc), toByte(obEnc))
    }

    /**
     * Apply the 4×5 colour matrix as upstream Skia
     * (`include/core/SkColorMatrix.h`) :
     * ```
     * | r' |   | m00 m01 m02 m03 m04 |   | r |
     * | g' |   | m10 m11 m12 m13 m14 |   | g |
     * | b' | = | m20 m21 m22 m23 m24 | * | b |
     * | a' |   | m30 m31 m32 m33 m34 |   | a |
     *                                    | 1 |
     * ```
     * The 5th column is the bias.
     */
    private fun applyMatrix4x5(
        m: FloatArray,
        r: Float, g: Float, b: Float, a: Float,
    ): FloatArray {
        return floatArrayOf(
            m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4],
            m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9],
            m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14],
            m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19],
        )
    }

    private fun toByte(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    // ─── sRGB transfer fn (encoded ↔ linear) ─────────────────────────

    private val sRGB: SkcmsTransferFunction = SkNamedTransferFn.kSRGB
    private val sRGBInverse: SkcmsTransferFunction =
        skcmsTransferFunctionInvert(sRGB) ?: error("sRGB inverse should always exist")

    /** Encoded sRGB channel ∈ `[0, 1]` → linear sRGB ∈ `[0, 1]`. EOTF. */
    private fun decodeSrgb(x: Float): Float = skcmsTransferFunctionEval(sRGB, x)

    /** Linear sRGB channel ∈ `[0, 1]` → encoded sRGB ∈ `[0, 1]`. OETF. */
    private fun encodeSrgb(x: Float): Float = skcmsTransferFunctionEval(sRGBInverse, x)

    // ─── Per-pixel L1 channel distance ───────────────────────────────

    private fun channelDistance(a: Int, b: Int): Int =
        abs(SkColorGetR(a) - SkColorGetR(b)) +
            abs(SkColorGetG(a) - SkColorGetG(b)) +
            abs(SkColorGetB(a) - SkColorGetB(b)) +
            abs(SkColorGetA(a) - SkColorGetA(b))
}
