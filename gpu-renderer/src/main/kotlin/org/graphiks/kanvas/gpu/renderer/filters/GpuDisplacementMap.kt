package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.math.roundToInt

/** Color channel selector for displacement map sampling. */
enum class GPUColorChannel { R, G, B, A }

/** Tile mode for out-of-bounds displaced coordinate sampling. */
enum class GPUTileMode { Clamp, Repeat, Mirror, Decal }

/** Descriptor for a texture binding used in displacement sampling. */
data class GPUTextureBinding(
    val textureId: String,
    val width: Int,
    val height: Int,
    val format: String = "rgba8",
)

/** Parameters for a displacement map filter pass. */
data class GPUDisplacementMapPlan(
    val channelX: GPUColorChannel,
    val channelY: GPUColorChannel,
    val scaleX: Float,
    val scaleY: Float,
    val tileMode: GPUTileMode,
)

/** Complete plan for displacement sampling with source, displacement, and target bindings. */
data class GPUDisplacementSamplingPlan(
    val sourceBinding: GPUTextureBinding,
    val displacementBinding: GPUTextureBinding,
    val targetBinding: GPUTextureBinding,
    val plan: GPUDisplacementMapPlan,
)

/** Result of validating a displacement map filter plan. */
data class GPUDisplacementMapResult(
    val accepted: Boolean,
    val diagnostics: List<String> = emptyList(),
    val pixelCount: Int = 0,
)

/** Applies a displacement map filter: offsets source sampling by per-pixel displacement values
 *  read from a displacement map texture at a selected color channel. */
class GpuDisplacementMap {

    /** Validates the displacement sampling plan and returns acceptance statistics. */
    fun accept(samplingPlan: GPUDisplacementSamplingPlan): GPUDisplacementMapResult {
        val diagnostics = mutableListOf<String>()

        if (samplingPlan.displacementBinding.width <= 0 ||
            samplingPlan.displacementBinding.height <= 0
        ) {
            diagnostics.add("unsupported.filter.displacement_missing_texture")
            return GPUDisplacementMapResult(accepted = false, diagnostics = diagnostics)
        }

        if (samplingPlan.sourceBinding.width <= 0 ||
            samplingPlan.sourceBinding.height <= 0
        ) {
            diagnostics.add("unsupported.filter.displacement_source_format")
            return GPUDisplacementMapResult(accepted = false, diagnostics = diagnostics)
        }

        if (samplingPlan.plan.scaleX == 0f && samplingPlan.plan.scaleY == 0f) {
            return GPUDisplacementMapResult(
                accepted = true,
                diagnostics = listOf("elision.identity_pass"),
                pixelCount = 0,
            )
        }

        val pixelCount = samplingPlan.targetBinding.width *
            samplingPlan.targetBinding.height
        return GPUDisplacementMapResult(accepted = true, pixelCount = pixelCount)
    }

    /** Executes a displacement map filter on CPU as a reference oracle.
     *  For each output pixel at (x, y):
     *    displacement = sampleNearest(displacementMap, x, y)
     *    dx = displacement[channelX] / 255.0 * scaleX
     *    dy = displacement[channelY] / 255.0 * scaleY
     *    output[x, y] = sampleNearest(source, x + dx, y + dy, tileMode)
     */
    fun execute(
        sourcePixels: IntArray,
        displacementPixels: IntArray,
        width: Int,
        height: Int,
        plan: GPUDisplacementMapPlan,
    ): IntArray {
        val output = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val dispPixel = displacementPixels[idx]

                val cx = extractChannel(dispPixel, plan.channelX)
                val cy = extractChannel(dispPixel, plan.channelY)

                val srcX = x + cx / 255.0f * plan.scaleX
                val srcY = y + cy / 255.0f * plan.scaleY

                output[idx] = sampleNearest(sourcePixels, srcX, srcY, width, height, plan.tileMode)
            }
        }

        return output
    }

    private fun extractChannel(pixel: Int, channel: GPUColorChannel): Float =
        when (channel) {
            GPUColorChannel.R -> ((pixel shr 16) and 0xFF).toFloat()
            GPUColorChannel.G -> ((pixel shr 8) and 0xFF).toFloat()
            GPUColorChannel.B -> (pixel and 0xFF).toFloat()
            GPUColorChannel.A -> ((pixel ushr 24) and 0xFF).toFloat()
        }

    private fun sampleNearest(
        pixels: IntArray,
        srcX: Float,
        srcY: Float,
        width: Int,
        height: Int,
        tileMode: GPUTileMode,
    ): Int {
        val ix = srcX.roundToInt()
        val iy = srcY.roundToInt()

        return when (tileMode) {
            GPUTileMode.Clamp -> {
                val cx = ix.coerceIn(0, width - 1)
                val cy = iy.coerceIn(0, height - 1)
                pixels[cy * width + cx]
            }
            GPUTileMode.Repeat -> {
                val rx = ((ix % width) + width) % width
                val ry = ((iy % height) + height) % height
                pixels[ry * width + rx]
            }
            GPUTileMode.Mirror -> {
                val rx = mirrorCoord(ix, width)
                val ry = mirrorCoord(iy, height)
                pixels[ry * width + rx]
            }
            GPUTileMode.Decal -> {
                if (ix < 0 || ix >= width || iy < 0 || iy >= height) {
                    0
                } else {
                    pixels[iy * width + ix]
                }
            }
        }
    }

    private fun mirrorCoord(coord: Int, size: Int): Int {
        if (size <= 1) return 0
        val period = 2 * (size - 1)
        val t = ((coord % period) + period) % period
        return if (t < size) t else period - t
    }
}
