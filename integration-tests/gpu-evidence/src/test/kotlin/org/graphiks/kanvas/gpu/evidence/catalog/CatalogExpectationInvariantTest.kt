package org.graphiks.kanvas.gpu.evidence.catalog

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCapabilities
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCompletedFrame
import org.graphiks.kanvas.gpu.evidence.runner.EvidencePreparedFramePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgramPreparation
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRecordingRequest
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRuntimePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceExecutionResult
import org.graphiks.kanvas.gpu.evidence.runner.GPUPreparedEvidenceExecutor
import org.graphiks.kanvas.gpu.evidence.runner.GpuEvidenceCliRunner
import org.graphiks.kanvas.gpu.evidence.runner.PreparedEvidenceProgram
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.gpu.evidence.runner.ScenePreparation
import org.graphiks.kanvas.gpu.evidence.runner.SceneRecordingContext
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

class CatalogExpectationInvariantTest {
    @Test
    fun `every render case has exactly one oracle and every refusal has none`() {
        assertEquals(30, GpuEvidenceCatalog.cases.size)
        assertEquals(28, GpuEvidenceCatalog.renderCases.size)
        assertEquals(2, GpuEvidenceCatalog.refusalCases.size)
        GpuEvidenceCatalog.renderCases.forEach { evidenceCase ->
            assertIs<org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram>(evidenceCase.program)
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertNotNull(evidenceCase.oracle, evidenceCase.descriptor.id.value)
        }
        GpuEvidenceCatalog.refusalCases.forEach { evidenceCase ->
            assertTrue(evidenceCase.program is SceneProgram || evidenceCase.program is org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram)
            when (evidenceCase.descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> error("refusalCases must not contain renders")
                is EvidenceExpectation.ShouldRefuse -> assertEquals(null, evidenceCase.oracle, evidenceCase.descriptor.id.value)
            }
        }
    }

