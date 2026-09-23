package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.math.roundToInt
import org.graphiks.kanvas.gpu.plan.W6dSamplingProgramV1
import org.graphiks.kanvas.gpu.plan.W6dSamplingProgramIdV1

/** Deterministic backend translation of a selected recipe; never consumes semantic operations. */
internal object GPUW6dAdvancedSamplingPass {
    fun fragment(program: W6dSamplingProgramV1): String = when (program.programId) {
        W6dSamplingProgramIdV1.MATRIX_CLAMP_RGBA8_V1,
        W6dSamplingProgramIdV1.MATRIX_REPEAT_RGBA8_V1,
        W6dSamplingProgramIdV1.MATRIX_MIRROR_RGBA8_V1,
        W6dSamplingProgramIdV1.MATRIX_DECAL_RGBA8_V1 -> matrixConvolutionFragment(program as W6dSamplingProgramV1.Convolution)
        W6dSamplingProgramIdV1.DISPLACEMENT_NEAREST_CLAMP_RGBA8_V1 -> displacementFragment(program as W6dSamplingProgramV1.Displacement)
        W6dSamplingProgramIdV1.MAGNIFIER_NEAREST_CLAMP_RGBA8_V1 -> magnifierFragment(program as W6dSamplingProgramV1.Magnifier)
    }

    private fun matrixConvolutionFragment(program: W6dSamplingProgramV1.Convolution): String {
        val terms = buildString {
            for (tap in program.taps()) {
                append("value += w6d_matrix_sample(vec2<i32>(round(vec2<f32>(base) + vec2<f32>(${tap.offsetXF64}f, ${tap.offsetYF64}f)))) * ${tap.weightF32}f;\n")
            }
        }
        val sampler = when (program.programId) {
            W6dSamplingProgramIdV1.MATRIX_CLAMP_RGBA8_V1 -> "return textureLoad(w6d_matrix_source, clamp(coord, vec2<i32>(0), extent - vec2<i32>(1)), 0);"
            W6dSamplingProgramIdV1.MATRIX_REPEAT_RGBA8_V1 -> "return textureLoad(w6d_matrix_source, vec2<i32>((coord.x % extent.x + extent.x) % extent.x, (coord.y % extent.y + extent.y) % extent.y), 0);"
            W6dSamplingProgramIdV1.MATRIX_MIRROR_RGBA8_V1 -> "let period = extent * 2; let p = vec2<i32>((coord.x % period.x + period.x) % period.x, (coord.y % period.y + period.y) % period.y); return textureLoad(w6d_matrix_source, min(p, period - vec2<i32>(1) - p), 0);"
            W6dSamplingProgramIdV1.MATRIX_DECAL_RGBA8_V1 -> "if (any(coord < vec2<i32>(0)) || any(coord >= extent)) { return vec4<f32>(0.0); } return textureLoad(w6d_matrix_source, coord, 0);"
            else -> error("Unfrozen W6d matrix tile mode")
        }
        return """
            @group(0) @binding(0) var w6d_matrix_source: texture_2d<f32>;
            fn w6d_matrix_sample(coord: vec2<i32>) -> vec4<f32> {
                let extent = vec2<i32>(textureDimensions(w6d_matrix_source));
                $sampler
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let base = vec2<i32>(position.xy);
                var value = vec4<f32>(0.0);
                $terms
                let rgb = clamp(value.rgb * ${program.gainF32}f + vec3<f32>(${program.normalizedBiasF32}f), vec3<f32>(0.0), vec3<f32>(1.0));
                let alpha = ${if (program.convolveAlpha) "clamp(value.a * ${program.gainF32}f + ${program.normalizedBiasF32}f, 0.0, 1.0)" else "w6d_matrix_sample(base).a"};
                return vec4<f32>(rgb, alpha);
            }
        """
    }

