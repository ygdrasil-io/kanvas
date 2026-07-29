package org.graphiks.kanvas.gpu.renderer.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextShaderComposer
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8Shader

internal object GPUPreparedTextCompositePreflightRefusalCodes {
    const val NATIVE_BLEND = "invalid.preflight.text.blend"
    const val COMPOSITE_SOURCE = "invalid.preflight.text.composite_source"
    const val COMPOSITE_ABI = "invalid.preflight.text.composite_abi"
    const val INSTANCE_VERTEX_ABI = "invalid.preflight.text.instance_vertex_abi"
    const val DRAW_UNIFORM = "invalid.preflight.text.draw_uniform"
    const val BINDING_LAYOUT = "invalid.preflight.text.composite_binding_layout"
}

/**
 * Pure Task 5 native-domain gate shared by recording and execution preflight.
 *
 * Non-fixed plans retain their semantic identity for later routes, but cannot enter the current
 * Prepared TextA8 native handoff.
 */
internal fun preparedTextNativeBlendDomainRefusal(
    blendPlans: List<GPUBlendPlan?>,
): GPUPreparedTextCompositePreflightRefusal? =
    if (blendPlans.any { blendPlan -> blendPlan !is GPUBlendPlan.FixedFunctionBlend }) {
        GPUPreparedTextCompositePreflightRefusal(
            code = GPUPreparedTextCompositePreflightRefusalCodes.NATIVE_BLEND,
            message =
                "Prepared TextA8 native materialization requires a fixed-function blend plan.",
        )
    } else {
        null
    }

internal data class GPUPreparedTextCompositePreflightRefusal(
    val code: String,
    val message: String,
) {
    init {
        require(code.isNotBlank() && message.isNotBlank())
    }
}

