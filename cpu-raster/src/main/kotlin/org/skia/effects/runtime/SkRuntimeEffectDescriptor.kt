package org.skia.effects.runtime

private val acceptedWgslImplementationIds: Set<String> = setOf(
    "wgsl/runtime_color_filter_luma_to_alpha",
    "wgsl/runtime_linear_gradient_rt",
    "wgsl/runtime_simple_rt",
    "wgsl/runtime_spiral_rt",
)
private val knownWgslImplementationIds: Set<String> = acceptedWgslImplementationIds

public data class SkRuntimeEffectDescriptor(
    val stableId: String,
    val kind: SkRuntimeEffect.Kind,
    val uniforms: List<SkRuntimeEffect.Uniform>,
    val children: List<SkRuntimeEffect.Child>,
    val flags: Int,
    val cpuImplementationId: String,
    val wgslImplementationId: String?,
)

public data class SkRuntimeEffectSupportMatrixEntry(
    val canonicalHash: Long,
    val stableId: String,
    val kind: SkRuntimeEffect.Kind,
    val uniforms: List<SkRuntimeEffect.Uniform>,
    val children: List<SkRuntimeEffect.Child>,
    val flags: Int,
    val cpuImplementationId: String,
    val wgslImplementationId: String?,
    val descriptorStatus: String,
    val descriptor: SkRuntimeEffectDescriptor?,
) {
    public val cpuSupport: String =
        if (cpuImplementationId.isBlank()) {
            "unsupported: CPU implementation id missing"
        } else {
            "supported:$cpuImplementationId"
        }

    public val gpuSupport: String =
        wgslImplementationId
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (it in acceptedWgslImplementationIds) {
                    "supported:$it"
                } else {
                    "unsupported: WGSL implementation id not promoted: $it"
                }
            }
            ?: "unsupported: WGSL implementation id missing"

    public val missingReason: String =
        if (descriptor == null) {
            "Runtime effect descriptor missing for dispatch-only effect: $canonicalHash"
        } else {
            "none"
        }
}

public data class SkRuntimeEffectSupportMatrixStatusCounts(
    val total: Int,
    val descriptorBacked: Int,
    val dispatchOnlyMissingDescriptor: Int,
    val cpuOnly: Int,
    val gpuBacked: Int,
)

public data class SkRuntimeEffectSupportMatrixV2Entry(
    val canonicalHash: Long?,
    val stableId: String,
    val kind: String,
    val uniforms: List<String>,
    val children: List<String>,
    val flags: Int?,
    val cpuImplementationId: String?,
    val wgslImplementationId: String?,
    val descriptorStatus: String,
    val supportState: String,
    val fallbackReason: String,
    val pmNote: String,
)

public data class SkRuntimeEffectSupportMatrixV2StatusCounts(
    val total: Int,
    val descriptorBacked: Int,
    val cpuOnly: Int,
    val gpuBacked: Int,
    val dependencyGated: Int,
    val expectedUnsupported: Int,
)

public object SkRuntimeEffectDescriptorRegistry {
    private val byHash: MutableMap<Long, SkRuntimeEffectDescriptor> = HashMap()
    private val byStableId: MutableMap<String, SkRuntimeEffectDescriptor> = HashMap()

