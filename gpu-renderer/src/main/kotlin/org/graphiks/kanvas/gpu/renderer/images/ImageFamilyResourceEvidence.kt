package org.graphiks.kanvas.gpu.renderer.images

import java.io.File
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUSampledTextureBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureAllocationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureOwnershipPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUseToken

data class ImageFamilyResourceEvidence(
    val rowId: String,
    val dumpLines: List<String>,
    val nonClaims: List<String>,
)

fun buildRepeatedImageTextureSamplerEvidence(
    provider: GPUConcreteResourceProvider = GPUConcreteResourceProvider(),
): ImageFamilyResourceEvidence {
    val context = GPUTargetPreparationContext(
        targetId = "phase6-image-target",
        frameId = "phase6-image-frame",
        deviceGeneration = 17,
        budgetClass = "phase6-image-simple",
    )
    val request = phase6TextureSamplerRequest()
    provider.materializeTextureSamplerBinding(request, context)
    provider.materializeTextureSamplerBinding(request, context)
    return ImageFamilyResourceEvidence(
        rowId = "phase6-image-repeated-texture-sampler",
        dumpLines = provider.telemetry.dumpLines(),
        nonClaims = listOf(
            "no-broad-image-support",
            "no-codec-support",
            "no-animation-support",
            "no-mipmap-support",
            "no-yuv-support",
            "no-image-filter-support",
            "no-perspective-support",
            "no-picture-shader-support",
            "no-broad-color-management-support",
        ),
    )
}

private fun phase6TextureSamplerRequest(): GPUTextureSamplerMaterializationRequest {
    val textureDescriptor = GPUTextureDescriptor(
        width = 64,
        height = 64,
        format = "rgba8unorm",
        usageLabels = setOf("copy_dst", "texture_binding"),
        sampleCount = 1,
    )
    val viewDescriptor = GPUTextureViewDescriptor(
        textureDescriptorHash = "texture:phase6-checker",
        viewDimension = "2d",
        mipRange = 0..0,
        arrayLayerRange = 0..0,
    )
    val samplerDescriptor = GPUSamplerDescriptor(
        addressModeU = "clamp-to-edge",
        addressModeV = "clamp-to-edge",
        magFilter = "nearest",
        minFilter = "nearest",
        mipmapFilter = "none",
    )
    val ownership = GPUTextureOwnershipPlan(
        ownerLabel = "phase6-image-cache",
        lifetimeClass = "recording-local",
        releasePolicy = "submission-complete",
        canAliasScratch = false,
    )
    val binding = GPUSampledTextureBinding(
        bindingLabel = "sampled-texture.phase6-checker",
        view = viewDescriptor,
        sampler = samplerDescriptor,
        useToken = GPUUseToken(17L),
    )
    return GPUTextureSamplerMaterializationRequest(
        targetId = "phase6-image-target",
        packetId = "packet-phase6-image-1",
        taskIds = listOf("task-phase6-image-texture-sampler"),
        resourcePlanLabels = listOf("texture-sampler:phase6-checker"),
        allocation = GPUTextureAllocationPlan.CreateTexture(
            descriptor = textureDescriptor,
            ownership = ownership,
        ),
        ownership = ownership,
        textureDescriptor = textureDescriptor,
        viewDescriptor = viewDescriptor,
        samplerDescriptor = samplerDescriptor,
        binding = binding,
        bindingLayoutHash = "layout-image-sampler-v1",
        deviceGeneration = 17,
        expectedResourceGeneration = 3,
        actualResourceGeneration = 3,
        requiredTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        availableTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        requiredMipLevels = 1,
        uploadBytes = 16384,
        uploadBudgetBytes = 65536,
        uploadCapabilityAvailable = true,
    )
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: ImageFamilyResourceEvidenceKt <output-json>" }
    val evidence = buildRepeatedImageTextureSamplerEvidence()
    val output = File(args[0])
    output.parentFile.mkdirs()
    output.writeText(evidence.toJson() + "\n")
}

private fun ImageFamilyResourceEvidence.toJson(): String =
    buildString {
        appendLine("{")
        appendLine("  \"rowId\": \"${rowId.escapeJson()}\",")
        appendLine("  \"dumpLines\": [")
        dumpLines.forEachIndexed { index, line ->
            val comma = if (index == dumpLines.lastIndex) "" else ","
            appendLine("    \"${line.escapeJson()}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"nonClaims\": [")
        nonClaims.forEachIndexed { index, line ->
            val comma = if (index == nonClaims.lastIndex) "" else ","
            appendLine("    \"${line.escapeJson()}\"$comma")
        }
        appendLine("  ]")
        append("}")
    }

private fun String.escapeJson(): String =
    buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
