package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.GPUTextureFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUCorePrimitiveDirectPreparedPassSeal
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilConsumerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilProducerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeStyle
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

class GPUCorePrimitivePreparedFrameTaskListBuilderTest {
    @Test
    fun `fill rect packet refuses a forged path semantic before resource planning`() {
        val base = recording(command(1, 0)).taskList.withClipPlans(
            mapOf(1 to GPUClipExecutionPlan.NoClip),
        )
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(
                    1 to semantic(
                        packet,
                        sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                    ),
                ),
            ),
        )

        assertEquals(
            "invalid.recording.core_primitive_semantic_authority",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `rrect packet analysis and render step mutations refuse before budget planning`() {
        val commandId = 105
        val base = recording(command(commandId, 0)).taskList
            .withClipPlans(mapOf(commandId to GPUClipExecutionPlan.NoClip))
            .withPacketRouteIdentity(
                commandId = commandId,
                analysisRecordId = "analysis.fill_rrect.$commandId",
                renderStepIdentity = CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY,
            )
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val semantic = semantic(
            packet,
            geometry = GPUCorePrimitiveGeometryInput.RRect(1f, 1f, 8f, 8f, List(8) { 1f }),
        )
        val mutations = listOf(
            base.withPacketRouteIdentity(
                commandId,
                analysisRecordId = "analysis.fill_rect.$commandId",
                renderStepIdentity = CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY,
            ),
            base.withPacketRouteIdentity(
                commandId,
                analysisRecordId = "analysis.fill_rrect.$commandId",
                renderStepIdentity = CORE_PRIMITIVE_FILL_RECT_STEP_IDENTITY,
            ),
        )

        mutations.forEach { mutated ->
            val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(mutated, mapOf(commandId to semantic)).copy(
                    configuredAggregateBudgetBytes = 1L,
                ),
            )

            assertEquals(
                "invalid.recording.core_primitive_semantic_authority",
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `promotable color only multisample base render retains exact continuation and budget authority`() {
        val base = recording(command(2, 0)).taskList.withClipPlans(
            mapOf(2 to GPUClipExecutionPlan.NoClip),
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(2 to semantic(packet))).copy(capabilities = msaaCapabilities()),
        )
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(result, result.toString()).taskList

        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(GPUSamplePlan.MultisampleFrame(4), prepared.samplePlan)
        assertEquals(base.tasks.filterIsInstance<GPUTask.Render>().single().sampleContinuationKey,
            prepared.sampleContinuationKey)
        assertEquals(
            targetBounds.width.toLong() * targetBounds.height * 4L * 4L,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaColor),
        )
        assertEquals(null, prepared.depthStencilLoadStore)
        assertTrue(prepared.resourceUses.none {
            it.role == GPUFrameResourceRole.PathDepthStencil ||
                it.role == GPUFrameResourceRole.ClipDepthStencil
        })
    }

    @Test
    fun `path stencil AA 4x builds one exact paired attachment render and budget`() {
        val pathVariants = listOf(
            Triple(102, GPUCorePrimitiveFillRule.Winding, false),
            Triple(103, GPUCorePrimitiveFillRule.Winding, true),
            Triple(104, GPUCorePrimitiveFillRule.EvenOdd, false),
            Triple(105, GPUCorePrimitiveFillRule.EvenOdd, true),
        )
        val base = recording(
            *pathVariants.mapIndexed { order, variant -> command(variant.first, order) }.toTypedArray(),
        ).taskList.withClipPlans(
            pathVariants.associate { it.first to GPUClipExecutionPlan.NoClip },
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4)).mergeRenderTasks()
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.associate { packet ->
            val variant = pathVariants.single { it.first == packet.commandIdValue }
            packet.commandIdValue to semantic(
                packet,
                stencilGeometry(
                    coverBounds = GPUPixelBounds(1, 1, 9, 10),
                    inverseFill = variant.third,
                    fillRule = variant.second,
                ),
                GPUCorePrimitiveCoverageMode.StencilAA,
            )
        }

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, semantics).copy(capabilities = msaaCapabilities()),
        )
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(result, result.toString()).taskList
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        val pathUse = render.resourceUses.single { it.role == GPUFrameResourceRole.PathDepthStencil }
        val continuation = requireNotNull(render.sampleContinuationKey)

        assertEquals(GPUSamplePlan.MultisampleFrame(4), render.samplePlan)
        assertEquals(
            listOf(
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
            ),
            render.drawPackets.map(GPUDrawPacket::role),
        )
        assertTrue(render.drawPackets.all {
            requireNotNull(it.corePrimitivePreparedAuthority).structuralPipelineKey.sampleCount == 4 &&
                it.targetStateHash == corePrimitiveTargetStateHash(4)
        })
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.passes.GPUSampleAttachmentAuthority.PreparedFramePayload,
            continuation.attachmentAuthority,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity(pathUse.resource.value),
            continuation.depthStencilAttachment,
        )
        assertEquals(GPULoadStorePlan("clear", GPUStorePlan.Store), render.loadStore)
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Discard,
                0u,
            ),
            render.depthStencilLoadStore,
        )
        val preparation = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { it.role == GPUFrameResourceRole.PathDepthStencil }
        val descriptor = assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor>(
            preparation.descriptor,
        )
        val attachmentBytes = targetBounds.width.toLong() * targetBounds.height * 4L * 4L
        assertEquals(targetBounds, descriptor.logicalBounds)
        assertEquals(4, descriptor.sampleCount)
        assertEquals(attachmentBytes, preparation.byteSize)
        assertEquals(
            attachmentBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaColor),
        )
        assertEquals(
            attachmentBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil),
        )

        val exactBudget = Math.addExact(
            taskList.memoryBudget.targetResidentBytes,
            taskList.memoryBudget.peakFrameTransientBytes,
        )
        assertEquals(
            "unsupported.frame_memory.aggregate_budget_exceeded",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, semantics).copy(
                        capabilities = msaaCapabilities(),
                        configuredAggregateBudgetBytes = exactBudget - 1L,
                    ),
                ),
            ).diagnostic.code.value,
        )
    }

    @Test
    fun `path stencil AA 4x refuses a mixed direct frame atomically`() {
        val base = recording(command(104, 0), command(105, 1)).taskList.withClipPlans(
            mapOf(104 to GPUClipExecutionPlan.NoClip, 105 to GPUClipExecutionPlan.NoClip),
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4)).mergeRenderTasks()
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            104 to semantic(packets.single { it.commandIdValue == 104 }),
            105 to semantic(
                packets.single { it.commandIdValue == 105 },
                stencilGeometry(GPUPixelBounds(1, 1, 9, 10)),
                GPUCorePrimitiveCoverageMode.StencilAA,
            ),
        )

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, semantics).copy(
                capabilities = msaaCapabilities(),
                configuredAggregateBudgetBytes = 1L,
            ),
        )

        assertEquals(
            "unsupported.recording.core_primitive_path_stencil_msaa_mixed_direct",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `path stencil AA 4x refuses retained color load and pass break atomically`() {
        val retainedBase = recording(command(106, 0)).taskList.withClipPlans(
            mapOf(106 to GPUClipExecutionPlan.NoClip),
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4))
            .withRenderLoadStore(GPULoadStorePlan("load", GPUStorePlan.Store))
        val passBreakBase = recording(command(107, 0), command(108, 1)).taskList.withClipPlans(
            mapOf(107 to GPUClipExecutionPlan.NoClip, 108 to GPUClipExecutionPlan.NoClip),
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4))
        val mutations = listOf(
            retainedBase to "unsupported.recording.core_primitive_path_stencil_msaa_retained_load",
            passBreakBase to "unsupported.recording.core_primitive_path_stencil_msaa_pass_break",
        )

        mutations.forEach { (mutated, expectedCode) ->
            val semantics = mutated.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets)
                .associate { packet ->
                    packet.commandIdValue to semantic(
                        packet,
                        stencilGeometry(GPUPixelBounds(1, 1, 9, 10)),
                        GPUCorePrimitiveCoverageMode.StencilAA,
                    )
                }
            val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(mutated, semantics).copy(
                    capabilities = msaaCapabilities(),
                    configuredAggregateBudgetBytes = 1L,
                ),
            )

            assertEquals(
                expectedCode,
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `multisample base render without exact resolve capability refuses before route promotion`() {
        val base = recording(command(6, 0)).taskList.withClipPlans(
            mapOf(6 to GPUClipExecutionPlan.NoClip),
        ).withSamplePlan(GPUSamplePlan.MultisampleFrame(4))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(6 to semantic(packet))),
        )

        assertEquals(
            "unsupported.core_primitive.coverage_sample.color_capability",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `compatible direct packets become one clear store render task`() {
        val base = recording(command(3, 0), command(4, 1)).taskList.withClipPlans(
            mapOf(3 to GPUClipExecutionPlan.NoClip, 4 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()

        assertEquals(1, renders.size)
        assertEquals(listOf(3, 4), renders.single().drawPackets.map(GPUDrawPacket::commandIdValue))
        assertEquals(
            GPULoadStorePlan("clear", GPUStorePlan.Store),
            renders.single().loadStore,
        )
    }

    @Test
    fun `clip resources have explicit mask and depth stencil roles`() {
        assertEquals("ClipMask", GPUFrameResourceRole.ClipMask.name)
        assertEquals("ClipDepthStencil", GPUFrameResourceRole.ClipDepthStencil.name)
    }

    @Test
    fun `refused base task remains refused with its original diagnostic`() {
        val base = recording(
            command(
                commandId = 8,
                paintOrder = 0,
                clip = GPUClipFacts.complexStack(GPUBounds(0f, 0f, 16f, 16f)),
            ),
        ).taskList
        val original = assertIs<GPUTask.Refused>(base.tasks.single()).diagnostic

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, emptyMap()).copy(
                targetBounds = GPUPixelBounds(1, 1, 1, 1),
                configuredAggregateBudgetBytes = 0,
            ),
        )

        val refused = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result)
        assertSame(original, refused.diagnostic)
        assertEquals("unsupported.clip.complex_stack", refused.diagnostic.code.value)
    }

    @Test
    fun `prepared packets preserve exact base ordering blend provenance and clip authorities`() {
        val base = recording(
            command(9, 7, GPUFrameProvenance.HarnessBackground, GPUClipCoveragePlan.NoClip),
            command(4, 2, GPUFrameProvenance.GmContent, scissor()),
        ).taskList.withClipPlans(
            mapOf(
                9 to GPUClipExecutionPlan.NoClip,
                4 to GPUClipExecutionPlan.ScissorOnly(targetBounds),
            ),
        )
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = basePackets.associate { packet -> packet.commandIdValue to semantic(packet) }

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        assertEquals(basePackets.map { it.commandIdValue }, prepared.map { it.commandIdValue })
        assertEquals(basePackets.map { it.sortKey }, prepared.map { it.sortKey })
        assertEquals(basePackets.map { it.originalPaintOrder }, prepared.map { it.originalPaintOrder })
        assertEquals(basePackets.map { it.blendPlan }, prepared.map { it.blendPlan })
        assertEquals(basePackets.map { it.frameProvenance }, prepared.map { it.frameProvenance })
        assertEquals(basePackets.map { it.clipCoveragePlan }, prepared.map { it.clipCoveragePlan })
    }

    @Test
    fun `direct packets share exact frame local geometry and aligned uniform slabs`() {
        val base = recording(command(90, 0), command(91, 1)).taskList.withClipPlans(
            mapOf(
                90 to GPUClipExecutionPlan.NoClip,
                91 to GPUClipExecutionPlan.ScissorOnly(targetBounds),
            ),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            90 to semantic(packets.single { it.commandIdValue == 90 }),
            91 to semantic(packets.single { it.commandIdValue == 91 }),
        )

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList

        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        val vertex = preparations.single { it.role == GPUFrameResourceRole.VertexData }
        val index = preparations.single { it.role == GPUFrameResourceRole.IndexData }
        val uniform = preparations.single { it.role == GPUFrameResourceRole.UniformData }
        assertEquals(
            GPUFrameBufferDescriptor(byteSize = 64L, alignmentBytes = 4L),
            vertex.descriptor,
        )
        assertEquals(
            GPUFrameBufferDescriptor(byteSize = 48L, alignmentBytes = 4L),
            index.descriptor,
        )
        assertEquals(GPUFrameBufferDescriptor(512L, 256L), uniform.descriptor)
        assertEquals(
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
            uniform.usages,
        )
        assertEquals(GPUFrameResourceLifetime.FrameLocal, uniform.lifetime)
        assertEquals(512L, uniform.byteSize)
        assertEquals(setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Vertex), vertex.usages)
        assertEquals(setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Index), index.usages)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, vertex.lifetime)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, index.lifetime)
        assertEquals(64L, vertex.byteSize)
        assertEquals(48L, index.byteSize)

        val shading = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { render -> render.drawPackets.all { it.role == GPUDrawPacketRole.Shading } }
        assertEquals(1, shading.size)
        shading.forEach { render ->
            assertEquals(
                setOf(
                    org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                        vertex.resource,
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceUsage.Vertex,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                    org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                        index.resource,
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceUsage.Index,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                ),
                render.resourceUses.filter {
                    it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData
                }.toSet(),
            )
            assertEquals(
                1,
                render.resourceUses.count { it.role == GPUFrameResourceRole.UniformData },
            )
        }
        assertEquals(
            624L,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch),
        )
        val preparedPackets = shading.single().drawPackets
        val authorities = preparedPackets.map { packet -> requireNotNull(packet.corePrimitivePreparedAuthority) }
        assertEquals(1, authorities.map { it.structuralPipelineKey }.distinct().size)
        val uniformSeal = requireNotNull(authorities.first().uniformSlabSeal)
        assertTrue(authorities.all { it.uniformSlabSeal === uniformSeal })
        val mutableSnapshot = uniformSeal.packedBytesSnapshot()
        mutableSnapshot[0] = (mutableSnapshot[0] + 1).toByte()
        assertTrue(
            uniformSeal.hasExactPayloads(
                expectedCommandIds = preparedPackets.map(GPUDrawPacket::commandIdValue),
                uniformBytesByDraw = preparedPackets.map { packet ->
                    requireNotNull(
                        (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive)
                            .payloadRef.uniformBlock,
                    ).bytes
                },
            ),
            "mutating evidence snapshots must not rewrite the builder-owned packed slab",
        )
    }

    @Test
    fun `analytic rect and rrect share one sealed uniform80 slab while values stay dynamic`() {
        val base = recording(command(92, 0), command(93, 1)).taskList.withClipPlans(
            mapOf(92 to GPUClipExecutionPlan.NoClip, 93 to GPUClipExecutionPlan.NoClip),
        ).withPacketRouteIdentity(
            commandId = 93,
            analysisRecordId = "analysis.fill_rrect.93",
            renderStepIdentity = CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY,
        )
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            92 to semantic(
                basePackets.single { it.commandIdValue == 92 },
                GPUCorePrimitiveGeometryInput.Rect(1f, 2f, 8f, 9f),
                GPUCorePrimitiveCoverageMode.ScalarAA,
            ),
            93 to semantic(
                basePackets.single { it.commandIdValue == 93 },
                GPUCorePrimitiveGeometryInput.RRect(
                    4f,
                    3f,
                    14f,
                    13f,
                    listOf(1f, 2f, 2f, 2f, 3f, 2f, 1f, 1f),
                ),
                GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
        )

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .single().drawPackets
        val authorities = prepared.map { requireNotNull(it.corePrimitivePreparedAuthority) }
        val seals = authorities.map { requireNotNull(it.analyticShapeUniformSeal) }
        val plan = seals.first().plan

        assertTrue(authorities.all { it.uniformSlabSeal == null })
        assertTrue(seals.all { it.plan === plan })
        assertEquals("core-primitive-analytic-shape-uniform-pass", plan.sourceLabel)
        assertEquals(
            listOf("analytic-shape-draw-92", "analytic-shape-draw-93"),
            plan.slots.map { it.slotLabel },
        )
        assertTrue(plan.slots.all { it.payloadBytes == 80L })
        assertEquals(1, authorities.map { it.structuralPipelineKey }.distinct().size)
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
            authorities.first().structuralPipelineKey.uniformLayout,
        )
        assertEquals(1, authorities.map { it.renderPipelineKey }.distinct().size)
        assertEquals(GPUPixelBounds(0, 1, 9, 10), seals[0].renderScissor)
        assertEquals(GPUPixelBounds(4, 3, 14, 13), seals[1].renderScissor)
        prepared.zip(seals).forEach { (packet, seal) ->
            val exact = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
            assertTrue(seal.hasExactSemantic(exact))
            assertFalse(seal.hasExactSemantic(exact.sameContentClone()))
        }
        assertEquals(1, seals[0].payloadBytesSnapshot().uint32(8))
        assertEquals(0, seals[1].payloadBytesSnapshot().uint32(8))
        assertFalse(seals[0].payloadBytesSnapshot().contentEquals(seals[1].payloadBytesSnapshot()))
        val packed = ByteArray(plan.totalBytes.toInt())
        seals.forEach { seal ->
            seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
        }
        val passSeal = GPUCorePrimitiveDirectPreparedPassSeal.analyticShape(
            structuralPipelineKey = authorities.first().structuralPipelineKey,
            analyticShapeUniformSeals = seals,
        )
        assertEquals(seals, passSeal.analyticShapeUniformSeals)
        assertContentEquals(packed, passSeal.packedUniformBytesForUpload())
        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitiveDirectPreparedPassSeal.analyticShape(
                structuralPipelineKey = authorities.first().structuralPipelineKey,
                analyticShapeUniformSeals = emptyList(),
            )
        }
    }

    @Test
    fun `stencil coverage sample contradictions refuse before the legacy sample guard`() {
        val stencilGeometry = stencilGeometry(GPUPixelBounds(1, 1, 8, 8))
        val cases = listOf(
            Triple(
                GPUCorePrimitiveCoverageMode.StencilAA,
                GPUSamplePlan.SingleSampleFrame,
                "invalid.core_primitive.coverage_sample.stencil_aa_requires_multisample",
            ),
            Triple(
                GPUCorePrimitiveCoverageMode.Stencil1x,
                GPUSamplePlan.MultisampleFrame(4),
                "invalid.core_primitive.coverage_sample.stencil_1x_requires_single_sample",
            ),
        )

        cases.forEachIndexed { index, (coverageMode, samplePlan, expectedCode) ->
            val commandId = 102 + index
            val base = recording(command(commandId, index)).taskList.withClipPlans(
                mapOf(commandId to GPUClipExecutionPlan.NoClip),
            ).withSamplePlan(samplePlan)
            val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
            val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(
                    base,
                    mapOf(commandId to semantic(packet, stencilGeometry, coverageMode)),
                ).copy(capabilities = msaaCapabilities()),
            )

            assertEquals(
                expectedCode,
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `legacy mask without classified execution plan remains refused`() {
        val mask = GPUClipCoveragePlan.Mask(
            contentKey = "clip.mask.pending-b2",
            width = 16,
            height = 16,
            sampleCount = 1,
            resolvedBytes = 256,
            requiredBytes = 256,
            elements = listOf(
                GPUClipCoverageElement(
                    operation = GPUClipCoverageOperation.Intersect,
                    kind = GPUClipCoverageElementKind.Rect,
                    values = listOf(1f, 1f, 12f, 12f),
                    vertexCount = 0,
                    antiAlias = true,
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
            ),
        )
        val base = recording(command(12, 0, GPUFrameProvenance.GmContent, mask)).taskList
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(packet.commandIdValue to semantic(packet))),
        )

        assertEquals(
            "invalid.recording.core_primitive_clip_execution_plan_missing",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `scissor and analytic execution plans add no clip producer or resource`() {
        val plans = listOf(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 0, 12, 12)),
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                GPUPixelBounds(0, 0, 16, 16),
                true,
            ),
        )

        plans.forEachIndexed { index, plan ->
            val base = recording(command(20 + index, index)).taskList.withClipPlans(
                mapOf(20 + index to plan),
            )
            val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
            val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, packets.associate { it.commandIdValue to semantic(it) }),
                ),
            ).taskList

            assertFalse(taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets).any {
                it.role == GPUDrawPacketRole.ClipProducer || it.role == GPUDrawPacketRole.StencilProducer
            })
            assertFalse(taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }.any {
                it.role == GPUFrameResourceRole.ClipMask || it.role == GPUFrameResourceRole.ClipDepthStencil
            })
        }
    }

    @Test
    fun `simple analytic rect and rrect clips seal one isolated uniform64 payload per direct draw`() {
        val cases = listOf(
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1.25f, 2.25f, 8.75f, 9.75f)),
                scissor = null,
                antiAlias = true,
            ).let { Triple(it, listOf(0f, 0f, 0f, 0f), GPUPixelBounds(0, 1, 10, 11)) },
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.RRect(
                    GPUClipBounds(1.25f, 2.25f, 8.75f, 9.75f),
                    listOf(2f, 3f, 2f, 3f, 2f, 3f, 2f, 3f),
                ),
                scissor = GPUPixelBounds(1, 2, 12, 13),
                antiAlias = false,
            ).let { Triple(it, listOf(2f, 3f, 0f, 0f), GPUPixelBounds(1, 2, 9, 10)) },
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.RRect(
                    GPUClipBounds(1.25f, 2.25f, 8.75f, 9.75f),
                    List(8) { 0f },
                ),
                scissor = null,
                antiAlias = true,
            ).let { Triple(it, listOf(0f, 0f, 0f, 0f), GPUPixelBounds(0, 1, 10, 11)) },
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.RRect(
                    GPUClipBounds(1.25f, 2.25f, 8.75f, 9.75f),
                    listOf(0f, 3f, 0f, 3f, 0f, 3f, 0f, 3f),
                ),
                scissor = null,
                antiAlias = true,
            ).let { Triple(it, listOf(0f, 0f, 0f, 0f), GPUPixelBounds(0, 1, 10, 11)) },
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.RRect(
                    GPUClipBounds(1.25f, 2.25f, 8.75f, 9.75f),
                    listOf(2f, 0f, 2f, 0f, 2f, 0f, 2f, 0f),
                ),
                scissor = null,
                antiAlias = false,
            ).let { Triple(it, listOf(0f, 0f, 0f, 0f), GPUPixelBounds(1, 2, 9, 10)) },
        )

        cases.forEachIndexed { index, (plan, expectedPackedRadii, expectedScissor) ->
            val commandId = 220 + index
            val base = recording(command(commandId, index)).taskList.withClipPlans(mapOf(commandId to plan))
            val basePacket = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

            val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, mapOf(commandId to semantic(basePacket))),
                ),
            ).taskList
            val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets)
                .single { it.commandIdValue == commandId }
            val authority = requireNotNull(prepared.corePrimitivePreparedAuthority)
            val seal = requireNotNull(authority.analyticClipUniformSeal)
            val bytes = seal.payloadBytesSnapshot()

            assertEquals(CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH, prepared.bindingLayoutHash)
            assertEquals(null, authority.uniformSlabSeal)
            assertEquals(commandId, seal.commandId)
            assertEquals(prepared.packetId, seal.packetId)
            assertEquals(plan.canonicalIdentity(), seal.clipCanonicalIdentity)
            assertEquals(plan.antiAlias, seal.antiAlias)
            assertEquals(expectedScissor, seal.conservativeScissor)
            assertEquals(64, bytes.size)
            assertEquals(64L, seal.payloadBytes)
            assertEquals(0L, seal.alignedOffset % seal.alignmentBytes)
            assertEquals(taskList.capabilitySeal.deviceGeneration.value, seal.deviceGeneration)
            assertEquals(authority.structuralPipelineKey, seal.structuralPipelineKey)
            assertEquals(authority.renderPipelineKey, seal.renderPipelineKey)
            assertEquals(16f, bytes.float32(0))
            assertEquals(16f, bytes.float32(4))
            val expectedClipType = if (expectedPackedRadii.all { it == 0f }) 0 else 1
            assertEquals(expectedClipType, bytes.uint32(8))
            assertEquals(
                if (expectedClipType == 0) {
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                } else {
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
                },
                seal.clipType,
            )
            assertEquals(
                GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
                    geometry = seal.clipType,
                    antiAlias = plan.antiAlias,
                ),
                authority.structuralPipelineKey.clip,
            )
            assertEquals(if (plan.antiAlias) 1 else 0, bytes.uint32(12))
            assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), (16 until 32 step 4).map { bytes.float32(it) })
            assertEquals(listOf(1.25f, 2.25f, 8.75f, 9.75f), (32 until 48 step 4).map { bytes.float32(it) })
            assertEquals(expectedPackedRadii, (48 until 64 step 4).map { bytes.float32(it) })

            val corrupted = seal.payloadBytesSnapshot()
            corrupted[0] = 0
            assertEquals(16f, seal.payloadBytesSnapshot().float32(0))
            assertTrue(taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
                .flatMap(GPUTask.PrepareResources::requests)
                .any { it.diagnosticLabel == "core-primitive.analytic-clip-uniforms" })
            assertFalse(taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
                .flatMap(GPUTask.PrepareResources::requests)
                .any { it.role == GPUFrameResourceRole.ClipMask })
        }
    }

    @Test
    fun `complex analytic rrect is refused before uniform or mask budget planning`() {
        val commandId = 222
        val plan = GPUClipExecutionPlan.AnalyticCoverage(
            GPUClipExecutionGeometry.RRect(
                GPUClipBounds(1f, 1f, 12f, 12f),
                listOf(2f, 3f, 2f, 3f, 4f, 3f, 2f, 3f),
            ),
            scissor = null,
            antiAlias = true,
        )
        val base = recording(command(commandId, 0)).taskList.withClipPlans(mapOf(commandId to plan))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(commandId to semantic(packet))).copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_analytic_clip_complex_rrect",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `analytic shape and analytic clip uniform layouts refuse atomically`() {
        val commandId = 223
        val plan = GPUClipExecutionPlan.AnalyticCoverage(
            GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
            scissor = null,
            antiAlias = true,
        )
        val base = recording(command(commandId, 0)).taskList.withClipPlans(mapOf(commandId to plan))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(commandId to semantic(packet, coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA)),
            ),
        )

        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `analytic direct clip refuses a frame mixed with path stencil`() {
        val analyticPlan = GPUClipExecutionPlan.AnalyticCoverage(
            GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
            scissor = null,
            antiAlias = true,
        )
        val base = recording(command(224, 0), command(225, 1)).taskList.withClipPlans(
            mapOf(224 to analyticPlan, 225 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(
                    224 to semantic(packets.single { it.commandIdValue == 224 }),
                    225 to semantic(
                        packets.single { it.commandIdValue == 225 },
                        stencilGeometry(GPUPixelBounds(1, 1, 8, 8)),
                        GPUCorePrimitiveCoverageMode.Stencil1x,
                    ),
                ),
            ),
        )

        assertEquals(
            "unsupported.recording.core_primitive_analytic_clip_path_mix",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `analytic clip values stay out of the pipeline key and batch in one uniform64 slab`() {
        val plans = mapOf(
            226 to GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1.25f, 1.5f, 9.25f, 10.5f)),
                null,
                true,
            ),
            227 to GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(3.25f, 2.5f, 12.25f, 14.5f)),
                null,
                true,
            ),
        )
        val base = recording(command(226, 0), command(227, 1)).taskList.withClipPlans(plans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets
        val seals = prepared.map { requireNotNull(it.corePrimitivePreparedAuthority?.analyticClipUniformSeal) }

        assertEquals(2, prepared.size)
        assertEquals(1, prepared.map(GPUDrawPacket::renderPipelineKey).distinct().size)
        assertSame(seals[0].plan, seals[1].plan)
        assertEquals(listOf(0L, 256L), seals.map { it.alignedOffset })
        assertNotEquals(seals[0].clipBounds, seals[1].clipBounds)
    }

    @Test
    fun `analytic intersections depth two and four seal exact immutable uniform160 payloads`() {
        val plans = mapOf(
            228 to analyticIntersection(
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(0.25f, 0.75f, 14.25f, 14.5f)),
                    antiAlias = true,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.RRect(
                        GPUClipBounds(2.25f, 1.25f, 10.75f, 12.25f),
                        listOf(2f, 3f, 2f, 3f, 2f, 3f, 2f, 3f),
                    ),
                    antiAlias = false,
                ),
            ),
            229 to analyticIntersection(
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.RRect(
                        GPUClipBounds(1f, 1f, 15f, 15f),
                        listOf(0f, 2f, 0f, 2f, 0f, 2f, 0f, 2f),
                    ),
                    antiAlias = true,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(2f, 2f, 14f, 14f)),
                    antiAlias = false,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(3f, 3f, 13f, 13f)),
                    antiAlias = true,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.RRect(
                        GPUClipBounds(4f, 4f, 12f, 12f),
                        List(4) { listOf(1f, 1.5f) }.flatten(),
                    ),
                    antiAlias = false,
                ),
            ),
        )
        val base = recording(command(228, 0), command(229, 1)).taskList.withClipPlans(plans)
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, basePackets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets
        val seals = prepared.map {
            requireNotNull(it.corePrimitivePreparedAuthority?.analyticIntersectionUniformSeal)
        }

        assertEquals(1, prepared.map(GPUDrawPacket::renderPipelineKey).distinct().size)
        assertSame(seals[0].plan, seals[1].plan)
        assertEquals(listOf(0L, 256L), seals.map { it.alignedOffset })
        assertEquals(listOf(2, 4), seals.map { it.elements.size })
        assertEquals(GPUPixelBounds(2, 1, 11, 13), seals[0].conservativeScissor)
        assertEquals(GPUPixelBounds(4, 4, 12, 12), seals[1].conservativeScissor)
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
            seals[0].structuralPipelineKey.uniformLayout,
        )
        assertTrue(prepared.all { it.bindingLayoutHash == CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH })
        assertTrue(prepared.all { it.corePrimitivePreparedAuthority?.uniformSlabSeal == null })
        assertTrue(prepared.all { it.corePrimitivePreparedAuthority?.analyticClipUniformSeal == null })

        val depthTwo = seals[0].payloadBytesSnapshot()
        assertEquals(160, depthTwo.size)
        assertEquals(16f, depthTwo.float32(0))
        assertEquals(16f, depthTwo.float32(4))
        assertEquals(2, depthTwo.uint32(8))
        assertEquals(0, depthTwo.uint32(12))
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), (16 until 32 step 4).map { depthTwo.float32(it) })
        assertEquals(listOf(0.25f, 0.75f, 14.25f, 14.5f), (32 until 48 step 4).map { depthTwo.float32(it) })
        assertEquals(listOf(0f, 0f), (48 until 56 step 4).map { depthTwo.float32(it) })
        assertEquals(0, depthTwo.uint32(56))
        assertEquals(1, depthTwo.uint32(60))
        assertEquals(listOf(2.25f, 1.25f, 10.75f, 12.25f), (64 until 80 step 4).map { depthTwo.float32(it) })
        assertEquals(listOf(2f, 3f), (80 until 88 step 4).map { depthTwo.float32(it) })
        assertEquals(1, depthTwo.uint32(88))
        assertEquals(0, depthTwo.uint32(92))
        assertTrue(depthTwo.sliceArray(96 until 160).all { it == 0.toByte() })
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (seals[0].elements as MutableList<Any>).clear()
        }
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            seals[1].elements.first().clipType,
            "an rrect with one zero radius axis canonicalizes to rect",
        )
    }

    @Test
    fun `uniform160 refuses a mixed layout frame before slab budget planning`() {
        val plans = mapOf(
            230 to analyticIntersection(
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                    antiAlias = true,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(2f, 2f, 11f, 11f)),
                    antiAlias = false,
                ),
            ),
            231 to GPUClipExecutionPlan.NoClip,
        )
        val base = recording(command(230, 0), command(231, 1)).taskList.withClipPlans(plans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) })
                .copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `uniform32 and uniform64 refuse their mixed frame before slab budget planning`() {
        val plans = mapOf(
            232 to GPUClipExecutionPlan.NoClip,
            233 to GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                scissor = null,
                antiAlias = true,
            ),
        )
        val base = recording(command(232, 0), command(233, 1)).taskList.withClipPlans(plans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) })
                .copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `uniform64 and uniform160 refuse their mixed frame before slab budget planning`() {
        val plans = mapOf(
            234 to GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                scissor = null,
                antiAlias = true,
            ),
            235 to analyticIntersection(
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(2f, 2f, 11f, 11f)),
                    antiAlias = false,
                ),
                GPUClipAnalyticElement(
                    GPUClipExecutionGeometry.Rect(GPUClipBounds(3f, 3f, 10f, 10f)),
                    antiAlias = true,
                ),
            ),
        )
        val base = recording(command(234, 0), command(235, 1)).taskList.withClipPlans(plans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) })
                .copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `uniform160 and path refuse before slab budget planning`() {
        val intersection = analyticIntersection(
            GPUClipAnalyticElement(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                antiAlias = true,
            ),
            GPUClipAnalyticElement(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(2f, 2f, 11f, 11f)),
                antiAlias = false,
            ),
        )
        val base = recording(command(236, 0), command(237, 1)).taskList.withClipPlans(
            mapOf(236 to intersection, 237 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(
                    236 to semantic(packets.single { it.commandIdValue == 236 }),
                    237 to semantic(
                        packets.single { it.commandIdValue == 237 },
                        stencilGeometry(GPUPixelBounds(1, 1, 8, 8)),
                        GPUCorePrimitiveCoverageMode.Stencil1x,
                    ),
                ),
            ).copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_analytic_clip_path_mix",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `stencil execution records one producer resource token and consumer accepted by frame planner`() {
        val plan = stencilPlan()
        val base = recording(command(30, 0)).taskList.withClipPlans(mapOf(30 to plan))
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.ClipDepthStencil })
        assertFalse(preparations.any {
            it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData
        })
        assertEquals(plan.requiredBytes, preparations.single { it.role == GPUFrameResourceRole.ClipDepthStencil }.byteSize)
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        val producer = renders.single { it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer }
        val consumer = renders.single { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
        assertEquals(GPULoadStorePlan("load", GPUStorePlan.Store), producer.loadStore)
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Store,
                0u,
            ),
            producer.depthStencilLoadStore,
        )
        assertEquals(GPUDepthStencilLoadStorePlan.ReadOnlyKeep, consumer.depthStencilLoadStore)
        assertTrue(taskList.dependencies.any {
            it.fromTaskId == producer.taskId && it.toTaskId == consumer.taskId &&
                it.useToken?.value == plan.orderingToken.value
        })
        assertEquals(
            plan.requiredBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil),
        )
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `native path clip producer and two consumers share exact geometry slabs and c1 structural keys`() {
        listOf(GPUClipFillRule.Winding, GPUClipFillRule.EvenOdd).forEach { fillRule ->
            val plan = nativePathStencilPlan(fillRule)
            val base = recording(command(330, 7), command(331, 9)).taskList.withClipPlans(
                mapOf(330 to plan, 331 to plan),
            )
            val packets = base.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets)
            val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            )
            val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                result,
                (result as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                    "${fillRule.name}: ${it.code.value}: ${it.message}"
                },
            ).taskList

            val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
                .flatMap(GPUTask.PrepareResources::requests)
            val vertex = preparations.single { it.role == GPUFrameResourceRole.VertexData }
            val index = preparations.single { it.role == GPUFrameResourceRole.IndexData }
            assertEquals(160L, vertex.byteSize, fillRule.name)
            assertEquals(96L, index.byteSize, fillRule.name)
            assertEquals(
                setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Vertex),
                vertex.usages,
                fillRule.name,
            )
            assertEquals(
                setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Index),
                index.usages,
                fillRule.name,
            )

            val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
            val producer = renders.single { render ->
                render.drawPackets.singleOrNull()?.role == GPUDrawPacketRole.StencilProducer
            }
            val consumers = renders.filter { render ->
                render.drawPackets.any { it.role == GPUDrawPacketRole.Shading }
            }
            assertEquals(2, consumers.size, fillRule.name)
            assertEquals(
                setOf(
                    GPUFrameResourceRole.VertexData,
                    GPUFrameResourceRole.IndexData,
                    GPUFrameResourceRole.ClipDepthStencil,
                ),
                producer.resourceUses.map { it.role }.toSet(),
                fillRule.name,
            )
            assertFalse(
                producer.resourceUses.any { it.role == GPUFrameResourceRole.UniformData },
                fillRule.name,
            )
            assertEquals(
                corePrimitiveClipStencilProducerRenderPipelineStructuralKey(fillRule)
                    .stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
                producer.drawPackets.single().renderPipelineKey,
                fillRule.name,
            )
            consumers.forEach { consumer ->
                assertEquals(
                    setOf(
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceRole.ClipDepthStencil,
                    ),
                    consumer.resourceUses.map { it.role }.toSet(),
                    fillRule.name,
                )
                val packet = consumer.drawPackets.single()
                assertEquals(
                    corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
                        inverseFill = false,
                        blendPlan = requireNotNull(packet.blendPlan),
                    ).stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
                    packet.renderPipelineKey,
                    fillRule.name,
                )
            }
            val attachment = producer.resourceUses.single {
                it.role == GPUFrameResourceRole.ClipDepthStencil
            }.resource
            val producerVertex = producer.resourceUses.single {
                it.role == GPUFrameResourceRole.VertexData
            }.resource
            val producerIndex = producer.resourceUses.single {
                it.role == GPUFrameResourceRole.IndexData
            }.resource
            assertTrue(consumers.all { consumer ->
                val uses = consumer.resourceUses
                uses.single {
                    it.role == GPUFrameResourceRole.ClipDepthStencil
                }.resource == attachment &&
                    uses.single { it.role == GPUFrameResourceRole.VertexData }.resource == producerVertex &&
                    uses.single { it.role == GPUFrameResourceRole.IndexData }.resource == producerIndex
            })
            val producerPacket = producer.drawPackets.single()
            val candidate = assertIs<GPUCorePrimitiveClipStencilPreparedCandidate>(
                producerPacket.corePrimitiveClipStencilPreparedCandidate,
                fillRule.name,
            )
            assertEquals(producerPacket.packetId, candidate.producerPacketId, fillRule.name)
            assertEquals(
                consumers.map { it.drawPackets.single().packetId },
                candidate.consumers.map { it.packetId },
                fillRule.name,
            )
            assertEquals(listOf(7, 9), candidate.consumers.map { it.sourceOrder }, fillRule.name)
            assertEquals(24, candidate.producerFanVertices.size, fillRule.name)
            assertEquals((0..11).toList(), candidate.producerFanIndices, fillRule.name)
            assertTrue(consumers.all { consumer ->
                consumer.drawPackets.single().corePrimitiveClipStencilPreparedCandidate === candidate
            })
        }
    }

    @Test
    fun `native path clip refuses foreign geometry in its bounded shared arena`() {
        val plan = nativePathStencilPlan(GPUClipFillRule.Winding)
        val base = recording(command(340, 7), command(341, 9)).taskList.withClipPlans(
            mapOf(340 to plan, 341 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) }),
        )

        assertEquals(
            "unsupported.recording.core_primitive_clip_stencil_mixed_geometry",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `no clip and dynamic scissor share one shading pipeline key`() {
        val base = recording(command(29, 0)).taskList.withClipPlans(mapOf(29 to GPUClipExecutionPlan.NoClip))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val semantic = semantic(packet)

        assertEquals(
            corePrimitiveRenderPipelineKey(semantic, GPUClipExecutionPlan.NoClip, requireNotNull(packet.blendPlan)),
            corePrimitiveRenderPipelineKey(
                semantic,
                GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(2, 3, 10, 11)),
                requireNotNull(packet.blendPlan),
            ),
        )
    }

    @Test
    fun `rect and direct triangles share one direct shading pipeline key`() {
        val base = recording(command(95, 0), command(96, 1)).taskList.withClipPlans(
            mapOf(95 to GPUClipExecutionPlan.NoClip, 96 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val rectPacket = packets.single { it.commandIdValue == 95 }
        val trianglePacket = packets.single { it.commandIdValue == 96 }
        val rect = semantic(rectPacket)
        val triangles = semantic(
            trianglePacket,
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(1f, 1f, 8f, 1f, 4f, 8f),
                indices = listOf(0, 2, 1),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 3,
                coverBounds = GPUPixelBounds(1, 1, 8, 8),
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
            ),
        )

        assertEquals(
            corePrimitiveRenderPipelineKey(
                rect,
                GPUClipExecutionPlan.NoClip,
                requireNotNull(rectPacket.blendPlan),
            ),
            corePrimitiveRenderPipelineKey(
                triangles,
                GPUClipExecutionPlan.NoClip,
                requireNotNull(trianglePacket.blendPlan),
            ),
        )
    }

    @Test
    fun `direct shading pipeline stays distinct from analytic and stencil routes`() {
        val base = recording(command(97, 0), command(98, 1), command(99, 2)).taskList.withClipPlans(
            mapOf(
                97 to GPUClipExecutionPlan.NoClip,
                98 to GPUClipExecutionPlan.NoClip,
                99 to GPUClipExecutionPlan.NoClip,
            ),
        ).withPacketRouteIdentity(
            commandId = 98,
            analysisRecordId = "analysis.fill_rrect.98",
            renderStepIdentity = CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY,
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        fun packet(commandId: Int) = packets.single { it.commandIdValue == commandId }
        val directPacket = packet(97)
        val analyticPacket = packet(98)
        val stencilPacket = packet(99)
        val direct = semantic(directPacket)
        val analytic = semantic(
            analyticPacket,
            GPUCorePrimitiveGeometryInput.RRect(1f, 1f, 8f, 8f, List(8) { 1f }),
            GPUCorePrimitiveCoverageMode.ScalarAA,
        )
        val stencil = semantic(
            stencilPacket,
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(
                    -1f, -1f, 1f, 1f, 7f, 1f,
                    -1f, -1f, 7f, 1f, 4f, 7f,
                    -1f, -1f, 4f, 7f, 1f, 1f,
                ),
                indices = (0..8).toList(),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 3,
                coverBounds = GPUPixelBounds(0, 0, 8, 8),
                geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            ),
            GPUCorePrimitiveCoverageMode.Stencil1x,
        )
        fun key(
            semantic: GPUDrawSemanticPayload.CorePrimitive,
            packet: GPUDrawPacket,
        ) = corePrimitiveRenderPipelineKey(
            semantic,
            GPUClipExecutionPlan.NoClip,
            requireNotNull(packet.blendPlan),
        )

        assertNotEquals(key(direct, directPacket), key(analytic, analyticPacket))
        assertNotEquals(key(direct, directPacket), key(stencil, stencilPacket))
    }

    @Test
    fun `shading pipeline blend identity ignores layer ordering token but retains fixed state`() {
        val base = recording(command(28, 0)).taskList.withClipPlans(mapOf(28 to GPUClipExecutionPlan.NoClip))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val semantic = semantic(packet)
        val child = requireNotNull(packet.blendPlan)
        val first = GPUBlendPlan.LayerCompositeBlend(child, "layer.order.first")
        val second = GPUBlendPlan.LayerCompositeBlend(child, "layer.order.second")
        val changedState = GPUBlendPlan.LayerCompositeBlend(
            (child as GPUBlendPlan.FixedFunctionBlend).copy(state = child.state.copy(writeMask = "rgb")),
            "layer.order.first",
        )

        assertEquals(
            corePrimitiveRenderPipelineKey(semantic, GPUClipExecutionPlan.NoClip, first),
            corePrimitiveRenderPipelineKey(semantic, GPUClipExecutionPlan.NoClip, second),
        )
        assertNotEquals(
            corePrimitiveRenderPipelineKey(semantic, GPUClipExecutionPlan.NoClip, first),
            corePrimitiveRenderPipelineKey(semantic, GPUClipExecutionPlan.NoClip, changedState),
        )
    }

    @Test
    fun `separate stencil artifacts never clear color between scene draws`() {
        val first = stencilPlan(contentKey = "clip.stencil.first")
        val second = stencilPlan(contentKey = "clip.stencil.second").copy(
            atomicGroup = GPUClipAtomicGroupID("atomic.clip.second"),
            orderingToken = GPUClipOrderingToken("token.clip.second"),
        )
        val base = recording(command(26, 0), command(27, 1)).taskList.withClipPlans(
            mapOf(26 to first, 27 to second),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val producers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer }
        val consumers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.Shading }

        assertTrue(producers.all { it.loadStore == GPULoadStorePlan("load", GPUStorePlan.Store) })
        assertTrue(producers.all {
            it.depthStencilLoadStore is GPUDepthStencilLoadStorePlan.WritableStencil
        })
        assertEquals(listOf("clear", "load"), consumers.map { it.loadStore.loadOp })
    }

    @Test
    fun `stencil pipeline key ignores clip content identity but changes with stencil state`() {
        fun producerKey(plan: GPUClipExecutionPlan.StencilCoverage) = run {
            val base = recording(command(31, 0)).taskList.withClipPlans(mapOf(31 to plan))
            val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
            val recorded = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, packets.associate { it.commandIdValue to semantic(it) }),
                ),
            ).taskList
            recorded.tasks.filterIsInstance<GPUTask.Render>()
                .single { it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer }
                .drawPackets.single().renderPipelineKey
        }
        val first = stencilPlan(contentKey = "clip.first")
        val sameStateDifferentContent = stencilPlan(contentKey = "clip.second")
        val sameStateDifferentDynamicValues = stencilPlan(contentKey = "clip.dynamic").copy(
            producer = stencilPlan(contentKey = "clip.dynamic").producer.copy(
                reference = 7u,
            ),
        )
        val unsupportedLoadStore = stencilPlan(contentKey = "clip.load-store").copy(
            producer = stencilPlan(contentKey = "clip.load-store").producer.copy(
                loadOperation = GPUClipStencilLoadOperation.Load,
                storeOperation = GPUClipStencilStoreOperation.Discard,
                clearValue = null,
            ),
        )
        val differentState = stencilPlan(contentKey = "clip.third").copy(
            producer = stencilPlan(contentKey = "clip.third").producer.copy(
                frontPassOperation = GPUClipStencilOperation.Invert,
            ),
        )

        assertEquals(producerKey(first), producerKey(sameStateDifferentContent))
        assertEquals(producerKey(first), producerKey(sameStateDifferentDynamicValues))
        assertEquals(
            corePrimitiveClipProducerPipelineKey(first, GPUClipProducerAuthority.Stencil(first.producer)),
            corePrimitiveClipProducerPipelineKey(
                unsupportedLoadStore,
                GPUClipProducerAuthority.Stencil(unsupportedLoadStore.producer),
            ),
        )
        assertNotEquals(producerKey(first), producerKey(differentState))
    }

    @Test
    fun `stencil attachment uses full target extent and semantic retains exact execution identity`() {
        val workBounds = GPUPixelBounds(2, 3, 10, 11)
        val plan = stencilPlan(bounds = workBounds)
        val base = recording(command(32, 0)).taskList.withClipPlans(mapOf(32 to plan))
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val preparation = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap { it.requests }
            .single { it.role == GPUFrameResourceRole.ClipDepthStencil }
        val descriptor = assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor>(
            preparation.descriptor,
        )
        assertEquals(targetBounds, descriptor.logicalBounds)
        assertEquals(targetBounds.width.toLong() * targetBounds.height * 4L, preparation.byteSize)
        assertNotEquals(plan.requiredBytes, preparation.byteSize)
        val consumerSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
            taskList.tasks.filterIsInstance<GPUTask.Render>()
                .single { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
                .drawPackets.single().semanticPayload,
        )
        assertEquals(plan.canonicalIdentity(), consumerSemantic.clipExecutionPlanIdentity)
        assertTrue(consumerSemantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `stencil edge fans become one ordered path render with exact attachment and authorities`() {
        val base = recording(command(33, 0), command(34, 1)).taskList.withClipPlans(
            mapOf(33 to GPUClipExecutionPlan.NoClip, 34 to GPUClipExecutionPlan.NoClip),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val normalBounds = GPUPixelBounds(1, 2, 8, 9)
        val inverseBounds = GPUPixelBounds(4, 4, 12, 12)
        val semantics = mapOf(
            33 to semantic(
                packets.single { it.commandIdValue == 33 },
                stencilGeometry(normalBounds),
                GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
            34 to semantic(
                packets.single { it.commandIdValue == 34 },
                stencilGeometry(inverseBounds, inverseFill = true, fillRule = GPUCorePrimitiveFillRule.EvenOdd),
                GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
        )

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        val prepared = render.drawPackets

        assertEquals(
            listOf(
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
            ),
            prepared.map(GPUDrawPacket::role),
        )
        assertEquals(listOf(33, 33, 34, 34), prepared.map(GPUDrawPacket::commandIdValue))
        assertEquals(1, prepared.map(GPUDrawPacket::passId).distinct().size)
        assertEquals(
            listOf(normalBounds, normalBounds, targetBounds, targetBounds).map(::corePrimitiveScissorAuthority),
            prepared.map(GPUDrawPacket::scissorBoundsHash),
        )
        assertEquals(
            listOf(normalBounds, normalBounds, targetBounds, targetBounds),
            prepared.map { packet ->
                assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload).scissorBounds
            },
        )
        assertEquals(GPULoadStorePlan("clear", GPUStorePlan.Store), render.loadStore)
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Discard,
                0u,
            ),
            render.depthStencilLoadStore,
        )
        val pathUse = render.resourceUses.single { it.role == GPUFrameResourceRole.PathDepthStencil }
        assertEquals(GPUFrameResourceUsage.RenderAttachment, pathUse.usage)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, pathUse.lifetime)
        assertTrue(pathUse.write)

        val preparation = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { it.role == GPUFrameResourceRole.PathDepthStencil }
        val descriptor = assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor>(
            preparation.descriptor,
        )
        val pathDepthStencilBytes = targetBounds.width.toLong() * targetBounds.height * 4L
        assertEquals(targetBounds, descriptor.logicalBounds)
        assertEquals("depth24plus-stencil8", descriptor.format.value)
        assertEquals(1, descriptor.sampleCount)
        assertEquals(setOf(GPUFrameResourceUsage.RenderAttachment), preparation.usages)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, preparation.lifetime)
        assertEquals(pathDepthStencilBytes, preparation.byteSize)
        val geometryPreparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        assertEquals(
            2L * (18L * Float.SIZE_BYTES + 4L * 2L * Float.SIZE_BYTES),
            geometryPreparations.single { it.role == GPUFrameResourceRole.VertexData }.byteSize,
        )
        assertEquals(
            2L * (9L * Int.SIZE_BYTES + 6L * Int.SIZE_BYTES),
            geometryPreparations.single { it.role == GPUFrameResourceRole.IndexData }.byteSize,
        )
        assertEquals(
            pathDepthStencilBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil),
        )

        prepared.chunked(2).forEach { (producer, cover) ->
            val producerAuthority = requireNotNull(producer.corePrimitivePreparedAuthority)
            val coverAuthority = requireNotNull(cover.corePrimitivePreparedAuthority)
            assertEquals(
                org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role
                    .PathStencilProducer,
                producerAuthority.structuralPipelineKey.role,
            )
            assertEquals(
                org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role
                    .PathStencilCover,
                coverAuthority.structuralPipelineKey.role,
            )
            assertNotEquals(producer.renderPipelineKey, cover.renderPipelineKey)
            assertSame(producerAuthority.uniformSlabSeal, coverAuthority.uniformSlabSeal)
            assertTrue(producerAuthority.uniformSlabSeal != null)
        }

        val exactBudget = Math.addExact(
            taskList.memoryBudget.targetResidentBytes,
            taskList.memoryBudget.peakFrameTransientBytes,
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, semantics).copy(configuredAggregateBudgetBytes = exactBudget),
            ),
        )
        assertEquals(
            "unsupported.frame_memory.aggregate_budget_exceeded",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, semantics).copy(configuredAggregateBudgetBytes = exactBudget - 1L),
                ),
            ).diagnostic.code.value,
        )
    }

    @Test
    fun `path stencil contradictions refuse before any prepared topology escapes`() {
        val base = recording(command(35, 0)).taskList.withClipPlans(
            mapOf(35 to GPUClipExecutionPlan.NoClip),
        )
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val geometry = stencilGeometry(GPUPixelBounds(1, 1, 8, 8))

        val wrongCoverage = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(35 to semantic(packet, geometry, GPUCorePrimitiveCoverageMode.FullOrScissor)),
            ).copy(configuredAggregateBudgetBytes = 1),
        )
        assertEquals(
            "invalid.core_primitive.coverage_sample.geometry_coverage",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(wrongCoverage).diagnostic.code.value,
        )

        val clippedBase = base.withClipPlans(mapOf(35 to stencilPlan()))
        val clippedPacket = clippedBase.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val wrongClip = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                clippedBase,
                mapOf(35 to semantic(clippedPacket, geometry, GPUCorePrimitiveCoverageMode.Stencil1x)),
            ).copy(configuredAggregateBudgetBytes = 1),
        )
        assertEquals(
            "unsupported.recording.core_primitive_path_stencil_clip",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(wrongClip).diagnostic.code.value,
        )

        val stroke = stencilGeometry(
            coverBounds = GPUPixelBounds(1, 1, 8, 8),
            geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        )
        val unsupportedStroke = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(
                base,
                mapOf(35 to semantic(packet, stroke, GPUCorePrimitiveCoverageMode.Stencil1x)),
            ).copy(configuredAggregateBudgetBytes = 1),
        )
        assertEquals(
            "invalid.core_primitive.coverage_sample.geometry_coverage",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(unsupportedStroke).diagnostic.code.value,
        )
    }

    @Test
    fun `path stencil scissor intersects normal bounds but inverse target viewport`() {
        val clipBounds = GPUPixelBounds(3, 3, 10, 10)
        val base = recording(command(36, 0), command(37, 1)).taskList.withClipPlans(
            mapOf(
                36 to GPUClipExecutionPlan.ScissorOnly(clipBounds),
                37 to GPUClipExecutionPlan.ScissorOnly(clipBounds),
            ),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            36 to semantic(
                packets.single { it.commandIdValue == 36 },
                stencilGeometry(GPUPixelBounds(1, 1, 8, 8)),
                GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
            37 to semantic(
                packets.single { it.commandIdValue == 37 },
                stencilGeometry(GPUPixelBounds(1, 1, 8, 8), inverseFill = true),
                GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
        )

        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets

        assertEquals(
            listOf(
                GPUPixelBounds(3, 3, 8, 8),
                GPUPixelBounds(3, 3, 8, 8),
                clipBounds,
                clipBounds,
            ).map(::corePrimitiveScissorAuthority),
            prepared.map(GPUDrawPacket::scissorBoundsHash),
        )
    }

    @Test
    fun `mixed direct path direct frame uses one depth stencil compatible render`() {
        val base = recording(command(38, 0), command(39, 1), command(40, 2)).taskList.withClipPlans(
            mapOf(
                38 to GPUClipExecutionPlan.NoClip,
                39 to GPUClipExecutionPlan.NoClip,
                40 to GPUClipExecutionPlan.NoClip,
            ),
        )
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            38 to semantic(basePackets.single { it.commandIdValue == 38 }),
            39 to semantic(
                basePackets.single { it.commandIdValue == 39 },
                stencilGeometry(GPUPixelBounds(1, 1, 8, 8)),
                GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
            40 to semantic(basePackets.single { it.commandIdValue == 40 }),
        )

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        val prepared = render.drawPackets

        assertEquals(
            listOf(
                GPUDrawPacketRole.Shading,
                GPUDrawPacketRole.PathStencilProducer,
                GPUDrawPacketRole.PathStencilCover,
                GPUDrawPacketRole.Shading,
            ),
            prepared.map(GPUDrawPacket::role),
        )
        assertEquals(listOf(38, 39, 39, 40), prepared.map(GPUDrawPacket::commandIdValue))
        assertEquals(
            listOf(
                basePackets.single { it.commandIdValue == 38 }.packetId.value,
                "${basePackets.single { it.commandIdValue == 39 }.packetId.value}.path-stencil-producer",
                "${basePackets.single { it.commandIdValue == 39 }.packetId.value}.path-stencil-cover",
                basePackets.single { it.commandIdValue == 40 }.packetId.value,
            ),
            prepared.map { it.packetId.value },
        )
        assertEquals(prepared.size, prepared.map(GPUDrawPacket::packetId).distinct().size)
        assertEquals(1, prepared.map(GPUDrawPacket::passId).distinct().size)
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Discard,
                0u,
            ),
            render.depthStencilLoadStore,
        )
        listOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.PathDepthStencil,
        ).forEach { role ->
            assertEquals(1, render.resourceUses.count { it.role == role }, "resource role $role")
        }
        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        listOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.PathDepthStencil,
        ).forEach { role ->
            assertEquals(1, preparations.count { it.role == role }, "preparation role $role")
        }

        val directAuthorities = prepared.filter { it.role == GPUDrawPacketRole.Shading }
            .map { requireNotNull(it.corePrimitivePreparedAuthority) }
        val neutralState = directAuthorities.map { authority ->
            assertIs<GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil>(
                authority.structuralPipelineKey.depthStencil,
            )
        }
        assertEquals(1, neutralState.distinct().size)
        neutralState.forEach { state ->
            assertEquals(
                GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
                state.format,
            )
            assertEquals(0u, state.readMask)
            assertEquals(0u, state.writeMask)
            listOf(state.front, state.back).forEach { face ->
                assertEquals(GPUClipStencilCompare.Always, face.compare)
                assertEquals(GPUClipStencilOperation.Keep, face.passOperation)
                assertEquals(GPUClipStencilOperation.Keep, face.failOperation)
                assertEquals(GPUClipStencilOperation.Keep, face.depthFailOperation)
            }
        }
        assertEquals(1, directAuthorities.map { it.renderPipelineKey }.distinct().size)
        val slab = requireNotNull(directAuthorities.first().uniformSlabSeal)
        prepared.forEach { packet ->
            assertSame(slab, requireNotNull(packet.corePrimitivePreparedAuthority).uniformSlabSeal)
        }

        val directOnlyBase = recording(command(41, 0)).taskList.withClipPlans(
            mapOf(41 to GPUClipExecutionPlan.NoClip),
        )
        val directOnlyPacket = directOnlyBase.tasks.filterIsInstance<GPUTask.Render>()
            .single().drawPackets.single()
        val directOnly = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(directOnlyBase, mapOf(41 to semantic(directOnlyPacket))),
            ),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val directOnlyAuthority = requireNotNull(directOnly.corePrimitivePreparedAuthority)
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
            directOnlyAuthority.structuralPipelineKey.depthStencil,
        )
        assertNotEquals(directOnlyAuthority.renderPipelineKey, directAuthorities.first().renderPipelineKey)
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `uniform32 and uniform80 shapes refuse atomically before budget planning`() {
        val base = recording(command(42, 0), command(44, 1)).taskList.withClipPlans(
            mapOf(
                42 to GPUClipExecutionPlan.NoClip,
                44 to GPUClipExecutionPlan.NoClip,
            ),
        ).withPacketRouteIdentity(
            commandId = 44,
            analysisRecordId = "analysis.fill_rrect.44",
            renderStepIdentity = CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY,
        )
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = mapOf(
            42 to semantic(basePackets.single { it.commandIdValue == 42 }),
            44 to semantic(
                basePackets.single { it.commandIdValue == 44 },
                GPUCorePrimitiveGeometryInput.RRect(1f, 1f, 8f, 8f, List(8) { 1f }),
                GPUCorePrimitiveCoverageMode.ScalarAA,
            ),
        )

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, semantics).copy(configuredAggregateBudgetBytes = 1L),
        )

        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `multisample clip refuses before a partial depth stencil allocation can be planned`() {
        val plan = stencilPlan(bounds = GPUPixelBounds(2, 3, 10, 11)).copy(sampleCount = 4)
        val base = recording(command(34, 0)).taskList.withClipPlans(mapOf(34 to plan))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(34 to semantic(packet))).copy(configuredAggregateBudgetBytes = 1),
        )

        assertEquals(
            "unsupported.recording.core_primitive_clip_multisample_topology",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `mask depth stencil refuses until its full target topology is available`() {
        val plan = maskPlan(depthStencilRequired = true)
        val base = recording(command(35, 0)).taskList.withClipPlans(mapOf(35 to plan))
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(35 to semantic(packet))).copy(configuredAggregateBudgetBytes = 1),
        )

        assertEquals(
            "unsupported.recording.core_primitive_clip_mask_depth_stencil_topology_unavailable",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `equal mask plans dedupe ordered producers and retain one consumer per draw paint order`() {
        val plan = maskPlan()
        val base = recording(command(40, 0), command(41, 1)).taskList.withClipPlans(
            mapOf(40 to plan, 41 to plan),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val producers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer }
        assertEquals(2, producers.size)
        assertTrue(producers[0].drawPackets.single().insertionReasonCode.contains("Intersect"))
        assertTrue(producers[1].drawPackets.single().insertionReasonCode.contains("Difference"))
        val consumers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
        assertEquals(listOf(0, 1), consumers.map { it.drawPackets.single().originalPaintOrder })
        assertTrue(consumers.all { it.drawPackets.size == 1 })
        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.ClipMask })
        assertFalse(preparations.any {
            it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData
        })
        assertEquals(
            plan.resolvedBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch),
        )
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `same clip key with a different full plan refuses before budget materialization`() {
        val first = maskPlan()
        val second = maskPlan(differenceAntiAlias = false)
        val base = recording(command(50, 0), command(51, 1)).taskList.withClipPlans(
            mapOf(50 to first, 51 to second),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) }).copy(
                configuredAggregateBudgetBytes = 1,
            ),
        )

        assertEquals(
            "invalid.recording.core_primitive_clip_content_key_collision",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `canonical clip identities keep colliding sanitized content keys distinct`() {
        val slashPlan = maskPlan(contentKey = "clip.a/b")
        val questionPlan = maskPlan(contentKey = "clip.a?b")
        val base = recording(command(60, 0), command(61, 1)).taskList.withClipPlans(
            mapOf(60 to slashPlan, 61 to questionPlan),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val producerIds = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer }
            .map { it.taskId }
        assertEquals(4, producerIds.size)
        assertEquals(producerIds.size, producerIds.distinct().size)
        val clipResources = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap { it.requests }
            .filter { it.role == GPUFrameResourceRole.ClipMask }
            .map { it.resource }
        assertEquals(2, clipResources.size)
        assertEquals(clipResources.size, clipResources.distinct().size)
    }

    @Test
    fun `every clip producer packet carries its exact typed producer authority`() {
        val stencil = stencilPlan()
        val mask = maskPlan()
        val base = recording(command(70, 0), command(71, 1)).taskList.withClipPlans(
            mapOf(70 to stencil, 71 to mask),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val producerPackets = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .map { it.drawPackets.single() }
            .filter { it.role == GPUDrawPacketRole.StencilProducer || it.role == GPUDrawPacketRole.ClipProducer }
        val stencilAuthority = assertIs<GPUClipProducerAuthority.Stencil>(
            producerPackets.single { it.role == GPUDrawPacketRole.StencilProducer }.clipProducerAuthority,
        )
        assertSame(stencil.producer, stencilAuthority.producer)
        val maskAuthorities = producerPackets.filter { it.role == GPUDrawPacketRole.ClipProducer }
            .map { packet -> assertIs<GPUClipProducerAuthority.Mask>(packet.clipProducerAuthority) }
        assertEquals(mask.producers, maskAuthorities.map(GPUClipProducerAuthority.Mask::producer))
    }

    @Test
    fun `mask producer blend writes exact dst in and dst out while stencil disables color writes`() {
        val stencil = stencilPlan()
        val mask = maskPlan()
        val base = recording(command(72, 0), command(73, 1)).taskList.withClipPlans(
            mapOf(72 to stencil, 73 to mask),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val producerTasks = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter {
                it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer ||
                    it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer
            }
        val producers = producerTasks.map { it.drawPackets.single() }
        val stencilBlend = assertIs<GPUBlendPlan.FixedFunctionBlend>(
            producers.single { it.role == GPUDrawPacketRole.StencilProducer }.blendPlan,
        )
        val maskBlends = producers.filter { it.role == GPUDrawPacketRole.ClipProducer }
            .map { assertIs<GPUBlendPlan.FixedFunctionBlend>(it.blendPlan) }

        assertEquals("none", stencilBlend.state.writeMask)
        assertEquals(listOf(GPUBlendMode.DST_IN, GPUBlendMode.DST_OUT), maskBlends.map { it.mode })
        assertTrue(maskBlends.all { it.state.writeMask == "rgba" })
        assertEquals(listOf("src-alpha", "one-minus-src-alpha"), maskBlends.map {
            it.state.color.destinationFactor
        })
        val maskLoadStores = producerTasks.filter { it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer }
            .map(GPUTask.Render::loadStore)
        assertEquals(
            listOf(
                GPULoadStorePlan("clear", GPUStorePlan.Store, CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL),
                GPULoadStorePlan("load", GPUStorePlan.Store),
            ),
            maskLoadStores,
        )
        assertNotEquals(maskBlends[0], maskBlends[1])
    }

    @Test
    fun `eligible coverage mask attaches prepared producer and consumer authorities`() {
        val plan = maskPlan(
            intersectAntiAlias = false,
            differenceAntiAlias = false,
            differenceGeometry = GPUClipExecutionGeometry.RRect(
                GPUClipBounds(4f, 4f, 12f, 12f),
                listOf(1f, 2f, 1f, 2f, 1f, 2f, 1f, 2f),
            ),
        )
        val base = recording(command(74, 0), command(75, 1)).taskList.withClipPlans(
            mapOf(74 to plan, 75 to plan),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val directTriangles = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(1f, 1f, 8f, 1f, 4f, 8f),
            indices = listOf(0, 2, 1),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(1, 1, 8, 8),
            geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        )
        val semantics = packets.associate { packet ->
            packet.commandIdValue to if (packet.commandIdValue == 75) {
                semantic(packet, directTriangles)
            } else {
                semantic(packet)
            }
        }

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, semantics),
        )
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            result,
            (result as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val preparedPackets = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val producers = preparedPackets.filter { it.role == GPUDrawPacketRole.ClipProducer }
        val consumers = preparedPackets.filter { it.role == GPUDrawPacketRole.Shading }

        assertEquals(2, producers.size)
        assertEquals(2, consumers.size)
        assertTrue(producers.all { it.corePrimitivePreparedAuthority != null })
        assertTrue(consumers.all { it.corePrimitivePreparedAuthority != null })
        assertEquals(
            List(2) { GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer },
            producers.map { requireNotNull(it.corePrimitivePreparedAuthority).structuralPipelineKey.role },
        )
        assertEquals(
            List(2) { GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer },
            consumers.map { requireNotNull(it.corePrimitivePreparedAuthority).structuralPipelineKey.role },
        )
        val slab = requireNotNull(
            producers.first().corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal,
        )
        assertTrue((producers + consumers).all {
            it.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal === slab
        })
        val detachedProducer = producers.first().withClipPlan(producers.first().clipExecutionPlan)
        assertFailsWith<IllegalArgumentException> {
            detachedProducer.attachCorePrimitivePreparedAuthority(
                requireNotNull(producers.first().corePrimitivePreparedAuthority).copy(
                    coverageMaskUniformSlabSeal = null,
                ),
            )
        }
        assertEquals(listOf(0, 1), slab.producerSourceOrders)
        assertEquals(producers.map { it.packetId }, slab.producerPacketIds)
        assertEquals(listOf(74, 75), slab.consumerCommandIds)
        assertEquals(consumers.map { it.packetId }, slab.consumerPacketIds)
        consumers.zip(slab.consumerSlots).forEach { (consumer, slot) ->
            val preparedSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
                consumer.semanticPayload,
            )
            assertTrue(slot.semanticAuthority.matches(preparedSemantic))
            assertFalse(slot.semanticAuthority.matches(preparedSemantic.sameContentClone()))
        }
        assertEquals(
            listOf(
                null,
                "prepared-core-primitive.coverage-mask.consumer.1.${consumers[1].packetId.value}",
            ),
            slab.consumerSlots.map { it.dependencyFromPreviousConsumerToken },
        )
        assertTrue(slab.plan.slots.all { it.payloadBytes == 64L })
        assertEquals(listOf(0L, 256L, 512L, 768L), slab.plan.slots.map { it.alignedOffset })

        val packedBytes = slab.packedBytesSnapshot()
        fun slotBytes(index: Int): ByteArray {
            val offset = slab.plan.slots[index].alignedOffset.toInt()
            return packedBytes.copyOfRange(offset, offset + 64)
        }
        val expectedIntersect = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            listOf(
                0f, 0f, 16f, 16f,
                0f, 0f, 16f, 16f,
                0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f,
            ).forEach(::putFloat)
        }.array()
        val expectedDifference = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            listOf(
                0f, 0f, 16f, 16f,
                4f, 4f, 12f, 12f,
                1f, 2f, 1f, 2f,
                1f, 2f, 1f, 2f,
            ).forEach(::putFloat)
        }.array()
        val expectedConsumer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(16f)
            putFloat(16f)
            putInt(0)
            putInt(0)
            putInt(16)
            putInt(16)
            putLong(0L)
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach(::putFloat)
            putInt(0)
            repeat(12) { put(0) }
        }.array()
        assertContentEquals(expectedIntersect, slotBytes(0))
        assertContentEquals(expectedDifference, slotBytes(1))
        assertContentEquals(expectedConsumer, slotBytes(2))
        assertContentEquals(expectedConsumer, slotBytes(3))
        assertEquals(0, slotBytes(2).uint32(8))
        assertEquals(0, slotBytes(2).uint32(12))
        assertEquals(16, slotBytes(2).uint32(16))
        assertEquals(16, slotBytes(2).uint32(20))
        packedBytes[slab.plan.slots[2].alignedOffset.toInt() + 16] = 0
        assertContentEquals(expectedConsumer, slab.packedBytesSnapshot().let { snapshot ->
            val offset = slab.plan.slots[2].alignedOffset.toInt()
            snapshot.copyOfRange(offset, offset + 64)
        })
        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        assertEquals(
            56L,
            preparations.single { it.role == GPUFrameResourceRole.VertexData }.byteSize,
        )
        assertEquals(
            36L,
            preparations.single { it.role == GPUFrameResourceRole.IndexData }.byteSize,
        )

        val producerTasks = taskList.tasks.filterIsInstance<GPUTask.Render>().filter {
            it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer
        }
        val consumerTasks = taskList.tasks.filterIsInstance<GPUTask.Render>().filter {
            it.drawPackets.single().role == GPUDrawPacketRole.Shading
        }
        assertEquals(
            listOf(
                producerTasks[0].taskId to producerTasks[1].taskId,
                producerTasks[1].taskId to consumerTasks[0].taskId,
                consumerTasks[0].taskId to consumerTasks[1].taskId,
            ),
            taskList.dependencies.map { it.fromTaskId to it.toTaskId },
        )
        assertEquals(
            slab.consumerSlots[1].dependencyFromPreviousConsumerToken,
            taskList.dependencies.single {
                it.fromTaskId == consumerTasks[0].taskId &&
                    it.toTaskId == consumerTasks[1].taskId
            }.useToken?.value,
        )
    }

    @Test
    fun `base DAG translation internalizes dependency when compatible draws coalesce`() {
        val base = recording(command(80, 0), command(81, 1)).taskList.withClipPlans(
            mapOf(80 to GPUClipExecutionPlan.NoClip, 81 to GPUClipExecutionPlan.NoClip),
        )
        val source = base.dependencies.single()
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val translatedCandidates = taskList.dependencies.filter { dependency ->
            dependency.fromTaskId.value == "${source.fromTaskId.value}.core-consumer.0" &&
                dependency.toTaskId.value == "${source.toTaskId.value}.core-consumer.0"
        }
        assertTrue(translatedCandidates.isEmpty())
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(listOf(80, 81), render.drawPackets.map(GPUDrawPacket::commandIdValue))
        assertFalse(taskList.dependencies.any { it.fromTaskId == render.taskId && it.toTaskId == render.taskId })
    }

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    ) = GPUCorePrimitivePreparedFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.core.authority"),
        targetBounds = targetBounds,
        semanticsByCommandId = semantics,
    )

    private fun ByteArray.float32(offset: Int): Float =
        ByteBuffer.wrap(this, offset, Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).float

    private fun ByteArray.uint32(offset: Int): Int =
        ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).int

    private fun semantic(
        packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
        geometry: GPUCorePrimitiveGeometryInput = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        sourceFamily: GPUCorePrimitiveSourceFamily? = null,
    ): GPUDrawSemanticPayload.CorePrimitive {
        val resolvedSourceFamily = sourceFamily ?: when (geometry) {
            is GPUCorePrimitiveGeometryInput.Rect -> GPUCorePrimitiveSourceFamily.Rect
            is GPUCorePrimitiveGeometryInput.RRect -> GPUCorePrimitiveSourceFamily.RRect
            is GPUCorePrimitiveGeometryInput.TriangulatedPath -> GPUCorePrimitiveSourceFamily.Path
        }
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = resolvedSourceFamily,
                geometry = geometry,
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = targetBounds,
                scissorBounds = targetBounds,
                clipCoveragePlan = requireNotNull(packet.clipCoveragePlan),
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
                coverageMode = coverageMode,
                analysisRecordId = when (resolvedSourceFamily) {
                    GPUCorePrimitiveSourceFamily.Rect -> packet.analysisRecordId
                    GPUCorePrimitiveSourceFamily.RRect -> "analysis.fill_rrect.${packet.commandIdValue}"
                    else -> null
                },
                analysisCommandFamily = when (resolvedSourceFamily) {
                    GPUCorePrimitiveSourceFamily.Rect -> "FillRect"
                    GPUCorePrimitiveSourceFamily.RRect -> "FillRRect"
                    else -> null
                },
                rectRouteAuthority = if (resolvedSourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                    GPUCorePrimitiveRectRouteAuthority.RectAxisAligned
                } else {
                    null
                },
                rectGeometryAuthority = if (resolvedSourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                    rectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.Rect)
                } else {
                    null
                },
                rrectGeometryAuthority = if (resolvedSourceFamily == GPUCorePrimitiveSourceFamily.RRect) {
                    rrectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.RRect)
                } else {
                    null
                },
            ),
        )
    }

    private fun GPUDrawSemanticPayload.CorePrimitive.sameContentClone() =
        GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = payloadRef,
            sourceFamily = sourceFamily,
            geometry = geometry,
            premultipliedRgba = premultipliedRgba,
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            clipCoveragePlan = clipCoveragePlan,
            clipExecutionPlanIdentity = clipExecutionPlanIdentity,
            blendPlanIdentity = blendPlanIdentity,
            frameProvenance = frameProvenance,
            canonicalHash = canonicalHash,
            coverageMode = coverageMode,
            analysisRecordId = analysisRecordId,
            analysisCommandFamily = analysisCommandFamily,
            rectRouteAuthority = rectRouteAuthority,
            rectGeometryAuthority = rectGeometryAuthority,
            rrectGeometryAuthority = rrectGeometryAuthority,
        )

    private fun rectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.Rect,
    ) = corePrimitiveRectGeometryAuthority(
        GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom),
        GPUTransformFacts.identity(),
    )

    private fun rrectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.RRect,
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority {
        val source = geometry.toSourceRRect()
        val accepted = assertIs<GPURRectNormalizationResult.Accepted>(GPURRectNormalizer.normalize(source))
        return assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
            corePrimitiveRRectGeometryAuthority(source, accepted, GPUTransformFacts.identity()),
        ).authority
    }

    private fun GPUCorePrimitiveGeometryInput.RRect.toSourceRRect(): GPURRect = GPURRect(
        rect = GPURect(left, top, right, bottom),
        topLeft = GPURRectCornerRadii(radii[0], radii[1]),
        topRight = GPURRectCornerRadii(radii[2], radii[3]),
        bottomRight = GPURRectCornerRadii(radii[4], radii[5]),
        bottomLeft = GPURRectCornerRadii(radii[6], radii[7]),
    )

    private fun stencilGeometry(
        coverBounds: GPUPixelBounds,
        inverseFill: Boolean = false,
        fillRule: GPUCorePrimitiveFillRule = GPUCorePrimitiveFillRule.Winding,
        geometryMode: GPUCorePrimitiveGeometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
    ): GPUCorePrimitiveGeometryInput.TriangulatedPath = when (geometryMode) {
        GPUCorePrimitiveGeometryMode.StencilEdgeFan -> GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(
                -1f, -1f, 1f, 1f, 7f, 1f,
                -1f, -1f, 7f, 1f, 4f, 7f,
                -1f, -1f, 4f, 7f, 1f, 1f,
            ),
            indices = (0..8).toList(),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = coverBounds,
            geometryMode = geometryMode,
            fillRule = fillRule,
            inverseFill = inverseFill,
        )
        GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan -> GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
            indices = listOf(0, 1, 2),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 2,
            coverBounds = coverBounds,
            geometryMode = geometryMode,
            fillRule = fillRule,
            inverseFill = inverseFill,
            strokeStyle = GPUCorePrimitiveStrokeStyle(
                width = 2f,
                cap = "butt",
                join = "miter",
                miterLimit = 4f,
                dashIntervals = emptyList(),
                dashPhase = 0f,
                loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1,
            ),
        )
        GPUCorePrimitiveGeometryMode.DirectTriangles -> error("Direct triangles are not stencil geometry")
    }

    private fun recording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        GPURecorder(GPURecordingID("recording.core.authority"), GPUFrameID(91), capabilities()).apply {
            commands.forEach(::record)
        }.close()

    private fun GPUTaskList.withClipPlans(plans: Map<Int, GPUClipExecutionPlan>): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet -> packet.withClipPlan(plans[packet.commandIdValue]) }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                packets,
                packets.associate { packet ->
                    packet.packetId to requireNotNull(task.batchEligibilityByPacketId[packet.packetId])
                },
                task.sampleContinuationKey,
                task.compositeMembership,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUTaskList.withSamplePlan(samplePlan: GPUSamplePlan): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val continuation = (samplePlan as? GPUSamplePlan.MultisampleFrame)?.let { multisample ->
                org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey(
                    target = org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity("target.core.authority"),
                    targetGeneration = 0L,
                    deviceGeneration = capabilitySeal.deviceGeneration,
                    colorFormat = org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat("rgba8unorm"),
                    colorInterpretation = org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation(
                        "encoded-premul-srgb",
                    ),
                    samplePlan = multisample,
                    attachmentAuthority = org.graphiks.kanvas.gpu.renderer.passes
                        .GPUSampleAttachmentAuthority.PreparedFramePayload,
                    colorAttachment = org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity(
                        "msaa-color:target.core.authority:0",
                    ),
                    depthStencilAttachment = null,
                )
            }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                task.drawPackets,
                task.batchEligibilityByPacketId,
                continuation,
                task.compositeMembership,
                task.depthStencilLoadStore,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUTaskList.withRenderLoadStore(loadStore: GPULoadStorePlan): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                task.drawPackets,
                task.batchEligibilityByPacketId,
                task.sampleContinuationKey,
                task.compositeMembership,
                task.depthStencilLoadStore,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUTaskList.mergeRenderTasks(): GPUTaskList {
        val renders = tasks.filterIsInstance<GPUTask.Render>()
        require(renders.isNotEmpty() && tasks.size == renders.size)
        val first = renders.first()
        val packets = renders.flatMap(GPUTask.Render::drawPackets)
        val merged = GPUTask.Render(
            first.taskId,
            first.recordingId,
            first.phase,
            first.target,
            GPULoadStorePlan("clear", GPUStorePlan.Store),
            first.samplePlan,
            renders.flatMap(GPUTask.Render::resourceUses).distinct(),
            first.provisionalSegmentKey,
            packets,
            renders.flatMap { it.batchEligibilityByPacketId.entries }.associate { it.toPair() },
            first.sampleContinuationKey,
            first.compositeMembership,
            first.depthStencilLoadStore,
        )
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            expectedReplayKeyHash = expectedReplayKeyHash,
            tasks = listOf(merged),
            dependencies = emptyList(),
            phaseOrder = phaseOrder,
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
        )
    }

    private fun GPUTaskList.withPacketRouteIdentity(
        commandId: Int,
        analysisRecordId: String,
        renderStepIdentity: String,
    ): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet ->
                if (packet.commandIdValue != commandId) {
                    packet
                } else {
                    packet.withClipPlan(
                        plan = packet.clipExecutionPlan,
                        analysisRecordId = analysisRecordId,
                        renderStepIdentity = renderStepIdentity,
                    )
                }
            }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                packets,
                task.batchEligibilityByPacketId,
                task.sampleContinuationKey,
                task.compositeMembership,
                task.depthStencilLoadStore,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUDrawPacket.withClipPlan(
        plan: GPUClipExecutionPlan?,
        analysisRecordId: String = this.analysisRecordId,
        renderStepIdentity: String = renderStepId.value,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, GPURenderStepID(renderStepIdentity), renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, plan, diagnostics,
        clipProducerAuthority,
    )

    private fun stencilPlan(
        contentKey: String = "clip.shared.stencil",
        bounds: GPUPixelBounds = targetBounds,
    ) = GPUClipExecutionPlan.StencilCoverage(
        contentKey = contentKey,
        bounds = bounds,
        sampleCount = 1,
        atomicGroup = GPUClipAtomicGroupID("atomic.clip.shared.stencil"),
        orderingToken = GPUClipOrderingToken("token.clip.shared.stencil"),
        producer = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
            scissor = targetBounds,
            fillRule = GPUClipFillRule.Winding,
            reference = 1u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = GPUClipStencilOperation.Replace,
            backPassOperation = GPUClipStencilOperation.Replace,
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = targetBounds,
            reference = 1u,
            compare = GPUClipStencilCompare.Equal,
        ),
    )

    private fun nativePathStencilPlan(
        fillRule: GPUClipFillRule,
    ) = GPUClipExecutionPlan.StencilCoverage(
        contentKey = "clip.native.path.${fillRule.name}",
        bounds = targetBounds,
        sampleCount = 1,
        atomicGroup = GPUClipAtomicGroupID("atomic.clip.native.path.${fillRule.name}"),
        orderingToken = GPUClipOrderingToken("token.clip.native.path.${fillRule.name}"),
        producer = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Path(
                vertices = listOf(2f, 2f, 14f, 2f, 14f, 14f, 2f, 14f),
                contourStarts = listOf(0),
                fillRule = fillRule,
                inverseFill = false,
            ),
            scissor = targetBounds,
            fillRule = fillRule,
            reference = 0u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.IncrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            backPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.DecrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = targetBounds,
            reference = 0u,
            compare = GPUClipStencilCompare.NotEqual,
        ),
    )

    private fun maskPlan(
        intersectAntiAlias: Boolean = true,
        differenceAntiAlias: Boolean = true,
        contentKey: String = "clip.shared.mask",
        depthStencilRequired: Boolean = false,
        differenceGeometry: GPUClipExecutionGeometry =
            GPUClipExecutionGeometry.Rect(GPUClipBounds(4f, 4f, 12f, 12f)),
    ) = GPUClipExecutionPlan.CoverageMask(
        contentKey = contentKey,
        bounds = targetBounds,
        sampleCount = 1,
        depthStencilRequired = depthStencilRequired,
        orderingToken = GPUClipOrderingToken("token.clip.shared.mask"),
        producers = listOf(
            GPUClipMaskProducerPlan(
                0,
                GPUClipExecutionGeometry.Rect(GPUClipBounds(0f, 0f, 16f, 16f)),
                GPUClipMaskCombine.Intersect,
                intersectAntiAlias,
            ),
            GPUClipMaskProducerPlan(
                1,
                differenceGeometry,
                GPUClipMaskCombine.Difference,
                differenceAntiAlias,
            ),
        ),
        consumer = GPUClipMaskConsumerPlan(),
    )

    private fun command(
        commandId: Int,
        paintOrder: Int,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        clipPlan: GPUClipCoveragePlan = GPUClipCoveragePlan.NoClip,
    ) = command(
        commandId,
        paintOrder,
        GPUClipFacts(
            kind = when (clipPlan) {
                GPUClipCoveragePlan.NoClip -> GPUClipKind.WideOpen
                is GPUClipCoveragePlan.Scissor -> GPUClipKind.DeviceRect
                is GPUClipCoveragePlan.AnalyticIntersection,
                is GPUClipCoveragePlan.Mask,
                is GPUClipCoveragePlan.Refused,
                -> GPUClipKind.ComplexStack
            },
            bounds = GPUBounds(0f, 0f, 16f, 16f),
            coveragePlan = clipPlan,
        ),
        provenance,
    )

    private fun command(
        commandId: Int,
        paintOrder: Int,
        clip: GPUClipFacts,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
    ) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = targetFacts,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        clip = clip,
        paintOrder = paintOrder,
        source = GPUCommandSource("unit-test", "fillRect", provenance),
    )

    private fun scissor() = GPUClipCoveragePlan.Scissor(GPUClipBounds(0f, 0f, 16f, 16f))

    private fun analyticIntersection(
        vararg elements: GPUClipAnalyticElement,
    ) = GPUClipExecutionPlan.AnalyticIntersection(elements.toList())

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit-test", "supported", true, "core-authority"),
            GPUCapabilityFact("first_slice.scissor.native", "unit-test", "supported", true, "core-authority"),
        ),
        snapshotId = "core-authority",
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private fun msaaCapabilities() = capabilities().copy(
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                    resolveSourceSampleCounts = setOf(4),
                ),
                GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                ),
            ),
        ),
    )

    private companion object {
        val targetBounds = GPUPixelBounds(0, 0, 16, 16)
        val targetFacts = GPUTargetFacts(16, 16, "rgba8unorm")
    }
}
