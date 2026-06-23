package org.graphiks.kanvas.glyph.gpu

enum class GPUTextRouteBlocker(
    val ownerLabel: String,
    val classification: String,
) {
    MISSING_RENDERER_CAPABILITY(
        ownerLabel = "gpu-renderer-route",
        classification = "DependencyGated",
    ),
    ARTIFACT_REGISTRY(
        ownerLabel = "text-artifact-registry",
        classification = "GPU-gated",
    ),
    ATLAS_DESCRIPTOR(
        ownerLabel = "text-atlas-descriptor",
        classification = "GPU-gated",
    ),
    ATLAS_ENTRY(
        ownerLabel = "text-atlas-entry",
        classification = "GPU-gated",
    ),
    UPLOAD_PLAN(
        ownerLabel = "text-upload-plan",
        classification = "GPU-gated",
    ),
    UPLOAD_BUDGET(
        ownerLabel = "text-upload-budget",
        classification = "GPU-gated",
    ),
    ATLAS_PAGE(
        ownerLabel = "text-atlas-page",
        classification = "GPU-gated",
    ),
    STALE_GENERATION(
        ownerLabel = "text-artifact-generation",
        classification = "GPU-gated",
    ),
    BINDING_LAYOUT(
        ownerLabel = "text-binding-layout",
        classification = "GPU-gated",
    ),
    EVICTION_BARRIER(
        ownerLabel = "text-atlas-eviction-order",
        classification = "GPU-gated",
    ),
    INSTANCE_UPLOAD_ORDER(
        ownerLabel = "text-instance-upload-order",
        classification = "GPU-gated",
    ),
    CPU_RENDERED_TEXTURE(
        ownerLabel = "forbidden-compatibility-path",
        classification = "expected-unsupported",
    ),
}

