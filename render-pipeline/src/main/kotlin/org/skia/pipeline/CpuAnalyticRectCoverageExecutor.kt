package org.skia.pipeline

import kotlin.math.ceil
import kotlin.math.floor

data class CpuAnalyticRectCoverageMetrics(
    val touchedPixels: Int,
    val scalarVectorStatus: String,
    val kernelId: String,
)

object CpuAnalyticRectCoverageExecutor {
    const val KERNEL_ID: String = "cpu.scalar.analytic_rect_coverage"

    fun execute(
        coverage: CoverageModel.AnalyticRect,
        clip: IntRect,
        visit: (x: Int, y: Int, coverage: Float) -> Unit,
    ): CpuAnalyticRectCoverageMetrics {
        val bounds = coverage.bounds
        if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) {
            return metrics(coverage.aa, touchedPixels = 0)
        }

        var touchedPixels = 0
        if (!coverage.aa) {
            val l = pixelEdge(bounds.left).coerceAtLeast(clip.left)
            val t = pixelEdge(bounds.top).coerceAtLeast(clip.top)
            val r = pixelEdge(bounds.right).coerceAtMost(clip.right)
            val b = pixelEdge(bounds.bottom).coerceAtMost(clip.bottom)
            for (y in t until b) {
                for (x in l until r) {
                    visit(x, y, 1f)
                    touchedPixels++
                }
            }
            return metrics(coverage.aa, touchedPixels)
        }

        val ix0 = floor(bounds.left).toInt().coerceAtLeast(clip.left)
        val iy0 = floor(bounds.top).toInt().coerceAtLeast(clip.top)
        val ix1 = ceil(bounds.right).toInt().coerceAtMost(clip.right)
        val iy1 = ceil(bounds.bottom).toInt().coerceAtMost(clip.bottom)
        for (y in iy0 until iy1) {
            val cy = covAxis(bounds.top, bounds.bottom, y)
            if (cy <= 0f) continue
            for (x in ix0 until ix1) {
                val cx = covAxis(bounds.left, bounds.right, x)
                if (cx <= 0f) continue
                visit(x, y, cx * cy)
                touchedPixels++
            }
        }
        return metrics(coverage.aa, touchedPixels)
    }

    fun countTouchedPixels(coverage: CoverageModel.AnalyticRect, clip: IntRect): Int =
        execute(coverage, clip) { _, _, _ -> }.touchedPixels

    private fun metrics(aa: Boolean, touchedPixels: Int): CpuAnalyticRectCoverageMetrics =
        CpuAnalyticRectCoverageMetrics(
            touchedPixels = touchedPixels,
            scalarVectorStatus = if (aa) "scalar-analytic-rect-aa" else "scalar-analytic-rect",
            kernelId = KERNEL_ID,
        )

    private fun pixelEdge(c: Float): Int = floor(c + 0.5f).toInt()

    private fun covAxis(lo: Float, hi: Float, pixel: Int): Float {
        val cov = minOf(hi, (pixel + 1).toFloat()) - maxOf(lo, pixel.toFloat())
        return when {
            cov >= 1f -> 1f
            cov <= 0f -> 0f
            else -> cov
        }
    }
}