internal object GPUPreparedTextCompositePreflight {
    fun validate(
        binding: GPUPreparedTextRenderBinding,
        semantic: GPUDrawSemanticPayload.TextA8,
        capabilities: GPUCapabilities,
        framePlan: GPUFramePlan,
        renderSourceStepIndex: Int,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val render = framePlan.steps.getOrNull(renderSourceStepIndex)
            as? GPUFrameStep.RenderPassStep
            ?: return bindingLayoutRefusal(
                "Prepared TextA8 composite validation requires its exact render step.",
            )
        val packet = render.drawPackets.singleOrNull { packet ->
            packet.packetId == binding.packetId
        } ?: return bindingLayoutRefusal(
            "Prepared TextA8 composite binding requires one exact packet.",
        )
        if (packet.semanticPayload !== semantic ||
            render.preparedTextBindingsByPacketId[binding.packetId] !== binding
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 semantic and binding do not belong to the exact render packet.",
            )
        }
        if (!semantic.hasCanonicalHashIntegrity() ||
            binding.preflightSeal.semanticCanonicalHash != semantic.canonicalHash ||
            binding.preflightSeal.capabilitySnapshotHash != semantic.capabilitySnapshotHash
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 semantic, capability, binding, and frame identities diverged.",
            )
        }
        val compositeSeal = binding.preflightSeal.textA8Composite
            ?: return bindingLayoutRefusal(
                "Prepared TextA8 binding requires one composite preflight seal.",
            )
        if (compositeSeal.deviceToLocal.rawBits() != semantic.deviceToLocal.rawBits()) {
            return drawUniformRefusal(
                "Prepared TextA8 device-to-local affine bits changed after sealing.",
            )
        }

        val preparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val targetPreparation = preparations.singleOrNull { request ->
            request.resource == render.target
        }
        val targetDescriptor = targetPreparation?.descriptor as? GPUFrameTextureDescriptor
            ?: return bindingLayoutRefusal(
                "Prepared TextA8 composite requires one exact target descriptor.",
            )
        val expectedFragment = runCatching {
            semantic.material.authenticatedSnapshot().composableFragment
        }.getOrElse { failure ->
            return sourceRefusal(
                "Prepared TextA8 material could not be re-authenticated: " +
                    failure::class.simpleName.orEmpty(),
            )
        }
        val fixedFunctionBlendState = (
            packet.blendPlan as? GPUBlendPlan.FixedFunctionBlend
            )?.state
        validateInstanceVertex(
            binding,
            PreparedTextA8Shader.VertexLayout,
            compositeSeal.compositeVertexLayout,
            render,
            preparations,
        )?.let { return it }
        validateDrawUniform(
            binding,
            semantic,
            capabilities,
        )?.let { return it }
        validateProgramSourceAndAbi(
            binding.compositeProgram,
            compositeSeal,
        )?.let { return it }
        validateBindingLayout(
            binding.compositeProgram,
            expectedFragment,
            render,
        )?.let { return it }
        validatePipelineKey(
            binding.compositeProgram,
            targetFormatClass = targetDescriptor.format.value,
            blendPlanIdentity = semantic.blendPlanIdentity,
            fixedFunctionBlendState = fixedFunctionBlendState,
            seal = compositeSeal,
        )?.let { return it }
        validateDrawUniformTopology(
            binding,
            framePlan,
            render,
            renderSourceStepIndex,
            preparations,
        )?.let { return it }
        return null
    }

    private fun validateProgramSourceAndAbi(
        actual: GPUPreparedTextCompositeProgram,
        seal: GPUPreparedTextCompositePreflightSeal,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val actualSourceHash = actual.wgslSource.encodeToByteArray().preparedTextSha256()
        if (actual.sourceHash != actualSourceHash ||
            seal.compositeSourceHash != actual.sourceHash
        ) {
            return sourceRefusal(
                "Prepared TextA8 final WGSL and its exact source hash changed after composition.",
            )
        }
        if (actual.vertexEntryPoint != "vs_main" ||
            actual.fragmentEntryPoint != "fs_main" ||
            seal.compositeVertexEntryPoint != actual.vertexEntryPoint ||
            seal.compositeFragmentEntryPoint != actual.fragmentEntryPoint ||
            !actual.abiHash.matches(Regex("[0-9a-f]{64}")) ||
            seal.compositeAbiHash != actual.abiHash
        ) {
            return abiRefusal(
                "Prepared TextA8 entry points, reflected ABI, or seal changed.",
            )
        }
        return null
    }

    private fun validateBindingLayout(
        actual: GPUPreparedTextCompositeProgram,
        expectedFragment: GPUPreparedMaterialFragment,
        render: GPUFrameStep.RenderPassStep,
    ): GPUPreparedTextCompositePreflightRefusal? {
        if (actual.bindingPlan.drawUniformGroup != 0 ||
            actual.bindingPlan.drawUniformBinding != 0 ||
            actual.bindingPlan.atlasTextureGroup != 2 ||
            actual.bindingPlan.atlasTextureBinding != 0 ||
            actual.bindingPlan.atlasSamplerGroup != 2 ||
            actual.bindingPlan.atlasSamplerBinding != 1 ||
            !actual.bindingPlan.materialFragment.matches(expectedFragment)
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 draw, material, and atlas binding layout changed.",
            )
        }
        // Task 9 has already authenticated every non-draw operand and its order.
        // Task 4 owns only the exact insertion point of the new draw-uniform use.
        val drawUniformRef = render.preparedTextBindingsByPacketId.values
            .filter(GPUPreparedTextRenderBinding::hasTextA8Composite)
            .map(GPUPreparedTextRenderBinding::drawUniformBufferPlan)
            .map { plan -> plan.bufferRef }
            .distinct()
            .singleOrNull()
        val drawUniformIndexes = render.resourceUses.mapIndexedNotNull { index, use ->
            index.takeIf { use.resource == drawUniformRef }
        }
        val drawUniformIndex = drawUniformIndexes.singleOrNull()
        if (drawUniformRef == null ||
            drawUniformIndex == null ||
            render.resourceUses.take(drawUniformIndex).any { use ->
                use.role != GPUFrameResourceRole.GlyphAtlas &&
                    use.role != GPUFrameResourceRole.VertexData
            } ||
            render.resourceUses.drop(drawUniformIndex + 1).any { use ->
                use.role == GPUFrameResourceRole.GlyphAtlas ||
                    use.role == GPUFrameResourceRole.VertexData
            }
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 render operands changed from their exact canonical order.",
            )
        }
        return null
    }

    private fun validatePipelineKey(
        actual: GPUPreparedTextCompositeProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
        fixedFunctionBlendState: GPUFixedFunctionBlendState?,
        seal: GPUPreparedTextCompositePreflightSeal,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val expectedPipelineKey = GPUPreparedTextShaderComposer.pipelineKey(
            sourceHash = actual.sourceHash,
            abiHash = actual.abiHash,
            targetFormatClass = targetFormatClass,
            blendPlanIdentity = blendPlanIdentity,
        )
        if (actual.pipelineKey != expectedPipelineKey ||
            actual.targetFormatClass != targetFormatClass ||
            actual.blendPlanIdentity != blendPlanIdentity ||
            actual.fixedFunctionBlendState != fixedFunctionBlendState ||
            seal.compositePipelineKey != actual.pipelineKey
        ) {
            return abiRefusal(
                "Prepared TextA8 target/blend state, pipeline key, or seal changed.",
            )
        }
        return null
    }

    private fun validateInstanceVertex(
        binding: GPUPreparedTextRenderBinding,
        expectedLayout: GPUPreparedTextVertexLayout,
        sealedLayout: GPUPreparedTextVertexLayout,
        render: GPUFrameStep.RenderPassStep,
        preparations: List<GPUResourcePreparationRequest>,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val layout = binding.compositeProgram.vertexLayout
        if (layout != expectedLayout ||
            layout != sealedLayout ||
            layout != PreparedTextA8Shader.VertexLayout ||
            layout.arrayStrideBytes != 64L ||
            layout.stepMode != "Instance" ||
            layout.attributes.map { attribute ->
                Triple(attribute.location, attribute.offsetBytes, attribute.format)
            } != listOf(
                Triple(0, 0L, "Float32x2"),
                Triple(1, 8L, "Float32x2"),
                Triple(2, 16L, "Float32x2"),
                Triple(3, 24L, "Float32x2"),
                Triple(4, 32L, "Float32x4"),
            )
        ) {
            return vertexRefusal(
                "Prepared TextA8 requires the exact 64-byte instanced vertex ABI.",
            )
        }
        val plan = binding.instanceBufferPlan
        val rangeEnd = checkedAdd(binding.firstInstance.toLong(), binding.instanceCount.toLong())
            ?: return vertexRefusal("Prepared TextA8 instance range overflowed.")
        val firstByte = checkedMultiply(binding.firstInstance.toLong(), layout.arrayStrideBytes)
            ?: return vertexRefusal("Prepared TextA8 first-instance byte offset overflowed.")
        val rangeBytes = checkedMultiply(binding.instanceCount.toLong(), layout.arrayStrideBytes)
            ?: return vertexRefusal("Prepared TextA8 instance byte range overflowed.")
        val byteEnd = checkedAdd(firstByte, rangeBytes)
            ?: return vertexRefusal("Prepared TextA8 instance byte end overflowed.")
        val expectedPlanBytes = checkedMultiply(
            plan.instanceCount.toLong(),
            layout.arrayStrideBytes,
        )
        if (plan.strideBytes.toLong() != layout.arrayStrideBytes ||
            expectedPlanBytes == null ||
            plan.byteSize != expectedPlanBytes ||
            binding.firstInstance != binding.preflightSeal.firstInstance ||
            binding.instanceCount != binding.preflightSeal.instanceCount ||
            rangeEnd > plan.instanceCount.toLong() ||
            byteEnd > plan.byteSize
        ) {
            return vertexRefusal(
                "Prepared TextA8 instance buffer, firstInstance, and byte ranges diverged.",
            )
        }
        val preparation = preparations.singleOrNull { request ->
            request.resource == plan.bufferRef
        }
        val descriptor = preparation?.descriptor as? GPUFrameBufferDescriptor
        val use = render.resourceUses.singleOrNull { candidate ->
            candidate.resource == plan.bufferRef
        }
        if (descriptor?.byteSize != plan.byteSize ||
            descriptor.alignmentBytes != plan.alignmentBytes.toLong() ||
            preparation.role != GPUFrameResourceRole.VertexData ||
            preparation.usages != setOf(
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceUsage.CopyDestination,
            ) ||
            preparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            preparation.byteSize != plan.byteSize ||
            use?.role != GPUFrameResourceRole.VertexData ||
            use.usage != GPUFrameResourceUsage.Vertex ||
            use.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            use.write
        ) {
            return vertexRefusal(
                "Prepared TextA8 instance buffer ownership and render operand are not exact.",
            )
        }
        return null
    }

    private fun validateDrawUniform(
        binding: GPUPreparedTextRenderBinding,
        semantic: GPUDrawSemanticPayload.TextA8,
        capabilities: GPUCapabilities,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val plan = binding.drawUniformBufferPlan
        val slice = binding.drawUniformSlice
        val seal = requireNotNull(binding.preflightSeal.textA8Composite)
        val limits = capabilities.limits
            ?: return drawUniformRefusal(
                "Prepared TextA8 draw uniforms require observed device limits.",
            )
        val bytes = plan.bytesForUpload()
        val stride = checkedAlignUp(
            PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
            plan.alignmentBytes,
        )
        val expectedBytes = stride?.let { checkedMultiply(it, plan.slices.size.toLong()) }
        if (plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            plan.logicalSliceSizeBytes != PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES ||
            stride == null ||
            expectedBytes == null ||
            plan.byteSize != expectedBytes ||
            plan.byteSize != bytes.size.toLong() ||
            limits.maxBufferSize?.let { plan.byteSize > it } == true ||
            plan.contentHash != bytes.preparedTextSha256() ||
            plan.bufferRef != seal.drawUniformBufferRef ||
            plan.alignmentBytes != seal.drawUniformAlignmentBytes ||
            plan.logicalSliceSizeBytes != seal.drawUniformLogicalSliceSizeBytes ||
            plan.byteSize != seal.drawUniformBufferByteSize ||
            plan.contentHash != seal.drawUniformBufferContentHash ||
            slice != seal.drawUniformSlice ||
            slice != plan.slices.singleOrNull { candidate ->
                candidate.packetId == binding.packetId
            } ||
            slice.packetId != binding.packetId
        ) {
            return drawUniformRefusal(
                "Prepared TextA8 draw-uniform plan, slice, limit, hash, or seal changed.",
            )
        }
        plan.slices.forEachIndexed { index, candidate ->
            val expectedOffset = checkedMultiply(stride, index.toLong())
                ?: return drawUniformRefusal(
                    "Prepared TextA8 draw-uniform slice offset overflowed.",
                )
            val logicalEnd = checkedAdd(candidate.offsetBytes, candidate.sizeBytes)
                ?: return drawUniformRefusal(
                    "Prepared TextA8 draw-uniform slice range overflowed.",
                )
            val strideEnd = checkedAdd(candidate.offsetBytes, stride)
                ?: return drawUniformRefusal(
                    "Prepared TextA8 draw-uniform stride range overflowed.",
                )
            if (candidate.offsetBytes != expectedOffset ||
                candidate.offsetBytes % plan.alignmentBytes != 0L ||
                candidate.sizeBytes != PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES ||
                logicalEnd > plan.byteSize ||
                strideEnd > plan.byteSize ||
                candidate.contentHash != bytes.copyOfRange(
                    candidate.offsetBytes.toInt(),
                    logicalEnd.toInt(),
                ).preparedTextSha256() ||
                (logicalEnd.toInt() until strideEnd.toInt()).any { paddingIndex ->
                    bytes[paddingIndex] != 0.toByte()
                }
            ) {
                return drawUniformRefusal(
                    "Prepared TextA8 draw-uniform slices are not one canonical padded slab.",
                )
            }
        }
        val sliceEnd = checkedAdd(slice.offsetBytes, slice.sizeBytes)
            ?: return drawUniformRefusal(
                "Prepared TextA8 selected draw-uniform range overflowed.",
            )
        if (sliceEnd > plan.byteSize ||
            slice.offsetBytes > Int.MAX_VALUE.toLong() ||
            sliceEnd > Int.MAX_VALUE.toLong()
        ) {
            return drawUniformRefusal(
                "Prepared TextA8 selected draw-uniform range exceeds its buffer.",
            )
        }
        val target = semantic.targetBounds
        if (target.left != 0 || target.top != 0 || target.width <= 0 || target.height <= 0) {
            return drawUniformRefusal(
                "Prepared TextA8 draw uniforms require an origin-zero positive target.",
            )
        }
        val expectedBits = listOf(
            target.width.toFloat().toRawBits(),
            target.height.toFloat().toRawBits(),
            semantic.material.paintAlpha.toRawBits(),
            0f.toRawBits(),
        ) + semantic.deviceToLocal.rawBits().let { affine ->
            listOf(
                affine[0],
                affine[1],
                affine[2],
                0f.toRawBits(),
                affine[3],
                affine[4],
                affine[5],
                0f.toRawBits(),
            )
        }
        val actualBits = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .position(slice.offsetBytes.toInt())
            .let { buffer ->
                List(expectedBits.size) { buffer.int }
            }
        if (actualBits != expectedBits) {
            return drawUniformRefusal(
                "Prepared TextA8 target, paintAlpha, or device-to-local bits changed.",
            )
        }
        return null
    }

    private fun validateDrawUniformTopology(
        binding: GPUPreparedTextRenderBinding,
        framePlan: GPUFramePlan,
        render: GPUFrameStep.RenderPassStep,
        renderSourceStepIndex: Int,
        preparations: List<GPUResourcePreparationRequest>,
    ): GPUPreparedTextCompositePreflightRefusal? {
        val plan = binding.drawUniformBufferPlan
        val preparationMatches = preparations.filter { request ->
            request.resource == plan.bufferRef
        }
        val preparation = preparationMatches.singleOrNull()
        val descriptor = preparation?.descriptor as? GPUFrameBufferDescriptor
        val uses = render.resourceUses.filter { use -> use.resource == plan.bufferRef }
        val use = uses.singleOrNull()
        val expectedRef =
            "buffer.prepared-text.draw-uniforms:${framePlan.frameId.value}:${plan.contentHash}"
        val aliasedBufferRefs = buildList {
            add(binding.instanceBufferPlan.bufferRef)
            binding.materialUniformBufferPlan?.let { add(it.bufferRef) }
            add(binding.atlasResourcePlan.stagingRef)
            binding.materialSampledResourcePlans.forEach { resource ->
                add(resource.stagingRef)
            }
        }
        if (plan.bufferRef.value != expectedRef ||
            aliasedBufferRefs.any { alias -> alias == plan.bufferRef } ||
            descriptor?.byteSize != plan.byteSize ||
            descriptor.alignmentBytes != plan.alignmentBytes ||
            preparation.role != GPUFrameResourceRole.UniformData ||
            preparation.usages != setOf(
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceUsage.CopyDestination,
            ) ||
            preparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            preparation.byteSize != plan.byteSize ||
            use?.role != GPUFrameResourceRole.UniformData ||
            use.usage != GPUFrameResourceUsage.Uniform ||
            use.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            use.write ||
            uses.size != 1
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 draw-uniform allocation, ownership, aliasing, or operand changed.",
            )
        }
        val prepareSteps = framePlan.steps.mapIndexedNotNull { index, step ->
            (step as? GPUFrameStep.PrepareResourcesStep)?.let { index to it }
        }
        val prepareStep = prepareSteps.singleOrNull()
        val prepareTaskId = prepareStep?.second?.sourceTaskIds?.singleOrNull()
        val renderTaskId = render.sourceTaskIds.singleOrNull()
        val dependencyCount = if (prepareTaskId == null || renderTaskId == null) {
            0
        } else {
            framePlan.dependencies.count { dependency ->
                dependency.fromTaskId == prepareTaskId &&
                    dependency.toTaskId == renderTaskId &&
                    dependency.dependencyKind == "prepared-surface-resource-order" &&
                    dependency.reasonCode == "prepared.surface.prepare-before-consumer"
            }
        }
        val aliasesUpload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .any { upload ->
                upload.staging == plan.bufferRef || upload.destination == plan.bufferRef
            }
        val allocationLabel = "prepared-text.draw-uniforms.${plan.contentHash}"
        val allocations = framePlan.memoryBudget.allocations.filter { allocation ->
            allocation.label == allocationLabel
        }
        if (prepareStep == null ||
            prepareStep.first >= renderSourceStepIndex ||
            dependencyCount != 1 ||
            aliasesUpload ||
            allocations.size != 1 ||
            allocations.single().category != GPUFrameMemoryCategory.ReusableScratch ||
            allocations.single().bytes != plan.byteSize ||
            allocations.single().resourceKind != GPUFrameMemoryResourceKind.Buffer ||
            allocations.single().extent != null
        ) {
            return bindingLayoutRefusal(
                "Prepared TextA8 draw-uniform allocation and prepare-before-consumer topology changed.",
            )
        }
        return null
    }

    private fun GPUPreparedMaterialFragment.matches(
        expected: GPUPreparedMaterialFragment,
    ): Boolean =
        declarationsWgsl == expected.declarationsWgsl &&
            evaluationFunctionWgsl == expected.evaluationFunctionWgsl &&
            uniformBinding == expected.uniformBinding &&
            sampledBindings == expected.sampledBindings &&
            fragmentHash == expected.fragmentHash &&
            abiHash == expected.abiHash &&
            colorContract == expected.colorContract &&
            coordinateContract == expected.coordinateContract

    private fun checkedAdd(left: Long, right: Long): Long? =
        try {
            Math.addExact(left, right)
        } catch (_: ArithmeticException) {
            null
        }

    private fun checkedMultiply(left: Long, right: Long): Long? =
        try {
            Math.multiplyExact(left, right)
        } catch (_: ArithmeticException) {
            null
        }

    private fun checkedAlignUp(value: Long, alignment: Long): Long? {
        if (alignment <= 0L || alignment and (alignment - 1L) != 0L) return null
        val remainder = value % alignment
        return if (remainder == 0L) value else checkedAdd(value, alignment - remainder)
    }

    private fun sourceRefusal(message: String) =
        GPUPreparedTextCompositePreflightRefusal(
            GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_SOURCE,
            message,
        )

    private fun abiRefusal(message: String) =
        GPUPreparedTextCompositePreflightRefusal(
            GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
            message,
        )

    private fun vertexRefusal(message: String) =
        GPUPreparedTextCompositePreflightRefusal(
            GPUPreparedTextCompositePreflightRefusalCodes.INSTANCE_VERTEX_ABI,
            message,
        )

    private fun drawUniformRefusal(message: String) =
        GPUPreparedTextCompositePreflightRefusal(
            GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
            message,
        )

    private fun bindingLayoutRefusal(message: String) =
        GPUPreparedTextCompositePreflightRefusal(
            GPUPreparedTextCompositePreflightRefusalCodes.BINDING_LAYOUT,
            message,
        )
}
