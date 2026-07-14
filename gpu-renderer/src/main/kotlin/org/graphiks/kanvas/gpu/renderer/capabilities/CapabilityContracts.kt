package org.graphiks.kanvas.gpu.renderer.capabilities

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage

/** Handle-free identity for one GPU device generation. */
@JvmInline
value class GPUDeviceGenerationID(val value: Long) {
    init {
        require(value >= 0L) { "GPUDeviceGenerationID.value must be non-negative" }
    }
}

/** Implementation identity for native or future pure Kotlin GPU facade backends. */
data class GPUImplementationIdentity(
    val facadeName: String,
    val implementationName: String,
    val adapterName: String,
    val deviceName: String,
    val vendorId: String? = null,
    val deviceId: String? = null,
)

/** Renderer-owned feature gates that do not correspond to GPU optional feature names. */
enum class GPURendererFeature(val dumpLabel: String) {
    RenderPass("render-pass"),
    CopyUpload("copy-upload"),
    Readback("readback"),
    UniformBuffer("uniform-buffer"),
    TextureSampling("texture-sampling"),
}

/** Exact implementation evidence for the optional copy-as-draw materialization primitive. */
data class GPUCopyAsDrawImplementationCapability(
    val implementationId: String,
    val implementationVersion: String,
    val available: Boolean,
) {
    init {
        require(implementationId.isNotBlank()) {
            "GPUCopyAsDrawImplementationCapability.implementationId must not be blank"
        }
        require(implementationVersion.isNotBlank()) {
            "GPUCopyAsDrawImplementationCapability.implementationVersion must not be blank"
        }
    }
}