    public fun register(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        validateDescriptor(hash, descriptor)
        rejectDuplicate(hash, descriptor)
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    public fun lookup(source: String): SkRuntimeEffectDescriptor? =
        byHash[SkRuntimeEffectDispatch.canonicalHash(source)]

    public fun missingDiagnostic(source: String): String =
        "Runtime effect descriptor not registered: ${SkRuntimeEffectDispatch.canonicalHash(source)}"

    public fun supportMatrixEntries(): List<SkRuntimeEffectSupportMatrixEntry> =
        (
            byHash.entries.map { (hash, descriptor) ->
                SkRuntimeEffectSupportMatrixEntry(
                    canonicalHash = hash,
                    stableId = descriptor.stableId,
                    kind = descriptor.kind,
                    uniforms = descriptor.uniforms,
                    children = descriptor.children,
                    flags = descriptor.flags,
                    cpuImplementationId = descriptor.cpuImplementationId,
                    wgslImplementationId = descriptor.wgslImplementationId,
                    descriptorStatus = "descriptor-backed",
                    descriptor = descriptor,
                )
            } + SkRuntimeEffectDispatch.builtinMetadataEntries()
                .filter { (hash, _) -> hash !in byHash }
                .map { (hash, metadata) ->
                    SkRuntimeEffectSupportMatrixEntry(
                        canonicalHash = hash,
                        stableId = metadata.stableId,
                        kind = metadata.kind,
                        uniforms = metadata.uniforms,
                        children = metadata.children,
                        flags = metadata.flags,
                        cpuImplementationId = metadata.cpuImplementationId,
                        wgslImplementationId = null,
                        descriptorStatus = "dispatch-only; missing descriptor",
                        descriptor = null,
                    )
                }
            )
            .sortedWith(
                compareBy<SkRuntimeEffectSupportMatrixEntry> { it.stableId }
                    .thenBy { it.canonicalHash },
            )

    public fun supportMatrixStatusCounts(): SkRuntimeEffectSupportMatrixStatusCounts {
        val entries = supportMatrixEntries()
        return SkRuntimeEffectSupportMatrixStatusCounts(
            total = entries.size,
            descriptorBacked = entries.count { it.descriptorStatus == "descriptor-backed" },
            dispatchOnlyMissingDescriptor = entries.count { it.descriptorStatus == "dispatch-only; missing descriptor" },
            cpuOnly = entries.count { it.cpuSupport.startsWith("supported:") && !it.gpuSupport.startsWith("supported:") },
            gpuBacked = entries.count { it.gpuSupport.startsWith("supported:") },
        )
    }

    public fun supportMatrixV2Entries(): List<SkRuntimeEffectSupportMatrixV2Entry> =
        (supportMatrixEntries().map(::toV2Entry) + supportMatrixV2PolicyRows())
            .sortedWith(
                compareBy<SkRuntimeEffectSupportMatrixV2Entry> { it.stableId }
                    .thenBy { it.canonicalHash ?: Long.MIN_VALUE },
            )

    public fun supportMatrixV2StatusCounts(): SkRuntimeEffectSupportMatrixV2StatusCounts {
        val entries = supportMatrixV2Entries()
        return SkRuntimeEffectSupportMatrixV2StatusCounts(
            total = entries.size,
            descriptorBacked = entries.count { it.descriptorStatus == "descriptor-backed" },
            cpuOnly = entries.count { it.supportState == "cpu-only" },
            gpuBacked = entries.count { it.supportState == "gpu-backed" },
            dependencyGated = entries.count { it.supportState == "dependency-gated" },
            expectedUnsupported = entries.count { it.supportState == "expected-unsupported" },
        )
    }

    public fun exportSupportMatrixMarkdown(): String = buildString {
        val counts = supportMatrixStatusCounts()
        appendLine("# Runtime Effect Descriptor Support Matrix")
        appendLine()
        appendLine("Derived evidence. The descriptor registry is the source of truth.")
        appendLine()
        appendLine(
            "Status counts: total=${counts.total}; descriptor-backed=${counts.descriptorBacked}; " +
                "dispatch-only/missing-descriptor=${counts.dispatchOnlyMissingDescriptor}; " +
                "CPU-only=${counts.cpuOnly}; GPU-backed=${counts.gpuBacked}.",
        )
        appendLine()
        appendLine(
            "| Stable id | Canonical hash | Kind | Uniforms | Children | Flags | CPU support | GPU support | Descriptor status | Missing reason |",
        )
        appendLine("|---|---:|---|---|---|---:|---|---|---|---|")
        supportMatrixEntries().forEach { entry ->
            append("| ")
            append(markdownCell(entry.stableId))
            append(" | ")
            append(entry.canonicalHash)
            append(" | ")
            append(entry.kind)
            append(" | ")
            append(markdownCell(entry.uniforms.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(markdownCell(entry.children.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(entry.flags)
            append(" | ")
            append(markdownCell(entry.cpuSupport))
            append(" | ")
            append(markdownCell(entry.gpuSupport))
            append(" | ")
            append(markdownCell(entry.descriptorStatus))
            append(" | ")
            append(markdownCell(entry.missingReason))
            appendLine(" |")
        }
    }

    public fun exportSupportMatrixV2Markdown(): String = buildString {
        val counts = supportMatrixV2StatusCounts()
        appendLine("# Runtime Effects V2 Support Matrix")
        appendLine()
        appendLine("Derived evidence. `SkRuntimeEffectDescriptorRegistry` is the source of truth.")
        appendLine("WGSL is the GPU target; SkSL is only a compatibility/refusal surface.")
        appendLine()
        appendLine(
            "Status counts: total=${counts.total}; descriptor-backed=${counts.descriptorBacked}; " +
                "CPU-only=${counts.cpuOnly}; GPU-backed=${counts.gpuBacked}; " +
                "dependency-gated=${counts.dependencyGated}; expected-unsupported=${counts.expectedUnsupported}.",
        )
        appendLine()
        appendLine(
            "| Stable id | Kind | Descriptor status | Support state | CPU implementation | WGSL implementation | Fallback reason | PM note |",
        )
        appendLine("|---|---|---|---|---|---|---|---|")
        supportMatrixV2Entries().forEach { entry ->
            append("| ")
            append(markdownCell(entry.stableId))
            append(" | ")
            append(markdownCell(entry.kind))
            append(" | ")
            append(markdownCell(entry.descriptorStatus))
            append(" | ")
            append(markdownCell(entry.supportState))
            append(" | ")
            append(markdownCell(entry.cpuImplementationId ?: "-"))
            append(" | ")
            append(markdownCell(entry.wgslImplementationId ?: "-"))
            append(" | ")
            append(markdownCell(entry.fallbackReason))
            append(" | ")
            append(markdownCell(entry.pmNote))
            appendLine(" |")
        }
        appendLine()
        appendLine("## Non-Claims")
        supportMatrixV2NonClaims().forEach { nonClaim ->
            appendLine("- $nonClaim")
        }
    }

    public fun exportSupportMatrixV2Json(): String {
        val counts = supportMatrixV2StatusCounts()
        val entries = supportMatrixV2Entries()
        return buildString {
            append("{")
            append("\"schemaVersion\":")
            appendJsonString("kanvas.runtime-effects.v2.support-matrix")
            append(",\"generatedBy\":")
            appendJsonString("SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Json")
            append(",\"sourceOfTruth\":")
            appendJsonString("SkRuntimeEffectDescriptorRegistry")
            append(",\"counts\":{")
            append("\"total\":${counts.total}")
            append(",\"descriptorBacked\":${counts.descriptorBacked}")
            append(",\"cpuOnly\":${counts.cpuOnly}")
            append(",\"gpuBacked\":${counts.gpuBacked}")
            append(",\"dependencyGated\":${counts.dependencyGated}")
            append(",\"expectedUnsupported\":${counts.expectedUnsupported}")
            append("}")
            append(",\"nonClaims\":")
            appendJsonStringArray(supportMatrixV2NonClaims())
            append(",\"rows\":[")
            entries.forEachIndexed { index, entry ->
                if (index > 0) append(",")
                append("{")
                append("\"stableId\":")
                appendJsonString(entry.stableId)
                append(",\"canonicalHash\":")
                append(entry.canonicalHash?.toString() ?: "null")
                append(",\"kind\":")
                appendJsonString(entry.kind)
                append(",\"uniforms\":")
                appendJsonStringArray(entry.uniforms)
                append(",\"children\":")
                appendJsonStringArray(entry.children)
                append(",\"flags\":")
                append(entry.flags?.toString() ?: "null")
                append(",\"cpuImplementationId\":")
                appendJsonNullableString(entry.cpuImplementationId)
                append(",\"wgslImplementationId\":")
                appendJsonNullableString(entry.wgslImplementationId)
                append(",\"descriptorStatus\":")
                appendJsonString(entry.descriptorStatus)
                append(",\"supportState\":")
                appendJsonString(entry.supportState)
                append(",\"fallbackReason\":")
                appendJsonString(entry.fallbackReason)
                append(",\"pmNote\":")
                appendJsonString(entry.pmNote)
                append("}")
            }
            append("]}")
        }
    }

    internal fun registerBuiltinIfAbsent(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        validateDescriptor(hash, descriptor)
        val existingByHash = byHash[hash]
        val existingByStableId = byStableId[descriptor.stableId]
        if (existingByHash != null || existingByStableId != null) {
            check(existingByHash?.stableId == descriptor.stableId && existingByStableId != null) {
                duplicateDiagnostic(hash, descriptor)
            }
            return
        }
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    internal fun registerForTestOverride(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        validateDescriptor(hash, descriptor)
        byHash.remove(hash)?.let { byStableId.remove(it.stableId) }
        byStableId.remove(descriptor.stableId)
        byHash.entries.removeIf { it.value.stableId == descriptor.stableId }
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    internal fun clearForTest() {
        byHash.clear()
        byStableId.clear()
    }

    private fun rejectDuplicate(hash: Long, descriptor: SkRuntimeEffectDescriptor) {
        check(hash !in byHash && descriptor.stableId !in byStableId) {
            duplicateDiagnostic(hash, descriptor)
        }
    }

    private fun validateDescriptor(hash: Long, descriptor: SkRuntimeEffectDescriptor) {
        check(descriptor.stableId.matches(STABLE_ID_PATTERN)) {
            "Invalid runtime effect descriptor stableId: canonicalHash=$hash stableId=${descriptor.stableId}"
        }
        check(descriptor.cpuImplementationId.matches(IMPLEMENTATION_ID_PATTERN)) {
            "Invalid runtime effect descriptor CPU implementation id: " +
                "canonicalHash=$hash stableId=${descriptor.stableId} cpuImplementationId=${descriptor.cpuImplementationId}"
        }
        descriptor.wgslImplementationId?.let { wgslId ->
            check(wgslId.matches(IMPLEMENTATION_ID_PATTERN)) {
                "Invalid runtime effect descriptor WGSL implementation id: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} wgslImplementationId=$wgslId"
            }
            check(wgslId in knownWgslImplementationIds) {
                "Runtime effect descriptor WGSL evidence missing: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} wgslImplementationId=$wgslId"
            }
        }
        descriptor.uniforms.forEach { uniform ->
            check(uniform.name.matches(DECLARATION_NAME_PATTERN)) {
                "Invalid runtime effect descriptor uniform: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} uniform=${uniform.name}"
            }
            check(uniform.offset >= 0 && uniform.count > 0 && isValidUniformFlags(uniform.flags)) {
                "Invalid runtime effect descriptor uniform layout: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} uniform=${uniform.name}"
            }
        }
        descriptor.children.forEachIndexed { index, child ->
            check(child.name.matches(DECLARATION_NAME_PATTERN)) {
                "Invalid runtime effect descriptor child: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} child=${child.name}"
            }
            check(child.index == index) {
                "Invalid runtime effect descriptor child index: " +
                    "canonicalHash=$hash stableId=${descriptor.stableId} child=${child.name} index=${child.index}"
            }
        }
        check(isValidEffectFlags(descriptor.flags)) {
            "Invalid runtime effect descriptor flags: canonicalHash=$hash stableId=${descriptor.stableId} flags=${descriptor.flags}"
        }
    }

    private fun isValidUniformFlags(flags: Int): Boolean =
        flags and SkRuntimeEffect.Uniform.kArray_Flag.inv() and
            SkRuntimeEffect.Uniform.kColor_Flag.inv() and
            SkRuntimeEffect.Uniform.kHalfPrecision_Flag.inv() == 0

    private fun isValidEffectFlags(flags: Int): Boolean {
        val allowed = SkRuntimeEffect.kUsesSampleCoords_Flag or
            SkRuntimeEffect.kAllowColorFilter_Flag or
            SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kAllowBlender_Flag or
            SkRuntimeEffect.kSamplesOutsideMain_Flag or
            SkRuntimeEffect.kUsesColorTransform_Flag
        return flags and allowed.inv() == 0
    }

    private fun duplicateDiagnostic(hash: Long, descriptor: SkRuntimeEffectDescriptor): String =
        "Duplicate runtime effect descriptor registration: canonicalHash=$hash stableId=${descriptor.stableId}"

    private fun toV2Entry(entry: SkRuntimeEffectSupportMatrixEntry): SkRuntimeEffectSupportMatrixV2Entry {
        val gpuBacked = entry.gpuSupport.startsWith("supported:")
        val cpuBacked = entry.cpuSupport.startsWith("supported:")
        val supportState = when {
            entry.descriptor == null -> "expected-unsupported"
            gpuBacked -> "gpu-backed"
            cpuBacked -> "cpu-only"
            else -> "dependency-gated"
        }
        val fallbackReason = when (supportState) {
            "gpu-backed" -> "none"
            "cpu-only" ->
                if (entry.kind == SkRuntimeEffect.Kind.kBlender) {
                    "runtime-effect.blender-dst-read-unsupported"
                } else {
                    "runtime-effect.wgsl-descriptor-missing"
                }
            "expected-unsupported" -> "runtime-effect.wgsl-descriptor-missing"
            else -> "runtime-effect.dependency-gated"
        }
        val pmNote = when (supportState) {
            "gpu-backed" ->
                "This row is a registered Kotlin/CPU and parser-validated WGSL implementation; " +
                    "it is not broad runtime-effect or dynamic SkSL support."
            "cpu-only" ->
                if (entry.kind == SkRuntimeEffect.Kind.kBlender) {
                    "This registered runtime blender has Kotlin/CPU behavior, but its result depends on " +
                        "destination color. WebGPU remains refused until an explicit shader/layer composite " +
                        "BlendPlan exists; this does not support all blend modes."
                } else {
                    "This row has registered Kotlin/CPU behavior but no parser-validated WGSL implementation; " +
                        "GPU remains refused with a stable missing-descriptor reason."
                }
            "expected-unsupported" ->
                "Legacy dispatch metadata lacks a registered WGSL descriptor; it remains visible expected-unsupported."
            else ->
                "Runtime-effect support is dependency-gated until descriptor, CPU, WGSL, and evidence are complete."
        }
        return SkRuntimeEffectSupportMatrixV2Entry(
            canonicalHash = entry.canonicalHash,
            stableId = entry.stableId,
            kind = entry.kind.toString(),
            uniforms = entry.uniforms.map { "${it.name}:${it.type}" },
            children = entry.children.map { "${it.name}:${it.type}" },
            flags = entry.flags,
            cpuImplementationId = entry.cpuImplementationId.takeIf { it.isNotBlank() },
            wgslImplementationId = entry.wgslImplementationId?.takeIf { it.isNotBlank() },
            descriptorStatus = entry.descriptorStatus,
            supportState = supportState,
            fallbackReason = fallbackReason,
            pmNote = pmNote,
        )
    }

    private fun supportMatrixV2PolicyRows(): List<SkRuntimeEffectSupportMatrixV2Entry> =
        listOf(
            SkRuntimeEffectSupportMatrixV2Entry(
                canonicalHash = null,
                stableId = "policy.arbitrary_sksl_input",
                kind = "policy",
                uniforms = emptyList(),
                children = emptyList(),
                flags = null,
                cpuImplementationId = null,
                wgslImplementationId = null,
                descriptorStatus = "policy-only",
                supportState = "expected-unsupported",
                fallbackReason = "runtime-effect.arbitrary-sksl-unsupported",
                pmNote = "Kanvas does not dynamically compile SkSL; arbitrary runtime-effect input remains a stable refusal.",
            ),
            SkRuntimeEffectSupportMatrixV2Entry(
                canonicalHash = null,
                stableId = "policy.unregistered_wgsl_descriptor",
                kind = "policy",
                uniforms = emptyList(),
                children = emptyList(),
                flags = null,
                cpuImplementationId = null,
                wgslImplementationId = null,
                descriptorStatus = "policy-only",
                supportState = "expected-unsupported",
                fallbackReason = "runtime-effect.wgsl-descriptor-missing",
                pmNote = "Runtime effects without a registered WGSL descriptor remain visible expected-unsupported rows.",
            ),
        )

    private fun supportMatrixV2NonClaims(): List<String> =
        listOf(
            "No dynamic SkSL compilation.",
            "No SkSL IR or VM.",
            "No broad runtime-effect support beyond registered descriptors.",
            "No support for arbitrary user WGSL input.",
        )

    private fun markdownCell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ")

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private fun StringBuilder.appendJsonNullableString(value: String?) {
        if (value == null) {
            append("null")
        } else {
            appendJsonString(value)
        }
    }

    private fun StringBuilder.appendJsonStringArray(values: List<String>) {
        append("[")
        values.forEachIndexed { index, value ->
            if (index > 0) append(",")
            appendJsonString(value)
        }
        append("]")
    }

    private val STABLE_ID_PATTERN: Regex = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+")
    private val IMPLEMENTATION_ID_PATTERN: Regex = Regex("[a-z][a-z0-9_]*/[a-z][a-z0-9_]*")
    private val DECLARATION_NAME_PATTERN: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
