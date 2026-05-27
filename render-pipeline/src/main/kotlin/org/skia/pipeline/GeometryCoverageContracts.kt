package org.skia.pipeline

sealed interface DiagnosticReason {
    val code: String
}

sealed interface GeometryReason : DiagnosticReason

sealed interface CoverageReason : DiagnosticReason

enum class StandardGeometryReason(override val code: String) : GeometryReason {
    NonFiniteInput("geometry.nonfinite-input"),
    UnsupportedPerspective("geometry.unsupported-perspective"),
    StrokeDegenerate("geometry.stroke-degenerate"),
    PathEffectUnsupported("geometry.path-effect-unsupported"),
    ClipStackUnsupported("geometry.clip-stack-unsupported"),
    ComputeTessellationNotEnabled("geometry.compute-tessellation-not-enabled"),
}

enum class StandardCoverageReason(override val code: String) : CoverageReason {
    SpanRunsUnsupported("coverage.span-runs-unsupported"),
    AlphaMaskUnsupported("coverage.alpha-mask-unsupported"),
    StencilCoverUnavailable("coverage.stencil-cover-unavailable"),
    EdgeCountExceeded("coverage.edge-count-exceeded"),
    AtlasPolicyUnavailable("coverage.atlas-policy-unavailable"),
    ArbitraryAaClipUnsupported("coverage.arbitrary-aa-clip-unsupported"),
}

data class MatrixSpec(
    val m00: Float,
    val m01: Float,
    val m02: Float,
    val m10: Float,
    val m11: Float,
    val m12: Float,
    val m20: Float,
    val m21: Float,
    val m22: Float,
) {
    companion object {
        val Identity: MatrixSpec = MatrixSpec(
            m00 = 1f,
            m01 = 0f,
            m02 = 0f,
            m10 = 0f,
            m11 = 1f,
            m12 = 0f,
            m20 = 0f,
            m21 = 0f,
            m22 = 1f,
        )
    }
}

data class TransformFacts(
    val matrix: MatrixSpec,
    val isAxisAligned: Boolean,
    val hasPerspective: Boolean,
    val maxScale: Float,
    val isInvertible: Boolean,
)

data class GeometryBounds(
    val conservative: FloatRect,
    val tight: FloatRect? = null,
)

data class RRectSpec(
    val bounds: FloatRect,
    val topLeftRadius: Point,
    val topRightRadius: Point,
    val bottomRightRadius: Point,
    val bottomLeftRadius: Point,
)

enum class PathFillType {
    Winding,
    EvenOdd,
}

data class StrokePlan(
    val width: Float,
    val miterLimit: Float,
)

data class PathVerbSlice(val verbCount: Int)

data class GlyphRunRef(val id: String)

data class SamplingGeometry(val filter: String)

sealed interface GeometryPrimitive {
    data class Rect(val source: FloatRect, val device: FloatRect) : GeometryPrimitive
    data class RRect(val shape: RRectSpec) : GeometryPrimitive
    data class Oval(val bounds: FloatRect) : GeometryPrimitive
    data class Path(
        val fillType: PathFillType,
        val stroke: StrokePlan?,
        val verbs: PathVerbSlice,
    ) : GeometryPrimitive

    data class GlyphMask(val run: GlyphRunRef) : GeometryPrimitive
    data class ImageRect(
        val source: FloatRect,
        val destination: FloatRect,
        val sampling: SamplingGeometry,
    ) : GeometryPrimitive
}

data class ClipShapeSpec(val bounds: FloatRect, val kind: String)

data class AaClipRef(val id: String)

sealed interface ClipInteraction {
    data object None : ClipInteraction
    data class DeviceRect(val bounds: IntRect) : ClipInteraction
    data class AnalyticShape(val shape: ClipShapeSpec) : ClipInteraction
    data class AaClip(val ref: AaClipRef, val bounds: IntRect) : ClipInteraction
    data class ShaderClip(val reason: CoverageReason) : ClipInteraction
    data class Unsupported(val reason: GeometryReason) : ClipInteraction
}

enum class ClipStackBackendDisposition {
    Supported,
    Refused,
}

