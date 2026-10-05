package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.BlendFormulaProgramV1
import org.graphiks.kanvas.gpu.plan.FilterInputSamplingV1
import org.graphiks.kanvas.gpu.plan.FilterPassOperationV1
import org.graphiks.kanvas.gpu.plan.W6FilterMergeRecipeV1
import org.graphiks.kanvas.gpu.plan.W6FilterBlendRecipeV1
import org.graphiks.kanvas.gpu.plan.W6FilterBlendInputRecipeV1

/** Native-only consumer of W6c's already ordered, already selected multi-input payloads. */
internal object GPUW6cMultiInputPass {
    /** Merge is mechanically translated from the frozen positional recipe, never FilterPass. */
    internal fun mergeFragment(recipe: W6FilterMergeRecipeV1): String = buildString {
        recipe.inputs().forEachIndexed { indexI32, input ->
            val offset = input.copyOutputToInputOffsetTargetLocalI32()
            append("@group(0) @binding($indexI32) var w6c_input_$indexI32: texture_2d<f32>;\n")
            append("fn w6c_sample_$indexI32(position: vec4<f32>) -> vec4<f32> {\n")
            append("let coordinate = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});\n")
            append("let extent = vec2<i32>(textureDimensions(w6c_input_$indexI32));\n")
            append("if (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) { return vec4<f32>(0.0); }\n")
            append("return textureLoad(w6c_input_$indexI32, coordinate, 0);\n}\n")
        }
        append("@fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {\nvar result = vec4<f32>(0.0);\n")
        recipe.inputs().indices.forEach { indexI32 -> append("let input_$indexI32 = w6c_sample_$indexI32(position);\nresult = input_$indexI32 + result * (1.0 - input_$indexI32.a);\n") }
        append("return result;\n}")
    }

    internal fun blendFragment(recipe: W6FilterBlendRecipeV1): String = buildString {
        appendBlendDeclarations(listOf(recipe.background(), recipe.foreground()))
        append(recipe.blendFormulaWgsl)
        append("@fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {\nlet background = w6c_sample_0(position);\nlet foreground = w6c_sample_1(position);\nreturn w6d2_frozen_blend(foreground, background);\n}")
    }
    internal fun legacyBlendFragment(operation: FilterPassOperationV1.Blend): String = buildString {
        appendDeclarations(listOf(operation.backgroundSampling(), operation.foregroundSampling()))
        val formula = requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(
            operation.blend.frozenModeLabel(), "w6c_frozen_blend",
        )) { "W6c BlendPlan has no sealed W5 blend formula." }
        append(formula)
        append("@fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {\n")
        append("let background = w6c_sample_0(position);\n")
        append("let foreground = w6c_sample_1(position);\n")
        append("return w6c_frozen_blend(foreground, background);\n}")
    }

    private fun StringBuilder.appendDeclarations(samplings: List<FilterInputSamplingV1>) {
        samplings.forEachIndexed { indexI32, sampling ->
            val offset = sampling.copyOutputToInputOffsetTargetLocalI32()
            append("@group(0) @binding($indexI32) var w6c_input_$indexI32: texture_2d<f32>;\n")
            append("fn w6c_sample_$indexI32(position: vec4<f32>) -> vec4<f32> {\n")
            append("let coordinate = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});\n")
            append("let extent = vec2<i32>(textureDimensions(w6c_input_$indexI32));\n")
            append("if (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) { return vec4<f32>(0.0); }\n")
            append("return textureLoad(w6c_input_$indexI32, coordinate, 0);\n}\n")
        }
    }
    private fun StringBuilder.appendBlendDeclarations(samplings: List<W6FilterBlendInputRecipeV1>) {
        samplings.forEachIndexed { indexI32, sampling ->
            val offset = sampling.copyOutputToInputOffsetTargetLocalI32()
            append("@group(0) @binding($indexI32) var w6c_input_$indexI32: texture_2d<f32>;\nfn w6c_sample_$indexI32(position: vec4<f32>) -> vec4<f32> {\nlet coordinate = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});\nlet extent = vec2<i32>(textureDimensions(w6c_input_$indexI32));\nif (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) { return vec4<f32>(0.0); }\nreturn textureLoad(w6c_input_$indexI32, coordinate, 0);\n}\n")
        }
    }

    /** Reads only the plan-selected W5 payload; no public mode is reconsidered in native code. */
    private fun BlendPlan.frozenModeLabel(): String = when (this) {
        BlendPlan.LegacySrcOverV1 -> "src_over"
        BlendPlan.NoOpV1 -> "dst"
        is BlendPlan.FixedFunctionV1 -> mode.name.lowercase()
        is BlendPlan.DestinationReadV1 -> mode.name.lowercase()
    }
}