    private fun displacementFragment(program: W6dSamplingProgramV1.Displacement): String {
        return """
            @group(0) @binding(0) var w6d_displacement: texture_2d<f32>;
            @group(0) @binding(1) var w6d_source: texture_2d<f32>;
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let base = vec2<i32>(position.xy);
                let map_extent = vec2<i32>(textureDimensions(w6d_displacement));
                let map = textureLoad(w6d_displacement, clamp(base, vec2<i32>(0), map_extent - vec2<i32>(1)), 0);
                let source_extent = vec2<i32>(textureDimensions(w6d_source));
                let coordinate = vec2<i32>(round(vec2<f32>(base) + vec2<f32>(map[${program.xComponentI32}], map[${program.yComponentI32}]) * ${program.scaleF32}f));
                return textureLoad(w6d_source, clamp(coordinate, vec2<i32>(0), source_extent - vec2<i32>(1)), 0);
            }
        """
    }

    private fun magnifierFragment(program: W6dSamplingProgramV1.Magnifier): String {
        return """
            @group(0) @binding(0) var w6d_magnifier_source: texture_2d<f32>;
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let point = position.xy;
                let inside = point.x >= ${program.innerLeftF64}f && point.x <= ${program.innerRightF64}f &&
                    point.y >= ${program.innerTopF64}f && point.y <= ${program.innerBottomF64}f;
                let sampled = select(point, vec2<f32>(${program.centerXF64}f, ${program.centerYF64}f) + (point - vec2<f32>(${program.centerXF64}f, ${program.centerYF64}f)) / ${program.zoomF32}f, inside);
                let extent = vec2<i32>(textureDimensions(w6d_magnifier_source));
                return textureLoad(w6d_magnifier_source, clamp(vec2<i32>(round(sampled - vec2<f32>(0.5))), vec2<i32>(0), extent - vec2<i32>(1)), 0);
            }
        """
    }
}

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
    val diagnostics: List<GPUFilterDiagnostic> = emptyList(),
    val pixelCount: Int = 0,
)

/** Applies a displacement map filter: offsets source sampling by per-pixel displacement values
 *  read from a displacement map texture at a selected color channel. */
class GPUDisplacementMap {

    /** Validates the displacement sampling plan and returns acceptance statistics. */
    fun accept(samplingPlan: GPUDisplacementSamplingPlan, supportedTileModes: Set<GPUTileMode> = GPUTileMode.entries.toSet()): GPUDisplacementMapResult {
        val diagnostics = mutableListOf<GPUFilterDiagnostic>()

        if (samplingPlan.displacementBinding.width <= 0 ||
            samplingPlan.displacementBinding.height <= 0
        ) {
            diagnostics.add(
                GPUFilterDiagnostic(
                    code = "unsupported.filter.displacement_missing_texture",
                    message = "Displacement map texture is missing or has invalid dimensions.",
                    terminal = true,
                ),
            )
            return GPUDisplacementMapResult(accepted = false, diagnostics = diagnostics)
        }

        if (samplingPlan.sourceBinding.width <= 0 ||
            samplingPlan.sourceBinding.height <= 0
        ) {
            diagnostics.add(
                GPUFilterDiagnostic(
                    code = "unsupported.filter.displacement_source_format",
                    message = "Source texture has invalid dimensions: ${samplingPlan.sourceBinding.width}x${samplingPlan.sourceBinding.height}.",
                    terminal = true,
                ),
            )
            return GPUDisplacementMapResult(accepted = false, diagnostics = diagnostics)
        }

        if (samplingPlan.plan.tileMode !in supportedTileModes) {
            diagnostics.add(
                GPUFilterDiagnostic(
                    code = "unsupported.filter.displacement_tile_mode_unsupported",
                    message = "Tile mode ${samplingPlan.plan.tileMode} is not supported by the adapter.",
                    terminal = true,
                ),
            )
            return GPUDisplacementMapResult(accepted = false, diagnostics = diagnostics)
        }

        if (samplingPlan.plan.scaleX == 0f && samplingPlan.plan.scaleY == 0f) {
            return GPUDisplacementMapResult(
                accepted = true,
                diagnostics = listOf(
                    GPUFilterDiagnostic(
                        code = "elision.identity_pass",
                        message = "Displacement scale is zero; elision identity pass.",
                        terminal = false,
                    ),
                ),
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
