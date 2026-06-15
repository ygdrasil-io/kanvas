package org.graphiks.kanvas.glyph.gpu

/**
 * Registry metadata for one text artifact type that may cross the font-to-renderer boundary.
 *
 * Registration makes the artifact type visible to route planning, but it is not
 * a GPU support claim. Individual routes still need route-specific implementation
 * and evidence before product activation can be enabled.
 */
data class TextGPUArtifactDescriptor(
    val artifactName: String,
    val descriptorVersion: Int,
    val ownerSubsystem: String,
    val keyPreimageFields: List<String>,
    val lifetimeClass: String,
    val invalidationFacts: List<String>,
    val memoryBudgetClass: String,
    val uploadBudgetClass: String?,
    val supportedRoutes: List<String>,
    val missingDiagnostic: String,
    val productActivation: Boolean = false,
) {
    init {
        require(artifactName.isNotBlank()) { "artifactName must not be blank." }
        require(descriptorVersion > 0) { "descriptorVersion must be positive." }
        require(ownerSubsystem.isNotBlank()) { "ownerSubsystem must not be blank." }
        require(keyPreimageFields.all { field -> field.isNotBlank() }) {
            "keyPreimageFields must not contain blank entries."
        }
        require(lifetimeClass.isNotBlank()) { "lifetimeClass must not be blank." }
        require(invalidationFacts.all { fact -> fact.isNotBlank() }) {
            "invalidationFacts must not contain blank entries."
        }
        require(memoryBudgetClass.isNotBlank()) { "memoryBudgetClass must not be blank." }
        require(uploadBudgetClass == null || uploadBudgetClass.isNotBlank()) {
            "uploadBudgetClass must not be blank when present."
        }
        require(supportedRoutes.all { route -> route.isNotBlank() }) {
            "supportedRoutes must not contain blank entries."
        }
        require(missingDiagnostic.isNotBlank()) { "missingDiagnostic must not be blank." }
        require(!productActivation) {
            "Text GPU artifact registration does not activate product renderer support."
        }
    }

    val descriptorCompactHash: String
        get() = textGPUArtifactDescriptorCompactHash(this)

    fun noSkLeakageReport(): TextPayloadLeakReport = validateGPUTextNoSkLeakage(
        payloadKind = "TextGPUArtifactDescriptor",
        fields = textPayloadLeakageFields(fieldPrefix = "descriptor"),
    )

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextArtifactJsonField("artifactName", artifactName, comma = true)
        appendTextArtifactJsonField("descriptorVersion", descriptorVersion, comma = true)
        appendTextArtifactJsonField("descriptorCompactHash", descriptorCompactHash, comma = true)
        appendTextArtifactJsonField("ownerSubsystem", ownerSubsystem, comma = true)
        appendTextArtifactJsonField("keyPreimageFields", keyPreimageFields, comma = true)
        appendTextArtifactJsonField("lifetimeClass", lifetimeClass, comma = true)
        appendTextArtifactJsonField("invalidationFacts", invalidationFacts, comma = true)
        appendTextArtifactJsonField("memoryBudgetClass", memoryBudgetClass, comma = true)
        appendTextArtifactJsonNullableField("uploadBudgetClass", uploadBudgetClass, comma = true)
        appendTextArtifactJsonField("supportedRoutes", supportedRoutes, comma = true)
        appendTextArtifactJsonField("missingDiagnostic", missingDiagnostic, comma = true)
        appendTextArtifactJsonField("productActivation", productActivation, comma = false)
        append("}")
    }
}

/**
 * Stable refusal emitted when a caller offers an artifact type that is not registered.
 */
