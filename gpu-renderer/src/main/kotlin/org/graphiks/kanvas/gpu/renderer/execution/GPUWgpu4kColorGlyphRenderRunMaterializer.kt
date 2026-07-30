package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureAspect
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan

internal data class GPUPreparedColorGlyphRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.ColorGlyph>,
    val bindings: List<GPUPreparedTextRenderBinding>,
    val exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    val atlasUploads: List<GPUPreparedTextTextureUploadPlan.Atlas>,
) {
    init {
        require(packets.isNotEmpty() && packets.size == bindings.size)
        require(sourceScopeIndices == exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex))
        require(sourceScopeIndices.distinct().size == sourceScopeIndices.size)
        require(bindings.map(GPUPreparedTextRenderBinding::packetId).distinct().size == bindings.size)
        require(bindings.zip(packets).all { (binding, semantic) ->
            binding.preflightSeal.semanticCanonicalHash == semantic.canonicalHash
        })
        val uploadKeys = exactScopeKeys.filter {
            it.operationKind == GPUEncoderOperationKind.Upload
        }
        require(atlasUploads.map { it.exactScopeKey }.toSet() == uploadKeys.toSet())
        val requiredAtlases = bindings
            .map(GPUPreparedTextRenderBinding::atlasResourcePlan)
            .distinctByIdentity()
        val suppliedAtlases = atlasUploads
            .map(GPUPreparedTextTextureUploadPlan.Atlas::resourcePlan)
            .distinctByIdentity()
        require(requiredAtlases.size == suppliedAtlases.size &&
            requiredAtlases.all { required -> suppliedAtlases.any { it === required } }
        )
    }
}

internal class GPUWgpu4kPreparedR8FrameResources(
    internal val texturesByPlan: IdentityHashMap<GPUR8FrameResourcePlan, NativeTexture>,
    val uploadOperands: List<GPUPreparedNativeScopeOperand.TextureUpload>,
    val ownedResources: GPUPreparedRenderRunOwnedResources,
)

internal sealed interface GPUWgpu4kPreparedR8FrameMaterialization {
    data class Ready(val resources: GPUWgpu4kPreparedR8FrameResources) :
        GPUWgpu4kPreparedR8FrameMaterialization

    data class Refused(
        val code: String,
        val message: String,
        val retainedCloseOwner: AutoCloseable? = null,
    ) : GPUWgpu4kPreparedR8FrameMaterialization
}

/**
 * Creates each generic R8 upload page exactly once for one frame. TextA8 and ColorGlyph borrow the
 * resulting views; the texture operands remain payload-owned until queue completion.
 */
internal class GPUWgpu4kPreparedR8FrameMaterializer(
    private val device: GPUDevice,
) {
    fun materializeAcceptedUploads(
        uploads: List<GPUPreparedTextTextureUploadPlan.Atlas>,
        generation: GPUDeviceGenerationID,
    ): GPUWgpu4kPreparedR8FrameMaterialization {
        val created = mutableListOf<AutoCloseable>()
        return try {
            val textures = IdentityHashMap<GPUR8FrameResourcePlan, NativeTexture>()
            val operands = uploads.map { upload ->
                val plan = upload.resourcePlan
                require(!textures.containsKey(plan)) {
                    "One accepted R8 resource plan may be uploaded only once per frame"
                }
                val texture = device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(
                            plan.artifactWidth.toUInt(),
                            plan.artifactHeight.toUInt(),
                            1u,
                        ),
                        format = GPUTextureFormat.R8Unorm,
                        usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                        mipLevelCount = 1u,
                        sampleCount = 1u,
                        label = "Kanvas.frame.preparedText.r8-page",
                    ),
                ).track(created)
                val view = texture.createView(
                    TextureViewDescriptor(
                        format = GPUTextureFormat.R8Unorm,
                        dimension = GPUTextureViewDimension.TwoD,
                        usage = GPUTextureUsage.TextureBinding,
                        aspect = GPUTextureAspect.All,
                        baseMipLevel = 0u,
                        mipLevelCount = 1u,
                        baseArrayLayer = 0u,
                        arrayLayerCount = 1u,
                        label = "Kanvas.frame.preparedText.r8-page-view",
                    ),
                ).track(created)
                textures[plan] = NativeTexture(texture, view)
                preparedTextTextureUpload(
                    role = "prepared-text-r8",
                    scope = upload.exactScopeKey,
                    bytes = plan.bytesForUpload(),
                    width = plan.artifactWidth,
                    height = plan.artifactHeight,
                    bytesPerRow = plan.uploadTaskLayout.bytesPerRow,
                    rowsPerImage = plan.uploadTaskLayout.rowsPerImage,
                    texture = texture,
                    generation = generation,
                )
            }
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUWgpu4kPreparedR8FrameMaterialization.Ready(
                GPUWgpu4kPreparedR8FrameResources(
                    texturesByPlan = textures,
                    uploadOperands = immutableList(operands),
                    ownedResources = owner,
                ),
            )
        } catch (failure: Throwable) {
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            val rollbackFailure = runCatching(owner::close).exceptionOrNull()
            GPUWgpu4kPreparedR8FrameMaterialization.Refused(
                code = "failed.prepared_text.r8-materialization",
                message = "Prepared R8 frame materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
                retainedCloseOwner = owner.takeIf { rollbackFailure != null },
            )
        }
    }
}

