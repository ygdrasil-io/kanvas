package org.graphiks.kanvas.gpu.renderer.telemetry

/** Budget tracking for a single rendering family. */
data class PerFamilyBudget(
    val familyName: String,
    val targetFps: Float,
    val warningFps: Float,
    val measuredMs: Float = 0f,
    val status: String = "unknown",
)

/** Evaluates per-family performance against a threshold. */
class PerformanceBudgetEvaluator {
    /** Compares measured FPS to threshold and returns the budget result. */
    fun evaluate(family: String, fps: Float, thresholdFps: Float): PerFamilyBudget {
        val status = when {
            fps >= thresholdFps -> "pass"
            fps >= thresholdFps * 0.5f -> "warning"
            else -> "fail"
        }
        return PerFamilyBudget(
            familyName = family,
            targetFps = thresholdFps,
            warningFps = thresholdFps * 0.5f,
            measuredMs = 1000f / fps,
            status = status,
        )
    }
}
