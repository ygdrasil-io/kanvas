package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameGatePolicyTest {

    private val policy = FrameGatePolicy()

    @Test
    fun `default policy targets 60fps and warns at 30fps`() {
        assertEquals(60, policy.targetFps)
        assertEquals(30, policy.warnFps)
        assertEquals(1000.0 / 60.0, policy.targetFrameMs, 0.0001)
        assertEquals(1000.0 / 30.0, policy.warnFrameMs, 0.0001)
    }

    @Test
    fun `frame at or above 60fps passes`() {
        val result = policy.evaluate("FillRect", 10.0)
        assertEquals(FrameGateStatus.Pass, result.status)
        assertEquals(100.0, result.fps, 0.0001)
        assertFalse(result.quarantined)
    }

    @Test
    fun `frame exactly at the 60fps budget passes`() {
        val result = policy.evaluate("FillRect", 1000.0 / 60.0)
        assertEquals(FrameGateStatus.Pass, result.status)
    }

    @Test
    fun `frame between 60fps and 30fps warns`() {
        val result = policy.evaluate("Blur", 25.0)
        assertEquals(FrameGateStatus.Warn, result.status)
        assertFalse(result.quarantined)
    }

    @Test
    fun `frame exactly at the 30fps threshold warns`() {
        val result = policy.evaluate("Blur", 1000.0 / 30.0)
        assertEquals(FrameGateStatus.Warn, result.status)
    }

    @Test
    fun `frame below 30fps is quarantined`() {
        val result = policy.evaluate("Text", 40.0)
        assertEquals(FrameGateStatus.Quarantine, result.status)
        assertTrue(result.quarantined)
    }

    @Test
    fun `evaluate rejects non-positive frame time`() {
        assertFailsWith<IllegalArgumentException> { policy.evaluate("FillRect", 0.0) }
    }

    @Test
    fun `evaluate rejects blank family`() {
        assertFailsWith<IllegalArgumentException> { policy.evaluate(" ", 10.0) }
    }

    @Test
    fun `report flags quarantine when any family is below 30fps`() {
        val report = policy.evaluateAll(
            listOf(
                "FillRect" to 8.0,
                "Blur" to 25.0,
                "Text" to 50.0,
            ),
        )
        assertTrue(report.anyQuarantined)
        assertEquals(3, report.results.size)
        assertEquals("Apple M-series", report.hardwareBaseline)
        assertFalse(report.productActivation)
    }

    @Test
    fun `report does not flag quarantine when all families pass or warn`() {
        val report = policy.evaluateAll(
            listOf(
                "FillRect" to 8.0,
                "Blur" to 25.0,
            ),
        )
        assertFalse(report.anyQuarantined)
    }

    @Test
    fun `report json contains baseline targets and per-family status`() {
        val report = policy.evaluateAll(listOf("Text" to 50.0))
        val json = report.toJson()
        assertTrue(json.contains("\"hardwareBaseline\": \"Apple M-series\""))
        assertTrue(json.contains("\"targetFps\": 60"))
        assertTrue(json.contains("\"warnFps\": 30"))
        assertTrue(json.contains("\"anyQuarantined\": true"))
        assertTrue(json.contains("\"productActivation\": false"))
        assertTrue(json.contains("\"family\": \"Text\""))
        assertTrue(json.contains("\"status\": \"quarantine\""))
    }

    @Test
    fun `report dump lines include a visible quarantine diagnostic`() {
        val report = policy.evaluateAll(listOf("Text" to 50.0))
        val lines = report.dumpLines()
        assertTrue(lines.any { it.contains("frame-gate family=Text") && it.contains("status=quarantine") })
        assertTrue(lines.any { it.contains("no-product-activation") })
    }
}
