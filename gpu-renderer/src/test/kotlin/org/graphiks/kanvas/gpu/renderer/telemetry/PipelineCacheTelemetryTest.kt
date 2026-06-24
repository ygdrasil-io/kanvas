package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PipelineCacheTelemetryTest {

    @Test
    fun `pipeline cache telemetry computes hit rate`() {
        val t = GPUPipelineCacheTelemetry(
            sceneId = "test-scene",
            hitCount = 80,
            missCount = 20,
            evictionCount = 5,
            moduleCount = 12,
        )
        assertEquals(0.8, t.hitRate, 0.001)
    }

    @Test
    fun `pipeline cache telemetry hit rate is zero when no requests`() {
        val t = GPUPipelineCacheTelemetry(
            sceneId = "empty-scene",
            hitCount = 0,
            missCount = 0,
            evictionCount = 0,
            moduleCount = 0,
        )
        assertEquals(0.0, t.hitRate)
    }

    @Test
    fun `pipeline cache telemetry rejects blank sceneId`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPipelineCacheTelemetry(
                sceneId = "",
                hitCount = 10,
                missCount = 5,
                evictionCount = 0,
                moduleCount = 3,
            )
        }
    }

    @Test
    fun `pipeline cache telemetry rejects negative counts`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPipelineCacheTelemetry(
                sceneId = "bad",
                hitCount = -1,
                missCount = 0,
                evictionCount = 0,
                moduleCount = 0,
            )
        }
    }

    @Test
    fun `pipeline cache telemetry dump line contains all fields`() {
        val t = GPUPipelineCacheTelemetry(
            sceneId = "demo-scene",
            hitCount = 100,
            missCount = 25,
            evictionCount = 3,
            moduleCount = 8,
        )
        val line = t.dumpLine()
        assertTrue(line.contains("pipeline-cache"))
        assertTrue(line.contains("scene=demo-scene"))
        assertTrue(line.contains("hitCount=100"))
        assertTrue(line.contains("missCount=25"))
        assertTrue(line.contains("hitRate="))
        assertTrue(line.contains("evictionCount=3"))
        assertTrue(line.contains("moduleCount=8"))
    }

    @Test
    fun `ledger records pipeline cache telemetry`() {
        val t = GPUPipelineCacheTelemetry(
            sceneId = "ledger-test",
            hitCount = 50,
            missCount = 10,
            evictionCount = 2,
            moduleCount = 6,
        )
        val ledger = GPUTelemetryLedger.empty().recordPipelineCacheTelemetry(t)
        assertEquals(1, ledger.pipelineCacheTelemetry.size)
        assertEquals(50, ledger.pipelineCacheTelemetry.first().hitCount)
    }

    @Test
    fun `ledger records multiple pipeline cache telemetry snapshots`() {
        val t1 = GPUPipelineCacheTelemetry("scene-a", 10, 2, 1, 3)
        val t2 = GPUPipelineCacheTelemetry("scene-b", 20, 5, 2, 4)
        val ledger = GPUTelemetryLedger.empty()
            .recordPipelineCacheTelemetry(t1)
            .recordPipelineCacheTelemetry(t2)
        assertEquals(2, ledger.pipelineCacheTelemetry.size)
    }

    @Test
    fun `ledger pipeline cache dump lines contain snapshot entries`() {
        val t = GPUPipelineCacheTelemetry("dump-scene", 30, 10, 1, 5)
        val ledger = GPUTelemetryLedger.empty().recordPipelineCacheTelemetry(t)
        val lines = ledger.pipelineCacheTelemetryDumpLines()
        assertEquals(1, lines.size)
        assertTrue(lines.first().contains("scene=dump-scene"))
    }

    @Test
    fun `empty ledger has no pipeline cache dump lines`() {
        val ledger = GPUTelemetryLedger.empty()
        assertTrue(ledger.pipelineCacheTelemetryDumpLines().isEmpty())
    }
}
