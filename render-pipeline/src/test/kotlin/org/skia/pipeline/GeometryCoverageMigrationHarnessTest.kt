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
        assertEquals(64, result.metrics.touchedPixels)
        assertEquals(CpuAnalyticRectCoverageExecutor.KERNEL_ID, result.metrics.kernelId)
        assertEquals("scalar-analytic-rect", result.metrics.scalarVectorStatus)
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
        assertEquals("scalar-analytic-rect-aa", result.metrics.scalarVectorStatus)
        assertEquals(CpuAnalyticRectCoverageExecutor.KERNEL_ID, result.metrics.kernelId)
    }

    @Test
    fun `shared analytic rect coverage executor uses skia non aa edge rules`() {
        val visited = mutableListOf<Pair<Int, Int>>()
        val metrics = CpuAnalyticRectCoverageExecutor.execute(
            CoverageModel.AnalyticRect(bounds = FloatRect(1.25f, 1.25f, 3.25f, 3.25f), aa = false),
            clip = IntRect(0, 0, 5, 5),
        ) { x, y, coverage ->
            assertEquals(1f, coverage)
            visited += x to y
        }

        assertEquals(4, metrics.touchedPixels)
        assertEquals(CpuAnalyticRectCoverageExecutor.KERNEL_ID, metrics.kernelId)
        assertEquals(listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2), visited)
    }

    @Test
    fun `shared analytic rect coverage executor reports aa fractional coverage`() {
        val visited = mutableMapOf<Pair<Int, Int>, Float>()
        val metrics = CpuAnalyticRectCoverageExecutor.execute(
            CoverageModel.AnalyticRect(bounds = FloatRect(1.25f, 1.5f, 2.75f, 2.5f), aa = true),
            clip = IntRect(0, 0, 5, 5),
        ) { x, y, coverage ->
            visited[x to y] = coverage
        }

        assertEquals(4, metrics.touchedPixels)
        assertEquals("scalar-analytic-rect-aa", metrics.scalarVectorStatus)
        assertEquals(0.375f, visited.getValue(1 to 1))
        assertEquals(0.375f, visited.getValue(2 to 1))
        assertEquals(0.375f, visited.getValue(1 to 2))
        assertEquals(0.375f, visited.getValue(2 to 2))
    }

    @Test
    fun `cpu rect compare records device rect clip interaction`() {
        val oracle = CpuScalarPipelineExecutor.legacySolidRect(8, 8, color)
        val result = GeometryCoverageMigrationHarness.compareAxisAlignedFilledRectAgainstOracle(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            oraclePixels = oracle,
            artifactPath = "artifacts/gra-37/rect-clip.json",
            antiAlias = false,
            clip = ClipInteraction.DeviceRect(IntRect(1, 1, 7, 7)),
        )

        assertEquals(true, result.diffSummary.passed)
        val dump = result.dump()
        assertTrue(dump.contains("clip=DeviceRect(1,1,7,7)"))
        assertTrue(dump.contains("geometry=Supported(Rect(device=0.0,0.0,8.0,8.0),clip=DeviceRect(1,1,7,7))"))
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
    fun `cpu rrect compare records analytic rrect path clip interaction and alpha mask ref`() {
        val coverage = ByteArray(16) { 255.toByte() }
        val oracle = PixelBuffer(
            width = 4,
            height = 4,
            argb8888 = IntArray(16) { 0xFF000000.toInt() },
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
            artifactPath = "artifacts/gra-37/rrect-analytic-clip.json",
            clip = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(0f, 0f, 4f, 4f), kind = "rrect-intersect"),
            ),
        )

        val dump = result.dump()
        assertEquals(true, result.diffSummary.passed)
        assertTrue(dump.contains("clip=AnalyticShape(rrect-intersect)"))
        assertTrue(dump.contains("clip=AnalyticShape(rrect-intersect,0.0,0.0,4.0,4.0)"))
        assertTrue(dump.contains("coverage=AlphaMask(ref=fixture.rrect.a8,bounds=0,0,4,4,format=A8)"))
    }

    @Test
    fun `path coverage compare records fill type inverse flag and counters`() {
        val coverage = ByteArray(16) { index ->
            if (index == 0 || index == 15) 0 else 255.toByte()
        }
        val oracle = PixelBuffer(
            width = 4,
            height = 4,
            argb8888 = IntArray(16) { index -> if (index == 0 || index == 15) 0x00000000 else 0xFF000000.toInt() },
        )

        val result = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = 4,
            height = 4,
            fixture = PathCoverageFixture(
                bounds = FloatRect(0f, 0f, 4f, 4f),
                fillType = PathFillType.EvenOdd,
                inverse = true,
                antiAlias = true,
                verbCount = 10,
                edgeCount = 8,
                segmentCount = 8,
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-35/path-coverage-even-odd-inverse.json",
        )

        assertEquals(true, result.diffSummary.passed)
        val dump = result.dump()
        assertTrue(dump.contains("drawKind=simple-filled-path"))
        assertTrue(dump.contains("geometry=Supported(Path(fillType=EvenOdd,stroke=false,verbs=10),clip=None)"))
        assertTrue(dump.contains("coverage=PathCoverage(fillType=EvenOdd,aa=true,inverse=true)"))
        assertTrue(dump.contains("lowering=Strategy.CpuSpanPath(fillType=EvenOdd,aa=true,inverse=true)"))
        assertTrue(dump.contains("fallback=strategy.CpuSpanPath"))
        assertTrue(dump.contains("metrics=touchedPixels=14,scalarVectorStatus=scalar-path-coverage,kernelId=cpu.scalar.path_coverage_src_over_clear,fallbackReason=none,pathVerbCount=10,edgeCount=8,segmentCount=8"))
    }

    @Test
    fun `stroke outline path coverage compare records stroke metadata and counters`() {
        val coverage = ByteArray(9) { index -> if (index % 2 == 0) 255.toByte() else 0 }
        val oracle = PixelBuffer(
            width = 3,
            height = 3,
            argb8888 = IntArray(9) { index -> if (index % 2 == 0) 0xFF000000.toInt() else 0x00000000 },
        )

        val result = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = 3,
            height = 3,
            fixture = PathCoverageFixture(
                bounds = FloatRect(0f, 0f, 3f, 3f),
                fillType = PathFillType.Winding,
                inverse = false,
                antiAlias = true,
                verbCount = 7,
                edgeCount = 6,
                segmentCount = 6,
                stroke = StrokePlan(width = 4f, miterLimit = 4f),
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-35/stroke-outline-path-coverage.json",
        )

        assertEquals(true, result.diffSummary.passed)
        val dump = result.dump()
        assertTrue(dump.contains("drawKind=stroke-outline-path"))
        assertTrue(dump.contains("currentRoute=kanvas-skia.current.stroke-path"))
        assertTrue(dump.contains("descriptorRoute=cpu.descriptor.coverage-plan.stroke-outline"))
        assertTrue(dump.contains("geometry=Supported(Path(fillType=Winding,stroke=true,verbs=7),clip=None)"))
        assertTrue(dump.contains("coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)"))
        assertTrue(dump.contains("metrics=touchedPixels=5,scalarVectorStatus=scalar-path-coverage,kernelId=cpu.scalar.stroke_outline_path_coverage_src_over_clear,fallbackReason=none,pathVerbCount=7,edgeCount=6,segmentCount=6"))
    }

    @Test
    fun `cpu path compare records aaclip ref and difference clip diagnostics`() {
        val coverage = ByteArray(4) { 255.toByte() }
        val oracle = PixelBuffer(
            width = 2,
            height = 2,
            argb8888 = IntArray(4) { 0xFF000000.toInt() },
        )
        val aaClipResult = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = 2,
            height = 2,
            fixture = PathCoverageFixture(
                bounds = FloatRect(0f, 0f, 2f, 2f),
                fillType = PathFillType.Winding,
                inverse = false,
                antiAlias = true,
                verbCount = 5,
                edgeCount = 4,
                segmentCount = 4,
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-37/aaclip-path.json",
            clip = ClipInteraction.AaClip(AaClipRef("cpu.sk-aa-clip.fixture"), IntRect(0, 0, 2, 2)),
        )
        val differenceClipResult = GeometryCoverageMigrationHarness.comparePathCoverageAgainstOracle(
            width = 2,
            height = 2,
            fixture = PathCoverageFixture(
                bounds = FloatRect(0f, 0f, 2f, 2f),
                fillType = PathFillType.Winding,
                inverse = false,
                antiAlias = false,
                verbCount = 5,
                edgeCount = 4,
                segmentCount = 4,
            ),
            color = Rgba(0f, 0f, 0f, 1f),
            oraclePixels = oracle,
            coverageAlpha = coverage,
            artifactPath = "artifacts/gra-37/difference-clip-path.json",
            clip = ClipInteraction.Unsupported(StandardGeometryReason.ClipStackUnsupported),
        )

        assertTrue(aaClipResult.dump().contains("clip=AaClip(cpu.sk-aa-clip.fixture)"))
        assertTrue(aaClipResult.dump().contains("clip=AaClip(ref=cpu.sk-aa-clip.fixture,bounds=0,0,2,2)"))
        assertTrue(differenceClipResult.dump().contains("clip=Unsupported(reason=geometry.clip-stack-unsupported)"))
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
    fun `default cutover selects cpu descriptor route for axis aligned filled rect`() {
        val result = GeometryCoverageMigrationHarness.defaultAxisAlignedFilledRect(
            width = 8,
            height = 8,
            rect = rect,
            color = color,
            artifactPath = "artifacts/gra-38/default-rect.json",
        )

        assertEquals(true, result.diffSummary.passed)
        assertEquals("cpu.descriptor.coverage-plan.solid-rect", result.selectedRoute.id)
        assertEquals("kanvas-skia.current.draw-rect", result.compatibilityFallbackRoute.id)
        assertEquals(null, result.metrics.fallbackReason)
        val dump = result.dump()
        assertTrue(dump.contains("mode=Default"))
        assertTrue(dump.contains("selectedRoute=cpu.descriptor.coverage-plan.solid-rect"))
        assertTrue(dump.contains("compatibilityFallbackRoute=kanvas-skia.current.draw-rect"))
        assertTrue(dump.contains("legacyRetainedReason=rollback fallback retained for unsupported transforms, clips, and descriptor runtime failures"))
        assertTrue(dump.contains("diff=compared(passed=true,width=8,height=8,differingPixels=0,maxChannelDelta=0,artifactPath=artifacts/gra-38/default-rect.json)"))
        assertEquals(64, result.metrics.touchedPixels)
        assertEquals(CpuAnalyticRectCoverageExecutor.KERNEL_ID, result.metrics.kernelId)
        assertEquals("scalar-analytic-rect", result.metrics.scalarVectorStatus)
        assertTrue(dump.contains("report=legacy->descriptor default,visualDiff=true,diagnostics=none,metricTable=[touchedPixels=64;kernel=${CpuAnalyticRectCoverageExecutor.KERNEL_ID};fallback=none]"))
    }

    @Test
    fun `gated mode keeps path descriptor route behind explicit cpu gate`() {
        val defaultDecision = GeometryCoverageMigrationHarness.gateDecision(
            primitive = DescriptorPrimitiveFamily.SimpleFilledPath,
            backend = BackendKind.CPU,
        )
        val enabledDecision = GeometryCoverageMigrationHarness.gateDecision(
            primitive = DescriptorPrimitiveFamily.SimpleFilledPath,
            backend = BackendKind.CPU,
            gates = listOf(
                DescriptorMigrationGate(
                    primitive = DescriptorPrimitiveFamily.SimpleFilledPath,
                    backend = BackendKind.CPU,
                    enabled = true,
                ),
            ),
        )

        assertFalse(defaultDecision.descriptorEnabled)
        assertEquals("kanvas-skia.current.draw-path", defaultDecision.selectedRoute.id)
        assertEquals("cpu.descriptor.coverage-plan.path-coverage", defaultDecision.descriptorRoute.id)
        assertTrue(enabledDecision.descriptorEnabled)
        assertEquals("cpu.descriptor.coverage-plan.path-coverage", enabledDecision.selectedRoute.id)
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
        assertFalse(dump.contains("mode=Default"))
    }

    @Test
    fun `unsupported path geometry reports stable diagnostic`() {
        val diagnostic = GeometryCoverageMigrationHarness.unsupportedGeometryDiagnostic(
            backend = BackendKind.CPU,
            primitive = DescriptorPrimitiveFamily.SimpleFilledPath,
            reason = StandardGeometryReason.PathEffectUnsupported,
        )

        val dump = diagnostic.dump()
        assertTrue(dump.contains("drawKind=simple-filled-path"))
        assertTrue(dump.contains("geometry=Unsupported(reason=geometry.path-effect-unsupported)"))
        assertTrue(dump.contains("coverage=Unsupported(reason=coverage.span-runs-unsupported)"))
        assertTrue(dump.contains("descriptorRoute=cpu.descriptor.coverage-plan.path-coverage"))
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
