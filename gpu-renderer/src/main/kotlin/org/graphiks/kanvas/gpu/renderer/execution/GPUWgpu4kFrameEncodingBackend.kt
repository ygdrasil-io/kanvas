package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUCommandBuffer
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.beginRenderPass
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget

internal data class GPUWgpu4kFrameEncodingCounters(
    val encoders: Long,
    val renderPasses: Long,
    val draws: Long,
    val readbackCopies: Long,
    val finishes: Long,
    val submits: Long,
    val pendingCommandBuffers: Int,
    val destinationCopies: Long = 0L,
    val resourceCopies: Long = 0L,
    val msaaResolves: Long = 0L,
    val drawIndexed: Long = 0L,
    val pipelineBinds: Long = 0L,
)

internal class GPUWgpu4kRenderCommandActions(
    val setPipeline: (io.ygdrasil.webgpu.GPURenderPipeline) -> Unit,
    val setStencilReference: (UInt) -> Unit,
    val setBindGroup: (UInt, io.ygdrasil.webgpu.GPUBindGroup, List<UInt>) -> Unit,
    val setVertexBuffer: (UInt, io.ygdrasil.webgpu.GPUBuffer, Long, Long) -> Unit,
    val setIndexBuffer: (io.ygdrasil.webgpu.GPUBuffer, io.ygdrasil.webgpu.GPUIndexFormat, Long, Long) -> Unit,
    val setScissor: (UInt, UInt, UInt, UInt) -> Unit,
    val draw: (UInt, UInt, UInt, UInt) -> Unit,
    val drawIndexed: (UInt, UInt, UInt, Int, UInt) -> Unit,
)

internal fun encodeWgpu4kRenderCommands(
    commands: List<GPUPreparedNativeRenderCommand>,
    actions: GPUWgpu4kRenderCommandActions,
): Int {
    var draws = 0
    commands.forEach { command ->
        when (command) {
            is GPUPreparedNativeRenderCommand.SetPipeline -> actions.setPipeline(command.pipeline.pipeline)
            is GPUPreparedNativeRenderCommand.SetStencilReference -> actions.setStencilReference(command.reference)
            is GPUPreparedNativeRenderCommand.SetBindGroup ->
                actions.setBindGroup(
                    command.index.toUInt(),
                    command.bindGroup.bindGroup,
                    command.dynamicOffsets.map { offset ->
                        require(offset <= UInt.MAX_VALUE.toLong()) {
                            "Dynamic uniform offset must fit the wgpu4k UInt API"
                        }
                        offset.toUInt()
                    },
                )
            is GPUPreparedNativeRenderCommand.SetScissor -> actions.setScissor(
                command.x.toUInt(), command.y.toUInt(), command.width.toUInt(), command.height.toUInt(),
            )
            is GPUPreparedNativeRenderCommand.SetVertexBuffer -> actions.setVertexBuffer(
                command.slot.toUInt(), command.buffer.buffer, command.offset, command.size,
            )
            is GPUPreparedNativeRenderCommand.SetIndexBuffer -> {
                actions.setIndexBuffer(
                    command.buffer.buffer,
                    when (command.format) {
                        GPUPreparedNativeIndexFormat.Uint16 -> io.ygdrasil.webgpu.GPUIndexFormat.Uint16
                        GPUPreparedNativeIndexFormat.Uint32 -> io.ygdrasil.webgpu.GPUIndexFormat.Uint32
                    },
                    command.offset,
                    command.size,
                )
            }
            is GPUPreparedNativeRenderCommand.Draw -> {
                actions.draw(
                    command.drawCall.vertexCount.toUInt(), command.drawCall.instanceCount.toUInt(),
                    command.drawCall.firstVertex.toUInt(), command.drawCall.firstInstance.toUInt(),
                )
                draws += 1
            }
            is GPUPreparedNativeRenderCommand.DrawIndexed -> {
                actions.drawIndexed(
                    command.drawCall.indexCount.toUInt(),
                    command.drawCall.instanceCount.toUInt(),
                    command.drawCall.firstIndex.toUInt(),
                    command.drawCall.baseVertex,
                    command.drawCall.firstInstance.toUInt(),
                )
                draws += 1
            }
        }
    }
    return draws
}