/** Stable dump label for GPU texture formats used in diagnostics and snapshots. */
fun GPUTextureFormat.dumpLabel(): String =
    when (this) {
        GPUTextureFormat.R8Unorm -> "r8unorm"
        GPUTextureFormat.R8Snorm -> "r8snorm"
        GPUTextureFormat.R8Uint -> "r8uint"
        GPUTextureFormat.R8Sint -> "r8sint"
        GPUTextureFormat.R16Uint -> "r16uint"
        GPUTextureFormat.R16Sint -> "r16sint"
        GPUTextureFormat.R16Float -> "r16float"
        GPUTextureFormat.RG8Unorm -> "rg8unorm"
        GPUTextureFormat.RG8Snorm -> "rg8snorm"
        GPUTextureFormat.RG8Uint -> "rg8uint"
        GPUTextureFormat.RG8Sint -> "rg8sint"
        GPUTextureFormat.R32Float -> "r32float"
        GPUTextureFormat.R32Uint -> "r32uint"
        GPUTextureFormat.R32Sint -> "r32sint"
        GPUTextureFormat.RG16Uint -> "rg16uint"
        GPUTextureFormat.RG16Sint -> "rg16sint"
        GPUTextureFormat.RG16Float -> "rg16float"
        GPUTextureFormat.RGBA8Unorm -> "rgba8unorm"
        GPUTextureFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
        GPUTextureFormat.RGBA8Snorm -> "rgba8snorm"
        GPUTextureFormat.RGBA8Uint -> "rgba8uint"
        GPUTextureFormat.RGBA8Sint -> "rgba8sint"
        GPUTextureFormat.BGRA8Unorm -> "bgra8unorm"
        GPUTextureFormat.BGRA8UnormSrgb -> "bgra8unorm-srgb"
        GPUTextureFormat.RGB10A2Uint -> "rgb10a2uint"
        GPUTextureFormat.RGB10A2Unorm -> "rgb10a2unorm"
        GPUTextureFormat.RG11B10Ufloat -> "rg11b10ufloat"
        GPUTextureFormat.RGB9E5Ufloat -> "rgb9e5ufloat"
        GPUTextureFormat.RG32Float -> "rg32float"
        GPUTextureFormat.RG32Uint -> "rg32uint"
        GPUTextureFormat.RG32Sint -> "rg32sint"
        GPUTextureFormat.RGBA16Uint -> "rgba16uint"
        GPUTextureFormat.RGBA16Sint -> "rgba16sint"
        GPUTextureFormat.RGBA16Float -> "rgba16float"
        GPUTextureFormat.RGBA32Float -> "rgba32float"
        GPUTextureFormat.RGBA32Uint -> "rgba32uint"
        GPUTextureFormat.RGBA32Sint -> "rgba32sint"
        GPUTextureFormat.Stencil8 -> "stencil8"
        GPUTextureFormat.Depth16Unorm -> "depth16unorm"
        GPUTextureFormat.Depth24Plus -> "depth24plus"
        GPUTextureFormat.Depth24PlusStencil8 -> "depth24plus-stencil8"
        GPUTextureFormat.Depth32Float -> "depth32float"
        GPUTextureFormat.Depth32FloatStencil8 -> "depth32float-stencil8"
        GPUTextureFormat.BC1RGBAUnorm -> "bc1-rgba-unorm"
        GPUTextureFormat.BC1RGBAUnormSrgb -> "bc1-rgba-unorm-srgb"
        GPUTextureFormat.BC2RGBAUnorm -> "bc2-rgba-unorm"
        GPUTextureFormat.BC2RGBAUnormSrgb -> "bc2-rgba-unorm-srgb"
        GPUTextureFormat.BC3RGBAUnorm -> "bc3-rgba-unorm"
        GPUTextureFormat.BC3RGBAUnormSrgb -> "bc3-rgba-unorm-srgb"
        GPUTextureFormat.BC4RUnorm -> "bc4-r-unorm"
        GPUTextureFormat.BC4RSnorm -> "bc4-r-snorm"
        GPUTextureFormat.BC5RGUnorm -> "bc5-rg-unorm"
        GPUTextureFormat.BC5RGSnorm -> "bc5-rg-snorm"
        GPUTextureFormat.BC6HRGBUfloat -> "bc6h-rgb-ufloat"
        GPUTextureFormat.BC6HRGBFloat -> "bc6h-rgb-float"
        GPUTextureFormat.BC7RGBAUnorm -> "bc7-rgba-unorm"
        GPUTextureFormat.BC7RGBAUnormSrgb -> "bc7-rgba-unorm-srgb"
        GPUTextureFormat.ETC2RGB8Unorm -> "etc2-rgb8unorm"
        GPUTextureFormat.ETC2RGB8UnormSrgb -> "etc2-rgb8unorm-srgb"
        GPUTextureFormat.ETC2RGB8A1Unorm -> "etc2-rgb8a1unorm"
        GPUTextureFormat.ETC2RGB8A1UnormSrgb -> "etc2-rgb8a1unorm-srgb"
        GPUTextureFormat.ETC2RGBA8Unorm -> "etc2-rgba8unorm"
        GPUTextureFormat.ETC2RGBA8UnormSrgb -> "etc2-rgba8unorm-srgb"
        GPUTextureFormat.EACR11Unorm -> "eac-r11unorm"
        GPUTextureFormat.EACR11Snorm -> "eac-r11snorm"
        GPUTextureFormat.EACRG11Unorm -> "eac-rg11unorm"
        GPUTextureFormat.EACRG11Snorm -> "eac-rg11snorm"
        GPUTextureFormat.ASTC4x4Unorm -> "astc-4x4-unorm"
        GPUTextureFormat.ASTC4x4UnormSrgb -> "astc-4x4-unorm-srgb"
        GPUTextureFormat.ASTC5x4Unorm -> "astc-5x4-unorm"
        GPUTextureFormat.ASTC5x4UnormSrgb -> "astc-5x4-unorm-srgb"
        GPUTextureFormat.ASTC5x5Unorm -> "astc-5x5-unorm"
        GPUTextureFormat.ASTC5x5UnormSrgb -> "astc-5x5-unorm-srgb"
        GPUTextureFormat.ASTC6x5Unorm -> "astc-6x5-unorm"
        GPUTextureFormat.ASTC6x5UnormSrgb -> "astc-6x5-unorm-srgb"
        GPUTextureFormat.ASTC6x6Unorm -> "astc-6x6-unorm"
        GPUTextureFormat.ASTC6x6UnormSrgb -> "astc-6x6-unorm-srgb"
        GPUTextureFormat.ASTC8x5Unorm -> "astc-8x5-unorm"
        GPUTextureFormat.ASTC8x5UnormSrgb -> "astc-8x5-unorm-srgb"
        GPUTextureFormat.ASTC8x6Unorm -> "astc-8x6-unorm"
        GPUTextureFormat.ASTC8x6UnormSrgb -> "astc-8x6-unorm-srgb"
        GPUTextureFormat.ASTC8x8Unorm -> "astc-8x8-unorm"
        GPUTextureFormat.ASTC8x8UnormSrgb -> "astc-8x8-unorm-srgb"
        GPUTextureFormat.ASTC10x5Unorm -> "astc-10x5-unorm"
        GPUTextureFormat.ASTC10x5UnormSrgb -> "astc-10x5-unorm-srgb"
        GPUTextureFormat.ASTC10x6Unorm -> "astc-10x6-unorm"
        GPUTextureFormat.ASTC10x6UnormSrgb -> "astc-10x6-unorm-srgb"
        GPUTextureFormat.ASTC10x8Unorm -> "astc-10x8-unorm"
        GPUTextureFormat.ASTC10x8UnormSrgb -> "astc-10x8-unorm-srgb"
        GPUTextureFormat.ASTC10x10Unorm -> "astc-10x10-unorm"
        GPUTextureFormat.ASTC10x10UnormSrgb -> "astc-10x10-unorm-srgb"
        GPUTextureFormat.ASTC12x10Unorm -> "astc-12x10-unorm"
        GPUTextureFormat.ASTC12x10UnormSrgb -> "astc-12x10-unorm-srgb"
        GPUTextureFormat.ASTC12x12Unorm -> "astc-12x12-unorm"
        GPUTextureFormat.ASTC12x12UnormSrgb -> "astc-12x12-unorm-srgb"
    }

