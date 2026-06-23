package org.graphiks.kanvas.glyph.gpu

import kotlin.uuid.Uuid

data class GPUTextIntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be >= left." }
        require(bottom >= top) { "bottom must be >= top." }
    }

    fun toCanonicalJson(): String =
        """{"left":$left,"top":$top,"right":$right,"bottom":$bottom}"""
}

data class GPUTextFloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(right >= left) { "right must be >= left." }
        require(bottom >= top) { "bottom must be >= top." }
    }

    fun toCanonicalJson(): String =
        """{"left":${left.canonicalFloat()},"top":${top.canonicalFloat()},"right":${right.canonicalFloat()},"bottom":${bottom.canonicalFloat()}}"""
}

data class GPUTextA8BindingRef(
    val bindingIndex: Int,
    val bindingName: String,
    val bindingKind: String,
) {
    init {
        require(bindingIndex >= 0) { "bindingIndex must be non-negative." }
        require(bindingName.isNotBlank()) { "bindingName must not be blank." }
        require(bindingKind.isNotBlank()) { "bindingKind must not be blank." }
    }

    fun toCanonicalJson(): String =
        """{"bindingIndex":$bindingIndex,"bindingName":${bindingName.quoted()},"bindingKind":${bindingKind.quoted()}}"""
}

data class GPUTextA8BindingLayout(
    val layoutId: String,
    val strideBytes: Int,
    val alignmentBytes: Int,
    val bindingHash: String,
    val bindingRefs: List<GPUTextA8BindingRef>,
) {
    init {
        require(layoutId.isNotBlank()) { "layoutId must not be blank." }
        require(strideBytes > 0) { "strideBytes must be positive." }
        require(alignmentBytes > 0) { "alignmentBytes must be positive." }
        require(bindingHash.isNotBlank()) { "bindingHash must not be blank." }
        require(bindingRefs.all { ref -> ref.bindingName.isNotBlank() }) { "bindingRefs must not contain blank names." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendJsonField("layoutId", layoutId, comma = true)
        appendJsonField("strideBytes", strideBytes, comma = true)
        appendJsonField("alignmentBytes", alignmentBytes, comma = true)
        appendJsonField("bindingHash", bindingHash, comma = true)
        append("\"bindingRefs\":")
        append(bindingRefs.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toCanonicalJson() })
        append("}")
    }
}

