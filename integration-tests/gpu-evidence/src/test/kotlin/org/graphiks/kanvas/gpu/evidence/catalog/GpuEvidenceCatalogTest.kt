package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a

class GpuEvidenceCatalogTest {
    @Test
    fun `catalog separates five public surface renders from two renderer refusals`() {
        val cases = GpuEvidenceCatalog.cases

        assertEquals(
            listOf(
                "solid-card-stack",
                "separable-blur-rect",
                "translucent-card-overlap",
                "scissor-overlay",
                "stroke-rect-outline",
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
            listOf("kanvas.surface.render", "kanvas.surface.render", "kanvas.surface.render", "kanvas.surface.render", "kanvas.surface.render"),
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

        listOf("translucent-card-overlap", "scissor-overlay", "stroke-rect-outline").forEach { id ->
            val evidenceCase = assertNotNull(cases.firstOrNull { it.descriptor.id.value == id })
            assertEquals(64, evidenceCase.descriptor.width)
            assertEquals(64, evidenceCase.descriptor.height)
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertNotNull(evidenceCase.oracle)
            assertNotNull(evidenceCase.descriptor.comparison)
        }
        assertEquals(1, cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.comparison?.perChannelTolerance)
        assertEquals(0, cases.first { it.descriptor.id.value == "scissor-overlay" }.descriptor.comparison?.perChannelTolerance)
        assertEquals(0, cases.first { it.descriptor.id.value == "stroke-rect-outline" }.descriptor.comparison?.perChannelTolerance)

        val budget = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "aggregate-memory-budget-refusal" })
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", assertIs<EvidenceExpectation.ShouldRefuse>(budget.descriptor.expectation).stableReasonCode)
        assertEquals(null, budget.oracle)
    }

    @Test
    fun `public surface programs record only the requested Canvas operations`() {
        val solid = ops("solid-card-stack")
        assertEquals(3, solid.size)
        assertIs<DisplayOp.DrawColor>(solid[0])
        assertEquals(
            listOf(Rect.fromLTRB(8f, 10f, 56f, 34f), Rect.fromLTRB(14f, 38f, 50f, 54f)),
            solid.drop(1).map { assertIs<DisplayOp.DrawRect>(it).rect },
        )

        val blur = assertIs<DisplayOp.DrawRect>(ops("separable-blur-rect").single())
        assertEquals(Rect.fromLTRB(16f, 16f, 48f, 48f), blur.rect)
        assertEquals(false, blur.paint.antiAlias)
        assertEquals(MaskFilter.Blur(BlurStyle.NORMAL, 3f), blur.paint.maskFilter)

        val translucent = ops("translucent-card-overlap")
        assertIs<DisplayOp.DrawColor>(translucent[0])
        assertEquals(2, translucent.drop(1).count { it is DisplayOp.DrawRect })
        assertEquals(listOf(128f / 255f, 128f / 255f), translucent.drop(1).map { assertIs<DisplayOp.DrawRect>(it).paint.color.a })

        val scissor = ops("scissor-overlay")
        assertEquals(2, scissor.count { it is DisplayOp.SetClip })
        assertEquals(2, scissor.count { it is DisplayOp.DrawRect })
        assertEquals(
            listOf(Rect.fromLTRB(16f, 16f, 40f, 40f), Rect.fromLTRB(24f, 24f, 48f, 48f)),
            scissor.filterIsInstance<DisplayOp.DrawRect>().map { it.clip }.map { clip ->
                assertIs<org.graphiks.kanvas.canvas.ClipStack.DeviceRect>(clip).rect
            },
        )

        val stroke = ops("stroke-rect-outline").filterIsInstance<DisplayOp.DrawRect>().single()
        assertEquals(PaintStyle.STROKE, stroke.paint.style)
        assertEquals(6f, stroke.paint.strokeWidth)
        assertEquals(false, stroke.paint.antiAlias)
    }

    private fun ops(id: String): List<DisplayOp> {
        val program = assertIs<KanvasSurfaceProgram>(GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == id }.program)
        val recordField = KanvasSurfaceProgram::class.java.getDeclaredField("record").also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val record = recordField.get(program) as Function1<org.graphiks.kanvas.canvas.Canvas, Unit>
        return Surface(64, 64).also { surface -> surface.canvas { record(this) } }.snapshotOps()
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
