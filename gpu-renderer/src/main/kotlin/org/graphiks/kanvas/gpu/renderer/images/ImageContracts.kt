package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.materials.GPUImageShaderPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSamplingPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialTileMode
import org.graphiks.kanvas.gpu.renderer.materials.MaterialKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationPreimagePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSampledTextureBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUseToken
import java.security.MessageDigest

/** Image upload artifact key. */
@JvmInline
value class GPUImageUploadArtifactKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUImageUploadArtifactKey.value must not be blank" }
    }
}

/** Image source descriptor. */
data class GPUImageSourceDescriptor(
    val sourceId: String,
    val sourceKind: String,
    val sizeLabel: String,
    val colorProfileLabel: String? = null,
    val provenance: String,
)

/** Already decoded CPU pixel source accepted by the first M4 image slice. */
data class GPUDecodedImagePixelsDescriptor(
    val sourceId: String,
    val width: Int,
    val height: Int,
    val pixelFormat: String,
    val rowBytes: Long,
    val alphaType: String,
    val colorProfileLabel: String,
    val orientationState: String,
    val generation: Long,
    val contentHash: String,
    val provenance: String,
)

/** Bounded image sampling request for already decoded pixels. */
data class GPUDecodedImageSamplingPlan(
    val tileModeX: String,
    val tileModeY: String,
    val filterMode: String,
    val mipmapMode: String,
    val anisotropy: Int = 1,
    val coordinateTransformClass: String = "affine",
    val lodMinClamp: String = "0",
    val lodMaxClamp: String = "0",
)

/** Encoded image source descriptor. */
data class GPUEncodedImageSource(
    val sourceId: String,
    val byteHash: String,
    val containerFormat: String,
    val frameCount: Int,
)

/** Image codec descriptor. */
data class GPUImageCodecDescriptor(
    val codecName: String,
    val codecVersion: String = "descriptor:unspecified",
    val supportedFormats: Set<String>,
    val colorManagementPolicy: String,
    val implementationKind: String = "unspecified",
    val deterministic: Boolean = false,
    val dependencyGate: String? = null,
)

/** Codec provenance request for encoded image planning. */
data class GPUImageCodecProvenanceRequest(
    val source: GPUEncodedImageSource,
    val requestedFormat: String,
    val conformanceTier: String,
)

/** Codec provenance planning result. */
data class GPUImageCodecProvenancePlan(
    val registry: GPUImageCodecRegistrySnapshot,
    val request: GPUImageCodecProvenanceRequest,
    val codec: GPUImageCodecDescriptor?,
    val classification: String,
    val diagnostic: GPUImageDiagnostic,
)

/** Dumpable codec registry snapshot used for dependency-gated evidence. */
data class GPUImageCodecRegistrySnapshot(
    val registryId: String,
    val generation: Long,
    val codecs: List<GPUImageCodecDescriptor>,
) {
    /** Plans codec provenance without decoding or promoting uploaded texture support. */
    fun planDecodeProvenance(request: GPUImageCodecProvenanceRequest): GPUImageCodecProvenancePlan {
        val format = request.requestedFormat.lowercase()
        val codec = codecs
            .sortedBy { descriptor -> descriptor.codecName }
            .firstOrNull { descriptor -> format in descriptor.supportedFormats.map { it.lowercase() } }
        val diagnostic = when {
            codec == null -> GPUImageDiagnostic(
                code = "dependency.image.codec.unregistered",
                sourceId = request.source.sourceId,
                message = "No dumpable codec descriptor is registered for $format.",
                terminal = true,
            )
            codec.implementationKind.startsWith("external") -> GPUImageDiagnostic(
                code = "dependency.image.codec.external_not_allowed",
                sourceId = request.source.sourceId,
                message = "Codec ${codec.codecName} is external/platform-backed and cannot satisfy this conformance tier.",
                terminal = true,
            )
            codec.codecVersion.isBlank() || !codec.deterministic -> GPUImageDiagnostic(
                code = "dependency.image.codec.version_nondeterministic",
                sourceId = request.source.sourceId,
                message = "Codec ${codec.codecName} lacks deterministic version or output policy.",
                terminal = true,
            )
            codec.dependencyGate != null -> GPUImageDiagnostic(
                code = codec.dependencyGate,
                sourceId = request.source.sourceId,
                message = "Codec ${codec.codecName} remains dependency-gated for $format.",
                terminal = true,
            )
            else -> GPUImageDiagnostic(
                code = "dependency.image.codec.decode_not_promoted",
                sourceId = request.source.sourceId,
                message = "Codec ${codec.codecName} has provenance only; decode output is not promoted.",
                terminal = true,
            )
        }

        return GPUImageCodecProvenancePlan(
            registry = this,
            request = request,
            codec = codec,
            classification = "DependencyGated",
            diagnostic = diagnostic,
        )
    }

    companion object {
        /** Refuses decoded output that lacks registry-backed codec provenance. */
        fun refuseDecodeOutputWithoutProvenance(
            sourceId: String,
            outputLabel: String,
        ): GPUImageDiagnostic =
            GPUImageDiagnostic(
                code = "dependency.image.decode.provenance_missing",
                sourceId = sourceId,
                message = "Decode output $outputLabel is refused because codec provenance is missing.",
                terminal = true,
            )
    }
}

