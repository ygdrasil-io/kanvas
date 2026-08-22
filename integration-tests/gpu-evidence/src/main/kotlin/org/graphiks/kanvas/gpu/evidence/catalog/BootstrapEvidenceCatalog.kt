package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.ReferenceRaster
import org.graphiks.kanvas.gpu.evidence.programs.ProductScenePrograms
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectID

/** Bootstrap catalog deliberately contains only code-backed product routes available at this commit. */
object BootstrapEvidenceCatalog {
    val cases: List<EvidenceCase> = listOf(solidCardStack(), unregisteredRuntimeEffectRefusal())
    val catalog = EvidenceSceneCatalog(cases.map(EvidenceCase::descriptor))

    private fun solidCardStack(): EvidenceCase {
        val draws = listOf(
            GPUSolidRectFrameResolvedDraw(1, GPUPixelBounds(0, 0, 64, 64), listOf(0.05f, 0.08f, 0.13f, 1f)),
            GPUSolidRectFrameResolvedDraw(2, GPUPixelBounds(8, 10, 56, 34), listOf(0.12f, 0.45f, 0.82f, 1f)),
            GPUSolidRectFrameResolvedDraw(3, GPUPixelBounds(14, 38, 50, 54), listOf(0.95f, 0.53f, 0.18f, 1f)),
        )
        return EvidenceCase(
            descriptor = EvidenceSceneDescriptor(
                EvidenceSceneId("solid-card-stack"), "Solid card stack", "Prepared-session product SolidRect recording.",
                64, 64, 1L, setOf("solid-rect", "prepared-session"), EvidenceExpectation.ShouldRender,
                OraclePolicy.GeneratedCpu("reference-raster-rect-src-over", 1), ComparisonPolicy(0, 100.0, 1, "Opaque rectangle fixture."), emptySet(),
            ),
            program = ProductScenePrograms.solidRects(draws),
            oracle = CpuOracle { width, height -> ReferenceRaster(width, height).apply {
                fillRect(0, 0, width, height, intArrayOf(13, 20, 33, 255))
                srcOver(8, 10, 56, 34, intArrayOf(31, 115, 209, 255))
                srcOver(14, 38, 50, 54, intArrayOf(242, 135, 46, 255))
            }.rgba() },
        )
    }

    private fun unregisteredRuntimeEffectRefusal() = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("custom-runtime-effect-unregistered-refusal"), "Unregistered runtime effect refusal", "Unknown custom runtime effect refuses before submission.",
            16, 16, 1L, setOf("runtime-effect", "refusal"),
            EvidenceExpectation.ShouldRefuse("unsupported.runtime_effect.custom_wgsl_not_registered"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        ProductScenePrograms.unregisteredRuntimeEffect(GPUCustomRuntimeEffectID("gpu-evidence.unregistered")),
        null,
    )
}
