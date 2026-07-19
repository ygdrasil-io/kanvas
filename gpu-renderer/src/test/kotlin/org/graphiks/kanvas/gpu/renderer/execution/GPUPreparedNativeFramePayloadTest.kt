package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID

class GPUPreparedNativeFramePayloadTest {
    @Test
    fun `coverage mask consumer pipeline factory accepts only exact consumer acquisition`() {
        val generation = GPUDeviceGenerationID(7)
        acquiredPipelineFixture(
            generation = generation,
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
            program = GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        ).use { fixture ->
            val operand = GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                acquired = fixture.acquired,
                deviceGeneration = generation,
                uniformAlignmentBytes = 256L,
            )

            assertEquals(GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired, operand.bindingPolicy)
        }
    }

    @Test
    fun `coverage mask consumer pipeline factory refuses non consumer acquisition`() {
        val generation = GPUDeviceGenerationID(7)
        acquiredPipelineFixture(
            generation = generation,
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
            program = GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
        ).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                    acquired = fixture.acquired,
                    deviceGeneration = generation,
                    uniformAlignmentBytes = 256L,
                )
            }
        }
    }

    @Test
    fun `coverage mask consumer pipeline factory refuses non positive uniform alignment`() {
        val generation = GPUDeviceGenerationID(7)
        acquiredPipelineFixture(
            generation = generation,
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
            program = GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        ).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                    acquired = fixture.acquired,
                    deviceGeneration = generation,
                    uniformAlignmentBytes = 0L,
                )
            }
        }
    }

    @Test
    fun `indexed draw binding requirement follows the acquired pipeline policy`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        noBindingsPipelineFixture(generation).use { fixture ->
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(fixture.operand),
                    vertexCommand(vertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
            )
        }

        val requiredPipeline = pipelineOperand("consumer", generation)
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(requiredPipeline),
                    vertexCommand(vertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
            )
        }
    }

    @Test
    fun `acquired no bindings pipeline refuses a bind group command`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        noBindingsPipelineFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = 2,
                    pass = GPUPreparedNativeRenderPassConfig(target),
                    commands = listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(fixture.operand),
                        GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroupOperand("unexpected", generation)),
                        vertexCommand(bufferOperand("vertices", generation)),
                        indexCommand(bufferOperand("indices", generation)),
                        GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                        drawCommand(),
                    ),
                )
            }
        }
    }

    @Test
    fun `command order remains the default direct render operand layout`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipeline = pipelineOperand("direct", generation)
        val bindGroup = bindGroupOperand("direct", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
        )

        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = GPUPreparedNativeRenderPassConfig(target),
            commands = commands,
        )

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(target, pipeline, bindGroup, vertex, index),
            render.operands,
        )
    }

    @Test
    fun `full target indexed core layout accepts draw indexed without scissor`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            fullTargetRender(
                pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                commands = fullTargetCommands(fixture),
            )
        }
    }

    @Test
    fun `full target indexed core layout fixes structural operand order`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val render = fullTargetRender(
                pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                commands = fullTargetCommands(fixture),
            )

            assertOperandIdentities(
                listOf(fixture.target, fixture.pipeline, fixture.vertex, fixture.index, fixture.bindGroup),
                render.operands,
            )
        }
    }

    @Test
    fun `full target indexed core layout refuses every non canonical command permutation`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val canonicalCommands = fullTargetCommands(fixture)
            val nonCanonicalPermutations = permutations(canonicalCommands)
                .filterNot { it == canonicalCommands }

            assertEquals(119, nonCanonicalPermutations.size)
            nonCanonicalPermutations.forEachIndexed { index, commands ->
                assertFailsWith<IllegalArgumentException>("permutation=$index") {
                    fullTargetRender(
                        pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                        commands = commands,
                    )
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a resolve target`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = fixture.target,
                        resolveTarget = textureViewOperand("resolve", generation),
                    ),
                    commands = fullTargetCommands(fixture),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a depth stencil target`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = fixture.target,
                        depthStencilTarget = textureViewOperand("depth-stencil", generation),
                    ),
                    commands = fullTargetCommands(fixture),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses an additional pipeline`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture)
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = commands +
                        commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().single(),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses an additional bind group`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture)
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = commands +
                        commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>().single(),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses an additional indexed draw`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture) + drawCommand(),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a non indexed draw`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture).dropLast(1) +
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(vertexCount = 3))
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = commands,
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a stencil reference`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture) +
                        GPUPreparedNativeRenderCommand.SetStencilReference(0u),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses every missing structural command`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture)
            val pass = GPUPreparedNativeRenderPassConfig(fixture.target)
            commands.indices.forEach { missingIndex ->
                assertFailsWith<IllegalArgumentException>("missingIndex=$missingIndex") {
                    fullTargetRender(
                        pass = pass,
                        commands = commands.filterIndexed { index, _ -> index != missingIndex },
                    )
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses duplicated geometry commands`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture)
            val pass = GPUPreparedNativeRenderPassConfig(fixture.target)
            commands.filter {
                it is GPUPreparedNativeRenderCommand.SetVertexBuffer ||
                    it is GPUPreparedNativeRenderCommand.SetIndexBuffer
            }.forEach { duplicate ->
                assertFailsWith<IllegalArgumentException>(duplicate::class.simpleName) {
                    fullTargetRender(pass, commands + duplicate)
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses set scissor at every command position`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val commands = fullTargetCommands(fixture)
            (0..commands.size).forEach { position ->
                val commandsWithScissor = commands.toMutableList().apply {
                    add(position, GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8))
                }
                val failure = assertFailsWith<IllegalArgumentException>("position=$position") {
                    fullTargetRender(
                        pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                        commands = commandsWithScissor,
                    )
                }
                assertEquals(
                    "Full-target indexed CorePrimitive render layout forbids SetScissor",
                    failure.message,
                    "position=$position",
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a generic binding pipeline`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(
                        fixture,
                        pipeline = pipelineOperand("generic", generation),
                    ),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a no bindings pipeline`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            noBindingsPipelineFixture(generation).use { noBindings ->
                assertFailsWith<IllegalArgumentException> {
                    fullTargetRender(
                        pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                        commands = fullTargetCommands(fixture, pipeline = noBindings.operand),
                    )
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout refuses a general consumer acquisition authority`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            acquiredPipelineFixture(
                generation,
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
            ).use { acquired ->
                val generalOperand = GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
                    acquired.acquired,
                    generation,
                )
                assertFailsWith<IllegalArgumentException> {
                    fullTargetRender(
                        pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                        commands = fullTargetCommands(fixture, pipeline = generalOperand),
                    )
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout requires bind group index zero`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture, bindGroupIndex = 1),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires exactly one dynamic offset`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            listOf(emptyList(), listOf(0L, 256L)).forEach { offsets ->
                assertFailsWith<IllegalArgumentException>("offsets=$offsets") {
                    fullTargetRender(
                        pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                        commands = fullTargetCommands(fixture, dynamicOffsets = offsets),
                    )
                }
            }
        }
    }

    @Test
    fun `full target indexed core layout requires an aligned dynamic offset`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture, dynamicOffsets = listOf(128L)),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires a borrowed target`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val ownedTarget = GPUPreparedNativeTextureViewOperand(
                fakeNative<GPUTextureView>("owned-target"),
                generation,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(ownedTarget),
                    commands = fullTargetCommands(fixture),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires a borrowed pipeline`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(
            generation,
            pipelineOwnership = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        ).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires a borrowed vertex buffer`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val ownedVertex = GPUPreparedNativeBufferOperand(
                fakeNative<GPUBuffer>("owned-vertices"),
                generation,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                byteCapacity = 256L,
            )
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture, vertex = ownedVertex),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires a borrowed index buffer`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val ownedIndex = GPUPreparedNativeBufferOperand(
                fakeNative<GPUBuffer>("owned-indices"),
                generation,
                GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                byteCapacity = 256L,
            )
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture, index = ownedIndex),
                )
            }
        }
    }

    @Test
    fun `full target indexed core layout requires a borrowed bind group`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            val ownedBindGroup = GPUPreparedNativeBindGroupOperand(
                fakeNative<GPUBindGroup>("owned-bind-group"),
                generation,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
            assertFailsWith<IllegalArgumentException> {
                fullTargetRender(
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture, bindGroup = ownedBindGroup),
                )
            }
        }
    }

    @Test
    fun `legacy indexed core layout still requires scissor before draw indexed`() {
        val generation = GPUDeviceGenerationID(7)
        fullTargetFixture(generation).use { fixture ->
            assertFailsWith<IllegalArgumentException> {
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = 2,
                    pass = GPUPreparedNativeRenderPassConfig(fixture.target),
                    commands = fullTargetCommands(fixture),
                    operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
                )
            }
        }
    }

    @Test
    fun `indexed core path layout matches path only C3 native operand keys`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val depthStencil = textureViewOperand("depth-stencil", generation)
        val producerPipeline = pipelineOperand("producer", generation)
        val coverPipeline = pipelineOperand("cover", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val producerBindGroup = bindGroupOperand("producer", generation)
        val coverBindGroup = bindGroupOperand("cover", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(producerPipeline),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, producerBindGroup, listOf(0L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(coverPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, coverBindGroup, listOf(256L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
        )
        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = pathPass(target, depthStencil),
            commands = commands,
            operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
        )
        val keys = listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "target"),
            key(
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandKind.TextureView,
                "depth-stencil",
            ),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "producer"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "cover"),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "vertices"),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "indices"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "producer"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "cover"),
        )

        val payload = payload(render, keys, generation)

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(
                target,
                depthStencil,
                producerPipeline,
                coverPipeline,
                vertex,
                index,
                producerBindGroup,
                coverBindGroup,
            ),
            render.operands,
        )
        assertSame(render, payload.scopeOperands.single())
        assertEquals(keys, payload.scopeOperandKeys.single())
    }

    @Test
    fun `indexed core mixed layout groups pipelines by native identity and keeps bind groups in draw order`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val depthStencil = textureViewOperand("depth-stencil", generation)
        val directPipelineHandle = fakeNative<GPURenderPipeline>("direct")
        val firstDirectPipeline = GPUPreparedNativeRenderPipelineOperand(directPipelineHandle, generation)
        val repeatedDirectPipeline = GPUPreparedNativeRenderPipelineOperand(directPipelineHandle, generation)
        val producerPipeline = pipelineOperand("producer", generation)
        val coverPipeline = pipelineOperand("cover", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val directFirstBindGroup = bindGroupOperand("direct-first", generation)
        val producerBindGroup = bindGroupOperand("producer", generation)
        val coverBindGroup = bindGroupOperand("cover", generation)
        val directLastBindGroup = bindGroupOperand("direct-last", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(firstDirectPipeline),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, directFirstBindGroup, listOf(0L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(producerPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, producerBindGroup, listOf(256L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(coverPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, coverBindGroup, listOf(512L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(repeatedDirectPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, directLastBindGroup, listOf(768L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
        )
        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = pathPass(target, depthStencil),
            commands = commands,
            operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
        )
        val keys = listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "target"),
            key(
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandKind.TextureView,
                "depth-stencil",
            ),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "direct"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "producer"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "cover"),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "vertices"),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "indices"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "direct-first"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "producer"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "cover"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "direct-last"),
        )

        val payload = payload(render, keys, generation)

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(
                target,
                depthStencil,
                firstDirectPipeline,
                producerPipeline,
                coverPipeline,
                vertex,
                index,
                directFirstBindGroup,
                producerBindGroup,
                coverBindGroup,
                directLastBindGroup,
            ),
            render.operands,
        )
        assertSame(render, payload.scopeOperands.single())
        assertEquals(keys, payload.scopeOperandKeys.single())
    }

    @Test
    fun `indexed core layout refuses ambiguous shared geometry commands`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipeline = pipelineOperand("pipeline", generation)
        val bindGroup = bindGroupOperand("bind-group", generation)
        val vertex = bufferOperand("vertices", generation)
        val otherVertex = bufferOperand("other-vertices", generation)
        val index = bufferOperand("indices", generation)
        val otherIndex = bufferOperand("other-indices", generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                    vertexCommand(vertex),
                    vertexCommand(otherVertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                    vertexCommand(vertex),
                    indexCommand(index),
                    indexCommand(otherIndex),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
    }

    @Test
    fun `indexed core layout refuses ambiguous metadata for one pipeline native identity`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipelineHandle = fakeNative<GPURenderPipeline>("pipeline")
        val borrowedPipeline = GPUPreparedNativeRenderPipelineOperand(pipelineHandle, generation)
        val completionOwnedPipeline = GPUPreparedNativeRenderPipelineOperand(
            pipelineHandle,
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val bindGroup = bindGroupOperand("bind-group", generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(borrowedPipeline),
                    vertexCommand(vertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                    GPUPreparedNativeRenderCommand.SetPipeline(completionOwnedPipeline),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
    }

    private fun pathPass(
        target: GPUPreparedNativeTextureViewOperand,
        depthStencil: GPUPreparedNativeTextureViewOperand,
    ) = GPUPreparedNativeRenderPassConfig(
        colorTarget = target,
        depthStencilTarget = depthStencil,
        depthReadOnly = true,
        stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
        stencilStoreOperation = GPUPreparedNativeStoreOperation.Discard,
        stencilClearValue = 0u,
        stencilReadOnly = false,
    )

    private fun payload(
        render: GPUPreparedNativeScopeOperand.Render,
        keys: List<GPUPreparedNativeOperandKey>,
        generation: GPUDeviceGenerationID,
    ): GPUPreparedNativeFramePayload {
        val scopeKey = GPUPreparedNativeScopeKey(
            sourceStepIndex = render.sourceStepIndex,
            operationKind = GPUEncoderOperationKind.Render,
            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
            operandKeys = keys,
        )
        return GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(32),
                contextIdentity = "target.scene",
                encoderPlanId = "frame.32",
                deviceGeneration = generation,
                targetGeneration = 1,
                scopes = listOf(scopeKey),
            ),
            scopeOperands = listOf(render),
            scopeOperandKeys = listOf(keys),
        )
    }

    private fun key(
        role: GPUPreparedNativeOperandRole,
        kind: GPUPreparedNativeOperandKind,
        binding: String,
    ) = GPUPreparedNativeOperandKey(role, kind, gpuPreparedNativeBindingKey(binding))

    private fun textureViewOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeTextureViewOperand(fakeNative<GPUTextureView>(label), generation)

    private fun pipelineOperand(
        label: String,
        generation: GPUDeviceGenerationID,
    ) = GPUPreparedNativeRenderPipelineOperand(
        fakeNative<GPURenderPipeline>(label),
        generation,
    )

    private fun noBindingsPipelineFixture(
        generation: GPUDeviceGenerationID,
    ): NoBindingsPipelineFixture {
        val nativeFactory = PayloadSessionNativeFactory()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(nativeFactory.device, generation, nativeFactory)
        val acquired = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(
            cache.acquire(
                GPUWgpu4kCorePrimitivePipelineCacheKey(
                    componentIdentity = PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
                    pipelineIdentity = GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
                        targetFormat = "rgba8unorm",
                        sampleCount = 1,
                        topology = "triangle-list",
                        frontFace = "ccw",
                        cullMode = "none",
                        program = GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
                    ),
                ),
            ),
        )
        return NoBindingsPipelineFixture(
            GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(acquired, generation),
            cache,
        )
    }

    private fun acquiredPipelineFixture(
        generation: GPUDeviceGenerationID,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        program: GPUWgpu4kCorePrimitivePipelineProgram,
    ): AcquiredPipelineFixture {
        val nativeFactory = PayloadSessionNativeFactory()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(nativeFactory.device, generation, nativeFactory)
        val acquired = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(
            cache.acquire(
                GPUWgpu4kCorePrimitivePipelineCacheKey(
                    componentIdentity = componentIdentity,
                    pipelineIdentity = GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
                        targetFormat = "rgba8unorm",
                        sampleCount = 1,
                        topology = "triangle-list",
                        frontFace = "ccw",
                        cullMode = "none",
                        program = program,
                    ),
                ),
            ),
        )
        return AcquiredPipelineFixture(acquired, cache)
    }

    private class NoBindingsPipelineFixture(
        val operand: GPUPreparedNativeRenderPipelineOperand,
        private val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) : AutoCloseable {
        override fun close() = cache.close()
    }

    private class AcquiredPipelineFixture(
        val acquired: GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired,
        private val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) : AutoCloseable {
        override fun close() = cache.close()
    }

    private class PayloadSessionNativeFactory : GPUWgpu4kCorePrimitiveSessionNativeFactory {
        val device = fakeNative<GPUDevice>("device")

        override fun acceptsPipelineIdentity(
            identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        ) = true

        override fun createBindGroupLayout(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        ) = fakeNative<GPUBindGroupLayout>("bind-group-layout")

        override fun createShaderModule(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            plan: GPUCorePrimitiveNativeShaderPlan,
        ) = fakeNative<GPUShaderModule>("shader")

        override fun createPipelineLayout(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            bindGroupLayout: GPUBindGroupLayout,
        ) = fakeNative<GPUPipelineLayout>("pipeline-layout")

        override fun createRenderPipeline(
            identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
            shader: GPUShaderModule,
            pipelineLayout: GPUPipelineLayout,
        ) = fakeNative<GPURenderPipeline>("pipeline")
    }

    private fun bindGroupOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeBindGroupOperand(fakeNative<GPUBindGroup>(label), generation)

    private fun bufferOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeBufferOperand(fakeNative<GPUBuffer>(label), generation, byteCapacity = 256L)

    private fun vertexCommand(buffer: GPUPreparedNativeBufferOperand) =
        GPUPreparedNativeRenderCommand.SetVertexBuffer(0, buffer, 0L, 256L, 8L)

    private fun indexCommand(buffer: GPUPreparedNativeBufferOperand) =
        GPUPreparedNativeRenderCommand.SetIndexBuffer(
            buffer,
            GPUPreparedNativeIndexFormat.Uint32,
            0L,
            256L,
        )

    private fun drawCommand() = GPUPreparedNativeRenderCommand.DrawIndexed(
        GPUPreparedNativeDrawCall.DrawIndexed(
            indexCount = 3,
            vertexCount = 4,
            maxLocalIndex = 2,
        ),
    )

    private fun fullTargetFixture(
        generation: GPUDeviceGenerationID,
        pipelineOwnership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
    ): FullTargetFixture {
        val acquisition = acquiredPipelineFixture(
            generation = generation,
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
            program = GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        )
        val pipeline = try {
            GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                acquired = acquisition.acquired,
                deviceGeneration = generation,
                uniformAlignmentBytes = 256L,
                ownership = pipelineOwnership,
            )
        } catch (failure: Throwable) {
            acquisition.close()
            throw failure
        }
        return FullTargetFixture(
            target = textureViewOperand("target", generation),
            pipeline = pipeline,
            bindGroup = bindGroupOperand("consumer", generation),
            vertex = bufferOperand("vertices", generation),
            index = bufferOperand("indices", generation),
            acquisition = acquisition,
        )
    }

    private fun fullTargetCommands(
        fixture: FullTargetFixture,
        pipeline: GPUPreparedNativeRenderPipelineOperand = fixture.pipeline,
        bindGroup: GPUPreparedNativeBindGroupOperand = fixture.bindGroup,
        vertex: GPUPreparedNativeBufferOperand = fixture.vertex,
        index: GPUPreparedNativeBufferOperand = fixture.index,
        bindGroupIndex: Int = 0,
        dynamicOffsets: List<Long> = listOf(256L),
    ): List<GPUPreparedNativeRenderCommand> = listOf(
        GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
        GPUPreparedNativeRenderCommand.SetBindGroup(bindGroupIndex, bindGroup, dynamicOffsets),
        vertexCommand(vertex),
        indexCommand(index),
        drawCommand(),
    )

    private class FullTargetFixture(
        val target: GPUPreparedNativeTextureViewOperand,
        val pipeline: GPUPreparedNativeRenderPipelineOperand,
        val bindGroup: GPUPreparedNativeBindGroupOperand,
        val vertex: GPUPreparedNativeBufferOperand,
        val index: GPUPreparedNativeBufferOperand,
        private val acquisition: AcquiredPipelineFixture,
    ) : AutoCloseable {
        override fun close() = acquisition.close()
    }

    private fun fullTargetRender(
        pass: GPUPreparedNativeRenderPassConfig,
        commands: List<GPUPreparedNativeRenderCommand>,
    ) = GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = 2,
        pass = pass,
        commands = commands,
        operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitiveFullTarget,
    )

    private fun <T> permutations(values: List<T>): List<List<T>> =
        if (values.isEmpty()) {
            listOf(emptyList())
        } else {
            values.flatMapIndexed { selectedIndex, selected ->
                permutations(values.filterIndexed { index, _ -> index != selectedIndex })
                    .map { remainder -> listOf(selected) + remainder }
            }
        }

    private fun assertOperandIdentities(
        expected: List<GPUPreparedNativeOperand>,
        actual: List<GPUPreparedNativeOperand>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (expectedOperand, actualOperand) ->
            assertSame(expectedOperand, actualOperand, "operand[$index]")
        }
    }
}

private inline fun <reified T> fakeNative(label: String): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { _, method, _ ->
    when (method.name) {
        "getLabel" -> label
        "setLabel", "close" -> Unit
        "toString" -> "FakeNative($label)"
        else -> error("Unexpected fake native call: ${method.name}")
    }
} as T
