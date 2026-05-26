package org.skia.pipeline

enum class AlphaDomain {
    Unpremul,
    Premul,
    Raw,
    Destination,
}

sealed interface ColorSpaceRole {
    data object SRGB : ColorSpaceRole
    data object Destination : ColorSpaceRole
    data object Working : ColorSpaceRole
    data class Explicit(val name: String?) : ColorSpaceRole
    data object RawBytes : ColorSpaceRole
}

enum class PrecisionDomain {
    U8,
    F16,
    F32,
}

data class ColorValueSpec(
    val alpha: AlphaDomain,
    val colorSpace: ColorSpaceRole,
    val precision: PrecisionDomain,
)

sealed interface CoverageModel {
    data object Full : CoverageModel
    data object Span : CoverageModel
    data class AlphaMask(val bounds: IntRect, val format: MaskFormat) : CoverageModel
    data class AnalyticRect(val bounds: FloatRect, val aa: Boolean) : CoverageModel
}

enum class MaskFormat {
    A8,
    A16,
}

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class FloatRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)
data class Point(val x: Float, val y: Float)
data class LinearGradientPayload(val start: Point, val end: Point, val startColor: Rgba, val endColor: Rgba)

sealed interface FallbackPlan {
    val reason: String
    val supportedBackends: Set<BackendKind>

    data class CpuShadeRow(override val reason: String) : FallbackPlan {
        override val supportedBackends: Set<BackendKind> = setOf(BackendKind.CPU)
    }

    data class HandwrittenGpuCompat(override val reason: String, val shaderId: String) : FallbackPlan {
        override val supportedBackends: Set<BackendKind> = setOf(BackendKind.GPU)
    }

    data class RefuseDiagnostic(override val reason: String) : FallbackPlan {
        override val supportedBackends: Set<BackendKind> = emptySet()
    }

    data class ExplicitLayerOrReadbackCompat(override val reason: String) : FallbackPlan {
        override val supportedBackends: Set<BackendKind> = setOf(BackendKind.CPU, BackendKind.GPU)
    }
}

enum class BackendKind {
    CPU,
    GPU,
}

sealed interface PipelineOp {
    data object SeedDeviceCoords : PipelineOp
    data class ConstantColor(val color: Rgba) : PipelineOp
    data class LinearGradient(val payload: LinearGradientPayload) : PipelineOp
    data class PaintColorModulate(val paintColor: Rgba) : PipelineOp
    data class ApplyCoverage(val coverage: CoverageModel) : PipelineOp
    data class BlendMode(val mode: String) : PipelineOp
    data class ColorSpaceXform(val src: ColorSpaceRole, val dst: ColorSpaceRole) : PipelineOp
    data object LoadDst : PipelineOp
    data object Store : PipelineOp
}

sealed interface AppendResult {
    data object Success : AppendResult
    data class Unsupported(val reason: String) : AppendResult
    data class Fatal(val reason: String) : AppendResult
}

