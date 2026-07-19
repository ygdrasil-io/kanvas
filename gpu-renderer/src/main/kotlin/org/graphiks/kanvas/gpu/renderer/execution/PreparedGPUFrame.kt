package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayout

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolMaintenanceDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.requireResourceDumpSafe

/** Current handle-free generations against which a semantic frame is prepared. */
class GPUFramePreflightContext(
    val targetId: String,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    resourceGenerations: Map<GPUFrameResourceRef, Long>,
    val surfaceGeneration: Long = targetGeneration,
) {
    val resourceGenerations: Map<GPUFrameResourceRef, Long> = immutableMap(resourceGenerations)

    init {
        requireExecutionDumpSafe("GPUFramePreflightContext.targetId", targetId)
        require(targetGeneration >= 0L) { "GPUFramePreflightContext.targetGeneration must be non-negative" }
        require(surfaceGeneration >= 0L) { "GPUFramePreflightContext.surfaceGeneration must be non-negative" }
        require(resourceGenerations.values.none { it < 0L }) {
            "GPUFramePreflightContext.resourceGenerations must be non-negative"
        }
    }
}

enum class GPUEncoderOperationKind {
    Render,
    Compute,
    Upload,
    Copy,
    CopyDestination,
    CopyAsDraw,
    Readback,
    SurfaceBlit,
}

/** One handle-free encoder scope mapped to exactly one encodable semantic step. */
class GPUCommandEncoderScopePlan internal constructor(
    val sourceStepIndex: Int,
    val operationKind: GPUEncoderOperationKind,
    val scopeLabel: String,
    sourceTaskIds: List<GPUTaskID>,
    sourcePacketIds: List<GPUDrawPacketID>,
    facadeOperationClasses: List<String>,
    val targetGeneration: Long,
    resourceGenerationLabels: List<String>,
    val passCommandStream: GPUPassCommandStream?,
    internal val corePrimitiveDirectNativeRouteSeal: GPUCorePrimitiveDirectNativeRouteSeal,
    internal val corePrimitivePathStencilNativeRouteSeal: GPUCorePrimitivePathStencilNativeRouteSeal =
        if (operationKind == GPUEncoderOperationKind.Render) {
            GPUCorePrimitivePathStencilNativeRouteSeal.Empty
        } else {
            GPUCorePrimitivePathStencilNativeRouteSeal.Missing
        },
    internal val corePrimitiveNativeScopeRouteSeal: GPUCorePrimitiveNativeScopeRouteSeal =
        if (operationKind == GPUEncoderOperationKind.Render) {
            GPUCorePrimitiveNativeScopeRouteSeal.Empty
        } else {
            GPUCorePrimitiveNativeScopeRouteSeal.Missing
        },
    internal val corePrimitiveClipStencilPreparedRouteSeal:
        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal =
        if (operationKind == GPUEncoderOperationKind.Render) {
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
        } else {
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing
        },
) {
    constructor(
        sourceStepIndex: Int,
        operationKind: GPUEncoderOperationKind,
        scopeLabel: String = "step.$sourceStepIndex",
        sourceTaskIds: List<GPUTaskID>,
        sourcePacketIds: List<GPUDrawPacketID> = emptyList(),
        facadeOperationClasses: List<String>,
        targetGeneration: Long,
        resourceGenerationLabels: List<String>,
        passCommandStream: GPUPassCommandStream? = null,
    ) : this(
        sourceStepIndex = sourceStepIndex,
        operationKind = operationKind,
        scopeLabel = scopeLabel,
        sourceTaskIds = sourceTaskIds,
        sourcePacketIds = sourcePacketIds,
        facadeOperationClasses = facadeOperationClasses,
        targetGeneration = targetGeneration,
        resourceGenerationLabels = resourceGenerationLabels,
        passCommandStream = passCommandStream,
        corePrimitiveDirectNativeRouteSeal = if (operationKind == GPUEncoderOperationKind.Render) {
            GPUCorePrimitiveDirectNativeRouteSeal.Empty
        } else {
            GPUCorePrimitiveDirectNativeRouteSeal.Missing
        },
    )

    val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
    val sourcePacketIds: List<GPUDrawPacketID> = immutableList(sourcePacketIds)
    val facadeOperationClasses: List<String> = immutableList(facadeOperationClasses)
    val resourceGenerationLabels: List<String> = immutableList(resourceGenerationLabels)
    internal var nativeOperandKeys: List<GPUPreparedNativeOperandKey> = emptyList()
        private set

    internal fun attachNativeOperandKeys(keys: List<GPUPreparedNativeOperandKey>): GPUCommandEncoderScopePlan {
        check(nativeOperandKeys.isEmpty()) { "Native operand keys are already attached" }
        require(keys.isNotEmpty()) { "Native operand keys must not be empty" }
        val pathSealed = corePrimitivePathStencilNativeRouteSeal is
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs
        val clipStencilSeal = corePrimitiveClipStencilPreparedRouteSeal
        val clipStencilSealed =
            clipStencilSeal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                clipStencilSeal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
        require(!(pathSealed && clipStencilSealed)) {
            "PathDepthStencil and ClipDepthStencil native operand seals are mutually exclusive"
        }
        val depthStencilKeys = keys.filter {
            it.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget
        }
        require(
            if (pathSealed || clipStencilSealed) {
                depthStencilKeys.size == 1 &&
                    depthStencilKeys.single().kind == GPUPreparedNativeOperandKind.TextureView &&
                    depthStencilKeys.single().ownership == GPUPreparedNativeOperandOwnership.Borrowed
            } else {
                depthStencilKeys.isEmpty()
            },
        ) {
            "A sealed stencil route requires exactly one borrowed depth/stencil texture-view operand, and other scopes forbid it"
        }
        if (clipStencilSealed) {
            val seal = when (clipStencilSeal) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> clipStencilSeal
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> clipStencilSeal
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> error("Unreachable clip-stencil seal")
            }
            val isProducer = seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
            val packetId = when (seal) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> seal.packetId
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> seal.packetId
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> error("Unreachable clip-stencil seal")
            }
            val sourceIndex = when (seal) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> seal.sourceStepIndex
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> seal.sourceStepIndex
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> error("Unreachable clip-stencil seal")
            }
            val slabs = when (seal) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> seal.slabAuthority
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> seal.slabAuthority
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> error("Unreachable clip-stencil seal")
            }
            val attachment = when (seal) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> seal.attachmentAuthority
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> seal.attachmentAuthority
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> error("Unreachable clip-stencil seal")
            }
            fun resourceLabel(resource: GPUFrameResourceRef, generation: Long): String =
                "${resource::class.simpleName}:${resource.value}@$generation"
            val expectedResourceLabels = buildList {
                add(resourceGenerationLabels.first())
                add(resourceLabel(slabs.vertexResource, slabs.vertexGeneration))
                add(resourceLabel(slabs.indexResource, slabs.indexGeneration))
                if (!isProducer) {
                    add(resourceLabel(slabs.uniformResource, slabs.uniformGeneration))
                }
                add(resourceLabel(attachment.resource, attachment.resourceGeneration))
            }
            val expectedKeys = buildList {
                add(GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    gpuPreparedNativeBindingKey(resourceGenerationLabels.first()),
                ))
                add(GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    gpuPreparedNativeBindingKey(
                        resourceLabel(attachment.resource, attachment.resourceGeneration),
                    ),
                ))
                add(GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    gpuPreparedNativeBindingKey(
                        "setRenderPipeline:setRenderPipeline.${packetId.value}",
                    ),
                ))
                add(GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey(
                        resourceLabel(slabs.vertexResource, slabs.vertexGeneration),
                    ),
                ))
                add(GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey(
                        resourceLabel(slabs.indexResource, slabs.indexGeneration),
                    ),
                ))
                if (!isProducer) {
                    add(GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.RenderBindGroup,
                        GPUPreparedNativeOperandKind.BindGroup,
                        gpuPreparedNativeBindingKey(
                            "setBindGroup:setBindGroup.${packetId.value}",
                        ),
                    ))
                }
            }
            require(sourceStepIndex == sourceIndex && sourcePacketIds == listOf(packetId)) {
                "Clip-stencil native operands require the exact sealed scope and packet identity"
            }
            require(resourceGenerationLabels.size == expectedResourceLabels.size &&
                resourceGenerationLabels.toSet() == expectedResourceLabels.toSet()
            ) {
                "Clip-stencil native operands require the exact typed resource generations"
            }
            require(keys == expectedKeys) {
                "Clip-stencil native operands differ from the exact producer or consumer seal"
            }
            val expectedBridgeKinds = buildList {
                add(org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline)
                if (!isProducer) {
                    add(org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup)
                }
                add(org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer)
                add(org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer)
            }
            require(requireNotNull(passCommandStream).operandBridge.map { it.operand.kind } ==
                expectedBridgeKinds
            ) {
                "Clip-stencil pass operands differ from the exact producer or consumer packet bridge"
            }
        }
        nativeOperandKeys = immutableList(keys)
        return this
    }

    init {
        require(sourceStepIndex >= 0) { "GPUCommandEncoderScopePlan.sourceStepIndex must be non-negative" }
        require(scopeLabel.isNotBlank()) { "GPUCommandEncoderScopePlan.scopeLabel must not be blank" }
        require(sourceTaskIds.isNotEmpty()) { "GPUCommandEncoderScopePlan.sourceTaskIds must not be empty" }
        require(facadeOperationClasses.isNotEmpty()) {
            "GPUCommandEncoderScopePlan.facadeOperationClasses must not be empty"
        }
        require(targetGeneration >= 0L) { "GPUCommandEncoderScopePlan.targetGeneration must be non-negative" }
        require((operationKind == GPUEncoderOperationKind.Render) == (passCommandStream != null)) {
            "Only a Render encoder scope must retain a materialized GPUPassCommandStream"
        }
        require(
            (operationKind == GPUEncoderOperationKind.Render) ==
                (corePrimitiveDirectNativeRouteSeal !== GPUCorePrimitiveDirectNativeRouteSeal.Missing),
        ) { "Only Render encoder scopes retain a CorePrimitive direct native route seal" }
        require(
            (operationKind == GPUEncoderOperationKind.Render) ==
                (corePrimitivePathStencilNativeRouteSeal !==
                    GPUCorePrimitivePathStencilNativeRouteSeal.Missing),
        ) { "Only Render encoder scopes retain a CorePrimitive path native route seal" }
        require(
            (operationKind == GPUEncoderOperationKind.Render) ==
                (corePrimitiveClipStencilPreparedRouteSeal !==
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing),
        ) { "Only Render encoder scopes retain a CorePrimitive clip-stencil prepared route seal" }
        require(
            (operationKind == GPUEncoderOperationKind.Render) ==
                (corePrimitiveNativeScopeRouteSeal !== GPUCorePrimitiveNativeScopeRouteSeal.Missing),
        ) { "Only Render encoder scopes retain a unified CorePrimitive native route seal" }
        if (corePrimitiveDirectNativeRouteSeal is GPUCorePrimitiveDirectNativeRouteSeal.Routes) {
            require(this.sourcePacketIds.containsOrderedSubsequence(
                corePrimitiveDirectNativeRouteSeal.routesByPacketId.keys.toList(),
            )) {
                "Direct CorePrimitive route identities must be an ordered render-packet subset"
            }
        }
        val pathPacketIds = when (corePrimitivePathStencilNativeRouteSeal) {
            is GPUCorePrimitivePathStencilNativeRouteSeal.Pairs ->
                corePrimitivePathStencilNativeRouteSeal.flattenedPacketIds
            GPUCorePrimitivePathStencilNativeRouteSeal.Empty,
            GPUCorePrimitivePathStencilNativeRouteSeal.Missing,
            -> emptyList()
        }
        require(this.sourcePacketIds.containsOrderedSubsequence(pathPacketIds)) {
            "Path CorePrimitive route identities must be an ordered render-packet subset"
        }
        val pathSealed = corePrimitivePathStencilNativeRouteSeal is
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs
        val unifiedContainsPath = (
            corePrimitiveNativeScopeRouteSeal as? GPUCorePrimitiveNativeScopeRouteSeal.Routes
            )?.orderedUnits?.any { unit ->
            unit is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
        } == true
        require(pathSealed == unifiedContainsPath) {
            "Path and unified CorePrimitive seals must agree on path-pair ownership"
        }
        if (corePrimitiveNativeScopeRouteSeal is GPUCorePrimitiveNativeScopeRouteSeal.Routes) {
            require(corePrimitiveNativeScopeRouteSeal.flattenedPacketIds == this.sourcePacketIds) {
                "Unified CorePrimitive route identities must exactly match render packet identities"
            }
            val directPacketIds = when (corePrimitiveDirectNativeRouteSeal) {
                is GPUCorePrimitiveDirectNativeRouteSeal.Routes ->
                    corePrimitiveDirectNativeRouteSeal.routesByPacketId.keys.toList()
                GPUCorePrimitiveDirectNativeRouteSeal.Empty,
                GPUCorePrimitiveDirectNativeRouteSeal.Missing,
                -> emptyList()
            }
            require(
                (directPacketIds + pathPacketIds).toSet() == this.sourcePacketIds.toSet() &&
                    directPacketIds.toSet().intersect(pathPacketIds.toSet()).isEmpty(),
            ) { "Derived CorePrimitive route seals must exactly partition the unified seal" }
        }
    }
}

