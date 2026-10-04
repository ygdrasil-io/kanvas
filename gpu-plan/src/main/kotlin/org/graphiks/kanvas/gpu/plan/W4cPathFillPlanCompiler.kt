package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSemanticValidationResult
import org.graphiks.kanvas.render.ir.SceneSemanticValidator
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.SizeI32

/** Closed W4c capability for bounded solid hard-edge path fills. */
public class W4cPathFillPlanCompiler internal constructor(internal val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot) : GpuPlanCompiler {
    private val admissionIssuerToken = Any()

    public constructor() : this(RuntimeEffectSemanticCatalogSnapshot.Unbound)
    override fun select(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalidSelection("Scene and target descriptors disagree")
        }
        when (val validation = SceneSemanticValidator.validate(scene)) {
            is SceneSemanticValidationResult.Invalid -> return invalidSelection(validation.message)
            SceneSemanticValidationResult.Valid -> Unit
        }
        if (target.colorSpace != ColorSpace.SRGB) {
            return notCandidate("W4c supports only sRGB targets")
        }
        if (W4cFramePreparationV1.qualifiesHardPathRoot(scene, target)) {
            val admission = issueOriginalFrameAdmission(scene, target, W4cOriginalFrameModeV1.HardPathRoot)
            return when (val preparation = W4cFramePreparationV1.prepareFrame(admission)) {
                is W4cFramePreparationResultV1.MaterialRefused -> GpuPlanSelection.MaterialOnlyRefusal(
                    W7_HARD_PATH_ROOT_CAPABILITY_ID, scene.canonicalId, target, preparation.refusals,
                )
                is W4cFramePreparationResultV1.Accepted -> {
                    val frame = preparation.hardPathRootFrame ?: return invalidSelection(
                        "HardPath root preparation did not retain its original-frame proof",
                    )
                    GpuPlanSelection.Candidate(W7HardPathRootCandidate(this, scene.canonicalId, target, frame))
                }
                is W4cFramePreparationResultV1.Gap -> notCandidate(preparation.message)
                is W4cFramePreparationResultV1.Invalid -> invalidSelection(preparation.message)
                is W4cFramePreparationResultV1.ResourceLimit -> resourceSelection(preparation.message)
            }
        }
        val admission = issueOriginalFrameAdmission(scene, target, W4cOriginalFrameModeV1.PathOnly)
        return when (val preparation = W4cFramePreparationV1.prepareFrame(admission)) {
            is W4cFramePreparationResultV1.MaterialRefused -> GpuPlanSelection.MaterialOnlyRefusal(CAPABILITY_ID, scene.canonicalId, target, preparation.refusals)
            is W4cFramePreparationResultV1.Accepted -> GpuPlanSelection.Candidate(
                W4cCandidate(this, scene.canonicalId, target, preparation.draws, preparation.materialPlanTable, preparation.capabilityId,preparation.sourceTable),
            )
            is W4cFramePreparationResultV1.Gap -> notCandidate(preparation.message)
            is W4cFramePreparationResultV1.Invalid -> invalidSelection(preparation.message)
            is W4cFramePreparationResultV1.ResourceLimit -> resourceSelection(preparation.message)
        }
    }

    private fun issueOriginalFrameAdmission(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        mode: W4cOriginalFrameModeV1,
    ): W4cOriginalFrameAdmissionV1 = W4cOriginalFrameAdmissionV1(
        owner = this,
        scene = scene,
        target = target,
        runtimeCatalog = runtimeCatalog,
        mode = mode,
        issuerToken = admissionIssuerToken,
    )

    internal fun authenticates(admission: W4cOriginalFrameAdmissionV1): Boolean =
        admission.owner === this &&
            admission.runtimeCatalog === runtimeCatalog &&
            admission.wasIssuedBy(admissionIssuerToken)

    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(),
        capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes,
        capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { it > 0L && it and (it - 1L) == 0L }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val hardPathCandidate = candidate as? W7HardPathRootCandidate
        if (hardPathCandidate != null) {
            if (hardPathCandidate.owner !== this || !hardPathCandidate.hasMatchingFingerprints()) return invalidCandidate()
            val extent = SizeI32(hardPathCandidate.target.extent.width, hardPathCandidate.target.extent.height)
            if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D)
                return promoted(W4cPlanDiagnostics.CapabilityTextureDimension, "Target extent exceeds device texture limits")
            if (FORMAT !in capabilities.supportedFormats()) return promoted(W4cPlanDiagnostics.CapabilityFormat, "W4c target format is unavailable")
            if (!REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() })
                return promoted(W4cPlanDiagnostics.CapabilityOperation, "W4c required operation is unavailable")
            if (PlanDepthStencilFormat.Depth24PlusStencil8 !in capabilities.supportedDepthStencilFormats())
                return promoted(W4cPlanDiagnostics.CapabilityDepthStencilFormat, "W4c depth-stencil format is unavailable")
            if (capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1)
                return promoted(W4cPlanDiagnostics.CapabilityDynamicUniform, "W4c requires one dynamic uniform buffer")
            if (!validAllocationFacts(capabilities))
                return promoted(W4cPlanDiagnostics.CapabilityAllocationPolicy, "W4c allocation facts are not positive powers of two")
            return try {
                W5bDestinationGraphSealer.constructHardPathRoot(hardPathCandidate.frame, capabilities, budget)
                    .publishConstructionResult()
            } catch (failure: RawMaterialRequirementsV2.Refusal) {
                RenderPlanResult.ResourceLimitExceeded(listOf(diag(
                    RenderDiagnosticCode(failure.code), RenderDiagnosticDomain.RESOURCE, failure.code,
                )))
            } catch (_: ArithmeticException) {
                resourceLimit(W4cPlanDiagnostics.SizeOverflow, "HardPath root graph arithmetic overflowed")
            } catch (failure: IllegalArgumentException) {
                val code = failure.message ?: "HardPath root graph invariants were not satisfied"
                if (code == "unsupported.w5b.destination-capability" ||
                    code == "unsupported.w5b.destination-texture" ||
                    code == "unsupported.w5b.destination-row-alignment") {
                    RenderPlanResult.GapOnPromotedScope(listOf(diag(
                        RenderDiagnosticCode(code), RenderDiagnosticDomain.CAPABILITY, code,
                    )))
                } else if (code == W4cPlanDiagnostics.CapabilityBufferSize) {
                    promoted(W4cPlanDiagnostics.CapabilityBufferSize, "W4c buffer capacity exceeds device limits")
                } else {
                    resourceLimit(W4cPlanDiagnostics.PlanIdentityInvalid,
                        code)
                }
            }
        }
        return if (hasPendingSources(candidate)) constructSources(candidate,capabilities,budget).prepareAndPublishSourcesV4()
        else construct(candidate,capabilities,budget).publishConstructionResult()
    }

    internal fun hasPendingSources(candidate: GpuPlanCandidate): Boolean =
        (candidate as? W4cCandidate)?.sourceTable?.sources()?.any { it.pending } == true

    internal fun construct(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraphConstruction> = constructChecked(candidate,capabilities,budget,
        clear = { selected,extent -> RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.constructClearOnly(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
            extent,capabilities,budget,selected.materialPlanTable))) },
        destination = { selected,extent,draws,resources,data,depth,memory ->
            require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
            RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bDestinationGraphSealer.construct(
                PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
                extent,capabilities,budget,draws,selected.materialPlanTable,memory.targetBytes,memory.readbackBytes,
                memory.readbackBytesPerRow,resources,drawDataResources=data,depthStencilByCommandI32=depth)))
        }) { selected,extent,topology ->
            require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
            RenderPlanResult.Ready(RenderGraph.construct(PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),
                CAPABILITY_ID,extent,FORMAT,capabilities,budget,selected.draws.size,topology.resources,topology.passes,
                topology.dependencies,topology.peakI64,selected.materialPlanTable))
        }

    internal fun constructSources(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> = constructChecked(candidate,capabilities,budget,
        clear = { selected,extent -> SourceDeferredRenderConstructionV4.clearOnly(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
            extent,capabilities,budget, preparedIdentity = { scene, _, _ -> PlanId(planIdentity(scene, selected.target, capabilities, budget)) }) },
        destination = { selected,extent,draws,resources,data,depth,memory ->
            val symbolic = draws.mapIndexed { ordinal,draw -> draw.withMaterialRef(MaterialPlanRef(ordinal)) }
            deferred(selected,extent,capabilities,budget,W5bDestinationGraphSealer.describeSources(W5B_CAPABILITY_ID,
                extent,capabilities,budget,symbolic,memory.targetBytes,memory.readbackBytes,memory.readbackBytesPerRow,
                resources,drawDataResources=data,depthStencilByCommandI32=depth))
        }) { selected,extent,topology ->
            val roots = selected.draws.map { it.material }
            deferred(selected,extent,capabilities,budget,W5bDestinationGraphSealer.DestinationTopologyV4(topology.format,
                topology.resources,remapSourcePassesV4(topology.passes) { ref ->
                    MaterialPlanRef(roots.indexOf(ref).also { require(it >= 0) }) },topology.dependencies,topology.peakI64))
        }

    private fun deferred(selected: W4cCandidate,extent: SizeI32,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        topology: W5bDestinationGraphSealer.DestinationTopologyV4): RenderPlanResult<SourceDeferredRenderConstructionV4> =
        when (val result = SourceDeferredRenderConstructionV4.of(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),selected.capabilityId,
            extent,topology.format,capabilities,budget,selected.draws.size,topology.resources,topology.passes,topology.dependencies,
            selected.sourceTable,if (selected.capabilityId == W5B_CAPABILITY_ID) DeferredLaneTopologyV4.GeometryBridge
                else DeferredLaneTopologyV4.Ordinary,null,emptyList(),emptyMap(),emptyMap(),
                preparedIdentity = { scene, _, _ -> PlanId(planIdentity(scene, selected.target, capabilities, budget)) })) {
            is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(result.value)
            is SourceConstructionResultV4.Refused -> result.failure
        }

    private fun <T: Any> constructChecked(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        clear: (W4cCandidate,SizeI32)->RenderPlanResult<T>,
        destination: (W4cCandidate,SizeI32,List<PathFillDraw>,List<PlanResource>,PlanDrawDataResources,
            Map<Int,PlanResourceId>,PathFillMemoryFootprint)->RenderPlanResult<T>,
        ordinary: (W4cCandidate,SizeI32,W5bDestinationGraphSealer.DestinationTopologyV4)->RenderPlanResult<T>): RenderPlanResult<T> {
        val selected = candidate as? W4cCandidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()

        val target = selected.target
        val extent = SizeI32(target.extent.width, target.extent.height)
        if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D) {
            return promoted(
                W4cPlanDiagnostics.CapabilityTextureDimension,
                "Target extent exceeds device texture limits",
            )
        }
        if (FORMAT !in capabilities.supportedFormats()) {
            return promoted(W4cPlanDiagnostics.CapabilityFormat, "W4c target format is unavailable")
        }
        if (!REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() }) {
            return promoted(W4cPlanDiagnostics.CapabilityOperation, "W4c required operation is unavailable")
        }
        if (PlanDepthStencilFormat.Depth24PlusStencil8 !in capabilities.supportedDepthStencilFormats()) {
            return promoted(
                W4cPlanDiagnostics.CapabilityDepthStencilFormat,
                "W4c depth-stencil format is unavailable",
            )
        }
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        if (capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1) {
            return promoted(
                W4cPlanDiagnostics.CapabilityDynamicUniform,
                "W4c requires one dynamic uniform buffer",
            )
        }
        if (!validAllocationFacts(capabilities)) {
            return promoted(
                W4cPlanDiagnostics.CapabilityAllocationPolicy,
                "W4c allocation facts are not positive powers of two",
            )
        }
        if (selected.draws.isEmpty()) return try {
            clear(selected,extent)
        } catch (failure: IllegalArgumentException) {
            resourceLimit(W4cPlanDiagnostics.PlanIdentityInvalid, failure.message ?: "Invalid W5b clear-only path frame")
        }
        val footprint = when (
            val memory = PathFillPlanBudget.calculate(
                targetExtent = extent,
                geometriesF32 = selected.draws.map(W4cSealedDrawV1::geometryF32),
                capabilities = capabilities,
                budget = budget,
            )
        ) {
            is PathFillPlanBudgetResult.WithinBudget -> memory.footprint
            is PathFillPlanBudgetResult.Exceeded -> {
                return resourceLimit(
                    W4cPlanDiagnostics.BudgetFrameLocalExceeded,
                    "Frame-local memory budget is exceeded",
                )
            }
            is PathFillPlanBudgetResult.Invalid -> {
                return resourceLimit(
                    W4cPlanDiagnostics.SizeOverflow,
                    "W4c physical footprint cannot be represented: ${memory.code}",
                )
            }
        }
        if (
            listOf(
                footprint.readbackBytes,
                footprint.vertexCapacityBytes,
                footprint.indexCapacityBytes,
                footprint.uniformCapacityBytes,
            ).any { it > capabilities.maxBufferSizeBytes }
        ) {
            return promoted(
                W4cPlanDiagnostics.CapabilityBufferSize,
                "W4c buffer capacity exceeds device limits",
            )
        }

        return try {
            val colorPassCount = checkedColorPassCount(selected.draws)
            val readbackIndex = colorPassCount
            val passCount = Math.addExact(readbackIndex, 1)
            val firstStencilPassIndex = selected.draws.indexOfFirst {
                it.strategy == PathFillStrategy.StencilCover
            }.let { drawIndex ->
                if (drawIndex < 0) -1 else checkedColorPassCount(selected.draws.take(drawIndex))
            }

            val logicalTarget = PlanResource.of(
                PlanResourceRole.LogicalTarget,
                0,
                PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(FORMAT),
                extent,
                footprint.targetBytes,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val staging = PlanResource.of(
                PlanResourceRole.ReadbackStaging,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.readbackBytes,
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                PlanResourceLifetime.FrameLocal,
                readbackIndex,
                passCount,
            )
            val vertex = PlanResource.of(
                PlanResourceRole.VertexData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.vertexCapacityBytes,
                setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val index = PlanResource.of(
                PlanResourceRole.IndexData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.indexCapacityBytes,
                setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val uniform = PlanResource.of(
                PlanResourceRole.UniformData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.uniformCapacityBytes,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val depthStencil = if (usesStencil) {
                PlanResource.of(
                    PlanResourceRole.DepthStencil,
                    0,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                    extent,
                    footprint.depthStencilBytes,
                    setOf(PlanResourceUsage.DepthStencilAttachment),
                    PlanResourceLifetime.FrameLocal,
                    firstStencilPassIndex,
                    passCount,
                )
            } else {
                null
            }
            val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
            if (selected.capabilityId == W5B_CAPABILITY_ID) {
                val draws = selected.draws.map { draw -> PathFillDraw.ofMaterial(draw.commandIndex, draw.material,
                    draw.geometryF32, draw.strategy, draw.scissorI32, draw.blend, draw.coordinates,
                    coordinatesV2 = draw.coordinatesV2,
                    coordinatesV4 = draw.coordinatesV4) }
                return destination(selected,extent,draws,listOfNotNull(vertex,index,uniform,depthStencil),drawData,
                    draws.mapNotNull { draw -> depthStencil?.id?.let { draw.commandIndex to it } }.toMap(),footprint)
            }
            val passes = mutableListOf<PlanPass>()
            var renderOrdinal = 0
            var producerOrdinal = 0
            var coverOrdinal = 0
            var firstColorAttachment = true
            selected.draws.forEach { sealed ->
                val draw = PathFillDraw.ofMaterial(
                    commandIndexI32 = sealed.commandIndex,
                    material = sealed.material,
                    geometryF32 = sealed.geometryF32,
                    strategy = sealed.strategy,
                    scissorI32 = sealed.scissorI32,
                    coordinates = sealed.coordinates,
                    coordinatesV2 = sealed.coordinatesV2,
                    coordinatesV4 = sealed.coordinatesV4,
                )
                val load = if (firstColorAttachment) {
                    AttachmentLoadPlan.ClearTransparent
                } else {
                    AttachmentLoadPlan.Load
                }
                when (sealed.strategy) {
                    PathFillStrategy.DirectTriangle -> {
                        passes += PlanPass.RenderPass(
                            renderOrdinal++,
                            logicalTarget.id,
                            listOf(draw),
                            load,
                            AttachmentStorePlan.Store,
                            drawData,
                        )
                        firstColorAttachment = false
                    }
                    PathFillStrategy.StencilCover -> {
                        val depth = requireNotNull(depthStencil)
                        val atomicGroup = PlanAtomicGroupId("w4c:${sealed.commandIndex}")
                        passes += PlanPass.StencilProducer(
                            producerOrdinal++,
                            logicalTarget.id,
                            depth.id,
                            draw,
                            drawData,
                            atomicGroup,
                            load,
                            AttachmentStorePlan.Store,
                            PlanDepthStencilAccess.Write,
                            PlanDepthStencilLoadStore.ClearZeroStore,
                        )
                        passes += PlanPass.StencilCover(
                            coverOrdinal++,
                            logicalTarget.id,
                            depth.id,
                            draw,
                            drawData,
                            atomicGroup,
                            AttachmentLoadPlan.Load,
                            AttachmentStorePlan.Store,
                            PlanDepthStencilAccess.ReadWrite,
                            PlanDepthStencilLoadStore.LoadStoreTestReset,
                        )
                        firstColorAttachment = false
                    }
                }
            }
            val readback = PlanPass.ReadbackPass(
                0,
                logicalTarget.id,
                staging.id,
                footprint.readbackBytesPerRow,
            )
            passes += readback
            val dependencies = passes.zipWithNext().map { (before, after) ->
                PlanPassDependency(before.id, after.id)
            }
            ordinary(selected,extent,W5bDestinationGraphSealer.DestinationTopologyV4(FORMAT,
                listOfNotNull(logicalTarget,staging,vertex,index,uniform,depthStencil),passes,dependencies,footprint.peakBytes))
        } catch (_: IllegalArgumentException) {
            resourceLimit(
                W4cPlanDiagnostics.PlanIdentityInvalid,
                "W4c graph invariants were not satisfied",
            )
        } catch (_: ArithmeticException) {
            resourceLimit(
                W4cPlanDiagnostics.SizeOverflow,
                "W4c graph arithmetic overflowed",
            )
        }
    }

    private fun checkedColorPassCount(draws: List<W4cSealedDrawV1>): Int = draws.fold(0) { count, draw ->
        Math.addExact(count, if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2)
    }

    private fun notCandidate(message: String): GpuPlanSelection.NotCandidate =
        GpuPlanSelection.NotCandidate(
            listOf(diag(W4cPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)),
        )

    private fun invalidSelection(message: String): GpuPlanSelection.InvalidScene =
        GpuPlanSelection.InvalidScene(
            listOf(diag(W4cPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)),
        )

    private fun resourceSelection(message: String): GpuPlanSelection.ResourceLimitExceeded =
        GpuPlanSelection.ResourceLimitExceeded(
            listOf(diag(W4cPlanDiagnostics.PathResourceLimit, RenderDiagnosticDomain.RESOURCE, message)),
        )

    private fun promoted(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> =
        RenderPlanResult.GapOnPromotedScope(
            listOf(diag(code, RenderDiagnosticDomain.CAPABILITY, message)),
        )

    private fun resourceLimit(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> =
        RenderPlanResult.ResourceLimitExceeded(
            listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)),
        )

    private fun invalidCandidate(): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(
        listOf(
            RenderDiagnostic(
                RenderDiagnosticCode("gpu-plan.selection.invalid-candidate"),
                RenderDiagnosticDomain.SCENE,
                RenderDiagnosticSeverity.ERROR,
                "W4c candidate does not belong to this compiler.",
            ),
        ),
    )

    private fun diag(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = W4cPlanDiagnostics.diagnostic(code, domain, message)

    internal fun planIdentity(
        scene: CanonicalId,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): String {
        val fields = listOf(
            "w4c-plan-w5a-material-v2",
            scene.value,
            target.canonicalId.value,
            target.extent.width.toString(),
            target.extent.height.toString(),
            target.colorSpace.name,
            target.colorSpace.transferFunction.name,
            target.colorSpace.gamut.name,
            capabilities.deviceGeneration.toString(),
            capabilities.maxTextureDimension2D.toString(),
            capabilities.maxBufferSizeBytes.toString(),
            capabilities.copyBytesPerRowAlignment.toString(),
            capabilities.identitySupportedFormats(target).map { it.name }.sorted().joinToString(","),
            capabilities.minUniformBufferOffsetAlignment.toString(),
            capabilities.maxDynamicUniformBuffersPerPipelineLayout.toString(),
            capabilities.supportedOperations().map { it.name }.sorted().joinToString(","),
            capabilities.bufferAllocationPolicy.vertexFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.indexFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.uniformFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.growth.name,
            capabilities.supportedDepthStencilFormats().map { it.name }.sorted().joinToString(","),
            budget.maxFrameLocalBytes.toString(),
        ) + planCapabilityIdentityFacts(capabilities, target)
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value.encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class W4cCandidate(
        val owner: W4cPathFillPlanCompiler,
        override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor,
        draws: List<W4cSealedDrawV1>,
        val materialPlanTable: MaterialPlanTable?,
        override val capabilityId: String,
        val sourceTable: MaterialSourceConstructionTableV4,
    ) : GpuPlanCandidate {

        val draws: List<W4cSealedDrawV1> = Collections.unmodifiableList(
            draws.map { draw ->
                draw.copy(
                    pathF32 = W4cFramePreparationV1.snapshotPath(draw.pathF32),
                    transform = draw.transform.copy(),
                    scissorI32 = draw.scissorI32.copy(),
                )
            },
        )

        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId

        fun hasMatchingFingerprints(): Boolean =
            capabilityId in setOf(CAPABILITY_ID, W5B_CAPABILITY_ID) &&
                sceneCanonicalId == sceneFingerprint &&
                target.canonicalId == targetFingerprint
    }

    private class W7HardPathRootCandidate(
        val owner: W4cPathFillPlanCompiler,
        override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor,
        val frame: W7HardPathRootFrameV1,
    ) : GpuPlanCandidate {
        override val capabilityId: String = W7_HARD_PATH_ROOT_CAPABILITY_ID
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId

        fun hasMatchingFingerprints(): Boolean =
            sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint &&
                frame.sceneFingerprint == sceneFingerprint && frame.targetFingerprint == targetFingerprint &&
                frame.authenticates(owner)
    }

    public companion object {
        internal const val W7_HARD_PATH_ROOT_CAPABILITY_ID: String = "w7.w4c.root-drawcolor-path.v1"
        public const val W5B_CAPABILITY_ID: String = "w5b-path-fill-final-blend-v3"
        /** Historical public graph contract; it carries only legacy per-draw colors. */
        public const val HISTORICAL_CAPABILITY_ID: String =
            "solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1"
        public const val CAPABILITY_ID: String =
            "w5a-solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v2"

        public fun isHistoricalCapabilityId(capabilityId: String): Boolean =
            capabilityId == HISTORICAL_CAPABILITY_ID

        public fun isW5aMaterialCapabilityId(capabilityId: String): Boolean =
            capabilityId == CAPABILITY_ID

        internal val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        internal val REQUIRED_OPERATIONS = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        )
        internal const val MAX_DRAWS: Int = 512
        internal const val MAX_WINDING_STENCIL_EDGES: Int = 255
    }
}