data class ClipStackBreadthCase(
    val family: String,
    val operation: String,
    val cpuClip: ClipInteraction,
    val cpuRoute: String,
    val cpuDisposition: ClipStackBackendDisposition,
    val cpuDescriptorFallbackReason: String?,
    val webGpuClip: ClipInteraction,
    val webGpuDisposition: ClipStackBackendDisposition,
    val webGpuReason: DiagnosticReason?,
    val pmEvidence: String,
) {
    fun dump(): String =
        "family=$family;operation=$operation;cpu=${dumpClipInteraction(cpuClip)};" +
            "cpuRoute=$cpuRoute;cpuDisposition=$cpuDisposition;" +
            "cpuDescriptorFallbackReason=${cpuDescriptorFallbackReason ?: "none"};" +
            "webgpu=${dumpClipInteraction(webGpuClip)};webgpuDisposition=$webGpuDisposition;" +
            "webgpuReason=${webGpuReason?.code ?: "none"};pmEvidence=$pmEvidence"
}

object ClipStackBreadthMatrix {
    val cases: List<ClipStackBreadthCase> = listOf(
        ClipStackBreadthCase(
            family = "rect-intersect",
            operation = "intersect",
            cpuClip = ClipInteraction.DeviceRect(IntRect(0, 0, 16, 16)),
            cpuRoute = "kanvas-skia.current.device-rect-clip",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = null,
            webGpuClip = ClipInteraction.DeviceRect(IntRect(0, 0, 16, 16)),
            webGpuDisposition = ClipStackBackendDisposition.Supported,
            webGpuReason = null,
            pmEvidence = "clip stress grid: rect intersect uses bounds tightening/scissor",
        ),
        ClipStackBreadthCase(
            family = "rrect-intersect",
            operation = "intersect",
            cpuClip = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(1f, 1f, 15f, 15f), kind = "rrect-intersect"),
            ),
            cpuRoute = "kanvas-skia.current.sk-aa-clip-or-analytic-shape",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = null,
            webGpuClip = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(1f, 1f, 15f, 15f), kind = "rrect-intersect"),
            ),
            webGpuDisposition = ClipStackBackendDisposition.Supported,
            webGpuReason = null,
            pmEvidence = "clip stress grid: rrect intersect uses analytic shape clip",
        ),
        ClipStackBreadthCase(
            family = "rect-difference",
            operation = "difference",
            cpuClip = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(4f, 4f, 12f, 12f), kind = "rect-difference"),
            ),
            cpuRoute = "kanvas-skia.current.sk-aa-clip-difference",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = null,
            webGpuClip = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(4f, 4f, 12f, 12f), kind = "rect-difference"),
            ),
            webGpuDisposition = ClipStackBackendDisposition.Supported,
            webGpuReason = null,
            pmEvidence = "clip stress grid: simple-shape difference remains analytic",
        ),
        ClipStackBreadthCase(
            family = "arbitrary-aa-path-intersect",
            operation = "intersect",
            cpuClip = ClipInteraction.AaClip(AaClipRef("cpu.sk-aa-clip.path-intersect"), IntRect(0, 0, 16, 16)),
            cpuRoute = "kanvas-skia.current.sk-aa-clip-rle",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = "coverage.cpu-descriptor-aa-clip-unsupported",
            webGpuClip = ClipInteraction.AaClip(AaClipRef("cpu.sk-aa-clip.path-intersect"), IntRect(0, 0, 16, 16)),
            webGpuDisposition = ClipStackBackendDisposition.Refused,
            webGpuReason = StandardCoverageReason.ArbitraryAaClipUnsupported,
            pmEvidence = "clip stress grid: arbitrary AA path intersect refused on WebGPU",
        ),
        ClipStackBreadthCase(
            family = "multi-shape-aa-difference",
            operation = "difference",
            cpuClip = ClipInteraction.AaClip(AaClipRef("cpu.sk-aa-clip.multi-shape-difference"), IntRect(0, 0, 16, 16)),
            cpuRoute = "kanvas-skia.current.sk-aa-clip-rle",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = "coverage.cpu-descriptor-aa-clip-unsupported",
            webGpuClip = ClipInteraction.AaClip(AaClipRef("cpu.sk-aa-clip.multi-shape-difference"), IntRect(0, 0, 16, 16)),
            webGpuDisposition = ClipStackBackendDisposition.Refused,
            webGpuReason = StandardCoverageReason.ArbitraryAaClipUnsupported,
            pmEvidence = "clip stress grid: multi-shape AA difference refused until mask/list/atlas strategy exists",
        ),
        ClipStackBreadthCase(
            family = "shader-clip",
            operation = "intersect",
            cpuClip = ClipInteraction.ShaderClip(StandardCoverageReason.ArbitraryAaClipUnsupported),
            cpuRoute = "kanvas-skia.current.clip-shader-coverage",
            cpuDisposition = ClipStackBackendDisposition.Supported,
            cpuDescriptorFallbackReason = "coverage.cpu-descriptor-clip-shader-unsupported",
            webGpuClip = ClipInteraction.ShaderClip(StandardCoverageReason.ArbitraryAaClipUnsupported),
            webGpuDisposition = ClipStackBackendDisposition.Refused,
            webGpuReason = StandardCoverageReason.ArbitraryAaClipUnsupported,
            pmEvidence = "clip stress grid: shader clip refuses as arbitrary coverage modulation on WebGPU",
        ),
        ClipStackBreadthCase(
            family = "unlowerable-stack",
            operation = "mixed",
            cpuClip = ClipInteraction.Unsupported(StandardGeometryReason.ClipStackUnsupported),
            cpuRoute = "kanvas-skia.current.unsupported",
            cpuDisposition = ClipStackBackendDisposition.Refused,
            cpuDescriptorFallbackReason = null,
            webGpuClip = ClipInteraction.Unsupported(StandardGeometryReason.ClipStackUnsupported),
            webGpuDisposition = ClipStackBackendDisposition.Refused,
            webGpuReason = StandardGeometryReason.ClipStackUnsupported,
            pmEvidence = "clip stress grid: unlowerable stack reports geometry.clip-stack-unsupported",
        ),
    )

    fun dump(): String = cases.joinToString(separator = "\n") { it.dump() }
}

