package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Verifies resource-provider refusal, descriptor usage, and lease diagnostics. */
class GPUResourceProviderTest {
    @Test
    fun `materialized keeps positional compatibility and snapshots public buffer resources`() {
        val positional = GPUResourceMaterializationDecision.Materialized(
            emptyList(),
            emptyList<GPUResourceDiagnostic>(),
            "root-target",
        )
        assertEquals("root-target", positional.targetId)

        val mutableBuffers = mutableListOf(GPUBufferResourceRef("buffer:first"))
        val decision = GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            bufferResources = mutableBuffers,
        )
        mutableBuffers += GPUBufferResourceRef("buffer:late")

        assertEquals(listOf(GPUBufferResourceRef("buffer:first")), decision.bufferResources)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (decision.bufferResources as MutableList<GPUBufferResourceRef>) += GPUBufferResourceRef("buffer:mutated")
        }
    }

    /** Ensures an unconfigured provider refuses instead of throwing or faking success. */
    @Test
    fun `resource provider test double refuses materialization by default`() {
        val provider = RefusingResourceProviderDouble()

        val decision = provider.materialize(
            plan = GPUTextureAllocationPlan.CreateTexture(
                descriptor = targetTextureDescriptor(),
                ownership = GPUTextureOwnershipPlan(
                    ownerLabel = "root-target",
                    lifetimeClass = "frame-local",
                    releasePolicy = "frame-end",
                    canAliasScratch = false,
                ),
            ),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals("unsupported.resource.provider_unconfigured", refused.diagnostic.code)
        assertEquals("root-target", refused.diagnostic.resourceLabel)
        assertEquals(
            listOf(
                "resource.materialization:refused target=root-target tasks=none resourcePlans=root-target code=unsupported.resource.provider_unconfigured terminal=true",
                "resource.diagnostic code=unsupported.resource.provider_unconfigured resource=root-target terminal=true facts=targetId=root-target",
            ),
            refused.dumpLines(),
        )
    }

    /** Ensures an unconfigured provider never treats a surface lease as success. */
    @Test
    fun `default resource provider refuses leased surface texture`() {
        val provider = RefusingResourceProviderDouble()

        val decision = provider.materialize(
            plan = GPUTextureAllocationPlan.LeaseSurfaceTexture(targetId = "root-target"),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals("unsupported.resource.provider_unconfigured", refused.diagnostic.code)
        assertEquals(
            listOf(
                "resource.materialization:refused target=root-target tasks=none resourcePlans=root-target code=unsupported.resource.provider_unconfigured terminal=true",
                "resource.diagnostic code=unsupported.resource.provider_unconfigured resource=root-target terminal=true facts=targetId=root-target",
            ),
            refused.dumpLines(),
        )
    }

    /** Ensures the opt-in provider revalidates and materializes only an already acquired lease. */
    @Test
    fun `surface lease provider materializes current lease without dumping resource ref`() {
        val lease = currentSurfaceLease(resourceRefValue = "surface-secret-handle-never-dump")
        val provider = RevalidatingSurfaceLeaseResourceProvider(
            request = surfaceLeaseRequest(
                lease = lease,
                taskIds = listOf("task-present", "task-fill-rect"),
            ),
        )

        val decision = provider.materialize(
            plan = GPUTextureAllocationPlan.LeaseSurfaceTexture(targetId = "root-target"),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals(listOf(lease.resourceRef), materialized.resources)
        assertEquals(emptyList(), materialized.diagnostics)
        assertEquals(
            listOf(
                "resource.materialization:materialized target=root-target tasks=task-fill-rect,task-present resourcePlans=root-target resourceCount=1 diagnostics=none",
            ),
            materialized.dumpLines(),
        )
        assertFalse(materialized.dumpLines().joinToString("\n").contains("surface-secret-handle-never-dump"))
    }

    /** Ensures the opt-in lease provider does not expand support to unrelated allocation plans. */
    @Test
    fun `surface lease provider refuses non surface allocation plans`() {
        val provider = RevalidatingSurfaceLeaseResourceProvider(
            request = surfaceLeaseRequest(),
        )
        val context = targetPreparationContext()

        val created = provider.materialize(
            plan = createTexturePlan(),
            context = context,
        )
        val imported = provider.materialize(
            plan = GPUTextureAllocationPlan.ImportExternalTexture(
                descriptor = GPUImportedTextureDescriptor(
                    externalId = "ExternalTextureHandle@0xDEADBEEF",
                    descriptor = targetTextureDescriptor(),
                    releasePolicy = "caller-owned",
                ),
                planLabel = "imported-camera-frame",
            ),
            context = context,
        )

        assertEquals(
            "unsupported.resource.plan_not_supported",
            assertIs<GPUResourceMaterializationDecision.Refused>(created).diagnostic.code,
        )
        assertEquals(
            "unsupported.resource.plan_not_supported",
            assertIs<GPUResourceMaterializationDecision.Refused>(imported).diagnostic.code,
        )
        assertFalse((created.dumpLines() + imported.dumpLines()).joinToString("\n").contains("ExternalTextureHandle"))
    }

    /** Ensures an expired surface lease remains a terminal late materialization refusal. */
    @Test
    fun `surface lease provider refuses expired lease`() {
        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(
                lease = currentSurfaceLease(expiredReason = "presented"),
            ),
            expectedCode = "stale.texture.surface_lease",
        )
    }

    /** Ensures an evicted surface resource remains a terminal late materialization refusal. */
    @Test
    fun `surface lease provider refuses evicted lease`() {
        val refused = assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(
                lease = currentSurfaceLease(evictedReason = "resource-cache-purge"),
            ),
            expectedCode = "unsupported.resource.evicted",
        )

        val dump = refused.dumpLines().joinToString("\n")
        assertContains(dump, "resource.materialization:refused")
        assertContains(dump, "resource.diagnostic code=unsupported.resource.evicted")
        assertFalse(dump.contains("execution.submission"))
    }

    /** Ensures missing required usage prevents lease materialization. */
    @Test
    fun `surface lease provider refuses missing required usage`() {
        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(
                lease = currentSurfaceLease(usageLabels = setOf("render_attachment")),
                requiredUsageLabels = setOf("render_attachment", "copy_src"),
            ),
            expectedCode = "unsupported.texture.usage_missing",
        )
    }

    /** Ensures a stale device generation prevents lease materialization. */
    @Test
    fun `surface lease provider refuses device mismatch`() {
        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(
                lease = currentSurfaceLease(deviceGeneration = 10),
            ),
            expectedCode = "unsupported.texture.device_generation_stale",
        )
    }

    /** Ensures plan, context, and lease target IDs must describe the same target. */
    @Test
    fun `surface lease provider refuses target mismatch`() {
        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(),
            plan = GPUTextureAllocationPlan.LeaseSurfaceTexture(targetId = "other-target"),
            expectedCode = "unsupported.texture.surface_lease_target_mismatch",
        )
    }

    /** Ensures a stale frame generation prevents lease materialization. */
    @Test
    fun `surface lease provider refuses frame mismatch`() {
        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(expectedFrameGeneration = 2),
            expectedCode = "unsupported.texture.frame_generation_stale",
        )
    }

    /** Ensures the active attachment cannot be sampled through the lease provider. */
    @Test
    fun `surface lease provider refuses active attachment sampled`() {
        val activeRef = GPUTextureResourceRef("surface-secret-handle-never-dump")

        assertSurfaceLeaseRefused(
            request = surfaceLeaseRequest(
                lease = currentSurfaceLease(resourceRefValue = activeRef.value),
                sampled = true,
                activeAttachmentRef = activeRef,
            ),
            expectedCode = "unsupported.texture.active_attachment_sampled",
            forbiddenDumpText = activeRef.value,
        )
    }

    /** Ensures a refused surface lease preserves every failed invariant for PM evidence. */
    @Test
    fun `surface lease provider preserves all validation diagnostics on refusal`() {
        val activeRef = GPUTextureResourceRef("surface-secret-handle-never-dump")
        val request = surfaceLeaseRequest(
            lease = currentSurfaceLease(
                targetGeneration = 6,
                frameGeneration = 0,
                deviceGeneration = 10,
                resourceRefValue = activeRef.value,
                usageLabels = setOf("render_attachment"),
                expiredReason = "presented",
            ),
            requiredUsageLabels = setOf("render_attachment", "copy_src"),
            expectedTargetGeneration = 7,
            expectedFrameGeneration = 1,
            sampled = true,
            activeAttachmentRef = activeRef,
        )

        val decision = RevalidatingSurfaceLeaseResourceProvider(request).materialize(
            plan = GPUTextureAllocationPlan.LeaseSurfaceTexture(targetId = "root-target"),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals(
            listOf(
                "stale.texture.surface_lease",
                "unsupported.texture.device_generation_stale",
                "unsupported.texture.target_generation_stale",
                "unsupported.texture.frame_generation_stale",
                "unsupported.texture.usage_missing",
                "unsupported.texture.active_attachment_sampled",
            ),
            refused.diagnostics.map { diagnostic -> diagnostic.code },
        )
        val dump = refused.dumpLines().joinToString("\n")
        assertContains(dump, "resource.diagnostic code=unsupported.texture.usage_missing")
        assertContains(dump, "resource.diagnostic code=unsupported.texture.active_attachment_sampled")
        assertFalse(dump.contains(activeRef.value))
    }

    /** Ensures target preparation context dumps expose PM facts without backend handles. */
    @Test
    fun `target preparation context dump exposes deterministic pm evidence`() {
        val context = GPUTargetPreparationContext(
            targetId = "root-target",
            frameId = "frame-42",
            deviceGeneration = 17,
            budgetClass = "interactive",
        )

        val lines = context.dumpLines()

        assertEquals(
            listOf(
                "resource.target_preparation target=root-target frame=frame-42 deviceGeneration=17 budgetClass=interactive",
            ),
            lines,
        )
        assertFalse(lines.joinToString("\n").contains("handle"))
    }

    /** Ensures an allocation plan that already refused preserves its stable diagnostic evidence. */
    @Test
    fun `resource provider preserves explicit refused allocation diagnostics`() {
        val provider = RefusingResourceProviderDouble()
        val diagnostic = GPUResourceDiagnostic(
            code = "unsupported.texture.promise_missing",
            resourceLabel = "promised-image",
            message = "Promise was not fulfilled before materialization.",
            terminal = true,
        )

        val decision = provider.materialize(
            plan = GPUTextureAllocationPlan.Refuse(diagnostic),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals(diagnostic, refused.diagnostic)
    }

    /** Ensures texture usage requirements fail with a stable diagnostic before allocation. */
    @Test
    fun `texture descriptor validation refuses missing required usage flag`() {
        val descriptor = targetTextureDescriptor(usageLabels = setOf("render_attachment"))

        val diagnostic = descriptor.validateRequiredUsage(
            requiredUsageLabels = setOf("render_attachment", "copy_src"),
            resourceLabel = "swapchain-target",
        )

        val missingUsage = requireNotNull(diagnostic)
        assertEquals("unsupported.texture.usage_missing", missingUsage.code)
        assertContains(missingUsage.message, "copy_src")
        assertNull(
            descriptor.validateRequiredUsage(
                requiredUsageLabels = setOf("render_attachment"),
                resourceLabel = "swapchain-target",
            ),
        )
    }

    /** Ensures surface leases report stale generation, usage, and attachment sampling evidence. */
    @Test
    fun `surface lease validation reports expired generation usage and active attachment refusals`() {
        val activeRef = GPUTextureResourceRef("surface-texture")
        val lease = GPUSurfaceTextureLease(
            targetId = "surface",
            targetGeneration = 7,
            frameGeneration = 3,
            deviceGeneration = 11,
            resourceRef = activeRef,
            useToken = GPUUseToken(42),
            usageLabels = setOf("render_attachment"),
            expiredReason = "presented",
        )

        val diagnostics = lease.validateForUse(
            requiredUsageLabels = setOf("texture_binding", "copy_src"),
            expectedTargetGeneration = 8,
            expectedFrameGeneration = 3,
            expectedDeviceGeneration = 12,
            sampled = true,
            activeAttachmentRef = activeRef,
        )

        assertEquals(
            listOf(
                "stale.texture.surface_lease",
                "unsupported.texture.device_generation_stale",
                "unsupported.texture.target_generation_stale",
                "unsupported.texture.usage_missing",
                "unsupported.texture.active_attachment_sampled",
            ),
            diagnostics.map { it.code },
        )
        assertContains(diagnostics.joinToString("\n") { it.message }, "texture_binding")
        assertContains(diagnostics.joinToString("\n") { it.message }, "presented")
    }

    /** Ensures stale frame-generation leases are refused even when target and device match. */
    @Test
    fun `surface lease validation reports stale frame generation separately`() {
        val lease = GPUSurfaceTextureLease(
            targetId = "surface",
            targetGeneration = 7,
            frameGeneration = 3,
            deviceGeneration = 11,
            resourceRef = GPUTextureResourceRef("surface-texture"),
            useToken = GPUUseToken(42),
            usageLabels = setOf("render_attachment"),
        )

        val diagnostics = lease.validateForUse(
            requiredUsageLabels = setOf("render_attachment"),
            expectedTargetGeneration = 7,
            expectedFrameGeneration = 4,
            expectedDeviceGeneration = 11,
        )

        assertEquals(
            listOf("unsupported.texture.frame_generation_stale"),
            diagnostics.map { it.code },
        )
        assertContains(diagnostics.single().message, "frame generation 3")
    }

    /** Ensures late materialization helpers expose required refusal evidence. */
    @Test
    fun `materialization helpers produce upload budget and pipeline failure diagnostics`() {
        val upload = GPUResourceMaterializationDecision.refusedUploadBudgetExceeded(
            resourceLabel = "solid-upload",
            requestedBytes = 4096,
            budgetBytes = 1024,
        )
        val pipeline = GPUResourceMaterializationDecision.refusedPipelineCreationFailure(
            resourceLabel = "solid-pipeline",
            reason = "blend feature missing",
        )

        assertEquals(
            "budget.resource.upload_exceeded",
            assertIs<GPUResourceMaterializationDecision.Refused>(upload).diagnostic.code,
        )
        assertEquals(
            "capability.pipeline.missing_feature",
            assertIs<GPUResourceMaterializationDecision.Refused>(pipeline).diagnostic.code,
        )
    }

    /** Ensures materialized resource dumps expose PM facts without backend handles. */
    @Test
    fun `materialized resource decision dump includes target task resource facts without handles`() {
        val decision = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("backend-handle-never-dump")),
            diagnostics = listOf(
                GPUResourceDiagnostic.textureUsageMissing(
                    resourceLabel = "surface-target",
                    missingUsageLabels = setOf("copy_src"),
                    availableUsageLabels = setOf("render_attachment"),
                ),
            ),
            targetId = "root-target",
            taskIds = listOf("task-fill-rect"),
            resourcePlanLabels = listOf("surface-target", "solid-upload"),
        )

        val lines = decision.dumpLines()

        assertEquals(
            listOf(
                "resource.materialization:materialized target=root-target tasks=task-fill-rect resourcePlans=solid-upload,surface-target resourceCount=1 diagnostics=unsupported.texture.usage_missing",
                "resource.diagnostic code=unsupported.texture.usage_missing resource=surface-target terminal=true facts=availableUsageLabels=render_attachment;missingUsageLabels=copy_src",
            ),
            lines,
        )
        assertFalse(lines.joinToString("\n").contains("backend-handle-never-dump"))
    }

    /** Ensures the provider bridge materializes scoped command operands for an accepted route. */
    @Test
    fun `command operand provider materializes bridge references`() {
        val decision = ValidatingCommandOperandResourceProvider().materializeCommandOperands(
            request = commandOperandMaterializationRequest(),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals(emptyList(), materialized.resources)
        assertEquals(
            listOf(
                null to "beginRenderPass",
                "packet-1" to "setRenderPipeline",
                "packet-1" to "setBindGroup",
                "packet-1" to "draw",
            ),
            materialized.dumpOperandBridgeSnapshot.map { bridge -> bridge.packetId to bridge.commandLabel },
        )
        assertEquals(
            listOf(
                "target-view:root",
                "render-pipeline:solid-fill",
                "bind-group:solid-fill:packet-1",
                "vertex-buffer:solid-quad",
            ),
            materialized.dumpOperandBridgeSnapshot.map { bridge -> bridge.operand.label },
        )
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=target-view:root kind=render-target " +
                "deviceGeneration=11 owner=render-pass:main-pass usage=render_attachment " +
                "invalidation=frame-end descriptor=sha256:target-view:root facts=loadStore=clear-store",
        )
    }

    /** Ensures command operand refs reject raw backend handles before they can be dumped. */
    @Test
    fun `command operand references reject handle like dump fields`() {
        assertFailsWith<IllegalArgumentException> {
            commandOperandRef(
                label = "WGPUTexture@0xDEADBEEF",
                kind = GPUMaterializedCommandOperandKind.Texture,
                usageLabels = listOf("texture_binding"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            commandOperandRef(
                label = "texture-binding",
                kind = GPUMaterializedCommandOperandKind.Texture,
                usageLabels = listOf("texture_binding"),
                evidenceFacts = mapOf("backend" to "WGPUBindGroup@0xDEADBEEF"),
            )
        }
    }

    /** Ensures refused provider operand plans cannot leak raw handles through diagnostics. */
    @Test
    fun `command operand materialization plans reject handle like refusal fields`() {
        assertFailsWith<IllegalArgumentException> {
            commandOperandPlan(
                packetId = "packet-1",
                commandLabel = "setBindGroup",
                label = "WGPUBindGroup@0xDEADBEEF",
                kind = GPUMaterializedCommandOperandKind.BindGroup,
                usageLabels = setOf("uniform"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            commandOperandPlan(
                packetId = "packet-1",
                commandLabel = "setBindGroup",
                label = "bind-group:solid-fill",
                kind = GPUMaterializedCommandOperandKind.BindGroup,
                usageLabels = setOf("uniform"),
                evictedReason = "WGPUResourceHandle@0xFEEDFACE",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUMaterializedCommandOperandBinding(
                packetId = "WGPUCommandBuffer@0xFEEDFACE",
                commandLabel = "setBindGroup",
                operand = commandOperandRef(
                    label = "bind-group:solid-fill",
                    kind = GPUMaterializedCommandOperandKind.BindGroup,
                    usageLabels = listOf("uniform"),
                ),
            )
        }
    }

    /** Ensures accepted first-route materialization dumps scoped command operands without handles. */
    @Test
    fun `materialized resource decision dump includes command operand references`() {
        val decision = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("backend-handle-never-dump")),
            operandRefs = listOf(
                commandOperandRef(
                    label = "render-pipeline:solid-fill",
                    kind = GPUMaterializedCommandOperandKind.RenderPipeline,
                    usageLabels = listOf("render"),
                    invalidationPolicy = "device-generation",
                    evidenceFacts = mapOf("cache" to "warm"),
                ),
                commandOperandRef(
                    label = "bind-group:solid-fill:packet-1",
                    kind = GPUMaterializedCommandOperandKind.BindGroup,
                    usageLabels = listOf("uniform", "texture_binding"),
                    evidenceFacts = mapOf("layout" to "layout-solid-v1"),
                ),
            ),
            targetId = "root-target",
            taskIds = listOf("task-fill-rect"),
            resourcePlanLabels = listOf("first-route"),
        )

        val lines = decision.dumpLines()

        assertEquals(
            "resource.materialization:materialized target=root-target tasks=task-fill-rect " +
                "resourcePlans=first-route resourceCount=1 operands=2 diagnostics=none",
            lines.first(),
        )
        assertContains(
            lines,
            "resource.materialization:operand operand=render-pipeline:solid-fill kind=render-pipeline " +
                "deviceGeneration=11 owner=render-pass:main-pass usage=render " +
                "invalidation=device-generation descriptor=sha256:render-pipeline:solid-fill facts=cache=warm",
        )
        assertContains(
            lines,
            "resource.materialization:operand operand=bind-group:solid-fill:packet-1 kind=bind-group " +
                "deviceGeneration=11 owner=render-pass:main-pass usage=texture_binding,uniform " +
                "invalidation=pass-end descriptor=sha256:bind-group:solid-fill:packet-1 " +
                "facts=layout=layout-solid-v1",
        )
        assertFalse(lines.joinToString("\n").contains("backend-handle-never-dump"))
        assertFalse(lines.joinToString("\n").contains("WGPU"))
    }

    /** Ensures mutable diagnostic, task, and resource-plan inputs cannot rewrite dump evidence. */
    @Test
    fun `resource materialization dump snapshots mutable diagnostics tasks and resource plans`() {
        val diagnosticFacts = mutableMapOf("rank" to "1")
        val diagnostics = mutableListOf(
            GPUResourceDiagnostic(
                code = "unstable.resource",
                resourceLabel = "resource-b",
                message = "Original diagnostic.",
                terminal = true,
                facts = diagnosticFacts,
            ),
        )
        val taskIds = mutableListOf("task-b")
        val resourcePlanLabels = mutableListOf("plan-b")
        val decision = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("backend-handle-never-dump")),
            diagnostics = diagnostics,
            targetId = "root-target",
            taskIds = taskIds,
            resourcePlanLabels = resourcePlanLabels,
        )

        diagnosticFacts["rank"] = "2"
        diagnostics += GPUResourceDiagnostic(
            code = "mutated.resource",
            resourceLabel = "resource-a",
            message = "Mutated diagnostic.",
            terminal = true,
        )
        taskIds += "task-a"
        resourcePlanLabels += "plan-a"

        assertEquals(
            listOf(
                "resource.materialization:materialized target=root-target tasks=task-b resourcePlans=plan-b resourceCount=1 diagnostics=unstable.resource",
                "resource.diagnostic code=unstable.resource resource=resource-b terminal=true facts=rank=1",
            ),
            decision.dumpLines(),
        )
    }

    /** Ensures imported and existing resource plans dump stable labels instead of handles. */
    @Test
    fun `resource plan labels for existing and imported textures avoid handle ids`() {
        val provider = RefusingResourceProviderDouble()
        val context = targetPreparationContext()
        val handleLikeExternalId = "ExternalTextureHandle@0xDEADBEEF"
        val handleLikeResourceRef = "GPUTextureResourceRef@0xFEEDFACE"

        val existing = provider.materialize(
            plan = GPUTextureAllocationPlan.ExistingGPUResource(
                ref = GPUTextureResourceRef(handleLikeResourceRef),
                planLabel = "existing-shadow-cache",
            ),
            context = context,
        )
        val imported = provider.materialize(
            plan = GPUTextureAllocationPlan.ImportExternalTexture(
                descriptor = GPUImportedTextureDescriptor(
                    externalId = handleLikeExternalId,
                    descriptor = targetTextureDescriptor(),
                    releasePolicy = "caller-owned",
                ),
                planLabel = "imported-camera-frame",
            ),
            context = context,
        )

        val lines = (existing.dumpLines() + imported.dumpLines()).joinToString("\n")

        assertContains(lines, "resourcePlans=existing-shadow-cache")
        assertContains(lines, "resourcePlans=imported-camera-frame")
        assertFalse(lines.contains(handleLikeExternalId))
        assertFalse(lines.contains(handleLikeResourceRef))
    }

    /** Ensures deferred resource dumps keep late-stage refusal-or-wait evidence explicit. */
    @Test
    fun `deferred resource decision dump includes reason and diagnostics`() {
        val decision = GPUResourceMaterializationDecision.Deferred(
            reasonCode = "budget.resource.upload_pending",
            diagnostics = listOf(
                GPUResourceDiagnostic.uploadBudgetExceeded(
                    resourceLabel = "solid-upload",
                    requestedBytes = 4096,
                    budgetBytes = 1024,
                ),
            ),
            targetId = "root-target",
            taskIds = listOf("task-upload"),
            resourcePlanLabels = listOf("solid-upload"),
        )

        assertEquals(
            listOf(
                "resource.materialization:deferred target=root-target tasks=task-upload resourcePlans=solid-upload reason=budget.resource.upload_pending diagnostics=budget.resource.upload_exceeded",
                "resource.diagnostic code=budget.resource.upload_exceeded resource=solid-upload terminal=true facts=budgetBytes=1024;requestedBytes=4096",
            ),
            decision.dumpLines(),
        )
    }

    /** Ensures diagnostics with identical primary keys use a complete canonical sort key. */
    @Test
    fun `resource diagnostics sort by complete canonical key`() {
        val later = GPUResourceDiagnostic(
            code = "same.resource",
            resourceLabel = "shared",
            message = "zeta",
            terminal = true,
            facts = mapOf("rank" to "2"),
        )
        val earlier = GPUResourceDiagnostic(
            code = "same.resource",
            resourceLabel = "shared",
            message = "alpha",
            terminal = true,
            facts = mapOf("rank" to "1"),
        )
        val decision = GPUResourceMaterializationDecision.Deferred(
            reasonCode = "unit-test",
            diagnostics = listOf(later, earlier),
            targetId = "root-target",
        )

        assertEquals(
            listOf(
                "resource.materialization:deferred target=root-target tasks=none resourcePlans=none reason=unit-test diagnostics=same.resource,same.resource",
                "resource.diagnostic code=same.resource resource=shared terminal=true facts=rank=1",
                "resource.diagnostic code=same.resource resource=shared terminal=true facts=rank=2",
            ),
            decision.dumpLines(),
        )
    }

    /** Creates a target descriptor for resource tests. */
    private fun targetTextureDescriptor(
        usageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    ): GPUTextureDescriptor =
        GPUTextureDescriptor(
            width = 64,
            height = 64,
            format = "rgba8unorm",
            usageLabels = usageLabels,
        )

    /** Creates a target preparation context for resource tests. */
    private fun targetPreparationContext(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "root-target",
            frameId = "frame-1",
            deviceGeneration = 11,
            budgetClass = "unit-test",
        )

    /** Creates a materialized command operand reference for decision dump tests. */
    private fun commandOperandRef(
        label: String,
        kind: GPUMaterializedCommandOperandKind,
        usageLabels: List<String>,
        invalidationPolicy: String = "pass-end",
        evidenceFacts: Map<String, String> = emptyMap(),
    ): GPUMaterializedCommandOperandReference =
        GPUMaterializedCommandOperandReference(
            label = label,
            kind = kind,
            descriptorHash = "sha256:$label",
            deviceGeneration = 11,
            ownerScope = "render-pass:main-pass",
            usageLabels = usageLabels,
            invalidationPolicy = invalidationPolicy,
            evidenceFacts = evidenceFacts,
        )

    /** Creates provider-owned command operand materialization input for bridge tests. */
    private fun commandOperandMaterializationRequest(): GPUCommandOperandMaterializationRequest =
        GPUCommandOperandMaterializationRequest(
            targetId = "root-target",
            taskIds = listOf("task-fill-rect"),
            resourcePlanLabels = listOf("first-route-command-operands"),
            operands = listOf(
                commandOperandPlan(
                    packetId = null,
                    commandLabel = "beginRenderPass",
                    label = "target-view:root",
                    kind = GPUMaterializedCommandOperandKind.RenderTarget,
                    usageLabels = setOf("render_attachment"),
                    invalidationPolicy = "frame-end",
                    evidenceFacts = mapOf("loadStore" to "clear-store"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "setRenderPipeline",
                    label = "render-pipeline:solid-fill",
                    kind = GPUMaterializedCommandOperandKind.RenderPipeline,
                    usageLabels = setOf("render"),
                    invalidationPolicy = "device-generation",
                    evidenceFacts = mapOf("cache" to "warm"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "setBindGroup",
                    label = "bind-group:solid-fill:packet-1",
                    kind = GPUMaterializedCommandOperandKind.BindGroup,
                    usageLabels = setOf("uniform", "texture_binding"),
                    evidenceFacts = mapOf("layout" to "layout-solid-v1"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "draw",
                    label = "vertex-buffer:solid-quad",
                    kind = GPUMaterializedCommandOperandKind.VertexBuffer,
                    usageLabels = setOf("vertex"),
                    evidenceFacts = mapOf("topology" to "triangle-list"),
                ),
            ),
        )

    /** Creates one provider command operand plan with matching required and available usage. */
    private fun commandOperandPlan(
        packetId: String?,
        commandLabel: String,
        label: String,
        kind: GPUMaterializedCommandOperandKind,
        usageLabels: Set<String>,
        invalidationPolicy: String = "pass-end",
        evidenceFacts: Map<String, String> = emptyMap(),
        evictedReason: String? = null,
    ): GPUCommandOperandMaterializationPlan =
        GPUCommandOperandMaterializationPlan(
            packetId = packetId,
            commandLabel = commandLabel,
            label = label,
            kind = kind,
            descriptorHash = "sha256:$label",
            deviceGeneration = 11,
            ownerScope = "render-pass:main-pass",
            requiredUsageLabels = usageLabels,
            availableUsageLabels = usageLabels,
            invalidationPolicy = invalidationPolicy,
            evidenceFacts = evidenceFacts,
            evictedReason = evictedReason,
        )

    /** Creates a renderer-owned texture allocation plan for refusal tests. */
    private fun createTexturePlan(): GPUTextureAllocationPlan.CreateTexture =
        GPUTextureAllocationPlan.CreateTexture(
            descriptor = targetTextureDescriptor(),
            ownership = GPUTextureOwnershipPlan(
                ownerLabel = "root-target",
                lifetimeClass = "frame-local",
                releasePolicy = "frame-end",
                canAliasScratch = false,
            ),
        )

    /** Creates a current surface lease for opt-in materialization tests. */
    private fun currentSurfaceLease(
        targetId: String = "root-target",
        targetGeneration: Long = 7,
        frameGeneration: Long = 1,
        deviceGeneration: Long = 11,
        resourceRefValue: String = "surface-texture-ref",
        usageLabels: Set<String> = setOf("render_attachment", "copy_src"),
        expiredReason: String? = null,
        evictedReason: String? = null,
    ): GPUSurfaceTextureLease =
        GPUSurfaceTextureLease(
            targetId = targetId,
            targetGeneration = targetGeneration,
            frameGeneration = frameGeneration,
            deviceGeneration = deviceGeneration,
            resourceRef = GPUTextureResourceRef(resourceRefValue),
            useToken = GPUUseToken(42),
            usageLabels = usageLabels,
            expiredReason = expiredReason,
            evictedReason = evictedReason,
        )

    /** Creates the explicit opt-in lease materialization request under test. */
    private fun surfaceLeaseRequest(
        lease: GPUSurfaceTextureLease = currentSurfaceLease(),
        requiredUsageLabels: Set<String> = setOf("render_attachment"),
        expectedTargetGeneration: Long = 7,
        expectedFrameGeneration: Long = 1,
        sampled: Boolean = false,
        activeAttachmentRef: GPUTextureResourceRef? = null,
        taskIds: List<String> = listOf("task-fill-rect"),
    ): GPUSurfaceTextureLeaseMaterializationRequest =
        GPUSurfaceTextureLeaseMaterializationRequest(
            lease = lease,
            requiredUsageLabels = requiredUsageLabels,
            expectedTargetGeneration = expectedTargetGeneration,
            expectedFrameGeneration = expectedFrameGeneration,
            sampled = sampled,
            activeAttachmentRef = activeAttachmentRef,
            taskIds = taskIds,
        )

    /** Asserts that lease materialization refuses with stable non-handle dump evidence. */
    private fun assertSurfaceLeaseRefused(
        request: GPUSurfaceTextureLeaseMaterializationRequest,
        expectedCode: String,
        plan: GPUTextureAllocationPlan = GPUTextureAllocationPlan.LeaseSurfaceTexture(targetId = "root-target"),
        forbiddenDumpText: String = request.lease.resourceRef.value,
    ): GPUResourceMaterializationDecision.Refused {
        val decision = RevalidatingSurfaceLeaseResourceProvider(request).materialize(
            plan = plan,
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals(expectedCode, refused.diagnostic.code)
        assertFalse(refused.dumpLines().joinToString("\n").contains(forbiddenDumpText))
        return refused
    }

    /** Resource test double that relies on production refuse-by-default behavior. */
    private class RefusingResourceProviderDouble : GPUResourceProvider
}
