package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUComputePipeline
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.requireResourceDumpSafe

/** Opaque, dump-safe identity of one adapter-owned prepared native payload. */
@JvmInline
internal value class GPUPreparedNativeFrameToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPreparedNativeFrameToken.value must not be blank" }
        requireResourceDumpSafe("GPUPreparedNativeFrameToken.value", value)
    }
}

/** Exact handle-free identity used to bind one native payload to one prepared encoder plan. */
internal class GPUPreparedNativeFrameIdentity(
    val frameId: GPUFrameID,
    val contextIdentity: String,
    val encoderPlanId: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    scopes: List<GPUPreparedNativeScopeKey>,
) {
    val scopes: List<GPUPreparedNativeScopeKey> = immutableList(scopes)

    init {
        require(contextIdentity.isNotBlank()) { "contextIdentity must not be blank" }
        require(encoderPlanId.isNotBlank()) { "encoderPlanId must not be blank" }
        require(targetGeneration >= 0L) {
            "GPUPreparedNativeFrameIdentity.targetGeneration must be non-negative"
        }
        require(scopes.map(GPUPreparedNativeScopeKey::sourceStepIndex).distinct().size == scopes.size) {
            "GPUPreparedNativeFrameIdentity.scopes must have unique source step indices"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is GPUPreparedNativeFrameIdentity &&
            frameId == other.frameId &&
            contextIdentity == other.contextIdentity &&
            encoderPlanId == other.encoderPlanId &&
            deviceGeneration == other.deviceGeneration &&
            targetGeneration == other.targetGeneration &&
            scopes == other.scopes

    override fun hashCode(): Int {
        var result = frameId.hashCode()
        result = 31 * result + contextIdentity.hashCode()
        result = 31 * result + encoderPlanId.hashCode()
        result = 31 * result + deviceGeneration.hashCode()
        result = 31 * result + targetGeneration.hashCode()
        result = 31 * result + scopes.hashCode()
        return result
    }
}

internal class GPUPreparedNativeScopeKey(
    val sourceStepIndex: Int,
    val operationKind: GPUEncoderOperationKind,
    resourceGenerationLabels: List<String> = emptyList(),
    operandKeys: List<GPUPreparedNativeOperandKey> = emptyList(),
) {
    val resourceGenerationLabels: List<String> = immutableList(resourceGenerationLabels)
    val operandKeys: List<GPUPreparedNativeOperandKey> = immutableList(operandKeys)

    init {
        require(sourceStepIndex >= 0) { "GPUPreparedNativeScopeKey.sourceStepIndex must be non-negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is GPUPreparedNativeScopeKey && sourceStepIndex == other.sourceStepIndex &&
            operationKind == other.operationKind && resourceGenerationLabels == other.resourceGenerationLabels &&
            operandKeys == other.operandKeys

    override fun hashCode(): Int =
        31 * (31 * (31 * sourceStepIndex + operationKind.hashCode()) +
            resourceGenerationLabels.hashCode()) + operandKeys.hashCode()
}

internal enum class GPUPreparedNativeOperandKind {
    Texture,
    TextureView,
    Buffer,
    RenderPipeline,
    ComputePipeline,
    BindGroup,
    Sampler,
}

internal enum class GPUPreparedNativeOperandRole {
    RenderColorTarget,
    RenderMsaaColorTarget,
    RenderResolveTarget,
    RenderPipeline,
    RenderBindGroup,
    RenderVertexBuffer,
    RenderIndexBuffer,
    ComputePipeline,
    ComputeBindGroup,
    UploadSource,
    UploadDestination,
    CopySource,
    CopyDestination,
    CopyAsDrawSource,
    CopyAsDrawTarget,
    CopyAsDrawPipeline,
    CopyAsDrawBindGroup,
    ReadbackSource,
    ReadbackDestination,
    SurfaceSource,
    SurfaceTarget,
    SurfacePipeline,
    SurfaceBindGroup,
}

internal data class GPUPreparedNativeOperandKey(
    val role: GPUPreparedNativeOperandRole,
    val kind: GPUPreparedNativeOperandKind,
    val bindingKey: String,
    val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) {
    init {
        require(bindingKey.isNotBlank()) { "GPUPreparedNativeOperandKey.bindingKey must not be blank" }
        requireExecutionDumpSafe("GPUPreparedNativeOperandKey.bindingKey", bindingKey)
    }
}

/** Injective handle-free encoding for arbitrary semantic labels into dump-safe operand evidence. */
internal fun gpuPreparedNativeBindingKey(value: String): String {
    require(value.isNotBlank()) { "Native operand binding source must not be blank" }
    return value.fold(StringBuilder("key.")) { encoded, character ->
        encoded.append(character.code.toString(16).padStart(4, '0'))
    }.toString()
}

/**
 * Private typed native operand. It never enters PreparedGPUFrame, dumps, hashes, or telemetry.
 */
internal sealed interface GPUPreparedNativeOperand {
    val deviceGeneration: GPUDeviceGenerationID
    val ownership: GPUPreparedNativeOperandOwnership
}

internal enum class GPUPreparedNativeOperandOwnership {
    Borrowed,
    PayloadOwnedCompletion,
    OutputOwnedReadback,
}

/**
 * Native handle required by a typed operand but not itself encoded as an operand.
 *
 * Uniform buffers retained by bind groups are the first such case. They remain in the same
 * identity-based ownership ledger as directly encoded handles and may never be borrowed.
 */
internal class GPUPreparedNativeAuxiliaryHandle(
    val handle: AutoCloseable,
    val ownership: GPUPreparedNativeOperandOwnership,
) {
    init {
        require(ownership != GPUPreparedNativeOperandOwnership.Borrowed) {
            "Auxiliary native handles must have an explicit payload or output owner"
        }
    }
}

/**
 * One completion-owned lifetime anchor for handles exposed as borrowed operands.
 *
 * Successful closes are removed immediately. Failed closes remain pending so the adapter can
 * quarantine the payload and retry without closing an already released handle twice.
 */
internal class GPUPreparedNativeCompletionAnchor(
    handles: List<AutoCloseable>,
) : AutoCloseable {
    private val pending = handles.asReversed().toMutableList()

    init {
        require(handles.isNotEmpty()) { "A native completion anchor must own at least one handle" }
        val identities = java.util.Collections.newSetFromMap(
            IdentityHashMap<AutoCloseable, Boolean>(),
        )
        require(handles.all(identities::add)) {
            "A native completion anchor cannot own one handle more than once"
        }
    }

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            try {
                handle.close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { failure ->
            throw IllegalStateException(
                "Native completion anchor retains ${pending.size} handle(s) after close failure",
                failure,
            )
        }
    }

    @Synchronized
    internal fun ownedHandlesSnapshot(): List<AutoCloseable> = pending.toList()

    @Synchronized
    internal fun detachOwnedHandles(handles: Collection<AutoCloseable>) {
        pending.removeAll { candidate -> handles.any { it === candidate } }
    }
}

/** Retryable ownership journal for native handles created before payload registration. */
internal class GPUPreRegistrationNativeHandleLedger {
    private enum class Owner {
        Materializer,
        Adapter,
        Released,
    }

    private var owner = Owner.Materializer
    private val pending = mutableListOf<AutoCloseable>()

    @Synchronized
    internal fun <T : AutoCloseable> track(handle: T): T {
        check(owner != Owner.Adapter) { "Adapter-owned native setup ledger cannot be extended" }
        if (owner == Owner.Released) owner = Owner.Materializer
        require(pending.none { it === handle }) {
            "A pre-registration native handle may be tracked only once"
        }
        pending += handle
        return handle
    }

    @Synchronized
    internal fun transferAll() {
        pending.clear()
        owner = Owner.Released
    }

    @Synchronized
    internal fun closeRetainingFailures(): Boolean {
        if (owner == Owner.Adapter) return false
        return closePending().also { released ->
            if (released) owner = Owner.Released
        }
    }

    @Synchronized
    internal fun transferOwnershipToAdapter(): Boolean {
        if (owner != Owner.Materializer || pending.isEmpty()) return false
        owner = Owner.Adapter
        return true
    }

    @Synchronized
    internal fun closeRetainingFailuresByAdapter(): Boolean {
        check(owner == Owner.Adapter) { "Adapter close requires transferred setup ownership" }
        return closePending().also { released ->
            if (released) owner = Owner.Released
        }
    }

    private fun closePending(): Boolean {
        for (index in pending.lastIndex downTo 0) {
            try {
                pending[index].close()
                pending.removeAt(index)
            } catch (_: Throwable) {
                // Keep the exact failed handle for a later materializer close retry.
            }
        }
        return pending.isEmpty()
    }

    @Synchronized
    internal fun pendingHandlesSnapshot(): List<AutoCloseable> = pending.toList()

    @Synchronized
    internal fun detachPendingHandles(handles: Collection<AutoCloseable>) {
        pending.removeAll { candidate -> handles.any { it === candidate } }
        if (pending.isEmpty() && owner == Owner.Materializer) owner = Owner.Released
    }

    @Synchronized
    internal fun isAdapterOwned(): Boolean = owner == Owner.Adapter

    internal val pendingHandleCount: Int
        @Synchronized get() = pending.size
}

internal class GPUPreparedNativeTextureOperand(
    val texture: GPUTexture,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeTextureViewOperand(
    val view: GPUTextureView,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeBufferOperand(
    val buffer: GPUBuffer,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeRenderPipelineOperand(
    val pipeline: GPURenderPipeline,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeComputePipelineOperand(
    val pipeline: GPUComputePipeline,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeBindGroupOperand(
    val bindGroup: GPUBindGroup,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal class GPUPreparedNativeSamplerOperand(
    val sampler: GPUSampler,
    override val deviceGeneration: GPUDeviceGenerationID,
    override val ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
) : GPUPreparedNativeOperand

internal sealed interface GPUPreparedNativeDrawCall {
    data class Draw(
        val vertexCount: Int,
        val instanceCount: Int = 1,
        val firstVertex: Int = 0,
        val firstInstance: Int = 0,
    ) : GPUPreparedNativeDrawCall {
        init { require(vertexCount > 0 && instanceCount > 0) }
    }

    data class DrawIndexed(
        val indexCount: Int,
        val instanceCount: Int = 1,
        val firstIndex: Int = 0,
        val baseVertex: Int = 0,
        val firstInstance: Int = 0,
    ) : GPUPreparedNativeDrawCall {
        init { require(indexCount > 0 && instanceCount > 0) }
    }
}

internal class GPUPreparedNativeRenderPassConfig(
    val colorTarget: GPUPreparedNativeTextureViewOperand,
    val resolveTarget: GPUPreparedNativeTextureViewOperand? = null,
    val depthStencilTarget: GPUPreparedNativeTextureViewOperand? = null,
    val loadOperation: GPUPreparedNativeLoadOperation = GPUPreparedNativeLoadOperation.Load,
    val storeOperation: GPUPreparedNativeStoreOperation = GPUPreparedNativeStoreOperation.Store,
    val clearColor: GPUPreparedNativeClearColor? = null,
    val depthClearValue: Float? = null,
    val depthLoadOperation: GPUPreparedNativeLoadOperation? = null,
    val depthStoreOperation: GPUPreparedNativeStoreOperation? = null,
    val depthReadOnly: Boolean = true,
    val stencilClearValue: UInt? = null,
    val stencilLoadOperation: GPUPreparedNativeLoadOperation? = null,
    val stencilStoreOperation: GPUPreparedNativeStoreOperation? = null,
    val stencilReadOnly: Boolean = true,
) {
    init {
        require((loadOperation == GPUPreparedNativeLoadOperation.Clear) == (clearColor != null)) {
            "Clear load operation requires exactly one clear color"
        }
        require(depthClearValue == null || depthClearValue.isFinite() && depthClearValue in 0.0f..1.0f) {
            "Depth clear value must be finite and in the inclusive range 0.0 to 1.0"
        }
        require(stencilClearValue == null || stencilClearValue <= 0xffu) {
            "Stencil clear value must fit the WebGPU stencil8 range"
        }
        validatePreparedDepthStencilAspect(
            aspect = "depth",
            target = depthStencilTarget,
            loadOperation = depthLoadOperation,
            storeOperation = depthStoreOperation,
            clearValuePresent = depthClearValue != null,
            readOnly = depthReadOnly,
        )
        validatePreparedDepthStencilAspect(
            aspect = "stencil",
            target = depthStencilTarget,
            loadOperation = stencilLoadOperation,
            storeOperation = stencilStoreOperation,
            clearValuePresent = stencilClearValue != null,
            readOnly = stencilReadOnly,
        )
    }
}

private fun validatePreparedDepthStencilAspect(
    aspect: String,
    target: GPUPreparedNativeTextureViewOperand?,
    loadOperation: GPUPreparedNativeLoadOperation?,
    storeOperation: GPUPreparedNativeStoreOperation?,
    clearValuePresent: Boolean,
    readOnly: Boolean,
) {
    require((loadOperation == GPUPreparedNativeLoadOperation.Clear) == clearValuePresent) {
        "$aspect clear load operation requires exactly one clear value"
    }
    require((loadOperation == null) == (storeOperation == null)) {
        "$aspect load and store operations must either both be present or both be absent"
    }
    if (target == null) {
        require(loadOperation == null && storeOperation == null && !clearValuePresent) {
            "$aspect operations require a depth-stencil target"
        }
    } else if (readOnly) {
        require(loadOperation == null && storeOperation == null && !clearValuePresent) {
            "$aspect read-only state cannot carry load, clear, or store operations"
        }
    } else {
        require(loadOperation != null && storeOperation != null) {
            "$aspect writable state requires explicit load and store operations"
        }
    }
}

internal enum class GPUPreparedNativeLoadOperation { Load, Clear }
internal enum class GPUPreparedNativeStoreOperation { Store, Discard }
internal data class GPUPreparedNativeClearColor(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double,
) {
    init { require(listOf(red, green, blue, alpha).all(Double::isFinite)) }
}

internal sealed interface GPUPreparedNativeRenderCommand {
    val operands: List<GPUPreparedNativeOperand>

    data class SetPipeline(val pipeline: GPUPreparedNativeRenderPipelineOperand) : GPUPreparedNativeRenderCommand {
        override val operands = listOf<GPUPreparedNativeOperand>(pipeline)
    }

    class SetBindGroup(
        val index: Int,
        val bindGroup: GPUPreparedNativeBindGroupOperand,
        dynamicOffsets: List<Long> = emptyList(),
    ) : GPUPreparedNativeRenderCommand {
        val dynamicOffsets = immutableList(dynamicOffsets)
        override val operands = listOf<GPUPreparedNativeOperand>(bindGroup)
        init { require(index >= 0 && this.dynamicOffsets.all { it >= 0L }) }
    }

    data class SetScissor(val x: Int, val y: Int, val width: Int, val height: Int) :
        GPUPreparedNativeRenderCommand {
        override val operands = emptyList<GPUPreparedNativeOperand>()
        init { require(x >= 0 && y >= 0 && width > 0 && height > 0) }
    }

    data class SetStencilReference(val reference: UInt) : GPUPreparedNativeRenderCommand {
        override val operands = emptyList<GPUPreparedNativeOperand>()
        init { require(reference <= 0xffu) { "Stencil reference must fit the WebGPU stencil8 range" } }
    }

    class SetVertexBuffer(
        val slot: Int,
        val buffer: GPUPreparedNativeBufferOperand,
        val offset: Long,
        val size: Long,
    ) : GPUPreparedNativeRenderCommand {
        override val operands = listOf<GPUPreparedNativeOperand>(buffer)
        init {
            require(slot >= 0 && offset == 0L && size > 0L) {
                "The first public-wgpu4k vertex-buffer bridge requires a zero offset and exact positive size"
            }
        }
    }

    class SetIndexBuffer(
        val buffer: GPUPreparedNativeBufferOperand,
        val format: GPUPreparedNativeIndexFormat,
        val offset: Long,
        val size: Long,
    ) : GPUPreparedNativeRenderCommand {
        override val operands = listOf<GPUPreparedNativeOperand>(buffer)
        init {
            require(offset == 0L && size > 0L) {
                "The first public-wgpu4k index-buffer bridge requires a zero offset and exact positive size"
            }
        }
    }

    data class Draw(val drawCall: GPUPreparedNativeDrawCall.Draw) : GPUPreparedNativeRenderCommand {
        override val operands = emptyList<GPUPreparedNativeOperand>()
    }

    data class DrawIndexed(val drawCall: GPUPreparedNativeDrawCall.DrawIndexed) : GPUPreparedNativeRenderCommand {
        override val operands = emptyList<GPUPreparedNativeOperand>()
    }
}

internal enum class GPUPreparedNativeIndexFormat { Uint16, Uint32 }

/** Closed per-scope operand algebra. No arbitrary encode callback can enter the payload. */
internal sealed interface GPUPreparedNativeScopeOperand {
    val sourceStepIndex: Int
    val operationKind: GPUEncoderOperationKind
    val operands: List<GPUPreparedNativeOperand>

    class Render(
        override val sourceStepIndex: Int,
        val pass: GPUPreparedNativeRenderPassConfig,
        commands: List<GPUPreparedNativeRenderCommand>,
        semanticPayloads: List<GPUDrawSemanticPayload> = emptyList(),
    ) : GPUPreparedNativeScopeOperand {
        val commands = immutableList(commands)
        val semanticPayloads = immutableList(semanticPayloads)
        override val operationKind = GPUEncoderOperationKind.Render
        override val operands: List<GPUPreparedNativeOperand> =
            immutableList(
                listOfNotNull(pass.colorTarget, pass.resolveTarget, pass.depthStencilTarget) +
                    this.commands.flatMap(GPUPreparedNativeRenderCommand::operands),
            )

        init {
            require(this.commands.any {
                it is GPUPreparedNativeRenderCommand.Draw || it is GPUPreparedNativeRenderCommand.DrawIndexed
            }) {
                "Render payload requires at least one closed typed draw"
            }
            var pipelineBound = false
            var bindGroupBound = false
            var vertexBufferBound = false
            var indexBufferBound = false
            var scissorBound = false
            this.commands.forEach { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.SetPipeline -> pipelineBound = true
                    is GPUPreparedNativeRenderCommand.SetBindGroup -> bindGroupBound = true
                    is GPUPreparedNativeRenderCommand.SetVertexBuffer -> vertexBufferBound = true
                    is GPUPreparedNativeRenderCommand.SetIndexBuffer -> indexBufferBound = true
                    is GPUPreparedNativeRenderCommand.SetScissor -> scissorBound = true
                    is GPUPreparedNativeRenderCommand.SetStencilReference -> Unit
                    is GPUPreparedNativeRenderCommand.Draw -> require(pipelineBound) {
                        "Every native draw requires a preceding SetPipeline command"
                    }
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> {
                        require(pipelineBound) { "Every native indexed draw requires a preceding SetPipeline command" }
                        require(bindGroupBound) { "Every native indexed draw requires a preceding SetBindGroup command" }
                        require(vertexBufferBound) { "Every native indexed draw requires a preceding SetVertexBuffer command" }
                        require(indexBufferBound) { "Every native indexed draw requires a preceding SetIndexBuffer command" }
                        require(scissorBound) { "Every native indexed draw requires a preceding SetScissor command" }
                        require(
                            command.drawCall.instanceCount == 1 && command.drawCall.firstIndex == 0 &&
                                command.drawCall.baseVertex == 0 && command.drawCall.firstInstance == 0,
                        ) { "The first public-wgpu4k indexed bridge requires default draw offsets and one instance" }
                    }
                }
            }
        }
    }

    class Compute(
        override val sourceStepIndex: Int,
        pipelines: List<GPUPreparedNativeComputePipelineOperand>,
        bindGroups: List<GPUPreparedNativeBindGroupOperand>,
    ) : GPUPreparedNativeScopeOperand {
        val pipelines = immutableList(pipelines)
        val bindGroups = immutableList(bindGroups)
        override val operationKind = GPUEncoderOperationKind.Compute
        override val operands: List<GPUPreparedNativeOperand> = immutableList(this.pipelines + this.bindGroups)

        init {
            require(this.pipelines.isNotEmpty()) { "Compute payload requires at least one pipeline" }
        }
    }

    class Upload(
        override val sourceStepIndex: Int,
        val source: GPUPreparedNativeBufferOperand,
        val destination: GPUPreparedNativeBufferOperand,
    ) : GPUPreparedNativeScopeOperand {
        override val operationKind = GPUEncoderOperationKind.Upload
        override val operands: List<GPUPreparedNativeOperand> = immutableList(listOf(source, destination))
    }

    class Copy(
        override val sourceStepIndex: Int,
        override val operationKind: GPUEncoderOperationKind,
        val source: GPUPreparedNativeTextureOperand,
        val destination: GPUPreparedNativeTextureOperand,
        val textureLayout: GPUPreparedNativeTextureCopyLayout? = null,
    ) : GPUPreparedNativeScopeOperand {
        override val operands: List<GPUPreparedNativeOperand> = immutableList(listOf(source, destination))

        init {
            require(operationKind in setOf(GPUEncoderOperationKind.Copy, GPUEncoderOperationKind.CopyDestination)) {
                "Copy payload only supports Copy or CopyDestination scopes"
            }
            require(textureLayout != null) {
                "Texture copy payload requires exact texture origins and extent"
            }
        }
    }

    class CopyAsDraw(
        override val sourceStepIndex: Int,
        val source: GPUPreparedNativeTextureViewOperand,
        val target: GPUPreparedNativeTextureViewOperand,
        val pipeline: GPUPreparedNativeRenderPipelineOperand,
        val bindGroup: GPUPreparedNativeBindGroupOperand,
    ) : GPUPreparedNativeScopeOperand {
        override val operationKind = GPUEncoderOperationKind.CopyAsDraw
        override val operands: List<GPUPreparedNativeOperand> =
            immutableList(listOf(source, target, pipeline, bindGroup))
    }

    class Readback(
        override val sourceStepIndex: Int,
        val source: GPUPreparedNativeTextureOperand,
        val destination: GPUPreparedNativeBufferOperand,
        val layout: GPUPreparedNativeReadbackLayout,
    ) : GPUPreparedNativeScopeOperand {
        override val operationKind = GPUEncoderOperationKind.Readback
        override val operands: List<GPUPreparedNativeOperand> = immutableList(listOf(source, destination))
    }

    class SurfaceBlit(
        override val sourceStepIndex: Int,
        val source: GPUPreparedNativeTextureViewOperand,
        val output: GPUSurfaceOutputRef,
        val pipeline: GPUPreparedNativeRenderPipelineOperand,
        val bindGroup: GPUPreparedNativeBindGroupOperand,
    ) : GPUPreparedNativeScopeOperand {
        private var boundTarget: GPUPreparedNativeTextureViewOperand? = null
        val target: GPUPreparedNativeTextureViewOperand
            get() = checkNotNull(boundTarget) { "SurfaceBlit target is not late-bound" }
        override val operationKind = GPUEncoderOperationKind.SurfaceBlit
        override val operands: List<GPUPreparedNativeOperand>
            get() = immutableList(listOfNotNull(source, boundTarget, pipeline, bindGroup))

        internal val isLateSurfaceBound: Boolean get() = boundTarget != null

        internal fun bindLateSurface(target: GPUPreparedNativeTextureViewOperand): Boolean {
            if (boundTarget != null || target.ownership != GPUPreparedNativeOperandOwnership.Borrowed) return false
            boundTarget = target
            return true
        }
    }
}

/** Exact texture-to-texture copy geometry; buffer row layout is intentionally absent. */
internal data class GPUPreparedNativeTextureCopyLayout(
    val sourceOriginX: Int,
    val sourceOriginY: Int,
    val destinationOriginX: Int,
    val destinationOriginY: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(sourceOriginX >= 0 && sourceOriginY >= 0) {
            "Texture copy source origin must be non-negative"
        }
        require(destinationOriginX >= 0 && destinationOriginY >= 0) {
            "Texture copy destination origin must be non-negative"
        }
        require(width > 0 && height > 0) { "Texture copy extent must be positive" }
    }
}

internal data class GPUPreparedNativeReadbackLayout(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    val bytesPerRow: Long,
    val rowsPerImage: Int,
    val bufferOffset: Long,
    val mappedSize: Long,
    val format: GPUTextureFormat,
) {
    init {
        require(originX >= 0 && originY >= 0 && width > 0 && height > 0)
        require(bytesPerRow > 0 && rowsPerImage > 0)
        require(bufferOffset >= 0 && mappedSize > 0)
        val minimumMappedSize = try {
            Math.addExact(
                bufferOffset,
                Math.addExact(
                    Math.multiplyExact(bytesPerRow, (height - 1).toLong()),
                    Math.multiplyExact(width.toLong(), 4L),
                ),
            )
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Readback row layout overflows Long")
        }
        require(mappedSize >= minimumMappedSize)
        require(bufferOffset <= Long.MAX_VALUE - mappedSize)
    }
}

/** Adapter-private payload; PreparedGPUFrame stores only the token returned at registration. */
internal class GPUPreparedNativeFramePayload(
    val identity: GPUPreparedNativeFrameIdentity,
    scopeOperands: List<GPUPreparedNativeScopeOperand>,
    scopeOperandKeys: List<List<GPUPreparedNativeOperandKey>>,
    auxiliaryOwnedHandles: List<GPUPreparedNativeAuxiliaryHandle> = emptyList(),
) {
    val scopeOperands: List<GPUPreparedNativeScopeOperand> = immutableList(scopeOperands)
    val scopeOperandKeys: List<List<GPUPreparedNativeOperandKey>> = immutableList(
        scopeOperandKeys.map(::immutableList),
    )
    internal val auxiliaryOwnedHandles: List<GPUPreparedNativeAuxiliaryHandle> =
        immutableList(auxiliaryOwnedHandles)
    private val ownershipByHandle = IdentityHashMap<AutoCloseable, GPUPreparedNativeOperandOwnership>()

    init {
        require(identity.scopes.size == this.scopeOperands.size) {
            "Native payload operands must cover every encoder scope"
        }
        require(this.scopeOperandKeys.size == this.scopeOperands.size) {
            "Native payload operand keys must cover every scope"
        }
        require(this.scopeOperands.indices.all { index ->
            val operands = this.scopeOperands[index].declaredOperandDescriptors()
            val keys = this.scopeOperandKeys[index]
            operands.size == keys.size && operands.zip(keys).all { (operand, key) ->
                operand.first == key.kind && operand.second == key.ownership
            }
        }) { "Native payload operand keys must exactly describe each typed native operand" }
        require(
            this.scopeOperands.indices.map { index ->
                GPUPreparedNativeScopeKey(
                    this.scopeOperands[index].sourceStepIndex,
                    this.scopeOperands[index].operationKind,
                    identity.scopes[index].resourceGenerationLabels,
                    this.scopeOperandKeys[index],
                )
            } == identity.scopes,
        ) { "Native payload operands must exactly match encoder scope and bridge keys in order" }
        require(this.scopeOperands.flatMap(GPUPreparedNativeScopeOperand::operands).all {
            it.deviceGeneration == identity.deviceGeneration
        }) { "Every native payload operand must match the payload device generation" }
        this.scopeOperands.flatMap(GPUPreparedNativeScopeOperand::operands).forEach { operand ->
            val handle = operand.nativeHandle()
            val previous = ownershipByHandle[handle]
            require(previous == null || previous == operand.ownership) {
                "One native handle cannot have multiple ownership categories"
            }
            ownershipByHandle[handle] = operand.ownership
        }
        this.auxiliaryOwnedHandles.forEach { auxiliary ->
            val previous = ownershipByHandle[auxiliary.handle]
            require(previous == null || previous == auxiliary.ownership) {
                "One native handle cannot have multiple ownership categories"
            }
            ownershipByHandle[auxiliary.handle] = auxiliary.ownership
        }
        val borrowedOperandHandles = java.util.Collections.newSetFromMap(
            IdentityHashMap<AutoCloseable, Boolean>(),
        )
        this.scopeOperands.flatMap(GPUPreparedNativeScopeOperand::operands)
            .filter { it.ownership == GPUPreparedNativeOperandOwnership.Borrowed }
            .mapTo(borrowedOperandHandles, GPUPreparedNativeOperand::nativeHandle)
        val anchoredHandles = java.util.Collections.newSetFromMap(
            IdentityHashMap<AutoCloseable, Boolean>(),
        )
        this.auxiliaryOwnedHandles.forEach { auxiliary ->
            val anchor = auxiliary.handle as? GPUPreparedNativeCompletionAnchor ?: return@forEach
            require(auxiliary.ownership == GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion) {
                "Native completion anchors must be completion-owned auxiliaries"
            }
            anchor.ownedHandlesSnapshot().forEach { anchored ->
                require(anchored in borrowedOperandHandles) {
                    "Native completion anchors may own only exact borrowed operand handles"
                }
                require(anchoredHandles.add(anchored)) {
                    "One borrowed operand handle cannot be owned by multiple completion anchors"
                }
            }
        }
    }

    internal val hasOutputOwnedReadback: Boolean = this.scopeOperands
        .flatMap(GPUPreparedNativeScopeOperand::operands)
        .any { it.ownership == GPUPreparedNativeOperandOwnership.OutputOwnedReadback }

    internal fun bindLateSurface(
        acquiredSurface: GPUAcquiredSurfaceOutput?,
        binding: GPUPreparedNativeFrameLateSurfaceBinding,
    ): Boolean {
        val surfaceScopes = scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.SurfaceBlit>()
        return when (binding) {
            GPUPreparedNativeFrameLateSurfaceBinding.NotRequired ->
                acquiredSurface == null && surfaceScopes.isEmpty()
            is GPUPreparedNativeFrameLateSurfaceBinding.Bound -> {
                val scope = surfaceScopes.singleOrNull() ?: return false
                acquiredSurface != null &&
                    binding.output == acquiredSurface.output &&
                    scope.output == acquiredSurface.output &&
                    binding.target.deviceGeneration == identity.deviceGeneration &&
                    bindLateSurfaceTarget(scope, binding.target)
            }
            is GPUPreparedNativeFrameLateSurfaceBinding.Refused -> false
        }
    }

    internal val lateSurfaceReady: Boolean
        get() = scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.SurfaceBlit>()
            .all(GPUPreparedNativeScopeOperand.SurfaceBlit::isLateSurfaceBound)

    private fun bindLateSurfaceTarget(
        scope: GPUPreparedNativeScopeOperand.SurfaceBlit,
        target: GPUPreparedNativeTextureViewOperand,
    ): Boolean {
        val handle = target.nativeHandle()
        val previous = ownershipByHandle[handle]
        if (previous != null && previous != target.ownership) return false
        if (!scope.bindLateSurface(target)) return false
        ownershipByHandle[handle] = target.ownership
        return true
    }
}

private fun GPUPreparedNativeScopeOperand.declaredOperandDescriptors(): List<
    Pair<GPUPreparedNativeOperandKind, GPUPreparedNativeOperandOwnership>,
> = when (this) {
    is GPUPreparedNativeScopeOperand.SurfaceBlit -> listOf(
        source.nativeKind() to source.ownership,
        GPUPreparedNativeOperandKind.TextureView to GPUPreparedNativeOperandOwnership.Borrowed,
        pipeline.nativeKind() to pipeline.ownership,
        bindGroup.nativeKind() to bindGroup.ownership,
    )
    else -> operands.map { it.nativeKind() to it.ownership }
}

internal fun GPUPreparedNativeOperand.nativeKind(): GPUPreparedNativeOperandKind = when (this) {
    is GPUPreparedNativeTextureOperand -> GPUPreparedNativeOperandKind.Texture
    is GPUPreparedNativeTextureViewOperand -> GPUPreparedNativeOperandKind.TextureView
    is GPUPreparedNativeBufferOperand -> GPUPreparedNativeOperandKind.Buffer
    is GPUPreparedNativeRenderPipelineOperand -> GPUPreparedNativeOperandKind.RenderPipeline
    is GPUPreparedNativeComputePipelineOperand -> GPUPreparedNativeOperandKind.ComputePipeline
    is GPUPreparedNativeBindGroupOperand -> GPUPreparedNativeOperandKind.BindGroup
    is GPUPreparedNativeSamplerOperand -> GPUPreparedNativeOperandKind.Sampler
}

internal fun GPUPreparedNativeOperand.nativeHandle(): AutoCloseable = when (this) {
    is GPUPreparedNativeTextureOperand -> texture
    is GPUPreparedNativeTextureViewOperand -> view
    is GPUPreparedNativeBufferOperand -> buffer
    is GPUPreparedNativeRenderPipelineOperand -> pipeline
    is GPUPreparedNativeComputePipelineOperand -> pipeline
    is GPUPreparedNativeBindGroupOperand -> bindGroup
    is GPUPreparedNativeSamplerOperand -> sampler
}

/** Reusable operands created before the ephemeral surface acquisition. */
internal class GPUPreparedNativeFrameDraft internal constructor(
    val payload: GPUPreparedNativeFramePayload,
) {
    private enum class OwnershipState {
        Draft,
        Adapter,
        Released,
    }

    private var ownershipState = OwnershipState.Draft
    private val pendingOwnedHandles = run {
        val identities = java.util.Collections.newSetFromMap(
            IdentityHashMap<AutoCloseable, Boolean>(),
        )
        (
            payload.scopeOperands.flatMap(GPUPreparedNativeScopeOperand::operands)
                .filter { it.ownership != GPUPreparedNativeOperandOwnership.Borrowed }
                .map(GPUPreparedNativeOperand::nativeHandle) +
                payload.auxiliaryOwnedHandles.map { it.handle }
            )
            .filter(identities::add)
            .toMutableList()
    }

    /** Releases ownership that reached a draft but was refused before adapter registration. */
    @Synchronized
    internal fun disposeBeforeRegistration(): Boolean {
        if (ownershipState != OwnershipState.Draft) return true
        val iterator = pendingOwnedHandles.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (_: Throwable) {
                // Retain only the failed handle so an explicit retry cannot double-close successes.
            }
        }
        return pendingOwnedHandles.isEmpty().also { released ->
            if (released) ownershipState = OwnershipState.Released
        }
    }

    /** Commits the one-way ownership transfer from the draft journal into the adapter registry. */
    @Synchronized
    internal fun transferOwnershipToAdapter(): Boolean {
        if (ownershipState != OwnershipState.Draft) return false
        pendingOwnedHandles.clear()
        ownershipState = OwnershipState.Adapter
        return true
    }

    /** Replaces one draft without leaving two journals able to close the same native handles. */
    internal fun transferOwnershipToDraft(replacement: GPUPreparedNativeFrameDraft): Boolean =
        synchronized(this) {
            synchronized(replacement) {
                if (ownershipState != OwnershipState.Draft ||
                    replacement.ownershipState != OwnershipState.Draft
                ) {
                    return@synchronized false
                }
                val replacementHandles = replacement.pendingOwnedHandles.toList()
                if (pendingOwnedHandles.any { source -> replacementHandles.none { it === source } }) {
                    return@synchronized false
                }
                pendingOwnedHandles.clear()
                ownershipState = OwnershipState.Released
                true
            }
        }

    @Synchronized
    internal fun pendingOwnedHandlesSnapshot(): List<AutoCloseable> = pendingOwnedHandles.toList()

    @Synchronized
    internal fun reservedOwnedHandlesSnapshot(): List<AutoCloseable> {
        val identities = java.util.Collections.newSetFromMap(
            IdentityHashMap<AutoCloseable, Boolean>(),
        )
        return buildList {
            pendingOwnedHandles.forEach { handle ->
                if (identities.add(handle)) add(handle)
                (handle as? GPUPreparedNativeCompletionAnchor)
                    ?.ownedHandlesSnapshot()
                    ?.filter(identities::add)
                    ?.let(::addAll)
            }
        }
    }

    @Synchronized
    internal fun detachPendingOwnedHandles(handles: Collection<AutoCloseable>) {
        pendingOwnedHandles.filterIsInstance<GPUPreparedNativeCompletionAnchor>().forEach { anchor ->
            anchor.detachOwnedHandles(handles)
        }
        pendingOwnedHandles.removeAll { candidate -> handles.any { it === candidate } }
    }

    @Synchronized
    internal fun isAdapterOwned(): Boolean = ownershipState == OwnershipState.Adapter
}

/** Allocation-free result that only attaches an acquired surface target to a reusable draft. */
internal sealed interface GPUPreparedNativeFrameLateSurfaceBinding {
    data object NotRequired : GPUPreparedNativeFrameLateSurfaceBinding
    data class Bound(
        val output: GPUSurfaceOutputRef,
        val target: GPUPreparedNativeTextureViewOperand,
    ) : GPUPreparedNativeFrameLateSurfaceBinding
    data class Refused(val code: String, val message: String) : GPUPreparedNativeFrameLateSurfaceBinding
}

internal sealed interface GPUPreparedNativeFrameBindingResult {
    data object Ready : GPUPreparedNativeFrameBindingResult
    data class Refused(val code: String, val message: String) : GPUPreparedNativeFrameBindingResult
}

internal sealed interface GPUPreparedNativeFrameRegistration {
    data class Registered(
        val ownership: GPUPreparedNativeFrameOwnership,
    ) : GPUPreparedNativeFrameRegistration {
        val token: GPUPreparedNativeFrameToken get() = ownership.token
    }
    data class Refused(
        val code: String,
        val ownership: RefusalOwnership,
    ) : GPUPreparedNativeFrameRegistration

    enum class RefusalOwnership {
        CallerRetained,
        ReleasedOrAdapterQuarantined,
    }
}

internal enum class GPUPreparedNativeOwnerTerminalization {
    CallerRetained,
    ReleasedOrAdapterQuarantined,
}

internal sealed interface GPUPreparedNativeFrameConsumption {
    data class Consumed(val payload: GPUPreparedNativeFramePayload) : GPUPreparedNativeFrameConsumption
    data class Refused(val code: String) : GPUPreparedNativeFrameConsumption
}

/** Typed preflight materializer. It creates payload data, never encoded work or callbacks. */
internal interface GPUPreparedNativeFramePayloadMaterializer {
    fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization

    fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding
}

internal sealed interface GPUPreparedNativeFramePayloadMaterialization {
    data class Materialized(
        val draft: GPUPreparedNativeFrameDraft,
    ) : GPUPreparedNativeFramePayloadMaterialization

    data class Refused(
        val code: String,
        val message: String,
        internal val retainedDraft: GPUPreparedNativeFrameDraft? = null,
        internal val retainedPreRegistrationLedger: GPUPreRegistrationNativeHandleLedger? = null,
        internal val retainedCloseOwner: AutoCloseable? = null,
    ) : GPUPreparedNativeFramePayloadMaterialization
}

/** One-way executor access to the adapter-owned registry. */
internal interface GPUPreparedNativeFramePayloadAccess {
    fun consumePreparedNativeFramePayload(
        token: GPUPreparedNativeFrameToken,
        expectedIdentity: GPUPreparedNativeFrameIdentity,
    ): GPUPreparedNativeFrameConsumption

    fun rollbackPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean
    fun markPreparedNativeFrameSubmitted(token: GPUPreparedNativeFrameToken): Boolean
    fun releasePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean
    fun claimOutputOwnedPreparedNativeFramePayloadMapping(token: GPUPreparedNativeFrameToken): Boolean = false
    fun releaseOutputOwnedPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean = false
    fun quarantinePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean
    fun quarantineOutputOwnedPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean = false
    fun bindLateSurface(
        token: GPUPreparedNativeFrameToken,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
        binding: GPUPreparedNativeFrameLateSurfaceBinding,
    ): GPUPreparedNativeFrameBindingResult
}

/** One registry/token ownership object. It is transferred only into the frame rollback journal. */
internal class GPUPreparedNativeFrameOwnership internal constructor(
    internal val token: GPUPreparedNativeFrameToken,
    private val access: GPUPreparedNativeFramePayloadAccess,
) {
    internal fun consume(identity: GPUPreparedNativeFrameIdentity): GPUPreparedNativeFrameConsumption =
        access.consumePreparedNativeFramePayload(token, identity)
    internal fun rollback(): Boolean = access.rollbackPreparedNativeFramePayload(token)
    internal fun markSubmitted(): Boolean = access.markPreparedNativeFrameSubmitted(token)
    internal fun releaseAfterCompletion(): Boolean = access.releasePreparedNativeFramePayload(token)
    internal fun claimOutputMapping(): Boolean =
        access.claimOutputOwnedPreparedNativeFramePayloadMapping(token)
    internal fun releaseOutputAfterReadback(): Boolean =
        access.releaseOutputOwnedPreparedNativeFramePayload(token)
    internal fun quarantine(): Boolean = access.quarantinePreparedNativeFramePayload(token)
    internal fun quarantineOutputAfterReadback(): Boolean =
        access.quarantineOutputOwnedPreparedNativeFramePayload(token)
    internal fun bindLateSurface(
        acquiredSurface: GPUAcquiredSurfaceOutput?,
        binding: GPUPreparedNativeFrameLateSurfaceBinding,
    ): GPUPreparedNativeFrameBindingResult = access.bindLateSurface(token, acquiredSurface, binding)
}

/** Single capability joining one concrete provider, its exact adapter, and one materializer. */
internal class GPUPreparedNativeFrameBoundary private constructor(
    internal val resourceProvider: GPUConcreteResourceProvider,
    private val adapter: GPURuntimeResourceAdapter,
    private val materializer: GPUPreparedNativeFramePayloadMaterializer,
) {
    internal fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val result = materializer.materializeReusable(
            framePlan,
            encoderPlan,
            resources,
            generationSeal,
        )
        if (result is GPUPreparedNativeFramePayloadMaterialization.Refused) {
            val retainedDraft = result.retainedDraft?.takeIf { draft ->
                releaseOrQuarantineBeforeRegistration(draft) ==
                    GPUPreparedNativeOwnerTerminalization.CallerRetained
            }
            val retainedLedger = result.retainedPreRegistrationLedger?.takeIf { ledger ->
                adapter.releaseOrQuarantinePreRegistrationLedger(ledger) ==
                    GPUPreparedNativeOwnerTerminalization.CallerRetained
            }
            val retainedCloseOwner = result.retainedCloseOwner?.takeIf { owner ->
                !adapter.quarantinePreRegistrationCloseOwner(owner)
            }
            return result.copy(
                retainedDraft = retainedDraft,
                retainedPreRegistrationLedger = retainedLedger,
                retainedCloseOwner = retainedCloseOwner,
            )
        }
        result as GPUPreparedNativeFramePayloadMaterialization.Materialized
        val expected = GPUPreparedNativeFrameIdentity(
            frameId = framePlan.frameId,
            contextIdentity = encoderPlan.contextIdentity,
            encoderPlanId = encoderPlan.planId,
            deviceGeneration = generationSeal.deviceGeneration,
            targetGeneration = generationSeal.targetGeneration,
            scopes = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            },
        )
        return if (result.draft.payload.identity == expected) {
            result
        } else {
            val retainedDraft = result.draft.takeIf { draft ->
                releaseOrQuarantineBeforeRegistration(draft) ==
                    GPUPreparedNativeOwnerTerminalization.CallerRetained
            }
            GPUPreparedNativeFramePayloadMaterialization.Refused(
                "stale.native-frame-payload.identity-mismatch",
                "Native payload scope and operand bridge keys do not match the encoder plan.",
                retainedDraft = retainedDraft,
            )
        }
    }

    internal fun register(draft: GPUPreparedNativeFrameDraft): GPUPreparedNativeFrameRegistration {
        val result = try {
            adapter.registerPreparedNativeFrameDraft(draft)
        } catch (_: Throwable) {
            GPUPreparedNativeFrameRegistration.Refused(
                "failed.native-frame-payload.registration",
                GPUPreparedNativeFrameRegistration.RefusalOwnership.CallerRetained,
            )
        }
        if (result !is GPUPreparedNativeFrameRegistration.Refused ||
            result.ownership != GPUPreparedNativeFrameRegistration.RefusalOwnership.CallerRetained
        ) {
            return result
        }
        return when (releaseOrQuarantineBeforeRegistration(draft)) {
            GPUPreparedNativeOwnerTerminalization.ReleasedOrAdapterQuarantined -> result.copy(
                ownership = GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined,
            )
            GPUPreparedNativeOwnerTerminalization.CallerRetained -> result
        }
    }

    internal fun releaseOrQuarantineBeforeRegistration(
        draft: GPUPreparedNativeFrameDraft,
    ): GPUPreparedNativeOwnerTerminalization =
        adapter.releaseOrQuarantinePreparedNativeFrameDraft(draft)

    internal fun bindLateSurface(
        ownership: GPUPreparedNativeFrameOwnership,
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameBindingResult {
        val binding = materializer.bindLateSurface(draft, acquiredSurface)
        if (binding is GPUPreparedNativeFrameLateSurfaceBinding.Refused) {
            return GPUPreparedNativeFrameBindingResult.Refused(binding.code, binding.message)
        }
        return ownership.bindLateSurface(acquiredSurface, binding)
    }

    internal companion object {
        fun bind(
            adapter: GPURuntimeResourceAdapter,
            resourceProvider: GPUConcreteResourceProvider,
            materializer: GPUPreparedNativeFramePayloadMaterializer,
        ): GPUPreparedNativeFrameBoundary {
            require(resourceProvider.isBackedBy(adapter)) {
                "Native frame boundary requires the exact adapter used by the concrete resource provider"
            }
            return GPUPreparedNativeFrameBoundary(resourceProvider, adapter, materializer)
        }
    }
}

internal fun GPURuntimeResourceAdapter.bindNativeFrameBoundary(
    resourceProvider: GPUConcreteResourceProvider,
    materializer: GPUPreparedNativeFramePayloadMaterializer,
): GPUPreparedNativeFrameBoundary = GPUPreparedNativeFrameBoundary.bind(this, resourceProvider, materializer)
