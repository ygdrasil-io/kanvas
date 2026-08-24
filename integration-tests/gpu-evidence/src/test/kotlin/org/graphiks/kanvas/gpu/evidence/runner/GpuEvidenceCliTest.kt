package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

@OptIn(ExperimentalUnsignedTypes::class)
class GpuEvidenceCliTest {
    @Test fun `cli disposes a created backend before returning a failing exit code`() {
        val events = mutableListOf<String>()
        val code = runner(FakeRuntime(events)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }
    @Test fun `cli opens executes then closes and disposes a returned backend`() {
        val events = mutableListOf<String>()
        val code = runner(FakeRuntime(events, returned = true)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli disposes after close throws`() {
        val closeEvents = mutableListOf<String>()
        assertEquals(1, runner(FakeRuntime(closeEvents, closeFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "close-session", "dispose"), closeEvents)
    }

    @Test fun `cli closes and disposes after open throws following runtime session creation`() {
        val openEvents = mutableListOf<String>()
        assertEquals(1, runner(FakeRuntime(openEvents, openFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), openEvents)
    }

    @Test fun `cli keeps primary execution failure when both cleanup hooks fail`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = IllegalStateException("dispose")
        val result = runner(FakeRuntime(events, returned = true, executionFails = true, closeFailure = closeFailure, disposeFailure = disposeFailure)).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertEquals("primary execution", assertNotNull(result.failure).message)
        assertEquals(listOf(closeFailure, disposeFailure), result.failure.suppressed.toList())
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli rethrows dispose Error after close Exception and retains close failure`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = LinkageError("fatal dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(disposeFailure, failure)
        assertEquals(listOf(closeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli rethrows close Error after dispose Exception and retains dispose failure`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(listOf(disposeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli de-duplicates the same cleanup failure instance`() {
        val events = mutableListOf<String>()
        val cleanupFailure = IllegalStateException("shared cleanup")
        var disposeThrew = false
        val result = runner(FakeRuntime(events, closeFailure = cleanupFailure, disposeFailure = cleanupFailure, onDisposeFailure = { disposeThrew = true })).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertSame(cleanupFailure, result.failure)
        assertEquals(true, disposeThrew)
        assertEquals(emptyList(), cleanupFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli does not duplicate a cleanup failure already reachable through the root cause`() {
        val events = mutableListOf<String>()
        val disposeFailure = IllegalStateException("dispose")
        val closeFailure = IllegalStateException("close", disposeFailure)
        val result = runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertSame(closeFailure, result.failure)
        assertSame(disposeFailure, closeFailure.cause)
        assertEquals(emptyList(), closeFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli snapshots dispose failure that has fatal close root as cause`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose", closeFailure)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(1, failure.suppressed.size)
        assertCycleAvoidanceSnapshot(failure.suppressed.single(), disposeFailure)
        assertSame(closeFailure, disposeFailure.cause)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli snapshots dispose failure that already suppresses fatal close root`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose")
        disposeFailure.addSuppressed(closeFailure)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(1, closeFailure.suppressed.size)
        assertCycleAvoidanceSnapshot(closeFailure.suppressed.single(), disposeFailure)
        assertEquals(listOf(closeFailure), disposeFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli keeps execution fatal as root and retains both cleanup failures in order`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = IllegalArgumentException("dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, returned = true, executionFatal = true, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertEquals("fatal execution", failure.message)
        assertEquals(listOf(closeFailure, disposeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli terminates failure graph traversal on an unrelated cause and suppressed cycle`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val cycleHead = IllegalStateException("cycle head")
        val cycleTail = IllegalStateException("cycle tail", cycleHead)
        cycleHead.addSuppressed(cycleTail)
        val disposeFailure = IllegalStateException("dispose", cycleHead)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertSame(disposeFailure, failure.suppressed.single())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates execution Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, returned = true, executionFatal = true)).run(validArgs())
        }
        assertEquals("fatal execution", failure.message)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates open Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, openFatal = true)).run(validArgs())
        }
        assertEquals("fatal open", failure.message)
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates cleanup Error after still attempting dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFatal = true)).run(validArgs())
        }
        assertEquals("fatal close", failure.message)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli does not absorb parser Error`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events), requestParser = { throw LinkageError("fatal parse") }).run(validArgs())
        }
        assertEquals("fatal parse", failure.message)
        assertEquals(emptyList(), events)
    }

    @Test fun `cli does not write generated evidence for an unexpected render refusal`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-refusal")

        assertEquals(1, runner(OutcomeRuntime(Outcome.UnexpectedRefusal)).run(validArgs(root)))
        assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
    }

    @Test fun `cli does not write generated evidence when rendered pixels fail comparison`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-comparison")
        val runtime = SurfaceComparisonRuntime()
        val evidenceCase = surfaceComparisonCase(runtime)

        assertEquals(1, GpuEvidenceCliRunner(runtime, cases = listOf(evidenceCase)).run(validArgs(root, "surface-comparison")))
        assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
    }

    @Test fun `cli dispatches a Surface program without preparing it through the backend`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-surface")
        val runtime = SurfaceRuntime()
        val surfaceCase = EvidenceCase(
            EvidenceSceneDescriptor(EvidenceSceneId("surface-cli"), "Surface CLI", "Surface dispatch contract.", 1, 1, 1L, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("literal-rgba", 1), ComparisonPolicy(0, 100.0, 1, "Exact literal RGBA8 oracle."), emptySet()),
            KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
                object : KanvasSurfaceRenderSession {
                    override fun render(): RenderResult {
                        runtime.observeSurfaceRender()
                        return RenderResult(ubyteArrayOf(0u, 0u, 0u, 0u), 1, 1, diagnostics = Diagnostics(), stats = RenderStats(1, 0, 1, 1, 1f))
                    }
                }
            }),
            CpuOracle { _, _ -> byteArrayOf(0, 0, 0, 0) },
        )

        assertEquals(1, GpuEvidenceCliRunner(runtime, cases = listOf(surfaceCase)).run(validArgs(root, "surface-cli")))
        assertEquals(1, runtime.surfaceRenders)
        assertEquals(0, runtime.prepareCalls)
    }

    private fun assertCycleAvoidanceSnapshot(snapshot: Throwable, original: Throwable) {
        val message = assertNotNull(snapshot.message)
        assertTrue(message.contains("failure snapshotted to avoid a cycle"))
        assertTrue(message.contains(original.javaClass.name))
        assertTrue(message.contains(original.message ?: "null"))
        assertNull(snapshot.cause)
        assertEquals(emptyList(), snapshot.suppressed.toList())
    }

    private fun runner(
        runtime: EvidenceRuntimePort,
        requestParser: (Array<String>) -> GpuEvidenceCliRequest = GpuEvidenceCliRequest::parse,
    ) = GpuEvidenceCliRunner(runtime, requestParser = requestParser, cases = GpuEvidenceCatalog.refusalCases)

    private fun surfaceComparisonCase(runtime: SurfaceComparisonRuntime) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("surface-comparison"),
            "Surface comparison",
            "Deterministic fake Surface pixels intentionally differ from the CPU oracle.",
            1,
            1,
            1L,
            emptySet(),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("literal-zero-rgba", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact fake comparison oracle."),
            emptySet(),
        ),
        KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
            object : KanvasSurfaceRenderSession {
                override fun render(): RenderResult {
                    runtime.observeSurfaceRender()
                    return RenderResult(
                        ubyteArrayOf(255u, 0u, 0u, 255u),
                        1,
                        1,
                        diagnostics = Diagnostics(),
                        stats = RenderStats(1, 0, 1, 1, 1f),
                    )
                }
            }
        }),
        CpuOracle { _, _ -> byteArrayOf(0, 0, 0, 0) },
    )

    private fun validArgs(root: java.nio.file.Path = Files.createTempDirectory("gpu-evidence-cli"), scene: String = "custom-runtime-effect-unregistered-refusal") = arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", scene)
    private class FakeRuntime(private val events: MutableList<String>, private val returned: Boolean = false, private val closeFails: Boolean = false, private val openFails: Boolean = false, private val executionFails: Boolean = false, private val executionFatal: Boolean = false, private val openFatal: Boolean = false, private val closeFatal: Boolean = false, private val closeFailure: Throwable? = null, private val disposeFailure: Throwable? = null, private val onDisposeFailure: (() -> Unit)? = null) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? { events += "open-session"; if (openFails || openFatal) { events += "runtime-session-created"; if (openFatal) throw LinkageError("fatal open") else error("primary open") }; return if (returned) object : EvidenceBackendPort { override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake"); override val deviceGeneration = 1L; override fun telemetry() = org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry(); override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation { events += "execute"; if (executionFatal) throw LinkageError("fatal execution"); if (executionFails) error("primary execution"); return EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "fake", emptyList()) }; override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable") } else null }
        override fun close() { events += "close-session"; closeFailure?.let { throw it }; if (closeFatal) throw LinkageError("fatal close"); if (closeFails) error("close") }
        override fun dispose() { events += "dispose"; disposeFailure?.let { onDisposeFailure?.invoke(); throw it } }
    }

    private enum class Outcome { UnexpectedRefusal, ComparisonFailure }

    private class OutcomeRuntime(private val outcome: Outcome) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake")
            override val deviceGeneration: Long = 1L
            private var submissions = 0L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = when (outcome) {
                Outcome.UnexpectedRefusal -> EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "unexpected refusal", emptyList())
                Outcome.ComparisonFailure -> EvidenceProgramPreparation.Recorded("product.fake", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
            }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
                override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                    submissions++
                    return EvidenceCompletedFrame.succeeded(program.readbackRequestId, ByteArray(width * height * 4))
                }
                override fun close() = Unit
            }
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class SurfaceRuntime : EvidenceRuntimePort {
        var prepareCalls = 0
        var surfaceRenders = 0
        private var submissions = 0L
        fun observeSurfaceRender() { surfaceRenders++; submissions++ }
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation { prepareCalls++; error("Surface program reached backend preparation") }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("Surface program reached prepared frame")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class SurfaceComparisonRuntime : EvidenceRuntimePort {
        private var submissions = 0L
        fun observeSurfaceRender() { submissions++ }
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = error("Surface program reached backend preparation")
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("Surface program reached prepared frame")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }
}
