package org.skia.effects.runtime

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
    val descriptor: SkRuntimeEffectDescriptor,
) {
    public val cpuSupport: String =
        if (descriptor.cpuImplementationId.isBlank()) {
            "unsupported: CPU implementation id missing"
        } else {
            "supported:${descriptor.cpuImplementationId}"
        }

    public val gpuSupport: String =
        descriptor.wgslImplementationId
            ?.takeIf { it.isNotBlank() }
            ?.let { "supported:$it" }
            ?: "unsupported: WGSL implementation id missing"

    public val missingDiagnostic: String =
        "Runtime effect descriptor not registered: $canonicalHash"
}

public object SkRuntimeEffectDescriptorRegistry {
    private val byHash: MutableMap<Long, SkRuntimeEffectDescriptor> = HashMap()
    private val byStableId: MutableMap<String, SkRuntimeEffectDescriptor> = HashMap()
    private val acceptedWgslImplementationIds: Set<String> = setOf("wgsl/runtime_simple_rt")

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
        byHash.entries
            .map { SkRuntimeEffectSupportMatrixEntry(it.key, it.value) }
            .sortedWith(
                compareBy<SkRuntimeEffectSupportMatrixEntry> { it.descriptor.stableId }
                    .thenBy { it.canonicalHash },
            )

    public fun exportSupportMatrixMarkdown(): String = buildString {
        appendLine("# Runtime Effect Descriptor Support Matrix")
        appendLine()
        appendLine("Derived evidence. The descriptor registry is the source of truth.")
        appendLine()
        appendLine(
            "| Stable id | Canonical hash | Kind | Uniforms | Children | Flags | CPU support | GPU support | Missing diagnostic |",
        )
        appendLine("|---|---:|---|---|---|---:|---|---|---|")
        supportMatrixEntries().forEach { entry ->
            val descriptor = entry.descriptor
            append("| ")
            append(markdownCell(descriptor.stableId))
            append(" | ")
            append(entry.canonicalHash)
            append(" | ")
            append(descriptor.kind)
            append(" | ")
            append(markdownCell(descriptor.uniforms.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(markdownCell(descriptor.children.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(descriptor.flags)
            append(" | ")
            append(markdownCell(entry.cpuSupport))
            append(" | ")
            append(markdownCell(entry.gpuSupport))
            append(" | ")
            append(markdownCell(entry.missingDiagnostic))
            appendLine(" |")
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
            check(wgslId in acceptedWgslImplementationIds) {
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
            SkRuntimeEffect.kSamplesOutsideMain_Flag
        return flags and allowed.inv() == 0
    }

    private fun duplicateDiagnostic(hash: Long, descriptor: SkRuntimeEffectDescriptor): String =
        "Duplicate runtime effect descriptor registration: canonicalHash=$hash stableId=${descriptor.stableId}"

    private fun markdownCell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ")

    private val STABLE_ID_PATTERN: Regex = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+")
    private val IMPLEMENTATION_ID_PATTERN: Regex = Regex("[a-z][a-z0-9_]*/[a-z][a-z0-9_]*")
    private val DECLARATION_NAME_PATTERN: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
