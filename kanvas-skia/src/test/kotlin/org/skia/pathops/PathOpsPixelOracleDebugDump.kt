package org.skia.pathops

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * D1.4.a-debug helper — exposes the same scale/rasterise/pixel-set-op
 * pipeline used by [PathOpsPixelOracle], but returns the expected and
 * actual bitmaps as ASCII strings for ad-hoc inspection in standalone
 * tests. Lives in `test/` because it's only used by debug tests. Reuses
 * the oracle's constants ; bumps in [PathOpsPixelOracle] should be
 * mirrored here.
 */
internal object PathOpsPixelOracleDebugDump {

    private const val W = 64
    private const val H = 64

    data class Result(
        val expectedAscii: String,
        val actualAscii: String,
        val diffAscii: String,
        val errors: Int,
    )

    fun dump(pathA: SkPath, pathB: SkPath, op: SkPathOp, result: SkPath): Result {
        val u = unionBounds(pathA, pathB)
            ?: return Result("(degenerate)", "(degenerate)", "(degenerate)", -1)
        val m = scaleMatrix(u)
        val a = rasterise(pathA.makeTransform(m))
        val b = rasterise(pathB.makeTransform(m))
        val expected = applyPixelOp(a, b, op)
        val actual = rasterise(result.makeTransform(m))
        val err = twoByTwoErrors(expected, actual)
        return Result(
            expectedAscii = asAscii(expected),
            actualAscii = asAscii(actual),
            diffAscii = diffAscii(expected, actual),
            errors = err,
        )
    }

    private fun unionBounds(a: SkPath, b: SkPath): SkRect? {
        val ra = a.computeBounds(); val rb = b.computeBounds()
        if (ra.isEmpty && rb.isEmpty) return null
        if (ra.isEmpty) return rb
        if (rb.isEmpty) return ra
        return SkRect.MakeLTRB(
            kotlin.math.min(ra.left, rb.left),
            kotlin.math.min(ra.top, rb.top),
            kotlin.math.max(ra.right, rb.right),
            kotlin.math.max(ra.bottom, rb.bottom),
        )
    }

    private fun scaleMatrix(b: SkRect): SkMatrix {
        val w = b.width().coerceAtLeast(4f)
        val h = b.height().coerceAtLeast(4f)
        val sx = (W - 2).toFloat() / w
        val sy = (H - 2).toFloat() / h
        val tx = 1f - b.left * sx
        val ty = 1f - b.top * sy
        return SkMatrix.MakeScale(sx, sy).copy(tx = tx, ty = ty)
    }

    private fun rasterise(p: SkPath): BooleanArray {
        val bm = SkBitmap(W, H).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.drawPath(p, SkPaint(SK_ColorBLACK).apply { isAntiAlias = false })
        val out = BooleanArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            out[y * W + x] = bm.getPixel(x, y) == SK_ColorBLACK
        }
        return out
    }

    private fun applyPixelOp(a: BooleanArray, b: BooleanArray, op: SkPathOp): BooleanArray {
        val out = BooleanArray(a.size)
        when (op) {
            SkPathOp.kIntersect -> for (i in a.indices) out[i] = a[i] && b[i]
            SkPathOp.kUnion -> for (i in a.indices) out[i] = a[i] || b[i]
            SkPathOp.kDifference -> for (i in a.indices) out[i] = a[i] && !b[i]
            SkPathOp.kReverseDifference -> for (i in a.indices) out[i] = b[i] && !a[i]
            SkPathOp.kXOR -> for (i in a.indices) out[i] = a[i] xor b[i]
        }
        return out
    }

    private fun asAscii(mask: BooleanArray): String {
        val sb = StringBuilder()
        for (y in 0 until H) {
            for (x in 0 until W) sb.append(if (mask[y * W + x]) '#' else '.')
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun diffAscii(e: BooleanArray, a: BooleanArray): String {
        val sb = StringBuilder()
        for (y in 0 until H) {
            for (x in 0 until W) {
                val i = y * W + x
                sb.append(when {
                    e[i] && a[i] -> '#'
                    !e[i] && !a[i] -> '.'
                    e[i] && !a[i] -> 'E'
                    else -> 'A'
                })
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun twoByTwoErrors(e: BooleanArray, a: BooleanArray): Int {
        var errors = 0
        for (y in 0 until H - 1) {
            for (x in 0 until W - 1) {
                val e00 = e[y * W + x]; val a00 = a[y * W + x]
                if (e00 == a00) continue
                val e10 = e[y * W + x + 1]; val a10 = a[y * W + x + 1]
                val e01 = e[(y + 1) * W + x]; val a01 = a[(y + 1) * W + x]
                val e11 = e[(y + 1) * W + x + 1]; val a11 = a[(y + 1) * W + x + 1]
                if (e10 != a10 && e01 != a01 && e11 != a11) errors++
            }
        }
        return errors
    }
}
