package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class RuntimeEffectChildTreeW51Test {
    private val registry = KanvasRuntimeEffectRegistry()
    private val snapshot = registry.snapshot()

    @Test
    fun `tree preserves ordered children and shared CPU GPU semantic inputs`() {
        val tree = GPURuntimeEffectChildTree(
            descriptorId = GPURuntimeEffectID("runtime.compose_cf"),
            children = listOf(
                GPURuntimeEffectChildTree.Child("inner", GPURuntimeEffectChildKind.ColorFilter),
                GPURuntimeEffectChildTree.Child("outer", GPURuntimeEffectChildKind.ColorFilter),
            ),
            uniformSchemaHash = "schema:compose_cf:v1",
            usesLocalCoordinates = true,
        )
        val result = assertIs<GPURuntimeEffectChildTreeResult.Accepted>(tree.validate(snapshot))
        assertEquals(listOf("inner", "outer"), result.plan.orderedChildren.map { it.slot })
        assertEquals(result.plan.cpuChildren, result.plan.gpuChildren)
        assertEquals(true, result.plan.usesLocalCoordinates)
    }

    @Test
    fun `optional null child is accepted only for optional slot`() {
        val optionalDescriptor = requireNotNull(snapshot.lookup(GPURuntimeEffectID("runtime.compose_cf"))).copy(
            childSlots = listOf(GPURuntimeEffectChildSlotPlan("inner", setOf("color-filter"), required = false)),
        )
        val tree = GPURuntimeEffectChildTree(
            GPURuntimeEffectID("runtime.compose_cf"),
            children = listOf(GPURuntimeEffectChildTree.Child("inner", GPURuntimeEffectChildKind.ColorFilter, present = false)),
            uniformSchemaHash = "schema:compose_cf:v1",
        )
        assertIs<GPURuntimeEffectChildTreeResult.Accepted>(tree.validate(snapshot.copy(descriptors = listOf(optionalDescriptor))))
        val requiredMissing = GPURuntimeEffectChildTree(
            GPURuntimeEffectID("runtime.compose_cf"), emptyList(), "schema:compose_cf:v1",
        )
        assertEquals("unsupported.runtime_effect.child_missing", (requiredMissing.validate(snapshot) as GPURuntimeEffectChildTreeResult.Refused).code)
    }

    @Test
    fun `kind mismatch and excessive depth refuse before CPU or GPU routes`() {
        for (kind in listOf(GPURuntimeEffectChildKind.Shader, GPURuntimeEffectChildKind.Blender)) {
            val mismatch = GPURuntimeEffectChildTree(
                GPURuntimeEffectID("runtime.compose_cf"),
                listOf(GPURuntimeEffectChildTree.Child("inner", kind)),
                "schema:compose_cf:v1",
            )
            assertEquals(
                "unsupported.runtime_effect.child_kind_mismatch",
                (mismatch.validate(snapshot) as GPURuntimeEffectChildTreeResult.Refused).code,
            )
        }

        val deep = GPURuntimeEffectChildTree(
            GPURuntimeEffectID("runtime.compose_cf"), emptyList(), "schema:compose_cf:v1",
            nestedDepth = 65,
        )
        assertEquals("unsupported.runtime_effect.child_depth_exceeded", (deep.validate(snapshot) as GPURuntimeEffectChildTreeResult.Refused).code)
    }

    @Test
    fun `accepted plan snapshots caller child list for both routes`() {
        val source = mutableListOf(
            GPURuntimeEffectChildTree.Child("inner", GPURuntimeEffectChildKind.ColorFilter),
            GPURuntimeEffectChildTree.Child("outer", GPURuntimeEffectChildKind.ColorFilter),
        )
        val plan = assertIs<GPURuntimeEffectChildTreeResult.Accepted>(
            GPURuntimeEffectChildTree(
                GPURuntimeEffectID("runtime.compose_cf"), source, "schema:compose_cf:v1",
            ).validate(snapshot),
        ).plan

        source.clear()
        assertEquals(2, plan.orderedChildren.size)
        assertEquals(plan.orderedChildren, plan.cpuChildren)
        assertEquals(plan.orderedChildren, plan.gpuChildren)
        assertFailsWith<IllegalArgumentException> {
            GPURuntimeEffectChildTree(
                GPURuntimeEffectID("runtime.compose_cf"), emptyList(), "", nestedDepth = 0,
            )
        }
    }
}
