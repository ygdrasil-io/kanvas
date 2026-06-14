package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandSubmission
import org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionCapabilities
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContext
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionPreflightReport
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionPreflightRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUFirstRouteRenderSubmitRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTargetDescriptor
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Result from the diagnostic first-route WebGPU bridge.
 *
 * [submission] is the durable execution evidence. Completed readbacks expose
 * only hashes and byte counts through renderer contracts; [readbackBytesByRequestId]
 * remains an internal test hook so PM dumps do not grow backend pixel payloads.
 */
internal data class GpuRendererFirstRouteWebGpuSubmitEvidence(
    val submission: GPUCommandSubmission,
    val readbacks: List<GPUReadbackResult>,
    val readbackBytesByRequestId: Map<String, ByteArray> = emptyMap(),
)

/**
 * WebGPU target binding owned by [GpuRendererFirstRouteWebGpuSubmitter].
 *
 * The binding keeps the concrete [HeadlessTarget] private to `gpu-raster` while
 * exposing only Kanvas-owned target and resource facts to renderer contracts.
 * This follows Graphite's late resource materialization idea, adapted as a
 * small Kanvas registry instead of copying Graphite resource ownership classes.
 */
internal class GpuRendererFirstRouteWebGpuTargetBinding internal constructor(
    val surfaceTarget: GPUSurfaceTarget,
    val resourceRef: GPUTextureResourceRef,
    private val resourcePlanLabel: String,
    internal val target: HeadlessTarget,
    internal val colorView: GPUTextureView,
) {
    /** Builds materialization evidence for this registered WebGPU target. */
    fun materialization(
        taskIds: List<String> = emptyList(),
        resourcePlanLabels: List<String> = listOf(resourcePlanLabel),
    ): GPUResourceMaterializationDecision.Materialized =
        GPUResourceMaterializationDecision.Materialized(
            resources = listOf(resourceRef),
            targetId = surfaceTarget.targetId,
            taskIds = taskIds,
            resourcePlanLabels = resourcePlanLabels,
        )
}

/**
 * Opt-in first-route submitter for a single solid FillRect WebGPU path.
 *
 * This is not a product renderer switch. Callers must explicitly register a
 * headless target and pass a [GPUFirstRouteRenderSubmitRequest]. The submitter
 * returns [GPUCommandSubmission.Submitted] only after it encodes the generated
 * solid-rect WGSL path and calls the real WebGPU queue submit. Every unsupported
 * command, pipeline, payload, target, or resource shape is refused before any
 * backend work occurs.
 */