private fun <T> List<T>.containsOrderedSubsequence(subsequence: List<T>): Boolean {
    var next = 0
    forEach { value ->
        if (next < subsequence.size && value == subsequence[next]) next += 1
    }
    return next == subsequence.size
}

enum class GPUPreparedStepLane {
    ResourcePreflight,
    Encoder,
    Dependency,
    RefusalEvidence,
    HostAction,
}

data class GPUPreparedStepEvidence(
    val sourceStepIndex: Int,
    val lane: GPUPreparedStepLane,
    val kind: String,
)

data class GPUPreparedDependencyEvidence(
    val sourceStepIndex: Int,
    val kind: String,
    val reasonCode: String,
)

enum class GPUHostActionKind {
    AcquireSurface,
    Present,
}

data class GPUFrameHostAction(
    val sourceStepIndex: Int,
    val kind: GPUHostActionKind,
    val output: GPUSurfaceOutputRef,
)

/** Deep immutable resource evidence; it never retains a mutable provider decision. */
data class GPUPreparedResourceEvidence(
    val logicalResource: GPUFrameResourceRef,
    val concreteResource: GPUPreparedConcreteResourceRef,
    val role: GPUFrameResourceRole,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceGeneration: Long,
    val textureAllocation: org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedTextureAllocationEvidence? = null,
) {
    init {
        require(textureAllocation == null || concreteResource is GPUPreparedConcreteResourceRef.Texture) {
            "Only prepared texture evidence may retain a texture allocation"
        }
    }
}

