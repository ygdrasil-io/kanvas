package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.types.Rect
import java.io.File
import kotlin.math.max

/**
 * Per-operation replay trace: total number of display operations, individual op
 * entries with contribution scores, and indices of suspect operations.
 */
data class OpTrace(
    val totalOps: Int,
    val ops: List<OpTraceEntry>,
    val suspectOps: List<Int>,
)

/**
 * A single operation's diagnostic record: operation type, how much pixel
 * divergence it caused (`pixelContribution` percentage), whether it is
 * suspicious (>5% contribution), and URLs to before/after/delta PNG images.
 */
data class OpTraceEntry(
    val index: Int,
    val type: String,
    val pixelContribution: Double,
    val isSuspect: Boolean,
    val beforeUrl: String?,
    val afterUrl: String?,
    val deltaUrl: String?,
)

/**
 * Layer 2 diagnostic: replays drawing operations incrementally onto fresh
 * surfaces, comparing each partial result against the reference image. Identifies
 * which specific draw calls caused the most pixel divergence. Uses sequential
 * replay for pictures with <=50 ops; falls back to checkpoint-based binary search
 * for larger pictures.
 */
object OpInspector {
    fun inspect(
        ops: List<DisplayOp>,
        referenceRgba: ByteArray,
        gmWidth: Int,
        gmHeight: Int,
        tolerance: Int,
        outputDir: File,
    ): OpTrace {
        if (ops.isEmpty()) return OpTrace(0, emptyList(), emptyList())

        val n = ops.size
        val entries = mutableListOf<OpTraceEntry>()

        if (n <= 50) {
            var prevSimilarity = 100.0
            for (i in 1..n) {
                val partialRgba = renderPartial(ops, i, gmWidth, gmHeight)
                val similarity = comparePartial(partialRgba, referenceRgba, gmWidth, gmHeight, tolerance)
                val contribution = max(0.0, prevSimilarity - similarity)
                prevSimilarity = similarity

                val suspect = contribution > 5.0
                var beforeUrl: String? = null
                var afterUrl: String? = null
                var deltaUrl: String? = null

                if (suspect) {
                    val beforeRgba = renderPartial(ops, i - 1, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(beforeRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${i - 1}_before.png"))
                    ComparisonUtils.saveRgbaAsPng(partialRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${i}_after.png"))
                    val delta = buildDelta(beforeRgba, partialRgba, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(delta, gmWidth, gmHeight,
                        outputDir.resolve("op_${i}_diff.png"))
                    beforeUrl = "op_${i - 1}_before.png"
                    afterUrl = "op_${i}_after.png"
                    deltaUrl = "op_${i}_diff.png"
                }

                val opType = opTypeName(ops[i - 1])

                entries.add(OpTraceEntry(
                    index = i - 1,
                    type = opType,
                    pixelContribution = contribution,
                    isSuspect = suspect,
                    beforeUrl = beforeUrl,
                    afterUrl = afterUrl,
                    deltaUrl = deltaUrl,
                ))
            }
        } else {
            val checkpoints = listOf(n / 2, n).filter { it > 0 }
            var prevSimilarity = 100.0
            var prevCount = 0
            for (cp in checkpoints) {
                val partialRgba = renderPartial(ops, cp, gmWidth, gmHeight)
                val similarity = comparePartial(partialRgba, referenceRgba, gmWidth, gmHeight, tolerance)
                val contribution = max(0.0, prevSimilarity - similarity)
                prevSimilarity = similarity

                val suspect = contribution > 5.0
                var beforeUrl: String? = null
                var afterUrl: String? = null
                var deltaUrl: String? = null

                if (suspect && prevCount > 0) {
                    val beforeRgba = renderPartial(ops, prevCount, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(beforeRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${prevCount}_before.png"))
                    ComparisonUtils.saveRgbaAsPng(partialRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${cp}_after.png"))
                    val delta = buildDelta(beforeRgba, partialRgba, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(delta, gmWidth, gmHeight,
                        outputDir.resolve("op_${cp}_diff.png"))
                    beforeUrl = "op_${prevCount}_before.png"
                    afterUrl = "op_${cp}_after.png"
                    deltaUrl = "op_${cp}_diff.png"
                }

                entries.add(OpTraceEntry(
                    index = cp - 1,
                    type = "batch_${prevCount}_to_${cp}",
                    pixelContribution = contribution,
                    isSuspect = suspect,
                    beforeUrl = beforeUrl,
                    afterUrl = afterUrl,
                    deltaUrl = deltaUrl,
                ))
                prevCount = cp
            }
        }

        val suspectOps = entries.filter { it.isSuspect }.map { it.index }
        return OpTrace(n, entries, suspectOps)
    }

    private fun opTypeName(op: DisplayOp): String = when (op) {
        is DisplayOp.DrawRect -> "DrawRect"
        is DisplayOp.DrawRRect -> "DrawRRect"
        is DisplayOp.DrawDRRect -> "DrawDRRect"
        is DisplayOp.DrawPath -> "DrawPath"
        is DisplayOp.DrawPoint -> "DrawPoint"
        is DisplayOp.DrawPoints -> "DrawPoints"
        is DisplayOp.DrawImage -> "DrawImage"
        is DisplayOp.DrawImageNine -> "DrawImageNine"
        is DisplayOp.DrawImageLattice -> "DrawImageLattice"
        is DisplayOp.DrawText -> "DrawText"
        is DisplayOp.DrawPicture -> "DrawPicture"
        is DisplayOp.DrawVertices -> "DrawVertices"
        is DisplayOp.DrawMesh -> "DrawMesh"
        is DisplayOp.DrawAtlas -> "DrawAtlas"
        is DisplayOp.DrawColor -> "DrawColor"
        is DisplayOp.Clear -> "Clear"
        is DisplayOp.SetTransform -> "SetTransform"
        is DisplayOp.SetClip -> "SetClip"
        is DisplayOp.BeginLayer -> "BeginLayer"
        is DisplayOp.EndLayer -> "EndLayer"
        is DisplayOp.Annotation -> "Annotation"
        is DisplayOp.FlushAndSnapshot -> "FlushAndSnapshot"
    }

    private fun renderPartial(ops: List<DisplayOp>, count: Int, width: Int, height: Int): ByteArray {
        val surface = Surface(width, height, config = RenderConfig.DEFAULT)
        val canvas = surface.canvas()
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false))
        for (i in 0 until count.coerceAtMost(ops.size)) {
            replayOp(canvas, ops[i])
        }
        val result = surface.render()
        return result.pixels.map { it.toByte() }.toByteArray()
    }

    private fun replayOp(canvas: Canvas, op: DisplayOp) {
        when (op) {
            is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
            is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
            is DisplayOp.DrawDRRect -> canvas.drawDRRect(op.outer, op.inner, op.paint)
            is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
            is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
            is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
            is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint ?: Paint())
            is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst, op.paint ?: Paint())
            is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(op.image, op.lattice, op.dst, op.paint ?: Paint())
            is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
            is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint ?: Paint())
            is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
            is DisplayOp.DrawMesh -> canvas.drawMesh(op.mesh, op.paint, op.blendMode)
            is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects, op.colors, op.blendMode, op.paint ?: Paint())
            is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
            is DisplayOp.Clear -> canvas.clear(op.color)
            is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
            is DisplayOp.SetClip -> {}
            is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
            is DisplayOp.EndLayer -> canvas.restore()
            is DisplayOp.Annotation -> {}
            is DisplayOp.FlushAndSnapshot -> {}
        }
    }

    private fun comparePartial(
        partialRgba: ByteArray,
        referenceRgba: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int,
    ): Double {
        return ComparisonUtils.compareRgba(partialRgba, referenceRgba, width, height, tolerance, 0.0).similarity
    }

    private fun buildDelta(before: ByteArray, after: ByteArray, width: Int, height: Int): ByteArray {
        val diff = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val b = i * 4
            val maxDelta = (0..3).maxOf {
                kotlin.math.abs((after[b + it].toInt() and 0xFF) - (before[b + it].toInt() and 0xFF))
            }
            if (maxDelta > 0) {
                diff[b] = 255.toByte()
                diff[b + 1] = 0
                diff[b + 2] = 0
                diff[b + 3] = 255.toByte()
            }
        }
        return diff
    }
}
