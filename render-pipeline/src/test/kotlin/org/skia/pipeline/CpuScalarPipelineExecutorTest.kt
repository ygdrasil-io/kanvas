package org.skia.pipeline

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CpuScalarPipelineExecutorTest {
    @Test
    fun solidRectPipelineMatchesLegacyPath() {
        val color = Rgba(0.2f, 0.4f, 0.8f, 1f)
        val ir = KanvasPipelineIR.demoSolidRectIr(color)

        val result = CpuScalarPipelineExecutor.execute(ir, width = 16, height = 8)
        val success = assertIs<CpuExecutionResult.Success>(result)
        val legacy = CpuScalarPipelineExecutor.legacySolidRect(16, 8, color)
        assertContentEquals(legacy.argb8888, success.pixels.argb8888)
    }

    @Test
    fun linearGradientPipelineMatchesLegacyPath() {
        val payload = LinearGradientPayload(
            start = Point(0f, 0f),
            end = Point(15f, 0f),
            startColor = Rgba(1f, 0f, 0f, 1f),
            endColor = Rgba(0f, 0f, 1f, 1f),
        )
        val ir = KanvasPipelineIR.demoLinearGradientRectIr(
            start = payload.start,
            end = payload.end,
            startColor = payload.startColor,
            endColor = payload.endColor,
        )

        val result = CpuScalarPipelineExecutor.execute(ir, width = 16, height = 4)
        val success = assertIs<CpuExecutionResult.Success>(result)
        val legacy = CpuScalarPipelineExecutor.legacyLinearGradientRect(16, 4, payload)
        assertContentEquals(legacy.argb8888, success.pixels.argb8888)
    }

    @Test
    fun explicitFallbackPlanReturnsDiagnostic() {
        val ir = KanvasPipelineIR.builder()
            .append(PipelineOp.SeedDeviceCoords)
            .setFallback(FallbackPlan.CpuShadeRow("legacy shader required"))
            .build()

        val result = CpuScalarPipelineExecutor.execute(ir, width = 4, height = 4)
        val fallback = assertIs<CpuExecutionResult.LegacyFallback>(result)
        assertEquals("Explicit fallback: legacy shader required", fallback.reason)
    }
}