data class TextGPUArtifactUnregisteredRefusal(
    val artifactName: String,
    val artifactHash: String?,
    val handoffDiagnostic: String = TEXT_GPU_ARTIFACT_UNREGISTERED_HANDOFF_DIAGNOSTIC,
    val rendererDiagnostic: String = TEXT_GPU_ARTIFACT_UNREGISTERED_RENDERER_DIAGNOSTIC,
    val claimPromotionAllowed: Boolean = false,
) {
    init {
        require(artifactName.isNotBlank()) { "artifactName must not be blank." }
        require(artifactHash == null || artifactHash.isNotBlank()) {
            "artifactHash must not be blank when present."
        }
        require(handoffDiagnostic.isNotBlank()) { "handoffDiagnostic must not be blank." }
        require(rendererDiagnostic.isNotBlank()) { "rendererDiagnostic must not be blank." }
        require(!claimPromotionAllowed) {
            "Unregistered text artifacts cannot promote renderer support claims."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextArtifactJsonField("artifactName", artifactName, comma = true)
        appendTextArtifactJsonNullableField("artifactHash", artifactHash, comma = true)
        appendTextArtifactJsonField("handoffDiagnostic", handoffDiagnostic, comma = true)
        appendTextArtifactJsonField("rendererDiagnostic", rendererDiagnostic, comma = true)
        appendTextArtifactJsonField("claimPromotionAllowed", claimPromotionAllowed, comma = false)
        append("}")
    }
}

/**
 * Deterministic registry of text GPU artifact descriptors.
 */
class TextGPUArtifactRegistry(descriptors: List<TextGPUArtifactDescriptor>) {
    val descriptors: List<TextGPUArtifactDescriptor> = descriptors.map { descriptor -> descriptor.snapshot() }

    init {
        require(this.descriptors.map { descriptor -> descriptor.artifactName }.distinct().size == this.descriptors.size) {
            "Text GPU artifact registry descriptors must have unique artifactName values."
        }
    }

    fun descriptor(artifactName: String): TextGPUArtifactDescriptor? =
        descriptors.singleOrNull { descriptor -> descriptor.artifactName == artifactName }

    fun refuseUnregistered(
        typeName: String,
        artifactHash: String?,
    ): TextGPUArtifactUnregisteredRefusal = TextGPUArtifactUnregisteredRefusal(
        artifactName = typeName,
        artifactHash = artifactHash,
    )

    fun noSkLeakageReport(): TextPayloadLeakReport = validateGPUTextNoSkLeakage(
        payloadKind = "TextGPUArtifactRegistry",
        fields = descriptors.flatMapIndexed { index, descriptor ->
            descriptor.textPayloadLeakageFields(fieldPrefix = "descriptors[$index]")
        },
    )

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextArtifactJsonField("schema", TEXT_GPU_ARTIFACT_REGISTRY_SCHEMA, comma = true)
        append("\"descriptors\":")
        append(descriptors.joinToString(separator = ",", prefix = "[", postfix = "]") { descriptor ->
            descriptor.toCanonicalJson()
        })
        append("}")
    }
}

private fun TextGPUArtifactDescriptor.snapshot(): TextGPUArtifactDescriptor = copy(
    keyPreimageFields = keyPreimageFields.toList(),
    invalidationFacts = invalidationFacts.toList(),
    supportedRoutes = supportedRoutes.toList(),
)