/** Readback state whose ownership survives ordinary GPU completion until map/depad/unmap. */
data class GPUPreparedReadbackOutput(
    val stagingResource: GPUFrameResourceRef,
    val concreteResource: GPUPreparedConcreteResourceRef.Buffer,
    val resourceGeneration: Long,
    val request: GPUFrameReadbackRequest,
    val layout: GPUReadbackLayout,
    val stagingLease: GPUReadbackStagingLease,
)

class GPUPreparedResourceSet(
    ordinaryResources: List<GPUPreparedResourceEvidence>,
    outputOwnedReadbacks: List<GPUPreparedReadbackOutput>,
    commandResourceLeases: List<GPUResourceLease> = emptyList(),
    commandTextureResources: List<GPUTextureResourceRef> = emptyList(),
    commandBufferResources: List<GPUBufferResourceRef> = emptyList(),
    commandDiagnostics: List<GPUResourceDiagnostic> = emptyList(),
) {
    val ordinaryResources: List<GPUPreparedResourceEvidence> = immutableList(ordinaryResources)
    val outputOwnedReadbacks: List<GPUPreparedReadbackOutput> = immutableList(outputOwnedReadbacks)
    val commandResourceLeases: List<GPUPreparedCommandResourceLease> = immutableList(
        commandResourceLeases.map(::GPUPreparedCommandResourceLease),
    )
    val commandTextureResources: List<GPUTextureResourceRef> = immutableList(commandTextureResources)
    val commandBufferResources: List<GPUBufferResourceRef> = immutableList(commandBufferResources)
    val commandDiagnostics: List<GPUPreparedCommandDiagnostic> = immutableList(
        commandDiagnostics.map(::GPUPreparedCommandDiagnostic),
    )

    init {
        require(
            ordinaryResources.none { it.role == GPUFrameResourceRole.ReadbackStaging },
        ) { "Readback staging must remain output-owned, never ordinary pooled state" }
        val ordinaryRefs = ordinaryResources.map { it.logicalResource }
        val outputRefs = outputOwnedReadbacks.map { it.stagingResource }
        require(ordinaryRefs.distinct().size == ordinaryRefs.size) {
            "GPUPreparedResourceSet.ordinaryResources must have unique logical refs"
        }
        require(outputRefs.distinct().size == outputRefs.size) {
            "GPUPreparedResourceSet.outputOwnedReadbacks must have unique staging refs"
        }
        require((ordinaryRefs.toSet() intersect outputRefs.toSet()).isEmpty()) {
            "GPUPreparedResourceSet ordinary and output-owned refs must be disjoint"
        }
        commandTextureResources.forEach { resource ->
            requireResourceDumpSafe("GPUPreparedResourceSet.commandTextureResources", resource.value)
        }
        commandBufferResources.forEach { resource ->
            requireResourceDumpSafe("GPUPreparedResourceSet.commandBufferResources", resource.value)
        }
    }
}

/** Deep immutable non-terminal evidence returned with command-resource materialization. */
class GPUPreparedCommandDiagnostic(diagnostic: GPUResourceDiagnostic) {
    val code: String = diagnostic.code
    val resourceLabel: String = diagnostic.resourceLabel
    val message: String = diagnostic.message
    val facts: Map<String, String> = immutableMap(diagnostic.facts)

    init {
        require(!diagnostic.terminal) {
            "Terminal command diagnostics cannot be retained by a prepared frame"
        }
        requireResourceDumpSafe("GPUPreparedCommandDiagnostic.code", code)
        requireResourceDumpSafe("GPUPreparedCommandDiagnostic.resourceLabel", resourceLabel)
        requireResourceDumpSafe("GPUPreparedCommandDiagnostic.message", message)
        facts.forEach { (key, value) ->
            requireResourceDumpSafe("GPUPreparedCommandDiagnostic.facts key", key)
            requireResourceDumpSafe("GPUPreparedCommandDiagnostic.facts value", value)
        }
    }
}

/** Deep immutable command-resource lifetime retained globally through submit/completion. */
class GPUPreparedCommandResourceLease(lease: GPUResourceLease) {
    val leaseId: String = lease.leaseId
    val resourceKind: GPUResourceLeaseKind = lease.resourceKind
    val deviceGeneration: Long = lease.deviceGeneration
    val descriptorHash: String = lease.descriptorHash
    val ownerScope: String = lease.ownerScope
    val usageLabels: List<String> = immutableList(lease.usageLabels)
    val releasePolicy: String = lease.releasePolicy
    val cacheResult: GPUResourceLeaseCacheResult = lease.cacheResult
    val evidenceFacts: Map<String, String> = immutableMap(lease.evidenceFacts)
}

class GPUPreparedGenerationSeal(
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    resourceGenerations: Map<GPUFrameResourceRef, Long>,
    val capabilitySealHash: String,
) {
    val resourceGenerations: Map<GPUFrameResourceRef, Long> = immutableMap(resourceGenerations)

    init {
        require(targetGeneration >= 0L) { "GPUPreparedGenerationSeal.targetGeneration must be non-negative" }
        require(resourceGenerations.values.none { it < 0L }) {
            "GPUPreparedGenerationSeal.resourceGenerations must be non-negative"
        }
        require(capabilitySealHash.isNotBlank()) { "GPUPreparedGenerationSeal.capabilitySealHash must not be blank" }
    }
}

enum class GPUSurfaceAcquisitionStatus {
    Lost,
    Outdated,
    Timeout,
    OutOfMemory,
    DeviceLost,
    DependencyUnavailable,
}

data class GPUSurfaceAcquisitionRequest(
    val descriptor: GPUSurfaceOutputDescriptor,
    val deviceGeneration: GPUDeviceGenerationID,
)

data class GPUAcquiredSurfaceOutput(
    val output: GPUSurfaceOutputRef,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    val evidenceLabel: String,
) {
    init {
        require(targetGeneration >= 0L) { "GPUAcquiredSurfaceOutput.targetGeneration must be non-negative" }
        requireExecutionDumpSafe("GPUAcquiredSurfaceOutput.evidenceLabel", evidenceLabel)
    }
}

sealed interface GPUSurfaceAcquisitionResult {
    data class Acquired(val output: GPUAcquiredSurfaceOutput) : GPUSurfaceAcquisitionResult
    data class Unavailable(val status: GPUSurfaceAcquisitionStatus) : GPUSurfaceAcquisitionResult
}

sealed interface GPUSurfaceReleaseResult {
    data object Released : GPUSurfaceReleaseResult
    data class Failed(val diagnostic: GPUDiagnostic) : GPUSurfaceReleaseResult
}

interface GPUSurfaceOutputProvider {
    fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult
    fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult
}

class GPUFrameRollbackResult(
    releaseOrder: List<String>,
    diagnostics: List<GPUDiagnostic>,
) {
    val releaseOrder: List<String> = immutableList(releaseOrder)
    val diagnostics: List<GPUDiagnostic> = immutableList(diagnostics.map { it.preflightSnapshot() })
    val successful: Boolean get() = diagnostics.isEmpty()
}

