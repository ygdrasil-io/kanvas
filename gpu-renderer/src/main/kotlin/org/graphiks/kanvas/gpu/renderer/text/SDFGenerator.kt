package org.graphiks.kanvas.gpu.renderer.text

/** Result of generating a signed-distance field from A8 glyph data. */
data class SDFGenerationResult(
    val width: Int,
    val height: Int,
    val sdfBytes: ByteArray,
    val radius: Float,
    val accepted: Boolean,
    val diagnostic: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SDFGenerationResult) return false
        return width == other.width &&
            height == other.height &&
            sdfBytes.contentEquals(other.sdfBytes) &&
            radius == other.radius &&
            accepted == other.accepted &&
            diagnostic == other.diagnostic
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + sdfBytes.contentHashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + accepted.hashCode()
        result = 31 * result + (diagnostic?.hashCode() ?: 0)
        return result
    }
}

/** Generates signed-distance fields from A8 rasterized glyphs. */
class SDFGenerator {
    companion object {
        const val SDF_RADIUS: Float = 8f
        const val SDF_THRESHOLD: Float = 0.5f
        const val SDF_SMOOTHING: Float = 0.125f
        const val nonClaimLine: String =
            "nonclaim:no-hybrid-sdf no-cpu-sdf-rasterizer no-distortion-mesh no-color-bitmap-sdf"
    }

    /** Generates an SDF texture from A8 glyph pixels using a brute-force distance transform. */
    fun generateFromA8(a8Pixels: ByteArray, width: Int, height: Int): SDFGenerationResult {
        if (width <= 0 || height <= 0) {
            return SDFGenerationResult(
                width = 0,
                height = 0,
                sdfBytes = ByteArray(0),
                radius = SDF_RADIUS,
                accepted = false,
                diagnostic = "SDF generation requires positive dimensions",
            )
        }
        if (a8Pixels.size != width * height) {
            return SDFGenerationResult(
                width = 0,
                height = 0,
                sdfBytes = ByteArray(0),
                radius = SDF_RADIUS,
                accepted = false,
                diagnostic = "A8 pixel buffer size mismatch",
            )
        }

        val sdfBytes = ByteArray(width * height)
        val radius = SDF_RADIUS.toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = (a8Pixels[y * width + x].toInt() and 0xFF) / 255f
                var minDist = radius.toFloat()

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val sampleX = (x + dx).coerceIn(0, width - 1)
                        val sampleY = (y + dy).coerceIn(0, height - 1)
                        val sample = (a8Pixels[sampleY * width + sampleX].toInt() and 0xFF) / 255f
                        val signedDist = (sample - SDF_THRESHOLD) * 2f
                        if (signedDist == 0f) continue
                        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                        val field = if (signedDist > 0f) dist else -dist
                        if (kotlin.math.abs(field) < kotlin.math.abs(minDist)) {
                            minDist = field
                        }
                    }
                }

                val normalized = ((minDist / radius + 1f) * 0.5f * 255f).toInt().coerceIn(0, 255)
                sdfBytes[y * width + x] = normalized.toByte()
            }
        }

        return SDFGenerationResult(
            width = width,
            height = height,
            sdfBytes = sdfBytes,
            radius = SDF_RADIUS,
            accepted = true,
        )
    }
}