sealed interface GeometryPlan {
    data class Supported(
        val primitive: GeometryPrimitive,
        val bounds: GeometryBounds,
        val transform: TransformFacts,
        val clip: ClipInteraction,
    ) : GeometryPlan

    data class Unsupported(val reason: GeometryReason) : GeometryPlan
}

data class AlphaMaskRef(val id: String)

data class CoverageAtlasRef(val id: String)

sealed interface CoverageCachePolicy {
    data object FrameLocal : CoverageCachePolicy
    data object PersistentByShapeKey : CoverageCachePolicy
    data object NoCache : CoverageCachePolicy
}

sealed interface CoveragePlan {
    data object Full : CoveragePlan
    data class AnalyticRect(val bounds: FloatRect, val aa: Boolean) : CoveragePlan
    data class AnalyticRRect(val shape: RRectSpec, val aa: Boolean) : CoveragePlan
    data class SpanRuns(val bounds: IntRect) : CoveragePlan
    data class AlphaMask(val ref: AlphaMaskRef, val bounds: IntRect, val format: MaskFormat) : CoveragePlan
    data class PathCoverage(val fillType: PathFillType, val aa: Boolean, val inverse: Boolean) : CoveragePlan
    data class CoverageAtlas(
        val ref: CoverageAtlasRef,
        val bounds: IntRect,
        val cachePolicy: CoverageCachePolicy,
    ) : CoveragePlan

    data class Unsupported(val reason: CoverageReason) : CoveragePlan
}

sealed interface CoverageBackendStrategy {
    val reasonCode: String?

    data class CpuSpanPath(
        val fillType: PathFillType,
        val aa: Boolean,
        val inverse: Boolean,
    ) : CoverageBackendStrategy {
        override val reasonCode: String? = null
    }

    data class CoverageAtlasSample(
        val ref: CoverageAtlasRef,
        val bounds: IntRect,
        val cachePolicy: CoverageCachePolicy,
    ) : CoverageBackendStrategy {
        override val reasonCode: String? = null
    }

    data class UnsupportedFallback(
        val fallback: FallbackPlan,
        val reason: DiagnosticReason,
    ) : CoverageBackendStrategy {
        override val reasonCode: String = reason.code
    }
}

