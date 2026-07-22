package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUCorePrimitiveSemanticBuilderTest {
    @Test
    fun `production builder and inventory return the exact same representative semantics`() {
        val inventory = inventory()
        val targetBounds = GPUPixelBounds(0, 0, 32, 24)

        val production = GPUCorePrimitiveSemanticBuilder.gather(
            visualCommands = inventory.visualCommands,
            recording = inventory.recording,
            targetBounds = targetBounds,
            blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
        )
        val harness = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(inventory, targetBounds)

        val productionGathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(production)
        val harnessGathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(harness)
        assertEquals(productionGathered.semantics.keys, harnessGathered.semantics.keys)
        productionGathered.semantics.forEach { (commandId, productionSemantic) ->
            val harnessSemantic = harnessGathered.semantics.getValue(commandId)
            assertEquals(productionSemantic.canonicalHash, harnessSemantic.canonicalHash)
            assertEquals(productionSemantic.payloadRef, harnessSemantic.payloadRef)
            assertEquals(productionSemantic.sourceFamily, harnessSemantic.sourceFamily)
            assertEquals(productionSemantic.geometry::class, harnessSemantic.geometry::class)
            assertEquals(productionSemantic.premultipliedRgba, harnessSemantic.premultipliedRgba)
            assertEquals(productionSemantic.targetBounds, harnessSemantic.targetBounds)
            assertEquals(productionSemantic.scissorBounds, harnessSemantic.scissorBounds)
            assertEquals(productionSemantic.clipCoveragePlan, harnessSemantic.clipCoveragePlan)
            assertEquals(productionSemantic.blendPlanIdentity, harnessSemantic.blendPlanIdentity)
            assertEquals(productionSemantic.frameProvenance, harnessSemantic.frameProvenance)
            assertEquals(productionSemantic.coverageMode, harnessSemantic.coverageMode)
            assertEquals(productionSemantic.analysisRecordId, harnessSemantic.analysisRecordId)
            assertEquals(productionSemantic.analysisCommandFamily, harnessSemantic.analysisCommandFamily)
            assertEquals(productionSemantic.rectRouteAuthority, harnessSemantic.rectRouteAuthority)
            assertEquals(productionSemantic.rectGeometryAuthority, harnessSemantic.rectGeometryAuthority)
            assertEquals(productionSemantic.rrectGeometryAuthority, harnessSemantic.rrectGeometryAuthority)
        }
    }

    @Test
    fun `production builder and inventory preserve the exact same semantic refusal`() {
        val inventory = inventory()
        val record = inventory.recording.analysis.records.single()
        val forged = inventory.copy(
            recording = inventory.recording.copy(
                analysis = inventory.recording.analysis.copy(records = listOf(record, record)),
            ),
        )
        val targetBounds = GPUPixelBounds(0, 0, 32, 24)

        val production = GPUCorePrimitiveSemanticBuilder.gather(
            visualCommands = forged.visualCommands,
            recording = forged.recording,
            targetBounds = targetBounds,
            blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
        )
        val harness = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(forged, targetBounds)

        assertEquals(production, harness)
        assertEquals(
            GPUCorePrimitiveSemanticGatherResult.Refused(
                code = "unsupported.core_primitive.analysis_record_bijection",
                message = "Core primitive geometry cannot be lowered exactly by the current canonical route.",
                facts = mapOf(
                    "matchingRecordCount" to "2",
                    "commandId" to "0",
                    "source" to "drawRect",
                ),
            ),
            production,
        )
    }

    @Test
    fun `inventory delegates semantic lowering and contains no copied lowering authority`() {
        val inventorySource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt",
        ).readText()
        val builderSource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt",
        ).readText()
        val forbiddenInventoryAuthorities = listOf(
            "GPUCorePrimitivePayloadGatherer",
            "toCorePrimitiveInput",
            "toDeviceGeometry",
            "pathDeviceGeometry",
            "strokeDeviceGeometry",
            "coverageMode()",
        )

        val delegation = "GPUCorePrimitiveSemanticBuilder.gather("
        assertEquals(1, inventorySource.split(delegation).size - 1)
        forbiddenInventoryAuthorities.forEach { authority ->
            assertFalse(authority in inventorySource, authority)
        }
        assertTrue("GPUCorePrimitivePayloadGatherer" in builderSource)
        assertTrue("strokeDeviceGeometry" in builderSource)
    }

    private fun inventory(): GPUFramePathInventoryPlan = GPUFramePathApiInventory.plan(
        operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(2f, 3f, 12f, 11f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        ),
        target = GPUTargetFacts(32, 24, "rgba8unorm"),
        config = RenderConfig.DEFAULT,
        capabilities = capabilities(),
    )

    private fun capabilities(): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts + GPUCapabilityFact(
                name = "first_slice.fill_rect.native",
                source = "test",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "test:first_slice.fill_rect.native",
            ),
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:semantic-builder-test",
        )
    }
}