/** Returns stable public usage labels in deterministic order. */
fun GPUTextureUsage.dumpLabels(): List<String> =
    buildList {
        for ((usage, label) in textureUsageDumpEntries) {
            if (containsUsage(usage)) add(label)
        }
        val unknownBits = unknownUsageBits()
        if (unknownBits != 0uL) add(unknownTextureUsageLabel(unknownBits))
    }

private fun GPUTextureUsage.containsUsage(required: GPUTextureUsage): Boolean =
    (value and required.value) == required.value

private val textureUsageDumpEntries: List<Pair<GPUTextureUsage, String>> =
    listOf(
        GPUTextureUsage.CopySrc to "copy_src",
        GPUTextureUsage.CopyDst to "copy_dst",
        GPUTextureUsage.TextureBinding to "texture_binding",
        GPUTextureUsage.StorageBinding to "storage_binding",
        GPUTextureUsage.RenderAttachment to "render_attachment",
    )

private val knownTextureUsageMask: ULong =
    textureUsageDumpEntries.fold(0uL) { mask, (usage, _) -> mask or usage.value }

private fun GPUTextureUsage.unknownUsageBits(): ULong =
    value and knownTextureUsageMask.inv()

private fun unknownTextureUsageLabel(bits: ULong): String =
    "unknown:0x${bits.toString(16)}"

private fun GPUTextureUsage.missingUsageLabelsFrom(supported: GPUTextureUsage): List<String> =
    buildList {
        for ((usage, label) in textureUsageDumpEntries) {
            if (containsUsage(usage) && !supported.containsUsage(usage)) add(label)
        }
        val unknownMissingBits = unknownUsageBits() and supported.value.inv()
        if (unknownMissingBits != 0uL) add(unknownTextureUsageLabel(unknownMissingBits))
    }

/** Single behavior-affecting capability fact. */
data class GPUCapabilityFact(
    val name: String,
    val source: String,
    val value: String,
    val affectsValidity: Boolean,
    val evidenceLabel: String,
)

/** Shader or device feature required by a route. */
data class GPUFeatureRequirement(
    val featureName: String,
    val requiredValue: String,
    val reasonCode: String,
)

/** Adapter/device limit required by a route. */
data class GPULimitRequirement(
    val limitName: String,
    val requiredMinimum: Long,
    val observedValue: Long? = null,
    val unit: String,
    val affectsValidity: Boolean,
)

