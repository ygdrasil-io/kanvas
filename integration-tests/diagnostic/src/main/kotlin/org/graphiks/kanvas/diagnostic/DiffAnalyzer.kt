package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.SsimBlock
import java.io.File
import kotlin.math.abs

data class SpatialReport(
    val ssim: Double,
    val ssimBlocks: List<SsimBlock>,
    val zones: List<ZoneRegion>,
    val heatmapUrl: String?,
    val perChannelHeatmapUrls: Map<String, String>,
)

object DiffAnalyzer {
    fun analyze(
        actualRgba: ByteArray,
        referenceRgba: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int,
        outputDir: File? = null,
    ): SpatialReport {
        val ssim = ComparisonUtils.computeSSIM(actualRgba, referenceRgba, width, height)
        val ssimBlocks = ComparisonUtils.computeSSIMBlocks(actualRgba, referenceRgba, width, height)

        val baseZones = SpatialZoneClassifier.classify(referenceRgba, width, height)
        val zones = SpatialZoneClassifier.classifyTextZones(baseZones, width, height)

        val zoneRegions = buildZoneRegions(actualRgba, referenceRgba, zones, width, height)

        var heatmapUrl: String? = null
        val perChannelUrls = mutableMapOf<String, String>()
        if (outputDir != null) {
            outputDir.mkdirs()
            saveHeatmap(actualRgba, referenceRgba, width, height, outputDir.resolve("heatmap.png"))
            heatmapUrl = "heatmap.png"
            for ((ch, idx) in listOf("R" to 0, "G" to 1, "B" to 2, "A" to 3)) {
                val file = outputDir.resolve("heatmap_$ch.png")
                saveChannelHeatmap(actualRgba, referenceRgba, width, height, idx, file)
                perChannelUrls[ch] = "heatmap_$ch.png"
            }
        }

        return SpatialReport(ssim, ssimBlocks, zoneRegions, heatmapUrl, perChannelUrls)
    }

    private fun buildZoneRegions(
        actual: ByteArray,
        reference: ByteArray,
        zones: Array<ZoneType>,
        width: Int,
        height: Int,
    ): List<ZoneRegion> {
        val statsByType = mutableMapOf<ZoneType, ZoneStats>()
        val zoneNames = mapOf(
            ZoneType.EDGE to "edge",
            ZoneType.SOLID to "solid",
            ZoneType.GRADIENT to "gradient",
            ZoneType.TEXT to "text",
        )
        for (i in 0 until width * height) {
            val base = i * 4
            val z = zones[i]
            val stats = statsByType.getOrPut(z) { ZoneStats() }
            for (ch in 0..3) {
                val d = abs((actual[base + ch].toInt() and 0xFF) - (reference[base + ch].toInt() and 0xFF))
                stats.maxDelta[ch] = maxOf(stats.maxDelta[ch], d)
                stats.sumDelta[ch] += d.toLong()
            }
            stats.totalPixels++
        }
        val channelNames = listOf("R", "G", "B", "A")
        return statsByType.map { (type, stats) ->
            val avgPerChannel = stats.sumDelta.mapIndexed { i, sum ->
                if (stats.totalPixels > 0) sum.toDouble() / stats.totalPixels else 0.0
            }
            val maxChannelIdx = avgPerChannel.indices.maxByOrNull { avgPerChannel[it] } ?: 0
            val avgMaxDelta = avgPerChannel[maxChannelIdx]
            val severity = when {
                avgMaxDelta > 20.0 -> "high"
                avgMaxDelta > 5.0 -> "medium"
                else -> "low"
            }
            ZoneRegion(
                label = zoneNames[type] ?: "unknown",
                bounds = ZoneBounds(0, 0, width, height),
                type = type,
                dominantChannel = channelNames[maxChannelIdx],
                severity = severity,
                avgDelta = avgMaxDelta,
            )
        }
    }

    private fun saveHeatmap(actual: ByteArray, reference: ByteArray, width: Int, height: Int, file: File) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val base = i * 4
            val maxDelta = (0..3).maxOf {
                abs((actual[base + it].toInt() and 0xFF) - (reference[base + it].toInt() and 0xFF))
            }
            val intensity = (maxDelta * 4).coerceIn(0, 255)
            val (r, g, b) = when {
                intensity < 64 -> Triple(0, intensity * 4, 0)
                intensity < 128 -> Triple((intensity - 64) * 4, 255, 0)
                intensity < 192 -> Triple(255, 255 - (intensity - 128) * 4, 0)
                else -> Triple(255, 0, (intensity - 192) * 4)
            }
            rgba[base] = r.toByte()
            rgba[base + 1] = g.toByte()
            rgba[base + 2] = b.toByte()
            rgba[base + 3] = if (maxDelta > 0) 255.toByte() else 0.toByte()
        }
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, file)
    }

    private fun saveChannelHeatmap(actual: ByteArray, reference: ByteArray, width: Int, height: Int, channel: Int, file: File) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val base = i * 4
            val delta = abs((actual[base + channel].toInt() and 0xFF) - (reference[base + channel].toInt() and 0xFF))
            val intensity = (delta * 4).coerceIn(0, 255)
            rgba[base] = if (channel == 0) intensity.toByte() else 0
            rgba[base + 1] = if (channel == 1) intensity.toByte() else 0
            rgba[base + 2] = if (channel == 2) intensity.toByte() else 0
            rgba[base + 3] = if (delta > 0) 255.toByte() else 0
        }
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, file)
    }

    private class ZoneStats {
        val maxDelta = IntArray(4) { 0 }
        val sumDelta = LongArray(4) { 0L }
        var totalPixels = 0
    }
}
