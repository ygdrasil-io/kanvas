package org.skia.diagnostics

import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * Phase 0 diagnostic #2: confirm that the canonical sRGB → Rec.2020 transform
 * (using the exact ICC parameters extracted from `bigrect.png`) maps pure sRGB
 * blue (0, 0, 255) onto the observed reference value (43, 13, 242).
 *
 * If this matches, Phase 0 is fully solved: target colorspace = Rec.2020 with
 * the parametric transfer function from the file. The remaining phases just
 * port the math.
 */
class ColorMathSanityTest {

    private val srgbToXYZD50 = arrayOf(
        doubleArrayOf(0.43603516, 0.38511658, 0.14305115),
        doubleArrayOf(0.22248840, 0.71690369, 0.06060791),
        doubleArrayOf(0.01391602, 0.09706116, 0.71409607),
    )

    private val rec2020ToXYZD50 = arrayOf(
        doubleArrayOf( 0.6734619140625,  0.1656646728515625,  0.1251068115234375),
        doubleArrayOf( 0.2790374755859375, 0.6753387451171875, 0.045623779296875),
        doubleArrayOf(-0.0019378662109375, 0.0299835205078125, 0.7971649169921875),
    )

    private val srgbTF = TransferFunction(
        g = 2.4, a = 1.0 / 1.055, b = 0.055 / 1.055,
        c = 1.0 / 12.92, d = 0.04045, e = 0.0, f = 0.0,
    )

    private val rec2020TF = TransferFunction(
        g = 2.2222137451171875,
        a = 0.90966796875,
        b = 0.09033203125,
        c = 0.22222900390625,
        d = 0.08123779296875,
        e = 0.0, f = 0.0,
    )

    private data class TransferFunction(
        val g: Double, val a: Double, val b: Double,
        val c: Double, val d: Double, val e: Double, val f: Double,
    ) {
        fun apply(x: Double): Double =
            if (x >= d) (a * x + b).pow(g) + e else c * x + f

        fun inverse(): TransferFunction {
            // Forward:   y = (a*x + b)^g + e   for x >= d
            //            y = c*x + f           for x <  d
            // Inverse:   x = (Y/a^g - e/a^g)^(1/g) + (-b/a)
            // Re-pack to (A*Y + B)^G + E template:
            //   A = 1/a^g, B = -e/a^g, G = 1/g, E = -b/a
            //   C = 1/c, F = -f/c, D' = c*d + f
            val aPowG = a.pow(g)
            return TransferFunction(
                g = 1.0 / g,
                a = 1.0 / aPowG,
                b = -e / aPowG,
                c = if (c == 0.0) 0.0 else 1.0 / c,
                d = c * d + f,
                e = -b / a,
                f = if (c == 0.0) 0.0 else -f / c,
            )
        }
    }

    private fun matVec(m: Array<DoubleArray>, v: DoubleArray): DoubleArray =
        DoubleArray(3) { i -> m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] }

    private fun invert3x3(m: Array<DoubleArray>): Array<DoubleArray> {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val inv = 1.0 / det
        return arrayOf(
            doubleArrayOf((e * i - f * h) * inv, (c * h - b * i) * inv, (b * f - c * e) * inv),
            doubleArrayOf((f * g - d * i) * inv, (a * i - c * g) * inv, (c * d - a * f) * inv),
            doubleArrayOf((d * h - e * g) * inv, (b * g - a * h) * inv, (a * e - b * d) * inv),
        )
    }

    @Test
    fun `pure sRGB blue maps to Rec_2020 (43, 13, 242)`() {
        // 1. Decode 8-bit sRGB blue → linear sRGB
        val srgbEncoded = doubleArrayOf(0.0 / 255.0, 0.0 / 255.0, 255.0 / 255.0)
        val srgbLinear = doubleArrayOf(
            srgbTF.apply(srgbEncoded[0]),
            srgbTF.apply(srgbEncoded[1]),
            srgbTF.apply(srgbEncoded[2]),
        )

        // 2. Gamut transform: sRGB-linear → D50 XYZ → Rec.2020-linear
        val xyz = matVec(srgbToXYZD50, srgbLinear)
        val rec2020Inv = invert3x3(rec2020ToXYZD50)
        val rec2020Linear = matVec(rec2020Inv, xyz)

        // 3. Encode Rec.2020-linear → 8-bit
        val rec2020TFInv = rec2020TF.inverse()
        val rec2020Encoded = doubleArrayOf(
            rec2020TFInv.apply(rec2020Linear[0]),
            rec2020TFInv.apply(rec2020Linear[1]),
            rec2020TFInv.apply(rec2020Linear[2]),
        )
        val r = (rec2020Encoded[0] * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val g = (rec2020Encoded[1] * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val b = (rec2020Encoded[2] * 255.0 + 0.5).toInt().coerceIn(0, 255)

        println("sRGB(0,0,255) -> linear sRGB: (${"%.4f".format(srgbLinear[0])}, ${"%.4f".format(srgbLinear[1])}, ${"%.4f".format(srgbLinear[2])})")
        println("D50 XYZ:                       (${"%.4f".format(xyz[0])}, ${"%.4f".format(xyz[1])}, ${"%.4f".format(xyz[2])})")
        println("linear Rec.2020:               (${"%.4f".format(rec2020Linear[0])}, ${"%.4f".format(rec2020Linear[1])}, ${"%.4f".format(rec2020Linear[2])})")
        println("encoded Rec.2020 (float):      (${"%.6f".format(rec2020Encoded[0])}, ${"%.6f".format(rec2020Encoded[1])}, ${"%.6f".format(rec2020Encoded[2])})")
        println("encoded Rec.2020 (8-bit):      ($r, $g, $b)")
        println("expected from bigrect.png:     (43, 13, 242)")
    }

    @Test
    fun `pure sRGB red maps to Rec_2020 expected`() {
        // For ground-truth crosscheck. Reference value not yet observed —
        // print only.
        val srgbEncoded = doubleArrayOf(255.0 / 255.0, 0.0, 0.0)
        val srgbLinear = doubleArrayOf(
            srgbTF.apply(srgbEncoded[0]),
            srgbTF.apply(srgbEncoded[1]),
            srgbTF.apply(srgbEncoded[2]),
        )
        val xyz = matVec(srgbToXYZD50, srgbLinear)
        val rec2020Linear = matVec(invert3x3(rec2020ToXYZD50), xyz)
        val tfInv = rec2020TF.inverse()
        val r = (tfInv.apply(rec2020Linear[0]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val g = (tfInv.apply(rec2020Linear[1]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val b = (tfInv.apply(rec2020Linear[2]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        println("sRGB(255,0,0) -> Rec.2020(8-bit): ($r, $g, $b)")
    }

    @Test
    fun `pure sRGB green maps to Rec_2020 expected`() {
        val srgbEncoded = doubleArrayOf(0.0, 1.0, 0.0)
        val srgbLinear = doubleArrayOf(
            srgbTF.apply(srgbEncoded[0]),
            srgbTF.apply(srgbEncoded[1]),
            srgbTF.apply(srgbEncoded[2]),
        )
        val xyz = matVec(srgbToXYZD50, srgbLinear)
        val rec2020Linear = matVec(invert3x3(rec2020ToXYZD50), xyz)
        val tfInv = rec2020TF.inverse()
        val r = (tfInv.apply(rec2020Linear[0]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val g = (tfInv.apply(rec2020Linear[1]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val b = (tfInv.apply(rec2020Linear[2]) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        println("sRGB(0,255,0) -> Rec.2020(8-bit): ($r, $g, $b)")
    }
}
