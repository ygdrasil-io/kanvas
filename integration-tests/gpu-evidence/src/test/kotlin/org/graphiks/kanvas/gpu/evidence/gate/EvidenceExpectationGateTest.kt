package org.graphiks.kanvas.gpu.evidence.gate

import kotlin.test.Test
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceExpectationGateTest {
    private val gate = EvidenceExpectationGate

    @Test
    fun `render pass requires comparison pass`() {
        assertIs<EvidenceVerdict.Pass>(gate.evaluate(renderDescriptor(), rendered(true)))
        assertIs<EvidenceVerdict.Fail>(gate.evaluate(renderDescriptor(), rendered(false)))
    }

    @Test
    fun `render diff fail and refusal are failures`() {
        assertIs<EvidenceVerdict.Fail>(gate.evaluate(renderDescriptor(), rendered(false)))
        assertIs<EvidenceVerdict.Fail>(
            gate.evaluate(renderDescriptor(), refused("unsupported.example", submissionDelta = 0L)),
        )
    }

    @Test
    fun `render unavailable remains unavailable`() {
        assertIs<EvidenceVerdict.Unavailable>(
            gate.evaluate(renderDescriptor(), unavailable("environment.unavailable")),
        )
    }

    @Test
    fun `exact refusal passes only before submission`() {
        val descriptor = refusalDescriptor("unsupported.example")

        assertIs<EvidenceVerdict.Pass>(
            gate.evaluate(descriptor, refused("unsupported.example", submissionDelta = 0L)),
        )
        assertIs<EvidenceVerdict.Fail>(
            gate.evaluate(descriptor, refused("unsupported.example", submissionDelta = 1L)),
        )
    }

    @Test
    fun `refusal wrong code and rendered observation fail`() {
        val descriptor = refusalDescriptor("unsupported.example")
        assertIs<EvidenceVerdict.Fail>(
            gate.evaluate(descriptor, refused("different.code", submissionDelta = 0L)),
        )
        assertIs<EvidenceVerdict.Fail>(gate.evaluate(descriptor, rendered(true)))
    }

    @Test
    fun `refusal unavailable remains unavailable`() {
        assertIs<EvidenceVerdict.Unavailable>(
            gate.evaluate(refusalDescriptor("unsupported.example"), unavailable("environment.unavailable")),
        )
    }

    private fun renderDescriptor() = EvidenceSceneDescriptor(
        id = EvidenceSceneId("render-scene"),
        title = "Render",
        purpose = "Render purpose",
        width = 16,
        height = 16,
        seed = 1L,
        tags = emptySet(),
        expectation = EvidenceExpectation.ShouldRender,
        oracle = OraclePolicy.GeneratedCpu("oracle", 1),
        comparison = ComparisonPolicy(1, 99.0, 1, "test"),
        requiredCapabilities = emptySet(),
    )

    private fun refusalDescriptor(code: String) = EvidenceSceneDescriptor(
        id = EvidenceSceneId("refusal-scene"),
        title = "Refusal",
        purpose = "Refusal purpose",
        width = 16,
        height = 16,
        seed = 1L,
        tags = emptySet(),
        expectation = EvidenceExpectation.ShouldRefuse(code),
        oracle = OraclePolicy.StableRefusal,
        comparison = null,
        requiredCapabilities = emptySet(),
    )

    private fun rendered(passed: Boolean) = SceneObservation.Rendered(
        rgba = byteArrayOf(0, 0, 0, 0),
        route = routeEvidence(),
        diagnostics = emptyList(),
        environment = environment(),
        comparison = ImageComparison(passed, if (passed) 100.0 else 0.0, if (passed) 0 else 1, if (passed) 0 else 255, 0.0, byteArrayOf(), 1),
    )

    private fun refused(code: String, submissionDelta: Long) = SceneObservation.Refused(
        stableReasonCode = code,
        message = "refused",
        submissionDelta = submissionDelta,
        route = routeEvidence(),
        diagnostics = emptyList(),
        environment = environment(),
    )

    private fun unavailable(code: String) = SceneObservation.Unavailable(code, "unavailable", environment())

    private fun environment() = EvidenceEnvironment("commit", "test", "1", "x86_64", "17", null, null, null, true)

    private fun routeEvidence() = RouteEvidence("route", null, null, "outcome", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
}
