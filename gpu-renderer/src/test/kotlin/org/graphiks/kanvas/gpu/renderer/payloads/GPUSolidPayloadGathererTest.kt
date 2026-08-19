package org.graphiks.kanvas.gpu.renderer.payloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies first-slice solid payload gathering without resource upload. */
class GPUSolidPayloadGathererTest {
    /** Solid rect payloads produce deterministic uniform block and slot facts. */
    @Test
    fun `solid rect gather writes color and rect intrinsic payload slots without upload`() {
        val gatherer = GPUSolidPayloadGatherer()
        val ref = gatherer.gather(solidGatherPlan(), solidPayload(commandId = 9))

        val uniformSlot = assertNotNull(ref.uniformSlot)
        val uniformBlock = assertNotNull(ref.uniformBlock)
        val fieldsByName = uniformBlock.fields.associateBy { it.fieldPath }

        assertEquals(9, ref.commandIdValue)
        assertEquals("rect-step:v1", ref.renderStepIdentity)
        assertEquals(GPUPayloadSlotID("pass-a:uniform:0"), uniformSlot.slotId)
        assertEquals(uniformBlock.fingerprint, uniformSlot.fingerprint)
        assertNull(ref.resourceSlot)
        assertNull(ref.resourceBlock)

        assertEquals(64L, uniformBlock.byteSize)
        assertEquals(64, uniformBlock.bytes.size)
        assertEquals("solid-rect-layout-v1", uniformBlock.packingPlanHash)
        assertEquals("pass-a", uniformBlock.scope)
        assertTrue(uniformBlock.zeroedPadding)

        assertEquals(0L, fieldsByName.getValue("rect.left").byteOffset)
        assertEquals(4L, fieldsByName.getValue("rect.top").byteOffset)
        assertEquals(8L, fieldsByName.getValue("rect.right").byteOffset)
        assertEquals(12L, fieldsByName.getValue("rect.bottom").byteOffset)
        assertEquals(16L, fieldsByName.getValue("radii.topLeft").byteOffset)
        assertEquals(20L, fieldsByName.getValue("radii.topRight").byteOffset)
        assertEquals(24L, fieldsByName.getValue("radii.bottomRight").byteOffset)
        assertEquals(28L, fieldsByName.getValue("radii.bottomLeft").byteOffset)
        assertEquals(32L, fieldsByName.getValue("color.r").byteOffset)
        assertEquals(36L, fieldsByName.getValue("color.g").byteOffset)
        assertEquals(40L, fieldsByName.getValue("color.b").byteOffset)
        assertEquals(44L, fieldsByName.getValue("color.a").byteOffset)
        assertEquals(48L, fieldsByName.getValue("padding.reserved").byteOffset)
        assertTrue(uniformBlock.bytes.drop(48).all { it == 0 })
    }

    /** Payload value changes affect payload slots, not durable key identity. */
    @Test
    fun `solid payload deduplicates equal values and separates changed rgba values`() {
        val gatherer = GPUSolidPayloadGatherer()
        val plan = solidGatherPlan()
        val first = gatherer.gather(plan, solidPayload(commandId = 1))
        val duplicate = gatherer.gather(plan, solidPayload(commandId = 2))
        val changedColor = gatherer.gather(plan, solidPayload(commandId = 3, r = "0.90"))

        assertEquals(first.uniformSlot?.slotId, duplicate.uniformSlot?.slotId)
        assertEquals(first.uniformBlock?.fingerprint, duplicate.uniformBlock?.fingerprint)
        assertNotEquals(first.uniformSlot?.slotId, changedColor.uniformSlot?.slotId)
        assertNotEquals(first.uniformBlock?.fingerprint, changedColor.uniformBlock?.fingerprint)
    }

    /** Concrete resource facts are ignored for solid payloads. */
    @Test
    fun `solid payload ignores concrete resource facts`() {
        val gatherer = GPUSolidPayloadGatherer()
        val plan = solidGatherPlan()
        val withoutResources = gatherer.gather(plan, solidPayload(commandId = 1))
        val withConcreteResources = gatherer.gather(
            plan,
            solidPayload(commandId = 2).copy(
                resourceFacts = mapOf(
                    "texture.handle" to "native-texture-42",
                    "surface.lease" to "lease-99",
                    "bindGroup.instance" to "bind-group-object-7",
                    "resource.address" to "0xdeadbeef",
                ),
            ),
        )

        assertEquals(withoutResources.uniformSlot?.slotId, withConcreteResources.uniformSlot?.slotId)
        assertNull(withConcreteResources.resourceSlot)
        assertNull(withConcreteResources.resourceBlock)
    }

    /** Required uniform fields must refuse instead of silently becoming zero-valued payload bytes. */
    @Test
    fun `solid payload refuses missing malformed and non finite required fields`() {
        val gatherer = GPUSolidPayloadGatherer()
        val plan = solidGatherPlan()

        val missingRect = solidPayload(commandId = 1).copy(
            valueFacts = solidPayload(commandId = 1).valueFacts - "rect.left",
        )
        val malformedColor = solidPayload(commandId = 2).copy(
            valueFacts = solidPayload(commandId = 2).valueFacts + ("color.r" to "red"),
        )
        val nonFiniteAlpha = solidPayload(commandId = 3).copy(
            valueFacts = solidPayload(commandId = 3).valueFacts + ("color.a" to "NaN"),
        )

        assertFailsWith<IllegalArgumentException> { gatherer.gather(plan, missingRect) }
        assertFailsWith<IllegalArgumentException> { gatherer.gather(plan, malformedColor) }
        assertFailsWith<IllegalArgumentException> { gatherer.gather(plan, nonFiniteAlpha) }
    }

