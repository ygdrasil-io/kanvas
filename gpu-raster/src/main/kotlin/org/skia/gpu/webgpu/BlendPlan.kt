package org.skia.gpu.webgpu

import org.skia.foundation.SkBlendMode

public data class BlendPlan(
    val mode: SkBlendMode,
    val kind: Kind,
    val reason: String,
) {
    public enum class Kind {
        FixedFunction,
        ShaderLayerComposite,
        RefuseDiagnostic,
    }

    public val isFixedFunction: Boolean
        get() = kind == Kind.FixedFunction

    public val isShaderLayerComposite: Boolean
        get() = kind == Kind.ShaderLayerComposite
}

private val FIXED_FUNCTION_BLEND_MODES: Set<SkBlendMode> = linkedSetOf(
    SkBlendMode.kClear,
    SkBlendMode.kSrc,
    SkBlendMode.kSrcOver,
    SkBlendMode.kDstOver,
    SkBlendMode.kPlus,
)

private val DRAW_SHADER_LAYER_COMPOSITE_BLEND_MODES: Set<SkBlendMode> = linkedSetOf(
    SkBlendMode.kModulate,
    SkBlendMode.kScreen,
    SkBlendMode.kDarken,
    SkBlendMode.kLighten,
    SkBlendMode.kDifference,
    SkBlendMode.kExclusion,
    SkBlendMode.kMultiply,
)

private val LAYER_SHADER_COMPOSITE_BLEND_MODES: Set<SkBlendMode> =
    linkedSetOf(SkBlendMode.kPlus) + DRAW_SHADER_LAYER_COMPOSITE_BLEND_MODES

private val fixedFunctionBlendModeNames: String =
    FIXED_FUNCTION_BLEND_MODES.joinToString(" / ") { it.name }

private val drawShaderLayerCompositeBlendModeNames: String =
    DRAW_SHADER_LAYER_COMPOSITE_BLEND_MODES.joinToString(" / ") { it.name }

private val layerFixedFunctionBlendModeNames: String =
    FIXED_FUNCTION_BLEND_MODES
        .filter { it !in LAYER_SHADER_COMPOSITE_BLEND_MODES }
        .joinToString(" / ") { it.name }

private val layerShaderCompositeBlendModeNames: String =
    LAYER_SHADER_COMPOSITE_BLEND_MODES.joinToString(" / ") { it.name }

public fun selectWebGpuBlendPlan(mode: SkBlendMode): BlendPlan = when (mode) {
    in FIXED_FUNCTION_BLEND_MODES -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.FixedFunction,
        reason = "fixed-function WebGPU blend allowlist accepts $mode",
    )

    in DRAW_SHADER_LAYER_COMPOSITE_BLEND_MODES -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.ShaderLayerComposite,
        reason = "blend mode $mode requires shader/layer composite BlendPlan; " +
            "fixed-function WebGPU path refuses it",
    )

    else -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.RefuseDiagnostic,
        reason = "blend mode $mode is not in the M7 WebGPU BlendPlan allowlist. " +
            "Fixed-function modes: $fixedFunctionBlendModeNames. " +
            "Shader layer-composite modes: $drawShaderLayerCompositeBlendModeNames. " +
            "Other modes need additional shader work and are deferred.",
    )
}

public fun selectLayerCompositeBlendPlan(mode: SkBlendMode): BlendPlan = when {
    mode in LAYER_SHADER_COMPOSITE_BLEND_MODES -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.ShaderLayerComposite,
        reason = "blend mode $mode requires shader/layer composite BlendPlan for saveLayer composition",
    )

    mode in FIXED_FUNCTION_BLEND_MODES -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.FixedFunction,
        reason = "fixed-function layer composite blend allowlist accepts $mode",
    )

    else -> BlendPlan(
        mode = mode,
        kind = BlendPlan.Kind.RefuseDiagnostic,
        reason = "blend mode $mode is not in the M7 layer composite BlendPlan allowlist. " +
            "Fixed-function modes: $layerFixedFunctionBlendModeNames. " +
            "Shader layer-composite modes: $layerShaderCompositeBlendModeNames. " +
            "Other modes need additional shader work and are deferred.",
    )
}