fun defaultTextGPUArtifactRegistry(): TextGPUArtifactRegistry = TextGPUArtifactRegistry(
    listOf(
        textGPUArtifactDescriptor(
            artifactName = "GlyphAtlasArtifact",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "atlasDimensions",
                "texelFormat",
            ),
            lifetimeClass = "atlas-generation",
            invalidationFacts = listOf("generation", "contentFingerprint", "atlasCapacity"),
            memoryBudgetClass = "glyph-atlas-memory",
            uploadBudgetClass = "glyph-atlas-upload",
            supportedRoutes = listOf("AtlasMaskSample"),
        ),
        textGPUArtifactDescriptor(
            artifactName = "SDFGlyphAtlasArtifact",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "atlasDimensions",
                "distanceRange",
            ),
            lifetimeClass = "atlas-generation",
            invalidationFacts = listOf("generation", "contentFingerprint", "distanceRange"),
            memoryBudgetClass = "sdf-atlas-memory",
            uploadBudgetClass = "sdf-atlas-upload",
        ),
        textGPUArtifactDescriptor(
            artifactName = "GlyphUploadPlan",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "uploadRanges",
                "glyphIDs",
            ),
            lifetimeClass = "upload-plan",
            invalidationFacts = listOf("generation", "contentFingerprint", "payloadByteSize"),
            memoryBudgetClass = "glyph-upload-payload",
            uploadBudgetClass = "glyph-upload-bytes",
        ),
        textGPUArtifactDescriptor(
            artifactName = "OutlineGlyphPlan",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "glyphIDs",
                "windingRule",
            ),
            lifetimeClass = "glyph-plan",
            invalidationFacts = listOf("generation", "contentFingerprint", "outlinePolicy"),
            memoryBudgetClass = "outline-plan-memory",
            uploadBudgetClass = null,
        ),
        textGPUArtifactDescriptor(
            artifactName = "ColorGlyphPlan",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "glyphIDs",
                "layerCount",
            ),
            lifetimeClass = "glyph-plan",
            invalidationFacts = listOf("generation", "contentFingerprint", "colorLayerPolicy"),
            memoryBudgetClass = "color-glyph-plan-memory",
            uploadBudgetClass = null,
        ),
        textGPUArtifactDescriptor(
            artifactName = "BitmapGlyphPlan",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "glyphIDs",
                "colorFormat",
            ),
            lifetimeClass = "glyph-plan",
            invalidationFacts = listOf("generation", "contentFingerprint", "bitmapPayloadPolicy"),
            memoryBudgetClass = "bitmap-glyph-plan-memory",
            uploadBudgetClass = null,
        ),
        textGPUArtifactDescriptor(
            artifactName = "SVGGlyphPlan",
            keyPreimageFields = listOf(
                "artifactID",
                "generation",
                "contentFingerprint",
                "glyphIDs",
                "documentCount",
            ),
            lifetimeClass = "glyph-plan",
            invalidationFacts = listOf("generation", "contentFingerprint", "vectorDocumentPolicy"),
            memoryBudgetClass = "svg-glyph-plan-memory",
            uploadBudgetClass = null,
        ),
    ),
)

private const val TEXT_GPU_ARTIFACT_REGISTRY_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.TextGPUArtifactRegistry.v1"
private const val TEXT_GPU_ARTIFACT_DESCRIPTOR_HASH_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.TextGPUArtifactDescriptorHash.v1"
private const val TEXT_GPU_ARTIFACT_UNREGISTERED_HANDOFF_DIAGNOSTIC =
    "text.gpu.artifact-unregistered"
private const val TEXT_GPU_ARTIFACT_UNREGISTERED_RENDERER_DIAGNOSTIC =
    "unsupported.text.artifact_unregistered"

private fun textGPUArtifactDescriptor(
    artifactName: String,
    keyPreimageFields: List<String>,
    lifetimeClass: String,
    invalidationFacts: List<String>,
    memoryBudgetClass: String,
    uploadBudgetClass: String?,
    supportedRoutes: List<String> = emptyList(),
): TextGPUArtifactDescriptor = TextGPUArtifactDescriptor(
    artifactName = artifactName,
    descriptorVersion = 1,
    ownerSubsystem = "pure-kotlin-text",
    keyPreimageFields = keyPreimageFields,
    lifetimeClass = lifetimeClass,
    invalidationFacts = invalidationFacts,
    memoryBudgetClass = memoryBudgetClass,
    uploadBudgetClass = uploadBudgetClass,
    supportedRoutes = supportedRoutes,
    missingDiagnostic = TEXT_GPU_ARTIFACT_UNREGISTERED_RENDERER_DIAGNOSTIC,
    productActivation = false,
)