/** Image codec registry contract. */
interface GPUImageCodecRegistry {
    /** Finds a codec descriptor for an encoded source. */
    fun findCodec(source: GPUEncodedImageSource): GPUImageCodecDescriptor? = TODO("Wire GPUImageCodecRegistry to real codec delivery")
}

/** Image decode request. */
data class GPUImageDecodeRequest(
    val requestId: String,
    val source: GPUEncodedImageSource,
    val frameSelection: GPUImageFrameSelection,
    val targetColorLabel: String,
)

/** Image decode plan. */
data class GPUImageDecodePlan(
    val request: GPUImageDecodeRequest,
    val codec: GPUImageCodecDescriptor,
    val outputPixelPlan: GPUImagePixelPlan,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Image decode result descriptor. */
sealed interface GPUImageDecodeResult {
    /** Decode produced a typed pixel artifact. */
    data class Decoded(val pixelPlan: GPUImagePixelPlan, val artifactKey: GPUImageUploadArtifactKey) : GPUImageDecodeResult

    /** Decode is dependency-gated. */
    data class DependencyGated(val diagnostic: GPUImageDiagnostic) : GPUImageDecodeResult

    /** Decode was refused. */
    data class Refused(val diagnostic: GPUImageDiagnostic) : GPUImageDecodeResult
}

/** Animated image plan. */
data class GPUAnimatedImagePlan(
    val sourceId: String,
    val frameCount: Int,
    val timingPolicy: String,
    val selectedFrame: GPUImageFrameSelection,
)

/** Image frame info. */
data class GPUImageFrameInfo(
    val frameIndex: Int,
    val durationMillis: Long,
    val boundsLabel: String,
    val disposalMode: String,
)

/** Image frame selection. */
data class GPUImageFrameSelection(
    val frameIndex: Int,
    val timeMillis: Long? = null,
)

/** Image color decode plan. */
data class GPUImageColorDecodePlan(
    val sourceProfileLabel: String,
    val targetProfileLabel: String,
    val conversionPolicy: String,
)

/** Image orientation plan. */
data class GPUImageOrientationPlan(
    val orientation: String,
    val transformHash: String,
    val swapsDimensions: Boolean,
)

/** Image pixel layout plan. */
data class GPUImagePixelPlan(
    val width: Int,
    val height: Int,
    val format: String,
    val rowBytes: Long,
    val alphaType: String,
)

/** Image mipmap plan. */
data class GPUImageMipmapPlan(
    val generateMipmaps: Boolean,
    val levelCount: Int,
    val filterPolicy: String,
)

/** Image upload plan. */
data class GPUImageUploadPlan(
    val artifactKey: GPUImageUploadArtifactKey,
    val pixelPlan: GPUImagePixelPlan,
    val mipmapPlan: GPUImageMipmapPlan,
    val uploadBudgetClass: String,
)

/** Image pipeline plan. */
data class GPUImagePipelinePlan(
    val source: GPUImageSourceDescriptor,
    val decodePlan: GPUImageDecodePlan? = null,
    val orientationPlan: GPUImageOrientationPlan? = null,
    val colorDecodePlan: GPUImageColorDecodePlan? = null,
    val uploadPlan: GPUImageUploadPlan? = null,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Uploaded texture artifact descriptor. */
data class UploadedTextureArtifact(
    val artifactKey: GPUImageUploadArtifactKey,
    val pixelPlan: GPUImagePixelPlan,
    val uploadPlan: GPUImageUploadPlan,
    val generation: Long,
    val lifetimeClass: String,
    val artifactType: String = "UploadedTextureArtifact",
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Image diagnostic. */
data class GPUImageDiagnostic(
    val code: String,
    val sourceId: String? = null,
    val message: String,
    val terminal: Boolean,
)

/** Emits stable M4 codec provenance evidence lines. */
fun GPUImageCodecProvenancePlan.dumpLines(): List<String> =
    buildList {
        add(
            "codec:registry id=${registry.registryId} generation=${registry.generation} " +
                "descriptors=${registry.codecs.size}",
        )
        registry.codecs.sortedBy { descriptor -> descriptor.codecName }.forEach { descriptor ->
            add(descriptor.dumpLine())
        }
        add(
            "codec:provenance source=${request.source.sourceId} format=${request.requestedFormat.lowercase()} " +
                "tier=${request.conformanceTier} classification=$classification reason=${diagnostic.code}",
        )
        add(codecNonClaimLine)
    }

/** Contract plan for the first decoded-pixel image shader route. */
data class GPUDecodedImageShaderRoutePlan(
    val source: GPUDecodedImagePixelsDescriptor,
    val imageSource: GPUImageSourceDescriptor,
    val pipelinePlan: GPUImagePipelinePlan?,
    val materialSource: GPUMaterialSourceDescriptor,
    val materialKey: MaterialKey,
    val textureDescriptor: GPUTextureDescriptor,
    val viewDescriptor: GPUTextureViewDescriptor,
    val samplerDescriptor: GPUSamplerDescriptor,
    val binding: GPUSampledTextureBinding,
    val artifact: UploadedTextureArtifact,
    val routeKind: String,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Contract evidence for sampler/tile/mipmap boundaries on image routes. */
data class GPUImageSamplerBoundaryPlan(
    val source: GPUDecodedImagePixelsDescriptor,
    val sampling: GPUDecodedImageSamplingPlan,
    val textureDescriptor: GPUTextureDescriptor,
    val viewDescriptor: GPUTextureViewDescriptor,
    val samplerDescriptor: GPUSamplerDescriptor,
    val samplerDescriptorHash: String,
    val samplerBehaviorKey: String,
    val pipelineKey: String,
    val routeKind: String,
    val classification: String = "TargetNative",
    val promoted: Boolean = false,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/**
 * Builds deterministic sampler/tile/mipmap boundary evidence for image routes.
 *
 * The boundary is intentionally non-promoting. It records the sampler facts and
 * key boundaries needed before a future adapter-backed `GPUNative` sampler
 * claim, and refuses unsupported modes without generating broad tile, mipmap,
 * anisotropic, cubic, or perspective sampling support.
 */
class GPUImageSamplerBoundaryPlanner(
    private val maxUploadBytes: Long = 1_048_576L,
) {
    /** Plans one image sampler boundary without materializing a backend sampler. */
    fun plan(
        source: GPUDecodedImagePixelsDescriptor,
        sampling: GPUDecodedImageSamplingPlan,
    ): GPUImageSamplerBoundaryPlan {
        val textureDescriptor = source.toTextureDescriptor()
        val viewDescriptor = GPUTextureViewDescriptor(
            textureDescriptorHash = textureDescriptor.stableDescriptorHash(),
            viewDimension = "2d",
            mipRange = 0..0,
            arrayLayerRange = 0..0,
        )
        val samplerDescriptor = sampling.toSamplerDescriptor()
        val samplerDescriptorHash = samplerDescriptor.stableSamplerHash()
        val samplerBehaviorKey = sampling.samplerBehaviorKey()
        val pipelineKey = sampling.samplerPipelineKey(textureDescriptor, viewDescriptor)
        val refusalCode = source.refusalCode(maxUploadBytes) ?: sampling.samplerBoundaryRefusalCode(
            availableMipLevels = viewDescriptor.mipRange.count(),
        )

        if (refusalCode != null) {
            val diagnostic = GPUImageDiagnostic(
                code = refusalCode,
                sourceId = source.sourceId.ifBlank { null },
                message = "Image sampler boundary refused: $refusalCode",
                terminal = true,
            )

            return GPUImageSamplerBoundaryPlan(
                source = source,
                sampling = sampling,
                textureDescriptor = textureDescriptor,
                viewDescriptor = viewDescriptor,
                samplerDescriptor = samplerDescriptor,
                samplerDescriptorHash = samplerDescriptorHash,
                samplerBehaviorKey = samplerBehaviorKey,
                pipelineKey = pipelineKey,
                routeKind = "RefuseDiagnostic",
                diagnostics = listOf(diagnostic),
            )
        }

        val diagnostic = GPUImageDiagnostic(
            code = "sampler-boundary.accepted",
            sourceId = source.sourceId,
            message = "Image sampler boundary facts are dumpable; native sampler execution remains unpromoted.",
            terminal = false,
        )

        return GPUImageSamplerBoundaryPlan(
            source = source,
            sampling = sampling,
            textureDescriptor = textureDescriptor,
            viewDescriptor = viewDescriptor,
            samplerDescriptor = samplerDescriptor,
            samplerDescriptorHash = samplerDescriptorHash,
            samplerBehaviorKey = samplerBehaviorKey,
            pipelineKey = pipelineKey,
            routeKind = "GPUNative",
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Builds bounded decoded-pixel image shader evidence without product activation. */
class GPUDecodedImageShaderPreparedPlanner(
    private val maxUploadBytes: Long = 1_048_576L,
) {
    /**
     * Plans one already decoded CPU pixel source as an uploaded texture artifact.
     *
     * This is contract evidence only. It names an upload-before-sample artifact,
     * sampled binding, sampler facts, and material-key boundary; it does not
     * decode codecs, create WebGPU resources, submit adapter work, generate
     * mipmaps, or activate image drawing in product routing.
     */
    fun plan(
        source: GPUDecodedImagePixelsDescriptor,
        sampling: GPUDecodedImageSamplingPlan,
    ): GPUDecodedImageShaderRoutePlan {
        val imageSource = source.toImageSourceDescriptor()
        val textureDescriptor = source.toTextureDescriptor()
        val viewDescriptor = GPUTextureViewDescriptor(
            textureDescriptorHash = textureDescriptor.stableDescriptorHash(),
            viewDimension = "2d",
            mipRange = 0..0,
            arrayLayerRange = 0..0,
        )
        val samplerDescriptor = sampling.toSamplerDescriptor()
        val materialKey = sampling.materialKey()
        val materialSource = sampling.toMaterialSource()
        val binding = GPUSampledTextureBinding(
            bindingLabel = bindingLabel,
            view = viewDescriptor,
            sampler = samplerDescriptor,
            useToken = GPUUseToken(1),
        )

        val refusalCode = source.refusalCode(maxUploadBytes) ?: sampling.refusalCode()
        if (refusalCode != null) {
            val diagnostic = GPUImageDiagnostic(
                code = refusalCode,
                sourceId = source.sourceId.ifBlank { null },
                message = "Decoded image shader route refused: $refusalCode",
                terminal = true,
            )
            return GPUDecodedImageShaderRoutePlan(
                source = source,
                imageSource = imageSource,
                pipelinePlan = null,
                materialSource = materialSource,
                materialKey = MaterialKey("refused:$refusalCode"),
                textureDescriptor = textureDescriptor,
                viewDescriptor = viewDescriptor,
                samplerDescriptor = samplerDescriptor,
                binding = binding,
                artifact = source.placeholderArtifact(textureDescriptor),
                routeKind = "RefuseDiagnostic",
                diagnostics = listOf(diagnostic),
            )
        }

        val pixelPlan = source.toPixelPlan()
        val mipmapPlan = GPUImageMipmapPlan(
            generateMipmaps = false,
            levelCount = 1,
            filterPolicy = "none",
        )
        val uploadPlan = GPUImageUploadPlan(
            artifactKey = source.artifactKey(),
            pixelPlan = pixelPlan,
            mipmapPlan = mipmapPlan,
            uploadBudgetClass = decodedImageUploadBudgetClass,
        )
        val diagnostic = GPUImageDiagnostic(
            code = "image:decoded.prepared",
            sourceId = source.sourceId,
            message = "Decoded pixels are available as an UploadedTextureArtifact for $bindingLabel",
            terminal = false,
        )
        val artifact = UploadedTextureArtifact(
            artifactKey = uploadPlan.artifactKey,
            pixelPlan = pixelPlan,
            uploadPlan = uploadPlan,
            generation = source.generation,
            lifetimeClass = "recording-local",
            diagnostics = listOf(diagnostic),
        )
        val pipelinePlan = GPUImagePipelinePlan(
            source = imageSource,
            uploadPlan = uploadPlan,
            diagnostics = listOf(diagnostic),
        )

        return GPUDecodedImageShaderRoutePlan(
            source = source,
            imageSource = imageSource,
            pipelinePlan = pipelinePlan,
            materialSource = materialSource,
            materialKey = materialKey,
            textureDescriptor = textureDescriptor,
            viewDescriptor = viewDescriptor,
            samplerDescriptor = samplerDescriptor,
            binding = binding,
            artifact = artifact,
            routeKind = "CPUPreparedGPU",
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Emits stable M4 decoded-image route evidence lines for reports and tests. */
fun GPUDecodedImageShaderRoutePlan.dumpLines(): List<String> {
    if (routeKind == "RefuseDiagnostic") {
        val diagnostic = diagnostics.firstOrNull()
        return listOf(
            "image:decoded.refused reason=${diagnostic?.code ?: "unknown"} " +
                "source=${source.sourceId} routeKind=RefuseDiagnostic",
            imageNonClaimLine,
        )
    }

    return listOf(
        "image:decoded.prepared routeKind=CPUPreparedGPU consumer=${binding.bindingLabel} " +
            "material=${materialKey.value.substringBeforeLast(':')}",
        "image:source id=${source.sourceId} kind=${imageSource.sourceKind} " +
            "size=${source.width}x${source.height} format=${source.pixelFormat} " +
            "alpha=${source.alphaType} color=${source.colorProfileLabel} " +
            "orientation=${source.orientationState} generation=${source.generation} " +
            "provenance=${source.provenance}",
        "image:upload artifact=${artifact.artifactKey.value} type=${artifact.artifactType} " +
            "lifetime=${artifact.lifetimeClass} budget=${artifact.uploadPlan.uploadBudgetClass} " +
            "uploadBeforeSample=true",
        "texture:descriptor size=${textureDescriptor.width}x${textureDescriptor.height} " +
            "format=${textureDescriptor.format} usage=${textureDescriptor.usageLabels.sorted().joinToString(",")} " +
            "sampleCount=${textureDescriptor.sampleCount} view=${viewDescriptor.viewDimension} " +
            "mipRange=${viewDescriptor.mipRange}",
        "sampler:descriptor address=${samplerDescriptor.addressModeU}/${samplerDescriptor.addressModeV} " +
            "filter=${samplerDescriptor.magFilter}/${samplerDescriptor.minFilter} " +
            "mipmap=${samplerDescriptor.mipmapFilter}",
        "binding:sampledTexture label=${binding.bindingLabel} " +
            "layout=group1.binding1.texture_2d_rgba8_unorm sampler=group1.binding2.sampler",
        "material:key=${materialKey.value} " +
            "excludes=upload-artifact-key,pixel-content,row-bytes,resource-handle",
        imageNonClaimLine,
    )
}

/** Emits stable M4 sampler/tile/mipmap boundary evidence lines. */
fun GPUImageSamplerBoundaryPlan.dumpLines(): List<String> {
    if (routeKind == "RefuseDiagnostic") {
        val diagnostic = diagnostics.firstOrNull()
        return listOf(
            "sampler-boundary:refused row=gpu-renderer.sampler-boundary " +
                "reason=${diagnostic?.code ?: "unknown"} source=${source.sourceId} " +
                "routeKind=RefuseDiagnostic classification=$classification promoted=$promoted",
            samplerBoundaryNonClaimLine,
        )
    }

    return listOf(
        "sampler-boundary:accepted row=gpu-renderer.sampler-boundary " +
            "routeKind=$routeKind classification=$classification promoted=$promoted " +
            "productActivation=false source=${source.sourceId}",
        "sampler-boundary:sampler descriptor=$samplerDescriptorHash " +
            "address=${samplerDescriptor.addressModeU}/${samplerDescriptor.addressModeV} " +
            "filter=${samplerDescriptor.magFilter}/${samplerDescriptor.minFilter} " +
            "mipmap=${samplerDescriptor.mipmapFilter} " +
            "lod=${samplerDescriptor.lodMinClamp}..${samplerDescriptor.lodMaxClamp} " +
            "compare=${samplerDescriptor.compareMode} " +
            "anisotropy=${samplerDescriptor.maxAnisotropy} " +
            "capabilities=${samplerDescriptor.capabilityRequirements.dumpCapabilities()}",
        "sampler-boundary:tile x=${sampling.tileModeX} y=${sampling.tileModeY} " +
            "address=${samplerDescriptor.addressModeU}/${samplerDescriptor.addressModeV} " +
            "wgsl=hardware-address-mode broadTileSupport=false",
        "sampler-boundary:mip requested=${sampling.mipmapMode} " +
            "availableLevels=${viewDescriptor.mipRange.count()} " +
            "viewMipRange=${viewDescriptor.mipRange} policy=no-mipmap support=false",
        "sampler-boundary:key sampler=$samplerBehaviorKey " +
            "includes=tile-mode,filter-mode,mipmap-mode,lod-clamp,compare-mode,anisotropy,coordinate-transform " +
            "excludes=texture-handle,upload-artifact-key,pixel-content,row-bytes,sampler-object",
        "sampler-boundary:pipelineKey=$pipelineKey " +
            "includes=binding-layout,sample-type,coordinate-transform-class " +
            "excludes=address-mode,filter-mode,mipmap-mode,lod-clamp,anisotropy,resource-handle,artifact-key,pixel-content",
        samplerBoundaryNonClaimLine,
    )
}

/**
 * Derives texture/sampler binding materialization preimage from sampler boundary evidence.
 *
 * The result names descriptor hashes and binding slots only. It does not create
 * texture or sampler handles, does not inspect cache residency, and does not
 * activate an image product route.
 */
fun GPUImageSamplerBoundaryPlan.toTextureSamplerBindingPreimage(): GPUResourceMaterializationPreimagePlan {
    val sourceLabel = source.sourceId.toMaterializationPreimageLabel()
    val planLabel = "image-sampler-binding:$sourceLabel"
    val refusal = diagnostics.firstOrNull { diagnostic -> diagnostic.terminal }?.code
    if (refusal != null || routeKind == "RefuseDiagnostic") {
        return GPUResourceMaterializationPreimagePlan(
            planLabel = planLabel,
            sourceGate = samplerBoundarySourceGate,
            accepted = false,
            resources = emptyList(),
            refusalCode = refusal ?: "unsupported.image.sampler_boundary",
        )
    }

    return GPUResourceMaterializationPreimagePlan(
        planLabel = planLabel,
        sourceGate = samplerBoundarySourceGate,
        accepted = true,
        resources = listOf(
            GPUMaterializedResourceReference(
                label = "image-texture:$sourceLabel",
                role = GPUMaterializedResourceRole.SampledTexture,
                descriptorHash = viewDescriptor.textureDescriptorHash,
                generation = source.generation,
                lifetimeClass = "recording-local",
                usageLabels = textureDescriptor.usageLabels.sorted(),
                evidenceFacts = mapOf(
                    "source" to source.sourceId,
                    "view" to viewDescriptor.viewDimension,
                ),
            ),
            GPUMaterializedResourceReference(
                label = "image-sampler:${samplerDescriptorHash.removePrefix("sha256:")}",
                role = GPUMaterializedResourceRole.Sampler,
                descriptorHash = samplerDescriptorHash,
                generation = 0,
                lifetimeClass = "pipeline-cache",
                evidenceFacts = mapOf(
                    "address" to "${samplerDescriptor.addressModeU}/${samplerDescriptor.addressModeV}",
                    "filter" to "${samplerDescriptor.magFilter}/${samplerDescriptor.minFilter}",
                    "mipmap" to samplerDescriptor.mipmapFilter,
                ),
            ),
        ),
        bindingLabels = listOf("group1.binding1", "group1.binding2"),
    )
}

private const val samplerBoundarySourceGate = "gpu-renderer.sampler-boundary"
private const val bindingLabel = "sampled-texture.image-shader"
private const val materialKeyPrefix = "image.shader.decoded-pixels.v1"
private const val materialSourceKey = "image-source:decoded-pixels"
private const val uploadArtifactDescriptorVersion = "descriptorv1"
private const val uploadArtifactGeneratorVersion = "m4-decoded-image-v1"
private const val uploadArtifactConformanceTier = "contract-only"
private const val decodedImageUploadBudgetClass = "image-small"
private const val imageNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-codec-support no-mipmap-support " +
        "no-broad-image-support no-cpu-rendered-compat-texture"
private const val samplerBoundaryNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-native-sampler-support " +
        "no-mipmap-support no-broad-tile-mode-support no-perspective-sampling no-cpu-rendered-compat-texture"
private const val codecNonClaimLine =
    "nonclaim:no-codec-implementation no-decode-output no-uploaded-texture-route-from-provenance " +
        "no-platform-decoder-substitute no-product-activation"

private fun GPUDecodedImagePixelsDescriptor.toImageSourceDescriptor(): GPUImageSourceDescriptor =
    GPUImageSourceDescriptor(
        sourceId = sourceId,
        sourceKind = "AlreadyDecodedPixels",
        sizeLabel = "${width}x$height",
        colorProfileLabel = colorProfileLabel,
        provenance = provenance,
    )

private fun GPUDecodedImagePixelsDescriptor.toPixelPlan(): GPUImagePixelPlan =
    GPUImagePixelPlan(
        width = width,
        height = height,
        format = pixelFormat,
        rowBytes = rowBytes,
        alphaType = alphaType,
    )

private fun GPUDecodedImagePixelsDescriptor.toTextureDescriptor(): GPUTextureDescriptor =
    GPUTextureDescriptor(
        width = width.coerceAtLeast(1),
        height = height.coerceAtLeast(1),
        format = pixelFormat.ifBlank { "unknown" },
        usageLabels = setOf("copy_dst", "texture_binding"),
        sampleCount = 1,
    )

private fun GPUDecodedImagePixelsDescriptor.placeholderArtifact(
    textureDescriptor: GPUTextureDescriptor,
): UploadedTextureArtifact {
    val safePixelPlan = GPUImagePixelPlan(
        width = textureDescriptor.width,
        height = textureDescriptor.height,
        format = textureDescriptor.format,
        rowBytes = rowBytes.coerceAtLeast(1L),
        alphaType = alphaType.ifBlank { "unknown" },
    )
    val safeMipmapPlan = GPUImageMipmapPlan(
        generateMipmaps = false,
        levelCount = 1,
        filterPolicy = "none",
    )
    val safeKey = GPUImageUploadArtifactKey("refused.image.${sourceId.encodeForImageKey()}")
    val safeUploadPlan = GPUImageUploadPlan(
        artifactKey = safeKey,
        pixelPlan = safePixelPlan,
        mipmapPlan = safeMipmapPlan,
        uploadBudgetClass = "refused",
    )
    return UploadedTextureArtifact(
        artifactKey = safeKey,
        pixelPlan = safePixelPlan,
        uploadPlan = safeUploadPlan,
        generation = generation.coerceAtLeast(0L),
        lifetimeClass = "refused",
    )
}

private fun GPUDecodedImagePixelsDescriptor.artifactKey(): GPUImageUploadArtifactKey =
    GPUImageUploadArtifactKey(
        "uploaded.image.decoded." +
            "$uploadArtifactDescriptorVersion." +
            "src${sourceId.encodeForImageKey()}." +
            "hash${contentHash.encodeForImageKey()}." +
            "gen$generation." +
            "${width}x$height." +
            "${pixelFormat.lowercase()}." +
            "row$rowBytes." +
            "alpha.${alphaType.lowercase()}." +
            "color.${colorProfileLabel.stableImageKeyLabel()}." +
            "orientation.${orientationState.lowercase()}." +
            "conformance.$uploadArtifactConformanceTier." +
            "budget.$decodedImageUploadBudgetClass." +
            "generator.$uploadArtifactGeneratorVersion." +
            "mips1",
    )

private fun GPUDecodedImagePixelsDescriptor.refusalCode(maxUploadBytes: Long): String? =
    when {
        sourceId.isBlank() || width <= 0 || height <= 0 || generation < 0 -> "unsupported.image.source_descriptor_invalid"
        pixelFormat != "RGBA8Unorm" -> "unsupported.image.pixel.format"
        alphaType !in setOf("Premul", "Unpremul", "Opaque") -> "unsupported.image.pixel.format"
        !colorProfileLabel.isDeterministicImageKeyFact() -> "unsupported.image.upload.artifact_key_nondeterministic"
        rowBytes < width * 4L || rowBytes % 4L != 0L -> "unsupported.image.pixel.row_stride"
        orientationState != "Applied" -> "unsupported.image.orientation"
        !contentHash.isDeterministicImageKeyFact() -> "unsupported.image.upload.artifact_key_nondeterministic"
        rowBytes * height > maxUploadBytes -> "unsupported.image.upload.budget_exceeded"
        else -> null
    }

private fun GPUDecodedImageSamplingPlan.refusalCode(): String? =
    when {
        tileModeX != "clamp" || tileModeY != "clamp" -> "unsupported.image.tile_mode"
        mipmapMode != "none" -> "unsupported.image.mip_required"
        filterMode == "cubic" -> "unsupported.image.sampling_cubic"
        filterMode !in setOf("nearest", "linear") -> "unsupported.image.sampling_filter"
        anisotropy < 1 -> "unsupported.image.sampler_anisotropy"
        anisotropy > 1 -> "unsupported.image.sampling_anisotropic"
        !hasAcceptedNoMipLodClamp() -> "unsupported.image.sampler_lod_clamp"
        coordinateTransformClass == "perspective" -> "unsupported.image.perspective_sampling"
        coordinateTransformClass != "affine" -> "unsupported.image.coordinate_transform"
        else -> null
    }

private fun GPUDecodedImageSamplingPlan.samplerBoundaryRefusalCode(availableMipLevels: Int): String? =
    when {
        tileModeX != "clamp" || tileModeY != "clamp" -> "unsupported.image.tile_mode"
        mipmapMode != "none" && availableMipLevels <= 1 -> "unsupported.texture.mipmap_unavailable"
        filterMode == "cubic" -> "unsupported.image.sampling_cubic"
        filterMode !in setOf("nearest", "linear") -> "unsupported.image.sampling_filter"
        anisotropy < 1 -> "unsupported.image.sampler_anisotropy"
        anisotropy > 1 -> "unsupported.image.sampling_anisotropic"
        !hasAcceptedNoMipLodClamp() -> "unsupported.image.sampler_lod_clamp"
        coordinateTransformClass == "perspective" -> "unsupported.image.perspective_sampling"
        coordinateTransformClass != "affine" -> "unsupported.image.coordinate_transform"
        else -> null
    }

private fun GPUDecodedImageSamplingPlan.toSamplerDescriptor(): GPUSamplerDescriptor =
    GPUSamplerDescriptor(
        addressModeU = tileModeX.toAddressMode(),
        addressModeV = tileModeY.toAddressMode(),
        magFilter = filterMode,
        minFilter = filterMode,
        mipmapFilter = mipmapMode,
        lodMinClamp = lodMinClamp,
        lodMaxClamp = lodMaxClamp,
        maxAnisotropy = anisotropy,
        capabilityRequirements = samplerCapabilityRequirements(),
    )

private fun GPUDecodedImageSamplingPlan.toMaterialSource(): GPUMaterialSourceDescriptor =
    GPUMaterialSourceDescriptor.Image(
        GPUImageShaderPlan(
            imageSourceKey = materialSourceKey,
            sampling = GPUMaterialSamplingPlan(
                tileModeX = tileModeX.toMaterialTileMode(),
                tileModeY = tileModeY.toMaterialTileMode(),
                filterMode = filterMode,
                mipmapMode = mipmapMode,
            ),
            colorTreatment = "sampled-unpremul-srgb-to-target",
        ),
    )

private fun GPUDecodedImageSamplingPlan.materialKey(): MaterialKey {
    val preimage = listOf(
        "sourceKind=ImageShader",
        "imageSourceKind=AlreadyDecodedPixels",
        "snippet=material.image_shader.decoded_pixels.v1",
        "bindingLayout=group1.binding1.texture_2d_rgba8_unorm+group1.binding2.sampler",
        "tileModeX=$tileModeX",
        "tileModeY=$tileModeY",
        "filterMode=$filterMode",
        "mipmapMode=$mipmapMode",
        "lodMinClamp=$lodMinClamp",
        "lodMaxClamp=$lodMaxClamp",
        "anisotropy=$anisotropy",
        "compareMode=none",
        "coordinateTransformClass=$coordinateTransformClass",
        "colorTreatment=sampled-unpremul-srgb-to-target",
    ).joinToString("\n")

    return MaterialKey("$materialKeyPrefix:${preimage.stableImageHash()}")
}

private fun GPUDecodedImageSamplingPlan.samplerBehaviorKey(): String {
    val preimage = listOf(
        "version=sampler-boundary-v1",
        "tileModeX=$tileModeX",
        "tileModeY=$tileModeY",
        "filterMode=$filterMode",
        "mipmapMode=$mipmapMode",
        "lodMinClamp=$lodMinClamp",
        "lodMaxClamp=$lodMaxClamp",
        "compareMode=none",
        "anisotropy=$anisotropy",
        "coordinateTransformClass=$coordinateTransformClass",
    ).joinToString("\n")

    return "sampler-boundary:${preimage.stableImageHash()}"
}

private fun GPUDecodedImageSamplingPlan.samplerPipelineKey(
    textureDescriptor: GPUTextureDescriptor,
    viewDescriptor: GPUTextureViewDescriptor,
): String {
    val preimage = listOf(
        "version=sampler-pipeline-boundary-v1",
        "bindingLayout=group1.binding1.texture_2d_rgba8_unorm+group1.binding2.sampler",
        "sampleType=${textureDescriptor.format.lowercase()}",
        "viewDimension=${viewDescriptor.viewDimension}",
        "coordinateTransformClass=$coordinateTransformClass",
    ).joinToString("\n")

    return "pipeline.image-sampler-boundary.v1:${preimage.stableImageHash()}"
}

private fun GPUDecodedImageSamplingPlan.samplerCapabilityRequirements(): Set<String> =
    buildSet {
        if (anisotropy > 1) {
            add("sampler-anisotropy")
        }
    }

private fun GPUDecodedImageSamplingPlan.hasAcceptedNoMipLodClamp(): Boolean {
    val lodMin = lodMinClamp.toSamplerLodOrNull() ?: return false
    val lodMax = lodMaxClamp.toSamplerLodOrNull() ?: return false
    return lodMin == 0.0 && lodMax == 0.0
}

private fun String.toSamplerLodOrNull(): Double? {
    val value = toDoubleOrNull() ?: return null
    return value.takeIf { scalar ->
        scalar >= 0.0 &&
            !scalar.isNaN() &&
            scalar != Double.POSITIVE_INFINITY &&
            scalar != Double.NEGATIVE_INFINITY
    }
}

private fun String.toAddressMode(): String =
    when (this) {
        "clamp" -> "clamp-to-edge"
        "repeat" -> "repeat"
        "mirror" -> "mirror-repeat"
        else -> this
    }

private fun String.toMaterialTileMode(): GPUMaterialTileMode =
    when (this) {
        "clamp" -> GPUMaterialTileMode.Clamp
        "repeat" -> GPUMaterialTileMode.Repeat
        "mirror" -> GPUMaterialTileMode.Mirror
        "decal" -> GPUMaterialTileMode.Decal
        else -> GPUMaterialTileMode.Clamp
    }

private fun String.toMaterializationPreimageLabel(): String =
    replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "unknown" }

private fun GPUTextureDescriptor.stableDescriptorHash(): String =
    listOf(
        width.toString(),
        height.toString(),
        format,
        usageLabels.sorted().joinToString(","),
        sampleCount.toString(),
    ).joinToString("|").stableImageHash()

private fun GPUSamplerDescriptor.stableSamplerHash(): String =
    listOf(
        addressModeU,
        addressModeV,
        magFilter,
        minFilter,
        mipmapFilter,
        lodMinClamp,
        lodMaxClamp,
        compareMode,
        maxAnisotropy.toString(),
        capabilityRequirements.sorted().joinToString(","),
    ).joinToString("|").stableImageHash()

private fun Set<String>.dumpCapabilities(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun String.isDeterministicImageKeyFact(): Boolean =
    isNotBlank() &&
        !contains("handle", ignoreCase = true) &&
        !contains("pointer", ignoreCase = true) &&
        !contains("0x", ignoreCase = true)

private fun String.encodeForImageKey(): String =
    encodeToByteArray()
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun String.stableImageKeyLabel(): String =
    lowercase()
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                char == '-' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')

private fun String.stableImageHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        .take(16)
}

private fun GPUImageCodecDescriptor.dumpLine(): String =
    "codec:descriptor id=$codecName version=$codecVersion " +
        "formats=${supportedFormats.map { format -> format.lowercase() }.sorted().joinToString(",")} " +
        "kind=$implementationKind deterministic=$deterministic color=$colorManagementPolicy " +
        "gate=${dependencyGate ?: "none"}"
