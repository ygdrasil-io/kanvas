package org.skia.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeometryCoverageMigrationHarnessTest {
    private val rect = FloatRect(0f, 0f, 8f, 8f)
    private val color = Rgba(0.2f, 0.4f, 0.8f, 1f)

    @Test
    fun `cpu rect shadow mode leaves legacy pixels unchanged and dumps descriptor`() {
        val result = GeometryCoverageMigrationHarness.shadowAxisAlignedFilledRect(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            artifactPath = "artifacts/gra-32/shadow-rect.txt",
        )

        val legacy = CpuScalarPipelineExecutor.legacySolidRect(8, 8, color)
        assertTrue(result.currentPixels.argb8888.contentEquals(legacy.argb8888))

        val dump = result.dump()
        assertTrue(dump.contains("mode=Shadow"))
        assertTrue(dump.contains("backend=CPU"))
        assertTrue(dump.contains("drawKind=axis-aligned-filled-rect"))
        assertTrue(dump.contains("ctm=1.0,0.0,0.0;0.0,1.0,0.0;0.0,0.0,1.0"))
        assertTrue(dump.contains("transformFacts=axisAligned=true,hasPerspective=false,maxScale=1.0,invertible=true"))
        assertTrue(dump.contains("clip=None"))
        assertTrue(dump.contains("geometry=Supported(Rect(device=0.0,0.0,8.0,8.0),clip=None)"))
        assertTrue(dump.contains("coverage=AnalyticRect(0.0,0.0,8.0,8.0,aa=false)"))
        assertTrue(dump.contains("lowering=CoverageModel.AnalyticRect(0.0,0.0,8.0,8.0,aa=false)"))
        assertTrue(dump.contains("currentRoute=kanvas-skia.current.draw-rect"))
        assertTrue(dump.contains("descriptorRoute=descriptor.shadow-only"))
        assertTrue(dump.contains("diff=not-compared(artifactPath=artifacts/gra-32/shadow-rect.txt)"))
    }

    @Test
    fun `compare mode records pass and artifact path for cpu rect fixture`() {
        val result = GeometryCoverageMigrationHarness.compareAxisAlignedFilledRect(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            artifactPath = "artifacts/gra-32/compare-rect.json",
        )

        assertTrue(result.diffSummary.compared)
        assertEquals(true, result.diffSummary.passed)
        assertEquals(0, result.diffSummary.differingPixels)
        assertEquals(0, result.diffSummary.maxChannelDelta)
        assertEquals("artifacts/gra-32/compare-rect.json", result.diffSummary.artifactPath)
        assertTrue(result.dump().contains("diff=compared(passed=true,width=8,height=8,differingPixels=0,maxChannelDelta=0,artifactPath=artifacts/gra-32/compare-rect.json)"))
        assertTrue(result.dump().contains("metrics=touchedPixels=64,scalarVectorStatus=scalar-analytic-rect,kernelId=cpu.scalar.analytic_rect_src_over_clear,fallbackReason=none"))
    }

    @Test
    fun `compare mode records aa descriptor coverage flag for cpu rect fixture`() {
        val result = GeometryCoverageMigrationHarness.compareAxisAlignedFilledRect(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            artifactPath = "artifacts/gra-33/compare-aa-rect.json",
            antiAlias = true,
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.dump().contains("coverage=AnalyticRect(0.0,0.0,8.0,8.0,aa=true)"))
    }

    @Test
    fun `materialized rrect coverage compares against oracle pixels`() {
        val coverage = ByteArray(16) { index ->
            if (index % 5 == 0) 0 else 255.toByte()
        }
        val oracle = PixelBuffer(
            width = 4,
            height = 4,
            argb8888 = IntArray(16) { index -> if (index % 5 == 0) 0x00000000 else 0xFF000000.toInt() },
        )
        val result = GeometryCoverageMigrationHarness.compareMaterializedRRectCoverageAgainstOracle(
            width = 4,
            height = 4,
            rrect = RRectSpec(
                bounds = FloatRect(0f, 0f, 4f, 4f),
                topLeftRadius = Point(1f, 1f),
                topRightRadius = Point(1f, 1f),
                bottomRightRadius = Point(1f, 1f),
                bottomLeftRadius = Point(1f, 1f),
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-33/compare-rrect.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertTrue(result.dump().contains("drawKind=axis-aligned-filled-rrect"))
        assertTrue(result.dump().contains("geometry=Supported(RRect(bounds=0.0,0.0,4.0,4.0),clip=None)"))
        assertTrue(result.dump().contains("coverage=AlphaMask(ref=fixture.rrect.a8,bounds=0,0,4,4,format=A8)"))
        assertTrue(result.dump().contains("lowering=CoverageModel.AlphaMask(0,0,4,4,format=A8)"))
        assertTrue(result.dump().contains("metrics=touchedPixels=12,scalarVectorStatus=scalar-materialized-mask,kernelId=cpu.scalar.materialized_a8_src_over_clear,fallbackReason=none"))
    }

    @Test
    fun `gated mode keeps descriptor route off by default`() {
        val decision = GeometryCoverageMigrationHarness.gateDecision(
            primitive = DescriptorPrimitiveFamily.AxisAlignedFilledRect,
            backend = BackendKind.CPU,
        )

        assertFalse(decision.descriptorEnabled)
        assertEquals("kanvas-skia.current.draw-rect", decision.selectedRoute.id)
        assertEquals("cpu.descriptor.coverage-plan.solid-rect", decision.descriptorRoute.id)
        assertTrue(decision.dump().contains("mode=Gated"))
        assertTrue(decision.dump().contains("descriptorEnabled=false"))
    }

    @Test
    fun `gated mode can explicitly select descriptor route`() {
        val decision = GeometryCoverageMigrationHarness.gateDecision(
            primitive = DescriptorPrimitiveFamily.AxisAlignedFilledRect,
            backend = BackendKind.CPU,
            gates = listOf(
                DescriptorMigrationGate(
                    primitive = DescriptorPrimitiveFamily.AxisAlignedFilledRect,
                    backend = BackendKind.CPU,
                    enabled = true,
                ),
            ),
        )

        assertTrue(decision.descriptorEnabled)
        assertEquals("cpu.descriptor.coverage-plan.solid-rect", decision.selectedRoute.id)
    }

    @Test
    fun `unsupported descriptor path reports stable diagnostic`() {
        val diagnostic = GeometryCoverageMigrationHarness.unsupportedDescriptorDiagnostic(
            backend = BackendKind.CPU,
            reason = StandardCoverageReason.EdgeCountExceeded,
        )

        val dump = diagnostic.dump()
        assertTrue(dump.contains("DescriptorMigrationDiagnostic(v1)"))
        assertTrue(dump.contains("coverage=Unsupported(reason=coverage.edge-count-exceeded)"))
        assertTrue(dump.contains("lowering=Strategy.UnsupportedFallback(reason=coverage.edge-count-exceeded,fallback=coverage.edge-count-exceeded)"))
        assertTrue(dump.contains("fallback=RefuseDiagnostic(reason=coverage.edge-count-exceeded,action=coverage.edge-count-exceeded)"))
    }

    @Test
    fun `unsupported geometry path reports stable diagnostic`() {
        val diagnostic = GeometryCoverageMigrationHarness.unsupportedGeometryDiagnostic(
            backend = BackendKind.CPU,
            primitive = DescriptorPrimitiveFamily.AxisAlignedFilledRect,
            reason = StandardGeometryReason.NonFiniteInput,
        )

        val dump = diagnostic.dump()
        assertTrue(dump.contains("geometry=Unsupported(reason=geometry.nonfinite-input)"))
        assertTrue(dump.contains("coverage=Unsupported(reason=coverage.span-runs-unsupported)"))
        assertTrue(dump.contains("descriptorRoute=cpu.descriptor.coverage-plan.solid-rect"))
    }

    @Test
    fun `gpu rect shadow fixture emits descriptor dump without enabling gpu route`() {
        val result = GeometryCoverageMigrationHarness.shadowAxisAlignedFilledRect(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            backend = BackendKind.GPU,
        )

        assertNotNull(result.currentPixels)
        val dump = result.dump()
        assertTrue(dump.contains("backend=GPU"))
        assertTrue(dump.contains("currentRoute=gpu.current.handwritten-solid-rect"))
        assertTrue(dump.contains("descriptorRoute=gpu.shadow.generated-rect-candidate"))
        assertTrue(dump.contains("diff=not-compared(artifactPath=none)"))
    }
}
