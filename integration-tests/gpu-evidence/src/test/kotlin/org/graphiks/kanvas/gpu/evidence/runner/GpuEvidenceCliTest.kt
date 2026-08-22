package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GpuEvidenceCliTest {
    @Test fun `cli disposes a created backend before returning a failing exit code`() {
        val events = mutableListOf<String>()
        val code = GpuEvidenceCliRunner(FakeRuntime(events)).run(arrayOf("--repository-root", Files.createTempDirectory("gpu-evidence-cli").toString(), "--source-commit", "a".repeat(40)))
        assertEquals(1, code)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }
    private class FakeRuntime(private val events: MutableList<String>) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? { events += "open-session"; return null }
        override fun close() { events += "close-session" }
        override fun dispose() { events += "dispose" }
    }
}