    /** Field metadata must describe actual zero-filled values instead of hard-coded field families. */
    @Test
    fun `solid payload field metadata reports non zero radii as payload values`() {
        val gatherer = GPUSolidPayloadGatherer()
        val ref = gatherer.gather(
            solidGatherPlan(),
            solidPayload(commandId = 4).copy(
                valueFacts = solidPayload(commandId = 4).valueFacts + ("radii.topLeft" to "3.50"),
            ),
        )

        val fieldsByName = requireNotNull(ref.uniformBlock).fields.associateBy { it.fieldPath }

        assertFalse(fieldsByName.getValue("radii.topLeft").zeroFilled)
        assertTrue(fieldsByName.getValue("radii.topRight").zeroFilled)
        assertTrue(fieldsByName.getValue("padding.reserved").zeroFilled)
    }

    @Test
    fun `solid semantic payload recursively snapshots gatherer bytes fields and explicit zero radii`() {
        val gathered = GPUSolidPayloadGatherer().gather(solidGatherPlan(), solidPayload(commandId = 12))
        val sourceBytes = requireNotNull(gathered.uniformBlock).bytes.toMutableList()
        val sourceFields = gathered.uniformBlock.fields.toMutableList()
        val semantic = GPUDrawSemanticPayload.SolidRect(
            gathered.copy(
                uniformBlock = gathered.uniformBlock.copy(bytes = sourceBytes, fields = sourceFields),
            ),
        )
        val expectedBytes = semantic.payloadRef.uniformBlock!!.bytes
        val expectedFields = semantic.payloadRef.uniformBlock!!.fields

        sourceBytes.fill(255)
        sourceFields.clear()

        assertEquals(expectedBytes, semantic.payloadRef.uniformBlock!!.bytes)
        assertEquals(expectedFields, semantic.payloadRef.uniformBlock!!.fields)
        assertNull(semantic.payloadRef.resourceBlock)
        assertTrue(semantic.payloadRef.uniformBlock!!.bytes.subList(16, 32).all { it == 0 })
        assertEquals(
            listOf("radii.topLeft", "radii.topRight", "radii.bottomRight", "radii.bottomLeft"),
            semantic.payloadRef.uniformBlock!!.fields.subList(4, 8).map { it.fieldPath },
        )
        assertTrue(semantic.payloadRef.uniformBlock!!.fields.subList(4, 8).all { it.zeroFilled })
    }

    @Test
    fun `solid gatherer is the semantic payload packing and validation authority`() {
        val semantic = GPUSolidPayloadGatherer().gatherSemantic(
            solidGatherPlan(),
            solidPayload(commandId = 13, r = "0.75", g = "0.50", b = "0.25", a = "1.0"),
        )

        assertEquals(13, semantic.payloadRef.commandIdValue)
        assertEquals("rect-step:v1", semantic.payloadRef.renderStepIdentity)
        assertEquals(semantic.payloadRef.uniformBlock!!.fingerprint, semantic.payloadRef.uniformSlot!!.fingerprint)
        assertEquals("solid-rect-layout-v1", semantic.payloadRef.uniformBlock!!.packingPlanHash)
        assertEquals(64, semantic.payloadRef.uniformBlock!!.bytes.size)
    }

    private fun solidGatherPlan(): GPUPayloadGatherPlan =
        GPUPayloadGatherPlan(
            planHash = "solid-gather-v1",
            commandFamily = "FillRect",
            materialAssemblyHash = "solid-material-assembly-v1",
            renderStepIdentity = "rect-step:v1",
            writePlanHash = "solid-write-v1",
            bindingPlanHash = "no-resources",
            uploadPlanHash = "no-upload",
            dedupScope = "pass-a",
        )

    private fun solidPayload(
        commandId: Int,
        r: String = "0.10",
        g: String = "0.20",
        b: String = "0.30",
        a: String = "0.40",
    ): GPUMaterialPayload =
        GPUMaterialPayload(
            materialKeyHash = "solid-material-key",
            payloadClass = "solid-rgba-rect",
            valueFacts = mapOf(
                "command.id" to commandId.toString(),
                "rect.left" to "1.50",
                "rect.top" to "2.25",
                "rect.right" to "10.50",
                "rect.bottom" to "22.25",
                "radii.topLeft" to "0.0",
                "radii.topRight" to "0.0",
                "radii.bottomRight" to "0.0",
                "radii.bottomLeft" to "0.0",
                "color.r" to r,
                "color.g" to g,
                "color.b" to b,
                "color.a" to a,
            ),
            resourceFacts = emptyMap(),
            diagnosticLabel = "unit:solid-rect#$commandId",
        )
}
