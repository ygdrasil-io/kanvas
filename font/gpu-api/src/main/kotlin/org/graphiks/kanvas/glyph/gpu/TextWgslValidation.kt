package org.graphiks.kanvas.glyph.gpu

data class TextWgslEntryPoint(
    val name: String,
    val stage: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(stage == "vertex" || stage == "fragment" || stage == "compute") {
            "stage must be vertex, fragment, or compute."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("name", name, comma = true)
        appendTextWgslJsonField("stage", stage, comma = false)
        append("}")
    }
}

data class TextWgslReflectedBinding(
    val group: Int,
    val binding: Int,
    val name: String,
    val resourceKind: String,
    val access: String,
    val minBindingSize: Int?,
) {
    init {
        require(group >= 0) { "group must be non-negative." }
        require(binding >= 0) { "binding must be non-negative." }
        require(name.isNotBlank()) { "name must not be blank." }
        require(resourceKind.isNotBlank()) { "resourceKind must not be blank." }
        require(access.isNotBlank()) { "access must not be blank." }
        require(minBindingSize == null || minBindingSize >= 0) { "minBindingSize must be non-negative when present." }
    }

    val slotRef: String
        get() = "group=$group,binding=$binding"

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("group", group, comma = true)
        appendTextWgslJsonField("binding", binding, comma = true)
        appendTextWgslJsonField("name", name, comma = true)
        appendTextWgslJsonField("resourceKind", resourceKind, comma = true)
        appendTextWgslJsonField("access", access, comma = true)
        appendTextWgslNullableIntField("minBindingSize", minBindingSize, comma = false)
        append("}")
    }
}

data class TextWgslLayoutMember(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(type.isNotBlank()) { "type must not be blank." }
        require(offset >= 0) { "offset must be non-negative." }
        require(size > 0) { "size must be positive." }
        require(alignment > 0) { "alignment must be positive." }
        require(stride == null || stride > 0) { "stride must be positive when present." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("name", name, comma = true)
        appendTextWgslJsonField("type", type, comma = true)
        appendTextWgslJsonField("offset", offset, comma = true)
        appendTextWgslJsonField("size", size, comma = true)
        appendTextWgslJsonField("alignment", alignment, comma = true)
        appendTextWgslNullableIntField("stride", stride, comma = false)
        append("}")
    }
}

