package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.ReferenceRaster
import org.graphiks.kanvas.gpu.evidence.oracle.SeparableBlurCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.GradientCpuOracle
import org.graphiks.kanvas.gpu.evidence.programs.KanvasScenePrograms
import org.graphiks.kanvas.gpu.evidence.programs.RendererRefusalPrograms
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectID
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Curated catalog deliberately contains only product routes backed by the evaluated commit. */
object GpuEvidenceCatalog {
    val renderCases: List<EvidenceCase> = listOf(
        solidCardStack(),
        separableBlurRect(),
        translucentCardOverlap(),
        scissorOverlay(),
        strokeRectOutline(),
        linearGradientLanes(),
        radialSwatch(),
        sweepDisk(),
    )
    val refusalCases: List<EvidenceCase> = listOf(
        unregisteredRuntimeEffectRefusal(),
        aggregateMemoryBudgetRefusal(),
    )
    val cases: List<EvidenceCase> = renderCases + refusalCases
    val catalog = EvidenceSceneCatalog(cases.map(EvidenceCase::descriptor))

    private fun solidCardStack(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("solid-card-stack"), "Solid card stack", "Public Kanvas Surface Canvas recording of opaque card rectangles.",
                64, 64, 1L, setOf("solid-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-rect-src-over", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from opaque SrcOver rectangles."), emptySet(),
            ),
            program = KanvasScenePrograms.solidCardStack(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                srcOver(8, 10, 56, 34, intArrayOf(31, 115, 209, 255))
                srcOver(14, 38, 50, 54, intArrayOf(242, 135, 46, 255))
            }.rgba() },
        )
    }

    private fun separableBlurRect(): EvidenceCase {
        val sourceColor = floatArrayOf(0.18f, 0.42f, 0.76f, 1f)
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("separable-blur-rect"), "Separable blur rectangle", "Public Kanvas Surface normal mask blur recording.",
                64, 64, 1L, setOf("separable-blur", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("separable-blur-transparent-decal", 1),
                ComparisonPolicy(2, 99.0, 1, "Bounded GPU floating-point rounding is allowed after the vertical pass quantization."), emptySet(),
            ),
            program = KanvasScenePrograms.separableBlurRect(),
            oracle = SeparableBlurCpuOracle(org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(16, 16, 48, 48), sourceColor, sigma = 3f),
        )
    }

    private fun translucentCardOverlap(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("translucent-card-overlap"), "Translucent card overlap", "Two partially transparent Kanvas Canvas cards exercise SrcOver overlap.",
                64, 64, 1L, setOf("solid-rect", "translucent", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-translucent-src-over", 1),
                ComparisonPolicy(1, 100.0, 1, "Hardware capture on Apple M2 Max showed rgba8unorm nearest quantization versus ReferenceRaster.srcOver integer truncation: RGB deltas are bounded to 1, alpha remains exact; delta 2 remains a failure."), emptySet(),
            ),
            program = KanvasScenePrograms.translucentCardOverlap(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                srcOver(8, 10, 44, 42, intArrayOf(32, 64, 96, 128))
                srcOver(24, 22, 56, 54, intArrayOf(64, 32, 16, 128))
            }.rgba() },
        )
    }

    private fun scissorOverlay(): EvidenceCase {
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("scissor-overlay"), "Scissor overlay", "Canvas save/clip/restore blocks constrain rectangle draws.",
                64, 64, 1L, setOf("solid-rect", "scissor", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-scissor-intersections", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from literal scissor intersections."), emptySet(),
            ),
            program = KanvasScenePrograms.scissorOverlay(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                fillRect(16, 16, 40, 40, intArrayOf(31, 115, 209, 255))
                fillRect(24, 24, 48, 48, intArrayOf(242, 135, 46, 255))
            }.rgba() },
        )
    }

    private fun strokeRectOutline(): EvidenceCase {
        val clear = intArrayOf(13, 20, 33, 255)
        val stroke = intArrayOf(242, 135, 46, 255)
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("stroke-rect-outline"), "Stroke rectangle outline", "A public Kanvas Paint stroke records one anti-alias-disabled rectangle.",
                64, 64, 1L, setOf("solid-rect", "stroke-rect", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-stroke-rect-bands", 1),
                ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from four literal analytic coverage bands."), emptySet(),
            ),
            program = KanvasScenePrograms.strokeRectOutline(),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, clear)
                fillRect(13, 13, 51, 19, stroke)
                fillRect(13, 45, 51, 51, stroke)
                fillRect(13, 19, 19, 45, stroke)
                fillRect(45, 19, 51, 45, stroke)
            }.rgba() },
        )
    }

    private fun linearGradientLanes(): EvidenceCase {
        val bounds = Rect.fromLTRB(8f, 16f, 56f, 48f)
        val stops = listOf(GradientStop(0f, Color.fromArgb(255, 255, 56, 56)), GradientStop(1f, Color.fromArgb(255, 56, 112, 255)))
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("linear-gradient-lanes"), "Linear gradient lanes", "Public Kanvas Surface clamp linear gradient across a literal rectangle.",
                64, 64, 1L, setOf("linear-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("gradient-cpu-oracle-linear-clamp", 1),
                ComparisonPolicy(1, 100.0, 1, "Independent two-stop opaque clamp-gradient RGBA8 oracle."), emptySet(),
            ),
            program = KanvasScenePrograms.linearGradientLanes(),
            oracle = GradientCpuOracle.linear(bounds, Point(8.5f, 32.5f), Point(55.5f, 32.5f), stops),
        )
    }

    private fun radialSwatch(): EvidenceCase {
        val bounds = Rect.fromLTRB(8f, 8f, 56f, 56f)
        val stops = listOf(GradientStop(0f, Color.fromArgb(255, 255, 232, 72)), GradientStop(1f, Color.fromArgb(255, 48, 80, 192)))
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("radial-swatch"), "Radial swatch", "Public Kanvas Surface clamp radial gradient across a literal swatch.",
                64, 64, 1L, setOf("radial-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("gradient-cpu-oracle-radial-clamp", 1),
                ComparisonPolicy(1, 100.0, 1, "Independent two-stop opaque clamp-gradient RGBA8 oracle."), emptySet(),
            ),
            program = KanvasScenePrograms.radialSwatch(),
            oracle = GradientCpuOracle.radial(bounds, Point(32.5f, 32.5f), 23.5f, stops),
        )
    }

    private fun sweepDisk(): EvidenceCase {
        val bounds = Rect.fromLTRB(8f, 8f, 56f, 56f)
        val stops = listOf(GradientStop(0f, Color.fromArgb(255, 255, 64, 64)), GradientStop(1f, Color.fromArgb(255, 64, 208, 255)))
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("sweep-disk"), "Sweep disk", "Public Kanvas Surface clamp sweep gradient across a literal disk swatch.",
                64, 64, 1L, setOf("sweep-gradient", "kanvas-surface"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("gradient-cpu-oracle-sweep-clamp", 1),
                ComparisonPolicy(1, 100.0, 1, "Independent two-stop opaque clamp-gradient RGBA8 oracle."), emptySet(),
            ),
            program = KanvasScenePrograms.sweepDisk(),
            oracle = GradientCpuOracle.sweep(bounds, Point(32.5f, 32.5f), 0f, 360f, stops),
        )
    }

    private fun unregisteredRuntimeEffectRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("custom-runtime-effect-unregistered-refusal"), "Unregistered runtime effect refusal", "Unknown custom runtime effect refuses before submission.",
            16, 16, 1L, setOf("runtime-effect", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.runtime_effect.custom_wgsl_not_registered"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        RendererRefusalPrograms.unregisteredRuntimeEffect(GPUCustomRuntimeEffectID("gpu-evidence.unregistered")),
        null,
    )

    private fun aggregateMemoryBudgetRefusal(): EvidenceCase = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("aggregate-memory-budget-refusal"), "Aggregate memory budget refusal", "A valid full-target SolidRect draw refuses during product recording under a one-byte budget.",
            16, 16, 1L, setOf("solid-rect", "frame-memory", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.frame_memory.aggregate_budget_exceeded"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        RendererRefusalPrograms.aggregateMemoryBudget(),
        null,
    )
}