internal fun buildWgpu4kRenderPassDescriptor(
    pass: GPUPreparedNativeRenderPassConfig,
): RenderPassDescriptor = RenderPassDescriptor(
    colorAttachments = listOf(
        RenderPassColorAttachment(
            view = pass.colorTarget.view,
            resolveTarget = pass.resolveTarget?.view,
            loadOp = when (pass.loadOperation) {
                GPUPreparedNativeLoadOperation.Load -> GPULoadOp.Load
                GPUPreparedNativeLoadOperation.Clear -> GPULoadOp.Clear
            },
            clearValue = pass.clearColor?.let {
                Color(it.red, it.green, it.blue, it.alpha)
            } ?: Color(0.0, 0.0, 0.0, 0.0),
            storeOp = when (pass.storeOperation) {
                GPUPreparedNativeStoreOperation.Store -> GPUStoreOp.Store
                GPUPreparedNativeStoreOperation.Discard -> GPUStoreOp.Discard
            },
        ),
    ),
    depthStencilAttachment = pass.depthStencilTarget?.let { target ->
        RenderPassDepthStencilAttachment(
            view = target.view,
            depthClearValue = pass.depthClearValue,
            depthLoadOp = pass.depthLoadOperation?.toWgpu4kLoadOperation(),
            depthStoreOp = pass.depthStoreOperation?.toWgpu4kStoreOperation(),
            depthReadOnly = pass.depthReadOnly,
            stencilClearValue = pass.stencilClearValue ?: 0u,
            stencilLoadOp = pass.stencilLoadOperation?.toWgpu4kLoadOperation(),
            stencilStoreOp = pass.stencilStoreOperation?.toWgpu4kStoreOperation(),
            stencilReadOnly = pass.stencilReadOnly,
        )
    },
)

private fun GPUPreparedNativeLoadOperation.toWgpu4kLoadOperation(): GPULoadOp = when (this) {
    GPUPreparedNativeLoadOperation.Load -> GPULoadOp.Load
    GPUPreparedNativeLoadOperation.Clear -> GPULoadOp.Clear
}

private fun GPUPreparedNativeStoreOperation.toWgpu4kStoreOperation(): GPUStoreOp = when (this) {
    GPUPreparedNativeStoreOperation.Store -> GPUStoreOp.Store
    GPUPreparedNativeStoreOperation.Discard -> GPUStoreOp.Discard
}

internal fun encodeWgpu4kRenderPass(
    encoder: GPUCommandEncoder,
    render: GPUPreparedNativeScopeOperand.Render,
    onRenderPassBegan: () -> Unit = {},
    onDrawEncoded: () -> Unit = {},
    onDrawIndexedEncoded: () -> Unit = {},
    onPipelineBound: () -> Unit = {},
): Int {
    var encodedDraws = 0
    encoder.beginRenderPass(buildWgpu4kRenderPassDescriptor(render.pass)) {
        onRenderPassBegan()
        encodedDraws = encodeWgpu4kRenderCommands(
            render.commands,
            GPUWgpu4kRenderCommandActions(
                setPipeline = { pipeline ->
                    setPipeline(pipeline)
                    onPipelineBound()
                },
                setStencilReference = { reference -> setStencilReference(reference) },
                setBindGroup = { index, bindGroup, dynamicOffsets ->
                    setBindGroup(index, bindGroup, dynamicOffsets)
                },
                setVertexBuffer = { slot, buffer, offset, size ->
                    setVertexBuffer(slot, buffer, offset.toULong(), size.toULong())
                },
                setIndexBuffer = { buffer, format, offset, size ->
                    setIndexBuffer(buffer, format, offset.toULong(), size.toULong())
                },
                setScissor = { x, y, width, height -> setScissorRect(x, y, width, height) },
                draw = { vertices, instances, firstVertex, firstInstance ->
                    draw(vertices, instances, firstVertex, firstInstance)
                    onDrawEncoded()
                },
                drawIndexed = { count, instances, firstIndex, baseVertex, firstInstance ->
                    drawIndexed(count, instances, firstIndex, baseVertex, firstInstance)
                    onDrawEncoded()
                    onDrawIndexedEncoded()
                },
            ),
        )
        end()
    }
    return encodedDraws
}

