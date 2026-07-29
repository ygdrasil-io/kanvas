package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.FontTextRefusalCodes

/**
 * Canonical stable refusal-code authority for the prepared GPU text pipeline.
 *
 * The one code consumed by font core aliases its neutral font-domain
 * authority. Every renderer-only refusal remains owned by this GPU API layer.
 */
object GPUTextRefusalCodes {
    const val TYPEFACE_MISSING: String = "unsupported.text.typeface_missing"
    const val TYPEFACE_UNSUPPORTED: String = "unsupported.text.typeface_unsupported"
    const val FONT_IDENTITY_UNSTABLE: String = "unsupported.text.font_identity_unstable"
    const val FONT_BYTES_MALFORMED: String = "unsupported.text.font_bytes_malformed"
    const val GLYPH_ID_INVALID: String = "unsupported.text.glyph_id_invalid"
    const val NOTDEF_UNAVAILABLE: String = "unsupported.text.notdef_unavailable"
    const val POSITION_COUNT_MISMATCH: String = "unsupported.text.position_count_mismatch"
    const val POSITION_NONFINITE: String = "unsupported.text.position_nonfinite"
    const val FONT_SIZE_INVALID: String = "unsupported.text.font_size_invalid"
    const val ORIGIN_NONFINITE: String = "unsupported.text.origin_nonfinite"
    const val REPRESENTATION_MISSING: String = "unsupported.text.representation_missing"
    const val BITMAP_CBDT_CBLC_UNSUPPORTED: String =
        "unsupported.text.bitmap_cbdt_cblc_unsupported"
    const val BITMAP_SBIX_UNSUPPORTED: String = "unsupported.text.bitmap_sbix_unsupported"
    const val COLRV1_UNPROVED: String = "unsupported.text.colrv1_unproved"
    const val TRANSFORM_NONFINITE: String = "unsupported.text.transform_nonfinite"
    const val TRANSFORM_SINGULAR: String = "unsupported.text.transform_singular"
    const val TRANSFORM_PERSPECTIVE: String = "unsupported.text.transform_perspective"
    const val PAINT_STYLE_UNSUPPORTED: String = "unsupported.text.paint_style_unsupported"
    const val MATERIAL_UNSUPPORTED: String = "unsupported.text.material_unsupported"
    const val BLEND_UNSUPPORTED: String = "unsupported.text.blend_unsupported"
    const val IMAGE_FILTER_REQUIRES_COMPOSITE: String =
        "unsupported.text.image_filter_requires_composite"
    const val MASK_FILTER_UNSUPPORTED: String = "unsupported.text.mask_filter_unsupported"
    const val PATH_EFFECT_UNSUPPORTED: String = "unsupported.text.path_effect_unsupported"

