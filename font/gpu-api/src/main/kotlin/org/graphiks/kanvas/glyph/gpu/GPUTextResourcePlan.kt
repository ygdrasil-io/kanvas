package org.graphiks.kanvas.glyph.gpu

data class GPUTextTextureOwnershipPlanRef(
    val ownershipPlanId: String,
    val provenance: String,
    val ownerScope: String,
    val resourceProvider: String,
    val deviceScope: String,
) {
    init {
        require(ownershipPlanId.isNotBlank()) { "ownershipPlanId must not be blank." }
        require(provenance == "AtlasTexture") { "text atlas ownership provenance must be AtlasTexture." }
        require(ownerScope.isNotBlank()) { "ownerScope must not be blank." }
        require(resourceProvider.isNotBlank()) { "resourceProvider must not be blank." }
        require(deviceScope.isNotBlank()) { "deviceScope must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("ownershipPlanId", ownershipPlanId, comma = true)
        appendResourceJsonField("provenance", provenance, comma = true)
        appendResourceJsonField("ownerScope", ownerScope, comma = true)
        appendResourceJsonField("resourceProvider", resourceProvider, comma = true)
        appendResourceJsonField("deviceScope", deviceScope, comma = false)
        append("}")
    }
}

data class GPUTextAtlasDescriptor(
    val descriptorId: String,
    val artifactType: String,
    val textureFormat: String,
    val width: Int,
    val height: Int,
    val pageCount: Int,
    val descriptorHash: String,
) {
    init {
        require(descriptorId.isNotBlank()) { "descriptorId must not be blank." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(textureFormat.isNotBlank()) { "textureFormat must not be blank." }
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(pageCount > 0) { "pageCount must be positive." }
        require(descriptorHash.isNotBlank()) { "descriptorHash must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("descriptorId", descriptorId, comma = true)
        appendResourceJsonField("artifactType", artifactType, comma = true)
        appendResourceJsonField("textureFormat", textureFormat, comma = true)
        appendResourceJsonField("width", width, comma = true)
        appendResourceJsonField("height", height, comma = true)
        appendResourceJsonField("pageCount", pageCount, comma = true)
        appendResourceJsonField("descriptorHash", descriptorHash, comma = false)
        append("}")
    }
}

data class GPUTextAtlasPageDescriptor(
    val pageId: String,
    val pageIndex: Int,
    val atlasGeneration: Int,
    val texturePlanId: String,
    val textureFormat: String,
    val width: Int,
    val height: Int,
    val rowStrideBytes: Int,
) {
    init {
        require(pageId.isNotBlank()) { "pageId must not be blank." }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(texturePlanId.isNotBlank()) { "texturePlanId must not be blank." }
        require(textureFormat.isNotBlank()) { "textureFormat must not be blank." }
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(rowStrideBytes >= width) { "rowStrideBytes must be >= width." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("pageId", pageId, comma = true)
        appendResourceJsonField("pageIndex", pageIndex, comma = true)
        appendResourceJsonField("atlasGeneration", atlasGeneration, comma = true)
        appendResourceJsonField("texturePlanId", texturePlanId, comma = true)
        appendResourceJsonField("textureFormat", textureFormat, comma = true)
        appendResourceJsonField("width", width, comma = true)
        appendResourceJsonField("height", height, comma = true)
        appendResourceJsonField("rowStrideBytes", rowStrideBytes, comma = false)
        append("}")
    }
}

data class GPUTextAtlasEntryRef(
    val glyphId: Int,
    val strikeKeyHash: String,
    val pageId: String,
    val pageIndex: Int,
    val atlasGeneration: Int,
    val atlasRect: GPUTextIntRect,
    val sourceBounds: GPUTextIntRect,
    val uvRect: GPUTextFloatRect,
    val sourceMaskHash: String,
) {
    init {
        require(glyphId >= 0) { "glyphId must be non-negative." }
        require(strikeKeyHash.isNotBlank()) { "strikeKeyHash must not be blank." }
        require(pageId.isNotBlank()) { "pageId must not be blank." }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(sourceMaskHash.isNotBlank()) { "sourceMaskHash must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("glyphId", glyphId, comma = true)
        appendResourceJsonField("strikeKeyHash", strikeKeyHash, comma = true)
        appendResourceJsonField("pageId", pageId, comma = true)
        appendResourceJsonField("pageIndex", pageIndex, comma = true)
        appendResourceJsonField("atlasGeneration", atlasGeneration, comma = true)
        appendResourceJsonRawField("atlasRect", atlasRect.toCanonicalJson(), comma = true)
        appendResourceJsonRawField("sourceBounds", sourceBounds.toCanonicalJson(), comma = true)
        appendResourceJsonRawField("uvRect", uvRect.toCanonicalJson(), comma = true)
        appendResourceJsonField("sourceMaskHash", sourceMaskHash, comma = false)
        append("}")
    }
}

class GPUTextDestinationTexturePlan(
    val texturePlanId: String,
    val ownershipPlanId: String,
    val textureFormat: String,
    usageFlags: List<String>,
    val width: Int,
    val height: Int,
    val rowStrideBytes: Int,
    val pageRegion: GPUTextIntRect,
) {
    val usageFlags: List<String> = usageFlags.toList()

    init {
        require(texturePlanId.isNotBlank()) { "texturePlanId must not be blank." }
        require(ownershipPlanId.isNotBlank()) { "ownershipPlanId must not be blank." }
        require(textureFormat.isNotBlank()) { "textureFormat must not be blank." }
        require(this.usageFlags.isNotEmpty()) { "usageFlags must not be empty." }
        require(this.usageFlags.all { flag -> flag.isNotBlank() }) { "usageFlags must not contain blanks." }
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(rowStrideBytes >= width) { "rowStrideBytes must be >= width." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("texturePlanId", texturePlanId, comma = true)
        appendResourceJsonField("ownershipPlanId", ownershipPlanId, comma = true)
        appendResourceJsonField("textureFormat", textureFormat, comma = true)
        appendResourceJsonStringListField("usageFlags", usageFlags, comma = true)
        appendResourceJsonField("width", width, comma = true)
        appendResourceJsonField("height", height, comma = true)
        appendResourceJsonField("rowStrideBytes", rowStrideBytes, comma = true)
        appendResourceJsonRawField("pageRegion", pageRegion.toCanonicalJson(), comma = false)
        append("}")
    }
}

data class GPUTextUploadByteRange(
    val offset: Int,
    val size: Int,
    val rowStrideBytes: Int,
    val label: String,
) {
    init {
        require(offset >= 0) { "offset must be non-negative." }
        require(size > 0) { "size must be positive." }
        require(rowStrideBytes > 0) { "rowStrideBytes must be positive." }
        require(label.isNotBlank()) { "label must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("offset", offset, comma = true)
        appendResourceJsonField("size", size, comma = true)
        appendResourceJsonField("rowStrideBytes", rowStrideBytes, comma = true)
        appendResourceJsonField("label", label, comma = false)
        append("}")
    }
}

data class GPUTextStagingPlan(
    val requiresStagingBuffer: Boolean,
    val stagingBufferBytes: Int,
    val alignmentBytes: Int,
    val lifetimeScope: String,
) {
    init {
        require(stagingBufferBytes >= 0) { "stagingBufferBytes must be non-negative." }
        require(alignmentBytes > 0) { "alignmentBytes must be positive." }
        require(lifetimeScope.isNotBlank()) { "lifetimeScope must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("requiresStagingBuffer", requiresStagingBuffer, comma = true)
        appendResourceJsonField("stagingBufferBytes", stagingBufferBytes, comma = true)
        appendResourceJsonField("alignmentBytes", alignmentBytes, comma = true)
        appendResourceJsonField("lifetimeScope", lifetimeScope, comma = false)
        append("}")
    }
}

class GPUTextRendererUploadPlan(
    val uploadPlanId: String,
    val subRunId: String,
    val sourceArtifactKeyHash: String,
    val destinationTexturePlan: GPUTextDestinationTexturePlan,
    byteRanges: List<GPUTextUploadByteRange>,
    val stagingPlan: GPUTextStagingPlan,
    val uploadBeforeSampleDependency: String,
    val uploadExecution: String = "not-executed",
) {
    val byteRanges: List<GPUTextUploadByteRange> = byteRanges.toList()

    init {
        require(uploadPlanId.isNotBlank()) { "uploadPlanId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(sourceArtifactKeyHash.isNotBlank()) { "sourceArtifactKeyHash must not be blank." }
        require(byteRanges.isNotEmpty()) { "byteRanges must not be empty." }
        require(uploadBeforeSampleDependency.isNotBlank()) { "uploadBeforeSampleDependency must not be blank." }
        require(uploadExecution == "not-executed") { "KFONT-M11-007 must not claim executed uploads." }
    }

    val byteSize: Int
        get() = byteRanges.sumOf { range -> range.size }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("uploadPlanId", uploadPlanId, comma = true)
        appendResourceJsonField("subRunId", subRunId, comma = true)
        appendResourceJsonField("sourceArtifactKeyHash", sourceArtifactKeyHash, comma = true)
        appendResourceJsonRawField("destinationTexturePlan", destinationTexturePlan.toCanonicalJson(), comma = true)
        append("\"byteRanges\":")
        append(byteRanges.joinToString(separator = ",", prefix = "[", postfix = "]") { range -> range.toCanonicalJson() })
        append(",")
        appendResourceJsonRawField("stagingPlan", stagingPlan.toCanonicalJson(), comma = true)
        appendResourceJsonField("uploadBeforeSampleDependency", uploadBeforeSampleDependency, comma = true)
        appendResourceJsonField("uploadExecution", uploadExecution, comma = false)
        append("}")
    }
}

data class GPUTextInstanceAttribute(
    val name: String,
    val format: String,
    val byteOffset: Int,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(format.isNotBlank()) { "format must not be blank." }
        require(byteOffset >= 0) { "byteOffset must be non-negative." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("name", name, comma = true)
        appendResourceJsonField("format", format, comma = true)
        appendResourceJsonField("byteOffset", byteOffset, comma = false)
        append("}")
    }
}

class GPUTextInstanceRecord(
    val glyphId: Int,
    val targetRect: GPUTextFloatRect,
    val sourceRect: GPUTextIntRect,
    val uvRect: GPUTextFloatRect,
    val pageIndex: Int,
    val atlasGeneration: Int,
    representationFlags: List<String>,
    val sdfParamsRef: String?,
) {
    val representationFlags: List<String> = representationFlags.toList()

    init {
        require(glyphId >= 0) { "glyphId must be non-negative." }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(this.representationFlags.isNotEmpty()) { "representationFlags must not be empty." }
        require(this.representationFlags.all { flag -> flag.isNotBlank() }) {
            "representationFlags must not contain blanks."
        }
        require(sdfParamsRef == null || sdfParamsRef.isNotBlank()) { "sdfParamsRef must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("glyphId", glyphId, comma = true)
        appendResourceJsonRawField("targetRect", targetRect.toCanonicalJson(), comma = true)
        appendResourceJsonRawField("sourceRect", sourceRect.toCanonicalJson(), comma = true)
        appendResourceJsonRawField("uvRect", uvRect.toCanonicalJson(), comma = true)
        appendResourceJsonField("pageIndex", pageIndex, comma = true)
        appendResourceJsonField("atlasGeneration", atlasGeneration, comma = true)
        appendResourceJsonStringListField("representationFlags", representationFlags, comma = true)
        appendResourceJsonNullableField("sdfParamsRef", sdfParamsRef, comma = false)
        append("}")
    }
}

class GPUTextInstanceLayout(
    val layoutId: String,
    val subRunId: String,
    val strideBytes: Int,
    val alignmentBytes: Int,
    attributes: List<GPUTextInstanceAttribute>,
    representationFlags: List<String>,
    val sdfParamsRequired: Boolean,
    val layoutHash: String,
) {
    val attributes: List<GPUTextInstanceAttribute> = attributes.toList()
    val representationFlags: List<String> = representationFlags.toList()

    init {
        require(layoutId.isNotBlank()) { "layoutId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(strideBytes > 0) { "strideBytes must be positive." }
        require(alignmentBytes > 0) { "alignmentBytes must be positive." }
        require(attributes.isNotEmpty()) { "attributes must not be empty." }
        require(representationFlags.isNotEmpty()) { "representationFlags must not be empty." }
        require(layoutHash.isNotBlank()) { "layoutHash must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("layoutId", layoutId, comma = true)
        appendResourceJsonField("subRunId", subRunId, comma = true)
        appendResourceJsonField("strideBytes", strideBytes, comma = true)
        appendResourceJsonField("alignmentBytes", alignmentBytes, comma = true)
        append("\"attributes\":")
        append(attributes.joinToString(separator = ",", prefix = "[", postfix = "]") { attribute ->
            attribute.toCanonicalJson()
        })
        append(",")
        appendResourceJsonStringListField("representationFlags", representationFlags, comma = true)
        appendResourceJsonField("sdfParamsRequired", sdfParamsRequired, comma = true)
        appendResourceJsonField("layoutHash", layoutHash, comma = false)
        append("}")
    }
}

class GPUTextInstanceBufferPlan(
    val instanceBufferPlanId: String,
    val subRunId: String,
    val instanceLayoutHash: String,
    val instanceCount: Int,
    val byteSize: Int,
    val lifetimeScope: String,
    val instanceUploadBeforeDrawDependency: String,
    instances: List<GPUTextInstanceRecord>,
) {
    val instances: List<GPUTextInstanceRecord> = instances.toList()

    init {
        require(instanceBufferPlanId.isNotBlank()) { "instanceBufferPlanId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(instanceLayoutHash.isNotBlank()) { "instanceLayoutHash must not be blank." }
        require(instanceCount > 0) { "instanceCount must be positive." }
        require(byteSize > 0) { "byteSize must be positive." }
        require(lifetimeScope.isNotBlank()) { "lifetimeScope must not be blank." }
        require(instanceUploadBeforeDrawDependency.isNotBlank()) {
            "instanceUploadBeforeDrawDependency must not be blank."
        }
        require(instances.size == instanceCount) { "instances must match instanceCount." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("instanceBufferPlanId", instanceBufferPlanId, comma = true)
        appendResourceJsonField("subRunId", subRunId, comma = true)
        appendResourceJsonField("instanceLayoutHash", instanceLayoutHash, comma = true)
        appendResourceJsonField("instanceCount", instanceCount, comma = true)
        appendResourceJsonField("byteSize", byteSize, comma = true)
        appendResourceJsonField("lifetimeScope", lifetimeScope, comma = true)
        appendResourceJsonField("instanceUploadBeforeDrawDependency", instanceUploadBeforeDrawDependency, comma = true)
        append("\"instances\":")
        append(instances.joinToString(separator = ",", prefix = "[", postfix = "]") { instance ->
            instance.toCanonicalJson()
        })
        append("}")
    }
}

data class GPUTextBindingSlot(
    val group: Int,
    val binding: Int,
    val name: String,
    val kind: String,
    val resourceRef: String,
) {
    init {
        require(group >= 0) { "group must be non-negative." }
        require(binding >= 0) { "binding must be non-negative." }
        require(name.isNotBlank()) { "name must not be blank." }
        require(kind.isNotBlank()) { "kind must not be blank." }
        require(resourceRef.isNotBlank()) { "resourceRef must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("group", group, comma = true)
        appendResourceJsonField("binding", binding, comma = true)
        appendResourceJsonField("name", name, comma = true)
        appendResourceJsonField("kind", kind, comma = true)
        appendResourceJsonField("resourceRef", resourceRef, comma = false)
        append("}")
    }
}

class GPUTextBinding(
    val bindingPlanId: String,
    val subRunId: String,
    val renderStep: String,
    val artifactType: String,
    val bindingLayoutHash: String,
    atlasGenerationFacts: List<String>,
    val materialPlanRef: String,
    resourceSlots: List<GPUTextBindingSlot>,
    materialKeyExcludedFields: List<String>,
) {
    val atlasGenerationFacts: List<String> = atlasGenerationFacts.toList()
    val resourceSlots: List<GPUTextBindingSlot> = resourceSlots.toList()
    val materialKeyExcludedFields: List<String> = materialKeyExcludedFields.toList()

    init {
        require(bindingPlanId.isNotBlank()) { "bindingPlanId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(bindingLayoutHash.isNotBlank()) { "bindingLayoutHash must not be blank." }
        require(atlasGenerationFacts.isNotEmpty()) { "atlasGenerationFacts must not be empty." }
        require(materialPlanRef.isNotBlank()) { "materialPlanRef must not be blank." }
        require(resourceSlots.isNotEmpty()) { "resourceSlots must not be empty." }
        require(materialKeyExcludedFields.containsAll(REQUIRED_MATERIAL_KEY_EXCLUDED_FIELDS)) {
            "materialKeyExcludedFields must prove atlas/resource facts stay out of MaterialKey."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("bindingPlanId", bindingPlanId, comma = true)
        appendResourceJsonField("subRunId", subRunId, comma = true)
        appendResourceJsonField("renderStep", renderStep, comma = true)
        appendResourceJsonField("artifactType", artifactType, comma = true)
        appendResourceJsonField("bindingLayoutHash", bindingLayoutHash, comma = true)
        appendResourceJsonStringListField("atlasGenerationFacts", atlasGenerationFacts, comma = true)
        appendResourceJsonField("materialPlanRef", materialPlanRef, comma = true)
        append("\"resourceSlots\":")
        append(resourceSlots.joinToString(separator = ",", prefix = "[", postfix = "]") { slot -> slot.toCanonicalJson() })
        append(",")
        appendResourceJsonStringListField("materialKeyExcludedFields", materialKeyExcludedFields, comma = false)
        append("}")
    }
}

class GPUTextResourcePlan(
    val resourcePlanId: String,
    val subRunId: String,
    val commandId: String,
    val route: String,
    val renderStep: String,
    val artifactType: String,
    val artifactKeyHash: String,
    textureOwnership: List<GPUTextTextureOwnershipPlanRef>,
    val atlasDescriptor: GPUTextAtlasDescriptor,
    atlasPages: List<GPUTextAtlasPageDescriptor>,
    atlasEntryRefs: List<GPUTextAtlasEntryRef>,
    val uploadPlanId: String,
    val instanceBufferPlanId: String,
    val bindingPlanId: String,
    val lifetimeScope: String,
    val resourceHandlesMaterialized: Boolean,
    diagnostics: List<String>,
) {
    val textureOwnership: List<GPUTextTextureOwnershipPlanRef> = textureOwnership.toList()
    val atlasPages: List<GPUTextAtlasPageDescriptor> = atlasPages.toList()
    val atlasEntryRefs: List<GPUTextAtlasEntryRef> = atlasEntryRefs.toList()
    val diagnostics: List<String> = diagnostics.toList()

    init {
        require(resourcePlanId.isNotBlank()) { "resourcePlanId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(route.isNotBlank()) { "route must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(artifactKeyHash.isNotBlank()) { "artifactKeyHash must not be blank." }
        require(textureOwnership.isNotEmpty()) { "textureOwnership must not be empty." }
        require(atlasPages.isNotEmpty()) { "atlasPages must not be empty." }
        require(atlasEntryRefs.isNotEmpty()) { "atlasEntryRefs must not be empty." }
        require(uploadPlanId.isNotBlank()) { "uploadPlanId must not be blank." }
        require(instanceBufferPlanId.isNotBlank()) { "instanceBufferPlanId must not be blank." }
        require(bindingPlanId.isNotBlank()) { "bindingPlanId must not be blank." }
        require(lifetimeScope.isNotBlank()) { "lifetimeScope must not be blank." }
        require(!resourceHandlesMaterialized) { "KFONT-M11-007 must not materialize GPU handles." }
        require(diagnostics.all { diagnostic -> diagnostic.startsWith("text.gpu.") }) {
            "diagnostics must use the text.gpu namespace."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendResourceJsonField("resourcePlanId", resourcePlanId, comma = true)
        appendResourceJsonField("subRunId", subRunId, comma = true)
        appendResourceJsonField("commandId", commandId, comma = true)
        appendResourceJsonField("route", route, comma = true)
        appendResourceJsonField("renderStep", renderStep, comma = true)
        appendResourceJsonField("artifactType", artifactType, comma = true)
        appendResourceJsonField("artifactKeyHash", artifactKeyHash, comma = true)
        append("\"textureOwnership\":")
        append(textureOwnership.joinToString(separator = ",", prefix = "[", postfix = "]") { ownership ->
            ownership.toCanonicalJson()
        })
        append(",")
        appendResourceJsonRawField("atlasDescriptor", atlasDescriptor.toCanonicalJson(), comma = true)
        append("\"atlasPages\":")
        append(atlasPages.joinToString(separator = ",", prefix = "[", postfix = "]") { page -> page.toCanonicalJson() })
        append(",")
        append("\"atlasEntryRefs\":")
        append(atlasEntryRefs.joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> entry.toCanonicalJson() })
        append(",")
        appendResourceJsonField("uploadPlanId", uploadPlanId, comma = true)
        appendResourceJsonField("instanceBufferPlanId", instanceBufferPlanId, comma = true)
        appendResourceJsonField("bindingPlanId", bindingPlanId, comma = true)
        appendResourceJsonField("lifetimeScope", lifetimeScope, comma = true)
        appendResourceJsonField("resourceHandlesMaterialized", resourceHandlesMaterialized, comma = true)
        appendResourceJsonStringListField("diagnostics", diagnostics, comma = false)
        append("}")
    }
}

class GPUTextResourceContractEvidence(
    val resourcePlan: GPUTextResourcePlan,
    val uploadPlan: GPUTextRendererUploadPlan,
    val instanceLayout: GPUTextInstanceLayout,
    val instanceBufferPlan: GPUTextInstanceBufferPlan,
    val bindingPlan: GPUTextBinding,
    dumpFamilies: List<String>,
) {
    val dumpFamilies: List<String> = dumpFamilies.toList()
}

data class GPUTextResourceContractFixture(
    val commandId: String,
    val subRunId: String,
    val artifactType: String,
    val artifactKeyHash: String,
    val route: String,
    val uploadPlanAvailable: Boolean,
    val uploadByteSize: Int,
    val uploadByteBudget: Int,
    val atlasPageAvailable: Boolean,
    val atlasEntryAvailable: Boolean,
    val bindingLayoutAvailable: Boolean,
) {
    init {
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(artifactKeyHash.isNotBlank()) { "artifactKeyHash must not be blank." }
        require(route.isNotBlank()) { "route must not be blank." }
        require(uploadByteSize > 0) { "uploadByteSize must be positive." }
        require(uploadByteBudget >= 0) { "uploadByteBudget must be non-negative." }
    }
}

sealed interface GPUTextResourceContractPlanningResult {
    data class Accepted(val evidence: GPUTextResourceContractEvidence) : GPUTextResourceContractPlanningResult

    data class Refused(val refusal: GPUTextRouteRefusal) : GPUTextResourceContractPlanningResult
}

fun planGPUTextResourceContracts(fixture: GPUTextResourceContractFixture): GPUTextResourceContractPlanningResult {
    val refusal = when {
        !fixture.uploadPlanAvailable -> fixture.refusal(
            reasonId = "upload-plan-missing",
            blocker = GPUTextRouteBlocker.UPLOAD_PLAN,
            handoffDiagnostic = "text.gpu.upload-plan-missing",
            rendererDiagnostic = "unsupported.text.upload_plan_missing",
        )
        fixture.uploadByteSize > fixture.uploadByteBudget -> fixture.refusal(
            reasonId = "upload-budget-exceeded",
            blocker = GPUTextRouteBlocker.UPLOAD_BUDGET,
            handoffDiagnostic = "text.gpu.upload-budget-exceeded",
            rendererDiagnostic = "unsupported.text.upload_budget_exceeded",
        )
        !fixture.atlasPageAvailable -> fixture.refusal(
            reasonId = "atlas-page-unavailable",
            blocker = GPUTextRouteBlocker.ATLAS_PAGE,
            handoffDiagnostic = "text.gpu.atlas-page-unavailable",
            rendererDiagnostic = "unsupported.text.atlas_page_unavailable",
        )
        !fixture.atlasEntryAvailable -> fixture.refusal(
            reasonId = "atlas-entry-missing",
            blocker = GPUTextRouteBlocker.ATLAS_ENTRY,
            handoffDiagnostic = "text.gpu.atlas-entry-missing",
            rendererDiagnostic = "unsupported.text.atlas_entry_missing",
        )
        !fixture.bindingLayoutAvailable -> fixture.refusal(
            reasonId = "binding-layout-unavailable",
            blocker = GPUTextRouteBlocker.BINDING_LAYOUT,
            handoffDiagnostic = "text.gpu.binding-layout-unavailable",
            rendererDiagnostic = "unsupported.text.binding_layout_unavailable",
        )
        else -> null
    }
    return if (refusal == null) {
        GPUTextResourceContractPlanningResult.Accepted(fixture.acceptedEvidence())
    } else {
        GPUTextResourceContractPlanningResult.Refused(refusal)
    }
}

fun defaultGPUTextResourceContractFixture(): GPUTextResourceContractFixture =
    GPUTextResourceContractFixture(
        commandId = "draw-text-a8-001",
        subRunId = "atlas-page-generation-split.0",
        artifactType = "GlyphAtlasArtifact",
        artifactKeyHash = "sha256:a8-atlas",
        route = "AtlasMaskSample",
        uploadPlanAvailable = true,
        uploadByteSize = 512,
        uploadByteBudget = 1024,
        atlasPageAvailable = true,
        atlasEntryAvailable = true,
        bindingLayoutAvailable = true,
    )

fun defaultGPUTextResourceContractEvidence(): GPUTextResourceContractEvidence =
    defaultGPUTextResourceContractFixture().acceptedEvidence()

private fun GPUTextResourceContractFixture.acceptedEvidence(): GPUTextResourceContractEvidence {
    val ownership = GPUTextTextureOwnershipPlanRef(
        ownershipPlanId = "ownership:text-atlas-a8-page-0",
        provenance = "AtlasTexture",
        ownerScope = "GPUResourceProvider:text-atlas-cache",
        resourceProvider = "GPUResourceProvider",
        deviceScope = "logical-device:text-fixture",
    )
    val atlasDescriptor = GPUTextAtlasDescriptor(
        descriptorId = "atlas:a8-simple-latin",
        artifactType = artifactType,
        textureFormat = "R8Unorm",
        width = 128,
        height = 64,
        pageCount = 1,
        descriptorHash = "fnv1a64:a8-atlas-descriptor",
    )
    val atlasPage = GPUTextAtlasPageDescriptor(
        pageId = "a8-page-0",
        pageIndex = 0,
        atlasGeneration = 3,
        texturePlanId = "texture:text-atlas-a8-page-0",
        textureFormat = "R8Unorm",
        width = 128,
        height = 64,
        rowStrideBytes = 128,
    )
    val entryRef = GPUTextAtlasEntryRef(
        glyphId = 42,
        strikeKeyHash = "fnv1a64:strike-key-A",
        pageId = "a8-page-0",
        pageIndex = 0,
        atlasGeneration = 3,
        atlasRect = GPUTextIntRect(left = 4, top = 8, right = 16, bottom = 24),
        sourceBounds = GPUTextIntRect(left = 0, top = -12, right = 12, bottom = 4),
        uvRect = GPUTextFloatRect(left = 0.03125f, top = 0.125f, right = 0.125f, bottom = 0.375f),
        sourceMaskHash = "sha256:glyph-mask-A",
    )
    val uploadPlan = GPUTextRendererUploadPlan(
        uploadPlanId = "gpu-text-upload-a8-page-0",
        subRunId = subRunId,
        sourceArtifactKeyHash = artifactKeyHash,
        destinationTexturePlan = GPUTextDestinationTexturePlan(
            texturePlanId = "texture:text-atlas-a8-page-0",
            ownershipPlanId = ownership.ownershipPlanId,
            textureFormat = "R8Unorm",
            usageFlags = listOf("copyDst", "sampledTexture"),
            width = 128,
            height = 64,
            rowStrideBytes = 128,
            pageRegion = GPUTextIntRect(left = 0, top = 0, right = 128, bottom = 64),
        ),
        byteRanges = listOf(
            GPUTextUploadByteRange(offset = 0, size = uploadByteSize, rowStrideBytes = 128, label = "page-0"),
        ),
        stagingPlan = GPUTextStagingPlan(
            requiresStagingBuffer = true,
            stagingBufferBytes = uploadByteSize,
            alignmentBytes = 256,
            lifetimeScope = "recording-pass",
        ),
        uploadBeforeSampleDependency = "upload-before-sample:upload-a8-page-0->sample-after-upload:0",
    )
    val instanceLayout = GPUTextInstanceLayout(
        layoutId = "text.a8-mask.instance-layout.v1",
        subRunId = subRunId,
        strideBytes = 48,
        alignmentBytes = 16,
        attributes = listOf(
            GPUTextInstanceAttribute(name = "targetRect", format = "float32x4", byteOffset = 0),
            GPUTextInstanceAttribute(name = "uvRect", format = "float32x4", byteOffset = 16),
            GPUTextInstanceAttribute(name = "pageAndFlags", format = "uint32x4", byteOffset = 32),
        ),
        representationFlags = listOf("A8MaskAtlas"),
        sdfParamsRequired = false,
        layoutHash = "fnv1a64:text-a8-instance-layout",
    )
    val instanceBufferPlan = GPUTextInstanceBufferPlan(
        instanceBufferPlanId = "gpu-text-instance-buffer-a8-0",
        subRunId = subRunId,
        instanceLayoutHash = instanceLayout.layoutHash,
        instanceCount = 1,
        byteSize = 48,
        lifetimeScope = "recording-pass",
        instanceUploadBeforeDrawDependency = "instance-upload-before-draw:instance-a8-0->draw-text-a8-001",
        instances = listOf(
            GPUTextInstanceRecord(
                glyphId = 42,
                targetRect = GPUTextFloatRect(left = 10.0f, top = 20.0f, right = 22.0f, bottom = 36.0f),
                sourceRect = entryRef.sourceBounds,
                uvRect = entryRef.uvRect,
                pageIndex = entryRef.pageIndex,
                atlasGeneration = entryRef.atlasGeneration,
                representationFlags = listOf("A8MaskAtlas"),
                sdfParamsRef = null,
            ),
        ),
    )
    val bindingPlan = GPUTextBinding(
        bindingPlanId = "gpu-text-binding-a8-0",
        subRunId = subRunId,
        renderStep = "A8TextMaskStep",
        artifactType = artifactType,
        bindingLayoutHash = "fnv1a64:text-a8-layout",
        atlasGenerationFacts = listOf("a8-page-0:generation=3"),
        materialPlanRef = "material:text-black",
        resourceSlots = listOf(
            GPUTextBindingSlot(group = 2, binding = 0, name = "glyphAtlas", kind = "sampledTexture", resourceRef = "texture:text-atlas-a8-page-0"),
            GPUTextBindingSlot(group = 2, binding = 1, name = "glyphSampler", kind = "sampler", resourceRef = "sampler:text-atlas-linear-clamp"),
            GPUTextBindingSlot(group = 2, binding = 2, name = "textParams", kind = "uniformBuffer", resourceRef = "uniform:text-a8-params"),
            GPUTextBindingSlot(group = 1, binding = 0, name = "glyphInstances", kind = "vertexBuffer", resourceRef = "gpu-text-instance-buffer-a8-0"),
        ),
        materialKeyExcludedFields = REQUIRED_MATERIAL_KEY_EXCLUDED_FIELDS,
    )
    val resourcePlan = GPUTextResourcePlan(
        resourcePlanId = "gpu-text-resource-a8-0",
        subRunId = subRunId,
        commandId = commandId,
        route = route,
        renderStep = "A8TextMaskStep",
        artifactType = artifactType,
        artifactKeyHash = artifactKeyHash,
        textureOwnership = listOf(ownership),
        atlasDescriptor = atlasDescriptor,
        atlasPages = listOf(atlasPage),
        atlasEntryRefs = listOf(entryRef),
        uploadPlanId = uploadPlan.uploadPlanId,
        instanceBufferPlanId = instanceBufferPlan.instanceBufferPlanId,
        bindingPlanId = bindingPlan.bindingPlanId,
        lifetimeScope = "atlas-cache",
        resourceHandlesMaterialized = false,
        diagnostics = listOf("text.gpu.resource-plan-ready"),
    )
    return GPUTextResourceContractEvidence(
        resourcePlan = resourcePlan,
        uploadPlan = uploadPlan,
        instanceLayout = instanceLayout,
        instanceBufferPlan = instanceBufferPlan,
        bindingPlan = bindingPlan,
        dumpFamilies = listOf("GPUTextResourcePlan", "GPUTextUploadPlan", "GPUTextInstanceLayout", "GPUTextBinding"),
    )
}

fun defaultGPUTextResourcePlanReportJson(): String {
    val evidence = defaultGPUTextResourceContractEvidence()
    return gpuTextResourceReportJson(
        schema = RESOURCE_PLAN_REPORT_SCHEMA,
        arrayName = "resourcePlans",
        values = listOf(evidence.resourcePlan.toCanonicalJson()),
    )
}

fun defaultGPUTextUploadPlanReportJson(): String {
    val evidence = defaultGPUTextResourceContractEvidence()
    return gpuTextResourceReportJson(
        schema = UPLOAD_PLAN_REPORT_SCHEMA,
        arrayName = "uploadPlans",
        values = listOf(evidence.uploadPlan.toCanonicalJson()),
    )
}

fun defaultGPUTextInstanceLayoutReportJson(): String {
    val evidence = defaultGPUTextResourceContractEvidence()
    return gpuTextResourceReportJson(
        schema = INSTANCE_LAYOUT_REPORT_SCHEMA,
        arrayName = "instanceLayouts",
        values = listOf(evidence.instanceLayout.toCanonicalJson()),
        extraFields = listOf("instanceBufferPlans" to "[${evidence.instanceBufferPlan.toCanonicalJson()}]"),
    )
}

fun defaultGPUTextBindingPlanReportJson(): String {
    val evidence = defaultGPUTextResourceContractEvidence()
    return gpuTextResourceReportJson(
        schema = BINDING_PLAN_REPORT_SCHEMA,
        arrayName = "bindingPlans",
        values = listOf(evidence.bindingPlan.toCanonicalJson()),
    )
}

fun defaultGPUTextResourceRefusalReportJson(): String =
    defaultGPUTextResourceRefusalReport().toCanonicalJson()

fun defaultGPUTextResourceRefusalReport(): GPUTextRouteRefusalReport {
    val fixture = defaultGPUTextResourceContractFixture()
    val refusals = listOf(
        fixture.copy(uploadPlanAvailable = false),
        fixture.copy(uploadByteBudget = 128),
        fixture.copy(atlasPageAvailable = false),
        fixture.copy(atlasEntryAvailable = false),
        fixture.copy(bindingLayoutAvailable = false),
    ).map { case ->
        (planGPUTextResourceContracts(case) as GPUTextResourceContractPlanningResult.Refused).refusal
    }
    return GPUTextRouteRefusalReport(
        fixtureName = "gpu-text-resource-refusals.json",
        refusals = refusals,
    )
}

private fun GPUTextResourceContractFixture.refusal(
    reasonId: String,
    blocker: GPUTextRouteBlocker,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
): GPUTextRouteRefusal =
    GPUTextRouteRefusal(
        refusalId = "gpu-text-resource-a8-0.$reasonId",
        commandId = commandId,
        textRange = null,
        glyphRange = "glyphs:0..0",
        artifactType = artifactType,
        artifactKeyHash = artifactKeyHash,
        attemptedRoute = route,
        blocker = blocker,
        handoffDiagnostic = handoffDiagnostic,
        rendererDiagnostic = rendererDiagnostic,
        legacyGates = listOf("dftext"),
    )

private fun gpuTextResourceReportJson(
    schema: String,
    arrayName: String,
    values: List<String>,
    extraFields: List<Pair<String, String>> = emptyList(),
): String = buildString {
    append("{")
    appendResourceJsonField("schema", schema, comma = true)
    appendResourceJsonStringListField("ownerTickets", listOf("KFONT-M11-007"), comma = true)
    appendResourceJsonField("classification", "GPU-gated", comma = true)
    append(arrayName.resourceQuoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]"))
    append(",")
    extraFields.forEach { (name, value) ->
        appendResourceJsonRawField(name, value, comma = true)
    }
    appendResourceJsonStringListField("nonClaims", DEFAULT_RESOURCE_NON_CLAIMS, comma = true)
    appendResourceJsonField("routePromotion", "not-promoted", comma = true)
    appendResourceJsonField("productActivation", false, comma = false)
    append("}\n")
}

private fun StringBuilder.appendResourceJsonField(name: String, value: String, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(value.resourceQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendResourceJsonField(name: String, value: Int, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendResourceJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendResourceJsonNullableField(name: String, value: String?, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(value?.resourceQuoted() ?: "null")
    if (comma) append(",")
}

private fun StringBuilder.appendResourceJsonRawField(name: String, value: String, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendResourceJsonStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.resourceQuoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]") { value -> value.resourceQuoted() })
    if (comma) append(",")
}

private fun String.resourceQuoted(): String = "\"${resourceEscapeJson()}\""

private fun String.resourceEscapeJson(): String = buildString(length) {
    for (ch in this@resourceEscapeJson) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

private const val RESOURCE_PLAN_REPORT_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextResourcePlanReport.v1"
private const val UPLOAD_PLAN_REPORT_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextUploadPlanReport.v1"
private const val INSTANCE_LAYOUT_REPORT_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextInstanceLayoutReport.v1"
private const val BINDING_PLAN_REPORT_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextBindingPlanReport.v1"

private val REQUIRED_MATERIAL_KEY_EXCLUDED_FIELDS = listOf(
    "glyphId",
    "atlasRect",
    "atlasGeneration",
    "uploadToken",
    "liveTextureHandle",
)

private val DEFAULT_RESOURCE_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
    "no-executed-gpu-upload-claim",
)
