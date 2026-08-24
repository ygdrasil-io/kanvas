package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceRecordedSession
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuEvidenceCatalogTest {
    @Test
    fun `catalog separates eight public surface renders from two renderer refusals`() {
        val cases = GpuEvidenceCatalog.cases

        assertEquals(
            listOf(
                "solid-card-stack",
                "separable-blur-rect",
                "translucent-card-overlap",
                "scissor-overlay",
                "stroke-rect-outline",
                "linear-gradient-lanes",
                "radial-swatch",
                "sweep-disk",
                "custom-runtime-effect-unregistered-refusal",
                "aggregate-memory-budget-refusal",
            ),
            cases.map { it.descriptor.id.value },
        )
        assertEquals(
            listOf(
                "solid-card-stack",
                "separable-blur-rect",
                "translucent-card-overlap",
                "scissor-overlay",
                "stroke-rect-outline",
                "linear-gradient-lanes",
                "radial-swatch",
                "sweep-disk",
            ),
            GpuEvidenceCatalog.renderCases.map { it.descriptor.id.value },
        )
        assertEquals(
            listOf("custom-runtime-effect-unregistered-refusal", "aggregate-memory-budget-refusal"),
            GpuEvidenceCatalog.refusalCases.map { it.descriptor.id.value },
        )
        assertTrue(GpuEvidenceCatalog.renderCases.all { it.program is KanvasSurfaceProgram })
        assertTrue(GpuEvidenceCatalog.renderCases.all { it.descriptor.expectation == EvidenceExpectation.ShouldRender })
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.program is SceneProgram })
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.descriptor.expectation is EvidenceExpectation.ShouldRefuse })
        assertEquals(
            List(8) { "kanvas.surface.render" },
            GpuEvidenceCatalog.renderCases.map { assertIs<KanvasSurfaceProgram>(it.program).routeId },
        )
        assertEquals(cases.size, cases.map { it.descriptor.id }.toSet().size)

        val solid = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "solid-card-stack" })
        assertEquals(64, solid.descriptor.width)
        assertEquals(64, solid.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(solid.descriptor.expectation)
        assertNotNull(solid.oracle)
        assertEquals(0, solid.descriptor.comparison?.perChannelTolerance)
        assertEquals(100.0, solid.descriptor.comparison?.minimumSimilarityPercent)

        val refusal = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "custom-runtime-effect-unregistered-refusal" })
        assertEquals(16, refusal.descriptor.width)
        assertEquals(16, refusal.descriptor.height)
        assertEquals(
            "unsupported.runtime_effect.custom_wgsl_not_registered",
            assertIs<EvidenceExpectation.ShouldRefuse>(refusal.descriptor.expectation).stableReasonCode,
        )
        assertEquals(null, refusal.oracle)

        val blur = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "separable-blur-rect" })
        assertEquals(64, blur.descriptor.width)
        assertEquals(64, blur.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(blur.descriptor.expectation)
        assertNotNull(blur.oracle)
        assertEquals(2, blur.descriptor.comparison?.perChannelTolerance)
        assertEquals(99.0, blur.descriptor.comparison?.minimumSimilarityPercent)
        assertEquals(1, blur.descriptor.comparison?.version)
        assertEquals("surface-srgb-mask-blur-normal-decal", (blur.descriptor.oracle as OraclePolicy.GeneratedCpu).oracleId)
        assertEquals(2, (blur.descriptor.oracle as OraclePolicy.GeneratedCpu).version)

        listOf("translucent-card-overlap", "scissor-overlay", "stroke-rect-outline").forEach { id ->
            val evidenceCase = assertNotNull(cases.firstOrNull { it.descriptor.id.value == id })
            assertEquals(64, evidenceCase.descriptor.width)
            assertEquals(64, evidenceCase.descriptor.height)
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertNotNull(evidenceCase.oracle)
            assertNotNull(evidenceCase.descriptor.comparison)
        }
        assertEquals(1, cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.comparison?.perChannelTolerance)
        assertEquals(100.0, cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.comparison?.minimumSimilarityPercent)
        assertEquals(1, cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.comparison?.version)
        val translucentOracle = cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.oracle as OraclePolicy.GeneratedCpu
        assertEquals("surface-srgb-linear-premul-src-over", translucentOracle.oracleId)
        assertEquals(2, translucentOracle.version)
        assertEquals(0, cases.first { it.descriptor.id.value == "scissor-overlay" }.descriptor.comparison?.perChannelTolerance)
        val strokeRect = cases.first { it.descriptor.id.value == "stroke-rect-outline" }
        assertEquals(0, strokeRect.descriptor.comparison?.perChannelTolerance)
        assertIs<KanvasSurfaceProgram>(strokeRect.program)
        assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(strokeRect.program).routeId)

        listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk").forEach { id ->
            val evidenceCase = assertNotNull(cases.firstOrNull { it.descriptor.id.value == id })
            assertEquals(64, evidenceCase.descriptor.width)
            assertEquals(64, evidenceCase.descriptor.height)
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertIs<KanvasSurfaceProgram>(evidenceCase.program)
            assertNotNull(evidenceCase.oracle)
            assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId)
            assertEquals(1, evidenceCase.descriptor.comparison?.perChannelTolerance)
            assertEquals(100.0, evidenceCase.descriptor.comparison?.minimumSimilarityPercent)
            assertEquals(1, evidenceCase.descriptor.comparison?.version)
        }
        assertEquals(
            mapOf(
                "linear-gradient-lanes" to "surface-srgb-gradient-linear-clamp",
                "radial-swatch" to "surface-srgb-gradient-radial-clamp",
                "sweep-disk" to "surface-srgb-gradient-sweep-clamp",
            ),
            listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk").associateWith { id ->
                (cases.first { it.descriptor.id.value == id }.descriptor.oracle as OraclePolicy.GeneratedCpu).oracleId
            },
        )
        listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk").forEach { id ->
            val oracle = cases.first { it.descriptor.id.value == id }.descriptor.oracle as OraclePolicy.GeneratedCpu
            assertEquals(2, oracle.version)
            assertEquals(
                "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage.",
                cases.first { it.descriptor.id.value == id }.descriptor.comparison?.rationale,
            )
        }

        val budget = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "aggregate-memory-budget-refusal" })
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", assertIs<EvidenceExpectation.ShouldRefuse>(budget.descriptor.expectation).stableReasonCode)
        assertEquals(null, budget.oracle)
    }

    @Test
    fun `catalog locks exact product routes policies and oracle identities`() {
        val expectedRenderIds = listOf(
            "solid-card-stack",
            "separable-blur-rect",
            "translucent-card-overlap",
            "scissor-overlay",
            "stroke-rect-outline",
            "linear-gradient-lanes",
            "radial-swatch",
            "sweep-disk",
        )
        assertEquals(
            expectedRenderIds.associateWith { "kanvas.surface.render" },
            GpuEvidenceCatalog.renderCases.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId
            },
        )
        assertEquals(
            mapOf(
                "custom-runtime-effect-unregistered-refusal" to "product.runtime-effect.custom",
                "aggregate-memory-budget-refusal" to "product.solid-rect",
            ),
            GpuEvidenceCatalog.refusalCases.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to assertIs<RoutedSceneProgram>(evidenceCase.program).routeId
            },
        )

        assertEquals(
            mapOf(
                "solid-card-stack" to OraclePolicy.GeneratedCpu("reference-raster-rect-src-over", 1),
                "separable-blur-rect" to OraclePolicy.GeneratedCpu("surface-srgb-mask-blur-normal-decal", 2),
                "translucent-card-overlap" to OraclePolicy.GeneratedCpu("surface-srgb-linear-premul-src-over", 2),
                "scissor-overlay" to OraclePolicy.GeneratedCpu("reference-raster-scissor-intersections", 1),
                "stroke-rect-outline" to OraclePolicy.GeneratedCpu("reference-raster-stroke-rect-bands", 1),
                "linear-gradient-lanes" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp", 2),
                "radial-swatch" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp", 2),
                "sweep-disk" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp", 2),
            ),
            GpuEvidenceCatalog.renderCases.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to evidenceCase.descriptor.oracle
            },
        )
        assertEquals(
            mapOf(
                "solid-card-stack" to ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from opaque SrcOver rectangles."),
                "separable-blur-rect" to ComparisonPolicy(2, 99.0, 1, "Bounded GPU floating-point rounding is allowed after the independently quantized vertical mask stage."),
                "translucent-card-overlap" to ComparisonPolicy(1, 100.0, 1, "Hardware rgba8unorm nearest quantization may differ from the independent linear-premultiplied sRGB oracle by one RGB byte; alpha remains exact and delta 2 remains a failure."),
                "scissor-overlay" to ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from literal scissor intersections."),
                "stroke-rect-outline" to ComparisonPolicy(0, 100.0, 1, "Exact integer RGBA8 output from four literal analytic coverage bands."),
                "linear-gradient-lanes" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "radial-swatch" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "sweep-disk" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
            ),
            GpuEvidenceCatalog.renderCases.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to evidenceCase.descriptor.comparison
            },
        )
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.descriptor.oracle == OraclePolicy.StableRefusal && it.oracle == null })
    }

    @Test
    fun `public surface programs record only the requested Canvas operations`() {
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRect(RectF32.ofLTRB(8f, 10f, 56f, 34f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)), Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRect(RectF32.ofLTRB(14f, 38f, 50f, 54f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)), Matrix3x3F32.Identity, ClipStack.WideOpen),
            ),
            ops("solid-card-stack"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(16f, 16f, 48f, 48f),
                    Paint(color = ColorARGB.fromRGBA(0.18f, 0.42f, 0.76f, 1f), maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 3f), antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            ops("separable-blur-rect"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRect(RectF32.ofLTRB(8f, 10f, 44f, 42f), Paint.fill(ColorARGB.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f)), Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRect(RectF32.ofLTRB(24f, 22f, 56f, 54f), Paint.fill(ColorARGB.fromRGBA(0.5f, 0.25f, 0.125f, 0.5f)), Matrix3x3F32.Identity, ClipStack.WideOpen),
            ),
            ops("translucent-card-overlap"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.SetClip(ClipStack.DeviceRect(RectF32.ofLTRB(16f, 16f, 40f, 40f), false)),
                DisplayOp.DrawRect(RectF32.ofLTRB(8f, 8f, 56f, 56f), Paint.fill(ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)), Matrix3x3F32.Identity, ClipStack.DeviceRect(RectF32.ofLTRB(16f, 16f, 40f, 40f), false)),
                DisplayOp.SetClip(ClipStack.DeviceRect(RectF32.ofLTRB(24f, 24f, 48f, 48f), false)),
                DisplayOp.DrawRect(RectF32.ofLTRB(16f, 16f, 56f, 56f), Paint.fill(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)), Matrix3x3F32.Identity, ClipStack.DeviceRect(RectF32.ofLTRB(24f, 24f, 48f, 48f), false)),
            ),
            ops("scissor-overlay"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRect(RectF32.ofLTRB(16f, 16f, 48f, 48f), Paint.stroke(ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f), 6f).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
            ),
            ops("stroke-rect-outline"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    Paint(
                        shader = Shader.LinearGradient(
                            Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                            listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            ops("linear-gradient-lanes"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 8f, 56f, 56f),
                    Paint(
                        shader = Shader.RadialGradient(
                            Point2F32(32.5f, 32.5f), 23.5f,
                            listOf(GradientStop(0f, ColorARGB.of(255, 255, 232, 72)), GradientStop(1f, ColorARGB.of(255, 48, 80, 192))),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            ops("radial-swatch"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 8f, 56f, 56f),
                    Paint(
                        shader = Shader.SweepGradient(
                            Point2F32(32.5f, 32.5f), 0f, 360f,
                            listOf(GradientStop(0f, ColorARGB.of(255, 255, 64, 64)), GradientStop(1f, ColorARGB.of(255, 64, 208, 255))),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            ops("sweep-disk"),
        )
    }

    private fun ops(id: String): List<DisplayOp> {
        val program = assertIs<KanvasSurfaceProgram>(GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == id }.program)
        val session = assertIs<KanvasSurfaceRecordedSession>(program.openSession(64, 64))
        return session.snapshotOps()
    }

    @Test
    fun `translucent policy accepts material rgba8 rounding delta one but rejects delta two`() {
        val evidenceCase = assertNotNull(
            GpuEvidenceCatalog.cases.firstOrNull { it.descriptor.id.value == "translucent-card-overlap" },
        )
        val policy = assertNotNull(evidenceCase.descriptor.comparison)
        assertEquals(1, policy.perChannelTolerance)
        assertEquals(100.0, policy.minimumSimilarityPercent)
        val oracle = assertNotNull(evidenceCase.oracle).render(64, 64)
        val comparator = EvidenceComparator()

        val deltaOne = oracle.copyOf().also { it[0] = (it[0].toInt() + 1).toByte() }
        val deltaTwo = oracle.copyOf().also { it[0] = (it[0].toInt() + 2).toByte() }

        val roundedGpu = comparator.compare(deltaOne, oracle, 64, 64, policy)
        assertTrue(roundedGpu.passed)
        assertEquals(100.0, roundedGpu.similarityPercent)

        val outOfBound = comparator.compare(deltaTwo, oracle, 64, 64, policy)
        assertFalse(outOfBound.passed)
    }
}