    const val PAYLOAD_NONDUMPABLE: String = "unsupported.text.payload_nondumpable"
    const val SK_TYPE_LEAKED: String = "unsupported.text.sk_type_leaked"
    const val ARTIFACT_UNREGISTERED: String = FontTextRefusalCodes.ARTIFACT_UNREGISTERED
    const val ARTIFACT_MISSING: String = "unsupported.text.artifact_missing"
    const val ARTIFACT_KEY_NONDETERMINISTIC: String =
        "unsupported.text.artifact_key_nondeterministic"
    const val ARTIFACT_GENERATION_STALE: String = "unsupported.text.artifact_generation_stale"
    const val ARTIFACT_BUDGET_EXCEEDED: String = "unsupported.text.artifact_budget_exceeded"
    const val UPLOAD_PLAN_MISSING: String = "unsupported.text.upload_plan_missing"
    const val UPLOAD_BUDGET_EXCEEDED: String = "unsupported.text.upload_budget_exceeded"
    const val UPLOAD_FAILED: String = "unsupported.text.upload_failed"
    const val ATLAS_DESCRIPTOR_UNACCEPTED: String =
        "unsupported.text.atlas_descriptor_unaccepted"
    const val ATLAS_PAGE_UNAVAILABLE: String = "unsupported.text.atlas_page_unavailable"
    const val ATLAS_ENTRY_MISSING: String = "unsupported.text.atlas_entry_missing"
    const val ATLAS_GENERATION_STALE: String = "unsupported.text.atlas_generation_stale"
    const val A8_ATLAS_ROUTE_UNAVAILABLE: String =
        "unsupported.text.a8_atlas_route_unavailable"
    const val SDF_ROUTE_UNAVAILABLE: String = "unsupported.text.sdf_route_unavailable"
    const val SDF_PARAMS_MISSING: String = "unsupported.text.sdf_params_missing"
    const val SDF_TRANSFORM_UNSUPPORTED: String = "unsupported.text.sdf_transform_unsupported"
    const val OUTLINE_ROUTE_UNAVAILABLE: String = "unsupported.text.outline_route_unavailable"
    const val COLOR_PLAN_UNSUPPORTED: String = "unsupported.text.color_plan_unsupported"
    const val COLOR_COMPOSITE_UNSUPPORTED: String =
        "unsupported.text.color_composite_unsupported"
    const val COLOR_FONT_FORMAT_UNAVAILABLE: String =
        "unsupported.text.color_font.format_unavailable"
    const val COLOR_FONT_LAYER_COUNT_EXCEEDED: String =
        "unsupported.text.color_font.layer_count_exceeded"
    const val BITMAP_ROUTE_UNSUPPORTED: String = "unsupported.text.bitmap_route_unsupported"
    const val SVG_PLAN_UNSUPPORTED: String = "unsupported.text.svg_plan_unsupported"
    const val EMOJI_COLOR_GLYPH_UNAVAILABLE: String =
        "dependency.text.emoji_color_glyph_unavailable"
    const val LCD_FUTURE_RESEARCH: String = "unsupported.text.lcd_future_research"
    const val INSTANCE_BUFFER_BUDGET_EXCEEDED: String =
        "unsupported.text.instance_buffer_budget_exceeded"
    const val BINDING_LAYOUT_UNAVAILABLE: String = "unsupported.text.binding_layout_unavailable"
    const val DESTINATION_READ_UNACCEPTED: String =
        "unsupported.text.destination_read_unaccepted"
    const val CLIP_ROUTE_UNACCEPTED: String = "unsupported.text.clip_route_unaccepted"
    const val CPU_RENDERED_TEXTURE_FORBIDDEN: String =
        "unsupported.text.cpu_rendered_texture_forbidden"
    const val SUBPIXEL_PIXEL_GEOMETRY: String = "unsupported.text.subpixel_pixel_geometry"
    const val SUBPIXEL_TARGET_FORMAT: String = "unsupported.text.subpixel_target_format"
    const val FALLBACK_EXHAUSTED: String = "unsupported.text.fallback_exhausted"
    const val EVICTION_BEFORE_DEPENDENT_DRAW: String =
        "unsupported.text.eviction_before_dependent_draw"
    const val INSTANCE_UPLOAD_AFTER_DRAW: String =
        "unsupported.text.instance_upload_after_draw"
    const val DRAW_RUN_ROUTE_UNAVAILABLE: String =
        "unsupported.text.draw_run_route_unavailable"
    const val OUTLINE_NO_TYPEFACE: String = "unsupported.text.outline.no_typeface"
    const val OUTLINE_NO_SCALER: String = "unsupported.text.outline.no_scaler"
    const val COLOR_NO_TYPEFACE: String = "unsupported.text.color.no_typeface"
    const val COLOR_NO_SCALER: String = "unsupported.text.color.no_scaler"
    const val COLOR_BITMAP_GLYPH: String = "unsupported.text.color_bitmap_glyph"

    const val ATLAS_PAGE_BUDGET_EXCEEDED: String =
        "unsupported.text.atlas_page_budget_exceeded"
    const val ATLAS_PAGE_BYTES_EXCEEDED: String =
        "unsupported.text.atlas_page_bytes_exceeded"
    const val ATLAS_TOTAL_BYTES_EXCEEDED: String =
        "unsupported.text.atlas_total_bytes_exceeded"
    const val GLYPH_BUDGET_EXCEEDED: String = "unsupported.text.glyph_budget_exceeded"
    const val SUBRUN_BUDGET_EXCEEDED: String = "unsupported.text.subrun_budget_exceeded"
    const val INSTANCE_BYTES_EXCEEDED: String = "unsupported.text.instance_bytes_exceeded"
    const val MASK_GENERATION_FAILED: String = "unsupported.text.mask_generation_failed"
    const val ABI_UNAVAILABLE: String = "unsupported.text.abi_unavailable"
    const val OWNERSHIP_INVALID: String = "unsupported.text.ownership_invalid"
    const val RASTERIZATION_FAILED: String = "unsupported.text.rasterization_failed"
    const val PACKING_FAILED: String = "unsupported.text.packing_failed"
}

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
            rendererDiagnostic = GPUTextRefusalCodes.SDF_ROUTE_UNAVAILABLE,
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
            rendererDiagnostic = GPUTextRefusalCodes.OUTLINE_ROUTE_UNAVAILABLE,
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
            rendererDiagnostic = GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED,
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
            rendererDiagnostic = GPUTextRefusalCodes.BITMAP_ROUTE_UNSUPPORTED,
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
            rendererDiagnostic = GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
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
            rendererDiagnostic = GPUTextRefusalCodes.ARTIFACT_UNREGISTERED,
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
            rendererDiagnostic = GPUTextRefusalCodes.UPLOAD_PLAN_MISSING,
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
            rendererDiagnostic = GPUTextRefusalCodes.ARTIFACT_GENERATION_STALE,
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
            rendererDiagnostic = GPUTextRefusalCodes.SDF_TRANSFORM_UNSUPPORTED,
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
            rendererDiagnostic = GPUTextRefusalCodes.CPU_RENDERED_TEXTURE_FORBIDDEN,
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
