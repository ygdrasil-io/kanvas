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

    fun toCanonicalJson(): String = buildString {
        append("{")
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
    val descriptors: List<TextGPUArtifactDescriptor> = descriptors.toList()

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