/** Production one-encoder backend. Native command buffers never escape its one-shot token table. */
internal class GPUWgpu4kFrameEncodingBackend(
    override val deviceGeneration: GPUDeviceGenerationID,
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val canonicalSceneTargetView: GPUTextureView? = null,
) : GPUFrameEncodingBackend, AutoCloseable {
    override val encodingMode: GPUFrameEncodingMode = GPUFrameEncodingMode.NativeOperandsRequired

    private val ordinal = AtomicLong(0L)
    private val liveEncoders = linkedMapOf<Long, GPUCommandEncoder>()
    private val commandBuffers = linkedMapOf<String, GPUCommandBuffer>()
    private val quarantinedEncoders = mutableListOf<GPUCommandEncoder>()
    private val quarantinedCommandBuffers = mutableListOf<GPUCommandBuffer>()
    private var closed = false
    private var encoderCount = 0L
    private var renderPassCount = 0L
    private var drawCount = 0L
    private var drawIndexedCount = 0L
    private var pipelineBindCount = 0L
    private var readbackCopyCount = 0L
    private var finishCount = 0L
    private var submitCount = 0L
    private var destinationCopyCount = 0L
    private var resourceCopyCount = 0L
    private var msaaResolveCount = 0L

    override fun isCanonicalSceneTargetView(
        sceneTarget: GPUSceneTarget,
        operand: GPUPreparedNativeTextureViewOperand,
    ): Boolean = canonicalSceneTargetView != null &&
        operand.view === canonicalSceneTargetView &&
        operand.deviceGeneration == deviceGeneration

    override fun createCommandEncoder(label: String): GPUFrameCommandEncoder {
        require(label.isNotBlank())
        val id: Long
        val encoder: GPUCommandEncoder
        synchronized(this) {
            check(!closed) { "GPU frame encoding backend is closed" }
            id = ordinal.incrementAndGet()
            encoder = device.createCommandEncoder()
            // wgpu4k snapshot 0.2.0-20260716.235022-2 exposes the setter, but its native
            // _wgpuCommandEncoderSetLabel implementation aborts with `not implemented`.
            // Keep the validated label in Kanvas telemetry/token identity until that optional
            // diagnostic API is implemented; encoding must not invoke an aborting native call.
            liveEncoders[id] = encoder
            encoderCount += 1
        }
        return NativeFrameEncoder(id, encoder)
    }

    override fun submit(commandBuffer: GPUFrameCommandBuffer) {
        val native = synchronized(this) {
            check(!closed) { "GPU frame encoding backend is closed" }
            commandBuffers.remove(commandBuffer.value)
                ?: error("Unknown or already submitted GPU frame command-buffer token")
        }
        try {
            queue.submit(listOf(native))
            synchronized(this) { submitCount += 1 }
        } finally {
            closeOrQuarantine(native, quarantinedCommandBuffers)
        }
    }

    override fun discard(commandBuffer: GPUFrameCommandBuffer): GPUFrameDiscardResult {
        val native = synchronized(this) {
            commandBuffers.remove(commandBuffer.value)
        } ?: return GPUFrameDiscardResult.AlreadyReleased
        return closeOrQuarantine(native, quarantinedCommandBuffers)
    }

    @Synchronized
    fun counters(): GPUWgpu4kFrameEncodingCounters = GPUWgpu4kFrameEncodingCounters(
        encoders = encoderCount,
        renderPasses = renderPassCount,
        draws = drawCount,
        drawIndexed = drawIndexedCount,
        readbackCopies = readbackCopyCount,
        finishes = finishCount,
        submits = submitCount,
        pendingCommandBuffers = commandBuffers.size,
        destinationCopies = destinationCopyCount,
        resourceCopies = resourceCopyCount,
        msaaResolves = msaaResolveCount,
        pipelineBinds = pipelineBindCount,
    )

    override fun close() {
        synchronized(this) {
            if (closed && remainingNativeOwnerCount() == 0) return
            closed = true
        }
        val failures = mutableListOf<Throwable>()
        closeMapEntries(liveEncoders, failures)
        closeMapEntries(commandBuffers, failures)
        closeListEntries(quarantinedEncoders, failures)
        closeListEntries(quarantinedCommandBuffers, failures)
        val remaining = synchronized(this) { remainingNativeOwnerCount() }
        if (remaining > 0) {
            throw GPUOwnedNativeCloseIncompleteException(
                ownerLabel = "frame-encoding",
                remainingOwnerCount = remaining,
                failures = failures,
            )
        }
    }

    private fun <K, T : AutoCloseable> closeMapEntries(
        entries: MutableMap<K, T>,
        failures: MutableList<Throwable>,
    ) {
        val snapshot = synchronized(this) { entries.entries.toList().asReversed() }
        snapshot.forEach { (key, handle) ->
            try {
                handle.close()
                synchronized(this) {
                    if (entries[key] === handle) entries.remove(key)
                }
            } catch (failure: Throwable) {
                failures += failure
            }
        }
    }

    private fun <T : AutoCloseable> closeListEntries(
        entries: MutableList<T>,
        failures: MutableList<Throwable>,
    ) {
        val snapshot = synchronized(this) { entries.toList().asReversed() }
        snapshot.forEach { handle ->
            try {
                handle.close()
                synchronized(this) { entries.removeAll { it === handle } }
            } catch (failure: Throwable) {
                failures += failure
            }
        }
    }

    private fun remainingNativeOwnerCount(): Int =
        liveEncoders.size + commandBuffers.size +
            quarantinedEncoders.size + quarantinedCommandBuffers.size

    private inner class NativeFrameEncoder(
        private val id: Long,
        private val native: GPUCommandEncoder,
    ) : GPUFrameCommandEncoder {
        private var finished = false

        override fun encode(
            scope: GPUCommandEncoderScopePlan,
            preparedFrame: PreparedGPUFrame,
            sceneTarget: GPUSceneTarget,
            nativeOperand: GPUPreparedNativeScopeOperand?,
        ) {
            check(!finished) { "GPU frame command encoder was already finished" }
            val operand = requireNotNull(nativeOperand) { "Native operand is required" }
            require(operand.sourceStepIndex == scope.sourceStepIndex)
            require(operand.operationKind == scope.operationKind)
            when (operand) {
                is GPUPreparedNativeScopeOperand.Render -> encodeRender(operand)
                is GPUPreparedNativeScopeOperand.Copy -> encodeCopy(operand)
                is GPUPreparedNativeScopeOperand.Readback -> encodeReadback(operand)
                is GPUPreparedNativeScopeOperand.SurfaceBlit -> encodeSurfaceBlit(operand)
                else -> error("Unsupported production native frame scope: ${operand.operationKind}")
            }
        }

        override fun finish(): GPUFrameCommandBuffer {
            check(!finished) { "GPU frame command encoder may finish only once" }
            finished = true
            val nativeBuffer = try {
                native.finish()
            } finally {
                synchronized(this@GPUWgpu4kFrameEncodingBackend) {
                    liveEncoders.remove(id)
                }
                closeOrQuarantine(native, quarantinedEncoders)
            }
            val token = "wgpu4k.frame-command-buffer.$id"
            synchronized(this@GPUWgpu4kFrameEncodingBackend) {
                if (closed) {
                    closeOrQuarantine(nativeBuffer, quarantinedCommandBuffers)
                    error("GPU frame encoding backend closed while finishing")
                }
                check(commandBuffers.put(token, nativeBuffer) == null)
                finishCount += 1
            }
            return GPUFrameCommandBuffer(token)
        }

        override fun discard(): GPUFrameDiscardResult {
            if (finished) return GPUFrameDiscardResult.AlreadyReleased
            finished = true
            synchronized(this@GPUWgpu4kFrameEncodingBackend) {
                liveEncoders.remove(id)
            }
            return closeOrQuarantine(native, quarantinedEncoders)
        }

        private fun encodeRender(render: GPUPreparedNativeScopeOperand.Render) {
            val pass = render.pass
            encodeWgpu4kRenderPass(
                native,
                render,
                onRenderPassBegan = {
                    synchronized(this@GPUWgpu4kFrameEncodingBackend) { renderPassCount += 1 }
                },
                onDrawEncoded = {
                    synchronized(this@GPUWgpu4kFrameEncodingBackend) { drawCount += 1 }
                },
                onDrawIndexedEncoded = {
                    synchronized(this@GPUWgpu4kFrameEncodingBackend) { drawIndexedCount += 1 }
                },
                onPipelineBound = {
                    synchronized(this@GPUWgpu4kFrameEncodingBackend) { pipelineBindCount += 1 }
                },
            )
            if (pass.resolveTarget != null) {
                synchronized(this@GPUWgpu4kFrameEncodingBackend) { msaaResolveCount += 1 }
            }
        }

        private fun encodeReadback(readback: GPUPreparedNativeScopeOperand.Readback) {
            require(readback.layout.originX == 0 && readback.layout.originY == 0) {
                "First production readback slice supports the exact full-target origin only"
            }
            native.copyTextureToBuffer(
                source = TexelCopyTextureInfo(texture = readback.source.texture),
                destination = TexelCopyBufferInfo(
                    buffer = readback.destination.buffer,
                    offset = readback.layout.bufferOffset.toULong(),
                    bytesPerRow = readback.layout.bytesPerRow.toUInt(),
                    rowsPerImage = readback.layout.rowsPerImage.toUInt(),
                ),
                copySize = Extent3D(
                    width = readback.layout.width.toUInt(),
                    height = readback.layout.height.toUInt(),
                ),
            )
            synchronized(this@GPUWgpu4kFrameEncodingBackend) { readbackCopyCount += 1 }
        }

        private fun encodeSurfaceBlit(blit: GPUPreparedNativeScopeOperand.SurfaceBlit) {
            native.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = blit.target.view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = Color(0.0, 0.0, 0.0, 0.0),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                setPipeline(blit.pipeline.pipeline)
                setBindGroup(0u, blit.bindGroup.bindGroup)
                draw(3u, 1u, 0u, 0u)
                end()
            }
            synchronized(this@GPUWgpu4kFrameEncodingBackend) {
                renderPassCount += 1
                drawCount += 1
                pipelineBindCount += 1
            }
        }

        private fun encodeCopy(copy: GPUPreparedNativeScopeOperand.Copy) {
            val layout = requireNotNull(copy.textureLayout) {
                "Texture copy requires exact native texture geometry"
            }
            native.copyTextureToTexture(
                source = TexelCopyTextureInfo(
                    texture = copy.source.texture,
                    origin = Origin3D(layout.sourceOriginX.toUInt(), layout.sourceOriginY.toUInt()),
                ),
                destination = TexelCopyTextureInfo(
                    texture = copy.destination.texture,
                    origin = Origin3D(layout.destinationOriginX.toUInt(), layout.destinationOriginY.toUInt()),
                ),
                copySize = Extent3D(layout.width.toUInt(), layout.height.toUInt()),
            )
            synchronized(this@GPUWgpu4kFrameEncodingBackend) {
                when (copy.operationKind) {
                    GPUEncoderOperationKind.Copy -> resourceCopyCount += 1
                    GPUEncoderOperationKind.CopyDestination -> destinationCopyCount += 1
                    else -> error("Unsupported texture copy operation ${copy.operationKind}")
                }
            }
        }
    }

    private fun <T : AutoCloseable> closeOrQuarantine(
        handle: T,
        quarantine: MutableList<T>,
    ): GPUFrameDiscardResult = try {
        handle.close()
        GPUFrameDiscardResult.Discarded
    } catch (failure: Throwable) {
        synchronized(this) {
            if (quarantine.none { it === handle }) quarantine += handle
        }
        GPUFrameDiscardResult.Failed(failure::class.simpleName.orEmpty())
    }
}
