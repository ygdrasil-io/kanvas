package org.graphiks.kanvas.gpu.renderer.wgsl

/** Minimal reviewed wgsl4k reflection report shape consumed by Kanvas evidence tooling. */
data class Wgsl4kReflectionReport(
    val sourceId: String,
    val moduleHash: String,
    val wgsl4kSha: String,
    val validation: Wgsl4kValidationSummary,
    val entryPoints: List<Wgsl4kEntryPointReflection>,
    val bindings: List<Wgsl4kBindingReflection>,
    val layouts: List<Wgsl4kLayoutReflection>,
    val unsupportedFeatures: List<String> = emptyList(),
)

/** Validation summary mirrored from the reviewed wgsl4k report contract. */
data class Wgsl4kValidationSummary(
    val success: Boolean,
    val diagnostics: List<Wgsl4kDiagnostic> = emptyList(),
)

/** Stable diagnostic reason and message emitted by wgsl4k validation/reflection. */
data class Wgsl4kDiagnostic(
    val reason: String,
    val message: String,
)

/** Reflected WGSL entry point facts emitted by wgsl4k. */
data class Wgsl4kEntryPointReflection(
    val name: String,
    val stage: String,
    val workgroupSize: List<Int>? = null,
)

/** Reflected WGSL resource binding facts emitted by wgsl4k. */
data class Wgsl4kBindingReflection(
    val group: Int,
    val binding: Int,
    val name: String,
    val resourceKind: String,
    val access: String = "read",
    val minBindingSize: Int? = null,
)

/** Reflected WGSL uniform or storage layout facts emitted by wgsl4k. */
data class Wgsl4kLayoutReflection(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    val members: List<Wgsl4kLayoutMemberReflection>,
)

/** Reflected WGSL layout member facts emitted by wgsl4k. */
data class Wgsl4kLayoutMemberReflection(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
)

/** Kanvas-side expectation used to compare a reviewed wgsl4k report with route-owned ABI facts. */
data class WgslReflectionExpectation(
    val reportKind: String,
    val moduleId: String,
    val allowedSourceIds: Set<String>,
    val expectedEntryPoints: List<WgslExpectedEntryPoint>,
    val expectedBindings: List<WgslExpectedBinding>,
    val expectedLayouts: List<WgslExpectedLayout>,
    val descriptorId: String? = null,
    val descriptorVersion: Int? = null,
    val routePromotion: String = "not-promoted",
    val productActivation: Boolean = false,
)

/** Expected Kanvas entry point fact used when comparing wgsl4k reflection. */
data class WgslExpectedEntryPoint(
    val name: String,
    val stage: String,
)

/** Expected Kanvas binding fact used when comparing wgsl4k reflection. */
data class WgslExpectedBinding(
    val group: Int,
    val binding: Int,
    val name: String,
    val resourceKind: String,
    val minBindingSize: Int?,
)

/** Expected Kanvas layout fact used when comparing wgsl4k reflection. */
data class WgslExpectedLayout(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    val members: List<WGSLUniformFieldLayout>,
)

/** Kanvas report produced after comparing a reviewed wgsl4k report with route-owned expectations. */
data class WgslConsumedReflectionReport(
    val schemaVersion: Int,
    val reportKind: String,
    val moduleId: String,
    val sourceId: String,
    val moduleHash: String,
    val wgsl4kSha: String,
    val descriptorId: String?,
    val descriptorVersion: Int?,
    val routePromotion: String,
    val productActivation: Boolean,
    val entryPoints: List<Wgsl4kEntryPointReflection>,
    val bindings: List<Wgsl4kBindingReflection>,
    val layouts: List<Wgsl4kLayoutReflection>,
    val comparison: WgslReflectionComparison,
    val diagnostics: List<WgslReportDiagnostic>,
)

/** Summary of reflected-vs-expected facts for a consumed WGSL report. */
data class WgslReflectionComparison(
    val status: String,
    val expectedBindings: Int,
    val reflectedBindings: Int,
    val expectedLayouts: Int,
    val reflectedLayouts: Int,
)

