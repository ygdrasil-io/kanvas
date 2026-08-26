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
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.PathMeasure
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuEvidenceCatalogTest {
    @Test
    fun `catalog separates forty six public surface renders from two refusals`() {
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
                "linear-gradient-three-stops", "sweep-gradient-partial-angle", "affine-solid-rect", "scissored-radial-gradient", "repeat-gradient-refusal", "gradient-stroke-refusal",
                "scaled-solid-rrect",
                "solid-drrect-hole",
                "asymmetric-solid-rrect",
                "ellipse-solid-rrect",
                "asymmetric-solid-drrect-hole",
                "clip-rrect-solid",
                "clip-rrect-ellipse",
                "clip-rrect-two-bands",
                "clip-path-triangle-solid",
                "clip-path-concave-solid",
                "clip-path-triangle-two-bands",
                "clip-path-translated-triangle-solid",
                "clip-path-uniform-scaled-triangle-solid",
                "clip-path-uniform-scaled-triangle-two-bands",
                "clip-path-triangle-linear-gradient",
                "clip-path-translated-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-linear-gradient",
                "clip-path-triangle-direct-triangle-solid",
                "clip-path-translated-triangle-direct-triangle-solid",
                "clip-path-triangle-direct-triangle-order",
                "clip-path-triangle-direct-triangle-linear-gradient",
                "clip-path-translated-triangle-direct-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
                "clip-path-solid-rrect",
                "clip-path-asymmetric-solid-rrect",
                "clip-path-ellipse-solid-rrect",
                "clip-path-solid-drrect",
                "clip-path-asymmetric-solid-drrect",
                "clip-path-ellipse-solid-drrect",
                "solid-triangle-path",
                "solid-concave-path",
                "even-odd-path-hole",
                "winding-path-hole",
                "inverse-winding-triangle-path",
                "inverse-even-odd-path-hole",
                "implicit-closure-triangle-path",
                "translated-triangle-path",
                "uniform-scaled-triangle-path",
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
                "linear-gradient-three-stops", "sweep-gradient-partial-angle", "affine-solid-rect", "scissored-radial-gradient", "repeat-gradient-refusal", "gradient-stroke-refusal",
                "scaled-solid-rrect",
                "solid-drrect-hole",
                "asymmetric-solid-rrect",
                "ellipse-solid-rrect",
                "asymmetric-solid-drrect-hole",
                "clip-rrect-solid",
                "clip-rrect-ellipse",
                "clip-rrect-two-bands",
                "clip-path-triangle-solid",
                "clip-path-concave-solid",
                "clip-path-triangle-two-bands",
                "clip-path-translated-triangle-solid",
                "clip-path-uniform-scaled-triangle-solid",
                "clip-path-uniform-scaled-triangle-two-bands",
                "clip-path-triangle-linear-gradient",
                "clip-path-translated-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-linear-gradient",
                "clip-path-triangle-direct-triangle-solid",
                "clip-path-translated-triangle-direct-triangle-solid",
                "clip-path-triangle-direct-triangle-order",
                "clip-path-triangle-direct-triangle-linear-gradient",
                "clip-path-translated-triangle-direct-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
                "clip-path-solid-rrect",
                "clip-path-asymmetric-solid-rrect",
                "clip-path-ellipse-solid-rrect",
                "clip-path-solid-drrect",
                "clip-path-asymmetric-solid-drrect",
                "clip-path-ellipse-solid-drrect",
                "solid-triangle-path",
                "solid-concave-path",
                "even-odd-path-hole",
                "winding-path-hole",
                "inverse-winding-triangle-path",
                "inverse-even-odd-path-hole",
                "implicit-closure-triangle-path",
                "translated-triangle-path",
                "uniform-scaled-triangle-path",
            ),
            GpuEvidenceCatalog.renderCases.map { it.descriptor.id.value },
        )
        assertEquals(
            listOf("custom-runtime-effect-unregistered-refusal", "aggregate-memory-budget-refusal"),
            GpuEvidenceCatalog.refusalCases.map { it.descriptor.id.value },
        )
        assertTrue(GpuEvidenceCatalog.renderCases.all { it.program is KanvasSurfaceProgram })
        assertTrue(GpuEvidenceCatalog.renderCases.all { it.descriptor.expectation == EvidenceExpectation.ShouldRender })
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.program is SceneProgram || it.program is KanvasSurfaceProgram })
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.descriptor.expectation is EvidenceExpectation.ShouldRefuse })
        assertEquals(
            List(52) { "kanvas.surface.render" },
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

        listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk", "linear-gradient-three-stops", "sweep-gradient-partial-angle", "scissored-radial-gradient", "repeat-gradient-refusal", "gradient-stroke-refusal").forEach { id ->
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
                "linear-gradient-three-stops" to "surface-srgb-gradient-linear-clamp",
                "sweep-gradient-partial-angle" to "surface-srgb-gradient-sweep-clamp",
                "scissored-radial-gradient" to "surface-srgb-gradient-radial-clamp",
                "repeat-gradient-refusal" to "surface-srgb-gradient-linear-repeat",
                "gradient-stroke-refusal" to "surface-srgb-gradient-linear-clamp-stroke-bands",
            ),
            listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk", "linear-gradient-three-stops", "sweep-gradient-partial-angle", "scissored-radial-gradient", "repeat-gradient-refusal", "gradient-stroke-refusal").associateWith { id ->
                (cases.first { it.descriptor.id.value == id }.descriptor.oracle as OraclePolicy.GeneratedCpu).oracleId
            },
        )
        listOf("linear-gradient-lanes", "radial-swatch", "sweep-disk", "linear-gradient-three-stops", "sweep-gradient-partial-angle", "scissored-radial-gradient", "repeat-gradient-refusal").forEach { id ->
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
            "linear-gradient-three-stops", "sweep-gradient-partial-angle", "affine-solid-rect", "scissored-radial-gradient", "repeat-gradient-refusal", "gradient-stroke-refusal",
            "scaled-solid-rrect",
            "solid-drrect-hole",
            "asymmetric-solid-rrect",
            "ellipse-solid-rrect",
            "asymmetric-solid-drrect-hole",
                "clip-rrect-solid",
                "clip-rrect-ellipse",
                "clip-rrect-two-bands",
                "clip-path-triangle-solid",
                "clip-path-concave-solid",
                "clip-path-triangle-two-bands",
                "clip-path-translated-triangle-solid",
                "clip-path-uniform-scaled-triangle-solid",
                "clip-path-uniform-scaled-triangle-two-bands",
                "clip-path-triangle-linear-gradient",
                "clip-path-translated-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-linear-gradient",
                "clip-path-triangle-direct-triangle-solid",
                "clip-path-translated-triangle-direct-triangle-solid",
                "clip-path-triangle-direct-triangle-order",
                "clip-path-triangle-direct-triangle-linear-gradient",
                "clip-path-translated-triangle-direct-triangle-linear-gradient",
                "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
                "clip-path-solid-rrect",
                "clip-path-asymmetric-solid-rrect",
                "clip-path-ellipse-solid-rrect",
                "clip-path-solid-drrect",
                "clip-path-asymmetric-solid-drrect",
                "clip-path-ellipse-solid-drrect",
                "solid-triangle-path",
            "solid-concave-path",
            "even-odd-path-hole",
            "winding-path-hole",
            "inverse-winding-triangle-path",
            "inverse-even-odd-path-hole",
            "implicit-closure-triangle-path",
            "translated-triangle-path",
            "uniform-scaled-triangle-path",
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
            GpuEvidenceCatalog.refusalCases.filter { it.program is RoutedSceneProgram }.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to assertIs<RoutedSceneProgram>(evidenceCase.program).routeId
            },
        )
        assertTrue(GpuEvidenceCatalog.refusalCases.none { it.program is KanvasSurfaceProgram })

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
                "linear-gradient-three-stops" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp", 2),
                "sweep-gradient-partial-angle" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-sweep-clamp", 2),
                "affine-solid-rect" to OraclePolicy.GeneratedCpu("reference-raster-affine-solid-rect", 1),
                "scissored-radial-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-radial-clamp", 2),
                "repeat-gradient-refusal" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-repeat", 2),
                "gradient-stroke-refusal" to OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-stroke-bands", 1),
                "scaled-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 1),
                "solid-drrect-hole" to OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 1),
                "asymmetric-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
                "ellipse-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
                "asymmetric-solid-drrect-hole" to OraclePolicy.GeneratedCpu("surface-srgb-rrect-pixel-center", 2),
                "clip-rrect-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
                "clip-rrect-ellipse" to OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
                "clip-rrect-two-bands" to OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1),
                "clip-path-triangle-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-concave-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-triangle-two-bands" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-translated-triangle-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-uniform-scaled-triangle-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-uniform-scaled-triangle-two-bands" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1),
                "clip-path-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-device-space", 1),
                "clip-path-translated-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-device-space", 1),
                "clip-path-uniform-scaled-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-linear-gradient-device-space", 1),
                "clip-path-triangle-direct-triangle-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-pixel-center", 1),
                "clip-path-translated-triangle-direct-triangle-solid" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-pixel-center", 1),
                "clip-path-triangle-direct-triangle-order" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-pixel-center", 1),
                "clip-path-triangle-direct-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-linear-gradient-device-space", 1),
                "clip-path-translated-triangle-direct-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-linear-gradient-device-space", 1),
                "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-direct-triangle-linear-gradient-device-space", 1),
                "clip-path-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-rrect-pixel-center", 1),
                "clip-path-asymmetric-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-rrect-pixel-center", 1),
                "clip-path-ellipse-solid-rrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-rrect-pixel-center", 1),
                "clip-path-solid-drrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-drrect-pixel-center", 1),
                "clip-path-asymmetric-solid-drrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-drrect-pixel-center", 1),
                "clip-path-ellipse-solid-drrect" to OraclePolicy.GeneratedCpu("surface-srgb-clip-path-drrect-pixel-center", 1),
                "solid-triangle-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "solid-concave-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "even-odd-path-hole" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "winding-path-hole" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "inverse-winding-triangle-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "inverse-even-odd-path-hole" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "implicit-closure-triangle-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "translated-triangle-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
                "uniform-scaled-triangle-path" to OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", 2),
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
                "linear-gradient-three-stops" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "sweep-gradient-partial-angle" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "affine-solid-rect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from hand-derived inverse affine pixel-center membership."),
                "scissored-radial-gradient" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "repeat-gradient-refusal" to ComparisonPolicy(1, 100.0, 1, "Independent sRGB decode, linear-premultiplied interpolation, and sRGB target storage."),
                "gradient-stroke-refusal" to ComparisonPolicy(1, 100.0, 1, "Independent four-band coverage with device-coordinate clamp linear-gradient sampling and one-LSB RGBA8 tolerance."),
                "scaled-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent analytic pixel-center RRect membership."),
                "solid-drrect-hole" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent analytic pixel-center RRect membership."),
                "asymmetric-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."),
                "ellipse-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."),
                "asymmetric-solid-drrect-hole" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent per-corner analytic pixel-center RRect membership."),
                "clip-rrect-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership."),
                "clip-rrect-ellipse" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership."),
                "clip-rrect-two-bands" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership and paint order."),
                "clip-path-triangle-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-concave-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-triangle-two-bands" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-translated-triangle-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-uniform-scaled-triangle-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-uniform-scaled-triangle-two-bands" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
                "clip-path-triangle-linear-gradient" to ComparisonPolicy(1, 100.0, 1, "Independent device-space pixel-center winding clip and linear-light clamp gradient oracle."),
                "clip-path-translated-triangle-linear-gradient" to ComparisonPolicy(1, 100.0, 1, "Independent device-space pixel-center winding clip and linear-light clamp gradient oracle."),
                "clip-path-uniform-scaled-triangle-linear-gradient" to ComparisonPolicy(1, 100.0, 1, "Independent device-space pixel-center winding clip and linear-light clamp gradient oracle."),
                "clip-path-triangle-direct-triangle-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space pixel-center clip and direct-triangle membership."),
                "clip-path-translated-triangle-direct-triangle-solid" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space pixel-center clip and direct-triangle membership."),
                "clip-path-triangle-direct-triangle-order" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space pixel-center clip and direct-triangle membership."),
                "clip-path-triangle-direct-triangle-linear-gradient" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space clip, direct-triangle, and clamp-gradient membership."),
                "clip-path-translated-triangle-direct-triangle-linear-gradient" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space clip, direct-triangle, and clamp-gradient membership."),
                "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent device-space clip, direct-triangle, and clamp-gradient membership."),
                "clip-path-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic RRect membership."),
                "clip-path-asymmetric-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic RRect membership."),
                "clip-path-ellipse-solid-rrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic RRect membership."),
                "clip-path-solid-drrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic DRRect membership."),
                "clip-path-asymmetric-solid-drrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic DRRect membership."),
                "clip-path-ellipse-solid-drrect" to ComparisonPolicy(0, 100.0, 1, "Exact RGBA8 output from independent pixel-center winding clip and analytic DRRect membership."),
                "solid-triangle-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "solid-concave-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "even-odd-path-hole" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "winding-path-hole" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "inverse-winding-triangle-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership."),
                "inverse-even-odd-path-hole" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership."),
                "implicit-closure-triangle-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "translated-triangle-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
                "uniform-scaled-triangle-path" to ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership."),
            ),
            GpuEvidenceCatalog.renderCases.associate { evidenceCase ->
                evidenceCase.descriptor.id.value to evidenceCase.descriptor.comparison
            },
        )
        assertTrue(GpuEvidenceCatalog.refusalCases.all { it.descriptor.oracle == OraclePolicy.StableRefusal && it.oracle == null })
    }

    @Test
    fun `gradient stroke public case is a 64 pixel clamp annulus with an independent oracle`() {
        val evidenceCase = assertNotNull(
            GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == "gradient-stroke-refusal" },
        )
        assertEquals(64, evidenceCase.descriptor.width)
        assertEquals(64, evidenceCase.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
        assertEquals(
            OraclePolicy.GeneratedCpu("surface-srgb-gradient-linear-clamp-stroke-bands", 1),
            evidenceCase.descriptor.oracle,
        )
        assertEquals(1, evidenceCase.descriptor.comparison?.perChannelTolerance)
        val pixels = assertNotNull(evidenceCase.oracle).render(64, 64)

        assertTrue(rgba(pixels, 8, 16, 64)[3] > 0)
        assertTrue(rgba(pixels, 55, 47, 64)[3] > 0)
        assertEquals(listOf(0, 0, 0, 0), rgba(pixels, 32, 32, 64))
        assertEquals(listOf(0, 0, 0, 0), rgba(pixels, 4, 4, 64))
    }

    @Test
    fun `gradient stroke policy accepts one LSB but rejects two LSB at a band sample`() {
        val evidenceCase = assertNotNull(
            GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == "gradient-stroke-refusal" },
        )
        val policy = assertNotNull(evidenceCase.descriptor.comparison)
        val oracle = assertNotNull(evidenceCase.oracle).render(64, 64)
        val interiorRedChannel = (16 * 64 + 32) * 4
        val comparator = EvidenceComparator()
        val deltaOne = oracle.copyOf().also { pixels ->
            pixels[interiorRedChannel] = (pixels[interiorRedChannel].toInt() + 1).toByte()
        }
        val deltaTwo = oracle.copyOf().also { pixels ->
            pixels[interiorRedChannel] = (pixels[interiorRedChannel].toInt() + 2).toByte()
        }

        assertTrue(comparator.compare(deltaOne, oracle, 64, 64, policy).passed)
        assertFalse(comparator.compare(deltaTwo, oracle, 64, 64, policy).passed)
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

    @Test
    fun `wave two public Surface programs record the approved literal operations`() {
        val red = ColorARGB.of(255, 255, 56, 56)
        val green = ColorARGB.of(255, 56, 220, 120)
        val blue = ColorARGB.of(255, 56, 112, 255)
        val sweepRed = ColorARGB.of(255, 255, 64, 64)
        val sweepBlue = ColorARGB.of(255, 64, 208, 255)
        val yellow = ColorARGB.of(255, 255, 232, 72)
        val radialBlue = ColorARGB.of(255, 48, 80, 192)
        val orange = ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)
        val linearAxisStart = Point2F32(8.5f, 32.5f)
        val linearAxisEnd = Point2F32(55.5f, 32.5f)
        val linearStops = listOf(GradientStop(0f, red), GradientStop(1f, blue))

        assertEquals(
            listOf(DisplayOp.DrawRect(
                RectF32.ofLTRB(8f, 16f, 56f, 48f),
                Paint(shader = Shader.LinearGradient(
                    linearAxisStart, linearAxisEnd,
                    listOf(GradientStop(0f, red), GradientStop(.5f, green), GradientStop(1f, blue)), TileMode.CLAMP,
                ), antiAlias = false),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            )),
            ops("linear-gradient-three-stops"),
        )
        assertEquals(
            listOf(DisplayOp.DrawRect(
                RectF32.ofLTRB(8f, 8f, 56f, 56f),
                Paint(shader = Shader.SweepGradient(
                    Point2F32(32.5f, 32.5f), 45f, 315f,
                    listOf(GradientStop(0f, sweepRed), GradientStop(1f, sweepBlue)), TileMode.CLAMP,
                ), antiAlias = false),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            )),
            ops("sweep-gradient-partial-angle"),
        )
        val affine = Matrix3x3F32(sx = 1f, kx = .25f, tx = 4f, sy = 1f)
        assertEquals(
            listOf(
                DisplayOp.SetTransform(affine),
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 16f, 40f, 48f), Paint.fill(orange).copy(antiAlias = false), affine, ClipStack.WideOpen,
                ),
            ),
            ops("affine-solid-rect"),
        )
        val scissor = ClipStack.DeviceRect(RectF32.ofLTRB(20f, 12f, 52f, 52f), false)
        assertEquals(
            listOf(
                DisplayOp.SetClip(scissor),
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 8f, 56f, 56f),
                    Paint(shader = Shader.RadialGradient(
                        Point2F32(32.5f, 32.5f), 23.5f,
                        listOf(GradientStop(0f, yellow), GradientStop(1f, radialBlue)), TileMode.CLAMP,
                    ), antiAlias = false),
                    Matrix3x3F32.Identity, scissor,
                ),
            ),
            ops("scissored-radial-gradient"),
        )
        assertEquals(
            listOf(DisplayOp.DrawRect(
                RectF32.ofLTRB(0f, 16f, 64f, 48f),
                Paint(shader = Shader.LinearGradient(
                    Point2F32(16.5f, 32.5f), Point2F32(31.5f, 32.5f), linearStops, TileMode.REPEAT,
                ), antiAlias = false),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            )),
            ops("repeat-gradient-refusal"),
        )
        assertEquals(
            listOf(DisplayOp.DrawRect(
                RectF32.ofLTRB(8f, 16f, 56f, 48f),
                Paint.stroke(ColorARGB.Transparent, 4f).copy(
                    shader = Shader.LinearGradient(linearAxisStart, linearAxisEnd, linearStops, TileMode.CLAMP), antiAlias = false,
                ),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            )),
            ops("gradient-stroke-refusal"),
        )
        val drrectBlue = ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)
        val scale = Matrix3x3F32(sx = 2f, sy = 1f)
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.SetTransform(scale),
                DisplayOp.DrawRRect(
                    RRectF32.of(RectF32.ofLTRB(8f, 16f, 24f, 48f), radius = 4f),
                    Paint.fill(orange).copy(antiAlias = false),
                    scale,
                    ClipStack.WideOpen,
                ),
            ),
            ops("scaled-solid-rrect"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawDRRect(
                    RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
                    RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
                    Paint.fill(drrectBlue).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            ops("solid-drrect-hole"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRRect(
                    RRectF32.of(
                        RectF32.ofLTRB(8f, 8f, 56f, 56f),
                        topLeft = CornerRadiiF32.of(4f, 8f), topRight = CornerRadiiF32.of(10f, 4f),
                        bottomRight = CornerRadiiF32.of(8f, 12f), bottomLeft = CornerRadiiF32.of(6f, 3f),
                    ),
                    Paint.fill(orange).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen,
                ),
            ),
            ops("asymmetric-solid-rrect"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawRRect(
                    RRectF32.of(
                        RectF32.ofLTRB(12f, 20f, 52f, 44f),
                        CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
                        CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
                    ),
                    Paint.fill(drrectBlue).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen,
                ),
            ),
            ops("ellipse-solid-rrect"),
        )
        assertEquals(
            listOf(
                DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawDRRect(
                    RRectF32.of(
                        RectF32.ofLTRB(6f, 8f, 58f, 56f),
                        CornerRadiiF32.of(4f, 8f), CornerRadiiF32.of(10f, 4f),
                        CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(6f, 3f),
                    ),
                    RRectF32.of(
                        RectF32.ofLTRB(20f, 20f, 44f, 44f),
                        CornerRadiiF32.of(2f, 4f), CornerRadiiF32.of(6f, 2f),
                        CornerRadiiF32.of(4f, 6f), CornerRadiiF32.of(3f, 2f),
                    ),
                    Paint.fill(drrectBlue).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen,
                ),
            ),
            ops("asymmetric-solid-drrect-hole"),
        )
    }

    @Test
    fun `path fill public Surface programs record approved literal path operations`() {
        val orange = ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)
        val blue = ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)
        val green = ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)

        assertPathCase(
            id = "solid-triangle-path",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 55f),
            paint = Paint.fill(orange).copy(antiAlias = false),
            convex = true,
        )
        assertPathCase(
            id = "solid-concave-path",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 56f),
            paint = Paint.fill(blue).copy(antiAlias = false),
            convex = false,
        )
        assertPathCase(
            id = "even-odd-path-hole",
            fillType = FillType.EVEN_ODD,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 56f),
            paint = Paint.fill(green).copy(antiAlias = false),
            convex = false,
        )
        assertPathCase(
            id = "winding-path-hole",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 56f),
            paint = Paint.fill(blue).copy(antiAlias = false),
            convex = false,
        )
        assertPathCase(
            id = "inverse-winding-triangle-path",
            fillType = FillType.INVERSE_WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 55f),
            paint = Paint.fill(orange).copy(antiAlias = false),
            convex = true,
            oracleVersion = 2,
            comparisonRationale = "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership.",
        )
        assertPathCase(
            id = "inverse-even-odd-path-hole",
            fillType = FillType.INVERSE_EVEN_ODD,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 56f),
            paint = Paint.fill(green).copy(antiAlias = false),
            convex = false,
            oracleVersion = 2,
            comparisonRationale = "Exact opaque RGBA8 output from independent pixel-center inverse winding/even-odd polygon membership.",
        )
    }

    @Test
    fun `closure and transform path cases keep literal local geometry and public Surface state`() {
        val orange = ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)
        val blue = ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)
        val green = ColorARGB.fromRGBA(56f / 255f, 220f / 255f, 120f / 255f)

        assertPathCase(
            id = "implicit-closure-triangle-path",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 55f),
            paint = Paint.fill(orange).copy(antiAlias = false),
            convex = true,
        )
        val implicit = assertIs<DisplayOp.DrawPath>(ops("implicit-closure-triangle-path")[1])
        assertFalse(PathMeasure(implicit.path).isClosed)

        assertPathCase(
            id = "translated-triangle-path",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 56f, 55f),
            paint = Paint.fill(blue).copy(antiAlias = false),
            convex = true,
            transform = Matrix3x3F32(tx = 4f, ty = 5f),
        )
        val translated = assertIs<DisplayOp.DrawPath>(ops("translated-triangle-path")[2])
        assertTrue(PathMeasure(translated.path).isClosed)

        assertPathCase(
            id = "uniform-scaled-triangle-path",
            fillType = FillType.WINDING,
            bounds = RectF32.ofLTRB(8f, 8f, 40f, 40f),
            paint = Paint.fill(green).copy(antiAlias = false),
            convex = true,
            transform = Matrix3x3F32(sx = 1.5f, sy = 1.5f),
        )
        val scaled = assertIs<DisplayOp.DrawPath>(ops("uniform-scaled-triangle-path")[2])
        assertTrue(PathMeasure(scaled.path).isClosed)
    }

    @Test
    fun `hard clip rrect cases keep one identity non AA intersect clip and opaque draw order`() {
        val blue = ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)
        val orange = ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)
        assertHardClipRRectCase(
            "clip-rrect-solid", 1, RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
            listOf(RectF32.ofLTRB(0f, 0f, 64f, 64f) to blue),
        )
        assertHardClipRRectCase(
            "clip-rrect-ellipse", 1, RRectF32.of(
                RectF32.ofLTRB(12f, 20f, 52f, 44f),
                CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
                CornerRadiiF32.of(20f, 12f), CornerRadiiF32.of(20f, 12f),
            ),
            listOf(RectF32.ofLTRB(0f, 0f, 64f, 64f) to orange),
        )
        assertHardClipRRectCase(
            "clip-rrect-two-bands", 2, RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
            listOf(
                RectF32.ofLTRB(0f, 0f, 64f, 64f) to blue,
                RectF32.ofLTRB(32f, 0f, 64f, 64f) to orange,
            ),
            "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership and paint order.",
        )
    }

    @Test
    fun `hard clip path cases keep one literal non AA intersect clip and ordered opaque draws`() {
        val blue = ColorARGB.fromRGBA(31f / 255f, 115f / 255f, 209f / 255f)
        val orange = ColorARGB.fromRGBA(242f / 255f, 135f / 255f, 46f / 255f)
        assertHardClipPathCase(
            "clip-path-triangle-solid", 1,
            listOf(Point2F32(8f, 8f), Point2F32(56f, 8f), Point2F32(8f, 55f)),
            listOf(RectF32.ofLTRB(0f, 0f, 64f, 64f) to orange),
        )
        assertHardClipPathCase(
            "clip-path-concave-solid", 1,
            listOf(
                Point2F32(8f, 8f), Point2F32(56f, 8f), Point2F32(56f, 24f), Point2F32(32f, 24f),
                Point2F32(32f, 40f), Point2F32(56f, 40f), Point2F32(56f, 56f), Point2F32(8f, 56f),
            ),
            listOf(RectF32.ofLTRB(0f, 0f, 64f, 64f) to blue),
        )
        assertHardClipPathCase(
            "clip-path-triangle-two-bands", 2,
            listOf(Point2F32(8f, 8f), Point2F32(56f, 8f), Point2F32(8f, 55f)),
            listOf(
                RectF32.ofLTRB(0f, 0f, 64f, 64f) to blue,
                RectF32.ofLTRB(32f, 0f, 64f, 64f) to orange,
            ),
        )
    }

    @Test
    fun `direct triangle evidence consumers avoid 1x pixel center edge ambiguity`() {
        listOf(
            "clip-path-triangle-direct-triangle-solid",
            "clip-path-translated-triangle-direct-triangle-solid",
            "clip-path-triangle-direct-triangle-order",
            "clip-path-triangle-direct-triangle-linear-gradient",
            "clip-path-translated-triangle-direct-triangle-linear-gradient",
            "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
        ).forEach { id ->
            val firstConsumer = ops(id).filterIsInstance<DisplayOp.DrawPath>().first()
            assertEquals(4.25f, assertNotNull(firstConsumer.path.computeBounds()).top)
            assertEquals(FillType.WINDING, firstConsumer.path.fillType)
            assertFalse(firstConsumer.paint.antiAlias)
        }
    }

    @Test
    fun `direct triangle clamp gradient cases use one hard clip and an opaque non AA paint`() {
        listOf(
            "clip-path-triangle-direct-triangle-linear-gradient",
            "clip-path-translated-triangle-direct-triangle-linear-gradient",
            "clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient",
        ).forEach { id ->
            val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id })
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId)
            assertEquals(0, assertNotNull(evidenceCase.descriptor.comparison).perChannelTolerance)
            val operations = ops(id)
            val clip = assertIs<ClipStack.Complex>(operations.filterIsInstance<DisplayOp.SetClip>().single().clip)
            val pathClip = assertIs<org.graphiks.kanvas.canvas.ClipStackOp.PathOp>(clip.ops.single())
            assertEquals(FillType.WINDING, pathClip.path.fillType)
            assertEquals(ClipOp.INTERSECT, pathClip.op)
            assertFalse(pathClip.antiAlias)
            val draw = operations.filterIsInstance<DisplayOp.DrawPath>().single()
            assertEquals(clip, draw.clip)
            assertEquals(FillType.WINDING, draw.path.fillType)
            assertFalse(draw.paint.antiAlias)
            val gradient = assertIs<Shader.LinearGradient>(draw.paint.shader)
            assertEquals(TileMode.CLAMP, gradient.tileMode)
            assertEquals(2, gradient.stops.size)
            assertTrue(gradient.stops.all { it.color.a == 1f })
        }
    }

    private fun assertHardClipRRectCase(
        id: String,
        drawCount: Int,
        expectedRRect: RRectF32,
        expectedDraws: List<Pair<RectF32, ColorARGB>>,
        rationale: String = "Exact opaque RGBA8 output from independent hard pixel-center RRect clip membership.",
    ) {
        val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id })
        assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
        assertEquals(OraclePolicy.GeneratedCpu("surface-srgb-clip-rrect-pixel-center", 1), evidenceCase.descriptor.oracle)
        assertEquals(ComparisonPolicy(0, 100.0, 1, rationale), evidenceCase.descriptor.comparison)
        assertIs<KanvasSurfaceProgram>(evidenceCase.program)
        assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId)
        assertIs<org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipRRectCpuOracle>(evidenceCase.oracle)

        val operations = ops(id)
        assertEquals(drawCount + 2, operations.size)
        assertEquals(
            DisplayOp.DrawColor(
                ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f),
                BlendMode.SRC_OVER,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            operations[0],
        )
        val clip = assertIs<DisplayOp.SetClip>(operations[1]).clip
        val complex = assertIs<ClipStack.Complex>(clip)
        assertEquals(1, complex.ops.size)
        val rrectOp = assertIs<org.graphiks.kanvas.canvas.ClipStackOp.RRectOp>(complex.ops.single())
        assertEquals(expectedRRect, rrectOp.rrect)
        assertEquals(ClipOp.INTERSECT, rrectOp.op)
        assertFalse(rrectOp.antiAlias)
        assertEquals(drawCount, expectedDraws.size)
        operations.drop(2).zip(expectedDraws).forEach { (operation, expected) ->
            val draw = assertIs<DisplayOp.DrawRect>(operation)
            assertEquals(expected.first, draw.rect)
            assertEquals(Matrix3x3F32.Identity, draw.transform)
            assertEquals(complex, draw.clip)
            assertFalse(draw.paint.antiAlias)
            assertEquals(expected.second, draw.paint.color)
            assertEquals(1f, draw.paint.color.a)
        }
    }

    private fun assertHardClipPathCase(
        id: String,
        drawCount: Int,
        expectedPoints: List<Point2F32>,
        expectedDraws: List<Pair<RectF32, ColorARGB>>,
    ) {
        val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id })
        assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
        assertEquals(OraclePolicy.GeneratedCpu("surface-srgb-clip-path-pixel-center", 1), evidenceCase.descriptor.oracle)
        assertEquals(
            ComparisonPolicy(0, 100.0, 1, "Exact opaque RGBA8 output from independent hard pixel-center winding path clip membership and paint order."),
            evidenceCase.descriptor.comparison,
        )
        assertIs<KanvasSurfaceProgram>(evidenceCase.program)
        assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId)
        assertIs<org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathCpuOracle>(evidenceCase.oracle)

        val operations = ops(id)
        assertEquals(drawCount + 2, operations.size)
        assertEquals(
            DisplayOp.DrawColor(
                ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f),
                BlendMode.SRC_OVER,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            operations[0],
        )
        val clip = assertIs<DisplayOp.SetClip>(operations[1]).clip
        val complex = assertIs<ClipStack.Complex>(clip)
        assertEquals(1, complex.ops.size)
        val pathOp = assertIs<org.graphiks.kanvas.canvas.ClipStackOp.PathOp>(complex.ops.single())
        assertEquals(
            RectF32.ofLTRB(
                expectedPoints.minOf { it.x }, expectedPoints.minOf { it.y },
                expectedPoints.maxOf { it.x }, expectedPoints.maxOf { it.y },
            ),
            pathOp.path.computeBounds(),
        )
        assertTrue(PathMeasure(pathOp.path).isClosed)
        assertEquals(FillType.WINDING, pathOp.path.fillType)
        assertEquals(ClipOp.INTERSECT, pathOp.op)
        assertFalse(pathOp.antiAlias)
        assertEquals("identity", pathOp.transformClass)
        assertEquals(drawCount, expectedDraws.size)
        operations.drop(2).zip(expectedDraws).forEach { (operation, expected) ->
            val draw = assertIs<DisplayOp.DrawRect>(operation)
            assertEquals(expected.first, draw.rect)
            assertEquals(Matrix3x3F32.Identity, draw.transform)
            assertEquals(complex, draw.clip)
            assertFalse(draw.paint.antiAlias)
            assertEquals(expected.second, draw.paint.color)
            assertEquals(1f, draw.paint.color.a)
        }
    }

    private fun assertPathCase(
        id: String,
        fillType: FillType,
        bounds: RectF32,
        paint: Paint,
        convex: Boolean,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        oracleVersion: Int = 2,
        comparisonRationale: String = "Exact opaque RGBA8 output from independent pixel-center winding/even-odd polygon membership.",
    ) {
        val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id })
        assertEquals(64, evidenceCase.descriptor.width)
        assertEquals(64, evidenceCase.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
        assertEquals(OraclePolicy.GeneratedCpu("surface-srgb-path-pixel-center", oracleVersion), evidenceCase.descriptor.oracle)
        assertEquals(
            ComparisonPolicy(0, 100.0, 1, comparisonRationale),
            evidenceCase.descriptor.comparison,
        )
        assertTrue(evidenceCase.descriptor.requiredCapabilities.isEmpty())
        assertEquals("kanvas.surface.render", assertIs<KanvasSurfaceProgram>(evidenceCase.program).routeId)

        val operations = ops(id)
        assertEquals(if (transform == Matrix3x3F32.Identity) 2 else 3, operations.size)
        assertEquals(
            DisplayOp.DrawColor(ColorARGB.fromRGBA(13f / 255f, 20f / 255f, 33f / 255f), BlendMode.SRC_OVER, Matrix3x3F32.Identity, ClipStack.WideOpen),
            operations[0],
        )
        if (transform != Matrix3x3F32.Identity) {
            assertEquals(DisplayOp.SetTransform(transform), operations[1])
        }
        val drawPath = assertIs<DisplayOp.DrawPath>(operations.last())
        assertEquals(fillType, drawPath.path.fillType)
        assertEquals(bounds, drawPath.path.computeBounds())
        assertEquals(convex, drawPath.path.isConvex())
        assertEquals(paint, drawPath.paint)
        assertEquals(transform, drawPath.transform)
        assertEquals(ClipStack.WideOpen, drawPath.clip)
    }

    private fun ops(id: String): List<DisplayOp> {
        val program = assertIs<KanvasSurfaceProgram>(GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == id }.program)
        val session = assertIs<KanvasSurfaceRecordedSession>(program.openSession(64, 64))
        return session.snapshotOps()
    }

    private fun rgba(pixels: ByteArray, x: Int, y: Int, width: Int): List<Int> {
        val offset = (y * width + x) * 4
        return (0..3).map { pixels[offset + it].toInt() and 0xff }
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