/** Materializes target-free ColorGlyph runs with the distinct COLRv0 shader and indexed ABI. */
internal class GPUWgpu4kColorGlyphRenderRunMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val sessionCache: GPUWgpu4kColorGlyphSessionCache,
) {
    fun materializeAcceptedRun(
        plan: GPUPreparedColorGlyphRenderRunPlan,
        preparedR8Resources: GPUWgpu4kPreparedR8FrameResources,
        generation: GPUDeviceGenerationID,
    ): GPUPreparedRenderRunMaterialization {
        val created = mutableListOf<AutoCloseable>()
        return try {
            val invariants = sessionCache.acquire()
            val renderScopes = plan.exactScopeKeys.filter {
                it.operationKind == GPUEncoderOperationKind.Render
            }
            require(renderScopes.size == plan.packets.size)
            val runs = plan.packets.indices.map { index ->
                val semantic = plan.packets[index]
                val binding = plan.bindings[index]
                val atlas = preparedR8Resources.texturesByPlan[binding.atlasResourcePlan]
                    ?: error("Accepted ColorGlyph atlas has no frame-local R8 resource")
                val vertexBytes = semantic.vertexData.size.toLong() * Float.SIZE_BYTES
                val indexBytes = semantic.indexData.size.toLong() * Int.SIZE_BYTES
                val uniformBytes = semantic.uniformBytes.size.toLong()
                val vertexBuffer = device.createBuffer(
                    BufferDescriptor(
                        size = vertexBytes.toULong(),
                        usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.colorGlyph.vertices",
                    ),
                ).track(created).also { buffer ->
                    queue.writeBuffer(
                        buffer,
                        0uL,
                        ArrayBuffer.of(semantic.vertexData.toFloatArray()),
                    )
                }
                val indexBuffer = device.createBuffer(
                    BufferDescriptor(
                        size = indexBytes.toULong(),
                        usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.colorGlyph.indices",
                    ),
                ).track(created).also { buffer ->
                    queue.writeBuffer(
                        buffer,
                        0uL,
                        ArrayBuffer.of(semantic.indexData.toIntArray()),
                    )
                }
                val uniformBuffer = device.createBuffer(
                    BufferDescriptor(
                        size = uniformBytes.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.colorGlyph.uniform784",
                    ),
                ).track(created).also { buffer ->
                    queue.writeBuffer(
                        buffer,
                        0uL,
                        ArrayBuffer.of(semantic.uniformBytes.map(Int::toByte).toByteArray()),
                    )
                }
                val bindGroup = device.createBindGroup(
                    BindGroupDescriptor(
                        label = "Kanvas.frame.colorGlyph.bindGroup0",
                        layout = invariants.bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(
                                binding = 0u,
                                resource = BufferBinding(
                                    buffer = uniformBuffer,
                                    offset = 0uL,
                                    size = uniformBytes.toULong(),
                                ),
                            ),
                            BindGroupEntry(binding = 1u, resource = atlas.view),
                            BindGroupEntry(binding = 2u, resource = invariants.sampler),
                        ),
                    ),
                ).track(created)
                val vertexOperand = GPUPreparedNativeBufferOperand(
                    vertexBuffer,
                    generation,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    vertexBytes,
                )
                val indexOperand = GPUPreparedNativeBufferOperand(
                    indexBuffer,
                    generation,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    indexBytes,
                )
                GPUPreparedNativeScopeOperand.PreparedColorGlyphRenderRun(
                    sourceStepIndex = renderScopes[index].sourceStepIndex,
                    commands = listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            GPUPreparedNativeRenderPipelineOperand(
                                invariants.pipeline,
                                generation,
                                GPUPreparedNativeOperandOwnership.Borrowed,
                            ),
                        ),
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            GPUPreparedNativeBindGroupOperand(
                                bindGroup,
                                generation,
                                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                            ),
                        ),
                        GPUPreparedNativeRenderCommand.SetVertexBuffer(
                            slot = 0,
                            buffer = vertexOperand,
                            offset = 0L,
                            size = vertexBytes,
                            vertexStrideBytes = 16L,
                        ),
                        GPUPreparedNativeRenderCommand.SetIndexBuffer(
                            indexOperand,
                            GPUPreparedNativeIndexFormat.Uint32,
                            0L,
                            indexBytes,
                        ),
                        GPUPreparedNativeRenderCommand.SetScissor(
                            semantic.scissorBounds.left,
                            semantic.scissorBounds.top,
                            semantic.scissorBounds.width,
                            semantic.scissorBounds.height,
                        ),
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = semantic.indexData.size,
                            ),
                        ),
                    ),
                    exactOperandKeys = renderScopes[index].operandKeys,
                    semanticPayloads = listOf(semantic),
                )
            }
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(runs),
                uniformUploads = emptyList(),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            val rollbackFailure = runCatching(owner::close).exceptionOrNull()
            GPUPreparedRenderRunMaterialization.Refused(
                code = "failed.color_glyph.materialization",
                message = "Prepared ColorGlyph native materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
                facts = mapOf("boundary" to "native"),
                retainedCloseOwner = owner.takeIf { rollbackFailure != null },
            )
        }
    }
}

private fun <T : Any> List<T>.distinctByIdentity(): List<T> {
    val identities = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
    return filter(identities::add)
}

private fun <T : AutoCloseable> T.track(handles: MutableList<AutoCloseable>): T =
    also(handles::add)