internal class GpuRendererFirstRouteWebGpuSubmitter(
    private val context: WebGpuContext,
    override val deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(1),
) : GPUExecutionContext, AutoCloseable {
    override val capabilities: GPUExecutionCapabilities =
        GPUExecutionCapabilities(render = true, readback = true)

    private val targetBindingsByRef = LinkedHashMap<String, GpuRendererFirstRouteWebGpuTargetBinding>()
    private var submissionCounter: Long = 0L

    private val bindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = io.ygdrasil.webgpu.GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )
    private val pipeline: GPURenderPipeline = context.device.createRenderPipeline(
        RenderPipelineDescriptor(
            layout = context.device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
            ),
            vertex = VertexState(
                module = context.device.createShaderModule(
                    ShaderModuleDescriptor(code = generatedSolidRectSource()),
                ),
                entryPoint = "vs_main",
            ),
            fragment = FragmentState(
                module = context.device.createShaderModule(
                    ShaderModuleDescriptor(code = generatedSolidRectSource()),
                ),
                entryPoint = "fs_main",
                targets = listOf(
                    ColorTargetState(
                        format = GPUTextureFormat.RGBA8Unorm,
                        blend = srcOverBlendState(),
                    ),
                ),
            ),
        ),
    )

    /** Creates and registers an RGBA8 headless target for diagnostic first-route submissions. */
    fun createHeadlessTarget(
        targetId: String,
        width: Int,
        height: Int,
        targetGeneration: Long = 1L,
    ): GpuRendererFirstRouteWebGpuTargetBinding {
        require(targetId.isNotBlank()) { "targetId must not be blank" }
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        val resourceRef = GPUTextureResourceRef("webgpu.first-route.$targetId.g$targetGeneration")
        require(resourceRef.value !in targetBindingsByRef) {
            "first-route target $targetId generation $targetGeneration is already registered"
        }
        val target = HeadlessTarget(
            context = context,
            width = width,
            height = height,
            format = GPUTextureFormat.RGBA8Unorm,
        )
        val binding = GpuRendererFirstRouteWebGpuTargetBinding(
            surfaceTarget = GPUSurfaceTarget(
                targetId = targetId,
                descriptor = GPUSurfaceTargetDescriptor(
                    width = width,
                    height = height,
                    colorFormat = "rgba8unorm",
                    surfaceBacked = false,
                    targetGeneration = targetGeneration,
                    usageLabels = setOf("render_attachment", "copy_src"),
                    readbackAvailable = true,
                ),
                deviceGeneration = deviceGeneration,
            ),
            resourceRef = resourceRef,
            resourcePlanLabel = "webgpu.headless-target.$targetId",
            target = target,
            colorView = target.colorTexture.createView(),
        )
        targetBindingsByRef[resourceRef.value] = binding
        return binding
    }

    override fun preflight(request: GPUExecutionPreflightRequest): GPUExecutionPreflightReport {
        val diagnostics = mutableListOf<GPUExecutionDiagnostic>()
        val scope = request.scope
        val target = request.target

        if (target != null) {
            diagnostics += target.descriptor.validateForUse(
                requiredUsageLabels = request.requiredTargetUsageLabels,
                targetLabel = target.targetId,
            )
            if (target.deviceGeneration != deviceGeneration) {
                diagnostics += GPUExecutionDiagnostic.deviceGenerationMismatch(
                    target = target,
                    expectedDeviceGeneration = deviceGeneration,
                )
            }
        }
        request.materializationDecision?.let { decision ->
            diagnostics += decision.executionPreflightDiagnostics()
        }
        if (scope is GPUCommandScope.Render) {
            if (scope.useTokenLabels.isEmpty()) {
                diagnostics += GPUExecutionDiagnostic.emptyRenderScope(scope)
            }
            if (request.passIds.isEmpty()) {
                diagnostics += GPUExecutionDiagnostic.emptyRenderPass(scope)
            }
        }
        if (!capabilities.supports(scope.commandClass)) {
            diagnostics += GPUExecutionDiagnostic.commandClassUnavailable(scope)
        }

        return GPUExecutionPreflightReport(
            scopeLabel = scope.scopeLabel,
            commandClass = scope.commandClass,
            targetId = target?.targetId,
            taskIds = request.taskIds,
            passIds = request.passIds,
            diagnostics = diagnostics,
        )
    }

    override fun submit(request: GPUFirstRouteRenderSubmitRequest): GPUCommandSubmission =
        submitInternal(command = null, request = request, completeReadback = false).submission

    /**
     * Submits a shadow-captured first-route FillRect and completes requested readbacks.
     *
     * [command] is checked against the request payload so this diagnostic bridge
     * proves the Kanvas payload, not the legacy `SkPaint`, drove the WebGPU work.
     */
    fun submitAndReadback(
        command: NormalizedDrawCommand.FillRect,
        request: GPUFirstRouteRenderSubmitRequest,
    ): GpuRendererFirstRouteWebGpuSubmitEvidence =
        submitInternal(command = command, request = request, completeReadback = true)

    private fun submitInternal(
        command: NormalizedDrawCommand.FillRect?,
        request: GPUFirstRouteRenderSubmitRequest,
        completeReadback: Boolean,
    ): GpuRendererFirstRouteWebGpuSubmitEvidence {
        val plan = validateRequest(command, request)
        if (plan is ValidatedSubmit.Refused) {
            return refusedEvidence(plan.diagnostic, request)
        }
        val accepted = plan as ValidatedSubmit.Accepted

        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = COLOR_UNIFORM_SIZE_BYTES,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "GpuRendererFirstRouteWebGpuSubmitter.color",
            ),
        )

        return try {
            context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(accepted.color))
            val bindGroup = context.device.createBindGroup(
                BindGroupDescriptor(
                    layout = bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    ),
                ),
            )
            val encoder = context.device.createCommandEncoder()
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = accepted.binding.colorView,
                            loadOp = GPULoadOp.Clear,
                            clearValue = Color(0.0, 0.0, 0.0, 0.0),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                setPipeline(pipeline)
                setBindGroup(0u, bindGroup)
                setScissorRect(
                    x = accepted.scissorX.toUInt(),
                    y = accepted.scissorY.toUInt(),
                    width = accepted.scissorWidth.toUInt(),
                    height = accepted.scissorHeight.toUInt(),
                )
                draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                end()
            }
            if (completeReadback && request.readbackRequests.isNotEmpty()) {
                accepted.binding.target.encodeCopyToStaging(encoder)
            }
            context.queue.submit(listOf(encoder.finish()))

            val readbackBytes = if (completeReadback && request.readbackRequests.isNotEmpty()) {
                runBlocking { accepted.binding.target.readPixels() }
            } else {
                null
            }
            val submission = submittedEvidence(request, accepted.binding)
            val readbacks = if (readbackBytes == null) {
                emptyList()
            } else {
                request.readbackRequests.map { readbackRequest ->
                    GPUReadbackResult.Completed(
                        request = readbackRequest,
                        payloadHash = "sha256:${sha256Hex(readbackBytes)}",
                        byteCount = readbackBytes.size.toLong(),
                    )
                }
            }
            GpuRendererFirstRouteWebGpuSubmitEvidence(
                submission = submission,
                readbacks = readbacks,
                readbackBytesByRequestId = readbackBytes?.let { bytes ->
                    request.readbackRequests.associate { readback -> readback.requestId to bytes.copyOf() }
                } ?: emptyMap(),
            )
        } catch (failure: Throwable) {
            val diagnostic = diagnostic(
                code = "failed.execution.first_route_backend_submit",
                message = "First-route WebGPU submit failed: ${failure.message ?: failure::class.simpleName}",
                request = request,
                facts = mapOf("exception" to (failure::class.qualifiedName ?: "unknown")),
            )
            GpuRendererFirstRouteWebGpuSubmitEvidence(
                submission = GPUCommandSubmission.Failed(diagnostic),
                readbacks = request.readbackRequests.map { readback ->
                    GPUReadbackResult.Refused(readback, diagnostic)
                },
            )
        } finally {
            uniform.close()
        }
    }

    private fun validateRequest(
        command: NormalizedDrawCommand.FillRect?,
        request: GPUFirstRouteRenderSubmitRequest,
    ): ValidatedSubmit {
        preflight(request.preflightRequest).diagnostics.firstOrNull { diagnostic -> diagnostic.terminal }?.let {
            return ValidatedSubmit.Refused(it.withSubmitFacts(request))
        }

        val resourceRef = request.materialization.resources.singleOrNull()
            ?: return refused("unsupported.execution.first_route_resource_count", request)
        val binding = targetBindingsByRef[resourceRef.value]
            ?: return refused("unsupported.execution.first_route_resource_unbound", request)
        val target = request.preflightRequest.target
            ?: return refused("unsupported.execution.first_route_target_missing", request)

        if (request.materialization.targetId != binding.surfaceTarget.targetId ||
            target.targetId != binding.surfaceTarget.targetId
        ) {
            return refused("unsupported.execution.first_route_target_mismatch", request)
        }
        if (target.descriptor != binding.surfaceTarget.descriptor) {
            return refused("unsupported.execution.first_route_target_descriptor_mismatch", request)
        }

        val preimage = request.pipelinePlan.preimage as? GPUPipelineKeyPreimage.Render
            ?: return refused("unsupported.execution.first_route_pipeline_not_render", request)
        if (preimage.renderStepIdentity != "fill-rect" ||
            preimage.targetFormatClass != "rgba8unorm" ||
            preimage.blendStateHash != "blend:src-over"
        ) {
            return refused("unsupported.execution.first_route_pipeline_unsupported", request)
        }

        val invocation = request.pass.invocations.singleOrNull()
            ?: return refused("unsupported.execution.first_route_invocation_count", request)
        if (request.pass.pipelineKeys.singleOrNull() != request.pipelinePlan.cacheKey.value ||
            invocation.pipelineKeyHash != request.pipelinePlan.cacheKey.value
        ) {
            return refused("unsupported.execution.first_route_pipeline_key_mismatch", request)
        }
        val payloadRef = request.payloadRefs.singleOrNull()
            ?: return refused("unsupported.execution.first_route_payload_count", request)
        if (invocation.commandIdValue != payloadRef.commandIdValue ||
            invocation.renderStepId.value != payloadRef.renderStepIdentity
        ) {
            return refused("unsupported.execution.first_route_payload_invocation_mismatch", request)
        }

        val payload = decodeSolidPayload(payloadRef)
            ?: return refused("unsupported.execution.first_route_payload_shape", request)
        if (payload.radii.any { radius -> radius != 0f }) {
            return refused("unsupported.execution.first_route_radii_unsupported", request)
        }
        if (!payload.isPixelAligned()) {
            return refused("unsupported.execution.first_route_rect_not_pixel_aligned", request)
        }
        val scissorX = payload.left.roundToInt()
        val scissorY = payload.top.roundToInt()
        val scissorRight = payload.right.roundToInt()
        val scissorBottom = payload.bottom.roundToInt()
        if (scissorX < 0 || scissorY < 0 ||
            scissorRight > binding.surfaceTarget.descriptor.width ||
            scissorBottom > binding.surfaceTarget.descriptor.height ||
            scissorX >= scissorRight ||
            scissorY >= scissorBottom
        ) {
            return refused("unsupported.execution.first_route_rect_outside_target", request)
        }
        if (payload.color.any { component -> component !in 0f..1f }) {
            return refused("unsupported.execution.first_route_color_unsupported", request)
        }

        if (command != null) {
            val commandDiagnostic = validateCommand(command, payload, request)
            if (commandDiagnostic != null) {
                return ValidatedSubmit.Refused(commandDiagnostic)
            }
        }
        val fullTargetBoundsLabel =
            "0,0 ${binding.surfaceTarget.descriptor.width}x${binding.surfaceTarget.descriptor.height}"
        request.readbackRequests.firstOrNull { readback -> readback.failureReason != null }?.let {
            return refused("unsupported.execution.first_route_readback_failure_reason", request)
        }
        request.readbackRequests.firstOrNull { readback -> readback.boundsLabel != fullTargetBoundsLabel }?.let {
            return refused("unsupported.execution.first_route_readback_bounds", request)
        }
        request.readbackRequests.firstOrNull { readback -> readback.format != "rgba8unorm" }?.let {
            return refused("unsupported.execution.first_route_readback_format", request)
        }

        return ValidatedSubmit.Accepted(
            binding = binding,
            scissorX = scissorX,
            scissorY = scissorY,
            scissorWidth = scissorRight - scissorX,
            scissorHeight = scissorBottom - scissorY,
            color = payload.color,
        )
    }

    private fun validateCommand(
        command: NormalizedDrawCommand.FillRect,
        payload: SolidPayload,
        request: GPUFirstRouteRenderSubmitRequest,
    ): GPUExecutionDiagnostic? {
        if (command.source.adapter != "GpuRendererShadowAdapter") {
            return diagnostic("unsupported.execution.first_route_command_source", request)
        }
        if (command.transform.type != GPUTransformType.Identity) {
            return diagnostic("unsupported.execution.first_route_transform", request)
        }
        if (command.clip.kind != GPUClipKind.WideOpen) {
            return diagnostic("unsupported.execution.first_route_clip", request)
        }
        if (command.blend.kind != GPUBlendKind.SrcOver || command.blend.requiresDestinationRead) {
            return diagnostic("unsupported.execution.first_route_blend", request)
        }
        val material = command.material as? GPUMaterialDescriptor.SolidColor
            ?: return diagnostic("unsupported.execution.first_route_material", request)
        if (command.commandId.value != request.payloadRefs.single().commandIdValue ||
            command.rect.left != payload.left ||
            command.rect.top != payload.top ||
            command.rect.right != payload.right ||
            command.rect.bottom != payload.bottom ||
            material.r != payload.color[0] ||
            material.g != payload.color[1] ||
            material.b != payload.color[2] ||
            material.a != payload.color[3]
        ) {
            return diagnostic("unsupported.execution.first_route_command_payload_mismatch", request)
        }
        return null
    }

    private fun decodeSolidPayload(payloadRef: GPUDrawPayloadRef): SolidPayload? {
        val block = payloadRef.uniformBlock ?: return null
        if (block.byteSize != SOLID_PAYLOAD_SIZE_BYTES || block.bytes.size != SOLID_PAYLOAD_SIZE_BYTES.toInt()) {
            return null
        }
        val bytes = block.bytes.map { byte -> byte.toByte() }.toByteArray()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val left = buffer.getFloat(0)
        val top = buffer.getFloat(4)
        val right = buffer.getFloat(8)
        val bottom = buffer.getFloat(12)
        val radii = floatArrayOf(
            buffer.getFloat(16),
            buffer.getFloat(20),
            buffer.getFloat(24),
            buffer.getFloat(28),
        )
        val color = floatArrayOf(
            buffer.getFloat(32),
            buffer.getFloat(36),
            buffer.getFloat(40),
            buffer.getFloat(44),
        )
        return SolidPayload(left, top, right, bottom, radii, color)
    }

    private fun submittedEvidence(
        request: GPUFirstRouteRenderSubmitRequest,
        binding: GpuRendererFirstRouteWebGpuTargetBinding,
    ): GPUCommandSubmission.Submitted =
        GPUCommandSubmission.Submitted(
            submissionId = "webgpu.first-route.${++submissionCounter}",
            scopeLabel = request.preflightRequest.scope.scopeLabel,
            deviceGeneration = deviceGeneration,
            targetGeneration = binding.surfaceTarget.descriptor.targetGeneration,
            scopeLabels = listOf(request.preflightRequest.scope.scopeLabel),
            taskIds = request.materialization.taskIds,
            passIds = listOf(request.pass.passId),
            resourceUsageSummary = listOf("${binding.surfaceTarget.targetId}:copy_src,render_attachment"),
            submittedRouteCounts = mapOf("GPUNative" to 1),
            readbackRequests = request.readbackRequests,
        )

    private fun refused(
        code: String,
        request: GPUFirstRouteRenderSubmitRequest,
    ): ValidatedSubmit.Refused =
        ValidatedSubmit.Refused(diagnostic(code, request))

    private fun refusedEvidence(
        diagnostic: GPUExecutionDiagnostic,
        request: GPUFirstRouteRenderSubmitRequest,
    ): GpuRendererFirstRouteWebGpuSubmitEvidence =
        GpuRendererFirstRouteWebGpuSubmitEvidence(
            submission = GPUCommandSubmission.Refused(diagnostic),
            readbacks = request.readbackRequests.map { readback ->
                GPUReadbackResult.Refused(readback, diagnostic)
            },
        )

    private fun diagnostic(
        code: String,
        request: GPUFirstRouteRenderSubmitRequest,
        message: String = "First-route WebGPU submit refused: $code",
        facts: Map<String, String> = emptyMap(),
    ): GPUExecutionDiagnostic =
        GPUExecutionDiagnostic(
            code = code,
            stage = "submit",
            message = message,
            terminal = true,
            facts = request.submitFacts() + facts,
        )

    private fun GPUExecutionDiagnostic.withSubmitFacts(
        request: GPUFirstRouteRenderSubmitRequest,
    ): GPUExecutionDiagnostic =
        copy(facts = request.submitFacts() + facts)

    private fun GPUResourceMaterializationDecision.executionPreflightDiagnostics(): List<GPUExecutionDiagnostic> =
        when (this) {
            is GPUResourceMaterializationDecision.Materialized ->
                diagnostics.map { diagnostic ->
                    diagnostic.toExecutionPreflightDiagnostic(
                        materializationOutcome = "materialized",
                        targetId = targetId,
                        taskIds = taskIds,
                        resourcePlanLabels = resourcePlanLabels,
                    )
                }
            is GPUResourceMaterializationDecision.Deferred ->
                listOf(
                    GPUExecutionDiagnostic(
                        code = reasonCode,
                        stage = "materialization",
                        message = "Resource materialization for target $targetId is deferred: $reasonCode.",
                        terminal = true,
                        facts = materializationFacts(
                            materializationOutcome = "deferred",
                            targetId = targetId,
                            taskIds = taskIds,
                            resourcePlanLabels = resourcePlanLabels,
                        ),
                    ),
                ) + diagnostics.map { diagnostic ->
                    diagnostic.toExecutionPreflightDiagnostic(
                        materializationOutcome = "deferred",
                        targetId = targetId,
                        taskIds = taskIds,
                        resourcePlanLabels = resourcePlanLabels,
                    )
                }
            is GPUResourceMaterializationDecision.Refused ->
                diagnostics.map { diagnostic ->
                    diagnostic.toExecutionPreflightDiagnostic(
                        materializationOutcome = "refused",
                        targetId = targetId,
                        taskIds = taskIds,
                        resourcePlanLabels = resourcePlanLabels,
                    )
                }
        }

    private fun GPUResourceDiagnostic.toExecutionPreflightDiagnostic(
        materializationOutcome: String,
        targetId: String,
        taskIds: List<String>,
        resourcePlanLabels: List<String>,
    ): GPUExecutionDiagnostic =
        GPUExecutionDiagnostic(
            code = code,
            stage = "materialization",
            message = message,
            terminal = terminal,
            facts = facts + materializationFacts(
                materializationOutcome = materializationOutcome,
                targetId = targetId,
                taskIds = taskIds,
                resourcePlanLabels = resourcePlanLabels,
                resourceLabel = resourceLabel,
            ),
        )

    private fun materializationFacts(
        materializationOutcome: String,
        targetId: String,
        taskIds: List<String>,
        resourcePlanLabels: List<String>,
        resourceLabel: String? = null,
    ): Map<String, String> {
        val facts = mutableMapOf(
            "materializationOutcome" to materializationOutcome,
            "resourcePlanLabels" to resourcePlanLabels.dumpSortedList(),
            "targetId" to targetId,
            "taskIds" to taskIds.dumpSequence(),
        )
        if (resourceLabel != null) {
            facts["resourceLabel"] = resourceLabel
        }
        return facts
    }

    override fun close() {
        targetBindingsByRef.values.forEach { binding ->
            binding.colorView.close()
            binding.target.close()
        }
        targetBindingsByRef.clear()
    }

    private sealed interface ValidatedSubmit {
        data class Accepted(
            val binding: GpuRendererFirstRouteWebGpuTargetBinding,
            val scissorX: Int,
            val scissorY: Int,
            val scissorWidth: Int,
            val scissorHeight: Int,
            val color: FloatArray,
        ) : ValidatedSubmit

        data class Refused(val diagnostic: GPUExecutionDiagnostic) : ValidatedSubmit
    }

    private data class SolidPayload(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radii: FloatArray,
        val color: FloatArray,
    ) {
        fun isPixelAligned(): Boolean =
            listOf(left, top, right, bottom).all { value ->
                value.isFinite() && value.roundToInt().toFloat() == value
            }
    }

    private companion object {
        private const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u
        private const val COLOR_UNIFORM_SIZE_BYTES: ULong = 16uL
        private const val SOLID_PAYLOAD_SIZE_BYTES: Long = 64L

        private fun generatedSolidRectSource(): String {
            val source = GeneratedSolidRectWgsl.generateDeterministic()
            val validation = GeneratedSolidRectWgsl.validate(source)
            require(validation.isSuccess) {
                "Generated solid rect WGSL failed validation: ${validation.diagnostics.joinToString("; ")}"
            }
            return source
        }

        private fun srcOverBlendState(): BlendState {
            val component = BlendComponent(
                operation = GPUBlendOperation.Add,
                srcFactor = GPUBlendFactor.One,
                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
            )
            return BlendState(color = component, alpha = component)
        }

        private fun List<String>.dumpSortedList(): String =
            if (isEmpty()) "none" else sorted().joinToString(",")

        private fun List<String>.dumpSequence(): String =
            if (isEmpty()) "none" else joinToString(",")

        private fun GPUFirstRouteRenderSubmitRequest.submitFacts(): Map<String, String> {
            val scope = preflightRequest.scope
            return mapOf(
                "commandClass" to scope.commandClass.name,
                "materializedResourceCount" to materialization.resources.size.toString(),
                "passIds" to pass.passId,
                "payloadCommands" to payloadRefs.joinToString(",") { ref -> ref.commandIdValue.toString() },
                "pipelineCacheKey" to pipelinePlan.cacheKey.value,
                "pipelineKeys" to pass.pipelineKeys.joinToString(",").ifBlank { "none" },
                "readbackRequests" to readbackRequests.joinToString(",") { request -> request.requestId }.ifBlank { "none" },
                "resourcePlans" to materialization.resourcePlanLabels.sorted().joinToString(",").ifBlank { "none" },
                "scopeLabel" to scope.scopeLabel,
                "targetId" to materialization.targetId,
                "tasks" to materialization.taskIds.joinToString(",").ifBlank { "none" },
            )
        }

        private fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