/** Stable Kanvas-side diagnostic emitted while consuming a wgsl4k report. */
data class WgslReportDiagnostic(
    val code: String,
    val message: String,
    val fieldOrBinding: String? = null,
    val terminal: Boolean = true,
)

/** Compares a reviewed wgsl4k report against Kanvas-owned WGSL expectations. */
fun consumeWgsl4kReflectionReport(
    report: Wgsl4kReflectionReport,
    expectation: WgslReflectionExpectation,
): WgslConsumedReflectionReport {
    val diagnostics = buildList {
        if (report.sourceId !in expectation.allowedSourceIds) {
            add(
                WgslReportDiagnostic(
                    code = "unsupported.wgsl.unregistered_module",
                    fieldOrBinding = report.sourceId,
                    message = "WGSL source is not registered for ${expectation.moduleId}: ${report.sourceId}",
                ),
            )
        }
        if (!report.validation.success) {
            addAll(
                report.validation.diagnostics.map {
                    WgslReportDiagnostic(code = it.reason, message = it.message)
                },
            )
        }
        addAll(entryPointDiagnostics(report.entryPoints, expectation.expectedEntryPoints))
        addAll(bindingDiagnostics(report.bindings, expectation.expectedBindings))
        addAll(layoutDiagnostics(report.layouts, expectation.expectedLayouts))
        addAll(
            report.unsupportedFeatures.map {
                WgslReportDiagnostic(
                    code = "unsupported.wgsl.feature_unrepresented_by_wgsl4k",
                    fieldOrBinding = it,
                    message = "wgsl4k reported an unsupported reflection feature: $it",
                )
            },
        )
    }

    return WgslConsumedReflectionReport(
        schemaVersion = 1,
        reportKind = expectation.reportKind,
        moduleId = expectation.moduleId,
        sourceId = report.sourceId,
        moduleHash = report.moduleHash,
        wgsl4kSha = report.wgsl4kSha,
        descriptorId = expectation.descriptorId,
        descriptorVersion = expectation.descriptorVersion,
        routePromotion = expectation.routePromotion,
        productActivation = expectation.productActivation,
        entryPoints = report.entryPoints,
        bindings = report.bindings,
        layouts = report.layouts,
        comparison = WgslReflectionComparison(
            status = if (diagnostics.any { it.terminal }) "rejected" else "accepted",
            expectedBindings = expectation.expectedBindings.size,
            reflectedBindings = report.bindings.size,
            expectedLayouts = expectation.expectedLayouts.size,
            reflectedLayouts = report.layouts.size,
        ),
        diagnostics = diagnostics.sortedWith(compareBy<WgslReportDiagnostic> { it.code }.thenBy { it.fieldOrBinding.orEmpty() }),
    )
}

private fun entryPointDiagnostics(
    actual: List<Wgsl4kEntryPointReflection>,
    expected: List<WgslExpectedEntryPoint>,
): List<WgslReportDiagnostic> =
    expected.mapNotNull { entryPoint ->
        val reflected = actual.singleOrNull { it.name == entryPoint.name }
        when {
            reflected == null -> WgslReportDiagnostic(
                code = "unsupported.wgsl.entry_point_reflection_mismatch",
                fieldOrBinding = entryPoint.name,
                message = "Missing reflected WGSL entry point: ${entryPoint.name}",
            )
            reflected.stage != entryPoint.stage -> WgslReportDiagnostic(
                code = "unsupported.wgsl.entry_point_reflection_mismatch",
                fieldOrBinding = entryPoint.name,
                message = "Entry point ${entryPoint.name} stage was ${reflected.stage} but expected ${entryPoint.stage}",
            )
            else -> null
        }
    }