class TextWgslUniformLayout(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    members: List<TextWgslLayoutMember>,
) {
    val members: List<TextWgslLayoutMember> = members.toList()

    init {
        require(structName.isNotBlank()) { "structName must not be blank." }
        require(addressSpace.isNotBlank()) { "addressSpace must not be blank." }
        require(size > 0) { "size must be positive." }
        require(alignment > 0) { "alignment must be positive." }
        require(this.members.isNotEmpty()) { "members must not be empty." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("structName", structName, comma = true)
        appendTextWgslJsonField("addressSpace", addressSpace, comma = true)
        appendTextWgslJsonField("size", size, comma = true)
        appendTextWgslJsonField("alignment", alignment, comma = true)
        append("\"members\":")
        append(members.joinToString(separator = ",", prefix = "[", postfix = "]") { member ->
            member.toCanonicalJson()
        })
        append("}")
    }
}

data class TextWgslInstanceInputExpectation(
    val name: String,
    val format: String,
    val byteOffset: Int,
    val strideBytes: Int,
    val layoutHash: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(format.isNotBlank()) { "format must not be blank." }
        require(byteOffset >= 0) { "byteOffset must be non-negative." }
        require(strideBytes > 0) { "strideBytes must be positive." }
        require(layoutHash.isNotBlank()) { "layoutHash must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("name", name, comma = true)
        appendTextWgslJsonField("format", format, comma = true)
        appendTextWgslJsonField("byteOffset", byteOffset, comma = true)
        appendTextWgslJsonField("strideBytes", strideBytes, comma = true)
        appendTextWgslJsonField("layoutHash", layoutHash, comma = false)
        append("}")
    }
}

class TextWgslReflectionReport(
    val moduleId: String,
    val sourceId: String,
    val moduleHash: String,
    val wgsl4kSha: String,
    val renderStep: String,
    entryPoints: List<TextWgslEntryPoint>,
    reflectedBindings: List<TextWgslReflectedBinding>,
    uniformLayouts: List<TextWgslUniformLayout>,
    instanceInputExpectations: List<TextWgslInstanceInputExpectation>,
    diagnostics: List<String>,
    val parserValidation: String = "accepted",
    val wgslLanguage: String = "WGSL",
    val routePromotion: String = "not-promoted",
    val productActivation: Boolean = false,
) {
    val entryPoints: List<TextWgslEntryPoint> = entryPoints.toList()
    val reflectedBindings: List<TextWgslReflectedBinding> = reflectedBindings.toList()
    val uniformLayouts: List<TextWgslUniformLayout> = uniformLayouts.toList()
    val instanceInputExpectations: List<TextWgslInstanceInputExpectation> = instanceInputExpectations.toList()
    val diagnostics: List<String> = diagnostics.toList()

    init {
        require(moduleId.isNotBlank()) { "moduleId must not be blank." }
        require(sourceId.isNotBlank()) { "sourceId must not be blank." }
        require(moduleHash.isNotBlank()) { "moduleHash must not be blank." }
        require(wgsl4kSha.isNotBlank()) { "wgsl4kSha must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(this.entryPoints.isNotEmpty()) { "entryPoints must not be empty." }
        require(this.reflectedBindings.isNotEmpty()) { "reflectedBindings must not be empty." }
        require(this.uniformLayouts.isNotEmpty()) { "uniformLayouts must not be empty." }
        require(this.instanceInputExpectations.isNotEmpty()) { "instanceInputExpectations must not be empty." }
        require(this.diagnostics.all { diagnostic -> diagnostic.isNotBlank() }) {
            "diagnostics must not contain blanks."
        }
        require(parserValidation == "accepted") { "default text WGSL reflection must parse successfully." }
        require(wgslLanguage == "WGSL") { "text shader validation must use WGSL." }
        require(routePromotion == "not-promoted") { "KFONT-M11-009 cannot promote routes." }
        require(!productActivation) { "KFONT-M11-009 cannot activate product support." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("schema", TEXT_WGSL_REFLECTION_SCHEMA, comma = true)
        appendTextWgslStringListField("ownerTickets", listOf("KFONT-M11-009"), comma = true)
        appendTextWgslJsonField("classification", "GPU-gated", comma = true)
        appendTextWgslJsonField("moduleId", moduleId, comma = true)
        appendTextWgslJsonField("sourceId", sourceId, comma = true)
        appendTextWgslJsonField("moduleHash", moduleHash, comma = true)
        appendTextWgslJsonField("wgsl4kSha", wgsl4kSha, comma = true)
        appendTextWgslJsonField("wgslLanguage", wgslLanguage, comma = true)
        appendTextWgslJsonField("renderStep", renderStep, comma = true)
        appendTextWgslJsonField("parserValidation", parserValidation, comma = true)
        append("\"entryPoints\":")
        append(entryPoints.joinToString(separator = ",", prefix = "[", postfix = "]") { entryPoint ->
            entryPoint.toCanonicalJson()
        })
        append(",")
        append("\"reflectedBindings\":")
        append(reflectedBindings.joinToString(separator = ",", prefix = "[", postfix = "]") { binding ->
            binding.toCanonicalJson()
        })
        append(",")
        append("\"uniformLayouts\":")
        append(uniformLayouts.joinToString(separator = ",", prefix = "[", postfix = "]") { layout ->
            layout.toCanonicalJson()
        })
        append(",")
        append("\"instanceInputExpectations\":")
        append(instanceInputExpectations.joinToString(separator = ",", prefix = "[", postfix = "]") { input ->
            input.toCanonicalJson()
        })
        append(",")
        appendTextWgslStringListField("diagnostics", diagnostics, comma = true)
        appendTextWgslStringListField("nonClaims", TEXT_WGSL_NON_CLAIMS, comma = true)
        appendTextWgslJsonField("routePromotion", routePromotion, comma = true)
        appendTextWgslJsonField("productActivation", productActivation, comma = false)
        append("}\n")
    }
}

data class TextWgslKotlinPlanComparison(
    val comparisonId: String,
    val kotlinPlanRef: String,
    val reflectedRef: String,
    val expectedKind: String,
    val reflectedKind: String,
    val status: String,
) {
    init {
        require(comparisonId.isNotBlank()) { "comparisonId must not be blank." }
        require(kotlinPlanRef.isNotBlank()) { "kotlinPlanRef must not be blank." }
        require(reflectedRef.isNotBlank()) { "reflectedRef must not be blank." }
        require(expectedKind.isNotBlank()) { "expectedKind must not be blank." }
        require(reflectedKind.isNotBlank()) { "reflectedKind must not be blank." }
        require(status == "accepted") { "accepted text WGSL plan comparisons must be accepted." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("comparisonId", comparisonId, comma = true)
        appendTextWgslJsonField("kotlinPlanRef", kotlinPlanRef, comma = true)
        appendTextWgslJsonField("reflectedRef", reflectedRef, comma = true)
        appendTextWgslJsonField("expectedKind", expectedKind, comma = true)
        appendTextWgslJsonField("reflectedKind", reflectedKind, comma = true)
        appendTextWgslJsonField("status", status, comma = false)
        append("}")
    }
}

data class TextWgslDiagnostic(
    val code: String,
    val moduleId: String,
    val renderStep: String,
    val fieldOrBinding: String,
    val message: String,
    val terminal: Boolean = true,
    val routePromotion: String = "not-promoted",
) {
    init {
        require(code.startsWith("unsupported.") || code.startsWith("wgsl4k.")) {
            "diagnostic code must be unsupported.* or wgsl4k.*."
        }
        require(moduleId.isNotBlank()) { "moduleId must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(fieldOrBinding.isNotBlank()) { "fieldOrBinding must not be blank." }
        require(message.isNotBlank()) { "message must not be blank." }
        require(terminal) { "text WGSL refusal diagnostics must be terminal." }
        require(routePromotion == "not-promoted") { "text WGSL diagnostics cannot promote routes." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("code", code, comma = true)
        appendTextWgslJsonField("moduleId", moduleId, comma = true)
        appendTextWgslJsonField("renderStep", renderStep, comma = true)
        appendTextWgslJsonField("fieldOrBinding", fieldOrBinding, comma = true)
        appendTextWgslJsonField("message", message, comma = true)
        appendTextWgslJsonField("terminal", terminal, comma = true)
        appendTextWgslJsonField("routePromotion", routePromotion, comma = false)
        append("}")
    }
}

class TextWgslValidationReport(
    val moduleId: String,
    val sourceId: String,
    val moduleHash: String,
    val wgsl4kSha: String,
    val renderStep: String,
    val route: String,
    val subRunId: String,
    val resourcePlanId: String,
    val bindingPlanId: String,
    val bindingLayoutHash: String,
    val instanceLayoutHash: String,
    val orderingTokenId: String,
    kotlinPlanComparisons: List<TextWgslKotlinPlanComparison>,
    diagnostics: List<TextWgslDiagnostic>,
    refusals: List<TextWgslDiagnostic>,
    val wgslLanguage: String = "WGSL",
    val sdfParamsRequired: Boolean = false,
    val sdfParamsStatus: String = "not-required-for-a8",
    val routePromotion: String = "not-promoted",
    val productActivation: Boolean = false,
) {
    val kotlinPlanComparisons: List<TextWgslKotlinPlanComparison> = kotlinPlanComparisons.toList()
    val diagnostics: List<TextWgslDiagnostic> = diagnostics.toList()
    val refusals: List<TextWgslDiagnostic> = refusals.toList()

    init {
        require(moduleId.isNotBlank()) { "moduleId must not be blank." }
        require(sourceId.isNotBlank()) { "sourceId must not be blank." }
        require(moduleHash.isNotBlank()) { "moduleHash must not be blank." }
        require(wgsl4kSha.isNotBlank()) { "wgsl4kSha must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(route.isNotBlank()) { "route must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(resourcePlanId.isNotBlank()) { "resourcePlanId must not be blank." }
        require(bindingPlanId.isNotBlank()) { "bindingPlanId must not be blank." }
        require(bindingLayoutHash.isNotBlank()) { "bindingLayoutHash must not be blank." }
        require(instanceLayoutHash.isNotBlank()) { "instanceLayoutHash must not be blank." }
        require(orderingTokenId.isNotBlank()) { "orderingTokenId must not be blank." }
        require(this.kotlinPlanComparisons.isNotEmpty()) { "kotlinPlanComparisons must not be empty." }
        require(wgslLanguage == "WGSL") { "text shader validation must use WGSL." }
        require(!sdfParamsRequired || sdfParamsStatus == "present") {
            "accepted reports cannot require missing SDF params."
        }
        require(routePromotion == "not-promoted") { "KFONT-M11-009 cannot promote routes." }
        require(!productActivation) { "KFONT-M11-009 cannot activate product support." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextWgslJsonField("schema", TEXT_WGSL_VALIDATION_SCHEMA, comma = true)
        appendTextWgslStringListField("ownerTickets", listOf("KFONT-M11-009"), comma = true)
        appendTextWgslJsonField("classification", "GPU-gated", comma = true)
        appendTextWgslJsonField("moduleId", moduleId, comma = true)
        appendTextWgslJsonField("sourceId", sourceId, comma = true)
        appendTextWgslJsonField("moduleHash", moduleHash, comma = true)
        appendTextWgslJsonField("wgsl4kSha", wgsl4kSha, comma = true)
        appendTextWgslJsonField("wgslLanguage", wgslLanguage, comma = true)
        appendTextWgslJsonField("renderStep", renderStep, comma = true)
        appendTextWgslJsonField("route", route, comma = true)
        appendTextWgslJsonField("subRunId", subRunId, comma = true)
        appendTextWgslJsonField("resourcePlanId", resourcePlanId, comma = true)
        appendTextWgslJsonField("bindingPlanId", bindingPlanId, comma = true)
        appendTextWgslJsonField("bindingLayoutHash", bindingLayoutHash, comma = true)
        appendTextWgslJsonField("instanceLayoutHash", instanceLayoutHash, comma = true)
        appendTextWgslJsonField("orderingTokenId", orderingTokenId, comma = true)
        append("\"kotlinPlanComparisons\":")
        append(kotlinPlanComparisons.joinToString(separator = ",", prefix = "[", postfix = "]") { comparison ->
            comparison.toCanonicalJson()
        })
        append(",")
        append("\"diagnostics\":")
        append(diagnostics.joinToString(separator = ",", prefix = "[", postfix = "]") { diagnostic ->
            diagnostic.toCanonicalJson()
        })
        append(",")
        append("\"refusals\":")
        append(refusals.joinToString(separator = ",", prefix = "[", postfix = "]") { refusal ->
            refusal.toCanonicalJson()
        })
        append(",")
        appendTextWgslJsonField("sdfParamsRequired", sdfParamsRequired, comma = true)
        appendTextWgslJsonField("sdfParamsStatus", sdfParamsStatus, comma = true)
        appendTextWgslStringListField("nonClaims", TEXT_WGSL_NON_CLAIMS, comma = true)
        appendTextWgslJsonField("routePromotion", routePromotion, comma = true)
        appendTextWgslJsonField("productActivation", productActivation, comma = false)
        append("}\n")
    }
}

data class TextWgslValidationFixture(
    val parserSuccess: Boolean,
    val bindingLayoutMatches: Boolean,
    val sdfParamsRequired: Boolean,
    val sdfParamsAvailable: Boolean,
    val moduleRegistered: Boolean,
) {
    init {
        require(!sdfParamsAvailable || sdfParamsRequired) {
            "sdfParamsAvailable is meaningful only when sdfParamsRequired is true."
        }
    }
}

sealed interface TextWgslValidationPlanningResult {
    data class Accepted(val report: TextWgslValidationReport) : TextWgslValidationPlanningResult

    data class Refused(val diagnostic: TextWgslDiagnostic) : TextWgslValidationPlanningResult
}

fun planTextWgslValidation(fixture: TextWgslValidationFixture): TextWgslValidationPlanningResult {
    val diagnostic = when {
        !fixture.parserSuccess -> textWgslDiagnostic(
            code = "wgsl4k.validation.syntax_error",
            fieldOrBinding = "text/a8_text_mask.wgsl",
            message = "wgsl4k rejected the text WGSL module before reflection.",
        )
        !fixture.moduleRegistered -> textWgslDiagnostic(
            code = "unsupported.wgsl.unregistered_module",
            fieldOrBinding = "text/unregistered.wgsl",
            message = "WGSL source is not registered for text.a8-mask.",
        )
        !fixture.bindingLayoutMatches -> textWgslDiagnostic(
            code = "unsupported.wgsl.binding_reflection_mismatch",
            fieldOrBinding = "group=2,binding=0",
            message = "Reflected text WGSL binding layout did not match GPUTextBinding.",
        )
        fixture.sdfParamsRequired && !fixture.sdfParamsAvailable -> textWgslDiagnostic(
            code = "unsupported.text.sdf_params_missing",
            fieldOrBinding = "GPUTextSDFParams",
            message = "SDF text WGSL validation requires GPUTextSDFParams before route promotion.",
        )
        else -> null
    }
    return if (diagnostic == null) {
        TextWgslValidationPlanningResult.Accepted(defaultTextWgslValidationReport(includeRefusals = false))
    } else {
        TextWgslValidationPlanningResult.Refused(diagnostic)
    }
}

fun defaultTextWgslValidationFixture(): TextWgslValidationFixture =
    TextWgslValidationFixture(
        parserSuccess = true,
        bindingLayoutMatches = true,
        sdfParamsRequired = false,
        sdfParamsAvailable = false,
        moduleRegistered = true,
    )

fun defaultTextWgslReflectionReportJson(): String =
    defaultTextWgslReflectionReport().toCanonicalJson()

fun defaultTextWgslValidationReportJson(): String =
    defaultTextWgslValidationReport().toCanonicalJson()

fun defaultTextWgslReflectionReport(): TextWgslReflectionReport {
    val evidence = defaultGPUTextResourceContractEvidence()
    val bindingPlan = evidence.bindingPlan
    val instanceLayout = evidence.instanceLayout
    return TextWgslReflectionReport(
        moduleId = TEXT_WGSL_MODULE_ID,
        sourceId = TEXT_WGSL_SOURCE_ID,
        moduleHash = TEXT_WGSL_MODULE_HASH,
        wgsl4kSha = TEXT_WGSL4K_SHA,
        renderStep = bindingPlan.renderStep,
        entryPoints = listOf(TextWgslEntryPoint(name = "fragmentMain", stage = "fragment")),
        reflectedBindings = listOf(
            TextWgslReflectedBinding(2, 0, "glyphAtlas", "sampledTexture", access = "read", minBindingSize = null),
            TextWgslReflectedBinding(2, 1, "glyphSampler", "sampler", access = "read", minBindingSize = null),
            TextWgslReflectedBinding(2, 2, "textParams", "uniformBuffer", access = "read", minBindingSize = 16),
        ),
        uniformLayouts = listOf(defaultTextWgslUniformLayout()),
        instanceInputExpectations = instanceLayout.attributes.map { attribute ->
            TextWgslInstanceInputExpectation(
                name = attribute.name,
                format = attribute.format,
                byteOffset = attribute.byteOffset,
                strideBytes = instanceLayout.strideBytes,
                layoutHash = instanceLayout.layoutHash,
            )
        },
        diagnostics = emptyList(),
    )
}

fun defaultTextWgslValidationReport(includeRefusals: Boolean = true): TextWgslValidationReport {
    val evidence = defaultGPUTextResourceContractEvidence()
    val resourcePlan = evidence.resourcePlan
    val bindingPlan = evidence.bindingPlan
    val instanceLayout = evidence.instanceLayout
    val orderingToken = defaultGPUTextOrderingToken()
    return TextWgslValidationReport(
        moduleId = TEXT_WGSL_MODULE_ID,
        sourceId = TEXT_WGSL_SOURCE_ID,
        moduleHash = TEXT_WGSL_MODULE_HASH,
        wgsl4kSha = TEXT_WGSL4K_SHA,
        renderStep = bindingPlan.renderStep,
        route = resourcePlan.route,
        subRunId = resourcePlan.subRunId,
        resourcePlanId = resourcePlan.resourcePlanId,
        bindingPlanId = bindingPlan.bindingPlanId,
        bindingLayoutHash = bindingPlan.bindingLayoutHash,
        instanceLayoutHash = instanceLayout.layoutHash,
        orderingTokenId = orderingToken.tokenId,
        kotlinPlanComparisons = defaultTextWgslPlanComparisons(evidence),
        diagnostics = emptyList(),
        refusals = if (includeRefusals) defaultTextWgslRefusals() else emptyList(),
    )
}

private fun defaultTextWgslPlanComparisons(
    evidence: GPUTextResourceContractEvidence,
): List<TextWgslKotlinPlanComparison> {
    val bindingPlan = evidence.bindingPlan
    val resourcePlan = evidence.resourcePlan
    val instanceLayout = evidence.instanceLayout
    val slots = bindingPlan.resourceSlots.associateBy { slot -> slot.name }
    return listOf(
        TextWgslKotlinPlanComparison(
            comparisonId = "binding:glyphAtlas",
            kotlinPlanRef = slots.getValue("glyphAtlas").resourceRef,
            reflectedRef = "group=2,binding=0",
            expectedKind = "sampledTexture",
            reflectedKind = "sampledTexture",
            status = "accepted",
        ),
        TextWgslKotlinPlanComparison(
            comparisonId = "binding:glyphSampler",
            kotlinPlanRef = slots.getValue("glyphSampler").resourceRef,
            reflectedRef = "group=2,binding=1",
            expectedKind = "sampler",
            reflectedKind = "sampler",
            status = "accepted",
        ),
        TextWgslKotlinPlanComparison(
            comparisonId = "binding:textParams",
            kotlinPlanRef = slots.getValue("textParams").resourceRef,
            reflectedRef = "group=2,binding=2",
            expectedKind = "uniformBuffer",
            reflectedKind = "uniformBuffer",
            status = "accepted",
        ),
        TextWgslKotlinPlanComparison(
            comparisonId = "instance-layout:${instanceLayout.layoutId}",
            kotlinPlanRef = slots.getValue("glyphInstances").resourceRef,
            reflectedRef = instanceLayout.layoutHash,
            expectedKind = "vertexBuffer",
            reflectedKind = "instanceInputLayout",
            status = "accepted",
        ),
        TextWgslKotlinPlanComparison(
            comparisonId = "resource-plan:${resourcePlan.resourcePlanId}",
            kotlinPlanRef = resourcePlan.bindingPlanId,
            reflectedRef = bindingPlan.bindingLayoutHash,
            expectedKind = "GPUTextBinding",
            reflectedKind = "WGSLBindingLayout",
            status = "accepted",
        ),
    )
}

private fun defaultTextWgslRefusals(): List<TextWgslDiagnostic> =
    listOf(
        defaultTextWgslValidationFixture().copy(parserSuccess = false),
        defaultTextWgslValidationFixture().copy(bindingLayoutMatches = false),
        defaultTextWgslValidationFixture().copy(sdfParamsRequired = true, sdfParamsAvailable = false),
        defaultTextWgslValidationFixture().copy(moduleRegistered = false),
    ).map { fixture ->
        (planTextWgslValidation(fixture) as TextWgslValidationPlanningResult.Refused).diagnostic
    }

private fun defaultTextWgslUniformLayout(): TextWgslUniformLayout =
    TextWgslUniformLayout(
        structName = "TextParams",
        addressSpace = "uniform",
        size = 16,
        alignment = 8,
        members = listOf(
            TextWgslLayoutMember("atlasScale", "vec2<f32>", offset = 0, size = 8, alignment = 8, stride = null),
            TextWgslLayoutMember("maskGamma", "f32", offset = 8, size = 4, alignment = 4, stride = null),
        ),
    )

private fun textWgslDiagnostic(
    code: String,
    fieldOrBinding: String,
    message: String,
): TextWgslDiagnostic =
    TextWgslDiagnostic(
        code = code,
        moduleId = TEXT_WGSL_MODULE_ID,
        renderStep = "A8TextMaskStep",
        fieldOrBinding = fieldOrBinding,
        message = message,
    )

private fun StringBuilder.appendTextWgslJsonField(name: String, value: String, comma: Boolean) {
    append(name.textWgslQuoted())
    append(":")
    append(value.textWgslQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendTextWgslJsonField(name: String, value: Int, comma: Boolean) {
    append(name.textWgslQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendTextWgslJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.textWgslQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendTextWgslNullableIntField(name: String, value: Int?, comma: Boolean) {
    append(name.textWgslQuoted())
    append(":")
    append(value ?: "null")
    if (comma) append(",")
}

private fun StringBuilder.appendTextWgslStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.textWgslQuoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]") { value -> value.textWgslQuoted() })
    if (comma) append(",")
}

private fun String.textWgslQuoted(): String = "\"${textWgslEscapeJson()}\""

private fun String.textWgslEscapeJson(): String = buildString(length) {
    for (ch in this@textWgslEscapeJson) {
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

private const val TEXT_WGSL_REFLECTION_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.TextWgslReflectionReport.v1"
private const val TEXT_WGSL_VALIDATION_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.TextWgslValidationReport.v1"
private const val TEXT_WGSL_MODULE_ID = "text.a8-mask"
private const val TEXT_WGSL_SOURCE_ID = "text/a8_text_mask.wgsl"
private const val TEXT_WGSL_MODULE_HASH = "sha256:text-a8-mask"
private const val TEXT_WGSL4K_SHA = "72a35b58758f241756d984a84768ae77308730da"

private val TEXT_WGSL_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
    "no-coloremoji-blendmode-retirement",
    "no-visual-correctness-claim",
    "no-sdf-route-support-claim",
)