data class GPUTextA8AtlasPage(
    val pageId: String,
    val pageIndex: Int,
    val textureFormat: String,
    val width: Int,
    val height: Int,
    val rowStrideBytes: Int,
) {
    init {
        require(pageId.isNotBlank()) { "pageId must not be blank." }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(textureFormat.isNotBlank()) { "textureFormat must not be blank." }
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(rowStrideBytes >= width) { "rowStrideBytes must be >= width." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendJsonField("pageId", pageId, comma = true)
        appendJsonField("pageIndex", pageIndex, comma = true)
        appendJsonField("textureFormat", textureFormat, comma = true)
        appendJsonField("width", width, comma = true)
        appendJsonField("height", height, comma = true)
        appendJsonField("rowStrideBytes", rowStrideBytes, comma = false)
        append("}")
    }
}

data class GPUTextA8AtlasEntryRef(
    val glyphId: Int,
    val strikeKeyHash: String,
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
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(sourceMaskHash.isNotBlank()) { "sourceMaskHash must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendJsonField("glyphId", glyphId, comma = true)
        appendJsonField("strikeKeyHash", strikeKeyHash, comma = true)
        appendJsonField("pageIndex", pageIndex, comma = true)
        appendJsonField("atlasGeneration", atlasGeneration, comma = true)
        appendJsonRawField("atlasRect", atlasRect.toCanonicalJson(), comma = true)
        appendJsonRawField("sourceBounds", sourceBounds.toCanonicalJson(), comma = true)
        appendJsonRawField("uvRect", uvRect.toCanonicalJson(), comma = true)
        appendJsonField("sourceMaskHash", sourceMaskHash, comma = false)
        append("}")
    }
}

data class GPUTextA8RouteFixture(
    val planId: String,
    val commandId: String,
    val layoutResultId: GPUTextLayoutResultID?,
    val glyphRunId: GPUGlyphRunID?,
    val artifact: GPUTextArtifactReference,
    val uploadPlan: GPUTextUploadPlan?,
    val expectedAtlasGeneration: Int,
    val atlasPages: List<GPUTextA8AtlasPage>,
    val entryRefs: List<GPUTextA8AtlasEntryRef>,
    val bindingLayout: GPUTextA8BindingLayout,
    val uploadDependencyLabels: List<String>,
) {
    init {
        require(planId.isNotBlank()) { "planId must not be blank." }
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(layoutResultId != null || glyphRunId != null) {
            "A8 route fixture must reference a layout result ID or glyph run ID."
        }
        require(expectedAtlasGeneration >= 0) { "expectedAtlasGeneration must be non-negative." }
        require(uploadDependencyLabels.all { it.isNotBlank() }) { "uploadDependencyLabels must not contain blanks." }
    }
}

class GPUTextA8RoutePlan(
    val planId: String,
    val commandId: String,
    val layoutResultId: GPUTextLayoutResultID?,
    val glyphRunId: GPUGlyphRunID?,
    val artifact: GPUTextArtifactReference,
    atlasPages: List<GPUTextA8AtlasPage>,
    entryRefs: List<GPUTextA8AtlasEntryRef>,
    val bindingLayout: GPUTextA8BindingLayout,
    uploadDependencyLabels: List<String>,
    nonClaims: List<String>,
    val selectedRoute: String = "AtlasMaskSample",
    val renderStep: String = "A8TextMaskStep",
    val wgslModuleId: String = "text.a8-mask",
    val routePromotion: String = A8_ROUTE_NOT_PROMOTED,
    val productActivation: Boolean = false,
) {
    val atlasPages: List<GPUTextA8AtlasPage> = atlasPages.toList()
    val entryRefs: List<GPUTextA8AtlasEntryRef> = entryRefs.toList()
    val uploadDependencyLabels: List<String> = uploadDependencyLabels.toList()
    val nonClaims: List<String> = nonClaims.toList()

    init {
        require(planId.isNotBlank()) { "planId must not be blank." }
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(selectedRoute == "AtlasMaskSample") { "selectedRoute must stay AtlasMaskSample for this slice." }
        require(renderStep == "A8TextMaskStep") { "renderStep must stay A8TextMaskStep for this slice." }
        require(wgslModuleId.isNotBlank()) { "wgslModuleId must not be blank." }
        require(routePromotion == A8_ROUTE_NOT_PROMOTED) { "A8 route plan cannot promote support in this slice." }
        require(!productActivation) { "A8 route plan cannot activate product support in this slice." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendJsonField("schema", A8_ROUTE_PLAN_SCHEMA, comma = true)
        appendJsonField("planId", planId, comma = true)
        appendJsonField("commandId", commandId, comma = true)
        appendJsonNullableField("layoutResultId", layoutResultId?.value?.toString(), comma = true)
        appendJsonNullableField("glyphRunId", glyphRunId?.value?.toString(), comma = true)
        appendJsonField("selectedRoute", selectedRoute, comma = true)
        appendJsonField("renderStep", renderStep, comma = true)
        appendJsonField("wgslModuleId", wgslModuleId, comma = true)
        appendJsonRawField("artifact", artifact.toA8RoutePlanCanonicalJson(), comma = true)
        append("\"atlasPages\":")
        append(atlasPages.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toCanonicalJson() })
        append(",")
        append("\"entryRefs\":")
        append(entryRefs.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toCanonicalJson() })
        append(",")
        appendJsonRawField("bindingLayout", bindingLayout.toCanonicalJson(), comma = true)
        appendJsonStringListField("uploadDependencyLabels", uploadDependencyLabels, comma = true)
        appendJsonStringListField("nonClaims", nonClaims, comma = true)
        appendJsonField("routePromotion", routePromotion, comma = true)
        appendJsonField("productActivation", productActivation, comma = false)
        append("}")
    }
}

sealed interface GPUTextA8RoutePlanningResult {
    data class Accepted(val plan: GPUTextA8RoutePlan) : GPUTextA8RoutePlanningResult

    data class Refused(val refusal: GPUTextRouteRefusal) : GPUTextA8RoutePlanningResult
}