    @Test
    fun `expectation gate cannot turn a wrong terminal outcome into pass`() {
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val observation = when (evidenceCase.descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> refused(evidenceCase, "unsupported.wrong")
                is EvidenceExpectation.ShouldRefuse -> rendered(evidenceCase)
            }
            assertIs<EvidenceVerdict.Fail>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation), evidenceCase.descriptor.id.value)
        }
    }

    @Test
    fun `fake renderer port runs each refusal contract before gate plumbing`() {
        GpuEvidenceCatalog.refusalCases.filter { it.program is SceneProgram }.forEach { evidenceCase ->
            val port = ExpectedOutcomePort()
            val observed = assertIs<EvidenceExecutionResult.Observed>(GPUPreparedEvidenceExecutor(port, "a".repeat(40)).execute(evidenceCase)).observation
            assertIs<EvidenceVerdict.Pass>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observed), evidenceCase.descriptor.id.value)
            when (evidenceCase.descriptor.id.value) {
                "custom-runtime-effect-unregistered-refusal" -> {
                    val refusal = assertIs<SceneObservation.Refused>(observed)
                    assertEquals("product.runtime-effect.custom", refusal.route.routeId)
                    assertEquals("unsupported.runtime_effect.custom_wgsl_not_registered", refusal.stableReasonCode)
                    assertEquals(0L, refusal.submissionDelta)
                    assertEquals(0, port.preparedFrameCount)
                }
                "aggregate-memory-budget-refusal" -> {
                    val refusal = assertIs<SceneObservation.Refused>(observed)
                    assertEquals("product.solid-rect", refusal.route.routeId)
                    assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", refusal.stableReasonCode)
                    assertEquals(0L, refusal.submissionDelta)
                    assertEquals(true, refusal.diagnostics.contains("diagnostic.code=unsupported.frame_memory.aggregate_budget_exceeded"))
                    assertEquals(0, port.preparedFrameCount)
                }
            }
        }
    }

    @Test
    fun `unavailable fake product port cannot produce a pass for any catalog case`() {
        val executor = GPUPreparedEvidenceExecutor(UnavailablePort, "a".repeat(40))

        GpuEvidenceCatalog.refusalCases.filter { it.program is SceneProgram }.forEach { evidenceCase ->
            val observed = assertIs<EvidenceExecutionResult.Observed>(executor.execute(evidenceCase)).observation
            assertIs<SceneObservation.Unavailable>(observed)
            assertIs<EvidenceVerdict.Unavailable>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observed))
        }
    }

    @Test
    fun `cli never writes generated evidence for unavailable or execution failure`() {
        listOf(UnavailablePort, FailingPort).forEach { backend ->
            val root = Files.createTempDirectory("gpu-evidence-invariant")
            val code = GpuEvidenceCliRunner(
                FakeRuntime(backend),
                cases = GpuEvidenceCatalog.refusalCases,
            ).run(args(root, "custom-runtime-effect-unregistered-refusal"))

            assertEquals(1, code)
            assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
        }
    }

    private fun refused(evidenceCase: EvidenceCase, code: String): SceneObservation.Refused = SceneObservation.Refused(
        code, "wrong outcome", 0L,
        RouteEvidence("fake", null, null, "refused", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry()),
        emptyList(), environment(),
    )

    private fun rendered(evidenceCase: EvidenceCase): SceneObservation.Rendered = SceneObservation.Rendered(
        ByteArray(evidenceCase.descriptor.width * evidenceCase.descriptor.height * 4),
        RouteEvidence("fake", "attempt", "Completed", "rendered", emptyList(), emptyList(), mapOf("queue.submit" to 1L), GPUBackendRuntimeTelemetry(submissions = 1L)),
        emptyList(), environment(),
        ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(evidenceCase.descriptor.width * evidenceCase.descriptor.height * 4), 1),
    )

    private fun environment() = EvidenceEnvironment("a".repeat(40), "test", "test", "test", "test", null, 1L, "fake", true)
    private fun args(root: java.nio.file.Path, scene: String) = arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", scene)

    private class FakeRuntime(private val backend: EvidenceBackendPort?) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? = backend
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private object UnavailablePort : EvidenceBackendPort {
        override val capabilities: EvidenceCapabilities? = null
        override val deviceGeneration: Long = 1L
        override fun telemetry() = GPUBackendRuntimeTelemetry()
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = error("unreachable")
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable")
    }

    private object FailingPort : EvidenceBackendPort {
        override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake")
        override val deviceGeneration: Long = 1L
        override fun telemetry() = GPUBackendRuntimeTelemetry()
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation =
            EvidenceProgramPreparation.Recorded("fake", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
            override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame = error("prepared session failed")
            override fun close() = Unit
        }
    }

    private class ExpectedOutcomePort : EvidenceBackendPort {
        private val productCapabilities = testCapabilities()
        override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake", productCapabilities)
        override val deviceGeneration: Long = 1L
        private var current: EvidenceCase? = null
        var preparedFrameCount = 0
            private set
        override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = if (current?.descriptor?.expectation == EvidenceExpectation.ShouldRender) 1L else 0L)
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
            val evidenceCase = requireNotNull(GpuEvidenceCatalog.refusalCases.firstOrNull { it.descriptor == context.descriptor })
            current = evidenceCase
            return when (val preparation = program.prepare(
                SceneRecordingContext(
                    productCapabilities,
                    GPUDeviceGenerationID(deviceGeneration),
                    GPUFrameTargetRef("target.catalog-test"),
                    GPUPixelBounds(0, 0, context.descriptor.width, context.descriptor.height),
                    context.frameOrdinal,
                    GPUReadbackRequestID(context.readbackRequestId),
                ),
            )) {
                is ScenePreparation.Recorded -> EvidenceProgramPreparation.Recorded(
                    preparation.routeId,
                    PreparedEvidenceProgram(preparation.taskList, context.readbackRequestId),
                    preparation.diagnostics,
                )
                is ScenePreparation.Refused -> EvidenceProgramPreparation.Refused(
                    requireNotNull(program as? RoutedSceneProgram).routeId,
                    preparation.stableReasonCode,
                    preparation.message,
                    preparation.diagnostics,
                )
            }
        }
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort {
            preparedFrameCount++
            return object : EvidencePreparedFramePort {
            override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                val evidenceCase = requireNotNull(current)
                return EvidenceCompletedFrame.succeeded(program.readbackRequestId, requireNotNull(evidenceCase.oracle).render(width, height))
            }
            override fun close() = Unit
            }
        }
    }

    private companion object {
        fun testCapabilities() = GPUCapabilities(
            implementation = GPUImplementationIdentity("GPU", "test", "catalog", "device"),
            facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "catalog-invariant")),
            snapshotId = "catalog-invariant-capabilities",
            limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
            rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
        )
    }
}
