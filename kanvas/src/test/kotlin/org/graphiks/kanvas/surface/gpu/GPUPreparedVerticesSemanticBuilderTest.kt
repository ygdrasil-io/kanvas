package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedVerticesSemanticBuilderTest {
    @Test
    fun `core and prepared vertices form one ordered command recording and semantic bijection`() {
        val inventory = plan(
            listOf(
                rect(),
                vertices(Color.RED, Matrix33.identity()),
                vertices(Color.BLUE, Matrix33.translate(3f, 4f)),
            ),
        )

        assertEquals(listOf(0, 1, 2), inventory.normalizedCommands.map { it.commandId.value })
        assertEquals(listOf(0, 1, 2), inventory.recording.recordedCommands.map { it.commandId.value })
        assertEquals(listOf(0, 1, 2), inventory.recording.analysis.records.map { it.commandIdValue })
        assertEquals(
            listOf(0),
            inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).map { it.commandIdValue },
        )
        val semanticOnlyDraws = inventory.recording.semanticOnlyDraws
        assertEquals(listOf(1, 2), semanticOnlyDraws.map { it.packet.commandIdValue })
        val semanticOnlyTasks = inventory.recording.taskList.tasks
            .filterIsInstance<GPUTask.SemanticOnly>()
        assertEquals(listOf(1, 2), semanticOnlyTasks.map { it.draw.packet.commandIdValue })
        assertEquals(
            listOf("unsupported.preflight.prepared_vertices_unmaterialized"),
            inventory.framePlan.steps.filterIsInstance<GPUFrameStep.RefusedLeafDrawStep>()
                .map { it.diagnostic.code.value }.distinct(),
        )
        @Suppress("UNCHECKED_CAST")
        assertTrue(
            runCatching {
                (semanticOnlyDraws as MutableList<org.graphiks.kanvas.gpu.renderer.recording.GPUSemanticOnlyDraw>)
                    .clear()
            }.isFailure,
        )
        semanticOnlyDraws.forEach { draw ->
            assertEquals("prepared_vertices_unmaterialized", draw.stateLabel)
            assertNull(draw.packet.renderPipelineKey)
            assertNull(draw.packet.computePipelineKey)
        }
        assertTrue(inventory.recording.analysisDecisionDump.lines[1].startsWith("decision:discard:"))
        assertTrue(inventory.recording.analysisDecisionDump.lines[2].startsWith("decision:discard:"))
        assertEquals(
            listOf(0, 1, 2),
            inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).map { it.commandIdValue } +
                semanticOnlyDraws.map { it.packet.commandIdValue },
        )

        val gatherResult = GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                inventory = inventory,
                targetBounds = BOUNDS,
                imageArtifactsByCommandId = emptyMap(),
            )
        val gathered = assertIs<GPUPreparedSurfaceSemanticGatherResult.Gathered>(
            gatherResult,
            (gatherResult as? GPUPreparedSurfaceSemanticGatherResult.Refused)?.diagnostic.toString(),
        )
        assertEquals(listOf(0, 1, 2), gathered.semanticsByCommandId.keys.toList())
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semanticsByCommandId.getValue(0))
        val first = assertIs<GPUDrawSemanticPayload.Vertices>(gathered.semanticsByCommandId.getValue(1))
        val second = assertIs<GPUDrawSemanticPayload.Vertices>(gathered.semanticsByCommandId.getValue(2))
        assertEquals(
            assertIs<NormalizedDrawCommand.DrawPreparedVertices>(inventory.normalizedCommands[1]).materialIdentity,
            first.materialIdentity,
        )
        val firstInventoryCommand = inventory.preparedVerticesInventory!!.mappedCommands
            .first { mapped -> mapped.commandId == 1 }
            .let { mapped ->
                inventory.preparedVerticesInventory.commandsByOperationIndex.getValue(mapped.operationIndex)
            }
        assertSame(firstInventoryCommand.materialFrameSnapshot.program, first.material)
        assertEquals(first.artifact.key, second.artifact.key)
        assertNotEquals(first.material, second.material)
        assertNotEquals(first.transformBytes, second.transformBytes)
        assertNotEquals(first.canonicalHash, second.canonicalHash)
        assertEquals("rgba8unorm", first.targetFormat)
        assertEquals(CAPABILITIES.canonicalSnapshotHash(), first.capabilitySnapshotHash)
        assertNotNull(inventory.preparedVerticesInventory)

        val swapped = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = inventory.visualCommands,
                normalizedCommands = inventory.normalizedCommands,
                recording = inventory.recording,
                targetBounds = BOUNDS,
                imageArtifactsByCommandId = emptyMap(),
                verticesSemanticsByCommandId = mapOf(1 to second, 2 to first),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )
        assertEquals("invalid.surface.prepared.semantic-command-bijection", swapped.diagnostic.code.value)
    }

    @Test
    fun `prepared vertices recording source contains no Task 8 pass or pipeline anticipation`() {
        val recordingSource = File(
            "../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/" +
                "RecordingContracts.kt",
        ).readText()
        val passSource = File(
            "../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt",
        ).readText()

        assertTrue("pending.pipeline.prepared_vertices" !in recordingSource)
        assertTrue("acceptedPreparedVertices" !in passSource)
        assertTrue("resourceDeclarations = listOf(\"vertices:" !in recordingSource)
    }

    @Test
    fun `prepared vertices unmaterialized preflight code has one shared production authority`() {
        val productionRoots = listOf(
            File("../gpu-renderer/src/main/kotlin"),
            File("src/main/kotlin"),
        )
        val literal = "unsupported.preflight.prepared_vertices_unmaterialized"
        val occurrences = productionRoots.flatMap { root ->
            root.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList()
        }.sumOf { file -> Regex(Regex.escape(literal)).findAll(file.readText()).count() }

        assertEquals(
            literal,
            PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE,
        )
        assertEquals(1, occurrences)
    }

    @Test
    fun `missing duplicate reordered and type-confused vertices authorities refuse atomically`() {
        val inventory = plan(listOf(rect(), vertices(Color.RED), vertices(Color.BLUE)))
        val prepared = assertNotNull(inventory.preparedVerticesInventory)
        val verticesIds = prepared.artifactKeyByCommandId.keys
        assertEquals(setOf(1, 2), verticesIds)

        val firstMapped = prepared.mappedCommands[0]
        val secondMapped = prepared.mappedCommands[1]
        fun mapped(
            commandId: Int = secondMapped.commandId,
            operationIndex: Int = secondMapped.operationIndex,
            artifactKey: String = secondMapped.artifactKey,
        ) = PreparedVerticesMappedCommand(
            commandId,
            operationIndex,
            artifactKey,
            secondMapped.frameProvenance,
        )

        listOf(
            inventory.copy(normalizedCommands = inventory.normalizedCommands.dropLast(1)),
            inventory.copy(normalizedCommands = inventory.normalizedCommands + inventory.normalizedCommands.last()),
            inventory.copy(normalizedCommands = inventory.normalizedCommands.reversed()),
            inventory.copy(
                preparedVerticesInventory = inventory.preparedVerticesInventory.testRebindCommandIds(
                    mapOf(1 to 0, 2 to 2),
                ),
            ),
            inventory.copy(
                preparedVerticesInventory = prepared.testWithMappedCommands(
                    listOf(firstMapped, mapped(commandId = firstMapped.commandId)),
                ),
            ),
            inventory.copy(
                preparedVerticesInventory = prepared.testWithMappedCommands(
                    listOf(firstMapped, mapped(operationIndex = firstMapped.operationIndex)),
                ),
            ),
            inventory.copy(
                preparedVerticesInventory = prepared.testWithMappedCommands(
                    listOf(firstMapped, mapped(operationIndex = 999)),
                ),
            ),
            inventory.copy(
                preparedVerticesInventory = prepared.testWithMappedCommands(
                    listOf(firstMapped, mapped(artifactKey = "forged.artifact")),
                ),
            ),
        ).forEach { malformed ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = malformed,
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.semantic-command-bijection",
                refused.diagnostic.code.value,
            )
        }

        val vertices = assertIs<NormalizedDrawCommand.DrawPreparedVertices>(
            inventory.normalizedCommands[1],
        )
        val changedTransform = vertices.transformBytes.toMutableList().also {
            it[2] = 1f.toRawBits()
        }
        listOf(
            vertices.withAuthorities(topologyIdentity = "TriangleStrip"),
            vertices.withAuthorities(layoutIdentity = "forged.layout"),
            vertices.withAuthorities(materialIdentity = "sha256:${"f".repeat(64)}"),
            vertices.withAuthorities(transformBytes = changedTransform),
            vertices.withAuthorities(transform = vertices.transform.copy(translateX = 999f)),
            vertices.withAuthorities(capabilitySnapshotHash = "capability:forged"),
        ).forEach { changed ->
            val authorityRefusal = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = inventory.copy(
                        normalizedCommands = inventory.normalizedCommands.toMutableList().also {
                            it[1] = changed
                        },
                    ),
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.vertices-semantic-authority",
                authorityRefusal.diagnostic.code.value,
            )
        }
    }

    @Test
    fun `analysis and semantic-only projections must preserve normalized command order`() {
        val inventory = plan(listOf(rect(), vertices(Color.RED), vertices(Color.BLUE)))
        val reorderedAnalysis = inventory.copy(
            recording = inventory.recording.copy(
                analysis = inventory.recording.analysis.copy(
                    records = inventory.recording.analysis.records.reversed(),
                ),
            ),
        )
        val reorderedSemanticOnly = inventory.copy(
            recording = inventory.recording.copy(
                semanticOnlyDrawEntries = inventory.recording.semanticOnlyDraws.reversed(),
            ),
        )
        val reorderedTasks = inventory.copy(
            recording = inventory.recording.copy(
                taskList = inventory.recording.taskList.testWithTasks(
                    inventory.recording.taskList.tasks.reversed(),
                ),
            ),
        )
        val prepared = assertNotNull(inventory.preparedVerticesInventory)
        val reorderedMappedCommands = inventory.copy(
            preparedVerticesInventory = prepared.testWithMappedCommands(
                prepared.mappedCommands.reversed(),
            ),
        )

        listOf(
            reorderedAnalysis,
            reorderedSemanticOnly,
            reorderedTasks,
            reorderedMappedCommands,
        ).forEach { malformed ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = malformed,
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.semantic-command-bijection",
                refused.diagnostic.code.value,
            )
        }
    }

    @Test
    fun `record packet and target authority mutations refuse before semantic publication`() {
        val inventory = plan(listOf(vertices(Color.RED)))
        val record = inventory.recording.analysis.records.single()
        val recordMutations = listOf(
            record.copy(recordId = "forged.record"),
            record.copy(commandFamily = "ForgedFamily"),
            record.copy(boundsHash = "forged.bounds"),
            record.copy(routeDecisionLabel = "forged.route"),
            record.copy(materialKeyHash = "forged.material"),
            record.copy(renderStepCandidates = listOf("forged.step")),
            record.copy(sortKey = org.graphiks.kanvas.gpu.renderer.analysis.SortKey(99)),
            record.copy(diagnostics = listOf(
                org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDiagnostic(
                    code = "forged.diagnostic",
                    terminal = true,
                ),
            )),
        )
        val packet = inventory.recording.semanticOnlyDraws.single().packet
        val corePacket = plan(listOf(rect())).recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val packetMutations = listOf(
            packet.copyForTest(
                packetId = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID("packet.forged"),
            ),
            packet.copyForTest(commandIdValue = 99),
            packet.copyForTest(analysisRecordId = "forged.record"),
            packet.copyForTest(passId = "forged.pass"),
            packet.copyForTest(layerId = "forged.layer"),
            packet.copyForTest(bindingListId = "forged.bindings"),
            packet.copyForTest(insertionReasonCode = "forged.reason"),
            packet.copyForTest(sortKey = 99L),
            packet.copyForTest(sortKeyPreimage = "forged.sort"),
            packet.copyForTest(
                renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID("forged.step"),
            ),
            packet.copyForTest(renderStepVersion = 2),
            packet.copyForTest(role = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole.Copy),
            packet.copyForTest(blendPlan = null),
            packet.copyForTest(bindingLayoutHash = "forged.layout"),
            packet.copyForTest(uniformSlot = corePacket.uniformSlot),
            packet.copyForTest(
                resourceSlot = org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot(
                    slotId = org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID("forged"),
                    fingerprint = org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint(
                        "forged",
                    ),
                    bindingIndex = 0,
                ),
            ),
            packet.copyForTest(semanticPayload = corePacket.semanticPayload),
            packet.copyForTest(vertexSourceLabel = "forged.vertex-source"),
            packet.copyForTest(scissorBoundsHash = "forged.scissor"),
            packet.copyForTest(targetStateHash = "forged.target-state"),
            packet.copyForTest(originalPaintOrder = 99),
            packet.copyForTest(resourceGeneration = 1L),
            packet.copyForTest(
                frameProvenance = org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance.HarnessBackground,
            ),
            packet.copyForTest(clipCoveragePlan = null),
            packet.copyForTest(
                clipExecutionPlan = org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip,
            ),
            packet.copyForTest(
                diagnostics = listOf(
                    org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic(
                        code = "forged.diagnostic",
                        terminal = true,
                    ),
                ),
            ),
        )
        val executableKeyMutations = listOf(
            packet.copyForTest(
                renderPipelineKey = org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey(
                    "pipeline.forged",
                ),
            ),
            packet.copyForTest(
                computePipelineKey = org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey(
                    "compute.forged",
                ),
            ),
        )
        executableKeyMutations.forEach { changedPacket ->
            assertFailsWith<IllegalArgumentException> {
                inventory.recording.withSemanticPacket(changedPacket)
            }
        }
        val malformed = recordMutations.map { changed ->
            inventory.copy(
                recording = inventory.recording.copy(
                    analysis = inventory.recording.analysis.copy(records = listOf(changed)),
                ),
            )
        } + packetMutations.map { changedPacket ->
            inventory.copy(recording = inventory.recording.withSemanticPacket(changedPacket))
        } + listOf(
            inventory.copy(
                recording = inventory.recording.copy(
                    analysisDecisionDump = inventory.recording.analysisDecisionDump.copy(
                        lines = listOf("decision:candidate:forged:prepared.vertices.semantic"),
                    ),
                ),
            ),
            inventory.copy(target = inventory.target.copy(width = 33)),
            inventory.copy(target = inventory.target.copy(height = 25)),
            inventory.copy(target = inventory.target.copy(colorFormat = "bgra8unorm")),
        )

        malformed.forEachIndexed { index, changed ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = changed,
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
                "mutation#$index",
            )
            val expectedCode = if (index == recordMutations.size + 1) {
                "invalid.surface.prepared.semantic-command-bijection"
            } else {
                "invalid.surface.prepared.vertices-semantic-authority"
            }
            assertEquals(
                expectedCode,
                refused.diagnostic.code.value,
                "mutation#$index",
            )
        }
    }

    @Test
    fun `foreign core and clip producer authorities refuse before semantic publication`() {
        val inventory = plan(listOf(vertices(Color.RED)))
        val record = inventory.recording.analysis.records.single()
        val rectRecord = plan(listOf(rect())).recording.analysis.records.single()
        val rrectRecord = plan(listOf(rrect())).recording.analysis.records.single()
        val recordMutations = listOf(
            record.copy(
                corePrimitiveRectRouteAuthority = assertNotNull(
                    rectRecord.corePrimitiveRectRouteAuthority,
                ),
            ),
            record.copy(
                corePrimitiveRectGeometryAuthority = assertNotNull(
                    rectRecord.corePrimitiveRectGeometryAuthority,
                ),
            ),
            record.copy(
                corePrimitiveRRectGeometryAuthority = assertNotNull(
                    rrectRecord.corePrimitiveRRectGeometryAuthority,
                ),
            ),
        )
        recordMutations.forEach { changedRecord ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = inventory.copy(
                        recording = inventory.recording.copy(
                            analysis = inventory.recording.analysis.copy(
                                records = listOf(changedRecord),
                            ),
                        ),
                    ),
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.vertices-semantic-authority",
                refused.diagnostic.code.value,
            )
        }

        val packet = inventory.recording.semanticOnlyDraws.single().packet
        val authority = org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority.Mask(
            org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan(
                sourceOrder = 0,
                geometry = org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry.Rect(
                    org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(0f, 0f, 1f, 1f),
                ),
                combine = org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine.Intersect,
                antiAlias = true,
            ),
        )
        val authorityField = packet.javaClass.getDeclaredField("clipProducerAuthority")
        authorityField.isAccessible = true
        authorityField.set(packet, authority)

        val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                inventory = inventory,
                targetBounds = BOUNDS,
                imageArtifactsByCommandId = emptyMap(),
            ),
        )
        assertEquals(
            "invalid.surface.prepared.vertices-semantic-authority",
            refused.diagnostic.code.value,
        )
    }

    private fun NormalizedDrawCommand.DrawPreparedVertices.withAuthorities(
        topologyIdentity: String = this.topologyIdentity,
        layoutIdentity: String = this.layoutIdentity,
        materialIdentity: String = this.materialIdentity,
        transformBytes: List<Int> = this.transformBytes,
        transform: org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts = this.transform,
        capabilitySnapshotHash: String = this.capabilitySnapshotHash,
    ) = NormalizedDrawCommand.DrawPreparedVertices(
        commandId = commandId,
        artifactKey = artifactKey,
        topologyIdentity = topologyIdentity,
        layoutIdentity = layoutIdentity,
        materialIdentity = materialIdentity,
        transformBytes = transformBytes,
        transform = transform,
        clip = clip,
        layer = layer,
        blend = blend,
        preparedBlendPlan = preparedBlendPlan,
        bounds = bounds,
        ordering = ordering,
        source = source,
        clipIdentity = clipIdentity,
        clipCoverageIdentity = clipCoverageIdentity,
        primitiveColorPresent = primitiveColorPresent,
        primitiveBlendIdentity = primitiveBlendIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        drawProvenance = drawProvenance,
    )

    private fun PreparedVerticesFrameInventory.testRebindCommandIds(
        commandIdByOperationIndex: Map<Int, Int>,
    ): PreparedVerticesFrameInventory? = when (val rebound = bindCommandIds(
        commandIdByOperationIndex,
        commandIdByOperationIndex.values.associateWith {
            org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance.None
        },
    )) {
        is PreparedVerticesCommandBindingResult.Ready -> rebound.inventory
        is PreparedVerticesCommandBindingResult.Refused -> null
    }

    private fun PreparedVerticesFrameInventory.testWithMappedCommands(
        mappedCommands: List<PreparedVerticesMappedCommand>,
    ): PreparedVerticesFrameInventory = PreparedVerticesFrameInventory(
        commands = commands,
        artifactsByKey = artifactsByKey,
        materialsByKey = materialsByKey,
        artifactKeyByOperationIndex = artifactKeyByOperationIndex,
        vertexUploadRanges = vertexUploadRanges,
        indexUploadRanges = indexUploadRanges,
        elidedVerticesOperationIndices = elidedVerticesOperationOrder,
        mappedCommands = mappedCommands,
        capabilitySnapshotHash = capabilitySnapshotHash,
        metrics = metrics,
        limitEvidence = limitEvidence,
    )

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPURecording.withSemanticPacket(
        packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPURecording = copy(
        semanticOnlyDrawEntries = listOf(semanticOnlyDraws.single().copy(packet = packet)),
    )

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList.testWithTasks(
        tasks: List<GPUTask>,
    ) = org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket.copyForTest(
        packetId: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID = this.packetId,
        commandIdValue: Int = this.commandIdValue,
        analysisRecordId: String = this.analysisRecordId,
        passId: String = this.passId,
        layerId: String = this.layerId,
        bindingListId: String = this.bindingListId,
        insertionReasonCode: String = this.insertionReasonCode,
        sortKey: Long = this.sortKey,
        sortKeyPreimage: String = this.sortKeyPreimage,
        renderStepId: org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID = this.renderStepId,
        renderStepVersion: Int = this.renderStepVersion,
        role: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole = this.role,
        blendPlan: org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan? = this.blendPlan,
        renderPipelineKey: org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey? =
            this.renderPipelineKey,
        computePipelineKey: org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey? =
            this.computePipelineKey,
        bindingLayoutHash: String = this.bindingLayoutHash,
        uniformSlot: org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot? = this.uniformSlot,
        resourceSlot: org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot? = this.resourceSlot,
        semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
        vertexSourceLabel: String = this.vertexSourceLabel,
        scissorBoundsHash: String? = this.scissorBoundsHash,
        targetStateHash: String = this.targetStateHash,
        originalPaintOrder: Int = this.originalPaintOrder,
        resourceGeneration: Long = this.resourceGeneration,
        frameProvenance: org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance = this.frameProvenance,
        clipCoveragePlan: GPUClipCoveragePlan? = this.clipCoveragePlan,
        clipExecutionPlan: org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan? =
            this.clipExecutionPlan,
        diagnostics: List<org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic> = this.diagnostics,
    ) = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = renderPipelineKey,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = diagnostics,
        clipProducerAuthority = clipProducerAuthority,
    )

    private fun plan(operations: List<DisplayOp>) = GPUFramePathApiInventory.plan(
        operations = operations,
        target = GPUTargetFacts(32, 24, "rgba8unorm"),
        config = RenderConfig.DEFAULT,
        capabilities = CAPABILITIES,
    )

    private fun rect() = DisplayOp.DrawRect(
        Rect.fromLTRB(1f, 1f, 7f, 7f),
        Paint.fill(Color.GREEN).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun rrect() = DisplayOp.DrawRRect(
        RRect(Rect.fromLTRB(1f, 1f, 7f, 7f), radius = 1f),
        Paint.fill(Color.GREEN).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun vertices(
        color: Color,
        transform: Matrix33 = Matrix33.identity(),
    ) = DisplayOp.DrawVertices(
        Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(4f, 0f), Point(0f, 4f)),
        ),
        Paint.fill(color),
        transform,
        ClipStack.WideOpen,
    )

    private companion object {
        val BOUNDS = GPUPixelBounds(0, 0, 32, 24)
        val CAPABILITIES: GPUCapabilities = GPUProductFlagConfig().buildCapabilities().let { base ->
            GPUCapabilities(
                implementation = base.implementation,
                facts = base.facts + GPUCapabilityFact(
                    "first_slice.fill_rect.native", "test", "supported", true, "vertices-semantic",
                ),
                knownUnsupportedFacts = base.knownUnsupportedFacts,
                snapshotId = "fp06-task7-vertices-semantic",
                limits = base.limits,
            )
        }
    }
}
