package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class EvidenceSceneContractsTest {
    @Test
    fun `scene ids must be lower kebab case`() {
        assertFailsWith<IllegalArgumentException> { EvidenceSceneId("") }
        assertFailsWith<IllegalArgumentException> { EvidenceSceneId("Not-Kebab") }
        assertFailsWith<IllegalArgumentException> { EvidenceSceneId("not_kebab") }
        assertFailsWith<IllegalArgumentException> { EvidenceSceneId("not--kebab") }
    }

    @Test
    fun `catalog rejects duplicate scene ids`() {
        val first = renderDescriptor("same-scene")
        assertFailsWith<IllegalArgumentException> {
            EvidenceSceneCatalog(listOf(first, first.copy(title = "second")))
        }
    }

    @Test
    fun `descriptor rejects non-positive dimensions and versions`() {
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("bad-width").copy(width = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("bad-height").copy(height = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("bad-comparison-version").copy(
                comparison = ComparisonPolicy(1, 99.0, 0, "test"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EvidenceSceneDescriptor(
                id = EvidenceSceneId("bad-oracle-version"),
                title = "Title",
                purpose = "Purpose",
                width = 1,
                height = 1,
                seed = 1L,
                tags = emptySet(),
                expectation = EvidenceExpectation.ShouldRender,
                oracle = OraclePolicy.GeneratedCpu("oracle", 0),
                comparison = ComparisonPolicy(1, 99.0, 1, "test"),
                requiredCapabilities = emptySet(),
            )
        }
    }

    @Test
    fun `descriptor rejects invalid tolerance and similarity`() {
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("negative-tolerance").copy(
                comparison = ComparisonPolicy(-1, 99.0, 1, "test"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("large-tolerance").copy(
                comparison = ComparisonPolicy(256, 99.0, 1, "test"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("negative-similarity").copy(
                comparison = ComparisonPolicy(1, -0.1, 1, "test"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("large-similarity").copy(
                comparison = ComparisonPolicy(1, 100.1, 1, "test"),
            )
        }
    }

    @Test
    fun `render expectations require an image oracle and comparison policy`() {
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("render-without-oracle").copy(oracle = OraclePolicy.StableRefusal)
        }
        assertFailsWith<IllegalArgumentException> {
            renderDescriptor("render-without-comparison").copy(comparison = null)
        }
    }

    @Test
    fun `refusal expectations require stable refusal and a code`() {
        assertFailsWith<IllegalArgumentException> {
            refusalDescriptor("refusal-with-image-policy").copy(
                oracle = OraclePolicy.CheckedInPng("ref.png", "a".repeat(64), "test"),
                comparison = ComparisonPolicy(1, 99.0, 1, "test"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            refusalDescriptor("refusal-without-stable-refusal").copy(
                oracle = OraclePolicy.GeneratedCpu("oracle", 1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            refusalDescriptor("refusal-with-blank-code").copy(
                expectation = EvidenceExpectation.ShouldRefuse(" "),
            )
        }
    }

    @Test
    fun `rendered and comparison pixels are defensively owned`() {
        val rgba = byteArrayOf(1, 2, 3, 4)
        val diff = byteArrayOf(4, 3, 2, 1)
        val observation = SceneObservation.Rendered(
            rgba = rgba,
            route = routeEvidence(),
            diagnostics = emptyList(),
            environment = environment(),
            comparison = ImageComparison(true, 100.0, 0, 0, 0.0, diff, 1),
        )
        rgba[0] = 9
        diff[0] = 9
        assertNotEquals(9, observation.rgba[0])
        assertNotEquals(9, observation.comparison.diffRgba[0])
        val returned = observation.rgba
        returned[1] = 9
        assertNotEquals(9, observation.rgba[1])
    }

    private fun renderDescriptor(id: String): EvidenceSceneDescriptor = EvidenceSceneDescriptor(
        id = EvidenceSceneId(id),
        title = "Test scene",
        purpose = "Test purpose",
        width = 16,
        height = 16,
        seed = 1L,
        tags = setOf("test"),
        expectation = EvidenceExpectation.ShouldRender,
        oracle = OraclePolicy.GeneratedCpu("test-oracle", 1),
        comparison = ComparisonPolicy(1, 99.0, 1, "test"),
        requiredCapabilities = emptySet(),
    )

    private fun refusalDescriptor(id: String): EvidenceSceneDescriptor = EvidenceSceneDescriptor(
        id = EvidenceSceneId(id),
        title = "Test refusal",
        purpose = "Test purpose",
        width = 16,
        height = 16,
        seed = 1L,
        tags = setOf("test"),
        expectation = EvidenceExpectation.ShouldRefuse("unsupported.example"),
        oracle = OraclePolicy.StableRefusal,
        comparison = null,
        requiredCapabilities = emptySet(),
    )

    private fun environment() = EvidenceEnvironment(
        sourceCommit = "commit",
        osName = "test",
        osVersion = "1",
        osArchitecture = "x86_64",
        javaVersion = "17",
        adapter = null,
        deviceGeneration = null,
        capabilityImplementation = null,
        available = true,
    )

    private fun routeEvidence() = RouteEvidence(
        routeId = "route",
        attemptId = null,
        furthestPhase = null,
        outcome = "rendered",
        encodedScopeKinds = emptyList(),
        structuralEvents = emptyList(),
        structuralCounters = emptyMap(),
        runtimeTelemetryDelta = org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry.Empty,
    )
}
