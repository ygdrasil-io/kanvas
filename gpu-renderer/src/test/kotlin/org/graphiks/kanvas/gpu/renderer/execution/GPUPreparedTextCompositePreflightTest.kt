package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextCompositePreflightRefusalCodes
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexAttribute
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout

class GPUPreparedTextCompositePreflightTest {
    @Test
    fun `accepted frame publishes one canonical draw uniform render operand`() {
        val fixture = preparedTextNativePreflightFixture()
        val binding = fixture.framePlan.textA8Bindings().first()
        val render = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .first { binding.packetId in it.preparedTextBindingsByPacketId }

        assertEquals(
            1,
            render.resourceUses.count { use ->
                use.resource == binding.drawUniformBufferPlan.bufferRef
            },
        )
    }

    @Test
    fun `vertex refusal precedes source refusal`() {
        val fixture = preparedTextNativePreflightFixture()
        val binding = fixture.framePlan.textA8Bindings().first()
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(
                sourceHash = "0".repeat(64),
                vertexLayout = binding.compositeProgram.vertexLayout.copyWith(
                    arrayStrideBytes = 68L,
                ),
            ),
        )
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture),
        )

        assertEquals(
            GPUPreparedTextCompositePreflightRefusalCodes.INSTANCE_VERTEX_ABI,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.nativePreparationEvents)
        assertEquals(0, probe.materializerInvocations)
        assertEquals(0L, probe.nativePayloadRegistrations)
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `binding refusal precedes pipeline key refusal`() {
        val fixture = preparedTextNativePreflightFixture()
        val binding = fixture.framePlan.textA8Bindings().first()
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(
                bindingPlan = binding.compositeProgram.bindingPlan.copy(
                    drawUniformBinding = 7,
                ),
                pipelineKey = "2".repeat(64),
            ),
        )
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture),
        )

        assertEquals(
            GPUPreparedTextCompositePreflightRefusalCodes.BINDING_LAYOUT,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.nativePreparationEvents)
        assertEquals(0, probe.materializerInvocations)
        assertEquals(0L, probe.nativePayloadRegistrations)
        assertEquals(0, probe.totalCreations)
    }

    @TestFactory
    fun `every composite mutation refuses before Task 10 and native work`(): List<DynamicTest> =
        compositeMutations.map { mutation ->
            DynamicTest.dynamicTest(mutation.name) {
                val fixture = preparedTextNativePreflightFixture()
                mutation.mutate(fixture.framePlan.textA8Bindings().first())
                val probe = GPUPreparedTextNativeCreationProbe()

                val refused = assertIs<GPUFramePreflightResult.Refused>(
                    probe.preflight(fixture),
                )

                assertEquals(mutation.expectedCode, refused.diagnostic.code.value)
                assertEquals(0, probe.nativePreparationEvents)
                assertEquals(0, probe.materializerInvocations)
                assertEquals(0L, probe.nativePayloadRegistrations)
                assertEquals(0, probe.totalCreations)
            }
        }

    @TestFactory
    fun `every draw uniform topology mutation refuses without native work`(): List<DynamicTest> =
        drawUniformTopologyMutations.map { mutation ->
            DynamicTest.dynamicTest(mutation.name) {
                val fixture = preparedTextNativePreflightFixture()
                mutation.mutate(fixture)
                val probe = GPUPreparedTextNativeCreationProbe()

                val refused = assertIs<GPUFramePreflightResult.Refused>(
                    probe.preflight(fixture),
                )

                assertEquals(
                    GPUPreparedTextCompositePreflightRefusalCodes.BINDING_LAYOUT,
                    refused.diagnostic.code.value,
                )
                assertEquals(0, probe.nativePreparationEvents)
                assertEquals(0, probe.materializerInvocations)
                assertEquals(0L, probe.nativePayloadRegistrations)
                assertEquals(0, probe.totalCreations)
            }
        }
}

private data class CompositeMutation(
    val name: String,
    val expectedCode: String,
    val mutate: (GPUPreparedTextRenderBinding) -> Unit,
)

private data class DrawUniformTopologyMutation(
    val name: String,
    val mutate: (PreparedTextNativePreflightFixture) -> Unit,
)

