package org.skia.kadre.runtime

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class M82KadreInputResizeRuntimeLoopTest {
    @Test
    fun deterministicFixtureRecordsInputResizeSceneStateAndTelemetry() {
        val evidence = buildM82KadreInputResizeRuntimeLoopEvidence()
        val result = evidence.deterministicResult
        val json = evidence.toJson()

        assertEquals("m82-deterministic-input-resize-live-state", result.fixtureId)
        assertEquals("pass", result.status)
        assertEquals(12, result.telemetry.eventCount)
        assertEquals(3, result.telemetry.frameTickCount)
        assertEquals(2, result.telemetry.pointerEventCount)
        assertEquals(3, result.telemetry.keyboardEventCount)
        assertEquals(1, result.telemetry.resizeEventCount)
        assertEquals(1, result.telemetry.scaleFactorEventCount)
        assertEquals(1, result.telemetry.closeEventCount)
        assertEquals(1, result.telemetry.unsupportedEventCount)
        assertEquals(2, result.telemetry.reconfigureCount)
        assertEquals(0, result.telemetry.reconfigureFailureCount)
        assertEquals(1, result.telemetry.droppedFrameCount)
        assertEquals(1, result.telemetry.hostDiagnosticCount)
        assertEquals(800, result.finalSurface.width)
        assertEquals(500, result.finalSurface.height)
        assertEquals(2.0, result.finalSurface.scaleFactor)
        assertEquals(2, result.finalSurface.resourceGeneration)
        assertEquals(false, result.finalSceneState.playing)
        assertEquals(false, result.finalSceneState.overlayVisible)
        assertEquals(1, result.finalSceneState.resetCount)
        assertEquals(true, result.finalSceneState.closeRequested)
        assertEquals(640.0, result.finalSceneState.pointerX)
        assertEquals(250.0, result.finalSceneState.pointerY)

        assertContains(json, "\"packId\": \"m82-kadre-input-resize-runtime-loop-v1\"")
        assertContains(json, "\"WindowEvent.Resized\"")
        assertContains(json, "\"WindowEvent.ScaleFactorChanged\"")
        assertContains(json, "\"WindowEvent.PointerMoved\"")
        assertContains(json, "\"WindowEvent.KeyboardInput\"")
        assertContains(json, "\"reason\": \"m82.surface-reconfigured.resize\"")
        assertContains(json, "\"reason\": \"m82.surface-reconfigured.scale-factor\"")
        assertContains(json, "\"droppedFrameCount\": 1")
        assertContains(json, "\"nativeOsEventInjectionClaimed\": false")
        assertContains(json, "CI does not synthesize real desktop OS pointer")
    }

    @Test
    fun refusalFixtureKeepsStableUnsupportedReasons() {
        val evidence = buildM82KadreInputResizeRuntimeLoopEvidence()
        val result = evidence.refusalResult
        val json = result.toJson("  ")

        assertEquals("expected-unsupported", result.status)
        assertEquals(3, result.telemetry.eventCount)
        assertEquals(0, result.telemetry.reconfigureCount)
        assertEquals(2, result.telemetry.reconfigureFailureCount)
        assertEquals(3, result.telemetry.hostDiagnosticCount)
        assertEquals(640, result.finalSurface.width)
        assertEquals(420, result.finalSurface.height)
        assertContains(json, "\"reason\": \"m82.resize.invalid-surface-size\"")
        assertContains(json, "\"reason\": \"m82.scale-factor.invalid\"")
        assertContains(json, "\"reason\": \"m82.kadre-event-family-unsupported\"")
        assertContains(json, "\"kadreEvent\": \"DeviceEvent.Motion\"")
    }

    @Test
    fun markdownNamesControlsAndValidationPath() {
        val markdown = buildM82KadreInputResizeRuntimeLoopEvidence().toMarkdown()

        assertContains(markdown, "Pointer move")
        assertContains(markdown, "`Space`")
        assertContains(markdown, "`O`")
        assertContains(markdown, "`R`")
        assertContains(markdown, "rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM82InputResizeRuntimeLoop")
        assertContains(markdown, "Dropped-frame telemetry is reporting-only")
    }
}