class GPUTextRouteRefusal(
    val refusalId: String,
    val commandId: String,
    val textRange: String?,
    val glyphRange: String?,
    val artifactType: String,
    val artifactKeyHash: String?,
    val attemptedRoute: String,
    val blocker: GPUTextRouteBlocker,
    val handoffDiagnostic: String,
    val rendererDiagnostic: String,
    legacyGates: List<String>,
    val claimPromotionAllowed: Boolean = false,
    val classification: String = blocker.classification,
) {
    val legacyGates: List<String> = legacyGates.toList()

    init {
        require(refusalId.isNotBlank()) { "refusalId must not be blank." }
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(textRange == null || textRange.isNotBlank()) { "textRange must not be blank when present." }
        require(glyphRange == null || glyphRange.isNotBlank()) { "glyphRange must not be blank when present." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(artifactKeyHash == null || artifactKeyHash.isNotBlank()) {
            "artifactKeyHash must not be blank when present."
        }
        require(attemptedRoute.isNotBlank()) { "attemptedRoute must not be blank." }
        require(handoffDiagnostic.startsWith("text.gpu.")) {
            "handoffDiagnostic must use the text.gpu namespace."
        }
        require(rendererDiagnostic.startsWith("unsupported.text.")) {
            "rendererDiagnostic must use the unsupported.text namespace."
        }
        require(legacyGates.all { gate -> gate.isNotBlank() }) {
            "legacyGates must not contain blank entries."
        }
        require(!claimPromotionAllowed) {
            "Unsupported text route refusals cannot promote renderer claims."
        }
        require(classification == blocker.classification) {
            "classification must match blocker classification."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendGPUTextRouteRefusalJsonField("refusalId", refusalId, comma = true)
        appendGPUTextRouteRefusalJsonField("commandId", commandId, comma = true)
        appendGPUTextRouteRefusalJsonNullableField("textRange", textRange, comma = true)
        appendGPUTextRouteRefusalJsonNullableField("glyphRange", glyphRange, comma = true)
        appendGPUTextRouteRefusalJsonField("artifactType", artifactType, comma = true)
        appendGPUTextRouteRefusalJsonNullableField("artifactKeyHash", artifactKeyHash, comma = true)
        appendGPUTextRouteRefusalJsonField("attemptedRoute", attemptedRoute, comma = true)
        appendGPUTextRouteRefusalJsonField("blocker", blocker.name, comma = true)
        appendGPUTextRouteRefusalJsonField("blockerOwner", blocker.ownerLabel, comma = true)
        appendGPUTextRouteRefusalJsonField("classification", classification, comma = true)
        appendGPUTextRouteRefusalJsonField("handoffDiagnostic", handoffDiagnostic, comma = true)
        appendGPUTextRouteRefusalJsonField("rendererDiagnostic", rendererDiagnostic, comma = true)
        appendGPUTextRouteRefusalJsonField("legacyGates", legacyGates, comma = true)
        appendGPUTextRouteRefusalJsonField("claimPromotionAllowed", claimPromotionAllowed, comma = false)
        append("}")
    }
}

data class GPUTextRouteDiagnosticMapping(
    val artifactType: String,
    val attemptedRoute: String,
    val handoffDiagnostic: String,
    val rendererDiagnostic: String,
) {
    init {
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(attemptedRoute.isNotBlank()) { "attemptedRoute must not be blank." }
        require(handoffDiagnostic.startsWith("text.gpu.")) {
            "handoffDiagnostic must use the text.gpu namespace."
        }
        require(rendererDiagnostic.startsWith("unsupported.text.")) {
            "rendererDiagnostic must use the unsupported.text namespace."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendGPUTextRouteRefusalJsonField("artifactType", artifactType, comma = true)
        appendGPUTextRouteRefusalJsonField("attemptedRoute", attemptedRoute, comma = true)
        appendGPUTextRouteRefusalJsonField("handoffDiagnostic", handoffDiagnostic, comma = true)
        appendGPUTextRouteRefusalJsonField("rendererDiagnostic", rendererDiagnostic, comma = false)
        append("}")
    }
}

data class GPUTextRouteClassificationRow(
    val blocker: GPUTextRouteBlocker,
    val classification: String,
    val count: Int,
) {
    init {
        require(classification == blocker.classification) {
            "classification must match blocker classification."
        }
        require(count > 0) { "count must be positive." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendGPUTextRouteRefusalJsonField("blocker", blocker.name, comma = true)
        appendGPUTextRouteRefusalJsonField("classification", classification, comma = true)
        appendGPUTextRouteRefusalJsonField("count", count, comma = false)
        append("}")
    }
}

class GPUTextRouteRefusalReport(
    val fixtureName: String,
    refusals: List<GPUTextRouteRefusal>,
) {
    val refusals: List<GPUTextRouteRefusal> = refusals.map { refusal -> refusal.snapshot() }
    val diagnosticMappings: List<GPUTextRouteDiagnosticMapping> =
        this.refusals.map { refusal ->
            GPUTextRouteDiagnosticMapping(
                artifactType = refusal.artifactType,
                attemptedRoute = refusal.attemptedRoute,
                handoffDiagnostic = refusal.handoffDiagnostic,
                rendererDiagnostic = refusal.rendererDiagnostic,
            )
        }
    val classificationRows: List<GPUTextRouteClassificationRow> =
        GPUTextRouteBlocker.entries.mapNotNull { blocker ->
            val count = this.refusals.count { refusal -> refusal.blocker == blocker }
            if (count == 0) {
                null
            } else {
                GPUTextRouteClassificationRow(
                    blocker = blocker,
                    classification = blocker.classification,
                    count = count,
                )
            }
        }

    init {
        require(fixtureName.isNotBlank()) { "fixtureName must not be blank." }
        require(this.refusals.map { refusal -> refusal.refusalId }.distinct().size == this.refusals.size) {
            "refusals must have unique refusalId values."
        }
        require(this.refusals.all { refusal -> !refusal.claimPromotionAllowed }) {
            "route refusal report cannot contain claim-promoting rows."
        }
    }

    fun refusal(refusalId: String): GPUTextRouteRefusal =
        refusals.single { refusal -> refusal.refusalId == refusalId }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendGPUTextRouteRefusalJsonField("schema", GPU_TEXT_ROUTE_REFUSAL_REPORT_SCHEMA, comma = true)
        appendGPUTextRouteRefusalJsonField("fixtureName", fixtureName, comma = true)
        append("\"refusals\":")
        append(refusals.joinToString(separator = ",", prefix = "[", postfix = "]") { refusal ->
            refusal.toCanonicalJson()
        })
        append(",")
        append("\"diagnosticMappings\":")
        append(diagnosticMappings.joinToString(separator = ",", prefix = "[", postfix = "]") { mapping ->
            mapping.toCanonicalJson()
        })
        append(",")
        append("\"classificationRows\":")
        append(classificationRows.joinToString(separator = ",", prefix = "[", postfix = "]") { row ->
            row.toCanonicalJson()
        })
        append("}")
    }
}

fun defaultGPUTextRouteRefusalReport(): GPUTextRouteRefusalReport = GPUTextRouteRefusalReport(
    fixtureName = "gpu-text-route-refusals.json",
    refusals = listOf(
        routeRefusal(
            refusalId = "sdf-route-unavailable",
            artifactType = "SDFGlyphAtlasArtifact",
            attemptedRoute = "AtlasSDFSample",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = TEXT_GPU_CAPABILITY_MISSING,
            rendererDiagnostic = "unsupported.text.sdf_route_unavailable",
            legacyGates = listOf("dftext"),
            artifactKeyHash = "sha256:sdf-route-fixture",
            glyphRange = "0..1",
        ),
        routeRefusal(
            refusalId = "outline-route-unavailable",
            artifactType = "OutlineGlyphPlan",
            attemptedRoute = "OutlinePathRoute",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = TEXT_GPU_CAPABILITY_MISSING,
            rendererDiagnostic = "unsupported.text.outline_route_unavailable",
            legacyGates = listOf("dftext"),
            artifactKeyHash = "sha256:outline-route-fixture",
            glyphRange = "2..2",
        ),
        routeRefusal(
            refusalId = "color-glyph-route-unavailable",
            artifactType = "ColorGlyphPlan",
            attemptedRoute = "ColorGlyphCompositeRoute",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = "text.gpu.color-plan-unsupported",
            rendererDiagnostic = "unsupported.text.color_plan_unsupported",
            legacyGates = listOf("coloremoji_blendmodes"),
            artifactKeyHash = "sha256:color-glyph-route-fixture",
            glyphRange = "3..3",
        ),
        routeRefusal(
            refusalId = "bitmap-glyph-route-unavailable",
            artifactType = "BitmapGlyphPlan",
            attemptedRoute = "BitmapGlyphTextureRoute",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = TEXT_GPU_CAPABILITY_MISSING,
            rendererDiagnostic = "unsupported.text.bitmap_route_unsupported",
            legacyGates = listOf("scaledemoji_rendering"),
            artifactKeyHash = "sha256:bitmap-glyph-route-fixture",
            glyphRange = "4..4",
        ),
        routeRefusal(
            refusalId = "svg-glyph-route-unavailable",
            artifactType = "SVGGlyphPlan",
            attemptedRoute = "SVGGlyphVectorRoute",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = "text.gpu.SVG-plan-unsupported",
            rendererDiagnostic = "unsupported.text.svg_plan_unsupported",
            legacyGates = listOf("scaledemoji_rendering"),
            artifactKeyHash = "sha256:svg-glyph-route-fixture",
            glyphRange = "5..5",
        ),
        routeRefusal(
            refusalId = "artifact-unregistered",
            artifactType = "UnregisteredTextArtifact",
            attemptedRoute = "ArtifactRegistryLookup",
            blocker = GPUTextRouteBlocker.ARTIFACT_REGISTRY,
            handoffDiagnostic = "text.gpu.artifact-unregistered",
            rendererDiagnostic = "unsupported.text.artifact_unregistered",
            legacyGates = listOf("dftext", "scaledemoji_rendering", "coloremoji_blendmodes"),
            artifactKeyHash = "sha256:unregistered-artifact-fixture",
            glyphRange = "6..6",
        ),
        routeRefusal(
            refusalId = "upload-plan-missing",
            artifactType = "GlyphUploadPlan",
            attemptedRoute = "UploadBeforeSample",
            blocker = GPUTextRouteBlocker.UPLOAD_PLAN,
            handoffDiagnostic = "text.gpu.upload-plan-missing",
            rendererDiagnostic = "unsupported.text.upload_plan_missing",
            legacyGates = listOf("dftext"),
            artifactKeyHash = "sha256:missing-upload-plan-fixture",
            glyphRange = "7..7",
        ),
        routeRefusal(
            refusalId = "atlas-generation-stale",
            artifactType = "GlyphAtlasArtifact",
            attemptedRoute = "AtlasGenerationValidation",
            blocker = GPUTextRouteBlocker.STALE_GENERATION,
            handoffDiagnostic = "text.gpu.atlas-generation-stale",
            rendererDiagnostic = "unsupported.text.artifact_generation_stale",
            legacyGates = listOf("dftext"),
            artifactKeyHash = "sha256:stale-generation-fixture",
            glyphRange = "8..8",
        ),
        routeRefusal(
            refusalId = "transform-unsupported",
            artifactType = "SDFGlyphAtlasArtifact",
            attemptedRoute = "AtlasSDFSample",
            blocker = GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY,
            handoffDiagnostic = "text.gpu.transform-unsupported",
            rendererDiagnostic = "unsupported.text.sdf_transform_unsupported",
            legacyGates = listOf("dftext"),
            artifactKeyHash = "sha256:sdf-transform-fixture",
            glyphRange = "9..9",
        ),
        routeRefusal(
            refusalId = "cpu-rendered-texture-forbidden",
            artifactType = "CPURenderedTextTexture",
            attemptedRoute = "ForbiddenFullTextTexture",
            blocker = GPUTextRouteBlocker.CPU_RENDERED_TEXTURE,
            handoffDiagnostic = "text.gpu.CPU-rendered-texture-forbidden",
            rendererDiagnostic = "unsupported.text.cpu_rendered_texture_forbidden",
            legacyGates = listOf("dftext", "scaledemoji_rendering", "coloremoji_blendmodes"),
            artifactKeyHash = "sha256:cpu-rendered-texture-forbidden-fixture",
            glyphRange = "10..10",
        ),
    ),
)

private const val GPU_TEXT_ROUTE_REFUSAL_REPORT_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.GPUTextRouteRefusalReport.v1"
private const val TEXT_GPU_CAPABILITY_MISSING = "text.gpu.capability-missing"

private fun routeRefusal(
    refusalId: String,
    artifactType: String,
    attemptedRoute: String,
    blocker: GPUTextRouteBlocker,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
    legacyGates: List<String>,
    artifactKeyHash: String,
    glyphRange: String,
): GPUTextRouteRefusal = GPUTextRouteRefusal(
    refusalId = refusalId,
    commandId = "draw-text-route-refusal-fixture",
    textRange = "0..16",
    glyphRange = glyphRange,
    artifactType = artifactType,
    artifactKeyHash = artifactKeyHash,
    attemptedRoute = attemptedRoute,
    blocker = blocker,
    handoffDiagnostic = handoffDiagnostic,
    rendererDiagnostic = rendererDiagnostic,
    legacyGates = legacyGates,
)

private fun GPUTextRouteRefusal.snapshot(): GPUTextRouteRefusal = GPUTextRouteRefusal(
    refusalId = refusalId,
    commandId = commandId,
    textRange = textRange,
    glyphRange = glyphRange,
    artifactType = artifactType,
    artifactKeyHash = artifactKeyHash,
    attemptedRoute = attemptedRoute,
    blocker = blocker,
    handoffDiagnostic = handoffDiagnostic,
    rendererDiagnostic = rendererDiagnostic,
    legacyGates = legacyGates,
    claimPromotionAllowed = claimPromotionAllowed,
    classification = classification,
)

private fun StringBuilder.appendGPUTextRouteRefusalJsonField(
    name: String,
    value: String,
    comma: Boolean,
) {
    append(gpuTextRouteRefusalJsonString(name))
    append(":")
    append(gpuTextRouteRefusalJsonString(value))
    if (comma) append(",")
}

private fun StringBuilder.appendGPUTextRouteRefusalJsonField(
    name: String,
    value: Int,
    comma: Boolean,
) {
    append(gpuTextRouteRefusalJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendGPUTextRouteRefusalJsonField(
    name: String,
    value: Boolean,
    comma: Boolean,
) {
    append(gpuTextRouteRefusalJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendGPUTextRouteRefusalJsonField(
    name: String,
    value: List<String>,
    comma: Boolean,
) {
    append(gpuTextRouteRefusalJsonString(name))
    append(":")
    append(value.joinToString(separator = ",", prefix = "[", postfix = "]") { entry ->
        gpuTextRouteRefusalJsonString(entry)
    })
    if (comma) append(",")
}

private fun StringBuilder.appendGPUTextRouteRefusalJsonNullableField(
    name: String,
    value: String?,
    comma: Boolean,
) {
    append(gpuTextRouteRefusalJsonString(name))
    append(":")
    append(value?.let(::gpuTextRouteRefusalJsonString) ?: "null")
    if (comma) append(",")
}

private fun gpuTextRouteRefusalJsonString(value: String): String = buildString {
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