private val compositeMutations = listOf(
    CompositeMutation(
        name = "composite source hash",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_SOURCE,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(sourceHash = "0".repeat(64)),
        )
    },
    CompositeMutation(
        name = "composite WGSL source",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_SOURCE,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(
                wgslSource = binding.compositeProgram.wgslSource + "\n",
            ),
        )
    },
    CompositeMutation(
        name = "fragment entry point",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(fragmentEntryPoint = "fs_forged"),
        )
    },
    CompositeMutation(
        name = "reflected draw binding",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.BINDING_LAYOUT,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(
                bindingPlan = binding.compositeProgram.bindingPlan.copy(
                    drawUniformBinding = 7,
                ),
            ),
        )
    },
    CompositeMutation(
        name = "vertex stride",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.INSTANCE_VERTEX_ABI,
    ) { binding ->
        binding.replaceVertexLayout(
            binding.compositeProgram.vertexLayout.copyWith(arrayStrideBytes = 68L),
        )
    },
    CompositeMutation(
        name = "vertex step mode",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.INSTANCE_VERTEX_ABI,
    ) { binding ->
        binding.replaceVertexLayout(
            binding.compositeProgram.vertexLayout.copyWith(stepMode = "Vertex"),
        )
    },
    CompositeMutation(
        name = "vertex attribute offset",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.INSTANCE_VERTEX_ABI,
    ) { binding ->
        binding.replaceVertexLayout(
            binding.compositeProgram.vertexLayout.copyWith(
                attributes = binding.compositeProgram.vertexLayout.attributes.map { attribute ->
                    if (attribute.location == 4) {
                        attribute.copy(offsetBytes = 36L)
                    } else {
                        attribute
                    }
                },
            ),
        )
    },
    CompositeMutation(
        name = "draw uniform alignment",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformBufferPlan.setPrivateField("alignmentBytes", 128L)
    },
    CompositeMutation(
        name = "draw uniform range",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformSlice.setPrivateField(
            "offsetBytes",
            binding.drawUniformSlice.offsetBytes + 1L,
        )
    },
    CompositeMutation(
        name = "draw uniform content",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformBytes()[binding.drawUniformSlice.offsetBytes.toInt() + 4] =
            0x5a.toByte()
    },
    CompositeMutation(
        name = "target size bits",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformFloats().putFloat(0, 31f)
    },
    CompositeMutation(
        name = "paint alpha bits",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformFloats().putFloat(8, 0.375f)
    },
    CompositeMutation(
        name = "device to local bits",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.DRAW_UNIFORM,
    ) { binding ->
        binding.drawUniformFloats().putFloat(16, 2f)
    },
    CompositeMutation(
        name = "composite ABI hash",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(abiHash = "1".repeat(64)),
        )
    },
    CompositeMutation(
        name = "pipeline key",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(pipelineKey = "2".repeat(64)),
        )
    },
    CompositeMutation(
        name = "native target format class",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(targetFormatClass = "rgba8unorm"),
        )
    },
    CompositeMutation(
        name = "native blend plan identity",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(blendPlanIdentity = "forged:src"),
        )
    },
    CompositeMutation(
        name = "native fixed-function blend state",
        expectedCode = GPUPreparedTextCompositePreflightRefusalCodes.COMPOSITE_ABI,
    ) { binding ->
        binding.replaceCompositeProgram(
            binding.compositeProgram.copy(
                fixedFunctionBlendState = preparedTextBlendState(GPUBlendMode.SRC),
            ),
        )
    },
)