sealed interface CoverageLoweringResult {
    data class CoverageModelResult(val coverage: CoverageModel) : CoverageLoweringResult
    data class StrategyResult(val strategy: CoverageBackendStrategy) : CoverageLoweringResult
}

object CoveragePlanAdapter {
    fun lower(plan: CoveragePlan): CoverageLoweringResult = when (plan) {
        CoveragePlan.Full -> CoverageLoweringResult.CoverageModelResult(CoverageModel.Full)
        is CoveragePlan.AnalyticRect -> CoverageLoweringResult.CoverageModelResult(
            CoverageModel.AnalyticRect(bounds = plan.bounds, aa = plan.aa),
        )
        is CoveragePlan.AnalyticRRect -> unsupported(StandardCoverageReason.AlphaMaskUnsupported)
        is CoveragePlan.SpanRuns -> CoverageLoweringResult.CoverageModelResult(CoverageModel.Span)
        is CoveragePlan.AlphaMask -> CoverageLoweringResult.CoverageModelResult(
            CoverageModel.AlphaMask(bounds = plan.bounds, format = plan.format),
        )
        is CoveragePlan.PathCoverage -> CoverageLoweringResult.StrategyResult(
            CoverageBackendStrategy.CpuSpanPath(
                fillType = plan.fillType,
                aa = plan.aa,
                inverse = plan.inverse,
            ),
        )
        is CoveragePlan.CoverageAtlas -> CoverageLoweringResult.StrategyResult(
            CoverageBackendStrategy.CoverageAtlasSample(
                ref = plan.ref,
                bounds = plan.bounds,
                cachePolicy = plan.cachePolicy,
            ),
        )
        is CoveragePlan.Unsupported -> unsupported(plan.reason)
    }

    private fun unsupported(reason: CoverageReason): CoverageLoweringResult.StrategyResult =
        CoverageLoweringResult.StrategyResult(
            CoverageBackendStrategy.UnsupportedFallback(
                fallback = FallbackPlan.RefuseDiagnostic(reason.code),
                reason = reason,
            ),
        )
}