private fun TextGPUArtifactDescriptor.textPayloadLeakageFields(fieldPrefix: String): List<TextPayloadField> =
    buildList {
        add(TextPayloadField("$fieldPrefix.artifactName", "String", artifactName))
        add(TextPayloadField("$fieldPrefix.descriptorVersion", "Int", descriptorVersion.toString()))
        add(TextPayloadField("$fieldPrefix.descriptorCompactHash", "String", descriptorCompactHash))
        add(TextPayloadField("$fieldPrefix.ownerSubsystem", "String", ownerSubsystem))
        add(TextPayloadField("$fieldPrefix.keyPreimageFields", "List<String>"))
        keyPreimageFields.forEachIndexed { index, field ->
            add(TextPayloadField("$fieldPrefix.keyPreimageFields[$index]", "String", field))
        }
        add(TextPayloadField("$fieldPrefix.lifetimeClass", "String", lifetimeClass))
        add(TextPayloadField("$fieldPrefix.invalidationFacts", "List<String>"))
        invalidationFacts.forEachIndexed { index, fact ->
            add(TextPayloadField("$fieldPrefix.invalidationFacts[$index]", "String", fact))
        }
        add(TextPayloadField("$fieldPrefix.memoryBudgetClass", "String", memoryBudgetClass))
        add(TextPayloadField("$fieldPrefix.uploadBudgetClass", "String?", uploadBudgetClass))
        add(TextPayloadField("$fieldPrefix.supportedRoutes", "List<String>"))
        supportedRoutes.forEachIndexed { index, route ->
            add(TextPayloadField("$fieldPrefix.supportedRoutes[$index]", "String", route))
        }
        add(TextPayloadField("$fieldPrefix.missingDiagnostic", "String", missingDiagnostic))
        add(TextPayloadField("$fieldPrefix.productActivation", "Boolean", productActivation.toString()))
    }

private fun textGPUArtifactDescriptorCompactHash(descriptor: TextGPUArtifactDescriptor): String =
    "fnv1a64:${textArtifactFnv1a64Hex(descriptor.toDescriptorHashPreimage())}"

private fun TextGPUArtifactDescriptor.toDescriptorHashPreimage(): String = buildString {
    append("{")
    appendTextArtifactJsonField("schema", TEXT_GPU_ARTIFACT_DESCRIPTOR_HASH_SCHEMA, comma = true)
    appendTextArtifactJsonField("artifactName", artifactName, comma = true)
    appendTextArtifactJsonField("descriptorVersion", descriptorVersion, comma = true)
    appendTextArtifactJsonField("ownerSubsystem", ownerSubsystem, comma = true)
    appendTextArtifactJsonField("keyPreimageFields", keyPreimageFields, comma = true)
    appendTextArtifactJsonField("lifetimeClass", lifetimeClass, comma = true)
    appendTextArtifactJsonField("invalidationFacts", invalidationFacts, comma = true)
    appendTextArtifactJsonField("memoryBudgetClass", memoryBudgetClass, comma = true)
    appendTextArtifactJsonNullableField("uploadBudgetClass", uploadBudgetClass, comma = true)
    appendTextArtifactJsonField("supportedRoutes", supportedRoutes, comma = true)
    appendTextArtifactJsonField("missingDiagnostic", missingDiagnostic, comma = true)
    appendTextArtifactJsonField("productActivation", productActivation, comma = false)
    append("}")
}

private fun textArtifactFnv1a64Hex(value: String): String {
    var hash = FNV1A64_OFFSET_BASIS
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        hash = hash xor (byte.toLong() and 0xFFL)
        hash *= FNV1A64_PRIME
    }
    return java.lang.Long.toUnsignedString(hash, 16).padStart(16, '0')
}

private const val FNV1A64_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV1A64_PRIME = 0x100000001b3L

private fun StringBuilder.appendTextArtifactJsonField(
    name: String,
    value: String,
    comma: Boolean,
) {
    append(textArtifactJsonString(name))
    append(":")
    append(textArtifactJsonString(value))
    if (comma) append(",")
}

private fun StringBuilder.appendTextArtifactJsonField(
    name: String,
    value: Int,
    comma: Boolean,
) {
    append(textArtifactJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendTextArtifactJsonField(
    name: String,
    value: Boolean,
    comma: Boolean,
) {
    append(textArtifactJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendTextArtifactJsonField(
    name: String,
    value: List<String>,
    comma: Boolean,
) {
    append(textArtifactJsonString(name))
    append(":")
    append(value.joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> textArtifactJsonString(entry) })
    if (comma) append(",")
}

private fun StringBuilder.appendTextArtifactJsonNullableField(
    name: String,
    value: String?,
    comma: Boolean,
) {
    append(textArtifactJsonString(name))
    append(":")
    append(value?.let(::textArtifactJsonString) ?: "null")
    if (comma) append(",")
}

private fun textArtifactJsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}