class KanvasPipelineIR private constructor(
    val ops: List<PipelineOp>,
    val fallbackPlan: FallbackPlan?,
) {
    fun dump(): String {
        val lines = mutableListOf<String>()
        lines += "KanvasPipelineIR(v1)"
        ops.forEachIndexed { index, op ->
            lines += "%02d %s".format(index, dumpOp(op))
        }
        lines += "fallback=${dumpFallback(fallbackPlan)}"
        return lines.joinToString("\n")
    }

    private fun dumpOp(op: PipelineOp): String = when (op) {
        PipelineOp.SeedDeviceCoords -> "SeedDeviceCoords"
        is PipelineOp.ConstantColor -> "ConstantColor(${op.color.r},${op.color.g},${op.color.b},${op.color.a})"
        is PipelineOp.LinearGradient -> "LinearGradient(${op.payload.start.x},${op.payload.start.y}->${op.payload.end.x},${op.payload.end.y})"
        is PipelineOp.PaintColorModulate -> "PaintColorModulate(${op.paintColor.r},${op.paintColor.g},${op.paintColor.b},${op.paintColor.a})"
        is PipelineOp.ApplyCoverage -> "ApplyCoverage(${dumpCoverage(op.coverage)})"
        is PipelineOp.BlendMode -> "BlendMode(${op.mode})"
        is PipelineOp.ColorSpaceXform -> "ColorSpaceXform(${dumpColorSpace(op.src)}->${dumpColorSpace(op.dst)})"
        PipelineOp.LoadDst -> "LoadDst"
        PipelineOp.Store -> "Store"
    }

    private fun dumpCoverage(model: CoverageModel): String = when (model) {
        CoverageModel.Full -> "Full"
        CoverageModel.Span -> "Span"
        is CoverageModel.AlphaMask -> "AlphaMask(${model.bounds.left},${model.bounds.top},${model.bounds.right},${model.bounds.bottom},${model.format})"
        is CoverageModel.AnalyticRect -> "AnalyticRect(${model.bounds.left},${model.bounds.top},${model.bounds.right},${model.bounds.bottom},aa=${model.aa})"
    }

    private fun dumpColorSpace(role: ColorSpaceRole): String = when (role) {
        ColorSpaceRole.SRGB -> "SRGB"
        ColorSpaceRole.Destination -> "Destination"
        ColorSpaceRole.Working -> "Working"
        is ColorSpaceRole.Explicit -> "Explicit(${role.name})"
        ColorSpaceRole.RawBytes -> "RawBytes"
    }

    private fun dumpFallback(plan: FallbackPlan?): String = when (plan) {
        null -> "none"
        is FallbackPlan.CpuShadeRow -> "CpuShadeRow(reason=${plan.reason})"
        is FallbackPlan.HandwrittenGpuCompat -> "HandwrittenGpuCompat(reason=${plan.reason},shaderId=${plan.shaderId})"
        is FallbackPlan.RefuseDiagnostic -> "RefuseDiagnostic(reason=${plan.reason})"
        is FallbackPlan.ExplicitLayerOrReadbackCompat -> "ExplicitLayerOrReadbackCompat(reason=${plan.reason})"
    }

    companion object {
        fun builder(): Builder = Builder()

        fun demoSolidRectIr(color: Rgba, coverage: CoverageModel = CoverageModel.Full): KanvasPipelineIR {
            return builder()
                .append(PipelineOp.SeedDeviceCoords)
                .append(PipelineOp.ConstantColor(color))
                .append(PipelineOp.ApplyCoverage(coverage))
                .append(PipelineOp.LoadDst)
                .append(PipelineOp.BlendMode("SrcOver"))
                .append(PipelineOp.Store)
                .build()
        }

        fun demoLinearGradientRectIr(
            start: Point,
            end: Point,
            startColor: Rgba,
            endColor: Rgba,
            coverage: CoverageModel = CoverageModel.Full,
        ): KanvasPipelineIR {
            return builder()
                .append(PipelineOp.SeedDeviceCoords)
                .append(PipelineOp.LinearGradient(LinearGradientPayload(start, end, startColor, endColor)))
                .append(PipelineOp.ApplyCoverage(coverage))
                .append(PipelineOp.LoadDst)
                .append(PipelineOp.BlendMode("SrcOver"))
                .append(PipelineOp.Store)
                .build()
        }
    }

    class Builder internal constructor() {
        private val ops: MutableList<PipelineOp> = mutableListOf()
        private var fallbackPlan: FallbackPlan? = null

        fun append(op: PipelineOp): Builder {
            ops += op
            return this
        }

        fun setFallback(plan: FallbackPlan?): Builder {
            fallbackPlan = plan
            return this
        }

        fun appendTransactional(block: (Builder) -> AppendResult): AppendResult {
            val snapshotOps = ops.toList()
            val snapshotFallback = fallbackPlan
            val draft = Builder()
            draft.ops += snapshotOps
            draft.fallbackPlan = snapshotFallback
            val result = block(draft)
            return when (result) {
                AppendResult.Success -> {
                    ops.clear()
                    ops += draft.ops
                    fallbackPlan = draft.fallbackPlan
                    result
                }

                is AppendResult.Unsupported,
                is AppendResult.Fatal,
                -> result
            }
        }

        fun build(): KanvasPipelineIR = KanvasPipelineIR(ops = ops.toList(), fallbackPlan = fallbackPlan)
    }
}
