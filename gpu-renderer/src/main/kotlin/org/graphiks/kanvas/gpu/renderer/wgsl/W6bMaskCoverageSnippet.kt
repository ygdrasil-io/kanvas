package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.render.ir.MaskBlurStyle

/**
 * WGSL fragments for already-frozen W6b mask-coverage work.
 *
 * Callers pass only plan-published target-local origins, resource bindings, and the selected
 * style.  This object chooses neither bounds, resources, operations, nor blend behavior.
 */
internal object W6bMaskCoverageSnippet {
    /** The frozen direct source target is already the clipped solid-rect coverage domain. */
    fun solidRectCoverageFragment(): String = """
        @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            return vec4<f32>(1.0);
        }
    """

    fun alphaCoverageFragment(
        inputOriginDeviceXI32: Int,
        inputOriginDeviceYI32: Int,
        outputOriginDeviceXI32: Int,
        outputOriginDeviceYI32: Int,
    ): String = """
        @group(0) @binding(0) var w6b_alpha_source: texture_2d<f32>;
        @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            let input_position = vec2<i32>(position.xy) + vec2<i32>($outputOriginDeviceXI32 - $inputOriginDeviceXI32,
                $outputOriginDeviceYI32 - $inputOriginDeviceYI32);
            let input_extent = vec2<i32>(textureDimensions(w6b_alpha_source));
            if (input_position.x < 0 || input_position.y < 0 || input_position.x >= input_extent.x || input_position.y >= input_extent.y) {
                return vec4<f32>(0.0);
            }
            return vec4<f32>(textureLoad(w6b_alpha_source, input_position, 0).a);
        }
    """

    fun maskStyleFragment(
        style: MaskBlurStyle,
        blurredOriginDeviceXI32: Int,
        blurredOriginDeviceYI32: Int,
        outputOriginDeviceXI32: Int,
        outputOriginDeviceYI32: Int,
        originalOriginDeviceXI32: Int?,
        originalOriginDeviceYI32: Int?,
    ): String {
        val originalDeclaration = if (style == MaskBlurStyle.NORMAL) "" else
            "@group(0) @binding(1) var w6b_original_coverage: texture_2d<f32>;"
        val originalSample = if (style == MaskBlurStyle.NORMAL) "0.0" else """
            w6b_sample_coverage(w6b_original_coverage, vec2<i32>(position.xy) +
                vec2<i32>($outputOriginDeviceXI32 - ${requireNotNull(originalOriginDeviceXI32)},
                    $outputOriginDeviceYI32 - ${requireNotNull(originalOriginDeviceYI32)}))
        """.trimIndent()
        val equation = when (style) {
            MaskBlurStyle.NORMAL -> "blurred"
            MaskBlurStyle.SOLID -> "max(original, blurred)"
            MaskBlurStyle.OUTER -> "max(0.0, blurred - original)"
            MaskBlurStyle.INNER -> "min(original, blurred)"
        }
        return """
            @group(0) @binding(0) var w6b_blurred_coverage: texture_2d<f32>;
            $originalDeclaration
            fn w6b_sample_coverage(source: texture_2d<f32>, coordinate: vec2<i32>) -> f32 {
                let extent = vec2<i32>(textureDimensions(source));
                if (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) { return 0.0; }
                return textureLoad(source, coordinate, 0).a;
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let blurred = w6b_sample_coverage(w6b_blurred_coverage, vec2<i32>(position.xy) +
                    vec2<i32>($outputOriginDeviceXI32 - $blurredOriginDeviceXI32,
                        $outputOriginDeviceYI32 - $blurredOriginDeviceYI32));
                let original = $originalSample;
                return vec4<f32>($equation);
            }
        """
    }

    fun maskedMaterialSourceFragment(
        sourceOriginDeviceXI32: Int,
        sourceOriginDeviceYI32: Int,
        coverageOriginDeviceXI32: Int,
        coverageOriginDeviceYI32: Int,
        outputOriginDeviceXI32: Int,
        outputOriginDeviceYI32: Int,
        replacesSourceAlpha: Boolean,
    ): String = """
        @group(0) @binding(0) var w6b_material_source: texture_2d<f32>;
        @group(0) @binding(1) var w6b_material_coverage: texture_2d<f32>;
        @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            let local = vec2<i32>(position.xy);
            let source_position = local + vec2<i32>($outputOriginDeviceXI32 - $sourceOriginDeviceXI32,
                $outputOriginDeviceYI32 - $sourceOriginDeviceYI32);
            let coverage_position = local + vec2<i32>($outputOriginDeviceXI32 - $coverageOriginDeviceXI32,
                $outputOriginDeviceYI32 - $coverageOriginDeviceYI32);
            let source_extent = vec2<i32>(textureDimensions(w6b_material_source));
            let coverage_extent = vec2<i32>(textureDimensions(w6b_material_coverage));
            if (coverage_position.x < 0 || coverage_position.y < 0 || coverage_position.x >= coverage_extent.x || coverage_position.y >= coverage_extent.y) {
                return vec4<f32>(0.0);
            }
            // A sealed Picture source is transparent black outside its exact RGBA extent,
            // yet a blurred parent mask may still carry alpha into that fringe.  Do not
            // discard that alpha when the frozen coverage operation replaces source alpha.
            var source = vec4<f32>(0.0);
            if (source_position.x >= 0 && source_position.y >= 0 &&
                source_position.x < source_extent.x && source_position.y < source_extent.y) {
                source = textureLoad(w6b_material_source, source_position, 0);
            }
            let mask = textureLoad(w6b_material_coverage, coverage_position, 0).a;
            let straight = select(vec3<f32>(0.0), source.rgb / source.a, source.a > 0.0);
            return ${if (replacesSourceAlpha) "vec4<f32>(straight * mask, mask)" else "source * mask"};
        }
    """
}
