package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.gpu.plan.FilterAxisV1
import org.graphiks.kanvas.render.ir.TileMode

/**
 * Emits the one fullscreen fragment program for an already frozen W6b blur pass.
 *
 * The caller supplies only immutable plan facts: axis, sigma, tile mode and the
 * output-to-input offset and input-local known rectangle.  Kernel selection, bounds expansion and pass ordering
 * remain exclusively in the GPU plan.
 */
internal object W6bSeparableBlurSnippet {
    fun fragment(
        axis: FilterAxisV1,
        sigmaF32: Float,
        tileMode: TileMode,
        outputToInputOffsetTargetLocalXI32: Int,
        outputToInputOffsetTargetLocalYI32: Int,
        knownInputLeftTargetLocalI32: Int,
        knownInputTopTargetLocalI32: Int,
        knownInputRightTargetLocalI32: Int,
        knownInputBottomTargetLocalI32: Int,
        transparentOutsideSource: Boolean = false,
    ): String {
        require(sigmaF32.isFinite() && sigmaF32 >= 0f)
        val axisX = if (axis == FilterAxisV1.X) "1" else "0"
        val axisY = if (axis == FilterAxisV1.Y) "1" else "0"
        val tileAddress = if (transparentOutsideSource) "coordinate" else when (tileMode) {
            TileMode.CLAMP -> "clamp(coordinate, lower, lower + extent - 1)"
            TileMode.REPEAT -> "lower + (((coordinate - lower) % extent) + extent) % extent"
            TileMode.MIRROR -> "lower + w6b_mirror(coordinate - lower, extent)"
            TileMode.DECAL -> "coordinate"
        }
        val decal = transparentOutsideSource || tileMode == TileMode.DECAL
        return """
            @group(0) @binding(0) var w6b_input: texture_2d<f32>;
            fn w6b_mirror(coordinate: i32, extent: i32) -> i32 {
                let period = 2 * extent;
                let folded = ((coordinate % period) + period) % period;
                return min(folded, period - 1 - folded);
            }
            fn w6b_address(coordinate: i32, lower: i32, extent: i32) -> i32 { return $tileAddress; }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let output_to_input = vec2<i32>(position.xy) + vec2<i32>($outputToInputOffsetTargetLocalXI32, $outputToInputOffsetTargetLocalYI32);
                let known_lower = vec2<i32>($knownInputLeftTargetLocalI32, $knownInputTopTargetLocalI32);
                let known_extent = vec2<i32>(${knownInputRightTargetLocalI32 - knownInputLeftTargetLocalI32}, ${knownInputBottomTargetLocalI32 - knownInputTopTargetLocalI32});
                let sigma = ${sigmaF32}f;
                if (sigma == 0.0) {
                    ${if (decal) "if (output_to_input.x < known_lower.x || output_to_input.x >= known_lower.x + known_extent.x || output_to_input.y < known_lower.y || output_to_input.y >= known_lower.y + known_extent.y) { return vec4<f32>(0.0); }" else ""}
                    let input_position = vec2<i32>(w6b_address(output_to_input.x, known_lower.x, known_extent.x), w6b_address(output_to_input.y, known_lower.y, known_extent.y));
                    let input_extent = vec2<i32>(textureDimensions(w6b_input));
                    if (input_position.x < 0 || input_position.y < 0 || input_position.x >= input_extent.x || input_position.y >= input_extent.y) { return vec4<f32>(0.0); }
                    return textureLoad(w6b_input, input_position, 0);
                }
                let radius = max(1, i32(ceil(3.0 * sigma)));
                var total = vec4<f32>(0.0);
                var weight_sum = 0.0;
                for (var tap = -radius; tap <= radius; tap = tap + 1) {
                    let coordinate = output_to_input + vec2<i32>(tap * $axisX, tap * $axisY);
                    let distance = f32(tap);
                    let weight = exp(-(distance * distance) / (2.0 * sigma * sigma));
                    weight_sum = weight_sum + weight;
                    ${if (decal) "if (coordinate.x < known_lower.x || coordinate.x >= known_lower.x + known_extent.x || coordinate.y < known_lower.y || coordinate.y >= known_lower.y + known_extent.y) { continue; }" else ""}
                    let input_position = vec2<i32>(w6b_address(coordinate.x, known_lower.x, known_extent.x), w6b_address(coordinate.y, known_lower.y, known_extent.y));
                    let input_extent = vec2<i32>(textureDimensions(w6b_input));
                    if (input_position.x < 0 || input_position.y < 0 || input_position.x >= input_extent.x || input_position.y >= input_extent.y) { continue; }
                    total = total + textureLoad(w6b_input, input_position, 0) * weight;
                }
                return total / weight_sum;
            }
        """
    }
}