fun planGPUTextA8Route(fixture: GPUTextA8RouteFixture): GPUTextA8RoutePlanningResult {
    if (fixture.uploadPlan == null) {
        return GPUTextA8RoutePlanningResult.Refused(
            fixture.refusal(
                reasonId = "upload-plan-missing",
                blocker = GPUTextRouteBlocker.UPLOAD_PLAN,
                handoffDiagnostic = "text.gpu.upload-plan-missing",
                rendererDiagnostic = "unsupported.text.upload_plan_missing",
            ),
        )
    }
    if (fixture.atlasPages.isEmpty() || fixture.atlasPages.any { page -> page.textureFormat != "R8Unorm" }) {
        return GPUTextA8RoutePlanningResult.Refused(
            fixture.refusal(
                reasonId = "atlas-descriptor-unaccepted",
                blocker = GPUTextRouteBlocker.ATLAS_DESCRIPTOR,
                handoffDiagnostic = "text.gpu.atlas-descriptor-unaccepted",
                rendererDiagnostic = "unsupported.text.atlas_descriptor_unaccepted",
            ),
        )
    }
    if (fixture.entryRefs.isEmpty() || fixture.entryRefs.any { entry -> fixture.atlasPages.none { page -> page.pageIndex == entry.pageIndex } }) {
        return GPUTextA8RoutePlanningResult.Refused(
            fixture.refusal(
                reasonId = "atlas-entry-missing",
                blocker = GPUTextRouteBlocker.ATLAS_ENTRY,
                handoffDiagnostic = "text.gpu.atlas-entry-missing",
                rendererDiagnostic = "unsupported.text.atlas_entry_missing",
            ),
        )
    }
    if (fixture.entryRefs.any { entry -> entry.atlasGeneration != fixture.expectedAtlasGeneration }) {
        return GPUTextA8RoutePlanningResult.Refused(
            fixture.refusal(
                reasonId = "atlas-generation-stale",
                blocker = GPUTextRouteBlocker.STALE_GENERATION,
                handoffDiagnostic = "text.gpu.atlas-generation-stale",
                rendererDiagnostic = "unsupported.text.atlas_generation_stale",
            ),
        )
    }
    return GPUTextA8RoutePlanningResult.Accepted(
        GPUTextA8RoutePlan(
            planId = fixture.planId,
            commandId = fixture.commandId,
            layoutResultId = fixture.layoutResultId,
            glyphRunId = fixture.glyphRunId,
            artifact = fixture.artifact,
            atlasPages = fixture.atlasPages,
            entryRefs = fixture.entryRefs,
            bindingLayout = fixture.bindingLayout,
            uploadDependencyLabels = fixture.uploadDependencyLabels,
            nonClaims = DEFAULT_A8_ROUTE_NON_CLAIMS,
        ),
    )
}

fun defaultGPUTextA8RoutePlan(): GPUTextA8RoutePlan =
    when (val result = planGPUTextA8Route(defaultGPUTextA8RouteFixture())) {
        is GPUTextA8RoutePlanningResult.Accepted -> result.plan
        is GPUTextA8RoutePlanningResult.Refused -> error("Default A8 route fixture must be accepted: ${result.refusal.rendererDiagnostic}")
    }

fun defaultGPUTextA8RouteRefusalReport(): GPUTextRouteRefusalReport {
    val fixture = defaultGPUTextA8RouteFixture()
    val missingEntryFixture = fixture.copy(
        entryRefs = listOf(fixture.entryRefs.first().copy(pageIndex = fixture.atlasPages.single().pageIndex + 1)),
    )
    val staleGenerationFixture = fixture.copy(
        entryRefs = listOf(
            fixture.entryRefs.first().copy(atlasGeneration = fixture.expectedAtlasGeneration + 1),
            fixture.entryRefs[1],
        ),
    )
    return GPUTextRouteRefusalReport(
        fixtureName = "gpu-text-a8-route-refusals.json",
        refusals = listOf(
            (planGPUTextA8Route(missingEntryFixture) as GPUTextA8RoutePlanningResult.Refused).refusal,
            (planGPUTextA8Route(staleGenerationFixture) as GPUTextA8RoutePlanningResult.Refused).refusal,
        ),
    )
}