private fun bindingDiagnostics(
    actual: List<Wgsl4kBindingReflection>,
    expected: List<WgslExpectedBinding>,
): List<WgslReportDiagnostic> =
    expected.mapNotNull { binding ->
        val reflected = actual.singleOrNull { it.group == binding.group && it.binding == binding.binding }
        val slot = "group=${binding.group},binding=${binding.binding}"
        when {
            reflected == null -> WgslReportDiagnostic(
                code = "unsupported.wgsl.binding_reflection_mismatch",
                fieldOrBinding = slot,
                message = "Missing reflected WGSL binding for $slot",
            )
            reflected.name != binding.name -> WgslReportDiagnostic(
                code = "unsupported.wgsl.binding_reflection_mismatch",
                fieldOrBinding = slot,
                message = "Binding $slot name was ${reflected.name} but expected ${binding.name}",
            )
            reflected.resourceKind != binding.resourceKind -> WgslReportDiagnostic(
                code = "unsupported.wgsl.binding_reflection_mismatch",
                fieldOrBinding = slot,
                message = "Binding $slot kind was ${reflected.resourceKind} but expected ${binding.resourceKind}",
            )
            binding.minBindingSize != null && reflected.minBindingSize != binding.minBindingSize -> WgslReportDiagnostic(
                code = "unsupported.wgsl.binding_reflection_mismatch",
                fieldOrBinding = slot,
                message = "Binding $slot min size was ${reflected.minBindingSize} but expected ${binding.minBindingSize}",
            )
            else -> null
        }
    }

private fun layoutDiagnostics(
    actual: List<Wgsl4kLayoutReflection>,
    expected: List<WgslExpectedLayout>,
): List<WgslReportDiagnostic> =
    expected.flatMap { layout ->
        val reflected = actual.singleOrNull { it.structName == layout.structName }
        if (reflected == null) {
            return@flatMap listOf(
                WgslReportDiagnostic(
                    code = "unsupported.wgsl.uniform_layout_mismatch",
                    fieldOrBinding = layout.structName,
                    message = "Missing reflected WGSL layout for ${layout.structName}",
                ),
            )
        }

        buildList {
            if (reflected.addressSpace != layout.addressSpace || reflected.size != layout.size || reflected.alignment != layout.alignment) {
                add(
                    WgslReportDiagnostic(
                        code = "unsupported.wgsl.uniform_layout_mismatch",
                        fieldOrBinding = layout.structName,
                        message = "Layout ${layout.structName} summary did not match expected address space, size, or alignment",
                    ),
                )
            }
            layout.members.forEach { member ->
                val reflectedMember = reflected.members.singleOrNull { it.name == member.name }
                when {
                    reflectedMember == null -> add(
                        WgslReportDiagnostic(
                            code = "unsupported.wgsl.uniform_layout_mismatch",
                            fieldOrBinding = "${layout.structName}.${member.name}",
                            message = "Missing reflected WGSL layout member ${layout.structName}.${member.name}",
                        ),
                    )
                    reflectedMember.type != member.type ||
                        reflectedMember.offset.toLong() != member.offset ||
                        reflectedMember.size.toLong() != member.sizeBytes ||
                        reflectedMember.alignment != member.alignment -> add(
                        WgslReportDiagnostic(
                            code = "unsupported.wgsl.uniform_layout_mismatch",
                            fieldOrBinding = "${layout.structName}.${member.name}",
                            message = "Layout member ${layout.structName}.${member.name} did not match expected type, offset, size, or alignment",
                        ),
                    )
                }
            }
        }
    }