data class CoverageDescriptorDump(
    val geometryPlan: GeometryPlan,
    val coveragePlan: CoveragePlan,
    val loweringResult: CoverageLoweringResult,
) {
    fun dump(): String = buildString {
        appendLine("GeometryCoverageDescriptor(v1)")
        appendLine("geometry=${dumpGeometry(geometryPlan)}")
        appendLine("coverage=${dumpCoveragePlan(coveragePlan)}")
        appendLine("lowering=${dumpLowering(loweringResult)}")
    }.trimEnd()

    private fun dumpGeometry(plan: GeometryPlan): String = when (plan) {
        is GeometryPlan.Supported -> "Supported(${dumpPrimitive(plan.primitive)},clip=${dumpClip(plan.clip)})"
        is GeometryPlan.Unsupported -> "Unsupported(reason=${plan.reason.code})"
    }

    private fun dumpPrimitive(primitive: GeometryPrimitive): String = when (primitive) {
        is GeometryPrimitive.Rect -> "Rect(device=${dumpFloatRect(primitive.device)})"
        is GeometryPrimitive.RRect -> "RRect(bounds=${dumpFloatRect(primitive.shape.bounds)})"
        is GeometryPrimitive.Oval -> "Oval(bounds=${dumpFloatRect(primitive.bounds)})"
        is GeometryPrimitive.Path -> "Path(fillType=${primitive.fillType},stroke=${primitive.stroke != null},verbs=${primitive.verbs.verbCount})"
        is GeometryPrimitive.GlyphMask -> "GlyphMask(ref=${primitive.run.id})"
        is GeometryPrimitive.ImageRect -> "ImageRect(dst=${dumpFloatRect(primitive.destination)},sampling=${primitive.sampling.filter})"
    }

    private fun dumpClip(clip: ClipInteraction): String = when (clip) {
        ClipInteraction.None -> "None"
        is ClipInteraction.DeviceRect -> "DeviceRect(${dumpIntRect(clip.bounds)})"
        is ClipInteraction.AnalyticShape -> "AnalyticShape(${clip.shape.kind},${dumpFloatRect(clip.shape.bounds)})"
        is ClipInteraction.AaClip -> "AaClip(ref=${clip.ref.id},bounds=${dumpIntRect(clip.bounds)})"
        is ClipInteraction.ShaderClip -> "ShaderClip(reason=${clip.reason.code})"
        is ClipInteraction.Unsupported -> "Unsupported(reason=${clip.reason.code})"
    }

    private fun dumpCoveragePlan(plan: CoveragePlan): String = when (plan) {
        CoveragePlan.Full -> "Full"
        is CoveragePlan.AnalyticRect -> "AnalyticRect(${dumpFloatRect(plan.bounds)},aa=${plan.aa})"
        is CoveragePlan.AnalyticRRect -> "AnalyticRRect(${dumpFloatRect(plan.shape.bounds)},aa=${plan.aa})"
        is CoveragePlan.SpanRuns -> "SpanRuns(${dumpIntRect(plan.bounds)})"
        is CoveragePlan.AlphaMask -> "AlphaMask(ref=${plan.ref.id},bounds=${dumpIntRect(plan.bounds)},format=${plan.format})"
        is CoveragePlan.PathCoverage -> "PathCoverage(fillType=${plan.fillType},aa=${plan.aa},inverse=${plan.inverse})"
        is CoveragePlan.CoverageAtlas -> "CoverageAtlas(ref=${plan.ref.id},bounds=${dumpIntRect(plan.bounds)},policy=${plan.cachePolicy::class.simpleName})"
        is CoveragePlan.Unsupported -> "Unsupported(reason=${plan.reason.code})"
    }

    private fun dumpLowering(result: CoverageLoweringResult): String = when (result) {
        is CoverageLoweringResult.CoverageModelResult -> "CoverageModel.${dumpCoverageModel(result.coverage)}"
        is CoverageLoweringResult.StrategyResult -> "Strategy.${dumpStrategy(result.strategy)}"
    }

    private fun dumpCoverageModel(model: CoverageModel): String = when (model) {
        CoverageModel.Full -> "Full"
        CoverageModel.Span -> "Span"
        is CoverageModel.AlphaMask -> "AlphaMask(${dumpIntRect(model.bounds)},format=${model.format})"
        is CoverageModel.AnalyticRect -> "AnalyticRect(${dumpFloatRect(model.bounds)},aa=${model.aa})"
    }

    private fun dumpStrategy(strategy: CoverageBackendStrategy): String = when (strategy) {
        is CoverageBackendStrategy.CpuSpanPath -> "CpuSpanPath(fillType=${strategy.fillType},aa=${strategy.aa},inverse=${strategy.inverse})"
        is CoverageBackendStrategy.CoverageAtlasSample -> "CoverageAtlasSample(ref=${strategy.ref.id},policy=${strategy.cachePolicy::class.simpleName})"
        is CoverageBackendStrategy.UnsupportedFallback -> "UnsupportedFallback(reason=${strategy.reason.code},fallback=${strategy.fallback.reason})"
    }

    private fun dumpFloatRect(rect: FloatRect): String =
        "${rect.left},${rect.top},${rect.right},${rect.bottom}"

    private fun dumpIntRect(rect: IntRect): String =
        "${rect.left},${rect.top},${rect.right},${rect.bottom}"
}

fun dumpClipInteraction(clip: ClipInteraction): String = when (clip) {
    ClipInteraction.None -> "None"
    is ClipInteraction.DeviceRect -> "DeviceRect(${clip.bounds.left},${clip.bounds.top},${clip.bounds.right},${clip.bounds.bottom})"
    is ClipInteraction.AnalyticShape -> "AnalyticShape(${clip.shape.kind},${clip.shape.bounds.left},${clip.shape.bounds.top},${clip.shape.bounds.right},${clip.shape.bounds.bottom})"
    is ClipInteraction.AaClip -> "AaClip(ref=${clip.ref.id},bounds=${clip.bounds.left},${clip.bounds.top},${clip.bounds.right},${clip.bounds.bottom})"
    is ClipInteraction.ShaderClip -> "ShaderClip(reason=${clip.reason.code})"
    is ClipInteraction.Unsupported -> "Unsupported(reason=${clip.reason.code})"
}