fun defaultGPUTextA8RouteFixture(): GPUTextA8RouteFixture =
    GPUTextA8RouteFixture(
        planId = "a8-text-route-plan-simple-latin",
        commandId = "draw-text-a8-001",
        layoutResultId = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441200")),
        glyphRunId = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441201")),
        artifact = GPUTextArtifactReference(
            artifactName = "GlyphAtlasArtifact",
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441202")),
            generation = GPUTextArtifactGeneration(3),
            contentFingerprint = "sha256:a8-atlas",
            sourceLabel = "fixture.a8-atlas",
            diagnostics = listOf("text.gpu.upload-plan-ready"),
        ),
        uploadPlan = GPUTextUploadPlan(
            artifactKey = GPUTextArtifactKey(
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441202")),
                generation = GPUTextArtifactGeneration(3),
                contentFingerprint = "sha256:a8-atlas",
            ),
            ranges = listOf(
                GPUTextUploadRange(
                    offset = 0,
                    size = 512,
                    label = "page-0",
                ),
            ),
            byteSize = 512,
        ),
        expectedAtlasGeneration = 3,
        atlasPages = listOf(
            GPUTextA8AtlasPage(
                pageId = "a8-page-0",
                pageIndex = 0,
                textureFormat = "R8Unorm",
                width = 128,
                height = 64,
                rowStrideBytes = 128,
            ),
        ),
        entryRefs = listOf(
            GPUTextA8AtlasEntryRef(
                glyphId = 42,
                strikeKeyHash = "fnv1a64:strike-key-A",
                pageIndex = 0,
                atlasGeneration = 3,
                atlasRect = GPUTextIntRect(left = 4, top = 8, right = 16, bottom = 24),
                sourceBounds = GPUTextIntRect(left = 0, top = -12, right = 12, bottom = 4),
                uvRect = GPUTextFloatRect(left = 0.03125f, top = 0.125f, right = 0.125f, bottom = 0.375f),
                sourceMaskHash = "sha256:glyph-mask-A",
            ),
            GPUTextA8AtlasEntryRef(
                glyphId = 43,
                strikeKeyHash = "fnv1a64:strike-key-B",
                pageIndex = 0,
                atlasGeneration = 3,
                atlasRect = GPUTextIntRect(left = 18, top = 8, right = 28, bottom = 24),
                sourceBounds = GPUTextIntRect(left = 1, top = -12, right = 11, bottom = 4),
                uvRect = GPUTextFloatRect(left = 0.140625f, top = 0.125f, right = 0.21875f, bottom = 0.375f),
                sourceMaskHash = "sha256:glyph-mask-B",
            ),
        ),
        bindingLayout = GPUTextA8BindingLayout(
            layoutId = "text.a8-mask.layout.v1",
            strideBytes = 32,
            alignmentBytes = 8,
            bindingHash = "fnv1a64:text-a8-layout",
            bindingRefs = listOf(
                GPUTextA8BindingRef(bindingIndex = 0, bindingName = "glyphAtlas", bindingKind = "sampledTexture"),
                GPUTextA8BindingRef(bindingIndex = 1, bindingName = "glyphSampler", bindingKind = "sampler"),
                GPUTextA8BindingRef(bindingIndex = 2, bindingName = "textParams", bindingKind = "uniformBuffer"),
            ),
        ),
        uploadDependencyLabels = listOf("upload-a8-page-0"),
    )

private fun GPUTextA8RouteFixture.refusal(
    reasonId: String,
    blocker: GPUTextRouteBlocker,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
): GPUTextRouteRefusal =
    GPUTextRouteRefusal(
        refusalId = "${planId}.$reasonId",
        commandId = commandId,
        textRange = null,
        glyphRange = "0..${entryRefs.lastIndex.coerceAtLeast(0)}",
        artifactType = artifact.artifactType,
        artifactKeyHash = artifact.artifactKeyHash,
        attemptedRoute = "AtlasMaskSample",
        blocker = blocker,
        handoffDiagnostic = handoffDiagnostic,
        rendererDiagnostic = rendererDiagnostic,
        legacyGates = listOf("dftext"),
    )

private fun GPUTextArtifactReference.toA8RoutePlanCanonicalJson(): String = buildString {
    append("{")
    appendJsonField("artifactName", artifactName, comma = true)
    appendJsonField("artifactType", artifactType, comma = true)
    appendJsonField("artifactId", artifactID.value.toString(), comma = true)
    appendJsonField("generation", generation.value, comma = true)
    appendJsonField("artifactKeyHash", artifactKeyHash, comma = true)
    appendJsonField("sourceLabel", sourceLabel, comma = false)
    append("}")
}

private fun Float.canonicalFloat(): String = toString()

private fun String.quoted(): String = "\"${escapeJson()}\""

private fun String.escapeJson(): String = buildString(length) {
    for (ch in this@escapeJson) {
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

private fun StringBuilder.appendJsonField(name: String, value: String, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(value.quoted())
    if (comma) append(",")
}

private fun StringBuilder.appendJsonField(name: String, value: Int, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendJsonNullableField(name: String, value: String?, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(value?.quoted() ?: "null")
    if (comma) append(",")
}

private fun StringBuilder.appendJsonRawField(name: String, value: String, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendJsonStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.quoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]") { value -> value.quoted() })
    if (comma) append(",")
}

private const val A8_ROUTE_PLAN_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextA8RoutePlan.v1"
private const val A8_ROUTE_NOT_PROMOTED = "not-promoted"

private val DEFAULT_A8_ROUTE_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
)