/** Adapter/device limits that affect backend route validity and resource planning. */
data class GPULimits(
    val maxTextureDimension2D: Long,
    val copyBytesPerRowAlignment: Long,
    val minUniformBufferOffsetAlignment: Long,
    val source: String = "device.limits",
    /** Facade-observed buffer allocation limit; absent until the selected backend reports it. */
    val maxBufferSize: Long? = null,
) {
    init {
        require(maxTextureDimension2D > 0L) { "GPULimits.maxTextureDimension2D must be positive" }
        require(copyBytesPerRowAlignment > 0L) { "GPULimits.copyBytesPerRowAlignment must be positive" }
        require(minUniformBufferOffsetAlignment > 0L) {
            "GPULimits.minUniformBufferOffsetAlignment must be positive"
        }
        require(maxBufferSize == null || maxBufferSize > 0L) {
            "GPULimits.maxBufferSize must be positive when observed"
        }
        require(source.isNotBlank()) { "GPULimits.source must not be blank" }
    }

    /** Converts these limits to deterministic capability facts for diagnostics and evidence dumps. */
    fun capabilityFacts(evidenceLabel: String): List<GPUCapabilityFact> {
        require(evidenceLabel.isNotBlank()) { "evidenceLabel must not be blank" }
        return listOf(
            GPUCapabilityFact(
                name = "maxTextureDimension2D",
                source = source,
                value = maxTextureDimension2D.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
            GPUCapabilityFact(
                name = "copyBytesPerRowAlignment",
                source = source,
                value = copyBytesPerRowAlignment.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
            GPUCapabilityFact(
                name = "minUniformBufferOffsetAlignment",
                source = source,
                value = minUniformBufferOffsetAlignment.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
        ) + listOfNotNull(
            maxBufferSize?.let { observedMaxBufferSize ->
                GPUCapabilityFact(
                    name = "maxBufferSize",
                    source = source,
                    value = observedMaxBufferSize.toString(),
                    affectsValidity = true,
                    evidenceLabel = evidenceLabel,
                )
            },
        )
    }

    companion object {
        /** Builds a limits snapshot from known conservative runtime assumptions. */
        fun conservative(
            maxTextureDimension2D: Long,
            copyBytesPerRowAlignment: Long,
            minUniformBufferOffsetAlignment: Long,
            maxBufferSize: Long? = null,
        ): GPULimits =
            GPULimits(
                maxTextureDimension2D = maxTextureDimension2D,
                copyBytesPerRowAlignment = copyBytesPerRowAlignment,
                minUniformBufferOffsetAlignment = minUniformBufferOffsetAlignment,
                maxBufferSize = maxBufferSize,
                source = "runtime.conservative",
            )
    }
}

/** Capability snapshot for the selected GPU facade implementation. */
data class GPUCapabilities(
    val implementation: GPUImplementationIdentity,
    val facts: List<GPUCapabilityFact>,
    val knownUnsupportedFacts: List<GPUCapabilityFact> = emptyList(),
    val snapshotId: String,
    val limits: GPULimits? = null,
    val supportedTextureFormats: Set<GPUTextureFormat> = emptySet(),
    val supportedTextureUsage: GPUTextureUsage? = null,
    val rendererFeatures: Set<GPURendererFeature> = emptySet(),
    /** Optional real implementation primitive captured by the device capability registry. */
    val copyAsDrawCapability: GPUCopyAsDrawImplementationCapability? = null,
) {
    init {
        require(snapshotId.isNotBlank()) { "GPUCapabilities.snapshotId must not be blank" }
    }
}

/** Validates a texture allocation request against known format, usage, and size capabilities. */
fun GPUCapabilities.validateTextureRequest(
    format: GPUTextureFormat,
    width: Int,
    height: Int,
    usage: GPUTextureUsage,
): GPUCapabilityDiagnostic? {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }

    if (supportedTextureFormats.isNotEmpty() && format !in supportedTextureFormats) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_format",
            severity = "error",
            requirementName = "texture.format",
            required = format.dumpLabel(),
            observed = supportedTextureFormats.map { it.dumpLabel() }.sorted().joinToString(","),
            isTerminal = true,
        )
    }

    val supportedUsage = supportedTextureUsage
    val missingUsageLabels = usage.missingUsageLabelsFrom(supportedUsage ?: usage)
    if (supportedUsage != null && missingUsageLabels.isNotEmpty()) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_usage",
            severity = "error",
            requirementName = "texture.usage",
            required = missingUsageLabels.joinToString(","),
            observed = supportedUsage.dumpLabels().joinToString(","),
            isTerminal = true,
        )
    }

    val maxTextureDimension2D = limits?.maxTextureDimension2D
    if (maxTextureDimension2D != null && (width.toLong() > maxTextureDimension2D || height.toLong() > maxTextureDimension2D)) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_size",
            severity = "error",
            requirementName = "texture.maxTextureDimension2D",
            required = maxOf(width, height).toString(),
            observed = maxTextureDimension2D.toString(),
            isTerminal = true,
        )
    }

    return null
}

/** Validates a dynamic uniform-buffer alignment request against known device limits. */
fun GPUCapabilities.validateUniformAlignment(alignmentBytes: Long): GPUCapabilityDiagnostic? {
    require(alignmentBytes > 0L) { "alignmentBytes must be positive" }

    val required = limits?.minUniformBufferOffsetAlignment ?: return null
    if (alignmentBytes >= required && alignmentBytes % required == 0L) {
        return null
    }

    return GPUCapabilityDiagnostic(
        code = "unsupported.capability.uniform_alignment",
        severity = "error",
        requirementName = "limits.minUniformBufferOffsetAlignment",
        required = required.toString(),
        observed = alignmentBytes.toString(),
        isTerminal = true,
    )
}

/** Validates that a renderer-owned feature gate is present when feature evidence is known. */
fun GPUCapabilities.validateRendererFeature(feature: GPURendererFeature): GPUCapabilityDiagnostic? {
    if (rendererFeatures.isEmpty() || feature in rendererFeatures) return null
    return GPUCapabilityDiagnostic(
        code = "unsupported.capability.feature",
        severity = "error",
        requirementName = "feature",
        required = feature.dumpLabel,
        observed = rendererFeatures.map { it.dumpLabel }.sorted().joinToString(","),
        isTerminal = true,
    )
}

/** Diagnostic emitted when capability facts block a route. */
data class GPUCapabilityDiagnostic(
    val code: String,
    val severity: String,
    val requirementName: String,
    val required: String,
    val observed: String? = null,
    val isTerminal: Boolean,
)
