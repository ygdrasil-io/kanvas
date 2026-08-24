package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

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
            assertEquals(productionSemantic.canonicalType, harnessSemantic.canonicalType)
            assertEquals(productionSemantic.payloadRef, harnessSemantic.payloadRef)
            if (productionSemantic is GPUDrawSemanticPayload.CorePrimitive &&
                harnessSemantic is GPUDrawSemanticPayload.CorePrimitive
            ) {
                assertEquals(productionSemantic.canonicalHash, harnessSemantic.canonicalHash)
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

    @Test
    fun `gradient descriptor admission rejects non finite local matrix facts`() {
        val invalidMatrix = listOf(
            1f, 0f, 0f,
            0f, Float.NaN, 0f,
            0f, 0f, 1f,
        )
        assertFailsWith<IllegalArgumentException> {
            GPUMaterialDescriptor.GradientFacts(localMatrix = invalidMatrix)
        }

        assertFailsWith<IllegalArgumentException> {
            GPUMaterialDescriptor.GradientFacts(localMatrix = invalidMatrix.dropLast(1))
        }
    }

    @Test
    fun `core builder admits radial and sweep materials without collapsing them to solid`() {
        val radial = radialDescriptor(
            localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
        )
        val sweep = sweepDescriptor(
            localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
        )
        val negativeSweep = sweepDescriptor(
            startAngle = -450f,
            endAngle = -90f,
            localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
        )

        listOf(radial, sweep, negativeSweep).forEach { descriptor ->
            val result = gatherMaterial(descriptor)
            val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(result)
            val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semantics.getValue(0))

            when (descriptor) {
                is GPUMaterialDescriptor.RadialGradient -> {
                    val material = assertIs<GPUCorePrimitiveMaterialPayload.RadialGradient>(semantic.material)
                    assertEquals(descriptor.centerX, material.centerX)
                    assertEquals(descriptor.centerY, material.centerY)
                    assertEquals(descriptor.radius, material.radius)
                }
                is GPUMaterialDescriptor.SweepGradient -> {
                    val material = assertIs<GPUCorePrimitiveMaterialPayload.SweepGradient>(semantic.material)
                    assertEquals(descriptor.startAngle, material.startAngle)
                    assertEquals(descriptor.endAngle, material.endAngle)
                }
                else -> error("Unexpected gradient fixture")
            }
            assertEquals("clamp", semantic.material.tileMode)
            assertEquals("srgb", semantic.material.interpolation)
            val descriptorMatrix = when (descriptor) {
                is GPUMaterialDescriptor.RadialGradient -> descriptor.localMatrix
                is GPUMaterialDescriptor.SweepGradient -> descriptor.localMatrix
            }
            val semanticMatrix = when (val material = semantic.material) {
                is GPUCorePrimitiveMaterialPayload.LinearGradient -> material.localMatrix
                is GPUCorePrimitiveMaterialPayload.RadialGradient -> material.localMatrix
                is GPUCorePrimitiveMaterialPayload.SweepGradient -> material.localMatrix
                is GPUCorePrimitiveMaterialPayload.SolidColor ->
                    error("Unexpected solid fixture")
            }
            assertEquals(descriptorMatrix, semanticMatrix)
            assertNotNull(semantic.material.materialHash)
            assertTrue(semantic.canonicalHash.isNotBlank())
            assertNotNull(semantic.payloadRef.uniformBlock)
        }
    }

    @Test
    fun `core builder admits bounded linear material without collapsing it to solid`() {
        val descriptor = linearDescriptor()

        val result = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(gatherMaterial(descriptor))
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(result.semantics.getValue(0))
        val material = assertIs<GPUCorePrimitiveMaterialPayload.LinearGradient>(semantic.material)

        assertEquals(descriptor.startX, material.startX)
        assertEquals(descriptor.startY, material.startY)
        assertEquals(descriptor.endX, material.endX)
        assertEquals(descriptor.endY, material.endY)
        assertEquals("clamp", material.tileMode)
        assertEquals("srgb", material.interpolation)
    }

    @Test
    fun `core builder refuses unsupported linear gradient facts before semantic payload creation`() {
        val cases = listOf(
            linearDescriptor(tileMode = "repeat") to "unsupported.core_primitive.material.tile_mode",
            linearDescriptor(tileMode = "mirror") to "unsupported.core_primitive.material.tile_mode",
            linearDescriptor(tileMode = "decal") to "unsupported.core_primitive.material.tile_mode",
            linearDescriptor(interpolation = "linear") to "unsupported.core_primitive.material.interpolation",
            linearDescriptor(localMatrix = listOf(1f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f)) to
                "unsupported.core_primitive.material.matrix",
            linearDescriptor(startX = Float.NaN) to "unsupported.core_primitive.material.non_finite",
            linearDescriptor(endX = 0f, endY = 4f) to "unsupported.core_primitive.material.linear.axis",
            linearDescriptor(startX = -Float.MAX_VALUE, endX = 0f, startY = 0f, endY = 0f) to
                "unsupported.core_primitive.material.linear.axis",
            linearDescriptor(startX = 0f, endX = Float.MIN_VALUE, startY = 0f, endY = 0f) to
                "unsupported.core_primitive.material.linear.axis",
            linearDescriptor(positions = floatArrayOf(0f, Float.NaN)) to "unsupported.core_primitive.material.stops",
        )

        cases.forEach { (material, expectedCode) ->
            val refusal = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(gatherMaterial(material))
            assertEquals(expectedCode, refusal.code)
            assertEquals(GPUMaterialKind.LinearGradient.name, refusal.facts["materialKind"])
        }
    }

    @Test
    fun `core builder refuses linear path stencil before creating a semantic payload`() {
        val path = Path().apply {
            moveTo(2f, 2f)
            lineTo(20f, 2f)
            lineTo(8f, 9f)
            lineTo(20f, 20f)
            lineTo(2f, 20f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path = path,
                    paint = Paint(shader = Shader.LinearGradient(
                        start = Point2F32(2f, 2f),
                        end = Point2F32(20f, 20f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                    )).copy(antiAlias = false),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 24, "rgba8unorm"),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption.StencilCoverage1x,
            inventory.visualCommands.single().geometryCoverage,
        )

        val refusal = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(
            GPUCorePrimitiveSemanticBuilder.gather(
                visualCommands = inventory.visualCommands,
                recording = inventory.recording,
                targetBounds = GPUPixelBounds(0, 0, 32, 24),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )

        assertEquals("unsupported.core_primitive.material.path_stencil", refusal.code)
        assertEquals(GPUMaterialKind.LinearGradient.name, refusal.facts["materialKind"])
        assertEquals("0", refusal.facts["commandId"])
        assertEquals("drawPath", refusal.facts["source"])
    }

    @Test
    fun `core builder refuses invalid radial and sweep facts before semantic payload creation`() {
        val cases = listOf(
            radialDescriptor(tileMode = "repeat") to "unsupported.core_primitive.material.tile_mode",
            radialDescriptor(tileMode = "mirror") to "unsupported.core_primitive.material.tile_mode",
            radialDescriptor(tileMode = "decal") to "unsupported.core_primitive.material.tile_mode",
            radialDescriptor(radius = 0f) to "unsupported.core_primitive.material.radial.radius",
            radialDescriptor(centerX = Float.NaN) to "unsupported.core_primitive.material.non_finite",
            radialDescriptor(positions = floatArrayOf(0f, Float.NaN)) to
                "unsupported.core_primitive.material.stops",
            radialDescriptor(
                positions = FloatArray(17) { it / 16f },
                colors = FloatArray(68) { 1f },
            ) to "unsupported.core_primitive.material.stops",
            radialDescriptor(interpolation = "linear") to
                "unsupported.core_primitive.material.interpolation",
            radialDescriptor(interpolation = "oklab") to
                "unsupported.core_primitive.material.interpolation",
            radialDescriptor(interpolation = "hsl") to
                "unsupported.core_primitive.material.interpolation",
            radialDescriptor(interpolation = "oklch") to
                "unsupported.core_primitive.material.interpolation",
            sweepDescriptor(startAngle = Float.NaN) to "unsupported.core_primitive.material.non_finite",
            sweepDescriptor(endAngle = Float.POSITIVE_INFINITY) to
                "unsupported.core_primitive.material.non_finite",
            sweepDescriptor(startAngle = 20f, endAngle = 20f) to
                "unsupported.core_primitive.material.sweep.range",
            sweepDescriptor(startAngle = 20f, endAngle = 10f) to
                "unsupported.core_primitive.material.sweep.range",
            sweepDescriptor(startAngle = -720f, endAngle = 0f) to
                "unsupported.core_primitive.material.sweep.range",
        )

        cases.forEach { (material, expectedCode) ->
            val first = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(gatherMaterial(material))
            val second = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(gatherMaterial(material))

            assertEquals(expectedCode, first.code)
            assertEquals(first, second)
            assertEquals(material.kind.name, first.facts["materialKind"])
            when (material) {
                is GPUMaterialDescriptor.RadialGradient -> {
                    assertEquals(material.tileMode, first.facts["tileMode"])
                    assertEquals(material.interpolation, first.facts["interpolation"])
                }
                is GPUMaterialDescriptor.SweepGradient -> {
                    assertEquals(material.tileMode, first.facts["tileMode"])
                    assertEquals(material.interpolation, first.facts["interpolation"])
                }
                else -> error("Unexpected gradient fixture")
            }
            assertNotNull(first.facts["materialHash"])
        }
    }

    @Test
    fun `core builder keeps the legacy refusal for unsupported material families`() {
        val materials = listOf<GPUMaterialDescriptor>(
            GPUMaterialDescriptor.ConicalGradient(
                startX = 0f,
                startY = 0f,
                endX = 8f,
                endY = 8f,
                startRadius = 1f,
                endRadius = 4f,
                startR = 1f,
                startG = 0f,
                startB = 0f,
                startA = 1f,
                endR = 0f,
                endG = 0f,
                endB = 1f,
                endA = 1f,
            ),
            GPUMaterialDescriptor.ImageDraw(),
            GPUMaterialDescriptor.RuntimeEffect(),
            GPUMaterialDescriptor.BlendShader(
                mode = "SRC_OVER",
                dst = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                src = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
            ),
        )

        materials.forEach { material ->
            val refusal = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(gatherMaterial(material))
            assertEquals("unsupported.core_primitive.material.non_solid", refusal.code)
            assertEquals(material.kind.name, refusal.facts["materialKind"])
            assertEquals("none", refusal.facts["tileMode"])
            assertEquals("none", refusal.facts["interpolation"])
            assertNotNull(refusal.facts["materialHash"])
        }
    }

    private fun inventory(
        paint: Paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false),
    ): GPUFramePathInventoryPlan = GPUFramePathApiInventory.plan(
        operations = listOf(
            DisplayOp.DrawRect(
                RectF32.ofLTRB(2f, 3f, 12f, 11f),
                paint,
                Matrix3x3F32.Identity,
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

    private fun gatherMaterial(
        material: GPUMaterialDescriptor,
    ): GPUCorePrimitiveSemanticGatherResult {
        val base = inventory()
        val visual = base.visualCommands.single()
        val normalized = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
        return GPUCorePrimitiveSemanticBuilder.gather(
            visualCommands = listOf(visual.copy(normalized = normalized.copy(material = material))),
            recording = base.recording,
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
        )
    }

    private fun radialDescriptor(
        centerX: Float = 4f,
        radius: Float = 4f,
        tileMode: String = "clamp",
        interpolation: String = "srgb",
        positions: FloatArray = floatArrayOf(0f, 1f),
        colors: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        localMatrix: List<Float> = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
    ) = GPUMaterialDescriptor.RadialGradient(
        centerX = centerX,
        centerY = 4f,
        radius = radius,
        startR = 1f,
        startG = 0f,
        startB = 0f,
        startA = 1f,
        endR = 0f,
        endG = 0f,
        endB = 1f,
        endA = 1f,
        tileMode = tileMode,
        allStopPositions = positions,
        allStopColors = colors,
    ).withGradientFacts(
        GPUMaterialDescriptor.GradientFacts(
            interpolation = interpolation,
            localMatrix = localMatrix,
        ),
    )

    private fun sweepDescriptor(
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        localMatrix: List<Float> = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
    ) = GPUMaterialDescriptor.SweepGradient(
        centerX = 4f,
        centerY = 4f,
        startAngle = startAngle,
        endAngle = endAngle,
        startR = 1f,
        startG = 0f,
        startB = 0f,
        startA = 1f,
        endR = 0f,
        endG = 0f,
        endB = 1f,
        endA = 1f,
        tileMode = "clamp",
        allStopPositions = floatArrayOf(0f, 1f),
        allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
    ).withGradientFacts(
        GPUMaterialDescriptor.GradientFacts(localMatrix = localMatrix),
    )

    private fun linearDescriptor(
        startX: Float = 0f,
        startY: Float = 4f,
        endX: Float = 8f,
        endY: Float = 4f,
        tileMode: String = "clamp",
        interpolation: String = "srgb",
        localMatrix: List<Float> = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
        positions: FloatArray = floatArrayOf(0f, 1f),
        colors: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
    ) = GPUMaterialDescriptor.LinearGradient(
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        startR = 1f,
        startG = 0f,
        startB = 0f,
        startA = 1f,
        endR = 0f,
        endG = 0f,
        endB = 1f,
        endA = 1f,
        tileMode = tileMode,
        allStopPositions = positions,
        allStopColors = colors,
    ).withGradientFacts(
        GPUMaterialDescriptor.GradientFacts(
            interpolation = interpolation,
            localMatrix = localMatrix,
        ),
    )
}
