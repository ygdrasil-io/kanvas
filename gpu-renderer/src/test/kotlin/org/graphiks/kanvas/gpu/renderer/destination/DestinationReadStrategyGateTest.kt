package org.graphiks.kanvas.gpu.renderer.destination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DestinationReadStrategyGateTest {
    @Test
    fun boundedTargetCopyProducesDestinationReadEvidenceDump() {
        val result = GPUDestinationReadStrategyPlanner().plan(targetCopyRequest())

        assertEquals("gpu-renderer.destination-read.strategy", result.evidenceRow)
        assertEquals("GPUNative", result.routeKind)
        assertEquals("TargetNative", result.classification)
        assertFalse(result.promoted)
        assertFalse(result.productActivation)
        assertFalse(result.materialized)
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(GPUDestinationReadStrategy.CopyTarget, result.plan.strategy)
        assertEquals(GPUDestinationReadAction.SplitPassAndCopyTarget, result.action)
        assertFalse(result.bindingInMaterialKey)

        assertEquals(
            listOf(
                "destination-read:strategy row=gpu-renderer.destination-read.strategy routeKind=GPUNative classification=TargetNative promoted=false productActivation=false materialized=false requirement=ShaderBlend strategy=TargetCopySnapshot action=SplitPassAndCopyTarget source=target:main generation=42",
                "destination-read:bounds command=blend:screen requested=shape-local unclipped=0,0,80,40 clipped=4,8,64,32 copy=4,8,64,32 finite=true pixelAligned=true conservative=true target=128x96",
                "destination-read:copy label=dst-copy:blend-screen descriptor=${result.copyDescriptorHash} source=target:main generation=42 size=64x32 format=rgba8unorm usage=copy_dst,texture_binding sampleCount=1 lifetime=pass-local owner=GPURecorderScope bytes=8192",
                "destination-read:binding label=dst-read:blend-screen layout=${result.bindingLayoutHash} textureView=${result.textureViewHash} sampler=${result.samplerHash} bounds=4,8,64,32 generation=42 slot=group1.binding3 materialKey=false",
                "destination-read:barrier split=true copyBeforeSample=true activeAttachmentSampled=false token=dst-token:blend:screen:42",
                "destination-read:resource sourceUsage=render_attachment,copy_src copyUsage=copy_dst,texture_binding budget=copy-small copyBytes=8192",
                "destination-read:nonclaim nativeDestinationRead=false adapterBacked=false framebufferFetch=false inputAttachment=false cpuReadbackFallback=false productActivation=false",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun validatedIntermediateProducesBindingWithoutCopyDump() {
        val result = GPUDestinationReadStrategyPlanner().plan(
            targetCopyRequest(
                requirement = GPUDestinationReadRequirement.ExistingIntermediate,
                strategy = GPUDestinationReadStrategy.BindIntermediate,
                action = GPUDestinationReadAction.UseExistingIntermediate,
                intermediateLabel = "intermediate:layer-card",
                intermediateValidated = true,
            ),
        )

        assertEquals(GPUDestinationReadStrategy.BindIntermediate, result.plan.strategy)
        assertEquals(GPUDestinationReadAction.UseExistingIntermediate, result.action)
        assertEquals(
            listOf(
                "destination-read:strategy row=gpu-renderer.destination-read.strategy routeKind=GPUNative classification=TargetNative promoted=false productActivation=false materialized=false requirement=ExistingIntermediate strategy=SampleExistingIntermediate action=UseExistingIntermediate source=intermediate:layer-card generation=42",
                "destination-read:bounds command=blend:screen requested=shape-local unclipped=0,0,80,40 clipped=4,8,64,32 copy=4,8,64,32 finite=true pixelAligned=true conservative=true target=128x96",
                "destination-read:intermediate label=intermediate:layer-card descriptor=${result.copyDescriptorHash} separateAttachment=true generation=42 bounds=4,8,64,32 lifetime=layer-local",
                "destination-read:binding label=dst-read:blend-screen layout=${result.bindingLayoutHash} textureView=${result.textureViewHash} sampler=${result.samplerHash} bounds=4,8,64,32 generation=42 slot=group1.binding3 materialKey=false",
                "destination-read:barrier split=false copyBeforeSample=false activeAttachmentSampled=false token=dst-token:blend:screen:42",
                "destination-read:resource sourceUsage=texture_binding copyUsage=texture_binding budget=intermediate-small copyBytes=8192",
                "destination-read:nonclaim nativeDestinationRead=false adapterBacked=false framebufferFetch=false inputAttachment=false cpuReadbackFallback=false productActivation=false",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun materialKeyExcludesDestinationReadHashesAndGeneration() {
        val base = GPUDestinationReadStrategyPlanner().plan(targetCopyRequest())
        val changedGeneration = GPUDestinationReadStrategyPlanner().plan(targetCopyRequest(targetGeneration = 99))

        assertEquals(base.materialKeyBoundaryHash, changedGeneration.materialKeyBoundaryHash)
    }

    @Test
    fun unsupportedDestinationReadVariantsRefuseWithStableDiagnostics() {
        val cases = listOf(
            refusalCase(
                "unbounded",
                bounds = destinationBounds(finite = false),
                reason = "unsupported.destination_read.bounds_unbounded",
            ),
            refusalCase(
                "active-attachment",
                activeAttachmentSampled = true,
                reason = "unsupported.destination_read.active_attachment_sampled",
            ),
            refusalCase(
                "copy-usage",
                sourceUsageLabels = setOf("render_attachment"),
                reason = "unsupported.destination_read.copy_usage_missing",
            ),
            refusalCase(
                "texture-binding",
                copyUsageLabels = setOf("copy_dst"),
                reason = "unsupported.destination_read.texture_binding_missing",
            ),
            refusalCase(
                "intermediate-unvalidated",
                requirement = GPUDestinationReadRequirement.ExistingIntermediate,
                strategy = GPUDestinationReadStrategy.BindIntermediate,
                action = GPUDestinationReadAction.UseExistingIntermediate,
                intermediateValidated = false,
                reason = "unsupported.destination_read.intermediate_unvalidated",
            ),
            refusalCase(
                "stale-generation",
                observedTargetGeneration = 41,
                reason = "unsupported.destination_read.target_generation_stale",
            ),
            refusalCase(
                "pass-split",
                passSplitAllowed = false,
                reason = "unsupported.destination_read.pass_split_illegal",
            ),
            refusalCase(
                "framebuffer-fetch",
                framebufferFetchRequested = true,
                reason = "unsupported.destination_read.framebuffer_fetch_unavailable",
            ),
            refusalCase(
                "cpu-readback",
                cpuReadbackRequested = true,
                reason = "unsupported.destination_read.cpu_readback_forbidden",
            ),
            refusalCase(
                "budget",
                maxCopyBytes = 1024,
                reason = "unsupported.destination_read.copy_budget_exceeded",
            ),
        )

        cases.forEach { case ->
            val result = GPUDestinationReadStrategyPlanner().plan(case.request)

            assertEquals(GPUDestinationReadStrategy.Refuse, result.plan.strategy)
            assertEquals(case.reason, result.diagnostics.single().code)
            assertEquals(
                listOf(
                    "destination-read:strategy.refused row=gpu-renderer.destination-read.strategy routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=false materialized=false command=blend:screen reason=${case.reason} label=${case.label}",
                    "destination-read:nonclaim nativeDestinationRead=false adapterBacked=false framebufferFetch=false inputAttachment=false cpuReadbackFallback=false productActivation=false",
                ),
                result.dumpLines(),
            )
        }
    }
}

private data class RefusalCase(
    val label: String,
    val request: GPUDestinationReadStrategyRequest,
    val reason: String,
)

private fun refusalCase(
    label: String,
    reason: String,
    requirement: GPUDestinationReadRequirement = GPUDestinationReadRequirement.TargetCopy,
    strategy: GPUDestinationReadStrategy = GPUDestinationReadStrategy.CopyTarget,
    action: GPUDestinationReadAction = GPUDestinationReadAction.SplitPassAndCopyTarget,
    bounds: GPUDestinationReadBounds = destinationBounds(),
    sourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    copyUsageLabels: Set<String> = setOf("copy_dst", "texture_binding"),
    activeAttachmentSampled: Boolean = false,
    intermediateValidated: Boolean = true,
    observedTargetGeneration: Long = 42,
    passSplitAllowed: Boolean = true,
    framebufferFetchRequested: Boolean = false,
    cpuReadbackRequested: Boolean = false,
    maxCopyBytes: Long = 16 * 1024 * 1024,
): RefusalCase = RefusalCase(
    label = label,
    reason = reason,
    request = targetCopyRequest(
        label = label,
        requirement = requirement,
        strategy = strategy,
        action = action,
        bounds = bounds,
        sourceUsageLabels = sourceUsageLabels,
        copyUsageLabels = copyUsageLabels,
        activeAttachmentSampled = activeAttachmentSampled,
        intermediateValidated = intermediateValidated,
        observedTargetGeneration = observedTargetGeneration,
        passSplitAllowed = passSplitAllowed,
        framebufferFetchRequested = framebufferFetchRequested,
        cpuReadbackRequested = cpuReadbackRequested,
        maxCopyBytes = maxCopyBytes,
    ),
)

private fun targetCopyRequest(
    label: String = "accepted",
    requirement: GPUDestinationReadRequirement = GPUDestinationReadRequirement.TargetCopy,
    strategy: GPUDestinationReadStrategy = GPUDestinationReadStrategy.CopyTarget,
    action: GPUDestinationReadAction = GPUDestinationReadAction.SplitPassAndCopyTarget,
    bounds: GPUDestinationReadBounds = destinationBounds(),
    sourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    copyUsageLabels: Set<String> = setOf("copy_dst", "texture_binding"),
    activeAttachmentSampled: Boolean = false,
    intermediateLabel: String = "target:main",
    intermediateValidated: Boolean = true,
    targetGeneration: Long = 42,
    observedTargetGeneration: Long = targetGeneration,
    passSplitAllowed: Boolean = true,
    framebufferFetchRequested: Boolean = false,
    cpuReadbackRequested: Boolean = false,
    maxCopyBytes: Long = 16 * 1024 * 1024,
): GPUDestinationReadStrategyRequest = GPUDestinationReadStrategyRequest(
    label = label,
    commandId = "blend:screen",
    requirement = requirement,
    strategy = strategy,
    action = action,
    bounds = bounds,
    sourceTargetLabel = "target:main",
    sourceUsageLabels = sourceUsageLabels,
    copyUsageLabels = copyUsageLabels,
    targetFormatClass = "rgba8unorm",
    targetGeneration = targetGeneration,
    observedTargetGeneration = observedTargetGeneration,
    activeAttachmentSampled = activeAttachmentSampled,
    intermediateLabel = intermediateLabel,
    intermediateValidated = intermediateValidated,
    passSplitAllowed = passSplitAllowed,
    framebufferFetchRequested = framebufferFetchRequested,
    cpuReadbackRequested = cpuReadbackRequested,
    maxCopyBytes = maxCopyBytes,
)

private fun destinationBounds(finite: Boolean = true): GPUDestinationReadBounds = GPUDestinationReadBounds(
    boundsLabel = "shape-local",
    conservative = true,
    pixelAligned = true,
    finite = finite,
    requestedBoundsLabel = "shape-local",
    unclippedBoundsLabel = "0,0,80,40",
    clippedBoundsLabel = "4,8,64,32",
    copyBoundsLabel = "4,8,64,32",
    originX = 4,
    originY = 8,
    width = 64,
    height = 32,
    targetWidth = 128,
    targetHeight = 96,
)
