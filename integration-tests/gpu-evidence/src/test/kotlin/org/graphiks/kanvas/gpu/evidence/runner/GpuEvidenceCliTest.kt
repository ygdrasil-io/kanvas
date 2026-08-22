package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GpuEvidenceCliTest {
    @Test fun `cli disposes a created backend before returning a failing exit code`() {
        val events = mutableListOf<String>()
        val code = GpuEvidenceCliRunner(FakeRuntime(events)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }
    @Test fun `cli opens executes then closes and disposes a returned backend`() {
        val events = mutableListOf<String>()
        val code = GpuEvidenceCliRunner(FakeRuntime(events, returned = true)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli disposes after close throws`() {
        val closeEvents = mutableListOf<String>()
        assertEquals(1, GpuEvidenceCliRunner(FakeRuntime(closeEvents, closeFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "close-session", "dispose"), closeEvents)
    }

    @Test fun `cli closes and disposes after open throws following runtime session creation`() {
        val openEvents = mutableListOf<String>()
        assertEquals(1, GpuEvidenceCliRunner(FakeRuntime(openEvents, openFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), openEvents)
    }

    @Test fun `cli keeps primary execution failure when cleanup also fails`() {
        val events = mutableListOf<String>()
        val result = GpuEvidenceCliRunner(FakeRuntime(events, returned = true, executionFails = true, closeFails = true)).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertEquals("primary execution", assertNotNull(result.failure).message)
        assertEquals(listOf("close"), result.failure.suppressed.map { it.message })
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates execution Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            GpuEvidenceCliRunner(FakeRuntime(events, returned = true, executionFatal = true)).run(validArgs())
        }
        assertEquals("fatal execution", failure.message)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates open Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            GpuEvidenceCliRunner(FakeRuntime(events, openFatal = true)).run(validArgs())
        }
        assertEquals("fatal open", failure.message)
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates cleanup Error after still attempting dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            GpuEvidenceCliRunner(FakeRuntime(events, closeFatal = true)).run(validArgs())
        }
        assertEquals("fatal close", failure.message)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli does not absorb parser Error`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            GpuEvidenceCliRunner(FakeRuntime(events), requestParser = { throw LinkageError("fatal parse") }).run(validArgs())
        }
        assertEquals("fatal parse", failure.message)
        assertEquals(emptyList(), events)
    }

    private fun validArgs() = arrayOf("--repository-root", Files.createTempDirectory("gpu-evidence-cli").toString(), "--source-commit", "a".repeat(40), "--scene", "solid-card-stack")
    private class FakeRuntime(private val events: MutableList<String>, private val returned: Boolean = false, private val closeFails: Boolean = false, private val openFails: Boolean = false, private val executionFails: Boolean = false, private val executionFatal: Boolean = false, private val openFatal: Boolean = false, private val closeFatal: Boolean = false) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? { events += "open-session"; if (openFails || openFatal) { events += "runtime-session-created"; if (openFatal) throw LinkageError("fatal open") else error("primary open") }; return if (returned) object : EvidenceBackendPort { override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake"); override val deviceGeneration = 1L; override fun telemetry() = org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry(); override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation { events += "execute"; if (executionFatal) throw LinkageError("fatal execution"); if (executionFails) error("primary execution"); return EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "fake", emptyList()) }; override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable") } else null }
        override fun close() { events += "close-session"; if (closeFatal) throw LinkageError("fatal close"); if (closeFails) error("close") }
        override fun dispose() { events += "dispose" }
    }
}
