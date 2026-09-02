package org.graphiks.kanvas.skia

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.skia.gm.composite.TestExtractAlphaGm
import org.graphiks.kanvas.surface.SceneRecordingScope
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkiaGmSceneCaptureTest {
    @Test
    fun `exhaustive W0 scene capture audit partitions every eligible GM`() {
        val report = captureEligibleGmScenes(SkiaGmRegistry.all())

        assertEquals(report.eligible.size, report.eligible.toSet().size, report.diagnosticSummary())
        assertEquals(report.eligible.size, report.outcomeNames.size, report.diagnosticSummary())
        assertEquals(report.eligible.toSet(), report.outcomeNames.toSet(), report.diagnosticSummary())
        assertTrue(report.setupBlocked.size <= 11, report.diagnosticSummary())
        assertTrue(report.captureInvalid.size <= 1, report.diagnosticSummary())
    }

    @Test
    fun `nonattempt W0 decisions skip GM setup and draw`() {
        val quarantined = object : SkiaGm {
            override val name: String = "jpg-color-cube"
            override val renderFamily: RenderFamily = RenderFamily.IMAGE
            override val renderCost: RenderCost = RenderCost.BLOCKING
            override val minSimilarity: Double = 0.0

            override fun onOnceBeforeDraw(canvas: GmCanvas) = error("quarantined GM setup ran")

            override fun draw(canvas: GmCanvas, width: Int, height: Int) =
                error("quarantined GM draw ran")
        }

        assertTrue(captureEligibleGmScenes(listOf(quarantined)).isComplete)
    }

    @Test
    fun `extractalpha capture retains its declared alpha image format`() {
        assertTrue(captureEligibleGmScenes(listOf(TestExtractAlphaGm())).isComplete)
    }
}

internal data class GmSceneCaptureFailure(
    val gmName: String,
    val detail: String,
)

/**
 * Separate recording-only execution failures from semantically invalid scene
 * captures. Callers can therefore report residual work without treating an
 * Invalid scene as if its GM never executed.
 */
internal data class GmSceneCaptureReport(
    val eligible: List<String>,
    val captured: List<String>,
    val setupBlocked: List<GmSceneCaptureFailure>,
    val captureInvalid: List<GmSceneCaptureFailure>,
) {
    val isComplete: Boolean get() = setupBlocked.isEmpty() && captureInvalid.isEmpty()
    val strictPassed: Boolean get() = isComplete
    val debtCount: Int get() = setupBlocked.size + captureInvalid.size
    val outcomeNames: List<String> get() = captured + setupBlocked.map { it.gmName } + captureInvalid.map { it.gmName }

    fun diagnosticSummary(): String = buildString {
        append("Scene capture report: Eligible=").append(eligible.size)
        append(", Captured=").append(captured.size)
        append(", SetupBlocked=").append(setupBlocked.size)
        append(", CaptureInvalid=").append(captureInvalid.size)
        if (!isComplete) {
            append('\n')
            setupBlocked.forEach { failure -> append("SetupBlocked ").append(failure.gmName).append(": ").append(failure.detail).append('\n') }
            captureInvalid.forEach { failure -> append("CaptureInvalid ").append(failure.gmName).append(": ").append(failure.detail).append('\n') }
        }
    }.trimEnd()
}

internal fun captureEligibleGmScenes(gms: List<SkiaGm>): GmSceneCaptureReport {
    val eligible = mutableListOf<String>()
    val captured = mutableListOf<String>()
    val setupBlocked = mutableListOf<GmSceneCaptureFailure>()
    val captureInvalid = mutableListOf<GmSceneCaptureFailure>()
    gms.forEach { gm ->
        val initialDecision = SkiaGmConformance.decisionFor(gm)
        if (!initialDecision.mustAttempt) return@forEach

        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)
        try {
            SceneRecordingScope.recordingOnly {
                canvas.drawRect(
                    RectF32(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
                    Paint(color = ColorARGB.White, antiAlias = false),
                )
                gm.onOnceBeforeDraw(canvas)
                gm.draw(canvas, gm.width, gm.height)
            }
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            eligible += gm.name
            setupBlocked += GmSceneCaptureFailure(gm.name, failure.message.orEmpty())
            return@forEach
        }

        val finalDecision = SkiaGmConformance.decisionFor(gm, canvas.observedExternalDependencies())
        if (!finalDecision.mustAttempt) return@forEach
        eligible += gm.name

        when (val result = surface.snapshotScene(GM_SCENE_CAPTURE_LIMITS)) {
            is SceneCaptureResult.Captured -> captured += gm.name
            is SceneCaptureResult.Invalid -> captureInvalid += GmSceneCaptureFailure(
                gm.name,
                result.diagnostics.joinToString { diagnostic -> "${diagnostic.code.value}: ${diagnostic.message}" },
            )
        }
    }
    return GmSceneCaptureReport(eligible, captured, setupBlocked, captureInvalid)
}

private val GM_SCENE_CAPTURE_LIMITS = SceneCaptureLimits(
    graphLimits = GraphLimits(maxNodes = 262_143),
    maxNodes = 262_143,
)
