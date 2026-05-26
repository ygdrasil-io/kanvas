package org.skia.pipeline

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun autoVectorModeUsesScalarWhenBenchmarkGateIsRejected() {
        val result = assertIs<CpuExecutionResult.Success>(
            CpuScalarPipelineExecutor.execute(
                KanvasPipelineIR.demoSolidRectIr(Rgba(0.2f, 0.4f, 0.8f, 1f)),
                width = 16,
                height = 8,
            ),
        )

        assertEquals("cpu.scalar.solid_src_over_clear", result.kernelId)
        assertEquals(
            "Vector API rejected by benchmark gate: " +
                "decision=rejected speedup=0.863 requiredSpeedup=1.500 gate=solid_src_over_clear/java25/reference-v1",
            result.diagnostics.single(),
        )
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

    @Test
    fun solidRectVectorKernelMatchesScalarOutputWhenAvailable() {
        val color = Rgba(0.1f, 0.7f, 0.3f, 1f)
        val ir = KanvasPipelineIR.demoSolidRectIr(color)

        val scalar = assertIs<CpuExecutionResult.Success>(
            CpuScalarPipelineExecutor.execute(
                ir,
                width = 33,
                height = 5,
                options = CpuPipelineExecutionOptions(vectorMode = CpuVectorMode.Disabled),
            )
        )
        val vector = assertIs<CpuExecutionResult.Success>(
            CpuScalarPipelineExecutor.execute(
                ir,
                width = 33,
                height = 5,
                options = CpuPipelineExecutionOptions(vectorMode = CpuVectorMode.Force),
            )
        )

        assertContentEquals(scalar.pixels.argb8888, vector.pixels.argb8888)
        if (vector.kernelId == "java25.vector.solid_src_over_clear") {
            assertTrue(vector.diagnostics.any { it.startsWith("Vector API force-selected") })
        } else {
            assertEquals("cpu.scalar.solid_src_over_clear", vector.kernelId)
            assertTrue(vector.diagnostics.any { it.startsWith("Vector API unavailable") })
        }
    }

    @Test
    fun autoVectorModeCanSelectVectorOnlyWithAcceptedBenchmarkGate() {
        val previous = System.getProperty(CpuVectorSolidRectKernel.ACCEPTED_GATE_PROPERTY)
        System.setProperty(
            CpuVectorSolidRectKernel.ACCEPTED_GATE_PROPERTY,
            CpuVectorSolidRectKernel.ACCEPTED_GATE_ID,
        )
        try {
            val result = assertIs<CpuExecutionResult.Success>(
                CpuScalarPipelineExecutor.execute(
                    KanvasPipelineIR.demoSolidRectIr(Rgba(0.1f, 0.7f, 0.3f, 1f)),
                    width = 33,
                    height = 5,
                ),
            )

            if (result.kernelId == "java25.vector.solid_src_over_clear") {
                assertTrue(result.diagnostics.any { it.startsWith("Vector API selected") })
            } else {
                assertEquals("cpu.scalar.solid_src_over_clear", result.kernelId)
                assertTrue(result.diagnostics.any { it.startsWith("Vector API unavailable") })
            }
        } finally {
            if (previous == null) {
                System.clearProperty(CpuVectorSolidRectKernel.ACCEPTED_GATE_PROPERTY)
            } else {
                System.setProperty(CpuVectorSolidRectKernel.ACCEPTED_GATE_PROPERTY, previous)
            }
        }
    }

    @Test
    fun vectorRuntimeGateFallsBackToScalarWithStableDiagnostic() {
        val previous = System.getProperty(CpuVectorSolidRectKernel.ENABLED_PROPERTY)
        System.setProperty(CpuVectorSolidRectKernel.ENABLED_PROPERTY, "false")
        try {
            val result = assertIs<CpuExecutionResult.Success>(
                CpuScalarPipelineExecutor.execute(
                    KanvasPipelineIR.demoSolidRectIr(Rgba(0.2f, 0.4f, 0.6f, 1f)),
                    width = 8,
                    height = 8,
                    options = CpuPipelineExecutionOptions(vectorMode = CpuVectorMode.Force),
                )
            )

            assertEquals("cpu.scalar.solid_src_over_clear", result.kernelId)
            assertEquals(
                "Vector API disabled by system property kanvas.cpu.vector.enabled=false",
                result.diagnostics.single(),
            )
        } finally {
            if (previous == null) {
                System.clearProperty(CpuVectorSolidRectKernel.ENABLED_PROPERTY)
            } else {
                System.setProperty(CpuVectorSolidRectKernel.ENABLED_PROPERTY, previous)
            }
        }
    }
}