/** Serializes a consumed WGSL report as stable JSON for checked-in evidence fixtures. */
fun WgslConsumedReflectionReport.toDeterministicJson(): String =
    buildString {
        append("{")
        appendJsonField("schemaVersion", schemaVersion)
        append(",")
        appendJsonField("reportKind", reportKind)
        append(",")
        appendJsonField("moduleId", moduleId)
        append(",")
        appendJsonField("sourceId", sourceId)
        append(",")
        appendJsonField("moduleHash", moduleHash)
        append(",")
        appendJsonField("wgsl4kSha", wgsl4kSha)
        descriptorId?.let {
            append(",")
            appendJsonField("descriptorId", it)
        }
        descriptorVersion?.let {
            append(",")
            appendJsonField("descriptorVersion", it)
        }
        append(",")
        appendJsonField("routePromotion", routePromotion)
        append(",")
        appendJsonField("productActivation", productActivation)
        append(",")
        append("\"comparison\":{")
        appendJsonField("status", comparison.status)
        append(",")
        appendJsonField("expectedBindings", comparison.expectedBindings)
        append(",")
        appendJsonField("reflectedBindings", comparison.reflectedBindings)
        append(",")
        appendJsonField("expectedLayouts", comparison.expectedLayouts)
        append(",")
        appendJsonField("reflectedLayouts", comparison.reflectedLayouts)
        append("},")
        append("\"entryPoints\":[")
        entryPoints.joinTo(this, separator = ",") { entry ->
            buildString {
                append("{")
                appendJsonField("name", entry.name)
                append(",")
                appendJsonField("stage", entry.stage)
                entry.workgroupSize?.let {
                    append(",")
                    append("\"workgroupSize\":[")
                    append(it.joinToString(","))
                    append("]")
                }
                append("}")
            }
        }
        append("],")
        append("\"bindings\":[")
        bindings.joinTo(this, separator = ",") { binding ->
            buildString {
                append("{")
                appendJsonField("group", binding.group)
                append(",")
                appendJsonField("binding", binding.binding)
                append(",")
                appendJsonField("name", binding.name)
                append(",")
                appendJsonField("resourceKind", binding.resourceKind)
                append(",")
                appendJsonField("access", binding.access)
                append(",")
                appendJsonNullableIntField("minBindingSize", binding.minBindingSize)
                append("}")
            }
        }
        append("],")
        append("\"layouts\":[")
        layouts.joinTo(this, separator = ",") { layout ->
            buildString {
                append("{")
                appendJsonField("structName", layout.structName)
                append(",")
                appendJsonField("addressSpace", layout.addressSpace)
                append(",")
                appendJsonField("size", layout.size)
                append(",")
                appendJsonField("alignment", layout.alignment)
                append(",\"members\":[")
                layout.members.joinTo(this, separator = ",") { member ->
                    buildString {
                        append("{")
                        appendJsonField("name", member.name)
                        append(",")
                        appendJsonField("type", member.type)
                        append(",")
                        appendJsonField("offset", member.offset)
                        append(",")
                        appendJsonField("size", member.size)
                        append(",")
                        appendJsonField("alignment", member.alignment)
                        append(",")
                        appendJsonNullableIntField("stride", member.stride)
                        append("}")
                    }
                }
                append("]}")
            }
        }
        append("],")
        append("\"diagnostics\":[")
        diagnostics.joinTo(this, separator = ",") { diagnostic ->
            buildString {
                append("{")
                appendJsonField("code", diagnostic.code)
                append(",")
                diagnostic.fieldOrBinding?.let {
                    appendJsonField("fieldOrBinding", it)
                    append(",")
                }
                appendJsonField("message", diagnostic.message)
                append(",")
                appendJsonField("terminal", diagnostic.terminal)
                append("}")
            }
        }
        append("]}")
    }

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append("\"")
    append(name)
    append("\":\"")
    append(value.jsonEscaped())
    append("\"")
}

private fun StringBuilder.appendJsonField(name: String, value: Int) {
    append("\"")
    append(name)
    append("\":")
    append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    append("\"")
    append(name)
    append("\":")
    append(value)
}

private fun StringBuilder.appendJsonNullableIntField(name: String, value: Int?) {
    append("\"")
    append(name)
    append("\":")
    append(value?.toString() ?: "null")
}

private fun String.jsonEscaped(): String =
    buildString {
        this@jsonEscaped.forEach { char ->
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