/** One-shot rollback retained by a prepared-but-never-submitted frame. */
class GPUFrameRollback internal constructor(
    private val ownerScope: String,
    private val resourceProvider: GPUFrameResourcePreflightProvider,
    private val surfaceProvider: GPUSurfaceOutputProvider,
    acquiredSurfaceOutput: GPUAcquiredSurfaceOutput? = null,
    nativePayloadOwnership: GPUPreparedNativeFrameOwnership? = null,
    private val completionProvider: GPUQueueCompletionProvider? = null,
    completionTicket: GPUQueueCompletionTicket? = null,
) {
    private enum class State { Open, SubmitEntered, RolledBack }

    private var state = State.Open
    private var acquiredSurfaceOutput: GPUAcquiredSurfaceOutput? = acquiredSurfaceOutput
    private var nativePayloadOwnership: GPUPreparedNativeFrameOwnership? = nativePayloadOwnership
    private var completionTicket: GPUQueueCompletionTicket? = completionTicket
    private var result: GPUFrameRollbackResult? = null

    init {
        require(completionTicket == null || completionProvider != null) {
            "GPUFrameRollback cannot own a completion ticket without its completion provider"
        }
    }

    internal val hasNativePayload: Boolean
        @Synchronized get() = nativePayloadOwnership != null

    @Synchronized
    internal fun ownsCompletionTicket(ticket: GPUQueueCompletionTicket): Boolean =
        completionTicket === ticket

    @Synchronized
    internal fun adoptNativePayload(ownership: GPUPreparedNativeFrameOwnership): Boolean {
        if (state != State.Open || nativePayloadOwnership != null) return false
        nativePayloadOwnership = ownership
        return true
    }

    @Synchronized
    internal fun adoptCompletionTicket(ticket: GPUQueueCompletionTicket): Boolean {
        if (state != State.Open || completionProvider == null || completionTicket != null) return false
        completionTicket = ticket
        return true
    }

    @Synchronized
    internal fun adoptSurface(output: GPUAcquiredSurfaceOutput): Boolean {
        if (state != State.Open || acquiredSurfaceOutput != null) return false
        acquiredSurfaceOutput = output
        return true
    }

    @Synchronized
    internal fun consumeNativePayload(
        identity: GPUPreparedNativeFrameIdentity,
    ): GPUPreparedNativeFrameConsumption {
        val ownership = nativePayloadOwnership ?: return GPUPreparedNativeFrameConsumption.Refused(
            "unsupported.native-frame-payload.token-missing",
        )
        return try {
            ownership.consume(identity)
        } catch (_: Throwable) {
            GPUPreparedNativeFrameConsumption.Refused(
                "failed.native-frame-payload.access",
            )
        }
    }

    @Synchronized
    internal fun enterSubmit(): GPUPreparedNativeFrameBindingResult {
        if (state != State.Open) return GPUPreparedNativeFrameBindingResult.Refused(
            "failed.frame-execution.submit-ownership",
            "Frame rollback ownership is no longer open.",
        )
        val ownership = nativePayloadOwnership
        if (ownership != null) {
            val marked = try {
                ownership.markSubmitted()
            } catch (_: Throwable) {
                false
            }
            if (!marked) return GPUPreparedNativeFrameBindingResult.Refused(
                "failed.native-frame-payload.submit-transition",
                "Native payload could not enter submitted ownership.",
            )
        }
        state = State.SubmitEntered
        return GPUPreparedNativeFrameBindingResult.Ready
    }

    @Synchronized
    internal fun releaseNativeAfterCompletion(): Boolean = try {
        nativePayloadOwnership?.releaseAfterCompletion() ?: true
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    internal fun claimNativeReadbackMapping(): Boolean = try {
        nativePayloadOwnership?.claimOutputMapping() ?: true
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    internal fun releaseNativeReadbackAfterOutput(): Boolean = try {
        nativePayloadOwnership?.releaseOutputAfterReadback() ?: true
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    internal fun quarantineNativeReadbackAfterOutput(): Boolean = try {
        nativePayloadOwnership?.quarantineOutputAfterReadback() ?: true
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    internal fun quarantineNativeAfterSubmit(): Boolean = try {
        nativePayloadOwnership?.quarantine() ?: true
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    fun execute(): GPUFrameRollbackResult {
        result?.let { return it }
        if (state == State.SubmitEntered) {
            return GPUFrameRollbackResult(
                emptyList(),
                listOf(
                    preflightDiagnostic(
                        "failed.preflight.rollback_after_submit_entry",
                        "Pre-submit rollback is forbidden after entering queue submission.",
                    ),
                ),
            ).also { result = it }
        }
        state = State.RolledBack
        val releases = mutableListOf<String>()
        val diagnostics = mutableListOf<GPUDiagnostic>()
        acquiredSurfaceOutput?.let { output ->
            try {
                when (val release = surfaceProvider.release(output)) {
                    GPUSurfaceReleaseResult.Released -> releases += "surface:${output.output.value}"
                    is GPUSurfaceReleaseResult.Failed -> {
                        releases += "surface:${output.output.value}:failed"
                        diagnostics += release.diagnostic
                    }
                }
            } catch (failure: Throwable) {
                releases += "surface:${output.output.value}:failed"
                diagnostics += preflightDiagnostic(
                    "failed.preflight.surface_release",
                    "Surface release failed during pre-submit rollback.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                )
            }
        }
        completionTicket?.let { ticket ->
            try {
                when (completionProvider?.abandonReservedTicket(ticket)) {
                    is GPUQueueCompletionTicketAbandonResult.Abandoned ->
                        releases += "completion-ticket:${ticket.ticketId.value}"
                    else -> {
                        releases += "completion-ticket:${ticket.ticketId.value}:failed"
                        diagnostics += preflightDiagnostic(
                            "failed.preflight.completion_ticket_abandon",
                            "Reserved completion ticket could not be abandoned before submit.",
                        )
                    }
                }
            } catch (failure: Throwable) {
                releases += "completion-ticket:${ticket.ticketId.value}:failed"
                diagnostics += preflightDiagnostic(
                    "failed.preflight.completion_ticket_abandon",
                    "Completion ticket abandonment failed without a typed result.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                )
            }
        }
        nativePayloadOwnership?.let { ownership ->
            try {
                if (ownership.rollback()) {
                    releases += "native-payload:${ownership.token.value}"
                } else {
                    releases += "native-payload:${ownership.token.value}:failed"
                    diagnostics += preflightDiagnostic(
                        "failed.preflight.native_payload_rollback",
                        "Prepared native payload could not be invalidated before submission.",
                    )
                }
            } catch (failure: Throwable) {
                releases += "native-payload:${ownership.token.value}:failed"
                diagnostics += preflightDiagnostic(
                    "failed.preflight.native_payload_rollback",
                    "Prepared native payload rollback failed.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                )
            }
        }
        try {
            when (val resources = resourceProvider.rollbackFrameResourcesBeforeSubmit(ownerScope)) {
                is GPUPhysicalPoolMaintenanceDecision.Applied -> {
                    releases += resources.value.releaseOrder.map { release ->
                        "resource:${release.reservationId}@${release.acquisitionOrdinal}"
                    }
                    if (resources.value.releaseOrder.isEmpty()) releases += "resources:$ownerScope"
                }
                is GPUPhysicalPoolMaintenanceDecision.Refused -> {
                    releases += "resources:$ownerScope:failed"
                    diagnostics += resources.diagnostic
                }
            }
        } catch (failure: Throwable) {
                releases += "resources:$ownerScope:failed"
                diagnostics += preflightDiagnostic(
                    "failed.preflight.resource_rollback",
                    "Resource rollback failed after all earlier rollback actions were attempted.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                )
        }
        return GPUFrameRollbackResult(releases, diagnostics).also { result = it }
    }
}

internal enum class PreparedGPUFrameOwnershipState {
    Prepared,
    Executing,
    RolledBack,
}

internal class PreparedGPUFrame(
    val semanticPlan: GPUFramePlan,
    val encoderPlan: GPUCommandEncoderPlan,
    val resources: GPUPreparedResourceSet,
    val generationSeal: GPUPreparedGenerationSeal,
    val completionTicket: GPUQueueCompletionTicket,
    val acquiredSurfaceOutput: GPUAcquiredSurfaceOutput?,
    val rollback: GPUFrameRollback,
    stepPartition: List<GPUPreparedStepEvidence>,
    dependencyEvidence: List<GPUPreparedDependencyEvidence>,
    hostActions: List<GPUFrameHostAction>,
) {
    private val ownership = AtomicReference(PreparedGPUFrameOwnershipState.Prepared)
    val stepPartition: List<GPUPreparedStepEvidence> = immutableList(stepPartition)
    val dependencyEvidence: List<GPUPreparedDependencyEvidence> = immutableList(dependencyEvidence)
    val hostActions: List<GPUFrameHostAction> = immutableList(hostActions)
    internal val hasNativePayload: Boolean get() = rollback.hasNativePayload

    /** Claims this prepared resource graph for the one executor allowed to own it. */
    internal fun claimForExecution(): Boolean = ownership.compareAndSet(
        PreparedGPUFrameOwnershipState.Prepared,
        PreparedGPUFrameOwnershipState.Executing,
    )

    /** Claims an unexecuted prepared frame for coordinator-owned rollback. */
    internal fun claimForRollback(): Boolean = ownership.compareAndSet(
        PreparedGPUFrameOwnershipState.Prepared,
        PreparedGPUFrameOwnershipState.RolledBack,
    )

    /** Transitions the executor's exclusive claim to rollback ownership before releasing resources. */
    internal fun rollbackAfterExecutionClaim(): GPUFrameRollbackResult {
        check(
            ownership.compareAndSet(
                PreparedGPUFrameOwnershipState.Executing,
                PreparedGPUFrameOwnershipState.RolledBack,
            ),
        ) { "Prepared frame rollback requires the executor's active ownership claim" }
        return rollback.execute()
    }

    init {
        require(rollback.ownsCompletionTicket(completionTicket)) {
            "PreparedGPUFrame rollback must own the exact completion ticket instance"
        }
        require(this.stepPartition.map { it.sourceStepIndex } == semanticPlan.steps.indices.toList()) {
            "PreparedGPUFrame.stepPartition must cover every semantic step exactly once and in order"
        }
        val expectedEncoderIndices = semanticPlan.steps.indices.filter { index ->
            semanticPlan.steps[index].executionKind ==
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Encoder
        }
        require(encoderPlan.scopes.map { it.sourceStepIndex } == expectedEncoderIndices) {
            "PreparedGPUFrame.encoderPlan must cover every encodable semantic step exactly once and in order"
        }
        encoderPlan.scopes.forEach { scope ->
            val step = semanticPlan.steps[scope.sourceStepIndex]
            require(scope.sourceTaskIds == step.sourceTaskIds) {
                "PreparedGPUFrame encoder scope tasks must exactly match the semantic step"
            }
            require(scope.operationKind == step.expectedEncoderOperationKind()) {
                "PreparedGPUFrame encoder operation kind must exactly match the semantic step"
            }
            require(scope.targetGeneration == generationSeal.targetGeneration) {
                "PreparedGPUFrame encoder scope target generation must match the generation seal"
            }
            require(scope.facadeOperationClasses == step.expectedFacadeOperations(scope)) {
                "PreparedGPUFrame encoder facade operations must exactly match the semantic step"
            }
            val expectedResources = step.preparedResourceRefs().map { resource ->
                val generation = requireNotNull(generationSeal.resourceGenerations[resource]) {
                    "PreparedGPUFrame generation seal is missing ${resource.typedLabel()}"
                }
                "${resource.typedLabel()}@$generation"
            }
            require(scope.resourceGenerationLabels == expectedResources) {
                "PreparedGPUFrame encoder resource generations must exactly match the semantic step"
            }
            if (step is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep) {
                require(scope.corePrimitiveDirectNativeRouteSeal !== GPUCorePrimitiveDirectNativeRouteSeal.Missing) {
                    "PreparedGPUFrame render scopes require a pure-preflight CorePrimitive route seal"
                }
                require(
                    scope.corePrimitivePathStencilNativeRouteSeal !==
                        GPUCorePrimitivePathStencilNativeRouteSeal.Missing,
                ) {
                    "PreparedGPUFrame render scopes require a pure-preflight CorePrimitive path route seal"
                }
                require(
                    scope.corePrimitiveClipStencilPreparedRouteSeal !==
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                ) {
                    "PreparedGPUFrame render scopes require a pure-preflight CorePrimitive clip-stencil route seal"
                }
                require(scope.corePrimitiveNativeScopeRouteSeal !== GPUCorePrimitiveNativeScopeRouteSeal.Missing) {
                    "PreparedGPUFrame render scopes require a pure-preflight unified CorePrimitive route seal"
                }
                val pathSealed = scope.corePrimitivePathStencilNativeRouteSeal is
                    GPUCorePrimitivePathStencilNativeRouteSeal.Pairs
                val unifiedContainsPath = (
                    scope.corePrimitiveNativeScopeRouteSeal as? GPUCorePrimitiveNativeScopeRouteSeal.Routes
                    )?.orderedUnits?.any { unit ->
                    unit is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
                } == true
                val pathUses = step.resourceUses.filter {
                    it.role == GPUFrameResourceRole.PathDepthStencil
                }
                val clipStencilSeal = scope.corePrimitiveClipStencilPreparedRouteSeal
                val clipStencilProducer = clipStencilSeal is
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
                val clipStencilConsumer = clipStencilSeal is
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                val clipStencilSealed = clipStencilProducer || clipStencilConsumer
                val clipStencilUses = step.resourceUses.filter {
                    it.role == GPUFrameResourceRole.ClipDepthStencil
                }
                val writablePathStencil = pathUses.size == 1 &&
                    step.depthStencilLoadStore is
                    org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil
                val depthStencilKeys = scope.nativeOperandKeys.filter {
                    it.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget
                }
                require(
                    pathSealed == unifiedContainsPath &&
                        pathSealed == (pathUses.size == 1) &&
                        pathSealed == writablePathStencil &&
                        (!pathSealed || (
                            depthStencilKeys.size == 1 &&
                            pathUses.single().write &&
                                pathUses.single().usage == GPUFrameResourceUsage.RenderAttachment &&
                                depthStencilKeys.single().kind == GPUPreparedNativeOperandKind.TextureView &&
                                depthStencilKeys.single().ownership ==
                                GPUPreparedNativeOperandOwnership.Borrowed
                            )) &&
                        (pathSealed || clipStencilSealed || depthStencilKeys.isEmpty()),
                ) {
                    "Prepared path seal, unified pair, writable attachment use, load/store, and native operand must agree exactly"
                }
                require(!(pathSealed && clipStencilSealed) &&
                    (!clipStencilSealed || pathUses.isEmpty())
                ) {
                    "Prepared PathDepthStencil and ClipDepthStencil seals are mutually exclusive"
                }
                val expectedClipStencilLoadStore = when {
                    clipStencilProducer ->
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
                            .WritableStencil(
                                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                                org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store,
                                0u,
                            )
                    clipStencilConsumer ->
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
                            .ReadOnlyKeep
                    else -> null
                }
                require(!clipStencilSealed || (
                    clipStencilUses.size == 1 &&
                        clipStencilUses.single().usage == GPUFrameResourceUsage.RenderAttachment &&
                        clipStencilUses.single().write == clipStencilProducer &&
                        step.depthStencilLoadStore == expectedClipStencilLoadStore &&
                        depthStencilKeys.size == 1 &&
                        depthStencilKeys.single().kind == GPUPreparedNativeOperandKind.TextureView &&
                        depthStencilKeys.single().ownership ==
                        GPUPreparedNativeOperandOwnership.Borrowed
                    )) {
                    "Prepared clip-stencil seal, attachment use, load/store, and native operand must agree exactly"
                }
                val expectedPackets = step.drawPackets.map { it.packetId }
                val stream = requireNotNull(scope.passCommandStream) {
                    "PreparedGPUFrame render scope must retain its command stream"
                }
                require(scope.sourcePacketIds == expectedPackets) {
                    "PreparedGPUFrame render packet identities must exactly match the semantic step"
                }
                require(stream.sourcePacketIds == step.expectedRenderCommandPacketIds(scope)) {
                    "PreparedGPUFrame render command stream must have exact per-packet command structure"
                }
                require(stream.sourcePassIds == step.drawPackets.map { it.passId }.distinct()) {
                    "PreparedGPUFrame render command stream must retain original pass identities"
                }
                require(stream.commandLabels == scope.facadeOperationClasses) {
                    "PreparedGPUFrame render facade operations must exactly match its command stream"
                }
                require(stream.operandBridge.size >= expectedPackets.size * 2) {
                    "PreparedGPUFrame render command stream must bridge at least pipeline and bind group operands per packet"
                }
            } else {
                require(scope.sourcePacketIds.isEmpty()) {
                    "PreparedGPUFrame non-render encoder scopes cannot name draw packets"
                }
            }
        }
        require(encoderPlan.deviceGeneration == generationSeal.deviceGeneration) {
            "PreparedGPUFrame encoder device generation must match the generation seal"
        }
        require(encoderPlan.targetGeneration == generationSeal.targetGeneration) {
            "PreparedGPUFrame encoder target generation must match the generation seal"
        }
        require(completionTicket.frameId == semanticPlan.frameId) {
            "PreparedGPUFrame completion ticket must belong to the semantic frame"
        }
        require(completionTicket.deviceGeneration == generationSeal.deviceGeneration) {
            "PreparedGPUFrame completion ticket device generation must match the generation seal"
        }
        require(generationSeal.deviceGeneration == semanticPlan.capabilitySeal.deviceGeneration) {
            "PreparedGPUFrame device generation must match the semantic capability seal"
        }
        require(generationSeal.capabilitySealHash == semanticPlan.capabilitySeal.sealHash) {
            "PreparedGPUFrame capability seal hash must match the semantic plan"
        }
        val resourceGenerations = buildMap {
            resources.ordinaryResources.forEach { put(it.logicalResource, it.resourceGeneration) }
            resources.outputOwnedReadbacks.forEach { put(it.stagingResource, it.resourceGeneration) }
        }
        require(generationSeal.resourceGenerations.keys.containsAll(resourceGenerations.keys)) {
            "PreparedGPUFrame generation seal must cover every prepared resource"
        }
        resources.ordinaryResources.forEach { resource ->
            require(resource.deviceGeneration == generationSeal.deviceGeneration) {
                "PreparedGPUFrame ordinary resource device generation must match the seal"
            }
            require(generationSeal.resourceGenerations[resource.logicalResource] == resource.resourceGeneration) {
                "PreparedGPUFrame ordinary resource generation must match the seal"
            }
        }
        resources.commandResourceLeases.forEach { lease ->
            require(lease.deviceGeneration == generationSeal.deviceGeneration.value) {
                "PreparedGPUFrame command resource lease device generation must match the seal"
            }
        }
        resources.outputOwnedReadbacks.forEach { output ->
            require(output.resourceGeneration >= 0L) {
                "PreparedGPUFrame readback resource generation must be non-negative"
            }
            require(output.stagingResource is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef) {
                "PreparedGPUFrame readback staging must be a logical buffer"
            }
            require(output.concreteResource.ref == output.stagingLease.resourceRef) {
                "PreparedGPUFrame readback concrete resource must match its output-owned lease"
            }
            require(output.stagingLease.backingBufferBytes >= output.stagingLease.logicalMinimumBytes) {
                "PreparedGPUFrame readback backing bytes must cover the logical minimum"
            }
            require(output.stagingLease.deviceGeneration == generationSeal.deviceGeneration) {
                "PreparedGPUFrame readback lease device generation must match the seal"
            }
            require(generationSeal.resourceGenerations[output.stagingResource] == output.resourceGeneration) {
                "PreparedGPUFrame readback resource generation must match the seal"
            }
        }
        acquiredSurfaceOutput?.let { output ->
            require(output.deviceGeneration == generationSeal.deviceGeneration) {
                "PreparedGPUFrame surface device generation must match the seal"
            }
            val surfaceGeneration = semanticPlan.steps
                .filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.AcquireSurfaceOutput>()
                .singleOrNull()
                ?.descriptor
                ?.targetGeneration
            require(output.targetGeneration == surfaceGeneration) {
                "PreparedGPUFrame surface generation must match its acquisition descriptor"
            }
        }
        stepPartition.forEach { evidence ->
            val step = semanticPlan.steps[evidence.sourceStepIndex]
            val expectedLane = when (step.executionKind) {
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Preflight ->
                    if (step is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.AcquireSurfaceOutput) {
                        GPUPreparedStepLane.HostAction
                    } else {
                        GPUPreparedStepLane.ResourcePreflight
                    }
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Encoder -> GPUPreparedStepLane.Encoder
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.DependencyOnly -> GPUPreparedStepLane.Dependency
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.PostSubmitHost -> GPUPreparedStepLane.HostAction
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.RefusalEvidence -> GPUPreparedStepLane.RefusalEvidence
            }
            require(evidence.lane == expectedLane) {
                "PreparedGPUFrame step lane must match the semantic execution kind"
            }
        }
        require(
            stepPartition.filter { it.lane == GPUPreparedStepLane.Encoder }.map { it.sourceStepIndex } ==
                encoderPlan.scopes.map { it.sourceStepIndex },
        ) { "PreparedGPUFrame encoder lane must exactly match encoder scopes" }
        val expectedDependencies = semanticPlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.DependencyBarrierStep ->
                    GPUPreparedDependencyEvidence(index, "DependencyBarrier", step.reasonCode)
                is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.TargetTransitionStep ->
                    GPUPreparedDependencyEvidence(index, "TargetTransition", step.transitionKind.name)
                else -> null
            }
        }
        require(dependencyEvidence == expectedDependencies) {
            "PreparedGPUFrame dependency evidence must exactly match semantic dependency steps"
        }
        val expectedHostActions = semanticPlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.AcquireSurfaceOutput ->
                    GPUFrameHostAction(index, GPUHostActionKind.AcquireSurface, step.descriptor.output)
                is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.PostSubmitPresentAction ->
                    GPUFrameHostAction(index, GPUHostActionKind.Present, step.output)
                else -> null
            }
        }
        require(hostActions == expectedHostActions) {
            "PreparedGPUFrame host actions must exactly match semantic acquire/present steps"
        }
        val acquiredRef = acquiredSurfaceOutput?.output
        val semanticAcquireRef = expectedHostActions.singleOrNull { it.kind == GPUHostActionKind.AcquireSurface }?.output
        require(acquiredRef == semanticAcquireRef) {
            "PreparedGPUFrame acquired surface must exactly match semantic host evidence"
        }
    }

    fun dumpLines(): List<String> = buildList {
        add(
            "prepared-frame frame=${semanticPlan.frameId.value} plan=${encoderPlan.planId} " +
                "context=${encoderPlan.contextIdentity} ticket=${completionTicket.ticketId.value} " +
                "deviceGeneration=${generationSeal.deviceGeneration.value} " +
                "targetGeneration=${generationSeal.targetGeneration} scopes=${encoderPlan.scopes.size}",
        )
        stepPartition.forEach { evidence ->
            add("prepared-step index=${evidence.sourceStepIndex} lane=${evidence.lane} kind=${evidence.kind}")
        }
        resources.ordinaryResources.forEach { resource ->
            add(
                "prepared-resource logical=${resource.logicalResource.typedLabel()} " +
                    "concrete=${resource.concreteResource::class.simpleName}:${resource.concreteResource.value} " +
                    "role=${resource.role} deviceGeneration=${resource.deviceGeneration.value} " +
                    "resourceGeneration=${resource.resourceGeneration}",
            )
        }
        resources.outputOwnedReadbacks.forEach { output ->
            add(
                "prepared-readback staging=${output.stagingResource.typedLabel()} " +
                    "concrete=${output.concreteResource.value} request=${output.request.requestId.value} " +
                    "resourceGeneration=${output.resourceGeneration} " +
                    "layout=${output.layout.width}x${output.layout.height}:${output.layout.paddedBytesPerRow}:${output.layout.totalBufferBytes} " +
                    "reservation=${output.stagingLease.reservationId} owner=${output.stagingLease.ownerScope} " +
                    "deviceGeneration=${output.stagingLease.deviceGeneration.value} " +
                    "ordinal=${output.stagingLease.reservationOrdinal} " +
                    "minimumBytes=${output.stagingLease.logicalMinimumBytes} " +
                    "backingBytes=${output.stagingLease.backingBufferBytes} " +
                    "usages=${output.stagingLease.usages.map { it.name }.sorted().joinToString(",")}",
            )
        }
        resources.commandResourceLeases.forEach { lease ->
            add(
                "prepared-command-lease id=${lease.leaseId} kind=${lease.resourceKind.dumpToken} " +
                    "deviceGeneration=${lease.deviceGeneration} owner=${lease.ownerScope} " +
                    "release=${lease.releasePolicy} usage=${lease.usageLabels.joinToString(",")}",
            )
        }
        resources.commandTextureResources.forEach { resource ->
            add("prepared-command-texture resource=${resource.value}")
        }
        resources.commandBufferResources.forEach { resource ->
            add("prepared-command-buffer resource=${resource.value}")
        }
        resources.commandDiagnostics.forEach { diagnostic ->
            add(
                "prepared-command-diagnostic code=${diagnostic.code} resource=${diagnostic.resourceLabel} " +
                    "facts=${diagnostic.facts.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }}",
            )
        }
        dependencyEvidence.forEach { evidence ->
            add("prepared-dependency index=${evidence.sourceStepIndex} kind=${evidence.kind} reason=${evidence.reasonCode}")
        }
        hostActions.forEach { action ->
            add("prepared-host index=${action.sourceStepIndex} kind=${action.kind} output=${action.output.value}")
        }
        acquiredSurfaceOutput?.let { output ->
            add(
                "prepared-surface output=${output.output.value} deviceGeneration=${output.deviceGeneration.value} " +
                    "targetGeneration=${output.targetGeneration} evidence=${output.evidenceLabel}",
            )
        }
        encoderPlan.scopes.forEach { scope ->
            add(
                "prepared-encoder index=${scope.sourceStepIndex} kind=${scope.operationKind} " +
                    "tasks=${scope.sourceTaskIds.joinToString(",") { it.value }} " +
                    "packets=${scope.sourcePacketIds.joinToString(",") { it.value }.ifEmpty { "none" }} " +
                    "operations=${scope.facadeOperationClasses.joinToString(",")} " +
                    "targetGeneration=${scope.targetGeneration} " +
                    "resources=${scope.resourceGenerationLabels.joinToString(",").ifEmpty { "none" }}",
            )
        }
    }

    fun stableHash(): String = PreparedHashSink().apply {
        string("semanticPlanHash", semanticPlan.stableHash())
        string("encoderPlanId", encoderPlan.planId)
        string("encoderContextIdentity", encoderPlan.contextIdentity)
        string("ticketId", completionTicket.ticketId.value)
        long("ticketFrame", completionTicket.frameId.value)
        long("deviceGeneration", generationSeal.deviceGeneration.value)
        long("targetGeneration", generationSeal.targetGeneration)
        string("capabilitySealHash", generationSeal.capabilitySealHash)
        list("resourceGenerations", generationSeal.resourceGenerations.entries.sortedBy { it.key.typedLabel() }) {
            string("resourceType", it.key::class.simpleName.orEmpty())
            string("resource", it.key.value)
            long("generation", it.value)
        }
        list("ordinaryResources", resources.ordinaryResources) {
            string("logical", it.logicalResource.value)
            string("logicalType", it.logicalResource::class.simpleName.orEmpty())
            string("concreteType", it.concreteResource::class.simpleName.orEmpty())
            string("concrete", it.concreteResource.value)
            string("role", it.role.name)
            long("deviceGeneration", it.deviceGeneration.value)
            long("resourceGeneration", it.resourceGeneration)
        }
        list("readbacks", resources.outputOwnedReadbacks) {
            string("staging", it.stagingResource.value)
            string("stagingType", it.stagingResource::class.simpleName.orEmpty())
            string("concrete", it.concreteResource.value)
            long("resourceGeneration", it.resourceGeneration)
            string("request", it.request.requestId.value)
            int("width", it.layout.width)
            int("height", it.layout.height)
            long("unpadded", it.layout.unpaddedBytesPerRow)
            long("padded", it.layout.paddedBytesPerRow)
            long("offset", it.layout.bufferOffset)
            long("total", it.layout.totalBufferBytes)
            string("lease", it.stagingLease.resourceRef.value)
            string("reservationId", it.stagingLease.reservationId)
            string("ownerScope", it.stagingLease.ownerScope)
            long("leaseDeviceGeneration", it.stagingLease.deviceGeneration.value)
            long("reservationOrdinal", it.stagingLease.reservationOrdinal)
            long("logicalMinimumBytes", it.stagingLease.logicalMinimumBytes)
            long("backingBufferBytes", it.stagingLease.backingBufferBytes)
            list("usages", it.stagingLease.usages.map { usage -> usage.name }.sorted()) { usage ->
                string("usage", usage)
            }
        }
        list("commandLeases", resources.commandResourceLeases) {
            string("leaseId", it.leaseId)
            string("kind", it.resourceKind.name)
            long("deviceGeneration", it.deviceGeneration)
            string("descriptorHash", it.descriptorHash)
            string("ownerScope", it.ownerScope)
            list("usageLabels", it.usageLabels) { usage -> string("usage", usage) }
            string("releasePolicy", it.releasePolicy)
            string("cacheResult", it.cacheResult.name)
            list("evidenceFacts", it.evidenceFacts.entries.sortedBy { entry -> entry.key }) { entry ->
                string("key", entry.key)
                string("value", entry.value)
            }
        }
        list("commandTextures", resources.commandTextureResources) { string("texture", it.value) }
        list("commandBuffers", resources.commandBufferResources) { string("buffer", it.value) }
        list("commandDiagnostics", resources.commandDiagnostics) {
            string("code", it.code)
            string("resource", it.resourceLabel)
            string("message", it.message)
            list("facts", it.facts.entries.sortedBy { entry -> entry.key }) { entry ->
                string("key", entry.key)
                string("value", entry.value)
            }
        }
        list("partition", stepPartition) {
            int("index", it.sourceStepIndex)
            string("lane", it.lane.name)
            string("kind", it.kind)
        }
        list("dependencies", dependencyEvidence) {
            int("index", it.sourceStepIndex)
            string("kind", it.kind)
            string("reason", it.reasonCode)
        }
        list("hostActions", hostActions) {
            int("index", it.sourceStepIndex)
            string("kind", it.kind.name)
            string("output", it.output.value)
        }
        list("encoderScopes", encoderPlan.scopes) {
            int("index", it.sourceStepIndex)
            string("kind", it.operationKind.name)
            string("scopeLabel", it.scopeLabel)
            list("tasks", it.sourceTaskIds) { task -> string("task", task.value) }
            list("packets", it.sourcePacketIds) { packet -> string("packet", packet.value) }
            list("operations", it.facadeOperationClasses) { operation -> string("operation", operation) }
            long("targetGeneration", it.targetGeneration)
            list("resources", it.resourceGenerationLabels) { resource -> string("resource", resource) }
            nullable("passCommandStream", it.passCommandStream) { stream ->
                string("streamId", stream.streamId)
                string("packetStreamId", stream.packetStreamId)
                string("passId", stream.passId)
                list("sourcePassIds", stream.sourcePassIds) { passId -> string("sourcePassId", passId) }
                list("dump", stream.dumpLines()) { line -> string("line", line) }
            }
        }
        nullable("surface", acquiredSurfaceOutput) {
            string("output", it.output.value)
            long("deviceGeneration", it.deviceGeneration.value)
            long("targetGeneration", it.targetGeneration)
            string("evidence", it.evidenceLabel)
        }
    }.finish()
}

internal sealed interface GPUFramePreflightResult {
    data class Prepared(val frame: PreparedGPUFrame) : GPUFramePreflightResult
    class Refused(
        diagnostic: GPUDiagnostic,
        val rollbackResult: GPUFrameRollbackResult? = null,
    ) : GPUFramePreflightResult {
        val diagnostic: GPUDiagnostic = diagnostic.preflightSnapshot()
    }
}

internal fun preflightDiagnostic(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Execution,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = immutableMap(facts),
    isTerminal = true,
)

private fun GPUDiagnostic.preflightSnapshot(): GPUDiagnostic = copy(facts = immutableMap(facts))

private class PreparedHashSink {
    private val bytes = ByteArrayOutputStream()
    private val output = DataOutputStream(bytes)

    init {
        string("root", "PreparedGPUFrame/v1")
    }

    fun string(name: String, value: String) {
        field(1, name)
        val encoded = value.toByteArray(Charsets.UTF_8)
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    fun int(name: String, value: Int) {
        field(2, name)
        output.writeInt(value)
    }

    fun long(name: String, value: Long) {
        field(3, name)
        output.writeLong(value)
    }

    fun <T> list(name: String, values: Collection<T>, encode: PreparedHashSink.(T) -> Unit) {
        field(4, name)
        output.writeInt(values.size)
        values.forEach { value ->
            field(5, "item")
            encode(value)
        }
    }

    fun <T> nullable(name: String, value: T?, encode: PreparedHashSink.(T) -> Unit) {
        field(6, name)
        output.writeBoolean(value != null)
        if (value != null) encode(value)
    }

    fun finish(): String {
        output.flush()
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun field(kind: Int, name: String) {
        output.writeByte(kind)
        val encoded = name.toByteArray(Charsets.UTF_8)
        output.writeInt(encoded.size)
        output.write(encoded)
    }
}

private fun GPUFrameResourceRef.typedLabel(): String = "${this::class.simpleName}:$value"

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.expectedEncoderOperationKind():
    GPUEncoderOperationKind = when (this) {
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep -> GPUEncoderOperationKind.Render
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ComputePassStep -> GPUEncoderOperationKind.Compute
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.UploadResourceStep -> GPUEncoderOperationKind.Upload
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyResourceStep -> GPUEncoderOperationKind.Copy
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyDestinationStep -> GPUEncoderOperationKind.CopyDestination
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyAsDrawMaterializationStep -> GPUEncoderOperationKind.CopyAsDraw
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ReadbackCopyStep -> GPUEncoderOperationKind.Readback
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.SurfaceBlitRenderPassStep -> GPUEncoderOperationKind.SurfaceBlit
    else -> error("Non-encodable semantic step has no encoder operation kind")
}

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.expectedFacadeOperations(
    scope: GPUCommandEncoderScopePlan,
): List<String> =
    when (this) {
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep -> buildList {
            add("beginRenderPass")
            drawPackets.forEach { packet ->
                add("setRenderPipeline")
                val clipStencilProducer =
                    scope.corePrimitiveClipStencilPreparedRouteSeal is
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
                if (!clipStencilProducer) add("setBindGroup")
                val directRoutes = scope.corePrimitiveDirectNativeRouteSeal as?
                    GPUCorePrimitiveDirectNativeRouteSeal.Routes
                val clipStencilSealed =
                    scope.corePrimitiveClipStencilPreparedRouteSeal is
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                        scope.corePrimitiveClipStencilPreparedRouteSeal is
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                if (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                    directRoutes?.routesByPacketId?.containsKey(packet.packetId) == true ||
                    clipStencilSealed) {
                    add("setVertexBuffer")
                    add("setIndexBuffer")
                }
                if (packet.scissorBoundsHash != null) add("setScissor")
                add("draw")
            }
            add("endRenderPass")
        }
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ComputePassStep ->
            listOf("beginComputePass") + List(dispatches.size) { "dispatchWorkgroups" } + "endComputePass"
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.UploadResourceStep -> listOf("writeBufferOrCopyBuffer")
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyResourceStep ->
            List(regions.size) { "copyResource" }
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyDestinationStep -> listOf("copyTextureToTexture")
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyAsDrawMaterializationStep ->
            listOf("beginRenderPass", "copyAsDraw", "endRenderPass")
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ReadbackCopyStep -> listOf("copyTextureToBuffer")
        is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.SurfaceBlitRenderPassStep ->
            listOf("beginRenderPass", "surfaceBlit", "endRenderPass")
        else -> error("Non-encodable semantic step has no facade operations")
    }

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep
    .expectedRenderCommandPacketIds(scope: GPUCommandEncoderScopePlan): List<GPUDrawPacketID> = buildList {
    val directRoutes = scope.corePrimitiveDirectNativeRouteSeal as?
        GPUCorePrimitiveDirectNativeRouteSeal.Routes
    val clipStencilSealed =
        scope.corePrimitiveClipStencilPreparedRouteSeal is
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
            scope.corePrimitiveClipStencilPreparedRouteSeal is
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
    val clipStencilProducer =
        scope.corePrimitiveClipStencilPreparedRouteSeal is
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
    drawPackets.forEach { packet ->
        add(packet.packetId)
        if (!clipStencilProducer) add(packet.packetId)
        if (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
            directRoutes?.routesByPacketId?.containsKey(packet.packetId) == true ||
            clipStencilSealed) {
            add(packet.packetId)
            add(packet.packetId)
        }
        if (packet.scissorBoundsHash != null) add(packet.packetId)
        add(packet.packetId)
    }
}

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.preparedResourceRefs():
    List<GPUFrameResourceRef> = when (this) {
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep ->
        listOf(target) + resourceUses.map { it.resource }
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ComputePassStep ->
        listOf(target) + resourceUses.map { it.resource }
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.UploadResourceStep -> listOf(staging, destination)
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyResourceStep -> listOf(source, destination)
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyDestinationStep -> listOf(source, snapshot)
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.CopyAsDrawMaterializationStep -> listOf(source, snapshot)
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.ReadbackCopyStep -> listOf(source, staging)
    is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.SurfaceBlitRenderPassStep -> listOf(scene)
    else -> emptyList()
}