private val drawUniformTopologyMutations = listOf(
    DrawUniformTopologyMutation("missing render operand") { fixture ->
        val binding = fixture.framePlan.textA8Bindings().first()
        val render = fixture.framePlan.textA8Render(binding)
        render.setPrivateField(
            "resourceUses",
            render.resourceUses.filterNot { use ->
                use.resource == binding.drawUniformBufferPlan.bufferRef
            },
        )
    },
    DrawUniformTopologyMutation("duplicate render operand") { fixture ->
        val binding = fixture.framePlan.textA8Bindings().first()
        val render = fixture.framePlan.textA8Render(binding)
        val use = render.resourceUses.single { candidate ->
            candidate.resource == binding.drawUniformBufferPlan.bufferRef
        }
        render.setPrivateField("resourceUses", render.resourceUses + use)
    },
    DrawUniformTopologyMutation("render operand role") { fixture ->
        val binding = fixture.framePlan.textA8Bindings().first()
        val render = fixture.framePlan.textA8Render(binding)
        render.setPrivateField(
            "resourceUses",
            render.resourceUses.map { use ->
                if (use.resource == binding.drawUniformBufferPlan.bufferRef) {
                    use.copy(role = GPUFrameResourceRole.StorageData)
                } else {
                    use
                }
            },
        )
    },
    DrawUniformTopologyMutation("render operand order") { fixture ->
        val binding = fixture.framePlan.textA8Bindings().first()
        val render = fixture.framePlan.textA8Render(binding)
        val use = render.resourceUses.single { candidate ->
            candidate.resource == binding.drawUniformBufferPlan.bufferRef
        }
        render.setPrivateField(
            "resourceUses",
            render.resourceUses.filterNot { candidate -> candidate == use } + use,
        )
    },
    DrawUniformTopologyMutation("preparation usage") { fixture ->
        fixture.drawUniformPreparation().setPrivateField(
            "usages",
            setOf(GPUFrameResourceUsage.CopyDestination),
        )
    },
    DrawUniformTopologyMutation("preparation ownership") { fixture ->
        fixture.drawUniformPreparation().setPrivateField(
            "lifetime",
            GPUFrameResourceLifetime.SharedCache,
        )
    },
    DrawUniformTopologyMutation("preparation descriptor") { fixture ->
        val request = fixture.drawUniformPreparation()
        val descriptor = request.descriptor as GPUFrameBufferDescriptor
        request.setPrivateField(
            "descriptor",
            descriptor.copy(alignmentBytes = descriptor.alignmentBytes * 2L),
        )
    },
    DrawUniformTopologyMutation("missing preparation") { fixture ->
        val ref = fixture.framePlan.textA8Bindings().first().drawUniformBufferPlan.bufferRef
        val step = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .single()
        step.setPrivateField(
            "requests",
            step.requests.filterNot { request -> request.resource == ref },
        )
    },
    DrawUniformTopologyMutation("duplicate preparation") { fixture ->
        val step = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .single()
        step.setPrivateField("requests", step.requests + fixture.drawUniformPreparation())
    },
    DrawUniformTopologyMutation("late upload alias") { fixture ->
        val ref = fixture.framePlan.textA8Bindings().first().drawUniformBufferPlan.bufferRef
        fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .first()
            .setPrivateField("staging", ref.value)
    },
    DrawUniformTopologyMutation("missing allocation") { fixture ->
        val plan = fixture.framePlan.textA8Bindings().first().drawUniformBufferPlan
        fixture.framePlan.memoryBudget.setPrivateField(
            "allocations",
            fixture.framePlan.memoryBudget.allocations.filterNot { allocation ->
                allocation.label == "prepared-text.draw-uniforms.${plan.contentHash}"
            },
        )
    },
)

private fun GPUPreparedTextRenderBinding.replaceCompositeProgram(
    program: org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram,
) {
    setPrivateField("compositeProgramOrNull", program)
}

private fun GPUPreparedTextRenderBinding.replaceVertexLayout(
    layout: GPUPreparedTextVertexLayout,
) {
    replaceCompositeProgram(compositeProgram.copy(vertexLayout = layout))
}

private fun GPUPreparedTextVertexLayout.copyWith(
    arrayStrideBytes: Long = this.arrayStrideBytes,
    stepMode: String = this.stepMode,
    attributes: List<GPUPreparedTextVertexAttribute> = this.attributes,
): GPUPreparedTextVertexLayout = GPUPreparedTextVertexLayout(
    arrayStrideBytes = arrayStrideBytes,
    stepMode = stepMode,
    attributes = attributes,
)

private fun GPUPreparedTextRenderBinding.drawUniformBytes(): ByteArray =
    drawUniformBufferPlan.privateField("uploadSnapshot")

private fun GPUPreparedTextRenderBinding.drawUniformFloats(): ByteBuffer =
    ByteBuffer.wrap(drawUniformBytes())
        .order(ByteOrder.LITTLE_ENDIAN)
        .position(drawUniformSlice.offsetBytes.toInt())
        .slice()
        .order(ByteOrder.LITTLE_ENDIAN)

private inline fun <reified T> Any.privateField(name: String): T =
    javaClass.getDeclaredField(name).run {
        isAccessible = true
        @Suppress("UNCHECKED_CAST")
        get(this@privateField) as T
    }

private fun Any.setPrivateField(name: String, value: Any) {
    javaClass.getDeclaredField(name).run {
        isAccessible = true
        set(this@setPrivateField, value)
    }
}

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan.textA8Bindings():
    List<GPUPreparedTextRenderBinding> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap { step ->
            step.drawPackets.mapNotNull { packet ->
                if (packet.semanticPayload is
                    org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.TextA8
                ) {
                    step.preparedTextBindingsByPacketId.getValue(packet.packetId)
                } else {
                    null
                }
            }
        }

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan.textA8Render(
    binding: GPUPreparedTextRenderBinding,
): GPUFrameStep.RenderPassStep =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .single { render -> binding.packetId in render.preparedTextBindingsByPacketId }

private fun PreparedTextNativePreflightFixture.drawUniformPreparation():
    org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest {
    val ref = framePlan.textA8Bindings().first().drawUniformBufferPlan.bufferRef
    return framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        .single { request -> request.resource == ref }
}
